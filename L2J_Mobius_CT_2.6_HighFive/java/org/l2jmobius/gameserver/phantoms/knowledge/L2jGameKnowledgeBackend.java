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

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntFunction;

import org.l2jmobius.gameserver.config.ServerConfig;
import org.l2jmobius.gameserver.data.SpawnTable;
import org.l2jmobius.gameserver.data.holders.RecipeHolder;
import org.l2jmobius.gameserver.data.xml.ItemData;
import org.l2jmobius.gameserver.data.xml.MapRegionData;
import org.l2jmobius.gameserver.data.xml.NpcData;
import org.l2jmobius.gameserver.data.xml.RecipeData;
import org.l2jmobius.gameserver.data.xml.SkillData;
import org.l2jmobius.gameserver.data.xml.SkillTreeData;
import org.l2jmobius.gameserver.model.Location;
import org.l2jmobius.gameserver.model.actor.enums.player.PlayerClass;
import org.l2jmobius.gameserver.model.actor.holders.npc.DropGroupHolder;
import org.l2jmobius.gameserver.model.actor.holders.npc.DropHolder;
import org.l2jmobius.gameserver.model.actor.templates.NpcTemplate;
import org.l2jmobius.gameserver.model.item.Armor;
import org.l2jmobius.gameserver.model.item.ItemTemplate;
import org.l2jmobius.gameserver.model.item.Weapon;
import org.l2jmobius.gameserver.model.item.recipe.RecipeList;
import org.l2jmobius.gameserver.model.skill.holders.SkillLearn;
import org.l2jmobius.gameserver.model.spawns.Spawn;
import org.l2jmobius.gameserver.model.zone.type.NpcSpawnTerritory;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.ChanceModel;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.ClassIntrinsicFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.DropFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.DropSourceKind;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.IngredientFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.ItemCategory;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.ItemFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.NpcFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.NpcKind;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.RecipeFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.SkillEvidence;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.SpawnFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.SpawnPointKind;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.TerritoryGeometry;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.TerritoryPolygon;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.TerritoryVertex;

/**
 * Read-only copying adapter over already loaded High Five data. Mutable loader
 * objects never cross this boundary.
 */
public final class L2jGameKnowledgeBackend implements PhantomGameKnowledgeBackend
{
	private static final Comparator<SkillEvidence> SKILL_ORDER = Comparator.comparingInt(SkillEvidence::skillId).thenComparingInt(SkillEvidence::skillLevel);

	@Override
	public BackendData load(PhantomGameKnowledgePolicy policy)
	{
		final List<ItemFact> items = copyItems(policy);
		final List<NpcTemplate> templates = NpcData.getInstance().getTemplates(_ -> true).stream().sorted(Comparator.comparingInt(NpcTemplate::getId)).toList();
		if (templates.size() > policy.maximumNpcTemplates())
		{
			throw failure("count", "Loaded NPC template count exceeds policy.");
		}
		final List<NpcFact> npcs = copyNpcs(templates);
		final List<DropFact> drops = copyDrops(templates, policy);
		final List<SpawnFact> spawns = copySpawns(policy);
		final List<RecipeFact> recipes = copyRecipes(policy);
		final List<ClassIntrinsicFact> classes = copyClasses();
		final Map<Integer, List<SkillEvidence>> classSkills = copyClassSkills(classes, policy);
		return new BackendData(items, npcs, drops, spawns, recipes, classes, classSkills);
	}

	private static List<ItemFact> copyItems(PhantomGameKnowledgePolicy policy)
	{
		final ArrayList<ItemFact> result = new ArrayList<>();
		for (ItemTemplate template : ItemData.getInstance().getAllItems())
		{
			if (template == null)
			{
				continue;
			}
			if (result.size() >= policy.maximumItems())
			{
				throw failure("count", "Loaded item count exceeds policy.");
			}
			final ItemCategory category = template instanceof Weapon ? ItemCategory.WEAPON : template instanceof Armor ? ItemCategory.ARMOR : ItemCategory.ETC;
			result.add(new ItemFact(template.getId(), category, template.getCrystalType().name(), template.getReferencePrice(), template.isStackable(), PhantomGameKnowledgeAuthority.SERVER_LOADER_FACT));
		}
		result.sort(Comparator.comparingInt(ItemFact::itemId));
		return List.copyOf(result);
	}

