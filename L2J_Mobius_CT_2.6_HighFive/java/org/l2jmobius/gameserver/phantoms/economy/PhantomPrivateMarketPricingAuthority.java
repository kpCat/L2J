package org.l2jmobius.gameserver.phantoms.economy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.l2jmobius.gameserver.config.PlayerConfig;
import org.l2jmobius.gameserver.config.RatesConfig;
import org.l2jmobius.gameserver.config.custom.PhantomMarketConfig;
import org.l2jmobius.gameserver.data.xml.ItemData;
import org.l2jmobius.gameserver.data.xml.EnchantItemData;
import org.l2jmobius.gameserver.data.xml.RecipeData;
import org.l2jmobius.gameserver.model.WorldObject;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.instance.Merchant;
import org.l2jmobius.gameserver.model.item.ItemTemplate;
import org.l2jmobius.gameserver.model.item.enums.BodyPart;
import org.l2jmobius.gameserver.model.item.instance.Item;
import org.l2jmobius.gameserver.model.item.recipe.RecipeList;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceCatalog;
import org.l2jmobius.gameserver.phantoms.economy.PhantomPrivateMarketQuote.Inputs;
import org.l2jmobius.gameserver.phantoms.economy.PhantomPrivateMarketQuote.Policy;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeSnapshot;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.NpcKind;

/** Single snapshot-owned Phantom BUY/SELL/MANUFACTURE price producer. */
public final class PhantomPrivateMarketPricingAuthority
{
	private static final double MAX_VALUE = 1.0e15;
	private final PhantomGameKnowledgeSnapshot _knowledge;
	private final PhantomCommerceCatalog _commerce;
	private final PhantomRateAwareLootFairValue.Rates _rates;
	private final PhantomRateAwareLootFairValue.Rates _bossRates;
	private final Policy _policy;
	private final Supplier<? extends Collection<WorldObject>> _visibleMerchants;
	private final boolean _requireLiveMerchantIdentity;
	private final List<Integer> _supportIds;
	private final Set<Integer> _enchantInputIds;
	private final Set<Integer> _eligibleNpcIds;
	private final Set<Integer> _epicNpcIds;
	private final PhantomEconomyValuation.Policy _valuation;
	private double _highJewelAnchor;
	private final Map<Integer, Double> _source = new ConcurrentHashMap<>();
	private final Map<Integer, String> _sourceReasons = new ConcurrentHashMap<>();

	public PhantomPrivateMarketPricingAuthority(PhantomGameKnowledgeSnapshot knowledge, PhantomCommerceCatalog commerce, PhantomRateAwareLootFairValue.Rates rates, Policy policy, Collection<WorldObject> visibleMerchants)
	{
		this(knowledge, commerce, rates, policy, snapshot(visibleMerchants), false);
	}

	/** Runtime source: the current collection is obtained again for every native NPC ceiling check. */
	public PhantomPrivateMarketPricingAuthority(PhantomGameKnowledgeSnapshot knowledge, PhantomCommerceCatalog commerce, PhantomRateAwareLootFairValue.Rates rates, Policy policy, Supplier<? extends Collection<WorldObject>> visibleMerchants)
	{
		this(knowledge, commerce, rates, policy, visibleMerchants, true);
	}

