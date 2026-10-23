/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.commons.threads.ThreadPool;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.managers.PremiumManager;
import org.l2jmobius.gameserver.model.events.EventDispatcher;
import org.l2jmobius.gameserver.model.events.holders.actor.player.OnPlayerLogin;
import org.l2jmobius.gameserver.qol.Economy070Pricing;
import org.l2jmobius.gameserver.qol.Economy070Shop;

/** Continues the retained fixture after correcting prepared entry IDs; no account or money reseed. */
public final class PhantomEconomy070Resume
{
	public static void main(String[] args) throws Exception
	{
		final Path module = Path.of(System.getProperty("phantom.module.root")).toRealPath();
		final Path task = module.getParent().resolve(".phantom-local/task070");
		final var players = new ArrayList<Player>(); final var report = new StringBuilder("check\tevidence\n");
		try
		{
			PhantomHeadlessPlayerTestEnvironment.initializeNativeInfrastructure(new PhantomTestContext(70007002L, module, task.resolve("reports")));
			final Player first = load("Econ070One"), second = load("Econ070Two"), poor = load("Econ070Poor"); players.add(first); players.add(second); players.add(poor);
			final var shop = Economy070Shop.getInstance(); final var pricing = Economy070Pricing.getInstance();
			PremiumManager.getInstance(); EventDispatcher.getInstance().notifyEvent(new OnPlayerLogin(first), first);
			final var receipt = new Properties();
			try (var connection = DatabaseFactory.getConnection(); var query = connection.prepareStatement("SELECT request_id,char_id,item_object_id FROM economy070_orders WHERE kind='EPIC' AND sku=6660"); var rows = query.executeQuery())
			{
				check(rows.next(), "Retained race receipt missing."); receipt.setProperty("request", rows.getString(1)); receipt.setProperty("winner", rows.getString(2)); receipt.setProperty("itemObject", rows.getString(3)); check(!rows.next(), "Race has duplicate receipts.");
			}
			check(shop.balance(first) + shop.balance(second) == 40_000_000_000L && shop.stock(6660).count() == 0, "Retained wallet/stock differs.");
			check(first.getAdena() == 69_992_000_000L && second.getAdena() == 70_000_000_000L, "Retained native payment/term conservation differs.");
			check(first.hasPremiumStatus(), "Retained account did not regain premium on native login event.");
			PhantomEconomy070Trades.run(first, poor, report, false);
			final var clock = new AtomicLong(System.currentTimeMillis()); final var timed = new Economy070Shop(pricing, clock::get);
			final Player loser = first.getObjectId() == Integer.parseInt(receipt.getProperty("winner")) ? second : first;
			timed.buy(loser, 6661, UUID.randomUUID().toString(), pricing.revision()); final long due = timed.stock(6661).nextRestockUtc();
			clock.set(due - 1); check(timed.stock(6661).count() == 0, "Early replenishment.");
			clock.set(due); check(timed.stock(6661).count() == 1 && timed.stock(6661).epoch() == 1, "Due replenishment not persisted.");
			clock.set(due + 100L * 86400000L); check(timed.stock(6661).count() == 1, "Stock accumulated beyond1.");
			receipt.setProperty("first", Integer.toString(first.getObjectId())); receipt.setProperty("adena", Long.toString(first.getAdena())); receipt.setProperty("balance", Long.toString(shop.balance(first))); receipt.setProperty("expiry", Long.toString(PremiumManager.getInstance().getPremiumExpiration(first.getAccountName()))); receipt.setProperty("due", Long.toString(shop.stock(6660).nextRestockUtc()));
			try (var writer = Files.newBufferedWriter(task.resolve("economy-native.properties"), java.nio.file.StandardOpenOption.CREATE_NEW)) { receipt.store(writer, "Retained070 exact receipts,no reseed"); }
			report.append("resume\told native counts+durable race/payment receipts verified;no refill;test-only clock stock1;next-process verification ready\n");
			System.out.println("ECONOMY070_NATIVE_RETAINED_CONTINUATION_PASS");
		}
		catch (Throwable failure) { report.append("failure\t").append(failure).append('\n'); throw failure; }
		finally { for (var player : players) { player.getInventory().updateDatabase(); player.storeMe(); player.stopAllTasks(); player.deleteMe(); } Files.writeString(task.resolve("reports/economy-native-resume-" + System.currentTimeMillis() + ".tsv"), report); ThreadPool.shutdown(); DatabaseFactory.close(); }
	}
	private static Player load(String name) throws Exception
	{
		try (var connection = DatabaseFactory.getConnection(); var query = connection.prepareStatement("SELECT charId FROM characters WHERE char_name=? AND account_name LIKE 'task070econ%' AND accesslevel=0"))
		{
			query.setString(1, name); try (var rows = query.executeQuery()) { check(rows.next(), "Retained actor missing."); final var player = Player.load(rows.getInt(1)); check(!rows.next(), "Ambiguous actor."); player.setOnlineStatus(true, false); player.spawnMe(); return player; }
		}
	}
	private static void check(boolean value, String message) { if (!value) { throw new AssertionError(message); } }
}
