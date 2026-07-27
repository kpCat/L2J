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

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomCuratedKnowledgeParser;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeAuthority;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeBackend;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeBuilder;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeMetrics;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.CapabilityRequirement;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.ChanceModel;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.ClassCapabilityFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.ClassIntrinsicFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.ContentKind;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.ContentRequirementFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.DropFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.DropSourceKind;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.IngredientFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.ItemCategory;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.ItemFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.KnowledgePage;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.ManorFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.NpcFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.NpcKind;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.PageRequest;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.RecipeFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.SkillEvidence;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.SpawnAreaSummary;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.SpawnFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.SpawnPointKind;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.TargetFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.TargetQuery;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgePolicy;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeQuery;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeService;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeSnapshot;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeValidationException;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomStaticManorParser;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyArea;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyMetrics;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyNode;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyNodeKind;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPolicy;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyQuery;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologySnapshot;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyValidationBackend;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyValidationBackend.DoorFact;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyValidationBackend.DoorState;

public final class PhantomGameKnowledgeCoreSuite implements PhantomTestSuite
{
	private static final PhantomGameKnowledgePolicy POLICY = PhantomGameKnowledgePolicy.productionDefaults();
	private final List<Path> _temporaryRoots = new ArrayList<>();
	private SyntheticFixture _fixture;

	@Override
	public String id()
	{
		return "knowledge-core";
	}

	@Override
	public void beforeAll(PhantomTestContext context) throws Exception
	{
		_fixture = build(context, "primary", false, false, false, 25d);
		context.record("knowledge.core.cases", 50);
		context.record("knowledge.core.combinedHash", _fixture.snapshot().combinedHash());
	}

	@Override
	public void afterAll(PhantomTestContext context) throws Exception
	{
		for (Path root : _temporaryRoots)
		{
			if (Files.exists(root))
			{
				try (var stream = Files.walk(root))
				{
					for (Path path : stream.sorted(Collections.reverseOrder()).toList())
					{
						Files.deleteIfExists(path);
					}
				}
			}
		}
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("01-policy-fixed-bounds", _ -> testPolicy());
		registry.add("02-item-fact-validation", _ -> testItemValidation());
		registry.add("03-npc-fact-validation", _ -> testNpcValidation());
		registry.add("04-drop-fact-validation", _ -> testDropValidation());
		registry.add("05-grouped-raw-semantics", _ -> testGrouped());
		registry.add("06-ungrouped-raw-semantics", _ -> testUngrouped());
		registry.add("07-exact-double-hashing", this::testExactDoubleHash);
		registry.add("08-fact-list-immutable", _ -> testFactListImmutable());
		registry.add("09-index-map-immutable", _ -> testIndexMapImmutable());
		registry.add("10-index-list-immutable", _ -> testIndexListImmutable());
		registry.add("11-canonical-input-order", this::testCanonicalOrder);
		registry.add("12-duplicate-item-rejected", this::testDuplicateItem);
		registry.add("13-missing-reference-rejected", this::testMissingReference);
		registry.add("14-drop-reverse-index", _ -> testDropIndex());
		registry.add("15-spoil-reverse-index", _ -> testSpoilIndex());
		registry.add("16-manor-five-role-index", _ -> testManorIndex());
		registry.add("17-recipe-list-index", _ -> testRecipeList());
		registry.add("18-recipe-product-index", _ -> testRecipeProduct());
		registry.add("19-recipe-ingredient-index", _ -> testRecipeIngredient());
		registry.add("20-spawn-exact-preserved", _ -> testExactSpawn());
		registry.add("21-spawn-unresolved-preserved", _ -> testUnresolvedSpawn());
		registry.add("22-spawn-outside-world-preserved-unmapped", _ -> testOutsideWorldSpawn());
		registry.add("23-spawn-map-area", _ -> testMapArea());
		registry.add("24-spawn-topology-area", _ -> testTopologyArea());
		registry.add("25-level-bucket-index", _ -> testLevelBucket());
		registry.add("26-target-level-filter", _ -> testTargetLevel());
		registry.add("27-target-preferred-order", _ -> testTargetOrder());
		registry.add("28-target-kind-filter", _ -> testTargetKind());
		registry.add("29-target-drop-filter", _ -> testTargetDrop());
		registry.add("30-target-spoil-filter", _ -> testTargetSpoil());
		registry.add("31-deterministic-page-cursor", _ -> testCursor());
		registry.add("32-page-size-bound", _ -> testPageBound());
		registry.add("33-target-range-bound", _ -> testRangeBound());
		registry.add("34-class-intrinsic-index", _ -> testClassIntrinsic());
		registry.add("35-class-capability-index", _ -> testClassCapabilities());
		registry.add("36-capability-reverse-index", _ -> testCapabilityReverse());
		registry.add("37-content-direct-index", _ -> testContent());
		registry.add("38-content-capability-index", _ -> testContentReverse());
		registry.add("39-startup-atomic-failure", _ -> testAtomicFailure());
		registry.add("40-inert-and-stop-lifecycle", _ -> testLifecycle());
		registry.add("41-query-has-no-source-seam", _ -> testNoSourceAfterBuild());
		registry.add("42-no-mutable-server-fields", _ -> testNoMutableServerFields());
		registry.add("43-runtime-drop-ordinals-preserved", _ -> testRuntimeDropOrdinals());
		registry.add("44-drop-ordinal-affects-hash", this::testDropOrdinalHash);
		registry.add("45-service-component-hashes", _ -> testServiceHashes());
		registry.add("46-inactive-component-hashes-none", _ -> testInactiveHashes());
		registry.add("47-spawn-area-summary-has-no-points", _ -> testSpawnAreaSummary());
		registry.add("48-target-area-summary-contract", _ -> testTargetAreaSummary());
		registry.add("49-target-summary-type-is-lightweight", _ -> testTargetSummaryType());
		registry.add("50-drop-group-ordinal-affects-hash", this::testDropGroupOrdinalHash);
	}

