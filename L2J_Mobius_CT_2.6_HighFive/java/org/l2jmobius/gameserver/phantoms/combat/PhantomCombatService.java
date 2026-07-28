/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.combat;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

import org.l2jmobius.commons.threads.ThreadPool;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.ActionOutcome;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.ActorSnapshot;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.LootCandidate;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.RespawnOutcome;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.ShotOutcome;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.TargetSnapshot;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.ThreatObservation;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatLoadout.SelectedSkill;

public final class PhantomCombatService
{
	public enum ServiceState
	{
		NEW,
		RUNNING,
		STOPPING,
		STOPPED,
		FAILED
	}

	public enum StartStatus
	{
		ACCEPTED,
		IDEMPOTENT,
		REJECTED_STATE,
		REJECTED_CAPACITY,
		REJECTED_EXISTING,
		REJECTED_ACTOR,
		REJECTED_TARGET,
		UNSUPPORTED_LOADOUT,
		CANCELLED,
		BACKEND_FAILURE
	}

	public record StartResult(StartStatus status, PhantomCombatSessionSnapshot session)
	{
		public boolean accepted()
		{
			return (status == StartStatus.ACCEPTED) || (status == StartStatus.IDEMPOTENT);
		}
	}

	public record ServiceSnapshot(ServiceState state, int activeSessions, int terminalSessions, int queuedSessions, int currentWorkers, int actorLeases, int maximumSessions)
	{
		public static ServiceSnapshot inactive()
		{
			return new ServiceSnapshot(ServiceState.STOPPED, 0, 0, 0, 0, 0, 0);
		}
	}

	@FunctionalInterface
	public interface Dispatcher
	{
		void dispatch(Runnable runnable, long delayMillis);
	}

	private final Object _monitor = new Object();
	private final PhantomCombatBackend _backend;
	private final PhantomCombatCapabilityResolver _capabilityResolver;
	private final PhantomCombatPolicy _policy;
	private final PhantomCombatMetrics _metrics;
	private final LongSupplier _clock;
	private final Dispatcher _dispatcher;
	private final Map<Long, PhantomCombatSession> _sessions = new HashMap<>();
	private final ArrayDeque<Long> _queue = new ArrayDeque<>();
	private final Set<Long> _queued = new HashSet<>();
	private ServiceState _state = ServiceState.NEW;
	private long _nextGeneration;
	private boolean _workerClaimed;
	private int _actorLeases;
	private int _startOperations;
	private boolean _stopFailureRecorded;

	public PhantomCombatService(PhantomCombatBackend backend, PhantomCombatCapabilityResolver capabilityResolver, PhantomCombatPolicy policy)
	{
		this(backend, capabilityResolver, policy, new PhantomCombatMetrics(), System::nanoTime, (runnable, delay) -> ThreadPool.schedule(runnable, delay));
	}

	public PhantomCombatService(PhantomCombatBackend backend, PhantomCombatCapabilityResolver capabilityResolver, PhantomCombatPolicy policy, PhantomCombatMetrics metrics, LongSupplier clock, Dispatcher dispatcher)
	{
		_backend = Objects.requireNonNull(backend, "backend");
		_capabilityResolver = Objects.requireNonNull(capabilityResolver, "capabilityResolver");
		_policy = Objects.requireNonNull(policy, "policy");
		_metrics = Objects.requireNonNull(metrics, "metrics");
		_clock = Objects.requireNonNull(clock, "clock");
		_dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
	}

	public void start()
	{
		synchronized (_monitor)
		{
			if (_state != ServiceState.NEW)
			{
				throw new IllegalStateException("Combat service is not new.");
			}
			_state = ServiceState.RUNNING;
		}
	}

