/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.combat;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.ActionOutcome;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.AcquisitionSkillKind;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.AcquisitionTargetSnapshot;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.AcquisitionActorPosition;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.ActorSnapshot;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.CpPotionSnapshot;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.CpPotionUse;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.PvpConsequenceSnapshot;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.PvpLinkedServitorSnapshot;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.PvpTargetSnapshot;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.PvpLocalSupportSnapshot;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.LootCandidate;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.LootObservation;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.ManorInventorySnapshot;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.ExternalOwnedAction;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.PlayableSnapshot;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.RespawnOutcome;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.QuestStateSnapshot;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.ShotOutcome;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.SiegeTargetSnapshot;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.TargetSnapshot;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.RaidTargetSnapshot;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.ThreatObservation;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatLoadout.SelectedSkill;

public interface PhantomCombatActorLease extends AutoCloseable
{
	int MAX_ACQUISITION_INVENTORY_ITEM_IDS = 128;
	ActorSnapshot actorSnapshot();

	TargetSnapshot targetSnapshot(int targetObjectId);

	default RaidTargetSnapshot raidTargetSnapshot(int targetObjectId)
	{
		return null;
	}

	default int raidActorLevel()
	{
		return 0;
	}

	default PvpTargetSnapshot pvpTargetSnapshot(int targetObjectId)
	{
		return null;
	}

	default PvpLinkedServitorSnapshot pvpLinkedServitorSnapshot(int ownerObjectId)
	{
		return null;
	}

	default int pvpTacticalTargetObjectId(int ownerObjectId, int maximumDistance)
	{
		final PvpTargetSnapshot owner = pvpTargetSnapshot(ownerObjectId);
		final PvpLinkedServitorSnapshot servitor = pvpLinkedServitorSnapshot(ownerObjectId);
		return servitor == null ? ownerObjectId : servitor.preferredTargetObjectId(actorSnapshot(), owner, maximumDistance);
	}

	default SiegeTargetSnapshot siegeTargetSnapshot(int targetObjectId, PhantomSiegeCombatRequest request)
	{
		return null;
	}


	default PvpLocalSupportSnapshot pvpLocalSupport(int targetObjectId, int limit)
	{
		return PvpLocalSupportSnapshot.empty(limit);
	}
	default int pvpLevel()
	{
		return 1;
	}

	default List<ThreatObservation> observedPlayerAttackers(int protectedObjectId, int limit)
	{
		return List.of();
	}

	default PvpConsequenceSnapshot pvpConsequences(int targetObjectId)
	{
		return null;
	}

	default List<CpPotionSnapshot> cpPotions()
	{
		return List.of();
	}

	default CpPotionUse useCpPotion(int itemObjectId, int itemId)
	{
		return null;
	}

	default boolean useHealingPotion() { return false; }

	/** Continue an owned native escape; ALREADY_OWNED means no pursuer remains. */
	default ActionOutcome retreatFrom(int targetObjectId)
	{
		return ActionOutcome.REJECTED;
	}

	default AcquisitionTargetSnapshot acquisitionTargetSnapshot(int targetObjectId)
	{
		return null;
	}

	default List<AcquisitionTargetSnapshot> acquisitionTargets(int npcId, int limit, int maximumDistance)
	{
		return List.of();
	}

	default long acquisitionInventoryCount(int itemId)
	{
		return -1;
	}

	default Map<Integer, Long> acquisitionInventoryCounts(List<Integer> exactItemIds)
	{
		validateAcquisitionInventoryItemIds(exactItemIds);
		final Map<Integer, Long> result = new LinkedHashMap<>();
		for (int itemId : exactItemIds)
		{
			final long count = acquisitionInventoryCount(itemId);
			if (count < 0)
			{
				throw new IllegalStateException("Acquisition inventory count is unavailable.");
			}
			result.put(itemId, count);
		}
		return Map.copyOf(result);
	}

	static void validateAcquisitionInventoryItemIds(List<Integer> exactItemIds)
	{
		if ((exactItemIds == null) || exactItemIds.isEmpty() || (exactItemIds.size() > MAX_ACQUISITION_INVENTORY_ITEM_IDS) || exactItemIds.stream().anyMatch(itemId -> itemId == null || itemId <= 0) || !exactItemIds.equals(exactItemIds.stream().distinct().sorted().toList()))
		{
			throw new IllegalArgumentException("Acquisition inventory item IDs must be positive, unique, sorted and bounded.");
		}
	}

	default int acquisitionLevel()
	{
		return 0;
	}

	default AcquisitionActorPosition acquisitionPosition()
	{
		return null;
	}

	default int knownSkillLevel(int skillId)
	{
		return 0;
	}

	default ManorInventorySnapshot manorInventory(int seedItemId, int cropItemId, int harvesterItemId)
	{
		return null;
	}

	default ActionOutcome useExactSeed(int seedObjectId, int seedItemId, int targetObjectId)
	{
		return ActionOutcome.REJECTED;
	}

	default ActionOutcome useExactHarvester(int harvesterObjectId, int harvesterItemId, int targetObjectId)
	{
		return ActionOutcome.REJECTED;
	}

	default QuestStateSnapshot questState(String questName, List<String> expectedVariables)
	{
		return null;
	}

	default PlayableSnapshot playableSnapshot(int objectId)
	{
		return null;
	}

