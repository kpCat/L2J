package org.l2jmobius.tests.phantoms;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.l2jmobius.gameserver.model.actor.enums.player.PlayerClass;
import org.l2jmobius.gameserver.phantoms.background.L2jPhantomBackgroundAuthority;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundGoalSpec;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState;
import org.l2jmobius.gameserver.phantoms.background.PhantomHistoricalBackgroundPlanner;
import org.l2jmobius.gameserver.phantoms.background.PhantomNormalGatekeeperTravel;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoal;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.NpcKind;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.PageRequest;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.TargetQuery;
import org.l2jmobius.gameserver.phantoms.topology.L2jTopologyValidationBackend;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyLoader;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyAnchorRole;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPolicy;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyValidationBackend;

/** Loaded TEST DB and native server-data gate for the LIVE-003 route proof. */
public final class PhantomLive003RuntimeAuthorityProofSuite implements PhantomTestSuite
{
	private static final int[] LEVELS = {1, 20, 40, 76, 85};
	private static final String[] STAGES = {"base", "first", "second", "third", "third"};
	private static final List<Lineage> LINEAGES = List.of(
		new Lineage("human_like", "population.ingress.human-fighter.01", new PlayerClass[] {PlayerClass.FIGHTER, PlayerClass.WARRIOR, PlayerClass.WARLORD, PlayerClass.DREADNOUGHT, PlayerClass.DREADNOUGHT}),
		new Lineage("dwarf_spoiler", "population.ingress.dwarf.01", new PlayerClass[] {PlayerClass.DWARVEN_FIGHTER, PlayerClass.SCAVENGER, PlayerClass.BOUNTY_HUNTER, PlayerClass.FORTUNE_SEEKER, PlayerClass.FORTUNE_SEEKER}),
		new Lineage("alternate_non_dwarf", "population.ingress.elf.01", new PlayerClass[] {PlayerClass.ELVEN_FIGHTER, PlayerClass.ELVEN_SCOUT, PlayerClass.SILVER_RANGER, PlayerClass.MOONLIGHT_SENTINEL, PlayerClass.MOONLIGHT_SENTINEL}));
	private final PhantomHeadlessPlayerTestEnvironment _environment = new PhantomHeadlessPlayerTestEnvironment();

	@Override
	public String id()
	{
		return "live003-runtime-authority-proof";
	}

	@Override
	public void beforeAll(PhantomTestContext context) throws Exception
	{
		_environment.initialize(context);
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("active-topology-loads-from-native-facts", _ ->
		{
			final DiagnosticBackend backend = new DiagnosticBackend();
			try
			{
				new PhantomTopologyLoader(Path.of("data/phantoms/topology"), backend, PhantomTopologyPolicy.productionDefaults()).load(1);
			}
			catch (RuntimeException failure)
			{
				throw new AssertionError("Loaded topology rejected native NPC " + backend.lastNpcId + " (spawns=" + backend.lastSpawnCount + ", exactXY=" + backend.exactRuinsPoint + ", nearest=" + backend.nearestRuinsPoint + "): " + failure.getMessage(), failure);
			}
		});
		registry.add("fifteen-production-planner-routes", this::proveRoutes);
		registry.add("native-target-page-reachability", this::measureTargetPages);
	}

	private void measureTargetPages(PhantomTestContext context) throws Exception
	{
		try (var fixture = PhantomBackgroundSuite.ProductionAuthorityFixture.start())
		{
			final var travel = PhantomNormalGatekeeperTravel.load(Path.of("data/phantoms/travel/high-five-normal-gk.xml"), fixture.topology());
			final var authority = new L2jPhantomBackgroundAuthority(fixture::knowledge, fixture::topology, fixture::progression, fixture::commerce, travel);
			final var routes = authority.travelQuery(fixture.topology());
			for (int level : LEVELS)
			{
				String cursor = null;
				int examined = 0;
				String first = "absent";
				for (int pageIndex = 0; pageIndex < 8; pageIndex++)
				{
					final var page = fixture.knowledge().suitableTargets(new TargetQuery(Math.max(1, level - 2), level + 2, level, null, null, Set.of(NpcKind.MONSTER), true, true, null, null, null, new PageRequest(256, cursor)));
					for (var target : page.values())
					{
						examined++;
						final var match = target.representativeAreas().stream().filter(area -> area.topologyNodeId() != null).flatMap(area -> fixture.topology().snapshot().anchorsByNode().getOrDefault(area.topologyNodeId(), List.of()).stream()).filter(anchor -> (anchor.role() == PhantomTopologyAnchorRole.FARMING) && ((anchor.npcId() == null) || (anchor.npcId() == target.npc().npcId()))).filter(anchor -> routes.route("population.ingress.dwarf.01", anchor.id()).isPresent()).findFirst();
						if (match.isPresent())
						{
							first = examined + ":" + target.npc().npcId() + ":" + match.get().id();
							break;
						}
					}
					if (!first.equals("absent") || (page.nextCursor() == null))
					{
						break;
					}
					cursor = page.nextCursor();
				}
				context.record("live003.firstDwarfRoute." + level, first);
			}
		}
	}

