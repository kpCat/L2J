/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationCatalog;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyCatalog;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyService;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyState;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyState.*;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyStore.*;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationManager;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationStore.ManagedSnapshot;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialCatalog;
import org.l2jmobius.tests.phantoms.PhantomAssertions;
import org.l2jmobius.tests.phantoms.PhantomTestSuite;
import org.l2jmobius.tests.phantoms.PhantomTestContext;
import org.l2jmobius.tests.phantoms.PhantomTestRegistry;
import org.l2jmobius.tests.phantoms.PhantomPopulationTestDoubles;
import org.l2jmobius.tests.phantoms.PhantomPopulationEcologyGoal033Suite;
import org.l2jmobius.tests.phantoms.PhantomTopologyCoreSuite;

/** Coordinator regressions; native consumer/continuity cases share the guarded aggregate target. */
public final class PhantomM1RuntimeHandoffSuite implements PhantomTestSuite
{
	private static final Instant NOW = Instant.parse("2026-01-05T20:00:00Z");
	private PhantomPopulationCatalog _population;
	private PhantomPopulationEcologyCatalog _catalog;

	@Override
	public String id() { return "m1-runtime-handoff"; }

	@Override
	public void beforeAll(PhantomTestContext context)
	{
		_population = PhantomPopulationCatalog.load(context.moduleRoot().resolve("dist/game/data/phantoms/population/high-five-population-v1.xml"), ZoneOffset.UTC);
		_catalog = PhantomPopulationEcologyCatalog.load(context.moduleRoot().resolve("dist/game/data/phantoms/population/high-five-ecology-v1.xml"), _population, PhantomSocialCatalog.load(context.moduleRoot().resolve("dist/game/data/phantoms/social/high-five-social-v1.xml")));
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		if (System.getProperty("phantom.m1.native.focus", "").equals("releasedOwner031"))
		{
			registry.add("16-released-native-owner-resumes-background-before-minute-refresh", this::releasedNativeOwner031);
			registry.add("17-native-release-handoff-shares-budget-with-ordinary-backlog", this::nativeReleaseBacklog031);
			return;
		}
		registry.add("01-calendar-online-survives-readiness", this::calendarOnline);
		registry.add("02-io-stall-does-not-block-population-pulse", this::nonblockingPulse);
		registry.add("03-native-one-point-result-is-executable", _ -> singlePointPath());
		registry.add("04-real-coordinator-readiness-locality-and-stop", this::coordinatorHandoff);
		registry.add("05-silent-dispatch-loss-and-stale-worker", this::silentDispatchLoss);
		registry.add("06-demand-continues-without-population-pulses", this::demandDrivenPump);
		registry.add("07-restored-request-begin-rejection-is-typed", this::beginRejection);
		registry.add("08-monotonic-coalescing-lost-worker-and-wake-rejection", this::monotonicPumpFences);
		registry.add("09-partial-retry-counts-committed-intervals", this::partialRetryBudget);
		registry.add("10-bounded-reversible-resize-inventory", this::boundedResize);
		registry.add("11-retired-pending-is-paused-and-returned", this::retiredPending);
		registry.add("12-late-near-preparation-keeps-ordinary-budget", this::lateNear);
		registry.add("13-protected-resize-rechecks-without-exhaustion", this::protectedResize);
		registry.add("14-outer-save-failure-keeps-committed-budget", this::outerSaveBudget);
		registry.add("15-mixed-recoverable-history-keeps-demand-and-ordinary-live", this::mixedRecoverableHistory);
	}

	private void releasedNativeOwner031(PhantomTestContext context)
	{
		final var population = new PhantomPopulationTestDoubles.MemoryStore(_population.hash());
		final var store = new PhantomPopulationEcologyGoal033Suite.EcologyMemoryStore(null);
		final var history = new PhantomPopulationEcologyGoal033Suite.HistoricalMemoryPort();
		final var initial = state(population.seedReady(1, 1));
		final var begun = history.begin(1, initial.calendarCursorEpochMinute(), initial.calendarCursorEpochMinute() + 100, 1);
		store.insert(1, initial.beginRequest(begun.snapshot().state().requestId(), initial.calendarCursorEpochMinute() + 100));
		final var live = new java.util.concurrent.atomic.AtomicBoolean(true);
		final var clock = new PhantomPopulationTestDoubles.MutableClock(NOW);
		final var ecology = new PhantomPopulationEcologyService(_catalog, _population, store, history, _ -> live.get(), _ -> "", clock, ZoneOffset.UTC, Preset.LIVING, 0, 10, worker -> { worker.run(); return true; });
		final var manager = new PhantomPopulationManager(population, _population, null, new PhantomPopulationTestDoubles.Ownership(), clock, ZoneOffset.UTC, 1, 1, 4, 4, 1, 64);
		manager.installEcology(ecology); manager.start();
		try
		{
			ecology.holdStartupPopulationPlan(); ecology.onPopulationPulse(); ecology.startupPopulationPlanApplied();
			ecology.updateMaterializationDemand(java.util.List.of(new PhantomPopulationEcologyService.DemandFact(1, 1, true, 1, 1)), 1);
			ecology.onPopulationPulse();
			final long heldCursor = history.status(1).orElseThrow().state().cursorEpochMinute();
			ecology.updateMaterializationDemand(java.util.List.of(), 1);
			for (int pulse = 0; pulse < 4; pulse++) { ecology.onPopulationPulse(); }
			PhantomAssertions.assertEquals(heldCursor, history.status(1).orElseThrow().state().cursorEpochMinute(), "Withdrawn physical demand bypassed the still-live native owner.");
			live.set(false);
			ecology.updateMaterializationDemand(java.util.List.of(), 1);
			for (int pulse = 0; pulse < 4; pulse++) { ecology.onPopulationPulse(); }
			PhantomAssertions.assertTrue(history.status(1).orElseThrow().state().cursorEpochMinute() > heldCursor, "Released native owner kept background fenced until the next wall-clock minute.");
			PhantomAssertions.assertTrue(ecology.snapshot().maximumPulseProfiles() <= _catalog.limits().maximumProfilesPerPulse() && ecology.snapshot().maximumPulseIntervals() <= _catalog.limits().maximumIntervalsPerPulse(), "Owner release multiplied the shared work budget.");
		}
		finally { ecology.beginStop(); manager.beginStop(); manager.finishStop(); }
	}
	private void mixedRecoverableHistory(PhantomTestContext context)
	{
		final var population = new PhantomPopulationTestDoubles.MemoryStore(_population.hash());
		final var store = new PhantomPopulationEcologyGoal033Suite.EcologyMemoryStore(null);
		final var backing = new PhantomPopulationEcologyGoal033Suite.HistoricalMemoryPort();
		final var failures = new java.util.HashMap<Long, String>();
		final var attempted = new java.util.HashSet<Long>();
		final var history = new PhantomPopulationEcologyService.HistoricalPort()
		{
			@Override public Optional<org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundCatchupStore.Snapshot> status(long id)
			{
				return backing.status(id).map(snapshot -> failures.containsKey(id) ? new org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundCatchupStore.Snapshot(snapshot.state().failed(failures.get(id)), snapshot.rowVersion()) : snapshot);
			}
			@Override public org.l2jmobius.gameserver.phantoms.background.PhantomHistoricalBackgroundService.Result begin(long id, long from, long target, long seed) { return backing.begin(id, from, target, seed); }
			@Override public org.l2jmobius.gameserver.phantoms.background.PhantomHistoricalBackgroundService.Result advance(long id, int intervals, int minutes)
			{
				if (failures.containsKey(id) && attempted.add(id))
				{
					return org.l2jmobius.gameserver.phantoms.background.PhantomHistoricalBackgroundService.Result.rejected(org.l2jmobius.gameserver.phantoms.background.PhantomHistoricalBackgroundService.ResultStatusCode.REPLAN_REQUIRED, failures.get(id), status(id).orElseThrow());
				}
				failures.remove(id);
				return backing.advance(id, intervals, minutes);
			}
		};
		for (long id = 1; id <= 32; id++)
		{
			final var initial = state(population.seedReady(id, 1));
			final var begun = backing.begin(id, initial.calendarCursorEpochMinute(), initial.calendarCursorEpochMinute() + 100, id);
			store.insert(id, initial.beginRequest(begun.snapshot().state().requestId(), initial.calendarCursorEpochMinute() + 100));
			if (id <= 30) { failures.put(id, id <= 18 ? "model.object_cap_indivisible" : id <= 24 ? "catchup.authority.unsupported" : "planner.target_or_route.absent"); }
		}
		final var clock = new PhantomPopulationTestDoubles.MutableClock(NOW.plusSeconds(40 * 60));
		final var ecology = new PhantomPopulationEcologyService(_catalog, _population, store, history, _ -> false, _ -> "", clock, ZoneOffset.UTC, Preset.LIVING, 0, 10, worker -> { worker.run(); return true; });
		final var manager = new PhantomPopulationManager(population, _population, null, new PhantomPopulationTestDoubles.Ownership(), clock, ZoneOffset.UTC, 32, 4, 64, 4, 2, 64);
		manager.installEcology(ecology); manager.start();
		try
		{
			ecology.updateMaterializationDemand(java.util.List.of(new PhantomPopulationEcologyService.DemandFact(30, 1, true, 1, 1)), 4);
			for (int batch = 0; batch < 280; batch++) { ecology.onPopulationPulse(); }
			final var focused = backing.status(30).orElseThrow().state();
			PhantomAssertions.assertTrue(focused.cursorEpochMinute() > focused.fromEpochMinute(), "Recoverable demand candidate became terminal behind mixed historical failures.");
			final var ordinary = backing.status(31).orElseThrow().state();
			PhantomAssertions.assertTrue(ordinary.cursorEpochMinute() > ordinary.fromEpochMinute(), "Mixed recovery starved ordinary work.");
			PhantomAssertions.assertTrue(ecology.snapshot().maximumPulseProfiles() <= 4 && ecology.snapshot().maximumPulseIntervals() <= 16, "Mixed recovery multiplied the shared worker budget.");
		}
		finally { ecology.beginStop(); manager.beginStop(); manager.finishStop(); }
	}