	public StartResult startSession(PhantomCombatRequest request)
	{
		Objects.requireNonNull(request, "request");
		_metrics.sessionRequested();
		final long now = now();
		final PhantomCombatSession reserved;
		synchronized (_monitor)
		{
			if (_state != ServiceState.RUNNING)
			{
				_metrics.sessionRejected();
				return new StartResult(StartStatus.REJECTED_STATE, null);
			}
			final PhantomCombatSession existing = _sessions.get(request.profileId());
			if (existing != null)
			{
				if (!existing._result.terminal() && existing._request.sameOperation(request))
				{
					return new StartResult(StartStatus.IDEMPOTENT, existing.snapshot());
				}
				_metrics.sessionRejected();
				return new StartResult(StartStatus.REJECTED_EXISTING, existing.snapshot());
			}
			if (_sessions.size() >= _policy.maximumSessions())
			{
				_metrics.sessionRejected();
				return new StartResult(StartStatus.REJECTED_CAPACITY, null);
			}
			reserved = new PhantomCombatSession(request, ++_nextGeneration, now, _policy.maximumThreatEntries());
			_sessions.put(request.profileId(), reserved);
			_startOperations++;
		}

		PhantomCombatActorLease lease = null;
		PhantomCombatLoadout resolvedLoadout = null;
		StartStatus failure = null;
		try
		{
			if (request.planOwnershipToken().isCancelled())
			{
				failure = StartStatus.CANCELLED;
			}
			else
			{
				lease = _backend.tryAcquireActor(request.profileId());
				if (lease == null)
				{
					_metrics.leaseRejected();
					failure = StartStatus.REJECTED_ACTOR;
				}
				else
				{
					_metrics.leaseAcquired();
					final ActorSnapshot actor = lease.actorSnapshot();
					final Optional<PhantomCombatLoadout> loadout = _capabilityResolver.resolve(actor, request.mode(), lease, _policy.maximumSelectedSkills());
					if (loadout.isEmpty())
					{
						failure = StartStatus.UNSUPPORTED_LOADOUT;
					}
					else
					{
						final TargetSnapshot target = lease.targetSnapshot(request.targetObjectId());
						if ((target == null) || !target.validFor(actor, _policy.maximumAcquisitionDistance()))
						{
							_metrics.target(false);
							failure = StartStatus.REJECTED_TARGET;
						}
						else
						{
							_metrics.target(true);
							resolvedLoadout = loadout.orElseThrow();
						}
					}
				}
			}
		}
		catch (RuntimeException e)
		{
			failure = StartStatus.BACKEND_FAILURE;
		}

		if (failure != null)
		{
			synchronized (_monitor)
			{
				_sessions.remove(request.profileId(), reserved);
			}
			try
			{
				closeUnowned(lease);
			}
			finally
			{
				finishStartOperation(reserved);
			}
			_metrics.sessionRejected();
			return new StartResult(failure, null);
		}

		boolean published = false;
		synchronized (_monitor)
		{
			if ((_state == ServiceState.RUNNING) && (_sessions.get(request.profileId()) == reserved) && !reserved._result.terminal() && !request.planOwnershipToken().isCancelled())
			{
				reserved._actorLease = lease;
				reserved._loadout = resolvedLoadout;
				reserved._phase = PhantomCombatPhase.ENGAGING;
				final long evictions = reserved._threatTable.evictions();
				reserved._threatTable.observe(request.targetObjectId(), PhantomCombatThreatTable.EXPLICIT_TARGET_BASE_THREAT, now, true);
				_metrics.threatObserved(reserved._threatTable.evictions() - evictions);
				_actorLeases++;
				reserved._metricsCounted = true;
				_metrics.sessionAccepted();
				enqueue(reserved);
				published = true;
			}
			else
			{
				_sessions.remove(request.profileId(), reserved);
			}
		}
		if (!published)
		{
			reserved._actorLease = null;
			try
			{
				closeUnowned(lease);
			}
			finally
			{
				finishStartOperation(reserved);
			}
			_metrics.sessionRejected();
			return new StartResult(StartStatus.CANCELLED, null);
		}
		finishStartOperation(reserved);
		ensureWorker();
		return new StartResult(StartStatus.ACCEPTED, reserved.snapshot());
	}

	public Optional<PhantomCombatSessionSnapshot> find(long profileId)
	{
		synchronized (_monitor)
		{
			final PhantomCombatSession session = _sessions.get(profileId);
			return session == null ? Optional.empty() : Optional.of(session.snapshot());
		}
	}

	public Optional<PhantomCombatSessionSnapshot> consumeTerminal(long profileId)
	{
		synchronized (_monitor)
		{
			final PhantomCombatSession session = _sessions.get(profileId);
			if ((session == null) || !session._result.terminal() || session._cleanupPending || session._startInProgress)
			{
				return Optional.empty();
			}
			_sessions.remove(profileId);
			removeSessionMetricLocked(session);
			return Optional.of(session.snapshot());
		}
	}

