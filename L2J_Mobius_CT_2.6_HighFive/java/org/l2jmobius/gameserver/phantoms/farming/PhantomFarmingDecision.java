/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.farming;

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
import org.l2jmobius.gameserver.phantoms.decision.PhantomStepHandlerRegistry;
import org.l2jmobius.gameserver.phantoms.decision.PhantomStepResult;
import org.l2jmobius.gameserver.phantoms.decision.PhantomWeightedConsideration;
import org.l2jmobius.gameserver.phantoms.farming.PhantomFarmingService.AdvanceResult;

/** One bounded claim/negotiation transition per existing Decision pulse. */
public final class PhantomFarmingDecision
{
	private static final Set<PhantomActivityState> STATES = Set.of(PhantomActivityState.ACTIVE, PhantomActivityState.WARM, PhantomActivityState.BACKGROUND);
	private static final long STEP_TIMEOUT_MILLIS = 30_000;
	private static final long RETRY_DELAY_MILLIS = 1000;
	private final PhantomFarmingService _service;

	public PhantomFarmingDecision(PhantomFarmingService service)
	{
		_service = Objects.requireNonNull(service);
	}

	public void registerCandidates(PhantomCandidateRegistry registry)
	{
		registry.register(new PhantomDecisionCandidate(
			PhantomFarmingService.CANDIDATE_KEY,
			Set.of(PhantomAcquisitionGoalSpec.GOAL_TYPE),
			STATES,
			List.of(),
			List.of(new PhantomWeightedConsideration("score.farming.conflict", 1, context ->
			{
				final boolean work = _service.hasWork(context.profileId());
				return new PhantomConsideration.Evaluation(work ? 1100 : 0, work ? "farming.conflict.required" : "farming.conflict.none");
			})),
			1100,
			this::plan));
	}

	public void registerHandlers(PhantomStepHandlerRegistry registry)
	{
		registry.register(PhantomFarmingService.ADVANCE_ACTION, context ->
		{
			if (context.cancellationToken().isCancelled())
			{
				return PhantomStepResult.of(PhantomStepResult.Type.CANCELLED, "farming.conflict.cancelled");
			}
			final AdvanceResult result = _service.advance(context.profileId());
			return switch (result.status())
			{
				case PROGRESSED, IDEMPOTENT -> PhantomStepResult.of(PhantomStepResult.Type.SUCCESS, result.reasonKey());
				case RETRY -> PhantomStepResult.retry(RETRY_DELAY_MILLIS, result.reasonKey());
				case STALE, FAILED -> PhantomStepResult.of(PhantomStepResult.Type.REPLAN, result.reasonKey());
			};
		});
	}

	private PhantomPlan plan(PhantomPlanningContext context)
	{
		if (!_service.hasWork(context.profileId()))
		{
			throw new IllegalStateException("Farming conflict candidate became non-executable.");
		}
		final PhantomPlanStep step = new PhantomPlanStep(0, PhantomFarmingService.ADVANCE_ACTION, context.goal().target(), Map.of("goal", context.goal().goalId(), "revision", context.goal().revision()), STEP_TIMEOUT_MILLIS, 3, "farming.conflict.advance");
		return new PhantomPlan(context.decisionSequence(), context.goal().goalId(), PhantomFarmingService.CANDIDATE_KEY, List.of(step), STEP_TIMEOUT_MILLIS, context.logicalNowNanos());
	}
}
