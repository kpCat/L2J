/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.knowledge;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Element;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.phantoms.activity.PhantomMaterializationRetentionPolicy;
import org.l2jmobius.gameserver.phantoms.background.*;
import org.l2jmobius.gameserver.phantoms.decision.*;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.*;
import org.l2jmobius.gameserver.phantoms.topology.*;
import org.l2jmobius.tests.phantoms.PhantomAssertions;

/** DB-free boundary proof. The opt-in required-product assertion intentionally remains RED. */
public final class PhantomOffscreen092Checks
{
	private static final PhantomBackgroundState.Hashes HASHES = new PhantomBackgroundState.Hashes("knowledge092", "topology092", "progression092", "commerce092");

	public static void main(String[] args) throws Exception
	{
		if (args.length != 2 || !Set.of("contracts", "required-product").contains(args[0]))
		{
			throw new IllegalArgumentException("Usage: contracts|required-product <module-root>");
		}
		final var witness = witness(Path.of(args[1]));
		final var state = state(witness.origin(), List.of());
		final var previous = goal(witness, 1, 1);
		final var absent = planner(witness, false).replaceFromState(92, state, previous, 920092, 1);
		System.out.println("GRAPH0_SOURCE anchor=" + witness.origin().id() + " destination=" + witness.destination().id()
			+ " npc=" + witness.npc().npcId() + " level=" + witness.npc().level() + " exp=" + witness.npc().exp() + " sp=" + witness.npc().sp()
			+ " result=" + absent.reasonKey() + "; source-xml=true; fixture-graph=true; native-viability=NOT_RUN");
		if (args[0].equals("required-product"))
		{
			// This witnesses the missing product path; it is not permission to weaken ordinary candidate().
			PhantomAssertions.assertTrue(absent.ready(), "REQUIRED092_RED: no stored offscreen admission exists for the known outdoor target on graph0.");
			throw new AssertionError("REQUIRED092_RED: a candidate alone cannot prove native transaction, movement or earned reward.");
		}
		PhantomAssertions.assertFalse(absent.ready(), "Ordinary planner must keep rejecting an uncertified route.");
		PhantomAssertions.assertEquals("planner.target_or_route.absent", absent.reasonKey(), "The witness must fail specifically at target/route admission.");
		final var certified = planner(witness, true).replaceFromState(92, state, previous, 920092, 1);
		PhantomAssertions.assertTrue(certified.ready(), "The identical source facts must be selectable with a certified fixture edge.");
		PhantomAssertions.assertEquals(List.of("fixture092.certified"), certified.routeEdgeIds(), "Existing certified travel must retain its route.");
		System.out.println("PASS092 ordinary-graph0-rejection/certified-positive-control; NOT_OFFSCREEN_GREEN");
		identityAndCodec(witness, previous);
		retentionBoundary();
		PhantomAssertions.assertFalse(DatabaseFactory.isInitialized(), "DB-free proof must not initialize JDBC.");
		System.out.println("PASS092 contracts=3; native-players=0; SQL=0; product=RED; NATIVE_VERIFIED=NOT_RUN");
	}

	private static void identityAndCodec(Witness witness, PhantomGoal first)
	{
		final var replacement = goal(witness, first.goalId() + 1, first.revision() + 1);
		PhantomAssertions.assertEquals(PhantomBackgroundGoalSpec.parse(first), PhantomBackgroundGoalSpec.parse(replacement),
			"The present farm-spec erases goal/revision: it cannot itself bind a residual to an offscreen transition.");
		final var history = new PhantomBackgroundOperationKey.HistoricalIdentity("a".repeat(64), 1, 0, 100, 101, "b".repeat(64));
		final var key = operation(first, witness, history);
		PhantomAssertions.assertFalse(key.digest().equals(operation(replacement, witness, history).digest()), "Canonical transaction identity already distinguishes changed goals; do not replace it.");
		PhantomAssertions.assertThrows(IllegalArgumentException.class,
			() -> new PhantomBackgroundOperationKey(92, 920092, 1, 1, 0, 0, PhantomBackgroundOperationKey.ActionKind.HISTORICAL_TRAVEL,
				witness.npc().npcId(), witness.destination().id(), PhantomBackgroundState.MODEL_VERSION, HASHES, null, history, "OFFSCREEN_VIRTUAL_TRANSIT"),
			"The NORMAL GK leg field must not be repurposed as an offscreen marker.");
		final var codec = new PhantomBackgroundStateCodec();
		for (int count : List.of(0, 161))
		{
			final var items = new ArrayList<PhantomBackgroundState.ItemObject>();
			for (int index = 0; index < count; index++)
			{
				items.add(new PhantomBackgroundState.ItemObject(930000 + index, 100, 1, false, PhantomBackgroundState.ItemLocation.INVENTORY));
			}
			final var original = state(witness.origin(), items);
			final byte[] bytes = codec.encode(original);
			PhantomAssertions.assertEquals(count == 0 ? 2 : 3, Byte.toUnsignedInt(bytes[5]), "Both actual format2 and compact3 must be exercised.");
			PhantomAssertions.assertEquals(original, codec.decode(bytes), "An unchanged state must round-trip exactly.");
			PhantomAssertions.assertTrue(Arrays.equals(bytes, codec.encode(codec.decode(bytes))), "Re-encoding must preserve the exact original bytes.");
		}
		System.out.println("PASS092 goal-spec-identity-gap/canonical-key-distinction/format2+compact3; residual-continuation=NOT_IMPLEMENTED");
	}

