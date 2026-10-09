/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.file.Path;
import org.l2jmobius.gameserver.model.actor.PlayerNativeEvidence;
import org.l2jmobius.gameserver.model.actor.PlayerNativeEvidence.Phase;
import org.l2jmobius.gameserver.model.actor.PlayerNativeEvidence.Target;

public final class PhantomNativeEvidenceContinuation022Suite implements PhantomTestSuite
{
	public static void main(String[] args)
	{
		System.exit(PhantomTestLauncher.runSuite("native-evidence-continuation022", new PhantomNativeEvidenceContinuation022Suite(), new PhantomTestContext(22002203, Path.of(args[0]), Path.of(args[1]))));
	}
	@Override public String id() { return "native-evidence-continuation022"; }
	@Override public void register(PhantomTestRegistry registry)
	{
		registry.add("S11-useful-native-offense-finishes-rest-gap-without-clearing-invalidity", _ ->
		{
			final var sensor = new PlayerNativeEvidence(1, System.nanoTime());
			sensor.nativeRegeneration(0, 1, false, false);
			final var resting = sensor.snapshot();
			PhantomAssertions.assertEquals(Phase.REGEN, resting.phase(), "Tagged positive native regeneration begins bounded rest.");
			sensor.damage(new Target(10, 0, 1), 1);
			PhantomAssertions.assertEquals(Phase.NONE, sensor.snapshot().phase(), "RED: actual useful offense completes a rest gap even while MP is still below maximum.");
			sensor.nativeRegeneration(0, 1, false, false);
			final var next = sensor.snapshot();
			PhantomAssertions.assertTrue(!next.overflow() && next.phase() == Phase.REGEN && next.phaseSinceNanos() > resting.phaseSinceNanos(), "A later actual regeneration gap has its own bounded native start.");
			sensor.pauseRegeneration(false, false); sensor.nativeRegeneration(0, 1, false, false);
			PhantomAssertions.assertEquals(next.phaseSinceNanos(), sensor.snapshot().phaseSinceNanos(), "Pause/retry without offense never renews the unfinished rest horizon.");
		});
		registry.add("S08-first-phase-deadline-reason-sticky-and-stale-clear", _ ->
		{
			final var sensor = new PlayerNativeEvidence(1, 100);
			sensor.phase(Phase.REGEN, 100, 200);
			final var failed = sensor.snapshot(200);
			PhantomAssertions.assertTrue(failed.overflow(), "Expired phase remains UNPROVEN.");
			PhantomAssertions.assertEquals("PHASE_DEADLINE", failed.scalarMap().get("nativeFirstUnprovenReason"), "RED: phase deadline must have its own first invalidation reason.");
			PhantomAssertions.assertEquals("200", failed.scalarMap().get("nativeFirstUnprovenNanos"), "First incident time is the observation, not report export time.");
			sensor.clearPhase(Phase.REGEN, 100); sensor.phase(Phase.COMBAT, 201, 301);
			PhantomAssertions.assertFalse(sensor.clearPhase(Phase.REGEN, 100), "Old completion cannot clear successor phase.");
			sensor.damage(new Target(-1, 0, 1), 1, 202);
			final var later = sensor.snapshot(302);
			PhantomAssertions.assertTrue(later.overflow(), "Invalidity stays sticky after exact producer clear.");
			PhantomAssertions.assertEquals("PHASE_DEADLINE", later.scalarMap().get("nativeFirstUnprovenReason"), "Later failures do not mask first incident.");
		});
		registry.add("S09-late-native-reward-after-next-target-counted-once", _ ->
		{
			for (int order = 0; order < 3; order++)
			{
				final var sensor = new PlayerNativeEvidence(1, 100);
				final var a = new Target(10, 0, 1); final var b = new Target(11, 0, 1);
				sensor.selected(a, 100); sensor.damage(a, 5, 101);
				if (order == 0) { sensor.reward(a, 20, 2, 102); sensor.killed(a, 103); sensor.selected(b, 104); }
				else if (order == 1) { sensor.killed(a, 102); sensor.reward(a, 20, 2, 103); sensor.selected(b, 104); }
				else { sensor.killed(a, 102); sensor.selected(b, 103); sensor.reward(a, 20, 2, 104); }
				PhantomAssertions.assertEquals(1L, sensor.snapshot(105).farmCycleSequence(), "RED: allowed native ordering completes exactly one cycle, order=" + order);
				sensor.selected(b, 106); sensor.killed(a, 107);
				PhantomAssertions.assertEquals(1L, sensor.snapshot(108).farmCycleSequence(), "Repeated native notifications cannot count a second cycle.");
			}
			final var foreign = new PlayerNativeEvidence(1, 100); final var a = new Target(10, 0, 1);
			foreign.selected(a, 100); foreign.killed(a, 101); foreign.reward(a, 20, 2, 102); foreign.selected(new Target(11, 0, 1), 103);
			PhantomAssertions.assertEquals(0L, foreign.snapshot(104).farmCycleSequence(), "No own damage means no attributed cycle.");
		});
		registry.add("S12-native-terminal-zero-SP-retired-without-inventing-cycle", _ ->
		{
			final var sensor = new PlayerNativeEvidence(1, System.nanoTime());
			java.lang.reflect.Method settle = null;
			try { settle = PlayerNativeEvidence.class.getMethod("rewardSettled", Target.class); } catch (NoSuchMethodException legacy) { /* Replay the prior sensor behavior for RED. */ }
			for (int i = 1; i <= 32; i++)
			{
				final var target = new Target(i, 0, 1);
				sensor.selected(target); sensor.damage(target, 2); sensor.reward(target, 1, 0); sensor.killed(target);
				if (settle != null) { settle.invoke(sensor, target); }
			}
			sensor.selected(new Target(100, 0, 1));
			final var snapshot = sensor.snapshot();
			PhantomAssertions.assertFalse(snapshot.overflow(), "RED: real reward settlement frees terminal zero-SP observations, not unfinished work.");
			PhantomAssertions.assertEquals(0L, snapshot.farmCycleSequence(), "EXP-only terminal awards cannot become an EXP+SP farm cycle.");
			PhantomAssertions.assertEquals(32L, snapshot.expGained(), "Only actual native EXP deltas remain recorded.");
			PhantomAssertions.assertEquals(0L, snapshot.spGained(), "No fabricated SP.");
			final var pending = new PlayerNativeEvidence(2, System.nanoTime());
			for (int i = 1; i <= 17; i++) { pending.damage(new Target(i, 0, 1), 1); }
			PhantomAssertions.assertEquals(PlayerNativeEvidence.UnprovenReason.TARGET_CAP, pending.snapshot().firstUnprovenReason(), "Genuine unfinished distinct targets remain capped at16.");
		});

		registry.add("S10-target-cap-generation-and-time-regression-truth", _ ->
		{
			final var sensor = new PlayerNativeEvidence(1, 100);
			for (int i = 1; i <= 16; i++) { sensor.damage(new Target(i, 0, 1), 1, 100 + i); }
			PhantomAssertions.assertFalse(sensor.snapshot(116).overflow(), "Exactly16 targets fit existing bound.");
			sensor.damage(new Target(1, 0, 2), 1, 117);
			PhantomAssertions.assertEquals("TARGET_CAP", sensor.snapshot(118).scalarMap().get("nativeFirstUnprovenReason"), "RED: reused object with new spawn generation is a distinct seventeenth target.");
			final var time = new PlayerNativeEvidence(2, 100); time.snapshot(110);
			PhantomAssertions.assertEquals("TIME_REGRESSION", time.snapshot(109).scalarMap().get("nativeFirstUnprovenReason"), "RED: time regression is distinct from phase/cap failure.");
			PhantomAssertions.assertFalse(time.matches(2, 101), "Old epoch never matches replacement.");
		});
	}
}
