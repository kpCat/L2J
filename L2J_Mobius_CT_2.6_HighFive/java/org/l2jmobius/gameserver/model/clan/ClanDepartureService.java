/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.model.clan;

import java.sql.SQLException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.l2jmobius.gameserver.config.PlayerConfig;
import org.l2jmobius.gameserver.data.sql.ClanTable;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.PlayerNativeWork;
import org.l2jmobius.gameserver.network.SystemMessageId;
import org.l2jmobius.gameserver.network.serverpackets.PledgeShowMemberListDelete;
import org.l2jmobius.gameserver.network.serverpackets.SystemMessage;

/** Durable voluntary departure for an owned headless member of an ordinary native clan. */
public final class ClanDepartureService
{
	private static final Logger LOGGER = Logger.getLogger(ClanDepartureService.class.getName());
	private static final ClanSocialMutationFence FENCE = ClanSocialMutationFence.getInstance();
	private static final ClanSocialRepository REPOSITORY = ClanSocialRepository.getInstance();

	public enum Outcome { APPLIED, DEFERRED, STALE, UNCONFIRMED }

	enum Readback { COMMITTED, NOT_WRITTEN, CONFLICT }

	record Change(int clanId, int characterId, int leaderId, long expiryBefore, long expiryAfter)
	{
	}

	public static final class PendingDeparture
	{
		private final Clan _clan;
		private final Change _change;

		private PendingDeparture(Clan clan, Change change)
		{
			_clan = clan;
			_change = change;
		}
	}

	private ClanDepartureService()
	{
	}

	public static Outcome leaveOrdinary(Player player, Clan expectedClan, int expectedLeaderId)
	{
		if (player == null || expectedClan == null || expectedLeaderId <= 0 || !authorizedAction(player)) { return Outcome.STALE; }
		return FENCE.execute(new long[] { ClanSocialMutationFence.clanKey(expectedClan.getId()) }, () ->
		{
			synchronized (player)
			{
				final PendingDeparture pending = player.getPendingClanDeparture();
				if (pending != null) { return pending._clan == expectedClan ? settleLocked(player, pending) : Outcome.UNCONFIRMED; }
				final ClanMember member = expectedClan.getClanMember(player.getObjectId());
				if (!authorizedAction(player) || player.getClan() != expectedClan || ClanTable.getInstance().getClan(expectedClan.getId()) != expectedClan || member == null || member.getPlayer() != player || expectedClan.getLeaderId() != expectedLeaderId)
				{
					return Outcome.STALE;
				}
				if (!ordinaryEligibility(player, expectedClan, expectedLeaderId)) { return Outcome.DEFERRED; }
				final Change change = new Change(expectedClan.getId(), player.getObjectId(), expectedLeaderId, player.getClanJoinExpiryTime(), joinExpiry(System.currentTimeMillis(), PlayerConfig.ALT_CLAN_JOIN_DAYS));
				final PendingDeparture departure = new PendingDeparture(expectedClan, change);
				player.setPendingClanDeparture(departure);
				try
				{
					if (!REPOSITORY.leaveOrdinaryMember(change))
					{
						player.setPendingClanDeparture(null);
						return Outcome.STALE;
					}
				}
				catch (SQLException failure)
				{
					LOGGER.log(Level.WARNING, "Native clan departure requires readback for character " + change.characterId(), failure);
					return settleLocked(player, departure);
				}
				return finishCommitted(player, departure);
			}
		});
	}

	/** Used before the STORE monitor and by competing native clan writers. */
	public static boolean settlePending(Player player)
	{
		final PendingDeparture pending = player.getPendingClanDeparture();
		return pending == null || settlePending(player, pending);
	}

	private static boolean settlePending(Player player, PendingDeparture pending)
	{
		return FENCE.execute(new long[] { ClanSocialMutationFence.clanKey(pending._clan.getId()) }, () ->
		{
			synchronized (player)
			{
				final PendingDeparture current = player.getPendingClanDeparture();
				return current == null || (current == pending && settleLocked(player, pending) != Outcome.UNCONFIRMED);
			}
		});
	}

