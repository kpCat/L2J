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
import java.util.concurrent.Executor;
import java.util.concurrent.Future;

import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.phantoms.PhantomScheduler.RegistrationStatus;
import org.l2jmobius.gameserver.phantoms.PhantomScheduler.SignalStatus;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityMaterializationPort;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityOverloadLevel;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityWorkItem;
import org.l2jmobius.gameserver.phantoms.activity.PhantomRelevanceSignal;
import org.l2jmobius.gameserver.phantoms.activity.PhantomSchedulerPolicy;
import org.l2jmobius.tests.phantoms.PhantomAssertions;
import org.l2jmobius.tests.phantoms.PhantomTestContext;
import org.l2jmobius.tests.phantoms.PhantomTestRegistry;
import org.l2jmobius.tests.phantoms.PhantomTestSuite;

public final class PhantomActivitySchedulerPerformanceSuite implements PhantomTestSuite
{
	private static final int SCALE = 10_000;

	@Override
	public String id()
	{
		return "activity-scheduler-performance";
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("01-ten-thousand-dormant-structural-bounds", this::testDormantScale);
		registry.add("02-ten-thousand-warm-fairness-and-overload", this::testWarmBurst);
	}

	private void testDormantScale(PhantomTestContext context)
	{
		final ManualClock clock = new ManualClock();
		final PhantomMetrics metrics = new PhantomMetrics();
		final PhantomScheduler scheduler = scheduler(clock, metrics, item ->
		{
		});
		for (long profileId = 1; profileId <= SCALE; profileId++)
		{
			PhantomAssertions.assertEquals(RegistrationStatus.REGISTERED, scheduler.register(profileId).status(), "Dormant scale registration failed.");
		}
		final PhantomScheduler.SchedulerSnapshot snapshot = scheduler.snapshot();
		PhantomAssertions.assertEquals(SCALE, snapshot.registered(), "Dormant registered count mismatch.");
		PhantomAssertions.assertEquals(0, snapshot.ready(), "Dormant profiles created ready work.");
		PhantomAssertions.assertEquals(0, snapshot.due(), "Dormant profiles created due work.");
		PhantomAssertions.assertEquals(0, snapshot.scheduledTaskCount(), "Manual scale scheduler reported per/profile future work.");
		PhantomAssertions.assertEquals(PhantomActivityState.SLEEPING, scheduler.find(1).orElseThrow().effectiveState(), "Dormant profile did not remain SLEEPING.");
		assertSlotHasNoRuntimeOwnerFields();
		context.record("activityScale.dormantProfiles", snapshot.registered());
		context.record("activityScale.dormantReady", snapshot.ready());
		context.record("activityScale.dormantDue", snapshot.due());
		context.record("activityScale.manualScheduledFutures", snapshot.scheduledTaskCount());
		scheduler.beginStop();
		PhantomAssertions.assertTrue(scheduler.finishStop(), "Dormant scale scheduler did not finish stop.");
		assertZeroResidue(scheduler);
	}

