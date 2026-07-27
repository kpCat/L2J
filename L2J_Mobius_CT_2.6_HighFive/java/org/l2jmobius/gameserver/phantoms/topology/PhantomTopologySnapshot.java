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

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyValidationBackend.DoorFact;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyValidationBackend.NpcFact;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyValidationBackend.SpawnFact;

/**
 * One fully validated, immutable topology generation.
 */
public final class PhantomTopologySnapshot
{
	record SpatialCell(int instanceId, int x, int y)
	{
	}

	private static final Comparator<PhantomTopologyNode> NODE_ORDER = Comparator.comparing(PhantomTopologyNode::id);
	private static final Comparator<PhantomTopologyAnchor> ANCHOR_ORDER = Comparator.comparing(PhantomTopologyAnchor::id);
	private static final Comparator<PhantomTopologyEdge> EDGE_ORDER = Comparator.comparing(PhantomTopologyEdge::id);

	private final int _schemaVersion;
	private final String _datasetId;
	private final int _datasetVersion;
	private final long _generation;
	private final String _canonicalHash;
	private final List<PhantomTopologyNode> _nodes;
	private final List<PhantomTopologyAnchor> _anchors;
	private final List<PhantomTopologyEdge> _edges;
	private final Map<String, PhantomTopologyNode> _nodeById;
	private final Map<String, PhantomTopologyAnchor> _anchorById;
	private final Map<String, PhantomTopologyEdge> _edgeById;
	private final Map<String, List<PhantomTopologyNode>> _childrenByParent;
	private final Map<String, List<PhantomTopologyEdge>> _edgesByNode;
	private final Map<String, List<PhantomTopologyAnchor>> _anchorsByNode;
	private final Map<PhantomTopologyAnchorRole, List<PhantomTopologyAnchor>> _anchorsByRole;
	private final Map<SpatialCell, List<PhantomTopologyNode>> _nodeSpatial;
	private final Map<SpatialCell, List<PhantomTopologyAnchor>> _anchorSpatial;
	private final List<PhantomTopologyNode> _oversizedSpatialNodes;
	private final Map<String, Integer> _depthByNode;
	private final PhantomTopologyPolicy _policy;

	private PhantomTopologySnapshot(int schemaVersion, String datasetId, int datasetVersion, long generation, List<PhantomTopologyNode> nodes, List<PhantomTopologyAnchor> anchors, List<PhantomTopologyEdge> edges, PhantomTopologyValidationBackend backend, PhantomTopologyPolicy policy)
	{
		_schemaVersion = schemaVersion;
		_datasetId = PhantomTopologyPolicy.requireId(datasetId, "dataset id");
		if ((schemaVersion != 1) || (datasetVersion < 1))
		{
			throw failure("schema", "Unsupported topology schema or dataset version.");
		}
		_datasetVersion = datasetVersion;
		if (generation < 0)
		{
			throw failure("generation", "Topology generation must be non-negative.");
		}
		_generation = generation;
		_policy = Objects.requireNonNull(policy, "policy");
		Objects.requireNonNull(backend, "backend");
		if ((nodes.size() > policy.maximumNodes()) || (anchors.size() > policy.maximumAnchors()) || (edges.size() > policy.maximumEdges()))
		{
			throw failure("count", "Topology entity count exceeds policy.");
		}
		_nodes = nodes.stream().sorted(NODE_ORDER).toList();
		_anchors = anchors.stream().sorted(ANCHOR_ORDER).toList();
		_edges = edges.stream().sorted(EDGE_ORDER).toList();
		_nodeById = uniqueMap(_nodes, PhantomTopologyNode::id, "node");
		_anchorById = uniqueMap(_anchors, PhantomTopologyAnchor::id, "anchor");
		_edgeById = uniqueMap(_edges, PhantomTopologyEdge::id, "edge");
		_depthByNode = validateNodes(backend);
		validateAnchors(backend);
		validateEdges(backend);
		_childrenByParent = groupNodes();
		_edgesByNode = groupEdges();
		_anchorsByNode = groupAnchorsByNode();
		_anchorsByRole = groupAnchorsByRole();
		final SpatialIndexes spatial = buildSpatialIndexes();
		_nodeSpatial = spatial._nodes;
		_anchorSpatial = spatial._anchors;
		_oversizedSpatialNodes = spatial._oversized;
		_canonicalHash = computeCanonicalHash();
	}

