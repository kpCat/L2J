/*
 * Copyright (c) 2026 L2jMobius
 * This file is part of the L2J Mobius project, licensed under GPLv3 or later.
 */
package handlers.bypass.communityboard;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.l2jmobius.gameserver.data.xml.ItemData;
import org.l2jmobius.gameserver.data.xml.RecipeData;
import org.l2jmobius.gameserver.handler.CommunityBoardHandler;
import org.l2jmobius.gameserver.handler.IParseBoardHandler;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.enums.player.PrivateStoreType;
import org.l2jmobius.gameserver.model.item.ItemTemplate;
import org.l2jmobius.gameserver.model.item.enums.BodyPart;
import org.l2jmobius.gameserver.model.item.enums.ItemLocation;
import org.l2jmobius.gameserver.model.item.type.CrystalType;
import org.l2jmobius.gameserver.model.item.type.EtcItemType;
import org.l2jmobius.gameserver.model.skill.CommonSkill;
import org.l2jmobius.gameserver.network.holders.TradeItem;

/** Request-only view of live native stores. It neither publishes listings nor trades. */
public class MarketSearchBoard implements IParseBoardHandler
{
	private static final int MAX_PLAYERS = 2048;
	private static final int MAX_LINES = 4096;
	private static final int MAX_STORE_LINES = 100;
	private static final int MAX_RESULTS = 12;
	private static final String[] MODES = { "SELL", "BUY", "CRAFT" };
	private static final String[] CATEGORIES = { "ALL", "WEAPON", "ARMOR", "JEWEL", "MATERIAL", "RECIPE", "SHOT" };
	private static final String[] GRADES = { "ALL", "NONE", "D", "C", "B", "A", "S", "S80", "S84" };

	@Override
	public String[] getCommandList()
	{
		return new String[] { "_bbsmarket" };
	}

	@Override
	public boolean onCommand(String command, Player player)
	{
		if (player == null) { return false; }
		String mode = "SELL";
		String category = "ALL";
		String grade = "ALL";
		String message = "Выберите тип лавки и нажмите «Поиск».";
		final List<Listing> results = new ArrayList<>();
		try
		{
			if (command.length() > 256) { throw new IllegalArgumentException(); }
			if (command.startsWith("_bbsmarket;mark;"))
			{
				message = mark(command, player);
			}
			else if (command.startsWith("_bbsmarket;search;"))
			{
				final String[] fields = command.split(";", 6);
				if (fields.length != 6) { throw new IllegalArgumentException(); }
				mode = choice(fields[2], MODES);
				category = choice(fields[3], CATEGORIES);
				grade = choice(fields[4], GRADES);
				final String name = fields[5].trim();
				if (name.length() > 64) { throw new IllegalArgumentException(); }
				final List<Player> owners = World.getInstance().getPlayers().stream().limit(MAX_PLAYERS + 1).toList();
				final ScanBudget budget = new ScanBudget(MAX_LINES);
				boolean limited = owners.size() > MAX_PLAYERS;
				search:
				for (Player owner : owners.subList(0, Math.min(owners.size(), MAX_PLAYERS)))
				{
					if (!eligible(player, owner)) { continue; }
					final List<Listing> listings = listings(owner, mode, budget);
					for (Listing listing : listings)
					{
						if (results.size() == MAX_RESULTS)
						{
							limited = true;
							break search;
						}
						final ItemTemplate item = ItemData.getInstance().getTemplate(listing.itemId());
						if (item != null && matches(item, category, grade, name)) { results.add(listing); }
					}
					if (budget.remaining == 0) { limited = true; break; }
				}
				limited |= budget.limited;
				message = "Найдено: " + results.size() + ". Название: " + escape(name) + (limited ? ". Выборка ограничена; уточните фильтр." : ".");
			}
			else if (!command.equals("_bbsmarket")) { throw new IllegalArgumentException(); }
		}
		catch (IllegalArgumentException invalid)
		{
			message = "Неверные параметры поиска. Выберите фильтры заново.";
		}
		show(player, mode, category, grade, message, results);
		return true;
	}

	private static boolean eligible(Player viewer, Player owner)
	{
		return owner != null && owner != viewer && World.getInstance().getPlayer(owner.getObjectId()) == owner
			&& owner.isOnline() && !owner.isAlikeDead() && !owner.isTeleporting() && !owner.isInvisible()
			&& owner.getInstanceId() == viewer.getInstanceId() && owner.getAccessLevel().allowTransaction() && !owner.hasPendingOwnedStore();
	}

