/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.commerce;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.ToLongFunction;
import java.util.function.LongUnaryOperator;

import org.l2jmobius.gameserver.data.xml.RecipeData;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.enums.creature.Race;
import org.l2jmobius.gameserver.model.item.ItemTemplate;
import org.l2jmobius.gameserver.model.item.enums.ItemLocation;
import org.l2jmobius.gameserver.network.holders.TradeItem;
import org.l2jmobius.gameserver.model.skill.CommonSkill;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomSupplyIntentStore.Binding;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoal;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus;
import org.l2jmobius.gameserver.phantoms.economy.PhantomMultipartyEconomyService;
import org.l2jmobius.gameserver.phantoms.economy.PhantomMultipartyEconomyService.StepResult;
import org.l2jmobius.gameserver.phantoms.economy.PhantomMultipartyEconomyService.StepStatus;
import org.l2jmobius.gameserver.phantoms.economy.PhantomSocialEconomyGoalSpec;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState;
import org.l2jmobius.gameserver.model.actor.enums.player.PrivateStoreType;
import org.l2jmobius.gameserver.services.PrivateStoreService;

/** Bounded discovery of published offline supplies; all effects stay in the existing social economy owner. */
public final class PhantomOfflineSupply
{
	private static final int MAXIMUM_STORE_LINES = 64;
	private final PhantomMultipartyEconomyService _service;
	private volatile org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionRecipePlanner _recipes;

	public PhantomOfflineSupply(PhantomMultipartyEconomyService service) { _service = java.util.Objects.requireNonNull(service); }
	public synchronized void bindRecipePlanning(org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionRecipePlanner recipes)
	{
		if (_recipes != null) { throw new IllegalStateException("Offline recipe planning already bound."); }
		_recipes = java.util.Objects.requireNonNull(recipes);
	}

	/** Only published, physically owned stock is a gear candidate; no hypothetical full-grade catalog. */
	public List<ItemTemplate> equipmentOffers(Player buyer)
	{
		return owners(buyer, owner -> owner.getPrivateStoreType() == PrivateStoreType.SELL && owner.getSellList().getItems().stream()
			.anyMatch(line -> publishedStock(owner, line) > 0 && org.l2jmobius.gameserver.phantoms.progression.PhantomNativeEquipmentPolicy.upgrade(buyer, line.getItem()))).stream()
			.flatMap(owner -> owner.getSellList().getItems().stream().filter(line -> publishedStock(owner, line) > 0 && org.l2jmobius.gameserver.phantoms.progression.PhantomNativeEquipmentPolicy.upgrade(buyer, line.getItem())).limit(MAXIMUM_STORE_LINES))
			.map(line -> line.getItem()).filter(item -> org.l2jmobius.gameserver.phantoms.progression.PhantomNativeEquipmentPolicy.upgrade(buyer, item))
			.distinct().sorted(org.l2jmobius.gameserver.phantoms.progression.PhantomNativeEquipmentPolicy.preference(buyer)).limit(64).toList();
	}

	/** Maintenance must not bank a partially purchased BOM while the matching supply need remains. */
	public Set<Integer> neededIngredients(Player buyer, List<ItemTemplate> wantedProducts)
	{
		final var products = wantedProducts.stream().map(ItemTemplate::getId).collect(java.util.stream.Collectors.toUnmodifiableSet());
		final var needed = new java.util.TreeSet<Integer>();
		for (var maker : owners(buyer, owner -> owner.getPrivateStoreType() == PrivateStoreType.MANUFACTURE && matches(owner, products)))
		{
			for (var listing : maker.getManufactureItems().values().stream().filter(value ->
			{ final var recipe = RecipeData.getInstance().getRecipeList(value.getRecipeId()); return recipe != null && products.contains(recipe.getItemId()); }).limit(MAXIMUM_STORE_LINES).toList())
			{
				final var recipe = RecipeData.getInstance().getRecipeList(listing.getRecipeId());
				if (recipe == null || !products.contains(recipe.getItemId()) || !recipe.isDwarvenRecipe() || !maker.hasRecipeList(recipe.getId()) || maker.getSkillLevel(CommonSkill.CREATE_DWARVEN.getId()) < recipe.getLevel()) { continue; }
				for (var ingredient : recipe.getRecipes()) { needed.add(ingredient.getItemId()); }
			}
		}
		return Set.copyOf(needed);
	}

