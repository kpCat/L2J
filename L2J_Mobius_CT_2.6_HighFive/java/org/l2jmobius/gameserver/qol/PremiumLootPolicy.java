/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.qol;

import java.util.Map;

import org.l2jmobius.gameserver.config.custom.PremiumSystemConfig;
import org.l2jmobius.gameserver.model.actor.Player;

/** Shared premium selection for grouped/ungrouped loot; server and champion rates remain outside this bonus. */
public final class PremiumLootPolicy
{
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
		final double cap = PremiumSystemConfig.PREMIUM_MAXIMUM_COMBINED_CHANCE_BONUS;
		return Math.min(Double.isFinite(cap) && cap >= 1 && cap <= 100 ? cap : 1, premium * itemBonus);
	}
}
