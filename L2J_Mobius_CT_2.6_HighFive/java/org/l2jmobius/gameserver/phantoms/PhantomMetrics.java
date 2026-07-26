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
package org.l2jmobius.gameserver.phantoms;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;

import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityOverloadLevel;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState;

/**
 * Fixed aggregate counters for Phantom World lifecycle and shared activity scheduling.
 */
public final class PhantomMetrics
{
	private final AtomicLong _lifecycleStarts = new AtomicLong();
	private final AtomicLong _lifecycleStops = new AtomicLong();
	private final AtomicLong _queueAccepted = new AtomicLong();
	private final AtomicLong _queueRejected = new AtomicLong();
	private final AtomicLong _traceRecorded = new AtomicLong();
	private final AtomicLong _traceDropped = new AtomicLong();
	private final AtomicLong _materializationRequested = new AtomicLong();
	private final AtomicLong _materializationSucceeded = new AtomicLong();
	private final AtomicLong _materializationRejected = new AtomicLong();
	private final AtomicLong _materializationFailuresRetained = new AtomicLong();
	private final AtomicLong _dematerializationSucceeded = new AtomicLong();
	private final AtomicLong _cleanupFailuresRetained = new AtomicLong();
	private final AtomicLong _retainedRecoverySucceeded = new AtomicLong();
	private final AtomicLong _retainedRecoveryRejected = new AtomicLong();
	private final AtomicLong _shutdownFailures = new AtomicLong();
	private final AtomicLong _activeCurrent = new AtomicLong();
	private final AtomicLong _activePeak = new AtomicLong();
	private final AtomicLong _activityRegisteredCurrent = new AtomicLong();
	private final AtomicLong _activityRegisteredPeak = new AtomicLong();
	private final AtomicLong _activityRegistrationAccepted = new AtomicLong();
	private final AtomicLong _activityRegistrationRejected = new AtomicLong();
	private final AtomicLong _activitySignalAccepted = new AtomicLong();
	private final AtomicLong _activitySignalCoalesced = new AtomicLong();
	private final AtomicLong _activitySignalStale = new AtomicLong();
	private final AtomicLong _activitySignalRejected = new AtomicLong();
	private final AtomicLong _activitySignalExpired = new AtomicLong();
	private final AtomicLong _activityPulsesStarted = new AtomicLong();
	private final AtomicLong _activityPulsesCompleted = new AtomicLong();
	private final AtomicLong _activityPulsesOverrun = new AtomicLong();
	private final AtomicLong _activityReadyEnqueued = new AtomicLong();
	private final AtomicLong _activityReadyBackpressure = new AtomicLong();
	private final AtomicLong _activityDueMoved = new AtomicLong();
	private final AtomicLong _activityDueDeferred = new AtomicLong();
	private final AtomicLong _activityWorkDelivered = new AtomicLong();
	private final AtomicLong _activityWorkFailures = new AtomicLong();
	private final AtomicLong _activityPromotions = new AtomicLong();
	private final AtomicLong _activityDemotions = new AtomicLong();
	private final AtomicLong _activityTransitionSucceeded = new AtomicLong();
	private final AtomicLong _activityTransitionTransientBlocked = new AtomicLong();
	private final AtomicLong _activityTransitionRetainedFailure = new AtomicLong();
	private final AtomicLong _activityExplicitRetries = new AtomicLong();
	private final AtomicLong _activityBeginStop = new AtomicLong();
	private final AtomicLong _activityFinishStop = new AtomicLong();
	private final AtomicLong _activityOverloadTransitions = new AtomicLong();
	private final AtomicLong _activityOverloadCurrent = new AtomicLong();
	private final AtomicLong _activityOverloadPeak = new AtomicLong();
	private final AtomicLongArray _activityStateCounts = new AtomicLongArray(5);
	private final AtomicLong _decisionAttachedCurrent = new AtomicLong();
	private final AtomicLong _decisionAttachedPeak = new AtomicLong();
	private final AtomicLong _decisionMutationRejected = new AtomicLong();
	private final AtomicLong _decisionReloadRejected = new AtomicLong();
	private final AtomicLong _decisionDecisions = new AtomicLong();
	private final AtomicLong _decisionNoGoal = new AtomicLong();
	private final AtomicLong _decisionNoCandidate = new AtomicLong();
	private final AtomicLong _decisionCandidatesEvaluated = new AtomicLong();
	private final AtomicLong _decisionCandidatesBlocked = new AtomicLong();
	private final AtomicLong _decisionCandidatesFailed = new AtomicLong();
	private final AtomicLong _decisionPlansCreated = new AtomicLong();
	private final AtomicLong _decisionPlansReplanned = new AtomicLong();
	private final AtomicLong _decisionPlansCompleted = new AtomicLong();
	private final AtomicLong _decisionPlansFailed = new AtomicLong();
	private final AtomicLong _decisionPlansCancelled = new AtomicLong();
	private final AtomicLong _decisionPlansTimedOut = new AtomicLong();
	private final AtomicLong _decisionStepsAttempted = new AtomicLong();
	private final AtomicLong _decisionStepsSucceeded = new AtomicLong();
	private final AtomicLong _decisionStepsRetried = new AtomicLong();
	private final AtomicLong _decisionStepsFailed = new AtomicLong();
	private final AtomicLong _decisionStepsCancelled = new AtomicLong();
	private final AtomicLong _decisionPersistenceConflicts = new AtomicLong();
	private final AtomicLong _decisionStaleResults = new AtomicLong();
	private final AtomicLong _decisionStopFailures = new AtomicLong();

