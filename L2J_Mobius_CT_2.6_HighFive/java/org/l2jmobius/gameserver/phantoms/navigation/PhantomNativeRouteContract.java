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

	public record Validation(Status status, List<PhantomNavigationPoint> waypoints, PhantomNavigationResult.BudgetProof proof)
	{
		public Validation(Status status, List<PhantomNavigationPoint> waypoints) { this(status, waypoints, PhantomNavigationResult.BudgetProof.unknown()); }
		public Validation { waypoints = List.copyOf(waypoints); java.util.Objects.requireNonNull(proof); }
	}

	public static Validation normalize(PhantomNavigationPoint origin, PhantomNavigationPoint destination, List<PhantomNavigationPoint> points, PhantomNavigationPolicy policy, double maximumDistance, BiPredicate<PhantomNavigationPoint, PhantomNavigationPoint> segment, Supplier<Status> interruption)
	{
		if ((points == null) || points.isEmpty()) { return rejected(Status.NO_PATH, PhantomNavigationResult.LimitKind.NO_PATH_WITHIN_SEARCH, origin, destination, maximumDistance, -1, policy.maximumWaypoints(), -1, -1, null); }
		if ((origin.instanceId() != destination.instanceId()) || !Double.isFinite(maximumDistance)) { return rejected(Status.BACKEND_FAILURE); }
		if (points.size() > policy.maximumWaypoints() + 1) { return rejected(Status.ROUTE_BUDGET_EXCEEDED, PhantomNavigationResult.LimitKind.RAW_WAYPOINTS, origin, destination, maximumDistance, -1, policy.maximumWaypoints() + 1, points.size(), -1, null); }
		if (origin.distanceTo(destination) > policy.maximumLocalStraightDistance()) { return rejected(Status.ROUTE_BUDGET_EXCEEDED, PhantomNavigationResult.LimitKind.CHORD_DISTANCE, origin, destination, policy.maximumLocalStraightDistance(), origin.distanceTo(destination), policy.maximumWaypoints(), points.size(), -1, destination); }
		final List<PhantomNavigationPoint> normalized = new ArrayList<>();
		PhantomNavigationPoint previous = origin;
		for (var point : points)
		{
			if ((point == null) || (point.instanceId() != origin.instanceId())) { return rejected(Status.BACKEND_FAILURE); }
			if (!previous.equals(point)) { normalized.add(point); previous = point; }
		}
		if (!previous.equals(destination)) { normalized.add(destination); }
		if (normalized.isEmpty()) { return rejected(Status.NO_PATH); }
		if (normalized.size() > policy.maximumWaypoints()) { return rejected(Status.ROUTE_BUDGET_EXCEEDED, PhantomNavigationResult.LimitKind.WAYPOINTS, origin, destination, maximumDistance, -1, policy.maximumWaypoints(), normalized.size(), -1, null); }
		double length = 0;
		previous = origin;
		int index = 0;
		for (var point : normalized)
		{
			length += previous.distanceTo(point);
			if (!Double.isFinite(length) || (length > Math.min(maximumDistance, policy.maximumRouteDistance()))) { return rejected(Status.ROUTE_BUDGET_EXCEEDED, PhantomNavigationResult.LimitKind.PATH_DISTANCE, origin, destination, Math.min(maximumDistance, policy.maximumRouteDistance()), Double.isFinite(length) ? length : -1, policy.maximumWaypoints(), normalized.size(), index, point); }
			previous = point; index++;
		}
		previous = origin; index = 0;
		for (var point : normalized)
		{
			final var interrupted = interruption.get(); if (interrupted != Status.PATH_FOUND) { return rejected(interrupted); }
			try { if (!segment.test(previous, point)) { return rejected(Status.ROUTE_OBSTRUCTED, PhantomNavigationResult.LimitKind.SEGMENT_BLOCKED, origin, destination, maximumDistance, length, policy.maximumWaypoints(), normalized.size(), index, point); } }
			catch (RuntimeException unavailable) { return rejected(Status.BACKEND_FAILURE); }
			previous = point; index++;
		}
		final var status = interruption.get();
		return status == Status.PATH_FOUND ? new Validation(status, normalized, proof(PhantomNavigationResult.LimitKind.NONE, origin, destination, maximumDistance, length, policy.maximumWaypoints(), normalized.size(), -1, null)) : rejected(status);
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
	private static PhantomNavigationResult.BudgetProof proof(PhantomNavigationResult.LimitKind kind, PhantomNavigationPoint origin, PhantomNavigationPoint destination, double maximumDistance, double used, int waypointLimit, int waypoints, int failed, PhantomNavigationPoint point)
	{ return new PhantomNavigationResult.BudgetProof(kind, "PATH", origin, destination, Double.isFinite(maximumDistance) ? maximumDistance : -1, used, waypointLimit, waypoints, -1, -1, -1, -1, failed, point, destination); }
	private static Validation rejected(Status status, PhantomNavigationResult.LimitKind kind, PhantomNavigationPoint origin, PhantomNavigationPoint destination, double maximumDistance, double used, int waypointLimit, int waypoints, int failed, PhantomNavigationPoint point)
	{ return new Validation(status, List.of(), proof(kind, origin, destination, maximumDistance, used, waypointLimit, waypoints, failed, point)); }
}
