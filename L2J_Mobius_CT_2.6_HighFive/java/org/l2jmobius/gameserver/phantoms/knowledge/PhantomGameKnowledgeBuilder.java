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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomCuratedKnowledgeParser.CuratedData;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeBackend.BackendData;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.CapabilityRequirement;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.ClassCapabilityFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.ClassIntrinsicFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.ContentKind;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.ContentRequirementFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.DropFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.IngredientFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.ItemFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.ManorFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.NpcFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.NpcKind;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.RecipeFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.SkillEvidence;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.SpawnAreaFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.SpawnFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.SpawnPointKind;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyQuery;

/**
 * Candidate-validate-index builder. It performs every source read before one
 * immutable snapshot is published by the service.
 */
public final class PhantomGameKnowledgeBuilder
{
	public static final Set<String> REQUIRED_CAPABILITIES = Set.of(
		"combat.tank",
		"combat.heal",
		"combat.resurrection",
		"combat.buff",
		"combat.debuff",
		"combat.crowd_control",
		"combat.melee_damage",
		"combat.ranged_physical_damage",
		"combat.ranged_magic_damage",
		"combat.summon",
		"profession.spoil",
		"profession.craft");

	private final PhantomGameKnowledgeBackend _backend;
	private final PhantomStaticManorParser _manorParser;
	private final PhantomCuratedKnowledgeParser _curatedParser;
	private final PhantomTopologyQuery _topology;
	private final PhantomGameKnowledgePolicy _policy;

	public PhantomGameKnowledgeBuilder(PhantomGameKnowledgeBackend backend, PhantomStaticManorParser manorParser, PhantomCuratedKnowledgeParser curatedParser, PhantomTopologyQuery topology, PhantomGameKnowledgePolicy policy)
	{
		_backend = Objects.requireNonNull(backend, "backend");
		_manorParser = Objects.requireNonNull(manorParser, "manorParser");
		_curatedParser = Objects.requireNonNull(curatedParser, "curatedParser");
		_topology = Objects.requireNonNull(topology, "topology");
		_policy = Objects.requireNonNull(policy, "policy");
	}

	public PhantomGameKnowledgeSnapshot build()
	{
		final BackendData loaded = _backend.load(_policy);
		final List<ManorFact> manorFacts = _manorParser.parse();
		final CuratedData curated = _curatedParser.parse();
		validateCounts(loaded, manorFacts, curated);
		final List<SpawnFact> mappedSpawns = mapTopology(loaded.spawns());
		final List<SpawnAreaFact> spawnAreas = aggregateSpawnAreas(mappedSpawns);
		validateReferences(loaded, mappedSpawns, manorFacts, curated);
		validateCuratedCoverage(loaded, curated);
		return new PhantomGameKnowledgeSnapshot(curated.datasetId(), curated.datasetVersion(), _topology.snapshot().canonicalHash(), _policy, loaded.items(), loaded.npcs(), loaded.drops(), mappedSpawns, spawnAreas, loaded.recipes(), manorFacts, loaded.classes(), curated.classCapabilities(), curated.contentRequirements());
	}

	private void validateCounts(BackendData loaded, List<ManorFact> manorFacts, CuratedData curated)
	{
		if ((loaded.items().size() > _policy.maximumItems()) || (loaded.npcs().size() > _policy.maximumNpcTemplates()) || (loaded.drops().size() > _policy.maximumDropSpoilFacts()) || (loaded.spawns().size() > _policy.maximumSpawnFacts()) || (loaded.recipes().size() > _policy.maximumRecipes()) || (manorFacts.size() > _policy.maximumManorFacts()) || (curated.classCapabilities().size() > _policy.maximumClassCapabilityFacts()) || (curated.contentRequirements().size() > _policy.maximumContentEntries()))
		{
			throw failure("count", "Authoritative source count exceeds Game Knowledge policy.");
		}
		final long ingredients = loaded.recipes().stream().mapToLong(recipe -> recipe.ingredients().size()).sum();
		if (ingredients > _policy.maximumRecipeIngredients())
		{
			throw failure("count", "Authoritative recipe ingredient count exceeds policy.");
		}
	}

