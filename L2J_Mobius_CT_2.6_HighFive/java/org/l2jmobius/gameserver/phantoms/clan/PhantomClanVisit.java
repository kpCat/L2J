/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.clan;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiPredicate;
import java.util.function.LongPredicate;

import org.l2jmobius.gameserver.ai.PlayerAI;
import org.l2jmobius.gameserver.data.xml.NpcData;
import org.l2jmobius.gameserver.model.Location;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.instance.Monster;
import org.l2jmobius.gameserver.model.actor.instance.VillageMaster;
import org.l2jmobius.gameserver.model.zone.ZoneId;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState;
import org.l2jmobius.gameserver.phantoms.activity.PhantomNativeJourneyDemand;
import org.l2jmobius.gameserver.phantoms.activity.PhantomSchedulerControlPort;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundGoalSpec;
import org.l2jmobius.gameserver.phantoms.background.PhantomNormalGatekeeperTravel;
import org.l2jmobius.gameserver.phantoms.clan.PhantomClanService.VisitRequest;
import org.l2jmobius.gameserver.phantoms.combat.PhantomNativeLootControl;
import org.l2jmobius.gameserver.phantoms.decision.PhantomCandidateRegistry;
import org.l2jmobius.gameserver.phantoms.decision.PhantomConsideration;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDecisionCandidate;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDecisionEngine;
import org.l2jmobius.gameserver.phantoms.decision.PhantomExecutionAdmission;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoal;
import org.l2jmobius.gameserver.phantoms.decision.PhantomPlan;
import org.l2jmobius.gameserver.phantoms.decision.PhantomPlanStep;
import org.l2jmobius.gameserver.phantoms.decision.PhantomStepContext;
import org.l2jmobius.gameserver.phantoms.decision.PhantomStepHandlerRegistry;
import org.l2jmobius.gameserver.phantoms.decision.PhantomStepResult;
import org.l2jmobius.gameserver.phantoms.decision.PhantomWeightedConsideration;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.SpawnFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.SpawnPointKind;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeQuery;
import org.l2jmobius.gameserver.phantoms.navigation.L2jNavigationBackend;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNativeTravelActions;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationRequest;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationResult;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationService;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomTravelStrategy;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationLifecyclePort;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService;

/** A disposable native trip. The clan service retains any accepted mutation and its reconciliation. */
public final class PhantomClanVisit implements PhantomNativeJourneyDemand, PhantomSchedulerControlPort, PhantomMaterializationLifecyclePort
{
	private static final String CANDIDATE = "candidate.clan.visit";
	private static final String ACTION = "clan.visit.advance";
	private static final int MAXIMUM_ROUTE_CHOICES = 3;
	private final PhantomClanService _clans;
	private final PhantomMaterializationService _materialization;
	private final PhantomNavigationService _navigation;
	private final PhantomNormalGatekeeperTravel _gatekeepers;
	private final L2jNavigationBackend _geo = new L2jNavigationBackend();
	private final List<SpawnFact> _masters;
	private final BiPredicate<Long, Long> _yieldFarm;
	private final BiPredicate<Long, Long> _releaseFarmYield;
	private final LongPredicate _allowed;
	private final BiPredicate<Long, PhantomGoal> _current;
	private final BiPredicate<Long, PhantomGoal> _exclusiveOwner;
	private final BiPredicate<PhantomGoal, Player> _field;
	private final Map<Long, Visit> _visits = new ConcurrentHashMap<>();
	private volatile boolean _stopping;