	public void recordLifecycleStart()
	{
		_lifecycleStarts.incrementAndGet();
	}

	public void recordLifecycleStop()
	{
		_lifecycleStops.incrementAndGet();
	}

	public void recordQueueAccepted()
	{
		_queueAccepted.incrementAndGet();
	}

	public void recordQueueRejected()
	{
		_queueRejected.incrementAndGet();
	}

	public void recordTraceRecorded()
	{
		_traceRecorded.incrementAndGet();
	}

	public void recordTraceDropped()
	{
		_traceDropped.incrementAndGet();
	}

	public void recordMaterializationRequested()
	{
		_materializationRequested.incrementAndGet();
	}

	public void recordMaterializationSucceeded()
	{
		_materializationSucceeded.incrementAndGet();
		final long current = _activeCurrent.incrementAndGet();
		_activePeak.accumulateAndGet(current, Math::max);
	}

	public void recordMaterializationRejected()
	{
		_materializationRejected.incrementAndGet();
	}

	public void recordMaterializationFailureRetained()
	{
		_materializationFailuresRetained.incrementAndGet();
	}

	public void recordDematerializationSucceeded()
	{
		_dematerializationSucceeded.incrementAndGet();
		_activeCurrent.updateAndGet(current -> Math.max(0, current - 1));
	}

	public void recordCleanupFailureRetained()
	{
		_cleanupFailuresRetained.incrementAndGet();
	}

	public void recordRetainedRecoverySucceeded()
	{
		_retainedRecoverySucceeded.incrementAndGet();
	}

	public void recordRetainedRecoveryRejected()
	{
		_retainedRecoveryRejected.incrementAndGet();
	}

	public void recordShutdownFailure()
	{
		_shutdownFailures.incrementAndGet();
	}

	public void recordActivityRegistered(PhantomActivityState state)
	{
		_activityRegistrationAccepted.incrementAndGet();
		final long current = _activityRegisteredCurrent.incrementAndGet();
		_activityRegisteredPeak.accumulateAndGet(current, Math::max);
		_activityStateCounts.incrementAndGet(stateIndex(state));
	}

	public void recordActivityRegistrationRejected()
	{
		_activityRegistrationRejected.incrementAndGet();
	}

	public void recordActivityUnregistered(PhantomActivityState state)
	{
		_activityRegisteredCurrent.updateAndGet(current -> Math.max(0, current - 1));
		_activityStateCounts.updateAndGet(stateIndex(state), current -> Math.max(0, current - 1));
	}

	public void recordActivitySignalAccepted()
	{
		_activitySignalAccepted.incrementAndGet();
	}

	public void recordActivitySignalCoalesced()
	{
		_activitySignalCoalesced.incrementAndGet();
	}

	public void recordActivitySignalStale()
	{
		_activitySignalStale.incrementAndGet();
	}

	public void recordActivitySignalRejected()
	{
		_activitySignalRejected.incrementAndGet();
	}

	public void recordActivitySignalExpired()
	{
		_activitySignalExpired.incrementAndGet();
	}

	public void recordActivityPulseStarted()
	{
		_activityPulsesStarted.incrementAndGet();
	}

	public void recordActivityPulseCompleted()
	{
		_activityPulsesCompleted.incrementAndGet();
	}

	public void recordActivityPulseOverrun()
	{
		_activityPulsesOverrun.incrementAndGet();
	}

	public void recordActivityReadyEnqueued()
	{
		_activityReadyEnqueued.incrementAndGet();
	}

