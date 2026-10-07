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
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
 * IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.l2jmobius.gameserver.phantoms.background;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.LongConsumer;
import java.util.function.Supplier;

import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundService.Directive;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundService.DirectiveKind;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundService.OperationResult;
import org.l2jmobius.gameserver.phantoms.decision.PhantomCandidateRegistry;
import org.l2jmobius.gameserver.phantoms.decision.PhantomConsideration;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDecisionCandidate;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDecisionEngine;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoal;
import org.l2jmobius.gameserver.phantoms.decision.PhantomPlan;
import org.l2jmobius.gameserver.phantoms.decision.PhantomPlanStep;
import org.l2jmobius.gameserver.phantoms.decision.PhantomPlanningContext;
import org.l2jmobius.gameserver.phantoms.decision.PhantomStepContext;
import org.l2jmobius.gameserver.phantoms.decision.PhantomStepHandlerRegistry;
import org.l2jmobius.gameserver.phantoms.decision.PhantomStepResult;
import org.l2jmobius.gameserver.phantoms.decision.PhantomStepResult.Type;
import org.l2jmobius.gameserver.phantoms.decision.PhantomWeightedConsideration;

/**
 * Decision adapter for one explicit persisted farm.background goal. Visible
 * combat uses the existing server-side AutoPlay pools under the normal goal.
 */
public final class PhantomBackgroundDecision
{
	private static final Set<PhantomActivityState> ALLOWED_STATES = Set.of(PhantomActivityState.ACTIVE, PhantomActivityState.NEARBY_PERCEPTIBLE, PhantomActivityState.WARM, PhantomActivityState.BACKGROUND);
	private static final int STEP_TIMEOUT_MILLIS = 5000;
	private static final int MAXIMUM_ATTEMPTS = 2;
	private static final long RETRY_DELAY_MILLIS = 250;
	private static final long VISIBLE_CORPSE_WINDOW_NANOS = 45_000_000_000L;
	private static final String VISIBLE_START_ACTION = "background.visible.start";
	private static final String VISIBLE_AWAIT_ACTION = "background.visible.await";

	private final PhantomBackgroundService _service;
	private final BiFunction<Long, PhantomGoal, Boolean> _visibleStart;
	private final BiFunction<Long, PhantomGoal, Boolean> _visibleRunning;
	private final BiFunction<Long, PhantomGoal, Boolean> _visibleSuitable;
	private final LongConsumer _visibleStop;
	private BiFunction<Long, PhantomGoal, PhantomStepResult> _typedVisibleStart;
	private BiFunction<Long, PhantomGoal, PhantomHistoricalBackgroundService.Result> _nativeRecoveryReady = (_profileId, _goal) -> PhantomHistoricalBackgroundService.Result.success(null, 0);
	private final ConcurrentHashMap<Long, DeadWindow> _deadWindows = new ConcurrentHashMap<>();

	public PhantomBackgroundDecision(PhantomBackgroundService service)
	{
		this(service, (_profileId, _goal) -> false, (_profileId, _goal) -> false, (_profileId, _goal) -> true, _profileId -> {});
	}

	public PhantomBackgroundDecision(PhantomBackgroundService service, BiFunction<Long, PhantomGoal, Boolean> visibleStart, BiFunction<Long, PhantomGoal, Boolean> visibleRunning, LongConsumer visibleStop)
	{
		this(service, visibleStart, visibleRunning, (_profileId, _goal) -> true, visibleStop);
	}

	public PhantomBackgroundDecision(PhantomBackgroundService service, BiFunction<Long, PhantomGoal, Boolean> visibleStart, BiFunction<Long, PhantomGoal, Boolean> visibleRunning, BiFunction<Long, PhantomGoal, Boolean> visibleSuitable, LongConsumer visibleStop)
	{
		_service = Objects.requireNonNull(service, "service");
		_visibleStart = Objects.requireNonNull(visibleStart, "visibleStart");
		_visibleRunning = Objects.requireNonNull(visibleRunning, "visibleRunning");
		_visibleSuitable = Objects.requireNonNull(visibleSuitable, "visibleSuitable");
		_visibleStop = Objects.requireNonNull(visibleStop, "visibleStop");
	}

