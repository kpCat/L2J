/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.progression;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.LongPredicate;
import java.util.function.LongConsumer;
import java.util.function.Supplier;

import org.l2jmobius.gameserver.ai.Intention;
import org.l2jmobius.gameserver.data.xml.NpcData;
import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.model.Location;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Npc;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.enums.player.PlayerClass;
import org.l2jmobius.gameserver.model.actor.instance.Trainer;
import org.l2jmobius.gameserver.model.zone.ZoneId;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState;
import org.l2jmobius.gameserver.phantoms.decision.PhantomCancellationToken;
import org.l2jmobius.gameserver.phantoms.decision.PhantomCandidateRegistry;
import org.l2jmobius.gameserver.phantoms.decision.PhantomConsideration;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDecisionCandidate;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoal;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStore;
import org.l2jmobius.gameserver.phantoms.decision.PhantomPlan;
import org.l2jmobius.gameserver.phantoms.decision.PhantomPlanStep;
import org.l2jmobius.gameserver.phantoms.decision.PhantomStepHandlerRegistry;
import org.l2jmobius.gameserver.phantoms.decision.PhantomStepResult;
import org.l2jmobius.gameserver.phantoms.decision.PhantomStepResult.Type;
import org.l2jmobius.gameserver.phantoms.decision.PhantomWeightedConsideration;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.SpawnFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.SpawnPointKind;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeQuery;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationRequest;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationResult;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationService;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.AcquireKind;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.LearnSkillRequest;

/**
 * Explicit class-bound learning intent, using the existing Decision, Navigation
 * and paid Progression owners. Learned native ranks determine the restart phase;
 * this adapter never grants skills, directly writes positions or owns a second AI pulse.
 */
public final class L2jPhantomLearningJourney
{
	public static final String GOAL_TYPE = "skill.learn";
	public static final String CANDIDATE = "candidate.learning.journey";
	public static final String ACTION = "learning.journey";
	private static final Set<String> CONSTRAINTS = Set.of("class.id", "class.index", "field.x", "field.y", "field.z", "field.instance");
	private static final int LIMIT = 128;
	private final Object _monitor = new Object();
	private final PhantomGoalStore _goals;
	private final PhantomMaterializationService _materialization;
	private final PhantomProgressionService _progression;
	private final PhantomNavigationService _navigation;
	private final Supplier<PhantomGameKnowledgeQuery> _knowledge;
	private final LongPredicate _actionAllowed;
	private final Set<Long> _active = new HashSet<>();
	private final Map<Long, Travel> _travels = new HashMap<>();
	private List<SpawnFact> _trainers = List.of();
	private String _knowledgeHash;
	private volatile State _state = State.NEW;
	private PhantomAutonomousLearning _automatic;

	public L2jPhantomLearningJourney(PhantomGoalStore goals, PhantomMaterializationService materialization, PhantomProgressionService progression, PhantomNavigationService navigation, Supplier<PhantomGameKnowledgeQuery> knowledge, LongPredicate actionAllowed)
	{
		_goals = java.util.Objects.requireNonNull(goals);
		_materialization = java.util.Objects.requireNonNull(materialization);
		_progression = java.util.Objects.requireNonNull(progression);
		_navigation = java.util.Objects.requireNonNull(navigation);
		_knowledge = java.util.Objects.requireNonNull(knowledge);
		_actionAllowed = java.util.Objects.requireNonNull(actionAllowed);
	}

	/** Install the ordinary farm producer before start; explicit learning remains compatible. */
	public void enableAutonomous(PhantomProfileRepository profiles, LongConsumer pauseFarm, PhantomAutonomousLearningPolicy policy)
	{
		synchronized (_monitor)
		{
			if (_state != State.NEW || _automatic != null) { throw new IllegalStateException("Autonomous learning must be installed once before start."); }
			_automatic = new PhantomAutonomousLearning(profiles, _materialization, _progression, this, pauseFarm, _actionAllowed, policy);
		}
	}

	public boolean ownsAutomaticIntent(long profileId, PhantomGoal root) { return _state == State.RUNNING && _automatic != null && _automatic.owns(profileId, root); }
	public Optional<PhantomGoal> automaticIntent(long profileId) { return _automatic == null ? Optional.empty() : _automatic.intent(profileId); }