	private static PhantomBackgroundOperationKey operation(PhantomGoal goal, Witness witness, PhantomBackgroundOperationKey.HistoricalIdentity history)
	{
		return new PhantomBackgroundOperationKey(92, 920092, goal.goalId(), goal.revision(), 0, 0, PhantomBackgroundOperationKey.ActionKind.HISTORICAL_TRAVEL,
			witness.npc().npcId(), witness.destination().id(), PhantomBackgroundState.MODEL_VERSION, HASHES, null, history);
	}

	private static void retentionBoundary()
	{
		final var now = new AtomicLong(1_000_000);
		final var facts = new AtomicReference<>(new PhantomMaterializationRetentionPolicy.Facts(true, true, true, false, false));
		final var policy = new PhantomMaterializationRetentionPolicy(_ -> facts.get(), now::get, 60_000);
		PhantomAssertions.assertTrue(policy.observe(92).reasons().contains(PhantomMaterializationRetentionPolicy.Reason.RECENT_HUMAN), "A real-local materialization must retain recent human continuity.");
		facts.set(new PhantomMaterializationRetentionPolicy.Facts(false, false, false, false, false));
		now.addAndGet(1_000_000);
		final var stored = policy.observe(92);
		PhantomAssertions.assertTrue(!stored.materialized() && stored.reasons().isEmpty() && stored.lastHumanNanos() == 0,
			"Current retention deliberately forgets a stored actor; this empty hold is not permission for offscreen movement.");
		System.out.println("PASS092 recent-human/stored-retention-boundary; offscreen-absence-proof=UNAVAILABLE");
	}

	private static PhantomHistoricalBackgroundPlanner planner(Witness witness, boolean linked)
	{
		final var anchors = List.of(witness.origin(), witness.destination());
		final var backend = new PhantomTopologyValidationBackend()
		{
			@Override public int mapRegionLocId(int x, int y) { return 1; }
			@Override public Optional<NpcFact> npc(int id) { return Optional.of(new NpcFact(id, "Monster", true)); }
			@Override public List<SpawnFact> spawns(int id, int bound) { return List.of(new SpawnFact(id, witness.destination().point())); }
			@Override public Optional<DoorFact> door(int id) { return Optional.empty(); }
			@Override public DoorState doorState(int id) { return DoorState.MISSING; }
			@Override public boolean sourceExists(String source) { return true; }
		};
		final var nodes = witness.nodes();
		final var edges = linked ? List.of(new PhantomTopologyEdge("fixture092.certified", witness.origin().nodeId(), witness.destination().nodeId(),
			PhantomTopologyEdgeMode.BACKGROUND, true, 1, 60_000, true, Set.of(), null, witness.origin().id(), witness.destination().id(), List.of())) : List.<PhantomTopologyEdge>of();
		final var topology = new PhantomTopologyQuery(PhantomTopologySnapshot.create(1, "fixture092", 1, 1, nodes, anchors, edges, backend, PhantomTopologyPolicy.productionDefaults()), backend, new PhantomTopologyMetrics());
		final var snapshot = new PhantomGameKnowledgeSnapshot("fixture092", 1, topology.snapshot().canonicalHash(), PhantomGameKnowledgePolicy.productionDefaults(),
			List.of(), List.of(witness.npc()), List.of(), List.of(), List.of(new SpawnAreaFact(witness.npc().npcId(), 0, witness.destination().nodeId(), 1, 1, 1,
				List.of(), PhantomGameKnowledgeAuthority.TOPOLOGY_SNAPSHOT_FACT)), List.of(), List.of(), List.of(), List.of(), List.of());
		final var authority = new PhantomBackgroundAuthority()
		{
			@Override public PhantomBackgroundState.Hashes hashes() { return HASHES; }
			@Override public PhantomBackgroundState capture(long id, Player player, PhantomGoal goal, PhantomBackgroundState previous) { throw new UnsupportedOperationException(); }
			@Override public boolean matchesRuntime(Player player, PhantomBackgroundState state) { return false; }
			@Override public FarmInput farmInput(PhantomBackgroundState state, PhantomBackgroundGoalSpec goal) { throw new UnsupportedOperationException(); }
			@Override public TravelAdvance advanceTravel(PhantomBackgroundState state, PhantomBackgroundGoalSpec goal, long budget) { throw new UnsupportedOperationException(); }
			@Override public Optional<PhantomBackgroundState.Position> canonicalRecoveryPosition(int x, int y, int z, int instance, int heading) { return Optional.empty(); }
			@Override public List<PhantomBackgroundState.AutoGetSkill> autoGetSkills(PhantomBackgroundState.Identity identity, int level) { return List.of(); }
		};
		return new PhantomHistoricalBackgroundPlanner(new PhantomGameKnowledgeQuery(snapshot, new PhantomGameKnowledgeMetrics()), topology, authority);
	}