	public boolean cancel(long profileId)
	{
		final PhantomCombatSession session;
		final Cleanup cleanup;
		final boolean accepted;
		synchronized (_monitor)
		{
			session = _sessions.get(profileId);
			if (session == null)
			{
				return false;
			}
			if (session._result.terminal())
			{
				cleanup = null;
				accepted = false;
			}
			else
			{
				cleanup = terminalLocked(session, PhantomCombatResult.CANCELLED);
				accepted = true;
			}
		}
		cleanup(cleanup);
		awaitCleanup(session);
		return accepted;
	}

	public RespawnOutcome respawnTown(long profileId)
	{
		_metrics.respawnRequested();
		synchronized (_monitor)
		{
			if (_state != ServiceState.RUNNING)
			{
				_metrics.respawnRejected();
				return RespawnOutcome.REJECTED;
			}
			_startOperations++;
		}
		PhantomCombatActorLease lease = null;
		boolean countedLease = false;
		try
		{
			lease = _backend.tryAcquireActor(profileId);
			if (lease == null)
			{
				_metrics.leaseRejected();
				_metrics.respawnRejected();
				return RespawnOutcome.RETRY;
			}
			_metrics.leaseAcquired();
			synchronized (_monitor)
			{
				_actorLeases++;
			}
			countedLease = true;
			_metrics.respawnAccepted();
			final RespawnOutcome outcome = lease.respawnTown();
			if (outcome == RespawnOutcome.COMPLETED)
			{
				_metrics.respawnCompleted();
			}
			else
			{
				_metrics.respawnRejected();
			}
			return outcome;
		}
		catch (RuntimeException e)
		{
			_metrics.respawnRejected();
			return RespawnOutcome.REJECTED;
		}
		finally
		{
			if (lease != null)
			{
				try
				{
					lease.close();
				}
				finally
				{
					if (countedLease)
					{
						synchronized (_monitor)
						{
							_actorLeases--;
						}
						_metrics.leaseReleased();
					}
				}
			}
			finishStartOperation();
		}
	}

	public void beginStop()
	{
		final List<Cleanup> cleanups = new ArrayList<>();
		synchronized (_monitor)
		{
			if ((_state == ServiceState.STOPPED) || (_state == ServiceState.STOPPING))
			{
				return;
			}
			_state = ServiceState.STOPPING;
			for (PhantomCombatSession session : _sessions.values())
			{
				if (!session._result.terminal())
				{
					cleanups.add(terminalLocked(session, PhantomCombatResult.CANCELLED));
				}
			}
			int removed = 0;
			for (PhantomCombatSession session : _sessions.values())
			{
				if (session._metricsCounted)
				{
					session._metricsCounted = false;
					removed++;
				}
			}
			_sessions.clear();
			_queue.clear();
			_queued.clear();
			for (int index = 0; index < removed; index++)
			{
				_metrics.sessionRemoved();
			}
		}
		cleanups.forEach(this::cleanup);
	}

	public boolean finishStop()
	{
		synchronized (_monitor)
		{
			if (_state == ServiceState.STOPPED)
			{
				return true;
			}
			if ((_state != ServiceState.STOPPING) || _workerClaimed || (_actorLeases != 0) || (_startOperations != 0) || !_sessions.isEmpty() || !_queue.isEmpty())
			{
				if ((_state == ServiceState.STOPPING) && !_stopFailureRecorded)
				{
					_stopFailureRecorded = true;
					_metrics.stopFailure();
				}
				return false;
			}
			_state = ServiceState.STOPPED;
			return true;
		}
	}

	public ServiceSnapshot snapshot()
	{
		synchronized (_monitor)
		{
			int active = 0;
			int terminal = 0;
			for (PhantomCombatSession session : _sessions.values())
			{
				if (session._result.terminal())
				{
					terminal++;
				}
				else
				{
					active++;
				}
			}
			return new ServiceSnapshot(_state, active, terminal, _queue.size(), _workerClaimed ? 1 : 0, _actorLeases, _policy.maximumSessions());
		}
	}

	public PhantomCombatMetrics.Snapshot metrics()
	{
		return _metrics.snapshot();
	}

