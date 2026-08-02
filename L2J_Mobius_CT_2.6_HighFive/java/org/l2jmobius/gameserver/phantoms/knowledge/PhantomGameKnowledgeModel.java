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

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable language-independent values exposed by Game Knowledge.
 */
public final class PhantomGameKnowledgeModel
{
	private PhantomGameKnowledgeModel()
	{
	}

	public enum ItemCategory
	{
		WEAPON,
		ARMOR,
		ETC
	}

	public enum NpcKind
	{
		MONSTER,
		RAID_BOSS,
		GRAND_BOSS,
		OTHER_ATTACKABLE
	}

	public enum DropSourceKind
	{
		DEATH_DROP,
		SPOIL
	}

	public enum ChanceModel
	{
		UNGROUPED_INDEPENDENT,
		GROUP_CUMULATIVE
	}

	public enum SpawnPointKind
	{
		EXACT,
		TERRITORY_POLYGON,
		TERRITORY_OR_UNRESOLVED
	}

	public record TerritoryVertex(int x, int y)
	{
	}

	public record TerritoryPolygon(List<TerritoryVertex> vertices, int lowZ, int highZ)
	{
		public TerritoryPolygon
		{
			vertices = List.copyOf(Objects.requireNonNull(vertices, "vertices"));
			if ((vertices.size() < 3) || (vertices.size() > 32) || (Set.copyOf(vertices).size() != vertices.size()) || (lowZ > highZ))
			{
				throw new IllegalArgumentException("Invalid territory polygon.");
			}
		}
	}

	public record TerritoryGeometry(String territoryName, String sourcePath, TerritoryPolygon main, List<TerritoryPolygon> banned, String geometryHash)
	{
		public TerritoryGeometry
		{
			if ((territoryName == null) || territoryName.isBlank() || (sourcePath == null) || sourcePath.isBlank() || (geometryHash == null) || !geometryHash.matches("[0-9a-f]{64}"))
			{
				throw new IllegalArgumentException("Invalid territory geometry identity.");
			}
			Objects.requireNonNull(main, "main");
			banned = List.copyOf(Objects.requireNonNull(banned, "banned"));
		}

		public boolean contains(int x, int y, int z)
		{
			if (!contains(main, x, y, z))
			{
				return false;
			}
			return banned.stream().noneMatch(polygon -> contains(polygon, x, y, z));
		}

		private static boolean contains(TerritoryPolygon polygon, int x, int y, int z)
		{
			if ((z < polygon.lowZ()) || (z > polygon.highZ()))
			{
				return false;
			}
			boolean inside = false;
			for (int current = 0, previous = polygon.vertices().size() - 1; current < polygon.vertices().size(); previous = current++)
			{
				final TerritoryVertex a = polygon.vertices().get(current);
				final TerritoryVertex b = polygon.vertices().get(previous);
				if (onSegment(a, b, x, y))
				{
					return true;
				}
				if (((a.y() > y) != (b.y() > y)) && (x < (((long) (b.x() - a.x()) * (y - a.y())) / (double) (b.y() - a.y())) + a.x()))
				{
					inside = !inside;
				}
			}
			return inside;
		}

		private static boolean onSegment(TerritoryVertex a, TerritoryVertex b, int x, int y)
		{
			final long cross = (((long) b.x() - a.x()) * ((long) y - a.y())) - (((long) b.y() - a.y()) * ((long) x - a.x()));
			return (cross == 0) && (x >= Math.min(a.x(), b.x())) && (x <= Math.max(a.x(), b.x())) && (y >= Math.min(a.y(), b.y())) && (y <= Math.max(a.y(), b.y()));
		}
	}

	public enum ManorItemRole
	{
		SEED,
		CROP,
		MATURE,
		REWARD_1,
		REWARD_2
	}

	public enum ContentKind
	{
		RIFT,
		RAID,
		EPIC,
		INSTANCE,
		FARMING,
		OTHER
	}