	private List<SpawnFact> mapTopology(List<SpawnFact> source)
	{
		final ArrayList<SpawnFact> result = new ArrayList<>(source.size());
		for (SpawnFact fact : source)
		{
			if ((fact.pointKind() == SpawnPointKind.EXACT) && isWithinWorldBounds(fact))
			{
				final String nodeId = _topology.mostSpecificNode(new PhantomTopologyPoint(fact.x(), fact.y(), fact.z(), fact.instanceId())).map(node -> node.id()).orElse(null);
				result.add(fact.withTopology(nodeId));
			}
			else
			{
				result.add(fact);
			}
		}
		result.sort(Comparator.comparingInt(SpawnFact::npcId).thenComparingInt(SpawnFact::spawnOrdinal));
		return List.copyOf(result);
	}

	private static boolean isWithinWorldBounds(SpawnFact fact)
	{
		return (fact.x() >= World.WORLD_X_MIN) && (fact.x() <= World.WORLD_X_MAX) && (fact.y() >= World.WORLD_Y_MIN) && (fact.y() <= World.WORLD_Y_MAX) && (fact.z() >= World.WORLD_Z_MIN) && (fact.z() <= World.WORLD_Z_MAX);
	}

	private List<SpawnAreaFact> aggregateSpawnAreas(List<SpawnFact> spawns)
	{
		final HashMap<AreaKey, AreaAccumulator> mutable = new HashMap<>();
		for (SpawnFact spawn : spawns)
		{
			mutable.computeIfAbsent(new AreaKey(spawn.npcId(), spawn.instanceId(), spawn.topologyNodeId(), spawn.mapRegionLocId()), _ -> new AreaAccumulator()).add(spawn, _policy.maximumSpawnSamples());
		}
		final ArrayList<SpawnAreaFact> result = new ArrayList<>(mutable.size());
		for (Map.Entry<AreaKey, AreaAccumulator> entry : mutable.entrySet())
		{
			final AreaKey key = entry.getKey();
			final AreaAccumulator value = entry.getValue();
			final PhantomGameKnowledgeAuthority authority = key._topologyNodeId == null ? PhantomGameKnowledgeAuthority.SERVER_LOADER_FACT : PhantomGameKnowledgeAuthority.TOPOLOGY_SNAPSHOT_FACT;
			result.add(new SpawnAreaFact(key._npcId, key._instanceId, key._topologyNodeId, key._mapRegionLocId, value._count, value._amount, value._representativePoints, authority));
		}
		result.sort(Comparator.comparing(SpawnAreaFact::stableKey));
		return List.copyOf(result);
	}

