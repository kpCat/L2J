/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.qol;

import java.sql.Connection;
import java.util.UUID;
import java.util.function.LongSupplier;
import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.config.PlayerConfig;
import org.l2jmobius.gameserver.config.custom.PremiumSystemConfig;
import org.l2jmobius.gameserver.config.custom.CommunityBoardConfig;
import org.l2jmobius.gameserver.data.xml.ItemData;
import org.l2jmobius.gameserver.managers.IdManager;
import org.l2jmobius.gameserver.managers.PremiumManager;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.model.item.instance.Item;
import org.l2jmobius.gameserver.network.serverpackets.InventoryUpdate;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEpicItemPolicy;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry;

/** OWN070 money, item rows, account term, stock and immutable receipts share one SQL commit. */
public final class Economy070Shop
{
	public record Stock(int itemId, int count, long nextRestockUtc, long epoch) { }
	private final Economy070Pricing _pricing;
	private final LongSupplier _clock;
	private final Runnable _afterEpicCommit;
	public Economy070Shop(Economy070Pricing pricing, LongSupplier clock) { this(pricing, clock, () -> { }); }
	public Economy070Shop(Economy070Pricing pricing, LongSupplier clock, Runnable afterEpicCommit) { _pricing = pricing; _clock = clock; _afterEpicCommit = afterEpicCommit; }
	public static Economy070Shop getInstance() { return Holder.INSTANCE; }
	private static final class Holder { private static final Economy070Shop INSTANCE = new Economy070Shop(Economy070Pricing.getInstance(), System::currentTimeMillis); }
	public static boolean eligible(Player player)
	{
		if (player == null || player.getAccessLevel() == null || player.getAccessLevel().getLevel() != 0 || player.isGM() || player.hasHeadlessOutboundSession() || player.isNativeWorkManaged()
			|| player.isAlikeDead() || player.isProcessingTransaction() || player.isInventoryDisabled() || player.isInStoreMode() || player.isInCombat() || player.isInOlympiadMode()) { return false; }
		if (CommunityBoardConfig.COMMUNITYBOARD_COMBAT_DISABLED && (player.isCastingNow() || player.isCastingSimultaneouslyNow() || player.isInDuel() || player.isOnEvent() || player.getPvpFlag() > 0 || player.isInsideZone(org.l2jmobius.gameserver.model.zone.ZoneId.SIEGE) || player.isInsideZone(org.l2jmobius.gameserver.model.zone.ZoneId.PVP))) { return false; }
		if (CommunityBoardConfig.COMMUNITYBOARD_KARMA_DISABLED && player.getKarma() > 0) { return false; }
		final var owner = PhantomIdentityLeaseRegistry.getInstance().getOwnerKind(player.getObjectId());
		return owner != PhantomIdentityLeaseRegistry.OwnerKind.PHANTOM && owner != PhantomIdentityLeaseRegistry.OwnerKind.BACKGROUND;
	}
	private void validate(Player player, String request, String revision)
	{
		if (!eligible(player) || !UUID.fromString(request).toString().equals(request) || !_pricing.revision().equals(revision)) { throw new IllegalArgumentException("Недоступная операция или устаревшая цена. Откройте страницу заново."); }
	}
	private static void own(Connection connection) throws Exception
	{
		if (!java.util.Set.of("l2jmobiush5_phantom_test_070", "l2jmobiush5_phantom_release_070").contains(connection.getCatalog()))
		{ throw new IllegalStateException("Служба 070 требует собственной БД 070."); }
		try (var query = connection.prepareStatement("SELECT TABLE_NAME,ENGINE FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN ('items','account_premium','economy070_wallet','economy070_stock','economy070_orders')"); var rows = query.executeQuery())
		{
			int count = 0;
			while (rows.next()) { count++; if (!"InnoDB".equalsIgnoreCase(rows.getString(2))) { throw new IllegalStateException("Атомарный платёж требует InnoDB: " + rows.getString(1)); } }
			if (count != 5) { throw new IllegalStateException("Таблицы 070 не установлены полностью."); }
		}
	}
	public long balance(Player player) throws Exception
	{
		try (var connection = DatabaseFactory.getConnection())
		{
			own(connection);
			try (var query = connection.prepareStatement("SELECT balance FROM economy070_wallet WHERE account_name=?"))
			{ query.setString(1, player.getAccountName()); try (var rows = query.executeQuery()) { return rows.next() ? rows.getLong(1) : 0; } }
		}
	}
	public long deposit(Player player, long amount, String request, String revision) throws Exception
	{
		return savings(player, amount, request, revision, true);
	}
	public long withdraw(Player player, long amount, String request, String revision) throws Exception
	{
		return savings(player, amount, request, revision, false);
	}
	private long savings(Player player, long amount, String request, String revision, boolean deposit) throws Exception
	{
		validate(player, request, revision);
		if (amount < 1 || amount > PlayerConfig.MAX_ADENA) { throw new IllegalArgumentException("Сумма вне лимита Adena."); }
		synchronized (player)
		{
			final Item coin = player.getInventory().getAdenaForCountTransaction();
			final String kind = deposit ? "DEPOSIT" : "WITHDRAW";
			final Long previous = replay(player, request, kind, amount, 0);
			if (previous != null) { return previous; }
			if (deposit && coin.getCount() < amount || !deposit && coin.getCount() > PlayerConfig.MAX_ADENA - amount)
			{ throw new IllegalArgumentException("Недостаточно Adena или места до лимита валюты."); }
			if (!deposit && (!player.getInventory().validateCapacityByItemId(57, amount) || !player.getInventory().validateWeightByItemId(57, amount)))
			{ throw new IllegalArgumentException("Недостаточно места в инвентаре."); }
			try
			{
				final long result = coin.transactCount(deposit ? -amount : amount, connection ->
				{
					own(connection); final long before = lockWallet(connection, player);
					checkReplay(connection, player, request, kind, amount, 0);
					final long after = Math.addExact(before, deposit ? amount : -amount);
					if (after < 0) { throw new IllegalArgumentException("Недостаточно средств в Epic Savings."); }
					wallet(connection, player, after);
					receipt(connection, player, request, kind, null, null, amount, 0, after);
					return after;
				});
				notifyCount(player, coin); return result;
			}
			catch (Replay previousResult) { return previousResult.value; }
		}
	}
	public long premium(Player player, int days, String request, String revision) throws Exception
	{
		validate(player, request, revision);
		if (!PremiumSystemConfig.PREMIUM_SYSTEM_ENABLED || !CommunityBoardConfig.COMMUNITY_PREMIUM_SYSTEM_ENABLED || !java.util.Set.of(1, 3, 7, 15, 20, 30).contains(days))
		{ throw new IllegalArgumentException("Премиум выключен или выбран неподдерживаемый срок."); }
		final long fee = Math.multiplyExact(_pricing.price("premium.day"), days);
		synchronized (player)
		{
			final Long previous = replay(player, request, "PREMIUM", days, 0);
			if (previous != null) { publishAccount(player); return previous; }
			final var coin = player.getInventory().getAdenaForCountTransaction();
			if (fee > coin.getCount()) { throw new IllegalArgumentException("Недостаточно Adena."); }
			try
			{
				final long expiry = coin.transactCount(-fee, connection ->
				{
					own(connection); lockWallet(connection, player); checkReplay(connection, player, request, "PREMIUM", days, 0);
					final long end = PremiumManager.getInstance().extendPremium(connection, player.getAccountName(), days, _clock.getAsLong());
					receipt(connection, player, request, "PREMIUM", null, null, days, 0, end); return end;
				});
				PremiumManager.getInstance().publishPremiumExpiration(player.getAccountName(), expiry); notifyCount(player, coin); return expiry;
			}
			catch (Replay previousResult) { publishAccount(player); return previousResult.value; }
		}
	}
	private static void publishAccount(Player player) throws Exception
	{
		try (var connection = DatabaseFactory.getConnection(); var query = connection.prepareStatement("SELECT enddate FROM account_premium WHERE account_name=?"))
		{
			own(connection); query.setString(1, player.getAccountName());
			try (var rows = query.executeQuery()) { if (rows.next()) { PremiumManager.getInstance().publishPremiumExpiration(player.getAccountName(), rows.getLong(1)); } }
		}
	}
	public Stock stock(int itemId) throws Exception
	{
		final var quote = _pricing.quote("epic." + itemId);
		try (var connection = DatabaseFactory.getConnection())
		{
			own(connection); connection.setAutoCommit(false);
			try { final Stock result = lockStock(connection, quote); connection.commit(); return result; }
			catch (Exception failure) { connection.rollback(); throw failure; }
		}
	}

