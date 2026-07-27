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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.l2jmobius.gameserver.phantoms.activity.PhantomRelevanceSignal;
import org.l2jmobius.gameserver.phantoms.topology.PhantomPerceptionChannel;
import org.l2jmobius.gameserver.phantoms.topology.PhantomPerceptionProvider;
import org.l2jmobius.gameserver.phantoms.topology.PhantomPerceptionProvider.CombatEvent;
import org.l2jmobius.gameserver.phantoms.topology.PhantomPerceptionProvider.LocalChatEvent;
import org.l2jmobius.gameserver.phantoms.topology.PhantomRelevanceSignalPort;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyAnchor;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyAnchorRole;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyArea;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyEdge;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyEdgeMode;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyMetrics;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyNode;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyNodeKind;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPolicy;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyProfileRegistry;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyQuery;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyService;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologySnapshot;

public final class PhantomTopologyPerformanceSuite implements PhantomTestSuite
{
	private static final int NODE_COUNT = 10_000;
	private static final int EDGE_COUNT = 20_000;
	private static final int ANCHOR_COUNT = 50_000;
	private static final int PROFILE_COUNT = 10_000;
	private static final int EVENT_COUNT = 1000;

	@Override
	public String id()
	{
		return "topology-performance";
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("bounded-10000-node-perception-structure", this::run);
	}

	private void run(PhantomTestContext context)
	{
		final long started = System.nanoTime();
		final PhantomTopologyPolicy policy = PhantomTopologyPolicy.productionDefaults();
		final PhantomTopologyCoreSuite.TestBackend backend = new PhantomTopologyCoreSuite.TestBackend();
		final ArrayList<PhantomTopologyNode> nodes = new ArrayList<>(NODE_COUNT);
		final ArrayList<PhantomTopologyAnchor> anchors = new ArrayList<>(ANCHOR_COUNT);
		final ArrayList<PhantomTopologyEdge> edges = new ArrayList<>(EDGE_COUNT);
		for (int index = 0; index < NODE_COUNT; index++)
		{
			final PhantomTopologyPoint center = point(index);
			final String nodeId = nodeId(index);
			nodes.add(new PhantomTopologyNode(nodeId, PhantomTopologyNodeKind.OUTDOOR_AREA, 0, PhantomTopologyArea.pointRadius(center, 400), null, List.of(), List.of()));
			for (int anchorIndex = 0; anchorIndex < 5; anchorIndex++)
			{
				anchors.add(new PhantomTopologyAnchor(String.format("anchor.%05d.%d", index, anchorIndex), PhantomTopologyAnchorRole.ROUTE, nodeId, new PhantomTopologyPoint(center.x() + anchorIndex, center.y(), center.z(), 0), null, null, 0, List.of(), List.of()));
			}
		}
		for (int index = 0; index < NODE_COUNT; index++)
		{
			edges.add(edge("edge.a.", index, (index + 1) % NODE_COUNT));
			edges.add(edge("edge.b.", index, (index + 2) % NODE_COUNT));
		}
		final PhantomTopologySnapshot snapshot = PhantomTopologySnapshot.create(1, "performance", 1, 1, nodes, anchors, edges, backend, policy);
		final PhantomTopologyMetrics metrics = new PhantomTopologyMetrics();
		final PhantomTopologyQuery query = new PhantomTopologyQuery(snapshot, backend, metrics);
		final CountingSignalPort signalPort = new CountingSignalPort();
		final PhantomTopologyService service = PhantomTopologyService.fromSnapshotForTesting(snapshot, backend, policy, signalPort);
		PhantomAssertions.assertTrue(service.start(), "Topology performance provider did not start.");
		for (int index = 0; index < PROFILE_COUNT; index++)
		{
			final long profileId = index + 1L;
			PhantomAssertions.assertEquals(PhantomTopologyProfileRegistry.RegistrationResult.REGISTERED, service.registerProfile(profileId), "Topology performance profile registration failed.");
			PhantomAssertions.assertEquals(PhantomTopologyProfileRegistry.UpdateResult.UPDATED, service.updateProfile(profileId, point(index), 1), "Topology performance profile update failed.");
		}
		for (int index = 0; index < EVENT_COUNT; index++)
		{
			final PhantomTopologyPoint source = point(index % NODE_COUNT);
			service.localChat(new LocalChatEvent("local." + index, source, nodeId(index % NODE_COUNT), index, 100_000, 5000));
			service.combat(new CombatEvent("combat." + index, source, nodeId(index % NODE_COUNT), List.of((index % PROFILE_COUNT) + 1L), index, 100_000, 3000));
		}
		final List<PhantomTopologyAnchor> nearest = query.nearestAnchors(point(0), PhantomTopologyAnchorRole.ROUTE, 16, 100_000);
		PhantomAssertions.assertEquals(16, nearest.size(), "Topology performance nearest-anchor shape changed.");
		PhantomAssertions.assertTrue(query.locate(point(0)).size() <= 64, "Topology performance locate exceeded 64 nodes.");
		PhantomAssertions.assertTrue(query.edges(nodeId(0)).size() <= 1024, "Topology performance edge query exceeded 1024.");
		PhantomAssertions.assertTrue(signalPort._maximumPerEvent <= 1024, "Topology performance event exceeded 1024 recipients.");
		PhantomAssertions.assertEquals(NODE_COUNT, snapshot.nodes().size(), "Topology performance node count changed.");
		PhantomAssertions.assertEquals(EDGE_COUNT, snapshot.edges().size(), "Topology performance edge count changed.");
		PhantomAssertions.assertEquals(ANCHOR_COUNT, snapshot.anchors().size(), "Topology performance anchor count changed.");
		PhantomAssertions.assertEquals(PROFILE_COUNT, service.snapshot().registeredProfiles(), "Topology performance profile count changed.");
		service.beginStop();
		PhantomAssertions.assertTrue(service.finishStop(), "Topology performance provider did not stop.");
		final long elapsedMillis = (System.nanoTime() - started) / 1_000_000;
		context.record("topology.performance.datasetHash", snapshot.canonicalHash());
		context.record("topology.performance.nodes", NODE_COUNT);
		context.record("topology.performance.edges", EDGE_COUNT);
		context.record("topology.performance.anchors", ANCHOR_COUNT);
		context.record("topology.performance.profiles", PROFILE_COUNT);
		context.record("topology.performance.localChatEvents", EVENT_COUNT);
		context.record("topology.performance.combatEvents", EVENT_COUNT);
		context.record("topology.performance.nearestLimit", nearest.size());
		context.record("topology.performance.maximumRecipients", signalPort._maximumPerEvent);
		context.record("topology.performance.elapsedMillis", elapsedMillis);
	}