	private void ensureWorker()
	{
		synchronized (_monitor)
		{
			if ((_state != ServiceState.RUNNING) || _workerClaimed || _queue.isEmpty())
			{
				return;
			}
			_workerClaimed = true;
		}
		try
		{
			_dispatcher.dispatch(this::pulse, _policy.pulseIntervalMillis());
			_metrics.workerDispatched();
		}
		catch (RuntimeException e)
		{
			synchronized (_monitor)
			{
				_workerClaimed = false;
			}
			_metrics.dispatchFailed();
			failAllActive();
		}
	}

	private void pulse()
	{
		final List<PhantomCombatSession> due = new ArrayList<>(_policy.maximumSessionsPerPulse());
		synchronized (_monitor)
		{
			if (_state == ServiceState.RUNNING)
			{
				while ((due.size() < _policy.maximumSessionsPerPulse()) && !_queue.isEmpty())
				{
					final long profileId = _queue.removeFirst();
					_queued.remove(profileId);
					final PhantomCombatSession session = _sessions.get(profileId);
					if ((session != null) && !session._result.terminal())
					{
						due.add(session);
					}
				}
			}
		}

		for (PhantomCombatSession session : due)
		{
			_metrics.pulse();
			process(session);
		}

		synchronized (_monitor)
		{
			_workerClaimed = false;
		}
		ensureWorker();
	}

	private void process(PhantomCombatSession session)
	{
		synchronized (_monitor)
		{
			if ((_state != ServiceState.RUNNING) || (_sessions.get(session._request.profileId()) != session) || session._result.terminal())
			{
				return;
			}
			session._processing = true;
		}

		try
		{
			final long now = now();
			if (session._request.planOwnershipToken().isCancelled())
			{
				finish(session, PhantomCombatResult.CANCELLED);
				return;
			}
			final ActorSnapshot actor = session._actorLease.actorSnapshot();
			session._lastPulseLogicalNanos = now;
			if (actor.dead() || actor.alikeDead())
			{
				finish(session, PhantomCombatResult.PLAYER_DEAD);
				return;
			}
			if (percent(actor.currentHp(), actor.maximumHp()) <= _policy.lowHpPercent())
			{
				finish(session, PhantomCombatResult.LOW_HP_STOPPED);
				return;
			}
			if (session._phase == PhantomCombatPhase.LOOTING)
			{
				processLoot(session, now);
				return;
			}
			if (elapsed(now, session._startedLogicalNanos) >= TimeUnit.MILLISECONDS.toNanos(session._request.timeoutMillis()))
			{
				finish(session, PhantomCombatResult.TIMEOUT);
				return;
			}

			final TargetSnapshot target = session._actorLease.targetSnapshot(session._request.targetObjectId());
			if ((target != null) && (target.dead() || target.alikeDead()))
			{
				if (session._request.lootAfterVictory())
				{
					session._phase = PhantomCombatPhase.LOOTING;
					session._lootStartedLogicalNanos = now;
					requeue(session);
				}
				else
				{
					finish(session, PhantomCombatResult.VICTORY);
				}
				return;
			}
			if ((target == null) || !target.validFor(actor, _policy.maximumAcquisitionDistance()))
			{
				finish(session, PhantomCombatResult.TARGET_LOST);
				return;
			}

			for (ThreatObservation observation : boundedAttackers(session._actorLease.observedAttackers(_policy.maximumObservedAttackers())))
			{
				final long evictions = session._threatTable.evictions();
				session._threatTable.observe(observation.targetObjectId(), observation.threatValue(), now, observation.targetObjectId() == session._request.targetObjectId());
				_metrics.threatObserved(session._threatTable.evictions() - evictions);
			}
			session._phase = PhantomCombatPhase.FIGHTING;
			issueAction(session, actor);
			requeue(session);
		}
		catch (RuntimeException e)
		{
			finish(session, PhantomCombatResult.BACKEND_FAILURE);
		}
		finally
		{
			finishProcessing(session);
		}
	}

