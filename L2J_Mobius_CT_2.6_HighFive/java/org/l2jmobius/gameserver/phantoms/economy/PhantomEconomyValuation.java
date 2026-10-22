/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.economy;

/** Shared explicit author policy over stock source facts, used by runtime and the offline CLI. */
public final class PhantomEconomyValuation
{
	private PhantomEconomyValuation() { }

	public record Policy(double targetMinutes, double killsPerMinute, double consumableCostPerKill, int scarcityBasisPoints, int epicPremiumBasisPoints, long manufactureMinimum, int manufactureBasisPoints)
	{
		public Policy
		{
			if (!finite(targetMinutes, 0, 100000) || !finite(killsPerMinute, 0.01, 1000) || !finite(consumableCostPerKill, 0, 1e12) || scarcityBasisPoints < 10000 || scarcityBasisPoints > 1000000 || epicPremiumBasisPoints <= 10000 || epicPremiumBasisPoints > 1000000 || manufactureMinimum < 1 || manufactureMinimum > 1_000_000_000L || manufactureBasisPoints < 1 || manufactureBasisPoints > 10000)
			{
				throw new IllegalArgumentException("Invalid explicit economy time/scarcity/service policy.");
			}
		}
		public static Policy defaults() { return new Policy(30, 4, 0, 10000, 50000, 100, 200); }
	}

	public record Source(double fair, String reason) { }

	public static Source source(long liquidation, boolean equipment, boolean epic, double ordinaryHighJewelAnchor, PhantomNpcAcquisitionPrice.Quote npc, PhantomRateAwareLootFairValue.Quote loot, Policy policy)
	{
		if (npc == null || policy == null || liquidation < 0 || npc.exists() && npc.exact() && npc.price() == 0) { throw new IllegalArgumentException("Unpriced: absent policy or ordinary free NPC source."); }
		if (epic && (loot == null || ordinaryHighJewelAnchor <= 0)) { throw new IllegalArgumentException("Unpriced: epic boss source or ordinary high-grade jewel anchor absent."); }
		double fair = loot == null ? 0 : loot.fairValue() * policy.scarcityBasisPoints() / 10000d;
		String reason = loot == null ? "liquidation.opportunity_only" : "loot." + loot.npcId() + "." + loot.source();
		if (loot != null && equipment)
		{
			final double budget = Math.max(0, loot.adenaPerKill() - policy.consumableCostPerKill()) * policy.killsPerMinute() * policy.targetMinutes();
			if (budget > fair) { fair = budget; reason += "+author.target_time"; }
		}
		if (epic)
		{
			fair = Math.max(fair, ordinaryHighJewelAnchor * policy.epicPremiumBasisPoints() / 10000d);
			reason += "+author.epic_premium";
		}
		else if (npc.exists() && npc.exact() && npc.price() > 0 && (fair <= 0 || npc.price() < fair))
		{
			fair = npc.price(); reason = npc.reason();
		}
		fair = Math.max(fair, liquidation);
		if (!finite(fair, 1, 1e15)) { throw new IllegalArgumentException("Unpriced: no bounded source value."); }
		return new Source(fair, reason);
	}

	public static long manufactureFee(double inputsPerAttempt, Policy policy, long maxAdena)
	{
		if (!finite(inputsPerAttempt, 0.000001, 1e15) || policy == null) { throw new IllegalArgumentException("Unpriced manufacture inputs."); }
		final double value = Math.max(policy.manufactureMinimum(), Math.ceil(inputsPerAttempt * policy.manufactureBasisPoints() / 10000d));
		if (!finite(value, 1, maxAdena)) { throw new IllegalArgumentException("Manufacture fee exceeds Adena boundary."); }
		return (long) value;
	}

	public static long liquidationFloor(long npcPayout, long crystalCount, long crystalNpcPayout)
	{
		if (npcPayout < 0 || crystalCount < 0 || crystalNpcPayout < 0) { throw new IllegalArgumentException("Negative liquidation input."); }
		return Math.max(npcPayout, Math.multiplyExact(crystalCount, crystalNpcPayout));
	}

	private static boolean finite(double value, double minimum, double maximum) { return Double.isFinite(value) && value >= minimum && value <= maximum; }
}
