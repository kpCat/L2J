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
package org.l2jmobius.tests.phantoms;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntFunction;

import org.l2jmobius.gameserver.data.SpawnTable;
import org.l2jmobius.gameserver.data.holders.RecipeHolder;
import org.l2jmobius.gameserver.data.xml.DoorData;
import org.l2jmobius.gameserver.data.xml.ItemData;
import org.l2jmobius.gameserver.data.xml.MapRegionData;
import org.l2jmobius.gameserver.data.xml.NpcData;
import org.l2jmobius.gameserver.data.xml.RecipeData;
import org.l2jmobius.gameserver.data.xml.SpawnData;
import org.l2jmobius.gameserver.model.Location;
import org.l2jmobius.gameserver.model.actor.holders.npc.DropGroupHolder;
import org.l2jmobius.gameserver.model.actor.holders.npc.DropHolder;
import org.l2jmobius.gameserver.model.actor.templates.NpcTemplate;
import org.l2jmobius.gameserver.model.item.Armor;
import org.l2jmobius.gameserver.model.item.ItemTemplate;
import org.l2jmobius.gameserver.model.item.Weapon;
import org.l2jmobius.gameserver.model.item.recipe.RecipeList;
import org.l2jmobius.gameserver.model.spawns.Spawn;
import org.l2jmobius.gameserver.model.zone.form.ZoneCuboid;
import org.l2jmobius.gameserver.model.zone.form.ZoneNPoly;
import org.l2jmobius.gameserver.model.zone.type.NpcSpawnTerritory;
import org.l2jmobius.gameserver.phantoms.knowledge.L2jGameKnowledgeBackend;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomCuratedKnowledgeParser;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeBackend;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeBackend.BackendData;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeBuilder;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeAuthority;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.ChanceModel;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.DropFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.DropSourceKind;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.IngredientFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.ItemCategory;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.ItemFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.ManorFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.NpcFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.NpcKind;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.PageRequest;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.RecipeFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.SpawnFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.SpawnPointKind;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgePolicy;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeQuery;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeService;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeSnapshot;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeValidationException;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomStaticManorParser;
import org.l2jmobius.gameserver.phantoms.topology.L2jTopologyValidationBackend;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyLoader;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyMetrics;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPolicy;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyQuery;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologySnapshot;

public final class PhantomGameKnowledgeParitySuite implements PhantomTestSuite
{
	private ProductionFixture _fixture;

	@Override
	public String id()
	{
		return "knowledge-parity";
	}

	@Override
	public void beforeAll(PhantomTestContext context) throws Exception
	{
		_fixture = ProductionFixture.start(context);
		final PhantomGameKnowledgeSnapshot snapshot = _fixture.snapshot();
		context.record("knowledge.items", snapshot.counts().items());
		context.record("knowledge.npcs", snapshot.counts().npcs());
		context.record("knowledge.deathDrops", snapshot.counts().deathDrops());
		context.record("knowledge.spoils", snapshot.counts().spoils());
		context.record("knowledge.spawnFacts", snapshot.counts().spawnFacts());
		context.record("knowledge.spawnAreas", snapshot.counts().spawnAreas());
		context.record("knowledge.recipes", snapshot.counts().recipes());
		context.record("knowledge.recipeIngredients", snapshot.counts().recipeIngredients());
		context.record("knowledge.manorFacts", snapshot.counts().manorFacts());
		context.record("knowledge.classFacts", snapshot.counts().classFacts());
		context.record("knowledge.classCapabilities", snapshot.counts().classCapabilities());
		context.record("knowledge.contentRequirements", snapshot.counts().contentRequirements());
		context.record("knowledge.itemsHash", snapshot.itemsHash());
		context.record("knowledge.npcDropSpoilHash", snapshot.npcDropSpoilHash());
		context.record("knowledge.spawnHash", snapshot.spawnHash());
		context.record("knowledge.recipeHash", snapshot.recipeHash());
		context.record("knowledge.manorHash", snapshot.manorHash());
		context.record("knowledge.classCapabilityHash", snapshot.classCapabilityHash());
		context.record("knowledge.contentRequirementHash", snapshot.contentRequirementHash());
		context.record("knowledge.topologyHash", snapshot.topologyHash());
		context.record("knowledge.combinedHash", snapshot.combinedHash());
		context.record("knowledge.unresolvedSpawns", snapshot.spawnFacts().stream().filter(fact -> fact.pointKind() == SpawnPointKind.TERRITORY_OR_UNRESOLVED).count());
		context.record("knowledge.unmappedExactSpawns", snapshot.spawnFacts().stream().filter(fact -> (fact.pointKind() == SpawnPointKind.EXACT) && (fact.topologyNodeId() == null)).count());
		context.record("knowledge.topologyMappedSpawns", snapshot.spawnFacts().stream().filter(fact -> fact.topologyNodeId() != null).count());
	}

