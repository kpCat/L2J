/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.util.List;
import java.util.Set;

import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationCancellationToken;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPolicy;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationRequest;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomWalkCorridorPlanner;

/** Causal focused contracts only. This runner never claims Player movement or payment. */
public final class PhantomTravel091Test
{
	public static void main(String[] args)
	{
		final var from = new PhantomNavigationPoint(0, 0, -900, 0);
		final var goal = new PhantomNavigationPoint(19000, 0, 0, 0);
		final var flat = new PhantomNavigationPoint(3000, 0, -900, 0);
		final var ascending = new PhantomNavigationPoint(2598, -1500, -100, 0);
		final var planner = new PhantomWalkCorridorPlanner(new PhantomWalkCorridorPlanner.Oracle()
		{
			@Override public PhantomNavigationPoint project(int x, int y, PhantomNavigationPoint surface)
			{
				return x == flat.x() && y == flat.y() ? flat : x == ascending.x() && y == ascending.y() ? ascending : null;
			}
			@Override public boolean direct(PhantomNavigationPoint a, PhantomNavigationPoint b)
			{
				return a.equals(from) && (b.equals(flat) || b.equals(ascending));
			}
			@Override public List<PhantomNavigationPoint> path(PhantomNavigationPoint a, PhantomNavigationPoint b) { return null; }
		}, () -> 1);
		final var request = new PhantomNavigationRequest(91, from, goal, 1, 1000, 100000);
		final var result = planner.planEpisode(request, new PhantomNavigationCancellationToken(), PhantomNavigationPolicy.productionDefaults(), Set.of());
		PhantomAssertions.assertEquals(PhantomWalkCorridorPlanner.Status.FOUND, result.status(), "A proven local ascent must remain admissible.");
		PhantomAssertions.assertEquals(ascending, result.points().getLast(), "A flat first success must not hide a proven connection towards the target surface.");
		PhantomAssertions.assertTrue(result.queries() <= 32 && result.nodes() <= 128 && result.edges() <= 512 && result.labels() <= 4096, "Surface choice retains existing local search caps.");
		System.out.println("PASS travel091.surface-choice; synthetic-only=true; native-player-actions=0");
	}
}
