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
		final Set<Long> first = ownership.activeIds();
		PhantomAssertions.assertEquals(2000, first.size(), "Actual manager ACTIVE admission cap mismatch.");
		clock.set(Instant.parse("2026-07-28T20:00:00Z"));
		manager.onPulse();
		drain(manager, REBALANCES);
		final Set<Long> second = ownership.activeIds();
		final long rebalanceElapsed = System.nanoTime() - rebalanceStart;
		PhantomAssertions.assertEquals(2000, second.size(), "Daily ACTIVE rotation changed the cap.");
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