	public static PhantomTopologySnapshot create(int schemaVersion, String datasetId, int datasetVersion, long generation, List<PhantomTopologyNode> nodes, List<PhantomTopologyAnchor> anchors, List<PhantomTopologyEdge> edges, PhantomTopologyValidationBackend backend, PhantomTopologyPolicy policy)
	{
		return new PhantomTopologySnapshot(schemaVersion, datasetId, datasetVersion, generation, List.copyOf(nodes), List.copyOf(anchors), List.copyOf(edges), backend, policy);
	}

	public static PhantomTopologySnapshot empty(PhantomTopologyValidationBackend backend, PhantomTopologyPolicy policy)
	{
		return create(1, "high-five-empty", 1, 0, List.of(), List.of(), List.of(), backend, policy);
	}

	private static <T> Map<String, T> uniqueMap(List<T> values, java.util.function.Function<T, String> keyFunction, String category)
	{
		final LinkedHashMap<String, T> result = new LinkedHashMap<>();
		for (T value : values)
		{
			if (result.put(keyFunction.apply(value), value) != null)
			{
				throw failure("duplicate", "Duplicate topology " + category + " id.");
			}
		}
		return Map.copyOf(result);
	}

	private Map<String, Integer> validateNodes(PhantomTopologyValidationBackend backend)
	{
		final HashMap<String, Integer> depths = new HashMap<>();
		for (PhantomTopologyNode node : _nodes)
		{
			validateSources(node.sourceRefs(), backend);
			if (node.parentId() != null)
			{
				final PhantomTopologyNode parent = _nodeById.get(node.parentId());
				if (parent == null)
				{
					throw failure("reference", "Topology node parent is missing.");
				}
				if (parent.instanceId() != node.instanceId())
				{
					throw failure("instance", "Topology child and parent instances differ.");
				}
				if (!parent.area().contains(node.area().representativePoint()))
				{
					throw failure("hierarchy", "Topology child representative point is outside its parent.");
				}
			}
			final int depth = depth(node, new HashSet<>(), depths);
			if (depth > _policy.maximumHierarchyDepth())
			{
				throw failure("hierarchy", "Topology hierarchy exceeds maximum depth.");
			}
			if ((node.kind() == PhantomTopologyNodeKind.ROOM) || (node.kind() == PhantomTopologyNodeKind.CORRIDOR))
			{
				PhantomTopologyNode current = node.parentId() == null ? null : _nodeById.get(node.parentId());
				boolean validParent = false;
				while (current != null)
				{
					if ((current.kind() == PhantomTopologyNodeKind.DUNGEON) || (current.kind() == PhantomTopologyNodeKind.CATACOMB) || (current.kind() == PhantomTopologyNodeKind.ROOM))
					{
						validParent = true;
						break;
					}
					current = current.parentId() == null ? null : _nodeById.get(current.parentId());
				}
				if (!validParent)
				{
					throw failure("hierarchy", "Room or corridor lacks a dungeon, catacomb or room parent chain.");
				}
			}
		}
		return Map.copyOf(depths);
	}

	private int depth(PhantomTopologyNode node, Set<String> visiting, Map<String, Integer> memo)
	{
		final Integer existing = memo.get(node.id());
		if (existing != null)
		{
			return existing;
		}
		if (!visiting.add(node.id()))
		{
			throw failure("hierarchy", "Topology hierarchy cycle detected.");
		}
		final int result = node.parentId() == null ? 0 : depth(_nodeById.get(node.parentId()), visiting, memo) + 1;
		visiting.remove(node.id());
		memo.put(node.id(), result);
		return result;
	}