	public void recordActivityReadyBackpressure()
	{
		_activityReadyBackpressure.incrementAndGet();
	}

	public void recordActivityDueMoved()
	{
		_activityDueMoved.incrementAndGet();
	}

	public void recordActivityDueDeferred()
	{
		_activityDueDeferred.incrementAndGet();
	}

	public void recordActivityWorkDelivered()
	{
		_activityWorkDelivered.incrementAndGet();
	}

	public void recordActivityWorkFailure()
	{
		_activityWorkFailures.incrementAndGet();
	}

	public void recordActivityTransition(PhantomActivityState previous, PhantomActivityState current)
	{
		_activityTransitionSucceeded.incrementAndGet();
		if (current.isHigherDetailThan(previous))
		{
			_activityPromotions.incrementAndGet();
		}
		else
		{
			_activityDemotions.incrementAndGet();
		}
		_activityStateCounts.updateAndGet(stateIndex(previous), value -> Math.max(0, value - 1));
		_activityStateCounts.incrementAndGet(stateIndex(current));
	}

	public void recordActivityTransitionTransientBlock()
	{
		_activityTransitionTransientBlocked.incrementAndGet();
	}

	public void recordActivityTransitionRetainedFailure()
	{
		_activityTransitionRetainedFailure.incrementAndGet();
	}

	public void recordActivityExplicitRetry()
	{
		_activityExplicitRetries.incrementAndGet();
	}

	public void recordActivityBeginStop()
	{
		_activityBeginStop.incrementAndGet();
	}

	public void recordActivityFinishStop()
	{
		_activityFinishStop.incrementAndGet();
	}

	public void recordActivityOverloadTransition(PhantomActivityOverloadLevel level)
	{
		_activityOverloadTransitions.incrementAndGet();
		final long code = overloadCode(level);
		_activityOverloadCurrent.set(code);
		_activityOverloadPeak.accumulateAndGet(code, Math::max);
	}

	public void recordDecisionAttached()
	{
		final long current = _decisionAttachedCurrent.incrementAndGet();
		_decisionAttachedPeak.accumulateAndGet(current, Math::max);
	}

	public void recordDecisionDetached()
	{
		_decisionAttachedCurrent.updateAndGet(current -> Math.max(0, current - 1));
	}

	public void recordDecisionMutationRejected()
	{
		_decisionMutationRejected.incrementAndGet();
	}

	public void recordDecisionReloadRejected()
	{
		_decisionReloadRejected.incrementAndGet();
	}

	public void recordDecision()
	{
		_decisionDecisions.incrementAndGet();
	}

	public void recordDecisionNoGoal()
	{
		_decisionNoGoal.incrementAndGet();
	}

	public void recordDecisionNoCandidate()
	{
		_decisionNoCandidate.incrementAndGet();
	}

	public void recordDecisionCandidates(int evaluated, int blocked, int failed)
	{
		_decisionCandidatesEvaluated.addAndGet(evaluated);
		_decisionCandidatesBlocked.addAndGet(blocked);
		_decisionCandidatesFailed.addAndGet(failed);
	}

	public void recordDecisionPlanCreated()
	{
		_decisionPlansCreated.incrementAndGet();
	}

	public void recordDecisionPlanReplanned()
	{
		_decisionPlansReplanned.incrementAndGet();
	}

	public void recordDecisionPlanCompleted()
	{
		_decisionPlansCompleted.incrementAndGet();
	}

	public void recordDecisionPlanFailed()
	{
		_decisionPlansFailed.incrementAndGet();
	}

	public void recordDecisionPlanCancelled()
	{
		_decisionPlansCancelled.incrementAndGet();
	}

	public void recordDecisionPlanTimedOut()
	{
		_decisionPlansTimedOut.incrementAndGet();
	}

	public void recordDecisionStepAttempted()
	{
		_decisionStepsAttempted.incrementAndGet();
	}

	public void recordDecisionStepSucceeded()
	{
		_decisionStepsSucceeded.incrementAndGet();
	}

	public void recordDecisionStepRetried()
	{
		_decisionStepsRetried.incrementAndGet();
	}

	public void recordDecisionStepFailed()
	{
		_decisionStepsFailed.incrementAndGet();
	}

	public void recordDecisionStepCancelled()
	{
		_decisionStepsCancelled.incrementAndGet();
	}

	public void recordDecisionPersistenceConflict()
	{
		_decisionPersistenceConflicts.incrementAndGet();
	}

