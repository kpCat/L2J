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

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.CapabilityRequirement;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.ClassCapabilityFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.ClassIntrinsicFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.ContentRequirementFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.DropFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.DropSourceKind;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.IngredientFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.ItemFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.ManorFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.NpcFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.RecipeFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.SkillEvidence;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.SpawnAreaFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.SpawnFact;

/**
 * One canonical immutable Game Knowledge generation and all complete indexes.
 */
public final class PhantomGameKnowledgeSnapshot
{
	public static final int SCHEMA_VERSION = 1;
	public static final long GENERATION = 1;

	private static final Comparator<DropFact> DROP_ORDER = Comparator.comparingInt(DropFact::itemId).thenComparingInt(DropFact::npcId).thenComparing(DropFact::sourceKind).thenComparingInt(DropFact::groupOrdinal).thenComparingInt(DropFact::itemOrdinal);
	private static final Comparator<DropFact> NPC_DROP_ORDER = Comparator.comparing(DropFact::sourceKind).thenComparingInt(DropFact::groupOrdinal).thenComparingInt(DropFact::itemOrdinal).thenComparingInt(DropFact::itemId);
	private static final Comparator<RecipeFact> RECIPE_ORDER = Comparator.comparingInt(RecipeFact::recipeListId);
	private static final Comparator<ClassCapabilityFact> CAPABILITY_ORDER = Comparator.comparing(ClassCapabilityFact::stableKey);

	private final String _datasetId;
	private final int _datasetVersion;
	private final String _topologyHash;
	private final PhantomGameKnowledgePolicy _policy;
	private final List<ItemFact> _items;
	private final List<NpcFact> _npcs;
	private final List<DropFact> _dropSpoilFacts;
	private final List<SpawnFact> _spawnFacts;
	private final List<SpawnAreaFact> _spawnAreas;
	private final List<RecipeFact> _recipes;
	private final List<ManorFact> _manorFacts;
	private final List<ClassIntrinsicFact> _classFacts;
	private final List<ClassCapabilityFact> _classCapabilities;
	private final List<ContentRequirementFact> _contentRequirements;
	private final Map<Integer, ItemFact> _itemById;
	private final Map<Integer, NpcFact> _npcById;
	private final Map<Integer, List<DropFact>> _dropSourcesByItem;
	private final Map<Integer, List<DropFact>> _spoilSourcesByItem;
	private final Map<Integer, List<ManorFact>> _manorFactsByItem;
	private final Map<Integer, List<DropFact>> _dropFactsByNpc;
	private final Map<Integer, List<DropFact>> _spoilFactsByNpc;
	private final Map<Integer, List<SpawnFact>> _spawnFactsByNpc;
	private final Map<Integer, List<SpawnAreaFact>> _spawnAreasByNpc;
	private final Map<String, List<NpcFact>> _npcsByTopologyNode;
	private final Map<Integer, List<NpcFact>> _npcsByMapRegion;
	private final Map<Integer, List<NpcFact>> _npcsByLevel;
	private final Map<Integer, RecipeFact> _recipeByListId;
	private final Map<Integer, List<RecipeFact>> _recipesByProduct;
	private final Map<Integer, List<RecipeFact>> _recipesByIngredient;
	private final Map<Integer, ClassIntrinsicFact> _classFactsByClassId;
	private final Map<Integer, List<ClassCapabilityFact>> _capabilitiesByClassId;
	private final Map<String, List<ClassCapabilityFact>> _classesByCapability;
	private final Map<String, ContentRequirementFact> _contentById;
	private final Map<String, List<ContentRequirementFact>> _contentByCapability;
	private final String _itemsHash;
	private final String _npcDropSpoilHash;
	private final String _spawnHash;
	private final String _recipeHash;
	private final String _manorHash;
	private final String _classCapabilityHash;
	private final String _contentRequirementHash;
	private final String _combinedHash;
	private final Hashes _hashes;
	private final Counts _counts;

