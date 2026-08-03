/*
 * Copyright (c) 2013 L2jMobius
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.l2jmobius.gameserver.services;

import java.util.Objects;
import java.util.logging.Logger;

import org.l2jmobius.gameserver.config.GeneralConfig;
import org.l2jmobius.gameserver.config.PlayerConfig;
import org.l2jmobius.gameserver.data.xml.EnchantItemData;
import org.l2jmobius.gameserver.managers.PunishmentManager;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.item.ItemTemplate;
import org.l2jmobius.gameserver.model.item.enchant.EnchantResultType;
import org.l2jmobius.gameserver.model.item.enchant.EnchantScroll;
import org.l2jmobius.gameserver.model.item.enchant.EnchantSupportItem;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.model.item.instance.Item;
import org.l2jmobius.gameserver.model.skill.CommonSkill;
import org.l2jmobius.gameserver.model.skill.Skill;
import org.l2jmobius.gameserver.network.SystemMessageId;
import org.l2jmobius.gameserver.network.serverpackets.EnchantResult;
import org.l2jmobius.gameserver.network.serverpackets.InventoryUpdate;
import org.l2jmobius.gameserver.network.serverpackets.MagicSkillUse;
import org.l2jmobius.gameserver.network.serverpackets.SystemMessage;

/** One canonical, client-packet-independent active enchant mutation service. */
public final class EnchantItemService
{
	private static final Logger LOGGER_ENCHANT = Logger.getLogger("enchant.items");

	private EnchantItemService()
	{
	}

