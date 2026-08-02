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

import java.nio.file.Path;
import java.util.List;

import org.l2jmobius.gameserver.data.xml.DoorData;
import org.l2jmobius.gameserver.data.xml.MapRegionData;
import org.l2jmobius.gameserver.data.xml.SpawnData;
import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyArea.Form;
import org.l2jmobius.gameserver.phantoms.topology.L2jTopologyValidationBackend;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyAnchor;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyAnchorRole;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyEdgeMode;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyLoader;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyMetrics;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyNodeKind;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPolicy;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyQuery;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologySnapshot;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyValidationBackend.DoorState;

public final class PhantomTopologyProductionCorpusSuite implements PhantomTestSuite
{
	private PhantomHeadlessPlayerTestEnvironment _environment;
	private L2jTopologyValidationBackend _backend;
	private PhantomTopologySnapshot _snapshot;
	private PhantomTopologyQuery _query;

	@Override
	public String id()
	{
		return "topology-corpus";
	}

	@Override
	public void beforeAll(PhantomTestContext context) throws Exception
	{
		_environment = new PhantomHeadlessPlayerTestEnvironment();
		_environment.initialize(context);
		MapRegionData.getInstance();
		SpawnData.getInstance();
		DoorData.getInstance();
		_backend = new L2jTopologyValidationBackend();
		_snapshot = new PhantomTopologyLoader(Path.of("data/phantoms/topology"), _backend, PhantomTopologyPolicy.productionDefaults()).load(1);
		_query = new PhantomTopologyQuery(_snapshot, _backend, new PhantomTopologyMetrics());
		context.record("topology.corpus.datasetId", _snapshot.datasetId());
		context.record("topology.corpus.datasetVersion", _snapshot.datasetVersion());
		context.record("topology.corpus.canonicalHash", _snapshot.canonicalHash());
		context.record("topology.corpus.nodes", _snapshot.nodes().size());
		context.record("topology.corpus.anchors", _snapshot.anchors().size());
		context.record("topology.corpus.edges", _snapshot.edges().size());
		context.record("topology.corpus.npcIds", "22859,30080,30081");
		context.record("topology.corpus.doorIds", "17240102");
		context.record("topology.corpus.mapRegionLocIds", "918");
		context.record("topology.corpus.coverage", "Giran city cluster, one outdoor Monster spawn, SSQ Disciples Necropolis Past first room/corridor");
	}

	@Override
	public void afterAll(PhantomTestContext context) throws Exception
	{
		if (_environment != null)
		{
			_environment.shutdown();
		}
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("01-dataset-version-counts", _ -> testDataset());
		registry.add("02-map-region-facts", _ -> testMapRegion());
		registry.add("03-npc-spawn-facts", _ -> testNpcSpawns());
		registry.add("04-door-room-passage-facts", _ -> testDoorPassage());
		registry.add("05-source-evidence-complete", _ -> testSources());
		registry.add("06-representative-roles-and-modes", _ -> testCoverage());
		registry.add("07-exact-feasible-territory-polygons", _ -> testFeasibleTerritories());
	}

	private void testDataset()
	{
		PhantomAssertions.assertEquals("high-five-core", _snapshot.datasetId(), "Production topology dataset ID changed.");
		PhantomAssertions.assertEquals(1, _snapshot.schemaVersion(), "Production topology schema version changed.");
		PhantomAssertions.assertEquals(2, _snapshot.datasetVersion(), "Production topology dataset version changed.");
		PhantomAssertions.assertEquals(23, _snapshot.nodes().size(), "Production topology node count changed.");
		PhantomAssertions.assertEquals(23, _snapshot.anchors().size(), "Production topology anchor count changed.");
		PhantomAssertions.assertEquals(3, _snapshot.edges().size(), "Production topology edge count changed.");
		PhantomAssertions.assertEquals(64, _snapshot.canonicalHash().length(), "Production topology canonical SHA-256 length changed.");
	}

	private void testMapRegion()
	{
		for (String id : List.of("giran.city.center", "giran.gatekeeper.30080", "giran.shop.30081"))
		{
			final PhantomTopologyAnchor anchor = _snapshot.anchorById().get(id);
			PhantomAssertions.assertEquals(918, _backend.mapRegionLocId(anchor.point().x(), anchor.point().y()), "Production Giran map-region fact changed for " + id + ".");
			PhantomAssertions.assertEquals(918, anchor.mapRegionLocId(), "Production Giran map-region claim changed.");
		}
	}

	private void testNpcSpawns()
	{
		for (int npcId : List.of(22859, 30080, 30081))
		{
			PhantomAssertions.assertTrue(_backend.npc(npcId).isPresent(), "Production topology NPC template is missing: " + npcId + ".");
			PhantomAssertions.assertFalse(_backend.spawns(npcId, 4096).isEmpty(), "Production topology NPC spawn is missing: " + npcId + ".");
		}
		PhantomAssertions.assertTrue(_backend.npc(22859).orElseThrow().monster(), "Production farming NPC is no longer a Monster template.");
	}

