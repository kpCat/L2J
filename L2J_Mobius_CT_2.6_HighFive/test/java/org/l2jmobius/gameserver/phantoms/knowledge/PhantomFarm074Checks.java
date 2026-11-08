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
		if (args.length == 1 && "--route-matrix129".equals(args[0])) { routeMatrix129(); return; }
		sourcePreflight129();
		rendezvous();
		supportRendezvous();
		historicalFailedRoute();
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

	private static void sourcePreflight129()
	{
		final var planner = planner(true, true); final var before = state(); final var previous = goal();
		final var sources = planner.rendezvousSources(74, before, previous, 740074, 1, Set.of(), Set.of());
		PhantomAssertions.assertTrue(sources.size() <= 64 && sources.stream().distinct().count() == sources.size(), "Source preview exceeded its finite distinct-source bound.");
		PhantomAssertions.assertTrue(sources.contains(new PhantomDomainRef("background.farm", "103@farm.alternative")), "Existing ranking/exclusion loop starved the connected alternative.");
		PhantomAssertions.assertTrue(sources.stream().allMatch(source -> planner.rendezvousRouteAvailable(before, source, Set.of())), "Preview produced a source outside the existing certified graph.");
		PhantomAssertions.assertFalse(planner(false).rendezvousRouteAvailable(before, new PhantomDomainRef("background.farm", "102@farm.far"), Set.of()), "Disconnected source acquired an invented itinerary.");
		PhantomAssertions.assertFalse(planner(false).rendezvousRouteAbsent(before, new PhantomDomainRef("background.farm", "102@farm.far")), "Graph-only absence cannot prove a native WALK impossible.");
		final var ingress = planner(false, true, true, true);
		final var far = new PhantomDomainRef("background.farm", "103@farm.alternative");
		PhantomAssertions.assertEquals(PhantomHistoricalBackgroundPlanner.RendezvousRoute.REACHABLE_VIA_INGRESS, ingress.rendezvousRoutePreview(before, far, Set.of()), "Composite preview lost the lawful WALK ingress.");
		final var proposal = ingress.replanVisibleRendezvous(74, before, previous, 740074, 1, LOCAL, far, Set.of(), _ -> true);
		PhantomAssertions.assertTrue(proposal.ready(), "Ingress preview and native goal adoption disagree.");
		PhantomAssertions.assertEquals(List.of("farm.alternative.link"), proposal.routeEdgeIds(), "WALK ingress invented an offscreen edge or lost the canonical onward route.");
		PhantomAssertions.assertFalse(planner.rendezvousRouteAbsent(before, new PhantomDomainRef("background.farm", "102@missing")), "Unknown source became a terminal route refusal.");
		PhantomAssertions.assertFalse(planner.rendezvousRouteAvailable(before, new PhantomDomainRef("background.farm", "103@farm.alternative"), Set.of("farm.alternative.link")), "Preview waived a current native route exclusion.");
		PhantomAssertions.assertEquals(before, state(), "Preview mutated earned state or native position.");
		final var healer = new PhantomBackgroundState(before.state(), new PhantomBackgroundState.Identity(74, 740074, 0, 16, 0), before.progress(), before.vitals(), before.position(), before.combat(), before.loadout(), before.inventory(), before.autoGetSkills(), before.clock(), before.receipt(), before.hashes());
		PhantomAssertions.assertTrue(planner(true, true, false).rendezvousSources(74, before, previous, 740074, 1, Set.of(), Set.of()).stream().findAny().isPresent(), "An explicit Party source still required an individual Solo victory.");
		PhantomAssertions.assertFalse(planner(true, true, false).replan(74, before, previous, 740074, 1).ready(), "Ordinary Solo admission bypassed individual combat feasibility.");
		PhantomAssertions.assertTrue(planner(true, true, false).rendezvousSources(74, healer, previous, 740074, 1, Set.of(), Set.of()).contains(new PhantomDomainRef("background.farm", "103@farm.alternative")), "Healer travel preview incorrectly required Solo DD feasibility.");
		final var common = planner.replanLogicalRendezvous(74, before, previous, 740074, 1, new PhantomDomainRef("background.farm", "103@farm.alternative"));
		PhantomAssertions.assertTrue(common.ready() && common.spec().anchorId().equals("farm.alternative"), "Logical Background silently preferred a different Solo source.");
		final var shared = new PhantomDomainRef("background.farm", "103@farm.alternative");
		PhantomAssertions.assertFalse(planner.replanLogicalRendezvous(74,before,previous,740074,1,shared,Set.of(shared.key()),Set.of(),false).ready(), "Logical recovery ignored an excluded shared target.");
		PhantomAssertions.assertFalse(planner.replanLogicalRendezvous(74,before,previous,740074,1,shared,Set.of(),Set.of("farm.alternative.link"),false).ready(), "Logical recovery immediately reselected its proven bad leg.");
		PhantomAssertions.assertFalse(ingress.replanLogicalRendezvous(74, before, previous, 740074, 1, far).ready(), "Native WALK ingress was invented as an offscreen edge.");
		PhantomAssertions.assertFalse(ingress.replanLogicalRendezvous(74, before, previous, 740074, 1, far, Set.of(), Set.of(), true).ready(), "Affordable logical recovery enabled an unrelated offscreen travel proposal.");
		System.out.println("ROUTE130_PLANNER_PREVIEW_PASS; bounded64; composite ingress proposal; common logical source; no offscreen native ingress; no earned-state writes");
	}

	/** Source graph proof only. Factual loader leaves are modeled; native/Geo admission is not exercised. */
	private static void routeMatrix129() throws Exception { sourceRoutes129(true); }
	/** Actual source graph/catalog with explicitly modeled factual-validation leaves, not native usability proof. */
	public static PhantomNormalGatekeeperTravel sourceRoutes129() throws Exception { return sourceRoutes129(false); }
	private static PhantomNormalGatekeeperTravel sourceRoutes129(boolean report) throws Exception
	{
		final var module = java.nio.file.Path.of(System.getProperty("phantom.module.root", ".")).toAbsolutePath().normalize();
		final var gameRoot = java.nio.file.Files.isDirectory(module.resolve("dist/game/data")) ? module.resolve("dist/game") : module;
		final var directory = gameRoot.resolve("data/phantoms/topology");
		final var nodes = new java.util.HashMap<String, org.w3c.dom.Element>();
		final var anchors = new java.util.HashMap<String, org.w3c.dom.Element>();
		final var edges = new java.util.ArrayList<org.w3c.dom.Element>();
		final var digest = java.security.MessageDigest.getInstance("SHA-256");
		final var factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
		factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
		factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
		factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
		final List<java.nio.file.Path> files;
		try (var paths = java.nio.file.Files.list(directory)) { files = paths.filter(p -> p.getFileName().toString().endsWith(".xml")).sorted().toList(); }
		for (var file : files)
		{
			final var bytes = java.nio.file.Files.readAllBytes(file);
			digest.update(file.getFileName().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)); digest.update((byte) 0); digest.update(bytes);
			final var root = factory.newDocumentBuilder().parse(new java.io.ByteArrayInputStream(bytes)).getDocumentElement();
			for (var child = root.getFirstChild(); child != null; child = child.getNextSibling())
			{
				if (!(child instanceof org.w3c.dom.Element element)) { continue; }
				switch (element.getTagName())
				{
					case "node" -> nodes.put(element.getAttribute("id"), element);
					case "anchor" -> anchors.put(element.getAttribute("id"), element);
					case "edge" -> edges.add(element);
				}
			}
		}
		final var regions = new java.util.HashMap<String, Integer>();
		final var spawns = new java.util.HashMap<Integer, List<PhantomTopologyValidationBackend.SpawnFact>>();
		final var doors = new java.util.HashMap<Integer, PhantomTopologyValidationBackend.DoorFact>();
		for (var anchor : anchors.values())
		{
			if (anchor.hasAttribute("mapRegionLocId")) { regions.put(anchor.getAttribute("x") + "," + anchor.getAttribute("y"), xmlInt129(anchor, "mapRegionLocId")); }
			if (anchor.hasAttribute("npcId"))
			{
				final int npc = xmlInt129(anchor, "npcId");
				final var source = anchor.getAttribute("tags").contains("normalized-point-farm") ? nodes.get(anchor.getAttribute("nodeId")) : anchor;
				spawns.computeIfAbsent(npc, _ -> new java.util.ArrayList<>()).add(new PhantomTopologyValidationBackend.SpawnFact(npc, xmlPoint129(source)));
			}
		}
		for (var edge : edges)
		{
			if (!edge.hasAttribute("doorId")) { continue; }
			final var a = xmlPoint129(anchors.get(edge.getAttribute("fromAnchorId"))); final var b = xmlPoint129(anchors.get(edge.getAttribute("toAnchorId")));
			final int x = Math.min(a.x(), b.x()) - 1, y = Math.min(a.y(), b.y()) - 1, xx = Math.max(a.x(), b.x()) + 1, yy = Math.max(a.y(), b.y()) + 1;
			final int id = xmlInt129(edge, "doorId");
			doors.put(id, new PhantomTopologyValidationBackend.DoorFact(id, a.instanceId(), Math.min(a.z(), b.z()), Math.max(a.z(), b.z()), List.of(new PhantomTopologyPoint(x, y, a.z(), a.instanceId()), new PhantomTopologyPoint(xx, y, a.z(), a.instanceId()), new PhantomTopologyPoint(xx, yy, a.z(), a.instanceId()), new PhantomTopologyPoint(x, yy, a.z(), a.instanceId()))));
		}
		final var modeled = new PhantomTopologyValidationBackend()
		{
			@Override public int mapRegionLocId(int x, int y) { return regions.getOrDefault(x + "," + y, 0); }
			@Override public Optional<NpcFact> npc(int id) { return spawns.containsKey(id) ? Optional.of(new NpcFact(id, "Monster", true)) : Optional.empty(); }
			@Override public List<SpawnFact> spawns(int id, int maximum) { final var rows = spawns.getOrDefault(id, List.of()); return rows.subList(0, Math.min(maximum, rows.size())); }
			@Override public Optional<DoorFact> door(int id) { return Optional.ofNullable(doors.get(id)); }
			@Override public DoorState doorState(int id) { return DoorState.OPEN; }
			@Override public boolean sourceExists(String path) { return java.nio.file.Files.isRegularFile(gameRoot.resolve(path)); }
		};
		final var topology = new PhantomTopologyQuery(new PhantomTopologyLoader(directory, modeled, PhantomTopologyPolicy.productionDefaults()).load(1), modeled, new PhantomTopologyMetrics());
		final var catalog = gameRoot.resolve("data/phantoms/travel/high-five-normal-gk.xml");
		if (org.l2jmobius.gameserver.data.xml.TeleporterData.getInstance().getTeleporterCount() == 0 && !org.l2jmobius.gameserver.data.xml.TeleporterData.getInstance().parseDirectory(gameRoot.resolve("data/teleporters").toFile(), true)) { throw new IllegalArgumentException("Route fixture teleporter data is absent."); }
		final var travel = PhantomNormalGatekeeperTravel.load(catalog, topology);
		if (!report)
		{
			if (org.l2jmobius.commons.database.DatabaseFactory.isInitialized()) { throw new AssertionError("Route fixture initialized JDBC"); }
			return travel;
		}
		final var from = List.of("generated.farm.4c99bd428d6bae74750f6eec.anchor", "generated.farm.c34d4845871d5a78bc5d3cd1.anchor", "generated.farm.72007cc387f17e963e4fcf2c.anchor", "hunters.recovery.anchor");
		final var to = List.of("generated.farm.72007cc387f17e963e4fcf2c.anchor", "generated.farm.4c99bd428d6bae74750f6eec.anchor", "generated.farm.c34d4845871d5a78bc5d3cd1.anchor", "generated.farm.004bf9b0681c8a71c852b231.anchor", "generated.farm.007f9057c1ee101b364fff57.anchor", "generated.farm.167d59b550bc5333b845ad5a.anchor");
		System.out.println("ROUTE129_SCOPE=STRUCTURAL_SOURCE_GRAPH_ONLY; factualValidation=MODELED; GeoNativeAdmission=NOT_RUN; topologyFiles=" + files.size() + "; topologyFilesSha=" + java.util.HexFormat.of().formatHex(digest.digest()) + "; topologyCanonicalSha=" + topology.snapshot().canonicalHash() + "; catalogSha=" + java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(java.nio.file.Files.readAllBytes(catalog))));
		for (var origin : from)
		{
			for (var target : to)
			{
				final var route = travel.route(origin, target).orElse(null);
				System.out.println("ROUTE129 from=" + origin + "; to=" + target + "; result=" + (route == null ? "ABSENT" : "PRESENT") + "; steps=" + (route == null ? -1 : route.size()) + "; millis=" + (route == null ? -1 : route.stream().mapToLong(PhantomNormalGatekeeperTravel.Step::travelMillis).sum()) + "; ids=" + (route == null ? List.of() : route.stream().map(PhantomNormalGatekeeperTravel.Step::id).toList()));
			}
		}
		if (org.l2jmobius.commons.database.DatabaseFactory.isInitialized()) { throw new AssertionError("Route-only proof initialized JDBC"); }
		System.out.println("ROUTE129_JDBC_INITIALIZED=false; WorldIdManagerConfigLoader=NOT_CALLED; sourceWrites=0; nativeActions=0");
		return travel;
	}
	private static int xmlInt129(org.w3c.dom.Element value, String key) { return Integer.parseInt(value.getAttribute(key)); }
	private static PhantomTopologyPoint xmlPoint129(org.w3c.dom.Element value) { return new PhantomTopologyPoint(xmlInt129(value, "x"), xmlInt129(value, "y"), xmlInt129(value, "z"), xmlInt129(value, "instanceId")); }

	private static void supportRendezvous() throws Exception
	{
		final var planner = planner(true, true, false); final var before = state(); final var previous = goal();
		final var source = new PhantomDomainRef("background.farm", "103@farm.alternative");
		final java.util.function.Predicate<PhantomGoal> allow = _ -> true;
		PhantomAssertions.assertFalse(planner.replanVisibleLocal(74, before, previous, 740074, 1, LOCAL, Set.of(), Set.of()).ready(), "A non-solo support cannot gain ordinary Solo farm admission.");
		PhantomAssertions.assertTrue(planner.replanVisibleRendezvous(74, before, previous, 740074, 1, LOCAL, source, Set.of(), allow).ready(), "Every explicit Party rendezvous API uses group-purpose admission.");
		final java.lang.reflect.Method support;
		try { support = PhantomHistoricalBackgroundPlanner.class.getMethod("replanVisibleRendezvous", long.class, PhantomBackgroundState.class, PhantomGoal.class, long.class, long.class, PhantomTopologyPoint.class, PhantomDomainRef.class, Set.class, boolean.class, java.util.function.Predicate.class); }
		catch (NoSuchMethodException oldProduction)
		{
			PhantomAssertions.assertTrue(planner.replanVisibleRendezvous(74, before, previous, 740074, 1, LOCAL, source, Set.of(), allow).ready(), "RED129: accepted support travel is wrongly coupled to individual Solo viability."); return;
		}
		final var shared = (PhantomHistoricalBackgroundPlanner.Result) support.invoke(planner, 74L, before, previous, 740074L, 1L, LOCAL, source, Set.of(), true, allow);
		PhantomAssertions.assertTrue(shared.ready() && shared.spec().anchorId().equals("farm.alternative"), "An authorized support may propose only the exact shared native travel source.");
		PhantomAssertions.assertEquals(List.of("farm.alternative.link"), shared.routeEdgeIds(), "Support adoption still needs its certified existing itinerary.");
		PhantomAssertions.assertFalse(PhantomOffscreenOutdoorTravel.pending(shared.goal()), "Support travel cannot create an offscreen group-farm plan.");
		PhantomAssertions.assertEquals(previous.goalId(), shared.goal().goalId(), "Support travel preserves the own root.");
		PhantomAssertions.assertEquals(previous.revision() + 1, shared.goal().revision(), "Support travel advances the own revision once.");
		PhantomAssertions.assertFalse(planner.remainsSuitable(before, shared.goal()), "The shared travel exception must not make the non-solo support historically suitable.");
		for (var denied : List.of(
			(PhantomHistoricalBackgroundPlanner.Result) support.invoke(planner, 74L, before, previous, 740074L, 1L, LOCAL, source, Set.of("farm.alternative.link"), true, allow),
			(PhantomHistoricalBackgroundPlanner.Result) support.invoke(planner, 74L, before, previous, 740074L, 1L, LOCAL, source, Set.of(), true, (java.util.function.Predicate<PhantomGoal>) _ -> false),
			(PhantomHistoricalBackgroundPlanner.Result) support.invoke(planner, 74L, before, previous, 740074L, 1L, new PhantomTopologyPoint(100, 100, 0, 1), source, Set.of(), true, allow)))
		{ PhantomAssertions.assertFalse(denied.ready(), "Support cannot waive route exclusions, native admission or instance identity."); }
		final var generation = planner.generation();
		final var catchup = new PhantomBackgroundCatchupState(PhantomBackgroundCatchupState.Status.RUNNING, "a".repeat(64), 740074, 100, 104, 100, 1, 0, 1,
			generation.knowledgeGeneration(), generation.topologyGeneration(), shared.goal().goalId(), shared.goal().revision(), shared.planIdentity(), PhantomBackgroundState.MODEL_VERSION, generation.authorityHashes(), "");
		final var reconsider = PhantomHistoricalBackgroundService.class.getDeclaredMethod("reconsiderHistorical", PhantomHistoricalBackgroundPlanner.class, long.class, PhantomBackgroundState.class, PhantomGoal.class, PhantomBackgroundCatchupState.class, java.util.function.BooleanSupplier.class);
		reconsider.setAccessible(true);
		final var cold = (PhantomHistoricalBackgroundPlanner.Result) reconsider.invoke(null, planner, 74L, before.withState(PhantomBackgroundState.State.READY), shared.goal(), catchup, (java.util.function.BooleanSupplier) () -> false);
		PhantomAssertions.assertTrue(cold.ready() && PhantomBackgroundGoalSpec.canonicalHistoricalIdle(74, cold.goal()), "The unchanged production historical reconsider guard must replace a non-solo shared root before an offline model interval.");
		PhantomAssertions.assertEquals(before.position().committedAnchorId(), cold.goal().selectedAnchor().key(), "Cold fallback cannot award the shared arrival.");
		System.out.println("OWN129_SUPPORT_TRAVEL_PASS; exact shared route only; Solo unchanged; historical reconsider blocks group simulation; DB_WRITES=0");
	}

	/** Group discovery/travel must never ask whether one member can win alone. */
	public static void widePartySources130()
	{
		final var base = state();
		final var before = new PhantomBackgroundState(base.state(), base.identity(), new PhantomBackgroundState.Progress(76, 0, 0, 0), base.vitals(), base.position(), base.combat(), base.loadout(), base.inventory(), base.autoGetSkills(), base.clock(), base.receipt(), base.hashes());
		final var backend = new PhantomTopologyValidationBackend()
		{
			@Override public int mapRegionLocId(int x, int y) { return 1; }
			@Override public Optional<PhantomTopologyValidationBackend.NpcFact> npc(int id) { return Optional.of(new PhantomTopologyValidationBackend.NpcFact(id, "Monster", true)); }
			@Override public List<SpawnFact> spawns(int id, int maximum) { return List.of(new SpawnFact(id, id == 101 ? LOCAL : FAR)); }
			@Override public Optional<DoorFact> door(int id) { return Optional.empty(); }
			@Override public DoorState doorState(int id) { return DoorState.MISSING; }
			@Override public boolean sourceExists(String source) { return true; }
		};
		final var nodes = new java.util.ArrayList<PhantomTopologyNode>(); final var anchors = new java.util.ArrayList<PhantomTopologyAnchor>();
		final var edges = new java.util.ArrayList<PhantomTopologyEdge>(); final var npcs = new java.util.ArrayList<NpcFact>(); final var areas = new java.util.ArrayList<SpawnAreaFact>();
		nodes.add(node("area.local", LOCAL)); anchors.add(anchor("farm.local", "area.local", LOCAL, 101));
		for (int index = 0; index < 67; index++)
		{
			final int npc = 20000 + index; final String node = "area.page." + index, anchor = "farm.page." + index;
			nodes.add(node(node, FAR)); anchors.add(anchor(anchor, node, FAR, npc));
			edges.add(new PhantomTopologyEdge("edge.page." + index, "area.local", node, PhantomTopologyEdgeMode.BACKGROUND, true, 1, 100000, true, Set.of(), null, "farm.local", anchor, List.of()));
			npcs.add(new NpcFact(npc, index == 65 ? 71 : index == 66 ? 81 : 76, NpcKind.MONSTER, true, true, false, 100, 10, PhantomGameKnowledgeAuthority.SERVER_LOADER_FACT));
			areas.add(new SpawnAreaFact(npc, 0, node, 1, 1, 1, List.of(), PhantomGameKnowledgeAuthority.TOPOLOGY_SNAPSHOT_FACT));
		}
		final var topology = new PhantomTopologyQuery(PhantomTopologySnapshot.create(1, "party130", 1, 1, nodes, anchors, edges, backend, PhantomTopologyPolicy.productionDefaults()), backend, new PhantomTopologyMetrics());
		final var knowledge = new PhantomGameKnowledgeQuery(new PhantomGameKnowledgeSnapshot("party130", 1, topology.snapshot().canonicalHash(), PhantomGameKnowledgePolicy.productionDefaults(), List.of(), npcs, List.of(), List.of(), areas, List.of(), List.of(), List.of(), List.of(), List.of()), new PhantomGameKnowledgeMetrics());
		final var authority = new PhantomBackgroundAuthority()
		{
			@Override public PhantomBackgroundState.Hashes hashes() { return HASHES; }
			@Override public boolean viableTarget(PlanningCombat combat, int npcId) { throw new AssertionError("PARTY called Solo viableTarget."); }
			@Override public PhantomBackgroundState capture(long id, Player player, PhantomGoal goal, PhantomBackgroundState previous) { throw new UnsupportedOperationException(); }
			@Override public boolean matchesRuntime(Player player, PhantomBackgroundState state) { return false; }
			@Override public FarmInput farmInput(PhantomBackgroundState state, PhantomBackgroundGoalSpec goal) { throw new UnsupportedOperationException(); }
			@Override public TravelAdvance advanceTravel(PhantomBackgroundState state, PhantomBackgroundGoalSpec goal, long budget) { throw new UnsupportedOperationException(); }
			@Override public Optional<PhantomBackgroundState.Position> canonicalRecoveryPosition(int x, int y, int z, int instance, int heading) { return Optional.empty(); }
			@Override public List<PhantomBackgroundState.AutoGetSkill> autoGetSkills(PhantomBackgroundState.Identity identity, int level) { return List.of(); }
		};
		final var planner = new PhantomHistoricalBackgroundPlanner(knowledge, topology, authority);
		final var common = new PhantomDomainRef("background.farm", "20064@farm.page.64");
		PhantomAssertions.assertFalse(planner.rendezvousSources(74, before, goal(), 130, 1, Set.of(), Set.of()).contains(common), "Fixture did not put the common source beyond the first page.");
		PhantomAssertions.assertEquals(List.of(common), planner.rendezvousSources(74, before, goal(), 130, 1, Set.of(), Set.of(), common::equals), "The 65th common source was lost before intersection.");
		PhantomAssertions.assertTrue(planner.partySourceAvailable(before, common, Set.of()), "Exact peer proof depends on its first source page.");
		PhantomAssertions.assertFalse(planner.partySourceAvailable(before, common, Set.of("edge.page.64")), "Excluded route was accepted.");
		PhantomAssertions.assertFalse(planner.partySourceAvailable(before, new PhantomDomainRef("background.farm", "20065@farm.page.65"), Set.of()), "NPC below member-4 accepted.");
		PhantomAssertions.assertFalse(planner.partySourceAvailable(before, new PhantomDomainRef("background.farm", "20066@farm.page.66"), Set.of()), "NPC above member+4 accepted.");
		PhantomAssertions.assertFalse(planner.partySourceAvailable(before, new PhantomDomainRef("background.farm", "20064@farm.page.63"), Set.of()), "NPC/anchor identity was invented.");
		PhantomAssertions.assertFalse(planner.partySourceAvailable(before, new PhantomDomainRef("background.farm", "99999@farm.page.64"), Set.of()), "Unknown NPC was invented.");
	}

	public static void partyContract130()
	{
		final var planner = planner(true, true, false);
		final var before = state(); final var previous = goal();
		final var source = new PhantomDomainRef("background.farm", "103@farm.alternative");
		PhantomAssertions.assertFalse(planner.replan(74, before, previous, 740074, 1).ready(), "Solo must retain individual viability.");
		PhantomAssertions.assertTrue(planner.rendezvousSources(74, before, previous, 740074, 1, Set.of(), Set.of()).contains(source),
			"Party source discovery incorrectly requires an individual Solo kill.");
		PhantomAssertions.assertTrue(planner.replanLogicalRendezvous(74, before, previous, 740074, 1, source).ready(), "Logical travel repeated Solo admission.");
		PhantomAssertions.assertTrue(planner.proposeLogicalActivity(74, before, previous, 740074, 1, source).ready(), "Activity revision repeated Solo admission.");
		PhantomAssertions.assertTrue(planner.replanVisibleRendezvous(74, before, previous, 740074, 1, LOCAL, source, Set.of(), _ -> true).ready(), "Visible Party travel repeated Solo admission.");
		PhantomAssertions.assertFalse(planner.remainsSuitable(before, previous), "Party planning weakened the ordinary Solo reward fence.");
		PhantomAssertions.assertFalse(planner.replanVisibleRendezvous(74, before, previous, 740074, 1, LOCAL, source, Set.of("farm.alternative.link"), _ -> true).ready(), "Party bypassed an excluded route.");
		PhantomAssertions.assertFalse(planner.replanVisibleRendezvous(74, before, previous, 740074, 1, LOCAL, source, Set.of(), _ -> false).ready(), "Party bypassed native travel admission.");
		PhantomAssertions.assertEquals(before, state(), "Planning changed earned state or resources.");
	}

	private static void historicalFailedRoute()
	{
		final var planner = planner(true, true); final var before = state(); final var previous = goal();
		final var exhaustedLocal = Set.of("101@farm.local");
		final var original = planner.replan(74, before, previous, 740074, 1, Set.of("101@farm.local", "103@farm.alternative"), Set.of());
		PhantomAssertions.assertTrue(original.ready() && original.routeEdgeIds().contains("farm.link"), "The control fixture must expose the failed historical itinerary.");
		final var alternative = planner.replan(74, before, previous, 740074, 1, exhaustedLocal, Set.of("farm.link"));
		PhantomAssertions.assertTrue(alternative.ready() && alternative.spec().anchorId().equals("farm.alternative"), "A failed shared route step must not be selected again while an admissible alternative exists.");
		PhantomAssertions.assertEquals(previous.goalId(), alternative.goal().goalId(), "Route recovery preserves the own root.");
		PhantomAssertions.assertEquals(previous.revision() + 1, alternative.goal().revision(), "Route recovery proposes one revision.");
		final var denied = planner.replan(74, before, previous, 740074, 1, exhaustedLocal, Set.of("farm.link", "farm.alternative.link"));
		PhantomAssertions.assertFalse(denied.ready(), "All denied itineraries must not invent a route or arrival.");
		PhantomAssertions.assertEquals("planner.target_or_route.absent", denied.reasonKey(), "No-route recovery must retain its existing canonical idle fallback reason.");
		final var idle = planner.idleFromState(74, before, previous, 740074, 1);
		PhantomAssertions.assertTrue(idle.ready() && PhantomBackgroundGoalSpec.canonicalHistoricalIdle(74, idle.goal()), "Exhausted historical routes may use the existing canonical idle plan.");
		PhantomAssertions.assertEquals(before.position().committedAnchorId(), idle.goal().selectedAnchor().key(), "Idle retains the proven current anchor, not the failed arrival.");
		PhantomAssertions.assertEquals(before, state(), "Read-only recovery planning cannot mutate earned facts, position or clock.");
		System.out.println("OWN129_HISTORICAL_ROUTE_EXCLUSION_PASS; alternate or canonical idle; DB_WRITES=0; OWNER_RECOVERY=NOT_RUN");
	}

	private static void rendezvous() throws Exception
	{
		final java.lang.reflect.Method replan;
		try { replan = PhantomHistoricalBackgroundPlanner.class.getMethod("replanVisibleRendezvous", long.class, PhantomBackgroundState.class, PhantomGoal.class, long.class, long.class, PhantomTopologyPoint.class, PhantomDomainRef.class, Set.class, java.util.function.Predicate.class); }
		catch (NoSuchMethodException missing) { throw new AssertionError("RED129: an accepted remote farm source must replace the local choice through the existing planner.", missing); }
		final var planner = planner(true, true); final var previous = goal(); final var before = state();
		final var source = new PhantomDomainRef("background.farm", "103@farm.alternative");
		final java.util.function.Predicate<PhantomGoal> allow = _ -> true;
		final var shared = (PhantomHistoricalBackgroundPlanner.Result) replan.invoke(planner, 74L, before, previous, 740074L, 1L, LOCAL, source, Set.of(), allow);
		PhantomAssertions.assertTrue(shared.ready(), "A distant accepted source must be selectable even while a different local farm remains suitable.");
		PhantomAssertions.assertEquals("farm.alternative", shared.spec().anchorId(), "Rendezvous cannot silently fall back to an individually preferred spot.");
		PhantomAssertions.assertEquals(103, shared.spec().npcId(), "Rendezvous must retain the exact accepted native target source.");
		PhantomAssertions.assertEquals(List.of("farm.alternative.link"), shared.routeEdgeIds(), "A distant shared source needs its existing canonical itinerary.");
		PhantomAssertions.assertEquals(740074L, shared.goal().goalId(), "Rendezvous retains the actor's own root identity.");
		PhantomAssertions.assertEquals(2L, shared.goal().revision(), "Rendezvous advances the own root revision once.");
		PhantomAssertions.assertEquals(before, state(), "Planning cannot mutate earned state or claim native arrival.");
		System.out.println("RENDEZVOUS129_PLANNER_PASS; exact remote source; own root/revision; DB_WRITES=0; NATIVE_ARRIVAL=0");
		final java.util.function.Predicate<PhantomGoal> deny = _ -> false;
		for (var result : List.of(
			(PhantomHistoricalBackgroundPlanner.Result) replan.invoke(planner, 74L, before, previous, 740074L, 1L, LOCAL, source, Set.of("farm.alternative.link"), allow),
			(PhantomHistoricalBackgroundPlanner.Result) replan.invoke(planner(false), 74L, before, previous, 740074L, 1L, LOCAL, new PhantomDomainRef("background.farm", "102@farm.far"), Set.of(), allow),
			(PhantomHistoricalBackgroundPlanner.Result) replan.invoke(planner, 74L, before, previous, 740074L, 1L, LOCAL, source, Set.of(), deny),
			(PhantomHistoricalBackgroundPlanner.Result) replan.invoke(planner, 74L, before, previous, 740074L, 1L, new PhantomTopologyPoint(100, 100, 0, 1), source, Set.of(), allow)))
		{
			PhantomAssertions.assertFalse(result.ready(), "Rendezvous cannot bypass excluded, disconnected, denied or foreign-instance travel.");
		}
		for (var malformed : List.of(new PhantomDomainRef("npc", "103"), new PhantomDomainRef("background.farm", "103@farm.alternative@extra"), new PhantomDomainRef("background.farm", "0@farm.alternative")))
		{
			try { replan.invoke(planner, 74L, before, previous, 740074L, 1L, LOCAL, malformed, Set.of(), allow); throw new AssertionError("Malformed rendezvous source was admitted."); }
			catch (java.lang.reflect.InvocationTargetException rejected) { PhantomAssertions.assertTrue(rejected.getCause() instanceof IllegalArgumentException, "Malformed source must be rejected by its contract."); }
		}
		System.out.println("RENDEZVOUS129_FENCES_PASS; exact source; route/admission/instance negatives; DB_WRITES=0");
	}

	public static PhantomHistoricalBackgroundPlanner planner(boolean linked)
	{
		return planner(linked, false);
	}
	private static PhantomHistoricalBackgroundPlanner planner(boolean linked, boolean alternative)
	{
		return planner(linked, alternative, true);
	}
	private static PhantomHistoricalBackgroundPlanner planner(boolean linked, boolean alternative, boolean soloViable)
	{
		return planner(linked, alternative, soloViable, false);
	}
	private static PhantomHistoricalBackgroundPlanner planner(boolean linked, boolean alternative, boolean soloViable, boolean ingress)
	{ return planner(linked, alternative, soloViable, ingress, false); }
	/** Same immutable planner fixture, aligned with the existing SQL130 receipt fixture; never native world facts. */
	public static PhantomHistoricalBackgroundPlanner receiptPlanner130(boolean linked) { return planner(linked, false, true, false, true); }
	public static PhantomHistoricalBackgroundPlanner receiptPlanner130(boolean linked, PhantomBackgroundAuthority authority) { return planner(linked, false, true, false, true, authority); }
	private static PhantomHistoricalBackgroundPlanner planner(boolean linked, boolean alternative, boolean soloViable, boolean ingress, boolean receipts)
	{ return planner(linked, alternative, soloViable, ingress, receipts, null); }
	private static PhantomHistoricalBackgroundPlanner planner(boolean linked, boolean alternative, boolean soloViable, boolean ingress, boolean receipts, PhantomBackgroundAuthority override)
	{
		final int localNpc = receipts ? 20101 : 101, farNpc = receipts ? 20102 : 102, level = receipts ? 1 : 20;
		final String localAnchor = receipts ? "farm.receipt130" : "farm.local", farAnchor = receipts ? "farm.receipt130.b" : "farm.far";
		final var backend = new PhantomTopologyValidationBackend()
		{
			@Override public int mapRegionLocId(int x, int y) { return 1; }
			@Override public Optional<PhantomTopologyValidationBackend.NpcFact> npc(int id) { return Optional.of(new PhantomTopologyValidationBackend.NpcFact(id, "Monster", true)); }
			@Override public List<PhantomTopologyValidationBackend.SpawnFact> spawns(int id, int bound) { return List.of(new PhantomTopologyValidationBackend.SpawnFact(id, id == localNpc ? LOCAL : id == farNpc ? FAR : FAR_ALTERNATIVE)); }
			@Override public Optional<DoorFact> door(int id) { return Optional.empty(); }
			@Override public DoorState doorState(int id) { return DoorState.MISSING; }
			@Override public boolean sourceExists(String source) { return true; }
		};
		final var nodes = new java.util.ArrayList<>(List.of(node("area.local", LOCAL), node("area.far", FAR)));
		final var anchors = new java.util.ArrayList<>(List.of(anchor(localAnchor, "area.local", LOCAL, localNpc), anchor(farAnchor, "area.far", FAR, farNpc)));
		final var edges = new java.util.ArrayList<>(linked ? List.of(new PhantomTopologyEdge("farm.link", "area.local", "area.far", PhantomTopologyEdgeMode.BACKGROUND, true, 1, 100000, true, Set.of(), null, localAnchor, farAnchor, List.of())) : List.<PhantomTopologyEdge>of());
		if (alternative)
		{
			nodes.add(node("area.alternative", FAR_ALTERNATIVE)); anchors.add(anchor("farm.alternative", "area.alternative", FAR_ALTERNATIVE, 103));
			edges.add(new PhantomTopologyEdge("farm.alternative.link", ingress ? "area.far" : "area.local", "area.alternative", PhantomTopologyEdgeMode.BACKGROUND, true, 1, 110000, true, Set.of(), null, ingress ? "farm.far" : "farm.local", "farm.alternative", List.of()));
		}
		final var topology = new PhantomTopologyQuery(PhantomTopologySnapshot.create(1, "farm074", 1, 1, nodes, anchors, edges, backend, PhantomTopologyPolicy.productionDefaults()), backend, new PhantomTopologyMetrics());
		final var npcs = new java.util.ArrayList<>(List.of(new NpcFact(localNpc, level, NpcKind.MONSTER, true, true, false, 100, 10, PhantomGameKnowledgeAuthority.SERVER_LOADER_FACT), new NpcFact(farNpc, level + 2, NpcKind.MONSTER, true, true, false, 200, 20, PhantomGameKnowledgeAuthority.SERVER_LOADER_FACT)));
		final var areas = new java.util.ArrayList<>(List.of(new SpawnAreaFact(localNpc, 0, "area.local", 1, 1, 1, List.of(), PhantomGameKnowledgeAuthority.TOPOLOGY_SNAPSHOT_FACT), new SpawnAreaFact(farNpc, 0, "area.far", 1, 1, 1, List.of(), PhantomGameKnowledgeAuthority.TOPOLOGY_SNAPSHOT_FACT)));
		if (alternative)
		{
			npcs.add(new NpcFact(103, 21, NpcKind.MONSTER, true, true, false, 150, 15, PhantomGameKnowledgeAuthority.SERVER_LOADER_FACT));
			areas.add(new SpawnAreaFact(103, 0, "area.alternative", 1, 1, 1, List.of(), PhantomGameKnowledgeAuthority.TOPOLOGY_SNAPSHOT_FACT));
		}
		final var snapshot = new PhantomGameKnowledgeSnapshot("farm074", 1, topology.snapshot().canonicalHash(), PhantomGameKnowledgePolicy.productionDefaults(), List.of(), npcs, List.of(), List.of(), areas, List.of(), List.of(), List.of(), List.of(), List.of());
		final var knowledge = new PhantomGameKnowledgeQuery(snapshot, new PhantomGameKnowledgeMetrics());
		final var authority = new PhantomBackgroundAuthority()
		{
			@Override public PhantomBackgroundState.Hashes hashes() { return receipts ? new PhantomBackgroundState.Hashes("knowledge130", "topology130", "progression130", "commerce130") : HASHES; }
			@Override public boolean viableTarget(PlanningCombat combat, int npcId) { return soloViable; }
			@Override public boolean offscreenOutdoorCandidate(PhantomTopologyQuery proposed, String from, TargetFact target, String to) { return true; }
			@Override public boolean nativeIngressPositionKnown(PhantomTopologyAnchor anchor, int heading) { return ingress && anchor.id().equals("farm.far"); }
			@Override public PhantomBackgroundState capture(long id, Player player, PhantomGoal goal, PhantomBackgroundState previous) { throw new UnsupportedOperationException(); }
			@Override public boolean matchesRuntime(Player player, PhantomBackgroundState state) { return false; }
			@Override public FarmInput farmInput(PhantomBackgroundState state, PhantomBackgroundGoalSpec goal) { throw new UnsupportedOperationException(); }
			@Override public TravelAdvance advanceTravel(PhantomBackgroundState state, PhantomBackgroundGoalSpec goal, long budget) { throw new UnsupportedOperationException(); }
			@Override public Optional<PhantomBackgroundState.Position> canonicalRecoveryPosition(int x, int y, int z, int instance, int heading) { return Optional.empty(); }
			@Override public List<PhantomBackgroundState.AutoGetSkill> autoGetSkills(PhantomBackgroundState.Identity identity, int level) { return List.of(); }
		};
		return new PhantomHistoricalBackgroundPlanner(knowledge, topology, override == null ? authority : override);
	}

	private static PhantomTopologyNode node(String id, PhantomTopologyPoint point)
	{
		return new PhantomTopologyNode(id, PhantomTopologyNodeKind.FARMING_AREA, 0, PhantomTopologyArea.cuboid(0, point.x() - 50, point.x() + 50, point.y() - 50, point.y() + 50, -100, 100), null, List.of("outdoor-farming"), List.of("fixture074"));
	}
	private static PhantomTopologyAnchor anchor(String id, String node, PhantomTopologyPoint point, int npc)
	{
		return new PhantomTopologyAnchor(id, PhantomTopologyAnchorRole.FARMING, node, point, npc, null, 0, List.of("outdoor-farming"), List.of("fixture074"));
	}
	public static PhantomGoal goal()
	{
		return new PhantomGoal(740074, "farm.background", PhantomGoalStatus.ACTIVE, new PhantomDomainRef("profile", "74"), new PhantomDomainRef("npc", "101"), 1, 0, "background.farm", List.of(new PhantomDomainRef("background.farm", "101@farm.local")), new PhantomDomainRef("topology.anchor", "farm.local"), "farm.background", 500, 0, 0, 0, Map.of(), "fixture074", 1);
	}
	public static PhantomBackgroundState state()
	{
		return new PhantomBackgroundState(PhantomBackgroundState.State.MATERIALIZED, new PhantomBackgroundState.Identity(74, 740074, 0, 1, 0), new PhantomBackgroundState.Progress(20, 0, 0, 0), new PhantomBackgroundState.Vitals(100, 100, 100, 100, 100, 100), new PhantomBackgroundState.Position(0, 100, 100, 0, 0, "farm.local"), new PhantomBackgroundState.CombatFacts(PhantomBackgroundState.ModelKind.MELEE, 100, 100, 100, 100, 500, 500, 1, 1, 1, 1, 0, 1, 1, 1, 1), PhantomBackgroundState.Loadout.none(), new PhantomBackgroundState.InventoryFacts(List.of(), List.of(), "fixture074", 0, 1000, 0, 100), List.of(), new PhantomBackgroundState.Clock(740074, 0, 0), PhantomBackgroundState.Receipt.empty(), HASHES);
	}
}
