/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.player;

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.l2jmobius.gameserver.data.xml.NpcData;
import org.l2jmobius.gameserver.model.actor.Creature;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.instance.Monster;
import org.l2jmobius.gameserver.model.skill.Skill;
import org.l2jmobius.tests.phantoms.PhantomAssertions;
import org.l2jmobius.tests.phantoms.PhantomTestContext;

/** TEST-only barriers at the original native writer, never replacing its damage/effects. */
public final class PhantomCallbackContinuation026Checks
{
	private PhantomCallbackContinuation026Checks() { }
	public static void actualKill(PhantomTestContext context, Player player, Monster npc, Skill skill, boolean listenerFailure) throws Exception
	{
		try (var lifetime = new PhantomM1DynamicRecipientChecks.NativeLifetime(player))
		{
			final var scope = lifetime.scope;
			final var calls = new java.util.concurrent.atomic.AtomicInteger();
			final var children = new java.util.concurrent.atomic.AtomicInteger();
			org.l2jmobius.gameserver.model.script.Quest questValue = null;
			java.util.concurrent.atomic.AtomicReference<java.util.function.Consumer<Player>> bodyValue = null;
			if (!listenerFailure)
			{
				org.l2jmobius.gameserver.scripting.ScriptEngine.getInstance().executeScript(context.moduleRoot().resolve("test/resources/phantoms/M1TimerBootstrap.java"));
				questValue = org.l2jmobius.gameserver.managers.ScriptManager.getInstance().getScript("M1TimerBootstrap");
				PhantomAssertions.assertTrue(questValue != null, "Existing stock ScriptEngine TEST Quest bootstrap.");
				final var bodyField = org.l2jmobius.tests.phantoms.PhantomM1TimerChecks.class.getDeclaredField("QUEST_BODY"); bodyField.setAccessible(true);
				@SuppressWarnings("unchecked") final var existingBody = (java.util.concurrent.atomic.AtomicReference<java.util.function.Consumer<Player>>) bodyField.get(null);
				bodyValue = existingBody;
				PhantomAssertions.assertTrue(existingBody.compareAndSet(null, actor ->
				{
					PhantomAssertions.assertTrue(actor == player && org.l2jmobius.gameserver.model.actor.PlayerNativeWork.current(scope) != null, "Stock QuestTimer body retains inherited exact earned owner.");
					children.incrementAndGet(); actor.setHeading((actor.getHeading() + 32) % 65536);
				}), "Existing Quest TEST body is exclusively owned.");
			}
			final var quest = questValue; final var questBody = bodyValue;
			final int heading = player.getHeading(); final long exp = player.getExp();
			final var listener = new org.l2jmobius.gameserver.model.events.listeners.ConsumerEventListener(npc, org.l2jmobius.gameserver.model.events.EventType.ON_ATTACKABLE_KILL,
				(org.l2jmobius.gameserver.model.events.holders.actor.npc.attackable.OnAttackableKill event) ->
				{
					PhantomAssertions.assertTrue(event.getAttacker() == player && event.getTarget() == npc && org.l2jmobius.gameserver.model.actor.PlayerNativeWork.current(scope) != null, "Actual native kill traversal retains the exact earned ticket.");
					calls.incrementAndGet(); player.setHeading((heading + 32) % 65536);
					if (listenerFailure) { throw new IllegalStateException("TASK026_LISTENER_AFTER_NATIVE_MUTATION"); }
					quest.startQuestTimer("TASK026_ACTUAL_KILL_CHILD", 150, npc, player);
				}, scope);
			npc.addListener(listener);
			try
			{
				try (var gate = new PhantomM1DynamicRecipientChecks.WorkerGate())
				{
					gate.acquire();
					final double beforeHp = npc.getCurrentHp();
					org.l2jmobius.gameserver.model.actor.PlayerNativeWork.run(player, java.util.List.of(npc), "TEST026_ORIGINAL_CALL_SKILL", () -> { player.setTarget(npc); player.callSkill(skill, java.util.List.of(npc)); });
					if (!npc.isDead()) { org.l2jmobius.gameserver.model.actor.PlayerNativeWork.run(player, java.util.List.of(npc), "TEST026_ORIGINAL_SECOND_CALL_SKILL", () -> player.callSkill(skill, java.util.List.of(npc))); }
					context.record("E04.nativeWriter." + listenerFailure, "hp=" + beforeHp + "->" + npc.getCurrentHp() + ";dead=" + npc.isDead() + ";EXP=" + exp + "->" + player.getExp() + ";npcInvul=" + npc.isInvul() + ";player=" + player.getX() + "," + player.getY() + "," + player.getZ() + ";knownSkill=" + skill + ";owner=" + scope.snapshot());
					PhantomAssertions.assertTrue(npc.isDead() && player.getExp() > exp, "Original native HP death and reward create this kill event.");
					context.record("E04.queued." + listenerFailure, scope.snapshot());
					PhantomAssertions.assertTrue(scope.snapshot().contains("EVENT:ON_ATTACKABLE_KILL:RESERVED") && calls.get() == 0, "Worker hold retains the actual RESERVED kill event; no timer completion.");
					gate.release();
				}
				if (listenerFailure)
				{
					PhantomAssertions.assertThrows(IllegalStateException.class, () -> scope.drainAndSeal(System.nanoTime() + TimeUnit.SECONDS.toNanos(12)), "A mutated failing listener cannot produce successful drain.");
					PhantomAssertions.assertTrue(scope.firstNativeIncident() != null && player.getHeading() == (heading + 32) % 65536, "The real listener failure and native mutation remain visible.");
				}
				else
				{
					scope.drainAndSeal(System.nanoTime() + TimeUnit.SECONDS.toNanos(12));
					PhantomAssertions.assertEquals(1, children.get(), "Actual stock QuestTimer child finished before SEALED.");
					PhantomAssertions.assertTrue(scope.sealed() && scope.outstanding() == 0 && scope.pendingTimers() == 0, "Exact event and native quest child drained without fabricated completion.");
				}
				PhantomAssertions.assertEquals(1, calls.get(), "Actual kill callback traversed once.");
				context.record("E05.complete." + listenerFailure, "calls=" + calls.get() + ";children=" + children.get() + ";scope=" + scope.snapshot());
			}
			finally { npc.removeListener(listener); if (questBody != null) { questBody.set(null); } }
		}
	}
	public static final class OriginalCastMonster extends Monster
	{
		private final CountDownLatch _secondWritten = new CountDownLatch(1);
		private Player _first, _second;
		private volatile double _before, _after;
		private volatile boolean _originalSecond;
		public OriginalCastMonster() { super(NpcData.getInstance().getTemplate(20121)); }
		public void bind(Player first, Player second) { _first = first; _second = second; }
		@Override public void reduceCurrentHp(double damage, Creature attacker, boolean awake, boolean dot, Skill skill)
		{
			if (attacker == _first && skill != null && damage > 0)
			{
				PhantomAssertions.assertFalse(Thread.holdsLock(this) || Thread.holdsLock(_first), "TEST026 barrier precedes native actor/status locks.");
				try { PhantomAssertions.assertTrue(_secondWritten.await(12, TimeUnit.SECONDS), "Original secondary MagicUseTask must write before first native HP writer."); }
				catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new IllegalStateException(failure); }
			}
			if (attacker == _second && skill != null && damage > 0)
			{
				_before = getCurrentHp();
				_originalSecond = Arrays.stream(Thread.currentThread().getStackTrace()).anyMatch(frame -> frame.getClassName().equals("org.l2jmobius.gameserver.model.actor.tasks.creature.MagicUseTask") && frame.getMethodName().equals("run"));
				super.reduceCurrentHp(damage, attacker, awake, dot, skill);
				_after = getCurrentHp(); _secondWritten.countDown();
			}
			else { super.reduceCurrentHp(damage, attacker, awake, dot, skill); }
		}
		public void requireOrder(PhantomTestContext context)
		{
			context.record("E02.originalEntry", "MagicUseTask=" + _originalSecond + ";secondaryHp=" + _before + "->" + _after + ";target=" + getNativeEvidenceTarget());
			PhantomAssertions.assertTrue(_originalSecond && _before > _after && _after > 0, "Original secondary doCast performs genuine nonlethal HP write before original first death writer.");
		}
	}
}
