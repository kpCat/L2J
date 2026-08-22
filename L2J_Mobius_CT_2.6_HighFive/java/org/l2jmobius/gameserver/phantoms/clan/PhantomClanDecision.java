/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.clan;

import java.util.List;
import java.util.Set;

import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState;
import org.l2jmobius.gameserver.phantoms.clan.PhantomClanService.AdvanceResult;
import org.l2jmobius.gameserver.phantoms.clan.PhantomClanService.OperationStatus;
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

/** Explicit clan goal adapters; candidate discovery remains goal-bounded. */
public final class PhantomClanDecision
{
	public static final String BUILD_CANDIDATE = "candidate.clan.build";
	public static final String JOIN_CANDIDATE = "candidate.clan.join";
	public static final String ROLE_CANDIDATE = "candidate.clan.role";
	public static final String CONTRIBUTE_CANDIDATE = "candidate.clan.contribute";
	public static final String CHAT_CANDIDATE = "candidate.clan.chat";
	public static final String ALLIANCE_CREATE_CANDIDATE = "candidate.clan.alliance.create";
	public static final String ALLIANCE_JOIN_CANDIDATE = "candidate.clan.alliance.join";
	public static final String ALLIANCE_LEAVE_CANDIDATE = "candidate.clan.alliance.leave";
	public static final String ALLIANCE_DISSOLVE_CANDIDATE = "candidate.clan.alliance.dissolve";
	public static final String WAR_DECLARE_CANDIDATE = "candidate.clan.war.declare";
	public static final String WAR_STOP_CANDIDATE = "candidate.clan.war.stop";
	public static final String WAR_PEACE_CANDIDATE = "candidate.clan.war.peace";
	public static final String ALLIANCE_CHAT_CANDIDATE = "candidate.clan.alliance.chat";
	public static final String BUILD_ACTION = "clan.build.advance";
	public static final String JOIN_ACTION = "clan.join.advance";
	public static final String ROLE_ACTION = "clan.role.advance";
	public static final String CONTRIBUTE_ACTION = "clan.contribute.advance";
	public static final String CHAT_ACTION = "clan.chat.advance";
	public static final String ALLIANCE_CREATE_ACTION = "clan.alliance.create.advance";
	public static final String ALLIANCE_JOIN_ACTION = "clan.alliance.join.advance";
	public static final String ALLIANCE_LEAVE_ACTION = "clan.alliance.leave.advance";
	public static final String ALLIANCE_DISSOLVE_ACTION = "clan.alliance.dissolve.advance";
	public static final String WAR_DECLARE_ACTION = "clan.war.declare.advance";
	public static final String WAR_STOP_ACTION = "clan.war.stop.advance";
	public static final String WAR_PEACE_ACTION = "clan.war.peace.advance";
	public static final String ALLIANCE_CHAT_ACTION = "clan.alliance.chat.advance";
	private static final long TIMEOUT_MILLIS = 30_000;
	private final PhantomClanService _service;

	public PhantomClanDecision(PhantomClanService service)
	{
		_service = service;
	}