	private static List<NpcFact> copyNpcs(List<NpcTemplate> templates)
	{
		final ArrayList<NpcFact> result = new ArrayList<>(templates.size());
		for (NpcTemplate template : templates)
		{
			final NpcKind kind = template.isType("GrandBoss") ? NpcKind.GRAND_BOSS : template.isType("RaidBoss") ? NpcKind.RAID_BOSS : template.isType("Monster") ? NpcKind.MONSTER : NpcKind.OTHER_ATTACKABLE;
			result.add(new NpcFact(template.getId(), Byte.toUnsignedInt(template.getLevel()), kind, template.isAttackable(), template.isTargetable(), template.canBeSown(), template.getExp(), template.getSP(), PhantomGameKnowledgeAuthority.SERVER_LOADER_FACT));
		}
		return List.copyOf(result);
	}

	private static List<DropFact> copyDrops(List<NpcTemplate> templates, PhantomGameKnowledgePolicy policy)
	{
		final ArrayList<DropFact> result = new ArrayList<>();
		for (NpcTemplate template : templates)
		{
			final List<DropGroupHolder> dropGroups = template.getDropGroups();
			final List<DropGroupHolder> groups = dropGroups == null ? List.of() : dropGroups;
			for (int groupOrdinal = 0; groupOrdinal < groups.size(); groupOrdinal++)
			{
				final DropGroupHolder group = groups.get(groupOrdinal);
				final List<DropHolder> holders = group.getDropList();
				for (int itemOrdinal = 0; itemOrdinal < holders.size(); itemOrdinal++)
				{
					addDrop(result, template.getId(), holders.get(itemOrdinal), DropSourceKind.DEATH_DROP, ChanceModel.GROUP_CUMULATIVE, groupOrdinal, itemOrdinal, group.getChance(), policy);
				}
			}
			copyUngrouped(result, template.getId(), template.getDropList(), DropSourceKind.DEATH_DROP, policy);
			copyUngrouped(result, template.getId(), template.getSpoilList(), DropSourceKind.SPOIL, policy);
		}
		result.sort(Comparator.comparingInt(DropFact::npcId).thenComparing(DropFact::sourceKind).thenComparingInt(DropFact::groupOrdinal).thenComparingInt(DropFact::itemOrdinal).thenComparingInt(DropFact::itemId));
		return List.copyOf(result);
	}

	private static void copyUngrouped(List<DropFact> result, int npcId, List<DropHolder> source, DropSourceKind sourceKind, PhantomGameKnowledgePolicy policy)
	{
		if (source == null)
		{
			return;
		}
		for (int ordinal = 0; ordinal < source.size(); ordinal++)
		{
			addDrop(result, npcId, source.get(ordinal), sourceKind, ChanceModel.UNGROUPED_INDEPENDENT, -1, ordinal, 0d, policy);
		}
	}

	private static void addDrop(List<DropFact> result, int npcId, DropHolder holder, DropSourceKind sourceKind, ChanceModel chanceModel, int groupOrdinal, int itemOrdinal, double groupChance, PhantomGameKnowledgePolicy policy)
	{
		if (result.size() >= policy.maximumDropSpoilFacts())
		{
			throw failure("count", "Loaded drop/spoil fact count exceeds policy.");
		}
		result.add(new DropFact(npcId, holder.getItemId(), sourceKind, chanceModel, groupOrdinal, itemOrdinal, groupChance, holder.getChance(), holder.getMin(), holder.getMax(), PhantomGameKnowledgeAuthority.SERVER_LOADER_FACT));
	}

