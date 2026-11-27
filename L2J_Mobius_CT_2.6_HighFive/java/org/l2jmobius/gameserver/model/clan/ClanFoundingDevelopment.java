/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.model.clan;

import java.nio.ByteBuffer;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.LongPredicate;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.l2jmobius.gameserver.data.sql.ClanTable;
import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.instance.VillageMaster;
import org.l2jmobius.gameserver.model.item.instance.Item;
import org.l2jmobius.gameserver.model.itemcontainer.Inventory;
import org.l2jmobius.gameserver.network.SystemMessageId;
import org.l2jmobius.gameserver.network.serverpackets.InventoryUpdate;
import org.l2jmobius.gameserver.network.serverpackets.MagicSkillLaunched;
import org.l2jmobius.gameserver.network.serverpackets.MagicSkillUse;
import org.l2jmobius.gameserver.network.serverpackets.StatusUpdate;
import org.l2jmobius.gameserver.network.serverpackets.SystemMessage;
import org.l2jmobius.gameserver.phantoms.clan.PhantomClanStore;
import org.l2jmobius.gameserver.phantoms.player.PhantomNativeWorkScope;

/** Actor-owned paid clan development with exact native commit evidence. */
public final class ClanFoundingDevelopment
{
	public static final long ADENA_COST = 650_000;
	public static final long SP_COST = 20_000;
	static final String COMPONENT_TYPE = "clan.development.payment";
	private static final String TRANSITION_COMPONENT_TYPE = "clan.development.transition";
	private static final String ITEM_TRANSITION_COMPONENT_TYPE = "clan.development.item-transition";
	private static final Logger LOGGER = Logger.getLogger(ClanFoundingDevelopment.class.getName());

	public enum Outcome { APPLIED, NOT_READY, DEFERRED, UNCONFIRMED }
	enum DurableOutcome { APPLIED, ROLLED_BACK, CONFLICT }

	static record Payment(long profileId, int actorId, int clanId, int baseClassId, int itemObjectId, long itemBefore, long spBefore, UUID operation, int currentLevel)
	{
		Payment(long profileId, int actorId, int clanId, int baseClassId, int itemObjectId, long itemBefore, long spBefore, UUID operation)
		{
			this(profileId, actorId, clanId, baseClassId, itemObjectId, itemBefore, spBefore, operation, 0);
		}

		Payment
		{
			if (!supportsLevel(currentLevel)) { throw new IllegalArgumentException("No recoverable inventory payment for this clan level."); }
		}

		Clan.LevelRequirement requirement() { return Clan.levelRequirement(currentLevel); }
		int version() { return Math.min(currentLevel + 1, 3); }
		String componentType()
		{
			return switch (version())
			{
				case 1 -> COMPONENT_TYPE;
				case 2 -> TRANSITION_COMPONENT_TYPE;
				default -> ITEM_TRANSITION_COMPONENT_TYPE;
			};
		}
		int feeItemId() { return currentLevel < 2 ? Inventory.ADENA_ID : requirement().itemId(); }
		long feeCount() { return currentLevel < 2 ? requirement().adena() : requirement().itemCount(); }
		long itemAfter() { return itemBefore - feeCount(); }
		long spAfter() { return spBefore - requirement().sp(); }
		byte[] evidence()
		{
			final int version = version();
			final var bytes = ByteBuffer.allocate(version == 1 ? 56 : version == 2 ? 76 : 80).putLong(operation.getMostSignificantBits()).putLong(operation.getLeastSignificantBits())
				.putLong(profileId).putInt(actorId).putInt(clanId).putInt(baseClassId).putInt(itemObjectId).putLong(itemBefore).putLong(spBefore);
			if (version >= 2) { bytes.putInt(currentLevel).putLong(feeCount()).putLong(requirement().sp()); }
			if (version == 3) { bytes.putInt(feeItemId()); }
			return bytes.array();
		}
	}

	/** Temporary completion state retained by the existing Player/STORE owner. */
	public static final class PendingPayment
	{
		private final Clan _clan;
		private final Item _item;
		private final Payment _payment;
		private final long _originalDurableSp;

		private PendingPayment(Clan clan, Item item, Payment payment, long originalDurableSp)
		{
			_clan = clan;
			_item = item;
			_payment = payment;
			_originalDurableSp = originalDurableSp;
		}
	}

	private ClanFoundingDevelopment() { }

	public static boolean supportsLevel(int currentLevel)
	{
		return currentLevel >= 0 && currentLevel < 5;
	}

