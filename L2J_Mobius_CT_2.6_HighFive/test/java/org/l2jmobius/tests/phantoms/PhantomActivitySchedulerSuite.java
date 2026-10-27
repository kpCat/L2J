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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityWorkSink;
import org.l2jmobius.gameserver.phantoms.activity.PhantomMaterializationRetentionPolicy;
import org.l2jmobius.gameserver.phantoms.activity.PhantomRelevanceSignal;
import org.l2jmobius.gameserver.phantoms.activity.PhantomReconcileFirstActivityPort;
import org.l2jmobius.gameserver.phantoms.activity.PhantomSchedulerPolicy;
import org.l2jmobius.gameserver.phantoms.population.PhantomPresenceRegistry;
import org.l2jmobius.gameserver.phantoms.topology.PhantomPerceptionChannel;
import org.l2jmobius.gameserver.phantoms.topology.PhantomSchedulerRelevanceSignalPort;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyService;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyProfileRegistry.RegistrationResult;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyProfileRegistry.UpdateResult;
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
import org.l2jmobius.tests.phantoms.PhantomTopologyCoreSuite;
import org.l2jmobius.tests.phantoms.PhantomTestContext;
import org.l2jmobius.tests.phantoms.PhantomTestRegistry;
import org.l2jmobius.tests.phantoms.PhantomTestSuite;

public final class PhantomActivitySchedulerSuite implements PhantomTestSuite
{
	private final boolean _localPriorityOnly;

	public PhantomActivitySchedulerSuite()
	{
		this(false);
	}

	public PhantomActivitySchedulerSuite(boolean localPriorityOnly)
	{
		_localPriorityOnly = localPriorityOnly;
	}

	@Override
	public String id()
	{
		return _localPriorityOnly ? "activity-scheduler-local-priority" : "activity-scheduler";
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("075-accepted-store-recovery-reuses-bounded-admission", this::testStoreRecovery075);
		if (System.getProperty("phantom.m1.native.focus", "").equals("recovery075")) { return; }
		if (_localPriorityOnly)
		{
			registry.add("01-local-promotion-progresses-while-general-pulse-blocked", _ -> testLocalPromotionUnderBlockedPulse());
			registry.add("02-same-profile-local-promotion-progresses-during-background-work", _ -> testLocalPromotionDuringBackgroundWork());
			return;
		}
		registry.add("01-start-zero-and-registration-bounds", _ -> testRegistration());
		registry.add("02-signal-validation-source-sequence-and-coalescing", _ -> testSignals());
		registry.add("03-queue-backpressure-leaves-signal-unchanged", _ -> testBackpressureAtomicity());
		registry.add("04-highest-detail-expiry-and-demotion-grace", _ -> testAggregationAndHysteresis());
		registry.add("05-active-nearby-canonical-materialization", _ -> testMaterializationBoundary());
		registry.add("06-clean-transition-block-bounded-retry", _ -> testTransientRetry());
		registry.add("07-retained-failure-explicit-retry", _ -> testRetainedFailure());
		registry.add("061-journey-clock-preserves-semantic-effect-revision", _ ->
		{
			final var goal = new PhantomGoal(61, "skill.learn", PhantomGoalStatus.ACTIVE, new org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef("profile", "61"), new org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef("skill", "3:1"), 1, 0, null, List.of(), null, "learning.autonomous", 600, 0, 0, 11_000, Map.of(), "clock.identity061", 7);
			final var started = org.l2jmobius.gameserver.phantoms.decision.PhantomJourneyLifetime.start(goal, 1000);
			final var paused = org.l2jmobius.gameserver.phantoms.decision.PhantomJourneyLifetime.pause(started, 2000);
			final var resumed = org.l2jmobius.gameserver.phantoms.decision.PhantomJourneyLifetime.resume(paused, 602_000);
			PhantomAssertions.assertTrue(started.revision() == 7 && paused.revision() == 7 && resumed.revision() == 7, "Clock-only CAS updates changed an accepted operation's semantic revision and orphaned its payment receipt.");
			PhantomAssertions.assertEquals(611_000L, resumed.deadlineEpochMillis(), "Clock metadata did not preserve nine remaining active seconds.");
		});
		registry.add("061-released-lifecycle-resumes-without-forcing-cleanup", _ ->
		{
			final Fixture released = fixture(2, 2);
			try
			{
				released.port().materializeOutcomes.add(Outcome.RETAINED_FAILURE);
				released.scheduler().register(1); released.scheduler().submitSignal(1, signal("journey", 1, PhantomActivityState.ACTIVE, 100)); released.scheduler().pulse();
				released.clock().advanceMillis(20); released.scheduler().pulse();
				PhantomAssertions.assertEquals(1, released.port().materializeCalls, "Still-owned lifecycle was materialized again.");
				PhantomAssertions.assertEquals(0, released.port().retryCalls, "Automatic observation forced native cleanup of retained ownership.");
				// The accepted lifecycle owner completed its own cleanup; no scheduler/operator cleanup request.
				released.port().lifecycleOwned.remove(1L); released.clock().advanceMillis(20); released.scheduler().pulse(); released.scheduler().pulse();
				PhantomAssertions.assertEquals(PhantomActivityState.ACTIVE, released.scheduler().find(1).orElseThrow().effectiveState(), "Released native ownership left an accepted journey permanently waiting for an explicit operator retry.");
				PhantomAssertions.assertEquals(2, released.port().materializeCalls, "Released lifecycle did not pass through a fresh normal materialization boundary.");
				PhantomAssertions.assertEquals(0, released.port().retryCalls, "Released lifecycle used forced cleanup instead of read-only ownership observation.");
			}
			finally { stop(released.scheduler()); }
		});
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
		registry.add("21-production-background-first-due-waits-5-to-15-minutes", _ -> testProductionBackgroundDue());
		registry.add("22-materialization-reconciles-before-canonical-player", _ -> testReconcileFirstMaterialization());
		registry.add("23-production-policy-10000-profiles-24h", this::testProductionPolicy10000);
		registry.add("24-fresh-human-preempts-policy-wait", _ -> testFreshHumanPolicyWait());
		registry.add("25-hard-and-recent-human-retention", _ -> testRetentionReasons());
		registry.add("26-soft-reclamation-preserves-scheduler-ownership", _ -> testSoftReclamation());
		registry.add("27-source-view-immutable-replacement-withdrawal", _ -> testSourceView());
		registry.add("28-source-view-overdue-read-does-not-expire", _ -> testOverdueSourceView());
		registry.add("29-calendar-active-releases-after-real-locality-expiry", this::testCalendarLocalityRelease);
		registry.add("30-calendar-sleeping-denies-locality-and-background-work", this::testSleepingCalendarLocality);
		registry.add("31-old-unseen-owner-reclaims-without-recent-human-pin", _ -> testSoftReclamation(true));
		registry.add("32-soft-reclaimed-owner-waits-one-grace-before-return", _ -> testSoftReclamation(false, true));
		registry.add("066-idle-retirement-rechecks-return-and-current-epoch", _ ->
		{
			final var clock = new ManualClock(); clock.advanceMillis(1);
			final var delegate = new FakeMaterializationPort();
			final var local = new java.util.HashSet<Long>(Set.of(1L));
			final var retention = new PhantomMaterializationRetentionPolicy(id -> new PhantomMaterializationRetentionPolicy.Facts(delegate.isMaterialized(id), local.contains(id), false, false, false), clock::nanoTime, 60000);
			final var port = new PhantomReconcileFirstActivityPort(new PhantomActivityMaterializationPort()
			{
				@Override public TransitionOutcome materialize(long id) { return delegate.materialize(id); }
				@Override public TransitionOutcome dematerialize(long id) { return delegate.dematerialize(id); }
				@Override public TransitionOutcome reclaimSoft(long id, long epoch) { return epoch == 66 ? delegate.dematerialize(id) : TransitionOutcome.deferred("wrong.epoch"); }
				@Override public TransitionOutcome retryCleanup(long id) { return delegate.retryCleanup(id); }
				@Override public boolean isMaterialized(long id) { return delegate.isMaterialized(id); }
				@Override public boolean hasLifecycleOwnership(long id) { return delegate.hasLifecycleOwnership(id); }
			});
			port.install(local::contains);
			port.installRetention(id -> retention.observe(id).retained());
			port.installSoftReclamation(_ -> 0, id -> !retention.observe(id).retained());
			final var metrics = new PhantomMetrics();
			final var scheduler = new PhantomScheduler(1, 100, 1, PhantomSchedulerPolicy.productionDefaults(100), clock, (_pulse, _period) -> null, false, metrics, new PhantomDiagnosticTrace(false, 0, 0, metrics), port, PhantomActivityWorkSink.noop());
			scheduler.start();
			try
			{
				scheduler.register(1); scheduler.submitSignal(1, signal("life", 1, PhantomActivityState.ACTIVE, 180000)); scheduler.pulse();
				PhantomAssertions.assertTrue(delegate.isMaterialized(1), "Idle retirement input was not admitted through readiness.");
				retention.observe(1); local.clear(); clock.advanceMillis(61000);
				PhantomAssertions.assertTrue(scheduler.requestIdleRetirement(1, 66), "Idle retirement was not queued without pressure.");
				local.add(1L); scheduler.pulse();
				PhantomAssertions.assertTrue(delegate.isMaterialized(1), "Return before execution failed to cancel retirement.");
				local.clear(); clock.advanceMillis(61000);
				PhantomAssertions.assertTrue(scheduler.requestIdleRetirement(1, 65), "Stale epoch test was not queued."); scheduler.pulse();
				PhantomAssertions.assertTrue(delegate.isMaterialized(1), "Stale epoch removed the current lifetime.");
				clock.advanceMillis(1000); scheduler.pulse();
				PhantomAssertions.assertTrue(scheduler.requestIdleRetirement(1, 66), "Current epoch idle retirement was starved."); scheduler.pulse();
				PhantomAssertions.assertFalse(delegate.isMaterialized(1), "Expired idle lifetime required another actor's pressure to retire.");
				PhantomAssertions.assertFalse(scheduler.find(1).orElseThrow().effectiveState().requiresMaterialization(), "Retirement left false scheduler ownership.");
			}
			finally { stop(scheduler); }
		});
	}

