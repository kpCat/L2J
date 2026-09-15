package org.l2jmobius.gameserver.phantoms.economy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.l2jmobius.gameserver.config.RatesConfig;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.ChanceModel;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.DropFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.DropSourceKind;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.NpcFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.NpcKind;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeSnapshot;

/** Source-backed Adena yield per obtained item; an explicit farming opportunity-price proxy. */
public final class PhantomRateAwareLootFairValue
{
	private static final int ADENA_ID = 57;
	private static final double MAX_VALUE = 1.0e15;

	private PhantomRateAwareLootFairValue()
	{
	}

	public static Quote quote(int itemId, PhantomGameKnowledgeSnapshot knowledge, Rates rates)
	{
		return quote(itemId, knowledge, rates, eligibleNpcIds(knowledge));
	}

	public static Set<Integer> eligibleNpcIds(PhantomGameKnowledgeSnapshot knowledge)
	{
		if (knowledge == null)
		{
			throw new IllegalArgumentException("Missing accepted loot knowledge.");
		}
		return knowledge.npcById().values().stream().filter(npc -> npc.attackable() && npc.targetable() && ((npc.kind() == NpcKind.MONSTER) || (npc.kind() == NpcKind.OTHER_ATTACKABLE)) && !knowledge.spawnFactsByNpc().getOrDefault(npc.npcId(), List.of()).isEmpty()).map(NpcFact::npcId).collect(java.util.stream.Collectors.toUnmodifiableSet());
	}

	public static Quote quote(int itemId, PhantomGameKnowledgeSnapshot knowledge, Rates rates, Set<Integer> eligibleNpcIds)
	{
		if (knowledge == null)
		{
			throw new IllegalArgumentException("Missing accepted loot knowledge.");
		}
		return quote(itemId, knowledge.dropSourcesByItem(), knowledge.spoilSourcesByItem(), knowledge.dropFactsByNpc(), eligibleNpcIds, rates);
	}

	public static Quote quote(int itemId, Map<Integer, List<DropFact>> deathByItem, Map<Integer, List<DropFact>> spoilByItem, Map<Integer, List<DropFact>> deathByNpc, Set<Integer> eligibleNpcIds, Rates rates)
	{
		if ((itemId <= 0) || (itemId == ADENA_ID) || (deathByItem == null) || (spoilByItem == null) || (deathByNpc == null) || (eligibleNpcIds == null) || (rates == null))
		{
			throw new IllegalArgumentException("Unsafe loot fair-value inputs.");
		}
		final List<DropFact> routes = new ArrayList<>(deathByItem.getOrDefault(itemId, List.of()));
		routes.addAll(spoilByItem.getOrDefault(itemId, List.of()));
		routes.sort(Comparator.comparingInt(DropFact::npcId).thenComparing(DropFact::sourceKind).thenComparingInt(DropFact::groupOrdinal).thenComparingInt(DropFact::itemOrdinal));
		Quote best = null;
		for (DropFact route : routes)
		{
			if (!eligibleNpcIds.contains(route.npcId()))
			{
				continue;
			}
			final double itemYield = expectedYield(route, route.sourceKind() == DropSourceKind.DEATH_DROP ? deathByNpc.getOrDefault(route.npcId(), List.of()) : List.of(route), rates);
			if ((itemYield <= 0) || !Double.isFinite(itemYield))
			{
				continue;
			}
			double adenaYield = 0;
			for (DropFact adena : deathByNpc.getOrDefault(route.npcId(), List.of()))
			{
				if ((adena.itemId() == ADENA_ID) && (adena.sourceKind() == DropSourceKind.DEATH_DROP))
				{
					adenaYield += expectedYield(adena, deathByNpc.get(route.npcId()), rates);
				}
			}
			if ((adenaYield <= 0) || !Double.isFinite(adenaYield))
			{
				continue;
			}
			final double value = adenaYield / itemYield;
			if (!Double.isFinite(value) || (value <= 0) || (value > MAX_VALUE))
			{
				continue;
			}
			if ((best == null) || (value < best.fairValue()))
			{
				best = new Quote(itemId, route.npcId(), route.sourceKind(), adenaYield, itemYield, value);
			}
		}
		if (best == null)
		{
			throw new IllegalArgumentException("No eligible paired Adena/item acquisition source.");
		}
		return best;
	}

