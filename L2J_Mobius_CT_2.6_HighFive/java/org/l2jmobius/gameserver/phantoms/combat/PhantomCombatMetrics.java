/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.combat;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

public final class PhantomCombatMetrics
{
	private final LongAdder _sessionsRequested = new LongAdder();
	private final LongAdder _sessionsAccepted = new LongAdder();
	private final LongAdder _sessionsRejected = new LongAdder();
	private final AtomicInteger _currentSessions = new AtomicInteger();
	private final AtomicInteger _peakSessions = new AtomicInteger();
	private final LongAdder _leasesAcquired = new LongAdder();
	private final LongAdder _leasesRejected = new LongAdder();
	private final LongAdder _leasesReleased = new LongAdder();
	private final AtomicInteger _currentLeases = new AtomicInteger();
	private final LongAdder _targetsAccepted = new LongAdder();
	private final LongAdder _targetsRejected = new LongAdder();
	private final LongAdder _targetsLost = new LongAdder();
	private final LongAdder _pulses = new LongAdder();
	private final LongAdder _workerDispatches = new LongAdder();
	private final LongAdder _dispatchFailures = new LongAdder();
	private final LongAdder _threatObservations = new LongAdder();
	private final LongAdder _threatEvictions = new LongAdder();
	private final LongAdder _normalAttacks = new LongAdder();
	private final LongAdder _skillCastsIssued = new LongAdder();
	private final LongAdder _skillCastsRejected = new LongAdder();
	private final LongAdder _shotsActivated = new LongAdder();
	private final LongAdder _shotsUnavailable = new LongAdder();
	private final LongAdder _shotsFailed = new LongAdder();
	private final LongAdder _playerDeaths = new LongAdder();
	private final LongAdder _targetDeaths = new LongAdder();
	private final LongAdder _lootCandidates = new LongAdder();
	private final LongAdder _lootPickups = new LongAdder();
	private final LongAdder _lootSuccess = new LongAdder();
	private final LongAdder _lootBlocked = new LongAdder();
	private final LongAdder _cancellations = new LongAdder();
	private final LongAdder _timeouts = new LongAdder();
	private final LongAdder _backendFailures = new LongAdder();
	private final LongAdder _respawnRequested = new LongAdder();
	private final LongAdder _respawnAccepted = new LongAdder();
	private final LongAdder _respawnRejected = new LongAdder();
	private final LongAdder _respawnCompleted = new LongAdder();
	private final LongAdder _cleanupFailures = new LongAdder();
	private final LongAdder _stopFailures = new LongAdder();

	void sessionRequested()
	{
		_sessionsRequested.increment();
	}

	void sessionAccepted()
	{
		_sessionsAccepted.increment();
		final int current = _currentSessions.incrementAndGet();
		_peakSessions.accumulateAndGet(current, Math::max);
	}

	void sessionRejected()
	{
		_sessionsRejected.increment();
	}

	void sessionRemoved()
	{
		_currentSessions.decrementAndGet();
	}

	void leaseAcquired()
	{
		_leasesAcquired.increment();
		_currentLeases.incrementAndGet();
	}

	void leaseRejected()
	{
		_leasesRejected.increment();
	}

	void leaseReleased()
	{
		_leasesReleased.increment();
		_currentLeases.decrementAndGet();
	}

	void target(boolean accepted)
	{
		(accepted ? _targetsAccepted : _targetsRejected).increment();
	}

	void targetLost()
	{
		_targetsLost.increment();
	}

	void pulse()
	{
		_pulses.increment();
	}

	void workerDispatched()
	{
		_workerDispatches.increment();
	}

	void dispatchFailed()
	{
		_dispatchFailures.increment();
	}

	void threatObserved(long evictions)
	{
		_threatObservations.increment();
		if (evictions > 0)
		{
			_threatEvictions.add(evictions);
		}
	}

