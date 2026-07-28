/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.combat;

public record PhantomCombatPolicy(int maximumSessions, int maximumSessionsPerPulse, int maximumThreatEntries, int maximumSelectedSkills, int maximumObservedAttackers, int maximumLootCandidates, int maximumRememberedLootIds, int maximumAcquisitionDistance, int maximumLootDistance, long pulseIntervalMillis, long defaultTimeoutMillis, long maximumTimeoutMillis, long lootTimeoutMillis, int lowHpPercent, int minimumMpReservePercent)
{
	public static final long MAXIMUM_TIMEOUT_MILLIS = 120_000;

	public PhantomCombatPolicy
	{
		if ((maximumSessions < 1) || (maximumSessions > 1_000_000) || (maximumSessionsPerPulse != 64) || (maximumThreatEntries != 32) || (maximumSelectedSkills != 4) || (maximumObservedAttackers != 16) || (maximumLootCandidates != 32) || (maximumRememberedLootIds != 64) || (maximumAcquisitionDistance != 2000) || (maximumLootDistance != 300) || (pulseIntervalMillis != 250) || (defaultTimeoutMillis != 30_000) || (maximumTimeoutMillis != MAXIMUM_TIMEOUT_MILLIS) || (lootTimeoutMillis != 5000) || (lowHpPercent != 15) || (minimumMpReservePercent != 10))
		{
			throw new IllegalArgumentException("Combat policy does not match the Goal 012 bounded contract.");
		}
	}

	public static PhantomCombatPolicy productionDefaults(int maximumSessions)
	{
		return new PhantomCombatPolicy(maximumSessions, 64, 32, 4, 16, 32, 64, 2000, 300, 250, 30_000, MAXIMUM_TIMEOUT_MILLIS, 5000, 15, 10);
	}
}