	private PhantomPrivateMarketPricingAuthority(PhantomGameKnowledgeSnapshot knowledge, PhantomCommerceCatalog commerce, PhantomRateAwareLootFairValue.Rates rates, Policy policy, Supplier<? extends Collection<WorldObject>> visibleMerchants, boolean requireLiveMerchantIdentity)
	{
		if ((knowledge == null) || (commerce == null) || (rates == null) || (policy == null) || (visibleMerchants == null))
		{
			throw new IllegalArgumentException("Missing accepted private-market generation.");
		}
		_knowledge = knowledge;
		_commerce = commerce;
		_rates = rates;
		_bossRates = new PhantomRateAwareLootFairValue.Rates(RatesConfig.RATE_RAID_DROP_CHANCE_MULTIPLIER > 0 ? RatesConfig.RATE_RAID_DROP_CHANCE_MULTIPLIER : rates.deathChance(), RatesConfig.RATE_RAID_DROP_AMOUNT_MULTIPLIER > 0 ? RatesConfig.RATE_RAID_DROP_AMOUNT_MULTIPLIER : rates.deathAmount(), rates.spoilChance(), rates.spoilAmount(), rates.chanceByItem(), rates.amountByItem());
		_policy = policy;
		_visibleMerchants = visibleMerchants;
		_requireLiveMerchantIdentity = requireLiveMerchantIdentity;
		_supportIds = knowledge.itemById().keySet().stream().filter(id -> EnchantItemData.getInstance().getSupportItemById(id) != null).sorted().toList();
		final Set<Integer> enchantInputs = new HashSet<>(_supportIds);
		EnchantItemData.getInstance().getScrolls().forEach(scroll -> enchantInputs.add(scroll.getId()));
		_enchantInputIds = Set.copyOf(enchantInputs);
		_eligibleNpcIds = PhantomRateAwareLootFairValue.eligibleNpcIds(knowledge);
		_epicNpcIds = knowledge.npcById().values().stream().filter(npc -> npc.kind() == NpcKind.GRAND_BOSS || npc.kind() == NpcKind.RAID_BOSS).map(npc -> npc.npcId()).collect(java.util.stream.Collectors.toUnmodifiableSet());
		_valuation = PhantomMarketConfig.valuationPolicy().orElseGet(PhantomEconomyValuation.Policy::defaults);
		for (ItemTemplate item : Arrays.stream(ItemData.getInstance().getAllItems()).filter(java.util.Objects::nonNull).filter(PhantomPrivateMarketPricingAuthority::ordinaryHighJewel).toList())
		{
			_highJewelAnchor = Math.max(_highJewelAnchor, Math.max(sourceFair(item.getId()), craftFloor(item.getId())));
		}
	}

	private static Supplier<Collection<WorldObject>> snapshot(Collection<WorldObject> visibleMerchants)
	{
		if (visibleMerchants == null) { throw new IllegalArgumentException("Missing accepted private-market generation."); }
		final List<WorldObject> accepted = visibleMerchants.stream().filter(object -> object instanceof Merchant).toList();
		return () -> accepted;
	}

	public record ItemQuote(int itemId, boolean priced, boolean bidAllowed, long bid, long ask, long fair, String sourceReason) { }

	/** Read-only unenchanted template quote. Inventory ownership is validated only when a plan is applied. */
	public ItemQuote quoteItem(int itemId)
	{
		try
		{
			final ItemTemplate template = ItemData.getInstance().getTemplate(itemId);
			validateMarketable(template);
			final PhantomNpcAcquisitionPrice.Quote npc = npc(itemId);
			final boolean unresolvedNpc = npc.exists() && !npc.exact();
			if (npc.exists() && npc.exact() && npc.price() == 0) { throw new IllegalArgumentException("ordinary free NPC acquisition"); }
			final long fair = checkedLong(base(itemId, template));
			final long craft = craftFloor(itemId);
			if (craft == 0 && _sourceReasons.getOrDefault(itemId, "").equals("liquidation.opportunity_only")) { throw new IllegalArgumentException("no acquisition source; liquidation.opportunity_only"); }
			final long altB = PhantomMarketConfig.altBSources().acquisitionCeiling(itemId);
			final boolean ceilingExists = npc.exists() && npc.exact() || craft > 0 || altB >= 0;
			long ceiling = npc.exists() && npc.exact() ? npc.price() : Long.MAX_VALUE;
			if (craft > 0) { ceiling = Math.min(ceiling, craft); }
			if (altB >= 0) { ceiling = Math.min(ceiling, altB); }
			if (!ceilingExists) { ceiling = 0; }
			final var quote = PhantomPrivateMarketQuote.quote(new Inputs(fair, liquidation(template), ceilingExists, ceiling, craft, 0, PlayerConfig.MAX_ADENA), _policy);
			final boolean bidAllowed = !unresolvedNpc && template.isStackable() && quote.buyAllowed();
			return new ItemQuote(itemId, true, bidAllowed, bidAllowed ? quote.bid() : 0, quote.ask(), fair, _sourceReasons.getOrDefault(itemId, "recipe.bom") + (craft > 0 ? "+recipe.expected_cost" : "") + (unresolvedNpc ? "+BUY_BLOCKED:" + npc.reason() : ""));
		}
		catch (RuntimeException unsafe)
		{
			return new ItemQuote(itemId, false, false, 0, 0, 0, "UNPRICED:" + unsafe.getMessage());
		}
	}

