/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.model.groups;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.network.SystemMessageId;
import org.l2jmobius.gameserver.network.serverpackets.ExAskJoinMPCC;
import org.l2jmobius.gameserver.network.serverpackets.SystemMessage;
import org.l2jmobius.gameserver.taskmanagers.GameTimeTaskManager;

/**
 * Canonical transport-neutral CommandChannel invitation and lifecycle facade.
 */
public final class CommandChannelInvitationService
{
	public enum InviteOutcome
	{
		UNSUPPORTED,
		DELIVERED,
		TARGET_NOT_FOUND,
		REQUESTER_NOT_IN_PARTY,
		REQUESTER_NOT_PARTY_LEADER,
		REQUESTER_NOT_COMMAND_CHANNEL_LEADER,
		TARGET_NOT_IN_PARTY,
		TARGET_NOT_PARTY_LEADER,
		SAME_PARTY,
		TARGET_ALREADY_IN_COMMAND_CHANNEL,
		FORMATION_AUTHORITY_REQUIRED,
		REQUESTER_BUSY,
		TARGET_BUSY
	}

	public enum Response
	{
		REFUSE(0),
		ACCEPT(1);

		private final int _clientValue;

		Response(int clientValue)
		{
			_clientValue = clientValue;
		}

		public static Optional<Response> fromClientValue(int value)
		{
			for (Response response : values())
			{
				if (response._clientValue == value)
				{
					return Optional.of(response);
				}
			}
			return Optional.empty();
		}
	}

	public enum RespondOutcome
	{
		UNSUPPORTED,
		ACCEPTED,
		REFUSED,
		INVALID_RESPONSE,
		NO_PENDING_INVITE,
		STALE_INVITE,
		EXPIRED,
		REQUESTER_UNAVAILABLE,
		REVALIDATION_FAILED
	}

	public enum DismissOutcome
	{
		UNSUPPORTED,
		COMPLETED,
		TARGET_NOT_FOUND,
		REQUESTER_NOT_IN_PARTY,
		REQUESTER_NOT_COMMAND_CHANNEL_LEADER,
		TARGET_NOT_IN_PARTY,
		TARGET_NOT_PARTY_LEADER,
		OWN_PARTY,
		DIFFERENT_COMMAND_CHANNEL
	}

	public enum CancelOutcome
	{
		UNSUPPORTED,
		CANCELLED,
		NO_PENDING_INVITE,
		STALE_INVITE
	}

	public record InvitationIdentity(long sequence, int requesterObjectId, int inviteeObjectId)
	{
		public InvitationIdentity
		{
			if ((sequence <= 0) || (requesterObjectId <= 0) || (inviteeObjectId <= 0))
			{
				throw new IllegalArgumentException("Invalid CommandChannel invitation identity.");
			}
		}
	}

	public record InviteResult(InviteOutcome outcome, InvitationIdentity identity)
	{
		public boolean delivered()
		{
			return outcome == InviteOutcome.DELIVERED;
		}
	}

	public record RespondResult(RespondOutcome outcome, InvitationIdentity identity, boolean createdCommandChannel)
	{
		public boolean accepted()
		{
			return outcome == RespondOutcome.ACCEPTED;
		}
	}

	public record CancelResult(CancelOutcome outcome, InvitationIdentity identity)
	{
		public boolean cancelled()
		{
			return outcome == CancelOutcome.CANCELLED;
		}
	}

	public record InvitationSnapshot(InvitationIdentity identity, int requesterPartyLeaderObjectId, int inviteePartyLeaderObjectId, int commandChannelLeaderObjectId, long expiresAtGameTick)
	{
	}

	private static final CommandChannelInvitationService INSTANCE = new CommandChannelInvitationService();

	private final Object _stateLock = new Object();
	private final Map<Integer, PendingInvitation> _pendingByInvitee = new HashMap<>();
	private final Map<Integer, PendingInvitation> _pendingByRequester = new HashMap<>();
	private long _nextSequence;

	private CommandChannelInvitationService()
	{
	}

	public static CommandChannelInvitationService getInstance()
	{
		return INSTANCE;
	}

