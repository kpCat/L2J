package org.l2jmobius.gameserver.phantoms.economy;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.l2jmobius.gameserver.config.PlayerConfig;
import org.l2jmobius.gameserver.data.xml.ItemData;
import org.l2jmobius.gameserver.data.xml.EnchantItemData;
import org.l2jmobius.gameserver.data.xml.RecipeData;
import org.l2jmobius.gameserver.model.WorldObject;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.instance.Merchant;
import org.l2jmobius.gameserver.model.item.ItemTemplate;
import org.l2jmobius.gameserver.model.item.instance.Item;
import org.l2jmobius.gameserver.model.item.recipe.RecipeList;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceCatalog;
import org.l2jmobius.gameserver.phantoms.economy.PhantomPrivateMarketQuote.Inputs;
import org.l2jmobius.gameserver.phantoms.economy.PhantomPrivateMarketQuote.Policy;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeSnapshot;

/** Single snapshot-owned Phantom BUY/SELL/MANUFACTURE price producer. */
public final class PhantomPrivateMarketPricingAuthority
{
	private static final double MAX_VALUE = 1.0e15;
	private final PhantomGameKnowledgeSnapshot _knowledge;
	private final PhantomCommerceCatalog _commerce;
	private final PhantomRateAwareLootFairValue.Rates _rates;
	private final Policy _policy;
	private final Collection<WorldObject> _visibleMerchants;
	private final List<Integer> _supportIds;
	private final Set<Integer> _enchantInputIds;
	private final Set<Integer> _eligibleNpcIds;
	private final Map<Integer, PhantomNpcAcquisitionPrice.Quote> _npc = new HashMap<>();
	private final Map<Integer, Double> _source = new HashMap<>();

	public PhantomPrivateMarketPricingAuthority(PhantomGameKnowledgeSnapshot knowledge, PhantomCommerceCatalog commerce, PhantomRateAwareLootFairValue.Rates rates, Policy policy, Collection<WorldObject> visibleMerchants)
	{
		if ((knowledge == null) || (commerce == null) || (rates == null) || (policy == null) || (visibleMerchants == null))
		{
			throw new IllegalArgumentException("Missing accepted private-market generation.");
		}
		_knowledge = knowledge;
		_commerce = commerce;
		_rates = rates;
		_policy = policy;
		_visibleMerchants = visibleMerchants.stream().filter(object -> object instanceof Merchant).toList();
		_supportIds = knowledge.itemById().keySet().stream().filter(id -> EnchantItemData.getInstance().getSupportItemById(id) != null).sorted().toList();
		final Set<Integer> enchantInputs = new HashSet<>(_supportIds);
		EnchantItemData.getInstance().getScrolls().forEach(scroll -> enchantInputs.add(scroll.getId()));
		_enchantInputIds = Set.copyOf(enchantInputs);
		_eligibleNpcIds = PhantomRateAwareLootFairValue.eligibleNpcIds(knowledge);
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
		final PhantomPrivateMarketQuote.Quote quoted = PhantomPrivateMarketQuote.quote(new Inputs(checkedLong(Math.max(base, enchantFloor)), PhantomPrivateMarketQuote.npcLiquidation(template), npc.exists() && npc.exact(), npc.exact() ? npc.price() : 0, craftFloor, enchantFloor, PlayerConfig.MAX_ADENA), _policy);
		return quoted.ask();
	}

	private long bid(int itemId)
	{
		final ItemTemplate template = ItemData.getInstance().getTemplate(itemId);
		validateMarketable(template);
		if (!template.isStackable())
		{
			throw new IllegalArgumentException("Native BUY item ID cannot specify enchanted gear.");
		}
		final PhantomNpcAcquisitionPrice.Quote npc = npc(itemId);
		if (npc.exists() && !npc.exact())
		{
			throw new IllegalArgumentException("NPC source tax or Merchant availability unresolved.");
		}
		final double base = base(itemId, template);
		final long craftFloor = craftFloor(itemId);
		final PhantomPrivateMarketQuote.Quote quoted = PhantomPrivateMarketQuote.quote(new Inputs(checkedLong(Math.max(base, craftFloor)), PhantomPrivateMarketQuote.npcLiquidation(template), npc.exists(), npc.price(), craftFloor, 0, PlayerConfig.MAX_ADENA), _policy);
		if (!quoted.buyAllowed())
		{
			throw new IllegalArgumentException("NPC acquisition/liquidation corridor excludes BUY.");
		}
		return quoted.bid();
	}

	private long manufactureFee(int recipeListId, int productId)
	{
		final RecipeList recipe = RecipeData.getInstance().getRecipeList(recipeListId);
		if ((recipe == null) || (recipe.getItemId() != productId))
		{
			throw new IllegalArgumentException("Manufacture recipe is not canonical.");
		}
		// Stock has no unavoidable Adena fee for self-craft; customer supplies materials.
		// A source-backed nonzero skill/service fee is unavailable, so the native listing is free.
		if (craftFloor(productId) <= 0)
		{
			throw new IllegalArgumentException("Manufacture BOM has no fair input source.");
		}
		return 0;
	}

	private double base(int itemId, ItemTemplate template)
	{
		final double source = sourceFair(itemId);
		final long craft = craftFloor(itemId);
		if ((source <= 0) && (craft <= 0))
		{
			throw new IllegalArgumentException("No legitimate market acquisition anchor.");
		}
		return Math.max(Math.max(source, craft), PhantomPrivateMarketQuote.npcLiquidation(template));
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
			double fair = npc.exists() && npc.exact() && (npc.price() > 0) ? npc.price() : Double.POSITIVE_INFINITY;
			try
			{
				fair = Math.min(fair, PhantomRateAwareLootFairValue.quote(id, _knowledge, _rates, _eligibleNpcIds).fairValue());
			}
			catch (IllegalArgumentException noLootSource)
			{
			}
			// Merchant liquidation is an opportunity-cost lower bound for ordinary
			// recipe inputs; scroll/support market scarcity requires a real source.
			final double opportunity = PhantomPrivateMarketQuote.npcLiquidation(template);
			fair = Double.isFinite(fair) ? Math.max(fair, opportunity) : _enchantInputIds.contains(id) ? 0 : opportunity;
			return Double.isFinite(fair) && (fair > 0) && (fair <= MAX_VALUE) ? fair : 0d;
		});
	}

	private PhantomNpcAcquisitionPrice.Quote npc(int itemId)
	{
		return _npc.computeIfAbsent(itemId, id -> PhantomNpcAcquisitionPrice.quote(id, _commerce, _visibleMerchants));
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
