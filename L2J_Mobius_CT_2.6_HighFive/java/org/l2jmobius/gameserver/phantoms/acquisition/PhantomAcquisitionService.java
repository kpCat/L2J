/*
 * Copyright (c) 2013 L2jMobius
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.l2jmobius.gameserver.phantoms.acquisition;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionCatalog.Method;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionSourcePlanner.RankedSource;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionSourcePlanner.ResourceEvidence;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionSourcePlanner.QuestEvidence;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.Candidate;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.Hashes;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.ManorBinding;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.Phase;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.Receipt;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.ReceiptKind;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.QuestBinding;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.Source;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.Status;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.TerminalResult;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionStore.StoredState;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState;
import org.l2jmobius.gameserver.phantoms.acquisition.manor.PhantomAcquisitionManorAuthority;
import org.l2jmobius.gameserver.phantoms.acquisition.quest.PhantomAcquisitionQuestCatalog;
import org.l2jmobius.gameserver.phantoms.acquisition.quest.PhantomAcquisitionQuestCatalog.Rule;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundService;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.AcquisitionSkillKind;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.AcquisitionActorPosition;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.AcquisitionTargetSnapshot;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.ActionOutcome;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatLoadout.SelectedSkill;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatMode;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatRequest;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatResult;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatService;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatService.ExternalActionKind;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatService.ExternalActionLease;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatService.ExternalActionRequest;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatService.ExternalActionStatus;
import org.l2jmobius.gameserver.phantoms.decision.PhantomCancellationToken;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoal;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateStore;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeQuery;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.SpawnFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.TerritoryGeometry;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationRequest;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationResult;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationService;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationService.SubmissionStatus;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionCatalog;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.CapabilityRule;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyQuery;

/**
 * Bounded acquisition lifecycle. It owns no worker and delegates every kill,
 * skill cast and background mutation to the existing authoritative subsystems.
 */
public final class PhantomAcquisitionService
{
	public static final String CANDIDATE_KEY = "candidate.acquisition.item";
	public static final String PLAN_ACTION = "acquisition.plan";
	public static final String TRAVEL_ACTION = "acquisition.travel";
	public static final String ACTIVE_ACTION = "acquisition.active.advance";
	public static final String BACKGROUND_ACTION = "acquisition.background.advance";
	public static final String VERIFY_ACTION = "acquisition.verify";
	public static final String SWITCH_ACTION = "acquisition.switch";
	private static final int MAXIMUM_TARGET_DISTANCE = 2000;
	private static final int TARGET_QUERY_LIMIT = 8;
	private static final long EXTERNAL_DEADLINE_NANOS = 5_000_000_000L;
	private static final long COMBAT_TIMEOUT_MILLIS = 30_000;
	private static final long NAVIGATION_DEADLINE_NANOS = 30_000_000_000L;
	private static final int ARRIVAL_RADIUS = 250;

	private final PhantomAcquisitionCatalog _catalog;
	private final PhantomAcquisitionStore _store;
	private final PhantomGoalStateStore _goals;
	private final PhantomAcquisitionSourcePlanner _planner;
	private final PhantomGameKnowledgeQuery _knowledge;
	private final PhantomTopologyQuery _topology;
	private final PhantomProgressionCatalog _progression;
	private final PhantomCombatService _combat;
	private final PhantomBackgroundService _background;
	private final PhantomNavigationService _navigation;
	private final PhantomAcquisitionManorAuthority _manor;
	private final PhantomAcquisitionQuestCatalog _quests;
	private final LongSupplier _epochMillis;
	private final ConcurrentHashMap<Long, Boolean> _claims = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<Long, ExternalActionLease> _external = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<Long, TravelOperation> _travels = new ConcurrentHashMap<>();
	private final AtomicLong _planned = new AtomicLong();
	private final AtomicLong _active = new AtomicLong();
	private final AtomicLong _completed = new AtomicLong();
	private final AtomicLong _blocked = new AtomicLong();
	private final AtomicLong _switches = new AtomicLong();
	private final AtomicLong _claimsAcquired = new AtomicLong();
	private final AtomicLong _uncertainRecoveries = new AtomicLong();
	private final AtomicLong _recipeNodes = new AtomicLong();
	private volatile ServiceState _state = ServiceState.NEW;

	public PhantomAcquisitionService(PhantomAcquisitionCatalog catalog, PhantomAcquisitionStore store, PhantomGoalStateStore goals, PhantomAcquisitionSourcePlanner planner, PhantomGameKnowledgeQuery knowledge, PhantomTopologyQuery topology, PhantomProgressionCatalog progression, PhantomCombatService combat, PhantomBackgroundService background, PhantomNavigationService navigation)
	{
		this(catalog, store, goals, planner, knowledge, topology, progression, combat, background, navigation, null, null, System::currentTimeMillis);
	}

	public PhantomAcquisitionService(PhantomAcquisitionCatalog catalog, PhantomAcquisitionStore store, PhantomGoalStateStore goals, PhantomAcquisitionSourcePlanner planner, PhantomGameKnowledgeQuery knowledge, PhantomTopologyQuery topology, PhantomProgressionCatalog progression, PhantomCombatService combat, PhantomBackgroundService background, PhantomNavigationService navigation, PhantomAcquisitionManorAuthority manor, PhantomAcquisitionQuestCatalog quests)
	{
		this(catalog, store, goals, planner, knowledge, topology, progression, combat, background, navigation, manor, quests, System::currentTimeMillis);
	}

	public PhantomAcquisitionService(PhantomAcquisitionCatalog catalog, PhantomAcquisitionStore store, PhantomGoalStateStore goals, PhantomAcquisitionSourcePlanner planner, PhantomGameKnowledgeQuery knowledge, PhantomTopologyQuery topology, PhantomProgressionCatalog progression, PhantomCombatService combat, PhantomBackgroundService background, PhantomNavigationService navigation, PhantomAcquisitionManorAuthority manor, PhantomAcquisitionQuestCatalog quests, LongSupplier epochMillis)
	{
		_catalog = Objects.requireNonNull(catalog, "catalog");
		_store = Objects.requireNonNull(store, "store");
		_goals = Objects.requireNonNull(goals, "goals");
		_planner = Objects.requireNonNull(planner, "planner");
		_knowledge = Objects.requireNonNull(knowledge, "knowledge");
		_topology = Objects.requireNonNull(topology, "topology");
		_progression = Objects.requireNonNull(progression, "progression");
		_combat = Objects.requireNonNull(combat, "combat");
		_background = Objects.requireNonNull(background, "background");
		_navigation = Objects.requireNonNull(navigation, "navigation");
		_manor = manor;
		_quests = quests;
		_epochMillis = Objects.requireNonNull(epochMillis, "epochMillis");
	}

	public synchronized boolean start()
	{
		if (_state == ServiceState.RUNNING)
		{
			return true;
		}
		if (_state != ServiceState.NEW)
		{
			return false;
		}
		_state = ServiceState.RUNNING;
		return true;
	}

	public synchronized boolean beginStop()
	{
		if (_state == ServiceState.STOPPED)
		{
			return false;
		}
		_state = ServiceState.STOPPING;
		_travels.forEach((profileId, travel) -> cancelTravel(profileId));
		_external.forEach((profileId, lease) -> releaseExternal(profileId));
		return true;
	}

	public synchronized boolean finishStop()
	{
		if (_state == ServiceState.STOPPED)
		{
			return true;
		}
		if ((_state != ServiceState.STOPPING) || !_claims.isEmpty() || !_external.isEmpty() || !_travels.isEmpty())
		{
			return false;
		}
		_state = ServiceState.STOPPED;
		return true;
	}

	public Directive directive(long profileId, PhantomGoal contextGoal, PhantomActivityState activityState)
	{
		if ((_state != ServiceState.RUNNING) || (activityState == null))
		{
			return new Directive(DirectiveKind.BLOCKED, "acquisition.service.unavailable", "");
		}
		final PhantomGoal goal = currentGoal(profileId, contextGoal, false);
		if (goal == null)
		{
			return new Directive(DirectiveKind.BLOCKED, "acquisition.goal.stale", "");
		}
		if (goal.status() == PhantomGoalStatus.COMPLETED)
		{
			return new Directive(DirectiveKind.COMPLETE, "acquisition.complete", "");
		}
		try
		{
			PhantomAcquisitionGoalSpec.parse(goal);
		}
		catch (IllegalArgumentException exception)
		{
			return new Directive(DirectiveKind.FAIL, "goal.invalid", "");
		}
		final Optional<StoredState> stored = _store.load(profileId);
		if (stored.isEmpty() || (stored.get().state().goalId() != goal.goalId()) || (stored.get().state().goalRevision() != goal.revision()))
		{
			return new Directive(DirectiveKind.PLAN, "acquisition.plan.required", "");
		}
		final PhantomAcquisitionState state = stored.get().state();
		final long generation = stored.get().rowVersion();
		final String sourceId = state.selectedSource() == null ? "" : state.selectedSource().sourceId();
		if (!state.hashes().equals(hashes()))
		{
			return new Directive(DirectiveKind.SWITCH, "source.authority_stale", sourceId, generation);
		}
		if (state.status() == Status.COMPLETED)
		{
			return new Directive(DirectiveKind.COMPLETE, "acquisition.complete", sourceId, generation);
		}
		if ((state.status() == Status.DEFERRED_CHECKPOINT_2) || (state.status() == Status.PLANNING_ONLY) || (state.status() == Status.FAILED) || (state.status() == Status.INCONSISTENT))
		{
			return new Directive(DirectiveKind.BLOCKED, state.status().name().toLowerCase(), sourceId, generation);
		}
		if (state.status() == Status.BLOCKED)
		{
			return new Directive(state.selectedSource() == null ? DirectiveKind.BLOCKED : DirectiveKind.SWITCH, "source.repeated_failure", sourceId, generation);
		}
		return switch (state.phase())
		{
			case TRAVEL_REQUIRED -> new Directive(DirectiveKind.TRAVEL, "acquisition.travel.required", sourceId, generation);
			case VERIFYING -> new Directive(DirectiveKind.VERIFY, "acquisition.verify.required", sourceId, generation);
			default -> new Directive(activityState == PhantomActivityState.BACKGROUND ? DirectiveKind.BACKGROUND : DirectiveKind.ACTIVE, "acquisition.advance.required", sourceId, generation);
		};
	}

	public OperationResult plan(long profileId, PhantomGoal contextGoal, PhantomActivityState activityState, long logicalNowNanos, long logicalMinute, PhantomCancellationToken token)
	{
		final Claim claim = claim(profileId);
		if (claim == null)
		{
			return retry("acquisition.claim.busy");
		}
		try (claim)
		{
			final PhantomGoalStateStore.StoredGoal storedGoal = exactActiveGoal(profileId, contextGoal);
			if (storedGoal == null)
			{
				return OperationResult.replan("goal.invalid");
			}
			final PhantomGoal goal = storedGoal.goal();
			final PhantomAcquisitionGoalSpec spec = PhantomAcquisitionGoalSpec.parse(goal);
			final Optional<StoredState> existing = _store.load(profileId);
			final Observation observation = observe(profileId, goal, spec, activityState, logicalNowNanos, token);
			if (observation == null)
			{
				return retry("acquisition.observation.unavailable");
			}
			if (existing.isEmpty() && !spec.initialCountMatches(observation.itemCount(), goal))
			{
				return OperationResult.fail("acquisition.baseline.mismatch");
			}
			final PhantomAcquisitionState previous = existing.map(StoredState::state).orElse(null);
			if ((previous != null) && ((previous.goalId() != goal.goalId()) || (previous.goalRevision() != goal.revision()) || (previous.targetItemId() != spec.itemId()) || (previous.requiredAmount() != spec.requiredAmount()) || (previous.baselineCount() != spec.baselineCount())))
			{
				return OperationResult.fail("acquisition.state.identity");
			}
			final long progress = PhantomAcquisitionState.observedProgress(spec.baselineCount(), observation.itemCount(), spec.requiredAmount());
			if (progress == spec.requiredAmount())
			{
				return completeObserved(profileId, storedGoal, existing.orElse(null), observation.itemCount(), logicalMinute);
			}
			final java.util.EnumSet<Method> effectiveMethods = java.util.EnumSet.copyOf(planningMethods(spec, progress));
			String recipeProbeReason = "";
			Observation planningObservation = observation;
			if (effectiveMethods.contains(Method.RECIPE_PREPARATION))
			{
				final var probe = _planner.probeRecipeInventory(spec.itemId(), spec.requiredAmount() - progress);
				if (!probe.successful())
				{
					effectiveMethods.remove(Method.RECIPE_PREPARATION);
					recipeProbeReason = probe.reasonKey();
				}
				else if (!probe.exactItemIds().isEmpty())
				{
					final Map<Integer, Long> inventory = recipeInventory(profileId, storedGoal, previous, spec, observation, activityState, probe.exactItemIds(), logicalNowNanos, token);
					if (inventory == null)
					{
						return retry("acquisition.recipe.inventory_unavailable");
					}
					planningObservation = observation.withInventory(inventory);
				}
			}
			if (effectiveMethods.contains(Method.MANOR_CROP))
			{
				final List<Integer> itemIds = _planner.probeManorInventory(spec.itemId());
				if (itemIds.isEmpty() || (itemIds.size() > _catalog.limits().questItemIdsPerRead()))
				{
					effectiveMethods.remove(Method.MANOR_CROP);
				}
				else
				{
					final Map<Integer, Long> inventory = recipeInventory(profileId, storedGoal, previous, spec, planningObservation, activityState, itemIds, logicalNowNanos, token);
					if (inventory == null)
					{
						return retry("acquisition.manor.inventory_unavailable");
					}
					planningObservation = planningObservation.withInventory(inventory);
				}
			}
			final Map<String, QuestEvidence> questEvidence = effectiveMethods.contains(Method.QUEST_COLLECTION) ? questEvidence(profileId, storedGoal, previous, spec, planningObservation, activityState, logicalNowNanos, token) : Map.of();
			final Map<String, Candidate> previousCandidates = candidates(previous);
			final PhantomAcquisitionSourcePlanner.Result planned = effectiveMethods.isEmpty() ? PhantomAcquisitionSourcePlanner.Result.blocked(recipeProbeReason.isEmpty() ? "source.ineligible" : recipeProbeReason) : _planner.plan(new PhantomAcquisitionSourcePlanner.Request(profileId, spec.itemId(), spec.requiredAmount() - progress, activityState, planningObservation.classId(), planningObservation.level(), planningObservation.inventory(), planningObservation.knownSkills(), java.util.Set.copyOf(effectiveMethods), spec.preferredMethod(), planningObservation.anchorId(), previous == null || previous.selectedSource() == null ? "" : previous.selectedSource().sourceId(), planningObservation.resources(), previousCandidates, logicalMinute, questEvidence));
			final List<Candidate> candidates = planned.ranked().stream().map(ranked -> merge(ranked, previousCandidates.get(ranked.source().sourceId()))).toList();
			final RankedSource selected = planned.selected();
			final Source source = selected == null ? null : selected.source();
			final Status status = selected == null ? (planned.deferred() ? Status.DEFERRED_CHECKPOINT_2 : Status.BLOCKED) : (source.method() == Method.RECIPE_PREPARATION ? Status.PLANNING_ONLY : Status.READY);
			final Phase phase = (source == null) || (source.method() == Method.RECIPE_PREPARATION) ? Phase.NONE : (!source.anchorId().equals(observation.anchorId()) ? Phase.TRAVEL_REQUIRED : Phase.TARGET_REQUIRED);
			final PhantomAcquisitionState next = new PhantomAcquisitionState(hashes(), goal.goalId(), goal.revision(), spec.itemId(), spec.requiredAmount(), spec.baselineCount(), observation.itemCount(), progress, status, source, candidates, source == null ? 0 : indexOf(candidates, source.sourceId()), previous == null ? 0 : previous.switchCount(), phase, 0, 0, 0, selected == null ? null : selected.recipePlan(), selected == null ? null : selected.methodBinding(), previous == null ? List.of() : previous.receipts(), 0, logicalMinute);
			final PhantomGoal projected = PhantomAcquisitionGoalSpec.project(goal, progress, PhantomGoalStatus.ACTIVE, source);
			if (existing.isEmpty())
			{
				_store.insertWithGoal(profileId, next, storedGoal.rowVersion(), projected);
			}
			else
			{
				_store.mutateWithGoal(profileId, existing.get().rowVersion(), next, storedGoal.rowVersion(), projected);
			}
			_planned.incrementAndGet();
			if (status == Status.BLOCKED)
			{
				_blocked.incrementAndGet();
			}
			if (next.recipePlan() != null)
			{
				_recipeNodes.addAndGet(next.recipePlan().nodes().size());
			}
			return OperationResult.success(planned.reasonKey());
		}
		catch (RuntimeException exception)
		{
			return OperationResult.replan("acquisition.plan.conflict");
		}
	}