	public InviteResult invite(Player requester, Player target)
	{
		if (requester == null)
		{
			return new InviteResult(InviteOutcome.TARGET_NOT_FOUND, null);
		}
		expireKnownInvitation(requester.getObjectId());
		if (target == null)
		{
			return new InviteResult(InviteOutcome.TARGET_NOT_FOUND, null);
		}
		expireKnownInvitation(target.getObjectId());

		final Party targetParty = target.getParty();
		final Player invitee = targetParty == null ? null : targetParty.getLeader();
		if (invitee != null)
		{
			expireKnownInvitation(invitee.getObjectId());
		}

		final PendingInvitation pending;
		synchronized (_stateLock)
		{
			final InviteOutcome validation = validateInvite(requester, target, targetParty, invitee, true);
			if (validation != null)
			{
				return new InviteResult(validation, null);
			}
			if (_pendingByRequester.containsKey(requester.getObjectId()) || requester.isProcessingRequest())
			{
				requester.sendPacket(SystemMessageId.WAITING_FOR_ANOTHER_REPLY);
				return new InviteResult(InviteOutcome.REQUESTER_BUSY, null);
			}
			if (_pendingByInvitee.containsKey(invitee.getObjectId()) || invitee.isProcessingRequest())
			{
				final SystemMessage message = new SystemMessage(SystemMessageId.C1_IS_ON_ANOTHER_TASK_PLEASE_TRY_AGAIN_LATER);
				message.addString(invitee.getName());
				requester.sendPacket(message);
				return new InviteResult(InviteOutcome.TARGET_BUSY, null);
			}

			final Party requesterParty = requester.getParty();
			final CommandChannel requesterChannel = requesterParty.getCommandChannel();
			final InvitationIdentity identity = new InvitationIdentity(++_nextSequence, requester.getObjectId(), invitee.getObjectId());
			final long expiresAt = currentGameTick() + ((long) Player.REQUEST_TIMEOUT * GameTimeTaskManager.TICKS_PER_SECOND);
			pending = new PendingInvitation(identity, requester, invitee, requesterParty, targetParty, requesterChannel, expiresAt);
			_pendingByRequester.put(requester.getObjectId(), pending);
			_pendingByInvitee.put(invitee.getObjectId(), pending);
			requester.onTransactionRequest(invitee);
		}

		final SystemMessage message = new SystemMessage(SystemMessageId.C1_IS_INVITING_YOU_TO_A_COMMAND_CHANNEL_DO_YOU_ACCEPT);
		message.addString(requester.getName());
		invitee.sendPacket(message);
		invitee.sendPacket(new ExAskJoinMPCC(requester.getName()));
		requester.sendMessage("You invited " + invitee.getName() + " to your Command Channel.");
		return new InviteResult(InviteOutcome.DELIVERED, pending._identity);
	}

	public RespondResult respond(Player invitee, int clientResponse, InvitationIdentity expectedIdentity)
	{
		final Optional<Response> response = Response.fromClientValue(clientResponse);
		if (response.isEmpty())
		{
			return new RespondResult(RespondOutcome.INVALID_RESPONSE, expectedIdentity, false);
		}
		return respond(invitee, response.orElseThrow(), expectedIdentity);
	}

	public RespondResult respond(Player invitee, Response response, InvitationIdentity expectedIdentity)
	{
		Objects.requireNonNull(response, "CommandChannel invitation response must not be null.");
		if (invitee == null)
		{
			return new RespondResult(RespondOutcome.NO_PENDING_INVITE, expectedIdentity, false);
		}

		synchronized (_stateLock)
		{
			final PendingInvitation pending = _pendingByInvitee.get(invitee.getObjectId());
			if (pending == null)
			{
				return new RespondResult(RespondOutcome.NO_PENDING_INVITE, expectedIdentity, false);
			}
			if ((expectedIdentity == null) || !pending._identity.equals(expectedIdentity))
			{
				return new RespondResult(RespondOutcome.STALE_INVITE, expectedIdentity, false);
			}
			removePending(pending);
			if (expired(pending))
			{
				clearRequestRelation(pending);
				return new RespondResult(RespondOutcome.EXPIRED, pending._identity, false);
			}
			if ((invitee.getActiveRequester() != pending._requester) || pending._requester.isRequestExpired())
			{
				clearRequestRelation(pending);
				return new RespondResult(RespondOutcome.REQUESTER_UNAVAILABLE, pending._identity, false);
			}
			if (response == Response.REFUSE)
			{
				clearRequestRelation(pending);
				pending._requester.sendMessage("The player declined to join your Command Channel.");
				return new RespondResult(RespondOutcome.REFUSED, pending._identity, false);
			}

			if (!revalidateAcceptance(pending))
			{
				clearRequestRelation(pending);
				return new RespondResult(RespondOutcome.REVALIDATION_FAILED, pending._identity, false);
			}

			clearRequestRelation(pending);
			boolean created = false;
			CommandChannel channel = pending._requesterChannel;
			if (channel == null)
			{
				channel = new CommandChannel(pending._requester);
				pending._requester.sendPacket(SystemMessageId.THE_COMMAND_CHANNEL_HAS_BEEN_FORMED);
				created = true;
			}
			channel.addParty(pending._inviteeParty);
			if (!created)
			{
				invitee.sendPacket(SystemMessageId.YOU_HAVE_JOINED_THE_COMMAND_CHANNEL);
			}
			return new RespondResult(RespondOutcome.ACCEPTED, pending._identity, created);
		}
	}

