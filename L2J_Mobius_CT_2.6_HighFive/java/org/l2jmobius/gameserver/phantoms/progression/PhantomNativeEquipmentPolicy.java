/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.progression;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

import org.l2jmobius.gameserver.data.xml.ArmorSetData;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.conditions.Condition;
import org.l2jmobius.gameserver.model.conditions.ConditionLogicAnd;
import org.l2jmobius.gameserver.model.conditions.ConditionLogicOr;
import org.l2jmobius.gameserver.model.conditions.ConditionUsingItemType;
import org.l2jmobius.gameserver.model.item.Armor;
import org.l2jmobius.gameserver.model.item.ItemTemplate;
import org.l2jmobius.gameserver.model.item.Weapon;
import org.l2jmobius.gameserver.model.item.enums.BodyPart;
import org.l2jmobius.gameserver.model.item.instance.Item;
import org.l2jmobius.gameserver.model.item.type.ArmorType;
import org.l2jmobius.gameserver.model.item.type.CrystalType;
import org.l2jmobius.gameserver.model.item.type.EtcItemType;
import org.l2jmobius.gameserver.model.itemcontainer.Inventory;
import org.l2jmobius.gameserver.model.stats.Stat;
import org.l2jmobius.gameserver.model.stats.functions.FuncTemplate;

/** Read-only loadout preference. Native Progression equips; native Commerce buys. */
public final class PhantomNativeEquipmentPolicy
{
	private static final List<BodyPart> ARMOR_SLOTS = List.of(BodyPart.HEAD, BodyPart.CHEST, BodyPart.LEGS, BodyPart.FULL_ARMOR, BodyPart.GLOVES, BodyPart.FEET);
	private static final Set<Stat> MASTERY_STATS = Set.of(Stat.POWER_ATTACK, Stat.MAGIC_ATTACK, Stat.POWER_DEFENCE, Stat.MAGIC_DEFENCE, Stat.POWER_ATTACK_SPEED, Stat.MAGIC_ATTACK_SPEED, Stat.MAX_HP, Stat.MAX_MP, Stat.EVASION_RATE, Stat.CRITICAL_RATE);
	private PhantomNativeEquipmentPolicy() { }

	/** Progression intent is bounded by the expertise actually learned by this actor. */
	public static CrystalType targetGrade(int level, int expertiseLevel)
	{
		final CrystalType target = level >= 84 ? CrystalType.S84 : level >= 80 ? CrystalType.S80 : level >= 76 ? CrystalType.S : level >= 61 ? CrystalType.A : level >= 52 ? CrystalType.B : level >= 40 ? CrystalType.C : level >= 20 ? CrystalType.D : CrystalType.NONE;
		return CrystalType.values()[Math.max(0, Math.min(target.getLevel(), expertiseLevel))];
	}

	public static boolean hasGradeDebt(Player player)
	{
		if (player == null) { return false; }
		final int target = targetGrade(player.getLevel(), player.getExpertiseLevel()).getLevel();
		if (target == 0) { return false; }
		if (player.getActiveWeaponItem() == null || player.getActiveWeaponItem() == player.getFistsWeaponItem() || player.getActiveWeaponItem().getCrystalType().getLevel() < target) { return true; }
		for (int slot : List.of(Inventory.PAPERDOLL_HEAD, Inventory.PAPERDOLL_CHEST, Inventory.PAPERDOLL_GLOVES, Inventory.PAPERDOLL_FEET))
		{
			final var item = player.getInventory().getPaperdollItem(slot);
			if (item == null || item.getTemplate().getCrystalType().getLevel() < target) { return true; }
		}
		final var chest = player.getInventory().getPaperdollItem(Inventory.PAPERDOLL_CHEST);
		final var legs = player.getInventory().getPaperdollItem(Inventory.PAPERDOLL_LEGS);
		return chest.getTemplate().getBodyPart() != BodyPart.FULL_ARMOR && (legs == null || legs.getTemplate().getCrystalType().getLevel() < target);
	}

