/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.util.List;

import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationCancellationToken;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPolicy;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationRequest;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomWalkCorridorPlanner;

/** Geometry contract controls; no database, World mutation or native gameplay claim. */
public final class PhantomWalkCorridor064Test
{
	public static void main(String[] args)
	{
		final var from = point(0, 0);
		final var to = point(4000, 0);
		final List<PhantomNavigationPoint> nativeRoute = List.of(point(0, 512), point(4000, 512), to);
		final var oracle = new PhantomWalkCorridorPlanner.Oracle()
		{
			@Override public PhantomNavigationPoint project(int x, int y, PhantomNavigationPoint surface) { return null; }
			@Override public boolean direct(PhantomNavigationPoint a, PhantomNavigationPoint b)
			{
				return a.equals(from) && b.equals(nativeRoute.get(0)) || a.equals(nativeRoute.get(0)) && b.equals(nativeRoute.get(1)) || a.equals(nativeRoute.get(1)) && b.equals(to);
			}
			@Override public List<PhantomNavigationPoint> path(PhantomNavigationPoint a, PhantomNavigationPoint b) { return a.equals(from) && b.equals(to) ? nativeRoute : null; }
		};
		final var full = plan(oracle, from, to);
		PhantomAssertions.assertEquals(PhantomWalkCorridorPlanner.Status.FOUND, full.status(), "A lawful complete native corridor within12k must not depend on coarse1024 grid nodes.");
		PhantomAssertions.assertEquals(nativeRoute, full.points(), "Every returned leg must retain the native proof.");
		PhantomAssertions.assertEquals(1, full.queries(), "The complete native path should resolve before spending the overlay query budget.");
		System.out.println("PASS corridor064.complete-native-before-coarse-overlay");

		final var invalid = new PhantomWalkCorridorPlanner.Oracle()
		{
			@Override public PhantomNavigationPoint project(int x, int y, PhantomNavigationPoint surface) { return null; }
			@Override public boolean direct(PhantomNavigationPoint a, PhantomNavigationPoint b) { return false; }
			@Override public List<PhantomNavigationPoint> path(PhantomNavigationPoint a, PhantomNavigationPoint b) { return List.of(new PhantomNavigationPoint(b.x(), b.y(), b.z(), 1)); }
		};
		final var wrongInstance = plan(invalid, from, to);
		PhantomAssertions.assertTrue(wrongInstance.status() != PhantomWalkCorridorPlanner.Status.FOUND && wrongInstance.points().isEmpty(), "Native output from another instance cannot become a complete route.");
		final var beyondLocal = plan(invalid, from, point(12001, 0));
		PhantomAssertions.assertEquals(0, beyondLocal.queries(), "A complete native query cannot escape the unchanged12k local policy.");
		System.out.println("PASS corridor064.native-instance-and-local-bounds");

		final List<PhantomNavigationPoint> nodes = List.of(from, point(-1024, 0), point(-1024, 1024), point(0, 1024), point(1024, 1024), point(2048, 1024), point(2048, 0));
		final var fallback = new PhantomWalkCorridorPlanner.Oracle()
		{
			@Override public PhantomNavigationPoint project(int x, int y, PhantomNavigationPoint surface) { return nodes.stream().filter(p -> p.x() == x && p.y() == y).findFirst().orElse(null); }
			@Override public boolean direct(PhantomNavigationPoint a, PhantomNavigationPoint b) { final int index = nodes.indexOf(a); return index >= 0 && index + 1 < nodes.size() && nodes.get(index + 1).equals(b); }
			@Override public List<PhantomNavigationPoint> path(PhantomNavigationPoint a, PhantomNavigationPoint b) { return null; }
		};
		final var detour = plan(fallback, from, nodes.getLast());
		PhantomAssertions.assertEquals(PhantomWalkCorridorPlanner.Status.FOUND, detour.status(), "A missing whole native path must preserve the existing non-greedy overlay fallback.");
		PhantomAssertions.assertEquals(point(-1024, 0), detour.points().getFirst(), "A lawful first leg may move away from the goal.");
		PhantomAssertions.assertTrue(detour.nodes() <= 128 && detour.edges() <= 512 && detour.queries() <= 32 && detour.labels() <= 4096, "Existing shared search budgets remain bounded.");
		System.out.println("PASS corridor064.fallback-backtracking-and-shared-budget");
	}

	private static PhantomWalkCorridorPlanner.Result plan(PhantomWalkCorridorPlanner.Oracle oracle, PhantomNavigationPoint from, PhantomNavigationPoint to)
	{
		return new PhantomWalkCorridorPlanner(oracle, () -> 1).plan(new PhantomNavigationRequest(64, from, to, 1, 1000, 100000), new PhantomNavigationCancellationToken(), PhantomNavigationPolicy.productionDefaults());
	}
	private static PhantomNavigationPoint point(int x, int y) { return new PhantomNavigationPoint(x, y, 0, 0); }
}
