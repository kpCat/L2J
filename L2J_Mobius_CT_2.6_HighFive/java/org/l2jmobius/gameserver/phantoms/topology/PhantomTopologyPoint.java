/*
 * Copyright (c) 2013 L2jMobius
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
 * IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.l2jmobius.gameserver.phantoms.topology;

import org.l2jmobius.gameserver.model.World;

/**
 * Immutable topology coordinate without a server object reference.
 */
public record PhantomTopologyPoint(int x, int y, int z, int instanceId)
{
	public PhantomTopologyPoint
	{
		if ((x < World.WORLD_X_MIN) || (x > World.WORLD_X_MAX) || (y < World.WORLD_Y_MIN) || (y > World.WORLD_Y_MAX) || (z < World.WORLD_Z_MIN) || (z > World.WORLD_Z_MAX))
		{
			throw new IllegalArgumentException("Topology point is outside world bounds.");
		}
		if (instanceId < 0)
		{
			throw new IllegalArgumentException("Topology point instanceId must be non-negative.");
		}
	}

	public long distanceSquared2D(PhantomTopologyPoint other)
	{
		if ((other == null) || (instanceId != other.instanceId))
		{
			return Long.MAX_VALUE;
		}
		final long dx = (long) x - other.x;
		final long dy = (long) y - other.y;
		return (dx * dx) + (dy * dy);
	}

	public double distance3D(PhantomTopologyPoint other)
	{
		if ((other == null) || (instanceId != other.instanceId))
		{
			return Double.POSITIVE_INFINITY;
		}
		final long dx = (long) x - other.x;
		final long dy = (long) y - other.y;
		final long dz = (long) z - other.z;
		return Math.sqrt((dx * dx) + (dy * dy) + (dz * dz));
	}
}