	@Override
	public void afterAll(PhantomTestContext context) throws Exception
	{
		if (_fixture != null)
		{
			_fixture.close();
		}
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("01-all-items-exactly-once", _ -> testItems());
		registry.add("02-all-npcs-exactly-once", _ -> testNpcs());
		registry.add("03-runtime-grouped-item-order", _ -> testGroupedItemOrder());
		registry.add("04-runtime-group-order", _ -> testGroupOrder());
		registry.add("05-runtime-ungrouped-death-order", _ -> testUngroupedDeathOrder());
		registry.add("06-runtime-spoil-order", _ -> testSpoilOrder());
		registry.add("07-all-drop-spoil-facts-direct-loader-exact", _ -> testDrops());
		registry.add("08-zaken-known-order-regression", _ -> testZakenOrder());
		registry.add("09-zaken-adena-authoritative-range", _ -> testZakenAdenaRange());
		registry.add("10-all-authoritative-drop-count-ranges-valid", _ -> testDropCountRanges());
		registry.add("11-drop-spoil-references-and-reverse-indexes", _ -> testDropIndexes());
		registry.add("12-all-spawns-direct-loader-exact", _ -> testSpawns());
		registry.add("13-all-recipes-and-ingredients-direct-loader-exact", _ -> testRecipes());
		registry.add("14-recipe-loader-count-and-identity-unique", _ -> testRecipeIdentity());
		registry.add("15-duplicate-recipe-item-fails-closed", _ -> testRecipeAmbiguity());
		registry.add("16-static-seeds-parser-parity", _ -> testManor());
		registry.add("17-no-mutable-manager-or-db-source", this::testForbiddenSources);
		registry.add("18-accepted-topology-hash-and-mapping", _ -> testTopology());
		registry.add("19-component-counts-within-policy", _ -> testBounds());
		registry.add("20-service-component-hashes-exact", _ -> testServiceHashes());
		registry.add("21-query-source-seam-stable", _ -> testNoQuerySourceAccess());
		registry.add("22-loaded-territory-boundary-and-feasible-coverage", this::testTerritoryCoverage);
	}

	private void testItems()
	{
		int expectedCount = 0;
		for (ItemTemplate template : ItemData.getInstance().getAllItems())
		{
			if (template == null)
			{
				continue;
			}
			expectedCount++;
			final ItemCategory category = template instanceof Weapon ? ItemCategory.WEAPON : template instanceof Armor ? ItemCategory.ARMOR : ItemCategory.ETC;
			final ItemFact expected = new ItemFact(template.getId(), category, template.getCrystalType().name(), template.getReferencePrice(), template.isStackable(), PhantomGameKnowledgeAuthority.SERVER_LOADER_FACT);
			PhantomAssertions.assertEquals(expected, _fixture.snapshot().itemById().get(template.getId()), "Direct ItemData parity changed.");
		}
		PhantomAssertions.assertEquals(expectedCount, _fixture.snapshot().items().size(), "Direct ItemData count parity changed.");
		PhantomAssertions.assertEquals(expectedCount, _fixture.snapshot().itemById().size(), "Loaded item direct index is incomplete.");
	}

	private void testNpcs()
	{
		final List<NpcTemplate> templates = templates();
		for (NpcTemplate template : templates)
		{
			final NpcKind kind = template.isType("GrandBoss") ? NpcKind.GRAND_BOSS : template.isType("RaidBoss") ? NpcKind.RAID_BOSS : template.isType("Monster") ? NpcKind.MONSTER : NpcKind.OTHER_ATTACKABLE;
			final NpcFact expected = new NpcFact(template.getId(), Byte.toUnsignedInt(template.getLevel()), kind, template.isAttackable(), template.isTargetable(), template.canBeSown(), template.getExp(), template.getSP(), PhantomGameKnowledgeAuthority.SERVER_LOADER_FACT);
			PhantomAssertions.assertEquals(expected, _fixture.snapshot().npcById().get(template.getId()), "Direct NpcData parity changed.");
		}
		PhantomAssertions.assertEquals(templates.size(), _fixture.snapshot().npcs().size(), "Direct NpcData count parity changed.");
		PhantomAssertions.assertEquals(templates.size(), _fixture.snapshot().npcById().size(), "Loaded NPC direct index is incomplete.");
	}

	private void testGroupedItemOrder()
	{
		final NpcTemplate template = NpcData.getInstance().getTemplate(29181);
		final List<DropHolder> source = template.getDropGroups().getFirst().getDropList();
		final List<DropFact> facts = _fixture.snapshot().dropFactsByNpc().get(29181).stream().filter(fact -> fact.groupOrdinal() == 0).toList();
		PhantomAssertions.assertEquals(source.stream().map(DropHolder::getItemId).toList(), facts.stream().map(DropFact::itemId).toList(), "Knowledge reordered the first Zaken runtime drop group.");
		PhantomAssertions.assertEquals(source.stream().mapToInt(DropHolder::getItemId).min().orElseThrow() == source.getFirst().getItemId(), false, "Known Zaken group no longer demonstrates runtime order distinct from item-ID order.");
	}

	private void testGroupOrder()
	{
		final NpcTemplate template = NpcData.getInstance().getTemplate(29181);
		final List<Double> expected = template.getDropGroups().stream().map(DropGroupHolder::getChance).toList();
		final List<Double> actual = _fixture.snapshot().dropFactsByNpc().get(29181).stream().filter(fact -> fact.groupOrdinal() >= 0).collect(java.util.stream.Collectors.groupingBy(DropFact::groupOrdinal, java.util.TreeMap::new, java.util.stream.Collectors.mapping(DropFact::rawGroupChance, java.util.stream.Collectors.toList()))).values().stream().map(List::getFirst).toList();
		PhantomAssertions.assertEquals(expected, actual, "Knowledge reordered runtime drop groups.");
	}

	private void testUngroupedDeathOrder()
	{
		final NpcTemplate template = templates().stream().filter(value -> (value.getDropList() != null) && (value.getDropList().size() > 1)).findFirst().orElseThrow();
		assertUngroupedOrder(template.getId(), template.getDropList(), DropSourceKind.DEATH_DROP);
	}