	public record ItemFact(int itemId, ItemCategory category, String crystalType, int referencePrice, boolean stackable, PhantomGameKnowledgeAuthority authority)
	{
		public ItemFact
		{
			if ((itemId <= 0) || (referencePrice < 0) || (crystalType == null) || crystalType.isBlank() || (authority != PhantomGameKnowledgeAuthority.SERVER_LOADER_FACT))
			{
				throw new IllegalArgumentException("Invalid item fact.");
			}
			Objects.requireNonNull(category, "category");
		}

		public String stableKey()
		{
			return key(itemId);
		}
	}

	public record NpcFact(int npcId, int level, NpcKind kind, boolean attackable, boolean targetable, boolean canBeSown, double exp, double sp, PhantomGameKnowledgeAuthority authority)
	{
		public NpcFact
		{
			if ((npcId <= 0) || (level < 0) || (level > 255) || !Double.isFinite(exp) || (exp < 0) || !Double.isFinite(sp) || (sp < 0) || (authority != PhantomGameKnowledgeAuthority.SERVER_LOADER_FACT))
			{
				throw new IllegalArgumentException("Invalid NPC fact.");
			}
			Objects.requireNonNull(kind, "kind");
		}

		public String stableKey()
		{
			return key(npcId);
		}
	}

	public record DropFact(int npcId, int itemId, DropSourceKind sourceKind, ChanceModel chanceModel, int groupOrdinal, int itemOrdinal, double rawGroupChance, double rawItemChance, long minimumCount, long maximumCount, PhantomGameKnowledgeAuthority authority)
	{
		public DropFact
		{
			if ((npcId <= 0) || (itemId <= 0) || (groupOrdinal < -1) || (itemOrdinal < 0) || !Double.isFinite(rawGroupChance) || (rawGroupChance < 0) || !Double.isFinite(rawItemChance) || (rawItemChance < 0) || (minimumCount < 0) || (maximumCount < minimumCount) || (authority != PhantomGameKnowledgeAuthority.SERVER_LOADER_FACT))
			{
				throw new IllegalArgumentException("Invalid raw drop/spoil fact.");
			}
			Objects.requireNonNull(sourceKind, "sourceKind");
			Objects.requireNonNull(chanceModel, "chanceModel");
			if ((chanceModel == ChanceModel.UNGROUPED_INDEPENDENT) && ((groupOrdinal != -1) || (rawGroupChance != 0d)))
			{
				throw new IllegalArgumentException("Ungrouped drop fact contains grouped semantics.");
			}
			if ((chanceModel == ChanceModel.GROUP_CUMULATIVE) && (groupOrdinal < 0))
			{
				throw new IllegalArgumentException("Grouped drop fact lacks a group ordinal.");
			}
		}

		public String stableKey()
		{
			return key(npcId) + ':' + sourceKind.ordinal() + ':' + key(groupOrdinal + 1) + ':' + key(itemOrdinal) + ':' + key(itemId);
		}
	}

	public record SpawnFact(int npcId, int spawnOrdinal, int instanceId, int x, int y, int z, int amount, int locationId, SpawnPointKind pointKind, String topologyNodeId, Integer mapRegionLocId, PhantomGameKnowledgeAuthority authority, TerritoryGeometry territoryGeometry)
	{
		public SpawnFact(int npcId, int spawnOrdinal, int instanceId, int x, int y, int z, int amount, int locationId, SpawnPointKind pointKind, String topologyNodeId, Integer mapRegionLocId, PhantomGameKnowledgeAuthority authority)
		{
			this(npcId, spawnOrdinal, instanceId, x, y, z, amount, locationId, pointKind, topologyNodeId, mapRegionLocId, authority, null);
		}

		public SpawnFact
		{
			if ((npcId <= 0) || (spawnOrdinal < 0) || (instanceId < 0) || (amount < 0) || ((authority != PhantomGameKnowledgeAuthority.SERVER_LOADER_FACT) && (authority != PhantomGameKnowledgeAuthority.TOPOLOGY_SNAPSHOT_FACT)))
			{
				throw new IllegalArgumentException("Invalid spawn fact.");
			}
			Objects.requireNonNull(pointKind, "pointKind");
			if (((pointKind == SpawnPointKind.TERRITORY_POLYGON) != (territoryGeometry != null)) || ((territoryGeometry != null) && ((x != 0) || (y != 0) || (z != 0))))
			{
				throw new IllegalArgumentException("Spawn territory geometry does not match its point kind.");
			}
			if ((topologyNodeId != null) && topologyNodeId.isBlank())
			{
				throw new IllegalArgumentException("Invalid topology node identity.");
			}
			if ((authority == PhantomGameKnowledgeAuthority.TOPOLOGY_SNAPSHOT_FACT) && (topologyNodeId == null))
			{
				throw new IllegalArgumentException("Topology-authoritative spawn fact lacks a topology node.");
			}
		}

