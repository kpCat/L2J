/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.navigation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.function.LongSupplier;

/** Bounded overlay of directed, proven native corridors. It never moves or owns a Player. */
public final class PhantomWalkCorridorPlanner
{
	public record Edge(PhantomNavigationPoint from, PhantomNavigationPoint to) { }
	public enum Status { FOUND, NO_ROUTE_WITHIN_BUDGET, SEARCH_LIMIT, CANCELLED, DEADLINE_EXPIRED }
	public record Result(Status status, List<PhantomNavigationPoint> points, int nodes, int edges, int queries, int labels) { }
	public interface Oracle
	{
		PhantomNavigationPoint project(int x, int y, PhantomNavigationPoint surface);
		boolean direct(PhantomNavigationPoint from, PhantomNavigationPoint to);
		List<PhantomNavigationPoint> path(PhantomNavigationPoint from, PhantomNavigationPoint to);
	}
	private static final int MAX_NODES = 128, MAX_EDGES = 512, MAX_QUERIES = 32, MAX_LABELS = 4096;
	private static final int STRIDE = 1024, LOCAL_SPAN = 3000;
	private static final int[][] DIRECTIONS = {{1,0},{1,1},{0,1},{-1,1},{-1,0},{-1,-1},{0,-1},{1,-1}};
	private final Oracle _oracle;
	private final LongSupplier _clock;
	/** One request may share the unchanged search limits across several WALK legs. */
	public static final class Budget
	{
		private int nodes, edges, queries, labels;
		boolean reserve(int addedNodes, int addedEdges)
		{
			if (addedNodes < 0 || addedEdges < 0 || nodes + addedNodes > MAX_NODES || edges + addedEdges > MAX_EDGES) { return false; }
			nodes += addedNodes; edges += addedEdges; return true;
		}
	}
	public PhantomWalkCorridorPlanner(Oracle oracle, LongSupplier clock) { _oracle = oracle; _clock = clock; }
	public Result plan(PhantomNavigationRequest request, PhantomNavigationCancellationToken cancellation, PhantomNavigationPolicy policy)
	{
		return plan(request, cancellation, policy, new Budget());
	}
	public Result plan(PhantomNavigationRequest request, PhantomNavigationCancellationToken cancellation, PhantomNavigationPolicy policy, Budget budget)
	{
		return new Search(request, cancellation, policy, java.util.Objects.requireNonNull(budget)).run();
	}
	private final class Search
	{
		private final PhantomNavigationRequest request;
		private final PhantomNavigationCancellationToken cancellation;
		private final long generation;
		private final PhantomNavigationPolicy policy;
		private final Budget budget;
		private final Map<PhantomNavigationPoint, Label> best = new HashMap<>();
		private final PriorityQueue<Label> open = new PriorityQueue<>((a,b) -> { int c = Double.compare(a.priority, b.priority); return c != 0 ? c : Long.compare(a.sequence, b.sequence); });
		private int edges, queries, labels;
		private boolean limited;
		private Search(PhantomNavigationRequest request, PhantomNavigationCancellationToken cancellation, PhantomNavigationPolicy policy, Budget budget)
		{
			this.request = request; this.cancellation = cancellation; generation = cancellation.generation(); this.policy = policy; this.budget = budget;
		}
		private Result run()
		{
			if (cancellation.changedSince(0)) { return result(Status.CANCELLED, List.of()); }
			if (interrupted() != null) { return result(interrupted(), List.of()); }
			if (request.origin().instanceId() != request.destination().instanceId()) { return result(Status.NO_ROUTE_WITHIN_BUDGET, List.of()); }
			if (budget.nodes >= MAX_NODES || budget.labels >= MAX_LABELS) { return result(Status.SEARCH_LIMIT, List.of()); }
			add(request.origin(), 0, null, List.of());
			if (_oracle.direct(request.origin(), request.destination()))
			{
				if (budget.edges >= MAX_EDGES || budget.labels >= MAX_LABELS || (!best.containsKey(request.destination()) && budget.nodes >= MAX_NODES)) { return result(Status.SEARCH_LIMIT, List.of()); }
				edges++; budget.edges++;
				add(request.destination(), request.origin().distanceTo(request.destination()), best.get(request.origin()), List.of(request.destination()));
				final Status interrupted = interrupted();
				return result(interrupted == null ? Status.FOUND : interrupted, interrupted == null ? List.of(request.destination()) : List.of());
			}
			while (!open.isEmpty())
			{
				final Status interrupted = interrupted();
				if (interrupted != null) { return result(interrupted, List.of()); }
				final Label current = open.poll();
				if (best.get(current.point) != current) { continue; }
				if (current.point.equals(request.destination())) { return result(Status.FOUND, points(current)); }
				final List<PhantomNavigationPoint> candidates = new ArrayList<>();
				if (local(current.point, request.destination())) { candidates.add(request.destination()); }
				for (int[] direction : DIRECTIONS)
				{
					final var candidate = _oracle.project(current.point.x() + direction[0] * STRIDE, current.point.y() + direction[1] * STRIDE, current.point);
					if ((candidate != null) && !candidate.equals(current.point) && !candidates.contains(candidate)) { candidates.add(candidate); }
				}
				// Ordering saves native queries; it never rejects a lateral or backwards candidate.
				candidates.sort((a,b) -> Double.compare(a.distanceTo(request.destination()), b.distanceTo(request.destination())));
				for (var candidate : candidates)
				{
					if (interrupted() != null) { return result(interrupted(), List.of()); }
					if (budget.edges >= MAX_EDGES) { return result(Status.SEARCH_LIMIT, List.of()); }
					edges++; budget.edges++;
					if (current.distance + current.point.distanceTo(candidate) > Math.min(request.maximumRouteDistance(), policy.maximumRouteDistance())) { limited = true; continue; }
					if (!best.containsKey(candidate) && budget.nodes >= MAX_NODES) { limited = true; continue; }
					List<PhantomNavigationPoint> corridor;
					if (_oracle.direct(current.point, candidate)) { corridor = List.of(candidate); }
					else
					{
						if (budget.queries >= MAX_QUERIES) { limited = true; continue; }
						queries++; budget.queries++;
						corridor = _oracle.path(current.point, candidate);
						final var proof = PhantomNativeRouteContract.normalize(current.point, candidate, corridor, policy, request.maximumRouteDistance(), _oracle::direct, () ->
						{
							final Status status = interrupted();
							return status == Status.CANCELLED ? PhantomNavigationResult.Status.CANCELLED : status == Status.DEADLINE_EXPIRED ? PhantomNavigationResult.Status.DEADLINE_EXPIRED : PhantomNavigationResult.Status.PATH_FOUND;
						});
						if (proof.status() != PhantomNavigationResult.Status.PATH_FOUND) { if (proof.status() == PhantomNavigationResult.Status.ROUTE_BUDGET_EXCEEDED) { limited = true; } continue; }
						corridor = proof.waypoints();
					}
					if (interrupted() != null) { return result(interrupted(), List.of()); }
					var previous = current.point; double distance = current.distance;
					for (var point : corridor) { distance += previous.distanceTo(point); previous = point; }
					if (distance > Math.min(request.maximumRouteDistance(), policy.maximumRouteDistance()) || points(current).size() + corridor.size() > policy.maximumWaypoints()) { limited = true; continue; }
					final var old = best.get(candidate);
					if ((old == null) || distance < old.distance)
					{
						if (budget.labels >= MAX_LABELS) { return result(Status.SEARCH_LIMIT, List.of()); }
						add(candidate, distance, current, corridor);
					}
				}
			}
			return result(limited ? Status.SEARCH_LIMIT : Status.NO_ROUTE_WITHIN_BUDGET, List.of());
		}
		private void add(PhantomNavigationPoint point, double distance, Label parent, List<PhantomNavigationPoint> corridor)
		{
			if (!best.containsKey(point)) { budget.nodes++; }
			budget.labels++;
			final var label = new Label(point, distance, distance + point.distanceTo(request.destination()), ++labels, parent, List.copyOf(corridor));
			best.put(point, label); open.add(label);
		}
		private List<PhantomNavigationPoint> points(Label label)
		{
			final List<List<PhantomNavigationPoint>> parts = new ArrayList<>();
			for (var current = label; current.parent != null; current = current.parent) { parts.addFirst(current.corridor); }
			final List<PhantomNavigationPoint> points = new ArrayList<>();
			for (var part : parts) { for (var point : part) { if (points.isEmpty() || !points.getLast().equals(point)) { points.add(point); } } }
			return List.copyOf(points);
		}
		private Status interrupted()
		{
			return cancellation.changedSince(generation) ? Status.CANCELLED : _clock.getAsLong() >= request.deadlineLogicalNanos() ? Status.DEADLINE_EXPIRED : null;
		}
		private Result result(Status status, List<PhantomNavigationPoint> points) { return new Result(status, points, best.size(), Math.min(edges, MAX_EDGES), queries, labels); }
	}
	private static boolean local(PhantomNavigationPoint from, PhantomNavigationPoint to)
	{
		return Math.max(Math.abs((long)from.x() - to.x()), Math.abs((long)from.y() - to.y())) <= LOCAL_SPAN;
	}
	private record Label(PhantomNavigationPoint point, double distance, double priority, long sequence, Label parent, List<PhantomNavigationPoint> corridor) { }
}