	private static List<Player> owners(Player buyer, Predicate<Player> matchesNeed)
	{
		// The native published-store index contains no mobs/items and has no radius cutoff.
		// Native Player admission bounds this complete snapshot. Matching stores are not
		// cut off by proximity: a cheap distant source must not disappear behind nearer shops.
		return World.getInstance().getPrivateStorePlayers().stream().filter(owner -> eligibleOwner(buyer, owner) && matchesNeed.test(owner))
			.sorted(Comparator.comparingInt(Player::getObjectId)).toList();
	}

	private static boolean eligibleOwner(Player buyer, Player owner)
	{
		return buyer != null && owner != buyer && offline(owner) && World.getInstance().getPlayer(owner.getObjectId()) == owner
			&& owner.getInstanceId() == buyer.getInstanceId() && owner.getAccessLevel().allowTransaction() && buyer.getAccessLevel().allowTransaction()
			&& !buyer.isCursedWeaponEquipped() && !owner.hasPendingOwnedStore();
	}

	private static long publishedStock(Player owner, TradeItem line)
	{
		if (line == null || line.getItem() == null || line.getPrice() <= 0 || line.getCount() <= 0) { return 0; }
		final var item = owner.getInventory().getItemByObjectId(line.getObjectId());
		return item == null || item.getOwnerId() != owner.getObjectId() || item.getId() != line.getItem().getId() || item.getItemLocation() != ItemLocation.INVENTORY || item.isEquipped() || !item.isTradeable()
			? 0 : Math.max(0, Math.min(line.getCount(), item.getCount()));
	}

	private static boolean matches(Player owner, Set<Integer> itemIds)
	{
		if (owner.getPrivateStoreType() == PrivateStoreType.SELL)
		{
			return owner.getSellList().getItems().stream().filter(line -> itemIds.contains(line.getItem().getId())).limit(MAXIMUM_STORE_LINES).anyMatch(line -> publishedStock(owner, line) > 0);
		}
		return owner.getPrivateStoreType() == PrivateStoreType.MANUFACTURE && owner.getRace() == Race.DWARF
			&& owner.getManufactureItems().values().stream().anyMatch(listing ->
			{ final var recipe = RecipeData.getInstance().getRecipeList(listing.getRecipeId()); return recipe != null && itemIds.contains(recipe.getItemId()); });
	}

	public Optional<PhantomGoal> offer(Player buyer, PhantomGoal root, Binding binding, long revision,
			List<ItemTemplate> items, long wanted, long reserve, long deadline, long maximumUnitPrice, ToLongFunction<Player> roundTripFee)
	{
		return offer(buyer, root, binding, revision, items, wanted, deadline, maximumUnitPrice, roundTripFee, fees -> Math.max(0, buyer.getAdena() - reserve - fees));
	}