	private void testStoreRecovery075(PhantomTestContext context)
	{
		final var clock = new ManualClock();
		final var delegate = new FakeMaterializationPort();
		final var port = new PhantomReconcileFirstActivityPort(delegate);
		final var presence = new PhantomPresenceRegistry(1);
		presence.schedule(1, PhantomActivityState.SLEEPING);
		final var plan = new org.l2jmobius.gameserver.phantoms.economy.PhantomStorePlan(org.l2jmobius.gameserver.phantoms.economy.PhantomStorePlan.Type.SELL, org.l2jmobius.gameserver.phantoms.economy.PhantomStorePlan.State.REQUESTED, "Accepted remaining store", List.of(new org.l2jmobius.gameserver.phantoms.economy.PhantomStorePlan.Line(101, 1865, 3, 40)), 1000);
		final var retained = new org.l2jmobius.gameserver.phantoms.economy.PhantomStoreService.RetainedPlan(501, 7, plan);
		final var request = new org.l2jmobius.gameserver.phantoms.economy.PhantomAutonomousMarketProducer.RecoveryRequest(retained, 21, 2, 1);
		final var pending = new java.util.concurrent.atomic.AtomicReference<org.l2jmobius.gameserver.phantoms.economy.PhantomAutonomousMarketProducer.RecoveryRequest>();
		final var cursorCurrent = new java.util.concurrent.atomic.AtomicBoolean();
		final var unobserved = new java.util.concurrent.atomic.AtomicBoolean();
		port.installReadiness(id -> pending.get() != null && pending.get().validFor(retained, 21, 2, clock.nanoTime() / 1_000_000L) && cursorCurrent.get() && unobserved.get()
			? TransitionOutcome.success() : TransitionOutcome.deferred("market.restore.wait"));
		port.installRetention(id -> pending.get() != null && pending.get().validFor(retained, 21, 2, clock.nanoTime() / 1_000_000L));
		final var metrics = new PhantomMetrics();
		final var scheduler = new PhantomScheduler(1, 10, 1, new PhantomSchedulerPolicy(16, 1000, 5, 2, 8, 1, 2, 3, 4, 50), clock, (_pulse, _period) -> null, false, metrics, new PhantomDiagnosticTrace(false, 0, 0, metrics), port, PhantomActivityWorkSink.noop());
		PhantomAssertions.assertTrue(scheduler.start(), "Recovery scheduler did not start.");
		try
		{
			scheduler.register(1);
			PhantomAssertions.assertEquals(RegistrationStatus.CAPACITY_REACHED, scheduler.register(2).status(), "Recovery bypassed existing profile capacity.");
			for (int phase = 0; phase < 3; phase++)
			{
				if (phase == 1) { pending.set(request); }
				if (phase == 2) { cursorCurrent.set(true); }
				scheduler.submitSignal(1, signal("market.accepted_restore", phase + 1, PhantomActivityState.ACTIVE, 1000));
				scheduler.pulse();
				PhantomAssertions.assertEquals(0, delegate.materializeCalls, "Absent owner, unfinished cursor or observed location reached Player admission.");
			}
			unobserved.set(true);
			delegate.materializeOutcomes.add(Outcome.DEFERRED); // Existing native capacity wait, not permission to allocate another actor.
			scheduler.submitSignal(1, signal("market.accepted_restore", 4, PhantomActivityState.ACTIVE, 1000)); scheduler.pulse();
			PhantomAssertions.assertEquals(1, delegate.materializeCalls, "Accepted continuation did not pass through the existing native delegate.");
			PhantomAssertions.assertFalse(delegate.isMaterialized(1), "Deferred native capacity created a Player.");
			scheduler.submitSignal(1, signal("market.accepted_restore", 5, PhantomActivityState.ACTIVE, 1000)); scheduler.pulse();
			PhantomAssertions.assertTrue(delegate.isMaterialized(1), "Capacity release did not resume the same continuation.");
			for (int replay = 0; replay < 3; replay++) { scheduler.pulse(); }
			PhantomAssertions.assertEquals(2, delegate.materializeCalls, "Duplicate recovery pulses allocated another actor.");
			PhantomAssertions.assertFalse(presence.permitsOrdinaryFarm(1), "Store continuation changed the sleeping farm schedule.");
			clock.advanceMillis(1000);
			PhantomAssertions.assertFalse(request.validFor(retained, 21, 2, clock.nanoTime() / 1_000_000L), "Recovery signal extended the absolute store expiry.");
			pending.set(null); scheduler.withdrawSignal(1, "market.accepted_restore", 5);
			for (int pulse = 0; pulse < 4; pulse++) { clock.advanceMillis(100); scheduler.pulse(); }
			PhantomAssertions.assertFalse(delegate.isMaterialized(1), "Expired continuation retained its native actor.");
		}
		finally { stop(scheduler); }
		context.record("owner075.recoveryAdmission", "same_queue+cursor_visibility_negative+capacity_wait+single_actor+expiry+sleeping_farm_off");
	}

