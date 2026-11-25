package org.l2jmobius.gameserver.phantoms.navigation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import org.l2jmobius.gameserver.phantoms.navigation.PhantomRouteLearning.Evidence;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomRouteLearning.Region;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomWalkCorridorPlanner.Edge;

/** Advisory directed search over an immutable learned snapshot. */
final class PhantomRouteGraph
{
	static final int CONNECTOR = 160;
	private static final int CELL_SHIFT = 8;
	private static final int MAX_EXPANSIONS = 512;
	private static final int MAX_RELAXATIONS = 4096;
	private static final int ITINERARY_EXPANSIONS = 12_000, ITINERARY_RELAXATIONS = 96_000;
	private static final int ANCHOR_RADIUS = 1800, ANCHOR_SCAN = 2048, ANCHOR_PROOFS = 16, BRIDGE_PROOFS = 192;
	private static final int GAP_RADIUS = 768, GAP_SCANS = 32;
	private static final Comparator<PhantomNavigationPoint> POINT_ORDER = Comparator.comparingInt(PhantomNavigationPoint::x)
		.thenComparingInt(PhantomNavigationPoint::y).thenComparingInt(PhantomNavigationPoint::z);
	private static final Comparator<Edge> EDGE_ORDER = Comparator.comparing(Edge::from, POINT_ORDER).thenComparing(Edge::to, POINT_ORDER);

	record Cell(int x, int y, int instance)
	{
		static Cell of(PhantomNavigationPoint point) { return new Cell(point.x() >> CELL_SHIFT, point.y() >> CELL_SHIFT, point.instanceId()); }
	}

	record Shard(Map<Edge, Evidence> entries, Map<Cell, List<Edge>> outgoing, long proofGeneration)
	{
		static Shard of(Map<Edge, Evidence> entries)
		{
			return of(entries, 0);
		}

		static Shard of(Map<Edge, Evidence> entries, long generation)
		{
			final var cells = new HashMap<Cell, List<Edge>>();
			for (var edge : entries.keySet())
			{
				cells.computeIfAbsent(Cell.of(edge.from()), _ -> new ArrayList<>()).add(edge);
			}
			cells.replaceAll((_, edges) ->
			{
				edges.sort(EDGE_ORDER);
				return List.copyOf(edges);
			});
			return new Shard(entries, Map.copyOf(cells), generation);
		}

		Shard updated(Map<Edge, Evidence> updated, Edge added, Edge evicted, long generation)
		{
			if (added == null) { return new Shard(updated, outgoing, generation); }
			final var cells = new HashMap<>(outgoing);
			if (evicted != null)
			{
				final var cell = Cell.of(evicted.from());
				final var edges = new ArrayList<>(cells.get(cell));
				edges.remove(evicted);
				if (edges.isEmpty()) { cells.remove(cell); }
				else { cells.put(cell, List.copyOf(edges)); }
			}
			final var cell = Cell.of(added.from());
			final var edges = new ArrayList<>(cells.getOrDefault(cell, List.of()));
			edges.add(-Collections.binarySearch(edges, added, EDGE_ORDER) - 1, added);
			cells.put(cell, List.copyOf(edges));
			return new Shard(updated, Map.copyOf(cells), generation);
		}
	}

	private record Label(PhantomNavigationPoint point, Label parent, Edge edge, double distance, double cost, double priority, int waypoints, long sequence) { }

	private final Map<Region, Shard> _shards;
	private final PhantomNavigationRequest _request;
	private final Set<Edge> _blocked;
	private final Set<PhantomNavigationPoint> _visited;
	private final long _nowMillis;
	private final BiPredicate<PhantomNavigationPoint, PhantomNavigationPoint> _walk;
	private final BooleanSupplier _interrupted;
	private final BiFunction<PhantomNavigationPoint, PhantomNavigationPoint, List<PhantomNavigationPoint>> _attach;
	private final double _distanceLimit;
	private final double _chordLimit;
	private final int _waypointLimit;
	private final boolean _episode;
	private final boolean _provenOnly;
	private final boolean _itinerary;
	private final PriorityQueue<Label> _queue = new PriorityQueue<>(Comparator.comparingDouble(Label::priority).thenComparingLong(Label::sequence));
	private final Map<PhantomNavigationPoint, Label> _best = new HashMap<>();
	private final Map<Edge, Boolean> _verified = new HashMap<>();
	private int _expanded;
	private int _relaxed;
	private int _bridgeProofs;
	private int _egressProofs;
	private int _gapScans;
	private Edge _scoutGap;
	private long _sequence;

