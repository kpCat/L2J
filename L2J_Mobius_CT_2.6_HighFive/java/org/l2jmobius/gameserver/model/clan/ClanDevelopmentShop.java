/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.model.clan;

import java.nio.ByteBuffer;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.l2jmobius.gameserver.config.GeneralConfig;
import org.l2jmobius.gameserver.config.custom.CommunityBoardConfig;
import org.l2jmobius.gameserver.data.sql.ClanTable;
import org.l2jmobius.gameserver.data.xml.ItemData;
import org.l2jmobius.gameserver.data.xml.MultisellData;
import org.l2jmobius.gameserver.managers.IdManager;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.clan.ClanFoundingDevelopment.DurableOutcome;
import org.l2jmobius.gameserver.model.clan.ClanFoundingDevelopment.Outcome;
import org.l2jmobius.gameserver.model.item.enums.ItemLocation;
import org.l2jmobius.gameserver.model.item.instance.Item;
import org.l2jmobius.gameserver.model.itemcontainer.Inventory;
import org.l2jmobius.gameserver.model.zone.ZoneId;
import org.l2jmobius.gameserver.network.serverpackets.InventoryUpdate;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyConflictPort;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyOperation.Reservation;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyOperation.ResourceKind;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNativeTravelActions;
import org.l2jmobius.gameserver.qol.PersonalProgressionShopService;

/** One curated clan fee item bought by its leader through the existing native currency writer. */
public final class ClanDevelopmentShop
{
	private static final String COMPONENT_TYPE = "clan.development.shop-purchase";
	private static final String BULK_COMPONENT_TYPE = "clan.development.shop-bulk-purchase";
	private static final Logger LOGGER = Logger.getLogger(ClanDevelopmentShop.class.getName());

	private ClanDevelopmentShop() { }

	record Purchase(long profileId, int actorId, int clanId, int level, int adenaId, long adenaBefore,
		int productId, int itemId, long count, long price, UUID operation)
	{
		Purchase(long profileId, int actorId, int clanId, int level, int adenaId, long adenaBefore, int productId, int itemId, long price, UUID operation)
		{
			this(profileId, actorId, clanId, level, adenaId, adenaBefore, productId, itemId, 1, price, operation);
		}

		long adenaAfter() { return adenaBefore - price; }
		int version() { return count == 1 ? 1 : 2; }
		String componentType() { return version() == 1 ? COMPONENT_TYPE : BULK_COMPONENT_TYPE; }

		byte[] evidence()
		{
			final var bytes = ByteBuffer.allocate(version() == 1 ? 64 : 72).putLong(operation.getMostSignificantBits()).putLong(operation.getLeastSignificantBits())
				.putLong(profileId).putInt(actorId).putInt(clanId).putInt(level).putInt(adenaId).putLong(adenaBefore)
				.putInt(productId).putInt(itemId).putLong(price);
			if (version() == 2) { bytes.putLong(count); }
			return bytes.array();
		}
	}

	public static final class PendingPurchase
	{
		private final Purchase _purchase;
		private final Item _adena;
		private final Item _product;
		private boolean _committed;

		private PendingPurchase(Purchase purchase, Item adena, Item product)
		{
			_purchase = purchase;
			_adena = adena;
			_product = product;
		}
	}

	public static Outcome buy(long profile, Player leader, int clanId, int level, long reserve, long expiresAt, BooleanSupplier stillCurrent)
	{
		if (!ClanFoundingDevelopment.authorizedAction(leader, profile)) { return Outcome.NOT_READY; }
		if (leader.getPendingClanPurchase() != null)
		{
			return settlePending(leader) ? Outcome.DEFERRED : Outcome.UNCONFIRMED;
		}
		final var clan = leader.getClan();
		final var requirement = Clan.levelRequirement(level);
		if (clan == null || clan.getId() != clanId || !ClanFoundingDevelopment.supportsLevel(level)
			|| requirement.itemCount() <= 0 || requirement.itemId() == 0 || reserve < 0 || !mayUseShop(leader)) { return Outcome.NOT_READY; }
		final int itemId = requirement.itemId();
		final long count = requirement.itemCount();
		final long price = PersonalProgressionShopService.getInstance().storefrontOffers().stream()
			.filter(offer -> offer.itemId() == itemId && offer.count() == count).mapToLong(PersonalProgressionShopService.StorefrontOffer::price)
			.findFirst().orElse(0);
		final var inventory = leader.getInventory();
		if (price <= 0 || leader.getAdena() - reserve < price) { return Outcome.NOT_READY; }
		final int actorId = leader.getObjectId();
		final var resources = List.of(
			new Reservation(profile, actorId, 0, ResourceKind.ADENA, 0, Inventory.ADENA_ID, price, leader.getAdena(), 0, "INVENTORY"),
			new Reservation(profile, actorId, 0, ResourceKind.ITEM_COUNT, 0, itemId, count, 0, 0, "INVENTORY"),
			new Reservation(profile, actorId, 0, ResourceKind.CAPACITY, 0, 0, 1, inventory.getSize(), 0, "INVENTORY"));
		try (var claim = PhantomEconomyConflictPort.claim(profile, null, resources))
		{
			if (!claim.acquired()) { return Outcome.NOT_READY; }
			return buyWithClaim(profile, leader, clan, requirement, price, reserve, expiresAt, stillCurrent, resources);
		}
	}

