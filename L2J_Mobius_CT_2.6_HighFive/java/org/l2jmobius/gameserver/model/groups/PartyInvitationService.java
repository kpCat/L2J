/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.model.groups;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicBoolean;

import org.l2jmobius.gameserver.config.GeneralConfig;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.holders.player.BlockList;
import org.l2jmobius.gameserver.model.groups.PartyInvitationDelivery.DeliveryOutcome;
import org.l2jmobius.gameserver.model.groups.PartyInvitationDelivery.PartyInvitation;
import org.l2jmobius.gameserver.model.groups.matching.PartyMatchRoom;
import org.l2jmobius.gameserver.model.groups.matching.PartyMatchRoomList;
import org.l2jmobius.gameserver.network.SystemMessageId;
import org.l2jmobius.gameserver.network.serverpackets.ActionFailed;
import org.l2jmobius.gameserver.network.serverpackets.AskJoinParty;
import org.l2jmobius.gameserver.network.serverpackets.ExManagePartyRoomMember;
import org.l2jmobius.gameserver.network.serverpackets.JoinParty;
import org.l2jmobius.gameserver.network.serverpackets.SystemMessage;
import org.l2jmobius.gameserver.taskmanagers.GameTimeTaskManager;

/**
 * Canonical transport-neutral party invitation and membership facade.
 */
public final class PartyInvitationService
{
	public enum InviteOutcome
	{
		DELIVERED_CLIENT,
		DELIVERED_MANAGED,
		INVALID_DISTRIBUTION,
		TARGET_NOT_FOUND,
		TARGET_OFFLINE,
		REQUESTER_PARTY_BANNED,
		TARGET_PARTY_BANNED,
		TARGET_NOT_VISIBLE,
		EVENT_RESTRICTED,
		TARGET_ALREADY_IN_PARTY,
		TARGET_BLOCKED_REQUESTER,
		SELF_INVITE,
		CURSED_WEAPON,
		JAIL,
		OLYMPIAD,
		REQUESTER_BUSY,
		TARGET_BUSY,
		NOT_LEADER,
		PARTY_FULL,
		PARTY_PENDING,
		DIMENSIONAL_RIFT,
		REVALIDATION_FAILED,
		MANAGED_BACKPRESSURE
	}

	public enum Response
	{
		DISABLED(-1),
		REFUSE(0),
		ACCEPT(1);

		private final int _clientValue;

		Response(int clientValue)
		{
			_clientValue = clientValue;
		}

		public int clientValue()
		{
			return _clientValue;
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
		ACCEPTED,
		REFUSED,
		DISABLED,
		INVALID_RESPONSE,
		NO_PENDING_INVITE,
		STALE_INVITE,
		EXPIRED,
		REQUESTER_UNAVAILABLE,
		REVALIDATION_FAILED,
		PARTY_FULL
	}

	public enum MembershipOutcome
	{
		COMPLETED,
		NOT_IN_PARTY,
		NOT_LEADER,
		TARGET_NOT_MEMBER,
		INVALID_TARGET
	}

	public record InvitationIdentity(long sequence, int requesterObjectId, int inviteeObjectId)
	{
		public InvitationIdentity
		{
			if ((sequence <= 0) || (requesterObjectId <= 0) || (inviteeObjectId <= 0))
			{
				throw new IllegalArgumentException("Invalid party invitation identity.");
			}
		}
	}

	public record InviteResult(InviteOutcome outcome, InvitationIdentity identity)
	{
		public boolean delivered()
		{
			return (outcome == InviteOutcome.DELIVERED_CLIENT) || (outcome == InviteOutcome.DELIVERED_MANAGED);
		}
	}

	public record RespondResult(RespondOutcome outcome, InvitationIdentity identity, Party party)
	{
		public boolean accepted()
		{
			return outcome == RespondOutcome.ACCEPTED;
		}
	}

	public record InvitationSnapshot(InvitationIdentity identity, int requesterObjectId, int inviteeObjectId, PartyDistributionType distributionType, int partyLeaderObjectId, long expiresAtGameTick, boolean managed)
	{
	}

	private static final PartyInvitationService INSTANCE = new PartyInvitationService();

	private final Object _stateLock = new Object();
	private final Map<Integer, PendingInvitation> _pendingByInvitee = new HashMap<>();
	private final Map<Integer, PendingInvitation> _pendingByRequester = new HashMap<>();
	private volatile PartyInvitationDelivery _managedDelivery = NoopDelivery.INSTANCE;
	private long _nextSequence;

