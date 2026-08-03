/*
 * Copyright (c) 2013 L2jMobius
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.l2jmobius.gameserver.phantoms.economy;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.l2jmobius.gameserver.config.PlayerConfig;
import org.l2jmobius.gameserver.data.enums.StatType;
import org.l2jmobius.gameserver.data.holders.RecipeHolder;
import org.l2jmobius.gameserver.data.holders.RecipeStatHolder;
import org.l2jmobius.gameserver.data.xml.EnchantItemData;
import org.l2jmobius.gameserver.data.xml.ItemData;
import org.l2jmobius.gameserver.model.item.ItemTemplate;
import org.l2jmobius.gameserver.model.item.enchant.EnchantResultType;
import org.l2jmobius.gameserver.model.item.enchant.EnchantScroll;
import org.l2jmobius.gameserver.model.item.enchant.EnchantSupportItem;
import org.l2jmobius.gameserver.model.item.recipe.RecipeList;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionCatalog.Method;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.RecipePlan;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundModel;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundModel.RandomStep;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.ItemLocation;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyOperation.Result;

/** Exact deterministic projections of current shipped non-ALT craft and enchant rules. */
public final class PhantomEconomyProjection
{
	private PhantomEconomyProjection()
	{
	}

	public static CraftOutcome craft(CraftRequest request)
	{
		Objects.requireNonNull(request);
		final PhantomAcquisitionState acquisition = request.acquisition();
		final RecipePlan plan = acquisition.recipePlan();
		final RecipeList recipe = request.recipe();
		if ((acquisition.status() != PhantomAcquisitionState.Status.PLANNING_ONLY) || (acquisition.selectedSource() == null) || (acquisition.selectedSource().method() != Method.RECIPE_PREPARATION) || (plan == null) || !plan.deficits().isEmpty() || (acquisition.progress() >= acquisition.requiredAmount()))
		{
			return CraftOutcome.rejected(Result.STALE_AUTHORITY);
		}
		if (PlayerConfig.ALT_GAME_CREATION || request.policy().craft().allowAltGameCreation() || !PlayerConfig.IS_CRAFTING_ENABLED)
		{
			return CraftOutcome.rejected(Result.ACTIVE_REQUIRED);
		}
		final int craftSkillId = recipe.isDwarvenRecipe() ? org.l2jmobius.gameserver.model.skill.CommonSkill.CREATE_DWARVEN.getId() : org.l2jmobius.gameserver.model.skill.CommonSkill.CREATE_COMMON.getId();
		if ((plan.recipeListId() != recipe.getId()) || (plan.productItemId() != recipe.getItemId()) || (plan.successRate() != recipe.getSuccessRate()) || (plan.dwarven() != recipe.isDwarvenRecipe()) || (plan.craftSkillId() != craftSkillId) || (plan.craftSkillLevel() != request.craftSkillLevel()) || (recipe.getLevel() > request.craftSkillLevel()) || !request.recipeKnown())
		{
			return CraftOutcome.rejected(Result.STALE_AUTHORITY);
		}
		final Map<Integer, Long> deltas = new LinkedHashMap<>();
		for (RecipeHolder ingredient : recipe.getRecipes())
		{
			if ((ingredient.getQuantity() <= 0) || (request.inventory().getOrDefault(ingredient.getItemId(), 0L) < ingredient.getQuantity()))
			{
				return CraftOutcome.rejected(Result.CONFLICT);
			}
			deltas.merge(ingredient.getItemId(), -(long) ingredient.getQuantity(), Math::addExact);
		}
		double hp = 0;
		double mp = 0;
		for (RecipeStatHolder stat : recipe.getStatUse())
		{
			if (stat.getType() == StatType.HP)
			{
				hp += stat.getValue();
			}
			else if (stat.getType() == StatType.MP)
			{
				mp += stat.getValue();
			}
			else
			{
				return CraftOutcome.rejected(Result.ACTIVE_REQUIRED);
			}
		}
		if ((request.currentHp() <= hp) || (request.currentMp() < mp))
		{
			return CraftOutcome.rejected(Result.CONFLICT);
		}
		RandomStep random = PhantomBackgroundModel.randomStep(request.rngState());
		final boolean success = (random.value() * 100) < recipe.getSuccessRate();
		int productItemId = 0;
		int productCount = 0;
		boolean rare = false;
		if (success)
		{
			productItemId = recipe.getItemId();
			productCount = recipe.getCount();
			if ((recipe.getRareItemId() != -1) && ((recipe.getRareItemId() == recipe.getItemId()) || PlayerConfig.CRAFT_MASTERWORK))
			{
				random = PhantomBackgroundModel.randomStep(random.nextState());
				if ((random.value() * 100) < (recipe.getRarity() * PlayerConfig.CRAFT_MASTERWORK_CHANCE_RATE))
				{
					productItemId = recipe.getRareItemId();
					productCount = recipe.getRareCount();
					rare = true;
				}
			}
			final ItemTemplate output = ItemData.getInstance().getTemplate(productItemId);
			if ((output == null) || (output.getTime() != -1) || (!output.isStackable() && (productCount > PhantomBackgroundModel.MAX_NEW_NON_STACKABLE_OBJECTS)))
			{
				return CraftOutcome.rejected(Result.ACTIVE_REQUIRED);
			}
			deltas.merge(productItemId, (long) productCount, Math::addExact);
		}
		final String authority = craftAuthority(acquisition, recipe, request.policy());
		return new CraftOutcome(success ? Result.SUCCESS : Result.CRAFT_FAILED, Map.copyOf(deltas), hp, mp, random.nextState(), productItemId, productCount, rare, authority);
	}

