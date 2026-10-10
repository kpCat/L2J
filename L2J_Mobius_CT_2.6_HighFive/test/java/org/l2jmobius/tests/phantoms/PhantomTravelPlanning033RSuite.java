/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.geoengine.pathfinding.GeoNode;
import org.l2jmobius.gameserver.geoengine.pathfinding.NodeBuffer;
import org.l2jmobius.gameserver.geoengine.pathfinding.PathFinding;
import org.l2jmobius.gameserver.phantoms.navigation.L2jNavigationBackend;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNativeRouteContract;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationCancellationToken;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPolicy;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationRequest;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationResult;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomWalkCorridorPlanner;

/** Owned native fixture and actual stock geometry; geometry tests never write XYZ. */
public final class PhantomTravelPlanning033RSuite implements PhantomTestSuite
{
	private final PhantomVisibleIntentRecoverySuite _intent = new PhantomVisibleIntentRecoverySuite();
	public static void main(String[] args)
	{
		System.exit(PhantomTestLauncher.runSuite("travel-planning033r", new PhantomTravelPlanning033RSuite(), new PhantomTestContext(33003301, Path.of(args[0]), Path.of(args[1]))));
	}
	@Override public String id() { return "travel-planning033r"; }
	@Override public void beforeAll(PhantomTestContext context) throws Exception { _intent.beforeAll(context); }
	@Override public void afterAll(PhantomTestContext context) throws Exception { _intent.afterAll(context); }
	@Override public void register(PhantomTestRegistry registry)
	{
		registry.add("T06-unsafe-stock-parent-chain-remains-rejected", this::lostTurn);
		registry.add("T01-complete-native-walk-to-dry-farm-ingress", this::completeWalk);
		registry.add("T03-U-detour-keeps-directed-surface-identity", this::detour);
		registry.add("T07-search-and-deadline-remain-bounded", this::budget);
	}
	private void lostTurn(PhantomTestContext context) throws Exception
	{
		final var from = new PhantomNavigationPoint(-88984, 250008, -3568, 0);
		final var to = new PhantomNavigationPoint(-87883, 251678, -3632, 0);
		final var geo = GeoEngine.getInstance();
		PhantomAssertions.assertTrue(geo.hasGeo(from.x(), from.y()) && geo.hasGeo(to.x(), to.y()), "Real retained032 geometry is required.");
		final var backend = new L2jNavigationBackend();
		final var full = nativeChain(from, to);
		PhantomAssertions.assertFalse(full.isEmpty(), "The unchanged stock A* must have a parent-chain witness.");
		var previous = from;
		String firstBad = "";
		for (var point : full)
		{
			if (firstBad.isEmpty() && !backend.canMoveDirect(previous, point)) { firstBad = previous + " -> " + point; }
			previous = point;
		}
		context.record("T06.parentChainPoints", full.size());
		context.record("T06.parentChainFirstBad", firstBad);
		final long now = System.nanoTime();
		final var points = backend.findPath(new PhantomNavigationRequest(1, from, to, now, now + 1_000_000_000L, 100000), new PhantomNavigationCancellationToken());
		final var normalized = PhantomNativeRouteContract.normalize(from, to, points, PhantomNavigationPolicy.productionDefaults(), 100000, backend::canMoveDirect, () -> PhantomNavigationResult.Status.PATH_FOUND);
		context.record("T06.backendPoints", points);
		context.record("T06.normalized", normalized.status());
		PhantomAssertions.assertFalse(firstBad.isEmpty(), "This witness disproves a lost-turn-only diagnosis; raw native parents also cross an illegal height transition.");
		PhantomAssertions.assertEquals(PhantomNavigationResult.Status.ROUTE_OBSTRUCTED, normalized.status(), "Unsafe native output must remain rejected; no smoothing or XYZ correction.");
	}
	private void completeWalk(PhantomTestContext context) throws Exception
	{
		final var from = new PhantomNavigationPoint(-88984, 250008, -3568, 0);
		final var geo = GeoEngine.getInstance();
		final var to = new PhantomNavigationPoint(-86401, 253927, geo.getHeight(-86401, 253927, -3736), 0);
		final var backend = new L2jNavigationBackend();
		PhantomAssertions.assertTrue(org.l2jmobius.gameserver.managers.ZoneManager.getInstance().getZone(to.x(), to.y(), to.z(), org.l2jmobius.gameserver.model.zone.type.WaterZone.class) == null, "Final native farm ingress must be dry.");
		final long now = System.nanoTime();
		final var corridor = backend.findWalkCorridor(new PhantomNavigationRequest(5, from, to, now, now + 1_000_000_000L, 100000), new PhantomNavigationCancellationToken(), PhantomNavigationPolicy.productionDefaults(), System::nanoTime);
		final var normalized = PhantomNativeRouteContract.normalize(from, to, corridor.points(), PhantomNavigationPolicy.productionDefaults(), 100000, backend::canWalkDirect, () -> PhantomNavigationResult.Status.PATH_FOUND);
		context.record("T01.destination", to);
		context.record("T01.corridor", corridor);
		context.record("T01.nativeProof", normalized.status());
		PhantomAssertions.assertEquals(PhantomWalkCorridorPlanner.Status.FOUND, corridor.status(), "Complete corridor required within unchanged deadline and stock buffers.");
		PhantomAssertions.assertEquals(PhantomNavigationResult.Status.PATH_FOUND, normalized.status(), "RED: a complete verified native corridor is required before movement, including a lawful alternative to the rejected ray.");
	}
	private void detour(PhantomTestContext context)
	{
		final List<PhantomNavigationPoint> nodes = List.of(point(0,0,0), point(-1024,0,0), point(-1024,1024,0), point(-1024,2048,0), point(0,2048,0), point(1024,2048,0), point(2048,2048,0), point(2048,1024,0), point(2048,0,0));
		final var oracle = new PhantomWalkCorridorPlanner.Oracle()
		{
			@Override public PhantomNavigationPoint project(int x, int y, PhantomNavigationPoint surface) { return nodes.stream().filter(p -> p.x() == x && p.y() == y && p.z() == surface.z()).findFirst().orElse(null); }
			@Override public boolean direct(PhantomNavigationPoint from, PhantomNavigationPoint to) { final int i = nodes.indexOf(from); return i >= 0 && i + 1 < nodes.size() && nodes.get(i + 1).equals(to); }
			@Override public List<PhantomNavigationPoint> path(PhantomNavigationPoint from, PhantomNavigationPoint to) { return null; }
		};
		final var planner = new PhantomWalkCorridorPlanner(oracle, () -> 1);
		final var result = planner.plan(new PhantomNavigationRequest(1, nodes.getFirst(), nodes.getLast(), 1, 1000, 100000), new PhantomNavigationCancellationToken(), PhantomNavigationPolicy.productionDefaults());
		PhantomAssertions.assertEquals(PhantomWalkCorridorPlanner.Status.FOUND, result.status(), "U-shaped corridor requires going away from the goal before coming around the wall.");
		PhantomAssertions.assertTrue(result.points().getFirst().distanceTo(nodes.getLast()) > nodes.getFirst().distanceTo(nodes.getLast()), "First useful step increases distance; no greedy ray chopping.");
		final var reverse = planner.plan(new PhantomNavigationRequest(1, nodes.getLast(), nodes.getFirst(), 1, 1000, 100000), new PhantomNavigationCancellationToken(), PhantomNavigationPolicy.productionDefaults());
		PhantomAssertions.assertEquals(PhantomWalkCorridorPlanner.Status.NO_ROUTE_WITHIN_BUDGET, reverse.status(), "Forward proof grants no reverse traversal.");
		final var otherFloor = planner.plan(new PhantomNavigationRequest(1, nodes.getFirst(), point(2048,0,256), 1, 1000, 100000), new PhantomNavigationCancellationToken(), PhantomNavigationPolicy.productionDefaults());
		PhantomAssertions.assertEquals(PhantomWalkCorridorPlanner.Status.NO_ROUTE_WITHIN_BUDGET, otherFloor.status(), "Same XY on another surface is a different unreachable node.");
		context.record("T03.detour", result);
	}
	private void budget(PhantomTestContext context)
	{
		final var oracle = new PhantomWalkCorridorPlanner.Oracle()
		{
			@Override public PhantomNavigationPoint project(int x, int y, PhantomNavigationPoint surface) { return point(x,y,0); }
			@Override public boolean direct(PhantomNavigationPoint from, PhantomNavigationPoint to) { return to.x() == from.x() + 1024 && to.y() == from.y() && to.z() == from.z(); }
			@Override public List<PhantomNavigationPoint> path(PhantomNavigationPoint from, PhantomNavigationPoint to) { return null; }
		};
		final var request = new PhantomNavigationRequest(1, point(0,0,0), point(0,2048,0), 1, 1000, 100000);
		final var limited = new PhantomWalkCorridorPlanner(oracle, () -> 1).plan(request, new PhantomNavigationCancellationToken(), PhantomNavigationPolicy.productionDefaults());
		PhantomAssertions.assertEquals(PhantomWalkCorridorPlanner.Status.SEARCH_LIMIT, limited.status(), "Unknown skipped edges after budget exhaustion cannot become UNREACHABLE.");
		PhantomAssertions.assertTrue(limited.nodes() <= 128 && limited.edges() <= 512 && limited.queries() <= 32 && limited.labels() <= 4096 && limited.points().isEmpty(), "All coordinator bounds and empty partial result enforced.");
		final var expired = new PhantomWalkCorridorPlanner(oracle, () -> 1000).plan(request, new PhantomNavigationCancellationToken(), PhantomNavigationPolicy.productionDefaults());
		PhantomAssertions.assertEquals(PhantomWalkCorridorPlanner.Status.DEADLINE_EXPIRED, expired.status(), "Existing request deadline wins over structural budget.");
		context.record("T07.bounds", limited);
	}
	private static PhantomNavigationPoint point(int x, int y, int z) { return new PhantomNavigationPoint(x,y,z,0); }
	private static List<PhantomNavigationPoint> nativeChain(PhantomNavigationPoint from, PhantomNavigationPoint to) throws Exception
	{
		final var alloc = PathFinding.class.getDeclaredMethod("alloc", int.class);
		alloc.setAccessible(true);
		final int gx = GeoEngine.getGeoX(from.x()), gy = GeoEngine.getGeoY(from.y());
		final int tx = GeoEngine.getGeoX(to.x()), ty = GeoEngine.getGeoY(to.y());
		final var buffer = (NodeBuffer) alloc.invoke(PathFinding.getInstance(), 64 + 2 * Math.max(Math.abs(gx - tx), Math.abs(gy - ty)));
		PhantomAssertions.assertTrue(buffer != null, "The test query must fit the unchanged native buffer.");
		try
		{
			GeoNode node = buffer.findPath(gx, gy, from.z(), tx, ty, to.z());
			final List<PhantomNavigationPoint> points = new ArrayList<>();
			while (node != null && points.size() <= 7000)
			{
				final var point = node.getLocation();
				points.addFirst(new PhantomNavigationPoint(point.getX(), point.getY(), point.getZ(), from.instanceId()));
				node = node.getParent();
			}
			PhantomAssertions.assertTrue(node == null, "Parent-chain witness must stay inside the native search bound.");
			return List.copyOf(points);
		}
		finally { buffer.free(); }
	}
}