	public OperationResult travel(long profileId, PhantomGoal contextGoal, PhantomActivityState activityState, long activityGeneration, long tickSequence, long logicalNowNanos, long logicalMinute, PhantomCancellationToken token)
	{
		final Claim claim = claim(profileId);
		if (claim == null)
		{
			return retry("acquisition.claim.busy");
		}
		try (claim)
		{
			final Current current = current(profileId, contextGoal);
			if ((current == null) || (current.state().phase() != Phase.TRAVEL_REQUIRED) || stale(current))
			{
				return OperationResult.replan("acquisition.travel.stale");
			}
			if (activityState != PhantomActivityState.BACKGROUND)
			{
				return travelMaterialized(current, logicalNowNanos, logicalMinute, token);
			}
			final PhantomBackgroundService.OperationResult result = _background.travelAcquisition(profileId, current.goal().goal(), current.goal().rowVersion(), current.state(), current.acquisition().rowVersion(), activityGeneration, tickSequence, activityState, logicalNowNanos);
			if (result.status() == PhantomBackgroundService.OperationStatus.SUCCESS || result.status() == PhantomBackgroundService.OperationStatus.IDEMPOTENT)
			{
				final Optional<PhantomBackgroundState> background = _background.acquisitionSnapshot(profileId);
				if (background.filter(value -> value.position().committedAnchorId().equals(current.state().selectedSource().anchorId())).isPresent())
				{
					_store.replace(profileId, current.acquisition().rowVersion(), current.state().withPhase(Phase.TARGET_REQUIRED, 0, 0, 0, logicalMinute));
				}
				return OperationResult.success("acquisition.travel.progress");
			}
			return map(result);
		}
	}

	public OperationResult backgroundAdvance(long profileId, PhantomGoal contextGoal, PhantomActivityState activityState, long activityGeneration, long tickSequence, long logicalNowNanos, long logicalMinute, PhantomCancellationToken token)
	{
		final Claim claim = claim(profileId);
		if (claim == null)
		{
			return retry("acquisition.claim.busy");
		}
		try (claim)
		{
			final Current current = current(profileId, contextGoal);
			if ((current == null) || stale(current) || (activityState != PhantomActivityState.BACKGROUND))
			{
				return OperationResult.replan("acquisition.background.stale");
			}
			final PhantomBackgroundService.OperationResult result = _background.acquireItem(profileId, current.goal().goal(), current.goal().rowVersion(), current.state(), current.acquisition().rowVersion(), activityGeneration, tickSequence, activityState, logicalNowNanos, logicalMinute);
			final OperationResult mapped = map(result);
			if (mapped.status() == OperationStatus.SUCCESS)
			{
				final PhantomGoal refreshed = _goals.load(profileId).map(PhantomGoalStateStore.StoredGoal::goal).orElse(null);
				if ((refreshed != null) && (refreshed.status() == PhantomGoalStatus.COMPLETED))
				{
					_completed.incrementAndGet();
					return OperationResult.complete("acquisition.background.complete");
				}
			}
			return mapped;
		}
	}

	public OperationResult activeAdvance(long profileId, PhantomGoal contextGoal, PhantomActivityState activityState, long activityGeneration, long tickSequence, long logicalNowNanos, long logicalMinute, PhantomCancellationToken token)
	{
		final Claim claim = claim(profileId);
		if (claim == null)
		{
			return retry("acquisition.claim.busy");
		}
		try (claim)
		{
			if ((activityState != PhantomActivityState.ACTIVE) && (activityState != PhantomActivityState.WARM))
			{
				return OperationResult.replan("acquisition.active.activity");
			}
			final Current current = current(profileId, contextGoal);
			if ((current == null) || stale(current) || (current.state().selectedSource() == null))
			{
				return OperationResult.replan("acquisition.active.stale");
			}
			_active.incrementAndGet();
			return switch (current.state().phase())
			{
				case TARGET_REQUIRED -> target(current, logicalNowNanos, logicalMinute, token);
				case SPOIL_PREPARED -> dispatch(current, AcquisitionSkillKind.SPOIL, Phase.SPOIL_DISPATCHING, logicalNowNanos, logicalMinute, token);
				case SPOIL_DISPATCHING -> observeSpoil(current, logicalNowNanos, logicalMinute, token);
				case SPOIL_OBSERVED -> prepareCombat(current, logicalMinute);
				case SOW_PREPARED -> dispatchManor(current, true, logicalNowNanos, logicalMinute, token);
				case SOW_DISPATCHING -> observeSow(current, logicalNowNanos, logicalMinute, token);
				case SOW_OBSERVED -> prepareCombat(current, logicalMinute);
				case COMBAT_PREPARED -> submitCombat(current, current.state().selectedSource().method() == Method.DEATH_DROP, Phase.COMBAT_SUBMITTED, logicalNowNanos, logicalMinute, token);
				case COMBAT_SUBMITTED -> observeCombat(current, logicalNowNanos, logicalMinute, token);
				case COMBAT_TERMINAL -> prepareSweepOrVerify(current, logicalNowNanos, logicalMinute, token);
				case HARVEST_PREPARED -> dispatchManor(current, false, logicalNowNanos, logicalMinute, token);
				case HARVEST_DISPATCHING -> observeHarvest(current, logicalNowNanos, logicalMinute, token);
				case QUEST_COMBAT_PREPARED -> submitCombat(current, false, Phase.QUEST_COMBAT_SUBMITTED, logicalNowNanos, logicalMinute, token);
				case QUEST_COMBAT_SUBMITTED -> observeCombat(current, logicalNowNanos, logicalMinute, token);
				case QUEST_COMBAT_TERMINAL -> prepareQuestCallback(current, logicalNowNanos, logicalMinute);
				case QUEST_CALLBACK_WAIT -> observeQuestCallback(current, logicalNowNanos, logicalMinute, token);
				case SWEEP_PREPARED -> dispatch(current, AcquisitionSkillKind.SWEEP, Phase.SWEEP_DISPATCHING, logicalNowNanos, logicalMinute, token);
				case SWEEP_DISPATCHING -> observeSweep(current, logicalNowNanos, logicalMinute, token);
				case VERIFYING -> verifyCurrent(current, logicalNowNanos, logicalMinute, token);
				default -> OperationResult.replan("acquisition.active.phase");
			};
		}
	}

	public OperationResult verify(long profileId, PhantomGoal contextGoal, PhantomActivityState activityState, long logicalNowNanos, long logicalMinute, PhantomCancellationToken token)
	{
		return activeAdvance(profileId, contextGoal, activityState, 0, 0, logicalNowNanos, logicalMinute, token);
	}

	public OperationResult switchSource(long profileId, PhantomGoal contextGoal, PhantomActivityState activityState, long logicalNowNanos, long logicalMinute, PhantomCancellationToken token)
	{
		final Claim claim = claim(profileId);
		if (claim == null)
		{
			return retry("acquisition.claim.busy");
		}
		try (claim)
		{
			if (_combat.hasClaim(profileId) || _external.containsKey(profileId) || _travels.containsKey(profileId))
			{
				return retry("acquisition.switch.claimed");
			}
			final Current current = current(profileId, contextGoal);
			if ((current == null) || (current.state().selectedSource() == null))
			{
				return OperationResult.replan("acquisition.switch.stale");
			}
			final PhantomAcquisitionGoalSpec spec = PhantomAcquisitionGoalSpec.parse(current.goal().goal());
			final boolean authorityStale = stale(current);
			if (!authorityStale && (current.state().switchCount() >= Math.min(spec.maximumSwitches(), _catalog.limits().sourceSwitches())))
			{
				persistTerminal(current, Status.FAILED, logicalMinute);
				return OperationResult.fail("source.exhausted");
			}
			Observation observation = observe(profileId, current.goal().goal(), spec, activityState, logicalNowNanos, token);
			if (observation == null)
			{
				return retry("acquisition.observation.unavailable");
			}
			final java.util.Set<Method> methods = planningMethods(spec, current.state().progress());
			if (methods.contains(Method.MANOR_CROP))
			{
				final List<Integer> itemIds = _planner.probeManorInventory(spec.itemId());
				if (!itemIds.isEmpty() && (itemIds.size() <= _catalog.limits().questItemIdsPerRead()))
				{
					final Map<Integer, Long> inventory = recipeInventory(profileId, current.goal(), current.state(), spec, observation, activityState, itemIds, logicalNowNanos, token);
					if (inventory != null)
					{
						observation = observation.withInventory(inventory);
					}
				}
			}
			final Map<String, QuestEvidence> evidence = methods.contains(Method.QUEST_COLLECTION) ? questEvidence(profileId, current.goal(), current.state(), spec, observation, activityState, logicalNowNanos, token) : Map.of();
			final Map<String, Candidate> previous = authorityStale ? Map.of() : candidates(current.state());
			final PhantomAcquisitionSourcePlanner.Result replanned = _planner.plan(new PhantomAcquisitionSourcePlanner.Request(profileId, spec.itemId(), spec.requiredAmount() - current.state().progress(), activityState, observation.classId(), observation.level(), observation.inventory(), observation.knownSkills(), methods, spec.preferredMethod(), observation.anchorId(), current.state().selectedSource().sourceId(), observation.resources(), previous, logicalMinute, evidence));
			final List<Candidate> ranked = replanned.ranked().stream().map(value -> merge(value, previous.get(value.source().sourceId()))).toList();
			final RankedSource next = authorityStale ? replanned.selected() : replanned.ranked().stream().filter(value -> !value.source().sourceId().equals(current.state().selectedSource().sourceId())).findFirst().orElse(null);
			if (next == null)
			{
				final Status terminalStatus = replanned.deferred() ? Status.DEFERRED_CHECKPOINT_2 : Status.FAILED;
				final PhantomAcquisitionState exhausted = new PhantomAcquisitionState(hashes(), current.state().goalId(), current.state().goalRevision(), current.state().targetItemId(), current.state().requiredAmount(), current.state().baselineCount(), current.state().lastObservedCount(), current.state().progress(), terminalStatus, null, ranked, 0, current.state().switchCount(), Phase.NONE, 0, 0, 0, null, null, current.state().receipts(), 0, logicalMinute);
				final PhantomGoal projected = PhantomAcquisitionGoalSpec.project(current.goal().goal(), exhausted.progress(), PhantomGoalStatus.ACTIVE, null);
				_store.mutateWithGoal(profileId, current.acquisition().rowVersion(), exhausted, current.goal().rowVersion(), projected);
				return OperationResult.fail("source.exhausted");
			}
			final boolean changedSource = !next.source().sourceId().equals(current.state().selectedSource().sourceId());
			if (changedSource && (current.state().switchCount() >= Math.min(spec.maximumSwitches(), _catalog.limits().sourceSwitches())))
			{
				persistTerminal(current, Status.FAILED, logicalMinute);
				return OperationResult.fail("source.exhausted");
			}
			final int cursor = indexOf(ranked, next.source().sourceId());
			final Status status = next.source().method() == Method.RECIPE_PREPARATION ? Status.PLANNING_ONLY : Status.READY;
			final Phase phase = next.source().method() == Method.RECIPE_PREPARATION ? Phase.NONE : (!next.source().anchorId().equals(observation.anchorId()) ? Phase.TRAVEL_REQUIRED : Phase.TARGET_REQUIRED);
			final PhantomAcquisitionState switched = new PhantomAcquisitionState(hashes(), current.state().goalId(), current.state().goalRevision(), current.state().targetItemId(), current.state().requiredAmount(), current.state().baselineCount(), observation.itemCount(), PhantomAcquisitionState.observedProgress(current.state().baselineCount(), observation.itemCount(), current.state().requiredAmount()), status, next.source(), ranked, cursor, current.state().switchCount() + (changedSource ? 1 : 0), phase, 0, 0, 0, next.source().method() == Method.RECIPE_PREPARATION ? next.recipePlan() : null, next.methodBinding(), current.state().receipts(), 0, logicalMinute);
			final PhantomGoal projected = PhantomAcquisitionGoalSpec.project(current.goal().goal(), switched.progress(), PhantomGoalStatus.ACTIVE, switched.selectedSource());
			_store.mutateWithGoal(profileId, current.acquisition().rowVersion(), switched, current.goal().rowVersion(), projected);
			if (changedSource)
			{
				_switches.incrementAndGet();
			}
			return OperationResult.success(changedSource ? "acquisition.switch.complete" : "acquisition.authority.refreshed");
		}
		catch (RuntimeException exception)
		{
			return OperationResult.replan("acquisition.switch.conflict");
		}
	}

