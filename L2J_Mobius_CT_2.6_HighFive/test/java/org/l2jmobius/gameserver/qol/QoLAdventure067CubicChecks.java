/*
 * This file is part of the L2J Mobius project.
 */
package org.l2jmobius.gameserver.qol;

import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.holders.creature.TimeStamp;
import org.l2jmobius.gameserver.model.item.instance.Item;
import org.l2jmobius.tests.phantoms.PhantomAssertions;

/** Native timestamp checks; the owning suite supplies isolated, normally admitted Players. */
public final class QoLAdventure067CubicChecks
{
	private QoLAdventure067CubicChecks()
	{
	}

	public static void legacyTimestampUsesPersonalHour(Player player, Item cubic)
	{
		PhantomAssertions.assertEquals(10632, cubic.getId(), "Cubic check requires the native Wondrous Cubic.");
		final TimeStamp previous = player.getItemReuseTimeStamps().get(cubic.getObjectId());
		try
		{
			final long canonicalExpiry = System.currentTimeMillis() + 82_740_000L;
			player.addTimeStampItem(cubic, 86_400_000L, canonicalExpiry);
			PhantomAssertions.assertEquals(0L, player.getItemRemainingReuseTime(cubic.getObjectId()), "An old daily timestamp still blocked the personal Cubic after sixty-one minutes.");
			final TimeStamp retained = player.getItemReuseTimeStamps().get(cubic.getObjectId());
			PhantomAssertions.assertEquals(86_400_000L, retained.getReuse(), "Personal availability rewrote the canonical daily duration.");
			PhantomAssertions.assertEquals(canonicalExpiry, retained.getStamp(), "Personal availability cleared the canonical daily expiry.");
			PhantomAssertions.assertEquals(3_600_000, PersonalItemReuseService.getInstance().getReuseDelay(player, cubic), "Personal Cubic packets did not use the effective hourly duration.");
		}
		finally
		{
			restore(player, cubic, previous);
		}
	}

	public static void recentTimestampBlocksPersonalReuse(Player player, Item cubic)
	{
		final TimeStamp previous = player.getItemReuseTimeStamps().get(cubic.getObjectId());
		try
		{
			player.addTimeStampItem(cubic, 86_400_000L, System.currentTimeMillis() + 86_340_000L);
			final long remaining = player.getItemRemainingReuseTime(cubic.getObjectId());
			PhantomAssertions.assertTrue((remaining > 3_530_000L) && (remaining <= 3_540_000L), "A Cubic used one minute ago did not retain fifty-nine minutes of personal reuse.");
			final TimeStamp timestamp = player.getItemReuseTimeStamps().get(cubic.getObjectId());
			final long lastUse = timestamp.getStamp() - 86_400_000L;
			PhantomAssertions.assertEquals(1_000L, PersonalItemReuseService.remaining(timestamp, 3_600_000L, lastUse + 3_599_000L), "Cubic reuse ended before the final second.");
			PhantomAssertions.assertEquals(1L, PersonalItemReuseService.remaining(timestamp, 3_600_000L, lastUse + 3_599_999L), "Cubic reuse ended before the final millisecond.");
			PhantomAssertions.assertEquals(0L, PersonalItemReuseService.remaining(timestamp, 3_600_000L, lastUse + 3_600_000L), "Cubic reuse did not end at sixty minutes.");
		}
		finally
		{
			restore(player, cubic, previous);
		}
	}

	public static void disabledTimestampRetainsDailyReuse(Player player, Item cubic)
	{
		final TimeStamp previous = player.getItemReuseTimeStamps().get(cubic.getObjectId());
		try
		{
			player.addTimeStampItem(cubic, 86_400_000L, System.currentTimeMillis() + 82_740_000L);
			final long remaining = player.getItemRemainingReuseTime(cubic.getObjectId());
			PhantomAssertions.assertTrue((remaining > 82_730_000L) && (remaining <= 82_740_000L), "Disabled or inadmissible Cubic reuse was shortened from the native daily timestamp.");
			PhantomAssertions.assertEquals(86_400_000, PersonalItemReuseService.getInstance().getReuseDelay(player, cubic), "Disabled Cubic packets displayed a shortened duration.");
		}
		finally
		{
			restore(player, cubic, previous);
		}
	}

	private static void restore(Player player, Item item, TimeStamp previous)
	{
		if (previous == null)
		{
			player.getItemReuseTimeStamps().remove(item.getObjectId());
		}
		else
		{
			player.getItemReuseTimeStamps().put(item.getObjectId(), previous);
		}
	}
}