	void attackIssued()
	{
		_normalAttacks.increment();
	}

	void castIssued()
	{
		_skillCastsIssued.increment();
	}

	void castRejected()
	{
		_skillCastsRejected.increment();
	}

	void shot(PhantomCombatBackend.ShotOutcome outcome)
	{
		switch (outcome)
		{
			case ACTIVATED -> _shotsActivated.increment();
			case UNAVAILABLE -> _shotsUnavailable.increment();
			case FAILED -> _shotsFailed.increment();
		}
	}

	void terminal(PhantomCombatResult result)
	{
		switch (result)
		{
			case PLAYER_DEAD -> _playerDeaths.increment();
			case TARGET_LOST -> _targetsLost.increment();
			case VICTORY -> _targetDeaths.increment();
			case VICTORY_LOOTED, VICTORY_LOOT_PARTIAL ->
			{
				_targetDeaths.increment();
				_lootSuccess.increment();
			}
			case VICTORY_LOOT_BLOCKED ->
			{
				_targetDeaths.increment();
				_lootBlocked.increment();
			}
			case CANCELLED -> _cancellations.increment();
			case TIMEOUT -> _timeouts.increment();
			case BACKEND_FAILURE -> _backendFailures.increment();
			default ->
			{
			}
		}
	}

	void lootCandidates(int count)
	{
		_lootCandidates.add(count);
	}

	void lootPickup()
	{
		_lootPickups.increment();
	}

	void respawnRequested()
	{
		_respawnRequested.increment();
	}

	void respawnAccepted()
	{
		_respawnAccepted.increment();
	}

	void respawnRejected()
	{
		_respawnRejected.increment();
	}

	void respawnCompleted()
	{
		_respawnCompleted.increment();
	}

	void cleanupFailure()
	{
		_cleanupFailures.increment();
	}

	void stopFailure()
	{
		_stopFailures.increment();
	}

	public Snapshot snapshot()
	{
		return new Snapshot(_sessionsRequested.sum(), _sessionsAccepted.sum(), _sessionsRejected.sum(), _currentSessions.get(), _peakSessions.get(), _leasesAcquired.sum(), _leasesRejected.sum(), _leasesReleased.sum(), _currentLeases.get(), _targetsAccepted.sum(), _targetsRejected.sum(), _targetsLost.sum(), _pulses.sum(), _workerDispatches.sum(), _dispatchFailures.sum(), _threatObservations.sum(), _threatEvictions.sum(), _normalAttacks.sum(), _skillCastsIssued.sum(), _skillCastsRejected.sum(), _shotsActivated.sum(), _shotsUnavailable.sum(), _shotsFailed.sum(), _playerDeaths.sum(), _targetDeaths.sum(), _lootCandidates.sum(), _lootPickups.sum(), _lootSuccess.sum(), _lootBlocked.sum(), _cancellations.sum(), _timeouts.sum(), _backendFailures.sum(), _respawnRequested.sum(), _respawnAccepted.sum(), _respawnRejected.sum(), _respawnCompleted.sum(), _cleanupFailures.sum(), _stopFailures.sum());
	}

	public record Snapshot(long sessionsRequested, long sessionsAccepted, long sessionsRejected, int currentSessions, int peakSessions, long leasesAcquired, long leasesRejected, long leasesReleased, int currentLeases, long targetsAccepted, long targetsRejected, long targetsLost, long pulses, long workerDispatches, long dispatchFailures, long threatObservations, long threatEvictions, long normalAttacks, long skillCastsIssued, long skillCastsRejected, long shotsActivated, long shotsUnavailable, long shotsFailed, long playerDeaths, long targetDeaths, long lootCandidates, long lootPickups, long lootSuccess, long lootBlocked, long cancellations, long timeouts, long backendFailures, long respawnRequested, long respawnAccepted, long respawnRejected, long respawnCompleted, long cleanupFailures, long stopFailures)
	{
	}
}