		public SpawnFact withTopology(String nodeId)
		{
			return new SpawnFact(npcId, spawnOrdinal, instanceId, x, y, z, amount, locationId, pointKind, nodeId, mapRegionLocId, nodeId == null ? PhantomGameKnowledgeAuthority.SERVER_LOADER_FACT : PhantomGameKnowledgeAuthority.TOPOLOGY_SNAPSHOT_FACT, territoryGeometry);
		}

		public String stableKey()
		{
			return key(npcId) + ':' + key(spawnOrdinal);
		}
	}

	public record SpawnAreaFact(int npcId, int instanceId, String topologyNodeId, Integer mapRegionLocId, int spawnCount, long totalConfiguredAmount, List<SpawnFact> representativePoints, PhantomGameKnowledgeAuthority authority, boolean additionalUnmappedTerritories)
	{
		public SpawnAreaFact(int npcId, int instanceId, String topologyNodeId, Integer mapRegionLocId, int spawnCount, long totalConfiguredAmount, List<SpawnFact> representativePoints, PhantomGameKnowledgeAuthority authority)
		{
			this(npcId, instanceId, topologyNodeId, mapRegionLocId, spawnCount, totalConfiguredAmount, representativePoints, authority, false);
		}

		public SpawnAreaFact
		{
			if ((npcId <= 0) || (instanceId < 0) || (spawnCount < 1) || (totalConfiguredAmount < 0))
			{
				throw new IllegalArgumentException("Invalid spawn area fact.");
			}
			representativePoints = List.copyOf(representativePoints);
			Objects.requireNonNull(authority, "authority");
		}

		public String stableKey()
		{
			return key(npcId) + ':' + key(instanceId) + ':' + Objects.toString(topologyNodeId, "") + ':' + Objects.toString(mapRegionLocId, "");
		}
	}

	public record SpawnAreaSummary(int npcId, int instanceId, String topologyNodeId, Integer mapRegionLocId, int spawnCount, long totalConfiguredAmount, PhantomGameKnowledgeAuthority authority, boolean additionalUnmappedTerritories)
	{
		public SpawnAreaSummary(int npcId, int instanceId, String topologyNodeId, Integer mapRegionLocId, int spawnCount, long totalConfiguredAmount, PhantomGameKnowledgeAuthority authority)
		{
			this(npcId, instanceId, topologyNodeId, mapRegionLocId, spawnCount, totalConfiguredAmount, authority, false);
		}

		public SpawnAreaSummary
		{
			if ((npcId <= 0) || (instanceId < 0) || (spawnCount < 1) || (totalConfiguredAmount < 0))
			{
				throw new IllegalArgumentException("Invalid spawn area summary.");
			}
			Objects.requireNonNull(authority, "authority");
		}

		public static SpawnAreaSummary from(SpawnAreaFact fact)
		{
			Objects.requireNonNull(fact, "fact");
			return new SpawnAreaSummary(fact.npcId(), fact.instanceId(), fact.topologyNodeId(), fact.mapRegionLocId(), fact.spawnCount(), fact.totalConfiguredAmount(), fact.authority(), fact.additionalUnmappedTerritories());
		}

		public String stableKey()
		{
			return key(npcId) + ':' + key(instanceId) + ':' + Objects.toString(topologyNodeId, "") + ':' + Objects.toString(mapRegionLocId, "");
		}
	}

	public record IngredientFact(int itemId, long count)
	{
		public IngredientFact
		{
			if ((itemId <= 0) || (count <= 0))
			{
				throw new IllegalArgumentException("Invalid recipe ingredient.");
			}
		}

		public String stableKey()
		{
			return key(itemId) + ':' + String.format("%020d", count);
		}
	}

