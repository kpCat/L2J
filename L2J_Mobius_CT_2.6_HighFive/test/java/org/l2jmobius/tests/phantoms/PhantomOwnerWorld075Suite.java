package org.l2jmobius.tests.phantoms;

import org.l2jmobius.gameserver.config.PlayerConfig;
import org.l2jmobius.gameserver.config.custom.ServitorShareConfig;
import org.l2jmobius.gameserver.model.stats.Stat;
import java.util.Map;
import org.l2jmobius.gameserver.data.xml.EnchantItemData;
import org.l2jmobius.gameserver.data.xml.EnchantItemGroupsData;
import org.l2jmobius.gameserver.data.xml.ItemData;
import org.l2jmobius.gameserver.model.StatSet;
import org.l2jmobius.gameserver.model.item.enchant.EnchantResultType;
import org.l2jmobius.gameserver.model.item.enchant.EnchantScroll;

/** DB-free checks of the real owner configuration and canonical enchant route. */
public final class PhantomOwnerWorld075Suite implements PhantomTestSuite
{
	@Override public String id() { return "owner-world075"; }

	@Override
	public void beforeAll(PhantomTestContext context)
	{
		PlayerConfig.load();
		ItemData.getInstance();
		EnchantItemGroupsData.getInstance();
		EnchantItemData.getInstance();
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("servitor-eleven-independent-coefficients-and-parity", context ->
		{
			final var doubled = new ServitorShareConfig.Settings(true, Map.of());
			final var off = new ServitorShareConfig.Settings(false, Map.of());
			for (Stat stat : new Stat[] { Stat.POWER_ATTACK, Stat.POWER_DEFENCE, Stat.MAGIC_ATTACK, Stat.MAGIC_DEFENCE, Stat.MAX_HP, Stat.MAX_MP, Stat.MAX_RECOVERABLE_HP, Stat.MAX_RECOVERABLE_MP, Stat.CRITICAL_RATE, Stat.POWER_ATTACK_SPEED, Stat.MAGIC_ATTACK_SPEED })
			{
				near(1.5, doubled.coefficient(1557, stat, 1.25));
				near(1.25, off.coefficient(1557, stat, 1.25));
				near(1.25, doubled.coefficient(1556, stat, 1.25));
				near(1.25, new ServitorShareConfig.Settings(true, Map.of(stat, 1.0)).coefficient(1557, stat, 1.25));
			}
			final var mixed = new ServitorShareConfig.Settings(true, Map.of(Stat.POWER_ATTACK, 2.0, Stat.MAGIC_ATTACK, 1.0));
			near(2.0, mixed.coefficient(1557, Stat.POWER_ATTACK, 1.5)); near(1.25, mixed.coefficient(1557, Stat.MAGIC_ATTACK, 1.25));
			near(.5, mixed.coefficient(1557, Stat.POWER_ATTACK, .5)); near(1.2, mixed.coefficient(1557, Stat.MAX_CP, 1.2));
			final var loaded = ServitorShareConfig.read(java.nio.file.Path.of(ServitorShareConfig.FILE));
			if (!loaded.enabled() || loaded.multipliers().size() != 11 || loaded.multipliers().values().stream().anyMatch(value -> value != 2.0)) { throw new AssertionError("All eleven owner multipliers must load independently."); }
		});
		registry.add("owner-player-effective-values", context ->
		{
			near(100, PlayerConfig.ALT_WEIGHT_LIMIT);
			for (int value : new int[] { PlayerConfig.MAX_PVTSTORESELL_SLOTS_DWARF, PlayerConfig.MAX_PVTSTORESELL_SLOTS_OTHER, PlayerConfig.MAX_PVTSTOREBUY_SLOTS_DWARF, PlayerConfig.MAX_PVTSTOREBUY_SLOTS_OTHER }) { near(20, value); }
			for (int value : new int[] { PlayerConfig.INVENTORY_MAXIMUM_NO_DWARF, PlayerConfig.INVENTORY_MAXIMUM_QUEST_ITEMS, PlayerConfig.ALT_FREIGHT_SLOTS }) { near(200, value); }
			for (int value : new int[] { PlayerConfig.INVENTORY_MAXIMUM_DWARF, PlayerConfig.INVENTORY_MAXIMUM_GM, PlayerConfig.WAREHOUSE_SLOTS_DWARF, PlayerConfig.WAREHOUSE_SLOTS_NO_DWARF, PlayerConfig.WAREHOUSE_SLOTS_CLAN }) { near(250, value); }
			near(100, PlayerConfig.DWARF_RECIPE_LIMIT); near(100, PlayerConfig.COMMON_RECIPE_LIMIT);
			final var policy = new org.l2jmobius.gameserver.phantoms.economy.PhantomMerchantPolicy(1, 1, 1, 1, 30, 20, 0, 0, 0, 1, 1, 0, 0, java.util.List.of(0), java.util.List.of(new org.l2jmobius.gameserver.phantoms.economy.PhantomMerchantPolicy.Site(0, 0, 0, 1, 1, 60)), java.util.List.of());
			near(20, policy.linesPerStore());
			final var lines = java.util.stream.IntStream.range(1, 21).mapToObj(id -> new org.l2jmobius.gameserver.phantoms.economy.PhantomStorePlan.Line(id, 1871, 1, 1)).toList();
			final var plan = new org.l2jmobius.gameserver.phantoms.economy.PhantomStorePlan(org.l2jmobius.gameserver.phantoms.economy.PhantomStorePlan.Type.SELL, org.l2jmobius.gameserver.phantoms.economy.PhantomStorePlan.State.OPEN, "20 lines", lines, 1);
			near(20, org.l2jmobius.gameserver.phantoms.economy.PhantomStorePlan.decode(plan.encode()).lines().size());
			if (!PlayerConfig.DISABLE_OVER_ENCHANTING || !PlayerConfig.OVER_ENCHANT_PROTECTION) { throw new AssertionError("Enchant protections must stay enabled."); }
		});
		registry.add("native-enchant-boundaries-and-bonus-overcap", context ->
		{
			final var groups = EnchantItemGroupsData.getInstance();
			for (String name : new String[] { "ARMOR_GROUP", "FULL_ARMOR_GROUP", "FIGHTER_WEAPON_GROUP", "MAGE_WEAPON_GROUP" })
			{
				final var group = groups.getItemGroup(name);
				for (int level : new int[] { 0, 2, 3, 4, 9, 10, 15, 16, 24, 25, 26 })
				{
					final double expected = level >= 25 ? 0 : level >= 16 ? 66 : level >= 10 ? 70 : level >= (name.equals("FULL_ARMOR_GROUP") ? 4 : 3) ? 85 : 100;
					near(expected, group.getChance(level));
				}
			}
			near(25, groups.getMaxWeaponEnchant()); near(25, groups.getMaxArmorEnchant()); near(25, groups.getMaxAccessoryEnchant());
			final var target = ItemData.getInstance().getAllWeapons().stream().filter(item -> item.isEnchantable() && item.getCrystalTypePlus().name().equals("D")).findFirst().orElseThrow();
			final var normal = EnchantItemData.getInstance().getScrolls().stream().filter(scroll -> scroll.getId() == 955).findFirst().orElseThrow();
			near(25, normal.getMaxEnchantLevel());
			if (normal.calculateSuccess(target, 24, null, 65.9) != EnchantResultType.SUCCESS || normal.calculateSuccess(target, 24, null, 66) != EnchantResultType.FAILURE) { throw new AssertionError("Native chance boundary."); }
			final StatSet bonus = new StatSet(); bonus.set("id", 955); bonus.set("targetGrade", "D"); bonus.set("bonusRate", 100);
			final var unboundedBonus = new EnchantScroll(bonus);
			if (unboundedBonus.calculateSuccess(target, 25, null, 0) != EnchantResultType.ERROR || normal.isValid(target, 25, null)) { throw new AssertionError("bonusRate100/default65535 bypasses +25."); }
		});
	}

	private static void near(double expected, double actual)
	{
		if (!Double.isFinite(actual) || Math.abs(expected - actual) > 1e-9) { throw new AssertionError("Expected " + expected + ", got " + actual); }
	}
}