	public static String craftAuthority(PhantomAcquisitionState acquisition, RecipeList recipe, PhantomEconomyPolicy policy)
	{
		return craftAuthorityFacts(acquisition, recipe, policy).hash();
	}

	public static String enchantAuthority(ItemTemplate target, int enchantLevel, EnchantScroll scroll, EnchantSupportItem support, PhantomEconomyPolicy policy)
	{
		return enchantAuthorityFacts(target, enchantLevel, scroll, support, policy).hash();
	}

	public static AuthorityFacts craftAuthorityFacts(PhantomAcquisitionState acquisition, RecipeList recipe, PhantomEconomyPolicy policy)
	{
		Objects.requireNonNull(acquisition);
		Objects.requireNonNull(recipe);
		Objects.requireNonNull(policy);
		final List<AuthorityFact> facts = new ArrayList<>();
		add(facts, "policy.hash", policy.hash());
		add(facts, "acquisition.catalog_hash", acquisition.hashes().catalog());
		add(facts, "acquisition.knowledge_hash", acquisition.hashes().knowledge());
		add(facts, "acquisition.progression_hash", acquisition.hashes().progression());
		add(facts, "acquisition.selected_source_id", acquisition.selectedSource().sourceId());
		final RecipePlan plan = acquisition.recipePlan();
		add(facts, "plan.recipe_list_id", plan.recipeListId());
		add(facts, "plan.product_item_id", plan.productItemId());
		add(facts, "plan.requested_output", plan.requestedOutput());
		add(facts, "plan.batch_count", plan.batchCount());
		add(facts, "plan.product_output", plan.productOutput());
		add(facts, "plan.success_rate", plan.successRate());
		add(facts, "plan.dwarven", plan.dwarven());
		add(facts, "plan.craft_skill_id", plan.craftSkillId());
		add(facts, "plan.craft_skill_level", plan.craftSkillLevel());
		add(facts, "plan.reason_key", plan.reasonKey());
		add(facts, "plan.node_count", plan.nodes().size());
		for (int i = 0; i < plan.nodes().size(); i++)
		{
			final var node = plan.nodes().get(i);
			final String prefix = "plan.node." + i + ".";
			add(facts, prefix + "item_id", node.itemId());
			add(facts, prefix + "requested_count", node.requestedCount());
			add(facts, prefix + "inventory_used", node.inventoryUsed());
			add(facts, prefix + "deficit", node.deficit());
			add(facts, prefix + "recipe_list_id", node.recipeListId());
			add(facts, prefix + "depth", node.depth());
			add(facts, prefix + "leaf", node.leaf());
		}
		add(facts, "plan.deficit_count", plan.deficits().size());
		for (int i = 0; i < plan.deficits().size(); i++)
		{
			final var deficit = plan.deficits().get(i);
			final String prefix = "plan.deficit." + i + ".";
			add(facts, prefix + "item_id", deficit.itemId());
			add(facts, prefix + "count", deficit.count());
			add(facts, prefix + "manor_deferred", deficit.manorDeferred());
			add(facts, prefix + "quest_deferred", deficit.questDeferred());
		}
		add(facts, "recipe.id", recipe.getId());
		add(facts, "recipe.recipe_id", recipe.getRecipeId());
		add(facts, "recipe.level", recipe.getLevel());
		add(facts, "recipe.dwarven", recipe.isDwarvenRecipe());
		add(facts, "recipe.product_item_id", recipe.getItemId());
		add(facts, "recipe.product_count", recipe.getCount());
		add(facts, "recipe.success_rate", recipe.getSuccessRate());
		add(facts, "recipe.rare_item_id", recipe.getRareItemId());
		add(facts, "recipe.rare_count", recipe.getRareCount());
		add(facts, "recipe.rarity", recipe.getRarity());
		add(facts, "recipe.ingredient_count", recipe.getRecipes().length);
		for (int i = 0; i < recipe.getRecipes().length; i++)
		{
			add(facts, "recipe.ingredient." + i + ".item_id", recipe.getRecipes()[i].getItemId());
			add(facts, "recipe.ingredient." + i + ".quantity", recipe.getRecipes()[i].getQuantity());
		}
		add(facts, "recipe.stat_count", recipe.getStatUse().length);
		for (int i = 0; i < recipe.getStatUse().length; i++)
		{
			add(facts, "recipe.stat." + i + ".type", recipe.getStatUse()[i].getType().name());
			add(facts, "recipe.stat." + i + ".value", recipe.getStatUse()[i].getValue());
		}
		final int craftSkillId = recipe.isDwarvenRecipe() ? org.l2jmobius.gameserver.model.skill.CommonSkill.CREATE_DWARVEN.getId() : org.l2jmobius.gameserver.model.skill.CommonSkill.CREATE_COMMON.getId();
		add(facts, "recipe.craft_skill_id", craftSkillId);
		add(facts, "recipe.craft_skill_level", plan.craftSkillLevel());
		addOutputFacts(facts, "recipe.normal_output.", ItemData.getInstance().getTemplate(recipe.getItemId()));
		addOutputFacts(facts, "recipe.rare_output.", recipe.getRareItemId() > 0 ? ItemData.getInstance().getTemplate(recipe.getRareItemId()) : null);
		add(facts, "config.alt_game_creation", PlayerConfig.ALT_GAME_CREATION);
		add(facts, "config.crafting_enabled", PlayerConfig.IS_CRAFTING_ENABLED);
		add(facts, "config.craft_masterwork", PlayerConfig.CRAFT_MASTERWORK);
		add(facts, "config.craft_masterwork_chance_rate", doubleHex(PlayerConfig.CRAFT_MASTERWORK_CHANCE_RATE));
		return new AuthorityFacts(facts);
	}

