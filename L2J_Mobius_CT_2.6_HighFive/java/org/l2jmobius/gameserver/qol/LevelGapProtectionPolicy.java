/*
 * This file is part of the L2J Mobius project.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.l2jmobius.gameserver.qol;

/**
 * Pure policy for neutralizing only the native drop/spoil level-gap gate.
 */
public final class LevelGapProtectionPolicy
{
	private LevelGapProtectionPolicy()
	{
	}

	public static Decision decide(boolean featureEnabled, boolean actorEligible, int maximumGap, int victimLevel, int rewardActorLevel)
	{
		final long signedGap = (long) victimLevel - rewardActorLevel;
		final long absoluteGap = Math.abs(signedGap);
		final boolean protectedGap = featureEnabled && actorEligible && (maximumGap > 0) && (absoluteGap <= maximumGap);
		return new Decision(signedGap, absoluteGap, Math.max(0, maximumGap), protectedGap);
	}

	public static double apply(double nativeChance, Decision decision)
	{
		return decision.protectedGap() ? Math.max(nativeChance, 100d) : nativeChance;
	}

	public record Decision(long signedGap, long absoluteGap, int maximumGap, boolean protectedGap)
	{
	}
}
