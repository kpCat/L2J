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
import org.l2jmobius.gameserver.model.clan.ClanAllianceService;
import org.l2jmobius.gameserver.network.SystemMessageId;

public class AllyDismiss extends ClientPacket
{
	private String _clanName;
	
	@Override
	protected void readImpl()
	{
		_clanName = readString();
	}
	
	@Override
	protected void runImpl()
	{
		if (_clanName == null)
		{
			return;
		}

		final Player player = getPlayer();
		if (player == null)
		{
			return;
		}

		final Clan targetClan = ClanTable.getInstance().getClanByName(_clanName);
		final ClanAllianceService service = ClanAllianceService.getInstance();
		final ClanAllianceService.Result result = service.expel(player, targetClan, service.currentIdentity(player.getClan()).orElse(null));
		if (result.successful())
		{
			player.sendPacket(SystemMessageId.YOU_HAVE_SUCCEEDED_IN_EXPELLING_THE_CLAN);
			return;
		}

		switch (result.reason())
		{
			case CLAN_NOT_FOUND:
			{
				player.sendPacket(SystemMessageId.YOU_ARE_NOT_A_CLAN_MEMBER_AND_CANNOT_PERFORM_THIS_ACTION);
				break;
			}
			case NOT_ALLIED:
			{
				player.sendPacket(SystemMessageId.YOU_ARE_NOT_CURRENTLY_ALLIED_WITH_ANY_CLANS);
				break;
			}
			case NOT_ALLIANCE_LEADER:
			{
				player.sendPacket(SystemMessageId.THIS_FEATURE_IS_ONLY_AVAILABLE_TO_ALLIANCE_LEADERS);
				break;
			}
			case TARGET_NOT_FOUND:
			{
				player.sendPacket(SystemMessageId.THAT_CLAN_DOES_NOT_EXIST);
				break;
			}
			case TARGET_IS_ALLIANCE_LEADER:
			{
				player.sendPacket(SystemMessageId.ALLIANCE_LEADERS_CANNOT_WITHDRAW);
				break;
			}
			case DIFFERENT_ALLIANCE:
			{
				player.sendPacket(SystemMessageId.DIFFERENT_ALLIANCE);
				break;
			}
			default:
			{
				player.sendPacket(SystemMessageId.YOU_HAVE_FAILED_TO_EXPEL_A_CLAN);
			}
		}
	}
}