	public static AuthorityFacts enchantAuthorityFacts(ItemTemplate target, int enchantLevel, EnchantScroll scroll, EnchantSupportItem support, PhantomEconomyPolicy policy)
	{
		Objects.requireNonNull(target);
		Objects.requireNonNull(scroll);
		Objects.requireNonNull(policy);
		final List<AuthorityFact> facts = new ArrayList<>();
		add(facts, "policy.hash", policy.hash());
		add(facts, "target.item_id", target.getId());
		add(facts, "target.enchant_level", enchantLevel);
		add(facts, "target.type2", target.getType2());
		add(facts, "target.crystal_grade", target.getCrystalType().name());
		add(facts, "target.enchantable", target.isEnchantable());
		add(facts, "target.crystallizable", target.isCrystallizable());
		add(facts, "target.crystal_item_id", target.getCrystalItemId());
		add(facts, "target.crystal_count", target.getCrystalCount());
		add(facts, "target.crystal_count_at_level", target.getCrystalCount(enchantLevel));
		add(facts, "target.crystal_destruction_consequence", target.isCrystallizable() && (target.getCrystalItemId() != 0) ? Math.max(1, target.getCrystalCount(enchantLevel) - ((target.getCrystalCount() + 1) / 2)) : 0);
		add(facts, "target.reference_price", target.getReferencePrice());
		addEnchantItemFacts(facts, "scroll.", scroll);
		add(facts, "scroll.safe", scroll.isSafe());
		add(facts, "scroll.blessed", scroll.isBlessed());
		add(facts, "scroll.base_chance", doubleHex(scroll.getChance(target, enchantLevel)));
		addEnchantItemFacts(facts, "support.", support);
		add(facts, "combination.valid", scroll.isValid(target, enchantLevel, support));
		add(facts, "config.disable_over_enchanting", PlayerConfig.DISABLE_OVER_ENCHANTING);
		return new AuthorityFacts(facts);
	}

