/*
 * Copyright (c) 2013 L2jMobius
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
 * IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.l2jmobius.gameserver.phantoms.decision;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.l2jmobius.gameserver.phantoms.PhantomMetrics;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityWorkItem;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityWorkSink;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStore.StoredGoal;
import org.l2jmobius.gameserver.phantoms.decision.PhantomUtilitySelector.CandidateEvaluation;
import org.l2jmobius.gameserver.phantoms.decision.PhantomUtilitySelector.Selection;

/**
 * Bounded domain-neutral decision and one-step plan executor.
 */
public final class PhantomDecisionEngine implements PhantomActivityWorkSink
{
	public static final int MAX_EXPLANATIONS = 8;
	private static final long STEP_START_UNSET = -1;
	private final Object _monitor = new Object();
	private final PhantomGoalStore _store;
	private final PhantomCandidateRegistry _candidateRegistry;
	private final PhantomStepHandlerRegistry _handlerRegistry;
	private final PhantomMetrics _metrics;
	private final DecisionObserver _observer;
	private final DecisionAdmission _admission;
	private final PhantomUtilitySelector _selector = new PhantomUtilitySelector();
	private final int _maximumAttachedProfiles;
	private final Map<Long, RuntimeSlot> _slots = new HashMap<>();
	private final Set<Long> _pendingAttaches = new HashSet<>();
	private long _persistenceOperationSequence;
	private volatile State _state = State.NEW;
	private PhantomExecutionHandoffStore _executionStore;
	private PhantomExecutionAdmission _executionAdmission;
	private java.util.Map<String, ExecutionOwner> _executionOwners = java.util.Map.of();

	/** Existing Scheduler calls one bounded transition. This installs no thread, timer or second executor. */
	public void installExecutionHandoff(PhantomExecutionHandoffStore store, PhantomExecutionAdmission admission, java.util.Map<String, ExecutionOwner> owners)
	{
		synchronized (_monitor)
		{
			if (_state != State.NEW || _executionStore != null || owners == null || owners.isEmpty() || owners.size() > 16)
			{ throw new IllegalStateException("Execution owners must bind once before Decision start."); }
			_executionStore = Objects.requireNonNull(store); _executionAdmission = Objects.requireNonNull(admission);
			_executionOwners = java.util.Map.copyOf(owners);
		}
	}

	/** All effectful cooperation stays with the original owner; no default implementation grants a yield. */
	public interface ExecutionOwner
	{
		ExecutionReadiness prepareYield(PhantomExecutionHandoffStore.Stored expected, long now);
		ExecutionReadiness prepareRecovery(PhantomExecutionHandoffStore.Stored expected, long now);
		/** Enclose callback in the exact native checkpoint, or prove the exact identity absent. */
		boolean checkpointBoundary(PhantomExecutionHandoffStore.Stored expected, Runnable callback);
		ExecutionCheckpoint checkpoint(PhantomExecutionHandoffStore.Stored expected, boolean recovery, long now);
		/** Recipient reservation/priority/TTL proof is checked again in the transfer transaction. */
		ExecutionAuthority authority(PhantomExecutionHandoffStore.Stored expected, long now);
	}
	public record ExecutionReadiness(boolean ready, String reason, long nextDue)
	{
		public ExecutionReadiness { if (reason == null || reason.isBlank() || nextDue < 0) { throw new IllegalArgumentException("Execution readiness requires a reason and finite due time."); } }
	}
	public record ExecutionAuthority(ExecutionReadiness readiness, java.util.List<org.l2jmobius.gameserver.phantoms.profile.PhantomProfileComponent> guards)
	{ public ExecutionAuthority { Objects.requireNonNull(readiness); guards = java.util.List.copyOf(guards); } }
	public record ExecutionCheckpoint(java.util.List<org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository.ComponentMutation> changes,
		java.util.List<String> dependencyTypes, java.util.List<org.l2jmobius.gameserver.phantoms.profile.PhantomProfileComponent> authority)
	{
		public ExecutionCheckpoint
		{
			changes = java.util.List.copyOf(changes); dependencyTypes = java.util.List.copyOf(dependencyTypes); authority = java.util.List.copyOf(authority);
			if (dependencyTypes.isEmpty()) { throw new IllegalArgumentException("Owner recovery/checkpoint requires its complete read set."); }
		}
	}
	public record ExecutionProgress(PhantomExecutionHandoffStore.Stored stored, boolean progressed, String reason, long nextDue) { }

	public PhantomExecutionHandoffStore.Stored requestExecution(PhantomExecutionHandoffStore.Stored expected, PhantomDomainRef recipient, String requestId, long now, long deadline)
	{
		requireExecutionOwner(expected.state().owner()); requireExecutionOwner(recipient);
		return _executionAdmission.requesting(expected.profileId(), expected.component().rowVersion(), () ->
		{
			final var requested = _executionStore.request(expected, recipient, requestId, now, deadline);
			publishExecution(requested); return requested;
		});
	}

	/** Cancellation has the same safe recovery path as timeout, never a new recipient grant. */
	public ExecutionProgress advanceExecution(PhantomExecutionHandoffStore.Stored expected, long now, boolean cancel)
	{
		final var state = expected.state(); final long id = expected.profileId();
		if (!_executionAdmission.current(id, expected.component().rowVersion(), state))
		{ return new ExecutionProgress(expected, false, "execution.stale_or_unacknowledged", Math.addExact(now, 100)); }
		final var source = requireExecutionOwner(state.owner());
		if (state.phase() == PhantomExecutionHandoff.Phase.ACTIVE)
		{ return new ExecutionProgress(expected, false, "execution.active", now); }
		if (!_executionAdmission.quiescentCurrent(id, expected.component().rowVersion()))
		{ return new ExecutionProgress(expected, false, "execution.wait_earned_before_prepare", Math.addExact(now, 100)); }
		final boolean recovering = state.phase() == PhantomExecutionHandoff.Phase.RECOVERING;
		final ExecutionReadiness ready;
		if (recovering || state.phase() == PhantomExecutionHandoff.Phase.REQUESTED)
		{
			try (var preparation = _executionAdmission.tryAcquirePreparation(id, expected.component().rowVersion(), state.owner()))
			{
				if (preparation == null) { return new ExecutionProgress(expected, false, "execution.wait_prepare_owner", Math.addExact(now, 100)); }
				try (var scope = preparation.enterScope()) { ready = recovering ? source.prepareRecovery(expected, now) : source.prepareYield(expected, now); }
			}
		}
		else { ready = new ExecutionReadiness(true, "execution.yielded", now); }
		if (!ready.ready()) { return waitingExecution(expected, ready, now); }
		if (!_executionAdmission.quiescentCurrent(id, expected.component().rowVersion()))
		{ return new ExecutionProgress(expected, false, "execution.wait_earned_or_recovery", Math.addExact(now, 100)); }
		final boolean expired = !recovering && now >= state.deadline();
		final ExecutionAuthority recipient;
		if (!recovering && !cancel && !expired && state.phase() == PhantomExecutionHandoff.Phase.CHECKPOINTED)
		{
			recipient = requireExecutionOwner(state.recipient()).authority(expected, now);
			if (!recipient.readiness().ready()) { return waitingExecution(expected, recipient.readiness(), now); }
		}
		else { recipient = null; }
		final var result = new java.util.concurrent.atomic.AtomicReference<PhantomExecutionHandoffStore.Stored>();
		final boolean completed = _executionAdmission.quiescent(id, expected.component().rowVersion(), () ->
		{
			final boolean nativeCompleted = source.checkpointBoundary(expected, () ->
			{
				PhantomExecutionHandoffStore.Stored next;
				if (recovering)
				{
					final var proof = source.checkpoint(expected, true, now);
					next = _executionStore.recovered(expected, proof.changes(), proof.dependencyTypes(), proof.authority());
				}
				else if (cancel || expired) { next = _executionStore.recoverSource(expected); }
				else if (state.phase() == PhantomExecutionHandoff.Phase.REQUESTED || state.phase() == PhantomExecutionHandoff.Phase.YIELDED)
				{
					final var proof = source.checkpoint(expected, false, now);
					final var yielded = state.phase() == PhantomExecutionHandoff.Phase.YIELDED ? expected : _executionStore.yielded(expected, now);
					publishExecution(yielded);
					next = _executionStore.checkpoint(yielded, proof.changes(), proof.dependencyTypes(), proof.authority(), now);
				}
				else { next = _executionStore.transfer(expected, recipient.guards(), now); }
				publishExecution(next); result.set(next);
			});
			if (nativeCompleted != (result.get() != null)) { throw new IllegalStateException("Owner checkpoint completion disagrees with its durable callback."); }
			return nativeCompleted;
		});
		if (!completed) { return new ExecutionProgress(expected, false, "execution.wait_native_boundary", Math.addExact(now, 100)); }
		return new ExecutionProgress(result.get(), true, "execution." + result.get().state().phase().name().toLowerCase(java.util.Locale.ROOT), now);
	}

