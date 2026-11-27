/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.progression;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

import org.l2jmobius.gameserver.config.custom.AutoPlayConfig;
import org.l2jmobius.gameserver.data.holders.ArmorSet;
import org.l2jmobius.gameserver.data.xml.ArmorSetData;
import org.l2jmobius.gameserver.data.xml.ItemData;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.enums.player.PlayerClass;
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
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionSourcePlanner;
import org.l2jmobius.gameserver.phantoms.combat.PhantomNativeCombatCapability;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.DropFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.DropSourceKind;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeQuery;

/** Read-only loadout preference. Native Progression equips; native Commerce buys. */
public final class PhantomNativeEquipmentPolicy
{
	private static final List<BodyPart> ARMOR_SLOTS = List.of(BodyPart.HEAD, BodyPart.CHEST, BodyPart.LEGS, BodyPart.FULL_ARMOR, BodyPart.GLOVES, BodyPart.FEET);
	private static final List<Integer> SET_SLOTS = List.of(Inventory.PAPERDOLL_CHEST, Inventory.PAPERDOLL_LEGS, Inventory.PAPERDOLL_HEAD, Inventory.PAPERDOLL_GLOVES, Inventory.PAPERDOLL_FEET);
	private static final Set<Stat> MASTERY_STATS = Set.of(Stat.POWER_ATTACK, Stat.MAGIC_ATTACK, Stat.POWER_DEFENCE, Stat.MAGIC_DEFENCE, Stat.POWER_ATTACK_SPEED, Stat.MAGIC_ATTACK_SPEED, Stat.MAX_HP, Stat.MAX_MP, Stat.EVASION_RATE, Stat.CRITICAL_RATE);
	private PhantomNativeEquipmentPolicy() { }

	/** Progression intent is bounded by the expertise actually learned by this actor. */
	public static CrystalType targetGrade(int level, int expertiseLevel)
	{
		final CrystalType target = level >= 84 ? CrystalType.S84 : level >= 80 ? CrystalType.S80 : level >= 76 ? CrystalType.S : level >= 61 ? CrystalType.A : level >= 52 ? CrystalType.B : level >= 40 ? CrystalType.C : level >= 20 ? CrystalType.D : CrystalType.NONE;
		return CrystalType.values()[Math.max(0, Math.min(target.getLevel(), expertiseLevel))];
	}

	public enum Need
	{
		IMPROVEMENT,
		REQUIRED,
		CRITICAL;

		private Need higher(Need other)
		{
			return compareTo(other) >= 0 ? this : other;
		}
	}

	public record EquipmentNeeds(Need weapon, Need chest, Need legs)
	{
		public boolean required()
		{
			return weapon.higher(chest).higher(legs) != Need.IMPROVEMENT;
		}

		public Need addressedBy(ItemTemplate item)
		{
			if (item instanceof Weapon) { return weapon; }
			return switch (item.getBodyPart())
			{
				case CHEST -> chest;
				case LEGS -> legs;
				case FULL_ARMOR -> chest.higher(legs);
				default -> Need.IMPROVEMENT;
			};
		}
	}

	/** Separates material readiness from the optional pursuit of the next legal grade. */
	public static Need slotNeed(Item equipped, CrystalType target, boolean usableWithoutItem)
	{
		return slotNeed(equipped, target, usableWithoutItem, true);
	}

	public static Need slotNeed(Item equipped, CrystalType target, boolean usableWithoutItem, boolean compatible)
	{
		if (equipped == null) { return usableWithoutItem ? Need.IMPROVEMENT : Need.CRITICAL; }
		return !protectedEffects(equipped) && (!compatible || target.getLevel() - equipped.getTemplate().getCrystalType().getLevel() >= 2) ? Need.REQUIRED : Need.IMPROVEMENT;
	}