	private void validateAnchors(PhantomTopologyValidationBackend backend)
	{
		for (PhantomTopologyAnchor anchor : _anchors)
		{
			validateSources(anchor.sourceRefs(), backend);
			final PhantomTopologyNode node = _nodeById.get(anchor.nodeId());
			if (node == null)
			{
				throw failure("reference", "Topology anchor node is missing.");
			}
			if (!node.area().contains(anchor.point()))
			{
				throw failure("geometry", "Topology anchor is outside its node.");
			}
			if ((anchor.mapRegionLocId() != null) && (backend.mapRegionLocId(anchor.point().x(), anchor.point().y()) != anchor.mapRegionLocId()))
			{
				throw failure("map-region", "Topology map-region fact does not match.");
			}
			if (anchor.npcId() != null)
			{
				final NpcFact npc = backend.npc(anchor.npcId()).orElseThrow(() -> failure("npc", "Topology NPC template is missing."));
				boolean matchingSpawn = false;
				for (SpawnFact spawn : backend.spawns(anchor.npcId(), 4096))
				{
					if (anchor.point().distance3D(spawn.point()) <= anchor.validationTolerance())
					{
						matchingSpawn = true;
						break;
					}
				}
				if (!matchingSpawn)
				{
					throw failure("spawn", "Topology NPC anchor has no factual spawn within tolerance.");
				}
				if ((anchor.role() == PhantomTopologyAnchorRole.FARMING) && !npc.monster())
				{
					throw failure("npc", "Topology farming anchor does not reference a Monster template.");
				}
			}
			if (((anchor.role() == PhantomTopologyAnchorRole.GATEKEEPER) || (anchor.role() == PhantomTopologyAnchorRole.SHOP) || (anchor.role() == PhantomTopologyAnchorRole.WAREHOUSE)) && ((anchor.npcId() == null) || anchor.sourceRefs().isEmpty()))
			{
				throw failure("evidence", "Semantic NPC anchor lacks explicit factual evidence.");
			}
			if ((anchor.role() == PhantomTopologyAnchorRole.FARMING) && (anchor.npcId() == null))
			{
				throw failure("npc", "Topology farming anchor requires a factual Monster NPC.");
			}
		}
	}

	private void validateEdges(PhantomTopologyValidationBackend backend)
	{
		final HashSet<String> semanticKeys = new HashSet<>();
		for (PhantomTopologyEdge edge : _edges)
		{
			validateSources(edge.sourceRefs(), backend);
			final PhantomTopologyNode from = _nodeById.get(edge.fromNodeId());
			final PhantomTopologyNode to = _nodeById.get(edge.toNodeId());
			if ((from == null) || (to == null))
			{
				throw failure("reference", "Topology edge node is missing.");
			}
			final boolean crossInstanceAllowed = (edge.mode() == PhantomTopologyEdgeMode.TELEPORT) || (edge.mode() == PhantomTopologyEdgeMode.GATEKEEPER) || (edge.mode() == PhantomTopologyEdgeMode.BACKGROUND);
			if ((from.instanceId() != to.instanceId()) && !crossInstanceAllowed)
			{
				throw failure("instance", "Topology edge crosses instances without an allowed transition mode.");
			}
			final boolean endpointAnchorsRequired = (edge.mode() == PhantomTopologyEdgeMode.WALK) || (edge.mode() == PhantomTopologyEdgeMode.PASSAGE) || (edge.mode() == PhantomTopologyEdgeMode.DOOR);
			if (endpointAnchorsRequired && ((edge.fromAnchorId() == null) || (edge.toAnchorId() == null)))
			{
				throw failure("reference", "Topology local edge requires endpoint anchors.");
			}
			final PhantomTopologyAnchor fromAnchor = edge.fromAnchorId() == null ? null : _anchorById.get(edge.fromAnchorId());
			final PhantomTopologyAnchor toAnchor = edge.toAnchorId() == null ? null : _anchorById.get(edge.toAnchorId());
			if (((edge.fromAnchorId() != null) && (fromAnchor == null)) || ((edge.toAnchorId() != null) && (toAnchor == null)))
			{
				throw failure("reference", "Topology edge anchor is missing.");
			}
			if (((fromAnchor != null) && !fromAnchor.nodeId().equals(edge.fromNodeId())) || ((toAnchor != null) && !toAnchor.nodeId().equals(edge.toNodeId())))
			{
				throw failure("reference", "Topology edge endpoint anchor belongs to another node.");
			}
			if (edge.mode() == PhantomTopologyEdgeMode.DOOR)
			{
				if (edge.doorId() == null)
				{
					throw failure("door", "Topology door edge lacks a door ID.");
				}
				final DoorFact door = backend.door(edge.doorId()).orElseThrow(() -> failure("door", "Topology door fact is missing."));
				if ((fromAnchor == null) || (toAnchor == null) || (fromAnchor.role() != PhantomTopologyAnchorRole.DOOR_SIDE) || (toAnchor.role() != PhantomTopologyAnchorRole.DOOR_SIDE))
				{
					throw failure("door", "Topology door edge requires two DOOR_SIDE anchors.");
				}
				if ((door.distance2D(fromAnchor.point()) > 500) || (door.distance2D(toAnchor.point()) > 500))
				{
					throw failure("door", "Topology door-side anchor is too far from factual door geometry.");
				}
			}
			else if (edge.doorId() != null)
			{
				throw failure("door", "Only a DOOR edge may reference a door ID.");
			}
			final String low = edge.bidirectional() && (edge.fromNodeId().compareTo(edge.toNodeId()) > 0) ? edge.toNodeId() : edge.fromNodeId();
			final String high = edge.bidirectional() && (edge.fromNodeId().compareTo(edge.toNodeId()) > 0) ? edge.fromNodeId() : edge.toNodeId();
			final String semanticKey = low + '\u0000' + high + '\u0000' + edge.mode() + '\u0000' + edge.bidirectional();
			if (!semanticKeys.add(semanticKey))
			{
				throw failure("duplicate", "Duplicate topology semantic edge.");
			}
		}
	}