	public PhantomClanVisit(PhantomClanService clans, PhantomMaterializationService materialization, PhantomNavigationService navigation,
		PhantomNormalGatekeeperTravel gatekeepers, PhantomGameKnowledgeQuery knowledge, BiPredicate<Long, Long> yieldFarm,
		BiPredicate<Long, Long> releaseFarmYield, LongPredicate allowed, BiPredicate<Long, PhantomGoal> current,
		BiPredicate<Long, PhantomGoal> exclusiveOwner, BiPredicate<PhantomGoal, Player> field)
	{
		_clans = clans;
		_materialization = materialization;
		_navigation = navigation;
		_gatekeepers = gatekeepers;
		_yieldFarm = yieldFarm;
		_releaseFarmYield = releaseFarmYield;
		_allowed = allowed;
		_current = current;
		_exclusiveOwner = exclusiveOwner;
		_field = field;
		_masters = knowledge.snapshot().spawnFactsByNpc().entrySet().stream()
			.filter(entry -> { final var npc = NpcData.getInstance().getTemplate(entry.getKey()); return npc != null && npc.getType().startsWith("VillageMaster"); })
			.flatMap(entry -> entry.getValue().stream()).filter(spawn -> spawn.pointKind() == SpawnPointKind.EXACT && spawn.instanceId() == 0 && spawn.amount() > 0)
			.sorted(Comparator.comparingInt(SpawnFact::npcId).thenComparingInt(SpawnFact::spawnOrdinal)).toList();
	}

	public void offer(VisitRequest request)
	{
		if (_stopping || _masters.isEmpty()) { return; }
		final long id = request.member().profileId();
		_visits.compute(id, (key, existing) -> existing == null ? new Visit(request) : existing);
	}

	public void registerCandidates(PhantomCandidateRegistry candidates)
	{
		candidates.register(new PhantomDecisionCandidate(CANDIDATE, Set.of(PhantomBackgroundGoalSpec.GOAL_TYPE),
			Set.of(PhantomActivityState.ACTIVE, PhantomActivityState.NEARBY_PERCEPTIBLE), List.of(),
			List.of(new PhantomWeightedConsideration("score.clan.visit", 1, context ->
			{
				final var visit = eligible(context.profileId(), context.goal());
				if (visit == null) { return new PhantomConsideration.Evaluation(0, "clan.visit.unavailable"); }
				final long age = System.currentTimeMillis() - visit.request.offeredAt();
				final long interval = Math.max(1, (visit.request.deadline() - visit.request.offeredAt()) / 4);
				final int rank = visit.accepted ? -1 : (int) Math.clamp(-55 + visit.request.preference() + 54 * age / interval, -100, -1);
				return new PhantomConsideration.Evaluation(1000, "clan.visit.ready", rank);
			})), 1000, context -> new PhantomPlan(context.decisionSequence(), context.goal().goalId(), CANDIDATE,
				List.of(new PhantomPlanStep(0, ACTION, null, Map.of(), 60_000, 10, "clan.visit.advance")), 60_000, context.logicalNowNanos())));
	}

	public void registerHandlers(PhantomStepHandlerRegistry handlers) { handlers.register(ACTION, this::advance); }

	private Visit eligible(long id, PhantomGoal root)
	{
		final var visit = _visits.get(id);
		if (visit == null || !visit.request.root().equals(root)) { return null; }
		if (visit.done && visit.accepted) { return visit; }
		if (_stopping || visit.done || System.currentTimeMillis() >= visit.request.deadline() || !_allowed.test(id)) { return null; }
		if (visit.accepted) { return visit; }
		if (_exclusiveOwner.test(id, root)) { return null; }
		try (var lease = _materialization.tryAcquireObservation(id).orElse(null))
		{
			return lease != null && actorMatches(visit, lease.player()) && _field.test(root, lease.player()) ? visit : null;
		}
	}

	public boolean offers(long id, PhantomGoal root) { return eligible(id, root) != null; }
	public boolean owns(long id, PhantomGoal root) { return root != null && requiresNativeContext(id, root.goalId(), root.revision()); }
	public boolean blocksNewMovement(long id)
	{
		final var visit = _visits.get(id);
		return visit != null && visit.accepted;
	}

