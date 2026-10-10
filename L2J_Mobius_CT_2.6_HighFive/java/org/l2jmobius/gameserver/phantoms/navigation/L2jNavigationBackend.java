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

import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;

import org.l2jmobius.gameserver.config.GeoEngineConfig;
import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.geoengine.pathfinding.GeoLocation;
import org.l2jmobius.gameserver.geoengine.pathfinding.PathFinding;
import org.l2jmobius.gameserver.geoengine.util.GridLineIterator2D;
import org.l2jmobius.gameserver.managers.ZoneManager;
import org.l2jmobius.gameserver.model.zone.type.WaterZone;

/**
 * Lazy factual adapter over the current High Five geo/pathfinding APIs.
 */
public final class L2jNavigationBackend implements PhantomNavigationBackend
{
	@Override
	public CapabilitySnapshot capability(PhantomNavigationPoint origin, PhantomNavigationPoint destination)
	{
		final GeoEngine geoEngine = GeoEngine.getInstance();
		final boolean startGeo = geoEngine.hasGeo(origin.x(), origin.y());
		final boolean targetGeo = geoEngine.hasGeo(destination.x(), destination.y());
		final PhantomNavigationCapability mode;
		if (!startGeo && !targetGeo)
		{
			mode = PhantomNavigationCapability.NO_GEODATA;
		}
		else if (startGeo != targetGeo)
		{
			mode = PhantomNavigationCapability.PARTIAL_GEODATA;
		}
		else if (GeoEngineConfig.PATHFINDING > 0)
		{
			mode = PhantomNavigationCapability.GEODATA_PATHFINDING;
		}
		else
		{
			mode = PhantomNavigationCapability.GEODATA_DIRECT_ONLY;
		}
		return new CapabilitySnapshot(mode, mode.ordinal());
	}

	@Override
	public boolean canMoveDirect(PhantomNavigationPoint origin, PhantomNavigationPoint destination)
	{
		return GeoEngine.getInstance().canMoveToTarget(origin.x(), origin.y(), origin.z(), destination.x(), destination.y(), destination.z(), origin.instanceId());
	}

	/** The existing visible-travel dry-cell/height contract, plus factual doors/NSWE. */
	public boolean canWalkDirect(PhantomNavigationPoint from, PhantomNavigationPoint to)
	{
		final var geo = GeoEngine.getInstance();
		if (from.instanceId() != to.instanceId() || !geo.hasGeo(from.x(), from.y()) || !geo.hasGeo(to.x(), to.y()) || geo.getHeight(to.x(), to.y(), to.z()) != to.z()) { return false; }
		boolean dry = ZoneManager.getInstance().getZone(from.x(), from.y(), from.z(), WaterZone.class) == null;
		int z = from.z();
		final var cells = new GridLineIterator2D(GeoEngine.getGeoX(from.x()), GeoEngine.getGeoY(from.y()), GeoEngine.getGeoX(to.x()), GeoEngine.getGeoY(to.y()));
		while (cells.next())
		{
			final int x = GeoEngine.getWorldX(cells.x()), y = GeoEngine.getWorldY(cells.y());
			if (!geo.hasGeo(x, y)) { return false; }
			z = geo.getHeight(x, y, z);
			if (z != geo.getHeight(x, y, z)) { return false; }
			if (ZoneManager.getInstance().getZone(x, y, z, WaterZone.class) != null) { if (dry) { return false; } }
			else { dry = true; }
		}
		return ZoneManager.getInstance().getZone(to.x(), to.y(), to.z(), WaterZone.class) == null && canMoveDirect(from, to);
	}

	public PhantomWalkCorridorPlanner.Result findWalkCorridor(PhantomNavigationRequest request, PhantomNavigationCancellationToken cancellation, PhantomNavigationPolicy policy, LongSupplier clock)
	{
		final var planner = new PhantomWalkCorridorPlanner(new PhantomWalkCorridorPlanner.Oracle()
		{
			@Override public PhantomNavigationPoint project(int x, int y, PhantomNavigationPoint surface)
			{
				final var geo = GeoEngine.getInstance();
				if (!geo.hasGeo(x, y)) { return null; }
				final int z = geo.getHeight(x, y, surface.z());
				if (z != geo.getHeight(x, y, z) || ZoneManager.getInstance().getZone(x, y, z, WaterZone.class) != null) { return null; }
				return new PhantomNavigationPoint(x, y, z, surface.instanceId());
			}
			@Override public boolean direct(PhantomNavigationPoint from, PhantomNavigationPoint to) { return canWalkDirect(from, to); }
			@Override public List<PhantomNavigationPoint> path(PhantomNavigationPoint from, PhantomNavigationPoint to)
			{
				return findPath(new PhantomNavigationRequest(request.profileId(), from, to, request.submittedLogicalNanos(), request.deadlineLogicalNanos(), request.maximumRouteDistance()), cancellation);
			}
		}, clock);
		return planner.plan(request, cancellation, policy);
	}

	@Override
	public List<PhantomNavigationPoint> findPath(PhantomNavigationRequest request, PhantomNavigationCancellationToken cancellationToken)
	{
		final List<GeoLocation> path = PathFinding.getInstance().findPath(
			request.origin().x(),
			request.origin().y(),
			request.origin().z(),
			request.destination().x(),
			request.destination().y(),
			request.destination().z(),
			request.origin().instanceId(),
			true);
		if (path == null)
		{
			return null;
		}
		final List<PhantomNavigationPoint> copy = new ArrayList<>(path.size());
		for (GeoLocation point : path)
		{
			copy.add(new PhantomNavigationPoint(point.getX(), point.getY(), point.getZ(), request.origin().instanceId()));
		}
		return List.copyOf(copy);
	}
}