	public Optional<PhantomGoal> offer(Player buyer, PhantomGoal root, Binding binding, long revision,
			List<ItemTemplate> items, long wanted, long deadline, long maximumUnitPrice, ToLongFunction<Player> roundTripFee, LongUnaryOperator availableBudget)
	{
		if (buyer == null || items.isEmpty() || wanted <= 0 || maximumUnitPrice <= 0) { return Optional.empty(); }
		final var choices = new ArrayList<Choice>();
		final var itemIds = items.stream().map(ItemTemplate::getId).collect(java.util.stream.Collectors.toUnmodifiableSet());
		for (var owner : owners(buyer, candidate -> matches(candidate, itemIds)))
		{
			final long fees = roundTripFee.applyAsLong(owner);
			final long budget = fees < 0 ? 0 : availableBudget.applyAsLong(fees);
			if (budget <= 0) { continue; }
			if (owner.getPrivateStoreType() == PrivateStoreType.SELL)
			{
				final var list = owner.getSellList();
				synchronized (list)
				{
					if (!eligibleOwner(buyer, owner) || owner.getPrivateStoreType() != PrivateStoreType.SELL) { continue; }
					final String hash = PrivateStoreService.listingHash(list);
					for (var line : list.getItems().stream().filter(value -> itemIds.contains(value.getItem().getId())).limit(MAXIMUM_STORE_LINES).toList())
					{
						final long stock = publishedStock(owner, line);
						if (stock <= 0 || line.getPrice() > maximumUnitPrice || !itemIds.contains(line.getItem().getId())) { continue; }
						final long count = Math.min(Math.min(wanted, stock), budget / line.getPrice());
						if (!L2jPhantomSupplyJourney.mobilePurchase(buyer, line.getItem(), count)) { continue; }
						final long total = Math.multiplyExact(count, line.getPrice());
						final String key = owner.getObjectId() + ";0;" + hash + ";0;" + total + ";B:" + line.getObjectId() + ':' + count + ':' + line.getPrice() + ':' + line.getItem().getId();
						final double distance = buyer.calculateDistance3D(owner);
						choices.add(new Choice(PhantomSocialEconomyGoalSpec.STORE_BUY_GOAL, key, total, count, distance, new AcquisitionQuote(total, fees, 0, count, distance), "supplies.offline.low_stock"));
					}
				}
			}
			else if (owner.getPrivateStoreType() == PrivateStoreType.MANUFACTURE && owner.getRace() == Race.DWARF)
			{
				for (var listing : owner.getManufactureItems().values().stream().filter(value ->
				{ final var recipe = RecipeData.getInstance().getRecipeList(value.getRecipeId()); return recipe != null && itemIds.contains(recipe.getItemId()); }).sorted(Comparator.comparingInt(value -> value.getRecipeId())).limit(MAXIMUM_STORE_LINES).toList())
				{
					final var recipe = RecipeData.getInstance().getRecipeList(listing.getRecipeId());
					if (recipe == null || !recipe.isDwarvenRecipe() || recipe.getCount() <= 0 || recipe.getCount() > 600 || recipe.getSuccessRate() <= 0 || listing.getCost() <= 0 || listing.getCost() > budget || items.stream().noneMatch(item -> item.getId() == recipe.getItemId()) || !owner.hasRecipeList(recipe.getId()) || owner.getSkillLevel(CommonSkill.CREATE_DWARVEN.getId()) < recipe.getLevel()) { continue; }
					long mp = 0, hp = 0;
					for (var cost : recipe.getStatUse())
					{
						if (cost.getType() == org.l2jmobius.gameserver.data.enums.StatType.MP) { mp = Math.addExact(mp, cost.getValue()); }
						if (cost.getType() == org.l2jmobius.gameserver.data.enums.StatType.HP) { hp = Math.addExact(hp, cost.getValue()); }
					}
					if (owner.isCrafting() || owner.getCurrentMp() < mp || owner.getCurrentHp() <= hp) { continue; }
					if (listing.getCost() / (double) recipe.getCount() > maximumUnitPrice || !L2jPhantomSupplyJourney.mobilePurchase(buyer, org.l2jmobius.gameserver.data.xml.ItemData.getInstance().getTemplate(recipe.getItemId()), recipe.getCount())) { continue; }
					final var ingredients = new java.util.TreeMap<Integer, Long>();
					for (var ingredient : recipe.getRecipes()) { ingredients.merge(ingredient.getItemId(), (long) ingredient.getQuantity(), Math::addExact); }
					if (ingredients.isEmpty()) { continue; }
					if (ingredients.entrySet().stream().anyMatch(entry -> buyer.getInventory().getInventoryItemCount(entry.getKey(), -1) < entry.getValue()))
					{
						if (_recipes != null)
						{
							final var component = componentChoice(buyer, owner, recipe, listing.getCost(), roundTripFee, availableBudget);
							if (component != null) { choices.add(component); }
						}
						continue;
					}
					final long inputValue = ingredientValue(recipe);
					if (inputValue < 0) { continue; }
					final String key = owner.getObjectId() + ";0;" + recipe.getId() + ';' + listing.getCost() + ';' + recipe.getItemId() + ';' + recipe.getCount() + ";1;" + listing.getCost();
					final double distance = buyer.calculateDistance3D(owner);
					choices.add(new Choice(PhantomSocialEconomyGoalSpec.MANUFACTURE_GOAL, key, listing.getCost(), recipe.getCount(), distance, new AcquisitionQuote(listing.getCost(), fees, inputValue, recipe.getCount(), distance, recipe.getSuccessRate()), "supplies.offline.low_stock"));
				}
			}
		}
		return choices.stream().sorted(Comparator.comparingDouble((Choice choice) -> choice.valuation.deliveredUnitCost()).thenComparingDouble(Choice::distance).thenComparing(Choice::key)).findFirst().map(choice ->
		{
			final var goal = new PhantomGoal(root.goalId(), choice.type, PhantomGoalStatus.ACTIVE, root.subject(), new PhantomDomainRef(PhantomSocialEconomyGoalSpec.TARGET_NAMESPACE, choice.key), 1, 0, null, List.of(), null, choice.type, 700, 0, choice.cost, deadline, Map.of(), choice.reason, revision, binding.encode());
			PhantomSocialEconomyGoalSpec.parse(goal);
			return goal;
		});
	}