	/** Read actual stock coordinates and rewards; fixture validation is explicitly not native Geo proof. */
	private static Witness witness(Path module) throws Exception
	{
		final var root = xml(module.resolve("dist/game/data/phantoms/topology/high-five-core.xml"));
		final var outdoor = new java.util.HashSet<String>();
		final var nodes = root.getElementsByTagName("node");
		for (int index = 0; index < nodes.getLength(); index++)
		{
			final var node = (Element) nodes.item(index);
			if (node.getAttribute("kind").equals("FARMING_AREA") && List.of(node.getAttribute("tags").split(",")).contains("outdoor-farming")) { outdoor.add(node.getAttribute("id")); }
		}
		final var anchors = new ArrayList<PhantomTopologyAnchor>();
		final var elements = root.getElementsByTagName("anchor");
		for (int index = 0; index < elements.getLength(); index++)
		{
			final var anchor = (Element) elements.item(index);
			if (!anchor.getAttribute("role").equals("FARMING") || !anchor.getAttribute("instanceId").equals("0") || !outdoor.contains(anchor.getAttribute("nodeId"))) { continue; }
			final var point = new PhantomTopologyPoint(number(anchor, "x"), number(anchor, "y"), number(anchor, "z"), 0);
			final var refs = new ArrayList<String>();
			final var sources = anchor.getElementsByTagName("source");
			for (int source = 0; source < sources.getLength(); source++) { refs.add(((Element) sources.item(source)).getAttribute("path")); }
			anchors.add(new PhantomTopologyAnchor(anchor.getAttribute("id"), PhantomTopologyAnchorRole.FARMING, anchor.getAttribute("nodeId"), point,
				anchor.hasAttribute("npcId") ? number(anchor, "npcId") : null, null, number(anchor, "tolerance"), List.of("monster-spawn"), refs));
		}
		final var destination = anchors.stream().filter(anchor -> anchor.npcId() != null).findFirst().orElseThrow();
		final var origin = anchors.stream().filter(anchor -> !anchor.nodeId().equals(destination.nodeId())).findFirst().orElseThrow();
		final int id = destination.npcId();
		final var npcs = xml(module.resolve("dist/game/data/stats/npcs/" + (id / 100 * 100) + "-" + (id / 100 * 100 + 99) + ".xml")).getElementsByTagName("npc");
		for (int index = 0; index < npcs.getLength(); index++)
		{
			final var npc = (Element) npcs.item(index);
			if (number(npc, "id") != id) { continue; }
			PhantomAssertions.assertEquals("Monster", npc.getAttribute("type"), "Source NPC must really be a stock monster.");
			final var acquire = (Element) npc.getElementsByTagName("acquire").item(0);
			final var spawn = xml(module.resolve("dist/game/data/spawns/Others/22_21.xml")).getElementsByTagName("npc");
			boolean found = false;
			for (int row = 0; row < spawn.getLength(); row++)
			{
				final var fact = (Element) spawn.item(row);
				found |= number(fact, "id") == id && number(fact, "x") == destination.point().x() && number(fact, "y") == destination.point().y() && number(fact, "z") == destination.point().z();
			}
			PhantomAssertions.assertTrue(found, "The destination must match an exact stock spawn, not only a topology tag.");
			return new Witness(origin, destination, new NpcFact(id, number(npc, "level"), NpcKind.MONSTER, true, true, false,
				Double.parseDouble(acquire.getAttribute("exp")), Double.parseDouble(acquire.getAttribute("sp")), PhantomGameKnowledgeAuthority.SERVER_LOADER_FACT),
				List.of(farmingNode(root, origin), farmingNode(root, destination)));
		}
		throw new AssertionError("Stock NPC witness is absent.");
	}

