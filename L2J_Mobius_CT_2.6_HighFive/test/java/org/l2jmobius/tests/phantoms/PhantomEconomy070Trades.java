/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import org.l2jmobius.gameserver.data.xml.MultisellData;
import org.l2jmobius.gameserver.data.xml.NpcData;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.instance.Folk;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.network.clientpackets.MultiSellChoose;

/** Exercises the real prepared multisell writer without constructing a GameClient. */
public final class PhantomEconomy070Trades
{
	private PhantomEconomy070Trades() { }
	public static void run(Player player, Player poor, StringBuilder report)
	{
		run(player, poor, report, true);
	}
	public static void run(Player player, Player poor, StringBuilder report, boolean fund)
	{
		if (!PhantomTask070Lane.enabled()) { throw new IllegalArgumentException("Own070 native fixture required."); }
		final var lists = MultisellData.getInstance();
		if (fund)
		{
			player.addItem(ItemProcessType.REWARD, 5575, 500_000_000L, player, false);
			player.addItem(ItemProcessType.REWARD, 9912, 2540, player, false);
		}
		for (int entry = 1; entry <= 4; entry++)
		{
			final int id = new int[] {6622, 9625, 9626, 9627}[entry - 1]; final long cost = new long[] {20_000_000, 40_000_000, 50_000_000, 100_000_000}[entry - 1];
			final long before = count(player, 5575), product = count(player, id);
			lists.separateAndSend(91005, player, null, false); MultiSellChoose.executePreparedExchange(player, 91005, player.getMultiSell().getEntries().get(entry - 1).getEntryId(), 1);
			check(count(player, 5575) == before - cost && count(player, id) == product + 1, "Native Codex debit/product differs: " + id);
		}
		for (int entry = 1; entry <= 3; entry++)
		{
			final int id = 9624 + entry; final long cost = new long[] {200, 540, 1800}[entry - 1], fee = new long[] {1_000_000, 1_260_000, 1_800_000}[entry - 1];
			final long before = count(player, 9912), adena = player.getAdena(), product = count(player, id);
			lists.separateAndSend(91006, player, null, false); MultiSellChoose.executePreparedExchange(player, 91006, player.getMultiSell().getEntries().get(entry - 1).getEntryId(), 1);
			check(count(player, 9912) == before - cost && player.getAdena() == adena - fee && count(player, id) == product + 1, "Native dual-cost epaulette trade differs.");
		}
		final var pushkin = new Folk(NpcData.getInstance().getTemplate(30300));
		try
		{
			pushkin.spawnMe(77458, 148169, -3595); player.teleToLocation(77460, 148170, -3595); player.setLastFolkNPC(pushkin);
			for (int entry = 1; entry <= 3; entry++)
			{
				final int stone = 6359 + entry; player.addItem(ItemProcessType.REWARD, stone, 3, player, false);
				final long ancient = count(player, 5575);
				lists.separateAndSend(91004, player, pushkin, false); MultiSellChoose.executePreparedExchange(player, 91004, player.getMultiSell().getEntries().get(entry - 1).getEntryId(), 3);
				check(count(player, stone) == 0 && count(player, 5575) == ancient + new int[] {9, 15, 30}[entry - 1], "Giran native stone ratio/debit differs.");
			}
			player.teleToLocation(82698, 148638, -3473); lists.separateAndSend(91004, player, pushkin, false);
			check(player.getMultiSell() == null, "Distant blacksmith quote accepted.");
		}
		finally { player.setLastFolkNPC(null); pushkin.deleteMe(); }
		lists.separateAndSend(91005, poor, null, false); MultiSellChoose.executePreparedExchange(poor, 91005, poor.getMultiSell().getEntries().getFirst().getEntryId(), 1);
		check(count(poor, 6622) == 0 && count(poor, 5575) == 0, "Insufficient native trade credited item.");
		lists.separateAndSend(91005, poor, null, false); MultiSellChoose.executePreparedExchange(poor, 91005, 1, Long.MAX_VALUE);
		check(poor.getMultiSell() == null && count(poor, 6622) == 0, "Invalid amount credited item.");
		lists.separateAndSend(600010, player, null, false); check(player.getMultiSell() == null, "Forged ordinary gear route reopened.");
		report.append("nativeMultisell\t4Codex AA actual debit;3fortress dual9912+Adena;GiranPushkin3colors/3-5-10;range/insufficient/overflow/oldgear negative;noGameClient\n");
	}
	private static long count(Player player, int id) { return player.getInventory().getInventoryItemCount(id, -1); }
	private static void check(boolean value, String message) { if (!value) { throw new AssertionError(message); } }
}
