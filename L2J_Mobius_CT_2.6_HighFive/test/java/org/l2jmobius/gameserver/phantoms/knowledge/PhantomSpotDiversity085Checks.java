/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.knowledge;

import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.phantoms.background.*;
import org.l2jmobius.gameserver.phantoms.decision.*;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.*;
import org.l2jmobius.gameserver.phantoms.topology.*;
import org.l2jmobius.tests.phantoms.PhantomAssertions;

/** Immutable virtual selections only: no Player construction, database, worker or gameplay. */
public final class PhantomSpotDiversity085Checks
{
	private static final PhantomBackgroundState.Hashes HASHES = new PhantomBackgroundState.Hashes("knowledge085", "topology085", "progression085", "commerce085");
	private static final long SEED = 850085;
	// Frozen before GREEN: four comparable independent anchors; legacy winner is farm.1.
	private static final List<Spot> COMPARABLE = List.of(
		new Spot(101, 20, 1, 1, 10000, 8, 100, true, true, true),
		new Spot(102, 21, 2, 1, 10000, 4, 90, true, true, true),
		new Spot(103, 19, 3, 1, 15000, 2, 80, true, true, true),
		new Spot(104, 20, 4, 2, 20000, 4, 95, true, true, true));

	public static void main(String[] args) throws Exception
	{
		if (args.length > 0 && args[0].equals("--identity"))
		{
			System.out.println("IDENTITY085=" + identity());
			return;
		}
		final boolean baseline = args.length > 0 && args[0].equals("--baseline");
		for (boolean shared : List.of(true, false))
		{
			for (int count : List.of(500, 1000, 5000))
			{
				final var histogram = measure(fixture(COMPARABLE, false, 20), count, shared);
				if (!baseline && count >= 1000)
				{
					PhantomAssertions.assertTrue(histogram.size() >= 3 && histogram.values().stream().mapToInt(Integer::intValue).max().orElseThrow() <= count * 0.60,
						"085-a RED: comparable legal anchors must reach >=3 geographies and top1 <=60%; " + histogram);
				}
			}
		}
		if (baseline) { return; }
		stability();
		eligibility();
		localAndReplan();
		competition();
		System.out.println("085-a/b/c/d/e PASS; IDENTITY085=" + identity() + "; DB_WRITES=0; NATIVE_PLAYERS=0");
	}

	private static Map<String, Integer> measure(Fixture fixture, int count, boolean shared)
	{
		for (int index = 1; index <= 100; index++) { fixture.plan(index, seed(index, shared)); }
		final var histogram = new TreeMap<String, Integer>();
		final long[] nanos = new long[count];
		final var bean = ManagementFactory.getThreadMXBean();
		final long cpuBefore = bean.getCurrentThreadCpuTime();
		final long heapBefore = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
		final long start = System.nanoTime();
		for (int index = 1; index <= count; index++)
		{
			final long before = System.nanoTime();
			final var result = fixture.plan(index, seed(index, shared));
			nanos[index - 1] = System.nanoTime() - before;
			PhantomAssertions.assertTrue(result.ready(), "Comparable fixture must be eligible.");
			histogram.merge(result.spec().anchorId(), 1, Integer::sum);
		}
		final long wall = System.nanoTime() - start;
		final long cpu = bean.getCurrentThreadCpuTime() - cpuBefore;
		final long heapAfter = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
		java.util.Arrays.sort(nanos);
		System.out.println("085-e count=" + count + " seed=" + (shared ? "shared" : "ecology-shaped") + " level=20 ingress=ingress candidates=4 anchors=" + histogram.size()
			+ " nodes=" + histogram.size() + " regions=" + histogram.size() + " histogram=" + histogram + " top1=" + (histogram.values().stream().mapToInt(Integer::intValue).max().orElseThrow() / (double) count)
			+ " wallNs=" + wall + " cpuNs=" + cpu + " p50Ns=" + nanos[(count - 1) / 2] + " p95Ns=" + nanos[(int) Math.ceil(count * 0.95) - 1]
			+ " heapBefore=" + heapBefore + " heapAfter=" + heapAfter + "; virtual selections, fixture regions, not World presence");
		return histogram;
	}

