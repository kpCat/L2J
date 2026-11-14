/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

import org.l2jmobius.gameserver.model.zone.type.NpcSpawnTerritory.GeometrySnapshot;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeBuilder;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.SpawnFact;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyAnchor;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyAnchorRole;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint;
import org.l2jmobius.gameserver.data.SpawnTable;
import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.model.actor.instance.Monster;
import org.l2jmobius.gameserver.phantoms.background.PhantomHistoricalBackgroundPlanner;

/** Local witness shared by acquired prepare/live fixtures; never installed in global topology. */
final class PhantomEncounterLocalSource
{
	static final String PREFIX = "encounter.local.";
	record SpawnBehavior(int heading, boolean randomWalking, int chaseRange)
	{
		static SpawnBehavior observed(org.l2jmobius.gameserver.model.spawns.Spawn spawn)
		{ return new SpawnBehavior(spawn.getHeading(), spawn.getRandomWalking(), spawn.getChaseRange()); }
	}
	record Binding(SpawnFact fact, PhantomTopologyAnchor anchor, PhantomTopologyPoint target, SpawnBehavior behavior)
	{
		PhantomDomainRef source() { return new PhantomDomainRef("background.farm", fact.npcId() + "@" + anchor.id()); }
	}
	static Optional<Binding> bind(SpawnFact fact, GeometrySnapshot observed, PhantomTopologyPoint actor, PhantomTopologyPoint target, SpawnBehavior behavior)
	{
		if (fact == null || fact.territoryGeometry() == null || actor == null || target == null || behavior == null) { return Optional.empty(); }
		final String identity = PhantomTrainingScenarios.hash(fact + "/" + actor + "/" + target + "/" + behavior);
		final var anchor = new PhantomTopologyAnchor(PREFIX + identity, PhantomTopologyAnchorRole.FARMING, PREFIX + fact.territoryGeometry().geometryHash(),
			actor, fact.npcId(), null, 0, List.of("local-encounter"), List.of(fact.territoryGeometry().sourcePath()));
		return PhantomGameKnowledgeBuilder.localEncounterSource(fact, observed, anchor, target) ? Optional.of(new Binding(fact, anchor, target, behavior)) : Optional.empty();
	}
	static boolean worldReady(Binding binding, Predicate<PhantomTopologyPoint> geometry, BooleanSupplier faction)
	{ return binding != null && geometry.test(binding.target()) && geometry.test(binding.anchor().point()) && faction.getAsBoolean(); }
	static boolean local(String source) { return source.contains("@" + PREFIX); }
	/** Ordinary native Party intent shape, not permission for global background simulation. */
	static org.l2jmobius.gameserver.phantoms.decision.PhantomGoal goal(long profile, Binding binding)
	{
		return new org.l2jmobius.gameserver.phantoms.decision.PhantomGoal(profile, "farm.background",
			org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus.ACTIVE, new PhantomDomainRef("profile", Long.toString(profile)),
			new PhantomDomainRef("npc", Integer.toString(binding.fact().npcId())), 1, 0, "background.farm", List.of(binding.source()),
			new PhantomDomainRef("topology.anchor", binding.anchor().id()), "farm.background", 500, 0, 0, 0,
			java.util.Map.of(), "encounter.local.source", 1);
	}
	static Optional<GeometrySnapshot> observed(SpawnFact fact)
	{
		return origin(fact).flatMap(spawn -> spawn.getSpawnTerritory().geometrySnapshot());
	}
	static Optional<org.l2jmobius.gameserver.model.spawns.Spawn> origin(SpawnFact fact)
	{
		final var matches = SpawnTable.getInstance().getSpawnTable().getOrDefault(fact.npcId(), java.util.Set.of()).stream()
			.filter(spawn -> spawn.getInstanceId() == fact.instanceId() && spawn.getAmount() > 0 && spawn.getRespawnMaxDelay() > 0 && spawn.getSpawnTerritory() != null)
			.filter(spawn -> spawn.getSpawnTerritory().geometrySnapshot().filter(geometry -> geometry.hash().equals(fact.territoryGeometry().geometryHash())).isPresent()).toList();
		return matches.isEmpty() || matches.stream().map(SpawnBehavior::observed).distinct().count() != 1 ? Optional.empty() : Optional.of(matches.getFirst());
	}
	static org.l2jmobius.gameserver.model.spawns.Spawn realize(Binding binding) throws Exception
	{
		final var origin = origin(binding.fact()).orElseThrow(() -> new IllegalStateException("AUTONOMOUS_LOCAL_SPAWN_ORIGIN_MISSING"));
		if (!binding.behavior().equals(SpawnBehavior.observed(origin))) { throw new IllegalStateException("AUTONOMOUS_LOCAL_SPAWN_BEHAVIOR_CHANGED"); }
		final var spawn = new org.l2jmobius.gameserver.model.spawns.Spawn(org.l2jmobius.gameserver.data.xml.NpcData.getInstance().getTemplate(binding.fact().npcId()));
		spawn.setXYZ(binding.target().x(), binding.target().y(), binding.target().z());
		// Preserve the native source inputs; default exact-spawn heading would enable a different RandomSpawns path.
		spawn.setHeading(origin.getHeading()); spawn.setRandomWalking(origin.getRandomWalking()); spawn.setChaseRange(origin.getChaseRange());
		return spawn;
	}
	static Optional<Binding> propose(PhantomBackgroundSuite.ProductionAuthorityFixture authority, Monster exemplar, PhantomTopologyPoint target)
	{
		if (exemplar.getSpawn() == null || exemplar.getSpawn().getSpawnTerritory() == null
			|| !SpawnTable.getInstance().getSpawnTable().getOrDefault(exemplar.getId(), java.util.Set.of()).contains(exemplar.getSpawn())) { return Optional.empty(); }
		final var geometry = exemplar.getSpawn().getSpawnTerritory().geometrySnapshot().orElse(null);
		if (geometry == null) { return Optional.empty(); }
		final var actor = new PhantomTopologyPoint(target.x() + 180, target.y(), target.z(), target.instanceId());
		return authority.knowledge().snapshot().spawnFacts().stream().filter(fact -> fact.npcId() == exemplar.getId() && fact.territoryGeometry() != null)
			.flatMap(fact -> origin(fact).filter(origin -> SpawnBehavior.observed(origin).equals(SpawnBehavior.observed(exemplar.getSpawn())))
				.flatMap(origin -> bind(fact, geometry, actor, target, SpawnBehavior.observed(origin))).stream()).findFirst();
	}
	static Optional<Binding> resolve(PhantomBackgroundSuite.ProductionAuthorityFixture authority, PhantomTrainingScenarios.Scene scene, PhantomTopologyPoint target)
	{
		if (!local(scene.source())) { return Optional.empty(); }
		final var actor = new PhantomTopologyPoint(scene.x(), scene.y(), scene.z(), 0);
		final var result = authority.knowledge().snapshot().spawnFacts().stream().filter(fact -> fact.npcId() == scene.npc() && fact.territoryGeometry() != null)
			.flatMap(fact -> origin(fact).flatMap(origin -> origin.getSpawnTerritory().geometrySnapshot()
				.flatMap(geometry -> bind(fact, geometry, actor, target, SpawnBehavior.observed(origin)))).stream())
			.filter(binding -> binding.source().key().equals(scene.source())).findFirst();
		if (result.isEmpty()) { throw new IllegalStateException("AUTONOMOUS_LOCAL_SOURCE_IDENTITY_CHANGED"); }
		return result;
	}
	static boolean sourceReady(PhantomHistoricalBackgroundPlanner planner, Binding binding, PhantomDomainRef source, Monster target)
	{
		if (binding == null || target == null || target.getId() != binding.fact().npcId() || !binding.source().equals(source)) { return false; }
		final var point = new PhantomTopologyPoint(target.getX(), target.getY(), target.getZ(), target.getInstanceId());
		final var geometry = observed(binding.fact()).orElse(null);
		if (!origin(binding.fact()).map(SpawnBehavior::observed).filter(binding.behavior()::equals).isPresent()
			|| !planner.localEncounterTarget(source, binding.fact(), geometry, binding.anchor(), point)) { return false; }
		final var geo = GeoEngine.getInstance(); final var actor = binding.anchor().point();
		return worldReady(new Binding(binding.fact(), binding.anchor(), point, binding.behavior()),
			p -> geo.hasGeo(p.x(), p.y()) && geo.getHeight(p.x(), p.y(), p.z()) == p.z()
				&& geo.canSeeTarget(actor.x(), actor.y(), actor.z(), 0, point.x(), point.y(), point.z(), 0)
				&& geo.canMoveToTarget(actor.x(), actor.y(), actor.z(), point.x(), point.y(), point.z(), 0)
				&& geo.canMoveToTarget(point.x(), point.y(), point.z(), actor.x(), actor.y(), actor.z(), 0),
			() -> PhantomTrainingScenarios.factionIsolated(target, point, target));
	}
	static java.util.Map<String, Object> diagnostics(Binding binding, Monster target)
	{
		final var result = new java.util.LinkedHashMap<String, Object>();
		result.put("source", binding.source().key()); result.put("sourceGeometry", binding.fact().territoryGeometry().geometryHash());
		result.put("pinnedBehavior", binding.behavior().toString()); result.put("liveOriginBehavior", origin(binding.fact()).map(SpawnBehavior::observed).map(Object::toString).orElse("MISSING"));
		result.put("plannedTarget", binding.target().toString()); result.put("actualTarget", new PhantomTopologyPoint(target.getX(), target.getY(), target.getZ(), target.getInstanceId()).toString());
		result.put("actualSpawn", target.getSpawn() == null ? "MISSING" : new PhantomTopologyPoint(target.getSpawn().getX(), target.getSpawn().getY(), target.getSpawn().getZ(), target.getSpawn().getInstanceId()).toString());
		result.put("realizedBehavior", target.getSpawn() == null ? "MISSING" : SpawnBehavior.observed(target.getSpawn()).toString());
		result.put("randomSpawnsEnabled", org.l2jmobius.gameserver.config.custom.RandomSpawnsConfig.ENABLE_RANDOM_MONSTER_SPAWNS);
		result.put("afterCut", false); return java.util.Map.copyOf(result);
	}
}