	public Outcome execute(Request request)
	{
		Objects.requireNonNull(request);
		final Player player = request.actor();
		if (player.isProcessingTransaction() || player.isInStoreMode())
		{
			player.sendPacket(SystemMessageId.YOU_CANNOT_ENCHANT_WHILE_OPERATING_A_PRIVATE_STORE_OR_PRIVATE_WORKSHOP);
			player.setActiveEnchantItemId(Player.ID_NONE);
			return observe(request, Outcome.ERROR, null, 0, 0, 0);
		}
		final Item item = player.getInventory().getItemByObjectId(request.targetObjectId());
		final Item scroll = player.getInventory().getItemByObjectId(request.scrollObjectId());
		final Item support = request.supportObjectId() == 0 ? null : player.getInventory().getItemByObjectId(request.supportObjectId());
		if ((item == null) || (scroll == null) || ((request.supportObjectId() != 0) && (support == null)))
		{
			cancel(player);
			return observe(request, Outcome.ERROR, item, 0, 0, 0);
		}

		final EnchantScroll scrollTemplate = EnchantItemData.getInstance().getEnchantScroll(scroll);
		final EnchantSupportItem supportTemplate = support == null ? null : EnchantItemData.getInstance().getSupportItem(support);
		if ((item.getOwnerId() != player.getObjectId()) || (scroll.getOwnerId() != player.getObjectId()) || ((support != null) && (support.getOwnerId() != player.getObjectId())) || (item == scroll) || (item == support) || (scroll == support) || !item.isEnchantable() || (scrollTemplate == null) || ((support != null) && (supportTemplate == null)) || !scrollTemplate.isValid(item, supportTemplate) || (PlayerConfig.DISABLE_OVER_ENCHANTING && (item.getEnchantLevel() == scrollTemplate.getMaxEnchantLevel())))
		{
			player.sendPacket(SystemMessageId.INAPPROPRIATE_ENCHANT_CONDITIONS);
			player.setActiveEnchantItemId(Player.ID_NONE);
			player.sendPacket(new EnchantResult(2, 0, 0));
			return observe(request, Outcome.ERROR, item, item.getEnchantLevel(), 0, 0);
		}

		final int beforeEnchant = item.getEnchantLevel();
		final Item destroyedScroll = player.getInventory().destroyItem(ItemProcessType.FEE, scroll.getObjectId(), 1, player, item);
		if (destroyedScroll == null)
		{
			invalidResource(player, item, "scroll", request.punishInvalidResource());
			return observe(request, Outcome.ERROR, item, beforeEnchant, 0, 0);
		}
		final InventoryUpdate update = new InventoryUpdate();
		addConsumed(update, destroyedScroll);
		if (support != null)
		{
			final Item destroyedSupport = player.getInventory().destroyItem(ItemProcessType.FEE, support.getObjectId(), 1, player, item);
			if (destroyedSupport == null)
			{
				invalidResource(player, item, "support item", request.punishInvalidResource());
				return observe(request, Outcome.ERROR, item, beforeEnchant, 0, 0);
			}
			addConsumed(update, destroyedSupport);
		}

		Outcome outcome = Outcome.ERROR;
		int crystalId = 0;
		int crystalCount = 0;
		synchronized (item)
		{
			if ((item.getOwnerId() != player.getObjectId()) || !item.isEnchantable())
			{
				player.sendPacket(SystemMessageId.INAPPROPRIATE_ENCHANT_CONDITIONS);
				player.setActiveEnchantItemId(Player.ID_NONE);
				player.sendPacket(new EnchantResult(2, 0, 0));
				return observe(request, Outcome.ERROR, item, beforeEnchant, 0, 0);
			}

			final EnchantResultType resultType = scrollTemplate.calculateSuccess(player, item, supportTemplate);
			if (resultType == EnchantResultType.ERROR)
			{
				player.sendPacket(SystemMessageId.INAPPROPRIATE_ENCHANT_CONDITIONS);
				player.setActiveEnchantItemId(Player.ID_NONE);
				player.sendPacket(new EnchantResult(2, 0, 0));
			}
			else if (resultType == EnchantResultType.SUCCESS)
			{
				applySuccess(player, item, scrollTemplate, update);
				outcome = Outcome.SUCCESS;
			}
			else if (scrollTemplate.isSafe())
			{
				player.sendPacket(SystemMessageId.ENCHANT_FAILED_THE_ENCHANT_LEVEL_FOR_THE_CORRESPONDING_ITEM_WILL_BE_EXACTLY_RETAINED);
				player.sendPacket(new EnchantResult(5, 0, 0));
				outcome = Outcome.SAFE_FAILURE;
			}
			else
			{
				unequipOnFailure(player, item, update);
				if (scrollTemplate.isBlessed())
				{
					player.sendPacket(SystemMessageId.THE_BLESSED_ENCHANT_FAILED_THE_ENCHANT_VALUE_OF_THE_ITEM_BECAME_0);
					item.setEnchantLevel(0);
					update.addModifiedItem(item);
					item.updateDatabase();
					player.sendPacket(new EnchantResult(3, 0, 0));
					outcome = Outcome.BLESSED_RESET;
				}
				else
				{
					final Item destroyedItem = player.getInventory().destroyItem(ItemProcessType.DESTROY, item, player, null);
					if (destroyedItem == null)
					{
						if (request.punishInvalidResource())
						{
							PunishmentManager.handleIllegalPlayerAction(player, "Unable to delete item on enchant failure from " + player + ", possible cheater !", GeneralConfig.DEFAULT_PUNISH);
						}
						player.setActiveEnchantItemId(Player.ID_NONE);
						player.sendPacket(new EnchantResult(2, 0, 0));
						logUnableToDestroy(player, item, scroll, support);
						return observe(request, Outcome.ERROR, item, beforeEnchant, 0, 0);
					}
					update.addRemovedItem(destroyedItem);
					crystalId = item.getTemplate().getCrystalItemId();
					if ((crystalId != 0) && item.getTemplate().isCrystallizable())
					{
						crystalCount = Math.max(1, item.getCrystalCount() - ((item.getTemplate().getCrystalCount() + 1) / 2));
						final Item crystals = player.getInventory().addItem(ItemProcessType.COMPENSATE, crystalId, crystalCount, player, item);
						final SystemMessage message = new SystemMessage(SystemMessageId.YOU_HAVE_EARNED_S2_S1_S);
						message.addItemName(crystalId);
						message.addLong(crystalCount);
						player.sendPacket(message);
						if (crystals.getLastChange() == Item.MODIFIED)
						{
							update.addModifiedItem(crystals);
						}
						else
						{
							update.addNewItem(crystals);
						}
						player.sendPacket(new EnchantResult(1, crystalId, crystalCount));
					}
					else
					{
						player.sendPacket(new EnchantResult(4, 0, 0));
					}
					outcome = Outcome.DESTROYED_WITH_CRYSTALS;
				}
			}
			if (outcome != Outcome.ERROR)
			{
				log(player, item, scroll, support, outcome);
			}
			player.sendInventoryUpdate(update);
			player.broadcastUserInfo();
			player.setActiveEnchantItemId(Player.ID_NONE);
		}
		return observe(request, outcome, item, beforeEnchant, crystalId, crystalCount);
	}