	public record RecipeFact(int recipeListId, int recipeItemId, int productItemId, long productCount, int rareProductItemId, long rareProductCount, int rareProductChance, int craftLevel, int successRate, boolean dwarven, List<IngredientFact> ingredients, PhantomGameKnowledgeAuthority authority)
	{
		public RecipeFact
		{
			if ((recipeListId <= 0) || (recipeItemId <= 0) || (productItemId <= 0) || (productCount <= 0) || (rareProductItemId < 0) || (rareProductCount < 0) || (rareProductChance < 0) || (craftLevel < 0) || (successRate < 0) || (ingredients == null) || ingredients.isEmpty() || (authority != PhantomGameKnowledgeAuthority.SERVER_LOADER_FACT))
			{
				throw new IllegalArgumentException("Invalid recipe fact.");
			}
			ingredients = List.copyOf(ingredients);
		}

		public String stableKey()
		{
			return key(recipeListId);
		}
	}

	public record ManorFact(int castleId, int seedItemId, int cropItemId, int matureItemId, int reward1ItemId, int reward2ItemId, int seedLevel, boolean alternative, int rawSeedLimit, int rawCropLimit, String sourcePath, PhantomGameKnowledgeAuthority authority)
	{
		public ManorFact
		{
			if ((castleId <= 0) || (seedItemId <= 0) || (cropItemId <= 0) || (matureItemId <= 0) || (reward1ItemId <= 0) || (reward2ItemId <= 0) || (seedLevel < 0) || (rawSeedLimit < 0) || (rawCropLimit < 0) || (sourcePath == null) || sourcePath.isBlank() || (authority != PhantomGameKnowledgeAuthority.STATIC_DATAPACK_FACT))
			{
				throw new IllegalArgumentException("Invalid static manor fact.");
			}
		}

		public String stableKey()
		{
			return key(castleId) + ':' + key(seedItemId) + ':' + key(cropItemId);
		}

		public boolean references(int itemId)
		{
			return (seedItemId == itemId) || (cropItemId == itemId) || (matureItemId == itemId) || (reward1ItemId == itemId) || (reward2ItemId == itemId);
		}
	}

	public record SkillEvidence(int skillId, int skillLevel)
	{
		public SkillEvidence
		{
			if ((skillId <= 0) || (skillLevel <= 0))
			{
				throw new IllegalArgumentException("Invalid skill evidence.");
			}
		}

		public String stableKey()
		{
			return key(skillId) + ':' + key(skillLevel);
		}
	}

	public record ClassIntrinsicFact(int classId, String race, int classTier, boolean mage, boolean summoner, Integer parentClassId, PhantomGameKnowledgeAuthority authority)
	{
		public ClassIntrinsicFact
		{
			if ((classId < 0) || (race == null) || race.isBlank() || (classTier < 0) || (parentClassId != null && parentClassId < 0) || (authority != PhantomGameKnowledgeAuthority.SERVER_LOADER_FACT))
			{
				throw new IllegalArgumentException("Invalid class intrinsic fact.");
			}
		}

		public String stableKey()
		{
			return key(classId);
		}
	}

	public record ClassCapabilityFact(int classId, String capabilityKey, int rank, List<SkillEvidence> evidenceSkills, List<String> sourceRefs, PhantomGameKnowledgeAuthority authority)
	{
		public ClassCapabilityFact
		{
			if ((classId < 0) || (capabilityKey == null) || capabilityKey.isBlank() || (rank < 1) || (rank > 1000) || (evidenceSkills == null) || evidenceSkills.isEmpty() || (sourceRefs == null) || sourceRefs.isEmpty() || (authority != PhantomGameKnowledgeAuthority.CURATED_RECOMMENDATION))
			{
				throw new IllegalArgumentException("Invalid class capability recommendation.");
			}
			evidenceSkills = List.copyOf(evidenceSkills);
			sourceRefs = List.copyOf(sourceRefs);
		}

		public String stableKey()
		{
			return key(classId) + ':' + capabilityKey + ':' + key(rank);
		}
	}

