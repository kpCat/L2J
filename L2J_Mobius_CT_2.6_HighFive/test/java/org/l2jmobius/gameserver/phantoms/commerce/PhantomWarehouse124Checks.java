/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.commerce;

import java.lang.reflect.Method;
import java.util.Map;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.data.xml.RecipeData;

/** Native recipe quantities and bounded warehouse planning; no native payment claimed. */
public final class PhantomWarehouse124Checks
{
	public static void main(String[] args) throws Exception
	{
		check(!DatabaseFactory.isInitialized(), "DB-free regression adopted JDBC");
		final var recipe = RecipeData.getInstance().getRecipeList(20);
		check(recipe != null && recipe.getSuccessRate() == 100 && recipe.getItemId() == 1463 && recipe.getCount() == 468, "Native D-shot recipe changed");
		final Method deficits;
		try { deficits = PhantomOfflineSupply.class.getDeclaredMethod("ingredientDeficits", recipe.getClass(), Map.class); }
		catch (NoSuchMethodException missing) { throw new AssertionError("TASK124 native recipe BOM has no quantity-based warehouse demand path", missing); }
		deficits.setAccessible(true);
		check(deficits.invoke(null, recipe, Map.of(1785, 4L)).equals(Map.of(1785, 5L, 1458, 3L)), "Wrong full native BOM deficit");
		check(deficits.invoke(null, recipe, Map.of(1785, 9L, 1458, 3L)).equals(Map.of()), "Complete BOM requested another warehouse withdrawal");
		check(deficits.invoke(null, recipe, Map.of(1785, 10L, 1458, Long.MAX_VALUE)).equals(Map.of()), "Surplus/large owned count underflowed");
		check(PhantomOfflineSupply.warehouseTrips(java.util.List.of(9L, 3L)) == 1 && PhantomOfflineSupply.warehouseTrips(java.util.List.of(1200L)) == 2, "Bank quotation charged each input or hid repeated600 batches");
		check(PhantomOfflineSupply.warehouseTrips(java.util.List.of(600L, 600L, 1800L)) == 4 && PhantomOfflineSupply.warehouseTrips(java.util.List.of(1800L, 600L, 600L)) == 3, "Bank quotation ignored exact object ordering/two-object packing");
		final Method count = L2jPhantomSupplyJourney.class.getDeclaredMethod("warehouseWithdrawalCount", long.class, long.class, int.class, long.class);
		count.setAccessible(true);
		check(count.invoke(null, 20L, 9L, 5, 30L).equals(6L), "Warehouse request ignored actual aggregate weight");
		check(count.invoke(null, 1000L, 1000L, 0, 0L).equals(600L), "Warehouse request escaped native600 bound");
		check(count.invoke(null, 3L, 9L, 5, 100L).equals(3L) && count.invoke(null, 20L, 0L, 5, 100L).equals(0L), "Missing/resolved native stock invented demand");
		check(count.invoke(null, 20L, 9L, 5, 4L).equals(0L) && count.invoke(null, 20L, 9L, -1, 100L).equals(0L), "Invalid/overweight input admitted");
		final long now = System.currentTimeMillis();
		final var root = new org.l2jmobius.gameserver.phantoms.decision.PhantomGoal(124, "farm.background", org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus.ACTIVE,
			new org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef("profile", "1"), new org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef("npc", "20205"), 1, 0, "background.farm", java.util.List.of(), null, "farm.background", 500, 0, 0, 0, Map.of(), "fixture124.root", 1);
		final var binding = new PhantomSupplyIntentStore.Binding(1, 111, 1, 0, 222, new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint(1, 2, 3, 0), 0);
		final var operations = java.util.List.of(
			new PhantomInventoryMaintenanceIntent.Operation(PhantomCommerceReceipt.OperationKind.SELL, 30180, 333, 3018000, 21, 444, 1),
			new PhantomInventoryMaintenanceIntent.Operation(PhantomCommerceReceipt.OperationKind.WAREHOUSE_DEPOSIT, 30190, 334, 0, 1871, 445, 600),
			new PhantomInventoryMaintenanceIntent.Operation(PhantomCommerceReceipt.OperationKind.WAREHOUSE_WITHDRAW, 30190, 334, 0, 1785, 446, 9),
			new PhantomInventoryMaintenanceIntent.Operation(PhantomCommerceReceipt.OperationKind.WAREHOUSE_WITHDRAW, 30190, 334, 0, 1458, 447, 3));
		var intent = PhantomInventoryMaintenanceIntent.create(root, binding, operations, 2, 30000, now + 60000, now, false);
		final var codec = new org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateCodec();
		for (int index = 0; index < operations.size(); index++)
		{
			intent = codec.decode(codec.encode(intent));
			check(PhantomInventoryMaintenanceIntent.parse(intent).current().equals(operations.get(index)), "Restart skipped or duplicated an exact BOM bank object");
			intent = PhantomInventoryMaintenanceIntent.advance(intent, new PhantomInventoryMaintenanceIntent.Spec(operations, index + 1, false, false), 30000, "fixture124.confirmed");
		}
		check(PhantomInventoryMaintenanceIntent.parse(intent).returning() && intent.deadlineEpochMillis() == now + 60000 && PhantomSupplyIntentStore.bound(intent, root), "Four operations changed original root/field/deadline");
		final var request = new PhantomCommerceReceipt.OperationRequest(PhantomCommerceReceipt.OperationKind.WAREHOUSE_WITHDRAW, 30190, 334, 0, 1785, 446, 9, 0, 0, 0, 0, "", 0, 0, 0);
		final var before = new PhantomCommerceReceipt.ConservationFacts(30000, 0, 1, 0, 1, 2, 3);
		final var prepared = PhantomCommerceReceipt.prepared(1, 124, 2, request, before, new PhantomCommerceReceipt.ConservationFacts(30000, 9, 1, 0, 1, 2, 3));
		check(L2jPhantomSupplyJourney.retreatReceiptSettled(null) && !L2jPhantomSupplyJourney.retreatReceiptSettled(prepared) && !L2jPhantomSupplyJourney.retreatReceiptSettled(prepared.withState(PhantomCommerceReceipt.State.COMMITTING)), "Survival moved an actor with unsettled native effects");
		check(L2jPhantomSupplyJourney.retreatReceiptSettled(prepared.withState(PhantomCommerceReceipt.State.ABORTED)) && !L2jPhantomSupplyJourney.retreatReceiptSettled(prepared.withState(PhantomCommerceReceipt.State.INCONSISTENT)), "Survival ignored fail-stop or refused a settled no-effect operation");
		check(!DatabaseFactory.isInitialized(), "Planning initialized JDBC");
		final var recovery = org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundService.class.getDeclaredMethod("nativeRecoveryDestination", org.l2jmobius.gameserver.model.Location.class, org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.Position.class);
		recovery.setAccessible(true);
		final var factory = javax.xml.parsers.DocumentBuilderFactory.newInstance(); factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
		final var xml = factory.newDocumentBuilder().parse(java.nio.file.Path.of("data/mapregion/dion_castle_town.xml").toFile());
		final var spawn = (org.w3c.dom.Element) xml.getElementsByTagName("respawnPoint").item(0);
		final var town = new org.l2jmobius.gameserver.model.Location(Integer.parseInt(spawn.getAttribute("X")), Integer.parseInt(spawn.getAttribute("Y")), Integer.parseInt(spawn.getAttribute("Z")));
		check(recovery.invoke(null, town, null) == town, "A real native Dion TOWN landing was rejected solely for absent topology projection");
		final var projected = new org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.Position(0, 1, 2, 3, 4, "fixture124.canonical");
		final var chosen = (org.l2jmobius.gameserver.model.Location) recovery.invoke(null, town, projected);
		check(chosen.getX() == 1 && chosen.getY() == 2 && chosen.getZ() == 3 && chosen.getHeading() == 4 && recovery.invoke(null, null, projected) == null, "Existing canonical preference or absent-native refusal changed");
		check(!DatabaseFactory.isInitialized(), "Native recovery selector initialized JDBC");
		System.out.println("TASK124_WAREHOUSE_PASS recipe20=9+3->468; quantity/weight/bounds/no-repeat/four-operation-restart; DB=false;NATIVE_PAID=NOT_RUN");
	}
	private static void check(boolean value, String reason) { if (!value) { throw new AssertionError("TASK124 " + reason); } }
}
