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
public record PhantomNavigationRequest(long profileId, PhantomNavigationPoint origin, PhantomNavigationPoint destination, long submittedLogicalNanos, long deadlineLogicalNanos, int maximumRouteDistance)
{
	public PhantomNavigationRequest
	{
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