	private void lateNear(PhantomTestContext context)
	{
		final var population = new PhantomPopulationTestDoubles.MemoryStore(_population.hash());
		final var store = new PhantomPopulationEcologyGoal033Suite.EcologyMemoryStore(null);
		final var history = new PhantomPopulationEcologyGoal033Suite.HistoricalMemoryPort();
		for (long id = 1; id <= 1280; id++)
		{
			final var initial = state(population.seedReady(id, 1));
			final var begun = history.begin(id, initial.calendarCursorEpochMinute(), initial.calendarCursorEpochMinute() + 100, id);
			store.insert(id, initial.beginRequest(begun.snapshot().state().requestId(), initial.calendarCursorEpochMinute() + 100));
		}
		final var clock = new PhantomPopulationTestDoubles.MutableClock(NOW.plusSeconds(40 * 60));
		final var ecology = new PhantomPopulationEcologyService(_catalog, _population, store, history, _ -> false, _ -> "", clock, ZoneOffset.UTC, Preset.LIVING, 0, 10, worker -> { worker.run(); return true; });
		final var manager = new PhantomPopulationManager(population, _population, null, new PhantomPopulationTestDoubles.Ownership(), clock, ZoneOffset.UTC, 1280, 64, 10000, 128, 2, 64);
		manager.installEcology(ecology); manager.start();
		try
		{
			ecology.holdStartupPopulationPlan();
			for (int pulse = 0; pulse < 320; pulse++) { ecology.onPopulationPulse(); }
			ecology.startupPopulationPlanApplied();
			final var facts = new java.util.ArrayList<PhantomPopulationEcologyService.DemandFact>();
			for (long id = 1; id <= 972; id++) { facts.add(new PhantomPopulationEcologyService.DemandFact(id, 1, false, 10000 + id, 1)); }
			ecology.updateMaterializationDemand(facts, 8);
			facts.add(new PhantomPopulationEcologyService.DemandFact(1280, 1, true, 1, 2));
			ecology.updateMaterializationDemand(facts, 8);
			for (int refresh = 0; refresh < 10; refresh++) { ecology.updateMaterializationDemand(facts, 8); ecology.requestMaterializationDue(1280); }
			ecology.onPopulationPulse();
			final var near = history.status(1280).orElseThrow().state();
			PhantomAssertions.assertEquals(12L, near.cursorEpochMinute() - near.fromEpochMinute(), "Late closest profile remained behind the physical demand queue.");
			final var ordinary = history.status(9).orElseThrow().state();
			PhantomAssertions.assertEquals(4L, ordinary.cursorEpochMinute() - ordinary.fromEpochMinute(), "Urgent demand starved oldest ordinary work.");
			PhantomAssertions.assertEquals(973, ecology.preparationSnapshot().physicalCount(), "Admission truncated physical truth.");
			PhantomAssertions.assertEquals(8, ecology.preparationSnapshot().admittedPreparationCount(), "Preparation exceeded eight slots.");
			facts.add(new PhantomPopulationEcologyService.DemandFact(1279, 1, true, 2, 3));
			ecology.updateMaterializationDemand(facts, 8);
			for (int batch = 0; batch < 8; batch++) { ecology.updateMaterializationDemand(facts, 8); ecology.onPopulationPulse(); }
			final var peer = history.status(1279).orElseThrow().state();
			PhantomAssertions.assertTrue(peer.cursorEpochMinute() - peer.fromEpochMinute() >= 12, "Eight focus batches starved an admitted same-priority peer.");
			ecology.updateMaterializationDemand(facts, 0);
			PhantomAssertions.assertEquals(0, ecology.preparationSnapshot().admittedPreparationCount(), "Full hard capacity retained cold preparation slots.");
			PhantomAssertions.assertEquals("ecology.preparation_capacity", ecology.requestMaterializationDue(1279).reason(), "Gate poll bypassed preparation capacity.");
			ecology.updateMaterializationDemand(java.util.List.of(), 8);
			PhantomAssertions.assertEquals(0, ecology.preparationSnapshot().physicalCount(), "Lost demand retained priority facts.");
			PhantomAssertions.assertTrue(ecology.snapshot().maximumPulseProfiles() <= 4 && ecology.snapshot().maximumPulseIntervals() <= 16, "Focus multiplied the shared work budget.");
		}
		finally { ecology.beginStop(); manager.beginStop(); manager.finishStop(); }
	}

