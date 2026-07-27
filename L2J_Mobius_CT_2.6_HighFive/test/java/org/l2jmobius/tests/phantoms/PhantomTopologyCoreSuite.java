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
package org.l2jmobius.tests.phantoms;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.l2jmobius.gameserver.phantoms.activity.PhantomRelevanceSignal;
import org.l2jmobius.gameserver.phantoms.topology.PhantomPerceptionChannel;
import org.l2jmobius.gameserver.phantoms.topology.PhantomRelevanceSignalPort;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyAnchor;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyAnchorRole;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyArea;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyEdge;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyEdgeMode;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyLoader;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyMetrics;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyNode;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyNodeKind;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPolicy;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyQuery;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyService;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologySnapshot;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyValidationBackend;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyValidationBackend.DoorFact;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyValidationBackend.DoorState;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyValidationBackend.NpcFact;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyValidationBackend.SpawnFact;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyValidationException;

public final class PhantomTopologyCoreSuite implements PhantomTestSuite
{
	static final PhantomTopologyPolicy POLICY = PhantomTopologyPolicy.productionDefaults();
	static final PhantomTopologyPoint LEFT_POINT = point(300, 500);
	static final PhantomTopologyPoint RIGHT_POINT = point(700, 500);
	static final PhantomTopologyPoint LEFT_DOOR = point(490, 500);
	static final PhantomTopologyPoint RIGHT_DOOR = point(510, 500);

	@Override
	public String id()
	{
		return "topology-core";
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("01-point-world-contract", _ -> testPoint());
		registry.add("02-cuboid-containment", _ -> testCuboid());
		registry.add("03-point-radius-containment", _ -> testPointRadius());
		registry.add("04-polygon-containment", _ -> testPolygon());
		registry.add("05-polygon-self-intersection", _ -> testPolygonSelfIntersection());
		registry.add("06-polygon-vertex-bound", _ -> testPolygonVertexBound());
		registry.add("07-id-contract", _ -> testId());
		registry.add("08-tag-bound", _ -> testTagBound());
		registry.add("09-source-bound", _ -> testSourceBound());
		registry.add("10-snapshot-immutable", _ -> testSnapshotImmutable());
		registry.add("11-duplicate-node-id", _ -> testDuplicateNode());
		registry.add("12-missing-parent", _ -> testMissingParent());
		registry.add("13-hierarchy-cycle", _ -> testHierarchyCycle());
		registry.add("14-hierarchy-depth", _ -> testHierarchyDepth());
		registry.add("15-parent-instance", _ -> testParentInstance());
		registry.add("16-child-containment", _ -> testChildContainment());
		registry.add("17-room-parent-chain", _ -> testRoomParent());
		registry.add("18-anchor-dangling-node", _ -> testAnchorDangling());
		registry.add("19-anchor-containment", _ -> testAnchorContainment());
		registry.add("20-map-region-factual", _ -> testMapRegion());
		registry.add("21-npc-template-factual", _ -> testNpcTemplate());
		registry.add("22-npc-spawn-factual", _ -> testNpcSpawn());
		registry.add("23-farming-monster-factual", _ -> testFarmingMonster());
		registry.add("24-semantic-evidence", _ -> testSemanticEvidence());
		registry.add("25-door-factual", _ -> testDoorMissing());
		registry.add("26-door-side-geometry", _ -> testDoorGeometry());
		registry.add("27-local-edge-endpoints", _ -> testLocalEdgeEndpoints());
		registry.add("28-duplicate-semantic-edge", _ -> testDuplicateSemanticEdge());
		registry.add("29-canonical-order-independent", context -> testCanonicalOrder(context));
		registry.add("30-loader-unknown-attribute", context -> testUnknownAttribute(context));
		registry.add("31-invalid-reload-retains-snapshot", context -> testReload(context));
		registry.add("32-most-specific-resolution", _ -> testMostSpecific());
		registry.add("33-nearest-anchor-order", _ -> testNearest());
		registry.add("34-live-door-overlay", _ -> testLiveDoor());
		registry.add("35-route-hint-bounded", _ -> testRouteHint());
		registry.add("36-returned-node-bound", _ -> testReturnedNodeBound());
		registry.add("37-no-mutable-server-object-exposure", _ -> testNoServerObjectExposure());
		registry.add("38-policy-production-bounds", _ -> testPolicy());
	}