	private PartyInvitationService()
	{
	}

	public static PartyInvitationService getInstance()
	{
		return INSTANCE;
	}

	public DeliveryRegistration installManagedDelivery(PartyInvitationDelivery delivery)
	{
		Objects.requireNonNull(delivery, "Managed party invitation delivery must not be null.");
		synchronized (_stateLock)
		{
			if (_managedDelivery != NoopDelivery.INSTANCE)
			{
				throw new IllegalStateException("Managed party invitation delivery is already installed.");
			}
			_managedDelivery = delivery;
		}
		return new DeliveryRegistration(this, delivery);
	}

	public InviteResult invite(Player requester, Player target, int requestedDistributionTypeId)
	{
		if (requester == null)
		{
			return new InviteResult(InviteOutcome.TARGET_NOT_FOUND, null);
		}
		expireKnownInvitation(requester.getObjectId());
		if (target == null)
		{
			requester.sendPacket(SystemMessageId.YOU_MUST_FIRST_SELECT_A_USER_TO_INVITE_TO_YOUR_PARTY);
			return new InviteResult(InviteOutcome.TARGET_NOT_FOUND, null);
		}
		expireKnownInvitation(target.getObjectId());
		final PartyInvitationDelivery deliveryPort = _managedDelivery;
		final OptionalLong managedIdentity = deliveryPort.managedIdentity(target.getObjectId());
		final InviteOutcome commonFailure = validateInvite(requester, target, managedIdentity.isPresent());
		if (commonFailure != null)
		{
			return new InviteResult(commonFailure, null);
		}

		final Party currentParty = requester.getParty();
		final PartyDistributionType distributionType;
		if (currentParty == null)
		{
			distributionType = PartyDistributionType.findById(requestedDistributionTypeId);
			if (distributionType == null)
			{
				return new InviteResult(InviteOutcome.INVALID_DISTRIBUTION, null);
			}
		}
		else
		{
			if (currentParty.isInDimensionalRift())
			{
				requester.sendMessage("You cannot invite a player when you are in the Dimensional Rift.");
				return new InviteResult(InviteOutcome.DIMENSIONAL_RIFT, null);
			}
			if (currentParty.getMemberCount() >= 9)
			{
				requester.sendPacket(SystemMessageId.THE_PARTY_IS_FULL);
				return new InviteResult(InviteOutcome.PARTY_FULL, null);
			}
			if (currentParty.getPendingInvitation())
			{
				if (!currentParty.isInvitationRequestExpired())
				{
					requester.sendPacket(SystemMessageId.WAITING_FOR_ANOTHER_REPLY);
					return new InviteResult(InviteOutcome.PARTY_PENDING, null);
				}
				currentParty.setPendingInvitation(false);
			}
			distributionType = currentParty.getDistributionType();
		}

		final SystemMessage invited = new SystemMessage(SystemMessageId.C1_HAS_BEEN_INVITED_TO_THE_PARTY);
		invited.addString(target.getName());

		final long expiresAt = currentGameTick() + ((long) Player.REQUEST_TIMEOUT * GameTimeTaskManager.TICKS_PER_SECOND);
		final PendingInvitation pending;
		final InviteOutcome reservationFailure;
		synchronized (_stateLock)
		{
			if (_pendingByRequester.containsKey(requester.getObjectId()))
			{
				pending = null;
				reservationFailure = InviteOutcome.REQUESTER_BUSY;
			}
			else if (_pendingByInvitee.containsKey(target.getObjectId()))
			{
				pending = null;
				reservationFailure = InviteOutcome.TARGET_BUSY;
			}
			else
			{
				final InvitationIdentity identity = new InvitationIdentity(++_nextSequence, requester.getObjectId(), target.getObjectId());
				pending = new PendingInvitation(identity, requester, target, currentParty, distributionType, expiresAt, managedIdentity.orElse(0), deliveryPort);
				reservationFailure = null;
				_pendingByInvitee.put(target.getObjectId(), pending);
				_pendingByRequester.put(requester.getObjectId(), pending);
			}
		}
		if (reservationFailure == InviteOutcome.REQUESTER_BUSY)
		{
			requester.sendPacket(SystemMessageId.WAITING_FOR_ANOTHER_REPLY);
			return new InviteResult(reservationFailure, null);
		}
		if (reservationFailure == InviteOutcome.TARGET_BUSY)
		{
			final SystemMessage message = new SystemMessage(SystemMessageId.C1_IS_ON_ANOTHER_TASK_PLEASE_TRY_AGAIN_LATER);
			message.addString(target.getName());
			requester.sendPacket(message);
			return new InviteResult(reservationFailure, null);
		}
		requester.sendPacket(invited);
		requester.setPartyDistributionType(distributionType);
		requester.onTransactionRequest(target);
		if (currentParty != null)
		{
			currentParty.setPendingInvitation(true);
		}

		if (managedIdentity.isPresent())
		{
			final DeliveryOutcome delivery = deliveryPort.deliver(pending.deliveryValue(), managedIdentity.orElseThrow());
			if (delivery != DeliveryOutcome.ACCEPTED)
			{
				clearExact(pending, "party.invite.delivery_rejected");
				requester.sendPacket(SystemMessageId.WAITING_FOR_ANOTHER_REPLY);
				return new InviteResult(InviteOutcome.MANAGED_BACKPRESSURE, null);
			}
			return new InviteResult(InviteOutcome.DELIVERED_MANAGED, pending._identity);
		}

		target.sendPacket(new AskJoinParty(requester.getName(), distributionType));
		return new InviteResult(InviteOutcome.DELIVERED_CLIENT, pending._identity);
	}

