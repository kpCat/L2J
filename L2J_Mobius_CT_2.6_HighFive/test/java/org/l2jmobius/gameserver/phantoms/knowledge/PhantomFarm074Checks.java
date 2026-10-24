/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.knowledge;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.phantoms.background.*;
import org.l2jmobius.gameserver.phantoms.decision.*;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.*;
import org.l2jmobius.gameserver.phantoms.topology.*;
import org.l2jmobius.tests.phantoms.PhantomAssertions;

/** Small immutable planner fixture: no Player, DB, scheduler, movement or rewards. */
public final class PhantomFarm074Checks
{
	private static final PhantomTopologyPoint LOCAL = new PhantomTopologyPoint(100, 100, 0, 0);
	private static final PhantomTopologyPoint FAR = new PhantomTopologyPoint(10100, 100, 0, 0);
	private static final PhantomTopologyPoint FAR_ALTERNATIVE = new PhantomTopologyPoint(20100, 100, 0, 0);
	private static final PhantomBackgroundState.Hashes HASHES = new PhantomBackgroundState.Hashes("knowledge074", "topology074", "progression074", "commerce074");

	public static void main(String[] args) throws Exception
	{
		final java.lang.reflect.Method admittedReplan;
		try { admittedReplan = PhantomHistoricalBackgroundPlanner.class.getMethod("replanVisibleLocal", long.class, PhantomBackgroundState.class, PhantomGoal.class, long.class, long.class, PhantomTopologyPoint.class, Set.class, Set.class, java.util.function.Predicate.class); }
		catch (NoSuchMethodException missing) { throw new AssertionError("RED: rejected preferred distant route must not starve an admissible alternative.", missing); }
		final var planner = planner(true);
		final var state = state();
		final var previous = goal();
		final var local = planner.replanVisibleLocal(74, state, previous, 740074, 1, LOCAL, Set.of(), Set.of());
		PhantomAssertions.assertTrue(local.ready() && local.spec().anchorId().equals("farm.local"), "Available local rewarded farm remains first.");
		final var excluded = Set.of("101@farm.local");
		final var fallback = planner.replanVisibleLocal(74, state, previous, 740074, 1, LOCAL, excluded, Set.of());
		PhantomAssertions.assertTrue(fallback.ready(), "RED: exhausted local area must consider the existing distant topology source.");
		PhantomAssertions.assertEquals("farm.far", fallback.spec().anchorId(), "Fallback selected an excluded local source.");
		PhantomAssertions.assertEquals(102, fallback.spec().npcId(), "Fallback must select the other rewarded NPC.");
		PhantomAssertions.assertEquals(List.of("farm.link"), fallback.routeEdgeIds(), "Remote admission needs its canonical itinerary.");
		PhantomAssertions.assertEquals(previous.goalId(), fallback.goal().goalId(), "Farm root identity changed.");
		PhantomAssertions.assertEquals(previous.revision() + 1, fallback.goal().revision(), "Replacement revision must advance exactly once.");
		PhantomAssertions.assertEquals(fallback, planner.replanVisibleLocal(74, state, previous, 740074, 1, LOCAL, excluded, Set.of()), "Same persisted inputs must survive restart deterministically.");
		System.out.println("FARM074_FALLBACK_PASS; distance=10000; canonical itinerary; same root; deterministic revision; DB_WRITES=0");

		PhantomAssertions.assertFalse(planner.replanVisibleLocal(74, state, previous, 740074, 1, LOCAL, excluded, Set.of("farm.link")).ready(), "Excluded route must not be reused.");
		PhantomAssertions.assertFalse(planner(false).replanVisibleLocal(74, state, previous, 740074, 1, LOCAL, excluded, Set.of()).ready(), "No topology path must remain unavailable; no invented transport.");
		PhantomAssertions.assertFalse(planner.replanVisibleLocal(74, state, previous, 740074, 1, new PhantomTopologyPoint(100, 100, 0, 1), excluded, Set.of()).ready(), "Instance mismatch cannot promote a world-map itinerary.");
		System.out.println("FARM074_FENCES_PASS; excluded target/edge; disconnected remote; DB_WRITES=0");

		final var alternatives = planner(true, true);
		final var preferred = alternatives.replanVisibleLocal(74, state, previous, 740074, 1, LOCAL, excluded, Set.of());
		final var inspections = new java.util.concurrent.atomic.AtomicInteger();
		final java.util.function.Predicate<PhantomGoal> permits = candidate ->
		{ inspections.incrementAndGet(); return !candidate.selectedAnchor().equals(preferred.goal().selectedAnchor()); };
		final var admitted = (PhantomHistoricalBackgroundPlanner.Result) admittedReplan.invoke(alternatives, 74L, state, previous, 740074L, 1L, LOCAL, excluded, Set.of(), permits);
		PhantomAssertions.assertTrue(admitted.ready() && !admitted.spec().anchorId().equals(preferred.spec().anchorId()), "Unaffordable/unusable preferred source starved the admissible distant alternative.");
		PhantomAssertions.assertEquals(2, inspections.get(), "Admission did not inspect exactly the rejected preferred and accepted alternative.");
		inspections.set(0);
		final java.util.function.Predicate<PhantomGoal> denyAll = candidate -> { inspections.incrementAndGet(); return false; };
		final var rejected = (PhantomHistoricalBackgroundPlanner.Result) admittedReplan.invoke(alternatives, 74L, state, previous, 740074L, 1L, LOCAL, excluded, Set.of(), denyAll);
		PhantomAssertions.assertFalse(rejected.ready(), "No admitted remote source must remain unavailable.");
		PhantomAssertions.assertTrue(inspections.get() <= 3, "Remote admission exceeded the existing bounded retry budget.");
		System.out.println("FARM074_ADMISSION_PASS; rejected preferred source skipped; affordable alternative selected; max3; DB_WRITES=0");
	}

