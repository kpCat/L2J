/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.util.List;
import java.util.Map;

import org.l2jmobius.gameserver.config.custom.PremiumSystemConfig;
import org.l2jmobius.gameserver.model.StatSet;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.enums.npc.DropType;
import org.l2jmobius.gameserver.model.actor.holders.npc.DropGroupHolder;
import org.l2jmobius.gameserver.model.actor.holders.npc.DropHolder;
import org.l2jmobius.gameserver.model.actor.instance.GrandBoss;
import org.l2jmobius.gameserver.model.actor.instance.Monster;
import org.l2jmobius.gameserver.model.actor.instance.RaidBoss;
import org.l2jmobius.gameserver.model.actor.templates.NpcTemplate;
import org.l2jmobius.gameserver.model.item.holders.ItemHolder;
import org.l2jmobius.gameserver.qol.PremiumLootPolicy;

/** Focused contracts plus actual NpcTemplate grouped/ungrouped/spoil execution in the guarded own070 fixture. */
public final class PhantomPremium070Checks
{
	private PhantomPremium070Checks()
	{
	}

	public static void policy()
	{
		check(PremiumLootPolicy.itemMultiplier(true, false, 6660, Map.of(), 2) == 2, "Raid rare chance must inherit premium.");
		check(PremiumLootPolicy.itemMultiplier(true, false, 6660, Map.of(6660, 0f), 2) == 0, "Explicit suppression must remain zero.");
		check(PremiumLootPolicy.itemMultiplier(false, false, 6660, Map.of(6660, 2f), 2) == 1, "Nonpremium owner inherited another account's benefit.");
		check(PremiumLootPolicy.itemMultiplier(true, true, 8600, Map.of(), 2) == 1, "Herb policy changed implicitly.");
		check(PremiumLootPolicy.combinedChance(2, 1.5) == 3 && PremiumLootPolicy.combinedChance(2, 2) == 3, "Combined chance bonus exceeded3.");
		check(PremiumLootPolicy.combinedChance(0, 1.5) == 0, "Zero premium item rule was revived.");
	}