	public RespondResult respond(Player invitee, int clientResponse, InvitationIdentity expectedIdentity)
	{
		final Optional<Response> response = Response.fromClientValue(clientResponse);
		if (response.isEmpty())
		{
			return new RespondResult(RespondOutcome.INVALID_RESPONSE, expectedIdentity, invitee == null ? null : invitee.getParty());
		}
		return respond(invitee, response.orElseThrow(), expectedIdentity);
	}

	public RespondResult respond(Player invitee, Response response, InvitationIdentity expectedIdentity)
	{
		Objects.requireNonNull(response, "Party invitation response must not be null.");
		if (invitee == null)
		{
			return new RespondResult(RespondOutcome.NO_PENDING_INVITE, expectedIdentity, null);
		}
		final PendingInvitation pending;
		final RespondOutcome lookupFailure;
		synchronized (_stateLock)
		{
			final PendingInvitation candidate = _pendingByInvitee.get(invitee.getObjectId());
			if (candidate == null)
			{
				pending = null;
				lookupFailure = RespondOutcome.NO_PENDING_INVITE;
			}
			else if ((expectedIdentity != null) && !candidate._identity.equals(expectedIdentity))
			{
				pending = null;
				lookupFailure = RespondOutcome.STALE_INVITE;
			}
			else
			{
				pending = candidate;
				lookupFailure = null;
				removePending(pending);
			}
		}
		if (lookupFailure != null)
		{
			return new RespondResult(lookupFailure, expectedIdentity, invitee.getParty());
		}
		if (expired(pending))
		{
			clearDetached(pending, "party.invite.expired");
			return new RespondResult(RespondOutcome.EXPIRED, pending._identity, invitee.getParty());
		}
		final Player requester = pending._requester;
		if ((requester == null) || (invitee.getActiveRequester() != requester) || requester.isRequestExpired())
		{
			clearDetached(pending, "party.invite.requester_unavailable");
			return new RespondResult(RespondOutcome.REQUESTER_UNAVAILABLE, pending._identity, invitee.getParty());
		}

		requester.sendPacket(new JoinParty(response.clientValue()));
		if (response == Response.DISABLED)
		{
			final SystemMessage message = new SystemMessage(SystemMessageId.C1_IS_SET_TO_REFUSE_PARTY_REQUESTS_AND_CANNOT_RECEIVE_A_PARTY_REQUEST);
			message.addPcName(invitee);
			requester.sendPacket(message);
			clearDetached(pending, "party.invite.disabled");
			return new RespondResult(RespondOutcome.DISABLED, pending._identity, invitee.getParty());
		}
		if (response == Response.REFUSE)
		{
			clearDetached(pending, "party.invite.refused");
			return new RespondResult(RespondOutcome.REFUSED, pending._identity, invitee.getParty());
		}

		final InviteOutcome revalidation = revalidateAcceptance(pending);
		if (revalidation != null)
		{
			if (revalidation == InviteOutcome.PARTY_FULL)
			{
				final SystemMessage message = new SystemMessage(SystemMessageId.THE_PARTY_IS_FULL);
				invitee.sendPacket(message);
				requester.sendPacket(message);
			}
			clearDetached(pending, "party.invite.revalidation_failed");
			return new RespondResult(revalidation == InviteOutcome.PARTY_FULL ? RespondOutcome.PARTY_FULL : RespondOutcome.REVALIDATION_FAILED, pending._identity, invitee.getParty());
		}

		Party party = requester.getParty();
		if (party == null)
		{
			party = new Party(requester, pending._distributionType);
			requester.setParty(party);
		}
		invitee.joinParty(party);
		updatePartyMatchRoom(requester, invitee);
		clearDetached(pending, "party.invite.accepted");
		return new RespondResult(RespondOutcome.ACCEPTED, pending._identity, party);
	}