	private void testWarmBurst(PhantomTestContext context)
	{
		final ManualClock clock = new ManualClock();
		final PhantomMetrics metrics = new PhantomMetrics();
		final long[] ticks = new long[SCALE + 1];
		final PhantomActivityOverloadLevel[] firstOverload = new PhantomActivityOverloadLevel[1];
		final PhantomScheduler scheduler = scheduler(clock, metrics, item ->
		{
			ticks[(int) item.profileId()]++;
			if (firstOverload[0] == null)
			{
				firstOverload[0] = item.overloadLevel();
			}
		});
		for (long profileId = 1; profileId <= SCALE; profileId++)
		{
			scheduler.register(profileId);
		}
		for (long profileId = 1; profileId <= SCALE; profileId++)
		{
			final SignalStatus status = scheduler.submitSignal(profileId, new PhantomRelevanceSignal("scale", 1, PhantomActivityState.WARM, 1000)).status();
			PhantomAssertions.assertEquals(SignalStatus.ACCEPTED, status, "Warm burst signal was not accepted.");
		}
		PhantomAssertions.assertEquals(SCALE, scheduler.snapshot().ready(), "Warm burst exceeded/lost bounded ready entries.");
		int pulses = 0;
		while (scheduler.snapshot().ready() > 0)
		{
			scheduler.pulse();
			pulses++;
			PhantomAssertions.assertTrue(pulses <= 100, "Warm burst did not drain within deterministic pulse bound.");
			PhantomAssertions.assertTrue(scheduler.snapshot().ready() <= SCALE, "Ready queue exceeded declared capacity.");
			PhantomAssertions.assertTrue(scheduler.snapshot().due() <= SCALE, "Due set exceeded one entry per profile.");
		}
		for (int profileId = 1; profileId <= SCALE; profileId++)
		{
			PhantomAssertions.assertEquals(1L, ticks[profileId], "A due cohort profile missed its first opportunity or received a second early opportunity.");
			PhantomAssertions.assertEquals(1L, scheduler.find(profileId).orElseThrow().tickSequence(), "Profile tick sequence violated first-cohort fairness.");
		}
		PhantomAssertions.assertEquals(PhantomActivityOverloadLevel.CRITICAL, firstOverload[0], "Full burst did not apply CRITICAL overload.");
		PhantomAssertions.assertEquals(PhantomActivityOverloadLevel.CRITICAL, scheduler.snapshot().peakOverloadLevel(), "Peak overload did not retain CRITICAL.");
		PhantomAssertions.assertEquals(24_000_000L, scheduler.find(1).orElseThrow().nextDueNanos(), "CRITICAL WARM cadence was not multiplied by eight.");
		PhantomAssertions.assertEquals(SCALE, scheduler.snapshot().due(), "Warm cadence did not retain exactly one due entry per profile.");

		context.record("activityScale.warmProfiles", SCALE);
		context.record("activityScale.profilesPerPulse", 128);
		context.record("activityScale.pulsesToFirstOpportunity", pulses);
		context.record("activityScale.firstOpportunityMinimum", 1);
		context.record("activityScale.firstOpportunityMaximum", 1);
		context.record("activityScale.maximumReady", SCALE);
		context.record("activityScale.maximumDue", SCALE);
		context.record("activityScale.criticalWarmDueNanos", scheduler.find(1).orElseThrow().nextDueNanos());
		scheduler.beginStop();
		PhantomAssertions.assertTrue(scheduler.finishStop(), "Warm scale scheduler did not finish stop.");
		assertZeroResidue(scheduler);
	}

	private static PhantomScheduler scheduler(ManualClock clock, PhantomMetrics metrics, java.util.function.Consumer<PhantomActivityWorkItem> sink)
	{
		final PhantomScheduler scheduler = new PhantomScheduler(
			SCALE,
			10,
			128,
			new PhantomSchedulerPolicy(16, 1000, 5, 2, 8, 1, 2, 3, 4, 50),
			clock,
			(pulse, period) -> null,
			false,
			metrics,
			new PhantomDiagnosticTrace(false, 0, 0, metrics),
			PhantomActivityMaterializationPort.noop(),
			sink::accept);
		PhantomAssertions.assertTrue(scheduler.start(), "Manual scale scheduler did not start.");
		return scheduler;
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
			final Class<?> type = field.getType();
			PhantomAssertions.assertFalse(Future.class.isAssignableFrom(type), "Slot stores a Future: " + field.getName());
			PhantomAssertions.assertFalse(Thread.class.isAssignableFrom(type), "Slot stores a Thread: " + field.getName());
			PhantomAssertions.assertFalse(Executor.class.isAssignableFrom(type), "Slot stores an Executor: " + field.getName());
			PhantomAssertions.assertFalse(Player.class.isAssignableFrom(type), "Slot stores a Player: " + field.getName());
		}
	}

	private static void assertZeroResidue(PhantomScheduler scheduler)
	{
		final PhantomScheduler.SchedulerSnapshot snapshot = scheduler.snapshot();
		PhantomAssertions.assertEquals(0, snapshot.registered(), "Stopped scheduler retained registrations.");
		PhantomAssertions.assertEquals(0, snapshot.ready(), "Stopped scheduler retained ready entries.");
		PhantomAssertions.assertEquals(0, snapshot.due(), "Stopped scheduler retained due entries.");
		PhantomAssertions.assertEquals(0, snapshot.scheduledTaskCount(), "Stopped scheduler retained a future.");
	}

	private static final class ManualClock implements PhantomScheduler.MonotonicClock
	{
		@Override
		public long nanoTime()
		{
			return 0;
		}
	}
}
