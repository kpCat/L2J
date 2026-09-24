package org.l2jmobius.gameserver.geoengine.pathfinding;

import java.util.List;

/** Test-tooling bridge to the package-local unfiltered path. */
public final class PathFindingRawAccess
{
	public static List<GeoLocation> find(PathFinding finder, int x, int y, int z, int tx, int ty, int tz, int instanceId, boolean playable)
	{
		return finder.findRawPath(x, y, z, tx, ty, tz, instanceId, playable);
	}
}
