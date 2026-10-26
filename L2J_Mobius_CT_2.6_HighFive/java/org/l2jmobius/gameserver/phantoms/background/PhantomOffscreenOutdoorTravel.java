/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.background;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.l2jmobius.gameserver.phantoms.activity.PhantomOffscreenObserverAdmission;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoal;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.TargetFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.SpawnFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.SpawnPointKind;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyAnchorRole;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyAnchor;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyArea;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyNodeKind;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyQuery;
import org.l2jmobius.gameserver.model.actor.enums.creature.InstanceType;
import org.l2jmobius.gameserver.model.zone.ZoneType;
import org.l2jmobius.gameserver.model.zone.type.EffectZone;
import org.l2jmobius.gameserver.model.zone.type.FishingZone;
import org.l2jmobius.gameserver.model.zone.type.NoLandingZone;
import org.l2jmobius.gameserver.model.zone.type.NoStoreZone;
import org.l2jmobius.gameserver.model.zone.type.TownZone;
import org.l2jmobius.gameserver.model.zone.type.PeaceZone;
import org.l2jmobius.gameserver.model.zone.type.RespawnZone;
import org.l2jmobius.gameserver.model.zone.type.MotherTreeZone;

/** A background intent and scalar clock, never a certified edge or a native WALK. */
public final class PhantomOffscreenOutdoorTravel
{
	public enum Mode { CERTIFIED, OFFSCREEN_OUTDOOR_PENDING }
	public static final String PENDING_REASON = "background.offscreen.outdoor.pending";
	public static final long MAXIMUM_TRAVEL_MILLIS = 24 * 60 * 60_000L;
	// Historical digests use their minute identity, not scheduler ticks. This reserved
	// receipt tag survives goal replacement/restart and fences the later Player load.
	public static final long RECEIPT_TICK = 92_009_202;
	private PhantomOffscreenOutdoorTravel() { }

	@FunctionalInterface
	public interface ObserverAdmission
	{
		PhantomOffscreenObserverAdmission.Verdict evaluate(long profileId, PhantomBackgroundState.Position source, PhantomBackgroundState.Position destination);
	}

	public static boolean pending(PhantomGoal goal) { return PENDING_REASON.equals(goal.reasonKey()); }

	/** Topology has already validated its source refs; native Geo/identity remain separate admission. */
	public static boolean sourceBacked(PhantomTopologyQuery topology, String sourceId, TargetFact target, String destinationId)
	{
		final var source = topology.findAnchor(sourceId).orElse(null);
		final var destination = topology.findAnchor(destinationId).orElse(null);
		if (source == null || destination == null || source.id().equals(destination.id())
			|| source.point().instanceId() != 0 || destination.point().instanceId() != 0
			|| source.sourceRefs().isEmpty() || destination.sourceRefs().isEmpty()
			|| destination.role() != PhantomTopologyAnchorRole.FARMING
			|| destination.npcId() != null && destination.npcId() != target.npc().npcId()) { return false; }
		final var node = topology.findNode(destination.nodeId()).orElse(null);
		return node != null && node.kind() == PhantomTopologyNodeKind.FARMING_AREA && node.tags().contains("outdoor-farming")
			&& !node.area().contains(source.point())
			&& publicAncestry(topology, source.nodeId()) && publicAncestry(topology, destination.nodeId())
			&& target.npc().kind() == org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.NpcKind.MONSTER
			&& target.npc().attackable() && target.npc().targetable()
			&& (target.npc().exp() > 0 || target.npc().sp() > 0)
			&& target.representativeAreas().stream().anyMatch(area -> area.npcId() == target.npc().npcId()
				&& area.instanceId() == 0 && area.totalConfiguredAmount() > 0 && !area.additionalUnmappedTerritories()
				&& node.id().equals(area.topologyNodeId()));
	}