	// Same mixing formula as ecology; declared virtual generation/join inputs, not persisted profiles.
	private static long seed(long profile, boolean shared)
	{
		if (shared) { return SEED; }
		long mixed = (85L ^ profile) ^ (29_880_000L + 0x9E3779B97F4A7C15L);
		mixed = (mixed ^ (mixed >>> 30)) * 0xBF58476D1CE4E5B9L;
		mixed = (mixed ^ (mixed >>> 27)) * 0x94D049BB133111EBL;
		return Math.max(1, (mixed ^ (mixed >>> 31)) & Long.MAX_VALUE);
	}

	private static String identity() throws Exception
	{
		final var fixture = fixture(COMPARABLE, false, 20);
		final var digest = MessageDigest.getInstance("SHA-256");
		for (int id = 1; id <= 100; id++)
		{
			final var result = fixture.plan(id, SEED);
			digest.update((result.planIdentity() + ":" + result.goal().goalId() + ":" + result.goal().revision() + "\n").getBytes(StandardCharsets.UTF_8));
		}
		return HexFormat.of().formatHex(digest.digest());
	}

	private static void stability()
	{
		final var first = fixture(COMPARABLE, false, 20);
		final var reordered = fixture(COMPARABLE, true, 20);
		final var duplicates = new ArrayList<>(COMPARABLE);
		duplicates.add(new Spot(105, 20, 1, 1, 10000, 8, 100, true, true, true));
		final var sameGeography = fixture(duplicates, false, 20);
		for (int id = 1; id <= 200; id++)
		{
			final var selected = first.plan(id, SEED);
			PhantomAssertions.assertEquals(selected, first.plan(id, SEED), "Repeat reranked saved input.");
			PhantomAssertions.assertEquals(selected, reordered.plan(id, SEED), "Recreated/shuffled source changed plan identity.");
			PhantomAssertions.assertEquals(selected.spec().anchorId(), sameGeography.plan(id, SEED).spec().anchorId(), "More NPC IDs at one anchor must not buy more geography lottery tickets.");
			PhantomAssertions.assertFalse(selected.planIdentity().equals(sameGeography.plan(id, SEED).planIdentity()), "Plan identity lost its changed source hash.");
			final var excluded = Set.of(selected.spec().npcId() + "@" + selected.spec().anchorId());
			final var next = first.planner.replan(id, state(20, 100), selected.goal(), SEED, 1, excluded, Set.of());
			PhantomAssertions.assertTrue(next.ready() && !next.spec().anchorId().equals(selected.spec().anchorId()), "Excluded current target must yield another lawful source.");
			PhantomAssertions.assertEquals(selected.goal().goalId(), next.goal().goalId(), "Causal replan changed persisted root.");
			PhantomAssertions.assertEquals(1L, next.goal().revision(), "Causal replan must increment revision once.");
			PhantomAssertions.assertTrue(first.planner.remainsSuitable(state(20, 100, first.planner.generation().authorityHashes()), selected.goal()), "Saved active plan must remain suitable without reranking.");
		}
	}