	private static PhantomTopologyPoint point(int index)
	{
		final int row = index / 100;
		final int column = index % 100;
		return new PhantomTopologyPoint(10_000 + (column * 1000), 10_000 + (row * 1000), 0, 0);
	}

	private static String nodeId(int index)
	{
		return String.format("node.%05d", index);
	}

	private static PhantomTopologyEdge edge(String prefix, int from, int to)
	{
		return new PhantomTopologyEdge(prefix + String.format("%05d", from), nodeId(from), nodeId(to), PhantomTopologyEdgeMode.BACKGROUND, false, 1, 1000, true, Set.of(PhantomPerceptionChannel.LOCAL_CHAT, PhantomPerceptionChannel.COMBAT, PhantomPerceptionChannel.TARGETABILITY), null, null, null, List.of());
	}

	private static final class CountingSignalPort implements PhantomRelevanceSignalPort
	{
		private int _currentEventSignals;
		private int _maximumPerEvent;
		private String _lastSource;

		@Override
		public SignalDelivery submit(long profileId, PhantomRelevanceSignal signal)
		{
			if (!signal.sourceKey().equals(_lastSource))
			{
				_lastSource = signal.sourceKey();
				_currentEventSignals = 0;
			}
			_currentEventSignals++;
			_maximumPerEvent = Math.max(_maximumPerEvent, _currentEventSignals);
			return SignalDelivery.ACCEPTED;
		}

		@Override
		public SignalDelivery withdraw(long profileId, String sourceKey, long sequence)
		{
			return SignalDelivery.ACCEPTED;
		}
	}
}