	private static void applySuccess(Player player, Item item, EnchantScroll scroll, InventoryUpdate update)
	{
		final ItemTemplate template = item.getTemplate();
		if (scroll.getChance(player, item) > 0)
		{
			item.setEnchantLevel(item.getEnchantLevel() + 1);
			update.addModifiedItem(item);
			item.updateDatabase();
		}
		player.sendPacket(new EnchantResult(0, 0, 0));
		final int minAnnounce = item.isArmor() ? 6 : 7;
		final int maxAnnounce = item.isArmor() ? 0 : 15;
		if ((item.getEnchantLevel() == minAnnounce) || (item.getEnchantLevel() == maxAnnounce))
		{
			final SystemMessage message = new SystemMessage(SystemMessageId.C1_HAS_SUCCESSFULLY_ENCHANTED_A_S2_S3);
			message.addString(player.getName());
			message.addInt(item.getEnchantLevel());
			message.addItemName(item);
			player.broadcastPacket(message);
			final Skill skill = CommonSkill.FIREWORK.getSkill();
			if (skill != null)
			{
				player.broadcastSkillPacket(new MagicSkillUse(player, player, skill.getId(), skill.getLevel(), skill.getHitTime(), skill.getReuseDelay()), player);
			}
		}
		if (item.isArmor() && (item.getEnchantLevel() == 4) && item.isEquipped())
		{
			final Skill skill = template.getEnchant4Skill();
			if (skill != null)
			{
				player.addSkill(skill, false);
				player.sendSkillList();
			}
		}
		player.broadcastUserInfo();
	}

	private static void unequipOnFailure(Player player, Item item, InventoryUpdate update)
	{
		if (!item.isEquipped())
		{
			return;
		}
		final SystemMessage message;
		if (item.isEnchanted())
		{
			message = new SystemMessage(SystemMessageId.THE_EQUIPMENT_S1_S2_HAS_BEEN_REMOVED);
			message.addInt(item.getEnchantLevel());
		}
		else
		{
			message = new SystemMessage(SystemMessageId.S1_HAS_BEEN_DISARMED);
		}
		message.addItemName(item);
		player.sendPacket(message);
		for (Item equipped : player.getInventory().unEquipItemInSlotAndRecord(item.getLocationSlot()))
		{
			update.addModifiedItem(equipped);
		}
	}

	private static void addConsumed(InventoryUpdate update, Item item)
	{
		if (item.getCount() > 0)
		{
			update.addModifiedItem(item);
		}
		else
		{
			update.addRemovedItem(item);
		}
	}

	private static void cancel(Player player)
	{
		player.setActiveEnchantItemId(Player.ID_NONE);
		player.sendPacket(SystemMessageId.YOU_HAVE_CANCELLED_THE_ENCHANTING_PROCESS);
		player.sendPacket(new EnchantResult(2, 0, 0));
	}