	boolean supportsSkill(SelectedSkill skill, PhantomCombatMode mode);

	default boolean supportsNormalAttack(PhantomCombatMode mode)
	{
		return mode != PhantomCombatMode.RANGED_MAGIC;
	}

	/** Uses this same actor owner for a bounded legal NPC spacing move, only between actions. */
	default ActionOutcome maintainRangedSpacing(int targetObjectId, SelectedSkill skill, PhantomCombatMode mode)
	{
		return ActionOutcome.UNAVAILABLE;
	}

	/** Current learned single-target NPC aggression only; this observation never writes hate. */
	default Optional<SelectedSkill> npcAggroSkill(int targetObjectId)
	{
		return Optional.empty();
	}

	default ActionOutcome castNpcAggro(int targetObjectId, SelectedSkill skill)
	{
		return ActionOutcome.REJECTED;
	}

	/** The admitted raid owner supplies its exact encounter authority to NPC tactics. */
	default Optional<SelectedSkill> npcRaidAggroSkill(PhantomRaidCombatRequest request)
	{
		return npcAggroSkill(request.targetObjectId());
	}

	default ActionOutcome castNpcRaidAggro(PhantomRaidCombatRequest request, SelectedSkill skill)
	{
		return castNpcAggro(request.targetObjectId(), skill);
	}

	/** Current learned SELF defense under this exact admitted raid authority. */
	default Optional<SelectedSkill> npcRaidDefenseSkill(PhantomRaidCombatRequest request)
	{
		return Optional.empty();
	}

	default ActionOutcome castNpcRaidDefense(PhantomRaidCombatRequest request, SelectedSkill skill)
	{
		return ActionOutcome.REJECTED;
	}


	default boolean supportsPvpSkill(SelectedSkill skill, PhantomCombatMode mode)
	{
		return false;
	}

	/** Learned native actions in tactical order; observations never grant a skill. */
	default List<SelectedSkill> learnedPvpSkills(PhantomCombatMode mode, int limit) { return List.of(); }
	default boolean pvpSkillReady(int targetObjectId, SelectedSkill skill, PhantomCombatMode mode) { return false; }
	default ActionOutcome maintainPvpPosition(int targetObjectId, SelectedSkill skill, PhantomCombatMode mode) { return maintainRangedSpacing(targetObjectId, skill, mode); }

	/** Existing paid stock, including the current charge, sufficient for this many actions. */
	default boolean hasPvpShotReserve(PhantomCombatMode mode, int hits) { return false; }

	/** Exact visible enemies admitted by reciprocal native clan-war data. */
	default List<Integer> declaredWarEnemies(int limit) { return List.of(); }

	List<ThreatObservation> observedAttackers(int limit);

	default List<ThreatObservation> observedAttackers(int protectedObjectId, int limit)
	{
		return List.of();
	}

	List<LootCandidate> lootCandidates(int limit, int maximumDistance);

	LootObservation observeLoot(LootCandidate candidate);

	ShotOutcome activateShot(PhantomCombatMode mode);

	ActionOutcome attack(int targetObjectId);


	default ActionOutcome attackRaid(int targetObjectId, PhantomRaidCombatRequest request)
	{
		return ActionOutcome.REJECTED;
	}

	default ActionOutcome attackPvp(int targetObjectId, String authorityHash)
	{
		return ActionOutcome.REJECTED;
	}

	default ActionOutcome attackPvp(int ownerObjectId, int tacticalTargetObjectId, String authorityHash)
	{
		return attackPvp(tacticalTargetObjectId, authorityHash);
	}

	default ActionOutcome castPvp(int targetObjectId, SelectedSkill skill, PhantomCombatMode mode, boolean forceUse, String authorityHash)
	{
		return ActionOutcome.REJECTED;
	}

	default ActionOutcome castPvp(int ownerObjectId, int tacticalTargetObjectId, SelectedSkill skill, PhantomCombatMode mode, boolean forceUse, String authorityHash)
	{
		return castPvp(tacticalTargetObjectId, skill, mode, forceUse, authorityHash);
	}

	default ActionOutcome attackSiege(int targetObjectId, PhantomSiegeCombatRequest request)
	{
		return ActionOutcome.REJECTED;
	}

	default ActionOutcome castSiege(int targetObjectId, SelectedSkill skill, PhantomSiegeCombatRequest request)
	{
		return ActionOutcome.REJECTED;
	}

	ActionOutcome cast(int targetObjectId, SelectedSkill skill, PhantomCombatMode mode);

	default ActionOutcome castAcquisition(int targetObjectId, SelectedSkill skill, AcquisitionSkillKind kind)
	{
		return ActionOutcome.REJECTED;
	}

	default ActionOutcome castRaid(int targetObjectId, SelectedSkill skill, PhantomRaidCombatRequest request)
	{
		return ActionOutcome.REJECTED;
	}

	default ActionOutcome castSupport(PhantomPartySupportAction action)
	{
		return ActionOutcome.REJECTED;
	}

	default ActionOutcome moveTo(int x, int y, int z, int instanceId)
	{
		return ActionOutcome.REJECTED;
	}

	ActionOutcome pickUp(int objectId);

	void cancelOwnedAction(PhantomOwnedAction action);

	default void cancelExternalAction(ExternalOwnedAction action)
	{
	}

	RespawnOutcome respawnTown();

	@Override
	void close();
}
