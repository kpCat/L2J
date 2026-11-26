/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.navigation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomDedicatedScoutRegions.Rejection;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomDedicatedScoutRegions.SeedOracle;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomDedicatedScoutRegions.Tile;

public final class PhantomDedicatedScoutRegionChecks
{
	private static final Tile FIRST = new Tile(20, 18);
	private static final Tile SECOND = new Tile(21, 18);
	private static final String FINGERPRINT = "a".repeat(64);

	public static void main(String[] args) throws Exception
	{
		run(Path.of(args[0]));
	}

	public static void run(Path root) throws Exception
	{
		Files.createDirectories(root);
		leasesAndBackoff(root.resolve("leases"));
		boundedProbeAndGeneration(root.resolve("budget"));
		visitedWorkSurvivesSeedStarvation(root.resolve("fairness"));
		outOfWorldLayerConsumesProbe(root.resolve("height"));
	}

	private static void outOfWorldLayerConsumesProbe(Path root) throws Exception
	{
		Files.createDirectory(root);
		final var terrain = new Terrain();
		terrain.firstProjectedHeight = World.WORLD_Z_MIN - 1;
		try (var memory = PhantomRouteLearning.open(root, FINGERPRINT))
		{
			final var regions = PhantomDedicatedScoutRegions.open(memory, terrain);
			regions.beginPulse();
			final var assignment = regions.claim(1, System.currentTimeMillis());
			check(assignment != null && assignment.tile().equals(SECOND) && regions.validateSeed(assignment),
				"an out-of-world layer must leave a valid next tile available in the same pulse");
			check(terrain.projects == 2 && regions.status().contains("NO_LAYER=1"), "invalid height must consume exactly one probe");
		}
	}

	private static void visitedWorkSurvivesSeedStarvation(Path root) throws Exception
	{
		Files.createDirectory(root);
		final long now = System.currentTimeMillis();
		final var terrain = new Terrain();
		terrain.allLoaded = true;
		terrain.safeTile = new Tile(World.TILE_X_MIN, World.TILE_Y_MIN);
		try (var memory = PhantomRouteLearning.open(root, FINGERPRINT))
		{
			final var regions = PhantomDedicatedScoutRegions.open(memory, terrain);
			regions.beginPulse();
			final var visited = regions.claim(1, now);
			check(visited != null && visited.tile().equals(terrain.safeTile), "fixture requires a native-arrived safe first tile");
			regions.seeded(visited);
			regions.arrived(visited);
			regions.release(visited, now, "STORED", false);
			regions.save(memory);
		}
		try (var memory = PhantomRouteLearning.open(root, FINGERPRINT))
		{
			final var regions = PhantomDedicatedScoutRegions.open(memory, terrain);
			final int before = terrain.projects;
			regions.beginPulse();
			final var work = regions.claim(2, now);
			check(work != null && work.tile().equals(terrain.safeTile) && regions.validateSeed(work), "unsafe unvisited tiles must leave probe budget for a currently usable visited tile");
			check(terrain.projects - before > 1 && terrain.projects - before <= 8, "hard tiles receive probes within the shared budget");
			check(regions.status().contains("dedicated_tiles_native_arrived=1\n"), "a new lease cannot manufacture an arrival");
		}
	}

