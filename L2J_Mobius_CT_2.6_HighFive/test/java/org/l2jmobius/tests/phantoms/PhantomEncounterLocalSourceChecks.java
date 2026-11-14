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
		final var binding = PhantomEncounterLocalSource.bind(fact, observed, actor, target).orElseThrow(() -> new AssertionError("RED: canonical wide SilentValley local encounter refused"));
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
	private static void check(boolean value, String message) { if (!value) { throw new AssertionError(message); } }
}
