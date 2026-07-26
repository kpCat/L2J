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

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.l2jmobius.gameserver.phantoms.PhantomScheduler.BeginStopResult;
import org.l2jmobius.gameserver.phantoms.PhantomScheduler.RegistrationStatus;
import org.l2jmobius.gameserver.phantoms.PhantomScheduler.RetryStatus;
import org.l2jmobius.gameserver.phantoms.PhantomScheduler.SignalStatus;
import org.l2jmobius.gameserver.phantoms.PhantomScheduler.UnregisterStatus;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityMaterializationPort;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityMaterializationPort.Outcome;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityMaterializationPort.TransitionOutcome;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityOverloadLevel;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivitySnapshot;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityTransitionStatus;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityWorkItem;
import org.l2jmobius.gameserver.phantoms.activity.PhantomRelevanceSignal;
import org.l2jmobius.gameserver.phantoms.activity.PhantomSchedulerPolicy;
import org.l2jmobius.gameserver.phantoms.decision.PhantomCandidateRegistry;
import org.l2jmobius.gameserver.phantoms.decision.PhantomConsideration;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDecisionCandidate;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDecisionEngine;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoal;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStore;
import org.l2jmobius.gameserver.phantoms.decision.PhantomPlan;
import org.l2jmobius.gameserver.phantoms.decision.PhantomPlanStep;
import org.l2jmobius.gameserver.phantoms.decision.PhantomStepHandlerRegistry;
import org.l2jmobius.gameserver.phantoms.decision.PhantomStepResult;
import org.l2jmobius.gameserver.phantoms.decision.PhantomWeightedConsideration;
import org.l2jmobius.tests.phantoms.PhantomAssertions;
import org.l2jmobius.tests.phantoms.PhantomTestContext;
import org.l2jmobius.tests.phantoms.PhantomTestRegistry;
import org.l2jmobius.tests.phantoms.PhantomTestSuite;

public final class PhantomActivitySchedulerSuite implements PhantomTestSuite
{
	@Override
	public String id()
	{
		return "activity-scheduler";
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("01-start-zero-and-registration-bounds", _ -> testRegistration());
		registry.add("02-signal-validation-source-sequence-and-coalescing", _ -> testSignals());
		registry.add("03-queue-backpressure-leaves-signal-unchanged", _ -> testBackpressureAtomicity());
		registry.add("04-highest-detail-expiry-and-demotion-grace", _ -> testAggregationAndHysteresis());
		registry.add("05-active-nearby-canonical-materialization", _ -> testMaterializationBoundary());
		registry.add("06-clean-transition-block-bounded-retry", _ -> testTransientRetry());
		registry.add("07-retained-failure-explicit-retry", _ -> testRetainedFailure());
		registry.add("08-work-cadence-sleeping-and-sink-isolation", _ -> testWorkCadence());
		registry.add("09-fair-cohort-and-once-per-pulse", _ -> testFairness());
		registry.add("10-overload-cadence-only-no-demotion", _ -> testOverload());
		registry.add("11-unregister-materialized-and-stop-retention", _ -> testUnregisterAndStop());
		registry.add("12-fixed-metrics-and-bounded-snapshots", this::testMetrics);
		registry.add("13-in-flight-promotion-unregister-cleans-late-success", _ -> testInFlightUnregisterSuccess());
		registry.add("14-in-flight-promotion-unregister-retained-explicit-retry", _ -> testInFlightUnregisterRetained());
		registry.add("15-retained-dematerialization-new-active-requires-fresh-materialize", _ -> testRetainedDematerializationTruth());
		registry.add("16-retained-materialization-survives-withdrawal-and-expiry", _ -> testRetainedMaterializationSignalLoss());
		registry.add("17-stopping-quiesces-boundary-and-work", _ -> testStoppingQuiescence());
		registry.add("18-cleanup-retry-recomputes-warm-to-sleeping", _ -> testCleanupRetryCurrentSleeping());
		registry.add("19-cleanup-retry-recomputes-warm-to-active", _ -> testCleanupRetryCurrentActive());
		registry.add("20-manual-scheduler-drives-one-decision-slice", _ -> testDecisionSinkIntegration());
	}

	private void testRegistration()
	{
		final Fixture fixture = fixture(2, 2);
		PhantomAssertions.assertEquals(0, fixture.scheduler().snapshot().registered(), "Manual scheduler did not start empty.");
		PhantomAssertions.assertEquals(0, fixture.scheduler().snapshot().scheduledTaskCount(), "Manual scheduler reported a production future.");
		PhantomAssertions.assertEquals(RegistrationStatus.INVALID_PROFILE_ID, fixture.scheduler().register(0).status(), "Non-positive profile ID was accepted.");
		PhantomAssertions.assertEquals(RegistrationStatus.REGISTERED, fixture.scheduler().register(1).status(), "First profile was not registered.");
		PhantomAssertions.assertEquals(RegistrationStatus.ALREADY_REGISTERED, fixture.scheduler().register(1).status(), "Duplicate registration was not idempotent/distinguishable.");
		PhantomAssertions.assertEquals(RegistrationStatus.REGISTERED, fixture.scheduler().register(2).status(), "Second profile was not registered.");
		PhantomAssertions.assertEquals(RegistrationStatus.CAPACITY_REACHED, fixture.scheduler().register(3).status(), "Registration exceeded hard capacity.");
		final PhantomActivitySnapshot sleeping = fixture.scheduler().find(1).orElseThrow();
		PhantomAssertions.assertEquals(PhantomActivityState.SLEEPING, sleeping.effectiveState(), "Registration did not begin SLEEPING.");
		PhantomAssertions.assertFalse(sleeping.due(), "Dormant registration created due work.");
		PhantomAssertions.assertEquals(UnregisterStatus.UNREGISTERED, fixture.scheduler().unregister(2).status(), "Dormant profile did not unregister immediately.");
		stop(fixture.scheduler());
	}

