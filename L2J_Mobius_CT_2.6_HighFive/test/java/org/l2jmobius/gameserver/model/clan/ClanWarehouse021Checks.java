/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.model.clan;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.config.IdManagerConfig;
import org.l2jmobius.gameserver.data.xml.ItemData;
import org.l2jmobius.gameserver.data.xml.PlayerTemplateData;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.appearance.PlayerAppearance;
import org.l2jmobius.gameserver.model.actor.templates.PlayerTemplate;
import org.l2jmobius.gameserver.model.clan.ClanWarehouseTransfer.Destination;
import org.l2jmobius.gameserver.model.clan.ClanWarehouseTransfer.Request;
import org.l2jmobius.gameserver.model.clan.ClanWarehouseTransfer.TransferOutcome;
import org.l2jmobius.gameserver.model.item.enums.ItemLocation;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.model.item.instance.Item;
import org.l2jmobius.gameserver.model.itemcontainer.ClanWarehouse;
import org.l2jmobius.gameserver.model.itemcontainer.Inventory;

/** Exact durable item/fee outcomes against the 021 isolated MariaDB stock schema. */
public final class ClanWarehouse021Checks
{
	private static final int ACTOR = 21002163;
	private static final int CLAN = 21002164;
	private static final long PROFILE = 21002162L;

	private ClanWarehouse021Checks() { }

