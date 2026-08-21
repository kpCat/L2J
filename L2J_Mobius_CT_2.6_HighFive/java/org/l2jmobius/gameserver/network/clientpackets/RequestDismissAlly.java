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

/**
 * @version $Revision: 1.3.2.1.2.3 $ $Date: 2005/03/27 15:29:30 $
 */
public class RequestDismissAlly extends ClientPacket
{
	@Override
	protected void readImpl()
	{
	}
	
	@Override
	protected void runImpl()
	{
		final Player player = getPlayer();
		if (player == null)
		{
			return;
		}

		final ClanAllianceService service = ClanAllianceService.getInstance();
		final ClanAllianceService.Result result = service.dissolve(player, service.currentIdentity(player.getClan()).orElse(null));
		if (result.successful())
		{
			return;
		}

		switch (result.reason())
		{
			case NOT_ALLIED:
			{
				player.sendPacket(SystemMessageId.YOU_ARE_NOT_CURRENTLY_ALLIED_WITH_ANY_CLANS);
				break;
			}
			case CLAN_NOT_FOUND:
			case NOT_ALLIANCE_LEADER:
			{
				player.sendPacket(SystemMessageId.THIS_FEATURE_IS_ONLY_AVAILABLE_TO_ALLIANCE_LEADERS);
				break;
			}
			case ACTOR_IN_SIEGE:
			{
				player.sendPacket(SystemMessageId.YOU_CANNOT_DISSOLVE_AN_ALLIANCE_WHILE_AN_AFFILIATED_CLAN_IS_PARTICIPATING_IN_A_SIEGE_BATTLE);
				break;
			}
			default:
			{
				player.sendPacket(SystemMessageId.YOU_HAVE_FAILED_TO_DISSOLVE_THE_ALLIANCE);
			}
		}
	}
}
