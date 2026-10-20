/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.questinstance;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState;
import org.l2jmobius.gameserver.phantoms.activity.PhantomRelevanceSignal;
import org.l2jmobius.gameserver.phantoms.acquisition.quest.PhantomAcquisitionQuestCatalog;
import org.l2jmobius.gameserver.phantoms.acquisition.quest.PhantomAcquisitionQuestCatalog.Rule;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatMode;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatRequest;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatResult;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatService;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatService.StartStatus;
import org.l2jmobius.gameserver.phantoms.decision.PhantomCancellationToken;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoal;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStore;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationRequest;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationResult;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationService;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationService.SubmissionStatus;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.EquipItemRequest;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionService;
import org.l2jmobius.gameserver.phantoms.questinstance.PhantomQuestInstanceBackend.ActionResult;
import org.l2jmobius.gameserver.phantoms.questinstance.PhantomQuestInstanceBackend.ActionStatus;
import org.l2jmobius.gameserver.phantoms.questinstance.PhantomQuestInstanceBackend.Observation;
import org.l2jmobius.gameserver.phantoms.questinstance.PhantomQuestInstanceBackend.QuestStatus;
import org.l2jmobius.gameserver.phantoms.questinstance.PhantomQuestInstanceBackend.Target;
import org.l2jmobius.gameserver.phantoms.questinstance.PhantomQuestInstanceCatalog.Content;
import org.l2jmobius.gameserver.phantoms.questinstance.PhantomQuestInstanceCatalog.Kind;
import org.l2jmobius.gameserver.phantoms.questinstance.PhantomQuestInstanceCatalog.Operation;
import org.l2jmobius.gameserver.phantoms.questinstance.PhantomQuestInstanceCatalog.Step;
import org.l2jmobius.gameserver.phantoms.topology.PhantomRelevanceSignalPort;

/** Caller-driven bounded coordinator. Native scripts remain the only mutation owners. */
public final class PhantomQuestInstanceService implements org.l2jmobius.gameserver.phantoms.activity.PhantomSchedulerControlPort, org.l2jmobius.gameserver.phantoms.activity.PhantomNativeJourneyDemand, org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationLifecyclePort
{
	public static final String QUEST_GOAL_TYPE = "quest.play";
	public static final String CLASS_GOAL_TYPE = "class.transfer";
	public static final String INSTANCE_GOAL_TYPE = "instance.play";
	public static final String TARGET_NAMESPACE = "supported.content";
	public static final String CANDIDATE_KEY = "candidate.questinstance.play";
	public static final String ACTION_KEY = "questinstance.advance";
	private static final String SIGNAL_SOURCE = "questinstance.active";
	private static final int MAXIMUM_OPERATIONS = 128;
	private static final long SIGNAL_TTL_MILLIS = TimeUnit.MINUTES.toMillis(5);
	private static final long NAVIGATION_DEADLINE_NANOS = TimeUnit.MINUTES.toNanos(2);
	private static final double INTERACTION_DISTANCE = 200;
	private static final double COMBAT_DISTANCE = 1000;
	private static final long COMBAT_TIMEOUT_MILLIS = TimeUnit.MINUTES.toMillis(2);
	private final Object _monitor = new Object();
	private final PhantomGoalStore _goals;
	private final PhantomQuestInstanceCatalog _catalog;
	private final PhantomAcquisitionQuestCatalog _acquisitions;
	private final PhantomQuestInstanceBackend _backend;
	private final PhantomCombatService _combat;
	private final PhantomNavigationService _navigation;
	private final PhantomProgressionService _progression;
	private final PhantomRelevanceSignalPort _signals;
	private final Set<Long> _active = new HashSet<>();
	private final Map<Long, Travel> _travels = new HashMap<>();
	private final Map<Long, CombatOperation> _combats = new HashMap<>();
	private final Map<Long, Long> _signalSequences = new HashMap<>();
	private volatile State _state = State.NEW;
	private PhantomAutonomousCareer _automatic;
	private java.util.function.BiConsumer<Long, PhantomGoal> _suspendReturn = (_, _) -> { };

	public void enableAutonomous(org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository profiles,
		org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService materialization, java.util.function.BiPredicate<Long, Long> pauseFarm,
		java.util.function.LongPredicate allowed, java.util.function.LongFunction<org.l2jmobius.gameserver.phantoms.population.PhantomPopulationCatalog.CareerArchetype> preference, PhantomAutonomousCareerPolicy policy)
	{
		synchronized (_monitor)
		{
			if (_state != State.NEW || _automatic != null) { throw new IllegalStateException("Autonomous career must be installed once before start."); }
			_automatic = new PhantomAutonomousCareer(profiles, materialization, this, _catalog, _progression, pauseFarm, allowed, preference, policy);
			_backend.avoidCombatArea(_automatic::avoidedCell);
		}
	}

	void registerAutonomousCandidates(org.l2jmobius.gameserver.phantoms.decision.PhantomCandidateRegistry registry) { if (_automatic != null) { _automatic.registerCandidates(registry); } }
	void registerAutonomousHandlers(org.l2jmobius.gameserver.phantoms.decision.PhantomStepHandlerRegistry registry) { if (_automatic != null) { _automatic.registerHandlers(registry); } }
	public boolean ownsAutonomousIntent(long profileId, PhantomGoal root) { return _state == State.RUNNING && _automatic != null && _automatic.owns(profileId, root); }
	/** Admission for the visible career Decision before a farm route can be resumed. */
	public boolean permitsAutonomousVisibleDecision(long profileId, PhantomGoal root)
	{
		return _state == State.RUNNING && _automatic != null && _goals.load(profileId).map(stored -> stored.goal().equals(root)).orElse(false)
			&& (_automatic.owns(profileId, root) || _automatic.needsOrOwns(profileId, root));
	}
	public boolean needsOrOwnsAutonomousIntent(long profileId, PhantomGoal root) { return _state == State.RUNNING && _automatic != null && _automatic.needsOrOwns(profileId, root); }
	public String autonomousTravelEvidence(long profileId)
	{
		final CombatOperation owned; synchronized (_monitor) { owned = _combats.get(profileId); }
		return _backend.travelEvidence(profileId) + (owned == null ? "" : ";combat.step=" + owned.stepId() + ";npc=" + owned.npcId() + ";target=" + owned.targetObjectId() + ";cell=" + owned.cell() + ";session=" + _combat.find(profileId).orElse(null));
	}
	public Optional<PhantomGoal> autonomousIntent(long profileId) { return _automatic == null ? Optional.empty() : _automatic.intent(profileId); }
	public Optional<Observation> autonomousObservation(long profileId)
	{
		final var intent = autonomousIntent(profileId).orElse(null);
		final var content = intent == null || intent.target() == null ? null : _catalog.content(intent.target().key()).orElse(null);
		return content == null || _state != State.RUNNING ? Optional.empty() : Optional.ofNullable(_backend.observe(profileId, content));
	}
	public void deferAutonomousWhen(java.util.function.BiPredicate<Long, PhantomGoal> predicate)
	{
		synchronized (_monitor) { if (_state != State.NEW || _automatic == null) { throw new IllegalStateException("Career priority must be bound before start."); } _automatic.deferWhen(predicate); }
	}
	public void useFieldReturn(java.util.function.BiFunction<org.l2jmobius.gameserver.phantoms.decision.PhantomStepContext, PhantomGoal, org.l2jmobius.gameserver.phantoms.decision.PhantomStepResult> action)
	{
		useFieldReturn(action, (_, _) -> { });
	}
	public void useFieldReturn(java.util.function.BiFunction<org.l2jmobius.gameserver.phantoms.decision.PhantomStepContext, PhantomGoal, org.l2jmobius.gameserver.phantoms.decision.PhantomStepResult> action, java.util.function.BiConsumer<Long, PhantomGoal> suspend)
	{
		synchronized (_monitor) { if (_state != State.NEW || _automatic == null) { throw new IllegalStateException("Career return must bind before start."); } _automatic.useFieldReturn(action); _suspendReturn = Objects.requireNonNull(suspend); }
	}
	public void requireField(java.util.function.BiPredicate<PhantomGoal, org.l2jmobius.gameserver.model.actor.Player> predicate)
	{
		synchronized (_monitor) { if (_state != State.NEW || _automatic == null) { throw new IllegalStateException("Career field must bind before start."); } _automatic.requireField(predicate); }
	}

