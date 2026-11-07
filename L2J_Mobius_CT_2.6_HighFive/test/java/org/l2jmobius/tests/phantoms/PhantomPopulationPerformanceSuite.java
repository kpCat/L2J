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
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;

import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationCatalog;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationManager;
import org.l2jmobius.tests.phantoms.PhantomAssertions;
import org.l2jmobius.tests.phantoms.PhantomPopulationTestDoubles.MemoryStore;
import org.l2jmobius.tests.phantoms.PhantomPopulationTestDoubles.MutableClock;
import org.l2jmobius.tests.phantoms.PhantomPopulationTestDoubles.Ownership;
import org.l2jmobius.tests.phantoms.PhantomTestContext;
import org.l2jmobius.tests.phantoms.PhantomTestRegistry;
import org.l2jmobius.tests.phantoms.PhantomTestSuite;

public final class PhantomPopulationPerformanceSuite implements PhantomTestSuite
{
	private static final int EVALUATIONS = 100_000;
	private static final int REBALANCES = 10_000;
	private static final int READY_PROFILES = 10_000;
	private static final int SYNTHETIC_PROFILES = 100_000;
	private static final int BOUNDARY_BUDGET = 64;

	@Override
	public String id()
	{
		return "population-performance";
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("01-one-hundred-thousand-real-manager-pulses-with-zero-db-writes", this::testControlPulses);
		registry.add("02-ten-thousand-ready-dirty-admission-and-daily-rotation", this::testEvaluationAndAdmission);
		registry.add("03-one-hundred-thousand-entry-memory-and-worker-contract", this::testScaleAndNoWorkerOwnership);
	}
	public static void registerCoordination130(PhantomTestRegistry registry)
	{
		registry.add("execution-composition-reuses-canonical-ordinary-party-intent", _ ->
		{
			final var profile = new org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef("profile", "2");
			final var idle = new org.l2jmobius.gameserver.phantoms.decision.PhantomGoal(2,
				org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundGoalSpec.HISTORICAL_IDLE_GOAL_TYPE,
				org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus.ACTIVE, profile, profile, 1, 0, "background.idle", java.util.List.of(),
				new org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef(org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundGoalSpec.ANCHOR_NAMESPACE, "fixture"),
				"background.history", 500, 0, 0, 0, java.util.Map.of(), "planner.target_or_route.absent", 7);
			final var group = new org.l2jmobius.gameserver.phantoms.party.PhantomLogicalParty("A".repeat(64), 1, 1, java.util.List.of(
				new org.l2jmobius.gameserver.phantoms.party.PhantomLogicalParty.Member(1, "damage", 1, 0),
				new org.l2jmobius.gameserver.phantoms.party.PhantomLogicalParty.Member(2, "support", 2, 6)),
				new org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef("background.farm", "21298@fixture"), "fixture",
				org.l2jmobius.gameserver.phantoms.party.PhantomLogicalParty.Phase.AGREED, 0, 1000, "");
			PhantomAssertions.assertTrue(org.l2jmobius.gameserver.phantoms.party.PhantomAutonomousFarmParty.ordinaryIntent(2, idle), "Canonical idle fixture is not an existing ordinary Party intent.");
			PhantomAssertions.assertTrue(PhantomSystem.executionGroupBinding(group, 2, idle), "Execution kept a lawfully agreed idle/support member in Solo until the Group could prepare its farm root.");
			PhantomAssertions.assertFalse(PhantomSystem.executionGroupBinding(group, 2, idle.withStatus(org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus.FAILED)), "A terminal intent inherited Group execution.");
			PhantomAssertions.assertFalse(PhantomSystem.executionGroupBinding(group, 3, idle), "A foreign member inherited the group's execution reservation.");
		});
		registry.add("12-five-thousand-admission-ambient-target-and-offline-census", context ->
		{
			final long started = System.nanoTime(); final var catalog = PhantomPopulationCatalog.load(context.moduleRoot().resolve("dist/game/data/phantoms/population/high-five-population-v1.xml"),ZoneOffset.UTC);
			final var readRows = new java.util.concurrent.atomic.AtomicInteger();
			final var store = new MemoryStore(catalog.hash())
			{
				@Override public synchronized java.util.List<org.l2jmobius.gameserver.phantoms.population.PhantomPopulationStore.ManagedSnapshot> loadManagedAfter(long after,int limit)
				{ final var page=super.loadManagedAfter(after,limit); readRows.addAndGet(page.size()); return page; }
			};
			for(long id=1;id<=5000;id++){store.seedReady(id,(int)(id%20));} store.resetWrites();
			final var clock = new MutableClock(Instant.parse("2026-07-27T20:00:00Z")); final var ownership = new Ownership();
			final var manager=manager(store,catalog,ownership,clock,5000,128,5000,128); PhantomAssertions.assertTrue(manager.start(),"Scale manager did not start.");
			try
			{
				drain(manager,512); final var admitted=new java.util.HashSet<Long>(); final var slots=new java.util.HashSet<Integer>();
				for(long id=1;id<=5000;id++)
				{
					final var fact=manager.admissionProfile(id).orElseThrow(); if(!fact.admitted()){continue;} admitted.add(id);
					final var demand=org.l2jmobius.gameserver.phantoms.activity.PhantomPhysicalDemand.ambientTarget(id,slots.size(),128,false,1000);
					PhantomAssertions.assertTrue(demand!=null && demand.ordinaryAdmission(fact.calendarOnline(),fact.admitted(),1000),"Admitted profile could not contribute to the physical target without a human.");
					slots.add(Math.toIntExact(100000+id)); // The existing physical-admission boundary is modeled; no native Player is created.
				}
				PhantomAssertions.assertEquals(128,slots.size(),"Ambient demand remained a ceiling rather than filling the admitted synthetic target.");
				PhantomAssertions.assertEquals(null,org.l2jmobius.gameserver.phantoms.activity.PhantomPhysicalDemand.ambientTarget(5001,slots.size(),128,false,1000),"Ambient target exceeded its physical quota.");
				final var merchants=java.util.stream.IntStream.rangeClosed(1,250).map(id->200000+id).boxed().collect(java.util.stream.Collectors.toSet());
				final var humans=new java.util.HashSet<>(Set.of(300001,200001)); final var world=new java.util.HashSet<>(slots); world.addAll(merchants); world.addAll(humans); world.add(400001); merchants.add(slots.iterator().next());
				final var census=PhantomSystem.coordinationWorldCounts(world,slots,merchants,humans);
				PhantomAssertions.assertTrue(census.get("materializedManaged")==128 && census.get("nativeOfflineMerchants")==250 && census.get("humanOnline")==1 && census.get("worldTotal")==380,"Offline/human identities overlapped managed capacity.");
				for(int pulse=0;pulse<100;pulse++){readRows.set(0);manager.onPulse();PhantomAssertions.assertTrue(readRows.get()<=256 && manager.snapshot().lastPulseOperations()<=BOUNDARY_BUDGET,"A scale pulse performed an unbounded persistence page/control scan.");}
				clock.set(Instant.parse("2026-07-28T20:00:00Z"));manager.onPulse();drain(manager,512);final var rotated=new java.util.HashSet<Long>();
				for(long id=1;id<=5000;id++){if(manager.admissionProfile(id).orElseThrow().admitted()){rotated.add(id);}}
				PhantomAssertions.assertTrue(rotated.size()==128 && !rotated.equals(admitted),"Daily rotation lost quota or permanently monopolized the initial slice.");
				PhantomAssertions.assertEquals(0L,store.writes(),"Synthetic schedule/density checks wrote profile persistence.");
				context.record("task130.admission5000With250OfflineMillis",(System.nanoTime()-started)/1_000_000);context.record("task130.syntheticManaged",128);context.record("task130.syntheticOffline",250);
			}
			finally{stop(manager);}
		});
	}