	/** Loader facts, not generated IDs/tags, own the concrete normalized arrival point.
	 * Exact polygon/source mapping is established by the existing knowledge builder;
	 * banned holes and ambiguous/overlapping geometry still require this check. */
	public static String stockRejection(PhantomTopologyQuery topology, String anchorId, int npcId, List<SpawnFact> facts, PhantomTopologyPoint point)
	{
		final var anchor = topology.findAnchor(anchorId).orElse(null);
		if (anchor == null || point.instanceId() != 0) { return "stock.anchor.absent"; }
		final var node = topology.findNode(anchor.nodeId()).orElse(null);
		if (node == null || !node.area().contains(point)) { return "stock.point.outside"; }
		final var selected = facts.stream().filter(fact -> fact.npcId() == npcId && fact.instanceId() == 0 && fact.amount() > 0
			&& anchor.nodeId().equals(fact.topologyNodeId())).toList();
		final var geometries = selected.stream().filter(fact -> fact.territoryGeometry() != null).map(SpawnFact::territoryGeometry).distinct().toList();
		if (!geometries.isEmpty())
		{
			if (geometries.size() != 1) { return "stock.geometry.ambiguous"; }
			final var geometry = geometries.getFirst();
			if (!node.sourceRefs().contains(geometry.sourcePath()) || !anchor.sourceRefs().contains(geometry.sourcePath())
				|| !geometry.contains(point.x(), point.y(), point.z())) { return "stock.geometry.mismatch"; }
			if (facts.stream().filter(fact -> fact.territoryGeometry() != null && !anchor.nodeId().equals(fact.topologyNodeId()))
				.anyMatch(fact -> fact.territoryGeometry().contains(point.x(), point.y(), point.z()))) { return "stock.geometry.overlap"; }
			return "";
		}
		return selected.stream().anyMatch(fact -> fact.pointKind() == SpawnPointKind.EXACT && fact.x() == point.x() && fact.y() == point.y()
			&& Math.abs((long) fact.z() - point.z()) <= anchor.validationTolerance()) ? "" : "stock.geometry.unverified";
	}

	/** Only zone semantics supported by ordinary outdoor travel; unknown types fail closed. */
	public static String zoneRejection(List<ZoneType> zones, boolean destination)
	{
		for (var zone : zones)
		{
			final var type = zone.getClass();
			if (type == NoLandingZone.class || type == NoStoreZone.class || type == FishingZone.class) { continue; }
			if (!destination && Set.of(TownZone.class, PeaceZone.class, RespawnZone.class, MotherTreeZone.class).contains(type)) { continue; }
			if (type == EffectZone.class && zone.getTargetType().isType(InstanceType.Npc)) { continue; }
			return "native.zone." + type.getSimpleName();
		}
		return "";
	}

	private static boolean publicAncestry(PhantomTopologyQuery topology, String id)
	{
		for (int depth = 0; id != null && depth < 16; depth++)
		{
			final var node = topology.findNode(id).orElse(null);
			if (node == null || node.instanceId() != 0 || !Set.of(PhantomTopologyNodeKind.WORLD_REGION, PhantomTopologyNodeKind.CITY,
				PhantomTopologyNodeKind.SETTLEMENT, PhantomTopologyNodeKind.OUTDOOR_AREA, PhantomTopologyNodeKind.FARMING_AREA,
				PhantomTopologyNodeKind.ROUTE_AREA).contains(node.kind())) { return false; }
			final String text = (node.id() + " " + node.tags() + " " + node.sourceRefs()).toLowerCase(Locale.ROOT);
			if (Set.of("catacomb", "necropolis", "cave", "rift", "sevensigns", "seven-signs", "dungeon", "instance", "closed", "olympiad")
				.stream().anyMatch(text::contains) || node.tags().contains("door") || node.id().contains(".door.")) { return false; }
			id = node.parentId();
		}
		return id == null;
	}

	/** Exact predecessor, including the current goal/revision, request, generation, plan and minute. */
	public static PhantomBackgroundOperationKey predecessor(PhantomBackgroundOperationKey key)
	{
		final var history = key.historical();
		if (history == null || history.intervalOrdinal() < 1 || history.fromEpochMinute() < 1) { return null; }
		return new PhantomBackgroundOperationKey(key.profileId(), key.characterObjectId(), key.goalId(), key.goalRevision(), 0, RECEIPT_TICK,
			PhantomBackgroundOperationKey.ActionKind.HISTORICAL_OFFSCREEN_TRAVEL, key.targetNpcId(), key.anchorId(), key.modelVersion(), key.hashes(), null,
			new PhantomBackgroundOperationKey.HistoricalIdentity(history.requestId(), history.catchupGeneration(), history.intervalOrdinal() - 1,
				history.fromEpochMinute() - 1, history.fromEpochMinute(), history.planIdentity()));
	}

