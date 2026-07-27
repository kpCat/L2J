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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.l2jmobius.gameserver.data.xml.DoorData;
import org.l2jmobius.gameserver.data.xml.MapRegionData;
import org.l2jmobius.gameserver.data.xml.SpawnData;
import org.l2jmobius.gameserver.phantoms.knowledge.L2jGameKnowledgeBackend;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomCuratedKnowledgeParser;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeBackend;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeBackend.BackendData;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeBuilder;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.DropFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.DropSourceKind;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.ItemFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.ManorFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.NpcFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.PageRequest;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.RecipeFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.SpawnFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.SpawnPointKind;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgePolicy;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeQuery;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeService;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeSnapshot;
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
		registry.add("03-all-drop-spoil-facts-exact", _ -> testDrops());
		registry.add("04-zaken-adena-authoritative-range", _ -> testZakenAdenaRange());
		registry.add("05-all-authoritative-drop-count-ranges-valid", _ -> testDropCountRanges());
		registry.add("06-drop-spoil-references-and-reverse-indexes", _ -> testDropIndexes());
		registry.add("07-all-spawns-represented", _ -> testSpawns());
		registry.add("08-all-recipes-and-ingredients-exact", _ -> testRecipes());
		registry.add("09-static-seeds-parser-parity", _ -> testManor());
		registry.add("10-no-mutable-manager-or-db-source", this::testForbiddenSources);
		registry.add("11-accepted-topology-hash-and-mapping", _ -> testTopology());
		registry.add("12-component-counts-within-policy", _ -> testBounds());
		registry.add("13-canonical-hashes-repeat-build", _ -> testRepeatBuild());
		registry.add("14-query-source-seam-stable", _ -> testNoQuerySourceAccess());
	}

	private void testItems()
	{
		PhantomAssertions.assertEquals(_fixture.loaded().items(), _fixture.snapshot().items(), "Loaded item parity changed.");
		PhantomAssertions.assertEquals(_fixture.loaded().items().size(), _fixture.snapshot().itemById().size(), "Loaded item direct index is incomplete.");
	}

	private void testNpcs()
	{
		PhantomAssertions.assertEquals(_fixture.loaded().npcs(), _fixture.snapshot().npcs(), "Loaded NPC parity changed.");
		PhantomAssertions.assertEquals(_fixture.loaded().npcs().size(), _fixture.snapshot().npcById().size(), "Loaded NPC direct index is incomplete.");
	}

	private void testDrops()
	{
		PhantomAssertions.assertEquals(_fixture.loaded().drops(), _fixture.snapshot().dropSpoilFacts(), "Loaded grouped/ungrouped drop and spoil parity changed.");
		final long sourceDeath = _fixture.loaded().drops().stream().filter(fact -> fact.sourceKind() == DropSourceKind.DEATH_DROP).count();
		final long sourceSpoil = _fixture.loaded().drops().stream().filter(fact -> fact.sourceKind() == DropSourceKind.SPOIL).count();
		PhantomAssertions.assertEquals(sourceDeath, (long) _fixture.snapshot().counts().deathDrops(), "Death-drop count changed.");
		PhantomAssertions.assertEquals(sourceSpoil, (long) _fixture.snapshot().counts().spoils(), "Spoil count changed.");
	}

	private void testZakenAdenaRange()
	{
		final List<DropFact> facts = _fixture.loaded().drops().stream().filter(fact -> (fact.npcId() == 29181) && (fact.itemId() == 57) && (fact.sourceKind() == DropSourceKind.DEATH_DROP)).toList();
		PhantomAssertions.assertEquals(1, facts.size(), "Zaken Adena authoritative drop identity is not unique.");
		final DropFact fact = facts.getFirst();
		PhantomAssertions.assertEquals(9_000_000L, fact.minimumCount(), "Zaken Adena authoritative minimum changed.");
		PhantomAssertions.assertEquals(11_000_000L, fact.maximumCount(), "Zaken Adena authoritative maximum changed.");
		PhantomAssertions.assertEquals(100.0, fact.rawGroupChance(), "Zaken Adena raw group chance changed.");
		PhantomAssertions.assertEquals(100.0, fact.rawItemChance(), "Zaken Adena raw item chance changed.");
		final DropFact snapshotFact = _fixture.snapshot().dropSpoilFacts().stream().filter(candidate -> candidate.stableKey().equals(fact.stableKey())).findFirst().orElseThrow();
		PhantomAssertions.assertEquals(fact, snapshotFact, "Zaken Adena raw source was normalized, reordered or excluded.");
	}

	private void testDropCountRanges()
	{
		for (DropFact fact : _fixture.loaded().drops())
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
		PhantomAssertions.assertEquals(_fixture.loaded().spawns().size(), _fixture.snapshot().spawnFacts().size(), "Loaded spawn count changed.");
		final Map<String, SpawnFact> actual = new HashMap<>();
		_fixture.snapshot().spawnFacts().forEach(fact -> actual.put(fact.stableKey(), fact));
		for (SpawnFact expected : _fixture.loaded().spawns())
		{
			final SpawnFact fact = actual.get(expected.stableKey());
			PhantomAssertions.assertTrue(fact != null, "Loaded spawn is missing.");
			PhantomAssertions.assertEquals(expected.npcId(), fact.npcId(), "Loaded spawn NPC changed.");
			PhantomAssertions.assertEquals(expected.instanceId(), fact.instanceId(), "Loaded spawn instance changed.");
			PhantomAssertions.assertEquals(expected.x(), fact.x(), "Loaded spawn X changed.");
			PhantomAssertions.assertEquals(expected.y(), fact.y(), "Loaded spawn Y changed.");
			PhantomAssertions.assertEquals(expected.z(), fact.z(), "Loaded spawn Z changed.");
			PhantomAssertions.assertEquals(expected.amount(), fact.amount(), "Loaded spawn amount changed.");
			PhantomAssertions.assertEquals(expected.pointKind(), fact.pointKind(), "Loaded spawn point semantics changed.");
			if (fact.pointKind() == SpawnPointKind.TERRITORY_OR_UNRESOLVED)
			{
				PhantomAssertions.assertEquals(0, fact.x(), "Unresolved spawn retained a runtime-random X coordinate.");
				PhantomAssertions.assertEquals(0, fact.y(), "Unresolved spawn retained a runtime-random Y coordinate.");
				PhantomAssertions.assertEquals(0, fact.z(), "Unresolved spawn retained a runtime-random Z coordinate.");
			}
		}
	}

	private void testRecipes()
	{
		PhantomAssertions.assertEquals(_fixture.loaded().recipes(), _fixture.snapshot().recipes(), "Loaded recipe/ingredient parity changed.");
		final long indexed = _fixture.snapshot().recipesByIngredient().values().stream().flatMap(List::stream).map(RecipeFact::recipeListId).distinct().count();
		PhantomAssertions.assertEquals((long) _fixture.loaded().recipes().size(), indexed, "Recipe ingredient reverse graph lost a recipe.");
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

	private void testRepeatBuild()
	{
		final PhantomGameKnowledgeSnapshot repeated = _fixture.newBuilder().build();
		PhantomAssertions.assertEquals(_fixture.snapshot().itemsHash(), repeated.itemsHash(), "Repeated item component hash changed.");
		PhantomAssertions.assertEquals(_fixture.snapshot().npcDropSpoilHash(), repeated.npcDropSpoilHash(), "Repeated NPC/drop/spoil component hash changed.");
		PhantomAssertions.assertEquals(_fixture.snapshot().spawnHash(), repeated.spawnHash(), "Repeated spawn component hash changed.");
		PhantomAssertions.assertEquals(_fixture.snapshot().recipeHash(), repeated.recipeHash(), "Repeated recipe component hash changed.");
		PhantomAssertions.assertEquals(_fixture.snapshot().manorHash(), repeated.manorHash(), "Repeated manor component hash changed.");
		PhantomAssertions.assertEquals(_fixture.snapshot().classCapabilityHash(), repeated.classCapabilityHash(), "Repeated class component hash changed.");
		PhantomAssertions.assertEquals(_fixture.snapshot().contentRequirementHash(), repeated.contentRequirementHash(), "Repeated content component hash changed.");
		PhantomAssertions.assertEquals(_fixture.snapshot().combinedHash(), repeated.combinedHash(), "Repeated combined knowledge hash changed.");
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

	static final class ProductionFixture
	{
		private final PhantomHeadlessPlayerTestEnvironment _environment;
		private final CountingBackend _backend;
		private final PhantomGameKnowledgePolicy _policy;
		private final PhantomTopologySnapshot _topology;
		private final PhantomTopologyQuery _topologyQuery;
		private final PhantomGameKnowledgeService _service;
		private final BackendData _loaded;

		private ProductionFixture(PhantomHeadlessPlayerTestEnvironment environment, CountingBackend backend, PhantomGameKnowledgePolicy policy, PhantomTopologySnapshot topology, PhantomTopologyQuery topologyQuery, PhantomGameKnowledgeService service, BackendData loaded)
		{
			_environment = environment;
			_backend = backend;
			_policy = policy;
			_topology = topology;
			_topologyQuery = topologyQuery;
			_service = service;
			_loaded = loaded;
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
				final BackendData loaded = backend.load(policy);
				return new ProductionFixture(environment, backend, policy, topology, topologyQuery, service, loaded);
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

		PhantomGameKnowledgeBuilder newBuilder()
		{
			return builder(_backend, _policy, _topologyQuery);
		}

		PhantomGameKnowledgeSnapshot snapshot()
		{
			return _service.query().snapshot();
		}

		PhantomGameKnowledgeQuery query()
		{
			return _service.query();
		}

		BackendData loaded()
		{
			return _loaded;
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