	PhantomGameKnowledgeSnapshot(String datasetId, int datasetVersion, String topologyHash, PhantomGameKnowledgePolicy policy, List<ItemFact> items, List<NpcFact> npcs, List<DropFact> dropSpoilFacts, List<SpawnFact> spawnFacts, List<SpawnAreaFact> spawnAreas, List<RecipeFact> recipes, List<ManorFact> manorFacts, List<ClassIntrinsicFact> classFacts, List<ClassCapabilityFact> classCapabilities, List<ContentRequirementFact> contentRequirements)
	{
		_datasetId = datasetId;
		_datasetVersion = datasetVersion;
		_topologyHash = topologyHash;
		_policy = policy;
		_items = sorted(items, Comparator.comparingInt(ItemFact::itemId));
		_npcs = sorted(npcs, Comparator.comparingInt(NpcFact::npcId));
		_dropSpoilFacts = sorted(dropSpoilFacts, Comparator.comparingInt(DropFact::npcId).thenComparing(DropFact::sourceKind).thenComparingInt(DropFact::groupOrdinal).thenComparingInt(DropFact::itemOrdinal).thenComparingInt(DropFact::itemId));
		_spawnFacts = sorted(spawnFacts, Comparator.comparingInt(SpawnFact::npcId).thenComparingInt(SpawnFact::spawnOrdinal));
		_spawnAreas = sorted(spawnAreas, Comparator.comparing(SpawnAreaFact::stableKey));
		_recipes = sorted(recipes, RECIPE_ORDER);
		_manorFacts = sorted(manorFacts, Comparator.comparing(ManorFact::stableKey));
		_classFacts = sorted(classFacts, Comparator.comparingInt(ClassIntrinsicFact::classId));
		_classCapabilities = sorted(classCapabilities, Comparator.comparing(ClassCapabilityFact::stableKey));
		_contentRequirements = sorted(contentRequirements, Comparator.comparing(ContentRequirementFact::contentId));

		_itemById = uniqueMap(_items, ItemFact::itemId);
		_npcById = uniqueMap(_npcs, NpcFact::npcId);
		_dropSourcesByItem = group(_dropSpoilFacts.stream().filter(fact -> fact.sourceKind() == DropSourceKind.DEATH_DROP).toList(), DropFact::itemId, DROP_ORDER);
		_spoilSourcesByItem = group(_dropSpoilFacts.stream().filter(fact -> fact.sourceKind() == DropSourceKind.SPOIL).toList(), DropFact::itemId, DROP_ORDER);
		_manorFactsByItem = buildManorIndex(_manorFacts);
		_dropFactsByNpc = group(_dropSpoilFacts.stream().filter(fact -> fact.sourceKind() == DropSourceKind.DEATH_DROP).toList(), DropFact::npcId, NPC_DROP_ORDER);
		_spoilFactsByNpc = group(_dropSpoilFacts.stream().filter(fact -> fact.sourceKind() == DropSourceKind.SPOIL).toList(), DropFact::npcId, NPC_DROP_ORDER);
		_spawnFactsByNpc = group(_spawnFacts, SpawnFact::npcId, Comparator.comparingInt(SpawnFact::spawnOrdinal));
		_spawnAreasByNpc = group(_spawnAreas, SpawnAreaFact::npcId, Comparator.comparing(SpawnAreaFact::stableKey));
		_npcsByTopologyNode = buildNpcAreaIndex(_spawnAreas, _npcById, true);
		_npcsByMapRegion = buildNpcAreaIndex(_spawnAreas, _npcById, false);
		_npcsByLevel = group(_npcs.stream().filter(NpcFact::attackable).toList(), NpcFact::level, Comparator.comparingInt(NpcFact::npcId));
		_recipeByListId = uniqueMap(_recipes, RecipeFact::recipeListId);
		_recipesByProduct = buildRecipeProductIndex(_recipes);
		_recipesByIngredient = buildRecipeIngredientIndex(_recipes);
		_classFactsByClassId = uniqueMap(_classFacts, ClassIntrinsicFact::classId);
		_capabilitiesByClassId = group(_classCapabilities, ClassCapabilityFact::classId, CAPABILITY_ORDER);
		_classesByCapability = group(_classCapabilities, ClassCapabilityFact::capabilityKey, CAPABILITY_ORDER);
		_contentById = uniqueMap(_contentRequirements, ContentRequirementFact::contentId);
		_contentByCapability = buildContentCapabilityIndex(_contentRequirements);

		_itemsHash = hashItems(_items);
		_npcDropSpoilHash = hashNpcDrops(_npcs, _dropSpoilFacts);
		_spawnHash = hashSpawns(_spawnFacts, _spawnAreas);
		_recipeHash = hashRecipes(_recipes);
		_manorHash = hashManor(_manorFacts);
		_classCapabilityHash = hashClasses(_classFacts, _classCapabilities);
		_contentRequirementHash = hashContents(_contentRequirements);
		_combinedHash = hashCombined();
		_hashes = new Hashes(_itemsHash, _npcDropSpoilHash, _spawnHash, _recipeHash, _manorHash, _classCapabilityHash, _contentRequirementHash, _topologyHash, _combinedHash);
		_counts = new Counts(_items.size(), _npcs.size(), countDrops(DropSourceKind.DEATH_DROP), countDrops(DropSourceKind.SPOIL), _spawnFacts.size(), _spawnAreas.size(), _recipes.size(), _recipes.stream().mapToInt(recipe -> recipe.ingredients().size()).sum(), _manorFacts.size(), _classFacts.size(), _classCapabilities.size(), _contentRequirements.size());
	}