	private static Outcome buyWithClaim(long profile, Player leader, Clan clan, Clan.LevelRequirement requirement,
		long price, long reserve, long expiresAt, BooleanSupplier stillCurrent, List<Reservation> resources)
	{
		final int clanId = clan.getId();
		final int level = requirement.currentLevel();
		final int itemId = requirement.itemId();
		final long count = requirement.itemCount();
		final var fence = ClanSocialMutationFence.getInstance();
		return fence.execute(new long[] { ClanSocialMutationFence.clanKey(clanId) }, () ->
		{
			synchronized (leader)
			{
				synchronized (clan)
				{
					if (leader.getPendingClanPurchase() != null || leader.getPendingClanDevelopment() != null || leader.getPendingClanDeparture() != null
						|| !ClanFoundingDevelopment.authorizedAction(leader, profile) || !mayUseShop(leader) || !stillCurrent.getAsBoolean()
						|| leader.getClan() != clan || ClanTable.getInstance().getClan(clanId) != clan || clan.getLeaderId() != leader.getObjectId()
						|| clan.getLevel() != level || clan.getDissolvingExpiryTime() != 0 || fence.isRetiring(clanId)
						|| clan.firstLevelPaymentPending() || leader.getSp() < requirement.sp() || level >= 5 && clan.getReputationScore() < requirement.reputation()
						|| clan.getMembersCount() < requirement.members()) { return Outcome.NOT_READY; }
					final var inventory = leader.getInventory();
					final var adena = inventory.getAdenaInstance();
					if (adena == null || inventory.getInventoryItemCount(itemId, -1) >= count
						|| !inventory.validateCapacity(1) || !inventory.validateWeightByItemId(itemId, count)) { return Outcome.NOT_READY; }
					synchronized (adena)
					{
						if (adena.getOwnerId() != leader.getObjectId() || adena.getCount() - reserve < price
							|| adena.blocksInventoryDebit() || System.currentTimeMillis() >= expiresAt
							|| !MultisellData.getInstance().matchesPersonalProgressionOffer(itemId, count, price)) { return Outcome.NOT_READY; }
						final var template = ItemData.getInstance().getTemplate(itemId);
						if (template == null || !template.isStackable() || template.isEquipable() || template.getDuration() != -1
							|| template.getTime() != -1) { return Outcome.NOT_READY; }
						final var product = new Item(IdManager.getInstance().getNextId(), template);
						product.setOwnerId(leader.getObjectId());
						product.setItemLocation(ItemLocation.INVENTORY, 0, false);
						product.setCount(count);
						final var purchase = new Purchase(profile, leader.getObjectId(), clanId, level, adena.getObjectId(), adena.getCount(),
							product.getObjectId(), itemId, count, price, UUID.randomUUID());
						return buyLocked(leader, new PendingPurchase(purchase, adena, product), expiresAt, resources);
					}
				}
			}
		});
	}