	public void recordDecisionStaleResult()
	{
		_decisionStaleResults.incrementAndGet();
	}

	public void recordDecisionStopFailure()
	{
		_decisionStopFailures.incrementAndGet();
	}

	public Snapshot snapshot()
	{
		return new Snapshot(
			_lifecycleStarts.get(),
			_lifecycleStops.get(),
			_queueAccepted.get(),
			_queueRejected.get(),
			_traceRecorded.get(),
			_traceDropped.get(),
			_materializationRequested.get(),
			_materializationSucceeded.get(),
			_materializationRejected.get(),
			_materializationFailuresRetained.get(),
			_dematerializationSucceeded.get(),
			_cleanupFailuresRetained.get(),
			_retainedRecoverySucceeded.get(),
			_retainedRecoveryRejected.get(),
			_shutdownFailures.get(),
			_activeCurrent.get(),
			_activePeak.get(),
			activitySnapshot(),
			decisionSnapshot());
	}

	private ActivitySnapshot activitySnapshot()
	{
		return new ActivitySnapshot(
			_activityRegisteredCurrent.get(),
			_activityRegisteredPeak.get(),
			_activityRegistrationAccepted.get(),
			_activityRegistrationRejected.get(),
			_activitySignalAccepted.get(),
			_activitySignalCoalesced.get(),
			_activitySignalStale.get(),
			_activitySignalRejected.get(),
			_activitySignalExpired.get(),
			_activityPulsesStarted.get(),
			_activityPulsesCompleted.get(),
			_activityPulsesOverrun.get(),
			_activityReadyEnqueued.get(),
			_activityReadyBackpressure.get(),
			_activityDueMoved.get(),
			_activityDueDeferred.get(),
			_activityWorkDelivered.get(),
			_activityWorkFailures.get(),
			_activityPromotions.get(),
			_activityDemotions.get(),
			_activityTransitionSucceeded.get(),
			_activityTransitionTransientBlocked.get(),
			_activityTransitionRetainedFailure.get(),
			_activityExplicitRetries.get(),
			_activityBeginStop.get(),
			_activityFinishStop.get(),
			_activityOverloadTransitions.get(),
			_activityOverloadCurrent.get(),
			_activityOverloadPeak.get(),
			List.of(_activityStateCounts.get(0), _activityStateCounts.get(1), _activityStateCounts.get(2), _activityStateCounts.get(3), _activityStateCounts.get(4)));
	}

	private DecisionSnapshot decisionSnapshot()
	{
		return new DecisionSnapshot(
			_decisionAttachedCurrent.get(),
			_decisionAttachedPeak.get(),
			_decisionMutationRejected.get(),
			_decisionReloadRejected.get(),
			_decisionDecisions.get(),
			_decisionNoGoal.get(),
			_decisionNoCandidate.get(),
			_decisionCandidatesEvaluated.get(),
			_decisionCandidatesBlocked.get(),
			_decisionCandidatesFailed.get(),
			_decisionPlansCreated.get(),
			_decisionPlansReplanned.get(),
			_decisionPlansCompleted.get(),
			_decisionPlansFailed.get(),
			_decisionPlansCancelled.get(),
			_decisionPlansTimedOut.get(),
			_decisionStepsAttempted.get(),
			_decisionStepsSucceeded.get(),
			_decisionStepsRetried.get(),
			_decisionStepsFailed.get(),
			_decisionStepsCancelled.get(),
			_decisionPersistenceConflicts.get(),
			_decisionStaleResults.get(),
			_decisionStopFailures.get());
	}

	private static int stateIndex(PhantomActivityState state)
	{
		return switch (state)
		{
			case ACTIVE -> 0;
			case NEARBY_PERCEPTIBLE -> 1;
			case WARM -> 2;
			case BACKGROUND -> 3;
			case SLEEPING -> 4;
		};
	}

	private static long overloadCode(PhantomActivityOverloadLevel level)
	{
		return switch (level)
		{
			case NORMAL -> 0;
			case ELEVATED -> 1;
			case HIGH -> 2;
			case CRITICAL -> 3;
		};
	}

