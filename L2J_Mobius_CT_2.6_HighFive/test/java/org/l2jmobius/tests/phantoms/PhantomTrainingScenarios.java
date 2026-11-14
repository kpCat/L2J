/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.l2jmobius.gameserver.config.RatesConfig;
import org.l2jmobius.gameserver.data.xml.SkillData;
import org.l2jmobius.gameserver.data.xml.SkillTreeData;
import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Attackable;
import org.l2jmobius.gameserver.model.actor.enums.player.PlayerClass;
import org.l2jmobius.gameserver.model.actor.instance.Monster;
import org.l2jmobius.gameserver.phantoms.background.PhantomHistoricalBackgroundPlanner;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatCapabilityResolver;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatMode;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatPolicy;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
import org.l2jmobius.gameserver.phantoms.learning.NeuralPolicy;
import org.l2jmobius.gameserver.phantoms.learning.TrainingConfig;
import org.l2jmobius.gameserver.phantoms.learning.AutonomousTrainingV1;
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
	{ return discover(config,authority,catalogSeed,Math.min(256,config.scenarioLimit()*4)); }
	static List<Scene> discover(TrainingConfig config, PhantomBackgroundSuite.ProductionAuthorityFixture authority, long catalogSeed, int candidateLimit)
	{
		if (candidateLimit < 1 || candidateLimit > 256) { throw new IllegalArgumentException("Bounded native scenario catalog required"); }
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
	/** Actual acquired proposal only. This path never forecasts learned ranks or provisions a loadout. */
	static PhantomCombatCapabilityResolver frozenResolver(int actorClass, PhantomCombatMode mode,
		List<org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.SkillRef> shortlist,
		java.util.function.Supplier<org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionCatalog> fallback)
	{
		if (PlayerClass.getPlayerClass(actorClass) == null || mode == null || shortlist.isEmpty() || shortlist.size() > 4
			|| shortlist.stream().map(value -> value.skillId()).distinct().count() != shortlist.size()) { throw new IllegalArgumentException("AUTONOMOUS_FROZEN_RESOLVER_INPUT"); }
		final var exact = List.copyOf(shortlist);
		return new PhantomCombatCapabilityResolver(classId ->
		{
			if (classId == actorClass)
			{
				return List.of(new PhantomCombatCapabilityResolver.CapabilityEvidence(mode.capabilityKey(), "autonomous-frozen", 1,
					exact.stream().map(ref -> new org.l2jmobius.gameserver.phantoms.combat.PhantomCombatLoadout.SelectedSkill(ref.skillId(), ref.skillLevel())).toList()));
			}
			final var catalog = fallback.get();
			return catalog == null ? List.of() : catalog.capabilities(classId).stream().map(fact -> new PhantomCombatCapabilityResolver.CapabilityEvidence(fact.capabilityKey(), fact.variantKey(), fact.rank(),
				List.of(new org.l2jmobius.gameserver.phantoms.combat.PhantomCombatLoadout.SelectedSkill(fact.actionSkill().skillId(), fact.actionSkill().skillLevel())))).toList();
		});
	}
	/** One data-selected acquired actor/roster; no class tree forecasting or gear provisioning. */
	static record AcquiredScene(Scene scene, Monster exemplar, PhantomTopologyPoint target) { }
	static <T> List<T> lawfulPage(java.util.stream.Stream<T> ordered, java.util.function.Predicate<T> lawful, int maximum, java.util.function.BooleanSupplier requested)
	{
		if (maximum < 1) { throw new IllegalArgumentException("AUTONOMOUS_SCENE_INPUT"); }
		requirePlanningActive(requested);
		final var result = ordered.filter(value -> { requirePlanningActive(requested); return lawful.test(value); }).limit(maximum).toList();
		requirePlanningActive(requested); return result;
	}
	static void requirePlanningActive(java.util.function.BooleanSupplier requested)
	{ if (requested.getAsBoolean()) { throw new IllegalStateException("AUTONOMOUS_PLANNING_REQUESTED_DRAIN"); } }
	static record ClanNeighbor(int x, int y, int z, int instanceId, Set<Integer> clans)
	{
		ClanNeighbor(PhantomTopologyPoint point, Set<Integer> clans) { this(point.x(), point.y(), point.z(), point.instanceId(), clans); }
	}
	static boolean inWorldBounds(int x, int y, int z)
	{
		return x >= World.WORLD_X_MIN && x <= World.WORLD_X_MAX && y >= World.WORLD_Y_MIN && y <= World.WORLD_Y_MAX
			&& z >= World.WORLD_Z_MIN && z <= World.WORLD_Z_MAX;
	}
	static List<AcquiredScene> acquiredScenes(AutonomousTrainingV1.Proposal proposal, PhantomBackgroundSuite.ProductionAuthorityFixture authority,
		List<Integer> rosterLevels, String grade, int enchant, long seed, int maximum, Map<String, Long> diagnostics,
		java.util.function.Predicate<AcquiredScene> lawful, java.util.function.BooleanSupplier requested)
	{
		if (maximum < 1 || maximum > 64 || rosterLevels.isEmpty() || !rosterLevels.contains(proposal.actor().level()) || proposal.shortlist().candidates().isEmpty())
		{ throw new IllegalArgumentException("AUTONOMOUS_SCENE_INPUT"); }
		final var band = acquiredLevelBand(rosterLevels);
		final var planner = new PhantomHistoricalBackgroundPlanner(authority.knowledge(), authority.topology(), authority.authority());
		final var geo = GeoEngine.getInstance(); final var candidates = new LinkedHashMap<String, AcquiredScene>();
		// Planning points may cross World-region boundaries. Snapshot the full current faction census here;
		// the actual clone still performs a fresh native-region check immediately before CUT.
		final var factionNeighbors = World.getInstance().getVisibleObjects().stream().filter(Attackable.class::isInstance).map(Attackable.class::cast)
			.filter(other -> other.isSpawned() && !other.isAlikeDead() && other.getInstanceId() == 0 && other.getTemplate().getClans() != null && !other.getTemplate().getClans().isEmpty())
			.map(other -> new ClanNeighbor(other.getX(), other.getY(), other.getZ(), 0, other.getTemplate().getClans())).toList();
		// Order by frozen identity/seed, never by outcomes, score or speed of an easy victory.
		final var monsters = World.getInstance().getVisibleObjects().stream().filter(Monster.class::isInstance).map(Monster.class::cast)
			.filter(npc -> npc.isSpawned() && !npc.isAlikeDead() && npc.getInstanceId() == 0 && !npc.isAggressive() && !npc.isRaid() && !npc.isChampion()
				&& npc.isTargetable() && !npc.isInvul() && !npc.hasMinions() && npc.getLeader() == null
				&& npc.getSpawn() != null && npc.getSpawn().getRespawnMaxDelay() > 0)
			.sorted(Comparator.comparing((Monster npc) -> hash(seed + "/" + npc.getId() + "/" + npc.getX() + "/" + npc.getY() + "/" + npc.getZ()))
				.thenComparingInt(Monster::getId).thenComparingInt(Monster::getObjectId)).toList();
		final var fieldsPerNpc = new java.util.HashMap<Integer, Integer>();
		for (var npc : monsters)
		{
			requirePlanningActive(requested);
			if (candidates.size() >= maximum) { break; }
			if (!inWorldBounds(npc.getX(), npc.getY(), npc.getZ())) { diagnostics.merge("OUT_OF_WORLD_TEMPLATE_REFUSED", 1L, Long::sum); continue; }
			diagnostics.merge("SAFE_TEMPLATE_SEEN", 1L, Long::sum);
			if (fieldsPerNpc.getOrDefault(npc.getId(), 0) >= 2 || !band.contains(npc.getLevel()) || !geo.hasGeo(npc.getX(), npc.getY())
				|| !PhantomCombatPolicy.farmRewardAvailable(proposal.actor().level() - npc.getLevel(), RatesConfig.MONSTER_EXP_MAX_LEVEL_DIFFERENCE,
					npc.getExpReward(proposal.actor().level()), npc.getSpReward(proposal.actor().level()))) { continue; }
			diagnostics.merge("LEVEL_REWARD_GEO_ACCEPTED", 1L, Long::sum);
			final var target = new PhantomTopologyPoint(npc.getX(), npc.getY(), npc.getZ(), 0);
			for (var anchor : authority.topology().nearestAnchors(target, PhantomTopologyAnchorRole.FARMING, 12, PhantomAutonomousFarmParty.MAXIMUM_TARGET_DISTANCE))
			{
				requirePlanningActive(requested);
				final var source = new PhantomDomainRef("background.farm", npc.getId() + "@" + anchor.id());
				if (!planner.partyNativeTarget(source, npc.getId(), target)) { continue; }
				diagnostics.merge("CANONICAL_ANCHOR_ACCEPTED", 1L, Long::sum);
				final var area = authority.topology().findNode(anchor.nodeId()).orElseThrow().area(); final var point = preparationPoint(anchor);
				if (!geo.hasGeo(point.x(), point.y()) || geo.getHeight(point.x(), point.y(), point.z()) != point.z() || !area.contains(point)
					|| org.l2jmobius.gameserver.managers.ZoneManager.getInstance().getZone(point.x(), point.y(), point.z(), org.l2jmobius.gameserver.model.zone.type.WaterZone.class) != null
					|| point.distance3D(target) < 100 || point.distance3D(target) > PhantomAutonomousFarmParty.MAXIMUM_TARGET_DISTANCE || Math.abs(point.z() - target.z()) > 200
					|| !geo.canSeeTarget(point.x(), point.y(), point.z(), 0, target.x(), target.y(), target.z(), 0)
					|| !geo.canMoveToTarget(point.x(), point.y(), point.z(), target.x(), target.y(), target.z(), 0)
					|| !geo.canMoveToTarget(target.x(), target.y(), target.z(), point.x(), point.y(), point.z(), 0)) { continue; }
				try { PhantomLearning001ActorFixture.requireSafePartySpawn(point.x(), point.y(), point.z()); }
				catch (IllegalStateException unsafe) { diagnostics.merge("ANCHOR_AGGRO_REFUSED", 1L, Long::sum); continue; }
				PhantomTopologyPoint isolated = null;
				for (var desired : acquiredTargetPoints(npc, anchor, seed))
				{
					requirePlanningActive(requested);
					if (!area.contains(desired) || !planner.partyNativeTarget(source, npc.getId(), desired) || point.distance3D(desired) < 100
						|| !geo.canSeeTarget(point.x(), point.y(), point.z(), 0, desired.x(), desired.y(), desired.z(), 0)
						|| !geo.canMoveToTarget(point.x(), point.y(), point.z(), desired.x() + 180, desired.y(), desired.z(), 0)
						|| !geo.canMoveToTarget(desired.x() + 180, desired.y(), desired.z(), desired.x(), desired.y(), desired.z(), 0)
						|| !geo.canMoveToTarget(desired.x(), desired.y(), desired.z(), desired.x() + 180, desired.y(), desired.z(), 0)
						|| !geo.hasGeo(desired.x() + 180, desired.y()) || geo.getHeight(desired.x() + 180, desired.y(), desired.z()) != desired.z()
						|| !factionClear(npc.getTemplate().getClans(), npc.getTemplate().getClanHelpRange(), npc.getTemplate().getCollisionRadius(), desired, factionNeighbors)) { continue; }
					try
					{
						PhantomLearning001ActorFixture.requireSafePartySpawn(desired.x() + 180, desired.y(), desired.z());
						PhantomLearning001ActorFixture.requireSafePartySpawn(desired.x() + 180 + (rosterLevels.size() - 1) * 20, desired.y(), desired.z());
					}
					catch (IllegalStateException unsafe) { continue; }
					isolated = desired; break;
				}
				if (isolated == null) { diagnostics.merge("NO_ISOLATED_TARGET_POINT", 1L, Long::sum); continue; }
				final String id = hash(acquiredSceneId(proposal, source.key(), npc.getId(), point.x(), point.y(), point.z(), grade, enchant) + "/target/" + isolated);
				final var scene = new Scene(id, source.key(), npc.getId(), proposal.actor().classId(), proposal.actor().level(), grade, enchant,
					point.x(), point.y(), point.z(), proposal.shortlist().candidates().stream().map(skill -> skill.skillId()).toList(), "UNASSIGNED");
				final var admitted = lawfulPage(java.util.stream.Stream.of(new AcquiredScene(scene, npc, isolated)), lawful, 1, requested);
				if (admitted.isEmpty()) { diagnostics.merge("TARGET_BOUND_SCENE_REFUSED", 1L, Long::sum); break; }
				if (candidates.putIfAbsent(id, admitted.getFirst()) == null)
				{ fieldsPerNpc.merge(npc.getId(), 1, Integer::sum); diagnostics.merge("ISOLATED_SCENE_ACCEPTED", 1L, Long::sum); }
				break;
			}
		}
		requirePlanningActive(requested);
		return orderedKeys(List.copyOf(candidates.keySet()), seed, 0).stream().map(candidates::get).toList();
	}
	private static List<PhantomTopologyPoint> acquiredTargetPoints(Monster npc, PhantomTopologyAnchor anchor, long seed)
	{
		if (npc.getTemplate().getClans() == null || npc.getTemplate().getClans().isEmpty()) { return List.of(new PhantomTopologyPoint(npc.getX(), npc.getY(), npc.getZ(), 0)); }
		final var result = new ArrayList<PhantomTopologyPoint>(); final var geo = GeoEngine.getInstance();
		for (int radius : List.of(400, 700, 1000))
		{
			for (int dx = -1; dx <= 1; dx++) { for (int dy = -1; dy <= 1; dy++)
			{
				if (dx == 0 && dy == 0) { continue; }
				final int x = anchor.point().x() + dx * radius, y = anchor.point().y() + dy * radius;
				if (!geo.hasGeo(x, y)) { continue; }
				final int z = geo.getHeight(x, y, npc.getZ());
				if (!inWorldBounds(x, y, z)) { continue; }
				final var point = new PhantomTopologyPoint(x, y, z, 0);
				if (point.distance3D(anchor.point()) <= PhantomAutonomousFarmParty.MAXIMUM_TARGET_DISTANCE && Math.abs((long) point.z() - anchor.point().z()) <= 200) { result.add(point); }
			} }
		}
		return result.stream().sorted(Comparator.comparing(point -> hash(seed + "/isolated-target/" + point))).toList();
	}
	/** Conservative pre-CUT placement proof. Native faction AI and all post-CUT threats remain ordinary. */
	static boolean factionClear(Set<Integer> clans, int helpRange, int collision, PhantomTopologyPoint point, List<ClanNeighbor> neighbors)
	{
		if (clans == null || clans.isEmpty()) { return true; }
		final long radius = Math.addExact(Math.addExact((long) helpRange, collision), 300L);
		if (helpRange < 0 || collision < 0) { throw new IllegalArgumentException("Invalid native faction radius"); }
		return neighbors.stream().noneMatch(other -> other.instanceId() == point.instanceId() && other.clans().stream().anyMatch(clans::contains)
			&& Math.hypot((long) other.x() - point.x(), (long) other.y() - point.y()) <= radius);
	}
	static boolean factionIsolated(Monster exemplar, PhantomTopologyPoint point, Monster ignored)
	{
		final var template = exemplar.getTemplate(); if (template.getClans() == null || template.getClans().isEmpty()) { return true; }
		final int queryRange = Math.addExact(PhantomAutonomousFarmParty.MAXIMUM_TARGET_DISTANCE * 2 + 900, Math.addExact(template.getClanHelpRange(), template.getCollisionRadius()));
		final var neighbors = new ArrayList<>(World.getInstance().getVisibleObjectsInRange(exemplar, Attackable.class, queryRange).stream()
			.filter(other -> other != ignored && other.isSpawned() && !other.isAlikeDead() && other.getInstanceId() == point.instanceId())
			.map(other -> new ClanNeighbor(other.getX(), other.getY(), other.getZ(), other.getInstanceId(), other.getTemplate().getClans() == null ? Set.of() : other.getTemplate().getClans())).toList());
		if (exemplar != ignored) { neighbors.add(new ClanNeighbor(exemplar.getX(), exemplar.getY(), exemplar.getZ(), exemplar.getInstanceId(), template.getClans())); }
		return factionClear(template.getClans(), template.getClanHelpRange(), template.getCollisionRadius(), point, neighbors);
	}
	/** Dispatch identity contains source binding; the separate pre-CUT comparison key excludes incarnation IDs. */
	static PhantomPartyFarmEligibility.NpcLevelBand acquiredLevelBand(List<Integer> levels)
	{
		return levels.size() == 1 ? PhantomPartyFarmEligibility.forMember(levels.getFirst())
			: PhantomPartyFarmEligibility.forRoster(levels).orElseThrow(() -> new IllegalStateException("AUTONOMOUS_ROSTER_LEVEL_BAND"));
	}
	static String acquiredSceneId(AutonomousTrainingV1.Proposal proposal, String source, int npc, int x, int y, int z, String grade, int enchant)
	{
		return hash(AutonomousTrainingV1.VERSION + "/" + proposal.actor().sourceSnapshotSha() + "/" + proposal.actor().classId() + "/" + proposal.actor().level()
			+ "/" + proposal.actor().classIndex() + "/" + proposal.pool().key() + "/" + proposal.shortlist().candidates() + "/" + source + "/" + npc + "/" + x + "/" + y + "/" + z + "/" + grade + "/" + enchant);
	}
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