	/** Reconcile unknown acknowledgement/restart only under the actual owner's native/absence boundary. */
	public PhantomExecutionHandoffStore.Stored recoverExecutionAcknowledgement(PhantomExecutionHandoffStore.Stored actual)
	{
		final var result = new java.util.concurrent.atomic.AtomicReference<PhantomExecutionHandoffStore.Stored>();
		final var recovered = _executionAdmission.recovering(actual.profileId(), actual.component().rowVersion(), actual.state(), () ->
		{
			final boolean completed = requireExecutionOwner(actual.state().owner()).checkpointBoundary(actual, () ->
			{
				final var fenced = actual.state().phase() == PhantomExecutionHandoff.Phase.ACTIVE ? _executionStore.restartRecovery(actual) : actual;
				publishExecution(fenced); result.set(fenced);
			});
			if (!completed || result.get() == null) { throw new IllegalStateException("Execution recovery could not prove its native/absence boundary."); }
			return result.get();
		});
		_executionAdmission.acknowledgeRecovery(recovered.profileId(), recovered.component().rowVersion(), recovered.state());
		return recovered;
	}
	private static ExecutionProgress waitingExecution(PhantomExecutionHandoffStore.Stored expected, ExecutionReadiness ready, long now)
	{
		if (ready.nextDue() <= now) { throw new IllegalStateException("A waiting execution owner must provide its next finite retry boundary."); }
		return new ExecutionProgress(expected, false, ready.reason(), ready.nextDue());
	}
	private ExecutionOwner requireExecutionOwner(PhantomDomainRef owner)
	{
		final var port = _executionOwners.get(owner.namespace());
		if (port == null) { throw new IllegalStateException("Missing execution owner: " + owner.namespace()); }
		return port;
	}
	private void publishExecution(PhantomExecutionHandoffStore.Stored stored)
	{ _executionAdmission.publish(stored.profileId(), stored.component().rowVersion(), stored.state()); }

	public PhantomDecisionEngine(PhantomGoalStore store, PhantomCandidateRegistry candidateRegistry, PhantomStepHandlerRegistry handlerRegistry, PhantomMetrics metrics, int maximumAttachedProfiles)
	{
		this(store, candidateRegistry, handlerRegistry, metrics, maximumAttachedProfiles, null);
	}

	public PhantomDecisionEngine(PhantomGoalStore store, PhantomCandidateRegistry candidateRegistry, PhantomStepHandlerRegistry handlerRegistry, PhantomMetrics metrics, int maximumAttachedProfiles, DecisionObserver observer)
	{
		this(store, candidateRegistry, handlerRegistry, metrics, maximumAttachedProfiles, observer, DecisionAdmission.allowAll());
	}

	public PhantomDecisionEngine(PhantomGoalStore store, PhantomCandidateRegistry candidateRegistry, PhantomStepHandlerRegistry handlerRegistry, PhantomMetrics metrics, int maximumAttachedProfiles, DecisionObserver observer, DecisionAdmission admission)
	{
		_store = Objects.requireNonNull(store, "Goal store must not be null.");
		_candidateRegistry = Objects.requireNonNull(candidateRegistry, "Candidate registry must not be null.");
		_handlerRegistry = Objects.requireNonNull(handlerRegistry, "Handler registry must not be null.");
		_metrics = Objects.requireNonNull(metrics, "Metrics must not be null.");
		_observer = observer;
		_admission = Objects.requireNonNull(admission, "Decision admission must not be null.");
		if (maximumAttachedProfiles < 1)
		{
			throw new IllegalArgumentException("Maximum attached profiles must be positive.");
		}
		_maximumAttachedProfiles = maximumAttachedProfiles;
	}

	public void start()
	{
		synchronized (_monitor)
		{
			if (_state != State.NEW)
			{
				throw new IllegalStateException("Decision engine can only start from NEW.");
			}
			if (!_candidateRegistry.isSealed() || !_handlerRegistry.isSealed())
			{
				throw new IllegalStateException("Decision registries must be sealed before engine start.");
			}
			_state = State.RUNNING;
		}
	}

	public AttachResult attach(long profileId)
	{
		return attach(profileId, PhantomCapabilitySet.empty());
	}

	public AttachResult attach(long profileId, PhantomCapabilitySet capabilities)
	{
		Objects.requireNonNull(capabilities, "Capabilities must not be null.");
		if (profileId <= 0)
		{
			return AttachResult.INVALID_PROFILE_ID;
		}
		synchronized (_monitor)
		{
			if (_state != State.RUNNING)
			{
				return AttachResult.NOT_RUNNING;
			}
			if (_slots.containsKey(profileId) || _pendingAttaches.contains(profileId))
			{
				return AttachResult.ALREADY_ATTACHED;
			}
			if ((_slots.size() + _pendingAttaches.size()) >= _maximumAttachedProfiles)
			{
				return AttachResult.CAPACITY_REJECTED;
			}
			_pendingAttaches.add(profileId);
		}

		boolean profileExists = false;
		Optional<StoredGoal> stored = Optional.empty();
		boolean persistenceFailed = false;
		try
		{
			profileExists = _store.profileExists(profileId);
			if (profileExists)
			{
				stored = Objects.requireNonNull(_store.load(profileId), "Goal store load result must not be null.");
			}
		}
		catch (RuntimeException e)
		{
			persistenceFailed = true;
		}

		synchronized (_monitor)
		{
			_pendingAttaches.remove(profileId);
			if (_state != State.RUNNING)
			{
				return AttachResult.CANCELLED_BY_STOP;
			}
			if (_slots.containsKey(profileId))
			{
				return AttachResult.ALREADY_ATTACHED;
			}
			if (persistenceFailed)
			{
				_metrics.recordDecisionPersistenceFailure();
				return AttachResult.PERSISTENCE_FAILED;
			}
			if (!profileExists)
			{
				return AttachResult.PROFILE_NOT_FOUND;
			}
			if (_slots.size() >= _maximumAttachedProfiles)
			{
				return AttachResult.CAPACITY_REJECTED;
			}
			final RuntimeSlot slot = new RuntimeSlot(profileId, capabilities);
			applyStoredGoalLocked(slot, stored);
			_slots.put(profileId, slot);
			_metrics.recordDecisionAttached();
			return AttachResult.ATTACHED;
		}
	}

