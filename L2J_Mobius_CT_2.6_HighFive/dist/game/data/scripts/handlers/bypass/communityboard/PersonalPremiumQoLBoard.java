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
import org.l2jmobius.gameserver.qol.CrystallizationService.Preview;
import org.l2jmobius.gameserver.qol.PersonalCharacterQoLService;
import org.l2jmobius.gameserver.qol.PersonalCrystallizationService;
import org.l2jmobius.gameserver.qol.PersonalCrystallizationService.ConfirmResult;
import org.l2jmobius.gameserver.qol.PersonalCrystallizationService.Page;
import org.l2jmobius.gameserver.qol.PersonalCrystallizationService.PendingConfirmation;
import org.l2jmobius.gameserver.qol.PersonalCrystallizationService.PrepareResult;
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
	private static final String CRYSTALLIZATION_PATH = "data/html/CommunityBoard/Custom/personal-qol/crystallization.html";
	private static final String CONFIRMATION_PATH = "data/html/CommunityBoard/Custom/personal-qol/crystallization-confirm.html";
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

		final PersonalPremiumQoLService premiumService = PersonalPremiumQoLService.getInstance();
		final PersonalCharacterQoLService personalService = PersonalCharacterQoLService.getInstance();
		if (!premiumService.isEnabled() && !personalService.isAnyFeatureEnabled(player))
		{
			player.setMultiSell(null);
			player.sendMessage("Личная QoL-функция выключена.");
			return false;
		}

		if ("_bbsqol;shop".equals(command))
		{
			if (!premiumService.isShopEnabled())
			{
				player.setMultiSell(null);
				player.sendMessage("Магазин личных QoL-предметов выключен.");
			}
			else
			{
				ThreadPool.schedule(() -> MultisellData.getInstance().separateAndSendPersonalPremiumQoL(player), 100);
			}
		}
		else if (command.startsWith("_bbsqol;crystallize"))
		{
			player.setMultiSell(null);
			if (!personalService.isCrystallizationEnabled(player))
			{
				player.sendMessage("Личная кристаллизация выключена или недоступна.");
				return false;
			}
			return handleCrystallization(command, player);
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
		final int maximumGap = premiumService.maximumGap(player);
		html = html.replace("%navigation%", navigation);
		html = html.replace("%current_gap%", maximumGap > 0 ? "±" + maximumGap + " уровней" : "нет активного предмета");
		html = html.replace("%shop_state%", premiumService.isShopEnabled() ? "включён" : "выключен");
		html = html.replace("%personal_actions%", personalActions(player, personalService));
		CommunityBoardHandler.getInstance().addBypass(player, "Personal QoL", "_bbsqol");
		CommunityBoardHandler.separateAndSend(html, player);
		return false;
	}

	private static boolean handleCrystallization(String command, Player player)
	{
		final String[] parts = command.split(";", -1);
		final PersonalCrystallizationService service = PersonalCrystallizationService.getInstance();
		if ((parts.length == 5) && "prepare".equals(parts[2]))
		{
			final int objectId = parsePositiveInt(parts[3]);
			final int page = parsePositiveInt(parts[4]);
			if (objectId <= 0)
			{
				player.sendMessage("Выбранный предмет больше недоступен.");
				return showCrystallization(player, service, page);
			}
			final PrepareResult result = service.prepare(player, objectId);
			if (result.pending() == null)
			{
				player.sendMessage("Предмет нельзя подготовить к кристаллизации.");
				return showCrystallization(player, service, page);
			}
			return showConfirmation(player, result.pending(), page);
		}
		if ((parts.length == 4) && "confirm".equals(parts[2]))
		{
			if ((player.getClient() == null) || !player.getClient().getFloodProtectors().canPerformTransaction())
			{
				player.sendMessage("Слишком частая попытка кристаллизации.");
				return showCrystallization(player, service, 1);
			}
			final ConfirmResult result = service.confirm(player, parts[3]);
			if (result.status() != PersonalCrystallizationService.ConfirmationStatus.SUCCESS)
			{
				player.sendMessage("Подтверждение устарело или уже было использовано.");
			}
			return showCrystallization(player, service, 1);
		}
		final int page = ((parts.length == 4) && "page".equals(parts[2])) ? parsePositiveInt(parts[3]) : 1;
		return showCrystallization(player, service, page);
	}

	private static boolean showCrystallization(Player player, PersonalCrystallizationService service, int requestedPage)
	{
		String html = HtmCache.getInstance().getHtm(player, CRYSTALLIZATION_PATH);
		final String navigation = HtmCache.getInstance().getHtm(player, NAVIGATION_PATH);
		if ((html == null) || (navigation == null))
		{
			player.sendMessage("Не удалось загрузить страницу кристаллизации.");
			return false;
		}

		final Page page = service.list(player, requestedPage);
		final StringBuilder rows = new StringBuilder();
		for (Preview item : page.items())
		{
			rows.append("<tr><td width=250>");
			rows.append(escapeHtml(item.itemName()));
			if (item.enchantLevel() > 0)
			{
				rows.append(" +").append(item.enchantLevel());
			}
			if (item.count() > 1)
			{
				rows.append(" x").append(item.count());
			}
			rows.append("</td><td width=80>").append(item.grade().name()).append("</td><td width=100>").append(item.crystalItemId()).append(" x").append(item.crystalCount()).append("</td><td width=90><button value=\"Выбрать\" action=\"bypass _bbsqol;crystallize;prepare;").append(item.itemObjectId()).append(';').append(page.page()).append("\" width=80 height=25 back=\"L2UI_CT1.Button_DF_Down\" fore=\"L2UI_CT1.Button_DF\"></td></tr>");
		}
		if (page.items().isEmpty())
		{
			rows.append("<tr><td width=520 align=center>Нет доступных предметов.</td></tr>");
		}

		final StringBuilder navigationRow = new StringBuilder();
		if (page.page() > 1)
		{
			navigationRow.append("<button value=\"Назад\" action=\"bypass _bbsqol;crystallize;page;").append(page.page() - 1).append("\" width=80 height=25 back=\"L2UI_CT1.Button_DF_Down\" fore=\"L2UI_CT1.Button_DF\">");
		}
		navigationRow.append(" Страница ").append(page.page()).append(" / ").append(page.totalPages()).append(' ');
		if (page.page() < page.totalPages())
		{
			navigationRow.append("<button value=\"Далее\" action=\"bypass _bbsqol;crystallize;page;").append(page.page() + 1).append("\" width=80 height=25 back=\"L2UI_CT1.Button_DF_Down\" fore=\"L2UI_CT1.Button_DF\">");
		}

		html = html.replace("%navigation%", navigation);
		html = html.replace("%items%", rows.toString());
		html = html.replace("%pages%", navigationRow.toString());
		CommunityBoardHandler.separateAndSend(html, player);
		return false;
	}

	private static boolean showConfirmation(Player player, PendingConfirmation pending, int page)
	{
		String html = HtmCache.getInstance().getHtm(player, CONFIRMATION_PATH);
		final String navigation = HtmCache.getInstance().getHtm(player, NAVIGATION_PATH);
		if ((html == null) || (navigation == null))
		{
			player.sendMessage("Не удалось загрузить подтверждение кристаллизации.");
			return false;
		}
		final Preview item = pending.preview();
		html = html.replace("%navigation%", navigation);
		html = html.replace("%item_name%", escapeHtml(item.itemName()));
		html = html.replace("%enchant%", item.enchantLevel() > 0 ? "+" + item.enchantLevel() : "без заточки");
		html = html.replace("%item_count%", Long.toString(item.count()));
		html = html.replace("%crystal_result%", item.crystalItemId() + " x" + item.crystalCount());
		html = html.replace("%confirm_token%", pending.token());
		html = html.replace("%return_page%", Integer.toString(Math.max(1, page)));
		CommunityBoardHandler.separateAndSend(html, player);
		return false;
	}

	private static String personalActions(Player player, PersonalCharacterQoLService service)
	{
		final StringBuilder actions = new StringBuilder();
		if (service.isCrossClassSkillsEnabled(player))
		{
			actions.append("<font color=\"B09878\">Обучение:</font> доступны штатные списки других профессий у подходящих наставников.<br>");
		}
		if (service.isCrystallizationEnabled(player))
		{
			actions.append("<button value=\"Кристаллизация\" action=\"bypass _bbsqol;crystallize\" width=200 height=30 back=\"L2UI_CT1.OlympiadWnd_DF_Reward_Down\" fore=\"L2UI_CT1.OlympiadWnd_DF_Reward\">");
		}
		return actions.toString();
	}

	private static int parsePositiveInt(String value)
	{
		try
		{
			final int parsed = Integer.parseInt(value);
			return parsed > 0 ? parsed : -1;
		}
		catch (RuntimeException e)
		{
			return -1;
		}
	}

	private static String escapeHtml(String value)
	{
		return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
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
