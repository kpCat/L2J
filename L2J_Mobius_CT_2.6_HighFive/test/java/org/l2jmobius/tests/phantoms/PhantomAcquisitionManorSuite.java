/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.tests.phantoms;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.l2jmobius.gameserver.config.GeneralConfig;
import org.l2jmobius.gameserver.config.RatesConfig;
import org.l2jmobius.gameserver.data.xml.MapRegionData;
import org.l2jmobius.gameserver.managers.CastleManorManager;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionCatalog.Method;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionCatalog.Limits;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.Candidate;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.Hashes;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.ManorBinding;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.Phase;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.Source;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.Status;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionStateCodec;
import org.l2jmobius.gameserver.phantoms.acquisition.manor.PhantomAcquisitionManorAuthority;
import org.l2jmobius.gameserver.phantoms.acquisition.manor.PhantomAcquisitionManorAuthority.Projection;
import org.l2jmobius.gameserver.phantoms.acquisition.quest.PhantomAcquisitionQuestCatalog;
import org.l2jmobius.gameserver.phantoms.acquisition.quest.PhantomAcquisitionQuestCatalog.Rule;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundModel;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundModel.BatchMode;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundModel.BatchRequest;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundModel.BatchResult;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundModel.DeathPolicy;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundModel.Drop;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundModel.ExperienceTable;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundModel.LevelForExperience;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundModel.ManorFormula;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundModel.RewardPolicy;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundModel.QuestFormula;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundModel.Target;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.Clock;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.CombatFacts;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.Identity;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.InventoryFacts;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.Loadout;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.ModelKind;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.Position;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.Progress;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.Receipt;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.State;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.Vitals;
import org.l2jmobius.gameserver.scripting.ScriptEngine;

public final class PhantomAcquisitionManorSuite implements PhantomTestSuite
{
	public enum Mode
	{
		CATALOG_SOURCE,
		BACKGROUND,
		RESTART_TRANSITION,
		LIFECYCLE_PERFORMANCE
	}

	private static final long SEED = 21002102L;
	private static final Hashes ACQUISITION_HASHES = new Hashes("a".repeat(64), "b".repeat(64), "c".repeat(64), "d".repeat(64), "e".repeat(64));
	private static final PhantomBackgroundState.Hashes BACKGROUND_HASHES = new PhantomBackgroundState.Hashes("1".repeat(64), "2".repeat(64), "3".repeat(64), "4".repeat(64));
	private final Mode _mode;
	private PhantomHeadlessPlayerTestEnvironment _environment;
	private PhantomBackgroundSuite.ProductionAuthorityFixture _production;
	private PhantomAcquisitionManorAuthority _authority;
	private PhantomAcquisitionQuestCatalog _quests;

	public PhantomAcquisitionManorSuite(Mode mode)
	{
		_mode = mode;
	}

	@Override
	public String id()
	{
		return _mode == Mode.LIFECYCLE_PERFORMANCE ? "acquisition-checkpoint2-lifecycle-performance" : "acquisition-manor-" + _mode.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
	}

	@Override
	public void beforeAll(PhantomTestContext context) throws Exception
	{
		PhantomAssertions.assertEquals(SEED, context.seed(), "Goal 021 Checkpoint 2 manor mode used the wrong seed.");
		if (_mode == Mode.CATALOG_SOURCE)
		{
			_environment = new PhantomHeadlessPlayerTestEnvironment();
			_environment.initialize(context);
			ScriptEngine.getInstance().executeScript(ScriptEngine.MASTER_HANDLER_FILE);
			MapRegionData.getInstance();
			_production = PhantomBackgroundSuite.ProductionAuthorityFixture.start();
			_authority = new PhantomAcquisitionManorAuthority(_production.knowledge(), _production.topology(), Path.of("data/mapregion"));
		}
		else if (_mode == Mode.LIFECYCLE_PERFORMANCE)
		{
			_quests = PhantomAcquisitionQuestCatalog.load(context.moduleRoot().resolve("dist/game/data/phantoms/acquisition/high-five-quest-collection-v1.xml"), context.moduleRoot().resolve("dist/game/data/scripts"));
		}
	}