	public static PhantomBackgroundDecision bindVisibleLife(PhantomBackgroundService service, PhantomVisibleFarmTravel travel, PhantomVisibleAutoPlay autoPlay, PhantomHistoricalBackgroundService history, Supplier<PhantomDecisionEngine> engine)
	{
		Objects.requireNonNull(travel, "travel");
		Objects.requireNonNull(autoPlay, "autoPlay");
		Objects.requireNonNull(history, "history");
		Objects.requireNonNull(engine, "engine");
		history.bindVisibleRecovery(travel, autoPlay);
		final var adapter = new PhantomBackgroundDecision(service, (profileId, goal) ->
		{
			if (!travel.arrive(profileId, goal))
			{
				autoPlay.stop(profileId);
				return false;
			}
			return autoPlay.start(profileId, goal);
		}, autoPlay::running, (profileId, goal) ->
		{
			if (autoPlay.noTargetExpired(profileId, goal))
			{
				history.recordVisibleFailure(profileId, goal, "");
			}
			return history.visibleFarmReady(profileId, goal);
		}, autoPlay::stop);
		adapter._nativeRecoveryReady = history::prepareNativeRecovery;
		adapter._typedVisibleStart = (profileId, goal) ->
		{
			final var arrival = travel.observeArrival(profileId, goal);
			return switch (arrival.kind())
			{
				case ARRIVED -> autoPlay.start(profileId, goal) ? PhantomStepResult.of(Type.SUCCESS, "background.visible.autoplay_started") : PhantomStepResult.retry(RETRY_DELAY_MILLIS, "background.visible.start_retry");
				case PENDING -> { autoPlay.stop(profileId); yield PhantomStepResult.retry(RETRY_DELAY_MILLIS, "background.visible.start_retry"); }
				case STALE_GOAL, TERMINAL -> { autoPlay.stop(profileId); yield PhantomStepResult.of(Type.REPLAN, arrival.reason()); }
			};
		};
		return adapter;
	}

	public void registerCandidates(PhantomCandidateRegistry registry)
	{
		Objects.requireNonNull(registry, "registry");
		registry.register(new PhantomDecisionCandidate(
			PhantomBackgroundGoalSpec.CANDIDATE_KEY,
			Set.of(PhantomBackgroundGoalSpec.GOAL_TYPE),
			ALLOWED_STATES,
			List.of(),
			List.of(new PhantomWeightedConsideration("score.background.farm", 1, context ->
			{
				final Directive directive = _service.directive(context.profileId(), context.goal(), context.effectiveState());
				final boolean visible = visibleEligible(context.effectiveState(), directive);
				final boolean recoverable = recoveryReady(context, directive);
				final boolean executable = visible || (directive.kind() == DirectiveKind.FARM) || (directive.kind() == DirectiveKind.TRAVEL) || ((directive.kind() == DirectiveKind.RECOVER) && recoverable);
				return new PhantomConsideration.Evaluation(executable ? 1000 : 0, executable ? "background.explicit.ready" : "background.explicit.blocked");
			})),
			1000,
			this::plan));
	}

	public void registerHandlers(PhantomStepHandlerRegistry registry)
	{
		Objects.requireNonNull(registry, "registry");
		registry.register(PhantomBackgroundGoalSpec.TRAVEL_ACTION, context -> execute(context, DirectiveKind.TRAVEL));
		registry.register(PhantomBackgroundGoalSpec.FARM_ACTION, context -> execute(context, DirectiveKind.FARM));
		registry.register(PhantomBackgroundGoalSpec.RECOVER_ACTION, context -> execute(context, DirectiveKind.RECOVER));
		registry.register(VISIBLE_START_ACTION, this::startVisible);
		registry.register(VISIBLE_AWAIT_ACTION, this::awaitVisible);
	}