	private static PhantomHistoricalBackgroundPlanner planner(boolean linked)
	{
		return planner(linked, false);
	}
	private static PhantomHistoricalBackgroundPlanner planner(boolean linked, boolean alternative)
	{
		final var backend = new PhantomTopologyValidationBackend()
		{
			@Override public int mapRegionLocId(int x, int y) { return 1; }
			@Override public Optional<PhantomTopologyValidationBackend.NpcFact> npc(int id) { return Optional.of(new PhantomTopologyValidationBackend.NpcFact(id, "Monster", true)); }
			@Override public List<PhantomTopologyValidationBackend.SpawnFact> spawns(int id, int bound) { return List.of(new PhantomTopologyValidationBackend.SpawnFact(id, id == 101 ? LOCAL : id == 102 ? FAR : FAR_ALTERNATIVE)); }
			@Override public Optional<DoorFact> door(int id) { return Optional.empty(); }
			@Override public DoorState doorState(int id) { return DoorState.MISSING; }
			@Override public boolean sourceExists(String source) { return true; }
		};
		final var nodes = new java.util.ArrayList<>(List.of(node("area.local", LOCAL), node("area.far", FAR)));
		final var anchors = new java.util.ArrayList<>(List.of(anchor("farm.local", "area.local", LOCAL, 101), anchor("farm.far", "area.far", FAR, 102)));
		final var edges = new java.util.ArrayList<>(linked ? List.of(new PhantomTopologyEdge("farm.link", "area.local", "area.far", PhantomTopologyEdgeMode.BACKGROUND, true, 1, 100000, true, Set.of(), null, "farm.local", "farm.far", List.of())) : List.<PhantomTopologyEdge>of());
		if (alternative)
		{
			nodes.add(node("area.alternative", FAR_ALTERNATIVE)); anchors.add(anchor("farm.alternative", "area.alternative", FAR_ALTERNATIVE, 103));
			edges.add(new PhantomTopologyEdge("farm.alternative.link", "area.local", "area.alternative", PhantomTopologyEdgeMode.BACKGROUND, true, 1, 110000, true, Set.of(), null, "farm.local", "farm.alternative", List.of()));
		}
		final var topology = new PhantomTopologyQuery(PhantomTopologySnapshot.create(1, "farm074", 1, 1, nodes, anchors, edges, backend, PhantomTopologyPolicy.productionDefaults()), backend, new PhantomTopologyMetrics());
		final var npcs = new java.util.ArrayList<>(List.of(new NpcFact(101, 20, NpcKind.MONSTER, true, true, false, 100, 10, PhantomGameKnowledgeAuthority.SERVER_LOADER_FACT), new NpcFact(102, 22, NpcKind.MONSTER, true, true, false, 200, 20, PhantomGameKnowledgeAuthority.SERVER_LOADER_FACT)));
		final var areas = new java.util.ArrayList<>(List.of(new SpawnAreaFact(101, 0, "area.local", 1, 1, 1, List.of(), PhantomGameKnowledgeAuthority.TOPOLOGY_SNAPSHOT_FACT), new SpawnAreaFact(102, 0, "area.far", 1, 1, 1, List.of(), PhantomGameKnowledgeAuthority.TOPOLOGY_SNAPSHOT_FACT)));
		if (alternative)
		{
			npcs.add(new NpcFact(103, 21, NpcKind.MONSTER, true, true, false, 150, 15, PhantomGameKnowledgeAuthority.SERVER_LOADER_FACT));
			areas.add(new SpawnAreaFact(103, 0, "area.alternative", 1, 1, 1, List.of(), PhantomGameKnowledgeAuthority.TOPOLOGY_SNAPSHOT_FACT));
		}
		final var snapshot = new PhantomGameKnowledgeSnapshot("farm074", 1, topology.snapshot().canonicalHash(), PhantomGameKnowledgePolicy.productionDefaults(), List.of(), npcs, List.of(), List.of(), areas, List.of(), List.of(), List.of(), List.of(), List.of());
		final var knowledge = new PhantomGameKnowledgeQuery(snapshot, new PhantomGameKnowledgeMetrics());
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
		return new PhantomHistoricalBackgroundPlanner(knowledge, topology, authority);
	}