	boolean hasOwnedWork(long profileId) { synchronized (_monitor) { return _travels.containsKey(profileId) || _combats.containsKey(profileId); } }
	PhantomGoal currentRoot(long profileId) { return _goals.load(profileId).map(PhantomGoalStore.StoredGoal::goal).orElse(null); }
	void finishAutonomousJourney(long profileId) { cleanup(profileId); }

	@Override public void onPulse()
	{
		if (_state == State.RUNNING && _automatic != null)
		{
			final Map<Long, CombatOperation> owned; synchronized (_monitor) { owned = Map.copyOf(_combats); }
			for (var entry : owned.entrySet())
			{
				if (_combat.find(entry.getKey()).map(value -> value.result() == PhantomCombatResult.PLAYER_DEAD).orElse(false))
				{
					_automatic.observeUnsafe(entry.getKey(), new CombatFailure(PhantomCombatResult.PLAYER_DEAD, entry.getValue().cell(), entry.getValue().npcId()));
				}
			}
		}
		resumeAcceptedIntents(System.currentTimeMillis());
	}
	@Override public Set<Long> acceptedProfiles() { synchronized (_monitor) { return Set.copyOf(_signalSequences.keySet()); } }
	@Override public boolean requiresNativeContext(long profileId, long rootId, long rootRevision)
	{
		return _state == State.RUNNING && _automatic != null && _automatic.requiresNativeContext(profileId, rootId, rootRevision);
	}
	@Override public void beforeMaterialize(long profileId, int objectId) { }
	@Override public void afterPlayerLoad(long profileId, org.l2jmobius.gameserver.model.actor.Player player) { }
	@Override public void materializeSucceeded(long profileId, int objectId) { }
	@Override public void materializeAborted(long profileId, int objectId) { }
	@Override public void beforeStore(long profileId, org.l2jmobius.gameserver.model.actor.Player player)
	{
		final var root = _goals.load(profileId).map(PhantomGoalStore.StoredGoal::goal).orElse(null);
		final CombatOperation owned; synchronized (_monitor) { owned = _combats.get(profileId); }
		if (_automatic != null && root != null && owned != null)
		{
			final var session = _combat.find(profileId).orElse(null);
			if (session != null && (unsafe(session.result()) || session.phase() == org.l2jmobius.gameserver.phantoms.combat.PhantomCombatPhase.RETREATING))
			{
				_automatic.recordUnsafe(profileId, root, player, new CombatFailure(unsafe(session.result()) ? session.result() : PhantomCombatResult.LOW_HP_STOPPED, owned.cell(), owned.npcId()));
			}
		}
		if (_automatic != null && root != null) { _automatic.pause(profileId, root, player); }
		cleanup(profileId);
	}
	@Override public void afterStore(long profileId, org.l2jmobius.gameserver.model.actor.Player player) { }
	public void resumeAcceptedIntents(long nowEpochMillis)
	{
		if (_state == State.RUNNING && _automatic != null) { _automatic.resumeAccepted(nowEpochMillis); }
	}

	void publishAcceptedResume(long profileId, PhantomGoal intent, long nowEpochMillis)
	{
		final var root = _goals.load(profileId).map(PhantomGoalStore.StoredGoal::goal).orElse(null);
		if (_state == State.RUNNING && root != null && org.l2jmobius.gameserver.phantoms.decision.PhantomJourneyLifetime.alive(intent, nowEpochMillis) && validChild(profileId, root, intent) != null)
		{
			publishActive(profileId, Math.max(1, System.nanoTime()));
		}
	}

	public PhantomQuestInstanceService(PhantomGoalStore goals, PhantomQuestInstanceCatalog catalog, PhantomAcquisitionQuestCatalog acquisitions, PhantomQuestInstanceBackend backend, PhantomCombatService combat, PhantomNavigationService navigation, PhantomProgressionService progression, PhantomRelevanceSignalPort signals)
	{
		_goals = Objects.requireNonNull(goals, "goals");
		_catalog = Objects.requireNonNull(catalog, "catalog");
		_acquisitions = Objects.requireNonNull(acquisitions, "acquisitions");
		_backend = Objects.requireNonNull(backend, "backend");
		_combat = Objects.requireNonNull(combat, "combat");
		_navigation = Objects.requireNonNull(navigation, "navigation");
		_progression = Objects.requireNonNull(progression, "progression");
		_signals = Objects.requireNonNull(signals, "signals");
	}

	public boolean start()
	{
		_catalog.validateRuntime(_acquisitions);
		synchronized (_monitor)
		{
			if (_state != State.NEW)
			{
				return false;
			}
			_state = State.RUNNING;
			return true;
		}
	}

	public AdvanceResult advance(long profileId, long goalId, long goalRevision, PhantomActivityState activityState, long logicalNowNanos, PhantomCancellationToken token)
	{
		return advance(profileId, goalId, goalRevision, activityState, logicalNowNanos, token, null, null);
	}

	AdvanceResult advanceIntent(long profileId, PhantomGoal root, PhantomGoal intent, PhantomActivityState state, long now, PhantomCancellationToken token)
	{
		return advance(profileId, root.goalId(), root.revision(), state, now, token, root, intent);
	}