	private PhantomPlan plan(PhantomPlanningContext context)
	{
		final PhantomBackgroundGoalSpec spec = PhantomBackgroundGoalSpec.parse(context.goal());
		final Directive directive = _service.directive(context.profileId(), context.goal(), context.effectiveState());
		if (visibleEligible(context.effectiveState(), directive))
		{
			final PhantomPlanStep start = new PhantomPlanStep(0, VISIBLE_START_ACTION, exactSource(context.goal(), spec), Map.of("npc", (long) spec.npcId()), 5_000, 2, "visible.farm.autoplay.start");
			final PhantomPlanStep await = new PhantomPlanStep(1, VISIBLE_AWAIT_ACTION, null, Map.of(), 12_000, 10, "visible.farm.autoplay.await");
			return new PhantomPlan(context.decisionSequence(), context.goal().goalId(), PhantomBackgroundGoalSpec.CANDIDATE_KEY, List.of(start, await), 15_000, context.logicalNowNanos());
		}
		final String action = switch (directive.kind())
		{
			case TRAVEL -> PhantomBackgroundGoalSpec.TRAVEL_ACTION;
			case FARM -> PhantomBackgroundGoalSpec.FARM_ACTION;
			case RECOVER -> PhantomBackgroundGoalSpec.RECOVER_ACTION;
			default -> throw new IllegalStateException("Background candidate became non-executable.");
		};
		final PhantomDomainRef source = exactSource(context.goal(), spec);
		final PhantomPlanStep step = new PhantomPlanStep(0, action, source, Map.of("npc", (long) spec.npcId()), STEP_TIMEOUT_MILLIS, MAXIMUM_ATTEMPTS, action + ".explicit");
		return new PhantomPlan(context.decisionSequence(), context.goal().goalId(), PhantomBackgroundGoalSpec.CANDIDATE_KEY, List.of(step), STEP_TIMEOUT_MILLIS, context.logicalNowNanos());
	}

	private static boolean visibleEligible(PhantomActivityState state, Directive directive)
	{
		return (state.requiresMaterialization() || (state == PhantomActivityState.WARM)) && (directive.kind() == DirectiveKind.REPLAN) && ("recovery.not_dead".equals(directive.reason()) || "visible.travel_pending".equals(directive.reason()));
	}

	private boolean recoveryReady(PhantomPlanningContext context, Directive directive)
	{
		if ((directive.kind() != DirectiveKind.RECOVER) || !context.effectiveState().requiresMaterialization())
		{
			_deadWindows.remove(context.profileId());
			return true;
		}
		if (_service.normalResurrectionPending(context.profileId()))
		{
			_deadWindows.remove(context.profileId());
			return true;
		}
		final DeadWindow window = _deadWindows.compute(context.profileId(), (profileId, existing) -> ((existing != null) && (existing.goalId() == context.goal().goalId()) && (existing.revision() == context.goal().revision()) && (context.logicalNowNanos() >= existing.firstObservedNanos())) ? existing : new DeadWindow(context.goal().goalId(), context.goal().revision(), context.logicalNowNanos()));
		return (context.logicalNowNanos() - window.firstObservedNanos()) >= VISIBLE_CORPSE_WINDOW_NANOS;
	}

	private record DeadWindow(long goalId, long revision, long firstObservedNanos)
	{
	}

	private PhantomStepResult startVisible(PhantomStepContext context)
	{
		if (context.cancellationToken().isCancelled())
		{
			return PhantomStepResult.of(Type.CANCELLED, "background.visible.cancelled");
		}
		final PhantomBackgroundGoalSpec spec;
		try
		{
			spec = PhantomBackgroundGoalSpec.parse(context.goal());
			if (!exactSource(context.goal(), spec).equals(context.step().target()) || !Map.of("npc", (long) spec.npcId()).equals(context.step().numericArguments()))
			{
				return PhantomStepResult.of(Type.REPLAN, "background.visible.stale");
			}
		}
		catch (IllegalArgumentException exception)
		{
			return PhantomStepResult.of(Type.REPLAN, "background.visible.invalid");
		}
		if (!visibleEligible(context.effectiveState(), _service.directive(context.profileId(), context.goal(), context.effectiveState())))
		{
			return PhantomStepResult.of(Type.REPLAN, "background.visible.blocked");
		}
		if (!_visibleSuitable.apply(context.profileId(), context.goal()))
		{
			_visibleStop.accept(context.profileId());
			return PhantomStepResult.of(Type.REPLAN, "background.visible.replan_required");
		}
		return _typedVisibleStart != null ? _typedVisibleStart.apply(context.profileId(), context.goal()) : _visibleStart.apply(context.profileId(), context.goal()) ? PhantomStepResult.of(Type.SUCCESS, "background.visible.autoplay_started") : PhantomStepResult.retry(RETRY_DELAY_MILLIS, "background.visible.start_retry");
	}

