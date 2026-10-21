/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.combat;

public record PhantomCombatPolicy(int maximumSessions, int maximumSessionsPerPulse, int maximumThreatEntries, int maximumSelectedSkills, int maximumObservedAttackers, int maximumLootCandidates, int maximumRememberedLootIds, int maximumAcquisitionDistance, int maximumLootDistance, long pulseIntervalMillis, long defaultTimeoutMillis, long maximumTimeoutMillis, long lootTimeoutMillis, int lowHpPercent, int minimumMpReservePercent)
{
	public static final long MAXIMUM_TIMEOUT_MILLIS = 120_000;
	public static final int PVE_CROWD_RETREAT_HP_PERCENT = 70;
	public static final int PVE_RECOVERY_HP_PERCENT = 80;

	/** New encounters stay above crowd withdrawal; native rest retains its recovery hysteresis. */
	public static boolean pveResourcesReady(double hp, double maximumHp, double mp, double requiredMp, boolean recovering)
	{
		return maximumHp > 0 && Double.isFinite(maximumHp) && Double.isFinite(hp) && Double.isFinite(mp) && Double.isFinite(requiredMp) && requiredMp >= 0
			&& (recovering ? hp * 100 >= maximumHp * PVE_RECOVERY_HP_PERCENT : hp * 100 > maximumHp * PVE_CROWD_RETREAT_HP_PERCENT) && mp >= requiredMp;
	}

	public static boolean pveNoTargetExpired(boolean rewardedFarm, long absenceNanos, long initialWaitNanos, long usefulWaitNanos)
	{
		return absenceNanos >= (rewardedFarm ? usefulWaitNanos : initialWaitNanos);
	}

	public PhantomCombatPolicy
	{
		if ((maximumSessions < 1) || (maximumSessions > 1_000_000) || (maximumSessionsPerPulse != 64) || (maximumThreatEntries != 32) || (maximumSelectedSkills != 4) || (maximumObservedAttackers != 16) || (maximumLootCandidates != 32) || (maximumRememberedLootIds != 64) || (maximumAcquisitionDistance != 2000) || (maximumLootDistance < 200) || (maximumLootDistance > 1200) || (pulseIntervalMillis != 250) || (defaultTimeoutMillis != 30_000) || (maximumTimeoutMillis != MAXIMUM_TIMEOUT_MILLIS) || (lootTimeoutMillis != 5000) || (lowHpPercent != 15) || (minimumMpReservePercent != 10))
		{
			throw new IllegalArgumentException("Combat policy does not match the Goal 012 bounded contract.");
		}
	}

	public static PhantomCombatPolicy productionDefaults(int maximumSessions)
	{
		final int configured = org.l2jmobius.gameserver.config.custom.AutoPlayConfig.PHANTOM_PICKUP_RANGE;
		return new PhantomCombatPolicy(maximumSessions, 64, 32, 4, 16, 32, 64, 2000, configured >= 200 && configured <= 1200 ? configured : 600, 250, 30_000, MAXIMUM_TIMEOUT_MILLIS, 5000, 15, 10);
	}
}
