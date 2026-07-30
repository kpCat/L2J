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
	enum DeliveryOutcome
	{
		ACCEPTED,
		BACKPRESSURE,
		STOPPING
	}

	OptionalLong managedIdentity(int characterObjectId);

	DeliveryOutcome deliver(PartyInvitation invitation, long managedIdentity);

	default void cancelled(PartyInvitation invitation, long managedIdentity, String reasonKey)
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
