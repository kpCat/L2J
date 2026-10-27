/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.l2jmobius.gameserver.phantoms.activity.PhantomMaterializationRetentionPolicy;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationCatalog;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyService.DemandFact;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationManager;
import org.l2jmobius.gameserver.phantoms.topology.PhantomHumanLocalityControl;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyService;

/** DB-free production owners; no claim of a network client or native World presence. */
public final class PhantomLivingPresence096Suite implements PhantomTestSuite
{
	@Override
	public String id() { return "living-presence096"; }

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("01-human-overflow-is-unknown-and-recovers", this::testOverflow);
		registry.add("02-recent-human-is-not-a-soft-victim", this::testRecentHuman);
		registry.add("03-lawful-demand-admission-fairness-and-calendar", this::testAdmission);
		registry.add("04-unadmitted-requester-cannot-reclaim", this::testReclaimAdmission);
	}

	private void testReclaimAdmission(PhantomTestContext context)
	{
		final var admitted = new java.util.concurrent.atomic.AtomicBoolean(false);
		final var releases = new AtomicLong();
		final var delegate = new org.l2jmobius.gameserver.phantoms.activity.PhantomActivityMaterializationPort()
		{
			@Override public TransitionOutcome materialize(long id) { return TransitionOutcome.deferred(); }
			@Override public TransitionOutcome dematerialize(long id) { releases.incrementAndGet(); return TransitionOutcome.success(); }
			@Override public TransitionOutcome retryCleanup(long id) { return TransitionOutcome.deferred(); }
			@Override public boolean isMaterialized(long id) { return id == 1; }
			@Override public boolean hasLifecycleOwnership(long id) { return id == 1; }
			@Override public TransitionOutcome reclaimSoft(long id, long epoch, long requester) { return dematerialize(id); }
		};
		final var signals = new org.l2jmobius.gameserver.phantoms.topology.PhantomRelevanceSignalPort()
		{
			@Override public SignalDelivery submit(long id, org.l2jmobius.gameserver.phantoms.activity.PhantomRelevanceSignal signal) { return SignalDelivery.ACCEPTED; }
			@Override public SignalDelivery withdraw(long id, String source, long sequence) { return SignalDelivery.ACCEPTED; }
		};
		final var topology = PhantomTopologyService.inertForTesting(signals, 2);
		final var locality = new PhantomHumanLocalityControl(topology, signals, List::of, () -> 1000);
		final var bridge = new org.l2jmobius.gameserver.phantoms.activity.PhantomReconcileFirstActivityPort(delegate);
		bridge.installPopulationReadiness(new org.l2jmobius.gameserver.phantoms.population.PhantomPresenceRegistry(2), locality, null, id -> false, id -> admitted.get());
		bridge.installSoftReclamationEpochs(id -> new org.l2jmobius.gameserver.phantoms.activity.PhantomActivityMaterializationPort.SoftReclaimSelection(1, 10), id -> true, id -> true);
		PhantomAssertions.assertEquals(0L, bridge.softReclaimSelection(2).profileId(), "Unadmitted request selected an idle victim.");
		admitted.set(true);
		PhantomAssertions.assertEquals(1L, bridge.softReclaimSelection(2).profileId(), "Lawful request did not reach existing selector.");
		admitted.set(false);
		PhantomAssertions.assertEquals(org.l2jmobius.gameserver.phantoms.activity.PhantomActivityMaterializationPort.Outcome.DEFERRED, bridge.reclaimSoft(1, 10, 2).outcome(), "Admission lost before boundary still evicted.");
		PhantomAssertions.assertEquals(0L, releases.get(), "Unadmitted request released capacity.");
	}

	private void testOverflow(PhantomTestContext context)
	{
		final var backend = new PhantomTopologyCoreSuite.TestBackend();
		final var signals = new org.l2jmobius.gameserver.phantoms.topology.PhantomRelevanceSignalPort()
		{
			@Override
			public SignalDelivery submit(long id, org.l2jmobius.gameserver.phantoms.activity.PhantomRelevanceSignal signal) { return SignalDelivery.ACCEPTED; }
			@Override
			public SignalDelivery withdraw(long id, String source, long sequence) { return SignalDelivery.ACCEPTED; }
		};
		final var topology = PhantomTopologyService.fromSnapshotForTesting(PhantomTopologyCoreSuite.snapshot(backend), backend, PhantomTopologyCoreSuite.POLICY, signals);
		final var now = new AtomicLong(1000);
		final var count = new AtomicLong(257);
		PhantomAssertions.assertTrue(topology.start(), "Topology start failed.");
		try
		{
			final var locality = new PhantomHumanLocalityControl(topology, signals,
				() -> java.util.Collections.nCopies((int) count.get(), PhantomTopologyCoreSuite.LEFT_POINT), now::get, id -> true, Map::of);
			locality.onPulse();
			PhantomAssertions.assertTrue(locality.physicalSnapshot().overflow(), "257 humans were silently reported as complete observation.");
			count.set(1); now.addAndGet(1000); locality.onPulse();
			PhantomAssertions.assertFalse(locality.physicalSnapshot().overflow(), "Bounded human census did not recover.");
			PhantomAssertions.assertEquals(0, locality.physicalSnapshot().facts().size(), "No residents must remain zero physical demand.");
			context.record("presence096.residents", "NO_ELIGIBLE_RESIDENT; PURE topology fixture only");
		}
		finally { topology.beginStop(); PhantomAssertions.assertTrue(topology.finishStop(), "Topology stop failed."); }
	}

	private void testRecentHuman(PhantomTestContext context)
	{
		final var now = new AtomicLong(1);
		final var local = new java.util.concurrent.atomic.AtomicBoolean(true);
		final var retention = new PhantomMaterializationRetentionPolicy(id -> new PhantomMaterializationRetentionPolicy.Facts(true, local.get(), false, false, false), now::get, 60_000);
		retention.observe(1); local.set(false); now.addAndGet(1_000_000);
		PhantomAssertions.assertFalse(retention.observe(1).reclaimable(), "Recent human grace was ignored under cap pressure.");
		now.addAndGet(60_000_000_000L);
		PhantomAssertions.assertTrue(retention.observe(1).reclaimable(), "Old unseen idle never became reclaimable.");
	}

	private void testAdmission(PhantomTestContext context) throws Exception
	{
		final var catalog = PhantomPopulationCatalog.load(context.moduleRoot().resolve("dist/game/data/phantoms/population/high-five-population-v3.xml"), ZoneOffset.UTC);
		final var store = new PhantomPopulationTestDoubles.MemoryStore(catalog.hash());
		for (long id = 1; id <= 10; id++) { store.seedReady(id, id <= 8 ? 1 : 2); }
		final var clock = new PhantomPopulationTestDoubles.MutableClock(Instant.parse("2026-10-26T20:00:00Z"));
		final var manager = new PhantomPopulationManager(store, catalog, null, new PhantomPopulationTestDoubles.Ownership(), clock, ZoneOffset.UTC, 10, 1, 16, 1, 2, 8);
		PhantomAssertions.assertTrue(manager.start(), "Population start failed.");
		try
		{
			for (int i = 0; i < 32; i++) { manager.onPulse(); }
			final long writes = store.writes();
			manager.updateMaterializationDemand(List.of(new DemandFact(9, 1, true, 100, 1), new DemandFact(1, 1, true, 200, 2)));
			for (int i = 0; i < 4; i++) { manager.onPulse(); }
			final long first = 9;
			PhantomAssertions.assertTrue(manager.admissionProfile(first).orElseThrow().admitted(), "Zero-quota region demand was not admitted.");
			clock.set(clock.instant().plusSeconds(1));
			manager.updateMaterializationDemand(List.of(new DemandFact(1, 2, true, 1, 2), new DemandFact(9, 2, true, 999, 1)));
			manager.onPulse();
			PhantomAssertions.assertTrue(manager.admissionProfile(first).orElseThrow().admitted(), "Adjacent observer motion churned admission.");
			clock.set(clock.instant().plusSeconds(30)); manager.onPulse();
			final long second = 1;
			PhantomAssertions.assertTrue(manager.admissionProfile(second).orElseThrow().admitted(), "Waiting lawful demand starved after bounded quantum.");
			PhantomAssertions.assertEquals(writes, store.writes(), "Demand pulse wrote durable population state.");
			clock.set(Instant.parse("2026-10-27T04:00:00Z"));
			for (int i = 0; i < 32; i++) { manager.onPulse(); }
			PhantomAssertions.assertFalse(manager.admissionProfile(9).orElseThrow().admitted(), "Demand bypassed sleeping legacy calendar.");
			context.record("presence096.admission", "cap1; zero regional quota; stable1s; fair31s; calendar enforced; no pulse writes; PURE identities, not town residents");
		}
		finally { manager.beginStop(); PhantomAssertions.assertTrue(manager.finishStop(), "Population stop failed."); }
	}
}
