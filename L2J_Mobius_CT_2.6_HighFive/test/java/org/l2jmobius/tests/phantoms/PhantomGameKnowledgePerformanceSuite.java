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
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;

import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.NpcFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.PageRequest;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.TargetQuery;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeQuery;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeSnapshot;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeService;
import org.l2jmobius.tests.phantoms.PhantomGameKnowledgeParitySuite.ProductionFixture;

public final class PhantomGameKnowledgePerformanceSuite implements PhantomTestSuite
{
	private static final int ITERATIONS = 100_000;
	private ProductionFixture _fixture;
	private PhantomGameKnowledgeSnapshot _snapshot;
	private PhantomGameKnowledgeQuery _query;
	private int[] _itemIds;
	private int[] _ingredientIds;
	private int[] _classIds;
	private TargetQuery _targetQuery;
	private int _baselineLoads;
	private int _baselineSourceChecks;

	@Override
	public String id()
	{
		return "knowledge-performance";
	}

	@Override
	public void beforeAll(PhantomTestContext context) throws Exception
	{
		final long started = System.nanoTime();
		_fixture = ProductionFixture.start(context);
		_snapshot = _fixture.snapshot();
		_query = _fixture.query();
		_itemIds = _snapshot.itemById().keySet().stream().mapToInt(Integer::intValue).sorted().toArray();
		_ingredientIds = _snapshot.recipesByIngredient().keySet().stream().mapToInt(Integer::intValue).sorted().toArray();
		_classIds = _snapshot.capabilitiesByClassId().keySet().stream().mapToInt(Integer::intValue).sorted().toArray();
		final NpcFact target = _snapshot.npcs().stream().filter(NpcFact::attackable).filter(NpcFact::targetable).findFirst().orElseThrow();
		_targetQuery = new TargetQuery(target.level(), target.level(), target.level(), null, null, Set.of(), true, true, null, null, null, PageRequest.first(16));
		_baselineLoads = _fixture.backend().loads();
		_baselineSourceChecks = _fixture.backend().sourceChecks();
		context.record("knowledge.performance.environmentAndBuildMillis", (System.nanoTime() - started) / 1_000_000L);
		context.record("knowledge.performance.serviceBuildMillis", _fixture.serviceSnapshot().buildDurationMillis());
		context.record("knowledge.performance.iterationsPerCategory", ITERATIONS);
		context.record("knowledge.performance.combinedHash", _snapshot.combinedHash());
		writeCanonicalSummary(context);
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
		registry.add("01-real-corpus-policy-bounds", _ -> testBounds());
		registry.add("02-item-source-lookups-100000", this::testItemLookups);
		registry.add("03-recipe-reverse-lookups-100000", this::testRecipeLookups);
		registry.add("04-class-capability-lookups-100000", this::testClassLookups);
		registry.add("05-bounded-target-lookups-100000", this::testTargetLookups);
		registry.add("06-no-loader-file-db-after-build", _ -> testNoSources());
		registry.add("07-no-worker-future-profile-state", _ -> testNoWorkers());
		registry.add("08-page-and-hash-structural-gates", _ -> testStructural());
	}

	private void testBounds()
	{
		final var counts = _snapshot.counts();
		PhantomAssertions.assertTrue(counts.items() <= _snapshot.policy().maximumItems(), "Performance corpus exceeds item policy.");
		PhantomAssertions.assertTrue(counts.npcs() <= _snapshot.policy().maximumNpcTemplates(), "Performance corpus exceeds NPC policy.");
		PhantomAssertions.assertTrue((counts.deathDrops() + counts.spoils()) <= _snapshot.policy().maximumDropSpoilFacts(), "Performance corpus exceeds drop/spoil policy.");
		PhantomAssertions.assertTrue(counts.spawnFacts() <= _snapshot.policy().maximumSpawnFacts(), "Performance corpus exceeds spawn policy.");
		PhantomAssertions.assertTrue(counts.recipes() <= _snapshot.policy().maximumRecipes(), "Performance corpus exceeds recipe policy.");
		PhantomAssertions.assertTrue(counts.classCapabilities() <= _snapshot.policy().maximumClassCapabilityFacts(), "Performance corpus exceeds capability policy.");
	}

	private void testItemLookups(PhantomTestContext context)
	{
		final long started = System.nanoTime();
		for (int index = 0; index < ITERATIONS; index++)
		{
			final int itemId = _itemIds[index % _itemIds.length];
			if ((index & 1) == 0)
			{
				_query.dropSources(itemId, PageRequest.first(8));
			}
			else
			{
				_query.spoilSources(itemId, PageRequest.first(8));
			}
		}
		context.record("knowledge.performance.itemSourceLookupMillis", elapsed(started));
	}

	private void testRecipeLookups(PhantomTestContext context)
	{
		final long started = System.nanoTime();
		for (int index = 0; index < ITERATIONS; index++)
		{
			_query.recipesUsing(_ingredientIds[index % _ingredientIds.length], PageRequest.first(8));
		}
		context.record("knowledge.performance.recipeReverseLookupMillis", elapsed(started));
	}

	private void testClassLookups(PhantomTestContext context)
	{
		final long started = System.nanoTime();
		for (int index = 0; index < ITERATIONS; index++)
		{
			_query.classCapabilities(_classIds[index % _classIds.length], PageRequest.first(8));
		}
		context.record("knowledge.performance.classCapabilityLookupMillis", elapsed(started));
	}