	private void testCalendarLocalityRelease(PhantomTestContext context)
	{
		testCalendarLocality(context, false);
	}

	private void testSleepingCalendarLocality(PhantomTestContext context)
	{
		testCalendarLocality(context, true);
	}

	private void testCalendarLocality(PhantomTestContext context, boolean sleeping)
	{
		final var clock = new ManualClock();
		final var delegate = new FakeMaterializationPort();
		final var port = new PhantomReconcileFirstActivityPort(delegate);
		final var metrics = new PhantomMetrics();
		final List<PhantomActivityWorkItem> work = new ArrayList<>();
		final var scheduler = new PhantomScheduler(2, 100, 2, PhantomSchedulerPolicy.productionDefaults(100), clock, (_pulse, _period) -> null, false, metrics, new PhantomDiagnosticTrace(false, 0, 0, metrics), port, work::add);
		PhantomAssertions.assertTrue(scheduler.start(), "Calendar/locality scheduler did not start.");
		final var catalog = org.l2jmobius.gameserver.phantoms.population.PhantomPopulationCatalog.load(context.moduleRoot().resolve("dist/game/data/phantoms/population/high-five-population-v1.xml"), java.time.ZoneOffset.UTC);
		final var store = new org.l2jmobius.tests.phantoms.PhantomPopulationTestDoubles.MemoryStore(catalog.hash());
		store.seedReady(1, 1);
		store.seedReady(2, 1);
		final var calendarClock = java.time.Clock.fixed(java.time.Instant.parse(sleeping ? "2026-01-05T12:00:00Z" : "2026-01-05T20:00:00Z"), java.time.ZoneOffset.UTC);
		final var population = new org.l2jmobius.gameserver.phantoms.population.PhantomPopulationManager(store, catalog, null, new CalendarSchedulerOwnership(scheduler, delegate), calendarClock, java.time.ZoneOffset.UTC, 2, 1, 2, 1, 1, 64);
		final var backend = new PhantomTopologyCoreSuite.TestBackend();
		final var signals = new PhantomSchedulerRelevanceSignalPort(scheduler);
		final var topology = PhantomTopologyService.fromSnapshotForTesting(PhantomTopologyCoreSuite.snapshot(backend), backend, PhantomTopologyCoreSuite.POLICY, signals);
		PhantomAssertions.assertTrue(topology.start(), "Calendar/locality topology did not start.");
		population.installTopologyMembership(id ->
		{
			topology.registerProfile(id);
			topology.updateProfile(id, id == 1 ? PhantomTopologyCoreSuite.LEFT_POINT : new PhantomTopologyPoint(10000, 10000, 0, 0), 1);
		}, topology::unregisterProfile);
		final var humans = new java.util.concurrent.atomic.AtomicReference<List<PhantomTopologyPoint>>(List.of(PhantomTopologyCoreSuite.LEFT_POINT));
		final var locality = new org.l2jmobius.gameserver.phantoms.topology.PhantomHumanLocalityControl(topology, signals, humans::get, () -> clock.nanoTime() / 1_000_000L, population.presence()::isOnline);
		final var retention = new org.l2jmobius.gameserver.phantoms.activity.PhantomMaterializationRetentionPolicy(id -> new org.l2jmobius.gameserver.phantoms.activity.PhantomMaterializationRetentionPolicy.Facts(delegate.isMaterialized(id), locality.isLocal(id), false, false, false), clock::nanoTime, 60_000);
		final var accepted = new java.util.concurrent.atomic.AtomicBoolean();
		port.installPopulationReadiness(population.presence(), locality, null, _ -> accepted.get());
		port.installRetention(id -> retention.observe(id).retained());
		PhantomAssertions.assertTrue(population.start(), "Calendar population did not start.");
		try
		{
			for (int pulse = 0; pulse < 12; pulse++) { population.onPulse(); }
			PhantomAssertions.assertEquals(sleeping ? 0 : 1, population.admissionSnapshot().admittedActive(), "Calendar admission capacity changed.");
			final long admitted = sleeping || population.admissionProfile(1).orElseThrow().admitted() ? 1 : 2;
			topology.updateProfile(admitted, PhantomTopologyCoreSuite.LEFT_POINT, 2);
			topology.updateProfile(3 - admitted, new PhantomTopologyPoint(10000, 10000, 0, 0), 2);
			locality.onPulse();
			scheduler.pulse();
			if (sleeping)
			{
				PhantomAssertions.assertFalse(population.presence().permitsOrdinaryFarm(1), "Sleeping calendar granted ordinary farm.");
				PhantomAssertions.assertFalse(locality.isLocal(1), "Sleeping calendar admitted a human.local signal.");
				PhantomAssertions.assertTrue(scheduler.signalSources(1).isEmpty(), "Sleeping calendar retained a work source.");
				PhantomAssertions.assertEquals(PhantomActivityState.SLEEPING, scheduler.find(1).orElseThrow().effectiveState(), "Sleeping calendar started work.");
				PhantomAssertions.assertTrue(work.isEmpty(), "Sleeping calendar dispatched background work.");
				accepted.set(true);
				PhantomAssertions.assertEquals(Outcome.SUCCESS, port.materialize(1).outcome(), "A previously accepted finite journey cannot resume after a JVM restart across its calendar boundary.");
				PhantomAssertions.assertTrue(delegate.isMaterialized(1), "Accepted continuation did not reach the native delegate.");
				PhantomAssertions.assertFalse(population.presence().permitsOrdinaryFarm(1), "Accepted continuation changed calendar eligibility for ordinary farming.");
				accepted.set(false);
				PhantomAssertions.assertEquals(Outcome.DEFERRED, port.materialize(2).outcome(), "Finished or invalid continuation opened a sleeping actor.");
				delegate.dematerialize(1);
				context.record("journey061.offlineContinuation", "sleeping/no work remains negative; exact accepted finite owner resumes through the existing delegate; calendar farm permission unchanged; withdrawal closes gate");
				return;
			}
			PhantomAssertions.assertTrue(population.presence().permitsOrdinaryFarm(admitted), "Active calendar lost logical farm eligibility.");
			PhantomAssertions.assertTrue(delegate.isMaterialized(admitted), "Real human.local did not prewarm native ownership.");
			retention.observe(admitted);
			humans.set(List.of());
			clock.advanceMillis(11_000);
			locality.onPulse();
			scheduler.pulse();
			PhantomAssertions.assertTrue(delegate.isMaterialized(admitted), "Recent human continuity was torn down early.");
			clock.advanceMillis(50_000);
			locality.onPulse();
			scheduler.pulse();
			clock.advanceMillis(30_000);
			scheduler.pulse();
			context.record("task045.scheduleSourcesAfterAway", scheduler.signalSources(admitted).toString());
			context.record("task045.requestedAfterAway", scheduler.find(admitted).orElseThrow().requestedState().name());
			PhantomAssertions.assertFalse(retention.observe(admitted).retained(), "Fixture still has a legitimate native hold.");
			PhantomAssertions.assertFalse(scheduler.find(admitted).orElseThrow().requestedState().requiresMaterialization(), "Calendar ACTIVE still requests a physical Player after human.local expiry and settlement.");
			PhantomAssertions.assertFalse(delegate.isMaterialized(admitted), "Calendar-only profile did not finish lawful native release.");
			PhantomAssertions.assertEquals(PhantomActivityState.BACKGROUND, scheduler.find(admitted).orElseThrow().effectiveState(), "Admitted active calendar lost ordinary background execution.");
			PhantomAssertions.assertEquals(PhantomActivityState.ACTIVE, population.admissionProfile(admitted).orElseThrow().effectiveState(), "Physical projection rewrote logical admission.");
			PhantomAssertions.assertTrue(population.presence().permitsOrdinaryFarm(admitted), "Release made the active calendar offline.");
			PhantomAssertions.assertEquals(PhantomActivityState.WARM, scheduler.find(3 - admitted).orElseThrow().effectiveState(), "Non-admitted schedule bypassed ACTIVE admission capacity.");
			PhantomAssertions.assertEquals(1, delegate.materializeCalls, "Calendar alone constructed another Player.");
			final var schedule = scheduler.signalSources(admitted).stream().filter(source -> source.sourceKey().equals("population.schedule")).findFirst().orElseThrow();
			PhantomAssertions.assertEquals(SignalStatus.STALE, scheduler.submitSignal(admitted, signal("population.schedule", schedule.sequence(), PhantomActivityState.ACTIVE, 10_000)).status(), "Stale schedule sequence replaced the lawful projection.");
			humans.set(List.of(PhantomTopologyCoreSuite.LEFT_POINT));
			clock.advanceMillis(1000);
			locality.onPulse();
			scheduler.pulse();
			PhantomAssertions.assertTrue(delegate.isMaterialized(admitted), "Current human.local did not rematerialize the same profile.");
			PhantomAssertions.assertEquals(2, delegate.materializeCalls, "Same-profile return duplicated native ownership.");
		}
		finally
		{
			population.beginStop();
			PhantomAssertions.assertTrue(population.finishStop(), "Calendar population did not stop.");
			stop(scheduler);
			topology.beginStop();
			PhantomAssertions.assertTrue(topology.finishStop(), "Calendar topology did not stop.");
		}
	}

