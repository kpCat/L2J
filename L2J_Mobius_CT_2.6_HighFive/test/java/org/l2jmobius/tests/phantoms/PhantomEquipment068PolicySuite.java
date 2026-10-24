/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.util.List;
import org.l2jmobius.gameserver.model.StatSet;
import org.l2jmobius.gameserver.model.conditions.ConditionLogicAnd;
import org.l2jmobius.gameserver.model.conditions.ConditionUsingItemType;
import org.l2jmobius.gameserver.model.item.Armor;
import org.l2jmobius.gameserver.model.item.EtcItem;
import org.l2jmobius.gameserver.model.item.Weapon;
import org.l2jmobius.gameserver.model.item.instance.Item;
import org.l2jmobius.gameserver.model.item.type.ArmorType;
import org.l2jmobius.gameserver.model.item.type.CrystalType;
import org.l2jmobius.gameserver.model.item.type.WeaponType;
import org.l2jmobius.gameserver.model.stats.Stat;
import org.l2jmobius.gameserver.model.stats.functions.FuncTemplate;
import org.l2jmobius.gameserver.phantoms.progression.PhantomNativeEquipmentPolicy;

/** Read-only candidate projection; no fake inventory swap is needed to inspect learned mastery. */
public final class PhantomEquipment068PolicySuite implements PhantomTestSuite
{
	@Override public String id() { return "equipment068-policy"; }
	@Override public void register(PhantomTestRegistry registry)
	{
		registry.add("01-weapon-affinity-uses-native-mask-not-item-or-class-id", context ->
		{
			for (WeaponType preferred : List.of(WeaponType.BLUNT, WeaponType.BOW, WeaponType.DAGGER))
			{
				final var mastery = new FuncTemplate(null, new ConditionUsingItemType(preferred.mask()), "Add", -1, Stat.POWER_ATTACK, 12);
				for (WeaponType candidate : List.of(WeaponType.BLUNT, WeaponType.BOW, WeaponType.DAGGER))
				{
					final double score = PhantomNativeEquipmentPolicy.equipmentAffinity(List.of(mastery), weapon(candidate));
					PhantomAssertions.assertTrue(preferred == candidate ? score > 0 : score == 0, "Native weapon mastery mask was ignored: " + preferred + '/' + candidate);
				}
			}
		});
		registry.add("02-armor-affinity-and-unsupported-conditions-fail-closed", context ->
		{
			for (ArmorType preferred : List.of(ArmorType.LIGHT, ArmorType.HEAVY, ArmorType.MAGIC))
			{
				final var mastery = new FuncTemplate(new ConditionUsingItemType(preferred.mask()), null, "Mul", -1, Stat.POWER_DEFENCE, 1.2);
				for (ArmorType candidate : List.of(ArmorType.LIGHT, ArmorType.HEAVY, ArmorType.MAGIC))
				{
					final double score = PhantomNativeEquipmentPolicy.equipmentAffinity(List.of(mastery), armor(candidate));
					PhantomAssertions.assertTrue(preferred == candidate ? score > 0 : score == 0, "Native armor mastery mask was ignored.");
				}
			}
			final var impossible = new ConditionLogicAnd();
			impossible.add(new ConditionUsingItemType(WeaponType.BOW.mask()));
			impossible.add(new ConditionUsingItemType(WeaponType.DAGGER.mask()));
			PhantomAssertions.assertEquals(0d, PhantomNativeEquipmentPolicy.equipmentAffinity(List.of(new FuncTemplate(null, impossible, "Add", -1, Stat.POWER_ATTACK, 50)), weapon(WeaponType.BOW)), "Conjunction was treated as an optional mask.");
			PhantomAssertions.assertEquals(0d, PhantomNativeEquipmentPolicy.equipmentAffinity(List.of(new FuncTemplate(null, null, "Add", -1, Stat.POWER_ATTACK, 50)), weapon(WeaponType.BOW)), "Unconditional generic stat was mistaken for equipment mastery.");
		});
		registry.add("03-actual-enchanted-current-cannot-be-replaced-by-bare-grade", context ->
		{
			for (WeaponType type : List.of(WeaponType.SWORD, WeaponType.BLUNT))
			{
				final var current = new Item(990077, gear(type, CrystalType.D));
				current.setEnchantLevel(25);
				PhantomAssertions.assertFalse(PhantomNativeEquipmentPolicy.improvesLoadout(gear(type, CrystalType.C), List.of(current), 100, 200), "Unpriced +25 native Item was discarded using a bare template.");
				PhantomAssertions.assertEquals(25, current.getEnchantLevel(), "Read-only preference changed native enchant.");
			}
		});
		registry.add("04-grade-never-overrides-combat-utility-or-combined-armor", context ->
		{
			for (WeaponType type : List.of(WeaponType.SWORD, WeaponType.BLUNT))
			{
				final var current = new Item(990078, gear(type, CrystalType.D));
				final var candidate = gear(type, CrystalType.C);
				PhantomAssertions.assertFalse(PhantomNativeEquipmentPolicy.improvesLoadout(candidate, List.of(current), 100, 90), "Higher grade overrode worse combat utility.");
				PhantomAssertions.assertFalse(PhantomNativeEquipmentPolicy.improvesLoadout(candidate, List.of(current), 100, 100), "Equal utility caused needless purchase.");
				PhantomAssertions.assertTrue(PhantomNativeEquipmentPolicy.improvesLoadout(candidate, List.of(current), 100, 120), "Useful legal upgrade was rejected.");
			}
			final var armor = new Item(990079, armor(ArmorType.HEAVY));
			final var legSet = item(); legSet.set("armor_type", ArmorType.HEAVY); legSet.set("bodypart", "legs");
			final var fullSet = item(); fullSet.set("armor_type", ArmorType.HEAVY); fullSet.set("bodypart", "fullarmor"); fullSet.set("crystal_type", "C");
			PhantomAssertions.assertFalse(PhantomNativeEquipmentPolicy.improvesLoadout(new Armor(fullSet), List.of(armor, new Item(990080, new Armor(legSet))), 140, 130), "Full armor ignored the combined displaced chest and legs.");
			PhantomAssertions.assertFalse(PhantomNativeEquipmentPolicy.improvesLoadout(new Armor(fullSet), List.of(armor), 100, Double.NaN), "Unknown utility did not fail closed.");
		});
		registry.add("05-special-native-skills-retained-without-skill-resolution", context ->
		{
			for (String skill : List.of("item_skill", "onmagic_skill", "oncrit_skill"))
			{
				final var set = item(); set.set("weapon_type", "SWORD"); set.set("crystal_type", "D"); set.set(skill, "1-1");
				final var current = new Item(990081, new Weapon(set));
				PhantomAssertions.assertFalse(PhantomNativeEquipmentPolicy.improvesLoadout(gear(WeaponType.SWORD, CrystalType.C), List.of(current), 100, 200), "Unvalued native special skill was discarded: " + skill);
			}
		});
		registry.add("06-two-hand-native-displacement-retains-shield-but-allows-ammunition", context ->
		{
			final var set = item(); set.set("weapon_type", "BLUNT"); set.set("bodypart", "lrhand"); set.set("crystal_type", "C"); set.set("is_magic_weapon", true);
			final var twoHand = new Weapon(set);
			final var shieldSet = item(); shieldSet.set("armor_type", ArmorType.SHIELD); shieldSet.set("bodypart", "lhand");
			final var shield = new Item(990082, new Armor(shieldSet));
			final var current = new Item(990083, gear(WeaponType.BLUNT, CrystalType.C));
			PhantomAssertions.assertFalse(PhantomNativeEquipmentPolicy.improvesLoadout(twoHand, List.of(current, shield), 101, 111), "Two-hand attack gain silently discarded shield defense or skills.");
			PhantomAssertions.assertFalse(PhantomNativeEquipmentPolicy.improvesLoadout(twoHand, List.of(shield), 0, 111), "Unarmed shortcut discarded the native offhand.");
			for (String ammunition : List.of("ARROW", "BOLT"))
			{
				final var ammoSet = item(); ammoSet.set("etcitem_type", ammunition); ammoSet.set("bodypart", "lhand"); ammoSet.set("crystal_type", "S");
				PhantomAssertions.assertTrue(PhantomNativeEquipmentPolicy.improvesLoadout(twoHand, List.of(current, new Item(990084, new EtcItem(ammoSet))), 101, 111), "Replaceable native ammunition blocked equipment improvement.");
			}
		});
	}
	private static Weapon gear(WeaponType type, CrystalType grade)
	{
		final var set = item(); set.set("weapon_type", type.name()); set.set("crystal_type", grade.name()); set.set("is_magic_weapon", type == WeaponType.BLUNT); return new Weapon(set);
	}
	private static Weapon weapon(WeaponType type)
	{
		final var set = item(); set.set("weapon_type", type.name()); return new Weapon(set);
	}
	private static Armor armor(ArmorType type)
	{
		final var set = item(); set.set("armor_type", type); return new Armor(set);
	}
	private static StatSet item()
	{
		final var set = new StatSet(); set.set("item_id", 990001); set.set("name", "Projection test"); set.set("bodypart", "chest"); return set;
	}
}