	/** One existing BUY child at a time; the original supply need is recomputed from native stock after return. */
	private Choice componentChoice(Player buyer, Player maker, org.l2jmobius.gameserver.model.item.recipe.RecipeList recipe, long craftFee,
		ToLongFunction<Player> roundTripFee, LongUnaryOperator availableBudget)
	{
		final var probe = _recipes.probe(recipe.getItemId(), recipe.getCount());
		if (!probe.successful()) { return null; }
		final var inventory = new java.util.TreeMap<Integer, Long>();
		for (int itemId : probe.exactItemIds()) { inventory.put(itemId, Math.max(0, buyer.getInventory().getInventoryItemCount(itemId, -1))); }
		final var plan = _recipes.plan(recipe.getItemId(), recipe.getCount(), inventory,
			new org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionRecipePlanner.CraftEvidence(CommonSkill.CREATE_DWARVEN.getId(), maker.getSkillLevel(CommonSkill.CREATE_DWARVEN.getId()), true));
		if (!plan.planned() || plan.plan().recipeListId() != recipe.getId() || plan.plan().batchCount() != 1 || plan.plan().productOutput() != recipe.getCount() || plan.plan().successRate() != recipe.getSuccessRate()) { return null; }
		final var projected = componentProjection(buyer, maker, recipe, craftFee, roundTripFee);
		if (projected == null || projected.valuation.purchaseAdena() - craftFee > availableBudget.applyAsLong(Math.addExact(projected.valuation.travelAdena(), craftFee))) { return null; }
		return projected;
	}