	private void validateReferences(BackendData loaded, List<SpawnFact> spawns, List<ManorFact> manorFacts, CuratedData curated)
	{
		final Map<Integer, ItemFact> items = unique(loaded.items(), ItemFact::itemId, "item");
		final Map<Integer, NpcFact> npcs = unique(loaded.npcs(), NpcFact::npcId, "NPC");
		final Map<Integer, ClassIntrinsicFact> classes = unique(loaded.classes(), ClassIntrinsicFact::classId, "class");
		unique(spawns, SpawnFact::stableKey, "spawn");
		final HashSet<String> dropKeys = new HashSet<>();
		for (DropFact fact : loaded.drops())
		{
			requireReference(npcs, fact.npcId(), "drop NPC");
			requireReference(items, fact.itemId(), "drop item");
			if (!dropKeys.add(fact.stableKey()))
			{
				throw failure("duplicate", "Duplicate drop/spoil fact.");
			}
		}
		for (SpawnFact fact : spawns)
		{
			requireReference(npcs, fact.npcId(), "spawn NPC");
			if ((fact.topologyNodeId() != null) && !_topology.snapshot().nodeById().containsKey(fact.topologyNodeId()))
			{
				throw failure("topology-reference", "Mapped spawn refers to a missing topology node.");
			}
		}
		final HashSet<Integer> recipeLists = new HashSet<>();
		for (RecipeFact fact : loaded.recipes())
		{
			requireReference(items, fact.recipeItemId(), "recipe item");
			requireReference(items, fact.productItemId(), "recipe product");
			if ((fact.rareProductItemId() > 0) && !items.containsKey(fact.rareProductItemId()))
			{
				throw failure("reference", "Recipe rare product reference is missing.");
			}
			if (!recipeLists.add(fact.recipeListId()))
			{
				throw failure("duplicate", "Duplicate recipe list identity.");
			}
			for (IngredientFact ingredient : fact.ingredients())
			{
				requireReference(items, ingredient.itemId(), "recipe ingredient");
			}
		}
		unique(loaded.recipes(), RecipeFact::recipeListId, "recipe list");
		for (ManorFact fact : manorFacts)
		{
			for (int itemId : List.of(fact.seedItemId(), fact.cropItemId(), fact.matureItemId(), fact.reward1ItemId(), fact.reward2ItemId()))
			{
				requireReference(items, itemId, "manor item");
			}
		}
		unique(manorFacts, ManorFact::stableKey, "manor");
		for (ClassIntrinsicFact fact : loaded.classes())
		{
			if ((fact.parentClassId() != null) && !classes.containsKey(fact.parentClassId()))
			{
				throw failure("reference", "Class parent reference is missing.");
			}
			if (!loaded.completeClassSkills().containsKey(fact.classId()))
			{
				throw failure("reference", "Complete class skill evidence is missing.");
			}
		}
		final HashSet<String> capabilityKeys = new HashSet<>();
		final HashSet<String> capabilityClassKeys = new HashSet<>();
		for (ClassCapabilityFact fact : curated.classCapabilities())
		{
			requireReference(classes, fact.classId(), "capability class");
			final Set<SkillEvidence> completeTree = Set.copyOf(loaded.completeClassSkills().get(fact.classId()));
			if (!completeTree.containsAll(fact.evidenceSkills()))
			{
				throw failure("evidence", "Capability skill evidence is absent from the complete class skill tree.");
			}
			if (!capabilityClassKeys.add(fact.classId() + ":" + fact.capabilityKey()))
			{
				throw failure("duplicate", "Duplicate class capability identity.");
			}
			if ((new HashSet<>(fact.evidenceSkills()).size() != fact.evidenceSkills().size()) || (new HashSet<>(fact.sourceRefs()).size() != fact.sourceRefs().size()))
			{
				throw failure("duplicate", "Duplicate class capability evidence.");
			}
			capabilityKeys.add(fact.capabilityKey());
		}
		unique(curated.contentRequirements(), ContentRequirementFact::contentId, "content");
		for (ContentRequirementFact content : curated.contentRequirements())
		{
			if ((content.npcId() != null) && !npcs.containsKey(content.npcId()))
			{
				throw failure("reference", "Content NPC reference is missing.");
			}
			if ((content.topologyNodeId() != null) && !_topology.snapshot().nodeById().containsKey(content.topologyNodeId()))
			{
				throw failure("topology-reference", "Content topology node reference is missing.");
			}
			if ((content.topologyAnchorId() != null) && !_topology.snapshot().anchorById().containsKey(content.topologyAnchorId()))
			{
				throw failure("topology-reference", "Content topology anchor reference is missing.");
			}
			if ((new HashSet<>(content.sourceRefs()).size() != content.sourceRefs().size()))
			{
				throw failure("duplicate", "Duplicate content evidence.");
			}
			final HashSet<String> requirementKeys = new HashSet<>();
			for (CapabilityRequirement requirement : content.requirements())
			{
				if (!capabilityKeys.contains(requirement.capabilityKey()))
				{
					throw failure("reference", "Content capability reference is missing.");
				}
				if (!requirementKeys.add(requirement.capabilityKey()))
				{
					throw failure("duplicate", "Duplicate content capability requirement.");
				}
			}
		}
	}