	private void testPolicy()
	{
		PhantomAssertions.assertEquals(256, POLICY.maximumQueryPageSize(), "Knowledge page policy changed.");
		PhantomAssertions.assertEquals(100, POLICY.maximumTargetLevelWidth(), "Knowledge target range policy changed.");
		PhantomAssertions.assertEquals(2_000_000, POLICY.maximumDropSpoilFacts(), "Knowledge source fact bound changed.");
	}

	private void testItemValidation()
	{
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> new ItemFact(0, ItemCategory.ETC, "NONE", 0, true, PhantomGameKnowledgeAuthority.SERVER_LOADER_FACT), "Invalid item identity was accepted.");
	}

	private void testNpcValidation()
	{
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> new NpcFact(1, -1, NpcKind.MONSTER, true, true, true, 0, 0, PhantomGameKnowledgeAuthority.SERVER_LOADER_FACT), "Invalid NPC level was accepted.");
	}

	private void testDropValidation()
	{
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> new DropFact(1, 1, DropSourceKind.DEATH_DROP, ChanceModel.UNGROUPED_INDEPENDENT, 0, 0, 0, 1, 1, 1, PhantomGameKnowledgeAuthority.SERVER_LOADER_FACT), "Ungrouped fact accepted a group ordinal.");
	}

	private void testGrouped()
	{
		final DropFact fact = _fixture.snapshot().dropSpoilFacts().stream().filter(value -> value.chanceModel() == ChanceModel.GROUP_CUMULATIVE).findFirst().orElseThrow();
		PhantomAssertions.assertEquals(50d, fact.rawGroupChance(), "Raw group chance changed.");
		PhantomAssertions.assertEquals(25d, fact.rawItemChance(), "Raw group item chance changed.");
		PhantomAssertions.assertEquals(0, fact.groupOrdinal(), "Canonical group ordinal changed.");
	}

	private void testUngrouped()
	{
		final DropFact fact = _fixture.snapshot().dropSpoilFacts().stream().filter(value -> (value.sourceKind() == DropSourceKind.DEATH_DROP) && (value.chanceModel() == ChanceModel.UNGROUPED_INDEPENDENT)).findFirst().orElseThrow();
		PhantomAssertions.assertEquals(-1, fact.groupOrdinal(), "Ungrouped sentinel changed.");
		PhantomAssertions.assertEquals(0d, fact.rawGroupChance(), "Ungrouped fact invented a group chance.");
	}

	private void testExactDoubleHash(PhantomTestContext context) throws Exception
	{
		final SyntheticFixture changed = build(context, "double", false, false, false, Math.nextUp(25d));
		PhantomAssertions.assertFalse(_fixture.snapshot().npcDropSpoilHash().equals(changed.snapshot().npcDropSpoilHash()), "Raw IEEE double change was lost from canonical hashing.");
	}

	private void testFactListImmutable()
	{
		PhantomAssertions.assertThrows(UnsupportedOperationException.class, () -> _fixture.snapshot().items().clear(), "Knowledge fact list remained mutable.");
	}

	private void testIndexMapImmutable()
	{
		PhantomAssertions.assertThrows(UnsupportedOperationException.class, () -> _fixture.snapshot().itemById().clear(), "Knowledge direct index remained mutable.");
	}

	private void testIndexListImmutable()
	{
		PhantomAssertions.assertThrows(UnsupportedOperationException.class, () -> _fixture.snapshot().dropSourcesByItem().get(1).clear(), "Knowledge reverse index list remained mutable.");
	}

	private void testCanonicalOrder(PhantomTestContext context) throws Exception
	{
		final SyntheticFixture reversed = build(context, "reversed", true, false, false, 25d);
		PhantomAssertions.assertEquals(_fixture.snapshot().combinedHash(), reversed.snapshot().combinedHash(), "Input collection order changed the canonical hash.");
	}

	private void testDuplicateItem(PhantomTestContext context) throws Exception
	{
		PhantomAssertions.assertThrows(PhantomGameKnowledgeValidationException.class, () -> build(context, "duplicate", false, true, false, 25d), "Duplicate authoritative item was accepted.");
	}

	private void testMissingReference(PhantomTestContext context) throws Exception
	{
		PhantomAssertions.assertThrows(PhantomGameKnowledgeValidationException.class, () -> build(context, "missing", false, false, true, 25d), "Missing authoritative item reference was accepted.");
	}

	private void testDropIndex()
	{
		PhantomAssertions.assertEquals(2, _fixture.query().dropSources(1, PageRequest.first(10)).values().size(), "Drop reverse index is incomplete.");
	}

	private void testSpoilIndex()
	{
		final DropFact fact = _fixture.query().spoilSources(3, PageRequest.first(10)).values().getFirst();
		PhantomAssertions.assertEquals(102, fact.npcId(), "Spoil reverse index points to the wrong NPC.");
	}

	private void testManorIndex()
	{
		for (int itemId = 1; itemId <= 5; itemId++)
		{
			PhantomAssertions.assertEquals(1, _fixture.query().manorSources(itemId, PageRequest.first(10)).values().size(), "Static manor relationship role is missing.");
		}
	}

	private void testRecipeList()
	{
		PhantomAssertions.assertEquals(10, _fixture.query().findRecipeByListId(10).orElseThrow().recipeListId(), "Recipe list direct index changed.");
	}

	private void testRecipeProduct()
	{
		PhantomAssertions.assertEquals(10, _fixture.query().recipesProducing(7, PageRequest.first(10)).values().getFirst().recipeListId(), "Recipe product reverse edge is missing.");
		PhantomAssertions.assertEquals(10, _fixture.query().recipesProducing(8, PageRequest.first(10)).values().getFirst().recipeListId(), "Rare recipe product reverse edge is missing.");
	}

	private void testRecipeIngredient()
	{
		PhantomAssertions.assertEquals(10, _fixture.query().recipesUsing(9, PageRequest.first(10)).values().getFirst().recipeListId(), "Recipe ingredient reverse edge is missing.");
	}

	private void testExactSpawn()
	{
		final SpawnFact fact = _fixture.query().spawnFacts(102, PageRequest.first(10)).values().getFirst();
		PhantomAssertions.assertEquals(SpawnPointKind.EXACT, fact.pointKind(), "Exact loaded spawn was not preserved.");
		PhantomAssertions.assertEquals(100, fact.x(), "Exact loaded spawn coordinate changed.");
	}

	private void testUnresolvedSpawn()
	{
		final SpawnFact fact = _fixture.query().spawnFacts(101, PageRequest.first(10)).values().getFirst();
		PhantomAssertions.assertEquals(SpawnPointKind.TERRITORY_OR_UNRESOLVED, fact.pointKind(), "Unresolved spawn semantics were lost.");
		PhantomAssertions.assertEquals(null, fact.topologyNodeId(), "Unresolved spawn fabricated a topology node.");
	}

	private void testOutsideWorldSpawn()
	{
		final SpawnFact fact = _fixture.query().spawnFacts(104, PageRequest.first(10)).values().getFirst();
		PhantomAssertions.assertEquals(World.WORLD_X_MAX + 1, fact.x(), "Outside-world exact spawn X changed.");
		PhantomAssertions.assertEquals(SpawnPointKind.EXACT, fact.pointKind(), "Outside-world exact spawn semantics changed.");
		PhantomAssertions.assertEquals(null, fact.topologyNodeId(), "Outside-world exact spawn fabricated a topology node.");
	}

	private void testMapArea()
	{
		PhantomAssertions.assertTrue(_fixture.snapshot().npcsByMapRegion().get(5).stream().anyMatch(npc -> npc.npcId() == 102), "Map-region spawn-area index is incomplete.");
	}

	private void testTopologyArea()
	{
		final SpawnFact fact = _fixture.query().spawnFacts(102, PageRequest.first(10)).values().getFirst();
		PhantomAssertions.assertEquals("synthetic.area", fact.topologyNodeId(), "Exact spawn did not use the immutable topology snapshot.");
		PhantomAssertions.assertEquals(PhantomGameKnowledgeAuthority.TOPOLOGY_SNAPSHOT_FACT, fact.authority(), "Mapped spawn authority is incorrect.");
	}

	private void testLevelBucket()
	{
		PhantomAssertions.assertEquals(1, _fixture.snapshot().npcsByLevel().get(20).size(), "Attackable NPC level bucket is incomplete.");
	}

	private void testTargetLevel()
	{
		final KnowledgePage<?> page = _fixture.query().suitableTargets(target(20, 20, null, Set.of(), null, null, PageRequest.first(10)));
		PhantomAssertions.assertEquals(1, page.values().size(), "Bounded target level filtering is incorrect.");
	}

	private void testTargetOrder()
	{
		final var values = _fixture.query().suitableTargets(target(20, 21, 21, Set.of(), null, null, PageRequest.first(10))).values();
		PhantomAssertions.assertEquals(103, values.getFirst().npc().npcId(), "Preferred target level order is not deterministic.");
		PhantomAssertions.assertEquals(102, values.get(1).npc().npcId(), "Preferred target tie order changed.");
	}

	private void testTargetKind()
	{
		final var values = _fixture.query().suitableTargets(target(20, 40, null, Set.of(NpcKind.RAID_BOSS), null, null, PageRequest.first(10))).values();
		PhantomAssertions.assertEquals(List.of(100), values.stream().map(value -> value.npc().npcId()).toList(), "Target kind filter is incorrect.");
	}

	private void testTargetDrop()
	{
		final var values = _fixture.query().suitableTargets(target(20, 21, null, Set.of(), 1, null, PageRequest.first(10))).values();
		PhantomAssertions.assertEquals(List.of(102, 103), values.stream().map(value -> value.npc().npcId()).toList(), "Target drop reverse filter is incomplete.");
	}

	private void testTargetSpoil()
	{
		final var values = _fixture.query().suitableTargets(target(20, 21, null, Set.of(), null, 3, PageRequest.first(10))).values();
		PhantomAssertions.assertEquals(List.of(102), values.stream().map(value -> value.npc().npcId()).toList(), "Target spoil reverse filter is incorrect.");
	}

	private void testCursor()
	{
		final KnowledgePage<DropFact> first = _fixture.query().dropSources(1, PageRequest.first(1));
		final KnowledgePage<DropFact> second = _fixture.query().dropSources(1, new PageRequest(1, first.nextCursor()));
		PhantomAssertions.assertTrue(first.hasMore(), "First bounded page lost continuation state.");
		PhantomAssertions.assertFalse(first.values().getFirst().equals(second.values().getFirst()), "Stable cursor repeated a fact.");
	}

	private void testPageBound()
	{
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> new PageRequest(257, null), "Page above 256 was accepted.");
		PhantomAssertions.assertTrue(_fixture.query().dropSources(1, PageRequest.first(256)).values().size() <= 256, "Public page exceeded 256 facts.");
	}

	private void testRangeBound()
	{
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> target(1, 102, null, Set.of(), null, null, PageRequest.first(1)), "Target level range above 100 was accepted.");
	}

	private void testClassIntrinsic()
	{
		PhantomAssertions.assertEquals(1, _fixture.snapshot().classFactsByClassId().get(1).classId(), "Class intrinsic direct index is missing.");
	}

	private void testClassCapabilities()
	{
		PhantomAssertions.assertEquals(12, _fixture.query().classCapabilities(1, PageRequest.first(20)).values().size(), "Class capability index is incomplete.");
	}

	private void testCapabilityReverse()
	{
		PhantomAssertions.assertEquals(1, _fixture.query().classesForCapability("combat.tank", 800, PageRequest.first(10)).values().getFirst().classId(), "Capability reverse index is incorrect.");
	}

	private void testContent()
	{
		PhantomAssertions.assertEquals(ContentKind.RIFT, _fixture.query().content("rift.synthetic").orElseThrow().contentKind(), "Content direct index is incorrect.");
	}

	private void testContentReverse()
	{
		PhantomAssertions.assertEquals(3, _fixture.query().contentsRequiring("combat.tank", PageRequest.first(10)).values().size(), "Content capability reverse index is incomplete.");
	}

	private void testAtomicFailure()
	{
		final PhantomGameKnowledgeService service = new PhantomGameKnowledgeService(() ->
		{
			throw new PhantomGameKnowledgeValidationException("reference", "expected");
		});
		PhantomAssertions.assertThrows(PhantomGameKnowledgeValidationException.class, service::start, "Failed startup did not propagate validation failure.");
		PhantomAssertions.assertEquals(PhantomGameKnowledgeService.State.FAILED, service.snapshot().state(), "Failed startup published a running service.");
		PhantomAssertions.assertEquals("none", service.snapshot().combinedHash(), "Failed startup published a partial snapshot.");
		PhantomAssertions.assertEquals("none", service.snapshot().hashes().itemsHash(), "Failed startup published a component hash.");
	}

	private void testLifecycle()
	{
		final PhantomGameKnowledgeService service = PhantomGameKnowledgeService.inertForTesting("topology");
		PhantomAssertions.assertEquals(PhantomGameKnowledgeService.State.NEW, service.snapshot().state(), "Inert service did not start at NEW.");
		PhantomAssertions.assertTrue(service.start(), "Inert service did not start.");
		final PhantomGameKnowledgeQuery retained = service.query();
		PhantomAssertions.assertTrue(service.beginStop(), "Knowledge beginStop failed.");
		PhantomAssertions.assertThrows(IllegalStateException.class, service::query, "Knowledge accepted a new query during stop.");
		PhantomAssertions.assertTrue(service.finishStop(), "Knowledge finishStop failed.");
		PhantomAssertions.assertEquals(0, retained.snapshot().counts().items(), "Already returned immutable query became unsafe after stop.");
	}

	private void testNoSourceAfterBuild()
	{
		final int loads = _fixture.backend()._loads;
		final int sourceChecks = _fixture.backend()._sourceChecks;
		for (int index = 0; index < 1000; index++)
		{
			_fixture.query().findItem(1);
			_fixture.query().dropSources(1, PageRequest.first(1));
			_fixture.query().recipesUsing(9, PageRequest.first(1));
		}
		PhantomAssertions.assertEquals(loads, _fixture.backend()._loads, "Query path reloaded authoritative data.");
		PhantomAssertions.assertEquals(sourceChecks, _fixture.backend()._sourceChecks, "Query path scanned source evidence.");
	}

	private void testNoMutableServerFields()
	{
		for (Class<?> type : List.of(PhantomGameKnowledgeSnapshot.class, PhantomGameKnowledgeQuery.class, ItemFact.class, NpcFact.class, DropFact.class, SpawnFact.class, RecipeFact.class, ManorFact.class, ClassCapabilityFact.class, ContentRequirementFact.class))
		{
			for (Field field : type.getDeclaredFields())
			{
				final String name = field.getType().getName();
				PhantomAssertions.assertFalse(name.contains("NpcTemplate") || name.contains("ItemTemplate") || name.endsWith(".Spawn") || name.contains("RecipeList") || name.endsWith(".Skill") || name.endsWith(".Player") || name.endsWith(".Creature"), "Knowledge type exposes a mutable server object.");
			}
		}
	}

	private void testRuntimeDropOrdinals()
	{
		final List<DropFact> grouped = _fixture.snapshot().dropSpoilFacts().stream().filter(fact -> (fact.npcId() == 102) && (fact.groupOrdinal() == 0) && (fact.chanceModel() == ChanceModel.GROUP_CUMULATIVE)).toList();
		PhantomAssertions.assertEquals(List.of(1, 2), grouped.stream().map(DropFact::itemId).toList(), "Grouped runtime item order changed.");
		PhantomAssertions.assertEquals(List.of(0, 1), grouped.stream().map(DropFact::itemOrdinal).toList(), "Grouped runtime item ordinals changed.");
	}

	private void testDropOrdinalHash(PhantomTestContext context) throws Exception
	{
		final SyntheticFixture changed = build(context, "ordinal", false, false, false, 25d, true, 0);
		PhantomAssertions.assertFalse(_fixture.snapshot().npcDropSpoilHash().equals(changed.snapshot().npcDropSpoilHash()), "Authoritative drop ordinal change was lost from hashing.");
	}

	private void testDropGroupOrdinalHash(PhantomTestContext context) throws Exception
	{
		final SyntheticFixture changed = build(context, "group-ordinal", false, false, false, 25d, false, 0, true);
		PhantomAssertions.assertFalse(_fixture.snapshot().npcDropSpoilHash().equals(changed.snapshot().npcDropSpoilHash()), "Authoritative drop group ordinal change was lost from hashing.");
	}

	private void testServiceHashes()
	{
		final PhantomGameKnowledgeSnapshot.Hashes hashes = _fixture.service().snapshot().hashes();
		PhantomAssertions.assertEquals(_fixture.snapshot().itemsHash(), hashes.itemsHash(), "Service item hash differs from its generation.");
		PhantomAssertions.assertEquals(_fixture.snapshot().topologyHash(), hashes.topologyHash(), "Service topology hash differs from its generation.");
		PhantomAssertions.assertEquals(_fixture.snapshot().combinedHash(), hashes.combinedHash(), "Service combined hash differs from its generation.");
	}

	private void testInactiveHashes()
	{
		final PhantomGameKnowledgeSnapshot.Hashes hashes = PhantomGameKnowledgeService.ServiceSnapshot.inactive().hashes();
		PhantomAssertions.assertTrue(List.of(hashes.itemsHash(), hashes.npcDropSpoilHash(), hashes.spawnHash(), hashes.recipeHash(), hashes.manorHash(), hashes.classCapabilityHash(), hashes.contentRequirementHash(), hashes.topologyHash(), hashes.combinedHash()).stream().allMatch("none"::equals), "Inactive component hashes are not fixed none values.");
	}

	private void testSpawnAreaSummary()
	{
		final SpawnAreaSummary summary = _fixture.query().spawnAreas(102, PageRequest.first(1)).values().getFirst();
		PhantomAssertions.assertEquals(102, summary.npcId(), "Spawn-area summary changed NPC identity.");
		PhantomAssertions.assertFalse(List.of(SpawnAreaSummary.class.getRecordComponents()).stream().anyMatch(component -> component.getName().equals("representativePoints")), "Public spawn-area summary exposes nested exact points.");
	}

	private void testTargetAreaSummary()
	{
		final TargetFact target = _fixture.query().suitableTargets(target(20, 20, null, Set.of(), null, null, PageRequest.first(1))).values().getFirst();
		PhantomAssertions.assertTrue(target.representativeAreas().size() <= 64, "Target fact exceeded the nested spawn-area cap.");
		PhantomAssertions.assertEquals(target.totalSpawnAreaCount() > target.representativeAreas().size(), target.hasMoreSpawnAreas(), "Target fact lost bounded spawn-area continuation truth.");
	}

	private void testTargetSummaryType()
	{
		final TargetFact target = _fixture.query().suitableTargets(target(20, 20, null, Set.of(), null, null, PageRequest.first(1))).values().getFirst();
		PhantomAssertions.assertTrue(target.representativeAreas().stream().allMatch(SpawnAreaSummary.class::isInstance), "Target fact embeds internal spawn-area facts.");
	}

	private static TargetQuery target(int minimumLevel, int maximumLevel, Integer preferredLevel, Set<NpcKind> kinds, Integer drop, Integer spoil, PageRequest page)
	{
		return new TargetQuery(minimumLevel, maximumLevel, preferredLevel, null, null, kinds, true, true, null, drop, spoil, page);
	}

	private SyntheticFixture build(PhantomTestContext context, String suffix, boolean reverse, boolean duplicateItem, boolean missingReference, double groupedChance) throws Exception
	{
		return build(context, suffix, reverse, duplicateItem, missingReference, groupedChance, false, 0);
	}

	private SyntheticFixture build(PhantomTestContext context, String suffix, boolean reverse, boolean duplicateItem, boolean missingReference, double groupedChance, boolean swapGroupedOrdinals, int extraSpawnAreas) throws Exception
	{
		return build(context, suffix, reverse, duplicateItem, missingReference, groupedChance, swapGroupedOrdinals, extraSpawnAreas, false);
	}

	private SyntheticFixture build(PhantomTestContext context, String suffix, boolean reverse, boolean duplicateItem, boolean missingReference, double groupedChance, boolean swapGroupedOrdinals, int extraSpawnAreas, boolean swapGroupOrdinals) throws Exception
	{
		final Path root = context.reportsDirectory().resolve("knowledge-core-" + ProcessHandle.current().pid() + "-" + suffix);
		Files.createDirectories(root.resolve("curated"));
		_temporaryRoots.add(root);
		Files.writeString(root.resolve("Seeds.xml"), """
			<?xml version="1.0" encoding="UTF-8"?>
			<list>
				<castle id="1">
					<crop id="2" seedId="1" mature_Id="3" reward1="4" reward2="5" alternative="false" level="10" limit_seed="100" limit_crops="200" />
				</castle>
			</list>
			""", StandardCharsets.UTF_8);
		Files.writeString(root.resolve("curated/knowledge.xml"), curatedXml(), StandardCharsets.UTF_8);
		final SyntheticBackend backend = new SyntheticBackend(reverse, duplicateItem, missingReference, groupedChance, swapGroupedOrdinals, extraSpawnAreas, swapGroupOrdinals);
		final PhantomTopologyQuery topology = topology();
		final PhantomGameKnowledgeBuilder builder = new PhantomGameKnowledgeBuilder(backend, new PhantomStaticManorParser(root.resolve("Seeds.xml"), POLICY), new PhantomCuratedKnowledgeParser(root.resolve("curated"), backend, POLICY), topology, POLICY);
		final PhantomGameKnowledgeService service = new PhantomGameKnowledgeService(builder);
		service.start();
		return new SyntheticFixture(service.query().snapshot(), service.query(), service, backend);
	}

	static String curatedXml()
	{
		final StringBuilder result = new StringBuilder("""
			<?xml version="1.0" encoding="UTF-8"?>
			<knowledge schemaVersion="1" datasetId="synthetic-core" datasetVersion="1">
			""");
		int rank = 800;
		for (String capability : PhantomGameKnowledgeBuilder.REQUIRED_CAPABILITIES.stream().sorted().toList())
		{
			result.append("\t<classCapability classId=\"1\" capabilityKey=\"").append(capability).append("\" rank=\"").append(rank++).append("\">\n");
			result.append("\t\t<skill id=\"500\" level=\"1\" />\n");
			result.append("\t\t<source path=\"data/source.xml\" />\n");
			result.append("\t</classCapability>\n");
		}
		for (String content : List.of("rift.synthetic:RIFT:", "raid.synthetic:RAID:100", "epic.synthetic:EPIC:101"))
		{
			final String[] values = content.split(":", -1);
			result.append("\t<contentRequirement contentId=\"").append(values[0]).append("\" contentKind=\"").append(values[1]).append("\"");
			if (!values[2].isEmpty())
			{
				result.append(" npcId=\"").append(values[2]).append("\"");
			}
			result.append(" recommendedMinParty=\"1\" recommendedMaxParty=\"9\">\n");
			result.append("\t\t<requirement capabilityKey=\"combat.tank\" minimumCount=\"1\" minimumRank=\"800\" required=\"true\" />\n");
			result.append("\t\t<source path=\"data/source.xml\" />\n");
			result.append("\t</contentRequirement>\n");
		}
		result.append("</knowledge>\n");
		return result.toString();
	}

	static PhantomTopologyQuery topology()
	{
		final TopologyBackend backend = new TopologyBackend();
		final PhantomTopologyNode node = new PhantomTopologyNode("synthetic.area", PhantomTopologyNodeKind.FARMING_AREA, 0, PhantomTopologyArea.cuboid(0, 0, 1000, 0, 1000, -100, 100), null, List.of(), List.of());
		final PhantomTopologyNode empty = new PhantomTopologyNode("synthetic.empty", PhantomTopologyNodeKind.FARMING_AREA, 0, PhantomTopologyArea.cuboid(0, 3000, 4000, 3000, 4000, -100, 100), null, List.of(), List.of());
		final PhantomTopologySnapshot snapshot = PhantomTopologySnapshot.create(1, "synthetic", 1, 1, List.of(node, empty), List.of(), List.of(), backend, PhantomTopologyPolicy.productionDefaults());
		return new PhantomTopologyQuery(snapshot, backend, new PhantomTopologyMetrics());
	}

	record SyntheticFixture(PhantomGameKnowledgeSnapshot snapshot, PhantomGameKnowledgeQuery query, PhantomGameKnowledgeService service, SyntheticBackend backend)
	{
	}

	static final class SyntheticBackend implements PhantomGameKnowledgeBackend
	{
		private final boolean _reverse;
		private final boolean _duplicateItem;
		private final boolean _missingReference;
		private final double _groupedChance;
		private final boolean _swapGroupedOrdinals;
		private final int _extraSpawnAreas;
		private final boolean _swapGroupOrdinals;
		private int _loads;
		private int _sourceChecks;

		SyntheticBackend(boolean reverse, boolean duplicateItem, boolean missingReference, double groupedChance)
		{
			this(reverse, duplicateItem, missingReference, groupedChance, false, 0);
		}

		SyntheticBackend(boolean reverse, boolean duplicateItem, boolean missingReference, double groupedChance, boolean swapGroupedOrdinals, int extraSpawnAreas)
		{
			this(reverse, duplicateItem, missingReference, groupedChance, swapGroupedOrdinals, extraSpawnAreas, false);
		}

		SyntheticBackend(boolean reverse, boolean duplicateItem, boolean missingReference, double groupedChance, boolean swapGroupedOrdinals, int extraSpawnAreas, boolean swapGroupOrdinals)
		{
			_reverse = reverse;
			_duplicateItem = duplicateItem;
			_missingReference = missingReference;
			_groupedChance = groupedChance;
			_swapGroupedOrdinals = swapGroupedOrdinals;
			_extraSpawnAreas = extraSpawnAreas;
			_swapGroupOrdinals = swapGroupOrdinals;
		}

		@Override
		public BackendData load(PhantomGameKnowledgePolicy policy)
		{
			_loads++;
			final ArrayList<ItemFact> items = new ArrayList<>();
			for (int itemId = 1; itemId <= 10; itemId++)
			{
				items.add(new ItemFact(itemId, ItemCategory.ETC, "NONE", itemId, true, PhantomGameKnowledgeAuthority.SERVER_LOADER_FACT));
			}
			if (_missingReference)
			{
				items.removeIf(item -> item.itemId() == 9);
			}
			if (_duplicateItem)
			{
				items.add(items.getFirst());
			}
			final ArrayList<NpcFact> npcs = new ArrayList<>(List.of(
				new NpcFact(100, 23, NpcKind.RAID_BOSS, true, true, false, 100, 10, PhantomGameKnowledgeAuthority.SERVER_LOADER_FACT),
				new NpcFact(101, 40, NpcKind.GRAND_BOSS, true, true, false, 200, 20, PhantomGameKnowledgeAuthority.SERVER_LOADER_FACT),
				new NpcFact(102, 20, NpcKind.MONSTER, true, true, true, 10, 1, PhantomGameKnowledgeAuthority.SERVER_LOADER_FACT),
				new NpcFact(103, 21, NpcKind.MONSTER, true, true, false, 11, 1, PhantomGameKnowledgeAuthority.SERVER_LOADER_FACT),
				new NpcFact(104, 22, NpcKind.MONSTER, true, true, false, 12, 1, PhantomGameKnowledgeAuthority.SERVER_LOADER_FACT)));
			final ArrayList<DropFact> drops = new ArrayList<>(List.of(
				new DropFact(102, 1, DropSourceKind.DEATH_DROP, ChanceModel.GROUP_CUMULATIVE, _swapGroupOrdinals ? 1 : 0, _swapGroupedOrdinals ? 1 : 0, 50d, _groupedChance, 1, 2, PhantomGameKnowledgeAuthority.SERVER_LOADER_FACT),
				new DropFact(102, 2, DropSourceKind.DEATH_DROP, ChanceModel.GROUP_CUMULATIVE, _swapGroupOrdinals ? 1 : 0, _swapGroupedOrdinals ? 0 : 1, 50d, 15d, 1, 1, PhantomGameKnowledgeAuthority.SERVER_LOADER_FACT),
				new DropFact(102, 5, DropSourceKind.DEATH_DROP, ChanceModel.GROUP_CUMULATIVE, _swapGroupOrdinals ? 0 : 1, 0, 25d, 100d, 1, 1, PhantomGameKnowledgeAuthority.SERVER_LOADER_FACT),
				new DropFact(103, 1, DropSourceKind.DEATH_DROP, ChanceModel.UNGROUPED_INDEPENDENT, -1, 0, 0d, 10d, 1, 1, PhantomGameKnowledgeAuthority.SERVER_LOADER_FACT),
				new DropFact(100, 4, DropSourceKind.DEATH_DROP, ChanceModel.UNGROUPED_INDEPENDENT, -1, 0, 0d, 10d, 1, 1, PhantomGameKnowledgeAuthority.SERVER_LOADER_FACT),
				new DropFact(102, 3, DropSourceKind.SPOIL, ChanceModel.UNGROUPED_INDEPENDENT, -1, 0, 0d, 5d, 1, 1, PhantomGameKnowledgeAuthority.SERVER_LOADER_FACT)));
			final ArrayList<SpawnFact> spawns = new ArrayList<>(List.of(
				new SpawnFact(100, 0, 0, 2000, 2000, 0, 1, 1, SpawnPointKind.EXACT, null, 6, PhantomGameKnowledgeAuthority.SERVER_LOADER_FACT),
				new SpawnFact(101, 0, 0, 0, 0, 0, 1, 0, SpawnPointKind.TERRITORY_OR_UNRESOLVED, null, null, PhantomGameKnowledgeAuthority.SERVER_LOADER_FACT),
				new SpawnFact(102, 0, 0, 100, 100, 0, 2, 2, SpawnPointKind.EXACT, null, 5, PhantomGameKnowledgeAuthority.SERVER_LOADER_FACT),
				new SpawnFact(103, 0, 0, 150, 150, 0, 1, 3, SpawnPointKind.EXACT, null, 5, PhantomGameKnowledgeAuthority.SERVER_LOADER_FACT),
				new SpawnFact(104, 0, 0, World.WORLD_X_MAX + 1, 100, 0, 1, 4, SpawnPointKind.EXACT, null, 5, PhantomGameKnowledgeAuthority.SERVER_LOADER_FACT)));
			for (int index = 1; index <= _extraSpawnAreas; index++)
			{
				spawns.add(new SpawnFact(102, index, index, 100, 100, 0, 1, 1000 + index, SpawnPointKind.EXACT, null, 5, PhantomGameKnowledgeAuthority.SERVER_LOADER_FACT));
			}
			final ArrayList<RecipeFact> recipes = new ArrayList<>(List.of(new RecipeFact(10, 6, 7, 1, 8, 1, 20, 1, 100, true, List.of(new IngredientFact(9, 2), new IngredientFact(10, 3)), PhantomGameKnowledgeAuthority.SERVER_LOADER_FACT)));
			final ArrayList<ClassIntrinsicFact> classes = new ArrayList<>(List.of(
				new ClassIntrinsicFact(0, "HUMAN", 0, false, false, null, PhantomGameKnowledgeAuthority.SERVER_LOADER_FACT),
				new ClassIntrinsicFact(1, "HUMAN", 3, false, false, 0, PhantomGameKnowledgeAuthority.SERVER_LOADER_FACT)));
			if (_reverse)
			{
				Collections.reverse(items);
				Collections.reverse(npcs);
				Collections.reverse(drops);
				Collections.reverse(spawns);
				Collections.reverse(recipes);
				Collections.reverse(classes);
			}
			return new BackendData(items, npcs, drops, spawns, recipes, classes, Map.of(0, List.of(), 1, List.of(new SkillEvidence(500, 1))));
		}

		@Override
		public boolean sourceExists(String relativeDatapackPath)
		{
			_sourceChecks++;
			return "data/source.xml".equals(relativeDatapackPath);
		}
	}

	private static final class TopologyBackend implements PhantomTopologyValidationBackend
	{
		@Override
		public int mapRegionLocId(int x, int y)
		{
			return 5;
		}

		@Override
		public Optional<PhantomTopologyValidationBackend.NpcFact> npc(int npcId)
		{
			return Optional.empty();
		}

		@Override
		public List<PhantomTopologyValidationBackend.SpawnFact> spawns(int npcId, int maximumResults)
		{
			return List.of();
		}

		@Override
		public Optional<DoorFact> door(int doorId)
		{
			return Optional.empty();
		}

		@Override
		public DoorState doorState(int doorId)
		{
			return DoorState.MISSING;
		}

		@Override
		public boolean sourceExists(String relativeDatapackPath)
		{
			return true;
		}
	}
}