	private static Choice componentProjection(Player buyer, Player maker, org.l2jmobius.gameserver.model.item.recipe.RecipeList recipe,
		long craftFee, ToLongFunction<Player> roundTripFee)
	{
		final long makerFees = roundTripFee.applyAsLong(maker);
		if (makerFees < 0) { return null; }
		final var ingredients = new java.util.TreeMap<Integer, Long>();
		for (var ingredient : recipe.getRecipes()) { ingredients.merge(ingredient.getItemId(), (long) ingredient.getQuantity(), Math::addExact); }
		if (ingredients.isEmpty() || ingredients.size() > MAXIMUM_STORE_LINES) { return null; }
		long money = craftFee, fees = makerFees, ownedValue = 0;
		Choice first = null;
		for (var ingredient : ingredients.entrySet())
		{
			final var item = org.l2jmobius.gameserver.data.xml.ItemData.getInstance().getTemplate(ingredient.getKey());
			if (item == null || item.getReferencePrice() <= 0) { return null; }
			final long owned = Math.min(ingredient.getValue(), Math.max(0, buyer.getInventory().getInventoryItemCount(ingredient.getKey(), -1)));
			ownedValue = Math.addExact(ownedValue, Math.multiplyExact(owned, item.getReferencePrice()));
			final long missing = ingredient.getValue() - owned;
			if (missing == 0) { continue; }
			final var purchase = componentPurchase(buyer, ingredient.getKey(), missing, roundTripFee);
			if (purchase == null) { return null; } // A missing/unpriced input is not an imaginary future source.
			money = Math.addExact(money, purchase.cost); fees = Math.addExact(fees, purchase.valuation.travelAdena());
			if (first == null) { first = purchase; }
		}
		if (first == null) { return null; }
		final String reason = "supplies.component.r" + recipe.getId() + ".m" + maker.getObjectId() + ".p" + recipe.getItemId();
		return new Choice(first.type, first.key, first.cost, first.count, first.distance,
			new AcquisitionQuote(money, fees, ownedValue, recipe.getCount(), buyer.calculateDistance3D(maker), recipe.getSuccessRate()), reason);
	}

	private static Choice componentPurchase(Player buyer, int itemId, long count, ToLongFunction<Player> roundTripFee)
	{
		if (count <= 0 || count > 600) { return null; }
		Choice best = null;
		for (var owner : owners(buyer, candidate -> candidate.getPrivateStoreType() == PrivateStoreType.SELL
			&& candidate.getSellList().getItems().stream().anyMatch(line -> componentLine(buyer, candidate, line, itemId, count))))
		{
			final long fees = roundTripFee.applyAsLong(owner); if (fees < 0) { continue; }
			final var list = owner.getSellList();
			synchronized (list)
			{
				if (!eligibleOwner(buyer, owner) || owner.getPrivateStoreType() != PrivateStoreType.SELL) { continue; }
				for (var line : list.getItems().stream().filter(value -> componentLine(buyer, owner, value, itemId, count)).limit(MAXIMUM_STORE_LINES).toList())
				{
					final long purchaseCount = line.getItem().getId() == itemId ? count : 1;
					if (!L2jPhantomSupplyJourney.mobilePurchase(buyer, line.getItem(), purchaseCount)) { continue; }
					final long cost = Math.multiplyExact(line.getPrice(), purchaseCount); final double distance = buyer.calculateDistance3D(owner);
					final String key = owner.getObjectId() + ";0;" + PrivateStoreService.listingHash(list) + ";0;" + cost + ";B:" + line.getObjectId() + ':' + purchaseCount + ':' + line.getPrice() + ':' + line.getItem().getId();
					// Charge the complete gear purchase to this batch. Surplus crystals are not sold Adena.
					final var candidate = new Choice(PhantomSocialEconomyGoalSpec.STORE_BUY_GOAL, key, cost, purchaseCount, distance, new AcquisitionQuote(cost, fees, 0, count, distance), "supplies.offline.low_stock");
					if (best == null || candidate.valuation.deliveredUnitCost() < best.valuation.deliveredUnitCost()
						|| candidate.valuation.deliveredUnitCost() == best.valuation.deliveredUnitCost() && (candidate.distance < best.distance || candidate.distance == best.distance && candidate.key.compareTo(best.key) < 0)) { best = candidate; }
				}
			}
		}
		return best;
	}

