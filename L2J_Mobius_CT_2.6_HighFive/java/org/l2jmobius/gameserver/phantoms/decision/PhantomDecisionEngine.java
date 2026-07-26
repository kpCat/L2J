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
	private final PhantomUtilitySelector _selector = new PhantomUtilitySelector();
	private final int _maximumAttachedProfiles;
	private final Map<Long, RuntimeSlot> _slots = new HashMap<>();
	private final Set<Long> _pendingAttaches = new HashSet<>();
	private long _persistenceOperationSequence;
	private volatile State _state = State.NEW;

	public PhantomDecisionEngine(PhantomGoalStore store, PhantomCandidateRegistry candidateRegistry, PhantomStepHandlerRegistry handlerRegistry, PhantomMetrics metrics, int maximumAttachedProfiles)
	{
		_store = Objects.requireNonNull(store, "Goal store must not be null.");
		_candidateRegistry = Objects.requireNonNull(candidateRegistry, "Candidate registry must not be null.");
		_handlerRegistry = Objects.requireNonNull(handlerRegistry, "Handler registry must not be null.");
		_metrics = Objects.requireNonNull(metrics, "Metrics must not be null.");
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
		final WorkClaim claim;
		synchronized (_monitor)
		{
			claim = claimWorkLocked(workItem);
		}
		if (claim == null)
		{
			return;
		}

		PhantomPlan plan = claim._existingPlan;
		Selection selection = null;
		if (plan == null)
		{
			selection = _selector.select(_candidateRegistry.snapshot(), claim._planningContext);
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
			handlerClaim = prepareHandlerLocked(claim, workItem, plan, selection);
		}
		if (handlerClaim == null)
		{
			return;
		}

		PhantomStepResult result;
		try
		{
			result = handlerClaim._handler.execute(handlerClaim._context);
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
	}

	public Optional<RuntimeSnapshot> find(long profileId)
	{
		synchronized (_monitor)
		{
			final RuntimeSlot slot = _slots.get(profileId);
			return slot == null ? Optional.empty() : Optional.of(snapshotLocked(slot));
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

	private WorkClaim claimWorkLocked(PhantomActivityWorkItem workItem)
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
		final PhantomPlanningContext planningContext = new PhantomPlanningContext(slot._profileId, slot._goal, slot._capabilities, workItem.effectiveState(), workItem.logicalNowNanos(), slot._decisionSequence);
		return new WorkClaim(slot, slot._generation, slot._goal, slot._plan, planningContext);
	}

	private HandlerClaim prepareHandlerLocked(WorkClaim claim, PhantomActivityWorkItem workItem, PhantomPlan planned, Selection selection)
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
		final PhantomCancellationToken token = () -> !isGenerationCurrent(slot, generation);
		final PhantomStepContext context = new PhantomStepContext(slot._profileId, slot._goal, plan, step, workItem.effectiveState(), workItem.logicalNowNanos(), slot._attempt, token);
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

	private boolean isGenerationCurrent(RuntimeSlot slot, long generation)
	{
		synchronized (_monitor)
		{
			return isCurrentLocked(slot, generation, slot._goal);
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