	public boolean start()
	{
		synchronized (_monitor)
		{
			if (_state != State.NEW) { return false; }
			final var snapshot = _knowledge.get().snapshot();
			_trainers = snapshot.spawnFactsByNpc().entrySet().stream().filter(entry -> { final var npc = NpcData.getInstance().getTemplate(entry.getKey()); return npc != null && "Trainer".equals(npc.getType()); }).flatMap(entry -> entry.getValue().stream()).filter(spawn -> spawn.pointKind() == SpawnPointKind.EXACT && spawn.instanceId() == 0 && spawn.amount() > 0).toList();
			if (_trainers.isEmpty() || _trainers.size() > 4096) { throw new IllegalStateException("Learning requires bounded authoritative Trainer spawns."); }
			_knowledgeHash = snapshot.combinedHash();
			_state = State.RUNNING;
			return true;
		}
	}

	public void registerCandidates(PhantomCandidateRegistry registry)
	{
		if (_automatic != null) { _automatic.registerCandidates(registry); }
		registry.register(new PhantomDecisionCandidate(CANDIDATE, Set.of(GOAL_TYPE), Set.of(PhantomActivityState.ACTIVE), List.of(), List.of(new PhantomWeightedConsideration("score.learning.journey", 1, context -> new PhantomConsideration.Evaluation(request(context.goal()).isPresent() ? 1000 : 0, "learning.explicit"))), 1000, context ->
		{
			final var step = new PhantomPlanStep(0, ACTION, context.goal().target(), Map.of(), 60_000, 10, "learning.advance");
			return new PhantomPlan(context.decisionSequence(), context.goal().goalId(), CANDIDATE, List.of(step), 60_000, context.logicalNowNanos());
		}));
	}

	public void registerHandlers(PhantomStepHandlerRegistry registry)
	{
		if (_automatic != null) { _automatic.registerHandlers(registry); }
		registry.register(ACTION, context -> advance(context.profileId(), context.goal(), context.cancellationToken(), context.logicalNowNanos()));
	}

	public PhantomStepResult advance(long profileId, PhantomGoal goal, PhantomCancellationToken token, long now)
	{
		return advanceIntent(profileId, goal, goal, token, now);
	}