	public DetachResult detach(long profileId)
	{
		synchronized (_monitor)
		{
			final RuntimeSlot slot = _slots.get(profileId);
			if (slot == null)
			{
				return DetachResult.NOT_ATTACHED;
			}
			slot._generation++;
			slot._detachPending = true;
			cancelPlanLocked(slot, "runtime.detached");
			resetDecisionEvidenceLocked(slot);
			if (slot._inFlight || slot._persistenceInFlight)
			{
				return DetachResult.PENDING;
			}
			removeSlotLocked(slot);
			return DetachResult.DETACHED;
		}
	}

	public MutationResult insertGoal(long profileId, PhantomGoal goal)
	{
		Objects.requireNonNull(goal, "Goal must not be null.");
		final PersistenceClaim claim;
		synchronized (_monitor)
		{
			final RuntimeSlot slot = mutableSlotLocked(profileId);
			if (slot == null)
			{
				return MutationResult.REJECTED;
			}
			if (slot._persistenceInFlight)
			{
				_metrics.recordDecisionMutationRejected();
				return MutationResult.BUSY;
			}
			if (slot._goal != null)
			{
				_metrics.recordDecisionMutationRejected();
				return MutationResult.GOAL_ALREADY_PRESENT;
			}
			claim = claimPersistenceLocked(slot, PersistenceOperationKind.INSERT, goal);
		}
		return reconcileMutation(claim, executeInsert(claim));
	}

	public MutationResult setGoal(long profileId, PhantomGoal goal)
	{
		Objects.requireNonNull(goal, "Goal must not be null.");
		final PersistenceClaim claim;
		synchronized (_monitor)
		{
			final RuntimeSlot slot = mutableSlotLocked(profileId);
			if (slot == null)
			{
				return MutationResult.REJECTED;
			}
			if (slot._persistenceInFlight)
			{
				_metrics.recordDecisionMutationRejected();
				return MutationResult.BUSY;
			}
			if (slot._goal == null)
			{
				_metrics.recordDecisionMutationRejected();
				return MutationResult.GOAL_NOT_PRESENT;
			}
			if (goal.revision() <= slot._goal.revision())
			{
				_metrics.recordDecisionMutationRejected();
				return MutationResult.REVISION_REJECTED;
			}
			claim = claimPersistenceLocked(slot, PersistenceOperationKind.REPLACE, goal);
		}
		return reconcileMutation(claim, executeReplace(claim));
	}

	public MutationResult clearGoal(long profileId)
	{
		final PersistenceClaim claim;
		synchronized (_monitor)
		{
			final RuntimeSlot slot = mutableSlotLocked(profileId);
			if (slot == null)
			{
				return MutationResult.REJECTED;
			}
			if (slot._persistenceInFlight)
			{
				_metrics.recordDecisionMutationRejected();
				return MutationResult.BUSY;
			}
			if (slot._goal == null)
			{
				_metrics.recordDecisionMutationRejected();
				return MutationResult.GOAL_NOT_PRESENT;
			}
			claim = claimPersistenceLocked(slot, PersistenceOperationKind.CLEAR, null);
		}
		return reconcileMutation(claim, executeDelete(claim));
	}

	public ReloadResult reload(long profileId)
	{
		final PersistenceClaim claim;
		synchronized (_monitor)
		{
			if (_state != State.RUNNING)
			{
				_metrics.recordDecisionReloadRejected();
				return ReloadResult.NOT_RUNNING;
			}
			final RuntimeSlot slot = _slots.get(profileId);
			if ((slot == null) || slot._detachPending)
			{
				_metrics.recordDecisionReloadRejected();
				return ReloadResult.REJECTED;
			}
			if (slot._inFlight || slot._persistenceInFlight)
			{
				_metrics.recordDecisionReloadRejected();
				return ReloadResult.BUSY;
			}
			claim = claimPersistenceLocked(slot, PersistenceOperationKind.RELOAD, null);
		}
		return reconcileReload(claim, executeLoad(claim));
	}

	@Override
	public void accept(PhantomActivityWorkItem workItem)
	{
		Objects.requireNonNull(workItem, "Work item must not be null.");
		final AdmissionLease admitted;
		try { admitted = _admission.tryAcquire(workItem.profileId()); }
		catch (RuntimeException unavailable) { return; }
		try (final var execution = admitted)
		{
			if (execution != null) { acceptAdmitted(workItem, execution); }
		}
	}

	private void acceptAdmitted(PhantomActivityWorkItem workItem, AdmissionLease execution)
	{
		final WorkClaim claim;
		synchronized (_monitor)
		{
			claim = claimWorkLocked(workItem, execution);
		}
		if (claim == null)
		{
			return;
		}

		PhantomPlan plan = claim._existingPlan;
		Selection selection = null;
		if (plan == null)
		{
			selection = _selector.select(_candidateRegistry.snapshot().stream().filter(candidate -> candidateAdmitted(workItem.profileId(), candidate.key())).toList(), claim._planningContext);
			_metrics.recordDecisionCandidates(selection.evaluated(), selection.blocked(), selection.failed());
			if (selection.candidate() != null)
			{
				try
				{
					plan = selection.candidate().planFactory().create(claim._planningContext);
					validatePlan(plan, claim._goal, selection.candidate(), _handlerRegistry.snapshot(), claim._planningContext.logicalNowNanos());
				}
				catch (Throwable throwable)
				{
					plan = null;
				}
			}
		}

		final HandlerClaim handlerClaim;
		synchronized (_monitor)
		{
			handlerClaim = prepareHandlerLocked(claim, workItem, plan, selection, execution);
		}
		if (handlerClaim == null)
		{
			notifyObserver(workItem);
			return;
		}

		PhantomStepResult result;
		try
		{
			result = executionCurrent(execution) ? handlerClaim._handler.execute(handlerClaim._context)
				: PhantomStepResult.of(PhantomStepResult.Type.REPLAN, "execution.admission_revoked");
			if (result == null)
			{
				result = PhantomStepResult.of(PhantomStepResult.Type.REPLAN, "handler.null_result");
			}
		}
		catch (Throwable throwable)
		{
			result = PhantomStepResult.of(PhantomStepResult.Type.REPLAN, "handler.failed");
		}
		final PersistenceClaim terminalClaim;
		synchronized (_monitor)
		{
			terminalClaim = applyHandlerResultLocked(handlerClaim, result, workItem.logicalNowNanos());
		}
		if (terminalClaim != null)
		{
			reconcileTerminal(terminalClaim, executeReplace(terminalClaim));
		}
		notifyObserver(workItem);
	}

