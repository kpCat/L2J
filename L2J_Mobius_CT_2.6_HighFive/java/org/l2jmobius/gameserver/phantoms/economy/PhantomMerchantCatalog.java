/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.economy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.l2jmobius.gameserver.data.xml.ItemData;
import org.l2jmobius.gameserver.data.xml.RecipeData;
import org.l2jmobius.gameserver.model.item.ItemTemplate;
import org.l2jmobius.gameserver.model.item.type.ActionType;
import org.l2jmobius.gameserver.model.item.type.CrystalType;
import org.l2jmobius.gameserver.model.item.type.EtcItemType;

/** One bounded assortment view for native listings; no separate inventory or pricing formula. */
public final class PhantomMerchantCatalog
{
	public record Entry(ItemTemplate item, PhantomPrivateMarketPricingAuthority.ItemQuote quote, String category) { }
	private final List<Entry> _entries;

	public PhantomMerchantCatalog(PhantomPrivateMarketPricingAuthority pricing)
	{
		final Map<String, List<ItemTemplate>> groups = new TreeMap<>();
		for (var item : ItemData.getInstance().getAllItems())
		{
			if (item == null || item.getId() == 57 || !item.isTradeable() || item.isQuestItem() || item.isHeroItem() || item.getTime() != -1 || PhantomEpicItemPolicy.isEpic(item)) { continue; }
			final String category = category(item); if (category == null) { continue; }
			groups.computeIfAbsent(category + '.' + item.getCrystalType().getLevel(), _ -> new ArrayList<>()).add(item);
		}
		final var acceptedGroups = new ArrayList<List<Entry>>();
		for (var group : groups.entrySet())
		{
			final var accepted = new ArrayList<Entry>();
			// Zero-reference event templates must not hide ordinary source-backed resources and equipment.
			for (var item : group.getValue().stream().sorted(Comparator.comparingLong((ItemTemplate item) -> item.getReferencePrice() > 0 ? item.getReferencePrice() : Long.MAX_VALUE).thenComparingInt(ItemTemplate::getId)).toList())
			{
				final var quote = pricing.quoteItem(item.getId());
				if (!quote.priced()) { continue; }
				accepted.add(new Entry(item, quote, group.getKey()));
				if (accepted.size() == 128) { break; }
			}
			acceptedGroups.add(accepted);
		}
		_entries = selectEntries(acceptedGroups);
		if (_entries.isEmpty()) { throw new IllegalArgumentException("No source-priced native merchant assortment."); }
	}
	static List<Entry> selectEntries(List<? extends List<Entry>> acceptedGroups)
	{
		final var entries = new ArrayList<Entry>();
		for (int index = 0; index < 128 && entries.size() < 128; index++)
		{
			for (var group : acceptedGroups)
			{
				if (index < group.size() && entries.size() < 128) { entries.add(group.get(index)); }
			}
		}
		return List.copyOf(entries);
	}
	public List<Entry> entries() { return _entries; }
	public List<Entry> bootstrapEntries()
	{
		return _entries.stream().filter(entry -> entry.item().getCrystalType().getLevel() <= CrystalType.A.getLevel() && !PhantomEpicItemPolicy.isEpic(entry.item())).toList();
	}
	private static String category(ItemTemplate item)
	{
		if (item.isCommon()) { return "common"; }
		if (item.isWeapon()) { return "weapon"; }
		if (item.isArmor()) { return "armor"; }
		if (Arrays.stream(CrystalType.values()).anyMatch(type -> type.getCrystalId() == item.getId())) { return "crystal"; }
		if (RecipeData.getInstance().getRecipeByItemId(item.getId()) != null) { return "recipe"; }
		if (item.getItemType() == EtcItemType.MATERIAL) { return "material"; }
		if (item.getItemType().toString().contains("ENCHANT")) { return "enchant"; }
		if (item.getDefaultAction() == ActionType.SOULSHOT || item.getDefaultAction() == ActionType.SPIRITSHOT || item.getItemType() == EtcItemType.ARROW || item.getItemType() == EtcItemType.BOLT) { return "consumable"; }
		return null;
	}
}
