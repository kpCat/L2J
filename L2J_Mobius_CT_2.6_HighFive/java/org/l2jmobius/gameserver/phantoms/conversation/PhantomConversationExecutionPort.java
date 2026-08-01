/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.conversation;

import java.util.Optional;

import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionModel.ExecutionEntry;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoal;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;

/** Narrow external boundary; conversation owns policy, not gameplay mutations. */
public interface PhantomConversationExecutionPort
{
	public enum ResultStatus
	{
		COMPLETED,
		NOT_FOUND,
		AMBIGUOUS,
		REJECTED,
		STALE,
		IDEMPOTENT
	}

	public record QueryResult(ResultStatus status, String facts)
	{
	}

	public record GoalPreparation(ResultStatus status, PhantomGoal goal)
	{
	}

	public record PendingInvitation(long sequence, int requesterObjectId, int inviteeObjectId, String requesterName, PhantomDomainRef requester)
	{
	}

	public record OutboundResult(ResultStatus status, int deliveries)
	{
	}

	QueryResult query(long profileId, ExecutionEntry entry);

	GoalPreparation prepareGoal(long profileId, ExecutionEntry entry, long goalId, long nowMinute);

	Optional<PendingInvitation> pendingInvitation(long profileId);

	ResultStatus respondToPending(long profileId, PendingInvitation invitation, boolean accept, String planId);

	OutboundResult dispatch(long profileId, ExecutionEntry entry);
}
