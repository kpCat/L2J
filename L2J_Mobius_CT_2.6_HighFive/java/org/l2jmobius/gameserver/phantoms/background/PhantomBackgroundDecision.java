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

import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundService.Directive;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundService.DirectiveKind;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundService.OperationResult;
import org.l2jmobius.gameserver.phantoms.decision.PhantomCandidateRegistry;
import org.l2jmobius.gameserver.phantoms.decision.PhantomConsideration;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDecisionCandidate;
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
 * Decision adapter for one explicit persisted farm.background goal. It neither
 * chooses a target nor manufactures a goal.
 */
public final class PhantomBackgroundDecision
{
	private static final Set<PhantomActivityState> ALLOWED_STATES = Set.of(PhantomActivityState.ACTIVE, PhantomActivityState.WARM, PhantomActivityState.BACKGROUND);
	private static final int STEP_TIMEOUT_MILLIS = 5000;
	private static final int MAXIMUM_ATTEMPTS = 2;
	private static final long RETRY_DELAY_MILLIS = 250;

	private final PhantomBackgroundService _service;

	public PhantomBackgroundDecision(PhantomBackgroundService service)
	{
		_service = Objects.requireNonNull(service, "service");
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
				final boolean executable = (directive.kind() == DirectiveKind.FARM) || (directive.kind() == DirectiveKind.TRAVEL) || (directive.kind() == DirectiveKind.RECOVER);
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
	}

	private PhantomPlan plan(PhantomPlanningContext context)
	{
		final PhantomBackgroundGoalSpec spec = PhantomBackgroundGoalSpec.parse(context.goal());
		final Directive directive = _service.directive(context.profileId(), context.goal(), context.effectiveState());
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
		final OperationResult result = switch (expected)
		{
			case FARM -> _service.farm(context.profileId(), context.goal(), context.activityGeneration(), context.tickSequence(), context.effectiveState(), context.logicalNowNanos());
			case TRAVEL -> _service.travel(context.profileId(), context.goal(), context.activityGeneration(), context.tickSequence(), context.effectiveState(), context.logicalNowNanos());
			case RECOVER -> _service.recover(context.profileId(), context.goal(), context.effectiveState());
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
