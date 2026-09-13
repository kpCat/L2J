/*
 * This file is part of the L2J Mobius project.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.l2jmobius.gameserver.qol;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.l2jmobius.gameserver.config.PlayerConfig;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.model.item.instance.Item;
import org.l2jmobius.gameserver.model.item.type.CrystalType;
import org.l2jmobius.gameserver.model.itemcontainer.PlayerInventory;
import org.l2jmobius.gameserver.model.skill.CommonSkill;
import org.l2jmobius.gameserver.network.PacketLogger;
import org.l2jmobius.gameserver.network.SystemMessageId;
import org.l2jmobius.gameserver.network.serverpackets.ActionFailed;
import org.l2jmobius.gameserver.network.serverpackets.InventoryUpdate;
import org.l2jmobius.gameserver.network.serverpackets.SystemMessage;

/**
 * Single mutation owner shared by native and Community Board crystallization.
 */
public final class CrystallizationService
{
	private final ConcurrentMap<Integer, Object> _inFlight = new ConcurrentHashMap<>();

	private CrystallizationService()
	{
	}

	public Inspection inspect(Player player, int objectId, long requestedCount)
	{
		return inspect(player, objectId, requestedCount, false, false);
	}

	public Result crystallize(Player player, int objectId, long requestedCount)
	{
		return crystallize(player, objectId, requestedCount, null);
	}

	public Result crystallize(Player player, int objectId, long requestedCount, Preview expected)
	{
		if ((player == null) || (requestedCount < 1))
		{
			return new Result(Status.INVALID_COUNT, null);
		}
		if (player.isInStoreMode() || player.isInCrystallize())
		{
			return fail(player, Status.BUSY, null);
		}

		final Object claim = new Object();
		if (_inFlight.putIfAbsent(player.getObjectId(), claim) != null)
		{
			return fail(player, Status.BUSY, null);
		}

		boolean compatibilityFlagSet = false;
		try
		{
			if (player.isInStoreMode() || player.isInCrystallize())
			{
				return fail(player, Status.BUSY, null);
			}
			player.setInCrystallize(true);
			compatibilityFlagSet = true;
			if (player.getSkillLevel(CommonSkill.CRYSTALLIZE.getId()) <= 0)
			{
				return fail(player, Status.SKILL_MISSING, null);
			}

			final Item item = player.getInventory().getItemByObjectId(objectId);
			if (item == null)
			{
				return fail(player, Status.ITEM_NOT_FOUND, null);
			}

			synchronized (item)
			{
				if ((expected != null) && (item.getCount() != expected.count()))
				{
					return fail(player, Status.STALE_PREVIEW, null);
				}
				final Inspection inspection = inspect(player, objectId, requestedCount, expected == null, true);
				if (!inspection.eligible())
				{
					return fail(player, inspection.status(), inspection.preview(), item);
				}
				final Preview preview = inspection.preview();
				if ((expected != null) && !expected.equals(preview))
				{
					return fail(player, Status.STALE_PREVIEW, preview);
				}
				return mutate(player, item, preview);
			}
		}
		finally
		{
			if (compatibilityFlagSet)
			{
				player.setInCrystallize(false);
			}
			_inFlight.remove(player.getObjectId(), claim);
		}
	}

	private Inspection inspect(Player player, int objectId, long requestedCount, boolean clampCount, boolean ignoreCompatibilityFlag)
	{
		if ((player == null) || (requestedCount < 1))
		{
			return new Inspection(Status.INVALID_COUNT, null);
		}
		if (player.isInStoreMode() || (!ignoreCompatibilityFlag && player.isInCrystallize()))
		{
			return new Inspection(Status.BUSY, null);
		}

		final int skillLevel = player.getSkillLevel(CommonSkill.CRYSTALLIZE.getId());
		if (skillLevel <= 0)
		{
			return new Inspection(Status.SKILL_MISSING, null);
		}

		final PlayerInventory inventory = player.getInventory();
		final Item item = inventory == null ? null : inventory.getItemByObjectId(objectId);
		if (item == null)
		{
			return new Inspection(Status.ITEM_NOT_FOUND, null);
		}
		if (item.isHeroItem() || (!PlayerConfig.ALT_ALLOW_AUGMENT_DESTROY && item.isAugmented()))
		{
			return new Inspection(Status.ITEM_RESTRICTED, null);
		}
		if (item.isShadowItem() || item.isTimeLimitedItem())
		{
			return new Inspection(Status.ITEM_RESTRICTED_SILENT, null);
		}
		if (!item.getTemplate().isCrystallizable() || (item.getTemplate().getCrystalCount() <= 0) || (item.getTemplate().getCrystalType() == CrystalType.NONE))
		{
			return new Inspection(Status.NOT_CRYSTALLIZABLE, null);
		}
		if (!inventory.canManipulateWithItemId(item.getId()))
		{
			return new Inspection(Status.NOT_OWNED, null);
		}

		final CrystalType grade = item.getTemplate().getCrystalTypePlus();
		if (skillLevel < grade.getLevel())
		{
			return new Inspection(Status.SKILL_TOO_LOW, null);
		}

		final long count;
		if (requestedCount > item.getCount())
		{
			if (!clampCount)
			{
				return new Inspection(Status.STALE_PREVIEW, null);
			}
			count = item.getCount();
		}
		else
		{
			count = requestedCount;
		}
		if (count < 1)
		{
			return new Inspection(Status.ITEM_NOT_FOUND, null);
		}

		final Preview preview = new Preview(player.getObjectId(), item.getObjectId(), item.getId(), item.getName(), count, item.getEnchantLevel(), item.getTemplate().getCrystalType(), item.getTemplate().getCrystalItemId(), item.getCrystalCount());
		return new Inspection(Status.ELIGIBLE, preview);
	}

