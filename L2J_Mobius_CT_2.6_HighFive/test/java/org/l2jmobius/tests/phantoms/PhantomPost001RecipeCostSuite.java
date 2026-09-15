package org.l2jmobius.tests.phantoms;

import java.util.List;
import java.util.Map;

import org.l2jmobius.gameserver.phantoms.economy.PhantomRecipeExpectedCost;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeAuthority;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.IngredientFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.RecipeFact;

public final class PhantomPost001RecipeCostSuite implements PhantomTestSuite
{
	@Override
	public String id()
	{
		return "post001-recipe-cost";
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("canonical-bom-success-and-fee", context ->
		{
			final RecipeFact recipe = recipe(1, 1000, 1, 60, 0, 0, List.of(new IngredientFact(2000, 3), new IngredientFact(3000, 2)));
			final PhantomRecipeExpectedCost.Quote cost = PhantomRecipeExpectedCost.floor(1000, Map.of(1000, List.of(recipe)), id -> id == 2000 ? 10 : id == 3000 ? 20 : -1, id -> id == 1 ? 5 : 0, 1);
			near(70, cost.inputsPerAttempt());
			near(.6, cost.ordinaryYieldPerAttempt());
			near(125, cost.perItemCost());
		});
		registry.add("nested-input-and-rare-diversion", context ->
		{
			final RecipeFact nested = recipe(2, 2000, 1, 100, 0, 0, List.of(new IngredientFact(4000, 2)));
			final RecipeFact finished = recipe(1, 1000, 1, 100, 9000, 10, List.of(new IngredientFact(2000, 3)));
			final PhantomRecipeExpectedCost.Quote cost = PhantomRecipeExpectedCost.floor(1000, Map.of(1000, List.of(finished), 2000, List.of(nested)), id -> id == 4000 ? 10 : -1, id -> 0, 2);
			near(60, cost.inputsPerAttempt());
			near(.8, cost.ordinaryYieldPerAttempt());
			near(75, cost.perItemCost());
		});
		registry.add("anchored-intermediate-keeps-recursive-craft-floor", context ->
		{
			final RecipeFact nested = recipe(2, 2000, 1, 50, 0, 0, List.of(new IngredientFact(4000, 2)));
			final RecipeFact finished = recipe(1, 1000, 1, 100, 0, 0, List.of(new IngredientFact(2000, 3)));
			final PhantomRecipeExpectedCost.Quote cost = PhantomRecipeExpectedCost.floor(1000, Map.of(1000, List.of(finished), 2000, List.of(nested)), id -> id == 2000 ? 5 : id == 4000 ? 10 : -1, id -> 0, 1);
			// Intermediate direct anchor 5 cannot erase its canonical 2*10/.5=40 floor.
			near(120, cost.inputsPerAttempt());
			near(120, cost.perItemCost());
		});
		registry.add("cyclic-and-unanchored-bom-excluded", context ->
		{
			final RecipeFact first = recipe(1, 1000, 1, 100, 0, 0, List.of(new IngredientFact(2000, 1)));
			final RecipeFact second = recipe(2, 2000, 1, 100, 0, 0, List.of(new IngredientFact(1000, 1)));
			try
			{
				PhantomRecipeExpectedCost.floor(1000, Map.of(1000, List.of(first), 2000, List.of(second)), id -> -1, id -> 0, 1);
				throw new AssertionError("Circular recipe must fail closed.");
			}
			catch (IllegalArgumentException expected)
			{
			}
		});
	}

	private static RecipeFact recipe(int recipeId, int productId, long count, int successRate, int rareId, int rareChance, List<IngredientFact> inputs)
	{
		return new RecipeFact(recipeId, recipeId + 100, productId, count, rareId, rareId > 0 ? 1 : 0, rareChance, 1, successRate, true, inputs, PhantomGameKnowledgeAuthority.SERVER_LOADER_FACT);
	}

	private static void near(double expected, double actual)
	{
		if (!Double.isFinite(actual) || (Math.abs(expected - actual) > (1.0e-8 * Math.max(1, Math.abs(expected)))))
		{
			throw new AssertionError("Expected " + expected + ", got " + actual);
		}
	}
}
