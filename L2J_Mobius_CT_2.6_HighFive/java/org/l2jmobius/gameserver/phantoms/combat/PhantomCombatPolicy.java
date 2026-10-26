/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.combat;

public record PhantomCombatPolicy(int maximumSessions, int maximumSessionsPerPulse, int maximumThreatEntries, int maximumSelectedSkills, int maximumObservedAttackers, int maximumLootCandidates, int maximumRememberedLootIds, int maximumAcquisitionDistance, int maximumLootDistance, long pulseIntervalMillis, long defaultTimeoutMillis, long maximumTimeoutMillis, long lootTimeoutMillis, int lowHpPercent, int minimumMpReservePercent)
{
	public static final long MAXIMUM_TIMEOUT_MILLIS = 120_000;
	public static final int PVE_CROWD_RETREAT_HP_PERCENT = 70;
	public static final int PVE_RECOVERY_HP_PERCENT = 80;

	/** Same strict level gate as Attackable.calculateExpAndSp; a pending kill alone is not evidence of profitable input. */
	public static boolean farmRewardAvailable(int levelDifference, int maximumDifference, long exp, int sp)
	{
		return maximumDifference > 0 && levelDifference < maximumDifference && levelDifference > -maximumDifference && (exp > 0 || sp > 0);
	}

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

	public enum PveRecoveryAction { ALLOW, WAIT_NATIVE, RETREAT, REST, EXPIRED }
	public enum PvePreparationAction { ALLOW, WAIT_NATIVE, RECOVER, MAINTAIN_SUPPLIES, SAFER_FARM_OR_OTHER_ACTIVITY }
	public record PvePreparation(int level, double hpPercent, double mpPercent, boolean weaponReady, boolean magicPrimary,
		boolean primaryMpReady, long shotHits, long healingStock, boolean activeBuff, boolean supportPet, boolean party, boolean funds) { }
	public record PvePreparationVerdict(PvePreparationAction action, String reason) { }

	/** Preferences over actual native inputs; support presence never supplies damage or future buff credit. */
	public static PvePreparationVerdict pvePreparation(PvePreparation facts, boolean viable, boolean risky, boolean busy)
	{
		if (busy) { return new PvePreparationVerdict(PvePreparationAction.WAIT_NATIVE, "readiness.native_inflight"); }
		if (facts == null || facts.level() < 1 || !Double.isFinite(facts.hpPercent()) || !Double.isFinite(facts.mpPercent()) || !viable)
		{ return new PvePreparationVerdict(PvePreparationAction.SAFER_FARM_OR_OTHER_ACTIVITY, "readiness.native_target_or_facts_unavailable"); }
		if (facts.hpPercent() <= PVE_CROWD_RETREAT_HP_PERCENT || facts.magicPrimary() && !facts.primaryMpReady())
		{ return new PvePreparationVerdict(PvePreparationAction.RECOVER, "readiness.native_hp_mp"); }
		if (!facts.party() && risky && facts.level() >= 40)
		{
			if (!facts.weaponReady() || facts.shotHits() < (facts.level() >= 76 ? 10 : 3))
			{ return new PvePreparationVerdict(facts.funds() ? PvePreparationAction.MAINTAIN_SUPPLIES : PvePreparationAction.SAFER_FARM_OR_OTHER_ACTIVITY, "readiness.gear_or_shots.existing_supply_required"); }
			if (facts.level() >= 76 && (!facts.activeBuff() || facts.hpPercent() < 90 || facts.magicPrimary() && facts.mpPercent() < 25 || facts.healingStock() == 0))
			{ return new PvePreparationVerdict(PvePreparationAction.SAFER_FARM_OR_OTHER_ACTIVITY, "readiness.high_solo.native_support_or_resources_missing"); }
		}
		return new PvePreparationVerdict(PvePreparationAction.ALLOW, risky ? "readiness.native_prepared" : "readiness.native_lower_risk");
	}

	public static boolean pveEarnedContinuation(boolean pending, boolean threatened, boolean nativeBusy)
	{
		return pending && !threatened && !nativeBusy;
	}

	public static double pveRecoveryMpCost(double cost, double maximumMp, boolean recovering)
	{
		return recovering ? Math.min(maximumMp, cost * 2) : cost;
	}

	public static PveRecoveryAction pveRecoveryAction(boolean resourcesReady, boolean viable, boolean threatened, boolean nativeBusy, boolean expired)
	{
		if (resourcesReady && viable) { return PveRecoveryAction.ALLOW; }
		if (nativeBusy) { return PveRecoveryAction.WAIT_NATIVE; }
		if (expired || !viable && !threatened) { return PveRecoveryAction.EXPIRED; }
		return threatened ? PveRecoveryAction.RETREAT : PveRecoveryAction.REST;
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
