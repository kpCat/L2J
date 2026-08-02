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
import java.util.HashMap;
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
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionSourcePlanner.ResourceEvidence;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionService;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionStore;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionStore.StoredState;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.Candidate;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.Deficit;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.Hashes;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.ManorBinding;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.Phase;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.Receipt;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.ReceiptKind;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.QuestBinding;
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
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatActorLease;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.AcquisitionActorPosition;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.AcquisitionSkillKind;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.AcquisitionTargetSnapshot;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.ActionOutcome;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.ActorSnapshot;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.LootCandidate;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.LootObservation;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.RespawnOutcome;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.ShotOutcome;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.TargetSnapshot;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.ThreatObservation;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatCapabilityResolver;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatCapabilityResolver.CapabilityEvidence;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatLoadout.SelectedSkill;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatMetrics;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatMode;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatPolicy;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatRequest;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatService;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatService.DispatchHandle;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatService.DispatchResult;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatService.DispatchState;
import org.l2jmobius.gameserver.phantoms.combat.PhantomOwnedAction;
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
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.SpawnFact;
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
		registry.add("04-schema-v1-recovery-and-v2-attempt-truth", this::testCodecRecovery);
	}

	private void registerSourcePlanner(PhantomTestRegistry registry)
	{
		final String focus = System.getProperty("phantom.acquisition.focus", "");
		if ("capability".equals(focus))
		{
			registry.add("01-dwarf-lineage-canonical-skills-and-actual-levels", this::testProfessionCapabilities);
			return;
		}
		if ("scoring".equals(focus))
		{
			registry.add("01-cross-method-ambiguity-and-all-policy-evidence", this::testAmbiguityAndScoringEvidence);
			return;
		}
		registry.add("01-production-death-drop-source-is-authoritative", context -> testProductionSource(context, Method.DEATH_DROP));
		registry.add("02-production-spoil-source-and-capability-are-exact", context -> testProductionSource(context, Method.SPOIL_SWEEP));
		registry.add("03-deterministic-ranking-bounds-and-no-corpus-copy", this::testPlannerDeterminism);
		registry.add("04-dwarf-lineage-canonical-skills-and-actual-levels", this::testProfessionCapabilities);
		registry.add("05-cross-method-ambiguity-and-all-policy-evidence", this::testAmbiguityAndScoringEvidence);
	}

	private void registerRecipePlanning(PhantomTestRegistry registry)
	{
		if ("recipe-inventory".equals(System.getProperty("phantom.acquisition.focus", "")))
		{
			registry.add("01-active-service-exact-recipe-inventory-truth", this::testProductionRecipeInventoryTruth);
			registry.add("02-failed-probe-excludes-recipe-before-service-inventory", this::testRecipeProbeUnionServiceBounds);
			return;
		}
		registry.add("01-production-direct-and-multilevel-recipes", this::testProductionRecipes);
		registry.add("02-inventory-ceiling-shared-dag-and-deferred-leaves", this::testSharedRecipeDag);
		registry.add("03-cycle-depth-node-deficit-and-prerequisite-controls", this::testRecipeNegativeControls);
		registry.add("04-active-service-exact-recipe-inventory-truth", this::testProductionRecipeInventoryTruth);
		registry.add("05-failed-probe-excludes-recipe-before-service-inventory", this::testRecipeProbeUnionServiceBounds);
	}

	private void registerSourceSwitching(PhantomTestRegistry registry)
	{
		final String focus = System.getProperty("phantom.acquisition.focus", "");
		if ("dispatch".equals(focus))
		{
			registry.add("01-service-dispatch-recovery-and-terminal-release", this::testDispatchRecovery);
			return;
		}
		if ("combat".equals(focus))
		{
			registry.add("01-combat-prepared-submitted-reconciliation", this::testCombatReconciliation);
			return;
		}
		registry.add("01-threshold-cooldown-and-deterministic-alternative", this::testThresholdAndCooldown);
		registry.add("02-partial-progress-switch-preserves-baseline", this::testPartialProgressSwitch);
		registry.add("03-authority-drift-and-exhausted-bounds", this::testAuthorityAndExhaustion);
		registry.add("04-service-dispatch-recovery-and-terminal-release", this::testDispatchRecovery);
		registry.add("05-combat-prepared-submitted-reconciliation", this::testCombatReconciliation);
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
		PhantomAssertions.assertEquals(List.of(Method.DEATH_DROP, Method.SPOIL_SWEEP, Method.RECIPE_PREPARATION, Method.MANOR_CROP, Method.QUEST_COLLECTION).stream().map(first::method).map(PhantomAcquisitionCatalog.MethodPolicy::status).toList(), List.of(MethodStatus.EXECUTABLE, MethodStatus.EXECUTABLE, MethodStatus.PLANNING_ONLY, MethodStatus.EXECUTABLE, MethodStatus.EXECUTABLE), "Checkpoint method statuses changed.");
		PhantomAssertions.assertEquals(4096, first.limits().payloadBytes(), "acquisition.state hard payload bound changed.");
		PhantomAssertions.assertEquals(8, first.limits().sourceCandidates(), "Source candidate bound changed.");
		PhantomAssertions.assertEquals(48, first.limits().recipeNodes(), "Recipe node bound changed.");
		PhantomAssertions.assertEquals(3, first.switchPolicy().failureThreshold(), "Switch failure threshold changed.");
		PhantomAssertions.assertTrue(first.sourceScoring().preferredMethodBonus() > first.sourceScoring().ambiguityThreshold(), "Preferred method bonus cannot resolve an eligible cross-method tie.");

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

	private void testCodecRecovery(PhantomTestContext context)
	{
		final PhantomAcquisitionStateCodec codec = new PhantomAcquisitionStateCodec();
		final PhantomAcquisitionState dispatching = phaseState(Phase.SPOIL_DISPATCHING).withPhase(Phase.SPOIL_DISPATCHING, 1000, 100, 0, 2, 9);
		PhantomAssertions.assertEquals(2, codec.decode(encodeSchema(codec, dispatching, 2)).phaseAttempt(), "Schema v2 lost the persisted dispatch attempt.");
		final byte[] legacy = encodeSchema(codec, phaseState(Phase.SPOIL_DISPATCHING), 1);
		final PhantomAcquisitionState recovered = codec.decode(legacy);
		PhantomAssertions.assertEquals(Phase.SPOIL_DISPATCHING, recovered.phase(), "Legacy DISPATCHING phase changed during recovery.");
		PhantomAssertions.assertEquals(0, recovered.phaseAttempt(), "Legacy schema did not recover with attempt zero.");
		context.record("acquisition.legacySchemaBytes", legacy.length);
	}

	private static byte[] encodeSchema(PhantomAcquisitionStateCodec codec, PhantomAcquisitionState state, int version)
	{
		try
		{
			final var method = PhantomAcquisitionStateCodec.class.getDeclaredMethod("encode", PhantomAcquisitionState.class, int.class);
			method.setAccessible(true);
			return (byte[]) method.invoke(codec, state, version);
		}
		catch (ReflectiveOperationException exception)
		{
			throw new AssertionError("Could not exercise a supported acquisition schema.", exception);
		}
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
			PhantomAssertions.assertEquals(254, source.spoilSkillId(), "Production spoil capability identity changed.");
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

	private void testProfessionCapabilities(PhantomTestContext context)
	{
		for (int classId : List.of(53, 54, 55, 117))
		{
			final var rules = _production.progression().capabilities(classId);
			PhantomAssertions.assertTrue(rules.stream().anyMatch(rule -> "profession.spoil".equals(rule.capabilityKey()) && (rule.actionSkill().skillId() == 254) && (rule.actionSkill().skillLevel() == 1)), "Dwarf spoil lineage is incomplete for class " + classId);
			PhantomAssertions.assertTrue(rules.stream().anyMatch(rule -> "profession.sweep".equals(rule.capabilityKey()) && (rule.actionSkill().skillId() == 42) && (rule.actionSkill().skillLevel() == 1)), "Dwarf sweep lineage is incomplete for class " + classId);
		}
		for (int classId : List.of(53, 56, 57, 118))
		{
			PhantomAssertions.assertTrue(_production.progression().capabilities(classId).stream().anyMatch(rule -> "profession.craft".equals(rule.capabilityKey()) && (rule.actionSkill().skillId() == 172) && (rule.actionSkill().skillLevel() == 1)), "Dwarf craft lineage is incomplete for class " + classId);
		}
		PhantomAssertions.assertTrue(_production.progression().capabilities(88).stream().noneMatch(rule -> rule.capabilityKey().startsWith("profession.")), "Non-profession class inherited a Dwarf capability.");
		final PlannedSource spoil = productionSource(Method.SPOIL_SWEEP, false);
		PhantomAssertions.assertEquals(254, spoil.result().selected().source().spoilSkillId(), "Spoil Crush replaced canonical Spoil.");
		PhantomAssertions.assertEquals(11, spoil.result().selected().source().spoilSkillLevel(), "Planner persisted the rule minimum instead of the actual learned Spoil level.");
		final var crushOnly = new PhantomAcquisitionSourcePlanner.Request(1, spoil.itemId(), 1, PhantomActivityState.ACTIVE, 117, 85, Map.of(), Map.of(348, 1, 42, 1), Set.of(Method.SPOIL_SWEEP), Method.SPOIL_SWEEP, "", Map.of(), 0);
		PhantomAssertions.assertTrue(spoil.planner().plan(crushOnly).ranked().isEmpty(), "Spoil Crush was admitted as generic Spoil evidence.");
		context.record("acquisition.professionClasses", 8);
	}

	private void testAmbiguityAndScoringEvidence(PhantomTestContext context) throws Exception
	{
		try (SyntheticSource fixture = syntheticSource(context))
		{
			final PhantomAcquisitionSourcePlanner.Result deathTie = fixture.planned().result();
			PhantomAssertions.assertEquals("source.ambiguous", deathTie.reasonKey(), "Two equal death sources were not reported as ambiguous.");
			PhantomAssertions.assertTrue(deathTie.selected() == null, "Equal death sources selected a source.");
			PhantomAssertions.assertTrue((deathTie.ranked().size() == 2) && deathTie.ranked().stream().allMatch(value -> value.source().method() == Method.DEATH_DROP) && (deathTie.ranked().get(0).score() == deathTie.ranked().get(1).score()), "Preferred death bonus did not preserve the same-method tie: " + deathTie);
			final PhantomAcquisitionSourcePlanner.Result repeatedDeathTie = fixture.planned().planner().plan(fixture.planned().request());
			PhantomAssertions.assertTrue(Arrays.equals(deathTie.toString().getBytes(StandardCharsets.UTF_8), repeatedDeathTie.toString().getBytes(StandardCharsets.UTF_8)), "Repeated equal death ranking was not byte-identical.");

			final String equalPreferences = Files.readString(xml(context), StandardCharsets.UTF_8).replace("<method key=\"death_drop\" status=\"EXECUTABLE\" preference=\"700\" />", "<method key=\"death_drop\" status=\"EXECUTABLE\" preference=\"800\" />");
			final Path policy = Files.createTempFile(context.reportsDirectory(), "acquisition-cross-method-", ".xml");
			Files.writeString(policy, equalPreferences, StandardCharsets.UTF_8);
			try
			{
				final PhantomAcquisitionSourcePlanner crossPlanner = new PhantomAcquisitionSourcePlanner(PhantomAcquisitionCatalog.load(policy), fixture.service().query(), fixture.topology(), _production.progression());
				final ResourceEvidence resources = new ResourceEvidence(0, 100, 0, 100, true);
				final var spoilTieRequest = new PhantomAcquisitionSourcePlanner.Request(1, 1, 1, PhantomActivityState.BACKGROUND, 117, 20, Map.of(), Map.of(254, 11, 42, 1), Set.of(Method.SPOIL_SWEEP), Method.SPOIL_SWEEP, "synthetic.anchor", "", resources, Map.of(), 0);
				final var spoilTie = crossPlanner.plan(spoilTieRequest);
				PhantomAssertions.assertEquals("source.ambiguous", spoilTie.reasonKey(), "Two equal spoil sources were not reported as ambiguous.");
				PhantomAssertions.assertTrue((spoilTie.selected() == null) && (spoilTie.ranked().size() == 2) && spoilTie.ranked().stream().allMatch(value -> value.source().method() == Method.SPOIL_SWEEP) && (spoilTie.ranked().get(0).score() == spoilTie.ranked().get(1).score()), "Preferred spoil bonus did not preserve the same-method tie.");

				final var allSourcesRequest = new PhantomAcquisitionSourcePlanner.Request(1, 1, 1, PhantomActivityState.BACKGROUND, 117, 20, Map.of(), Map.of(254, 11, 42, 1), Set.of(Method.DEATH_DROP, Method.SPOIL_SWEEP), null, "synthetic.anchor", "", resources, Map.of(), 0);
				final var allSources = crossPlanner.plan(allSourcesRequest);
				final Map<String, Candidate> coolingDuplicates = new LinkedHashMap<>();
				final Set<Method> retainedMethods = java.util.EnumSet.noneOf(Method.class);
				for (RankedSource value : allSources.ranked())
				{
					if (!retainedMethods.add(value.source().method()))
					{
						coolingDuplicates.put(value.source().sourceId(), new Candidate(value.source().sourceId(), value.source().method(), value.score(), 3, 10, "source.target_unavailable"));
					}
				}
				final var crossRequest = new PhantomAcquisitionSourcePlanner.Request(1, 1, 1, PhantomActivityState.BACKGROUND, 117, 20, Map.of(), Map.of(254, 11, 42, 1), Set.of(Method.DEATH_DROP, Method.SPOIL_SWEEP), null, "synthetic.anchor", "", resources, coolingDuplicates, 11);
				final var cross = crossPlanner.plan(crossRequest);
				PhantomAssertions.assertEquals("source.ambiguous", cross.reasonKey(), "Cross-method near tie was not reported as ambiguous.");
				PhantomAssertions.assertTrue(cross.selected() == null, "Cross-method near tie selected a source.");
				PhantomAssertions.assertTrue(cross.ranked().size() > 1 && cross.ranked().get(0).source().method() != cross.ranked().get(1).source().method(), "Cross-method ambiguity control did not compare different methods.");
				for (Method preferred : List.of(Method.DEATH_DROP, Method.SPOIL_SWEEP))
				{
					final var preferredRequest = new PhantomAcquisitionSourcePlanner.Request(1, 1, 1, PhantomActivityState.BACKGROUND, 117, 20, Map.of(), Map.of(254, 11, 42, 1), Set.of(Method.DEATH_DROP, Method.SPOIL_SWEEP), preferred, "synthetic.anchor", "", resources, coolingDuplicates, 11);
					final var preferredResult = crossPlanner.plan(preferredRequest);
					PhantomAssertions.assertEquals(preferred, preferredResult.selected().source().method(), "Preferred method bonus was applied after ambiguity for " + preferred);
					PhantomAssertions.assertEquals(preferredResult, crossPlanner.plan(preferredRequest), "Preferred method ranking is not deterministic for " + preferred);
				}
				final var ineligibleSpoil = new PhantomAcquisitionSourcePlanner.Request(1, 1, 1, PhantomActivityState.BACKGROUND, 117, 20, Map.of(), Map.of(), Set.of(Method.DEATH_DROP, Method.SPOIL_SWEEP), Method.SPOIL_SWEEP, "synthetic.anchor", "", resources, coolingDuplicates, 11);
				PhantomAssertions.assertEquals(Method.DEATH_DROP, crossPlanner.plan(ineligibleSpoil).selected().source().method(), "Preferred bonus made an ineligible Spoil source executable.");
				final Map<String, Candidate> coolingSpoil = new LinkedHashMap<>(coolingDuplicates);
				allSources.ranked().stream().filter(value -> value.source().method() == Method.SPOIL_SWEEP).forEach(value -> coolingSpoil.put(value.source().sourceId(), new Candidate(value.source().sourceId(), value.source().method(), value.score(), 3, 10, "source.target_unavailable")));
				final var coolingRequest = new PhantomAcquisitionSourcePlanner.Request(1, 1, 1, PhantomActivityState.BACKGROUND, 117, 20, Map.of(), Map.of(254, 11, 42, 1), Set.of(Method.DEATH_DROP, Method.SPOIL_SWEEP), Method.SPOIL_SWEEP, "synthetic.anchor", "", resources, coolingSpoil, 11);
				PhantomAssertions.assertEquals(Method.DEATH_DROP, crossPlanner.plan(coolingRequest).selected().source().method(), "Preferred bonus bypassed source cooldown.");
			}
			finally
			{
				Files.deleteIfExists(policy);
			}

			final RankedSource reference = fixture.planned().result().ranked().getFirst();
			final PhantomAcquisitionSourcePlanner planner = fixture.planned().planner();
			final var exact = new PhantomAcquisitionSourcePlanner.Request(1, 1, 1, PhantomActivityState.BACKGROUND, 88, 20, Map.of(), Map.of(), Set.of(Method.DEATH_DROP), Method.DEATH_DROP, reference.source().anchorId(), reference.source().sourceId(), new ResourceEvidence(0, 100, 0, 100, true), Map.of(), 0);
			final int exactScore = scoreOf(planner.plan(exact), reference.source().sourceId());
			final var noRouteEvidence = new PhantomAcquisitionSourcePlanner.Request(1, 1, 1, PhantomActivityState.BACKGROUND, 88, 20, Map.of(), Map.of(), Set.of(Method.DEATH_DROP), Method.DEATH_DROP, "unknown.anchor", reference.source().sourceId(), new ResourceEvidence(0, 100, 0, 100, true), Map.of(), 0);
			PhantomAssertions.assertTrue(exactScore > scoreOf(planner.plan(noRouteEvidence), reference.source().sourceId()), "Missing topology evidence received zero cost.");
			final var switched = new PhantomAcquisitionSourcePlanner.Request(1, 1, 1, PhantomActivityState.BACKGROUND, 88, 20, Map.of(), Map.of(), Set.of(Method.DEATH_DROP), Method.DEATH_DROP, reference.source().anchorId(), "f".repeat(64), new ResourceEvidence(0, 100, 0, 100, true), Map.of(), 0);
			PhantomAssertions.assertTrue(exactScore > scoreOf(planner.plan(switched), reference.source().sourceId()), "Switch penalty did not use the current selected source.");
			final var pressure = new PhantomAcquisitionSourcePlanner.Request(1, 1, 1, PhantomActivityState.BACKGROUND, 88, 20, Map.of(), Map.of(), Set.of(Method.DEATH_DROP), Method.DEATH_DROP, reference.source().anchorId(), reference.source().sourceId(), new ResourceEvidence(99, 100, 99, 100, true), Map.of(), 0);
			PhantomAssertions.assertTrue(exactScore > scoreOf(planner.plan(pressure), reference.source().sourceId()), "Resource reserve pressure did not affect bounded scoring.");
		}

		final RecipeFact recipe = _production.knowledge().snapshot().recipeByListId().values().stream().filter(value -> !value.ingredients().isEmpty()).sorted(Comparator.comparingInt(RecipeFact::recipeListId)).findFirst().orElseThrow();
		final IngredientFact leaf = recipe.ingredients().getFirst();
		final PhantomAcquisitionCatalog catalog = catalog(context);
		final PhantomAcquisitionSourcePlanner recipePlanner = new PhantomAcquisitionSourcePlanner(catalog, _production.knowledge(), _production.topology(), _production.progression());
		final ResourceEvidence resources = new ResourceEvidence(0, 1_000_000, 0, 100, true);
		final var withoutReuse = new PhantomAcquisitionSourcePlanner.Request(1, recipe.productItemId(), 1, PhantomActivityState.BACKGROUND, 118, 85, Map.of(), Map.of(172, 10), Set.of(Method.RECIPE_PREPARATION), Method.RECIPE_PREPARATION, "", "", resources, Map.of(), 0);
		final var withReuse = new PhantomAcquisitionSourcePlanner.Request(1, recipe.productItemId(), 1, PhantomActivityState.BACKGROUND, 118, 85, Map.of(leaf.itemId(), leaf.count()), Map.of(172, 10), Set.of(Method.RECIPE_PREPARATION), Method.RECIPE_PREPARATION, "", "", resources, Map.of(), 0);
		PhantomAssertions.assertTrue(recipePlanner.plan(withReuse).ranked().getFirst().score() > recipePlanner.plan(withoutReuse).ranked().getFirst().score(), "Recipe leaf reuse weight ignored actual inventory evidence.");
		context.record("acquisition.scoringWeightsExercised", 4);
	}

	private static int scoreOf(PhantomAcquisitionSourcePlanner.Result result, String sourceId)
	{
		return result.ranked().stream().filter(value -> value.source().sourceId().equals(sourceId)).findFirst().orElseThrow().score();
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
			final var probe = planner.probe(7, 2);
			PhantomAssertions.assertTrue(probe.successful() && probe.exactItemIds().equals(probe.exactItemIds().stream().distinct().sorted().toList()), "Recipe probe did not return a canonical exact inventory union.");
			PhantomAssertions.assertTrue(probe.exactItemIds().containsAll(List.of(1, 2, 9, 10)), "Recipe probe omitted a bounded alternative dependency.");
			PhantomAssertions.assertTrue(result.planned(), "Shared-ingredient synthetic recipe was not planned.");
			PhantomAssertions.assertEquals(2L, result.plan().batchCount(), "Synthetic root ceiling batches changed.");
			PhantomAssertions.assertEquals((long) result.plan().nodes().size(), result.plan().nodes().stream().map(RecipeNode::itemId).distinct().count(), "Shared ingredients were duplicated instead of DAG-aggregated.");
			PhantomAssertions.assertEquals(before, inventory, "Recipe planning mutated caller inventory.");
			final RecipeNode shared = result.plan().nodes().stream().filter(node -> node.itemId() == 1).findFirst().orElseThrow();
			PhantomAssertions.assertEquals(3L, shared.inventoryUsed(), "Shared ingredient inventory was not subtracted exactly once.");
			PhantomAssertions.assertTrue(result.plan().deficits().stream().noneMatch(Deficit::questDeferred), "Absent quest evidence was reported as a known quest source.");
			PhantomAssertions.assertTrue(result.plan().deficits().stream().anyMatch(Deficit::manorDeferred), "Known manor leaf was not typed as deferred.");
			PhantomAssertions.assertEquals("", result.plan().reasonKey(), "Ready craft evidence was rejected.");
			context.record("acquisition.sharedRecipeNodes", result.plan().nodes().size());
		}
		try (SyntheticKnowledge fixture = syntheticRecipes(context, RecipeShape.ALTERNATIVES))
		{
			final PhantomAcquisitionRecipePlanner planner = new PhantomAcquisitionRecipePlanner(fixture.query(), catalog(context).limits());
			PhantomAssertions.assertEquals(10, planner.plan(7, 1, Map.of(1, 2L), new CraftEvidence(172, 10, true)).plan().recipeListId(), "Actual inventory did not select the first lower-deficit alternative.");
			PhantomAssertions.assertEquals(11, planner.plan(7, 1, Map.of(2, 2L), new CraftEvidence(172, 10, true)).plan().recipeListId(), "Actual inventory did not select the second lower-deficit alternative.");
		}
	}

	private void testRecipeNegativeControls(PhantomTestContext context) throws Exception
	{
		for (RecipeShape shape : List.of(RecipeShape.CYCLE, RecipeShape.DEPTH, RecipeShape.NODES, RecipeShape.DEFICITS))
		{
			try (SyntheticKnowledge fixture = syntheticRecipes(context, shape))
			{
				final var planner = new PhantomAcquisitionRecipePlanner(fixture.query(), catalog(context).limits());
				final var result = planner.plan(7, 1, Map.of(), new CraftEvidence(172, 10, true));
				PhantomAssertions.assertFalse(result.planned(), "Recipe overflow/cycle was accepted: " + shape);
				PhantomAssertions.assertEquals("recipe.bounds", result.reasonKey(), "Recipe bound failure is not typed: " + shape);
				if (shape != RecipeShape.DEFICITS)
				{
					PhantomAssertions.assertEquals("recipe.bounds", planner.probe(7, 1).reasonKey(), "Recipe probe bound failure is not typed: " + shape);
				}
			}
		}
		try (SyntheticKnowledge fixture = syntheticRecipes(context, RecipeShape.ALTERNATIVE_UNION))
		{
			final PhantomAcquisitionRecipePlanner planner = new PhantomAcquisitionRecipePlanner(fixture.query(), catalog(context).limits());
			for (int alternative = 0; alternative < 4; alternative++)
			{
				final int intermediate = 11 + (alternative * 33);
				final var individual = planner.plan(7, 1, Map.of(intermediate + 1, 1L), new CraftEvidence(172, 10, true));
				PhantomAssertions.assertTrue(individual.planned(), "Individually bounded root alternative was rejected: " + alternative);
				PhantomAssertions.assertEquals(10 + alternative, individual.plan().recipeListId(), "Inventory evidence did not select the expected bounded root alternative.");
				PhantomAssertions.assertTrue((individual.plan().nodes().size() <= 48) && (individual.plan().deficits().size() <= 32) && individual.plan().nodes().stream().allMatch(node -> node.depth() <= 6), "An individual root alternative exceeded a recipe bound.");
			}
			PhantomAssertions.assertEquals("recipe.bounds", planner.probe(7, 1).reasonKey(), "The 132-ID alternative union did not fail closed at the 128-ID probe bound.");
			context.record("acquisition.recipeAlternativeUnionIds", 132);
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

	private void testProductionRecipeInventoryTruth(PhantomTestContext context) throws Exception
	{
		final PhantomAcquisitionRecipePlanner recipePlanner = new PhantomAcquisitionRecipePlanner(_production.knowledge(), catalog(context).limits());
		final RecipeFact recipe = _production.knowledge().snapshot().recipeByListId().values().stream().sorted(Comparator.comparingInt(RecipeFact::recipeListId)).filter(value -> recipePlanner.probe(value.productItemId(), 1).successful() && recipePlanner.plan(value.productItemId(), 1, Map.of(), new CraftEvidence(172, 10, true)).planned()).findFirst().orElseThrow();
		final List<Integer> exactIds = recipePlanner.probe(recipe.productItemId(), 1).exactItemIds();
		PhantomAssertions.assertTrue(!exactIds.isEmpty() && exactIds.size() <= 128, "Production recipe probe did not expose a bounded ingredient inventory set.");
		final Map<Integer, Long> partial = new LinkedHashMap<>();
		partial.put(exactIds.getFirst(), 1_000L);
		final Map<Integer, Long> before = Map.copyOf(partial);
		long zeroDeficit;
		try (AcquisitionServiceFixture fixture = acquisitionPlanningFixture(recipe, Map.of()))
		{
			PhantomAssertions.assertEquals(PhantomAcquisitionService.OperationStatus.SUCCESS, fixture.plan(1).status(), "Active recipe service plan without ingredients failed.");
			zeroDeficit = fixture.state().recipePlan().deficits().stream().mapToLong(Deficit::count).sum();
			PhantomAssertions.assertEquals(exactIds, fixture.lease().lastInventoryRequest, "Active recipe planning did not request only the probed exact IDs.");
			PhantomAssertions.assertEquals(0L, fixture.lease().acquisitionInventoryCounts(List.of(99999)).get(99999), "Absent active inventory item was not reported as zero.");
			PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> fixture.lease().acquisitionInventoryCounts(java.util.stream.IntStream.rangeClosed(1, 129).boxed().toList()), "129 active inventory IDs were admitted.");
			PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> fixture.lease().acquisitionInventoryCounts(List.of(2, 1)), "Unsorted active inventory IDs were admitted.");
			PhantomAssertions.assertThrows(UnsupportedOperationException.class, () -> fixture.lease().acquisitionInventoryCounts(List.of(99999)).put(99999, 1L), "Active inventory count map was mutable.");
		}
		try (AcquisitionServiceFixture fixture = acquisitionPlanningFixture(recipe, partial))
		{
			PhantomAssertions.assertEquals(PhantomAcquisitionService.OperationStatus.SUCCESS, fixture.plan(1).status(), "Active recipe service plan with partial ingredients failed.");
			final var plan = fixture.state().recipePlan();
			PhantomAssertions.assertTrue(plan.nodes().stream().mapToLong(RecipeNode::inventoryUsed).sum() > 0, "Partial active ingredients were not consumed by the final plan.");
			PhantomAssertions.assertTrue(plan.deficits().stream().mapToLong(Deficit::count).sum() < zeroDeficit, "Partial active ingredients did not reduce the final deficit.");
			PhantomAssertions.assertEquals(before, partial, "Active recipe planning mutated caller inventory evidence.");
			PhantomAssertions.assertEquals(0, fixture.service().snapshot().externalClaims(), "Active recipe planning retained an action lease.");
		}
		context.record("acquisition.recipeExactInventoryIds", exactIds.size());
	}

	private void testRecipeProbeUnionServiceBounds(PhantomTestContext context) throws Exception
	{
		try (SyntheticKnowledge fixture = syntheticRecipes(context, RecipeShape.ALTERNATIVE_UNION))
		{
			final PhantomAcquisitionRecipePlanner planner = new PhantomAcquisitionRecipePlanner(fixture.query(), catalog(context).limits());
			PhantomAssertions.assertEquals("recipe.bounds", planner.probe(7, 1).reasonKey(), "Synthetic alternative union did not exceed the exact inventory probe bound.");

			try (AcquisitionServiceFixture recipeOnly = acquisitionPlanningFixture(7, Map.of(), fixture.query(), fixture.topology(), Set.of(Method.RECIPE_PREPARATION)))
			{
				final Map<Integer, Long> before = Map.copyOf(recipeOnly.lease().inventoryCounts);
				final var result = recipeOnly.plan(1);
				PhantomAssertions.assertEquals(PhantomAcquisitionService.OperationStatus.SUCCESS, result.status(), "Recipe-only failed probe did not persist one terminal planning transition.");
				PhantomAssertions.assertEquals("recipe.bounds", result.reasonKey(), "Recipe-only failed probe lost its exact reason.");
				PhantomAssertions.assertEquals(Status.BLOCKED, recipeOnly.state().status(), "Recipe-only failed probe did not persist BLOCKED.");
				PhantomAssertions.assertTrue((recipeOnly.state().selectedSource() == null) && (recipeOnly.state().recipePlan() == null), "Recipe-only failed probe recreated a source or recipe plan.");
				PhantomAssertions.assertTrue(recipeOnly.lease().lastInventoryRequest.isEmpty(), "Recipe-only failed probe called the active exact inventory API.");
				PhantomAssertions.assertEquals(before, recipeOnly.lease().inventoryCounts, "Recipe-only failed probe mutated active inventory evidence.");
				PhantomAssertions.assertTrue((recipeOnly.storedGoal().acquisitionMethod() == null) && (recipeOnly.storedGoal().selectedAnchor() == null), "Recipe-only failed probe retained stale Goal method or anchor.");
				PhantomAssertions.assertEquals(PhantomAcquisitionService.DirectiveKind.BLOCKED, recipeOnly.service().directive(recipeOnly.profileId(), recipeOnly.storedGoal(), PhantomActivityState.ACTIVE).kind(), "Recipe-only failed probe left an infinite PLAN directive.");
			}

			try (AcquisitionServiceFixture mixed = acquisitionPlanningFixture(7, Map.of(), fixture.query(), fixture.topology(), Set.of(Method.DEATH_DROP, Method.RECIPE_PREPARATION)))
			{
				final var result = mixed.plan(1);
				PhantomAssertions.assertEquals(PhantomAcquisitionService.OperationStatus.SUCCESS, result.status(), "Mixed failed probe did not continue with the executable source.");
				PhantomAssertions.assertEquals(Method.DEATH_DROP, mixed.state().selectedSource().method(), "Failed recipe probe was not excluded before authoritative death-source planning.");
				PhantomAssertions.assertTrue(mixed.state().recipePlan() == null, "Mixed failed probe recreated a recipe plan.");
				PhantomAssertions.assertTrue(mixed.lease().lastInventoryRequest.isEmpty(), "Mixed failed probe called the active exact inventory API.");
				PhantomAssertions.assertEquals(Method.DEATH_DROP.key(), mixed.storedGoal().acquisitionMethod(), "Mixed failed probe did not project the executable method to Goal state.");
				PhantomAssertions.assertTrue(mixed.storedGoal().selectedAnchor() != null, "Mixed failed probe did not project the authoritative death-source anchor.");
			}
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
		PhantomAssertions.assertTrue(PhantomAcquisitionState.class.getDeclaredFields().length <= 33, "Acquisition state accumulated unbounded mutable infrastructure.");
		context.record("acquisition.maximumSwitches", PhantomAcquisitionState.MAX_SWITCHES);
	}

	private void testDispatchRecovery(PhantomTestContext context) throws Exception
	{
		for (AcquisitionSkillKind kind : AcquisitionSkillKind.values())
		{
			final Phase prepared = kind == AcquisitionSkillKind.SPOIL ? Phase.SPOIL_PREPARED : Phase.SWEEP_PREPARED;
			try (AcquisitionServiceFixture fixture = acquisitionServiceFixture(Method.SPOIL_SWEEP, prepared, 0))
			{
				fixture.lease().castOutcome = ActionOutcome.UNAVAILABLE;
				for (int attempt = 1; attempt <= 2; attempt++)
				{
					PhantomAssertions.assertEquals(PhantomAcquisitionService.OperationStatus.RETRY, fixture.advance(attempt).status(), kind + " UNAVAILABLE did not remain bounded-retryable at attempt " + attempt);
					PhantomAssertions.assertEquals(attempt, fixture.state().phaseAttempt(), kind + " attempt was not persisted exactly.");
					PhantomAssertions.assertEquals(prepared, fixture.state().phase(), kind + " did not atomically return to PREPARED.");
				}
				PhantomAssertions.assertEquals(PhantomAcquisitionService.OperationStatus.REPLAN, fixture.advance(3).status(), kind + " third UNAVAILABLE did not exhaust the source.");
				PhantomAssertions.assertEquals(Status.BLOCKED, fixture.state().status(), kind + " exhausted dispatch did not expose source switching.");
				PhantomAssertions.assertEquals(PhantomAcquisitionService.DirectiveKind.SWITCH, fixture.service().directive(fixture.profileId(), fixture.goal(), PhantomActivityState.ACTIVE).kind(), kind + " source switching was not available after the bounded limit.");
				PhantomAssertions.assertEquals(0, fixture.service().snapshot().externalClaims(), kind + " exhausted dispatch retained external ownership.");
			}
		}

		for (AcquisitionSkillKind kind : AcquisitionSkillKind.values())
		{
			final Phase prepared = kind == AcquisitionSkillKind.SPOIL ? Phase.SPOIL_PREPARED : Phase.SWEEP_PREPARED;
			try (AcquisitionServiceFixture fixture = acquisitionServiceFixture(Method.SPOIL_SWEEP, prepared, 0))
			{
				fixture.lease().castOutcome = ActionOutcome.REJECTED;
				long sequence = 1;
				for (int failure = 1; failure <= 3; failure++)
				{
					PhantomAssertions.assertEquals(PhantomAcquisitionService.OperationStatus.REPLAN, fixture.advance(sequence++).status(), kind + " REJECTED dispatch was not typed as a replan.");
					PhantomAssertions.assertEquals(failure, fixture.state().candidates().getFirst().failures(), kind + " REJECTED did not persist exactly one source failure.");
					PhantomAssertions.assertEquals("source.ineligible", fixture.state().candidates().getFirst().lastFailureReason(), kind + " REJECTED reason did not come from the exact repeat check.");
					PhantomAssertions.assertEquals(0, fixture.service().snapshot().externalClaims(), kind + " REJECTED retained external ownership.");
					if (failure < 3)
					{
						PhantomAssertions.assertEquals(Phase.TARGET_REQUIRED, fixture.state().phase(), kind + " REJECTED remained in a dispatch phase.");
						if (kind == AcquisitionSkillKind.SWEEP)
						{
							fixture.forcePhase(Phase.SWEEP_PREPARED);
						}
						else
						{
							PhantomAssertions.assertEquals(PhantomAcquisitionService.OperationStatus.SUCCESS, fixture.advance(sequence++).status(), kind + " target was not reacquired after a bounded rejection.");
						}
					}
				}
				PhantomAssertions.assertEquals(Status.BLOCKED, fixture.state().status(), kind + " third REJECTED did not expose source switching.");
				PhantomAssertions.assertEquals(PhantomAcquisitionService.DirectiveKind.SWITCH, fixture.service().directive(fixture.profileId(), fixture.goal(), PhantomActivityState.ACTIVE).kind(), kind + " bounded REJECTED did not select SWITCH.");
				final int casts = fixture.lease().castCalls;
				PhantomAssertions.assertEquals(PhantomAcquisitionService.OperationStatus.REPLAN, fixture.advance(sequence).status(), kind + " blocked state did not remain non-executable.");
				PhantomAssertions.assertEquals(casts, fixture.lease().castCalls, kind + " cast repeated after the rejection threshold.");
			}
		}

		try (AcquisitionServiceFixture fixture = acquisitionServiceFixture(Method.SPOIL_SWEEP, Phase.SPOIL_PREPARED, 0, 2, 5, 9, 10))
		{
			fixture.lease().castOutcome = ActionOutcome.REJECTED;
			fixture.lease().invalidateTargetOnRejectedCast = true;
			PhantomAssertions.assertEquals(PhantomAcquisitionService.OperationStatus.REPLAN, fixture.advance(1).status(), "Stale-target REJECTED was not bounded.");
			PhantomAssertions.assertEquals("source.target_unavailable", fixture.state().candidates().getFirst().lastFailureReason(), "Stale-target rejection lost its exact reason.");
			fixture.lease().target = fixture.lease().liveTarget(201);
			PhantomAssertions.assertEquals(PhantomAcquisitionService.OperationStatus.SUCCESS, fixture.advance(2).status(), "A new target was not selected after stale-target rejection.");
			PhantomAssertions.assertEquals(201, fixture.state().targetObjectId(), "Stale target identity was reused.");
			PhantomAssertions.assertEquals(5L, fixture.state().baselineCount(), "Target recovery changed the acquisition baseline.");
			PhantomAssertions.assertEquals(4L, fixture.state().progress(), "Target recovery changed baseline-derived progress.");
		}

		try (AcquisitionServiceFixture fixture = acquisitionServiceFixture(Method.SPOIL_SWEEP, Phase.SPOIL_DISPATCHING, 0))
		{
			fixture.lease().actor = fixture.lease().castingActor(254, 11);
			PhantomAssertions.assertEquals(PhantomAcquisitionService.OperationStatus.RETRY, fixture.advance(1).status(), "Restart during exact Spoil cast was not observed.");
			PhantomAssertions.assertEquals(Phase.SPOIL_DISPATCHING, fixture.state().phase(), "Exact active cast was blindly redispatched.");
			PhantomAssertions.assertEquals(0, fixture.state().phaseAttempt(), "Exact active cast consumed a verification attempt.");
		}

		try (AcquisitionServiceFixture fixture = acquisitionServiceFixture(Method.SPOIL_SWEEP, Phase.SPOIL_DISPATCHING, 0))
		{
			fixture.lease().target = fixture.lease().spoilObservedTarget();
			PhantomAssertions.assertEquals(PhantomAcquisitionService.OperationStatus.SUCCESS, fixture.advance(1).status(), "Observed Spoil effect was not recovered after restart.");
			PhantomAssertions.assertEquals(Phase.SPOIL_OBSERVED, fixture.state().phase(), "Observed Spoil effect did not advance canonically.");
		}

		try (AcquisitionServiceFixture fixture = acquisitionServiceFixture(Method.SPOIL_SWEEP, Phase.SWEEP_DISPATCHING, 0))
		{
			fixture.lease().inventoryCount = 1;
			PhantomAssertions.assertEquals(PhantomAcquisitionService.OperationStatus.SUCCESS, fixture.advance(1).status(), "Observed Sweep inventory delta was not recovered after restart.");
			PhantomAssertions.assertEquals(Phase.VERIFYING, fixture.state().phase(), "Observed Sweep delta did not advance to verification.");
		}

		try (AcquisitionServiceFixture fixture = acquisitionServiceFixture(Method.SPOIL_SWEEP, Phase.SPOIL_DISPATCHING, 0))
		{
			PhantomAssertions.assertEquals(PhantomAcquisitionService.OperationStatus.RETRY, fixture.advance(1).status(), "Missing restart evidence did not consume one bounded attempt.");
			PhantomAssertions.assertEquals(1, fixture.state().phaseAttempt(), "Missing restart evidence attempt was not durable.");
			PhantomAssertions.assertEquals(Phase.SPOIL_PREPARED, fixture.state().phase(), "Missing restart evidence did not return to PREPARED.");
		}

		try (AcquisitionServiceFixture fixture = acquisitionServiceFixture(Method.SPOIL_SWEEP, Phase.SWEEP_DISPATCHING, 2))
		{
			PhantomAssertions.assertEquals(PhantomAcquisitionService.OperationStatus.REPLAN, fixture.advance(1).status(), "Terminal recovery uncertainty was not fail-closed.");
			PhantomAssertions.assertEquals(Status.BLOCKED, fixture.state().status(), "Terminal recovery uncertainty did not persist BLOCKED truth.");
			PhantomAssertions.assertEquals(0, fixture.service().snapshot().externalClaims(), "Terminal recovery uncertainty retained external ownership.");
		}
		context.record("acquisition.dispatchVerificationAttempts", catalog(context).limits().verificationAttempts());
	}

	private void testCombatReconciliation(PhantomTestContext context) throws Exception
	{
		for (Method method : List.of(Method.DEATH_DROP, Method.SPOIL_SWEEP))
		{
			try (AcquisitionServiceFixture fixture = acquisitionServiceFixture(method, Phase.COMBAT_PREPARED, 0))
			{
				PhantomAssertions.assertEquals(PhantomAcquisitionService.OperationStatus.SUCCESS, fixture.advance(1).status(), method + " Combat submission failed.");
				PhantomAssertions.assertEquals(Phase.COMBAT_SUBMITTED, fixture.state().phase(), method + " was marked submitted without an exact session.");
				PhantomAssertions.assertTrue(fixture.combat().matchesAcquisitionSession(fixture.profileId(), 200, fixture.combatOwner()), method + " session lost exact Goal/source/target ownership.");
			}
		}

		try (AcquisitionServiceFixture fixture = acquisitionServiceFixture(Method.DEATH_DROP, Phase.COMBAT_PREPARED, 0))
		{
			PhantomAssertions.assertTrue(fixture.combat().startAcquisitionSession(fixture.request(200), fixture.combatOwner()).accepted(), "Crash-window exact Combat session was not established.");
			final int acquisitions = fixture.backend().acquireCalls;
			PhantomAssertions.assertEquals(PhantomAcquisitionService.OperationStatus.SUCCESS, fixture.advance(1).status(), "Crash-window Combat session was not reconciled.");
			PhantomAssertions.assertEquals(acquisitions, fixture.backend().acquireCalls, "Crash-window reconciliation started Combat twice.");
			PhantomAssertions.assertEquals(Phase.COMBAT_SUBMITTED, fixture.state().phase(), "Crash-window reconciliation did not persist COMBAT_SUBMITTED.");
		}

		try (AcquisitionServiceFixture fixture = acquisitionServiceFixture(Method.DEATH_DROP, Phase.COMBAT_PREPARED, 0))
		{
			PhantomAssertions.assertTrue(fixture.combat().startSession(fixture.request(200)).accepted(), "Foreign Combat fixture was not established.");
			final PhantomAcquisitionService.OperationResult result = fixture.advance(1);
			PhantomAssertions.assertEquals(PhantomAcquisitionService.OperationStatus.REPLAN, result.status(), "Foreign Combat session was inherited by acquisition.");
			PhantomAssertions.assertEquals("acquisition.combat.foreign_session", result.reasonKey(), "Foreign Combat session did not retain its typed reason.");
			PhantomAssertions.assertEquals(Phase.COMBAT_PREPARED, fixture.state().phase(), "Foreign Combat session falsely advanced state.");
		}

		try (AcquisitionServiceFixture fixture = acquisitionServiceFixture(Method.DEATH_DROP, Phase.COMBAT_PREPARED, 0))
		{
			PhantomAssertions.assertTrue(fixture.combat().startAcquisitionSession(fixture.request(201), fixture.combatOwner()).accepted(), "Target-mismatch Combat fixture was not established.");
			PhantomAssertions.assertEquals(PhantomAcquisitionService.OperationStatus.REPLAN, fixture.advance(1).status(), "Target-mismatch session was inherited.");
			PhantomAssertions.assertEquals(Phase.COMBAT_PREPARED, fixture.state().phase(), "Target mismatch falsely advanced state.");
		}

		try (AcquisitionServiceFixture fixture = acquisitionServiceFixture(Method.DEATH_DROP, Phase.COMBAT_PREPARED, 0, 1))
		{
			PhantomAssertions.assertTrue(fixture.combat().startSession(new PhantomCombatRequest(999, 200, PhantomCombatMode.MELEE_PHYSICAL, true, false, 30_000, () -> false)).accepted(), "Capacity fixture did not occupy Combat.");
			PhantomAssertions.assertEquals(PhantomAcquisitionService.OperationStatus.RETRY, fixture.advance(1).status(), "Temporary Combat capacity did not remain retryable.");
			PhantomAssertions.assertEquals(Phase.COMBAT_PREPARED, fixture.state().phase(), "Temporary Combat capacity falsely marked submission.");
		}

		try (AcquisitionServiceFixture fixture = acquisitionServiceFixture(Method.DEATH_DROP, Phase.COMBAT_SUBMITTED, 0))
		{
			PhantomAssertions.assertTrue(fixture.combat().startSession(fixture.request(200)).accepted(), "Submitted foreign-owner fixture was not established.");
			final var result = fixture.advance(1);
			PhantomAssertions.assertEquals("acquisition.combat.foreign_session", result.reasonKey(), "Submitted foreign owner was not rejected before observation.");
			PhantomAssertions.assertEquals(Phase.COMBAT_SUBMITTED, fixture.state().phase(), "Foreign submitted session mutated acquisition state.");
			PhantomAssertions.assertTrue(fixture.combat().find(fixture.profileId()).isPresent(), "Foreign submitted session was consumed.");
		}

		try (AcquisitionServiceFixture fixture = acquisitionServiceFixture(Method.DEATH_DROP, Phase.COMBAT_SUBMITTED, 0))
		{
			PhantomAssertions.assertTrue(fixture.combat().startAcquisitionSession(fixture.request(201), fixture.combatOwner()).accepted(), "Submitted foreign-target fixture was not established.");
			final var result = fixture.advance(1);
			PhantomAssertions.assertEquals("acquisition.combat.foreign_session", result.reasonKey(), "Submitted foreign target was not rejected before observation.");
			PhantomAssertions.assertEquals(Phase.COMBAT_SUBMITTED, fixture.state().phase(), "Foreign submitted target mutated acquisition state.");
			PhantomAssertions.assertTrue(fixture.combat().find(fixture.profileId()).isPresent(), "Foreign submitted target was consumed.");
		}

		try (AcquisitionServiceFixture fixture = acquisitionServiceFixture(Method.DEATH_DROP, Phase.COMBAT_SUBMITTED, 0))
		{
			PhantomAssertions.assertEquals(PhantomAcquisitionService.OperationStatus.SUCCESS, fixture.advance(1).status(), "Missing exact session did not recover a live target.");
			PhantomAssertions.assertEquals(Phase.COMBAT_PREPARED, fixture.state().phase(), "Live target recovery was not durable COMBAT_PREPARED.");
		}

		try (AcquisitionServiceFixture fixture = acquisitionServiceFixture(Method.SPOIL_SWEEP, Phase.COMBAT_SUBMITTED, 0))
		{
			fixture.lease().target = fixture.lease().sweepTarget();
			PhantomAssertions.assertEquals(PhantomAcquisitionService.OperationStatus.SUCCESS, fixture.advance(1).status(), "Missing exact session did not recover an owned spoiled corpse.");
			PhantomAssertions.assertEquals(Phase.COMBAT_TERMINAL, fixture.state().phase(), "Owned spoiled corpse was not durable COMBAT_TERMINAL.");
		}

		try (AcquisitionServiceFixture fixture = acquisitionServiceFixture(Method.DEATH_DROP, Phase.COMBAT_SUBMITTED, 0))
		{
			fixture.lease().inventoryCount = 1;
			PhantomAssertions.assertEquals(PhantomAcquisitionService.OperationStatus.SUCCESS, fixture.advance(1).status(), "Missing exact session ignored authoritative inventory growth.");
			PhantomAssertions.assertEquals(Phase.VERIFYING, fixture.state().phase(), "Inventory growth did not recover to VERIFYING.");
		}

		try (AcquisitionServiceFixture fixture = acquisitionServiceFixture(Method.DEATH_DROP, Phase.COMBAT_SUBMITTED, 0))
		{
			fixture.lease().target = new AcquisitionTargetSnapshot(200, 101, 0, 10, false, false, true, true, false, true, true, false, true, false, 0, false, false);
			PhantomAssertions.assertEquals(PhantomAcquisitionService.OperationStatus.RETRY, fixture.advance(1).status(), "Reused exact object identity was admitted as the acquisition target.");
			PhantomAssertions.assertEquals(1, fixture.state().phaseAttempt(), "Reused target identity did not consume one bounded evidence attempt.");
		}

		try (AcquisitionServiceFixture fixture = acquisitionServiceFixture(Method.DEATH_DROP, Phase.COMBAT_SUBMITTED, 0))
		{
			fixture.lease().target = null;
			for (int attempt = 1; attempt <= 2; attempt++)
			{
				PhantomAssertions.assertEquals(PhantomAcquisitionService.OperationStatus.RETRY, fixture.advance(attempt).status(), "Missing Combat evidence did not use a bounded retry.");
				PhantomAssertions.assertEquals(attempt, fixture.state().phaseAttempt(), "Missing Combat attempt was not durable.");
			}
			PhantomAssertions.assertEquals(PhantomAcquisitionService.OperationStatus.REPLAN, fixture.advance(3).status(), "Missing Combat evidence did not fail closed at the verification limit.");
			PhantomAssertions.assertEquals(Status.BLOCKED, fixture.state().status(), "Missing Combat uncertainty did not persist BLOCKED.");
			PhantomAssertions.assertEquals(TerminalResult.UNCERTAIN, fixture.state().receipts().getLast().result(), "Missing Combat recovery did not persist an UNCERTAIN receipt.");
			PhantomAssertions.assertEquals(0, fixture.service().snapshot().externalClaims(), "Missing Combat terminal recovery retained external ownership.");
		}
		context.record("acquisition.combatKillOwners", 1);
	}

	private AcquisitionServiceFixture acquisitionServiceFixture(Method method, Phase phase, int phaseAttempt) throws Exception
	{
		return acquisitionServiceFixture(method, phase, phaseAttempt, 2, 0, 0, 1);
	}

	private AcquisitionServiceFixture acquisitionServiceFixture(Method method, Phase phase, int phaseAttempt, int maximumCombatSessions) throws Exception
	{
		return acquisitionServiceFixture(method, phase, phaseAttempt, maximumCombatSessions, 0, 0, 1);
	}

	private AcquisitionServiceFixture acquisitionServiceFixture(Method method, Phase phase, int phaseAttempt, int maximumCombatSessions, long baseline, long lastObserved, long required) throws Exception
	{
		final PhantomProfileRepository profiles = PhantomProfileRepository.open();
		final PhantomProfile profile = profiles.create(_environment.primary().objectId());
		PhantomCombatService combat = null;
		PhantomAcquisitionService service = null;
		try
		{
			final PhantomAcquisitionCatalog catalog = PhantomAcquisitionCatalog.load(Path.of("data/phantoms/acquisition/high-five-acquisition-v1.xml"));
			final PhantomGoalStateStore goals = new PhantomGoalStateStore(profiles);
			final String anchorId = _production.topology().snapshot().anchors().getFirst().id();
			final PhantomGoal goal = new PhantomGoal(21, PhantomAcquisitionGoalSpec.GOAL_TYPE, PhantomGoalStatus.ACTIVE, new PhantomDomainRef("profile", "self"), new PhantomDomainRef("item", "57"), required, PhantomAcquisitionState.observedProgress(baseline, lastObserved, required), method.key(), List.of(new PhantomDomainRef(PhantomAcquisitionGoalSpec.SOURCE_NAMESPACE, method.key())), new PhantomDomainRef(PhantomAcquisitionGoalSpec.ANCHOR_NAMESPACE, anchorId), PhantomAcquisitionGoalSpec.PURPOSE_KEY, 500, 0, 0, 0, Map.of(PhantomAcquisitionGoalSpec.BASELINE_CONSTRAINT, baseline, PhantomAcquisitionGoalSpec.MAXIMUM_SWITCHES_CONSTRAINT, 4L), "acquisition.safety.test", 0);
			goals.insert(profile.profileId(), goal);
			final PhantomBackgroundService background = new PhantomBackgroundService(profiles, goals, PhantomIdentityLeaseRegistry.getInstance(), new PhantomBackgroundTransaction(), _production.authority(), new PhantomBackgroundCompetitionRegistry(), noSignals(), () -> null);
			final var backgroundHashes = background.authorityHashes();
			final Hashes hashes = new Hashes(catalog.hash(), _production.knowledge().snapshot().combinedHash(), _production.topology().snapshot().canonicalHash(), _production.progression().combinedHash().toLowerCase(java.util.Locale.ROOT), canonicalDigest(backgroundHashes.knowledge(), backgroundHashes.topology(), backgroundHashes.progression(), backgroundHashes.commerce()));
			final Source source = method == Method.SPOIL_SWEEP ? new Source("2".repeat(64), method, 100, 57, "test:spoil:57", "test.node", anchorId, 0, 254, 11, 42, 1) : new Source("1".repeat(64), method, 100, 57, "test:death-drop:57", "test.node", anchorId, 0, 0, 0, 0, 0);
			final Candidate candidate = new Candidate(source.sourceId(), source.method(), 1, 0, 0, "");
			final PhantomAcquisitionState state = new PhantomAcquisitionState(hashes, goal.goalId(), goal.revision(), 57, required, baseline, lastObserved, PhantomAcquisitionState.observedProgress(baseline, lastObserved, required), Status.READY, source, List.of(candidate), 0, 0, phase, 200, 100, 0, null, List.of(), phaseAttempt, 0);
			final PhantomAcquisitionStore store = new PhantomAcquisitionStore(profiles, goals);
			store.insert(profile.profileId(), state);
			final FakeAcquisitionLease lease = new FakeAcquisitionLease();
			if ((phase == Phase.SWEEP_PREPARED) || (phase == Phase.SWEEP_DISPATCHING))
			{
				lease.target = lease.sweepTarget();
			}
			final FakeAcquisitionBackend backend = new FakeAcquisitionBackend(lease);
			final HoldingDispatcher dispatcher = new HoldingDispatcher();
			final PhantomCombatCapabilityResolver resolver = new PhantomCombatCapabilityResolver(_ -> List.of(new CapabilityEvidence(PhantomCombatMode.MELEE_PHYSICAL.capabilityKey(), "acquisition-test", 1, List.of())));
			combat = new PhantomCombatService(backend, resolver, PhantomCombatPolicy.productionDefaults(maximumCombatSessions), new PhantomCombatMetrics(), () -> 1, dispatcher);
			combat.start();
			service = new PhantomAcquisitionService(catalog, store, goals, new PhantomAcquisitionSourcePlanner(catalog, _production.knowledge(), _production.topology(), _production.progression()), _production.knowledge(), _production.topology(), _production.progression(), combat, background, new PhantomNavigationService(new PhantomMetrics()));
			PhantomAssertions.assertTrue(service.start(), "Acquisition safety fixture service did not start.");
			return new AcquisitionServiceFixture(profiles, profile, goal, goals, store, service, combat, backend, lease);
		}
		catch (Throwable failure)
		{
			if (service != null)
			{
				service.beginStop();
				service.finishStop();
			}
			if (combat != null)
			{
				combat.beginStop();
				combat.finishStop();
			}
			profiles.find(profile.profileId()).ifPresent(current -> profiles.delete(current.profileId(), current.rowVersion()));
			throw failure;
		}
	}

	private AcquisitionServiceFixture acquisitionPlanningFixture(RecipeFact recipe, Map<Integer, Long> inventory) throws Exception
	{
		return acquisitionPlanningFixture(recipe.productItemId(), inventory, _production.knowledge(), _production.topology(), Set.of(Method.RECIPE_PREPARATION));
	}

	private AcquisitionServiceFixture acquisitionPlanningFixture(int itemId, Map<Integer, Long> inventory, PhantomGameKnowledgeQuery knowledge, PhantomTopologyQuery topology, Set<Method> allowedMethods) throws Exception
	{
		final PhantomProfileRepository profiles = PhantomProfileRepository.open();
		final PhantomProfile profile = profiles.create(_environment.primary().objectId());
		PhantomCombatService combat = null;
		PhantomAcquisitionService service = null;
		try
		{
			final PhantomAcquisitionCatalog catalog = PhantomAcquisitionCatalog.load(Path.of("data/phantoms/acquisition/high-five-acquisition-v1.xml"));
			final PhantomGoalStateStore goals = new PhantomGoalStateStore(profiles);
			final String anchorId = topology.snapshot().anchors().getFirst().id();
			final List<Method> orderedMethods = allowedMethods.stream().sorted(Comparator.comparing(Method::key)).toList();
			final Method initialMethod = allowedMethods.contains(Method.RECIPE_PREPARATION) ? Method.RECIPE_PREPARATION : orderedMethods.getFirst();
			final List<PhantomDomainRef> validSources = orderedMethods.stream().map(method -> new PhantomDomainRef(PhantomAcquisitionGoalSpec.SOURCE_NAMESPACE, method.key())).toList();
			final PhantomGoal goal = new PhantomGoal(21, PhantomAcquisitionGoalSpec.GOAL_TYPE, PhantomGoalStatus.ACTIVE, new PhantomDomainRef("profile", "self"), new PhantomDomainRef("item", Integer.toString(itemId)), 1, 0, initialMethod.key(), validSources, new PhantomDomainRef(PhantomAcquisitionGoalSpec.ANCHOR_NAMESPACE, anchorId), PhantomAcquisitionGoalSpec.PURPOSE_KEY, 500, 0, 0, 0, Map.of(PhantomAcquisitionGoalSpec.BASELINE_CONSTRAINT, 0L, PhantomAcquisitionGoalSpec.MAXIMUM_SWITCHES_CONSTRAINT, 4L), "acquisition.recipe.inventory.test", 0);
			goals.insert(profile.profileId(), goal);
			final PhantomBackgroundService background = new PhantomBackgroundService(profiles, goals, PhantomIdentityLeaseRegistry.getInstance(), new PhantomBackgroundTransaction(), _production.authority(), new PhantomBackgroundCompetitionRegistry(), noSignals(), () -> null);
			final FakeAcquisitionLease lease = new FakeAcquisitionLease();
			lease.actor = lease.actorForClass(118);
			lease.inventoryCounts.putAll(inventory);
			final FakeAcquisitionBackend backend = new FakeAcquisitionBackend(lease);
			combat = new PhantomCombatService(backend, new PhantomCombatCapabilityResolver(_ -> List.of(new CapabilityEvidence(PhantomCombatMode.MELEE_PHYSICAL.capabilityKey(), "acquisition-test", 1, List.of()))), PhantomCombatPolicy.productionDefaults(2), new PhantomCombatMetrics(), () -> 1, new HoldingDispatcher());
			combat.start();
			final PhantomAcquisitionStore store = new PhantomAcquisitionStore(profiles, goals);
			service = new PhantomAcquisitionService(catalog, store, goals, new PhantomAcquisitionSourcePlanner(catalog, knowledge, topology, _production.progression()), knowledge, topology, _production.progression(), combat, background, new PhantomNavigationService(new PhantomMetrics()));
			PhantomAssertions.assertTrue(service.start(), "Acquisition recipe inventory fixture service did not start.");
			return new AcquisitionServiceFixture(profiles, profile, goal, goals, store, service, combat, backend, lease);
		}
		catch (Throwable failure)
		{
			if (service != null)
			{
				service.beginStop();
				service.finishStop();
			}
			if (combat != null)
			{
				combat.beginStop();
				combat.finishStop();
			}
			profiles.find(profile.profileId()).ifPresent(current -> profiles.delete(current.profileId(), current.rowVersion()));
			throw failure;
		}
	}

	private static PhantomRelevanceSignalPort noSignals()
	{
		return new PhantomRelevanceSignalPort()
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
	}

	private static String canonicalDigest(Object... values)
	{
		try
		{
			final MessageDigest digest = MessageDigest.getInstance("SHA-256");
			for (Object value : values)
			{
				digest.update(String.valueOf(value).getBytes(StandardCharsets.UTF_8));
				digest.update((byte) 0);
			}
			return HexFormat.of().formatHex(digest.digest());
		}
		catch (Exception exception)
		{
			throw new IllegalStateException(exception);
		}
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
					final List<NpcFact> npcs = base.npcs().stream().map(npc -> ((npc.npcId() == 102) || (npc.npcId() == 103)) ? new NpcFact(npc.npcId(), 20, npc.kind(), npc.attackable(), npc.targetable(), npc.canBeSown(), npc.exp(), npc.sp(), npc.authority()) : npc).toList();
					final List<SpawnFact> spawns = base.spawns().stream().map(spawn -> ((spawn.npcId() == 102) || (spawn.npcId() == 103)) ? new SpawnFact(spawn.npcId(), spawn.spawnOrdinal(), spawn.instanceId(), spawn.x(), spawn.y(), spawn.z(), 1, spawn.locationId(), spawn.pointKind(), spawn.topologyNodeId(), spawn.mapRegionLocId(), spawn.authority()) : spawn).toList();
					final ArrayList<DropFact> drops = new ArrayList<>(base.drops().stream().filter(fact -> (fact.itemId() != 1) || ((fact.npcId() != 102) && (fact.npcId() != 103))).toList());
					drops.add(new DropFact(102, 1, DropSourceKind.DEATH_DROP, ChanceModel.UNGROUPED_INDEPENDENT, -1, 0, 0, 10, 1, 1, PhantomGameKnowledgeAuthority.SERVER_LOADER_FACT));
					drops.add(new DropFact(103, 1, DropSourceKind.DEATH_DROP, ChanceModel.UNGROUPED_INDEPENDENT, -1, 0, 0, 10, 1, 1, PhantomGameKnowledgeAuthority.SERVER_LOADER_FACT));
					drops.add(new DropFact(102, 1, DropSourceKind.SPOIL, ChanceModel.UNGROUPED_INDEPENDENT, -1, 0, 0, 10, 1, 1, PhantomGameKnowledgeAuthority.SERVER_LOADER_FACT));
					drops.add(new DropFact(103, 1, DropSourceKind.SPOIL, ChanceModel.UNGROUPED_INDEPENDENT, -1, 0, 0, 10, 1, 1, PhantomGameKnowledgeAuthority.SERVER_LOADER_FACT));
					return new BackendData(base.items(), npcs, drops, spawns, base.recipes(), base.classes(), base.completeClassSkills());
				}

				@Override
				public boolean sourceExists(String relativeDatapackPath)
				{
					return delegate.sourceExists(relativeDatapackPath);
				}
			};
			final PhantomTopologyCoreSuite.TestBackend topologyBackend = new PhantomTopologyCoreSuite.TestBackend();
			topologyBackend._npcs.put(102, new PhantomTopologyValidationBackend.NpcFact(102, "Monster", true));
			topologyBackend._npcs.put(103, new PhantomTopologyValidationBackend.NpcFact(103, "Monster", true));
			topologyBackend._spawns.put(102, List.of(new PhantomTopologyValidationBackend.SpawnFact(102, new PhantomTopologyPoint(150, 150, 0, 0))));
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
			return new SyntheticSource(service, topology, new PlannedSource(1, planner, request, result));
		}
		catch (Exception exception)
		{
			throw new AssertionError("Could not build synthetic acquisition alternatives.", exception);
		}
	}

	private PhantomAcquisitionSourcePlanner.Request request(Method method, int itemId, Map<String, Candidate> previous, long minute)
	{
		final int classId = method == Method.SPOIL_SWEEP ? 117 : 88;
		final Map<Integer, Integer> skills = method == Method.SPOIL_SWEEP ? Map.of(254, 11, 42, 1) : Map.of();
		return new PhantomAcquisitionSourcePlanner.Request(1, itemId, 1, PhantomActivityState.BACKGROUND, classId, 85, Map.of(), skills, Set.of(method), method, "", previous, minute);
	}

	private static PhantomAcquisitionSourcePlanner.Request withPrevious(PhantomAcquisitionSourcePlanner.Request source, Map<String, Candidate> previous, long minute)
	{
		return new PhantomAcquisitionSourcePlanner.Request(source.profileId(), source.itemId(), source.remainingAmount(), source.activityState(), source.classId(), source.level(), source.inventory(), source.knownSkills(), source.allowedMethods(), source.preferredMethod(), source.currentAnchorId(), source.currentSourceId(), source.resources(), previous, minute);
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
				final ArrayList<DropFact> drops = new ArrayList<>(base.drops());
				final ArrayList<RecipeFact> recipes = new ArrayList<>();
				final int highest = shape == RecipeShape.ALTERNATIVE_UNION ? 180 : (shape == RecipeShape.NODES ? 70 : (shape == RecipeShape.DEFICITS ? 50 : (shape == RecipeShape.DEPTH ? 20 : 10)));
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
					case ALTERNATIVE_UNION ->
					{
						for (int alternative = 0; alternative < 4; alternative++)
						{
							final int intermediate = 11 + (alternative * 33);
							recipes.add(recipe(10 + alternative, 7, List.of(new IngredientFact(intermediate, 1))));
							recipes.add(recipe(100 + alternative, intermediate, java.util.stream.IntStream.rangeClosed(intermediate + 1, intermediate + 32).mapToObj(item -> new IngredientFact(item, 1)).toList()));
						}
						drops.add(new DropFact(102, 7, DropSourceKind.DEATH_DROP, ChanceModel.UNGROUPED_INDEPENDENT, -1, 0, 0, 10, 1, 1, PhantomGameKnowledgeAuthority.SERVER_LOADER_FACT));
					}
					case ALTERNATIVES ->
					{
						recipes.add(recipe(10, 7, List.of(new IngredientFact(1, 2), new IngredientFact(2, 1))));
						recipes.add(recipe(11, 7, List.of(new IngredientFact(1, 1), new IngredientFact(2, 2))));
					}
				}
				return new BackendData(items, base.npcs(), drops, base.spawns(), recipes, base.classes(), base.completeClassSkills());
			}

			@Override
			public boolean sourceExists(String relativeDatapackPath)
			{
				return delegate.sourceExists(relativeDatapackPath);
			}
		};
		final PhantomGameKnowledgePolicy policy = PhantomGameKnowledgePolicy.productionDefaults();
		final PhantomTopologyCoreSuite.TestBackend topologyBackend = new PhantomTopologyCoreSuite.TestBackend();
		topologyBackend._npcs.put(102, new PhantomTopologyValidationBackend.NpcFact(102, "Monster", true));
		topologyBackend._spawns.put(102, List.of(new PhantomTopologyValidationBackend.SpawnFact(102, new PhantomTopologyPoint(100, 100, 0, 0))));
		final PhantomTopologyNode node = new PhantomTopologyNode("synthetic.area", PhantomTopologyNodeKind.FARMING_AREA, 0, PhantomTopologyArea.cuboid(0, 0, 1000, 0, 1000, -100, 100), null, List.of(), List.of());
		final PhantomTopologyAnchor anchor = new PhantomTopologyAnchor("synthetic.anchor", PhantomTopologyAnchorRole.ROUTE, node.id(), new PhantomTopologyPoint(100, 100, 0, 0), null, null, 0, List.of(), List.of());
		final PhantomTopologySnapshot snapshot = PhantomTopologySnapshot.create(1, "synthetic-recipes", 1, 1, List.of(node), List.of(anchor), List.of(), topologyBackend, PhantomTopologyPolicy.productionDefaults());
		final PhantomTopologyQuery topology = new PhantomTopologyQuery(snapshot, topologyBackend, new PhantomTopologyMetrics());
		final PhantomGameKnowledgeService service = new PhantomGameKnowledgeService(new PhantomGameKnowledgeBuilder(backend, new PhantomStaticManorParser(root.resolve("Seeds.xml"), policy), new PhantomCuratedKnowledgeParser(root.resolve("curated"), backend, policy), topology, policy));
		PhantomAssertions.assertTrue(service.start(), "Synthetic recipe knowledge did not start.");
		return new SyntheticKnowledge(service, topology);
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
		final Status status = method == Method.RECIPE_PREPARATION ? Status.PLANNING_ONLY : Status.READY;
		final Phase phase = status == Status.READY ? Phase.TARGET_REQUIRED : Phase.NONE;
		return new PhantomAcquisitionState(HASHES, 1, 0, 57, 10, 5, 5, 0, status, source, List.of(candidate), 0, 0, phase, 0, 0, 0, recipe, binding(method), List.of(), 0, 1);
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
		final boolean manor = Set.of(Phase.SOW_PREPARED, Phase.SOW_DISPATCHING, Phase.SOW_OBSERVED, Phase.HARVEST_PREPARED, Phase.HARVEST_DISPATCHING).contains(phase);
		final boolean quest = Set.of(Phase.QUEST_COMBAT_PREPARED, Phase.QUEST_COMBAT_SUBMITTED, Phase.QUEST_COMBAT_TERMINAL, Phase.QUEST_CALLBACK_WAIT).contains(phase);
		final Method method = spoil ? Method.SPOIL_SWEEP : manor ? Method.MANOR_CROP : quest ? Method.QUEST_COLLECTION : Method.DEATH_DROP;
		final Source source = source(method, 0);
		final Candidate candidate = new Candidate(source.sourceId(), source.method(), 100, 0, 0, "");
		final boolean target = !Set.of(Phase.TRAVEL_REQUIRED, Phase.TARGET_REQUIRED).contains(phase);
		return new PhantomAcquisitionState(HASHES, 1, 0, 57, 1, 0, 0, 0, phase == Phase.TARGET_REQUIRED || phase == Phase.TRAVEL_REQUIRED ? Status.READY : Status.ACTIVE, source, List.of(candidate), 0, 0, phase, target ? 1000 : 0, target ? source.npcId() : 0, 0, null, binding(method), List.of(), phase == Phase.SOW_DISPATCHING || phase == Phase.HARVEST_DISPATCHING || phase == Phase.QUEST_CALLBACK_WAIT ? 1 : 0, 1);
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
		final int npcId = method == Method.RECIPE_PREPARATION ? 0 : 100 + variant;
		final String fact = method == Method.RECIPE_PREPARATION ? "recipe:1:57" : method == Method.MANOR_CROP ? "manor:fact" : method == Method.QUEST_COLLECTION ? "quest:rule" : method.key() + ":fact";
		return new Source(hash(method.code() * 100 + variant), method, npcId, 57, fact, method == Method.RECIPE_PREPARATION ? "planning" : "node", method == Method.RECIPE_PREPARATION ? "planning" : "anchor", 0, method == Method.SPOIL_SWEEP ? 254 : 0, method == Method.SPOIL_SWEEP ? 11 : 0, method == Method.SPOIL_SWEEP ? 42 : 0, method == Method.SPOIL_SWEEP ? 1 : 0);
	}

	private static PhantomAcquisitionState.MethodBinding binding(Method method)
	{
		return method == Method.MANOR_CROP ? new ManorBinding(1, 1, 57, 2, 3, 4, 10, false, 100, 100, 0, 0, 1, 5, "1".repeat(64)) : method == Method.QUEST_COLLECTION ? new QuestBinding("rule", "2".repeat(64), 102, "Q00102_SeaOfSporesFever", "3".repeat(64), "STARTED", 2, 57, 9, 100, 5, 0, "4".repeat(64)) : null;
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

	private final class AcquisitionServiceFixture implements AutoCloseable
	{
		private final PhantomProfileRepository _profiles;
		private final PhantomProfile _profile;
		private final PhantomGoal _goal;
		private final PhantomGoalStateStore _goals;
		private final PhantomAcquisitionStore _store;
		private final PhantomAcquisitionService _service;
		private final PhantomCombatService _combat;
		private final FakeAcquisitionBackend _backend;
		private final FakeAcquisitionLease _lease;

		private AcquisitionServiceFixture(PhantomProfileRepository profiles, PhantomProfile profile, PhantomGoal goal, PhantomGoalStateStore goals, PhantomAcquisitionStore store, PhantomAcquisitionService service, PhantomCombatService combat, FakeAcquisitionBackend backend, FakeAcquisitionLease lease)
		{
			_profiles = profiles;
			_profile = profile;
			_goal = goal;
			_goals = goals;
			_store = store;
			_service = service;
			_combat = combat;
			_backend = backend;
			_lease = lease;
		}

		private long profileId()
		{
			return _profile.profileId();
		}

		private PhantomGoal goal()
		{
			return _goal;
		}

		private PhantomGoal storedGoal()
		{
			return _goals.load(profileId()).orElseThrow().goal();
		}

		private PhantomAcquisitionService service()
		{
			return _service;
		}

		private PhantomCombatService combat()
		{
			return _combat;
		}

		private FakeAcquisitionBackend backend()
		{
			return _backend;
		}

		private FakeAcquisitionLease lease()
		{
			return _lease;
		}

		private PhantomAcquisitionState state()
		{
			return _store.load(profileId()).orElseThrow().state();
		}

		private PhantomAcquisitionService.OperationResult advance(long sequence)
		{
			return _service.activeAdvance(profileId(), _goal, PhantomActivityState.ACTIVE, 1, sequence, 1_000_000 + sequence, sequence, () -> false);
		}

		private PhantomAcquisitionService.OperationResult plan(long sequence)
		{
			return _service.plan(profileId(), _goal, PhantomActivityState.ACTIVE, 1_000_000 + sequence, sequence, () -> false);
		}

		private void forcePhase(Phase phase)
		{
			final StoredState stored = _store.load(profileId()).orElseThrow();
			_store.replace(profileId(), stored.rowVersion(), stored.state().withPhase(phase, 200, 100, 0, stored.state().phaseAttempt(), stored.state().logicalMinute() + 1));
		}

		private PhantomCombatRequest request(int targetObjectId)
		{
			return new PhantomCombatRequest(profileId(), targetObjectId, PhantomCombatMode.MELEE_PHYSICAL, true, state().selectedSource().method() == Method.DEATH_DROP, 30_000, () -> false);
		}

		private String combatOwner()
		{
			final PhantomAcquisitionState state = state();
			return "acquisition:" + canonicalDigest(state.goalId(), state.goalRevision(), state.selectedSource().sourceId(), state.targetObjectId()).substring(0, 48);
		}

		@Override
		public void close()
		{
			_service.beginStop();
			PhantomAssertions.assertTrue(_service.finishStop(), "Acquisition safety fixture retained claims.");
			_combat.beginStop();
			PhantomAssertions.assertTrue(_combat.finishStop(), "Combat safety fixture retained claims.");
			_profiles.find(profileId()).ifPresent(current -> _profiles.delete(current.profileId(), current.rowVersion()));
		}
	}

	private static final class FakeAcquisitionBackend implements PhantomCombatBackend
	{
		private final FakeAcquisitionLease _lease;
		private int acquireCalls;

		private FakeAcquisitionBackend(FakeAcquisitionLease lease)
		{
			_lease = lease;
		}

		@Override
		public PhantomCombatActorLease tryAcquireActor(long profileId)
		{
			acquireCalls++;
			return _lease;
		}
	}

	private static final class FakeAcquisitionLease implements PhantomCombatActorLease
	{
		private ActorSnapshot actor = liveActor();
		private AcquisitionTargetSnapshot target = liveAcquisitionTarget();
		private ActionOutcome castOutcome = ActionOutcome.ISSUED;
		private long inventoryCount;
		private int castCalls;
		private boolean invalidateTargetOnRejectedCast;
		private final Map<Integer, Long> inventoryCounts = new HashMap<>();
		private List<Integer> lastInventoryRequest = List.of();

		private static ActorSnapshot liveActor()
		{
			return new ActorSnapshot(10, 117, 0, 100, 100, 100, 100, 50, 100, false, false, false, false, false, 0, "IDLE", 0, 0);
		}

		private ActorSnapshot castingActor(int skillId, int skillLevel)
		{
			return new ActorSnapshot(10, 117, 0, 100, 100, 100, 100, 50, 100, false, false, false, true, false, 200, "CAST", skillId, skillLevel);
		}

		private ActorSnapshot actorForClass(int classId)
		{
			return new ActorSnapshot(10, classId, 0, 100, 100, 100, 100, 50, 100, false, false, false, false, false, 0, "IDLE", 0, 0);
		}

		private static AcquisitionTargetSnapshot liveAcquisitionTarget()
		{
			return new AcquisitionTargetSnapshot(200, 100, 0, 10, false, false, true, true, false, true, true, false, true, false, 0, false, false);
		}

		private AcquisitionTargetSnapshot liveTarget(int objectId)
		{
			return new AcquisitionTargetSnapshot(objectId, 100, 0, 10, false, false, true, true, false, true, true, false, true, false, 0, false, false);
		}

		private AcquisitionTargetSnapshot spoilObservedTarget()
		{
			return new AcquisitionTargetSnapshot(200, 100, 0, 10, false, false, true, true, false, true, true, false, true, true, 10, false, false);
		}

		private AcquisitionTargetSnapshot sweepTarget()
		{
			return new AcquisitionTargetSnapshot(200, 100, 0, 10, true, true, true, true, false, true, true, false, true, true, 10, true, true);
		}

		@Override
		public ActorSnapshot actorSnapshot()
		{
			return actor;
		}

		@Override
		public TargetSnapshot targetSnapshot(int targetObjectId)
		{
			return new TargetSnapshot(targetObjectId, 100, 0, 100, 100, false, false, true, true, false, true, true, 10, false, true);
		}

		@Override
		public AcquisitionTargetSnapshot acquisitionTargetSnapshot(int targetObjectId)
		{
			return (target != null) && (target.objectId() == targetObjectId) ? target : null;
		}

		@Override
		public List<AcquisitionTargetSnapshot> acquisitionTargets(int npcId, int limit, int maximumDistance)
		{
			return (target != null) && (target.npcId() == npcId) ? List.of(target) : List.of();
		}

		@Override
		public long acquisitionInventoryCount(int itemId)
		{
			return inventoryCounts.getOrDefault(itemId, inventoryCount);
		}

		@Override
		public Map<Integer, Long> acquisitionInventoryCounts(List<Integer> exactItemIds)
		{
			lastInventoryRequest = List.copyOf(exactItemIds);
			return PhantomCombatActorLease.super.acquisitionInventoryCounts(exactItemIds);
		}

		@Override
		public int acquisitionLevel()
		{
			return 85;
		}

		@Override
		public AcquisitionActorPosition acquisitionPosition()
		{
			return new AcquisitionActorPosition(0, 0, 0, 0);
		}

		@Override
		public int knownSkillLevel(int skillId)
		{
			return skillId == 254 ? 11 : (skillId == 42 ? 1 : (skillId == 172 ? 10 : 0));
		}

		@Override
		public boolean supportsSkill(SelectedSkill skill, PhantomCombatMode mode)
		{
			return false;
		}

		@Override
		public List<ThreatObservation> observedAttackers(int limit)
		{
			return List.of();
		}

		@Override
		public List<LootCandidate> lootCandidates(int limit, int maximumDistance)
		{
			return List.of();
		}

		@Override
		public LootObservation observeLoot(LootCandidate candidate)
		{
			return LootObservation.PENDING;
		}

		@Override
		public ShotOutcome activateShot(PhantomCombatMode mode)
		{
			return ShotOutcome.UNAVAILABLE;
		}

		@Override
		public ActionOutcome attack(int targetObjectId)
		{
			return ActionOutcome.ISSUED;
		}

		@Override
		public ActionOutcome cast(int targetObjectId, SelectedSkill skill, PhantomCombatMode mode)
		{
			return ActionOutcome.ISSUED;
		}

		@Override
		public ActionOutcome castAcquisition(int targetObjectId, SelectedSkill skill, AcquisitionSkillKind kind)
		{
			castCalls++;
			if (invalidateTargetOnRejectedCast && (castOutcome == ActionOutcome.REJECTED))
			{
				target = new AcquisitionTargetSnapshot(target.objectId(), target.npcId(), target.instanceId(), target.distance(), true, true, target.targetable(), target.attackable(), target.invulnerable(), target.normalMonster(), target.knowledgeMonster(), target.peaceRestricted(), target.surroundingRegion(), false, 0, false, false);
			}
			return castOutcome;
		}

		@Override
		public ActionOutcome pickUp(int objectId)
		{
			return ActionOutcome.ISSUED;
		}

		@Override
		public void cancelOwnedAction(PhantomOwnedAction action)
		{
		}

		@Override
		public RespawnOutcome respawnTown()
		{
			return RespawnOutcome.COMPLETED;
		}

		@Override
		public void close()
		{
		}
	}

	private static final class HoldingDispatcher implements PhantomCombatService.Dispatcher
	{
		private final HoldingHandle _handle = new HoldingHandle();

		@Override
		public DispatchResult dispatch(Runnable runnable, long delayMillis)
		{
			return DispatchResult.accepted(_handle);
		}
	}

	private static final class HoldingHandle implements DispatchHandle
	{
		private DispatchState _state = DispatchState.SCHEDULED;

		@Override
		public boolean cancelIfNotStarted()
		{
			if (_state != DispatchState.SCHEDULED)
			{
				return false;
			}
			_state = DispatchState.CANCELLED;
			return true;
		}

		@Override
		public DispatchState state()
		{
			return _state;
		}
	}

	private enum RecipeShape
	{
		SHARED,
		CYCLE,
		DEPTH,
		NODES,
		DEFICITS,
		ALTERNATIVE_UNION,
		ALTERNATIVES
	}

	private record PlannedSource(int itemId, PhantomAcquisitionSourcePlanner planner, PhantomAcquisitionSourcePlanner.Request request, PhantomAcquisitionSourcePlanner.Result result)
	{
	}

	private record SyntheticKnowledge(PhantomGameKnowledgeService service, PhantomTopologyQuery topology) implements AutoCloseable
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

	private record SyntheticSource(PhantomGameKnowledgeService service, PhantomTopologyQuery topology, PlannedSource planned) implements AutoCloseable
	{
		@Override
		public void close()
		{
			service.beginStop();
			PhantomAssertions.assertTrue(service.finishStop(), "Synthetic source knowledge did not stop.");
		}
	}
}