	@Override
	public void afterAll(PhantomTestContext context) throws Exception
	{
		try
		{
			if (_production != null)
			{
				_production.close();
			}
		}
		finally
		{
			if (_environment != null)
			{
				_environment.shutdown();
			}
		}
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		switch (_mode)
		{
			case CATALOG_SOURCE ->
			{
				registry.add("01-current-seed-handler-crop-and-castle-authority", this::testCurrentSources);
				registry.add("02-missing-items-disabled-manor-and-authority-drift", this::testSourceControls);
				registry.add("03-exact-shipped-formulas", this::testFormulaContract);
			}
			case BACKGROUND ->
			{
				registry.add("01-seed-crop-and-ordinary-drop-conservation", this::testBackgroundConservation);
				registry.add("02-failed-sow-and-harvest-bounds", this::testBackgroundFailures);
				registry.add("03-catalog-driven-non-default-attempt-parity", this::testAttemptPolicyVariant);
			}
			case RESTART_TRANSITION ->
			{
				registry.add("01-schema3-binding-roundtrip-and-4096-bound", this::testRestartCodec);
				registry.add("02-dispatch-transition-invariants", this::testTransitions);
			}
			case LIFECYCLE_PERFORMANCE ->
			{
				registry.add("01-100k-manor-formula-plans", this::testPlanningPerformance);
				registry.add("02-100k-quest-rule-plans", this::testQuestPlanningPerformance);
				registry.add("03-10k-manor-background-operations", this::testBackgroundPerformance);
				registry.add("04-10k-quest-background-operations", this::testQuestBackgroundPerformance);
				registry.add("05-no-worker-or-runtime-script-scan-ownership", this::testLifecycleStructure);
			}
		}
	}

	private void testCurrentSources(PhantomTestContext context)
	{
		PhantomAssertions.assertTrue(_authority.current(), "Current manor authority rejected canonical handlers or data.");
		final var selected = currentCandidate();
		final var fact = selected.fact();
		final var runtime = CastleManorManager.getInstance().getSeed(fact.seedItemId());
		PhantomAssertions.assertTrue(runtime != null, "Static ManorFact has no runtime Seed.");
		PhantomAssertions.assertEquals(fact.cropItemId(), runtime.getCropId(), "Static/runtime crop identity differs.");
		PhantomAssertions.assertEquals(fact.matureItemId(), runtime.getMatureId(), "Static/runtime mature identity differs.");
		PhantomAssertions.assertTrue((selected.fact().cropItemId() != selected.fact().matureItemId()) && (selected.fact().cropItemId() != selected.fact().reward1ItemId()) && (selected.fact().cropItemId() != selected.fact().reward2ItemId()), "Manor direct product is not crop-only.");
		PhantomAssertions.assertEquals("Seed", selected.seedHandler().handlerName(), "Canonical Seed handler identity changed.");
		PhantomAssertions.assertEquals("Harvester", _authority.harvesterHandler().handlerName(), "Canonical Harvester handler identity changed.");
		context.record("manor.sourceId", selected.sourceId());
		context.record("manor.authorityHash", _authority.authorityHash());
	}