	private static boolean ordinaryHighJewel(ItemTemplate item)
	{
		return !PhantomEpicItemPolicy.isEpic(item) && item.isTradeable() && item.getCrystalType().getLevel() >= 5 && Set.of(BodyPart.NECK, BodyPart.LR_EAR, BodyPart.LR_FINGER, BodyPart.R_EAR, BodyPart.L_EAR, BodyPart.R_FINGER, BodyPart.L_FINGER).contains(item.getBodyPart());
	}

	public PhantomStorePlan pricePlan(Player owner, PhantomStorePlan requested)
	{
		if ((owner == null) || (requested == null))
		{
			throw new IllegalArgumentException("Missing store owner or plan.");
		}
		final List<PhantomStorePlan.Line> priced = new ArrayList<>();
		for (PhantomStorePlan.Line line : requested.lines())
		{
			final long price = switch (requested.type())
			{
				case SELL, PACKAGE_SELL -> ask(owner.getInventory().getItemByObjectId(line.objectOrRecipeId()), line.itemId());
				case BUY -> bid(line.itemId());
				case MANUFACTURE -> manufactureFee(line.objectOrRecipeId(), line.itemId());
			};
			priced.add(new PhantomStorePlan.Line(line.objectOrRecipeId(), line.itemId(), line.count(), price));
		}
		return requested.withLines(priced);
	}

	private long ask(Item item, int itemId)
	{
		if ((item == null) || (item.getId() != itemId) || !item.isTradeable())
		{
			throw new IllegalArgumentException("SELL ownership or item eligibility failed.");
		}
		final ItemTemplate template = item.getTemplate();
		validateMarketable(template);
		final double base = base(itemId, template);
		final long craftFloor = craftFloor(itemId);
		final long enchantFloor;
		if (item.getEnchantLevel() > 0)
		{
			final var steps = PhantomCanonicalEnchantRoutes.steps(template, item.getEnchantLevel(), _supportIds, this::sourceFair);
			enchantFloor = checkedLong(PhantomEnchantExpectedCost.quote(Math.max(base, craftFloor), steps).fairValue());
		}
		else
		{
			enchantFloor = 0;
		}
		final PhantomNpcAcquisitionPrice.Quote npc = npc(itemId);
		if (npc.exists() && npc.exact() && (npc.price() == 0))
		{
			throw new IllegalArgumentException("Ordinary free NPC acquisition excludes private-market listing.");
		}
		final PhantomPrivateMarketQuote.Quote quoted = PhantomPrivateMarketQuote.quote(new Inputs(checkedLong(Math.max(base, enchantFloor)), liquidation(template), npc.exists() && npc.exact(), npc.exact() ? npc.price() : 0, craftFloor, enchantFloor, PlayerConfig.MAX_ADENA), _policy);
		return quoted.ask();
	}

	private long bid(int itemId)
	{
		final ItemQuote quote = quoteItem(itemId);
		if (!quote.priced() || !quote.bidAllowed()) { throw new IllegalArgumentException("Unsafe BUY quote: " + quote.sourceReason()); }
		return quote.bid();
	}

	public long manufactureFee(int recipeListId, int productId)
	{
		final RecipeList recipe = RecipeData.getInstance().getRecipeList(recipeListId);
		if ((recipe == null) || (recipe.getItemId() != productId))
		{
			throw new IllegalArgumentException("Manufacture recipe is not canonical.");
		}
		double inputs = 0;
		for (var ingredient : recipe.getRecipes())
		{
			final double cost = sourceFair(ingredient.getItemId());
			if (cost <= 0) { throw new IllegalArgumentException("Manufacture BOM has no fair input source."); }
			inputs += cost * ingredient.getQuantity();
		}
		return PhantomEconomyValuation.manufactureFee(inputs, _valuation, PlayerConfig.MAX_ADENA);
	}