	public static EnchantOutcome enchant(EnchantRequest request)
	{
		Objects.requireNonNull(request);
		final PhantomEnchantGoalSpec goal = request.goal();
		if (request.targetLocation() == ItemLocation.PAPERDOLL)
		{
			return EnchantOutcome.rejected(Result.ACTIVE_REQUIRED);
		}
		if ((request.targetLocation() != ItemLocation.INVENTORY) || request.augmented() || request.elemented() || request.timeLimited() || request.leased())
		{
			return EnchantOutcome.rejected(Result.ACTIVE_REQUIRED);
		}
		final EnchantScroll scroll = EnchantItemData.getInstance().getScrolls().stream().filter(value -> value.getId() == request.scrollItemId()).findFirst().orElse(null);
		final EnchantSupportItem support = request.supportItemId() == 0 ? null : EnchantItemData.getInstance().getSupportItemById(request.supportItemId());
		if ((scroll == null) || ((request.supportItemId() != 0) && (support == null)) || !scroll.isValid(request.target(), request.enchantLevel(), support))
		{
			return EnchantOutcome.rejected(Result.STALE_AUTHORITY);
		}
		final long expense = Math.addExact(Math.max(0, scroll.getItem().getReferencePrice()), support == null ? 0 : Math.max(0, support.getItem().getReferencePrice()));
		final long requiredReplacement = Math.floorDiv(Math.addExact(Math.multiplyExact((long) Math.max(0, request.target().getReferencePrice()), request.policy().risk().replacementReservePercent()), 99), 100);
		final boolean destructive = !scroll.isSafe() && !scroll.isBlessed();
		final long maximumExpense = Math.floorDiv(Math.multiplyExact((long) Math.max(0, request.target().getReferencePrice()), request.policy().risk().maximumExpensePercent()), 100);
		if (!goal.accepts(request.enchantLevel(), request.scrollItemId(), request.supportItemId(), expense) || ((goal.expenseUsed() + expense) > request.expenseBudget()) || (goal.replacementReserve() < requiredReplacement) || (goal.replacementReserve() > request.replacementEvidence()) || (destructive && (!goal.destructionPermitted() || (request.target().getReferencePrice() > request.riskBudget()) || (expense > maximumExpense))))
		{
			return EnchantOutcome.rejected(Result.CONFLICT);
		}
		final RandomStep random = PhantomBackgroundModel.randomStep(request.rngState());
		final EnchantResultType rolled = scroll.calculateSuccess(request.target(), request.enchantLevel(), support, random.value() * 100);
		if (rolled == EnchantResultType.ERROR)
		{
			return EnchantOutcome.rejected(Result.STALE_AUTHORITY);
		}
		Result result;
		int nextEnchant = request.enchantLevel();
		int crystalItemId = 0;
		int crystalCount = 0;
		boolean targetSurvives = true;
		if (rolled == EnchantResultType.SUCCESS)
		{
			if (scroll.getChance(request.target(), request.enchantLevel()) > 0)
			{
				nextEnchant++;
			}
			result = Result.SUCCESS;
		}
		else if (scroll.isSafe())
		{
			result = Result.SAFE_FAILURE;
		}
		else if (scroll.isBlessed())
		{
			nextEnchant = 0;
			result = Result.BLESSED_RESET;
		}
		else
		{
			targetSurvives = false;
			result = Result.DESTROYED_WITH_CRYSTALS;
			crystalItemId = request.target().getCrystalItemId();
			if ((crystalItemId != 0) && request.target().isCrystallizable())
			{
				crystalCount = Math.max(1, request.target().getCrystalCount(request.enchantLevel()) - ((request.target().getCrystalCount() + 1) / 2));
			}
		}
		final String authority = enchantAuthority(request.target(), request.enchantLevel(), scroll, support, request.policy());
		return new EnchantOutcome(result, nextEnchant, targetSurvives, crystalItemId, crystalCount, random.nextState(), expense, authority);
	}

