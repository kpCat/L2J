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

import java.util.Objects;

/**
 * Immutable bounded local navigation request.
 */
public record PhantomNavigationRequest(long profileId, PhantomNavigationPoint origin, PhantomNavigationPoint destination, long submittedLogicalNanos, long deadlineLogicalNanos, int maximumRouteDistance, java.util.List<PveThreat> pveThreats)
{
	public record PveThreat(PhantomNavigationPoint point, int radius)
	{
		public PveThreat { Objects.requireNonNull(point); if (radius < 1 || radius > 12000) { throw new IllegalArgumentException("Invalid native threat radius."); } }
	}
	public PhantomNavigationRequest(long profileId, PhantomNavigationPoint origin, PhantomNavigationPoint destination, long submittedLogicalNanos, long deadlineLogicalNanos, int maximumRouteDistance)
	{
		this(profileId, origin, destination, submittedLogicalNanos, deadlineLogicalNanos, maximumRouteDistance, java.util.List.of());
	}
	/** Same exclusion/escape geometry as the live native PvE segment guard. */
	public boolean permitsWalkSegment(PhantomNavigationPoint from, PhantomNavigationPoint to)
	{
		if (pveThreats.isEmpty()) { return true; }
		if (from.instanceId() != to.instanceId() || from.instanceId() != origin.instanceId()) { return false; }
		final double dx = (double) to.x() - from.x(), dy = (double) to.y() - from.y(), squared = dx * dx + dy * dy;
		if (squared == 0) { return false; }
		for (var threat : pveThreats)
		{
			final var point = threat.point();
			if (point.instanceId() != from.instanceId() || Math.abs((long) point.z() - from.z()) > 256 && Math.abs((long) point.z() - to.z()) > 256) { continue; }
			final double mx = (double) point.x() - from.x(), my = (double) point.y() - from.y();
			final double projection = Math.clamp((mx * dx + my * dy) / squared, 0, 1);
			final double closest = Math.hypot(mx - projection * dx, my - projection * dy);
			final double before = Math.hypot(mx, my), after = Math.hypot((double) to.x() - point.x(), (double) to.y() - point.y());
			if (closest < Math.min(threat.radius(), before) - 16 || before < threat.radius() && after < before + 32) { return false; }
		}
		return true;
	}
	public PhantomNavigationRequest
	{
		pveThreats = java.util.List.copyOf(pveThreats);
		if (pveThreats.size() > 64) { throw new IllegalArgumentException("Too many observed native threats."); }
		if (profileId <= 0)
		{
			throw new IllegalArgumentException("profileId must be positive.");
		}
		Objects.requireNonNull(origin, "origin");
		Objects.requireNonNull(destination, "destination");
		if (origin.instanceId() != destination.instanceId())
		{
			throw new IllegalArgumentException("Origin and destination must use the same instance.");
		}
		if (submittedLogicalNanos < 0)
		{
			throw new IllegalArgumentException("submittedLogicalNanos must not be negative.");
		}
		if (deadlineLogicalNanos <= submittedLogicalNanos)
		{
			throw new IllegalArgumentException("deadlineLogicalNanos must be after submission.");
		}
		if ((maximumRouteDistance < 1) || (maximumRouteDistance > 100_000))
		{
			throw new IllegalArgumentException("maximumRouteDistance must be between 1 and 100000.");
		}
	}
}