	private AdvanceResult advance(long profileId, long goalId, long goalRevision, PhantomActivityState activityState, long logicalNowNanos, PhantomCancellationToken token, PhantomGoal root, PhantomGoal intent)
	{
		Objects.requireNonNull(activityState, "activityState");
		Objects.requireNonNull(token, "token");
		if (!reserve(profileId))
		{
			return retry("questinstance.operation.busy");
		}
		try
		{
			if (token.isCancelled())
			{
				return new AdvanceResult(Status.CANCELLED, "questinstance.cancelled");
			}
			final PhantomGoal goal = intent == null ? validGoal(profileId, goalId, goalRevision) : validChild(profileId, root, intent);
			if (goal == null)
			{
				cleanup(profileId);
				return fail("questinstance.goal.stale");
			}
			final Content content = _catalog.content(goal.target().key()).orElse(null);
			if ((content == null) || !goalMatches(goal, content))
			{
				cleanup(profileId);
				return fail("questinstance.content.unsupported");
			}
			if ((goal.deadlineEpochMillis() > 0) && (goal.deadlineEpochMillis() <= System.currentTimeMillis()))
			{
				cleanup(profileId);
				return fail("questinstance.goal.expired");
			}
			publishActive(profileId, logicalNowNanos);
			if (activityState != PhantomActivityState.ACTIVE)
			{
				return retry("questinstance.active.required");
			}
			final Observation observation = _backend.observe(profileId, content);
			if (observation == null)
			{
				return retry("questinstance.actor.unavailable");
			}
			if (intent != null && (intent.constraints().getOrDefault("owner.object", -1L) != observation.playerObjectId()
				|| intent.constraints().getOrDefault("class.index", -1L) != observation.classIndex()
				|| !((intent.constraints().getOrDefault("class.from", -1L) == observation.classId() && observation.baseClassId() == observation.classId())
					|| (intent.constraints().getOrDefault("class.to", -1L) == observation.classId() && observation.baseClassId() == observation.classId() && observation.quest().status() == QuestStatus.COMPLETED))))
			{
				cleanup(profileId); return fail("career.native.identity_changed");
			}
			final AdvanceResult combatSettlement = settleOwnedCombat(profileId, content);
			if (combatSettlement != null)
			{
				return combatSettlement;
			}
			if (observation.dead() || observation.busy())
			{
				return retry("questinstance.actor.busy");
			}
			// The durable child owns native Combat across ordinary Decision replans/retry exhaustion.
			final PhantomCancellationToken combatToken = ownedCombatToken(profileId, root, intent, token);
			return switch (content.kind())
			{
				case QUEST -> "quest.102".equals(content.id()) ? q102(profileId, content, observation, logicalNowNanos, token) : q152(profileId, content, observation, logicalNowNanos, token);
				case CLASS_PATH -> warrior(profileId, content, observation, logicalNowNanos, combatToken,
					goal.deadlineEpochMillis() == 0 ? TimeUnit.NANOSECONDS.toMillis(NAVIGATION_DEADLINE_NANOS) : Math.max(0, goal.deadlineEpochMillis() - System.currentTimeMillis()));
				case KAMALOKA -> kamaloka(profileId, content, observation, logicalNowNanos, token);
				case PAILAKA -> pailaka(profileId, content, observation, logicalNowNanos, token);
			};
		}
		finally
		{
			release(profileId);
		}
	}

	/** Settles a previously started shared Combat before routing by freshly changed native truth. */
	private AdvanceResult settleOwnedCombat(long profileId, Content content)
	{
		final CombatOperation owned;
		synchronized (_monitor)
		{
			owned = _combats.get(profileId);
		}
		if (owned == null)
		{
			return null;
		}
		final var existing = _combat.find(profileId).orElse(null);
		if (existing == null)
		{
			synchronized (_monitor)
			{
				_combats.remove(profileId, owned);
			}
			return null;
		}
		if (!owned.contentId().equals(content.id()) || (owned.targetObjectId() != existing.targetObjectId()) || !_combat.matchesContentSession(profileId, existing.targetObjectId(), combatOwner(content)))
		{
			return retry("questinstance.combat.foreign");
		}
		if (!existing.result().terminal())
		{
			return retry("questinstance.combat.active");
		}
		final var terminal = _combat.consumeTerminal(profileId).orElse(null);
		if (terminal == null)
		{
			return retry("questinstance.combat.cleanup");
		}
		synchronized (_monitor)
		{
			_combats.remove(profileId, owned);
		}
		return combatOutcome(owned, terminal.result());
	}

	private static boolean unsafe(PhantomCombatResult result)
	{
		return result == PhantomCombatResult.PLAYER_DEAD || result == PhantomCombatResult.LOW_HP_STOPPED || result == PhantomCombatResult.TIMEOUT || result == PhantomCombatResult.TARGET_LOST || result == PhantomCombatResult.UNSUPPORTED_LOADOUT;
	}

	private PhantomCancellationToken ownedCombatToken(long profileId, PhantomGoal root, PhantomGoal intent, PhantomCancellationToken fallback)
	{
		return intent == null ? fallback : () -> _state != State.RUNNING || _automatic == null || !_automatic.permitsOwnedCombat(profileId, root, intent) || validChild(profileId, root, intent) == null;
	}

	private static AdvanceResult combatOutcome(CombatOperation owned, PhantomCombatResult result)
	{
		return result.victory() ? progress("questinstance.combat.victory") : result == PhantomCombatResult.CANCELLED ? new AdvanceResult(Status.CANCELLED, "questinstance.combat.cancelled")
			: new AdvanceResult(Status.RETRY, "questinstance.combat." + result.name().toLowerCase(java.util.Locale.ROOT), unsafe(result) ? new CombatFailure(result, owned.cell(), owned.npcId()) : null);
	}

	/** An unsafe career returns through the existing journey adapter, settling its combat first. */
	AdvanceResult prepareReturn(long profileId, PhantomGoal root, PhantomGoal intent, PhantomCancellationToken token)
	{
		if (!reserve(profileId)) { return retry("questinstance.operation.busy"); }
		try
		{
			if (token.isCancelled() || validChild(profileId, root, intent) == null || _automatic == null || !_automatic.permitsOwnedCombat(profileId, root, intent)) { return new AdvanceResult(Status.CANCELLED, "career.native.preparation_owner_pending"); }
			final Content content = _catalog.content(intent.target().key()).orElseThrow();
			final var settlement = settleOwnedCombat(profileId, content);
			if (settlement != null) { return settlement.status() == Status.CANCELLED ? retry("career.native.previous_combat_settled") : settlement; }
			final var actor = _backend.observe(profileId, content);
			if (actor == null || actor.dead()) { return retry("career.native.recovery_pending"); }
			final var pursuer = _combat.observeNativePursuer(profileId).orElse(null);
			if (pursuer == null) { return null; }
			cancelTravel(profileId);
			_suspendReturn.accept(profileId, intent);
			final var combatStep = content.steps().stream().filter(step -> step.operation() == Operation.COMBAT).findFirst().orElse(null);
			boolean retreat = true;
			if (combatStep != null)
			{
				final var readiness = _backend.prepareCombat(profileId, content, combatStep, actor.fingerprint());
				if (readiness.status() == ActionStatus.IDEMPOTENT) { retreat = false; }
				else if (!"questinstance.combat.retreat_required".equals(readiness.reasonKey())) { return retry(readiness.reasonKey()); }
			}
			// Recovery can make defense viable; never restart unconditional escape from the same pursuer.
			return startCombat(profileId, content, retreat ? "preparation.retreat" : "preparation.defense", pursuer.objectId(), pursuer.npcId(), actor,
				ownedCombatToken(profileId, root, intent, token), retreat);
		}
		finally { release(profileId); }
	}