	private void outerSaveBudget(PhantomTestContext context)
	{
		final var population = new PhantomPopulationTestDoubles.MemoryStore(_population.hash());
		final var backing = new PhantomPopulationEcologyGoal033Suite.EcologyMemoryStore(null);
		final var history = new PhantomPopulationEcologyGoal033Suite.HistoricalMemoryPort();
		for (long id = 1; id <= 4; id++)
		{
			final var initial = state(population.seedReady(id, 1));
			final long end = initial.calendarCursorEpochMinute() + (id == 1 ? 12 : 100);
			final var begun = history.begin(id, initial.calendarCursorEpochMinute(), end, id);
			backing.insert(id, initial.beginRequest(begun.snapshot().state().requestId(), end));
		}
		final var failOnce = new java.util.concurrent.atomic.AtomicBoolean(true);
		final var persistence = new PhantomPopulationEcologyService.PersistencePort()
		{
			@Override public Optional<org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyStore.StoredState> load(long id) { return backing.load(id); }
			@Override public org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyStore.StoredState insert(long id, PhantomPopulationEcologyState state) { return backing.insert(id, state); }
			@Override public org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyStore.StoredState save(long id, org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyStore.StoredState expected, PhantomPopulationEcologyState state) { if (id == 1 && !state.requestPending() && failOnce.getAndSet(false)) { throw new IllegalStateException("fixture.outer_save_failure"); } return backing.save(id, expected, state); }
			@Override public org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyStore.ArchivedResult archive(ManagedSnapshot value, org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyStore.StoredState state, long generation, long now) { throw new AssertionError("No archive in budget regression."); }
		};
		final var clock = new PhantomPopulationTestDoubles.MutableClock(NOW);
		final var ecology = new PhantomPopulationEcologyService(_catalog, _population, persistence, history, _ -> false, _ -> "", clock, ZoneOffset.UTC, Preset.LIVING, 0, 10, worker -> { worker.run(); return true; });
		final var manager = new PhantomPopulationManager(population, _population, null, new PhantomPopulationTestDoubles.Ownership(), clock, ZoneOffset.UTC, 4, 4, 16, 4, 2, 64);
		manager.installEcology(ecology); manager.start();
		try
		{
			ecology.holdStartupPopulationPlan(); ecology.onPopulationPulse(); ecology.startupPopulationPlanApplied();
			ecology.updateMaterializationDemand(java.util.List.of(new PhantomPopulationEcologyService.DemandFact(1, 1, true, 1, 1), new PhantomPopulationEcologyService.DemandFact(2, 1, true, 2, 2)), 2);
			ecology.onPopulationPulse();
			long actual = 0;
			for (long id = 1; id <= 4; id++) { final var request = history.status(id).orElseThrow().state(); actual += request.cursorEpochMinute() - request.fromEpochMinute(); }
			PhantomAssertions.assertEquals(16L, actual, "Outer save failure granted a second interval budget after durable focus commit.");
			PhantomAssertions.assertEquals(16, ecology.snapshot().lastPulseIntervals(), "Outer save failure lost committed interval accounting.");
		}
		finally { ecology.beginStop(); manager.beginStop(); manager.finishStop(); }
	}

	private void protectedResize(PhantomTestContext context)
	{
		final var population = new PhantomPopulationTestDoubles.MemoryStore(_population.hash());
		for (long id = 1; id <= 3; id++) { population.seedReady(id, 1); }
		final var protectedOwner = new java.util.concurrent.atomic.AtomicBoolean(true);
		final var clock = new PhantomPopulationTestDoubles.MutableClock(NOW);
		final var ownership = new PhantomPopulationTestDoubles.Ownership();
		final var manager = new PhantomPopulationManager(population, _population, null, ownership, clock, ZoneOffset.UTC, 1, 1, 16, 4, 2, 64);
		manager.installRetirementProtection(_ -> protectedOwner.get()); manager.start();
		try
		{
			for (int pulse = 0; pulse < 300; pulse++) { manager.onPulse(); }
			PhantomAssertions.assertEquals(3, manager.snapshot().ready(), "Resize removed a protected owner.");
			PhantomAssertions.assertEquals("resize_pending", manager.resizeSnapshot().phase(), "Protected excess was reported steady.");
			for (int wait = 0; wait < 100; wait++) { ownership.unregisterOutcomes(PhantomScheduler.UnregisterStatus.PENDING); }
			protectedOwner.set(false); clock.set(NOW.plusSeconds(60));
			for (int pulse = 0; pulse < 300; pulse++) { manager.onPulse(); }
			PhantomAssertions.assertEquals(org.l2jmobius.gameserver.phantoms.population.PhantomPopulationState.State.RETIRE_REQUESTED, manager.find(3).orElseThrow().state().state(), "Native pending boundary exhausted retirement into INCONSISTENT.");
			for (int pulse = 0; pulse < 600; pulse++) { manager.onPulse(); }
			PhantomAssertions.assertEquals(1, manager.snapshot().ready(), "Released owner required a new external resize request.");
			PhantomAssertions.assertEquals(2, manager.snapshot().retired(), "Guard release lost the reserve.");
		}
		finally { manager.beginStop(); manager.finishStop(); }
	}

	private void boundedResize(PhantomTestContext context)
	{
		final var population = new PhantomPopulationTestDoubles.MemoryStore(_population.hash());
		final var identities = new java.util.HashMap<Long, Integer>();
		for (long id = 1; id <= 10000; id++) { identities.put(id, population.seedReady(id, 1).profile().characterObjectId()); }
		population.resetWrites();
		var manager = new PhantomPopulationManager(population, _population, null, new PhantomPopulationTestDoubles.Ownership(), new PhantomPopulationTestDoubles.MutableClock(NOW), ZoneOffset.UTC, 1280, 64, 10000, 128, 2, 64);
		manager.start();
		try
		{
			PhantomAssertions.assertEquals(0L, population.writes(), "Resize callback performed thousands of synchronous retirement writes.");
			for (int pulse = 0; pulse < 2000 && manager.snapshot().retired() != 8720; pulse++) { manager.onPulse(); }
			PhantomAssertions.assertEquals(1280, manager.snapshot().ready(), "Resize did not retain the target participants.");
			PhantomAssertions.assertEquals(8720, manager.snapshot().retired(), "Resize did not preserve retired reserve.");
			manager.beginStop(); manager.finishStop(); population.resetWrites();
			manager = new PhantomPopulationManager(population, _population, null, new PhantomPopulationTestDoubles.Ownership(), new PhantomPopulationTestDoubles.MutableClock(NOW), ZoneOffset.UTC, 1280, 64, 10000, 128, 2, 64);
			manager.start();
			for (int pulse = 0; pulse < 40; pulse++) { manager.onPulse(); }
			PhantomAssertions.assertEquals(0L, population.writes(), "Restart repeated the retirement writes.");
			PhantomAssertions.assertEquals(1280, manager.snapshot().ready(), "Restart returned reserve without target change.");
			manager.reconcileTarget(3000, 64); manager.reconcileTarget(3000, 64);
			for (int pulse = 0; pulse < 2000 && manager.snapshot().ready() != 3000; pulse++) { manager.onPulse(); }
			PhantomAssertions.assertEquals(3000, manager.snapshot().ready(), "Repeated return plan replaced or duplicated identities.");
			PhantomAssertions.assertEquals(10000, population.size(), "Resize changed persistent inventory.");
			for (var entry : identities.entrySet()) { PhantomAssertions.assertEquals(entry.getValue(), population.reload(entry.getKey()).profile().characterObjectId(), "Resize replaced character identity."); }
			context.record("m1.resize", "10000→1280→restart1280→3000 metadata; no recreation or repeated retirement writes");
		}
		finally { manager.beginStop(); manager.finishStop(); }
	}

