/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.file.Files;
import java.nio.file.Path;
import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.commons.threads.ThreadPool;
import org.l2jmobius.gameserver.data.xml.InitialEquipmentData;
import org.l2jmobius.gameserver.data.xml.InitialShortcutData;
import org.l2jmobius.gameserver.data.xml.MapRegionData;
import org.l2jmobius.gameserver.data.xml.PlayerTemplateData;
import org.l2jmobius.gameserver.handler.CommunityBoardHandler;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.appearance.PlayerAppearance;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.scripting.ScriptEngine;

/** Actual registered CB actions with an ordinary Player; no GameClient or packet/auth fixture. */
public final class PhantomPlayable069Native
{
	public static void main(String[] args) throws Exception
	{
		if (args.length != 1 || !args[0].equals("commerce") || !PhantomTask069Lane.enabled()) { throw new IllegalArgumentException("Explicit own069 commerce required."); }
		final Path module = Path.of(System.getProperty("phantom.module.root")).toRealPath();
		final Path report = module.getParent().resolve(".phantom-local/task069/reports/commerce-" + System.currentTimeMillis() + ".tsv");
		Player player = null;
		final StringBuilder evidence = new StringBuilder("stage\tvalue\n");
		try
		{
			PhantomHeadlessPlayerTestEnvironment.initializeNativeInfrastructure(new PhantomTestContext(69006901L, module, report.getParent()));
			InitialEquipmentData.getInstance(); InitialShortcutData.getInstance(); MapRegionData.getInstance();
			ScriptEngine.getInstance().executeScript(ScriptEngine.MASTER_HANDLER_FILE);
			player = buyer();
			check(player.getClient() == null && !player.hasHeadlessOutboundSession() && player.getAccessLevel().getLevel() == 0 && !player.isGM(), "Ordinary native Player required.");
			evidence.append("fixture\taccess0; disabled TEST-only account; no GameClient; initial1000A+3eachSeal declared FIXTURE_PRECONDITION\n");
			final long adena = player.getAdena(), mana = count(player, 728);
			CommunityBoardHandler.getInstance().handleParseCommand("_bbsconsumables;mana;1", player);
			check(player.getAdena() == adena - 1000 && count(player, 728) == mana + 1, "Native visible CB purchase did not debit exactly1000A and grant1mana.");
			evidence.append("mana\tadena=").append(adena).append("->").append(player.getAdena()).append(";item728=").append(mana).append("->").append(count(player, 728)).append('\n');
			CommunityBoardHandler.getInstance().handleParseCommand("_bbsconsumables;mana;1", player);
			check(player.getAdena() == 0 && count(player, 728) == mana + 1, "Insufficient funds credited an item.");
			final String[] colors = {"blue", "green", "red"};
			for (int index = 0; index < 3; index++)
			{
				final long stones = count(player, 6360 + index), ancient = count(player, 5575);
				CommunityBoardHandler.getInstance().handleParseCommand("_bbsconsumables;" + colors[index] + ";3", player);
				check(count(player, 6360 + index) == stones - 3 && count(player, 5575) == ancient + 3 * new int[] {3, 5, 10}[index], "Native stone exchange ratio/debit differs.");
				final long credited = count(player, 5575);
				CommunityBoardHandler.getInstance().handleParseCommand("_bbsconsumables;" + colors[index] + ";3", player);
				check(count(player, 5575) == credited, "Empty repeated exchange credited Ancient Adena.");
				evidence.append(colors[index]).append("\tstones=").append(stones).append("->").append(count(player, 6360 + index)).append(";ancient=").append(ancient).append("->").append(credited).append('\n');
			}
			for (String amount : new String[] {"0", "-1", "9223372036854775807", "bad"})
			{
				CommunityBoardHandler.getInstance().handleParseCommand("_bbsconsumables;red;" + amount, player);
			}
			check(count(player, 5575) == 54 && player.getAdena() == 0, "Invalid amount mutated inventory.");
			evidence.append("negative\tinsufficient/repeat/zero/negative/overflow/malformed PASS\n");
			System.out.println("OWN069_COMMERCE_PASS " + report);
		}
		finally
		{
			Files.writeString(report, evidence);
			if (player != null) { player.stopAllTasks(); player.storeMe(); player.deleteMe(); }
			ThreadPool.shutdown(); DatabaseFactory.close();
		}
	}

	private static Player buyer() throws Exception
	{
		Integer existing = null;
		try (var connection = DatabaseFactory.getConnection())
		{
			try (var query = connection.prepareStatement("SELECT c.charId,c.accesslevel,a.accessLevel FROM characters c JOIN accounts a ON a.login=c.account_name WHERE c.account_name='task069buyer' AND c.char_name='Buyer069'"); var rows = query.executeQuery()) { if (rows.next()) { existing = rows.getInt(1); check(rows.getInt(2) == 0 && rows.getInt(3) == -1 && !rows.next(), "Existing buyer authority differs."); } }
			if (existing == null) { try (var insert = connection.prepareStatement("INSERT INTO accounts(login,password,accessLevel,lastServer) VALUES('task069buyer','DISABLED_NATIVE_TEST_ONLY',-1,1)")) { insert.executeUpdate(); } }
		}
		final Player player = existing == null ? Player.create(PlayerTemplateData.getInstance().getTemplate(0), "task069buyer", "Buyer069", new PlayerAppearance((byte) 0, (byte) 0, (byte) 0, false)) : Player.load(existing);
		check(player != null, "Native buyer creation failed.");
		if (existing == null)
		{
			player.addAdena(ItemProcessType.REWARD, 1000, player, false);
			for (int id = 6360; id <= 6362; id++) { player.addItem(ItemProcessType.REWARD, id, 3, player, false); }
		}
		check(player.getAdena() == 1000 && count(player, 6360) == 3 && count(player, 6361) == 3 && count(player, 6362) == 3 && count(player, 5575) == 0, "Fixture already consumed; no automatic refund/reseed.");
		player.setCurrentHp(player.getMaxHp()); player.setCurrentMp(player.getMaxMp());
		player.setOnlineStatus(true, false); player.spawnMe(81700, 148500, -3470);
		return player;
	}

	private static long count(Player player, int id) { return player.getInventory().getInventoryItemCount(id, -1); }
	private static void check(boolean value, String message) { if (!value) { throw new AssertionError(message); } }
}
