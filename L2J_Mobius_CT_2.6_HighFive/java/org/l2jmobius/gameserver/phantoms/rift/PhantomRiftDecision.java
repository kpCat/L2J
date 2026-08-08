/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.rift;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState;
import org.l2jmobius.gameserver.phantoms.decision.PhantomCandidateRegistry;
import org.l2jmobius.gameserver.phantoms.decision.PhantomConsideration;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDecisionCandidate;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
import org.l2jmobius.gameserver.phantoms.decision.PhantomPlan;
import org.l2jmobius.gameserver.phantoms.decision.PhantomPlanStep;
import org.l2jmobius.gameserver.phantoms.decision.PhantomPlanningContext;
import org.l2jmobius.gameserver.phantoms.decision.PhantomStepHandlerRegistry;
import org.l2jmobius.gameserver.phantoms.decision.PhantomStepResult;
import org.l2jmobius.gameserver.phantoms.decision.PhantomWeightedConsideration;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftService.AdvanceOutcome;

public final class PhantomRiftDecision
{
	public static final String CANDIDATE = "candidate.rift.prepare";
	public static final String ACTION = "rift.prepare";
	private static final long STEP_TIMEOUT_MILLIS = 60_000;
	private final PhantomRiftService _service;

	public PhantomRiftDecision(PhantomRiftService service)
	{
		_service = service;
	}

	public void registerCandidates(PhantomCandidateRegistry registry)
	{
		registry.register(new PhantomDecisionCandidate(CANDIDATE, Set.of(PhantomRiftService.GOAL_TYPE), Set.of(PhantomActivityState.ACTIVE, PhantomActivityState.WARM), List.of(), List.of(new PhantomWeightedConsideration("score.rift.prepare", 1, context -> new PhantomConsideration.Evaluation(1000, "rift.goal.explicit"))), 1000, this::plan));
	}

	public void registerHandlers(PhantomStepHandlerRegistry registry)
	{
		registry.register(ACTION, context ->
		{
			if (context.cancellationToken().isCancelled())
			{
				return PhantomStepResult.of(PhantomStepResult.Type.CANCELLED, "rift.prepare.cancelled");
			}
			final int tier = tier(context.goal().target() != null ? context.goal().target() : context.goal().subject());
			if (tier < 1)
			{
				return PhantomStepResult.of(PhantomStepResult.Type.FAIL_GOAL, "rift.tier.invalid");
			}
			final var result = _service.advance(context.profileId(), context.goal().goalId(), context.goal().revision(), tier);
			if (result.outcome() == AdvanceOutcome.READY)
			{
				return PhantomStepResult.of(PhantomStepResult.Type.COMPLETE_GOAL, "rift.ready.observed");
			}
			if (result.outcome() == AdvanceOutcome.REPLAN)
			{
				return PhantomStepResult.of(PhantomStepResult.Type.FAIL_GOAL, result.reasonKey());
			}
			return PhantomStepResult.of(PhantomStepResult.Type.REPLAN, result.reasonKey());
		});
	}

	private PhantomPlan plan(PhantomPlanningContext context)
	{
		final PhantomPlanStep step = new PhantomPlanStep(0, ACTION, context.goal().target(), Map.of(), STEP_TIMEOUT_MILLIS, 1, "rift.prepare.advance");
		return new PhantomPlan(context.decisionSequence(), context.goal().goalId(), CANDIDATE, List.of(step), STEP_TIMEOUT_MILLIS, context.logicalNowNanos());
	}

	private static int tier(PhantomDomainRef ref)
	{
		if ((ref == null) || !"rift.tier".equals(ref.namespace()))
		{
			return -1;
		}
		try
		{
			final int value = Integer.parseInt(ref.key());
			return (value >= 1) && (value <= 6) ? value : -1;
		}
		catch (NumberFormatException e)
		{
			return -1;
		}
	}
}