	/** Multiple exact fee stacks can survive a full-debit rollback with a concurrent native reward. */
	public static boolean isItemFee(int itemId)
	{
		for (int level = 2; supportsLevel(level); level++)
		{
			if (Clan.levelRequirement(level).itemId() == itemId) { return true; }
		}
		return false;
	}

	public static Outcome payForFirstLevel(long profileId, Player leader, VillageMaster npc, BooleanSupplier mandatoryPreparationPending)
	{
		return payForLevel(profileId, leader, npc, 0, Long.MAX_VALUE, mandatoryPreparationPending);
	}

	public static Outcome payForLevel(long profileId, Player leader, VillageMaster npc, int expectedLevel, long expiresAt, BooleanSupplier mandatoryPreparationPending)
	{
		if (profileId <= 0 || leader == null || !leader.hasHeadlessOutboundSession() || leader.isDedicatedGeoScout() || !authorizedAction(leader, profileId)) { return Outcome.NOT_READY; }
		final Clan clan = leader.getClan();
		final Clan.LevelRequirement requirement = Clan.levelRequirement(expectedLevel);
		if (clan == null || !supportsLevel(expectedLevel)) { return Outcome.NOT_READY; }
		if (leader.getPendingClanDevelopment() != null) { return settlePending(leader) ? Outcome.DEFERRED : Outcome.UNCONFIRMED; }
		if (mandatoryPreparationPending.getAsBoolean()) { return Outcome.NOT_READY; }
		final var fence = ClanSocialMutationFence.getInstance();
		return fence.execute(new long[] { ClanSocialMutationFence.clanKey(clan.getId()) }, () ->
		{
			synchronized (leader)
			{
				if (leader.getPendingClanDevelopment() != null) { return settleLocked(leader); }
				if (fence.isRetiring(clan.getId()) || clan.firstLevelPaymentPending() || !eligible(leader, clan, npc, requirement)) { return Outcome.NOT_READY; }
				final Item fee = requirement.itemId() == 0 ? leader.getInventory().getAdenaInstance() : leader.getInventory().getItemByItemId(requirement.itemId());
				if (fee == null) { return Outcome.NOT_READY; }
				synchronized (fee)
				{
					final var payment = new Payment(profileId, leader.getObjectId(), clan.getId(), leader.getActiveClass(),
						fee.getObjectId(), fee.getCount(), leader.getSp(), UUID.randomUUID(), expectedLevel);
					if (System.currentTimeMillis() >= expiresAt || fee.getId() != payment.feeItemId()
						|| fee.getOwnerId() != leader.getObjectId() || payment.itemAfter() < 0) { return Outcome.NOT_READY; }
					return payLocked(leader, clan, fee, payment);
				}
			}
		});
	}

	private static boolean authorizedAction(Player player, long profileId)
	{
		if (!(player.getNativeWorkOwner() instanceof PhantomNativeWorkScope owner) || owner.player() != player || !owner.isCurrent()) { return false; }
		final var execution = owner.currentExecutionStamp();
		return execution != null && execution.profileId() == profileId;
	}

	private static boolean eligible(Player leader, Clan clan, VillageMaster npc, Clan.LevelRequirement requirement)
	{
		return ClanTable.getInstance().getClan(clan.getId()) == clan && leader.getClan() == clan && clan.getLeaderId() == leader.getObjectId()
			&& clan.getLevel() == requirement.currentLevel() && (requirement.currentLevel() != 0 || clan.getMembersCount() >= 2)
			&& clan.getDissolvingExpiryTime() == 0
			&& !leader.isAlikeDead() && !leader.isInCombat() && !leader.isChangingClass() && leader.getClassIndex() == 0 && leader.getActiveClass() == leader.getBaseClass()
			&& leader.getSp() >= requirement.sp() && npc != null && npc.isSpawned() && !npc.isAlikeDead() && npc.canInteract(leader)
			&& GeoEngine.getInstance().canSeeTarget(leader, npc);
	}

	private static Outcome payLocked(Player leader, Clan clan, Item fee, Payment payment)
	{
		try
		{
			fee.transactRecoverableInventoryDebit(payment.actorId(), payment.itemBefore(), payment.feeCount(), connection ->
			{
				final long durableBefore = stage(connection, payment, leader.getStat()::matchesNativeSkillPointCredit);
				if (payment.spBefore() == durableBefore) { leader.getStat().acknowledgeStoredSkillPoints(0, durableBefore); }
				leader.setPendingClanDevelopment(new PendingPayment(clan, fee, payment, durableBefore));
				clan.setFirstLevelPaymentPending(true);
				return null;
			});
		}
		catch (Exception failure)
		{
			if (leader.getPendingClanDevelopment() == null) { return Outcome.DEFERRED; }
			LOGGER.log(Level.WARNING, "Clan level payment requires native readback for character " + payment.actorId(), failure);
			return settleLocked(leader);
		}
		publishPaid(leader, leader.getPendingClanDevelopment());
		return Outcome.APPLIED;
	}