	private static double expectedYield(DropFact fact, List<DropFact> sameNpcDeath, Rates rates)
	{
		final double chanceRate = fact.sourceKind() == DropSourceKind.SPOIL ? rates.spoilChance() : rates.chanceByItem().getOrDefault(fact.itemId(), rates.deathChance());
		final double amountRate = fact.sourceKind() == DropSourceKind.SPOIL ? rates.spoilAmount() : rates.amountByItem().getOrDefault(fact.itemId(), rates.deathAmount());
		if ((chanceRate <= 0) || (amountRate <= 0) || (fact.maximumCount() <= 0))
		{
			return 0;
		}
		final double chance;
		if (fact.chanceModel() == ChanceModel.UNGROUPED_INDEPENDENT)
		{
			chance = Math.min(100, fact.rawItemChance() * chanceRate) / 100;
		}
		else if ((fact.chanceModel() == ChanceModel.GROUP_CUMULATIVE) && (sameNpcDeath != null))
		{
			if (chanceRate == 1)
			{
				double accumulated = 0;
				double earlierFail = 1;
				double current = 0;
				for (DropFact sibling : sameNpcDeath.stream().filter(candidate -> (candidate.sourceKind() == DropSourceKind.DEATH_DROP) && (candidate.groupOrdinal() == fact.groupOrdinal()) && (candidate.chanceModel() == ChanceModel.GROUP_CUMULATIVE)).sorted(Comparator.comparingInt(DropFact::itemOrdinal)).toList())
				{
					accumulated += sibling.rawItemChance();
					final double siblingChance = Math.min(100, accumulated * (sibling.rawGroupChance() / 100)) / 100;
					if (sibling.itemOrdinal() == fact.itemOrdinal())
					{
						current = earlierFail * siblingChance;
						break;
					}
					earlierFail *= 1 - siblingChance;
				}
				chance = current;
			}
			else
			{
				// Stock grouped custom-rate path checks each line independently; occurrence caps can reduce realized yield.
				chance = Math.min(100, fact.rawItemChance() * (fact.rawGroupChance() / 100) * chanceRate) / 100;
			}
		}
		else
		{
			return 0;
		}
		final double meanCount = (fact.minimumCount() + (double) fact.maximumCount()) / 2;
		final double expected = chance * meanCount * amountRate;
		return Double.isFinite(expected) && (expected > 0) && (expected <= MAX_VALUE) ? expected : 0;
	}

	public record Rates(double deathChance, double deathAmount, double spoilChance, double spoilAmount, Map<Integer, Double> chanceByItem, Map<Integer, Double> amountByItem)
	{
		public Rates
		{
			if (!positive(deathChance) || !positive(deathAmount) || !positive(spoilChance) || !positive(spoilAmount) || (chanceByItem == null) || (amountByItem == null))
			{
				throw new IllegalArgumentException("Unsafe H5 loot rates.");
			}
			chanceByItem = Map.copyOf(chanceByItem);
			amountByItem = Map.copyOf(amountByItem);
			if (!chanceByItem.values().stream().allMatch(PhantomRateAwareLootFairValue::nonnegative) || !amountByItem.values().stream().allMatch(PhantomRateAwareLootFairValue::nonnegative))
			{
				throw new IllegalArgumentException("Unsafe H5 item-specific loot rates.");
			}
		}

		public static Rates capture()
		{
			return new Rates(RatesConfig.RATE_DEATH_DROP_CHANCE_MULTIPLIER, RatesConfig.RATE_DEATH_DROP_AMOUNT_MULTIPLIER, RatesConfig.RATE_SPOIL_DROP_CHANCE_MULTIPLIER, RatesConfig.RATE_SPOIL_DROP_AMOUNT_MULTIPLIER, convert(RatesConfig.RATE_DROP_CHANCE_BY_ID), convert(RatesConfig.RATE_DROP_AMOUNT_BY_ID));
		}
	}

	private static boolean positive(double value)
	{
		return Double.isFinite(value) && (value > 0) && (value <= 1000);
	}

	private static boolean nonnegative(double value)
	{
		return Double.isFinite(value) && (value >= 0) && (value <= 1000);
	}

	private static Map<Integer, Double> convert(Map<Integer, Float> input)
	{
		if (input == null)
		{
			throw new IllegalArgumentException("Uninitialized H5 item rates.");
		}
		final Map<Integer, Double> copy = new HashMap<>();
		input.forEach((id, rate) -> copy.put(id, rate.doubleValue()));
		return Map.copyOf(copy);
	}

	public record Quote(int itemId, int npcId, DropSourceKind source, double adenaPerKill, double itemPerKill, double fairValue)
	{
	}
}