	public void registerCandidates(PhantomCandidateRegistry registry)
	{
		registry.register(candidate(BUILD_CANDIDATE, PhantomClanService.BUILD_GOAL, BUILD_ACTION));
		registry.register(candidate(JOIN_CANDIDATE, PhantomClanService.JOIN_GOAL, JOIN_ACTION));
		registry.register(candidate(ROLE_CANDIDATE, PhantomClanService.ROLE_GOAL, ROLE_ACTION));
		registry.register(candidate(CONTRIBUTE_CANDIDATE, PhantomClanService.CONTRIBUTE_GOAL, CONTRIBUTE_ACTION));
		registry.register(candidate(CHAT_CANDIDATE, PhantomClanService.CHAT_GOAL, CHAT_ACTION));
		registry.register(candidate(ALLIANCE_CREATE_CANDIDATE, PhantomClanService.ALLIANCE_CREATE_GOAL, ALLIANCE_CREATE_ACTION));
		registry.register(candidate(ALLIANCE_JOIN_CANDIDATE, PhantomClanService.ALLIANCE_JOIN_GOAL, ALLIANCE_JOIN_ACTION));
		registry.register(candidate(ALLIANCE_LEAVE_CANDIDATE, PhantomClanService.ALLIANCE_LEAVE_GOAL, ALLIANCE_LEAVE_ACTION));
		registry.register(candidate(ALLIANCE_DISSOLVE_CANDIDATE, PhantomClanService.ALLIANCE_DISSOLVE_GOAL, ALLIANCE_DISSOLVE_ACTION));
		registry.register(candidate(WAR_DECLARE_CANDIDATE, PhantomClanService.WAR_DECLARE_GOAL, WAR_DECLARE_ACTION));
		registry.register(candidate(WAR_STOP_CANDIDATE, PhantomClanService.WAR_STOP_GOAL, WAR_STOP_ACTION));
		registry.register(candidate(WAR_PEACE_CANDIDATE, PhantomClanService.WAR_PEACE_GOAL, WAR_PEACE_ACTION));
		registry.register(candidate(ALLIANCE_CHAT_CANDIDATE, PhantomClanService.ALLIANCE_CHAT_GOAL, ALLIANCE_CHAT_ACTION));
	}

	public void registerHandlers(PhantomStepHandlerRegistry registry)
	{
		registry.register(BUILD_ACTION, this::execute);
		registry.register(JOIN_ACTION, this::execute);
		registry.register(ROLE_ACTION, this::execute);
		registry.register(CONTRIBUTE_ACTION, this::execute);
		registry.register(CHAT_ACTION, this::execute);
		registry.register(ALLIANCE_CREATE_ACTION, this::execute);
		registry.register(ALLIANCE_JOIN_ACTION, this::execute);
		registry.register(ALLIANCE_LEAVE_ACTION, this::execute);
		registry.register(ALLIANCE_DISSOLVE_ACTION, this::execute);
		registry.register(WAR_DECLARE_ACTION, this::execute);
		registry.register(WAR_STOP_ACTION, this::execute);
		registry.register(WAR_PEACE_ACTION, this::execute);
		registry.register(ALLIANCE_CHAT_ACTION, this::execute);
	}

	private PhantomStepResult execute(PhantomStepContext context)
	{
		if (context.cancellationToken().isCancelled())
		{
			_service.cancel(context.profileId(), context.goal().goalId(), context.goal().revision(), "clan.decision.cancelled");
			return PhantomStepResult.of(PhantomStepResult.Type.CANCELLED, "clan.decision.cancelled");
		}
		final AdvanceResult result = _service.advance(context.profileId(), context.goal().goalId(), context.goal().revision());
		return switch (result.status())
		{
			case COMPLETE -> PhantomStepResult.of(PhantomStepResult.Type.COMPLETE_GOAL, result.reasonKey());
			case WAITING, REPLAN, STALE -> PhantomStepResult.of(PhantomStepResult.Type.REPLAN, result.reasonKey());
			case CANCELLED -> PhantomStepResult.of(PhantomStepResult.Type.CANCELLED, result.reasonKey());
			case FAILED, EXPIRED, UNSUPPORTED -> PhantomStepResult.of(PhantomStepResult.Type.FAIL_GOAL, result.reasonKey());
		};
	}

	private static PhantomDecisionCandidate candidate(String candidateKey, String goalType, String actionKey)
	{
		return new PhantomDecisionCandidate(candidateKey, Set.of(goalType), Set.of(PhantomActivityState.ACTIVE), List.of(), List.of(new PhantomWeightedConsideration("score." + actionKey, 1, context -> new PhantomConsideration.Evaluation(1000, "clan.goal.explicit"))), 1000, context -> plan(context, candidateKey, actionKey));
	}

	private static PhantomPlan plan(PhantomPlanningContext context, String candidateKey, String actionKey)
	{
		final PhantomPlanStep step = new PhantomPlanStep(0, actionKey, context.goal().target(), context.goal().constraints(), TIMEOUT_MILLIS, 1, actionKey + ".explicit");
		return new PhantomPlan(context.decisionSequence(), context.goal().goalId(), candidateKey, List.of(step), TIMEOUT_MILLIS, context.logicalNowNanos());
	}
}
