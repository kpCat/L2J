/*
 * This file is part of the L2J Mobius project.
 */
package org.l2jmobius.gameserver.qol;

import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.holders.creature.TimeStamp;
import org.l2jmobius.gameserver.model.item.instance.Item;

/** Personal availability is a read-only view of the canonical native item timestamps. */
public final class PersonalItemReuseService
{
	private static final int WONDROUS_CUBIC = 10632;
	private static final long DAILY_REUSE = 86_400_000L;

	private PersonalItemReuseService()
	{
	}

	public int getReuseDelay(Player player, Item item)
	{
		if ((item.getId() != WONDROUS_CUBIC) || !isEnabled(player))
		{
			return item.getReuseDelay();
		}
		return (int) Math.min(item.getReuseDelay(), PersonalCharacterQoLService.getInstance().getWondrousCubicCooldownMillis(player));
	}

	public long adjustItemRemainingReuseTime(Player player, int itemObjectId, long stockRemaining)
	{
		if (!isEnabled(player))
		{
			return stockRemaining;
		}
		final TimeStamp selected = player.getItemReuseTimeStamps().get(itemObjectId);
		if ((selected != null) && (selected.getItemId() != WONDROUS_CUBIC))
		{
			return stockRemaining;
		}
		if (selected == null)
		{
			final Item item = player.getInventory().getItemByObjectId(itemObjectId);
			if ((item == null) || (item.getId() != WONDROUS_CUBIC))
			{
				return stockRemaining;
			}
		}

		final long reuse = PersonalCharacterQoLService.getInstance().getWondrousCubicCooldownMillis(player);
		final long now = System.currentTimeMillis();
		long remaining = 0;
		for (TimeStamp timestamp : player.getItemReuseTimeStamps().values())
		{
			if (timestamp.getItemId() == WONDROUS_CUBIC)
			{
				remaining = Math.max(remaining, remaining(timestamp, reuse, now));
			}
		}
		return remaining;
	}

	public long adjustGroupRemainingReuseTime(Player player, int group, long stockRemaining)
	{
		if ((group <= 0) || !isEnabled(player))
		{
			return stockRemaining;
		}
		final long reuse = PersonalCharacterQoLService.getInstance().getWondrousCubicCooldownMillis(player);
		final long now = System.currentTimeMillis();
		boolean hasCubic = false;
		long remaining = 0;
		for (TimeStamp timestamp : player.getItemReuseTimeStamps().values())
		{
			if (timestamp.getSharedReuseGroup() == group)
			{
				if (timestamp.getItemId() == WONDROUS_CUBIC)
				{
					hasCubic = true;
					remaining = Math.max(remaining, remaining(timestamp, reuse, now));
				}
				else
				{
					remaining = Math.max(remaining, timestamp.getStamp() - now);
				}
			}
		}
		return hasCubic ? remaining : stockRemaining;
	}

	static long remaining(TimeStamp timestamp, long effectiveReuse, long now)
	{
		final long stamp = timestamp.getStamp();
		if (stamp == 0)
		{
			return 0;
		}
		// An unknown persisted duration is never interpreted as permission to shorten reuse.
		final long reduction = (timestamp.getReuse() == DAILY_REUSE) && (effectiveReuse > 0) && (effectiveReuse <= DAILY_REUSE) ? DAILY_REUSE - effectiveReuse : 0;
		return Math.max(0, stamp - reduction - now);
	}

	private boolean isEnabled(Player player)
	{
		return (player != null) && !player.isAlikeDead() && PersonalCharacterQoLService.getInstance().isWondrousCubicCooldownEnabled(player);
	}

	public static PersonalItemReuseService getInstance()
	{
		return SingletonHolder.INSTANCE;
	}

	private static class SingletonHolder
	{
		private static final PersonalItemReuseService INSTANCE = new PersonalItemReuseService();
	}
}