	private PhantomStepResult awaitVisible(PhantomStepContext context)
	{
		if (context.cancellationToken().isCancelled())
		{
			_visibleStop.accept(context.profileId());
			return PhantomStepResult.of(Type.CANCELLED, "background.visible.cancelled");
		}
		if (!_visibleSuitable.apply(context.profileId(), context.goal()))
		{
			_visibleStop.accept(context.profileId());
			return PhantomStepResult.of(Type.REPLAN, "background.visible.replan_required");
		}
		if (!visibleEligible(context.effectiveState(), _service.directive(context.profileId(), context.goal(), context.effectiveState())) || !_visibleRunning.apply(context.profileId(), context.goal()))
		{
			_visibleStop.accept(context.profileId());
			return PhantomStepResult.of(Type.REPLAN, "background.visible.autoplay_stopped");
		}
		return PhantomStepResult.retry(1_000, "background.visible.autoplay_running");
	}

	private PhantomStepResult execute(PhantomStepContext context, DirectiveKind expected)
	{
		if (context.cancellationToken().isCancelled())
		{
			return PhantomStepResult.of(Type.CANCELLED, "background.cancelled");
		}
		final PhantomBackgroundGoalSpec spec;
		try
		{
			spec = PhantomBackgroundGoalSpec.parse(context.goal());
			if (!exactSource(context.goal(), spec).equals(context.step().target()) || !Map.of("npc", (long) spec.npcId()).equals(context.step().numericArguments()))
			{
				return PhantomStepResult.of(Type.REPLAN, "background.step.stale");
			}
		}
		catch (IllegalArgumentException exception)
		{
			return PhantomStepResult.of(Type.REPLAN, "background.step.invalid");
		}
		if (expected == DirectiveKind.RECOVER)
		{
			final var prepared = _nativeRecoveryReady.apply(context.profileId(), context.goal());
			if (!prepared.successful()) { return PhantomStepResult.retry(RETRY_DELAY_MILLIS, "background.recovery.baseline_retry." + prepared.reason()); }
		}
		final OperationResult result = switch (expected)
		{
			case FARM -> _service.farm(context.profileId(), context.goal(), context.activityGeneration(), context.tickSequence(), context.effectiveState(), context.logicalNowNanos());
			case TRAVEL -> _service.travel(context.profileId(), context.goal(), context.activityGeneration(), context.tickSequence(), context.effectiveState(), context.logicalNowNanos(), System.currentTimeMillis() / 60_000);
			case RECOVER -> _service.recover(context.profileId(), context.goal(), context.effectiveState(), context.cancellationToken()::isCancelled);
			default -> throw new IllegalArgumentException("Unsupported background directive.");
		};
		final String reason = switch (result.status())
		{
			case SUCCESS -> "background.success";
			case IDEMPOTENT -> "background.idempotent";
			case RETRY -> "background.retry";
			case REPLAN -> "background.replan";
			case INCONSISTENT -> "background.inconsistent";
			case FAIL_GOAL -> "background.death.recovered";
		};
		return switch (result.status())
		{
			case SUCCESS, IDEMPOTENT -> PhantomStepResult.of(Type.SUCCESS, reason);
			case RETRY -> PhantomStepResult.retry(RETRY_DELAY_MILLIS, reason);
			case REPLAN, INCONSISTENT -> PhantomStepResult.of(Type.REPLAN, reason);
			case FAIL_GOAL -> PhantomStepResult.of(Type.FAIL_GOAL, reason);
		};
	}

	private static PhantomDomainRef exactSource(PhantomGoal goal, PhantomBackgroundGoalSpec spec)
	{
		return goal.validSources().stream().filter(source -> PhantomBackgroundGoalSpec.SOURCE_NAMESPACE.equals(source.namespace()) && (source.key().equals(spec.npcId() + "@" + spec.anchorId()))).findFirst().orElseThrow(() -> new IllegalArgumentException("Exact background source is absent."));
	}
}