	public boolean cancel(InvitationIdentity identity)
	{
		if (identity == null)
		{
			return false;
		}
		final PendingInvitation pending = detachExact(identity);
		if (pending == null)
		{
			return false;
		}
		clearDetached(pending, "party.invite.cancelled");
		return true;
	}

	public boolean expire(InvitationIdentity identity)
	{
		if (identity == null)
		{
			return false;
		}
		final PendingInvitation candidate = findExact(identity);
		if ((candidate == null) || !expired(candidate))
		{
			return false;
		}
		final PendingInvitation pending = detachExact(identity);
		if (pending == null)
		{
			return false;
		}
		clearDetached(pending, "party.invite.expired");
		return true;
	}

	public Optional<InvitationSnapshot> observe(Player invitee)
	{
		if (invitee == null)
		{
			return Optional.empty();
		}
		final PendingInvitation pending = findByInvitee(invitee.getObjectId());
		if (pending == null)
		{
			return Optional.empty();
		}
		if (expired(pending))
		{
			final PendingInvitation expiredPending = detachExact(pending._identity);
			if (expiredPending != null)
			{
				clearDetached(expiredPending, "party.invite.expired");
			}
			return Optional.empty();
		}
		return Optional.of(pending.snapshot());
	}

	public MembershipOutcome leave(Player member)
	{
		if ((member == null) || (member.getParty() == null))
		{
			return MembershipOutcome.NOT_IN_PARTY;
		}
		member.getParty().removePartyMember(member, PartyMessageType.LEFT);
		return MembershipOutcome.COMPLETED;
	}

	public MembershipOutcome expel(Player requester, Player member)
	{
		if ((requester == null) || (member == null) || (requester == member))
		{
			return MembershipOutcome.INVALID_TARGET;
		}
		final Party party = requester.getParty();
		if (party == null)
		{
			return MembershipOutcome.NOT_IN_PARTY;
		}
		if (!party.isLeader(requester))
		{
			return MembershipOutcome.NOT_LEADER;
		}
		if (!party.containsPlayer(member))
		{
			return MembershipOutcome.TARGET_NOT_MEMBER;
		}
		party.removePartyMember(member, PartyMessageType.EXPELLED);
		return MembershipOutcome.COMPLETED;
	}

	public MembershipOutcome transferLeader(Player requester, Player member)
	{
		if ((requester == null) || (member == null) || (requester == member))
		{
			return MembershipOutcome.INVALID_TARGET;
		}
		final Party party = requester.getParty();
		if (party == null)
		{
			return MembershipOutcome.NOT_IN_PARTY;
		}
		if (!party.isLeader(requester))
		{
			return MembershipOutcome.NOT_LEADER;
		}
		if (!party.containsPlayer(member))
		{
			return MembershipOutcome.TARGET_NOT_MEMBER;
		}
		party.setLeader(member);
		return MembershipOutcome.COMPLETED;
	}