	private void testTargetLookups(PhantomTestContext context)
	{
		final long started = System.nanoTime();
		for (int index = 0; index < ITERATIONS; index++)
		{
			PhantomAssertions.assertTrue(_query.suitableTargets(_targetQuery).values().size() <= 16, "Bounded target query exceeded its page.");
		}
		context.record("knowledge.performance.targetLookupMillis", elapsed(started));
	}

	private static long elapsed(long started)
	{
		return (System.nanoTime() - started) / 1_000_000L;
	}

	private void testNoSources()
	{
		PhantomAssertions.assertEquals(_baselineLoads, _fixture.backend().loads(), "Performance queries touched the loader seam.");
		PhantomAssertions.assertEquals(_baselineSourceChecks, _fixture.backend().sourceChecks(), "Performance queries touched datapack source evidence.");
	}

	private void testNoWorkers()
	{
		for (Class<?> type : List.of(PhantomGameKnowledgeService.class, PhantomGameKnowledgeQuery.class, PhantomGameKnowledgeSnapshot.class))
		{
			for (Field field : type.getDeclaredFields())
			{
				PhantomAssertions.assertFalse(Thread.class.isAssignableFrom(field.getType()) || Executor.class.isAssignableFrom(field.getType()) || Future.class.isAssignableFrom(field.getType()), "Knowledge owns a worker/thread/Future.");
				PhantomAssertions.assertFalse(field.getName().toLowerCase(java.util.Locale.ROOT).contains("profile"), "Knowledge owns per-profile state.");
			}
		}
	}

	private void testStructural()
	{
		PhantomAssertions.assertEquals(256, _snapshot.policy().maximumQueryPageSize(), "Production query page bound changed.");
		PhantomAssertions.assertEquals(64, _snapshot.combinedHash().length(), "Combined canonical hash is not SHA-256.");
		PhantomAssertions.assertTrue(List.of(_fixture.serviceSnapshot().hashes().itemsHash(), _fixture.serviceSnapshot().hashes().npcDropSpoilHash(), _fixture.serviceSnapshot().hashes().spawnHash(), _fixture.serviceSnapshot().hashes().recipeHash(), _fixture.serviceSnapshot().hashes().manorHash(), _fixture.serviceSnapshot().hashes().classCapabilityHash(), _fixture.serviceSnapshot().hashes().contentRequirementHash(), _fixture.serviceSnapshot().hashes().topologyHash(), _fixture.serviceSnapshot().hashes().combinedHash()).stream().allMatch(hash -> hash.length() == 64), "Service component hash diagnostics are incomplete.");
		PhantomAssertions.assertEquals(PhantomGameKnowledgeService.State.RUNNING, _fixture.serviceSnapshot().state(), "Performance service is not a single running generation.");
		PhantomAssertions.assertEquals(1L, _fixture.serviceSnapshot().metrics().buildsStarted(), "Performance service performed more than one build.");
		PhantomAssertions.assertEquals(1L, _fixture.serviceSnapshot().metrics().buildsCompleted(), "Performance service did not publish exactly one build.");
	}

	private void writeCanonicalSummary(PhantomTestContext context) throws Exception
	{
		final String summary = """
			schemaVersion=%d
			datasetId=%s
			datasetVersion=%d
			generation=%d
			items=%d
			npcs=%d
			deathDrops=%d
			spoils=%d
			spawnFacts=%d
			spawnAreas=%d
			recipes=%d
			recipeIngredients=%d
			manorFacts=%d
			classFacts=%d
			classCapabilities=%d
			contentRequirements=%d
			itemsHash=%s
			npcDropSpoilHash=%s
			spawnHash=%s
			recipeHash=%s
			manorHash=%s
			classCapabilityHash=%s
			contentRequirementHash=%s
			topologyHash=%s
			combinedHash=%s
			itemSourceLookups=%d
			recipeReverseLookups=%d
			classCapabilityLookups=%d
			boundedTargetLookups=%d
			""".formatted(_snapshot.schemaVersion(), _snapshot.datasetId(), _snapshot.datasetVersion(), _snapshot.generation(), _snapshot.counts().items(), _snapshot.counts().npcs(), _snapshot.counts().deathDrops(), _snapshot.counts().spoils(), _snapshot.counts().spawnFacts(), _snapshot.counts().spawnAreas(), _snapshot.counts().recipes(), _snapshot.counts().recipeIngredients(), _snapshot.counts().manorFacts(), _snapshot.counts().classFacts(), _snapshot.counts().classCapabilities(), _snapshot.counts().contentRequirements(), _snapshot.itemsHash(), _snapshot.npcDropSpoilHash(), _snapshot.spawnHash(), _snapshot.recipeHash(), _snapshot.manorHash(), _snapshot.classCapabilityHash(), _snapshot.contentRequirementHash(), _snapshot.topologyHash(), _snapshot.combinedHash(), ITERATIONS, ITERATIONS, ITERATIONS, ITERATIONS);
		Files.createDirectories(context.reportsDirectory());
		Files.writeString(context.reportsDirectory().resolve("knowledge-performance-summary.txt"), summary, StandardCharsets.UTF_8);
	}
}