	/** Only decision attachment is inert; both producers deliver through the real scheduler API. */
	private record CalendarSchedulerOwnership(PhantomScheduler scheduler, FakeMaterializationPort nativeOwner) implements org.l2jmobius.gameserver.phantoms.population.PhantomPopulationOwnershipPort
	{
		@Override public RegistrationStatus register(long id) { return scheduler.register(id).status(); }
		@Override public PhantomDecisionEngine.AttachResult attach(long id) { return PhantomDecisionEngine.AttachResult.ATTACHED; }
		@Override public SignalStatus submit(long id, String source, long sequence, PhantomActivityState state, long ttl) { return scheduler.submitSignal(id, signal(source, sequence, state, ttl)).status(); }
		@Override public SignalStatus withdraw(long id, String source, long sequence) { return scheduler.withdrawSignal(id, source, sequence).status(); }
		@Override public UnregisterStatus unregister(long id) { return scheduler.unregister(id).status(); }
		@Override public PhantomDecisionEngine.DetachResult detach(long id) { return PhantomDecisionEngine.DetachResult.DETACHED; }
		@Override public PhantomDecisionEngine.ReloadResult reload(long id) { return PhantomDecisionEngine.ReloadResult.RELOADED; }
		@Override public boolean registered(long id) { return scheduler.find(id).isPresent(); }
		@Override public boolean materialized(long id) { return nativeOwner.isMaterialized(id); }
		@Override public int registeredCount() { return scheduler.snapshot().registered(); }
	}

	private void testSourceView()
	{
		final Fixture fixture = fixture(2, 2);
		final PhantomScheduler scheduler = fixture.scheduler();
		scheduler.register(1);
		scheduler.submitSignal(1, signal("combat", 1, PhantomActivityState.ACTIVE, 100));
		scheduler.submitSignal(1, signal("human.local", 2, PhantomActivityState.NEARBY_PERCEPTIBLE, 200));
		final var before = scheduler.find(1).orElseThrow();
		final var metrics = fixture.metrics().snapshot();
		final var sources = scheduler.signalSources(1);
		PhantomAssertions.assertEquals(2, sources.size(), "Both accepted source owners must be visible.");
		PhantomAssertions.assertEquals("combat", sources.get(0).sourceKey(), "Sources must be sorted by key.");
		PhantomAssertions.assertEquals(1L, sources.get(0).sequence(), "Accepted sequence was lost.");
		PhantomAssertions.assertEquals(PhantomActivityState.ACTIVE, sources.get(0).requiredState(), "Required state was lost.");
		PhantomAssertions.assertEquals(100L, sources.get(0).originalTtlMillis(), "Original TTL was lost.");
		PhantomAssertions.assertEquals(100_000_000L, sources.get(0).expiresAtNanos(), "Absolute expiry was lost.");
		PhantomAssertions.assertEquals(100L, sources.get(0).remainingTtlMillis(), "Remaining TTL was lost.");
		PhantomAssertions.assertFalse(sources.get(0).expiredAtRead(), "Fresh source was marked overdue.");
		PhantomAssertions.assertEquals(before, scheduler.find(1).orElseThrow(), "Source read changed scheduler state.");
		PhantomAssertions.assertEquals(metrics, fixture.metrics().snapshot(), "Source read changed metrics.");
		PhantomAssertions.assertTrue(scheduler.signalSources(999).isEmpty(), "Unknown profile must have no sources.");
		boolean immutable = false;
		try { sources.clear(); }
		catch (UnsupportedOperationException expected) { immutable = true; }
		PhantomAssertions.assertTrue(immutable, "Source list must be immutable.");
		fixture.clock().advanceMillis(25);
		scheduler.submitSignal(1, signal("combat", 3, PhantomActivityState.ACTIVE, 300));
		final var replaced = scheduler.signalSources(1).get(0);
		PhantomAssertions.assertEquals(3L, replaced.sequence(), "Replacement sequence was not captured.");
		PhantomAssertions.assertEquals(325_000_000L, replaced.expiresAtNanos(), "Replacement expiry was not captured.");
		PhantomAssertions.assertEquals(100L, sources.get(0).remainingTtlMillis(), "Old snapshot changed after replacement.");
		scheduler.withdrawSignal(1, "combat", 4);
		PhantomAssertions.assertEquals(1, scheduler.signalSources(1).size(), "Withdrawn source must be omitted.");
		PhantomAssertions.assertEquals("human.local", scheduler.signalSources(1).get(0).sourceKey(), "Wrong remaining source owner.");
		stop(scheduler);
	}