	private void testSourceControls(PhantomTestContext context)
	{
		final int cropItemId = currentCandidate().fact().cropItemId();
		PhantomAssertions.assertEquals("manor.harvester_missing", _authority.candidates(cropItemId, 85, Map.of()).reasonKey(), "Missing Harvester did not fail closed.");
		final Map<Integer, Long> harvesterOnly = Map.of(PhantomAcquisitionManorAuthority.HARVESTER_ITEM_ID, 1L);
		PhantomAssertions.assertEquals("manor.seed_missing", _authority.candidates(cropItemId, 85, harvesterOnly).reasonKey(), "Missing Seed did not fail closed.");
		final boolean allowBaseline = GeneralConfig.ALLOW_MANOR;
		final int rateBaseline = RatesConfig.RATE_DROP_MANOR;
		try
		{
			GeneralConfig.ALLOW_MANOR = false;
			PhantomAssertions.assertTrue(_authority.candidates(cropItemId, 85, Map.of(PhantomAcquisitionManorAuthority.HARVESTER_ITEM_ID, 1L)).candidates().isEmpty(), "Disabled manor produced a source.");
			GeneralConfig.ALLOW_MANOR = allowBaseline;
			RatesConfig.RATE_DROP_MANOR = rateBaseline + 1;
			PhantomAssertions.assertFalse(_authority.current(), "Manor rate drift did not stale the authority.");
		}
		finally
		{
			GeneralConfig.ALLOW_MANOR = allowBaseline;
			RatesConfig.RATE_DROP_MANOR = rateBaseline;
		}
		PhantomAssertions.assertTrue(_authority.current(), "Restored manor authority did not become current.");
		context.record("manor.sourceCandidateBound", 8);
	}

	private void testFormulaContract(PhantomTestContext context)
	{
		PhantomAssertions.assertEquals(90, PhantomAcquisitionManorAuthority.sowChance(20, false, 20, 20), "Normal sow base differs from Sow.");
		PhantomAssertions.assertEquals(20, PhantomAcquisitionManorAuthority.sowChance(20, true, 20, 20), "Alternative sow base differs from Sow.");
		PhantomAssertions.assertEquals(65, PhantomAcquisitionManorAuthority.sowChance(20, false, 25, 35), "Sow level penalty differs from Sow.");
		PhantomAssertions.assertEquals(75, PhantomAcquisitionManorAuthority.harvestChance(20, 30), "Harvest level penalty differs from Harvesting.");
		PhantomAssertions.assertEquals(1, PhantomAcquisitionManorAuthority.harvestChance(1, 85), "Harvest minimum differs from Harvesting.");
		PhantomAssertions.assertEquals(24, PhantomAcquisitionManorAuthority.harvestPayload(30, 20, 7, 2), "Strong multiplier/manor-rate payload differs from Harvesting.");
		context.record("manor.formulaBranches", 6);
	}

	private void testBackgroundConservation(PhantomTestContext context)
	{
		final PhantomBackgroundModel model = new PhantomBackgroundModel();
		final ManorFormula formula = new ManorFormula(5016, 5072, 8, 100, 100, 3, 3, 3);
		final BatchResult result = model.evaluate(request(state(SEED), BatchMode.ACQUISITION_MANOR_CROP, 5072, formula));
		PhantomAssertions.assertEquals(1, result.encounters(), "Successful manor batch did not execute exactly one combat encounter.");
		PhantomAssertions.assertEquals(-1L, result.inventoryDelta().itemDeltas().get(5016), "Successful manor batch did not consume exactly one seed.");
		PhantomAssertions.assertEquals(3L, result.inventoryDelta().itemDeltas().get(5072), "Successful manor batch did not credit exact crop payload.");
		PhantomAssertions.assertEquals(1L, result.inventoryDelta().itemDeltas().get(57), "Ordinary death drop was not kept separate.");
		PhantomAssertions.assertEquals(result.acquisitionTargetDelta(), result.inventoryDelta().itemDeltas().get(5072), "Crop delta and acquisition evidence differ.");
		context.record("manor.backgroundSeedDelta", result.inventoryDelta().itemDeltas().get(5016));
	}

