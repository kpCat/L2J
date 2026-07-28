/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.combat;

import java.util.List;

import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.ActionOutcome;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.ActorSnapshot;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.LootCandidate;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.LootObservation;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.RespawnOutcome;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.ShotOutcome;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.TargetSnapshot;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.ThreatObservation;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatLoadout.SelectedSkill;

public interface PhantomCombatActorLease extends AutoCloseable
{
	ActorSnapshot actorSnapshot();

	TargetSnapshot targetSnapshot(int targetObjectId);

	boolean supportsSkill(SelectedSkill skill, PhantomCombatMode mode);

	List<ThreatObservation> observedAttackers(int limit);

	List<LootCandidate> lootCandidates(int limit, int maximumDistance);

	LootObservation observeLoot(LootCandidate candidate);

	ShotOutcome activateShot(PhantomCombatMode mode);

	ActionOutcome attack(int targetObjectId);

	ActionOutcome cast(int targetObjectId, SelectedSkill skill, PhantomCombatMode mode);

	ActionOutcome pickUp(int objectId);

	void cancelOwnedAction(PhantomOwnedAction action);

	RespawnOutcome respawnTown();

	@Override
	void close();
}