	static long stage(Connection connection, Payment payment, LongPredicate witnessedCredit) throws SQLException
	{
		try (var profile = connection.prepareStatement("SELECT p.character_object_id FROM phantom_profiles p INNER JOIN phantom_profile_components c ON c.profile_id=p.profile_id AND c.component_type=? WHERE p.profile_id=? FOR UPDATE"))
		{
			profile.setString(1, PhantomClanStore.COMPONENT_TYPE);
			profile.setLong(2, payment.profileId());
			try (var rows = profile.executeQuery())
			{
				if (!rows.next() || rows.getInt(1) != payment.actorId()) { throw new SQLException("Missing exact clan organization owner."); }
			}
		}
		try (var receipt = connection.prepareStatement("SELECT component_schema_version FROM phantom_profile_components WHERE profile_id=? AND component_type=? FOR UPDATE"))
		{
			receipt.setLong(1, payment.profileId());
			receipt.setString(2, payment.componentType());
			try (var rows = receipt.executeQuery())
			{
				if (rows.next() && rows.getInt(1) != payment.version()) { throw new SQLException("Unsupported clan payment receipt version."); }
			}
		}
		final long durableSp = readSp(connection, payment, true);
		if (payment.itemAfter() < 0 || payment.spAfter() < 0 || (durableSp != payment.spBefore() && !witnessedCredit.test(durableSp)))
		{
			throw new SQLException("Native clan payment preimage changed.");
		}
		try (var clan = connection.prepareStatement("UPDATE clan_data SET clan_level=? WHERE clan_id=? AND clan_level=? AND leader_id=? AND dissolving_expiry_time=0"))
		{
			clan.setInt(1, payment.requirement().nextLevel());
			clan.setInt(2, payment.clanId());
			clan.setInt(3, payment.currentLevel());
			clan.setInt(4, payment.actorId());
			if (clan.executeUpdate() != 1) { throw new SQLException("Native clan leadership or level changed."); }
		}
		if (payment.spAfter() != durableSp)
		{
			try (var character = connection.prepareStatement("UPDATE characters SET sp=? WHERE charId=? AND classid=? AND base_class=? AND clanid=? AND sp=?"))
			{
				character.setLong(1, payment.spAfter());
				character.setInt(2, payment.actorId());
				character.setInt(3, payment.baseClassId());
				character.setInt(4, payment.baseClassId());
				character.setInt(5, payment.clanId());
				character.setLong(6, durableSp);
				if (character.executeUpdate() != 1) { throw new SQLException("Native clan fee could not debit the exact SP row."); }
			}
		}
		try (var receipt = connection.prepareStatement("INSERT INTO phantom_profile_components (profile_id,component_type,component_schema_version,payload) VALUES (?,?,?,?) ON DUPLICATE KEY UPDATE component_schema_version=VALUES(component_schema_version), payload=VALUES(payload), row_version=row_version+1"))
		{
			receipt.setLong(1, payment.profileId());
			receipt.setString(2, payment.componentType());
			receipt.setInt(3, payment.version());
			receipt.setBytes(4, payment.evidence());
			receipt.executeUpdate();
		}
		return durableSp;
	}

	private static long readSp(Connection connection, Payment payment, boolean requireClan) throws SQLException
	{
		try (var query = connection.prepareStatement("SELECT sp,clanid FROM characters WHERE charId=? AND classid=? AND base_class=? FOR UPDATE"))
		{
			query.setInt(1, payment.actorId());
			query.setInt(2, payment.baseClassId());
			query.setInt(3, payment.baseClassId());
			try (var rows = query.executeQuery())
			{
				if (!rows.next() || (requireClan && rows.getInt(2) != payment.clanId())) { throw new SQLException("Native SP owner changed."); }
				return rows.getLong(1);
			}
		}
	}

	/** Called by the existing owner before either native STORE entrance. No clan stripe is taken here. */
	public static boolean settlePending(Player leader)
	{
		synchronized (leader)
		{
			return leader.getPendingClanDevelopment() == null || settleLocked(leader) != Outcome.UNCONFIRMED;
		}
	}

	private static Outcome settleLocked(Player leader)
	{
		final PendingPayment pending = leader.getPendingClanDevelopment();
		synchronized (pending._item)
		{
			return settleItemLocked(leader, pending);
		}
	}

