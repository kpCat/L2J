/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.combat;

import java.util.HashSet;
import java.util.Set;

import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatLoadout.SelectedSkill;

public final class PhantomCombatSession
{
	final PhantomCombatRequest _request;
	final long _generation;
	final long _startedLogicalNanos;
	final PhantomCombatThreatTable _threatTable;
	final Set<Integer> _rememberedLootIds = new HashSet<>();
	PhantomCombatActorLease _actorLease;
	PhantomCombatLoadout _loadout;
	PhantomCombatPhase _phase = PhantomCombatPhase.RESERVED;
	PhantomCombatResult _result = PhantomCombatResult.ACTIVE;
	long _lastPulseLogicalNanos;
	long _lootStartedLogicalNanos;
	int _nextSkill;
	int _lastLootObjectId;
	int _lootPickupsIssued;
	int _lootPickupsObserved;
	SelectedSkill _ownedSkill;
	PhantomCombatActorLease _deferredCleanupLease;
	boolean _startInProgress = true;
	boolean _processing;
	boolean _cleanupPending;
	boolean _metricsCounted;

	PhantomCombatSession(PhantomCombatRequest request, long generation, long startedLogicalNanos, int maximumThreatEntries)
	{
		_request = request;
		_generation = generation;
		_startedLogicalNanos = startedLogicalNanos;
		_lastPulseLogicalNanos = startedLogicalNanos;
		_threatTable = new PhantomCombatThreatTable(maximumThreatEntries);
	}

	PhantomCombatSessionSnapshot snapshot()
	{
		return new PhantomCombatSessionSnapshot(_request.profileId(), _generation, _request.targetObjectId(), _request.mode(), _phase, _result, _startedLogicalNanos, _lastPulseLogicalNanos, _loadout == null ? 0 : _loadout.selectedSkills().size(), _threatTable.size(), _rememberedLootIds.size(), _lootPickupsIssued);
	}
}