	public record Snapshot(long lifecycleStarts, long lifecycleStops, long queueAccepted, long queueRejected, long traceRecorded, long traceDropped, long materializationRequested, long materializationSucceeded, long materializationRejected, long materializationFailuresRetained, long dematerializationSucceeded, long cleanupFailuresRetained, long retainedRecoverySucceeded, long retainedRecoveryRejected, long shutdownFailures, long activeCurrent, long activePeak, ActivitySnapshot activity, DecisionSnapshot decision)
	{
		public boolean isZero()
		{
			return (lifecycleStarts == 0) //
				&& (lifecycleStops == 0) //
				&& (queueAccepted == 0) //
				&& (queueRejected == 0) //
				&& (traceRecorded == 0) //
				&& (traceDropped == 0) //
				&& (materializationRequested == 0) //
				&& (materializationSucceeded == 0) //
				&& (materializationRejected == 0) //
				&& (materializationFailuresRetained == 0) //
				&& (dematerializationSucceeded == 0) //
				&& (cleanupFailuresRetained == 0) //
				&& (retainedRecoverySucceeded == 0) //
				&& (retainedRecoveryRejected == 0) //
				&& (shutdownFailures == 0) //
				&& (activeCurrent == 0) //
				&& (activePeak == 0) //
				&& activity.isZero() //
				&& decision.isZero();
		}
	}

	public record ActivitySnapshot(long registeredCurrent, long registeredPeak, long registrationAccepted, long registrationRejected, long signalAccepted, long signalCoalesced, long signalStale, long signalRejected, long signalExpired, long pulsesStarted, long pulsesCompleted, long pulsesOverrun, long readyEnqueued, long readyBackpressure, long dueMoved, long dueDeferred, long workDelivered, long workFailures, long promotions, long demotions, long transitionSucceeded, long transitionTransientBlocked, long transitionRetainedFailure, long explicitRetries, long beginStop, long finishStop, long overloadTransitions, long overloadCurrent, long overloadPeak, List<Long> stateCounts)
	{
		public ActivitySnapshot
		{
			stateCounts = List.copyOf(stateCounts);
		}

		public boolean isZero()
		{
			return (registeredCurrent == 0) //
				&& (registeredPeak == 0) //
				&& (registrationAccepted == 0) //
				&& (registrationRejected == 0) //
				&& (signalAccepted == 0) //
				&& (signalCoalesced == 0) //
				&& (signalStale == 0) //
				&& (signalRejected == 0) //
				&& (signalExpired == 0) //
				&& (pulsesStarted == 0) //
				&& (pulsesCompleted == 0) //
				&& (pulsesOverrun == 0) //
				&& (readyEnqueued == 0) //
				&& (readyBackpressure == 0) //
				&& (dueMoved == 0) //
				&& (dueDeferred == 0) //
				&& (workDelivered == 0) //
				&& (workFailures == 0) //
				&& (promotions == 0) //
				&& (demotions == 0) //
				&& (transitionSucceeded == 0) //
				&& (transitionTransientBlocked == 0) //
				&& (transitionRetainedFailure == 0) //
				&& (explicitRetries == 0) //
				&& (beginStop == 0) //
				&& (finishStop == 0) //
				&& (overloadTransitions == 0) //
				&& (overloadCurrent == 0) //
				&& (overloadPeak == 0) //
				&& stateCounts.stream().allMatch(value -> value == 0);
		}
	}

	public record DecisionSnapshot(long attachedCurrent, long attachedPeak, long mutationRejected, long reloadRejected, long decisions, long noGoal, long noCandidate, long candidatesEvaluated, long candidatesBlocked, long candidatesFailed, long plansCreated, long plansReplanned, long plansCompleted, long plansFailed, long plansCancelled, long plansTimedOut, long stepsAttempted, long stepsSucceeded, long stepsRetried, long stepsFailed, long stepsCancelled, long persistenceConflicts, long staleResults, long stopFailures)
	{
		public boolean isZero()
		{
			return (attachedCurrent == 0) //
				&& (attachedPeak == 0) //
				&& (mutationRejected == 0) //
				&& (reloadRejected == 0) //
				&& (decisions == 0) //
				&& (noGoal == 0) //
				&& (noCandidate == 0) //
				&& (candidatesEvaluated == 0) //
				&& (candidatesBlocked == 0) //
				&& (candidatesFailed == 0) //
				&& (plansCreated == 0) //
				&& (plansReplanned == 0) //
				&& (plansCompleted == 0) //
				&& (plansFailed == 0) //
				&& (plansCancelled == 0) //
				&& (plansTimedOut == 0) //
				&& (stepsAttempted == 0) //
				&& (stepsSucceeded == 0) //
				&& (stepsRetried == 0) //
				&& (stepsFailed == 0) //
				&& (stepsCancelled == 0) //
				&& (persistenceConflicts == 0) //
				&& (staleResults == 0) //
				&& (stopFailures == 0);
		}
	}
}