	private AdvanceResult q102(long profileId, Content content, Observation observation, long now, PhantomCancellationToken token)
	{
		return switch (observation.quest().status())
		{
			case ABSENT -> interact(profileId, content, "prepare.alberius", observation, now);
			case CREATED -> content.eligible(observation.level(), observation.race(), observation.classId()) ? interact(profileId, content, "start.alberius", observation, now) : fail("questinstance.q102.ineligible");
			case COMPLETED -> complete(profileId, "questinstance.q102.complete");
			case STARTED -> switch (observation.quest().cond())
			{
				case 1 -> interact(profileId, content, "talk.cobendell", observation, now);
				case 2 -> questCombat(profileId, content, observation, "collect", 966, now, token);
				case 3 -> interact(profileId, content, "talk.cobendell", observation, now);
				case 4 -> interact(profileId, content, "report.alberius", observation, now);
				case 5 -> observation.itemCount(1131) > 0 ? interact(profileId, content, "sentinel.berros", observation, now) : observation.itemCount(1132) > 0 ? interact(profileId, content, "sentinel.veltress", observation, now) : observation.itemCount(1133) > 0 ? interact(profileId, content, "sentinel.rayen", observation, now) : observation.itemCount(1134) > 0 ? interact(profileId, content, "sentinel.gartrandell", observation, now) : retry("questinstance.q102.native_transition_pending");
				case 6 -> interact(profileId, content, "complete.alberius", observation, now);
				default -> fail("questinstance.q102.cond.unsupported");
			};
		};
	}

	private AdvanceResult q152(long profileId, Content content, Observation observation, long now, PhantomCancellationToken token)
	{
		return switch (observation.quest().status())
		{
			case ABSENT -> interact(profileId, content, "prepare.harris", observation, now);
			case CREATED -> content.eligible(observation.level(), observation.race(), observation.classId()) ? interact(profileId, content, "start.harris", observation, now) : fail("questinstance.q152.ineligible");
			case COMPLETED -> complete(profileId, "questinstance.q152.complete");
			case STARTED -> switch (observation.quest().cond())
			{
				case 1 -> interact(profileId, content, "talk.altran.begin", observation, now);
				case 2 -> questCombat(profileId, content, observation, "collect", 1010, now, token);
				case 3 -> interact(profileId, content, "talk.altran", observation, now);
				case 4 -> interact(profileId, content, "complete.harris", observation, now);
				default -> fail("questinstance.q152.cond.unsupported");
			};
		};
	}

	private AdvanceResult warrior(long profileId, Content content, Observation observation, long now, PhantomCancellationToken token, long remainingMillis)
	{
		if ((observation.classId() == 1) && (observation.baseClassId() == 1))
		{
			return complete(profileId, "questinstance.warrior.complete");
		}
		if ((observation.classId() != 0) || (observation.baseClassId() != 0))
		{
			return fail("questinstance.warrior.class.invalid");
		}
		if (observation.quest().status() == QuestStatus.STARTED)
		{
			final var pursuer = _combat.observeNativePursuer(profileId).orElse(null);
			if (pursuer != null)
			{
				cancelTravel(profileId);
				final var readiness = _backend.prepareCombat(profileId, content, content.step("collect.skeleton").orElseThrow(), observation.fingerprint());
				if ("questinstance.combat.retreat_required".equals(readiness.reasonKey())) { return startCombat(profileId, content, "travel.retreat", pursuer.objectId(), pursuer.npcId(), observation, token, true); }
				if (readiness.status() != ActionStatus.IDEMPOTENT) { return retry(readiness.reasonKey()); }
				return startCombat(profileId, content, "travel.defense", pursuer.objectId(), pursuer.npcId(), observation, token, false);
			}
		}
		return switch (observation.quest().status())
		{
			case ABSENT -> interact(profileId, content, "prepare.auron", observation, now, remainingMillis);
			case CREATED -> content.eligible(observation.level(), observation.race(), observation.classId()) ? interact(profileId, content, "start.auron", observation, now, remainingMillis) : fail("questinstance.q401.ineligible");
			case COMPLETED ->
			{
				if ((observation.level() < 20) || (observation.itemCount(1145) <= 0))
				{
					yield fail("questinstance.warrior.prerequisite");
				}
				yield interact(profileId, content, "profession.warrior", observation, now, remainingMillis);
			}
			case STARTED -> switch (observation.quest().cond())
			{
				case 1 -> interact(profileId, content, "simplon.begin", observation, now, remainingMillis);
				case 2 -> combat(profileId, content, observation, "collect.skeleton", now, token, remainingMillis);
				case 3 -> interact(profileId, content, "simplon.handin", observation, now, remainingMillis);
				case 4 -> interact(profileId, content, "auron.sword", observation, now, remainingMillis);
				case 5 -> observation.activeWeaponItemId() == 1142 ? combat(profileId, content, observation, "collect.spider", now, token, remainingMillis) : equip(profileId, content, observation, "equip.sword", token);
				case 6 -> interact(profileId, content, "auron.complete", observation, now, remainingMillis);
				default -> fail("questinstance.q401.cond.unsupported");
			};
		};
	}

	private AdvanceResult kamaloka(long profileId, Content content, Observation observation, long now, PhantomCancellationToken token)
	{
		if (observation.instanceReuseUntilMillis() > System.currentTimeMillis())
		{
			return complete(profileId, "questinstance.kamaloka.reuse_observed");
		}
		if ((observation.instanceTemplateId() != 0) && (observation.instanceTemplateId() != content.instanceTemplateId()))
		{
			return fail("questinstance.kamaloka.foreign_instance");
		}
		if (observation.instanceTemplateId() == content.instanceTemplateId())
		{
			return combat(profileId, content, observation, "combat.boss", now, token);
		}
		if (!content.eligible(observation.level(), observation.race(), observation.classId()))
		{
			return fail("questinstance.kamaloka.level");
		}
		if ((observation.partySize() == 0) || !observation.partyLeader() || !observation.partyLevelEligible())
		{
			return retry("questinstance.kamaloka.party_required");
		}
		return interact(profileId, content, "enter", observation, now);
	}

	private AdvanceResult pailaka(long profileId, Content content, Observation observation, long now, PhantomCancellationToken token)
	{
		return switch (observation.quest().status())
		{
			case ABSENT -> interact(profileId, content, "prepare.adler", observation, now);
			case CREATED -> content.eligible(observation.level(), observation.race(), observation.classId()) ? interact(profileId, content, "start.adler", observation, now) : fail("questinstance.pailaka.ineligible");
			case COMPLETED -> complete(profileId, "questinstance.pailaka.complete");
			case STARTED ->
			{
				if ((observation.instanceTemplateId() != 0) && (observation.instanceTemplateId() != content.instanceTemplateId()))
				{
					yield fail("questinstance.pailaka.foreign_instance");
				}
				if (observation.instanceTemplateId() == 0)
				{
					yield observation.quest().cond() == 1 ? interact(profileId, content, "enter", observation, now) : fail("questinstance.pailaka.instance_missing");
				}
				yield switch (observation.quest().cond())
				{
					case 1 -> interact(profileId, content, "sinai.arm", observation, now);
					case 2 -> observation.activeWeaponItemId() == 13034 ? combat(profileId, content, observation, "combat.hillas", now, token) : equip(profileId, content, observation, "equip.sprite", token);
					case 3 -> interact(profileId, content, "inspector.water", observation, now);
					case 4 -> observation.activeWeaponItemId() == 13035 ? combat(profileId, content, observation, "combat.papion", now, token) : equip(profileId, content, observation, "equip.enhanced", token);
					case 5 -> combat(profileId, content, observation, "combat.kinsus", now, token);
					case 6 -> interact(profileId, content, "inspector.fire", observation, now);
					case 7 -> observation.activeWeaponItemId() == 13036 ? combat(profileId, content, observation, "combat.gargos", now, token) : equip(profileId, content, observation, "equip.icefire", token);
					case 8 -> combat(profileId, content, observation, "combat.adiantum", now, token);
					case 9 -> interact(profileId, content, "terminal.adler", observation, now);
					default -> fail("questinstance.pailaka.cond.unsupported");
				};
			}
		};
	}

