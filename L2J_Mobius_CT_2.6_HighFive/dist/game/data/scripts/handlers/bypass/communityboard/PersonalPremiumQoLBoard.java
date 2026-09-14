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
import org.l2jmobius.gameserver.qol.PersonalPlayerControlService;
import org.l2jmobius.gameserver.qol.PersonalPlayerControlService.HerbCategory;
import org.l2jmobius.gameserver.qol.PersonalPremiumQoLService;
import org.l2jmobius.gameserver.qol.PersonalPremiumQoLService.StorefrontOffer;

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
		final PersonalPlayerControlService controlService = PersonalPlayerControlService.getInstance();
		String section = "overview";
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
		else if ("_bbsqol;crystallize".equals(command) || command.startsWith("_bbsqol;crystallize;"))
		{
			player.setMultiSell(null);
			if (!personalService.isCrystallizationEnabled(player))
			{
				player.sendMessage("Личная кристаллизация выключена или недоступна.");
				return false;
			}
			return handleCrystallization(command, player);
		}
		else if ("_bbsqol".equals(command))
		{
			player.setMultiSell(null);
		}
		else
		{
			player.setMultiSell(null);
			final String[] parts = command.split(";", -1);
			if ((parts.length == 3) && "_bbsqol".equals(parts[0]) && "view".equals(parts[1]) && isKnownSection(parts[2]))
			{
				section = parts[2];
			}
			else if ((parts.length == 3) && "_bbsqol".equals(parts[0]) && "exp".equals(parts[1]) && isOnOff(parts[2]))
			{
				controlService.setExperienceGainEnabled(player, "on".equals(parts[2]));
				section = "character";
			}
			else if ((parts.length == 4) && "_bbsqol".equals(parts[0]) && "herb".equals(parts[1]) && isOnOff(parts[3]))
			{
				final HerbCategory category = parseHerbCategory(parts[2]);
				if (category == null)
				{
					return false;
				}
				controlService.setHerbEnabled(player, category, "on".equals(parts[3]));
				section = "herbs";
			}
			else
			{
				return false;
			}
		}

		String html = HtmCache.getInstance().getHtm(player, PAGE_PATH);
		final String navigation = HtmCache.getInstance().getHtm(player, NAVIGATION_PATH);
		if ((html == null) || (navigation == null))
		{
			player.setMultiSell(null);
			player.sendMessage("Не удалось загрузить страницу личных QoL-предметов.");
			return false;
		}
		html = html.replace("%navigation%", navigation);
		html = html.replace("%section_navigation%", sectionNavigation());
		html = html.replace("%section_title%", sectionTitle(section));
		html = html.replace("%section_content%", sectionContent(section, player, premiumService, personalService, controlService));
		CommunityBoardHandler.getInstance().addBypass(player, "Personal QoL", "_bbsqol");
		CommunityBoardHandler.separateAndSend(html, player);
		return false;
	}

	private static boolean handleCrystallization(String command, Player player)
	{
		final String[] parts = command.split(";", -1);
		final PersonalCrystallizationService service = PersonalCrystallizationService.getInstance();
		if ((parts.length == 2) && "crystallize".equals(parts[1]))
		{
			return showCrystallization(player, service, 1);
		}
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
		if ((parts.length == 4) && "page".equals(parts[2]))
		{
			final int page = parsePositiveInt(parts[3]);
			if (page > 0)
			{
				return showCrystallization(player, service, page);
			}
		}
		player.sendMessage("Некорректная команда личной QoL-панели.");
		return false;
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

	private static boolean isKnownSection(String section)
	{
		return "overview".equals(section) || "character".equals(section) || "passes".equals(section) || "herbs".equals(section) || "crystallization".equals(section) || "utilities".equals(section) || "help".equals(section);
	}

	private static boolean isOnOff(String value)
	{
		return "on".equals(value) || "off".equals(value);
	}

	private static HerbCategory parseHerbCategory(String value)
	{
		if ("recovery".equals(value))
		{
			return HerbCategory.RECOVERY;
		}
		if ("combat".equals(value))
		{
			return HerbCategory.COMBAT;
		}
		return "vitality".equals(value) ? HerbCategory.VITALITY : null;
	}

	private static String sectionNavigation()
	{
		return "<table width=520><tr>" +
			"<td><button value=\"Персонаж / EXP\" action=\"bypass _bbsqol;view;character\" width=120 height=25 back=\"L2UI_CT1.Button_DF_Down\" fore=\"L2UI_CT1.Button_DF\"></td>" +
			"<td><button value=\"Дроп и спойл\" action=\"bypass _bbsqol;view;passes\" width=120 height=25 back=\"L2UI_CT1.Button_DF_Down\" fore=\"L2UI_CT1.Button_DF\"></td>" +
			"<td><button value=\"Травы\" action=\"bypass _bbsqol;view;herbs\" width=85 height=25 back=\"L2UI_CT1.Button_DF_Down\" fore=\"L2UI_CT1.Button_DF\"></td>" +
			"<td><button value=\"Кристаллизация\" action=\"bypass _bbsqol;view;crystallization\" width=135 height=25 back=\"L2UI_CT1.Button_DF_Down\" fore=\"L2UI_CT1.Button_DF\"></td></tr><tr>" +
			"<td><button value=\"Расходники\" action=\"bypass _bbsqol;view;utilities\" width=120 height=25 back=\"L2UI_CT1.Button_DF_Down\" fore=\"L2UI_CT1.Button_DF\"></td>" +
			"<td><button value=\"Статус / справка\" action=\"bypass _bbsqol;view;help\" width=120 height=25 back=\"L2UI_CT1.Button_DF_Down\" fore=\"L2UI_CT1.Button_DF\"></td>" +
			"<td><button value=\"Обзор\" action=\"bypass _bbsqol\" width=85 height=25 back=\"L2UI_CT1.Button_DF_Down\" fore=\"L2UI_CT1.Button_DF\"></td><td></td></tr></table>";
	}

	private static String sectionTitle(String section)
	{
		if ("character".equals(section))
		{
			return "Персонаж / EXP";
		}
		if ("passes".equals(section))
		{
			return "Дроп и спойл";
		}
		if ("herbs".equals(section))
		{
			return "Травы";
		}
		if ("crystallization".equals(section))
		{
			return "Кристаллизация";
		}
		if ("utilities".equals(section))
		{
			return "Расходники и утилиты";
		}
		return "help".equals(section) ? "Статус и справка" : "Личная QoL-панель";
	}

	private static String sectionContent(String section, Player player, PersonalPremiumQoLService premiumService, PersonalCharacterQoLService personalService, PersonalPlayerControlService controlService)
	{
		if ("character".equals(section))
		{
			return characterContent(player, personalService, controlService);
		}
		if ("passes".equals(section))
		{
			return passesContent(player, premiumService);
		}
		if ("herbs".equals(section))
		{
			return herbsContent(player, controlService);
		}
		if ("crystallization".equals(section))
		{
			return crystallizationContent(player, personalService);
		}
		if ("utilities".equals(section))
		{
			return utilitiesContent();
		}
		return "help".equals(section) ? helpContent(player, premiumService, personalService, controlService) : overviewContent();
	}

	private static String overviewContent()
	{
		return "Все личные функции собраны по категориям выше.<br1>" +
			"Настройки EXP и трав сохраняются отдельно для каждого персонажа.<br1>" +
			"Магазин и кристаллизация используют штатные серверные механики и ограничения.<br><br>" +
			"Начните с раздела <font color=\"LEVEL\">Статус / справка</font>, чтобы увидеть активные возможности.";
	}

	private static String characterContent(Player player, PersonalCharacterQoLService personalService, PersonalPlayerControlService controlService)
	{
		final boolean expEnabled = controlService.isExperienceGainEnabled(player);
		final StringBuilder content = new StringBuilder();
		content.append("<font color=\"B09878\">Получение опыта:</font> <font color=\"LEVEL\">").append(state(expEnabled)).append("</font><br>");
		content.append(toggleButton("EXP включить", "_bbsqol;exp;on", expEnabled));
		content.append(toggleButton("EXP выключить", "_bbsqol;exp;off", !expEnabled));
		content.append("<br><br>Тот же параметр изменяют команды <font color=\"LEVEL\">.expon</font> и <font color=\"LEVEL\">.expoff</font>.<br>");
		content.append("<font color=\"B09878\">Обучение у других профессий:</font> ").append(personalService.isCrossClassSkillsEnabled(player) ? "доступно у подходящих наставников." : "недоступно для этого персонажа.");
		return content.toString();
	}

	private static String passesContent(Player player, PersonalPremiumQoLService service)
	{
		final int maximumGap = service.maximumGap(player);
		final StringBuilder content = new StringBuilder();
		content.append("<font color=\"B09878\">Текущая защита:</font> <font color=\"LEVEL\">").append(maximumGap > 0 ? "±" + maximumGap + " уровней" : "нет активного предмета").append("</font><br>");
		content.append("Предмет в основном инвентаре снимает только штраф разницы уровней для обычного DROP и SPOIL. Действует максимальный тир.<br><br>");
		content.append("<table width=500 border=0 cellspacing=0 cellpadding=3>");
		for (StorefrontOffer offer : service.storefrontOffers())
		{
			content.append("<tr><td width=335><font color=\"LEVEL\">").append(escapeHtml(offer.label())).append("</font></td><td>").append(formatNumber(offer.price())).append(" Adena</td></tr>");
		}
		if (service.storefrontOffers().isEmpty())
		{
			content.append("<tr><td width=500>Каталог временно недоступен.</td></tr>");
		}
		content.append("</table><br><font color=\"B09878\">Магазин:</font> ").append(service.isShopEnabled() ? "включён" : "выключен администратором").append("<br>");
		if (service.isShopEnabled())
		{
			content.append("<button value=\"Открыть магазин\" action=\"bypass _bbsqol;shop\" width=200 height=30 back=\"L2UI_CT1.OlympiadWnd_DF_Reward_Down\" fore=\"L2UI_CT1.OlympiadWnd_DF_Reward\">");
		}
		return content.toString();
	}

	private static String herbsContent(Player player, PersonalPlayerControlService service)
	{
		final StringBuilder content = new StringBuilder("Подобранная отключённая трава исчезает штатно, но её эффект не применяется и предмет не засоряет инвентарь.<br><br>");
		appendHerbControl(content, player, service, HerbCategory.RECOVERY, "recovery");
		appendHerbControl(content, player, service, HerbCategory.COMBAT, "combat");
		appendHerbControl(content, player, service, HerbCategory.VITALITY, "vitality");
		return content.toString();
	}

	private static void appendHerbControl(StringBuilder content, Player player, PersonalPlayerControlService service, HerbCategory category, String route)
	{
		final boolean enabled = service.isHerbEnabled(player, category);
		content.append("<font color=\"B09878\">").append(category.displayName()).append(":</font> <font color=\"LEVEL\">").append(state(enabled)).append("</font> ");
		content.append(toggleButton("Включить", "_bbsqol;herb;" + route + ";on", enabled));
		content.append(toggleButton("Выключить", "_bbsqol;herb;" + route + ";off", !enabled));
		content.append("<br>");
	}

	private static String crystallizationContent(Player player, PersonalCharacterQoLService service)
	{
		if (!service.isCrystallizationEnabled(player))
		{
			return "Кристаллизация выключена или недоступна этому персонажу.";
		}
		return "Список строится только из подходящих предметов текущего инвентаря. Перед необратимым действием требуется отдельное подтверждение.<br><br>" +
			"<button value=\"Открыть кристаллизацию\" action=\"bypass _bbsqol;crystallize\" width=210 height=30 back=\"L2UI_CT1.OlympiadWnd_DF_Reward_Down\" fore=\"L2UI_CT1.OlympiadWnd_DF_Reward\">";
	}

	private static String utilitiesContent()
	{
		return "Mana-, Vitality- и rate-расходники проверены по серверным данным, но безопасный источник розничной цены в Adena не найден.<br><br>" +
			"Продажа отложена: панель не создаёт новые предметы или цены без подтверждённого экономического контракта.";
	}

	private static String helpContent(Player player, PersonalPremiumQoLService premiumService, PersonalCharacterQoLService personalService, PersonalPlayerControlService controlService)
	{
		return "EXP: <font color=\"LEVEL\">" + state(controlService.isExperienceGainEnabled(player)) + "</font><br1>" +
			"Травы HP/MP: <font color=\"LEVEL\">" + state(controlService.isHerbEnabled(player, HerbCategory.RECOVERY)) + "</font><br1>" +
			"Боевые травы: <font color=\"LEVEL\">" + state(controlService.isHerbEnabled(player, HerbCategory.COMBAT)) + "</font><br1>" +
			"Vitality-травы: <font color=\"LEVEL\">" + state(controlService.isHerbEnabled(player, HerbCategory.VITALITY)) + "</font><br1>" +
			"Магазин пропусков: <font color=\"LEVEL\">" + (premiumService.isShopEnabled() ? "включён" : "выключен") + "</font><br1>" +
			"Кристаллизация: <font color=\"LEVEL\">" + (personalService.isCrystallizationEnabled(player) ? "доступна" : "недоступна") + "</font><br><br>" +
			"Все кнопки применяются только к текущему персонажу. Штатные ограничения Community Board сохраняются.";
	}

	private static String toggleButton(String label, String command, boolean current)
	{
		return current ? " <font color=\"777777\">[" + label + "]</font> " : " <button value=\"" + label + "\" action=\"bypass " + command + "\" width=105 height=25 back=\"L2UI_CT1.Button_DF_Down\" fore=\"L2UI_CT1.Button_DF\"> ";
	}

	private static String state(boolean enabled)
	{
		return enabled ? "включено" : "выключено";
	}

	private static String formatNumber(long value)
	{
		return String.format("%,d", value).replace(',', ' ');
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