	private void testControlPulses(PhantomTestContext context)
	{
		final PhantomPopulationCatalog catalog = catalog();
		final MemoryStore store = new MemoryStore(catalog.hash());
		store.seedReady(1, 1);
		store.resetWrites();
		final Ownership ownership = new Ownership();
		final MutableClock clock = new MutableClock(Instant.parse("2026-07-27T12:00:00Z"));
		final PhantomPopulationManager manager = manager(store, catalog, ownership, clock, 1, 0, 1, 1);
		PhantomAssertions.assertTrue(manager.start(), "Synthetic population manager did not start.");
		drain(manager, 32);
		final long start = System.nanoTime();
		for (int pulse = 0; pulse < EVALUATIONS; pulse++)
		{
			manager.onPulse();
			PhantomAssertions.assertTrue(manager.snapshot().lastPulseOperations() <= BOUNDARY_BUDGET, "A real population pulse exceeded its operation budget.");
		}
		final long elapsed = System.nanoTime() - start;
		PhantomAssertions.assertEquals(0L, store.writes(), "Pure schedule pulses wrote synthetic persistence.");
		PhantomAssertions.assertTrue(manager.snapshot().controlCalls() >= EVALUATIONS, "Real manager did not observe every control pulse.");
		stop(manager);
		context.record("populationPerformance.controlPulses", EVALUATIONS);
		context.record("populationPerformance.controlElapsedNanos", elapsed);
		context.record("populationPerformance.databaseWrites", store.writes());
	}

