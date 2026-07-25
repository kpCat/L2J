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
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
 * IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.l2jmobius.gameserver.phantoms.player;

import java.util.Objects;

import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;

/**
 * Bounded Task 004 action surface. No other Phantom action is exposed.
 */
public final class PhantomActionFacade
{
	public static final int FIXTURE_ITEM_ID = 57;
	public static final long FIXTURE_ITEM_AMOUNT = 1;

	public ActionResult performReversibleInventoryFixture(Player player, Runnable afterMutation)
	{
		Objects.requireNonNull(player, "player");
		Objects.requireNonNull(afterMutation, "afterMutation");

		synchronized (player.getInventory())
		{
			final long before = getFixtureCount(player);
			boolean added = false;
			try
			{
				if (player.getInventory().addItem(ItemProcessType.REWARD, FIXTURE_ITEM_ID, FIXTURE_ITEM_AMOUNT, null, this) == null)
				{
					throw new IllegalStateException("Could not add the Task 004 fixture item");
				}
				added = true;

				final long afterAdd = getFixtureCount(player);
				if (afterAdd != (before + FIXTURE_ITEM_AMOUNT))
				{
					throw new IllegalStateException("Fixture add did not conserve the expected delta");
				}

				afterMutation.run();

				if (player.getInventory().destroyItemByItemId(ItemProcessType.DESTROY, FIXTURE_ITEM_ID, FIXTURE_ITEM_AMOUNT, null, this) == null)
				{
					throw new IllegalStateException("Could not remove the Task 004 fixture item");
				}
				added = false;

				final long after = getFixtureCount(player);
				if (after != before)
				{
					throw new IllegalStateException("Fixture action did not restore the baseline");
				}
				return new ActionResult(before, afterAdd, after);
			}
			finally
			{
				if (added)
				{
					player.getInventory().destroyItemByItemId(ItemProcessType.DESTROY, FIXTURE_ITEM_ID, FIXTURE_ITEM_AMOUNT, null, this);
				}
			}
		}
	}

	public void restoreFixtureBaseline(Player player, long baseline)
	{
		Objects.requireNonNull(player, "player");
		synchronized (player.getInventory())
		{
			final long current = getFixtureCount(player);
			if (current < baseline)
			{
				throw new IllegalStateException("Fixture item count fell below its baseline");
			}
			if ((current > baseline) && (player.getInventory().destroyItemByItemId(ItemProcessType.DESTROY, FIXTURE_ITEM_ID, current - baseline, null, this) == null))
			{
				throw new IllegalStateException("Could not restore the Task 004 fixture baseline");
			}
		}
	}

	public long getFixtureCount(Player player)
	{
		return player.getInventory().getInventoryItemCount(FIXTURE_ITEM_ID, -1);
	}

	public record ActionResult(long before, long afterAdd, long after)
	{
	}
}
