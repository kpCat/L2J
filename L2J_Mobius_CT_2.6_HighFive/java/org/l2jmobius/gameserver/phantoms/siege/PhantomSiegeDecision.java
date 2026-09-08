/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.siege;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState;
import org.l2jmobius.gameserver.phantoms.decision.PhantomCandidateRegistry;
import org.l2jmobius.gameserver.phantoms.decision.PhantomConsideration;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDecisionCandidate;
import org.l2jmobius.gameserver.phantoms.decision.PhantomPlan;
import org.l2jmobius.gameserver.phantoms.decision.PhantomPlanStep;
import org.l2jmobius.gameserver.phantoms.decision.PhantomPlanningContext;
import org.l2jmobius.gameserver.phantoms.decision.PhantomStepHandlerRegistry;
import org.l2jmobius.gameserver.phantoms.decision.PhantomStepResult;
import org.l2jmobius.gameserver.phantoms.decision.PhantomWeightedConsideration;
import org.l2jmobius.gameserver.phantoms.siege.PhantomSiegeModel.AdvanceResult;
import org.l2jmobius.gameserver.phantoms.siege.PhantomSiegeModel.Stage;

/** Explicit Goal/Decision bridge; it does not scan clans or own a timer. */
public final class PhantomSiegeDecision
{
	public static final String PREPARE_CANDIDATE = "candidate.siege.prepare";
	public static final String PARTICIPATE_CANDIDATE = "candidate.siege.participate";
	public static final String PREPARE_ACTION = "siege.prepare";
	public static final String PARTICIPATE_ACTION = "siege.participate";
	private static final long STEP_TIMEOUT_MILLIS = 60_000;
	private final PhantomSiegeService _service;

	public PhantomSiegeDecision(PhantomSiegeService service)
	{
		_service = java.util.Objects.requireNonNull(service);
	}

	public void registerCandidates(PhantomCandidateRegistry registry)
	{
		final Set<PhantomActivityState> states = Set.of(PhantomActivityState.ACTIVE, PhantomActivityState.WARM);
		registry.register(new PhantomDecisionCandidate(PREPARE_CANDIDATE, Set.of(PhantomSiegeService.PREPARE_GOAL_TYPE), states, List.of(), List.of(score("score.siege.prepare", "siege.prepare.explicit")), 1000, context -> plan(context, PREPARE_CANDIDATE, PREPARE_ACTION)));
		registry.register(new PhantomDecisionCandidate(PARTICIPATE_CANDIDATE, Set.of(PhantomSiegeService.PARTICIPATE_GOAL_TYPE), states, List.of(), List.of(score("score.siege.participate", "siege.participate.explicit")), 1000, context -> plan(context, PARTICIPATE_CANDIDATE, PARTICIPATE_ACTION)));
	}

	public void registerHandlers(PhantomStepHandlerRegistry registry)
	{
		registry.register(PREPARE_ACTION, context -> advance(context, true));
		registry.register(PARTICIPATE_ACTION, context -> advance(context, false));
	}

	private PhantomStepResult advance(org.l2jmobius.gameserver.phantoms.decision.PhantomStepContext context, boolean prepare)
	{
		if (context.cancellationToken().isCancelled())
		{
			_service.cancel(context.profileId(), context.goal().goalId(), context.goal().revision(), "siege.decision.cancelled");
			return PhantomStepResult.of(PhantomStepResult.Type.CANCELLED, prepare ? "siege.prepare.cancelled" : "siege.participate.cancelled");
		}
		final AdvanceResult result = _service.advance(context.profileId(), context.goal().goalId(), context.goal().revision());
		if (result.stage() == Stage.COMPLETE)
		{
			return PhantomStepResult.of(PhantomStepResult.Type.COMPLETE_GOAL, result.reasonKey());
		}
		if ((result.stage() == Stage.ABANDONED) || (result.stage() == Stage.EXPIRED))
		{
			return PhantomStepResult.of(PhantomStepResult.Type.FAIL_GOAL, result.reasonKey());
		}
		return PhantomStepResult.of(PhantomStepResult.Type.REPLAN, result.reasonKey());
	}

	private static PhantomWeightedConsideration score(String key, String reason)
	{
		return new PhantomWeightedConsideration(key, 1, context -> new PhantomConsideration.Evaluation(1000, reason));
	}

	private static PhantomPlan plan(PhantomPlanningContext context, String candidate, String action)
	{
		final PhantomPlanStep step = new PhantomPlanStep(0, action, context.goal().target(), Map.of(), STEP_TIMEOUT_MILLIS, 1, action + ".advance");
		return new PhantomPlan(context.decisionSequence(), context.goal().goalId(), candidate, List.of(step), STEP_TIMEOUT_MILLIS, context.logicalNowNanos());
	}
}