	public static void verify() throws Exception
	{
		try (var connection = DatabaseFactory.getConnection(); var sql = connection.createStatement())
		{
			sql.executeUpdate("INSERT INTO phantom_profiles(profile_id,character_object_id) VALUES(21002162,21002163)");
			sql.executeUpdate("INSERT INTO phantom_profile_components(profile_id,component_type,component_schema_version,payload) VALUES(21002162,'clan.organization',2,X'01')");
			sql.executeUpdate("INSERT INTO clan_data(clan_id,clan_name,clan_level,leader_id) VALUES(21002164,'ClanWarehouseProof021',5,21002163)");
			sql.executeUpdate("INSERT INTO characters(charId,char_name,sp,classid,base_class,clanid) VALUES(21002163,'ClanWarehouseProof021',450,88,88,21002164)");
		}
		IdManagerConfig.load();
		final int firstNativeObject = IdManagerConfig.FIRST_OBJECT_ID + 21021;
		final Item sword = item(firstNativeObject, 1, ACTOR, ItemLocation.INVENTORY, 1);
		sword.setEnchantLevel(4);
		final Item adena = item(firstNativeObject + 1, Inventory.ADENA_ID, ACTOR, ItemLocation.INVENTORY, 200);
		final Item stems = item(firstNativeObject + 2, 1864, ACTOR, ItemLocation.INVENTORY, 11);
		for (Item item : new Item[] { sword, adena, stems }) { item.updateDatabase(true); }
		try (var connection = DatabaseFactory.getConnection(); var sql = connection.prepareStatement("UPDATE items SET time_of_use=7 WHERE object_id=?"))
		{
			sql.setInt(1, sword.getObjectId());
			check(sql.executeUpdate() == 1, "Isolated gear metadata fixture was not created.");
		}
		final var deposit = ClanWarehouseTransfer.prepare(PROFILE, ACTOR, CLAN, ACTOR, new UUID(21, 51), sword, null, adena, 1, true);
		final var staleLeader = ClanWarehouseTransfer.prepare(PROFILE, ACTOR, CLAN, ACTOR + 1, new UUID(21, 56), sword, null, adena, 1, true);
		reject(staleLeader, "Native clan warehouse is unavailable.");
		try (var connection = DatabaseFactory.getConnection())
		{
			connection.setAutoCommit(false);
			deposit.stage(connection);
			connection.rollback();
		}
		check(read(deposit) == ClanWarehouseTransfer.Outcome.ROLLED_BACK, "A rolled-back object transfer left the fee, ownership or receipt changed.");
		try (var connection = DatabaseFactory.getConnection())
		{
			connection.setAutoCommit(false);
			deposit.stage(connection);
			connection.commit();
		}
		check(read(deposit) == ClanWarehouseTransfer.Outcome.APPLIED, "Reconnection lost the exact transfer or fee.");
		reject(deposit, "Clan warehouse receipt already committed.");
		deposit.publish();
		check(sword.getOwnerId() == CLAN && sword.getItemLocation() == ItemLocation.CLANWH && sword.getEnchantLevel() == 4 && adena.getCount() == 170,
			"Native memory lost the gear object, enchantment or deposit fee.");
		final var withdraw = ClanWarehouseTransfer.prepare(PROFILE, ACTOR, CLAN, ACTOR, new UUID(21, 52), sword, null, null, 1, false);
		commit(withdraw);
		check(sword.getOwnerId() == ACTOR && sword.getItemLocation() == ItemLocation.INVENTORY && sword.getEnchantLevel() == 4 && adena.getCount() == 170,
			"Withdrawal did not restore the original object without another fee.");
		try (var connection = DatabaseFactory.getConnection(); var sql = connection.prepareStatement("SELECT time_of_use FROM items WHERE object_id=?"))
		{
			sql.setInt(1, sword.getObjectId());
			try (var row = sql.executeQuery()) { check(row.next() && row.getInt(1) == 7, "Whole-object movement rewrote native usage metadata."); }
		}

		final Item newStack = item(firstNativeObject + 3, 1864, CLAN, ItemLocation.CLANWH, 4);
		try
		{
			ClanWarehouseTransfer.prepare(PROFILE, ACTOR, CLAN, ACTOR, new UUID(21, 57), stems, new Destination(newStack, false), adena, 4, true);
			throw new AssertionError("An unsaved existing stack was treated as a fresh object.");
		}
		catch (IllegalArgumentException expected)
		{
			check("Invalid clan warehouse stack transfer.".equals(expected.getMessage()), "Existing unsaved target was rejected for the wrong reason.");
		}
		final var split = ClanWarehouseTransfer.prepare(PROFILE, ACTOR, CLAN, ACTOR, new UUID(21, 53), stems, new Destination(newStack, true), adena, 4, true);
		check(split.execute(() -> false, () -> { throw new AssertionError("Cancelled transfer published."); }) == ClanWarehouseTransfer.Outcome.ROLLED_BACK
			&& read(split) == ClanWarehouseTransfer.Outcome.ROLLED_BACK && !split.pending(), "Stale native authority left a staged write.");
		commit(split);
		check(stems.getCount() == 7 && newStack.getCount() == 4 && adena.getCount() == 140, "Native stack split or fee differs.");
		final var merge = ClanWarehouseTransfer.prepare(PROFILE, ACTOR, CLAN, ACTOR, new UUID(21, 54), newStack, new Destination(stems, false), null, 4, false);
		commit(merge);
		check(newStack.getCount() == 0 && stems.getCount() == 11 && adena.getCount() == 140, "Consumed stack or merged count differs.");

		final var sameAdena = item(firstNativeObject + 4, Inventory.ADENA_ID, CLAN, ItemLocation.CLANWH, 20);
		final var money = ClanWarehouseTransfer.prepare(PROFILE, ACTOR, CLAN, ACTOR, new UUID(21, 55), adena, new Destination(sameAdena, true), adena, 20, true);
		check(money.execute(() -> true, () -> { throw new IllegalStateException("TEST settlement interruption"); }) == ClanWarehouseTransfer.Outcome.CONFLICT
			&& money.pending() && adena.blocksInventoryDebit() && sameAdena.blocksInventoryDebit(),
			"Confirmed SQL with interrupted native publication lost its item fences.");
		check(adena.tryCreditContainerStack(ItemProcessType.NONE, 7, null, null, () -> true),
			"An earned native credit was diverted to a second Adena stack while settlement was pending.");
		check(money.settle(() -> { }) == ClanWarehouseTransfer.Outcome.APPLIED && !money.pending() && !adena.blocksInventoryDebit(),
			"Exact receipt did not complete interrupted native publication.");
		check(adena.getCount() == 97 && sameAdena.getCount() == 20, "Adena deposit did not preserve the fee, transferred quantity and late credit.");
		adena.updateDatabase(true);
		final Clan restored = new Clan(CLAN);
		final Item cold = restored.getWarehouse().getItemByObjectId(sameAdena.getObjectId());
		check(cold != null && cold.getOwnerId() == CLAN && cold.getItemLocation() == ItemLocation.CLANWH && cold.getCount() == 20,
			"Native clan warehouse did not restore the exact deposited currency object.");
		System.out.println("PASS 021 native warehouse exact gear, split, merge, Adena, fee and cold transaction readback");
		verifyNativeContainers(restored, sword.getObjectId(), cold.getObjectId());
	}

