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
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.function.LongSupplier;

import org.l2jmobius.gameserver.config.custom.PhantomPlayersConfig;
import org.l2jmobius.gameserver.phantoms.PhantomScaleEnvelope.Verdict;
import org.l2jmobius.gameserver.phantoms.PhantomScaleEnvelope.Violation;
import org.l2jmobius.gameserver.phantoms.PhantomScheduler.RegistrationStatus;
import org.l2jmobius.gameserver.phantoms.PhantomScheduler.SchedulerSnapshot;
import org.l2jmobius.gameserver.phantoms.PhantomScheduler.SchedulerState;
import org.l2jmobius.gameserver.phantoms.PhantomScheduler.SignalStatus;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityMaterializationPort;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityOverloadLevel;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityWorkItem;
import org.l2jmobius.gameserver.phantoms.activity.PhantomRelevanceSignal;
import org.l2jmobius.gameserver.phantoms.activity.PhantomSchedulerPolicy;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationBackend;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationBackend.CapabilitySnapshot;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationCancellationToken;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationCapability;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPolicy;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationRequest;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationService;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationService.SubmissionStatus;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationResult.Status;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService.MaterializationSnapshot;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService.ServiceState;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializedPlayer.State;
import org.l2jmobius.tests.phantoms.PhantomAssertions;
import org.l2jmobius.tests.phantoms.PhantomTestContext;
import org.l2jmobius.tests.phantoms.PhantomTestRegistry;
import org.l2jmobius.tests.phantoms.PhantomTestSuite;

public final class PhantomScaleEnvelopeGoal029Checkpoint1Suite implements PhantomTestSuite
{
	public enum Mode
	{
		ALL,
		SCHEDULER,
		MATERIALIZATION,
		NAVIGATION
	}

	private static final int SCALE = 10_000;
	private static final int PROFILES_PER_PULSE = 128;
	private static final int SWEEP_PULSES = 79;
	private static final long SIGNAL_TTL_MILLIS = 1_000_000;
	private static final PhantomNavigationPoint ORIGIN = new PhantomNavigationPoint(0, 0, 0, 0);
	private final Mode _mode;

	public PhantomScaleEnvelopeGoal029Checkpoint1Suite()
	{
		this(Mode.ALL);
	}

	public PhantomScaleEnvelopeGoal029Checkpoint1Suite(Mode mode)
	{
		_mode = mode;
	}

	@Override
	public String id()
	{
		return "scale-envelope-goal029cp1-" + _mode.name().toLowerCase();
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		if (_mode == Mode.ALL)
		{
			registry.add("01-baseline-envelope-disabled-defaults", this::testBaselineEnvelope);
			registry.add("02-scheduler-10k-cap-two-sweep-fairness", this::testSchedulerCapacityAndFairness);
			registry.add("03-scheduler-overload-wave-and-recovery", this::testOverloadWaveAndRecovery);
			registry.add("04-materialization-cap32-retained-recovery", this::testMaterializationCapacity);
			registry.add("05-navigation-production-saturation-recovery", this::testNavigationSaturation);
			registry.add("06-pure-assessment-and-structural-source-bounds", this::testAssessmentAndStructuralBounds);
			return;
		}
		if (_mode == Mode.SCHEDULER)
		{
			registry.add("01-scheduler-10k-cap-two-sweep-fairness", this::testSchedulerCapacityAndFairness);
			registry.add("02-scheduler-overload-wave-and-recovery", this::testOverloadWaveAndRecovery);
		}
		else if (_mode == Mode.MATERIALIZATION)
		{
			registry.add("01-materialization-cap32-retained-recovery", this::testMaterializationCapacity);
		}
		else
		{
			registry.add("01-navigation-production-saturation-recovery", this::testNavigationSaturation);
		}
	}

