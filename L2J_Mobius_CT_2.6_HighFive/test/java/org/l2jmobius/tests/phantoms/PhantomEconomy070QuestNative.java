/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.file.Files;
import java.nio.file.Path;
import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.commons.threads.ThreadPool;
import org.l2jmobius.gameserver.data.sql.ClanTable;
import org.l2jmobius.gameserver.data.xml.InitialEquipmentData;
import org.l2jmobius.gameserver.data.xml.InitialShortcutData;
import org.l2jmobius.gameserver.data.xml.MultisellData;
import org.l2jmobius.gameserver.data.xml.NpcData;
import org.l2jmobius.gameserver.data.xml.PlayerTemplateData;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.appearance.PlayerAppearance;
import org.l2jmobius.gameserver.model.actor.instance.Folk;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.network.clientpackets.MultiSellChoose;
import org.l2jmobius.gameserver.scripting.ScriptEngine;

/** Native subclass quest consumption and clan5 upgrade, plus a real Mammon prepared exchange. */
public final class PhantomEconomy070QuestNative
{
	public static void main(String[] args) throws Exception
	{
		if (args.length != 1 || !java.util.Set.of("prepare", "verify", "resume").contains(args[0]) || !PhantomTask070Lane.enabled()) { throw new IllegalArgumentException("Own070 action required."); }
		final Path module = Path.of(System.getProperty("phantom.module.root")).toRealPath(); final Path task = module.getParent().resolve(".phantom-local/task070");
		final var report = new StringBuilder("check\tevidence\n"); Player player = null;
		try
		{
			PhantomHeadlessPlayerTestEnvironment.initializeNativeInfrastructure(new PhantomTestContext(70007003L, module, task.resolve("reports")));
			InitialEquipmentData.getInstance(); InitialShortcutData.getInstance();
			ScriptEngine.getInstance().executeScript(ScriptEngine.MASTER_HANDLER_FILE);
			ScriptEngine.getInstance().executeScript(module.resolve("test/resources/phantoms/economy070/NativeQuestBootstrap070.java"));
			if (args[0].equals("verify"))
			{
				try (var connection = DatabaseFactory.getConnection(); var query = connection.prepareStatement("SELECT charId FROM characters WHERE account_name='task070quests' AND char_name='Quest070' AND accesslevel=0"); var rows = query.executeQuery())
				{ check(rows.next(), "Retained quest actor missing."); player = Player.load(rows.getInt(1)); check(!rows.next(), "Ambiguous quest actor."); }
				org.l2jmobius.gameserver.model.script.Quest.playerEnter(player);
				PhantomPaidSubclass070Checks.afterRestart(player, report);
				check(player.getClan() != null && player.getClan().getLevel() == 5, "Native clan level5 did not survive restart.");
			}
			else
			{
				if (args[0].equals("resume"))
				{
					try (var connection = DatabaseFactory.getConnection(); var query = connection.prepareStatement("SELECT charId FROM characters WHERE account_name='task070quests' AND char_name='Quest070' AND accesslevel=0"); var rows = query.executeQuery())
					{ check(rows.next(), "Retained actor missing."); player = Player.load(rows.getInt(1)); check(!rows.next(), "Ambiguous actor."); player.setOnlineStatus(true, false); player.spawnMe(); }
					org.l2jmobius.gameserver.model.script.Quest.playerEnter(player);
				}
				else
				{
				try (var connection = DatabaseFactory.getConnection(); var insert = connection.prepareStatement("INSERT INTO accounts(login,password,accessLevel,lastServer) VALUES('task070quests','DISABLED_NATIVE_TEST_ONLY',-1,1)")) { insert.executeUpdate(); }
				player = Player.create(PlayerTemplateData.getInstance().getTemplate(0), "task070quests", "Quest070", new PlayerAppearance((byte) 0, (byte) 0, (byte) 0, false));
				player.getStat().setExp(player.getStat().getExpForLevel(75)); player.getStat().setLevel((byte) 75); player.setSp(5_000_000);
				player.addAdena(ItemProcessType.REWARD, 500_000_000, player, false); player.setCurrentHp(player.getMaxHp()); player.setOnlineStatus(true, false); player.spawnMe(82698, 148638, -3473);
				}
				report.append("preconditions\tordinary75actor;500mAdena+5mSP explicit TEST inputs,not earned or release grants\n");
				PhantomPaidSubclass070Checks.nativeProgress(player, report);
				final var clan = ClanTable.getInstance().createClan(player, "QuestClan070"); check(clan != null, "Native clan create failed.");
				check(clan.levelUpClan(player) && clan.levelUpClan(player), "Native clan0->2 failed.");
				check(!clan.levelUpClan(player) && clan.getLevel() == 2, "Clan3 granted without native proof.");
				final var lists = MultisellData.getInstance();
				for (int id : new int[] {1419, 3874, 3870})
				{
					lists.separateAndSend(600024, player, null, false);
					final var offer = player.getMultiSell().getEntries().stream().filter(e -> e.getProducts().size() == 1 && e.getProducts().getFirst().getItemId() == id).findFirst().orElseThrow();
					MultiSellChoose.executePreparedExchange(player, 600024, offer.getEntryId(), 1);
					check(count(player, id) == 1 && clan.levelUpClan(player) && count(player, id) == 0, "Purchased clan proof did not enter native consumption: " + id);
				}
				check(clan.getLevel() == 5 && player.getSp() == 1_030_000, "Native clan5/SP budget differs.");
				final var mammon = new Folk(NpcData.getInstance().getTemplate(31126));
				try
				{
					mammon.spawnMe(82710, 148638, -3473); player.setLastFolkNPC(mammon);
					player.addItem(ItemProcessType.REWARD, 4681, 1, player, false);
					lists.separateAndSend(311262509, player, mammon, true);
					final int entry = player.getMultiSell().getEntries().getFirst().getEntryId();
					MultiSellChoose.executePreparedExchange(player, 311262509, entry, 1);
					check(count(player, 4681) == 0 && count(player, 72) == 1, "Native Mammon remove-SA exchange failed.");
				}
				finally { player.setLastFolkNPC(null); mammon.deleteMe(); }
				report.append("clanMammon\tactual0->5clan;three paid proof items consumed;retainedSPandAdena;native31126removeSA4681->72;SevenSignsperiodunchanged;noNPCbypassfromCB\n");
			}
			System.out.println("ECONOMY070_QUEST_" + args[0].toUpperCase(java.util.Locale.ROOT) + "_PASS");
		}
		catch (Throwable failure) { report.append("failure\t").append(failure).append('\n'); throw failure; }
		finally { if (player != null) { player.getInventory().updateDatabase(); player.storeMe(); player.stopAllTasks(); player.deleteMe(); } Files.writeString(task.resolve("reports/quest-native-" + args[0] + '-' + System.currentTimeMillis() + ".tsv"), report); ThreadPool.shutdown(); DatabaseFactory.close(); }
	}
	private static long count(Player player, int id) { return player.getInventory().getInventoryItemCount(id, -1); }
	private static void check(boolean value, String message) { if (!value) { throw new AssertionError(message); } }
}
