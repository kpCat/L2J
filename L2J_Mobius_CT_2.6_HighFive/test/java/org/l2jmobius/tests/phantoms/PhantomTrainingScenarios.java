/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.l2jmobius.gameserver.config.RatesConfig;
import org.l2jmobius.gameserver.data.xml.SkillData;
import org.l2jmobius.gameserver.data.xml.SkillTreeData;
import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.enums.player.PlayerClass;
import org.l2jmobius.gameserver.model.actor.instance.Monster;
import org.l2jmobius.gameserver.phantoms.background.PhantomHistoricalBackgroundPlanner;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatCapabilityResolver;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatMode;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatPolicy;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
import org.l2jmobius.gameserver.phantoms.learning.NeuralPolicy;
import org.l2jmobius.gameserver.phantoms.learning.TrainingConfig;
import org.l2jmobius.gameserver.phantoms.party.PhantomAutonomousFarmParty;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyFarmEligibility;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyAnchor;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyAnchorRole;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint;

/** Selection only: every selected field still needs live Player/Party admission. */
final class PhantomTrainingScenarios
{
	static final String HEADER = "id\tsource\tnpc\tclass\tlevel\tgrade\tweaponEnchant\tx\ty\tz\tskills\tsplit";
	static record Scene(String id, String source, int npc, int classId, int level, String grade, int weaponEnchant, int x, int y, int z, List<Integer> skills, String split)
	{
		Scene { skills = List.copyOf(skills); if (weaponEnchant < 0 || weaponEnchant > 25) { throw new IllegalArgumentException("Invalid actual weapon enchant"); } }
		String row() { return String.join("\t", id, source, Integer.toString(npc), Integer.toString(classId), Integer.toString(level), grade, Integer.toString(weaponEnchant),
			Integer.toString(x), Integer.toString(y), Integer.toString(z), skills.stream().map(String::valueOf).collect(Collectors.joining(",")), split); }
		Scene withSplit(String value) { return new Scene(id, source, npc, classId, level, grade, weaponEnchant, x, y, z, skills, value); }
		static Scene parse(String row)
		{
			var p = row.split("\t", -1);
			if (p.length != 12 || !p[0].matches("[a-f0-9]{64}") || !p[1].matches("[0-9]+@[^\t\r\n]{1,180}")) { throw new IllegalArgumentException("Invalid sealed training scene"); }
			return new Scene(p[0], p[1], Integer.parseInt(p[2]), Integer.parseInt(p[3]), Integer.parseInt(p[4]), p[5], Integer.parseInt(p[6]), Integer.parseInt(p[7]),
				Integer.parseInt(p[8]), Integer.parseInt(p[9]), java.util.Arrays.stream(p[10].split(",")).map(Integer::parseInt).toList(), p[11]);
		}
	}

	static List<String> orderedKeys(List<String> keys, long seed, long ordinal)
	{
		if (ordinal < 0) { throw new IllegalArgumentException("Negative training ordinal"); }
		var ordered = keys.stream().distinct().sorted(Comparator.comparing((String key) -> hash(seed + ":" + key)).thenComparing(Comparator.naturalOrder())).toList();
		if (ordered.isEmpty()) { return List.of(); }
		int offset = Math.floorMod(ordinal, ordered.size());
		return java.util.stream.IntStream.range(0, ordered.size()).mapToObj(index -> ordered.get((index + offset) % ordered.size())).toList();
	}

	static List<Scene> discover(TrainingConfig config, PhantomBackgroundSuite.ProductionAuthorityFixture authority)
	{
		return discover(config, authority, config.seed());
	}

