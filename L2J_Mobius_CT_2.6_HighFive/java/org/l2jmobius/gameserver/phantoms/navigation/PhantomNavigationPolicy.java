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

/**
 * Immutable structural safety bounds. Operational tuning remains a later goal.
 */
public record PhantomNavigationPolicy(int maximumQueuedRequests, int maximumConcurrentPathfinders, int maximumTrackedProfiles, int maximumCacheEntries, long cacheTtlMillis, long pathfindingCooldownMillis, int maximumLocalStraightDistance, int maximumWaypoints, int maximumRouteDistance, long defaultRequestDeadlineMillis, long stuckWindowMillis, int minimumProgress, int arrivalRadius, long maximumAttemptDurationMillis)
{
	public PhantomNavigationPolicy
	{
		requireRange(maximumQueuedRequests, 1, 10_000, "maximumQueuedRequests");
		requireRange(maximumConcurrentPathfinders, 1, 2, "maximumConcurrentPathfinders");
		requireRange(maximumTrackedProfiles, 1, 1_000_000, "maximumTrackedProfiles");
		requireRange(maximumCacheEntries, 1, 100_000, "maximumCacheEntries");
		requireRange(cacheTtlMillis, 1, 86_400_000, "cacheTtlMillis");
		requireRange(pathfindingCooldownMillis, 1, 86_400_000, "pathfindingCooldownMillis");
		requireRange(maximumLocalStraightDistance, 1, 100_000, "maximumLocalStraightDistance");
		requireRange(maximumWaypoints, 1, 64, "maximumWaypoints");
		requireRange(maximumRouteDistance, 1, 100_000, "maximumRouteDistance");
		requireRange(defaultRequestDeadlineMillis, 1, 60_000, "defaultRequestDeadlineMillis");
		requireRange(stuckWindowMillis, 1, 3_600_000, "stuckWindowMillis");
		requireRange(minimumProgress, 1, 100_000, "minimumProgress");
		requireRange(arrivalRadius, 0, 100_000, "arrivalRadius");
		requireRange(maximumAttemptDurationMillis, 1, 86_400_000, "maximumAttemptDurationMillis");
		if (maximumAttemptDurationMillis < stuckWindowMillis)
		{
			throw new IllegalArgumentException("maximumAttemptDurationMillis must not be shorter than stuckWindowMillis.");
		}
	}

	public static PhantomNavigationPolicy productionDefaults()
	{
		return new PhantomNavigationPolicy(256, 2, 10_000, 1024, 5000, 1000, 12_000, 64, 100_000, 1000, 3000, 20, 50, 120_000);
	}

	public long cacheTtlNanos()
	{
		return millisToNanos(cacheTtlMillis);
	}

	public long pathfindingCooldownNanos()
	{
		return millisToNanos(pathfindingCooldownMillis);
	}

	public long defaultRequestDeadlineNanos()
	{
		return millisToNanos(defaultRequestDeadlineMillis);
	}

	public long stuckWindowNanos()
	{
		return millisToNanos(stuckWindowMillis);
	}

	public long maximumAttemptDurationNanos()
	{
		return millisToNanos(maximumAttemptDurationMillis);
	}

	private static long millisToNanos(long value)
	{
		return Math.multiplyExact(value, 1_000_000L);
	}

	private static void requireRange(long value, long minimum, long maximum, String name)
	{
		if ((value < minimum) || (value > maximum))
		{
			throw new IllegalArgumentException(name + " must be between " + minimum + " and " + maximum + ".");
		}
	}
}