	private void notifyObserver(PhantomActivityWorkItem workItem)
	{
		if (_observer == null)
		{
			return;
		}
		try
		{
			if (!_observer.interested(workItem.profileId()))
			{
				return;
			}
		}
		catch (Throwable ignored)
		{
			return;
		}
		final RuntimeSnapshot snapshot;
		synchronized (_monitor)
		{
			final RuntimeSlot slot = _slots.get(workItem.profileId());
			if (slot == null)
			{
				return;
			}
			snapshot = snapshotLocked(slot);
		}
		try
		{
			_observer.onDecision(workItem.effectiveState(), snapshot, workItem.logicalNowNanos());
		}
		catch (Throwable ignored)
		{
			// Diagnostics must never change or fail the canonical decision result.
		}
	}

	public Optional<RuntimeSnapshot> find(long profileId)
	{
		synchronized (_monitor)
		{
			final RuntimeSlot slot = _slots.get(profileId);
			return slot == null ? Optional.empty() : Optional.of(snapshotLocked(slot));
		}
	}

	/** Immutable current root only; no persistence or handler is invoked by this lifetime observation. */
	public Optional<PhantomGoal> currentGoal(long profileId)
	{
		synchronized (_monitor)
		{
			final RuntimeSlot slot = _slots.get(profileId);
			return slot == null ? Optional.empty() : Optional.ofNullable(slot._goal);
		}
	}

	public List<RuntimeSnapshot> list()
	{
		synchronized (_monitor)
		{
			final List<RuntimeSnapshot> snapshots = new ArrayList<>(_slots.size());
			for (RuntimeSlot slot : _slots.values())
			{
				snapshots.add(snapshotLocked(slot));
			}
			snapshots.sort(Comparator.comparingLong(RuntimeSnapshot::profileId));
			return List.copyOf(snapshots);
		}
	}

	public BeginStopResult beginStop()
	{
		synchronized (_monitor)
		{
			if (_state == State.STOPPED)
			{
				return BeginStopResult.ALREADY_STOPPED;
			}
			if (_state == State.STOPPING)
			{
				return BeginStopResult.ALREADY_STOPPING;
			}
			_state = State.STOPPING;
			for (RuntimeSlot slot : _slots.values())
			{
				slot._generation++;
				cancelPlanLocked(slot, "runtime.stopping");
				resetDecisionEvidenceLocked(slot);
			}
			return BeginStopResult.STARTED;
		}
	}

	public boolean finishStop()
	{
		synchronized (_monitor)
		{
			if (_state == State.STOPPED)
			{
				return false;
			}
			if ((_state != State.STOPPING) && (_state != State.NEW))
			{
				_metrics.recordDecisionStopFailure();
				return false;
			}
			if (!_pendingAttaches.isEmpty())
			{
				_metrics.recordDecisionStopFailure();
				return false;
			}
			for (RuntimeSlot slot : _slots.values())
			{
				if (slot._inFlight || slot._persistenceInFlight)
				{
					_metrics.recordDecisionStopFailure();
					return false;
				}
			}
			for (RuntimeSlot slot : _slots.values())
			{
				_metrics.recordDecisionDetached();
			}
			_slots.clear();
			_state = State.STOPPED;
			return true;
		}
	}

	public EngineSnapshot snapshot()
	{
		synchronized (_monitor)
		{
			return new EngineSnapshot(
				_state,
				_slots.size(),
				_pendingAttaches.size(),
				_maximumAttachedProfiles,
				_candidateRegistry.snapshot().size(),
				_handlerRegistry.snapshot().size(),
				_slots.values().stream().filter(slot -> slot._inFlight).count(),
				_slots.values().stream().filter(slot -> slot._persistenceInFlight).count());
		}
	}

	private boolean candidateAdmitted(long id, String key)
	{
		try { return _admission.permitsCandidate(id, key); }
		catch (RuntimeException unavailable) { return false; }
	}

	private WorkClaim claimWorkLocked(PhantomActivityWorkItem workItem, AdmissionLease execution)
	{
		if (_state != State.RUNNING)
		{
			return null;
		}
		final RuntimeSlot slot = _slots.get(workItem.profileId());
		if ((slot == null) || slot._detachPending)
		{
			return null;
		}
		if (slot._persistenceInFlight)
		{
			return null;
		}
		final long executionGeneration;
		try
		{
			if (!_admission.permits(workItem.profileId()))
			{
				return null;
			}
			executionGeneration = execution.executionGeneration();
			if (executionGeneration < 0) { return null; }
		}
		catch (RuntimeException exception)
		{
			return null;
		}
		if (slot._executionGeneration != executionGeneration)
		{
			slot._executionGeneration = executionGeneration;
			slot._generation++;
			cancelPlanLocked(slot, "execution.generation_changed");
			resetDecisionEvidenceLocked(slot);
			if ((slot._goal != null) && (slot._goal.status() == PhantomGoalStatus.ACTIVE) && !requiresExplicitReload(slot._runtimeState))
			{ slot._runtimeState = RuntimeState.NEEDS_REPLAN; }
		}
		if (workItem.activityGeneration() < slot._activityGeneration)
		{
			return null;
		}
		if (workItem.activityGeneration() > slot._activityGeneration)
		{
			slot._activityGeneration = workItem.activityGeneration();
			slot._generation++;
			cancelPlanLocked(slot, "activity.generation_changed");
			resetDecisionEvidenceLocked(slot);
			if ((slot._goal != null) && (slot._goal.status() == PhantomGoalStatus.ACTIVE) && !requiresExplicitReload(slot._runtimeState))
			{
				slot._runtimeState = RuntimeState.NEEDS_REPLAN;
			}
		}
		if (slot._inFlight)
		{
			return null;
		}
		if (slot._goal == null)
		{
			_metrics.recordDecisionNoGoal();
			slot._runtimeState = RuntimeState.NO_GOAL;
			return null;
		}
		if ((slot._goal.status() != PhantomGoalStatus.ACTIVE) || requiresExplicitReload(slot._runtimeState))
		{
			return null;
		}
		if ((slot._retryDueNanos > 0) && (workItem.logicalNowNanos() < slot._retryDueNanos))
		{
			return null;
		}
		slot._inFlight = true;
		slot._decisionSequence++;
		_metrics.recordDecision();
		final PhantomPlanningContext planningContext = new PhantomPlanningContext(slot._profileId, slot._goal, slot._capabilities, workItem.effectiveState(), workItem.activityGeneration(), workItem.tickSequence(), workItem.logicalNowNanos(), slot._decisionSequence);
		return new WorkClaim(slot, slot._generation, slot._goal, slot._plan, planningContext);
	}

