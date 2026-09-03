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
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint;
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
	public static final String LEAVE_CANDIDATE = "candidate.party.leave";
	public static final String EXPEL_CANDIDATE = "candidate.party.expel_member";
	public static final String TRANSFER_LEADER_CANDIDATE = "candidate.party.transfer_leader";
	public static final String TRAVEL_CANDIDATE = "candidate.party.travel";
	public static final String FORM_ACTION = "party.form_invite";
	public static final String JOIN_ACTION = "party.await_join";
	public static final String LEAVE_ACTION = "party.leave";
	public static final String EXPEL_ACTION = "party.expel_member";
	public static final String TRANSFER_LEADER_ACTION = "party.transfer_leader";
	public static final String TRAVEL_ACTION = "party.travel";
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
		registry.register(commandCandidate(LEAVE_CANDIDATE, PhantomPartyCoordinator.LEAVE_GOAL, LEAVE_ACTION));
		registry.register(commandCandidate(EXPEL_CANDIDATE, PhantomPartyCoordinator.EXPEL_GOAL, EXPEL_ACTION));
		registry.register(commandCandidate(TRANSFER_LEADER_CANDIDATE, PhantomPartyCoordinator.TRANSFER_LEADER_GOAL, TRANSFER_LEADER_ACTION));
		registry.register(commandCandidate(TRAVEL_CANDIDATE, PhantomPartyCoordinator.TRAVEL_GOAL, TRAVEL_ACTION));
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
			if ((formed != CommandOutcome.ACCEPTED) && (formed != CommandOutcome.IDEMPOTENT))
			{
				return PhantomStepResult.retry(50, "party.form.retry");
			}
			final CommandOutcome invited = _coordinator.inviteTarget(context.profileId(), context.step().target(), PartyDistributionType.FINDERS_KEEPERS);
			return Set.of(CommandOutcome.ACCEPTED, CommandOutcome.IDEMPOTENT).contains(invited) ? PhantomStepResult.retry(50, "party.invite.await") : PhantomStepResult.of(PhantomStepResult.Type.REPLAN, "party.invite.replan");
		});
		registry.register(JOIN_ACTION, context -> _coordinator.committed(context.profileId()) ? PhantomStepResult.of(PhantomStepResult.Type.SUCCESS, "party.join.committed") : PhantomStepResult.retry(50, "party.join.await"));
		registry.register(LEAVE_ACTION, context ->
		{
			final CommandOutcome outcome = _coordinator.leave(context.profileId(), context.goal().goalId(), context.goal().revision(), generation(context.step().numericArguments()));
			// Canonical leave is the terminal objective, not another successful plan step.
			return Set.of(CommandOutcome.ACCEPTED, CommandOutcome.IDEMPOTENT).contains(outcome) ? PhantomStepResult.of(PhantomStepResult.Type.COMPLETE_GOAL, "party.leave.committed") : commandResult(outcome, "party.leave");
		});
		registry.register(EXPEL_ACTION, context -> commandResult(_coordinator.expelTarget(context.profileId(), context.goal().goalId(), context.goal().revision(), generation(context.step().numericArguments()), context.step().target()), "party.expel_member"));
		registry.register(TRANSFER_LEADER_ACTION, context -> commandResult(_coordinator.transferLeaderTarget(context.profileId(), context.goal().goalId(), context.goal().revision(), generation(context.step().numericArguments()), context.step().target()), "party.transfer_leader"));
		registry.register(TRAVEL_ACTION, context ->
		{
			try
			{
				final Map<String, Long> arguments = context.step().numericArguments();
				final PhantomNavigationPoint destination = new PhantomNavigationPoint(Math.toIntExact(arguments.getOrDefault("party.x", 0L)), Math.toIntExact(arguments.getOrDefault("party.y", 0L)), Math.toIntExact(arguments.getOrDefault("party.z", 0L)), Math.toIntExact(arguments.getOrDefault("party.instance", 0L)));
				return commandResult(_coordinator.travel(context.profileId(), context.goal().goalId(), context.goal().revision(), generation(arguments), context.step().target(), destination), "party.travel");
			}
			catch (IllegalArgumentException e)
			{
				return PhantomStepResult.of(PhantomStepResult.Type.REPLAN, "party.travel.invalid_destination");
			}
		});
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

	private static PhantomDecisionCandidate commandCandidate(String candidateKey, String goalType, String actionKey)
	{
		return new PhantomDecisionCandidate(candidateKey, Set.of(goalType), Set.of(PhantomActivityState.ACTIVE, PhantomActivityState.WARM), List.of(), List.of(new PhantomWeightedConsideration("score." + actionKey, 1, context -> new PhantomConsideration.Evaluation(1000, "party.goal.explicit"))), 1000, context -> commandPlan(context, candidateKey, actionKey));
	}

	private static PhantomPlan commandPlan(PhantomPlanningContext context, String candidateKey, String actionKey)
	{
		final PhantomPlanStep step = new PhantomPlanStep(0, actionKey, context.goal().target(), context.goal().constraints(), TIMEOUT_MILLIS, 1, "party.command.explicit");
		return new PhantomPlan(context.decisionSequence(), context.goal().goalId(), candidateKey, List.of(step), TIMEOUT_MILLIS, context.logicalNowNanos());
	}

	private static long generation(Map<String, Long> arguments)
	{
		return arguments.getOrDefault("party.generation", 0L);
	}

	private static PhantomStepResult commandResult(CommandOutcome outcome, String key)
	{
		if (Set.of(CommandOutcome.ACCEPTED, CommandOutcome.IDEMPOTENT).contains(outcome))
		{
			return PhantomStepResult.of(PhantomStepResult.Type.SUCCESS, key + ".committed");
		}
		if (outcome == CommandOutcome.STALE_GENERATION)
		{
			return PhantomStepResult.of(PhantomStepResult.Type.CANCELLED, key + ".stale_generation");
		}
		return PhantomStepResult.of(PhantomStepResult.Type.REPLAN, key + ".rejected");
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
