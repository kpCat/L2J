/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.party;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.l2jmobius.gameserver.model.groups.PartyDistributionType;
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
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyCoordinator.CommandOutcome;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.ObjectiveMode;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.RoleRequirement;

/**
 * Explicit goal adapters; no candidate discovery or global matchmaking.
 */
public final class PhantomPartyDecision
{
	public static final String FORM_CANDIDATE = "candidate.party.form";
	public static final String JOIN_CANDIDATE = "candidate.party.join";
	public static final String FORM_ACTION = "party.form_invite";
	public static final String JOIN_ACTION = "party.await_join";
	private static final long TIMEOUT_MILLIS = 30_000;
	private final PhantomPartyCoordinator _coordinator;

	public PhantomPartyDecision(PhantomPartyCoordinator coordinator)
	{
		_coordinator = coordinator;
	}

	public void registerCandidates(PhantomCandidateRegistry registry)
	{
		registry.register(new PhantomDecisionCandidate(FORM_CANDIDATE, Set.of(PhantomPartyCoordinator.FORM_GOAL), Set.of(PhantomActivityState.ACTIVE, PhantomActivityState.WARM), List.of(), List.of(new PhantomWeightedConsideration("score.party.form", 1, context -> new PhantomConsideration.Evaluation(1000, "party.goal.explicit"))), 1000, this::formPlan));
		registry.register(new PhantomDecisionCandidate(JOIN_CANDIDATE, Set.of(PhantomPartyCoordinator.JOIN_GOAL), Set.of(PhantomActivityState.ACTIVE, PhantomActivityState.WARM), List.of(), List.of(new PhantomWeightedConsideration("score.party.join", 1, context -> new PhantomConsideration.Evaluation(1000, "party.goal.explicit"))), 1000, this::joinPlan));
	}

	public void registerHandlers(PhantomStepHandlerRegistry registry)
	{
		registry.register(FORM_ACTION, context ->
		{
			if (context.cancellationToken().isCancelled())
			{
				return PhantomStepResult.of(PhantomStepResult.Type.CANCELLED, "party.form.cancelled");
			}
			if (_coordinator.committed(context.profileId()))
			{
				return PhantomStepResult.of(PhantomStepResult.Type.SUCCESS, "party.form.committed");
			}
			final CommandOutcome formed = _coordinator.form(context.profileId(), context.goal().goalId(), context.goal().revision(), objective(context.step().numericArguments()), context.goal().subject() == null ? new PhantomDomainRef("party", "general") : context.goal().subject(), requirements(context.step().numericArguments()));
			if ((formed != CommandOutcome.ACCEPTED) && (formed != CommandOutcome.CLAIM_EXISTS))
			{
				return PhantomStepResult.retry(50, "party.form.retry");
			}
			final CommandOutcome invited = _coordinator.inviteTarget(context.profileId(), context.step().target(), PartyDistributionType.FINDERS_KEEPERS);
			return invited == CommandOutcome.ACCEPTED ? PhantomStepResult.retry(50, "party.invite.await") : PhantomStepResult.of(PhantomStepResult.Type.REPLAN, "party.invite.replan");
		});
		registry.register(JOIN_ACTION, context -> _coordinator.committed(context.profileId()) ? PhantomStepResult.of(PhantomStepResult.Type.SUCCESS, "party.join.committed") : PhantomStepResult.retry(50, "party.join.await"));
	}

	private PhantomPlan formPlan(PhantomPlanningContext context)
	{
		final PhantomPlanStep step = new PhantomPlanStep(0, FORM_ACTION, context.goal().target(), context.goal().constraints(), TIMEOUT_MILLIS, 10, "party.form.explicit");
		return new PhantomPlan(context.decisionSequence(), context.goal().goalId(), FORM_CANDIDATE, List.of(step), TIMEOUT_MILLIS, context.logicalNowNanos());
	}

	private PhantomPlan joinPlan(PhantomPlanningContext context)
	{
		final PhantomPlanStep step = new PhantomPlanStep(0, JOIN_ACTION, context.goal().target(), Map.of(), TIMEOUT_MILLIS, 10, "party.join.explicit");
		return new PhantomPlan(context.decisionSequence(), context.goal().goalId(), JOIN_CANDIDATE, List.of(step), TIMEOUT_MILLIS, context.logicalNowNanos());
	}

	private static ObjectiveMode objective(Map<String, Long> arguments)
	{
		final long ordinal = arguments.getOrDefault("party.objective", 0L);
		return (ordinal >= 0) && (ordinal < ObjectiveMode.values().length) ? ObjectiveMode.values()[(int) ordinal] : ObjectiveMode.GENERAL_PVE;
	}

	private static List<RoleRequirement> requirements(Map<String, Long> arguments)
	{
		final List<RoleRequirement> result = new ArrayList<>();
		for (Map.Entry<String, Long> entry : arguments.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList())
		{
			final String prefix = "party.role.";
			if (!entry.getKey().startsWith(prefix) || (result.size() >= 9))
			{
				continue;
			}
			final String role = entry.getKey().substring(prefix.length());
			final boolean required = entry.getValue() > 0;
			result.add(new RoleRequirement("slot." + result.size(), role, required, (int) Math.max(1, Math.min(10000, Math.abs(entry.getValue())))));
		}
		return result;
	}
}
