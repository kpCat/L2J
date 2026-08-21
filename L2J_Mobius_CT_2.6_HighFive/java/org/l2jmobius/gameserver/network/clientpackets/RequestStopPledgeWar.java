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
import org.l2jmobius.gameserver.network.SystemMessageId;
import org.l2jmobius.gameserver.network.serverpackets.ActionFailed;

public class RequestStopPledgeWar extends ClientPacket
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

		final ClanWarService service = ClanWarService.getInstance();
		final ClanWarService.WarIdentity identity = service.currentWar(playerClan, targetClan).orElse(null);
		if (identity == null)
		{
			player.sendMessage("You aren't at war with this clan.");
			player.sendPacket(ActionFailed.STATIC_PACKET);
			return;
		}

		final ClanWarService.Result result = service.stop(player, targetClan, identity.warId());
		if (result.successful())
		{
			return;
		}
		switch (result.reason())
		{
			case NOT_AUTHORIZED:
				player.sendPacket(SystemMessageId.YOU_ARE_NOT_AUTHORIZED_TO_DO_THAT);
				break;
			case ATTACK_STANCE:
				player.sendPacket(SystemMessageId.A_CEASE_FIRE_DURING_A_CLAN_WAR_CAN_NOT_BE_CALLED_WHILE_MEMBERS_OF_YOUR_CLAN_ARE_ENGAGED_IN_BATTLE);
				break;
			case NOT_AT_WAR:
				player.sendMessage("You aren't at war with this clan.");
				player.sendPacket(ActionFailed.STATIC_PACKET);
				break;
			default:
				player.sendMessage("Clan war could not be stopped.");
		}
	}
}
