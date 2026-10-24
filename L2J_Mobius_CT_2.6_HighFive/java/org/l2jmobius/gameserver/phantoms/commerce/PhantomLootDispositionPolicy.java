/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.commerce;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.IntFunction;

import org.l2jmobius.gameserver.data.xml.ItemData;
import org.l2jmobius.gameserver.data.xml.RecipeData;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.item.ItemTemplate;
import org.l2jmobius.gameserver.model.item.enums.BodyPart;
import org.l2jmobius.gameserver.model.item.enums.ItemLocation;
import org.l2jmobius.gameserver.model.item.instance.Item;
import org.l2jmobius.gameserver.model.item.type.CrystalType;
import org.l2jmobius.gameserver.model.item.type.EtcItemType;
import org.l2jmobius.gameserver.phantoms.progression.PhantomNativeEquipmentPolicy;
import org.l2jmobius.gameserver.qol.CrystallizationService;

/** Shared read-only disposition rules; native Commerce remains the sole maintenance writer. */
public final class PhantomLootDispositionPolicy
{
	public enum Disposition { KEEP, SELL_NPC, WAREHOUSE, CRYSTALLIZE, MARKET_RESERVE }
	public record Decision(Disposition disposition, long count, String reason)
	{
		public Decision
		{
			if (disposition == null || count < 0 || reason == null || reason.isBlank() || disposition != Disposition.KEEP && count == 0) { throw new IllegalArgumentException("Invalid loot disposition."); }
		}
	}
	public record RecipeRole(int highestProductGrade, boolean commonResource, boolean recipeProduct, long reserve, int productKinds)
	{
		public boolean keyPart() { return highestProductGrade >= 0 && !commonResource && !recipeProduct && productKinds > 0 && productKinds <= 4; }
	}
	public record MarketEvidence(boolean known, long fairValue, boolean productionDemand)
	{
		public MarketEvidence { if (fairValue < 0 || !known && fairValue != 0) { throw new IllegalArgumentException("Invalid surplus market evidence."); } }
		public static MarketEvidence unknown() { return new MarketEvidence(false, 0, false); }
	}
	private static final Set<BodyPart> ORDINARY_ARMOR = Set.of(BodyPart.HEAD, BodyPart.CHEST, BodyPart.LEGS, BodyPart.FULL_ARMOR, BodyPart.GLOVES, BodyPart.FEET, BodyPart.L_HAND);
	private final Map<Integer, RecipeRole> _roles;
	private final Set<Integer> _rareProducts;
	private final Set<Integer> _crystals;
	private volatile IntFunction<MarketEvidence> _market = _ -> MarketEvidence.unknown();

	private PhantomLootDispositionPolicy()
	{
		final var recipes = RecipeData.getInstance(); final var items = ItemData.getInstance();
		final var products = new HashSet<Integer>(); final var rare = new HashSet<Integer>();
		for (int recipeItem : recipes.getAllItemIds())
		{
			final var recipe = recipes.getRecipeByItemId(recipeItem);
			if (recipe == null) { continue; }
			products.add(recipe.getItemId()); if (recipe.getRareItemId() > 0) { products.add(recipe.getRareItemId()); rare.add(recipe.getRareItemId()); }
		}
		final var builders = new HashMap<Integer, RoleBuilder>();
		for (int recipeItem : recipes.getAllItemIds())
		{
			final var recipe = recipes.getRecipeByItemId(recipeItem); if (recipe == null) { continue; }
			final var product = items.getTemplate(recipe.getItemId()); final var rareProduct = recipe.getRareItemId() > 0 ? items.getTemplate(recipe.getRareItemId()) : null;
			for (var ingredient : recipe.getRecipes())
			{
				final var role = builders.computeIfAbsent(ingredient.getItemId(), _ -> new RoleBuilder());
				role.common |= product == null || !product.isWeapon() && !product.isArmor();
				role.grade = Math.max(role.grade, product == null ? -1 : product.getCrystalType().getLevel());
				if (rareProduct != null) { role.grade = Math.max(role.grade, rareProduct.getCrystalType().getLevel()); }
				role.products.add(recipe.getItemId());
				role.reserve = Math.max(role.reserve, Math.min(1_000_000L, Math.multiplyExact(2L, ingredient.getQuantity())));
			}
		}
		final var roles = new HashMap<Integer, RecipeRole>();
		builders.forEach((id, role) -> roles.put(id, new RecipeRole(role.grade, role.common, products.contains(id), Math.max(2, role.reserve), role.products.size())));
		_roles = Map.copyOf(roles); _rareProducts = Set.copyOf(rare);
		final var crystals = new HashSet<Integer>(); for (var grade : CrystalType.values()) { if (grade.getCrystalId() > 0) { crystals.add(grade.getCrystalId()); } } _crystals = Set.copyOf(crystals);
	}

