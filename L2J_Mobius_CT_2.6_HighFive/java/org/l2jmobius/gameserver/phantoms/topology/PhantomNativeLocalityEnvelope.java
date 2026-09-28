/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.topology;

import java.util.ArrayList;
import java.util.List;

import org.l2jmobius.gameserver.model.World;

/** World.getRegion conversion and WorldRegion.isSurroundingRegion, without creating World objects. */
public final class PhantomNativeLocalityEnvelope
{
	public record RegionKey(int instanceId, int x, int y, int z) {}

	private PhantomNativeLocalityEnvelope() {}

	public static RegionKey region(PhantomTopologyPoint point)
	{
		if (point == null) { return null; }
		final int x = (point.x() >> World.SHIFT_BY) + World.OFFSET_X;
		final int y = (point.y() >> World.SHIFT_BY) + World.OFFSET_Y;
		final int layers = (Math.abs(World.WORLD_Z_MIN) + Math.abs(World.WORLD_Z_MAX)) / World.Z_REGION_SIZE;
		final int z = point.z() < World.WORLD_Z_MIN ? 0 : point.z() > World.WORLD_Z_MAX ? layers - 1 : (point.z() - World.WORLD_Z_MIN) / World.Z_REGION_SIZE;
		// Exactly WORLD_Z_MAX is outside the native array too; preserve that boundary.
		return (x < 0) || (x > (World.WORLD_X_MAX >> World.SHIFT_BY) + World.OFFSET_X) || (y < 0) || (y > (World.WORLD_Y_MAX >> World.SHIFT_BY) + World.OFFSET_Y) || (z >= layers) ? null : new RegionKey(point.instanceId(), x, y, z);
	}

	public static boolean couldKnow(PhantomTopologyPoint human, PhantomTopologyPoint profile)
	{
		return contains(region(human), region(profile), 1);
	}

	public static boolean prewarm(PhantomTopologyPoint human, PhantomTopologyPoint profile)
	{
		return contains(region(human), region(profile), 2);
	}

	private static boolean contains(RegionKey human, RegionKey profile, int distance)
	{
		return (human != null) && (profile != null) && (human.instanceId() == profile.instanceId()) && (Math.abs(human.x() - profile.x()) <= distance) && (Math.abs(human.y() - profile.y()) <= distance) && (Math.abs(human.z() - profile.z()) <= distance);
	}

	static List<RegionKey> buckets(PhantomTopologyPoint human)
	{
		final RegionKey center = region(human);
		if (center == null) { return List.of(); }
		final List<RegionKey> result = new ArrayList<>(125);
		for (int x = center.x() - 2; x <= center.x() + 2; x++)
		{
			for (int y = center.y() - 2; y <= center.y() + 2; y++)
			{
				for (int z = center.z() - 2; z <= center.z() + 2; z++) { result.add(new RegionKey(center.instanceId(), x, y, z)); }
			}
		}
		return List.copyOf(result);
	}
}