	private HandlerClaim prepareHandlerLocked(WorkClaim claim, PhantomActivityWorkItem workItem, PhantomPlan planned, Selection selection, AdmissionLease execution)
	{
		final RuntimeSlot slot = claim._slot;
		if (!isCurrentLocked(slot, claim._generation, claim._goal))
		{
			finishStaleLocked(slot);
			return null;
		}
		if (claim._existingPlan == null)
		{
			slot._explanations = selection != null ? selection.explanations() : List.of();
			slot._selectedCandidateKey = selection != null && selection.candidate() != null ? selection.candidate().key() : null;
			slot._selectedScore = selection != null ? selection.score() : -1;
			if (planned == null)
			{
				slot._runtimeState = RuntimeState.NO_CANDIDATE;
				slot._reasonKey = "decision.no_candidate";
				slot._inFlight = false;
				_metrics.recordDecisionNoCandidate();
				finishDetachIfPendingLocked(slot);
				return null;
			}
			slot._plan = planned;
			slot._currentStep = 0;
			slot._attempt = 0;
			slot._stepStartedNanos = STEP_START_UNSET;
			slot._retryDueNanos = 0;
			slot._runtimeState = RuntimeState.EXECUTING;
			_metrics.recordDecisionPlanCreated();
		}

		final PhantomPlan plan = slot._plan;
		if (timedOut(workItem.logicalNowNanos(), plan.createdAtLogicalNanos(), plan.totalTimeoutMillis()))
		{
			timeoutPlanLocked(slot, "plan.total_timeout");
			return null;
		}
		final PhantomPlanStep step = plan.steps().get(slot._currentStep);
		if ((slot._stepStartedNanos != STEP_START_UNSET) && timedOut(workItem.logicalNowNanos(), slot._stepStartedNanos, step.timeoutMillis()))
		{
			timeoutPlanLocked(slot, "plan.step_timeout");
			return null;
		}
		if (slot._stepStartedNanos == STEP_START_UNSET)
		{
			slot._stepStartedNanos = workItem.logicalNowNanos();
		}
		final PhantomStepHandler handler = _handlerRegistry.snapshot().get(step.actionKey());
		if (handler == null)
		{
			replanLocked(slot, "handler.missing");
			slot._inFlight = false;
			finishDetachIfPendingLocked(slot);
			return null;
		}
		slot._attempt++;
		_metrics.recordDecisionStepAttempted();
		final long generation = slot._generation;
		final PhantomCancellationToken token = new PhantomCancellationToken()
		{
			@Override public boolean isCancelled() { return !executionCurrent(execution) || !isPlanCurrent(slot, generation, plan); }
			@Override public boolean yieldRequested() { return execution.yieldRequested() && isPlanCurrent(slot, generation, plan); }
		};
		final PhantomStepContext context = new PhantomStepContext(slot._profileId, slot._goal, plan, step, workItem.effectiveState(), workItem.activityGeneration(), workItem.tickSequence(), workItem.logicalNowNanos(), slot._attempt, token);
		return new HandlerClaim(slot, generation, slot._goal, plan, handler, context);
	}

	private PersistenceClaim applyHandlerResultLocked(HandlerClaim claim, PhantomStepResult result, long logicalNowNanos)
	{
		final RuntimeSlot slot = claim._slot;
		if (!isCurrentLocked(slot, claim._generation, claim._goal) || (slot._plan != claim._plan))
		{
			finishStaleLocked(slot);
			return null;
		}
		if (claim._context.cancellationToken().isCancelled())
		{
			replanLocked(slot, "execution.admission_revoked");
			slot._inFlight = false;
			finishDetachIfPendingLocked(slot);
			return null;
		}
		slot._lastResult = result.type();
		slot._reasonKey = result.reasonKey();
		PersistenceClaim terminalClaim = null;
		switch (result.type())
		{
			case SUCCESS ->
			{
				_metrics.recordDecisionStepSucceeded();
				slot._currentStep++;
				slot._attempt = 0;
				slot._stepStartedNanos = STEP_START_UNSET;
				slot._retryDueNanos = 0;
				if (slot._currentStep >= slot._plan.steps().size())
				{
					slot._plan = null;
					slot._runtimeState = RuntimeState.NEEDS_REPLAN;
					_metrics.recordDecisionPlanCompleted();
				}
			}
			case RETRY ->
			{
				if (slot._attempt >= slot._plan.steps().get(slot._currentStep).maximumAttempts())
				{
					_metrics.recordDecisionStepFailed();
					replanLocked(slot, "step.retry_exhausted");
				}
				else
				{
					slot._retryDueNanos = saturatingAdd(logicalNowNanos, millisToNanos(result.retryDelayMillis()));
					slot._runtimeState = RuntimeState.WAITING_RETRY;
					_metrics.recordDecisionStepRetried();
				}
			}
			case REPLAN ->
			{
				_metrics.recordDecisionStepFailed();
				replanLocked(slot, result.reasonKey());
			}
			case COMPLETE_GOAL -> terminalClaim = claimTerminalPersistenceLocked(slot, PhantomGoalStatus.COMPLETED);
			case FAIL_GOAL -> terminalClaim = claimTerminalPersistenceLocked(slot, PhantomGoalStatus.FAILED);
			case CANCELLED ->
			{
				_metrics.recordDecisionStepCancelled();
				cancelPlanLocked(slot, result.reasonKey());
				slot._runtimeState = RuntimeState.NEEDS_REPLAN;
			}
		}
		if (terminalClaim == null)
		{
			slot._inFlight = false;
			finishDetachIfPendingLocked(slot);
		}
		return terminalClaim;
	}

	private void timeoutPlanLocked(RuntimeSlot slot, String reasonKey)
	{
		slot._plan = null;
		slot._currentStep = 0;
		slot._attempt = 0;
		slot._stepStartedNanos = STEP_START_UNSET;
		slot._retryDueNanos = 0;
		slot._runtimeState = RuntimeState.NEEDS_REPLAN;
		slot._reasonKey = reasonKey;
		slot._inFlight = false;
		_metrics.recordDecisionPlanTimedOut();
		finishDetachIfPendingLocked(slot);
	}

	private void replanLocked(RuntimeSlot slot, String reasonKey)
	{
		slot._plan = null;
		slot._currentStep = 0;
		slot._attempt = 0;
		slot._stepStartedNanos = STEP_START_UNSET;
		slot._retryDueNanos = 0;
		slot._runtimeState = RuntimeState.NEEDS_REPLAN;
		slot._reasonKey = reasonKey;
		_metrics.recordDecisionPlanReplanned();
	}

	private void cancelPlanLocked(RuntimeSlot slot, String reasonKey)
	{
		if (slot._plan != null)
		{
			_metrics.recordDecisionPlanCancelled();
		}
		slot._plan = null;
		slot._currentStep = 0;
		slot._attempt = 0;
		slot._stepStartedNanos = STEP_START_UNSET;
		slot._retryDueNanos = 0;
		slot._reasonKey = reasonKey;
	}

	private void replaceRuntimeGoalLocked(RuntimeSlot slot, StoredGoal stored)
	{
		slot._generation++;
		cancelPlanLocked(slot, "goal.replaced");
		resetDecisionEvidenceLocked(slot);
		slot._goal = stored.goal();
		slot._componentRowVersion = stored.rowVersion();
		slot._runtimeState = stored.goal().status() == PhantomGoalStatus.ACTIVE ? RuntimeState.NEEDS_REPLAN : RuntimeState.TERMINAL;
	}