	private static void invalidResource(Player player, Item item, String resource, boolean punish)
	{
		player.sendPacket(SystemMessageId.INCORRECT_ITEM_COUNT_2);
		if (punish)
		{
			PunishmentManager.handleIllegalPlayerAction(player, player + " tried to enchant with a " + resource + " he doesn't have", GeneralConfig.DEFAULT_PUNISH);
		}
		player.setActiveEnchantItemId(Player.ID_NONE);
		player.sendPacket(new EnchantResult(2, 0, 0));
	}

	private static void log(Player player, Item item, Item scroll, Item support, Outcome outcome)
	{
		if (GeneralConfig.LOG_ITEM_ENCHANTS)
		{
			final String prefix = switch (outcome)
			{
				case SUCCESS -> "Success";
				case SAFE_FAILURE -> "Safe Fail";
				case BLESSED_RESET -> "Blessed Fail";
				case DESTROYED_WITH_CRYSTALS -> "Fail";
				case ERROR -> throw new IllegalArgumentException("Error enchant outcomes are not logged here.");
			};
			LOGGER_ENCHANT.info(message(prefix, player, item, scroll, support));
		}
	}

	private static void logUnableToDestroy(Player player, Item item, Item scroll, Item support)
	{
		if (GeneralConfig.LOG_ITEM_ENCHANTS)
		{
			LOGGER_ENCHANT.info(message("Unable to destroy", player, item, scroll, support));
		}
	}

	private static String message(String prefix, Player player, Item item, Item scroll, Item support)
	{
		return prefix + ", Character:" + player.getName() + " [" + player.getObjectId() + "] Account:" + player.getAccountName() + " IP:" + player.getIPAddress() + ", " + (item.isEnchanted() ? "+" + item.getEnchantLevel() + " " : "") + item.getName() + "(" + item.getCount() + ") [" + item.getObjectId() + "], " + scroll.getName() + "(" + scroll.getCount() + ") [" + scroll.getObjectId() + "]" + (support == null ? "" : ", " + support.getName() + "(" + support.getCount() + ") [" + support.getObjectId() + "]");
	}

	private static Outcome observe(Request request, Outcome outcome, Item item, int beforeEnchant, int crystalId, int crystalCount)
	{
		try
		{
			request.observer().onResult(new Event(outcome, request.actor().getObjectId(), request.targetObjectId(), request.scrollObjectId(), request.supportObjectId(), item == null ? 0 : item.getId(), beforeEnchant, item == null || outcome == Outcome.DESTROYED_WITH_CRYSTALS ? 0 : item.getEnchantLevel(), crystalId, crystalCount));
		}
		catch (RuntimeException exception)
		{
			LOGGER_ENCHANT.warning("Enchant observer rejected an immutable event: " + exception.getMessage());
		}
		return outcome;
	}

	public enum Outcome
	{
		ERROR,
		SUCCESS,
		SAFE_FAILURE,
		BLESSED_RESET,
		DESTROYED_WITH_CRYSTALS
	}

	@FunctionalInterface
	public interface Observer
	{
		Observer NONE = event ->
		{
		};

		void onResult(Event event);
	}

	public record Request(Player actor, int targetObjectId, int scrollObjectId, int supportObjectId, boolean punishInvalidResource, Observer observer)
	{
		public Request
		{
			Objects.requireNonNull(actor);
			observer = observer == null ? Observer.NONE : observer;
			if ((targetObjectId <= 0) || (scrollObjectId <= 0) || (supportObjectId < 0))
			{
				throw new IllegalArgumentException("Invalid canonical enchant request.");
			}
		}
	}

	public record Event(Outcome outcome, int actorObjectId, int targetObjectId, int scrollObjectId, int supportObjectId, int targetItemId, int beforeEnchantLevel, int afterEnchantLevel, int crystalItemId, int crystalCount)
	{
	}

	public static EnchantItemService getInstance()
	{
		return SingletonHolder.INSTANCE;
	}

	private static class SingletonHolder
	{
		private static final EnchantItemService INSTANCE = new EnchantItemService();
	}
}
