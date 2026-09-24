package org.l2jmobius.tests.phantoms;

import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/** Focused negative controls for candidate validation before publication. */
public final class PhantomGeoValidationRulesTest
{
	private static final PhantomGeoValidationRules.Point A = new PhantomGeoValidationRules.Point(100, 100, 20, 0);
	private static final PhantomGeoValidationRules.Point B = new PhantomGeoValidationRules.Point(200, 100, 20, 0);

	public static void main(String[] args) throws Exception
	{
		anchorControls();
		routeControls();
		chainControls();
		shardControls();
		System.out.println("PHANTOM GEO RULES: PASS");
	}

	private static void shardControls() throws Exception
	{
		final Path first = Files.createTempDirectory("phantom-geoshard-first-");
		final Path second = Files.createTempDirectory("phantom-geoshard-second-");
		try
		{
			final List<String> entities = List.of("\t<node id=\"a\" tags=\"" + "é".repeat(35) + "\" />\n", "\t<node id=\"b\" tags=\"" + "é".repeat(35) + "\" />\n");
			final int limit = 250;
			final List<String> names = PhantomTopologyGeodataPublisher.writeShards(first, entities, limit);
			check(2, names.size());
			check(names, PhantomTopologyGeodataPublisher.writeShards(second, entities, limit));
			for (String name : names)
			{
				check(true, Files.size(first.resolve(name)) <= limit);
				check(true, Arrays.equals(Files.readAllBytes(first.resolve(name)), Files.readAllBytes(second.resolve(name))));
			}
		}
		finally
		{
			for (String name : List.of("high-five-generated-01.xml", "high-five-generated-02.xml"))
			{
				Files.deleteIfExists(first.resolve(name));
				Files.deleteIfExists(second.resolve(name));
			}
			Files.delete(first);
			Files.delete(second);
		}
	}

	private static void anchorControls()
	{
		final Probe probe = new Probe();
		probe.geo = false;
		check("NO_GEODATA", PhantomGeoValidationRules.anchor(A, List.of(B), 20, 100, probe).reason());
		probe.geo = true;
		probe.height = 500;
		check("OUTSIDE_GEOMETRY", PhantomGeoValidationRules.anchor(A, List.of(B), 20, 100, probe).reason());
		probe.height = 20;
		probe.unstable = true;
		check("NO_STABLE_Z", PhantomGeoValidationRules.anchor(A, List.of(B), 20, 100, probe).reason());
		probe.unstable = false;
		probe.move = false;
		check("NO_LOCAL_MOVEMENT", PhantomGeoValidationRules.anchor(A, List.of(B), 20, 100, probe).reason());
		probe.move = true;
		probe.height = 24;
		check("VALID", PhantomGeoValidationRules.anchor(A, List.of(B), 20, 100, probe).reason());
		check(24, PhantomGeoValidationRules.anchor(A, List.of(B), 20, 100, probe).point().z());
		check(true, PhantomGeoValidationRules.withinTargetDistance(A, List.of(B), 2000));
		check(false, PhantomGeoValidationRules.withinTargetDistance(A, List.of(new PhantomGeoValidationRules.Point(2201, 100, 20, 0)), 2000));
	}

	private static void routeControls()
	{
		final Probe probe = new Probe();
		check(2296, PhantomGeoValidationRules.requiredBuffer(new PhantomGeoValidationRules.Point(139714, -177456, -1536, 0), new PhantomGeoValidationRules.Point(124885, -159590, -1288, 0)));
		check(500, PhantomGeoValidationRules.maximumBuffer("100x6;128x6;192x6;256x4;320x4;384x4;500x2"));
		probe.move = false;
		check("DIRECT_BLOCKED_PATHFINDER_OUT_OF_RANGE", PhantomGeoValidationRules.route(A, new PhantomGeoValidationRules.Point(9000, 100, 20, 0), probe, 500).reason());
		check("NO_PATH_WITHIN_BUFFER", PhantomGeoValidationRules.route(A, B, probe, 500).reason());
		probe.move = true;
		check("VALID_DIRECT", PhantomGeoValidationRules.route(A, new PhantomGeoValidationRules.Point(9000, 100, 20, 0), probe, 500).reason());
		probe.oneWay = true;
		check("VALID_DIRECT", PhantomGeoValidationRules.route(A, B, probe).reason());
		check("NO_PATH", PhantomGeoValidationRules.route(B, A, probe).reason());
		probe.oneWay = false;
		probe.move = false;
		probe.path = List.of(new PhantomGeoValidationRules.Point(150, 100, 20, 0));
		probe.segmentMove = true;
		check("VALID_PATH", PhantomGeoValidationRules.route(A, B, probe).reason());
		probe.segmentMove = false;
		check("PATH_SEGMENT_BLOCKED", PhantomGeoValidationRules.route(A, B, probe).reason());
		probe.path = null;
		check("NO_PATH", PhantomGeoValidationRules.route(A, B, probe).reason());
		check("CROSS_INSTANCE", PhantomGeoValidationRules.route(A, new PhantomGeoValidationRules.Point(200, 100, 20, 1), probe).reason());
		check("NO_MOVEMENT", PhantomGeoValidationRules.route(A, A, probe).reason());
	}