	private void applyStoredGoalLocked(RuntimeSlot slot, Optional<StoredGoal> stored)
	{
		slot._generation++;
		cancelPlanLocked(slot, "goal.reloaded");
		resetDecisionEvidenceLocked(slot);
		if (stored.isPresent())
		{
			slot._goal = stored.get().goal();
			slot._componentRowVersion = stored.get().rowVersion();
			slot._runtimeState = slot._goal.status() == PhantomGoalStatus.ACTIVE ? RuntimeState.NEEDS_REPLAN : RuntimeState.TERMINAL;
		}
		else
		{
			slot._goal = null;
			slot._componentRowVersion = -1;
			slot._runtimeState = RuntimeState.NO_GOAL;
			slot._reasonKey = "goal.absent";
		}
	}

	private PersistenceClaim claimPersistenceLocked(RuntimeSlot slot, PersistenceOperationKind kind, PhantomGoal replacementGoal)
	{
		if (slot._persistenceInFlight)
		{
			throw new IllegalStateException("Runtime already owns a persistence operation.");
		}
		if (_persistenceOperationSequence == Long.MAX_VALUE)
		{
			throw new IllegalStateException("Decision persistence operation sequence exhausted.");
		}
		final long operationId = ++_persistenceOperationSequence;
		final PersistenceClaim claim = new PersistenceClaim(slot, operationId, kind, slot._goal, slot._componentRowVersion, replacementGoal);
		slot._persistenceInFlight = true;
		slot._persistenceOperationId = operationId;
		slot._persistenceOperationKind = kind;
		slot._generation++;
		cancelPlanLocked(slot, "persistence.in_flight");
		resetDecisionEvidenceLocked(slot);
		return claim;
	}

	private PersistenceClaim claimTerminalPersistenceLocked(RuntimeSlot slot, PhantomGoalStatus terminalStatus)
	{
		if (slot._persistenceInFlight)
		{
			throw new IllegalStateException("Terminal result raced an existing persistence operation.");
		}
		if (_persistenceOperationSequence == Long.MAX_VALUE)
		{
			throw new IllegalStateException("Decision persistence operation sequence exhausted.");
		}
		final PersistenceOperationKind kind = terminalStatus == PhantomGoalStatus.COMPLETED ? PersistenceOperationKind.TERMINAL_COMPLETE : PersistenceOperationKind.TERMINAL_FAIL;
		final long operationId = ++_persistenceOperationSequence;
		final PersistenceClaim claim = new PersistenceClaim(slot, operationId, kind, slot._goal, slot._componentRowVersion, slot._goal.withStatus(terminalStatus));
		slot._persistenceInFlight = true;
		slot._persistenceOperationId = operationId;
		slot._persistenceOperationKind = kind;
		slot._plan = null;
		slot._currentStep = 0;
		slot._attempt = 0;
		slot._stepStartedNanos = STEP_START_UNSET;
		slot._retryDueNanos = 0;
		return claim;
	}

	private StoreExecution executeInsert(PersistenceClaim claim)
	{
		try
		{
			return new StoreExecution(Objects.requireNonNull(_store.insert(claim._slot._profileId, claim._replacementGoal), "Goal store insert result must not be null."), PersistenceFailure.NONE);
		}
		catch (ConcurrentModificationException e)
		{
			return new StoreExecution(null, PersistenceFailure.CONFLICT);
		}
		catch (RuntimeException e)
		{
			return new StoreExecution(null, PersistenceFailure.FAILURE);
		}
	}

	private StoreExecution executeReplace(PersistenceClaim claim)
	{
		try
		{
			return new StoreExecution(Objects.requireNonNull(_store.replace(claim._slot._profileId, claim._expectedComponentRowVersion, claim._replacementGoal), "Goal store replace result must not be null."), PersistenceFailure.NONE);
		}
		catch (ConcurrentModificationException e)
		{
			return new StoreExecution(null, PersistenceFailure.CONFLICT);
		}
		catch (RuntimeException e)
		{
			return new StoreExecution(null, PersistenceFailure.FAILURE);
		}
	}

	private StoreExecution executeDelete(PersistenceClaim claim)
	{
		try
		{
			_store.delete(claim._slot._profileId, claim._expectedComponentRowVersion);
			return new StoreExecution(null, PersistenceFailure.NONE);
		}
		catch (ConcurrentModificationException e)
		{
			return new StoreExecution(null, PersistenceFailure.CONFLICT);
		}
		catch (RuntimeException e)
		{
			return new StoreExecution(null, PersistenceFailure.FAILURE);
		}
	}

	private LoadExecution executeLoad(PersistenceClaim claim)
	{
		try
		{
			return new LoadExecution(Objects.requireNonNull(_store.load(claim._slot._profileId), "Goal store load result must not be null."), PersistenceFailure.NONE);
		}
		catch (ConcurrentModificationException e)
		{
			return new LoadExecution(Optional.empty(), PersistenceFailure.CONFLICT);
		}
		catch (RuntimeException e)
		{
			return new LoadExecution(Optional.empty(), PersistenceFailure.FAILURE);
		}
	}

	private MutationResult reconcileMutation(PersistenceClaim claim, StoreExecution execution)
	{
		synchronized (_monitor)
		{
			final RuntimeSlot slot = claim._slot;
			if (!isPersistenceClaimCurrentLocked(claim))
			{
				return MutationResult.REJECTED;
			}
			final MutationResult result;
			if (execution._failure == PersistenceFailure.CONFLICT)
			{
				enterPersistenceConflictLocked(slot);
				result = MutationResult.PERSISTENCE_CONFLICT;
			}
			else if (execution._failure == PersistenceFailure.FAILURE)
			{
				enterPersistenceFailureLocked(slot);
				result = MutationResult.PERSISTENCE_FAILED;
			}
			else
			{
				switch (claim._kind)
				{
					case INSERT, REPLACE -> replaceRuntimeGoalLocked(slot, execution._storedGoal);
					case CLEAR ->
					{
						slot._generation++;
						cancelPlanLocked(slot, "goal.cleared");
						resetDecisionEvidenceLocked(slot);
						slot._goal = null;
						slot._componentRowVersion = -1;
						slot._runtimeState = RuntimeState.NO_GOAL;
						slot._reasonKey = "goal.absent";
					}
					default -> throw new IllegalStateException("Unexpected explicit mutation persistence kind.");
				}
				result = MutationResult.APPLIED;
			}
			clearPersistenceClaimLocked(slot, claim);
			finishDetachIfPendingLocked(slot);
			return result;
		}
	}

	private ReloadResult reconcileReload(PersistenceClaim claim, LoadExecution execution)
	{
		synchronized (_monitor)
		{
			final RuntimeSlot slot = claim._slot;
			if (!isPersistenceClaimCurrentLocked(claim))
			{
				return ReloadResult.REJECTED;
			}
			final ReloadResult result;
			if (execution._failure == PersistenceFailure.CONFLICT)
			{
				enterPersistenceConflictLocked(slot);
				result = ReloadResult.PERSISTENCE_CONFLICT;
			}
			else if (execution._failure == PersistenceFailure.FAILURE)
			{
				enterPersistenceFailureLocked(slot);
				result = ReloadResult.PERSISTENCE_FAILED;
			}
			else
			{
				applyStoredGoalLocked(slot, execution._storedGoal);
				result = ReloadResult.RELOADED;
			}
			clearPersistenceClaimLocked(slot, claim);
			finishDetachIfPendingLocked(slot);
			return result;
		}
	}

