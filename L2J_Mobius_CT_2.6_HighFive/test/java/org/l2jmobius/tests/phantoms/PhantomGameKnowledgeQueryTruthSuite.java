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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.l2jmobius.gameserver.phantoms.knowledge.PhantomCuratedKnowledgeParser;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeBuilder;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.ContentKind;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.ContentRequirementFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.KnowledgePage;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.NpcKind;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.PageRequest;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.SpawnAreaSummary;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.SpawnFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.TargetFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.TargetQuery;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgePolicy;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeQuery;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeService;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeSnapshot;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomStaticManorParser;

public final class PhantomGameKnowledgeQueryTruthSuite implements PhantomTestSuite
{
	private static final PhantomGameKnowledgePolicy POLICY = PhantomGameKnowledgePolicy.productionDefaults();
	private Path _temporaryRoot;
	private PhantomGameKnowledgeService _service;
	private PhantomGameKnowledgeQuery _query;

	@Override
	public String id()
	{
		return "knowledge-query-truth";
	}

	@Override
	public void beforeAll(PhantomTestContext context) throws Exception
	{
		_temporaryRoot = context.reportsDirectory().resolve("knowledge-query-truth-" + ProcessHandle.current().pid());
		Files.createDirectories(_temporaryRoot.resolve("curated"));
		Files.writeString(_temporaryRoot.resolve("Seeds.xml"), """
			<?xml version="1.0" encoding="UTF-8"?>
			<list>
				<castle id="1">
					<crop id="2" seedId="1" mature_Id="3" reward1="4" reward2="5" alternative="false" level="10" limit_seed="100" limit_crops="200" />
				</castle>
			</list>
			""", StandardCharsets.UTF_8);
		Files.writeString(_temporaryRoot.resolve("curated/knowledge.xml"), PhantomGameKnowledgeCoreSuite.curatedXml(), StandardCharsets.UTF_8);
		final PhantomGameKnowledgeCoreSuite.SyntheticBackend backend = new PhantomGameKnowledgeCoreSuite.SyntheticBackend(false, false, false, 25d, false, 70);
		final PhantomGameKnowledgeBuilder builder = new PhantomGameKnowledgeBuilder(backend, new PhantomStaticManorParser(_temporaryRoot.resolve("Seeds.xml"), POLICY), new PhantomCuratedKnowledgeParser(_temporaryRoot.resolve("curated"), backend, POLICY), PhantomGameKnowledgeCoreSuite.topology(), POLICY);
		_service = new PhantomGameKnowledgeService(builder);
		PhantomAssertions.assertTrue(_service.start(), "Query-truth knowledge service did not start.");
		_query = _service.query();
		context.record("knowledge.queryTruth.cases", 14);
		context.record("knowledge.queryTruth.combinedHash", _query.snapshot().combinedHash());
	}

	@Override
	public void afterAll(PhantomTestContext context) throws Exception
	{
		if ((_temporaryRoot != null) && Files.exists(_temporaryRoot))
		{
			try (var stream = Files.walk(_temporaryRoot))
			{
				for (Path path : stream.sorted(Collections.reverseOrder()).toList())
				{
					Files.deleteIfExists(path);
				}
			}
		}
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("01-missing-topology-is-empty", _ -> assertEmpty(target("missing.topology", null, null, null, PageRequest.first(10))));
		registry.add("02-known-empty-topology-is-empty", _ -> assertEmpty(target("synthetic.empty", null, null, null, PageRequest.first(10))));
		registry.add("03-missing-map-region-is-empty", _ -> assertEmpty(target(null, 999, null, null, PageRequest.first(10))));
		registry.add("04-loaded-item-without-drops-is-empty", _ -> assertEmpty(target(null, null, 10, null, PageRequest.first(10))));
		registry.add("05-unknown-drop-item-is-empty", _ -> assertEmpty(target(null, null, 9999, null, PageRequest.first(10))));
		registry.add("06-loaded-item-without-spoil-is-empty", _ -> assertEmpty(target(null, null, null, 1, PageRequest.first(10))));
		registry.add("07-unknown-spoil-item-is-empty", _ -> assertEmpty(target(null, null, null, 9999, PageRequest.first(10))));
		registry.add("08-empty-filter-intersection-is-empty", _ -> assertEmpty(target("synthetic.area", null, 4, null, PageRequest.first(10))));
		registry.add("09-empty-result-ignores-arbitrary-cursor", _ -> assertEmpty(target("missing.topology", null, null, null, new PageRequest(10, "arbitrary-cursor"))));
		registry.add("10-spawn-area-page-is-lightweight", _ -> testSpawnAreaSummary());
		registry.add("11-target-area-summaries-are-capped", _ -> testTargetCap());
		registry.add("12-exact-spawn-page-is-capped", _ -> testSpawnFactCap());
		registry.add("13-service-exposes-component-hashes", _ -> testServiceHashes());
		registry.add("14-content-kind-query-preserves-exact-truth", _ -> testContentKindQuery());
	}