	public static boolean compatible(Player player, ItemTemplate item)
	{
		if (player == null || item == null || item.isQuestItem() || item.getCrystalType().getLevel() > targetGrade(player.getLevel(), player.getExpertiseLevel()).getLevel() || !item.checkCondition(player, player, false)) { return false; }
		if (item instanceof Weapon weapon)
		{
			if (weapon.isMagicWeapon() != player.isMageClass()) { return false; }
			if (affinity(player, item) > 0) { return true; }
			final var current = player.getActiveWeaponItem();
			return player.isMageClass() || current == null || current == player.getFistsWeaponItem() || current.isMagicWeapon() || current.getItemType() == weapon.getItemType();
		}
		if (!(item instanceof Armor armor) || !ARMOR_SLOTS.contains(item.getBodyPart())) { return false; }
		return armor.getItemType() == ArmorType.NONE || affinity(player, item) > 0 || (player.isMageClass() ? armor.getItemType() == ArmorType.MAGIC : armor.getItemType() == ArmorType.LIGHT || armor.getItemType() == ArmorType.HEAVY);
	}

	/** Positive native equipment-conditioned passive functions, without reading class names or item IDs. */
	public static double equipmentAffinity(List<FuncTemplate> functions, ItemTemplate candidate)
	{
		double result = 0;
		for (var function : functions)
		{
			if (!MASTERY_STATS.contains(function.getStat()) || function.getValue() <= 0) { continue; }
			final int attach = projected(function.getAttachCondition(), candidate), apply = projected(function.getApplyCondition(), candidate);
			if (attach >= 0 && apply >= 0 && (attach > 0 || apply > 0)) { result += 1 + Math.log1p(function.getValue()); }
		}
		return result;
	}

	// 0 = no condition, 1 = known matching equipment condition, -1 = mismatch or unsupported dynamic condition.
	private static int projected(Condition condition, ItemTemplate candidate)
	{
		if (condition == null) { return 0; }
		if (condition instanceof ConditionUsingItemType using) { return using.matchesItemType(candidate) ? 1 : -1; }
		if (condition instanceof ConditionLogicAnd all)
		{
			int result = 0;
			for (var child : all.conditions) { final int value = projected(child, candidate); if (value < 0) { return -1; } result = Math.max(result, value); }
			return result;
		}
		if (condition instanceof ConditionLogicOr any)
		{
			for (var child : any.conditions) { if (projected(child, candidate) > 0) { return 1; } }
		}
		return -1;
	}

	private static double affinity(Player player, ItemTemplate item)
	{
		return player.getAllSkills().stream().filter(skill -> skill.isPassive()).mapToDouble(skill -> equipmentAffinity(skill.getStatFunctionTemplates(), item)).sum();
	}

	private static double utility(Player player, ItemTemplate item)
	{
		// Preference weights are author policy; native combat calculation and skill/set application remain unchanged.
		return power(player, item) * (1 + Math.min(0.5, affinity(player, item) * 0.02) + (completedSet(player, item) ? 0.1 : 0));
	}

	private static boolean completedSet(Player player, ItemTemplate candidate)
	{
		if (!(candidate instanceof Armor)) { return false; }
		final int chest = projectedSlot(player, candidate, Inventory.PAPERDOLL_CHEST, BodyPart.CHEST);
		final var set = chest > 0 ? ArmorSetData.getInstance().getSet(chest) : null;
		return set != null && !set.getSkills().isEmpty() && set.containAll(chest,
			projectedSlot(player, candidate, Inventory.PAPERDOLL_LEGS, BodyPart.LEGS), projectedSlot(player, candidate, Inventory.PAPERDOLL_HEAD, BodyPart.HEAD),
			projectedSlot(player, candidate, Inventory.PAPERDOLL_GLOVES, BodyPart.GLOVES), projectedSlot(player, candidate, Inventory.PAPERDOLL_FEET, BodyPart.FEET));
	}