	private double base(int itemId, ItemTemplate template)
	{
		final double source = sourceFair(itemId);
		final long craft = craftFloor(itemId);
		if (((source <= 0) || _sourceReasons.getOrDefault(itemId, "").equals("liquidation.opportunity_only")) && (craft <= 0))
		{
			throw new IllegalArgumentException("No legitimate market acquisition anchor.");
		}
		return Math.max(Math.max(source, craft), liquidation(template));
	}

	private static long liquidation(ItemTemplate template)
	{
		final int crystalId = template.getCrystalType().getCrystalId();
		final ItemTemplate crystal = crystalId > 0 ? ItemData.getInstance().getTemplate(crystalId) : null;
		return PhantomEconomyValuation.liquidationFloor(PhantomPrivateMarketQuote.npcLiquidation(template), template.getCrystalCount(), PhantomPrivateMarketQuote.npcLiquidation(crystal));
	}

	private long craftFloor(int itemId)
	{
		try
		{
			final double floor = PhantomRecipeExpectedCost.floor(itemId, _knowledge, this::sourceFair, recipeId -> 0, PlayerConfig.CRAFT_MASTERWORK_CHANCE_RATE).perItemCost();
			return checkedLong(floor);
		}
		catch (IllegalArgumentException unsafe)
		{
			return 0;
		}
	}

	private double sourceFair(int itemId)
	{
		return _source.computeIfAbsent(itemId, id ->
		{
			final ItemTemplate template = ItemData.getInstance().getTemplate(id);
			if (template == null)
			{
				return 0d;
			}
			final PhantomNpcAcquisitionPrice.Quote npc = npc(id);
			if (npc.exists() && npc.exact() && (npc.price() == 0))
			{
				return 0d;
			}
			PhantomRateAwareLootFairValue.Quote loot = null;
			try
			{
				loot = PhantomRateAwareLootFairValue.quote(id, _knowledge, PhantomEpicItemPolicy.isEpic(template) ? _bossRates : _rates, PhantomEpicItemPolicy.isEpic(template) ? _epicNpcIds : _eligibleNpcIds);
			}
			catch (IllegalArgumentException noLootSource)
			{
			}
			if (_enchantInputIds.contains(id) && loot == null && !(npc.exists() && npc.exact() && npc.price() > 0)) { return 0d; }
			try
			{
				final var value = PhantomEconomyValuation.source(liquidation(template), template.isWeapon() || template.isArmor(), PhantomEpicItemPolicy.isEpic(template), _highJewelAnchor, npc, loot, _valuation);
				_sourceReasons.put(id, value.reason());
				return value.fair();
			}
			catch (IllegalArgumentException unpriced) { _sourceReasons.put(id, unpriced.getMessage()); return 0d; }
		});
	}

	private PhantomNpcAcquisitionPrice.Quote npc(int itemId)
	{
		// The live supplier keeps NPC arrival/respawn independent of immutable knowledge/value caches.
		final var visible = _visibleMerchants.get();
		if (visible == null) { throw new IllegalArgumentException("Current native merchant source unavailable."); }
		final List<WorldObject> current = visible.stream().filter(object -> object instanceof Merchant merchant
			&& (!_requireLiveMerchantIdentity || merchant.isSpawned() && World.getInstance().findObject(merchant.getObjectId()) == merchant)).toList();
		return PhantomNpcAcquisitionPrice.quote(itemId, _commerce, current);
	}

	private static long checkedLong(double value)
	{
		if (!Double.isFinite(value) || (value <= 0) || (value > MAX_VALUE) || (value > PlayerConfig.MAX_ADENA))
		{
			throw new IllegalArgumentException("Unsafe source-backed private-market value.");
		}
		return (long) Math.ceil(value);
	}

	private static void validateMarketable(ItemTemplate item)
	{
		if ((item == null) || !item.isTradeable() || item.isQuestItem() || item.isHeroItem() || (item.getTime() != -1))
		{
			throw new IllegalArgumentException("Unsupported private-market item category.");
		}
	}
}