	private Result mutate(Player player, Item item, Preview preview)
	{
		SystemMessage message;
		if (item.isEquipped())
		{
			final InventoryUpdate unequipUpdate = new InventoryUpdate();
			for (Item equippedItem : player.getInventory().unEquipItemInSlotAndRecord(item.getLocationSlot()))
			{
				unequipUpdate.addModifiedItem(equippedItem);
			}
			player.sendPacket(unequipUpdate);

			if (item.isEnchanted())
			{
				message = new SystemMessage(SystemMessageId.THE_EQUIPMENT_S1_S2_HAS_BEEN_REMOVED);
				message.addInt(item.getEnchantLevel());
				message.addItemName(item);
			}
			else
			{
				message = new SystemMessage(SystemMessageId.S1_HAS_BEEN_DISARMED);
				message.addItemName(item);
			}
			player.sendPacket(message);
		}

		final Item removedItem = player.getInventory().destroyItem(ItemProcessType.DESTROY, preview.itemObjectId(), preview.count(), player, null);
		if (removedItem == null)
		{
			return fail(player, Status.DESTROY_FAILED, preview);
		}
		final InventoryUpdate destroyUpdate = new InventoryUpdate();
		destroyUpdate.addRemovedItem(removedItem);
		player.sendPacket(destroyUpdate);

		final Item createdItem = player.getInventory().addItem(ItemProcessType.COMPENSATE, preview.crystalItemId(), preview.crystalCount(), player, player);
		if (createdItem == null)
		{
			return new Result(Status.CRYSTAL_CREDIT_FAILED, preview);
		}

		message = new SystemMessage(SystemMessageId.S1_HAS_BEEN_CRYSTALLIZED);
		message.addItemName(removedItem);
		player.sendPacket(message);
		message = new SystemMessage(SystemMessageId.YOU_HAVE_EARNED_S2_S1_S);
		message.addItemName(createdItem);
		message.addLong(preview.crystalCount());
		player.sendPacket(message);
		player.broadcastUserInfo();
		World.getInstance().removeObject(removedItem);
		return new Result(Status.SUCCESS, preview);
	}

	private static Result fail(Player player, Status status, Preview preview)
	{
		return fail(player, status, preview, null);
	}

	private static Result fail(Player player, Status status, Preview preview, Item item)
	{
		switch (status)
		{
			case BUSY:
			{
				player.sendPacket(SystemMessageId.WHILE_OPERATING_A_PRIVATE_STORE_OR_WORKSHOP_YOU_CANNOT_DISCARD_DESTROY_OR_TRADE_AN_ITEM);
				break;
			}
			case SKILL_MISSING:
			case SKILL_TOO_LOW:
			{
				player.sendPacket(SystemMessageId.YOU_MAY_NOT_CRYSTALLIZE_THIS_ITEM_YOUR_CRYSTALLIZATION_SKILL_LEVEL_IS_TOO_LOW);
				player.sendPacket(ActionFailed.STATIC_PACKET);
				break;
			}
			case ITEM_NOT_FOUND:
			case ITEM_RESTRICTED:
			case DESTROY_FAILED:
			{
				player.sendPacket(ActionFailed.STATIC_PACKET);
				break;
			}
			case NOT_CRYSTALLIZABLE:
			{
				PacketLogger.warning(player.getName() + " (" + player.getObjectId() + ") tried to crystallize " + (item == null ? "a non-crystallizable item" : item.getId()) + ".");
				break;
			}
			case NOT_OWNED:
			{
				player.sendMessage("You cannot use this item.");
				break;
			}
			default:
			{
				break;
			}
		}
		return new Result(status, preview);
	}

	public enum Status
	{
		ELIGIBLE,
		SUCCESS,
		INVALID_COUNT,
		BUSY,
		SKILL_MISSING,
		SKILL_TOO_LOW,
		ITEM_NOT_FOUND,
		ITEM_RESTRICTED,
		ITEM_RESTRICTED_SILENT,
		NOT_CRYSTALLIZABLE,
		NOT_OWNED,
		STALE_PREVIEW,
		DESTROY_FAILED,
		CRYSTAL_CREDIT_FAILED
	}

	public record Preview(int playerObjectId, int itemObjectId, int itemId, String itemName, long count, int enchantLevel, CrystalType grade, int crystalItemId, int crystalCount)
	{
	}

	public record Inspection(Status status, Preview preview)
	{
		public boolean eligible()
		{
			return status == Status.ELIGIBLE;
		}
	}

	public record Result(Status status, Preview preview)
	{
		public boolean success()
		{
			return status == Status.SUCCESS;
		}
	}

	public static CrystallizationService getInstance()
	{
		return SingletonHolder.INSTANCE;
	}

	private static class SingletonHolder
	{
		private static final CrystallizationService INSTANCE = new CrystallizationService();
	}
}
