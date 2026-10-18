/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.phantoms.background.PhantomNormalGatekeeperTravel;
import org.l2jmobius.gameserver.phantoms.navigation.L2jNavigationBackend;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNativeTravelActions;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationCancellationToken;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPolicy;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationRequest;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationResult;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomTravelStrategy;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomWalkCorridorPlanner;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyQuery;

/** Actual geo/stock GK planning control only; never moves a Player or charges a fee. */
final class PhantomGatekeeper060CorridorGuards
{
	static void sharedBudget(PhantomTestContext context)
	{
		final var calls = new java.util.concurrent.atomic.AtomicInteger();
		final var planner = new PhantomWalkCorridorPlanner(new PhantomWalkCorridorPlanner.Oracle()
		{
			@Override public PhantomNavigationPoint project(int x, int y, PhantomNavigationPoint surface) { return null; }
			@Override public boolean direct(PhantomNavigationPoint from, PhantomNavigationPoint to) { return false; }
			@Override public List<PhantomNavigationPoint> path(PhantomNavigationPoint from, PhantomNavigationPoint to) { calls.incrementAndGet(); return null; }
		}, () -> 1);
		final var budget = new PhantomWalkCorridorPlanner.Budget();
		final var policy = PhantomNavigationPolicy.productionDefaults();
		for (int index = 0; index < 140; index++)
		{
			final var result = planner.plan(new PhantomNavigationRequest(1, new PhantomNavigationPoint(0, 0, 0, 0), new PhantomNavigationPoint(1024, 0, 0, 0), 1, 1000, 10000), new PhantomNavigationCancellationToken(), policy, budget);
			PhantomAssertions.assertTrue(result.status() != PhantomWalkCorridorPlanner.Status.FOUND, "Unproven shared-budget edge was accepted.");
		}
		PhantomAssertions.assertEquals(32, calls.get(), "Shared GK itinerary reset the native stock-query budget between WALK stages.");
		context.record("gatekeeper060.sharedBudget", "PURE_ORACLE_ONLY;140 searches sharing one budget; native path callbacks=" + calls.get() + "; no Player/DB/geometry mutation");
	}

	static void run(PhantomTestContext context, PhantomTopologyQuery topology) throws Exception
	{
		PhantomCampaign058DatabaseLane.assertActiveFixture(context);
		final var geo = GeoEngine.getInstance();
		final var backend = new L2jNavigationBackend();
		final var policy = PhantomNavigationPolicy.productionDefaults();
		final var leg = PhantomNormalGatekeeperTravel.load(Path.of("data/phantoms/travel/high-five-normal-gk.xml"), topology).legs().stream()
			.filter(value -> value.teleporterNpcId() == 30540 && value.destinationX() == 87126 && value.destinationY() == -143520).findFirst().orElseThrow();
		PhantomAssertions.assertTrue(PhantomNativeTravelActions.available(leg), "Native declared Dwarf-to-Schuttgart leg is unavailable.");
		final var from = new PhantomNavigationPoint(116333, -175680, geo.getHeight(116333, -175680, -1047), 0);
		final var source = new PhantomNavigationPoint(leg.sourceX(), leg.sourceY(), geo.getHeight(leg.sourceX(), leg.sourceY(), leg.sourceZ()), 0);
		final var landing = new PhantomNavigationPoint(leg.destinationX(), leg.destinationY(), geo.getHeight(leg.destinationX(), leg.destinationY(), leg.destinationZ()) + 5, 0);
		final var destination = new PhantomNavigationPoint(landing.x(), landing.y(), geo.getHeight(landing.x(), landing.y(), landing.z()), 0);
		var now = System.nanoTime();
		final var ingress = backend.findWalkCorridor(new PhantomNavigationRequest(1, from, source, now, now + TimeUnit.SECONDS.toNanos(2), policy.maximumRouteDistance()), new PhantomNavigationCancellationToken(), policy, System::nanoTime);
		now = System.nanoTime();
		final var egress = backend.findWalkCorridor(new PhantomNavigationRequest(1, landing, destination, now, now + TimeUnit.SECONDS.toNanos(2), policy.maximumRouteDistance()), new PhantomNavigationCancellationToken(), policy, System::nanoTime);
		context.record("gatekeeper060.corridors", "READ_ONLY_NATIVE ingress=" + ingress + "; egress=" + egress + "; straight=" + backend.canWalkDirect(from, source) + '/' + backend.canWalkDirect(landing, destination));
		PhantomAssertions.assertTrue(ingress.status() == PhantomWalkCorridorPlanner.Status.FOUND && egress.status() == PhantomWalkCorridorPlanner.Status.FOUND, "BLOCKED_GK_CORRIDOR_INPUT: both real geo corridors must exist before product assertion.");
		final var options = new PhantomTravelStrategy.Options(List.of(leg), 150, 10000, 0, 0, 4, System.currentTimeMillis() / 60000, true, "native.control", null, 0, 120000, Set.of());
		now = System.nanoTime();
		final var request = new PhantomNavigationRequest(1, from, destination, now, now + policy.defaultRequestDeadlineNanos(), policy.maximumRouteDistance());
		final var result = backend.planTravel(request, new PhantomNavigationCancellationToken(), policy, System::nanoTime, options);
		context.record("gatekeeper060.plan", "READ_ONLY_NATIVE " + result + "; no Player movement/payment/reward");
		PhantomAssertions.assertEquals(PhantomNavigationResult.Status.PATH_FOUND, result.status(), "GK planner discarded proven native corridors because they are not straight lines.");
		PhantomAssertions.assertTrue(result.candidate().stages().stream().anyMatch(stage -> stage.mode() == PhantomTravelStrategy.Mode.GK && stage.gatekeeper().id().equals(leg.id())), "Native GK stage disappeared.");
		final var noCash = new PhantomTravelStrategy.Options(List.of(leg), 150, 0, 0, 1, 41, System.currentTimeMillis() / 60000, true, "native.control", null, 0, 120000, Set.of());
		now = System.nanoTime();
		final var refused = backend.planTravel(new PhantomNavigationRequest(1, from, destination, now, now + policy.defaultRequestDeadlineNanos(), policy.maximumRouteDistance()), new PhantomNavigationCancellationToken(), policy, System::nanoTime, noCash);
		PhantomAssertions.assertTrue(refused.candidate() == null, "Paid native GK leg accepted zero funds outside the native free main-class level range.");
	}
}
