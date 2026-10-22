/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.background;

import java.util.List;

import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint;
import org.l2jmobius.tests.phantoms.PhantomAssertions;
import org.l2jmobius.tests.phantoms.PhantomTestRegistry;
import org.l2jmobius.tests.phantoms.PhantomTestSuite;

/** Pure ordinary itinerary proposals; native movement/checkpoint effects remain in their owners. */
public final class PhantomVisibleItinerary068Checks implements PhantomTestSuite
{
	@Override public String id() { return "visible-itinerary068"; }
	@Override public void register(PhantomTestRegistry registry)
	{
		registry.add("01-canonical-small-farm-standpoint", _ -> canonicalFarm());
		registry.add("02-composite-route-uses-first-leg-and-full-native-fees", _ -> itinerary());
		registry.add("03-off-anchor-background-return-retains-current-lineage", _ -> localReturn());
		registry.add("04-native-current-anchor-return-is-only-a-bounded-walk-proposal", _ -> nativeReturn());
	}
	public static void main(String[] args)
	{
		if (args.length > 0 && args[0].equals("local-return")) { localReturn(); System.out.println("OWN068_LOCAL_RETURN_PASS; DB_CONNECTIONS=0; ACTORS=0"); return; }
		canonicalFarm();
		itinerary();
		localReturn();
		nativeReturn();
		System.out.println("OWN068_VISIBLE_ITINERARY_PASS; DB_CONNECTIONS=0; ACTORS=0");
	}
	private static void nativeReturn()
	{
		final var current = new PhantomBackgroundState.Position(0, 10_000, 10_000, 0, 0, "current");
		final var next = new PhantomNormalGatekeeperTravel.Step(PhantomNormalGatekeeperTravel.Type.TOPOLOGY_BACKGROUND, "next", "current", "final", 1, null);
		final var route = List.of(next);
		for (int distance : List.of(0, 4096, 4097, 100_000))
		{
			final var live = new org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint(current.x() + distance, current.y(), current.z(), 0);
			final var step = PhantomVisibleFarmTravel.resumeStep(route, null, current, live, "final");
			if (distance > 4096)
			{
				PhantomAssertions.assertEquals(null, step, "An intended lineage outside the existing local-return bound cannot admit native travel.");
				continue;
			}
			PhantomAssertions.assertTrue(step != null, "An off-anchor native actor needs a bounded return proposal before its original itinerary can resume.");
			PhantomAssertions.assertEquals("current", step.fromAnchorId(), "A return preserves the committed lineage.");
			PhantomAssertions.assertEquals("current", step.toAnchorId(), "A return cannot claim the distant final goal or skip its factual checkpoint.");
			PhantomAssertions.assertTrue(step.id().startsWith("live.approach."), "The existing live approach worker owns the return.");
			PhantomAssertions.assertEquals(PhantomNormalGatekeeperTravel.Type.TOPOLOGY_BACKGROUND, step.type(), "The proposal cannot itself perform or charge a gatekeeper transition.");
		}
		final var live = new org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint(10_100, 10_000, 0, 0);
		final var otherInstance = new org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint(live.x(), live.y(), live.z(), 1);
		PhantomAssertions.assertEquals(null, PhantomVisibleFarmTravel.resumeStep(route, null, current, otherInstance, "final"), "A local return cannot cross instances.");
		PhantomAssertions.assertEquals(null, PhantomVisibleFarmTravel.resumeStep(route, null, null, live, "final"), "A return requires a proven canonical committed point.");
		PhantomAssertions.assertEquals(null, PhantomVisibleFarmTravel.resumeStep(List.of(), null, current, live, "final"), "A return cannot hide a missing subsequent itinerary.");
		PhantomAssertions.assertEquals(null, PhantomVisibleFarmTravel.resumeStep(route, "foreign", current, live, "final"), "An actual but disconnected departure cannot be replaced with lineage proximity.");
		PhantomAssertions.assertEquals(next, PhantomVisibleFarmTravel.resumeStep(route, "current", current, live, "final"), "An already factual departure retains its original first itinerary leg.");
	}
	private static void localReturn()
	{
		final var before = new PhantomBackgroundState.Position(0, 115080, -178168, -880, 0, "current");
		final var current = new PhantomBackgroundState.Position(0, 116056, -175091, -1016, 0, "current");
		final var destination = new PhantomBackgroundState.Position(0, -33539, 137701, -3480, 0, "final");
		final var clock = new PhantomBackgroundState.Clock(123, 0, 7);
		final var policy = new PhantomBackgroundSimulationPolicy(2, 20, 1, 1, true, false, true, true, false, 1, 1, 0.6, 1.5, 2, 2.5, 3, 0, 0, 3.5, 3.5, "0".repeat(64), 125.35, 60_000, 0);
		final java.util.function.Predicate<PhantomBackgroundState.Position> dry = target -> Math.hypot((long) target.x() - before.x(), (long) target.y() - before.y()) <= 4096;
		for (long budget : List.of(10_000L, 25_753L, 60_000L))
		{
			final var result = L2jPhantomBackgroundAuthority.localReturn(before, clock, current, destination, budget, policy, dry);
			PhantomAssertions.assertEquals(budget < 25_753 ? PhantomBackgroundAuthority.TravelAdvance.Status.PARTIAL : PhantomBackgroundAuthority.TravelAdvance.Status.ARRIVED, result.status(), "Off-anchor TRAVEL must first return to its current lineage, rather than reject a distant final goal.");
			PhantomAssertions.assertEquals(budget < 25_753 ? before : current, result.position(), "A partial return cannot award a new position; completion retains the current anchor, never the final goal.");
			PhantomAssertions.assertEquals(budget < 25_753 ? 25_753L - budget : 0L, result.clock().residualTravelMillis(), "Elapsed scalar travel is charged once with the unchanged residual budget.");
			PhantomAssertions.assertEquals(123L, result.clock().rngState(), "Travel cannot award a new RNG outcome.");
			PhantomAssertions.assertEquals(7L, result.clock().residualEncounterMillis(), "Travel cannot consume or create encounter time.");
			PhantomAssertions.assertEquals(0L, result.feeAdena(), "A dry local return cannot charge or grant a gatekeeper fee.");
		}
		final var blocked = L2jPhantomBackgroundAuthority.localReturn(before, clock, current, destination, 60_000, policy, _ -> false);
		PhantomAssertions.assertEquals(PhantomBackgroundAuthority.TravelAdvance.Status.NO_ROUTE, blocked.status(), "A rejected native dry path remains fenced.");
		PhantomAssertions.assertEquals(before, blocked.position(), "Rejected dry proof cannot change position.");
		PhantomAssertions.assertEquals(clock, blocked.clock(), "Rejected dry proof cannot consume time.");
		final var partial = L2jPhantomBackgroundAuthority.localReturn(before, clock, current, destination, 10_000, policy, dry);
		final var resumed = L2jPhantomBackgroundAuthority.localReturn(before, partial.clock(), current, destination, 15_753, policy, dry);
		PhantomAssertions.assertEquals(PhantomBackgroundAuthority.TravelAdvance.Status.ARRIVED, resumed.status(), "A later interval consumes only the remaining travel time.");
		PhantomAssertions.assertEquals(current, resumed.position(), "Residual completion retains the intermediate lineage.");
		PhantomAssertions.assertEquals(0L, resumed.clock().residualTravelMillis(), "The completed return cannot leave reusable travel credit.");
		final var same = L2jPhantomBackgroundAuthority.localReturn(before, clock, current, current, 60_000, policy, dry);
		PhantomAssertions.assertEquals(current, same.position(), "The existing same-final-anchor local return remains supported.");
	}
	private static void itinerary()
	{
		final var first = new PhantomNormalGatekeeperTravel.Step(PhantomNormalGatekeeperTravel.Type.TOPOLOGY_BACKGROUND, "walk.ingress", "start", "gk.a", 1, null);
		final var second = new PhantomNormalGatekeeperTravel.Step(PhantomNormalGatekeeperTravel.Type.NORMAL_GATEKEEPER, "gk.a-b", "gk.a", "gk.b", 1, leg("a-b", "gk.a", "gk.b", 32_000));
		final var third = new PhantomNormalGatekeeperTravel.Step(PhantomNormalGatekeeperTravel.Type.NORMAL_GATEKEEPER, "gk.b-c", "gk.b", "gk.c", 1, leg("b-c", "gk.b", "gk.c", 610));
		final var last = new PhantomNormalGatekeeperTravel.Step(PhantomNormalGatekeeperTravel.Type.TOPOLOGY_BACKGROUND, "walk.egress", "gk.c", "farm", 1, null);
		final var route = List.of(first, second, third, last);
		PhantomAssertions.assertEquals(first, PhantomVisibleFarmTravel.itineraryStep(route, "start", "farm"), "A composite goal must start with its existing first physical segment, not one global approach.");
		PhantomAssertions.assertEquals(second, PhantomVisibleFarmTravel.itineraryStep(route.subList(1, 4), "gk.a", "farm"), "After a successful intermediate checkpoint the committed anchor advances the same route.");
		PhantomAssertions.assertEquals(null, PhantomVisibleFarmTravel.itineraryStep(route.subList(1, 4), "start", "farm"), "An uncheckpointed departure cannot skip the original first segment.");
		PhantomAssertions.assertEquals(null, PhantomVisibleFarmTravel.itineraryStep(List.of(first, third, last), "start", "farm"), "A disconnected itinerary is not an admission proposal.");
		PhantomAssertions.assertEquals(null, PhantomVisibleFarmTravel.itineraryStep(route, "start", "foreign"), "The physical itinerary must retain the exact final goal anchor.");
		for (long budget : List.of(0L, 32_000L, 32_609L, 32_610L, 50_000L))
		{
			PhantomAssertions.assertEquals(budget >= 32_610, PhantomVisibleFarmTravel.itineraryAffordable(route, 1, 50, 0, budget), "All native fees are checked together; individually affordable legs must not overdraw the wallet.");
		}
		final int previous = org.l2jmobius.gameserver.config.PlayerConfig.MAX_FREE_TELEPORT_LEVEL;
		try
		{
			org.l2jmobius.gameserver.config.PlayerConfig.MAX_FREE_TELEPORT_LEVEL = 40;
			PhantomAssertions.assertTrue(PhantomVisibleFarmTravel.itineraryAffordable(route, 0, 20, 0, 0), "Ordinary native free teleport eligibility applies to every leg without a wallet grant.");
		}
		finally { org.l2jmobius.gameserver.config.PlayerConfig.MAX_FREE_TELEPORT_LEVEL = previous; }
	}
	private static PhantomNormalGatekeeperTravel.Leg leg(String id, String from, String to, long fee)
	{
		return new PhantomNormalGatekeeperTravel.Leg(id, from, to, "source", "transition", "destination", 30540, "NORMAL", 0, 0, 0, 0, 0, 0, 0, 57, fee, 1, "fixture", List.of());
	}
	private static void canonicalFarm()
	{
		final var canonical = new PhantomNavigationPoint(-33539, 137701, -3480, 0);
		for (long profile : List.of(1L, 10L, 99L))
		{
			final var candidates = PhantomVisibleFarmTravel.farmingCandidates(profile, canonical);
			PhantomAssertions.assertEquals(canonical, candidates.getFirst(), "A usable radius-one canonical farm point must be considered before offsets outside its area.");
			PhantomAssertions.assertEquals(25, candidates.size(), "Canonical proposal retains the existing bounded offset alternatives.");
		}
	}
}
