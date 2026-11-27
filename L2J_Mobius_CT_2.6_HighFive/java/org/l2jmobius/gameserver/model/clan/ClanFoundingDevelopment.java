/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.model.clan;

import java.nio.ByteBuffer;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.LongPredicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.config.PlayerConfig;
import org.l2jmobius.gameserver.data.sql.ClanTable;
import org.l2jmobius.gameserver.data.xml.SkillData;
import org.l2jmobius.gameserver.data.xml.SkillTreeData;
import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.instance.VillageMaster;
import org.l2jmobius.gameserver.model.item.instance.Item;
import org.l2jmobius.gameserver.model.item.enums.ItemLocation;
import org.l2jmobius.gameserver.model.itemcontainer.Inventory;
import org.l2jmobius.gameserver.model.skill.Skill;
import org.l2jmobius.gameserver.model.skill.holders.SkillLearn;
import org.l2jmobius.gameserver.network.SystemMessageId;
import org.l2jmobius.gameserver.network.serverpackets.InventoryUpdate;
import org.l2jmobius.gameserver.network.serverpackets.MagicSkillLaunched;
import org.l2jmobius.gameserver.network.serverpackets.MagicSkillUse;
import org.l2jmobius.gameserver.network.serverpackets.StatusUpdate;
import org.l2jmobius.gameserver.network.serverpackets.SystemMessage;
import org.l2jmobius.gameserver.phantoms.clan.PhantomClanStore;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyConflictPort;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyOperation.Reservation;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyOperation.ResourceKind;
import org.l2jmobius.gameserver.phantoms.player.PhantomNativeWorkScope;

/** Actor-owned paid clan development with exact native commit evidence. */
public final class ClanFoundingDevelopment
{
	public static final long ADENA_COST = 650_000;
	public static final long SP_COST = 20_000;
	static final String COMPONENT_TYPE = "clan.development.payment";
	private static final String TRANSITION_COMPONENT_TYPE = "clan.development.transition";
	private static final String ITEM_TRANSITION_COMPONENT_TYPE = "clan.development.item-transition";
	private static final String REPUTATION_TRANSITION_COMPONENT_TYPE = "clan.development.reputation-transition";
	private static final String SKILL_PAYMENT_COMPONENT_TYPE = "clan.development.skill-payment";
	private static final Logger LOGGER = Logger.getLogger(ClanFoundingDevelopment.class.getName());
	private static final Set<Integer> FEE_ITEM_IDS = IntStream.range(2, 11).map(level -> Clan.levelRequirement(level).itemId())
		.filter(id -> id > 0).boxed().collect(Collectors.toUnmodifiableSet());
	// Plain stack fees used by the stock ordinary pledge tree.
	private static final Set<Integer> PLEDGE_FEE_ITEM_IDS = Set.of(8175, 8176, 9814, 9815, 9816, 9817, 9818);

	public enum Outcome { APPLIED, NOT_READY, DEFERRED, UNCONFIRMED }
	enum DurableOutcome { APPLIED, ROLLED_BACK, CONFLICT }

	public record PledgeTerms(int clanLevel, int skillId, int rank, int reputation, int itemId, long itemCount)
	{
	}