	private void retiredPending(PhantomTestContext context)
	{
		final var population = new PhantomPopulationTestDoubles.MemoryStore(_population.hash());
		final var profile = population.seedReady(1, 1);
		final var store = new PhantomPopulationEcologyGoal033Suite.EcologyMemoryStore(null);
		final var history = new PhantomPopulationEcologyGoal033Suite.HistoricalMemoryPort();
		final var initial = state(profile);
		final var begun = history.begin(1, initial.calendarCursorEpochMinute(), initial.calendarCursorEpochMinute() + 13, 1);
		final var pending = initial.beginRequest(begun.snapshot().state().requestId(), initial.calendarCursorEpochMinute() + 13);
		store.insert(1, pending);
		final var workers = new java.util.ArrayDeque<Runnable>();
		final var clock = new PhantomPopulationTestDoubles.MutableClock(NOW);
		final var ecology = new PhantomPopulationEcologyService(_catalog, _population, store, history, _ -> false, _ -> "", clock, ZoneOffset.UTC, Preset.LIVING, 0, 10, worker -> { workers.add(worker); return true; });
		final var manager = new PhantomPopulationManager(population, _population, null, new PhantomPopulationTestDoubles.Ownership(), clock, ZoneOffset.UTC, 0, 0, 16, 4, 2, 64);
		manager.installEcology(ecology); manager.start();
		try
		{
			for (int pulse = 0; pulse < 20; pulse++) { manager.onPulse(); while (!workers.isEmpty()) { workers.remove().run(); } }
			PhantomAssertions.assertEquals(1, manager.snapshot().retired(), "Saved pending blocked ordinary retirement.");
			PhantomAssertions.assertEquals(pending, store.load(1).orElseThrow().state(), "Retirement advanced or cleared saved pending.");
			PhantomAssertions.assertEquals(begun.snapshot(), history.status(1).orElseThrow(), "Retirement replayed saved history.");
			PhantomAssertions.assertFalse(ecology.dueSnapshot(1).queued(), "Retired MANAGED profile remained runnable.");
			manager.reconcileTarget(1, 0);
			for (int pulse = 0; pulse < 100; pulse++) { manager.onPulse(); while (!workers.isEmpty()) { workers.remove().run(); } }
			PhantomAssertions.assertEquals(profile.profile().characterObjectId(), manager.find(1).orElseThrow().profile().characterObjectId(), "Return replaced saved identity.");
			PhantomAssertions.assertTrue(ecology.dueSnapshot(1).complete(), "Returned pending did not resume canonically.");
		}
		finally { ecology.beginStop(); manager.beginStop(); manager.finishStop(); }
	}

	private void monotonicPumpFences(PhantomTestContext context)
	{
		for (boolean reject : java.util.List.of(false, true))
		{
			final var population = new PhantomPopulationTestDoubles.MemoryStore(_population.hash());
			final var profile = population.seedReady(1, 1);
			final var store = new PhantomPopulationEcologyGoal033Suite.EcologyMemoryStore(null); store.insert(1, state(profile));
			final var workers = new java.util.ArrayDeque<Runnable>();
			final var wakes = new java.util.ArrayDeque<Runnable>();
			final var now = new java.util.concurrent.atomic.AtomicLong(1);
			final var clock = new PhantomPopulationTestDoubles.MutableClock(NOW);
			final var ecology = new PhantomPopulationEcologyService(_catalog, _population, store, new PhantomPopulationEcologyGoal033Suite.HistoricalMemoryPort(), _ -> false, _ -> "", clock, ZoneOffset.UTC, Preset.LIVING, 0, 10, worker -> { workers.add(worker); return true; });
			ecology.installDemandPump(100, now::get, (wake, delay) -> { if (reject) { return null; } wakes.add(wake); return () -> {}; });
			final var manager = new PhantomPopulationManager(population, _population, null, new PhantomPopulationTestDoubles.Ownership(), clock, ZoneOffset.UTC, 1, 1, 16, 4, 2, 64);
			manager.installEcology(ecology); manager.start();
			try
			{
				for (int i = 0; i < 100; i++) { ecology.requestMaterializationDue(1); ecology.onPopulationPulse(); }
				if (reject) { PhantomAssertions.assertEquals("ecology.wake_rejected", ecology.dueSnapshot(1).reason(), "Rejected wake remained anonymous pending."); }
				else
				{
					PhantomAssertions.assertEquals(1, wakes.size(), "Demand burst created multiple service wakes.");
					wakes.remove().run();
					PhantomAssertions.assertEquals(1, workers.size(), "One wake did not dispatch one worker.");
					for (int i = 0; i < 100; i++) { ecology.onPopulationPulse(); }
					PhantomAssertions.assertEquals(1, workers.size(), "Population pulses multiplied the worker budget.");
					now.addAndGet(25600); wakes.remove().run();
					PhantomAssertions.assertEquals(2, workers.size(), "Monotonic deadline did not replace the lost unstarted worker.");
					workers.remove().run();
					PhantomAssertions.assertFalse(ecology.dueSnapshot(1).initialCatchupComplete(), "Late generation mutated canonical state.");
				}
			}
			finally { ecology.beginStop(); while (!workers.isEmpty()) { workers.remove().run(); } while (!wakes.isEmpty()) { wakes.remove().run(); } PhantomAssertions.assertTrue(ecology.finishStop(), "Stopped late wake retained ownership."); manager.beginStop(); manager.finishStop(); }
		}
	}