	private InviteOutcome validateInvite(Player requester, Player target, boolean managedTarget)
	{
		if (!managedTarget && ((target.getClient() == null) || target.getClient().isDetached()))
		{
			requester.sendMessage("Player is in offline mode.");
			return InviteOutcome.TARGET_OFFLINE;
		}
		if (requester.isPartyBanned())
		{
			requester.sendPacket(SystemMessageId.YOU_HAVE_BEEN_REPORTED_AS_AN_ILLEGAL_PROGRAM_USER_SO_PARTICIPATING_IN_A_PARTY_IS_NOT_ALLOWED);
			requester.sendPacket(ActionFailed.STATIC_PACKET);
			return InviteOutcome.REQUESTER_PARTY_BANNED;
		}
		if (target.isPartyBanned())
		{
			final SystemMessage message = new SystemMessage(SystemMessageId.C1_HAS_BEEN_REPORTED_AS_AN_ILLEGAL_PROGRAM_USER_AND_CANNOT_JOIN_A_PARTY);
			message.addString(target.getName());
			requester.sendPacket(message);
			return InviteOutcome.TARGET_PARTY_BANNED;
		}
		if (!target.isVisibleFor(requester))
		{
			requester.sendPacket(SystemMessageId.THAT_IS_AN_INCORRECT_TARGET);
			return InviteOutcome.TARGET_NOT_VISIBLE;
		}
		if (!sameEventPartyAllowed(requester, target))
		{
			return InviteOutcome.EVENT_RESTRICTED;
		}
		if (target.isInParty())
		{
			final SystemMessage message = new SystemMessage(SystemMessageId.C1_IS_A_MEMBER_OF_ANOTHER_PARTY_AND_CANNOT_BE_INVITED);
			message.addString(target.getName());
			requester.sendPacket(message);
			return InviteOutcome.TARGET_ALREADY_IN_PARTY;
		}
		if (BlockList.isBlocked(target, requester))
		{
			final SystemMessage message = new SystemMessage(SystemMessageId.S1_HAS_PLACED_YOU_ON_HIS_HER_IGNORE_LIST);
			message.addString(target.getName());
			requester.sendPacket(message);
			return InviteOutcome.TARGET_BLOCKED_REQUESTER;
		}
		if (target == requester)
		{
			requester.sendPacket(SystemMessageId.YOU_HAVE_INVITED_THE_WRONG_TARGET);
			return InviteOutcome.SELF_INVITE;
		}
		if (target.isCursedWeaponEquipped() || requester.isCursedWeaponEquipped())
		{
			requester.sendPacket(SystemMessageId.INVALID_TARGET);
			return InviteOutcome.CURSED_WEAPON;
		}
		if (target.isJailed() || requester.isJailed())
		{
			requester.sendMessage("You cannot invite a player while is in Jail.");
			return InviteOutcome.JAIL;
		}
		if (!sameOlympiadPartyAllowed(requester, target))
		{
			requester.sendPacket(SystemMessageId.A_USER_CURRENTLY_PARTICIPATING_IN_THE_OLYMPIAD_CANNOT_SEND_PARTY_AND_FRIEND_INVITATIONS);
			return InviteOutcome.OLYMPIAD;
		}
		if (requester.isProcessingRequest())
		{
			requester.sendPacket(SystemMessageId.WAITING_FOR_ANOTHER_REPLY);
			return InviteOutcome.REQUESTER_BUSY;
		}
		if (target.isProcessingRequest())
		{
			final SystemMessage message = new SystemMessage(SystemMessageId.C1_IS_ON_ANOTHER_TASK_PLEASE_TRY_AGAIN_LATER);
			message.addString(target.getName());
			requester.sendPacket(message);
			return InviteOutcome.TARGET_BUSY;
		}
		final Party party = requester.getParty();
		if ((party != null) && !party.isLeader(requester))
		{
			requester.sendPacket(SystemMessageId.ONLY_THE_LEADER_CAN_GIVE_OUT_INVITATIONS);
			return InviteOutcome.NOT_LEADER;
		}
		return null;
	}

