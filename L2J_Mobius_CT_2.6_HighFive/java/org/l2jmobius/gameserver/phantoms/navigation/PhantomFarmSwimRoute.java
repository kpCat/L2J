/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.navigation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

/** Geometry and timing proof for an ordinary farm shore crossing. */
public final class PhantomFarmSwimRoute
{
	private static final int CELL = 16;
	private static final int MOVE_LIMIT = 640;
	private static final int MAX_CROSSING = 1500;
	private static final int MIN_DISPLACEMENT = 256;
	private static final int ENTRANCE_LIMIT = 2;
	private static final int MAX_RAY_TURNS = 8;
	private static final long EXIT_RESERVE_MILLIS = 5000;
	private static final long DISPATCH_MARGIN_MILLIS = 1000;
	private static final int[][] DIRECTIONS = {{1,0},{0,1},{-1,0},{0,-1},{1,1},{-1,1},{-1,-1},{1,-1}};

	public enum Kind { WALK_TO_SHORE, ENTER_WATER, SWIM, EXIT_WATER }
	public record Leg(Kind kind, PhantomNavigationPoint from, PhantomNavigationPoint to) { }
	public record Plan(List<Leg> legs)
	{
		public Plan { legs = List.copyOf(legs); }

		public long requiredMillis(int first, double speed)
		{
			return requiredMillis(first, speed, EXIT_RESERVE_MILLIS);
		}

		public long egressMillis(int first, double speed)
		{
			return requiredMillis(first, speed, DISPATCH_MARGIN_MILLIS);
		}

		private long requiredMillis(int first, double speed, long reserve)
		{
			if (!Double.isFinite(speed) || speed <= 0) { return Long.MAX_VALUE; }
			double millis = reserve;
			for (int i = first; i < legs.size(); i++)
			{
				final var leg = legs.get(i);
				if (leg.kind() != Kind.WALK_TO_SHORE) { millis += leg.from().distanceTo(leg.to()) * 1000 / speed + DISPATCH_MARGIN_MILLIS; }
			}
			return (long) Math.ceil(millis);
		}
	}
	public interface Geometry
	{
		/** Projection uses the nearest stable layer, never an invented bank or seabed. */
		PhantomNavigationPoint ground(int x, int y, PhantomNavigationPoint surface);
		/** Positive actual water zone identity, zero on dry land, negative if inaccessible or unknown. */
		int water(PhantomNavigationPoint point);
		/** Stock geometry and current obstacle guards, including any request-specific threats. */
		boolean line(PhantomNavigationPoint from, PhantomNavigationPoint to);
		default boolean permitsCrossing(Plan plan) { return true; }
	}

	private PhantomFarmSwimRoute() { }

	/** Recover only to a previously validated bank, or along the remaining contiguous crossing. */
	public static Plan egress(Plan crossing, int index, PhantomNavigationPoint actual, Geometry geometry, double speed, long availableMillis)
	{
		if (index < 0 || index >= crossing.legs().size() || geometry.water(actual) <= 0) { return null; }
		final var active = crossing.legs().get(index);
		final var candidates = new ArrayList<Plan>();
		final var remaining = new ArrayList<Leg>();
		if (!actual.equals(active.to()) && active.kind() != Kind.WALK_TO_SHORE)
		{
			remaining.add(new Leg(active.kind() == Kind.EXIT_WATER ? Kind.EXIT_WATER : Kind.SWIM, actual, active.to()));
		}
		remaining.addAll(crossing.legs().subList(index + 1, crossing.legs().size()));
		if (!remaining.isEmpty() && remaining.getFirst().from().equals(actual)
			&& remaining.getLast().kind() == Kind.EXIT_WATER && remaining.stream().noneMatch(leg -> leg.kind() == Kind.ENTER_WATER))
		{ candidates.add(new Plan(remaining)); }
		final var entry = crossing.legs().stream().filter(leg -> leg.kind() == Kind.ENTER_WATER).findFirst().orElse(null);
		if (entry != null) { candidates.add(new Plan(List.of(new Leg(Kind.EXIT_WATER, actual, entry.from())))); }
		candidates.add(new Plan(List.of(new Leg(Kind.EXIT_WATER, actual, crossing.legs().getLast().to()))));
		return candidates.stream().filter(plan -> plan.egressMillis(0, speed) <= availableMillis
			&& plan.legs().stream().allMatch(leg -> valid(leg, geometry)))
			.min(java.util.Comparator.comparingLong(plan -> plan.egressMillis(0, speed))).orElse(null);
	}