	private void testBackgroundFailures(PhantomTestContext context)
	{
		final PhantomBackgroundModel model = new PhantomBackgroundModel();
		BatchResult failedSow = null;
		for (long rng = 1; (rng < 10000) && (failedSow == null); rng++)
		{
			final BatchResult result = model.evaluate(request(state(rng), BatchMode.ACQUISITION_MANOR_CROP, 5072, new ManorFormula(5016, 5072, 8, 1, 100, 3, 3, 3)));
			if (result.encounters() == 0)
			{
				failedSow = result;
			}
		}
		PhantomAssertions.assertTrue((failedSow != null) && (failedSow.manorSowAttempts() == 3) && (failedSow.inventoryDelta().itemDeltas().get(5016) == -3L) && (failedSow.acquisitionTargetDelta() == 0), "Failed sow attempts did not conserve exact seeds/no crop.");
		BatchResult failedHarvest = null;
		for (long rng = 1; (rng < 10000) && (failedHarvest == null); rng++)
		{
			final BatchResult result = model.evaluate(request(state(rng), BatchMode.ACQUISITION_MANOR_CROP, 5072, new ManorFormula(5016, 5072, 8, 100, 1, 3, 3, 3)));
			if ((result.encounters() == 1) && (result.acquisitionTargetDelta() == 0))
			{
				failedHarvest = result;
			}
		}
		PhantomAssertions.assertTrue((failedHarvest != null) && (failedHarvest.manorHarvestAttempts() == 3) && (failedHarvest.inventoryDelta().itemDeltas().get(5016) == -1L), "Failed harvest retries did not retain bounded seed/combat truth.");
		context.record("manor.failedSowAttempts", failedSow.manorSowAttempts());
	}

	private void testAttemptPolicyVariant(PhantomTestContext context) throws Exception
	{
		final Limits limits = new Limits(8, 4, 4, 6, 48, 32, 8, 8, 4, 8, 4096, 2000, 3, 2, 2, 6000, 8, 4, 8, 4, 16, 1);
		final PhantomBackgroundModel model = new PhantomBackgroundModel();
		BatchResult failedSow = null;
		BatchResult failedHarvest = null;
		for (long rng = 1; (rng < 10000) && ((failedSow == null) || (failedHarvest == null)); rng++)
		{
			if (failedSow == null)
			{
				final BatchResult result = model.evaluate(request(state(rng), BatchMode.ACQUISITION_MANOR_CROP, 5072, new ManorFormula(5016, 5072, 8, 1, 100, 3, limits.manorAttemptsPerTarget(), limits.harvestAttemptsPerCorpse())));
				if (result.encounters() == 0)
				{
					failedSow = result;
				}
			}
			if (failedHarvest == null)
			{
				final BatchResult result = model.evaluate(request(state(rng), BatchMode.ACQUISITION_MANOR_CROP, 5072, new ManorFormula(5016, 5072, 8, 100, 1, 3, limits.manorAttemptsPerTarget(), limits.harvestAttemptsPerCorpse())));
				if ((result.encounters() == 1) && (result.acquisitionTargetDelta() == 0))
				{
					failedHarvest = result;
				}
			}
		}
		PhantomAssertions.assertTrue((failedSow != null) && (failedSow.manorSowAttempts() == 2), "Background manor ignored non-default catalog sow attempts.");
		PhantomAssertions.assertTrue((failedHarvest != null) && (failedHarvest.manorHarvestAttempts() == 2), "Background manor ignored non-default catalog harvest attempts.");
		final String service = Files.readString(context.moduleRoot().resolve("java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundService.java"));
		PhantomAssertions.assertTrue(service.contains("_acquisitionLimits.manorAttemptsPerTarget()") && service.contains("_acquisitionLimits.harvestAttemptsPerCorpse()") && !service.contains("projection.harvestPayload(), 3, 3"), "Background manor formula is not catalog-driven.");
		context.record("manor.variantSowAttempts", limits.manorAttemptsPerTarget());
		context.record("manor.variantHarvestAttempts", limits.harvestAttemptsPerCorpse());
	}

