/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.commons.threads.ThreadPool;
import org.l2jmobius.gameserver.data.xml.InitialEquipmentData;
import org.l2jmobius.gameserver.data.xml.InitialShortcutData;
import org.l2jmobius.gameserver.data.xml.PlayerTemplateData;
import org.l2jmobius.gameserver.managers.PremiumManager;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.appearance.PlayerAppearance;
import org.l2jmobius.gameserver.model.events.EventDispatcher;
import org.l2jmobius.gameserver.model.events.holders.actor.player.OnPlayerLogin;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.qol.Economy070Pricing;
import org.l2jmobius.gameserver.qol.Economy070Shop;

/** Real native Player currency + SQL stock, race, receipt and next-process reload. Funds are explicit TEST preconditions. */
public final class PhantomEconomy070Native
{
	public static void main(String[] args) throws Exception
	{
		if (args.length != 1 || !java.util.Set.of("prepare", "verify").contains(args[0]) || !PhantomTask070Lane.enabled()) { throw new IllegalArgumentException("Exact own070 action required."); }
		final Path module = Path.of(System.getProperty("phantom.module.root")).toRealPath();
		final Path task = module.getParent().resolve(".phantom-local/task070");
		final Path receipt = task.resolve("economy-native.properties");
		final var players = new ArrayList<Player>();
		final var report = new StringBuilder("check\tevidence\n");
		try
		{
			PhantomHeadlessPlayerTestEnvironment.initializeNativeInfrastructure(new PhantomTestContext(70007001L, module, task.resolve("reports")));
			InitialEquipmentData.getInstance(); InitialShortcutData.getInstance();
			final var pricing = Economy070Pricing.getInstance(); final var shop = Economy070Shop.getInstance();
			final var values = new Properties();
			if (args[0].equals("verify"))
			{
				try (var reader = Files.newBufferedReader(receipt)) { values.load(reader); }
				final var first = Player.load(Integer.parseInt(values.getProperty("first"))); players.add(first); first.setOnlineStatus(true, false); first.spawnMe();
				PremiumManager.getInstance(); EventDispatcher.getInstance().notifyEvent(new OnPlayerLogin(first), first);
				check(first.getAdena() == Long.parseLong(values.getProperty("adena")), "SameDB native Adena changed.");
				check(shop.balance(first) == Long.parseLong(values.getProperty("balance")), "SameDB savings changed.");
				check(PremiumManager.getInstance().getPremiumExpiration(first.getAccountName()) == Long.parseLong(values.getProperty("expiry")) && first.hasPremiumStatus(), "Account term did not reload.");
				final Player winner = first.getObjectId() == Integer.parseInt(values.getProperty("winner")) ? first : Player.load(Integer.parseInt(values.getProperty("winner")));
				if (winner != first) { players.add(winner); winner.setOnlineStatus(true, false); winner.spawnMe(); }
				check(shop.stock(6660).count() == 0 && shop.stock(6660).nextRestockUtc() == Long.parseLong(values.getProperty("due")), "Restart replenished stock.");
				check(shop.buy(winner, 6660, values.getProperty("request"), pricing.revision()) == Integer.parseInt(values.getProperty("itemObject")), "Receipt replay changed item.");
				check(winner.getInventory().getInventoryItemCount(6660, -1) == 1, "Restart/replay duplicated epic.");
				final Player other;
				try (var connection = DatabaseFactory.getConnection(); var query = connection.prepareStatement("SELECT charId FROM characters WHERE account_name=? AND char_name='Econ070Alt'"))
				{
					query.setString(1, first.getAccountName());
					try (var rows = query.executeQuery()) { check(rows.next(), "Retained TEST sibling row missing."); other = Player.load(rows.getInt(1)); }
				}
				check(other != null, "Retained TEST sibling missing."); players.add(other); other.setOnlineStatus(true, false); other.spawnMe();
				oppositeStackTransfers(first, other);
				report.append("restart\tnative Adena/savings/epic object/expiry retained; replay no debit/new item;stock0\n");
				report.append("count-lock\ttwo opposite native existing-stack transfers finish under held source monitors;counts conserved\n");
			}
			else
			{
				if (Files.exists(receipt)) { throw new IllegalStateException("Existing fixture must be verified, never reseeded."); }
				for (String account : new String[] {"task070econone", "task070econtwo", "task070econpoor"})
				{
					try (var connection = DatabaseFactory.getConnection(); var insert = connection.prepareStatement("INSERT INTO accounts(login,password,accessLevel,lastServer) VALUES(?,'DISABLED_NATIVE_TEST_ONLY',-1,1)"))
					{ insert.setString(1, account); insert.executeUpdate(); }
				}
				final Player first = actor("task070econone", "Econ070One"), second = actor("task070econtwo", "Econ070Two"), sibling = actor("task070econone", "Econ070Alt"), poor = actor("task070econpoor", "Econ070Poor");
				players.addAll(java.util.List.of(first, second, sibling, poor));
				for (var player : java.util.List.of(first, second)) { player.addAdena(ItemProcessType.REWARD, 100_000_000_000L, player, false); player.getInventory().updateDatabase(); }
				report.append("preconditions\tordinary access0 accounts with disabled network login;100b Adena/actor explicit TEST funding,not earned,not shipped\n");
				final String duplicate = UUID.randomUUID().toString();
				check(shop.deposit(first, 10_000_000_000L, duplicate, pricing.revision()) == 10_000_000_000L, "Deposit failed.");
				check(shop.deposit(first, 10_000_000_000L, duplicate, pricing.revision()) == 10_000_000_000L && first.getAdena() == 90_000_000_000L, "Deposit replay debited twice.");
				for (var player : java.util.List.of(first, second))
				{ for (int i = player == first ? 1 : 0; i < 3; i++) { shop.deposit(player, 10_000_000_000L, UUID.randomUUID().toString(), pricing.revision()); } }
				check(shop.withdraw(first, 100_000, UUID.randomUUID().toString(), pricing.revision()) == 29_999_900_000L, "Withdrawal failed.");
				shop.deposit(first, 100_000, UUID.randomUUID().toString(), pricing.revision());
				final long cash = first.getAdena(), wallet = shop.balance(first);
				reject(() -> shop.deposit(first, -1, UUID.randomUUID().toString(), pricing.revision()));
				reject(() -> shop.withdraw(first, wallet + 1, UUID.randomUUID().toString(), pricing.revision()));
				reject(() -> shop.deposit(first, 1, UUID.randomUUID().toString(), "0".repeat(64)));
				reject(() -> first.getInventory().getAdenaInstance().transactCount(-100, connection -> { throw new IllegalStateException("Injected before commit"); }));
				check(first.getAdena() == cash && shop.balance(first) == wallet, "Rejected operation changed resources.");
				final String request1 = UUID.randomUUID().toString(), request2 = UUID.randomUUID().toString();
				int result1, result2;
				try (var executor = Executors.newFixedThreadPool(2))
				{
					final var start = new java.util.concurrent.CountDownLatch(1);
					final var a = executor.submit(() -> { start.await(); return tryBuy(shop, first, request1, pricing.revision()); });
					final var b = executor.submit(() -> { start.await(); return tryBuy(shop, second, request2, pricing.revision()); });
					start.countDown(); result1 = a.get(); result2 = b.get();
				}
				check((result1 > 0) != (result2 > 0), "Last unit has not exactly one winner.");
				final Player winner = result1 > 0 ? first : second; final int itemObject = Math.max(result1, result2); final String request = result1 > 0 ? request1 : request2;
				check(shop.balance(first) + shop.balance(second) == 60_000_000_000L - pricing.price("epic.6660"), "Race debit conservation failed.");
				check(shop.buy(winner, 6660, request, pricing.revision()) == itemObject && winner.getInventory().getInventoryItemCount(6660, -1) == 1, "Purchase replay duplicated native item.");
				final long beforePremium = first.getAdena(), now = System.currentTimeMillis();
				final String premiumRequest = UUID.randomUUID().toString();
				final long day = shop.premium(first, 1, premiumRequest, pricing.revision());
				check(shop.premium(first, 1, premiumRequest, pricing.revision()) == day, "Premium replay changed term.");
				final long expiry = shop.premium(first, 7, UUID.randomUUID().toString(), pricing.revision());
				check(expiry - day == 7 * 86400000L && day >= now + 86400000L && first.getAdena() == beforePremium - pricing.price("premium.day") * 8, "Premium native fee/real-time term differs.");
				check(first.hasPremiumStatus() && sibling.hasPremiumStatus() && !second.hasPremiumStatus(), "Account-wide eligibility differs.");
				reject(() -> shop.premium(poor, 1, UUID.randomUUID().toString(), pricing.revision()));
				check(!poor.hasPremiumStatus() && poor.getAdena() == 0, "Insufficient premium purchase granted benefit.");
				PhantomPremium070Checks.nativeLoot(first, report);
				PhantomEconomy070Trades.run(first, poor, report);
				final var testClock = new AtomicLong(System.currentTimeMillis()); final var timed = new Economy070Shop(pricing, testClock::get);
				final Player other = winner == first ? second : first;
				timed.buy(other, 6661, UUID.randomUUID().toString(), pricing.revision()); final long due = timed.stock(6661).nextRestockUtc();
				testClock.set(due - 1); check(timed.stock(6661).count() == 0, "Early restock.");
				testClock.set(due); check(timed.stock(6661).count() == 1 && timed.stock(6661).epoch() == 1, "Due restock not persisted.");
				testClock.set(due + 100L * 86400000L); check(timed.stock(6661).count() == 1, "Restock accumulated more than one.");
				report.append("transactions\tdeposit/withdraw1:1;replay;negative/stale/insufficient;precommit rollback;racing two SQL buyers exactly1;native epic20b;premium1+7days;account sibling;test clock maxStock1\n");
				values.setProperty("first", Integer.toString(first.getObjectId())); values.setProperty("winner", Integer.toString(winner.getObjectId())); values.setProperty("request", request); values.setProperty("itemObject", Integer.toString(itemObject));
				values.setProperty("adena", Long.toString(first.getAdena())); values.setProperty("balance", Long.toString(shop.balance(first))); values.setProperty("expiry", Long.toString(expiry)); values.setProperty("due", Long.toString(shop.stock(6660).nextRestockUtc()));
				try (var writer = Files.newBufferedWriter(receipt, java.nio.file.StandardOpenOption.CREATE_NEW)) { values.store(writer, "Explicit070 native receipts"); }
			}
			System.out.println("ECONOMY070_NATIVE_" + args[0].toUpperCase(java.util.Locale.ROOT) + "_PASS");
		}
		catch (Throwable failure) { report.append("failure\t").append(failure).append('\n'); throw failure; }
		finally
		{
			for (var player : players) { player.getInventory().updateDatabase(); player.storeMe(); player.stopAllTasks(); player.deleteMe(); }
			Files.writeString(task.resolve("reports/economy-native-" + args[0] + '-' + System.currentTimeMillis() + ".tsv"), report);
			ThreadPool.shutdown(); DatabaseFactory.close();
		}
	}
	private static void oppositeStackTransfers(Player first, Player second) throws Exception
	{
		final var a = first.getInventory().addItem(ItemProcessType.REWARD, 728, 2, first, null);
		final var b = second.getInventory().addItem(ItemProcessType.REWARD, 728, 2, second, null);
		final long beforeA = a.getCount(), beforeB = b.getCount();
		final var ready = new java.util.concurrent.CountDownLatch(2);
		final var failure = new java.util.concurrent.atomic.AtomicReference<Throwable>();
		final Thread one = new Thread(() ->
		{
			synchronized (a)
			{
				try { ready.countDown(); ready.await(); check(first.getInventory().transferItem(ItemProcessType.TRANSFER, a.getObjectId(), 1, second.getInventory(), first, null) == b, "Forward native transfer failed."); }
				catch (Throwable error) { failure.compareAndSet(null, error); }
			}
		}, "070-native-transfer-a");
		final Thread two = new Thread(() ->
		{
			synchronized (b)
			{
				try { ready.countDown(); ready.await(); check(second.getInventory().transferItem(ItemProcessType.TRANSFER, b.getObjectId(), 1, first.getInventory(), second, null) == a, "Reverse native transfer failed."); }
				catch (Throwable error) { failure.compareAndSet(null, error); }
			}
		}, "070-native-transfer-b");
		one.setDaemon(true); two.setDaemon(true); one.start(); two.start(); one.join(10000); two.join(10000);
		check(!one.isAlive() && !two.isAlive(), "Opposite stack transfers deadlocked.");
		if (failure.get() != null) { throw new IllegalStateException("Native transfer failed.", failure.get()); }
		check(a.getCount() == beforeA && b.getCount() == beforeB, "Native transfer count conservation failed.");
		first.getInventory().destroyItem(ItemProcessType.DESTROY, a, 2, first, null);
		second.getInventory().destroyItem(ItemProcessType.DESTROY, b, 2, second, null);
	}
	private static Player actor(String account, String name)
	{
		final var player = Player.create(PlayerTemplateData.getInstance().getTemplate(0), account, name, new PlayerAppearance((byte) 0, (byte) 0, (byte) 0, false));
		check(player != null && !player.isGM() && !player.hasHeadlessOutboundSession(), "Ordinary player fixture failed.");
		player.setOnlineStatus(true, false); player.setCurrentHp(player.getMaxHp()); player.spawnMe(82698, 148638, -3473); return player;
	}
	private static int tryBuy(Economy070Shop shop, Player player, String request, String revision) throws Exception
	{ try { return shop.buy(player, 6660, request, revision); } catch (IllegalArgumentException soldOut) { return 0; } }
	@FunctionalInterface private interface Operation { Object run() throws Exception; }
	private static void reject(Operation operation) throws Exception { try { operation.run(); } catch (IllegalArgumentException | IllegalStateException expected) { return; } throw new AssertionError("Negative operation was accepted."); }
	private static void check(boolean value, String message) { if (!value) { throw new AssertionError(message); } }
}