	private AdvanceResult questCombat(long profileId, Content content, Observation observation, String stepId, int itemId, long now, PhantomCancellationToken token)
	{
		final Rule rule = _acquisitions.rule(content.acquisitionRuleId()).orElse(null);
		if ((rule == null) || (rule.questId() != content.questId()) || !rule.allowedConds().contains(observation.quest().cond()) || (observation.itemCount(itemId) < 0) || (observation.itemCount(itemId) > (rule.itemCap() + 1L)))
		{
			return fail("questinstance.acquisition.binding");
		}
		if ((observation.itemCount(itemId) < rule.itemCap()) && !rule.supports(observation.quest().cond(), observation.itemCount(itemId), rule.targetNpcIds().getFirst(), false))
		{
			return fail("questinstance.acquisition.ineligible");
		}
		return combat(profileId, content, observation, stepId, now, token);
	}

	private AdvanceResult interact(long profileId, Content content, String stepId, Observation observation, long now)
	{
		return interact(profileId, content, stepId, observation, now, TimeUnit.NANOSECONDS.toMillis(NAVIGATION_DEADLINE_NANOS));
	}

	private AdvanceResult interact(long profileId, Content content, String stepId, Observation observation, long now, long remainingMillis)
	{
		final Step step = content.step(stepId).orElseThrow();
		final AdvanceResult reach = reach(profileId, content, step, observation, now, INTERACTION_DISTANCE, remainingMillis);
		if (reach != null)
		{
			return reach;
		}
		final ActionResult result = _backend.invoke(profileId, content, step, observation.fingerprint());
		return switch (result.status())
		{
			case ISSUED -> progress(result.reasonKey());
			case IDEMPOTENT, UNAVAILABLE -> retry(result.reasonKey());
			case STALE -> progress(result.reasonKey());
			case REJECTED, FAILURE -> fail(result.reasonKey());
		};
	}

	private AdvanceResult equip(long profileId, Content content, Observation observation, String stepId, PhantomCancellationToken token)
	{
		final Step step = content.step(stepId).orElseThrow();
		final int objectId = observation.itemObjectId(step.itemId());
		if (objectId <= 0)
		{
			return fail("questinstance.equip.item_missing");
		}
		final var result = _progression.equipOwnedItem(new EquipItemRequest(profileId, objectId, token));
		return switch (result.status())
		{
			case SUCCESS, IDEMPOTENT -> progress("questinstance.equip.observed");
			case OPERATION_IN_PROGRESS, ACTOR_NOT_MATERIALIZED, ACTOR_STATE_REJECTED -> retry("questinstance.equip.retry");
			case CANCELLED -> new AdvanceResult(Status.CANCELLED, "questinstance.equip.cancelled");
			default -> fail("questinstance.equip.rejected");
		};
	}

	private AdvanceResult combat(long profileId, Content content, Observation observation, String stepId, long now, PhantomCancellationToken token)
	{
		return combat(profileId, content, observation, stepId, now, token, TimeUnit.NANOSECONDS.toMillis(NAVIGATION_DEADLINE_NANOS));
	}

	private AdvanceResult combat(long profileId, Content content, Observation observation, String stepId, long now, PhantomCancellationToken token, long remainingMillis)
	{
		final Step step = content.step(stepId).orElseThrow();
		final CombatOperation owned;
		synchronized (_monitor)
		{
			owned = _combats.get(profileId);
		}
		final var existing = _combat.find(profileId).orElse(null);
		if (existing != null)
		{
			final String owner = combatOwner(content);
			if ((owned == null) || !owned.contentId().equals(content.id()) || !owned.stepId().equals(step.id()) || (owned.targetObjectId() != existing.targetObjectId()) || !_combat.matchesContentSession(profileId, existing.targetObjectId(), owner))
			{
				return retry("questinstance.combat.foreign");
			}
			if (!existing.result().terminal())
			{
				return retry("questinstance.combat.active");
			}
			final var terminal = _combat.consumeTerminal(profileId).orElse(null);
			if (terminal == null)
			{
				return retry("questinstance.combat.cleanup");
			}
			synchronized (_monitor)
			{
				_combats.remove(profileId, owned);
			}
			return combatOutcome(owned, terminal.result());
		}
		if (content.kind() == Kind.CLASS_PATH)
		{
			final var readiness = _backend.prepareCombat(profileId, content, step, observation.fingerprint());
			if (readiness.status() != ActionStatus.IDEMPOTENT) { return retry(readiness.reasonKey()); }
		}
		final Target target = _backend.locate(profileId, content, step);
		if (target == null)
		{
			return retry("questinstance.combat.target_pending");
		}
		final AdvanceResult reach = reach(profileId, content, step, observation, now, COMBAT_DISTANCE, remainingMillis);
		if (reach != null)
		{
			return reach;
		}
		if (!target.liveObject())
		{
			return retry("questinstance.combat.target_pending");
		}
		return startCombat(profileId, content, step.id(), target.objectId(), target.npcId(), observation, token, false);
	}

	private AdvanceResult startCombat(long profileId, Content content, String stepId, int targetObjectId, int npcId, Observation observation, PhantomCancellationToken token, boolean retreat)
	{
		final String owner = combatOwner(content);
		// Career preparation consumes ordinary resources; retain the existing native
		// victory/pickup pipeline for its owned PvE. Retreat never waits for loot.
		final var request = new PhantomCombatRequest(profileId, targetObjectId, PhantomCombatMode.MELEE_PHYSICAL, !retreat, !retreat && content.kind() == Kind.CLASS_PATH, COMBAT_TIMEOUT_MILLIS, token);
		final var started = retreat ? _combat.startContentRetreatSession(request, owner) : _combat.startContentSession(request, owner);
		if ((started.status() == StartStatus.ACCEPTED) || (started.status() == StartStatus.IDEMPOTENT))
		{
			synchronized (_monitor)
			{
				if ((_combats.size() >= MAXIMUM_OPERATIONS) && !_combats.containsKey(profileId))
				{
					_combat.cancel(profileId);
					return retry("questinstance.operation.capacity");
				}
				_combats.put(profileId, new CombatOperation(content.id(), stepId, targetObjectId, npcId, PhantomAutonomousCareer.cell(observation.x(), observation.y())));
			}
			return progress("questinstance.combat.started");
		}
		return switch (started.status())
		{
			case REJECTED_ACTOR, REJECTED_CAPACITY, REJECTED_STATE, REJECTED_EXISTING -> retry("questinstance.combat.retry");
			case CANCELLED -> new AdvanceResult(Status.CANCELLED, "questinstance.combat.cancelled");
			default -> fail("questinstance.combat.rejected." + started.status().name());
		};
	}