	private static boolean componentLine(Player buyer, Player owner, TradeItem line, int itemId, long count)
	{
		final long stock = publishedStock(owner, line);
		if (stock <= 0) { return false; }
		if (line.getItem().getId() == itemId) { return stock >= count; }
		if (line.getItem().getCrystalType().getCrystalId() != itemId) { return false; }
		final var item = owner.getInventory().getItemByObjectId(line.getObjectId());
		return PhantomLootDispositionPolicy.getInstance().prospectiveCrystallization(buyer, item, Set.of(itemId))
			.filter(preview -> preview.crystalItemId() == itemId && preview.crystalCount() >= count).isPresent();
	}

	/** Exact native recipe provenance for a component child; an unrelated material cannot bypass grade checks. */
	public static int componentProduct(PhantomGoal goal)
	{
		try
		{
			if (!goal.reasonKey().startsWith("supplies.component.")) { return 0; }
			final var parts = goal.reasonKey().split("\\.");
			if (parts.length != 5 || !parts[2].startsWith("r") || !parts[3].startsWith("m") || !parts[4].startsWith("p")) { return 0; }
			final var recipe = RecipeData.getInstance().getRecipeList(Integer.parseInt(parts[2].substring(1)));
			final var maker = World.getInstance().getPlayer(Integer.parseInt(parts[3].substring(1)));
			final var spec = spec(goal);
			if (recipe == null || !recipe.isDwarvenRecipe() || recipe.getSuccessRate() <= 0 || recipe.getItemId() != Integer.parseInt(parts[4].substring(1)) || maker == null || !offline(maker)
				|| maker.getRace() != Race.DWARF || maker.getPrivateStoreType() != PrivateStoreType.MANUFACTURE || !maker.getManufactureItems().containsKey(recipe.getId()) || !maker.hasRecipeList(recipe.getId()) || maker.getSkillLevel(CommonSkill.CREATE_DWARVEN.getId()) < recipe.getLevel()
				|| !(spec instanceof PhantomSocialEconomyGoalSpec.StoreBuy buy) || buy.lines().size() != 1) { return 0; }
			final var line = buy.lines().getFirst();
			final var buyer = World.getInstance().getPlayer(Binding.parse(goal).actorObjectId());
			if (buyer == null) { return 0; }
			final long required = java.util.Arrays.stream(recipe.getRecipes()).filter(ingredient -> ingredient.getItemId() == line.itemId()).mapToLong(ingredient -> ingredient.getQuantity()).sum();
			if (required > 0 && required - buyer.getInventory().getInventoryItemCount(line.itemId(), -1) >= line.count()) { return recipe.getItemId(); }
			final var seller = World.getInstance().getPlayer(buy.counterpartyCharacterObjectId());
			if (seller == null || line.count() != 1) { return 0; }
			final var item = seller.getInventory().getItemByObjectId(line.objectId());
			final var preview = PhantomLootDispositionPolicy.getInstance().prospectiveCrystallization(buyer, item, Set.of(recipe.getItemId())).orElse(null);
			if (preview == null) { return 0; }
			final long crystalRequired = java.util.Arrays.stream(recipe.getRecipes()).filter(ingredient -> ingredient.getItemId() == preview.crystalItemId()).mapToLong(ingredient -> ingredient.getQuantity()).sum();
			final long missingCrystals = crystalRequired - buyer.getInventory().getInventoryItemCount(preview.crystalItemId(), -1);
			return missingCrystals > 0 && missingCrystals <= preview.crystalCount() ? recipe.getItemId() : 0;
		}
		catch (RuntimeException invalid) { return 0; }
	}

