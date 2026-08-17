/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.party;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

import org.l2jmobius.gameserver.model.groups.CommandChannelInvitationService;
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
	int MAX_FORCE_PARTIES = 16;
	int MAX_FORCE_MEMBERS = 144;

	OptionalLong managedProfileId(int characterObjectId);

	Optional<MemberRef> currentMember(long profileId);

	InviteResult invite(MemberRef requester, MemberRef target, PartyDistributionType distribution);

	RespondResult respond(MemberRef invitee, Response response, InvitationIdentity identity);

	default CommandChannelInvitationService.InviteResult inviteCommandChannel(MemberRef requester, MemberRef target)
	{
		return new CommandChannelInvitationService.InviteResult(CommandChannelInvitationService.InviteOutcome.UNSUPPORTED, null);
	}

	default CommandChannelInvitationService.RespondResult respondCommandChannel(MemberRef invitee, CommandChannelInvitationService.Response response, CommandChannelInvitationService.InvitationIdentity identity)
	{
		return new CommandChannelInvitationService.RespondResult(CommandChannelInvitationService.RespondOutcome.UNSUPPORTED, identity, false);
	}

	default CommandChannelInvitationService.DismissOutcome dismissCommandChannel(MemberRef requester, MemberRef target)
	{
		return CommandChannelInvitationService.DismissOutcome.UNSUPPORTED;
	}

	default Optional<CommandChannelInvitationService.InvitationSnapshot> observeCommandChannelInvitation(MemberRef invitee)
	{
		return Optional.empty();
	}

	MembershipOutcome leave(MemberRef member);

	MembershipOutcome expel(MemberRef requester, MemberRef member);

	MembershipOutcome transferLeader(MemberRef requester, MemberRef member);

	Optional<PartySnapshot> observe(MemberRef member);

	Optional<MemberSnapshot> memberSnapshot(MemberRef member);

	default CurrentForceObservation currentForce(MemberRef actor)
	{
		return CurrentForceObservation.unavailable("party.current_force.unsupported");
	}

	List<org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberCapability> capabilities(MemberRef actor, int exactTargetObjectId);

	default List<PvpProtection> pvpProtection(MemberRef helper, int limit)
	{
		return List.of();
	}

	boolean materialize(long profileId);

	record PvpProtection(MemberRef protectedMember, int attackerObjectId)
	{
		public PvpProtection
		{
			if ((protectedMember == null) || (attackerObjectId <= 0))
			{
				throw new IllegalArgumentException("Invalid exact Party PvP protection evidence.");
			}
		}
	}

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

	enum CurrentForceStatus
	{
		AVAILABLE,
		PARTY_ABSENT,
		UNAVAILABLE,
		BOUNDS_EXCEEDED
	}

	record CurrentForceObservation(CurrentForceStatus status, CurrentForceSnapshot snapshot, String reason)
	{
		public CurrentForceObservation
		{
			Objects.requireNonNull(status, "status");
			if ((reason == null) || reason.isBlank() || ((status == CurrentForceStatus.AVAILABLE) != (snapshot != null)))
			{
				throw new IllegalArgumentException("Invalid current force observation.");
			}
		}

		public static CurrentForceObservation available(CurrentForceSnapshot snapshot)
		{
			return new CurrentForceObservation(CurrentForceStatus.AVAILABLE, Objects.requireNonNull(snapshot), "party.current_force.available");
		}

		public static CurrentForceObservation partyAbsent()
		{
			return new CurrentForceObservation(CurrentForceStatus.PARTY_ABSENT, null, "party.current_force.party_absent");
		}

		public static CurrentForceObservation unavailable(String reason)
		{
			return new CurrentForceObservation(CurrentForceStatus.UNAVAILABLE, null, reason);
		}

		public static CurrentForceObservation boundsExceeded()
		{
			return new CurrentForceObservation(CurrentForceStatus.BOUNDS_EXCEEDED, null, "party.current_force.bounds_exceeded");
		}
	}

	record CurrentForceSnapshot(MemberRef actor, MemberRef partyLeader, String commandChannelIdentity, MemberRef commandChannelLeader, int commandChannelLevel, int totalMemberCount, List<PartySnapshot> parties, List<MemberSnapshot> members)
	{
		public CurrentForceSnapshot
		{
			Objects.requireNonNull(actor, "actor");
			Objects.requireNonNull(partyLeader, "partyLeader");
			commandChannelIdentity = Objects.requireNonNull(commandChannelIdentity, "commandChannelIdentity");
			if ((commandChannelLevel < 0) || (totalMemberCount < 1) || (parties == null) || parties.isEmpty() || (parties.size() > MAX_FORCE_PARTIES) || (members == null) || members.isEmpty() || (members.size() > MAX_FORCE_MEMBERS) || (totalMemberCount != members.size()) || ((commandChannelLeader == null) != commandChannelIdentity.isEmpty()) || ((commandChannelLeader == null) != (commandChannelLevel == 0)))
			{
				throw new IllegalArgumentException("Invalid current force snapshot.");
			}
			parties = parties.stream().sorted(Comparator.comparing(party -> party.leader().stableKey())).toList();
			members = members.stream().sorted(Comparator.comparing(member -> member.ref().stableKey())).toList();
			final Set<MemberRef> memberRefs = new HashSet<>();
			for (MemberSnapshot member : members)
			{
				if (!memberRefs.add(member.ref()))
				{
					throw new IllegalArgumentException("Duplicate member in current force snapshot.");
				}
			}
			final Set<MemberRef> partyMemberRefs = new HashSet<>();
			for (PartySnapshot party : parties)
			{
				partyMemberRefs.addAll(party.members());
			}
			if (!memberRefs.equals(partyMemberRefs) || !memberRefs.contains(actor) || !memberRefs.contains(partyLeader) || ((commandChannelLeader != null) && !memberRefs.contains(commandChannelLeader)))
			{
				throw new IllegalArgumentException("Current force identities are inconsistent.");
			}
		}

		public boolean commandChannelPresent()
		{
			return commandChannelLeader != null;
		}
	}
}
