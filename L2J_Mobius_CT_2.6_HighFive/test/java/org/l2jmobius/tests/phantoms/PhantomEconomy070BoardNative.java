/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.UUID;
import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.commons.threads.ThreadPool;
import org.l2jmobius.gameserver.config.custom.CommunityBoardConfig;
import org.l2jmobius.gameserver.data.xml.InitialEquipmentData;
import org.l2jmobius.gameserver.data.xml.InitialShortcutData;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.qol.Economy070Board;
import org.l2jmobius.gameserver.qol.Economy070Shop;

/** Actual native HTML generation and denial semantics; no client, purchase or new fixture. */
public final class PhantomEconomy070BoardNative
{
	public static void main(String[] args) throws Exception
	{
		if (args.length != 0 || !PhantomTask070Lane.enabled()) { throw new IllegalArgumentException("Own070 only."); }
		final Path module = Path.of(System.getProperty("phantom.module.root")).toRealPath();
		final Path task = module.getParent().resolve(".phantom-local/task070");
		final Path output = task.resolve("reports/native-board070");
		if (Files.exists(output)) { throw new IllegalArgumentException("Preserve evidence."); }
		Files.createDirectory(output);
		Player player = null;
		try
		{
			PhantomHeadlessPlayerTestEnvironment.initializeNativeInfrastructure(new PhantomTestContext(70007002L, module, output));
			InitialEquipmentData.getInstance(); InitialShortcutData.getInstance();
			final var receipt = new Properties();
			try (var reader = Files.newBufferedReader(task.resolve("economy-native.properties"))) { receipt.load(reader); }
			player = Player.load(Integer.parseInt(receipt.getProperty("first")));
			check(player != null && !player.isGM() && !player.hasHeadlessOutboundSession(), "Retained ordinary actor required.");
			final var shop = Economy070Shop.getInstance();
			final long cash = player.getAdena(), savings = shop.balance(player), due = shop.stock(6660).nextRestockUtc();
			for (String page : new String[] {"home", "consumables", "quest", "scrolls", "epic", "codex", "mammon", "epaulette", "premium"})
			{
				final String html = Economy070Board.render("_bbseconomy;" + page, player);
				check(html.endsWith("</html>") && html.contains("_bbsqol") && html.contains("<table width=750>"), "Native page failed: " + page);
				Files.writeString(output.resolve(page + ".html"), html);
			}
			final String stale = Economy070Board.render("_bbseconomy;buy;6660;" + UUID.randomUUID() + ";" + "0".repeat(64), player);
			check(!stale.contains("<table width=750>"), "Stale quote was accepted.");
			check(cash == player.getAdena() && savings == shop.balance(player) && due == shop.stock(6660).nextRestockUtc() && shop.stock(6660).count() == 0, "Denied UI changed resources/stock.");
			CommunityBoardConfig.CUSTOM_CB_ENABLED = false;
			check(!Economy070Board.render("_bbseconomy;home", player).contains("<table width=750>"), "OFF route remained available.");
			System.out.println("ECONOMY070_BOARD_NATIVE_PASS; pages=9; stale/OFF denied; funds/stock unchanged; CLIENT_VISUAL_NOT_RUN");
		}
		finally
		{
			if (player != null) { player.stopAllTasks(); player.deleteMe(); }
			ThreadPool.shutdown(); DatabaseFactory.close();
		}
	}
	private static void check(boolean value, String message) { if (!value) { throw new AssertionError(message); } }
}