	public static Plan plan(PhantomNavigationPoint from, PhantomNavigationPoint toward, Geometry geometry,
		double speed, long availableMillis, BooleanSupplier interrupted)
	{
		if (from.instanceId() != 0 || toward.instanceId() != from.instanceId() || geometry.water(from) != 0
			|| !Double.isFinite(speed) || speed <= 0 || availableMillis <= EXIT_RESERVE_MILLIS || interrupted.getAsBoolean()) { return null; }
		final int span = (int) Math.min(MAX_CROSSING, (availableMillis - EXIT_RESERVE_MILLIS) * speed / 1000);
		final var dry = new HashMap<PhantomNavigationPoint, PhantomNavigationPoint>();
		final var frontier = new ArrayDeque<PhantomNavigationPoint>();
		final var entrances = new ArrayList<Leg>();
		final var budget = new PhantomWalkCorridorPlanner.Budget();
		budget.reserve(1, 0);
		dry.put(from, null);
		frontier.add(from);
		findEntrances:
		while (!frontier.isEmpty() && entrances.size() < ENTRANCE_LIMIT)
		{
			if (interrupted.getAsBoolean()) { return null; }
			final var current = frontier.removeFirst();
			for (var dir : DIRECTIONS)
			{
				final var point = geometry.ground(current.x() + CELL * dir[0], current.y() + CELL * dir[1], current);
				if (point == null || dry.containsKey(point)) { continue; }
				if (!budget.reserve(0, 1)) { break findEntrances; }
				final int wet = geometry.water(point);
				if (wet > 0)
				{
					final var entry = new Leg(Kind.ENTER_WATER, current, point);
					if (entrances.stream().noneMatch(previous -> previous.to().equals(point)) && valid(entry, geometry))
					{
						entrances.add(entry);
						if (entrances.size() == ENTRANCE_LIMIT) { break; }
					}
				}
				else if (wet == 0 && dry.size() < PhantomWalkCorridorPlanner.MAX_NODES - PhantomWalkCorridorPlanner.MAX_QUERIES
					&& valid(new Leg(Kind.WALK_TO_SHORE, current, point), geometry) && budget.reserve(1, 0))
				{
					dry.put(point, current);
					frontier.add(point);
				}
			}
		}
		Plan best = null;
		double bestScore = Double.POSITIVE_INFINITY;
		for (var entrance : entrances)
		{
			final var entryPoint = entrance.to();
			final double bearing = Math.atan2((double) entryPoint.y() - entrance.from().y(), (double) entryPoint.x() - entrance.from().x());
			final int rayCount = PhantomWalkCorridorPlanner.MAX_QUERIES / ENTRANCE_LIMIT;
			for (int i = 0; i < rayCount; i++)
			{
				if (interrupted.getAsBoolean()) { return null; }
				final int turn = (i + 1) / 2 * (i % 2 == 0 ? 1 : -1);
				final double angle = bearing + turn * (2 * Math.PI / rayCount);
				final var wet = shoreRay(entryPoint, from, angle, span, geometry, dry, budget, interrupted);
				if (wet.isEmpty()) { continue; }
				final var legs = new ArrayList<Leg>();
				for (var point = entrance.from(); dry.get(point) != null; point = dry.get(point))
				{
					legs.addFirst(new Leg(Kind.WALK_TO_SHORE, dry.get(point), point));
				}
				legs.add(entrance);
				legs.addAll(wet);
				if (legs.size() > 64) { continue; }
				final var candidate = new Plan(legs);
				final long millis = candidate.requiredMillis(0, speed);
				final double score = wet.getLast().to().distanceTo(toward) + millis * speed / 1000;
				if (millis <= availableMillis && score < bestScore && geometry.permitsCrossing(candidate))
				{
					best = candidate;
					bestScore = score;
				}
			}
		}
		return interrupted.getAsBoolean() ? null : best;
	}