	private static void leasesAndBackoff(Path root) throws Exception
	{
		Files.createDirectory(root);
		final long now = System.currentTimeMillis();
		final var terrain = new Terrain();
		try (var memory = PhantomRouteLearning.open(root, FINGERPRINT))
		{
			final var regions = PhantomDedicatedScoutRegions.open(memory, terrain);
			regions.beginPulse();
			final var first = regions.claim(1001L, now);
			final var second = regions.claim(1002L, now);
			check(first != null && first.tileX() == 20 && second != null && second.tileX() == 21, "actors receive distinct loaded regions");
			check(regions.claim(1001L, now) == first && regions.claim(1003L, now) == null, "leases are exclusive per actor and region");
			check(!regions.tracks(first, new PhantomNavigationPoint(first.seed().x(), first.seed().y(), first.seed().z(), 1)), "instance change breaks region tracking");
			final var nearSide = new PhantomNavigationPoint(World.TILE_SIZE - 152, 3000, 0, 0);
			final var farSide = new PhantomNavigationPoint(World.TILE_SIZE + 248, 3000, 0, 0);
			check(!regions.tracks(first, farSide) && regions.admitCrossing(first, nearSide, farSide) && regions.tracks(first, farSide),
				"only the admitted neighboring passage extends the lease");
			check(!regions.tracks(first, new PhantomNavigationPoint(farSide.x(), 8000, farSide.z(), 0)), "passage cannot grant the rest of the neighbor");
			check(regions.status().contains("dedicated_tiles_seeded=0\n"), "assignment alone is not a native seed");
			regions.seeded(first);
			regions.arrived(first);
			regions.release(first, now, "STORED", false);
			check(!regions.tracks(first, farSide), "released passage cannot publish an arrival");
			regions.release(second, now, "DEAD", true);
			regions.save(memory);
		}
		try (var memory = PhantomRouteLearning.open(root, FINGERPRINT))
		{
			final var regions = PhantomDedicatedScoutRegions.open(memory, terrain);
			regions.beginPulse();
			final var first = regions.claim(1003L, now + 1);
			check(first != null && first.tileX() == 20, "failed tile remains in backoff after writer restart");
			regions.release(first, now + 1, "STORED", false);
			regions.beginPulse();
			final var second = regions.claim(1003L, now + 900_000);
			check(second != null && second.tileX() == 21, "restart retains native coverage priority after backoff expires");
		}
		try (var memory = PhantomRouteLearning.open(root, "b".repeat(64)))
		{
			final var regions = PhantomDedicatedScoutRegions.open(memory, terrain);
			regions.beginPulse();
			check(regions.claim(1003L, now).tileX() == 20 && regions.claim(1004L, now).tileX() == 21, "stale GeoData checkpoint carries no old backoff or leases");
		}
	}

	private static void boundedProbeAndGeneration(Path root) throws Exception
	{
		Files.createDirectory(root);
		final long now = System.currentTimeMillis();
		final var terrain = new Terrain();
		terrain.allLoaded = true;
		terrain.rejection = Rejection.WATER;
		try (var memory = PhantomRouteLearning.open(root, FINGERPRINT))
		{
			final var regions = PhantomDedicatedScoutRegions.open(memory, terrain);
			regions.beginPulse();
			for (int actor = 1; actor <= 32; actor++)
			{
				check(regions.claim(actor, now) == null, "unsafe candidates never acquire leases");
			}
			check(terrain.projects > 0 && terrain.projects <= 8, "32 actors share a bounded seed probe budget");
			regions.save(memory);
		}
		try (var memory = PhantomRouteLearning.open(root, FINGERPRINT))
		{
			final var regions = PhantomDedicatedScoutRegions.open(memory, terrain);
			terrain.rejection = null;
			regions.beginPulse();
			final var assignment = regions.claim(1, now);
			check(assignment != null && assignment.tileX() == 11 && assignment.tileY() == 18 && regions.validateSeed(assignment), "fresh writer resumes at the saved tile cursor");
			terrain.rejection = Rejection.AGGRESSIVE_NPC;
			check(!regions.validateSeed(assignment), "seed hazards are checked again before relocation");
			terrain.rejection = null;
			terrain.generation++;
			regions.beginPulse();
			check(!regions.validateSeed(assignment) && regions.claim(2, now) == null, "GeoData reload disables leases until a fresh fingerprint");
		}
	}

	private static void check(boolean condition, String message)
	{
		if (!condition) { throw new AssertionError(message); }
	}

	private static final class Terrain implements SeedOracle
	{
		private final Set<Tile> tiles = Set.of(FIRST, SECOND);
		private Tile safeTile;
		private int projects;
		private int firstProjectedHeight = -3200;
		private long generation;
		private boolean allLoaded;
		private Rejection rejection;

		@Override public long generation() { return generation; }
		@Override public boolean loaded(Tile tile) { return allLoaded || tiles.contains(tile); }
		@Override public PhantomNavigationPoint project(int x, int y, int hintZ)
		{
			projects++;
			check(x >= World.WORLD_X_MIN && x < World.WORLD_X_MAX && y >= World.WORLD_Y_MIN && y < World.WORLD_Y_MAX, "seed is inside world bounds");
			return PhantomDedicatedScoutRegions.seedAtHeight(x, y, projects == 1 ? firstProjectedHeight : -3200);
		}
		@Override public Rejection reject(PhantomNavigationPoint point)
		{
			return safeTile != null && !safeTile.contains(point) ? Rejection.WATER : rejection;
		}
	}
}
