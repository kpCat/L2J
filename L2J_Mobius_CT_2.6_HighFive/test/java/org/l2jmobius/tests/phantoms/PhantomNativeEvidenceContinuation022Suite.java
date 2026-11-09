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
		registry.add("130-stock-restored-hp-receipt-fences-owner-and-zero-effect", _ ->
		{
			final var evidence = new PlayerNativeEvidence(12, 100);
			evidence.recordRestoredHp(12, 99, "Healer", 40, 80);
			evidence.recordRestoredHp(13, 100, "Healer", 40, 80);
			evidence.recordRestoredHp(12, 100, "Healer", 0, 80);
			PhantomAssertions.assertEquals(null, evidence.restoredHpReceipt(), "Foreign, stale and zero-effect messages cannot witness Heal.");
			evidence.recordRestoredHp(12, 100, "Healer", 40, 80);
			evidence.recordRestoredHp(12, 100, "Healer", 20, 100);
			PhantomAssertions.assertEquals(new PlayerNativeEvidence.RestoredHpReceipt(2, "Healer", 20, 100), evidence.restoredHpReceipt(), "Only the latest positive stock receipt should be kept.");
		});
		registry.add("130-stock-heal-writer-has-no-conditional-test-mechanics", context ->
		{
			final String heal = java.nio.file.Files.readString(context.moduleRoot().resolve("dist/game/data/scripts/handlers/skill/effects/Heal.java"));
			PhantomAssertions.assertFalse(heal.contains("PlayerNativeEvidence") || heal.contains("synchronized (effected.getStatus())"), "F8: stock Heal must not change its writer/locking according to a test observer.");
			PhantomAssertions.assertTrue(heal.contains("effected.setCurrentHp(amount + effected.getCurrentHp());"), "The stock human and Phantom HP writer must be retained.");
		});
		registry.add("129-causal-heal-observation-is-exact-and-cannot-change-native-outcome", _ ->
		{
			final var method = PlayerNativeEvidence.class.getMethod("healed", int.class, long.class, int.class, long.class, int.class, int.class, double.class, double.class, double.class, long.class);
			final var field = PlayerNativeEvidence.class.getDeclaredField("_healObserver"); field.setAccessible(true);
			final var failureField = PlayerNativeEvidence.class.getDeclaredField("_healObserverFailure"); failureField.setAccessible(true);
			final Object previous = field.get(null), previousFailure = failureField.get(null);
			final var events = new java.util.ArrayList<long[]>();
			try
			{
				field.set(null, (java.util.function.BiConsumer<String, long[]>) (kind, values) -> { PhantomAssertions.assertEquals("HEAL", kind, "Native HP writer needs a distinct event kind."); events.add(values); });
				final var sensor = new PlayerNativeEvidence(1, 100);
				method.invoke(sensor, 1, 99L, 2, 200L, 1011, 3, 50d, 80d, 90d, 500L);
				method.invoke(sensor, 1, 100L, 1, 200L, 1011, 3, 50d, 80d, 90d, 500L);
				method.invoke(sensor, 1, 100L, 2, 200L, 1011, 3, 50d, 50d, 90d, 500L);
				method.invoke(sensor, 1, 100L, 2, 200L, 1011, 3, 50d, 80d, 90d, 500L);
				PhantomAssertions.assertEquals(1, events.size(), "Stale/self/zero-HP observations must not publish Heal.");
				PhantomAssertions.assertTrue(java.util.Arrays.equals(new long[] {1, 100, 2, 200, 1011, 3, Double.doubleToRawLongBits(50), Double.doubleToRawLongBits(80), Double.doubleToRawLongBits(90), 500}, events.getFirst()), "Receipt binds actual caster/recipient epochs, learned skill and native HP/MP/reuse.");
				field.set(null, (java.util.function.BiConsumer<String, long[]>) (kind, values) -> { throw new IllegalStateException("TEST129_OBSERVER_FAILURE"); });
				method.invoke(sensor, 1, 100L, 2, 200L, 1011, 3, 50d, 80d, 90d, 500L);
				PhantomAssertions.assertEquals("java.lang.IllegalStateException", failureField.get(null), "Observer failure remains diagnostic only.");
				PhantomAssertions.assertFalse(sensor.snapshot(101).overflow(), "Observer failure must not poison the native owner.");
			}
			finally { field.set(null, previous); failureField.set(null, previousFailure); }
		});
		registry.add("123-successful-consumption-is-exact-and-observational", _ ->
		{
			final var method = PlayerNativeEvidence.class.getMethod("consumed", int.class, long.class, int.class, int.class, long.class, long.class);
			final var field = PlayerNativeEvidence.class.getDeclaredField("_pickupObserver"); field.setAccessible(true);
			final Object previous = field.get(null);
			final var events = new java.util.ArrayList<long[]>();
			try
			{
				field.set(null, (java.util.function.BiConsumer<String, long[]>) (kind, values) -> { PhantomAssertions.assertEquals("CONSUME", kind, "Successful decrement needs a distinct event kind."); events.add(values); });
				final var sensor = new PlayerNativeEvidence(1, 100);
				method.invoke(sensor, 2, 100L, 33, 1835, 600L, 598L);
				method.invoke(sensor, 1, 99L, 33, 1835, 600L, 598L);
				method.invoke(sensor, 1, 100L, 33, 1835, 600L, 600L);
				method.invoke(sensor, 1, 100L, 33, 1835, 600L, 601L);
				method.invoke(sensor, 1, 100L, 33, 1835, 600L, 598L);
				PhantomAssertions.assertEquals(1, events.size(), "Foreign/stale/no-decrement observations must not publish consumption.");
				PhantomAssertions.assertTrue(java.util.Arrays.equals(new long[] {1, 33, 1835, 600, 1, 2, 598, 100}, events.getFirst()), "Receipt must bind actor, epoch, item object and actual locked before/after counts.");
				final var snapshot = sensor.snapshot(101);
				PhantomAssertions.assertTrue(snapshot.rewardSequence() == 0 && snapshot.lootSequence() == 0 && snapshot.expGained() == 0 && snapshot.spGained() == 0 && !snapshot.overflow(), "Observation must not create earned progress or loot credit.");
			}
			finally { field.set(null, previous); }
		});
		registry.add("123-consumption-observer-failure-cannot-change-native-outcome", _ ->
		{
			final var method = PlayerNativeEvidence.class.getMethod("consumed", int.class, long.class, int.class, int.class, long.class, long.class);
			final var field = PlayerNativeEvidence.class.getDeclaredField("_pickupObserver"); field.setAccessible(true);
			final var failureField = PlayerNativeEvidence.class.getDeclaredField("_pickupObserverFailure"); failureField.setAccessible(true);
			final Object previous = field.get(null), previousFailure = failureField.get(null);
			try
			{
				field.set(null, (java.util.function.BiConsumer<String, long[]>) (kind, values) -> { throw new IllegalStateException("TEST123_OBSERVER_FAILURE"); });
				final var sensor = new PlayerNativeEvidence(1, 100);
				method.invoke(sensor, 1, 100L, 33, 1835, 2L, 0L);
				PhantomAssertions.assertEquals(IllegalStateException.class.getName(), failureField.get(null), "Diagnostic failure must be visible independently of native outcome.");
				PhantomAssertions.assertFalse(sensor.snapshot(101).overflow(), "A diagnostic observer must not poison the native owner.");
			}
			finally { field.set(null, previous); failureField.set(null, previousFailure); }
		});
		registry.add("094-completed-cycle-retains-late-native-settlement-receipt", _ ->
		{
			for (int order = 0; order < 3; order++)
			{
				final var sensor = new PlayerNativeEvidence(1, 100);
				final var a = new Target(10, 0, 1); final var b = new Target(11, 0, 1);
				sensor.selected(a, 100); sensor.damage(a, 5, 101);
				if (order == 0) { sensor.selected(b, 102); sensor.reward(a, 20, 2, 103); sensor.killed(a, 104); }
				else if (order == 1) { sensor.reward(a, 20, 2, 102); sensor.selected(b, 103); sensor.killed(a, 104); }
				else { sensor.killed(a, 102); sensor.selected(b, 103); sensor.reward(a, 20, 2, 104); }
				PhantomAssertions.assertEquals(1L, sensor.snapshot(105).farmCycleSequence(), "Actual completed cycle changed, order=" + order);
				sensor.rewardSettled(a);
				PhantomAssertions.assertEquals(a, sensor.targetReceipts().settledTarget(), "Native settlement was lost after cycle retirement, order=" + order);
				PhantomAssertions.assertEquals(1L, sensor.targetReceipts().settledRewardSequence(), "Receipt did not bind the actual native award sequence.");
				sensor.rewardSettled(b);
				PhantomAssertions.assertEquals(a, sensor.targetReceipts().settledTarget(), "Unawarded target borrowed another target's native sequence.");
				sensor.reward(b, 30, 3, 106); sensor.rewardSettled(b); sensor.rewardSettled(a);
				PhantomAssertions.assertEquals(b, sensor.targetReceipts().settledTarget(), "Old boundary overwrote the latest exact native settlement.");
				final var now = sensor.snapshot(107);
				PhantomAssertions.assertTrue(!now.overflow() && now.expGained() == 50 && now.spGained() == 5 && now.rewardSequence() == 2 && now.killSequence() == 1 && now.farmCycleSequence() == 1, "Receipt changes fabricated awards or cycles.");
			}
		});
		registry.add("094-native-target-receipts-disambiguate-interleaved-settlements", _ ->
		{
			final var sensor = new PlayerNativeEvidence(1, 100);
			final var a = new Target(10, 0, 1); final var b = new Target(11, 0, 1);
			sensor.damage(a, 1, 101); sensor.damage(b, 1, 102);
			sensor.reward(b, 20, 2, 103); sensor.killed(b, 104); sensor.rewardSettled(b);
			final var global = sensor.snapshot(105); final var other = sensor.targetReceipts();
			PhantomAssertions.assertEquals(1L, global.rewardSequence(), "Exactly one other NPC settlement was observed.");
			PhantomAssertions.assertEquals(1L, global.killSequence(), "Exactly one other NPC kill was observed.");
			PhantomAssertions.assertEquals(b, other.rewardTarget(), "Global +1 cannot attribute NPC B reward to a dead NPC A with a pending boundary.");
			PhantomAssertions.assertEquals(b, other.killTarget(), "Kill receipt belongs to its actual native target.");
			PhantomAssertions.assertEquals(b, other.settledTarget(), "Only a completed exact target boundary proves settlement.");
			PhantomAssertions.assertFalse(a.equals(other.rewardTarget()), "The delayed A boundary cannot borrow B counters.");
			sensor.reward(a, 20, 2, 106); sensor.killed(a, 107); sensor.rewardSettled(a);
			final var exact = sensor.targetReceipts();
			PhantomAssertions.assertEquals(a, exact.rewardTarget(), "Actual A award replaces only the bounded latest receipt.");
			PhantomAssertions.assertEquals(a, exact.killTarget(), "Actual A kill remains source bound.");
			PhantomAssertions.assertEquals(2L, exact.settledRewardSequence(), "Settlement receipt stamps the exact award sequence.");
			final var support = new PlayerNativeEvidence(2, 100);
			support.reward(a, 20, 2, 101); support.rewardSettled(a, false);
			PhantomAssertions.assertEquals(a, support.targetReceipts().settledTarget(), "Pure support keeps its exact terminal receipt after retiring the unfinished map entry.");
			PhantomAssertions.assertEquals(0, support.snapshot(102).trackedTargets(), "Exact receipt does not retain unbounded target state.");
			final var respawn = new Target(a.objectId(), a.instanceId(), 2);
			support.reward(respawn, 20, 2, 103); support.rewardSettled(respawn, false);
			PhantomAssertions.assertEquals(respawn, support.targetReceipts().rewardTarget(), "A reused object ID requires its actual new spawn generation.");
			PhantomAssertions.assertFalse(a.equals(support.targetReceipts().settledTarget()), "A previous incarnation cannot satisfy a new spawn receipt.");
		});
		registry.add("094-pure-party-support-settlements-do-not-exhaust-unfinished-cap", _ ->
		{
			final var sensor = new PlayerNativeEvidence(1, System.nanoTime());
			for (int index = 1; index <= 32; index++)
			{
				final var target = new Target(index, 0, 1);
				sensor.reward(target, 20, 2); sensor.rewardSettled(target, false);
			}
			final var snapshot = sensor.snapshot();
			PhantomAssertions.assertFalse(snapshot.overflow(), "Settled non-damaging Party support must not overflow the unfinished target cap.");
			PhantomAssertions.assertEquals(640L, snapshot.expGained(), "Only actual Party EXP remains recorded.");
			PhantomAssertions.assertEquals(64L, snapshot.spGained(), "Only actual Party SP remains recorded.");
			PhantomAssertions.assertEquals(0L, snapshot.farmCycleSequence(), "Pure support rewards never fabricate own damage/kill cycles.");
		});
		registry.add("094-frozen-contributor-preserves-late-one-HP-damage-and-native-cycle", _ ->
		{
			for (boolean frozenContributor : new boolean[] {true, false})
			{
				final var sensor = new PlayerNativeEvidence(1, System.nanoTime());
				final var target = new Target(10, 0, 1);
				sensor.selected(target); sensor.reward(target, 20, 2);
				if (!frozenContributor) { sensor.damage(target, 1); }
				sensor.rewardSettled(target, frozenContributor);
				if (frozenContributor) { sensor.damage(target, 1); }
				sensor.killed(target); sensor.selected(new Target(11, 0, 1));
				final var snapshot = sensor.snapshot();
				PhantomAssertions.assertFalse(snapshot.overflow(), "Frozen contributor or existing damage cannot be retired as pure support.");
				PhantomAssertions.assertEquals(1L, snapshot.farmCycleSequence(), "Reward-before-damage and one-HP contribution preserve exactly one native cycle.");
				PhantomAssertions.assertEquals(20L, snapshot.expGained(), "Settlement never duplicates the native award.");
			}
		});
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
