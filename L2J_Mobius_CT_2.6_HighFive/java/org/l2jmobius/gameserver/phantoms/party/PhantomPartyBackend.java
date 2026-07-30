/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.party;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

import org.l2jmobius.gameserver.model.groups.PartyDistributionType;
import org.l2jmobius.gameserver.model.groups.PartyInvitationService.InvitationIdentity;
import org.l2jmobius.gameserver.model.groups.PartyInvitationService.InviteResult;
import org.l2jmobius.gameserver.model.groups.PartyInvitationService.MembershipOutcome;
import org.l2jmobius.gameserver.model.groups.PartyInvitationService.RespondResult;
import org.l2jmobius.gameserver.model.groups.PartyInvitationService.Response;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberRef;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberSnapshot;

/**
 * Copies exact live Party/Player state without exposing either mutable object.
 */
public interface PhantomPartyBackend
{
	OptionalLong managedProfileId(int characterObjectId);

	Optional<MemberRef> currentMember(long profileId);

	InviteResult invite(MemberRef requester, MemberRef target, PartyDistributionType distribution);

	RespondResult respond(MemberRef invitee, Response response, InvitationIdentity identity);

	MembershipOutcome leave(MemberRef member);

	MembershipOutcome expel(MemberRef requester, MemberRef member);

	MembershipOutcome transferLeader(MemberRef requester, MemberRef member);

	Optional<PartySnapshot> observe(MemberRef member);

	Optional<MemberSnapshot> memberSnapshot(MemberRef member);

	boolean materialize(long profileId);

	record PartySnapshot(MemberRef leader, List<MemberRef> members, PartyDistributionType distribution)
	{
		public PartySnapshot
		{
			if ((leader == null) || (members == null) || members.isEmpty() || (members.size() > 9) || !members.contains(leader) || (distribution == null))
			{
				throw new IllegalArgumentException("Invalid canonical party snapshot.");
			}
			members = List.copyOf(members);
		}
	}
}
