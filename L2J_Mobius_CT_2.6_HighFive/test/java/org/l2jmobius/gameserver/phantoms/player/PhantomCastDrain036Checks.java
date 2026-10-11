/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.player;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.l2jmobius.gameserver.data.xml.NpcData;
import org.l2jmobius.gameserver.model.actor.Creature;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.PlayerNativeWork;
import org.l2jmobius.gameserver.model.actor.instance.Monster;
import org.l2jmobius.gameserver.model.skill.Skill;
import org.l2jmobius.gameserver.model.spawns.Spawn;
import org.l2jmobius.tests.phantoms.PhantomAssertions;
import org.l2jmobius.tests.phantoms.PhantomTestContext;

/** Reuses existing exact NativeLifetime disposal for deliberately failed TEST-only owners. */
public final class PhantomCastDrain036Checks
{
	private PhantomCastDrain036Checks() { }
	public static void sharedTarget(PhantomTestContext context, Player first, Player second) throws Exception
	{
		final var npc = new SharedMonster(); final var late = new Monster(NpcData.getInstance().getTemplate(20121)); spawn(npc, first); spawn(late, second);
		try (var one = new PhantomM1DynamicRecipientChecks.NativeLifetime(first); var two = new PhantomM1DynamicRecipientChecks.NativeLifetime(second))
		{
			PlayerNativeWork.run(first, List.of(npc), "TEST036_SHARED_CAST_ONE", () -> { first.setTarget(npc); first.doCast(first.getKnownSkill(1177)); });
			PlayerNativeWork.run(second, List.of(npc), "TEST036_SHARED_CAST_TWO", () -> { second.setTarget(npc); second.doCast(second.getKnownSkill(1177)); });
			final long initialDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(12);
			while ((first.isCastingNow() || second.isCastingNow() || one.scope.outstanding() != 0 || two.scope.outstanding() != 0) && System.nanoTime() < initialDeadline) { Thread.sleep(10); }
			context.record("E11.cooperative", "writers=" + npc._writers.size() + ";original=" + npc._original + ";dead=" + npc.isDead() + ";hp=" + npc.getCurrentHp());
			PhantomAssertions.assertTrue(npc._writers.contains(first) && npc._writers.contains(second) && npc._original, "E11 two legal managed doCast/MagicUseTask writers share the same NPC; their native damage may kill it.");
			// A real native kill while a second published cast is still pending; no HP assignment/reward fabrication.
			PlayerNativeWork.run(second, List.of(late), "TEST036_LATE_CAST", () -> { second.setTarget(late); second.doCast(second.getKnownSkill(1177)); });
			PhantomAssertions.assertTrue(second.isCastingNow() && two.scope.outstanding() > 0, "Actual late cast is already earned before native death.");
			PlayerNativeWork.run(first, List.of(late), "TEST036_NATIVE_DEATH_WRITER", () -> late.reduceCurrentHp(late.getMaxHp() * 2, first, null));
			PhantomAssertions.assertTrue(late.isDead(), "Stock native HP/death writer killed the target.");
			final long firstExp = first.getExp(), firstSp = first.getSp(), secondExp = second.getExp(), secondSp = second.getSp();
			one.scope.drainAndSeal(System.nanoTime() + TimeUnit.SECONDS.toNanos(10));
			two.scope.drainAndSeal(System.nanoTime() + TimeUnit.SECONDS.toNanos(10));
			PhantomAssertions.assertTrue(!second.isCastingNow() && one.scope.outstanding() == 0 && two.scope.outstanding() == 0 && one.scope.firstNativeIncident() == null && two.scope.firstNativeIncident() == null, "Late stock no-op retires both exact owners without poisoning cooperative admission.");
			PhantomAssertions.assertTrue(first.getExp() == firstExp && first.getSp() == firstSp && second.getExp() == secondExp && second.getSp() == secondSp, "Late corpse hit cannot fabricate duplicate EXP/SP.");
			context.record("E11.shared", "writers=" + npc._writers.size() + ";first=" + one.scope.diagnosticScalars() + ";second=" + two.scope.diagnosticScalars() + ";EXP=" + firstExp + "," + secondExp + ";SP=" + firstSp + "," + secondSp);
		}
		finally { npc.abortAttack(); npc.abortCast(); npc.deleteMe(); late.abortAttack(); late.abortCast(); late.deleteMe(); }
	}
	public static void failedSibling(PhantomTestContext context, Player first, Player second) throws Exception
	{
		final var fault = new FaultMonster(); final var healthy = new Monster(NpcData.getInstance().getTemplate(20121));
		spawn(fault, first); spawn(healthy, second);
		final double faultHp = fault.getCurrentHp(), healthyHp = healthy.getCurrentHp();
		PhantomNativeWorkScope failedScope = null;
		try (var firstLifetime = new PhantomM1DynamicRecipientChecks.NativeLifetime(first); var secondLifetime = new PhantomM1DynamicRecipientChecks.NativeLifetime(second))
		{
			failedScope = firstLifetime.scope;
			PlayerNativeWork.run(first, List.of(fault), "TEST036_FAILING_NATIVE_CAST", () -> { first.setTarget(fault); first.doCast(first.getKnownSkill(1177)); });
			PlayerNativeWork.run(second, List.of(healthy), "TEST036_HEALTHY_SIBLING_CAST", () -> { second.setTarget(healthy); second.doCast(second.getKnownSkill(1177)); });
			PhantomAssertions.assertTrue(first.isCastingNow() && second.isCastingNow(), "Both actual native casts published before any fault.");
			firstLifetime.scope.beginTerminalDrain(); secondLifetime.scope.beginTerminalDrain();
			final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
			while (firstLifetime.scope.firstNativeIncident() == null && System.nanoTime() < deadline) { Thread.sleep(10); }
			final var incident = firstLifetime.scope.firstNativeIncident();
			PhantomAssertions.assertTrue(incident != null && incident.message().contains("TASK036_NATIVE_BODY_AFTER_HP") && fault._original && fault._calls == 1 && fault.getCurrentHp() < faultHp, "E08 actual MagicUseTask mutation preserves its original body failure exactly once.");
			PhantomAssertions.assertThrows(IllegalStateException.class, () -> firstLifetime.scope.drainAndSeal(deadline), "True failed owner cannot produce healthy drain.");
			secondLifetime.scope.drainAndSeal(deadline);
			PhantomAssertions.assertTrue(secondLifetime.scope.outstanding() == 0 && secondLifetime.scope.firstNativeIncident() == null && !second.isCastingNow() && healthy.getCurrentHp() < healthyHp, "E09 already published healthy sibling finishes while shared dependencies remain live.");
			context.record("E08E09.failedSibling", "aggregate=FAILED;first=" + firstLifetime.scope.diagnosticScalars() + ";second=" + secondLifetime.scope.diagnosticScalars() + ";faultCalls=" + fault._calls + ";hp=" + faultHp + "->" + fault.getCurrentHp() + ";healthyHp=" + healthyHp + "->" + healthy.getCurrentHp() + ";disposition=FAILED_TEST_DISPOSAL_AFTER_QUIESCENCE");
			PhantomAssertions.assertTrue(firstLifetime.scope.isCurrent() && !firstLifetime.scope.diagnosticScalars().get("nativeOwnerFailure").isEmpty(), "Original failure/ownership retained at the negative boundary.");
		}
		finally { fault.abortAttack(); fault.abortCast(); fault.deleteMe(); healthy.abortAttack(); healthy.abortCast(); healthy.deleteMe(); }
		PhantomAssertions.assertTrue(failedScope != null && failedScope.firstNativeIncident() != null && !failedScope.diagnosticScalars().get("nativeOwnerFailure").isEmpty(), "TEST disposal never clears the original failure or relabels it COMPLETE.");
	}
	private static void spawn(Monster npc, Player player) throws Exception
	{
		final var spawn = new Spawn(npc.getTemplate()); spawn.setXYZ(player.getX() + 40, player.getY(), player.getZ()); npc.setSpawn(spawn);
		npc.setCurrentHpMp(npc.getMaxHp(), npc.getMaxMp()); npc.spawnMe(spawn.getX(), spawn.getY(), spawn.getZ());
	}
	private static final class FaultMonster extends Monster
	{
		int _calls; boolean _original;
		FaultMonster() { super(NpcData.getInstance().getTemplate(20121)); }
		@Override public void reduceCurrentHp(double damage, Creature attacker, boolean awake, boolean dot, Skill skill)
		{
			if (skill != null && damage > 0)
			{
				_calls++;
				_original = java.util.Arrays.stream(Thread.currentThread().getStackTrace()).anyMatch(frame -> frame.getClassName().endsWith(".MagicUseTask") && frame.getMethodName().equals("run"));
				super.reduceCurrentHp(damage, attacker, awake, dot, skill);
				throw new IllegalStateException("TASK036_NATIVE_BODY_AFTER_HP");
			}
			super.reduceCurrentHp(damage, attacker, awake, dot, skill);
		}
	}
	private static final class SharedMonster extends Monster
	{
		final java.util.Set<Creature> _writers = java.util.concurrent.ConcurrentHashMap.newKeySet();
		volatile boolean _original = true;
		SharedMonster() { super(NpcData.getInstance().getTemplate(20121)); }
		@Override public void reduceCurrentHp(double damage, Creature attacker, boolean awake, boolean dot, Skill skill)
		{
			if (skill != null && damage > 0)
			{
				_writers.add(attacker);
				_original &= java.util.Arrays.stream(Thread.currentThread().getStackTrace()).anyMatch(frame -> frame.getClassName().endsWith(".MagicUseTask") && frame.getMethodName().equals("run"));
			}
			super.reduceCurrentHp(damage, attacker, awake, dot, skill);
		}
	}
}