	public Optional<InvitationSnapshot> observe(Player invitee)
	{
		if (invitee == null)
		{
			return Optional.empty();
		}
		synchronized (_stateLock)
		{
			final PendingInvitation pending = _pendingByInvitee.get(invitee.getObjectId());
			if (pending == null)
			{
				return Optional.empty();
			}
			if (expired(pending))
			{
				removePending(pending);
				clearRequestRelation(pending);
				return Optional.empty();
			}
			return Optional.of(pending.snapshot());
		}
	}

	/**
	 * Removes only the exact pending identity. This is a cleanup seam; it does not
	 * accept or refuse on behalf of either Party.
	 */
	public CancelResult cancel(InvitationIdentity expectedIdentity)
	{
		if (expectedIdentity == null)
		{
			return new CancelResult(CancelOutcome.NO_PENDING_INVITE, null);
		}
		synchronized (_stateLock)
		{
			final PendingInvitation pending = _pendingByInvitee.get(expectedIdentity.inviteeObjectId());
			if (pending == null)
			{
				return new CancelResult(CancelOutcome.NO_PENDING_INVITE, expectedIdentity);
			}
			if (!pending._identity.equals(expectedIdentity))
			{
				return new CancelResult(CancelOutcome.STALE_INVITE, expectedIdentity);
			}
			removePending(pending);
			clearRequestRelation(pending);
			return new CancelResult(CancelOutcome.CANCELLED, pending._identity);
		}
	}

	public DismissOutcome dismiss(Player requester, Player target)
	{
		if ((requester == null) || (target == null))
		{
			return dismissFailure(requester, DismissOutcome.TARGET_NOT_FOUND);
		}
		final Party requesterParty = requester.getParty();
		if (requesterParty == null)
		{
			return dismissFailure(requester, DismissOutcome.REQUESTER_NOT_IN_PARTY);
		}
		final CommandChannel channel = requesterParty.getCommandChannel();
		if ((channel == null) || (channel.getLeader() != requester))
		{
			return dismissFailure(requester, DismissOutcome.REQUESTER_NOT_COMMAND_CHANNEL_LEADER);
		}
		final Party targetParty = target.getParty();
		if (targetParty == null)
		{
			return dismissFailure(requester, DismissOutcome.TARGET_NOT_IN_PARTY);
		}
		if (targetParty == requesterParty)
		{
			return DismissOutcome.OWN_PARTY;
		}
		if (targetParty.getCommandChannel() != channel)
		{
			return dismissFailure(requester, DismissOutcome.DIFFERENT_COMMAND_CHANNEL);
		}

		final String targetLeaderName = targetParty.getLeader().getName();
		channel.removeParty(targetParty);
		targetParty.broadcastPacket(new SystemMessage(SystemMessageId.YOU_WERE_DISMISSED_FROM_THE_COMMAND_CHANNEL));
		if (requesterParty.getCommandChannel() == channel)
		{
			final SystemMessage message = new SystemMessage(SystemMessageId.C1_S_PARTY_HAS_BEEN_DISMISSED_FROM_THE_COMMAND_CHANNEL);
			message.addString(targetLeaderName);
			channel.broadcastPacket(message);
		}
		return DismissOutcome.COMPLETED;
	}

	private InviteOutcome validateInvite(Player requester, Player target, Party targetParty, Player invitee, boolean notify)
	{
		final Party requesterParty = requester.getParty();
		if (requesterParty == null)
		{
			return InviteOutcome.REQUESTER_NOT_IN_PARTY;
		}
		if (requesterParty.getLeader() != requester)
		{
			if (notify)
			{
				requester.sendPacket(SystemMessageId.YOU_DO_NOT_HAVE_AUTHORITY_TO_INVITE_SOMEONE_TO_THE_COMMAND_CHANNEL);
			}
			return InviteOutcome.REQUESTER_NOT_PARTY_LEADER;
		}
		final CommandChannel channel = requesterParty.getCommandChannel();
		if ((channel != null) && (channel.getLeader() != requester))
		{
			if (notify)
			{
				requester.sendPacket(SystemMessageId.YOU_DO_NOT_HAVE_AUTHORITY_TO_INVITE_SOMEONE_TO_THE_COMMAND_CHANNEL);
			}
			return InviteOutcome.REQUESTER_NOT_COMMAND_CHANNEL_LEADER;
		}
		if (targetParty == null)
		{
			if (notify)
			{
				requester.sendMessage(target.getName() + " doesn't have party and cannot be invited to Command Channel.");
			}
			return InviteOutcome.TARGET_NOT_IN_PARTY;
		}
		if (requesterParty == targetParty)
		{
			return InviteOutcome.SAME_PARTY;
		}
		if (invitee == null)
		{
			return InviteOutcome.TARGET_NOT_PARTY_LEADER;
		}
		if (targetParty.getCommandChannel() != null)
		{
			if (notify)
			{
				final SystemMessage message = new SystemMessage(SystemMessageId.C1_S_PARTY_IS_ALREADY_A_MEMBER_OF_THE_COMMAND_CHANNEL);
				message.addString(target.getName());
				requester.sendPacket(message);
			}
			return InviteOutcome.TARGET_ALREADY_IN_COMMAND_CHANNEL;
		}
		if (!hasFormationAuthority(requester))
		{
			if (notify)
			{
				requester.sendPacket(SystemMessageId.COMMAND_CHANNELS_CAN_ONLY_BE_FORMED_BY_A_PARTY_LEADER_WHO_IS_ALSO_THE_LEADER_OF_A_LEVEL_5_CLAN);
			}
			return InviteOutcome.FORMATION_AUTHORITY_REQUIRED;
		}
		return null;
	}

