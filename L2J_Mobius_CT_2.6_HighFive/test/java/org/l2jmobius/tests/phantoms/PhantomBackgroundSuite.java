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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.config.PlayerConfig;
import org.l2jmobius.gameserver.data.xml.DoorData;
import org.l2jmobius.gameserver.data.xml.ExperienceData;
import org.l2jmobius.gameserver.data.xml.ItemData;
import org.l2jmobius.gameserver.data.xml.MapRegionData;
import org.l2jmobius.gameserver.data.xml.NpcData;
import org.l2jmobius.gameserver.data.xml.SkillData;
import org.l2jmobius.gameserver.data.xml.SpawnData;
import org.l2jmobius.gameserver.managers.IdManager;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.enums.player.MountType;
import org.l2jmobius.gameserver.model.actor.enums.player.PlayerClass;
import org.l2jmobius.gameserver.model.actor.holders.npc.DropHolder;
import org.l2jmobius.gameserver.model.actor.templates.NpcTemplate;
import org.l2jmobius.gameserver.model.item.ItemTemplate;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.model.item.instance.Item;
import org.l2jmobius.gameserver.phantoms.PhantomSystem;
import org.l2jmobius.gameserver.phantoms.PhantomDiagnosticTrace;
import org.l2jmobius.gameserver.phantoms.PhantomMetrics;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState;
import org.l2jmobius.gameserver.phantoms.background.L2jPhantomBackgroundAuthority;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundAuthority;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundCompetitionRegistry;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundDecision;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundGoalSpec;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundLoginGuard;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundModel;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundModel.BatchRequest;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundModel.BatchResult;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundModel.DeathPolicy;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundModel.Drop;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundModel.DropDisposition;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundModel.ExperienceTable;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundModel.LevelForExperience;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundModel.RewardPolicy;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundModel.Target;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundOperationKey;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundOperationKey.ActionKind;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundService;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundService.OperationStatus;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.AutoGetSkill;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.Clock;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.CombatFacts;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.Hashes;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.Identity;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.InventoryFacts;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.ItemLocation;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.ItemObject;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.Loadout;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.ModelKind;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.Position;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.Progress;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.Receipt;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.State;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.Vitals;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundStateCodec;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundTransaction;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundTransaction.FaultPoint;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundTransaction.Result;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundTransaction.Status;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceCatalog;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceCatalogLoader;
import org.l2jmobius.gameserver.phantoms.decision.PhantomCandidateRegistry;
import org.l2jmobius.gameserver.phantoms.decision.PhantomCapabilitySet;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoal;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateStore;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus;
import org.l2jmobius.gameserver.phantoms.decision.PhantomPlan;
import org.l2jmobius.gameserver.phantoms.decision.PhantomPlanningContext;
import org.l2jmobius.gameserver.phantoms.decision.PhantomStepContext;
import org.l2jmobius.gameserver.phantoms.decision.PhantomStepHandlerRegistry;
import org.l2jmobius.gameserver.phantoms.decision.PhantomStepResult;
import org.l2jmobius.gameserver.phantoms.player.PhantomActionFacade;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationLifecyclePort;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializedPlayer.FailurePoint;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfile;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.gameserver.phantoms.knowledge.L2jGameKnowledgeBackend;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomCuratedKnowledgeParser;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeBuilder;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.DropFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.NpcKind;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgePolicy;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeQuery;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeService;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomStaticManorParser;
import org.l2jmobius.gameserver.phantoms.progression.L2jProgressionBackend;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionCatalog;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionCatalogBuilder;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.CapabilityRule;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionPolicy;
import org.l2jmobius.gameserver.phantoms.topology.PhantomRelevanceSignalPort;
import org.l2jmobius.gameserver.phantoms.topology.L2jTopologyValidationBackend;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyAnchor;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyAnchorRole;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyEdge;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyLoader;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyMetrics;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPolicy;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyQuery;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologySnapshot;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyValidationBackend;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyValidationBackend.DoorState;
import org.l2jmobius.gameserver.scripting.ScriptEngine;

public final class PhantomBackgroundSuite implements PhantomTestSuite
{
	public enum Mode
	{
		MODEL("background-model", false),
		TRANSACTION("background-transaction", true),
		LIFECYCLE("background-lifecycle", true),
		DECISION("background-decision", true),
		SERVER_INTEGRATION("background-server-integration", true),
		PERFORMANCE("background-performance", true),
		MATERIALIZATION_ABORT("background-materialization-abort", true),
		QUIESCENCE("background-quiescence", true),
		COMPACT_INVENTORY("background-compact-inventory", true),
		AUTHORITATIVE_SHOTS("background-authoritative-shots", true),
		PRODUCTION_AUDIT("background-production-audit", true),
		RECOVERY_TELEPORT("background-recovery-teleport", true),
		REAL_LOGIN("background-real-login", true),
		PRODUCTION_LOOT_UNBLOCK("background-production-loot-unblock", true);

		private final String _id;
		private final boolean _database;

		Mode(String id, boolean database)
		{
			_id = id;
			_database = database;
		}
	}

	private static final long SEED = 15001501L;
	private static final long PRODUCTION_LOOT_UNBLOCK_SEED = 15001502L;
	private static final int TARGET_NPC_ID = 100;
	private static final String ANCHOR_ID = "test.anchor";
	private static final int PRODUCTION_TARGET_NPC_ID = 22859;
	private static final String PRODUCTION_FARM_ANCHOR_ID = "giran.farming.22859";
	private static final int NO_GRADE_WEAPON_ITEM_ID = 6;
	private static final int NO_GRADE_SOULSHOT_ITEM_ID = 1835;
	private static final int NO_GRADE_SPIRITSHOT_ITEM_ID = 2509;
	private static final List<Integer> PRODUCTION_GROUND_LOSS_ITEM_IDS = List.of(8600, 8601, 8602, 8603, 8604, 8605, 8606, 8607, 8608, 8609, 8610, 8611, 8612, 8613, 8614, 10655, 10656, 10657, 13028);
	private static final Hashes HASHES = new Hashes("knowledge-v1", "topology-v1", "progression-v1", "commerce-v1");

	private final Mode _mode;
	private PhantomHeadlessPlayerTestEnvironment _environment;
	private PhantomProfileRepository _repository;
	private ProductionAuthorityFixture _production;

	public PhantomBackgroundSuite(Mode mode)
	{
		_mode = mode;
	}

	@Override
	public String id()
	{
		return _mode._id;
	}

	@Override
	public void beforeAll(PhantomTestContext context) throws Exception
	{
		final long expectedSeed = _mode == Mode.PRODUCTION_LOOT_UNBLOCK ? PRODUCTION_LOOT_UNBLOCK_SEED : SEED;
		PhantomAssertions.assertEquals(expectedSeed, context.seed(), "Goal 015 mode seed changed.");
		if (_mode._database)
		{
			_environment = new PhantomHeadlessPlayerTestEnvironment();
			_environment.initialize(context);
			_repository = PhantomProfileRepository.open();
			deleteStaleTestProfile(_environment.primary().objectId());
			deleteStaleTestProfile(_environment.observer().objectId());
			context.record("background.database", PhantomTestDatabaseGuard.TARGET_DATABASE);
			if (_mode == Mode.AUTHORITATIVE_SHOTS)
			{
				ScriptEngine.getInstance().executeScript(ScriptEngine.MASTER_HANDLER_FILE);
			}
			if ((_mode == Mode.SERVER_INTEGRATION) || (_mode == Mode.AUTHORITATIVE_SHOTS) || (_mode == Mode.PRODUCTION_AUDIT) || (_mode == Mode.PRODUCTION_LOOT_UNBLOCK))
			{
				_production = ProductionAuthorityFixture.start();
				context.record("background.productionKnowledgeHash", _production.knowledge().snapshot().combinedHash());
				context.record("background.productionTopologyHash", _production.topology().snapshot().canonicalHash());
			}
		}
	}

	private void deleteStaleTestProfile(int characterObjectId)
	{
		final Optional<PhantomProfile> stale = _repository.findByCharacterObjectId(characterObjectId);
		if (stale.isPresent())
		{
			_repository.delete(stale.get().profileId(), stale.get().rowVersion());
		}
	}

	@Override
	public void afterAll(PhantomTestContext context) throws Exception
	{
		if (_production != null)
		{
			_production.close();
		}
		if (_environment != null)
		{
			_environment.shutdown();
		}
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		switch (_mode)
		{
			case MODEL -> registerModel(registry);
			case TRANSACTION -> registerTransaction(registry);
			case LIFECYCLE -> registerLifecycle(registry);
			case DECISION -> registerDecision(registry);
			case SERVER_INTEGRATION -> registerServerIntegration(registry);
			case PERFORMANCE -> registerPerformance(registry);
			case MATERIALIZATION_ABORT -> registerMaterializationAbort(registry);
			case QUIESCENCE -> registerQuiescence(registry);
			case COMPACT_INVENTORY -> registerCompactInventory(registry);
			case AUTHORITATIVE_SHOTS -> registerAuthoritativeShots(registry);
			case PRODUCTION_AUDIT -> registerProductionAudit(registry);
			case RECOVERY_TELEPORT -> registerRecoveryTeleport(registry);
			case REAL_LOGIN -> registerRealLogin(registry);
			case PRODUCTION_LOOT_UNBLOCK -> registerProductionLootUnblock(registry);
		}
	}

	private void registerModel(PhantomTestRegistry registry)
	{
		registry.add("01-state-codec-and-bound", _ -> testStateCodec());
		registry.add("02-exp-sp-formula", _ -> testRewardFormula());
		registry.add("03-rng-replay-and-resources", _ -> testDeterminismAndResources());
		registry.add("04-drop-object-capacity", _ -> testDropsAndCapacity());
		registry.add("05-causal-death-and-loss", _ -> testCausalDeath());
		registry.add("06-competition-capacity-release", _ -> testCompetition());
		registry.add("07-grouped-ungrouped-occurrence-parity", _ -> testDropOccurrenceParity());
	}

	private void registerTransaction(PhantomTestRegistry registry)
	{
		registry.add("01-atomic-canonical-batch-and-duplicate", _ -> testCanonicalBatch());
		registry.add("02-precommit-fault-rollback", _ -> testPrecommitFaults());
		registry.add("03-verify-pending-restart-and-inconsistent", _ -> testVerifyPending());
		registry.add("04-main-subclass-sql-isolation", _ -> testSubclassIsolation());
		registry.add("05-stale-goal-generation-and-hash", _ -> testOperationIdentityGuards());
		registry.add("06-transition-and-postcommit-faults", _ -> testTransitionFaults());
		registry.add("07-level-auto-get-and-drop-items", _ -> testLevelAutoGetAndDropItems());
	}

	private void registerLifecycle(PhantomTestRegistry registry)
	{
		registry.add("01-active-background-100-ticks", _ -> testLifecycleLoop(1, 100));
		registry.add("02-fifty-transition-conservation", _ -> testLifecycleLoop(50, 0));
		registry.add("03-death-warm-recovery", _ -> testDeathRecovery());
		registry.add("04-disabled-stop-drain", _ -> testStopDrain());
	}

	private void registerDecision(PhantomTestRegistry registry)
	{
		registry.add("01-exact-goal-contract", _ -> testGoalContract());
		registry.add("02-exact-candidate-and-handlers", _ -> testDecisionRegistrations());
		registry.add("03-activity-identity-reaches-handler", _ -> testDecisionExecutionIdentity());
	}

	private void registerServerIntegration(PhantomTestRegistry registry)
	{
		registry.add("01-real-player-transition", _ -> testLifecycleLoop(2, 2));
		registry.add("02-real-login-background-arbitration", _ -> testIdentityArbitration());
		registry.add("03-restart-every-durable-phase", _ -> testRestartPhases());
		registry.add("04-real-player-monster-drop-fixture", _ -> testProductionAuthorityFixture());
		registry.add("05-real-topology-travel", _ -> testProductionTravel());
	}

	private void registerPerformance(PhantomTestRegistry registry)
	{
		registry.add("01-100k-pure-model-evaluations", this::testModelPerformance);
		registry.add("02-10k-duplicate-reconciliations", this::testDuplicatePerformance);
		registry.add("03-bounded-batch-and-no-worker", _ -> testBoundedStructure());
	}

	private void registerMaterializationAbort(PhantomTestRegistry registry)
	{
		registry.add("01-terminal-callback-matrix", _ -> testMaterializationAbortMatrix());
		registry.add("02-background-claim-abort-retry", _ -> testBackgroundClaimAbortRetry());
	}

	private void registerQuiescence(PhantomTestRegistry registry)
	{
		registry.add("01-materializing-drain-gate", _ -> testMaterializingQuiescence());
		registry.add("02-transaction-and-retained-drain-gate", _ -> testBlockedQuiescence());
	}

	private void registerCompactInventory(PhantomTestRegistry registry)
	{
		registry.add("01-full-inventory-hash-over-64", _ -> testCompactInventoryHash());
		registry.add("02-fifty-transition-byte-conservation", _ -> testLifecycleLoop(50, 0));
	}

	private void registerAuthoritativeShots(PhantomTestRegistry registry)
	{
		registry.add("01-current-data-shot-contract", _ -> testAuthoritativeShotContract());
	}

	private void registerProductionAudit(PhantomTestRegistry registry)
	{
		registry.add("01-current-corpus-supported-pair-audit", this::testProductionCorpusAudit);
	}

	private void registerProductionLootUnblock(PhantomTestRegistry registry)
	{
		registry.add("01-shipped-loot-policy-authority-and-drift", _ -> testProductionLootPolicy());
		registry.add("02-canonical-ground-loss-model", _ -> testGroundLossModelSemantics());
		registry.add("03-real-player-atomic-batch-and-conservation", this::testProductionLootBatch);
	}

	private void registerRecoveryTeleport(PhantomTestRegistry registry)
	{
		registry.add("01-bounded-canonical-town-recovery", _ -> testDeathRecovery());
		registry.add("02-recovery-cancellation", _ -> testRecoveryCancellation());
	}

	private void registerRealLogin(PhantomTestRegistry registry)
	{
		registry.add("01-durable-background-login-guard", _ -> testRealLoginGuard());
	}

	private void testMaterializationAbortMatrix() throws Exception
	{
		final PhantomIdentityLeaseRegistry identities = PhantomIdentityLeaseRegistry.getInstance();
		final int primaryId = _environment.primary().objectId();
		final int observerId = _environment.observer().objectId();

		final PhantomProfile changedProfile = _repository.create(primaryId);
		final RecordingLifecyclePort changedPort = new RecordingLifecyclePort();
		final AtomicReference<PhantomMaterializationService> changedRef = new AtomicReference<>();
		changedPort._before = () -> changedRef.get().shutdown();
		final PhantomMaterializationService changed = materialization(1, point ->
		{
		}, changedPort);
		changedRef.set(changed);
		changed.start();
		PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SERVICE_NOT_RUNNING, changed.materialize(changedProfile.profileId()).status(), "Service-state change after beforeMaterialize was not typed.");
		changedPort.assertTerminal(1, 0, 1);
		deleteProfile(changedProfile);

