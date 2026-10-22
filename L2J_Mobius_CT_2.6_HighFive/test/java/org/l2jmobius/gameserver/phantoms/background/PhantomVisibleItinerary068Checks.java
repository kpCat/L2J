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
	}
	public static void main(String[] args)
	{
		canonicalFarm();
		itinerary();
		System.out.println("OWN068_VISIBLE_ITINERARY_PASS; DB_CONNECTIONS=0; ACTORS=0");
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