	private void testEvaluationAndAdmission(PhantomTestContext context)
	{
		final PhantomPopulationCatalog catalog = catalog();
		final long evaluationStart = System.nanoTime();
		Instant instant = Instant.parse("2026-01-01T00:00:00Z");
		for (int index = 0; index < EVALUATIONS; index++)
		{
			catalog.evaluate("evening", instant.plusSeconds((index % 10080) * 60L), ZoneOffset.UTC, index % 31);
		}
		final long evaluationElapsed = System.nanoTime() - evaluationStart;

		final MemoryStore store = new MemoryStore(catalog.hash());
		for (long profileId = 1; profileId <= READY_PROFILES; profileId++)
		{
			store.seedReady(profileId, (int) (profileId % 20));
		}
		store.resetWrites();
		final Ownership ownership = new Ownership();
		final MutableClock clock = new MutableClock(Instant.parse("2026-07-27T20:00:00Z"));
		final PhantomPopulationManager manager = manager(store, catalog, ownership, clock, READY_PROFILES, 2000, READY_PROFILES, 2000);
		final long rebalanceStart = System.nanoTime();
		PhantomAssertions.assertTrue(manager.start(), "Ten-thousand READY manager did not start.");
		drain(manager, 5000);
		final Set<Long> first = admittedIds(manager);
		PhantomAssertions.assertEquals(2000, first.size(), "Actual manager ACTIVE admission cap mismatch.");
		PhantomAssertions.assertEquals(0, ownership.activeIds().size(), "Calendar admission must not request physical ACTIVE without locality.");
		clock.set(Instant.parse("2026-07-28T20:00:00Z"));
		manager.onPulse();
		drain(manager, REBALANCES);
		final Set<Long> second = admittedIds(manager);
		final long rebalanceElapsed = System.nanoTime() - rebalanceStart;
		PhantomAssertions.assertEquals(2000, second.size(), "Daily ACTIVE rotation changed the cap.");
		PhantomAssertions.assertEquals(0, ownership.activeIds().size(), "Daily rotation must preserve locality-owned physical admission.");
		PhantomAssertions.assertTrue(!first.equals(second), "Actual manager daily ACTIVE rotation did not change membership.");
		PhantomAssertions.assertEquals(0L, store.writes(), "Admission and rotation wrote synthetic persistence.");
		PhantomAssertions.assertTrue(manager.snapshot().lastPulseOperations() <= BOUNDARY_BUDGET, "Admission pulse exceeded its operation budget.");
		stop(manager);
		context.record("populationPerformance.scheduleEvaluations", EVALUATIONS);
		context.record("populationPerformance.scheduleElapsedNanos", evaluationElapsed);
		context.record("populationPerformance.admissionRebalances", REBALANCES);
		context.record("populationPerformance.admissionElapsedNanos", rebalanceElapsed);
		context.record("populationPerformance.readyProfiles", READY_PROFILES);
	}