	static record Payment(long profileId, int actorId, int clanId, int baseClassId, int itemObjectId, long itemBefore,
		long spBefore, UUID operation, Clan.LevelRequirement requirement, int reputationBefore, int territoryCastleId, PledgeTerms pledge)
	{
		Payment(long profileId, int actorId, int clanId, int baseClassId, int itemObjectId, long itemBefore, long spBefore, UUID operation)
		{
			this(profileId, actorId, clanId, baseClassId, itemObjectId, itemBefore, spBefore, operation, 0);
		}

		Payment(long profileId, int actorId, int clanId, int baseClassId, int itemObjectId, long itemBefore, long spBefore, UUID operation, int currentLevel)
		{
			this(profileId, actorId, clanId, baseClassId, itemObjectId, itemBefore, spBefore, operation, Clan.levelRequirement(currentLevel), 0, 0);
		}

		Payment(long profileId, int actorId, int clanId, int baseClassId, int itemObjectId, long itemBefore, long spBefore, UUID operation,
			Clan.LevelRequirement requirement, int reputationBefore)
		{
			this(profileId, actorId, clanId, baseClassId, itemObjectId, itemBefore, spBefore, operation, requirement, reputationBefore, 0);
		}

		Payment(long profileId, int actorId, int clanId, int baseClassId, int itemObjectId, long itemBefore, long spBefore, UUID operation,
			Clan.LevelRequirement requirement, int reputationBefore, int territoryCastleId)
		{
			this(profileId, actorId, clanId, baseClassId, itemObjectId, itemBefore, spBefore, operation, requirement, reputationBefore, territoryCastleId, null);
		}

		Payment(long profileId, int actorId, int clanId, int baseClassId, int itemObjectId, long itemBefore, UUID operation,
			PledgeTerms pledge, int reputationBefore)
		{
			this(profileId, actorId, clanId, baseClassId, itemObjectId, itemBefore, 0, operation, null, reputationBefore, 0, pledge);
		}

		Payment
		{
			if (pledge == null && (requirement == null || !supportsLevel(requirement.currentLevel()))) { throw new IllegalArgumentException("No recoverable payment for this clan level."); }
		}

		int currentLevel() { return pledge != null ? pledge.clanLevel() : requirement.currentLevel(); }
		int targetLevel() { return pledge != null ? currentLevel() : requirement.nextLevel(); }
		long spCost() { return pledge != null ? 0 : requirement.sp(); }
		int reputationCost() { return pledge != null ? pledge.reputation() : requirement.reputation(); }
		int requiredMembers() { return pledge != null ? 0 : requirement.members(); }
		boolean needsTerritory() { return pledge == null && requirement.territory(); }
		boolean reputationTier() { return currentLevel() >= 5; }
		int version() { return pledge != null ? 5 : reputationTier() ? 4 : Math.min(currentLevel() + 1, 3); }
		String componentType()
		{
			return switch (version())
			{
				case 1 -> COMPONENT_TYPE;
				case 2 -> TRANSITION_COMPONENT_TYPE;
				case 3 -> ITEM_TRANSITION_COMPONENT_TYPE;
				case 5 -> SKILL_PAYMENT_COMPONENT_TYPE;
				default -> REPUTATION_TRANSITION_COMPONENT_TYPE;
			};
		}
		int feeItemId() { return pledge != null ? pledge.itemId() : currentLevel() < 2 ? Inventory.ADENA_ID : requirement.itemId(); }
		long feeCount() { return pledge != null ? pledge.itemCount() : currentLevel() < 2 ? requirement.adena() : requirement.itemCount(); }
		long itemAfter() { return itemBefore - feeCount(); }
		boolean keepsAdenaReserve(long reserve) { return feeItemId() != Inventory.ADENA_ID || itemAfter() >= reserve; }
		long spAfter() { return spBefore - spCost(); }
		int reputationAfter() { return reputationBefore - reputationCost(); }
		byte[] evidence()
		{
			final int version = version();
			final int size = switch (version)
			{
				case 1 -> 56;
				case 2 -> 76;
				case 3 -> 80;
				case 5 -> 96;
				default -> 100;
			};
			final var bytes = ByteBuffer.allocate(size).putLong(operation.getMostSignificantBits()).putLong(operation.getLeastSignificantBits())
				.putLong(profileId).putInt(actorId).putInt(clanId).putInt(baseClassId).putInt(itemObjectId).putLong(itemBefore).putLong(spBefore);
			if (version >= 2) { bytes.putInt(currentLevel()).putLong(feeCount()).putLong(spCost()); }
			if (version >= 3) { bytes.putInt(feeItemId()); }
			if (version == 4)
			{
				bytes.putInt(reputationBefore).putInt(requirement.reputation()).putInt(requirement.members())
					.putInt(requirement.territory() ? 1 : 0).putInt(territoryCastleId);
			}
			if (pledge != null)
			{
				bytes.putInt(reputationBefore).putInt(pledge.reputation()).putInt(pledge.skillId()).putInt(pledge.rank());
			}
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
		private final int _originalDurableReputation;
		private DurableOutcome _reputationReadback;
		private int _lastDurableReputation;
		private int _attemptedReputationSave;

		private PendingPayment(Clan clan, Item item, Payment payment, long originalDurableSp)
		{
			this(clan, item, payment, originalDurableSp, 0);
		}

		PendingPayment(Clan clan, Item item, Payment payment, long originalDurableSp, int originalDurableReputation)
		{
			_clan = clan;
			_item = item;
			_payment = payment;
			_originalDurableSp = originalDurableSp;
			_originalDurableReputation = originalDurableReputation;
			_lastDurableReputation = originalDurableReputation;
			_attemptedReputationSave = originalDurableReputation;
		}
	}

	private ClanFoundingDevelopment() { }

	public static boolean supportsLevel(int currentLevel)
	{
		return currentLevel >= 0 && currentLevel < 11;
	}

	/** Multiple exact fee stacks can survive a full-debit rollback with a concurrent native reward. */
	public static boolean isItemFee(int itemId)
	{
		return FEE_ITEM_IDS.contains(itemId) || PLEDGE_FEE_ITEM_IDS.contains(itemId);
	}

	public static Item findItemFee(Player leader, int itemId)
	{
		return leader.getInventory().getAllItemsByItemId(itemId).stream()
			.filter(item -> item.getOwnerId() == leader.getObjectId() && item.getItemLocation() == ItemLocation.INVENTORY && !item.blocksInventoryDebit())
			.max(Comparator.comparingLong(Item::getCount).thenComparingInt(Item::getObjectId)).orElse(null);
	}

	static PledgeTerms pledgeTerms(int clanLevel, SkillLearn learn)
	{
		if (learn == null || learn.isResidencialSkill() || clanLevel < 5 || clanLevel < learn.getGetLevel()
			|| learn.getSkillLevel() <= 0 || learn.getLevelUpSp() < 0) { return null; }
		final var items = learn.getRequiredItems();
		if (!PlayerConfig.LIFE_CRYSTAL_NEEDED || items.isEmpty())
		{
			return new PledgeTerms(clanLevel, learn.getSkillId(), learn.getSkillLevel(), learn.getLevelUpSp(), 0, 0);
		}
		if (items.size() != 1) { return null; }
		final var item = items.iterator().next();
		return PLEDGE_FEE_ITEM_IDS.contains(item.getId()) && item.getCount() > 0
			? new PledgeTerms(clanLevel, learn.getSkillId(), learn.getSkillLevel(), learn.getLevelUpSp(), item.getId(), item.getCount()) : null;
	}

	private static boolean pledgeReady(Clan clan, PledgeTerms offer)
	{
		if (offer == null || offer.reputation() > clan.getReputationScore() / 2) { return false; }
		final Skill previous = clan.getSkills().get(offer.skillId());
		final int rank = previous == null ? 0 : previous.getLevel();
		return rank + 1 == offer.rank() && offer.equals(pledgeTerms(clan.getLevel(), SkillTreeData.getInstance().getPledgeSkill(offer.skillId(), offer.rank())));
	}

	public static PledgeTerms choosePledgeSkill(Player leader)
	{
		final Clan clan = leader.getClan();
		if (clan == null) { return null; }
		synchronized (clan)
		{
			if (clan.getLeaderId() != leader.getObjectId() || clan.firstLevelPaymentPending() || clan.getDissolvingExpiryTime() != 0)
			{
				return null;
			}
			PledgeTerms best = null;
			int bestClass = Integer.MAX_VALUE;
			for (SkillLearn learn : SkillTreeData.getInstance().getAvailablePledgeSkills(clan))
			{
				final PledgeTerms offer = pledgeTerms(clan.getLevel(), learn);
				if (!pledgeReady(clan, offer)) { continue; }
				final Skill skill = SkillData.getInstance().getSkill(offer.skillId(), offer.rank());
				if (skill == null) { continue; }
				if (offer.itemId() != 0)
				{
					final Item fee = findItemFee(leader, offer.itemId());
					if (fee == null || fee.getCount() < offer.itemCount()) { continue; }
				}
				final int pledgeClass = Clan.minimumOrdinarySkillPledgeClass(skill);
				if (best == null || pledgeClass < bestClass || pledgeClass == bestClass
					&& Comparator.comparingInt(PledgeTerms::rank).thenComparingInt(PledgeTerms::reputation)
						.thenComparingInt(PledgeTerms::skillId).compare(offer, best) < 0)
				{
					best = offer;
					bestClass = pledgeClass;
				}
			}
			return best;
		}
	}

	public static Outcome payForPledgeSkill(long profileId, Player leader, VillageMaster npc, int expectedClanId, PledgeTerms offer,
		long expiresAt, BooleanSupplier preparationOrAuthorityChanged)
	{
		return payForPledgeSkill(profileId, leader, npc, expectedClanId, offer, expiresAt, preparationOrAuthorityChanged, () -> true);
	}

	public static Outcome payForPledgeSkill(long profileId, Player leader, VillageMaster npc, int expectedClanId, PledgeTerms offer,
		long expiresAt, BooleanSupplier preparationOrAuthorityChanged, BooleanSupplier localExecution)
	{
		if (profileId <= 0 || leader == null || !leader.hasHeadlessOutboundSession() || leader.isDedicatedGeoScout()
			|| !authorizedAction(leader, profileId) || offer == null) { return Outcome.NOT_READY; }
		final Clan clan = leader.getClan();
		if (clan == null || clan.getId() != expectedClanId) { return Outcome.NOT_READY; }
		if (leader.getPendingClanDevelopment() != null) { return settlePending(leader) ? Outcome.DEFERRED : Outcome.UNCONFIRMED; }
		if (preparationOrAuthorityChanged.getAsBoolean()) { return Outcome.NOT_READY; }
		if (offer.itemId() == 0)
		{
			return payForPledgeSkillWithClaim(profileId, leader, clan, npc, offer, expiresAt, null, List.of(), localExecution);
		}
		final Item fee = findItemFee(leader, offer.itemId());
		if (fee == null) { return Outcome.NOT_READY; }
		final long before = fee.getCount();
		if (before < offer.itemCount()) { return Outcome.NOT_READY; }
		final var resources = List.of(new Reservation(profileId, leader.getObjectId(), 0, ResourceKind.ITEM_OBJECT,
			fee.getObjectId(), fee.getId(), offer.itemCount(), before, fee.getEnchantLevel(), ItemLocation.INVENTORY.name()));
		try (var claim = PhantomEconomyConflictPort.claim(profileId, null, resources))
		{
			return claim.acquired() ? payForPledgeSkillWithClaim(profileId, leader, clan, npc, offer, expiresAt, fee, resources, localExecution) : Outcome.NOT_READY;
		}
	}

	private static Outcome payForPledgeSkillWithClaim(long profileId, Player leader, Clan clan, VillageMaster npc, PledgeTerms offer,
		long expiresAt, Item fee, List<Reservation> resources, BooleanSupplier localExecution)
	{
		final var fence = ClanSocialMutationFence.getInstance();
		return fence.execute(new long[] { ClanSocialMutationFence.clanKey(clan.getId()) }, () ->
		{
			synchronized (leader)
			{
				synchronized (clan)
				{
					if (leader.getPendingClanDevelopment() != null) { return settleLocked(leader); }
					final BooleanSupplier eligible = () -> System.currentTimeMillis() < expiresAt && !fence.isRetiring(clan.getId()) && localExecution.getAsBoolean()
						&& eligibleActor(leader, clan, npc, offer.clanLevel())
						&& pledgeReady(clan, offer);
					if (clan.firstLevelPaymentPending() || !eligible.getAsBoolean()) { return Outcome.NOT_READY; }
					final int reputation = clan.getReputationScore();
					if (fee == null)
					{
						final var payment = new Payment(profileId, leader.getObjectId(), clan.getId(), leader.getActiveClass(), 0, 0, UUID.randomUUID(), offer, reputation);
						return payReputationLocked(leader, clan, null, payment, eligible, resources);
					}
					synchronized (fee)
					{
						if (fee.getCount() != resources.getFirst().expectedCount() || fee.blocksInventoryDebit()
							|| leader.getInventory().getItemByObjectId(fee.getObjectId()) != fee) { return Outcome.NOT_READY; }
						final var payment = new Payment(profileId, leader.getObjectId(), clan.getId(), leader.getActiveClass(),
							fee.getObjectId(), fee.getCount(), UUID.randomUUID(), offer, reputation);
						return payReputationLocked(leader, clan, fee, payment, eligible, resources);
					}
				}
			}
		});
	}

	public static Outcome payForFirstLevel(long profileId, Player leader, VillageMaster npc, BooleanSupplier mandatoryPreparationPending)
	{
		return payForLevel(profileId, leader, npc, 0, Long.MAX_VALUE, mandatoryPreparationPending);
	}

	public static Outcome payForLevel(long profileId, Player leader, VillageMaster npc, int expectedLevel, long expiresAt, BooleanSupplier mandatoryPreparationPending)
	{
		return payForLevel(profileId, leader, npc, expectedLevel, 0, expiresAt, mandatoryPreparationPending, () -> true);
	}

	public static Outcome payForLevel(long profileId, Player leader, VillageMaster npc, int expectedLevel, long adenaReserve,
		long expiresAt, BooleanSupplier mandatoryPreparationPending, BooleanSupplier localExecution)
	{
		if (profileId <= 0 || leader == null || !leader.hasHeadlessOutboundSession() || leader.isDedicatedGeoScout() || !authorizedAction(leader, profileId)) { return Outcome.NOT_READY; }
		final Clan clan = leader.getClan();
		final Clan.LevelRequirement requirement = Clan.levelRequirement(expectedLevel);
		if (clan == null || !supportsLevel(expectedLevel)) { return Outcome.NOT_READY; }
		if (leader.getPendingClanDevelopment() != null) { return settlePending(leader) ? Outcome.DEFERRED : Outcome.UNCONFIRMED; }
		if (adenaReserve < 0 || mandatoryPreparationPending.getAsBoolean()) { return Outcome.NOT_READY; }
		final var fence = ClanSocialMutationFence.getInstance();
		return fence.execute(new long[] { ClanSocialMutationFence.clanKey(clan.getId()) }, () ->
		{
			synchronized (leader)
			{
				synchronized (clan)
				{
					if (leader.getPendingClanDevelopment() != null) { return settleLocked(leader); }
					final int castleId = requirement.territory() ? clan.getCastleId() : 0;
					final BooleanSupplier eligible = () -> System.currentTimeMillis() < expiresAt && !fence.isRetiring(clan.getId())
						&& localExecution.getAsBoolean() && requirement.equals(Clan.levelRequirement(expectedLevel))
						&& (!requirement.territory() || clan.ownsTerritory(castleId)) && eligible(leader, clan, npc, requirement);
					if (clan.firstLevelPaymentPending() || !authorizedAction(leader, profileId) || !eligible.getAsBoolean()) { return Outcome.NOT_READY; }
					final int reputation = clan.getReputationScore();
					if (expectedLevel >= 5 && requirement.itemId() == 0)
					{
						final var payment = new Payment(profileId, leader.getObjectId(), clan.getId(), leader.getActiveClass(),
							0, 0, 0, UUID.randomUUID(), requirement, reputation, castleId);
						return payReputationLocked(leader, clan, null, payment, eligible, List.of());
					}
					final Item fee = requirement.itemId() == 0 ? leader.getInventory().getAdenaInstance() : findItemFee(leader, requirement.itemId());
					if (fee == null) { return Outcome.NOT_READY; }
					synchronized (fee)
					{
						final var payment = new Payment(profileId, leader.getObjectId(), clan.getId(), leader.getActiveClass(),
							fee.getObjectId(), fee.getCount(), expectedLevel < 5 ? leader.getSp() : 0, UUID.randomUUID(), requirement, reputation);
						if (!eligible.getAsBoolean() || fee.getId() != payment.feeItemId() || fee.getOwnerId() != leader.getObjectId()
							|| payment.itemAfter() < 0 || !payment.keepsAdenaReserve(adenaReserve)) { return Outcome.NOT_READY; }
						return payment.reputationTier() ? payReputationLocked(leader, clan, fee, payment, eligible, List.of())
							: payLocked(leader, clan, fee, payment, adenaReserve, eligible);
					}
				}
			}
		});
	}

	static boolean authorizedAction(Player player, long profileId)
	{
		if (!(player.getNativeWorkOwner() instanceof PhantomNativeWorkScope owner) || owner.player() != player || !owner.isCurrent()) { return false; }
		final var execution = owner.currentExecutionStamp();
		return execution != null && execution.profileId() == profileId;
	}

	private static boolean eligible(Player leader, Clan clan, VillageMaster npc, Clan.LevelRequirement requirement)
	{
		return eligibleActor(leader, clan, npc, requirement.currentLevel())
			&& clan.getMembersCount() >= (requirement.currentLevel() == 0 ? 2 : requirement.members())
			&& (requirement.currentLevel() < 5 || clan.getReputationScore() >= requirement.reputation())
			&& (!requirement.territory() || clan.ownsTerritory())
			&& leader.getSp() >= requirement.sp();
	}

	private static boolean eligibleActor(Player leader, Clan clan, VillageMaster npc, int clanLevel)
	{
		return ClanTable.getInstance().getClan(clan.getId()) == clan && leader.getClan() == clan && clan.getLeaderId() == leader.getObjectId()
			&& clan.getLevel() == clanLevel && clan.getDissolvingExpiryTime() == 0
			&& !leader.isAlikeDead() && !leader.isInCombat() && !leader.isChangingClass() && leader.getClassIndex() == 0 && leader.getActiveClass() == leader.getBaseClass()
			&& leader.getParty() == null && !leader.isAttackingNow() && !leader.isCastingNow() && !leader.isMoving() && !leader.isTeleporting()
			&& !leader.isProcessingTransaction() && !leader.isInventoryDisabled() && !leader.hasPendingOwnedStore()
			&& leader.getPendingClanPurchase() == null && leader.getPendingClanDeparture() == null && leader.getPendingClanWarehouse() == null
			&& npc != null && npc.getInstanceId() == leader.getInstanceId() && npc.isSpawned() && !npc.isAlikeDead() && npc.canInteract(leader)
			&& GeoEngine.getInstance().canSeeTarget(leader, npc);
	}

	private static Outcome payLocked(Player leader, Clan clan, Item fee, Payment payment, long adenaReserve, BooleanSupplier eligible)
	{
		try
		{
			fee.transactRecoverableInventoryDebit(payment.actorId(), payment.itemBefore(), payment.feeCount(), connection ->
			{
				final long durableBefore = stage(connection, payment, leader.getStat()::matchesNativeSkillPointCredit, adenaReserve,
					() -> authorizedAction(leader, payment.profileId()) && eligible.getAsBoolean());
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

	private static Outcome payReputationLocked(Player leader, Clan clan, Item fee, Payment payment, BooleanSupplier eligible, List<Reservation> resources)
	{
		final Item.CountTransaction<Void> stage = connection ->
		{
			final int stored = stageReputation(connection, payment, clan.storedReputationScore());
			if (!authorizedAction(leader, payment.profileId()) || !eligible.getAsBoolean())
			{
				throw new SQLException("Native clan payment authority changed before commit.");
			}
			clan.beginReputationResolution(payment.reputationBefore(), payment.reputationAfter());
			leader.setPendingClanDevelopment(new PendingPayment(clan, fee, payment, 0, stored));
			clan.setFirstLevelPaymentPending(true);
			return null;
		};
		try
		{
			if (fee != null)
			{
				fee.transactRecoverableInventoryDebit(payment.actorId(), payment.itemBefore(), payment.feeCount(), connection ->
				{
					requireOrganizationOwner(connection, payment.profileId(), payment.actorId());
					if (!resources.isEmpty()) { PhantomEconomyConflictPort.requireNoConflict(connection, null, resources); }
					return null;
				}, stage);
			}
			else
			{
				try (Connection connection = DatabaseFactory.getConnection())
				{
					connection.setAutoCommit(false);
					try
					{
						stage.run(connection);
						connection.commit();
					}
					catch (Exception failure)
					{
						try { connection.rollback(); } catch (Exception rollback) { failure.addSuppressed(rollback); }
						throw failure;
					}
				}
			}
		}
		catch (Exception failure)
		{
			if (leader.getPendingClanDevelopment() == null) { return Outcome.DEFERRED; }
			LOGGER.log(Level.WARNING, "Clan reputation payment requires native readback for character " + payment.actorId(), failure);
			return settleLocked(leader);
		}
		publishPaid(leader, leader.getPendingClanDevelopment());
		return Outcome.APPLIED;
	}

	static void requireOrganizationOwner(Connection connection, long profileId, int actorId) throws SQLException
	{
		try (var profile = connection.prepareStatement("SELECT p.character_object_id FROM phantom_profiles p INNER JOIN phantom_profile_components c ON c.profile_id=p.profile_id AND c.component_type=? WHERE p.profile_id=? FOR UPDATE"))
		{
			profile.setString(1, PhantomClanStore.COMPONENT_TYPE);
			profile.setLong(2, profileId);
			try (var rows = profile.executeQuery())
			{
				if (!rows.next() || rows.getInt(1) != actorId) { throw new SQLException("Missing exact clan organization owner."); }
			}
		}
	}

	static long stage(Connection connection, Payment payment, LongPredicate witnessedCredit) throws SQLException
	{
		return stage(connection, payment, witnessedCredit, 0, () -> true);
	}

	static long stage(Connection connection, Payment payment, LongPredicate witnessedCredit, long adenaReserve, BooleanSupplier eligible) throws SQLException
	{
		requireOrganizationOwner(connection, payment.profileId(), payment.actorId());
		lockReceipt(connection, payment);
		final long durableSp = readSp(connection, payment, true);
		if (payment.itemAfter() < 0 || payment.spAfter() < 0 || (durableSp != payment.spBefore() && !witnessedCredit.test(durableSp)))
		{
			throw new SQLException("Native clan payment preimage changed.");
		}
		if (!payment.keepsAdenaReserve(adenaReserve)) { throw new SQLException("Native clan payment would spend the personal Adena reserve."); }
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
		writeReceipt(connection, payment);
		if (!eligible.getAsBoolean()) { throw new SQLException("Native clan payment authority changed before commit."); }
		return durableSp;
	}

	private static void lockReceipt(Connection connection, Payment payment) throws SQLException
	{
		try (var receipt = connection.prepareStatement("SELECT component_schema_version FROM phantom_profile_components WHERE profile_id=? AND component_type=? FOR UPDATE"))
		{
			receipt.setLong(1, payment.profileId());
			receipt.setString(2, payment.componentType());
			try (var rows = receipt.executeQuery())
			{
				if (rows.next() && rows.getInt(1) != payment.version()) { throw new SQLException("Unsupported clan payment receipt version."); }
			}
		}
	}

	private static void writeReceipt(Connection connection, Payment payment) throws SQLException
	{
		try (var receipt = connection.prepareStatement("INSERT INTO phantom_profile_components (profile_id,component_type,component_schema_version,payload) VALUES (?,?,?,?) ON DUPLICATE KEY UPDATE component_schema_version=VALUES(component_schema_version), payload=VALUES(payload), row_version=row_version+1"))
		{
			receipt.setLong(1, payment.profileId());
			receipt.setString(2, payment.componentType());
			receipt.setInt(3, payment.version());
			receipt.setBytes(4, payment.evidence());
			receipt.executeUpdate();
		}
	}

	static int stageReputation(Connection connection, Payment payment, int witnessedReputation) throws SQLException
	{
		final var pledge = payment.pledge();
		if (!payment.reputationTier() || payment.reputationCost() < 0 || payment.reputationAfter() < 0 || payment.itemAfter() < 0
			|| (payment.itemObjectId() == 0) != (payment.feeItemId() == 0))
		{
			throw new SQLException("Invalid native reputation payment.");
		}
		requireOrganizationOwner(connection, payment.profileId(), payment.actorId());
		lockReceipt(connection, payment);
		readSp(connection, payment, true); // The durable actor still owns the clan and the base class.
		if (payment.requiredMembers() > 0)
		{
			try (var query = connection.prepareStatement("SELECT charId FROM characters WHERE clanid=? FOR UPDATE"))
			{
				query.setInt(1, payment.clanId());
				try (var members = query.executeQuery())
				{
					int count = 0;
					while (count < payment.requiredMembers() && members.next()) { count++; }
					if (count < payment.requiredMembers()) { throw new SQLException("Native clan membership is below the required count."); }
				}
			}
		}
		final int stored;
		try (var query = connection.prepareStatement("SELECT reputation_score,hasCastle FROM clan_data WHERE clan_id=? AND clan_level=? AND leader_id=? AND dissolving_expiry_time=0 FOR UPDATE"))
		{
			query.setInt(1, payment.clanId());
			query.setInt(2, payment.currentLevel());
			query.setInt(3, payment.actorId());
			try (var row = query.executeQuery())
			{
				if (!row.next()) { throw new SQLException("Native clan leadership or level changed."); }
				if (payment.needsTerritory() && (payment.territoryCastleId() <= 0 || row.getInt(2) != payment.territoryCastleId()))
				{
					throw new SQLException("Native territory ownership changed.");
				}
				stored = row.getInt(1);
			}
		}
		if (stored != witnessedReputation && stored != payment.reputationBefore()) { throw new SQLException("Native reputation preimage changed."); }
		if (pledge != null)
		{
			final var skill = SkillData.getInstance().getSkill(pledge.skillId(), pledge.rank());
			if (skill == null || !pledge.equals(pledgeTerms(payment.currentLevel(), SkillTreeData.getInstance().getPledgeSkill(pledge.skillId(), pledge.rank()))))
			{
				throw new SQLException("Native clan skill terms changed.");
			}
			if (Clan.readOrdinarySkillRank(connection, payment.clanId(), pledge.skillId()) != pledge.rank() - 1)
			{
				throw new SQLException("Native clan skill rank changed.");
			}
			Clan.storeSkillRank(connection, payment.clanId(), skill, -2, pledge.rank() - 1);
		}
		if (payment.currentLevel() != payment.targetLevel() || stored != payment.reputationAfter())
		{
			try (var update = connection.prepareStatement("UPDATE clan_data SET clan_level=?,reputation_score=? WHERE clan_id=? AND clan_level=? AND leader_id=? AND reputation_score=? AND dissolving_expiry_time=0"))
			{
				update.setInt(1, payment.targetLevel());
				update.setInt(2, payment.reputationAfter());
				update.setInt(3, payment.clanId());
				update.setInt(4, payment.currentLevel());
				update.setInt(5, payment.actorId());
				update.setInt(6, stored);
				if (update.executeUpdate() != 1) { throw new SQLException("Could not publish the exact native clan payment."); }
			}
		}
		writeReceipt(connection, payment);
		return stored;
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
		synchronized (pending._clan)
		{
			if (pending._item == null) { return settleReputationLocked(leader, pending); }
			synchronized (pending._item)
			{
				return pending._payment.reputationTier() ? settleReputationLocked(leader, pending) : settleItemLocked(leader, pending);
			}
		}
	}

	private static Outcome settleReputationLocked(Player leader, PendingPayment pending)
	{
		final var payment = pending._payment;
		final int before = pending._originalDurableReputation;
		if (pending._reputationReadback == null)
		{
			final DurableOutcome durable;
			try
			{
				if (pending._item == null)
				{
					try (Connection connection = DatabaseFactory.getConnection())
					{
						connection.setAutoCommit(false);
						durable = readReputation(connection, payment, 0, before);
						connection.commit();
					}
				}
				else
				{
					if (leader.getInventory().getItemByObjectId(payment.itemObjectId()) != pending._item) { return Outcome.UNCONFIRMED; }
					durable = pending._item.reconcileCountTransaction(payment.actorId(), payment.itemBefore(), payment.itemAfter(), (connection, count) ->
					{
						final var outcome = readReputation(connection, payment, count, before);
						return outcome == DurableOutcome.CONFLICT ? null : outcome;
					});
				}
			}
			catch (Exception unavailable) { return Outcome.UNCONFIRMED; }
			if (durable == null || durable == DurableOutcome.CONFLICT) { return Outcome.UNCONFIRMED; }
			pending._reputationReadback = durable;
			pending._lastDurableReputation = durable == DurableOutcome.APPLIED ? payment.reputationAfter() : before;
			pending._attemptedReputationSave = pending._lastDurableReputation;
		}
		final boolean applied = pending._reputationReadback == DurableOutcome.APPLIED;
		final int latest = pending._clan.reputationForResolution(applied, pending._lastDurableReputation);
		if (!saveLateReputation(pending, latest, applied)) { return Outcome.UNCONFIRMED; }
		if (applied)
		{
			publishPaid(leader, pending);
			return Outcome.APPLIED;
		}
		pending._clan.resolveReputationPayment(false, latest);
		clearPending(leader, pending);
		return Outcome.DEFERRED;
	}

	static boolean saveLateReputation(PendingPayment pending, int latest, boolean applied)
	{
		if (latest == pending._lastDurableReputation && latest == pending._attemptedReputationSave) { return true; }
		final var payment = pending._payment;
		final int level = applied ? payment.targetLevel() : payment.currentLevel();
		try (Connection connection = DatabaseFactory.getConnection())
		{
			connection.setAutoCommit(false);
			final int stored;
			try (var query = connection.prepareStatement("SELECT reputation_score FROM clan_data WHERE clan_id=? AND leader_id=? AND clan_level=? FOR UPDATE"))
			{
				query.setInt(1, payment.clanId());
				query.setInt(2, payment.actorId());
				query.setInt(3, level);
				try (var row = query.executeQuery())
				{
					if (!row.next()) { return false; }
					stored = row.getInt(1);
				}
			}
			if (stored != pending._lastDurableReputation && stored != pending._attemptedReputationSave) { return false; }
			pending._lastDurableReputation = stored;
			pending._attemptedReputationSave = latest;
			if (stored != latest)
			{
				try (var update = connection.prepareStatement("UPDATE clan_data SET reputation_score=? WHERE clan_id=? AND leader_id=? AND clan_level=? AND reputation_score=?"))
				{
					update.setInt(1, latest);
					update.setInt(2, payment.clanId());
					update.setInt(3, payment.actorId());
					update.setInt(4, level);
					update.setInt(5, stored);
					if (update.executeUpdate() != 1) { return false; }
				}
			}
			connection.commit();
			pending._lastDurableReputation = latest;
			return true;
		}
		catch (SQLException unavailable) { return false; }
	}

	static DurableOutcome readReputation(Connection connection, Payment payment, long count, int originalDurableReputation) throws SQLException
	{
		try (var query = connection.prepareStatement("SELECT clan_level,reputation_score FROM clan_data WHERE clan_id=? AND leader_id=? FOR UPDATE"))
		{
			query.setInt(1, payment.clanId());
			query.setInt(2, payment.actorId());
			try (var row = query.executeQuery())
			{
				if (!row.next()) { return DurableOutcome.CONFLICT; }
				final int level = row.getInt(1);
				final int reputation = row.getInt(2);
				final boolean receipt = hasReceipt(connection, payment);
				final var pledge = payment.pledge();
				final int rank = pledge == null ? 0 : Clan.readOrdinarySkillRank(connection, payment.clanId(), pledge.skillId());
				if (receipt && level == payment.targetLevel() && reputation == payment.reputationAfter() && count == payment.itemAfter()
					&& (pledge == null || rank == pledge.rank()))
				{
					return DurableOutcome.APPLIED;
				}
				if (!receipt && level == payment.currentLevel() && reputation == originalDurableReputation && count == payment.itemBefore()
					&& (pledge == null || rank == pledge.rank() - 1))
				{
					return DurableOutcome.ROLLED_BACK;
				}
				return DurableOutcome.CONFLICT;
			}
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
		final boolean receipt = hasReceipt(connection, payment);
		if (receipt && level == payment.requirement().nextLevel() && count == payment.itemAfter() && sp == payment.spAfter()) { return DurableOutcome.APPLIED; }
		if (!receipt && level == payment.currentLevel() && count == payment.itemBefore() && sp == originalDurableSp) { return DurableOutcome.ROLLED_BACK; }
		return DurableOutcome.CONFLICT;
	}

	private static boolean hasReceipt(Connection connection, Payment payment) throws SQLException
	{
		try (var query = connection.prepareStatement("SELECT component_schema_version,payload FROM phantom_profile_components WHERE profile_id=? AND component_type=? FOR UPDATE"))
		{
			query.setLong(1, payment.profileId());
			query.setString(2, payment.componentType());
			try (var rows = query.executeQuery())
			{
				return rows.next() && rows.getInt(1) == payment.version() && Arrays.equals(rows.getBytes(2), payment.evidence());
			}
		}
	}

	private static void publishPaid(Player leader, PendingPayment pending)
	{
		final var payment = pending._payment;
		final Clan clan = pending._clan;
		if (pending._payment.reputationTier())
		{
			final int durable = pending._reputationReadback == null ? pending._payment.reputationAfter() : pending._lastDurableReputation;
			clan.resolveReputationPayment(true, durable);
		}
		else
		{
			leader.setSp(leader.getSp() - payment.spCost());
			leader.getStat().acknowledgeStoredSkillPoints(0, pending._payment.spAfter());
		}
		final boolean removed;
		try
		{
			removed = payment.currentLevel() >= 2 && payment.feeItemId() > 0 && leader.getInventory().publishCommittedStackDebit(pending._item, leader);
			if (ClanTable.getInstance().getClan(clan.getId()) == clan)
			{
				if (payment.pledge() == null) { clan.publishPaidLevel(leader, payment.requirement()); }
				else { clan.publishPaidPledgeSkill(payment.pledge().skillId(), payment.pledge().rank()); }
			}
		}
		finally { clearPending(leader, pending); }
		if (pending._item != null)
		{
			final var inventory = new InventoryUpdate();
			if (removed) { inventory.addRemovedItem(pending._item); }
			else { inventory.addModifiedItem(pending._item); }
			leader.sendInventoryUpdate(inventory);
		}
		if (payment.currentLevel() >= 2 && payment.feeItemId() > 0)
		{
			final var message = new SystemMessage(pending._payment.reputationTier() ? SystemMessageId.S2_S1_HAS_DISAPPEARED : SystemMessageId.S1_HAS_DISAPPEARED)
				.addItemName(payment.feeItemId());
			if (pending._payment.reputationTier()) { message.addLong(payment.feeCount()); }
			leader.sendPacket(message);
		}
		if (pending._payment.reputationTier())
		{
			leader.sendPacket(new SystemMessage(SystemMessageId.S1_POINTS_HAVE_BEEN_DEDUCTED_FROM_THE_CLAN_S_REPUTATION_SCORE).addInt(payment.reputationCost()));
		}
		else
		{
			final var sp = new StatusUpdate(leader);
			sp.addAttribute(StatusUpdate.SP, (int) leader.getSp());
			leader.sendPacket(sp);
			leader.sendPacket(new SystemMessage(SystemMessageId.YOUR_SP_HAS_DECREASED_BY_S1).addInt((int) payment.spCost()));
		}
		if (payment.pledge() == null)
		{
			leader.broadcastSkillPacket(new MagicSkillUse(leader, 5103, 1, 0, 0), leader);
			leader.broadcastSkillPacket(new MagicSkillLaunched(leader, 5103, 1), leader);
		}
	}

	private static void clearPending(Player player, PendingPayment pending)
	{
		pending._clan.setFirstLevelPaymentPending(false);
		player.setPendingClanDevelopment(null);
	}
}