	/** An adjacent grade is urgent only when its legal, projected gain is substantial. */
	public static Need upgradeNeed(Item equipped, ItemTemplate candidate, CrystalType target, double currentUtility, double candidateUtility)
	{
		return equipped != null && !protectedEffects(equipped) && candidate.getCrystalType() == target
			&& target.getLevel() - equipped.getTemplate().getCrystalType().getLevel() == 1
			&& candidateUtility - currentUtility >= Math.max(5, currentUtility * 0.35) ? Need.REQUIRED : Need.IMPROVEMENT;
	}

	public static EquipmentNeeds equipmentNeeds(Player player)
	{
		final var target = targetGrade(player.getLevel(), player.getExpertiseLevel());
		final var weapon = player.getActiveWeaponItem();
		final var equippedWeapon = weapon != null && weapon != player.getFistsWeaponItem()
			&& weapon.getCrystalType().getLevel() <= player.getExpertiseLevel() && weapon.checkCondition(player, player, false) ? player.getActiveWeaponInstance() : null;
		final boolean usableFists = player.getActiveWeaponInstance() == null && weapon != null && weapon == player.getFistsWeaponItem()
			&& (!player.isMageClass() && target == CrystalType.NONE || player.getPlayerClass().equalsOrChildOf(PlayerClass.ORC_MONK));
		final var inventory = player.getInventory();
		final var chest = inventory.getPaperdollItem(Inventory.PAPERDOLL_CHEST);
		final boolean fullArmor = chest != null && chest.getTemplate().getBodyPart() == BodyPart.FULL_ARMOR;
		final var legs = inventory.getPaperdollItem(Inventory.PAPERDOLL_LEGS);
		return new EquipmentNeeds(slotNeed(equippedWeapon, target, usableFists, equippedWeapon == null || weapon.isMagicWeapon() == player.isMageClass()),
			slotNeed(chest, target, false, chest == null || compatible(player, chest.getTemplate())),
			fullArmor ? Need.IMPROVEMENT : slotNeed(legs, target, false, legs == null || compatible(player, legs.getTemplate())));
	}

	public static boolean hasRequiredEquipment(Player player)
	{
		return equipmentNeeds(player).required();
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
		if (!(item instanceof Armor armor)) { return false; }
		if (item.getBodyPart() == BodyPart.L_HAND)
		{
			final var weapon = player.getActiveWeaponItem();
			return (weapon == null || weapon.getBodyPart() != BodyPart.LR_HAND)
				&& (affinity(player, item) > 0 || (player.isMageClass() ? armor.getItemType() == ArmorType.SIGIL : armor.getItemType() == ArmorType.SHIELD));
		}
		if (!ARMOR_SLOTS.contains(item.getBodyPart()) && !accessory(item.getBodyPart())) { return false; }
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
		return power(player, item) * (1 + Math.min(0.5, affinity(player, item) * 0.02));
	}

	private static ArmorSet completedSet(Player player, ItemTemplate candidate)
	{
		final int chest = projectedSlot(player, candidate, Inventory.PAPERDOLL_CHEST, BodyPart.CHEST);
		final var set = chest > 0 ? ArmorSetData.getInstance().getSet(chest) : null;
		return set != null && set.containAll(chest,
			projectedSlot(player, candidate, Inventory.PAPERDOLL_LEGS, BodyPart.LEGS), projectedSlot(player, candidate, Inventory.PAPERDOLL_HEAD, BodyPart.HEAD),
			projectedSlot(player, candidate, Inventory.PAPERDOLL_GLOVES, BodyPart.GLOVES), projectedSlot(player, candidate, Inventory.PAPERDOLL_FEET, BodyPart.FEET)) ? set : null;
	}

	private static int projectedSlot(Player player, ItemTemplate candidate, int slot, BodyPart body)
	{
		final var replacement = candidate == null ? null : candidate.getBodyPart();
		if (replacement == body || body == BodyPart.CHEST && replacement == BodyPart.FULL_ARMOR) { return candidate.getId(); }
		if (body == BodyPart.LEGS && replacement == BodyPart.FULL_ARMOR || body == BodyPart.L_HAND && replacement == BodyPart.LR_HAND) { return 0; }
		final var current = player.getInventory().getPaperdollItem(slot);
		if (body == BodyPart.CHEST && replacement == BodyPart.LEGS && current != null && current.getTemplate().getBodyPart() == BodyPart.FULL_ARMOR) { return 0; }
		return current == null ? 0 : current.getId();
	}

