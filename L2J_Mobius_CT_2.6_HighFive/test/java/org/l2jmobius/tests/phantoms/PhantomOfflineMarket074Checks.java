/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.util.List;

import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.item.ItemTemplate;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomOfflineSupply;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomSupplyIntentStore.Binding;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoal;
import org.l2jmobius.gameserver.phantoms.economy.PhantomSocialEconomyGoalSpec;

/** Read-only regression on already prepared OWN074 actors; never creates a seller, item or goal row. */
public final class PhantomOfflineMarket074Checks
{
	private PhantomOfflineMarket074Checks() { }

	public static void main(String[] args) throws Exception
	{
		final Class<?> quote;
		try { quote = Class.forName("org.l2jmobius.gameserver.phantoms.commerce.PhantomOfflineSupply$AcquisitionQuote"); }
		catch (ClassNotFoundException missing)
		{ throw new AssertionError("RED: offline supply has no complete purchase/travel/material cost quote for source comparison.", missing); }
		final var create = quote.getConstructor(long.class, long.class, long.class, long.class, double.class);
		final var unit = quote.getMethod("deliveredUnitCost");
		final Object farCheapUnit = create.newInstance(600L, 900L, 0L, 60L, 10000d);
		final Object nearDearUnit = create.newInstance(1200L, 0L, 0L, 60L, 100d);
		final Object manufacture = create.newInstance(10L, 100L, 1800L, 60L, 500d);
		final Object uncertainManufacture = quote.getConstructor(long.class, long.class, long.class, long.class, double.class, int.class).newInstance(10L, 100L, 1800L, 60L, 500d, 50);
		PhantomAssertions.assertTrue((double) unit.invoke(nearDearUnit) < (double) unit.invoke(farCheapUnit), "Cheaper unit price must lose when the physical journey costs more overall.");
		PhantomAssertions.assertEquals(1910d / 60, unit.invoke(manufacture), "Already owned recipe inputs must retain their declared value; craft is not free.");
		PhantomAssertions.assertEquals(1910d / 30, unit.invoke(uncertainManufacture), "Native success probability must affect expected acquired-unit cost.");
		final Object directCrystalBatch = create.newInstance(22600L, 0L, 0L, 450L, 100d);
		final Object dearGearBatch = create.newInstance(38600L, 0L, 0L, 450L, 100d);
		final Object cheapGearBatch = create.newInstance(17600L, 0L, 0L, 450L, 100d);
		PhantomAssertions.assertTrue((double) unit.invoke(directCrystalBatch) < (double) unit.invoke(dearGearBatch), "Surplus crystal output cannot discount an expensive whole gear purchase before sale.");
		PhantomAssertions.assertTrue((double) unit.invoke(cheapGearBatch) < (double) unit.invoke(directCrystalBatch), "Actual cheap listed gear price must remain a useful complete-batch alternative.");
		indexedAdmission(quote, create);
		System.out.println("OFFLINE074_COST_PASS; purchase+travel+material/native chance per acquired charge; DB_WRITES=0");
	}