	/** Returns null when the exact native target is ready for the requested operation. */
	private AdvanceResult reach(long profileId, Content content, Step step, Observation observation, long now, double requiredDistance, long remainingMillis)
	{
		final Target target = _backend.locate(profileId, content, step);
		if (target == null)
		{
			return retry("questinstance.target.unavailable");
		}
		if (target.liveObject() && (target.distance() <= requiredDistance))
		{
			cancelTravel(profileId);
			return null;
		}
		Travel travel;
		synchronized (_monitor)
		{
			travel = _travels.get(profileId);
		}
		if ((travel != null) && (!travel.contentId().equals(content.id()) || !travel.stepId().equals(step.id()) || !sameDestination(travel.target(), target)))
		{
			cancelTravel(profileId);
			travel = null;
		}
		if (travel == null)
		{
			final PhantomNavigationPoint origin = new PhantomNavigationPoint(observation.x(), observation.y(), observation.z(), observation.instanceId());
			final var options = _backend.travelOptions(profileId, content, remainingMillis);
			List<PhantomNavigationPoint> destinations = options == null ? List.of(new PhantomNavigationPoint(target.x(), target.y(), target.z(), target.instanceId()))
				: _backend.approaches(profileId, content, step, target, requiredDistance, _navigation.arrivalRadius());
			if (destinations.isEmpty() || destinations.size() > 17) { return retry("questinstance.navigation.no_approach"); }
			TravelMode mode = options == null ? TravelMode.LEGACY_WALK : TravelMode.ITINERARY;
			if (options != null && origin.distanceTo(destinations.getFirst()) > _navigation.policy().maximumLocalStraightDistance())
			{
				final var leg = nearbyGatekeeper(options, origin, target);
				if (leg == null)
				{
					mode = TravelMode.PROVEN_WALK;
					// A free long journey may advance through separately proven local WALK corridors.
					// Its original career deadline and every native search/geo bound remain unchanged.
					if (remainingMillis > TimeUnit.NANOSECONDS.toMillis(NAVIGATION_DEADLINE_NANOS))
					{
						final var local = _backend.walkApproaches(profileId, content, step, target, requiredDistance, _navigation.arrivalRadius());
						if (!local.isEmpty() && local.size() <= 17) { destinations = local; }
					}
				}
				else
				{
					final var source = new PhantomNavigationPoint(leg.sourceX(), leg.sourceY(), leg.sourceZ(), 0);
					if (origin.distanceTo(source) > org.l2jmobius.gameserver.model.actor.Npc.INTERACTION_DISTANCE)
					{
						// A free native approach moves first; the remaining complete itinerary is proved before any fee.
						mode = TravelMode.GATEKEEPER_APPROACH; destinations = List.of(source);
					}
				}
			}
			travel = new Travel(content.id(), step.id(), target, origin, 0, List.of(), 0, mode, null, 0, destinations, 0);
			synchronized (_monitor)
			{
				if ((_travels.size() >= MAXIMUM_OPERATIONS) && !_travels.containsKey(profileId))
				{
					return retry("questinstance.operation.capacity");
				}
				_travels.put(profileId, travel);
			}
		}
		if (travel.requestId() == 0)
		{
			final var origin = new PhantomNavigationPoint(observation.x(), observation.y(), observation.z(), observation.instanceId());
			final long deadline = now > (Long.MAX_VALUE - NAVIGATION_DEADLINE_NANOS) ? Long.MAX_VALUE : now + NAVIGATION_DEADLINE_NANOS;
			final var request = new PhantomNavigationRequest(profileId, origin, travel.destination(), Math.max(0, now), Math.max(now + 1, deadline), 100_000);
			final var options = travel.mode() == TravelMode.ITINERARY ? _backend.travelOptions(profileId, content, remainingMillis) : null;
			if (travel.mode() == TravelMode.ITINERARY && options == null) { return retry("questinstance.actor.unavailable"); }
			final var submission = travel.mode() == TravelMode.ITINERARY ? _navigation.submitTravelPlan(request, options)
				: travel.mode() == TravelMode.LEGACY_WALK ? _navigation.submit(request) : _navigation.submitWalkCorridor(request);
			if (submission.status() == SubmissionStatus.REJECTED) { return retry("questinstance.navigation.busy"); }
			final List<PhantomNavigationPoint> immediate = travel.mode() == TravelMode.LEGACY_WALK && submission.status() == SubmissionStatus.COMPLETED && submission.immediateResult() != null && submission.immediateResult().route() != null ? submission.immediateResult().route().waypoints() : List.of();
			travel = travel.withRequest(origin, submission.requestId(), immediate);
			synchronized (_monitor) { _travels.put(profileId, travel); }
			return retry("questinstance.navigation.requested");
		}
		if (travel.mode() == TravelMode.ITINERARY && travel.plan() == null)
		{
			final var result = _navigation.consume(travel.requestId()).orElse(null);
			if (result == null) { return retry("questinstance.navigation.pending"); }
			if (Set.of(PhantomNavigationResult.Status.COOLDOWN, PhantomNavigationResult.Status.PROFILE_BUSY, PhantomNavigationResult.Status.QUEUE_BACKPRESSURE).contains(result.status()))
			{
				travel = travel.withRequest(travel.origin(), 0, List.of());
				synchronized (_monitor) { _travels.put(profileId, travel); }
				return retry("questinstance.navigation.transient");
			}
			final var plan = _navigation.consumeTravelPlan(travel.requestId()).orElse(null);
			if (result.route() == null || result.route().mode() == org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationRoute.Mode.DIRECT_UNVERIFIED_NO_GEODATA || plan == null)
			{
				// A failed complete itinerary is not a failed durable journey. Reuse the existing local WALK owner.
				if (remainingMillis > TimeUnit.NANOSECONDS.toMillis(NAVIGATION_DEADLINE_NANOS)
					&& travel.origin().distanceTo(travel.destination()) > org.l2jmobius.gameserver.phantoms.navigation.PhantomNativeTravelActions.LOCAL_WALK_DISTANCE
					&& Set.of(PhantomNavigationResult.Status.NO_PATH, PhantomNavigationResult.Status.ROUTE_OBSTRUCTED, PhantomNavigationResult.Status.ROUTE_BUDGET_EXCEEDED).contains(result.status()))
				{
					final var local = _backend.walkApproaches(profileId, content, step, target, requiredDistance, _navigation.arrivalRadius());
					if (!local.isEmpty() && local.size() <= 17)
					{
						_navigation.cancel(profileId, travel.requestId());
						travel = new Travel(content.id(), step.id(), target, new PhantomNavigationPoint(observation.x(), observation.y(), observation.z(), observation.instanceId()), 0, List.of(), 0, TravelMode.PROVEN_WALK, null, 0, local, 0);
						synchronized (_monitor) { _travels.put(profileId, travel); }
						return retry("questinstance.navigation.local_checkpoint_retry");
					}
				}
				if (travel.destinationIndex() + 1 < travel.destinations().size())
				{
					travel = travel.withDestination(travel.destinationIndex() + 1);
					synchronized (_monitor) { _travels.put(profileId, travel); }
					return retry("questinstance.navigation.approach_retry");
				}
				cancelTravel(profileId); return retry("questinstance.navigation.no_path");
			}
			if (!plan.stages().getFirst().from().equals(travel.origin()) || !plan.stages().getLast().to().equals(travel.destination()))
			{
				cancelTravel(profileId); return fail("questinstance.navigation.plan_identity");
			}
			travel = travel.withPlan(plan);
			synchronized (_monitor) { _travels.put(profileId, travel); }
			return progress("questinstance.navigation.ready");
		}
		if (travel.plan() != null)
		{
			final var stage = travel.plan().stages().get(travel.stage());
			if (stage.mode() == org.l2jmobius.gameserver.phantoms.navigation.PhantomTravelStrategy.Mode.GK)
			{
				final var moved = _backend.gatekeeper(profileId, content, stage.gatekeeper(), observation.fingerprint());
				if (moved.status() == ActionStatus.ISSUED || moved.status() == ActionStatus.IDEMPOTENT)
				{
					final var after = moved.after();
					if (after == null || after.instanceId() != stage.to().instanceId() || stage.to().distanceTo(new PhantomNavigationPoint(after.x(), after.y(), after.z(), after.instanceId())) > _navigation.arrivalRadius()) { cancelTravel(profileId); return fail("questinstance.gatekeeper.destination"); }
					if (travel.stage() + 1 == travel.plan().stages().size()) { cancelTravel(profileId); return progress("questinstance.navigation.arrived"); }
					travel = travel.withStage(travel.stage() + 1);
					synchronized (_monitor) { _travels.put(profileId, travel); }
					return progress("questinstance.gatekeeper.arrived");
				}
				if (moved.status() == ActionStatus.UNAVAILABLE) { return retry(moved.reasonKey()); }
				if (moved.status() == ActionStatus.STALE) { cancelTravel(profileId); return progress(moved.reasonKey()); }
				cancelTravel(profileId); return fail(moved.reasonKey());
			}
			if (stage.mode() != org.l2jmobius.gameserver.phantoms.navigation.PhantomTravelStrategy.Mode.WALK) { cancelTravel(profileId); return fail("questinstance.navigation.unsupported_stage"); }
		}
		if (travel.mode() != TravelMode.ITINERARY && travel.waypoints().isEmpty())
		{
			final Optional<PhantomNavigationResult> result = _navigation.consume(travel.requestId());
			if (result.isEmpty())
			{
				return retry("questinstance.navigation.pending");
			}
			if (Set.of(PhantomNavigationResult.Status.COOLDOWN, PhantomNavigationResult.Status.PROFILE_BUSY, PhantomNavigationResult.Status.QUEUE_BACKPRESSURE).contains(result.orElseThrow().status()))
			{
				travel = travel.withRequest(travel.origin(), 0, List.of());
				synchronized (_monitor) { _travels.put(profileId, travel); }
				return retry("questinstance.navigation.transient");
			}
			if (result.orElseThrow().route() == null || travel.mode() != TravelMode.LEGACY_WALK && result.orElseThrow().route().mode() == org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationRoute.Mode.DIRECT_UNVERIFIED_NO_GEODATA)
			{
				if (travel.destinationIndex() + 1 < travel.destinations().size())
				{
					travel = travel.withDestination(travel.destinationIndex() + 1);
					synchronized (_monitor) { _travels.put(profileId, travel); }
					return retry("questinstance.navigation.approach_retry");
				}
				cancelTravel(profileId);
				return retry("questinstance.navigation.no_path");
			}
			travel = travel.withWaypoints(result.orElseThrow().route().waypoints());
			synchronized (_monitor)
			{
				_travels.put(profileId, travel);
			}
			return progress("questinstance.navigation.ready");
		}
		final List<PhantomNavigationPoint> points = travel.plan() == null ? travel.waypoints() : travel.plan().stages().get(travel.stage()).points();
		final PhantomNavigationPoint waypoint = points.get(travel.waypoint());
		final double dx = (double) waypoint.x() - observation.x();
		final double dy = (double) waypoint.y() - observation.y();
		final double dz = (double) waypoint.z() - observation.z();
		if (Math.sqrt((dx * dx) + (dy * dy) + (dz * dz)) <= _navigation.arrivalRadius())
		{
			final int next = travel.waypoint() + 1;
			if (next >= points.size())
			{
				if (travel.plan() != null && travel.stage() + 1 < travel.plan().stages().size())
				{
					travel = travel.withStage(travel.stage() + 1);
					synchronized (_monitor) { _travels.put(profileId, travel); }
					return progress("questinstance.navigation.stage");
				}
				cancelTravel(profileId);
				return progress(travel.mode() == TravelMode.GATEKEEPER_APPROACH ? "questinstance.gatekeeper.approached" : "questinstance.navigation.arrived");
			}
			travel = travel.withWaypoint(next);
			synchronized (_monitor)
			{
				_travels.put(profileId, travel);
			}
			return progress("questinstance.navigation.waypoint");
		}
		final Target waypointTarget = new Target(0, travel.target().npcId(), waypoint.x(), waypoint.y(), waypoint.z(), waypoint.instanceId(), Math.sqrt((dx * dx) + (dy * dy) + (dz * dz)), false);
		final ActionResult moved = _backend.move(profileId, content, step, waypointTarget);
		return switch (moved.status())
		{
			case ISSUED, IDEMPOTENT, UNAVAILABLE -> retry(moved.reasonKey());
			case STALE -> progress(moved.reasonKey());
			case REJECTED, FAILURE -> fail(moved.reasonKey());
		};
	}

