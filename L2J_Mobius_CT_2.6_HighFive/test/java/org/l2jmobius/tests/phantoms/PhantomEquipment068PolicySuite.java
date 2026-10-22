/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.util.List;
import org.l2jmobius.gameserver.model.StatSet;
import org.l2jmobius.gameserver.model.conditions.ConditionLogicAnd;
import org.l2jmobius.gameserver.model.conditions.ConditionUsingItemType;
import org.l2jmobius.gameserver.model.item.Armor;
import org.l2jmobius.gameserver.model.item.Weapon;
import org.l2jmobius.gameserver.model.item.type.ArmorType;
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