	private void proveRoutes(PhantomTestContext context) throws Exception
	{
		final List<String> rows = new ArrayList<>();
		rows.add("archetype\tlevel\tclassId\tclassStage\tsourceAnchor\ttargetNpc\ttargetLevel\ttargetAnchor\trouteStepCount\trouteDigest\trouteManifest\tsuitability\tstatus\tblocker");
		int proven = 0;
		try (var fixture = PhantomBackgroundSuite.ProductionAuthorityFixture.start())
		{
			final PhantomNormalGatekeeperTravel travel = PhantomNormalGatekeeperTravel.load(Path.of("data/phantoms/travel/high-five-normal-gk.xml"), fixture.topology());
			final var authority = new L2jPhantomBackgroundAuthority(fixture::knowledge, fixture::topology, fixture::progression, fixture::commerce, travel);
			final var planner = new PhantomHistoricalBackgroundPlanner(fixture.knowledge(), fixture.topology(), authority);
			final var routeQuery = authority.travelQuery(fixture.topology());
			int rowIndex = 0;
			for (Lineage lineage : LINEAGES)
			{
				for (int stage = 0; stage < LEVELS.length; stage++)
				{
					final var source = fixture.topology().findAnchor(stage == 0 ? lineage.sourceAnchor() : "population.ingress.dwarf.01").orElseThrow();
					final int level = LEVELS[stage];
					final PlayerClass playerClass = lineage.classes()[stage];
					final var base = PhantomBackgroundSuite.productionState(source, authority.hashes());
					final var identity = new PhantomBackgroundState.Identity(15001501 + rowIndex, 15001501 + rowIndex, 0, playerClass.getId(), playerClass.getRace().ordinal());
					final var state = new PhantomBackgroundState(base.state(), identity, new PhantomBackgroundState.Progress(level, 0, 0, 0), base.vitals(), base.position(), base.combat(), base.loadout(), base.inventory(), base.autoGetSkills(), base.clock(), base.receipt(), base.hashes());
					final var targets = fixture.knowledge().suitableTargets(new TargetQuery(Math.max(1, level - 2), level + 2, level, null, null, Set.of(NpcKind.MONSTER), true, true, null, null, null, PageRequest.first(64))).values();
					final PhantomHistoricalBackgroundPlanner.Result plan = targets.isEmpty() ? PhantomHistoricalBackgroundPlanner.Result.blocked("knowledge.targets.absent") : planner.replan(identity.profileId(), state, previousGoal(identity.profileId(), targets.getFirst().npc().npcId(), source.id()), context.seed() + rowIndex, 1);
					final var target = plan.ready() ? fixture.knowledge().findNpc(plan.spec().npcId()).orElse(null) : null;
					final boolean suitable = plan.ready() && (target != null) && planner.remainsSuitable(state, plan.goal());
					final boolean routePresent = plan.ready() && !plan.routeEdgeIds().isEmpty() && fixture.topology().findAnchor(plan.spec().anchorId()).isPresent();
					final boolean valid = routePresent && suitable;
					if (valid)
					{
						proven++;
					}
					final String manifest = String.join("|", plan.routeEdgeIds());
					final long mapped = targets.stream().flatMap(value -> value.representativeAreas().stream().filter(area -> area.topologyNodeId() != null).flatMap(area -> fixture.topology().snapshot().anchorsByNode().getOrDefault(area.topologyNodeId(), List.of()).stream().filter(anchor -> (anchor.role() == PhantomTopologyAnchorRole.FARMING) && ((anchor.npcId() == null) || (anchor.npcId() == value.npc().npcId()))))).count();
					final long routed = targets.stream().flatMap(value -> value.representativeAreas().stream().filter(area -> area.topologyNodeId() != null).flatMap(area -> fixture.topology().snapshot().anchorsByNode().getOrDefault(area.topologyNodeId(), List.of()).stream().filter(anchor -> (anchor.role() == PhantomTopologyAnchorRole.FARMING) && ((anchor.npcId() == null) || (anchor.npcId() == value.npc().npcId()))))).filter(anchor -> routeQuery.route(source.id(), anchor.id()).isPresent()).count();
					final var widerTargets = valid ? List.copyOf(targets) : fixture.knowledge().suitableTargets(new TargetQuery(Math.max(1, level - 2), level + 2, level, null, null, Set.of(NpcKind.MONSTER), true, true, null, null, null, PageRequest.first(256))).values();
					final long routed256 = widerTargets.stream().flatMap(value -> value.representativeAreas().stream().filter(area -> area.topologyNodeId() != null).flatMap(area -> fixture.topology().snapshot().anchorsByNode().getOrDefault(area.topologyNodeId(), List.of()).stream().filter(anchor -> (anchor.role() == PhantomTopologyAnchorRole.FARMING) && ((anchor.npcId() == null) || (anchor.npcId() == value.npc().npcId()))))).filter(anchor -> routeQuery.route(source.id(), anchor.id()).isPresent()).count();
					rows.add(String.join("\t", lineage.name(), Integer.toString(level), Integer.toString(playerClass.getId()), STAGES[stage], source.id(), plan.ready() ? Integer.toString(plan.spec().npcId()) : "", target == null ? "" : Integer.toString(target.level()), plan.ready() ? plan.spec().anchorId() : "", Integer.toString(plan.routeEdgeIds().size()), manifest.isEmpty() ? "" : sha256(manifest), manifest, Boolean.toString(suitable), valid ? "PROVEN" : "NOT_PROVEN", valid ? "none" : plan.reasonKey() + ";targets=" + targets.size() + ";mapped=" + mapped + ";routed=" + routed + ";routed256=" + routed256));
					rowIndex++;
				}
			}
		}
		final Path output = context.moduleRoot().resolve("docs/phantoms/live-world/LIVE003_PROGRESSION_ROUTE_PROOF.tsv");
		Files.writeString(output, String.join("\n", rows) + "\n", StandardCharsets.UTF_8);
		context.record("live003.progression.proven", proven);
		PhantomAssertions.assertEquals(15, proven, "Production-loaded progression planner did not prove every required row.");
	}