	private void partialRetryBudget(PhantomTestContext context)
	{
		final var population = new PhantomPopulationTestDoubles.MemoryStore(_population.hash());
		final var profile = population.seedReady(1, 1);
		final var store = new PhantomPopulationEcologyGoal033Suite.EcologyMemoryStore(null);
		final var backing = new PhantomPopulationEcologyGoal033Suite.HistoricalMemoryPort();
		final var initial = state(profile);
		final var begun = backing.begin(1, initial.calendarCursorEpochMinute(), initial.calendarCursorEpochMinute() + 13, 1);
		store.insert(1, initial.beginRequest(begun.snapshot().state().requestId(), initial.calendarCursorEpochMinute() + 13));
		final var history = new PhantomPopulationEcologyService.HistoricalPort()
		{
			@Override public Optional<org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundCatchupStore.Snapshot> status(long id) { return backing.status(id); }
			@Override public org.l2jmobius.gameserver.phantoms.background.PhantomHistoricalBackgroundService.Result begin(long id, long from, long to, long seed) { throw new AssertionError("Matching request was restarted."); }
			@Override public org.l2jmobius.gameserver.phantoms.background.PhantomHistoricalBackgroundService.Result advance(long id, int intervals, int minutes)
			{
				for (int i = 0; (i < Math.min(intervals, minutes)) && (backing.status(id).orElseThrow().state().status() != org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundCatchupState.Status.COMPLETE); i++) { backing.advance(id, 1, 1); }
				return org.l2jmobius.gameserver.phantoms.background.PhantomHistoricalBackgroundService.Result.rejected(org.l2jmobius.gameserver.phantoms.background.PhantomHistoricalBackgroundService.ResultStatusCode.RETRY, "transaction.item_busy", backing.status(id).orElseThrow());
			}
		};
		final var workers = new java.util.ArrayDeque<Runnable>();
		final var clock = new PhantomPopulationTestDoubles.MutableClock(NOW.minusSeconds(47 * 60));
		final var ecology = new PhantomPopulationEcologyService(_catalog, _population, store, history, _ -> false, _ -> "", clock, ZoneOffset.UTC, Preset.LIVING, 0, 10, worker -> { workers.add(worker); return true; });
		final var manager = new PhantomPopulationManager(population, _population, null, new PhantomPopulationTestDoubles.Ownership(), clock, ZoneOffset.UTC, 1, 1, 16, 4, 2, 64);
		manager.installEcology(ecology); manager.start(); manager.onPulse(); workers.remove().run();
		PhantomAssertions.assertEquals(0, ecology.snapshot().lastPulseIntervals(), "Metadata setup advanced partial retry history.");
		PhantomAssertions.assertEquals(0L, backing.status(1).orElseThrow().state().intervalOrdinal(), "Metadata setup consumed partial retry intervals.");
		manager.onPulse(); workers.remove().run();
		try { PhantomAssertions.assertEquals(13, ecology.snapshot().lastPulseIntervals(), "Committed partial retry was omitted from the shared interval budget."); PhantomAssertions.assertEquals(13L, backing.status(1).orElseThrow().state().intervalOrdinal(), "Partial retry replayed history."); }
		finally { ecology.beginStop(); manager.beginStop(); manager.finishStop(); }
	}

	private void beginRejection(PhantomTestContext context)
	{
		final var population = new PhantomPopulationTestDoubles.MemoryStore(_population.hash());
		final var profile = population.seedReady(1, 1);
		final var store = new PhantomPopulationEcologyGoal033Suite.EcologyMemoryStore(null);
		final var initial = state(profile);
		store.insert(1, initial.beginRequest("f".repeat(64), initial.calendarCursorEpochMinute() + 13));
		final var history = new PhantomPopulationEcologyService.HistoricalPort()
		{
			@Override public Optional<org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundCatchupStore.Snapshot> status(long id) { return Optional.empty(); }
			@Override public org.l2jmobius.gameserver.phantoms.background.PhantomHistoricalBackgroundService.Result begin(long id, long from, long to, long seed) { return org.l2jmobius.gameserver.phantoms.background.PhantomHistoricalBackgroundService.Result.rejected(org.l2jmobius.gameserver.phantoms.background.PhantomHistoricalBackgroundService.ResultStatusCode.CONFLICT, "catchup.claim.stale", null); }
			@Override public org.l2jmobius.gameserver.phantoms.background.PhantomHistoricalBackgroundService.Result advance(long id, int intervals, int minutes) { throw new AssertionError("Rejected begin crossed historical advance."); }
		};
		final var workers = new java.util.ArrayDeque<Runnable>();
		final var clock = new PhantomPopulationTestDoubles.MutableClock(NOW);
		final var ecology = new PhantomPopulationEcologyService(_catalog, _population, store, history, _ -> false, _ -> "", clock, ZoneOffset.UTC, Preset.LIVING, 0, 10, worker -> { workers.add(worker); return true; });
		final var manager = new PhantomPopulationManager(population, _population, null, new PhantomPopulationTestDoubles.Ownership(), clock, ZoneOffset.UTC, 1, 1, 16, 4, 2, 64);
		manager.installEcology(ecology); manager.start(); manager.onPulse(); workers.remove().run();
		PhantomAssertions.assertEquals(0, ecology.snapshot().lastPulseIntervals(), "Metadata setup advanced rejected request history.");
		manager.onPulse(); workers.remove().run();
		try { PhantomAssertions.assertEquals("catchup.claim.stale", ecology.dueSnapshot(1).reason(), "Rejected restored begin was hidden behind commit_pending."); }
		finally { ecology.beginStop(); manager.beginStop(); manager.finishStop(); }
	}

	private void demandDrivenPump(PhantomTestContext context) throws Exception
	{
		final var population = new PhantomPopulationTestDoubles.MemoryStore(_population.hash());
		final var profile = population.seedReady(1, 1);
		final var store = new PhantomPopulationEcologyGoal033Suite.EcologyMemoryStore(null);
		store.insert(1, state(profile));
		final var workers = new java.util.concurrent.ConcurrentLinkedQueue<Runnable>();
		final var clock = new PhantomPopulationTestDoubles.MutableClock(NOW);
		final var ecology = new PhantomPopulationEcologyService(_catalog, _population, store, new PhantomPopulationEcologyGoal033Suite.HistoricalMemoryPort(), _ -> false, _ -> "", clock, ZoneOffset.UTC, Preset.LIVING, 0, 10, worker -> { workers.add(worker); return true; });
		final var timer = Executors.newSingleThreadScheduledExecutor();
		ecology.installDemandPump(100, () -> System.nanoTime() / 1_000_000L, (wake, delay) -> { final var future = timer.schedule(wake, delay, TimeUnit.MILLISECONDS); return () -> future.cancel(false); });
		final var manager = new PhantomPopulationManager(population, _population, null, new PhantomPopulationTestDoubles.Ownership(), clock, ZoneOffset.UTC, 1, 1, 16, 4, 2, 64);
		manager.installEcology(ecology);
		manager.start();
		try
		{
			manager.onPulse();
			final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
			while (workers.isEmpty() && (System.nanoTime() < deadline)) { Thread.sleep(5); }
			workers.remove().run();
			Thread.sleep(200);
			PhantomAssertions.assertFalse(workers.isEmpty(), "Incomplete readiness has neither a worker nor a self-continuation wake.");
		}
		finally { ecology.beginStop(); manager.beginStop(); while (!workers.isEmpty()) { workers.remove().run(); } manager.finishStop(); timer.shutdownNow(); }
	}