	PhantomStepResult advanceIntent(long profileId, PhantomGoal goal, PhantomGoal intent, PhantomCancellationToken token, long now)
	{
		if (profileId <= 0 || now < 0) { return fail("learning.request.invalid"); }
		synchronized (_monitor) { if (_state != State.RUNNING || _active.size() >= LIMIT || !_active.add(profileId)) { return retry("learning.busy"); } }
		try
		{
			final Request request = request(intent).orElse(null);
			if (token.isCancelled()) { cleanup(profileId); return PhantomStepResult.of(Type.CANCELLED, "learning.cancelled"); }
			if (request == null || !current(profileId, goal)) { cleanup(profileId); return fail("learning.goal.stale"); }
			if (!_actionAllowed.test(profileId)) { return retry("learning.owner.busy"); }
			if (!_knowledgeHash.equals(_knowledge.get().snapshot().combinedHash())) { cleanup(profileId); return fail("learning.knowledge.changed"); }
			final var acquired = _materialization.tryAcquireAction(profileId);
			if (acquired.isEmpty()) { return retry("learning.actor.unavailable"); }
			try (var lease = acquired.orElseThrow())
			{
				final Player player = lease.player();
				if (!intent.equals(goal) && (!"learning.autonomous".equals(intent.purposeKey()) || intent.goalId() != goal.goalId() || intent.constraints().getOrDefault("owner.revision", -1L) != goal.revision() || intent.constraints().getOrDefault("owner.object", -1L) != player.getObjectId())) { cleanup(profileId); return fail("learning.intent.owner_changed"); }
				if (player.getActiveClass() != request.classId() || player.getClassIndex() != request.classIndex() || player.getLearningClass().getId() != request.classId() || player.getInstanceId() != 0) { cleanup(profileId); return fail("learning.class_or_instance.changed"); }
				if (player.isAlikeDead()) { cleanup(profileId); return fail("learning.actor.dead"); }
				if (player.isInParty() || player.isInCombat() || player.isAttackingNow() || player.isCastingNow() || player.isCastingSimultaneouslyNow() || player.isChangingClass() || player.isMounted() || player.isTransformed()) { return retry("learning.actor.busy"); }
				final boolean returning = player.getSkillLevel(request.skillId()) >= request.skillLevel();
				if (returning && player.calculateDistance3D(request.field().x(), request.field().y(), request.field().z()) <= _navigation.arrivalRadius())
				{
					cleanup(profileId);
					return player.isInsideZone(ZoneId.PEACE) ? fail("learning.field.peace") : PhantomStepResult.of(Type.COMPLETE_GOAL, "learning.field.returned");
				}
				Travel travel;
				synchronized (_monitor) { travel = _travels.get(profileId); }
				if (travel != null && (travel.actor() != player || travel.goalId() != goal.goalId() || travel.goalRevision() != goal.revision() || !travel.request().equals(request) || travel.returning() != returning)) { cleanup(profileId); travel = null; }
				if (travel == null)
				{
					if (player.isMoving()) { return retry("learning.foreign_movement"); }
					final SpawnFact trainer = returning ? null : _trainers.stream().filter(spawn -> NpcData.getInstance().getTemplate(spawn.npcId()).canTeach(player.getLearningClass())).min(Comparator.comparingDouble((SpawnFact spawn) -> player.calculateDistance3D(spawn.x(), spawn.y(), spawn.z())).thenComparingInt(SpawnFact::npcId)).orElse(null);
					if (!returning && trainer == null) { return fail("learning.trainer.unsupported"); }
					final var destinations = returning ? List.of(request.field()) : approaches(player, trainer);
					if (destinations.isEmpty()) { return fail("learning.trainer.no_approach"); }
					travel = new Travel(player, goal.goalId(), goal.revision(), request, returning, trainer == null ? 0 : trainer.npcId(), destinations, 0, now + TimeUnit.MINUTES.toNanos(2), 0, List.of(), 0);
					if (!put(profileId, travel)) { return retry("learning.capacity"); }
				}
				if (!returning)
				{
					final int npcId = travel.trainerNpcId();
					final Npc trainer = World.getInstance().getVisibleObjects(player, Npc.class).stream().filter(npc -> npc instanceof Trainer && npc.getId() == npcId && npc.getInstanceId() == 0 && npc.isSpawned() && !npc.isAlikeDead() && npc.canInteract(player)).min(Comparator.comparingDouble((Npc value) -> player.calculateDistance3D(value))).orElse(null);
					if (trainer != null)
					{
						cleanup(profileId);
						if (cancelled(intent, request, player, token) || !current(profileId, goal) || !_actionAllowed.test(profileId)) { return PhantomStepResult.of(Type.CANCELLED, "learning.cancelled"); }
						if (player.getTarget() != trainer) { trainer.onAction(player, false); }
						trainer.onAction(player);
						final int nextRank = Math.min(request.skillLevel(), Math.max(1, player.getSkillLevel(request.skillId()) + 1));
						final var result = _progression.learnClassSkill(new LearnSkillRequest(profileId, trainer.getObjectId(), AcquireKind.CLASS, request.skillId(), nextRank, () -> cancelled(intent, request, player, token)));
						return switch (result.status())
						{
							case SUCCESS, IDEMPOTENT -> retry("learning.skill.committed");
							case OPERATION_IN_PROGRESS, ACTOR_NOT_MATERIALIZED, ACTOR_STATE_REJECTED, TRAINER_REQUIRED -> retry("learning.native.busy");
							case CANCELLED -> PhantomStepResult.of(Type.CANCELLED, "learning.cancelled");
							default -> fail("learning.rejected." + result.status().name().toLowerCase(java.util.Locale.ROOT));
						};
					}
				}
				if (now >= travel.deadlineNanos()) { cleanup(profileId); return fail("learning.route.deadline"); }
				if (travel.requestId() == 0)
				{
					final var origin = new PhantomNavigationPoint(player.getX(), player.getY(), player.getZ(), 0);
					final var submission = _navigation.submitWalkCorridor(new PhantomNavigationRequest(profileId, origin, travel.destination(), Math.max(0, now), travel.deadlineNanos(), 50_000));
					if (submission.status() == PhantomNavigationService.SubmissionStatus.REJECTED) { return retry("learning.navigation.busy"); }
					final var immediate = submission.immediateResult();
					if (immediate != null && !verified(immediate)) { return routeFailure(profileId, travel, immediate); }
					final Travel submitted = travel.route(submission.requestId(), immediate == null ? List.of() : immediate.route().waypoints());
					if (!put(profileId, submitted)) { _navigation.cancel(profileId, submission.requestId()); return PhantomStepResult.of(Type.CANCELLED, "learning.stopping"); }
					return retry("learning.navigation.requested");
				}
				if (travel.waypoints().isEmpty())
				{
					final var route = _navigation.consume(travel.requestId());
					if (route.isEmpty()) { return retry("learning.navigation.pending"); }
					if (!verified(route.orElseThrow())) { return routeFailure(profileId, travel, route.orElseThrow()); }
					put(profileId, travel.route(travel.requestId(), route.orElseThrow().route().waypoints()));
					return retry("learning.navigation.ready");
				}
				final var waypoint = travel.waypoints().get(travel.waypoint());
				if (player.calculateDistance3D(waypoint.x(), waypoint.y(), waypoint.z()) <= _navigation.arrivalRadius())
				{
					if (travel.waypoint() + 1 >= travel.waypoints().size()) { cleanup(profileId); return retry("learning.navigation.arrived"); }
					put(profileId, travel.next()); return retry("learning.navigation.waypoint");
				}
				if (cancelled(intent, request, player, token) || !current(profileId, goal) || !_actionAllowed.test(profileId)) { cleanup(profileId); return PhantomStepResult.of(Type.CANCELLED, "learning.cancelled"); }
				if (!player.isMoving() && !player.isMovementDisabled()) { player.getAI().setIntention(Intention.MOVE_TO, new Location(waypoint.x(), waypoint.y(), waypoint.z())); }
				return retry(returning ? "learning.field.moving" : "learning.trainer.moving");
			}
		}
		finally { synchronized (_monitor) { _active.remove(profileId); } }
	}