	private org.l2jmobius.gameserver.phantoms.background.PhantomNormalGatekeeperTravel.Leg nearbyGatekeeper(org.l2jmobius.gameserver.phantoms.navigation.PhantomTravelStrategy.Options options, PhantomNavigationPoint origin, Target target)
	{
		if (!options.gatekeeperAllowed()) { return null; }
		final int local = _navigation.policy().maximumLocalStraightDistance();
		return options.legs().stream().filter(leg -> Math.hypot((long) leg.sourceX() - origin.x(), (long) leg.sourceY() - origin.y()) <= local
			&& Math.hypot((long) leg.destinationX() - target.x(), (long) leg.destinationY() - target.y()) <= local
			&& org.l2jmobius.gameserver.phantoms.background.PhantomNormalGatekeeperTravel.fee(leg, options.classIndex(), options.level(), options.epochMinute()) <= options.adena())
			.min(java.util.Comparator.comparingDouble((org.l2jmobius.gameserver.phantoms.background.PhantomNormalGatekeeperTravel.Leg leg) -> Math.hypot((long) leg.sourceX() - origin.x(), (long) leg.sourceY() - origin.y()) + Math.hypot((long) leg.destinationX() - target.x(), (long) leg.destinationY() - target.y())).thenComparing(org.l2jmobius.gameserver.phantoms.background.PhantomNormalGatekeeperTravel.Leg::id)).orElse(null);
	}

	public boolean beginStop()
	{
		final List<Long> profiles;
		synchronized (_monitor)
		{
			if (_state == State.STOPPED)
			{
				return true;
			}
			if (_state == State.NEW)
			{
				_state = State.STOPPED;
				return true;
			}
			_state = State.STOPPING;
			profiles = new ArrayList<>(_travels.keySet());
			profiles.addAll(_combats.keySet().stream().filter(profile -> !profiles.contains(profile)).toList());
		}
		profiles.forEach(this::cleanup);
		return true;
	}

	public boolean finishStop()
	{
		beginStop();
		synchronized (_monitor)
		{
			if (!_active.isEmpty() || !_travels.isEmpty() || !_combats.isEmpty())
			{
				return false;
			}
			_state = State.STOPPED;
			if (_automatic != null) { _automatic.clear(); }
			_backend.clearTravelEvidence();
			return true;
		}
	}

	public Snapshot snapshot()
	{
		synchronized (_monitor)
		{
			return new Snapshot(_state, _active.size(), _travels.size(), _combats.size(), _signalSequences.size());
		}
	}

