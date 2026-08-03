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

import org.l2jmobius.gameserver.config.PlayerConfig;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.network.holders.RequestTrade;
import org.l2jmobius.gameserver.network.serverpackets.ActionFailed;
import org.l2jmobius.gameserver.services.PrivateStoreService;

/** Client parsing and flood-protection adapter for private buy stores. */
public class RequestPrivateStoreSell extends ClientPacket
{
	private static final int BATCH_LENGTH = 28;
	private int _storePlayerId;
	private RequestTrade[] _items;

	@Override
	protected void readImpl()
	{
		_storePlayerId = readInt();
		final int count = readInt();
		if ((count <= 0) || (count > PlayerConfig.MAX_ITEM_IN_PACKET) || ((count * BATCH_LENGTH) != remaining()))
		{
			return;
		}
		_items = new RequestTrade[count];
		for (int i = 0; i < count; i++)
		{
			final int objectId = readInt();
			final int itemId = readInt();
			readShort();
			readShort();
			final long itemCount = readLong();
			final long price = readLong();
			if ((objectId < 1) || (itemId < 1) || (itemCount < 1) || (price < 0))
			{
				_items = null;
				return;
			}
			_items[i] = new RequestTrade(objectId, itemId, itemCount, price);
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
			player.sendMessage("You are selling items too fast.");
			return;
		}
		PrivateStoreService.getInstance().sell(player, _storePlayerId, _items);
	}
}