	private void testRestartCodec(PhantomTestContext context)
	{
		final PhantomAcquisitionStateCodec codec = new PhantomAcquisitionStateCodec();
		final PhantomAcquisitionState state = manorState(Phase.SOW_DISPATCHING, 2);
		final byte[] payload = codec.encode(state);
		final PhantomAcquisitionState decoded = codec.decode(payload);
		PhantomAssertions.assertEquals(state, decoded, "Schema-3 manor binding did not round-trip.");
		PhantomAssertions.assertTrue(payload.length <= 4096 && codec.declaredWorstCaseBytes() <= 4096, "Manor state exceeded acquisition.state bound.");
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> new ManorBinding(1, 5016, 5072, 5073, 57, 4037, 20, false, 100, 100, 1, 0, 8, 0, "f".repeat(64)), "Partial exact manor object binding was admitted.");
		context.record("manor.schema3Bytes", payload.length);
	}

	private void testTransitions(PhantomTestContext context) throws Exception
	{
		for (Phase phase : List.of(Phase.SOW_PREPARED, Phase.SOW_DISPATCHING, Phase.SOW_OBSERVED, Phase.COMBAT_PREPARED, Phase.COMBAT_SUBMITTED, Phase.COMBAT_TERMINAL, Phase.HARVEST_PREPARED, Phase.HARVEST_DISPATCHING, Phase.VERIFYING))
		{
			final PhantomAcquisitionState state = manorState(phase, phase == Phase.SOW_DISPATCHING || phase == Phase.HARVEST_DISPATCHING || phase == Phase.COMBAT_SUBMITTED ? 1 : 0);
			PhantomAssertions.assertEquals(Method.MANOR_CROP, state.methodBinding().method(), "Manor transition lost schema-3 method ownership.");
		}
		final PhantomAcquisitionState terminal = manorState(Phase.COMBAT_TERMINAL, 0);
		final ManorBinding previous = (ManorBinding) terminal.methodBinding();
		final ManorBinding refreshed = new ManorBinding(previous.castleId(), previous.seedItemId(), previous.cropItemId(), previous.matureItemId(), previous.reward1ItemId(), previous.reward2ItemId(), previous.seedLevel(), previous.alternative(), previous.rawSeedLimit(), previous.rawCropLimit(), previous.seedObjectId(), previous.harvesterObjectId(), previous.seedCountBeforeDispatch(), 9, previous.authorityHash());
		final PhantomAcquisitionState prepared = terminal.withBinding(refreshed, Phase.HARVEST_PREPARED, terminal.targetObjectId(), terminal.targetNpcId(), terminal.targetInstanceId(), 0, 2);
		PhantomAssertions.assertEquals(9L, ((ManorBinding) new PhantomAcquisitionStateCodec().decode(new PhantomAcquisitionStateCodec().encode(prepared)).methodBinding()).cropCountBeforeDispatch(), "Refreshed pre-harvest baseline was not durable.");
		final String service = Files.readString(context.moduleRoot().resolve("java/org/l2jmobius/gameserver/phantoms/acquisition/PhantomAcquisitionService.java"));
		final int refresh = service.indexOf("final ManorBinding refreshed");
		final int store = service.indexOf("withBinding(refreshed, Phase.HARVEST_PREPARED", refresh);
		final int release = service.indexOf("releaseExternal(current.profileId())", store);
		PhantomAssertions.assertTrue((refresh >= 0) && (store > refresh) && (release > store) && service.contains("inventory.cropCount() == manor.cropCountBeforeDispatch()"), "Pre-harvest baseline/store/release or pre-dispatch crop guard is missing.");
		context.record("manor.persistedTransitions", 9);
	}

	private void testPlanningPerformance(PhantomTestContext context)
	{
		long checksum = 0;
		final long started = System.nanoTime();
		for (int index = 0; index < 100000; index++)
		{
			checksum += PhantomAcquisitionManorAuthority.sowChance(20, (index & 1) == 0, 15 + (index % 20), 20 + (index % 30));
			checksum += PhantomAcquisitionManorAuthority.harvestChance(20 + (index % 30), 15 + (index % 20));
		}
		PhantomAssertions.assertTrue(checksum != 0, "100k manor plan formula checksum is empty.");
		context.record("manor.100kPlanMillis", (System.nanoTime() - started) / 1_000_000L);
	}

	private void testBackgroundPerformance(PhantomTestContext context)
	{
		final PhantomBackgroundModel model = new PhantomBackgroundModel();
		long checksum = 0;
		final long started = System.nanoTime();
		for (int index = 0; index < 10000; index++)
		{
			checksum += model.evaluate(request(state(SEED + index), BatchMode.ACQUISITION_MANOR_CROP, 5072, new ManorFormula(5016, 5072, 8, 90, 100, 3, 3, 3))).nextRngState();
		}
		PhantomAssertions.assertTrue(checksum != 0, "10k manor background checksum is empty.");
		context.record("manor.10kBackgroundMillis", (System.nanoTime() - started) / 1_000_000L);
	}

	private void testQuestPlanningPerformance(PhantomTestContext context)
	{
		long checksum = 0;
		final long started = System.nanoTime();
		for (int index = 0; index < 100000; index++)
		{
			for (Rule rule : _quests.rules())
			{
				checksum += rule.supports(rule.allowedConds().getFirst(), index % rule.itemCap(), rule.targetNpcIds().getFirst(), false) ? 1 : 0;
			}
		}
		PhantomAssertions.assertTrue(checksum > 0, "100k quest plan checksum is empty.");
		context.record("quest.100kPlanMillis", (System.nanoTime() - started) / 1_000_000L);
	}

	private void testQuestBackgroundPerformance(PhantomTestContext context)
	{
		final PhantomBackgroundModel model = new PhantomBackgroundModel();
		final Rule rule = _quests.rules().getFirst();
		long checksum = 0;
		final long started = System.nanoTime();
		for (int index = 0; index < 10000; index++)
		{
			final QuestFormula formula = new QuestFormula(rule.rollBound(), rule.rollThreshold(), rule.maximumCount(), 0, rule.itemCap());
			checksum += model.evaluate(questRequest(state(SEED + index), rule, formula)).nextRngState();
		}
		PhantomAssertions.assertTrue(checksum != 0, "10k quest background checksum is empty.");
		context.record("quest.10kBackgroundMillis", (System.nanoTime() - started) / 1_000_000L);
	}

	private void testLifecycleStructure(PhantomTestContext context) throws Exception
	{
		final String manor = java.nio.file.Files.readString(context.moduleRoot().resolve("java/org/l2jmobius/gameserver/phantoms/acquisition/manor/PhantomAcquisitionManorAuthority.java"));
		final String quests = java.nio.file.Files.readString(context.moduleRoot().resolve("java/org/l2jmobius/gameserver/phantoms/acquisition/quest/PhantomAcquisitionQuestCatalog.java"));
		for (String forbidden : List.of("new Thread", "new ScheduledThreadPool", "CompletableFuture", "ScheduledFuture"))
		{
			PhantomAssertions.assertFalse(manor.contains(forbidden) || quests.contains(forbidden), "Checkpoint 2 authority introduced worker ownership: " + forbidden);
		}
		PhantomAssertions.assertFalse(quests.contains("Files.walk") || quests.contains("Files.list"), "Quest runtime authority scans the script tree.");
		context.record("checkpoint2.newWorkers", 0);
	}

	private org.l2jmobius.gameserver.phantoms.acquisition.manor.PhantomAcquisitionManorAuthority.Candidate currentCandidate()
	{
		final var snapshot = _production.knowledge().snapshot();
		final List<Integer> mappedTargetLevels = snapshot.spawnFacts().stream().filter(fact -> fact.topologyNodeId() != null).map(fact -> snapshot.npcById().get(fact.npcId())).filter(Objects::nonNull).filter(fact -> fact.canBeSown() && fact.attackable() && fact.targetable()).map(fact -> fact.level()).distinct().sorted().toList();
		for (int playerLevel : mappedTargetLevels)
		{
			for (int cropItemId : snapshot.manorFacts().stream().map(fact -> fact.cropItemId()).distinct().sorted().toList())
			{
				final Map<Integer, Long> inventory = _authority.probe(cropItemId).requiredItemIds().stream().collect(java.util.stream.Collectors.toMap(itemId -> itemId, _ -> 64L));
				final var result = _authority.candidates(cropItemId, playerLevel, inventory);
				if (!result.candidates().isEmpty())
				{
					return result.candidates().getFirst();
				}
			}
		}
		throw new AssertionError("No bounded current manor candidate is available.");
	}

	private static PhantomAcquisitionState manorState(Phase phase, int attempt)
	{
		final String sourceId = "9".repeat(64);
		final Source source = new Source(sourceId, Method.MANOR_CROP, 20013, 5072, "manor:test", "node", "anchor", 0, 0, 0, 0, 0);
		final Candidate candidate = new Candidate(sourceId, Method.MANOR_CROP, 100, 0, 0, "");
		final ManorBinding binding = new ManorBinding(1, 5016, 5072, 5073, 57, 4037, 20, false, 100, 100, 7001, 7002, 8, 0, "f".repeat(64));
		return new PhantomAcquisitionState(ACQUISITION_HASHES, 1, 0, 5072, 10, 0, 0, 0, Status.ACTIVE, source, List.of(candidate), 0, 0, phase, 8001, 20013, 0, null, binding, List.of(), attempt, 1);
	}

	private static PhantomBackgroundState state(long rng)
	{
		return new PhantomBackgroundState(State.READY, new Identity(1, 1, 0, 88, 0), new Progress(20, 1900, 0, 0), new Vitals(100, 100, 100, 100, 10, 10), new Position(0, 1, 2, 3, 0, "anchor"), new CombatFacts(ModelKind.MELEE, 1000, 1000, 1000, 1000, 1000, 1000, 0, 0, 1, 1, 0, 1, 1, 1, 1), Loadout.none(), new InventoryFacts(List.of(57, 5016, 5072), List.of(), "manor", 0, 1_000_000, 0, 100), List.of(), new Clock(rng, 0, 0), Receipt.empty(), BACKGROUND_HASHES);
	}

	private static BatchRequest request(PhantomBackgroundState state, BatchMode mode, int targetItemId, ManorFormula formula)
	{
		final Drop ordinary = new Drop(57, -1, 0, 1_000_000, 1_000_000, 1, 1, 1, null, 1, 100, true, 0);
		final Target target = new Target(20013, 20, true, 1, 1, 1, 1, 1, 1, 1000, 1000, 0, 0, List.of(ordinary), 1);
		return new BatchRequest(state, target, new RewardPolicy(11, 1, 1), deathPolicy(), experienceTable(), levelForExperience(), false, mode, targetItemId, 1, true, formula, null, 1, true, 0);
	}

	private static BatchRequest questRequest(PhantomBackgroundState state, Rule rule, QuestFormula formula)
	{
		final Drop ordinary = new Drop(57, -1, 0, 1_000_000, 1_000_000, 1, 1, 1, null, 1, 100, true, 0);
		final Target target = new Target(rule.targetNpcIds().getFirst(), 20, true, 1, 1, 1, 1, 1, 1, 1000, 1000, 0, 0, List.of(ordinary), 1);
		return new BatchRequest(state, target, new RewardPolicy(11, 1, 1), deathPolicy(), experienceTable(), levelForExperience(), false, BatchMode.ACQUISITION_QUEST_COLLECTION, rule.questItemId(), 1, true, null, formula, 1, true, 0);
	}

	private static DeathPolicy deathPolicy()
	{
		return new DeathPolicy()
		{
			@Override
			public double lossPercent(int level)
			{
				return 0;
			}

			@Override
			public double normalMonsterReductionMultiplier()
			{
				return 1;
			}
		};
	}

	private static ExperienceTable experienceTable()
	{
		return new ExperienceTable()
		{
			@Override
			public long experienceForLevel(int level)
			{
				return Math.max(0, level - 1) * 100L;
			}

			@Override
			public int maximumLevel()
			{
				return 85;
			}
		};
	}

	private static LevelForExperience levelForExperience()
	{
		return experience -> (int) Math.clamp((experience / 100) + 1, 1, 85);
	}
}