	private static int projectedSlot(Player player, ItemTemplate candidate, int slot, BodyPart body)
	{
		if (candidate.getBodyPart() == body || body == BodyPart.CHEST && candidate.getBodyPart() == BodyPart.FULL_ARMOR) { return candidate.getId(); }
		if (body == BodyPart.LEGS && candidate.getBodyPart() == BodyPart.FULL_ARMOR) { return 0; }
		final var current = player.getInventory().getPaperdollItem(slot);
		if (body == BodyPart.CHEST && candidate.getBodyPart() == BodyPart.LEGS && current != null && current.getTemplate().getBodyPart() == BodyPart.FULL_ARMOR) { return 0; }
		return current == null ? 0 : current.getId();
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
			final var current = player.getActiveWeaponInstance();
			final var offhand = candidate.getBodyPart() == BodyPart.LR_HAND ? player.getInventory().getPaperdollItem(Inventory.PAPERDOLL_LHAND) : null;
			final var replaced = java.util.stream.Stream.of(current, offhand).filter(java.util.Objects::nonNull).distinct().toList();
			if (current != null && affinity(player, current.getTemplate()) > 0 && affinity(player, candidate) <= 0) { return false; }
			return improvesLoadout(candidate, replaced, current == null ? 0 : utility(player, current.getTemplate()), utility(player, candidate));
		}
		final var replaced = player.getInventory().getItems().stream().filter(Item::isEquipped).filter(item -> overlaps(candidate.getBodyPart(), item.getTemplate().getBodyPart())).toList();
		if (replaced.stream().anyMatch(item -> affinity(player, item.getTemplate()) > 0) && affinity(player, candidate) <= 0) { return false; }
		if (replaced.stream().anyMatch(item -> completedSet(player, item.getTemplate())) && !completedSet(player, candidate)) { return false; }
		return improvesLoadout(candidate, replaced, replaced.stream().mapToDouble(item -> utility(player, item.getTemplate())).sum(), utility(player, candidate));
	}

	/** Shared read-only replacement gate for the actual paperdoll objects. */
	public static boolean improvesLoadout(ItemTemplate candidate, List<Item> replaced, double currentUtility, double candidateUtility)
	{
		// Template utility cannot value enchant, augmentation, attributes or special skills.
		// Retain those native objects rather than silently pricing their effects at zero.
		if (candidate == null || !Double.isFinite(currentUtility) || !Double.isFinite(candidateUtility) || currentUtility < 0 || candidateUtility <= currentUtility) { return false; }
		for (var item : replaced)
		{
			final var template = item.getTemplate();
			if (candidate instanceof Weapon && !(template instanceof Weapon))
			{
				// Native two-hand equip clears LHAND. Ammo can be replaced from owned stock;
				// shield defense/skills cannot be priced as weapon attack, so retain them.
				if (item.getItemType() == EtcItemType.ARROW || item.getItemType() == EtcItemType.BOLT) { continue; }
				return false;
			}
			if (item.getEnchantLevel() > 0 || item.isAugmented() || item.getElementals() != null && item.getElementals().length > 0
				|| template.isQuestItem() || template.isHeroItem() || template.isPvpItem() || template.getSkills() != null && template.getSkills().length > 0
				|| template.getElementals() != null && template.getElementals().length > 0
				|| template instanceof Weapon weapon && (weapon.hasTriggeredSkills() || weapon.getReducedSoulShot() > 0 || weapon.getReducedMpConsume() > 0)) { return false; }
		}
		final int grade = replaced.stream().filter(item -> item.getTemplate() instanceof Weapon || item.getTemplate() instanceof Armor).mapToInt(item -> item.getTemplate().getCrystalType().getLevel()).max().orElse(0);
		return candidate.getCrystalType().getLevel() >= grade;
	}

	private static boolean overlaps(BodyPart first, BodyPart second)
	{
		return first == second || first == BodyPart.FULL_ARMOR && (second == BodyPart.CHEST || second == BodyPart.LEGS)
			|| second == BodyPart.FULL_ARMOR && (first == BodyPart.CHEST || first == BodyPart.LEGS);
	}

	/** Highest legal grade first, then the native item's base combat value, with a stable tie break. */
	public static Comparator<ItemTemplate> preference(Player player)
	{
		final var values = new java.util.HashMap<Integer, Double>();
		return Comparator.comparingInt((ItemTemplate item) -> item instanceof Weapon ? 0 : 1)
			.thenComparing(Comparator.comparingInt((ItemTemplate item) -> item.getCrystalType().getLevel()).reversed())
			.thenComparing(Comparator.comparingDouble((ItemTemplate item) -> values.computeIfAbsent(item.getId(), ignored -> utility(player, item))).reversed()).thenComparingInt(ItemTemplate::getId);
	}
}