	private void testSignals()
	{
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> new PhantomRelevanceSignal("Bad", 0, PhantomActivityState.WARM, 1), "Invalid source key was accepted.");
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> new PhantomRelevanceSignal("valid", -1, PhantomActivityState.WARM, 1), "Negative sequence was accepted.");
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> new PhantomRelevanceSignal("valid", 0, PhantomActivityState.WARM, 0), "Zero TTL was accepted.");

		final Fixture fixture = fixture(2, 2);
		fixture.scheduler().register(1);
		for (int i = 0; i < 16; i++)
		{
			final SignalStatus expected = i == 0 ? SignalStatus.ACCEPTED : SignalStatus.COALESCED;
			PhantomAssertions.assertEquals(expected, fixture.scheduler().submitSignal(1, signal("source." + i, 1, PhantomActivityState.BACKGROUND, 100)).status(), "Bounded source was not accepted/coalesced.");
		}
		PhantomAssertions.assertEquals(SignalStatus.REJECTED, fixture.scheduler().submitSignal(1, signal("source.16", 1, PhantomActivityState.WARM, 100)).status(), "Seventeenth source was accepted.");
		PhantomAssertions.assertEquals(SignalStatus.STALE, fixture.scheduler().submitSignal(1, signal("source.0", 1, PhantomActivityState.ACTIVE, 100)).status(), "Equal sequence mutated a source.");
		PhantomAssertions.assertEquals(SignalStatus.STALE, fixture.scheduler().withdrawSignal(1, "source.0", 1).status(), "Equal withdrawal sequence was accepted.");
		PhantomAssertions.assertEquals(SignalStatus.COALESCED, fixture.scheduler().withdrawSignal(1, "source.0", 2).status(), "New withdrawal did not coalesce.");
		PhantomAssertions.assertEquals(1, fixture.scheduler().snapshot().ready(), "Coalescing created duplicate ready entries.");
		stop(fixture.scheduler());
	}

	@SuppressWarnings("unchecked")
	private void testBackpressureAtomicity() throws Exception
	{
		final Fixture fixture = fixture(2, 1);
		fixture.scheduler().register(1);
		final Field field = PhantomScheduler.class.getDeclaredField("_readyQueue");
		field.setAccessible(true);
		final ArrayBlockingQueue<Long> ready = (ArrayBlockingQueue<Long>) field.get(fixture.scheduler());
		ready.add(100L);
		ready.add(101L);
		final SignalStatus result = fixture.scheduler().submitSignal(1, signal("capacity", 1, PhantomActivityState.ACTIVE, 100)).status();
		PhantomAssertions.assertEquals(SignalStatus.BACKPRESSURE, result, "Saturated queue did not return explicit backpressure.");
		final PhantomActivitySnapshot unchanged = fixture.scheduler().find(1).orElseThrow();
		PhantomAssertions.assertEquals(0, unchanged.activeSignalSources(), "Backpressured signal mutated the source map.");
		PhantomAssertions.assertEquals(PhantomActivityState.SLEEPING, unchanged.requestedState(), "Backpressured signal mutated requested state.");
		stop(fixture.scheduler());
	}

	private void testAggregationAndHysteresis()
	{
		final Fixture fixture = fixture(4, 4);
		fixture.scheduler().register(1);
		fixture.scheduler().submitSignal(1, signal("low", 1, PhantomActivityState.BACKGROUND, 100));
		fixture.scheduler().submitSignal(1, signal("high", 1, PhantomActivityState.WARM, 10));
		fixture.scheduler().pulse();
		PhantomAssertions.assertEquals(PhantomActivityState.WARM, fixture.scheduler().find(1).orElseThrow().effectiveState(), "Highest-detail signal did not win.");
		fixture.clock().advanceMillis(10);
		fixture.scheduler().pulse();
		final PhantomActivitySnapshot grace = fixture.scheduler().find(1).orElseThrow();
		PhantomAssertions.assertEquals(PhantomActivityState.WARM, grace.effectiveState(), "Expired higher signal demoted without grace.");
		PhantomAssertions.assertEquals(PhantomActivityTransitionStatus.DEMOTION_GRACE, grace.transitionStatus(), "Demotion grace status was not exposed.");
		fixture.clock().advanceMillis(5);
		fixture.scheduler().pulse();
		PhantomAssertions.assertEquals(PhantomActivityState.BACKGROUND, fixture.scheduler().find(1).orElseThrow().effectiveState(), "Demotion did not commit after grace.");
		stop(fixture.scheduler());
	}

	private void testMaterializationBoundary()
	{
		PhantomAssertions.assertEquals(Outcome.TRANSIENT_BLOCK, PhantomActivityMaterializationPort.noop().materialize(1).outcome(), "No-op materialization port falsely reported canonical materialization.");
		final Fixture fixture = fixture(2, 2);
		fixture.scheduler().register(1);
		fixture.scheduler().submitSignal(1, signal("interest", 1, PhantomActivityState.NEARBY_PERCEPTIBLE, 100));
		fixture.scheduler().pulse();
		PhantomAssertions.assertEquals(PhantomActivityState.NEARBY_PERCEPTIBLE, fixture.scheduler().find(1).orElseThrow().effectiveState(), "NEARBY became effective without transition success.");
		PhantomAssertions.assertEquals(1, fixture.port().materializeCalls, "NEARBY did not materialize canonically.");
		fixture.scheduler().submitSignal(1, signal("interest", 2, PhantomActivityState.ACTIVE, 100));
		fixture.scheduler().pulse();
		PhantomAssertions.assertEquals(PhantomActivityState.ACTIVE, fixture.scheduler().find(1).orElseThrow().effectiveState(), "NEARBY to ACTIVE did not commit.");
		PhantomAssertions.assertEquals(1, fixture.port().materializeCalls, "ACTIVE/NEARBY transition duplicated materialization.");
		fixture.scheduler().submitSignal(1, signal("interest", 3, PhantomActivityState.WARM, 100));
		fixture.scheduler().pulse();
		fixture.clock().advanceMillis(5);
		fixture.scheduler().pulse();
		PhantomAssertions.assertEquals(PhantomActivityState.WARM, fixture.scheduler().find(1).orElseThrow().effectiveState(), "Materialized demotion did not commit.");
		PhantomAssertions.assertEquals(1, fixture.port().dematerializeCalls, "Materialized demotion did not use the lifecycle port.");
		stop(fixture.scheduler());
	}

	private void testTransientRetry()
	{
		final Fixture fixture = fixture(2, 2);
		fixture.port().materializeOutcomes.add(Outcome.TRANSIENT_BLOCK);
		fixture.scheduler().register(1);
		fixture.scheduler().submitSignal(1, signal("interest", 1, PhantomActivityState.ACTIVE, 100));
		fixture.scheduler().pulse();
		final PhantomActivitySnapshot blocked = fixture.scheduler().find(1).orElseThrow();
		PhantomAssertions.assertEquals(PhantomActivityState.SLEEPING, blocked.effectiveState(), "Blocked promotion falsified ACTIVE state.");
		PhantomAssertions.assertEquals(PhantomActivityTransitionStatus.TRANSIENTLY_BLOCKED, blocked.transitionStatus(), "Clean rejection was not transiently blocked.");
		PhantomAssertions.assertEquals(1, fixture.port().materializeCalls, "First materialization attempt count mismatch.");
		fixture.clock().advanceMillis(1);
		fixture.scheduler().pulse();
		PhantomAssertions.assertEquals(1, fixture.port().materializeCalls, "Retry occurred before bounded backoff.");
		fixture.clock().advanceMillis(1);
		fixture.scheduler().pulse();
		PhantomAssertions.assertEquals(2, fixture.port().materializeCalls, "Due retry did not occur.");
		PhantomAssertions.assertEquals(PhantomActivityState.ACTIVE, fixture.scheduler().find(1).orElseThrow().effectiveState(), "Successful retry did not commit ACTIVE.");
		stop(fixture.scheduler());
	}

	private void testRetainedFailure()
	{
		final Fixture promotion = fixture(2, 2);
		promotion.port().materializeOutcomes.add(Outcome.RETAINED_FAILURE);
		promotion.scheduler().register(1);
		promotion.scheduler().submitSignal(1, signal("interest", 1, PhantomActivityState.ACTIVE, 100));
		promotion.scheduler().pulse();
		PhantomAssertions.assertEquals(PhantomActivityTransitionStatus.RETAINED_FAILURE_REQUIRES_EXPLICIT_RETRY, promotion.scheduler().find(1).orElseThrow().transitionStatus(), "Retained promotion failure did not require explicit retry.");
		promotion.clock().advanceMillis(20);
		promotion.scheduler().pulse();
		PhantomAssertions.assertEquals(1, promotion.port().materializeCalls, "Retained promotion retried automatically.");
		PhantomAssertions.assertEquals(RetryStatus.SCHEDULED, promotion.scheduler().retryTransition(1).status(), "Explicit retry was not scheduled.");
		promotion.scheduler().pulse();
		PhantomAssertions.assertEquals(1, promotion.port().retryCalls, "Explicit retry did not clean retained lifecycle state.");
		promotion.scheduler().pulse();
		PhantomAssertions.assertEquals(PhantomActivityState.ACTIVE, promotion.scheduler().find(1).orElseThrow().effectiveState(), "Promotion did not resume after explicit cleanup.");
		stop(promotion.scheduler());

		final Fixture cleanup = fixture(2, 2);
		cleanup.scheduler().register(1);
		cleanup.scheduler().submitSignal(1, signal("interest", 1, PhantomActivityState.ACTIVE, 100));
		cleanup.scheduler().pulse();
		cleanup.port().dematerializeOutcomes.add(Outcome.RETAINED_FAILURE);
		cleanup.scheduler().submitSignal(1, signal("interest", 2, PhantomActivityState.WARM, 100));
		cleanup.scheduler().pulse();
		cleanup.clock().advanceMillis(5);
		cleanup.scheduler().pulse();
		PhantomAssertions.assertEquals(PhantomActivityState.ACTIVE, cleanup.scheduler().find(1).orElseThrow().effectiveState(), "Retained cleanup failure falsified demotion.");
		cleanup.scheduler().retryTransition(1);
		cleanup.scheduler().pulse();
		PhantomAssertions.assertEquals(PhantomActivityState.WARM, cleanup.scheduler().find(1).orElseThrow().effectiveState(), "Explicit cleanup retry did not commit demotion.");
		stop(cleanup.scheduler());
	}

	private void testWorkCadence()
	{
		final Fixture fixture = fixture(3, 3);
		fixture.scheduler().register(1);
		fixture.scheduler().submitSignal(1, signal("work", 1, PhantomActivityState.WARM, 100));
		fixture.scheduler().pulse();
		PhantomAssertions.assertEquals(1, fixture.work().size(), "Initial WARM work was not delivered.");
		fixture.scheduler().pulse();
		PhantomAssertions.assertEquals(1, fixture.work().size(), "Work ran before cadence.");
		fixture.clock().advanceMillis(3);
		fixture.scheduler().pulse();
		PhantomAssertions.assertEquals(2, fixture.work().size(), "Due WARM cadence did not run.");

		fixture.failNextWork = true;
		fixture.clock().advanceMillis(3);
		fixture.scheduler().pulse();
		PhantomAssertions.assertEquals(3L, fixture.scheduler().find(1).orElseThrow().tickSequence(), "Sink failure did not consume one isolated tick.");
		fixture.clock().advanceMillis(3);
		fixture.scheduler().pulse();
		PhantomAssertions.assertEquals(3, fixture.work().size(), "Normal cadence did not continue after sink failure.");
		fixture.scheduler().withdrawSignal(1, "work", 2);
		fixture.scheduler().pulse();
		fixture.clock().advanceMillis(5);
		fixture.scheduler().pulse();
		final int sleepingWork = fixture.work().size();
		fixture.clock().advanceMillis(50);
		fixture.scheduler().pulse();
		PhantomAssertions.assertEquals(sleepingWork, fixture.work().size(), "SLEEPING profile received periodic work.");
		stop(fixture.scheduler());
	}

	private void testFairness()
	{
		final Fixture fixture = fixture(5, 2);
		for (long profileId = 1; profileId <= 5; profileId++)
		{
			fixture.scheduler().register(profileId);
			fixture.scheduler().submitSignal(profileId, signal("cohort", 1, PhantomActivityState.WARM, 100));
		}
		fixture.scheduler().pulse();
		fixture.scheduler().pulse();
		fixture.scheduler().pulse();
		for (long profileId = 1; profileId <= 5; profileId++)
		{
			PhantomAssertions.assertEquals(1L, fixture.scheduler().find(profileId).orElseThrow().tickSequence(), "Due cohort did not receive one fair first opportunity.");
		}
		fixture.scheduler().submitSignal(1, signal("cohort", 2, PhantomActivityState.WARM, 100));
		fixture.scheduler().submitSignal(1, signal("cohort", 3, PhantomActivityState.WARM, 100));
		fixture.scheduler().pulse();
		PhantomAssertions.assertEquals(1L, fixture.scheduler().find(1).orElseThrow().tickSequence(), "Immediate coalesced signal duplicated processing/work inside cadence.");
		stop(fixture.scheduler());
	}

	private void testOverload()
	{
		final Fixture fixture = fixture(4, 4);
		for (long profileId = 1; profileId <= 4; profileId++)
		{
			fixture.scheduler().register(profileId);
			final PhantomActivityState state = switch ((int) profileId)
			{
				case 1 -> PhantomActivityState.ACTIVE;
				case 2 -> PhantomActivityState.NEARBY_PERCEPTIBLE;
				case 3 -> PhantomActivityState.WARM;
				default -> PhantomActivityState.BACKGROUND;
			};
			fixture.scheduler().submitSignal(profileId, signal("load", 1, state, 100));
		}
		fixture.scheduler().pulse();
		PhantomAssertions.assertEquals(PhantomActivityOverloadLevel.CRITICAL, fixture.scheduler().snapshot().peakOverloadLevel(), "Full ready queue did not reach CRITICAL.");
		final PhantomActivitySnapshot active = fixture.scheduler().find(1).orElseThrow();
		final PhantomActivitySnapshot nearby = fixture.scheduler().find(2).orElseThrow();
		final PhantomActivitySnapshot warm = fixture.scheduler().find(3).orElseThrow();
		final PhantomActivitySnapshot background = fixture.scheduler().find(4).orElseThrow();
		PhantomAssertions.assertEquals(1_000_000L, active.nextDueNanos(), "CRITICAL overload degraded ACTIVE cadence.");
		PhantomAssertions.assertEquals(2_000_000L, nearby.nextDueNanos(), "CRITICAL overload degraded NEARBY cadence.");
		PhantomAssertions.assertEquals(24_000_000L, warm.nextDueNanos(), "CRITICAL overload did not multiply WARM cadence by eight.");
		PhantomAssertions.assertEquals(32_000_000L, background.nextDueNanos(), "CRITICAL overload did not multiply BACKGROUND cadence by eight.");
		PhantomAssertions.assertEquals(PhantomActivityState.ACTIVE, active.effectiveState(), "Overload demoted ACTIVE state.");
		PhantomAssertions.assertEquals(PhantomActivityState.NEARBY_PERCEPTIBLE, nearby.effectiveState(), "Overload demoted NEARBY state.");
		PhantomAssertions.assertEquals(PhantomActivityState.WARM, warm.effectiveState(), "Overload demoted WARM state.");
		PhantomAssertions.assertEquals(PhantomActivityState.BACKGROUND, background.effectiveState(), "Overload demoted BACKGROUND state.");
		stop(fixture.scheduler());
	}

	private void testUnregisterAndStop()
	{
		final Fixture fixture = fixture(2, 2);
		fixture.scheduler().register(1);
		fixture.scheduler().submitSignal(1, signal("interest", 1, PhantomActivityState.ACTIVE, 100));
		fixture.scheduler().pulse();
		PhantomAssertions.assertEquals(UnregisterStatus.PENDING, fixture.scheduler().unregister(1).status(), "Materialized unregister did not enter pending cleanup.");
		fixture.scheduler().pulse();
		fixture.clock().advanceMillis(5);
		fixture.scheduler().pulse();
		PhantomAssertions.assertTrue(fixture.scheduler().find(1).isEmpty(), "Materialized unregister did not remove the profile after cleanup.");
		fixture.scheduler().register(2);
		PhantomAssertions.assertEquals(BeginStopResult.STARTED, fixture.scheduler().beginStop(), "Scheduler did not begin stop.");
		PhantomAssertions.assertEquals(RegistrationStatus.NOT_RUNNING, fixture.scheduler().register(3).status(), "STOPPING scheduler accepted registration.");
		PhantomAssertions.assertEquals(SignalStatus.NOT_RUNNING, fixture.scheduler().submitSignal(2, signal("stop", 1, PhantomActivityState.WARM, 10)).status(), "STOPPING scheduler accepted signal.");
		PhantomAssertions.assertEquals(1, fixture.scheduler().snapshot().registered(), "beginStop cleared retained slot snapshots.");
		PhantomAssertions.assertTrue(fixture.scheduler().finishStop(), "finishStop did not clear stopped scheduler.");
		PhantomAssertions.assertEquals(0, fixture.scheduler().snapshot().registered(), "finishStop retained slots.");
		PhantomAssertions.assertEquals(0, fixture.scheduler().snapshot().ready(), "finishStop retained ready work.");
		PhantomAssertions.assertEquals(0, fixture.scheduler().snapshot().due(), "finishStop retained due work.");
	}

	private void testMetrics(PhantomTestContext context)
	{
		final Fixture fixture = fixture(2, 2);
		fixture.scheduler().register(1);
		fixture.scheduler().submitSignal(1, signal("metrics", 1, PhantomActivityState.WARM, 100));
		fixture.scheduler().pulse();
		final PhantomMetrics.ActivitySnapshot metrics = fixture.metrics().snapshot().activity();
		PhantomAssertions.assertEquals(1L, metrics.registeredCurrent(), "Registered current metric mismatch.");
		PhantomAssertions.assertEquals(1L, metrics.registrationAccepted(), "Registration accepted metric mismatch.");
		PhantomAssertions.assertEquals(1L, metrics.signalAccepted(), "Signal accepted metric mismatch.");
		PhantomAssertions.assertEquals(1L, metrics.workDelivered(), "Work delivered metric mismatch.");
		PhantomAssertions.assertEquals(1L, metrics.promotions(), "Promotion metric mismatch.");
		PhantomAssertions.assertEquals(List.of(0L, 0L, 1L, 0L, 0L), metrics.stateCounts(), "Fixed state counts mismatch.");
		context.record("activityScheduler.cases", 17);
		context.record("activityScheduler.recurringFutureManual", fixture.scheduler().snapshot().scheduledTaskCount());
		stop(fixture.scheduler());
	}

	private void testInFlightUnregisterSuccess() throws Exception
	{
		final Fixture fixture = fixture(2, 2);
		fixture.scheduler().register(1);
		fixture.scheduler().submitSignal(1, signal("interest", 1, PhantomActivityState.ACTIVE, 100));
		fixture.port().blockNextMaterialize();
		final Thread pulse = pulseThread(fixture.scheduler(), "t007a-unregister-success");
		pulse.start();
		fixture.port().awaitMaterializeEntered();

		final PhantomActivitySnapshot inFlight = fixture.scheduler().find(1).orElseThrow();
		PhantomAssertions.assertTrue(inFlight.processing(), "In-flight promotion did not retain processing ownership.");
		PhantomAssertions.assertTrue(inFlight.boundaryInFlight(), "In-flight promotion did not expose boundary ownership.");
		PhantomAssertions.assertEquals(UnregisterStatus.PENDING, fixture.scheduler().unregister(1).status(), "In-flight non-materialized slot was removed by unregister.");
		final PhantomActivitySnapshot pending = fixture.scheduler().find(1).orElseThrow();
		PhantomAssertions.assertEquals(PhantomActivityTransitionStatus.UNREGISTER_PENDING, pending.transitionStatus(), "In-flight unregister did not retain pending status.");
		PhantomAssertions.assertEquals(0, pending.activeSignalSources(), "In-flight unregister retained live signals.");

		fixture.port().releaseMaterialize();
		join(pulse);
		PhantomAssertions.assertTrue(fixture.scheduler().find(1).isPresent(), "Late materialization success orphaned lifecycle ownership by removing the slot.");
		PhantomAssertions.assertEquals(1, fixture.port().materializeCalls, "Blocked promotion call count mismatch.");
		fixture.scheduler().pulse();
		fixture.clock().advanceMillis(5);
		fixture.scheduler().pulse();
		PhantomAssertions.assertEquals(1, fixture.port().dematerializeCalls, "Late materialization success was not followed by cleanup.");
		PhantomAssertions.assertFalse(fixture.port().hasLifecycleOwnership(1), "Late materialization cleanup retained lifecycle ownership.");
		PhantomAssertions.assertTrue(fixture.scheduler().find(1).isEmpty(), "Pending unregister did not remove the slot after terminal cleanup.");
		stop(fixture.scheduler());
	}

	private void testInFlightUnregisterRetained() throws Exception
	{
		final Fixture fixture = fixture(2, 2);
		fixture.port().materializeOutcomes.add(Outcome.RETAINED_FAILURE);
		fixture.scheduler().register(1);
		fixture.scheduler().submitSignal(1, signal("interest", 1, PhantomActivityState.ACTIVE, 100));
		fixture.port().blockNextMaterialize();
		final Thread pulse = pulseThread(fixture.scheduler(), "t007a-unregister-retained");
		pulse.start();
		fixture.port().awaitMaterializeEntered();
		PhantomAssertions.assertEquals(UnregisterStatus.PENDING, fixture.scheduler().unregister(1).status(), "Retained in-flight promotion was removed by unregister.");
		fixture.port().releaseMaterialize();
		join(pulse);

		final PhantomActivitySnapshot retained = fixture.scheduler().find(1).orElseThrow();
		PhantomAssertions.assertEquals(PhantomActivityTransitionStatus.RETAINED_FAILURE_REQUIRES_EXPLICIT_RETRY, retained.transitionStatus(), "Late retained promotion did not require explicit cleanup.");
		PhantomAssertions.assertTrue(fixture.port().hasLifecycleOwnership(1), "Retained promotion fixture lost lifecycle ownership.");
		fixture.scheduler().pulse();
		PhantomAssertions.assertEquals(0, fixture.port().retryCalls, "Retained in-flight promotion retried automatically.");
		PhantomAssertions.assertEquals(RetryStatus.SCHEDULED, fixture.scheduler().retryTransition(1).status(), "Retained in-flight cleanup retry was not scheduled.");
		fixture.scheduler().pulse();
		PhantomAssertions.assertEquals(1, fixture.port().retryCalls, "Explicit retained in-flight cleanup did not run.");
		PhantomAssertions.assertFalse(fixture.port().hasLifecycleOwnership(1), "Explicit retained in-flight cleanup kept lifecycle ownership.");
		PhantomAssertions.assertTrue(fixture.scheduler().find(1).isEmpty(), "Retained pending unregister did not remove the terminal slot.");
		stop(fixture.scheduler());
	}

	private void testRetainedDematerializationTruth()
	{
		final Fixture fixture = fixture(2, 2);
		fixture.scheduler().register(1);
		fixture.scheduler().submitSignal(1, signal("interest", 1, PhantomActivityState.ACTIVE, 100));
		fixture.scheduler().pulse();
		fixture.port().dematerializeOutcomes.add(Outcome.RETAINED_FAILURE);
		fixture.scheduler().submitSignal(1, signal("interest", 2, PhantomActivityState.WARM, 100));
		fixture.scheduler().pulse();
		fixture.clock().advanceMillis(5);
		fixture.scheduler().pulse();
		PhantomAssertions.assertEquals(PhantomActivityTransitionStatus.RETAINED_FAILURE_REQUIRES_EXPLICIT_RETRY, fixture.scheduler().find(1).orElseThrow().transitionStatus(), "Retained dematerialization did not require explicit cleanup.");

		fixture.scheduler().submitSignal(1, signal("interest", 3, PhantomActivityState.ACTIVE, 100));
		fixture.scheduler().pulse();
		final PhantomActivitySnapshot equalButRetained = fixture.scheduler().find(1).orElseThrow();
		PhantomAssertions.assertEquals(PhantomActivityState.ACTIVE, equalButRetained.requestedState(), "New ACTIVE request was not observed.");
		PhantomAssertions.assertEquals(PhantomActivityState.ACTIVE, equalButRetained.effectiveState(), "Retained failure unexpectedly changed effective state before cleanup.");
		PhantomAssertions.assertEquals(PhantomActivityTransitionStatus.RETAINED_FAILURE_REQUIRES_EXPLICIT_RETRY, equalButRetained.transitionStatus(), "requested==effective erased retained cleanup ownership.");
		PhantomAssertions.assertEquals(0, fixture.port().retryCalls, "Retained dematerialization retried automatically.");

		PhantomAssertions.assertEquals(RetryStatus.SCHEDULED, fixture.scheduler().retryTransition(1).status(), "Explicit retained dematerialization cleanup was not scheduled.");
		fixture.scheduler().pulse();
		final PhantomActivitySnapshot cleaned = fixture.scheduler().find(1).orElseThrow();
		PhantomAssertions.assertEquals(PhantomActivityState.SLEEPING, cleaned.effectiveState(), "Cleanup retry falsely published ACTIVE without a canonical actor.");
		PhantomAssertions.assertEquals(1, fixture.port().materializeCalls, "Cleanup retry performed an implicit materialization.");
		PhantomAssertions.assertFalse(fixture.port().hasLifecycleOwnership(1), "Successful cleanup retry retained lifecycle ownership.");

		fixture.scheduler().pulse();
		PhantomAssertions.assertEquals(2, fixture.port().materializeCalls, "Fresh ACTIVE opportunity did not call materialize.");
		PhantomAssertions.assertEquals(PhantomActivityState.ACTIVE, fixture.scheduler().find(1).orElseThrow().effectiveState(), "Fresh materialization did not restore ACTIVE.");
		stop(fixture.scheduler());
	}

	private void testRetainedMaterializationSignalLoss()
	{
		final Fixture withdrawal = fixture(2, 2);
		withdrawal.port().materializeOutcomes.add(Outcome.RETAINED_FAILURE);
		withdrawal.scheduler().register(1);
		withdrawal.scheduler().submitSignal(1, signal("interest", 1, PhantomActivityState.ACTIVE, 100));
		withdrawal.scheduler().pulse();
		withdrawal.scheduler().withdrawSignal(1, "interest", 2);
		withdrawal.scheduler().pulse();
		final PhantomActivitySnapshot withdrawn = withdrawal.scheduler().find(1).orElseThrow();
		PhantomAssertions.assertEquals(PhantomActivityState.SLEEPING, withdrawn.requestedState(), "Signal withdrawal did not request SLEEPING.");
		PhantomAssertions.assertEquals(PhantomActivityTransitionStatus.RETAINED_FAILURE_REQUIRES_EXPLICIT_RETRY, withdrawn.transitionStatus(), "Signal withdrawal erased retained materialization ownership.");
		withdrawal.scheduler().retryTransition(1);
		withdrawal.scheduler().pulse();
		PhantomAssertions.assertFalse(withdrawal.port().hasLifecycleOwnership(1), "Withdrawal cleanup retry retained lifecycle ownership.");
		PhantomAssertions.assertEquals(PhantomActivityState.SLEEPING, withdrawal.scheduler().find(1).orElseThrow().effectiveState(), "Withdrawal cleanup did not leave stable SLEEPING truth.");
		stop(withdrawal.scheduler());

		final Fixture expiry = fixture(2, 2);
		expiry.port().materializeOutcomes.add(Outcome.RETAINED_FAILURE);
		expiry.scheduler().register(1);
		expiry.scheduler().submitSignal(1, signal("interest", 1, PhantomActivityState.ACTIVE, 10));
		expiry.scheduler().pulse();
		expiry.clock().advanceMillis(10);
		expiry.scheduler().pulse();
		final PhantomActivitySnapshot expired = expiry.scheduler().find(1).orElseThrow();
		PhantomAssertions.assertEquals(PhantomActivityState.SLEEPING, expired.requestedState(), "TTL expiry did not request SLEEPING.");
		PhantomAssertions.assertEquals(PhantomActivityTransitionStatus.RETAINED_FAILURE_REQUIRES_EXPLICIT_RETRY, expired.transitionStatus(), "TTL expiry erased retained materialization ownership.");
		expiry.scheduler().retryTransition(1);
		expiry.scheduler().pulse();
		PhantomAssertions.assertFalse(expiry.port().hasLifecycleOwnership(1), "Expiry cleanup retry retained lifecycle ownership.");
		PhantomAssertions.assertEquals(PhantomActivityState.SLEEPING, expiry.scheduler().find(1).orElseThrow().effectiveState(), "Expiry cleanup did not leave stable SLEEPING truth.");
		stop(expiry.scheduler());
	}

	private void testStoppingQuiescence() throws Exception
	{
		final Fixture boundary = fixture(2, 2);
		boundary.scheduler().register(1);
		boundary.scheduler().submitSignal(1, signal("interest", 1, PhantomActivityState.ACTIVE, 100));
		boundary.port().blockNextMaterialize();
		final Thread boundaryPulse = pulseThread(boundary.scheduler(), "t007a-stop-boundary");
		boundaryPulse.start();
		boundary.port().awaitMaterializeEntered();
		PhantomAssertions.assertEquals(BeginStopResult.STARTED, boundary.scheduler().beginStop(), "Boundary race did not enter STOPPING.");
		PhantomAssertions.assertTrue(boundary.scheduler().snapshot().pulseInFlight(), "Boundary race lost the in-flight pulse marker.");
		PhantomAssertions.assertFalse(boundary.scheduler().finishStop(), "finishStop cleared a blocked boundary pulse.");
		PhantomAssertions.assertEquals(1, boundary.scheduler().snapshot().registered(), "Failed finishStop cleared boundary snapshots.");
		boundary.port().releaseMaterialize();
		join(boundaryPulse);
		PhantomAssertions.assertEquals(0, boundary.work().size(), "STOPPING started work after the boundary returned.");
		PhantomAssertions.assertFalse(boundary.scheduler().snapshot().pulseInFlight(), "Completed boundary pulse retained its in-flight marker.");
		PhantomAssertions.assertTrue(boundary.scheduler().finishStop(), "Quiescent boundary scheduler did not finish stop.");
		assertStoppedWithoutResidue(boundary.scheduler());

		final Fixture work = fixture(2, 2);
		work.scheduler().register(1);
		work.scheduler().submitSignal(1, signal("work", 1, PhantomActivityState.WARM, 100));
		work.blockNextWork();
		final Thread workPulse = pulseThread(work.scheduler(), "t007a-stop-work");
		workPulse.start();
		work.awaitWorkEntered();
		PhantomAssertions.assertEquals(BeginStopResult.STARTED, work.scheduler().beginStop(), "Work race did not enter STOPPING.");
		work.scheduler().pulse();
		PhantomAssertions.assertEquals(1L, work.scheduler().snapshot().pulseSequence(), "Overlapping/STOPPING pulse started new work.");
		PhantomAssertions.assertFalse(work.scheduler().finishStop(), "finishStop cleared a blocked work pulse.");
		PhantomAssertions.assertEquals(1, work.scheduler().snapshot().registered(), "Failed finishStop cleared work snapshots.");
		work.releaseWork();
		join(workPulse);
		PhantomAssertions.assertEquals(1, work.work().size(), "Blocked work did not complete exactly once.");
		PhantomAssertions.assertTrue(work.scheduler().finishStop(), "Quiescent work scheduler did not finish stop.");
		assertStoppedWithoutResidue(work.scheduler());
	}

	private void testCleanupRetryCurrentSleeping() throws Exception
	{
		final Fixture fixture = retainedDematerializationFixture();
		fixture.port().blockNextRetry();
		PhantomAssertions.assertEquals(RetryStatus.SCHEDULED, fixture.scheduler().retryTransition(1).status(), "Cleanup retry was not scheduled.");
		final Thread retryPulse = pulseThread(fixture.scheduler(), "t008-retry-sleeping");
		retryPulse.start();
		fixture.port().awaitRetryEntered();
		fixture.scheduler().withdrawSignal(1, "interest", 3);
		fixture.port().releaseRetry();
		join(retryPulse);
		final PhantomActivitySnapshot snapshot = fixture.scheduler().find(1).orElseThrow();
		PhantomAssertions.assertEquals(PhantomActivityState.SLEEPING, snapshot.requestedState(), "Blocked cleanup retry retained a stale WARM request after withdrawal.");
		PhantomAssertions.assertEquals(PhantomActivityState.SLEEPING, snapshot.effectiveState(), "Blocked cleanup retry published stale WARM instead of current SLEEPING.");
		PhantomAssertions.assertEquals(PhantomActivityTransitionStatus.STABLE, snapshot.transitionStatus(), "Current SLEEPING request did not stabilize after cleanup.");
		stop(fixture.scheduler());
	}

	private void testCleanupRetryCurrentActive() throws Exception
	{
		final Fixture fixture = retainedDematerializationFixture();
		fixture.port().blockNextRetry();
		PhantomAssertions.assertEquals(RetryStatus.SCHEDULED, fixture.scheduler().retryTransition(1).status(), "Cleanup retry was not scheduled.");
		final Thread retryPulse = pulseThread(fixture.scheduler(), "t008-retry-active");
		retryPulse.start();
		fixture.port().awaitRetryEntered();
		fixture.scheduler().submitSignal(1, signal("interest", 3, PhantomActivityState.ACTIVE, 100));
		fixture.port().releaseRetry();
		join(retryPulse);
		final PhantomActivitySnapshot cleaned = fixture.scheduler().find(1).orElseThrow();
		PhantomAssertions.assertEquals(PhantomActivityState.ACTIVE, cleaned.requestedState(), "Blocked cleanup retry retained stale WARM instead of current ACTIVE.");
		PhantomAssertions.assertEquals(PhantomActivityState.SLEEPING, cleaned.effectiveState(), "Cleanup retry published ACTIVE without fresh materialization.");
		PhantomAssertions.assertEquals(PhantomActivityTransitionStatus.PROMOTION_PENDING, cleaned.transitionStatus(), "Current ACTIVE request did not retain a fresh promotion opportunity.");
		fixture.scheduler().pulse();
		PhantomAssertions.assertEquals(2, fixture.port().materializeCalls, "Current ACTIVE request did not perform a fresh materialization.");
		PhantomAssertions.assertEquals(PhantomActivityState.ACTIVE, fixture.scheduler().find(1).orElseThrow().effectiveState(), "Fresh materialization did not restore ACTIVE.");
		stop(fixture.scheduler());
	}

	private static Fixture retainedDematerializationFixture()
	{
		final Fixture fixture = fixture(2, 2);
		fixture.scheduler().register(1);
		fixture.scheduler().submitSignal(1, signal("interest", 1, PhantomActivityState.ACTIVE, 100));
		fixture.scheduler().pulse();
		fixture.port().dematerializeOutcomes.add(Outcome.RETAINED_FAILURE);
		fixture.scheduler().submitSignal(1, signal("interest", 2, PhantomActivityState.WARM, 100));
		fixture.scheduler().pulse();
		fixture.clock().advanceMillis(5);
		fixture.scheduler().pulse();
		PhantomAssertions.assertEquals(PhantomActivityTransitionStatus.RETAINED_FAILURE_REQUIRES_EXPLICIT_RETRY, fixture.scheduler().find(1).orElseThrow().transitionStatus(), "Fixture did not reach retained dematerialization.");
		return fixture;
	}

	private void testDecisionSinkIntegration()
	{
		final SchedulerGoalStore store = new SchedulerGoalStore();
		final PhantomCandidateRegistry candidates = new PhantomCandidateRegistry();
		candidates.register(new PhantomDecisionCandidate("candidate.scheduler", Set.of("goal.scheduler"), Set.of(PhantomActivityState.WARM), List.of(), List.of(new PhantomWeightedConsideration("score.scheduler", 1, _ -> new PhantomConsideration.Evaluation(1000, "score.scheduler"))), 0, context -> new PhantomPlan(1, context.goal().goalId(), "candidate.scheduler", List.of(new PhantomPlanStep(0, "action.scheduler", null, Map.of(), 1000, 1, "reason.scheduler")), 1000, context.logicalNowNanos())));
		candidates.seal();
		final AtomicInteger handlerCalls = new AtomicInteger();
		final PhantomStepHandlerRegistry handlers = new PhantomStepHandlerRegistry();
		handlers.register("action.scheduler", _ ->
		{
			handlerCalls.incrementAndGet();
			return PhantomStepResult.of(PhantomStepResult.Type.SUCCESS, "step.success");
		});
		handlers.seal();
		final PhantomMetrics metrics = new PhantomMetrics();
		final PhantomDecisionEngine engine = new PhantomDecisionEngine(store, candidates, handlers, metrics, 2);
		engine.start();
		PhantomAssertions.assertEquals(PhantomDecisionEngine.AttachResult.ATTACHED, engine.attach(1), "Decision integration profile did not attach.");
		final PhantomGoal goal = new PhantomGoal(1, "goal.scheduler", PhantomGoalStatus.ACTIVE, null, null, 0, 0, null, List.of(), null, "purpose.scheduler", 500, 0, 0, 0, Map.of(), "reason.scheduler", 0);
		PhantomAssertions.assertEquals(PhantomDecisionEngine.MutationResult.APPLIED, engine.insertGoal(1, goal), "Decision integration goal did not insert.");

		final ManualClock clock = new ManualClock();
		final PhantomScheduler scheduler = new PhantomScheduler(2, 10, 2, new PhantomSchedulerPolicy(16, 1000, 5, 2, 8, 1, 2, 3, 4, 50), clock, (pulse, period) -> null, false, metrics, new PhantomDiagnosticTrace(true, 16, 1, metrics), PhantomActivityMaterializationPort.noop(), engine);
		PhantomAssertions.assertTrue(scheduler.start(), "Decision integration scheduler did not start.");
		scheduler.register(1);
		scheduler.submitSignal(1, signal("decision", 1, PhantomActivityState.WARM, 100));
		scheduler.pulse();
		PhantomAssertions.assertEquals(1, handlerCalls.get(), "One scheduler work item did not drive exactly one decision handler.");
		PhantomAssertions.assertEquals("candidate.scheduler", engine.find(1).orElseThrow().selectedCandidateKey(), "Scheduler-driven selection was not deterministic.");
		stop(scheduler);
		engine.beginStop();
		PhantomAssertions.assertTrue(engine.finishStop(), "Decision integration engine did not stop.");
	}

	private static Fixture fixture(int maximumProfiles, int profilesPerPulse)
	{
		final ManualClock clock = new ManualClock();
		final FakeMaterializationPort port = new FakeMaterializationPort();
		final PhantomMetrics metrics = new PhantomMetrics();
		final PhantomDiagnosticTrace trace = new PhantomDiagnosticTrace(true, 16, 1, metrics);
		final List<PhantomActivityWorkItem> work = new ArrayList<>();
		final Fixture[] holder = new Fixture[1];
		final PhantomScheduler scheduler = new PhantomScheduler(
			maximumProfiles,
			10,
			profilesPerPulse,
			new PhantomSchedulerPolicy(16, 1000, 5, 2, 8, 1, 2, 3, 4, 50),
			clock,
			(pulse, period) -> null,
			false,
			metrics,
			trace,
			port,
			item ->
			{
				if ((holder[0] != null) && holder[0].failNextWork)
				{
					holder[0].failNextWork = false;
					throw new IllegalStateException("Injected sink failure.");
				}
				if (holder[0] != null)
				{
					holder[0].awaitBlockedWork();
				}
				work.add(item);
			});
		final Fixture fixture = new Fixture(scheduler, clock, port, metrics, work);
		holder[0] = fixture;
		PhantomAssertions.assertTrue(scheduler.start(), "Manual scheduler did not start.");
		return fixture;
	}

	private static PhantomRelevanceSignal signal(String source, long sequence, PhantomActivityState state, long ttlMillis)
	{
		return new PhantomRelevanceSignal(source, sequence, state, ttlMillis);
	}

	private static void stop(PhantomScheduler scheduler)
	{
		scheduler.beginStop();
		PhantomAssertions.assertTrue(scheduler.finishStop(), "Scheduler did not finish a quiescent stop.");
	}

	private static Thread pulseThread(PhantomScheduler scheduler, String name)
	{
		return new Thread(scheduler::pulse, name);
	}

	private static void join(Thread thread) throws InterruptedException
	{
		thread.join(TimeUnit.SECONDS.toMillis(2));
		PhantomAssertions.assertFalse(thread.isAlive(), "Scheduler pulse thread did not finish.");
	}

	private static void await(CountDownLatch latch, String message)
	{
		try
		{
			PhantomAssertions.assertTrue(latch.await(2, TimeUnit.SECONDS), message);
		}
		catch (InterruptedException e)
		{
			Thread.currentThread().interrupt();
			throw new AssertionError(message, e);
		}
	}

	private static void assertStoppedWithoutResidue(PhantomScheduler scheduler)
	{
		final PhantomScheduler.SchedulerSnapshot snapshot = scheduler.snapshot();
		PhantomAssertions.assertEquals(PhantomScheduler.SchedulerState.STOPPED, snapshot.state(), "Scheduler did not reach STOPPED.");
		PhantomAssertions.assertEquals(0, snapshot.registered(), "Stopped scheduler retained slots.");
		PhantomAssertions.assertEquals(0, snapshot.ready(), "Stopped scheduler retained ready entries.");
		PhantomAssertions.assertEquals(0, snapshot.due(), "Stopped scheduler retained due entries.");
		PhantomAssertions.assertFalse(snapshot.pulseInFlight(), "Stopped scheduler retained an in-flight pulse.");
	}

	private static final class ManualClock implements PhantomScheduler.MonotonicClock
	{
		private long _nowNanos;

		@Override
		public long nanoTime()
		{
			return _nowNanos;
		}

		private void advanceMillis(long millis)
		{
			_nowNanos += millis * 1_000_000L;
		}
	}

	private static final class FakeMaterializationPort implements PhantomActivityMaterializationPort
	{
		private final Queue<Outcome> materializeOutcomes = new ArrayDeque<>();
		private final Queue<Outcome> dematerializeOutcomes = new ArrayDeque<>();
		private final Queue<Outcome> retryOutcomes = new ArrayDeque<>();
		private final Map<Long, Boolean> materialized = new HashMap<>();
		private final Map<Long, Boolean> lifecycleOwned = new HashMap<>();
		private int materializeCalls;
		private int dematerializeCalls;
		private int retryCalls;
		private volatile CountDownLatch materializeEntered;
		private volatile CountDownLatch releaseMaterialize;
		private volatile CountDownLatch retryEntered;
		private volatile CountDownLatch releaseRetry;

		@Override
		public TransitionOutcome materialize(long profileId)
		{
			materializeCalls++;
			final CountDownLatch entered = materializeEntered;
			final CountDownLatch release = releaseMaterialize;
			if (entered != null)
			{
				entered.countDown();
				await(release, "Timed out waiting to release blocked materialization.");
				materializeEntered = null;
				releaseMaterialize = null;
			}
			final Outcome outcome = materializeOutcomes.isEmpty() ? Outcome.SUCCESS : materializeOutcomes.remove();
			if (outcome == Outcome.SUCCESS)
			{
				lifecycleOwned.put(profileId, true);
				materialized.put(profileId, true);
			}
			else if (outcome == Outcome.RETAINED_FAILURE)
			{
				lifecycleOwned.put(profileId, true);
			}
			return new TransitionOutcome(outcome);
		}

		@Override
		public TransitionOutcome dematerialize(long profileId)
		{
			dematerializeCalls++;
			final Outcome outcome = dematerializeOutcomes.isEmpty() ? Outcome.SUCCESS : dematerializeOutcomes.remove();
			if (outcome == Outcome.SUCCESS)
			{
				lifecycleOwned.remove(profileId);
				materialized.remove(profileId);
			}
			return new TransitionOutcome(outcome);
		}

		@Override
		public TransitionOutcome retryCleanup(long profileId)
		{
			retryCalls++;
			final CountDownLatch entered = retryEntered;
			final CountDownLatch release = releaseRetry;
			if (entered != null)
			{
				entered.countDown();
				await(release, "Timed out waiting to release blocked cleanup retry.");
				retryEntered = null;
				releaseRetry = null;
			}
			final Outcome outcome = retryOutcomes.isEmpty() ? Outcome.SUCCESS : retryOutcomes.remove();
			if (outcome == Outcome.SUCCESS)
			{
				lifecycleOwned.remove(profileId);
				materialized.remove(profileId);
			}
			return new TransitionOutcome(outcome);
		}

		@Override
		public boolean isMaterialized(long profileId)
		{
			return materialized.getOrDefault(profileId, false);
		}

		@Override
		public boolean hasLifecycleOwnership(long profileId)
		{
			return lifecycleOwned.getOrDefault(profileId, false);
		}

		private void blockNextMaterialize()
		{
			materializeEntered = new CountDownLatch(1);
			releaseMaterialize = new CountDownLatch(1);
		}

		private void awaitMaterializeEntered()
		{
			await(materializeEntered, "Blocked materialization did not enter the lifecycle port.");
		}

		private void releaseMaterialize()
		{
			releaseMaterialize.countDown();
		}

		private void blockNextRetry()
		{
			retryEntered = new CountDownLatch(1);
			releaseRetry = new CountDownLatch(1);
		}

		private void awaitRetryEntered()
		{
			await(retryEntered, "Blocked cleanup retry did not enter the lifecycle port.");
		}

		private void releaseRetry()
		{
			releaseRetry.countDown();
		}
	}

	private static final class SchedulerGoalStore implements PhantomGoalStore
	{
		private StoredGoal _goal;

		@Override
		public boolean profileExists(long profileId)
		{
			return profileId == 1;
		}

		@Override
		public Optional<StoredGoal> load(long profileId)
		{
			return Optional.ofNullable(_goal);
		}

		@Override
		public StoredGoal insert(long profileId, PhantomGoal goal)
		{
			_goal = new StoredGoal(goal, 0);
			return _goal;
		}

		@Override
		public StoredGoal replace(long profileId, long expectedRowVersion, PhantomGoal goal)
		{
			_goal = new StoredGoal(goal, expectedRowVersion + 1);
			return _goal;
		}

		@Override
		public void delete(long profileId, long expectedRowVersion)
		{
			_goal = null;
		}
	}

	private static final class Fixture
	{
		private final PhantomScheduler _scheduler;
		private final ManualClock _clock;
		private final FakeMaterializationPort _port;
		private final PhantomMetrics _metrics;
		private final List<PhantomActivityWorkItem> _work;
		private boolean failNextWork;
		private volatile CountDownLatch _workEntered;
		private volatile CountDownLatch _releaseWork;

		private Fixture(PhantomScheduler scheduler, ManualClock clock, FakeMaterializationPort port, PhantomMetrics metrics, List<PhantomActivityWorkItem> work)
		{
			_scheduler = scheduler;
			_clock = clock;
			_port = port;
			_metrics = metrics;
			_work = work;
		}

		private PhantomScheduler scheduler()
		{
			return _scheduler;
		}

		private ManualClock clock()
		{
			return _clock;
		}

		private FakeMaterializationPort port()
		{
			return _port;
		}

		private PhantomMetrics metrics()
		{
			return _metrics;
		}

		private List<PhantomActivityWorkItem> work()
		{
			return _work;
		}

		private void blockNextWork()
		{
			_workEntered = new CountDownLatch(1);
			_releaseWork = new CountDownLatch(1);
		}

		private void awaitWorkEntered()
		{
			await(_workEntered, "Blocked work did not enter the sink.");
		}

		private void releaseWork()
		{
			_releaseWork.countDown();
		}

		private void awaitBlockedWork()
		{
			final CountDownLatch entered = _workEntered;
			final CountDownLatch release = _releaseWork;
			if (entered != null)
			{
				entered.countDown();
				await(release, "Timed out waiting to release blocked work.");
				_workEntered = null;
				_releaseWork = null;
			}
		}
	}
}
