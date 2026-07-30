/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.model.groups;

import java.util.OptionalLong;

/**
 * Generic delivery boundary for server-managed party invitees.
 */
public interface PartyInvitationDelivery
{
	enum PreparationOutcome
	{
		ACCEPTED,
		REJECTED,
		STOPPING
	}

	enum DeliveryOutcome
	{
		ACCEPTED,
		BACKPRESSURE,
		STOPPING
	}

	enum TerminalOutcome
	{
		ACCEPTED,
		REFUSED,
		DISABLED,
		EXPIRED,
		CANCELLED,
		DELIVERY_REJECTED,
		REVALIDATION_FAILED,
		REQUESTER_UNAVAILABLE
	}

	OptionalLong managedIdentity(int characterObjectId);

	default PreparationOutcome prepare(PartyInvitation invitation, OptionalLong managedRequester, OptionalLong managedInvitee)
	{
		return PreparationOutcome.ACCEPTED;
	}

	DeliveryOutcome deliver(PartyInvitation invitation, long managedIdentity);

	default void terminal(PartyInvitation invitation, OptionalLong managedRequester, OptionalLong managedInvitee, TerminalOutcome outcome, String reasonKey)
	{
	}

	record PartyInvitation(PartyInvitationService.InvitationIdentity identity, int requesterObjectId, String requesterName, int inviteeObjectId, String inviteeName, PartyDistributionType distributionType, int partyLeaderObjectId, long expiresAtGameTick)
	{
		public PartyInvitation
		{
			if ((identity == null) || (requesterObjectId <= 0) || (inviteeObjectId <= 0) || (requesterName == null) || requesterName.isBlank() || (inviteeName == null) || inviteeName.isBlank() || (distributionType == null) || (partyLeaderObjectId < 0) || (expiresAtGameTick < 0))
			{
				throw new IllegalArgumentException("Invalid managed party invitation.");
			}
		}
	}

	static PartyInvitationDelivery noop()
	{
		return new PartyInvitationDelivery()
		{
			@Override
			public OptionalLong managedIdentity(int characterObjectId)
			{
				return OptionalLong.empty();
			}

			@Override
			public DeliveryOutcome deliver(PartyInvitation invitation, long managedIdentity)
			{
				return DeliveryOutcome.STOPPING;
			}
		};
	}
}
