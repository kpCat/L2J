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
import java.util.TreeMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;
import java.util.function.LongSupplier;

import org.l2jmobius.commons.threads.ThreadPool;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.ActionOutcome;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.ActorSnapshot;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.ExternalOwnedAction;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.AcquisitionSkillKind;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.AcquisitionActorPosition;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.AcquisitionTargetSnapshot;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.LootCandidate;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.LootObservation;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.PlayableSnapshot;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.RespawnOutcome;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.ShotOutcome;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.TargetSnapshot;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.PvpConsequenceSnapshot;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.PvpLocalSupportSnapshot;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.PvpTargetSnapshot;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.ThreatObservation;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatLoadout.SelectedSkill;
import org.l2jmobius.gameserver.phantoms.decision.PhantomCancellationToken;

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

	public enum CancelStatus
	{
		CANCELLED_CLEAN,
		CLEANUP_PENDING,
		CLEANUP_FAILED,
		NOT_FOUND,
		ALREADY_TERMINAL,
		NOT_RUNNING
	}

	public enum CleanupState
	{
		NONE,
		PENDING,
		IN_PROGRESS,
		FAILED_RETRYABLE,
		COMPLETE
	}

	public enum DispatchState
	{
		SCHEDULED,
		RUNNING,
		FINISHED,
		CANCELLED
	}

	public enum ExternalActionKind
	{
		ACQUISITION,
		PARTY_TACTIC,
		PARTY_SUPPORT,
		PARTY_ROUTE,
		PVP_RETREAT
	}

	public enum ExternalActionStatus
	{
		ACQUIRED,
		REJECTED_STATE,
		REJECTED_EXISTING,
		REJECTED_ACTOR,
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

	public record ServiceSnapshot(ServiceState state, int activeSessions, int terminalSessions, int queuedSessions, int currentWorkers, int actorLeases, int externalActions, int maximumSessions)
	{
		public static ServiceSnapshot inactive()
		{
			return new ServiceSnapshot(ServiceState.STOPPED, 0, 0, 0, 0, 0, 0, 0);
		}
	}

	public record PvpObservedTarget(PvpTargetSnapshot target, PvpConsequenceSnapshot consequences, PvpLocalSupportSnapshot localSupport, boolean canonicalContextAllowed, boolean actualAttacker, boolean selectedTarget)
	{
		public PvpObservedTarget
		{
			Objects.requireNonNull(target, "target");
			Objects.requireNonNull(consequences, "consequences");
			Objects.requireNonNull(localSupport, "localSupport");
		}
	}

	public record PvpObservation(ActorSnapshot actor, int actorLevel, List<PhantomCombatMode> supportedModes, List<PvpObservedTarget> targets)
	{
		public PvpObservation
		{
			Objects.requireNonNull(actor, "actor");
			if ((actorLevel < 1) || (supportedModes == null) || (supportedModes.size() > PhantomCombatMode.values().length) || (targets == null) || (targets.size() > 32))
			{
				throw new IllegalArgumentException("Invalid bounded PvP observation.");
			}
			supportedModes = List.copyOf(supportedModes);
			targets = List.copyOf(targets);
		}
	}

	public record ExternalActionRequest(long profileId, ExternalActionKind kind, String operationKey, long deadlineLogicalNanos, PhantomCancellationToken ownershipToken)
	{
		public ExternalActionRequest
		{
			if ((profileId <= 0) || (kind == null) || (operationKey == null) || operationKey.isBlank() || (operationKey.length() > 128) || (deadlineLogicalNanos <= 0) || (ownershipToken == null))
			{
				throw new IllegalArgumentException("Invalid external combat action request.");
			}
		}
	}

	public record ExternalActionResult(ExternalActionStatus status, ExternalActionLease lease)
	{
		public ExternalActionResult
		{
			if ((status == ExternalActionStatus.ACQUIRED) != (lease != null))
			{
				throw new IllegalArgumentException("External action status and lease disagree.");
			}
		}
	}

	@FunctionalInterface
	public interface Dispatcher
	{
		DispatchResult dispatch(Runnable runnable, long delayMillis);
	}

	public interface DispatchHandle
	{
		boolean cancelIfNotStarted();

		DispatchState state();
	}

	public record DispatchResult(boolean accepted, DispatchHandle handle)
	{
		public DispatchResult
		{
			if (accepted && (handle == null))
			{
				throw new IllegalArgumentException("Accepted dispatch requires a handle.");
			}
		}

		public static DispatchResult accepted(DispatchHandle handle)
		{
			return new DispatchResult(true, handle);
		}

		public static DispatchResult rejected()
		{
			return new DispatchResult(false, null);
		}
	}

	private static final int MAXIMUM_AUTOMATIC_CLEANUP_ATTEMPTS = 3;
	private static final long CLEANUP_WAIT_MILLIS = 5000;
	private final Object _dispatchGate = new Object();
	private final Object _monitor = new Object();
	private final PhantomCombatBackend _backend;
	private final PhantomCombatCapabilityResolver _capabilityResolver;
	private final PhantomCombatPolicy _policy;
	private final PhantomCombatMetrics _metrics;
	private final LongSupplier _clock;
	private final Dispatcher _dispatcher;
	private final Map<Long, PhantomCombatSession> _sessions = new HashMap<>();
	private final Map<Long, String> _sessionOperationOwners = new HashMap<>();
	private final ArrayDeque<Long> _queue = new ArrayDeque<>();
	private final Set<Long> _queued = new HashSet<>();
	private ServiceState _state = ServiceState.NEW;
	private long _nextGeneration;
	private long _nextWorkerGeneration;
	private long _nextRespawnGeneration;
	private long _nextExternalGeneration;
	private WorkerClaim _workerClaim;
	private final Map<Long, RespawnOperation> _respawnOperations = new HashMap<>();
	private final Map<Long, ExternalOperation> _externalOperations = new HashMap<>();
	private int _actorLeases;
	private int _startOperations;
	private boolean _stopFailureRecorded;
	private boolean _stopRequested;

	public PhantomCombatService(PhantomCombatBackend backend, PhantomCombatCapabilityResolver capabilityResolver, PhantomCombatPolicy policy)
	{
		this(backend, capabilityResolver, policy, new PhantomCombatMetrics(), System::nanoTime, scheduledDispatcher(ThreadPool::schedule));
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

	public static Dispatcher scheduledDispatcher(BiFunction<Runnable, Long, ScheduledFuture<?>> scheduler)
	{
		Objects.requireNonNull(scheduler, "scheduler");
		return (runnable, delayMillis) ->
		{
			final ScheduledDispatchHandle handle = new ScheduledDispatchHandle();
			final ScheduledFuture<?> future = scheduler.apply(() ->
			{
				if (!handle.start())
				{
					return;
				}
				try
				{
					runnable.run();
				}
				finally
				{
					handle.finish();
				}
			}, delayMillis);
			if (future == null)
			{
				return DispatchResult.rejected();
			}
			handle.publish(future);
			return DispatchResult.accepted(handle);
		};
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
		return startSession(request, "");
	}

	public StartResult startAcquisitionSession(PhantomCombatRequest request, String operationOwner)
	{
		if ((operationOwner == null) || operationOwner.isBlank() || (operationOwner.length() > 128))
		{
			throw new IllegalArgumentException("Invalid acquisition combat operation owner.");
		}
		return startSession(request, operationOwner);
	}

	public StartResult startPvpSession(PhantomPvpCombatRequest request)
	{
		Objects.requireNonNull(request, "request");
		return startSession(request.leaseRequest(), "", request);
	}

	private StartResult startSession(PhantomCombatRequest request, String operationOwner)
	{
		return startSession(request, operationOwner, null);
	}

	private StartResult startSession(PhantomCombatRequest request, String operationOwner, PhantomPvpCombatRequest pvpRequest)
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
			if (_respawnOperations.containsKey(request.profileId()) || _externalOperations.containsKey(request.profileId()))
			{
				_metrics.sessionRejected();
				return new StartResult(StartStatus.REJECTED_EXISTING, null);
			}
			final PhantomCombatSession existing = _sessions.get(request.profileId());
			if (existing != null)
			{
				final boolean sameOperation = pvpRequest == null ? (existing._pvpRequest == null) && existing._request.sameOperation(request) : (existing._pvpRequest != null) && existing._pvpRequest.sameOperation(pvpRequest);
				if (!existing._result.terminal() && sameOperation && operationOwner.equals(_sessionOperationOwners.getOrDefault(request.profileId(), "")))
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
			reserved = pvpRequest == null ? new PhantomCombatSession(request, ++_nextGeneration, now, _policy.maximumThreatEntries()) : new PhantomCombatSession(pvpRequest, ++_nextGeneration, now, _policy.maximumThreatEntries());
			_sessions.put(request.profileId(), reserved);
			if (operationOwner.isEmpty())
			{
				_sessionOperationOwners.remove(request.profileId());
			}
			else
			{
				_sessionOperationOwners.put(request.profileId(), operationOwner);
			}
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
					final Optional<PhantomCombatLoadout> loadout = pvpRequest == null ? _capabilityResolver.resolve(actor, request.mode(), lease, _policy.maximumSelectedSkills()) : _capabilityResolver.resolvePvp(actor, request.mode(), lease, _policy.maximumSelectedSkills());
					if (loadout.isEmpty())
					{
						failure = StartStatus.UNSUPPORTED_LOADOUT;
					}
					else
					{
						final boolean validTarget;
						if (pvpRequest == null)
						{
							final TargetSnapshot target = lease.targetSnapshot(request.targetObjectId());
							validTarget = (target != null) && target.validFor(actor, _policy.maximumAcquisitionDistance());
						}
						else
						{
							final PvpTargetSnapshot target = lease.pvpTargetSnapshot(request.targetObjectId());
							validTarget = (target != null) && target.validFor(actor, _policy.maximumAcquisitionDistance());
						}
						_metrics.target(validTarget);
						failure = validTarget ? null : StartStatus.REJECTED_TARGET;
						resolvedLoadout = validTarget ? loadout.orElseThrow() : null;
					}
				}
			}
		}
		catch (Throwable throwable)
		{
			failure = StartStatus.BACKEND_FAILURE;
		}

		if (failure != null)
		{
			synchronized (_monitor)
			{
				_sessions.remove(request.profileId(), reserved);
				_sessionOperationOwners.remove(request.profileId());
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
				_sessionOperationOwners.remove(request.profileId());
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

	public boolean matchesAcquisitionSession(long profileId, int targetObjectId, String operationOwner)
	{
		synchronized (_monitor)
		{
			final PhantomCombatSession session = _sessions.get(profileId);
			return (session != null) && (session._request.targetObjectId() == targetObjectId) && Objects.equals(_sessionOperationOwners.get(profileId), operationOwner);
		}
	}

	public boolean matchesPvpSession(long profileId, int targetObjectId, String authorityHash)
	{
		synchronized (_monitor)
		{
			final PhantomCombatSession session = _sessions.get(profileId);
			return (session != null) && (session._pvpRequest != null) && (session._pvpRequest.targetObjectId() == targetObjectId) && session._pvpRequest.authorityHash().equals(authorityHash);
		}
	}

	/**
	 * Bounded Player observation through the same actor lease owner. Exact targets
	 * come only from an upstream causal owner; the selected target is context, not
	 * an aggression candidate by itself.
	 */
	public Optional<PvpObservation> observePvp(long profileId, List<Integer> exactTargetObjectIds, int attackerLimit, int localRiskPlayerLimit)
	{
		if ((profileId <= 0) || (attackerLimit < 1) || (attackerLimit > 32) || (localRiskPlayerLimit < 1) || (localRiskPlayerLimit > 32) || (exactTargetObjectIds == null) || (exactTargetObjectIds.size() > 10) || exactTargetObjectIds.stream().anyMatch(id -> id == null || id <= 0) || !exactTargetObjectIds.equals(exactTargetObjectIds.stream().distinct().sorted().toList()))
		{
			return Optional.empty();
		}
		synchronized (_monitor)
		{
			final PhantomCombatSession session = _sessions.get(profileId);
			if ((_state != ServiceState.RUNNING) || ((session != null) && (!session._result.terminal() || (session._cleanupState != CleanupState.COMPLETE))) || _respawnOperations.containsKey(profileId) || _externalOperations.containsKey(profileId))
			{
				return Optional.empty();
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
				return Optional.empty();
			}
			_metrics.leaseAcquired();
			synchronized (_monitor)
			{
				_actorLeases++;
			}
			countedLease = true;
			final ActorSnapshot actor = lease.actorSnapshot();
			if ((actor == null) || actor.dead() || actor.alikeDead())
			{
				return Optional.empty();
			}
			final List<PhantomCombatMode> modes = new ArrayList<>();
			for (PhantomCombatMode mode : PhantomCombatMode.values())
			{
				if (_capabilityResolver.resolvePvp(actor, mode, lease, _policy.maximumSelectedSkills()).isPresent())
				{
					modes.add(mode);
				}
			}
			final Set<Integer> attackers = new HashSet<>();
			for (ThreatObservation observation : lease.observedPlayerAttackers(actor.objectId(), attackerLimit))
			{
				attackers.add(observation.targetObjectId());
			}
			final Set<Integer> selected = new HashSet<>(exactTargetObjectIds);
			if (actor.currentTargetObjectId() > 0)
			{
				selected.add(actor.currentTargetObjectId());
			}
			final TreeMap<Integer, PvpObservedTarget> observed = new TreeMap<>();
			final Set<Integer> identities = new HashSet<>(selected);
			identities.addAll(attackers);
			for (int targetObjectId : identities.stream().sorted().limit(32).toList())
			{
				final PvpTargetSnapshot target = lease.pvpTargetSnapshot(targetObjectId);
				final PvpConsequenceSnapshot consequences = target == null ? null : lease.pvpConsequences(targetObjectId);
				if ((target != null) && (consequences != null))
				{
					final PvpLocalSupportSnapshot localSupport = lease.pvpLocalSupport(targetObjectId, localRiskPlayerLimit);
					observed.put(targetObjectId, new PvpObservedTarget(target, consequences, localSupport, target.validFor(actor, _policy.maximumAcquisitionDistance()), attackers.contains(targetObjectId), actor.currentTargetObjectId() == targetObjectId));
				}
			}
			return Optional.of(new PvpObservation(actor, lease.pvpLevel(), modes, List.copyOf(observed.values())));
		}
		catch (RuntimeException e)
		{
			return Optional.empty();
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

	public boolean hasClaim(long profileId)
	{
		synchronized (_monitor)
		{
			return _sessions.containsKey(profileId) || _respawnOperations.containsKey(profileId) || _externalOperations.containsKey(profileId);
		}
	}

	public Optional<PhantomCombatSessionSnapshot> consumeTerminal(long profileId)
	{
		synchronized (_monitor)
		{
			final PhantomCombatSession session = _sessions.get(profileId);
			if ((session == null) || !session._result.terminal() || (session._cleanupState != CleanupState.COMPLETE) || session._startInProgress)
			{
				return Optional.empty();
			}
			_sessions.remove(profileId);
			_sessionOperationOwners.remove(profileId);
			removeSessionMetricLocked(session);
			return Optional.of(session.snapshot());
		}
	}

	public CancelStatus cancel(long profileId)
	{
		final PhantomCombatSession session;
		synchronized (_monitor)
		{
			if ((_state != ServiceState.RUNNING) && (_state != ServiceState.FAILED))
			{
				return CancelStatus.NOT_RUNNING;
			}
			session = _sessions.get(profileId);
			if (session == null)
			{
				return CancelStatus.NOT_FOUND;
			}
			if (session._result.terminal())
			{
				if (session._cleanupState == CleanupState.COMPLETE)
				{
					return CancelStatus.ALREADY_TERMINAL;
				}
			}
			else
			{
				terminalLocked(session, PhantomCombatResult.CANCELLED);
			}
		}
		attemptCleanup(session, false);
		return awaitCleanup(session);
	}

	public RespawnOutcome respawnTown(PhantomRespawnRequest request)
	{
		Objects.requireNonNull(request, "request");
		_metrics.respawnRequested();
		final RespawnOperation operation;
		synchronized (_monitor)
		{
			if ((_state != ServiceState.RUNNING) || request.planOwnershipToken().isCancelled())
			{
				_metrics.respawnRejected();
				return request.planOwnershipToken().isCancelled() ? RespawnOutcome.CANCELLED : RespawnOutcome.REJECTED;
			}
			final PhantomCombatSession session = _sessions.get(request.profileId());
			if ((session != null) && (!session._result.terminal() || (session._cleanupState != CleanupState.COMPLETE)))
			{
				_metrics.respawnRejected();
				return RespawnOutcome.RETRY;
			}
			if (_respawnOperations.containsKey(request.profileId()) || _externalOperations.containsKey(request.profileId()))
			{
				_metrics.respawnRejected();
				return RespawnOutcome.RETRY;
			}
			operation = new RespawnOperation(request.profileId(), ++_nextRespawnGeneration, request.planOwnershipToken());
			_respawnOperations.put(request.profileId(), operation);
			_startOperations++;
		}
		PhantomCombatActorLease lease = null;
		boolean countedLease = false;
		try
		{
			lease = _backend.tryAcquireActor(request.profileId());
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
			synchronized (_monitor)
			{
				operation._actorAcquired = true;
				final PhantomCombatSession session = _sessions.get(request.profileId());
				if ((_respawnOperations.get(request.profileId()) != operation) || !operation.matches(request) || operation.cancelled() || ((_state != ServiceState.RUNNING) && !((_state == ServiceState.STOPPING) && _stopRequested)) || ((session != null) && (!session._result.terminal() || (session._cleanupState != CleanupState.COMPLETE))))
				{
					_metrics.respawnRejected();
					return operation.cancelled() ? RespawnOutcome.CANCELLED : RespawnOutcome.RETRY;
				}
				operation._sideEffectStarted = true;
			}
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
		catch (Throwable throwable)
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
			synchronized (_monitor)
			{
				_respawnOperations.remove(request.profileId(), operation);
			}
			finishStartOperation();
		}
	}

	public ExternalActionResult acquireExternalAction(ExternalActionRequest request)
	{
		Objects.requireNonNull(request, "request");
		_metrics.externalRequested();
		final ExternalOperation operation;
		synchronized (_monitor)
		{
			if (_state != ServiceState.RUNNING)
			{
				_metrics.externalRejected();
				return new ExternalActionResult(ExternalActionStatus.REJECTED_STATE, null);
			}
			if (request.ownershipToken().isCancelled())
			{
				_metrics.externalRejected();
				return new ExternalActionResult(ExternalActionStatus.CANCELLED, null);
			}
			final PhantomCombatSession session = _sessions.get(request.profileId());
			if (((session != null) && (!session._result.terminal() || (session._cleanupState != CleanupState.COMPLETE))) || _respawnOperations.containsKey(request.profileId()) || _externalOperations.containsKey(request.profileId()))
			{
				_metrics.externalRejected();
				return new ExternalActionResult(ExternalActionStatus.REJECTED_EXISTING, null);
			}
			operation = new ExternalOperation(request, ++_nextExternalGeneration);
			_externalOperations.put(request.profileId(), operation);
			_startOperations++;
		}

		PhantomCombatActorLease actorLease = null;
		ExternalActionStatus failure = null;
		try
		{
			actorLease = _backend.tryAcquireActor(request.profileId());
			if (actorLease == null)
			{
				_metrics.leaseRejected();
				failure = ExternalActionStatus.REJECTED_ACTOR;
			}
			else
			{
				_metrics.leaseAcquired();
			}
		}
		catch (Throwable throwable)
		{
			failure = ExternalActionStatus.BACKEND_FAILURE;
		}

		if (failure != null)
		{
			synchronized (_monitor)
			{
				_externalOperations.remove(request.profileId(), operation);
			}
			closeUnowned(actorLease);
			finishStartOperation();
			_metrics.externalRejected();
			return new ExternalActionResult(failure, null);
		}

		final ExternalActionLease publishedLease;
		synchronized (_monitor)
		{
			if ((_state == ServiceState.RUNNING) && (_externalOperations.get(request.profileId()) == operation) && !request.ownershipToken().isCancelled())
			{
				publishedLease = new ExternalActionLease(this, operation, actorLease);
				operation._lease = publishedLease;
				_actorLeases++;
				_metrics.externalAcquired();
			}
			else
			{
				_externalOperations.remove(request.profileId(), operation);
				publishedLease = null;
			}
		}
		if (publishedLease == null)
		{
			closeUnowned(actorLease);
			finishStartOperation();
			_metrics.externalRejected();
			return new ExternalActionResult(ExternalActionStatus.CANCELLED, null);
		}
		finishStartOperation();
		return new ExternalActionResult(ExternalActionStatus.ACQUIRED, publishedLease);
	}

	public boolean cancelExternalAction(long profileId, String operationKey)
	{
		final ExternalActionLease lease;
		synchronized (_monitor)
		{
			final ExternalOperation operation = _externalOperations.get(profileId);
			if ((operation == null) || !operation._request.operationKey().equals(operationKey))
			{
				return false;
			}
			lease = operation._lease;
		}
		if (lease == null)
		{
			return false;
		}
		lease.close();
		return true;
	}

	public void beginStop()
	{
		final List<PhantomCombatSession> cleanups = new ArrayList<>();
		final List<ExternalActionLease> externalLeases = new ArrayList<>();
		synchronized (_dispatchGate)
		{
			synchronized (_monitor)
			{
				if (_state == ServiceState.STOPPED)
				{
					return;
				}
				_stopRequested = true;
				if (_state != ServiceState.FAILED)
				{
					_state = ServiceState.STOPPING;
				}
				final WorkerClaim claim = _workerClaim;
				if ((claim != null) && !claim._running && (claim._handle != null) && claim._handle.cancelIfNotStarted())
				{
					releaseWorkerClaimLocked(claim);
				}
				_queue.clear();
				_queued.clear();
				for (PhantomCombatSession session : _sessions.values())
				{
					if (!session._result.terminal())
					{
						terminalLocked(session, PhantomCombatResult.CANCELLED);
					}
					if ((session._cleanupState == CleanupState.PENDING) || (session._cleanupState == CleanupState.FAILED_RETRYABLE))
					{
						cleanups.add(session);
					}
				}
				for (ExternalOperation operation : _externalOperations.values())
				{
					if (operation._lease != null)
					{
						externalLeases.add(operation._lease);
					}
				}
				removeCompletedStopSessionsLocked();
			}
		}
		cleanups.forEach(session -> attemptCleanup(session, false));
		externalLeases.forEach(ExternalActionLease::close);
	}

	public boolean finishStop()
	{
		synchronized (_monitor)
		{
			if (_state == ServiceState.STOPPED)
			{
				return true;
			}
			removeCompletedStopSessionsLocked();
			if (!_stopRequested || (_workerClaim != null) || (_actorLeases != 0) || (_startOperations != 0) || !_respawnOperations.isEmpty() || !_externalOperations.isEmpty() || !_sessions.isEmpty() || !_queue.isEmpty())
			{
				if (_stopRequested && !_stopFailureRecorded)
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

	public boolean retryFailedCleanup()
	{
		final List<PhantomCombatSession> retries;
		synchronized (_monitor)
		{
			retries = _sessions.values().stream().filter(session -> session._cleanupState == CleanupState.FAILED_RETRYABLE).toList();
		}
		retries.forEach(session -> attemptCleanup(session, true));
		synchronized (_monitor)
		{
			removeCompletedStopSessionsLocked();
			return retries.stream().allMatch(session -> session._cleanupState == CleanupState.COMPLETE);
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
			return new ServiceSnapshot(_state, active, terminal, _queue.size(), _workerClaim == null ? 0 : 1, _actorLeases, _externalOperations.size(), _policy.maximumSessions());
		}
	}

	public PhantomCombatMetrics.Snapshot metrics()
	{
		return _metrics.snapshot();
	}

	private void ensureWorker()
	{
		WorkerClaim claim = null;
		boolean rejected = false;
		synchronized (_dispatchGate)
		{
			synchronized (_monitor)
			{
				if ((_state != ServiceState.RUNNING) || (_workerClaim != null) || _queue.isEmpty())
				{
					return;
				}
				claim = new WorkerClaim(++_nextWorkerGeneration);
				_workerClaim = claim;
			}
			DispatchResult result = null;
			try
			{
				final WorkerClaim exactClaim = claim;
				result = _dispatcher.dispatch(() -> pulse(exactClaim), _policy.pulseIntervalMillis());
			}
			catch (Throwable throwable)
			{
				result = null;
			}
			synchronized (_monitor)
			{
				if ((result != null) && result.accepted())
				{
					if (_workerClaim == claim)
					{
						claim._handle = result.handle();
					}
					_metrics.workerDispatched();
				}
				else
				{
					releaseWorkerClaimLocked(claim);
					rejected = true;
					_metrics.dispatchFailed();
				}
			}
		}
		if (rejected)
		{
			failAllActive();
		}
	}

	private void pulse(WorkerClaim claim)
	{
		boolean ownsClaim = false;
		try
		{
			synchronized (_dispatchGate)
			{
				synchronized (_monitor)
				{
					if (_workerClaim != claim)
					{
						return;
					}
					claim._running = true;
					ownsClaim = true;
					if (_state != ServiceState.RUNNING)
					{
						return;
					}
				}
			}

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
						if ((session != null) && (!session._result.terminal() || cleanupRetryDue(session)))
						{
							due.add(session);
						}
					}
				}
			}

			for (PhantomCombatSession session : due)
			{
				_metrics.pulse();
				try
				{
					process(session);
				}
				catch (Throwable throwable)
				{
					handleProcessThrowable(session);
				}
			}
		}
		finally
		{
			if (ownsClaim)
			{
				synchronized (_monitor)
				{
					releaseWorkerClaimLocked(claim);
				}
			}
			ensureWorker();
		}
	}

	private void process(PhantomCombatSession session)
	{
		boolean cleanup = false;
		synchronized (_monitor)
		{
			if ((_state != ServiceState.RUNNING) || (_sessions.get(session._request.profileId()) != session))
			{
				return;
			}
			if (session._result.terminal())
			{
				cleanup = cleanupRetryDue(session);
			}
			else
			{
				session._processing = true;
			}
		}
		if (cleanup)
		{
			attemptCleanup(session, false);
			return;
		}

		try
		{
			if (session._result.terminal())
			{
				return;
			}
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

			if (session._pvpRequest != null)
			{
				processPvp(session, actor);
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
		catch (Throwable throwable)
		{
			finish(session, PhantomCombatResult.BACKEND_FAILURE);
		}
		finally
		{
			finishProcessing(session);
		}
	}


	private void processPvp(PhantomCombatSession session, ActorSnapshot actor)
	{
		final PvpTargetSnapshot target = session._actorLease.pvpTargetSnapshot(session._request.targetObjectId());
		if ((target != null) && (target.dead() || target.alikeDead()))
		{
			finish(session, PhantomCombatResult.VICTORY);
			return;
		}
		if ((target == null) || !target.validFor(actor, _policy.maximumAcquisitionDistance()))
		{
			finish(session, PhantomCombatResult.TARGET_LOST);
			return;
		}
		if ((actor.maximumCp() > 0) && (percent(actor.currentCp(), actor.maximumCp()) <= session._pvpRequest.cpPotionThresholdPercent()))
		{
			session._actorLease.cpPotions().stream().filter(PhantomCombatBackend.CpPotionSnapshot::ready).findFirst().ifPresent(potion -> session._actorLease.useCpPotion(potion.itemObjectId(), potion.itemId()));
		}
		session._phase = PhantomCombatPhase.FIGHTING;
		issuePvpAction(session, actor);
		requeue(session);
	}

	private void issuePvpAction(PhantomCombatSession session, ActorSnapshot actor)
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
			_metrics.shot(session._actorLease.activateShot(session._request.mode()));
		}
		if (selected != null)
		{
			final ActionOutcome outcome = session._actorLease.castPvp(session._request.targetObjectId(), selected, session._request.mode(), session._pvpRequest.forceUse(), session._pvpRequest.authorityHash());
			if (outcome == ActionOutcome.ISSUED)
			{
				session._ownedAction = session._ownedAction.withSelectedSkill(selected);
				_metrics.castIssued();
				return;
			}
			if (outcome != ActionOutcome.ALREADY_OWNED)
			{
				_metrics.castRejected();
			}
			if ((outcome != ActionOutcome.UNAVAILABLE) || !session._loadout.normalAttackFallback())
			{
				return;
			}
		}
		final ActionOutcome outcome = session._actorLease.attackPvp(session._request.targetObjectId(), session._pvpRequest.authorityHash());
		if (outcome == ActionOutcome.ISSUED)
		{
			session._ownedAction = session._ownedAction.withSelectedSkill(null);
			_metrics.attackIssued();
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
			outcome = session._actorLease.cast(session._request.targetObjectId(), selected, session._request.mode());
			if (outcome == ActionOutcome.ISSUED)
			{
				session._ownedAction = session._ownedAction.withSelectedSkill(selected);
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
			session._ownedAction = session._ownedAction.withSelectedSkill(null);
			_metrics.attackIssued();
		}
	}

	private void processLoot(PhantomCombatSession session, long now)
	{
		if (session._lootAttempt != null)
		{
			final LootObservation observation = session._actorLease.observeLoot(session._lootAttempt);
			if (observation == LootObservation.PENDING)
			{
				if (elapsed(now, session._lootStartedLogicalNanos) >= TimeUnit.MILLISECONDS.toNanos(_policy.lootTimeoutMillis()))
				{
					session._lootLostWithoutAcquisition++;
					session._lootAttempt = null;
					finish(session, lootResult(session, true));
				}
				else
				{
					requeue(session);
				}
				return;
			}
			if (observation == LootObservation.ACQUIRED_BY_ACTOR)
			{
				session._lootAcquiredByActor++;
			}
			else
			{
				session._lootLostWithoutAcquisition++;
			}
			session._lootAttempt = null;
			session._ownedAction = session._ownedAction.withPickupObjectId(0);
		}

		if (elapsed(now, session._lootStartedLogicalNanos) >= TimeUnit.MILLISECONDS.toNanos(_policy.lootTimeoutMillis()))
		{
			finish(session, lootResult(session, true));
			return;
		}
		final List<LootCandidate> candidates = session._actorLease.lootCandidates(_policy.maximumLootCandidates(), _policy.maximumLootDistance());
		if (candidates.size() > _policy.maximumLootCandidates())
		{
			throw new IllegalStateException("Combat backend exceeded the loot candidate bound.");
		}
		_metrics.lootCandidates(candidates.size());
		final List<LootCandidate> ordered = candidates.stream().sorted(Comparator.comparingInt(LootCandidate::worldObjectId)).toList();
		for (LootCandidate candidate : ordered)
		{
			if (session._rememberedLootIds.contains(candidate.worldObjectId()))
			{
				continue;
			}
			if (session._rememberedLootIds.size() >= _policy.maximumRememberedLootIds())
			{
				finish(session, lootResult(session, false));
				return;
			}
			session._rememberedLootIds.add(candidate.worldObjectId());
			final ActionOutcome outcome = session._actorLease.pickUp(candidate.worldObjectId());
			if ((outcome == ActionOutcome.ISSUED) || (outcome == ActionOutcome.ALREADY_OWNED))
			{
				session._lootAttempt = candidate;
				session._ownedAction = session._ownedAction.withPickupObjectId(candidate.worldObjectId());
				session._lootPickupsIssued++;
				_metrics.lootPickup();
			}
			else
			{
				session._lootLostWithoutAcquisition++;
			}
			requeue(session);
			return;
		}
		finish(session, lootResult(session, false));
	}

	private static PhantomCombatResult lootResult(PhantomCombatSession session, boolean timedOut)
	{
		if (session._lootAcquiredByActor > 0)
		{
			return ((session._lootLostWithoutAcquisition > 0) || (timedOut && (session._lootAttempt != null))) ? PhantomCombatResult.VICTORY_LOOT_PARTIAL : PhantomCombatResult.VICTORY_LOOTED;
		}
		return session._rememberedLootIds.isEmpty() ? PhantomCombatResult.VICTORY : PhantomCombatResult.VICTORY_LOOT_BLOCKED;
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
		boolean cleanupNow;
		synchronized (_monitor)
		{
			if ((_sessions.get(session._request.profileId()) != session) || session._result.terminal())
			{
				return;
			}
			terminalLocked(session, result);
			cleanupNow = !session._processing;
		}
		if (cleanupNow)
		{
			attemptCleanup(session, false);
		}
	}

	private void terminalLocked(PhantomCombatSession session, PhantomCombatResult result)
	{
		session._phase = PhantomCombatPhase.TERMINAL;
		session._result = result;
		_queued.remove(session._request.profileId());
		_queue.remove(session._request.profileId());
		session._cleanupState = session._actorLease == null ? CleanupState.COMPLETE : CleanupState.PENDING;
		_metrics.terminal(result);
	}

	private void attemptCleanup(PhantomCombatSession session, boolean explicitRetry)
	{
		final PhantomCombatActorLease lease;
		final PhantomOwnedAction action;
		synchronized (_monitor)
		{
			if ((_sessions.get(session._request.profileId()) != session) || session._processing || session._startInProgress || (session._cleanupState == CleanupState.NONE) || (session._cleanupState == CleanupState.COMPLETE) || (session._cleanupState == CleanupState.IN_PROGRESS))
			{
				return;
			}
			if (!explicitRetry && (session._cleanupAttempts >= MAXIMUM_AUTOMATIC_CLEANUP_ATTEMPTS))
			{
				_state = ServiceState.FAILED;
				return;
			}
			lease = session._actorLease;
			action = session._ownedAction;
			if (lease == null)
			{
				session._cleanupState = CleanupState.COMPLETE;
				_monitor.notifyAll();
				return;
			}
			session._cleanupState = CleanupState.IN_PROGRESS;
			session._cleanupAttempts++;
		}
		Throwable failure = null;
		try
		{
			lease.cancelOwnedAction(action);
			lease.close();
		}
		catch (Throwable throwable)
		{
			failure = throwable;
		}
		boolean dispatchRetry = false;
		boolean failRemaining = false;
		synchronized (_monitor)
		{
			if ((_sessions.get(session._request.profileId()) != session) || (session._actorLease != lease) || (session._cleanupState != CleanupState.IN_PROGRESS))
			{
				return;
			}
			if (failure == null)
			{
				session._actorLease = null;
				session._cleanupState = CleanupState.COMPLETE;
				_actorLeases--;
				_metrics.leaseReleased();
				if (_stopRequested)
				{
					removeCompletedStopSessionsLocked();
				}
			}
			else
			{
				session._cleanupState = CleanupState.FAILED_RETRYABLE;
				session._cleanupFailures++;
				_metrics.cleanupFailure();
				if (!explicitRetry && (session._cleanupAttempts >= MAXIMUM_AUTOMATIC_CLEANUP_ATTEMPTS))
				{
					_state = ServiceState.FAILED;
					failRemaining = true;
				}
				else if (!explicitRetry && (_state == ServiceState.RUNNING))
				{
					enqueue(session);
					dispatchRetry = true;
				}
			}
			_monitor.notifyAll();
		}
		if (failRemaining)
		{
			failAllActive();
		}
		else if (dispatchRetry)
		{
			ensureWorker();
		}
	}

	private void finishProcessing(PhantomCombatSession session)
	{
		boolean cleanupNow = false;
		boolean dispatchCleanup = false;
		synchronized (_monitor)
		{
			session._processing = false;
			if (session._cleanupState == CleanupState.PENDING)
			{
				cleanupNow = true;
			}
			else if (session._cleanupState == CleanupState.FAILED_RETRYABLE)
			{
				if (_state == ServiceState.RUNNING)
				{
					enqueue(session);
					dispatchCleanup = true;
				}
			}
			_monitor.notifyAll();
		}
		if (cleanupNow)
		{
			attemptCleanup(session, false);
		}
		else if (dispatchCleanup)
		{
			ensureWorker();
		}
	}

	private CancelStatus awaitCleanup(PhantomCombatSession session)
	{
		final long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(CLEANUP_WAIT_MILLIS);
		boolean interrupted = false;
		synchronized (_monitor)
		{
			while (session._processing || session._startInProgress || (session._cleanupState == CleanupState.IN_PROGRESS))
			{
				try
				{
					final long remaining = deadline - System.nanoTime();
					if (remaining <= 0)
					{
						break;
					}
					final long millis = TimeUnit.NANOSECONDS.toMillis(remaining);
					final int nanos = (int) (remaining - TimeUnit.MILLISECONDS.toNanos(millis));
					_monitor.wait(millis, nanos);
				}
				catch (InterruptedException e)
				{
					interrupted = true;
					break;
				}
			}
		}
		if (interrupted)
		{
			Thread.currentThread().interrupt();
		}
		synchronized (_monitor)
		{
			if (session._cleanupState == CleanupState.COMPLETE)
			{
				return CancelStatus.CANCELLED_CLEAN;
			}
			if ((session._cleanupAttempts >= MAXIMUM_AUTOMATIC_CLEANUP_ATTEMPTS) && (session._cleanupState == CleanupState.FAILED_RETRYABLE))
			{
				return CancelStatus.CLEANUP_FAILED;
			}
			return CancelStatus.CLEANUP_PENDING;
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

	private void releaseExternal(ExternalOperation operation, PhantomCombatActorLease actorLease, ExternalOwnedAction ownedAction, boolean cancel)
	{
		Throwable failure = null;
		try
		{
			if (cancel)
			{
				actorLease.cancelExternalAction(ownedAction);
			}
		}
		catch (Throwable throwable)
		{
			failure = throwable;
		}
		try
		{
			actorLease.close();
		}
		catch (Throwable throwable)
		{
			if (failure == null)
			{
				failure = throwable;
			}
			else
			{
				failure.addSuppressed(throwable);
			}
		}
		synchronized (_monitor)
		{
			if (_externalOperations.remove(operation._request.profileId(), operation))
			{
				operation._lease = null;
				_actorLeases--;
				_metrics.leaseReleased();
				_metrics.externalReleased(failure == null);
				_monitor.notifyAll();
			}
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
		final List<PhantomCombatSession> cleanups = new ArrayList<>();
		synchronized (_monitor)
		{
			for (PhantomCombatSession session : _sessions.values())
			{
				if (!session._result.terminal())
				{
					terminalLocked(session, PhantomCombatResult.BACKEND_FAILURE);
					if (!session._processing)
					{
						cleanups.add(session);
					}
				}
			}
		}
		cleanups.forEach(session -> attemptCleanup(session, false));
	}

	private void handleProcessThrowable(PhantomCombatSession session)
	{
		try
		{
			finish(session, PhantomCombatResult.BACKEND_FAILURE);
		}
		catch (Throwable ignored)
		{
			synchronized (_monitor)
			{
				if ((_sessions.get(session._request.profileId()) == session) && !session._result.terminal())
				{
					terminalLocked(session, PhantomCombatResult.BACKEND_FAILURE);
				}
			}
		}
	}

	private boolean cleanupRetryDue(PhantomCombatSession session)
	{
		return !session._processing && ((session._cleanupState == CleanupState.PENDING) || ((session._cleanupState == CleanupState.FAILED_RETRYABLE) && (session._cleanupAttempts < MAXIMUM_AUTOMATIC_CLEANUP_ATTEMPTS)));
	}

	private void releaseWorkerClaimLocked(WorkerClaim claim)
	{
		if (_workerClaim == claim)
		{
			_workerClaim = null;
			_monitor.notifyAll();
		}
	}

	private void removeCompletedStopSessionsLocked()
	{
		if (!_stopRequested)
		{
			return;
		}
		for (PhantomCombatSession session : List.copyOf(_sessions.values()))
		{
			if (session._result.terminal() && (session._cleanupState == CleanupState.COMPLETE) && !session._startInProgress && !session._processing)
			{
				_sessions.remove(session._request.profileId(), session);
				removeSessionMetricLocked(session);
			}
		}
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

	private static final class WorkerClaim
	{
		private final long _generation;
		private DispatchHandle _handle;
		private boolean _running;

		private WorkerClaim(long generation)
		{
			_generation = generation;
		}
	}

	private static final class ScheduledDispatchHandle implements DispatchHandle
	{
		private ScheduledFuture<?> _future;
		private DispatchState _state = DispatchState.SCHEDULED;

		private synchronized void publish(ScheduledFuture<?> future)
		{
			_future = Objects.requireNonNull(future, "future");
		}

		private synchronized boolean start()
		{
			if (_state != DispatchState.SCHEDULED)
			{
				return false;
			}
			_state = DispatchState.RUNNING;
			return true;
		}

		private synchronized void finish()
		{
			if (_state == DispatchState.RUNNING)
			{
				_state = DispatchState.FINISHED;
			}
		}

		@Override
		public synchronized boolean cancelIfNotStarted()
		{
			if ((_state != DispatchState.SCHEDULED) || (_future == null) || !_future.cancel(false))
			{
				return false;
			}
			_state = DispatchState.CANCELLED;
			return true;
		}

		@Override
		public synchronized DispatchState state()
		{
			return _state;
		}
	}

	private static final class RespawnOperation
	{
		private final long _profileId;
		private final long _generation;
		private final org.l2jmobius.gameserver.phantoms.decision.PhantomCancellationToken _token;
		private boolean _actorAcquired;
		private boolean _sideEffectStarted;

		private RespawnOperation(long profileId, long generation, org.l2jmobius.gameserver.phantoms.decision.PhantomCancellationToken token)
		{
			_profileId = profileId;
			_generation = generation;
			_token = token;
		}

		private boolean matches(PhantomRespawnRequest request)
		{
			return (_profileId == request.profileId()) && (_token == request.planOwnershipToken());
		}

		private boolean cancelled()
		{
			return _token.isCancelled();
		}
	}

	private static final class ExternalOperation
	{
		private final ExternalActionRequest _request;
		private final long _generation;
		private ExternalActionLease _lease;

		private ExternalOperation(ExternalActionRequest request, long generation)
		{
			_request = request;
			_generation = generation;
		}
	}

	public static final class ExternalActionLease implements AutoCloseable
	{
		private final PhantomCombatService _owner;
		private final ExternalOperation _operation;
		private final PhantomCombatActorLease _actorLease;
		private final AtomicBoolean _released = new AtomicBoolean();
		private volatile ExternalOwnedAction _ownedAction;

		private ExternalActionLease(PhantomCombatService owner, ExternalOperation operation, PhantomCombatActorLease actorLease)
		{
			_owner = owner;
			_operation = operation;
			_actorLease = actorLease;
		}

		public long profileId()
		{
			return _operation._request.profileId();
		}

		public ExternalActionKind kind()
		{
			return _operation._request.kind();
		}

		public String operationKey()
		{
			return _operation._request.operationKey();
		}

		public long generation()
		{
			return _operation._generation;
		}

		public boolean expired(long logicalNowNanos)
		{
			return (logicalNowNanos >= _operation._request.deadlineLogicalNanos()) || _operation._request.ownershipToken().isCancelled();
		}

		public ActorSnapshot actorSnapshot()
		{
			return active() ? _actorLease.actorSnapshot() : null;
		}

		public PlayableSnapshot playableSnapshot(int objectId)
		{
			return active() ? _actorLease.playableSnapshot(objectId) : null;
		}

		public TargetSnapshot targetSnapshot(int objectId)
		{
			return active() ? _actorLease.targetSnapshot(objectId) : null;
		}

		public AcquisitionTargetSnapshot acquisitionTargetSnapshot(int objectId)
		{
			return active() && (kind() == ExternalActionKind.ACQUISITION) ? _actorLease.acquisitionTargetSnapshot(objectId) : null;
		}

		public List<AcquisitionTargetSnapshot> acquisitionTargets(int npcId, int limit, int maximumDistance)
		{
			return active() && (kind() == ExternalActionKind.ACQUISITION) ? _actorLease.acquisitionTargets(npcId, limit, maximumDistance) : List.of();
		}

		public long acquisitionInventoryCount(int itemId)
		{
			return active() && (kind() == ExternalActionKind.ACQUISITION) ? _actorLease.acquisitionInventoryCount(itemId) : -1;
		}

		public Map<Integer, Long> acquisitionInventoryCounts(List<Integer> exactItemIds)
		{
			if (!active() || (kind() != ExternalActionKind.ACQUISITION))
			{
				return Map.of();
			}
			return _actorLease.acquisitionInventoryCounts(exactItemIds);
		}

		public int acquisitionLevel()
		{
			return active() && (kind() == ExternalActionKind.ACQUISITION) ? _actorLease.acquisitionLevel() : 0;
		}

		public AcquisitionActorPosition acquisitionPosition()
		{
			return active() && (kind() == ExternalActionKind.ACQUISITION) ? _actorLease.acquisitionPosition() : null;
		}

		public int knownSkillLevel(int skillId)
		{
			return active() && (kind() == ExternalActionKind.ACQUISITION) ? _actorLease.knownSkillLevel(skillId) : 0;
		}

		public PhantomCombatBackend.ManorInventorySnapshot manorInventory(int seedItemId, int cropItemId, int harvesterItemId)
		{
			return active() && (kind() == ExternalActionKind.ACQUISITION) ? _actorLease.manorInventory(seedItemId, cropItemId, harvesterItemId) : null;
		}

		public PhantomCombatBackend.QuestStateSnapshot questState(String questName, List<String> expectedVariables)
		{
			return active() && (kind() == ExternalActionKind.ACQUISITION) ? _actorLease.questState(questName, expectedVariables) : null;
		}

		public ActionOutcome useExactSeed(int seedObjectId, int seedItemId, int targetObjectId)
		{
			if (!active() || (kind() != ExternalActionKind.ACQUISITION))
			{
				return ActionOutcome.REJECTED;
			}
			final ActionOutcome outcome = _actorLease.useExactSeed(seedObjectId, seedItemId, targetObjectId);
			if ((outcome == ActionOutcome.ISSUED) || (outcome == ActionOutcome.ALREADY_OWNED))
			{
				_ownedAction = new ExternalOwnedAction(kind(), targetObjectId, null, 0, 0, 0, 0);
			}
			return outcome;
		}

		public ActionOutcome useExactHarvester(int harvesterObjectId, int harvesterItemId, int targetObjectId)
		{
			if (!active() || (kind() != ExternalActionKind.ACQUISITION))
			{
				return ActionOutcome.REJECTED;
			}
			final ActionOutcome outcome = _actorLease.useExactHarvester(harvesterObjectId, harvesterItemId, targetObjectId);
			if ((outcome == ActionOutcome.ISSUED) || (outcome == ActionOutcome.ALREADY_OWNED))
			{
				_ownedAction = new ExternalOwnedAction(kind(), targetObjectId, null, 0, 0, 0, 0);
			}
			return outcome;
		}

		public ActionOutcome castAcquisition(int targetObjectId, SelectedSkill skill, AcquisitionSkillKind acquisitionKind)
		{
			if (!active() || (kind() != ExternalActionKind.ACQUISITION) || (skill == null) || (acquisitionKind == null))
			{
				return ActionOutcome.REJECTED;
			}
			final ActorSnapshot actor = _actorLease.actorSnapshot();
			final AcquisitionTargetSnapshot target = _actorLease.acquisitionTargetSnapshot(targetObjectId);
			if ((actor == null) || (target == null))
			{
				return ActionOutcome.REJECTED;
			}
			final ActionOutcome outcome = _actorLease.castAcquisition(targetObjectId, skill, acquisitionKind);
			if ((outcome == ActionOutcome.ISSUED) || (outcome == ActionOutcome.ALREADY_OWNED))
			{
				_ownedAction = new ExternalOwnedAction(kind(), targetObjectId, skill, 0, 0, 0, actor.instanceId());
			}
			return outcome;
		}

		public List<ThreatObservation> observedAttackers(int protectedObjectId, int limit)
		{
			return active() ? _actorLease.observedAttackers(protectedObjectId, limit) : List.of();
		}

		public ActionOutcome castSupport(PhantomPartySupportAction action)
		{
			if (!active() || (kind() != ExternalActionKind.PARTY_SUPPORT) || (action == null))
			{
				return ActionOutcome.REJECTED;
			}
			final ActionOutcome outcome = _actorLease.castSupport(action);
			if ((outcome == ActionOutcome.ISSUED) || (outcome == ActionOutcome.ALREADY_OWNED))
			{
				_ownedAction = new ExternalOwnedAction(kind(), action.targetObjectId(), action.skill(), 0, 0, 0, 0);
				_owner._metrics.externalSupportIssued();
			}
			return outcome;
		}

		public ActionOutcome attack(int targetObjectId)
		{
			if (!active() || (kind() != ExternalActionKind.PARTY_TACTIC))
			{
				return ActionOutcome.REJECTED;
			}
			final ActorSnapshot actor = _actorLease.actorSnapshot();
			final TargetSnapshot target = _actorLease.targetSnapshot(targetObjectId);
			if ((actor == null) || (target == null) || !target.validFor(actor, 2500))
			{
				return ActionOutcome.REJECTED;
			}
			final ActionOutcome outcome = _actorLease.attack(targetObjectId);
			if ((outcome == ActionOutcome.ISSUED) || (outcome == ActionOutcome.ALREADY_OWNED))
			{
				_ownedAction = new ExternalOwnedAction(kind(), targetObjectId, null, 0, 0, 0, actor.instanceId());
			}
			return outcome;
		}

		public ActionOutcome moveTo(int x, int y, int z, int instanceId)
		{
			if (!active() || ((kind() != ExternalActionKind.PARTY_ROUTE) && (kind() != ExternalActionKind.ACQUISITION) && (kind() != ExternalActionKind.PVP_RETREAT)))
			{
				return ActionOutcome.REJECTED;
			}
			final ActionOutcome outcome = _actorLease.moveTo(x, y, z, instanceId);
			if ((outcome == ActionOutcome.ISSUED) || (outcome == ActionOutcome.ALREADY_OWNED))
			{
				_ownedAction = new ExternalOwnedAction(kind(), 0, null, x, y, z, instanceId);
				_owner._metrics.externalRouteIssued();
			}
			return outcome;
		}

		public void complete()
		{
			release(false);
		}

		@Override
		public void close()
		{
			release(true);
		}

		private boolean active()
		{
			return !_released.get() && !_operation._request.ownershipToken().isCancelled();
		}

		private void release(boolean cancel)
		{
			if (_released.compareAndSet(false, true))
			{
				_owner.releaseExternal(_operation, _actorLease, _ownedAction, cancel);
			}
		}
	}
}