	private InviteOutcome revalidateAcceptance(PendingInvitation pending)
	{
		final Player requester = pending._requester;
		final Player invitee = pending._invitee;
		if ((requester.getObjectId() != pending._identity.requesterObjectId()) || (invitee.getObjectId() != pending._identity.inviteeObjectId()) || (invitee.getActiveRequester() != requester))
		{
			return InviteOutcome.TARGET_BUSY;
		}
		if (requester.isPartyBanned() || invitee.isPartyBanned())
		{
			return InviteOutcome.REQUESTER_PARTY_BANNED;
		}
		if (!invitee.isVisibleFor(requester))
		{
			return InviteOutcome.TARGET_NOT_VISIBLE;
		}
		if (!sameEventPartyAllowed(requester, invitee) || !sameOlympiadPartyAllowed(requester, invitee))
		{
			return InviteOutcome.EVENT_RESTRICTED;
		}
		if (invitee.isInParty() || BlockList.isBlocked(invitee, requester))
		{
			return InviteOutcome.TARGET_ALREADY_IN_PARTY;
		}
		if (invitee.isCursedWeaponEquipped() || requester.isCursedWeaponEquipped())
		{
			return InviteOutcome.CURSED_WEAPON;
		}
		if (invitee.isJailed() || requester.isJailed())
		{
			return InviteOutcome.JAIL;
		}
		final Party party = requester.getParty();
		if (party == null)
		{
			return pending._partyAtInvite == null ? null : InviteOutcome.REVALIDATION_FAILED;
		}
		if (!party.isLeader(requester))
		{
			return InviteOutcome.NOT_LEADER;
		}
		if (party.isInDimensionalRift())
		{
			return InviteOutcome.DIMENSIONAL_RIFT;
		}
		if (party.getMemberCount() >= 9)
		{
			return InviteOutcome.PARTY_FULL;
		}
		if ((pending._partyAtInvite != null) && (party != pending._partyAtInvite))
		{
			return InviteOutcome.NOT_LEADER;
		}
		return null;
	}

	private boolean sameEventPartyAllowed(Player requester, Player target)
	{
		if (!requester.isRegisteredOnEvent() && !target.isRegisteredOnEvent())
		{
			return true;
		}
		if (GeneralConfig.ALLOW_PARTY_IN_SAME_EVENT && (requester.getInstanceId() == target.getInstanceId()) && requester.isRegisteredOnEvent() && target.isRegisteredOnEvent() && requester.getTeam().equals(target.getTeam()))
		{
			return true;
		}
		requester.sendMessage(GeneralConfig.ALLOW_PARTY_IN_SAME_EVENT && !requester.getTeam().equals(target.getTeam()) ? "You cannot be invited to a party of another team." : "Event paticipants cannot be invited to parties.");
		return false;
	}

	private static boolean sameOlympiadPartyAllowed(Player requester, Player target)
	{
		return !target.isInOlympiadMode() && !requester.isInOlympiadMode() || ((target.isInOlympiadMode() == requester.isInOlympiadMode()) && (target.getOlympiadGameId() == requester.getOlympiadGameId()) && (target.getOlympiadSide() == requester.getOlympiadSide()));
	}

	private static void updatePartyMatchRoom(Player requester, Player invitee)
	{
		if (requester.isInPartyMatchRoom() && invitee.isInPartyMatchRoom())
		{
			final PartyMatchRoomList list = PartyMatchRoomList.getInstance();
			if ((list != null) && (list.getPlayerRoomId(requester) == list.getPlayerRoomId(invitee)))
			{
				final PartyMatchRoom room = list.getPlayerRoom(requester);
				if (room != null)
				{
					broadcastRoomMember(invitee, room);
				}
			}
		}
		else if (requester.isInPartyMatchRoom() && !invitee.isInPartyMatchRoom())
		{
			final PartyMatchRoomList list = PartyMatchRoomList.getInstance();
			if (list != null)
			{
				final PartyMatchRoom room = list.getPlayerRoom(requester);
				if (room != null)
				{
					room.addMember(invitee);
					broadcastRoomMember(invitee, room);
					invitee.setPartyRoom(room.getId());
					invitee.broadcastUserInfo();
				}
			}
		}
	}

	private static void broadcastRoomMember(Player invitee, PartyMatchRoom room)
	{
		final ExManagePartyRoomMember packet = new ExManagePartyRoomMember(invitee, room, 1);
		for (Player member : room.getPartyMembers())
		{
			if (member != null)
			{
				member.sendPacket(packet);
			}
		}
	}

	private void expireKnownInvitation(int playerObjectId)
	{
		final PendingInvitation pending = findByInvitee(playerObjectId);
		if ((pending != null) && expired(pending))
		{
			final PendingInvitation expiredPending = detachExact(pending._identity);
			if (expiredPending != null)
			{
				clearDetached(expiredPending, "party.invite.expired");
			}
		}
	}

	private boolean expired(PendingInvitation pending)
	{
		return (currentGameTick() >= pending._expiresAtGameTick) || pending._requester.isRequestExpired();
	}

	private static long currentGameTick()
	{
		return GameTimeTaskManager.getInstance().getGameTicks();
	}

