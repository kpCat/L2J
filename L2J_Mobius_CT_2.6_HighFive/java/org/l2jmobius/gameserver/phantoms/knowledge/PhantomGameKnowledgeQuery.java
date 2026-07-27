/*
 * Copyright (c) 2013 L2jMobius
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
 * IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.l2jmobius.gameserver.phantoms.knowledge;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeMetrics.QueryCategory;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.ClassCapabilityFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.ContentRequirementFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.DropFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.ItemFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.KnowledgePage;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.ManorFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.NpcFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.PageRequest;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.RecipeFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.SpawnAreaFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.SpawnAreaSummary;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.SpawnFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.TargetFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.TargetQuery;

/**
 * Deterministic map/index/page-only queries over one immutable generation.
 */
public final class PhantomGameKnowledgeQuery
{
	private final PhantomGameKnowledgeSnapshot _snapshot;
	private final PhantomGameKnowledgeMetrics _metrics;

	PhantomGameKnowledgeQuery(PhantomGameKnowledgeSnapshot snapshot, PhantomGameKnowledgeMetrics metrics)
	{
		_snapshot = Objects.requireNonNull(snapshot, "snapshot");
		_metrics = Objects.requireNonNull(metrics, "metrics");
	}

	public Optional<ItemFact> findItem(int itemId)
	{
		_metrics.recordQuery(QueryCategory.ITEM);
		return Optional.ofNullable(_snapshot.itemById().get(itemId));
	}

	public Optional<NpcFact> findNpc(int npcId)
	{
		_metrics.recordQuery(QueryCategory.NPC);
		return Optional.ofNullable(_snapshot.npcById().get(npcId));
	}

	public KnowledgePage<DropFact> dropSources(int itemId, PageRequest page)
	{
		_metrics.recordQuery(QueryCategory.DROP);
		return page(_snapshot.dropSourcesByItem().getOrDefault(itemId, List.of()), page, DropFact::stableKey);
	}

	public KnowledgePage<DropFact> spoilSources(int itemId, PageRequest page)
	{
		_metrics.recordQuery(QueryCategory.SPOIL);
		return page(_snapshot.spoilSourcesByItem().getOrDefault(itemId, List.of()), page, DropFact::stableKey);
	}

	public KnowledgePage<ManorFact> manorSources(int itemId, PageRequest page)
	{
		_metrics.recordQuery(QueryCategory.MANOR);
		return page(_snapshot.manorFactsByItem().getOrDefault(itemId, List.of()), page, ManorFact::stableKey);
	}

	public KnowledgePage<SpawnAreaSummary> spawnAreas(int npcId, PageRequest page)
	{
		_metrics.recordQuery(QueryCategory.SPAWN_AREA);
		final KnowledgePage<SpawnAreaFact> facts = page(_snapshot.spawnAreasByNpc().getOrDefault(npcId, List.of()), page, SpawnAreaFact::stableKey);
		return new KnowledgePage<>(facts.values().stream().map(SpawnAreaSummary::from).toList(), facts.nextCursor(), facts.hasMore());
	}

	public KnowledgePage<SpawnFact> spawnFacts(int npcId, PageRequest page)
	{
		_metrics.recordQuery(QueryCategory.SPAWN_FACT);
		return page(_snapshot.spawnFactsByNpc().getOrDefault(npcId, List.of()), page, SpawnFact::stableKey);
	}

	public Optional<RecipeFact> findRecipeByListId(int recipeListId)
	{
		_metrics.recordQuery(QueryCategory.RECIPE);
		return Optional.ofNullable(_snapshot.recipeByListId().get(recipeListId));
	}

	public KnowledgePage<RecipeFact> recipesProducing(int itemId, PageRequest page)
	{
		_metrics.recordQuery(QueryCategory.RECIPE_PRODUCT);
		return page(_snapshot.recipesByProduct().getOrDefault(itemId, List.of()), page, RecipeFact::stableKey);
	}

