/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.commerce;

import java.util.List;

/** Synthetic opportunities exercise the production selector, not native players, stock or paid effects. */
public final class PhantomEarnedMarket119Checks
{
	public static void main(String[] args)
	{
		final var terms = new PhantomOfflineSupply.SaleTerms(10, 1000, 100);
		final var losing = new PhantomOfflineSupply.SaleChoice("test-only.losing", terms, 60_000, 1, 101);
		check(PhantomOfflineSupply.selectSale(List.of(losing)).isEmpty(), "A positive NPC premium admitted a trip losing more farm income");
		check(PhantomOfflineSupply.selectSale(List.of(new PhantomOfflineSupply.SaleChoice("test-only.break-even", terms, 60_000, 1, 100))).isEmpty(), "Break-even trip displaced productive farming");
		check(PhantomOfflineSupply.selectSale(List.of(new PhantomOfflineSupply.SaleChoice("test-only.unknown", terms, 60_000, 1, -1))).isEmpty(), "Unknown farm opportunity was treated as free");
		final var nearby = new PhantomOfflineSupply.SaleChoice("test-only.near", new PhantomOfflineSupply.SaleTerms(10, 1000, 60), 60_000, 2, 10);
		final var distant = new PhantomOfflineSupply.SaleChoice("test-only.distant", new PhantomOfflineSupply.SaleTerms(10, 1000, 100), 60_000, 3, 90);
		check(PhantomOfflineSupply.selectSale(List.of(distant, nearby, losing)).orElseThrow().equals(nearby), "Gross premium won over the better net productive alternative");
		check(PhantomOfflineSupply.selectSale(List.of()).isEmpty(), "Missing real offers generated a sale");
		System.out.println("TASK119_SELECTOR_PASS contrasts=5 TEST_ONLY_FACTS=true NATIVE_PAID=NOT_RUN");
	}
	private static void check(boolean value, String reason) { if (!value) { throw new AssertionError("TASK119 " + reason); } }
}