	private static List<Listing> listings(Player owner, String mode, ScanBudget budget)
	{
		final List<Listing> result = new ArrayList<>();
		if (mode.equals("CRAFT"))
		{
			if (owner.getPrivateStoreType() != PrivateStoreType.MANUFACTURE || owner.isCrafting()) { return result; }
			final var recipes = owner.getManufactureItems();
			synchronized (recipes)
			{
				if (owner.getPrivateStoreType() != PrivateStoreType.MANUFACTURE) { return result; }
				int inspected = 0;
				for (var listing : recipes.values())
				{
					if (++inspected > MAX_STORE_LINES) { budget.limited = true; break; }
					if (!budget.inspect()) { break; }
					final var recipe = RecipeData.getInstance().getRecipeList(listing.getRecipeId());
					if (recipe == null || !owner.hasRecipeList(recipe.getId()) || recipe.getCount() <= 0 || listing.getCost() < 0
						|| owner.getSkillLevel((recipe.isDwarvenRecipe() ? CommonSkill.CREATE_DWARVEN : CommonSkill.CREATE_COMMON).getId()) < recipe.getLevel()) { continue; }
					result.add(new Listing(mode, owner.getObjectId(), recipe.getId(), recipe.getItemId(), 0, recipe.getCount(), listing.getCost(), recipe.getSuccessRate()));
				}
			}
			return result;
		}
		final boolean sell = mode.equals("SELL");
		if (owner.getPrivateStoreType() != (sell ? PrivateStoreType.SELL : PrivateStoreType.BUY)) { return result; }
		final var list = sell ? owner.getSellList() : owner.getBuyList();
		synchronized (list)
		{
			if (owner.getPrivateStoreType() != (sell ? PrivateStoreType.SELL : PrivateStoreType.BUY) || list.getOwner() != owner || list.isPackaged()) { return result; }
			int inspected = 0;
			for (TradeItem line : list.getItems())
			{
				if (++inspected > MAX_STORE_LINES) { budget.limited = true; break; }
				if (!budget.inspect()) { break; }
				if (line.getItem() == null || line.getPrice() < 0 || line.getCount() <= 0 || line.getItem().isQuestItem() || !line.getItem().isTradeable()) { continue; }
				long count = line.getCount();
				if (sell)
				{
					final var owned = owner.getInventory().getItemByObjectId(line.getObjectId());
					if (owned == null || owned.getOwnerId() != owner.getObjectId() || owned.getId() != line.getItem().getId()
						|| owned.getItemLocation() != ItemLocation.INVENTORY || owned.isEquipped() || !owned.isTradeable() || owned.getEnchantLevel() != line.getEnchant()) { continue; }
					count = Math.min(count, owned.getCount());
				}
				else if (line.getPrice() > 0) { count = Math.min(count, owner.getAdena() / line.getPrice()); }
				if (count <= 0 || (line.getPrice() > 0 && count > Long.MAX_VALUE / line.getPrice())) { continue; }
				result.add(new Listing(mode, owner.getObjectId(), sell ? line.getObjectId() : line.getItem().getId(), line.getItem().getId(), line.getEnchant(), count, line.getPrice(), 100));
			}
		}
		return result;
	}

	private static String mark(String command, Player viewer)
	{
		final String[] fields = command.split(";", -1);
		if (fields.length != 10) { throw new IllegalArgumentException(); }
		final String mode = choice(fields[2], MODES);
		final int ownerId = Integer.parseInt(fields[3]);
		final int lineId = Integer.parseInt(fields[4]);
		final int itemId = Integer.parseInt(fields[5]);
		final int enchant = Integer.parseInt(fields[6]);
		final long count = Long.parseLong(fields[7]);
		final long price = Long.parseLong(fields[8]);
		final int success = Integer.parseInt(fields[9]);
		final Player owner = World.getInstance().getPlayer(ownerId);
		if (ownerId <= 0 || lineId <= 0 || itemId <= 0 || enchant < 0 || count <= 0 || price < 0 || !eligible(viewer, owner)) { return "Лавка больше недоступна. Повторите поиск."; }
		final Listing expected = new Listing(mode, ownerId, lineId, itemId, enchant, count, price, success);
		if (!listings(owner, mode, new ScanBudget(MAX_STORE_LINES)).contains(expected) || !eligible(viewer, owner)
			|| owner.getPrivateStoreType() != (mode.equals("CRAFT") ? PrivateStoreType.MANUFACTURE : mode.equals("SELL") ? PrivateStoreType.SELL : PrivateStoreType.BUY)) { return "Количество, цена или состояние лавки изменились. Повторите поиск."; }
		viewer.getRadar().addMarker(owner.getX(), owner.getY(), owner.getZ());
		return "Владелец лавки «" + escape(owner.getName()) + "» отмечен на радаре.";
	}