	public static boolean pendingDebt(PhantomBackgroundState state)
	{
		return state.clock().residualTravelMillis() > 0 && taggedReceipt(state);
	}

	public static boolean taggedReceipt(PhantomBackgroundState state)
	{
		return state.receipt().activityGeneration() == 0
			&& state.receipt().tickSequence() == RECEIPT_TICK && !state.receipt().expectedAfterHash().isEmpty();
	}

	/** MATERIALIZED is persisted before spawn. An aborted load's cleanup must keep
	 * the fence; only capture of the exact World Player may retire it. */
	public static PhantomBackgroundState.Receipt nativeReceipt(PhantomBackgroundState previous, boolean worldPresent)
	{
		return previous == null || worldPresent && previous.state() == PhantomBackgroundState.State.MATERIALIZED && taggedReceipt(previous)
			? PhantomBackgroundState.Receipt.empty() : previous.receipt();
	}

	public static boolean ownsResidual(PhantomBackgroundState state, PhantomBackgroundOperationKey key)
	{
		final var previous = predecessor(key);
		return previous != null && !state.receipt().expectedAfterHash().isEmpty() && previous.digest().equals(state.receipt().operationKey());
	}

	/** A later native load takes over at the unchanged factual point, abandoning only typed virtual debt. */
	static PhantomBackgroundState.Clock nativeClock(PhantomBackgroundState previous)
	{
		if (previous == null) { return new PhantomBackgroundState.Clock(L2jPhantomBackgroundAuthority.INITIAL_RNG_SEED, 0, 0); }
		return pendingDebt(previous) ? new PhantomBackgroundState.Clock(previous.clock().rngState(), 0, previous.clock().residualEncounterMillis()) : previous.clock();
	}

	public static PhantomBackgroundAuthority.TravelAdvance advance(PhantomBackgroundState state, PhantomBackgroundState.Position destination,
		PhantomBackgroundOperationKey key, long budget, PhantomBackgroundSimulationPolicy policy)
	{
		final var denied = new PhantomBackgroundAuthority.TravelAdvance(PhantomBackgroundAuthority.TravelAdvance.Status.NO_ROUTE, state.position(), state.clock(), "offscreen-outdoor");
		if (state.state() != PhantomBackgroundState.State.READY || state.position().instanceId() != 0 || destination.instanceId() != 0
			|| key.actionKind() != PhantomBackgroundOperationKey.ActionKind.HISTORICAL_OFFSCREEN_TRAVEL || key.historical() == null
			|| !key.hashes().equals(state.hashes()) || key.profileId() != state.identity().profileId() || key.characterObjectId() != state.identity().characterObjectId()
			|| !key.anchorId().equals(destination.committedAnchorId()) || policy == null || policy.runSpeed() <= 0
			|| budget < 1 || budget > Math.min(60_000, policy.maximumBatchMillis()) || state.clock().residualEncounterMillis() != 0) { return denied; }
		final long total = Math.max(1, (long) Math.ceil(Math.sqrt(Math.pow((long) destination.x() - state.position().x(), 2)
			+ Math.pow((long) destination.y() - state.position().y(), 2) + Math.pow((long) destination.z() - state.position().z(), 2)) * 1000 / policy.runSpeed()));
		if (total > MAXIMUM_TRAVEL_MILLIS) { return denied; }
		final long residual = state.clock().residualTravelMillis();
		if (residual > 0)
		{
			if (residual >= total || !ownsResidual(state, key)) { return denied; }
		}
		final long remaining = residual == 0 ? total : residual;
		final boolean arrived = remaining <= budget;
		return new PhantomBackgroundAuthority.TravelAdvance(arrived ? PhantomBackgroundAuthority.TravelAdvance.Status.ARRIVED : PhantomBackgroundAuthority.TravelAdvance.Status.PARTIAL,
			arrived ? destination : state.position(), new PhantomBackgroundState.Clock(state.clock().rngState(), arrived ? 0 : remaining - budget, 0), "offscreen-outdoor");
	}
}