	static boolean settleMember(ClanMember member)
	{
		final Player player = member.getPlayer();
		final PendingDeparture pending = player == null ? null : player.getPendingClanDeparture();
		return pending == null || (pending._clan == member.getClan() && settlePending(player, pending));
	}

	private static Outcome settleLocked(Player player, PendingDeparture pending)
	{
		try
		{
			return switch (REPOSITORY.ordinaryDepartureState(pending._change))
			{
				case COMMITTED -> finishCommitted(player, pending);
				case NOT_WRITTEN ->
				{
					player.setPendingClanDeparture(null);
					yield Outcome.DEFERRED;
				}
				case CONFLICT -> Outcome.UNCONFIRMED;
			};
		}
		catch (SQLException failure)
		{
			return Outcome.UNCONFIRMED;
		}
	}

	private static Outcome finishCommitted(Player player, PendingDeparture pending)
	{
		if (!pending._clan.finishOrdinaryDeparture(player, pending._change.expiryAfter())) { return Outcome.UNCONFIRMED; }
		player.setPendingClanDeparture(null);
		announceVoluntary(pending._clan, player);
		return Outcome.APPLIED;
	}

	static void announceVoluntary(Clan clan, Player player)
	{
		final SystemMessage message = new SystemMessage(SystemMessageId.S1_HAS_WITHDRAWN_FROM_THE_CLAN);
		message.addString(player.getName());
		clan.broadcastToOnlineMembers(message);
		clan.broadcastToOnlineMembers(new PledgeShowMemberListDelete(player.getName()));
		player.sendPacket(SystemMessageId.YOU_HAVE_WITHDRAWN_FROM_THE_CLAN);
		player.sendPacket(SystemMessageId.AFTER_LEAVING_OR_HAVING_BEEN_DISMISSED_FROM_A_CLAN_YOU_MUST_WAIT_AT_LEAST_A_DAY_BEFORE_JOINING_ANOTHER_CLAN);
	}

	static long joinExpiry(long now, int penaltyDays)
	{
		return now + TimeUnit.DAYS.toMillis(penaltyDays);
	}

	static Readback observe(Change change, int clanId, long expiry, boolean clearedMembership)
	{
		if (clanId == 0 && expiry == change.expiryAfter() && clearedMembership) { return Readback.COMMITTED; }
		if (clanId == change.clanId() && expiry == change.expiryBefore()) { return Readback.NOT_WRITTEN; }
		return Readback.CONFLICT;
	}

	private static boolean authorizedAction(Player player)
	{
		final var owner = player.getNativeWorkOwner();
		final var ticket = owner == null ? null : PlayerNativeWork.current(owner);
		return player.hasHeadlessOutboundSession() && player.hasOwnedStoreBoundary() && !player.isDedicatedGeoScout() && player.getClient() == null
			&& owner != null && owner.player() == player && owner.isCurrent() && ticket != null && ticket.isRunning();
	}

	private static boolean ordinaryEligibility(Player player, Clan clan, int expectedLeaderId)
	{
		return player.isOnline() && !player.isInCombat() && !player.isAlikeDead() && !player.isChangingClass() && player.getDeleteTimer() == 0
			&& player.getObjectId() != expectedLeaderId && clan.getNewLeaderId() != player.getObjectId() && !FENCE.isRetiring(clan.getId())
			&& clan.getLevel() < 5 && clan.getAllyId() == 0 && clan.getDissolvingExpiryTime() == 0 && clan.getAllSubPledges().isEmpty()
			&& clan.getCastleId() == 0 && clan.getFortId() == 0 && clan.getHideoutId() == 0
			&& player.getPledgeType() == 0 && player.getLvlJoinedAcademy() == 0 && player.getApprentice() == 0 && player.getSponsor() == 0;
	}
}
