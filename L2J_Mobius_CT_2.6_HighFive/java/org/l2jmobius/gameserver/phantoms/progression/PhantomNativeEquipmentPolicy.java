/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.progression;

import java.util.Comparator;
import java.util.List;

import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.item.Armor;
import org.l2jmobius.gameserver.model.item.ItemTemplate;
import org.l2jmobius.gameserver.model.item.Weapon;
import org.l2jmobius.gameserver.model.item.enums.BodyPart;
import org.l2jmobius.gameserver.model.item.instance.Item;
import org.l2jmobius.gameserver.model.item.type.ArmorType;
import org.l2jmobius.gameserver.model.stats.Stat;

/** Read-only loadout preference. Native Progression equips; native Commerce buys. */
public final class PhantomNativeEquipmentPolicy
{
	private static final List<BodyPart> ARMOR_SLOTS = List.of(BodyPart.HEAD, BodyPart.CHEST, BodyPart.LEGS, BodyPart.FULL_ARMOR, BodyPart.GLOVES, BodyPart.FEET);
	private PhantomNativeEquipmentPolicy() { }

	public static boolean compatible(Player player, ItemTemplate item)
	{
		if (player == null || item == null || item.isQuestItem() || item.getCrystalType().getLevel() > Math.max(0, player.getExpertiseLevel()) || !item.checkCondition(player, player, false)) { return false; }
		if (item instanceof Weapon weapon)
		{
			if (weapon.isMagicWeapon() != player.isMageClass()) { return false; }
			final var current = player.getActiveWeaponItem();
			return player.isMageClass() || current == null || current == player.getFistsWeaponItem() || current.isMagicWeapon() || current.getItemType() == weapon.getItemType();
		}
		if (!(item instanceof Armor armor) || !ARMOR_SLOTS.contains(item.getBodyPart())) { return false; }
		return armor.getItemType() == ArmorType.NONE || (player.isMageClass() ? armor.getItemType() == ArmorType.MAGIC : armor.getItemType() == ArmorType.LIGHT || armor.getItemType() == ArmorType.HEAVY);
	}

	public static double power(Player player, ItemTemplate item)
	{
		if (item == null) { return 0; }
		final Stat stat = item instanceof Weapon ? player.isMageClass() ? Stat.MAGIC_ATTACK : Stat.POWER_ATTACK : Stat.POWER_DEFENCE;
		return item.getStatFuncs(null, player).stream().filter(function -> function.getStat() == stat && function.getApplyCond() == null).mapToDouble(function -> Math.max(0, function.getValue())).max().orElse(0);
	}

	public static boolean upgrade(Player player, ItemTemplate candidate)
	{
		if (!compatible(player, candidate)) { return false; }
		if (candidate instanceof Weapon)
		{
			final var current = player.getActiveWeaponItem();
			return current == null || current == player.getFistsWeaponItem() || !compatible(player, current) || candidate.getCrystalType().getLevel() >= current.getCrystalType().getLevel() && power(player, candidate) > power(player, current);
		}
		final var replaced = player.getInventory().getItems().stream().filter(Item::isEquipped).filter(item -> overlaps(candidate.getBodyPart(), item.getTemplate().getBodyPart())).toList();
		return replaced.isEmpty() || replaced.stream().anyMatch(item -> !compatible(player, item.getTemplate()))
			|| candidate.getCrystalType().getLevel() >= replaced.stream().mapToInt(item -> item.getTemplate().getCrystalType().getLevel()).max().orElse(0)
				&& power(player, candidate) > replaced.stream().mapToDouble(item -> power(player, item.getTemplate())).sum();
	}

	private static boolean overlaps(BodyPart first, BodyPart second)
	{
		return first == second || first == BodyPart.FULL_ARMOR && (second == BodyPart.CHEST || second == BodyPart.LEGS)
			|| second == BodyPart.FULL_ARMOR && (first == BodyPart.CHEST || first == BodyPart.LEGS);
	}

	/** Highest legal grade first, then the native item's base combat value, with a stable tie break. */
	public static Comparator<ItemTemplate> preference(Player player)
	{
		return Comparator.comparingInt((ItemTemplate item) -> item instanceof Weapon ? 0 : 1)
			.thenComparing(Comparator.comparingInt((ItemTemplate item) -> item.getCrystalType().getLevel()).reversed())
			.thenComparing(Comparator.comparingDouble((ItemTemplate item) -> power(player, item)).reversed()).thenComparingInt(ItemTemplate::getId);
	}
}
