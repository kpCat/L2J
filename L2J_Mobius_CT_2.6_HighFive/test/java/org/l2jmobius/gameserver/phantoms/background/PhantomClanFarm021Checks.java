/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.background;

import java.util.List;

import org.l2jmobius.gameserver.phantoms.background.PhantomHistoricalBackgroundPlanner.LootValue;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeAuthority;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.ChanceModel;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.DropFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.DropSourceKind;
import org.l2jmobius.tests.phantoms.PhantomAssertions;

public final class PhantomClanFarm021Checks
{
	public static void main(String[] args)
	{
		final var stock = List.of(
			new DropFact(18001, 1419, DropSourceKind.DEATH_DROP, ChanceModel.GROUP_CUMULATIVE, 1, 0, 20, 100, 1, 1, PhantomGameKnowledgeAuthority.SERVER_LOADER_FACT),
			new DropFact(18001, 3874, DropSourceKind.SPOIL, ChanceModel.UNGROUPED_INDEPENDENT, -1, 0, 0, 100, 1, 1, PhantomGameKnowledgeAuthority.SERVER_LOADER_FACT));
		final double bloodMark = PhantomHistoricalBackgroundPlanner.clanDropValue(1419, stock);
		PhantomAssertions.assertEquals(0.2, bloodMark, "Clan preference lost the stock group chance.");
		PhantomAssertions.assertEquals(0.0, PhantomHistoricalBackgroundPlanner.clanDropValue(3874, stock), "Spoil was treated as a death drop.");
		PhantomAssertions.assertEquals(0.0, PhantomHistoricalBackgroundPlanner.clanDropValue(0, stock), "A profile with no missing item preferred a clan drop.");
		final var clan = new LootValue(0.02, bloodMark);
		final var gear = new LootValue(0.021, 0);
		PhantomAssertions.assertTrue(clan.betterThan(new LootValue(0.02, 0)), "A viable clan drop did not break a tie without personal benefit.");
		PhantomAssertions.assertTrue(gear.betterThan(clan) && !clan.betterThan(gear), "Clan loot displaced a meaningful personal upgrade.");
		PhantomAssertions.assertTrue(!clan.betterThan(clan), "An equal score displaced the ordinary ordering.");
		System.out.println("PASS PhantomClanFarm021Checks");
	}
}
