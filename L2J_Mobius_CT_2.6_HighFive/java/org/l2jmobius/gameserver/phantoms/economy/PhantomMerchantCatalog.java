/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.economy;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.l2jmobius.gameserver.config.ServerConfig;
import org.l2jmobius.gameserver.data.xml.ItemData;
import org.l2jmobius.gameserver.data.xml.RecipeData;
import org.l2jmobius.gameserver.model.item.ItemTemplate;
import org.l2jmobius.gameserver.model.item.recipe.RecipeList;
import org.l2jmobius.gameserver.model.item.type.ActionType;
import org.l2jmobius.gameserver.model.item.type.CrystalType;
import org.l2jmobius.gameserver.model.item.type.EtcItemType;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgePolicy;

/** Complete immutable eligibility universe; small proposals and physical inventory are separate views. */
public final class PhantomMerchantCatalog
{
	public record Entry(ItemTemplate item, PhantomPrivateMarketPricingAuthority.ItemQuote quote, String category) { }
	public static final String CLASSIFICATION_FILE = "data/phantoms/merchant-market-classification.tsv";
	public record Classification(String category, String targetGrade, List<Integer> recipes, String provenance, String exclusionReason)
	{
		public Classification { recipes = List.copyOf(recipes); }
		public boolean eligible() { return exclusionReason.isEmpty(); }
	}
	public record Audit(ItemTemplate item, Classification classification, PhantomPrivateMarketPricingAuthority.ItemQuote quote) { }
	public record Mapping(String category, String exactName, List<Integer> recipes, String provenance) { }
	private final List<Entry> _entries;
	private final List<Entry> _buyEntries;
	private final List<Audit> _audit;
	private final Map<Integer, Classification> _classifications;
	private final Map<Integer, Entry> _byId;

