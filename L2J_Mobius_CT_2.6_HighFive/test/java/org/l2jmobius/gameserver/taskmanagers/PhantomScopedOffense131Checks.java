/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.taskmanagers;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.l2jmobius.gameserver.model.StatSet;
import org.l2jmobius.gameserver.model.skill.Skill;
import org.l2jmobius.gameserver.model.skill.SkillOperateType;
import org.l2jmobius.gameserver.taskmanagers.AutoPlayTaskManager.ScopedOffenseChoice;

/** DB-free scope checks for the callback around the existing stock writer. */
public final class PhantomScopedOffense131Checks
{
	public static void main(String[] args)
	{
		final Skill skill = skill(1, 1);
		final var writes = new AtomicInteger();
		final var deferred = new AtomicReference<Runnable>();
		final var scoped = new ScopedOffenseChoice(skill, submit ->
		{
			deferred.set(submit);
			final var wrongThread = Thread.ofVirtual().start(submit);
			try { wrongThread.join(); }
			catch (InterruptedException failure) { throw new IllegalStateException(failure); }
			submit.run();
			submit.run();
		});
		if (!AutoUseTaskManager.submitScoped(scoped, () -> true, writes::incrementAndGet)) { throw new AssertionError("Fresh synchronous native submission was denied."); }
		deferred.get().run();
		if (writes.get() != 1) { throw new AssertionError("A repeated, deferred or cross-thread submission reached the writer."); }

		final var current = new AtomicBoolean(true);
		final var revoked = new ScopedOffenseChoice(skill, submit ->
		{
			current.set(false);
			submit.run();
		});
		if (AutoUseTaskManager.submitScoped(revoked, current::get, writes::incrementAndGet) || writes.get() != 1)
		{
			throw new AssertionError("A revoked scope reached the writer.");
		}
		System.out.println("TASK131_SCOPED_SOLO_WRITER_PASS fresh=1 stale=0 duplicate=0 deferred=0 crossThread=0");
		testAllowedSkillsBeforeBound();
	}

	private static void testAllowedSkillsBeforeBound()
	{
		final Skill baseline = skill(10, 2), outsideRank = skill(11, 1), alternative = skill(11, 2);
		final var learned = List.of(skill(1, 1), skill(2, 1), skill(3, 1), skill(4, 1), outsideRank, alternative, baseline);
		final var allowed = Set.of(baseline, alternative);
		if (!AutoUseTaskManager.scopedOffenseCandidates(baseline, learned, allowed::contains, ignored -> true).equals(List.of(baseline, alternative)))
		{
			throw new AssertionError("Unlisted early skills or ranks displaced the pinned legal alternative.");
		}
		if (!AutoUseTaskManager.scopedOffenseCandidates(outsideRank, learned, allowed::contains, ignored -> true).isEmpty())
		{
			throw new AssertionError("An unlisted stock baseline must stay on the stock path.");
		}
		System.out.println("TASK131_SCOPED_SOLO_SKILL_SCOPE_PASS ranked=2 unlistedBaseline=stock");
	}

	private static Skill skill(int id, int level)
	{
		final var data = new StatSet();
		data.set("skill_id", id);
		data.set("level", level);
		data.set("operateType", SkillOperateType.A1);
		return new Skill(data);
	}
}
