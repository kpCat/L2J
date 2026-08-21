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

import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.clan.ClanAllianceService;
import org.l2jmobius.gameserver.network.SystemMessageId;

public class RequestAnswerJoinAlly extends ClientPacket
{
	private int _response;
	
	@Override
	protected void readImpl()
	{
		_response = readInt();
	}
	
	@Override
	protected void runImpl()
	{
		final Player player = getPlayer();
		if (player == null)
		{
			return;
		}

		final Player requestor = player.getRequest().getPartner();
		if (requestor == null)
		{
			return;
		}

		if (_response == 0)
		{
			player.sendPacket(SystemMessageId.NO_RESPONSE_YOUR_ENTRANCE_TO_THE_ALLIANCE_HAS_BEEN_CANCELLED);
			requestor.sendPacket(SystemMessageId.NO_RESPONSE_INVITATION_TO_JOIN_AN_ALLIANCE_HAS_BEEN_CANCELLED);
		}
		else
		{
			if (!(requestor.getRequest().getRequestPacket() instanceof RequestJoinAlly request))
			{
				return; // hax
			}

			final ClanAllianceService.Result result = ClanAllianceService.getInstance().join(requestor, player, request.getAllianceIdentity(), request.getTargetEpoch());
			if (result.successful())
			{
				// TODO: Need correct message id
				requestor.sendPacket(SystemMessageId.THAT_PERSON_HAS_BEEN_SUCCESSFULLY_ADDED_TO_YOUR_FRIEND_LIST);
				player.sendPacket(SystemMessageId.YOU_HAVE_ACCEPTED_THE_ALLIANCE);
			}
			else
			{
				RequestJoinAlly.sendFailure(requestor, player, result.reason());
			}
		}

		player.getRequest().onRequestResponse();
	}
}