	private void testOverdueSourceView()
	{
		final Fixture fixture = fixture(1, 1);
		final PhantomScheduler scheduler = fixture.scheduler();
		scheduler.register(1);
		scheduler.submitSignal(1, signal("combat", 1, PhantomActivityState.ACTIVE, 100));
		scheduler.pulse();
		fixture.clock().advanceMillis(101);
		final var before = scheduler.find(1).orElseThrow();
		final var sources = scheduler.signalSources(1);
		PhantomAssertions.assertEquals(1, sources.size(), "Overdue unpulsed source must remain visible.");
		PhantomAssertions.assertTrue(sources.get(0).expiredAtRead(), "Overdue source must be identified.");
		PhantomAssertions.assertEquals(-1L, sources.get(0).remainingTtlMillis(), "Signed overdue time must be preserved.");
		PhantomAssertions.assertEquals(101_000_000L, sources.get(0).sampleNanos(), "Snapshot must use scheduler clock.");
		PhantomAssertions.assertEquals(before, scheduler.find(1).orElseThrow(), "Read must not expire source or demote ACTIVE.");
		scheduler.pulse();
		PhantomAssertions.assertTrue(scheduler.signalSources(1).isEmpty(), "Ordinary pulse must expire the source.");
		stop(scheduler);
	}

	private void testLocalPromotionUnderBlockedPulse() throws InterruptedException
	{
		final ManualClock clock = new ManualClock();
		final FakeMaterializationPort port = new FakeMaterializationPort();
		final PhantomMetrics metrics = new PhantomMetrics();
		final CountDownLatch generalEntered = new CountDownLatch(1);
		final CountDownLatch releaseGeneral = new CountDownLatch(1);
		final AtomicInteger ordinaryWork = new AtomicInteger();
		final PhantomScheduler scheduler = new PhantomScheduler(2, 100, 2, PhantomSchedulerPolicy.productionDefaults(100), clock, (pulse, period) -> null, false, metrics, new PhantomDiagnosticTrace(false, 16, 1, metrics), port, _ -> ordinaryWork.incrementAndGet());
		PhantomAssertions.assertTrue(scheduler.installControlPort(() ->
		{
			generalEntered.countDown();
			await(releaseGeneral, "Timed out waiting to release blocked general pulse.");
		}), "General control port could not be installed.");
		PhantomAssertions.assertTrue(scheduler.installLocalControlPort(() -> scheduler.submitSignal(2, signal("human.local", 1, PhantomActivityState.NEARBY_PERCEPTIBLE, 10_000))), "Local control port could not be installed.");
		PhantomAssertions.assertTrue(scheduler.start(), "Priority-lane scheduler did not start.");
		scheduler.register(1);
		scheduler.register(2);
		final Thread general = pulseThread(scheduler, "blocked-general-pulse");
		general.start();
		try
		{
			await(generalEntered, "General pulse did not enter its blocking control stage.");
			scheduler.localPulse();
			PhantomAssertions.assertEquals(PhantomActivityState.NEARBY_PERCEPTIBLE, scheduler.find(2).orElseThrow().effectiveState(), "Human-local signal waited for the blocked general pulse.");
			PhantomAssertions.assertEquals(1, port.materializeCalls, "Human-local signal was not materialized exactly once.");
			PhantomAssertions.assertEquals(0, ordinaryWork.get(), "Priority lane dispatched ordinary work.");
		}
		finally
		{
			releaseGeneral.countDown();
			join(general);
		}
		stop(scheduler);
	}

	private void testLocalPromotionDuringBackgroundWork() throws InterruptedException
	{
		final ManualClock clock = new ManualClock();
		final FakeMaterializationPort port = new FakeMaterializationPort();
		final PhantomMetrics metrics = new PhantomMetrics();
		final CountDownLatch workEntered = new CountDownLatch(1);
		final CountDownLatch releaseWork = new CountDownLatch(1);
		final PhantomSchedulerPolicy policy = new PhantomSchedulerPolicy(16, 10_000, 2000, 1000, 30_000, 100, 250, 1000, 1, 50);
		final PhantomScheduler scheduler = new PhantomScheduler(1, 100, 1, policy, clock, (pulse, period) -> null, false, metrics, new PhantomDiagnosticTrace(false, 16, 1, metrics), port, _ ->
		{
			workEntered.countDown();
			await(releaseWork, "Timed out waiting to release background work.");
		});
		PhantomAssertions.assertTrue(scheduler.installLocalControlPort(() -> scheduler.submitSignal(1, signal("human.local", 1, PhantomActivityState.NEARBY_PERCEPTIBLE, 10_000))), "Same-profile local control port could not be installed.");
		PhantomAssertions.assertTrue(scheduler.start(), "Same-profile priority fixture did not start.");
		scheduler.register(1);
		scheduler.submitSignal(1, signal("background", 1, PhantomActivityState.BACKGROUND, 10_000));
		final Thread general = pulseThread(scheduler, "blocked-background-work");
		general.start();
		try
		{
			await(workEntered, "Background work did not block the general pulse.");
			scheduler.localPulse();
			PhantomAssertions.assertEquals(PhantomActivityState.NEARBY_PERCEPTIBLE, scheduler.find(1).orElseThrow().effectiveState(), "Same-profile background work delayed local materialization.");
			PhantomAssertions.assertEquals(1, port.materializeCalls, "Same-profile priority promotion was not exactly once.");
		}
		finally
		{
			releaseWork.countDown();
			join(general);
		}
		stop(scheduler);
	}