	private void testSpoilOrder()
	{
		final NpcTemplate template = templates().stream().filter(value -> (value.getSpoilList() != null) && (value.getSpoilList().size() > 1)).findFirst().orElseThrow();
		assertUngroupedOrder(template.getId(), template.getSpoilList(), DropSourceKind.SPOIL);
	}

	private void assertUngroupedOrder(int npcId, List<DropHolder> source, DropSourceKind sourceKind)
	{
		final List<DropFact> facts = (sourceKind == DropSourceKind.DEATH_DROP ? _fixture.snapshot().dropFactsByNpc() : _fixture.snapshot().spoilFactsByNpc()).get(npcId).stream().filter(fact -> fact.groupOrdinal() == -1).toList();
		PhantomAssertions.assertEquals(source.stream().map(DropHolder::getItemId).toList(), facts.stream().map(DropFact::itemId).toList(), "Knowledge reordered a runtime ungrouped drop/spoil list.");
		PhantomAssertions.assertEquals(source.size(), facts.size(), "Knowledge changed an ungrouped drop/spoil list cardinality.");
	}

	private void testDrops()
	{
		long expectedDeath = 0;
		long expectedSpoil = 0;
		for (NpcTemplate template : templates())
		{
			final Map<String, DropFact> actual = new HashMap<>();
			_fixture.snapshot().dropFactsByNpc().getOrDefault(template.getId(), List.of()).forEach(fact -> actual.put(fact.stableKey(), fact));
			_fixture.snapshot().spoilFactsByNpc().getOrDefault(template.getId(), List.of()).forEach(fact -> actual.put(fact.stableKey(), fact));
			int expectedForNpc = 0;
			final List<DropGroupHolder> groups = template.getDropGroups() == null ? List.of() : template.getDropGroups();
			for (int groupOrdinal = 0; groupOrdinal < groups.size(); groupOrdinal++)
			{
				final DropGroupHolder group = groups.get(groupOrdinal);
				final List<DropHolder> holders = group.getDropList();
				for (int itemOrdinal = 0; itemOrdinal < holders.size(); itemOrdinal++)
				{
					assertDirectDrop(template.getId(), holders.get(itemOrdinal), DropSourceKind.DEATH_DROP, ChanceModel.GROUP_CUMULATIVE, groupOrdinal, itemOrdinal, group.getChance(), actual);
					expectedDeath++;
					expectedForNpc++;
				}
			}
			final List<DropHolder> death = template.getDropList() == null ? List.of() : template.getDropList();
			for (int itemOrdinal = 0; itemOrdinal < death.size(); itemOrdinal++)
			{
				assertDirectDrop(template.getId(), death.get(itemOrdinal), DropSourceKind.DEATH_DROP, ChanceModel.UNGROUPED_INDEPENDENT, -1, itemOrdinal, 0d, actual);
				expectedDeath++;
				expectedForNpc++;
			}
			final List<DropHolder> spoil = template.getSpoilList() == null ? List.of() : template.getSpoilList();
			for (int itemOrdinal = 0; itemOrdinal < spoil.size(); itemOrdinal++)
			{
				assertDirectDrop(template.getId(), spoil.get(itemOrdinal), DropSourceKind.SPOIL, ChanceModel.UNGROUPED_INDEPENDENT, -1, itemOrdinal, 0d, actual);
				expectedSpoil++;
				expectedForNpc++;
			}
			PhantomAssertions.assertEquals(expectedForNpc, actual.size(), "Knowledge has extra direct-loader drop/spoil facts for an NPC.");
		}
		PhantomAssertions.assertEquals(expectedDeath, (long) _fixture.snapshot().counts().deathDrops(), "Direct-loader death-drop count changed.");
		PhantomAssertions.assertEquals(expectedSpoil, (long) _fixture.snapshot().counts().spoils(), "Direct-loader spoil count changed.");
	}

	private void assertDirectDrop(int npcId, DropHolder holder, DropSourceKind sourceKind, ChanceModel chanceModel, int groupOrdinal, int itemOrdinal, double groupChance, Map<String, DropFact> actual)
	{
		final DropFact expected = new DropFact(npcId, holder.getItemId(), sourceKind, chanceModel, groupOrdinal, itemOrdinal, groupChance, holder.getChance(), holder.getMin(), holder.getMax(), PhantomGameKnowledgeAuthority.SERVER_LOADER_FACT);
		PhantomAssertions.assertEquals(expected, actual.get(expected.stableKey()), "Direct NpcData drop/spoil parity changed.");
	}

	private void testZakenOrder()
	{
		final List<DropHolder> firstGroup = NpcData.getInstance().getTemplate(29181).getDropGroups().getFirst().getDropList();
		PhantomAssertions.assertEquals(13144, firstGroup.getFirst().getItemId(), "Zaken runtime first-group head changed.");
		PhantomAssertions.assertEquals(13143, firstGroup.getLast().getItemId(), "Zaken runtime chance-sorted first-group tail changed.");
		final List<DropFact> actual = _fixture.snapshot().dropFactsByNpc().get(29181).stream().filter(fact -> fact.groupOrdinal() == 0).toList();
		PhantomAssertions.assertEquals(List.of(13144, 13143), List.of(actual.getFirst().itemId(), actual.getLast().itemId()), "Zaken runtime order was canonicalized by item ID.");
	}