	private void validateCuratedCoverage(BackendData loaded, CuratedData curated)
	{
		final HashSet<String> keys = new HashSet<>();
		final HashMap<String, List<ClassCapabilityFact>> byKey = new HashMap<>();
		final HashMap<Integer, List<ClassCapabilityFact>> byClass = new HashMap<>();
		for (ClassCapabilityFact fact : curated.classCapabilities())
		{
			keys.add(fact.capabilityKey());
			byKey.computeIfAbsent(fact.capabilityKey(), _ -> new ArrayList<>()).add(fact);
			byClass.computeIfAbsent(fact.classId(), _ -> new ArrayList<>()).add(fact);
		}
		if (!keys.containsAll(REQUIRED_CAPABILITIES))
		{
			throw failure("coverage", "Required curated capability coverage is incomplete.");
		}
		final HashSet<Integer> parentIds = new HashSet<>();
		for (ClassIntrinsicFact fact : loaded.classes())
		{
			if (fact.parentClassId() != null)
			{
				parentIds.add(fact.parentClassId());
			}
		}
		for (ClassIntrinsicFact fact : loaded.classes())
		{
			if (!parentIds.contains(fact.classId()))
			{
				final boolean covered = byClass.getOrDefault(fact.classId(), List.of()).stream().anyMatch(capability -> capability.capabilityKey().startsWith("combat.") || capability.capabilityKey().startsWith("profession."));
				if (!covered)
				{
					throw failure("coverage", "Terminal playable class capability coverage is incomplete.");
				}
			}
		}
		boolean rift = false;
		boolean raid = false;
		boolean epic = false;
		final Map<Integer, NpcFact> npcs = loaded.npcs().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(NpcFact::npcId, java.util.function.Function.identity()));
		for (ContentRequirementFact content : curated.contentRequirements())
		{
			for (CapabilityRequirement requirement : content.requirements())
			{
				final long satisfyingClasses = byKey.getOrDefault(requirement.capabilityKey(), List.of()).stream().filter(fact -> fact.rank() >= requirement.minimumRank()).map(ClassCapabilityFact::classId).distinct().count();
				if (satisfyingClasses < requirement.minimumCount())
				{
					throw failure("satisfiability", "Content capability requirement is not satisfiable.");
				}
			}
			rift |= content.contentKind() == ContentKind.RIFT;
			raid |= (content.contentKind() == ContentKind.RAID) && (content.npcId() != null) && (npcs.get(content.npcId()).kind() == NpcKind.RAID_BOSS);
			epic |= (content.contentKind() == ContentKind.EPIC) && (content.npcId() != null) && (npcs.get(content.npcId()).kind() == NpcKind.GRAND_BOSS);
		}
		if (!rift || !raid || !epic)
		{
			throw failure("coverage", "Rift, RaidBoss and GrandBoss content coverage is required.");
		}
	}

	private static <K, V> Map<K, V> unique(List<V> values, java.util.function.Function<V, K> key, String label)
	{
		final HashMap<K, V> result = new HashMap<>();
		for (V value : values)
		{
			if (result.put(key.apply(value), value) != null)
			{
				throw failure("duplicate", "Duplicate " + label + " fact.");
			}
		}
		return Map.copyOf(result);
	}

	private static <K, V> void requireReference(Map<K, V> values, K key, String label)
	{
		if (!values.containsKey(key))
		{
			throw failure("reference", "Missing " + label + " reference.");
		}
	}

	private static PhantomGameKnowledgeValidationException failure(String category, String message)
	{
		return new PhantomGameKnowledgeValidationException(category, message);
	}

	private record AreaKey(int _npcId, int _instanceId, String _topologyNodeId, Integer _mapRegionLocId)
	{
	}

	private static final class AreaAccumulator
	{
		private int _count;
		private long _amount;
		private final ArrayList<SpawnFact> _representativePoints = new ArrayList<>();

		private void add(SpawnFact fact, int maximumSamples)
		{
			_count++;
			_amount = Math.addExact(_amount, fact.amount());
			if (_representativePoints.size() < maximumSamples)
			{
				_representativePoints.add(fact);
			}
		}
	}
}