	private void testProductionPolicy10000(PhantomTestContext context) throws Exception
	{
		final int profiles = 10_000;
		final PhantomSchedulerPolicy policy = PhantomSchedulerPolicy.productionDefaults(10);
		long minimumCadence = Long.MAX_VALUE;
		long maximumCadence = 0;
		long totalCadence = 0;
		for (long profileId = 1; profileId <= profiles; profileId++)
		{
			final long cadence = policy.cadenceMillis(PhantomActivityState.BACKGROUND, profileId, 1);
			minimumCadence = Math.min(minimumCadence, cadence);
			maximumCadence = Math.max(maximumCadence, cadence);
			totalCadence += cadence;
		}
		PhantomAssertions.assertTrue((minimumCadence >= 300_000) && (maximumCadence <= 900_000) && (maximumCadence > minimumCadence), "10k cadence escaped the deterministic 5-15m distribution.");
		final ManualClock clock = new ManualClock();
		final PhantomMetrics metrics = new PhantomMetrics();
		final AtomicInteger dispatches = new AtomicInteger();
		final PhantomScheduler scheduler = new PhantomScheduler(profiles, 10, profiles, policy, clock, (pulse, period) -> null, false, metrics, new PhantomDiagnosticTrace(false, 16, 1, metrics), PhantomActivityMaterializationPort.noop(), item -> dispatches.incrementAndGet());
		PhantomAssertions.assertTrue(scheduler.start(), "10k production-policy scheduler did not start.");
		final PhantomPresenceRegistry presence = new PhantomPresenceRegistry(profiles);
		presence.installExternalBusySource("party", profileId -> (profileId % 100) == 1);
		final PhantomTopologyCoreSuite.TestBackend topologyBackend = new PhantomTopologyCoreSuite.TestBackend();
		final PhantomTopologyService topology = PhantomTopologyService.fromSnapshotForTesting(PhantomTopologyCoreSuite.snapshot(topologyBackend), topologyBackend, PhantomTopologyCoreSuite.POLICY.withMaximumRegisteredProfiles(profiles), new PhantomSchedulerRelevanceSignalPort(scheduler));
		PhantomAssertions.assertTrue(topology.start(), "Integrated 10k topology registry did not start.");
		for (long profileId = 1; profileId <= profiles; profileId++)
		{
			PhantomAssertions.assertEquals(RegistrationStatus.REGISTERED, scheduler.register(profileId).status(), "10k registration exceeded the configured bound.");
			final boolean online = (profileId % 10) != 0;
			presence.schedule(profileId, online ? PhantomActivityState.BACKGROUND : PhantomActivityState.SLEEPING);
			if (online)
			{
				scheduler.submitSignal(profileId, signal("background", 1, PhantomActivityState.BACKGROUND, PhantomRelevanceSignal.MAXIMUM_TTL_MILLIS));
			}
			PhantomAssertions.assertEquals(RegistrationResult.REGISTERED, topology.registerProfile(profileId), "Integrated 10k topology registration failed.");
			PhantomAssertions.assertEquals(UpdateResult.UPDATED, topology.updateProfile(profileId, profileId <= 100 ? PhantomTopologyCoreSuite.LEFT_POINT : new PhantomTopologyPoint(50, 50, 50, 0), 1), "Integrated 10k committed position failed.");
		}
		final var local = topology.perceptibleProfilesAt(PhantomTopologyCoreSuite.LEFT_POINT, PhantomPerceptionChannel.TARGETABILITY, 32);
		PhantomAssertions.assertEquals(32, local.size(), "Integrated local query lost its cap.");
		PhantomAssertions.assertEquals(100, topology.registrySnapshot().maximumCandidatesExamined(), "Integrated local query scanned remote profiles.");
		PhantomAssertions.assertEquals(100, presence.snapshot().externalBusy(), "External BUSY count changed in integrated 10k run.");
		PhantomAssertions.assertEquals(1000, presence.snapshot().offline(), "Representative offline schedule count changed.");
		int maximumDue = 0;
		int maximumReady = 0;
		for (int minute = 0; minute < 1440; minute++)
		{
			scheduler.pulse();
			final var snapshot = scheduler.snapshot();
			maximumDue = Math.max(maximumDue, snapshot.due());
			maximumReady = Math.max(maximumReady, snapshot.ready());
			PhantomAssertions.assertTrue((snapshot.due() + snapshot.ready()) <= profiles, "Shared due/ready queues exceeded logical profile capacity.");
			clock.advanceMillis(60_000);
		}
		PhantomAssertions.assertTrue(dispatches.get() > profiles, "24h simulation did not dispatch recurring BACKGROUND work.");
		PhantomAssertions.assertTrue((maximumDue <= profiles) && (maximumReady <= profiles), "24h simulation exceeded bounded shared queues.");
		scheduler.beginStop();
		PhantomAssertions.assertTrue(scheduler.finishStop(), "10k scheduler did not stop cleanly.");
		final var registry = topology.registrySnapshot();
		topology.beginStop();
		PhantomAssertions.assertTrue(topology.finishStop(), "Integrated 10k topology registry did not stop cleanly.");
		final String output = "metric\tvalue\n" + "scope\tintegrated_scheduler_presence_topology\n" + "profiles\t" + profiles + "\n" + "hours\t24\n" + "dispatches\t" + dispatches.get() + "\n" + "cadence_min_ms\t" + minimumCadence + "\n" + "cadence_mean_ms\t" + (totalCadence / profiles) + "\n" + "cadence_max_ms\t" + maximumCadence + "\n" + "queue_due_max\t" + maximumDue + "\n" + "queue_ready_max\t" + maximumReady + "\n" + "presence_available\t" + presence.snapshot().available() + "\n" + "presence_external_busy\t" + presence.snapshot().externalBusy() + "\n" + "presence_offline\t" + presence.snapshot().offline() + "\n" + "registry_profiles\t" + registry.registered() + "\n" + "registry_resolved\t" + registry.resolved() + "\n" + "registry_buckets\t" + registry.occupiedNodeBuckets() + "\n" + "registry_query_max_candidates\t" + registry.maximumCandidatesExamined() + "\n" + "shared_scheduler_drivers\t1\n" + "players_constructed\t0\n" + "per_profile_futures\t0\n";
		Files.writeString(context.moduleRoot().resolve("docs/phantoms/live-world/LIVE003_CORE_SCALE_10000.tsv"), output, StandardCharsets.UTF_8);
		context.record("live003.scaleDispatches", dispatches.get());
	}

	private void testReconcileFirstMaterialization()
	{
		final FakeMaterializationPort delegate = new FakeMaterializationPort();
		final PhantomReconcileFirstActivityPort port = new PhantomReconcileFirstActivityPort(delegate);
		final java.util.concurrent.atomic.AtomicBoolean cursorCurrent = new java.util.concurrent.atomic.AtomicBoolean();
		final java.util.concurrent.atomic.AtomicBoolean nativeVisible = new java.util.concurrent.atomic.AtomicBoolean();
		port.install(profileId -> (profileId == 1) && cursorCurrent.get());
		port.installRetention(profileId -> (profileId == 1) && nativeVisible.get());
		PhantomAssertions.assertEquals("DEFERRED", port.materialize(1).outcome().name(), "Policy admission was reported as a transition failure.");
		PhantomAssertions.assertEquals(0, delegate.materializeCalls, "Blocked materialization reached the Player owner.");
		cursorCurrent.set(true);
		nativeVisible.set(true);
		PhantomAssertions.assertEquals(Outcome.SUCCESS, port.materialize(1).outcome(), "Reconciled visible profile did not materialize.");
		PhantomAssertions.assertEquals("DEFERRED", port.dematerialize(1).outcome().name(), "Native retention was reported as a transition failure.");
		PhantomAssertions.assertEquals(0, delegate.dematerializeCalls, "Visible retention reached the Player owner.");
		nativeVisible.set(false);
		PhantomAssertions.assertEquals(Outcome.SUCCESS, port.dematerialize(1).outcome(), "Exited Player did not store after the visible retention cleared.");
		for (int cycle = 0; cycle < 100; cycle++)
		{
			PhantomAssertions.assertEquals(Outcome.SUCCESS, port.materialize(1).outcome(), "Reconciled profile did not materialize.");
			PhantomAssertions.assertTrue(port.isMaterialized(1) && port.hasLifecycleOwnership(1), "Canonical materialization owner was lost.");
			PhantomAssertions.assertEquals(Outcome.SUCCESS, port.dematerialize(1).outcome(), "Canonical dematerialization failed.");
			PhantomAssertions.assertFalse(port.isMaterialized(1) || port.hasLifecycleOwnership(1), "Lifecycle ownership leaked after dematerialization.");
		}
		PhantomAssertions.assertEquals(101, delegate.materializeCalls, "Materialization was duplicated after reconciliation.");
	}