	private static void addOutputFacts(List<AuthorityFact> facts, String prefix, ItemTemplate template)
	{
		add(facts, prefix + "present", template != null);
		if (template != null)
		{
			add(facts, prefix + "item_id", template.getId());
			add(facts, prefix + "stackable", template.isStackable());
			add(facts, prefix + "time", template.getTime());
			add(facts, prefix + "weight", template.getWeight());
		}
	}

	private static void addEnchantItemFacts(List<AuthorityFact> facts, String prefix, org.l2jmobius.gameserver.model.item.enchant.AbstractEnchantItem item)
	{
		add(facts, prefix + "present", item != null);
		if (item != null)
		{
			add(facts, prefix + "item_id", item.getId());
			add(facts, prefix + "grade", item.getGrade().name());
			add(facts, prefix + "maximum_enchant", item.getMaxEnchantLevel());
			add(facts, prefix + "bonus_rate", doubleHex(item.getBonusRate()));
			add(facts, prefix + "weapon", item.isWeapon());
		}
	}

	private static void add(List<AuthorityFact> facts, String key, Object value)
	{
		facts.add(new AuthorityFact(key, String.valueOf(value)));
	}

	private static String doubleHex(double value)
	{
		return String.format("%016x", Double.doubleToRawLongBits(value));
	}

	public record CraftRequest(PhantomAcquisitionState acquisition, RecipeList recipe, boolean recipeKnown, int craftSkillLevel, Map<Integer, Long> inventory, double currentHp, double currentMp, long rngState, PhantomEconomyPolicy policy)
	{
		public CraftRequest
		{
			inventory = Map.copyOf(inventory);
		}
	}

	public record CraftOutcome(Result result, Map<Integer, Long> itemDeltas, double hpConsumed, double mpConsumed, long nextRngState, int productItemId, int productCount, boolean rare, String authorityHash)
	{
		public static CraftOutcome rejected(Result result)
		{
			return new CraftOutcome(result, Map.of(), 0, 0, 0, 0, 0, false, "");
		}

		public boolean executable()
		{
			return (result == Result.SUCCESS) || (result == Result.CRAFT_FAILED);
		}
	}

	public record EnchantRequest(PhantomEnchantGoalSpec goal, ItemTemplate target, int targetObjectId, int enchantLevel, ItemLocation targetLocation, int scrollObjectId, int scrollItemId, int supportObjectId, int supportItemId, boolean augmented, boolean elemented, boolean timeLimited, boolean leased, long replacementEvidence, long riskBudget, long expenseBudget, long rngState, PhantomEconomyPolicy policy)
	{
	}

	public record AuthorityFact(String key, String value)
	{
		public AuthorityFact
		{
			key = Objects.requireNonNull(key);
			value = Objects.requireNonNull(value);
			if (key.isEmpty() || (key.getBytes(StandardCharsets.UTF_8).length > 160) || (value.getBytes(StandardCharsets.UTF_8).length > 512))
			{
				throw new IllegalArgumentException("Invalid economy authority fact.");
			}
		}
	}

	public record AuthorityFacts(List<AuthorityFact> facts)
	{
		public AuthorityFacts
		{
			facts = List.copyOf(facts);
			if (facts.isEmpty() || (facts.stream().map(AuthorityFact::key).distinct().count() != facts.size()))
			{
				throw new IllegalArgumentException("Economy authority facts must be non-empty and uniquely named.");
			}
		}

		public String canonical()
		{
			final StringBuilder result = new StringBuilder();
			for (AuthorityFact fact : facts)
			{
				result.append(fact.key().getBytes(StandardCharsets.UTF_8).length).append(':').append(fact.key()).append('=').append(fact.value().getBytes(StandardCharsets.UTF_8).length).append(':').append(fact.value()).append(';');
			}
			return result.toString();
		}

		public String hash()
		{
			return PhantomEconomyOperation.sha256(canonical());
		}
	}

	public record EnchantOutcome(Result result, int nextEnchantLevel, boolean targetSurvives, int crystalItemId, int crystalCount, long nextRngState, long expense, String authorityHash)
	{
		public static EnchantOutcome rejected(Result result)
		{
			return new EnchantOutcome(result, 0, false, 0, 0, 0, 0, "");
		}

		public boolean executable()
		{
			return (result == Result.SUCCESS) || (result == Result.SAFE_FAILURE) || (result == Result.BLESSED_RESET) || (result == Result.DESTROYED_WITH_CRYSTALS);
		}
	}
}