	private boolean reserve(long profileId)
	{
		if (profileId <= 0)
		{
			return false;
		}
		synchronized (_monitor)
		{
			return (_state == State.RUNNING) && (_active.size() < MAXIMUM_OPERATIONS) && _active.add(profileId);
		}
	}

	private void release(long profileId)
	{
		synchronized (_monitor)
		{
			_active.remove(profileId);
		}
	}

	private PhantomGoal validGoal(long profileId, long goalId, long goalRevision)
	{
		final PhantomGoal goal = _goals.load(profileId).map(PhantomGoalStore.StoredGoal::goal).orElse(null);
		return (goal != null) && (goal.goalId() == goalId) && (goal.revision() == goalRevision) && (goal.status() == PhantomGoalStatus.ACTIVE) && (goal.target() != null) && TARGET_NAMESPACE.equals(goal.target().namespace()) ? goal : null;
	}

	private static boolean goalMatches(PhantomGoal goal, Content content)
	{
		return switch (content.kind())
		{
			case QUEST -> QUEST_GOAL_TYPE.equals(goal.goalType());
			case CLASS_PATH -> CLASS_GOAL_TYPE.equals(goal.goalType());
			case KAMALOKA, PAILAKA -> INSTANCE_GOAL_TYPE.equals(goal.goalType());
		};
	}

	private PhantomGoal validChild(long profileId, PhantomGoal root, PhantomGoal intent)
	{
		final var current = _goals.load(profileId).map(PhantomGoalStore.StoredGoal::goal).orElse(null);
		if (!Objects.equals(root, current) || root.status() != PhantomGoalStatus.ACTIVE || !org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundGoalSpec.GOAL_TYPE.equals(root.goalType())
			|| intent.status() != PhantomGoalStatus.ACTIVE || !"career.autonomous".equals(intent.purposeKey()) || intent.goalId() != root.goalId() || !intent.subject().equals(root.subject())
			|| intent.constraints().getOrDefault("owner.revision", -1L) != root.revision() || intent.target() == null || !TARGET_NAMESPACE.equals(intent.target().namespace())) { return null; }
		final var content = _catalog.content(intent.target().key()).orElse(null);
		return content != null && content.kind() == Kind.CLASS_PATH && content.contentHash().equals(intent.payloadText()) ? intent : null;
	}

	private void publishActive(long profileId, long now)
	{
		final long sequence;
		synchronized (_monitor)
		{
			sequence = Math.max(Math.max(0, now), _signalSequences.getOrDefault(profileId, 0L) + 1);
			_signalSequences.put(profileId, sequence);
		}
		_signals.submit(profileId, new PhantomRelevanceSignal(SIGNAL_SOURCE, sequence, PhantomActivityState.ACTIVE, SIGNAL_TTL_MILLIS));
	}

	private void cleanup(long profileId)
	{
		cancelTravel(profileId);
		final CombatOperation combat;
		final Long sequence;
		synchronized (_monitor)
		{
			combat = _combats.remove(profileId);
			sequence = _signalSequences.remove(profileId);
		}
		if (combat != null)
		{
			_combat.cancel(profileId);
			_combat.consumeTerminal(profileId);
		}
		if (sequence != null)
		{
			_signals.withdraw(profileId, SIGNAL_SOURCE, sequence);
		}
	}

	private void cancelTravel(long profileId)
	{
		_backend.cancelMove(profileId);
		final Travel travel;
		synchronized (_monitor)
		{
			travel = _travels.remove(profileId);
		}
		if (travel != null && travel.requestId() > 0)
		{
			_navigation.cancel(profileId, travel.requestId());
			_navigation.consume(travel.requestId());
		}
	}

	private AdvanceResult complete(long profileId, String reason)
	{
		cleanup(profileId);
		return new AdvanceResult(Status.COMPLETE, reason);
	}

	private static String combatOwner(Content content)
	{
		return "questinstance." + content.id();
	}

	private static boolean sameDestination(Target left, Target right)
	{
		return (left.npcId() == right.npcId()) && (left.instanceId() == right.instanceId()) && (left.x() == right.x()) && (left.y() == right.y()) && (left.z() == right.z());
	}

	private static AdvanceResult progress(String reason)
	{
		return new AdvanceResult(Status.PROGRESS, reason);
	}

	private static AdvanceResult retry(String reason)
	{
		return new AdvanceResult(Status.RETRY, reason);
	}

	private static AdvanceResult fail(String reason)
	{
		return new AdvanceResult(Status.FAIL, reason);
	}

	public enum State
	{
		NEW,
		RUNNING,
		STOPPING,
		STOPPED
	}

	public enum Status
	{
		PROGRESS,
		RETRY,
		COMPLETE,
		FAIL,
		CANCELLED
	}

	public record CombatFailure(PhantomCombatResult result, long cell, int npcId) { }
	public record AdvanceResult(Status status, String reasonKey, CombatFailure combatFailure)
	{
		public AdvanceResult(Status status, String reasonKey) { this(status, reasonKey, null); }
		public AdvanceResult
		{
			Objects.requireNonNull(status, "status");
			if ((reasonKey == null) || reasonKey.isBlank())
			{
				throw new IllegalArgumentException("Reason key must not be blank.");
			}
		}
	}

	public record Snapshot(State state, int activeOperations, int travelOperations, int combatOperations, int relevanceSignals)
	{
	}

	private enum TravelMode { LEGACY_WALK, PROVEN_WALK, ITINERARY, GATEKEEPER_APPROACH }
	private record Travel(String contentId, String stepId, Target target, PhantomNavigationPoint origin, long requestId, List<PhantomNavigationPoint> waypoints, int waypoint, TravelMode mode, org.l2jmobius.gameserver.phantoms.navigation.PhantomTravelStrategy.Candidate plan, int stage, List<PhantomNavigationPoint> destinations, int destinationIndex)
	{
		private Travel
		{
			waypoints = List.copyOf(waypoints);
			destinations = List.copyOf(destinations);
		}

		private Travel withWaypoints(List<PhantomNavigationPoint> replacement)
		{
			return new Travel(contentId, stepId, target, origin, requestId, replacement, 0, mode, plan, stage, destinations, destinationIndex);
		}

		private Travel withWaypoint(int replacement)
		{
			return new Travel(contentId, stepId, target, origin, requestId, waypoints, replacement, mode, plan, stage, destinations, destinationIndex);
		}
		private PhantomNavigationPoint destination() { return destinations.get(destinationIndex); }
		private Travel withRequest(PhantomNavigationPoint from, long id, List<PhantomNavigationPoint> immediate) { return new Travel(contentId, stepId, target, from, id, immediate, 0, mode, null, 0, destinations, destinationIndex); }
		private Travel withDestination(int index) { return new Travel(contentId, stepId, target, origin, 0, List.of(), 0, mode, null, 0, destinations, index); }
		private Travel withPlan(org.l2jmobius.gameserver.phantoms.navigation.PhantomTravelStrategy.Candidate replacement) { return new Travel(contentId, stepId, target, origin, requestId, List.of(), 0, mode, replacement, 0, destinations, destinationIndex); }
		private Travel withStage(int replacement) { return new Travel(contentId, stepId, target, origin, requestId, List.of(), 0, mode, plan, replacement, destinations, destinationIndex); }
	}

	private record CombatOperation(String contentId, String stepId, int targetObjectId, int npcId, long cell)
	{
	}
}
