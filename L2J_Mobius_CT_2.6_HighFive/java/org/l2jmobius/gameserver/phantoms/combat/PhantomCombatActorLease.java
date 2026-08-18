/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.combat;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.ActionOutcome;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.AcquisitionSkillKind;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.AcquisitionTargetSnapshot;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.AcquisitionActorPosition;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.ActorSnapshot;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.CpPotionSnapshot;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.CpPotionUse;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.PvpConsequenceSnapshot;
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


	default boolean supportsPvpSkill(SelectedSkill skill, PhantomCombatMode mode)
	{
		return false;
	}

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

	default ActionOutcome castPvp(int targetObjectId, SelectedSkill skill, PhantomCombatMode mode, boolean forceUse, String authorityHash)
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