	private void issueAction(PhantomCombatSession session, ActorSnapshot actor)
	{
		SelectedSkill selected = null;
		if (!session._loadout.selectedSkills().isEmpty() && (percent(actor.currentMp(), actor.maximumMp()) > _policy.minimumMpReservePercent()))
		{
			selected = session._loadout.selectedSkills().get(session._nextSkill++ % session._loadout.selectedSkills().size());
		}
		if ((selected == null) && !session._loadout.normalAttackFallback())
		{
			return;
		}
		if (session._request.useShotsIfAvailable())
		{
			final ShotOutcome shot = session._actorLease.activateShot(session._request.mode());
			_metrics.shot(shot);
		}
		final ActionOutcome outcome;
		if (selected != null)
		{
			outcome = session._actorLease.cast(session._request.targetObjectId(), selected);
			if (outcome == ActionOutcome.ISSUED)
			{
				session._ownedSkill = selected;
				_metrics.castIssued();
			}
			else
			{
				if (outcome != ActionOutcome.ALREADY_OWNED)
				{
					_metrics.castRejected();
				}
				if ((outcome == ActionOutcome.UNAVAILABLE) && session._loadout.normalAttackFallback())
				{
					issueNormalAttack(session);
				}
			}
		}
		else
		{
			issueNormalAttack(session);
		}
	}

	private void issueNormalAttack(PhantomCombatSession session)
	{
		final ActionOutcome outcome = session._actorLease.attack(session._request.targetObjectId());
		if (outcome == ActionOutcome.ISSUED)
		{
			session._ownedSkill = null;
			_metrics.attackIssued();
		}
	}

	private void processLoot(PhantomCombatSession session, long now)
	{
		final List<LootCandidate> candidates = session._actorLease.lootCandidates(_policy.maximumLootCandidates(), _policy.maximumLootDistance());
		if (candidates.size() > _policy.maximumLootCandidates())
		{
			throw new IllegalStateException("Combat backend exceeded the loot candidate bound.");
		}
		_metrics.lootCandidates(candidates.size());
		final List<LootCandidate> ordered = candidates.stream().sorted(Comparator.comparingInt(LootCandidate::objectId)).toList();
		if ((session._lastLootObjectId > 0) && ordered.stream().noneMatch(candidate -> candidate.objectId() == session._lastLootObjectId))
		{
			session._lootPickupsObserved++;
			session._lastLootObjectId = 0;
		}
		if (elapsed(now, session._lootStartedLogicalNanos) >= TimeUnit.MILLISECONDS.toNanos(_policy.lootTimeoutMillis()))
		{
			finish(session, session._lootPickupsObserved > 0 ? PhantomCombatResult.VICTORY_LOOT_PARTIAL : PhantomCombatResult.VICTORY_LOOT_BLOCKED);
			return;
		}
		for (LootCandidate candidate : ordered)
		{
			if (session._rememberedLootIds.contains(candidate.objectId()))
			{
				continue;
			}
			if (session._rememberedLootIds.size() >= _policy.maximumRememberedLootIds())
			{
				finish(session, session._lootPickupsObserved > 0 ? PhantomCombatResult.VICTORY_LOOT_PARTIAL : PhantomCombatResult.VICTORY_LOOT_BLOCKED);
				return;
			}
			session._rememberedLootIds.add(candidate.objectId());
			final ActionOutcome outcome = session._actorLease.pickUp(candidate.objectId());
			if ((outcome == ActionOutcome.ISSUED) || (outcome == ActionOutcome.ALREADY_OWNED))
			{
				session._lastLootObjectId = candidate.objectId();
				session._lootPickupsIssued++;
				_metrics.lootPickup();
			}
			requeue(session);
			return;
		}
		if (session._lastLootObjectId > 0)
		{
			requeue(session);
			return;
		}
		if (session._lootPickupsObserved > 0)
		{
			finish(session, PhantomCombatResult.VICTORY_LOOTED);
		}
		else if (session._rememberedLootIds.isEmpty())
		{
			finish(session, PhantomCombatResult.VICTORY);
		}
		else
		{
			finish(session, PhantomCombatResult.VICTORY_LOOT_BLOCKED);
		}
	}

	private List<ThreatObservation> boundedAttackers(List<ThreatObservation> observations)
	{
		if (observations == null)
		{
			return List.of();
		}
		if (observations.size() > _policy.maximumObservedAttackers())
		{
			throw new IllegalStateException("Combat backend exceeded the attacker observation bound.");
		}
		return observations;
	}

	private void requeue(PhantomCombatSession session)
	{
		synchronized (_monitor)
		{
			if ((_state == ServiceState.RUNNING) && (_sessions.get(session._request.profileId()) == session) && !session._result.terminal())
			{
				enqueue(session);
			}
		}
	}

	private void enqueue(PhantomCombatSession session)
	{
		if (_queued.add(session._request.profileId()))
		{
			_queue.addLast(session._request.profileId());
		}
	}