	private PhantomStepResult advance(PhantomStepContext context)
	{
		final long id = context.profileId();
		final var visit = _visits.get(id);
		if (visit == null || !visit.busy.compareAndSet(false, true)) { return retry("clan.visit.pending"); }
		try
		{
			if (visit.done) { return finish(id, visit, "clan.visit.released"); }
			if (context.cancellationToken().isCancelled() || context.cancellationToken().yieldRequested() || eligible(id, context.goal()) != visit || !_current.test(id, context.goal()))
			{ return finish(id, visit, "clan.visit.owner_changed"); }
			if (visit.yieldUntil == 0)
			{
				final long interval = visit.request.deadline() - visit.request.offeredAt();
				visit.yieldUntil = Math.min(visit.request.deadline(), System.currentTimeMillis() + interval / 16);
			}
			if (!visit.accepted && !_yieldFarm.test(id, visit.yieldUntil))
			{
				return System.currentTimeMillis() < visit.yieldUntil ? retry("clan.visit.finish_target") : finish(id, visit, "clan.visit.field_busy");
			}
			final VillageMaster master;
			try (var lease = _materialization.tryAcquireAction(id).orElse(null))
			{
				if (lease == null) { return retry("clan.visit.actor_pending"); }
				final var player = lease.player();
				if (!lease.executionValid() || !actorMatches(visit, player) || player.hasPendingOwnedStore() || player.isChangingClass()
					|| visit.accepted && (player != visit.player || visit.owner != player.getNativeWorkOwner() || visit.classId != player.getActiveClass()
						|| visit.classIndex != player.getClassIndex() || visit.activityGeneration != context.activityGeneration()
						|| !java.util.Objects.equals(visit.execution, lease.executionStamp())))
				{ return finish(id, visit, "clan.visit.actor_changed"); }
				if (PhantomNativeLootControl.threat(player) != null || player.isInCombat()) { return finish(id, visit, "clan.visit.threat"); }
				if (!PhantomNativeTravelActions.ordinary(player) || player.isAttackingNow() || player.isProcessingTransaction() || player.isTeleporting())
				{ return visit.accepted ? finish(id, visit, "clan.visit.native_state_changed") : retry("clan.visit.native_busy"); }
				if (!live(id, visit, context)) { return finish(id, visit, "clan.visit.owner_changed"); }
				if (!visit.accepted)
				{
					if (player.isMoving() || player.isMovementDisabled() || player.getMoveSpeed() <= 0) { return retry("clan.visit.release_movement"); }
					visit.player = player;
					visit.owner = player.getNativeWorkOwner();
					visit.classId = player.getActiveClass();
					visit.classIndex = player.getClassIndex();
					visit.activityGeneration = context.activityGeneration();
					visit.execution = lease.executionStamp();
					visit.origin = PhantomNativeTravelActions.point(player);
					visit.choices = routeChoices(player, visit.origin);
					if (visit.choices.isEmpty()) { return finish(id, visit, "clan.visit.master_unavailable"); }
					visit.accepted = true;
				}
				master = visit.master == null ? visit.choices.stream().filter(value -> currentMaster(value) && L2jPhantomClanBackend.canVisit(player, value)).findFirst().orElse(null)
					: currentMaster(visit.master) && L2jPhantomClanBackend.canVisit(player, visit.master) ? visit.master : null;
				if (master == null)
				{
					if (visit.plan == null) { return plan(id, visit, player, context.logicalNowNanos()); }
					if (!currentMaster(visit.master)) { return finish(id, visit, "clan.visit.master_changed"); }
					return walk(id, visit, player, context);
				}
				if (player.isMoving()) { releaseWalk(visit); return retry("clan.visit.arrival_settling"); }
			}
			if (!live(id, visit, context)) { return finish(id, visit, "clan.visit.owner_changed"); }
			final var result = _clans.completeAutonomousVisit(visit.request, master.getObjectId(), () -> live(id, visit, context));
			return result == PhantomClanService.VisitOutcome.BUSY ? retry("clan.visit.writer_busy")
				: finish(id, visit, result == PhantomClanService.VisitOutcome.HANDLED ? "clan.visit.arrival_handled" : "clan.visit.no_longer_eligible");
		}
		finally { visit.busy.set(false); }
	}

	private static boolean actorMatches(Visit visit, Player player)
	{
		return player.getObjectId() == visit.request.member().characterObjectId() && !player.isAlikeDead() && player.getParty() == null && player.getInstanceId() == 0
			&& !player.isInsideZone(ZoneId.WATER) && !player.isInStoreMode() && !player.isTransformed() && !player.isMounted()
			&& (visit.request.purpose() == PhantomClanService.VisitPurpose.FOUND ? player.getClanId() == 0 : player.getClanId() == visit.request.clanId());
	}