	private OperationResult target(Current current, long logicalNowNanos, long logicalMinute, PhantomCancellationToken token)
	{
		final ExternalActionLease lease = external(current, logicalNowNanos, token);
		if (lease == null)
		{
			return retry("acquisition.actor.unavailable");
		}
		final Source source = current.state().selectedSource();
		final AcquisitionTargetSnapshot target = lease.acquisitionTargets(source.npcId(), TARGET_QUERY_LIMIT, MAXIMUM_TARGET_DISTANCE).stream().filter(candidate -> sourceOwnsTarget(source, candidate)).findFirst().orElse(null);
		if (target == null)
		{
			releaseExternal(current.profileId());
			return failSource(current, "source.target_unavailable", logicalMinute);
		}
		Phase next = source.method() == Method.SPOIL_SWEEP ? Phase.SPOIL_PREPARED : Phase.COMBAT_PREPARED;
		PhantomAcquisitionState prepared;
		if (source.method() == Method.MANOR_CROP)
		{
			if (!(current.state().methodBinding() instanceof ManorBinding manor) || (target.instanceId() != 0) || (target.seeded()) || !target.manorLiveValidFor(lease.actorSnapshot(), source.npcId(), MAXIMUM_TARGET_DISTANCE))
			{
				releaseExternal(current.profileId());
				return failSource(current, "source.target_unavailable", logicalMinute);
			}
			final var inventory = lease.manorInventory(manor.seedItemId(), manor.cropItemId(), PhantomAcquisitionManorAuthority.HARVESTER_ITEM_ID);
			if ((inventory == null) || (inventory.seedObjectId() <= 0) || (inventory.harvesterObjectId() <= 0) || (inventory.seedCount() <= 0) || (inventory.harvesterCount() <= 0))
			{
				releaseExternal(current.profileId());
				return failSource(current, (inventory == null) || (inventory.harvesterObjectId() <= 0) ? "manor.harvester_missing" : "manor.seed_missing", logicalMinute);
			}
			final ManorBinding exact = new ManorBinding(manor.castleId(), manor.seedItemId(), manor.cropItemId(), manor.matureItemId(), manor.reward1ItemId(), manor.reward2ItemId(), manor.seedLevel(), manor.alternative(), manor.rawSeedLimit(), manor.rawCropLimit(), inventory.seedObjectId(), inventory.harvesterObjectId(), inventory.seedCount(), inventory.cropCount(), manor.authorityHash());
			next = Phase.SOW_PREPARED;
			prepared = current.state().withBinding(exact, next, target.objectId(), target.npcId(), target.instanceId(), 0, logicalMinute);
		}
		else if (source.method() == Method.QUEST_COLLECTION)
		{
			if (!(current.state().methodBinding() instanceof QuestBinding quest) || (target.instanceId() != 0) || !target.liveValidFor(lease.actorSnapshot(), source.npcId(), MAXIMUM_TARGET_DISTANCE) || (target.onKillDelayMillis() > _catalog.limits().questCallbackWaitMillis()) || !exactQuestState(lease, quest))
			{
				releaseExternal(current.profileId());
				return failSource(current, "quest.cond_ineligible", logicalMinute);
			}
			final long count = lease.acquisitionInventoryCount(quest.questItemId());
			if ((count < 0) || (count >= quest.itemCap()))
			{
				releaseExternal(current.profileId());
				return failSource(current, count >= quest.itemCap() ? "quest.item_cap" : "quest.cond_ineligible", logicalMinute);
			}
			final QuestBinding exact = questBinding(quest, count, 0);
			next = Phase.QUEST_COMBAT_PREPARED;
			prepared = current.state().withBinding(exact, next, target.objectId(), target.npcId(), target.instanceId(), 0, logicalMinute);
		}
		else
		{
			prepared = current.state().withPhase(next, target.objectId(), target.npcId(), target.instanceId(), logicalMinute);
		}
		_store.replace(current.profileId(), current.acquisition().rowVersion(), prepared);
		if ((source.method() == Method.SPOIL_SWEEP) || (source.method() == Method.MANOR_CROP))
		{
			return OperationResult.success(source.method() == Method.SPOIL_SWEEP ? "acquisition.spoil.prepared" : "acquisition.manor.sow_prepared");
		}
		releaseExternal(current.profileId());
		return OperationResult.success("acquisition.combat.prepared");
	}

	private OperationResult travelMaterialized(Current current, long logicalNowNanos, long logicalMinute, PhantomCancellationToken token)
	{
		if ((current == null) || ((current.state().phase() != Phase.TRAVEL_REQUIRED)) || ((current.state().selectedSource() == null)))
		{
			return OperationResult.replan("acquisition.travel.stale");
		}
		final var anchor = _topology.findAnchor(current.state().selectedSource().anchorId()).orElse(null);
		if ((anchor == null) || (anchor.point().instanceId() != current.state().selectedSource().instanceId()))
		{
			return failSource(current, "source.authority_stale", logicalMinute);
		}
		final PhantomNavigationPoint destination = new PhantomNavigationPoint(anchor.point().x(), anchor.point().y(), anchor.point().z(), anchor.point().instanceId());
		final ExternalActionLease lease = external(current, logicalNowNanos, token);
		if (lease == null)
		{
			return retry("acquisition.actor.unavailable");
		}
		final AcquisitionActorPosition actor = lease.acquisitionPosition();
		if ((actor == null) || (actor.instanceId() != destination.instanceId()))
		{
			releaseExternal(current.profileId());
			return failSource(current, "source.target_unavailable", logicalMinute);
		}
		final PhantomNavigationPoint origin = new PhantomNavigationPoint(actor.x(), actor.y(), actor.z(), actor.instanceId());
		if (origin.distanceTo(destination) <= ARRIVAL_RADIUS)
		{
			cancelTravel(current.profileId());
			releaseExternal(current.profileId());
			_store.replace(current.profileId(), current.acquisition().rowVersion(), current.state().withPhase(Phase.TARGET_REQUIRED, 0, 0, 0, logicalMinute));
			return OperationResult.success("acquisition.travel.arrived");
		}
		TravelOperation travel = _travels.get(current.profileId());
		if ((travel != null) && (!travel.sourceId().equals(current.state().selectedSource().sourceId()) || (travel.goalRevision() != current.state().goalRevision())))
		{
			cancelTravel(current.profileId());
			travel = null;
		}
		if (travel == null)
		{
			releaseExternal(current.profileId());
			final long deadline = logicalNowNanos > (Long.MAX_VALUE - NAVIGATION_DEADLINE_NANOS) ? Long.MAX_VALUE : logicalNowNanos + NAVIGATION_DEADLINE_NANOS;
			final var submission = _navigation.submit(new PhantomNavigationRequest(current.profileId(), origin, destination, Math.max(0, logicalNowNanos), Math.max(logicalNowNanos + 1, deadline), 100_000));
			if (submission.status() == SubmissionStatus.REJECTED)
			{
				return retry("acquisition.navigation.busy");
			}
			final List<PhantomNavigationPoint> immediate = (submission.status() == SubmissionStatus.COMPLETED) && (submission.immediateResult() != null) && (submission.immediateResult().route() != null) ? submission.immediateResult().route().waypoints() : List.of();
			travel = new TravelOperation(current.state().selectedSource().sourceId(), current.state().goalRevision(), submission.requestId(), immediate, 0);
			_travels.put(current.profileId(), travel);
			return retry("acquisition.navigation.requested");
		}
		if (travel.waypoints().isEmpty())
		{
			releaseExternal(current.profileId());
			final Optional<PhantomNavigationResult> result = _navigation.consume(travel.requestId());
			if (result.isEmpty())
			{
				return retry("acquisition.navigation.pending");
			}
			if (result.get().route() == null)
			{
				cancelTravel(current.profileId());
				return failSource(current, "source.target_unavailable", logicalMinute);
			}
			travel = travel.withWaypoints(result.get().route().waypoints());
			_travels.put(current.profileId(), travel);
			return OperationResult.success("acquisition.navigation.ready");
		}
		final PhantomNavigationPoint waypoint = travel.waypoints().get(travel.waypoint());
		if (origin.distanceTo(waypoint) <= ARRIVAL_RADIUS)
		{
			releaseExternal(current.profileId());
			final int nextWaypoint = travel.waypoint() + 1;
			if (nextWaypoint >= travel.waypoints().size())
			{
				cancelTravel(current.profileId());
				_store.replace(current.profileId(), current.acquisition().rowVersion(), current.state().withPhase(Phase.TARGET_REQUIRED, 0, 0, 0, logicalMinute));
				return OperationResult.success("acquisition.travel.arrived");
			}
			_travels.put(current.profileId(), travel.withWaypoint(nextWaypoint));
			return OperationResult.success("acquisition.travel.waypoint");
		}
		final ActionOutcome outcome = lease.moveTo(waypoint.x(), waypoint.y(), waypoint.z(), waypoint.instanceId());
		return switch (outcome)
		{
			case ISSUED, ALREADY_OWNED -> retry("acquisition.travel.moving");
			case UNAVAILABLE -> retry("acquisition.travel.waiting");
			case REJECTED -> failSource(current, "source.target_unavailable", logicalMinute);
		};
	}

	private OperationResult dispatchManor(Current current, boolean sow, long logicalNowNanos, long logicalMinute, PhantomCancellationToken token)
	{
		if (!(current.state().methodBinding() instanceof ManorBinding manor) || (_manor == null) || !_manor.current() || !manor.authorityHash().equals(_manor.authorityHash()))
		{
			return failSource(current, "source.authority_stale", logicalMinute);
		}
		final ExternalActionLease lease = external(current, logicalNowNanos, token);
		if (lease == null)
		{
			return retry("acquisition.actor.unavailable");
		}
		final AcquisitionTargetSnapshot target = lease.acquisitionTargetSnapshot(current.state().targetObjectId());
		final var actor = lease.actorSnapshot();
		final var inventory = lease.manorInventory(manor.seedItemId(), manor.cropItemId(), PhantomAcquisitionManorAuthority.HARVESTER_ITEM_ID);
		final long cropCount = inventory == null ? -1 : inventory.cropCount();
		if (!sow)
		{
			if ((cropCount < current.state().lastObservedCount()) || (cropCount < manor.cropCountBeforeDispatch()))
			{
				releaseExternal(current.profileId());
				return failSource(current, "manor.inventory_inconsistent", logicalMinute);
			}
			if (cropCount > current.state().lastObservedCount())
			{
				releaseExternal(current.profileId());
				return observeManor(current, manor, cropCount, ReceiptKind.VERIFY, Phase.HARVEST_PREPARED, current.state().phaseAttempt(), logicalMinute, "acquisition.manor.external_crop_observed");
			}
			if (cropCount != manor.cropCountBeforeDispatch())
			{
				final ManorBinding refreshed = manorWithCropBaseline(manor, cropCount);
				_store.replace(current.profileId(), current.acquisition().rowVersion(), current.state().withBinding(refreshed, Phase.HARVEST_PREPARED, current.state().targetObjectId(), current.state().targetNpcId(), current.state().targetInstanceId(), current.state().phaseAttempt(), logicalMinute));
				releaseExternal(current.profileId());
				return retry("acquisition.manor.harvest_reprepared");
			}
		}
		final boolean targetValid = (target != null) && (actor != null) && sourceOwnsTarget(current.state().selectedSource(), target) && (sow ? target.manorLiveValidFor(actor, current.state().selectedSource().npcId(), MAXIMUM_TARGET_DISTANCE) : target.harvestValidFor(actor, current.state().selectedSource().npcId(), manor.seedItemId(), MAXIMUM_TARGET_DISTANCE));
		final boolean inventoryValid = (inventory != null) && (inventory.harvesterObjectId() == manor.harvesterObjectId()) && (inventory.harvesterCount() > 0) && (sow ? ((inventory.seedObjectId() == manor.seedObjectId()) && (inventory.seedCount() == manor.seedCountBeforeDispatch()) && (inventory.seedCount() > 0)) : true);
		if (!targetValid || !inventoryValid)
		{
			releaseExternal(current.profileId());
			return sow ? uncertain(current, ReceiptKind.ACTIVE_MANOR_SOW, logicalMinute, cropCount) : failSource(current, "source.target_unavailable", logicalMinute);
		}
		final Phase dispatching = sow ? Phase.SOW_DISPATCHING : Phase.HARVEST_DISPATCHING;
		final StoredState dispatched = _store.replace(current.profileId(), current.acquisition().rowVersion(), current.state().withPhase(dispatching, target.objectId(), target.npcId(), target.instanceId(), current.state().phaseAttempt(), logicalMinute));
		final Current persisted = new Current(current.profileId(), current.goal(), dispatched);
		final ActionOutcome outcome = sow ? lease.useExactSeed(manor.seedObjectId(), manor.seedItemId(), target.objectId()) : lease.useExactHarvester(manor.harvesterObjectId(), PhantomAcquisitionManorAuthority.HARVESTER_ITEM_ID, target.objectId());
		return switch (outcome)
		{
			case ISSUED, ALREADY_OWNED -> OperationResult.success(sow ? "acquisition.manor.sow_dispatched" : "acquisition.manor.harvest_dispatched");
			case UNAVAILABLE -> retry(sow ? "acquisition.manor.sow_pending_observation" : "acquisition.manor.harvest_pending_observation");
			case REJECTED ->
			{
				releaseExternal(current.profileId());
				yield failSource(persisted, "source.ineligible", logicalMinute);
			}
		};
	}