	private static List<Leg> shoreRay(PhantomNavigationPoint entry, PhantomNavigationPoint origin, double angle, int span,
		Geometry geometry, Map<PhantomNavigationPoint, PhantomNavigationPoint> dry, PhantomWalkCorridorPlanner.Budget budget, BooleanSupplier interrupted)
	{
		final int zone = geometry.water(entry);
		final double cos = Math.cos(angle), sin = Math.sin(angle);
		final var directions = Arrays.stream(DIRECTIONS).filter(direction -> direction[0] * cos + direction[1] * sin >= -0.01)
			.sorted(Comparator.comparingDouble(direction -> -(direction[0] * cos + direction[1] * sin) / Math.hypot(direction[0], direction[1]))).toList();
		final var points = new ArrayList<PhantomNavigationPoint>();
		points.add(entry);
		var previous = entry;
		int shiftX = 0, shiftY = 0, turns = 0;
		double length = 0;
		for (int distance = CELL; distance <= span && points.size() + dry.size() < PhantomWalkCorridorPlanner.MAX_NODES; distance += CELL)
		{
			if (interrupted.getAsBoolean()) { return List.of(); }
			final int x = entry.x() + shiftX + (int) Math.round(cos * distance);
			final int y = entry.y() + shiftY + (int) Math.round(sin * distance);
			var next = geometry.ground(x, y, previous);
			if (previous.equals(next)) { continue; }
			if (!canAdvance(previous, next, origin, zone, points, geometry, dry, budget))
			{
				if (++turns > MAX_RAY_TURNS) { return List.of(); }
				next = null;
				for (var direction : directions)
				{
					if (interrupted.getAsBoolean()) { return List.of(); }
					final var alternative = geometry.ground(previous.x() + CELL * direction[0], previous.y() + CELL * direction[1], previous);
					if (canAdvance(previous, alternative, origin, zone, points, geometry, dry, budget)) { next = alternative; break; }
				}
				if (next == null) { return List.of(); }
				// Keep the new lateral position after clearing the obstacle.
				shiftX += next.x() - x;
				shiftY += next.y() - y;
			}
			length += previous.distanceTo(next);
			if (length > span) { return List.of(); }
			points.add(next);
			if (geometry.water(next) == 0)
			{
				return wetLegs(points, geometry, budget);
			}
			previous = next;
		}
		return List.of();
	}

	private static boolean canAdvance(PhantomNavigationPoint from, PhantomNavigationPoint to, PhantomNavigationPoint origin, int zone,
		List<PhantomNavigationPoint> points, Geometry geometry, Map<PhantomNavigationPoint, PhantomNavigationPoint> dry, PhantomWalkCorridorPlanner.Budget budget)
	{
		if (to == null || points.contains(to)) { return false; }
		final int water = geometry.water(to);
		if (water == 0 && (origin.distanceTo(to) < MIN_DISPLACEMENT || dry.containsKey(to))) { return false; }
		return (water == zone || water == 0) && budget.reserve(0, 1) && geometry.line(from, to);
	}

	private static List<Leg> wetLegs(List<PhantomNavigationPoint> points, Geometry geometry, PhantomWalkCorridorPlanner.Budget budget)
	{
		final var result = new ArrayList<Leg>();
		int at = 0;
		while (at < points.size() - 1)
		{
			int next = at + 1;
			while (next < points.size() - 1 && points.get(at).distanceTo(points.get(next + 1)) <= MOVE_LIMIT) { next++; }
			Leg leg = null;
			for (; next > at; next--)
			{
				final var candidate = new Leg(next == points.size() - 1 ? Kind.EXIT_WATER : Kind.SWIM, points.get(at), points.get(next));
				if (!budget.reserve(0, 1)) { return List.of(); }
				if (valid(candidate, geometry)) { leg = candidate; break; }
			}
			if (leg == null) { return List.of(); }
			result.add(leg);
			at = next;
		}
		return result;
	}

	public static boolean valid(Leg leg, Geometry geometry)
	{
		final var from = leg.from();
		final var to = leg.to();
		final double distance = from.distanceTo(to);
		if (from.instanceId() != 0 || to.instanceId() != from.instanceId() || distance <= 0 || distance > MOVE_LIMIT
			|| from.x() == to.x() && from.y() == to.y() || !geometry.line(from, to)) { return false; }
		final int start = geometry.water(from);
		final int end = geometry.water(to);
		final int zone = Math.max(start, end);
		final boolean expected = switch (leg.kind())
		{
			case WALK_TO_SHORE -> start == 0 && end == 0;
			case ENTER_WATER -> start == 0 && end > 0;
			case SWIM -> start > 0 && start == end;
			case EXIT_WATER -> start > 0 && end == 0;
		};
		if (!expected) { return false; }
		final int steps = (int) Math.ceil(distance / 8);
		int previous = start;
		for (int i = 1; i <= steps; i++)
		{
			final double fraction = (double) i / steps;
			final int x = from.x() + (int) Math.round((to.x() - from.x()) * fraction);
			final int y = from.y() + (int) Math.round((to.y() - from.y()) * fraction);
			final int z = from.z() + (int) Math.round((to.z() - from.z()) * fraction);
			final var point = new PhantomNavigationPoint(x, y, z, from.instanceId());
			final var floor = geometry.ground(x, y, point);
			final int water = geometry.water(point);
			if (floor == null || start > 0 && water > 0 && z < floor.z() - 8 || water != 0 && water != zone
				|| previous == end && water != end) { return false; }
			previous = water;
		}
		return previous == end;
	}
}
