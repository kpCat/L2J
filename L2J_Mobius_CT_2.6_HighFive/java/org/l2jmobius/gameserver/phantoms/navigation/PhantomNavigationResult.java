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

import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationBackend.CapabilitySnapshot;

public record PhantomNavigationResult(Status status, long profileId, long requestId, CapabilitySnapshot capability, PhantomNavigationRoute route, boolean fromCache, long completedLogicalNanos)
{
	public enum Status
	{
		DIRECT_VALIDATED,
		DIRECT_UNVERIFIED_NO_GEODATA,
		PATH_FOUND,
		NO_GEODATA,
		PATHFINDING_DISABLED,
		NO_PATH,
		ROUTE_BUDGET_EXCEEDED,
		QUEUE_BACKPRESSURE,
		PROFILE_BUSY,
		COOLDOWN,
		CANCELLED,
		DEADLINE_EXPIRED,
		BACKEND_FAILURE,
		SERVICE_NOT_RUNNING
	}

	public PhantomNavigationResult
	{
		if (status == null)
		{
			throw new IllegalArgumentException("status must not be null.");
		}
		if (profileId <= 0)
		{
			throw new IllegalArgumentException("profileId must be positive.");
		}
		if (requestId < 0)
		{
			throw new IllegalArgumentException("requestId must not be negative.");
		}
		if (completedLogicalNanos < 0)
		{
			throw new IllegalArgumentException("completedLogicalNanos must not be negative.");
		}
		final boolean successful = (status == Status.DIRECT_VALIDATED) || (status == Status.DIRECT_UNVERIFIED_NO_GEODATA) || (status == Status.PATH_FOUND);
		if (successful != (route != null))
		{
			throw new IllegalArgumentException("Only successful results may contain a route.");
		}
		if (fromCache && (status != Status.PATH_FOUND))
		{
			throw new IllegalArgumentException("Only computed path results may come from cache.");
		}
	}
}