	private OperationResult observeSow(Current current, long logicalNowNanos, long logicalMinute, PhantomCancellationToken token)
	{
		if (!(current.state().methodBinding() instanceof ManorBinding manor))
		{
			return OperationResult.replan("acquisition.manor.binding");
		}
		final ExternalActionLease lease = external(current, logicalNowNanos, token);
		if (lease == null)
		{
			return retryManor(current, true, logicalMinute, -1);
		}
		final AcquisitionTargetSnapshot target = lease.acquisitionTargetSnapshot(current.state().targetObjectId());
		final var inventory = lease.manorInventory(manor.seedItemId(), manor.cropItemId(), PhantomAcquisitionManorAuthority.HARVESTER_ITEM_ID);
		final var actor = lease.actorSnapshot();
		if ((target != null) && (actor != null) && sourceOwnsTarget(current.state().selectedSource(), target) && target.seeded() && (target.seederObjectId() == actor.objectId()) && (target.seedItemId() == manor.seedItemId()) && (target.objectId() == current.state().targetObjectId()) && (target.npcId() == current.state().targetNpcId()) && (target.instanceId() == current.state().targetInstanceId()))
		{
			_store.replace(current.profileId(), current.acquisition().rowVersion(), current.state().withPhase(Phase.SOW_OBSERVED, target.objectId(), target.npcId(), target.instanceId(), logicalMinute));
			releaseExternal(current.profileId());
			return OperationResult.success("acquisition.manor.sow_observed");
		}
		if (exactManorCastActive(lease, current.state(), true))
		{
			return retry("acquisition.manor.sow_casting");
		}
		final long seedCount = inventory == null ? -1 : inventory.seedCount();
		final long cropCount = inventory == null ? -1 : inventory.cropCount();
		releaseExternal(current.profileId());
		if ((seedCount >= 0) && (seedCount < manor.seedCountBeforeDispatch()))
		{
			return uncertain(current, ReceiptKind.ACTIVE_MANOR_SOW, logicalMinute, cropCount);
		}
		return retryManor(current, true, logicalMinute, cropCount);
	}

	private OperationResult observeHarvest(Current current, long logicalNowNanos, long logicalMinute, PhantomCancellationToken token)
	{
		if (!(current.state().methodBinding() instanceof ManorBinding manor))
		{
			return OperationResult.replan("acquisition.manor.binding");
		}
		final ExternalActionLease lease = external(current, logicalNowNanos, token);
		if (lease == null)
		{
			return retryManor(current, false, logicalMinute, -1);
		}
		final var inventory = lease.manorInventory(manor.seedItemId(), manor.cropItemId(), PhantomAcquisitionManorAuthority.HARVESTER_ITEM_ID);
		final long cropCount = inventory == null ? -1 : inventory.cropCount();
		if (cropCount > manor.cropCountBeforeDispatch())
		{
			releaseExternal(current.profileId());
			return observeManor(current, manor, cropCount, ReceiptKind.ACTIVE_MANOR_HARVEST, Phase.TARGET_REQUIRED, 0, logicalMinute, "acquisition.manor.harvest_observed");
		}
		if ((cropCount < manor.cropCountBeforeDispatch()) || (cropCount < current.state().lastObservedCount()))
		{
			releaseExternal(current.profileId());
			return failSource(current, "manor.inventory_inconsistent", logicalMinute);
		}
		if (exactManorCastActive(lease, current.state(), false))
		{
			return retry("acquisition.manor.harvest_casting");
		}
		final AcquisitionTargetSnapshot target = lease.acquisitionTargetSnapshot(current.state().targetObjectId());
		final boolean recoverable = (target != null) && sourceOwnsTarget(current.state().selectedSource(), target) && target.harvestValidFor(lease.actorSnapshot(), current.state().selectedSource().npcId(), manor.seedItemId(), MAXIMUM_TARGET_DISTANCE);
		releaseExternal(current.profileId());
		return recoverable ? retryManor(current, false, logicalMinute, cropCount) : failSource(current, "source.target_unavailable", logicalMinute);
	}

	private OperationResult retryManor(Current current, boolean sow, long logicalMinute, long observedCount)
	{
		final int attempt = current.state().phaseAttempt() + 1;
		final int maximum = sow ? _catalog.limits().manorAttemptsPerTarget() : _catalog.limits().harvestAttemptsPerCorpse();
		if (attempt < maximum)
		{
			final Phase prepared = sow ? Phase.SOW_PREPARED : Phase.HARVEST_PREPARED;
			_store.replace(current.profileId(), current.acquisition().rowVersion(), current.state().withPhase(prepared, current.state().targetObjectId(), current.state().targetNpcId(), current.state().targetInstanceId(), attempt, logicalMinute));
			return retry(sow ? "acquisition.manor.sow_recovery_retry" : "acquisition.manor.harvest_recovery_retry");
		}
		return sow ? uncertain(current, ReceiptKind.ACTIVE_MANOR_SOW, logicalMinute, observedCount) : failSource(current, "manor.harvest_no_progress", logicalMinute);
	}

	private boolean exactManorCastActive(ExternalActionLease lease, PhantomAcquisitionState state, boolean sow)
	{
		if ((_manor == null) || !(state.methodBinding() instanceof ManorBinding manor))
		{
			return false;
		}
		final var identity = sow ? _manor.seedHandler(manor.seedItemId()) : _manor.harvesterHandler();
		final var actor = lease.actorSnapshot();
		return (actor != null) && actor.casting() && "CAST".equals(actor.intention()) && (actor.currentTargetObjectId() == state.targetObjectId()) && (actor.currentSkillId() == identity.skillId()) && (actor.currentSkillLevel() == identity.skillLevel());
	}

	private OperationResult prepareQuestCallback(Current current, long logicalNowNanos, long logicalMinute)
	{
		if (!(current.state().methodBinding() instanceof QuestBinding quest))
		{
			return OperationResult.replan("acquisition.quest.binding");
		}
		final long nowMillis = Math.max(0, _epochMillis.getAsLong());
		final long wait = _catalog.limits().questCallbackWaitMillis();
		final long deadline = saturatingAdd(nowMillis, wait);
		final QuestBinding waiting = questBinding(quest, quest.itemCountBeforeKill(), deadline);
		_store.replace(current.profileId(), current.acquisition().rowVersion(), current.state().withBinding(waiting, Phase.QUEST_CALLBACK_WAIT, current.state().targetObjectId(), current.state().targetNpcId(), current.state().targetInstanceId(), 0, logicalMinute));
		return OperationResult.success("acquisition.quest.callback_wait");
	}

	private OperationResult observeQuestCallback(Current current, long logicalNowNanos, long logicalMinute, PhantomCancellationToken token)
	{
		if (!(current.state().methodBinding() instanceof QuestBinding quest) || (_quests == null))
		{
			return OperationResult.replan("acquisition.quest.binding");
		}
		final ExternalActionLease lease = external(current, logicalNowNanos, token);
		if (lease == null)
		{
			return retry("acquisition.actor.unavailable");
		}
		final Rule rule = exactQuestRule(quest);
		if ((rule == null) || !exactQuestState(lease, quest, rule))
		{
			releaseExternal(current.profileId());
			return failSource(current, "quest.cond_ineligible", logicalMinute);
		}
		final long count = lease.acquisitionInventoryCount(quest.questItemId());
		releaseExternal(current.profileId());
		if (count < quest.itemCountBeforeKill())
		{
			return failSource(current, "quest.item_count_decreased", logicalMinute);
		}
		if (count > quest.itemCap())
		{
			return failSource(current, "quest.item_cap", logicalMinute);
		}
		if (count > quest.itemCountBeforeKill())
		{
			return observeQuestCollection(current, quest, rule, count, logicalMinute);
		}
		final long nowMillis = Math.max(0, _epochMillis.getAsLong());
		final long wait = _catalog.limits().questCallbackWaitMillis();
		final long remaining = quest.callbackDeadlineMillis() > nowMillis ? quest.callbackDeadlineMillis() - nowMillis : 0;
		if ((remaining > 0) && (remaining <= wait))
		{
			return retry("acquisition.quest.callback_pending");
		}
		final int attempt = current.state().phaseAttempt() + 1;
		if (attempt < _catalog.limits().verificationAttempts())
		{
			_store.replace(current.profileId(), current.acquisition().rowVersion(), current.state().withPhase(Phase.QUEST_CALLBACK_WAIT, current.state().targetObjectId(), current.state().targetNpcId(), current.state().targetInstanceId(), attempt, logicalMinute));
			return retry("quest.callback_timeout");
		}
		return failSource(current, "quest.callback_timeout", logicalMinute);
	}

	private boolean exactQuestState(ExternalActionLease lease, QuestBinding binding)
	{
		final Rule rule = exactQuestRule(binding);
		return (rule != null) && exactQuestState(lease, binding, rule);
	}

	private Rule exactQuestRule(QuestBinding binding)
	{
		if ((_quests == null) || !_quests.current() || !binding.authorityHash().equals(_quests.authorityHash()))
		{
			return null;
		}
		return _quests.rule(binding.ruleId()).filter(value -> value.ruleHash().equals(binding.ruleHash()) && value.scriptHash().equals(binding.scriptHash()) && (value.questId() == binding.questId()) && value.questName().equals(binding.questName()) && value.requiredState().equals(binding.expectedState()) && value.allowedConds().contains(binding.expectedCond()) && (value.questItemId() == binding.questItemId()) && (value.itemCap() == binding.itemCap()) && value.targetNpcIds().contains(binding.targetNpcId())).orElse(null);
	}

	private static boolean exactQuestState(ExternalActionLease lease, QuestBinding binding, Rule rule)
	{
		final var snapshot = lease.questState(binding.questName(), rule.expectedVars().stream().sorted().toList());
		return (snapshot != null) && binding.expectedState().equals(snapshot.state()) && (binding.expectedCond() == snapshot.cond()) && rule.allowedConds().contains(snapshot.cond()) && snapshot.variables().keySet().equals(java.util.Set.copyOf(rule.expectedVars()));
	}

	private OperationResult observeQuestCollection(Current current, QuestBinding binding, Rule rule, long count, long logicalMinute)
	{
		final long before = binding.itemCountBeforeKill();
		if (count < before)
		{
			return failSource(current, "quest.item_count_decreased", logicalMinute);
		}
		if (count > binding.itemCap())
		{
			return failSource(current, "quest.item_cap", logicalMinute);
		}
		final long delta = count - before;
		if ((delta < rule.minimumCount()) || (delta > rule.maximumCount()))
		{
			return failSource(current, "quest.invalid_delta", logicalMinute);
		}
		final Receipt proof = receipt(current.state(), ReceiptKind.ACTIVE_QUEST_COLLECTION, before, count, TerminalResult.OBSERVED, logicalMinute);
		final QuestBinding observedBinding = questBinding(binding, count, 0);
		final PhantomAcquisitionState observed = current.state().observeBound(count, Status.READY, Phase.TARGET_REQUIRED, 0, 0, 0, observedBinding, 0, proof, logicalMinute);
		final PhantomGoal projected = PhantomAcquisitionGoalSpec.project(current.goal().goal(), observed.progress(), observed.status() == Status.COMPLETED ? PhantomGoalStatus.COMPLETED : PhantomGoalStatus.ACTIVE, observed.selectedSource());
		_store.mutateWithGoal(current.profileId(), current.acquisition().rowVersion(), observed, current.goal().rowVersion(), projected);
		if (observed.status() == Status.COMPLETED)
		{
			_completed.incrementAndGet();
			return OperationResult.complete("acquisition.complete");
		}
		return OperationResult.success("acquisition.quest.collection_observed");
	}

	private static QuestBinding questBinding(QuestBinding binding, long itemCount, long deadline)
	{
		return new QuestBinding(binding.ruleId(), binding.ruleHash(), binding.questId(), binding.questName(), binding.scriptHash(), binding.expectedState(), binding.expectedCond(), binding.questItemId(), binding.itemCap(), binding.targetNpcId(), itemCount, deadline, binding.authorityHash());
	}