	private static void validateSources(List<String> sources, PhantomTopologyValidationBackend backend)
	{
		for (String source : sources)
		{
			if (!backend.sourceExists(source))
			{
				throw failure("evidence", "Topology source evidence is missing.");
			}
		}
	}

	private Map<String, List<PhantomTopologyNode>> groupNodes()
	{
		final HashMap<String, ArrayList<PhantomTopologyNode>> groups = new HashMap<>();
		for (PhantomTopologyNode node : _nodes)
		{
			if (node.parentId() != null)
			{
				groups.computeIfAbsent(node.parentId(), _ -> new ArrayList<>()).add(node);
			}
		}
		return immutableGroups(groups, NODE_ORDER);
	}

	private Map<String, List<PhantomTopologyEdge>> groupEdges()
	{
		final HashMap<String, ArrayList<PhantomTopologyEdge>> groups = new HashMap<>();
		for (PhantomTopologyEdge edge : _edges)
		{
			groups.computeIfAbsent(edge.fromNodeId(), _ -> new ArrayList<>()).add(edge);
			groups.computeIfAbsent(edge.toNodeId(), _ -> new ArrayList<>()).add(edge);
		}
		return immutableGroups(groups, EDGE_ORDER);
	}

	private Map<String, List<PhantomTopologyAnchor>> groupAnchorsByNode()
	{
		final HashMap<String, ArrayList<PhantomTopologyAnchor>> groups = new HashMap<>();
		for (PhantomTopologyAnchor anchor : _anchors)
		{
			groups.computeIfAbsent(anchor.nodeId(), _ -> new ArrayList<>()).add(anchor);
		}
		return immutableGroups(groups, ANCHOR_ORDER);
	}

	private Map<PhantomTopologyAnchorRole, List<PhantomTopologyAnchor>> groupAnchorsByRole()
	{
		final EnumMap<PhantomTopologyAnchorRole, List<PhantomTopologyAnchor>> result = new EnumMap<>(PhantomTopologyAnchorRole.class);
		for (PhantomTopologyAnchorRole role : PhantomTopologyAnchorRole.values())
		{
			result.put(role, _anchors.stream().filter(anchor -> anchor.role() == role).sorted(ANCHOR_ORDER).toList());
		}
		return Map.copyOf(result);
	}

	private static <T> Map<String, List<T>> immutableGroups(Map<String, ArrayList<T>> groups, Comparator<T> comparator)
	{
		final HashMap<String, List<T>> result = new HashMap<>();
		for (Map.Entry<String, ArrayList<T>> entry : groups.entrySet())
		{
			entry.getValue().sort(comparator);
			result.put(entry.getKey(), List.copyOf(entry.getValue()));
		}
		return Map.copyOf(result);
	}