	private boolean live(long id, Visit visit, PhantomStepContext context)
	{
		return !_stopping && !visit.done && _allowed.test(id) && _current.test(id, visit.request.root())
			&& !context.cancellationToken().isCancelled() && !context.cancellationToken().yieldRequested() && System.currentTimeMillis() < visit.request.deadline();
	}

	private List<VillageMaster> routeChoices(Player player, PhantomNavigationPoint origin)
	{
		final var free = _gatekeepers.legs().stream().filter(leg -> PhantomNormalGatekeeperTravel.phantomMaximumFee(leg, player.getClassIndex(), player.getLevel()) == 0)
			.filter(PhantomNativeTravelActions::available).toList();
		return _masters.stream().map(PhantomClanVisit::spawnedMaster).filter(java.util.Objects::nonNull).distinct()
			.sorted(Comparator.comparingDouble((VillageMaster master) ->
			{
				final var target = point(master);
				return Math.min(origin.distanceTo(target), free.stream().mapToDouble(leg ->
					origin.distanceTo(new PhantomNavigationPoint(leg.sourceX(), leg.sourceY(), leg.sourceZ(), 0))
					+ target.distanceTo(new PhantomNavigationPoint(leg.destinationX(), leg.destinationY(), leg.destinationZ(), 0))).min().orElse(Double.POSITIVE_INFINITY));
			}).thenComparingInt(VillageMaster::getObjectId)).limit(MAXIMUM_ROUTE_CHOICES).toList();
	}

	private static VillageMaster spawnedMaster(SpawnFact spawn)
	{
		final var region = World.getInstance().getRegion(spawn.x(), spawn.y(), spawn.z());
		return region == null ? null : region.getVisibleObjects().stream().filter(value -> value instanceof VillageMaster).map(VillageMaster.class::cast)
			.filter(master -> master.getId() == spawn.npcId() && currentMaster(master) && master.calculateDistance3D(spawn.x(), spawn.y(), spawn.z()) < 64)
			.min(Comparator.comparingInt(VillageMaster::getObjectId)).orElse(null);
	}

	private static boolean currentMaster(VillageMaster master)
	{
		return master.getInstanceId() == 0 && master.isSpawned() && !master.isAlikeDead() && World.getInstance().findObject(master.getObjectId()) == master;
	}

	private static PhantomNavigationPoint point(VillageMaster master)
	{
		final int height = org.l2jmobius.gameserver.geoengine.GeoEngine.getInstance().getHeight(master.getX(), master.getY(), master.getZ());
		return new PhantomNavigationPoint(master.getX(), master.getY(), height, master.getInstanceId());
	}

	private PhantomStepResult plan(long id, Visit visit, Player player, long now)
	{
		if (!visit.origin.equals(PhantomNativeTravelActions.point(player))) { return finish(id, visit, "clan.visit.origin_changed"); }
		if (visit.choice >= visit.choices.size())
		{
			if (visit.best == null) { return finish(id, visit, "clan.visit.route_unavailable"); }
			visit.plan = visit.best;
			return retry("clan.visit.route_proven");
		}
		final var target = visit.choices.get(visit.choice);
		if (visit.navigationId == 0)
		{
			if (!currentMaster(target)) { visit.choice++; return retry("clan.visit.master_changed"); }
			final var options = PhantomNativeTravelActions.snapshot(player, _gatekeepers.legs(), Math.max(0, visit.request.deadline() - System.currentTimeMillis()));
			final var free = new PhantomTravelStrategy.Options(options.legs().stream()
				.filter(leg -> PhantomNormalGatekeeperTravel.phantomMaximumFee(leg, options.classIndex(), options.level()) == 0).toList(), options.speed(), 0, 0,
				options.classIndex(), options.level(), options.epochMinute(), options.gatekeeperAllowed(), "clan.visit.walk_or_gatekeeper",
				null, 0, options.remainingMillis(), options.blockedEdges());
			visit.navigationDeadline = now + _navigation.policy().defaultRequestDeadlineNanos();
			final var submitted = _navigation.submitCrossRegionTravel(new PhantomNavigationRequest(id, visit.origin, point(target), now,
				visit.navigationDeadline, _navigation.policy().maximumRouteDistance()), free);
			if (submitted.status() == PhantomNavigationService.SubmissionStatus.REJECTED) { return retry("clan.visit.navigation_busy"); }
			visit.navigationId = submitted.requestId();
			final var immediate = submitted.immediateResult();
			if (immediate != null) { _navigation.consume(visit.navigationId); return acceptPlan(visit, target, immediate); }
		}
		final var result = _navigation.consume(visit.navigationId).orElse(null);
		if (result != null) { return acceptPlan(visit, target, result); }
		if (now >= visit.navigationDeadline)
		{
			_navigation.cancel(id, visit.navigationId);
			visit.navigationId = 0;
			visit.choice++;
		}
		return retry("clan.visit.navigation_pending");
	}

