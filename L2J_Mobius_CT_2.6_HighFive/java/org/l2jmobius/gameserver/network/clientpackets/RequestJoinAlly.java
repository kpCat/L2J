/*
 * Copyright (c) 2013 L2jMobius
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
 * IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.l2jmobius.gameserver.network.clientpackets;

import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.clan.ClanAllianceService;
import org.l2jmobius.gameserver.model.clan.ClanAllianceService.AllianceIdentity;
import org.l2jmobius.gameserver.model.clan.ClanAllianceService.MembershipEpoch;
import org.l2jmobius.gameserver.network.SystemMessageId;
import org.l2jmobius.gameserver.network.serverpackets.AskJoinAlly;
import org.l2jmobius.gameserver.network.serverpackets.SystemMessage;

/**
 * @version $Revision: 1.3.4.2 $ $Date: 2005/03/27 15:29:30 $
 */
public class RequestJoinAlly extends ClientPacket
{
	private int _id;
	private AllianceIdentity _allianceIdentity;
	private MembershipEpoch _targetEpoch;
	
	@Override
	protected void readImpl()
	{
		_id = readInt();
	}
	
	@Override
	protected void runImpl()
	{
		final Player player = getPlayer();
		if (player == null)
		{
			return;
		}

		final Player target = World.getInstance().getPlayer(_id);
		if (target == null)
		{
			player.sendPacket(SystemMessageId.YOU_HAVE_INVITED_THE_WRONG_TARGET);
			return;
		}

		final ClanAllianceService.Result result = ClanAllianceService.getInstance().checkInvite(player, target);
		if (!result.successful())
		{
			sendFailure(player, target, result.reason());
			return;
		}
		_allianceIdentity = result.identity();
		_targetEpoch = result.targetEpoch();
		if (!player.getRequest().setRequest(target, this))
		{
			return;
		}

		final SystemMessage message = new SystemMessage(SystemMessageId.S1_LEADER_S2_HAS_REQUESTED_AN_ALLIANCE);
		message.addString(player.getClan().getAllyName());
		message.addString(player.getName());
		target.sendPacket(message);
		target.sendPacket(new AskJoinAlly(player.getObjectId(), player.getClan().getAllyName()));
	}

	AllianceIdentity getAllianceIdentity()
	{
		return _allianceIdentity;
	}
	MembershipEpoch getTargetEpoch()
	{
		return _targetEpoch;
	}

	static void sendFailure(Player player, Player target, ClanAllianceService.Reason reason)
	{
		switch (reason)
		{
			case CLAN_NOT_FOUND:
			{
				player.sendPacket(SystemMessageId.YOU_ARE_NOT_A_CLAN_MEMBER_AND_CANNOT_PERFORM_THIS_ACTION);
				break;
			}
			case NOT_ALLIANCE_LEADER:
			{
				player.sendPacket(SystemMessageId.THIS_FEATURE_IS_ONLY_AVAILABLE_TO_ALLIANCE_LEADERS);
				break;
			}
			case LEADER_DISMISS_PENALTY:
			{
				player.sendPacket(SystemMessageId.YOU_MAY_NOT_ACCEPT_ANY_CLAN_WITHIN_A_DAY_AFTER_EXPELLING_ANOTHER_CLAN);
				break;
			}
			case TARGET_NOT_FOUND:
			{
				player.sendPacket(SystemMessageId.YOU_HAVE_INVITED_THE_WRONG_TARGET);
				break;
			}
			case SELF_TARGET:
			{
				player.sendPacket(SystemMessageId.YOU_CANNOT_ASK_YOURSELF_TO_APPLY_TO_A_CLAN);
				break;
			}
			case TARGET_NOT_IN_CLAN:
			{
				player.sendPacket(SystemMessageId.THE_TARGET_MUST_BE_A_CLAN_MEMBER);
				break;
			}
			case TARGET_NOT_LEADER:
			{
				final SystemMessage message = new SystemMessage(SystemMessageId.S1_IS_NOT_A_CLAN_LEADER);
				message.addString(target.getName());
				player.sendPacket(message);
				break;
			}
			case TARGET_ALREADY_ALLIED:
			{
				final SystemMessage message = new SystemMessage(SystemMessageId.S1_CLAN_IS_ALREADY_A_MEMBER_OF_S2_ALLIANCE);
				message.addString(target.getClan().getName());
				message.addString(target.getClan().getAllyName());
				player.sendPacket(message);
				break;
			}
			case TARGET_LEAVE_PENALTY:
			{
				final SystemMessage message = new SystemMessage(SystemMessageId.S1_CLAN_CANNOT_JOIN_THE_ALLIANCE_BECAUSE_ONE_DAY_HAS_NOT_YET_PASSED_SINCE_THEY_LEFT_ANOTHER_ALLIANCE);
				message.addString(target.getClan().getName());
				message.addString(target.getClan().getAllyName());
				player.sendPacket(message);
				break;
			}
			case TARGET_DISMISSED_PENALTY:
			{
				player.sendPacket(SystemMessageId.A_CLAN_THAT_HAS_WITHDRAWN_OR_BEEN_EXPELLED_CANNOT_ENTER_INTO_AN_ALLIANCE_WITHIN_ONE_DAY_OF_WITHDRAWAL_OR_EXPULSION);
				break;
			}
			case BOTH_IN_SIEGE:
			{
				player.sendPacket(SystemMessageId.THE_OPPOSING_CLAN_IS_PARTICIPATING_IN_A_SIEGE_BATTLE);
				break;
			}
			case AT_WAR:
			{
				player.sendPacket(SystemMessageId.YOU_MAY_NOT_ALLY_WITH_A_CLAN_YOU_ARE_CURRENTLY_AT_WAR_WITH_THAT_WOULD_BE_DIABOLICAL_AND_TREACHEROUS);
				break;
			}
			case ALLIANCE_FULL:
			{
				player.sendPacket(SystemMessageId.YOU_HAVE_EXCEEDED_THE_LIMIT);
				break;
			}
			default:
			{
				player.sendPacket(SystemMessageId.YOU_HAVE_FAILED_TO_INVITE_A_CLAN_INTO_THE_ALLIANCE);
			}
		}
	}
}
