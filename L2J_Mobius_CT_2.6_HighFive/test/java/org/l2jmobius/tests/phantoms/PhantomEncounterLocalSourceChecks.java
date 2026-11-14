/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.l2jmobius.gameserver.model.zone.form.ZoneNPoly;
import org.l2jmobius.gameserver.model.zone.type.NpcSpawnTerritory;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeAuthority;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.*;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeBuilder;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomFarm074Checks;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint;

public final class PhantomEncounterLocalSourceChecks
{
	public static void main(String[] args) throws Exception
	{
		if (args.length == 1 && args[0].equals("spawn-sentinel"))
		{
			final boolean previous = org.l2jmobius.gameserver.config.custom.RandomSpawnsConfig.ENABLE_RANDOM_MONSTER_SPAWNS;
			try
			{
				for (boolean enabled : List.of(false, true))
				{
					org.l2jmobius.gameserver.config.custom.RandomSpawnsConfig.ENABLE_RANDOM_MONSTER_SPAWNS = enabled;
					final var spawn = new org.l2jmobius.gameserver.model.spawns.Spawn(null);
					final var location = new org.l2jmobius.gameserver.model.Location(0, 0, 0);
					for (int heading : new int[] { -1, 0, 1, 32768, 65535, 65536, -2, Integer.MIN_VALUE, Integer.MAX_VALUE })
					{
						spawn.setHeading(heading); location.setHeading(heading);
						check(spawn.getHeading() == (heading == -1 ? -1 : location.getHeading()), "Spawn heading sentinel/clamp contract changed: " + heading);
					}
				}
			}
			finally { org.l2jmobius.gameserver.config.custom.RandomSpawnsConfig.ENABLE_RANDOM_MONSTER_SPAWNS = previous; }
			System.out.println("NATIVE_SPAWN_SENTINEL_GREEN values=9 randomFlags=2 ordinaryLocation=UNCHANGED");
			return;
		}
		final String path = "data/spawns/Aden/SilentValley.xml";
		final var document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(Path.of(path).toFile());
		final var spawn = (Element) document.getElementsByTagName("spawn").item(0);
		final var territory = (Element) spawn.getElementsByTagName("territory").item(0);
		final var nodes = territory.getElementsByTagName("node"); final int[] x = new int[nodes.getLength()], y = new int[nodes.getLength()];
		for (int i = 0; i < x.length; i++) { final var node = (Element) nodes.item(i); x[i] = Integer.parseInt(node.getAttribute("x")); y[i] = Integer.parseInt(node.getAttribute("y")); }
		final var nativeTerritory = new NpcSpawnTerritory(spawn.getAttribute("zone"), new ZoneNPoly(x, y, Integer.parseInt(territory.getAttribute("minZ")), Integer.parseInt(territory.getAttribute("maxZ"))), path);
		final var observed = nativeTerritory.geometrySnapshot().orElseThrow();
		final var polygon = new TerritoryPolygon(observed.main().vertices().stream().map(v -> new TerritoryVertex(v.x(), v.y())).toList(), observed.main().lowZ(), observed.main().highZ());
		final var geometry = new TerritoryGeometry(observed.territoryName(), path, polygon, List.of(), observed.hash());
		final var npcElement = (Element) spawn.getElementsByTagName("npc").item(0);
		final int npc = Integer.parseInt(npcElement.getAttribute("id"));
		final var fact = new SpawnFact(npc, 0, 0, 0, 0, 0, Integer.parseInt(npcElement.getAttribute("count")), 0, SpawnPointKind.TERRITORY_POLYGON, null, null, PhantomGameKnowledgeAuthority.SERVER_LOADER_FACT, geometry);
		final var target = new PhantomTopologyPoint((int) java.util.Arrays.stream(x).average().orElseThrow(), (int) java.util.Arrays.stream(y).average().orElseThrow(), (polygon.lowZ() + polygon.highZ()) / 2, 0);
		final var actor = new PhantomTopologyPoint(target.x() + 180, target.y(), target.z(), 0);
		final var behaviorFixture = new PhantomEncounterLocalSource.SpawnBehavior(0, false, 0);
		final var binding = PhantomEncounterLocalSource.bind(fact, observed, actor, target, behaviorFixture).orElseThrow(() -> new AssertionError("RED: canonical wide SilentValley local encounter refused"));
		check(!binding.source().equals(PhantomEncounterLocalSource.bind(fact, observed, actor, target,
			new PhantomEncounterLocalSource.SpawnBehavior(1, false, 0)).orElseThrow().source()), "Changed spawn behavior retained source identity");
		check(geometry.contains(target.x(), target.y(), target.z()) && geometry.contains(actor.x(), actor.y(), actor.z()), "Fixture must use real source geometry");
		final var intent = PhantomEncounterLocalSource.goal(110011, binding);
		check(binding.source().equals(org.l2jmobius.gameserver.phantoms.party.PhantomAutonomousFarmParty.farmSource(intent))
			&& org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundGoalSpec.parse(intent).npcId() == npc, "Local native intent did not round-trip through ordinary Party source parser");
		final var wrong = new NpcSpawnTerritory(observed.territoryName(), new ZoneNPoly(x, y, polygon.lowZ(), polygon.highZ()), "data/spawns/wrong.xml").geometrySnapshot().orElseThrow();
		check(!PhantomGameKnowledgeBuilder.localEncounterSource(fact, wrong, binding.anchor(), target), "Wrong source identity accepted");
		check(PhantomEncounterLocalSource.worldReady(binding, _ -> true, () -> true), "Positive world guards refused");
		check(!PhantomEncounterLocalSource.worldReady(binding, _ -> false, () -> true), "Geo/LOS failure accepted");
		check(!PhantomEncounterLocalSource.worldReady(binding, _ -> true, () -> PhantomTrainingScenarios.factionClear(Set.of(1), 300, 10, target,
			List.of(new PhantomTrainingScenarios.ClanNeighbor(target, Set.of(1))))), "Actual faction radius guard failure accepted");
		final var planner = PhantomFarm074Checks.planner(true);
		final var before = planner.rendezvousSources(74, PhantomFarm074Checks.state(), PhantomFarm074Checks.goal(), 740074, 1, Set.of(), Set.of());
		check(planner.partyNativeTarget(new PhantomDomainRef("background.farm", "101@farm.local"), 101, new PhantomTopologyPoint(100, 100, 0, 0)), "Existing successful FARMING source regressed");
		check(!planner.partyNativeTarget(binding.source(), npc, target), "Local witness leaked into global farm admission");
		check(before.equals(planner.rendezvousSources(74, PhantomFarm074Checks.state(), PhantomFarm074Checks.goal(), 740074, 1, Set.of(), Set.of())), "Global planner changed after local binding");
		System.out.println("ENCOUNTER_LOCAL_SOURCE_GREEN canonicalSilentValley; existingFarm; wrongSource; geoLosFaction; globalUnchanged");
	}
	/** Isolated lifecycle contracts before any encounter actor/CUT, never game outcomes. */
	static void nativeSpawns(PhantomEncounterLocalSource.Binding binding, Path directory) throws Exception
	{
		final boolean previous = org.l2jmobius.gameserver.config.custom.RandomSpawnsConfig.ENABLE_RANDOM_MONSTER_SPAWNS;
		final var previousExemptions = org.l2jmobius.gameserver.config.custom.RandomSpawnsConfig.MOBS_LIST_NOT_RANDOM;
		int cases = 0;
		try
		{
			// A disabled installation need not initialize this set. The enabled probe is explicitly a non-exempt fixture.
			if (previousExemptions == null) { org.l2jmobius.gameserver.config.custom.RandomSpawnsConfig.MOBS_LIST_NOT_RANDOM = Set.of(); }
			for (boolean enabled : List.of(false, true))
			{
				org.l2jmobius.gameserver.config.custom.RandomSpawnsConfig.ENABLE_RANDOM_MONSTER_SPAWNS = enabled;
				for (String mode : List.of("TERRITORY", "SENTINEL", "ZERO", "FIXED"))
				{
					final var spawn = PhantomEncounterLocalSource.realize(binding);
					final var origin = PhantomEncounterLocalSource.origin(binding.fact()).orElseThrow();
					final var recording = mode.equals("TERRITORY") ? new RecordingTerritory(origin.getSpawnTerritory()) : null;
					if (recording != null) { spawn.setSpawnTerritory(recording); }
					else { spawn.setHeading(mode.equals("SENTINEL") ? -1 : mode.equals("ZERO") ? 0 : 32768); }
					org.l2jmobius.gameserver.model.actor.Npc npc = null;
					final var fact = new java.util.LinkedHashMap<String, Object>();
					fact.put("mode", mode); fact.put("randomSpawns", enabled); fact.put("afterCut", false); fact.put("trainingCredit", false);
					try
					{
						npc = spawn.doSpawn(false); spawn.stopRespawn();
						check(npc != null && npc.isSpawned() && npc.getSpawn() == spawn, "Ordinary native spawn failed");
						fact.put("initial", point(npc)); fact.put("spawnHeading", spawn.getHeading());
						final int initializedHeading = spawn.getHeading();
						if (recording != null)
						{
							check(initializedHeading == -1 && npc.getX() == recording.last.getX() && npc.getY() == recording.last.getY(), "Territory acquired an extra random placement");
							fact.put("territoryDraw", recording.last.toString());
						}
						else
						{
							fact.put("cachedSource", spawn.getSpawnLocation().toString());
							if (!enabled || mode.equals("SENTINEL")) { check(sameXY(npc, binding.target()), "Sentinel/disabled random spawns moved exact NPC"); }
							else { check(randomBounds(npc, binding.target()), "Ordinary random spawn exceeded unchanged native range"); }
						}
						// Explicit native lifecycle invocation; no damage/death/reward or queued respawn task.
						npc.onDecay(); spawn.startRespawn(); spawn.respawnNpc(npc); spawn.stopRespawn();
						check(npc.isSpawned() && !npc.isAlikeDead() && spawn.getLastSpawn() == npc, "Ordinary native respawn failed");
						fact.put("respawn", point(npc));
						if (recording != null) { check(spawn.getHeading() == -1 && npc.getX() == recording.last.getX() && npc.getY() == recording.last.getY(), "Territory respawn acquired a second offset"); }
						else if (!enabled || initializedHeading == -1) { check(sameXY(npc, binding.target()), "Sentinel respawn left cached native source"); }
						else { check(randomBounds(npc, binding.target()), "Ordinary respawn exceeded native range"); }
						fact.put("pass", true); cases++;
					}
					finally
					{
						spawn.stopRespawn(); if (npc != null) { npc.deleteMe(); check(!npc.isSpawned(), "Probe NPC leaked into encounter world"); }
						java.nio.file.Files.writeString(directory.resolve("native-spawn-contract.jsonl"), PhantomLearning005Evaluation.mapJson(fact) + "\n", java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
					}
				}
			}
		}
		finally
		{
			org.l2jmobius.gameserver.config.custom.RandomSpawnsConfig.ENABLE_RANDOM_MONSTER_SPAWNS = previous;
			org.l2jmobius.gameserver.config.custom.RandomSpawnsConfig.MOBS_LIST_NOT_RANDOM = previousExemptions;
		}
		check(org.l2jmobius.gameserver.config.custom.RandomSpawnsConfig.ENABLE_RANDOM_MONSTER_SPAWNS == previous
			&& org.l2jmobius.gameserver.config.custom.RandomSpawnsConfig.MOBS_LIST_NOT_RANDOM == previousExemptions, "Native config fixture not restored");
		java.nio.file.Files.writeString(directory.resolve("native-spawn-contract.complete.json"), "{\"cases\":" + cases + ",\"passed\":true,\"configFlagRestored\":true,\"afterCut\":false,\"trainingCredit\":false}\n", java.nio.file.StandardOpenOption.CREATE_NEW);
	}
	private static boolean sameXY(org.l2jmobius.gameserver.model.actor.Npc npc, PhantomTopologyPoint point) { return npc.getX() == point.x() && npc.getY() == point.y(); }
	private static boolean randomBounds(org.l2jmobius.gameserver.model.actor.Npc npc, PhantomTopologyPoint point)
	{
		final int dx = npc.getX() - point.x(), dy = npc.getY() - point.y();
		return dx >= org.l2jmobius.gameserver.config.custom.RandomSpawnsConfig.MOB_MIN_SPAWN_RANGE && dx <= org.l2jmobius.gameserver.config.custom.RandomSpawnsConfig.MOB_MAX_SPAWN_RANGE
			&& dy >= org.l2jmobius.gameserver.config.custom.RandomSpawnsConfig.MOB_MIN_SPAWN_RANGE && dy <= org.l2jmobius.gameserver.config.custom.RandomSpawnsConfig.MOB_MAX_SPAWN_RANGE;
	}
	private static String point(org.l2jmobius.gameserver.model.actor.Npc npc) { return new PhantomTopologyPoint(npc.getX(), npc.getY(), npc.getZ(), npc.getInstanceId()).toString(); }
	private static final class RecordingTerritory extends NpcSpawnTerritory
	{
		private final NpcSpawnTerritory delegate;
		private org.l2jmobius.gameserver.model.Location last;
		RecordingTerritory(NpcSpawnTerritory origin) { this(origin, origin.geometrySnapshot().orElseThrow()); }
		private RecordingTerritory(NpcSpawnTerritory origin, NpcSpawnTerritory.GeometrySnapshot geometry)
		{
			super(geometry.territoryName(), zone(geometry.main()), geometry.sourcePath()); delegate = origin;
			for (var banned : geometry.banned()) { addBannedTerritory(zone(banned)); }
		}
		@Override public org.l2jmobius.gameserver.model.Location getRandomPoint()
		{
			final var result = delegate.getRandomPoint(); last = new org.l2jmobius.gameserver.model.Location(result.getX(), result.getY(), result.getZ(), result.getHeading()); return result;
		}
		@Override public int getHighZ() { return delegate.getHighZ(); }
		private static ZoneNPoly zone(NpcSpawnTerritory.PolygonGeometry geometry)
		{ return new ZoneNPoly(geometry.vertices().stream().mapToInt(NpcSpawnTerritory.Vertex::x).toArray(), geometry.vertices().stream().mapToInt(NpcSpawnTerritory.Vertex::y).toArray(), geometry.lowZ(), geometry.highZ()); }
	}
	private static void check(boolean value, String message) { if (!value) { throw new AssertionError(message); } }
}
