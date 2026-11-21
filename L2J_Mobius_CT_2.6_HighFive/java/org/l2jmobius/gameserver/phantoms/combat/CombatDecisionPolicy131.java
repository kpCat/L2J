/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.combat;

import java.util.Comparator;
import java.util.List;

import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatLoadout.SelectedSkill;

/** Heuristic ordering for the player's current target. Callers own admission and native dispatch. */
public final class CombatDecisionPolicy131
{
	public record OffenseContext(boolean pvp, boolean targetCasting, boolean lineOfSight, double targetHpFraction, double actorMpFraction, double distance)
	{
	}

	public record Offense(SelectedSkill skill, boolean damage, boolean stopsCast, boolean effectActive, int nativePriority,
		int range, int castMillis, int magicLevel, double power, double mpCost, int recipients)
	{
		public Offense(SelectedSkill skill, boolean damage, boolean stopsCast, boolean effectActive, int nativePriority,
			int range, int castMillis, int magicLevel, double power, double mpCost)
		{
			this(skill, damage, stopsCast, effectActive, nativePriority, range, castMillis, magicLevel, power, mpCost, 1);
		}
	}

	private CombatDecisionPolicy131()
	{
	}

	public static List<Offense> rankOffense(OffenseContext context, List<Offense> candidates)
	{
		final boolean finishTarget = context.targetHpFraction() <= .25;
		final boolean conserveMp = context.actorMpFraction() <= .35;
		return candidates.stream().sorted(Comparator.comparingInt((Offense action) -> priority(context, action, finishTarget))
			.thenComparing(action -> !inRange(context, action))
			.thenComparingInt(action -> finishTarget ? action.castMillis() : 0)
			.thenComparingDouble(action -> conserveMp ? action.mpCost() : 0)
			.thenComparing(Comparator.comparingInt(Offense::recipients).reversed())
			.thenComparing(Comparator.comparingInt(Offense::nativePriority).reversed())
			.thenComparing(Comparator.comparingInt(Offense::magicLevel).reversed())
			.thenComparing(Comparator.comparingDouble(Offense::power).reversed())
			.thenComparingInt(Offense::castMillis)
			.thenComparingInt(action -> action.skill().skillId())
			.thenComparingInt(action -> action.skill().skillLevel())).toList();
	}

	private static int priority(OffenseContext context, Offense action, boolean finishTarget)
	{
		if (!action.effectActive() && action.stopsCast() && context.targetCasting() && inRange(context, action))
		{
			return 0;
		}
		if (action.recipients() > 1 && !action.damage() && action.stopsCast() && !action.effectActive())
		{
			return 1;
		}
		if (!finishTarget && context.pvp() && !action.effectActive() && action.nativePriority() >= 300 && inRange(context, action))
		{
			return 1;
		}
		return action.damage() ? 2 : action.effectActive() ? 4 : 3;
	}

	private static boolean inRange(OffenseContext context, Offense action)
	{
		return context.lineOfSight() && action.range() > 0 && context.distance() <= action.range();
	}
}
