/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.raid;

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
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidAssemblyService.AssemblyStatus;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidAssemblyService.ParticipationOutcome;

public final class PhantomRaidDecision
{
	public static final String PREPARE_CANDIDATE = "candidate.raid.prepare";
	public static final String PARTICIPATE_CANDIDATE = "candidate.raid.participate";
	public static final String PREPARE_ACTION = "raid.prepare";
	public static final String PARTICIPATE_ACTION = "raid.participate";
	private static final long STEP_TIMEOUT_MILLIS = 60_000;

	private final AssemblyPort _assembly;
	private final AttemptPort _attempt;

	public PhantomRaidDecision(AssemblyPort assembly, AttemptPort attempt)
	{
		_assembly = java.util.Objects.requireNonNull(assembly);
		_attempt = java.util.Objects.requireNonNull(attempt);
	}

	public void registerCandidates(PhantomCandidateRegistry registry)
	{
		final Set<PhantomActivityState> states = Set.of(PhantomActivityState.ACTIVE, PhantomActivityState.WARM);
		registry.register(new PhantomDecisionCandidate(PREPARE_CANDIDATE, Set.of(PhantomRaidAssemblyService.PREPARE_GOAL_TYPE), states, List.of(), List.of(score("score.raid.prepare", "raid.prepare.explicit")), 1000, context -> plan(context, PREPARE_CANDIDATE, PREPARE_ACTION)));
		registry.register(new PhantomDecisionCandidate(PARTICIPATE_CANDIDATE, Set.of(PhantomRaidAssemblyService.PARTICIPATE_GOAL_TYPE), states, List.of(), List.of(score("score.raid.participate", "raid.participate.explicit")), 1000, context -> plan(context, PARTICIPATE_CANDIDATE, PARTICIPATE_ACTION)));
	}

	public void registerHandlers(PhantomStepHandlerRegistry registry)
	{
		registry.register(PREPARE_ACTION, context ->
		{
			if (context.cancellationToken().isCancelled())
			{
				_attempt.cancel(context.profileId(), context.goal().goalId(), context.goal().revision(), "raid.attempt.decision_cancelled");
				_assembly.cancel(context.profileId(), context.goal().goalId(), context.goal().revision(), "raid.assembly.decision_cancelled");
				return PhantomStepResult.of(PhantomStepResult.Type.CANCELLED, "raid.prepare.cancelled");
			}
			final PhantomRaidAttemptService.AdvanceResult attempt = _attempt.advance(context.profileId(), context.goal().goalId(), context.goal().revision());
			if (attempt.status() != PhantomRaidAttemptService.AttemptStatus.WAITING_FOR_READY)
			{
				return attemptResult(attempt);
			}
			final var assembly = _assembly.advance(context.profileId(), context.goal().goalId(), context.goal().revision());
			if (assembly.status() == AssemblyStatus.CANCELLED)
			{
				return PhantomStepResult.of(PhantomStepResult.Type.CANCELLED, assembly.reasonKey());
			}
			if ((assembly.status() == AssemblyStatus.BLOCKED) || (assembly.status() == AssemblyStatus.EXPIRED))
			{
				return PhantomStepResult.of(PhantomStepResult.Type.FAIL_GOAL, assembly.reasonKey());
			}
			if (assembly.status() != AssemblyStatus.READY_AT_STAGING)
			{
				return PhantomStepResult.of(PhantomStepResult.Type.REPLAN, assembly.reasonKey());
			}
			return attemptResult(_attempt.advance(context.profileId(), context.goal().goalId(), context.goal().revision()));
		});
		registry.register(PARTICIPATE_ACTION, context ->
		{
			if (context.cancellationToken().isCancelled())
			{
				return PhantomStepResult.of(PhantomStepResult.Type.CANCELLED, "raid.participate.cancelled");
			}
			return switch (_attempt.participation(context.profileId(), context.goal().goalId(), context.goal().revision()))
			{
				case WAITING_FOR_LEADER, ACTIVE, RETREATING -> PhantomStepResult.of(PhantomStepResult.Type.REPLAN, "raid.participate.attempt_active");
				case VICTORY -> PhantomStepResult.of(PhantomStepResult.Type.COMPLETE_GOAL, "raid.participate.victory");
				case EXPIRED -> PhantomStepResult.of(PhantomStepResult.Type.FAIL_GOAL, "raid.participate.expired");
				case FAILED, CANCELLED -> PhantomStepResult.of(PhantomStepResult.Type.FAIL_GOAL, "raid.participate.failed");
			};
		});
	}

	public interface AssemblyPort
	{
		PhantomRaidAssemblyService.AdvanceResult advance(long leaderProfileId, long goalId, long goalRevision);

		boolean cancel(long leaderProfileId, long goalId, long goalRevision, String reasonKey);
	}

	public interface AttemptPort
	{
		PhantomRaidAttemptService.AdvanceResult advance(long leaderProfileId, long goalId, long goalRevision);

		boolean cancel(long leaderProfileId, long goalId, long goalRevision, String reasonKey);

		PhantomRaidAttemptService.ParticipationStatus participation(long profileId, long goalId, long goalRevision);
	}

	private static PhantomStepResult attemptResult(PhantomRaidAttemptService.AdvanceResult attempt)
	{
		return switch (attempt.status())
		{
			case VICTORY -> PhantomStepResult.of(PhantomStepResult.Type.COMPLETE_GOAL, attempt.reasonKey());
			case ABORTED, WIPED, EXPIRED -> PhantomStepResult.of(PhantomStepResult.Type.FAIL_GOAL, attempt.reasonKey());
			case CANCELLED -> PhantomStepResult.of(PhantomStepResult.Type.CANCELLED, attempt.reasonKey());
			default -> PhantomStepResult.of(PhantomStepResult.Type.REPLAN, attempt.reasonKey());
		};
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