	private void testFreshHumanPolicyWait()
	{
		final ManualClock clock = new ManualClock();
		final FakeMaterializationPort delegate = new FakeMaterializationPort();
		final var admitted = new java.util.concurrent.atomic.AtomicBoolean();
		final var port = new PhantomReconcileFirstActivityPort(delegate);
		port.install(_ -> admitted.get());
		final var metrics = new PhantomMetrics();
		final var scheduler = new PhantomScheduler(2, 100, 2, PhantomSchedulerPolicy.productionDefaults(100), clock, (_pulse, _period) -> null, false, metrics, new PhantomDiagnosticTrace(false, 0, 0, metrics), port, PhantomActivityWorkSink.noop());
		scheduler.start();
		try
		{
			scheduler.register(1);
			scheduler.submitSignal(1, signal("population", 1, PhantomActivityState.ACTIVE, 120_000));
			scheduler.pulse();
			for (long delay : new long[] {1000, 2000, 4000, 8000, 16000})
			{
				clock.advanceMillis(delay);
				scheduler.pulse();
			}
			admitted.set(true);
			scheduler.submitSignal(1, signal("human.local", 1, PhantomActivityState.NEARBY_PERCEPTIBLE, 10_000));
			scheduler.pulse();
			PhantomAssertions.assertEquals(PhantomActivityState.ACTIVE, scheduler.find(1).orElseThrow().effectiveState(), "Fresh human locality inherited the old failure deadline.");
			PhantomAssertions.assertEquals(1, delegate.materializeCalls, "Policy wait reached canonical ownership or duplicated promotion.");
		}
		finally
		{
			stop(scheduler);
		}
	}

	private void testRetentionReasons()
	{
		final var clock = new ManualClock();
		final var facts = new java.util.concurrent.atomic.AtomicReference<>(new org.l2jmobius.gameserver.phantoms.activity.PhantomMaterializationRetentionPolicy.Facts(true, true, true, false, false));
		final var policy = new org.l2jmobius.gameserver.phantoms.activity.PhantomMaterializationRetentionPolicy(_ -> facts.get(), clock::nanoTime, 60_000);
		PhantomAssertions.assertTrue(policy.observe(1).hard(), "Native-visible Player was not hard retained.");
		facts.set(new org.l2jmobius.gameserver.phantoms.activity.PhantomMaterializationRetentionPolicy.Facts(true, false, false, true, false));
		clock.advanceMillis(61_000);
		PhantomAssertions.assertTrue(policy.observe(1).hard(), "REAL party lost retention when human locality expired.");
		PhantomAssertions.assertEquals(0L, policy.oldestSoft(List.of(1L)), "Capacity pressure selected a hard party pin.");
		facts.set(new org.l2jmobius.gameserver.phantoms.activity.PhantomMaterializationRetentionPolicy.Facts(true, true, false, false, false));
		policy.observe(1);
		facts.set(new org.l2jmobius.gameserver.phantoms.activity.PhantomMaterializationRetentionPolicy.Facts(true, false, false, false, false));
		clock.advanceMillis(59_999);
		PhantomAssertions.assertTrue(policy.observe(1).retained(), "Short human exit lost continuity.");
		PhantomAssertions.assertEquals(0L, policy.oldestSoft(List.of(1L)), "Recent human continuity was reclaimed before its full grace.");
		clock.advanceMillis(1);
		PhantomAssertions.assertFalse(policy.observe(1).retained(), "Recent human hold did not expire after 60 seconds.");
		PhantomAssertions.assertEquals(1L, policy.oldestSoft(List.of(1L)), "Old unseen idle did not become reclaimable after full human grace.");
		facts.set(new org.l2jmobius.gameserver.phantoms.activity.PhantomMaterializationRetentionPolicy.Facts(true, true, false, false, false));
		policy.observe(1);
		clock.advanceMillis(20_000);
		policy.observe(1);
		facts.set(new org.l2jmobius.gameserver.phantoms.activity.PhantomMaterializationRetentionPolicy.Facts(true, false, false, false, false));
		clock.advanceMillis(40_001);
		PhantomAssertions.assertTrue(policy.observe(1).retained(), "Re-entry did not renew meaningful human contact.");
		facts.set(new org.l2jmobius.gameserver.phantoms.activity.PhantomMaterializationRetentionPolicy.Facts(true, false, false, false, true));
		clock.advanceMillis(60_000);
		PhantomAssertions.assertTrue(policy.observe(1).hard(), "Admitted native action was torn down after soft expiry.");
	}

	private void testSoftReclamation()
	{
		testSoftReclamation(false);
	}

	private void testSoftReclamation(boolean oldUnseen)
	{
		testSoftReclamation(oldUnseen, false);
	}

