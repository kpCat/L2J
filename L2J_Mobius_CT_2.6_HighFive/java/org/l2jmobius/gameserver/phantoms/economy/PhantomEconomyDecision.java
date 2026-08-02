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
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
 * IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.l2jmobius.gameserver.phantoms.economy;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionGoalSpec;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState;
import org.l2jmobius.gameserver.phantoms.decision.PhantomCandidateRegistry;
import org.l2jmobius.gameserver.phantoms.decision.PhantomConsideration;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDecisionCandidate;
import org.l2jmobius.gameserver.phantoms.decision.PhantomPlan;
import org.l2jmobius.gameserver.phantoms.decision.PhantomPlanStep;
import org.l2jmobius.gameserver.phantoms.decision.PhantomPlanningContext;
import org.l2jmobius.gameserver.phantoms.decision.PhantomStepContext;
import org.l2jmobius.gameserver.phantoms.decision.PhantomStepHandlerRegistry;
import org.l2jmobius.gameserver.phantoms.decision.PhantomStepResult;
import org.l2jmobius.gameserver.phantoms.decision.PhantomStepResult.Type;
import org.l2jmobius.gameserver.phantoms.decision.PhantomWeightedConsideration;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyService.StepResult;

/** Explicit reserve, dispatch and reconcile steps for one economy attempt. */
public final class PhantomEconomyDecision
{
	public static final String CANDIDATE_KEY = "candidate.economy.operation";
	public static final String RESERVE_ACTION = "economy.reserve";
	public static final String DISPATCH_ACTION = "economy.dispatch";
	public static final String RECONCILE_ACTION = "economy.reconcile";
	private static final Set<PhantomActivityState> ALLOWED_STATES = Set.of(PhantomActivityState.ACTIVE, PhantomActivityState.NEARBY_PERCEPTIBLE, PhantomActivityState.BACKGROUND);
	private static final int MAXIMUM_ATTEMPTS = 3;
	private static final long RETRY_DELAY_MILLIS = 250;
	private final PhantomEconomyService _service;

	public PhantomEconomyDecision(PhantomEconomyService service)
	{
		_service = Objects.requireNonNull(service);
	}

	public void registerCandidates(PhantomCandidateRegistry registry)
	{
		Objects.requireNonNull(registry);
		registry.register(new PhantomDecisionCandidate(
			CANDIDATE_KEY,
			Set.of(PhantomAcquisitionGoalSpec.GOAL_TYPE, PhantomEnchantGoalSpec.GOAL_TYPE),
			ALLOWED_STATES,
			List.of(),
			List.of(new PhantomWeightedConsideration("score.economy.operation", 1, context ->
			{
				final boolean supported = _service.supports(context.profileId(), context.goal(), context.effectiveState());
				return new PhantomConsideration.Evaluation(supported ? 1000 : 0, supported ? "economy.operation.ready" : "economy.operation.blocked");
			})),
			1000,
			this::plan));
	}

	public void registerHandlers(PhantomStepHandlerRegistry registry)
	{
		Objects.requireNonNull(registry);
		registry.register(RESERVE_ACTION, context -> execute(context, Phase.RESERVE));
		registry.register(DISPATCH_ACTION, context -> execute(context, Phase.DISPATCH));
		registry.register(RECONCILE_ACTION, context -> execute(context, Phase.RECONCILE));
	}

	private PhantomPlan plan(PhantomPlanningContext context)
	{
		final Map<String, Long> arguments = arguments(context.goal().goalId(), context.goal().revision(), context.activityGeneration());
		final List<PhantomPlanStep> steps = List.of(
			new PhantomPlanStep(0, RESERVE_ACTION, context.goal().target(), arguments, 30_000, MAXIMUM_ATTEMPTS, "economy.reserve.explicit"),
			new PhantomPlanStep(1, DISPATCH_ACTION, context.goal().target(), arguments, 30_000, MAXIMUM_ATTEMPTS, "economy.dispatch.explicit"),
			new PhantomPlanStep(2, RECONCILE_ACTION, context.goal().target(), arguments, 300_000, MAXIMUM_ATTEMPTS, "economy.reconcile.explicit"));
		return new PhantomPlan(context.decisionSequence(), context.goal().goalId(), CANDIDATE_KEY, steps, 360_000, context.logicalNowNanos());
	}

	private PhantomStepResult execute(PhantomStepContext context, Phase phase)
	{
		if (context.cancellationToken().isCancelled())
		{
			return PhantomStepResult.of(Type.CANCELLED, "economy.operation.cancelled");
		}
		if (!Objects.equals(context.goal().target(), context.step().target()) || !arguments(context.goal().goalId(), context.goal().revision(), context.activityGeneration()).equals(context.step().numericArguments()))
		{
			return PhantomStepResult.of(Type.REPLAN, "economy.step.stale");
		}
		final long now = System.currentTimeMillis();
		final StepResult result = switch (phase)
		{
			case RESERVE -> _service.reserve(context.profileId(), context.goal(), context.effectiveState(), context.activityGeneration(), context.tickSequence(), now);
			case DISPATCH -> _service.dispatch(context.profileId(), context.goal(), context.activityGeneration(), now);
			case RECONCILE -> _service.reconcile(context.profileId(), context.goal(), context.effectiveState(), context.activityGeneration(), now);
		};
		return switch (result.status())
		{
			case SUCCESS -> PhantomStepResult.of(Type.SUCCESS, result.reason());
			case RETRY -> PhantomStepResult.retry(RETRY_DELAY_MILLIS, result.reason());
			case REPLAN -> PhantomStepResult.of(Type.REPLAN, result.reason());
		};
	}

	private static Map<String, Long> arguments(long goalId, long revision, long generation)
	{
		return Map.of("goal", goalId, "revision", revision, "generation", generation);
	}

	private enum Phase
	{
		RESERVE,
		DISPATCH,
		RECONCILE
	}
}
