/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.tests.phantoms;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionCapabilityEvaluator;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionCatalog;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionCatalogBuilder;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.EquipItemRequest;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.OperationResult;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.OperationStatus;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.OwnedEquipmentFilter;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.PageRequest;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionPolicy;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionService;

public final class PhantomProgressionPerformanceSuite implements PhantomTestSuite
{
	private static final int CATALOG_BUILDS = 3;
	private static final int CLASS_QUERIES = 100_000;
	private static final int SKILL_QUERIES = 100_000;
	private static final int CAPABILITY_EVALUATIONS = 100_000;
	private static final int EQUIPMENT_QUERIES = 50_000;
	private static final int SUMMON_PET_QUERIES = 50_000;
	private static final int OPERATIONS = 10_000;
	private final PhantomProgressionLoaderFixture _fixture = new PhantomProgressionLoaderFixture();
	private final List<String> _hashes = new ArrayList<>();
	private long _elapsedMillis;
	private int _operationsAfter;
	private int _leasesAfter;
	private int _maximumPageObserved;
	private int _maximumCandidatesObserved;

	@Override
	public String id()
	{
		return "progression-performance";
	}

	@Override
	public void beforeAll(PhantomTestContext context) throws Exception
	{
		final long started = System.nanoTime();
		final PhantomProgressionPolicy policy = PhantomProgressionPolicy.productionDefaults();
		final PhantomProgressionCatalog initial = _fixture.start(context);
		_hashes.add(initial.combinedHash());
		final PhantomProgressionCatalogBuilder builder = new PhantomProgressionCatalogBuilder();
		for (int build = 1; build < CATALOG_BUILDS; build++)
		{
			_hashes.add(builder.build(_fixture.backend().load(policy), policy).combinedHash());
		}
		final List<Integer> classIds = initial.classes(PageRequest.first(256)).values().stream().map(value -> value.classId()).toList();
		_maximumPageObserved = Math.max(_maximumPageObserved, classIds.size());
		final List<org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.SkillRef> skills = initial.skills(PageRequest.first(256)).values().stream().map(value -> value.skill()).toList();
		final List<Integer> items = initial.equipment(PageRequest.first(256)).values().stream().map(value -> value.itemId()).toList();
		for (int i = 0; i < CLASS_QUERIES; i++)
		{
			PhantomAssertions.assertTrue(initial.classFact(classIds.get(i % classIds.size())) != null, "Class index returned null.");
		}
		for (int i = 0; i < SKILL_QUERIES; i++)
		{
			PhantomAssertions.assertTrue(initial.skill(skills.get(i % skills.size())) != null, "Skill index returned null.");
		}
		for (int i = 0; i < EQUIPMENT_QUERIES; i++)
		{
			PhantomAssertions.assertTrue(initial.equipment(items.get(i % items.size())) != null, "Equipment index returned null.");
		}
		for (int i = 0; i < SUMMON_PET_QUERIES; i++)
		{
			if ((i & 1) == 0)
			{
				initial.summons(classIds.get(i % classIds.size()));
			}
			else
			{
				final var page = initial.pets(PageRequest.first(64));
				_maximumPageObserved = Math.max(_maximumPageObserved, page.values().size());
			}
		}
		final PhantomProgressionSyntheticBackend runtimeBackend = new PhantomProgressionSyntheticBackend();
		final PhantomProgressionCatalog runtimeCatalog = builder.build(PhantomProgressionSyntheticBackend.data(), policy);
		final PhantomProgressionCapabilityEvaluator evaluator = new PhantomProgressionCapabilityEvaluator();
		try (var lease = runtimeBackend.lease())
		{
			final var actor = lease.snapshot(runtimeCatalog.combinedHash(), runtimeCatalog.referencedResourceItemIds(), runtimeCatalog.certificationSkillIds());
			for (int i = 0; i < CAPABILITY_EVALUATIONS; i++)
			{
				evaluator.evaluate(runtimeCatalog, actor, lease, 99);
			}
		}
		runtimeBackend.equipResult(new OperationResult(OperationStatus.IDEMPOTENT, 0, 0, Map.of(), Map.of(), 0, true));
		final PhantomProgressionService operations = new PhantomProgressionService(runtimeBackend, policy);
		operations.start();
		for (int i = 0; i < OPERATIONS; i++)
		{
			PhantomAssertions.assertEquals(OperationStatus.IDEMPOTENT, operations.equipOwnedItem(new EquipItemRequest(1, 1000, () -> false)).status(), "Performance operation result changed.");
		}
		for (int i = 0; i < EQUIPMENT_QUERIES; i++)
		{
			final OwnedEquipmentFilter filter = (i & 1) == 0 ? OwnedEquipmentFilter.all() : new OwnedEquipmentFilter(null, "SWORD", true);
			final var page = operations.equipmentCandidates(1, filter, PageRequest.first(64));
			_maximumCandidatesObserved = Math.max(_maximumCandidatesObserved, page.values().size());
		}
		_operationsAfter = operations.snapshot().currentOperations();
		_leasesAfter = operations.snapshot().currentActorLeases();
		operations.beginStop();
		PhantomAssertions.assertTrue(operations.finishStop(), "Performance operation service did not stop.");
		_elapsedMillis = (System.nanoTime() - started) / 1_000_000;
		context.record("progressionPerformance.catalogBuilds", CATALOG_BUILDS);
		context.record("progressionPerformance.classQueries", CLASS_QUERIES);
		context.record("progressionPerformance.skillQueries", SKILL_QUERIES);
		context.record("progressionPerformance.capabilityEvaluations", CAPABILITY_EVALUATIONS);
		context.record("progressionPerformance.equipmentQueries", EQUIPMENT_QUERIES);
		context.record("progressionPerformance.summonPetQueries", SUMMON_PET_QUERIES);
		context.record("progressionPerformance.operations", OPERATIONS);
		context.record("progressionPerformance.workers", 0);
		context.record("progressionPerformance.tasks", 0);
		context.record("progressionPerformance.futures", 0);
		context.record("progressionPerformance.operationsAfter", _operationsAfter);
		context.record("progressionPerformance.actorLeasesAfter", _leasesAfter);
		context.record("progressionPerformance.maximumPage", _maximumPageObserved);
		context.record("progressionPerformance.maximumCandidates", _maximumCandidatesObserved);
		context.record("progressionPerformance.elapsedMillis", _elapsedMillis);
	}

	@Override
	public void afterAll(PhantomTestContext context) throws Exception
	{
		_fixture.close();
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("01-fixed-load-query-operation-matrix", _ ->
		{
			PhantomAssertions.assertEquals(CATALOG_BUILDS, _hashes.size(), "Catalog build count changed.");
			PhantomAssertions.assertTrue(_hashes.stream().distinct().count() == 1, "Repeated real-corpus catalog hashes differ.");
			PhantomAssertions.assertEquals(0, _operationsAfter, "Operation slot remained after performance run.");
			PhantomAssertions.assertEquals(0, _leasesAfter, "Actor lease remained after performance run.");
		});
		registry.add("02-structural-bounds-and-timeout", _ ->
		{
			PhantomAssertions.assertTrue(_maximumPageObserved <= 256, "Query page exceeded 256.");
			PhantomAssertions.assertTrue(_maximumCandidatesObserved <= 64, "Equipment candidates exceeded 64.");
			PhantomAssertions.assertTrue(_elapsedMillis <= 120_000, "Focused progression performance smoke exceeded 120 seconds.");
		});
	}
}