	private void testScaleAndNoWorkerOwnership(PhantomTestContext context)
	{
		final PhantomPopulationCatalog catalog = catalog();
		final MemoryStore store = new MemoryStore(catalog.hash());
		final long scaleStart = System.nanoTime();
		for (long profileId = 1; profileId <= SYNTHETIC_PROFILES; profileId++)
		{
			store.seedReady(profileId, (int) (profileId % 20));
		}
		store.resetWrites();
		final PhantomPopulationManager manager = manager(store, catalog, new Ownership(), new MutableClock(Instant.parse("2026-07-27T20:00:00Z")), SYNTHETIC_PROFILES, 1000, SYNTHETIC_PROFILES, 1000);
		PhantomAssertions.assertTrue(manager.start(), "One-hundred-thousand-entry manager did not start.");
		manager.onPulse();
		final long scaleElapsed = System.nanoTime() - scaleStart;
		PhantomAssertions.assertEquals(SYNTHETIC_PROFILES, manager.snapshot().managed(), "Synthetic memory population size mismatch.");
		PhantomAssertions.assertEquals(SYNTHETIC_PROFILES, manager.snapshot().ready(), "Synthetic READY index size mismatch.");
		PhantomAssertions.assertTrue(manager.snapshot().lastPulseOperations() <= BOUNDARY_BUDGET, "Scale pulse exceeded its operation budget.");
		PhantomAssertions.assertEquals(0L, store.writes(), "Scale pulse wrote synthetic persistence.");
		for (Field field : PhantomPopulationManager.class.getDeclaredFields())
		{
			final Class<?> type = field.getType();
			PhantomAssertions.assertTrue(!Thread.class.isAssignableFrom(type), "Population manager owns a Thread field.");
			PhantomAssertions.assertTrue(!Executor.class.isAssignableFrom(type), "Population manager owns an Executor field.");
			PhantomAssertions.assertTrue(!Future.class.isAssignableFrom(type), "Population manager owns a Future field.");
		}
		stop(manager);
		context.record("populationPerformance.syntheticProfiles", SYNTHETIC_PROFILES);
		context.record("populationPerformance.syntheticElapsedNanos", scaleElapsed);
	}

	private static Set<Long> admittedIds(PhantomPopulationManager manager)
	{
		final Set<Long> admitted = new java.util.HashSet<>();
		for (long profileId = 1; profileId <= READY_PROFILES; profileId++)
		{
			if (manager.admissionProfile(profileId).orElseThrow().admitted()) { admitted.add(profileId); }
		}
		PhantomAssertions.assertEquals(manager.admissionSnapshot().admittedActive(), admitted.size(), "Per-profile admission disagrees with manager census.");
		return Set.copyOf(admitted);
	}

	private static PhantomPopulationCatalog catalog()
	{
		return PhantomPopulationCatalog.load(Path.of("dist/game/data/phantoms/population/high-five-population-v1.xml"), ZoneOffset.UTC);
	}

	private static PhantomPopulationManager manager(MemoryStore store, PhantomPopulationCatalog catalog, Ownership ownership, MutableClock clock, int target, int activeTarget, int maximumScheduled, int maximumMaterialized)
	{
		return new PhantomPopulationManager(store, catalog, null, ownership, clock, ZoneOffset.UTC, target, activeTarget, maximumScheduled, maximumMaterialized, 64, BOUNDARY_BUDGET);
	}

	private static void drain(PhantomPopulationManager manager, int maximumPulses)
	{
		for (int pulse = 0; (pulse < maximumPulses) && (manager.snapshot().retryActions() > 0); pulse++)
		{
			manager.onPulse();
			PhantomAssertions.assertTrue(manager.snapshot().lastPulseOperations() <= BOUNDARY_BUDGET, "Drain pulse exceeded its operation budget.");
		}
		PhantomAssertions.assertEquals(0, manager.snapshot().retryActions(), "Synthetic ownership actions did not drain.");
	}

	private static void stop(PhantomPopulationManager manager)
	{
		manager.beginStop();
		PhantomAssertions.assertTrue(manager.finishStop(), "Synthetic population manager did not stop.");
	}
}
