/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.conversation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

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
		IDEMPOTENT,
		UNCERTAIN
	}

	public record QueryFact(String key, PhantomDomainRef reference, Long number, String value, String authorityKey) implements Comparable<QueryFact>
	{
		public QueryFact
		{
			key = PhantomConversationExecutionModel.requireKey(key, "Query fact key");
			authorityKey = PhantomConversationExecutionModel.requireKey(authorityKey, "Query fact authority");
			final int values = (reference == null ? 0 : 1) + (number == null ? 0 : 1) + (value == null ? 0 : 1);
			if (values != 1)
			{
				throw new IllegalArgumentException("Query fact must contain exactly one value.");
			}
			if (value != null)
			{
				value = PhantomConversationExecutionModel.requireUtf8(value, 64, "Query fact value");
			}
		}

		@Override
		public int compareTo(QueryFact other)
		{
			return key.compareTo(other.key);
		}
	}

	public record QueryResult(ResultStatus status, List<QueryFact> facts)
	{
		public QueryResult
		{
			Objects.requireNonNull(status);
			final List<QueryFact> ordered = new ArrayList<>(Objects.requireNonNull(facts));
			if (ordered.size() > 8)
			{
				throw new IllegalArgumentException("Query result exceeds eight facts.");
			}
			ordered.sort(null);
			final Set<String> keys = new HashSet<>();
			if (!ordered.stream().map(QueryFact::key).allMatch(keys::add))
			{
				throw new IllegalArgumentException("Query result contains duplicate fact keys.");
			}
			facts = List.copyOf(ordered);
		}
	}

	public record GoalPreparation(ResultStatus status, PhantomGoal goal)
	{
	}

	public record PendingInvitation(long sequence, int requesterObjectId, int inviteeObjectId, String requesterName, PhantomDomainRef requester)
	{
	}

	public record OutboundResult(ResultStatus status, int deliveries, boolean expectedCounterpartDelivered)
	{
		public OutboundResult
		{
			Objects.requireNonNull(status);
			if (deliveries < 0)
			{
				throw new IllegalArgumentException("Outbound delivery count is invalid.");
			}
		}

		public OutboundResult(ResultStatus status, int deliveries)
		{
			this(status, deliveries, deliveries > 0);
		}
	}

	QueryResult query(long profileId, ExecutionEntry entry);

	GoalPreparation prepareGoal(long profileId, ExecutionEntry entry, long goalId, long nowMinute);

	default boolean allowsGoalSupersession(long profileId, ExecutionEntry entry, PhantomGoal previousGoal)
	{
		return false;
	}

	Optional<PendingInvitation> pendingInvitation(long profileId);

	ResultStatus respondToPending(long profileId, PendingInvitation invitation, boolean accept, String planId);

	default ResultStatus reconcileInvitation(long profileId, ExecutionEntry entry)
	{
		return ResultStatus.UNCERTAIN;
	}

	OutboundResult dispatch(long profileId, ExecutionEntry entry);
}
