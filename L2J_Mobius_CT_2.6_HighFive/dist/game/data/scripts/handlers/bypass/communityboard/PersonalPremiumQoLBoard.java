/*
 * This file is part of the L2J Mobius project.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package handlers.bypass.communityboard;

import org.l2jmobius.commons.threads.ThreadPool;
import org.l2jmobius.gameserver.cache.HtmCache;
import org.l2jmobius.gameserver.config.custom.CommunityBoardConfig;
import org.l2jmobius.gameserver.data.xml.MultisellData;
import org.l2jmobius.gameserver.handler.CommunityBoardHandler;
import org.l2jmobius.gameserver.handler.IParseBoardHandler;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.zone.ZoneId;
import org.l2jmobius.gameserver.qol.PersonalPremiumQoLService;

/**
 * Personal/Premium QoL Community Board page and the sole admitted shop entry point.
 */
public class PersonalPremiumQoLBoard implements IParseBoardHandler
{
	private static final String[] COMMANDS =
	{
		"_bbsqol"
	};
	private static final String PAGE_PATH = "data/html/CommunityBoard/Custom/personal-qol/main.html";
	private static final String NAVIGATION_PATH = "data/html/CommunityBoard/Custom/navigation.html";

	@Override
	public boolean onCommand(String command, Player player)
	{
		if (!CommunityBoardConfig.CUSTOM_CB_ENABLED)
		{
			player.setMultiSell(null);
			player.sendMessage("Личная QoL-страница недоступна: Custom Community Board выключена.");
			return false;
		}
		if (CommunityBoardConfig.COMMUNITYBOARD_COMBAT_DISABLED && isRestrictedState(player))
		{
			player.setMultiSell(null);
			player.sendMessage("Сейчас нельзя использовать Community Board.");
			return false;
		}
		if (CommunityBoardConfig.COMMUNITYBOARD_KARMA_DISABLED && (player.getKarma() > 0))
		{
			player.setMultiSell(null);
			player.sendMessage("Игроки с кармой не могут использовать Community Board.");
			return false;
		}
		if (CommunityBoardConfig.COMMUNITYBOARD_PEACE_ONLY && !player.isInsideZone(ZoneId.PEACE))
		{
			player.setMultiSell(null);
			player.sendMessage("Community Board доступна только в мирной зоне.");
			return false;
		}

		final PersonalPremiumQoLService service = PersonalPremiumQoLService.getInstance();
		if (!service.isEnabled())
		{
			player.setMultiSell(null);
			player.sendMessage("Личная QoL-функция выключена.");
			return false;
		}

		if ("_bbsqol;shop".equals(command))
		{
			if (!service.isShopEnabled())
			{
				player.setMultiSell(null);
				player.sendMessage("Магазин личных QoL-предметов выключен.");
			}
			else
			{
				ThreadPool.schedule(() -> MultisellData.getInstance().separateAndSendPersonalPremiumQoL(player), 100);
			}
		}
		else if (!"_bbsqol".equals(command))
		{
			player.setMultiSell(null);
			return false;
		}

		String html = HtmCache.getInstance().getHtm(player, PAGE_PATH);
		final String navigation = HtmCache.getInstance().getHtm(player, NAVIGATION_PATH);
		if ((html == null) || (navigation == null))
		{
			player.setMultiSell(null);
			player.sendMessage("Не удалось загрузить страницу личных QoL-предметов.");
			return false;
		}
		final int maximumGap = service.maximumGap(player);
		html = html.replace("%navigation%", navigation);
		html = html.replace("%current_gap%", maximumGap > 0 ? "±" + maximumGap + " уровней" : "нет активного предмета");
		html = html.replace("%shop_state%", service.isShopEnabled() ? "включён" : "выключен");
		CommunityBoardHandler.getInstance().addBypass(player, "Personal QoL", "_bbsqol");
		CommunityBoardHandler.separateAndSend(html, player);
		return false;
	}

	private static boolean isRestrictedState(Player player)
	{
		return player.isCastingNow() || player.isCastingSimultaneouslyNow() || player.isInCombat() || player.isInDuel() || player.isInOlympiadMode() || player.isInsideZone(ZoneId.SIEGE) || player.isInsideZone(ZoneId.PVP) || (player.getPvpFlag() > 0) || player.isAlikeDead() || player.isOnEvent() || player.isInStoreMode();
	}

	@Override
	public String[] getCommandList()
	{
		return COMMANDS;
	}
}
