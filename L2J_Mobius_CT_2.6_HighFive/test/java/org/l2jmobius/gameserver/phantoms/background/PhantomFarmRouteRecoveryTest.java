/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.background;

import java.util.List;

import org.l2jmobius.gameserver.model.actor.PlayerNativeEvidence;
import org.l2jmobius.gameserver.phantoms.background.PhantomVisibleFarmTravel.NativeWalkBudget;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationBackend.CapabilitySnapshot;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationCapability;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPolicy;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationRoute;
import org.l2jmobius.tests.phantoms.PhantomAssertions;

/** Ordinary WALK release over a dry corner captured from the profile 32 GeoData route. */
public final class PhantomFarmRouteRecoveryTest
{
	public static void main(String[] args)
	{
		final var from = new PhantomNavigationPoint(115016, -164632, -528, 0);
		final var corner = new PhantomNavigationPoint(115048, -164664, -528, 0);
		final var checkpoint = new PhantomNavigationPoint(115128, -165160, -384, 0);
		final var target = new PhantomNavigationPoint(116056, -175091, -1016, 0);
		final var policy = PhantomNavigationPolicy.productionDefaults();
		final var route = new PhantomNavigationRoute(PhantomNavigationRoute.Mode.COMPUTED, from, checkpoint, List.of(corner, checkpoint),
			new CapabilitySnapshot(PhantomNavigationCapability.GEODATA_PATHFINDING, 1), 1, false, policy.maximumWaypoints(), policy.maximumRouteDistance());
		final var budget = new NativeWalkBudget(from, target, 1, 80, policy);
		PhantomAssertions.assertTrue(budget.admit(route, 2), "Proven nonfinal WALK must fit the existing budget.");
		PhantomAssertions.assertEquals(0, PhantomVisibleFarmTravel.nextWalkWaypoint(from, route.waypoints(), 0, policy.arrivalRadius(), (_, _) -> false),
			"Dispatch the captured corner when the shorter chord is blocked.");
		PhantomAssertions.assertFalse(budget.checkpoint(corner, 3), "An interior corner does not publish checkpoint arrival.");
		PhantomAssertions.assertEquals(1, PhantomVisibleFarmTravel.nextWalkWaypoint(corner, route.waypoints(), 0, policy.arrivalRadius(),
			(start, end) -> start.equals(corner) && end.equals(checkpoint)), "After native arrival at the corner the proven outgoing leg is usable.");
		PhantomAssertions.assertTrue(budget.checkpoint(checkpoint, 4), "The admitted checkpoint can be consumed once.");
		PhantomAssertions.assertFalse(budget.checkpoint(checkpoint, 5), "Duplicate checkpoint cannot renew time or budget.");
		final var returnToOrigin = new PhantomNavigationRoute(route.mode(), checkpoint, from, List.of(corner, from),
			route.geodataCapability(), 5, false, policy.maximumWaypoints(), policy.maximumRouteDistance());
		final var beforeLoop = budget.suspend(5);
		PhantomAssertions.assertFalse(budget.admit(returnToOrigin, 5), "Returning to the prior checkpoint must not reopen an episode.");
		PhantomAssertions.assertEquals(beforeLoop, budget.suspend(5), "A rejected loop must preserve the charged route, time and visited set.");
		PhantomAssertions.assertEquals(1, PhantomVisibleFarmTravel.nextWalkWaypoint(from, route.waypoints(), 0, policy.arrivalRadius(), (_, _) -> true),
			"A geometrically revalidated nearby-waypoint shortcut remains allowed.");
		verifyFarmSettlement();
		System.out.println("PASS ordinary farm WALK keeps a mandatory corner and consumes the bounded checkpoint once");
	}

	private static void verifyFarmSettlement()
	{
		final var evidence = new PlayerNativeEvidence(41, 7);
		final var attackTarget = new PlayerNativeEvidence.Target(42, 0, 1);
		final var foreignTarget = new PlayerNativeEvidence.Target(43, 0, 1);
		final var arrival = evidence.snapshot();
		evidence.damage(attackTarget, 10);
		PhantomAssertions.assertFalse(PhantomVisibleFarmTravel.settledFarmAfter(evidence, arrival, 0), "Damage leaves the incident open.");
		evidence.killed(attackTarget);
		evidence.reward(foreignTarget, 10, 1);
		evidence.rewardSettled(foreignTarget);
		PhantomAssertions.assertFalse(PhantomVisibleFarmTravel.settledFarmAfter(evidence, arrival, 0), "Unrelated reward leaves the incident open.");
		evidence.reward(attackTarget, 10, 1);
		PhantomAssertions.assertFalse(PhantomVisibleFarmTravel.settledFarmAfter(evidence, arrival, 0), "The reward writer must finish before incident release.");
		evidence.rewardSettled(attackTarget);
		PhantomAssertions.assertTrue(PhantomVisibleFarmTravel.settledFarmAfter(evidence, arrival, 0), "Exact fresh kill and settled EXP/SP close the incident.");
		PhantomAssertions.assertFalse(PhantomVisibleFarmTravel.settledFarmAfter(evidence, evidence.snapshot(), 0), "A prior reward cannot close a later incident.");
		PhantomAssertions.assertFalse(PhantomVisibleFarmTravel.settledFarmAfter(evidence, arrival, 1), "A different instance cannot consume the farm receipt.");
	}
}
