/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.commerce;

import java.util.List;

import org.l2jmobius.gameserver.phantoms.background.PhantomVisibleFarmTravel.NativeWalkBudget;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomOfflineSupply.AcquisitionQuote;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationBackend;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationCapability;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPolicy;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationRoute;

/** Synthetic cost contrasts; these quotes never prove native stock, payments or equipment. */
public final class PhantomCareer121Checks
{
	public static void main(String[] args)
	{
		final var npc = new AcquisitionQuote(1200, 0, 0, 1, 100);
		check(PhantomOfflineSupply.preferEquipment(new AcquisitionQuote(900, 100, 0, 1, 200), npc), "Cheaper legal offline equipment was hidden by an available NPC offer");
		check(!PhantomOfflineSupply.preferEquipment(new AcquisitionQuote(1100, 101, 0, 1, 50), npc)
			&& !PhantomOfflineSupply.preferEquipment(new AcquisitionQuote(600, 0, 300, 1, 50, 60), npc), "Travel or native recipe risk/input value was ignored");
		check(PhantomOfflineSupply.preferEquipment(new AcquisitionQuote(1200, 0, 0, 1, 50), npc)
			&& !PhantomOfflineSupply.preferEquipment(new AcquisitionQuote(1200, 0, 0, 1, 200), npc)
			&& !PhantomOfflineSupply.preferEquipment(null, npc)
			&& PhantomOfflineSupply.preferEquipment(npc, null), "Distance tie or absent authoritative quote selected an invalid source");
		wholeWalkDeadline();
		System.out.println("TASK121_EQUIPMENT_SELECTOR_PASS groups=3;WHOLE_WALK_DEADLINE_PASS;SYNTHETIC=true;NATIVE_PAID=NOT_RUN");
	}
	private static void wholeWalkDeadline()
	{
		final var policy = PhantomNavigationPolicy.productionDefaults();
		final var origin = new PhantomNavigationPoint(0, 0, 0, 0);
		final var destination = new PhantomNavigationPoint(95000, 0, 0, 0);
		final var first = new PhantomNavigationPoint(1000, 0, 0, 0);
		final var capability = new PhantomNavigationBackend.CapabilitySnapshot(PhantomNavigationCapability.GEODATA_PATHFINDING, 1);
		final var route = new PhantomNavigationRoute(PhantomNavigationRoute.Mode.COMPUTED, origin, first, List.of(first), capability, 1, false, 1, policy.maximumRouteDistance());
		final var budget = new NativeWalkBudget(origin, destination, 1, 125, policy);
		check(L2jPhantomSupplyJourney.nativeWalkPlan(route, origin, destination, 125, 475391, budget, 2) == null, "A short checkpoint hid an impossible whole WALK behind the original trip deadline");
		check(budget.visited().size() == 1, "Rejected whole WALK consumed the existing checkpoint budget");
		check(L2jPhantomSupplyJourney.nativeWalkPlan(route, origin, destination, 125, 800000, budget, 2) != null, "A feasible whole WALK lost its original bounded checkpoint plan");
		check(L2jPhantomSupplyJourney.walkFitsRemaining(origin, first, 100, 10000) && !L2jPhantomSupplyJourney.walkFitsRemaining(origin, first, 100, 9999), "Whole WALK changed the exact remaining-time boundary");
		check(!L2jPhantomSupplyJourney.walkFitsRemaining(origin, first, 0, 10000) && !L2jPhantomSupplyJourney.walkFitsRemaining(origin, first, Double.NaN, 10000)
			&& !L2jPhantomSupplyJourney.walkFitsRemaining(origin, new PhantomNavigationPoint(first.x(), first.y(), first.z(), 1), 100, 10000), "Invalid speed or another instance admitted a whole WALK");
	}
	private static void check(boolean value, String reason) { if (!value) { throw new AssertionError("TASK121 " + reason); } }
}
