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
package org.l2jmobius.gameserver.phantoms.navigation;

import org.l2jmobius.gameserver.model.World;

/**
 * Immutable Phantom-owned world position.
 */
public record PhantomNavigationPoint(int x, int y, int z, int instanceId)
{
	public PhantomNavigationPoint
	{
		if ((x < World.WORLD_X_MIN) || (x > World.WORLD_X_MAX))
		{
			throw new IllegalArgumentException("x is outside the world bounds.");
		}
		if ((y < World.WORLD_Y_MIN) || (y > World.WORLD_Y_MAX))
		{
			throw new IllegalArgumentException("y is outside the world bounds.");
		}
		if ((z < World.WORLD_Z_MIN) || (z > World.WORLD_Z_MAX))
		{
			throw new IllegalArgumentException("z is outside the world bounds.");
		}
		if (instanceId < 0)
		{
			throw new IllegalArgumentException("instanceId must not be negative.");
		}
	}

	public double distanceTo(PhantomNavigationPoint other)
	{
		if (other == null)
		{
			throw new IllegalArgumentException("other point must not be null.");
		}
		if (instanceId != other.instanceId)
		{
			throw new IllegalArgumentException("Points from different instances cannot be measured.");
		}
		final double deltaX = (double) other.x - x;
		final double deltaY = (double) other.y - y;
		final double deltaZ = (double) other.z - z;
		return Math.sqrt((deltaX * deltaX) + (deltaY * deltaY) + (deltaZ * deltaZ));
	}
}
