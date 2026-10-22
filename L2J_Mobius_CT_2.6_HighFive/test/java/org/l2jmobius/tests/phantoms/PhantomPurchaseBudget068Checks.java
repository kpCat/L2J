/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import org.l2jmobius.gameserver.phantoms.commerce.PhantomAutonomousSupplyPolicy;

/** Shared NPC/private purchase budget contract; no DB or synthetic inventory. */
public final class PhantomPurchaseBudget068Checks
{
	public static void main(String[] args)
	{
		final var policy = new PhantomAutonomousSupplyPolicy(true, 3, 30, 500, 30, 5, 16);
		PhantomAssertions.assertEquals(75L, policy.availableBudget(100, 0, 0, true), "Desired reserve locked a poor actor out of productive supplies.");
		PhantomAssertions.assertEquals(60L, policy.availableBudget(100, 20, 0, true), "Mandatory route fee was not protected before discretionary reserve.");
		PhantomAssertions.assertEquals(45L, policy.availableBudget(100, 20, 20, true), "Equipment earmark was counted twice or consumed.");
		PhantomAssertions.assertEquals(0L, policy.availableBudget(0, 0, 0, true), "Zero cash manufactured a budget.");
		PhantomAssertions.assertEquals(0L, policy.availableBudget(100, 101, 0, true), "Unaffordable mandatory fee was ignored.");
		PhantomAssertions.assertEquals(0L, policy.availableBudget(100, 80, 21, true), "Earmark overflow was ignored.");
		PhantomAssertions.assertEquals(100L, policy.availableBudget(600, 0, 0, false), "Equipment spent the full supply reserve.");
		PhantomAssertions.assertEquals(0L, policy.availableBudget(Long.MAX_VALUE, Long.MAX_VALUE, 1, true), "Long budget overflowed.");
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> policy.availableBudget(-1, 0, 0, true), "Negative cash accepted.");
		PhantomAssertions.assertTrue(policy.usefulPurchase(true, 0, 1, 1, 100, 100), "Nearby useful partial purchase was rejected.");
		PhantomAssertions.assertFalse(policy.usefulPurchase(true, 0, 1, 1, 2000, 100), "Unproductive long round trip admitted for one hit.");
		System.out.println("OWN068_PURCHASE_BUDGET_PASS cases=3; DB_WRITES=0");
	}
}
