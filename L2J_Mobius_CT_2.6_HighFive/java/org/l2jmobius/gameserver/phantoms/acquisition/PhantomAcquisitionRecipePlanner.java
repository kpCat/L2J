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
package org.l2jmobius.gameserver.phantoms.acquisition;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.Deficit;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.RecipeNode;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.RecipePlan;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.PageRequest;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.RecipeFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeQuery;

/** Deterministic bounded ingredient-DAG planning only; it performs no crafting. */
public final class PhantomAcquisitionRecipePlanner
{
	private final PhantomGameKnowledgeQuery _knowledge;
	private final PhantomAcquisitionCatalog.Limits _limits;

	public PhantomAcquisitionRecipePlanner(PhantomGameKnowledgeQuery knowledge, PhantomAcquisitionCatalog.Limits limits)
	{
		_knowledge = Objects.requireNonNull(knowledge, "knowledge");
		_limits = Objects.requireNonNull(limits, "limits");
	}

	public Result plan(int itemId, long requested, Map<Integer, Long> inventory, CraftEvidence craft)
	{
		if ((itemId <= 0) || (requested <= 0) || (inventory == null) || inventory.entrySet().stream().anyMatch(entry -> (entry.getKey() <= 0) || (entry.getValue() < 0)))
		{
			return Result.blocked("recipe.invalid_request");
		}
		final List<RecipeFact> alternatives = _knowledge.recipesProducing(itemId, new PageRequest(_limits.recipesPerProduct(), null)).values().stream().filter(this::validRecipe).sorted(Comparator.comparingInt(RecipeFact::recipeListId)).toList();
		if (alternatives.isEmpty())
		{
			return Result.blocked("recipe.missing");
		}
		final List<Planned> planned = new ArrayList<>(alternatives.size());
		for (RecipeFact recipe : alternatives)
		{
			try
			{
				planned.add(build(recipe, requested, inventory, craft));
			}
			catch (BoundFailure failure)
			{
				// Another exact recipe may still fit the checkpoint bounds.
			}
		}
		if (planned.isEmpty())
		{
			return Result.blocked("recipe.bounds");
		}
		planned.sort(Comparator.comparing((Planned value) -> !value.craftEligible()).thenComparingLong(Planned::missingUnits).thenComparingInt(value -> value.plan().nodes().stream().mapToInt(RecipeNode::depth).max().orElse(0)).thenComparingInt(value -> value.plan().nodes().size()).thenComparingInt(value -> value.plan().recipeListId()));
		return new Result(planned.getFirst().plan(), "recipe.planned");
	}

	private Planned build(RecipeFact root, long requested, Map<Integer, Long> inventory, CraftEvidence craft)
	{
		final Build build = new Build(new HashMap<>(inventory));
		expand(root.productItemId(), requested, 0, root, build, new HashSet<>());
		final List<RecipeNode> nodes = build.nodes.values().stream().map(Node::snapshot).sorted(Comparator.comparingInt(RecipeNode::depth).thenComparingInt(RecipeNode::itemId)).toList();
		final List<Deficit> deficits = nodes.stream().filter(node -> node.leaf() && (node.deficit() > 0)).map(node -> new Deficit(node.itemId(), node.deficit(), !_knowledge.manorSources(node.itemId(), new PageRequest(1, null)).values().isEmpty(), false)).sorted(Comparator.comparingInt(Deficit::itemId)).toList();
		if ((nodes.size() > _limits.recipeNodes()) || (deficits.size() > _limits.deficits()))
		{
			throw new BoundFailure();
		}
		final long batches = ceiling(requested, root.productCount());
		final long output = Math.multiplyExact(batches, root.productCount());
		final boolean eligible = craft.eligible(root);
		final String reason = eligible ? "" : "recipe.craft_evidence_missing";
		final RecipePlan plan = new RecipePlan(root.recipeListId(), root.productItemId(), requested, batches, output, root.successRate(), root.dwarven(), craft.skillId(), craft.skillLevel(), nodes, deficits, reason);
		final long missing = deficits.stream().mapToLong(Deficit::count).reduce(0, Math::addExact);
		return new Planned(plan, eligible, missing);
	}