	/** Monetary effects and native input valuation stay separate; probability never grants output. */
	public record AcquisitionQuote(long purchaseAdena, long travelAdena, long materialAdena, long count, double distance, int successPercent)
	{
		public AcquisitionQuote(long purchaseAdena, long travelAdena, long materialAdena, long count, double distance)
		{ this(purchaseAdena, travelAdena, materialAdena, count, distance, 100); }
		public AcquisitionQuote
		{
			if (purchaseAdena < 0 || travelAdena < 0 || materialAdena < 0 || count <= 0 || !Double.isFinite(distance) || distance < 0 || successPercent < 1 || successPercent > 100) { throw new IllegalArgumentException("Invalid native acquisition cost quote."); }
			Math.addExact(Math.addExact(purchaseAdena, travelAdena), materialAdena);
		}
		public double deliveredUnitCost() { return Math.addExact(Math.addExact(purchaseAdena, travelAdena), materialAdena) / (count * (successPercent / 100d)); }
		public String materialEvidence() { return materialAdena == 0 ? "none" : "native.reference_price.estimate_not_paid_basis"; }
	}

	/** Fresh read-only quote for comparison with NPC/self craft; dispatch still uses the native exact receipt path. */
	public static Optional<AcquisitionQuote> quote(Player buyer, PhantomGoal goal, ToLongFunction<Player> roundTripFee)
	{
		try
		{
			final var spec = spec(goal); final var owner = World.getInstance().getPlayer(spec.counterpartyCharacterObjectId());
			if (!eligibleOwner(buyer, owner)) { return Optional.empty(); }
			final long fees = roundTripFee.applyAsLong(owner);
			if (fees < 0) { return Optional.empty(); }
			if (spec instanceof PhantomSocialEconomyGoalSpec.StoreBuy buy)
			{
				if (owner.getPrivateStoreType() != PrivateStoreType.SELL || buy.packageExpected()) { return Optional.empty(); }
				final var list = owner.getSellList();
				final AcquisitionQuote direct;
				synchronized (list)
				{
					if (!PrivateStoreService.listingHash(list).equals(buy.listingHash())) { return Optional.empty(); }
					long total = 0, count = 0;
					for (var wanted : buy.lines())
					{
						final var line = list.getItems().stream().filter(value -> value.getObjectId() == wanted.objectId() && value.getItem().getId() == wanted.itemId() && value.getPrice() == wanted.price()).findFirst().orElse(null);
						if (publishedStock(owner, line) < wanted.count()) { return Optional.empty(); }
						total = Math.addExact(total, Math.multiplyExact(wanted.count(), wanted.price())); count = Math.addExact(count, wanted.count());
					}
					direct = new AcquisitionQuote(total, fees, 0, count, buyer.calculateDistance3D(owner));
				}
				if (goal.reasonKey().startsWith("supplies.component."))
				{
					if (componentProduct(goal) <= 0) { return Optional.empty(); }
					final var parts = goal.reasonKey().split("\\.");
					final var recipe = RecipeData.getInstance().getRecipeList(Integer.parseInt(parts[2].substring(1)));
					final var maker = World.getInstance().getPlayer(Integer.parseInt(parts[3].substring(1)));
					final var listing = maker.getManufactureItems().get(recipe.getId());
					if (listing == null) { return Optional.empty(); }
					// Never hold one native store lock while quoting another component owner.
					final var projected = componentProjection(buyer, maker, recipe, listing.getCost(), roundTripFee);
					return projected != null && projected.key.equals(goal.target().key()) ? Optional.of(projected.valuation) : Optional.empty();
				}
				return Optional.of(direct);
			}
			if (spec instanceof PhantomSocialEconomyGoalSpec.Manufacture craft)
			{
				final var recipe = RecipeData.getInstance().getRecipeList(craft.recipeListId());
				final var listing = owner.getManufactureItems().get(craft.recipeListId());
				if (owner.getPrivateStoreType() != PrivateStoreType.MANUFACTURE || owner.getRace() != Race.DWARF || owner.isCrafting()
					|| recipe == null || !recipe.isDwarvenRecipe() || recipe.getItemId() != craft.productItemId() || recipe.getCount() != craft.productCount()
					|| listing == null || listing.getCost() != craft.listingPrice() || !owner.hasRecipeList(recipe.getId()) || owner.getSkillLevel(CommonSkill.CREATE_DWARVEN.getId()) < recipe.getLevel()) { return Optional.empty(); }
				final long inputs = ingredientValue(recipe); if (inputs < 0) { return Optional.empty(); }
				return Optional.of(new AcquisitionQuote(craft.listingPrice(), fees, inputs, craft.productCount(), buyer.calculateDistance3D(owner), recipe.getSuccessRate()));
			}
			return Optional.empty();
		}
		catch (RuntimeException changed) { return Optional.empty(); }
	}

