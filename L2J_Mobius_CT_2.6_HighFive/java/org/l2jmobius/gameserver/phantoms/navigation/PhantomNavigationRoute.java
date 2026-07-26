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

import java.util.List;
import java.util.Objects;

import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationBackend.CapabilitySnapshot;

/**
 * Immutable Phantom-owned route. Waypoints exclude the separately stored origin.
 */
public final class PhantomNavigationRoute
{
	public enum Mode
	{
		DIRECT_VALIDATED,
		DIRECT_UNVERIFIED_NO_GEODATA,
		COMPUTED
	}

	private final Mode _mode;
	private final PhantomNavigationPoint _origin;
	private final PhantomNavigationPoint _destination;
	private final List<PhantomNavigationPoint> _waypoints;
	private final double _totalDistance;
	private final CapabilitySnapshot _geodataCapability;
	private final long _createdLogicalNanos;
	private final boolean _cacheable;

	public PhantomNavigationRoute(Mode mode, PhantomNavigationPoint origin, PhantomNavigationPoint destination, List<PhantomNavigationPoint> waypoints, CapabilitySnapshot geodataCapability, long createdLogicalNanos, boolean cacheable, int maximumWaypoints, double maximumRouteDistance)
	{
		_mode = Objects.requireNonNull(mode, "mode");
		_origin = Objects.requireNonNull(origin, "origin");
		_destination = Objects.requireNonNull(destination, "destination");
		_geodataCapability = Objects.requireNonNull(geodataCapability, "geodataCapability");
		if (origin.instanceId() != destination.instanceId())
		{
			throw new IllegalArgumentException("Route endpoints must use the same instance.");
		}
		if ((maximumWaypoints < 1) || (maximumWaypoints > 64))
		{
			throw new IllegalArgumentException("maximumWaypoints must be between 1 and 64.");
		}
		if (!(maximumRouteDistance >= 0) || !Double.isFinite(maximumRouteDistance))
		{
			throw new IllegalArgumentException("maximumRouteDistance must be finite and non-negative.");
		}
		if (createdLogicalNanos < 0)
		{
			throw new IllegalArgumentException("createdLogicalNanos must not be negative.");
		}
		final List<PhantomNavigationPoint> copy = List.copyOf(Objects.requireNonNull(waypoints, "waypoints"));
		if (copy.isEmpty() || (copy.size() > maximumWaypoints))
		{
			throw new IllegalArgumentException("Route waypoint count is outside its bound.");
		}
		if (!copy.getLast().equals(destination))
		{
			throw new IllegalArgumentException("The final waypoint must equal the exact destination.");
		}
		double totalDistance = 0;
		PhantomNavigationPoint previous = origin;
		for (PhantomNavigationPoint point : copy)
		{
			Objects.requireNonNull(point, "Route waypoint must not be null.");
			if (point.instanceId() != origin.instanceId())
			{
				throw new IllegalArgumentException("Every route point must use the request instance.");
			}
			if (previous.equals(point) && (copy.size() > 1))
			{
				throw new IllegalArgumentException("Adjacent duplicate route points are not allowed.");
			}
			totalDistance += previous.distanceTo(point);
			if (!Double.isFinite(totalDistance) || (totalDistance > maximumRouteDistance))
			{
				throw new IllegalArgumentException("Route distance exceeds its bound.");
			}
			previous = point;
		}
		_waypoints = copy;
		_totalDistance = totalDistance;
		_createdLogicalNanos = createdLogicalNanos;
		_cacheable = cacheable;
	}

	public Mode mode()
	{
		return _mode;
	}

	public PhantomNavigationPoint origin()
	{
		return _origin;
	}

	public PhantomNavigationPoint destination()
	{
		return _destination;
	}

	public List<PhantomNavigationPoint> waypoints()
	{
		return _waypoints;
	}

	public double totalDistance()
	{
		return _totalDistance;
	}

	public CapabilitySnapshot geodataCapability()
	{
		return _geodataCapability;
	}

	public long createdLogicalNanos()
	{
		return _createdLogicalNanos;
	}

	public boolean cacheable()
	{
		return _cacheable;
	}
}