	public static PhantomGameKnowledgeSnapshot empty(String topologyHash)
	{
		return new PhantomGameKnowledgeSnapshot("empty", 1, topologyHash, PhantomGameKnowledgePolicy.productionDefaults(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
	}

	private int countDrops(DropSourceKind kind)
	{
		return (int) _dropSpoilFacts.stream().filter(fact -> fact.sourceKind() == kind).count();
	}

	private static <T> List<T> sorted(List<T> source, Comparator<? super T> comparator)
	{
		final ArrayList<T> copy = new ArrayList<>(source);
		copy.sort(comparator);
		return List.copyOf(copy);
	}

	private static <K, V> Map<K, V> uniqueMap(List<V> values, java.util.function.Function<V, K> key)
	{
		final LinkedHashMap<K, V> result = new LinkedHashMap<>();
		for (V value : values)
		{
			if (result.put(key.apply(value), value) != null)
			{
				throw new PhantomGameKnowledgeValidationException("duplicate", "Duplicate fact identity.");
			}
		}
		return Map.copyOf(result);
	}

	private static <K, V> Map<K, List<V>> group(List<V> values, java.util.function.Function<V, K> key, Comparator<? super V> comparator)
	{
		final HashMap<K, ArrayList<V>> mutable = new HashMap<>();
		for (V value : values)
		{
			mutable.computeIfAbsent(key.apply(value), _ -> new ArrayList<>()).add(value);
		}
		final HashMap<K, List<V>> result = new HashMap<>();
		for (Map.Entry<K, ArrayList<V>> entry : mutable.entrySet())
		{
			entry.getValue().sort(comparator);
			result.put(entry.getKey(), List.copyOf(entry.getValue()));
		}
		return Map.copyOf(result);
	}

	private static Map<Integer, List<ManorFact>> buildManorIndex(List<ManorFact> facts)
	{
		final HashMap<Integer, ArrayList<ManorFact>> mutable = new HashMap<>();
		for (ManorFact fact : facts)
		{
			for (int itemId : List.of(fact.seedItemId(), fact.cropItemId(), fact.matureItemId(), fact.reward1ItemId(), fact.reward2ItemId()))
			{
				final ArrayList<ManorFact> values = mutable.computeIfAbsent(itemId, _ -> new ArrayList<>());
				if (!values.contains(fact))
				{
					values.add(fact);
				}
			}
		}
		return freeze(mutable, Comparator.comparing(ManorFact::stableKey));
	}

	private static <K, V> Map<K, List<V>> freeze(Map<K, ArrayList<V>> mutable, Comparator<? super V> comparator)
	{
		final HashMap<K, List<V>> result = new HashMap<>();
		for (Map.Entry<K, ArrayList<V>> entry : mutable.entrySet())
		{
			entry.getValue().sort(comparator);
			result.put(entry.getKey(), List.copyOf(entry.getValue()));
		}
		return Map.copyOf(result);
	}

	@SuppressWarnings("unchecked")
	private static <K> Map<K, List<NpcFact>> buildNpcAreaIndex(List<SpawnAreaFact> areas, Map<Integer, NpcFact> npcs, boolean topology)
	{
		final HashMap<K, ArrayList<NpcFact>> mutable = new HashMap<>();
		for (SpawnAreaFact area : areas)
		{
			final Object rawKey = topology ? area.topologyNodeId() : area.mapRegionLocId();
			final NpcFact npc = npcs.get(area.npcId());
			if ((rawKey == null) || (npc == null) || !npc.attackable())
			{
				continue;
			}
			final ArrayList<NpcFact> values = mutable.computeIfAbsent((K) rawKey, _ -> new ArrayList<>());
			if (!values.contains(npc))
			{
				values.add(npc);
			}
		}
		return freeze(mutable, Comparator.comparingInt(NpcFact::level).thenComparingInt(NpcFact::npcId));
	}

	private static Map<Integer, List<RecipeFact>> buildRecipeProductIndex(List<RecipeFact> recipes)
	{
		final HashMap<Integer, ArrayList<RecipeFact>> mutable = new HashMap<>();
		for (RecipeFact recipe : recipes)
		{
			mutable.computeIfAbsent(recipe.productItemId(), _ -> new ArrayList<>()).add(recipe);
			if (recipe.rareProductItemId() > 0)
			{
				mutable.computeIfAbsent(recipe.rareProductItemId(), _ -> new ArrayList<>()).add(recipe);
			}
		}
		return freeze(mutable, RECIPE_ORDER);
	}

	private static Map<Integer, List<RecipeFact>> buildRecipeIngredientIndex(List<RecipeFact> recipes)
	{
		final HashMap<Integer, ArrayList<RecipeFact>> mutable = new HashMap<>();
		for (RecipeFact recipe : recipes)
		{
			for (IngredientFact ingredient : recipe.ingredients())
			{
				final ArrayList<RecipeFact> values = mutable.computeIfAbsent(ingredient.itemId(), _ -> new ArrayList<>());
				if (!values.contains(recipe))
				{
					values.add(recipe);
				}
			}
		}
		return freeze(mutable, RECIPE_ORDER);
	}

	private static Map<String, List<ContentRequirementFact>> buildContentCapabilityIndex(List<ContentRequirementFact> contents)
	{
		final HashMap<String, ArrayList<ContentRequirementFact>> mutable = new HashMap<>();
		for (ContentRequirementFact content : contents)
		{
			for (CapabilityRequirement requirement : content.requirements())
			{
				final ArrayList<ContentRequirementFact> values = mutable.computeIfAbsent(requirement.capabilityKey(), _ -> new ArrayList<>());
				if (!values.contains(content))
				{
					values.add(content);
				}
			}
		}
		return freeze(mutable, Comparator.comparing(ContentRequirementFact::contentId));
	}

	private static String hashItems(List<ItemFact> facts)
	{
		final CanonicalHash hash = new CanonicalHash();
		hash.integer(facts.size());
		for (ItemFact fact : facts)
		{
			hash.integer(fact.itemId()).enumeration(fact.category()).string(fact.crystalType()).integer(fact.referencePrice()).bool(fact.stackable()).enumeration(fact.authority());
		}
		return hash.finish();
	}

	private static String hashNpcDrops(List<NpcFact> npcs, List<DropFact> drops)
	{
		final CanonicalHash hash = new CanonicalHash();
		hash.integer(npcs.size());
		for (NpcFact fact : npcs)
		{
			hash.integer(fact.npcId()).integer(fact.level()).enumeration(fact.kind()).bool(fact.attackable()).bool(fact.targetable()).bool(fact.canBeSown()).rawDouble(fact.exp()).rawDouble(fact.sp()).enumeration(fact.authority());
		}
		hash.integer(drops.size());
		for (DropFact fact : drops)
		{
			hash.integer(fact.npcId()).integer(fact.itemId()).enumeration(fact.sourceKind()).enumeration(fact.chanceModel()).integer(fact.groupOrdinal()).integer(fact.itemOrdinal()).rawDouble(fact.rawGroupChance()).rawDouble(fact.rawItemChance()).longValue(fact.minimumCount()).longValue(fact.maximumCount()).enumeration(fact.authority());
		}
		return hash.finish();
	}

	private static String hashSpawns(List<SpawnFact> spawns, List<SpawnAreaFact> areas)
	{
		final CanonicalHash hash = new CanonicalHash();
		hash.integer(spawns.size());
		for (SpawnFact fact : spawns)
		{
			hash.integer(fact.npcId()).integer(fact.spawnOrdinal()).integer(fact.instanceId()).integer(fact.x()).integer(fact.y()).integer(fact.z()).integer(fact.amount()).integer(fact.locationId()).enumeration(fact.pointKind()).nullableString(fact.topologyNodeId()).nullableInteger(fact.mapRegionLocId()).enumeration(fact.authority());
		}
		hash.integer(areas.size());
		for (SpawnAreaFact fact : areas)
		{
			hash.integer(fact.npcId()).integer(fact.instanceId()).nullableString(fact.topologyNodeId()).nullableInteger(fact.mapRegionLocId()).integer(fact.spawnCount()).longValue(fact.totalConfiguredAmount()).enumeration(fact.authority()).integer(fact.representativePoints().size());
			for (SpawnFact point : fact.representativePoints())
			{
				hash.integer(point.spawnOrdinal());
			}
		}
		return hash.finish();
	}

	private static String hashRecipes(List<RecipeFact> recipes)
	{
		final CanonicalHash hash = new CanonicalHash();
		hash.integer(recipes.size());
		for (RecipeFact fact : recipes)
		{
			hash.integer(fact.recipeListId()).integer(fact.recipeItemId()).integer(fact.productItemId()).longValue(fact.productCount()).integer(fact.rareProductItemId()).longValue(fact.rareProductCount()).integer(fact.rareProductChance()).integer(fact.craftLevel()).integer(fact.successRate()).bool(fact.dwarven()).enumeration(fact.authority()).integer(fact.ingredients().size());
			for (IngredientFact ingredient : fact.ingredients())
			{
				hash.integer(ingredient.itemId()).longValue(ingredient.count());
			}
		}
		return hash.finish();
	}

	private static String hashManor(List<ManorFact> facts)
	{
		final CanonicalHash hash = new CanonicalHash();
		hash.integer(facts.size());
		for (ManorFact fact : facts)
		{
			hash.integer(fact.castleId()).integer(fact.seedItemId()).integer(fact.cropItemId()).integer(fact.matureItemId()).integer(fact.reward1ItemId()).integer(fact.reward2ItemId()).integer(fact.seedLevel()).bool(fact.alternative()).integer(fact.rawSeedLimit()).integer(fact.rawCropLimit()).string(fact.sourcePath()).enumeration(fact.authority());
		}
		return hash.finish();
	}

	private static String hashClasses(List<ClassIntrinsicFact> classes, List<ClassCapabilityFact> capabilities)
	{
		final CanonicalHash hash = new CanonicalHash();
		hash.integer(classes.size());
		for (ClassIntrinsicFact fact : classes)
		{
			hash.integer(fact.classId()).string(fact.race()).integer(fact.classTier()).bool(fact.mage()).bool(fact.summoner()).nullableInteger(fact.parentClassId()).enumeration(fact.authority());
		}
		hash.integer(capabilities.size());
		for (ClassCapabilityFact fact : capabilities)
		{
			hash.integer(fact.classId()).string(fact.capabilityKey()).integer(fact.rank()).enumeration(fact.authority()).integer(fact.evidenceSkills().size());
			for (SkillEvidence evidence : fact.evidenceSkills())
			{
				hash.integer(evidence.skillId()).integer(evidence.skillLevel());
			}
			hash.strings(fact.sourceRefs());
		}
		return hash.finish();
	}

	private static String hashContents(List<ContentRequirementFact> contents)
	{
		final CanonicalHash hash = new CanonicalHash();
		hash.integer(contents.size());
		for (ContentRequirementFact fact : contents)
		{
			hash.string(fact.contentId()).enumeration(fact.contentKind()).nullableInteger(fact.npcId()).nullableString(fact.topologyNodeId()).nullableString(fact.topologyAnchorId()).integer(fact.recommendedMinParty()).integer(fact.recommendedMaxParty()).enumeration(fact.authority()).integer(fact.requirements().size());
			for (CapabilityRequirement requirement : fact.requirements())
			{
				hash.string(requirement.capabilityKey()).integer(requirement.minimumCount()).integer(requirement.minimumRank()).bool(requirement.required());
			}
			hash.strings(fact.sourceRefs());
		}
		return hash.finish();
	}

	private String hashCombined()
	{
		final CanonicalHash hash = new CanonicalHash();
		hash.integer(SCHEMA_VERSION).string(_datasetId).integer(_datasetVersion).longValue(GENERATION);
		hash.string("items").string(_itemsHash);
		hash.string("npc-drop-spoil").string(_npcDropSpoilHash);
		hash.string("spawn").string(_spawnHash);
		hash.string("recipe").string(_recipeHash);
		hash.string("manor").string(_manorHash);
		hash.string("class-capability").string(_classCapabilityHash);
		hash.string("content-requirement").string(_contentRequirementHash);
		hash.string("topology").string(_topologyHash);
		return hash.finish();
	}

	public int schemaVersion()
	{
		return SCHEMA_VERSION;
	}

	public String datasetId()
	{
		return _datasetId;
	}

	public int datasetVersion()
	{
		return _datasetVersion;
	}

	public long generation()
	{
		return GENERATION;
	}

	public String topologyHash()
	{
		return _topologyHash;
	}

	public PhantomGameKnowledgePolicy policy()
	{
		return _policy;
	}

	public List<ItemFact> items()
	{
		return _items;
	}

	public List<NpcFact> npcs()
	{
		return _npcs;
	}

	public List<DropFact> dropSpoilFacts()
	{
		return _dropSpoilFacts;
	}

	public List<SpawnFact> spawnFacts()
	{
		return _spawnFacts;
	}

	public List<SpawnAreaFact> spawnAreas()
	{
		return _spawnAreas;
	}

	public List<RecipeFact> recipes()
	{
		return _recipes;
	}

	public List<ManorFact> manorFacts()
	{
		return _manorFacts;
	}

	public List<ClassIntrinsicFact> classFacts()
	{
		return _classFacts;
	}

	public List<ClassCapabilityFact> classCapabilities()
	{
		return _classCapabilities;
	}

	public List<ContentRequirementFact> contentRequirements()
	{
		return _contentRequirements;
	}

	public Map<Integer, ItemFact> itemById()
	{
		return _itemById;
	}

	public Map<Integer, NpcFact> npcById()
	{
		return _npcById;
	}

	public Map<Integer, List<DropFact>> dropSourcesByItem()
	{
		return _dropSourcesByItem;
	}

	public Map<Integer, List<DropFact>> spoilSourcesByItem()
	{
		return _spoilSourcesByItem;
	}

	public Map<Integer, List<ManorFact>> manorFactsByItem()
	{
		return _manorFactsByItem;
	}

	public Map<Integer, List<DropFact>> dropFactsByNpc()
	{
		return _dropFactsByNpc;
	}

	public Map<Integer, List<DropFact>> spoilFactsByNpc()
	{
		return _spoilFactsByNpc;
	}

	public Map<Integer, List<SpawnFact>> spawnFactsByNpc()
	{
		return _spawnFactsByNpc;
	}

	public Map<Integer, List<SpawnAreaFact>> spawnAreasByNpc()
	{
		return _spawnAreasByNpc;
	}

	public Map<String, List<NpcFact>> npcsByTopologyNode()
	{
		return _npcsByTopologyNode;
	}

	public Map<Integer, List<NpcFact>> npcsByMapRegion()
	{
		return _npcsByMapRegion;
	}

	public Map<Integer, List<NpcFact>> npcsByLevel()
	{
		return _npcsByLevel;
	}

	public Map<Integer, RecipeFact> recipeByListId()
	{
		return _recipeByListId;
	}

	public Map<Integer, List<RecipeFact>> recipesByProduct()
	{
		return _recipesByProduct;
	}

	public Map<Integer, List<RecipeFact>> recipesByIngredient()
	{
		return _recipesByIngredient;
	}

	public Map<Integer, ClassIntrinsicFact> classFactsByClassId()
	{
		return _classFactsByClassId;
	}

	public Map<Integer, List<ClassCapabilityFact>> capabilitiesByClassId()
	{
		return _capabilitiesByClassId;
	}

	public Map<String, List<ClassCapabilityFact>> classesByCapability()
	{
		return _classesByCapability;
	}

	public Map<String, ContentRequirementFact> contentById()
	{
		return _contentById;
	}

	public Map<String, List<ContentRequirementFact>> contentByCapability()
	{
		return _contentByCapability;
	}

	public String itemsHash()
	{
		return _itemsHash;
	}

	public String npcDropSpoilHash()
	{
		return _npcDropSpoilHash;
	}

	public String spawnHash()
	{
		return _spawnHash;
	}

	public String recipeHash()
	{
		return _recipeHash;
	}

	public String manorHash()
	{
		return _manorHash;
	}

	public String classCapabilityHash()
	{
		return _classCapabilityHash;
	}

	public String contentRequirementHash()
	{
		return _contentRequirementHash;
	}

	public String combinedHash()
	{
		return _combinedHash;
	}

	public Hashes hashes()
	{
		return _hashes;
	}

	public Counts counts()
	{
		return _counts;
	}

	public record Hashes(String itemsHash, String npcDropSpoilHash, String spawnHash, String recipeHash, String manorHash, String classCapabilityHash, String contentRequirementHash, String topologyHash, String combinedHash)
	{
		public Hashes
		{
			Objects.requireNonNull(itemsHash, "itemsHash");
			Objects.requireNonNull(npcDropSpoilHash, "npcDropSpoilHash");
			Objects.requireNonNull(spawnHash, "spawnHash");
			Objects.requireNonNull(recipeHash, "recipeHash");
			Objects.requireNonNull(manorHash, "manorHash");
			Objects.requireNonNull(classCapabilityHash, "classCapabilityHash");
			Objects.requireNonNull(contentRequirementHash, "contentRequirementHash");
			Objects.requireNonNull(topologyHash, "topologyHash");
			Objects.requireNonNull(combinedHash, "combinedHash");
		}

		public static Hashes none()
		{
			return new Hashes("none", "none", "none", "none", "none", "none", "none", "none", "none");
		}
	}

	public record Counts(int items, int npcs, int deathDrops, int spoils, int spawnFacts, int spawnAreas, int recipes, int recipeIngredients, int manorFacts, int classFacts, int classCapabilities, int contentRequirements)
	{
	}

	private static final class CanonicalHash
	{
		private final MessageDigest _digest;

		private CanonicalHash()
		{
			try
			{
				_digest = MessageDigest.getInstance("SHA-256");
			}
			catch (NoSuchAlgorithmException exception)
			{
				throw new IllegalStateException(exception);
			}
		}

		private CanonicalHash integer(int value)
		{
			_digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(value).array());
			return this;
		}

		private CanonicalHash longValue(long value)
		{
			_digest.update(ByteBuffer.allocate(Long.BYTES).putLong(value).array());
			return this;
		}

		private CanonicalHash rawDouble(double value)
		{
			return longValue(Double.doubleToRawLongBits(value));
		}

		private CanonicalHash bool(boolean value)
		{
			_digest.update((byte) (value ? 1 : 0));
			return this;
		}

		private CanonicalHash enumeration(Enum<?> value)
		{
			return integer(value.ordinal());
		}

		private CanonicalHash string(String value)
		{
			final byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
			integer(bytes.length);
			_digest.update(bytes);
			return this;
		}

		private CanonicalHash nullableString(String value)
		{
			bool(value != null);
			return value == null ? this : string(value);
		}

		private CanonicalHash nullableInteger(Integer value)
		{
			bool(value != null);
			return value == null ? this : integer(value);
		}

		private CanonicalHash strings(List<String> values)
		{
			integer(values.size());
			for (String value : values)
			{
				string(value);
			}
			return this;
		}

		private String finish()
		{
			return java.util.HexFormat.of().formatHex(_digest.digest());
		}
	}
}