	public static double power(Player player, ItemTemplate item)
	{
		if (item == null) { return 0; }
		final Stat stat = item instanceof Weapon ? player.isMageClass() ? Stat.MAGIC_ATTACK : Stat.POWER_ATTACK
			: accessory(item.getBodyPart()) ? Stat.MAGIC_DEFENCE : item.getItemType() == ArmorType.SHIELD ? Stat.SHIELD_DEFENCE : Stat.POWER_DEFENCE;
		return item.getStatFuncs(null, player).stream().filter(function -> function.getStat() == stat && function.getApplyCond() == null).mapToDouble(function -> Math.max(0, function.getValue())).max().orElse(0);
	}

	public static boolean upgrade(Player player, ItemTemplate candidate)
	{
		return evaluateUpgrade(player, candidate) != null;
	}

	public record Upgrade(Need need, double gain, double relativeGain) { }

	public record FarmDrop(int itemId, Upgrade upgrade, double stockWeight, DropSourceKind kind)
	{
		public double value() { return stockWeight * Math.min(2, upgrade.relativeGain()); }
	}

	/** Bounded by one useful, level-appropriate NPC and authoritative loot rows. */
	public static List<FarmDrop> farmDrops(Player player, PhantomGameKnowledgeQuery knowledge, int npcId, boolean protectWeapon)
	{
		final var npc = knowledge.findNpc(npcId).orElse(null);
		if (npc == null || !npc.attackable() || !npc.targetable() || npc.exp() <= 0 || Math.abs(npc.level() - player.getLevel()) > 4) { return List.of(); }
		final var target = targetGrade(player.getLevel(), player.getExpertiseLevel());
		final var facts = new java.util.ArrayList<>(knowledge.snapshot().dropFactsByNpc().getOrDefault(npcId, List.of()));
		if (canSpoil(player))
		{
			facts.addAll(knowledge.snapshot().spoilFactsByNpc().getOrDefault(npcId, List.of()));
		}
		final var sources = new java.util.HashMap<ItemTemplate, DropFact>();
		for (var fact : facts)
		{
			final var item = ItemData.getInstance().getTemplate(fact.itemId());
			if (!(item instanceof Armor || !protectWeapon && item instanceof Weapon) || item.getCrystalType().getLevel() > target.getLevel()) { continue; }
			if (farmLootWeight(fact) > 0) { sources.merge(item, fact, (left, right) -> farmLootWeight(left) >= farmLootWeight(right) ? left : right); }
		}
		return sources.entrySet().stream().sorted(Comparator.<java.util.Map.Entry<ItemTemplate, DropFact>>comparingDouble(entry -> farmLootWeight(entry.getValue())).reversed().thenComparingInt(entry -> entry.getKey().getId())).limit(16)
			.map(entry ->
			{
				final var item = entry.getKey();
				final var upgrade = evaluateUpgrade(player, item);
				return upgrade == null || player.getInventory().getInventoryItemCount(item.getId(), -1) > 0 ? null
					: new FarmDrop(item.getId(), upgrade, farmLootWeight(entry.getValue()), entry.getValue().sourceKind());
			}).filter(java.util.Objects::nonNull).sorted(Comparator.comparingDouble(FarmDrop::value).reversed().thenComparingInt(FarmDrop::itemId)).limit(4).toList();
	}

	private static double farmLootWeight(DropFact fact)
	{
		// Stock spoil does not reveal the probability of landing Spoil and completing the owned Sweep.
		return PhantomAcquisitionSourcePlanner.stockDropWeight(fact) * (fact.sourceKind() == DropSourceKind.SPOIL ? 0.5 : 1);
	}

