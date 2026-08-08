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
package org.l2jmobius.gameserver.managers;

import java.util.List;
import java.util.Objects;

/** Immutable packet-independent observation seam for canonical RecipeManager work. */
@FunctionalInterface
public interface RecipeCraftObserver
{
	RecipeCraftObserver NONE = event ->
	{
	};

	void onEvent(Event event);

	enum Type
	{
		ACCEPTED,
		FEE_TRANSFERRED,
		INGREDIENTS_CONSUMED,
		SUCCESS_PRODUCT,
		RARE_PRODUCT,
		CRAFT_FAILED,
		ABORTED
	}

	record ItemDelta(int itemId, long count)
	{
		public ItemDelta
		{
			if ((itemId <= 0) || (count <= 0))
			{
				throw new IllegalArgumentException("Invalid craft item delta.");
			}
		}
	}

	record Authority(int recipeListId, int recipeItemId, int productItemId, long productCount, int rareProductItemId, long rareProductCount, int rarity, int craftLevel, int successRate, boolean dwarven, int skillId, int skillLevel, long listingPrice, List<ItemDelta> requiredIngredients)
	{
		public Authority
		{
			requiredIngredients = List.copyOf(requiredIngredients);
			if ((recipeListId <= 0) || (recipeItemId <= 0) || (productItemId <= 0) || (productCount <= 0) || (rareProductItemId < -1) || (rareProductCount < 0) || (rarity < 0) || (craftLevel <= 0) || (successRate < 0) || (successRate > 100) || (skillId <= 0) || (skillLevel < 0) || (listingPrice < 0) || requiredIngredients.isEmpty() || (requiredIngredients.size() > 32))
			{
				throw new IllegalArgumentException("Invalid craft authority.");
			}
			if ((rareProductItemId <= 0) && (rareProductCount != 0))
			{
				throw new IllegalArgumentException("Invalid rare craft authority.");
			}
		}
	}

	record Event(Type type, int recipeListId, int recipeItemId, int crafterObjectId, int targetObjectId, Authority authority, List<ItemDelta> items, long feeTransferred, long crafterAdenaDelta, long targetAdenaDelta, long expConsequence, long spConsequence, double hpConsumed, double mpConsumed)
	{
		public Event
		{
			Objects.requireNonNull(type);
			Objects.requireNonNull(authority);
			items = List.copyOf(items);
			if ((recipeListId <= 0) || (recipeItemId <= 0) || (recipeListId != authority.recipeListId()) || (recipeItemId != authority.recipeItemId()) || (crafterObjectId <= 0) || (targetObjectId <= 0) || (items.size() > 32) || (feeTransferred < 0) || (expConsequence < 0) || (spConsequence < 0) || !Double.isFinite(hpConsumed) || !Double.isFinite(mpConsumed) || (hpConsumed < 0) || (mpConsumed < 0))
			{
				throw new IllegalArgumentException("Invalid craft observation event.");
			}
		}
	}
}
