/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.util.List;

import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationCancellationToken;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPolicy;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationRequest;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomWalkCorridorPlanner;

/** Focused finite-buffer regression. Synthetic geometry is not native gameplay evidence. */
public final class PhantomTravel089Test
{
	public static void main(String[] args)
	{
		final var from = point(0, 0);
		final var destination = point(19000, 0);
		final var oracle = new PhantomWalkCorridorPlanner.Oracle()
		{
			@Override public PhantomNavigationPoint project(int x, int y, PhantomNavigationPoint surface) { return point(x, y); }
			@Override public boolean direct(PhantomNavigationPoint a, PhantomNavigationPoint b)
			{
				// A narrow path can be followed locally, but not shortcut across its corners.
				return a.y() == 0 && b.y() == 128 && a.x() == b.x()
					|| a.y() == 128 && b.y() == 128 && b.x() - a.x() <= 3000 && b.x() > a.x()
					|| a.y() == 128 && b.y() == 0 && a.x() == b.x();
			}
			@Override public List<PhantomNavigationPoint> path(PhantomNavigationPoint a, PhantomNavigationPoint b)
			{
				return b.x() > a.x() && b.x() - a.x() <= 3000 && b.y() == 0
					? List.of(point(a.x(), 128), point(b.x(), 128), b) : null;
			}
		};
		final var request = new PhantomNavigationRequest(89, from, destination, 1, 1000, 100000);
		final var result = new PhantomWalkCorridorPlanner(oracle, () -> 1).planEpisode(request, new PhantomNavigationCancellationToken(), PhantomNavigationPolicy.productionDefaults());
		PhantomAssertions.assertEquals(PhantomWalkCorridorPlanner.Status.FOUND, result.status(), "Long first-mile must release a bounded proven checkpoint instead of exhausting a global search.");
		PhantomAssertions.assertTrue(!result.points().isEmpty() && result.points().getLast().distanceTo(from) <= 3200, "An episode must not claim the unproved remote destination.");
		System.out.println("PASS travel089.local-checkpoint; synthetic-only=true");
		final var alternative = new PhantomWalkCorridorPlanner.Oracle()
		{
			@Override public PhantomNavigationPoint project(int x, int y, PhantomNavigationPoint surface)
			{
				return x == 3000 && y == 0 || x == 2598 && y == -1500 ? point(x, y) : null;
			}
			@Override public boolean direct(PhantomNavigationPoint a, PhantomNavigationPoint b) { return a.equals(from) && b.equals(point(2598, -1500)); }
			@Override public List<PhantomNavigationPoint> path(PhantomNavigationPoint a, PhantomNavigationPoint b) { return null; }
		};
		final var counted = new PhantomWalkCorridorPlanner(alternative, () -> 1).planEpisode(request, new PhantomNavigationCancellationToken(), PhantomNavigationPolicy.productionDefaults());
		PhantomAssertions.assertEquals(PhantomWalkCorridorPlanner.Status.FOUND, counted.status(), "The second local alternative must remain admissible.");
		PhantomAssertions.assertTrue(counted.queries() > 0 && counted.nodes() >= 3 && counted.edges() >= 2, "A successful alternative must retain failed-probe cost in its shared budget.");
		PhantomAssertions.assertThrows(IllegalArgumentException.class,
			() -> new PhantomNavigationRequest(89, from, new PhantomNavigationPoint(19000, 0, 0, 1), 1, 1000, 100000), "Another instance cannot enter the native request contract.");
		PhantomAssertions.assertEquals(PhantomWalkCorridorPlanner.Status.DEADLINE_EXPIRED,
			new PhantomWalkCorridorPlanner(oracle, () -> 1000).planEpisode(request, new PhantomNavigationCancellationToken(), PhantomNavigationPolicy.productionDefaults()).status(), "Expired episodes cannot release a checkpoint.");
		System.out.println("PASS travel089.shared-budget-instance-deadline; synthetic-only=true");
		final var escape = point(-1024, 0);
		final var backwards = new PhantomWalkCorridorPlanner.Oracle()
		{
			@Override public PhantomNavigationPoint project(int x, int y, PhantomNavigationPoint surface) { return x == escape.x() && y == escape.y() ? escape : null; }
			@Override public boolean direct(PhantomNavigationPoint a, PhantomNavigationPoint b) { return a.equals(from) && b.equals(escape); }
			@Override public List<PhantomNavigationPoint> path(PhantomNavigationPoint a, PhantomNavigationPoint b) { return null; }
		};
		final var escaped = new PhantomWalkCorridorPlanner(backwards, () -> 1).planEpisode(request, new PhantomNavigationCancellationToken(), PhantomNavigationPolicy.productionDefaults());
		PhantomAssertions.assertEquals(PhantomWalkCorridorPlanner.Status.FOUND, escaped.status(), "A local escape may initially move away from the remote goal.");
		PhantomAssertions.assertEquals(escape, escaped.points().getLast(), "The escape must keep its factual native proof.");
		final var upperGoal = new PhantomNavigationPoint(19000, 0, 1000, 0);
		final var upperCheckpoint = new PhantomNavigationPoint(3000, 0, 1000, 0);
		final var surfaces = new PhantomWalkCorridorPlanner.Oracle()
		{
			@Override public PhantomNavigationPoint project(int x, int y, PhantomNavigationPoint surface)
			{
				return x == 3000 && y == 0 ? new PhantomNavigationPoint(x, y, surface.z(), 0) : null;
			}
			@Override public boolean direct(PhantomNavigationPoint a, PhantomNavigationPoint b) { return a.equals(from) && b.equals(upperCheckpoint); }
			@Override public List<PhantomNavigationPoint> path(PhantomNavigationPoint a, PhantomNavigationPoint b) { return null; }
		};
		final var upper = new PhantomWalkCorridorPlanner(surfaces, () -> 1).planEpisode(new PhantomNavigationRequest(89, from, upperGoal, 1, 1000, 100000), new PhantomNavigationCancellationToken(), PhantomNavigationPolicy.productionDefaults());
		PhantomAssertions.assertEquals(PhantomWalkCorridorPlanner.Status.FOUND, upper.status(), "Target-surface candidates need native proof instead of silently staying on the lower layer.");
		PhantomAssertions.assertEquals(upperCheckpoint, upper.points().getLast(), "The native surface connector must be retained.");
		System.out.println("PASS travel089.non-monotone-local-escape; synthetic-only=true");
	}
	private static PhantomNavigationPoint point(int x, int y) { return new PhantomNavigationPoint(x, y, 0, 0); }
}