	PhantomRouteGraph(Map<Region, Shard> shards, PhantomNavigationRequest request, PhantomNavigationPolicy policy, boolean episode, boolean provenOnly,
		Set<PhantomNavigationPoint> visited, Set<Edge> blocked, long nowMillis, BiPredicate<PhantomNavigationPoint, PhantomNavigationPoint> walk, BooleanSupplier interrupted)
	{
		this(shards, request, episode, provenOnly, false, visited, blocked, nowMillis, walk, interrupted,
			Math.min(request.maximumRouteDistance(), policy.maximumRouteDistance()),
			episode ? Math.min(policy.maximumLocalStraightDistance(), PhantomNativeTravelActions.LOCAL_WALK_DISTANCE) : policy.maximumLocalStraightDistance(),
			policy.maximumWaypoints(), null);
	}

	static List<PhantomNavigationPoint> itinerary(Map<Region, Shard> shards, PhantomNavigationRequest request, Set<Edge> blocked,
		long nowMillis, BiPredicate<PhantomNavigationPoint, PhantomNavigationPoint> walk, BooleanSupplier interrupted,
		BiFunction<PhantomNavigationPoint, PhantomNavigationPoint, List<PhantomNavigationPoint>> attach, Consumer<Edge> reportGap)
	{
		final var graph = new PhantomRouteGraph(shards, request, false, true, true, Set.of(), blocked, nowMillis, walk, interrupted,
			Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, ITINERARY_RELAXATIONS, attach);
		final var path = graph.search();
		if (path.isEmpty() && graph._scoutGap != null && !interrupted.getAsBoolean()) { reportGap.accept(graph._scoutGap); }
		return path;
	}

	private PhantomRouteGraph(Map<Region, Shard> shards, PhantomNavigationRequest request, boolean episode, boolean provenOnly, boolean itinerary,
		Set<PhantomNavigationPoint> visited, Set<Edge> blocked, long nowMillis, BiPredicate<PhantomNavigationPoint, PhantomNavigationPoint> walk,
		BooleanSupplier interrupted, double distanceLimit, double chordLimit, int waypointLimit,
		BiFunction<PhantomNavigationPoint, PhantomNavigationPoint, List<PhantomNavigationPoint>> attach)
	{
		_shards = shards;
		_request = request;
		_episode = episode;
		_provenOnly = provenOnly;
		_itinerary = itinerary;
		_visited = visited;
		_blocked = blocked;
		_nowMillis = nowMillis;
		_walk = walk;
		_interrupted = interrupted;
		_attach = attach;
		_distanceLimit = distanceLimit;
		_chordLimit = chordLimit;
		_waypointLimit = waypointLimit;
	}

