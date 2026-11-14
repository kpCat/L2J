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
	record Binding(SpawnFact fact, PhantomTopologyAnchor anchor, PhantomTopologyPoint target)
	{
		PhantomDomainRef source() { return new PhantomDomainRef("background.farm", fact.npcId() + "@" + anchor.id()); }
	}
	static Optional<Binding> bind(SpawnFact fact, GeometrySnapshot observed, PhantomTopologyPoint actor, PhantomTopologyPoint target)
	{
		if (fact == null || fact.territoryGeometry() == null || actor == null || target == null) { return Optional.empty(); }
		final String identity = PhantomTrainingScenarios.hash(fact + "/" + actor + "/" + target);
		final var anchor = new PhantomTopologyAnchor(PREFIX + identity, PhantomTopologyAnchorRole.FARMING, PREFIX + fact.territoryGeometry().geometryHash(),
			actor, fact.npcId(), null, 0, List.of("local-encounter"), List.of(fact.territoryGeometry().sourcePath()));
		return PhantomGameKnowledgeBuilder.localEncounterSource(fact, observed, anchor, target) ? Optional.of(new Binding(fact, anchor, target)) : Optional.empty();
	}
	static boolean worldReady(Binding binding, Predicate<PhantomTopologyPoint> geometry, BooleanSupplier faction)
	{ return binding != null && geometry.test(binding.target()) && geometry.test(binding.anchor().point()) && faction.getAsBoolean(); }
	static boolean local(String source) { return source.contains("@" + PREFIX); }
	/** Ordinary native Party intent shape, not permission for global background simulation. */
	static org.l2jmobius.gameserver.phantoms.decision.PhantomGoal goal(org.l2jmobius.gameserver.phantoms.decision.PhantomGoal previous, Binding binding)
	{
		return new org.l2jmobius.gameserver.phantoms.decision.PhantomGoal(previous.goalId(), "farm.background",
			org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus.ACTIVE, previous.subject(),
			new PhantomDomainRef("npc", Integer.toString(binding.fact().npcId())), 1, 0, "background.farm", List.of(binding.source()),
			new PhantomDomainRef("topology.anchor", binding.anchor().id()), "farm.background", 500, 0, 0, 0,
			previous.constraints(), "encounter.local.source", Math.addExact(previous.revision(), 1));
	}
	static Optional<GeometrySnapshot> observed(SpawnFact fact)
	{
		return SpawnTable.getInstance().getSpawnTable().getOrDefault(fact.npcId(), java.util.Set.of()).stream()
			.filter(spawn -> spawn.getInstanceId() == fact.instanceId() && spawn.getAmount() > 0 && spawn.getRespawnMaxDelay() > 0 && spawn.getSpawnTerritory() != null)
			.flatMap(spawn -> spawn.getSpawnTerritory().geometrySnapshot().stream())
			.filter(geometry -> geometry.hash().equals(fact.territoryGeometry().geometryHash())).findFirst();
	}
	static Optional<Binding> propose(PhantomBackgroundSuite.ProductionAuthorityFixture authority, Monster exemplar, PhantomTopologyPoint target)
	{
		if (exemplar.getSpawn() == null || exemplar.getSpawn().getSpawnTerritory() == null
			|| !SpawnTable.getInstance().getSpawnTable().getOrDefault(exemplar.getId(), java.util.Set.of()).contains(exemplar.getSpawn())) { return Optional.empty(); }
		final var geometry = exemplar.getSpawn().getSpawnTerritory().geometrySnapshot().orElse(null);
		if (geometry == null) { return Optional.empty(); }
		final var actor = new PhantomTopologyPoint(target.x() + 180, target.y(), target.z(), target.instanceId());
		return authority.knowledge().snapshot().spawnFacts().stream().filter(fact -> fact.npcId() == exemplar.getId() && fact.territoryGeometry() != null)
			.map(fact -> bind(fact, geometry, actor, target)).flatMap(Optional::stream).findFirst();
	}
	static Optional<Binding> resolve(PhantomBackgroundSuite.ProductionAuthorityFixture authority, PhantomTrainingScenarios.Scene scene, PhantomTopologyPoint target)
	{
		if (!local(scene.source())) { return Optional.empty(); }
		final var actor = new PhantomTopologyPoint(scene.x(), scene.y(), scene.z(), 0);
		final var result = authority.knowledge().snapshot().spawnFacts().stream().filter(fact -> fact.npcId() == scene.npc() && fact.territoryGeometry() != null)
			.flatMap(fact -> observed(fact).flatMap(geometry -> bind(fact, geometry, actor, target)).stream())
			.filter(binding -> binding.source().key().equals(scene.source())).findFirst();
		if (result.isEmpty()) { throw new IllegalStateException("AUTONOMOUS_LOCAL_SOURCE_IDENTITY_CHANGED"); }
		return result;
	}
	static boolean sourceReady(PhantomHistoricalBackgroundPlanner planner, Binding binding, PhantomDomainRef source, Monster target)
	{
		if (binding == null || target == null || target.getId() != binding.fact().npcId() || !binding.source().equals(source)) { return false; }
		final var point = new PhantomTopologyPoint(target.getX(), target.getY(), target.getZ(), target.getInstanceId());
		final var geometry = observed(binding.fact()).orElse(null);
		if (!planner.localEncounterTarget(source, binding.fact(), geometry, binding.anchor(), point)) { return false; }
		final var geo = GeoEngine.getInstance(); final var actor = binding.anchor().point();
		return worldReady(new Binding(binding.fact(), binding.anchor(), point),
			p -> geo.hasGeo(p.x(), p.y()) && geo.getHeight(p.x(), p.y(), p.z()) == p.z()
				&& geo.canSeeTarget(actor.x(), actor.y(), actor.z(), 0, point.x(), point.y(), point.z(), 0)
				&& geo.canMoveToTarget(actor.x(), actor.y(), actor.z(), point.x(), point.y(), point.z(), 0)
				&& geo.canMoveToTarget(point.x(), point.y(), point.z(), actor.x(), actor.y(), actor.z(), 0),
			() -> PhantomTrainingScenarios.factionIsolated(target, point, target));
	}
}