	private static PhantomTopologyNode node(String id, PhantomTopologyPoint point)
	{
		return new PhantomTopologyNode(id, PhantomTopologyNodeKind.FARMING_AREA, 0, PhantomTopologyArea.cuboid(0, point.x() - 50, point.x() + 50, point.y() - 50, point.y() + 50, -100, 100), null, List.of(), List.of());
	}
	private static PhantomTopologyAnchor anchor(String id, String node, PhantomTopologyPoint point, int npc)
	{
		return new PhantomTopologyAnchor(id, PhantomTopologyAnchorRole.FARMING, node, point, npc, null, 0, List.of(), List.of());
	}
	private static PhantomGoal goal()
	{
		return new PhantomGoal(740074, "farm.background", PhantomGoalStatus.ACTIVE, new PhantomDomainRef("profile", "74"), new PhantomDomainRef("npc", "101"), 1, 0, "background.farm", List.of(new PhantomDomainRef("background.farm", "101@farm.local")), new PhantomDomainRef("topology.anchor", "farm.local"), "farm.background", 500, 0, 0, 0, Map.of(), "fixture074", 1);
	}
	private static PhantomBackgroundState state()
	{
		return new PhantomBackgroundState(PhantomBackgroundState.State.MATERIALIZED, new PhantomBackgroundState.Identity(74, 740074, 0, 1, 0), new PhantomBackgroundState.Progress(20, 0, 0, 0), new PhantomBackgroundState.Vitals(100, 100, 100, 100, 100, 100), new PhantomBackgroundState.Position(0, 100, 100, 0, 0, "farm.local"), new PhantomBackgroundState.CombatFacts(PhantomBackgroundState.ModelKind.MELEE, 100, 100, 100, 100, 500, 500, 1, 1, 1, 1, 0, 1, 1, 1, 1), PhantomBackgroundState.Loadout.none(), new PhantomBackgroundState.InventoryFacts(List.of(), List.of(), "fixture074", 0, 1000, 0, 100), List.of(), new PhantomBackgroundState.Clock(740074, 0, 0), PhantomBackgroundState.Receipt.empty(), HASHES);
	}
}