	private static List<SpawnFact> copySpawns(PhantomGameKnowledgePolicy policy)
	{
		final ArrayList<RawSpawn> raw = new ArrayList<>();
		final HashMap<RawTerritorySpawn, Integer> territoryAmounts = new HashMap<>();
		final IdentityHashMap<NpcSpawnTerritory, TerritoryGeometry> geometries = new IdentityHashMap<>();
		for (Map.Entry<Integer, Set<Spawn>> entry : SpawnTable.getInstance().getSpawnTable().entrySet())
		{
			for (Spawn spawn : entry.getValue())
			{
				if (raw.size() >= policy.maximumSpawnFacts())
				{
					throw failure("count", "Loaded spawn fact count exceeds policy.");
				}
				final Location spawnLocation = spawn.getSpawnLocation();
				final int loadedX = spawnLocation == null ? spawn.getX() : spawnLocation.getX();
				final int loadedY = spawnLocation == null ? spawn.getY() : spawnLocation.getY();
				final int loadedZ = spawnLocation == null ? spawn.getZ() : spawnLocation.getZ();
				final NpcSpawnTerritory territory = spawn.getSpawnTerritory();
				final TerritoryGeometry geometry = territory == null ? null : geometries.computeIfAbsent(territory, L2jGameKnowledgeBackend::copyGeometry);
				final boolean exact = (territory == null) && (spawn.getLocationId() == 0) && ((loadedX != 0) || (loadedY != 0));
				final SpawnPointKind pointKind = exact ? SpawnPointKind.EXACT : geometry == null ? SpawnPointKind.TERRITORY_OR_UNRESOLVED : SpawnPointKind.TERRITORY_POLYGON;
				final int x = exact ? loadedX : 0;
				final int y = exact ? loadedY : 0;
				final int z = exact ? loadedZ : 0;
				final Integer mapRegion = exact ? MapRegionData.getInstance().getMapRegionLocId(x, y) : null;
				if (geometry == null)
				{
					raw.add(new RawSpawn(entry.getKey(), spawn.getInstanceId(), x, y, z, spawn.getAmount(), spawn.getLocationId(), pointKind, mapRegion, null));
				}
				else
				{
					territoryAmounts.merge(new RawTerritorySpawn(entry.getKey(), spawn.getInstanceId(), spawn.getLocationId(), geometry), spawn.getAmount(), Math::addExact);
				}
			}
		}
		for (Map.Entry<RawTerritorySpawn, Integer> entry : territoryAmounts.entrySet())
		{
			final RawTerritorySpawn spawn = entry.getKey();
			raw.add(new RawSpawn(spawn._npcId, spawn._instanceId, 0, 0, 0, entry.getValue(), spawn._locationId, SpawnPointKind.TERRITORY_POLYGON, null, spawn._territoryGeometry));
		}
		if (raw.size() > policy.maximumSpawnFacts())
		{
			throw failure("count", "Loaded spawn fact count exceeds policy.");
		}
		raw.sort(RawSpawn.ORDER);
		final ArrayList<SpawnFact> result = new ArrayList<>(raw.size());
		int currentNpcId = -1;
		int ordinal = 0;
		for (RawSpawn spawn : raw)
		{
			if (spawn._npcId != currentNpcId)
			{
				currentNpcId = spawn._npcId;
				ordinal = 0;
			}
			result.add(new SpawnFact(spawn._npcId, ordinal++, spawn._instanceId, spawn._x, spawn._y, spawn._z, spawn._amount, spawn._locationId, spawn._pointKind, null, spawn._mapRegionLocId, PhantomGameKnowledgeAuthority.SERVER_LOADER_FACT, spawn._territoryGeometry));
		}
		return List.copyOf(result);
	}

	private static TerritoryGeometry copyGeometry(NpcSpawnTerritory territory)
	{
		return territory.geometrySnapshot().map(snapshot -> new TerritoryGeometry(snapshot.territoryName(), snapshot.sourcePath(), copyPolygon(snapshot.main()), snapshot.banned().stream().map(L2jGameKnowledgeBackend::copyPolygon).toList(), snapshot.hash())).orElse(null);
	}

	private static TerritoryPolygon copyPolygon(NpcSpawnTerritory.PolygonGeometry polygon)
	{
		return new TerritoryPolygon(polygon.vertices().stream().map(vertex -> new TerritoryVertex(vertex.x(), vertex.y())).toList(), polygon.lowZ(), polygon.highZ());
	}

