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
package org.l2jmobius.gameserver.phantoms.economy;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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
import org.l2jmobius.gameserver.phantoms.decision.PhantomWeightedConsideration;
import org.l2jmobius.gameserver.phantoms.economy.PhantomMultipartyEconomyService.StepResult;

/** Six explicit durable steps for Goal 022 C2 social operations. */
public final class PhantomMultipartyEconomyDecision
{
	public static final String CANDIDATE_KEY = "candidate.economy.multiparty";
	public static final String DISCOVER_OR_LOAD_OFFER = "economy.multiparty.discover_offer";
	public static final String OFFER_OR_ACCEPT = "economy.multiparty.offer_accept";
	public static final String RESERVE = "economy.multiparty.reserve";
	public static final String DISPATCH = "economy.multiparty.dispatch";
	public static final String OBSERVE_RECONCILE = "economy.multiparty.observe_reconcile";
	public static final String CLOSE = "economy.multiparty.close";
	private static final Set<PhantomActivityState> ALLOWED_STATES = Set.of(PhantomActivityState.ACTIVE, PhantomActivityState.NEARBY_PERCEPTIBLE, PhantomActivityState.BACKGROUND);
	private static final long RETRY_DELAY_MILLIS = 250;
	private final PhantomMultipartyEconomyService _service;

	public PhantomMultipartyEconomyDecision(PhantomMultipartyEconomyService service)
	{
		_service = Objects.requireNonNull(service);
	}

	public void registerCandidates(PhantomCandidateRegistry registry)
	{
		registry.register(new PhantomDecisionCandidate(CANDIDATE_KEY, Set.of(PhantomSocialEconomyGoalSpec.DIRECT_TRADE_GOAL, PhantomSocialEconomyGoalSpec.STORE_BUY_GOAL, PhantomSocialEconomyGoalSpec.STORE_SELL_GOAL, PhantomSocialEconomyGoalSpec.MANUFACTURE_GOAL), ALLOWED_STATES, List.of(), List.of(new PhantomWeightedConsideration("score.economy.multiparty", 1, context ->
		{
			final boolean supported = _service.supports(context.profileId(), context.goal(), context.effectiveState());
			return new PhantomConsideration.Evaluation(supported ? 1000 : 0, supported ? "economy.multiparty.ready" : "economy.multiparty.blocked");
		})), 1000, this::plan));
	}

	public void registerHandlers(PhantomStepHandlerRegistry registry)
	{
		registry.register(DISCOVER_OR_LOAD_OFFER, context -> execute(context, Phase.DISCOVER));
		registry.register(OFFER_OR_ACCEPT, context -> execute(context, Phase.ACCEPT));
		registry.register(RESERVE, context -> execute(context, Phase.RESERVE));
		registry.register(DISPATCH, context -> execute(context, Phase.DISPATCH));
		registry.register(OBSERVE_RECONCILE, context -> execute(context, Phase.OBSERVE));
		registry.register(CLOSE, context -> execute(context, Phase.CLOSE));
	}

	private PhantomPlan plan(PhantomPlanningContext context)
	{
		final Map<String, Long> arguments = arguments(context.goal().goalId(), context.goal().revision(), context.activityGeneration());
		final List<PhantomPlanStep> steps = List.of(
			new PhantomPlanStep(0, DISCOVER_OR_LOAD_OFFER, context.goal().target(), arguments, 30_000, 3, "economy.offer.discover"),
			new PhantomPlanStep(1, OFFER_OR_ACCEPT, context.goal().target(), arguments, 120_000, 32, "economy.offer.accept"),
			new PhantomPlanStep(2, RESERVE, context.goal().target(), arguments, 30_000, 3, "economy.reserve.explicit"),
			new PhantomPlanStep(3, DISPATCH, context.goal().target(), arguments, 30_000, 3, "economy.dispatch.explicit"),
			new PhantomPlanStep(4, OBSERVE_RECONCILE, context.goal().target(), arguments, 300_000, 32, "economy.observe.explicit"),
			new PhantomPlanStep(5, CLOSE, context.goal().target(), arguments, 30_000, 3, "economy.close.explicit"));
		return new PhantomPlan(context.decisionSequence(), context.goal().goalId(), CANDIDATE_KEY, steps, 540_000, context.logicalNowNanos());
	}

	private PhantomStepResult execute(PhantomStepContext context, Phase phase)
	{
		if (context.cancellationToken().isCancelled())
		{
			return map(_service.cancel(context.profileId(), context.goal(), System.currentTimeMillis()), true);
		}
		if (!Objects.equals(context.goal().target(), context.step().target()) || !arguments(context.goal().goalId(), context.goal().revision(), context.activityGeneration()).equals(context.step().numericArguments()))
		{
			return PhantomStepResult.of(PhantomStepResult.Type.REPLAN, "economy.multiparty.step.stale");
		}
		final long now = System.currentTimeMillis();
		final StepResult result = switch (phase)
		{
			case DISCOVER -> _service.discoverOrLoad(context.profileId(), context.goal(), now);
			case ACCEPT -> _service.offerOrAccept(context.profileId(), context.goal(), context.effectiveState(), now);
			case RESERVE -> _service.reserve(context.profileId(), context.goal(), context.activityGeneration(), context.tickSequence(), now);
			case DISPATCH -> _service.dispatch(context.profileId(), context.goal(), now);
			case OBSERVE -> _service.observeReconcile(context.profileId(), context.goal(), context.effectiveState(), now);
			case CLOSE -> _service.close(context.profileId(), context.goal(), now);
		};
		return map(result, false);
	}

	private static PhantomStepResult map(StepResult result, boolean cancellation)
	{
		return switch (result.status())
		{
			case SUCCESS -> PhantomStepResult.of(cancellation ? PhantomStepResult.Type.CANCELLED : PhantomStepResult.Type.SUCCESS, result.reason());
			case RETRY, ACTIVE_REQUIRED -> PhantomStepResult.retry(RETRY_DELAY_MILLIS, result.reason());
			case REPLAN -> PhantomStepResult.of(PhantomStepResult.Type.REPLAN, result.reason());
		};
	}

	private static Map<String, Long> arguments(long goalId, long revision, long generation)
	{
		return Map.of("goal", goalId, "revision", revision, "generation", generation);
	}

	private enum Phase
	{
		DISCOVER,
		ACCEPT,
		RESERVE,
		DISPATCH,
		OBSERVE,
		CLOSE
	}
}
