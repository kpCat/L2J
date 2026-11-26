/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.model.clan;

import java.util.ArrayDeque;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.l2jmobius.gameserver.managers.CastleManager;
import org.l2jmobius.gameserver.managers.FortManager;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.siege.Castle;
import org.l2jmobius.gameserver.model.siege.Fort;
import org.l2jmobius.gameserver.network.SystemMessageId;
import org.l2jmobius.gameserver.network.serverpackets.AskJoinPledge;
import org.l2jmobius.gameserver.network.serverpackets.JoinPledge;
import org.l2jmobius.gameserver.network.serverpackets.PledgeShowInfoUpdate;
import org.l2jmobius.gameserver.network.serverpackets.PledgeShowMemberListAdd;
import org.l2jmobius.gameserver.network.serverpackets.PledgeShowMemberListAll;
import org.l2jmobius.gameserver.network.serverpackets.SystemMessage;
import org.l2jmobius.gameserver.taskmanagers.GameTimeTaskManager;

/**
 * Canonical transport-neutral clan invitation facade. Ordinary client packets
 * and headless callers share the same validation, consent and join mutation.
 */
public final class ClanInvitationService
{
	public enum InviteOutcome
	{
		DELIVERED,
		TARGET_NOT_FOUND,
		JOIN_CONDITION_FAILED,
		REQUESTER_BUSY,
		TARGET_BUSY,
		CAPACITY_REACHED
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
		ACCEPTED,
		REFUSED,
		INVALID_RESPONSE,
		NO_PENDING_INVITE,
		STALE_INVITE,
		EXPIRED,
		REQUESTER_UNAVAILABLE,
		REVALIDATION_FAILED,
		PERSISTENCE_FAILED
	}

	public enum CancelOutcome
	{
		CANCELLED,
		NO_PENDING_INVITE,
		STALE_INVITE
	}

