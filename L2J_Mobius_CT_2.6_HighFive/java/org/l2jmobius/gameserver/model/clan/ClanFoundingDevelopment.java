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

/** An actor-owned, paid stock first-level transition with exact native commit evidence. */
public final class ClanFoundingDevelopment
{
	public static final long ADENA_COST = 650_000;
	public static final long SP_COST = 20_000;
	static final String COMPONENT_TYPE = "clan.development.payment";
	private static final Logger LOGGER = Logger.getLogger(ClanFoundingDevelopment.class.getName());

	public enum Outcome { APPLIED, NOT_READY, DEFERRED, UNCONFIRMED }
	enum DurableOutcome { APPLIED, ROLLED_BACK, CONFLICT }

	static record Payment(long profileId, int actorId, int clanId, int baseClassId, int coinObjectId, long adenaBefore, long spBefore, UUID operation)
	{
		long adenaAfter() { return adenaBefore - ADENA_COST; }
		long spAfter() { return spBefore - SP_COST; }
		byte[] evidence()
		{
			return ByteBuffer.allocate(56).putLong(operation.getMostSignificantBits()).putLong(operation.getLeastSignificantBits())
				.putLong(profileId).putInt(actorId).putInt(clanId).putInt(baseClassId).putInt(coinObjectId).putLong(adenaBefore).putLong(spBefore).array();
		}
	}

	/** Temporary completion state retained by the existing Player/STORE owner. */
	public static final class PendingPayment
	{
		private final Clan _clan;
		private final Item _coin;
		private final Payment _payment;
		private final long _originalDurableSp;

		private PendingPayment(Clan clan, Item coin, Payment payment, long originalDurableSp)
		{
			_clan = clan;
			_coin = coin;
			_payment = payment;
			_originalDurableSp = originalDurableSp;
		}
	}

	private ClanFoundingDevelopment() { }