	public Map<Integer, RecipeRole> recipeRoles() { return _roles; }
	public void installMarketValuation(IntFunction<MarketEvidence> market) { _market = market == null ? _ -> MarketEvidence.unknown() : market; }

	/** Grade and NPC reference price alone never establish that a recipe input is disposable. */
	public static Disposition recipeSurplus(RecipeRole role, long npcPrice, MarketEvidence market, boolean warehouseFull)
	{
		if (role == null || market == null) { return Disposition.KEEP; }
		if (role.commonResource() || role.recipeProduct() || role.productKinds() > 1 || role.highestProductGrade() >= CrystalType.B.getLevel()
			|| !market.known() || market.productionDemand() || market.fairValue() > npcPrice) { return Disposition.MARKET_RESERVE; }
		if (npcPrice > 0 && npcPrice <= 5000 && role.keyPart()) { return Disposition.SELL_NPC; }
		return warehouseFull ? Disposition.KEEP : Disposition.WAREHOUSE;
	}

	private MarketEvidence market(int itemId)
	{
		try { final var result = _market.apply(itemId); return result == null ? MarketEvidence.unknown() : result; }
		catch (RuntimeException unavailable) { return MarketEvidence.unknown(); }
	}
	public boolean protectedTemplate(ItemTemplate template)
	{
		return template == null || template.isQuestItem() || template.isHeroItem() || template.getTime() != -1 || _rareProducts.contains(template.getId())
			|| template.isArmor() && !ORDINARY_ARMOR.contains(template.getBodyPart());
	}

	/** No unknown material, accessory/epic, crystal, recipe or consumable enters cheap liquidation. */
	public Disposition category(ItemTemplate template)
	{
		if (protectedTemplate(template) || _crystals.contains(template.getId())) { return Disposition.KEEP; }
		if (template.getItemType() instanceof EtcItemType type)
		{
			if (switch (type) { case SCRL_ENCHANT_WP, SCRL_ENCHANT_AM, BLESS_SCRL_ENCHANT_WP, BLESS_SCRL_ENCHANT_AM, SCRL_ENCHANT_ATTR, SCRL_INC_ENCHANT_PROP_WP, SCRL_INC_ENCHANT_PROP_AM, ANCIENT_CRYSTAL_ENCHANT_WP, ANCIENT_CRYSTAL_ENCHANT_AM -> true; default -> false; }) { return Disposition.MARKET_RESERVE; }
			final var role = _roles.get(template.getId());
			if (type != EtcItemType.MATERIAL || role == null) { return Disposition.KEEP; }
			return recipeSurplus(role, template.isSellable() ? template.getReferencePrice() / 2L : 0, market(template.getId()), false);
		}
		if (template.isWeapon() || template.isArmor())
		{
			if (commonCrystalValue(template)) { return Disposition.CRYSTALLIZE; }
			if (template.getCrystalType().getLevel() >= CrystalType.B.getLevel()) { return Disposition.WAREHOUSE; }
			return template.isSellable() && template.getReferencePrice() > 1 ? Disposition.SELL_NPC : Disposition.KEEP;
		}
		return Disposition.KEEP;
	}

	/** Native Common metadata plus actual crystal output value, never a name/grade-only liquidation rule. */
	private static boolean commonCrystalValue(ItemTemplate template)
	{
		if (template == null || !template.isCommon() || !template.isCrystallizable() || template.getCrystalCount() <= 0 || !template.isWeapon() && !template.isArmor()) { return false; }
		final var crystal = ItemData.getInstance().getTemplate(template.getCrystalItemId());
		return crystal != null && crystal.getReferencePrice() > 1 && Math.multiplyExact((long) crystal.getReferencePrice() / 2, template.getCrystalCount()) > template.getReferencePrice() / 2L;
	}

	/** A foreign listed object is only a quote; its eventual owned object must pass evaluate and native inspect again. */
	public Optional<CrystallizationService.Preview> prospectiveCrystallization(Player buyer, Item offered, Set<Integer> neededItems)
	{
		if (buyer == null || offered == null || neededItems == null || offered.getOwnerId() <= 0 || offered.getOwnerId() == buyer.getObjectId()
			|| offered.getItemLocation() != ItemLocation.INVENTORY || offered.isEquipped() || offered.isStackable() || offered.getCount() != 1 || offered.getEnchantLevel() > 0
			|| offered.isAugmented() || offered.isShadowItem() || offered.isTimeLimitedItem() || offered.getElementals() != null && offered.getElementals().length > 0 || !offered.isTradeable()
			|| buyer.isProcessingTransaction() || buyer.isInStoreMode() || buyer.hasPendingOwnedStore() || buyer.isCrafting() || protectedTemplate(offered.getTemplate()) || !commonCrystalValue(offered.getTemplate())
			|| neededItems.contains(offered.getId()) || PhantomNativeEquipmentPolicy.upgrade(buyer, offered.getTemplate())
			|| buyer.getCommonRecipeBook().stream().anyMatch(recipe -> java.util.Arrays.stream(recipe.getRecipes()).anyMatch(part -> part.getItemId() == offered.getId()))
			|| buyer.getDwarvenRecipeBook().stream().anyMatch(recipe -> java.util.Arrays.stream(recipe.getRecipes()).anyMatch(part -> part.getItemId() == offered.getId()))) { return Optional.empty(); }
		final var inspection = CrystallizationService.getInstance().inspectProspective(buyer, offered, 1);
		return inspection.eligible() ? Optional.of(inspection.preview()) : Optional.empty();
	}