	/** No JDBC here: the existing Decision token supplies in-flight goal authority inside the SP transaction. */
	private boolean cancelled(PhantomGoal goal, Request request, Player player, PhantomCancellationToken token)
	{
		return _state != State.RUNNING || token.isCancelled() || goal.deadlineEpochMillis() <= System.currentTimeMillis() || player.getActiveClass() != request.classId() || player.getClassIndex() != request.classIndex() || player.getLearningClass().getId() != request.classId() || player.getInstanceId() != 0 || player.isInParty();
	}

	private boolean current(long profileId, PhantomGoal goal) { return _goals.load(profileId).map(stored -> stored.goal().equals(goal)).orElse(false); }

	/** NPC centers can be blocked; every alternative still needs the shared native route verdict. */
	private static List<PhantomNavigationPoint> approaches(Player player, SpawnFact trainer)
	{
		final var geo = GeoEngine.getInstance();
		final List<PhantomNavigationPoint> points = new ArrayList<>();
		for (int radius : new int[] {0,64,112})
		{
			for (int direction = 0; direction < (radius == 0 ? 1 : 8); direction++)
			{
				final double angle = direction * Math.PI / 4;
				final int x = trainer.x() + (int) Math.round(radius * Math.cos(angle)), y = trainer.y() + (int) Math.round(radius * Math.sin(angle));
				if (!geo.hasGeo(x,y)) { continue; }
				final int z = geo.getHeight(x,y,trainer.z());
				final double distance = Math.sqrt(Math.pow(x-trainer.x(),2)+Math.pow(y-trainer.y(),2)+Math.pow(z-trainer.z(),2));
				if (distance >= Npc.INTERACTION_DISTANCE) { continue; }
				final var point = new PhantomNavigationPoint(x,y,z,0);
				if (!points.contains(point)) { points.add(point); }
			}
		}
		points.sort(Comparator.comparingDouble((PhantomNavigationPoint point)->player.calculateDistance3D(point.x(),point.y(),point.z())).thenComparingInt(PhantomNavigationPoint::x).thenComparingInt(PhantomNavigationPoint::y));
		return List.copyOf(points);
	}

	private PhantomStepResult routeFailure(long profileId, Travel travel, PhantomNavigationResult result)
	{
		if (result.status() == PhantomNavigationResult.Status.COOLDOWN)
		{
			_navigation.consume(result.requestId());
			return put(profileId,travel.route(0,List.of())) ? retry("learning.navigation.cooldown") : PhantomStepResult.of(Type.CANCELLED,"learning.stopping");
		}
		if (!travel.returning() && Set.of(PhantomNavigationResult.Status.NO_PATH,PhantomNavigationResult.Status.ROUTE_OBSTRUCTED,PhantomNavigationResult.Status.ROUTE_BUDGET_EXCEEDED).contains(result.status()) && travel.destinationIndex()+1 < travel.destinations().size())
		{
			_navigation.consume(result.requestId());
			return put(profileId,travel.nextDestination()) ? retry("learning.trainer.replan") : PhantomStepResult.of(Type.CANCELLED,"learning.stopping");
		}
		cleanup(profileId);
		return fail("learning.route."+result.status().name().toLowerCase(java.util.Locale.ROOT));
	}
	private boolean put(long profileId, Travel travel) { synchronized (_monitor) { if (_state != State.RUNNING || _travels.size() >= LIMIT && !_travels.containsKey(profileId)) { return false; } _travels.put(profileId, travel); return true; } }
	private void cleanup(long profileId) { final Travel removed; synchronized (_monitor) { removed = _travels.remove(profileId); } if (removed != null && removed.requestId() > 0) { _navigation.cancel(profileId, removed.requestId()); } }
	private static boolean verified(PhantomNavigationResult result) { return result.route() != null && result.status() != PhantomNavigationResult.Status.DIRECT_UNVERIFIED_NO_GEODATA && !result.route().waypoints().isEmpty(); }
	private static PhantomStepResult retry(String reason) { return PhantomStepResult.retry(500, reason); }
	private static PhantomStepResult fail(String reason) { return PhantomStepResult.of(Type.FAIL_GOAL, reason); }

