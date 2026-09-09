/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.questinstance;

import java.util.EnumSet;
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

/** Explicit Decision bridge for the bounded supported-content goal types. */
public final class PhantomQuestInstanceDecision
{
	private static final long STEP_TIMEOUT_MILLIS = 60_000;
	private static final Set<PhantomActivityState> STATES = Set.copyOf(EnumSet.allOf(PhantomActivityState.class));
	private static final Set<String> GOALS = Set.of(PhantomQuestInstanceService.QUEST_GOAL_TYPE, PhantomQuestInstanceService.CLASS_GOAL_TYPE, PhantomQuestInstanceService.INSTANCE_GOAL_TYPE);
	private final PhantomQuestInstanceService _service;

	public PhantomQuestInstanceDecision(PhantomQuestInstanceService service)
	{
		_service = java.util.Objects.requireNonNull(service, "service");
	}

	public void registerCandidates(PhantomCandidateRegistry registry)
	{
		registry.register(new PhantomDecisionCandidate(PhantomQuestInstanceService.CANDIDATE_KEY, GOALS, STATES, List.of(), List.of(new PhantomWeightedConsideration("score.questinstance.play", 1, context -> new PhantomConsideration.Evaluation(1000, "questinstance.explicit"))), 1000, this::plan));
	}

	public void registerHandlers(PhantomStepHandlerRegistry registry)
	{
		registry.register(PhantomQuestInstanceService.ACTION_KEY, context ->
		{
			final var result = _service.advance(context.profileId(), context.goal().goalId(), context.goal().revision(), context.effectiveState(), context.logicalNowNanos(), context.cancellationToken());
			return switch (result.status())
			{
				case PROGRESS -> PhantomStepResult.of(PhantomStepResult.Type.REPLAN, result.reasonKey());
				case RETRY -> PhantomStepResult.retry(500, result.reasonKey());
				case COMPLETE -> PhantomStepResult.of(PhantomStepResult.Type.COMPLETE_GOAL, result.reasonKey());
				case FAIL -> PhantomStepResult.of(PhantomStepResult.Type.FAIL_GOAL, result.reasonKey());
				case CANCELLED -> PhantomStepResult.of(PhantomStepResult.Type.CANCELLED, result.reasonKey());
			};
		});
	}

	private PhantomPlan plan(PhantomPlanningContext context)
	{
		final PhantomPlanStep step = new PhantomPlanStep(0, PhantomQuestInstanceService.ACTION_KEY, context.goal().target(), Map.of("goal", context.goal().goalId(), "revision", context.goal().revision()), STEP_TIMEOUT_MILLIS, 10, "questinstance.advance");
		return new PhantomPlan(context.decisionSequence(), context.goal().goalId(), PhantomQuestInstanceService.CANDIDATE_KEY, List.of(step), STEP_TIMEOUT_MILLIS, context.logicalNowNanos());
	}
}