	private static boolean matches(ItemTemplate item, String category, String grade, String name)
	{
		if (!grade.equals("ALL") && item.getCrystalTypePlus() != CrystalType.valueOf(grade)) { return false; }
		if (!item.getName().toLowerCase(Locale.ROOT).contains(name.toLowerCase(Locale.ROOT))) { return false; }
		final BodyPart slot = item.getBodyPart();
		final boolean jewel = slot == BodyPart.NECK || slot == BodyPart.R_EAR || slot == BodyPart.L_EAR || slot == BodyPart.LR_EAR || slot == BodyPart.R_FINGER || slot == BodyPart.L_FINGER || slot == BodyPart.LR_FINGER;
		return switch (category)
		{
			case "ALL" -> true;
			case "WEAPON" -> item.isWeapon();
			case "ARMOR" -> item.isArmor() && !jewel;
			case "JEWEL" -> jewel;
			case "MATERIAL" -> item.getItemType() == EtcItemType.MATERIAL;
			case "RECIPE" -> item.getItemType() == EtcItemType.RECIPE;
			case "SHOT" -> item.getItemType() == EtcItemType.SHOT;
			default -> false;
		};
	}

	private static void show(Player player, String mode, String category, String grade, String message, List<Listing> results)
	{
		final StringBuilder html = new StringBuilder("<html><body><center><br><font color=LEVEL>Поиск по лавкам игроков</font><br>");
		html.append("<a action=\"bypass _bbstop\">Главная</a><br><table><tr><td>Лавка</td><td>Категория</td><td>Grade</td><td>Название содержит</td></tr><tr>");
		combo(html, "marketmode", mode, MODES); combo(html, "marketcategory", category, CATEGORIES); combo(html, "marketgrade", grade, GRADES);
		html.append("<td><edit var=marketname width=180 length=64></td></tr></table><button value=\"Поиск\" action=\"bypass _bbsmarket;search;$marketmode;$marketcategory;$marketgrade;$marketname\" width=100 height=25 back=\"L2UI_CT1.Button_DF_Down\" fore=\"L2UI_CT1.Button_DF\"><br>")
			.append(message).append("<br><font color=AAAAAA>SELL — продажа поштучно; BUY — закупка; CRAFT — комиссия за попытку.<br1>CRAFT: материалы заказчика, результат зависит от рецепта. Цены в Adena.</font><br>");
		for (Listing listing : results)
		{
			final Player owner = World.getInstance().getPlayer(listing.ownerId());
			final ItemTemplate item = ItemData.getInstance().getTemplate(listing.itemId());
			if (owner == null || item == null) { continue; }
			html.append("<table width=700 bgcolor=202020><tr><td>").append(listing.mode()).append(" | ").append(escape(item.getName())).append(" [").append(item.getCrystalTypePlus()).append("] +").append(listing.enchant())
				.append("<br1>").append(listing.mode().equals("CRAFT") ? "Выпуск за попытку: " : "Количество: ").append(listing.count())
				.append(listing.mode().equals("CRAFT") ? "; комиссия: " : "; цена за 1: ").append(listing.price()).append("; сумма: ").append(listing.mode().equals("CRAFT") ? listing.price() : listing.count() * listing.price());
			if (listing.mode().equals("CRAFT")) { html.append("; шанс: ").append(listing.success()).append('%'); }
			html.append("<br1>").append(escape(owner.getName())).append(" | ").append(owner.getX()).append(", ").append(owner.getY()).append(", ").append(owner.getZ())
				.append("</td><td width=80><a action=\"bypass _bbsmarket;mark;").append(listing.mode()).append(';').append(listing.ownerId()).append(';').append(listing.lineId()).append(';').append(listing.itemId()).append(';').append(listing.enchant()).append(';').append(listing.count()).append(';').append(listing.price()).append(';').append(listing.success()).append("\">Радар</a></td></tr></table><br>");
		}
		html.append("<font color=AAAAAA>До 12 результатов. Поиск проверяет до 2048 персонажей и 4096 позиций.<br1>Маркер проверяет выбранную позицию заново.</font></center></body></html>");
		CommunityBoardHandler.separateAndSend(html.toString(), player);
	}

	private static void combo(StringBuilder html, String variable, String selected, String[] choices)
	{
		html.append("<td><combobox var=").append(variable).append(" width=115 list=\"").append(selected);
		for (String value : choices) { if (!value.equals(selected)) { html.append(';').append(value); } }
		html.append("\"></td>");
	}

	private static String choice(String value, String[] choices)
	{
		for (String allowed : choices) { if (allowed.equals(value)) { return value; } }
		throw new IllegalArgumentException();
	}

	private static String escape(String value)
	{
		return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
	}

	private record Listing(String mode, int ownerId, int lineId, int itemId, int enchant, long count, long price, int success) { }

	private static final class ScanBudget
	{
		private int remaining;
		private boolean limited;
		private ScanBudget(int maximum) { remaining = maximum; }
		private boolean inspect()
		{
			if (remaining == 0) { limited = true; return false; }
			remaining--;
			return true;
		}
	}
}
