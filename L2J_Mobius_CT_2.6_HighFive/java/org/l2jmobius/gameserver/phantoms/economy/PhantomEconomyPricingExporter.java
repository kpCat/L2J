/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.economy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.l2jmobius.gameserver.config.custom.PhantomMarketConfig;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeAuthority;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.ChanceModel;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.DropFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.DropSourceKind;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.IngredientFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.RecipeFact;
import org.w3c.dom.Element;

/** Read-only DB-free XML adapter. All valuation arithmetic is shared with the runtime market. */
public final class PhantomEconomyPricingExporter
{
	private record Item(int id, long reference, long npcPayout, boolean stackable, boolean equipment, boolean jewel, String grade, boolean marketable, int crystals, int crystalId, boolean enchantInput) { }
	private record Quote(boolean priced, boolean bidAllowed, long bid, long ask, long fair, String reason) { }
	private final Path _root;
	private final Map<String, String> _hashes = new TreeMap<>();
	private final Map<Integer, Item> _items = new TreeMap<>();
	private final Map<Integer, List<DropFact>> _deathItem = new HashMap<>(), _spoilItem = new HashMap<>(), _deathNpc = new HashMap<>();
	private final Map<Integer, List<RecipeFact>> _recipes = new HashMap<>();
	private final Set<Integer> _spawned = new HashSet<>(), _ordinary = new HashSet<>(), _bosses = new HashSet<>();
	private final Map<Integer, Long> _npc = new HashMap<>();
	private final Map<Integer, Double> _source = new HashMap<>();
	private final Map<Integer, String> _reasons = new HashMap<>();
	private final Map<Integer, Quote> _quotes = new HashMap<>();
	private final PhantomAltBPriceCatalog _altB;
	private final PhantomEconomyValuation.Policy _policy;
	private final PhantomPrivateMarketQuote.Policy _spread;
	private final PhantomRateAwareLootFairValue.Rates _rates;
	private final PhantomRateAwareLootFairValue.Rates _bossRates;
	private final boolean _zeroSell;
	private final String _controlSummary;
	private final long _maxAdena;
	private final double _masterwork;
	private double _highJewelAnchor;

