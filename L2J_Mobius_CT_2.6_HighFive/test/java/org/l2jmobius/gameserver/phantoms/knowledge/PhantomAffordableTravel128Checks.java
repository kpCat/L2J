/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.knowledge;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Element;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundAuthority;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundGoalSpec;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState;
import org.l2jmobius.gameserver.phantoms.background.PhantomHistoricalBackgroundPlanner;
import org.l2jmobius.gameserver.phantoms.background.PhantomNormalGatekeeperTravel;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoal;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeAuthority;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeMetrics;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgePolicy;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeQuery;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeSnapshot;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomOffscreen092Checks;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyAnchor;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyAnchorRole;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyArea;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyMetrics;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyNode;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyNodeKind;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPolicy;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyQuery;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologySnapshot;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyValidationBackend;

/** DB-free planning regression. Fixture farms are not native movement or earned rewards. */
public final class PhantomAffordableTravel128Checks
{
	private static void check(boolean value, String reason) { if (!value) { throw new AssertionError(reason); } }
	private static PhantomTopologyAnchor anchor(String id, int x, int y, int z, int npc)
	{
		return new PhantomTopologyAnchor(id, PhantomTopologyAnchorRole.FARMING, id + ".node", new PhantomTopologyPoint(x, y, z, 0), npc, null, 100, List.of(), List.of());
	}
	public static void main(String[] args) throws Exception
	{
		final var document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(Path.of("data/phantoms/travel/high-five-normal-gk.xml").toFile());
		final Element root = document.getDocumentElement();
		Element expensive = null, cheap = null;
		for (var node = root.getFirstChild(); node != null;)
		{
			final var next = node.getNextSibling();
			if (node instanceof Element leg && leg.getAttribute("fromAnchorId").equals("giran.city.center"))
			{
				if (leg.getAttribute("toAnchorId").equals("generated.route.live002.goddard.arrival.anchor")) { expensive = leg; }
				else if (leg.getAttribute("toAnchorId").equals("generated.route.live002.aden.arrival.anchor")) { cheap = leg; }
			}
			if (node != expensive && node != cheap) { root.removeChild(node); }
			node = next;
		}
		check(expensive != null && cheap != null, "Source-backed NORMAL comparison legs are absent");
		final long wallet = Long.parseLong(cheap.getAttribute("feeCount"));
		check(wallet > 0 && wallet < Long.parseLong(expensive.getAttribute("feeCount")), "Regression needs distinct real native fees");
		final var origin = anchor("giran.city.center", 82480, 149087, -3464, 999996);
		final var distant = anchor(expensive.getAttribute("toAnchorId"), 148024, -55281, -2728, 999998);
		final var affordable = anchor(cheap.getAttribute("toAnchorId"), 146783, 25808, -2008, 999997);
		final var anchors = List.of(origin, distant, affordable);
		final var backend = new PhantomTopologyValidationBackend()
		{
			@Override public int mapRegionLocId(int x, int y) { return 1; }
			@Override public Optional<NpcFact> npc(int id) { return Optional.of(new NpcFact(id, "Monster", true)); }
			@Override public List<SpawnFact> spawns(int id, int bound) { return anchors.stream().filter(a -> a.npcId() == id).map(a -> new SpawnFact(id, a.point())).toList(); }
			@Override public Optional<DoorFact> door(int id) { return Optional.empty(); }
			@Override public DoorState doorState(int id) { return DoorState.MISSING; }
			@Override public boolean sourceExists(String source) { return true; }
		};
		final var nodes = anchors.stream().map(a -> new PhantomTopologyNode(a.nodeId(), PhantomTopologyNodeKind.FARMING_AREA, 0, PhantomTopologyArea.pointRadius(a.point(), 1000), null, List.of("outdoor-farming"), List.of())).toList();
		final var topology = new PhantomTopologyQuery(PhantomTopologySnapshot.create(1, "affordable128", 1, 1, nodes, anchors, List.of(), backend, PhantomTopologyPolicy.productionDefaults()), backend, new PhantomTopologyMetrics());
		final var facts = PhantomGameKnowledgeAuthority.SERVER_LOADER_FACT;
		final var npcs = List.of(new PhantomGameKnowledgeModel.NpcFact(999998, 80, PhantomGameKnowledgeModel.NpcKind.MONSTER, true, true, false, 1000, 100, facts), new PhantomGameKnowledgeModel.NpcFact(999997, 79, PhantomGameKnowledgeModel.NpcKind.MONSTER, true, true, false, 1000, 100, facts));
		final var areas = List.of(new PhantomGameKnowledgeModel.SpawnAreaFact(999998, 0, distant.nodeId(), 1, 1, 1, List.of(), facts), new PhantomGameKnowledgeModel.SpawnAreaFact(999997, 0, affordable.nodeId(), 1, 1, 1, List.of(), facts));
		final var knowledge = new PhantomGameKnowledgeQuery(new PhantomGameKnowledgeSnapshot("affordable128", 1, topology.snapshot().canonicalHash(), PhantomGameKnowledgePolicy.productionDefaults(), List.of(), npcs, List.of(), List.of(), areas, List.of(), List.of(), List.of(), List.of(), List.of()), new PhantomGameKnowledgeMetrics());
		final Path catalog = Files.createTempFile("phantom-affordable128-", ".xml");
		try
		{
			TransformerFactory.newInstance().newTransformer().transform(new DOMSource(document), new StreamResult(catalog.toFile()));
			final var travel = PhantomNormalGatekeeperTravel.load(catalog, topology);
			final var leg = travel.legs().stream().filter(value -> value.fromAnchorId().equals(origin.id())).findFirst().orElseThrow();
			check(PhantomNormalGatekeeperTravel.maximumFee(leg, 1, 80) == leg.feeCount() && leg.feeCount() > 0, "Ordinary player/catalog price was changed");
			check(PhantomNormalGatekeeperTravel.phantomMaximumFee(leg, 1, 80) == 0 && PhantomNormalGatekeeperTravel.phantomFee(leg, 1, 80, 1000) == 0, "Managed Phantom travel still reserves or charges a fee");
			final var authority = new PhantomBackgroundAuthority()
			{
				@Override public PhantomBackgroundState.Hashes hashes() { return new PhantomBackgroundState.Hashes("knowledge128", "topology128", "progression128", "commerce128"); }
				@Override public PhantomNormalGatekeeperTravel travelQuery(PhantomTopologyQuery ignored) { return travel; }
				@Override public PhantomBackgroundState capture(long id, Player player, PhantomGoal goal, PhantomBackgroundState previous) { throw new UnsupportedOperationException(); }
				@Override public boolean matchesRuntime(Player player, PhantomBackgroundState state) { return false; }
				@Override public FarmInput farmInput(PhantomBackgroundState state, PhantomBackgroundGoalSpec goal) { throw new UnsupportedOperationException(); }
				@Override public TravelAdvance advanceTravel(PhantomBackgroundState state, PhantomBackgroundGoalSpec goal, long budget) { throw new UnsupportedOperationException(); }
				@Override public Optional<PhantomBackgroundState.Position> canonicalRecoveryPosition(int x, int y, int z, int instance, int heading) { return Optional.empty(); }
				@Override public List<PhantomBackgroundState.AutoGetSkill> autoGetSkills(PhantomBackgroundState.Identity identity, int level) { return List.of(); }
			};
			final var planner = new PhantomHistoricalBackgroundPlanner(knowledge, topology, authority);
			final var witness = new PhantomOffscreen092Checks.Witness(origin, distant, npcs.getFirst(), nodes);
			final var goal = PhantomOffscreen092Checks.goal(witness, 128, 1);
			final var empty = PhantomOffscreen092Checks.state(origin, List.of());
			final var inventory = new PhantomBackgroundState.InventoryFacts(List.of(57), List.of(new PhantomBackgroundState.ItemObject(128001, 57, wallet, true, PhantomBackgroundState.ItemLocation.INVENTORY)), "fixture128", 0, 1000, 1, 250);
			final var state = new PhantomBackgroundState(empty.state(), empty.identity(), empty.progress(), empty.vitals(), empty.position(), empty.combat(), empty.loadout(), inventory, empty.autoGetSkills(), empty.clock(), empty.receipt(), empty.hashes());
			final var paidSource = new PhantomDomainRef("background.farm", "999997@" + affordable.id());
			final var beyondWallet = new PhantomDomainRef("background.farm", "999998@" + distant.id());
			check(planner.partyRouteCost(state, paidSource).orElseThrow().feeAdena() == wallet, "Party route did not reserve the full ordinary Gatekeeper fare");
			check(planner.partyRouteCost(state, beyondWallet).isEmpty(), "Party route selected an unaffordable Gatekeeper");
			check(planner.partyRouteCost(empty, paidSource).isEmpty(), "Empty Party wallet was treated as a paid route");
			check(!planner.partySourceAvailable(empty, paidSource, java.util.Set.of(), wallet - 1), "Adena below the stock fare admitted the source");
			check(planner.partySourceAvailable(empty, paidSource, java.util.Set.of(), wallet), "Canonical Party funds were lost when idle history did not track Adena");
			check(planner.rendezvousItinerary(empty, paidSource, java.util.Set.of(), wallet).steps().stream()
				.anyMatch(step -> step.type() == PhantomNormalGatekeeperTravel.Type.NORMAL_GATEKEEPER), "Physical preflight lost the paid leg from the owner proposal");
			check(!planner.partySourceAvailable(empty, beyondWallet, java.util.Set.of(), wallet), "Canonical funds waived the exact Party fare");
			final var atGate = new PhantomBackgroundState.Position(0, leg.sourceX(), leg.sourceY(), leg.sourceZ(), 0, origin.id());
			final var storedAfterWalk = new PhantomBackgroundState(state.state(), state.identity(), state.progress(), state.vitals(), atGate,
				state.combat(), state.loadout(), state.inventory(), state.autoGetSkills(), state.clock(), state.receipt(), state.hashes());
			final var resumed = planner.rendezvousItinerary(storedAfterWalk, paidSource, java.util.Set.of());
			check(resumed.available() && resumed.departureAnchorId().equals(origin.id()), "A saved Party at the native Gatekeeper lost its lawful connector on restart");
			check(!planner.rendezvousItinerary(storedAfterWalk, beyondWallet, java.util.Set.of()).available(), "Gatekeeper ingress bypassed the Party wallet");
			check(planner.replaceFromState(92, state, goal, 128, 1).goal().selectedAnchor().key().equals(distant.id()), "Positive control must prefer the unaffordable exact-level farm");
			final var selected = planner.replaceAffordableFromState(92, state, goal, 128, 1);
			check(selected.ready() && selected.goal().selectedAnchor().key().equals(distant.id()), "Free Phantom pricing still excluded the preferred native route");
			final var absent = planner.replaceAffordableFromState(92, empty, goal, 128, 1);
			check(absent.ready() && absent.goal().selectedAnchor().key().equals(distant.id()), "An empty Phantom wallet still blocked a free journey");
			check(PhantomBackgroundGoalSpec.canonicalHistoricalIdle(92, planner.idleFromState(92, empty, goal, 128, 1).goal()), "No affordable route lost canonical idle eligibility");
			check(state.inventory().itemCount(57) == wallet && empty.inventory().itemCount(57) == 0 && !DatabaseFactory.isInitialized(), "Pure planning changed funds or initialized JDBC");
			System.out.println("PASS128 Phantom routes/reserves are free with an empty wallet; ordinary native fees and immutable funds/JDBC remain unchanged");
		}
		finally { Files.delete(catalog); }
	}
}