	private SpatialIndexes buildSpatialIndexes()
	{
		final HashMap<SpatialCell, ArrayList<PhantomTopologyNode>> nodeCells = new HashMap<>();
		final ArrayList<PhantomTopologyNode> oversized = new ArrayList<>();
		for (PhantomTopologyNode node : _nodes)
		{
			final int minCellX = Math.floorDiv(node.area().minX(), _policy.spatialCellSize());
			final int maxCellX = Math.floorDiv(node.area().maxX(), _policy.spatialCellSize());
			final int minCellY = Math.floorDiv(node.area().minY(), _policy.spatialCellSize());
			final int maxCellY = Math.floorDiv(node.area().maxY(), _policy.spatialCellSize());
			final long references = ((long) maxCellX - minCellX + 1) * ((long) maxCellY - minCellY + 1);
			if (references > _policy.maximumSpatialReferencesPerNode())
			{
				oversized.add(node);
				continue;
			}
			for (int x = minCellX; x <= maxCellX; x++)
			{
				for (int y = minCellY; y <= maxCellY; y++)
				{
					nodeCells.computeIfAbsent(new SpatialCell(node.instanceId(), x, y), _ -> new ArrayList<>()).add(node);
				}
			}
		}
		if (oversized.size() > _policy.maximumOversizedSpatialNodes())
		{
			throw failure("spatial", "Too many oversized topology areas for bounded lookup.");
		}
		final HashMap<SpatialCell, ArrayList<PhantomTopologyAnchor>> anchorCells = new HashMap<>();
		for (PhantomTopologyAnchor anchor : _anchors)
		{
			anchorCells.computeIfAbsent(cell(anchor.point()), _ -> new ArrayList<>()).add(anchor);
		}
		return new SpatialIndexes(immutableSpatial(nodeCells, NODE_ORDER), immutableSpatial(anchorCells, ANCHOR_ORDER), oversized.stream().sorted(NODE_ORDER).toList());
	}

	private SpatialCell cell(PhantomTopologyPoint point)
	{
		return new SpatialCell(point.instanceId(), Math.floorDiv(point.x(), _policy.spatialCellSize()), Math.floorDiv(point.y(), _policy.spatialCellSize()));
	}

	private static <T> Map<SpatialCell, List<T>> immutableSpatial(Map<SpatialCell, ArrayList<T>> source, Comparator<T> comparator)
	{
		final HashMap<SpatialCell, List<T>> result = new HashMap<>();
		for (Map.Entry<SpatialCell, ArrayList<T>> entry : source.entrySet())
		{
			entry.getValue().sort(comparator);
			result.put(entry.getKey(), List.copyOf(entry.getValue()));
		}
		return Map.copyOf(result);
	}

	private String computeCanonicalHash()
	{
		try
		{
			final MessageDigest digest = MessageDigest.getInstance("SHA-256");
			add(digest, Integer.toString(_schemaVersion));
			add(digest, _datasetId);
			add(digest, Integer.toString(_datasetVersion));
			for (PhantomTopologyNode node : _nodes)
			{
				add(digest, "node");
				add(digest, node.id());
				add(digest, node.kind().name());
				add(digest, Integer.toString(node.instanceId()));
				add(digest, node.parentId());
				addArea(digest, node.area());
				node.tags().forEach(value -> add(digest, value));
				node.sourceRefs().forEach(value -> add(digest, value));
			}
			for (PhantomTopologyAnchor anchor : _anchors)
			{
				add(digest, "anchor");
				add(digest, anchor.id());
				add(digest, anchor.role().name());
				add(digest, anchor.nodeId());
				addPoint(digest, anchor.point());
				add(digest, anchor.npcId() == null ? null : anchor.npcId().toString());
				add(digest, anchor.mapRegionLocId() == null ? null : anchor.mapRegionLocId().toString());
				add(digest, Integer.toString(anchor.validationTolerance()));
				anchor.tags().forEach(value -> add(digest, value));
				anchor.sourceRefs().forEach(value -> add(digest, value));
			}
			for (PhantomTopologyEdge edge : _edges)
			{
				add(digest, "edge");
				add(digest, edge.id());
				add(digest, edge.fromNodeId());
				add(digest, edge.toNodeId());
				add(digest, edge.mode().name());
				add(digest, Boolean.toString(edge.bidirectional()));
				add(digest, Integer.toString(edge.baseCost()));
				add(digest, Long.toString(edge.baseTravelMillis()));
				add(digest, Boolean.toString(edge.backgroundEligible()));
				edge.perceptionChannels().stream().map(Enum::name).sorted().forEach(value -> add(digest, value));
				add(digest, edge.doorId() == null ? null : edge.doorId().toString());
				add(digest, edge.fromAnchorId());
				add(digest, edge.toAnchorId());
				edge.sourceRefs().forEach(value -> add(digest, value));
			}
			return java.util.HexFormat.of().formatHex(digest.digest());
		}
		catch (NoSuchAlgorithmException exception)
		{
			throw new IllegalStateException("SHA-256 is unavailable.", exception);
		}
	}

