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
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.l2jmobius.gameserver.network.clientpackets;

import java.util.HashSet;
import java.util.Set;

import org.l2jmobius.gameserver.config.GeneralConfig;
import org.l2jmobius.gameserver.config.PlayerConfig;
import org.l2jmobius.gameserver.managers.PunishmentManager;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.network.holders.RequestTrade;
import org.l2jmobius.gameserver.network.serverpackets.ActionFailed;
import org.l2jmobius.gameserver.services.PrivateStoreService;

/** Client parsing, flood protection and package-cheat punishment adapter. */
public class RequestPrivateStoreBuy extends ClientPacket
{
	private static final int BATCH_LENGTH = 20;
	private int _storePlayerId;
	private Set<RequestTrade> _items;

	@Override
	protected void readImpl()
	{
		_storePlayerId = readInt();
		final int count = readInt();
		if ((count <= 0) || (count > PlayerConfig.MAX_ITEM_IN_PACKET) || ((count * BATCH_LENGTH) != remaining()))
		{
			return;
		}
		_items = new HashSet<>();
		for (int i = 0; i < count; i++)
		{
			final int objectId = readInt();
			final long itemCount = readLong();
			final long price = readLong();
			if ((objectId < 1) || (itemCount < 1) || (price < 0))
			{
				_items = null;
				return;
			}
			_items.add(new RequestTrade(objectId, itemCount, price));
		}
	}

	@Override
	protected void runImpl()
	{
		final Player player = getPlayer();
		if (player == null)
		{
			return;
		}
		if (_items == null)
		{
			player.sendPacket(ActionFailed.STATIC_PACKET);
			return;
		}
		if (!getClient().getFloodProtectors().canPerformTransaction())
		{
			player.sendMessage("You are buying items too fast.");
			return;
		}
		if (PrivateStoreService.getInstance().buy(player, _storePlayerId, _items) == PrivateStoreService.Result.PACKAGE_VIOLATION)
		{
			PunishmentManager.handleIllegalPlayerAction(player, "[RequestPrivateStoreBuy] " + player + " tried to buy less items than sold by package-sell, ban this player for bot usage!", GeneralConfig.DEFAULT_PUNISH);
		}
	}
}