	public KnowledgePage<RecipeFact> recipesUsing(int ingredientItemId, PageRequest page)
	{
		_metrics.recordQuery(QueryCategory.RECIPE_INGREDIENT);
		return page(_snapshot.recipesByIngredient().getOrDefault(ingredientItemId, List.of()), page, RecipeFact::stableKey);
	}

	public KnowledgePage<ClassCapabilityFact> classCapabilities(int classId, PageRequest page)
	{
		_metrics.recordQuery(QueryCategory.CLASS);
		return page(_snapshot.capabilitiesByClassId().getOrDefault(classId, List.of()), page, ClassCapabilityFact::stableKey);
	}

	public KnowledgePage<ClassCapabilityFact> classesForCapability(String capabilityKey, int minimumRank, PageRequest page)
	{
		if ((capabilityKey == null) || capabilityKey.isBlank() || (minimumRank < 1) || (minimumRank > 1000))
		{
			_metrics.recordRejectedQuery();
			throw new IllegalArgumentException("Invalid capability query.");
		}
		_metrics.recordQuery(QueryCategory.CAPABILITY);
		final List<ClassCapabilityFact> facts = _snapshot.classesByCapability().getOrDefault(capabilityKey, List.of());
		return filteredPage(facts, fact -> fact.rank() >= minimumRank, page, ClassCapabilityFact::stableKey);
	}

	public Optional<ContentRequirementFact> content(String contentId)
	{
		_metrics.recordQuery(QueryCategory.CONTENT);
		return Optional.ofNullable(_snapshot.contentById().get(contentId));
	}

	public KnowledgePage<ContentRequirementFact> contentsRequiring(String capabilityKey, PageRequest page)
	{
		if ((capabilityKey == null) || capabilityKey.isBlank())
		{
			_metrics.recordRejectedQuery();
			throw new IllegalArgumentException("Invalid content capability query.");
		}
		_metrics.recordQuery(QueryCategory.CONTENT_CAPABILITY);
		return page(_snapshot.contentByCapability().getOrDefault(capabilityKey, List.of()), page, ContentRequirementFact::stableKey);
	}

	public KnowledgePage<TargetFact> suitableTargets(TargetQuery query)
	{
		Objects.requireNonNull(query, "query");
		if ((query.page().limit() > _snapshot.policy().maximumQueryPageSize()) || ((query.maximumLevel() - query.minimumLevel()) > _snapshot.policy().maximumTargetLevelWidth()))
		{
			_metrics.recordRejectedQuery();
			throw new IllegalArgumentException("Target query exceeds knowledge policy.");
		}
		_metrics.recordQuery(QueryCategory.TARGET);
		final Set<Integer> topologyIds = ids(query.topologyNodeId() != null, query.topologyNodeId() == null ? null : _snapshot.npcsByTopologyNode().get(query.topologyNodeId()));
		final Set<Integer> mapIds = ids(query.mapRegionLocId() != null, query.mapRegionLocId() == null ? null : _snapshot.npcsByMapRegion().get(query.mapRegionLocId()));
		final Set<Integer> dropIds = sourceNpcIds(query.dropsItemId() != null, query.dropsItemId() == null ? null : _snapshot.dropSourcesByItem().get(query.dropsItemId()));
		final Set<Integer> spoilIds = sourceNpcIds(query.spoilsItemId() != null, query.spoilsItemId() == null ? null : _snapshot.spoilSourcesByItem().get(query.spoilsItemId()));
		if (isRequestedEmpty(topologyIds) || isRequestedEmpty(mapIds) || isRequestedEmpty(dropIds) || isRequestedEmpty(spoilIds))
		{
			final KnowledgePage<TargetFact> empty = page(List.of(), query.page(), fact -> fact.stableKey(query.preferredLevel()));
			_metrics.recordTargetCandidates(0, 0);
			return empty;
		}
		final ArrayList<TargetFact> candidates = new ArrayList<>();
		int considered = 0;
		for (int level = query.minimumLevel(); level <= query.maximumLevel(); level++)
		{
			for (NpcFact npc : _snapshot.npcsByLevel().getOrDefault(level, List.of()))
			{
				considered++;
				if ((query.requireAttackable() && !npc.attackable()) || (query.requireTargetable() && !npc.targetable()) || ((query.canBeSown() != null) && (npc.canBeSown() != query.canBeSown())) || (!query.allowedKinds().isEmpty() && !query.allowedKinds().contains(npc.kind())) || ((topologyIds != null) && !topologyIds.contains(npc.npcId())) || ((mapIds != null) && !mapIds.contains(npc.npcId())) || ((dropIds != null) && !dropIds.contains(npc.npcId())) || ((spoilIds != null) && !spoilIds.contains(npc.npcId())))
				{
					continue;
				}
				final List<SpawnAreaFact> areas = _snapshot.spawnAreasByNpc().getOrDefault(npc.npcId(), List.of());
				final int summaryCount = Math.min(areas.size(), Math.min(64, _snapshot.policy().maximumTopologyNodeResults()));
				final List<SpawnAreaSummary> summaries = areas.subList(0, summaryCount).stream().map(SpawnAreaSummary::from).toList();
				candidates.add(new TargetFact(npc, areas.size(), summaries, areas.size() > summaryCount));
			}
		}
		candidates.sort(Comparator.comparingInt((TargetFact fact) -> query.preferredLevel() == null ? 0 : Math.abs(fact.npc().level() - query.preferredLevel())).thenComparingInt(fact -> fact.npc().level()).thenComparingInt(fact -> fact.npc().npcId()));
		final KnowledgePage<TargetFact> result = page(candidates, query.page(), fact -> fact.stableKey(query.preferredLevel()));
		_metrics.recordTargetCandidates(considered, result.values().size());
		return result;
	}