	public void beginStop()
	{
		final List<Map.Entry<Long, Travel>> owned;
		synchronized (_monitor) { if (_state == State.STOPPED) { return; } _state = State.STOPPING; owned = new ArrayList<>(_travels.entrySet()); _travels.clear(); }
		for (var entry : owned) { if (entry.getValue().requestId() > 0) { _navigation.cancel(entry.getKey(), entry.getValue().requestId()); } }
		if (_automatic != null) { _automatic.clear(); }
	}

	public boolean finishStop() { beginStop(); synchronized (_monitor) { if (!_active.isEmpty()) { return false; } _state = State.STOPPED; return true; } }

	/** Durable intent binds the exact class/index and a normal-world return anchor. */
	public static Optional<Request> request(PhantomGoal goal)
	{
		if (goal == null || !GOAL_TYPE.equals(goal.goalType()) || goal.status() != PhantomGoalStatus.ACTIVE || goal.target() == null || !"skill".equals(goal.target().namespace()) || !validConstraints(goal) || goal.deadlineEpochMillis() <= System.currentTimeMillis() || goal.deadlineEpochMillis() > System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(30)) { return Optional.empty(); }
		try
		{
			final String[] skill = goal.target().key().split(":", -1);
			if (skill.length != 2) { return Optional.empty(); }
			final var values = goal.constraints();
			return Optional.of(new Request(Math.toIntExact(values.get("class.id")), Math.toIntExact(values.get("class.index")), Integer.parseInt(skill[0]), Integer.parseInt(skill[1]), new PhantomNavigationPoint(Math.toIntExact(values.get("field.x")), Math.toIntExact(values.get("field.y")), Math.toIntExact(values.get("field.z")), Math.toIntExact(values.get("field.instance")))));
		}
		catch (IllegalArgumentException | ArithmeticException exception) { return Optional.empty(); }
	}

	private static boolean validConstraints(PhantomGoal goal)
	{
		if (goal.constraints().keySet().equals(CONSTRAINTS)) { return true; }
		final var expected = new HashSet<>(CONSTRAINTS);
		expected.addAll(Set.of("owner.revision", "owner.object", "retry.after"));
		return "learning.autonomous".equals(goal.purposeKey()) && goal.constraints().keySet().equals(expected) && goal.constraints().get("owner.revision") >= 0 && goal.constraints().get("owner.object") > 0 && goal.constraints().get("retry.after") >= 0;
	}

	public record Request(int classId, int classIndex, int skillId, int skillLevel, PhantomNavigationPoint field)
	{
		public Request
		{
			if (PlayerClass.getPlayerClass(classId) == null || classIndex < 0 || classIndex > 3 || skillId <= 0 || skillLevel <= 0 || skillLevel > 65535 || field == null || field.instanceId() != 0 || field.x() < -1_000_000 || field.x() > 1_000_000 || field.y() < -1_000_000 || field.y() > 1_000_000 || field.z() < -32768 || field.z() > 32767) { throw new IllegalArgumentException("Invalid class-bound learning journey."); }
		}
	}

	private record Travel(Player actor, long goalId, long goalRevision, Request request, boolean returning, int trainerNpcId, List<PhantomNavigationPoint> destinations, int destinationIndex, long deadlineNanos, long requestId, List<PhantomNavigationPoint> waypoints, int waypoint)
	{
		private PhantomNavigationPoint destination() { return destinations.get(destinationIndex); }
		private Travel route(long id, List<PhantomNavigationPoint> points) { return new Travel(actor, goalId, goalRevision, request, returning, trainerNpcId, destinations, destinationIndex, deadlineNanos, id, List.copyOf(points), 0); }
		private Travel next() { return new Travel(actor, goalId, goalRevision, request, returning, trainerNpcId, destinations, destinationIndex, deadlineNanos, requestId, waypoints, waypoint + 1); }
		private Travel nextDestination() { return new Travel(actor, goalId, goalRevision, request, returning, trainerNpcId, destinations, destinationIndex+1, deadlineNanos, 0, List.of(), 0); }
	}

	private enum State { NEW, RUNNING, STOPPING, STOPPED }
}
