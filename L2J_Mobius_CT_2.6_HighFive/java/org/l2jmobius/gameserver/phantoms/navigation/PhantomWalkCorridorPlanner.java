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
	/**
	 * One local proof, not an arrival at the remote destination. The caller must
	 * observe physical arrival at points.getLast() before admitting another episode.
	 * No unproved tail is appended and every alternative shares the original budget.
	 */
	public Result planEpisode(PhantomNavigationRequest request, PhantomNavigationCancellationToken cancellation, PhantomNavigationPolicy policy)
	{
		return planEpisode(request, cancellation, policy, java.util.Set.of());
	}
	public Result planEpisode(PhantomNavigationRequest request, PhantomNavigationCancellationToken cancellation, PhantomNavigationPolicy policy, java.util.Set<PhantomNavigationPoint> visited)
	{
		if (visited.size() > 64) { throw new IllegalArgumentException("Too many physical episode checkpoints."); }
		final var budget = new Budget();
		if (request.origin().instanceId() != request.destination().instanceId()) { return new Result(Status.NO_ROUTE_WITHIN_BUDGET, List.of(), 0, 0, 0, 0); }
		final double bearing = Math.atan2((double) request.destination().y() - request.origin().y(), (double) request.destination().x() - request.origin().x());
		final List<PhantomNavigationPoint> candidates = new ArrayList<>();
		if (request.origin().distanceTo(request.destination()) <= LOCAL_SPAN) { candidates.add(request.destination()); }
		// Probe the intended surface as well as the current one. A different Z is
		// only a candidate: normalize must still prove every native connecting segment.
		for (var surface : request.origin().z() == request.destination().z() ? List.of(request.origin()) : List.of(request.destination(), request.origin()))
		{
			for (int span : new int[] {LOCAL_SPAN, 1500, 750})
			{
				for (double turn : new double[] {0, -Math.PI / 6, Math.PI / 6})
				{
					final Status interrupted = episodeInterruption(request, cancellation);
					if (interrupted != null) { return episodeResult(interrupted, List.of(), budget); }
					final var from = request.origin();
					final var checkpoint = _oracle.project(from.x() + (int) Math.round(span * Math.cos(bearing + turn)), from.y() + (int) Math.round(span * Math.sin(bearing + turn)), surface);
					if (checkpoint != null && checkpoint.instanceId() == from.instanceId() && !checkpoint.equals(from) && !candidates.contains(checkpoint)) { candidates.add(checkpoint); }
				}
			}
			// A heuristic orders work; it is not a reachability veto.
			final var escapes = new ArrayList<PhantomNavigationPoint>();
			for (var direction : DIRECTIONS)
			{
				final var point = _oracle.project(request.origin().x() + direction[0] * STRIDE, request.origin().y() + direction[1] * STRIDE, surface);
				if (point != null && point.instanceId() == request.origin().instanceId() && !point.equals(request.origin()) && !candidates.contains(point)) { escapes.add(point); }
			}
			escapes.sort(java.util.Comparator.comparingDouble(point -> point.distanceTo(request.destination())));
			candidates.addAll(escapes);
		}
		// XY-first success can keep a multilayer journey on the wrong floor forever.
		// Rank actual projected surfaces before spending the shared native-query budget.
		// This is only frontier ordering: every released segment still needs native proof.
		candidates.sort(java.util.Comparator.comparingDouble((PhantomNavigationPoint point) -> point.distanceTo(request.destination())
			+ 2d * Math.abs((long) point.z() - request.destination().z())).thenComparingInt(PhantomNavigationPoint::x).thenComparingInt(PhantomNavigationPoint::y).thenComparingInt(PhantomNavigationPoint::z));
		budget.nodes = 1; budget.labels = 1;
		PhantomNavigationPoint fallback = null;
		for (var target : candidates)
		{
			final Status interrupted = episodeInterruption(request, cancellation);
			if (interrupted != null) { return episodeResult(interrupted, List.of(), budget); }
			if (!target.equals(request.destination()) && visited.stream().anyMatch(point -> point.instanceId() == target.instanceId() && Math.hypot((long) point.x() - target.x(), (long) point.y() - target.y()) < 256 && Math.abs((long) point.z() - target.z()) < 100)) { continue; }
			if (fallback == null) { fallback = target; }
			if (!budget.reserve(1, 1)) { return episodeResult(Status.SEARCH_LIMIT, List.of(), budget); }
			budget.labels++;
			List<PhantomNavigationPoint> points;
			if (_oracle.direct(request.origin(), target)) { points = List.of(target); }
			else
			{
				if (budget.queries >= MAX_QUERIES) { return episodeResult(Status.SEARCH_LIMIT, List.of(), budget); }
				budget.queries++;
				points = _oracle.path(request.origin(), target);
			}
			final var proof = PhantomNativeRouteContract.normalize(request.origin(), target, points, policy, request.maximumRouteDistance(), _oracle::direct,
				() -> cancellation.changedSince(0) ? PhantomNavigationResult.Status.CANCELLED : _clock.getAsLong() >= request.deadlineLogicalNanos() ? PhantomNavigationResult.Status.DEADLINE_EXPIRED : PhantomNavigationResult.Status.PATH_FOUND);
			if (episodeInterruption(request, cancellation) != null) { return episodeResult(episodeInterruption(request, cancellation), List.of(), budget); }
			if (proof.status() == PhantomNavigationResult.Status.PATH_FOUND) { return episodeResult(Status.FOUND, proof.waypoints(), budget); }
		}
		if (fallback == null) { return episodeResult(Status.NO_ROUTE_WITHIN_BUDGET, List.of(), budget); }
		final var local = new PhantomNavigationRequest(request.profileId(), request.origin(), fallback, request.submittedLogicalNanos(), request.deadlineLogicalNanos(), request.maximumRouteDistance());
		final var result = plan(local, cancellation, policy, budget);
		return episodeResult(result.status(), result.points(), budget);
	}
	private Status episodeInterruption(PhantomNavigationRequest request, PhantomNavigationCancellationToken cancellation)
	{
		return cancellation.changedSince(0) ? Status.CANCELLED : _clock.getAsLong() >= request.deadlineLogicalNanos() ? Status.DEADLINE_EXPIRED : null;
	}
	private static Result episodeResult(Status status, List<PhantomNavigationPoint> points, Budget budget) { return new Result(status, points, budget.nodes, budget.edges, budget.queries, budget.labels); }
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
			// A narrow native passage need not contain any point of the coarse overlay grid.
			// Try the complete local query once, retaining the same shared budget and proof contract.
			if (request.origin().distanceTo(request.destination()) <= policy.maximumLocalStraightDistance() && budget.queries < MAX_QUERIES)
			{
				queries++; budget.queries++;
				final var nativePath = _oracle.path(request.origin(), request.destination());
				final var proof = PhantomNativeRouteContract.normalize(request.origin(), request.destination(), nativePath, policy, request.maximumRouteDistance(), _oracle::direct, () ->
				{
					final Status status = interrupted();
					return status == Status.CANCELLED ? PhantomNavigationResult.Status.CANCELLED : status == Status.DEADLINE_EXPIRED ? PhantomNavigationResult.Status.DEADLINE_EXPIRED : PhantomNavigationResult.Status.PATH_FOUND;
				});
				if (interrupted() != null) { return result(interrupted(), List.of()); }
				if (proof.status() == PhantomNavigationResult.Status.PATH_FOUND && proof.waypoints().size() <= policy.maximumWaypoints())
				{
					if (budget.edges >= MAX_EDGES || budget.nodes >= MAX_NODES || budget.labels >= MAX_LABELS) { return result(Status.SEARCH_LIMIT, List.of()); }
					double distance = 0; var previous = request.origin();
					for (var point : proof.waypoints()) { distance += previous.distanceTo(point); previous = point; }
					edges++; budget.edges++;
					add(request.destination(), distance, best.get(request.origin()), proof.waypoints());
					return result(Status.FOUND, proof.waypoints());
				}
				if (proof.status() == PhantomNavigationResult.Status.ROUTE_BUDGET_EXCEEDED) { limited = true; }
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
				else
				{
					// Match the existing journey checkpoint span without requiring a larger native buffer.
					final double bearing = Math.atan2((double) request.destination().y() - current.point.y(), (double) request.destination().x() - current.point.x());
					final var checkpoint = _oracle.project(current.point.x() + (int) Math.round(LOCAL_SPAN * Math.cos(bearing)), current.point.y() + (int) Math.round(LOCAL_SPAN * Math.sin(bearing)), current.point);
					if (checkpoint != null && !checkpoint.equals(current.point)) { candidates.add(checkpoint); }
				}
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