	private static void eligibility()
	{
		final var spots = new ArrayList<>(COMPARABLE);
		spots.add(new Spot(201, 23, 5, 1, 10000, 100, 1000, true, true, true)); // outside +2
		spots.add(new Spot(202, 20, 6, 1, 10000, 100, 1000, false, true, true)); // no route
		spots.add(new Spot(203, 20, 7, 1, 10000, 100, 1000, true, false, true)); // no spawn
		spots.add(new Spot(204, 20, 8, 1, 10000, 100, 1000, true, true, false)); // native viability
		spots.add(new Spot(205, 20, 9, 1, 3_600_000, 100, 1000, true, true, true)); // excessive duration
		spots.add(new Spot(206, 20, 10, 1, 10000, 100, 1, true, true, true)); // poor reward
		final var fixture = fixture(spots, false, 20);
		for (int id = 1; id <= 1000; id++)
		{
			PhantomAssertions.assertTrue(fixture.plan(id, SEED).spec().npcId() < 200, "085-b ineligible/uncomparable source selected.");
		}
		final var one = fixture(List.of(COMPARABLE.getFirst()), false, 20);
		for (int id = 1; id <= 100; id++) { PhantomAssertions.assertEquals("farm.1", one.plan(id, SEED).spec().anchorId(), "One-candidate legacy choice changed."); }
		final var goal = one.plan(85, SEED).goal();
		PhantomAssertions.assertFalse(one.planner.replan(85, state(20, 100), goal, SEED, 1, Set.of(), Set.of("edge.1")).ready(), "Blocked route reused.");
		PhantomAssertions.assertFalse(one.planner.replan(85, state(20, 0), goal, SEED, 1).ready(), "Dead/negative combat passed viability.");
		PhantomAssertions.assertFalse(fixture(COMPARABLE, false, 85).plan(85, SEED).ready(), "Unknown high-level must stay unavailable.");
		final var low = new Spot(301, 10, 1, 1, 10000, 1, 10, true, true, true);
		PhantomAssertions.assertEquals(301, fixture(List.of(low), false, 20).plan(85, SEED).spec().npcId(), "Lower -10 fallback lost.");
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> one.planner.replan(85, state(20, 100), corruptGoal(), SEED, 1), "Corrupt goal accepted.");
		final var pages = new ArrayList<Spot>();
		for (int index = 0; index < 1856; index++) { pages.add(new Spot(1000 + index, 20, 1, 1, 10000, 1, 100, true, false, true)); }
		pages.add(new Spot(3000, 20, 1, 1, 10000, 1, 100, true, true, true));
		PhantomAssertions.assertFalse(fixture(pages, false, 20).plan(85, SEED).ready(), "Planner exceeded 8-page (64+7*256) ceiling.");
	}

	private static void localAndReplan()
	{
		final var fixture = fixture(COMPARABLE, false, 20);
		final var prior = fixture.plan(85, SEED).goal();
		final var before = state(20, 100);
		for (int id = 1; id <= 100; id++)
		{
			final var local = fixture.planner.replanVisibleLocal(id, before, prior, SEED, 1, point(1), Set.of(), Set.of());
			PhantomAssertions.assertEquals("farm.1", local.spec().anchorId(), "085-c live local-first lost to global preference.");
			PhantomAssertions.assertTrue(fixture.planner.isVisibleLocal(local.goal(), point(1)), "Local 2km area guard lost.");
		}
		PhantomAssertions.assertEquals(before, state(20, 100), "Planning mutated state/receipts/rewards.");
	}

	private static void competition()
	{
		final var registry = new PhantomBackgroundCompetitionRegistry();
		final var leases = new ArrayList<PhantomBackgroundCompetitionRegistry.Reservation>();
		for (int index = 0; index < 32; index++) { leases.add(java.util.Objects.requireNonNull(registry.tryReserve("area.1", 101, 100))); }
		PhantomAssertions.assertEquals(null, registry.tryReserve("area.1", 101, 100), "Operation cap32 lost.");
		leases.forEach(lease -> { lease.close(); lease.close(); });
		PhantomAssertions.assertEquals(0, registry.currentReservations(), "Operation leases leaked.");
		try (var next = registry.tryReserve("area.1", 101, 1)) { PhantomAssertions.assertTrue(next != null, "Released operation became permanent occupancy."); }
	}

	private record Spot(int npc, int level, int geography, int legs, long millis, int amount, double exp, boolean linked, boolean spawn, boolean viable) { }
	private record Fixture(PhantomHistoricalBackgroundPlanner planner)
	{
		PhantomHistoricalBackgroundPlanner.Result plan(long id, long seed) { return planner.planInitial(id, null, seed, 0); }
	}

	private static Fixture fixture(List<Spot> spots, boolean reverse, int level)
	{
		final var backend = new PhantomTopologyValidationBackend()
		{
			@Override public int mapRegionLocId(int x, int y) { return Math.max(1, 1 + x / 10000); }
			@Override public Optional<PhantomTopologyValidationBackend.NpcFact> npc(int id) { return Optional.empty(); }
			@Override public List<PhantomTopologyValidationBackend.SpawnFact> spawns(int id, int bound) { return List.of(); }
			@Override public Optional<DoorFact> door(int id) { return Optional.empty(); }
			@Override public DoorState doorState(int id) { return DoorState.MISSING; }
			@Override public boolean sourceExists(String source) { return source.equals("data/fixture085.xml"); }
		};
		final var nodes = new ArrayList<PhantomTopologyNode>();
		final var anchors = new ArrayList<PhantomTopologyAnchor>();
		final var edges = new ArrayList<PhantomTopologyEdge>();
		nodes.add(node(0));
		anchors.add(new PhantomTopologyAnchor("ingress", PhantomTopologyAnchorRole.CITY_CENTER, "area.0", point(0), null, null, 0, List.of(), List.of()));
		for (Spot spot : spots.stream().collect(java.util.stream.Collectors.toMap(Spot::geography, value -> value, (a, b) -> a, TreeMap::new)).values())
		{
			final int geo = spot.geography();
			nodes.add(node(geo));
			anchors.add(new PhantomTopologyAnchor("farm." + geo, PhantomTopologyAnchorRole.FARMING, "area." + geo, point(geo), null, geo, 0, List.of(), List.of("data/fixture085.xml")));
			if (spot.linked())
			{
				if (spot.legs() == 2)
				{
					final int via = 100 + geo;
					nodes.add(node(via));
					anchors.add(new PhantomTopologyAnchor("farm." + via, PhantomTopologyAnchorRole.ROUTE, "area." + via, point(via), null, null, 0, List.of(), List.of()));
					edges.add(edge("edge." + geo + ".via", 0, via, spot.millis() / 2));
					edges.add(edge("edge." + geo, via, geo, spot.millis() / 2));
				}
				else { edges.add(edge("edge." + geo, 0, geo, spot.millis())); }
			}
		}
		if (reverse) { Collections.reverse(nodes); Collections.reverse(anchors); Collections.reverse(edges); }
		final var topology = new PhantomTopologyQuery(PhantomTopologySnapshot.create(1, "fixture085", 1, 1, nodes, anchors, edges, backend, PhantomTopologyPolicy.productionDefaults()), backend, new PhantomTopologyMetrics());
		final var npcs = new ArrayList<NpcFact>();
		final var areas = new ArrayList<SpawnAreaFact>();
		for (Spot spot : spots)
		{
			npcs.add(new NpcFact(spot.npc(), spot.level(), NpcKind.MONSTER, true, true, false, spot.exp(), spot.exp() / 10, PhantomGameKnowledgeAuthority.SERVER_LOADER_FACT));
			if (spot.spawn()) { areas.add(new SpawnAreaFact(spot.npc(), 0, "area." + spot.geography(), spot.geography(), 1, spot.amount(), List.of(), PhantomGameKnowledgeAuthority.TOPOLOGY_SNAPSHOT_FACT)); }
		}
		if (reverse) { Collections.reverse(npcs); Collections.reverse(areas); }
		final var snapshot = new PhantomGameKnowledgeSnapshot("fixture085", 1, topology.snapshot().canonicalHash(), PhantomGameKnowledgePolicy.productionDefaults(), List.of(), npcs, List.of(), List.of(), areas, List.of(), List.of(), List.of(), List.of(), List.of());
		final var travel = PhantomNormalGatekeeperTravel.empty(topology);
		final var hashes = new PhantomBackgroundState.Hashes(snapshot.combinedHash(), topology.snapshot().canonicalHash(), "progression085", "commerce085");
		final var authority = new PhantomBackgroundAuthority()
		{
			@Override public PhantomBackgroundState.Hashes hashes() { return hashes; }
			@Override public PlanningSnapshot planningSnapshot(Player player) { return new PlanningSnapshot(level, 0, "ingress", 0, 0, 0, 0, 0); }
			@Override public Optional<PlanningCombat> planningCombat(Player player) { return Optional.of(PlanningCombat.from(state(level, 100))); }
			@Override public boolean viableTarget(PlanningCombat combat, int npc) { return combat.vitals().currentHp() > 0 && spots.stream().anyMatch(spot -> spot.npc() == npc && spot.viable()); }
			@Override public PhantomNormalGatekeeperTravel travelQuery(PhantomTopologyQuery ignored) { return travel; }
			@Override public PhantomBackgroundState capture(long id, Player player, PhantomGoal goal, PhantomBackgroundState previous) { throw new UnsupportedOperationException(); }
			@Override public boolean matchesRuntime(Player player, PhantomBackgroundState state) { return false; }
			@Override public FarmInput farmInput(PhantomBackgroundState state, PhantomBackgroundGoalSpec goal) { throw new UnsupportedOperationException(); }
			@Override public TravelAdvance advanceTravel(PhantomBackgroundState state, PhantomBackgroundGoalSpec goal, long budget) { throw new UnsupportedOperationException(); }
			@Override public Optional<PhantomBackgroundState.Position> canonicalRecoveryPosition(int x, int y, int z, int instance, int heading) { return Optional.empty(); }
			@Override public List<PhantomBackgroundState.AutoGetSkill> autoGetSkills(PhantomBackgroundState.Identity identity, int currentLevel) { return List.of(); }
		};
		return new Fixture(new PhantomHistoricalBackgroundPlanner(new PhantomGameKnowledgeQuery(snapshot, new PhantomGameKnowledgeMetrics()), topology, authority));
	}

	private static PhantomTopologyPoint point(int index) { return new PhantomTopologyPoint(index == 0 ? 0 : 100 + ((index >= 100 ? index - 100 : index) - 1) * 10000, index >= 100 ? 10100 : 100, 0, 0); }
	private static PhantomTopologyNode node(int index)
	{
		final var point = point(index);
		return new PhantomTopologyNode("area." + index, PhantomTopologyNodeKind.FARMING_AREA, 0, PhantomTopologyArea.polygon(0, -100, 100, List.of(new PhantomTopologyArea.Vertex(point.x() - 50, point.y() - 50), new PhantomTopologyArea.Vertex(point.x() + 50, point.y() - 50), new PhantomTopologyArea.Vertex(point.x() + 50, point.y() + 50), new PhantomTopologyArea.Vertex(point.x() - 50, point.y() + 50))), null, List.of(), List.of("data/fixture085.xml"));
	}
	private static PhantomTopologyEdge edge(String id, int from, int to, long millis)
	{
		return new PhantomTopologyEdge(id, "area." + from, "area." + to, PhantomTopologyEdgeMode.BACKGROUND, true, 1, millis, true, Set.of(), null, from == 0 ? "ingress" : "farm." + from, "farm." + to, List.of());
	}
	private static PhantomGoal corruptGoal()
	{
		return new PhantomGoal(850085, "farm.background", PhantomGoalStatus.ACTIVE, new PhantomDomainRef("profile", "85"), new PhantomDomainRef("npc", "101"), 1, 0, "background.farm", List.of(), new PhantomDomainRef("topology.anchor", "farm.1"), "farm.background", 500, 0, 0, 0, Map.of(), "fixture085", 0);
	}
	private static PhantomBackgroundState state(int level, double hp)
	{
		return state(level, hp, HASHES);
	}
	private static PhantomBackgroundState state(int level, double hp, PhantomBackgroundState.Hashes hashes)
	{
		return new PhantomBackgroundState(PhantomBackgroundState.State.MATERIALIZED, new PhantomBackgroundState.Identity(85, 850085, 0, 1, 0), new PhantomBackgroundState.Progress(level, 0, 0, 0), new PhantomBackgroundState.Vitals(hp, 100, 100, 100, 100, 100), new PhantomBackgroundState.Position(0, 0, 100, 0, 0, "ingress"), new PhantomBackgroundState.CombatFacts(PhantomBackgroundState.ModelKind.MELEE, 100, 100, 100, 100, 500, 500, 1, 1, 1, 1, 0, 1, 1, 1, 1), PhantomBackgroundState.Loadout.none(), new PhantomBackgroundState.InventoryFacts(List.of(), List.of(), "fixture085", 0, 1000, 0, 100), List.of(), new PhantomBackgroundState.Clock(SEED, 0, 0), PhantomBackgroundState.Receipt.empty(), hashes);
	}
}
