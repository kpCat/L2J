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
import java.util.regex.Pattern;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeAuthority;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.ChanceModel;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.DropFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.DropSourceKind;
import org.w3c.dom.Element;

/** DB-free source inventory and explicit TASK070 price payloads. Never writes the selected game. */
public final class PhantomEconomy070CatalogExporter
{
	private static final String HEADER = "key\tcategory\titemId\tname\tpath\tordinal\tcurrencyId\tbaseX1\tsecondaryCurrencyId\tsecondaryBaseX1\tcount\trestockDays\tbasis\tprovenance\trequirements";
	private static final List<Integer> EPICS = List.of(6656, 6657, 6658, 6659, 6660, 6661, 6662, 8191, 10314, 16025, 16026, 21712);
	private static final List<String> XMLS = List.of("data/multisell/custom/600012.xml", "data/multisell/custom/600024.xml", "data/multisell/91002.xml", "data/multisell/91003.xml", "data/multisell/91005.xml", "data/multisell/91006.xml");
	private record Item(String name, String type, String slot, String grade, long reference) { }
	private final Path _game;
	private final Path _output;
	private final Map<String, String> _hashes = new TreeMap<>();
	private final Map<Integer, Item> _items = new TreeMap<>();
	private final Map<Integer, List<DropFact>> _drops = new HashMap<>();
	private final Map<Integer, String> _bossNames = new TreeMap<>();
	private final Map<Integer, String> _bossPaths = new TreeMap<>();
	private final Set<Integer> _spawned = new HashSet<>();
	private final Set<Integer> _ordinary = new HashSet<>();
	private final Properties _policy;
	private final Properties _rates;
	private final PhantomRateAwareLootFairValue.Rates _actual;
	private final PhantomRateAwareLootFairValue.Rates _x1 = new PhantomRateAwareLootFairValue.Rates(1, 1, 1, 1, Map.of(), Map.of());
	private final long _maxAdena;

