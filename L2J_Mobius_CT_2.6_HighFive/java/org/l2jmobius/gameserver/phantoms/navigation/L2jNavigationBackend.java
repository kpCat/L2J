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

import org.l2jmobius.gameserver.config.GeoEngineConfig;
import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.geoengine.pathfinding.GeoLocation;
import org.l2jmobius.gameserver.geoengine.pathfinding.PathFinding;

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
