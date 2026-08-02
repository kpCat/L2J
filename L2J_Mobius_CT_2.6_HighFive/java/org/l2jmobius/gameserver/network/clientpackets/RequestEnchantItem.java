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

import org.l2jmobius.gameserver.config.GeneralConfig;
import org.l2jmobius.gameserver.config.PlayerConfig;
import org.l2jmobius.gameserver.data.xml.EnchantItemData;
import org.l2jmobius.gameserver.managers.PunishmentManager;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.item.enchant.EnchantScroll;
import org.l2jmobius.gameserver.model.item.enchant.EnchantSupportItem;
import org.l2jmobius.gameserver.model.item.instance.Item;
import org.l2jmobius.gameserver.network.SystemMessageId;
import org.l2jmobius.gameserver.network.serverpackets.EnchantResult;
import org.l2jmobius.gameserver.services.EnchantItemService;
import org.l2jmobius.gameserver.services.EnchantItemService.Request;

/**
 * Ordinary-client adapter. Client timing and punishment stay here; all
 * reusable enchant mutation is owned by {@link EnchantItemService}.
 * @author Mobius
 */
public class RequestEnchantItem extends ClientPacket
{
	private int _objectId;

	@Override
	protected void readImpl()
	{
		_objectId = readInt();
		// The High Five client does not send the support object in this packet.
	}

	@Override
	protected void runImpl()
	{
		final Player player = getPlayer();
		if ((player == null) || (_objectId == 0))
		{
			return;
		}
		if (!player.isOnline() || getClient().isDetached())
		{
			player.setActiveEnchantItemId(Player.ID_NONE);
			return;
		}
		if (player.isProcessingTransaction() || player.isInStoreMode())
		{
			player.sendPacket(SystemMessageId.YOU_CANNOT_ENCHANT_WHILE_OPERATING_A_PRIVATE_STORE_OR_PRIVATE_WORKSHOP);
			player.setActiveEnchantItemId(Player.ID_NONE);
			return;
		}

		final int scrollObjectId = player.getActiveEnchantItemId();
		final int supportObjectId = Math.max(0, player.getActiveEnchantSupportItemId());
		final Item item = player.getInventory().getItemByObjectId(_objectId);
		final Item scroll = player.getInventory().getItemByObjectId(scrollObjectId);
		final Item support = player.getInventory().getItemByObjectId(supportObjectId);
		if ((item == null) || (scroll == null))
		{
			player.setActiveEnchantItemId(Player.ID_NONE);
			player.sendPacket(SystemMessageId.YOU_HAVE_CANCELLED_THE_ENCHANTING_PROCESS);
			player.sendPacket(new EnchantResult(2, 0, 0));
			return;
		}
		final EnchantScroll scrollTemplate = EnchantItemData.getInstance().getEnchantScroll(scroll);
		if (scrollTemplate == null)
		{
			return;
		}
		EnchantSupportItem supportTemplate = null;
		if (support != null)
		{
			supportTemplate = EnchantItemData.getInstance().getSupportItem(support);
			if (supportTemplate == null)
			{
				player.setActiveEnchantItemId(Player.ID_NONE);
				return;
			}
		}
		if (!scrollTemplate.isValid(item, supportTemplate) || (PlayerConfig.DISABLE_OVER_ENCHANTING && (item.getEnchantLevel() == scrollTemplate.getMaxEnchantLevel())))
		{
			player.sendPacket(SystemMessageId.INAPPROPRIATE_ENCHANT_CONDITIONS);
			player.setActiveEnchantItemId(Player.ID_NONE);
			player.sendPacket(new EnchantResult(2, 0, 0));
			return;
		}
		if ((player.getActiveEnchantTimestamp() == 0) || ((System.currentTimeMillis() - player.getActiveEnchantTimestamp()) < 2000))
		{
			PunishmentManager.handleIllegalPlayerAction(player, player + " use autoenchant program ", GeneralConfig.DEFAULT_PUNISH);
			player.setActiveEnchantItemId(Player.ID_NONE);
			player.sendPacket(new EnchantResult(2, 0, 0));
			return;
		}

		EnchantItemService.getInstance().execute(new Request(player, _objectId, scrollObjectId, supportObjectId, true, EnchantItemService.Observer.NONE));
	}
}