	/** Existing native reference prices value consumed inputs; an unpriced ingredient never becomes free. */
	private static long ingredientValue(org.l2jmobius.gameserver.model.item.recipe.RecipeList recipe)
	{
		long value = 0;
		for (var ingredient : recipe.getRecipes())
		{
			final var item = org.l2jmobius.gameserver.data.xml.ItemData.getInstance().getTemplate(ingredient.getItemId());
			if (item == null || item.getReferencePrice() <= 0) { return -1; }
			value = Math.addExact(value, Math.multiplyExact((long) item.getReferencePrice(), ingredient.getQuantity()));
		}
		return value;
	}

	public StepResult advance(long profileId, PhantomGoal goal, long generation, long tick)
	{
		final long now = System.currentTimeMillis();
		StepResult result = _service.discoverOrLoad(profileId, goal, now);
		if (result.status() != StepStatus.SUCCESS) { return result; }
		result = _service.offerOrAccept(profileId, goal, PhantomActivityState.ACTIVE, now);
		if (result.status() != StepStatus.SUCCESS) { return result; }
		result = _service.reserve(profileId, goal, generation, tick, now);
		if (result.status() != StepStatus.SUCCESS) { return result; }
		result = _service.dispatch(profileId, goal, now);
		if (result.status() != StepStatus.SUCCESS) { return result; }
		return _service.observeReconcile(profileId, goal, PhantomActivityState.ACTIVE, now);
	}

	public StepResult cancel(long profileId, PhantomGoal goal) { return _service.cancel(profileId, goal, System.currentTimeMillis()); }
	public static boolean offline(Player owner) { return owner != null && owner.isOnline() && owner.getOfflineStartTime() > 0 && (owner.getClient() == null || owner.getClient().isDetached()) && !owner.isAlikeDead(); }
	public static PhantomSocialEconomyGoalSpec spec(PhantomGoal goal)
	{
		return PhantomSocialEconomyGoalSpec.parse(new PhantomGoal(goal.goalId(), goal.goalType(), PhantomGoalStatus.ACTIVE, goal.subject(), goal.target(), 1, 0, null, List.of(), null, goal.purposeKey(), goal.priority(), goal.riskBudget(), goal.expenseBudget(), goal.deadlineEpochMillis(), Map.of(), goal.reasonKey(), goal.revision(), goal.payloadText()));
	}
	public static boolean returning(PhantomGoal goal) { return PhantomSupplyIntentStore.social(goal) && goal.status() == PhantomGoalStatus.COMPLETED && Binding.parse(goal).retryAfterEpochMillis() == 0; }
	public static long remaining(PhantomGoal goal, long now) { return returning(goal) ? Math.max(0, goal.deadlineEpochMillis() - now) : org.l2jmobius.gameserver.phantoms.decision.PhantomJourneyLifetime.remaining(goal, now); }
	/** Exact social goals retain their original wall-clock deadline and empty constraints across store/death. */
	public static PhantomGoal pause(PhantomGoal goal, long now) { return PhantomSupplyIntentStore.social(goal) ? goal : org.l2jmobius.gameserver.phantoms.decision.PhantomJourneyLifetime.pause(goal, now); }
	private record Choice(String type, String key, long cost, long count, double distance, AcquisitionQuote valuation, String reason) { }
}
