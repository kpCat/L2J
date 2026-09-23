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
		shardControls();
		System.out.println("PHANTOM GEO RULES: 17/17 PASS");
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
		probe.move = true;
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