	public PhantomMerchantCatalog(PhantomPrivateMarketPricingAuthority pricing)
	{
		final var recipes = nativeRecipes();
		final Map<Integer, List<RecipeList>> uses = new HashMap<>(), scrolls = new HashMap<>();
		final Set<Integer> gear = new HashSet<>();
		for (var recipe : recipes)
		{
			scrolls.computeIfAbsent(recipe.getRecipeId(), _ -> new ArrayList<>()).add(recipe);
			final var target = ItemData.getInstance().getTemplate(recipe.getItemId());
			if (target != null && target.isEquipable() && (target.isArmor() || target.isWeapon())) { gear.add(target.getId()); }
			for (var ingredient : recipe.getRecipes()) { uses.computeIfAbsent(ingredient.getItemId(), _ -> new ArrayList<>()).add(recipe); }
		}
		final var mapping = mappings();
		final Map<String, List<Entry>> groups = new TreeMap<>();
		final var audit = new ArrayList<Audit>();
		final Map<Integer, Classification> classifications = new HashMap<>();
		for (var item : Arrays.stream(ItemData.getInstance().getAllItems()).filter(java.util.Objects::nonNull).sorted(Comparator.comparingInt(ItemTemplate::getId)).toList())
		{
			final var classification = classify(item, uses.getOrDefault(item.getId(), List.of()), scrolls.getOrDefault(item.getId(), List.of()), mapping.get(item.getId()), gear.contains(item.getId()));
			if (classification == null) { continue; }
			classifications.put(item.getId(), classification);
			final var quote = classification.eligible() ? pricing.quoteItem(item.getId()) : new PhantomPrivateMarketPricingAuthority.ItemQuote(item.getId(), false, false, 0, 0, 0, classification.exclusionReason());
			audit.add(new Audit(item, classification, quote));
			if (!classification.eligible() || !quote.priced()) { continue; }
			final String group = classification.category() + '.' + classification.targetGrade();
			groups.computeIfAbsent(group, _ -> new ArrayList<>()).add(new Entry(item, quote, group));
		}
		_entries = selectEntries(groups.values().stream().map(group -> group.stream().sorted(Comparator.comparingLong((Entry entry) -> entry.quote().fair()).thenComparingInt(entry -> entry.item().getId())).toList()).toList());
		_buyEntries = _entries.stream().filter(entry -> entry.item().isStackable() && entry.quote().bidAllowed()).toList();
		_audit = List.copyOf(audit); _classifications = Map.copyOf(classifications);
		_byId = _entries.stream().collect(java.util.stream.Collectors.toUnmodifiableMap(entry -> entry.item().getId(), entry -> entry));
		if (_entries.isEmpty()) { throw new IllegalArgumentException("No source-priced native merchant assortment."); }
	}
	/** Same bounded list-ID recovery used by L2jGameKnowledgeBackend for duplicate scroll5008. */
	static List<RecipeList> nativeRecipes()
	{
		final var data = RecipeData.getInstance();
		final int[] expected = data.getAllItemIds();
		final var resolved = new ArrayList<RecipeList>();
		final int ceiling = PhantomGameKnowledgePolicy.productionDefaults().maximumRecipes();
		for (int id = 1; id <= ceiling && resolved.size() < expected.length; id++)
		{
			final var recipe = data.getRecipeList(id); if (recipe != null) { resolved.add(recipe); }
		}
		final int[] actual = resolved.stream().mapToInt(RecipeList::getRecipeId).sorted().toArray();
		Arrays.sort(expected);
		if (!Arrays.equals(expected, actual)) { throw new IllegalArgumentException("Merchant RecipeData identity multiset differs; no partial catalog."); }
		return List.copyOf(resolved);
	}
	private static Map<Integer, Mapping> mappings()
	{
		final Map<Integer, Mapping> result = new HashMap<>();
		try
		{
			for (String line : Files.readAllLines(new java.io.File(ServerConfig.DATAPACK_ROOT, CLASSIFICATION_FILE).toPath(), java.nio.charset.StandardCharsets.UTF_8))
			{
				if (line.isBlank() || line.startsWith("#")) { continue; }
				final String[] cells = line.split("\t", -1);
				if (cells.length != 5 || !Set.of("MATERIAL_RESOURCE", "EQUIPMENT_PIECE").contains(cells[1]) || result.size() >= 4096) { throw new IllegalArgumentException("Invalid exact merchant classification row."); }
				final int id = Integer.parseInt(cells[0]);
				final var recipeIds = Arrays.stream(cells[3].split(",")).map(Integer::parseInt).sorted().toList();
				if (result.put(id, new Mapping(cells[1], cells[2], recipeIds, cells[4])) != null) { throw new IllegalArgumentException("Duplicate exact merchant classification."); }
			}
		}
		catch (java.io.IOException invalid) { throw new IllegalArgumentException("Source-backed merchant classification unavailable.", invalid); }
		return Map.copyOf(result);
	}
	static Classification classify(ItemTemplate item, List<RecipeList> uses, List<RecipeList> scrolls, Mapping mapping, boolean canonicalGear)
	{
		String category = null, grade = "NONE", provenance = "ItemData", reason = "";
		List<Integer> ids = List.of();
		// Recipe and material deny precedence applies even when the same IDs look useful.
		if (item.getItemType() == EtcItemType.RECIPE || !scrolls.isEmpty())
		{
			category = "RECIPE_SCROLL60"; ids = scrolls.stream().map(RecipeList::getId).sorted().toList();
			final var targets = scrolls.stream().map(recipe -> ItemData.getInstance().getTemplate(recipe.getItemId())).toList();
			grade = grades(targets); provenance = "RecipeData.scroll=" + item.getId();
			if (item.getItemType() != EtcItemType.RECIPE || targets.isEmpty() || targets.stream().anyMatch(target -> target == null || !target.isEquipable() || !(target.isWeapon() || target.isArmor()))) { reason = "NOT_EQUIPMENT_RECIPE"; }
			else if (targets.stream().anyMatch(target -> target.getCrystalType().getLevel() < CrystalType.B.getLevel())) { reason = "FORBIDDEN_RECIPE_GRADE"; }
			else if (scrolls.stream().anyMatch(recipe -> recipe.getSuccessRate() != 60)) { reason = "RECIPE_NOT_EXACTLY_60_PERCENT"; }
		}
		else if (mapping != null || item.getItemType() == EtcItemType.MATERIAL || !uses.isEmpty())
		{
			category = mapping == null ? "UNRESOLVED_CLASSIFICATION" : mapping.category(); ids = uses.stream().map(RecipeList::getId).sorted().toList();
			if (mapping == null || !mapping.exactName().equals(item.getName()) || !mapping.recipes().equals(ids)) { reason = "UNRESOLVED_CLASSIFICATION"; }
			else
			{
				provenance = mapping.provenance();
				if (category.equals("EQUIPMENT_PIECE"))
				{
					final var targets = uses.stream().map(recipe -> ItemData.getInstance().getTemplate(recipe.getItemId())).filter(target -> target != null && target.isEquipable() && (target.isWeapon() || target.isArmor())).toList();
					grade = grades(targets);
					if (targets.isEmpty()) { reason = "UNRESOLVED_PIECE_TARGET"; }
					else if (targets.stream().anyMatch(target -> target.getCrystalType().getLevel() < CrystalType.B.getLevel())) { reason = "FORBIDDEN_PIECE_GRADE"; }
				}
				else if (uses.isEmpty()) { reason = "NO_CANONICAL_CRAFT_USE"; }
			}
		}
		else if (item.isEquipable() && (item.isWeapon() || item.isArmor()))
		{
			category = "FINISHED_GEAR"; grade = item.getCrystalType().name(); provenance = canonicalGear ? "ItemData+RecipeData.output" : "ItemData.native_template;ACQUISITION_QUOTE_REQUIRED";
		}
		else if (Arrays.stream(CrystalType.values()).anyMatch(type -> type.getCrystalId() == item.getId()) || useful(item)) { category = "OTHER_USEFUL"; provenance = "ItemData.typed_useful"; }
		if (category == null) { return null; }
		if (item.getId() == 57 || !item.isTradeable() || item.isQuestItem() || item.isHeroItem() || item.isPvpItem() || item.getTime() != -1 || PhantomEpicItemPolicy.isEpic(item)) { reason = "NATIVE_MARKET_RESTRICTION"; }
		return new Classification(category, grade, ids, provenance, reason);
	}
	private static String grades(List<ItemTemplate> targets)
	{
		return targets.stream().filter(java.util.Objects::nonNull).map(ItemTemplate::getCrystalType).distinct().sorted(Comparator.comparingInt(CrystalType::getLevel)).map(Enum::name).collect(java.util.stream.Collectors.joining(","));
	}
	private static boolean useful(ItemTemplate item)
	{
		return Set.of(EtcItemType.SCRL_ENCHANT_WP, EtcItemType.SCRL_ENCHANT_AM, EtcItemType.BLESS_SCRL_ENCHANT_WP, EtcItemType.BLESS_SCRL_ENCHANT_AM, EtcItemType.SCRL_ENCHANT_ATTR, EtcItemType.SCRL_INC_ENCHANT_PROP_WP, EtcItemType.SCRL_INC_ENCHANT_PROP_AM, EtcItemType.ANCIENT_CRYSTAL_ENCHANT_WP, EtcItemType.ANCIENT_CRYSTAL_ENCHANT_AM, EtcItemType.ARROW, EtcItemType.BOLT, EtcItemType.POTION, EtcItemType.ELIXIR, EtcItemType.SHOT).contains(item.getItemType()) || item.getDefaultAction() == ActionType.SOULSHOT || item.getDefaultAction() == ActionType.SPIRITSHOT;
	}
	static List<Entry> selectEntries(List<? extends List<Entry>> acceptedGroups)
	{
		final var entries = new ArrayList<Entry>();
		final int longest = acceptedGroups.stream().mapToInt(List::size).max().orElse(0);
		final var seen = new HashSet<Integer>();
		for (int index = 0; index < longest; index++)
		{
			for (var group : acceptedGroups)
			{
				if (index < group.size() && seen.add(group.get(index).quote().itemId())) { entries.add(group.get(index)); }
			}
		}
		return List.copyOf(entries);
	}
	public List<Entry> entries() { return _entries; }
	public List<Audit> audit() { return _audit; }
	public boolean contains(int itemId) { return _byId.containsKey(itemId); }
	public Classification classification(int itemId) { return _classifications.get(itemId); }
	public List<Entry> sample(long cursor, int count)
	{
		return page(_entries, cursor, count);
	}
	public List<Entry> buySample(long cursor, int count)
	{
		return page(_buyEntries, cursor, count);
	}
	private static List<Entry> page(List<Entry> pool, long cursor, int count)
	{
		if (count < 1 || count > 128) { throw new IllegalArgumentException("Merchant proposal page outside1..128."); }
		if (pool.isEmpty()) { return List.of(); }
		final var result = new ArrayList<Entry>();
		final int start = (int) Math.floorMod(cursor, pool.size());
		for (int index = 0; index < Math.min(count, pool.size()); index++) { result.add(pool.get((start + index) % pool.size())); }
		return List.copyOf(result);
	}
	public List<Entry> bootstrapEntries()
	{
		return _entries.stream().filter(entry -> Arrays.stream(_classifications.get(entry.item().getId()).targetGrade().split(",")).allMatch(grade -> grade.isEmpty() || CrystalType.valueOf(grade).getLevel() <= CrystalType.A.getLevel())).toList();
	}
}
