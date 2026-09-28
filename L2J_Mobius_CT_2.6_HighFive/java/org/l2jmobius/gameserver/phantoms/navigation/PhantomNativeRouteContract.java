/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.navigation;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Supplier;

import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationResult.Status;

/** Pure structural contract shared by runtime and geometric compatibility probes. */
public final class PhantomNativeRouteContract
{
	private PhantomNativeRouteContract() { }

	public record Validation(Status status, List<PhantomNavigationPoint> waypoints)
	{
		public Validation { waypoints = List.copyOf(waypoints); }
	}

	public static Validation normalize(PhantomNavigationPoint origin, PhantomNavigationPoint destination, List<PhantomNavigationPoint> points, PhantomNavigationPolicy policy, double maximumDistance, BiPredicate<PhantomNavigationPoint, PhantomNavigationPoint> segment, Supplier<Status> interruption)
	{
		if ((points == null) || points.isEmpty()) { return rejected(Status.NO_PATH); }
		if ((origin.instanceId() != destination.instanceId()) || !Double.isFinite(maximumDistance)) { return rejected(Status.BACKEND_FAILURE); }
		if ((points.size() > policy.maximumWaypoints() + 1) || (origin.distanceTo(destination) > policy.maximumLocalStraightDistance())) { return rejected(Status.ROUTE_BUDGET_EXCEEDED); }
		final List<PhantomNavigationPoint> normalized = new ArrayList<>();
		PhantomNavigationPoint previous = origin;
		for (var point : points)
		{
			if ((point == null) || (point.instanceId() != origin.instanceId())) { return rejected(Status.BACKEND_FAILURE); }
			if (!previous.equals(point)) { normalized.add(point); previous = point; }
		}
		if (!previous.equals(destination)) { normalized.add(destination); }
		if (normalized.isEmpty()) { return rejected(Status.NO_PATH); }
		if (normalized.size() > policy.maximumWaypoints()) { return rejected(Status.ROUTE_BUDGET_EXCEEDED); }
		double length = 0;
		previous = origin;
		for (var point : normalized)
		{
			length += previous.distanceTo(point);
			if (!Double.isFinite(length) || (length > Math.min(maximumDistance, policy.maximumRouteDistance()))) { return rejected(Status.ROUTE_BUDGET_EXCEEDED); }
			previous = point;
		}
		final Status status = validateSegments(origin, normalized, segment, interruption);
		return status == Status.PATH_FOUND ? new Validation(status, normalized) : rejected(status);
	}

	public static Status validateSegments(PhantomNavigationPoint origin, List<PhantomNavigationPoint> waypoints, BiPredicate<PhantomNavigationPoint, PhantomNavigationPoint> segment, Supplier<Status> interruption)
	{
		PhantomNavigationPoint previous = origin;
		for (var waypoint : waypoints)
		{
			final Status interrupted = interruption.get();
			if (interrupted != Status.PATH_FOUND) { return interrupted; }
			try { if (!segment.test(previous, waypoint)) { return Status.ROUTE_OBSTRUCTED; } }
			catch (RuntimeException exception) { return Status.BACKEND_FAILURE; }
			previous = waypoint;
		}
		return interruption.get();
	}

	private static Validation rejected(Status status) { return new Validation(status, List.of()); }
}