	private void testBaselineEnvelope(PhantomTestContext context)
	{
		final PhantomPlayersConfig.Settings disabled = PhantomPlayersConfig.read(context.moduleRoot().resolve(Path.of("dist", "game", "config", "Custom", "PhantomPlayers.ini")));
		PhantomAssertions.assertFalse(disabled.enabled(), "Production Phantom config is no longer disabled.");
		PhantomAssertions.assertEquals(0, disabled.populationTarget(), "Disabled production settings expose a population target.");
		PhantomAssertions.assertEquals(0, disabled.populationActiveTarget(), "Disabled production settings expose an ACTIVE target.");
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> PhantomScaleEnvelope.from(disabled, PhantomNavigationPolicy.productionDefaults()), "Disabled effective settings produced a nonzero scale envelope.");

		final PhantomScaleEnvelope envelope = envelope();
		PhantomAssertions.assertEquals(10_000, envelope.scheduledProfilesCap(), "Scheduled profile cap drifted.");
		PhantomAssertions.assertEquals(32, envelope.materializedCap(), "Materialization cap drifted.");
		PhantomAssertions.assertEquals(100, envelope.schedulerPulseMillis(), "Scheduler pulse drifted.");
		PhantomAssertions.assertEquals(128, envelope.schedulerProfilesPerPulse(), "Scheduler profile budget drifted.");
		PhantomAssertions.assertEquals(0, envelope.populationTarget(), "Population target drifted.");
		PhantomAssertions.assertEquals(0, envelope.populationActiveTarget(), "Population ACTIVE target drifted.");
		PhantomAssertions.assertEquals(2, envelope.populationCreationInFlight(), "Population creation bound drifted.");
		PhantomAssertions.assertEquals(64, envelope.populationBoundariesPerPulse(), "Population boundary budget drifted.");
		PhantomAssertions.assertEquals(64, envelope.partyOperationsPerPulse(), "Party operation budget drifted.");
		PhantomAssertions.assertEquals(1024, envelope.socialCacheProfiles(), "Social cache cap drifted.");
		PhantomAssertions.assertEquals(256, envelope.navigationQueueCap(), "Navigation queue cap drifted.");
		PhantomAssertions.assertEquals(2, envelope.navigationWorkerCap(), "Navigation worker cap drifted.");
		PhantomAssertions.assertEquals(10_000, envelope.navigationTrackedProfilesCap(), "Navigation tracked profile cap drifted.");
		PhantomAssertions.assertEquals(1024, envelope.navigationCacheCap(), "Navigation cache cap drifted.");
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> new PhantomScaleEnvelope(32, 33, 100, 16, 0, 0, 1, 1, 10, 16, 8, 2, 32, 8), "Invalid capacity relationship was normalized.");
		context.record("scaleEnvelope.scheduledProfilesCap", envelope.scheduledProfilesCap());
		context.record("scaleEnvelope.materializedCap", envelope.materializedCap());
		context.record("scaleEnvelope.schedulerPulseMillis", envelope.schedulerPulseMillis());
		context.record("scaleEnvelope.schedulerProfilesPerPulse", envelope.schedulerProfilesPerPulse());
		context.record("scaleEnvelope.navigation", "256/2/10000/1024");
	}

	private void testSchedulerCapacityAndFairness(PhantomTestContext context)
	{
		final ManualClock clock = new ManualClock();
		final long[] deliveries = new long[SCALE + 1];
		final PhantomScheduler scheduler = scheduler(SCALE, PROFILES_PER_PULSE, clock, PhantomSchedulerPolicy.productionDefaults(100), item -> deliveries[(int) item.profileId()]++);
		for (long profileId = 1; profileId <= SCALE; profileId++)
		{
			PhantomAssertions.assertEquals(RegistrationStatus.REGISTERED, scheduler.register(profileId).status(), "Synthetic registration failed.");
			PhantomAssertions.assertEquals(SignalStatus.ACCEPTED, scheduler.submitSignal(profileId, signal(profileId, PhantomActivityState.WARM)).status(), "Synthetic profile was not continuously eligible.");
		}
		PhantomAssertions.assertEquals(RegistrationStatus.CAPACITY_REACHED, scheduler.register(SCALE + 1L).status(), "10001st profile exceeded scheduler capacity.");
		PhantomAssertions.assertEquals(SCALE, scheduler.snapshot().registered(), "Registration capacity changed after rejection.");

		int productivePulses = 0;
		int wallPulses = 0;
		while (productivePulses < (SWEEP_PULSES * 2))
		{
			final long before = delivered(deliveries);
			scheduler.pulse();
			final long delta = delivered(deliveries) - before;
			PhantomAssertions.assertTrue(delta <= PROFILES_PER_PULSE, "Productive pulse exceeded configured profile budget.");
			if (delta > 0)
			{
				productivePulses++;
			}
			wallPulses++;
			if (productivePulses == SWEEP_PULSES)
			{
				for (int profileId = 1; profileId <= SCALE; profileId++)
				{
					PhantomAssertions.assertTrue(deliveries[profileId] >= 1, "First productive sweep starved profile " + profileId + ".");
				}
			}
			PhantomAssertions.assertTrue(scheduler.snapshot().ready() <= SCALE, "Ready queue exceeded scheduled cap.");
			PhantomAssertions.assertTrue(scheduler.snapshot().due() <= SCALE, "Due set exceeded scheduled cap.");
			PhantomAssertions.assertTrue(wallPulses <= 400, "Two productive sweeps did not finish deterministically.");
			clock.advanceMillis(100);
		}
		long minimum = Long.MAX_VALUE;
		long maximum = Long.MIN_VALUE;
		for (int profileId = 1; profileId <= SCALE; profileId++)
		{
			minimum = Math.min(minimum, deliveries[profileId]);
			maximum = Math.max(maximum, deliveries[profileId]);
		}
		PhantomAssertions.assertTrue(minimum >= 2, "Second sweep did not reach every profile.");
		PhantomAssertions.assertTrue((maximum - minimum) <= 1, "Second-sweep delivery skew exceeded one.");
		assertSlotHasNoRuntimeOwnerFields();
		context.record("scheduler.capacityAccepted", SCALE);
		context.record("scheduler.capacityRejectedProfile", SCALE + 1);
		context.record("scheduler.firstSweepProductivePulses", SWEEP_PULSES);
		context.record("scheduler.secondSweepSkew", maximum - minimum);
		context.record("scheduler.maximumPerProductivePulse", PROFILES_PER_PULSE);
		stop(scheduler);
	}

	private void testOverloadWaveAndRecovery(PhantomTestContext context)
	{
		for (PhantomActivityOverloadLevel level : PhantomActivityOverloadLevel.values())
		{
			final int lowerDetail = switch (level)
			{
				case NORMAL -> 1;
				case ELEVATED -> 2;
				case HIGH -> 4;
				case CRITICAL -> 8;
			};
			PhantomAssertions.assertEquals(1, level.cadenceMultiplier(PhantomActivityState.ACTIVE), "ACTIVE cadence degraded.");
			PhantomAssertions.assertEquals(1, level.cadenceMultiplier(PhantomActivityState.NEARBY_PERCEPTIBLE), "NEARBY cadence degraded.");
			PhantomAssertions.assertEquals(lowerDetail, level.cadenceMultiplier(PhantomActivityState.WARM), "WARM cadence multiplier drifted.");
			PhantomAssertions.assertEquals(lowerDetail, level.cadenceMultiplier(PhantomActivityState.BACKGROUND), "BACKGROUND cadence multiplier drifted.");
		}

		final ManualClock clock = new ManualClock();
		final EnumSet<PhantomActivityOverloadLevel> workLevels = EnumSet.noneOf(PhantomActivityOverloadLevel.class);
		final List<PhantomActivityWorkItem> work = new ArrayList<>();
		final PhantomScheduler scheduler = scheduler(100, 10, clock, new PhantomSchedulerPolicy(16, SIGNAL_TTL_MILLIS, 0, 1, 8, 1000, 1000, 1000, 1000, 50), item ->
		{
			work.add(item);
			workLevels.add(item.overloadLevel());
		});
		for (long profileId = 1; profileId <= 100; profileId++)
		{
			scheduler.register(profileId);
			scheduler.submitSignal(profileId, signal(profileId, PhantomActivityState.WARM));
		}
		for (int pulse = 0; pulse < 10; pulse++)
		{
			final int before = work.size();
			scheduler.pulse();
			PhantomAssertions.assertTrue((work.size() - before) <= 10, "Overload pulse exceeded profile budget.");
		}
		PhantomAssertions.assertEquals(EnumSet.allOf(PhantomActivityOverloadLevel.class), workLevels, "WorkItem did not carry every reachable overload level.");
		PhantomAssertions.assertEquals(PhantomActivityOverloadLevel.CRITICAL, scheduler.snapshot().peakOverloadLevel(), "Pressure did not reach CRITICAL.");
		PhantomAssertions.assertEquals(PhantomActivityOverloadLevel.NORMAL, scheduler.snapshot().overloadLevel(), "Drained pressure did not recover to NORMAL.");

		for (long profileId = 1; profileId <= 100; profileId++)
		{
			scheduler.submitSignal(profileId, new PhantomRelevanceSignal("scale." + profileId, 2, PhantomActivityState.WARM, SIGNAL_TTL_MILLIS));
		}
		scheduler.pulse();
		PhantomAssertions.assertEquals(PhantomActivityOverloadLevel.CRITICAL, scheduler.snapshot().overloadLevel(), "Second deterministic pressure wave did not reach CRITICAL.");
		for (long profileId = 1; profileId <= 100; profileId++)
		{
			scheduler.withdrawSignal(profileId, "scale." + profileId, 3);
		}
		for (int pulse = 0; (pulse < 20) && (scheduler.snapshot().ready() > 0); pulse++)
		{
			scheduler.pulse();
		}
		scheduler.pulse();
		PhantomAssertions.assertEquals(PhantomActivityOverloadLevel.NORMAL, scheduler.snapshot().overloadLevel(), "Overload latched after pressure removal.");
		PhantomAssertions.assertEquals(0, scheduler.snapshot().scheduledTaskCount(), "Manual scheduler created a runtime task.");
		context.record("scheduler.overloadLevelsInWork", workLevels);
		context.record("scheduler.overloadPeak", scheduler.snapshot().peakOverloadLevel());
		context.record("scheduler.overloadRecovered", scheduler.snapshot().overloadLevel());
		stop(scheduler);
	}

	private void testMaterializationCapacity(PhantomTestContext context)
	{
		final MaterializationSnapshotFixture fixture = new MaterializationSnapshotFixture(32);
		int peak = 0;
		for (long profileId = 1; profileId <= 32; profileId++)
		{
			PhantomAssertions.assertTrue(fixture.admit(profileId, 1000 + (int) profileId), "Canonical cap fixture rejected admission " + profileId + ".");
			peak = Math.max(peak, fixture.snapshot().retainedEntries());
		}
		PhantomAssertions.assertFalse(fixture.admit(33, 1033), "33rd actor exceeded materialization cap.");
		PhantomAssertions.assertEquals(32, fixture.snapshot().retainedEntries(), "Rejected 33rd actor changed retained ownership.");
		PhantomAssertions.assertFalse(fixture.release(1, false), "Injected retained cleanup failure released capacity.");
		PhantomAssertions.assertFalse(fixture.admit(33, 1033), "Retained cleanup failure was bypassed.");
		PhantomAssertions.assertFalse(fixture.admit(1, 2001), "Retained profile identity was duplicated.");
		PhantomAssertions.assertFalse(fixture.admit(100, 1002), "Retained character identity was duplicated.");
		PhantomAssertions.assertTrue(fixture.release(1, true), "Successful release did not free ownership.");
		PhantomAssertions.assertTrue(fixture.admit(33, 1033), "Later admission did not reuse released capacity.");
		peak = Math.max(peak, fixture.snapshot().retainedEntries());
		PhantomAssertions.assertTrue(peak <= 32, "Materialization peak exceeded cap.");
		PhantomAssertions.assertEquals(Verdict.AT_CAPACITY, envelope().assess(emptyScheduler(), fixture.snapshot(), emptyNavigation()).verdict(), "Valid cap32 snapshot was not accepted at capacity.");
		context.record("materialization.cap", 32);
		context.record("materialization.peak", peak);
		context.record("materialization.retainedCleanupBlockedAdmission", true);
		context.record("materialization.releaseReadmission", true);
	}

	private void testNavigationSaturation(PhantomTestContext context)
	{
		final PhantomNavigationPolicy policy = PhantomNavigationPolicy.productionDefaults();
		final ManualDispatcher dispatcher = new ManualDispatcher();
		final NavigationBackend backend = new NavigationBackend();
		final PhantomNavigationService service = new PhantomNavigationService(policy, backend, dispatcher, () -> 0, new PhantomMetrics());
		PhantomAssertions.assertTrue(service.start(), "Navigation service did not start.");
		for (int profile = 1; profile <= policy.maximumQueuedRequests(); profile++)
		{
			PhantomAssertions.assertEquals(SubmissionStatus.ACCEPTED, service.submit(request(profile, destination(profile))).status(), "Bounded navigation request was not accepted.");
			assertNavigationBounds(service.snapshot(), policy);
		}
		PhantomAssertions.assertEquals(2, dispatcher.size(), "Production worker claims did not stop at two.");
		PhantomAssertions.assertEquals(2, service.snapshot().currentWorkers(), "Current navigation workers changed.");
		PhantomAssertions.assertEquals(256, service.snapshot().queuedRequests(), "Navigation queue did not saturate at 256.");
		final var rejected = service.submit(request(257, destination(257)));
		PhantomAssertions.assertEquals(SubmissionStatus.REJECTED, rejected.status(), "Request beyond queue capacity was accepted.");
		PhantomAssertions.assertEquals(Status.QUEUE_BACKPRESSURE, rejected.immediateResult().status(), "Queue saturation did not return bounded backpressure.");
		dispatcher.runAll();
		PhantomAssertions.assertEquals(0, service.snapshot().queuedRequests(), "Released workers did not drain the queue.");
		PhantomAssertions.assertEquals(0, service.snapshot().currentWorkers(), "Released workers did not return to zero.");
		PhantomAssertions.assertEquals(0, service.snapshot().activeRequests(), "Navigation drain retained active ownership.");

		for (int index = 0; index <= policy.maximumTrackedProfiles(); index++)
		{
			final long profileId = 1000L + index;
			PhantomAssertions.assertEquals(SubmissionStatus.ACCEPTED, service.submit(request(profileId, destination(1000 + index))).status(), "Sequential bounded navigation submission was rejected.");
			dispatcher.runAll();
			assertNavigationBounds(service.snapshot(), policy);
		}
		PhantomAssertions.assertEquals(1024, service.snapshot().cacheEntries(), "Navigation cache did not retain its exact bounded capacity.");
		PhantomAssertions.assertEquals(10_000, service.snapshot().completedResults(), "Tracked terminal results did not retain their exact bounded capacity.");
		final var later = service.submit(request(50_000, destination(50_000)));
		PhantomAssertions.assertEquals(SubmissionStatus.ACCEPTED, later.status(), "Post-drain navigation submission was not accepted.");
		dispatcher.runAll();
		assertNavigationBounds(service.snapshot(), policy);
		context.record("navigation.queuePeak", service.snapshot().peakQueuedRequests());
		context.record("navigation.workerPeak", service.snapshot().peakWorkers());
		context.record("navigation.cachePeak", service.snapshot().peakCacheEntries());
		context.record("navigation.completedRetained", service.snapshot().completedResults());
		service.beginStop();
		PhantomAssertions.assertTrue(service.finishStop(), "Navigation service did not stop after deterministic drain.");
	}

	private void testAssessmentAndStructuralBounds(PhantomTestContext context)
	{
		final PhantomScaleEnvelope envelope = envelope();
		final MaterializationSnapshotFixture materialization = new MaterializationSnapshotFixture(32);
		for (long profileId = 1; profileId <= 32; profileId++)
		{
			materialization.admit(profileId, 2000 + (int) profileId);
		}
		final SchedulerSnapshot schedulerAtCap = new SchedulerSnapshot(SchedulerState.RUNNING, 10_000, 10_000, 0, 10_000, 1, 1, false, PhantomActivityOverloadLevel.CRITICAL, PhantomActivityOverloadLevel.CRITICAL);
		final PhantomNavigationService.ServiceSnapshot navigationAtCap = new PhantomNavigationService.ServiceSnapshot(PhantomNavigationService.ServiceState.RUNNING, 256, 256, 256, 256, 2, 2, 2, 1024, 1024, 1024, 10_000, 10_000, 10_000);
		final var valid = envelope.assess(schedulerAtCap, materialization.snapshot(), navigationAtCap);
		PhantomAssertions.assertEquals(Verdict.AT_CAPACITY, valid.verdict(), "Snapshots exactly at capacity were rejected.");
		PhantomAssertions.assertEquals(PhantomActivityOverloadLevel.CRITICAL, valid.overloadLevel(), "Assessment replaced scheduler overload truth.");

		final SchedulerSnapshot impossibleScheduler = new SchedulerSnapshot(SchedulerState.RUNNING, 10_001, 10_001, 10_001, 10_000, 2, 1, false, PhantomActivityOverloadLevel.HIGH, PhantomActivityOverloadLevel.CRITICAL);
		final PhantomNavigationService.ServiceSnapshot impossibleNavigation = new PhantomNavigationService.ServiceSnapshot(PhantomNavigationService.ServiceState.RUNNING, 10_001, 257, 256, 257, 3, 2, 3, 1025, 1024, 1025, 10_001, 10_001, 10_001);
		final var invalid = envelope.assess(impossibleScheduler, materialization.snapshot(), impossibleNavigation);
		PhantomAssertions.assertEquals(Verdict.VIOLATED, invalid.verdict(), "Impossible snapshots were normalized.");
		PhantomAssertions.assertTrue(invalid.violations().contains(Violation.SCHEDULER_REGISTERED_EXCEEDED), "Scheduler violation was not typed.");
		PhantomAssertions.assertTrue(invalid.violations().contains(Violation.NAVIGATION_QUEUE_EXCEEDED), "Navigation queue violation was not typed.");
		PhantomAssertions.assertTrue(invalid.violations().contains(Violation.NAVIGATION_CACHE_EXCEEDED), "Navigation cache violation was not typed.");
		PhantomAssertions.assertEquals(16, PhantomSchedulerPolicy.productionDefaults(100).maximumSignalSources(), "Signal-source bound drifted.");
		PhantomAssertions.assertEquals(64, PhantomSystem.TRACE_CAPACITY, "Selected trace runtime capacity drifted.");
		PhantomAssertions.assertEquals(64, PhantomSelectedDecisionTrace.MAX_CAPACITY, "Selected trace type capacity drifted.");
		PhantomAssertions.assertEquals(64, PhantomDecisionReplay.MAX_FRAMES, "Replay bundle capacity drifted.");
		assertSlotHasNoRuntimeOwnerFields();
		context.record("assessment.validVerdict", valid.verdict());
		context.record("assessment.invalidViolationCount", invalid.violations().size());
		context.record("structural.selectedTrace", "1x64");
		context.record("structural.replay", "1x64");
	}

	private static PhantomScaleEnvelope envelope()
	{
		return PhantomScaleEnvelope.from(new PhantomPlayersConfig.Settings(true, false), PhantomNavigationPolicy.productionDefaults());
	}

	private static PhantomScheduler scheduler(int capacity, int budget, ManualClock clock, PhantomSchedulerPolicy policy, java.util.function.Consumer<PhantomActivityWorkItem> sink)
	{
		final PhantomMetrics metrics = new PhantomMetrics();
		final PhantomScheduler scheduler = new PhantomScheduler(capacity, 100, budget, policy, clock, (pulse, period) -> null, false, metrics, new PhantomDiagnosticTrace(false, 0, 0, metrics), PhantomActivityMaterializationPort.noop(), sink::accept);
		PhantomAssertions.assertTrue(scheduler.start(), "Manual scheduler did not start.");
		return scheduler;
	}

	private static PhantomRelevanceSignal signal(long profileId, PhantomActivityState state)
	{
		return new PhantomRelevanceSignal("scale." + profileId, 1, state, SIGNAL_TTL_MILLIS);
	}

	private static long delivered(long[] deliveries)
	{
		long total = 0;
		for (int profileId = 1; profileId < deliveries.length; profileId++)
		{
			total += deliveries[profileId];
		}
		return total;
	}

	private static void stop(PhantomScheduler scheduler)
	{
		scheduler.beginStop();
		PhantomAssertions.assertTrue(scheduler.finishStop(), "Manual scheduler did not stop cleanly.");
	}

	private static void assertSlotHasNoRuntimeOwnerFields()
	{
		Class<?> slotClass = null;
		for (Class<?> nested : PhantomScheduler.class.getDeclaredClasses())
		{
			if ("Slot".equals(nested.getSimpleName()))
			{
				slotClass = nested;
				break;
			}
		}
		PhantomAssertions.assertTrue(slotClass != null, "Scheduler Slot class was not found.");
		for (Field field : slotClass.getDeclaredFields())
		{
			PhantomAssertions.assertFalse(Future.class.isAssignableFrom(field.getType()), "Slot stores a Future.");
			PhantomAssertions.assertFalse(Thread.class.isAssignableFrom(field.getType()), "Slot stores a Thread.");
			PhantomAssertions.assertFalse(Executor.class.isAssignableFrom(field.getType()), "Slot stores an Executor.");
		}
	}

	private static SchedulerSnapshot emptyScheduler()
	{
		return new SchedulerSnapshot(SchedulerState.RUNNING, 0, 0, 0, 10_000, 0, 0, false, PhantomActivityOverloadLevel.NORMAL, PhantomActivityOverloadLevel.NORMAL);
	}

	private static PhantomNavigationService.ServiceSnapshot emptyNavigation()
	{
		return new PhantomNavigationService.ServiceSnapshot(PhantomNavigationService.ServiceState.RUNNING, 0, 0, 256, 0, 0, 2, 0, 0, 1024, 0, 0, 0, 0);
	}

	private static PhantomNavigationRequest request(long profileId, PhantomNavigationPoint destination)
	{
		return new PhantomNavigationRequest(profileId, ORIGIN, destination, 0, 1_000_000_000L, 100_000);
	}

	private static PhantomNavigationPoint destination(int index)
	{
		return new PhantomNavigationPoint(1000 + (index % 10_000), index / 10_000, 0, 0);
	}

	private static void assertNavigationBounds(PhantomNavigationService.ServiceSnapshot snapshot, PhantomNavigationPolicy policy)
	{
		PhantomAssertions.assertTrue(snapshot.queuedRequests() <= policy.maximumQueuedRequests(), "Navigation queue exceeded policy.");
		PhantomAssertions.assertTrue(snapshot.currentWorkers() <= policy.maximumConcurrentPathfinders(), "Navigation workers exceeded policy.");
		PhantomAssertions.assertTrue(snapshot.activeRequests() <= policy.maximumTrackedProfiles(), "Navigation active tracking exceeded policy.");
		PhantomAssertions.assertTrue(snapshot.completedResults() <= policy.maximumTrackedProfiles(), "Navigation completed tracking exceeded policy.");
		PhantomAssertions.assertTrue(snapshot.cooldownProfiles() <= policy.maximumTrackedProfiles(), "Navigation cooldown tracking exceeded policy.");
		PhantomAssertions.assertTrue(snapshot.trackedProgressAttempts() <= policy.maximumTrackedProfiles(), "Navigation progress tracking exceeded policy.");
		PhantomAssertions.assertTrue(snapshot.cacheEntries() <= policy.maximumCacheEntries(), "Navigation cache exceeded policy.");
	}

	private static final class ManualClock implements PhantomScheduler.MonotonicClock
	{
		private long _now;

		@Override
		public long nanoTime()
		{
			return _now;
		}

		private void advanceMillis(long millis)
		{
			_now += millis * 1_000_000L;
		}
	}

	private static final class MaterializationSnapshotFixture
	{
		private final int _capacity;
		private final Map<Long, MaterializationSnapshot> _byProfile = new LinkedHashMap<>();
		private final Map<Integer, Long> _byCharacter = new LinkedHashMap<>();

		private MaterializationSnapshotFixture(int capacity)
		{
			_capacity = capacity;
		}

		private boolean admit(long profileId, int characterObjectId)
		{
			if ((_byProfile.size() >= _capacity) || _byProfile.containsKey(profileId) || _byCharacter.containsKey(characterObjectId))
			{
				return false;
			}
			_byProfile.put(profileId, new MaterializationSnapshot(profileId, characterObjectId, State.ACTIVE, true, true, true, true, 0, true, 1, 0));
			_byCharacter.put(characterObjectId, profileId);
			return true;
		}

		private boolean release(long profileId, boolean cleanupSucceeded)
		{
			final MaterializationSnapshot current = _byProfile.get(profileId);
			if ((current == null) || !cleanupSucceeded)
			{
				return false;
			}
			_byProfile.remove(profileId);
			_byCharacter.remove(current.characterObjectId());
			return true;
		}

		private PhantomMaterializationService.ServiceSnapshot snapshot()
		{
			return new PhantomMaterializationService.ServiceSnapshot(ServiceState.RUNNING, _capacity, _capacity - _byProfile.size(), _byProfile.size(), List.copyOf(_byProfile.values()));
		}
	}

	private static final class ManualDispatcher implements PhantomNavigationService.Dispatcher
	{
		private final Deque<Runnable> _workers = new ArrayDeque<>();

		@Override
		public boolean dispatch(Runnable worker)
		{
			_workers.addLast(worker);
			return true;
		}

		private int size()
		{
			return _workers.size();
		}

		private void runAll()
		{
			while (!_workers.isEmpty())
			{
				_workers.removeFirst().run();
			}
		}
	}

	private static final class NavigationBackend implements PhantomNavigationBackend
	{
		private boolean _initialDirect;

		@Override
		public CapabilitySnapshot capability(PhantomNavigationPoint origin, PhantomNavigationPoint destination)
		{
			_initialDirect = true;
			return new CapabilitySnapshot(PhantomNavigationCapability.GEODATA_PATHFINDING, 1);
		}

		@Override
		public boolean canMoveDirect(PhantomNavigationPoint origin, PhantomNavigationPoint destination)
		{
			if (_initialDirect)
			{
				_initialDirect = false;
				return false;
			}
			return true;
		}

		@Override
		public List<PhantomNavigationPoint> findPath(PhantomNavigationRequest request, PhantomNavigationCancellationToken cancellationToken)
		{
			return List.of(request.origin(), request.destination());
		}
	}
}
