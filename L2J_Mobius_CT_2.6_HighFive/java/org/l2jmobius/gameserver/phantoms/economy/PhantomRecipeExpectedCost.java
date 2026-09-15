package org.l2jmobius.gameserver.phantoms.economy;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntToDoubleFunction;

import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.IngredientFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.RecipeFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeSnapshot;

/** Expected canonical recipe inputs per ordinary finished item, with bounded recursive BOM. */
public final class PhantomRecipeExpectedCost
{
	private static final double MAX_VALUE = 1.0e15;
	private static final int MAX_DEPTH = 16;

	private PhantomRecipeExpectedCost()
	{
	}

	public static Quote floor(int productItemId, PhantomGameKnowledgeSnapshot knowledge, IntToDoubleFunction legitimateInputCost, IntToDoubleFunction feePerAttempt, double masterworkChanceRate)
	{
		if (knowledge == null)
		{
			throw new IllegalArgumentException("Missing recipe knowledge.");
		}
		return floor(productItemId, knowledge.recipesByProduct(), legitimateInputCost, feePerAttempt, masterworkChanceRate);
	}

	public static Quote floor(int productItemId, Map<Integer, List<RecipeFact>> recipesByProduct, IntToDoubleFunction legitimateInputCost, IntToDoubleFunction feePerAttempt, double masterworkChanceRate)
	{
		if ((productItemId <= 0) || (recipesByProduct == null) || (legitimateInputCost == null) || (feePerAttempt == null) || !Double.isFinite(masterworkChanceRate) || (masterworkChanceRate < 0) || (masterworkChanceRate > 1000))
		{
			throw new IllegalArgumentException("Unsafe recipe valuation inputs.");
		}
		final Resolver resolver = new Resolver(recipesByProduct, legitimateInputCost, feePerAttempt, masterworkChanceRate);
		final Quote result = resolver.recipe(productItemId, new HashSet<>(), 0);
		if (result == null)
		{
			throw new IllegalArgumentException("No source-backed ordinary recipe floor.");
		}
		return result;
	}

	private static final class Resolver
	{
		private final Map<Integer, List<RecipeFact>> _recipes;
		private final IntToDoubleFunction _input;
		private final IntToDoubleFunction _fee;
		private final double _masterworkRate;

		private Resolver(Map<Integer, List<RecipeFact>> recipes, IntToDoubleFunction input, IntToDoubleFunction fee, double masterworkRate)
		{
			_recipes = recipes;
			_input = input;
			_fee = fee;
			_masterworkRate = masterworkRate;
		}

		private double inputCost(int itemId, Set<Integer> path, int depth)
		{
			final double direct = _input.applyAsDouble(itemId);
			final double anchored = Double.isFinite(direct) && (direct > 0) && (direct <= MAX_VALUE) ? direct : Double.NaN;
			if (!_recipes.containsKey(itemId))
			{
				return anchored;
			}
			final Quote nested = recipe(itemId, path, depth);
			if (nested == null)
			{
				return anchored;
			}
			return Double.isNaN(anchored) ? nested.perItemCost() : Math.max(anchored, nested.perItemCost());
		}

		private Quote recipe(int itemId, Set<Integer> path, int depth)
		{
			if ((depth > MAX_DEPTH) || !path.add(itemId))
			{
				return null;
			}
			Quote best = null;
			for (RecipeFact recipe : _recipes.getOrDefault(itemId, List.of()))
			{
				if ((recipe == null) || (recipe.productItemId() != itemId) || (recipe.productCount() <= 0) || (recipe.successRate() <= 0) || (recipe.successRate() > 100))
				{
					continue;
				}
				double inputs = 0;
				boolean safe = true;
				for (IngredientFact ingredient : recipe.ingredients())
				{
					final double value = inputCost(ingredient.itemId(), path, depth + 1);
					if (!Double.isFinite(value) || (value <= 0) || (value > MAX_VALUE) || (ingredient.count() > (MAX_VALUE / value)))
					{
						safe = false;
						break;
					}
					inputs += ingredient.count() * value;
				}
				final double fee = _fee.applyAsDouble(recipe.recipeListId());
				if (!safe || !Double.isFinite(fee) || (fee < 0) || (fee > MAX_VALUE) || ((inputs + fee) > MAX_VALUE))
				{
					continue;
				}
				final double rareChance = recipe.rareProductItemId() > 0 ? Math.min(100, recipe.rareProductChance() * _masterworkRate) / 100 : 0;
				final double ordinaryYield = (recipe.successRate() / 100.0) * (1 - rareChance) * recipe.productCount();
				if (ordinaryYield <= 1.0e-9)
				{
					continue;
				}
				final double perItem = (inputs + fee) / ordinaryYield;
				if (!Double.isFinite(perItem) || (perItem <= 0) || (perItem > MAX_VALUE))
				{
					continue;
				}
				if ((best == null) || (perItem < best.perItemCost()))
				{
					best = new Quote(recipe.recipeListId(), inputs, fee, ordinaryYield, perItem);
				}
			}
			path.remove(itemId);
		return best;
		}
	}

	public record Quote(int recipeListId, double inputsPerAttempt, double feePerAttempt, double ordinaryYieldPerAttempt, double perItemCost)
	{
	}
}
