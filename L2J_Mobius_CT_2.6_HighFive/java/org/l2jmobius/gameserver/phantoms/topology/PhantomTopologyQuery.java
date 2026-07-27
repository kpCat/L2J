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
package org.l2jmobius.gameserver.phantoms.topology;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyValidationBackend.DoorState;

/**
 * Deterministic bounded queries over one immutable snapshot and a live door overlay.
 */
public final class PhantomTopologyQuery
{
	public record RouteHint(String fromAnchorId, String toAnchorId, List<String> edgeIds)
	{
		public RouteHint
		{
			edgeIds = List.copyOf(edgeIds);
		}
	}

	private final PhantomTopologySnapshot _snapshot;
	private final PhantomTopologyValidationBackend _backend;
	private final PhantomTopologyMetrics _metrics;

	public PhantomTopologyQuery(PhantomTopologySnapshot snapshot, PhantomTopologyValidationBackend backend, PhantomTopologyMetrics metrics)
	{
		_snapshot = Objects.requireNonNull(snapshot, "snapshot");
		_backend = Objects.requireNonNull(backend, "backend");
		_metrics = Objects.requireNonNull(metrics, "metrics");
	}

	public Optional<PhantomTopologyNode> findNode(String id)
	{
		return Optional.ofNullable(_snapshot.nodeById().get(id));
	}

	public Optional<PhantomTopologyAnchor> findAnchor(String id)
	{
		return Optional.ofNullable(_snapshot.anchorById().get(id));
	}

	public List<PhantomTopologyNode> locate(PhantomTopologyPoint point)
	{
		Objects.requireNonNull(point, "point");
		_metrics.recordSpatialQuery();
		return _snapshot.spatialNodes(point).stream().filter(node -> node.area().contains(point)).sorted(Comparator.comparingInt((PhantomTopologyNode node) -> _snapshot.depth(node.id())).reversed().thenComparingDouble(node -> node.area().measure()).thenComparing(PhantomTopologyNode::id)).limit(_snapshot.policy().maximumReturnedNodes()).toList();
	}

	public Optional<PhantomTopologyNode> mostSpecificNode(PhantomTopologyPoint point)
	{
		final List<PhantomTopologyNode> located = locate(point);
		return located.isEmpty() ? Optional.empty() : Optional.of(located.getFirst());
	}

	public List<PhantomTopologyAnchor> nearestAnchors(PhantomTopologyPoint point, PhantomTopologyAnchorRole role, int limit, int maximumDistance)
	{
		Objects.requireNonNull(point, "point");
		Objects.requireNonNull(role, "role");
		if ((limit < 1) || (limit > 64) || (maximumDistance < 0) || (maximumDistance > 100_000))
		{
			throw new IllegalArgumentException("Invalid nearest-anchor bounds.");
		}
		_metrics.recordNearestQuery();
		final int cellSize = _snapshot.policy().spatialCellSize();
		final int minimumCellX = Math.floorDiv(point.x() - maximumDistance, cellSize);
		final int maximumCellX = Math.floorDiv(point.x() + maximumDistance, cellSize);
		final int minimumCellY = Math.floorDiv(point.y() - maximumDistance, cellSize);
		final int maximumCellY = Math.floorDiv(point.y() + maximumDistance, cellSize);
		final long maximumDistanceSquared = (long) maximumDistance * maximumDistance;
		final ArrayList<PhantomTopologyAnchor> candidates = new ArrayList<>();
		for (int x = minimumCellX; x <= maximumCellX; x++)
		{
			for (int y = minimumCellY; y <= maximumCellY; y++)
			{
				for (PhantomTopologyAnchor anchor : _snapshot.anchorsInCell(point.instanceId(), x, y))
				{
					if ((anchor.role() == role) && (anchor.point().distanceSquared2D(point) <= maximumDistanceSquared))
					{
						candidates.add(anchor);
					}
				}
			}
		}
		candidates.sort(Comparator.comparingLong((PhantomTopologyAnchor anchor) -> anchor.point().distanceSquared2D(point)).thenComparing(PhantomTopologyAnchor::id));
		return List.copyOf(candidates.subList(0, Math.min(limit, candidates.size())));
	}

	public List<PhantomTopologyEdge> edges(String nodeId)
	{
		PhantomTopologyPolicy.requireId(nodeId, "query node id");
		_metrics.recordEdgeQuery();
		final List<PhantomTopologyEdge> edges = _snapshot.edgesByNode().getOrDefault(nodeId, List.of());
		return edges.size() <= _snapshot.policy().maximumReturnedEdges() ? edges : edges.subList(0, _snapshot.policy().maximumReturnedEdges());
	}

	public boolean isTraversable(String edgeId)
	{
		final PhantomTopologyEdge edge = _snapshot.edgeById().get(edgeId);
		if (edge == null)
		{
			return false;
		}
		if (edge.mode() != PhantomTopologyEdgeMode.DOOR)
		{
			return true;
		}
		_metrics.recordDoorCheck();
		final DoorState state = _backend.doorState(edge.doorId());
		return state != DoorState.CLOSED;
	}

	public boolean isPerceptible(String edgeId, PhantomPerceptionChannel channel)
	{
		final PhantomTopologyEdge edge = _snapshot.edgeById().get(edgeId);
		return (edge != null) && edge.perceptionChannels().contains(channel) && isTraversable(edgeId);
	}

	public Optional<RouteHint> routeHint(String fromAnchorId, String toAnchorId)
	{
		final PhantomTopologyAnchor from = _snapshot.anchorById().get(fromAnchorId);
		final PhantomTopologyAnchor to = _snapshot.anchorById().get(toAnchorId);
		if ((from == null) || (to == null))
		{
			return Optional.empty();
		}
		if (from.nodeId().equals(to.nodeId()))
		{
			return Optional.of(new RouteHint(fromAnchorId, toAnchorId, List.of()));
		}
		final ArrayDeque<String> queue = new ArrayDeque<>();
		final Set<String> visited = new HashSet<>();
		final Map<String, Previous> previous = new HashMap<>();
		queue.add(from.nodeId());
		visited.add(from.nodeId());
		while (!queue.isEmpty() && (visited.size() <= _snapshot.policy().maximumGraphNodes()))
		{
			final String nodeId = queue.removeFirst();
			for (PhantomTopologyEdge edge : edges(nodeId))
			{
				if (!isTraversable(edge.id()))
				{
					continue;
				}
				final String next = edge.otherNode(nodeId);
				if ((next == null) || !visited.add(next))
				{
					continue;
				}
				previous.put(next, new Previous(nodeId, edge.id()));
				if (next.equals(to.nodeId()))
				{
					return Optional.of(new RouteHint(fromAnchorId, toAnchorId, reconstruct(previous, from.nodeId(), to.nodeId())));
				}
				queue.addLast(next);
			}
		}
		return Optional.empty();
	}

	private static List<String> reconstruct(Map<String, Previous> previous, String start, String end)
	{
		final ArrayList<String> edges = new ArrayList<>();
		String current = end;
		while (!current.equals(start))
		{
			final Previous step = previous.get(current);
			if (step == null)
			{
				return List.of();
			}
			edges.add(step._edgeId);
			current = step._nodeId;
		}
		java.util.Collections.reverse(edges);
		return List.copyOf(edges);
	}

	public PhantomTopologySnapshot snapshot()
	{
		return _snapshot;
	}

	private record Previous(String _nodeId, String _edgeId)
	{
	}
}
