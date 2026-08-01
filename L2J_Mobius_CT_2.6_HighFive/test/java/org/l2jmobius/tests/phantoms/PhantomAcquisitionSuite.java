/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.tests.phantoms;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionCatalog;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionCatalog.Method;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionCatalog.MethodStatus;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionDecision;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionGoalSpec;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionRecipePlanner;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionRecipePlanner.CraftEvidence;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionSourcePlanner;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionSourcePlanner.RankedSource;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionService;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionStore;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.Candidate;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.Deficit;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.Hashes;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.Phase;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.Receipt;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.ReceiptKind;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.RecipeNode;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.RecipePlan;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.Source;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.Status;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.TerminalResult;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionStateCodec;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundCompetitionRegistry;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundService;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundTransaction;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatCapabilityResolver;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatPolicy;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatService;
import org.l2jmobius.gameserver.phantoms.decision.PhantomCandidateRegistry;
import org.l2jmobius.gameserver.phantoms.decision.PhantomCapabilitySet;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoal;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateStore;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus;
import org.l2jmobius.gameserver.phantoms.decision.PhantomPlanningContext;
import org.l2jmobius.gameserver.phantoms.decision.PhantomStepContext;
import org.l2jmobius.gameserver.phantoms.decision.PhantomStepHandlerRegistry;
import org.l2jmobius.gameserver.phantoms.decision.PhantomStepResult;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomCuratedKnowledgeParser;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeBackend;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeBackend.BackendData;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeBuilder;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeAuthority;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.DropFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.DropSourceKind;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.ChanceModel;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.IngredientFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.ItemCategory;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.ItemFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.NpcFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.RecipeFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgePolicy;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeQuery;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeService;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomStaticManorParser;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationService;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfile;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.gameserver.phantoms.PhantomMetrics;
import org.l2jmobius.gameserver.phantoms.topology.PhantomRelevanceSignalPort;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyAnchor;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyAnchorRole;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyArea;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyMetrics;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyNode;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyNodeKind;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPolicy;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyQuery;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologySnapshot;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyValidationBackend;

public final class PhantomAcquisitionSuite implements PhantomTestSuite
{
	public enum Mode
	{
		CATALOG_CODEC,
		SOURCE_PLANNER,
		RECIPE_PLANNING,
		SOURCE_SWITCHING,
		LIFECYCLE_PERFORMANCE
	}

	private static final long SEED = 21002101L;
	private static final Hashes HASHES = new Hashes("a".repeat(64), "b".repeat(64), "c".repeat(64), "d".repeat(64), "e".repeat(64));
	private final Mode _mode;
	private PhantomHeadlessPlayerTestEnvironment _environment;
	private PhantomBackgroundSuite.ProductionAuthorityFixture _production;
	private final List<Path> _temporaryRoots = new ArrayList<>();

	public PhantomAcquisitionSuite(Mode mode)
	{
		_mode = mode;
	}

	@Override
	public String id()
	{
		return "acquisition-" + _mode.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
	}

	@Override
	public void beforeAll(PhantomTestContext context) throws Exception
	{
		PhantomAssertions.assertEquals(SEED, context.seed(), "Goal 021 Checkpoint 1 mode used the wrong seed.");
		if (_mode != Mode.CATALOG_CODEC)
		{
			_environment = new PhantomHeadlessPlayerTestEnvironment();
			_environment.initialize(context);
			_production = PhantomBackgroundSuite.ProductionAuthorityFixture.start();
			context.record("acquisition.knowledgeHash", _production.knowledge().snapshot().combinedHash());
			context.record("acquisition.topologyHash", _production.topology().snapshot().canonicalHash());
			context.record("acquisition.progressionHash", _production.progression().combinedHash());
		}
	}

	@Override
	public void afterAll(PhantomTestContext context) throws Exception
	{
		Throwable failure = null;
		try
		{
			if (_production != null)
			{
				_production.close();
			}
		}
		catch (Throwable throwable)
		{
			failure = throwable;
		}
		for (Path root : _temporaryRoots)
		{
			try
			{
				deleteTree(root);
			}
			catch (Throwable throwable)
			{
				if (failure == null)
				{
					failure = throwable;
				}
			}
		}
		if (_environment != null)
		{
			_environment.shutdown();
		}
		if (failure instanceof Exception exception)
		{
			throw exception;
		}
		if (failure != null)
		{
			throw new AssertionError(failure);
		}
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		switch (_mode)
		{
			case CATALOG_CODEC -> registerCatalogCodec(registry);
			case SOURCE_PLANNER -> registerSourcePlanner(registry);
			case RECIPE_PLANNING -> registerRecipePlanning(registry);
			case SOURCE_SWITCHING -> registerSourceSwitching(registry);
			case LIFECYCLE_PERFORMANCE -> registerLifecyclePerformance(registry);
		}
	}

	private void registerCatalogCodec(PhantomTestRegistry registry)
	{
		registry.add("01-strict-xml-hash-order-and-checkpoint-boundaries", this::testStrictCatalog);
		registry.add("02-all-method-status-phase-roundtrips", this::testCodecRoundTrips);
		registry.add("03-worst-case-payload-and-fail-closed-codec", this::testCodecBounds);
	}

	private void registerSourcePlanner(PhantomTestRegistry registry)
	{
		registry.add("01-production-death-drop-source-is-authoritative", context -> testProductionSource(context, Method.DEATH_DROP));
		registry.add("02-production-spoil-source-and-capability-are-exact", context -> testProductionSource(context, Method.SPOIL_SWEEP));
		registry.add("03-deterministic-ranking-bounds-and-no-corpus-copy", this::testPlannerDeterminism);
	}

	private void registerRecipePlanning(PhantomTestRegistry registry)
	{
		registry.add("01-production-direct-and-multilevel-recipes", this::testProductionRecipes);
		registry.add("02-inventory-ceiling-shared-dag-and-deferred-leaves", this::testSharedRecipeDag);
		registry.add("03-cycle-depth-node-deficit-and-prerequisite-controls", this::testRecipeNegativeControls);
	}

	private void registerSourceSwitching(PhantomTestRegistry registry)
	{
		registry.add("01-threshold-cooldown-and-deterministic-alternative", this::testThresholdAndCooldown);
		registry.add("02-partial-progress-switch-preserves-baseline", this::testPartialProgressSwitch);
		registry.add("03-authority-drift-and-exhausted-bounds", this::testAuthorityAndExhaustion);
	}

	private void registerLifecyclePerformance(PhantomTestRegistry registry)
	{
		registry.add("01-100k-indexed-source-plans", this::testSourcePerformance);
		registry.add("02-10k-bounded-recipe-dags", this::testRecipePerformance);
		registry.add("03-10k-acquisition-decision-advances", this::testDecisionPerformance);
		registry.add("04-no-workers-and-fixed-lifecycle-bounds", this::testStructuralLifecycle);
	}