	private void reconcileTerminal(PersistenceClaim claim, StoreExecution execution)
	{
		synchronized (_monitor)
		{
			final RuntimeSlot slot = claim._slot;
			if (!isPersistenceClaimCurrentLocked(claim))
			{
				return;
			}
			if (execution._failure == PersistenceFailure.CONFLICT)
			{
				enterPersistenceConflictLocked(slot);
			}
			else if (execution._failure == PersistenceFailure.FAILURE)
			{
				enterPersistenceFailureLocked(slot);
			}
			else
			{
				slot._generation++;
				slot._goal = execution._storedGoal.goal();
				slot._componentRowVersion = execution._storedGoal.rowVersion();
				slot._plan = null;
				slot._currentStep = 0;
				slot._attempt = 0;
				slot._stepStartedNanos = STEP_START_UNSET;
				slot._retryDueNanos = 0;
				slot._runtimeState = RuntimeState.TERMINAL;
				resetDecisionEvidenceLocked(slot);
				if (claim._kind == PersistenceOperationKind.TERMINAL_COMPLETE)
				{
					_metrics.recordDecisionStepSucceeded();
					_metrics.recordDecisionPlanCompleted();
				}
				else
				{
					_metrics.recordDecisionStepFailed();
					_metrics.recordDecisionPlanFailed();
				}
			}
			clearPersistenceClaimLocked(slot, claim);
			slot._inFlight = false;
			finishDetachIfPendingLocked(slot);
		}
	}

	private boolean isPersistenceClaimCurrentLocked(PersistenceClaim claim)
	{
		final RuntimeSlot slot = claim._slot;
		return (_slots.get(slot._profileId) == slot) //
			&& slot._persistenceInFlight //
			&& (slot._persistenceOperationId == claim._operationId) //
			&& (slot._persistenceOperationKind == claim._kind) //
			&& (slot._goal == claim._expectedGoal) //
			&& (slot._componentRowVersion == claim._expectedComponentRowVersion);
	}

	private void clearPersistenceClaimLocked(RuntimeSlot slot, PersistenceClaim claim)
	{
		if ((slot._persistenceOperationId != claim._operationId) || (slot._persistenceOperationKind != claim._kind))
		{
			throw new IllegalStateException("Cannot release a stale decision persistence claim.");
		}
		slot._persistenceInFlight = false;
		slot._persistenceOperationId = 0;
		slot._persistenceOperationKind = null;
	}

	private RuntimeSlot mutableSlotLocked(long profileId)
	{
		if (_state != State.RUNNING)
		{
			_metrics.recordDecisionMutationRejected();
			return null;
		}
		final RuntimeSlot slot = _slots.get(profileId);
		if ((slot == null) || slot._detachPending || requiresExplicitReload(slot._runtimeState))
		{
			_metrics.recordDecisionMutationRejected();
			return null;
		}
		return slot;
	}

	private void enterPersistenceConflictLocked(RuntimeSlot slot)
	{
		slot._generation++;
		cancelPlanLocked(slot, "persistence.conflict");
		resetDecisionEvidenceLocked(slot);
		slot._runtimeState = RuntimeState.PERSISTENCE_CONFLICT_REQUIRES_EXPLICIT_RELOAD;
		_metrics.recordDecisionPersistenceConflict();
	}

	private void enterPersistenceFailureLocked(RuntimeSlot slot)
	{
		slot._generation++;
		cancelPlanLocked(slot, "persistence.failure");
		resetDecisionEvidenceLocked(slot);
		slot._runtimeState = RuntimeState.PERSISTENCE_FAILURE_REQUIRES_EXPLICIT_RELOAD;
		_metrics.recordDecisionPersistenceFailure();
	}

	private static boolean requiresExplicitReload(RuntimeState runtimeState)
	{
		return (runtimeState == RuntimeState.PERSISTENCE_CONFLICT_REQUIRES_EXPLICIT_RELOAD) || (runtimeState == RuntimeState.PERSISTENCE_FAILURE_REQUIRES_EXPLICIT_RELOAD);
	}

	private static void resetDecisionEvidenceLocked(RuntimeSlot slot)
	{
		slot._selectedCandidateKey = null;
		slot._selectedScore = -1;
		slot._lastResult = null;
		slot._explanations = List.of();
	}

	private boolean isPlanCurrent(RuntimeSlot slot, long generation, PhantomPlan plan)
	{
		synchronized (_monitor)
		{
			return isCurrentLocked(slot, generation, slot._goal) && (slot._plan == plan);
		}
	}

	private boolean isCurrentLocked(RuntimeSlot slot, long generation, PhantomGoal goal)
	{
		return (_state == State.RUNNING) && (_slots.get(slot._profileId) == slot) && !slot._detachPending && (slot._generation == generation) && (slot._goal == goal);
	}

	private void finishStaleLocked(RuntimeSlot slot)
	{
		slot._inFlight = false;
		_metrics.recordDecisionStaleResult();
		finishDetachIfPendingLocked(slot);
	}

	private void finishDetachIfPendingLocked(RuntimeSlot slot)
	{
		if (slot._detachPending && !slot._inFlight && !slot._persistenceInFlight)
		{
			removeSlotLocked(slot);
		}
	}

	private void removeSlotLocked(RuntimeSlot slot)
	{
		if (_slots.remove(slot._profileId, slot))
		{
			_metrics.recordDecisionDetached();
		}
	}

	private RuntimeSnapshot snapshotLocked(RuntimeSlot slot)
	{
		return new RuntimeSnapshot(
			slot._profileId,
			slot._goal != null ? slot._goal.goalId() : 0,
			slot._goal != null ? slot._goal.goalType() : null,
			slot._goal != null ? slot._goal.revision() : -1,
			slot._goal != null ? slot._goal.status() : null,
			slot._runtimeState,
			slot._decisionSequence,
			slot._selectedCandidateKey,
			slot._selectedScore,
			slot._plan != null ? slot._plan.planId() : 0,
			slot._plan != null ? slot._currentStep : -1,
			slot._attempt,
			slot._lastResult,
			slot._reasonKey,
			slot._explanations,
			slot._inFlight,
			slot._persistenceInFlight,
			slot._persistenceOperationId,
			slot._persistenceOperationKind,
			slot._generation,
			slot._activityGeneration,
			slot._componentRowVersion);
	}

	private static void validatePlan(PhantomPlan plan, PhantomGoal goal, PhantomDecisionCandidate candidate, Map<String, PhantomStepHandler> handlers, long logicalNowNanos)
	{
		Objects.requireNonNull(plan, "Plan factory returned null.");
		if ((plan.goalId() != goal.goalId()) || !plan.candidateKey().equals(candidate.key()))
		{
			throw new IllegalArgumentException("Plan must reference the exact selected goal and candidate.");
		}
		if (plan.createdAtLogicalNanos() != logicalNowNanos)
		{
			throw new IllegalArgumentException("Plan must use the current scheduler logical creation time.");
		}
		for (PhantomPlanStep step : plan.steps())
		{
			if (!handlers.containsKey(step.actionKey()))
			{
				throw new IllegalArgumentException("Plan references an unregistered action key.");
			}
		}
	}

