package org.l2jmobius.tests.phantoms;

import java.util.ArrayList;
import java.util.List;

import org.l2jmobius.gameserver.geoengine.GeoEngine;

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
		return route(from, to, probe, Integer.MAX_VALUE, false);
	}

	static RouteProof connectorRoute(String kind, Point from, Point to, boolean hasFromSource, boolean hasToSource, Probe probe)
	{
		if ((from.instanceId() != to.instanceId()) || (from.instanceId() != 0))
		{
			return new RouteProof("CROSS_INSTANCE", 0, 0);
		}
		if ("DEST_TO_ANCHOR".equals(kind) && hasFromSource && hasToSource && (from.x() == to.x()) && (from.y() == to.y()) && probe.hasGeo(from.x(), from.y()))
		{
			final int stableZ = probe.height(from.x(), from.y(), from.z());
			if ((stableZ == probe.height(from.x(), from.y(), from.z())) && (stableZ == probe.height(from.x(), from.y(), stableZ)) && (to.z() == stableZ))
			{
				return new RouteProof("VALID_IDENTITY", 0, 0);
			}
		}
		return route(from, to, probe);
	}

	static int requiredBuffer(Point from, Point to)
	{
		final int deltaX = Math.abs(GeoEngine.getGeoX(from.x()) - GeoEngine.getGeoX(to.x()));
		final int deltaY = Math.abs(GeoEngine.getGeoY(from.y()) - GeoEngine.getGeoY(to.y()));
		return 64 + (2 * Math.max(deltaX, deltaY));
	}

	static int maximumBuffer(String configuration)
	{
		int maximum = 0;
		for (String entry : configuration.split(";"))
		{
			final String[] parts = entry.trim().split("x", -1);
			if ((parts.length != 2) || (Integer.parseInt(parts[1]) <= 0))
			{
				throw new IllegalArgumentException("Invalid PathFindBuffers entry: " + entry);
			}
			maximum = Math.max(maximum, Integer.parseInt(parts[0]));
		}
		return maximum;
	}

	static RouteProof route(Point from, Point to, Probe probe, int maximumBuffer)
	{
		return route(from, to, probe, maximumBuffer, true);
	}

	private static RouteProof route(Point from, Point to, Probe probe, int maximumBuffer, boolean classifyNull)
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
		if (requiredBuffer(from, to) > maximumBuffer)
		{
			return new RouteProof("DIRECT_BLOCKED_PATHFINDER_OUT_OF_RANGE", 0, 0);
		}
		final List<Point> path = probe.path(from, to);
		if ((path == null) || path.isEmpty())
		{
			return new RouteProof(classifyNull ? "NO_PATH_WITHIN_BUFFER" : "NO_PATH", 0, 0);
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
		return segments == 0 ? new RouteProof(classifyNull ? "NO_PATH_WITHIN_BUFFER" : "NO_PATH", 0, 0) : new RouteProof("VALID_PATH", length, segments);
	}

	private static long distance(Point from, Point to)
	{
		final long dx = (long) from.x() - to.x();
		final long dy = (long) from.y() - to.y();
		final long dz = (long) from.z() - to.z();
		return (long) Math.ceil(Math.sqrt((dx * dx) + (dy * dy) + (dz * dz)));
	}
}