	private void testZakenAdenaRange()
	{
		final List<DropFact> facts = _fixture.snapshot().dropSpoilFacts().stream().filter(fact -> (fact.npcId() == 29181) && (fact.itemId() == 57) && (fact.sourceKind() == DropSourceKind.DEATH_DROP)).toList();
		PhantomAssertions.assertEquals(1, facts.size(), "Zaken Adena authoritative drop identity is not unique.");
		final DropFact fact = facts.getFirst();
		PhantomAssertions.assertEquals(9_000_000L, fact.minimumCount(), "Zaken Adena authoritative minimum changed.");
		PhantomAssertions.assertEquals(11_000_000L, fact.maximumCount(), "Zaken Adena authoritative maximum changed.");
		PhantomAssertions.assertEquals(100.0, fact.rawGroupChance(), "Zaken Adena raw group chance changed.");
		PhantomAssertions.assertEquals(100.0, fact.rawItemChance(), "Zaken Adena raw item chance changed.");
		final DropHolder source = NpcData.getInstance().getTemplate(29181).getDropGroups().get(fact.groupOrdinal()).getDropList().get(fact.itemOrdinal());
		PhantomAssertions.assertEquals(source.getItemId(), fact.itemId(), "Zaken Adena runtime ordinal changed.");
	}

	private void testDropCountRanges()
	{
		for (DropFact fact : _fixture.snapshot().dropSpoilFacts())
		{
			PhantomAssertions.assertTrue(fact.minimumCount() >= 0, "Authoritative raw drop/spoil minimum is negative.");
			PhantomAssertions.assertTrue(fact.maximumCount() >= fact.minimumCount(), "Authoritative raw drop/spoil maximum is below minimum.");
		}
	}

	private void testDropIndexes()
	{
		final Set<Integer> npcIds = _fixture.snapshot().npcById().keySet();
		_fixture.snapshot().dropSpoilFacts().forEach(fact -> PhantomAssertions.assertTrue(npcIds.contains(fact.npcId()), "Reverse drop/spoil entry lacks an NPC fact."));
		final long indexedDrops = _fixture.snapshot().dropSourcesByItem().values().stream().mapToLong(List::size).sum();
		final long indexedSpoils = _fixture.snapshot().spoilSourcesByItem().values().stream().mapToLong(List::size).sum();
		PhantomAssertions.assertEquals((long) _fixture.snapshot().counts().deathDrops(), indexedDrops, "Drop reverse index is incomplete.");
		PhantomAssertions.assertEquals((long) _fixture.snapshot().counts().spoils(), indexedSpoils, "Spoil reverse index is incomplete.");
	}

	private void testSpawns()
	{
		final ArrayList<DirectSpawn> direct = new ArrayList<>();
		final Map<DirectTerritorySpawn, Integer> territoryAmounts = new HashMap<>();
		for (Map.Entry<Integer, Set<Spawn>> entry : SpawnTable.getInstance().getSpawnTable().entrySet())
		{
			for (Spawn spawn : entry.getValue())
			{
				final Location location = spawn.getSpawnLocation();
				final int loadedX = location == null ? spawn.getX() : location.getX();
				final int loadedY = location == null ? spawn.getY() : location.getY();
				final int loadedZ = location == null ? spawn.getZ() : location.getZ();
				final var geometry = spawn.getSpawnTerritory() == null ? null : spawn.getSpawnTerritory().geometrySnapshot().orElse(null);
				final boolean exact = (spawn.getSpawnTerritory() == null) && (spawn.getLocationId() == 0) && ((loadedX != 0) || (loadedY != 0));
				final SpawnPointKind pointKind = exact ? SpawnPointKind.EXACT : geometry == null ? SpawnPointKind.TERRITORY_OR_UNRESOLVED : SpawnPointKind.TERRITORY_POLYGON;
				final int x = exact ? loadedX : 0;
				final int y = exact ? loadedY : 0;
				final int z = exact ? loadedZ : 0;
				final Integer mapRegion = exact ? MapRegionData.getInstance().getMapRegionLocId(x, y) : null;
				if (geometry == null)
				{
					direct.add(new DirectSpawn(entry.getKey(), spawn.getInstanceId(), x, y, z, spawn.getAmount(), spawn.getLocationId(), pointKind, mapRegion, ""));
				}
				else
				{
					territoryAmounts.merge(new DirectTerritorySpawn(entry.getKey(), spawn.getInstanceId(), spawn.getLocationId(), geometry.hash()), spawn.getAmount(), Math::addExact);
				}
			}
		}
		territoryAmounts.forEach((spawn, amount) -> direct.add(new DirectSpawn(spawn.npcId(), spawn.instanceId(), 0, 0, 0, amount, spawn.locationId(), SpawnPointKind.TERRITORY_POLYGON, null, spawn.geometryHash())));
		direct.sort(DirectSpawn.ORDER);
		PhantomAssertions.assertEquals(direct.size(), _fixture.snapshot().spawnFacts().size(), "Direct SpawnTable count changed.");
		final Map<String, SpawnFact> actual = new HashMap<>();
		_fixture.snapshot().spawnFacts().forEach(fact -> actual.put(fact.stableKey(), fact));
		int currentNpcId = -1;
		int ordinal = 0;
		for (DirectSpawn expected : direct)
		{
			if (expected.npcId() != currentNpcId)
			{
				currentNpcId = expected.npcId();
				ordinal = 0;
			}
			final String key = String.format("%010d:%010d", expected.npcId(), ordinal++);
			final SpawnFact fact = actual.get(key);
			PhantomAssertions.assertTrue(fact != null, "Loaded spawn is missing.");
			PhantomAssertions.assertEquals(expected.npcId(), fact.npcId(), "Loaded spawn NPC changed.");
			PhantomAssertions.assertEquals(expected.instanceId(), fact.instanceId(), "Loaded spawn instance changed.");
			PhantomAssertions.assertEquals(expected.x(), fact.x(), "Loaded spawn X changed.");
			PhantomAssertions.assertEquals(expected.y(), fact.y(), "Loaded spawn Y changed.");
			PhantomAssertions.assertEquals(expected.z(), fact.z(), "Loaded spawn Z changed.");
			PhantomAssertions.assertEquals(expected.amount(), fact.amount(), "Loaded spawn amount changed.");
			PhantomAssertions.assertEquals(expected.locationId(), fact.locationId(), "Loaded spawn location id changed.");
			PhantomAssertions.assertEquals(expected.pointKind(), fact.pointKind(), "Loaded spawn point semantics changed.");
			PhantomAssertions.assertEquals(expected.mapRegionLocId(), fact.mapRegionLocId(), "Loaded spawn map region changed.");
			PhantomAssertions.assertEquals(expected.geometryHash(), fact.territoryGeometry() == null ? "" : fact.territoryGeometry().geometryHash(), "Loaded spawn territory geometry identity changed.");
			if (fact.pointKind() == SpawnPointKind.TERRITORY_OR_UNRESOLVED)
			{
				PhantomAssertions.assertEquals(0, fact.x(), "Unresolved spawn retained a runtime-random X coordinate.");
				PhantomAssertions.assertEquals(0, fact.y(), "Unresolved spawn retained a runtime-random Y coordinate.");
				PhantomAssertions.assertEquals(0, fact.z(), "Unresolved spawn retained a runtime-random Z coordinate.");
			}
		}
	}