	private static boolean timedOut(long nowNanos, long startNanos, long timeoutMillis)
	{
		return (nowNanos >= startNanos) && ((nowNanos - startNanos) >= millisToNanos(timeoutMillis));
	}

	private static long millisToNanos(long millis)
	{
		return millis > (Long.MAX_VALUE / 1_000_000L) ? Long.MAX_VALUE : millis * 1_000_000L;
	}

	private static long saturatingAdd(long left, long right)
	{
		return left > (Long.MAX_VALUE - right) ? Long.MAX_VALUE : left + right;
	}

	@FunctionalInterface
	public interface DecisionAdmission
	{
		boolean permits(long profileId);
		default boolean permitsCandidate(long profileId, String candidateKey) { return true; }

		/** The lease spans planning, the handler and persistence; a transfer must wait for its close. */
		default AdmissionLease tryAcquire(long profileId)
		{
			if (!permits(profileId)) { return null; }
			return new AdmissionLease()
			{
				@Override public boolean valid() { return permits(profileId); }
				@Override public void close() { }
			};
		}

		static DecisionAdmission allowAll()
		{
			return _ -> true;
		}
	}

	public interface AdmissionLease extends AutoCloseable
	{
		boolean valid();
		default long executionGeneration() { return 0; }
		default boolean yieldRequested() { return false; }
		@Override void close();
	}

	private static boolean executionCurrent(AdmissionLease execution)
	{
		try { return execution.valid(); }
		catch (RuntimeException unavailable) { return false; }
	}

	@FunctionalInterface
	public interface DecisionObserver
	{
		default boolean interested(long profileId)
		{
			return true;
		}

		void onDecision(PhantomActivityState activityState, RuntimeSnapshot snapshot);

		default void onDecision(PhantomActivityState activityState, RuntimeSnapshot snapshot, long logicalNowNanos)
		{
			onDecision(activityState, snapshot);
		}
	}

	public enum State
	{
		NEW,
		RUNNING,
		STOPPING,
		STOPPED
	}

	public enum RuntimeState
	{
		NO_GOAL,
		NEEDS_REPLAN,
		NO_CANDIDATE,
		EXECUTING,
		WAITING_RETRY,
		TERMINAL,
		PERSISTENCE_CONFLICT_REQUIRES_EXPLICIT_RELOAD,
		PERSISTENCE_FAILURE_REQUIRES_EXPLICIT_RELOAD
	}

	public enum AttachResult
	{
		ATTACHED,
		ALREADY_ATTACHED,
		INVALID_PROFILE_ID,
		PROFILE_NOT_FOUND,
		CAPACITY_REJECTED,
		NOT_RUNNING,
		CANCELLED_BY_STOP,
		PERSISTENCE_FAILED
	}

	public enum DetachResult
	{
		DETACHED,
		PENDING,
		NOT_ATTACHED
	}

	public enum MutationResult
	{
		APPLIED,
		REJECTED,
		GOAL_ALREADY_PRESENT,
		GOAL_NOT_PRESENT,
		REVISION_REJECTED,
		BUSY,
		PERSISTENCE_CONFLICT,
		PERSISTENCE_FAILED
	}

	public enum ReloadResult
	{
		RELOADED,
		REJECTED,
		BUSY,
		NOT_RUNNING,
		PERSISTENCE_CONFLICT,
		PERSISTENCE_FAILED
	}

	public enum BeginStopResult
	{
		STARTED,
		ALREADY_STOPPING,
		ALREADY_STOPPED
	}

	public enum PersistenceOperationKind
	{
		INSERT,
		REPLACE,
		CLEAR,
		RELOAD,
		TERMINAL_COMPLETE,
		TERMINAL_FAIL
	}

	public record RuntimeSnapshot(long profileId, long goalId, String goalType, long goalRevision, PhantomGoalStatus goalStatus, RuntimeState runtimeState, long decisionSequence, String selectedCandidateKey, int selectedScore, long planId, int currentStep, int attempt, PhantomStepResult.Type lastResult, String reasonKey, List<CandidateEvaluation> topCandidateEvaluations, boolean inFlight, boolean persistenceInFlight, long persistenceOperationId, PersistenceOperationKind persistenceOperationKind, long generation, long activityGeneration, long componentRowVersion)
	{
		public RuntimeSnapshot
		{
			topCandidateEvaluations = List.copyOf(topCandidateEvaluations.subList(0, Math.min(MAX_EXPLANATIONS, topCandidateEvaluations.size())));
		}
	}

	public record EngineSnapshot(State state, int attached, int pendingAttaches, int capacity, int registeredCandidates, int registeredHandlers, long inFlight, long persistenceInFlight)
	{
		public static EngineSnapshot inactive()
		{
			return new EngineSnapshot(State.STOPPED, 0, 0, 0, 0, 0, 0, 0);
		}
	}

	private static final class RuntimeSlot
	{
		private final long _profileId;
		private PhantomCapabilitySet _capabilities;
		private PhantomGoal _goal;
		private long _componentRowVersion = -1;
		private RuntimeState _runtimeState = RuntimeState.NO_GOAL;
		private long _generation;
		private long _executionGeneration;
		private long _activityGeneration;
		private long _decisionSequence;
		private PhantomPlan _plan;
		private int _currentStep;
		private int _attempt;
		private long _stepStartedNanos = STEP_START_UNSET;
		private long _retryDueNanos;
		private String _selectedCandidateKey;
		private int _selectedScore = -1;
		private PhantomStepResult.Type _lastResult;
		private String _reasonKey = "goal.absent";
		private List<CandidateEvaluation> _explanations = List.of();
		private boolean _inFlight;
		private boolean _persistenceInFlight;
		private long _persistenceOperationId;
		private PersistenceOperationKind _persistenceOperationKind;
		private boolean _detachPending;

		private RuntimeSlot(long profileId, PhantomCapabilitySet capabilities)
		{
			_profileId = profileId;
			_capabilities = capabilities;
		}
	}

	private record WorkClaim(RuntimeSlot _slot, long _generation, PhantomGoal _goal, PhantomPlan _existingPlan, PhantomPlanningContext _planningContext)
	{
	}

	private record HandlerClaim(RuntimeSlot _slot, long _generation, PhantomGoal _goal, PhantomPlan _plan, PhantomStepHandler _handler, PhantomStepContext _context)
	{
	}

	private enum PersistenceFailure
	{
		NONE,
		CONFLICT,
		FAILURE
	}

	private record PersistenceClaim(RuntimeSlot _slot, long _operationId, PersistenceOperationKind _kind, PhantomGoal _expectedGoal, long _expectedComponentRowVersion, PhantomGoal _replacementGoal)
	{
	}

	private record StoreExecution(StoredGoal _storedGoal, PersistenceFailure _failure)
	{
	}

	private record LoadExecution(Optional<StoredGoal> _storedGoal, PersistenceFailure _failure)
	{
	}
}