	private void finish(PhantomCombatSession session, PhantomCombatResult result)
	{
		final Cleanup cleanup;
		synchronized (_monitor)
		{
			if ((_sessions.get(session._request.profileId()) != session) || session._result.terminal())
			{
				return;
			}
			cleanup = terminalLocked(session, result);
		}
		cleanup(cleanup);
	}

	private Cleanup terminalLocked(PhantomCombatSession session, PhantomCombatResult result)
	{
		session._phase = PhantomCombatPhase.TERMINAL;
		session._result = result;
		_queued.remove(session._request.profileId());
		_queue.remove(session._request.profileId());
		final PhantomCombatActorLease lease = session._actorLease;
		session._actorLease = null;
		session._cleanupPending = lease != null;
		_metrics.terminal(result);
		if ((lease != null) && session._processing)
		{
			session._deferredCleanupLease = lease;
			return null;
		}
		return new Cleanup(session, lease, session._request.targetObjectId(), session._ownedSkill);
	}

	private void cleanup(Cleanup cleanup)
	{
		if ((cleanup == null) || (cleanup.lease() == null))
		{
			return;
		}
		try
		{
			cleanup.lease().cancelOwnedAction(cleanup.targetObjectId(), cleanup.selectedSkill());
		}
		catch (RuntimeException e)
		{
			// Lease release remains mandatory even if canonical action cleanup fails.
		}
		finally
		{
			try
			{
				cleanup.lease().close();
			}
			finally
			{
				synchronized (_monitor)
				{
					_actorLeases--;
				}
				_metrics.leaseReleased();
				synchronized (_monitor)
				{
					cleanup.session()._cleanupPending = false;
					_monitor.notifyAll();
				}
			}
		}
	}

	private void finishProcessing(PhantomCombatSession session)
	{
		final Cleanup cleanup;
		synchronized (_monitor)
		{
			session._processing = false;
			final PhantomCombatActorLease lease = session._deferredCleanupLease;
			session._deferredCleanupLease = null;
			cleanup = lease == null ? null : new Cleanup(session, lease, session._request.targetObjectId(), session._ownedSkill);
			_monitor.notifyAll();
		}
		cleanup(cleanup);
	}

	private void awaitCleanup(PhantomCombatSession session)
	{
		boolean interrupted = false;
		synchronized (_monitor)
		{
			while (session._cleanupPending || session._startInProgress)
			{
				try
				{
					_monitor.wait();
				}
				catch (InterruptedException e)
				{
					interrupted = true;
				}
			}
		}
		if (interrupted)
		{
			Thread.currentThread().interrupt();
		}
	}

	private void removeSessionMetricLocked(PhantomCombatSession session)
	{
		if (session._metricsCounted)
		{
			session._metricsCounted = false;
			_metrics.sessionRemoved();
		}
	}

	private void closeUnowned(PhantomCombatActorLease lease)
	{
		if (lease != null)
		{
			try
			{
				lease.close();
			}
			finally
			{
				_metrics.leaseReleased();
			}
		}
	}

	private void finishStartOperation()
	{
		finishStartOperation(null);
	}

	private void finishStartOperation(PhantomCombatSession session)
	{
		synchronized (_monitor)
		{
			if (session != null)
			{
				session._startInProgress = false;
			}
			_startOperations--;
			_monitor.notifyAll();
		}
	}

	private void failAllActive()
	{
		final List<Cleanup> cleanups = new ArrayList<>();
		synchronized (_monitor)
		{
			for (PhantomCombatSession session : _sessions.values())
			{
				if (!session._result.terminal())
				{
					cleanups.add(terminalLocked(session, PhantomCombatResult.BACKEND_FAILURE));
				}
			}
		}
		cleanups.forEach(this::cleanup);
	}

	private long now()
	{
		return Math.max(0, _clock.getAsLong());
	}

	private static long elapsed(long now, long start)
	{
		return now >= start ? now - start : Long.MAX_VALUE;
	}

	private static int percent(double current, double maximum)
	{
		if (maximum <= 0)
		{
			return 0;
		}
		return (int) Math.max(0, Math.min(100, Math.floor((current * 100d) / maximum)));
	}

	private record Cleanup(PhantomCombatSession session, PhantomCombatActorLease lease, int targetObjectId, SelectedSkill selectedSkill)
	{
	}
}