	private PhantomEconomyPricingExporter(Path root, Path profile, double adenaScale, double lootScale) throws Exception
	{
		_root = root.toAbsolutePath().normalize().toRealPath();
		_policy = PhantomMarketConfig.readValuation(profile);
		_spread = PhantomMarketConfig.read(profile);
		if (_policy == null || _spread == null || !Double.isFinite(adenaScale) || adenaScale <= 0 || !Double.isFinite(lootScale) || lootScale <= 0) { throw new IllegalArgumentException("Valid explicit pricing profile and rate scales required."); }
		_hashes.put("@profile", PhantomEconomyXml.hash(profile));
		for (Class<?> formula : List.of(PhantomEconomyValuation.class, PhantomPrivateMarketQuote.class, PhantomRateAwareLootFairValue.class, PhantomRecipeExpectedCost.class, PhantomEpicItemPolicy.class, PhantomAltBPriceCatalog.class, PhantomEconomyPricingExporter.class))
		{
			try (var stream = formula.getResourceAsStream(formula.getSimpleName() + ".class")) { _hashes.put("@formula:" + formula.getName(), java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes()))); }
		}
		properties("config/Custom/PhantomMarket.ini");
		final Properties community = properties("config/Custom/CommunityBoard.ini");
		final Properties general = properties("config/General.ini");
		_zeroSell = Boolean.parseBoolean(properties("config/Custom/MerchantZeroSellPrice.ini").getProperty("MerchantZeroSellPrice", "false"));
		_controlSummary = "Controls snapshot: CustomCommunityBoard=" + community.getProperty("CustomCommunityBoard", "False") + ";CustomMultisellLoad=" + general.getProperty("CustomMultisellLoad", "False") + "; candidates require these features enabled before native Alt+B proof";
		final Properties player = properties("config/Player.ini");
		_maxAdena = Long.parseLong(player.getProperty("MaxAdena", "3000000000000000"));
		_masterwork = Double.parseDouble(player.getProperty("CraftMasterworkChanceRate", "1"));
		final Properties rates = properties("config/Rates.ini");
		final Map<Integer, Double> amount = rates(rates.getProperty("DropAmountMultiplierByItemId", ""), lootScale);
		amount.put(57, amount.getOrDefault(57, 1d) / lootScale * adenaScale);
		_rates = new PhantomRateAwareLootFairValue.Rates(number(rates, "DeathDropChanceMultiplier", 1), number(rates, "DeathDropAmountMultiplier", 1) * lootScale, number(rates, "SpoilDropChanceMultiplier", 1), number(rates, "SpoilDropAmountMultiplier", 1) * lootScale, rates(rates.getProperty("DropChanceMultiplierByItemId", ""), 1), amount);
		_bossRates = new PhantomRateAwareLootFairValue.Rates(number(rates, "RaidDropChanceMultiplier", 1), number(rates, "RaidDropAmountMultiplier", 1) * lootScale, _rates.spoilChance(), _rates.spoilAmount(), _rates.chanceByItem(), _rates.amountByItem());
		for (Path file : files("data/stats/items")) { readItems(file); }
		for (Path file : files("data/spawns"))
		{
			final Element list = xml(file);
			if ("false".equalsIgnoreCase(list.getAttribute("enabled"))) { continue; }
			final var nodes = list.getElementsByTagName("npc");
			for (int index = 0; index < nodes.getLength(); index++) { final Element npc = (Element) nodes.item(index); if (!npc.hasAttribute("count") || Long.parseLong(npc.getAttribute("count")) > 0) { _spawned.add(Integer.parseInt(npc.getAttribute("id"))); } }
		}
		for (Path file : files("data/stats/npcs")) { readNpcs(file); }
		_ordinary.retainAll(_spawned);
		readRecipes();
		for (Path file : files("data/buylists")) { readBuylist(file); }
		_altB = PhantomAltBPriceCatalog.load(_root); _hashes.putAll(_altB.hashes());
		for (Item item : _items.values())
		{
			if (item.jewel() && Set.of("S", "S80", "S84").contains(item.grade()) && !PhantomEpicItemPolicy.isEpic(item.id())) { _highJewelAnchor = Math.max(_highJewelAnchor, Math.max(source(item.id()), craft(item.id()))); }
		}
	}

	private static double number(Properties values, String key, double fallback) { return Double.parseDouble(values.getProperty(key, Double.toString(fallback))); }
	private static Map<Integer, Double> rates(String text, double scale)
	{
		final Map<Integer, Double> result = new HashMap<>();
		for (String part : text.split(";")) { if (!part.isBlank()) { final String[] pair = part.trim().split(","); result.put(Integer.parseInt(pair[0]), Double.parseDouble(pair[1]) * scale); } }
		return result;
	}
	private List<Path> files(String relative) throws Exception
	{
		try (var paths = Files.walk(_root.resolve(relative))) { return paths.filter(path -> path.toString().endsWith(".xml")).sorted().toList(); }
	}
	private Element xml(Path file) throws Exception
	{
		PhantomEconomyXml.physical(_root, file); _hashes.put(_root.relativize(file).toString().replace('\\', '/'), PhantomEconomyXml.hash(file)); return PhantomEconomyXml.read(file);
	}
	private Properties properties(String relative) throws Exception
	{
		final Path file = PhantomEconomyXml.physical(_root, _root.resolve(relative)); _hashes.put(relative, PhantomEconomyXml.hash(file));
		final Properties result = new Properties(); try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { result.load(reader); } return result;
	}
	private void readItems(Path file) throws Exception
	{
		for (Element item : PhantomEconomyXml.children(xml(file), "item"))
		{
			final Map<String, String> values = new HashMap<>();
			for (Element setting : PhantomEconomyXml.children(item, "set")) { values.put(setting.getAttribute("name"), setting.getAttribute("val")); }
			final int id = Integer.parseInt(item.getAttribute("id"));
			final long reference = Long.parseLong(values.getOrDefault("price", "0"));
			final String grade = values.getOrDefault("crystal_type", "NONE");
			final int crystalId = switch (grade) { case "D" -> 1458; case "C" -> 1459; case "B" -> 1460; case "A" -> 1461; case "S", "S80", "S84" -> 1462; default -> 0; };
			final String body = values.getOrDefault("bodypart", "none");
			final boolean jewel = Set.of("neck", "rear;lear", "rfinger;lfinger", "rear", "lear", "rfinger", "lfinger").contains(body);
			final boolean marketable = !"false".equals(values.get("is_tradable")) && !"true".equals(values.get("is_questitem")) && "-1".equals(values.getOrDefault("time", "-1")) && "-1".equals(values.getOrDefault("duration", "-1"));
			_items.put(id, new Item(id, reference, _zeroSell || "false".equals(values.get("is_sellable")) ? 0 : reference / 2, "true".equals(values.get("is_stackable")), !"EtcItem".equals(item.getAttribute("type")), jewel, grade, marketable && "0".equals(values.getOrDefault("enchanted", "0")), Integer.parseInt(values.getOrDefault("crystal_count", "0")), crystalId, values.getOrDefault("etcitem_type", "").contains("ENCHANT")));
		}
	}
	private void readNpcs(Path file) throws Exception
	{
		for (Element npc : PhantomEconomyXml.children(xml(file), "npc"))
		{
			final int id = Integer.parseInt(npc.getAttribute("id"));
			final String type = npc.getAttribute("type");
			if (Set.of("GrandBoss", "RaidBoss").contains(type)) { _bosses.add(id); }
			else if (Set.of("Monster", "RiftInvader").contains(type)) { _ordinary.add(id); }
			for (Element lists : PhantomEconomyXml.children(npc, "dropLists"))
			{
				for (Element list : PhantomEconomyXml.children(lists, null))
				{
					final DropSourceKind kind = list.getTagName().equals("spoil") ? DropSourceKind.SPOIL : DropSourceKind.DEATH_DROP;
					int groupOrdinal = 0, itemOrdinal = 0;
					for (Element entry : PhantomEconomyXml.children(list, null))
					{
						if (entry.getTagName().equals("group"))
						{
							int ordinal = 0; for (Element line : PhantomEconomyXml.children(entry, "item")) { addDrop(id, line, kind, groupOrdinal, ordinal++, Double.parseDouble(entry.getAttribute("chance"))); } groupOrdinal++;
						}
						else if (entry.getTagName().equals("item")) { addDrop(id, entry, kind, -1, itemOrdinal++, 0); }
					}
				}
			}
		}
	}
	private void addDrop(int npc, Element line, DropSourceKind kind, int group, int ordinal, double groupChance)
	{
		final DropFact fact = new DropFact(npc, Integer.parseInt(line.getAttribute("id")), kind, group < 0 ? ChanceModel.UNGROUPED_INDEPENDENT : ChanceModel.GROUP_CUMULATIVE, group, ordinal, groupChance, Double.parseDouble(line.getAttribute("chance")), Long.parseLong(line.getAttribute("min")), Long.parseLong(line.getAttribute("max")), PhantomGameKnowledgeAuthority.SERVER_LOADER_FACT);
		(kind == DropSourceKind.SPOIL ? _spoilItem : _deathItem).computeIfAbsent(fact.itemId(), _ -> new ArrayList<>()).add(fact);
		if (kind == DropSourceKind.DEATH_DROP) { _deathNpc.computeIfAbsent(npc, _ -> new ArrayList<>()).add(fact); }
	}
	private void readRecipes() throws Exception
	{
		for (Element recipe : PhantomEconomyXml.children(xml(_root.resolve("data/Recipes.xml")), "item"))
		{
			final var production = PhantomEconomyXml.children(recipe, "production").getFirst();
			final var rare = PhantomEconomyXml.children(recipe, "productionRare");
			final List<IngredientFact> ingredients = PhantomEconomyXml.children(recipe, "ingredient").stream().map(part -> new IngredientFact(Integer.parseInt(part.getAttribute("id")), Long.parseLong(part.getAttribute("count")))).toList();
			final RecipeFact fact = new RecipeFact(Integer.parseInt(recipe.getAttribute("id")), Integer.parseInt(recipe.getAttribute("recipeId")), Integer.parseInt(production.getAttribute("id")), Long.parseLong(production.getAttribute("count")), rare.isEmpty() ? 0 : Integer.parseInt(rare.getFirst().getAttribute("id")), rare.isEmpty() ? 0 : Long.parseLong(rare.getFirst().getAttribute("count")), rare.isEmpty() ? 0 : Integer.parseInt(rare.getFirst().getAttribute("rarity")), Integer.parseInt(recipe.getAttribute("craftLevel")), Integer.parseInt(recipe.getAttribute("successRate")), "dwarven".equals(recipe.getAttribute("type")), ingredients, PhantomGameKnowledgeAuthority.SERVER_LOADER_FACT);
			_recipes.computeIfAbsent(fact.productItemId(), _ -> new ArrayList<>()).add(fact);
		}
	}
	private void readBuylist(Path file) throws Exception
	{
		final Element list = xml(file);
		if (PhantomEconomyXml.children(list, "npcs").isEmpty()) { return; }
		for (Element entry : PhantomEconomyXml.children(list, "item"))
		{
			final int id = Integer.parseInt(entry.getAttribute("id")); final Item item = _items.get(id);
			if (item == null || entry.hasAttribute("count")) { continue; }
			final long price = entry.hasAttribute("price") ? Long.parseLong(entry.getAttribute("price")) : item.reference();
			if (price > 0) { _npc.merge(id, price, Math::min); }
		}
	}
	private long liquidation(Item item)
	{
		final Item crystal = _items.get(item.crystalId());
		return PhantomEconomyValuation.liquidationFloor(item.npcPayout(), item.crystals(), crystal == null ? 0 : crystal.npcPayout());
	}
	private double source(int id)
	{
		return _source.computeIfAbsent(id, key ->
		{
			final Item item = _items.get(key); if (item == null) { return 0d; }
			PhantomRateAwareLootFairValue.Quote loot = null;
			try { loot = PhantomRateAwareLootFairValue.quote(id, _deathItem, _spoilItem, _deathNpc, PhantomEpicItemPolicy.isEpic(id) ? _bosses : _ordinary, PhantomEpicItemPolicy.isEpic(id) ? _bossRates : _rates); } catch (IllegalArgumentException absent) { }
			final boolean npc = _npc.containsKey(id);
			if (item.enchantInput() && loot == null && !npc) { _reasons.put(id, "UNPRICED:enchant input source absent"); return 0d; }
			try
			{
				final var value = PhantomEconomyValuation.source(liquidation(item), item.equipment(), PhantomEpicItemPolicy.isEpic(id), _highJewelAnchor, new PhantomNpcAcquisitionPrice.Quote(npc, true, _npc.getOrDefault(id, 0L), "npc.catalog.untaxed_lower_bound"), loot, _policy);
				_reasons.put(id, value.reason()); return value.fair();
			}
			catch (IllegalArgumentException absent) { _reasons.put(id, absent.getMessage()); return 0d; }
		});
	}
	private long craft(int id)
	{
		try { return checked(PhantomRecipeExpectedCost.floor(id, _recipes, this::source, _ -> 0, _masterwork).perItemCost()); } catch (IllegalArgumentException absent) { return 0; }
	}
	private long checked(double value)
	{
		if (!Double.isFinite(value) || value < 1 || value > Math.min(1e15, _maxAdena)) { throw new IllegalArgumentException("UNPRICED:Adena value outside boundary"); } return (long) Math.ceil(value);
	}
	private Quote quote(int id)
	{
		return _quotes.computeIfAbsent(id, key ->
		{
			try
			{
				final Item item = _items.get(key); if (item == null || !item.marketable() || id == 57) { throw new IllegalArgumentException("unknown or restricted template"); }
				final double direct = source(key); final long craft = craft(key);
				if ((direct <= 0 || _reasons.getOrDefault(key, "").equals("liquidation.opportunity_only")) && craft <= 0) { throw new IllegalArgumentException("no acquisition source; " + _reasons.getOrDefault(key, "absent")); }
				final long fair = checked(Math.max(Math.max(direct, craft), liquidation(item)));
				long ceiling = _npc.getOrDefault(key, Long.MAX_VALUE); final long altB = _altB.acquisitionCeiling(key);
				if (craft > 0) { ceiling = Math.min(ceiling, craft); } if (altB >= 0) { ceiling = Math.min(ceiling, altB); }
				final var value = PhantomPrivateMarketQuote.quote(new PhantomPrivateMarketQuote.Inputs(fair, liquidation(item), ceiling != Long.MAX_VALUE, ceiling == Long.MAX_VALUE ? 0 : ceiling, craft, 0, _maxAdena), _spread);
				return new Quote(true, item.stackable() && value.buyAllowed(), item.stackable() && value.buyAllowed() ? value.bid() : 0, value.ask(), fair, _reasons.getOrDefault(key, "recipe.bom") + (craft > 0 ? "+recipe.expected_cost" : ""));
			}
			catch (RuntimeException unsafe) { return new Quote(false, false, 0, 0, 0, "UNPRICED:" + unsafe.getMessage()); }
		});
	}
	private static String cell(Object text) { return text.toString().replace('\t', ' ').replace('\r', ' ').replace('\n', ' '); }
	private static void write(Path output, String name, List<String> lines) throws Exception { Files.writeString(output.resolve(name), String.join("\n", lines) + "\n", StandardCharsets.UTF_8); }
	private void export(Path output, String inspectIds) throws Exception
	{
		Files.createDirectories(output);
		final Set<Integer> ids = new TreeSet<>();
		_altB.offers().forEach(offer -> ids.addAll(offer.products().keySet()));
		for (Item item : _items.values()) { if (PhantomEpicItemPolicy.isEpic(item.id()) || Set.of(1458, 1459, 1460, 1461, 1462).contains(item.id())) { ids.add(item.id()); } }
		for (String id : inspectIds.split(",")) { if (!id.isBlank()) { ids.add(Integer.parseInt(id)); } }
		final List<String> quotes = new ArrayList<>(List.of("item_id\tpriced\tbid_allowed\tbid\task\tfair\tsource_reason"));
		for (int id : ids) { final Quote value = quote(id); quotes.add(id + "\t" + value.priced() + "\t" + value.bidAllowed() + "\t" + value.bid() + "\t" + value.ask() + "\t" + value.fair() + "\t" + cell(value.reason())); }
		final List<String> offers = new ArrayList<>(List.of("path\tkind\tordinal\told_price\tnew_price\tstatus\tliquidation\tprivate_bid\treason"));
		for (var offer : _altB.offers())
		{
			long price = 0, payout = 0, bid = 0; String reason = offer.reason(); boolean supported = offer.supported();
			try
			{
				for (var product : new TreeMap<>(offer.products()).entrySet())
				{
					final Quote value = quote(product.getKey()); if (!value.priced()) { supported = false; reason = value.reason(); break; }
					price = Math.addExact(price, Math.multiplyExact(value.ask(), product.getValue()));
					payout = Math.addExact(payout, Math.multiplyExact(liquidation(_items.get(product.getKey())), product.getValue()));
					bid = Math.addExact(bid, Math.multiplyExact(value.bid(), product.getValue()));
				}
				if (supported && (price > _maxAdena || price <= payout || price <= bid)) { throw new IllegalArgumentException("bounded closed cycle or MaxAdena conflict"); }
			}
			catch (RuntimeException unsafe) { supported = false; reason = "UNPRICED:" + unsafe.getMessage(); }
			offers.add(offer.path() + "\t" + offer.kind() + "\t" + offer.ordinal() + "\t" + offer.oldPrice() + "\t" + (supported ? price : 0) + "\t" + (supported ? "PRICED" : "UNPRICED") + "\t" + payout + "\t" + bid + "\t" + cell(reason));
		}
		final List<String> hashes = new ArrayList<>(List.of("path\tsha256")); _hashes.forEach((path, hash) -> hashes.add(path + "\t" + hash));
		write(output, "quotes.tsv", quotes); write(output, "offers.tsv", offers); write(output, "sources.tsv", hashes);
		write(output, "unknown.txt", java.util.stream.Stream.concat(_altB.unknown().stream(), java.util.stream.Stream.of(_controlSummary, "NPC tax: offline catalog base is a conservative untaxed lower bound; native Merchant tax is rechecked by runtime", "Dynamic NPC shops/quests/non-Adena exchanges/enchant conversions and arbitrary longer graph cycles are outside this bounded proof", "Grouped occurrence caps can reduce realized yield; authored target time is an explicit model, not measured class combat throughput")).toList());
	}

	public static void main(String[] args) throws Exception
	{
		if (args.length != 6) { throw new IllegalArgumentException("Usage: PhantomEconomyPricingExporter <game-root> <profile.ini> <output-dir> <adena-scale> <loot-scale> <inspect-ids>"); }
		new PhantomEconomyPricingExporter(Path.of(args[0]), Path.of(args[1]), Double.parseDouble(args[3]), Double.parseDouble(args[4])).export(Path.of(args[2]), args[5]);
	}
}