	private void testStrictCatalog(PhantomTestContext context) throws Exception
	{
		final Path xml = xml(context);
		final byte[] source = Files.readAllBytes(xml);
		final PhantomAcquisitionCatalog first = PhantomAcquisitionCatalog.load(xml);
		final PhantomAcquisitionCatalog second = PhantomAcquisitionCatalog.load(xml);
		PhantomAssertions.assertEquals(first.hash(), second.hash(), "Acquisition policy hash is not content deterministic.");
		PhantomAssertions.assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source)), first.hash(), "Acquisition policy hash is not the raw UTF-8 SHA-256.");
		PhantomAssertions.assertEquals(List.of(Method.DEATH_DROP, Method.SPOIL_SWEEP, Method.RECIPE_PREPARATION, Method.MANOR_CROP, Method.QUEST_COLLECTION).stream().map(first::method).map(PhantomAcquisitionCatalog.MethodPolicy::status).toList(), List.of(MethodStatus.EXECUTABLE, MethodStatus.EXECUTABLE, MethodStatus.PLANNING_ONLY, MethodStatus.DEFERRED_CHECKPOINT_2, MethodStatus.DEFERRED_CHECKPOINT_2), "Checkpoint method statuses changed.");
		PhantomAssertions.assertEquals(4096, first.limits().payloadBytes(), "acquisition.state hard payload bound changed.");
		PhantomAssertions.assertEquals(8, first.limits().sourceCandidates(), "Source candidate bound changed.");
		PhantomAssertions.assertEquals(48, first.limits().recipeNodes(), "Recipe node bound changed.");
		PhantomAssertions.assertEquals(3, first.switchPolicy().failureThreshold(), "Switch failure threshold changed.");

		final String text = new String(source, StandardCharsets.UTF_8);
		assertInvalidCatalog(context, text.replace("<sourceScoring ", "<sourceScoring unknown=\"1\" "), "unknown-attribute");
		assertInvalidCatalog(context, text.replace("<method key=\"death_drop\"", "<method key=\"spoil_sweep\""), "duplicate-method");
		assertInvalidCatalog(context, swapLines(text, "<sourceScoring ", "<switchPolicy "), "section-order");
		assertInvalidCatalog(context, text.replace("<?xml version=\"1.0\" encoding=\"UTF-8\"?>", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<!DOCTYPE acquisitionPolicy [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]>"), "xxe");
		context.record("acquisition.catalogHash", first.hash());
	}

	private void testCodecRoundTrips(PhantomTestContext context)
	{
		final PhantomAcquisitionStateCodec codec = new PhantomAcquisitionStateCodec();
		for (Method method : Method.values())
		{
			final PhantomAcquisitionState state = methodState(method);
			PhantomAssertions.assertEquals(state, codec.decode(codec.encode(state)), "Method round-trip changed " + method);
		}
		for (Status status : Status.values())
		{
			final PhantomAcquisitionState state = statusState(status);
			PhantomAssertions.assertEquals(state, codec.decode(codec.encode(state)), "Status round-trip changed " + status);
		}
		for (Phase phase : Phase.values())
		{
			final PhantomAcquisitionState state = phaseState(phase);
			PhantomAssertions.assertEquals(state, codec.decode(codec.encode(state)), "Phase round-trip changed " + phase);
		}
		PhantomAssertions.assertEquals(0L, PhantomAcquisitionState.observedProgress(10, 7, 5), "Baseline-derived progress admitted an inventory loss.");
		PhantomAssertions.assertEquals(3L, PhantomAcquisitionState.observedProgress(10, 13, 5), "Baseline-derived progress changed.");
		PhantomAssertions.assertEquals(5L, PhantomAcquisitionState.observedProgress(10, 99, 5), "Baseline-derived progress was not clamped.");
		context.record("acquisition.codecMethods", Method.values().length);
		context.record("acquisition.codecStatuses", Status.values().length);
		context.record("acquisition.codecPhases", Phase.values().length);
	}

	private void testCodecBounds(PhantomTestContext context)
	{
		final PhantomAcquisitionStateCodec codec = new PhantomAcquisitionStateCodec();
		final PhantomAcquisitionState execution = maximumExecutionState();
		final PhantomAcquisitionState recipe = maximumRecipeState();
		final byte[] executionPayload = codec.encode(execution);
		final byte[] recipePayload = codec.encode(recipe);
		PhantomAssertions.assertTrue(executionPayload.length <= codec.declaredWorstCaseBytes() && recipePayload.length <= codec.declaredWorstCaseBytes(), "A maximum acquisition.state exceeded the declared bound.");
		PhantomAssertions.assertTrue(codec.declaredWorstCaseBytes() <= 4096, "Declared acquisition.state bound exceeds the component envelope.");
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> codec.decode(Arrays.copyOf(executionPayload, executionPayload.length - 1)), "Truncated acquisition.state was accepted.");
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> codec.decode(Arrays.copyOf(executionPayload, executionPayload.length + 1)), "Trailing acquisition.state byte was accepted.");
		final byte[] unknown = executionPayload.clone();
		unknown[0] ^= 1;
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> codec.decode(unknown), "Unknown acquisition.state header was accepted.");
		final Candidate duplicate = execution.candidates().getFirst();
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> new PhantomAcquisitionState(HASHES, 1, 0, 57, 10, 0, 0, 0, Status.BLOCKED, duplicateSource(), List.of(duplicate, duplicate), 0, 0, Phase.NONE, 0, 0, 0, null, List.of(), 0), "Duplicate acquisition candidates were accepted.");
		context.record("acquisition.executionWorstBytes", executionPayload.length);
		context.record("acquisition.recipeWorstBytes", recipePayload.length);
		context.record("acquisition.declaredWorstBytes", codec.declaredWorstCaseBytes());
	}

	private void testProductionSource(PhantomTestContext context, Method method)
	{
		final PlannedSource fixture = productionSource(method, false);
		final Source source = fixture.result().selected().source();
		PhantomAssertions.assertEquals(method, source.method(), "Planner selected the wrong acquisition method.");
		PhantomAssertions.assertEquals(0, source.instanceId(), "Executable acquisition source is not normal-world instance 0.");
		PhantomAssertions.assertTrue(_production.topology().findNode(source.topologyNodeId()).isPresent(), "Source topology node is not authoritative.");
		PhantomAssertions.assertTrue(_production.topology().snapshot().anchorById().containsKey(source.anchorId()), "Source anchor is not authoritative.");
		final List<DropFact> facts = method == Method.DEATH_DROP ? _production.knowledge().snapshot().dropSourcesByItem().getOrDefault(fixture.itemId(), List.of()) : _production.knowledge().snapshot().spoilSourcesByItem().getOrDefault(fixture.itemId(), List.of());
		final DropSourceKind kind = method == Method.DEATH_DROP ? DropSourceKind.DEATH_DROP : DropSourceKind.SPOIL;
		PhantomAssertions.assertTrue(facts.stream().anyMatch(fact -> (fact.npcId() == source.npcId()) && (fact.sourceKind() == kind) && fact.stableKey().equals(source.factKey())), "Selected source is absent from authoritative Game Knowledge.");
		if (method == Method.SPOIL_SWEEP)
		{
			PhantomAssertions.assertEquals(348, source.spoilSkillId(), "Production spoil capability identity changed.");
			PhantomAssertions.assertEquals(42, source.sweepSkillId(), "Production sweep capability identity changed.");
		}
		context.record("acquisition." + method.key() + ".itemId", fixture.itemId());
		context.record("acquisition." + method.key() + ".npcId", source.npcId());
		context.record("acquisition." + method.key() + ".sourceId", source.sourceId());
	}

	private void testPlannerDeterminism(PhantomTestContext context)
	{
		for (Method method : List.of(Method.DEATH_DROP, Method.SPOIL_SWEEP))
		{
			final PlannedSource fixture = productionSource(method, true);
			final var repeated = fixture.planner().plan(fixture.request());
			PhantomAssertions.assertEquals(fixture.result(), repeated, "Production source ranking is not deterministic for " + method);
			PhantomAssertions.assertTrue(repeated.ranked().size() <= 8, "Source planner exceeded its indexed page bound.");
		}
		final Set<Class<?>> forbiddenCopies = Set.of(List.class, ArrayList.class, LinkedHashMap.class);
		for (var field : PhantomAcquisitionSourcePlanner.class.getDeclaredFields())
		{
			PhantomAssertions.assertFalse(forbiddenCopies.contains(field.getType()), "Source planner retained a copied corpus field: " + field.getName());
		}
		context.record("acquisition.sourceCandidateBound", 8);
	}

	private void testProductionRecipes(PhantomTestContext context)
	{
		final PhantomAcquisitionRecipePlanner planner = new PhantomAcquisitionRecipePlanner(_production.knowledge(), catalog(context).limits());
		final RecipeFact direct = _production.knowledge().snapshot().recipeByListId().values().stream().sorted(Comparator.comparingInt(RecipeFact::recipeListId)).findFirst().orElseThrow();
		final var directResult = planner.plan(direct.productItemId(), direct.productCount() + 1, Map.of(), new CraftEvidence(172, 10, true));
		PhantomAssertions.assertTrue(directResult.planned(), "Production direct recipe was not planned.");
		PhantomAssertions.assertEquals(2L, directResult.plan().batchCount(), "Recipe output ceiling changed.");
		final RecipeFact multilevel = _production.knowledge().snapshot().recipeByListId().values().stream().filter(recipe -> recipe.ingredients().stream().anyMatch(ingredient -> _production.knowledge().snapshot().recipesByProduct().containsKey(ingredient.itemId()))).sorted(Comparator.comparingInt(RecipeFact::recipeListId)).findFirst().orElseThrow(() -> new AssertionError("Production corpus has no multi-level recipe."));
		final var multilevelResult = planner.plan(multilevel.productItemId(), Math.max(1, multilevel.productCount()), Map.of(), new CraftEvidence(172, 10, true));
		PhantomAssertions.assertTrue(multilevelResult.planned(), "Production multi-level recipe was not planned.");
		PhantomAssertions.assertTrue(multilevelResult.plan().nodes().stream().anyMatch(node -> node.depth() > 1), "Production multi-level recipe did not expand an ingredient DAG.");
		PhantomAssertions.assertTrue(multilevelResult.plan().nodes().size() <= 48 && multilevelResult.plan().deficits().size() <= 32, "Production recipe exceeded checkpoint bounds.");
		context.record("acquisition.directRecipe", direct.recipeListId());
		context.record("acquisition.multilevelRecipe", multilevel.recipeListId());
	}

	private void testSharedRecipeDag(PhantomTestContext context) throws Exception
	{
		try (SyntheticKnowledge fixture = syntheticRecipes(context, RecipeShape.SHARED))
		{
			final PhantomAcquisitionRecipePlanner planner = new PhantomAcquisitionRecipePlanner(fixture.query(), catalog(context).limits());
			final Map<Integer, Long> inventory = new LinkedHashMap<>(Map.of(1, 3L, 2, 1L));
			final Map<Integer, Long> before = Map.copyOf(inventory);
			final var result = planner.plan(7, 2, inventory, new CraftEvidence(172, 10, true));
			PhantomAssertions.assertTrue(result.planned(), "Shared-ingredient synthetic recipe was not planned.");
			PhantomAssertions.assertEquals(2L, result.plan().batchCount(), "Synthetic root ceiling batches changed.");
			PhantomAssertions.assertEquals((long) result.plan().nodes().size(), result.plan().nodes().stream().map(RecipeNode::itemId).distinct().count(), "Shared ingredients were duplicated instead of DAG-aggregated.");
			PhantomAssertions.assertEquals(before, inventory, "Recipe planning mutated caller inventory.");
			final RecipeNode shared = result.plan().nodes().stream().filter(node -> node.itemId() == 1).findFirst().orElseThrow();
			PhantomAssertions.assertEquals(3L, shared.inventoryUsed(), "Shared ingredient inventory was not subtracted exactly once.");
			PhantomAssertions.assertTrue(result.plan().deficits().stream().allMatch(Deficit::questDeferred), "Quest acquisition leaf was not deferred to Checkpoint 2.");
			PhantomAssertions.assertTrue(result.plan().deficits().stream().anyMatch(Deficit::manorDeferred), "Known manor leaf was not typed as deferred.");
			PhantomAssertions.assertEquals("", result.plan().reasonKey(), "Ready craft evidence was rejected.");
			context.record("acquisition.sharedRecipeNodes", result.plan().nodes().size());
		}
	}

	private void testRecipeNegativeControls(PhantomTestContext context) throws Exception
	{
		for (RecipeShape shape : List.of(RecipeShape.CYCLE, RecipeShape.DEPTH, RecipeShape.NODES, RecipeShape.DEFICITS))
		{
			try (SyntheticKnowledge fixture = syntheticRecipes(context, shape))
			{
				final var result = new PhantomAcquisitionRecipePlanner(fixture.query(), catalog(context).limits()).plan(7, 1, Map.of(), new CraftEvidence(172, 10, true));
				PhantomAssertions.assertFalse(result.planned(), "Recipe overflow/cycle was accepted: " + shape);
				PhantomAssertions.assertEquals("recipe.bounds", result.reasonKey(), "Recipe bound failure is not typed: " + shape);
			}
		}
		try (SyntheticKnowledge fixture = syntheticRecipes(context, RecipeShape.SHARED))
		{
			final PhantomAcquisitionRecipePlanner planner = new PhantomAcquisitionRecipePlanner(fixture.query(), catalog(context).limits());
			PhantomAssertions.assertEquals("recipe.missing", planner.plan(1, 1, Map.of(), new CraftEvidence(172, 10, true)).reasonKey(), "Missing recipe was not rejected.");
			final var noCraft = planner.plan(7, 1, Map.of(), new CraftEvidence(0, 0, false));
			PhantomAssertions.assertTrue(noCraft.planned(), "Planning-only recipe incorrectly required execution authorization.");
			PhantomAssertions.assertEquals("recipe.craft_evidence_missing", noCraft.plan().reasonKey(), "Missing craft prerequisite was not preserved in the plan.");
		}
	}

	private void testThresholdAndCooldown(PhantomTestContext context)
	{
		try (SyntheticSource sourceFixture = syntheticSource(context))
		{
			final PlannedSource fixture = sourceFixture.planned();
			final RankedSource current = fixture.result().ranked().getFirst();
			final Candidate failed = new Candidate(current.source().sourceId(), current.source().method(), current.score(), catalog(context).switchPolicy().failureThreshold(), 100, "source.target_unavailable");
			final var cooling = fixture.planner().plan(withPrevious(fixture.request(), Map.of(failed.sourceId(), failed), 101));
			PhantomAssertions.assertTrue(cooling.ranked().stream().noneMatch(value -> value.source().sourceId().equals(failed.sourceId())), "Failed source ignored its cooldown.");
			PhantomAssertions.assertTrue(!cooling.ranked().isEmpty(), "No deterministic alternative remained during cooldown.");
			final var expired = fixture.planner().plan(withPrevious(fixture.request(), Map.of(failed.sourceId(), failed), 105));
			PhantomAssertions.assertTrue(expired.ranked().stream().anyMatch(value -> value.source().sourceId().equals(failed.sourceId())), "Source did not become eligible at the exact cooldown boundary.");
			PhantomAssertions.assertEquals(cooling, fixture.planner().plan(withPrevious(fixture.request(), Map.of(failed.sourceId(), failed), 101)), "Alternative source selection is not deterministic.");
		}
	}

	private void testPartialProgressSwitch(PhantomTestContext context)
	{
		try (SyntheticSource sourceFixture = syntheticSource(context))
		{
			final PlannedSource fixture = sourceFixture.planned();
			final List<RankedSource> ranked = fixture.result().ranked();
			final List<Candidate> candidates = ranked.stream().map(RankedSource::candidate).toList();
			final Source first = ranked.get(0).source();
			final Source second = ranked.get(1).source();
			final PhantomAcquisitionState state = new PhantomAcquisitionState(HASHES, 21, 2, fixture.itemId(), 10, 100, 104, 4, Status.BLOCKED, first, candidates, 0, 0, Phase.NONE, 0, 0, 0, null, List.of(), 10);
			final PhantomAcquisitionState switched = state.switchSource(1, second, null, 11);
			PhantomAssertions.assertEquals(100L, switched.baselineCount(), "Source switch changed authoritative baseline.");
			PhantomAssertions.assertEquals(104L, switched.lastObservedCount(), "Source switch changed last authoritative count.");
			PhantomAssertions.assertEquals(4L, switched.progress(), "Source switch changed partial progress.");
			PhantomAssertions.assertEquals(1, switched.switchCount(), "Source switch count did not increment exactly once.");
			PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> state.switchSource(0, first, null, 11), "Same-source switch was accepted.");
			context.record("acquisition.partialProgress", switched.progress());
		}
	}

	private void testAuthorityAndExhaustion(PhantomTestContext context)
	{
		final Hashes changed = new Hashes(HASHES.catalog(), "f".repeat(64), HASHES.topology(), HASHES.progression(), HASHES.background());
		PhantomAssertions.assertFalse(HASHES.equals(changed), "Changed authority generation was not distinguishable.");
		final Source source = duplicateSource();
		final Candidate candidate = new Candidate(source.sourceId(), source.method(), 1, 3, 10, "source.target_unavailable");
		final PhantomAcquisitionState exhausted = new PhantomAcquisitionState(HASHES, 21, 0, 57, 1, 0, 0, 0, Status.BLOCKED, source, List.of(candidate), 0, 4, Phase.NONE, 0, 0, 0, null, List.of(), 10);
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> exhausted.switchSource(0, source, null, 11), "Exhausted source state admitted another switch.");
		PhantomAssertions.assertTrue(PhantomAcquisitionState.class.getDeclaredFields().length < 32, "Acquisition state accumulated unbounded mutable infrastructure.");
		context.record("acquisition.maximumSwitches", PhantomAcquisitionState.MAX_SWITCHES);
	}

	private void testSourcePerformance(PhantomTestContext context)
	{
		final PlannedSource fixture = productionSource(Method.DEATH_DROP, false);
		final long started = System.nanoTime();
		for (int index = 0; index < 100_000; index++)
		{
			final var result = fixture.planner().plan(fixture.request());
			if (result.ranked().isEmpty())
			{
				throw new AssertionError("Indexed source plan became empty at " + index);
			}
		}
		context.record("acquisition.sourcePlans100kNanos", System.nanoTime() - started);
	}

	private void testRecipePerformance(PhantomTestContext context)
	{
		final PhantomAcquisitionRecipePlanner planner = new PhantomAcquisitionRecipePlanner(_production.knowledge(), catalog(context).limits());
		final RecipeFact recipe = _production.knowledge().snapshot().recipeByListId().values().stream().sorted(Comparator.comparingInt(RecipeFact::recipeListId)).findFirst().orElseThrow();
		final long started = System.nanoTime();
		for (int index = 0; index < 10_000; index++)
		{
			if (!planner.plan(recipe.productItemId(), 1, Map.of(), new CraftEvidence(172, 10, true)).planned())
			{
				throw new AssertionError("Bounded recipe plan failed at " + index);
			}
		}
		context.record("acquisition.recipePlans10kNanos", System.nanoTime() - started);
	}

	private void testDecisionPerformance(PhantomTestContext context)
	{
		final PhantomProfileRepository profiles = PhantomProfileRepository.open();
		final PhantomProfile profile = profiles.create(_environment.primary().objectId());
		PhantomAcquisitionService service = null;
		try
		{
			final PhantomGoal goal = new PhantomGoal(21, PhantomAcquisitionGoalSpec.GOAL_TYPE, PhantomGoalStatus.COMPLETED, new PhantomDomainRef("profile", "self"), new PhantomDomainRef("item", "57"), 1, 1, Method.DEATH_DROP.key(), List.of(new PhantomDomainRef(PhantomAcquisitionGoalSpec.SOURCE_NAMESPACE, Method.DEATH_DROP.key())), new PhantomDomainRef(PhantomAcquisitionGoalSpec.ANCHOR_NAMESPACE, _production.topology().snapshot().anchors().getFirst().id()), PhantomAcquisitionGoalSpec.PURPOSE_KEY, 500, 0, 0, 0, Map.of(PhantomAcquisitionGoalSpec.BASELINE_CONSTRAINT, 0L, PhantomAcquisitionGoalSpec.MAXIMUM_SWITCHES_CONSTRAINT, 4L), "acquisition.performance.complete", 0);
			final PhantomGoalStateStore goals = new PhantomGoalStateStore(profiles);
			goals.insert(profile.profileId(), goal);
			final PhantomAcquisitionCatalog catalog = catalog(context);
			final PhantomCombatService combat = new PhantomCombatService(PhantomCombatBackend.inert(), new PhantomCombatCapabilityResolver(_ -> List.of()), PhantomCombatPolicy.productionDefaults(1));
			final PhantomRelevanceSignalPort signals = new PhantomRelevanceSignalPort()
			{
				@Override
				public SignalDelivery submit(long profileId, org.l2jmobius.gameserver.phantoms.activity.PhantomRelevanceSignal signal)
				{
					return SignalDelivery.ACCEPTED;
				}

				@Override
				public SignalDelivery withdraw(long profileId, String sourceKey, long sequence)
				{
					return SignalDelivery.ACCEPTED;
				}
			};
			final PhantomBackgroundService background = new PhantomBackgroundService(profiles, goals, PhantomIdentityLeaseRegistry.getInstance(), new PhantomBackgroundTransaction(), _production.authority(), new PhantomBackgroundCompetitionRegistry(), signals, () -> null);
			service = new PhantomAcquisitionService(catalog, new PhantomAcquisitionStore(profiles, goals), goals, new PhantomAcquisitionSourcePlanner(catalog, _production.knowledge(), _production.topology(), _production.progression()), _production.knowledge(), _production.topology(), _production.progression(), combat, background, new PhantomNavigationService(new PhantomMetrics()));
			PhantomAssertions.assertTrue(service.start(), "Acquisition Decision performance service did not start.");
			final PhantomAcquisitionDecision decision = new PhantomAcquisitionDecision(service);
			final PhantomCandidateRegistry candidates = new PhantomCandidateRegistry();
			decision.registerCandidates(candidates);
			candidates.seal();
			final PhantomStepHandlerRegistry handlers = new PhantomStepHandlerRegistry();
			decision.registerHandlers(handlers);
			handlers.seal();
			final var candidate = candidates.snapshot().getFirst();
			long checksum = 0;
			final long started = System.nanoTime();
			for (int index = 0; index < 10_000; index++)
			{
				final long sequence = index + 1L;
				final PhantomPlanningContext planning = new PhantomPlanningContext(profile.profileId(), goal, PhantomCapabilitySet.empty(), PhantomActivityState.BACKGROUND, 1, sequence, sequence, sequence);
				final var plan = candidate.planFactory().create(planning);
				final var step = plan.steps().getFirst();
				final PhantomStepResult result = handlers.snapshot().get(step.actionKey()).execute(new PhantomStepContext(profile.profileId(), goal, plan, step, PhantomActivityState.BACKGROUND, 1, sequence, sequence, 1, () -> false));
				if (result.type() != PhantomStepResult.Type.COMPLETE_GOAL)
				{
					throw new AssertionError("Acquisition Decision advance changed at " + index + ": " + result);
				}
				checksum += plan.planId();
			}
			PhantomAssertions.assertTrue(checksum != 0, "10k acquisition Decision advances were optimized away.");
			context.record("acquisition.decisionAdvances10kNanos", System.nanoTime() - started);
		}
		finally
		{
			if (service != null)
			{
				service.beginStop();
				PhantomAssertions.assertTrue(service.finishStop(), "Acquisition Decision performance service retained claims on shutdown.");
				final var snapshot = service.snapshot();
				PhantomAssertions.assertEquals(0, snapshot.currentClaims(), "Acquisition service retained transition claims.");
				PhantomAssertions.assertEquals(0, snapshot.externalClaims(), "Acquisition service retained external action claims.");
				PhantomAssertions.assertEquals(0, snapshot.navigationClaims(), "Acquisition service retained navigation claims.");
			}
			profiles.find(profile.profileId()).ifPresent(current -> profiles.delete(current.profileId(), current.rowVersion()));
		}
	}

	private void testStructuralLifecycle(PhantomTestContext context)
	{
		PhantomAssertions.assertTrue(Arrays.stream(org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionService.class.getDeclaredFields()).noneMatch(field -> Thread.class.isAssignableFrom(field.getType()) || java.util.concurrent.Executor.class.isAssignableFrom(field.getType()) || java.util.concurrent.ScheduledFuture.class.isAssignableFrom(field.getType())), "Acquisition service owns a worker/thread/future.");
		PhantomAssertions.assertEquals(8, PhantomAcquisitionState.MAX_CANDIDATES, "Candidate lifecycle bound changed.");
		PhantomAssertions.assertEquals(48, PhantomAcquisitionState.MAX_RECIPE_NODES, "Recipe lifecycle bound changed.");
		PhantomAssertions.assertEquals(8, PhantomAcquisitionState.MAX_RECEIPTS, "Receipt lifecycle bound changed.");
		PhantomAssertions.assertTrue(new PhantomAcquisitionStateCodec().declaredWorstCaseBytes() <= 4096, "Lifecycle durable payload bound changed.");
		context.record("acquisition.ownedWorkers", 0);
		context.record("acquisition.retainedClaimsAfterStop", 0);
	}

	private PlannedSource productionSource(Method method, boolean allowAmbiguous)
	{
		final PhantomAcquisitionCatalog catalog = PhantomAcquisitionCatalog.load(Path.of("data/phantoms/acquisition/high-five-acquisition-v1.xml"));
		final PhantomAcquisitionSourcePlanner planner = new PhantomAcquisitionSourcePlanner(catalog, _production.knowledge(), _production.topology(), _production.progression());
		final Set<Integer> itemIds = method == Method.DEATH_DROP ? _production.knowledge().snapshot().dropSourcesByItem().keySet() : _production.knowledge().snapshot().spoilSourcesByItem().keySet();
		for (int itemId : itemIds.stream().sorted().toList())
		{
			final var request = request(method, itemId, Map.of(), 0);
			final var result = planner.plan(request);
			if (!result.ranked().isEmpty() && (allowAmbiguous || result.selected() != null))
			{
				return new PlannedSource(itemId, planner, request, result);
			}
		}
		throw new AssertionError("No production " + method + " source fits checkpoint bounds: " + sourceDiagnostics(method));
	}

	private String sourceDiagnostics(Method method)
	{
		final List<DropFact> facts = (method == Method.DEATH_DROP ? _production.knowledge().snapshot().dropSourcesByItem() : _production.knowledge().snapshot().spoilSourcesByItem()).values().stream().flatMap(List::stream).toList();
		long withAreas = 0;
		long withNodes = 0;
		long withAnchors = 0;
		long eligibleFacts = 0;
		int minimumAnchorOrdinal = Integer.MAX_VALUE;
		int minimumAreaOrdinal = Integer.MAX_VALUE;
		for (DropFact fact : facts)
		{
			final var areas = _production.knowledge().snapshot().spawnAreasByNpc().getOrDefault(fact.npcId(), List.of());
			if (!areas.isEmpty())
			{
				withAreas++;
			}
			if (areas.stream().anyMatch(area -> area.topologyNodeId() != null))
			{
				withNodes++;
			}
			if (areas.stream().filter(area -> area.topologyNodeId() != null).anyMatch(area -> !_production.topology().snapshot().anchorsByNode().getOrDefault(area.topologyNodeId(), List.of()).isEmpty()))
			{
				withAnchors++;
				final List<DropFact> itemFacts = method == Method.DEATH_DROP ? _production.knowledge().snapshot().dropSourcesByItem().getOrDefault(fact.itemId(), List.of()) : _production.knowledge().snapshot().spoilSourcesByItem().getOrDefault(fact.itemId(), List.of());
				minimumAnchorOrdinal = Math.min(minimumAnchorOrdinal, itemFacts.indexOf(fact));
				for (int index = 0; index < areas.size(); index++)
				{
					final var area = areas.get(index);
					if ((area.topologyNodeId() != null) && !_production.topology().snapshot().anchorsByNode().getOrDefault(area.topologyNodeId(), List.of()).isEmpty())
					{
						minimumAreaOrdinal = Math.min(minimumAreaOrdinal, index);
					}
				}
				if (_production.knowledge().findNpc(fact.npcId()).filter(npc -> npc.kind() == org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.NpcKind.MONSTER && npc.attackable() && npc.targetable()).isPresent() && (fact.rawGroupChance() > 0) && (fact.rawItemChance() > 0) && (fact.minimumCount() > 0))
				{
					eligibleFacts++;
				}
			}
		}
		return "facts=" + facts.size() + ",areas=" + withAreas + ",nodes=" + withNodes + ",anchors=" + withAnchors + ",eligible=" + eligibleFacts + ",minimumAnchorOrdinal=" + minimumAnchorOrdinal + ",minimumAreaOrdinal=" + minimumAreaOrdinal;
	}

	private SyntheticSource syntheticSource(PhantomTestContext context)
	{
		try
		{
			final Path root = Files.createTempDirectory(context.reportsDirectory(), "acquisition-sources-");
			_temporaryRoots.add(root);
			Files.createDirectories(root.resolve("curated"));
			Files.writeString(root.resolve("Seeds.xml"), "<?xml version=\"1.0\" encoding=\"UTF-8\"?><list />", StandardCharsets.UTF_8);
			Files.writeString(root.resolve("curated/knowledge.xml"), PhantomGameKnowledgeCoreSuite.curatedXml(), StandardCharsets.UTF_8);
			final PhantomGameKnowledgeBackend delegate = new PhantomGameKnowledgeCoreSuite.SyntheticBackend(false, false, false, 50d);
			final PhantomGameKnowledgeBackend backend = new PhantomGameKnowledgeBackend()
			{
				@Override
				public BackendData load(PhantomGameKnowledgePolicy policy)
				{
					final BackendData base = delegate.load(policy);
					final List<NpcFact> npcs = base.npcs().stream().map(npc -> npc.npcId() == 103 ? new NpcFact(103, 20, npc.kind(), npc.attackable(), npc.targetable(), npc.canBeSown(), npc.exp(), npc.sp(), npc.authority()) : npc).toList();
					final ArrayList<DropFact> drops = new ArrayList<>(base.drops().stream().filter(fact -> (fact.itemId() != 1) || ((fact.npcId() != 102) && (fact.npcId() != 103))).toList());
					drops.add(new DropFact(102, 1, DropSourceKind.DEATH_DROP, ChanceModel.UNGROUPED_INDEPENDENT, -1, 0, 0, 10, 1, 1, PhantomGameKnowledgeAuthority.SERVER_LOADER_FACT));
					drops.add(new DropFact(103, 1, DropSourceKind.DEATH_DROP, ChanceModel.UNGROUPED_INDEPENDENT, -1, 0, 0, 10, 1, 1, PhantomGameKnowledgeAuthority.SERVER_LOADER_FACT));
					return new BackendData(base.items(), npcs, drops, base.spawns(), base.recipes(), base.classes(), base.completeClassSkills());
				}

				@Override
				public boolean sourceExists(String relativeDatapackPath)
				{
					return delegate.sourceExists(relativeDatapackPath);
				}
			};
			final PhantomTopologyCoreSuite.TestBackend topologyBackend = new PhantomTopologyCoreSuite.TestBackend();
			topologyBackend._npcs.put(103, new PhantomTopologyValidationBackend.NpcFact(103, "Monster", true));
			topologyBackend._spawns.put(103, List.of(new PhantomTopologyValidationBackend.SpawnFact(103, new PhantomTopologyPoint(150, 150, 0, 0))));
			final PhantomTopologyNode node = new PhantomTopologyNode("synthetic.area", PhantomTopologyNodeKind.FARMING_AREA, 0, PhantomTopologyArea.cuboid(0, 0, 1000, 0, 1000, -100, 100), null, List.of(), List.of());
			final PhantomTopologyAnchor anchor = new PhantomTopologyAnchor("synthetic.anchor", PhantomTopologyAnchorRole.ROUTE, node.id(), new PhantomTopologyPoint(100, 100, 0, 0), null, null, 0, List.of(), List.of());
			final PhantomTopologySnapshot snapshot = PhantomTopologySnapshot.create(1, "synthetic-acquisition", 1, 1, List.of(node), List.of(anchor), List.of(), topologyBackend, PhantomTopologyPolicy.productionDefaults());
			final PhantomTopologyQuery topology = new PhantomTopologyQuery(snapshot, topologyBackend, new PhantomTopologyMetrics());
			final PhantomGameKnowledgePolicy policy = PhantomGameKnowledgePolicy.productionDefaults();
			final PhantomGameKnowledgeService service = new PhantomGameKnowledgeService(new PhantomGameKnowledgeBuilder(backend, new PhantomStaticManorParser(root.resolve("Seeds.xml"), policy), new PhantomCuratedKnowledgeParser(root.resolve("curated"), backend, policy), topology, policy));
			PhantomAssertions.assertTrue(service.start(), "Synthetic source knowledge did not start.");
			final PhantomAcquisitionSourcePlanner planner = new PhantomAcquisitionSourcePlanner(catalog(context), service.query(), topology, _production.progression());
			final var request = new PhantomAcquisitionSourcePlanner.Request(1, 1, 1, PhantomActivityState.BACKGROUND, 88, 20, Map.of(), Map.of(), Set.of(Method.DEATH_DROP), Method.DEATH_DROP, "synthetic.anchor", Map.of(), 0);
			final var result = planner.plan(request);
			PhantomAssertions.assertEquals(2, result.ranked().size(), "Synthetic alternative source fixture is incomplete.");
			return new SyntheticSource(service, new PlannedSource(1, planner, request, result));
		}
		catch (Exception exception)
		{
			throw new AssertionError("Could not build synthetic acquisition alternatives.", exception);
		}
	}

	private PhantomAcquisitionSourcePlanner.Request request(Method method, int itemId, Map<String, Candidate> previous, long minute)
	{
		final int classId = method == Method.SPOIL_SWEEP ? 117 : 88;
		final Map<Integer, Integer> skills = method == Method.SPOIL_SWEEP ? Map.of(348, 1, 42, 1) : Map.of();
		return new PhantomAcquisitionSourcePlanner.Request(1, itemId, 1, PhantomActivityState.BACKGROUND, classId, 85, Map.of(), skills, Set.of(method), method, "", previous, minute);
	}

	private static PhantomAcquisitionSourcePlanner.Request withPrevious(PhantomAcquisitionSourcePlanner.Request source, Map<String, Candidate> previous, long minute)
	{
		return new PhantomAcquisitionSourcePlanner.Request(source.profileId(), source.itemId(), source.remainingAmount(), source.activityState(), source.classId(), source.level(), source.inventory(), source.knownSkills(), source.allowedMethods(), source.preferredMethod(), source.currentAnchorId(), previous, minute);
	}

	private SyntheticKnowledge syntheticRecipes(PhantomTestContext context, RecipeShape shape) throws Exception
	{
		final Path root = Files.createTempDirectory(context.reportsDirectory(), "acquisition-recipes-");
		_temporaryRoots.add(root);
		Files.createDirectories(root.resolve("curated"));
		Files.writeString(root.resolve("Seeds.xml"), "<?xml version=\"1.0\" encoding=\"UTF-8\"?><list><castle id=\"1\"><crop id=\"7\" seedId=\"1\" mature_Id=\"2\" reward1=\"3\" reward2=\"4\" alternative=\"false\" level=\"1\" limit_seed=\"1\" limit_crops=\"1\" /></castle></list>", StandardCharsets.UTF_8);
		Files.writeString(root.resolve("curated/knowledge.xml"), PhantomGameKnowledgeCoreSuite.curatedXml(), StandardCharsets.UTF_8);
		final PhantomGameKnowledgeBackend delegate = new PhantomGameKnowledgeCoreSuite.SyntheticBackend(false, false, false, 50d);
		final PhantomGameKnowledgeBackend backend = new PhantomGameKnowledgeBackend()
		{
			@Override
			public BackendData load(PhantomGameKnowledgePolicy policy)
			{
				final BackendData base = delegate.load(policy);
				final ArrayList<ItemFact> items = new ArrayList<>(base.items());
				final ArrayList<RecipeFact> recipes = new ArrayList<>();
				final int highest = shape == RecipeShape.NODES ? 70 : (shape == RecipeShape.DEFICITS ? 50 : (shape == RecipeShape.DEPTH ? 20 : 10));
				for (int itemId = 11; itemId <= highest; itemId++)
				{
					items.add(new ItemFact(itemId, ItemCategory.ETC, "NONE", itemId, true, PhantomGameKnowledgeAuthority.SERVER_LOADER_FACT));
				}
				switch (shape)
				{
					case SHARED ->
					{
						recipes.add(base.recipes().getFirst());
						recipes.add(recipe(11, 9, List.of(new IngredientFact(1, 2), new IngredientFact(2, 1))));
						recipes.add(recipe(12, 10, List.of(new IngredientFact(1, 1), new IngredientFact(2, 2))));
					}
					case CYCLE ->
					{
						recipes.add(base.recipes().getFirst());
						recipes.add(recipe(11, 9, List.of(new IngredientFact(7, 1))));
					}
					case DEPTH ->
					{
						recipes.add(base.recipes().getFirst());
						recipes.add(recipe(11, 9, List.of(new IngredientFact(11, 1))));
						for (int item = 11, id = 12; item <= 18; item++, id++)
						{
							recipes.add(recipe(id, item, List.of(new IngredientFact(item + 1, 1))));
						}
					}
					case NODES -> recipes.add(recipe(10, 7, java.util.stream.IntStream.rangeClosed(11, 59).mapToObj(item -> new IngredientFact(item, 1)).toList()));
					case DEFICITS -> recipes.add(recipe(10, 7, java.util.stream.IntStream.rangeClosed(11, 43).mapToObj(item -> new IngredientFact(item, 1)).toList()));
				}
				return new BackendData(items, base.npcs(), base.drops(), base.spawns(), recipes, base.classes(), base.completeClassSkills());
			}

			@Override
			public boolean sourceExists(String relativeDatapackPath)
			{
				return delegate.sourceExists(relativeDatapackPath);
			}
		};
		final PhantomGameKnowledgePolicy policy = PhantomGameKnowledgePolicy.productionDefaults();
		final PhantomGameKnowledgeService service = new PhantomGameKnowledgeService(new PhantomGameKnowledgeBuilder(backend, new PhantomStaticManorParser(root.resolve("Seeds.xml"), policy), new PhantomCuratedKnowledgeParser(root.resolve("curated"), backend, policy), PhantomGameKnowledgeCoreSuite.topology(), policy));
		PhantomAssertions.assertTrue(service.start(), "Synthetic recipe knowledge did not start.");
		return new SyntheticKnowledge(service);
	}

	private static RecipeFact recipe(int listId, int product, List<IngredientFact> ingredients)
	{
		return new RecipeFact(listId, 6, product, 1, 8, 1, 0, 1, 100, true, ingredients, PhantomGameKnowledgeAuthority.SERVER_LOADER_FACT);
	}

	private static PhantomAcquisitionCatalog catalog(PhantomTestContext context)
	{
		return PhantomAcquisitionCatalog.load(xml(context));
	}

	private static Path xml(PhantomTestContext context)
	{
		return context.moduleRoot().resolve("dist/game/data/phantoms/acquisition/high-five-acquisition-v1.xml");
	}

	private static void assertInvalidCatalog(PhantomTestContext context, String contents, String suffix) throws Exception
	{
		final Path invalid = Files.createTempFile(context.reportsDirectory(), "acquisition-" + suffix + '-', ".xml");
		try
		{
			Files.writeString(invalid, contents, StandardCharsets.UTF_8);
			PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> PhantomAcquisitionCatalog.load(invalid), "Strict catalog accepted " + suffix);
		}
		finally
		{
			Files.deleteIfExists(invalid);
		}
	}

	private static String swapLines(String text, String firstMarker, String secondMarker)
	{
		final String first = text.lines().filter(line -> line.contains(firstMarker)).findFirst().orElseThrow();
		final String second = text.lines().filter(line -> line.contains(secondMarker)).findFirst().orElseThrow();
		final String separator = text.contains("\r\n") ? "\r\n" : "\n";
		return text.replace(first + separator + second, second + separator + first);
	}

	private static PhantomAcquisitionState methodState(Method method)
	{
		final Source source = source(method, 0);
		final Candidate candidate = new Candidate(source.sourceId(), method, 100, 0, 0, "");
		final RecipePlan recipe = method == Method.RECIPE_PREPARATION ? smallRecipe() : null;
		final Status status = (method == Method.DEATH_DROP) || (method == Method.SPOIL_SWEEP) ? Status.READY : Status.DEFERRED_CHECKPOINT_2;
		final Phase phase = status == Status.READY ? Phase.TARGET_REQUIRED : Phase.NONE;
		return new PhantomAcquisitionState(HASHES, 1, 0, 57, 10, 5, 5, 0, status, source, List.of(candidate), 0, 0, phase, 0, 0, 0, recipe, List.of(), 1);
	}

	private static PhantomAcquisitionState statusState(Status status)
	{
		if (status == Status.COMPLETED)
		{
			return new PhantomAcquisitionState(HASHES, 1, 0, 57, 1, 0, 1, 1, status, null, List.of(), 0, 0, Phase.NONE, 0, 0, 0, null, List.of(), 1);
		}
		if ((status == Status.PLANNING) || (status == Status.STALE_AUTHORITY) || (status == Status.INCONSISTENT))
		{
			return new PhantomAcquisitionState(HASHES, 1, 0, 57, 1, 0, 0, 0, status, null, List.of(), 0, 0, Phase.NONE, 0, 0, 0, null, List.of(), 1);
		}
		final Source source = duplicateSource();
		final Candidate candidate = new Candidate(source.sourceId(), source.method(), 100, status == Status.BLOCKED ? 1 : 0, status == Status.BLOCKED ? 1 : 0, status == Status.BLOCKED ? "source.target_unavailable" : "");
		final Phase phase = (status == Status.READY) || (status == Status.ACTIVE) ? Phase.TARGET_REQUIRED : Phase.NONE;
		return new PhantomAcquisitionState(HASHES, 1, 0, 57, 1, 0, 0, 0, status, source, List.of(candidate), 0, 0, phase, 0, 0, 0, null, List.of(), 1);
	}

	private static PhantomAcquisitionState phaseState(Phase phase)
	{
		if (phase == Phase.NONE)
		{
			return statusState(Status.PLANNING);
		}
		final boolean spoil = Set.of(Phase.SPOIL_PREPARED, Phase.SPOIL_DISPATCHING, Phase.SPOIL_OBSERVED, Phase.SWEEP_PREPARED, Phase.SWEEP_DISPATCHING).contains(phase);
		final Source source = source(spoil ? Method.SPOIL_SWEEP : Method.DEATH_DROP, 0);
		final Candidate candidate = new Candidate(source.sourceId(), source.method(), 100, 0, 0, "");
		final boolean target = !Set.of(Phase.TRAVEL_REQUIRED, Phase.TARGET_REQUIRED).contains(phase);
		return new PhantomAcquisitionState(HASHES, 1, 0, 57, 1, 0, 0, 0, phase == Phase.TARGET_REQUIRED || phase == Phase.TRAVEL_REQUIRED ? Status.READY : Status.ACTIVE, source, List.of(candidate), 0, 0, phase, target ? 1000 : 0, target ? source.npcId() : 0, 0, null, List.of(), 1);
	}

	private static PhantomAcquisitionState maximumExecutionState()
	{
		final Source source = new Source(hash(1), Method.DEATH_DROP, 100, 57, "f".repeat(160), "n".repeat(96), "a".repeat(96), 0, 0, 0, 0, 0);
		final List<Candidate> candidates = java.util.stream.IntStream.range(0, 8).mapToObj(index -> new Candidate(hash(index + 1), Method.DEATH_DROP, 1000 - index, 8, 10, "r".repeat(64))).toList();
		final List<Receipt> receipts = java.util.stream.IntStream.range(0, 8).mapToObj(index -> new Receipt(hash(100 + index), source.sourceId(), ReceiptKind.ACTIVE_DEATH_DROP, index, index + 1, TerminalResult.OBSERVED, index)).toList();
		return new PhantomAcquisitionState(HASHES, 1, 0, 57, 100, 0, 0, 0, Status.BLOCKED, source, candidates, 0, 4, Phase.NONE, 0, 0, 0, null, receipts, 10);
	}

	private static PhantomAcquisitionState maximumRecipeState()
	{
		final Source source = source(Method.RECIPE_PREPARATION, 0);
		final List<RecipeNode> nodes = java.util.stream.IntStream.rangeClosed(1, 48).mapToObj(item -> new RecipeNode(item, 10, 0, 10, item == 1 ? 1 : 0, Math.min(6, item / 8), item != 1)).toList();
		final List<Deficit> deficits = java.util.stream.IntStream.rangeClosed(2, 33).mapToObj(item -> new Deficit(item, 10, true, true)).toList();
		final RecipePlan recipe = new RecipePlan(1, 57, 10, 10, 10, 100, true, 172, 10, nodes, deficits, "r".repeat(64));
		return new PhantomAcquisitionState(HASHES, 1, 0, 57, 10, 0, 0, 0, Status.DEFERRED_CHECKPOINT_2, source, List.of(new Candidate(source.sourceId(), source.method(), 1, 0, 0, "")), 0, 0, Phase.NONE, 0, 0, 0, recipe, List.of(), 10);
	}

	private static RecipePlan smallRecipe()
	{
		return new RecipePlan(1, 57, 1, 1, 1, 100, true, 172, 1, List.of(new RecipeNode(57, 1, 0, 1, 1, 0, false), new RecipeNode(1, 1, 0, 1, 0, 1, true)), List.of(new Deficit(1, 1, false, true)), "");
	}

	private static Source duplicateSource()
	{
		return source(Method.DEATH_DROP, 0);
	}

	private static Source source(Method method, int variant)
	{
		final int npcId = (method == Method.DEATH_DROP) || (method == Method.SPOIL_SWEEP) ? 100 + variant : 0;
		final String fact = method == Method.RECIPE_PREPARATION ? "recipe:1:57" : method.key() + ":fact";
		return new Source(hash(method.code() * 100 + variant), method, npcId, 57, fact, method == Method.RECIPE_PREPARATION ? "planning" : "node", method == Method.RECIPE_PREPARATION ? "planning" : "anchor", 0, method == Method.SPOIL_SWEEP ? 348 : 0, method == Method.SPOIL_SWEEP ? 1 : 0, method == Method.SPOIL_SWEEP ? 42 : 0, method == Method.SPOIL_SWEEP ? 1 : 0);
	}

	private static String hash(int value)
	{
		return String.format("%064x", value);
	}

	private static void deleteTree(Path root) throws Exception
	{
		if (!Files.exists(root))
		{
			return;
		}
		try (var paths = Files.walk(root))
		{
			for (Path path : paths.sorted(Comparator.reverseOrder()).toList())
			{
				Files.deleteIfExists(path);
			}
		}
	}

	private enum RecipeShape
	{
		SHARED,
		CYCLE,
		DEPTH,
		NODES,
		DEFICITS
	}

	private record PlannedSource(int itemId, PhantomAcquisitionSourcePlanner planner, PhantomAcquisitionSourcePlanner.Request request, PhantomAcquisitionSourcePlanner.Result result)
	{
	}

	private record SyntheticKnowledge(PhantomGameKnowledgeService service) implements AutoCloseable
	{
		private PhantomGameKnowledgeQuery query()
		{
			return service.query();
		}

		@Override
		public void close()
		{
			service.beginStop();
			PhantomAssertions.assertTrue(service.finishStop(), "Synthetic recipe knowledge did not stop.");
		}
	}

	private record SyntheticSource(PhantomGameKnowledgeService service, PlannedSource planned) implements AutoCloseable
	{
		@Override
		public void close()
		{
			service.beginStop();
			PhantomAssertions.assertTrue(service.finishStop(), "Synthetic source knowledge did not stop.");
		}
	}
}