	public record CapabilityRequirement(String capabilityKey, int minimumCount, int minimumRank, boolean required)
	{
		public CapabilityRequirement
		{
			if ((capabilityKey == null) || capabilityKey.isBlank() || (minimumCount < 1) || (minimumRank < 1) || (minimumRank > 1000))
			{
				throw new IllegalArgumentException("Invalid content capability requirement.");
			}
		}

		public String stableKey()
		{
			return capabilityKey + ':' + key(minimumRank) + ':' + key(minimumCount) + ':' + (required ? '1' : '0');
		}
	}

	public record ContentRequirementFact(String contentId, ContentKind contentKind, Integer npcId, String topologyNodeId, String topologyAnchorId, int recommendedMinParty, int recommendedMaxParty, List<CapabilityRequirement> requirements, List<String> sourceRefs, PhantomGameKnowledgeAuthority authority)
	{
		public ContentRequirementFact
		{
			if ((contentId == null) || contentId.isBlank() || (npcId != null && npcId <= 0) || (recommendedMinParty < 1) || (recommendedMaxParty < recommendedMinParty) || (requirements == null) || requirements.isEmpty() || (sourceRefs == null) || sourceRefs.isEmpty() || (authority != PhantomGameKnowledgeAuthority.CURATED_RECOMMENDATION))
			{
				throw new IllegalArgumentException("Invalid content recommendation.");
			}
			Objects.requireNonNull(contentKind, "contentKind");
			requirements = List.copyOf(requirements);
			sourceRefs = List.copyOf(sourceRefs);
		}

		public String stableKey()
		{
			return contentId;
		}
	}

	public record PageRequest(int limit, String afterKey)
	{
		public PageRequest
		{
			if ((limit < 1) || (limit > 256) || ((afterKey != null) && afterKey.isBlank()))
			{
				throw new IllegalArgumentException("Invalid Game Knowledge page request.");
			}
		}

		public static PageRequest first(int limit)
		{
			return new PageRequest(limit, null);
		}
	}

	public record KnowledgePage<T>(List<T> values, String nextCursor, boolean hasMore)
	{
		public KnowledgePage
		{
			values = List.copyOf(values);
			if (hasMore && ((nextCursor == null) || nextCursor.isBlank()))
			{
				throw new IllegalArgumentException("A continued knowledge page requires a cursor.");
			}
		}
	}

	public record TargetQuery(int minimumLevel, int maximumLevel, Integer preferredLevel, String topologyNodeId, Integer mapRegionLocId, Set<NpcKind> allowedKinds, boolean requireAttackable, boolean requireTargetable, Boolean canBeSown, Integer dropsItemId, Integer spoilsItemId, PageRequest page)
	{
		public TargetQuery
		{
			if ((minimumLevel < 0) || (maximumLevel < minimumLevel) || ((maximumLevel - minimumLevel) > 100) || ((preferredLevel != null) && ((preferredLevel < minimumLevel) || (preferredLevel > maximumLevel))) || ((dropsItemId != null) && (dropsItemId <= 0)) || ((spoilsItemId != null) && (spoilsItemId <= 0)))
			{
				throw new IllegalArgumentException("Invalid bounded target query.");
			}
			allowedKinds = Set.copyOf(Objects.requireNonNull(allowedKinds, "allowedKinds"));
			Objects.requireNonNull(page, "page");
		}
	}

	public record TargetFact(NpcFact npc, int totalSpawnAreaCount, List<SpawnAreaSummary> representativeAreas, boolean hasMoreSpawnAreas)
	{
		public TargetFact
		{
			Objects.requireNonNull(npc, "npc");
			representativeAreas = List.copyOf(representativeAreas);
			if ((totalSpawnAreaCount < representativeAreas.size()) || (representativeAreas.size() > 64) || (hasMoreSpawnAreas != (totalSpawnAreaCount > representativeAreas.size())))
			{
				throw new IllegalArgumentException("Invalid bounded target spawn-area summary.");
			}
		}

		public String stableKey(Integer preferredLevel)
		{
			final int distance = preferredLevel == null ? 0 : Math.abs(npc.level() - preferredLevel);
			return key(distance) + ':' + key(npc.level()) + ':' + key(npc.npcId());
		}
	}

	static String key(int value)
	{
		return String.format("%010d", value);
	}
}