	private OperationResult dispatch(Current current, AcquisitionSkillKind kind, Phase dispatching, long logicalNowNanos, long logicalMinute, PhantomCancellationToken token)
	{
		final ExternalActionLease lease = external(current, logicalNowNanos, token);
		if (lease == null)
		{
			return retry("acquisition.actor.unavailable");
		}
		final Source source = current.state().selectedSource();
		final AcquisitionTargetSnapshot target = lease.acquisitionTargetSnapshot(current.state().targetObjectId());
		final boolean valid = (target != null) && (kind == AcquisitionSkillKind.SPOIL ? target.liveValidFor(lease.actorSnapshot(), source.npcId(), MAXIMUM_TARGET_DISTANCE) : target.sweepValidFor(lease.actorSnapshot(), source.npcId(), MAXIMUM_TARGET_DISTANCE));
		final int skillId = kind == AcquisitionSkillKind.SPOIL ? source.spoilSkillId() : source.sweepSkillId();
		final int skillLevel = kind == AcquisitionSkillKind.SPOIL ? source.spoilSkillLevel() : source.sweepSkillLevel();
		if (!valid || (lease.knownSkillLevel(skillId) < skillLevel))
		{
			releaseExternal(current.profileId());
			return failSource(current, "source.ineligible", logicalMinute);
		}
		final StoredState dispatched = _store.replace(current.profileId(), current.acquisition().rowVersion(), current.state().withPhase(dispatching, target.objectId(), target.npcId(), target.instanceId(), current.state().phaseAttempt(), logicalMinute));
		final Current persisted = new Current(current.profileId(), current.goal(), dispatched);
		final ActionOutcome outcome = lease.castAcquisition(target.objectId(), new SelectedSkill(skillId, skillLevel), kind);
		return switch (outcome)
		{
			case ISSUED, ALREADY_OWNED -> OperationResult.success(kind == AcquisitionSkillKind.SPOIL ? "acquisition.spoil.dispatched" : "acquisition.sweep.dispatched");
			case UNAVAILABLE ->
			{
				releaseExternal(current.profileId());
				final int attempt = persisted.state().phaseAttempt() + 1;
				if (attempt >= _catalog.limits().verificationAttempts())
				{
					yield exhaustSource(persisted, "source.repeated_failure", logicalMinute);
				}
				_store.replace(persisted.profileId(), persisted.acquisition().rowVersion(), persisted.state().withPhase(preparedPhase(kind), target.objectId(), target.npcId(), target.instanceId(), attempt, logicalMinute));
				yield retry("acquisition.skill.unavailable");
			}
			case REJECTED ->
			{
				yield failSource(persisted, rejectionReason(lease, persisted.state(), kind), logicalMinute);
			}
		};
	}

	private OperationResult observeSpoil(Current current, long logicalNowNanos, long logicalMinute, PhantomCancellationToken token)
	{
		final ExternalActionLease lease = external(current, logicalNowNanos, token);
		if (lease == null)
		{
			return recoverDispatch(current, AcquisitionSkillKind.SPOIL, ReceiptKind.ACTIVE_SPOIL, logicalMinute, -1);
		}
		final AcquisitionTargetSnapshot target = lease.acquisitionTargetSnapshot(current.state().targetObjectId());
		if ((target != null) && target.spoiled() && (target.spoilerObjectId() == lease.actorSnapshot().objectId()))
		{
			_store.replace(current.profileId(), current.acquisition().rowVersion(), current.state().withPhase(Phase.SPOIL_OBSERVED, target.objectId(), target.npcId(), target.instanceId(), logicalMinute));
			releaseExternal(current.profileId());
			return OperationResult.success("acquisition.spoil.observed");
		}
		if (exactCastActive(lease, current.state(), AcquisitionSkillKind.SPOIL))
		{
			return retry("acquisition.spoil.pending");
		}
		final long count = lease.acquisitionInventoryCount(current.state().targetItemId());
		releaseExternal(current.profileId());
		return recoverDispatch(current, AcquisitionSkillKind.SPOIL, ReceiptKind.ACTIVE_SPOIL, logicalMinute, count);
	}

	private OperationResult prepareCombat(Current current, long logicalMinute)
	{
		_store.replace(current.profileId(), current.acquisition().rowVersion(), current.state().withPhase(Phase.COMBAT_PREPARED, current.state().targetObjectId(), current.state().targetNpcId(), current.state().targetInstanceId(), logicalMinute));
		releaseExternal(current.profileId());
		return OperationResult.success("acquisition.combat.prepared");
	}

	private OperationResult submitCombat(Current current, boolean loot, Phase submittedPhase, long logicalNowNanos, long logicalMinute, PhantomCancellationToken token)
	{
		final String owner = combatOwner(current.state());
		final var existing = _combat.find(current.profileId()).orElse(null);
		if (existing != null)
		{
			if (!_combat.matchesAcquisitionSession(current.profileId(), current.state().targetObjectId(), owner))
			{
				return OperationResult.replan("acquisition.combat.foreign_session");
			}
			_store.replace(current.profileId(), current.acquisition().rowVersion(), current.state().withPhase(submittedPhase, current.state().targetObjectId(), current.state().targetNpcId(), current.state().targetInstanceId(), logicalMinute));
			return OperationResult.success("acquisition.combat.reconciled");
		}
		if ((current.state().selectedSource().method() == Method.MANOR_CROP) || (current.state().selectedSource().method() == Method.QUEST_COLLECTION))
		{
			final ExternalActionLease lease = external(current, logicalNowNanos, token);
			if (lease == null)
			{
				return retry("acquisition.actor.unavailable");
			}
			final AcquisitionTargetSnapshot target = lease.acquisitionTargetSnapshot(current.state().targetObjectId());
			final boolean valid = (target != null) && sourceOwnsTarget(current.state().selectedSource(), target) && target.liveValidFor(lease.actorSnapshot(), current.state().selectedSource().npcId(), MAXIMUM_TARGET_DISTANCE);
			if (!valid)
			{
				releaseExternal(current.profileId());
				return OperationResult.replan("source.target_unavailable");
			}
			if (current.state().selectedSource().method() == Method.QUEST_COLLECTION)
			{
				if (!(current.state().methodBinding() instanceof QuestBinding quest) || !exactQuestState(lease, quest))
				{
					releaseExternal(current.profileId());
					return failSource(current, "quest.cond_ineligible", logicalMinute);
				}
				final long count = lease.acquisitionInventoryCount(quest.questItemId());
				releaseExternal(current.profileId());
				if (count >= quest.itemCap())
				{
					return failSource(current, "quest.item_cap", logicalMinute);
				}
				if (count != quest.itemCountBeforeKill())
				{
					return failSource(current, "quest.item_count_changed", logicalMinute);
				}
			}
			else
			{
				releaseExternal(current.profileId());
			}
		}
		final PhantomCombatService.StartResult started = _combat.startAcquisitionSession(new PhantomCombatRequest(current.profileId(), current.state().targetObjectId(), PhantomCombatMode.MELEE_PHYSICAL, true, loot, COMBAT_TIMEOUT_MILLIS, token), owner);
		if (started.accepted() && (started.session() != null) && (started.session().targetObjectId() == current.state().targetObjectId()) && _combat.matchesAcquisitionSession(current.profileId(), current.state().targetObjectId(), owner))
		{
			_store.replace(current.profileId(), current.acquisition().rowVersion(), current.state().withPhase(submittedPhase, current.state().targetObjectId(), current.state().targetNpcId(), current.state().targetInstanceId(), logicalMinute));
			return OperationResult.success("acquisition.combat.submitted");
		}
		return switch (started.status())
		{
			case REJECTED_EXISTING -> OperationResult.replan("acquisition.combat.foreign_session");
			case REJECTED_TARGET, UNSUPPORTED_LOADOUT, CANCELLED -> OperationResult.replan("acquisition.combat.replan");
			default -> retry("acquisition.combat.unavailable");
		};
	}

	private OperationResult observeCombat(Current current, long logicalNowNanos, long logicalMinute, PhantomCancellationToken token)
	{
		final String owner = combatOwner(current.state());
		if (!_combat.matchesAcquisitionSession(current.profileId(), current.state().targetObjectId(), owner))
		{
			if (_combat.find(current.profileId()).isPresent())
			{
				return OperationResult.replan("acquisition.combat.foreign_session");
			}
			return recoverMissingCombat(current, logicalNowNanos, logicalMinute, token);
		}
		final var snapshot = _combat.find(current.profileId()).orElse(null);
		if (snapshot == null)
		{
			return recoverMissingCombat(current, logicalNowNanos, logicalMinute, token);
		}
		if ((snapshot.targetObjectId() != current.state().targetObjectId()) || !_combat.matchesAcquisitionSession(current.profileId(), current.state().targetObjectId(), owner))
		{
			return OperationResult.replan("acquisition.combat.foreign_session");
		}
		if (!snapshot.result().terminal())
		{
			return retry("acquisition.combat.active");
		}
		final var terminal = _combat.consumeTerminal(current.profileId()).orElse(null);
		if (terminal == null)
		{
			return retry("acquisition.combat.cleanup");
		}
		if ((terminal.targetObjectId() != current.state().targetObjectId()) || !terminal.result().victory())
		{
			return terminal != null && terminal.result() == PhantomCombatResult.CANCELLED ? OperationResult.replan("acquisition.combat.cancelled") : failSource(current, "source.repeated_failure", logicalMinute);
		}
		final Phase terminalPhase = current.state().selectedSource().method() == Method.QUEST_COLLECTION ? Phase.QUEST_COMBAT_TERMINAL : Phase.COMBAT_TERMINAL;
		_store.replace(current.profileId(), current.acquisition().rowVersion(), current.state().withPhase(terminalPhase, current.state().targetObjectId(), current.state().targetNpcId(), current.state().targetInstanceId(), logicalMinute));
		return OperationResult.success("acquisition.combat.terminal");
	}

	private OperationResult recoverMissingCombat(Current current, long logicalNowNanos, long logicalMinute, PhantomCancellationToken token)
	{
		final ExternalActionLease lease = external(current, logicalNowNanos, token);
		if (lease == null)
		{
			return recoverMissingCombatAttempt(current, logicalMinute, -1);
		}
		final long count = lease.acquisitionInventoryCount(current.state().targetItemId());
		final AcquisitionTargetSnapshot target = lease.acquisitionTargetSnapshot(current.state().targetObjectId());
		final Source source = current.state().selectedSource();
		if (source.method() == Method.QUEST_COLLECTION)
		{
			if (!(current.state().methodBinding() instanceof QuestBinding quest))
			{
				releaseExternal(current.profileId());
				return OperationResult.replan("acquisition.quest.binding");
			}
			if (count != quest.itemCountBeforeKill())
			{
				if (count < quest.itemCountBeforeKill())
				{
					releaseExternal(current.profileId());
					return failSource(current, "quest.item_count_decreased", logicalMinute);
				}
				final Rule rule = exactQuestRule(quest);
				final boolean exact = (rule != null) && exactQuestState(lease, quest, rule);
				releaseExternal(current.profileId());
				return exact ? observeQuestCollection(current, quest, rule, count, logicalMinute) : failSource(current, "quest.cond_ineligible", logicalMinute);
			}
		}
		else if (count > current.state().lastObservedCount())
		{
			releaseExternal(current.profileId());
			return advanceToVerify(current, logicalMinute);
		}
		final var actor = lease.actorSnapshot();
		if ((target != null) && (actor != null) && sourceOwnsTarget(source, target) && target.liveValidFor(actor, source.npcId(), MAXIMUM_TARGET_DISTANCE))
		{
			releaseExternal(current.profileId());
			final Phase prepared = source.method() == Method.QUEST_COLLECTION ? Phase.QUEST_COMBAT_PREPARED : Phase.COMBAT_PREPARED;
			_store.replace(current.profileId(), current.acquisition().rowVersion(), current.state().withPhase(prepared, target.objectId(), target.npcId(), target.instanceId(), logicalMinute));
			return OperationResult.success("acquisition.combat.recovered_live_target");
		}
		if ((source.method() == Method.MANOR_CROP) && (current.state().methodBinding() instanceof ManorBinding manor) && (target != null) && (actor != null) && sourceOwnsTarget(source, target) && target.harvestValidFor(actor, source.npcId(), manor.seedItemId(), MAXIMUM_TARGET_DISTANCE))
		{
			releaseExternal(current.profileId());
			_store.replace(current.profileId(), current.acquisition().rowVersion(), current.state().withPhase(Phase.COMBAT_TERMINAL, target.objectId(), target.npcId(), target.instanceId(), logicalMinute));
			return OperationResult.success("acquisition.combat.recovered_seeded_corpse");
		}
		if ((source.method() == Method.QUEST_COLLECTION) && (target != null) && sourceOwnsTarget(source, target) && (target.dead() || target.alikeDead()))
		{
			releaseExternal(current.profileId());
			_store.replace(current.profileId(), current.acquisition().rowVersion(), current.state().withPhase(Phase.QUEST_COMBAT_TERMINAL, target.objectId(), target.npcId(), target.instanceId(), logicalMinute));
			return OperationResult.success("acquisition.quest.recovered_kill");
		}
		if ((source.method() == Method.SPOIL_SWEEP) && (target != null) && (actor != null) && (target.spoilerObjectId() == actor.objectId()) && target.sweepValidFor(actor, source.npcId(), MAXIMUM_TARGET_DISTANCE))
		{
			releaseExternal(current.profileId());
			_store.replace(current.profileId(), current.acquisition().rowVersion(), current.state().withPhase(Phase.COMBAT_TERMINAL, target.objectId(), target.npcId(), target.instanceId(), logicalMinute));
			return OperationResult.success("acquisition.combat.recovered_corpse");
		}
		releaseExternal(current.profileId());
		return recoverMissingCombatAttempt(current, logicalMinute, count);
	}