	public static void nativeLoot(Player player, StringBuilder report)
	{
		if (!PhantomTask070Lane.enabled()) { throw new IllegalArgumentException("Exact own070 native lane required."); }
		check(player != null && !player.isGM() && !player.hasHeadlessOutboundSession() && !player.isNativeWorkManaged(), "Ordinary access0 actor required.");
		policy();
		check(PremiumSystemConfig.PREMIUM_SYSTEM_ENABLED, "OWN070 premium profile is disabled.");
		check(PremiumSystemConfig.PREMIUM_RATE_XP == 2 && PremiumSystemConfig.PREMIUM_RATE_SP == 2, "OWN070 XP/SP bonus differs.");
		check(PremiumSystemConfig.PREMIUM_RATE_DROP_CHANCE == 2 && PremiumSystemConfig.PREMIUM_RATE_DROP_AMOUNT == 1, "OWN070 death chance/amount differs.");
		check(PremiumSystemConfig.PREMIUM_RATE_SPOIL_CHANCE == 2 && PremiumSystemConfig.PREMIUM_RATE_SPOIL_AMOUNT == 1, "OWN070 spoil chance/amount differs.");
		final double rareChance = PremiumLootPolicy.itemMultiplier(true, false, 6660, PremiumSystemConfig.PREMIUM_RATE_DROP_CHANCE_BY_ID, PremiumSystemConfig.PREMIUM_RATE_DROP_CHANCE);
		final double rareAmount = PremiumLootPolicy.itemMultiplier(true, false, 6660, PremiumSystemConfig.PREMIUM_RATE_DROP_AMOUNT_BY_ID, PremiumSystemConfig.PREMIUM_RATE_DROP_AMOUNT);
		final double adenaChance = PremiumLootPolicy.itemMultiplier(true, false, 57, PremiumSystemConfig.PREMIUM_RATE_DROP_CHANCE_BY_ID, PremiumSystemConfig.PREMIUM_RATE_DROP_CHANCE);
		final double adenaAmount = PremiumLootPolicy.itemMultiplier(true, false, 57, PremiumSystemConfig.PREMIUM_RATE_DROP_AMOUNT_BY_ID, PremiumSystemConfig.PREMIUM_RATE_DROP_AMOUNT);
		check(rareChance == 2 && rareAmount == 1, "Queen Ant30% must become60%, one ring per successful native roll.");
		for (int itemId : new int[] {6656, 6657, 6658, 6659, 6660, 6661, 6662, 8191, 10314, 16025, 16026, 21712})
		{
			check(PremiumLootPolicy.itemMultiplier(true, false, itemId, PremiumSystemConfig.PREMIUM_RATE_DROP_CHANCE_BY_ID, PremiumSystemConfig.PREMIUM_RATE_DROP_CHANCE) == 2, "Rare item-specific override still disables premium: " + itemId);
			check(PremiumLootPolicy.itemMultiplier(true, false, itemId, PremiumSystemConfig.PREMIUM_RATE_DROP_AMOUNT_BY_ID, PremiumSystemConfig.PREMIUM_RATE_DROP_AMOUNT) == 1, "Rare item-specific amount differs: " + itemId);
		}
		check(adenaChance == 1 && adenaAmount == 2, "Adena must multiply once, through amount2/chance1.");
		report.append("premium070.math\tQA source group30% x item100% ->60%;rareAmount1;AdenaChance1;AdenaAmount2;combinedCap3;nonpremium1\n");
		final boolean oldPremium = player.hasPremiumStatus();
		final List<Monster> victims = List.of(new Monster(template()), new RaidBoss(template()), new GrandBoss(template()));
		try
		{
			for (Monster victim : victims)
			{
				final var group = new DropGroupHolder(100);
				group.addDrop(new DropHolder(DropType.DROP, 6660, 1, 1, 100));
				victim.getTemplate().setDropGroups(List.of(group));
				victim.getTemplate().addDrop(new DropHolder(DropType.DROP, 6656, 1, 1, 100));
				victim.getTemplate().addDrop(new DropHolder(DropType.DROP, 1864, 3, 3, 100));
				victim.getTemplate().addDrop(new DropHolder(DropType.DROP, 57, 17, 17, 100));
				victim.getTemplate().addDrop(new DropHolder(DropType.DROP, 21747, 1, 1, 100));
				victim.getTemplate().addSpoil(new DropHolder(DropType.SPOIL, 1866, 5, 5, 100));
				player.setPremiumStatus(false);
				final List<ItemHolder> base = victim.getTemplate().calculateDrops(DropType.DROP, victim, player);
				final List<ItemHolder> baseSpoil = victim.getTemplate().calculateDrops(DropType.SPOIL, victim, player);
				player.setPremiumStatus(true);
				check(PremiumLootPolicy.eligible(player), "Native premium purchaser is ineligible.");
				final List<ItemHolder> premium = victim.getTemplate().calculateDrops(DropType.DROP, victim, player);
				final List<ItemHolder> premiumSpoil = victim.getTemplate().calculateDrops(DropType.SPOIL, victim, player);
				check(count(base, 6660) > 0 && count(base, 6660) == count(premium, 6660), "Grouped rare amount multiplied.");
				check(count(base, 6656) > 0 && count(base, 6656) == count(premium, 6656), "Ungrouped rare amount multiplied.");
				check(count(base, 1864) > 0 && count(base, 1864) == count(premium, 1864), "Ungrouped ordinary amount multiplied.");
				check(count(baseSpoil, 1866) > 0 && count(baseSpoil, 1866) == count(premiumSpoil, 1866), "Spoil amount multiplied.");
				check(count(base, 57) > 0 && count(premium, 57) == 2 * count(base, 57), "Native Adena reward is not exactly x2.");
				check(count(base, 21747) == 0 && count(premium, 21747) == 0, "Item-specific rate0 suppression was lost.");
				report.append("premium070.nativeLoot\t").append(victim.getClass().getSimpleName()).append(";base=").append(base).append(";premium=").append(premium).append(";spoil=").append(premiumSpoil).append(";suppressed21747=0;isolated100%-boundary table,not boss loot award\n");
			}
		}
		finally
		{
			player.setPremiumStatus(oldPremium);
			for (Monster victim : victims) { victim.deleteMe(); }
		}
	}

	private static long count(List<ItemHolder> drops, int id)
	{
		return drops == null ? 0 : drops.stream().filter(item -> item.getId() == id).mapToLong(ItemHolder::getCount).sum();
	}

	private static NpcTemplate template()
	{
		final var set = new StatSet();
		set.set("id", 18342); set.set("displayId", 18342); set.set("level", 85); set.set("type", "Monster");
		set.set("name", "Premium070Boundary"); set.set("baseHpMax", 1000); set.set("baseMpMax", 100);
		set.set("collisionRadius", 8); set.set("collisionHeight", 20);
		final var result = new NpcTemplate(set); result.setSkills(Map.of()); return result;
	}

	private static void check(boolean condition, String message)
	{
		if (!condition) { throw new AssertionError(message); }
	}
}
