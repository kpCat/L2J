/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.UUID;
import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.commons.threads.ThreadPool;
import org.l2jmobius.gameserver.managers.PremiumManager;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.events.EventDispatcher;
import org.l2jmobius.gameserver.model.events.holders.actor.player.OnPlayerLogin;
import org.l2jmobius.gameserver.qol.Economy070Pricing;
import org.l2jmobius.gameserver.qol.Economy070Shop;

/** A deliberate failure after SQL commit proves cache recovery never reissues money or item rows. */
public final class PhantomEconomy070RecoveryNative
{
	public static void main(String[] args) throws Exception
	{
		if (args.length > 1 || args.length == 1 && !java.util.Set.of("inspect", "next").contains(args[0]) || !PhantomTask070Lane.enabled()) { throw new IllegalArgumentException("Own070 recovery probe only."); }
		final Path module = Path.of(System.getProperty("phantom.module.root")).toRealPath(), task = module.getParent().resolve(".phantom-local/task070");
		final var report = new StringBuilder("check\tevidence\n"); Player player = null;
		try
		{
			PhantomHeadlessPlayerTestEnvironment.initializeNativeInfrastructure(new PhantomTestContext(70007004L, module, task.resolve("reports")));
			final var receipt = new Properties(); try (var reader = Files.newBufferedReader(task.resolve("economy-native.properties"))) { receipt.load(reader); }
			player = Player.load(Integer.parseInt(receipt.getProperty("first"))); player.setOnlineStatus(true, false); player.spawnMe();
			PremiumManager.getInstance(); EventDispatcher.getInstance().notifyEvent(new OnPlayerLogin(player), player);
			final var shop = Economy070Shop.getInstance(); final var pricing = Economy070Pricing.getInstance();
			if (args.length == 1 && args[0].equals("inspect"))
			{
				try (var connection = DatabaseFactory.getConnection(); var query = connection.prepareStatement("SELECT request_id,item_object_id,result_value,amount FROM economy070_orders WHERE char_id=? AND kind='EPIC' AND sku=6662"))
				{
					query.setInt(1, player.getObjectId()); try (var rows = query.executeQuery())
					{
						while (rows.next())
						{
							final String request = rows.getString(1); final long before = shop.balance(player); final int objectId = rows.getInt(2);
							report.append("inspectBefore\trequest=").append(request).append(";object=").append(objectId).append(";result=").append(rows.getLong(3)).append(";balance=").append(before).append(";count=").append(player.getInventory().getInventoryItemCount(6662, -1)).append('\n');
							final int replay = shop.buy(player, 6662, request, pricing.revision());
							report.append("inspectAfter\tresult=").append(replay).append(";balance=").append(shop.balance(player)).append(";count=").append(player.getInventory().getInventoryItemCount(6662, -1)).append('\n');
							check(replay == objectId && before == shop.balance(player) && player.getInventory().getInventoryItemCount(6662, -1) == 1, "Retained replay differs; see scalar evidence.");
						}
					}
				}
				System.out.println("ECONOMY070_RECOVERY_INSPECT_PASS"); return;
			}
			final int sku = args.length == 1 ? 6661 : 6662;
			final long beforeItems = player.getInventory().getInventoryItemCount(sku, -1);
			check(shop.stock(sku).count() == 1, "Fault fixture already consumed; refuse reseed.");
			shop.deposit(player, 20_000_000_000L, UUID.randomUUID().toString(), pricing.revision());
			final long balance = shop.balance(player); final String request = UUID.randomUUID().toString();
			final var failing = new Economy070Shop(pricing, System::currentTimeMillis, () -> { throw new IllegalStateException("Injected after durable commit before native publication"); });
			try { failing.buy(player, sku, request, pricing.revision()); throw new AssertionError("Fault was not reached."); }
			catch (IllegalStateException expected) { check(expected.getMessage().startsWith("Injected"), "Unexpected fault."); }
			check(shop.stock(sku).count() == 0 && shop.balance(player) == balance - pricing.price("epic." + sku) && player.getInventory().getInventoryItemCount(sku, -1) == beforeItems, "Postcommit fault boundary differs.");
			final int objectId = shop.buy(player, sku, request, pricing.revision());
			check(objectId > 0 && player.getInventory().getInventoryItemCount(sku, -1) == beforeItems + 1, "Committed native row was not hydrated on replay.");
			check(player.getInventory().getItemByObjectId(objectId) != null && org.l2jmobius.gameserver.model.World.getInstance().getPlayer(player.getObjectId()) == player, "Restored object ID violated the native row contract.");
			final int secondReplay = shop.buy(player, sku, request, pricing.revision());
			check(secondReplay == objectId && player.getInventory().getInventoryItemCount(sku, -1) == beforeItems + 1 && shop.balance(player) == balance - pricing.price("epic." + sku), "Delivery replay differs: object=" + objectId + ";replay=" + secondReplay + ";count=" + player.getInventory().getInventoryItemCount(sku, -1) + ";balance=" + shop.balance(player) + ";expected=" + (balance - pricing.price("epic." + sku)));
			String premiumRequest;
			try (var connection = DatabaseFactory.getConnection(); var query = connection.prepareStatement("SELECT request_id FROM economy070_orders WHERE account_name=? AND kind='PREMIUM' AND amount=1"))
			{ query.setString(1, player.getAccountName()); try (var rows = query.executeQuery()) { check(rows.next(), "Premium receipt missing."); premiumRequest = rows.getString(1); } }
			final long adena = player.getAdena(), expiry = PremiumManager.getInstance().getPremiumExpiration(player.getAccountName());
			player.setPremiumStatus(false); shop.premium(player, 1, premiumRequest, pricing.revision());
			check(player.hasPremiumStatus() && player.getAdena() == adena && PremiumManager.getInstance().getPremiumExpiration(player.getAccountName()) == expiry, "Premium receipt did not republish actual account term.");
			receipt.setProperty("adena", Long.toString(player.getAdena())); receipt.setProperty("balance", Long.toString(shop.balance(player))); receipt.setProperty("recoveredItem", Integer.toString(objectId));
			try (var writer = Files.newBufferedWriter(task.resolve("economy-native.properties"))) { receipt.store(writer, "Same070 retained checkpoint after explicit recovery probe"); }
			report.append("recovery\tpostcommit/precache fault:SQLmoney/stock/item/receipt committed once;replay hydrates exactobject=").append(objectId).append(";replaynoextra;premiumcacheloss+replayrestoresactual8daytermnocharge;noOSclockchange\n");
			System.out.println("ECONOMY070_RECOVERY_NATIVE_PASS");
		}
		catch (Throwable failure) { report.append("failure\t").append(failure).append('\n'); throw failure; }
		finally { if (player != null) { player.getInventory().updateDatabase(); player.storeMe(); player.stopAllTasks(); player.deleteMe(); } Files.writeString(task.resolve("reports/recovery-native-" + System.currentTimeMillis() + ".tsv"), report); ThreadPool.shutdown(); DatabaseFactory.close(); }
	}
	private static void check(boolean value, String message) { if (!value) { throw new AssertionError(message); } }
}
