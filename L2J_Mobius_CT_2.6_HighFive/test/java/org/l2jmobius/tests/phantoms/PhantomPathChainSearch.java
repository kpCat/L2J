package org.l2jmobius.tests.phantoms;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/** Bounded directed search whose every accepted hop has a native GeoEngine proof. */
final class PhantomPathChainSearch
{
	static final int MAX_NODES = 2500;
	static final int MAX_FANOUT = 8;

	record Waypoint(String id, PhantomGeoValidationRules.Point point, String kind, String sourceRefs)
	{
	}

	record Hop(Waypoint from, Waypoint to, PhantomGeoValidationRules.RouteProof proof, int requiredBuffer)
	{
	}

	record SearchOutcome(List<Hop> hops, Set<String> settled)
	{
	}

	private record QueueItem(String id, long cost)
	{
	}

	static List<Hop> search(List<Waypoint> waypoints, String sourceId, String targetId, int maximumBuffer, PhantomGeoValidationRules.Probe probe)
	{
		return searchDetailed(waypoints, sourceId, targetId, maximumBuffer, probe).hops();
	}

	static SearchOutcome searchDetailed(List<Waypoint> waypoints, String sourceId, String targetId, int maximumBuffer, PhantomGeoValidationRules.Probe probe)
	{
		if ((waypoints.size() > MAX_NODES) || (maximumBuffer < 64))
		{
			throw new IllegalArgumentException("Invalid path chain bounds");
		}
		final Map<String, Waypoint> byId = new HashMap<>();
		for (Waypoint waypoint : waypoints)
		{
			if ((waypoint.point().instanceId() != 0) || (byId.putIfAbsent(waypoint.id(), waypoint) != null))
			{
				throw new IllegalArgumentException("Invalid path chain point: " + waypoint.id());
			}
		}
		final Waypoint source = byId.get(sourceId);
		final Waypoint target = byId.get(targetId);
		if ((source == null) || (target == null) || sourceId.equals(targetId))
		{
			throw new IllegalArgumentException("Invalid path chain endpoints");
		}

		// A long hop is accepted only when the native direct movement proof itself succeeds.
		final PhantomGeoValidationRules.RouteProof direct = PhantomGeoValidationRules.route(source.point(), target.point(), probe, maximumBuffer);
		if ("VALID_DIRECT".equals(direct.reason()) || "VALID_PATH".equals(direct.reason()))
		{
			return new SearchOutcome(List.of(new Hop(source, target, direct, PhantomGeoValidationRules.requiredBuffer(source.point(), target.point()))), Set.of(sourceId, targetId));
		}

		final Map<String, Long> best = new HashMap<>();
		final Map<String, Hop> parents = new HashMap<>();
		final Set<String> settled = new HashSet<>();
		final PriorityQueue<QueueItem> queue = new PriorityQueue<>(Comparator.comparingLong(QueueItem::cost).thenComparing(QueueItem::id));
		best.put(sourceId, 0L);
		queue.add(new QueueItem(sourceId, 0));
		while (!queue.isEmpty())
		{
			final QueueItem current = queue.remove();
			if (!settled.add(current.id()))
			{
				continue;
			}
			if (current.id().equals(targetId))
			{
				final ArrayList<Hop> result = new ArrayList<>();
				String cursor = targetId;
				while (!cursor.equals(sourceId))
				{
					final Hop hop = parents.get(cursor);
					result.addFirst(hop);
					cursor = hop.from().id();
				}
				return new SearchOutcome(result, Set.copyOf(settled));
			}
			final Waypoint from = byId.get(current.id());
			final List<Waypoint> ordered = waypoints.stream()
				.filter(to -> !to.id().equals(from.id()) && !settled.contains(to.id()))
				.filter(to -> distanceSquared(from, to) > 0)
				.filter(to -> PhantomGeoValidationRules.requiredBuffer(from.point(), to.point()) <= maximumBuffer)
				.sorted(Comparator.comparingLong((Waypoint to) -> distanceSquared(from, to)).thenComparing(Waypoint::id))
				.toList();
			final Map<Integer, Waypoint> byDirection = new HashMap<>();
			for (Waypoint candidate : ordered)
			{
				final double angle = Math.atan2(candidate.point().y() - from.point().y(), candidate.point().x() - from.point().x());
				final int direction = Math.floorMod((int) Math.round(angle * 4 / Math.PI), MAX_FANOUT);
				byDirection.putIfAbsent(direction, candidate);
				if (byDirection.size() == MAX_FANOUT)
				{
					break;
				}
			}
			final List<Waypoint> neighbors = byDirection.values().stream().sorted(Comparator.comparingLong((Waypoint to) -> distanceSquared(from, to)).thenComparing(Waypoint::id)).toList();
			for (Waypoint to : neighbors)
			{
				final PhantomGeoValidationRules.RouteProof proof = PhantomGeoValidationRules.route(from.point(), to.point(), probe, maximumBuffer);
				if (!"VALID_DIRECT".equals(proof.reason()) && !"VALID_PATH".equals(proof.reason()))
				{
					continue;
				}
				final long cost = current.cost() + proof.length();
				if (cost < best.getOrDefault(to.id(), Long.MAX_VALUE))
				{
					best.put(to.id(), cost);
					parents.put(to.id(), new Hop(from, to, proof, PhantomGeoValidationRules.requiredBuffer(from.point(), to.point())));
					queue.add(new QueueItem(to.id(), cost));
				}
			}
		}
		return new SearchOutcome(List.of(), Set.copyOf(settled));
	}

	private static long distanceSquared(Waypoint from, Waypoint to)
	{
		final long dx = (long) from.point().x() - to.point().x();
		final long dy = (long) from.point().y() - to.point().y();
		return (dx * dx) + (dy * dy);
	}
}