	private PhantomEconomy070CatalogExporter(Path game, Path output) throws Exception
	{
		_game = game.toAbsolutePath().normalize().toRealPath();
		_output = output.toAbsolutePath().normalize();
		if (_output.startsWith(_game)) { throw new IllegalArgumentException("Pricing output must be outside selected game."); }
		Files.createDirectories(_output);
		_policy = properties("data/economy070/price-policy070.properties");
		final Path questContract = PhantomEconomyXml.physical(_game, _game.resolve("data/economy070/quest-contract070.tsv"));
		_hashes.put("data/economy070/quest-contract070.tsv", PhantomEconomyXml.hash(questContract));
		try (var stream = PhantomEconomy070CatalogExporter.class.getResourceAsStream("PhantomEconomy070CatalogExporter.class")) { _hashes.put("@formula:" + PhantomEconomy070CatalogExporter.class.getName(), java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes()))); }
		_rates = properties("config/Rates.ini");
		properties("config/Custom/PremiumSystem.ini");
		properties("config/Custom/PhantomMarket.ini");
		_maxAdena = Long.parseLong(properties("config/Player.ini").getProperty("MaxAdena", "3000000000000000"));
		_actual = new PhantomRateAwareLootFairValue.Rates(number(_rates, "DeathDropChanceMultiplier"), number(_rates, "DeathDropAmountMultiplier"), number(_rates, "SpoilDropChanceMultiplier"), number(_rates, "SpoilDropAmountMultiplier"), overrides("DropChanceMultiplierByItemId"), overrides("DropAmountMultiplierByItemId"));
		for (Path file : files("data/stats/items"))
		{
			for (Element item : PhantomEconomyXml.children(xml(file), "item"))
			{
				final Map<String, String> values = new HashMap<>();
				for (Element setting : PhantomEconomyXml.children(item, "set")) { values.put(setting.getAttribute("name"), setting.getAttribute("val")); }
				_items.put(Integer.parseInt(item.getAttribute("id")), new Item(item.getAttribute("name"), item.getAttribute("type"), values.getOrDefault("bodypart", ""), values.getOrDefault("crystal_type", "NONE"), Long.parseLong(values.getOrDefault("price", "0"))));
			}
		}
		for (Path file : files("data/spawns"))
		{
			final Element list = xml(file);
			if ("false".equalsIgnoreCase(list.getAttribute("enabled"))) { continue; }
			final var nodes = list.getElementsByTagName("npc");
			for (int index = 0; index < nodes.getLength(); index++) { final Element npc = (Element) nodes.item(index); if (!npc.hasAttribute("count") || Long.parseLong(npc.getAttribute("count")) > 0) { _spawned.add(Integer.parseInt(npc.getAttribute("id"))); } }
		}
		for (Path file : files("data/stats/npcs"))
		{
			for (Element npc : PhantomEconomyXml.children(xml(file), "npc"))
			{
				final int id = Integer.parseInt(npc.getAttribute("id"));
				if (Set.of("GrandBoss", "RaidBoss").contains(npc.getAttribute("type"))) { _bossNames.put(id, npc.getAttribute("name")); _bossPaths.put(id, relative(file)); }
				if (Set.of("Monster", "RiftInvader").contains(npc.getAttribute("type"))) { _ordinary.add(id); }
				for (Element lists : PhantomEconomyXml.children(npc, "dropLists"))
				{
					for (Element list : PhantomEconomyXml.children(lists, "drop"))
					{
						int group = 0, ordinal = 0;
						for (Element line : PhantomEconomyXml.children(list, null))
						{
							if (line.getTagName().equals("group")) { int position = 0; for (Element entry : PhantomEconomyXml.children(line, "item")) { addDrop(id, entry, group, position++, Double.parseDouble(line.getAttribute("chance"))); } group++; }
							else if (line.getTagName().equals("item")) { addDrop(id, line, -1, ordinal++, 0); }
						}
					}
				}
			}
		}
		_ordinary.retainAll(_spawned);
	}

	private void addDrop(int npc, Element item, int group, int ordinal, double groupChance)
	{
		_drops.computeIfAbsent(npc, _ -> new ArrayList<>()).add(new DropFact(npc, Integer.parseInt(item.getAttribute("id")), DropSourceKind.DEATH_DROP, group < 0 ? ChanceModel.UNGROUPED_INDEPENDENT : ChanceModel.GROUP_CUMULATIVE, group, ordinal, groupChance, Double.parseDouble(item.getAttribute("chance")), Long.parseLong(item.getAttribute("min")), Long.parseLong(item.getAttribute("max")), PhantomGameKnowledgeAuthority.SERVER_LOADER_FACT));
	}
	private List<Path> files(String path) throws Exception { try (var stream = Files.walk(_game.resolve(path))) { return stream.filter(file -> file.toString().endsWith(".xml")).sorted().toList(); } }
	private String relative(Path file) { return _game.relativize(file).toString().replace('\\', '/'); }
	private Element xml(Path file) throws Exception { PhantomEconomyXml.physical(_game, file); _hashes.put(relative(file), PhantomEconomyXml.hash(file)); return PhantomEconomyXml.read(file); }
	private Properties properties(String path) throws Exception
	{
		final Path file = PhantomEconomyXml.physical(_game, _game.resolve(path)); _hashes.put(path, PhantomEconomyXml.hash(file));
		final Properties result = new Properties(); try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { result.load(reader); } return result;
	}
	private static double number(Properties properties, String key) { return Double.parseDouble(properties.getProperty(key, "1")); }
	private Map<Integer, Double> overrides(String key)
	{
		final Map<Integer, Double> result = new HashMap<>();
		for (String pair : _rates.getProperty(key, "").split(";")) { if (!pair.isBlank()) { final String[] split = pair.trim().split(","); result.put(Integer.parseInt(split[0]), Double.parseDouble(split[1])); } }
		return result;
	}
	private long policy(String key) { final long value = Long.parseLong(_policy.getProperty(key)); if (value < 1) { throw new IllegalArgumentException("Positive explicit policy required: " + key); } return value; }
	private static String cell(Object value) { return value.toString().replace('\t', ' ').replace('\r', ' ').replace('\n', ' '); }
	private static void write(Path root, String path, List<String> lines) throws Exception { final Path file = root.resolve(path); Files.createDirectories(file.getParent()); Files.writeString(file, String.join("\n", lines) + "\n", StandardCharsets.UTF_8); }
	private double multiplier(Set<Integer> items, boolean seals)
	{
		double baseline = 0, actual = 0;
		for (int npc : _ordinary)
		{
			for (DropFact fact : _drops.getOrDefault(npc, List.of()))
			{
				if (!items.contains(fact.itemId())) { continue; }
				final int weight = !seals ? 1 : switch (fact.itemId()) { case 6360 -> 3; case 6361 -> 5; case 6362 -> 10; default -> throw new IllegalArgumentException(); };
				baseline += weight * PhantomRateAwareLootFairValue.expectedYield(fact, _drops.get(npc), _x1);
				actual += weight * PhantomRateAwareLootFairValue.expectedYield(fact, _drops.get(npc), _actual);
			}
		}
		if (!Double.isFinite(actual) || baseline <= 0 || actual <= 0) { throw new IllegalArgumentException("No positive stock acquisition multiplier for " + items); }
		return actual / baseline;
	}
	private static long scaled(long base, double factor)
	{
		final double value = Math.ceil(base * factor);
		if (!Double.isFinite(value) || value < 1 || value > 9_000_000_000_000_000L) { throw new IllegalArgumentException("Price overflow or suppressed acquisition."); }
		return (long) value;
	}
	private record BossSource(int npc, double yield, String provenance) { }
	private BossSource bossSource(int itemId, PhantomRateAwareLootFairValue.Rates rates)
	{
		if (itemId == 10314)
		{
			try
			{
				final String relative = "data/scripts/ai/bosses/Beleth/Beleth.java";
				final Path file = PhantomEconomyXml.physical(_game, _game.resolve(relative));
				final String script = Files.readString(file, StandardCharsets.UTF_8);
				if (!script.contains("new ItemHolder(10314, 1)") || !script.contains("player.getObjectId() == _killer.getObjectId()") || !script.contains("giveItems(player, RING)")) { throw new IllegalArgumentException("Beleth scripted reward source changed."); }
				_hashes.put(relative, PhantomEconomyXml.hash(file));
				return new BossSource(29118, 1, relative + "#RING10314;onTalk killer-only;one reward after real Beleth kill;SCRIPT_REWARD_NOT_RATE_SCALED_DROP");
			}
			catch (Exception absent) { throw new IllegalArgumentException("UNPRICED Beleth scripted source unavailable.", absent); }
		}
		BossSource best = null;
		for (int npc : _bossNames.keySet())
		{
			// The active Antharas script spawns29068;29019 is a legacy template, not this encounter.
			if (itemId == 6656 && npc != 29068) { continue; }
			for (DropFact fact : _drops.getOrDefault(npc, List.of()))
			{
				if (fact.itemId() != itemId) { continue; }
				final double yield = PhantomRateAwareLootFairValue.expectedYield(fact, _drops.get(npc), rates);
				if (yield > 0 && (best == null || yield > best.yield())) { best = new BossSource(npc, yield, _bossPaths.get(npc) + "#npc=" + npc + ";name=" + _bossNames.get(npc) + ";groupChance=" + fact.rawGroupChance() + ";itemChance=" + fact.rawItemChance() + ";min=" + fact.minimumCount() + ";max=" + fact.maximumCount()); }
			}
		}
		if (best == null) { throw new IllegalArgumentException("UNPRICED epic has no stock boss source: " + itemId); }
		return best;
	}
	private int restock(int id) { return Set.of(6660, 6661, 6662).contains(id) ? 14 : Set.of(6659, 6658, 8191, 16025).contains(id) ? 30 : 60; }
	private long tierFloor(int id) { return policy("epic.floor." + restock(id)); }
	private String encounter(int id) throws Exception
	{
		if (id == 6656)
		{
			final String path = "data/scripts/ai/bosses/Antharas/Antharas.java";
			final Path script = PhantomEconomyXml.physical(_game, _game.resolve(path));
			if (!Files.readString(script, StandardCharsets.UTF_8).contains("ANTHARAS_NPC_ID = 29068")) { throw new IllegalArgumentException("Active Antharas source changed."); }
			_hashes.put(path, PhantomEconomyXml.hash(script));
		}
		final String instance = switch (id) { case 6659 -> "CavernOfThePirateCaptainWorldNight60.xml"; case 8191 -> "LastImperialTomb.xml"; case 16025 -> "IceQueensCastleBattleEasy.xml"; case 16026 -> "IceQueensCastleBattleHardcore.xml"; case 21712 -> "CavernOfThePirateCaptainWorldDay83.xml"; default -> ""; };
		if (!instance.isEmpty())
		{
			final String path = "data/instances/" + instance;
			final Element data = xml(_game.resolve(path));
			final var resets = data.getElementsByTagName("reset"); final List<String> times = new ArrayList<>();
			for (int index = 0; index < resets.getLength(); index++) { final Element reset = (Element) resets.item(index); times.add(reset.getAttribute("day") + " " + reset.getAttribute("hour") + ":" + reset.getAttribute("minute")); }
			return path + ";template=" + data.getAttribute("id") + ";reuseResets=" + times + ";native entry eligibility remains";
		}
		final String boss = switch (id) { case 6656 -> "Antharas"; case 6657 -> "Valakas"; case 6658 -> "Baium"; case 6660 -> "QueenAnt"; case 6661 -> "Orfen"; case 6662 -> "Core"; case 10314 -> "Beleth"; default -> throw new IllegalArgumentException("Unknown epic encounter."); };
		final Properties values = properties("config/GrandBoss.ini");
		return "config/GrandBoss.ini;IntervalOf" + boss + "Spawn=" + values.getProperty("IntervalOf" + boss + "Spawn") + "h;RandomOf" + boss + "Spawn=" + values.getProperty("RandomOf" + boss + "Spawn") + "h;native event eligibility remains";
	}
	private Map<Integer, Long> sharedFair() throws Exception
	{
		final Set<Integer> inspect = new TreeSet<>(EPICS);
		_items.forEach((id, item) -> { if (Set.of("S80", "S84").contains(item.grade()) && (item.slot().contains("finger") || item.slot().contains("ear") || item.slot().equals("neck"))) { inspect.add(id); } });
		PhantomEconomyPricingExporter.main(new String[] { _game.toString(), _game.resolve("config/Custom/PhantomMarket.ini").toString(), _output.resolve("shared-fair").toString(), "1", "1", inspect.stream().map(Object::toString).collect(java.util.stream.Collectors.joining(",")) });
		final Map<Integer, Long> result = new TreeMap<>();
		for (String line : Files.readAllLines(_output.resolve("shared-fair/quotes.tsv"), StandardCharsets.UTF_8).subList(1, Files.readAllLines(_output.resolve("shared-fair/quotes.tsv")).size()))
		{
			final String[] row = line.split("\t", -1); if (row[1].equals("true")) { result.put(Integer.parseInt(row[0]), Long.parseLong(row[5])); }
		}
		for (String line : Files.readAllLines(_output.resolve("shared-fair/sources.tsv"), StandardCharsets.UTF_8).subList(1, Files.readAllLines(_output.resolve("shared-fair/sources.tsv")).size())) { final String[] row = line.split("\t", -1); _hashes.put(row[0], row[1]); }
		return result;
	}
	private long jewelSetFloor(Map<Integer, Long> fair)
	{
		long neck = 0, ear = 0, ring = 0;
		for (var row : fair.entrySet())
		{
			final Item item = _items.get(row.getKey());
			if (item == null || !Set.of("S80", "S84").contains(item.grade()) || PhantomEpicItemPolicy.isEpic(row.getKey())) { continue; }
			if (item.slot().equals("neck")) { neck = Math.max(neck, row.getValue()); }
			else if (item.slot().contains("ear")) { ear = Math.max(ear, row.getValue()); }
			else if (item.slot().contains("finger")) { ring = Math.max(ring, row.getValue()); }
		}
		if (neck <= 0 || ear <= 0 || ring <= 0) { throw new IllegalArgumentException("Source-priced ordinary S80/S84 full jewel set absent."); }
		return Math.multiplyExact(Math.addExact(neck, Math.multiplyExact(2, Math.addExact(ear, ring))), policy("epic.fullSetFactor"));
	}
	private void baseline() throws Exception
	{
		if (Math.abs(multiplier(Set.of(57), false) - 1) > 0.00000001 || Math.abs(multiplier(Set.of(6360, 6361, 6362), true) - 1) > 0.00000001) { throw new IllegalArgumentException("Canonical first capture requires economic x1 source rates."); }
		if (Files.exists(_output.resolve("baseline.properties"))) { throw new IllegalArgumentException("Canonical baseline already exists; never recapture applied prices."); }
		final Map<Integer, Long> fair = sharedFair();
		final long fullSetFloor = jewelSetFloor(fair);
		final List<String> catalog = new ArrayList<>(List.of(HEADER));
		for (String path : XMLS)
		{
			int ordinal = 0;
			for (Element offer : PhantomEconomyXml.children(xml(_game.resolve(path)), "item"))
			{
				ordinal++;
				final Element product = PhantomEconomyXml.children(offer, "production").getFirst();
				final int id = Integer.parseInt(product.getAttribute("id"));
				final List<Element> ingredients = PhantomEconomyXml.children(offer, "ingredient");
				final Element primary = ingredients.getFirst();
				final int currency = Integer.parseInt(primary.getAttribute("id"));
				final long old = Long.parseLong(primary.getAttribute("count"));
				final String category = path.contains("600024") || path.contains("91002") ? "quest" : path.contains("91005") ? "codex" : path.contains("91006") ? "epaulette" : path.contains("91003") ? "mana" : "scroll";
				final long base = Long.parseLong(_policy.getProperty(category + "." + id, Long.toString(old)));
				final int secondary = ingredients.size() == 2 ? Integer.parseInt(ingredients.get(1).getAttribute("id")) : 0;
				final long secondaryBase = secondary == 0 ? 0 : Long.parseLong(ingredients.get(1).getAttribute("count"));
				if (ingredients.size() > 2 || base <= 0 || _items.get(id) == null) { throw new IllegalArgumentException("Unrecognized native offer: " + path + ":" + ordinal); }
				catalog.add(path.substring(path.lastIndexOf('/') + 1).replace(".xml", "") + "." + ordinal + "\t" + category + "\t" + id + "\t" + cell(_items.get(id).name()) + "\t" + path + "\t" + ordinal + "\t" + currency + "\t" + base + "\t" + secondary + "\t" + secondaryBase + "\t" + product.getAttribute("count") + "\t0\t" + (category.equals("quest") ? "AUTHOR_TIME_FLOOR_NOT_MEASURED" : category.equals("codex") ? "DESIGN_DEFAULT_NOT_STOCK" : "CANONICAL_NATIVE_OR_OWNER_BASE") + "\t" + path + ";original=" + old + ";sourceSHA=" + _hashes.get(path) + "\t" + requirements(id, category));
			}
		}
		catalog.add("premium.day\tpremium\t0\tПремиум на сутки\t-\t0\t57\t" + policy("premium.day") + "\t0\t0\t1\t0\tOWNER_BASE_ADENA\tPRODUCT_DECISIONS.md\tAccount duration; native premium owner");
		catalog.add("subclass.gateway\tquest-service\t0\tПодготовка доступа к подклассу\t-\t0\t57\t" + policy("subclass.gateway") + "\t0\t0\t1\t0\tAUTHOR_MAJOR_RAID_QUEST_TIME_FLOOR_NOT_MEASURED\tVillageMaster.checkQuests;Q234;Q235\tPaid completion service; level75 and native class/subclass validators remain; durable receipt required");
		for (int id : EPICS)
		{
			final BossSource source = bossSource(id, _x1);
			final long sourceFair = fair.getOrDefault(id, 0L);
			final long rarity = scaled(policy("epic.attemptBudget." + restock(id)), 1 / source.yield());
			final long base = Math.max(Math.max(tierFloor(id), fullSetFloor), Math.max(Math.multiplyExact(sourceFair, policy("epic.fairFactor")), rarity));
			catalog.add("epic." + id + "\tepic\t" + id + "\t" + cell(_items.get(id).name()) + "\t-\t0\t57\t" + base + "\t0\t0\t1\t" + restock(id) + "\t" + (sourceFair > 0 ? "SHARED_FAIR_X5_SET_X10_AUTHOR_TIER" : "AUTHOR_RARITY_OPPORTUNITY_SET_X10_TIER_NO_PAIRED_ADENA") + "\t" + cell(source.provenance()) + ";sharedFair=" + sourceFair + ";setFloor=" + fullSetFloor + ";rarityOpportunity=" + rarity + "\tGlobal stock1; native account savings; no restart reseed");
		}
		write(_output, "STORE_CATALOG070.tsv", catalog);
		write(_output, "baseline.properties", List.of("version=1", "owner=TASK070_ECONOMY_QOL", "gameRoot=" + _game.toString().replace('\\', '/'), "catalogSHA256=" + PhantomEconomyXml.hash(_output.resolve("STORE_CATALOG070.tsv"))));
		writeSources();
	}
	private static String requirements(int id, String category)
	{
		if (!category.equals("quest")) { return category.equals("epaulette") ? "Stock fortress dual costs preserved; no ordinary gear" : "Native item transaction"; }
		return switch (id)
		{
			case 1419 -> "Clan level3: leader+SP; item alone is not clan upgrade";
			case 3874 -> "Clan level4: leader+SP; Q501 item; native upgrade consumes1";
			case 3870 -> "Clan level5: leader+SP; Q503 item; native upgrade consumes1";
			case 4295 -> "Baium entry: native boss state; consumes cloth1; not subclass quest completion";
			case 3865 -> "Antharas entry: native boss state/capacity/party leader; checks stone possession, DOES NOT consume it in this source";
			case 15307, 15308, 15309 -> "Native healer class/skill-link requirements remain; token is not class transfer";
			case 7215, 7225 -> "Native faction level/state and exclusivity remain; bought token alone does not set quest state";
			default -> "Native quest/instance level and state remain; item issuance is not quest completion";
		};
	}
	private void writeSources() throws Exception
	{
		final List<String> sources = new ArrayList<>(List.of("path\tsha256"));
		_hashes.forEach((path, hash) -> sources.add(path + "\t" + hash));
		write(_output, "sources.tsv", sources);
	}
	private void mammon() throws Exception
	{
		final Map<Integer, String> services = new TreeMap<>(Map.ofEntries(Map.entry(311262501, "S-grade SA"), Map.entry(311262502, "A-grade duals"), Map.entry(311262503, "S-grade duals"), Map.entry(311262504, "S-grade armor unseal"), Map.entry(311262505, "S-grade accessories unseal"), Map.entry(311262509, "SA removal"), Map.entry(311262510, "A-grade SA"), Map.entry(311262511, "Weapon upgrade"), Map.entry(311262512, "D-B weapon exchange"), Map.entry(311262513, "Foundation completion"), Map.entry(311262516, "Rare upper armor conversion"), Map.entry(311262519, "A weapon exchange")));
		final List<String> rows = new ArrayList<>(List.of("listId\tservice\tnativeNpc\tofferCount\tpaymentUnits\taccess\tprovenance\tlimitation"));
		for (var service : services.entrySet())
		{
			final String path = "data/multisell/" + service.getKey() + ".xml";
			final Element list = xml(_game.resolve(path));
			final Set<Integer> currencies = new TreeSet<>();
			for (Element offer : PhantomEconomyXml.children(list, "item")) { for (Element ingredient : PhantomEconomyXml.children(offer, "ingredient")) { final int id = Integer.parseInt(ingredient.getAttribute("id")); if (Set.of(57, 5575, 1458, 1459, 1460, 1461, 1462, 2130, 2131, 2132, 2133, 2134).contains(id)) { currencies.add(id); } } }
			rows.add(service.getKey() + "\t" + service.getValue() + "\t31126\t" + PhantomEconomyXml.children(list, "item").size() + "\t" + currencies + ";owned native input items\tNATIVE_NPC_ONLY_UNLESS_EXPLICIT_SAFE_WHITELIST\t" + path + ";SHA=" + _hashes.get(path) + "\tNo forged npc object bypass; native owned inputs, grade, crystals/gems/AA and SevenSigns conditions remain");
		}
		for (String path : List.of("data/html/seven_signs/mammblack_1.htm", "data/html/seven_signs/mammblack_2a.htm", "data/html/seven_signs/mammblack_2b.htm", "data/html/trainer/30300.htm")) { _hashes.put(path, PhantomEconomyXml.hash(PhantomEconomyXml.physical(_game, _game.resolve(path)))); }
		rows.add("91004\tSeal Stone exchange3/5/10\t30300 Pushkin Giran\t3\t6360/6361/6362 ->5575\tCB_AND_PHYSICAL_GIRAN_BLACKSMITH\tdata/multisell/91004.xml;data/spawns/Others/22_22.xml;data/html/trainer/30300.htm\tNative inventory/distance; independent of SevenSigns phase");
		xml(_game.resolve("data/multisell/91004.xml"));
		write(_output, "MAMMON_SERVICES070.tsv", rows);
		final Element fortress = xml(_game.resolve("data/multisell/90001000.xml"));
		final List<String> epa = new ArrayList<>(List.of("itemId\tname\t9912CostX1\tAdenaCostX1\tnativeSource\tnativeNpc\tnativeSourceSHA\taltBList\tstatus"));
		for (int index = 0; index < 3; index++)
		{
			final Element offer = PhantomEconomyXml.children(fortress, "item").get(index);
			final var input = PhantomEconomyXml.children(offer, "ingredient"); final Element product = PhantomEconomyXml.children(offer, "production").getFirst(); final int id = Integer.parseInt(product.getAttribute("id"));
			if (!input.get(0).getAttribute("id").equals("9912") || !input.get(1).getAttribute("id").equals("57") || !Set.of(9625, 9626, 9627).contains(id)) { throw new IllegalArgumentException("Fortress native source changed."); }
			epa.add(id + "\t" + _items.get(id).name() + "\t" + input.get(0).getAttribute("count") + "\t" + input.get(1).getAttribute("count") + "\tdata/multisell/90001000.xml#" + (index + 1) + "\t35659 and20other fortress suspicious merchants\t" + _hashes.get("data/multisell/90001000.xml") + "\t91006\tEXACT_DUAL_COSTS_NO_GEAR");
		}
		write(_output, "EPOLETTE_SHOP070.tsv", epa);
	}
	private void plan(Path baseline) throws Exception
	{
		final Properties binding = new Properties();
		try (var reader = Files.newBufferedReader(baseline.resolve("baseline.properties"), StandardCharsets.UTF_8)) { binding.load(reader); }
		final Path canonical = baseline.resolve("STORE_CATALOG070.tsv");
		if (!"TASK070_ECONOMY_QOL".equals(binding.getProperty("owner")) || !PhantomEconomyXml.hash(canonical).equals(binding.getProperty("catalogSHA256")) || !Path.of(binding.getProperty("gameRoot")).toRealPath().equals(_game)) { throw new IllegalArgumentException("Canonical owner/root/hash differs."); }
		_hashes.put("@baseline:STORE_CATALOG070.tsv", PhantomEconomyXml.hash(canonical));
		_hashes.put("@baseline:baseline.properties", PhantomEconomyXml.hash(baseline.resolve("baseline.properties")));
		final double adena = multiplier(Set.of(57), false), aa = multiplier(Set.of(6360, 6361, 6362), true);
		final Map<Integer, Long> fair = sharedFair();
		final long setFloor = jewelSetFloor(fair);
		final List<String> rows = Files.readAllLines(canonical, StandardCharsets.UTF_8);
		if (!rows.getFirst().equals(HEADER)) { throw new IllegalArgumentException("Canonical schema differs."); }
		final List<String> pricing = new ArrayList<>(List.of("key\titemId\tcurrencyId\tprice\trestockDays\tname"));
		final List<String> report = new ArrayList<>(List.of("key\titemId\tcurrencyId\tcurrent\tbaseX1\ttarget\tsecondaryCurrencyId\tsecondaryCurrent\tsecondaryTarget\tbasis\tprovenance"));
		final List<String> epic = new ArrayList<>(List.of("itemId\tname\tsourceNpc\tsourceYieldX1\tsourceYieldCurrent\tsharedFair\tfullSetFloor\tbaseX1\ttarget\trestockDays\tbasis\tprovenance\tnativeEncounterAndRespawn"));
		final Map<String, List<String[]>> byPath = new TreeMap<>();
		for (String line : rows.subList(1, rows.size()))
		{
			final String[] row = line.split("\t", -1);
			if (row.length != 15) { throw new IllegalArgumentException("Invalid canonical row."); }
			final int id = Integer.parseInt(row[2]), currency = Integer.parseInt(row[6]), secondary = Integer.parseInt(row[8]);
			final String policyKey = row[4].equals("-") ? row[0] : row[1] + "." + id;
			final long base = Long.parseLong(_policy.getProperty(policyKey, row[7]));
			if (base < 1) { throw new IllegalArgumentException("Nonpositive explicit x1 base."); }
			long target = scaled(base, currency == 5575 ? aa : currency == 57 ? adena : 1);
			final long secondaryTarget = secondary == 0 ? 0 : scaled(Long.parseLong(row[9]), secondary == 57 ? adena : 1);
			if (row[1].equals("epic"))
			{
				if (!EPICS.contains(id) || !PhantomEpicItemPolicy.isEpic(id)) { throw new IllegalArgumentException("Noncanonical epic identity."); }
				final BossSource source = bossSource(id, _x1), current = bossSource(id, _actual);
				final long shared = fair.getOrDefault(id, 0L);
				target = Math.max(Math.max(scaled(tierFloor(id), adena), setFloor), Math.max(Math.multiplyExact(shared, policy("epic.fairFactor")), scaled(policy("epic.attemptBudget." + restock(id)), adena / current.yield())));
				epic.add(id + "\t" + row[3] + "\t" + source.npc() + "\t" + source.yield() + "\t" + current.yield() + "\t" + shared + "\t" + setFloor + "\t" + base + "\t" + target + "\t" + row[11] + "\t" + row[12] + "\t" + row[13] + "\t" + encounter(id));
			}
			if (row[4].equals("-"))
			{
				pricing.add(row[0] + "\t" + id + "\t" + currency + "\t" + target + "\t" + row[11] + "\t" + row[3]);
				report.add(row[0] + "\t" + id + "\t" + currency + "\t0\t" + base + "\t" + target + "\t0\t0\t0\t" + row[12] + "\t" + row[13]);
			}
			else
			{
				if (!XMLS.contains(row[4]) || target > _maxAdena && currency == 57 || secondaryTarget > _maxAdena) { throw new IllegalArgumentException("Static native price exceeds actual MaxAdena or exact scope."); }
				final String[] extended = java.util.Arrays.copyOf(row, 17); extended[7] = Long.toString(base); extended[15] = Long.toString(target); extended[16] = Long.toString(secondaryTarget);
				byPath.computeIfAbsent(row[4], _ -> new ArrayList<>()).add(extended);
				if (row[1].equals("mana")) { pricing.add("mana\t728\t57\t" + target + "\t0\t" + row[3]); }
			}
		}
		final List<String> files = new ArrayList<>(List.of("path\tpreHash\tpostHash"));
		for (var group : byPath.entrySet())
		{
			final Path source = PhantomEconomyXml.physical(_game, _game.resolve(group.getKey()));
			_hashes.put(group.getKey(), PhantomEconomyXml.hash(source));
			String text = Files.readString(source, StandardCharsets.UTF_8);
			final var matcher = Pattern.compile("(?s)<item\\b[^>]*>.*?</item>").matcher(text);
			final List<int[]> ranges = new ArrayList<>(); while (matcher.find()) { ranges.add(new int[] { matcher.start(), matcher.end() }); }
			final List<String[]> offers = group.getValue();
			for (int index = offers.size() - 1; index >= 0; index--)
			{
				final String[] row = offers.get(index);
				final int ordinal = Integer.parseInt(row[5]);
				if (ordinal < 1 || ordinal > ranges.size()) { throw new IllegalArgumentException("Offer structure changed."); }
				final int[] range = ranges.get(ordinal - 1);
				String block = text.substring(range[0], range[1]);
				final Element nativeOffer = PhantomEconomyXml.children(xml(source), "item").get(ordinal - 1);
				final List<Element> products = PhantomEconomyXml.children(nativeOffer, "production"), ingredients = PhantomEconomyXml.children(nativeOffer, "ingredient");
				if (products.size() != 1 || !products.getFirst().getAttribute("id").equals(row[2]) || !products.getFirst().getAttribute("count").equals(row[10]) || ingredients.size() != (row[8].equals("0") ? 1 : 2)) { throw new IllegalArgumentException("Canonical product/ingredient structure drifted."); }
				final List<String> current = new ArrayList<>();
				for (int part = ingredients.size() - 1; part >= 0; part--)
				{
					final int currency = Integer.parseInt(row[part == 0 ? 6 : 8]);
					if (!ingredients.get(part).getAttribute("id").equals(Integer.toString(currency))) { throw new IllegalArgumentException("Native payment unit changed."); }
					current.add(0, ingredients.get(part).getAttribute("count"));
					final var payment = Pattern.compile("<ingredient\\b[^>]*/>").matcher(block);
					int position = 0; boolean found = false;
					while (payment.find())
					{
						if (position++ != part) { continue; }
						final String replacement = payment.group().replaceFirst("count\\s*=\\s*\"[0-9]+\"", "count=\"" + row[part == 0 ? 15 : 16] + "\"");
						block = block.substring(0, payment.start()) + replacement + block.substring(payment.end()); found = true; break;
					}
					if (!found) { throw new IllegalArgumentException("Native ingredient absent."); }
				}
				text = text.substring(0, range[0]) + block + text.substring(range[1]);
				report.add(row[0] + "\t" + row[2] + "\t" + row[6] + "\t" + current.getFirst() + "\t" + row[7] + "\t" + row[15] + "\t" + row[8] + "\t" + (current.size() > 1 ? current.get(1) : "0") + "\t" + row[16] + "\t" + row[12] + "\t" + row[13]);
			}
			final Path payload = _output.resolve("payload").resolve(group.getKey()); Files.createDirectories(payload.getParent()); Files.writeString(payload, text, StandardCharsets.UTF_8); PhantomEconomyXml.read(payload);
			files.add(group.getKey() + "\t" + PhantomEconomyXml.hash(source) + "\t" + PhantomEconomyXml.hash(payload));
		}
		write(_output.resolve("payload"), "data/economy070/pricing070.tsv", pricing);
		write(_output.resolve("payload"), "data/economy070/pricing070.sha256", List.of(PhantomEconomyXml.hash(_output.resolve("payload/data/economy070/pricing070.tsv"))));
		for (String path : List.of("data/economy070/pricing070.tsv", "data/economy070/pricing070.sha256")) { final Path before = _game.resolve(path); files.add(path + "\t" + (Files.exists(before) ? PhantomEconomyXml.hash(PhantomEconomyXml.physical(_game, before)) : "MISSING") + "\t" + PhantomEconomyXml.hash(_output.resolve("payload").resolve(path))); }
		write(_output, "FILE_PLAN070.tsv", files);
		write(_output, "PRICING_PLAN070.tsv", report);
		write(_output, "PRICE_TABLE070.tsv", report);
		write(_output, "EPIC_CATALOG070.tsv", epic);
		write(_output, "STORE_CATALOG070.tsv", rows);
		write(_output, "QUEST_CATALOG070.tsv", java.util.stream.Stream.concat(java.util.stream.Stream.of(HEADER), rows.stream().skip(1).filter(line -> line.split("\t", -1)[1].equals("quest"))).toList());
		write(_output, "QUEST_BYPASS_COVERAGE070.tsv", Files.readAllLines(_game.resolve("data/economy070/quest-contract070.tsv"), StandardCharsets.UTF_8));
		write(_output, "EPOLETTE_SHOP070.tsv", java.util.stream.Stream.concat(java.util.stream.Stream.of(HEADER), rows.stream().skip(1).filter(line -> line.split("\t", -1)[1].equals("epaulette"))).toList());
		write(_output, "UNPRICED.tsv", List.of("key\treason"));
		write(_output, "STOCK_VARIANCES.tsv", List.of("key\treason", "epic.all\tPersistent DB stock is checked by runtime; exporter does not read/mutate DB"));
		write(_output, "CONFIG_RATE_SNAPSHOT.tsv", List.of("key\tvalue", "nonpremiumAdenaSourceWeightedMultiplier\t" + adena, "sealStoneToAASourceWeightedMultiplier\t" + aa, "XP_SP_price_multiplier\tIGNORED", "premium_price_multiplier\tIGNORED", "nativeMaxAdena\t" + _maxAdena, "method\tShared stock expected yield; spawned ordinary NPC sources weighted equally, no claimed measured combat throughput"));
		mammon();
		writeSources();
	}
	public static void main(String[] args) throws Exception
	{
		if (args.length != 3 && args.length != 4) { throw new IllegalArgumentException("Usage: <baseline|plan> <game-root> <output-dir> [canonical-baseline-dir]"); }
		final var exporter = new PhantomEconomy070CatalogExporter(Path.of(args[1]), Path.of(args[2]));
		if (args[0].equals("baseline") && args.length == 3) { exporter.baseline(); }
		else if (args[0].equals("plan") && args.length == 4) { exporter.plan(Path.of(args[3]).toAbsolutePath().normalize().toRealPath()); }
		else { throw new IllegalArgumentException("Unknown mode or missing canonical baseline."); }
	}
}