	private void testTerritoryCoverage(PhantomTestContext context)
	{
		final Set<Integer> targetNpcs = Set.of(20013, 20019, 20016);
		final List<SpawnFact> facts = _fixture.snapshot().spawnFacts().stream().filter(fact -> targetNpcs.contains(fact.npcId()) && (fact.pointKind() == SpawnPointKind.TERRITORY_POLYGON) && ((fact.npcId() == 20016) ? fact.territoryGeometry().sourcePath().equals("data/spawns/TalkingIsland/TalkingIslandMonsters.xml") : fact.territoryGeometry().sourcePath().equals("data/spawns/ElvenTerritory/ElvenStarting.xml"))).toList();
		PhantomAssertions.assertEquals(20L, facts.stream().filter(fact -> fact.npcId() == 20013).count(), "NPC 20013 territory occurrence count changed.");
		PhantomAssertions.assertEquals(50L, facts.stream().filter(fact -> fact.npcId() == 20013).mapToLong(SpawnFact::amount).sum(), "NPC 20013 configured amount changed.");
		PhantomAssertions.assertEquals(17L, facts.stream().filter(fact -> fact.npcId() == 20019).count(), "NPC 20019 territory occurrence count changed.");
		PhantomAssertions.assertEquals(49L, facts.stream().filter(fact -> fact.npcId() == 20019).mapToLong(SpawnFact::amount).sum(), "NPC 20019 configured amount changed.");
		PhantomAssertions.assertEquals(8L, facts.stream().filter(fact -> fact.npcId() == 20016).count(), "NPC 20016 territory occurrence count changed.");
		PhantomAssertions.assertEquals(27L, facts.stream().filter(fact -> fact.npcId() == 20016).mapToLong(SpawnFact::amount).sum(), "NPC 20016 configured amount changed.");
		final long unique = facts.stream().map(fact -> fact.territoryGeometry().sourcePath() + ':' + fact.territoryGeometry().territoryName()).distinct().count();
		final long mapped = facts.stream().filter(fact -> fact.topologyNodeId() != null).map(fact -> fact.territoryGeometry().sourcePath() + ':' + fact.territoryGeometry().territoryName()).distinct().count();
		PhantomAssertions.assertEquals(35L, unique, "Curated factual territory identity count changed.");
		PhantomAssertions.assertEquals(15L, mapped, "Mapped feasible factual territory count changed.");
		PhantomAssertions.assertEquals(20L, unique - mapped, "Distance-infeasible factual territory count changed.");
		PhantomAssertions.assertEquals(9L, facts.stream().filter(fact -> (fact.npcId() == 20013) && (fact.topologyNodeId() != null)).count(), "NPC 20013 feasible occurrence count changed.");
		PhantomAssertions.assertEquals(7L, facts.stream().filter(fact -> (fact.npcId() == 20019) && (fact.topologyNodeId() != null)).count(), "NPC 20019 feasible occurrence count changed.");
		PhantomAssertions.assertEquals(1L, facts.stream().filter(fact -> (fact.npcId() == 20016) && (fact.topologyNodeId() != null)).count(), "NPC 20016 feasible occurrence count changed.");
		PhantomAssertions.assertTrue(_fixture.snapshot().spawnAreas().stream().filter(area -> targetNpcs.contains(area.npcId()) && (area.topologyNodeId() != null)).allMatch(area -> area.additionalUnmappedTerritories()), "Mapped source lost partial factual coverage evidence.");
		for (var group : facts.stream().collect(java.util.stream.Collectors.groupingBy(fact -> fact.territoryGeometry().geometryHash())).values())
		{
			final var shared = group.getFirst().territoryGeometry();
			PhantomAssertions.assertTrue(group.stream().allMatch(fact -> fact.territoryGeometry() == shared), "One loaded territory was deep-copied per NPC occurrence.");
		}

		final ZoneNPoly polygon = new ZoneNPoly(new int[]
		{
			0,
			10,
			0
		}, new int[]
		{
			0,
			0,
			10
		}, -10, 10);
		final NpcSpawnTerritory authoritative = new NpcSpawnTerritory("test", polygon, "data/spawns/test.xml");
		final var before = authoritative.geometrySnapshot().orElseThrow();
		polygon.getX()[0] = 99;
		PhantomAssertions.assertEquals(before, authoritative.geometrySnapshot().orElseThrow(), "Mutable legacy polygon arrays changed the immutable snapshot.");
		PhantomAssertions.assertTrue(new NpcSpawnTerritory("legacy", polygon).geometrySnapshot().isEmpty(), "Legacy territory constructor became source-authoritative.");
		PhantomAssertions.assertTrue(new NpcSpawnTerritory("unsupported", new ZoneCuboid(0, 10, 0, 10, -10, 10), "data/spawns/test.xml").geometrySnapshot().isEmpty(), "Unsupported territory form did not fail closed.");
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> new NpcSpawnTerritory("bad", polygon, "../outside.xml"), "Traversal territory source was accepted.");
		context.record("knowledge.loadedTerritoryFacts", facts.size());
		context.record("knowledge.mappedFeasibleTerritories", mapped);
		context.record("knowledge.unmappedDistanceInfeasibleTerritories", unique - mapped);
		context.record("knowledge.unmappedUnsupportedTerritories", 0);
	}

	private void testRecipes()
	{
		final Set<Integer> listIds = new HashSet<>();
		final List<RecipeList> recipes = directRecipes();
		for (RecipeList recipe : recipes)
		{
			PhantomAssertions.assertTrue(listIds.add(recipe.getId()), "Direct RecipeData exposes a duplicate recipe list id.");
			final ArrayList<IngredientFact> ingredients = new ArrayList<>();
			for (RecipeHolder holder : recipe.getRecipes())
			{
				ingredients.add(new IngredientFact(holder.getItemId(), holder.getQuantity()));
			}
			ingredients.sort(Comparator.comparingInt(IngredientFact::itemId).thenComparingLong(IngredientFact::count));
			final RecipeFact expected = new RecipeFact(recipe.getId(), recipe.getRecipeId(), recipe.getItemId(), recipe.getCount(), recipe.getRareItemId(), recipe.getRareCount(), recipe.getRarity(), recipe.getLevel(), recipe.getSuccessRate(), recipe.isDwarvenRecipe(), ingredients, PhantomGameKnowledgeAuthority.SERVER_LOADER_FACT);
			PhantomAssertions.assertEquals(expected, _fixture.snapshot().recipeByListId().get(recipe.getId()), "Direct RecipeData recipe/ingredient parity changed.");
		}
		PhantomAssertions.assertEquals(recipes.size(), _fixture.snapshot().recipes().size(), "RecipeData cardinality changed.");
		final long indexed = _fixture.snapshot().recipesByIngredient().values().stream().flatMap(List::stream).map(RecipeFact::recipeListId).distinct().count();
		PhantomAssertions.assertEquals((long) recipes.size(), indexed, "Recipe ingredient reverse graph lost a recipe.");
	}

	private void testRecipeIdentity()
	{
		final int[] recipeItemIds = RecipeData.getInstance().getAllItemIds();
		final Set<Integer> uniqueListIds = new HashSet<>();
		for (int recipeItemId : recipeItemIds)
		{
			final RecipeList recipe = RecipeData.getInstance().getRecipeByItemId(recipeItemId);
			PhantomAssertions.assertTrue((recipe != null) && (recipe.getRecipeId() == recipeItemId), "RecipeData item lookup lost an exposed recipe-item identity.");
		}
		for (RecipeList recipe : directRecipes())
		{
			PhantomAssertions.assertTrue(uniqueListIds.add(recipe.getId()), "RecipeData list identity is ambiguous.");
		}
		PhantomAssertions.assertEquals(recipeItemIds.length, _fixture.snapshot().counts().recipes(), "Knowledge silently omitted a loaded recipe.");
	}

	private void testRecipeAmbiguity() throws Exception
	{
		final int recipeItemId = RecipeData.getInstance().getAllItemIds()[0];
		final RecipeList recipe = RecipeData.getInstance().getRecipeByItemId(recipeItemId);
		final Method method = L2jGameKnowledgeBackend.class.getDeclaredMethod("copyRecipes", int[].class, IntFunction.class, PhantomGameKnowledgePolicy.class);
		method.setAccessible(true);
		try
		{
			method.invoke(null, new int[]
			{
				recipeItemId,
				recipeItemId
			}, (IntFunction<RecipeList>) _ -> recipe, _fixture.policy());
			throw new AssertionError("Duplicate recipe-item ambiguity was accepted.");
		}
		catch (InvocationTargetException exception)
		{
			PhantomAssertions.assertTrue(exception.getCause() instanceof PhantomGameKnowledgeValidationException, "Recipe ambiguity did not fail through deterministic validation.");
			PhantomAssertions.assertEquals("ambiguity", ((PhantomGameKnowledgeValidationException) exception.getCause()).category(), "Recipe ambiguity failure category changed.");
		}
	}

	private List<RecipeList> directRecipes()
	{
		final RecipeData data = RecipeData.getInstance();
		final int[] sourceItemIds = data.getAllItemIds();
		final ArrayList<RecipeList> recipes = new ArrayList<>(sourceItemIds.length);
		for (int listId = 1; (listId <= _fixture.policy().maximumRecipes()) && (recipes.size() < sourceItemIds.length); listId++)
		{
			final RecipeList recipe = data.getRecipeList(listId);
			if (recipe != null)
			{
				recipes.add(recipe);
			}
		}
		PhantomAssertions.assertEquals(sourceItemIds.length, recipes.size(), "Public RecipeData list lookup cannot reconstruct every loaded recipe within policy.");
		final int[] expected = sourceItemIds.clone();
		Arrays.sort(expected);
		final int[] actual = recipes.stream().mapToInt(RecipeList::getRecipeId).sorted().toArray();
		PhantomAssertions.assertTrue(Arrays.equals(expected, actual), "RecipeData list identities changed the exposed recipe-item multiset.");
		return List.copyOf(recipes);
	}

	private void testManor()
	{
		final List<ManorFact> parsed = new PhantomStaticManorParser(Path.of("data/Seeds.xml"), _fixture.policy()).parse();
		PhantomAssertions.assertEquals(parsed, _fixture.snapshot().manorFacts(), "Static Seeds.xml parser parity changed.");
		parsed.forEach(fact ->
		{
			for (int itemId : List.of(fact.seedItemId(), fact.cropItemId(), fact.matureItemId(), fact.reward1ItemId(), fact.reward2ItemId()))
			{
				PhantomAssertions.assertTrue(_fixture.snapshot().itemById().containsKey(itemId), "Static manor fact has a missing item reference.");
			}
		});
	}

	private void testForbiddenSources(PhantomTestContext context) throws Exception
	{
		final Path packageRoot = context.moduleRoot().resolve("java/org/l2jmobius/gameserver/phantoms/knowledge");
		try (var stream = Files.list(packageRoot))
		{
			for (Path file : stream.filter(path -> path.toString().endsWith(".java")).toList())
			{
				final String source = Files.readString(file, StandardCharsets.UTF_8);
				PhantomAssertions.assertFalse(source.contains("CastleManorManager") || source.contains("DimensionalRiftManager") || source.contains("DatabaseFactory") || source.contains("java.sql."), "Knowledge package references a forbidden mutable manager or DB API.");
			}
		}
	}

	private void testTopology()
	{
		PhantomAssertions.assertEquals(_fixture.topology().canonicalHash(), _fixture.snapshot().topologyHash(), "Knowledge did not bind the accepted topology snapshot hash.");
		_fixture.snapshot().spawnFacts().stream().filter(fact -> fact.topologyNodeId() != null).forEach(fact -> PhantomAssertions.assertTrue(_fixture.topology().nodeById().containsKey(fact.topologyNodeId()), "Mapped spawn refers to a missing accepted topology node."));
	}

	private void testBounds()
	{
		final var counts = _fixture.snapshot().counts();
		PhantomAssertions.assertTrue(counts.items() <= _fixture.policy().maximumItems(), "Item corpus exceeds policy.");
		PhantomAssertions.assertTrue(counts.npcs() <= _fixture.policy().maximumNpcTemplates(), "NPC corpus exceeds policy.");
		PhantomAssertions.assertTrue((counts.deathDrops() + counts.spoils()) <= _fixture.policy().maximumDropSpoilFacts(), "Drop/spoil corpus exceeds policy.");
		PhantomAssertions.assertTrue(counts.spawnFacts() <= _fixture.policy().maximumSpawnFacts(), "Spawn corpus exceeds policy.");
		PhantomAssertions.assertTrue(counts.recipes() <= _fixture.policy().maximumRecipes(), "Recipe corpus exceeds policy.");
	}

	private void testServiceHashes()
	{
		final PhantomGameKnowledgeSnapshot snapshot = _fixture.snapshot();
		final PhantomGameKnowledgeSnapshot.Hashes hashes = _fixture.serviceSnapshot().hashes();
		PhantomAssertions.assertEquals(snapshot.itemsHash(), hashes.itemsHash(), "Service item component hash changed.");
		PhantomAssertions.assertEquals(snapshot.npcDropSpoilHash(), hashes.npcDropSpoilHash(), "Service NPC/drop/spoil component hash changed.");
		PhantomAssertions.assertEquals(snapshot.spawnHash(), hashes.spawnHash(), "Service spawn component hash changed.");
		PhantomAssertions.assertEquals(snapshot.recipeHash(), hashes.recipeHash(), "Service recipe component hash changed.");
		PhantomAssertions.assertEquals(snapshot.manorHash(), hashes.manorHash(), "Service manor component hash changed.");
		PhantomAssertions.assertEquals(snapshot.classCapabilityHash(), hashes.classCapabilityHash(), "Service class component hash changed.");
		PhantomAssertions.assertEquals(snapshot.contentRequirementHash(), hashes.contentRequirementHash(), "Service content component hash changed.");
		PhantomAssertions.assertEquals(snapshot.topologyHash(), hashes.topologyHash(), "Service topology component hash changed.");
		PhantomAssertions.assertEquals(snapshot.combinedHash(), hashes.combinedHash(), "Service combined knowledge hash changed.");
	}

	private void testNoQuerySourceAccess()
	{
		final int loads = _fixture.backend().loads();
		final int sourceChecks = _fixture.backend().sourceChecks();
		for (ItemFact item : _fixture.snapshot().items().stream().limit(1000).toList())
		{
			_fixture.query().findItem(item.itemId());
			_fixture.query().dropSources(item.itemId(), PageRequest.first(1));
			_fixture.query().spoilSources(item.itemId(), PageRequest.first(1));
		}
		PhantomAssertions.assertEquals(loads, _fixture.backend().loads(), "Query path touched a loader.");
		PhantomAssertions.assertEquals(sourceChecks, _fixture.backend().sourceChecks(), "Query path touched datapack source evidence.");
	}

	private static List<NpcTemplate> templates()
	{
		return NpcData.getInstance().getTemplates(_ -> true).stream().sorted(Comparator.comparingInt(NpcTemplate::getId)).toList();
	}

	private record DirectSpawn(int npcId, int instanceId, int x, int y, int z, int amount, int locationId, SpawnPointKind pointKind, Integer mapRegionLocId, String geometryHash)
	{
		private static final Comparator<DirectSpawn> ORDER = Comparator.comparingInt(DirectSpawn::npcId).thenComparingInt(DirectSpawn::instanceId).thenComparingInt(DirectSpawn::x).thenComparingInt(DirectSpawn::y).thenComparingInt(DirectSpawn::z).thenComparingInt(DirectSpawn::amount).thenComparingInt(DirectSpawn::locationId).thenComparing(DirectSpawn::pointKind).thenComparing(DirectSpawn::geometryHash);
	}

	private record DirectTerritorySpawn(int npcId, int instanceId, int locationId, String geometryHash)
	{
	}

	static final class ProductionFixture
	{
		private final PhantomHeadlessPlayerTestEnvironment _environment;
		private final CountingBackend _backend;
		private final PhantomGameKnowledgePolicy _policy;
		private final PhantomTopologySnapshot _topology;
		private final PhantomGameKnowledgeService _service;

		private ProductionFixture(PhantomHeadlessPlayerTestEnvironment environment, CountingBackend backend, PhantomGameKnowledgePolicy policy, PhantomTopologySnapshot topology, PhantomGameKnowledgeService service)
		{
			_environment = environment;
			_backend = backend;
			_policy = policy;
			_topology = topology;
			_service = service;
		}

		static ProductionFixture start(PhantomTestContext context) throws Exception
		{
			final PhantomHeadlessPlayerTestEnvironment environment = new PhantomHeadlessPlayerTestEnvironment();
			environment.initialize(context);
			try
			{
				MapRegionData.getInstance();
				SpawnData.getInstance();
				DoorData.getInstance();
				final L2jTopologyValidationBackend topologyBackend = new L2jTopologyValidationBackend();
				final PhantomTopologySnapshot topology = new PhantomTopologyLoader(Path.of("data/phantoms/topology"), topologyBackend, PhantomTopologyPolicy.productionDefaults()).load(1);
				final PhantomTopologyQuery topologyQuery = new PhantomTopologyQuery(topology, topologyBackend, new PhantomTopologyMetrics());
				final PhantomGameKnowledgePolicy policy = PhantomGameKnowledgePolicy.productionDefaults();
				final CountingBackend backend = new CountingBackend(new L2jGameKnowledgeBackend());
				final PhantomGameKnowledgeBuilder builder = builder(backend, policy, topologyQuery);
				final PhantomGameKnowledgeService service = new PhantomGameKnowledgeService(builder);
				PhantomAssertions.assertTrue(service.start(), "Production Game Knowledge service did not start.");
				return new ProductionFixture(environment, backend, policy, topology, service);
			}
			catch (Throwable throwable)
			{
				environment.shutdown();
				throw throwable;
			}
		}

		private static PhantomGameKnowledgeBuilder builder(PhantomGameKnowledgeBackend backend, PhantomGameKnowledgePolicy policy, PhantomTopologyQuery topology)
		{
			return new PhantomGameKnowledgeBuilder(backend, new PhantomStaticManorParser(Path.of("data/Seeds.xml"), policy), new PhantomCuratedKnowledgeParser(Path.of("data/phantoms/knowledge"), backend, policy), topology, policy);
		}

		PhantomGameKnowledgeSnapshot snapshot()
		{
			return _service.query().snapshot();
		}

		PhantomGameKnowledgeQuery query()
		{
			return _service.query();
		}

		CountingBackend backend()
		{
			return _backend;
		}

		PhantomGameKnowledgePolicy policy()
		{
			return _policy;
		}

		PhantomTopologySnapshot topology()
		{
			return _topology;
		}

		PhantomGameKnowledgeService.ServiceSnapshot serviceSnapshot()
		{
			return _service.snapshot();
		}

		void close() throws Exception
		{
			_service.beginStop();
			_service.finishStop();
			_environment.shutdown();
		}
	}

	static final class CountingBackend implements PhantomGameKnowledgeBackend
	{
		private final PhantomGameKnowledgeBackend _delegate;
		private int _loads;
		private int _sourceChecks;

		CountingBackend(PhantomGameKnowledgeBackend delegate)
		{
			_delegate = delegate;
		}

		@Override
		public BackendData load(PhantomGameKnowledgePolicy policy)
		{
			_loads++;
			return _delegate.load(policy);
		}

		@Override
		public boolean sourceExists(String relativeDatapackPath)
		{
			_sourceChecks++;
			return _delegate.sourceExists(relativeDatapackPath);
		}

		int loads()
		{
			return _loads;
		}

		int sourceChecks()
		{
			return _sourceChecks;
		}
	}
}