	private void testSoftReclamation(boolean oldUnseen, boolean returnDuringGrace)
	{
		final var clock = new ManualClock();
		final var delegate = new FakeMaterializationPort();
		final Set<Long> local = new java.util.HashSet<>(Set.of(1L));
		final var policy = new org.l2jmobius.gameserver.phantoms.activity.PhantomMaterializationRetentionPolicy(id -> new org.l2jmobius.gameserver.phantoms.activity.PhantomMaterializationRetentionPolicy.Facts(delegate.isMaterialized(id), local.contains(id), false, false, false), clock::nanoTime, 60_000);
		final var port = new PhantomReconcileFirstActivityPort(new PhantomActivityMaterializationPort()
		{
			@Override
			public TransitionOutcome materialize(long id)
			{
				return delegate.materialized.isEmpty() ? delegate.materialize(id) : TransitionOutcome.deferred();
			}
			@Override public TransitionOutcome dematerialize(long id) { return delegate.dematerialize(id); }
			@Override public TransitionOutcome retryCleanup(long id) { return delegate.retryCleanup(id); }
			@Override public boolean isMaterialized(long id) { return delegate.isMaterialized(id); }
			@Override public boolean hasLifecycleOwnership(long id) { return delegate.hasLifecycleOwnership(id); }
		});
		port.install(local::contains);
		port.installRetention(id -> policy.observe(id).retained());
		port.installSoftReclamation(id -> local.contains(id) && (delegate.materialized.size() == 1) ? policy.oldestSoft(List.copyOf(delegate.materialized.keySet())) : 0, id -> policy.observe(id).reclaimable());
		final var metrics = new PhantomMetrics();
		final var scheduler = new PhantomScheduler(2, 100, 2, PhantomSchedulerPolicy.productionDefaults(100), clock, (_pulse, _period) -> null, false, metrics, new PhantomDiagnosticTrace(false, 0, 0, metrics), port, PhantomActivityWorkSink.noop());
		scheduler.start();
		try
		{
			scheduler.register(1);
			scheduler.register(2);
			scheduler.submitSignal(1, signal("human.local", 1, PhantomActivityState.ACTIVE, 120_000));
			scheduler.pulse();
			policy.observe(1);
			local.remove(1L);
			if (oldUnseen) { clock.advanceMillis(61_000); }
			scheduler.submitSignal(2, signal("population.active", 1, PhantomActivityState.ACTIVE, 120_000));
			scheduler.pulse();
			scheduler.pulse();
			PhantomAssertions.assertTrue(delegate.isMaterialized(1), "Nonlocal admission refusal reclaimed a recent-human Player.");
			local.add(2L);
			if (!oldUnseen) { clock.advanceMillis(60_001); }
			scheduler.submitSignal(2, signal("human.local", 1, PhantomActivityState.ACTIVE, 120_000));
			scheduler.pulse();
			scheduler.pulse();
			clock.advanceMillis(1000);
			scheduler.pulse();
			PhantomAssertions.assertFalse(delegate.isMaterialized(1), "Soft hold starved new human materialization under capacity pressure.");
			PhantomAssertions.assertTrue(delegate.isMaterialized(2), "Capacity reclamation did not admit the new human-nearby Player.");
			PhantomAssertions.assertFalse(scheduler.find(1).orElseThrow().effectiveState().requiresMaterialization(), "Soft reclamation left scheduler falsely materialized.");
			PhantomAssertions.assertEquals(PhantomActivityState.ACTIVE, scheduler.find(2).orElseThrow().effectiveState(), "New human promotion did not publish canonical ownership.");
			if (returnDuringGrace)
			{
				policy.observe(2);
				local.remove(2L); local.add(1L);
				scheduler.submitSignal(1, signal("human.local", 2, PhantomActivityState.ACTIVE, 120_000));
				for (int pulse = 0; pulse < 4; pulse++) { scheduler.pulse(); }
				PhantomAssertions.assertFalse(delegate.isMaterialized(1), "A fresh signal bypassed soft reclamation hysteresis and ping-ponged the same slot.");
				PhantomAssertions.assertTrue(delegate.isMaterialized(2), "One grace period evicted both owners.");
				clock.advanceMillis(2_001);
				PhantomAssertions.assertTrue(delegate.isMaterialized(2), "Scheduler cooldown replaced the longer recent-human grace.");
				clock.advanceMillis(58_000);
				for (int pulse = 0; pulse < 3; pulse++) { scheduler.pulse(); }
				clock.advanceMillis(1_001); scheduler.pulse();
				PhantomAssertions.assertTrue(delegate.isMaterialized(1), "Soft cooldown permanently starved a new current-local request after its bounded grace.");
			}
		}
		finally { stop(scheduler); }
	}

	private void testProductionBackgroundDue()
	{
		final PhantomSchedulerPolicy policy = PhantomSchedulerPolicy.productionDefaults(10);
		final long firstInterval = policy.cadenceMillis(PhantomActivityState.BACKGROUND, 1, 1);
		PhantomAssertions.assertTrue((firstInterval >= 300_000) && (firstInterval <= 900_000), "Production BACKGROUND cadence is outside 5..15 minutes.");
		PhantomAssertions.assertEquals(firstInterval, policy.cadenceMillis(PhantomActivityState.BACKGROUND, 1, 1), "Production cadence is not deterministic.");
		PhantomAssertions.assertTrue(policy.cadenceMillis(PhantomActivityState.BACKGROUND, 2, 1) != firstInterval, "Profile identity did not spread due times.");
		final ManualClock clock = new ManualClock();
		final PhantomMetrics metrics = new PhantomMetrics();
		final List<PhantomActivityWorkItem> work = new ArrayList<>();
		final PhantomScheduler scheduler = new PhantomScheduler(1, 10, 1, policy, clock, (pulse, period) -> null, false, metrics, new PhantomDiagnosticTrace(false, 16, 1, metrics), PhantomActivityMaterializationPort.noop(), work::add);
		PhantomAssertions.assertTrue(scheduler.start(), "Production cadence fixture did not start.");
		scheduler.register(1);
		scheduler.submitSignal(1, signal("background", 1, PhantomActivityState.BACKGROUND, PhantomRelevanceSignal.MAXIMUM_TTL_MILLIS));
		scheduler.pulse();
		PhantomAssertions.assertEquals(0, work.size(), "First BACKGROUND work was delivered immediately.");
		PhantomAssertions.assertEquals(firstInterval * 1_000_000L, scheduler.find(1).orElseThrow().nextDueNanos(), "First BACKGROUND due differs from production policy.");
		clock.advanceMillis(firstInterval - 1);
		scheduler.pulse();
		PhantomAssertions.assertEquals(0, work.size(), "BACKGROUND work was delivered before due.");
		clock.advanceMillis(1);
		scheduler.pulse();
		PhantomAssertions.assertEquals(1, work.size(), "Due BACKGROUND work was not delivered.");
		PhantomAssertions.assertEquals(0, scheduler.snapshot().scheduledTaskCount(), "Manual scheduler created a per-profile future.");
		stop(scheduler);
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
		final Fixture repeated = fixture(2, 2);
		for (int i = 0; i < 5; i++) { repeated.port().materializeOutcomes.add(Outcome.TRANSIENT_BLOCK); }
		repeated.scheduler().register(1);
		repeated.scheduler().submitSignal(1, signal("interest", 1, PhantomActivityState.ACTIVE, 100));
		repeated.scheduler().pulse();
		int attempts = 1;
		for (long delay : new long[] {2, 4, 8, 8, 8})
		{
			repeated.scheduler().submitSignal(1, signal("human.local", attempts, PhantomActivityState.NEARBY_PERCEPTIBLE, 100));
			repeated.clock().advanceMillis(delay - 1);
			repeated.scheduler().pulse();
			PhantomAssertions.assertEquals(attempts, repeated.port().materializeCalls, "Fresh signal bypassed a genuine transition failure backoff.");
			repeated.clock().advanceMillis(1);
			repeated.scheduler().pulse();
			PhantomAssertions.assertEquals(++attempts, repeated.port().materializeCalls, "Bounded exponential retry deadline drifted.");
		}
		PhantomAssertions.assertEquals(PhantomActivityState.ACTIVE, repeated.scheduler().find(1).orElseThrow().effectiveState(), "Repeated genuine failure did not recover.");
		stop(repeated.scheduler());
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
