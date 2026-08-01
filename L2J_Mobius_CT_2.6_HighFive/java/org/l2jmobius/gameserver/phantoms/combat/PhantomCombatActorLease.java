/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.combat;

import java.util.List;

import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.ActionOutcome;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.AcquisitionSkillKind;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.AcquisitionTargetSnapshot;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.AcquisitionActorPosition;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.ActorSnapshot;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.LootCandidate;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.LootObservation;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.ExternalOwnedAction;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.PlayableSnapshot;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.RespawnOutcome;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.ShotOutcome;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.TargetSnapshot;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.ThreatObservation;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatLoadout.SelectedSkill;

public interface PhantomCombatActorLease extends AutoCloseable
{
	ActorSnapshot actorSnapshot();

	TargetSnapshot targetSnapshot(int targetObjectId);

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

	default PlayableSnapshot playableSnapshot(int objectId)
	{
		return null;
	}

	boolean supportsSkill(SelectedSkill skill, PhantomCombatMode mode);

	List<ThreatObservation> observedAttackers(int limit);

	default List<ThreatObservation> observedAttackers(int protectedObjectId, int limit)
	{
		return List.of();
	}

	List<LootCandidate> lootCandidates(int limit, int maximumDistance);

	LootObservation observeLoot(LootCandidate candidate);

	ShotOutcome activateShot(PhantomCombatMode mode);

	ActionOutcome attack(int targetObjectId);

	ActionOutcome cast(int targetObjectId, SelectedSkill skill, PhantomCombatMode mode);

	default ActionOutcome castAcquisition(int targetObjectId, SelectedSkill skill, AcquisitionSkillKind kind)
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
