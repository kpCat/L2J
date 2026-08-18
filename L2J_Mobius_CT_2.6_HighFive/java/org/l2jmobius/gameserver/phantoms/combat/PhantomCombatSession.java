/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.combat;

import java.util.HashSet;
import java.util.Set;

import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.LootCandidate;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatService.CleanupState;

public final class PhantomCombatSession
{
	final PhantomCombatRequest _request;
	final PhantomPvpCombatRequest _pvpRequest;
	final PhantomRaidCombatRequest _raidRequest;
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
	int _lootPickupsIssued;
	int _lootAcquiredByActor;
	int _lootLostWithoutAcquisition;
	LootCandidate _lootAttempt;
	PhantomOwnedAction _ownedAction;
	boolean _startInProgress = true;
	boolean _processing;
	CleanupState _cleanupState = CleanupState.NONE;
	int _cleanupAttempts;
	int _cleanupFailures;
	boolean _metricsCounted;

	PhantomCombatSession(PhantomCombatRequest request, long generation, long startedLogicalNanos, int maximumThreatEntries)
	{
		_request = request;
		_pvpRequest = null;
		_raidRequest = null;
		_generation = generation;
		_startedLogicalNanos = startedLogicalNanos;
		_lastPulseLogicalNanos = startedLogicalNanos;
		_threatTable = new PhantomCombatThreatTable(maximumThreatEntries);
		_ownedAction = new PhantomOwnedAction(generation, request.targetObjectId(), null, 0);
	}

	PhantomCombatSession(PhantomPvpCombatRequest request, long generation, long startedLogicalNanos, int maximumThreatEntries)
	{
		_pvpRequest = request;
		_raidRequest = null;
		_request = request.leaseRequest();
		_generation = generation;
		_startedLogicalNanos = startedLogicalNanos;
		_lastPulseLogicalNanos = startedLogicalNanos;
		_threatTable = new PhantomCombatThreatTable(maximumThreatEntries);
		_ownedAction = new PhantomOwnedAction(generation, request.targetObjectId(), null, 0);
	}


	PhantomCombatSession(PhantomRaidCombatRequest request, long generation, long startedLogicalNanos, int maximumThreatEntries)
	{
		_raidRequest = request;
		_pvpRequest = null;
		_request = request.leaseRequest();
		_generation = generation;
		_startedLogicalNanos = startedLogicalNanos;
		_lastPulseLogicalNanos = startedLogicalNanos;
		_threatTable = new PhantomCombatThreatTable(maximumThreatEntries);
		_ownedAction = new PhantomOwnedAction(generation, request.targetObjectId(), null, 0);
	}
	PhantomCombatSessionSnapshot snapshot()
	{
		return new PhantomCombatSessionSnapshot(_request.profileId(), _generation, _request.targetObjectId(), _request.mode(), _phase, _result, _startedLogicalNanos, _lastPulseLogicalNanos, _loadout == null ? 0 : _loadout.selectedSkills().size(), _threatTable.size(), _rememberedLootIds.size(), _lootPickupsIssued);
	}
}