	private static List<RecipeFact> copyRecipes(PhantomGameKnowledgePolicy policy)
	{
		final RecipeData data = RecipeData.getInstance();
		final int[] recipeItemIds = data.getAllItemIds();
		final HashSet<Integer> uniqueItemIds = new HashSet<>();
		if (Arrays.stream(recipeItemIds).anyMatch(itemId -> !uniqueItemIds.add(itemId)))
		{
			return copyRecipesByListId(data, recipeItemIds, policy);
		}
		return copyRecipes(recipeItemIds, data::getRecipeByItemId, policy);
	}

	private static List<RecipeFact> copyRecipes(int[] sourceRecipeItemIds, IntFunction<RecipeList> lookup, PhantomGameKnowledgePolicy policy)
	{
		final int[] recipeItemIds = sourceRecipeItemIds.clone();
		Arrays.sort(recipeItemIds);
		final HashSet<Integer> itemIds = new HashSet<>();
		final HashSet<Integer> listIds = new HashSet<>();
		final ArrayList<RecipeList> resolved = new ArrayList<>();
		for (int recipeItemId : recipeItemIds)
		{
			if (!itemIds.add(recipeItemId))
			{
				throw failure("ambiguity", "RecipeData exposes an ambiguous recipe item id.");
			}
			final RecipeList recipe = lookup.apply(recipeItemId);
			if (recipe == null)
			{
				throw failure("reference", "RecipeData item lookup lost a loaded recipe.");
			}
			if (recipe.getRecipeId() != recipeItemId)
			{
				throw failure("ambiguity", "RecipeData item lookup resolved a different recipe item id.");
			}
			if (!listIds.add(recipe.getId()))
			{
				throw failure("ambiguity", "RecipeData exposes an ambiguous recipe list id.");
			}
			resolved.add(recipe);
		}
		if (resolved.size() != recipeItemIds.length)
		{
			throw failure("ambiguity", "RecipeData recipe lookup is not one-to-one.");
		}
		return copyRecipeFacts(resolved, policy);
	}

	private static List<RecipeFact> copyRecipesByListId(RecipeData data, int[] sourceRecipeItemIds, PhantomGameKnowledgePolicy policy)
	{
		final ArrayList<RecipeList> resolved = new ArrayList<>(sourceRecipeItemIds.length);
		for (int listId = 1; (listId <= policy.maximumRecipes()) && (resolved.size() < sourceRecipeItemIds.length); listId++)
		{
			final RecipeList recipe = data.getRecipeList(listId);
			if (recipe != null)
			{
				resolved.add(recipe);
			}
		}
		if (resolved.size() != sourceRecipeItemIds.length)
		{
			throw failure("ambiguity", "RecipeData duplicate item identities cannot be resolved by unique list identity within policy.");
		}
		final int[] resolvedItemIds = resolved.stream().mapToInt(RecipeList::getRecipeId).sorted().toArray();
		final int[] expectedItemIds = sourceRecipeItemIds.clone();
		Arrays.sort(expectedItemIds);
		if (!Arrays.equals(expectedItemIds, resolvedItemIds))
		{
			throw failure("ambiguity", "RecipeData list identity does not preserve the loaded recipe-item multiset.");
		}
		return copyRecipeFacts(resolved, policy);
	}

	private static List<RecipeFact> copyRecipeFacts(List<RecipeList> resolved, PhantomGameKnowledgePolicy policy)
	{
		if (resolved.size() > policy.maximumRecipes())
		{
			throw failure("count", "Loaded recipe count exceeds policy.");
		}
		final ArrayList<RecipeFact> result = new ArrayList<>(resolved.size());
		final HashSet<Integer> listIds = new HashSet<>();
		int ingredientCount = 0;
		for (RecipeList recipe : resolved)
		{
			if (!listIds.add(recipe.getId()))
			{
				throw failure("ambiguity", "RecipeData exposes an ambiguous recipe list id.");
			}
			final ArrayList<IngredientFact> ingredients = new ArrayList<>();
			for (RecipeHolder holder : recipe.getRecipes())
			{
				if (++ingredientCount > policy.maximumRecipeIngredients())
				{
					throw failure("count", "Loaded recipe ingredient count exceeds policy.");
				}
				ingredients.add(new IngredientFact(holder.getItemId(), holder.getQuantity()));
			}
			ingredients.sort(Comparator.comparingInt(IngredientFact::itemId).thenComparingLong(IngredientFact::count));
			result.add(new RecipeFact(recipe.getId(), recipe.getRecipeId(), recipe.getItemId(), recipe.getCount(), recipe.getRareItemId(), recipe.getRareCount(), recipe.getRarity(), recipe.getLevel(), recipe.getSuccessRate(), recipe.isDwarvenRecipe(), ingredients, PhantomGameKnowledgeAuthority.SERVER_LOADER_FACT));
		}
		result.sort(Comparator.comparingInt(RecipeFact::recipeListId));
		return List.copyOf(result);
	}