		final PhantomProfile primary = _repository.create(primaryId);
		final PhantomProfile observer = _repository.create(observerId);
		final RecordingLifecyclePort port = new RecordingLifecyclePort();
		final PhantomMaterializationService service = materialization(2, point ->
		{
		}, port);
		service.start();
		try
		{
			PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, service.materialize(primary.profileId()).status(), "Matrix baseline materialization failed.");
			PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.ALREADY_ACTIVE, service.materialize(primary.profileId()).status(), "ALREADY_ACTIVE path changed.");
			port.assertTerminal(2, 1, 1);

			final Field profilesField = PhantomMaterializationService.class.getDeclaredField("_activeByProfile");
			final Field charactersField = PhantomMaterializationService.class.getDeclaredField("_activeByCharacter");
			profilesField.setAccessible(true);
			charactersField.setAccessible(true);
			final ConcurrentHashMap<?, ?> profiles = (ConcurrentHashMap<?, ?>) profilesField.get(service);
			@SuppressWarnings("unchecked")
			final ConcurrentHashMap<Integer, Object> characters = (ConcurrentHashMap<Integer, Object>) charactersField.get(service);
			final Object existingEntry = profiles.get(primary.profileId());
			characters.put(observerId, existingEntry);
			try
			{
				PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.CHARACTER_ALREADY_ACTIVE, service.materialize(observer.profileId()).status(), "CHARACTER_ALREADY_ACTIVE path changed.");
			}
			finally
			{
				characters.remove(observerId, existingEntry);
			}
			port.assertTerminal(3, 1, 2);
			PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, service.dematerialize(primary.profileId()).status(), "Matrix baseline cleanup failed.");

			try (var identity = identities.tryAcquire(observerId, PhantomIdentityLeaseRegistry.OwnerKind.BACKGROUND))
			{
				PhantomAssertions.assertTrue(identity != null, "Could not reserve the identity-busy fixture.");
				PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.IDENTITY_BUSY, service.materialize(observer.profileId()).status(), "IDENTITY_BUSY path changed.");
			}
			port.assertTerminal(4, 1, 3);

			try (var retained = identities.tryAcquire(observerId, PhantomIdentityLeaseRegistry.OwnerKind.REAL_LOGIN))
			{
				PhantomAssertions.assertTrue((retained != null) && retained.markRetained(), "Could not create retained real-login ownership.");
				updateCharacterOnline(observerId, 1);
				try
				{
					PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.RETAINED_IDENTITY_NOT_RECOVERABLE, service.materialize(observer.profileId()).status(), "Retained recovery rejection path changed.");
				}
				finally
				{
					updateCharacterOnline(observerId, 0);
				}
			}
			port.assertTerminal(5, 1, 4);
		}
		finally
		{
			service.shutdown();
			deleteProfile(primary);
			deleteProfile(observer);
		}

		assertInjectedMaterializationAbort(primaryId, FailurePoint.AFTER_IDENTITY_CLAIM, false);
		assertInjectedMaterializationAbort(primaryId, FailurePoint.AFTER_PLAYER_LOAD, false);
		assertInjectedMaterializationAbort(primaryId, FailurePoint.AFTER_WORLD_SPAWN, false);
		assertInjectedMaterializationAbort(primaryId, null, true);

		final PhantomProfile missing = _repository.create(2_000_000_001);
		final RecordingLifecyclePort missingPort = new RecordingLifecyclePort();
		final PhantomMaterializationService missingService = materialization(1, point ->
		{
		}, missingPort);
		missingService.start();
		try
		{
			PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.MATERIALIZATION_FAILED_CLEAN, missingService.materialize(missing.profileId()).status(), "Player.load failure was not cleanly aborted.");
			missingPort.assertTerminal(1, 0, 1);
		}
		finally
		{
			missingService.shutdown();
			deleteProfile(missing);
		}

		final PhantomProfile capacityPrimary = _repository.create(primaryId);
		final PhantomProfile capacityObserver = _repository.create(observerId);
		final RecordingLifecyclePort capacityPort = new RecordingLifecyclePort();
		final PhantomMaterializationService capacity = materialization(1, point ->
		{
		}, capacityPort);
		capacity.start();
		try
		{
			PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, capacity.materialize(capacityPrimary.profileId()).status(), "Capacity fixture baseline failed.");
			PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.CAPACITY_REACHED, capacity.materialize(capacityObserver.profileId()).status(), "CAPACITY_REACHED path changed.");
			capacityPort.assertTerminal(2, 1, 1);
			capacity.dematerialize(capacityPrimary.profileId());
		}
		finally
		{
			capacity.shutdown();
			deleteProfile(capacityPrimary);
			deleteProfile(capacityObserver);
		}
		PhantomAssertions.assertEquals(null, identities.getOwnerSnapshot(primaryId), "Materialization abort matrix leaked primary identity ownership.");
		PhantomAssertions.assertEquals(null, identities.getOwnerSnapshot(observerId), "Materialization abort matrix leaked observer identity ownership.");
	}

	private void assertInjectedMaterializationAbort(int characterObjectId, FailurePoint failurePoint, boolean callbackFailure) throws Exception
	{
		final PhantomProfile profile = _repository.create(characterObjectId);
		final RecordingLifecyclePort port = new RecordingLifecyclePort();
		if (callbackFailure)
		{
			port._afterLoadFailure = true;
		}
		final PhantomMaterializationService service = materialization(1, point ->
		{
			if (point == failurePoint)
			{
				throw new InjectedFailure();
			}
		}, port);
		service.start();
		try
		{
			PhantomAssertions.assertTrue(service.materialize(profile.profileId()).status() != PhantomMaterializationService.ResultStatus.SUCCESS, "Injected materialization failure unexpectedly succeeded: " + failurePoint);
			port.assertTerminal(1, 0, 1);
			PhantomAssertions.assertEquals(0, service.snapshot().retainedEntries(), "Injected materialization failure retained an entry: " + failurePoint);
		}
		finally
		{
			service.shutdown();
			deleteProfile(profile);
		}
	}

	private void testBackgroundClaimAbortRetry() throws Exception
	{
		final AtomicBoolean failOnce = new AtomicBoolean(true);
		final RuntimeFixture runtime = createRuntimeFixture(_environment.primary().objectId(), new PhantomBackgroundTransaction(), point ->
		{
			if ((point == FailurePoint.AFTER_PLAYER_LOAD) && failOnce.compareAndSet(true, false))
			{
				throw new InjectedFailure();
			}
		});
		try
		{
			final var failed = runtime.materialization().materialize(runtime.profileId());
			PhantomAssertions.assertTrue(failed.status() != PhantomMaterializationService.ResultStatus.SUCCESS, "Injected post-load failure unexpectedly succeeded.");
			PhantomAssertions.assertTrue(runtime.background().materializationQuiescence().ready(), "Aborted attempt leaked a background transition claim.");
			PhantomAssertions.assertEquals(State.READY, runtime.transaction().load(runtime.profileId()).state().state(), "Aborted MATERIALIZED state was not restored to READY.");
			PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, runtime.materialization().materialize(runtime.profileId()).status(), "Retry after terminal abort did not materialize.");
			PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, runtime.materialization().dematerialize(runtime.profileId()).status(), "Retry cleanup failed.");
			runtime.background().materializeAborted(runtime.profileId(), runtime.characterObjectId());
			PhantomAssertions.assertTrue(runtime.background().materializationQuiescence().ready(), "Idempotent abort changed the quiescence state.");
		}
		finally
		{
			runtime.close();
		}
	}

	private void testMaterializingQuiescence() throws Exception
	{
		final RuntimeFixture runtime = createRuntimeFixture(_environment.primary().objectId());
		try
		{
			runtime.background().beforeMaterialize(runtime.profileId(), runtime.characterObjectId());
			runtime.background().beginStop();
			final PhantomBackgroundService.QuiescenceSnapshot blocked = runtime.background().materializationQuiescence();
			PhantomAssertions.assertEquals(1, blocked.materializingTransitionClaims(), "MATERIALIZING claim was not exposed to shutdown.");
			PhantomAssertions.assertFalse(PhantomSystem.permitsMaterializationShutdown(blocked), "Materialization shutdown ignored a MATERIALIZING claim.");
			PhantomAssertions.assertEquals(PhantomMaterializationService.ServiceState.RUNNING, runtime.materialization().snapshot().state(), "Materialization stopped before background quiescence.");
			runtime.background().materializeAborted(runtime.profileId(), runtime.characterObjectId());
			PhantomAssertions.assertTrue(PhantomSystem.permitsMaterializationShutdown(runtime.background().materializationQuiescence()), "Terminal abort did not open the materialization shutdown gate.");
			PhantomAssertions.assertEquals(PhantomMaterializationService.ServiceState.STOPPED, runtime.materialization().shutdown().state(), "Materialization did not stop after quiescence.");
			PhantomAssertions.assertTrue(runtime.background().finishStop(), "Background did not finish after materialization stopped.");
		}
		finally
		{
			runtime.close();
		}
	}

	private void testBlockedQuiescence() throws Exception
	{
		final CountDownLatch entered = new CountDownLatch(1);
		final CountDownLatch release = new CountDownLatch(1);
		final AtomicBoolean block = new AtomicBoolean();
		final PhantomBackgroundTransaction transaction = new PhantomBackgroundTransaction(DatabaseFactory::getConnection, allocator(new AtomicInteger()), point ->
		{
			if ((point == FaultPoint.AFTER_PROFILE_LOCK) && block.get())
			{
				entered.countDown();
				try
				{
					if (!release.await(10, TimeUnit.SECONDS))
					{
						throw new AssertionError("Timed out waiting to release blocked background transaction.");
					}
				}
				catch (InterruptedException exception)
				{
					Thread.currentThread().interrupt();
					throw new AssertionError("Blocked background transaction was interrupted.", exception);
				}
			}
		});
		final RuntimeFixture runtime = createRuntimeFixture(_environment.primary().objectId(), transaction, point ->
		{
		});
		try
		{
			runtime.materialization().materialize(runtime.profileId());
			runtime.materialization().dematerialize(runtime.profileId());
			block.set(true);
			final AtomicReference<PhantomBackgroundService.OperationResult> outcome = new AtomicReference<>();
			final Thread worker = Thread.ofPlatform().name("goal015-blocked-transaction").start(() -> outcome.set(runtime.background().farm(runtime.profileId(), runtime.goal(), 1, 1, PhantomActivityState.BACKGROUND, 1)));
			PhantomAssertions.assertTrue(entered.await(10, TimeUnit.SECONDS), "Background transaction did not reach the blocking point.");
			final PhantomBackgroundService.QuiescenceSnapshot blocked = runtime.background().materializationQuiescence();
			PhantomAssertions.assertTrue((blocked.operations() == 1) && (blocked.identityLeases() == 1) && (blocked.transactions() == 1), "In-flight operation/identity/transaction were not exposed together.");
			PhantomAssertions.assertFalse(PhantomSystem.permitsMaterializationShutdown(blocked), "Materialization shutdown ignored an in-flight transaction.");
			runtime.background().beginStop();
			PhantomAssertions.assertEquals(PhantomMaterializationService.ServiceState.RUNNING, runtime.materialization().snapshot().state(), "Materialization stopped while the transaction was blocked.");
			release.countDown();
			worker.join(10_000);
			PhantomAssertions.assertFalse(worker.isAlive(), "Blocked background worker did not drain.");
			PhantomAssertions.assertTrue(outcome.get() != null, "Blocked background worker produced no result.");

			final Field retainedField = PhantomBackgroundService.class.getDeclaredField("_retainedIdentityLeases");
			retainedField.setAccessible(true);
			@SuppressWarnings("unchecked")
			final Map<Integer, Object> retained = (Map<Integer, Object>) retainedField.get(runtime.background());
			final int sentinelId = 2_000_000_002;
			final var sentinel = PhantomIdentityLeaseRegistry.getInstance().tryAcquire(sentinelId, PhantomIdentityLeaseRegistry.OwnerKind.BACKGROUND);
			PhantomAssertions.assertTrue(sentinel != null, "Could not reserve retained-quiescence sentinel.");
			retained.put(sentinelId, sentinel);
			try
			{
				PhantomAssertions.assertEquals(1, runtime.background().materializationQuiescence().retainedIdentityLeases(), "Retained identity was not exposed to shutdown.");
				PhantomAssertions.assertFalse(PhantomSystem.permitsMaterializationShutdown(runtime.background().materializationQuiescence()), "Materialization shutdown ignored retained identity ownership.");
			}
			finally
			{
				retained.remove(sentinelId);
				sentinel.close();
			}
			PhantomAssertions.assertTrue(runtime.background().materializationQuiescence().ready(), "Drained transaction did not become quiescent.");
		}
		finally
		{
			release.countDown();
			runtime.close();
		}
	}

	private void testCompactInventoryHash() throws Exception
	{
		final RuntimeFixture runtime = createRuntimeFixture(_environment.primary().objectId());
		try
		{
			PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, runtime.materialization().materialize(runtime.profileId()).status(), "Compact inventory fixture did not materialize.");
			try (var action = runtime.materialization().tryAcquireAction(runtime.profileId()).orElseThrow())
			{
				final Item unrelated = action.player().getInventory().addItem(ItemProcessType.REWARD, NO_GRADE_WEAPON_ITEM_ID, 100, action.player(), this);
				PhantomAssertions.assertTrue(unrelated != null, "Could not create >64 unrelated canonical inventory objects.");
			}
			PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, runtime.materialization().dematerialize(runtime.profileId()).status(), "Compact inventory fixture did not capture.");
			final PhantomBackgroundState ready = runtime.transaction().load(runtime.profileId()).state();
			PhantomAssertions.assertTrue(scalarLong("SELECT COUNT(*) FROM items WHERE owner_id=? AND item_id=" + NO_GRADE_WEAPON_ITEM_ID, runtime.characterObjectId()) >= 100, "Current inventory API did not create the required >64 unrelated objects.");
			PhantomAssertions.assertFalse(ready.inventory().canonicalHash().isBlank(), "Full canonical inventory hash was not persisted.");
			PhantomAssertions.assertTrue(ready.inventory().objects().stream().noneMatch(item -> item.itemId() == NO_GRADE_WEAPON_ITEM_ID), "Unrelated objects leaked into the compact mutable projection.");
			PhantomAssertions.assertTrue(new PhantomBackgroundStateCodec().encode(ready).length <= 4096, "Compact >64 inventory state exceeded 4096 bytes.");
			final int unrelatedObjectId = (int) scalarLong("SELECT MIN(object_id) FROM items WHERE owner_id=? AND item_id=" + NO_GRADE_WEAPON_ITEM_ID, runtime.characterObjectId());
			try (Connection connection = DatabaseFactory.getConnection();
				PreparedStatement statement = connection.prepareStatement("UPDATE items SET count=2 WHERE object_id=? AND owner_id=?"))
			{
				statement.setInt(1, unrelatedObjectId);
				statement.setInt(2, runtime.characterObjectId());
				PhantomAssertions.assertEquals(1, statement.executeUpdate(), "Concurrent unrelated mutation fixture failed.");
			}
			final Result conflict = runtime.transaction().execute(new PhantomBackgroundTransaction.Command(ready, runtime.goal(), key(runtime.fixture(), 1, 1, ActionKind.FARM), ready.progress(), ready.vitals(), ready.position(), ready.clock(), Map.of(), ready.autoGetSkills()));
			PhantomAssertions.assertEquals(Status.CANONICAL_MISMATCH, conflict.status(), "Concurrent untracked canonical inventory change was not a typed conflict.");
			try (Connection connection = DatabaseFactory.getConnection();
				PreparedStatement statement = connection.prepareStatement("UPDATE items SET count=1 WHERE object_id=? AND owner_id=?"))
			{
				statement.setInt(1, unrelatedObjectId);
				statement.setInt(2, runtime.characterObjectId());
				statement.executeUpdate();
			}
			final Result exact = runtime.transaction().execute(new PhantomBackgroundTransaction.Command(ready, runtime.goal(), key(runtime.fixture(), 2, 1, ActionKind.FARM), ready.progress(), ready.vitals(), ready.position(), ready.clock(), Map.of(57, -1L, 10, 2L), ready.autoGetSkills()));
			PhantomAssertions.assertEquals(Status.SUCCESS, exact.status(), "Supported tracked resource/drop batch failed with >64 unrelated objects.");
			PhantomAssertions.assertEquals(_environment.primary().fixtureItemBaseline() - 1, scalarLong("SELECT COALESCE(SUM(count),0) FROM items WHERE owner_id=? AND item_id=57", runtime.characterObjectId()), "Tracked stack resource delta was not exact.");
			PhantomAssertions.assertEquals(2L, scalarLong("SELECT COUNT(*) FROM items WHERE owner_id=? AND item_id=10", runtime.characterObjectId()), "Tracked non-stackable drop delta was not exact.");
		}
		finally
		{
			runtime.close();
		}
	}

	private void testAuthoritativeShotContract() throws Exception
	{
		final int objectId = _environment.primary().objectId();
		final Canonical original = canonical(objectId);
		final int originalBaseClass = (int) scalarLong("SELECT base_class FROM characters WHERE charId=?", objectId);
		final ShotCapabilitySelection selection = productionShotCapability();
		Player player = null;
		try
		{
			try (Connection connection = DatabaseFactory.getConnection();
				PreparedStatement statement = connection.prepareStatement("UPDATE characters SET classid=?,base_class=?,race=?,level=85,exp=? WHERE charId=?"))
			{
				statement.setInt(1, selection.playerClass().getId());
				statement.setInt(2, selection.playerClass().getId());
				statement.setInt(3, selection.playerClass().getRace().ordinal());
				statement.setLong(4, ExperienceData.getInstance().getExpForLevel(85));
				statement.setInt(5, objectId);
				statement.executeUpdate();
			}
			player = Player.load(objectId);
			PhantomAssertions.assertTrue(player != null, "Authoritative shot Player could not be loaded.");
			final var skill = SkillData.getInstance().getSkill(selection.rule().actionSkill().skillId(), selection.rule().actionSkill().skillLevel());
			PhantomAssertions.assertTrue(skill != null, "Authoritative physical capability skill is missing.");
			player.addSkill(skill, false);
			final Item weapon = player.getInventory().addItem(ItemProcessType.REWARD, NO_GRADE_WEAPON_ITEM_ID, 1, player, this);
			PhantomAssertions.assertTrue(weapon != null, "No-grade authoritative weapon could not be created.");
			player.getInventory().equipItem(weapon);
			final Item shots = player.getInventory().addItem(ItemProcessType.REWARD, NO_GRADE_SOULSHOT_ITEM_ID, 10, player, this);
			PhantomAssertions.assertTrue(shots != null, "No-grade authoritative soulshot could not be created.");
			final Player configuredPlayer = player;
			final L2jPhantomBackgroundAuthority.ShotContract positive = _production.authority().validateShotContract(configuredPlayer, goalWithShot(NO_GRADE_SOULSHOT_ITEM_ID, 1));
			PhantomAssertions.assertEquals(ModelKind.MELEE, positive.modelKind(), "Physical capability selected the wrong background model.");
			PhantomAssertions.assertEquals(1, positive.shotsPerEncounter(), "Current weapon soulshot count changed.");
			PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> _production.authority().validateShotContract(configuredPlayer, goalWithShot(57, 1)), "Adena/arbitrary item was admitted as a shot.");
			PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> _production.authority().validateShotContract(configuredPlayer, goalWithShot(NO_GRADE_SPIRITSHOT_ITEM_ID, 1)), "Wrong physical shot type was admitted.");
			PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> _production.authority().validateShotContract(configuredPlayer, goalWithShot(1463, 1)), "Wrong-grade shot was admitted.");
			PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> _production.authority().validateShotContract(configuredPlayer, goalWithShot(NO_GRADE_SOULSHOT_ITEM_ID, 2)), "Wrong bounded shot count was admitted.");
		}
		finally
		{
			_environment.cleanupLoadedPlayer(player);
			restoreCharacter(objectId, original, originalBaseClass);
			restorePrimaryInventoryAndSkills(objectId);
		}
	}

	private void testProductionCorpusAudit(PhantomTestContext context)
	{
		final var knowledge = _production.knowledge().snapshot();
		final List<String> audited = new ArrayList<>();
		final List<String> supported = new ArrayList<>();
		for (PhantomTopologyAnchor anchor : _production.topology().snapshot().anchors().stream().filter(candidate -> candidate.role() == PhantomTopologyAnchorRole.FARMING).sorted(Comparator.comparing(PhantomTopologyAnchor::id)).toList())
		{
			final int npcId = anchor.npcId() == null ? 0 : anchor.npcId();
			final var npc = knowledge.npcById().get(npcId);
			final boolean normal = (npc != null) && (npc.kind() == NpcKind.MONSTER) && npc.attackable() && npc.targetable() && (NpcData.getInstance().getTemplate(npcId) != null);
			final boolean spawned = knowledge.spawnAreasByNpc().getOrDefault(npcId, List.of()).stream().anyMatch(area -> (area.instanceId() == 0) && (area.totalConfiguredAmount() > 0) && anchor.nodeId().equals(area.topologyNodeId()));
			final List<Integer> acquired = new ArrayList<>();
			final List<Integer> leaveOnGround = new ArrayList<>();
			final List<Integer> autoAcquiredUnsupported = new ArrayList<>();
			boolean templatesResolved = true;
			for (DropFact fact : knowledge.dropFactsByNpc().getOrDefault(npcId, List.of()))
			{
				final var item = ItemData.getInstance().getTemplate(fact.itemId());
				if (item == null)
				{
					templatesResolved = false;
					continue;
				}
				final boolean immediateOrLimited = item.hasExImmediateEffect() || (item.getTime() != -1);
				if (!immediateOrLimited)
				{
					acquired.add(fact.itemId());
				}
				else if (currentAutoLoot(item))
				{
					autoAcquiredUnsupported.add(fact.itemId());
				}
				else
				{
					leaveOnGround.add(fact.itemId());
				}
			}
			final List<Integer> exactAcquired = acquired.stream().distinct().sorted().toList();
			final List<Integer> exactGround = leaveOnGround.stream().distinct().sorted().toList();
			final List<Integer> exactAutoAcquired = autoAcquiredUnsupported.stream().distinct().sorted().toList();
			final boolean pairSupported = normal && spawned && templatesResolved && exactAutoAcquired.isEmpty();
			final String evidence = npcId + "@" + anchor.id() + ":normal=" + normal + ":spawned=" + spawned + ":acquire=" + exactAcquired + ":leaveOnGround=" + exactGround + ":autoAcquiredUnsupported=" + exactAutoAcquired + ":supported=" + pairSupported;
			audited.add(evidence);
			if (pairSupported)
			{
				supported.add(npcId + "@" + anchor.id());
			}
		}
		PhantomAssertions.assertEquals(1, audited.size(), "Deterministic FARMING anchor audit cardinality changed.");
		PhantomAssertions.assertTrue(audited.getFirst().contains(":leaveOnGround=" + PRODUCTION_GROUND_LOSS_ITEM_IDS + ":"), "Shipped production ground-loss corpus changed.");
		PhantomAssertions.assertTrue(audited.getFirst().contains(":autoAcquiredUnsupported=[]:supported=true"), "Shipped production pair is no longer fail-closed supported.");
		PhantomAssertions.assertEquals(List.of(PRODUCTION_TARGET_NPC_ID + "@" + PRODUCTION_FARM_ANCHOR_ID), supported, "Exact supported production farm pair changed.");
		context.record("background.productionLootAudit", String.join("|", audited));
	}

	private void testProductionLootPolicy() throws Exception
	{
		final Map<String, String> shipped = shippedAutoLootConfig();
		PhantomAssertions.assertEquals("False", shipped.get("AutoLootHerbs"), "Shipped AutoLootHerbs changed.");
		PhantomAssertions.assertEquals("False", shipped.get("AutoLoot"), "Shipped AutoLoot changed.");
		PhantomAssertions.assertEquals("True", shipped.get("AutoLootSlotLimit"), "Shipped AutoLootSlotLimit changed.");
		PhantomAssertions.assertEquals("0", shipped.get("AutoLootItemIds"), "Shipped AutoLootItemIds changed.");
		PhantomAssertions.assertFalse(PlayerConfig.AUTO_LOOT_HERBS, "Loaded AutoLootHerbs differs from shipped Player.ini.");
		PhantomAssertions.assertFalse(PlayerConfig.AUTO_LOOT, "Loaded AutoLoot differs from shipped Player.ini.");
		PhantomAssertions.assertTrue(PlayerConfig.AUTO_LOOT_SLOT_LIMIT, "Loaded AutoLootSlotLimit differs from shipped Player.ini.");
		PhantomAssertions.assertEquals(Set.of(), PlayerConfig.AUTO_LOOT_ITEM_IDS, "Loaded AutoLootItemIds differs from shipped Player.ini.");

		try (ProductionPlayerFixture fixture = openProductionPlayerFixture())
		{
			final L2jPhantomBackgroundAuthority authority = _production.authority();
			final PhantomBackgroundGoalSpec spec = PhantomBackgroundGoalSpec.parse(fixture.goal());
			final Hashes baselineHashes = authority.hashes();
			PhantomAssertions.assertEquals(baselineHashes, authority.hashes(), "Current loot-policy authority hash is not deterministic.");
			final PhantomBackgroundState captured = authority.capture(PRODUCTION_LOOT_UNBLOCK_SEED, fixture.player(), fixture.goal(), null);
			final PhantomBackgroundState oldReady = captured.withState(State.READY);
			final PhantomBackgroundAuthority.FarmInput input = authority.farmInput(oldReady, spec);
			final List<Drop> acquired = input.target().drops().stream().filter(drop -> drop.disposition() == DropDisposition.ACQUIRE).toList();
			final List<Drop> ground = input.target().drops().stream().filter(drop -> drop.disposition() == DropDisposition.LEAVE_ON_GROUND).toList();
			PhantomAssertions.assertTrue(!acquired.isEmpty(), "Ordinary production drops were not classified ACQUIRE.");
			PhantomAssertions.assertEquals(PRODUCTION_GROUND_LOSS_ITEM_IDS, ground.stream().map(Drop::itemId).distinct().sorted().toList(), "Immediate/time-limited production drops were not classified LEAVE_ON_GROUND.");
			final int immediateId = ground.stream().map(Drop::itemId).filter(itemId -> ItemData.getInstance().getTemplate(itemId).hasExImmediateEffect()).findFirst().orElseThrow();
			final ItemTemplate timeLimitedItem = firstTimeLimitedOrdinaryItem();
			PhantomAssertions.assertEquals(DropDisposition.LEAVE_ON_GROUND, productionDropDisposition(timeLimitedItem), "Time-limited ordinary item was not classified LEAVE_ON_GROUND.");

			fixture.player().setFlying(true);
			try
			{
				PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> authority.capture(PRODUCTION_LOOT_UNBLOCK_SEED, fixture.player(), fixture.goal(), null), "Flying Player was admitted to production background farming.");
			}
			finally
			{
				fixture.player().setFlying(false);
			}
			final Field mountType = Player.class.getDeclaredField("_mountType");
			mountType.setAccessible(true);
			final Object originalMountType = mountType.get(fixture.player());
			mountType.set(fixture.player(), MountType.STRIDER);
			try
			{
				PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> authority.capture(PRODUCTION_LOOT_UNBLOCK_SEED, fixture.player(), fixture.goal(), null), "Mounted Player was admitted to production background farming.");
			}
			finally
			{
				mountType.set(fixture.player(), originalMountType);
			}

			final boolean originalAutoLoot = PlayerConfig.AUTO_LOOT;
			final boolean originalAutoLootHerbs = PlayerConfig.AUTO_LOOT_HERBS;
			final boolean originalAutoLootSlotLimit = PlayerConfig.AUTO_LOOT_SLOT_LIMIT;
			final Set<Integer> originalAutoLootItemIds = PlayerConfig.AUTO_LOOT_ITEM_IDS;
			try
			{
				PlayerConfig.AUTO_LOOT_SLOT_LIMIT = !originalAutoLootSlotLimit;
				final Hashes drifted = authority.hashes();
				PhantomAssertions.assertFalse(baselineHashes.equals(drifted), "LOOT_POLICY_V1 did not fingerprint AutoLootSlotLimit drift.");
				PhantomAssertions.assertThrows(IllegalStateException.class, () -> authority.farmInput(oldReady, spec), "Old READY authority state did not fail closed on loot-policy drift.");
			}
			finally
			{
				PlayerConfig.AUTO_LOOT = originalAutoLoot;
				PlayerConfig.AUTO_LOOT_HERBS = originalAutoLootHerbs;
				PlayerConfig.AUTO_LOOT_SLOT_LIMIT = originalAutoLootSlotLimit;
				PlayerConfig.AUTO_LOOT_ITEM_IDS = originalAutoLootItemIds;
			}
			PhantomAssertions.assertEquals(baselineHashes, authority.hashes(), "Loot-policy config restoration did not restore the authority hash.");

			try
			{
				PlayerConfig.AUTO_LOOT_HERBS = true;
				PhantomAssertions.assertFalse(baselineHashes.equals(authority.hashes()), "LOOT_POLICY_V1 did not fingerprint AutoLootHerbs drift.");
				PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> authority.capture(PRODUCTION_LOOT_UNBLOCK_SEED, fixture.player(), fixture.goal(), captured), "Auto-looted immediate drop did not reject the target before baseline.");
			}
			finally
			{
				PlayerConfig.AUTO_LOOT_HERBS = originalAutoLootHerbs;
			}
			try
			{
				PlayerConfig.AUTO_LOOT = true;
				PhantomAssertions.assertFalse(baselineHashes.equals(authority.hashes()), "LOOT_POLICY_V1 did not fingerprint AutoLoot drift.");
				PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> productionDropDisposition(timeLimitedItem), "Auto-looted time-limited drop did not fail closed.");
			}
			finally
			{
				PlayerConfig.AUTO_LOOT = originalAutoLoot;
			}
			try
			{
				PlayerConfig.AUTO_LOOT_ITEM_IDS = Set.of(immediateId, timeLimitedItem.getId());
				PhantomAssertions.assertFalse(baselineHashes.equals(authority.hashes()), "LOOT_POLICY_V1 did not fingerprint AutoLootItemIds drift.");
				PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> authority.capture(PRODUCTION_LOOT_UNBLOCK_SEED, fixture.player(), fixture.goal(), captured), "Specific auto-loot item IDs did not reject the target before baseline.");
				PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> productionDropDisposition(timeLimitedItem), "Specific time-limited auto-loot item ID did not fail closed.");
			}
			finally
			{
				PlayerConfig.AUTO_LOOT_ITEM_IDS = originalAutoLootItemIds;
			}
			PhantomAssertions.assertEquals(baselineHashes, authority.hashes(), "Static loot-policy values were not fully restored.");
		}
	}

	private void testGroundLossModelSemantics()
	{
		final PhantomBackgroundModel model = new PhantomBackgroundModel();
		final Drop immediateGround = new Drop(8600, -1, 0, 100, 100, 1, 1, 1, null, 1, 100, false, 1_000_000, DropDisposition.LEAVE_ON_GROUND);
		final Drop timeLimitedGround = new Drop(10655, -1, 1, 100, 100, 1, 1, 1, null, 1, 100, false, 1_000_000, DropDisposition.LEAVE_ON_GROUND);
		final InventoryFacts full = new InventoryFacts(List.of(), List.of(), "ground-loss", 100, 100, 100, 100);
		final PhantomBackgroundState fullState = state(1, 101, State.READY, 100, 100, full);
		final BatchRequest groundOnlyRequest = request(fullState, target(1, 0, 0, List.of(immediateGround, timeLimitedGround)));
		final BatchResult groundOnly = model.evaluate(groundOnlyRequest);
		PhantomAssertions.assertTrue(groundOnly.encounters() > 0, "Ground-only encounter was blocked by full inventory.");
		PhantomAssertions.assertEquals(Map.of(), groundOnly.inventoryDelta().itemDeltas(), "Ground loss entered Player inventory deltas.");
		PhantomAssertions.assertEquals(0L, groundOnly.inventoryDelta().addedWeight(), "Ground loss consumed Player weight.");
		PhantomAssertions.assertEquals(0, groundOnly.inventoryDelta().addedSlots(), "Ground loss consumed Player slots.");
		PhantomAssertions.assertEquals(0, groundOnly.inventoryDelta().newNonStackableObjects(), "Ground loss reserved Player item objects.");
		PhantomAssertions.assertTrue(groundOnly.groundLosses().keySet().containsAll(List.of(8600, 10655)), "Immediate/time-limited ground-loss evidence is incomplete.");
		PhantomAssertions.assertEquals(groundOnly, model.evaluate(groundOnlyRequest), "Ground-loss RNG replay changed.");

		final List<Drop> mixedDrops = List.of(
			new Drop(8600, 0, 0, 100, 50, 1, 1, 1, null, 1, 100, true, 0, DropDisposition.LEAVE_ON_GROUND),
			new Drop(57, 0, 1, 100, 50, 1, 1, 1, null, 1, 100, true, 0, DropDisposition.ACQUIRE),
			new Drop(10655, 1, 0, 100, 99, 1, 1, 1, null, 1, 100, true, 0, DropDisposition.LEAVE_ON_GROUND),
			new Drop(4037, 2, 0, 100, 99, 1, 1, 1, null, 1, 100, true, 0, DropDisposition.ACQUIRE),
			new Drop(13028, -1, 0, 100, 99, 1, 1, 1, null, 1, 100, true, 0, DropDisposition.LEAVE_ON_GROUND),
			new Drop(10, -1, 1, 100, 99, 1, 1, 1, null, 1, 100, true, 0, DropDisposition.ACQUIRE));
		final List<Drop> allAcquiredDrops = mixedDrops.stream().map(drop -> new Drop(drop.itemId(), drop.groupOrdinal(), drop.itemOrdinal(), drop.rawGroupChance(), drop.rawItemChance(), drop.minimumCount(), drop.maximumCount(), drop.chanceMultiplier(), drop.configuredChanceMultiplier(), drop.amountMultiplier(), drop.levelGapChance(), drop.stackable(), drop.itemWeight(), DropDisposition.ACQUIRE)).toList();
		boolean observedGroupedGroundSuppression = false;
		boolean observedGroupedGroundOccurrence = false;
		boolean observedUngroupedGroundOccurrence = false;
		for (long seed = 1; seed <= 1_000; seed++)
		{
			final PhantomBackgroundState base = state(1, 101, State.READY, 1, 1, inventory());
			final PhantomBackgroundState seeded = base.after(base.progress(), base.vitals(), base.position(), base.inventory(), base.autoGetSkills(), new Clock(seed, 0, 0), base.receipt());
			final BatchResult groupedOnly = model.evaluate(request(seeded, singleEncounterTarget(mixedDrops.subList(0, 2))));
			if (groupedOnly.groundLosses().containsKey(8600))
			{
				PhantomAssertions.assertFalse(groupedOnly.inventoryDelta().itemDeltas().containsKey(57), "Selected ignored group award did not suppress its later alternative.");
				observedGroupedGroundSuppression = true;
			}
			final Target mixedTarget = singleEncounterTarget(mixedDrops);
			final Target acquiredTarget = singleEncounterTarget(allAcquiredDrops);
			final BatchResult mixed = model.evaluate(request(seeded, mixedTarget));
			final BatchResult acquired = model.evaluate(request(seeded, acquiredTarget));
			final Map<Integer, Long> combinedAwards = new LinkedHashMap<>(mixed.inventoryDelta().itemDeltas());
			mixed.groundLosses().forEach((itemId, count) -> combinedAwards.merge(itemId, count, Math::addExact));
			PhantomAssertions.assertEquals(acquired.inventoryDelta().itemDeltas(), combinedAwards, "Disposition changed canonical grouped/ungrouped occurrence awards at seed " + seed);
			PhantomAssertions.assertEquals(acquired.nextRngState(), mixed.nextRngState(), "Disposition changed canonical RNG advancement at seed " + seed);
			observedGroupedGroundOccurrence |= mixed.groundLosses().containsKey(10655);
			observedUngroupedGroundOccurrence |= mixed.groundLosses().containsKey(13028);
			if (observedGroupedGroundSuppression && observedGroupedGroundOccurrence && observedUngroupedGroundOccurrence)
			{
				break;
			}
		}
		PhantomAssertions.assertTrue(observedGroupedGroundSuppression, "Grouped ignored award suppression was not exercised.");
		PhantomAssertions.assertTrue(observedGroupedGroundOccurrence, "Grouped occurrence budget did not include a ground-loss award.");
		PhantomAssertions.assertTrue(observedUngroupedGroundOccurrence, "Ungrouped occurrence budget did not include a ground-loss award.");
	}

	private void testProductionLootBatch(PhantomTestContext context) throws Exception
	{
		ProductionPlayerFixture playerFixture = null;
		PhantomProfile profile = null;
		PhantomBackgroundService background = null;
		PhantomMaterializationService materialization = null;
		final AtomicInteger reservedObjectIds = new AtomicInteger();
		try
		{
			playerFixture = openProductionPlayerFixture();
			final int objectId = playerFixture.player().getObjectId();
			profile = _repository.create(objectId);
			final PhantomGoal goal = playerFixture.goal();
			final PhantomGoalStateStore goals = new PhantomGoalStateStore(_repository);
			goals.insert(profile.profileId(), goal);
			final PhantomBackgroundTransaction.ObjectIdAllocator ids = new PhantomBackgroundTransaction.ObjectIdAllocator()
			{
				@Override
				public int reserve()
				{
					reservedObjectIds.incrementAndGet();
					return IdManager.getInstance().getNextId();
				}

				@Override
				public void release(int objectId)
				{
					IdManager.getInstance().releaseId(objectId);
				}
			};
			final PhantomBackgroundTransaction transaction = new PhantomBackgroundTransaction(DatabaseFactory::getConnection, ids, PhantomBackgroundTransaction.FaultInjector.none());
			final AtomicReference<PhantomMaterializationService> materializationRef = new AtomicReference<>();
			background = new PhantomBackgroundService(_repository, goals, PhantomIdentityLeaseRegistry.getInstance(), transaction, _production.authority(), new PhantomBackgroundCompetitionRegistry(), noSignals(), materializationRef::get);
			PhantomAssertions.assertTrue(background.start(), "Production background service did not start.");
			final PhantomMetrics metrics = new PhantomMetrics();
			materialization = new PhantomMaterializationService(_repository, PhantomIdentityLeaseRegistry.getInstance(), metrics, new PhantomDiagnosticTrace(false, 64, 16, metrics), 1, point ->
			{
			}, exactAnchorLifecycle(background, playerFixture.farm().anchor()), 5_000, 10_000);
			PhantomAssertions.assertTrue(materialization.start(), "Production materialization service did not start.");
			materializationRef.set(materialization);

			final PhantomBackgroundState initialCapture = _production.authority().capture(profile.profileId(), playerFixture.player(), goal, null);
			playerFixture.player().storeMe();
			final PhantomBackgroundState zeroResidual = initialCapture.after(initialCapture.progress(), initialCapture.vitals(), initialCapture.position(), initialCapture.inventory(), initialCapture.autoGetSkills(), new Clock(context.seed(), 0, 0), initialCapture.receipt()).withState(State.READY);
			final PhantomBackgroundAuthority.FarmInput zeroResidualInput = _production.authority().farmInput(zeroResidual, PhantomBackgroundGoalSpec.parse(goal));
			final long residualEncounterMillis = largestSuccessfulResidual(zeroResidual, zeroResidualInput);
			final PhantomBackgroundState seededPrevious = initialCapture.after(initialCapture.progress(), initialCapture.vitals(), initialCapture.position(), initialCapture.inventory(), initialCapture.autoGetSkills(), new Clock(context.seed(), 0, residualEncounterMillis), initialCapture.receipt());
			final PhantomBackgroundState seededCapture = _production.authority().capture(profile.profileId(), playerFixture.player(), goal, seededPrevious);
			PhantomAssertions.assertEquals(Status.SUCCESS, transaction.captureBaseline(seededCapture, goal).status(), "Seeded real production baseline capture failed.");
			PhantomAssertions.assertEquals(Status.SUCCESS, transaction.markMaterialized(profile.profileId(), objectId).status(), "Seeded real production baseline did not enter MATERIALIZED.");
			background.beforeStore(profile.profileId(), playerFixture.player());
			playerFixture.player().storeMe();
			background.afterStore(profile.profileId(), playerFixture.player());
			playerFixture.releaseRuntime();
			final PhantomBackgroundState ready = transaction.load(profile.profileId()).state();
			PhantomAssertions.assertEquals(State.READY, ready.state(), "Real production baseline did not dematerialize to READY.");
			PhantomAssertions.assertEquals(PRODUCTION_LOOT_UNBLOCK_SEED, context.seed(), "Production batch used the wrong deterministic seed.");
			PhantomAssertions.assertEquals(PRODUCTION_LOOT_UNBLOCK_SEED, ready.clock().rngState(), "Captured production baseline did not use seed 15001502.");

			final PhantomBackgroundAuthority.FarmInput input = _production.authority().farmInput(ready, PhantomBackgroundGoalSpec.parse(goal));
			final PhantomBackgroundModel model = new PhantomBackgroundModel();
			final BatchResult expected = model.evaluate(new BatchRequest(ready, input.target(), input.rewardPolicy(), input.deathPolicy(), input.experienceTable(), input.levelForExperience(), false));
			PhantomAssertions.assertTrue(expected.mutated(), "Seeded production model produced no successful encounter: reason=" + expected.reason() + ", mp=" + ready.vitals().currentMp() + ", skillMp=" + ready.loadout().skillMpPerEncounter());
			PhantomAssertions.assertEquals(1, expected.encounters(), "Focused production batch did not execute exactly one real encounter.");
			PhantomAssertions.assertFalse(expected.dead(), "Supported real production capability did not survive one encounter.");
			PhantomAssertions.assertTrue(!expected.groundLosses().isEmpty(), "Seeded production batch did not exercise ground-loss evidence.");
			final PhantomBackgroundOperationKey operationKey = new PhantomBackgroundOperationKey(profile.profileId(), objectId, goal.goalId(), goal.revision(), 1, 1, ActionKind.FARM, PRODUCTION_TARGET_NPC_ID, PRODUCTION_FARM_ANCHOR_ID, PhantomBackgroundState.MODEL_VERSION, _production.authority().hashes());
			final PhantomBackgroundService.OperationResult committed = background.farm(profile.profileId(), goal, 1, 1, PhantomActivityState.BACKGROUND, 1);
			PhantomAssertions.assertEquals(OperationStatus.SUCCESS, committed.status(), "Real production background batch did not commit.");
			PhantomAssertions.assertEquals(expected.encounters(), committed.encounters(), "Committed production encounter count differs from the production model.");
			final PhantomBackgroundState after = transaction.load(profile.profileId()).state();
			PhantomAssertions.assertEquals(expected.progress(), after.progress(), "Canonical production EXP/SP differs from the model.");
			PhantomAssertions.assertEquals(Math.round(expected.vitals().currentHp()), (long) after.vitals().currentHp(), "Canonical production HP differs from the model.");
			PhantomAssertions.assertEquals(Math.round(expected.vitals().currentMp()), (long) after.vitals().currentMp(), "Canonical production MP differs from the model.");
			PhantomAssertions.assertEquals(expected.nextRngState(), after.clock().rngState(), "Committed production RNG state differs from the model.");
			PhantomAssertions.assertEquals(operationKey.digest(), after.receipt().operationKey(), "Committed production receipt identity differs.");
			PhantomAssertions.assertTrue(!after.receipt().expectedAfterHash().isBlank(), "Committed production receipt lacks canonical after-hash.");
			final Map<Integer, Long> beforeCounts = trackedInventoryCounts(ready);
			final Map<Integer, Long> afterCounts = trackedInventoryCounts(after);
			for (Map.Entry<Integer, Long> delta : expected.inventoryDelta().itemDeltas().entrySet())
			{
				PhantomAssertions.assertEquals(Math.addExact(beforeCounts.getOrDefault(delta.getKey(), 0L), delta.getValue()), afterCounts.getOrDefault(delta.getKey(), 0L), "Exact acquired production item delta differs for " + delta.getKey());
			}
			PhantomAssertions.assertEquals(expected.inventoryDelta().addedSlots(), reservedObjectIds.get(), "Ground losses changed object-ID reservation count.");
			for (int itemId : PRODUCTION_GROUND_LOSS_ITEM_IDS)
			{
				PhantomAssertions.assertEquals(0L, scalarLong("SELECT COUNT(*) FROM items WHERE owner_id = ? AND item_id = " + itemId, objectId), "Ground-loss item entered canonical Player inventory: " + itemId);
			}

			final PhantomBackgroundStateCodec codec = new PhantomBackgroundStateCodec();
			final byte[] beforeDuplicate = codec.encode(after);
			final int reservationsBeforeDuplicate = reservedObjectIds.get();
			final List<AutoGetSkill> expectedAutoSkills = _production.authority().autoGetSkills(ready.identity(), expected.progress().level());
			final PhantomBackgroundTransaction.Command duplicateCommand = new PhantomBackgroundTransaction.Command(ready, goal, operationKey, expected.progress(), expected.vitals(), ready.position(), new Clock(expected.nextRngState(), 0, 0), expected.inventoryDelta().itemDeltas(), expectedAutoSkills);
			final Result duplicate = transaction.execute(duplicateCommand);
			PhantomAssertions.assertEquals(Status.IDEMPOTENT, duplicate.status(), "Exact production duplicate was not idempotent.");
			PhantomAssertions.assertTrue(java.util.Arrays.equals(beforeDuplicate, codec.encode(transaction.load(profile.profileId()).state())), "Production duplicate rerolled or regranted durable loot.");
			PhantomAssertions.assertEquals(reservationsBeforeDuplicate, reservedObjectIds.get(), "Production duplicate reserved another object ID.");

			final Player committedProbe = Player.load(objectId);
			PhantomAssertions.assertTrue(committedProbe != null, "Committed production Player did not reload for the conservation preflight.");
			try
			{
				final PhantomTopologyAnchor anchor = playerFixture.farm().anchor();
				committedProbe.setXYZInvisible(anchor.point().x(), anchor.point().y(), anchor.point().z());
				assertProductionRuntimeMatches(committedProbe, transaction.load(profile.profileId()).state());
			}
			finally
			{
				_environment.cleanupLoadedPlayer(committedProbe);
			}
			final PhantomMaterializationService.MaterializeResult materialized = materialization.materialize(profile.profileId());
			PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, materialized.status(), "Committed production state did not materialize.");
			try (var action = materialization.tryAcquireAction(profile.profileId()).orElseThrow())
			{
				final Player reloaded = action.player();
				PhantomAssertions.assertTrue(_production.authority().matchesRuntime(reloaded, transaction.load(profile.profileId()).state()), "Reloaded real Player differs from committed production state.");
				for (int itemId : PRODUCTION_GROUND_LOSS_ITEM_IDS)
				{
					PhantomAssertions.assertTrue(reloaded.getInventory().getItemByItemId(itemId) == null, "Ground-loss item materialized into Player inventory: " + itemId);
				}
			}
			PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, materialization.dematerialize(profile.profileId()).status(), "Production conservation dematerialization failed.");
			final PhantomBackgroundState reloadedReady = new PhantomBackgroundTransaction().load(profile.profileId()).state();
			PhantomAssertions.assertTrue(java.util.Arrays.equals(beforeDuplicate, codec.encode(reloadedReady)), "Materialization/dematerialization changed the committed production state.");
			context.record("background.productionEncounters", committed.encounters());
			context.record("background.productionAcquiredDeltas", expected.inventoryDelta().itemDeltas());
			context.record("background.productionGroundLosses", expected.groundLosses());
			context.record("background.productionReservedObjectIds", reservedObjectIds.get());
			context.record("background.productionResidualEncounterMillis", residualEncounterMillis);
		}
		finally
		{
			if ((materialization != null) && (profile != null) && materialization.find(profile.profileId()).isPresent())
			{
				materialization.dematerialize(profile.profileId());
			}
			if (materialization != null)
			{
				materialization.shutdown();
			}
			if (background != null)
			{
				background.beginStop();
				background.finishStop();
			}
			if (profile != null)
			{
				deleteProfile(profile);
			}
			if (playerFixture != null)
			{
				playerFixture.close();
			}
		}
	}

	private void assertProductionRuntimeMatches(Player player, PhantomBackgroundState state)
	{
		PhantomAssertions.assertTrue(player.getObjectId() == state.identity().characterObjectId(), "Reloaded production Player object ID differs.");
		PhantomAssertions.assertTrue(player.getClassIndex() == state.identity().classIndex(), "Reloaded production Player class index differs.");
		PhantomAssertions.assertTrue(player.getActiveClass() == state.identity().activeClassId(), "Reloaded production Player active class differs.");
		PhantomAssertions.assertTrue(player.getRace().ordinal() == state.identity().raceOrdinal(), "Reloaded production Player race differs.");
		PhantomAssertions.assertTrue(player.getLevel() == state.progress().level(), "Reloaded production Player level differs.");
		PhantomAssertions.assertTrue(player.getExp() == state.progress().experience(), "Reloaded production Player EXP differs.");
		PhantomAssertions.assertTrue(player.getSp() == state.progress().skillPoints(), "Reloaded production Player SP differs.");
		PhantomAssertions.assertTrue(player.getExpBeforeDeath() == state.progress().experienceBeforeDeath(), "Reloaded production Player pre-death EXP differs.");
		assertProductionDouble(state.vitals().currentHp(), player.getCurrentHp(), "HP");
		assertProductionDouble(state.vitals().maximumHp(), player.getMaxHp(), "maximum HP");
		assertProductionDouble(state.vitals().currentMp(), player.getCurrentMp(), "MP");
		assertProductionDouble(state.vitals().maximumMp(), player.getMaxMp(), "maximum MP");
		assertProductionDouble(state.vitals().currentCp(), player.getCurrentCp(), "CP");
		assertProductionDouble(state.vitals().maximumCp(), player.getMaxCp(), "maximum CP");
		PhantomAssertions.assertTrue(player.getInstanceId() == state.position().instanceId(), "Reloaded production Player instance differs.");
		PhantomAssertions.assertTrue(player.getX() == state.position().x(), "Reloaded production Player X differs: expected=" + state.position().x() + ", actual=" + player.getX());
		PhantomAssertions.assertTrue(player.getY() == state.position().y(), "Reloaded production Player Y differs: expected=" + state.position().y() + ", actual=" + player.getY());
		PhantomAssertions.assertTrue(player.getZ() == state.position().z(), "Reloaded production Player Z differs: expected=" + state.position().z() + ", actual=" + player.getZ());
		PhantomAssertions.assertTrue(player.getHeading() == state.position().heading(), "Reloaded production Player heading differs: expected=" + state.position().heading() + ", actual=" + player.getHeading());
		PhantomAssertions.assertTrue(_production.authority().matchesRuntime(player, state), "Exact production authority rejected a field-by-field matching reload.");
	}

	private static PhantomMaterializationLifecyclePort exactAnchorLifecycle(PhantomMaterializationLifecyclePort delegate, PhantomTopologyAnchor anchor)
	{
		return new PhantomMaterializationLifecyclePort()
		{
			@Override
			public void beforeMaterialize(long profileId, int characterObjectId)
			{
				delegate.beforeMaterialize(profileId, characterObjectId);
			}

			@Override
			public void afterPlayerLoad(long profileId, Player player)
			{
				player.setXYZInvisible(anchor.point().x(), anchor.point().y(), anchor.point().z());
				delegate.afterPlayerLoad(profileId, player);
			}

			@Override
			public void materializeSucceeded(long profileId, int characterObjectId)
			{
				delegate.materializeSucceeded(profileId, characterObjectId);
			}

			@Override
			public void materializeAborted(long profileId, int characterObjectId)
			{
				delegate.materializeAborted(profileId, characterObjectId);
			}

			@Override
			public void beforeStore(long profileId, Player player)
			{
				delegate.beforeStore(profileId, player);
			}

			@Override
			public void afterStore(long profileId, Player player)
			{
				delegate.afterStore(profileId, player);
			}
		};
	}

	private static void assertProductionDouble(double expected, double actual, String field)
	{
		PhantomAssertions.assertTrue(Math.abs(expected - actual) <= 0.000_001, "Reloaded production Player " + field + " differs: expected=" + expected + ", actual=" + actual);
	}

	private void testRecoveryCancellation() throws Exception
	{
		final RuntimeFixture runtime = createRuntimeFixture(_environment.primary().objectId());
		try
		{
			makeDead(runtime);
			final var cancelled = runtime.background().recover(runtime.profileId(), runtime.goal(), PhantomActivityState.WARM, () -> true);
			PhantomAssertions.assertEquals(OperationStatus.RETRY, cancelled.status(), "Recovery cancellation was not typed RETRY.");
			PhantomAssertions.assertEquals("recovery.teleport_cancelled", cancelled.reason(), "Recovery cancellation reason changed.");
			PhantomAssertions.assertEquals(State.DEAD, runtime.transaction().load(runtime.profileId()).state().state(), "Cancelled recovery changed durable DEAD state.");
			PhantomAssertions.assertTrue(runtime.materialization().find(runtime.profileId()).isEmpty(), "Cancelled recovery materialized the Player.");
		}
		finally
		{
			runtime.close();
		}
	}

	private void testRealLoginGuard() throws Exception
	{
		final Fixture fixture = createFixture(_environment.primary().objectId(), null);
		try
		{
			final byte[] before = new PhantomBackgroundStateCodec().encode(fixture.transaction().load(fixture.profileId()).state());
			PhantomAssertions.assertEquals(PhantomBackgroundLoginGuard.Decision.REJECT_BACKGROUND_OWNED, PhantomBackgroundLoginGuard.inspect(fixture.characterObjectId()), "READY background state did not block real login.");
			try (var login = PhantomIdentityLeaseRegistry.getInstance().tryAcquire(fixture.characterObjectId(), PhantomIdentityLeaseRegistry.OwnerKind.REAL_LOGIN))
			{
				PhantomAssertions.assertTrue(login != null, "Between-ticks real-login lease could not be acquired.");
				PhantomAssertions.assertEquals(PhantomBackgroundLoginGuard.Decision.REJECT_BACKGROUND_OWNED, PhantomBackgroundLoginGuard.inspect(fixture.characterObjectId()), "Between-ticks durable background state did not block real login.");
			}
			try (var background = PhantomIdentityLeaseRegistry.getInstance().tryAcquire(fixture.characterObjectId(), PhantomIdentityLeaseRegistry.OwnerKind.BACKGROUND))
			{
				PhantomAssertions.assertTrue(background != null, "Background lease fixture could not be acquired.");
				PhantomAssertions.assertTrue(PhantomIdentityLeaseRegistry.getInstance().tryAcquire(fixture.characterObjectId(), PhantomIdentityLeaseRegistry.OwnerKind.REAL_LOGIN) == null, "Real login bypassed a held background lease.");
			}
			final byte[] after = new PhantomBackgroundStateCodec().encode(fixture.transaction().load(fixture.profileId()).state());
			PhantomAssertions.assertTrue(java.util.Arrays.equals(before, after), "Rejected real-login checks changed durable background state.");
			PhantomAssertions.assertEquals(Status.SUCCESS, fixture.transaction().markMaterialized(fixture.profileId(), fixture.characterObjectId()).status(), "Could not mark the real-login positive control MATERIALIZED.");
			PhantomAssertions.assertEquals(PhantomBackgroundLoginGuard.Decision.ALLOW_MATERIALIZED, PhantomBackgroundLoginGuard.inspect(fixture.characterObjectId()), "MATERIALIZED state was not admitted to the existing real-login arbitration seam.");
			PhantomAssertions.assertEquals(Status.SUCCESS, fixture.transaction().abortMaterialization(fixture.profileId(), fixture.characterObjectId()).status(), "Could not restore the real-login fixture.");
		}
		finally
		{
			fixture.close();
		}
	}

	private void testStateCodec()
	{
		final PhantomBackgroundState state = state(1, 101, State.READY, 100, 100, inventory());
		final PhantomBackgroundStateCodec codec = new PhantomBackgroundStateCodec();
		final byte[] first = codec.encode(state);
		final byte[] second = codec.encode(codec.decode(first));
		PhantomAssertions.assertTrue(java.util.Arrays.equals(first, second), "background.state codec is not byte deterministic.");
		PhantomAssertions.assertTrue(first.length <= 4096, "background.state exceeded 4096 bytes.");
	}

	private void testRewardFormula()
	{
		final PhantomBackgroundModel model = new PhantomBackgroundModel();
		final CombatFacts combat = combat(ModelKind.MELEE, 2, 3, 0);
		final Target target = target(90, 1000, 10, List.of());
		final PhantomBackgroundModel.Rewards reward = PhantomBackgroundModel.calculateRewards(85, target, new RewardPolicy(11, 2, 3), combat);
		PhantomAssertions.assertEquals(1680L, reward.experience(), "High-level EXP rounding diverged from Attackable semantics.");
		PhantomAssertions.assertEquals(37L, reward.skillPoints(), "High-level SP truncation diverged from Attackable semantics.");
		final BatchResult ignored = model.evaluate(request(state(1, 101, State.READY, 100, 100, inventory()), target));
		PhantomAssertions.assertTrue(ignored.encounters() > 0, "Supported reward fixture did not execute.");
	}

	private void testDeterminismAndResources()
	{
		final InventoryFacts inventory = InventoryFacts.sorted(List.of(1463, 2509, 6645), List.of(
			new ItemObject(1, 1463, 500, true, ItemLocation.INVENTORY),
			new ItemObject(2, 2509, 500, true, ItemLocation.INVENTORY),
			new ItemObject(3, 6645, 500, true, ItemLocation.INVENTORY)), "model", 1000, 100000, 3, 100);
		final PhantomBackgroundModel model = new PhantomBackgroundModel();
		for (int shotItemId : List.of(1463, 2509))
		{
			final Loadout loadout = new Loadout(1, 1, 0, 1, shotItemId, 1, 6645, 1);
			final PhantomBackgroundState state = state(1, 101, State.READY, 100, 100, inventory, combat(ModelKind.MAGIC, 1, 1, 0), loadout);
			final BatchRequest request = request(state, target(1, 1, 0, List.of()));
			final BatchResult first = model.evaluate(request);
			final BatchResult second = model.evaluate(request);
			PhantomAssertions.assertEquals(first, second, "Persisted RNG stream did not replay.");
			PhantomAssertions.assertEquals(-((long) first.encounters()), first.inventoryDelta().itemDeltas().get(shotItemId), "Soulshot/spiritshot consumption is not exact.");
			PhantomAssertions.assertEquals(-((long) first.encounters()), first.inventoryDelta().itemDeltas().get(6645), "Summon-resource consumption is not exact.");
			PhantomAssertions.assertTrue(first.vitals().currentMp() < state.vitals().currentMp(), "Selected-skill MP was not consumed.");
		}
	}

	private void testDropsAndCapacity()
	{
		final Drop stackable = new Drop(57, -1, 0, 100, 100, 3, 3, 1, null, 1, 100, true, 0);
		final Drop nonstackable = new Drop(10, -1, 1, 100, 100, 1, 1, 1, null, 1, 100, false, 10);
		final PhantomBackgroundState state = state(1, 101, State.READY, 100, 100, new InventoryFacts(List.of(10, 57), List.of(), "model", 0, 100000, 0, 100));
		final BatchResult result = new PhantomBackgroundModel().evaluate(request(state, target(1, 1, 0, List.of(stackable, nonstackable))));
		PhantomAssertions.assertTrue(result.encounters() > 0, "Guaranteed drop fixture produced no encounters.");
		PhantomAssertions.assertTrue(result.inventoryDelta().itemDeltas().get(57) % 3 == 0, "Drop amount became fractional.");
		PhantomAssertions.assertTrue(result.inventoryDelta().newNonStackableObjects() <= PhantomBackgroundModel.MAX_NEW_NON_STACKABLE_OBJECTS, "Non-stackable object cap was exceeded.");
		final InventoryFacts full = new InventoryFacts(List.of(10), List.of(), "model", 100, 100, 0, 100);
		final BatchResult rejected = new PhantomBackgroundModel().evaluate(request(state(1, 101, State.READY, 100, 100, full), target(1, 1, 0, List.of(nonstackable))));
		PhantomAssertions.assertEquals(PhantomBackgroundModel.ResultReason.WEIGHT_CAPACITY, rejected.reason(), "Weight limit did not stop before mutation.");
		final List<Drop> tooMany = new ArrayList<>();
		for (int index = 0; index < 17; index++)
		{
			tooMany.add(new Drop(100 + index, -1, index, 100, 100, 1, 1, 1, null, 1, 100, true, 0));
		}
		final BatchResult objectRejected = new PhantomBackgroundModel().evaluate(request(state, target(1, 1, 0, tooMany)));
		PhantomAssertions.assertEquals(PhantomBackgroundModel.ResultReason.OBJECT_CAP, objectRejected.reason(), "Changed-item-object limit did not stop before mutation.");
		PhantomAssertions.assertEquals(0, objectRejected.encounters(), "Changed-item-object limit mutated an encounter.");
	}

	private void testCausalDeath()
	{
		final PhantomBackgroundState state = state(1, 101, State.READY, 2, 100, inventory(), combat(ModelKind.MELEE, 1, 1, 0), Loadout.none());
		final Target lethal = new Target(TARGET_NPC_ID, 1, true, 100, 10, 100000, 1, 1, 1, 1000, 1000, 0, 0, List.of(), 2);
		final BatchResult result = new PhantomBackgroundModel().evaluate(request(state, lethal));
		PhantomAssertions.assertTrue(result.dead(), "Death was not caused by encounter attrition.");
		PhantomAssertions.assertEquals(0d, result.vitals().currentHp(), "Death did not set HP to zero.");
		PhantomAssertions.assertEquals(0d, result.vitals().currentCp(), "Death did not set CP to zero.");
		final long loss = PhantomBackgroundModel.calculateDeathExperienceLoss(deathPolicy(), experienceTable(), 2, 100);
		PhantomAssertions.assertEquals(10L, loss, "Normal-monster death loss cap diverged.");
	}

	private void testCompetition()
	{
		final PhantomBackgroundCompetitionRegistry registry = new PhantomBackgroundCompetitionRegistry();
		final var first = registry.tryReserve("node", TARGET_NPC_ID, 1);
		PhantomAssertions.assertTrue(first != null, "First competition reservation failed.");
		PhantomAssertions.assertTrue(registry.tryReserve("node", TARGET_NPC_ID, 1) == null, "Competition exceeded spawn capacity.");
		first.close();
		PhantomAssertions.assertEquals(0, registry.currentReservations(), "Competition reservation was not released.");
	}

	private void testDropOccurrenceParity()
	{
		final Drop grouped = new Drop(57, 0, 0, 100, 99, 1, 1, 1, null, 1, 100, true, 0);
		final Drop ungrouped = new Drop(4037, -1, 0, 100, 99, 1, 1, 1, null, 1, 100, true, 0);
		final Target separateBudgets = new Target(TARGET_NPC_ID, 1, true, 1, 1, 1_000_000, 1, 1, 1, 1_000, 1_000, 0, 0, List.of(grouped, ungrouped), 1);
		boolean observedBoth = false;
		for (long seed = 1; seed <= 1_000; seed++)
		{
			final PhantomBackgroundState base = state(1, 101, State.READY, 1, 100, inventory());
			final PhantomBackgroundState seeded = base.after(base.progress(), base.vitals(), base.position(), base.inventory(), base.autoGetSkills(), new Clock(seed, 0, 0), base.receipt());
			final Map<Integer, Long> deltas = new PhantomBackgroundModel().evaluate(request(seeded, separateBudgets)).inventoryDelta().itemDeltas();
			if (deltas.containsKey(grouped.itemId()) && deltas.containsKey(ungrouped.itemId()))
			{
				observedBoth = true;
				break;
			}
		}
		PhantomAssertions.assertTrue(observedBoth, "Grouped and ungrouped current-loader occurrence budgets were incorrectly shared.");

		final Drop first = new Drop(57, 0, 0, 100, 50, 1, 1, 1, null, 1, 100, true, 0);
		final Drop second = new Drop(4037, 0, 1, 100, 50, 1, 1, 1, null, 1, 100, true, 0);
		final Target cumulativeGroup = new Target(TARGET_NPC_ID, 1, true, 1, 1, 1_000_000, 1, 1, 1, 1_000, 1_000, 0, 0, List.of(first, second), 1);
		boolean observedFirst = false;
		boolean observedSecond = false;
		for (long seed = 1; seed <= 1_000; seed++)
		{
			final PhantomBackgroundState base = state(1, 101, State.READY, 1, 100, inventory());
			final PhantomBackgroundState seeded = base.after(base.progress(), base.vitals(), base.position(), base.inventory(), base.autoGetSkills(), new Clock(seed, 0, 0), base.receipt());
			final Map<Integer, Long> deltas = new PhantomBackgroundModel().evaluate(request(seeded, cumulativeGroup)).inventoryDelta().itemDeltas();
			PhantomAssertions.assertFalse(deltas.containsKey(first.itemId()) && deltas.containsKey(second.itemId()), "Rate-x1 cumulative group awarded two items in one encounter.");
			observedFirst |= deltas.containsKey(first.itemId());
			observedSecond |= deltas.containsKey(second.itemId());
		}
		PhantomAssertions.assertTrue(observedFirst && observedSecond, "Cumulative group corpus did not exercise both exact item identities.");
	}

	private void testCanonicalBatch() throws Exception
	{
		final Fixture fixture = createFixture(_environment.primary().objectId(), null);
		try
		{
			final PhantomBackgroundState ready = fixture.ready();
			final PhantomBackgroundOperationKey key = key(fixture, 1, 1, ActionKind.FARM);
			final Progress progress = new Progress(ready.progress().level(), ready.progress().experience() + 10, ready.progress().skillPoints() + 2, ready.progress().experienceBeforeDeath());
			final Vitals vitals = new Vitals(ready.vitals().currentHp() - 1, ready.vitals().maximumHp(), ready.vitals().currentMp(), ready.vitals().maximumMp(), ready.vitals().currentCp(), ready.vitals().maximumCp());
			final Result committed = fixture.transaction().execute(new PhantomBackgroundTransaction.Command(ready, fixture.goal(), key, progress, vitals, ready.position(), new Clock(2, 0, 0), Map.of(PhantomActionFacade.FIXTURE_ITEM_ID, -1L), ready.autoGetSkills()));
			PhantomAssertions.assertEquals(Status.SUCCESS, committed.status(), "Atomic canonical batch failed.");
			final Result duplicate = fixture.transaction().execute(new PhantomBackgroundTransaction.Command(ready, fixture.goal(), key, progress, vitals, ready.position(), new Clock(2, 0, 0), Map.of(PhantomActionFacade.FIXTURE_ITEM_ID, -1L), ready.autoGetSkills()));
			PhantomAssertions.assertEquals(Status.IDEMPOTENT, duplicate.status(), "Exact duplicate operation was not idempotent.");
			assertCharacter(fixture.characterObjectId(), progress.experience(), progress.skillPoints(), vitals.currentHp());
		}
		finally
		{
			fixture.close();
		}
	}

	private void testPrecommitFaults() throws Exception
	{
		final List<FaultPoint> points = List.of(FaultPoint.AFTER_PROFILE_LOCK, FaultPoint.AFTER_GOAL_LOCK, FaultPoint.AFTER_BACKGROUND_LOCK, FaultPoint.AFTER_CHARACTER_LOCK, FaultPoint.AFTER_SKILL_LOCKS, FaultPoint.AFTER_ITEM_LOCKS, FaultPoint.AFTER_CANONICAL_WRITES, FaultPoint.BEFORE_OPERATION_COMMIT);
		for (FaultPoint point : points)
		{
			final AtomicInteger released = new AtomicInteger();
			final PhantomBackgroundTransaction.ObjectIdAllocator ids = allocator(released);
			final PhantomBackgroundTransaction transaction = new PhantomBackgroundTransaction(DatabaseFactory::getConnection, ids, actual ->
			{
				if (actual == point)
				{
					throw new InjectedFailure();
				}
			});
			final Fixture fixture = createFixture(_environment.primary().objectId(), transaction);
			try
			{
				final PhantomBackgroundState ready = fixture.ready();
				final Result failed = transaction.execute(new PhantomBackgroundTransaction.Command(ready, fixture.goal(), key(fixture, 1, 1, ActionKind.FARM), ready.progress(), ready.vitals(), ready.position(), new Clock(2, 0, 0), Map.of(), ready.autoGetSkills()));
				PhantomAssertions.assertTrue(!failed.successful(), "Injected precommit fault unexpectedly succeeded: " + point);
				PhantomAssertions.assertEquals(ready, fixture.transaction().load(fixture.profileId()).state(), "Precommit rollback changed durable state: " + point);
			}
			finally
			{
				fixture.close();
			}
		}
	}

	private void testVerifyPending() throws Exception
	{
		final AtomicInteger attempts = new AtomicInteger();
		final PhantomBackgroundTransaction transaction = new PhantomBackgroundTransaction(DatabaseFactory::getConnection, allocator(new AtomicInteger()), point ->
		{
			if ((point == FaultPoint.BEFORE_VERIFY_COMMIT) && (attempts.getAndIncrement() == 0))
			{
				throw new InjectedFailure();
			}
		});
		final Fixture fixture = createFixture(_environment.primary().objectId(), transaction);
		try
		{
			final PhantomBackgroundState ready = fixture.ready();
			final Result uncertain = transaction.execute(new PhantomBackgroundTransaction.Command(ready, fixture.goal(), key(fixture, 1, 1, ActionKind.FARM), ready.progress(), ready.vitals(), ready.position(), new Clock(2, 0, 0), Map.of(), ready.autoGetSkills()));
			PhantomAssertions.assertEquals(Status.POST_COMMIT_VERIFICATION_FAILED, uncertain.status(), "Verification fault did not expose pending outcome.");
			final Result restarted = new PhantomBackgroundTransaction().reconcileVerifyPending(fixture.profileId(), fixture.characterObjectId());
			PhantomAssertions.assertEquals(Status.SUCCESS, restarted.status(), "Restart did not reconcile VERIFY_PENDING.");
			PhantomAssertions.assertEquals(State.READY, restarted.state().state(), "Restart promoted to the wrong state.");
			try (Connection connection = DatabaseFactory.getConnection();
				PreparedStatement statement = connection.prepareStatement("UPDATE characters SET curHp = curHp - 1 WHERE charId = ?"))
			{
				statement.setInt(1, fixture.characterObjectId());
				statement.executeUpdate();
			}
			final Result inconsistent = new PhantomBackgroundTransaction().reconcileVerifyPending(fixture.profileId(), fixture.characterObjectId());
			PhantomAssertions.assertEquals(Status.INCONSISTENT, inconsistent.status(), "Canonical mismatch did not fail-stop as INCONSISTENT.");
		}
		finally
		{
			fixture.close();
		}
	}

	private void testSubclassIsolation() throws Exception
	{
		final int objectId = _environment.observer().objectId();
		try (Connection connection = DatabaseFactory.getConnection();
			PreparedStatement statement = connection.prepareStatement("INSERT INTO character_subclasses (charId,class_id,exp,sp,level,class_index) VALUES (?,1,100,20,2,1)"))
		{
			statement.setInt(1, objectId);
			statement.executeUpdate();
		}
		final Fixture fixture = createFixture(objectId, null, 1, 1, 2, 100, 20);
		try
		{
			final long mainExperience = scalarLong("SELECT exp FROM characters WHERE charId = ?", objectId);
			final PhantomBackgroundState ready = fixture.ready();
			final Progress progress = new Progress(2, 120, 25, ready.progress().experienceBeforeDeath());
			final Result result = fixture.transaction().execute(new PhantomBackgroundTransaction.Command(ready, fixture.goal(), key(fixture, 1, 1, ActionKind.FARM), progress, ready.vitals(), ready.position(), new Clock(2, 0, 0), Map.of(), exactAutoGetSkills(ready.identity(), progress.level())));
			PhantomAssertions.assertEquals(Status.SUCCESS, result.status(), "Subclass batch failed.");
			PhantomAssertions.assertEquals(mainExperience, scalarLong("SELECT exp FROM characters WHERE charId = ?", objectId), "Subclass batch contaminated base EXP.");
			PhantomAssertions.assertEquals(120L, scalarLong("SELECT exp FROM character_subclasses WHERE charId = ? AND class_index = 1", objectId), "Subclass EXP was not updated.");
		}
		finally
		{
			fixture.close();
			try (Connection connection = DatabaseFactory.getConnection();
				PreparedStatement statement = connection.prepareStatement("DELETE FROM character_subclasses WHERE charId = ? AND class_index = 1"))
			{
				statement.setInt(1, objectId);
				statement.executeUpdate();
			}
		}
	}

	private void testOperationIdentityGuards() throws Exception
	{
		final Fixture fixture = createFixture(_environment.primary().objectId(), null);
		try
		{
			final PhantomBackgroundState ready = fixture.ready();
			final PhantomBackgroundOperationKey committedKey = new PhantomBackgroundOperationKey(fixture.profileId(), fixture.characterObjectId(), fixture.goal().goalId(), fixture.goal().revision(), 2, 2, ActionKind.FARM, TARGET_NPC_ID, ANCHOR_ID, PhantomBackgroundState.MODEL_VERSION, HASHES);
			final Result committed = fixture.transaction().execute(new PhantomBackgroundTransaction.Command(ready, fixture.goal(), committedKey, ready.progress(), ready.vitals(), ready.position(), new Clock(2, 0, 0), Map.of(), ready.autoGetSkills()));
			PhantomAssertions.assertEquals(Status.SUCCESS, committed.status(), "Operation identity fixture commit failed.");
			final PhantomBackgroundState current = committed.state();

			final PhantomBackgroundOperationKey staleKey = new PhantomBackgroundOperationKey(fixture.profileId(), fixture.characterObjectId(), fixture.goal().goalId(), fixture.goal().revision(), 1, 99, ActionKind.FARM, TARGET_NPC_ID, ANCHOR_ID, PhantomBackgroundState.MODEL_VERSION, HASHES);
			PhantomAssertions.assertEquals(Status.STALE_OPERATION, fixture.transaction().execute(new PhantomBackgroundTransaction.Command(current, fixture.goal(), staleKey, current.progress(), current.vitals(), current.position(), current.clock(), Map.of(), current.autoGetSkills())).status(), "Older activity generation was not rejected.");

			final Hashes changedHashes = new Hashes("knowledge-v2", HASHES.topology(), HASHES.progression(), HASHES.commerce());
			final PhantomBackgroundOperationKey hashKey = new PhantomBackgroundOperationKey(fixture.profileId(), fixture.characterObjectId(), fixture.goal().goalId(), fixture.goal().revision(), 3, 1, ActionKind.FARM, TARGET_NPC_ID, ANCHOR_ID, PhantomBackgroundState.MODEL_VERSION, changedHashes);
			PhantomAssertions.assertEquals(Status.HASH_STALE, fixture.transaction().execute(new PhantomBackgroundTransaction.Command(current, fixture.goal(), hashKey, current.progress(), current.vitals(), current.position(), current.clock(), Map.of(), current.autoGetSkills())).status(), "Changed authority hash was not rejected.");

			final PhantomGoal changedGoal = new PhantomGoal(fixture.goal().goalId(), fixture.goal().goalType(), fixture.goal().status(), fixture.goal().subject(), fixture.goal().target(), fixture.goal().requiredAmount(), fixture.goal().currentAmount(), fixture.goal().acquisitionMethod(), fixture.goal().validSources(), fixture.goal().selectedAnchor(), fixture.goal().purposeKey(), fixture.goal().priority(), fixture.goal().riskBudget(), fixture.goal().expenseBudget(), fixture.goal().deadlineEpochMillis(), fixture.goal().constraints(), fixture.goal().reasonKey(), fixture.goal().revision() + 1);
			final PhantomBackgroundOperationKey changedGoalKey = new PhantomBackgroundOperationKey(fixture.profileId(), fixture.characterObjectId(), changedGoal.goalId(), changedGoal.revision(), 3, 1, ActionKind.FARM, TARGET_NPC_ID, ANCHOR_ID, PhantomBackgroundState.MODEL_VERSION, HASHES);
			PhantomAssertions.assertEquals(Status.GOAL_STALE, fixture.transaction().execute(new PhantomBackgroundTransaction.Command(current, changedGoal, changedGoalKey, current.progress(), current.vitals(), current.position(), current.clock(), Map.of(), current.autoGetSkills())).status(), "Changed persisted goal identity was not rejected.");

			final Progress fabricatedLevel = new Progress(current.progress().level() + 1, current.progress().experience(), current.progress().skillPoints(), current.progress().experienceBeforeDeath());
			final PhantomBackgroundOperationKey progressionKey = new PhantomBackgroundOperationKey(fixture.profileId(), fixture.characterObjectId(), fixture.goal().goalId(), fixture.goal().revision(), 3, 2, ActionKind.FARM, TARGET_NPC_ID, ANCHOR_ID, PhantomBackgroundState.MODEL_VERSION, HASHES);
			PhantomAssertions.assertEquals(Status.PROGRESSION_CONFLICT, fixture.transaction().execute(new PhantomBackgroundTransaction.Command(current, fixture.goal(), progressionKey, fabricatedLevel, current.vitals(), current.position(), current.clock(), Map.of(), current.autoGetSkills())).status(), "Fabricated level/EXP pair was admitted.");
			PhantomAssertions.assertEquals(current, fixture.transaction().load(fixture.profileId()).state(), "Rejected operation identity changed durable state.");
		}
		finally
		{
			fixture.close();
		}
	}

	private void testTransitionFaults() throws Exception
	{
		final Fixture baseline = createFixture(_environment.primary().objectId(), null);
		try
		{
			final PhantomGoal actualGoal = baseline.goal();
			final PhantomGoal staleGoal = new PhantomGoal(actualGoal.goalId(), actualGoal.goalType(), actualGoal.status(), actualGoal.subject(), actualGoal.target(), actualGoal.requiredAmount(), actualGoal.currentAmount(), actualGoal.acquisitionMethod(), actualGoal.validSources(), actualGoal.selectedAnchor(), actualGoal.purposeKey(), actualGoal.priority(), actualGoal.riskBudget(), actualGoal.expenseBudget(), actualGoal.deadlineEpochMillis(), actualGoal.constraints(), actualGoal.reasonKey(), actualGoal.revision() + 1);
			final Result staleCapture = baseline.transaction().captureBaseline(baseline.ready().withState(State.MATERIALIZED), staleGoal);
			PhantomAssertions.assertEquals(Status.GOAL_STALE, staleCapture.status(), "Baseline capture did not lock and reject a stale persisted goal.");
			PhantomAssertions.assertEquals(baseline.ready(), baseline.transaction().load(baseline.profileId()).state(), "Stale baseline capture changed durable state.");
			for (FaultPoint point : List.of(FaultPoint.BEFORE_CAPTURE_COMMIT, FaultPoint.BEFORE_MATERIALIZED_COMMIT))
			{
				final PhantomBackgroundTransaction faulting = new PhantomBackgroundTransaction(DatabaseFactory::getConnection, allocator(new AtomicInteger()), actual ->
				{
					if (actual == point)
					{
						throw new InjectedFailure();
					}
				});
				final Result failed = point == FaultPoint.BEFORE_CAPTURE_COMMIT ? faulting.captureBaseline(baseline.ready().withState(State.MATERIALIZED), baseline.goal()) : faulting.markMaterialized(baseline.profileId(), baseline.characterObjectId());
				PhantomAssertions.assertTrue(!failed.successful(), "Transition fault unexpectedly committed: " + point);
				PhantomAssertions.assertEquals(baseline.ready(), baseline.transaction().load(baseline.profileId()).state(), "Transition fault changed durable state: " + point);
			}
		}
		finally
		{
			baseline.close();
		}

		final PhantomBackgroundTransaction postCommit = new PhantomBackgroundTransaction(DatabaseFactory::getConnection, allocator(new AtomicInteger()), point ->
		{
			if (point == FaultPoint.AFTER_OPERATION_COMMIT)
			{
				throw new InjectedFailure();
			}
		});
		final Fixture fixture = createFixture(_environment.primary().objectId(), postCommit);
		try
		{
			final PhantomBackgroundState ready = fixture.ready();
			final Result unknown = postCommit.execute(new PhantomBackgroundTransaction.Command(ready, fixture.goal(), key(fixture, 1, 1, ActionKind.FARM), ready.progress(), ready.vitals(), ready.position(), new Clock(2, 0, 0), Map.of(), ready.autoGetSkills()));
			PhantomAssertions.assertEquals(Status.COMMIT_OUTCOME_UNKNOWN, unknown.status(), "Post-commit fault did not expose unknown outcome.");
			final Result reconciled = new PhantomBackgroundTransaction().reconcileVerifyPending(fixture.profileId(), fixture.characterObjectId());
			PhantomAssertions.assertEquals(Status.SUCCESS, reconciled.status(), "Fresh restart proof did not resolve post-commit outcome.");
			PhantomAssertions.assertEquals(State.READY, reconciled.state().state(), "Post-commit outcome reconciled to the wrong state.");
		}
		finally
		{
			fixture.close();
		}
	}

	private void testLevelAutoGetAndDropItems() throws Exception
	{
		final Fixture fixture = createFixture(_environment.primary().objectId(), null);
		try
		{
			final PhantomBackgroundState ready = fixture.ready();
			final int crossedLevel = 20;
			final Progress progress = new Progress(crossedLevel, ExperienceData.getInstance().getExpForLevel(crossedLevel), ready.progress().skillPoints(), ready.progress().experienceBeforeDeath());
			final List<AutoGetSkill> desired = exactAutoGetSkills(ready.identity(), crossedLevel);
			final Result committed = fixture.transaction().execute(new PhantomBackgroundTransaction.Command(ready, fixture.goal(), key(fixture, 1, 1, ActionKind.FARM), progress, ready.vitals(), ready.position(), new Clock(2, 0, 0), Map.of(57, 10L, 10, 2L), desired));
			PhantomAssertions.assertEquals(Status.SUCCESS, committed.status(), "Level/auto-get/item canonical batch failed.");
			PhantomAssertions.assertEquals(_environment.primary().fixtureItemBaseline() + 10, scalarLong("SELECT COALESCE(SUM(count),0) FROM items WHERE owner_id = ? AND item_id = 57", fixture.characterObjectId()), "Stackable Adena award diverged.");
			PhantomAssertions.assertEquals(2L, scalarLong("SELECT COUNT(*) FROM items WHERE owner_id = ? AND item_id = 10", fixture.characterObjectId()), "Non-stackable awards did not create exact objects.");
			for (AutoGetSkill skill : desired)
			{
				PhantomAssertions.assertEquals((long) skill.skillLevel(), scalarLong("SELECT skill_level FROM character_skills WHERE charId = ? AND class_index = 0 AND skill_id = " + skill.skillId(), fixture.characterObjectId()), "Auto-get skill crossing diverged for " + skill.skillId());
			}
			final List<AutoGetSkill> fabricated = new ArrayList<>(desired);
			fabricated.add(new AutoGetSkill(9999, 1));
			final PhantomBackgroundState current = committed.state();
			final PhantomBackgroundOperationKey invalidKey = new PhantomBackgroundOperationKey(fixture.profileId(), fixture.characterObjectId(), fixture.goal().goalId(), fixture.goal().revision(), 2, 1, ActionKind.FARM, TARGET_NPC_ID, ANCHOR_ID, PhantomBackgroundState.MODEL_VERSION, HASHES);
			PhantomAssertions.assertEquals(Status.PROGRESSION_CONFLICT, fixture.transaction().execute(new PhantomBackgroundTransaction.Command(current, fixture.goal(), invalidKey, current.progress(), current.vitals(), current.position(), current.clock(), Map.of(), fabricated)).status(), "Manual/non-auto skill was admitted by the canonical writer.");
		}
		finally
		{
			fixture.close();
		}
	}

	private void testLifecycleLoop(int transitions, int ticks) throws Exception
	{
		final RuntimeFixture runtime = createRuntimeFixture(_environment.primary().objectId());
		try
		{
			for (int index = 0; index < transitions; index++)
			{
				PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, runtime.materialization().materialize(runtime.profileId()).status(), "Materialization failed at transition " + index);
				PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, runtime.materialization().dematerialize(runtime.profileId()).status(), "Dematerialization failed at transition " + index);
			}
			for (int tick = 1; tick <= ticks; tick++)
			{
				final var result = runtime.background().farm(runtime.profileId(), runtime.goal(), 1, tick, PhantomActivityState.BACKGROUND, tick);
				PhantomAssertions.assertTrue(result.successful(), "Background tick failed: " + tick + " " + result.reason());
			}
			PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, runtime.materialization().materialize(runtime.profileId()).status(), "Final promotion failed.");
			PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, runtime.materialization().dematerialize(runtime.profileId()).status(), "Final demotion failed.");
			final Result verified = runtime.transaction().reconcileVerifyPending(runtime.profileId(), runtime.characterObjectId());
			PhantomAssertions.assertEquals(Status.SUCCESS, verified.status(), "Final lifecycle state is not canonical.");
			PhantomAssertions.assertEquals(0, runtime.background().snapshot().currentOperations(), "Lifecycle leaked background operations.");
			PhantomAssertions.assertEquals(0, runtime.background().snapshot().currentIdentityLeases(), "Lifecycle leaked background identity leases.");
		}
		finally
		{
			runtime.close();
		}
	}

	private void testDeathRecovery() throws Exception
	{
		final RuntimeFixture runtime = createRuntimeFixture(_environment.primary().objectId());
		try
		{
			final PhantomBackgroundState dead = makeDead(runtime);
			final long itemCount = scalarLong("SELECT COALESCE(SUM(count),0) FROM items WHERE owner_id=? AND item_id=57", runtime.characterObjectId());
			final var recovered = runtime.background().recover(runtime.profileId(), runtime.goal(), PhantomActivityState.WARM);
			PhantomAssertions.assertEquals(OperationStatus.FAIL_GOAL, recovered.status(), "Canonical recovery did not return typed FAIL_GOAL: " + recovered.reason());
			final PhantomBackgroundState ready = runtime.transaction().load(runtime.profileId()).state();
			PhantomAssertions.assertEquals(State.READY, ready.state(), "Recovered canonical state is not READY.");
			PhantomAssertions.assertTrue(ready.vitals().currentHp() > 0, "Recovery did not restore canonical HP.");
			PhantomAssertions.assertEquals(dead.progress().experience(), ready.progress().experience(), "Recovery fabricated EXP.");
			PhantomAssertions.assertEquals(itemCount, scalarLong("SELECT COALESCE(SUM(count),0) FROM items WHERE owner_id=? AND item_id=57", runtime.characterObjectId()), "Recovery fabricated supplies.");
			PhantomAssertions.assertEquals(ready.position().x(), (int) scalarLong("SELECT x FROM characters WHERE charId=?", runtime.characterObjectId()), "Recovery DB X differs from resolved town.");
			PhantomAssertions.assertEquals(ready.position().y(), (int) scalarLong("SELECT y FROM characters WHERE charId=?", runtime.characterObjectId()), "Recovery DB Y differs from resolved town.");
			PhantomAssertions.assertEquals(ready.position().z(), (int) scalarLong("SELECT z FROM characters WHERE charId=?", runtime.characterObjectId()), "Recovery DB Z differs from resolved town.");
			PhantomAssertions.assertEquals(Status.SUCCESS, runtime.transaction().markMaterialized(runtime.profileId(), runtime.characterObjectId()).status(), "Recovered durable state does not admit MATERIALIZED.");
			PhantomAssertions.assertEquals(Status.SUCCESS, runtime.transaction().abortMaterialization(runtime.profileId(), runtime.characterObjectId()).status(), "Recovered durable MATERIALIZED control did not restore READY.");
			Player probe = null;
			try
			{
				probe = Player.load(runtime.characterObjectId());
				PhantomAssertions.assertTrue(probe != null, "Recovered canonical Player probe could not load.");
				PhantomAssertions.assertEquals(ready.progress().level(), probe.getLevel(), "Recovered raw Player level differs.");
				PhantomAssertions.assertEquals(ready.progress().experience(), probe.getExp(), "Recovered raw Player EXP differs.");
				PhantomAssertions.assertEquals(ready.progress().skillPoints(), probe.getSp(), "Recovered raw Player SP differs.");
				PhantomAssertions.assertEquals(ready.vitals().currentHp(), probe.getCurrentHp(), "Recovered raw Player HP differs.");
				PhantomAssertions.assertEquals(ready.position().x(), probe.getX(), "Recovered raw Player X differs.");
				PhantomAssertions.assertEquals(ready.position().y(), probe.getY(), "Recovered raw Player Y differs.");
				PhantomAssertions.assertEquals(ready.position().z(), probe.getZ(), "Recovered raw Player Z differs.");
			}
			finally
			{
				_environment.cleanupLoadedPlayer(probe);
			}
			PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, runtime.materialization().materialize(runtime.profileId()).status(), "Recovered state could not rematerialize.");
			try (var action = runtime.materialization().tryAcquireAction(runtime.profileId()).orElseThrow())
			{
				PhantomAssertions.assertEquals(ready.position().x(), action.player().getX(), "Rematerialized recovery X differs.");
				PhantomAssertions.assertEquals(ready.position().y(), action.player().getY(), "Rematerialized recovery Y differs.");
				PhantomAssertions.assertEquals(ready.position().z(), action.player().getZ(), "Rematerialized recovery Z differs.");
				PhantomAssertions.assertTrue(action.player().getCurrentHp() > 0, "Rematerialized recovery retained zero HP.");
			}
			PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, runtime.materialization().dematerialize(runtime.profileId()).status(), "Recovered rematerialization cleanup failed.");
		}
		finally
		{
			runtime.close();
		}
	}

	private PhantomBackgroundState makeDead(RuntimeFixture runtime) throws Exception
	{
		runtime.materialization().materialize(runtime.profileId());
		runtime.materialization().dematerialize(runtime.profileId());
		final PhantomBackgroundState ready = runtime.transaction().load(runtime.profileId()).state();
		final Vitals deadVitals = new Vitals(0, ready.vitals().maximumHp(), ready.vitals().currentMp(), ready.vitals().maximumMp(), 0, ready.vitals().maximumCp());
		final Progress deadProgress = new Progress(ready.progress().level(), ready.progress().experience(), ready.progress().skillPoints(), ready.progress().experience());
		final Result dead = runtime.transaction().execute(new PhantomBackgroundTransaction.Command(ready, runtime.goal(), key(runtime.fixture(), 2, 1, ActionKind.FARM), deadProgress, deadVitals, ready.position(), new Clock(3, 0, 0), Map.of(), exactAutoGetSkills(ready.identity(), deadProgress.level())));
		PhantomAssertions.assertEquals(Status.SUCCESS, dead.status(), "Causal DEAD state could not be committed.");
		PhantomAssertions.assertEquals(State.DEAD, dead.state().state(), "Zero HP did not promote DEAD.");
		return dead.state();
	}

	private void testStopDrain() throws Exception
	{
		final org.l2jmobius.gameserver.phantoms.PhantomSystem disabled = new org.l2jmobius.gameserver.phantoms.PhantomSystem(new org.l2jmobius.gameserver.config.custom.PhantomPlayersConfig.Settings(false, true, 32));
		PhantomAssertions.assertFalse(disabled.start(), "Disabled PhantomSystem started.");
		PhantomAssertions.assertEquals(org.l2jmobius.gameserver.phantoms.PhantomSystem.State.DISABLED, disabled.snapshot().state(), "Disabled PhantomSystem entered the wrong state.");
		PhantomAssertions.assertEquals(null, disabled.snapshot().background(), "Disabled PhantomSystem created a background component.");
		disabled.shutdown();

		final RuntimeFixture runtime = createRuntimeFixture(_environment.primary().objectId());
		try
		{
			PhantomAssertions.assertTrue(runtime.background().beginStop(), "beginStop failed.");
			PhantomAssertions.assertTrue(runtime.background().finishStop(), "Idle background service did not stop.");
			PhantomAssertions.assertEquals(OperationStatus.RETRY, runtime.background().farm(runtime.profileId(), runtime.goal(), 1, 1, PhantomActivityState.BACKGROUND, 1).status(), "Stopped service admitted work.");
		}
		finally
		{
			runtime.close();
		}
	}

	private void testGoalContract()
	{
		final PhantomGoal goal = goal();
		final PhantomBackgroundGoalSpec spec = PhantomBackgroundGoalSpec.parse(goal);
		PhantomAssertions.assertEquals(TARGET_NPC_ID, spec.npcId(), "Explicit NPC identity changed.");
		PhantomAssertions.assertEquals(ANCHOR_ID, spec.anchorId(), "Explicit anchor identity changed.");
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> PhantomBackgroundGoalSpec.parse(new PhantomGoal(goal.goalId(), goal.goalType(), goal.status(), goal.subject(), new PhantomDomainRef("npc", "101"), goal.requiredAmount(), goal.currentAmount(), goal.acquisitionMethod(), goal.validSources(), goal.selectedAnchor(), goal.purposeKey(), goal.priority(), goal.riskBudget(), goal.expenseBudget(), goal.deadlineEpochMillis(), goal.constraints(), goal.reasonKey(), goal.revision())), "Mismatched target was admitted.");
	}

	private void testDecisionRegistrations() throws Exception
	{
		final RuntimeFixture runtime = createRuntimeFixture(_environment.primary().objectId());
		try
		{
			runtime.materialization().materialize(runtime.profileId());
			runtime.materialization().dematerialize(runtime.profileId());
			final PhantomBackgroundDecision decision = new PhantomBackgroundDecision(runtime.background());
			final PhantomCandidateRegistry candidates = new PhantomCandidateRegistry();
			decision.registerCandidates(candidates);
			candidates.seal();
			PhantomAssertions.assertEquals(List.of(PhantomBackgroundGoalSpec.CANDIDATE_KEY), candidates.snapshot().stream().map(candidate -> candidate.key()).toList(), "Background candidate registration changed.");
			final PhantomStepHandlerRegistry handlers = new PhantomStepHandlerRegistry();
			decision.registerHandlers(handlers);
			handlers.seal();
			PhantomAssertions.assertEquals(java.util.Set.of(PhantomBackgroundGoalSpec.TRAVEL_ACTION, PhantomBackgroundGoalSpec.FARM_ACTION, PhantomBackgroundGoalSpec.RECOVER_ACTION), handlers.snapshot().keySet(), "Background action registration changed.");
			PhantomAssertions.assertFalse(handlers.snapshot().containsKey("progression.learn_skill"), "Goal 015 enabled progression.learn_skill.");
		}
		finally
		{
			runtime.close();
		}
	}

	private void testDecisionExecutionIdentity() throws Exception
	{
		final RuntimeFixture runtime = createRuntimeFixture(_environment.primary().objectId());
		try
		{
			runtime.materialization().materialize(runtime.profileId());
			runtime.materialization().dematerialize(runtime.profileId());
			final PhantomBackgroundDecision decision = new PhantomBackgroundDecision(runtime.background());
			final PhantomCandidateRegistry candidates = new PhantomCandidateRegistry();
			decision.registerCandidates(candidates);
			candidates.seal();
			final PhantomStepHandlerRegistry handlers = new PhantomStepHandlerRegistry();
			decision.registerHandlers(handlers);
			handlers.seal();
			final var candidate = candidates.snapshot().getFirst();
			final PhantomPlanningContext planning = new PhantomPlanningContext(runtime.profileId(), runtime.goal(), PhantomCapabilitySet.empty(), PhantomActivityState.BACKGROUND, 7, 9, 1234, 1);
			final PhantomPlan plan = candidate.planFactory().create(planning);
			final PhantomStepResult result = handlers.snapshot().get(plan.steps().getFirst().actionKey()).execute(new PhantomStepContext(runtime.profileId(), runtime.goal(), plan, plan.steps().getFirst(), PhantomActivityState.BACKGROUND, 7, 9, 1234, 1, () -> false));
			PhantomAssertions.assertEquals(PhantomStepResult.Type.SUCCESS, result.type(), "Background handler did not execute propagated activity identity.");
			final Receipt receipt = runtime.transaction().load(runtime.profileId()).state().receipt();
			PhantomAssertions.assertEquals(7L, receipt.activityGeneration(), "Activity generation did not reach the transaction receipt.");
			PhantomAssertions.assertEquals(9L, receipt.tickSequence(), "Tick sequence did not reach the transaction receipt.");
		}
		finally
		{
			runtime.close();
		}
	}

	private void testIdentityArbitration() throws Exception
	{
		final int objectId = _environment.primary().objectId();
		final PhantomIdentityLeaseRegistry registry = PhantomIdentityLeaseRegistry.getInstance();
		try (var lease = registry.tryAcquire(objectId, PhantomIdentityLeaseRegistry.OwnerKind.BACKGROUND))
		{
			PhantomAssertions.assertTrue(lease != null, "Background identity lease failed.");
			PhantomAssertions.assertTrue(registry.tryAcquire(objectId, PhantomIdentityLeaseRegistry.OwnerKind.REAL_LOGIN) == null, "Real login bypassed background ownership.");
			PhantomAssertions.assertTrue(registry.tryAcquire(objectId, PhantomIdentityLeaseRegistry.OwnerKind.PHANTOM) == null, "Materialization bypassed background ownership.");
		}
	}

	private void testRestartPhases() throws Exception
	{
		testCanonicalBatch();
		testVerifyPending();
		testLifecycleLoop(1, 1);
	}

	private void testProductionAuthorityFixture() throws Exception
	{
		final ProductionFarmSelection farm = productionFarmSelection();
		final PhantomTopologyAnchor anchor = farm.anchor();
		final PhantomGoal goal = goal(farm.npcId(), anchor.id());
		final int objectId = _environment.primary().objectId();
		final CapabilitySelection selection = productionCapability();
		Canonical original = null;
		int originalBaseClass = 0;
		Player player = null;
		try
		{
			original = canonical(objectId);
			originalBaseClass = (int) scalarLong("SELECT base_class FROM characters WHERE charId = ?", objectId);
			try (Connection connection = DatabaseFactory.getConnection();
				PreparedStatement statement = connection.prepareStatement("UPDATE characters SET classid=?,base_class=?,race=?,level=85,exp=? WHERE charId=?"))
			{
				statement.setInt(1, selection.playerClass().getId());
				statement.setInt(2, selection.playerClass().getId());
				statement.setInt(3, selection.playerClass().getRace().ordinal());
				statement.setLong(4, ExperienceData.getInstance().getExpForLevel(85));
				statement.setInt(5, objectId);
				PhantomAssertions.assertEquals(1, statement.executeUpdate(), "Could not configure a real supported Player class.");
			}
			player = Player.load(objectId);
			PhantomAssertions.assertTrue(player != null, "Real Player fixture could not be loaded.");
			player.setXYZInvisible(anchor.point().x(), anchor.point().y(), anchor.point().z());
			final CapabilityRule selected = selection.rule();
			final var selectedSkill = SkillData.getInstance().getSkill(selected.actionSkill().skillId(), selected.actionSkill().skillLevel());
			PhantomAssertions.assertTrue(selectedSkill != null, "Selected production capability skill is absent.");
			player.addSkill(selectedSkill, false);
			PhantomAssertions.assertTrue((player.getKnownSkill(selected.actionSkill().skillId()) != null) && (player.getKnownSkill(selected.actionSkill().skillId()).getLevel() >= selected.actionSkill().skillLevel()), "Selected production capability was not installed on the real Player fixture.");
			final NpcTemplate template = NpcData.getInstance().getTemplate(farm.npcId());
			PhantomAssertions.assertTrue(template != null, "Real current target NPC is absent.");
			final List<DropFact> facts = _production.knowledge().snapshot().dropFactsByNpc().getOrDefault(farm.npcId(), List.of());
			final List<DropHolder> loaderDrops = new ArrayList<>();
			if (template.getDropGroups() != null)
			{
				template.getDropGroups().forEach(group -> loaderDrops.addAll(group.getDropList()));
			}
			if (template.getDropList() != null)
			{
				loaderDrops.addAll(template.getDropList());
			}
			PhantomAssertions.assertEquals(loaderDrops.size(), facts.size(), "Game Knowledge omitted or fabricated a real death drop.");
			for (int index = 0; index < facts.size(); index++)
			{
				final DropFact fact = facts.get(index);
				final DropHolder loaderDrop = loaderDrops.get(index);
				PhantomAssertions.assertEquals(loaderDrop.getItemId(), fact.itemId(), "Production drop item identity/order changed at " + index);
				PhantomAssertions.assertEquals(loaderDrop.getChance(), fact.rawItemChance(), "Production item chance changed at " + index);
				PhantomAssertions.assertEquals(loaderDrop.getMin(), fact.minimumCount(), "Production minimum count changed at " + index);
				PhantomAssertions.assertEquals(loaderDrop.getMax(), fact.maximumCount(), "Production maximum count changed at " + index);
			}
			PhantomAssertions.assertTrue(facts.stream().anyMatch(fact -> ItemData.getInstance().getTemplate(fact.itemId()).hasExImmediateEffect()), "Real target no longer contains immediate-effect ground-loss evidence.");
			final PhantomBackgroundState captured = _production.authority().capture(15001501, player, goal, null);
			final PhantomBackgroundAuthority.FarmInput input = _production.authority().farmInput(captured, PhantomBackgroundGoalSpec.parse(goal));
			PhantomAssertions.assertEquals(PRODUCTION_GROUND_LOSS_ITEM_IDS, input.target().drops().stream().filter(drop -> drop.disposition() == DropDisposition.LEAVE_ON_GROUND).map(Drop::itemId).distinct().sorted().toList(), "Production authority ground-loss classification changed.");
			PhantomAssertions.assertTrue(input.target().drops().stream().anyMatch(drop -> drop.disposition() == DropDisposition.ACQUIRE), "Production authority omitted every ordinary acquired drop.");

			final Set<String> operationKeys = new HashSet<>();
			for (int identity = 1; identity <= 300; identity++)
			{
				final PhantomBackgroundOperationKey key = new PhantomBackgroundOperationKey(15001501, player.getObjectId(), goal.goalId(), goal.revision(), 1, identity, ActionKind.FARM, farm.npcId(), anchor.id(), PhantomBackgroundState.MODEL_VERSION, _production.authority().hashes());
				PhantomAssertions.assertTrue(operationKeys.add(key.digest()), "A production result operation identity collided at " + identity);
			}
			PhantomAssertions.assertEquals(300, operationKeys.size(), "Production result identity corpus is incomplete.");
		}
		finally
		{
			_environment.cleanupLoadedPlayer(player);
			if (original != null)
			{
				restoreCharacter(objectId, original, originalBaseClass);
			}
		}
	}

	private ProductionPlayerFixture openProductionPlayerFixture() throws Exception
	{
		final ProductionFarmSelection farm = productionFarmSelection();
		final CapabilitySelection selection = productionCapability();
		final int objectId = _environment.primary().objectId();
		final Canonical original = canonical(objectId);
		final int originalBaseClass = (int) scalarLong("SELECT base_class FROM characters WHERE charId = ?", objectId);
		Player player = null;
		try
		{
			try (Connection connection = DatabaseFactory.getConnection();
				PreparedStatement statement = connection.prepareStatement("UPDATE characters SET classid=?,base_class=?,race=?,level=85,exp=?,x=?,y=?,z=?,heading=0,online=0 WHERE charId=?"))
			{
				statement.setInt(1, selection.playerClass().getId());
				statement.setInt(2, selection.playerClass().getId());
				statement.setInt(3, selection.playerClass().getRace().ordinal());
				statement.setLong(4, ExperienceData.getInstance().getExpForLevel(85));
				statement.setInt(5, farm.anchor().point().x());
				statement.setInt(6, farm.anchor().point().y());
				statement.setInt(7, farm.anchor().point().z());
				statement.setInt(8, objectId);
				PhantomAssertions.assertEquals(1, statement.executeUpdate(), "Could not configure the production loot Player.");
			}
			final Identity identity = new Identity(PRODUCTION_LOOT_UNBLOCK_SEED, objectId, 0, selection.playerClass().getId(), selection.playerClass().getRace().ordinal());
			ensureAutoGetSkills(identity, exactAutoGetSkills(identity, 85));
			ensureAutoGetSkills(identity, List.of(new AutoGetSkill(selection.rule().actionSkill().skillId(), selection.rule().actionSkill().skillLevel())));
			player = Player.load(objectId);
			PhantomAssertions.assertTrue(player != null, "Production loot real Player could not be loaded.");
			player.setXYZInvisible(farm.anchor().point().x(), farm.anchor().point().y(), farm.anchor().point().z());
			player.setCurrentHp(player.getMaxHp());
			player.setCurrentMp(player.getMaxMp());
			player.setCurrentCp(player.getMaxCp());
			PhantomAssertions.assertTrue((player.getKnownSkill(selection.rule().actionSkill().skillId()) != null) && (player.getKnownSkill(selection.rule().actionSkill().skillId()).getLevel() >= selection.rule().actionSkill().skillLevel()), "Production loot capability is not ready on the real Player.");
			return new ProductionPlayerFixture(player, farm, goal(farm.npcId(), farm.anchor().id()), original, originalBaseClass);
		}
		catch (Throwable failure)
		{
			_environment.cleanupLoadedPlayer(player);
			restoreCharacter(objectId, original, originalBaseClass);
			restorePrimaryInventoryAndSkills(objectId);
			throw failure;
		}
	}

	private static Map<String, String> shippedAutoLootConfig() throws Exception
	{
		final Set<String> keys = Set.of("AutoLootHerbs", "AutoLoot", "AutoLootSlotLimit", "AutoLootItemIds");
		final Map<String, String> values = new LinkedHashMap<>();
		final Path playerConfig = Path.of(System.getProperty("phantom.module.root")).resolve("dist/game/config/Player.ini");
		for (String line : Files.readAllLines(playerConfig, StandardCharsets.UTF_8))
		{
			final int separator = line.indexOf('=');
			if (separator < 0)
			{
				continue;
			}
			final String key = line.substring(0, separator).trim();
			if (keys.contains(key))
			{
				values.put(key, line.substring(separator + 1).trim());
			}
		}
		return Map.copyOf(values);
	}

	private static boolean currentAutoLoot(ItemTemplate item)
	{
		return PlayerConfig.AUTO_LOOT_ITEM_IDS.contains(item.getId()) || (!item.hasExImmediateEffect() && PlayerConfig.AUTO_LOOT) || (item.hasExImmediateEffect() && PlayerConfig.AUTO_LOOT_HERBS);
	}

	private static ItemTemplate firstTimeLimitedOrdinaryItem()
	{
		for (int itemId = 1; itemId <= 50_000; itemId++)
		{
			final ItemTemplate item = ItemData.getInstance().getTemplate(itemId);
			if ((item != null) && !item.hasExImmediateEffect() && (item.getTime() != -1))
			{
				return item;
			}
		}
		throw new AssertionError("Current ItemData has no time-limited ordinary item fixture.");
	}

	private static DropDisposition productionDropDisposition(ItemTemplate item) throws Exception
	{
		final Method method = L2jPhantomBackgroundAuthority.class.getDeclaredMethod("dropDisposition", ItemTemplate.class);
		method.setAccessible(true);
		try
		{
			return (DropDisposition) method.invoke(null, item);
		}
		catch (InvocationTargetException exception)
		{
			if (exception.getCause() instanceof RuntimeException runtime)
			{
				throw runtime;
			}
			throw exception;
		}
	}

	private static Target singleEncounterTarget(List<Drop> drops)
	{
		return new Target(TARGET_NPC_ID, 1, true, 1, 1, 1_000_000, 1, 1, 1, 500, 500, 0, 0, drops, 1);
	}

	private static long largestSuccessfulResidual(PhantomBackgroundState state, PhantomBackgroundAuthority.FarmInput input)
	{
		final PhantomBackgroundModel model = new PhantomBackgroundModel();
		long low = 0;
		long high = PhantomBackgroundModel.MAX_ELAPSED_MILLIS - 1;
		long result = -1;
		while (low <= high)
		{
			final long middle = (low + high) >>> 1;
			final Clock clock = new Clock(state.clock().rngState(), state.clock().residualTravelMillis(), middle);
			final PhantomBackgroundState candidate = state.after(state.progress(), state.vitals(), state.position(), state.inventory(), state.autoGetSkills(), clock, state.receipt());
			final BatchResult batch = model.evaluate(new BatchRequest(candidate, input.target(), input.rewardPolicy(), input.deathPolicy(), input.experienceTable(), input.levelForExperience(), false));
			if (batch.mutated())
			{
				result = middle;
				low = middle + 1;
			}
			else
			{
				high = middle - 1;
			}
		}
		if (result < 0)
		{
			throw new AssertionError("Production model has no residual budget admitting one encounter.");
		}
		return result;
	}

	private static Map<Integer, Long> trackedInventoryCounts(PhantomBackgroundState state)
	{
		final Map<Integer, Long> counts = new LinkedHashMap<>();
		state.inventory().objects().stream().filter(item -> item.location() == ItemLocation.INVENTORY).forEach(item -> counts.merge(item.itemId(), item.count(), Math::addExact));
		return Map.copyOf(counts);
	}

	private CapabilitySelection productionCapability()
	{
		final List<CapabilitySelection> selections = new ArrayList<>();
		for (PlayerClass playerClass : PlayerClass.values())
		{
			final CapabilityRule rule = _production.progression().capabilities(playerClass.getId()).stream().filter(candidate -> supportedCapability(candidate.capabilityKey()) && candidate.requiredEquipmentFamilies().isEmpty() && candidate.requiredItems().isEmpty() && !candidate.summonRequired() && !candidate.servitorRequired()).filter(candidate ->
			{
				final var fact = _production.progression().skill(candidate.actionSkill());
				return (fact != null) && fact.damage() && !fact.pvpOnly() && !fact.suicideAttack() && (fact.hpConsume() == 0) && (fact.itemConsumeId() == 0);
			}).sorted(Comparator.comparingInt(CapabilityRule::rank).reversed().thenComparing(CapabilityRule::stableKey)).findFirst().orElse(null);
			if (rule != null)
			{
				selections.add(new CapabilitySelection(playerClass, rule));
			}
		}
		return selections.stream().sorted(Comparator.comparingInt((CapabilitySelection selection) -> selection.playerClass().getId()).reversed().thenComparing(selection -> selection.rule().stableKey())).findFirst().orElseThrow(() -> new AssertionError("Production progression has no supported background combat capability."));
	}

	private ShotCapabilitySelection productionShotCapability()
	{
		final var equipment = _production.progression().equipment(NO_GRADE_WEAPON_ITEM_ID);
		PhantomAssertions.assertTrue(equipment != null, "Current progression catalog does not classify the no-grade weapon.");
		for (PlayerClass playerClass : PlayerClass.values())
		{
			final CapabilityRule rule = _production.progression().capabilities(playerClass.getId()).stream().filter(candidate -> "combat.melee_damage".equals(candidate.capabilityKey()) && candidate.requiredItems().isEmpty() && !candidate.summonRequired() && !candidate.servitorRequired() && Set.of(equipment.family()).containsAll(candidate.requiredEquipmentFamilies())).filter(candidate ->
			{
				final var fact = _production.progression().skill(candidate.actionSkill());
				return (fact != null) && fact.damage() && !fact.pvpOnly() && !fact.suicideAttack() && (fact.hpConsume() == 0) && (fact.itemConsumeId() == 0);
			}).sorted(Comparator.comparingInt(CapabilityRule::rank).reversed().thenComparing(CapabilityRule::stableKey)).findFirst().orElse(null);
			if (rule != null)
			{
				return new ShotCapabilitySelection(playerClass, rule);
			}
		}
		throw new AssertionError("Current progression corpus has no no-grade physical shot capability.");
	}

	private ProductionFarmSelection productionFarmSelection()
	{
		final var snapshot = _production.knowledge().snapshot();
		final var npc = snapshot.npcById().get(PRODUCTION_TARGET_NPC_ID);
		final PhantomTopologyAnchor anchor = _production.topology().findAnchor(PRODUCTION_FARM_ANCHOR_ID).orElseThrow();
		PhantomAssertions.assertTrue((npc != null) && (npc.kind() == NpcKind.MONSTER) && npc.attackable() && npc.targetable(), "Production explicit farm target is not a real normal monster.");
		PhantomAssertions.assertTrue(snapshot.spawnAreasByNpc().getOrDefault(PRODUCTION_TARGET_NPC_ID, List.of()).stream().anyMatch(area -> (area.instanceId() == 0) && (area.totalConfiguredAmount() > 0) && anchor.nodeId().equals(area.topologyNodeId())), "Production explicit farm target is not spawned at its exact topology anchor.");
		return new ProductionFarmSelection(PRODUCTION_TARGET_NPC_ID, anchor);
	}

	private void testProductionTravel()
	{
		final PhantomTopologyEdge edge = _production.topology().snapshot().edges().stream().filter(PhantomTopologyEdge::backgroundEligible).filter(candidate -> (candidate.fromAnchorId() != null) && (candidate.toAnchorId() != null)).findFirst().orElseThrow();
		final PhantomTopologyAnchor departure = _production.topology().findAnchor(edge.fromAnchorId()).orElseThrow();
		final PhantomTopologyAnchor arrival = _production.topology().findAnchor(edge.toAnchorId()).orElseThrow();
		final int targetNpcId = productionFarmSelection().npcId();
		final PhantomGoal travelGoal = goal(targetNpcId, arrival.id());
		PhantomBackgroundState state = productionState(departure, _production.authority().hashes());
		final PhantomBackgroundAuthority.TravelAdvance first = _production.authority().advanceTravel(state, PhantomBackgroundGoalSpec.parse(travelGoal), PhantomBackgroundService.FARM_TRAVEL_BUDGET_MILLIS);
		PhantomAssertions.assertEquals(PhantomBackgroundAuthority.TravelAdvance.Status.PARTIAL, first.status(), "Real background edge did not preserve a partial travel phase.");
		PhantomAssertions.assertEquals(state.position(), first.position(), "Partial travel moved the canonical position off the committed anchor.");
		state = state.after(state.progress(), state.vitals(), first.position(), state.inventory(), state.autoGetSkills(), first.clock(), state.receipt());
		for (int step = 0; (step < 32) && (state.clock().residualTravelMillis() > 0); step++)
		{
			final PhantomBackgroundAuthority.TravelAdvance advance = _production.authority().advanceTravel(state, PhantomBackgroundGoalSpec.parse(travelGoal), PhantomBackgroundService.FARM_TRAVEL_BUDGET_MILLIS);
			state = state.after(state.progress(), state.vitals(), advance.position(), state.inventory(), state.autoGetSkills(), advance.clock(), state.receipt());
		}
		PhantomAssertions.assertEquals(0L, state.clock().residualTravelMillis(), "Real topology travel did not finish within the bounded edge duration.");
		PhantomAssertions.assertEquals(arrival.id(), state.position().committedAnchorId(), "Completed travel committed the wrong exact anchor.");
		PhantomAssertions.assertEquals(arrival.point().x(), state.position().x(), "Completed travel committed the wrong X coordinate.");

		final PhantomTopologyEdge doorEdge = _production.topology().snapshot().edges().stream().filter(candidate -> (candidate.doorId() != null) && (candidate.fromAnchorId() != null) && (candidate.toAnchorId() != null)).findFirst().orElseThrow();
		final PhantomTopologyQuery closedTopology = new PhantomTopologyQuery(_production.topology().snapshot(), new ClosedDoorBackend(_production.topologyBackend(), doorEdge.doorId()), new PhantomTopologyMetrics());
		final L2jPhantomBackgroundAuthority closedAuthority = _production.authority(closedTopology);
		final PhantomTopologyAnchor closedDeparture = closedTopology.findAnchor(doorEdge.fromAnchorId()).orElseThrow();
		final PhantomGoal closedGoal = goal(targetNpcId, doorEdge.toAnchorId());
		final PhantomBackgroundState closedState = productionState(closedDeparture, closedAuthority.hashes());
		final PhantomBackgroundAuthority.TravelAdvance closed = closedAuthority.advanceTravel(closedState, PhantomBackgroundGoalSpec.parse(closedGoal), 1_000);
		PhantomAssertions.assertTrue((closed.status() == PhantomBackgroundAuthority.TravelAdvance.Status.NO_ROUTE) || (closed.status() == PhantomBackgroundAuthority.TravelAdvance.Status.EDGE_CLOSED), "Closed real door edge was admitted for background travel.");
		PhantomAssertions.assertEquals(closedState.position(), closed.position(), "Closed edge mutated canonical position.");
		PhantomAssertions.assertEquals(closedState.clock(), closed.clock(), "Closed edge consumed residual travel time.");
	}

	private void testModelPerformance(PhantomTestContext context)
	{
		final PhantomBackgroundModel model = new PhantomBackgroundModel();
		final BatchRequest request = request(state(1, 101, State.READY, 100, 100, inventory()), target(1, 1, 0, List.of()));
		long checksum = 0;
		final long started = System.nanoTime();
		for (int index = 0; index < 100_000; index++)
		{
			checksum += model.evaluate(request).encounters();
		}
		final long elapsed = System.nanoTime() - started;
		PhantomAssertions.assertTrue(checksum > 0, "100k model evaluations were optimized away.");
		context.record("background.model100kNanos", elapsed);
	}

	private void testDuplicatePerformance(PhantomTestContext context) throws Exception
	{
		final Fixture fixture = createFixture(_environment.primary().objectId(), null);
		try
		{
			final PhantomBackgroundState ready = fixture.ready();
			final PhantomBackgroundOperationKey key = key(fixture, 1, 1, ActionKind.FARM);
			final PhantomBackgroundTransaction.Command command = new PhantomBackgroundTransaction.Command(ready, fixture.goal(), key, ready.progress(), ready.vitals(), ready.position(), new Clock(2, 0, 0), Map.of(), ready.autoGetSkills());
			PhantomAssertions.assertEquals(Status.SUCCESS, fixture.transaction().execute(command).status(), "Performance receipt setup failed.");
			final long started = System.nanoTime();
			for (int index = 0; index < 10_000; index++)
			{
				PhantomAssertions.assertEquals(Status.IDEMPOTENT, fixture.transaction().execute(command).status(), "Duplicate reconciliation changed at index " + index);
			}
			context.record("background.duplicate10kNanos", System.nanoTime() - started);
		}
		finally
		{
			fixture.close();
		}
	}

	private void testBoundedStructure()
	{
		PhantomAssertions.assertEquals(32, PhantomBackgroundModel.MAX_ENCOUNTERS, "Encounter bound changed.");
		PhantomAssertions.assertEquals(60_000L, PhantomBackgroundModel.MAX_ELAPSED_MILLIS, "Logical batch bound changed.");
		PhantomAssertions.assertEquals(16, PhantomBackgroundModel.MAX_CHANGED_ITEM_OBJECTS, "Changed-object bound changed.");
		PhantomAssertions.assertEquals(8, PhantomBackgroundModel.MAX_NEW_NON_STACKABLE_OBJECTS, "New non-stackable bound changed.");
		PhantomAssertions.assertTrue(PhantomBackgroundService.class.getDeclaredFields().length < 40, "Background coordinator accumulated unbounded infrastructure.");
	}

	private Fixture createFixture(int characterObjectId, PhantomBackgroundTransaction transaction) throws Exception
	{
		final Canonical canonical = canonical(characterObjectId);
		return createFixture(characterObjectId, transaction, 0, canonical.classId(), canonical.level(), canonical.experience(), canonical.skillPoints());
	}

	private Fixture createFixture(int characterObjectId, PhantomBackgroundTransaction supplied, int classIndex, int activeClassId, int level, long experience, long skillPoints) throws Exception
	{
		final PhantomProfile profile = _repository.create(characterObjectId);
		final PhantomGoal goal = goal();
		new PhantomGoalStateStore(_repository).insert(profile.profileId(), goal);
		final PhantomBackgroundTransaction transaction = supplied == null ? new PhantomBackgroundTransaction() : supplied;
		final Canonical canonical = canonical(characterObjectId);
		final Identity identity = new Identity(profile.profileId(), characterObjectId, classIndex, activeClassId, canonical.race());
		final List<AutoGetSkill> autoGetSkills = exactAutoGetSkills(identity, level);
		ensureAutoGetSkills(identity, autoGetSkills);
		final PhantomBackgroundState materialized = new PhantomBackgroundState(
			State.MATERIALIZED,
			identity,
			new Progress(level, experience, skillPoints, canonical.experienceBeforeDeath()),
			new Vitals(canonical.currentHp(), canonical.maximumHp(), canonical.currentMp(), canonical.maximumMp(), canonical.currentCp(), canonical.maximumCp()),
			new Position(0, canonical.x(), canonical.y(), canonical.z(), canonical.heading(), ANCHOR_ID),
			combat(ModelKind.MELEE, 1, 1, 100),
			Loadout.none(),
			new InventoryFacts(List.of(10, 57), List.of(), "", 0, 1_000_000, 0, 100),
			autoGetSkills,
			new Clock(SEED, 0, 0),
			Receipt.empty(),
			HASHES);
		final Result captured = transaction.captureBaseline(materialized, goal);
		PhantomAssertions.assertEquals(Status.SUCCESS, captured.status(), "Fixture baseline capture failed.");
		return new Fixture(profile.profileId(), characterObjectId, goal, transaction, captured.state(), canonical);
	}

	private static void ensureAutoGetSkills(Identity identity, List<AutoGetSkill> skills) throws Exception
	{
		try (Connection connection = DatabaseFactory.getConnection();
			PreparedStatement statement = connection.prepareStatement("INSERT INTO character_skills (charId,skill_id,skill_level,class_index) VALUES (?,?,?,?) ON DUPLICATE KEY UPDATE skill_level=VALUES(skill_level)"))
		{
			for (AutoGetSkill skill : skills)
			{
				statement.setInt(1, identity.characterObjectId());
				statement.setInt(2, skill.skillId());
				statement.setInt(3, skill.skillLevel());
				statement.setInt(4, identity.classIndex());
				statement.addBatch();
			}
			statement.executeBatch();
		}
	}

	private RuntimeFixture createRuntimeFixture(int characterObjectId) throws Exception
	{
		return createRuntimeFixture(characterObjectId, new PhantomBackgroundTransaction(), point ->
		{
		});
	}

	private RuntimeFixture createRuntimeFixture(int characterObjectId, PhantomBackgroundTransaction transaction, org.l2jmobius.gameserver.phantoms.player.PhantomMaterializedPlayer.FailureInjector failureInjector) throws Exception
	{
		final PhantomProfile profile = _repository.create(characterObjectId);
		final PhantomGoal goal = goal();
		final PhantomGoalStateStore goals = new PhantomGoalStateStore(_repository);
		goals.insert(profile.profileId(), goal);
		final AtomicReference<PhantomMaterializationService> materializationRef = new AtomicReference<>();
		final PhantomBackgroundService background = new PhantomBackgroundService(_repository, goals, PhantomIdentityLeaseRegistry.getInstance(), transaction, new FakeAuthority(), new PhantomBackgroundCompetitionRegistry(), noSignals(), materializationRef::get);
		background.start();
		final PhantomMetrics metrics = new PhantomMetrics();
		final PhantomMaterializationService materialization = new PhantomMaterializationService(_repository, PhantomIdentityLeaseRegistry.getInstance(), metrics, new PhantomDiagnosticTrace(false, 64, 16, metrics), 2, failureInjector, background, 5_000, 10_000);
		materialization.start();
		materializationRef.set(materialization);
		return new RuntimeFixture(new Fixture(profile.profileId(), characterObjectId, goal, transaction, null, canonical(characterObjectId)), background, materialization);
	}

	private PhantomMaterializationService materialization(int maximum, org.l2jmobius.gameserver.phantoms.player.PhantomMaterializedPlayer.FailureInjector failureInjector, PhantomMaterializationLifecyclePort lifecycle)
	{
		final PhantomMetrics metrics = new PhantomMetrics();
		return new PhantomMaterializationService(_repository, PhantomIdentityLeaseRegistry.getInstance(), metrics, new PhantomDiagnosticTrace(false, 64, 16, metrics), maximum, failureInjector, lifecycle, 5_000, 10_000);
	}

	private void deleteProfile(PhantomProfile profile)
	{
		_repository.find(profile.profileId()).ifPresent(current -> _repository.delete(current.profileId(), current.rowVersion()));
	}

	private static void updateCharacterOnline(int characterObjectId, int online) throws Exception
	{
		try (Connection connection = DatabaseFactory.getConnection();
			PreparedStatement statement = connection.prepareStatement("UPDATE characters SET online=? WHERE charId=?"))
		{
			statement.setInt(1, online);
			statement.setInt(2, characterObjectId);
			PhantomAssertions.assertEquals(1, statement.executeUpdate(), "Character online fixture update failed.");
		}
	}

	private static PhantomGoal goal()
	{
		return goal(TARGET_NPC_ID, ANCHOR_ID);
	}

	private static PhantomGoal goal(int npcId, String anchorId)
	{
		return goal(npcId, anchorId, Map.of());
	}

	private static PhantomGoal goalWithShot(int shotItemId, int count)
	{
		return goal(PRODUCTION_TARGET_NPC_ID, PRODUCTION_FARM_ANCHOR_ID, Map.of(PhantomBackgroundGoalSpec.SHOT_ITEM, (long) shotItemId, PhantomBackgroundGoalSpec.SHOT_COUNT, (long) count));
	}

	private static PhantomGoal goal(int npcId, String anchorId, Map<String, Long> constraints)
	{
		return new PhantomGoal(15, PhantomBackgroundGoalSpec.GOAL_TYPE, PhantomGoalStatus.ACTIVE, new PhantomDomainRef("profile", "self"), new PhantomDomainRef("npc", Integer.toString(npcId)), 1, 0, "background.farm", List.of(new PhantomDomainRef(PhantomBackgroundGoalSpec.SOURCE_NAMESPACE, npcId + "@" + anchorId)), new PhantomDomainRef(PhantomBackgroundGoalSpec.ANCHOR_NAMESPACE, anchorId), "farm.background", 500, 0, 0, 0, constraints, "background.explicit", 0);
	}

	private static PhantomBackgroundState productionState(PhantomTopologyAnchor anchor, Hashes hashes)
	{
		return new PhantomBackgroundState(State.READY, new Identity(15001501, 15001501, 0, 0, 0), new Progress(1, 0, 0, 0), new Vitals(100, 100, 100, 100, 10, 10), new Position(0, anchor.point().x(), anchor.point().y(), anchor.point().z(), 0, anchor.id()), combat(ModelKind.MELEE, 1, 1, 0), Loadout.none(), inventory(), List.of(), new Clock(SEED, 0, 0), Receipt.empty(), hashes);
	}

	private static boolean supportedCapability(String capabilityKey)
	{
		return "combat.melee_damage".equals(capabilityKey) || "combat.ranged_physical_damage".equals(capabilityKey) || "combat.ranged_magic_damage".equals(capabilityKey);
	}

	private static List<AutoGetSkill> exactAutoGetSkills(Identity identity, int level)
	{
		return new L2jPhantomBackgroundAuthority(() -> null, () -> null, () -> null, () -> null).autoGetSkills(identity, level);
	}

	private static PhantomBackgroundState state(long profileId, int characterObjectId, State state, double hp, double maxHp, InventoryFacts inventory)
	{
		return state(profileId, characterObjectId, state, hp, maxHp, inventory, combat(ModelKind.MELEE, 1, 1, 0), Loadout.none());
	}

	private static PhantomBackgroundState state(long profileId, int characterObjectId, State state, double hp, double maxHp, InventoryFacts inventory, CombatFacts combat, Loadout loadout)
	{
		return new PhantomBackgroundState(state, new Identity(profileId, characterObjectId, 0, 0, 0), new Progress(2, 100, 20, 0), new Vitals(hp, maxHp, 100, 100, 10, 10), new Position(0, 1, 2, 3, 0, ANCHOR_ID), combat, loadout, inventory, List.of(), new Clock(SEED, 0, 0), Receipt.empty(), HASHES);
	}

	private static CombatFacts combat(ModelKind kind, double expMultiplier, double spMultiplier, double hpRegen)
	{
		return new CombatFacts(kind, 1000, 1000, 1000, 1000, 1000, 1000, hpRegen, 0, expMultiplier, spMultiplier, 0, 1, 1, 1, 1);
	}

	private static InventoryFacts inventory()
	{
		return new InventoryFacts(List.of(), List.of(), "model", 0, 100000, 0, 100);
	}

	private static Target target(int level, double experience, double skillPoints, List<Drop> drops)
	{
		return new Target(TARGET_NPC_ID, level, true, 1, 1, 1, 1, 1, 1, 500, 500, experience, skillPoints, drops, 2);
	}

	private static BatchRequest request(PhantomBackgroundState state, Target target)
	{
		return new BatchRequest(state, target, new RewardPolicy(11, 1, 1), deathPolicy(), experienceTable(), levelForExperience(), false);
	}

	private static DeathPolicy deathPolicy()
	{
		return new DeathPolicy()
		{
			@Override
			public double lossPercent(int level)
			{
				return 100;
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
				return (long) (level - 1) * 100;
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

	private static PhantomBackgroundOperationKey key(Fixture fixture, long generation, long tick, ActionKind action)
	{
		return new PhantomBackgroundOperationKey(fixture.profileId(), fixture.characterObjectId(), fixture.goal().goalId(), fixture.goal().revision(), generation, tick, action, TARGET_NPC_ID, ANCHOR_ID, PhantomBackgroundState.MODEL_VERSION, HASHES);
	}

	private static PhantomBackgroundTransaction.ObjectIdAllocator allocator(AtomicInteger releases)
	{
		return new PhantomBackgroundTransaction.ObjectIdAllocator()
		{
			@Override
			public int reserve()
			{
				return IdManager.getInstance().getNextId();
			}

			@Override
			public void release(int objectId)
			{
				releases.incrementAndGet();
				IdManager.getInstance().releaseId(objectId);
			}
		};
	}

	private Canonical canonical(int objectId) throws Exception
	{
		try (Connection connection = DatabaseFactory.getConnection();
			PreparedStatement statement = connection.prepareStatement("SELECT level,exp,expBeforeDeath,sp,curHp,maxHp,curMp,maxMp,curCp,maxCp,x,y,z,heading,classid,race FROM characters WHERE charId=?"))
		{
			statement.setInt(1, objectId);
			try (ResultSet result = statement.executeQuery())
			{
				PhantomAssertions.assertTrue(result.next(), "Fixture character is absent.");
				return new Canonical(result.getInt("level"), result.getLong("exp"), result.getLong("expBeforeDeath"), result.getLong("sp"), result.getDouble("curHp"), result.getDouble("maxHp"), result.getDouble("curMp"), result.getDouble("maxMp"), result.getDouble("curCp"), result.getDouble("maxCp"), result.getInt("x"), result.getInt("y"), result.getInt("z"), result.getInt("heading"), result.getInt("classid"), result.getInt("race"));
			}
		}
	}

	private static void assertCharacter(int objectId, long experience, long skillPoints, double hp) throws Exception
	{
		try (Connection connection = DatabaseFactory.getConnection();
			PreparedStatement statement = connection.prepareStatement("SELECT exp,sp,curHp FROM characters WHERE charId=?"))
		{
			statement.setInt(1, objectId);
			try (ResultSet result = statement.executeQuery())
			{
				PhantomAssertions.assertTrue(result.next(), "Character row disappeared.");
				PhantomAssertions.assertEquals(experience, result.getLong("exp"), "Canonical EXP mismatch.");
				PhantomAssertions.assertEquals(skillPoints, result.getLong("sp"), "Canonical SP mismatch.");
				PhantomAssertions.assertEquals(hp, result.getDouble("curHp"), "Canonical HP mismatch.");
			}
		}
	}

	private static long scalarLong(String sql, int objectId) throws Exception
	{
		try (Connection connection = DatabaseFactory.getConnection();
			PreparedStatement statement = connection.prepareStatement(sql))
		{
			statement.setInt(1, objectId);
			try (ResultSet result = statement.executeQuery())
			{
				PhantomAssertions.assertTrue(result.next(), "Expected scalar row is absent.");
				return result.getLong(1);
			}
		}
	}

	private static void restoreCharacter(int objectId, Canonical canonical, int baseClass) throws Exception
	{
		try (Connection connection = DatabaseFactory.getConnection();
			PreparedStatement statement = connection.prepareStatement("UPDATE characters SET level=?,exp=?,expBeforeDeath=?,sp=?,curHp=?,maxHp=?,curMp=?,maxMp=?,curCp=?,maxCp=?,x=?,y=?,z=?,heading=?,classid=?,base_class=?,race=? WHERE charId=?"))
		{
			statement.setInt(1, canonical.level());
			statement.setLong(2, canonical.experience());
			statement.setLong(3, canonical.experienceBeforeDeath());
			statement.setLong(4, canonical.skillPoints());
			statement.setDouble(5, canonical.currentHp());
			statement.setDouble(6, canonical.maximumHp());
			statement.setDouble(7, canonical.currentMp());
			statement.setDouble(8, canonical.maximumMp());
			statement.setDouble(9, canonical.currentCp());
			statement.setDouble(10, canonical.maximumCp());
			statement.setInt(11, canonical.x());
			statement.setInt(12, canonical.y());
			statement.setInt(13, canonical.z());
			statement.setInt(14, canonical.heading());
			statement.setInt(15, canonical.classId());
			statement.setInt(16, baseClass);
			statement.setInt(17, canonical.race());
			statement.setInt(18, objectId);
			PhantomAssertions.assertEquals(1, statement.executeUpdate(), "Real Player fixture restore did not affect exactly one row.");
		}
	}

	private void restorePrimaryInventoryAndSkills(int objectId) throws Exception
	{
		final List<Integer> removedObjectIds = new ArrayList<>();
		try (Connection connection = DatabaseFactory.getConnection();
			PreparedStatement select = connection.prepareStatement("SELECT object_id FROM items WHERE owner_id=? AND item_id<>? ORDER BY object_id");
			PreparedStatement delete = connection.prepareStatement("DELETE FROM items WHERE owner_id=? AND item_id<>?"))
		{
			select.setInt(1, objectId);
			select.setInt(2, PhantomActionFacade.FIXTURE_ITEM_ID);
			try (ResultSet rows = select.executeQuery())
			{
				while (rows.next())
				{
					removedObjectIds.add(rows.getInt(1));
				}
			}
			delete.setInt(1, objectId);
			delete.setInt(2, PhantomActionFacade.FIXTURE_ITEM_ID);
			delete.executeUpdate();
		}
		removedObjectIds.forEach(IdManager.getInstance()::releaseId);
		try (Connection connection = DatabaseFactory.getConnection();
			PreparedStatement item = connection.prepareStatement("UPDATE items SET count=? WHERE owner_id=? AND item_id=?");
			PreparedStatement skills = connection.prepareStatement("DELETE FROM character_skills WHERE charId=? AND NOT (class_index=0 AND skill_id=?)");
			PreparedStatement restoreSkill = connection.prepareStatement("INSERT INTO character_skills (charId,skill_id,skill_level,class_index) VALUES (?,?,1,0) ON DUPLICATE KEY UPDATE skill_level=1"))
		{
			item.setLong(1, _environment.primary().fixtureItemBaseline());
			item.setInt(2, objectId);
			item.setInt(3, PhantomActionFacade.FIXTURE_ITEM_ID);
			PhantomAssertions.assertEquals(1, item.executeUpdate(), "Primary fixture item restore failed.");
			skills.setInt(1, objectId);
			skills.setInt(2, _environment.primary().skillId());
			skills.executeUpdate();
			restoreSkill.setInt(1, objectId);
			restoreSkill.setInt(2, _environment.primary().skillId());
			restoreSkill.executeUpdate();
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

	private final class Fixture implements AutoCloseable
	{
		private final long _profileId;
		private final int _characterObjectId;
		private final PhantomGoal _goal;
		private final PhantomBackgroundTransaction _transaction;
		private final PhantomBackgroundState _ready;
		private final Canonical _canonical;

		private Fixture(long profileId, int characterObjectId, PhantomGoal goal, PhantomBackgroundTransaction transaction, PhantomBackgroundState ready, Canonical canonical)
		{
			_profileId = profileId;
			_characterObjectId = characterObjectId;
			_goal = goal;
			_transaction = transaction;
			_ready = ready;
			_canonical = canonical;
		}

		private long profileId()
		{
			return _profileId;
		}

		private int characterObjectId()
		{
			return _characterObjectId;
		}

		private PhantomGoal goal()
		{
			return _goal;
		}

		private PhantomBackgroundTransaction transaction()
		{
			return _transaction;
		}

		private PhantomBackgroundState ready()
		{
			return _ready;
		}

		@Override
		public void close() throws Exception
		{
			final Optional<PhantomProfile> profile = _repository.find(_profileId);
			if (profile.isPresent())
			{
				_repository.delete(_profileId, profile.get().rowVersion());
			}
			try (Connection connection = DatabaseFactory.getConnection();
				PreparedStatement statement = connection.prepareStatement("UPDATE characters SET level=?,exp=?,expBeforeDeath=?,sp=?,curHp=?,maxHp=?,curMp=?,maxMp=?,curCp=?,maxCp=?,x=?,y=?,z=?,heading=?,classid=?,race=? WHERE charId=?"))
			{
				statement.setInt(1, _canonical.level());
				statement.setLong(2, _canonical.experience());
				statement.setLong(3, _canonical.experienceBeforeDeath());
				statement.setLong(4, _canonical.skillPoints());
				statement.setDouble(5, _canonical.currentHp());
				statement.setDouble(6, _canonical.maximumHp());
				statement.setDouble(7, _canonical.currentMp());
				statement.setDouble(8, _canonical.maximumMp());
				statement.setDouble(9, _canonical.currentCp());
				statement.setDouble(10, _canonical.maximumCp());
				statement.setInt(11, _canonical.x());
				statement.setInt(12, _canonical.y());
				statement.setInt(13, _canonical.z());
				statement.setInt(14, _canonical.heading());
				statement.setInt(15, _canonical.classId());
				statement.setInt(16, _canonical.race());
				statement.setInt(17, _characterObjectId);
				statement.executeUpdate();
			}
			final long fixtureItemBaseline = _characterObjectId == _environment.primary().objectId() ? _environment.primary().fixtureItemBaseline() : _environment.observer().fixtureItemBaseline();
			final List<Integer> extraObjectIds = new ArrayList<>();
			try (Connection connection = DatabaseFactory.getConnection();
				PreparedStatement statement = connection.prepareStatement("SELECT object_id FROM items WHERE owner_id=? AND item_id<>? ORDER BY object_id"))
			{
				statement.setInt(1, _characterObjectId);
				statement.setInt(2, PhantomActionFacade.FIXTURE_ITEM_ID);
				try (ResultSet rows = statement.executeQuery())
				{
					while (rows.next())
					{
						extraObjectIds.add(rows.getInt(1));
					}
				}
			}
			try (Connection connection = DatabaseFactory.getConnection();
				PreparedStatement statement = connection.prepareStatement("DELETE FROM items WHERE owner_id=? AND item_id<>?"))
			{
				statement.setInt(1, _characterObjectId);
				statement.setInt(2, PhantomActionFacade.FIXTURE_ITEM_ID);
				statement.executeUpdate();
			}
			extraObjectIds.forEach(objectId -> IdManager.getInstance().releaseId(objectId));
			try (Connection connection = DatabaseFactory.getConnection();
				PreparedStatement statement = connection.prepareStatement("UPDATE items SET count=? WHERE owner_id=? AND item_id=?"))
			{
				statement.setLong(1, fixtureItemBaseline);
				statement.setInt(2, _characterObjectId);
				statement.setInt(3, PhantomActionFacade.FIXTURE_ITEM_ID);
				PhantomAssertions.assertEquals(1, statement.executeUpdate(), "Fixture item baseline restore did not affect exactly one row.");
			}
			try (Connection connection = DatabaseFactory.getConnection();
				PreparedStatement statement = connection.prepareStatement("DELETE FROM character_skills WHERE charId=? AND NOT (class_index=0 AND skill_id=?)"))
			{
				statement.setInt(1, _characterObjectId);
				statement.setInt(2, _environment.primary().skillId());
				statement.executeUpdate();
			}
		}
	}

	private final class ProductionPlayerFixture implements AutoCloseable
	{
		private Player _player;
		private final ProductionFarmSelection _farm;
		private final PhantomGoal _goal;
		private final Canonical _original;
		private final int _originalBaseClass;

		private ProductionPlayerFixture(Player player, ProductionFarmSelection farm, PhantomGoal goal, Canonical original, int originalBaseClass)
		{
			_player = player;
			_farm = farm;
			_goal = goal;
			_original = original;
			_originalBaseClass = originalBaseClass;
		}

		private Player player()
		{
			if (_player == null)
			{
				throw new IllegalStateException("Production Player runtime has been released.");
			}
			return _player;
		}

		private ProductionFarmSelection farm()
		{
			return _farm;
		}

		private PhantomGoal goal()
		{
			return _goal;
		}

		private void releaseRuntime()
		{
			if (_player != null)
			{
				_environment.cleanupLoadedPlayer(_player);
				_player = null;
			}
		}

		@Override
		public void close() throws Exception
		{
			final int objectId = _player == null ? _environment.primary().objectId() : _player.getObjectId();
			releaseRuntime();
			restoreCharacter(objectId, _original, _originalBaseClass);
			restorePrimaryInventoryAndSkills(objectId);
		}
	}

	private record RuntimeFixture(Fixture fixture, PhantomBackgroundService background, PhantomMaterializationService materialization) implements AutoCloseable
	{
		private long profileId()
		{
			return fixture.profileId();
		}

		private int characterObjectId()
		{
			return fixture.characterObjectId();
		}

		private PhantomGoal goal()
		{
			return fixture.goal();
		}

		private PhantomBackgroundTransaction transaction()
		{
			return fixture.transaction();
		}

		@Override
		public void close() throws Exception
		{
			if (materialization.find(profileId()).isPresent())
			{
				materialization.dematerialize(profileId());
			}
			materialization.shutdown();
			background.beginStop();
			background.finishStop();
			fixture.close();
		}
	}

	private static final class FakeAuthority implements PhantomBackgroundAuthority
	{
		@Override
		public Hashes hashes()
		{
			return HASHES;
		}

		@Override
		public PhantomBackgroundState capture(long profileId, Player player, PhantomGoal goal, PhantomBackgroundState previous)
		{
			PhantomBackgroundGoalSpec.parse(goal);
			final Identity identity = new Identity(profileId, player.getObjectId(), player.getClassIndex(), player.getActiveClass(), player.getRace().ordinal());
			return new PhantomBackgroundState(State.MATERIALIZED, identity, new Progress(player.getLevel(), player.getExp(), player.getSp(), player.getExpBeforeDeath()), new Vitals(player.getCurrentHp(), player.getMaxHp(), player.getCurrentMp(), player.getMaxMp(), player.getCurrentCp(), player.getMaxCp()), new Position(0, player.getX(), player.getY(), player.getZ(), player.getHeading(), ANCHOR_ID), combat(ModelKind.MELEE, 1, 1, 100), Loadout.none(), new InventoryFacts(List.of(10, 57), List.of(), "", 0, 1_000_000, 0, 100), exactAutoGetSkills(identity, player.getLevel()), previous == null ? new Clock(SEED, 0, 0) : previous.clock(), previous == null ? Receipt.empty() : previous.receipt(), HASHES);
		}

		@Override
		public boolean matchesRuntime(Player player, PhantomBackgroundState state)
		{
			return (player.getObjectId() == state.identity().characterObjectId()) && (player.getLevel() == state.progress().level()) && (player.getExp() == state.progress().experience()) && (player.getSp() == state.progress().skillPoints()) && (Math.abs(player.getCurrentHp() - state.vitals().currentHp()) < 0.000001) && (player.getX() == state.position().x()) && (player.getY() == state.position().y()) && (player.getZ() == state.position().z());
		}

		@Override
		public FarmInput farmInput(PhantomBackgroundState state, PhantomBackgroundGoalSpec goal)
		{
			return new FarmInput(target(1, 0, 0, List.of()), new RewardPolicy(11, 1, 1), deathPolicy(), experienceTable(), levelForExperience(), "test.node", 2);
		}

		@Override
		public TravelAdvance advanceTravel(PhantomBackgroundState state, PhantomBackgroundGoalSpec goal, long elapsedBudgetMillis)
		{
			if (state.position().committedAnchorId().equals(goal.anchorId()))
			{
				return new TravelAdvance(TravelAdvance.Status.AT_DESTINATION, state.position(), state.clock(), "");
			}
			return new TravelAdvance(TravelAdvance.Status.ARRIVED, new Position(0, state.position().x(), state.position().y(), state.position().z(), state.position().heading(), goal.anchorId()), new Clock(state.clock().rngState(), 0, state.clock().residualEncounterMillis()), "test.edge");
		}

		@Override
		public List<AutoGetSkill> autoGetSkills(Identity identity, int level)
		{
			return exactAutoGetSkills(identity, level);
		}
	}

	private record ProductionAuthorityFixture(PhantomGameKnowledgeService knowledgeService, PhantomGameKnowledgeQuery knowledge, PhantomTopologyQuery topology, L2jTopologyValidationBackend topologyBackend, PhantomProgressionCatalog progression, PhantomCommerceCatalog commerce, L2jPhantomBackgroundAuthority authority) implements AutoCloseable
	{
		private static ProductionAuthorityFixture start()
		{
			MapRegionData.getInstance();
			SpawnData.getInstance();
			DoorData.getInstance();
			final L2jTopologyValidationBackend topologyBackend = new L2jTopologyValidationBackend();
			final PhantomTopologySnapshot topologySnapshot = new PhantomTopologyLoader(Path.of("data/phantoms/topology"), topologyBackend, PhantomTopologyPolicy.productionDefaults()).load(1);
			final PhantomTopologyQuery topology = new PhantomTopologyQuery(topologySnapshot, topologyBackend, new PhantomTopologyMetrics());
			final PhantomGameKnowledgePolicy knowledgePolicy = PhantomGameKnowledgePolicy.productionDefaults();
			final L2jGameKnowledgeBackend knowledgeBackend = new L2jGameKnowledgeBackend();
			final PhantomGameKnowledgeService knowledgeService = new PhantomGameKnowledgeService(new PhantomGameKnowledgeBuilder(knowledgeBackend, new PhantomStaticManorParser(Path.of("data/Seeds.xml"), knowledgePolicy), new PhantomCuratedKnowledgeParser(Path.of("data/phantoms/knowledge"), knowledgeBackend, knowledgePolicy), topology, knowledgePolicy));
			if (!knowledgeService.start())
			{
				throw new IllegalStateException("Production Game Knowledge fixture did not start.");
			}
			try
			{
				final PhantomGameKnowledgeQuery knowledge = knowledgeService.query();
				final PhantomProgressionPolicy progressionPolicy = PhantomProgressionPolicy.productionDefaults();
				final L2jProgressionBackend progressionBackend = new L2jProgressionBackend(null, Path.of("."), () -> knowledge);
				final PhantomProgressionCatalog progression = new PhantomProgressionCatalogBuilder().build(progressionBackend.load(progressionPolicy), progressionPolicy);
				final PhantomCommerceCatalog commerce = new PhantomCommerceCatalogLoader(Path.of(".")).load().catalog();
				final L2jPhantomBackgroundAuthority authority = new L2jPhantomBackgroundAuthority(() -> knowledge, () -> topology, () -> progression, () -> commerce);
				return new ProductionAuthorityFixture(knowledgeService, knowledge, topology, topologyBackend, progression, commerce, authority);
			}
			catch (RuntimeException | Error failure)
			{
				knowledgeService.beginStop();
				knowledgeService.finishStop();
				throw failure;
			}
		}

		private L2jPhantomBackgroundAuthority authority(PhantomTopologyQuery topologyOverride)
		{
			return new L2jPhantomBackgroundAuthority(() -> knowledge, () -> topologyOverride, () -> progression, () -> commerce);
		}

		@Override
		public void close()
		{
			knowledgeService.beginStop();
			if (!knowledgeService.finishStop())
			{
				throw new IllegalStateException("Production Game Knowledge fixture did not stop.");
			}
		}
	}

	private record ClosedDoorBackend(PhantomTopologyValidationBackend delegate, int closedDoorId) implements PhantomTopologyValidationBackend
	{
		@Override
		public int mapRegionLocId(int x, int y)
		{
			return delegate.mapRegionLocId(x, y);
		}

		@Override
		public Optional<NpcFact> npc(int npcId)
		{
			return delegate.npc(npcId);
		}

		@Override
		public List<SpawnFact> spawns(int npcId, int maximumResults)
		{
			return delegate.spawns(npcId, maximumResults);
		}

		@Override
		public Optional<DoorFact> door(int doorId)
		{
			return delegate.door(doorId);
		}

		@Override
		public DoorState doorState(int doorId)
		{
			return doorId == closedDoorId ? DoorState.CLOSED : delegate.doorState(doorId);
		}

		@Override
		public boolean sourceExists(String relativeDatapackPath)
		{
			return delegate.sourceExists(relativeDatapackPath);
		}
	}

	private record Canonical(int level, long experience, long experienceBeforeDeath, long skillPoints, double currentHp, double maximumHp, double currentMp, double maximumMp, double currentCp, double maximumCp, int x, int y, int z, int heading, int classId, int race)
	{
	}

	private record CapabilitySelection(PlayerClass playerClass, CapabilityRule rule)
	{
	}

	private record ShotCapabilitySelection(PlayerClass playerClass, CapabilityRule rule)
	{
	}

	private record ProductionFarmSelection(int npcId, PhantomTopologyAnchor anchor)
	{
	}

	private static final class RecordingLifecyclePort implements PhantomMaterializationLifecyclePort
	{
		private final AtomicInteger _beforeCount = new AtomicInteger();
		private final AtomicInteger _successCount = new AtomicInteger();
		private final AtomicInteger _abortCount = new AtomicInteger();
		private final AtomicInteger _activeAttempts = new AtomicInteger();
		private Runnable _before = () ->
		{
		};
		private boolean _afterLoadFailure;

		@Override
		public void beforeMaterialize(long profileId, int characterObjectId)
		{
			_beforeCount.incrementAndGet();
			_activeAttempts.incrementAndGet();
			_before.run();
		}

		@Override
		public void afterPlayerLoad(long profileId, Player player)
		{
			if (_afterLoadFailure)
			{
				throw new InjectedFailure();
			}
		}

		@Override
		public void materializeSucceeded(long profileId, int characterObjectId)
		{
			_successCount.incrementAndGet();
			PhantomAssertions.assertTrue(_activeAttempts.decrementAndGet() >= 0, "Success callback underflowed lifecycle attempts.");
		}

		@Override
		public void materializeAborted(long profileId, int characterObjectId)
		{
			_abortCount.incrementAndGet();
			PhantomAssertions.assertTrue(_activeAttempts.decrementAndGet() >= 0, "Abort callback underflowed lifecycle attempts.");
		}

		@Override
		public void beforeStore(long profileId, Player player)
		{
		}

		@Override
		public void afterStore(long profileId, Player player)
		{
		}

		private void assertTerminal(int before, int success, int abort)
		{
			PhantomAssertions.assertEquals(before, _beforeCount.get(), "beforeMaterialize callback count changed.");
			PhantomAssertions.assertEquals(success, _successCount.get(), "Materialization success terminal count changed.");
			PhantomAssertions.assertEquals(abort, _abortCount.get(), "Materialization abort terminal count changed.");
			PhantomAssertions.assertEquals(before, success + abort, "A successful beforeMaterialize attempt lacks exactly one terminal callback.");
			PhantomAssertions.assertEquals(0, _activeAttempts.get(), "Lifecycle attempt counter leaked.");
		}
	}

	private static final class InjectedFailure extends RuntimeException
	{
		private static final long serialVersionUID = 1L;
	}
}