	private void silentDispatchLoss(PhantomTestContext context)
	{
		final var population = new PhantomPopulationTestDoubles.MemoryStore(_population.hash());
		final var profile = population.seedReady(1, 1);
		final var store = new PhantomPopulationEcologyGoal033Suite.EcologyMemoryStore(null);
		store.insert(1, state(profile));
		final var workers = new java.util.ArrayDeque<Runnable>();
		final var ecology = new PhantomPopulationEcologyService(_catalog, _population, store, new PhantomPopulationEcologyGoal033Suite.HistoricalMemoryPort(), _ -> false, _ -> "", new PhantomPopulationTestDoubles.MutableClock(NOW), ZoneOffset.UTC, Preset.LIVING, 0, 10, worker -> { workers.add(worker); return true; });
		final var manager = new PhantomPopulationManager(population, _population, null, new PhantomPopulationTestDoubles.Ownership(), new PhantomPopulationTestDoubles.MutableClock(NOW), ZoneOffset.UTC, 1, 1, 16, 4, 2, 64);
		manager.installEcology(ecology);
		manager.start();
		manager.onPulse();
		for (int i = 0; i < 256; i++) { ecology.onPopulationPulse(); }
		PhantomAssertions.assertEquals(2, workers.size(), "Silent submission loss stranded the batch forever.");
		workers.removeFirst().run();
		PhantomAssertions.assertFalse(ecology.dueSnapshot(1).initialCatchupComplete(), "Stale generation acquired canonical work.");
		ecology.beginStop();
		PhantomAssertions.assertTrue(ecology.finishStop(), "Unstarted claim stranded shutdown.");
		workers.removeFirst().run();
		PhantomAssertions.assertFalse(ecology.dueSnapshot(1).initialCatchupComplete(), "Stopped queued generation mutated state.");
		manager.beginStop(); manager.finishStop();
	}

	private PhantomPopulationEcologyState state(ManagedSnapshot population)
	{
		final long minute = NOW.toEpochMilli() / 60_000 - 60;
		return new PhantomPopulationEcologyState(_catalog.hash(), Preset.LIVING, 1, 1, minute, minute, minute, minute, Pace.OUTLIER, 10_000, _catalog.limits().productiveBlockMinutes(), Personality.BALANCED, Map.of(1, 0, 2, 0, 3, 0, 4, 0, 5, 0, 6, 0), population.state().scheduleTemplate(), Disposition.MANAGED, minute + 10_000, 0, "", 0, 0, 0, "");
	}

	private PhantomPopulationManager manager(PhantomPopulationTestDoubles.MemoryStore population, PhantomPopulationEcologyService.PersistencePort store, PhantomPopulationEcologyService.Dispatcher dispatcher)
	{
		final var clock = new PhantomPopulationTestDoubles.MutableClock(NOW);
		final var ecology = new PhantomPopulationEcologyService(_catalog, _population, store, new PhantomPopulationEcologyGoal033Suite.HistoricalMemoryPort(), _ -> false, _ -> "", clock, ZoneOffset.UTC, Preset.LIVING, 0, 10, dispatcher);
		final var manager = new PhantomPopulationManager(population, _population, null, new PhantomPopulationTestDoubles.Ownership(), clock, ZoneOffset.UTC, 1, 1, 16, 4, 2, 64);
		manager.installEcology(ecology);
		PhantomAssertions.assertTrue(manager.start(), "Population manager failed to start.");
		return manager;
	}

	private void nativeReleaseBacklog031(PhantomTestContext context)
	{
		final int count = _catalog.limits().maximumProfilesPerPulse() + 16;
		final var population = new PhantomPopulationTestDoubles.MemoryStore(_population.hash());
		final var store = new PhantomPopulationEcologyGoal033Suite.EcologyMemoryStore(null);
		final var history = new PhantomPopulationEcologyGoal033Suite.HistoricalMemoryPort();
		for (long id = 1; id <= count; id++)
		{
			final var initial = state(population.seedReady(id, Math.toIntExact(id)));
			final var begun = history.begin(id, initial.calendarCursorEpochMinute(), initial.calendarCursorEpochMinute() + 100, id);
			store.insert(id, initial.beginRequest(begun.snapshot().state().requestId(), initial.calendarCursorEpochMinute() + 100));
		}
		final var live = new java.util.concurrent.atomic.AtomicBoolean(true);
		final var clock = new PhantomPopulationTestDoubles.MutableClock(NOW);
		final var ecology = new PhantomPopulationEcologyService(_catalog, _population, store, history, id -> id == count && live.get(), _ -> "", clock, ZoneOffset.UTC, Preset.LIVING, 0, 10, worker -> { worker.run(); return true; });
		final var manager = new PhantomPopulationManager(population, _population, null, new PhantomPopulationTestDoubles.Ownership(), clock, ZoneOffset.UTC, count, 0, count, 4, 1, 64);
		manager.installEcology(ecology); manager.start();
		try
		{
			ecology.holdStartupPopulationPlan();
			for (int pulse = 0; pulse < count; pulse++) { ecology.onPopulationPulse(); }
			ecology.startupPopulationPlanApplied();
			ecology.updateMaterializationDemand(java.util.List.of(new PhantomPopulationEcologyService.DemandFact(count, 1, true, 1, 1)), 1);
			ecology.onPopulationPulse();
			final long held = history.status(count).orElseThrow().state().cursorEpochMinute();
			final long ordinary = java.util.stream.LongStream.range(1, count).map(id -> history.status(id).orElseThrow().state().cursorEpochMinute()).sum();
			ecology.updateMaterializationDemand(java.util.List.of(), 1);
			live.set(false); ecology.updateMaterializationDemand(java.util.List.of(), 1);
			for (int pulse = 0; pulse < 2; pulse++) { ecology.onPopulationPulse(); }
			PhantomAssertions.assertTrue(history.status(count).orElseThrow().state().cursorEpochMinute() > held, "Released native handoff waited behind the ordinary backlog.");
			PhantomAssertions.assertTrue(java.util.stream.LongStream.range(1, count).map(id -> history.status(id).orElseThrow().state().cursorEpochMinute()).sum() > ordinary, "Native handoff starved ordinary work.");
			PhantomAssertions.assertTrue(ecology.snapshot().maximumPulseProfiles() <= _catalog.limits().maximumProfilesPerPulse() && ecology.snapshot().maximumPulseIntervals() <= _catalog.limits().maximumIntervalsPerPulse(), "Native handoff multiplied shared budgets.");
		}
		finally { ecology.beginStop(); manager.beginStop(); manager.finishStop(); }
	}
	private void calendarOnline(PhantomTestContext context)
	{
		final var population = new PhantomPopulationTestDoubles.MemoryStore(_population.hash());
		final var profile = population.seedReady(1, 1);
		final var store = new PhantomPopulationEcologyGoal033Suite.EcologyMemoryStore(null);
		store.insert(1, state(profile));
		final var workers = new java.util.ArrayDeque<Runnable>();
		final var manager = manager(population, store, worker -> { workers.add(worker); return true; });
		try
		{
			manager.onPulse();
			PhantomAssertions.assertTrue(manager.presence().isOnline(1), "Calendar-online profile became OFFLINE solely because ecology was pending.");
			PhantomAssertions.assertFalse(manager.admissionProfile(1).orElseThrow().eligible(), "Uncommitted ecology admitted normal activity.");
		}
		finally { while (!workers.isEmpty()) { workers.removeFirst().run(); } manager.beginStop(); manager.finishStop(); }
	}

