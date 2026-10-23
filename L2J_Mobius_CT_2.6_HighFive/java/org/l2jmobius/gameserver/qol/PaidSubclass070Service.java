/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.qol;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.enums.creature.Race;
import org.l2jmobius.gameserver.model.item.enums.ItemLocation;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.model.item.instance.Item;
import org.l2jmobius.gameserver.model.script.QuestState;
import org.l2jmobius.gameserver.network.serverpackets.InventoryUpdate;

/** Paid replacement for bounded acquisition steps; real quests retain NPC progression, costs and completion. */
public final class PaidSubclass070Service
{
	private static final Logger LOGGER = Logger.getLogger(PaidSubclass070Service.class.getName());
	private static final String KIND = "SUBCLASS_GATEWAY";
	private static final String FATES_WHISPER = "Q00234_FatesWhisper";
	private static final String MIMIRS_ELIXIR = "Q00235_MimirsElixir";

	private PaidSubclass070Service()
	{
	}

	public static long purchase(Player player, String request, String revision) throws Exception
	{
		if (!eligible(player) || (completed(player, FATES_WHISPER) && completed(player, MIMIRS_ELIXIR)))
		{
			throw new IllegalArgumentException("Нужен обычный персонаж основного класса уровня75+, не Kamael, с незавершённым доступом к подклассу.");
		}
		return Economy070Shop.getInstance().paidEntitlement(player, "subclass.gateway", KIND, request, revision);
	}

	public static boolean hasEntitlement(Player player) throws SQLException
	{
		if (player == null) { return false; }
		try (var connection = DatabaseFactory.getConnection())
		{
			Economy070Shop.verifySchema(connection);
			try (var query = connection.prepareStatement("SELECT 1 FROM economy070_orders WHERE account_name=? AND char_id=? AND kind=? LIMIT 1"))
			{
				query.setString(1, player.getAccountName()); query.setInt(2, player.getObjectId()); query.setString(3, KIND);
				try (var rows = query.executeQuery()) { return rows.next(); }
			}
		}
	}

	public static void supplyQuestMaterials(Player player, QuestState state)
	{
		if (!eligible(player) || (state == null) || (state.getPlayer() != player) || !state.isStarted()
			|| !Set.of(FATES_WHISPER, MIMIRS_ELIXIR).contains(state.getQuestName())) { return; }
		synchronized (player)
		{
			try
			{
				if (!hasEntitlement(player)) { return; }
				if (FATES_WHISPER.equals(state.getQuestName()))
				{
					switch (state.getMemoState())
					{
						case 1 -> missing(player, 4666, 1);
						case 2 -> { missing(player, 4667, 1); missing(player, 4668, 1); missing(player, 4669, 1); }
						case 8 ->
						{
							// Native Kaspar supports thirty bloody fabrics even when white fabrics already exist.
							if (count(player, 4673) == 0) { missing(player, 14361, 30); }
						}
					}
				}
				else if (state.isMemoState(3))
				{
					missing(player, 6322, 1);
				}
				else if (state.isMemoState(6) && (count(player, 5905) > 0))
				{
					missing(player, 6318, 1);
				}
			}
			catch (Exception failure)
			{
				LOGGER.warning("Paid subclass entitlement could not be verified: " + failure.getMessage());
				player.sendMessage("Проверка оплаченного доступа к подклассу сейчас недоступна. Повторите диалог позже.");
			}
		}
	}

	private static boolean eligible(Player player)
	{
		return Economy070Shop.eligible(player) && (player.getLevel() >= 75) && !player.isSubClassActive() && (player.getRace() != Race.KAMAEL);
	}

	private static boolean completed(Player player, String quest)
	{
		final var state = player.getQuestState(quest);
		return (state != null) && state.isCompleted();
	}

	private static long count(Player player, int itemId)
	{
		return player.getInventory().getInventoryItemCount(itemId, -1);
	}

	private static void missing(Player player, int itemId, long required) throws Exception
	{
		// Keep stack selection, durable publication and native insertion in the inventory's existing add lock.
		synchronized (player.getInventory())
		{
			if (count(player, itemId) >= required) { return; }
			final String request = UUID.nameUUIDFromBytes(("subclass-material070:" + player.getObjectId() + ':' + itemId).getBytes(StandardCharsets.UTF_8)).toString();
			try (var connection = DatabaseFactory.getConnection(); var query = connection.prepareStatement("SELECT request_id FROM economy070_orders WHERE request_id=?"))
			{
				query.setString(1, request);
				try (var rows = query.executeQuery()) { if (rows.next()) { return; } }
			}
			Item stack = player.getInventory().getItemByItemId(itemId);
			final boolean detached = stack == null;
			if (stack == null)
			{
				stack = new Item(itemId);
				stack.setCount(0); stack.setOwnerId(player.getObjectId()); stack.setItemLocation(ItemLocation.INVENTORY);
				// Quest.hasQuestItems checks presence, so an unpaid/uncertain zero stack must never enter inventory.
			}
			final Item material = stack;
			synchronized (material)
			{
				final long missing = required - material.getCount();
				if (missing <= 0) { return; }
				final boolean newlyVisible = material.getCount() == 0;
				try
				{
					material.transactCount(missing, connection ->
					{
						Economy070Shop.verifySchema(connection);
						try (var query = connection.prepareStatement("SELECT request_id FROM economy070_orders WHERE request_id=?"))
						{
							query.setString(1, request);
							try (var rows = query.executeQuery()) { if (rows.next()) { throw new AlreadySupplied(); } }
						}
						try (var insert = connection.prepareStatement("INSERT INTO economy070_orders(request_id,account_name,char_id,kind,sku,epoch,amount,revision,item_object_id,result_value,created_utc) VALUES(?,?,?,'SUBCLASS_MATERIAL',?,?,?,?,?,?,?)"))
						{
							insert.setString(1, request); insert.setString(2, player.getAccountName()); insert.setInt(3, player.getObjectId());
							insert.setInt(4, itemId); insert.setLong(5, player.getObjectId()); insert.setLong(6, missing);
							insert.setString(7, Economy070Pricing.getInstance().revision()); insert.setInt(8, material.getObjectId());
							insert.setLong(9, required); insert.setLong(10, System.currentTimeMillis()); insert.executeUpdate();
						}
						return required;
					});
					final Item visible;
					if (detached)
					{
						World.getInstance().addObject(material);
						visible = player.getInventory().addItem(ItemProcessType.RESTORE, material, player, null);
					}
					else
					{
						// The native existing-stack route refreshes weight without changing the committed amount.
						visible = player.getInventory().addItem(ItemProcessType.RESTORE, itemId, 0, player, null);
					}
					final var update = new InventoryUpdate();
					if (newlyVisible && (visible == material)) { visible.setLastChange(Item.ADDED); update.addNewItem(visible); }
					else { update.addModifiedItem(visible); }
					player.sendInventoryUpdate(update);
				}
				catch (AlreadySupplied ignored)
				{
					// Lost/sold/abandoned paid materials are not minted again; original quest acquisition remains available.
				}
			}
		}
	}

	private static final class AlreadySupplied extends Exception
	{
		private static final long serialVersionUID = 1L;
	}
}
