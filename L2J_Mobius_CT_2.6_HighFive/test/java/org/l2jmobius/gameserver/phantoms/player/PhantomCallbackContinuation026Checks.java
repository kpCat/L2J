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
