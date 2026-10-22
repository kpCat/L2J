/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyValuation;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyValuation.Policy;
import org.l2jmobius.gameserver.phantoms.economy.PhantomNpcAcquisitionPrice;
import org.l2jmobius.gameserver.phantoms.economy.PhantomRateAwareLootFairValue;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.DropSourceKind;

/** Shared author policy boundaries; raw loot/recipe arithmetic remains covered by existing suites. */
public final class PhantomEconomy068PricingSuite implements PhantomTestSuite
{
	@Override public String id() { return "economy068-pricing"; }
	@Override public void register(PhantomTestRegistry registry)
	{
		registry.add("01-rate-snapshot-budget-and-npc-ceiling", context ->
		{
			final Policy policy = new Policy(10, 4, 5, 10000, 50000, 100, 200);
			final var absent = new PhantomNpcAcquisitionPrice.Quote(false, true, 0, "absent");
			final var x10 = new PhantomRateAwareLootFairValue.Quote(100, 200, DropSourceKind.DEATH_DROP, 1000, 2, 500);
			final var x20 = new PhantomRateAwareLootFairValue.Quote(100, 200, DropSourceKind.DEATH_DROP, 2000, 4, 500);
			PhantomAssertions.assertEquals(39800d, PhantomEconomyValuation.source(0, true, false, 0, absent, x10, policy).fair(), "Target budget ignored actual Adena yield and costs.");
			PhantomAssertions.assertEquals(79800d, PhantomEconomyValuation.source(0, true, false, 0, absent, x20, policy).fair(), "x20 snapshot did not change earned target budget.");
			PhantomAssertions.assertEquals(500d, PhantomEconomyValuation.source(0, false, false, 0, absent, x20, policy).fair(), "Materials received a blanket rate multiplier.");
			PhantomAssertions.assertEquals(600d, PhantomEconomyValuation.source(0, true, false, 0, new PhantomNpcAcquisitionPrice.Quote(true, true, 600, "npc"), x10, policy).fair(), "Real repeatable NPC acquisition was ignored.");
		});
		registry.add("02-epic-independent-of-grade-and-no-fallback", context ->
		{
			final var absent = new PhantomNpcAcquisitionPrice.Quote(false, true, 0, "absent");
			PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> PhantomEconomyValuation.source(308000, true, true, 10_000_000, absent, null, Policy.defaults()), "An epic with no boss source received its ordinary NPC price.");
			final var boss = new PhantomRateAwareLootFairValue.Quote(6660, 29001, DropSourceKind.DEATH_DROP, 135000, 0.3, 450000);
			PhantomAssertions.assertTrue(PhantomEconomyValuation.source(308000, true, true, 10_000_000, absent, boss, Policy.defaults()).fair() > 10_000_000, "B-grade Queen Ant did not exceed the ordinary high-grade jewel anchor.");
		});
		registry.add("03-positive-fee-rounding-and-overflow-fences", context ->
		{
			final Policy policy = Policy.defaults();
			PhantomAssertions.assertEquals(100L, PhantomEconomyValuation.manufactureFee(1, policy, 100000), "Minimum fee was lost.");
			PhantomAssertions.assertEquals(201L, PhantomEconomyValuation.manufactureFee(10001, policy, 100000), "Fee rounded down.");
			PhantomAssertions.assertEquals(800L, PhantomEconomyValuation.liquidationFloor(100, 20, 40), "Crystal recovery was omitted from the closed-cycle payout floor.");
			PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> PhantomEconomyValuation.manufactureFee(Double.MAX_VALUE, policy, 100000), "Overflowing BOM produced a fee.");
			PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> new Policy(-1, 1, 0, 10000, 50000, 100, 200), "Negative time budget accepted.");
		});
	}
}