	public static double farmMaterialValue(Player player, PhantomGameKnowledgeQuery knowledge, int npcId, Set<Integer> missing)
	{
		if (missing.isEmpty()) { return 0; }
		final var npc = knowledge.findNpc(npcId).orElse(null);
		if (npc == null || !npc.attackable() || !npc.targetable() || npc.exp() <= 0 || Math.abs(npc.level() - player.getLevel()) > 4) { return 0; }
		final var stock = knowledge.snapshot();
		final var drop = stock.dropFactsByNpc().getOrDefault(npcId, List.of()).stream();
		final var spoil = canSpoil(player) ? stock.spoilFactsByNpc().getOrDefault(npcId, List.of()).stream() : java.util.stream.Stream.<DropFact>empty();
		return java.util.stream.Stream.concat(drop, spoil).filter(fact -> missing.contains(fact.itemId()))
			.mapToDouble(fact -> farmLootWeight(fact) * Math.min(3, fact.minimumCount()) * 0.25).max().orElse(0);
	}

	private static boolean canSpoil(Player player)
	{
		final var spoil = player.getKnownSkill(254);
		return AutoPlayConfig.ENABLE_AUTO_SKILL && !AutoPlayConfig.DISABLED_AUTO_SKILLS.contains(254) && player.getSkillLevel(42) > 0
			&& spoil != null && spoil.isActive() && !spoil.isPassive() && !spoil.isToggle() && PhantomNativeCombatCapability.ordinaryUtility(spoil);
	}

	public static Upgrade evaluateUpgrade(Player player, ItemTemplate candidate)
	{
		if (!compatible(player, candidate)) { return null; }
		final var inventory = player.getInventory();
		final List<Item> replaced;
		if (candidate instanceof Weapon)
		{
			final var current = player.getActiveWeaponInstance();
			final var offhand = candidate.getBodyPart() == BodyPart.LR_HAND ? inventory.getPaperdollItem(Inventory.PAPERDOLL_LHAND) : null;
			replaced = java.util.stream.Stream.of(current, offhand).filter(java.util.Objects::nonNull).distinct().toList();
		}
		else if (accessory(candidate.getBodyPart())) { replaced = accessoryReplacements(candidate.getBodyPart(), inventory::getPaperdollItem); }
		else if (candidate.getBodyPart() == BodyPart.L_HAND)
		{
			final var offhand = inventory.getPaperdollItem(Inventory.PAPERDOLL_LHAND);
			replaced = offhand == null ? List.of() : List.of(offhand);
		}
		else
		{
			replaced = SET_SLOTS.stream().map(inventory::getPaperdollItem).filter(java.util.Objects::nonNull)
				.filter(item -> overlaps(candidate.getBodyPart(), item.getTemplate().getBodyPart())).toList();
		}
		if (replaced.stream().anyMatch(item -> affinity(player, item.getTemplate()) > 0) && affinity(player, candidate) <= 0) { return null; }
		final double currentUtility = replaced.stream().filter(item -> item.getTemplate() instanceof Weapon || item.getTemplate() instanceof Armor)
			.mapToDouble(item -> utility(player, item.getTemplate())).sum();
		double candidateUtility = utility(player, candidate);
		if (!accessory(candidate.getBodyPart()) && (!(candidate instanceof Weapon) || candidate.getBodyPart() == BodyPart.LR_HAND))
		{
			final var before = completedSet(player, null);
			final var after = completedSet(player, candidate);
			final boolean shieldBefore = shieldBonus(player, before, null);
			final boolean shieldAfter = shieldBonus(player, after, candidate);
			if (before != null && before != after || shieldBefore && !shieldAfter) { return null; }
			if (before == null && after != null || !shieldBefore && shieldAfter)
			{
				final double setDefence = SET_SLOTS.stream().map(inventory::getPaperdollItem).filter(java.util.Objects::nonNull)
					.filter(item -> !replaced.contains(item)).mapToDouble(item -> utility(player, item.getTemplate())).sum() + candidateUtility;
				candidateUtility += setDefence * ((before == null && after != null ? 0.10 : 0) + (shieldAfter && !shieldBefore ? 0.05 : 0));
			}
		}
		if (!improvesLoadout(candidate, replaced, currentUtility, candidateUtility)) { return null; }
		final var target = targetGrade(player.getLevel(), player.getExpertiseLevel());
		final var primary = replaced.stream().filter(item -> item.getTemplate() instanceof Weapon || item.getTemplate() instanceof Armor).findFirst().orElse(null);
		final var need = upgradeNeed(primary, candidate, target, currentUtility, candidateUtility);
		return new Upgrade(need, candidateUtility - currentUtility, (candidateUtility - currentUtility) / Math.max(10, currentUtility));
	}