	public Decision evaluate(Player player, Item item, Set<Integer> neededItems, boolean reserved)
	{
		return evaluate(player, item, neededItems, reserved, false);
	}

	public Decision evaluate(Player player, Item item, Set<Integer> neededItems, boolean reserved, boolean inventoryPressure)
	{
		if (item != null && PhantomStarterShotService.protectedStock(player, item.getId())) { return keep("protected.starter_subsidy"); }
		if (player == null || item == null || reserved || item.getOwnerId() != player.getObjectId() || item.getItemLocation() != ItemLocation.INVENTORY
			|| item.isEquipped() || item.getEnchantLevel() > 0 || item.isAugmented() || item.isShadowItem() || item.isTimeLimitedItem()
			|| item.getElementals() != null && item.getElementals().length > 0 || !item.isAvailable(player, false, true)
			|| player.isProcessingTransaction() || player.isInStoreMode() || player.hasPendingOwnedStore() || player.isCrafting()) { return keep("protected.native_owner_or_item"); }
		if (neededItems.contains(item.getId()) || player.getCommonRecipeBook().stream().anyMatch(recipe -> java.util.Arrays.stream(recipe.getRecipes()).anyMatch(part -> part.getItemId() == item.getId()))
			|| player.getDwarvenRecipeBook().stream().anyMatch(recipe -> java.util.Arrays.stream(recipe.getRecipes()).anyMatch(part -> part.getItemId() == item.getId()))
			|| PhantomNativeEquipmentPolicy.upgrade(player, item.getTemplate())) { return keep("protected.need_or_replacement"); }
		final var kind = category(item.getTemplate());
		if (kind == Disposition.CRYSTALLIZE)
		{
			if (item.isStackable() || item.getCount() != 1) { return keep("protected.native_crystallization_count"); }
			final var inspection = CrystallizationService.getInstance().inspect(player, item.getObjectId(), 1);
			return inspection.eligible() ? new Decision(Disposition.CRYSTALLIZE, 1, "native.common_crystal_value") : keep("protected.native_crystallization_" + inspection.status().name().toLowerCase(java.util.Locale.ROOT));
		}
		if (kind == Disposition.KEEP)
		{
			if (inventoryPressure && item.getTemplate().getItemType() == EtcItemType.MATERIAL && _roles.containsKey(item.getId()) && !_crystals.contains(item.getId()) && !protectedTemplate(item.getTemplate()) && item.isDepositable(true)) { return new Decision(Disposition.WAREHOUSE, Math.min(600, item.getCount()), "native.pressure.known_resources_to_bank"); }
			return keep("protected.unknown_or_conservative_category");
		}
		final var role = _roles.get(item.getId());
		final long count = Math.min(600, Math.max(0, item.getCount() - (role != null ? role.reserve() : 0)));
		if (count <= 0) { return keep("protected.recipe_reserve"); }
		if (kind == Disposition.SELL_NPC && !item.isStackable() && item.getCount() == 1 && item.getTemplate().getCrystalType().getLevel() > 0)
		{
			final var inspection = CrystallizationService.getInstance().inspect(player, item.getObjectId(), 1);
			if (inspection.eligible())
			{
				final var preview = inspection.preview(); final var crystal = ItemData.getInstance().getTemplate(preview.crystalItemId());
				if (crystal != null && (player.getInventory().getInventoryItemCount(preview.crystalItemId(), -1) < 500 || Math.multiplyExact((long) crystal.getReferencePrice() / 2, preview.crystalCount()) > item.getReferencePrice() / 2L)) { return new Decision(Disposition.CRYSTALLIZE, 1, "native.crystal_need_or_value"); }
			}
		}
		return new Decision(kind, count, "data.recipe_demand_market_and_surplus");
	}

	private static Decision keep(String reason) { return new Decision(Disposition.KEEP, 0, reason); }
	private static final class RoleBuilder { int grade = -1; boolean common; long reserve; final Set<Integer> products = new HashSet<>(); }
	public static PhantomLootDispositionPolicy getInstance() { return Holder.INSTANCE; }
	private static final class Holder { private static final PhantomLootDispositionPolicy INSTANCE = new PhantomLootDispositionPolicy(); }
}