	private static Outcome settleItemLocked(Player leader, PendingPayment pending)
	{
		final Payment payment = pending._payment;
		final DurableOutcome durable;
		try
		{
			if (leader.getActiveClass() != payment.baseClassId() || leader.getClassIndex() != 0
				|| leader.getInventory().getItemByObjectId(payment.itemObjectId()) != pending._item || pending._item.getOwnerId() != payment.actorId()
				|| leader.getSp() < payment.spBefore() || (leader.getSp() != payment.spBefore() && !leader.getStat().matchesNativeSkillPointCredit(pending._originalDurableSp))) { return Outcome.UNCONFIRMED; }
			durable = pending._item.reconcileCountTransaction(payment.actorId(), payment.itemBefore(), payment.itemAfter(), (connection, count) ->
			{
				final DurableOutcome observed = readback(connection, payment, count, pending._originalDurableSp);
				return observed == DurableOutcome.CONFLICT ? null : observed;
			});
		}
		catch (Exception unavailable) { return Outcome.UNCONFIRMED; }
		if (durable == null) { return Outcome.UNCONFIRMED; }
		if (durable == DurableOutcome.APPLIED)
		{
			publishPaid(leader, pending);
			return Outcome.APPLIED;
		}
		clearPending(leader, pending);
		return Outcome.DEFERRED;
	}

	static DurableOutcome readback(Connection connection, Payment payment, long count, long originalDurableSp) throws SQLException
	{
		final long sp = readSp(connection, payment, false);
		final int level;
		try (var query = connection.prepareStatement("SELECT clan_level FROM clan_data WHERE clan_id=? AND leader_id=? FOR UPDATE"))
		{
			query.setInt(1, payment.clanId());
			query.setInt(2, payment.actorId());
			try (var rows = query.executeQuery())
			{
				if (!rows.next()) { return DurableOutcome.CONFLICT; }
				level = rows.getInt(1);
			}
		}
		try (var query = connection.prepareStatement("SELECT component_schema_version,payload FROM phantom_profile_components WHERE profile_id=? AND component_type=? FOR UPDATE"))
		{
			query.setLong(1, payment.profileId());
			query.setString(2, payment.componentType());
			try (var rows = query.executeQuery())
			{
				final boolean receipt = rows.next() && rows.getInt(1) == payment.version() && Arrays.equals(rows.getBytes(2), payment.evidence());
				if (receipt && level == payment.requirement().nextLevel() && count == payment.itemAfter() && sp == payment.spAfter()) { return DurableOutcome.APPLIED; }
				if (!receipt && level == payment.currentLevel() && count == payment.itemBefore() && sp == originalDurableSp) { return DurableOutcome.ROLLED_BACK; }
				return DurableOutcome.CONFLICT;
			}
		}
	}

	private static void publishPaid(Player leader, PendingPayment pending)
	{
		final var requirement = pending._payment.requirement();
		leader.setSp(leader.getSp() - requirement.sp());
		leader.getStat().acknowledgeStoredSkillPoints(0, pending._payment.spAfter());
		final Clan clan = pending._clan;
		final boolean removed;
		try
		{
			removed = requirement.itemId() > 0 && leader.getInventory().publishCommittedStackDebit(pending._item, leader);
			if (ClanTable.getInstance().getClan(clan.getId()) == clan) { clan.publishPaidLevel(leader, requirement); }
		}
		finally { clearPending(leader, pending); }
		final var inventory = new InventoryUpdate();
		if (removed) { inventory.addRemovedItem(pending._item); }
		else { inventory.addModifiedItem(pending._item); }
		leader.sendInventoryUpdate(inventory);
		if (requirement.itemId() > 0)
		{
			leader.sendPacket(new SystemMessage(SystemMessageId.S1_HAS_DISAPPEARED).addItemName(requirement.itemId()));
		}
		final var sp = new StatusUpdate(leader);
		sp.addAttribute(StatusUpdate.SP, (int) leader.getSp());
		leader.sendPacket(sp);
		leader.sendPacket(new SystemMessage(SystemMessageId.YOUR_SP_HAS_DECREASED_BY_S1).addInt((int) requirement.sp()));
		leader.broadcastSkillPacket(new MagicSkillUse(leader, 5103, 1, 0, 0), leader);
		leader.broadcastSkillPacket(new MagicSkillLaunched(leader, 5103, 1), leader);
	}

	private static void clearPending(Player player, PendingPayment pending)
	{
		pending._clan.setFirstLevelPaymentPending(false);
		player.setPendingClanDevelopment(null);
	}
}