	private void clearExact(PendingInvitation pending, String reasonKey)
	{
		if (detachExact(pending._identity) == null)
		{
			return;
		}
		clearDetached(pending, reasonKey);
	}

	private PendingInvitation findByInvitee(int inviteeObjectId)
	{
		synchronized (_stateLock)
		{
			return _pendingByInvitee.get(inviteeObjectId);
		}
	}

	private PendingInvitation findExact(InvitationIdentity identity)
	{
		synchronized (_stateLock)
		{
			final PendingInvitation pending = _pendingByInvitee.get(identity.inviteeObjectId());
			return ((pending != null) && pending._identity.equals(identity)) ? pending : null;
		}
	}

	private PendingInvitation detachExact(InvitationIdentity identity)
	{
		synchronized (_stateLock)
		{
			final PendingInvitation pending = _pendingByInvitee.get(identity.inviteeObjectId());
			if ((pending == null) || !pending._identity.equals(identity))
			{
				return null;
			}
			removePending(pending);
			return pending;
		}
	}

	private void removePending(PendingInvitation pending)
	{
		_pendingByInvitee.remove(pending._identity.inviteeObjectId(), pending);
		_pendingByRequester.remove(pending._identity.requesterObjectId(), pending);
	}

	private void clearDetached(PendingInvitation pending, String reasonKey)
	{
		if (pending._invitee.getActiveRequester() == pending._requester)
		{
			pending._invitee.setActiveRequester(null);
		}
		pending._requester.onTransactionResponse();
		final Party party = pending._requester.getParty();
		if ((pending._partyAtInvite != null) && (party == pending._partyAtInvite) && party.getPendingInvitation())
		{
			party.setPendingInvitation(false);
		}
		if (pending._managedIdentity > 0)
		{
			pending._delivery.cancelled(pending.deliveryValue(), pending._managedIdentity, reasonKey);
		}
	}

	private void uninstall(PartyInvitationDelivery delivery)
	{
		synchronized (_stateLock)
		{
			if (_managedDelivery == delivery)
			{
				_managedDelivery = NoopDelivery.INSTANCE;
			}
		}
	}

	private static final class PendingInvitation
	{
		private final InvitationIdentity _identity;
		private final Player _requester;
		private final Player _invitee;
		private final Party _partyAtInvite;
		private final PartyDistributionType _distributionType;
		private final long _expiresAtGameTick;
		private final long _managedIdentity;
		private final PartyInvitationDelivery _delivery;

		private PendingInvitation(InvitationIdentity identity, Player requester, Player invitee, Party partyAtInvite, PartyDistributionType distributionType, long expiresAtGameTick, long managedIdentity, PartyInvitationDelivery delivery)
		{
			_identity = identity;
			_requester = requester;
			_invitee = invitee;
			_partyAtInvite = partyAtInvite;
			_distributionType = distributionType;
			_expiresAtGameTick = expiresAtGameTick;
			_managedIdentity = managedIdentity;
			_delivery = delivery;
		}

		private PartyInvitation deliveryValue()
		{
			return new PartyInvitation(_identity, _requester.getObjectId(), _requester.getName(), _invitee.getObjectId(), _invitee.getName(), _distributionType, _partyAtInvite == null ? 0 : _partyAtInvite.getLeaderObjectId(), _expiresAtGameTick);
		}

		private InvitationSnapshot snapshot()
		{
			return new InvitationSnapshot(_identity, _requester.getObjectId(), _invitee.getObjectId(), _distributionType, _partyAtInvite == null ? 0 : _partyAtInvite.getLeaderObjectId(), _expiresAtGameTick, _managedIdentity > 0);
		}
	}

	public static final class DeliveryRegistration implements AutoCloseable
	{
		private final PartyInvitationService _owner;
		private final PartyInvitationDelivery _delivery;
		private final AtomicBoolean _closed = new AtomicBoolean();

		private DeliveryRegistration(PartyInvitationService owner, PartyInvitationDelivery delivery)
		{
			_owner = owner;
			_delivery = delivery;
		}

		@Override
		public void close()
		{
			if (_closed.compareAndSet(false, true))
			{
				_owner.uninstall(_delivery);
			}
		}
	}

	private interface NoopMarker
	{
	}

	private enum NoopDelivery implements PartyInvitationDelivery, NoopMarker
	{
		INSTANCE;

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
	}
}
