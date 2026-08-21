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

import org.l2jmobius.gameserver.data.sql.ClanTable;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.clan.Clan;
import org.l2jmobius.gameserver.model.clan.ClanWarService;
import org.l2jmobius.gameserver.network.PacketLogger;
import org.l2jmobius.gameserver.network.SystemMessageId;
import org.l2jmobius.gameserver.network.serverpackets.ActionFailed;
import org.l2jmobius.gameserver.network.serverpackets.SystemMessage;

public class RequestSurrenderPledgeWar extends ClientPacket
{
	private String _pledgeName;
	
	@Override
	protected void readImpl()
	{
		_pledgeName = readString();
	}
	
	@Override
	protected void runImpl()
	{
		final Player player = getPlayer();
		if (player == null)
		{
			return;
		}

		final Clan playerClan = player.getClan();
		if (playerClan == null)
		{
			return;
		}
		final Clan targetClan = ClanTable.getInstance().getClanByName(_pledgeName);
		if (targetClan == null)
		{
			player.sendMessage("No such clan.");
			player.sendPacket(ActionFailed.STATIC_PACKET);
			return;
		}

		PacketLogger.info("RequestSurrenderPledgeWar by " + playerClan.getName() + " with " + _pledgeName);
		final ClanWarService service = ClanWarService.getInstance();
		final ClanWarService.WarIdentity identity = service.currentWar(playerClan, targetClan).orElse(null);
		if (identity == null)
		{
			player.sendMessage("You aren't at war with this clan.");
			player.sendPacket(ActionFailed.STATIC_PACKET);
			return;
		}
		final ClanWarService.Result result = service.surrender(player, targetClan, identity.warId());
		if (!result.successful())
		{
			player.sendMessage("Clan war surrender failed.");
			return;
		}

		final SystemMessage message = new SystemMessage(SystemMessageId.YOU_HAVE_SURRENDERED_TO_THE_S1_CLAN);
		message.addString(_pledgeName);
		player.sendPacket(message);
	}
}