	private static boolean shieldBonus(Player player, ArmorSet set, ItemTemplate candidate)
	{
		return set != null && !set.getShieldSkillId().isEmpty()
			&& set.containShield(projectedSlot(player, candidate, Inventory.PAPERDOLL_LHAND, BodyPart.L_HAND));
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
			if (protectedEffects(item)) { return false; }
		}
		final int grade = replaced.stream().filter(item -> item.getTemplate() instanceof Weapon || item.getTemplate() instanceof Armor).mapToInt(item -> item.getTemplate().getCrystalType().getLevel()).max().orElse(0);
		return candidate.getCrystalType().getLevel() >= grade;
	}

	/** The same valuable native effects survive both replacement and later liquidation. */
	public static boolean protectedTemplateEffects(ItemTemplate template)
	{
		return template == null || template.isQuestItem() || template.isHeroItem() || template.isPvpItem()
			|| template.getSkills() != null && template.getSkills().length > 0 || template.getElementals() != null && template.getElementals().length > 0
			|| template instanceof Weapon weapon && (weapon.hasTriggeredSkills() || weapon.getReducedSoulShot() > 0 || weapon.getReducedMpConsume() > 0);
	}

	public static boolean protectedEffects(Item item)
	{
		return item == null || item.getEnchantLevel() > 0 || item.isAugmented() || item.getElementals() != null && item.getElementals().length > 0 || protectedTemplateEffects(item.getTemplate());
	}

	private static boolean accessory(BodyPart body)
	{
		return switch (body) { case NECK, L_EAR, R_EAR, LR_EAR, L_FINGER, R_FINGER, LR_FINGER -> true; default -> false; };
	}

	/** Mirrors stock Inventory.equipItem: fill a free paired slot, otherwise displace LEFT only. */
	public static List<Item> accessoryReplacements(BodyPart body, java.util.function.IntFunction<Item> paperdoll)
	{
		final int left, right;
		switch (body)
		{
			case NECK -> { final var item = paperdoll.apply(Inventory.PAPERDOLL_NECK); return item == null ? List.of() : List.of(item); }
			case L_EAR, R_EAR, LR_EAR -> { left = Inventory.PAPERDOLL_LEAR; right = Inventory.PAPERDOLL_REAR; }
			case L_FINGER, R_FINGER, LR_FINGER -> { left = Inventory.PAPERDOLL_LFINGER; right = Inventory.PAPERDOLL_RFINGER; }
			default -> throw new IllegalArgumentException("Unsupported accessory body part.");
		}
		final var item = paperdoll.apply(left);
		return item == null || paperdoll.apply(right) == null ? List.of() : List.of(item);
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
		final var sets = new java.util.HashMap<Integer, Boolean>();
		return preference(item -> values.computeIfAbsent(item.getId(), ignored -> utility(player, item)), item -> sets.computeIfAbsent(item.getId(), ignored -> item instanceof Armor && !accessory(item.getBodyPart()) && completedSet(player, item) != null));
	}

	private static Comparator<ItemTemplate> preference(java.util.function.ToDoubleFunction<ItemTemplate> utility, java.util.function.Predicate<ItemTemplate> completedSet)
	{
		return Comparator.comparingInt((ItemTemplate item) -> item instanceof Weapon ? 0 : 1)
			.thenComparing(Comparator.comparingInt((ItemTemplate item) -> item.getCrystalType().getLevel()).reversed())
			.thenComparingInt(item -> completedSet.test(item) ? 0 : 1)
			.thenComparing(Comparator.comparingDouble(utility).reversed()).thenComparingInt(ItemTemplate::getId);
	}
}