	private static void verifyNativeContainers(Clan clan, int swordId, int warehouseAdenaId) throws Exception
	{
		final var constructor = Player.class.getDeclaredConstructor(int.class, PlayerTemplate.class, String.class, PlayerAppearance.class);
		constructor.setAccessible(true);
		final var player = constructor.newInstance(ACTOR, PlayerTemplateData.getInstance().getTemplate(0), "test021",
			new PlayerAppearance((byte) 0, (byte) 0, (byte) 0, false));
		try
		{
			clan.getClanMember(ACTOR).setPlayer(player);
			player.setClan(clan);
			player.getInventory().restore();
			final ClanWarehouse warehouse = (ClanWarehouse) clan.getWarehouse();
			final Item sword = player.getInventory().getItemByObjectId(swordId);
			check(sword != null && sword.getEnchantLevel() == 4 && player.getAdena() == 97, "Native inventory did not load the durable warehouse fixture.");
			final var deposit = new Request(CLAN, ACTOR, swordId, 1, true, new UUID(21, 61), Long.MAX_VALUE);
			final var withdrawal = new Request(CLAN, ACTOR, warehouseAdenaId, 5, false, new UUID(21, 62), Long.MAX_VALUE);
			synchronized (player)
			{
				synchronized (warehouse)
				{
					check(ClanWarehouseTransfer.transferLocked(PROFILE, player, clan, warehouse, deposit, 68, List.of(), () -> true) == TransferOutcome.NOT_READY,
						"Deposit crossed the protected Adena reserve.");
					check(ClanWarehouseTransfer.transferLocked(PROFILE, player, clan, warehouse, deposit, 67, List.of(), () -> warehouse.hasPendingTransfer()
						&& warehouse.transferItem(ItemProcessType.TRANSFER, warehouseAdenaId, 1, player.getInventory(), player, null) == null) == TransferOutcome.APPLIED,
						"The native container deposit did not safely exclude a stock withdrawal.");
					check(warehouse.getItemByObjectId(swordId) == sword && player.getInventory().getItemByObjectId(swordId) == null && player.getAdena() == 67,
						"Native gear custody or player Adena cache differs after deposit.");
					check(ClanWarehouseTransfer.transferLocked(PROFILE, player, clan, warehouse, withdrawal, 0, List.of(), () -> true) == TransferOutcome.APPLIED,
						"The native warehouse currency withdrawal did not complete.");
					check(warehouse.getItemByObjectId(warehouseAdenaId).getCount() == 15 && player.getAdena() == 72
						&& !warehouse.hasPendingTransfer() && player.getPendingClanWarehouse() == null && ClanWarehouseTransfer.settlePending(player),
						"Native withdrawal left a retired item, stale Adena cache or unresolved STORE guard.");
				}
			}
			check(player.getInventory().destroyItemByItemId(ItemProcessType.NONE, Inventory.ADENA_ID, 72, player, null) != null
				&& player.getInventory().getAdenaInstance() == null, "Could not prepare the isolated empty-wallet case.");
			final Item wallet = player.getInventory().getAdenaForCountTransaction();
			final var toEmptyWallet = new Request(CLAN, ACTOR, warehouseAdenaId, 15, false, new UUID(21, 63), Long.MAX_VALUE);
			synchronized (player)
			{
				synchronized (warehouse)
				{
					check(ClanWarehouseTransfer.transferLocked(PROFILE, player, clan, warehouse, toEmptyWallet, 0, List.of(), () -> true) == TransferOutcome.APPLIED
						&& player.getInventory().getAdenaInstance() == wallet && player.getAdena() == 15
						&& player.getInventory().getAllItemsByItemId(Inventory.ADENA_ID).size() == 1 && warehouse.getItemByObjectId(warehouseAdenaId) == null,
						"Empty-wallet withdrawal did not publish the one native Adena object.");
				}
			}
		}
		finally { player.stopAllTasks(); }
		System.out.println("PASS 021 native warehouse containers, protected reserve, stock exclusion and STORE settlement");
	}

	private static void reject(ClanWarehouseTransfer.Move move, String reason) throws SQLException
	{
		try (var connection = DatabaseFactory.getConnection())
		{
			connection.setAutoCommit(false);
			try
			{
				move.stage(connection);
				throw new AssertionError("A stale warehouse transfer was staged.");
			}
			catch (SQLException rejected)
			{
				check(reason.equals(rejected.getMessage()), "Warehouse transfer was rejected for the wrong reason.");
			}
			finally { connection.rollback(); }
		}
	}

	private static void commit(ClanWarehouseTransfer.Move move) throws Exception
	{
		try (var connection = DatabaseFactory.getConnection())
		{
			connection.setAutoCommit(false);
			move.stage(connection);
			connection.commit();
		}
		check(read(move) == ClanWarehouseTransfer.Outcome.APPLIED, "Committed warehouse receipt and objects did not match on reconnect.");
		move.publish();
	}

	private static ClanWarehouseTransfer.Outcome read(ClanWarehouseTransfer.Move move) throws SQLException
	{
		try (var connection = DatabaseFactory.getConnection())
		{
			connection.setAutoCommit(false);
			final var outcome = move.readback(connection);
			connection.rollback();
			return outcome;
		}
	}

	private static Item item(int objectId, int itemId, int ownerId, ItemLocation location, long count)
	{
		final Item item = new Item(objectId, ItemData.getInstance().getTemplate(itemId));
		item.setOwnerId(ownerId);
		item.setItemLocation(location, 0, false);
		item.setCount(count);
		return item;
	}

	private static void check(boolean condition, String message)
	{
		if (!condition) { throw new AssertionError(message); }
	}
}