	private static PhantomTopologyNode farmingNode(Element root, PhantomTopologyAnchor anchor)
	{
		final var nodes = root.getElementsByTagName("node");
		for (int index = 0; index < nodes.getLength(); index++)
		{
			final var node = (Element) nodes.item(index);
			if (!node.getAttribute("id").equals(anchor.nodeId())) { continue; }
			final PhantomTopologyArea area;
			if (node.getAttribute("form").equals("POLYGON"))
			{
				final var vertices = new ArrayList<PhantomTopologyArea.Vertex>();
				final var elements = node.getElementsByTagName("vertex");
				for (int vertex = 0; vertex < elements.getLength(); vertex++)
				{
					final var element = (Element) elements.item(vertex);
					vertices.add(new PhantomTopologyArea.Vertex(number(element, "x"), number(element, "y")));
				}
				area = PhantomTopologyArea.polygon(0, number(node, "minZ"), number(node, "maxZ"), vertices);
			}
			else if (node.getAttribute("form").equals("POINT_RADIUS"))
			{
				area = PhantomTopologyArea.pointRadius(new PhantomTopologyPoint(number(node, "x"), number(node, "y"), number(node, "z"), 0), number(node, "radius"));
			}
			else { throw new AssertionError("Unsupported source fixture geometry."); }
			return new PhantomTopologyNode(anchor.nodeId(), PhantomTopologyNodeKind.FARMING_AREA, 0, area, null, List.of("outdoor-farming"), anchor.sourceRefs());
		}
		throw new AssertionError("Source farming node is absent.");
	}

	private static Element xml(Path file) throws Exception
	{
		final var factory = DocumentBuilderFactory.newInstance();
		factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
		return factory.newDocumentBuilder().parse(file.toFile()).getDocumentElement();
	}

	private static int number(Element element, String key) { return Integer.parseInt(element.getAttribute(key)); }
	private record Witness(PhantomTopologyAnchor origin, PhantomTopologyAnchor destination, NpcFact npc, List<PhantomTopologyNode> nodes) { }

	private static PhantomGoal goal(Witness witness, long id, long revision)
	{
		return new PhantomGoal(id, "farm.background", PhantomGoalStatus.ACTIVE, new PhantomDomainRef("profile", "92"), new PhantomDomainRef("npc", Integer.toString(witness.npc().npcId())),
			1, 0, "background.farm", List.of(new PhantomDomainRef("background.farm", witness.npc().npcId() + "@" + witness.destination().id())),
			new PhantomDomainRef("topology.anchor", witness.destination().id()), "farm.background", 500, 0, 0, 0, Map.of(), "fixture092", revision);
	}

	private static PhantomBackgroundState state(PhantomTopologyAnchor origin, List<PhantomBackgroundState.ItemObject> items)
	{
		return new PhantomBackgroundState(PhantomBackgroundState.State.READY, new PhantomBackgroundState.Identity(92, 920092, 0, 1, 0), new PhantomBackgroundState.Progress(80, 0, 0, 0),
			new PhantomBackgroundState.Vitals(100, 100, 100, 100, 100, 100), new PhantomBackgroundState.Position(0, origin.point().x(), origin.point().y(), origin.point().z(), 0, origin.id()),
			new PhantomBackgroundState.CombatFacts(PhantomBackgroundState.ModelKind.MELEE, 100, 100, 100, 100, 500, 500, 1, 1, 1, 1, 0, 1, 1, 1, 1), PhantomBackgroundState.Loadout.none(),
			new PhantomBackgroundState.InventoryFacts(items.isEmpty() ? List.of() : List.of(100), items, "fixture092", 0, 1000, items.size(), 250), List.of(),
			new PhantomBackgroundState.Clock(920092, 30_000, 0), PhantomBackgroundState.Receipt.empty(), HASHES);
	}
}