	public record InvitationIdentity(long sequence, int requesterObjectId, int inviteeObjectId, int clanId, int pledgeType)
	{
		public InvitationIdentity
		{
			if ((sequence <= 0) || (requesterObjectId <= 0) || (inviteeObjectId <= 0) || (clanId <= 0))
			{
				throw new IllegalArgumentException("Invalid clan invitation identity.");
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

	public record RespondResult(RespondOutcome outcome, InvitationIdentity identity)
	{
		public boolean accepted()
		{
			return outcome == RespondOutcome.ACCEPTED;
		}
	}

	public record CancelResult(CancelOutcome outcome, InvitationIdentity identity)
	{
	}

	public record InvitationSnapshot(InvitationIdentity identity, String clanName, long expiresAtGameTick)
	{
	}

	private static final int MAX_PENDING_INVITATIONS = 4096;
	private static final ClanInvitationService INSTANCE = new ClanInvitationService();
	private final Object _stateLock = new Object();
	private final Map<Integer, PendingInvitation> _pendingByRequester = new HashMap<>();
	private final Map<Integer, PendingInvitation> _pendingByInvitee = new ConcurrentHashMap<>();
	private final ArrayDeque<PendingInvitation> _pendingOrder = new ArrayDeque<>();
	private long _nextSequence;

	private ClanInvitationService()
	{
	}

	public static ClanInvitationService getInstance()
	{
		return INSTANCE;
	}

	public InviteResult invite(Player requester, Player target, int pledgeType)
	{
		return invite(requester, target, pledgeType, 0);
	}

	/** Headless consent shares canonical pending identities without reserving the generic client request channel. */
	public InviteResult inviteAutonomous(Player requester, Player target, int expectedClanId)
	{
		if ((expectedClanId <= 0) || (requester == null) || (target == null) || !requester.hasHeadlessOutboundSession() || !target.hasHeadlessOutboundSession())
		{
			return new InviteResult(InviteOutcome.TARGET_NOT_FOUND, null);
		}
		return invite(requester, target, 0, expectedClanId);
	}

	private InviteResult invite(Player requester, Player target, int pledgeType, int expectedClanId)
	{
		final boolean autonomous = expectedClanId > 0;
		if ((requester == null) || (target == null))
		{
			if (requester != null)
			{
				requester.sendPacket(SystemMessageId.YOU_HAVE_INVITED_THE_WRONG_TARGET);
			}
			return new InviteResult(InviteOutcome.TARGET_NOT_FOUND, null);
		}
		if (requester.isDedicatedGeoScout() || target.isDedicatedGeoScout())
		{
			requester.sendPacket(SystemMessageId.YOU_HAVE_INVITED_THE_WRONG_TARGET);
			return new InviteResult(InviteOutcome.JOIN_CONDITION_FAILED, null);
		}

		final PendingInvitation pending;
		synchronized (_stateLock)
		{
			expireOldest();
			final Clan clan = requester.getClan();
			if ((clan == null) || (autonomous && (clan.getId() != expectedClanId || !requester.isClanLeader())) || !clan.checkClanJoinCondition(requester, target, pledgeType))
			{
				return new InviteResult(InviteOutcome.JOIN_CONDITION_FAILED, null);
			}
			if (_pendingByRequester.containsKey(requester.getObjectId()) || (!autonomous && requester.isProcessingRequest()))
			{
				requester.sendPacket(SystemMessageId.WAITING_FOR_ANOTHER_REPLY);
				return new InviteResult(InviteOutcome.REQUESTER_BUSY, null);
			}
			if (_pendingByInvitee.containsKey(target.getObjectId()) || (!autonomous && target.isProcessingRequest()))
			{
				final SystemMessage message = new SystemMessage(SystemMessageId.C1_IS_ON_ANOTHER_TASK_PLEASE_TRY_AGAIN_LATER);
				message.addString(target.getName());
				requester.sendPacket(message);
				return new InviteResult(InviteOutcome.TARGET_BUSY, null);
			}
			if (_pendingByInvitee.size() >= MAX_PENDING_INVITATIONS)
			{
				return new InviteResult(InviteOutcome.CAPACITY_REACHED, null);
			}
			final InvitationIdentity identity = new InvitationIdentity(++_nextSequence, requester.getObjectId(), target.getObjectId(), clan.getId(), pledgeType);
			final long expiresAt = currentGameTick() + ((long) Player.REQUEST_TIMEOUT * GameTimeTaskManager.TICKS_PER_SECOND);
			pending = new PendingInvitation(identity, requester, target, clan, clan.getName(), expiresAt, autonomous);
			_pendingByRequester.put(requester.getObjectId(), pending);
			_pendingByInvitee.put(target.getObjectId(), pending);
			_pendingOrder.addLast(pending);
			if (!autonomous)
			{
				requester.onTransactionRequest(target);
			}
		}

		if (!autonomous)
		{
			final String subPledgeName = pending._clan.getSubPledge(pledgeType) == null ? null : pending._clan.getSubPledge(pledgeType).getName();
			target.sendPacket(new AskJoinPledge(requester.getObjectId(), subPledgeName, pledgeType, pending._clanName));
		}
		return new InviteResult(InviteOutcome.DELIVERED, pending._identity);
	}

	public RespondResult respond(Player invitee, int clientResponse, InvitationIdentity expectedIdentity)
	{
		final Optional<Response> response = Response.fromClientValue(clientResponse);
		return response.isEmpty() ? new RespondResult(RespondOutcome.INVALID_RESPONSE, expectedIdentity) : respond(invitee, response.orElseThrow(), expectedIdentity);
	}

	public RespondResult respond(Player invitee, Response response, InvitationIdentity expectedIdentity)
	{
		return respond(invitee, null, response, expectedIdentity);
	}

	/** Caller retains the current native owners for both headless actors through the response. */
	public RespondResult respondAutonomous(Player invitee, Player requester, Response response, InvitationIdentity expectedIdentity)
	{
		if (invitee == null || !invitee.hasHeadlessOutboundSession() || requester == null)
		{
			return new RespondResult(RespondOutcome.REQUESTER_UNAVAILABLE, expectedIdentity);
		}
		return respond(invitee, requester, response, expectedIdentity);
	}

	private RespondResult respond(Player invitee, Player requester, Response response, InvitationIdentity expectedIdentity)
	{
		if (expectedIdentity == null) { return respondLocked(invitee, requester, response, null); }
		final var fence = ClanSocialMutationFence.getInstance();
		return fence.execute(new long[] { ClanSocialMutationFence.clanKey(expectedIdentity.clanId()) }, () -> respondLocked(invitee, requester, response, expectedIdentity));
	}

	private RespondResult respondLocked(Player invitee, Player requester, Response response, InvitationIdentity expectedIdentity)
	{
		Objects.requireNonNull(response);
		if (invitee == null)
		{
			return new RespondResult(RespondOutcome.NO_PENDING_INVITE, expectedIdentity);
		}
		final PendingInvitation pending;
		synchronized (_stateLock)
		{
			pending = _pendingByInvitee.get(invitee.getObjectId());
			if (pending == null)
			{
				return new RespondResult(RespondOutcome.NO_PENDING_INVITE, expectedIdentity);
			}
			if ((expectedIdentity == null) || !pending._identity.equals(expectedIdentity))
			{
				return new RespondResult(RespondOutcome.STALE_INVITE, expectedIdentity);
			}
			if ((requester != null && requester != pending._requester) || (pending._autonomous && requester == null && response == Response.ACCEPT))
			{
				return new RespondResult(RespondOutcome.REQUESTER_UNAVAILABLE, expectedIdentity);
			}
			removePending(pending);
			if (expired(pending))
			{
				clearRequestRelation(pending);
				return new RespondResult(RespondOutcome.EXPIRED, pending._identity);
			}
			if (pending._invitee != invitee || (pending._autonomous ? !pending._requester.isOnline() || !pending._requester.hasHeadlessOutboundSession() || !invitee.hasHeadlessOutboundSession() || !pending._requester.isClanLeader()
				: invitee.getActiveRequester() != pending._requester || pending._requester.isRequestExpired()))
			{
				clearRequestRelation(pending);
				return new RespondResult(RespondOutcome.REQUESTER_UNAVAILABLE, pending._identity);
			}
			if (response == Response.REFUSE)
			{
				refuse(pending);
				clearRequestRelation(pending);
				return new RespondResult(RespondOutcome.REFUSED, pending._identity);
			}
			clearRequestRelation(pending);
		}
		// The per-clan native fence and the invitee's native STORE monitor cover the canonical write.
		// No SQL or packet delivery on successful joins runs under the invitation index monitor.
		synchronized (invitee)
		{
			if (invitee.getPendingClanCreation() != null)
			{
				return new RespondResult(RespondOutcome.PERSISTENCE_FAILED, pending._identity);
			}
			if (ClanSocialMutationFence.getInstance().isRetiring(pending._identity.clanId()) || pending._requester.isDedicatedGeoScout() || invitee.isDedicatedGeoScout()
				|| (pending._autonomous && !pending._requester.isClanLeader()) || (pending._requester.getClan() != pending._clan)
				|| !pending._clan.checkClanJoinCondition(pending._requester, invitee, pending._identity.pledgeType()))
			{
				return new RespondResult(RespondOutcome.REVALIDATION_FAILED, pending._identity);
			}
			if (requester != null && !persistAutonomousJoin(pending, invitee))
			{
				return new RespondResult(RespondOutcome.PERSISTENCE_FAILED, pending._identity);
			}
			completeJoin(pending, invitee);
			return new RespondResult(RespondOutcome.ACCEPTED, pending._identity);
		}
	}

	private static boolean persistAutonomousJoin(PendingInvitation pending, Player player)
	{
		final int pledgeType = pending._identity.pledgeType();
		final boolean academy = pledgeType == Clan.SUBUNIT_ACADEMY;
		final int powerGrade = academy ? 9 : 5;
		try
		{
			return ClanSocialRepository.getInstance().joinMember(pending._identity.clanId(), player.getObjectId(), pledgeType, powerGrade,
				pending._clan.getRankPrivs(powerGrade).getMask(), academy ? player.getLevel() : 0);
		}
		catch (SQLException failure)
		{
			return false;
		}
	}

	public boolean isAutonomous(InvitationIdentity identity)
	{
		if (identity == null) { return false; }
		final var pending = _pendingByInvitee.get(identity.inviteeObjectId());
		return pending != null && pending._autonomous && pending._identity.equals(identity);
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
			if (pending == null || pending._invitee != invitee)
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

	public boolean hasPending(int inviteeObjectId)
	{
		final var pending = _pendingByInvitee.get(inviteeObjectId);
		return pending != null && !expired(pending);
	}

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

	private static void completeJoin(PendingInvitation pending, Player player)
	{
		final Clan clan = pending._clan;
		player.sendPacket(new JoinPledge(clan.getId()));
		player.setPledgeType(pending._identity.pledgeType());
		if (pending._identity.pledgeType() == Clan.SUBUNIT_ACADEMY)
		{
			player.setPowerGrade(9);
			player.setLvlJoinedAcademy(player.getLevel());
		}
		else
		{
			player.setPowerGrade(5);
		}
		clan.addClanMember(player);
		player.setClanPrivileges(clan.getRankPrivs(player.getPowerGrade()));
		player.sendPacket(SystemMessageId.ENTERED_THE_CLAN);
		clan.broadcastToOnlineMembers(new SystemMessage(SystemMessageId.S1_HAS_JOINED_THE_CLAN).addString(player.getName()));

		if (clan.getCastleId() > 0)
		{
			final Castle castle = CastleManager.getInstance().getCastleByOwner(clan);
			if (castle != null)
			{
				castle.giveResidentialSkills(player);
			}
		}
		if (clan.getFortId() > 0)
		{
			final Fort fort = FortManager.getInstance().getFortByOwner(clan);
			if (fort != null)
			{
				fort.giveResidentialSkills(player);
			}
		}
		player.sendSkillList();
		clan.broadcastToOtherOnlineMembers(new PledgeShowMemberListAdd(player), player);
		clan.broadcastToOnlineMembers(new PledgeShowInfoUpdate(clan));
		player.sendPacket(new PledgeShowMemberListAll(clan, player));
		player.setClanJoinExpiryTime(0);
		player.broadcastUserInfo();
	}

	private static void refuse(PendingInvitation pending)
	{
		SystemMessage message = new SystemMessage(SystemMessageId.YOU_DIDN_T_RESPOND_TO_S1_S_INVITATION_JOINING_HAS_BEEN_CANCELLED);
		message.addString(pending._requester.getName());
		pending._invitee.sendPacket(message);
		message = new SystemMessage(SystemMessageId.S1_DID_NOT_RESPOND_INVITATION_TO_THE_CLAN_HAS_BEEN_CANCELLED);
		message.addString(pending._invitee.getName());
		pending._requester.sendPacket(message);
	}

	private void expireOldest()
	{
		while (!_pendingOrder.isEmpty() && expired(_pendingOrder.peekFirst()))
		{
			final PendingInvitation pending = _pendingOrder.removeFirst();
			if (_pendingByInvitee.get(pending._identity.inviteeObjectId()) == pending)
			{
				removePending(pending);
				clearRequestRelation(pending);
			}
		}
	}

	private void removePending(PendingInvitation pending)
	{
		_pendingByRequester.remove(pending._identity.requesterObjectId(), pending);
		_pendingByInvitee.remove(pending._identity.inviteeObjectId(), pending);
		_pendingOrder.remove(pending);
	}

	private static void clearRequestRelation(PendingInvitation pending)
	{
		if (pending._autonomous)
		{
			return;
		}
		if (pending._invitee.getActiveRequester() == pending._requester)
		{
			pending._invitee.setActiveRequester(null);
		}
		pending._requester.onTransactionResponse();
	}

	private static boolean expired(PendingInvitation pending)
	{
		return (currentGameTick() >= pending._expiresAtGameTick) || (!pending._autonomous && pending._requester.isRequestExpired());
	}

	private static long currentGameTick()
	{
		return GameTimeTaskManager.getInstance().getGameTicks();
	}

	private static final class PendingInvitation
	{
		private final InvitationIdentity _identity;
		private final Player _requester;
		private final Player _invitee;
		private final Clan _clan;
		private final String _clanName;
		private final long _expiresAtGameTick;
		private final boolean _autonomous;

		private PendingInvitation(InvitationIdentity identity, Player requester, Player invitee, Clan clan, String clanName, long expiresAtGameTick, boolean autonomous)
		{
			_identity = identity;
			_requester = requester;
			_invitee = invitee;
			_clan = clan;
			_clanName = clanName;
			_expiresAtGameTick = expiresAtGameTick;
			_autonomous = autonomous;
		}

		private InvitationSnapshot snapshot()
		{
			return new InvitationSnapshot(_identity, _clanName, _expiresAtGameTick);
		}
	}
}