	private static PhantomGoal previousGoal(long profileId, int npcId, String anchorId)
	{
		return new PhantomGoal(123, PhantomBackgroundGoalSpec.GOAL_TYPE, PhantomGoalStatus.ACTIVE, new PhantomDomainRef("profile", Long.toString(profileId)), new PhantomDomainRef(PhantomBackgroundGoalSpec.NPC_NAMESPACE, Integer.toString(npcId)), 1, 0, "background.farm", List.of(new PhantomDomainRef(PhantomBackgroundGoalSpec.SOURCE_NAMESPACE, npcId + "@" + anchorId)), new PhantomDomainRef(PhantomBackgroundGoalSpec.ANCHOR_NAMESPACE, anchorId), "farm.background", 500, 0, 0, 0, java.util.Map.of(), "background.catchup.plan", 0);
	}

	private static String sha256(String value) throws Exception
	{
		return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
	}

	private record Lineage(String name, String sourceAnchor, PlayerClass[] classes)
	{
	}

	@Override
	public void afterAll(PhantomTestContext context) throws Exception
	{
		_environment.shutdown();
	}

	private static final class DiagnosticBackend implements PhantomTopologyValidationBackend
	{
		private final L2jTopologyValidationBackend _native = new L2jTopologyValidationBackend();
		private int lastNpcId;
		private int lastSpawnCount;
		private String exactRuinsPoint = "absent";
		private String nearestRuinsPoint = "absent";

		@Override
		public int mapRegionLocId(int x, int y)
		{
			return _native.mapRegionLocId(x, y);
		}

		@Override
		public Optional<NpcFact> npc(int npcId)
		{
			return _native.npc(npcId);
		}

		@Override
		public List<SpawnFact> spawns(int npcId, int maximumResults)
		{
			lastNpcId = npcId;
			final List<SpawnFact> result = _native.spawns(npcId, maximumResults);
			lastSpawnCount = result.size();
			if (npcId == 20059)
			{
				exactRuinsPoint = result.stream().filter(spawn -> (spawn.point().x() == -33539) && (spawn.point().y() == 137701)).map(spawn -> spawn.point().toString()).findFirst().orElse("absent");
				nearestRuinsPoint = result.stream().min(java.util.Comparator.comparingLong(spawn -> Math.abs((long) spawn.point().x() + 33539) + Math.abs((long) spawn.point().y() - 137701))).map(spawn -> spawn.point().toString()).orElse("absent");
			}
			return result;
		}

		@Override
		public Optional<DoorFact> door(int doorId)
		{
			return _native.door(doorId);
		}

		@Override
		public DoorState doorState(int doorId)
		{
			return _native.doorState(doorId);
		}

		@Override
		public boolean sourceExists(String relativeDatapackPath)
		{
			return _native.sourceExists(relativeDatapackPath);
		}
	}
}
