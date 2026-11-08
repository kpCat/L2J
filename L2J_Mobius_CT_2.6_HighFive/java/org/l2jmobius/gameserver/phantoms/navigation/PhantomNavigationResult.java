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

public record PhantomNavigationResult(Status status, long profileId, long requestId, CapabilitySnapshot capability, PhantomNavigationRoute route, boolean fromCache, long completedLogicalNanos, BudgetProof proof)
{
	public PhantomNavigationResult(Status status, long profileId, long requestId, CapabilitySnapshot capability, PhantomNavigationRoute route, boolean fromCache, long completedLogicalNanos)
	{ this(status, profileId, requestId, capability, route, fromCache, completedLogicalNanos, BudgetProof.unknown()); }
	public enum LimitKind { NONE, UNKNOWN, CHORD_DISTANCE, PATH_DISTANCE, RAW_WAYPOINTS, WAYPOINTS, SEARCH_LIMIT, SEGMENT_BLOCKED, NO_PATH_WITHIN_SEARCH }
	/** -1 means unavailable, never a reconstructed historical measurement. */
	public record BudgetProof(LimitKind kind, String requestKind, PhantomNavigationPoint origin, PhantomNavigationPoint destination,
		double distanceLimit, double distanceUsed, int waypointLimit, int waypointsUsed, int nodes, int edges, int queries, int labels, int failedSegment, PhantomNavigationPoint failedPoint, PhantomNavigationPoint submittedDestination)
	{
		public BudgetProof
		{
			if (kind == null || requestKind == null || !requestKind.matches("[A-Z_]{1,32}") || (origin == null) != (destination == null)
				|| !Double.isFinite(distanceLimit) || !Double.isFinite(distanceUsed) || distanceLimit < -1 || distanceUsed < -1
				|| waypointLimit < -1 || waypointsUsed < -1 || nodes < -1 || edges < -1 || queries < -1 || labels < -1 || failedSegment < -1)
			{ throw new IllegalArgumentException("Invalid navigation budget proof."); }
		}
		public static BudgetProof unknown() { return new BudgetProof(LimitKind.UNKNOWN, "UNKNOWN", null, null, -1, -1, -1, -1, -1, -1, -1, -1, -1, null, null); }
		public static BudgetProof context(LimitKind kind, String mode, PhantomNavigationRequest request, PhantomNavigationPolicy policy)
		{ return new BudgetProof(kind, mode, request.origin(), request.destination(), Math.min(request.maximumRouteDistance(), policy.maximumRouteDistance()), -1, policy.maximumWaypoints(), -1, -1, -1, -1, -1, -1, null, request.destination()); }
		public BudgetProof submission(PhantomNavigationRequest request, String mode) { return new BudgetProof(kind, mode, origin, destination, distanceLimit, distanceUsed, waypointLimit, waypointsUsed, nodes, edges, queries, labels, failedSegment, failedPoint, request.destination()); }
		public BudgetProof search(PhantomWalkCorridorPlanner.Result result)
		{ return new BudgetProof(kind, requestKind, origin, destination, distanceLimit, distanceUsed, waypointLimit, waypointsUsed, result.nodes(), result.edges(), result.queries(), result.labels(), failedSegment, failedPoint, submittedDestination); }
		public BudgetProof search(BudgetProof other)
		{ return new BudgetProof(kind, requestKind, origin, destination, distanceLimit, distanceUsed, waypointLimit, waypointsUsed, other.nodes, other.edges, other.queries, other.labels, failedSegment, failedPoint, submittedDestination); }
		public int nodeLimit() { return PhantomWalkCorridorPlanner.MAX_NODES; }
		public int edgeLimit() { return PhantomWalkCorridorPlanner.MAX_EDGES; }
		public int queryLimit() { return PhantomWalkCorridorPlanner.MAX_QUERIES; }
		public int labelLimit() { return PhantomWalkCorridorPlanner.MAX_LABELS; }
		public String diagnostic() { return toString() + ";searchLimits=" + (queries < 0 ? "UNKNOWN" : nodeLimit() + "/" + edgeLimit() + "/" + queryLimit() + "/" + labelLimit()); }
	}
	public enum Status
	{
		DIRECT_VALIDATED,
		DIRECT_UNVERIFIED_NO_GEODATA,
		PATH_FOUND,
		NO_GEODATA,
		PATHFINDING_DISABLED,
		NO_PATH,
		ROUTE_OBSTRUCTED,
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
		java.util.Objects.requireNonNull(proof, "proof");
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