	private PhantomStepResult acceptPlan(Visit visit, VillageMaster target, PhantomNavigationResult result)
	{
		final var candidate = _navigation.consumeTravelPlan(visit.navigationId).orElse(null);
		visit.navigationId = 0;
		visit.choice++;
		if (result.status() != PhantomNavigationResult.Status.DIRECT_UNVERIFIED_NO_GEODATA && candidate != null && candidate.adena() == 0 && candidate.scrolls() == 0
			&& (visit.best == null || candidate.millis() < visit.best.millis()))
		{
			visit.best = candidate;
			visit.master = target;
		}
		return retry("clan.visit.navigation_checked");
	}

	private PhantomStepResult walk(long id, Visit visit, Player player, PhantomStepContext context)
	{
		if (player.isMoving()) { return retry("clan.visit.walking"); }
		if (visit.stage >= visit.plan.stages().size()) { return finish(id, visit, "clan.visit.arrival_unverified"); }
		final var stage = visit.plan.stages().get(visit.stage);
		if (stage.mode() == PhantomTravelStrategy.Mode.GK)
		{
			if (!live(id, visit, context) || PhantomNormalGatekeeperTravel.phantomMaximumFee(stage.gatekeeper(), player.getClassIndex(), player.getLevel()) != 0)
			{ return finish(id, visit, "clan.visit.gatekeeper_refused"); }
			final var receipt = PhantomNativeTravelActions.gatekeeper(player, stage.gatekeeper());
			if (receipt.status() != PhantomNativeTravelActions.Status.ARRIVED) { return finish(id, visit, "clan.visit.gatekeeper_failed"); }
			visit.stage++;
			return retry("clan.visit.gatekeeper_arrived");
		}
		if (stage.mode() != PhantomTravelStrategy.Mode.WALK) { return finish(id, visit, "clan.visit.invalid_stage"); }
		final var current = PhantomNativeTravelActions.point(player);
		final var waypoint = stage.points().get(visit.waypoint);
		if (current.distanceTo(waypoint) <= Math.min(16, _navigation.arrivalRadius()))
		{
			if (++visit.waypoint == stage.points().size()) { visit.stage++; visit.waypoint = 0; }
			return retry("clan.visit.waypoint");
		}
		if (!live(id, visit, context) || player.isMovementDisabled() || !_geo.canWalkDirect(current, waypoint))
		{ return finish(id, visit, "clan.visit.segment_changed"); }
		if (!PhantomNativeTravelActions.safePveWalkSegment(player, waypoint.x(), waypoint.y(), waypoint.z(), World.getInstance().getVisibleObjects(player, Monster.class), Set.of()))
		{ return finish(id, visit, "clan.visit.segment_threat"); }
		if (!(player.getAI() instanceof PlayerAI ai)) { return finish(id, visit, "clan.visit.player_ai_unavailable"); }
		if (player.isAllSkillsDisabled() || ai.getNextIntention() != null) { return retry("clan.visit.native_action_pending"); }
		ai.moveImmediately(new Location(waypoint.x(), waypoint.y(), waypoint.z()));
		visit.walk = PhantomNativeTravelActions.captureWalk(player);
		if (visit.walk == null || !_geo.canWalkDirect(current, visit.walk.destination()))
		{ return finish(id, visit, "clan.visit.move_rejected"); }
		return retry("clan.visit.walking");
	}

	private static PhantomStepResult retry(String reason) { return PhantomStepResult.retry(500, reason); }

