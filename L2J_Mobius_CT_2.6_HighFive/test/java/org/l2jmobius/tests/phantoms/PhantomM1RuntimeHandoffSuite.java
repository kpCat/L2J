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
		registry.add("01-calendar-online-survives-readiness", this::calendarOnline);
		registry.add("02-io-stall-does-not-block-population-pulse", this::nonblockingPulse);
		registry.add("03-native-one-point-result-is-executable", _ -> singlePointPath());
		registry.add("04-real-coordinator-readiness-locality-and-stop", this::coordinatorHandoff);
		registry.add("05-silent-dispatch-loss-and-stale-worker", this::silentDispatchLoss);
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
				workers.removeFirst().run();
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
				PhantomAssertions.assertTrue(scheduler.find(1).orElseThrow().lastTransitionReason().startsWith("ecology."), "Typed policy reason was not published by the real scheduler.");
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