	public static Outcome payForFirstLevel(long profileId, Player leader, VillageMaster npc, BooleanSupplier mandatoryPreparationPending)
	{
		if (profileId <= 0 || leader == null || !leader.hasHeadlessOutboundSession() || leader.isDedicatedGeoScout() || !authorizedAction(leader, profileId)) { return Outcome.NOT_READY; }
		final Clan clan = leader.getClan();
		if (clan == null || mandatoryPreparationPending.getAsBoolean()) { return Outcome.NOT_READY; }
		final var fence = ClanSocialMutationFence.getInstance();
		return fence.execute(new long[] { ClanSocialMutationFence.clanKey(clan.getId()) }, () ->
		{
			synchronized (leader)
			{
				if (leader.getPendingClanDevelopment() != null) { return settleLocked(leader); }
				if (fence.isRetiring(clan.getId()) || clan.firstLevelPaymentPending() || !eligible(leader, clan, npc)) { return Outcome.NOT_READY; }
				final Item coin = leader.getInventory().getAdenaInstance();
				if (coin == null) { return Outcome.NOT_READY; }
				synchronized (coin)
				{
					if (coin.getId() != Inventory.ADENA_ID || coin.getOwnerId() != leader.getObjectId() || coin.getCount() < ADENA_COST) { return Outcome.NOT_READY; }
					return payLocked(profileId, leader, clan, coin);
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

	private static boolean eligible(Player leader, Clan clan, VillageMaster npc)
	{
		return ClanTable.getInstance().getClan(clan.getId()) == clan && leader.getClan() == clan && clan.getLeaderId() == leader.getObjectId()
			&& clan.getLevel() == 0 && clan.getMembersCount() >= 2 && clan.getDissolvingExpiryTime() <= System.currentTimeMillis()
			&& !leader.isAlikeDead() && !leader.isInCombat() && !leader.isChangingClass() && leader.getClassIndex() == 0 && leader.getActiveClass() == leader.getBaseClass()
			&& leader.getSp() >= SP_COST && npc != null && npc.isSpawned() && !npc.isAlikeDead() && npc.canInteract(leader)
			&& GeoEngine.getInstance().canSeeTarget(leader, npc);
	}

	private static Outcome payLocked(long profileId, Player leader, Clan clan, Item coin)
	{
		final var payment = new Payment(profileId, leader.getObjectId(), clan.getId(), leader.getActiveClass(), coin.getObjectId(), coin.getCount(), leader.getSp(), UUID.randomUUID());
		try
		{
			coin.transactRecoverableAdenaDebit(ADENA_COST, connection ->
			{
				final long durableBefore = stage(connection, payment, System.currentTimeMillis(), leader.getStat()::matchesNativeSkillPointCredit);
				if (payment.spBefore() == durableBefore) { leader.getStat().acknowledgeStoredSkillPoints(0, durableBefore); }
				leader.setPendingClanDevelopment(new PendingPayment(clan, coin, payment, durableBefore));
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

	static long stage(Connection connection, Payment payment, long now, LongPredicate witnessedCredit) throws SQLException
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
			receipt.setString(2, COMPONENT_TYPE);
			try (var rows = receipt.executeQuery())
			{
				if (rows.next() && rows.getInt(1) != 1) { throw new SQLException("Unsupported clan payment receipt version."); }
			}
		}
		final long durableSp = readSp(connection, payment, true);
		if (payment.adenaAfter() < 0 || payment.spAfter() < 0 || (durableSp != payment.spBefore() && !witnessedCredit.test(durableSp)))
		{
			throw new SQLException("Native clan payment preimage changed.");
		}
		try (var clan = connection.prepareStatement("UPDATE clan_data SET clan_level=1 WHERE clan_id=? AND clan_level=0 AND leader_id=? AND dissolving_expiry_time<=?"))
		{
			clan.setInt(1, payment.clanId());
			clan.setInt(2, payment.actorId());
			clan.setLong(3, now);
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
		try (var receipt = connection.prepareStatement("INSERT INTO phantom_profile_components (profile_id,component_type,component_schema_version,payload) VALUES (?,?,1,?) ON DUPLICATE KEY UPDATE payload=VALUES(payload), row_version=row_version+1"))
		{
			receipt.setLong(1, payment.profileId());
			receipt.setString(2, COMPONENT_TYPE);
			receipt.setBytes(3, payment.evidence());
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
		final Payment payment = pending._payment;
		final DurableOutcome durable;
		try
		{
			if (leader.getActiveClass() != payment.baseClassId() || leader.getClassIndex() != 0 || leader.getInventory().getAdenaInstance() != pending._coin || pending._coin.getOwnerId() != payment.actorId()
				|| leader.getSp() < payment.spBefore() || (leader.getSp() != payment.spBefore() && !leader.getStat().matchesNativeSkillPointCredit(pending._originalDurableSp))) { return Outcome.UNCONFIRMED; }
			durable = pending._coin.reconcileCountTransaction(payment.actorId(), payment.adenaBefore(), payment.adenaAfter(), (connection, adena) ->
			{
				final DurableOutcome observed = readback(connection, payment, adena, pending._originalDurableSp);
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

	static DurableOutcome readback(Connection connection, Payment payment, long adena, long originalDurableSp) throws SQLException
	{
		final long sp = readSp(connection, payment, false);
		try (var query = connection.prepareStatement("SELECT component_schema_version,payload FROM phantom_profile_components WHERE profile_id=? AND component_type=? FOR UPDATE"))
		{
			query.setLong(1, payment.profileId());
			query.setString(2, COMPONENT_TYPE);
			try (var rows = query.executeQuery())
			{
				final boolean receipt = rows.next() && rows.getInt(1) == 1 && Arrays.equals(rows.getBytes(2), payment.evidence());
				if (receipt && adena == payment.adenaAfter() && sp == payment.spAfter()) { return DurableOutcome.APPLIED; }
				if (!receipt && adena == payment.adenaBefore() && sp == originalDurableSp) { return DurableOutcome.ROLLED_BACK; }
				return DurableOutcome.CONFLICT;
			}
		}
	}

	private static void publishPaid(Player leader, PendingPayment pending)
	{
		leader.setSp(leader.getSp() - SP_COST);
		leader.getStat().acknowledgeStoredSkillPoints(0, pending._payment.spAfter());
		final Clan clan = pending._clan;
		try
		{
			if (ClanTable.getInstance().getClan(clan.getId()) == clan) { clan.publishPaidFirstLevel(leader); }
		}
		finally { clearPending(leader, pending); }
		final var inventory = new InventoryUpdate();
		inventory.addModifiedItem(pending._coin);
		leader.sendInventoryUpdate(inventory);
		final var sp = new StatusUpdate(leader);
		sp.addAttribute(StatusUpdate.SP, (int) leader.getSp());
		leader.sendPacket(sp);
		leader.sendPacket(new SystemMessage(SystemMessageId.YOUR_SP_HAS_DECREASED_BY_S1).addInt((int) SP_COST));
		leader.broadcastSkillPacket(new MagicSkillUse(leader, 5103, 1, 0, 0), leader);
		leader.broadcastSkillPacket(new MagicSkillLaunched(leader, 5103, 1), leader);
	}

	private static void clearPending(Player player, PendingPayment pending)
	{
		pending._clan.setFirstLevelPaymentPending(false);
		player.setPendingClanDevelopment(null);
	}
}