	private PhantomStepResult finish(long id, Visit visit, String reason)
	{
		visit.done = true;
		if (!releaseWalk(visit)) { return retry("clan.visit.waiting_owned_move"); }
		visit.accepted = false;
		if (visit.navigationId != 0)
		{
			_navigation.cancel(id, visit.navigationId);
			_navigation.consumeTravelPlan(visit.navigationId);
			visit.navigationId = 0;
		}
		if (visit.yieldUntil == 0 || _releaseFarmYield.test(id, visit.yieldUntil)) { _visits.remove(id, visit); }
		return PhantomStepResult.of(PhantomStepResult.Type.REPLAN, reason);
	}

	private static boolean releaseWalk(Visit visit)
	{
		final var walk = visit.walk;
		if (walk == null) { return true; }
		final var player = walk.player();
		final var destination = walk.destination();
		if (!PhantomNativeTravelActions.cancelWalk(walk) && player.getNativeWorkOwner() == walk.owner() && walk.owner().isCurrent()
			&& World.getInstance().getPlayer(player.getObjectId()) == player && player.isMoving() && player.getInstanceId() == destination.instanceId()
			&& player.getXdestination() == destination.x() && player.getYdestination() == destination.y() && player.getZdestination() == destination.z())
		{
			return false;
		}
		visit.walk = null;
		return true;
	}

	private boolean cancel(long id, String reason)
	{
		final var visit = _visits.get(id);
		if (visit == null) { return true; }
		if (!visit.busy.compareAndSet(false, true)) { return false; }
		try { finish(id, visit, reason); return _visits.get(id) != visit; }
		finally { visit.busy.set(false); }
	}

	@Override
	public void onPulse()
	{
		final long now = System.currentTimeMillis();
		_visits.forEach((id, visit) ->
		{
			if (_stopping || visit.done || now >= visit.request.deadline() || !_allowed.test(id) || !_current.test(id, visit.request.root()))
			{ cancel(id, "clan.visit.expired_or_replaced"); }
		});
	}

	@Override public Set<Long> acceptedProfiles() { return _visits.entrySet().stream().filter(entry -> entry.getValue().accepted).map(Map.Entry::getKey).collect(java.util.stream.Collectors.toUnmodifiableSet()); }
	@Override public boolean requiresNativeContext(long id, long goalId, long revision)
	{
		final var visit = _visits.get(id);
		return visit != null && visit.accepted
			&& visit.request.root().goalId() == goalId && visit.request.root().revision() == revision;
	}

	public PhantomDecisionEngine.ExecutionReadiness prepareExecutionYield(long id, long now)
	{
		return cancel(id, "clan.visit.execution_yield") ? new PhantomDecisionEngine.ExecutionReadiness(true, "clan.visit.yielded", now)
			: new PhantomDecisionEngine.ExecutionReadiness(false, "clan.visit.handler_pending", now + 500);
	}

	public void beginStop() { _stopping = true; onPulse(); }
	public boolean finishStop() { beginStop(); return _visits.isEmpty(); }
	@Override public void beforeMaterialize(long id, int objectId) { }
	@Override public void afterPlayerLoad(long id, Player player) { }
	@Override public void materializeSucceeded(long id, int objectId) { }
	@Override public void materializeAborted(long id, int objectId) { cancel(id, "clan.visit.materialization_aborted"); }
	@Override public void beforeStore(long id, Player player)
	{
		if (!cancel(id, "clan.visit.store")) { throw new IllegalStateException("Clan visit handler remains active before STORE."); }
	}
	@Override public void afterStore(long id, Player player) { }

	private static final class Visit
	{
		private final VisitRequest request;
		private final AtomicBoolean busy = new AtomicBoolean();
		private volatile boolean accepted;
		private volatile boolean done;
		private long yieldUntil;
		private Player player;
		private org.l2jmobius.gameserver.model.actor.PlayerNativeWork.Owner owner;
		private PhantomExecutionAdmission.Stamp execution;
		private long activityGeneration;
		private int classId, classIndex;
		private PhantomNavigationPoint origin;
		private List<VillageMaster> choices;
		private int choice, stage, waypoint;
		private long navigationId, navigationDeadline;
		private VillageMaster master;
		private PhantomTravelStrategy.Candidate best, plan;
		private PhantomNativeTravelActions.OwnedWalk walk;

		private Visit(VisitRequest request) { this.request = request; }
	}
}