	private static List<ClassIntrinsicFact> copyClasses()
	{
		return Arrays.stream(PlayerClass.values()).sorted(Comparator.comparingInt(PlayerClass::getId)).map(playerClass -> new ClassIntrinsicFact(playerClass.getId(), playerClass.getRace().name(), playerClass.level(), playerClass.isMage(), playerClass.isSummoner(), playerClass.getParent() == null ? null : playerClass.getParent().getId(), PhantomGameKnowledgeAuthority.SERVER_LOADER_FACT)).toList();
	}

	private static Map<Integer, List<SkillEvidence>> copyClassSkills(List<ClassIntrinsicFact> classes, PhantomGameKnowledgePolicy policy)
	{
		final HashMap<Integer, List<SkillEvidence>> result = new HashMap<>();
		final SkillTreeData skillTrees = SkillTreeData.getInstance();
		final SkillData skills = SkillData.getInstance();
		for (ClassIntrinsicFact classFact : classes)
		{
			final PlayerClass playerClass = PlayerClass.getPlayerClass(classFact.classId());
			final ArrayList<SkillEvidence> evidence = new ArrayList<>();
			for (SkillLearn learn : skillTrees.getCompleteClassSkillTree(playerClass).values())
			{
				if (skills.getSkill(learn.getSkillId(), learn.getSkillLevel()) != null)
				{
					evidence.add(new SkillEvidence(learn.getSkillId(), learn.getSkillLevel()));
				}
			}
			evidence.sort(SKILL_ORDER);
			final ArrayList<SkillEvidence> unique = new ArrayList<>();
			SkillEvidence previous = null;
			for (SkillEvidence value : evidence)
			{
				if (!value.equals(previous))
				{
					unique.add(value);
					previous = value;
				}
			}
			result.put(classFact.classId(), List.copyOf(unique));
		}
		return Map.copyOf(result);
	}

	@Override
	public boolean sourceExists(String relativeDatapackPath)
	{
		if ((relativeDatapackPath == null) || relativeDatapackPath.isBlank() || relativeDatapackPath.contains("..") || relativeDatapackPath.startsWith("/") || relativeDatapackPath.startsWith("\\"))
		{
			return false;
		}
		final File root = ServerConfig.DATAPACK_ROOT;
		final File source = new File(root, relativeDatapackPath.replace('/', File.separatorChar));
		try
		{
			return source.isFile() && source.getCanonicalPath().startsWith(root.getCanonicalPath() + File.separator);
		}
		catch (Exception exception)
		{
			return false;
		}
	}

	private static PhantomGameKnowledgeValidationException failure(String category, String message)
	{
		return new PhantomGameKnowledgeValidationException(category, message);
	}

	private record RawSpawn(int _npcId, int _instanceId, int _x, int _y, int _z, int _amount, int _locationId, SpawnPointKind _pointKind, Integer _mapRegionLocId, TerritoryGeometry _territoryGeometry)
	{
		private static final Comparator<RawSpawn> ORDER = Comparator.comparingInt(RawSpawn::_npcId).thenComparingInt(RawSpawn::_instanceId).thenComparingInt(RawSpawn::_x).thenComparingInt(RawSpawn::_y).thenComparingInt(RawSpawn::_z).thenComparingInt(RawSpawn::_amount).thenComparingInt(RawSpawn::_locationId).thenComparing(RawSpawn::_pointKind).thenComparing(spawn -> spawn._territoryGeometry == null ? "" : spawn._territoryGeometry.geometryHash());
	}

	private record RawTerritorySpawn(int _npcId, int _instanceId, int _locationId, TerritoryGeometry _territoryGeometry)
	{
	}
}