	private static void check(Object expected, Object actual)
	{
		if (!expected.equals(actual))
		{
			throw new AssertionError("Expected " + expected + " but got " + actual);
		}
	}

	private static void chainControls()
	{
		final var a = new PhantomPathChainSearch.Waypoint("a", new PhantomGeoValidationRules.Point(100, 100, 20, 0), "FACTUAL", "a");
		final var middle = new PhantomPathChainSearch.Waypoint("middle", new PhantomGeoValidationRules.Point(200, 100, 20, 0), "ROUTE", "tile");
		final var b = new PhantomPathChainSearch.Waypoint("b", new PhantomGeoValidationRules.Point(300, 100, 20, 0), "FACTUAL", "b");
		final var probe = new ChainProbe();
		final var points = List.of(a, middle, b);
		final var path = PhantomPathChainSearch.search(points, "a", "b", 500, probe);
		check(2, path.size());
		check("a", path.getFirst().from().id());
		check("middle", path.getFirst().to().id());
		check("b", path.getLast().to().id());
		check("VALID_DIRECT", path.getFirst().proof().reason());
		check(path, PhantomPathChainSearch.search(points, "a", "b", 500, probe));
		check(List.of(), PhantomPathChainSearch.search(points, "b", "a", 500, probe));
		probe.blocked = true;
		check(List.of(), PhantomPathChainSearch.search(points, "a", "b", 500, probe));
		probe.pathCalls = 0;
		check(List.of(), PhantomPathChainSearch.search(List.of(a, b), "a", "b", 70, probe));
		check(0, probe.pathCalls);
	}

	private static final class ChainProbe implements PhantomGeoValidationRules.Probe
	{
		boolean blocked;
		int pathCalls;

		@Override
		public boolean hasGeo(int x, int y)
		{
			return true;
		}

		@Override
		public int height(int x, int y, int z)
		{
			return z;
		}

		@Override
		public boolean canMove(PhantomGeoValidationRules.Point from, PhantomGeoValidationRules.Point to)
		{
			return !blocked && (to.x() - from.x() == 100);
		}

		@Override
		public List<PhantomGeoValidationRules.Point> path(PhantomGeoValidationRules.Point from, PhantomGeoValidationRules.Point to)
		{
			pathCalls++;
			return null;
		}
	}

	private static final class Probe implements PhantomGeoValidationRules.Probe
	{
		boolean geo = true;
		boolean unstable;
		boolean move;
		boolean oneWay;
		boolean segmentMove;
		int height = 20;
		List<PhantomGeoValidationRules.Point> path;
		int calls;

		@Override
		public boolean hasGeo(int x, int y)
		{
			return geo;
		}

		@Override
		public int height(int x, int y, int z)
		{
			return unstable && ((calls++ % 2) == 1) ? height + 1 : height;
		}

		@Override
		public boolean canMove(PhantomGeoValidationRules.Point from, PhantomGeoValidationRules.Point to)
		{
			if (oneWay)
			{
				return from.x() < to.x();
			}
			return move || (segmentMove && (Math.abs(from.x() - to.x()) <= 60));
		}

		@Override
		public List<PhantomGeoValidationRules.Point> path(PhantomGeoValidationRules.Point from, PhantomGeoValidationRules.Point to)
		{
			return path;
		}
	}
}