	private void testDoorPassage()
	{
		final var edge = _snapshot.edgeById().get("ssq.necropolis.past.door.17240102");
		PhantomAssertions.assertEquals(PhantomTopologyEdgeMode.DOOR, edge.mode(), "Production room passage is no longer a DOOR edge.");
		PhantomAssertions.assertTrue(_backend.door(17240102).isPresent(), "Production factual door 17240102 is missing.");
		PhantomAssertions.assertEquals(DoorState.CLOSED, _backend.doorState(17240102), "Production factual door default live state changed.");
		PhantomAssertions.assertFalse(_query.isTraversable(edge.id()), "Closed production room door was traversable.");
		PhantomAssertions.assertEquals(PhantomTopologyNodeKind.ROOM, _snapshot.nodeById().get(edge.fromNodeId()).kind(), "Production factual first-room node changed.");
		PhantomAssertions.assertEquals(PhantomTopologyNodeKind.CORRIDOR, _snapshot.nodeById().get(edge.toNodeId()).kind(), "Production factual corridor node changed.");
	}

	private void testSources()
	{
		_snapshot.nodes().forEach(node -> node.sourceRefs().forEach(source -> PhantomAssertions.assertTrue(_backend.sourceExists(source), "Production node evidence source is missing.")));
		_snapshot.anchors().forEach(anchor -> anchor.sourceRefs().forEach(source -> PhantomAssertions.assertTrue(_backend.sourceExists(source), "Production anchor evidence source is missing.")));
		_snapshot.edges().forEach(edge -> edge.sourceRefs().forEach(source -> PhantomAssertions.assertTrue(_backend.sourceExists(source), "Production edge evidence source is missing.")));
	}

	private void testCoverage()
	{
		final List<PhantomTopologyAnchorRole> roles = _snapshot.anchors().stream().map(PhantomTopologyAnchor::role).distinct().toList();
		for (PhantomTopologyAnchorRole role : List.of(PhantomTopologyAnchorRole.CITY_CENTER, PhantomTopologyAnchorRole.SHOP, PhantomTopologyAnchorRole.GATEKEEPER, PhantomTopologyAnchorRole.FARMING, PhantomTopologyAnchorRole.ROUTE, PhantomTopologyAnchorRole.ROOM_CENTER, PhantomTopologyAnchorRole.DOOR_SIDE))
		{
			PhantomAssertions.assertTrue(roles.contains(role), "Production topology role coverage is missing: " + role + ".");
		}
		PhantomAssertions.assertTrue(_snapshot.edges().stream().anyMatch(edge -> edge.backgroundEligible() && (edge.mode() == PhantomTopologyEdgeMode.BACKGROUND)), "Production topology lacks a background-eligible edge.");
	}

	private void testFeasibleTerritories()
	{
		final var nodes = _snapshot.nodes().stream().filter(node -> (node.kind() == PhantomTopologyNodeKind.FARMING_AREA) && (node.area().form() == Form.POLYGON)).toList();
		final var anchors = _snapshot.anchors().stream().filter(anchor -> (anchor.role() == PhantomTopologyAnchorRole.FARMING) && (_snapshot.nodeById().get(anchor.nodeId()).area().form() == Form.POLYGON)).toList();
		final StringBuilder zDrift = new StringBuilder();
		PhantomAssertions.assertEquals(15, nodes.size(), "Feasible factual territory node count changed.");
		PhantomAssertions.assertEquals(15, anchors.size(), "Feasible factual territory anchor count changed.");
		for (var node : nodes)
		{
			final var matching = anchors.stream().filter(anchor -> anchor.nodeId().equals(node.id())).toList();
			PhantomAssertions.assertEquals(1, matching.size(), "Feasible territory does not have exactly one shared anchor.");
			final var anchor = matching.getFirst();
			PhantomAssertions.assertTrue(node.area().contains(anchor.point()), "Feasible territory anchor is outside the exact polygon.");
			final int normalizedZ = GeoEngine.getInstance().getHeight(anchor.point().x(), anchor.point().y(), anchor.point().z());
			if (normalizedZ != anchor.point().z())
			{
				zDrift.append(node.id()).append('=').append(normalizedZ).append(';');
			}
			final int factualZ = node.area().minZ() + ((node.area().maxZ() - node.area().minZ()) / 2);
			PhantomAssertions.assertEquals(Math.abs(anchor.point().z() - factualZ), anchor.validationTolerance(), "Feasible territory anchor tolerance differs from the exact GeoEngine Z delta.");
			final long maximumSquared = node.area().vertices().stream().mapToLong(vertex ->
			{
				final long dx = (long) anchor.point().x() - vertex.x();
				final long dy = (long) anchor.point().y() - vertex.y();
				return (dx * dx) + (dy * dy);
			}).max().orElseThrow();
			PhantomAssertions.assertTrue(maximumSquared <= 4_000_000L, "Feasible territory anchor exceeds activeTargetDistance=2000.");
			PhantomAssertions.assertEquals(node.sourceRefs(), anchor.sourceRefs(), "Feasible node/anchor source identity differs.");
		}
		PhantomAssertions.assertEquals("", zDrift.toString(), "Feasible territory anchor Z is not GeoEngine-normalized.");
	}
}