	private OperationResult recoverMissingCombatAttempt(Current current, long logicalMinute, long observedCount)
	{
		final int attempt = current.state().phaseAttempt() + 1;
		if (attempt < _catalog.limits().verificationAttempts())
		{
			final Phase submitted = current.state().selectedSource().method() == Method.QUEST_COLLECTION ? Phase.QUEST_COMBAT_SUBMITTED : Phase.COMBAT_SUBMITTED;
			_store.replace(current.profileId(), current.acquisition().rowVersion(), current.state().withPhase(submitted, current.state().targetObjectId(), current.state().targetNpcId(), current.state().targetInstanceId(), attempt, logicalMinute));
			return retry("acquisition.combat.missing_session");
		}
		final ReceiptKind kind = switch (current.state().selectedSource().method())
		{
			case DEATH_DROP -> ReceiptKind.ACTIVE_DEATH_DROP;
			case MANOR_CROP -> ReceiptKind.ACTIVE_MANOR_HARVEST;
			case QUEST_COLLECTION -> ReceiptKind.ACTIVE_QUEST_COLLECTION;
			default -> ReceiptKind.ACTIVE_SPOIL;
		};
		if (current.state().selectedSource().method() == Method.QUEST_COLLECTION)
		{
			return failSource(current, "acquisition.combat.missing_session", logicalMinute);
		}
		return uncertain(current, kind, logicalMinute, observedCount);
	}

	private OperationResult prepareSweepOrVerify(Current current, long logicalNowNanos, long logicalMinute, PhantomCancellationToken token)
	{
		if (current.state().selectedSource().method() == Method.DEATH_DROP)
		{
			_store.replace(current.profileId(), current.acquisition().rowVersion(), current.state().withPhase(Phase.VERIFYING, current.state().targetObjectId(), current.state().targetNpcId(), current.state().targetInstanceId(), logicalMinute));
			return OperationResult.success("acquisition.death_drop.verify");
		}
		if (current.state().selectedSource().method() == Method.MANOR_CROP)
		{
			final ExternalActionLease lease = external(current, logicalNowNanos, token);
			if ((lease == null) || !(current.state().methodBinding() instanceof ManorBinding manor))
			{
				return retry("acquisition.actor.unavailable");
			}
			final AcquisitionTargetSnapshot target = lease.acquisitionTargetSnapshot(current.state().targetObjectId());
			final var inventory = lease.manorInventory(manor.seedItemId(), manor.cropItemId(), PhantomAcquisitionManorAuthority.HARVESTER_ITEM_ID);
			final long cropCount = inventory == null ? -1 : inventory.cropCount();
			if (cropCount < current.state().lastObservedCount())
			{
				releaseExternal(current.profileId());
				return failSource(current, "manor.inventory_inconsistent", logicalMinute);
			}
			if (cropCount > current.state().lastObservedCount())
			{
				releaseExternal(current.profileId());
				return observeManor(current, manor, cropCount, ReceiptKind.VERIFY, Phase.HARVEST_PREPARED, 0, logicalMinute, "acquisition.manor.external_crop_observed");
			}
			if ((target == null) || !sourceOwnsTarget(current.state().selectedSource(), target) || !target.harvestValidFor(lease.actorSnapshot(), current.state().selectedSource().npcId(), manor.seedItemId(), MAXIMUM_TARGET_DISTANCE) || (inventory.harvesterObjectId() != manor.harvesterObjectId()) || (inventory.harvesterCount() <= 0))
			{
				releaseExternal(current.profileId());
				return failSource(current, "source.target_unavailable", logicalMinute);
			}
			final ManorBinding refreshed = manorWithCropBaseline(manor, cropCount);
			_store.replace(current.profileId(), current.acquisition().rowVersion(), current.state().withBinding(refreshed, Phase.HARVEST_PREPARED, target.objectId(), target.npcId(), target.instanceId(), 0, logicalMinute));
			releaseExternal(current.profileId());
			return OperationResult.success("acquisition.manor.harvest_prepared");
		}
		final ExternalActionLease lease = external(current, logicalNowNanos, token);
		if (lease == null)
		{
			return retry("acquisition.actor.unavailable");
		}
		final AcquisitionTargetSnapshot target = lease.acquisitionTargetSnapshot(current.state().targetObjectId());
		if ((target == null) || !target.sweepValidFor(lease.actorSnapshot(), current.state().selectedSource().npcId(), MAXIMUM_TARGET_DISTANCE))
		{
			final long count = lease.acquisitionInventoryCount(current.state().targetItemId());
			releaseExternal(current.profileId());
			return count > current.state().lastObservedCount() ? advanceToVerify(current, logicalMinute) : uncertain(current, ReceiptKind.ACTIVE_SWEEP, logicalMinute, count);
		}
		_store.replace(current.profileId(), current.acquisition().rowVersion(), current.state().withPhase(Phase.SWEEP_PREPARED, target.objectId(), target.npcId(), target.instanceId(), logicalMinute));
		return OperationResult.success("acquisition.sweep.prepared");
	}

	private OperationResult observeManor(Current current, ManorBinding binding, long count, ReceiptKind kind, Phase nextPhase, int nextAttempt, long logicalMinute, String reason)
	{
		final long before = kind == ReceiptKind.ACTIVE_MANOR_HARVEST ? binding.cropCountBeforeDispatch() : current.state().lastObservedCount();
		final Receipt proof = receipt(current.state(), kind, before, count, TerminalResult.OBSERVED, logicalMinute);
		final boolean retainTarget = nextPhase == Phase.HARVEST_PREPARED;
		final PhantomAcquisitionState observed = current.state().observeBound(count, Status.ACTIVE, nextPhase, retainTarget ? current.state().targetObjectId() : 0, retainTarget ? current.state().targetNpcId() : 0, retainTarget ? current.state().targetInstanceId() : 0, manorWithCropBaseline(binding, count), nextAttempt, proof, logicalMinute);
		final PhantomGoal projected = PhantomAcquisitionGoalSpec.project(current.goal().goal(), observed.progress(), observed.status() == Status.COMPLETED ? PhantomGoalStatus.COMPLETED : PhantomGoalStatus.ACTIVE, observed.selectedSource());
		_store.mutateWithGoal(current.profileId(), current.acquisition().rowVersion(), observed, current.goal().rowVersion(), projected);
		if (observed.status() == Status.COMPLETED)
		{
			_completed.incrementAndGet();
			return OperationResult.complete("acquisition.complete");
		}
		return OperationResult.success(reason);
	}

	private static ManorBinding manorWithCropBaseline(ManorBinding binding, long cropCount)
	{
		return new ManorBinding(binding.castleId(), binding.seedItemId(), binding.cropItemId(), binding.matureItemId(), binding.reward1ItemId(), binding.reward2ItemId(), binding.seedLevel(), binding.alternative(), binding.rawSeedLimit(), binding.rawCropLimit(), binding.seedObjectId(), binding.harvesterObjectId(), binding.seedCountBeforeDispatch(), cropCount, binding.authorityHash());
	}

	private OperationResult observeSweep(Current current, long logicalNowNanos, long logicalMinute, PhantomCancellationToken token)
	{
		final ExternalActionLease lease = external(current, logicalNowNanos, token);
		if (lease == null)
		{
			return recoverDispatch(current, AcquisitionSkillKind.SWEEP, ReceiptKind.ACTIVE_SWEEP, logicalMinute, -1);
		}
		final long count = lease.acquisitionInventoryCount(current.state().targetItemId());
		final AcquisitionTargetSnapshot target = lease.acquisitionTargetSnapshot(current.state().targetObjectId());
		if (count > current.state().lastObservedCount())
		{
			releaseExternal(current.profileId());
			return advanceToVerify(current, logicalMinute);
		}
		if (exactCastActive(lease, current.state(), AcquisitionSkillKind.SWEEP))
		{
			return retry("acquisition.sweep.pending");
		}
		if ((target == null) || !target.sweepActive())
		{
			releaseExternal(current.profileId());
			return recoverDispatch(current, AcquisitionSkillKind.SWEEP, ReceiptKind.ACTIVE_SWEEP, logicalMinute, count);
		}
		releaseExternal(current.profileId());
		return recoverDispatch(current, AcquisitionSkillKind.SWEEP, ReceiptKind.ACTIVE_SWEEP, logicalMinute, count);
	}

	private OperationResult recoverDispatch(Current current, AcquisitionSkillKind kind, ReceiptKind receiptKind, long logicalMinute, long observedCount)
	{
		final int attempt = current.state().phaseAttempt() + 1;
		if (attempt < _catalog.limits().verificationAttempts())
		{
			_store.replace(current.profileId(), current.acquisition().rowVersion(), current.state().withPhase(preparedPhase(kind), current.state().targetObjectId(), current.state().targetNpcId(), current.state().targetInstanceId(), attempt, logicalMinute));
			return retry(kind == AcquisitionSkillKind.SPOIL ? "acquisition.spoil.recovery_retry" : "acquisition.sweep.recovery_retry");
		}
		return uncertain(current, receiptKind, logicalMinute, observedCount);
	}

	private static Phase preparedPhase(AcquisitionSkillKind kind)
	{
		return kind == AcquisitionSkillKind.SPOIL ? Phase.SPOIL_PREPARED : Phase.SWEEP_PREPARED;
	}

	private static boolean exactCastActive(ExternalActionLease lease, PhantomAcquisitionState state, AcquisitionSkillKind kind)
	{
		final var actor = lease.actorSnapshot();
		final int skillId = kind == AcquisitionSkillKind.SPOIL ? state.selectedSource().spoilSkillId() : state.selectedSource().sweepSkillId();
		final int skillLevel = kind == AcquisitionSkillKind.SPOIL ? state.selectedSource().spoilSkillLevel() : state.selectedSource().sweepSkillLevel();
		return (actor != null) && actor.casting() && "CAST".equals(actor.intention()) && (actor.currentTargetObjectId() == state.targetObjectId()) && (actor.currentSkillId() == skillId) && (actor.currentSkillLevel() == skillLevel);
	}

	private static String rejectionReason(ExternalActionLease lease, PhantomAcquisitionState state, AcquisitionSkillKind kind)
	{
		final Source source = state.selectedSource();
		final int skillId = kind == AcquisitionSkillKind.SPOIL ? source.spoilSkillId() : source.sweepSkillId();
		final int skillLevel = kind == AcquisitionSkillKind.SPOIL ? source.spoilSkillLevel() : source.sweepSkillLevel();
		if (lease.knownSkillLevel(skillId) < skillLevel)
		{
			return "source.ineligible";
		}
		final AcquisitionTargetSnapshot target = lease.acquisitionTargetSnapshot(state.targetObjectId());
		final var actor = lease.actorSnapshot();
		final boolean valid = (target != null) && (actor != null) && (kind == AcquisitionSkillKind.SPOIL ? target.liveValidFor(actor, source.npcId(), MAXIMUM_TARGET_DISTANCE) : target.sweepValidFor(actor, source.npcId(), MAXIMUM_TARGET_DISTANCE));
		return valid ? "source.ineligible" : "source.target_unavailable";
	}

	private boolean sourceOwnsTarget(Source source, AcquisitionTargetSnapshot target)
	{
		return ownsMappedTarget(_knowledge, source, target);
	}

	public static boolean ownsMappedTarget(PhantomGameKnowledgeQuery knowledge, Source source, AcquisitionTargetSnapshot target)
	{
		Objects.requireNonNull(knowledge, "knowledge");
		Objects.requireNonNull(source, "source");
		Objects.requireNonNull(target, "target");
		if ((source.method() != Method.MANOR_CROP) && (source.method() != Method.QUEST_COLLECTION))
		{
			return true;
		}
		if ((target.instanceId() != source.instanceId()) || (target.npcId() != source.npcId()))
		{
			return false;
		}
		final List<SpawnFact> facts = knowledge.snapshot().spawnFactsByNpc().getOrDefault(source.npcId(), List.of());
		final List<TerritoryGeometry> selected = facts.stream().filter(fact -> source.topologyNodeId().equals(fact.topologyNodeId()) && (fact.territoryGeometry() != null)).map(SpawnFact::territoryGeometry).distinct().toList();
		if (selected.size() != 1)
		{
			return false;
		}
		final TerritoryGeometry geometry = selected.getFirst();
		if (target.spawnTerritoryPresent())
		{
			return geometry.geometryHash().equals(target.territoryGeometryHash()) && geometry.sourcePath().equals(target.territorySourcePath()) && geometry.territoryName().equals(target.territoryName());
		}
		if (!target.spawnPresent() || target.exactPointSpawn() || !geometry.contains(target.x(), target.y(), target.z()))
		{
			return false;
		}
		return facts.stream().filter(fact -> (fact.topologyNodeId() != null) && !source.topologyNodeId().equals(fact.topologyNodeId()) && (fact.territoryGeometry() != null)).map(SpawnFact::territoryGeometry).noneMatch(other -> other.contains(target.x(), target.y(), target.z()));
	}

	private static String combatOwner(PhantomAcquisitionState state)
	{
		return "acquisition:" + digest(state.goalId(), state.goalRevision(), state.selectedSource().sourceId(), state.targetObjectId()).substring(0, 48);
	}

	private OperationResult advanceToVerify(Current current, long logicalMinute)
	{
		_store.replace(current.profileId(), current.acquisition().rowVersion(), current.state().withPhase(Phase.VERIFYING, current.state().targetObjectId(), current.state().targetNpcId(), current.state().targetInstanceId(), logicalMinute));
		return OperationResult.success("acquisition.verify.prepared");
	}

