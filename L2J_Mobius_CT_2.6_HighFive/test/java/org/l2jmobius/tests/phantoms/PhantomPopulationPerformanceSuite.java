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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityMaterializationPort;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState;
import org.l2jmobius.gameserver.phantoms.activity.PhantomSchedulerPolicy;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationCatalog;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationManager;
import org.l2jmobius.tests.phantoms.PhantomAssertions;
import org.l2jmobius.tests.phantoms.PhantomTestContext;
import org.l2jmobius.tests.phantoms.PhantomTestRegistry;
import org.l2jmobius.tests.phantoms.PhantomTestSuite;

public final class PhantomPopulationPerformanceSuite implements PhantomTestSuite
{
	private static final int EVALUATIONS = 100_000;
	private static final int REBALANCES = 10_000;
	private static final int SYNTHETIC_PROFILES = 10_000;

	@Override
	public String id()
	{
		return "population-performance";
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("01-one-hundred-thousand-shared-control-pulses", this::testControlPulses);
		registry.add("02-schedule-evaluations-and-admission-rebalances", this::testEvaluationAndAdmission);
		registry.add("03-no-population-worker-task-or-future-fields", this::testNoWorkerOwnership);
	}

	private void testControlPulses(PhantomTestContext context)
	{
		final ManualClock clock = new ManualClock();
		final PhantomMetrics metrics = new PhantomMetrics();
		final AtomicLong calls = new AtomicLong();
		final PhantomScheduler scheduler = new PhantomScheduler(
			1,
			10,
			1,
			new PhantomSchedulerPolicy(16, 1000, 5, 2, 8, 1, 2, 3, 4, 50),
			clock,
			(pulse, period) -> null,
			false,
			metrics,
			new PhantomDiagnosticTrace(false, 0, 0, metrics),
			PhantomActivityMaterializationPort.noop(),
			item ->
			{
			});
		PhantomAssertions.assertTrue(scheduler.installControlPort(calls::incrementAndGet), "Control port installation failed.");
		PhantomAssertions.assertTrue(scheduler.start(), "Manual population scheduler did not start.");
		final long start = System.nanoTime();
		for (int pulse = 0; pulse < EVALUATIONS; pulse++)
		{
			scheduler.pulse();
			clock._nanos += 10_000_000L;
		}
		final long elapsed = System.nanoTime() - start;
		PhantomAssertions.assertEquals((long) EVALUATIONS, calls.get(), "Scheduler did not invoke exactly one control hook per pulse.");
		PhantomAssertions.assertEquals(0, scheduler.snapshot().scheduledTaskCount(), "Manual population pulses created a scheduled task.");
		scheduler.beginStop();
		PhantomAssertions.assertTrue(scheduler.finishStop(), "Manual population scheduler did not stop.");
		context.record("populationPerformance.controlPulses", calls.get());
		context.record("populationPerformance.controlElapsedNanos", elapsed);
		context.record("populationPerformance.databaseWrites", 0);
	}

	private void testEvaluationAndAdmission(PhantomTestContext context)
	{
		final PhantomPopulationCatalog catalog = PhantomPopulationCatalog.load(Path.of("dist/game/data/phantoms/population/high-five-population-v1.xml"), ZoneOffset.UTC);
		final long evaluationStart = System.nanoTime();
		Instant instant = Instant.parse("2026-01-01T00:00:00Z");
		for (int index = 0; index < EVALUATIONS; index++)
		{
			catalog.evaluate("evening", instant.plusSeconds((index % 10080) * 60L), ZoneOffset.UTC, index % 31);
		}
		final long evaluationElapsed = System.nanoTime() - evaluationStart;

		final List<PhantomPopulationManager.AdmissionProfile> small = new ArrayList<>();
		for (long profileId = 1; profileId <= 32; profileId++)
		{
			small.add(new PhantomPopulationManager.AdmissionProfile(profileId, (int) (profileId % 4), 16_001_601L + profileId, PhantomActivityState.ACTIVE));
		}
		final long rebalanceStart = System.nanoTime();
		for (int day = 0; day < REBALANCES; day++)
		{
			PhantomAssertions.assertEquals(8, PhantomPopulationManager.selectActiveProfiles(small, 8, day).size(), "Repeated ACTIVE rebalance cap mismatch.");
		}
		final long rebalanceElapsed = System.nanoTime() - rebalanceStart;

		final List<PhantomPopulationManager.AdmissionProfile> scale = new ArrayList<>(SYNTHETIC_PROFILES);
		for (long profileId = 1; profileId <= SYNTHETIC_PROFILES; profileId++)
		{
			scale.add(new PhantomPopulationManager.AdmissionProfile(profileId, (int) (profileId % 20), 16_001_601L + profileId, (profileId % 3) == 0 ? PhantomActivityState.WARM : PhantomActivityState.ACTIVE));
		}
		final long scaleStart = System.nanoTime();
		final Set<Long> admitted = PhantomPopulationManager.selectActiveProfiles(scale, 2000, 42);
		final long scaleElapsed = System.nanoTime() - scaleStart;
		PhantomAssertions.assertEquals(2000, admitted.size(), "Synthetic 10000-profile ACTIVE admission mismatch.");
		context.record("populationPerformance.scheduleEvaluations", EVALUATIONS);
		context.record("populationPerformance.scheduleElapsedNanos", evaluationElapsed);
		context.record("populationPerformance.admissionRebalances", REBALANCES);
		context.record("populationPerformance.admissionElapsedNanos", rebalanceElapsed);
		context.record("populationPerformance.syntheticProfiles", SYNTHETIC_PROFILES);
		context.record("populationPerformance.syntheticElapsedNanos", scaleElapsed);
	}

	private void testNoWorkerOwnership(PhantomTestContext context)
	{
		for (Field field : PhantomPopulationManager.class.getDeclaredFields())
		{
			final Class<?> type = field.getType();
			PhantomAssertions.assertTrue(!Thread.class.isAssignableFrom(type), "Population manager owns a Thread field.");
			PhantomAssertions.assertTrue(!Executor.class.isAssignableFrom(type), "Population manager owns an Executor field.");
			PhantomAssertions.assertTrue(!Future.class.isAssignableFrom(type), "Population manager owns a Future field.");
		}
	}

	private static final class ManualClock implements PhantomScheduler.MonotonicClock
	{
		private long _nanos;

		@Override
		public long nanoTime()
		{
			return _nanos;
		}
	}
}
