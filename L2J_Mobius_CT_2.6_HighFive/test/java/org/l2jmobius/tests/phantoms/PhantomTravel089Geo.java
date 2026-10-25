/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.l2jmobius.gameserver.config.GeoEngineConfig;
import org.l2jmobius.gameserver.config.ServerConfig;
import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.geoengine.HermeticGeoMovement;
import org.l2jmobius.gameserver.geoengine.StaticXmlCollisionOracle;
import org.l2jmobius.gameserver.geoengine.pathfinding.PathFinding;
import org.l2jmobius.gameserver.geoengine.pathfinding.PathFindingRawAccess;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationCancellationToken;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPolicy;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationRequest;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomWalkCorridorPlanner;

/** DB-free actual stock geometry. Checkpoint projection never counts as a Player WALK. */
public final class PhantomTravel089Geo
{
	public static void main(String[] args) throws Exception
	{
		if (args.length != 2) { throw new IllegalArgumentException("Usage: source-candidates-tsv output-tsv"); }
		ServerConfig.DATAPACK_ROOT = new File(".").getCanonicalFile();
		GeoEngineConfig.load();
		PhantomAssertions.assertEquals(2, GeoEngineConfig.PATHFINDING, "Stock pathfinding required.");
		final var geo = GeoEngine.getInstance();
		final var finder = PathFinding.getInstance();
		final var collisions = StaticXmlCollisionOracle.load(Path.of("data/Doors.xml"), Path.of("data/FenceData.xml"));
		final var oracle = new PhantomWalkCorridorPlanner.Oracle()
		{
			@Override public PhantomNavigationPoint project(int x, int y, PhantomNavigationPoint surface)
			{
				if (!geo.hasGeo(x, y)) { return null; }
				final int z = geo.getHeight(x, y, surface.z());
				return z >= org.l2jmobius.gameserver.model.World.WORLD_Z_MIN && z <= org.l2jmobius.gameserver.model.World.WORLD_Z_MAX && geo.getHeight(x, y, z) == z ? new PhantomNavigationPoint(x, y, z, surface.instanceId()) : null;
			}
			@Override public boolean direct(PhantomNavigationPoint a, PhantomNavigationPoint b)
			{
				return a.instanceId() == b.instanceId() && geo.hasGeo(a.x(), a.y()) && geo.hasGeo(b.x(), b.y())
					&& geo.getHeight(b.x(), b.y(), b.z()) == b.z()
					&& HermeticGeoMovement.canMove(geo, a.x(), a.y(), a.z(), b.x(), b.y(), b.z(), a.instanceId(), collisions);
			}
			@Override public List<PhantomNavigationPoint> path(PhantomNavigationPoint a, PhantomNavigationPoint b)
			{
				final var path = PathFindingRawAccess.find(finder, a.x(), a.y(), a.z(), b.x(), b.y(), b.z(), a.instanceId(), true);
				return path == null ? null : path.stream().map(p -> new PhantomNavigationPoint(p.getX(), p.getY(), p.getZ(), a.instanceId())).toList();
			}
		};
		final var policy = PhantomNavigationPolicy.productionDefaults();
		final var planner = new PhantomWalkCorridorPlanner(oracle, System::nanoTime);
		final var lines = Files.readAllLines(Path.of(args[0]));
		PhantomAssertions.assertEquals(5, lines.size(), "All three starter fixtures and Giran control are mandatory.");
		PhantomAssertions.assertEquals("connector_id\tfrom_x\tfrom_y\tfrom_z\tfrom_instance\tto_x\tto_y\tto_z\tto_instance", lines.getFirst(), "Source candidate header.");
		PhantomAssertions.assertEquals(java.util.Set.of("giran.city.center", "population.ingress.elf.01", "population.ingress.human-fighter.01", "population.ingress.orc.01"),
			lines.subList(1, lines.size()).stream().map(line -> line.split("\t")[0]).collect(java.util.stream.Collectors.toSet()), "Exact source fixture identities required.");
		final var output = new ArrayList<String>();
		output.add("source\tepisode\tfrom\tto\tstatus\tnodes\tedges\tqueries\tlabels\twallMicros\tcategory");
		int complete = 0;
		for (String line : lines.subList(1, lines.size()))
		{
			final var row = line.split("\t");
			var from = point(row, 1, geo);
			final var to = point(row, 5, geo);
			int waypoints = 0; double distance = 0;
			final var visited = new java.util.HashSet<PhantomNavigationPoint>();
			for (int episode = 0; episode < 64 && !from.equals(to); episode++)
			{
				visited.add(from);
				final long now = System.nanoTime();
				final var result = planner.planEpisode(new PhantomNavigationRequest(89, from, to, now, now + policy.defaultRequestDeadlineNanos(), 100000), new PhantomNavigationCancellationToken(), policy, visited);
				output.add(row[0] + "\t" + episode + "\t" + from + "\t" + (result.points().isEmpty() ? "NONE" : result.points().getLast()) + "\t" + result.status() + "\t" + result.nodes() + "\t" + result.edges() + "\t" + result.queries() + "\t" + result.labels() + "\t" + (System.nanoTime() - now) / 1000 + "\tSOURCE_PROVEN_NOT_PLAYER_WALK");
				Files.write(Path.of(args[1]), output);
				PhantomAssertions.assertTrue(result.nodes() <= 128 && result.edges() <= 512 && result.queries() <= 32 && result.labels() <= 4096, "Local budget escaped.");
				if (result.status() != PhantomWalkCorridorPlanner.Status.FOUND) { break; }
				for (var next : result.points()) { distance += from.distanceTo(next); from = next; }
				waypoints += result.points().size();
				if (distance > 100000) { break; }
			}
			final boolean found = from.equals(to) && distance <= 100000;
			if (found) { complete++; }
			System.out.println("TASK089_GEO source=" + row[0] + "; complete=" + found + "; waypoints=" + waypoints + "; distance=" + Math.round(distance) + "; PlayerMove=0");
		}
		Files.write(Path.of(args[1]), output);
		System.out.println("TASK089_GEO_RESULT=" + complete + "/" + (lines.size() - 1) + "; nativeGameplay=NOT_RUN; waterZoneRuntime=NOT_RUN");
		if (complete != lines.size() - 1) { throw new AssertionError("Native geometry first-mile incomplete; no connector may be certified."); }
	}
	private static PhantomNavigationPoint point(String[] row, int start, GeoEngine geo)
	{
		final int x = Integer.parseInt(row[start]), y = Integer.parseInt(row[start + 1]);
		return new PhantomNavigationPoint(x, y, geo.getHeight(x, y, Integer.parseInt(row[start + 2])), Integer.parseInt(row[start + 3]));
	}
}