	private static boolean mayUseShop(Player player)
	{
		return PersonalProgressionShopService.getInstance().isShopEnabled() && GeneralConfig.ENABLE_COMMUNITY_BOARD && CommunityBoardConfig.CUSTOM_CB_ENABLED
			&& (!CommunityBoardConfig.COMMUNITYBOARD_KARMA_DISABLED || player.getKarma() == 0)
			&& player.hasHeadlessOutboundSession() && !player.isDedicatedGeoScout() && !player.isGM()
			&& player.getAccessLevel() != null && player.getAccessLevel().getLevel() == 0
			&& PhantomNativeTravelActions.safePreparationTown(player) && !player.isAlikeDead() && !player.isInCombat()
			&& !player.isAttackingNow() && !player.isCastingNow() && !player.isCastingSimultaneouslyNow()
			&& !player.isMoving() && !player.isTeleporting() && !player.isAutoPlaying()
			&& !player.isInParty() && !player.isInDuel() && !player.isInOlympiadMode() && !player.isOnEvent()
			&& player.getPvpFlag() == 0 && !player.isInsideZone(ZoneId.SIEGE) && !player.isInsideZone(ZoneId.PVP)
			&& !player.isProcessingTransaction() && !player.isChangingClass() && player.getClassIndex() == 0
			&& player.getActiveClass() == player.getBaseClass() && !player.isInventoryDisabled()
			&& !player.isInStoreMode() && !player.hasPendingOwnedStore();
	}

	private static Outcome buyLocked(Player leader, PendingPurchase pending, long expiresAt, List<Reservation> resources)
	{
		try
		{
			pending._adena.transactExactRecoverableAdenaDebit(pending._purchase.actorId(), pending._purchase.adenaBefore(),
				pending._purchase.price(), connection -> lockAuthority(connection, pending._purchase, resources),
				connection ->
				{
					stage(connection, pending._purchase, pending._product);
					if (System.currentTimeMillis() >= expiresAt || !mayUseShop(leader)
						|| !MultisellData.getInstance().matchesPersonalProgressionOffer(pending._purchase.itemId(), pending._purchase.count(), pending._purchase.price()))
					{
						throw new SQLException("The clan shop quote expired before commit.");
					}
					leader.setPendingClanPurchase(pending);
					return null;
				});
		}
		catch (Exception failure)
		{
			if (leader.getPendingClanPurchase() == null)
			{
				IdManager.getInstance().releaseId(pending._product.getObjectId());
				return Outcome.DEFERRED;
			}
			LOGGER.log(Level.WARNING, "Clan shop purchase requires native readback for character " + leader.getObjectId(), failure);
			return settleLocked(leader, pending);
		}
		pending._committed = true;
		return publish(leader, pending);
	}

	static Void lockAuthority(Connection connection, Purchase purchase, List<Reservation> resources) throws SQLException
	{
		ClanFoundingDevelopment.requireOrganizationOwner(connection, purchase.profileId(), purchase.actorId());
		PhantomEconomyConflictPort.requireNoConflict(connection, null, resources);
		try (var query = connection.prepareStatement("SELECT c.clan_id FROM clan_data c JOIN characters a ON a.charId=c.leader_id AND a.clanid=c.clan_id WHERE c.clan_id=? AND c.clan_level=? AND c.leader_id=? AND c.dissolving_expiry_time=0 FOR UPDATE"))
		{
			query.setInt(1, purchase.clanId());
			query.setInt(2, purchase.level());
			query.setInt(3, purchase.actorId());
			try (var rows = query.executeQuery())
			{
				if (!rows.next()) { throw new SQLException("Native clan shop leadership or level changed."); }
			}
		}
		return null;
	}

	static void stage(Connection connection, Purchase purchase, Item product) throws SQLException
	{
		final var requirement = Clan.levelRequirement(purchase.level());
		if (requirement == null || !ClanFoundingDevelopment.supportsLevel(purchase.level()) || requirement.itemCount() != purchase.count()
			|| requirement.itemId() != purchase.itemId() || purchase.price() <= 0 || purchase.adenaAfter() < 0
			|| purchase.itemId() == Inventory.ADENA_ID || purchase.adenaId() == purchase.productId()
			|| product.getOwnerId() != purchase.actorId() || product.getObjectId() != purchase.productId()
			|| product.getId() != purchase.itemId() || product.getCount() != purchase.count()) { throw new SQLException("Invalid native clan purchase facts."); }
		try (var query = connection.prepareStatement("SELECT count FROM items WHERE owner_id=? AND item_id=? AND loc='INVENTORY' AND count>0 FOR UPDATE"))
		{
			query.setInt(1, purchase.actorId());
			query.setInt(2, purchase.itemId());
			try (var rows = query.executeQuery())
			{
				long count = 0;
				while (count < purchase.count() && rows.next()) { count = Math.addExact(count, rows.getLong(1)); }
				if (count >= purchase.count()) { throw new SQLException("The native clan fee item is already owned."); }
			}
		}
		if (hasReceipt(connection, purchase)) { throw new SQLException("This clan purchase is already recorded."); }
		product.stagePlainInventoryCredit(connection);
		try (var receipt = connection.prepareStatement("INSERT INTO phantom_profile_components (profile_id,component_type,component_schema_version,payload) VALUES (?,?,?,?) ON DUPLICATE KEY UPDATE payload=VALUES(payload), row_version=row_version+1"))
		{
			bindReceipt(receipt, purchase);
			receipt.setInt(3, purchase.version());
			receipt.setBytes(4, purchase.evidence());
			receipt.executeUpdate();
		}
	}