	static List<Scene> discover(TrainingConfig config, PhantomBackgroundSuite.ProductionAuthorityFixture authority, long catalogSeed)
	{
		var resolver = PhantomCombatCapabilityResolver.fromProgression(authority::progression);
		var planner = new PhantomHistoricalBackgroundPlanner(authority.knowledge(), authority.topology(), authority.authority());
		var geo = GeoEngine.getInstance();
		var scenes = new LinkedHashMap<String, Scene>();
		var monsters = World.getInstance().getVisibleObjects().stream().filter(Monster.class::isInstance).map(Monster.class::cast)
			.filter(npc -> npc.isSpawned() && !npc.isAlikeDead() && npc.getInstanceId() == 0 && !npc.isAggressive() && !npc.isRaid() && !npc.isChampion()
				&& npc.isTargetable() && !npc.isInvul() && !npc.hasMinions() && npc.getLeader() == null
				&& (npc.getTemplate().getClans() == null || npc.getTemplate().getClans().isEmpty())
				&& npc.getSpawn() != null && npc.getSpawn().getRespawnMaxDelay() > 0
				&& (config.npcIds().isEmpty() || config.npcIds().contains(npc.getId())))
			.sorted(Comparator.comparingDouble(Monster::getMaxHp).thenComparingInt(Monster::getId).thenComparingInt(Monster::getObjectId)).toList();
		for (int classId : config.classes())
		{
			var type = PlayerClass.getPlayerClass(classId);
			if (type == null) { skip("CLASS_UNKNOWN", Integer.toString(classId)); continue; }
			for (int level : config.levels())
			{
				var learned = new LinkedHashMap<Integer, Integer>();
				for (var entry : SkillTreeData.getInstance().getCompleteClassSkillTree(type).values())
				{
					if (entry.getGetLevel() <= level && (entry.isLearnedByNpc() || entry.isAutoGet())) { learned.merge(entry.getSkillId(), entry.getSkillLevel(), Math::max); }
				}
				var loadout = resolver.resolve(classId, PhantomCombatMode.RANGED_MAGIC, id -> learned.getOrDefault(id, 0), selected ->
					learned.getOrDefault(selected.skillId(), 0) >= selected.skillLevel()
						&& PhantomLearning001ActorFixture.eligible(SkillData.getInstance().getSkill(selected.skillId(), selected.skillLevel())), false, 4).orElse(null);
				if (loadout == null || loadout.selectedSkills().size() < 2) { skip("INSUFFICIENT_ATTACK_CAPABILITIES", classId + ":" + level); continue; }
				var skills = loadout.selectedSkills().stream().map(skill -> skill.skillId()).toList();
				var fieldsByTemplate = new LinkedHashMap<Integer, Integer>();
				var distinctFields = new java.util.HashSet<String>();
				var rosterLevels = new ArrayList<Integer>(); rosterLevels.add(level); rosterLevels.add(level);
				for (int helper = 0; helper < Math.min(3, config.maxActiveActors() - 2); helper++) { rosterLevels.add(76); }
				var rosterBand = PhantomPartyFarmEligibility.forRoster(rosterLevels).orElseThrow();
				for (var npc : monsters)
				{
					if (fieldsByTemplate.getOrDefault(npc.getId(), 0) >= 6) { continue; }
					if (scenes.size() >= 4096) { skip("CATALOG_BOUND", "4096"); break; }
					if (!rosterBand.contains(npc.getLevel()) || !geo.hasGeo(npc.getX(), npc.getY())
						|| !PhantomCombatPolicy.farmRewardAvailable(level - npc.getLevel(), RatesConfig.MONSTER_EXP_MAX_LEVEL_DIFFERENCE, npc.getExpReward(level), npc.getSpReward(level))) { continue; }
					var target = new PhantomTopologyPoint(npc.getX(), npc.getY(), npc.getZ(), 0);
					for (var anchor : authority.topology().nearestAnchors(target, PhantomTopologyAnchorRole.FARMING, 12, PhantomAutonomousFarmParty.MAXIMUM_TARGET_DISTANCE))
					{
						if (fieldsByTemplate.getOrDefault(npc.getId(), 0) >= 6) { break; }
						var source = new PhantomDomainRef("background.farm", npc.getId() + "@" + anchor.id());
						if (!planner.partyNativeTarget(source, npc.getId(), target)) { continue; }
						var area = authority.topology().findNode(anchor.nodeId()).orElseThrow().area();
						var point = preparationPoint(anchor);
						int x = point.x();
						int y = point.y();
						if (!geo.hasGeo(x, y)) { continue; }
						int z = point.z();
						if (geo.getHeight(x, y, z) != z || !area.contains(point) || org.l2jmobius.gameserver.managers.ZoneManager.getInstance().getZone(x, y, z,
							org.l2jmobius.gameserver.model.zone.type.WaterZone.class) != null || point.distance3D(target) < 100 || point.distance3D(target) > PhantomAutonomousFarmParty.MAXIMUM_TARGET_DISTANCE
							|| Math.abs(z - target.z()) > 200 || !geo.canSeeTarget(x, y, z, 0, target.x(), target.y(), target.z(), 0)
							|| !geo.canMoveToTarget(x, y, z, target.x(), target.y(), target.z(), 0) || !geo.canMoveToTarget(target.x(), target.y(), target.z(), x, y, z, 0)) { continue; }
						try { PhantomLearning001ActorFixture.requireSafePartySpawn(x, y, z); }
						catch (IllegalStateException unsafe) { continue; }
						boolean added = false;
						for (String grade : config.grades())
						{
							if (!List.of("A", "S").contains(grade)) { skip("UNSUPPORTED_STOCK_LOADOUT_GRADE", grade); continue; }
							for (int enchant : config.weaponEnchants())
							{
								if (scenes.size() >= 4096) { break; }
								String id = physicalId(source.key(), classId, level, grade, x, y, z, skills, Math.min(3, config.maxActiveActors() - 2), enchant);
								added |= scenes.putIfAbsent(id, new Scene(id, source.key(), npc.getId(), classId, level, grade, enchant, x, y, z, skills, "UNASSIGNED")) == null;
							}
						}
						if (added && distinctFields.add(source.key() + ":" + x + ":" + y + ":" + z)) { fieldsByTemplate.merge(npc.getId(), 1, Integer::sum); }
					}
				}
			}
		}
		// Round-robin real templates before filling variants; a dense field cannot consume every slot.
		var byNpc = new LinkedHashMap<Integer, List<Scene>>();
		for (var key : orderedKeys(List.copyOf(scenes.keySet()), catalogSeed, 0)) { var scene = scenes.get(key); byNpc.computeIfAbsent(scene.npc(), _ -> new ArrayList<>()).add(scene); }
		var partitioned = new LinkedHashMap<String, List<Scene>>();
		for (String partition : List.of("TRAIN", "TUNING", "AUDIT")) { partitioned.put(partition, new ArrayList<>()); }
		for (int variant = 0; ; variant++)
		{
			boolean added = false;
			for (var group : byNpc.values())
			{
				if (variant < group.size()) { var scene = group.get(variant); partitioned.get(sceneSplit(scene.id())).add(scene); added = true; }
			}
			if (!added) { break; }
		}
		var result = new ArrayList<Scene>();
		int candidateLimit = Math.min(256, config.scenarioLimit() * 4);
		for (int variant = 0; result.size() < candidateLimit; variant++)
		{
			boolean added = false;
			for (var group : partitioned.values())
			{
				if (variant < group.size()) { result.add(group.get(variant)); added = true; }
				if (result.size() == candidateLimit) { break; }
			}
			if (!added) { break; }
		}
		return List.copyOf(result);
	}
	static PhantomTopologyPoint preparationPoint(PhantomTopologyAnchor anchor) { return anchor.point(); }
	static String physicalId(String source, int classId, int level, String grade, int x, int y, int z, List<Integer> skills, int helperCount, int weaponEnchant)
	{
		String facts = source + ":" + classId + ":" + level + ":" + grade + ":" + x + ":" + y + ":" + z + ":" + skills + ":helpers=" + helperCount;
		String legacy = hash(facts);
		// The first sealed cohorts encoded this actual enchant through their fixed partition.
		int legacyEnchant = switch (sceneSplit(legacy)) { case "TRAIN" -> 0; case "TUNING" -> 4; default -> 2; };
		return weaponEnchant == legacyEnchant ? legacy : hash(facts + ":weaponEnchant=" + weaponEnchant);
	}
	static String sceneSplit(String actualScene)
	{
		int partition = Math.floorMod(actualScene.hashCode(), 5);
		return partition == 0 ? "AUDIT" : partition == 1 ? "TUNING" : "TRAIN";
	}

	static void skip(String reason, String detail) { System.out.println("TRAIN_SCENE_SKIP reason=" + reason + " detail=" + detail); }
	static String hash(String value) { return NeuralPolicy.sha256(value.getBytes(StandardCharsets.UTF_8)); }
	private PhantomTrainingScenarios() { }
}
