/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.qol;

import java.util.Map;

import org.l2jmobius.gameserver.config.custom.PremiumSystemConfig;
import org.l2jmobius.gameserver.model.actor.Player;

/** Shared premium selection for grouped/ungrouped loot; server and champion rates remain outside this bonus. */
public final class PremiumLootPolicy
{
	private static final double MAXIMUM_COMBINED_CHANCE_BONUS = 3;

	private PremiumLootPolicy()
	{
	}

	public static boolean eligible(Player player)
	{
		return PremiumSystemConfig.PREMIUM_SYSTEM_ENABLED && (player != null) && player.hasPremiumStatus() && !player.hasHeadlessOutboundSession() && !player.isNativeWorkManaged();
	}

	public static double itemMultiplier(boolean premium, boolean immediateEffect, int itemId, Map<Integer, Float> perItem, double ordinary)
	{
		return premium ? perItem.getOrDefault(itemId, immediateEffect ? 1f : (float) ordinary) : 1;
	}

	public static double combinedChance(double premium, double itemBonus)
	{
		return Math.min(MAXIMUM_COMBINED_CHANCE_BONUS, premium * itemBonus);
	}
}