	private static void bindReceipt(java.sql.PreparedStatement statement, Purchase purchase) throws SQLException
	{
		statement.setLong(1, purchase.profileId());
		statement.setString(2, purchase.componentType());
	}

	private static boolean hasReceipt(Connection connection, Purchase purchase) throws SQLException
	{
		try (var query = connection.prepareStatement("SELECT component_schema_version,payload FROM phantom_profile_components WHERE profile_id=? AND component_type=? FOR UPDATE"))
		{
			bindReceipt(query, purchase);
			try (var rows = query.executeQuery())
			{
				if (!rows.next()) { return false; }
				if (rows.getInt(1) != purchase.version()) { throw new SQLException("Unsupported native clan shop receipt version."); }
				return Arrays.equals(rows.getBytes(2), purchase.evidence());
			}
		}
	}

	static DurableOutcome readback(Connection connection, Purchase purchase, long durableAdena) throws SQLException
	{
		final boolean receipt = hasReceipt(connection, purchase);
		try (var query = connection.prepareStatement("SELECT owner_id,item_id,count,loc,loc_data,enchant_level,custom_type1,custom_type2,mana_left,time FROM items WHERE object_id=? FOR UPDATE"))
		{
			query.setInt(1, purchase.productId());
			try (var row = query.executeQuery())
			{
				if (!row.next())
				{
					return !receipt && durableAdena == purchase.adenaBefore() ? DurableOutcome.ROLLED_BACK : DurableOutcome.CONFLICT;
				}
				return receipt && durableAdena == purchase.adenaAfter() && row.getInt(1) == purchase.actorId() && row.getInt(2) == purchase.itemId()
					&& row.getLong(3) == purchase.count() && "INVENTORY".equals(row.getString(4)) && row.getInt(5) == 0 && row.getInt(6) == 0
					&& row.getInt(7) == 0 && row.getInt(8) == 0 && row.getInt(9) == -1 && row.getLong(10) == -1
					? DurableOutcome.APPLIED : DurableOutcome.CONFLICT;
			}
		}
	}

	/** The actor/STORE owner can finish an accepted purchase after the initiating intent has expired. */
	public static boolean settlePending(Player leader)
	{
		synchronized (leader)
		{
			final var pending = leader.getPendingClanPurchase();
			if (pending == null) { return true; }
			synchronized (pending._adena) { return settleLocked(leader, pending) != Outcome.UNCONFIRMED; }
		}
	}

	private static Outcome settleLocked(Player leader, PendingPurchase pending)
	{
		if (!pending._committed)
		{
			final var purchase = pending._purchase;
			if (leader.getInventory().getItemByObjectId(purchase.adenaId()) != pending._adena) { return Outcome.UNCONFIRMED; }
			try
			{
				final var durable = pending._adena.reconcileCountTransaction(purchase.actorId(), purchase.adenaBefore(), purchase.adenaAfter(),
					(connection, count) ->
					{
						final var outcome = readback(connection, purchase, count);
						return outcome == DurableOutcome.CONFLICT ? null : outcome;
					});
				if (durable == null) { return Outcome.UNCONFIRMED; }
				if (durable == DurableOutcome.ROLLED_BACK)
				{
					IdManager.getInstance().releaseId(pending._product.getObjectId());
					leader.setPendingClanPurchase(null);
					return Outcome.DEFERRED;
				}
				pending._committed = true;
			}
			catch (Exception failure)
			{
				LOGGER.log(Level.WARNING, "Clan shop readback failed for character " + leader.getObjectId(), failure);
				return Outcome.UNCONFIRMED;
			}
		}
		return publish(leader, pending);
	}

	private static Outcome publish(Player leader, PendingPurchase pending)
	{
		if (!leader.getInventory().publishPurchasedClanItem(pending._product)) { return Outcome.UNCONFIRMED; }
		leader.setPendingClanPurchase(null);
		final var update = new InventoryUpdate();
		update.addModifiedItem(pending._adena);
		update.addNewItem(pending._product);
		leader.sendInventoryUpdate(update);
		return Outcome.APPLIED;
	}
}