	/** One paid character entitlement; quest scripts retain the native stage and consumption authority. */
	public long paidEntitlement(Player player, String key, String kind, String request, String revision) throws Exception
	{
		validate(player, request, revision);
		if (!key.equals("subclass.gateway") || !kind.equals("SUBCLASS_GATEWAY")) { throw new IllegalArgumentException("Unsupported entitlement."); }
		synchronized (player)
		{
			try (var connection = DatabaseFactory.getConnection())
			{
				own(connection); final Long existing = entitlement(connection, player, kind); if (existing != null) { return existing; }
			}
			final var coin = player.getInventory().getAdenaForCountTransaction(); final long fee = _pricing.price(key);
			if (coin.getCount() < fee) { throw new IllegalArgumentException("Недостаточно Adena."); }
			try
			{
				final long result = coin.transactCount(-fee, connection ->
				{
					own(connection); lockWallet(connection, player);
					final Long existing = entitlement(connection, player, kind); if (existing != null) { throw new Replay(existing); }
					checkReplay(connection, player, request, kind, fee, 0);
					receipt(connection, player, request, kind, null, null, fee, 0, player.getObjectId()); return (long) player.getObjectId();
				});
				notifyCount(player, coin); return result;
			}
			catch (Replay existing) { return existing.value; }
		}
	}
	private static Long entitlement(Connection connection, Player player, String kind) throws Exception
	{
		try (var query = connection.prepareStatement("SELECT result_value FROM economy070_orders WHERE account_name=? AND char_id=? AND kind=? LIMIT 1"))
		{ query.setString(1, player.getAccountName()); query.setInt(2, player.getObjectId()); query.setString(3, kind); try (var rows = query.executeQuery()) { return rows.next() ? rows.getLong(1) : null; } }
	}
	public int buy(Player player, int itemId, String request, String revision) throws Exception
	{
		validate(player, request, revision);
		final var quote = _pricing.quote("epic." + itemId);
		// The canonical catalog, rather than grade or a forged bypass, defines admission.
		if (!PhantomEpicItemPolicy.isEpic(itemId) || quote.itemId() != itemId || quote.currencyId() != 57 || quote.restockDays() <= 0 || ItemData.getInstance().getTemplate(itemId) == null)
		{ throw new IllegalArgumentException("Неподтверждённый эпический предмет."); }
		synchronized (player)
		{
			reconcileEpicDelivery(player);
			final Long previous = replay(player, request, "EPIC", quote.price(), itemId);
			if (previous != null) { return Math.toIntExact(previous); }
			if (!player.getInventory().validateCapacityByItemId(itemId, 1) || !player.getInventory().validateWeightByItemId(itemId, 1))
			{ throw new IllegalArgumentException("Недостаточно места в инвентаре."); }
			int objectId = 0; boolean committing = false;
			try (var connection = DatabaseFactory.getConnection())
			{
				own(connection); connection.setAutoCommit(false);
				try
				{
					final long before = lockWallet(connection, player); checkReplay(connection, player, request, "EPIC", quote.price(), itemId);
					final Stock stock = lockStock(connection, quote);
					if (stock.count() != 1 || before < quote.price()) { throw new IllegalArgumentException("Нет запаса или средств в Epic Savings."); }
					wallet(connection, player, before - quote.price());
					final long due = Math.addExact(_clock.getAsLong(), Math.multiplyExact(quote.restockDays(), 86400000L));
					try (var update = connection.prepareStatement("UPDATE economy070_stock SET current_stock=0,next_restock=?,version=version+1,restock_days=?,source_price_revision=? WHERE sku=? AND current_stock=1 AND max_stock=1"))
					{ update.setLong(1, due); update.setInt(2, quote.restockDays()); update.setString(3, _pricing.revision()); update.setInt(4, itemId); if (update.executeUpdate() != 1) { throw new IllegalStateException("Запас изменился."); } }
					objectId = IdManager.getInstance().getNextId();
					try (var insert = connection.prepareStatement("INSERT INTO items(owner_id,object_id,item_id,count,loc,loc_data,enchant_level,mana_left,time) VALUES(?,?,?,1,'INVENTORY',0,0,-1,-1)"))
					{ insert.setInt(1, player.getObjectId()); insert.setInt(2, objectId); insert.setInt(3, itemId); insert.executeUpdate(); }
					receipt(connection, player, request, "EPIC", itemId, stock.epoch(), quote.price(), objectId, objectId);
					committing = true; connection.commit();
				}
				catch (Exception failure)
				{
					connection.rollback();
					if (objectId != 0 && !committing) { IdManager.getInstance().releaseId(objectId); }
					throw failure;
				}
			}
			catch (Replay previousResult) { return Math.toIntExact(previousResult.value); }
			_afterEpicCommit.run();
			// On interruption here the native row and receipt already exist. Replay never inserts again;
			// ordinary relog/restart restores the exact item object from the same committed row.
			reconcileEpicDelivery(player); return objectId;
		}
	}
	/** Replay only hydrates an existing owned native row; a sold/transferred item is never recreated. */
	private void reconcileEpicDelivery(Player player) throws Exception
	{
		// Item.readRestoreRow requires object_id in column1, as in native ItemContainer.restore.
		try (var connection = DatabaseFactory.getConnection(); var query = connection.prepareStatement("SELECT i.object_id,i.* FROM items i JOIN economy070_orders o ON o.item_object_id=i.object_id WHERE o.kind='EPIC' AND o.char_id=? AND o.account_name=? AND i.owner_id=? AND i.loc='INVENTORY'"))
		{
			own(connection); query.setInt(1, player.getObjectId()); query.setString(2, player.getAccountName()); query.setInt(3, player.getObjectId());
			try (var rows = query.executeQuery())
			{
				while (rows.next())
				{
					final int objectId = rows.getInt("object_id");
					if (player.getInventory().getItemByObjectId(objectId) != null) { continue; }
					final var existing = World.getInstance().findObject(objectId);
					final Item item;
					if (existing == null) { item = Item.restoreFromDb(player.getObjectId(), rows); World.getInstance().addObject(item); }
					else if (existing instanceof Item current && current.getOwnerId() == player.getObjectId() && current.getItemLocation() == org.l2jmobius.gameserver.model.item.enums.ItemLocation.INVENTORY) { item = current; }
					else { throw new IllegalStateException("Epic delivery pending: native object ownership differs."); }
					player.getInventory().addItem(ItemProcessType.RESTORE, item, player, this); notifyCount(player, item);
				}
			}
		}
	}
	private Stock lockStock(Connection connection, Economy070Pricing.Quote quote) throws Exception
	{
		try (var query = connection.prepareStatement("SELECT current_stock,next_restock,epoch FROM economy070_stock WHERE sku=? FOR UPDATE"))
		{
			query.setInt(1, quote.itemId()); try (var rows = query.executeQuery())
			{
				if (!rows.next()) { throw new IllegalStateException("Запас не инициализирован оператором."); }
				int count = rows.getInt(1); long due = rows.getLong(2), epoch = rows.getLong(3);
				if (count == 0 && due > 0 && _clock.getAsLong() >= due)
				{
					try (var update = connection.prepareStatement("UPDATE economy070_stock SET current_stock=1,next_restock=0,epoch=epoch+1,version=version+1 WHERE sku=?"))
					{ update.setInt(1, quote.itemId()); update.executeUpdate(); }
					count = 1; due = 0; epoch++;
				}
				return new Stock(quote.itemId(), count, due, epoch);
			}
		}
	}
	private static long lockWallet(Connection connection, Player player) throws Exception
	{
		try (var insert = connection.prepareStatement("INSERT IGNORE INTO economy070_wallet(account_name,balance) VALUES(?,0)")) { insert.setString(1, player.getAccountName()); insert.executeUpdate(); }
		try (var query = connection.prepareStatement("SELECT balance FROM economy070_wallet WHERE account_name=? FOR UPDATE"))
		{ query.setString(1, player.getAccountName()); try (var rows = query.executeQuery()) { if (!rows.next()) { throw new IllegalStateException("Wallet missing."); } return rows.getLong(1); } }
	}
	private static void wallet(Connection connection, Player player, long value) throws Exception
	{
		try (var update = connection.prepareStatement("UPDATE economy070_wallet SET balance=? WHERE account_name=?"))
		{ update.setLong(1, value); update.setString(2, player.getAccountName()); if (update.executeUpdate() != 1) { throw new IllegalStateException("Wallet changed."); } }
	}
	private Long replay(Player player, String request, String kind, long amount, int sku) throws Exception
	{
		try (var connection = DatabaseFactory.getConnection()) { own(connection); return findReceipt(connection, player, request, kind, amount, sku); }
	}
	private static Long findReceipt(Connection connection, Player player, String request, String kind, long amount, int sku) throws Exception
	{
		try (var query = connection.prepareStatement("SELECT account_name,char_id,kind,amount,COALESCE(sku,0),result_value FROM economy070_orders WHERE request_id=?"))
		{
			query.setString(1, request); try (var rows = query.executeQuery())
			{
				if (!rows.next()) { return null; }
				if (!player.getAccountName().equals(rows.getString(1)) || player.getObjectId() != rows.getInt(2) || !kind.equals(rows.getString(3)) || amount != rows.getLong(4) || sku != rows.getInt(5))
				{ throw new IllegalArgumentException("Request receipt belongs to another operation."); }
				return rows.getLong(6);
			}
		}
	}
	private static void checkReplay(Connection connection, Player player, String request, String kind, long amount, int sku) throws Exception
	{
		final Long result = findReceipt(connection, player, request, kind, amount, sku); if (result != null) { throw new Replay(result); }
	}
	private void receipt(Connection connection, Player player, String request, String kind, Integer sku, Long epoch, long amount, int objectId, long result) throws Exception
	{
		try (var insert = connection.prepareStatement("INSERT INTO economy070_orders(request_id,account_name,char_id,kind,sku,epoch,amount,revision,item_object_id,result_value,created_utc) VALUES(?,?,?,?,?,?,?,?,?,?,?)"))
		{
			insert.setString(1, request); insert.setString(2, player.getAccountName()); insert.setInt(3, player.getObjectId()); insert.setString(4, kind);
			insert.setObject(5, sku); insert.setObject(6, epoch); insert.setLong(7, amount); insert.setString(8, _pricing.revision()); insert.setInt(9, objectId); insert.setLong(10, result); insert.setLong(11, _clock.getAsLong()); insert.executeUpdate();
		}
	}
	private static void notifyCount(Player player, Item item) { final var update = new InventoryUpdate(); update.addItem(item); player.sendInventoryUpdate(update); }
	private static final class Replay extends Exception { private static final long serialVersionUID = 1L; private final long value; private Replay(long result) { value = result; } }
}