	private OperationResult verifyCurrent(Current current, long logicalNowNanos, long logicalMinute, PhantomCancellationToken token)
	{
		if (current.state().selectedSource().method() == Method.QUEST_COLLECTION)
		{
			if (!(current.state().methodBinding() instanceof QuestBinding quest))
			{
				return OperationResult.replan("acquisition.quest.binding");
			}
			final ExternalActionLease lease = external(current, logicalNowNanos, token);
			if (lease == null)
			{
				return retry("acquisition.actor.unavailable");
			}
			final Rule rule = exactQuestRule(quest);
			if ((rule == null) || !exactQuestState(lease, quest, rule))
			{
				releaseExternal(current.profileId());
				return failSource(current, "quest.cond_ineligible", logicalMinute);
			}
			final long count = lease.acquisitionInventoryCount(quest.questItemId());
			releaseExternal(current.profileId());
			return observeQuestCollection(current, quest, rule, count, logicalMinute);
		}
		final ExternalActionLease lease = external(current, logicalNowNanos, token);
		if (lease == null)
		{
			return retry("acquisition.actor.unavailable");
		}
		final long count = lease.acquisitionInventoryCount(current.state().targetItemId());
		releaseExternal(current.profileId());
		if (count < 0)
		{
			return uncertain(current, ReceiptKind.VERIFY, logicalMinute, count);
		}
		final ReceiptKind kind = switch (current.state().selectedSource().method())
		{
			case MANOR_CROP -> ReceiptKind.ACTIVE_MANOR_HARVEST;
			default -> ReceiptKind.VERIFY;
		};
		final Receipt receipt = receipt(current.state(), kind, current.state().lastObservedCount(), count, count > current.state().lastObservedCount() ? TerminalResult.OBSERVED : TerminalResult.NO_PROGRESS, logicalMinute);
		PhantomAcquisitionState observed = current.state().observe(count, Status.READY, Phase.TARGET_REQUIRED, receipt, logicalMinute);
		if (observed.status() != Status.COMPLETED && (count <= current.state().lastObservedCount()))
		{
			observed = observed.failSource("source.target_unavailable", logicalMinute);
		}
		final PhantomGoal projected = PhantomAcquisitionGoalSpec.project(current.goal().goal(), observed.progress(), observed.status() == Status.COMPLETED ? PhantomGoalStatus.COMPLETED : PhantomGoalStatus.ACTIVE, observed.selectedSource());
		_store.mutateWithGoal(current.profileId(), current.acquisition().rowVersion(), observed, current.goal().rowVersion(), projected);
		if (observed.status() == Status.COMPLETED)
		{
			_completed.incrementAndGet();
			return OperationResult.complete("acquisition.complete");
		}
		return observed.status() == Status.BLOCKED ? OperationResult.replan("source.target_unavailable") : OperationResult.success("acquisition.progress.observed");
	}

	private OperationResult uncertain(Current current, ReceiptKind kind, long logicalMinute, long observedCount)
	{
		final long safeCount = observedCount < 0 ? current.state().lastObservedCount() : observedCount;
		if (safeCount > current.state().lastObservedCount())
		{
			final Receipt proof = receipt(current.state(), kind, current.state().lastObservedCount(), safeCount, TerminalResult.OBSERVED, logicalMinute);
			final PhantomAcquisitionState observed = current.state().observe(safeCount, Status.READY, Phase.TARGET_REQUIRED, proof, logicalMinute);
			final PhantomGoal projected = PhantomAcquisitionGoalSpec.project(current.goal().goal(), observed.progress(), observed.status() == Status.COMPLETED ? PhantomGoalStatus.COMPLETED : PhantomGoalStatus.ACTIVE, observed.selectedSource());
			_store.mutateWithGoal(current.profileId(), current.acquisition().rowVersion(), observed, current.goal().rowVersion(), projected);
			if (observed.status() == Status.COMPLETED)
			{
				_completed.incrementAndGet();
				return OperationResult.complete("acquisition.complete");
			}
			return OperationResult.success("acquisition.progress.observed");
		}
		final Receipt receipt = receipt(current.state(), kind, current.state().lastObservedCount(), safeCount, TerminalResult.UNCERTAIN, logicalMinute);
		final PhantomAcquisitionState next = current.state().observe(safeCount, Status.BLOCKED, Phase.NONE, receipt, logicalMinute).failSource("source.target_unavailable", logicalMinute);
		final PhantomGoal projected = PhantomAcquisitionGoalSpec.project(current.goal().goal(), next.progress(), PhantomGoalStatus.ACTIVE, next.selectedSource());
		_store.mutateWithGoal(current.profileId(), current.acquisition().rowVersion(), next, current.goal().rowVersion(), projected);
		_uncertainRecoveries.incrementAndGet();
		_blocked.incrementAndGet();
		return OperationResult.replan("acquisition.recovery.uncertain");
	}

	private OperationResult failSource(Current current, String reason, long logicalMinute)
	{
		cancelTravel(current.profileId());
		releaseExternal(current.profileId());
		try
		{
			final PhantomAcquisitionState failed = current.state().failSource(reason, logicalMinute);
			final Candidate selected = failed.candidates().get(failed.sourceCursor());
			final boolean thresholdReached = selected.failures() >= _catalog.switchPolicy().failureThreshold();
			final PhantomAcquisitionState next = thresholdReached ? failed : new PhantomAcquisitionState(failed.hashes(), failed.goalId(), failed.goalRevision(), failed.targetItemId(), failed.requiredAmount(), failed.baselineCount(), failed.lastObservedCount(), failed.progress(), Status.READY, failed.selectedSource(), failed.candidates(), failed.sourceCursor(), failed.switchCount(), Phase.TARGET_REQUIRED, 0, 0, 0, failed.recipePlan(), failed.methodBinding(), failed.receipts(), 0, logicalMinute);
			_store.replace(current.profileId(), current.acquisition().rowVersion(), next);
			if (thresholdReached)
			{
				_blocked.incrementAndGet();
			}
		}
		catch (RuntimeException exception)
		{
			return OperationResult.replan("acquisition.state.conflict");
		}
		return OperationResult.replan(reason);
	}

	private OperationResult exhaustSource(Current current, String reason, long logicalMinute)
	{
		cancelTravel(current.profileId());
		releaseExternal(current.profileId());
		try
		{
			_store.replace(current.profileId(), current.acquisition().rowVersion(), current.state().failSource(reason, logicalMinute));
			_blocked.incrementAndGet();
			return OperationResult.replan(reason);
		}
		catch (RuntimeException exception)
		{
			return OperationResult.replan("acquisition.state.conflict");
		}
	}

	private ExternalActionLease external(Current current, long logicalNowNanos, PhantomCancellationToken token)
	{
		ExternalActionLease lease = _external.get(current.profileId());
		if ((lease != null) && (lease.actorSnapshot() != null) && !lease.expired(logicalNowNanos))
		{
			return lease;
		}
		if (lease != null)
		{
			releaseExternal(current.profileId());
		}
		final String operation = "acq-" + digest(current.profileId(), current.state().goalId(), current.state().goalRevision(), current.state().phase(), current.state().phaseAttempt(), current.state().targetObjectId()).substring(0, 48);
		final var result = _combat.acquireExternalAction(new ExternalActionRequest(current.profileId(), ExternalActionKind.ACQUISITION, operation, Math.max(1, logicalNowNanos + EXTERNAL_DEADLINE_NANOS), token));
		if ((result.status() != ExternalActionStatus.ACQUIRED) || (result.lease() == null))
		{
			return null;
		}
		final ExternalActionLease existing = _external.putIfAbsent(current.profileId(), result.lease());
		if (existing != null)
		{
			result.lease().close();
			return existing;
		}
		return result.lease();
	}

	private Observation observe(long profileId, PhantomGoal goal, PhantomAcquisitionGoalSpec spec, PhantomActivityState activityState, long logicalNowNanos, PhantomCancellationToken token)
	{
		final Optional<PhantomBackgroundState> durable = _background.acquisitionSnapshot(profileId);
		if (activityState == PhantomActivityState.BACKGROUND)
		{
			if (durable.isEmpty() || !durable.get().hashes().equals(_background.authorityHashes()))
			{
				return null;
			}
			final PhantomBackgroundState state = durable.get();
			final List<Integer> requestedSkills = requestedCapabilitySkillIds(state.identity().activeClassId());
			final Map<Integer, Integer> learnedSkills;
			if (requestedSkills.isEmpty())
			{
				learnedSkills = Map.of();
			}
			else
			{
				final String progressionHash = progressionHash();
				final var eligibility = _background.acquisitionEligibility(profileId, state, requestedSkills, progressionHash);
				if (eligibility.isEmpty() || !eligibility.get().progressionHash().equals(progressionHash) || !eligibility.get().backgroundHashes().equals(state.hashes()))
				{
					return null;
				}
				learnedSkills = eligibility.get().skillLevels();
			}
			final ResourceEvidence resources = new ResourceEvidence(state.inventory().currentLoad(), state.inventory().maximumLoad(), state.inventory().usedSlots(), state.inventory().maximumSlots(), true);
			return new Observation(state.identity().activeClassId(), state.progress().level(), Map.of(spec.itemId(), state.inventory().itemCount(spec.itemId())), learnedSkills, state.position().committedAnchorId(), resources, spec.itemId(), state.inventory().itemCount(spec.itemId()), state);
		}
		final PhantomGoalStateStore.StoredGoal stored = _goals.load(profileId).orElse(null);
		if (stored == null)
		{
			return null;
		}
		final PhantomAcquisitionState placeholder = new PhantomAcquisitionState(hashes(), goal.goalId(), goal.revision(), spec.itemId(), spec.requiredAmount(), spec.baselineCount(), spec.baselineCount(), 0, Status.PLANNING, null, List.of(), 0, 0, Phase.NONE, 0, 0, 0, null, List.of(), 0);
		final Current observationCurrent = new Current(profileId, stored, new StoredState(placeholder, -1));
		final ExternalActionLease lease = external(observationCurrent, logicalNowNanos, token);
		if ((lease == null) || (lease.actorSnapshot() == null))
		{
			return null;
		}
		final int classId = lease.actorSnapshot().classId();
		final Map<Integer, Integer> skills = new HashMap<>();
		for (CapabilityRule rule : _progression.capabilities(classId))
		{
			for (var evidence : rule.evidenceSkills())
			{
				skills.put(evidence.skillId(), lease.knownSkillLevel(evidence.skillId()));
			}
			skills.put(rule.actionSkill().skillId(), lease.knownSkillLevel(rule.actionSkill().skillId()));
		}
		final long count = lease.acquisitionInventoryCount(spec.itemId());
		final String anchor = durable.map(value -> value.position().committedAnchorId()).orElse("");
		final int level = lease.acquisitionLevel();
		releaseExternal(profileId);
		return (count < 0) || (level < 1) ? null : new Observation(classId, level, Map.of(spec.itemId(), count), skills, anchor, ResourceEvidence.unavailable(), spec.itemId(), count, null);
	}

	private Map<Integer, Long> recipeInventory(long profileId, PhantomGoalStateStore.StoredGoal goal, PhantomAcquisitionState previous, PhantomAcquisitionGoalSpec spec, Observation observation, PhantomActivityState activityState, List<Integer> exactItemIds, long logicalNowNanos, PhantomCancellationToken token)
	{
		if (activityState == PhantomActivityState.BACKGROUND)
		{
			return observation.backgroundState() == null ? null : _background.acquisitionInventoryCounts(profileId, observation.backgroundState(), exactItemIds).orElse(null);
		}
		final PhantomAcquisitionState leaseState = previous != null ? previous : new PhantomAcquisitionState(hashes(), goal.goal().goalId(), goal.goal().revision(), spec.itemId(), spec.requiredAmount(), spec.baselineCount(), observation.itemCount(), PhantomAcquisitionState.observedProgress(spec.baselineCount(), observation.itemCount(), spec.requiredAmount()), Status.PLANNING, null, List.of(), 0, 0, Phase.NONE, 0, 0, 0, null, List.of(), 0);
		final Current current = new Current(profileId, goal, new StoredState(leaseState, -1));
		final ExternalActionLease lease = external(current, logicalNowNanos, token);
		if (lease == null)
		{
			return null;
		}
		try
		{
			return lease.acquisitionInventoryCounts(exactItemIds);
		}
		finally
		{
			releaseExternal(profileId);
		}
	}

	private Map<String, QuestEvidence> questEvidence(long profileId, PhantomGoalStateStore.StoredGoal goal, PhantomAcquisitionState previous, PhantomAcquisitionGoalSpec spec, Observation observation, PhantomActivityState activityState, long logicalNowNanos, PhantomCancellationToken token)
	{
		if (_quests == null)
		{
			return Map.of();
		}
		if (activityState == PhantomActivityState.BACKGROUND)
		{
			if (observation.backgroundState() == null)
			{
				return Map.of();
			}
			final List<String> names = _quests.rulesForItem(spec.itemId()).stream().map(rule -> rule.questName()).distinct().sorted().toList();
			if (names.isEmpty())
			{
				return Map.of();
			}
			final Map<String, Map<String, String>> rows = _background.acquisitionQuestRows(profileId, observation.backgroundState(), names).orElse(Map.of());
			final Map<String, QuestEvidence> result = new LinkedHashMap<>();
			for (var rule : _quests.rulesForItem(spec.itemId()))
			{
				final Map<String, String> questRows = rows.get(rule.questName());
				if ((questRows == null) || (questRows.size() != (2 + rule.expectedVars().size())) || !"Started".equals(questRows.get("<state>")))
				{
					continue;
				}
				try
				{
					final int cond = Integer.parseInt(questRows.getOrDefault("cond", "-1"));
					final Map<String, String> variables = new LinkedHashMap<>();
					for (String name : rule.expectedVars())
					{
						if (!questRows.containsKey(name))
						{
							throw new IllegalArgumentException("Missing curated quest variable.");
						}
						variables.put(name, questRows.get(name));
					}
					result.put(rule.id(), new QuestEvidence("STARTED", cond, variables, observation.itemCount()));
				}
				catch (RuntimeException ignored)
				{
					// Exact malformed rows are ineligible; background never repairs quest state.
				}
			}
			return Map.copyOf(result);
		}
		final PhantomAcquisitionState leaseState = previous != null ? previous : new PhantomAcquisitionState(hashes(), goal.goal().goalId(), goal.goal().revision(), spec.itemId(), spec.requiredAmount(), spec.baselineCount(), observation.itemCount(), PhantomAcquisitionState.observedProgress(spec.baselineCount(), observation.itemCount(), spec.requiredAmount()), Status.PLANNING, null, List.of(), 0, 0, Phase.NONE, 0, 0, 0, null, List.of(), 0);
		final Current current = new Current(profileId, goal, new StoredState(leaseState, -1));
		final ExternalActionLease lease = external(current, logicalNowNanos, token);
		if (lease == null)
		{
			return Map.of();
		}
		try
		{
			final Map<String, QuestEvidence> result = new LinkedHashMap<>();
			for (var rule : _quests.rulesForItem(spec.itemId()))
			{
				final var snapshot = lease.questState(rule.questName(), rule.expectedVars().stream().sorted().toList());
				if (snapshot != null)
				{
					result.put(rule.id(), new QuestEvidence(snapshot.state(), snapshot.cond(), snapshot.variables(), observation.itemCount()));
				}
			}
			return Map.copyOf(result);
		}
		finally
		{
			releaseExternal(profileId);
		}
	}