	private boolean revalidateAcceptance(PendingInvitation pending)
	{
		final Player requester = pending._requester;
		final Player invitee = pending._invitee;
		if ((requester.getObjectId() != pending._identity.requesterObjectId()) || (invitee.getObjectId() != pending._identity.inviteeObjectId()))
		{
			return false;
		}
		if ((requester.getParty() != pending._requesterParty) || (pending._requesterParty.getLeader() != requester) || !hasFormationAuthority(requester))
		{
			return false;
		}
		if ((invitee.getParty() != pending._inviteeParty) || (pending._inviteeParty.getLeader() != invitee) || (pending._requesterParty == pending._inviteeParty) || pending._inviteeParty.isInCommandChannel())
		{
			return false;
		}
		if (pending._requesterChannel == null)
		{
			return !pending._requesterParty.isInCommandChannel();
		}
		return (pending._requesterParty.getCommandChannel() == pending._requesterChannel) && (pending._requesterChannel.getLeader() == requester);
	}

	private static boolean hasFormationAuthority(Player requester)
	{
		return (requester.isClanLeader() && (requester.getClan() != null) && (requester.getClan().getLevel() >= 5)) || (requester.getInventory().getItemByItemId(8871) != null) || ((requester.getPledgeClass() >= 5) && (requester.getKnownSkill(391) != null));
	}

	private void expireKnownInvitation(int playerObjectId)
	{
		synchronized (_stateLock)
		{
			final PendingInvitation pending = _pendingByInvitee.containsKey(playerObjectId) ? _pendingByInvitee.get(playerObjectId) : _pendingByRequester.get(playerObjectId);
			if ((pending != null) && expired(pending))
			{
				removePending(pending);
				clearRequestRelation(pending);
			}
		}
	}

	private static boolean expired(PendingInvitation pending)
	{
		return (currentGameTick() >= pending._expiresAtGameTick) || pending._requester.isRequestExpired();
	}

	private static long currentGameTick()
	{
		return GameTimeTaskManager.getInstance().getGameTicks();
	}

	private void removePending(PendingInvitation pending)
	{
		_pendingByInvitee.remove(pending._identity.inviteeObjectId(), pending);
		_pendingByRequester.remove(pending._identity.requesterObjectId(), pending);
	}

	private static void clearRequestRelation(PendingInvitation pending)
	{
		if (pending._invitee.getActiveRequester() == pending._requester)
		{
			pending._invitee.setActiveRequester(null);
			pending._requester.onTransactionResponse();
		}
	}

	private static DismissOutcome dismissFailure(Player requester, DismissOutcome outcome)
	{
		if (requester != null)
		{
			requester.sendPacket(SystemMessageId.YOUR_TARGET_CANNOT_BE_FOUND);
		}
		return outcome;
	}

	private static final class PendingInvitation
	{
		private final InvitationIdentity _identity;
		private final Player _requester;
		private final Player _invitee;
		private final Party _requesterParty;
		private final Party _inviteeParty;
		private final CommandChannel _requesterChannel;
		private final long _expiresAtGameTick;

		private PendingInvitation(InvitationIdentity identity, Player requester, Player invitee, Party requesterParty, Party inviteeParty, CommandChannel requesterChannel, long expiresAtGameTick)
		{
			_identity = identity;
			_requester = requester;
			_invitee = invitee;
			_requesterParty = requesterParty;
			_inviteeParty = inviteeParty;
			_requesterChannel = requesterChannel;
			_expiresAtGameTick = expiresAtGameTick;
		}

		private InvitationSnapshot snapshot()
		{
			return new InvitationSnapshot(_identity, _requesterParty.getLeaderObjectId(), _inviteeParty.getLeaderObjectId(), _requesterChannel == null ? 0 : _requesterChannel.getLeaderObjectId(), _expiresAtGameTick);
		}
	}
}
