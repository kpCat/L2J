/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.config.custom;

import org.l2jmobius.gameserver.model.actor.enums.npc.ChampionType;

/** One categorical roll, with final rather than conditional frequencies. */
public final class ChampionSelectionPolicy
{
	private ChampionSelectionPolicy()
	{
	}

	public static boolean validFrequencies(int red, int blue)
	{
		return (red >= 0) && (red <= 100) && (blue >= 0) && (blue <= 100) && ((red + blue) <= 100);
	}

	public static boolean eligible(boolean monster, boolean quest, boolean undying, boolean raid, boolean minion, int level, int instanceId, boolean enabled, int minimumLevel, int maximumLevel, boolean instances)
	{
		return enabled && monster && !quest && !undying && !raid && !minion && (level >= minimumLevel) && (level <= maximumLevel) && (instances || (instanceId == 0));
	}

	public static ChampionType select(int red, int blue, boolean redEligible, boolean blueEligible, int roll)
	{
		if (!validFrequencies(red, blue) || (roll < 0) || (roll >= 100))
		{
			return ChampionType.NORMAL;
		}
		final int redFrequency = redEligible ? red : 0;
		final int blueFrequency = blueEligible ? blue : 0;
		if (roll < redFrequency)
		{
			return ChampionType.RED;
		}
		return roll < (redFrequency + blueFrequency) ? ChampionType.BLUE : ChampionType.NORMAL;
	}
}