	List<PhantomNavigationPoint> search()
	{
		final var origin = _request.origin();
		final var goal = _request.destination();
		if (_shards.isEmpty() || origin.equals(goal) || origin.instanceId() != goal.instanceId()) { return List.of(); }
		enqueue(origin, null, null, 0, 0, 0);
		if (_itinerary)
		{
			final var root = _best.get(origin);
			for (var anchor : nearbySources(origin, ANCHOR_RADIUS, ANCHOR_SCAN).stream().limit(ANCHOR_PROOFS).toList())
			{
				if (_interrupted.getAsBoolean()) { return List.of(); }
				attach(root, anchor);
			}
		}
		Label partial = null;
		final double initialRemaining = origin.distanceTo(goal);
		while (!_queue.isEmpty())
		{
			if (_interrupted.getAsBoolean()) { return List.of(); }
			final var current = _queue.remove();
			if (_best.get(current.point()) != current) { continue; }
			if (current.point().equals(goal)) { return waypoints(current); }
			if (++_expanded > (_itinerary ? ITINERARY_EXPANSIONS : MAX_EXPANSIONS)) { return List.of(); }
			if (current.parent() != null)
			{
				final double remaining = current.point().distanceTo(goal);
				if (_itinerary && remaining <= ANCHOR_RADIUS && _egressProofs++ < ANCHOR_PROOFS) { attach(current, goal); }
				else if (!_itinerary && connector(current.point(), goal) && current.waypoints() < _waypointLimit && current.distance() + remaining <= _distanceLimit
					&& initialRemaining <= _chordLimit && allows(current.point(), goal))
				{
					enqueue(goal, current, null, current.distance() + remaining, current.cost() + remaining, current.waypoints() + 1);
				}
				if (_episode && remaining + CONNECTOR < initialRemaining && !PhantomWalkCorridorPlanner.nearVisited(current.point(), _visited)
					&& (partial == null || remaining < partial.point().distanceTo(goal)))
				{
					partial = current;
				}
			}
			final long before = _sequence;
			for (int x = (current.point().x() - CONNECTOR) >> CELL_SHIFT; x <= (current.point().x() + CONNECTOR) >> CELL_SHIFT; x++)
			{
				for (int y = (current.point().y() - CONNECTOR) >> CELL_SHIFT; y <= (current.point().y() + CONNECTOR) >> CELL_SHIFT; y++)
				{
					final var shard = _shards.get(new Region(x >> (15 - CELL_SHIFT), y >> (15 - CELL_SHIFT)));
					if (shard == null) { continue; }
					for (var edge : shard.outgoing().getOrDefault(new Cell(x, y, origin.instanceId()), List.of()))
					{
						if (++_relaxed > (_itinerary ? ITINERARY_RELAXATIONS : MAX_RELAXATIONS) || _interrupted.getAsBoolean()) { return List.of(); }
						relax(current, edge, shard.entries().get(edge));
					}
				}
			}
			if (_itinerary && current.parent() != null && _sequence == before && _gapScans < GAP_SCANS)
			{
				_gapScans++;
				for (var anchor : nearbySources(current.point(), GAP_RADIUS, 512).stream().filter(p -> current.point().distanceTo(p) > CONNECTOR)
					.filter(p -> !_best.containsKey(p)).limit(4).toList())
				{
					if (_interrupted.getAsBoolean()) { return List.of(); }
					final double length = current.point().distanceTo(anchor);
					if (allowsBridge(current.point(), anchor))
					{
						enqueue(anchor, current, null, current.distance() + length, current.cost() + length + CONNECTOR, current.waypoints() + 1);
					}
					else if (!Region.of(current.point()).equals(Region.of(anchor)) && !_blocked.contains(new Edge(current.point(), anchor))
						&& (_scoutGap == null || current.point().distanceTo(goal) < _scoutGap.from().distanceTo(goal)))
					{
						_scoutGap = new Edge(current.point(), anchor);
					}
				}
			}
		}
		return partial == null || _interrupted.getAsBoolean() ? List.of() : waypoints(partial);
	}

	private List<PhantomNavigationPoint> nearbySources(PhantomNavigationPoint point, int radius, int scanLimit)
	{
		final var candidates = new java.util.HashSet<PhantomNavigationPoint>();
		int scanned = 0;
		collect:
		for (int x = (point.x() - radius) >> CELL_SHIFT; x <= (point.x() + radius) >> CELL_SHIFT; x++)
		{
			for (int y = (point.y() - radius) >> CELL_SHIFT; y <= (point.y() + radius) >> CELL_SHIFT; y++)
			{
				final var shard = _shards.get(new Region(x >> (15 - CELL_SHIFT), y >> (15 - CELL_SHIFT)));
				if (shard == null) { continue; }
				for (var edge : shard.outgoing().getOrDefault(new Cell(x, y, point.instanceId()), List.of()))
				{
					if (++scanned > scanLimit || _interrupted.getAsBoolean()) { break collect; }
					if (edge.from().distanceTo(point) <= radius && eligible(edge, shard.entries().get(edge))) { candidates.add(edge.from()); }
				}
			}
		}
		return candidates.stream().filter(p -> !p.equals(point)).sorted(Comparator.comparingDouble(point::distanceTo).thenComparing(POINT_ORDER)).toList();
	}