	private void expand(int itemId, long requested, int depth, RecipeFact forcedRecipe, Build build, Set<Integer> path)
	{
		if ((depth > _limits.recipeDepth()) || !path.add(itemId))
		{
			throw new BoundFailure();
		}
		try
		{
			Node node = build.nodes.get(itemId);
			if (node == null)
			{
				if (build.nodes.size() >= _limits.recipeNodes())
				{
					throw new BoundFailure();
				}
				final long available = build.inventory.getOrDefault(itemId, 0L);
				final long used = Math.min(available, requested);
				build.inventory.put(itemId, available - used);
				node = new Node(itemId, requested, used, depth);
				build.nodes.put(itemId, node);
			}
			else
			{
				final long available = build.inventory.getOrDefault(itemId, 0L);
				final long used = Math.min(available, requested);
				build.inventory.put(itemId, available - used);
				node.requested = Math.addExact(node.requested, requested);
				node.inventoryUsed = Math.addExact(node.inventoryUsed, used);
				node.depth = Math.min(node.depth, depth);
			}
			final long deficit = node.requested - node.inventoryUsed;
			final RecipeFact recipe = forcedRecipe != null ? forcedRecipe : chooseRecipe(itemId);
			if ((deficit == 0) || (recipe == null))
			{
				return;
			}
			if ((depth >= _limits.recipeDepth()) || (recipe.productItemId() != itemId))
			{
				throw new BoundFailure();
			}
			node.recipeListId = recipe.recipeListId();
			final long requiredBatches = ceiling(deficit, recipe.productCount());
			final long deltaBatches = requiredBatches - node.expandedBatches;
			if (deltaBatches <= 0)
			{
				return;
			}
			node.expandedBatches = requiredBatches;
			for (var ingredient : recipe.ingredients())
			{
				expand(ingredient.itemId(), Math.multiplyExact(deltaBatches, ingredient.count()), depth + 1, null, build, path);
			}
		}
		finally
		{
			path.remove(itemId);
		}
	}

	private RecipeFact chooseRecipe(int itemId)
	{
		return _knowledge.recipesProducing(itemId, new PageRequest(_limits.recipesPerProduct(), null)).values().stream().filter(this::validRecipe).min(Comparator.comparingInt(RecipeFact::recipeListId)).orElse(null);
	}

	private boolean validRecipe(RecipeFact recipe)
	{
		return (recipe.recipeListId() > 0) && (recipe.productItemId() > 0) && (recipe.productCount() > 0) && (recipe.successRate() >= 0) && (recipe.successRate() <= 100) && !recipe.ingredients().isEmpty() && recipe.ingredients().stream().allMatch(ingredient -> (ingredient.itemId() > 0) && (ingredient.count() > 0));
	}

	private static long ceiling(long value, long divisor)
	{
		return Math.addExact(value, divisor - 1) / divisor;
	}

	public record CraftEvidence(int skillId, int skillLevel, boolean ready)
	{
		public CraftEvidence
		{
			if ((skillId < 0) || (skillLevel < 0) || ((skillId == 0) != (skillLevel == 0)))
			{
				throw new IllegalArgumentException("Invalid acquisition craft evidence.");
			}
		}

		private boolean eligible(RecipeFact recipe)
		{
			return ready && (skillId > 0) && (skillLevel >= recipe.craftLevel());
		}
	}

	public record Result(RecipePlan plan, String reasonKey)
	{
		public Result
		{
			reasonKey = Objects.requireNonNull(reasonKey, "reasonKey");
		}

		public static Result blocked(String reason)
		{
			return new Result(null, reason);
		}

		public boolean planned()
		{
			return plan != null;
		}
	}

	private static final class Build
	{
		private final Map<Integer, Long> inventory;
		private final Map<Integer, Node> nodes = new LinkedHashMap<>();

		private Build(Map<Integer, Long> inventory)
		{
			this.inventory = inventory;
		}
	}

	private static final class Node
	{
		private final int itemId;
		private long requested;
		private long inventoryUsed;
		private int depth;
		private int recipeListId;
		private long expandedBatches;

		private Node(int itemId, long requested, long inventoryUsed, int depth)
		{
			this.itemId = itemId;
			this.requested = requested;
			this.inventoryUsed = inventoryUsed;
			this.depth = depth;
		}

		private RecipeNode snapshot()
		{
			return new RecipeNode(itemId, requested, inventoryUsed, requested - inventoryUsed, recipeListId, depth, recipeListId == 0);
		}
	}

	private record Planned(RecipePlan plan, boolean craftEligible, long missingUnits)
	{
	}

	private static final class BoundFailure extends RuntimeException
	{
		private static final long serialVersionUID = 1L;
	}
}
