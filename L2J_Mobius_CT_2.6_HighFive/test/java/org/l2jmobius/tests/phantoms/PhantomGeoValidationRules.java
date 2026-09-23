package org.l2jmobius.tests.phantoms;

import java.util.ArrayList;
import java.util.List;

/** Bounded, deterministic checks shared by the fixture tests and the real GeoEngine tool. */
final class PhantomGeoValidationRules
{
	record Point(int x, int y, int z, int instanceId)
	{
	}

	record AnchorProof(String reason, Point point)
	{
	}

	record RouteProof(String reason, long length, int segments)
	{
	}

	interface Probe
	{
		boolean hasGeo(int x, int y);

		int height(int x, int y, int z);

		boolean canMove(Point from, Point to);

		List<Point> path(Point from, Point to);
	}

	static AnchorProof anchor(Point candidate, List<Point> localPoints, int sourceZ, int maximumZDelta, Probe probe)
	{
		if (candidate.instanceId() != 0)
		{
			return new AnchorProof("CROSS_INSTANCE", null);
		}
		if (!probe.hasGeo(candidate.x(), candidate.y()))
		{
			return new AnchorProof("NO_GEODATA", null);
		}
		final int z = probe.height(candidate.x(), candidate.y(), sourceZ);
		if ((z != probe.height(candidate.x(), candidate.y(), sourceZ)) || (z != probe.height(candidate.x(), candidate.y(), z)))
		{
			return new AnchorProof("NO_STABLE_Z", null);
		}
		if (Math.abs((long) z - sourceZ) > maximumZDelta)
		{
			return new AnchorProof("OUTSIDE_GEOMETRY", null);
		}
		final Point normalized = new Point(candidate.x(), candidate.y(), z, 0);
		for (Point local : localPoints)
		{
			if ((local.instanceId() != 0) || ((local.x() == candidate.x()) && (local.y() == candidate.y())) || !probe.hasGeo(local.x(), local.y()))
			{
			continue;
			}
			final int localZ = probe.height(local.x(), local.y(), z);
			if ((localZ != probe.height(local.x(), local.y(), z)) || (localZ != probe.height(local.x(), local.y(), localZ)))
			{
			continue;
			}
			if (probe.canMove(normalized, new Point(local.x(), local.y(), localZ, 0)))
			{
				return new AnchorProof("VALID", normalized);
			}
		}
		return new AnchorProof("NO_LOCAL_MOVEMENT", null);
	}

	static boolean withinTargetDistance(Point anchor, List<Point> vertices, int maximumDistance)
	{
		if (vertices.isEmpty())
		{
			return false;
		}
		final long maximumSquared = (long) maximumDistance * maximumDistance;
		for (Point vertex : vertices)
		{
			final long dx = (long) anchor.x() - vertex.x();
			final long dy = (long) anchor.y() - vertex.y();
			if ((vertex.instanceId() != anchor.instanceId()) || ((dx * dx) + (dy * dy) > maximumSquared))
			{
				return false;
			}
		}
		return true;
	}

	static RouteProof route(Point from, Point to, Probe probe)
	{
		if ((from.instanceId() != to.instanceId()) || (from.instanceId() != 0))
		{
			return new RouteProof("CROSS_INSTANCE", 0, 0);
		}
		if ((from.x() == to.x()) && (from.y() == to.y()))
		{
			return new RouteProof("NO_MOVEMENT", 0, 0);
		}
		if (!probe.hasGeo(from.x(), from.y()) || !probe.hasGeo(to.x(), to.y()))
		{
			return new RouteProof("NO_GEODATA", 0, 0);
		}
		if (probe.canMove(from, to))
		{
			return new RouteProof("VALID_DIRECT", distance(from, to), 1);
		}
		final List<Point> path = probe.path(from, to);
		if ((path == null) || path.isEmpty())
		{
			return new RouteProof("NO_PATH", 0, 0);
		}
		if (path.size() > 256)
		{
			return new RouteProof("PATH_OVERSIZED", 0, 0);
		}
		final ArrayList<Point> waypoints = new ArrayList<>(path);
		waypoints.add(to);
		Point previous = from;
		long length = 0;
		int segments = 0;
		for (Point current : waypoints)
		{
			if (current.equals(previous))
			{
				continue;
			}
			if ((current.instanceId() != 0) || !probe.hasGeo(current.x(), current.y()) || (probe.height(current.x(), current.y(), current.z()) != current.z()) || !probe.canMove(previous, current))
			{
				return new RouteProof("PATH_SEGMENT_BLOCKED", 0, 0);
			}
			length += distance(previous, current);
			segments++;
			previous = current;
		}
		return segments == 0 ? new RouteProof("NO_PATH", 0, 0) : new RouteProof("VALID_PATH", length, segments);
	}

	private static long distance(Point from, Point to)
	{
		final long dx = (long) from.x() - to.x();
		final long dy = (long) from.y() - to.y();
		final long dz = (long) from.z() - to.z();
		return (long) Math.ceil(Math.sqrt((dx * dx) + (dy * dy) + (dz * dz)));
	}
}
