package org.l2jmobius.tests.phantoms;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.l2jmobius.gameserver.phantoms.economy.PhantomRateAwareLootFairValue;
import org.l2jmobius.gameserver.phantoms.economy.PhantomRateAwareLootFairValue.Rates;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeAuthority;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.ChanceModel;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.DropFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.DropSourceKind;

public final class PhantomPost001LootFairValueSuite implements PhantomTestSuite
{
	private static final DropFact ADENA = fact(100, 57, DropSourceKind.DEATH_DROP, 100, 100);
	private static final DropFact MATERIAL = fact(100, 2000, DropSourceKind.DEATH_DROP, 10, 1);
	private static final Map<Integer, List<DropFact>> DEATH_BY_ITEM = Map.of(2000, List.of(MATERIAL));
	private static final Map<Integer, List<DropFact>> DEATH_BY_NPC = Map.of(100, List.of(ADENA, MATERIAL));

	@Override
	public String id()
	{
		return "post001-loot-fair-value";
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("relative-adena-drop-rate-matrix", context ->
		{
			near(1000, value(new Rates(1, 1, 1, 1, Map.of(57, 1d), Map.of(57, 1d))));
			near(200, value(new Rates(5, 1, 1, 1, Map.of(57, 1d), Map.of(57, 1d))));
			near(5000, value(new Rates(1, 1, 1, 1, Map.of(57, 1d), Map.of(57, 5d))));
			near(1000, value(new Rates(5, 1, 1, 1, Map.of(57, 1d), Map.of(57, 5d))));
			near(200, value(new Rates(10, 5, 1, 1, Map.of(57, 1d), Map.of(57, 10d))));
		});
		registry.add("item-specific-rate-precedence-and-spoil", context ->
		{
			near(1000, value(new Rates(5, 5, 1, 1, Map.of(57, 1d, 2000, 1d), Map.of(57, 1d, 2000, 1d))));
			final DropFact spoil = fact(100, 2000, DropSourceKind.SPOIL, 10, 1);
			final double spoilBase = PhantomRateAwareLootFairValue.quote(2000, Map.of(), Map.of(2000, List.of(spoil)), Map.of(100, List.of(ADENA)), Set.of(100), new Rates(1, 1, 1, 1, Map.of(57, 1d), Map.of(57, 1d))).fairValue();
			final double spoilHigh = PhantomRateAwareLootFairValue.quote(2000, Map.of(), Map.of(2000, List.of(spoil)), Map.of(100, List.of(ADENA)), Set.of(100), new Rates(1, 1, 5, 1, Map.of(57, 1d), Map.of(57, 1d))).fairValue();
			near(1000, spoilBase);
			near(200, spoilHigh);
		});
		registry.add("ineligible-or-unpaired-source-excluded", context ->
		{
			try
			{
				PhantomRateAwareLootFairValue.quote(2000, DEATH_BY_ITEM, Map.of(), DEATH_BY_NPC, Set.of(), new Rates(1, 1, 1, 1, Map.of(57, 1d), Map.of(57, 1d)));
				throw new AssertionError("Unspawned source must be unsafe.");
			}
			catch (IllegalArgumentException expected)
			{
			}
		});
	}

	private static double value(Rates rates)
	{
		return PhantomRateAwareLootFairValue.quote(2000, DEATH_BY_ITEM, Map.of(), DEATH_BY_NPC, Set.of(100), rates).fairValue();
	}

	private static DropFact fact(int npcId, int itemId, DropSourceKind kind, double chance, long count)
	{
		return new DropFact(npcId, itemId, kind, ChanceModel.UNGROUPED_INDEPENDENT, -1, 0, 0, chance, count, count, PhantomGameKnowledgeAuthority.SERVER_LOADER_FACT);
	}

	private static void near(double expected, double actual)
	{
		if (!Double.isFinite(actual) || (Math.abs(expected - actual) > (1.0e-8 * Math.max(1, Math.abs(expected)))))
		{
			throw new AssertionError("Expected " + expected + ", got " + actual);
		}
	}
}
