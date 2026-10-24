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
		registry.add("native-store-close-keeps-craft-admission-monitor", context ->
		{
			final String source = java.nio.file.Files.readString(context.moduleRoot().resolve("java/org/l2jmobius/gameserver/phantoms/economy/PhantomStoreService.java"));
			final String close = source.substring(source.indexOf("public Result close("), source.indexOf("public ShutdownResult shutdown("));
			final int sell = close.indexOf("synchronized (player.getSellList())");
			final int buy = close.indexOf("synchronized (player.getBuyList())", Math.max(0, sell));
			final int manufacture = close.indexOf("synchronized (player.getManufactureItems())", Math.max(0, buy));
			final int busy = close.indexOf("RecipeManager.getInstance().isMakingItem(player)", Math.max(0, manufacture));
			final int clear = close.indexOf("player.getManufactureItems().clear()", Math.max(0, busy));
			final int reset = close.indexOf("player.setPrivateStoreType(PrivateStoreType.NONE)", Math.max(0, clear));
			if (sell < 0 || buy <= sell || manufacture <= buy || busy <= manufacture || clear <= busy || reset <= clear) { throw new AssertionError("Native close must recheck queued craft under SELL/BUY/manufacture admission monitors before clearing and releasing its owner."); }
		});
		registry.add("ordinary-retail-reuses-canonical-recipe-reserve", context ->
		{
			final String source = java.nio.file.Files.readString(context.moduleRoot().resolve("java/org/l2jmobius/gameserver/phantoms/economy/PhantomAutonomousMarketProducer.java"));
			if (!source.contains("private static long surplusCount(") || !source.contains("policy.evaluate(owner, item, java.util.Set.of(), false, false)") || !source.contains("role.keyPart()") || !source.contains("decision.count()")) { throw new AssertionError("Ordinary retail must reuse native known-recipe protection and bound every line by canonical surplus above reserve."); }
		});
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
			final var expected = profile(context);
			if (loaded.enabled() != Boolean.parseBoolean(expected.get("EnableServitorShareBonusTuning")) || loaded.multipliers().size() != 11) { throw new AssertionError("All eleven owner multipliers must load independently."); }
			final Map<Stat, String> keys = Map.ofEntries(Map.entry(Stat.POWER_ATTACK, "PAtk"), Map.entry(Stat.POWER_DEFENCE, "PDef"), Map.entry(Stat.MAGIC_ATTACK, "MAtk"), Map.entry(Stat.MAGIC_DEFENCE, "MDef"), Map.entry(Stat.MAX_HP, "MaxHp"), Map.entry(Stat.MAX_MP, "MaxMp"), Map.entry(Stat.MAX_RECOVERABLE_HP, "MaxRecoverableHp"), Map.entry(Stat.MAX_RECOVERABLE_MP, "MaxRecoverableMp"), Map.entry(Stat.CRITICAL_RATE, "CritRate"), Map.entry(Stat.POWER_ATTACK_SPEED, "PAtkSpd"), Map.entry(Stat.MAGIC_ATTACK_SPEED, "MAtkSpd"));
			for (var entry : keys.entrySet()) { near(Double.parseDouble(expected.get("ServitorShare" + entry.getValue() + "BonusMultiplier")), loaded.multipliers().get(entry.getKey())); }
		});
		registry.add("owner-player-effective-values", context ->
		{
			final var expected = profile(context);
			final Map<String, Double> actual = Map.ofEntries(
				Map.entry("AltWeightLimit", PlayerConfig.ALT_WEIGHT_LIMIT), Map.entry("MaxPvtStoreSellSlotsDwarf", (double) PlayerConfig.MAX_PVTSTORESELL_SLOTS_DWARF), Map.entry("MaxPvtStoreSellSlotsOther", (double) PlayerConfig.MAX_PVTSTORESELL_SLOTS_OTHER),
				Map.entry("MaxPvtStoreBuySlotsDwarf", (double) PlayerConfig.MAX_PVTSTOREBUY_SLOTS_DWARF), Map.entry("MaxPvtStoreBuySlotsOther", (double) PlayerConfig.MAX_PVTSTOREBUY_SLOTS_OTHER),
				Map.entry("MaximumSlotsForNoDwarf", (double) PlayerConfig.INVENTORY_MAXIMUM_NO_DWARF), Map.entry("MaximumSlotsForDwarf", (double) PlayerConfig.INVENTORY_MAXIMUM_DWARF), Map.entry("MaximumSlotsForGMPlayer", (double) PlayerConfig.INVENTORY_MAXIMUM_GM), Map.entry("MaximumSlotsForQuestItems", (double) PlayerConfig.INVENTORY_MAXIMUM_QUEST_ITEMS),
				Map.entry("MaximumWarehouseSlotsForDwarf", (double) PlayerConfig.WAREHOUSE_SLOTS_DWARF), Map.entry("MaximumWarehouseSlotsForNoDwarf", (double) PlayerConfig.WAREHOUSE_SLOTS_NO_DWARF), Map.entry("MaximumWarehouseSlotsForClan", (double) PlayerConfig.WAREHOUSE_SLOTS_CLAN),
				Map.entry("MaximumFreightSlots", (double) PlayerConfig.ALT_FREIGHT_SLOTS), Map.entry("DwarfRecipeLimit", (double) PlayerConfig.DWARF_RECIPE_LIMIT), Map.entry("CommonRecipeLimit", (double) PlayerConfig.COMMON_RECIPE_LIMIT));
			for (var entry : actual.entrySet()) { near(Double.parseDouble(expected.get(entry.getKey())), entry.getValue()); }
			final var policy = new org.l2jmobius.gameserver.phantoms.economy.PhantomMerchantPolicy(1, 1, 1, 1, 30, 20, 0, 0, 0, 1, 1, 0, 0, java.util.List.of(0), java.util.List.of(new org.l2jmobius.gameserver.phantoms.economy.PhantomMerchantPolicy.Site(0, 0, 0, 1, 1, 60)), java.util.List.of());
			near(20, policy.linesPerStore());
			final var lines = java.util.stream.IntStream.range(1, 21).mapToObj(id -> new org.l2jmobius.gameserver.phantoms.economy.PhantomStorePlan.Line(id, 1871, 1, 1)).toList();
			final var plan = new org.l2jmobius.gameserver.phantoms.economy.PhantomStorePlan(org.l2jmobius.gameserver.phantoms.economy.PhantomStorePlan.Type.SELL, org.l2jmobius.gameserver.phantoms.economy.PhantomStorePlan.State.OPEN, "20 lines", lines, 1);
			near(20, org.l2jmobius.gameserver.phantoms.economy.PhantomStorePlan.decode(plan.encode()).lines().size());
			if (PlayerConfig.DISABLE_OVER_ENCHANTING != Boolean.parseBoolean(expected.get("DisableOverEnchanting")) || PlayerConfig.OVER_ENCHANT_PROTECTION != Boolean.parseBoolean(expected.get("OverEnchantProtection"))) { throw new AssertionError("Enchant protections differ from the current owner profile."); }
		});
		registry.add("native-enchant-boundaries-and-bonus-overcap", context ->
		{
			final var groups = EnchantItemGroupsData.getInstance();
			final var expected = profile(context);
			final int maximum = Integer.parseInt(expected.get("standard scroll maxEnchant previously16"));
			for (String name : new String[] { "ARMOR_GROUP", "FULL_ARMOR_GROUP", "FIGHTER_WEAPON_GROUP", "MAGE_WEAPON_GROUP" })
			{
				final var group = groups.getItemGroup(name);
				for (int level : new int[] { 0, 2, 3, 4, 9, 10, 15, 16, maximum - 1, maximum, maximum + 1 })
				{
					near(chance(expected.get(name.equals("FULL_ARMOR_GROUP") ? "FULL_ARMOR_GROUP.current" : "ARMOR_GROUP/FIGHTER_WEAPON_GROUP/MAGE_WEAPON_GROUP.current"), level), group.getChance(level));
				}
			}
			near(maximum, groups.getMaxWeaponEnchant()); near(maximum, groups.getMaxArmorEnchant()); near(maximum, groups.getMaxAccessoryEnchant());
			final var target = ItemData.getInstance().getAllWeapons().stream().filter(item -> item.isEnchantable() && item.getCrystalTypePlus().name().equals("D")).findFirst().orElseThrow();
			final var normal = EnchantItemData.getInstance().getScrolls().stream().filter(scroll -> scroll.getId() == 955).findFirst().orElseThrow();
			near(maximum, normal.getMaxEnchantLevel());
			final double chance = normal.getChance(target, maximum - 1);
			if (chance <= 0 || normal.calculateSuccess(target, maximum - 1, null, chance - .001) != EnchantResultType.SUCCESS || chance < 100 && normal.calculateSuccess(target, maximum - 1, null, chance) != EnchantResultType.FAILURE) { throw new AssertionError("Native chance boundary."); }
			final StatSet bonus = new StatSet(); bonus.set("id", 955); bonus.set("targetGrade", "D"); bonus.set("bonusRate", 100);
			final var unboundedBonus = new EnchantScroll(bonus);
			if (unboundedBonus.calculateSuccess(target, 25, null, 0) != EnchantResultType.ERROR || normal.isValid(target, maximum, null)) { throw new AssertionError("bonusRate100/default65535 bypasses the native hard limit or configured ordinary cap."); }
		});
	}

	private static Map<String, String> profile(PhantomTestContext context) throws java.io.IOException
	{
		final Map<String, String> values = new java.util.HashMap<>();
		for (String row : java.nio.file.Files.readAllLines(context.moduleRoot().resolve("dist/game/config/OwnerProfile38.tsv")).subList(1, 39))
		{
			final String[] fields = row.split("\t", -1);
			if (fields.length != 5 || values.putIfAbsent(fields[2], fields[3]) != null) { throw new AssertionError("Invalid owner profile fixture."); }
		}
		return values;
	}

	private static double chance(String ranges, int level)
	{
		for (String segment : ranges.split(";"))
		{
			final String[] pair = segment.split(":"), bounds = pair[0].split("-");
			if (level >= Integer.parseInt(bounds[0]) && level <= Integer.parseInt(bounds[bounds.length - 1])) { return Double.parseDouble(pair[1]); }
		}
		throw new AssertionError("Owner enchant range does not cover " + level);
	}

	private static void near(double expected, double actual)
	{
		if (!Double.isFinite(actual) || Math.abs(expected - actual) > 1e-9) { throw new AssertionError("Expected " + expected + ", got " + actual); }
	}
}