	private static void indexedAdmission(Class<?> quote, java.lang.reflect.Constructor<?> create) throws Exception
	{
		try
		{
			World.class.getMethod("getPrivateStorePlayers", java.util.Set.class);
			World.class.getMethod("getPrivateStoreCrystalPlayers", int.class);
			PhantomOfflineSupply.class.getMethod("offer", Player.class, PhantomGoal.class, Binding.class, long.class,
				List.class, long.class, long.class, long.class, java.util.function.ToLongFunction.class, java.util.function.LongUnaryOperator.class, java.util.function.Predicate.class);
		}
		catch (NoSuchMethodException missing) { throw new AssertionError("RED: exact published-item index and pre-selection whole-product admission are required.", missing); }
		final var choice = Class.forName("org.l2jmobius.gameserver.phantoms.commerce.PhantomOfflineSupply$Choice");
		final var constructor = choice.getDeclaredConstructor(String.class, String.class, long.class, long.class, double.class, quote, String.class);
		constructor.setAccessible(true);
		final var select = PhantomOfflineSupply.class.getDeclaredMethod("bestChoice", List.class, java.util.function.Predicate.class);
		select.setAccessible(true);
		final var candidates = new java.util.ArrayList<Object>();
		for (int index = 1; index <= 3; index++)
		{
			candidates.add(constructor.newInstance("buy", "thin" + index, 1L, 1L, 1000d, create.newInstance(1L, 0L, 0L, 1L, 1000d), "need"));
		}
		// A component child buys54 ore, but admission must inspect its final450-shot output.
		final Object wholeProduct = constructor.newInstance("buy", "useful", 13500L, 54L, 100d, create.newInstance(22600L, 0L, 0L, 450L, 100d), "need");
		candidates.add(wholeProduct);
		final var policy = new org.l2jmobius.gameserver.phantoms.commerce.PhantomAutonomousSupplyPolicy(true, 3, 60, 500, 30, 8, 16);
		final java.util.function.Predicate<Object> useful = value ->
		{
			try { return policy.usefulPurchase(true, 0, (long) quote.getMethod("count").invoke(value), 1, (double) quote.getMethod("distance").invoke(value), 100); }
			catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
		};
		final var selected = (java.util.Optional<?>) select.invoke(null, candidates, useful);
		PhantomAssertions.assertTrue(selected.isPresent() && selected.orElseThrow() == wholeProduct, "Three cheaper thin stores must not hide the fourth useful whole-product alternative.");
		System.out.println("OFFLINE074_INDEX_ADMISSION_PASS; exact publication APIs; all alternatives admitted before cheapest selection; final product quantity; DB_WRITES=0");
	}

	public static void globalDiscovery(PhantomOfflineSupply supply, Player buyer, PhantomGoal root, Binding binding,
		ItemTemplate exactNeed, int expectedSeller, StringBuilder report)
	{
		final var owner = World.getInstance().getPlayer(expectedSeller);
		PhantomAssertions.assertTrue(owner != null && buyer.calculateDistance3D(owner) > 6000, "PREMEASUREMENT far discovery control required; do not move a measured actor.");
		final long beforeAdena = buyer.getAdena(), beforeStock = buyer.getInventory().getInventoryItemCount(exactNeed.getId(), -1);
		final var offer = supply.offer(buyer, root, binding, 1, List.of(exactNeed), 60, System.currentTimeMillis() + 480000, Long.MAX_VALUE, seller -> 0, fees -> Math.max(0, buyer.getAdena() - 500));
		PhantomAssertions.assertTrue(offer.isPresent(), "RED: exact matching offline supply beyond6000 must remain discoverable; movement still requires native navigation.");
		final var spec = PhantomOfflineSupply.spec(offer.orElseThrow());
		PhantomAssertions.assertEquals(expectedSeller, spec.counterpartyCharacterObjectId(), "Discovery selected a different seller than the declared exact B+ fixture.");
		PhantomAssertions.assertTrue(spec instanceof PhantomSocialEconomyGoalSpec.StoreBuy buy && buy.lines().stream().allMatch(line -> line.itemId() == exactNeed.getId()), "Discovery changed the requested item/grade.");
		PhantomAssertions.assertEquals(beforeAdena, buyer.getAdena(), "Discovery debited money before physical commerce.");
		PhantomAssertions.assertEquals(beforeStock, buyer.getInventory().getInventoryItemCount(exactNeed.getId(), -1), "Discovery granted items.");
		report.append("OFFLINE074_GLOBAL_DISCOVERY_GREEN\tbuyer=").append(buyer.getObjectId()).append(";seller=").append(expectedSeller).append(";distance=").append(buyer.calculateDistance3D(owner)).append(";item=").append(exactNeed.getId()).append(";nativeOffer=").append(offer.orElseThrow()).append(";PREMEASUREMENT_READ_ONLY;no route/arrival/payment claim\n");
	}
}