	private void testPoint()
	{
		PhantomAssertions.assertEquals(160000L, LEFT_POINT.distanceSquared2D(RIGHT_POINT), "Topology point distance changed.");
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> new PhantomTopologyPoint(Integer.MAX_VALUE, 0, 0, 0), "Out-of-world topology point was accepted.");
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> new PhantomTopologyPoint(0, 0, 0, -1), "Negative topology instance was accepted.");
	}

	private void testCuboid()
	{
		final PhantomTopologyArea area = PhantomTopologyArea.cuboid(0, 0, 100, 0, 100, 0, 100);
		PhantomAssertions.assertTrue(area.contains(point(50, 50, 50)), "Cuboid lost an interior point.");
		PhantomAssertions.assertFalse(area.contains(point(101, 50, 50)), "Cuboid accepted an exterior point.");
	}

	private void testPointRadius()
	{
		final PhantomTopologyArea area = PhantomTopologyArea.pointRadius(point(100, 100), 50);
		PhantomAssertions.assertTrue(area.contains(point(130, 100)), "Point-radius area lost an interior point.");
		PhantomAssertions.assertFalse(area.contains(point(151, 100)), "Point-radius area accepted an exterior point.");
	}

	private void testPolygon()
	{
		final PhantomTopologyArea area = PhantomTopologyArea.polygon(0, 0, 100, List.of(new PhantomTopologyArea.Vertex(0, 0), new PhantomTopologyArea.Vertex(100, 0), new PhantomTopologyArea.Vertex(100, 100), new PhantomTopologyArea.Vertex(0, 100)));
		PhantomAssertions.assertTrue(area.contains(point(50, 50, 50)), "Polygon lost an interior point.");
		PhantomAssertions.assertTrue(area.contains(point(0, 50, 50)), "Polygon boundary was not included.");
		PhantomAssertions.assertFalse(area.contains(point(150, 50, 50)), "Polygon accepted an exterior point.");
	}

	private void testPolygonSelfIntersection()
	{
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> PhantomTopologyArea.polygon(0, 0, 100, List.of(new PhantomTopologyArea.Vertex(0, 0), new PhantomTopologyArea.Vertex(100, 100), new PhantomTopologyArea.Vertex(0, 100), new PhantomTopologyArea.Vertex(100, 0))), "Self-intersecting polygon was accepted.");
	}

	private void testPolygonVertexBound()
	{
		final ArrayList<PhantomTopologyArea.Vertex> vertices = new ArrayList<>();
		for (int index = 0; index < 33; index++)
		{
			vertices.add(new PhantomTopologyArea.Vertex(index, index * index));
		}
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> PhantomTopologyArea.polygon(0, 0, 100, vertices), "Polygon above 32 vertices was accepted.");
	}

	private void testId()
	{
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> node("Invalid"), "Upper-case topology identity was accepted.");
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> node("a".repeat(97)), "Overlong topology identity was accepted.");
	}

	private void testTagBound()
	{
		final List<String> tags = java.util.stream.IntStream.range(0, 17).mapToObj(index -> "tag" + index).toList();
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> new PhantomTopologyNode("node", PhantomTopologyNodeKind.OUTDOOR_AREA, 0, area(0, 1000), null, tags, List.of()), "Topology accepted more than 16 tags.");
	}

	private void testSourceBound()
	{
		final List<String> sources = java.util.stream.IntStream.range(0, 9).mapToObj(index -> "data/source" + index + ".xml").toList();
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> new PhantomTopologyNode("node", PhantomTopologyNodeKind.OUTDOOR_AREA, 0, area(0, 1000), null, List.of(), sources), "Topology accepted more than 8 sources.");
	}

	private void testSnapshotImmutable()
	{
		final PhantomTopologySnapshot snapshot = snapshot(new TestBackend());
		PhantomAssertions.assertThrows(UnsupportedOperationException.class, () -> snapshot.nodes().add(node("extra")), "Topology node list remained mutable.");
		PhantomAssertions.assertThrows(UnsupportedOperationException.class, () -> snapshot.nodeById().clear(), "Topology node index remained mutable.");
		PhantomAssertions.assertThrows(UnsupportedOperationException.class, () -> snapshot.edgesByNode().get("dungeon.left").clear(), "Topology adjacency list remained mutable.");
	}

	private void testDuplicateNode()
	{
		final TestBackend backend = new TestBackend();
		PhantomAssertions.assertThrows(PhantomTopologyValidationException.class, () -> create(List.of(node("same"), node("same")), List.of(), List.of(), backend), "Duplicate topology node ID was accepted.");
	}

	private void testMissingParent()
	{
		final PhantomTopologyNode child = new PhantomTopologyNode("child", PhantomTopologyNodeKind.OUTDOOR_AREA, 0, area(0, 100), "missing", List.of(), List.of());
		PhantomAssertions.assertThrows(PhantomTopologyValidationException.class, () -> create(List.of(child), List.of(), List.of(), new TestBackend()), "Missing topology parent was accepted.");
	}

	private void testHierarchyCycle()
	{
		final PhantomTopologyNode a = new PhantomTopologyNode("a", PhantomTopologyNodeKind.OUTDOOR_AREA, 0, area(0, 1000), "b", List.of(), List.of());
		final PhantomTopologyNode b = new PhantomTopologyNode("b", PhantomTopologyNodeKind.OUTDOOR_AREA, 0, area(0, 1000), "a", List.of(), List.of());
		PhantomAssertions.assertThrows(PhantomTopologyValidationException.class, () -> create(List.of(a, b), List.of(), List.of(), new TestBackend()), "Topology hierarchy cycle was accepted.");
	}

	private void testHierarchyDepth()
	{
		final ArrayList<PhantomTopologyNode> nodes = new ArrayList<>();
		for (int index = 0; index < 10; index++)
		{
			nodes.add(new PhantomTopologyNode("n" + index, PhantomTopologyNodeKind.OUTDOOR_AREA, 0, area(0, 1000), index == 0 ? null : "n" + (index - 1), List.of(), List.of()));
		}
		PhantomAssertions.assertThrows(PhantomTopologyValidationException.class, () -> create(nodes, List.of(), List.of(), new TestBackend()), "Topology hierarchy above depth 8 was accepted.");
	}

	private void testParentInstance()
	{
		final PhantomTopologyNode parent = new PhantomTopologyNode("parent", PhantomTopologyNodeKind.OUTDOOR_AREA, 0, area(0, 1000), null, List.of(), List.of());
		final PhantomTopologyNode child = new PhantomTopologyNode("child", PhantomTopologyNodeKind.OUTDOOR_AREA, 1, PhantomTopologyArea.cuboid(1, 0, 100, 0, 100, 0, 100), "parent", List.of(), List.of());
		PhantomAssertions.assertThrows(PhantomTopologyValidationException.class, () -> create(List.of(parent, child), List.of(), List.of(), new TestBackend()), "Cross-instance topology parent was accepted.");
	}

	private void testChildContainment()
	{
		final PhantomTopologyNode parent = new PhantomTopologyNode("parent", PhantomTopologyNodeKind.OUTDOOR_AREA, 0, area(0, 100), null, List.of(), List.of());
		final PhantomTopologyNode child = new PhantomTopologyNode("child", PhantomTopologyNodeKind.OUTDOOR_AREA, 0, area(200, 300), "parent", List.of(), List.of());
		PhantomAssertions.assertThrows(PhantomTopologyValidationException.class, () -> create(List.of(parent, child), List.of(), List.of(), new TestBackend()), "Child outside parent was accepted.");
	}

	private void testRoomParent()
	{
		final PhantomTopologyNode parent = new PhantomTopologyNode("parent", PhantomTopologyNodeKind.CITY, 0, area(0, 1000), null, List.of(), List.of());
		final PhantomTopologyNode room = new PhantomTopologyNode("room", PhantomTopologyNodeKind.ROOM, 0, area(100, 200), "parent", List.of(), List.of());
		PhantomAssertions.assertThrows(PhantomTopologyValidationException.class, () -> create(List.of(parent, room), List.of(), List.of(), new TestBackend()), "Room without dungeon chain was accepted.");
	}

	private void testAnchorDangling()
	{
		final PhantomTopologyAnchor anchor = anchor("anchor", "missing", LEFT_POINT, PhantomTopologyAnchorRole.ROUTE);
		PhantomAssertions.assertThrows(PhantomTopologyValidationException.class, () -> create(List.of(node("node")), List.of(anchor), List.of(), new TestBackend()), "Anchor with dangling node was accepted.");
	}

	private void testAnchorContainment()
	{
		final PhantomTopologyNode node = new PhantomTopologyNode("node", PhantomTopologyNodeKind.OUTDOOR_AREA, 0, area(0, 100), null, List.of(), List.of());
		final PhantomTopologyAnchor anchor = anchor("anchor", "node", LEFT_POINT, PhantomTopologyAnchorRole.ROUTE);
		PhantomAssertions.assertThrows(PhantomTopologyValidationException.class, () -> create(List.of(node), List.of(anchor), List.of(), new TestBackend()), "Anchor outside node was accepted.");
	}

	private void testMapRegion()
	{
		final TestBackend backend = new TestBackend();
		final PhantomTopologyAnchor anchor = new PhantomTopologyAnchor("anchor", PhantomTopologyAnchorRole.ROUTE, "node", LEFT_POINT, null, 999, 0, List.of(), List.of());
		PhantomAssertions.assertThrows(PhantomTopologyValidationException.class, () -> create(List.of(new PhantomTopologyNode("node", PhantomTopologyNodeKind.OUTDOOR_AREA, 0, area(0, 1000), null, List.of(), List.of())), List.of(anchor), List.of(), backend), "Incorrect map-region claim was accepted.");
	}

	private void testNpcTemplate()
	{
		final PhantomTopologyAnchor anchor = new PhantomTopologyAnchor("anchor", PhantomTopologyAnchorRole.ROUTE, "node", LEFT_POINT, 999, null, 10, List.of(), List.of());
		PhantomAssertions.assertThrows(PhantomTopologyValidationException.class, () -> create(List.of(new PhantomTopologyNode("node", PhantomTopologyNodeKind.OUTDOOR_AREA, 0, area(0, 1000), null, List.of(), List.of())), List.of(anchor), List.of(), new TestBackend()), "Missing NPC template was accepted.");
	}

	private void testNpcSpawn()
	{
		final TestBackend backend = new TestBackend();
		backend._npcs.put(999, new NpcFact(999, "Folk", false));
		final PhantomTopologyAnchor anchor = new PhantomTopologyAnchor("anchor", PhantomTopologyAnchorRole.ROUTE, "node", LEFT_POINT, 999, null, 10, List.of(), List.of());
		PhantomAssertions.assertThrows(PhantomTopologyValidationException.class, () -> create(List.of(new PhantomTopologyNode("node", PhantomTopologyNodeKind.OUTDOOR_AREA, 0, area(0, 1000), null, List.of(), List.of())), List.of(anchor), List.of(), backend), "NPC anchor without a matching spawn was accepted.");
	}

	private void testFarmingMonster()
	{
		final TestBackend backend = new TestBackend();
		backend._npcs.put(999, new NpcFact(999, "Folk", false));
		backend._spawns.put(999, List.of(new SpawnFact(999, LEFT_POINT)));
		final PhantomTopologyAnchor anchor = new PhantomTopologyAnchor("anchor", PhantomTopologyAnchorRole.FARMING, "node", LEFT_POINT, 999, null, 0, List.of(), List.of());
		PhantomAssertions.assertThrows(PhantomTopologyValidationException.class, () -> create(List.of(new PhantomTopologyNode("node", PhantomTopologyNodeKind.FARMING_AREA, 0, area(0, 1000), null, List.of(), List.of())), List.of(anchor), List.of(), backend), "Non-Monster farming anchor was accepted.");
	}

	private void testSemanticEvidence()
	{
		final TestBackend backend = new TestBackend();
		final PhantomTopologyAnchor anchor = new PhantomTopologyAnchor("anchor", PhantomTopologyAnchorRole.GATEKEEPER, "node", LEFT_POINT, 100, null, 0, List.of(), List.of());
		PhantomAssertions.assertThrows(PhantomTopologyValidationException.class, () -> create(List.of(new PhantomTopologyNode("node", PhantomTopologyNodeKind.CITY, 0, area(0, 1000), null, List.of(), List.of())), List.of(anchor), List.of(), backend), "Semantic NPC role without source evidence was accepted.");
	}

	private void testDoorMissing()
	{
		final TestBackend backend = new TestBackend();
		backend._doors.clear();
		PhantomAssertions.assertThrows(PhantomTopologyValidationException.class, () -> snapshot(backend), "Door edge with missing factual door was accepted.");
	}

	private void testDoorGeometry()
	{
		final TestBackend backend = new TestBackend();
		final List<PhantomTopologyNode> nodes = baseNodes();
		final PhantomTopologyAnchor far = anchor("door.far", "dungeon.left", point(100, 100), PhantomTopologyAnchorRole.DOOR_SIDE);
		final PhantomTopologyAnchor right = anchor("door.right", "dungeon.right", RIGHT_DOOR, PhantomTopologyAnchorRole.DOOR_SIDE);
		final PhantomTopologyEdge edge = doorEdge("door.edge", far.id(), right.id());
		PhantomAssertions.assertThrows(PhantomTopologyValidationException.class, () -> create(nodes, List.of(far, right), List.of(edge), backend), "Door-side anchor far from factual geometry was accepted.");
	}

	private void testLocalEdgeEndpoints()
	{
		final PhantomTopologyEdge edge = new PhantomTopologyEdge("walk", "dungeon.left", "dungeon.right", PhantomTopologyEdgeMode.WALK, true, 1, 1, false, Set.of(), null, null, null, List.of());
		PhantomAssertions.assertThrows(PhantomTopologyValidationException.class, () -> create(baseNodes(), List.of(), List.of(edge), new TestBackend()), "Local edge without endpoint anchors was accepted.");
	}

	private void testDuplicateSemanticEdge()
	{
		final List<PhantomTopologyAnchor> anchors = baseDoorAnchors();
		final PhantomTopologyEdge first = doorEdge("door.first", anchors.get(0).id(), anchors.get(1).id());
		final PhantomTopologyEdge second = doorEdge("door.second", anchors.get(0).id(), anchors.get(1).id());
		PhantomAssertions.assertThrows(PhantomTopologyValidationException.class, () -> create(baseNodes(), anchors, List.of(first, second), new TestBackend()), "Duplicate semantic topology edge was accepted.");
	}

	private void testCanonicalOrder(PhantomTestContext context) throws Exception
	{
		final Path directory = Files.createTempDirectory("phantom-topology-order-");
		try
		{
			final Path xml = directory.resolve("core.xml");
			Files.writeString(xml, topologyXml(nodeXml("alpha", 0) + nodeXml("beta", 200)));
			final PhantomTopologyLoader loader = new PhantomTopologyLoader(directory, new TestBackend(), POLICY);
			final String first = loader.load(1).canonicalHash();
			Files.writeString(xml, topologyXml(nodeXml("beta", 200) + nodeXml("alpha", 0)));
			final String second = loader.load(1).canonicalHash();
			PhantomAssertions.assertEquals(first, second, "Canonical topology hash depends on XML entity order.");
			context.record("topology.core.canonicalHash", first);
		}
		finally
		{
			deleteTree(directory);
		}
	}

	private void testUnknownAttribute(PhantomTestContext context) throws Exception
	{
		final Path directory = Files.createTempDirectory("phantom-topology-schema-");
		try
		{
			Files.writeString(directory.resolve("core.xml"), "<topology schemaVersion=\"1\" datasetId=\"test\" datasetVersion=\"1\" unknown=\"x\" />");
			PhantomAssertions.assertThrows(PhantomTopologyValidationException.class, () -> new PhantomTopologyLoader(directory, new TestBackend(), POLICY).load(1), "Unknown topology XML attribute was accepted.");
		}
		finally
		{
			deleteTree(directory);
		}
	}

	private void testReload(PhantomTestContext context) throws Exception
	{
		final Path directory = Files.createTempDirectory("phantom-topology-reload-");
		try
		{
			final Path xml = directory.resolve("core.xml");
			Files.writeString(xml, topologyXml(nodeXml("alpha", 0)));
			final TestBackend backend = new TestBackend();
			final PhantomTopologyService service = new PhantomTopologyService(new PhantomTopologyLoader(directory, backend, POLICY), backend, POLICY, new NoopSignalPort());
			PhantomAssertions.assertTrue(service.start(), "Topology service did not start.");
			final String original = service.query().snapshot().canonicalHash();
			Files.writeString(xml, "<topology schemaVersion=\"2\" datasetId=\"test\" datasetVersion=\"1\" />");
			PhantomAssertions.assertEquals(PhantomTopologyService.ReloadResult.REJECTED_VALIDATION, service.reload(), "Invalid topology reload was not rejected.");
			PhantomAssertions.assertEquals(original, service.query().snapshot().canonicalHash(), "Invalid reload replaced active topology snapshot.");
			service.beginStop();
			PhantomAssertions.assertTrue(service.finishStop(), "Topology service did not stop after reload test.");
		}
		finally
		{
			deleteTree(directory);
		}
	}

	private void testMostSpecific()
	{
		final PhantomTopologyQuery query = query(new TestBackend());
		final List<PhantomTopologyNode> located = query.locate(LEFT_POINT);
		PhantomAssertions.assertEquals("dungeon.left", located.getFirst().id(), "Most-specific topology node ordering changed.");
	}

	private void testNearest()
	{
		final TestBackend backend = new TestBackend();
		final PhantomTopologyNode node = new PhantomTopologyNode("outdoor", PhantomTopologyNodeKind.OUTDOOR_AREA, 0, area(0, 1000), null, List.of(), List.of());
		final PhantomTopologyAnchor a = anchor("anchor.b", "outdoor", point(200, 200), PhantomTopologyAnchorRole.ROUTE);
		final PhantomTopologyAnchor b = anchor("anchor.a", "outdoor", point(200, 200), PhantomTopologyAnchorRole.ROUTE);
		final PhantomTopologyQuery query = new PhantomTopologyQuery(create(List.of(node), List.of(a, b), List.of(), backend), backend, new PhantomTopologyMetrics());
		PhantomAssertions.assertEquals(List.of("anchor.a", "anchor.b"), query.nearestAnchors(point(0, 0), PhantomTopologyAnchorRole.ROUTE, 2, 1000).stream().map(PhantomTopologyAnchor::id).toList(), "Nearest anchors are not deterministic by distance then ID.");
	}

	private void testLiveDoor()
	{
		final TestBackend backend = new TestBackend();
		final PhantomTopologyQuery query = query(backend);
		backend._doorStates.put(500, DoorState.CLOSED);
		PhantomAssertions.assertFalse(query.isTraversable("dungeon.door"), "Closed live door remained traversable.");
		backend._doorStates.put(500, DoorState.OPEN);
		PhantomAssertions.assertTrue(query.isTraversable("dungeon.door"), "Open live door was not traversable.");
		backend._doorStates.put(500, DoorState.DEAD);
		PhantomAssertions.assertTrue(query.isTraversable("dungeon.door"), "Dead live door was not traversable.");
	}

	private void testRouteHint()
	{
		final PhantomTopologyQuery query = query(new TestBackend());
		final var hint = query.routeHint("door.left", "door.right").orElseThrow();
		PhantomAssertions.assertEquals(List.of("dungeon.door"), hint.edgeIds(), "Bounded topology route hint changed.");
		PhantomAssertions.assertTrue(query.routeHint("missing", "door.right").isEmpty(), "Route hint accepted a missing anchor.");
	}

	private void testReturnedNodeBound()
	{
		final ArrayList<PhantomTopologyNode> nodes = new ArrayList<>();
		for (int index = 0; index < 70; index++)
		{
			nodes.add(new PhantomTopologyNode("node" + index, PhantomTopologyNodeKind.OUTDOOR_AREA, 0, area(0, 1000), null, List.of(), List.of()));
		}
		final TestBackend backend = new TestBackend();
		final PhantomTopologyQuery query = new PhantomTopologyQuery(create(nodes, List.of(), List.of(), backend), backend, new PhantomTopologyMetrics());
		PhantomAssertions.assertEquals(64, query.locate(point(500, 500)).size(), "Topology locate exceeded or missed the 64-node bound.");
	}

	private void testNoServerObjectExposure()
	{
		for (Class<?> type : List.of(PhantomTopologyPoint.class, PhantomTopologyNode.class, PhantomTopologyAnchor.class, PhantomTopologyEdge.class, PhantomTopologySnapshot.class))
		{
			for (java.lang.reflect.Field field : type.getDeclaredFields())
			{
				final String name = field.getType().getName();
				PhantomAssertions.assertFalse(name.endsWith(".Player") || name.endsWith(".Creature") || name.endsWith(".Npc") || name.endsWith(".Door") || name.endsWith(".Spawn") || name.endsWith(".WorldObject"), "Topology value exposes mutable server object type.");
			}
		}
	}

	private void testPolicy()
	{
		PhantomAssertions.assertEquals(100_000, POLICY.maximumNodes(), "Topology node policy changed.");
		PhantomAssertions.assertEquals(200_000, POLICY.maximumEdges(), "Topology edge policy changed.");
		PhantomAssertions.assertEquals(10_000, POLICY.maximumRegisteredProfiles(), "Topology profile policy changed.");
		PhantomAssertions.assertEquals(256, POLICY.maximumGraphNodes(), "Topology graph policy changed.");
	}

	static PhantomTopologySnapshot snapshot(TestBackend backend)
	{
		final List<PhantomTopologyAnchor> anchors = baseDoorAnchors();
		return create(baseNodes(), anchors, List.of(doorEdge("dungeon.door", anchors.get(0).id(), anchors.get(1).id())), backend);
	}

	static PhantomTopologyQuery query(TestBackend backend)
	{
		return new PhantomTopologyQuery(snapshot(backend), backend, new PhantomTopologyMetrics());
	}

	static List<PhantomTopologyNode> baseNodes()
	{
		return List.of(
			new PhantomTopologyNode("dungeon", PhantomTopologyNodeKind.DUNGEON, 0, area(0, 1000), null, List.of(), List.of()),
			new PhantomTopologyNode("dungeon.left", PhantomTopologyNodeKind.ROOM, 0, PhantomTopologyArea.cuboid(0, 100, 500, 100, 900, 0, 100), "dungeon", List.of(), List.of()),
			new PhantomTopologyNode("dungeon.right", PhantomTopologyNodeKind.CORRIDOR, 0, PhantomTopologyArea.cuboid(0, 501, 900, 100, 900, 0, 100), "dungeon", List.of(), List.of()));
	}

	static List<PhantomTopologyAnchor> baseDoorAnchors()
	{
		return List.of(anchor("door.left", "dungeon.left", LEFT_DOOR, PhantomTopologyAnchorRole.DOOR_SIDE), anchor("door.right", "dungeon.right", RIGHT_DOOR, PhantomTopologyAnchorRole.DOOR_SIDE));
	}

	static PhantomTopologyEdge doorEdge(String id, String fromAnchor, String toAnchor)
	{
		return new PhantomTopologyEdge(id, "dungeon.left", "dungeon.right", PhantomTopologyEdgeMode.DOOR, true, 1, 1000, false, Set.of(PhantomPerceptionChannel.LOCAL_CHAT, PhantomPerceptionChannel.COMBAT, PhantomPerceptionChannel.TARGETABILITY), 500, fromAnchor, toAnchor, List.of());
	}

	static PhantomTopologySnapshot create(List<PhantomTopologyNode> nodes, List<PhantomTopologyAnchor> anchors, List<PhantomTopologyEdge> edges, TestBackend backend)
	{
		return PhantomTopologySnapshot.create(1, "test", 1, 1, nodes, anchors, edges, backend, POLICY);
	}

	static PhantomTopologyNode node(String id)
	{
		return new PhantomTopologyNode(id, PhantomTopologyNodeKind.OUTDOOR_AREA, 0, area(0, 1000), null, List.of(), List.of());
	}

	static PhantomTopologyAnchor anchor(String id, String nodeId, PhantomTopologyPoint point, PhantomTopologyAnchorRole role)
	{
		return new PhantomTopologyAnchor(id, role, nodeId, point, null, null, 0, List.of(), List.of());
	}

	static PhantomTopologyArea area(int minimum, int maximum)
	{
		return PhantomTopologyArea.cuboid(0, minimum, maximum, minimum, maximum, 0, 100);
	}

	static PhantomTopologyPoint point(int x, int y)
	{
		return point(x, y, 10);
	}

	static PhantomTopologyPoint point(int x, int y, int z)
	{
		return new PhantomTopologyPoint(x, y, z, 0);
	}

	private static String topologyXml(String entities)
	{
		return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><topology schemaVersion=\"1\" datasetId=\"test\" datasetVersion=\"1\">" + entities + "</topology>";
	}

	private static String nodeXml(String id, int minimum)
	{
		return "<node id=\"" + id + "\" kind=\"OUTDOOR_AREA\" instanceId=\"0\" form=\"CUBOID\" minX=\"" + minimum + "\" maxX=\"" + (minimum + 100) + "\" minY=\"0\" maxY=\"100\" minZ=\"0\" maxZ=\"100\" />";
	}

	private static void deleteTree(Path directory) throws Exception
	{
		if (!Files.exists(directory))
		{
			return;
		}
		try (var paths = Files.walk(directory))
		{
			for (Path path : paths.sorted(Comparator.reverseOrder()).toList())
			{
				Files.deleteIfExists(path);
			}
		}
	}

	static final class TestBackend implements PhantomTopologyValidationBackend
	{
		final Map<Integer, NpcFact> _npcs = new HashMap<>();
		final Map<Integer, List<SpawnFact>> _spawns = new HashMap<>();
		final Map<Integer, DoorFact> _doors = new HashMap<>();
		final Map<Integer, DoorState> _doorStates = new HashMap<>();
		int _mapRegionLocId = 1;
		Runnable _doorStateHook;

		TestBackend()
		{
			_npcs.put(100, new NpcFact(100, "Teleporter", false));
			_npcs.put(101, new NpcFact(101, "Merchant", false));
			_npcs.put(102, new NpcFact(102, "Monster", true));
			_spawns.put(100, List.of(new SpawnFact(100, LEFT_POINT)));
			_spawns.put(101, List.of(new SpawnFact(101, LEFT_POINT)));
			_spawns.put(102, List.of(new SpawnFact(102, LEFT_POINT)));
			final List<PhantomTopologyPoint> vertices = List.of(point(495, 480), point(505, 480), point(505, 520), point(495, 520));
			_doors.put(500, new DoorFact(500, 0, 0, 100, vertices));
			_doorStates.put(500, DoorState.OPEN);
		}

		@Override
		public int mapRegionLocId(int x, int y)
		{
			return _mapRegionLocId;
		}

		@Override
		public Optional<NpcFact> npc(int npcId)
		{
			return Optional.ofNullable(_npcs.get(npcId));
		}

		@Override
		public List<SpawnFact> spawns(int npcId, int maximumResults)
		{
			final List<SpawnFact> result = _spawns.getOrDefault(npcId, List.of());
			return result.subList(0, Math.min(maximumResults, result.size()));
		}

		@Override
		public Optional<DoorFact> door(int doorId)
		{
			return Optional.ofNullable(_doors.get(doorId));
		}

		@Override
		public DoorState doorState(int doorId)
		{
			if (_doorStateHook != null)
			{
				_doorStateHook.run();
			}
			return _doorStates.getOrDefault(doorId, DoorState.MISSING);
		}

		@Override
		public boolean sourceExists(String relativeDatapackPath)
		{
			return true;
		}
	}

	private static final class NoopSignalPort implements PhantomRelevanceSignalPort
	{
		@Override
		public SignalDelivery submit(long profileId, PhantomRelevanceSignal signal)
		{
			return SignalDelivery.ACCEPTED;
		}

		@Override
		public SignalDelivery withdraw(long profileId, String sourceKey, long sequence)
		{
			return SignalDelivery.ACCEPTED;
		}
	}
}
