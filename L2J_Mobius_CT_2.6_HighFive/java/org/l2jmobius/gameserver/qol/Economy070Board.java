/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.qol;

import java.time.Instant;
import java.util.Comparator;
import java.util.UUID;
import org.l2jmobius.gameserver.config.custom.CommunityBoardConfig;
import org.l2jmobius.gameserver.config.custom.PremiumSystemConfig;
import org.l2jmobius.gameserver.data.xml.ItemData;
import org.l2jmobius.gameserver.managers.PremiumManager;
import org.l2jmobius.gameserver.model.actor.Player;

/** Native High Five HTML, server quotes and real transactions; no client assets. */
public final class Economy070Board
{
	private Economy070Board() { }
	public static String render(String command, Player player)
	{
		String page = "home", notice = "";
		try
		{
			if (!CommunityBoardConfig.CUSTOM_CB_ENABLED) { throw new IllegalStateException("Alt+B выключен в этом профиле."); }
			final String[] parts = command.split(";", -1);
			if (parts.length >= 2) { page = parts[1]; }
			final var pricing = Economy070Pricing.getInstance();
			final var shop = Economy070Shop.getInstance();
			if (java.util.Set.of("deposit", "withdraw", "buy", "subscribe", "subclass").contains(page))
			{
				if (parts.length != 5) { throw new IllegalArgumentException("Неверный запрос. Обновите страницу."); }
				final String action = page; page = action.equals("subclass") ? "quest" : action.equals("subscribe") ? "premium" : "epic";
				final long value = Long.parseLong(parts[2]);
				final long result = switch (action)
				{
					case "deposit" -> shop.deposit(player, value, parts[3], parts[4]);
					case "withdraw" -> shop.withdraw(player, value, parts[3], parts[4]);
					case "subscribe" -> shop.premium(player, Math.toIntExact(value), parts[3], parts[4]);
					case "buy" -> shop.buy(player, Math.toIntExact(value), parts[3], parts[4]);
					case "subclass" -> PaidSubclass070Service.purchase(player, parts[3], parts[4]);
					default -> throw new IllegalArgumentException();
				};
				notice = action.equals("subscribe") ? "Премиум до " + Instant.ofEpochMilli(result) : "Операция сохранена. Квитанция: " + parts[3];
			}
			final StringBuilder html = new StringBuilder("<html><body><center><font color=LEVEL>Phantom World — экономика</font><br>");
			html.append("<table width=750><tr><td width=180 valign=top>");
			for (String[] link : new String[][] {{"home", "Магазины"}, {"consumables", "Расходники"}, {"quest", "Квесты / клан"}, {"scrolls", "Заточка / свитки"}, {"epic", "Epic Jewelry"}, {"codex", "Giant's Codex"}, {"mammon", "Ancient Adena / Маммон"}, {"epaulette", "Knight's Epaulettes"}, {"premium", "Премиум"}})
			{ html.append(link(link[1], "_bbseconomy;" + link[0])).append("<br>"); }
			html.append(link("Личная QoL-панель", "_bbsqol")).append("<br>").append(link("Buffer", "_bbstop;buffer/main.html")).append("<br>").append(link("Gatekeeper", "_bbstop;gatekeeper/main.html"));
			html.append("</td><td width=570 valign=top>").append(escape(notice)).append("<br>");
			switch (page)
			{
				case "epic" ->
				{
					html.append("<font color=LEVEL>Epic Savings</font><br>Счёт аккаунта: ").append(shop.balance(player)).append(" Adena.<br>Пополнение 1:1 настоящей Adena; вывод ограничен свободным местом до лимита валюты.<br><edit var=amount width=140 type=number><br>");
					html.append(action("Внести Adena", "deposit", "$amount", pricing)).append(" ").append(action("Вывести Adena", "withdraw", "$amount", pricing)).append("<br>");
					if (!Economy070Shop.eligible(player)) { html.append("Покупка доступна обычному живому персонажу access0 вне боя/торговли.<br>"); }
					for (var quote : pricing.quotes().stream().filter(q -> q.key().startsWith("epic.")).sorted(Comparator.comparingInt(Economy070Pricing.Quote::itemId)).toList())
					{
						final var item = ItemData.getInstance().getTemplate(quote.itemId()); final var stock = shop.stock(quote.itemId());
						html.append("<table><tr><td><img src=\"").append(item.getIcon()).append("\" width=32 height=32></td><td>").append(escape(item.getName())).append(" — ").append(quote.price()).append(" Adena<br>Глобальный запас: ").append(stock.count()).append("/1; пополнение: ").append(stock.nextRestockUtc() > 0 ? Instant.ofEpochMilli(stock.nextRestockUtc()) : "после продажи через " + quote.restockDays() + " суток").append("<br>");
						if (stock.count() == 1 && Economy070Shop.eligible(player)) { html.append(action("Купить со счёта", "buy", Integer.toString(quote.itemId()), pricing)); }
						else { html.append("Недоступно: ").append(stock.count() == 0 ? "распродано" : "условия персонажа"); }
						html.append("</td></tr></table>");
					}
				}
				case "premium" ->
				{
					html.append("Премиум аккаунта: ").append(player.hasPremiumStatus() ? "до " + Instant.ofEpochMilli(PremiumManager.getInstance().getPremiumExpiration(player.getAccountName())) : "не активен").append("<br>XP ×2, SP ×2; шанс обычного и редкого drop/spoil ×2; количество ×1. Adena суммарно ×2.<br>Цена суток: ").append(pricing.price("premium.day")).append(" Adena. Срок идёт по реальному времени.<br>");
					if (PremiumSystemConfig.PREMIUM_SYSTEM_ENABLED && CommunityBoardConfig.COMMUNITY_PREMIUM_SYSTEM_ENABLED && Economy070Shop.eligible(player))
					{ for (int days : new int[] {1, 3, 7, 15, 20, 30}) { html.append(action(days + " дн. — " + Math.multiplyExact(pricing.price("premium.day"), days) + " A", "subscribe", Integer.toString(days), pricing)).append("<br>"); } }
					else { html.append("Покупка недоступна: система выключена или персонаж не соответствует условиям."); }
					html.append("<br>Настоящая timed-руна drop/spoil в stock H5 не найдена; вымышленные предметы не продаются.");
				}
				case "codex" -> html.append("Альтернатива за Ancient Adena (5575): 6622 / 9625 / 9626 / 9627. Точная текущая цена показана в native окне обмена.<br>Оригинальный путь Olympiad: 6622 за 550 Olympiad Tokens сохранён.<br>").append(multisell("Открыть Giant's Codex", 91005));
				case "epaulette" -> html.append("Knight's Epaulettes (9912). Три проверенных крепостных предложения Codex; сохраняются обе валюты 9912 + Adena и native смысл варианта.<br>").append(multisell("Крепостные награды", 91006));
				case "quest" ->
				{
					html.append("Дорогие альтернативные предметы; остальные условия квеста, уровень, SP и репутация клана сохраняются.<br>").append(multisell("Квестовые предметы / клан", 600024));
					html.append("<br>Subclass: оплачиваемая помощь с групповыми этапами Fate's Whisper / Mimir's Elixir — ").append(pricing.price("subclass.gateway")).append(" Adena, один раз на персонажа. Уровень75+, основная профессия, не Kamael. Требуются настоящие диалоги Leorin/Ladd,984 CrystalB, подходящее B-оружие и Pure Silver. Помощь выдаёт только материалы текущего этапа; native обмены и завершение квестов сохраняются.<br>");
					html.append(action("Оплатить помощь subclass", "subclass", "0", pricing));
					html.append("<br>Материалы выдаются один раз. Утрата предметов или отмена квеста не возвращает оплату; исходная добыча остаётся доступна.");
				}
				case "scrolls" -> html.append("Все действующие цены и валюты показаны до подтверждения native обмена.<br>").append(multisell("Заточка / свитки", 600012));
				case "consumables" -> html.append("Mana Potion 728: ").append(pricing.price("mana")).append(" Adena; разрешена в PvP, исходный эффект и reuse.<br>").append(multisell("Купить Mana Potion", 91003)).append("<br>").append(multisell("Камни → Ancient Adena (3/5/10)", 91004));
				case "mammon" -> html.append(multisell("Постоянный обмен камней 3/5/10", 91004)).append("<br>Также у кузнеца Pushkin (30300), Giran: 77458, 148169, −3595, в любой период Seven Signs.<br>Маммон: снятие SA, обмен/улучшение оружия, S услуги и rare/foundation требуют вашего исходного предмета, AA и native материалов.<br>Эти операции доступны у настоящего Blacksmith of Mammon в катакомбах: ограничения NPC и Seven Signs сохраняются. Виртуальный NPC bypass не используется.<br>A распечатка — у городских кузнецов. Условия и стоимость в native диалоге NPC.<br>").append(link("Дорогие Codex за AA", "_bbseconomy;codex"));
				default -> html.append("Выберите раздел слева. Обычные готовые оружие, броня и бижутерия недоступны в Alt+B. Источники: добыча, крафт, рынок.<br>Все цены берутся из явно применённого плана. Предмет и валюта проверяются сервером.");
			}
			html.append("<br><font color=777777>План ").append(pricing.revision().substring(0, 12)).append("</font></td></tr></table></center></body></html>");
			return html.toString();
		}
		catch (Exception | LinkageError failure)
		{ return "<html><body><center>Служба недоступна: " + escape(failure.getMessage()) + "<br>" + link("Обновить магазины", "_bbseconomy;home") + "</center></body></html>"; }
	}
	private static String action(String text, String action, String value, Economy070Pricing pricing) { return link(text, "_bbseconomy;" + action + ';' + value + ';' + UUID.randomUUID() + ';' + pricing.revision()); }
	private static String multisell(String text, int list) { return link(text, "_bbsmultisell;" + list + ",merchant/main"); }
	private static String link(String text, String command) { return "<a action=\"bypass " + command + "\">" + escape(text) + "</a>"; }
	private static String escape(String text) { return text == null ? "" : text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;"); }
}