	private void attach(Label from, PhantomNavigationPoint to)
	{
		if (_blocked.contains(new Edge(from.point(), to))) { return; }
		final var points = _attach.apply(from.point(), to);
		if (points.isEmpty() || !points.getLast().equals(to)) { return; }
		var parent = from;
		for (int i = 0; i + 1 < points.size(); i++)
		{
			final var point = points.get(i);
			final double length = parent.point().distanceTo(point);
			parent = new Label(point, parent, null, parent.distance() + length, parent.cost() + length, 0, parent.waypoints() + 1, 0);
		}
		final double length = parent.point().distanceTo(to);
		enqueue(to, parent, null, parent.distance() + length, parent.cost() + length, parent.waypoints() + 1);
	}

	private boolean eligible(Edge edge, Evidence evidence)
	{
		return (evidence.successes() > 0 || !_provenOnly && evidence.candidate())
			&& evidence.retryAfter() <= _nowMillis && !_blocked.contains(edge);
	}

	private void relax(Label current, Edge edge, Evidence evidence)
	{
		final var point = current.point();
		final var next = edge.to();
		if (!eligible(edge, evidence)
			|| !connector(point, edge.from()) || next.equals(point) || _request.origin().distanceTo(next) > _chordLimit) { return; }
		final double length = point.distanceTo(edge.from()) + edge.from().distanceTo(next);
		final double distance = current.distance() + length;
		final int waypoints = current.waypoints() + (point.equals(edge.from()) ? 1 : 2);
		final double cost = current.cost() + length + (evidence.successes() == 0 ? 32 : 0) + Math.min(256, evidence.geometryFailures() * 16);
		final var old = _best.get(next);
		if (distance > _distanceLimit || waypoints > _waypointLimit || (old != null && old.cost() <= cost)
			|| !allowsBridge(point, edge.from()) || !_itinerary && !allows(edge.from(), next)
			|| _itinerary && !_request.permitsWalkSegment(edge.from(), next)) { return; }
		enqueue(next, current, edge, distance, cost, waypoints);
	}

	private boolean allowsBridge(PhantomNavigationPoint from, PhantomNavigationPoint to)
	{
		if (!_itinerary || from.equals(to)) { return allows(from, to); }
		final var edge = new Edge(from, to);
		if (!_verified.containsKey(edge) && ++_bridgeProofs > BRIDGE_PROOFS) { return false; }
		return allows(from, to);
	}

	private void enqueue(PhantomNavigationPoint point, Label parent, Edge edge, double distance, double cost, int waypoints)
	{
		final var old = _best.get(point);
		if (old != null && old.cost() <= cost) { return; }
		final var label = new Label(point, parent, edge, distance, cost, cost + point.distanceTo(_request.destination()), waypoints, ++_sequence);
		_best.put(point, label);
		_queue.add(label);
	}

	private boolean allows(PhantomNavigationPoint from, PhantomNavigationPoint to)
	{
		if (from.equals(to)) { return true; }
		final var edge = new Edge(from, to);
		return !_blocked.contains(edge) && _verified.computeIfAbsent(edge, key -> _walk.test(key.from(), key.to()));
	}

	private static List<PhantomNavigationPoint> waypoints(Label label)
	{
		final var path = new ArrayList<PhantomNavigationPoint>(label.waypoints());
		for (var step = label; step.parent() != null; step = step.parent())
		{
			path.add(step.point());
			if (step.edge() != null && !step.parent().point().equals(step.edge().from())) { path.add(step.edge().from()); }
		}
		return List.copyOf(path.reversed());
	}

	private static boolean connector(PhantomNavigationPoint from, PhantomNavigationPoint to)
	{
		return from.instanceId() == to.instanceId() && Math.abs((long) from.z() - to.z()) <= 32 && from.distanceTo(to) <= CONNECTOR;
	}
}