	private static void addArea(MessageDigest digest, PhantomTopologyArea area)
	{
		add(digest, area.form().name());
		add(digest, Integer.toString(area.instanceId()));
		add(digest, Integer.toString(area.minX()));
		add(digest, Integer.toString(area.maxX()));
		add(digest, Integer.toString(area.minY()));
		add(digest, Integer.toString(area.maxY()));
		add(digest, Integer.toString(area.minZ()));
		add(digest, Integer.toString(area.maxZ()));
		if (area.center() != null)
		{
			addPoint(digest, area.center());
			add(digest, Integer.toString(area.radius()));
		}
		for (PhantomTopologyArea.Vertex vertex : area.vertices())
		{
			add(digest, Integer.toString(vertex.x()));
			add(digest, Integer.toString(vertex.y()));
		}
	}

	private static void addPoint(MessageDigest digest, PhantomTopologyPoint point)
	{
		add(digest, Integer.toString(point.x()));
		add(digest, Integer.toString(point.y()));
		add(digest, Integer.toString(point.z()));
		add(digest, Integer.toString(point.instanceId()));
	}

	private static void add(MessageDigest digest, String value)
	{
		final byte[] bytes = value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8);
		digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
		digest.update(bytes);
	}

	private static PhantomTopologyValidationException failure(String category, String message)
	{
		return new PhantomTopologyValidationException(category, message);
	}

	List<PhantomTopologyNode> spatialNodes(PhantomTopologyPoint point)
	{
		final LinkedHashSet<PhantomTopologyNode> result = new LinkedHashSet<>(_nodeSpatial.getOrDefault(cell(point), List.of()));
		result.addAll(_oversizedSpatialNodes);
		return List.copyOf(result);
	}

	List<PhantomTopologyAnchor> anchorsInCell(int instanceId, int cellX, int cellY)
	{
		return _anchorSpatial.getOrDefault(new SpatialCell(instanceId, cellX, cellY), List.of());
	}

	int depth(String nodeId)
	{
		return _depthByNode.getOrDefault(nodeId, 0);
	}

	PhantomTopologyPolicy policy()
	{
		return _policy;
	}

	public int schemaVersion()
	{
		return _schemaVersion;
	}

	public String datasetId()
	{
		return _datasetId;
	}

	public int datasetVersion()
	{
		return _datasetVersion;
	}

	public long generation()
	{
		return _generation;
	}

	public String canonicalHash()
	{
		return _canonicalHash;
	}

	public List<PhantomTopologyNode> nodes()
	{
		return _nodes;
	}

	public List<PhantomTopologyAnchor> anchors()
	{
		return _anchors;
	}

	public List<PhantomTopologyEdge> edges()
	{
		return _edges;
	}

	public Map<String, PhantomTopologyNode> nodeById()
	{
		return _nodeById;
	}

	public Map<String, PhantomTopologyAnchor> anchorById()
	{
		return _anchorById;
	}

	public Map<String, PhantomTopologyEdge> edgeById()
	{
		return _edgeById;
	}

	public Map<String, List<PhantomTopologyNode>> childrenByParent()
	{
		return _childrenByParent;
	}

	public Map<String, List<PhantomTopologyEdge>> edgesByNode()
	{
		return _edgesByNode;
	}

	public Map<String, List<PhantomTopologyAnchor>> anchorsByNode()
	{
		return _anchorsByNode;
	}

	public Map<PhantomTopologyAnchorRole, List<PhantomTopologyAnchor>> anchorsByRole()
	{
		return _anchorsByRole;
	}

	private record SpatialIndexes(Map<SpatialCell, List<PhantomTopologyNode>> _nodes, Map<SpatialCell, List<PhantomTopologyAnchor>> _anchors, List<PhantomTopologyNode> _oversized)
	{
	}
}
