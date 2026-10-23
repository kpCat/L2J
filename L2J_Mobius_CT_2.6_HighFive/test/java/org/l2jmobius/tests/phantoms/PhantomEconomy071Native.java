/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.util.UUID;
import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.config.custom.PremiumSystemConfig;
import org.l2jmobius.gameserver.data.xml.MultisellData;
import org.l2jmobius.gameserver.managers.PremiumManager;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.model.item.instance.Item;
import org.l2jmobius.gameserver.network.clientpackets.MultiSellChoose;
import org.l2jmobius.gameserver.qol.Economy070Board;
import org.l2jmobius.gameserver.qol.Economy070Pricing;
import org.l2jmobius.gameserver.qol.Economy070Shop;
import org.l2jmobius.gameserver.qol.Economy071OlympiadShop;
import org.l2jmobius.gameserver.qol.PremiumLootPolicy;

/** Called only by the root-owned explicit OWN071 native fixture. Funding is declared fixture setup. */
public final class PhantomEconomy071Native
{
	private PhantomEconomy071Native() { }
	/** SQL-only retained diagnostic: no native actors are loaded, saved, reconciled or modified. */
	public static void inspectRetained(StringBuilder report) throws Exception
	{
		if (!PhantomTask071Lane.enabled()) { throw new IllegalArgumentException("Explicit OWN071 native TEST lane required."); }
		final var snapshot = new java.util.Properties();
		final var receiptPath = java.nio.file.Path.of(System.getProperty("phantom.module.root")).getParent().resolve(".phantom-local/task071/native-acceptance071.properties");
		try (var reader = java.nio.file.Files.newBufferedReader(receiptPath)) { snapshot.load(reader); }
		try (var connection = DatabaseFactory.getConnection())
		{
			check(PhantomTask071Lane.SCHEMA.equals(connection.getCatalog()), "Own071 TEST schema required."); Economy070Shop.verifySchema(connection);
			for (String account : new String[] {"task071nativeone", "task071nativepoor"})
			{
				int owner = 0; String name = ""; final var inventory = new StringBuilder(); int rowsFound = 0;
				try (var query = connection.prepareStatement("SELECT charId,char_name FROM characters WHERE account_name=?"))
				{
					query.setString(1, account);
					try (var rows = query.executeQuery())
					{
						check(rows.next(), "Exact071 diagnostic account has no character: " + account); owner = rows.getInt(1); name = rows.getString(2);
						check(!rows.next(), "Exact071 diagnostic account has ambiguous characters.");
					}
				}
				check(owner == Integer.parseInt(snapshot.getProperty(account.equals("task071nativeone") ? "first" : "poor")), "Retained071 diagnostic owner differs.");
				try (var query = connection.prepareStatement("SELECT owner_id,object_id,item_id,count,loc,loc_data,enchant_level,mana_left,time FROM items WHERE owner_id=? ORDER BY object_id"))
				{
					query.setInt(1, owner);
					try (var rows = query.executeQuery())
					{
						while (rows.next())
						{
							rowsFound++; report.append("economy071.inspect.item\t").append(account);
							for (int column = 1; column <= 9; column++) { report.append(';').append(rows.getString(column)); } report.append('\n');
							if ("INVENTORY".equals(rows.getString(5)) || "PAPERDOLL".equals(rows.getString(5)))
							{ inventory.append(rows.getInt(2)).append(':').append(rows.getInt(3)).append(':').append(rows.getLong(4)).append(':').append(rows.getInt(7)).append('\n'); }
						}
					}
				}
				final String currentHash = diagnosticHash(inventory.toString());
				report.append("economy071.inspect.inventory\t").append(account).append(";owner=").append(owner).append(";sqlRows=").append(rowsFound).append(";sqlInventorySHA=").append(currentHash).append(";originalSHA=").append(snapshot.getProperty(name + ".inventory")).append(";canonical=").append(inventory.toString().replace("\n", "| ")).append('\n');
				try (var query = connection.prepareStatement("SELECT request_id,kind,sku,item_object_id,result_value,amount,created_utc FROM economy070_orders WHERE account_name=? AND char_id=? AND kind IN ('EPIC','DEPOSIT','PREMIUM') ORDER BY created_utc,request_id"))
				{
					query.setString(1, account); query.setInt(2, owner);
					try (var rows = query.executeQuery())
					{
						while (rows.next())
						{
							report.append("economy071.inspect.receipt\t").append(account); for (int column = 1; column <= 7; column++) { report.append(';').append(rows.getString(column)); } report.append('\n');
							if ("EPIC".equals(rows.getString(2)) && rows.getInt(3) == 6660)
							{
								final String ring = rows.getInt(4) + ":6660:1:0\n", ringHash = diagnosticHash(ring);
								report.append("economy071.inspect.ringOnly\t").append(account).append(";receiptObject=").append(rows.getInt(4)).append(";result=").append(rows.getLong(5)).append(";SHA=").append(ringHash).append(";matchesOriginal=").append(ringHash.equals(snapshot.getProperty(name + ".inventory"))).append(";matchesSqlInventory=").append(ringHash.equals(currentHash)).append('\n');
							}
						}
					}
				}
				try (var query = connection.prepareStatement("SELECT balance FROM economy070_wallet WHERE account_name=?"))
				{ query.setString(1, account); try (var rows = query.executeQuery()) { report.append("economy071.inspect.wallet\t").append(account).append(';').append(rows.next() ? Long.toString(rows.getLong(1)) : "MISSING").append(";original=").append(snapshot.getProperty(name + ".wallet")).append('\n'); } }
			}
			try (var query = connection.prepareStatement("SELECT sku,current_stock,next_restock,epoch,version,max_stock,restock_days,source_price_revision FROM economy070_stock ORDER BY sku"); var rows = query.executeQuery())
			{ while (rows.next()) { report.append("economy071.inspect.stock\t"); for (int column = 1; column <= 8; column++) { if (column > 1) { report.append(';'); } report.append(rows.getString(column)); } report.append('\n'); } }
		}
		report.append("economy071.inspect.mode\tSQL SELECT only;no Player.load/save;no receipt replay;no stock restock/reconciliation;zero-count native Adena snapshot is a hypothesis pending row/hash evidence\n");
	}
	private static String diagnosticHash(String value) throws Exception
	{
		return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
	}
	/** Strict raw equality first; only the immutable v1 poor fixture may prove its removed zero57 stack. */
	public static void verifyInventory(Player player, String expectedRawHash, StringBuilder report) throws Exception
	{
		final var nativeRows = new StringBuilder();
		for (var item : player.getInventory().getItems().stream().sorted(java.util.Comparator.comparingInt(Item::getObjectId)).toList())
		{ nativeRows.append(item.getObjectId()).append(':').append(item.getId()).append(':').append(item.getCount()).append(':').append(item.getEnchantLevel()).append('\n'); }
		final String currentHash = diagnosticHash(nativeRows.toString());
		if (currentHash.equals(expectedRawHash)) { report.append("economy071.inventory.exact\t").append(player.getName()).append(';').append(currentHash).append('\n'); return; }
		check(PhantomTask071Lane.enabled() && "task071nativepoor".equals(player.getAccountName()) && "Native071Poor".equals(player.getName()), "Native inventory changed across own071 restart: " + player.getName());
		final var snapshot = new java.util.Properties();
		final var receiptPath = java.nio.file.Path.of(System.getProperty("phantom.module.root")).getParent().resolve(".phantom-local/task071/native-acceptance071.properties");
		try (var reader = java.nio.file.Files.newBufferedReader(receiptPath)) { snapshot.load(reader); }
		check(player.getObjectId() == Integer.parseInt(snapshot.getProperty("poor")) && expectedRawHash.equals(snapshot.getProperty("Native071Poor.inventory")) && "0".equals(snapshot.getProperty("Native071Poor.wallet")), "Original v1 poor snapshot authority differs.");
		check(player.getInventory().getItems().size() == 1, "Zero57 exception requires exactly one native ring row.");
		final Item ring = player.getInventory().getItems().iterator().next();
		check(ring.getOwnerId() == player.getObjectId() && ring.getId() == 6660 && ring.getCount() == 1 && ring.getEnchantLevel() == 0 && ring.getItemLocation() == org.l2jmobius.gameserver.model.item.enums.ItemLocation.INVENTORY, "Positive native ring preimage differs.");
		final String ringRow = ring.getObjectId() + ":6660:1:0\n";
		check(ringRow.equals(nativeRows.toString()), "Native inventory is not exactly the receipt ring.");
		String depositRequest = null, epicRequest = null; long depositTime = 0, epicTime = 0;
		try (var connection = DatabaseFactory.getConnection())
		{
			check(PhantomTask071Lane.SCHEMA.equals(connection.getCatalog()), "Own071 TEST schema required."); Economy070Shop.verifySchema(connection);
			try (var query = connection.prepareStatement("SELECT object_id,item_id,count,loc,enchant_level FROM items WHERE owner_id=? ORDER BY object_id"))
			{
				query.setInt(1, player.getObjectId());
				try (var rows = query.executeQuery())
				{ check(rows.next() && rows.getInt(1) == ring.getObjectId() && rows.getInt(2) == 6660 && rows.getLong(3) == 1 && "INVENTORY".equals(rows.getString(4)) && rows.getInt(5) == 0 && !rows.next(), "SQL owner rows must contain only the exact positive ring; zero57 must be absent."); }
			}
			try (var query = connection.prepareStatement("SELECT request_id,kind,amount,sku,item_object_id,result_value,created_utc FROM economy070_orders WHERE account_name='task071nativepoor' AND char_id=? AND kind IN ('DEPOSIT','EPIC') ORDER BY created_utc,request_id"))
			{
				query.setInt(1, player.getObjectId());
				try (var rows = query.executeQuery())
				{
					while (rows.next())
					{
						if ("DEPOSIT".equals(rows.getString(2)))
						{ check(depositRequest == null && rows.getLong(3) == 200_000_000L && rows.getObject(4) == null && rows.getInt(5) == 0 && rows.getLong(6) == 200_000_000L, "V1 poor deposit receipt differs."); depositRequest = rows.getString(1); depositTime = rows.getLong(7); }
						else
						{ check(epicRequest == null && rows.getLong(3) == 200_000_000L && rows.getInt(4) == 6660 && rows.getInt(5) == ring.getObjectId() && rows.getLong(6) == ring.getObjectId(), "V1 poor epic receipt differs."); epicRequest = rows.getString(1); epicTime = rows.getLong(7); }
					}
				}
			}
			check(depositRequest != null && epicRequest != null && depositTime <= epicTime, "Exact deposit->epic receipt chain required.");
			try (var query = connection.prepareStatement("SELECT balance FROM economy070_wallet WHERE account_name='task071nativepoor'"); var rows = query.executeQuery())
			{ check(rows.next() && rows.getLong(1) == 0 && !rows.next(), "Poor wallet is not the exact zero postimage."); }
			try (var query = connection.prepareStatement("SELECT current_stock,next_restock,max_stock FROM economy070_stock WHERE sku=6660"); var rows = query.executeQuery())
			{ check(rows.next() && rows.getInt(1) == 0 && rows.getLong(2) == Long.parseLong(snapshot.getProperty("due")) && rows.getInt(3) == 1 && !rows.next(), "Retained sold-out stock/due differs."); }
		}
		int zeroObjectId = 0, matches = 0;
		// V1 did not record its zero currency object. A bounded unique preimage match proves that ID;
		// no zero rows or arbitrary changed values are filtered from the current inventory.
		final int lower = Math.max(player.getObjectId() + 1, ring.getObjectId() - 4096), upper = Math.addExact(ring.getObjectId(), 4096);
		for (int candidate = lower; candidate <= upper; candidate++)
		{
			if (candidate == ring.getObjectId()) { continue; }
			final String zero = candidate + ":57:0:0\n", original = candidate < ring.getObjectId() ? zero + ringRow : ringRow + zero;
			if (diagnosticHash(original).equals(expectedRawHash)) { zeroObjectId = candidate; matches++; }
		}
		check(matches == 1, "Cannot uniquely prove the original ring+zero57 inventory hash; no normalization permitted.");
		report.append("economy071.inventory.v1Zero57\t").append(player.getName()).append(";rawOriginalSHA=").append(expectedRawHash).append(";canonicalRingSHA=").append(currentHash).append(";reconstructedZero57ObjectId=").append(zeroObjectId).append(";uniqueSHApreimage=1;candidateRange=").append(lower).append("..").append(upper).append(";depositReceipt=").append(depositRequest).append(";epicReceipt=").append(epicRequest).append(";positiveNativeAndSql=").append(ringRow.trim()).append(";zero57Sql=ABSENT;wallet=0;stock=0;due=").append(snapshot.getProperty("due")).append(";originalSnapshot=UNCHANGED;SQL_SELECT_ONLY;bounded v1 snapshot normalization,not raw hash equality\n");
	}
	public static void run(Player player, Player poor, StringBuilder report) throws Exception
	{
		if (!PhantomTask071Lane.enabled()) { throw new IllegalArgumentException("Explicit OWN071 native TEST lane required."); }
		try (var connection = DatabaseFactory.getConnection()) { check(PhantomTask071Lane.SCHEMA.equals(connection.getCatalog()), "Own071 TEST schema required."); Economy070Shop.verifySchema(connection); }
		final String installation = System.getProperty("phantom.economy.installation");
		try (var connection = DatabaseFactory.getConnection())
		{
			try
			{
				System.setProperty("phantom.economy.installation", "00000000-0000-0000-0000-000000000000");
				try { Economy070Shop.verifySchema(connection); throw new AssertionError("Foreign runtime installation accepted."); }
				catch (java.sql.SQLException expected) { }
			}
			finally { System.setProperty("phantom.economy.installation", installation); }
		}
		check(Economy070Shop.eligible(player) && Economy070Shop.eligible(poor), "Ordinary access0 native actors required.");
		final var shop = Economy070Shop.getInstance(); final String revision = Economy070Pricing.getInstance().revision();
		player.addItem(ItemProcessType.REWARD, 5575, 22_040_000L, player, false);
		report.append("economy071.fixture\tAA5575+22040000;ordinary ownedTEST actor;not earned income;Noblesse fixture only for native alias gates\n");
		final long aa = count(player, 5575), tokens = count(player, 13722);
		final String buy550 = UUID.randomUUID().toString(), sell550 = UUID.randomUUID().toString();
		check(shop.exchangeTokens(player, 550, false, buy550, revision) == 550, "11mAA->550Tokens credit.");
		check(count(player, 5575) == aa - 11_000_000L && count(player, 13722) == tokens + 550, "11mAA->550Tokens physical debit.");
		shop.exchangeTokens(player, 550, false, buy550, revision);
		check(count(player, 5575) == aa - 11_000_000L && count(player, 13722) == tokens + 550, "Replay minted tokens.");
		rejects(() -> shop.exchangeTokens(player, 1, false, buy550, revision), "Receipt amount was reused.");
		rejects(() -> shop.exchangeTokens(player, 550, true, buy550, revision), "Receipt direction was reused.");
		check(shop.exchangeTokens(player, 550, true, sell550, revision) == 11_000_000L, "550Tokens->11mAA credit.");
		check(count(player, 5575) == aa && count(player, 13722) == tokens, "Symmetric550 roundtrip conservation.");
		shop.exchangeTokens(player, 1, false, UUID.randomUUID().toString(), revision);
		shop.exchangeTokens(player, 1, true, UUID.randomUUID().toString(), revision);
		check(count(player, 5575) == aa && count(player, 13722) == tokens, "Symmetric1 roundtrip conservation.");
		Economy070Board.render("_bbseconomy;aatotokens;1;" + UUID.randomUUID() + ';' + revision, player);
		check(count(player, 5575) == aa - 20_000 && count(player, 13722) == tokens + 1, "Alt+B transaction did not debit/credit native currency.");
		Economy070Board.render("_bbseconomy;tokenstoaa;1;" + UUID.randomUUID() + ';' + revision, player);
		check(count(player, 5575) == aa && count(player, 13722) == tokens, "Alt+B reverse transaction failed conservation.");
		final String raceRequest = UUID.randomUUID().toString(); final var exchangeWorkers = java.util.concurrent.Executors.newFixedThreadPool(2);
		try
		{
			final var ready = new java.util.concurrent.CountDownLatch(1);
			final var first = exchangeWorkers.submit(() -> { ready.await(); return shop.exchangeTokens(player, 1, false, raceRequest, revision); });
			final var second = exchangeWorkers.submit(() -> { ready.await(); return shop.exchangeTokens(player, 1, false, raceRequest, revision); }); ready.countDown();
			check(first.get(20, java.util.concurrent.TimeUnit.SECONDS) == 1 && second.get(20, java.util.concurrent.TimeUnit.SECONDS) == 1, "Concurrent token requests differ.");
			check(count(player, 5575) == aa - 20_000 && count(player, 13722) == tokens + 1, "Concurrent same-request token exchange duplicated currency.");
		}
		finally { exchangeWorkers.shutdownNow(); }
		shop.exchangeTokens(player, 1, true, UUID.randomUUID().toString(), revision);
		for (long quantity : new long[] {0, -1, Long.MAX_VALUE, 1_000_001})
		{ rejects(() -> shop.exchangeTokens(player, quantity, false, UUID.randomUUID().toString(), revision), "Invalid finite quantity accepted."); }
		rejects(() -> shop.exchangeTokens(poor, 1, false, UUID.randomUUID().toString(), revision), "Poor account received free tokens.");
		rejects(() -> shop.exchangeTokens(poor, 550, false, buy550, revision), "Other account replay accepted.");
		player.setInOlympiadMode(true);
		try { rejects(() -> shop.exchangeTokens(player, 1, false, UUID.randomUUID().toString(), revision), "Olympiad token conversion accepted."); }
		finally { player.setInOlympiadMode(false); }
		shop.exchangeTokens(player, 1, false, UUID.randomUUID().toString(), revision);
		final Item source = player.getInventory().getItemByItemId(13722), target = player.getInventory().getItemByItemId(5575);
		final long sourceBefore = source.getCount(), targetBefore = target.getCount();
		try { Item.transactCounts(source, 1, target, 20_000, connection -> { throw new IllegalArgumentException("OWN071 rollback boundary fixture"); }); throw new AssertionError("Fault not propagated."); }
		catch (IllegalArgumentException expected) { check(expected.getMessage().contains("rollback boundary"), "Unexpected rollback failure."); }
		check(source.getCount() == sourceBefore && target.getCount() == targetBefore, "Rollback published native counts.");
		shop.exchangeTokens(player, 1, true, UUID.randomUUID().toString(), revision);
		report.append("economy071.token\t1and550 both directions exact1:20000;physical debit/credit;Alt+B actions;concurrent sameUUID exactly once;replay/crossAccount/quantity/poor/Olympiad/rollback negatives;receipts=").append(buy550).append(',').append(sell550).append('\n');
		aliases(player, report);
		premium(report);
		paidAndStock(player, poor, report);
	}
	public static void verifyRetained(Player player, StringBuilder report) throws Exception
	{
		if (!PhantomTask071Lane.enabled()) { throw new IllegalArgumentException("Explicit OWN071 native TEST lane required."); }
		final long aa = count(player, 5575), tokens = count(player, 13722); int receipts = 0;
		try (var connection = DatabaseFactory.getConnection(); var query = connection.prepareStatement("SELECT request_id,kind,amount FROM economy070_orders WHERE char_id=? AND account_name=? AND kind IN ('TOKEN_TO_AA','AA_TO_TOKEN')"))
		{
			Economy070Shop.verifySchema(connection); query.setInt(1, player.getObjectId()); query.setString(2, player.getAccountName());
			try (var rows = query.executeQuery())
			{
				while (rows.next()) { Economy070Shop.getInstance().exchangeTokens(player, rows.getLong(3), rows.getString(2).equals("TOKEN_TO_AA"), rows.getString(1), Economy070Pricing.getInstance().revision()); receipts++; }
			}
		}
		check(receipts >= 6 && count(player, 5575) == aa && count(player, 13722) == tokens, "Retained replay changed currency or receipts missing.");
		verifyPaidRetained(player, report);
		report.append("economy071.retained\t").append(receipts).append(" immutable token receipts replayed;balances unchanged;ordinary native reload\n");
	}
	public static void verifyPaidRetained(Player player, StringBuilder report) throws Exception
	{
		check(PhantomTask071Lane.enabled() && java.util.Set.of("task071nativeone", "task071nativepoor").contains(player.getAccountName()), "Exact071 paid retained fixture required.");
		final String beforeInventory = retainedInventoryHash(player), beforeStock = retainedStock();
		final long beforeWallet = Economy070Shop.getInstance().balance(player); int epic = 0, paid = 0;
		try (var connection = DatabaseFactory.getConnection(); var query = connection.prepareStatement("SELECT request_id,kind,amount,sku,result_value,item_object_id FROM economy070_orders WHERE char_id=? AND account_name=? AND kind IN ('EPIC','PREMIUM') ORDER BY created_utc,request_id"))
		{
			check(PhantomTask071Lane.SCHEMA.equals(connection.getCatalog()), "Own071 TEST schema required."); Economy070Shop.verifySchema(connection);
			query.setInt(1, player.getObjectId()); query.setString(2, player.getAccountName());
			try (var rows = query.executeQuery())
			{
				while (rows.next())
				{
					paid++;
					final var shop = Economy070Shop.getInstance(); final String revision = Economy070Pricing.getInstance().revision();
					if (rows.getString(2).equals("EPIC"))
					{
						epic++; final Item physical = player.getInventory().getItemByObjectId(rows.getInt(6));
						check(rows.getInt(4) == 6660 && rows.getInt(6) == rows.getLong(5) && physical != null && physical.getOwnerId() == player.getObjectId() && physical.getId() == 6660 && physical.getCount() == 1 && physical.getEnchantLevel() == 0, "Retained paid receipt has no exact owned ring.");
						check(shop.buy(player, rows.getInt(4), rows.getString(1), revision) == rows.getLong(5), "Retained epic object changed.");
					}
					else { check(shop.premium(player, (int) rows.getLong(3), rows.getString(1), revision) == rows.getLong(5), "Retained premium receipt changed."); }
					report.append("economy071.paidReplay.receipt\t").append(player.getName()).append(';').append(rows.getString(1)).append(';').append(rows.getString(2)).append(";object=").append(rows.getInt(6)).append(";result=").append(rows.getLong(5)).append('\n');
				}
			}
		}
		check(paid > 0 && (!"task071nativepoor".equals(player.getAccountName()) || epic == 1), "Poor winner requires exactly one retained EPIC receipt.");
		check(beforeInventory.equals(retainedInventoryHash(player)) && beforeWallet == Economy070Shop.getInstance().balance(player) && beforeStock.equals(retainedStock()), "Paid replay changed physical inventory, wallet or stock/due.");
		report.append("economy071.paidReplay.retained\t").append(player.getName()).append(";receipts=").append(paid).append(";epic=").append(epic).append(";inventorySHA=").append(beforeInventory).append(";wallet=").append(beforeWallet).append(";stock=").append(beforeStock).append(";physical/wallet/stockBeforeAfter=UNCHANGED\n");
	}
	private static String retainedInventoryHash(Player player) throws Exception
	{
		final var rows = new StringBuilder();
		for (var item : player.getInventory().getItems().stream().sorted(java.util.Comparator.comparingInt(Item::getObjectId)).toList()) { rows.append(item.getObjectId()).append(':').append(item.getId()).append(':').append(item.getCount()).append(':').append(item.getEnchantLevel()).append('\n'); }
		return diagnosticHash(rows.toString());
	}
	private static String retainedStock() throws Exception
	{
		try (var connection = DatabaseFactory.getConnection(); var query = connection.prepareStatement("SELECT current_stock,next_restock,epoch,version,max_stock,restock_days,source_price_revision FROM economy070_stock WHERE sku=6660"); var rows = query.executeQuery())
		{ check(PhantomTask071Lane.SCHEMA.equals(connection.getCatalog()) && rows.next(), "Retained own071 stock missing."); final var value = new StringBuilder(); for (int column = 1; column <= 7; column++) { value.append(rows.getString(column)).append(';'); } check(!rows.next(), "Duplicate retained stock."); return value.toString(); }
	}
	private static void paidAndStock(Player player, Player poor, StringBuilder report) throws Exception
	{
		final var shop = Economy070Shop.getInstance(); final String revision = Economy070Pricing.getInstance().revision();
		final long fee = Economy070Pricing.getInstance().price("epic.6660");
		check(fee == 200_000_000L && shop.stock(6660).count() == 1, "Fresh OWN071 last unit required; existing sold stock must not be reset.");
		player.addItem(ItemProcessType.REWARD, 57, fee + 1_000_000L, player, false); poor.addItem(ItemProcessType.REWARD, 57, fee, poor, false);
		report.append("economy071.fixturePaid\tAdena+201000000 first,+200000000 poor;explicit TEST funds for paid stock/premium,not earned income\n");
		shop.deposit(player, fee, UUID.randomUUID().toString(), revision); shop.deposit(poor, fee, UUID.randomUUID().toString(), revision);
		final long adenaBefore = player.getAdena(); final String premiumRequest = UUID.randomUUID().toString();
		final long end = shop.premium(player, 1, premiumRequest, revision);
		check(player.getAdena() == adenaBefore - 1_000_000L && end > System.currentTimeMillis(), "Paid1day premium debit/expiry.");
		check(shop.premium(player, 1, premiumRequest, revision) == end && player.getAdena() == adenaBefore - 1_000_000L && PremiumManager.getInstance().getPremiumExpiration(player.getAccountName()) == end, "Premium replay duplicated fee/term.");
		final var start = new java.util.concurrent.CountDownLatch(1); final var workers = java.util.concurrent.Executors.newFixedThreadPool(2);
		try
		{
			final var first = workers.submit(() -> raceBuy(shop, player, revision, start));
			final var second = workers.submit(() -> raceBuy(shop, poor, revision, start)); start.countDown();
			final int won = first.get(20, java.util.concurrent.TimeUnit.SECONDS) + second.get(20, java.util.concurrent.TimeUnit.SECONDS);
			check(won == 1 && count(player, 6660) + count(poor, 6660) == 1, "Last-unit race did not produce exactly one owned ring.");
			check(shop.stock(6660).count() == 0 && shop.stock(6660).nextRestockUtc() > System.currentTimeMillis() && shop.balance(player) + shop.balance(poor) == fee, "Finite stock/restock/wallet conservation failed.");
		}
		finally { workers.shutdownNow(); }
		report.append("economy071.paid\tpremium1day paid1000000+sameUUID replay;200m epic6660 lastunit2buyer race1winner;nativeRing1/globalStock0;wallet sum200m;no restock reset\n");
	}
	private static int raceBuy(Economy070Shop shop, Player player, String revision, java.util.concurrent.CountDownLatch start) throws Exception
	{
		start.await();
		try { shop.buy(player, 6660, UUID.randomUUID().toString(), revision); return 1; }
		catch (IllegalArgumentException expected) { return 0; }
	}
	private static void aliases(Player player, StringBuilder report) throws Exception
	{
		final var lists = MultisellData.getInstance(); final boolean noble = player.isNoble(); final int fame = player.getFame();
		try
		{
			player.setNoble(false); lists.separateAndSend(91072, player, null, false); check(player.getMultiSell() == null, "Native Noblesse gate bypassed.");
			player.setNoble(true); check(Economy071OlympiadShop.eligible(player), "Alias test requires third main class as declared fixture.");
			for (int list : new int[] {91071, 91072})
			{ lists.separateAndSend(list, player, null, false); check(player.getMultiSell() != null && player.getMultiSell().getEntries().size() == (list == 91071 ? 38 : 69), "Complete alias unavailable."); }
			player.addItem(ItemProcessType.REWARD, 21874, 1, player, false);
			player.setFame(0); lists.separateAndSend(91072, player, null, false);
			MultiSellChoose.executePreparedExchange(player, 91072, player.getMultiSell().getEntries().getFirst().getEntryId(), 1);
			check(count(player, 21874) == 0 && player.getFame() == 100, "Native Medal->Fame -300 transfer failed.");
			Economy070Shop.getInstance().exchangeTokens(player, 550, false, UUID.randomUUID().toString(), Economy070Pricing.getInstance().revision());
			final long tokenCodexBefore = count(player, 6622), tokensBefore = count(player, 13722);
			lists.separateAndSend(91072, player, null, false);
			final var tokenCodex = player.getMultiSell().getEntries().stream().filter(entry -> entry.getProducts().stream().anyMatch(product -> product.getItemId() == 6622)).findFirst().orElseThrow();
			MultiSellChoose.executePreparedExchange(player, 91072, tokenCodex.getEntryId(), 1);
			check(count(player, 13722) == tokensBefore - 550 && count(player, 6622) == tokenCodexBefore + 1, "Native alias Codex550-token debit/product failed.");
			final long beforeCodex = count(player, 6622), beforeAa = count(player, 5575);
			lists.separateAndSend(91005, player, null, false); MultiSellChoose.executePreparedExchange(player, 91005, player.getMultiSell().getEntries().getFirst().getEntryId(), 1);
			check(count(player, 6622) == beforeCodex + 1 && count(player, 5575) == beforeAa - 11_000_000L, "Native AA Codex11m purchase failed.");
			report.append("economy071.olympiad\taliases38/69;native Noblesse gate negative;21874->Fame100(-300) native debit/credit;Codex6622 native550Tokens and AA11000000;Noblesse restored;no hero/points created\n");
		}
		finally { player.setNoble(noble); player.setFame(fame); player.setMultiSell(null); }
	}
	private static void premium(StringBuilder report)
	{
		final double previous = PremiumSystemConfig.PREMIUM_MAXIMUM_COMBINED_CHANCE_BONUS;
		try
		{
			check(PremiumSystemConfig.validatedCombinedChanceCap("3") == 3 && PremiumSystemConfig.validatedCombinedChanceCap("2.5") == 2.5, "Valid configured chance cap rejected.");
			for (String value : new String[] {"0", "-1", "NaN", "Infinity", "101", "bad"}) { check(PremiumSystemConfig.validatedCombinedChanceCap(value) == 1, "Invalid cap failed open: " + value); }
			PremiumSystemConfig.PREMIUM_MAXIMUM_COMBINED_CHANCE_BONUS = 2.5;
			check(PremiumLootPolicy.combinedChance(2, 2) == 2.5 && PremiumLootPolicy.combinedChance(0, 2) == 0, "Configured cap or explicit zero ignored.");
			report.append("economy071.premium\tconfigured3/2.5;invalid/nonfinite->1;perItem0 retained;existing multipliers unchanged\n");
		}
		finally { PremiumSystemConfig.PREMIUM_MAXIMUM_COMBINED_CHANCE_BONUS = previous; }
	}
	@FunctionalInterface private interface Operation { void run() throws Exception; }
	private static void rejects(Operation operation, String message) throws Exception { try { operation.run(); } catch (IllegalArgumentException expected) { return; } throw new AssertionError(message); }
	private static long count(Player player, int id) { return player.getInventory().getInventoryItemCount(id, -1); }
	private static void check(boolean value, String message) { if (!value) { throw new AssertionError(message); } }
}