	private void nonblockingPulse(PhantomTestContext context) throws Exception
	{
		final var population = new PhantomPopulationTestDoubles.MemoryStore(_population.hash());
		final var profile = population.seedReady(1, 1);
		final var entered = new CountDownLatch(1);
		final var release = new CountDownLatch(1);
		final var backing = new PhantomPopulationEcologyGoal033Suite.EcologyMemoryStore(null);
		backing.insert(1, state(profile));
		final var store = new PhantomPopulationEcologyService.PersistencePort()
		{
			@Override public Optional<StoredState> load(long id)
			{
				entered.countDown();
				try { if (!release.await(5, TimeUnit.SECONDS)) { throw new IllegalStateException("fixture.io_timeout"); } }
				catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
				return backing.load(id);
			}
			@Override public StoredState insert(long id, PhantomPopulationEcologyState state) { return backing.insert(id, state); }
			@Override public StoredState save(long id, StoredState expected, PhantomPopulationEcologyState state) { return backing.save(id, expected, state); }
			@Override public ArchivedResult archive(ManagedSnapshot value, StoredState state, long generation, long now) { return backing.archive(value, state, generation, now); }
		};
		try (var executor = Executors.newSingleThreadExecutor())
		{
			final var manager = manager(population, store, worker -> { executor.submit(worker); return true; });
			final var pulse = java.util.concurrent.CompletableFuture.runAsync(manager::onPulse);
			try
			{
				PhantomAssertions.assertTrue(entered.await(2, TimeUnit.SECONDS), "Fixture did not enter persistence.");
				pulse.get(200, TimeUnit.MILLISECONDS);
				java.util.concurrent.CompletableFuture.runAsync(manager::onPulse).get(200, TimeUnit.MILLISECONDS);
				PhantomAssertions.assertTrue(manager.presence().isOnline(1), "Pending I/O erased calendar presence.");
			}
			finally { release.countDown(); pulse.get(3, TimeUnit.SECONDS); manager.beginStop(); manager.finishStop(); }
		}
	}

	private void singlePointPath()
	{
		final var computed = new java.util.concurrent.atomic.AtomicBoolean();
		final var backend = new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationBackend()
		{
			@Override public CapabilitySnapshot capability(org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint from, org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint to) { return new CapabilitySnapshot(org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationCapability.GEODATA_PATHFINDING, 1); }
			@Override public boolean canMoveDirect(org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint from, org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint to) { return computed.get(); }
			@Override public java.util.List<org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint> findPath(org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationRequest request, org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationCancellationToken cancellation) { computed.set(true); return java.util.List.of(request.destination()); }
		};
		final var navigation = new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationService(org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPolicy.productionDefaults(), backend, worker -> { worker.run(); return true; }, () -> 1, new org.l2jmobius.gameserver.phantoms.PhantomMetrics());
		navigation.start();
		try
		{
			final var result = navigation.submit(new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationRequest(1, new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint(0, 0, 0, 0), new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint(100, 0, 0, 0), 1, 1_000_000_000L, 100_000)).immediateResult();
			PhantomAssertions.assertEquals(org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationResult.Status.PATH_FOUND, result.status(), "One native endpoint was rejected solely by path list length.");
		}
		finally { navigation.beginStop(); navigation.finishStop(); }
	}

	private void coordinatorHandoff(PhantomTestContext context) throws Exception
	{
		for (String boundary : java.util.List.of("ready", "offline", "anchor", "stop")) { handoffCase(boundary); }
		context.record("m1.coordinator", "same-profile commit/materialize, ready peer during I/O, offline, anchor, stop fences");
	}