	private void testContentKindQuery()
	{
		final KnowledgePage<ContentRequirementFact> raid = _query.contents(ContentKind.RAID, PageRequest.first(1));
		PhantomAssertions.assertEquals(List.of("raid.synthetic"), raid.values().stream().map(ContentRequirementFact::contentId).toList(), "RAID content-kind query changed deterministic truth.");
		PhantomAssertions.assertFalse(raid.hasMore(), "Single RAID content unexpectedly advertised another page.");
		PhantomAssertions.assertEquals(_query.content("raid.synthetic").orElseThrow(), raid.values().getFirst(), "Exact content(contentId) truth diverged from kind enumeration.");
		PhantomAssertions.assertEquals(List.of("epic.synthetic"), _query.contents(ContentKind.EPIC, PageRequest.first(1)).values().stream().map(ContentRequirementFact::contentId).toList(), "EPIC content-kind query changed deterministic truth.");
		PhantomAssertions.assertThrows(NullPointerException.class, () -> _query.contents(null, PageRequest.first(1)), "Null content kind did not fail closed.");
	}

	private void assertEmpty(TargetQuery target)
	{
		final KnowledgePage<TargetFact> page = _query.suitableTargets(target);
		PhantomAssertions.assertTrue(page.values().isEmpty(), "Requested empty target filter was treated as absent.");
		PhantomAssertions.assertFalse(page.hasMore(), "Empty target result has continuation state.");
		PhantomAssertions.assertEquals(null, page.nextCursor(), "Empty target result invented a cursor.");
	}

	private void testSpawnAreaSummary()
	{
		final KnowledgePage<SpawnAreaSummary> page = _query.spawnAreas(102, PageRequest.first(256));
		PhantomAssertions.assertEquals(71, page.values().size(), "Public spawn-area page lost summaries.");
		PhantomAssertions.assertFalse(List.of(SpawnAreaSummary.class.getRecordComponents()).stream().anyMatch(component -> component.getName().equals("representativePoints")), "Public spawn-area page exposes nested exact points.");
	}

	private void testTargetCap()
	{
		final TargetFact target = _query.suitableTargets(new TargetQuery(20, 20, 20, null, null, Set.of(NpcKind.MONSTER), true, true, null, null, null, PageRequest.first(1))).values().getFirst();
		PhantomAssertions.assertEquals(71, target.totalSpawnAreaCount(), "Target fact lost total spawn-area cardinality.");
		PhantomAssertions.assertEquals(64, target.representativeAreas().size(), "Target fact did not apply the 64-area summary cap.");
		PhantomAssertions.assertTrue(target.hasMoreSpawnAreas(), "Target fact did not disclose truncated area summaries.");
	}

	private void testSpawnFactCap()
	{
		final KnowledgePage<SpawnFact> page = _query.spawnFacts(102, PageRequest.first(256));
		PhantomAssertions.assertEquals(71, page.values().size(), "Exact spawn facts are not available through their dedicated page.");
		PhantomAssertions.assertTrue(page.values().size() <= 256, "Exact spawn fact page exceeded the fixed bound.");
	}

	private void testServiceHashes()
	{
		final PhantomGameKnowledgeSnapshot snapshot = _query.snapshot();
		final PhantomGameKnowledgeSnapshot.Hashes hashes = _service.snapshot().hashes();
		PhantomAssertions.assertEquals(snapshot.itemsHash(), hashes.itemsHash(), "Service item hash is stale.");
		PhantomAssertions.assertEquals(snapshot.npcDropSpoilHash(), hashes.npcDropSpoilHash(), "Service NPC/drop/spoil hash is stale.");
		PhantomAssertions.assertEquals(snapshot.spawnHash(), hashes.spawnHash(), "Service spawn hash is stale.");
		PhantomAssertions.assertEquals(snapshot.recipeHash(), hashes.recipeHash(), "Service recipe hash is stale.");
		PhantomAssertions.assertEquals(snapshot.manorHash(), hashes.manorHash(), "Service manor hash is stale.");
		PhantomAssertions.assertEquals(snapshot.classCapabilityHash(), hashes.classCapabilityHash(), "Service class capability hash is stale.");
		PhantomAssertions.assertEquals(snapshot.contentRequirementHash(), hashes.contentRequirementHash(), "Service content requirement hash is stale.");
		PhantomAssertions.assertEquals(snapshot.topologyHash(), hashes.topologyHash(), "Service topology hash is stale.");
		PhantomAssertions.assertEquals(snapshot.combinedHash(), hashes.combinedHash(), "Service combined hash is stale.");
	}

	private TargetQuery target(String topologyNodeId, Integer mapRegionLocId, Integer dropsItemId, Integer spoilsItemId, PageRequest page)
	{
		return new TargetQuery(20, 40, null, topologyNodeId, mapRegionLocId, Set.of(), true, true, null, dropsItemId, spoilsItemId, page);
	}
}