	private static boolean isRequestedEmpty(Set<Integer> values)
	{
		return (values != null) && values.isEmpty();
	}

	private static Set<Integer> ids(boolean requested, List<NpcFact> values)
	{
		if (!requested)
		{
			return null;
		}
		final HashSet<Integer> result = new HashSet<>();
		for (NpcFact value : values == null ? List.<NpcFact>of() : values)
		{
			result.add(value.npcId());
		}
		return Set.copyOf(result);
	}

	private static Set<Integer> sourceNpcIds(boolean requested, List<DropFact> values)
	{
		if (!requested)
		{
			return null;
		}
		final HashSet<Integer> result = new HashSet<>();
		for (DropFact value : values == null ? List.<DropFact>of() : values)
		{
			result.add(value.npcId());
		}
		return Set.copyOf(result);
	}

	private <T> KnowledgePage<T> filteredPage(List<T> source, Predicate<T> filter, PageRequest request, Function<T, String> key)
	{
		final ArrayList<T> values = new ArrayList<>();
		for (T value : source)
		{
			if (filter.test(value))
			{
				values.add(value);
			}
		}
		return page(values, request, key);
	}

	private <T> KnowledgePage<T> page(List<T> source, PageRequest request, Function<T, String> key)
	{
		Objects.requireNonNull(request, "request");
		if (request.limit() > _snapshot.policy().maximumQueryPageSize())
		{
			_metrics.recordRejectedQuery();
			throw new IllegalArgumentException("Knowledge page exceeds policy.");
		}
		int start = 0;
		if (request.afterKey() != null)
		{
			int low = 0;
			int high = source.size();
			while (low < high)
			{
				final int middle = (low + high) >>> 1;
				if (key.apply(source.get(middle)).compareTo(request.afterKey()) <= 0)
				{
					low = middle + 1;
				}
				else
				{
					high = middle;
				}
			}
			start = low;
		}
		final int end = Math.min(source.size(), start + request.limit());
		final List<T> values = List.copyOf(source.subList(start, end));
		final boolean hasMore = end < source.size();
		final String nextCursor = hasMore && !values.isEmpty() ? key.apply(values.getLast()) : null;
		_metrics.recordPage();
		return new KnowledgePage<>(values, nextCursor, hasMore);
	}

	public PhantomGameKnowledgeSnapshot snapshot()
	{
		return _snapshot;
	}
}