	private List<Integer> requestedCapabilitySkillIds(int classId)
	{
		return _progression.capabilities(classId).stream().filter(rule -> "profession.spoil".equals(rule.capabilityKey()) || "profession.sweep".equals(rule.capabilityKey()) || "profession.craft".equals(rule.capabilityKey())).flatMap(rule -> rule.evidenceSkills().stream()).map(skill -> skill.skillId()).distinct().sorted().limit(8).toList();
	}

	private OperationResult completeObserved(long profileId, PhantomGoalStateStore.StoredGoal goal, StoredState acquisition, long count, long logicalMinute)
	{
		if (acquisition == null)
		{
			return OperationResult.fail("acquisition.baseline.completed_before_plan");
		}
		final Receipt receipt = receipt(acquisition.state(), ReceiptKind.VERIFY, acquisition.state().lastObservedCount(), count, TerminalResult.OBSERVED, logicalMinute);
		final PhantomAcquisitionState next = acquisition.state().observe(count, Status.COMPLETED, Phase.NONE, receipt, logicalMinute);
		final PhantomGoal projected = PhantomAcquisitionGoalSpec.project(goal.goal(), next.progress(), PhantomGoalStatus.COMPLETED, next.selectedSource());
		_store.mutateWithGoal(profileId, acquisition.rowVersion(), next, goal.rowVersion(), projected);
		_completed.incrementAndGet();
		return OperationResult.complete("acquisition.complete");
	}

	private Current current(long profileId, PhantomGoal contextGoal)
	{
		final PhantomGoalStateStore.StoredGoal goal = _goals.load(profileId).orElse(null);
		final StoredState state = _store.load(profileId).orElse(null);
		if ((goal == null) || (state == null) || !sameGoal(contextGoal, goal.goal()) || (state.state().goalId() != goal.goal().goalId()) || (state.state().goalRevision() != goal.goal().revision()))
		{
			return null;
		}
		return new Current(profileId, goal, state);
	}

	private PhantomGoalStateStore.StoredGoal exactActiveGoal(long profileId, PhantomGoal contextGoal)
	{
		final PhantomGoalStateStore.StoredGoal stored = _goals.load(profileId).orElse(null);
		if ((stored == null) || !sameGoal(contextGoal, stored.goal()) || (stored.goal().status() != PhantomGoalStatus.ACTIVE))
		{
			return null;
		}
		return stored;
	}

	private PhantomGoal currentGoal(long profileId, PhantomGoal contextGoal, boolean active)
	{
		final PhantomGoal goal = _goals.load(profileId).map(PhantomGoalStateStore.StoredGoal::goal).orElse(null);
		return (goal != null) && sameGoal(contextGoal, goal) && (!active || (goal.status() == PhantomGoalStatus.ACTIVE)) ? goal : null;
	}

	private boolean stale(Current current)
	{
		if (!current.state().hashes().equals(hashes()))
		{
			return true;
		}
		if (current.state().methodBinding() instanceof ManorBinding manor)
		{
			return (_manor == null) || !_manor.current() || !manor.authorityHash().equals(_manor.authorityHash());
		}
		if (current.state().methodBinding() instanceof QuestBinding quest)
		{
			return (_quests == null) || !_quests.current() || !quest.authorityHash().equals(_quests.authorityHash()) || _quests.rule(quest.ruleId()).filter(rule -> rule.ruleHash().equals(quest.ruleHash()) && rule.scriptHash().equals(quest.scriptHash())).isEmpty();
		}
		return false;
	}

	private Hashes hashes()
	{
		final PhantomBackgroundState.Hashes background = _background.authorityHashes();
		return new Hashes(_catalog.hash(), _knowledge.snapshot().combinedHash(), _topology.snapshot().canonicalHash(), progressionHash(), digest(background.knowledge(), background.topology(), background.progression(), background.commerce()));
	}

	private String progressionHash()
	{
		return _progression.combinedHash().toLowerCase(java.util.Locale.ROOT);
	}

	private void persistTerminal(Current current, Status status, long logicalMinute)
	{
		final PhantomAcquisitionState state = current.state();
		final PhantomAcquisitionState next = new PhantomAcquisitionState(state.hashes(), state.goalId(), state.goalRevision(), state.targetItemId(), state.requiredAmount(), state.baselineCount(), state.lastObservedCount(), state.progress(), status, state.selectedSource(), state.candidates(), state.sourceCursor(), state.switchCount(), Phase.NONE, 0, 0, 0, state.recipePlan(), state.methodBinding(), state.receipts(), 0, logicalMinute);
		_store.replace(current.profileId(), current.acquisition().rowVersion(), next);
	}

	private Claim claim(long profileId)
	{
		if ((_state != ServiceState.RUNNING) || (profileId <= 0) || (_claims.putIfAbsent(profileId, Boolean.TRUE) != null))
		{
			return null;
		}
		_claimsAcquired.incrementAndGet();
		return new Claim(profileId);
	}

	private void releaseExternal(long profileId)
	{
		final ExternalActionLease lease = _external.remove(profileId);
		if (lease != null)
		{
			lease.close();
		}
	}

	private void cancelTravel(long profileId)
	{
		final TravelOperation travel = _travels.remove(profileId);
		if ((travel != null) && (travel.requestId() > 0) && travel.waypoints().isEmpty())
		{
			_navigation.cancel(profileId, travel.requestId());
		}
	}

	private static boolean sameGoal(PhantomGoal context, PhantomGoal actual)
	{
		return (context != null) && (context.goalId() == actual.goalId()) && (context.revision() == actual.revision()) && context.goalType().equals(actual.goalType());
	}

	private static Map<String, Candidate> candidates(PhantomAcquisitionState state)
	{
		if (state == null)
		{
			return Map.of();
		}
		final Map<String, Candidate> result = new LinkedHashMap<>();
		state.candidates().forEach(candidate -> result.put(candidate.sourceId(), candidate));
		return Map.copyOf(result);
	}

	private static java.util.Set<Method> planningMethods(PhantomAcquisitionGoalSpec spec, long progress)
	{
		if ((progress == 0) || !spec.allowedMethods().contains(Method.RECIPE_PREPARATION))
		{
			return spec.allowedMethods();
		}
		final java.util.EnumSet<Method> result = java.util.EnumSet.copyOf(spec.allowedMethods());
		result.remove(Method.RECIPE_PREPARATION);
		if (result.isEmpty())
		{
			throw new IllegalArgumentException("A planning-only recipe cannot continue an executed acquisition Goal.");
		}
		return java.util.Set.copyOf(result);
	}

	private static Candidate merge(RankedSource ranked, Candidate previous)
	{
		return previous == null ? ranked.candidate() : new Candidate(ranked.source().sourceId(), ranked.source().method(), ranked.score(), previous.failures(), previous.lastFailureMinute(), previous.lastFailureReason());
	}

	private static int indexOf(List<Candidate> candidates, String sourceId)
	{
		for (int index = 0; index < candidates.size(); index++)
		{
			if (candidates.get(index).sourceId().equals(sourceId))
			{
				return index;
			}
		}
		throw new IllegalArgumentException("Acquisition source is not a persisted candidate.");
	}

	private static Receipt receipt(PhantomAcquisitionState state, ReceiptKind kind, long before, long after, TerminalResult result, long logicalMinute)
	{
		return new Receipt(digest(state.goalId(), state.goalRevision(), state.selectedSource().sourceId(), kind, before, after, logicalMinute), state.selectedSource().sourceId(), kind, before, after, result, logicalMinute);
	}

	private static String digest(Object... values)
	{
		try
		{
			final MessageDigest digest = MessageDigest.getInstance("SHA-256");
			for (Object value : values)
			{
				digest.update(String.valueOf(value).getBytes(StandardCharsets.UTF_8));
				digest.update((byte) 0);
			}
			return HexFormat.of().formatHex(digest.digest());
		}
		catch (Exception exception)
		{
			throw new IllegalStateException("SHA-256 is unavailable.", exception);
		}
	}

	private static long saturatingAdd(long left, long right)
	{
		return left > (Long.MAX_VALUE - right) ? Long.MAX_VALUE : left + right;
	}

	private static OperationResult map(PhantomBackgroundService.OperationResult result)
	{
		return switch (result.status())
		{
			case SUCCESS, IDEMPOTENT -> OperationResult.success(result.reason());
			case RETRY -> retry(result.reason());
			case REPLAN, INCONSISTENT -> OperationResult.replan(result.reason());
			case FAIL_GOAL -> OperationResult.fail(result.reason());
		};
	}

	private static OperationResult retry(String reason)
	{
		return new OperationResult(OperationStatus.RETRY, reason);
	}

	public Snapshot snapshot()
	{
		return new Snapshot(_state, _catalog.hash(), _planned.get(), _active.get(), _completed.get(), _blocked.get(), _switches.get(), _claimsAcquired.get(), _uncertainRecoveries.get(), _recipeNodes.get(), _claims.size(), _external.size(), _travels.size(), PhantomAcquisitionState.MAX_CANDIDATES, PhantomAcquisitionState.MAX_RECIPE_NODES, new PhantomAcquisitionStateCodec().declaredWorstCaseBytes());
	}

	public enum ServiceState
	{
		NEW,
		RUNNING,
		STOPPING,
		STOPPED
	}

	public enum DirectiveKind
	{
		PLAN,
		TRAVEL,
		ACTIVE,
		BACKGROUND,
		VERIFY,
		SWITCH,
		COMPLETE,
		FAIL,
		BLOCKED
	}

	public enum OperationStatus
	{
		SUCCESS,
		RETRY,
		REPLAN,
		COMPLETE_GOAL,
		FAIL_GOAL
	}

	public record Directive(DirectiveKind kind, String reasonKey, String sourceId, long generation)
	{
		public Directive(DirectiveKind kind, String reasonKey, String sourceId)
		{
			this(kind, reasonKey, sourceId, -1);
		}
	}

	public record OperationResult(OperationStatus status, String reasonKey)
	{
		public static OperationResult success(String reason)
		{
			return new OperationResult(OperationStatus.SUCCESS, reason);
		}

		public static OperationResult replan(String reason)
		{
			return new OperationResult(OperationStatus.REPLAN, reason);
		}

		public static OperationResult complete(String reason)
		{
			return new OperationResult(OperationStatus.COMPLETE_GOAL, reason);
		}

		public static OperationResult fail(String reason)
		{
			return new OperationResult(OperationStatus.FAIL_GOAL, reason);
		}
	}

	public record Snapshot(ServiceState state, String catalogHash, long planned, long activeAdvances, long completed, long blocked, long switches, long claimsAcquired, long uncertainRecoveries, long recipeNodes, int currentClaims, int externalClaims, int navigationClaims, int maximumCandidates, int maximumRecipeNodes, int maximumPayloadBytes)
	{
	}

	private record TravelOperation(String sourceId, long goalRevision, long requestId, List<PhantomNavigationPoint> waypoints, int waypoint)
	{
		private TravelOperation
		{
			waypoints = List.copyOf(waypoints);
		}

		private TravelOperation withWaypoints(List<PhantomNavigationPoint> value)
		{
			return new TravelOperation(sourceId, goalRevision, requestId, value, 0);
		}

		private TravelOperation withWaypoint(int value)
		{
			return new TravelOperation(sourceId, goalRevision, requestId, waypoints, value);
		}
	}

	private record Observation(int classId, int level, Map<Integer, Long> inventory, Map<Integer, Integer> knownSkills, String anchorId, ResourceEvidence resources, int targetItemId, long itemCount, PhantomBackgroundState backgroundState)
	{
		private Observation withInventory(Map<Integer, Long> exactCounts)
		{
			final Map<Integer, Long> merged = new HashMap<>(exactCounts);
			merged.put(targetItemId(), itemCount);
			return new Observation(classId, level, Map.copyOf(merged), knownSkills, anchorId, resources, targetItemId, itemCount, backgroundState);
		}
	}

	private record Current(long profileId, PhantomGoalStateStore.StoredGoal goal, StoredState acquisition)
	{
		private PhantomAcquisitionState state()
		{
			return acquisition.state();
		}
	}

	private final class Claim implements AutoCloseable
	{
		private final long _profileId;
		private boolean _closed;

		private Claim(long profileId)
		{
			_profileId = profileId;
		}

		@Override
		public void close()
		{
			if (!_closed)
			{
				_closed = true;
				_claims.remove(_profileId);
			}
		}
	}
}