	private void handoffCase(String boundary) throws Exception
	{
		final var wall = new PhantomPopulationTestDoubles.MutableClock(NOW);
		final var nanos = new java.util.concurrent.atomic.AtomicLong(1);
		final var population = new PhantomPopulationTestDoubles.MemoryStore(_population.hash());
		final var first = population.seedReady(1, 1);
		final var second = population.seedReady(2, 1);
		final var backing = new PhantomPopulationEcologyGoal033Suite.EcologyMemoryStore(null);
		backing.insert(1, state(first));
		backing.insert(2, state(second).advanceCalendar(NOW.toEpochMilli() / 60_000));
		final var entered = new CountDownLatch(1);
		final var release = new CountDownLatch(1);
		final var stored = new PhantomPopulationEcologyService.PersistencePort()
		{
			@Override public Optional<StoredState> load(long id) { return backing.load(id); }
			@Override public StoredState insert(long id, PhantomPopulationEcologyState state) { return backing.insert(id, state); }
			@Override public StoredState save(long id, StoredState expected, PhantomPopulationEcologyState replacement)
			{
				if (id == 1)
				{
					entered.countDown();
					try { if (!release.await(5, TimeUnit.SECONDS)) { throw new IllegalStateException("fixture.io_timeout"); } }
					catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
				}
				return backing.save(id, expected, replacement);
			}
			@Override public ArchivedResult archive(ManagedSnapshot value, StoredState state, long generation, long now) { return backing.archive(value, state, generation, now); }
		};
		final var identities = new java.util.concurrent.ConcurrentHashMap<Long, Integer>();
		final var births = new java.util.concurrent.ConcurrentHashMap<Long, Integer>();
		final var delegate = new org.l2jmobius.gameserver.phantoms.activity.PhantomActivityMaterializationPort()
		{
			@Override public TransitionOutcome materialize(long id) { identities.put(id, Math.toIntExact(10_000 + id)); births.merge(id, 1, Integer::sum); return TransitionOutcome.success(); }
			@Override public TransitionOutcome dematerialize(long id) { identities.remove(id); return TransitionOutcome.success(); }
			@Override public TransitionOutcome retryCleanup(long id) { return TransitionOutcome.success(); }
			@Override public boolean isMaterialized(long id) { return identities.containsKey(id); }
			@Override public boolean hasLifecycleOwnership(long id) { return identities.containsKey(id); }
		};
		final var port = new org.l2jmobius.gameserver.phantoms.activity.PhantomReconcileFirstActivityPort(delegate);
		final var metrics = new PhantomMetrics();
		final var scheduler = new PhantomScheduler(2, 100, 2, org.l2jmobius.gameserver.phantoms.activity.PhantomSchedulerPolicy.productionDefaults(100), nanos::get, (_pulse, _period) -> null, false, metrics, new PhantomDiagnosticTrace(false, 0, 0, metrics), port, org.l2jmobius.gameserver.phantoms.activity.PhantomActivityWorkSink.noop());
		final var decisionOwnership = new PhantomPopulationTestDoubles.Ownership();
		final var ownership = new org.l2jmobius.gameserver.phantoms.population.PhantomPopulationOwnershipPort()
		{
			@Override public PhantomScheduler.RegistrationStatus register(long id) { return scheduler.register(id).status(); }
			@Override public org.l2jmobius.gameserver.phantoms.decision.PhantomDecisionEngine.AttachResult attach(long id) { return decisionOwnership.attach(id); }
			@Override public PhantomScheduler.SignalStatus submit(long id, String source, long sequence, org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState state, long ttl) { return scheduler.submitSignal(id, new org.l2jmobius.gameserver.phantoms.activity.PhantomRelevanceSignal(source, sequence, state, ttl)).status(); }
			@Override public PhantomScheduler.SignalStatus withdraw(long id, String source, long sequence) { return scheduler.withdrawSignal(id, source, sequence).status(); }
			@Override public PhantomScheduler.UnregisterStatus unregister(long id) { return scheduler.unregister(id).status(); }
			@Override public org.l2jmobius.gameserver.phantoms.decision.PhantomDecisionEngine.DetachResult detach(long id) { return decisionOwnership.detach(id); }
			@Override public org.l2jmobius.gameserver.phantoms.decision.PhantomDecisionEngine.ReloadResult reload(long id) { return decisionOwnership.reload(id); }
			@Override public boolean registered(long id) { return scheduler.find(id).isPresent(); }
			@Override public boolean materialized(long id) { return delegate.isMaterialized(id); }
			@Override public int registeredCount() { return scheduler.snapshot().registered(); }
		};
		final var manager = new PhantomPopulationManager(population, _population, null, ownership, wall, ZoneOffset.UTC, 2, 2, 2, 2, 1, 64);
		final var workers = new java.util.ArrayDeque<Runnable>();
		final var ecology = new PhantomPopulationEcologyService(_catalog, _population, stored, new PhantomPopulationEcologyGoal033Suite.HistoricalMemoryPort(), delegate::isMaterialized, _ -> "", wall, ZoneOffset.UTC, Preset.LIVING, 0, 10, worker -> { workers.add(worker); return true; });
		ecology.enablePeriodicDueMode();
		manager.installEcology(ecology);
		final var signals = new org.l2jmobius.gameserver.phantoms.topology.PhantomSchedulerRelevanceSignalPort(scheduler);
		final var backend = new PhantomTopologyCoreSuite.TestBackend();
		final var topology = org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyService.fromSnapshotForTesting(PhantomTopologyCoreSuite.snapshot(backend), backend, PhantomTopologyCoreSuite.POLICY.withMaximumRegisteredProfiles(2), signals);
		topology.start();
		final var point = new java.util.concurrent.atomic.AtomicReference<>(PhantomTopologyCoreSuite.LEFT_POINT);
		final var sequence = new java.util.concurrent.atomic.AtomicLong();
		manager.installTopologyMembership(id -> { topology.registerProfile(id); topology.updateProfile(id, id == 1 ? point.get() : PhantomTopologyCoreSuite.LEFT_POINT, sequence.incrementAndGet()); }, topology::unregisterProfile);
		final var locality = new org.l2jmobius.gameserver.phantoms.topology.PhantomHumanLocalityControl(topology, signals, () -> java.util.List.of(PhantomTopologyCoreSuite.LEFT_POINT), () -> nanos.get() / 1_000_000L, manager.presence()::isOnline);
		port.installPopulationReadiness(manager.presence(), locality, ecology);
		ecology.installMaterializationDemand(id -> manager.presence().isOnline(id) && locality.isLocal(id));
		scheduler.installControlPort(manager);
		scheduler.installLocalControlPort(locality);
		scheduler.start();
		manager.start();
		try (var executor = Executors.newSingleThreadExecutor())
		{
			try
			{
				manager.onPulse();
				ecology.holdStartupPopulationPlan();
				workers.removeFirst().run();
				ecology.startupPopulationPlanApplied();
				manager.onPulse();
				scheduler.localPulse();
				PhantomAssertions.assertTrue(locality.isLocal(1) && manager.presence().isOnline(1), "Pending calendar-online profile lost actual local demand.");
				PhantomAssertions.assertFalse(identities.containsKey(1L), "Normal materialization crossed uncommitted readiness.");
				PhantomAssertions.assertEquals(10_002, identities.get(2L), "Ready local peer waited for stale ecology.");
				manager.onPulse();
				final var drain = executor.submit(workers.removeFirst());
				PhantomAssertions.assertTrue(entered.await(2, TimeUnit.SECONDS), "Canonical save did not reach I/O latch.");
				java.util.concurrent.CompletableFuture.runAsync(scheduler::pulse).get(200, TimeUnit.MILLISECONDS);
				java.util.concurrent.CompletableFuture.runAsync(scheduler::localPulse).get(200, TimeUnit.MILLISECONDS);
				PhantomAssertions.assertTrue(scheduler.find(1).orElseThrow().lastTransitionReason().startsWith("ecology."), "Typed policy reason was not published by the real scheduler: " + scheduler.find(1).orElseThrow().lastTransitionReason());
				final long horizon = ecology.dueSnapshot(1).requestedHorizonMinute();
				ecology.requestMaterializationDue(1);
				PhantomAssertions.assertEquals(horizon, ecology.dueSnapshot(1).requestedHorizonMinute(), "Repeated demand reset the requested horizon.");
				if (boundary.equals("offline")) { wall.set(NOW.plusSeconds(8 * 60 * 60)); manager.onPulse(); }
				if (boundary.equals("anchor")) { point.set(new org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint(50_000, 50_000, 0, 0)); topology.updateProfile(1, point.get(), sequence.incrementAndGet()); }
				if (boundary.equals("stop")) { ecology.beginStop(); PhantomAssertions.assertFalse(ecology.finishStop(), "Running commit released ecology ownership before drain."); }
				release.countDown();
				drain.get(3, TimeUnit.SECONDS);
				for (int pulse = 0; pulse < 100; pulse++)
				{
					nanos.addAndGet(100_000_000L);
					scheduler.pulse();
					if (!workers.isEmpty()) { workers.removeFirst().run(); }
					scheduler.localPulse();
					if (ecology.dueSnapshot(1).complete() && identities.containsKey(1L)) { break; }
				}
				if (boundary.equals("ready"))
				{
					PhantomAssertions.assertTrue(ecology.dueSnapshot(1).complete(), "Same-profile request did not reach committed READY.");
					PhantomAssertions.assertEquals(10_001, identities.get(1L), "Committed local demand did not reach canonical normal materialization.");
					PhantomAssertions.assertEquals(1, births.get(1L), "Readiness completion duplicated materialization identity.");
					PhantomAssertions.assertTrue(ecology.requestBackgroundReadiness(1).complete(), "Work sink readiness lost committed state.");
					PhantomAssertions.assertEquals(0, ecology.requestBackgroundReadiness(1).advancedIntervals(), "Admission consumed the farm handler receipt.");
					PhantomAssertions.assertTrue(ecology.requestBackgroundDue(1).advancedIntervals() > 0, "Committed interval receipt was lost.");
					PhantomAssertions.assertEquals(0, ecology.requestBackgroundDue(1).advancedIntervals(), "Repeated READY replayed a committed interval receipt.");
				}
				else { PhantomAssertions.assertFalse(identities.containsKey(1L), "Stale demand bypassed " + boundary + " boundary."); }
				if (boundary.equals("stop")) { PhantomAssertions.assertTrue(ecology.finishStop(), "Completed drain did not release ownership."); ecology.onPopulationPulse(); PhantomAssertions.assertTrue(workers.isEmpty(), "Worker dispatched after STOPPED."); }
			}
			finally { release.countDown(); }
		}
		finally
		{
			ecology.beginStop();
			while (!workers.isEmpty()) { workers.removeFirst().run(); }
			manager.beginStop(); manager.finishStop();
			scheduler.beginStop(); scheduler.finishStop();
			topology.beginStop(); topology.finishStop();
		}
	}
}
