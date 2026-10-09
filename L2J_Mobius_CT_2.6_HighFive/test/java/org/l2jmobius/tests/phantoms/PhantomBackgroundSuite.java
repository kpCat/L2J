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
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
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
import java.util.function.IntUnaryOperator;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.config.PlayerConfig;
import org.l2jmobius.gameserver.config.RatesConfig;
import org.l2jmobius.gameserver.config.custom.AutoPlayConfig;
import org.l2jmobius.commons.threads.ThreadPool;
import org.l2jmobius.gameserver.data.xml.DoorData;
import org.l2jmobius.gameserver.data.xml.ExperienceData;
import org.l2jmobius.gameserver.data.xml.ItemData;
import org.l2jmobius.gameserver.data.xml.MapRegionData;
import org.l2jmobius.gameserver.data.xml.NpcData;
import org.l2jmobius.gameserver.data.xml.SkillData;
import org.l2jmobius.gameserver.data.xml.SkillTreeData;
import org.l2jmobius.gameserver.data.xml.SpawnData;
import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.managers.IdManager;
import org.l2jmobius.gameserver.managers.ItemManager;
import org.l2jmobius.gameserver.model.Location;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.localplay.LocalPlayM1Observation;
import org.l2jmobius.gameserver.model.actor.Creature;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.PlayerNativeWork;
import org.l2jmobius.gameserver.model.actor.enums.player.MountType;
import org.l2jmobius.gameserver.model.actor.enums.player.PlayerClass;
import org.l2jmobius.gameserver.model.actor.enums.player.TeleportWhereType;
import org.l2jmobius.gameserver.model.actor.holders.npc.DropHolder;
import org.l2jmobius.gameserver.model.actor.templates.NpcTemplate;
import org.l2jmobius.gameserver.model.item.ItemTemplate;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.model.item.instance.Item;
import org.l2jmobius.gameserver.taskmanagers.AutoPlayTaskManager;
import org.l2jmobius.gameserver.taskmanagers.AutoUseTaskManager;
import org.l2jmobius.gameserver.phantoms.PhantomSystem;
import org.l2jmobius.gameserver.phantoms.PhantomDiagnosticTrace;
import org.l2jmobius.gameserver.phantoms.PhantomMetrics;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionCatalog;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionGoalSpec;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionRecipePlanner;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionRecipePlanner.CraftEvidence;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionService;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionSourcePlanner;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.Candidate;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.Phase;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.QuestBinding;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.ReceiptKind;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.Source;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.TerminalResult;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionStateCodec;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionStore;
import org.l2jmobius.gameserver.phantoms.acquisition.quest.PhantomAcquisitionQuestCatalog;
import org.l2jmobius.gameserver.phantoms.acquisition.quest.PhantomAcquisitionQuestCatalog.Rule;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState;
import org.l2jmobius.gameserver.phantoms.background.L2jPhantomBackgroundAuthority;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundAuthority;
import org.l2jmobius.gameserver.phantoms.background.PhantomNativeContext;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundCompetitionRegistry;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundCatchupState;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundCatchupStore;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundDecision;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundGoalSpec;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundLoginGuard;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundModel;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundModel.BatchRequest;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundModel.BatchResult;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundModel.BatchMode;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundModel.DeathPolicy;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundModel.Drop;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundModel.DropDisposition;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundModel.DropOrigin;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundModel.ExperienceTable;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundModel.LevelForExperience;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundModel.QuestFormula;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundModel.RewardPolicy;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundModel.Target;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundOperationKey;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundOperationKey.AcquisitionIdentity;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundOperationKey.ActionKind;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundService;
import org.l2jmobius.gameserver.phantoms.background.PhantomVisibleAutoPlay;
import org.l2jmobius.gameserver.phantoms.background.PhantomHistoricalBackgroundPlanner;
import org.l2jmobius.gameserver.phantoms.background.PhantomHistoricalBackgroundService;
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
import org.l2jmobius.gameserver.phantoms.background.PhantomLegacyHeadlessRecovery;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundTransaction.FaultPoint;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundTransaction.Result;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundTransaction.Status;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatCapabilityResolver;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatPolicy;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatService;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceCatalog;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceCatalogLoader;
import org.l2jmobius.gameserver.phantoms.decision.PhantomCandidateRegistry;
import org.l2jmobius.gameserver.phantoms.decision.PhantomCapabilitySet;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoal;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDecisionEngine;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateStore;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus;
import org.l2jmobius.gameserver.phantoms.decision.PhantomPlan;
import org.l2jmobius.gameserver.phantoms.decision.PhantomPlanningContext;
import org.l2jmobius.gameserver.phantoms.decision.PhantomStepContext;
import org.l2jmobius.gameserver.phantoms.decision.PhantomStepHandlerRegistry;
import org.l2jmobius.gameserver.phantoms.decision.PhantomStepResult;
import org.l2jmobius.gameserver.phantoms.decision.PhantomUtilitySelector;
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
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.RecipeFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgePolicy;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeQuery;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeService;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomStaticManorParser;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationService;
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
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint;
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
		NATIVE_LIFECYCLE("m1-native-lifecycle", true),
		SERVER_INTEGRATION("background-server-integration", true),
		PERFORMANCE("background-performance", true),
		MATERIALIZATION_ABORT("background-materialization-abort", true),
		QUIESCENCE("background-quiescence", true),
		COMPACT_INVENTORY("background-compact-inventory", true),
		AUTHORITATIVE_SHOTS("background-authoritative-shots", true),
		PRODUCTION_AUDIT("background-production-audit", true),
		RECOVERY_TELEPORT("background-recovery-teleport", true),
		REAL_LOGIN("background-real-login", true),
		POSITION_CANONICALIZATION("background-position-canonicalization", true),
		PRODUCTION_LOOT_UNBLOCK("background-production-loot-unblock", true),
		ACQUISITION_PARITY("acquisition-background-parity", true),
		ACQUISITION_ATOMIC_RESTART("acquisition-atomic-restart", true);

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
	private static final long ACQUISITION_SEED = 21002101L;
	private static final long QUEST_CAP_SEED = 21002102L;
	private static final int TARGET_NPC_ID = 100;
	private static final String ANCHOR_ID = "test.anchor";
	private static final int PRODUCTION_TARGET_NPC_ID = 22859;
	private static final String PRODUCTION_FARM_ANCHOR_ID = "giran.farming.22859";
	private static final String NO_METHOD_BINDING_HASH = "140bedbf9c3f6d56a9846d2ba7088798683f4da0c248231336e6a05679e4fdfe";
	private static final String PARENT_PRODUCTION_TOPOLOGY_HASH = "f8046ed902f024a9181f39b3247d8a6697279db4921ec0a69231c1e9b47cae7f";
	private static final int NO_GRADE_WEAPON_ITEM_ID = 6;
	private static final int NO_GRADE_SOULSHOT_ITEM_ID = 1835;
	private static final int NO_GRADE_SPIRITSHOT_ITEM_ID = 2509;
	private static final List<Integer> PRODUCTION_GROUND_LOSS_ITEM_IDS = List.of(8600, 8601, 8602, 8603, 8604, 8605, 8606, 8607, 8608, 8609, 8610, 8611, 8612, 8613, 8614, 10655, 10656, 10657, 13028);
	private static final Hashes HASHES = new Hashes("knowledge-v1", "topology-v1", "progression-v1", "commerce-v1");

	private final Mode _mode;
	private PhantomHeadlessPlayerTestEnvironment _environment;
	private PhantomProfileRepository _repository;
	private ProductionAuthorityFixture _production;
	private String _retainedSummonCompletion = "";

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
		requireSummonCleanupAdmissible(); PhantomM1NativePhaseChecks.requireNoRetainedCompletion(context);
		PhantomAssertions.assertFalse(PhantomOwnedStoreProcessCrashChecks.retainedCleanupRequired(), "Q14 retained child/snapshot forbids a new native fixture.");
		final boolean questCap = (_mode == Mode.ACQUISITION_ATOMIC_RESTART) && "quest-cap".equals(System.getProperty("phantom.acquisition.focus", ""));
		final long expectedSeed = questCap ? QUEST_CAP_SEED : (_mode == Mode.ACQUISITION_PARITY) || (_mode == Mode.ACQUISITION_ATOMIC_RESTART) ? ACQUISITION_SEED : ((_mode == Mode.PRODUCTION_LOOT_UNBLOCK) || (_mode == Mode.POSITION_CANONICALIZATION) ? PRODUCTION_LOOT_UNBLOCK_SEED : SEED);
		PhantomAssertions.assertEquals(expectedSeed, context.seed(), "Goal 015 mode seed changed.");
		if (_mode == Mode.NATIVE_LIFECYCLE && Set.of("codec", "native-context-contract").contains(System.getProperty("phantom.m1.native.focus", "all"))) { return; }
		if (_mode._database)
		{
			_environment = new PhantomHeadlessPlayerTestEnvironment();
			_environment.initialize(context);
			_repository = PhantomProfileRepository.open();
			deleteStaleTestProfile(_environment.primary().objectId());
			deleteStaleTestProfile(_environment.observer().objectId());
			context.record("background.database", PhantomTestDatabaseGuard.TARGET_DATABASE);
			if ((_mode == Mode.AUTHORITATIVE_SHOTS) || ((_mode == Mode.NATIVE_LIFECYCLE) && Set.of("all", "complete", "producer-closeout", "store", "cast", "async", "checkpoint", "drain", "q266", "producer", "queued", "round3", "loot", "review", "watchdog", "movement", "secondary", "dynamic", "teleport", "buff-reload", "extension", "party-loot", "raw", "delayed", "race", "ai-delay", "ai-cast", "delayed-watchdog", "watchdog-race", "publisher", "pool-restore", "process-crash", "load-observer", "native-context-contract", "native-phase", "native-summon-phase").contains(System.getProperty("phantom.m1.native.focus", "all"))))
			{
				ScriptEngine.getInstance().executeScript(ScriptEngine.MASTER_HANDLER_FILE);
			}
			if (((_mode == Mode.NATIVE_LIFECYCLE) && Set.of("all", "complete", "producer-closeout", "store", "cast", "travel", "geometry", "async", "checkpoint", "drain", "q266", "producer", "queued", "round3", "loot", "review", "watchdog", "movement", "secondary", "dynamic", "teleport", "buff-reload", "extension", "party-loot", "raw", "delayed", "race", "ai-delay", "ai-cast", "delayed-watchdog", "watchdog-race", "publisher", "pool-restore", "process-crash", "load-observer", "native-context-contract", "native-phase", "native-summon-phase").contains(System.getProperty("phantom.m1.native.focus", "all"))) || (_mode == Mode.SERVER_INTEGRATION) || (_mode == Mode.AUTHORITATIVE_SHOTS) || (_mode == Mode.PRODUCTION_AUDIT) || (_mode == Mode.RECOVERY_TELEPORT) || (_mode == Mode.POSITION_CANONICALIZATION) || (_mode == Mode.PRODUCTION_LOOT_UNBLOCK) || (_mode == Mode.ACQUISITION_PARITY) || ((_mode == Mode.ACQUISITION_ATOMIC_RESTART) && "recipe-inventory".equals(System.getProperty("phantom.acquisition.focus", ""))))
			{
				try
				{
					_production = ProductionAuthorityFixture.start();
				}
				catch (RuntimeException exception)
				{
					context.record("background.productionLoadCause", String.valueOf(exception.getCause()));
					throw exception;
				}
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
		requireSummonCleanupAdmissible(); PhantomM1NativePhaseChecks.requireNoRetainedCompletion(context);
		PhantomAssertions.assertFalse(PhantomOwnedStoreProcessCrashChecks.retainedCleanupRequired(), "Q14 retained child/snapshot forbids native or canonical cleanup; private journal retained.");
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
	public void register(PhantomTestRegistry targetRegistry)
	{
		final var registry = new PhantomTestRegistry(id());
		switch (_mode)
		{
			case MODEL -> registerModel(registry);
			case TRANSACTION -> registerTransaction(registry);
			case LIFECYCLE -> registerLifecycle(registry);
			case DECISION -> registerDecision(registry);
			case NATIVE_LIFECYCLE -> registerNativeLifecycle(registry);
			case SERVER_INTEGRATION -> registerServerIntegration(registry);
			case PERFORMANCE -> registerPerformance(registry);
			case MATERIALIZATION_ABORT -> registerMaterializationAbort(registry);
			case QUIESCENCE -> registerQuiescence(registry);
			case COMPACT_INVENTORY -> registerCompactInventory(registry);
			case AUTHORITATIVE_SHOTS -> registerAuthoritativeShots(registry);
			case PRODUCTION_AUDIT -> registerProductionAudit(registry);
			case RECOVERY_TELEPORT -> registerRecoveryTeleport(registry);
			case REAL_LOGIN -> registerRealLogin(registry);
			case POSITION_CANONICALIZATION -> registerPositionCanonicalization(registry);
			case PRODUCTION_LOOT_UNBLOCK -> registerProductionLootUnblock(registry);
			case ACQUISITION_PARITY -> registerAcquisitionParity(registry);
			case ACQUISITION_ATOMIC_RESTART -> registerAcquisitionAtomicRestart(registry);
		}
		for (var entry : registry.orderedTests())
		{
			targetRegistry.add(entry.identity().substring(id().length() + 1), context ->
			{
				requireSummonCleanupAdmissible(); PhantomM1NativePhaseChecks.requireNoRetainedCompletion(context); entry.testCase().run(context);
			});
		}
	}
	private void requireSummonCleanupAdmissible() { PhantomAssertions.assertTrue(_retainedSummonCompletion.isEmpty(), "SUMMON_TEST_RETAINED_CLEANUP_ADMISSION_REFUSED " + _retainedSummonCompletion); }

	private void registerAcquisitionParity(PhantomTestRegistry registry)
	{
		if ("goal015".equals(System.getProperty("phantom.acquisition.focus", "")))
		{
			registry.add("01-ordinary-goal-015-regression", _ -> testAcquisitionOrdinaryRegression());
			return;
		}
		registry.add("01-authoritative-death-drop-parity", _ -> testAcquisitionBackgroundParity(PhantomAcquisitionCatalog.Method.DEATH_DROP));
		registry.add("02-authoritative-spoil-sweep-parity", _ -> testAcquisitionBackgroundParity(PhantomAcquisitionCatalog.Method.SPOIL_SWEEP));
		registry.add("03-capacity-capability-and-death-controls", _ -> testAcquisitionBackgroundControls());
		registry.add("04-ordinary-goal-015-regression", _ -> testAcquisitionOrdinaryRegression());
		registry.add("05-configured-drop-rate-retains-player-multipliers", _ -> testConfiguredDropRateRetainsPlayerMultipliers());
	}

	private void registerAcquisitionAtomicRestart(PhantomTestRegistry registry)
	{
		final String focus = System.getProperty("phantom.acquisition.focus", "");
		if ("eligibility".equals(focus))
		{
			registry.add("01-learned-skill-ledger-subclass-and-rollback", _ -> testAcquisitionEligibilityAndRollback());
			return;
		}
		if ("operation-identity".equals(focus))
		{
			registry.add("01-versioned-operation-identity-and-goal015-digest", _ -> testAcquisitionOperationIdentity());
			return;
		}
		if ("recipe-inventory".equals(focus))
		{
			registry.add("01-exact-background-recipe-inventory-read-boundary", this::testAcquisitionInventoryReadBoundary);
			return;
		}
		if ("quest-cap".equals(focus))
		{
			registry.add("01-real-model-transaction-quest-cap-boundaries", this::testQuestCapBoundaries);
			return;
		}
		registry.add("01-precommit-fault-matrix-is-atomic", _ -> testAcquisitionPrecommitFaults());
		registry.add("02-postcommit-restart-and-exact-replay", _ -> testAcquisitionPostcommitRestart());
		registry.add("03-stale-identity-hash-and-version-guards", _ -> testAcquisitionAtomicGuards());
		registry.add("04-repeated-active-background-conservation", _ -> testAcquisitionRepeatedTransitions());
		registry.add("05-learned-skill-ledger-and-rollback", _ -> testAcquisitionEligibilityAndRollback());
		registry.add("06-versioned-operation-identity-and-goal015-digest", _ -> testAcquisitionOperationIdentity());
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
		registry.add("08-ordinary-spoil-separate-from-death-drops", _ -> testOrdinarySpoil());
		registry.add("09-mixed-rate-empty-random-occurrence-parity031", _ -> testMixedRateOccurrence031());
	}

	private void registerTransaction(PhantomTestRegistry registry)
	{
		registry.add("01-atomic-canonical-batch-and-duplicate", _ -> testCanonicalBatch());
		registry.add("02-precommit-fault-rollback", _ -> testPrecommitFaults());
		registry.add("03-verify-pending-restart-and-inconsistent", _ -> testVerifyPending());
		registry.add("03a-profile13-shaped-materialized-mismatch", _ -> testProfile13ShapedMaterializedMismatch());
		registry.add("03b-attested-legacy-headless-recovery", _ -> testAttestedLegacyHeadlessRecovery());
		registry.add("03d-attested-latent-materialized-recovery", _ -> testAttestedLatentMaterializedRecovery());
		registry.add("03c-legacy-witness-bounded-loader", _ -> testLegacyWitnessLoader());
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

	private void registerNativeLifecycle(PhantomTestRegistry registry)
	{
		final String focus = System.getProperty("phantom.m1.native.focus", "all");
		if (focus.equals("complete") || focus.equals("producer-closeout"))
		{
			final var families = focus.equals("producer-closeout") ? List.of("raw", "delayed", "publisher", "watchdog-race", "ai-delay", "party-loot", "codec") : List.of("all", "async", "checkpoint", "q266", "timers", "closure", "movement", "dynamic", "extension", "race", "party-loot", "raw", "delayed", "publisher", "watchdog-race", "ai-delay", "codec", "loot", "pool-restore", "process-crash", "load-observer", "native-context-contract", "native-phase", "native-summon-phase");
			for (String family : families) { registerNativeLifecycle(registry, family); }
			if (focus.equals("producer-closeout")) { return; }
			registry.add("A09-native-damage-kill-reward-sensor", context -> testNativeRewardCompletion(context, false, false, false, false, false, true));
			registry.add("T-native-terminal-and-continuation", this::testVisibleNativeTravel);
			registry.add("Q10-actual-native-timeout-completion-queues-safe-retry", context -> testNativeRewardCompletion(context, false, true, false, false, false, false, true));
			registry.add("T07-actual-native-dry-MOVE_TO-water-corridor", context -> testNativeRouteScenario(context, false));
			registry.add("T03-actual-native-missing-GK-cooldown", context -> testNativeRouteScenario(context, true));
			registry.add("T08-actual-native-canonical-farm-standpoint", context -> testNativeRouteScenario(context, "standpoint"));
			registry.add("T07-actual-native-wrong-height-canonical", context -> testNativeRouteScenario(context, "height"));
			for (boolean ordinary : List.of(false, true))
			{
				registry.add("Q12-actual-native-shared-party-exp-" + ordinary, context -> testNativeSecondaryRecipients(context, false, ordinary));
				registry.add("Q12-actual-native-third-transfer-hp-" + ordinary, context -> testNativeSecondaryRecipients(context, true, ordinary));
			}
			return;
		}
		registerNativeLifecycle(registry, focus);
	}

	private void registerNativeLifecycle(PhantomTestRegistry registry, String focus)
	{
		if (!Set.of("all", "ownership", "errors", "store", "cast", "travel", "geometry", "async", "checkpoint", "drain", "q266", "timers", "producer", "queued", "p06", "teardown", "round3", "loot", "faults", "closure", "review", "watchdog", "movement", "secondary", "dynamic", "teleport", "buff-reload", "extension", "party-loot", "raw", "delayed", "race", "ai-delay", "ai-cast", "codec", "delayed-watchdog", "watchdog-race", "publisher", "pool-restore", "process-crash", "load-observer", "native-context-contract", "native-phase", "native-summon-phase").contains(focus)) { throw new IllegalArgumentException("Unknown native M1 test focus: " + focus); }
		if (focus.equals("native-context-contract"))
		{
			registry.add("Q14-native-context-unknown-exact-binding-contract", _ -> org.l2jmobius.gameserver.phantoms.PhantomM1NativeContextChecks.unknownAndExactBinding());
			registry.add("Q14-native-context-pending-zero-native-points-contract", _ -> org.l2jmobius.gameserver.phantoms.PhantomM1NativeContextChecks.pendingZeroAndNativePoints());
			registry.add("Q14-native-context-exact-load-witness-contract", _ -> org.l2jmobius.gameserver.phantoms.PhantomM1NativeContextChecks.loadedPointsRequireExactWitness(state(7, 100007, State.READY, 100, 100, inventory())));
			return;
		}
		if (focus.equals("native-phase")) { registry.add("A07-actual-native-cast-MP-regen-bounded-phase", this::testNativeRegenPhase); return; }
		if (focus.equals("native-summon-phase"))
		{
			registry.add("A08-0-ordinary-original-native-Servitor-damage-control", this::testOrdinarySummonPhase);
			registry.add("A08-1-managed-original-native-Servitor-COMBAT", this::testNativeSummonPhase);
			return;
		}
		if (focus.equals("load-observer"))
		{
			registry.add("W-native-load-dirty-arrow-reward-lawful-store", this::testNativeLoadObserver);
			return;
		}
		if (focus.equals("process-crash"))
		{
			for (var boundary : PhantomOwnedStoreProcessCrashChecks.Boundary.values()) { registry.add("Q14-actual-process-halt-reconcile-reload-" + boundary, context -> testNativeProcessCrash(context, boundary)); }
			return;
		}
		if (focus.equals("watchdog-race"))
		{
			for (String kind : List.of("watchdog-claim", "watchdog-registration", "watchdog-stop")) { registry.add("Q07-original-native-" + kind, context -> testNativeDelayedState(context, kind, true)); }
			return;
		}
		if (focus.equals("delayed-watchdog"))
		{
			for (boolean managed : List.of(true, false)) { registry.add("Q12-original-running-native-watchdog-" + managed, context -> testNativeDelayedState(context, "watchdog", managed)); }
			return;
		}
		if (focus.equals("publisher"))
		{
			for (String kind : List.of("status", "icons"))
			{
				for (String mode : List.of("NULL", "INLINE")) { registry.add("Q07-original-native-" + kind + "-" + mode, context -> testNativePublisher(context, kind, mode, false)); }
			}
			for (boolean managed : List.of(true, false))
			{
				registry.add("Q12-stock-buff-eviction-" + managed, context -> testNativePublisher(context, "eviction", "", managed));
				registry.add("Q07-optional-watchdog-confirmation-" + managed, context -> testNativeDelayedState(context, "watchdog-cancel", managed));
			}
			registry.add("Q07-optional-watchdog-earned-confirmation", context -> testNativeDelayedState(context, "watchdog-earned", true));
			return;
		}
		if (focus.equals("codec")) { new PhantomOwnedStoreIntentCodecChecks().register(registry); return; }
		if (focus.equals("ai-delay") || focus.equals("ai-cast"))
		{
			for (boolean managed : List.of(true, false))
			{
				for (String kind : focus.equals("ai-cast") ? List.of("cast") : List.of("ready", "cast", "arrived")) { registry.add("Q12-original-native-AI-delay-" + kind + "-" + managed, context -> testNativeAiDelay(context, kind, managed)); }
			}
			return;
		}
		if (focus.equals("delayed"))
		{
			for (boolean managed : List.of(true, false))
			{
				for (String kind : List.of("sit", "stand", "icons", "watchdog")) { registry.add("Q12-native-delayed-" + kind + "-" + managed, context -> testNativeDelayedState(context, kind, managed)); }
			}
			return;
		}
		if (focus.equals("race"))
		{
			for (var stage : PhantomM1MaterializationRaceChecks.Stage.values())
			{
				for (boolean shutdown : List.of(false, true)) { registry.add("Q15-native-materialization-race-" + stage + "-" + shutdown, context -> testNativeMaterializationRace(context, stage, shutdown, PhantomM1MaterializationRaceChecks.AbortFailure.NONE)); }
			}
			for (var failure : List.of(PhantomM1MaterializationRaceChecks.AbortFailure.ERROR, PhantomM1MaterializationRaceChecks.AbortFailure.RUNTIME)) { registry.add("E04-native-materialization-abort-" + failure, context -> testNativeMaterializationRace(context, PhantomM1MaterializationRaceChecks.Stage.AFTER_PLAYER_LOAD, false, failure)); }
			return;
		}
		if (focus.equals("raw"))
		{
			for (boolean managed : List.of(true, false))
			{
				for (String kind : List.of("follow", "status", "buff-control", "buff-null")) { registry.add("Q12-actual-native-raw-" + kind + "-" + managed, context -> testNativeRawProducer(context, kind, managed)); }
			}
			registry.add("Q07-actual-native-BuffFinish-INLINE-stop-publication", context -> testNativeRawProducer(context, "buff-inline", true));
			return;
		}
		if (focus.equals("party-loot"))
		{
			for (boolean ordinary : List.of(false, true))
			{
				for (int itemId : List.of(57, 1866)) { registry.add("Q12-actual-native-party-pickup-" + itemId + "-" + ordinary, context -> testNativePartyLoot(context, itemId, ordinary)); }
				for (int itemId : List.of(57, 1866)) { registry.add("Q12-actual-native-party-autoloot-" + itemId + "-" + ordinary, context -> testNativePartyAutoLoot(context, itemId, ordinary)); }
			}
			for (boolean sealed : List.of(false, true)) { registry.add("Q12-actual-native-party-late-" + sealed, context -> testNativePartyLateJoin(context, false, sealed)); }
			registry.add("Q12-actual-native-party-late-ordinary", context -> testNativePartyLateJoin(context, true, false));
			return;
		}
		if (focus.equals("extension"))
		{
			registry.add("Q12-stock-positive-buff-actual-materialization-reload", context -> testNativeBuffReload(context, false));
			registry.add("Q12-stock-expired-teardown-buff-does-not-resurrect", context -> testNativeBuffReload(context, true));
			registry.add("Q09-native-production-travel-pending-receipt", context -> testNativeReviewCheckpoint(context, false));
			registry.add("Q09-native-finalized-mismatch-keeps-fence", context -> testNativeReviewCheckpoint(context, true));
			for (String producer : List.of("event", "jail", "residence")) { registry.add("Q12-native-teleport-" + producer, context -> testNativeTeleportProducer(context, producer)); }
			for (String producer : List.of("event", "jail", "residence")) { registry.add("Q12-ordinary-native-teleport-" + producer, context -> testOrdinaryNativeTeleportProducer(context, producer)); }
			return;
		}
		if (focus.equals("dynamic"))
		{
			for (boolean transfer : List.of(false, true))
			{
				registry.add("Q12-dynamic-native-recipient-ordinary-" + transfer, context -> testNativeDynamicRecipients(context, transfer, false, false));
				registry.add("Q12-dynamic-native-recipient-open-" + transfer, context -> testNativeDynamicRecipients(context, transfer, true, false));
				registry.add("Q12-dynamic-native-recipient-sealed-" + transfer, context -> testNativeDynamicRecipients(context, transfer, true, true));
			}
			return;
		}
		if (focus.equals("buff-reload"))
		{
			registry.add("Q12-stock-positive-buff-actual-materialization-reload", context -> testNativeBuffReload(context, false));
			registry.add("Q12-stock-expired-teardown-buff-does-not-resurrect", context -> testNativeBuffReload(context, true));
			return;
		}
		if (focus.equals("teleport"))
		{
			for (String producer : List.of("event", "jail", "residence"))
			{
				registry.add("Q12-native-teleport-" + producer, context -> testNativeTeleportProducer(context, producer));
			}
			return;
		}
		if (focus.equals("secondary"))
		{
			registry.add("T06-actual-native-heal-without-work-debt-reset", context -> testNativeLoot(context, "watchdog"));
			for (boolean ordinary : List.of(false, true))
			{
				registry.add("Q12-actual-native-shared-party-exp-" + ordinary, context -> testNativeSecondaryRecipients(context, false, ordinary));
				registry.add("Q12-actual-native-third-transfer-hp-" + ordinary, context -> testNativeSecondaryRecipients(context, true, ordinary));
			}
			return;
		}
		if (focus.equals("movement")) { registry.add("Q12-actual-native-movement-zone-exit-before-first-effect-removal", this::testNativeMovementExit); return; }
		if (focus.equals("review"))
		{
			registry.add("Q09-native-live-pending-resume-reopens-exact-lifetime", context -> testNativeReviewCheckpoint(context, false));
			registry.add("Q09-native-arrival-finalized-mismatch-keeps-fence", context -> testNativeReviewCheckpoint(context, true));
			return;
		}
		if (focus.equals("watchdog")) { registry.add("T06-actual-native-heal-without-work-debt-reset", context -> testNativeLoot(context, "watchdog")); return; }
		if (focus.equals("faults") || focus.equals("closure"))
		{
			registry.add("Q11-actual-native-timer-preserves-primary-and-secondary", context -> org.l2jmobius.gameserver.phantoms.player.PhantomM1NativeScopeChecks.timerPrimary(context, _environment.primary().objectId()));
			registry.add("Q07-actual-native-scheduler-faults-and-ordinary-control", context -> org.l2jmobius.gameserver.phantoms.player.PhantomM1NativeScopeChecks.scheduler(context, _environment.primary().objectId()));
			registry.add("Q05-actual-native-1000-owner-start-cancel-interleavings", context -> org.l2jmobius.gameserver.phantoms.player.PhantomM1NativeScopeChecks.interleavings(context, _environment.primary().objectId()));
			registry.add("Q08-actual-native-old-queued-and-new-REAL-identity", context -> org.l2jmobius.gameserver.phantoms.player.PhantomM1NativeScopeChecks.identityReuse(context, _environment.primary().objectId()));
			if (focus.equals("closure"))
			{
				registry.add("Q12-actual-native-running-effect-and-buff-reload", context -> testNativeTimerCompletion(context, "buff"));
				for (String producer : List.of("buff-expiry", "zone-effect", "zone-damage")) { registry.add("Q12-actual-native-" + producer, context -> testNativeTimerCompletion(context, producer)); }
				registry.add("Q15-actual-native-early-load-owner-without-lifecycle-monitor", _ -> testNativeEarlyLoadOwnership());
			}
			return;
		}
		if (Set.of("all", "queued", "round3").contains(focus))
		{
			for (String producer : List.of("attack", "cast", "incomingAttack", "incomingCast")) { registry.add("Q03-Q12-native-queued-" + producer, context -> testNativeQueuedWork(context, producer)); }
			if (focus.equals("queued")) { return; }
		}
		if (Set.of("all", "teardown", "round3").contains(focus)) { registry.add("Q15-native-logout-reward-before-final-store", this::testNativeLogoutOrdering); if (focus.equals("teardown")) { return; } }
		if (focus.equals("p06")) { registry.add("P06-ordinary-offline-DELETE-keeps-new-registration", this::testNativeOfflineReplacement); return; }
		if (focus.equals("loot"))
		{
			for (String kind : List.of("pickup20", "pickup150", "autoloot", "protected", "concurrent", "capacity", "weight", "watchdog", "excluded")) { registry.add("L-T06-actual-native-" + kind, context -> testNativeLoot(context, kind)); }
			return;
		}
		if (focus.equals("round3"))
		{
			registry.add("Q10-actual-native-timeout-completion-queues-safe-retry", context -> testNativeRewardCompletion(context, false, true, false, false, false, false, true));
			registry.add("P06-ordinary-offline-DELETE-keeps-new-registration", this::testNativeOfflineReplacement);
			registry.add("T07-actual-native-dry-MOVE_TO-water-corridor", context -> testNativeRouteScenario(context, false));
			registry.add("T03-actual-native-missing-GK-cooldown", context -> testNativeRouteScenario(context, true));
			registry.add("T08-actual-native-canonical-farm-standpoint", context -> testNativeRouteScenario(context, "standpoint"));
			registry.add("T07-actual-native-wrong-height-canonical", context -> testNativeRouteScenario(context, "height"));
			return;
		}
		if (focus.equals("drain")) { registry.add("Q10-actual-native-timeout-completion-queues-safe-retry", context -> testNativeRewardCompletion(context, false, true, false, false, false, false, true)); return; }
		if (focus.equals("geometry"))
		{
			registry.add("T07-actual-native-dry-MOVE_TO-water-corridor", context -> testNativeRouteScenario(context, false));
			registry.add("T03-actual-native-missing-GK-cooldown", context -> testNativeRouteScenario(context, true));
			return;
		}
		if (focus.equals("producer"))
		{
			registry.add("A09-native-damage-kill-reward-sensor", context -> testNativeRewardCompletion(context, false, false, false, false, false, true));
			registry.add("T-native-terminal-and-continuation", this::testVisibleNativeTravel);
			registry.add("Q06-native-TimerHolder-body-drains-after-unregister", this::testNativeTimerCompletion);
			return;
		}
		if (focus.equals("timers"))
		{
			registry.add("Q04-native-QuestTimer-held-body", context -> testNativeTimerCompletion(context, "quest"));
			registry.add("Q05-native-pending-cancel-and-long-repeat", context -> testNativeTimerCompletion(context, "cancel"));
			registry.add("Q06-native-TimerHolder-body-drains-after-unregister", this::testNativeTimerCompletion);
			registry.add("Q06-native-repeating-body-stops-and-drains", context -> testNativeTimerCompletion(context, "repeat"));
			registry.add("Q13-native-zero-earned-child-and-grandchild", context -> testNativeTimerCompletion(context, "chain"));
			return;
		}
		if (focus.equals("pool-restore")) { registry.add("Q12-original-pool4-native-duplicate-merge-restore", this::testNativeDuplicatePoolRestore); return; }
		if (focus.equals("q266")) { registry.add("Q02-actual-stock-Q266-drains-and-persists", context -> testNativeRewardCompletion(context, false, false, true, false, true)); return; }
		if (focus.equals("checkpoint")) { registry.add("Q09-active-native-store-drains-quest-reward", context -> testNativeRewardCompletion(context, false, false, true, true)); return; }
		if (focus.equals("async")) { registry.add("Q01-Q05-delayed-native-kill-quest-reward", context -> testNativeRewardCompletion(context, false, false, true)); return; }
		if (focus.equals("travel")) { registry.add("T-native-terminal-and-continuation", this::testVisibleNativeTravel); return; }
		if (focus.equals("cast")) { registry.add("P09-Q01-Q02-native-mage-reward", context -> testNativeRewardCompletion(context, true, false)); return; }
		if (focus.equals("all") || focus.equals("errors"))
		{
			registry.add("E01-first-cleanup-failure-survives-retry", _ -> testNativeFirstFailure(false));
			registry.add("E05-detached-cleanup-evidence-survives-entry-removal", _ -> testNativeFirstFailure(true));
			registry.add("E06-native-incident-bounds-and-hostile-formatter", _ -> testNativeIncidentBounds());
			registry.add("E02-cleanup-primary-survives-native-task-finalizer", _ -> testNativeCleanupPrimary());
			registry.add("E04-materialize-error-survives-abort-error", _ -> testNativeAbortPrimary());
			registry.add("E03-native-store-primary-survives-boundary-finalizer", _ -> testNativeStorePrimary(false));
			registry.add("E03-native-resume-primary-survives-boundary-finalizer", _ -> testNativeStorePrimary(true));
		}
		if (focus.equals("all") || focus.equals("store"))
		{
			registry.add("Q01-Q02-native-hit-reward-drains-and-persists", context -> testNativeRewardCompletion(context, false, false));
			registry.add("Q01-Q02-native-mage-reward-drains-and-persists", context -> testNativeRewardCompletion(context, true, false));
			registry.add("Q05-native-completion-timeout-retains-ownership", context -> testNativeRewardCompletion(context, false, true));
		}
		if (focus.equals("errors") || focus.equals("store")) { return; }
		registry.add("P07-stale-visible-start-rollback-keeps-current-session", _ -> testNativeSessionOverlap());
		registry.add("P06-ordinary-offline-DELETE-keeps-new-registration", this::testNativeOfflineReplacement);
		registry.add("P08-partial-visible-pair-is-unhealthy-and-repaired", _ -> testNativePartialPair());
		for (Class<?> manager : List.of(AutoPlayTaskManager.class, AutoUseTaskManager.class))
		{
			registry.add("P04-" + manager.getSimpleName() + "-stale-rejection-keeps-replacement", _ -> testNativePolicyReplacement(manager, false));
			registry.add("P05-" + manager.getSimpleName() + "-stale-exception-keeps-pair", _ -> testNativePolicyReplacement(manager, true));
			registry.add("P01-" + manager.getSimpleName() + "-missing-policy-skips-stock-effects", _ -> testNativeMissingPolicy(manager));
		}
	}

	private void testNativeProcessCrash(PhantomTestContext context, PhantomOwnedStoreProcessCrashChecks.Boundary boundary) throws Exception
	{
		PhantomAssertions.assertFalse(PhantomOwnedStoreProcessCrashChecks.retainedCleanupRequired(), "Q14 previous child/snapshot must be reconciled before creating another fixture.");
		final var fixture = openNativeProductionFixture(context, true, new PhantomBackgroundTransaction(), _environment.primary().objectId(), _ -> {}, null, false);
		try
		{
			fixture.visible.stop(fixture.id());
			final var stopped = fixture.materialization.shutdown();
			PhantomAssertions.assertTrue(stopped.failedProfileIds().isEmpty() && fixture.materialization.snapshot().retainedEntries() == 0, "Q14 parent materialization did not drain.");
			fixture.background.beginStop();
			PhantomAssertions.assertTrue(fixture.background.finishStop(), "Q14 parent background did not stop.");
			fixture.engine.beginStop();
			PhantomAssertions.assertTrue(fixture.engine.finishStop(), "Q14 parent decision did not stop.");
			final var journal = context.moduleRoot().resolve(".phantom-local/m1-007-q14-" + boundary.name().toLowerCase(java.util.Locale.ROOT) + "-" + System.nanoTime() + ".bin");
			final var receipt = PhantomOwnedStoreProcessCrashChecks.run(context, fixture.id(), fixture.profile.characterObjectId(), fixture.seed.farm().anchor().id(), boundary, journal);
			context.record("q14." + boundary + ".receipt", receipt);
			PhantomAssertions.assertFalse(PhantomOwnedStoreProcessCrashChecks.retainedCleanupRequired(), "Q14 completed case retained its snapshot.");
			PhantomAssertions.assertFalse(java.nio.file.Files.exists(journal), "Q14 full restored snapshot retained its private journal.");
		}
		finally
		{
			if (!PhantomOwnedStoreProcessCrashChecks.retainedCleanupRequired()) { fixture.close(); }
		}
	}

	private void testNativeLoadObserver(PhantomTestContext context) throws Exception
	{
		final var farm = new ProductionFarmSelection(20534, _production.topology().findAnchor("population.farming.elf.20534").orElseThrow());
		final var seed = openProductionPlayerFixture(farm.anchor(), 7, PlayerClass.ELVEN_FIGHTER, farm);
		NativeProductionFixture owned = null;
		try
		{
			final Player prepared = seed.player();
			final Item bow = prepared.getInventory().addItem(ItemProcessType.REWARD, 13, 1, prepared, this);
			final Item arrows = prepared.getInventory().addItem(ItemProcessType.REWARD, 17, 8, prepared, this);
			PhantomAssertions.assertTrue(bow != null && arrows != null && arrows.getCount() == 8, "Native load observer stock equipment seed failed.");
			prepared.getInventory().equipItem(bow);
			prepared.getInventory().equipItem(arrows);
			final var bridge = new org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationLifecycleBridge();
			owned = ownNativeFixture(context, seed, false, new PhantomBackgroundTransaction(), _ -> {}, bridge, false);
			final var observer = new org.l2jmobius.gameserver.phantoms.PhantomM1NativeLoadObserver(Map.of(owned.id(), owned.profile.characterObjectId()));
			bridge.install(observer);
			PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, owned.materialization.materialize(owned.id()).status(), "Native load observer Player did not materialize.");
			final var monster = owned.monster(true);
			try { org.l2jmobius.gameserver.phantoms.PhantomM1NativeLoadObserverChecks.run(context, owned.id(), owned.materialization, observer, monster); }
			finally { monster.abortAttack(); monster.abortCast(); monster.deleteMe(); }
		}
		finally { if (owned != null) { owned.close(); } else { seed.close(); } }
	}

	private void testNativeDuplicatePoolRestore(PhantomTestContext context) throws Exception
	{
		final String primaryInventory = canonicalInventoryHash(_environment.primary().objectId());
		final String observerInventory = canonicalInventoryHash(_environment.observer().objectId());
		final var farm = new ProductionFarmSelection(20534, _production.topology().findAnchor("population.farming.elf.20534").orElseThrow());
		try (var first = openProductionPlayerFixture(farm.anchor(), 7, PlayerClass.ELVEN_MAGE, farm);
			var third = openCreatedNativePartySeed(context, first.player(), farm))
		{
			PhantomM1PoolRestoreChecks.ordinaryDuplicate(context, third.player(), _environment.primary().objectId(), _environment.observer().objectId(), _environment::cleanupLoadedPlayer);
		}
		finally
		{
			PhantomAssertions.assertEquals(primaryInventory, canonicalInventoryHash(_environment.primary().objectId()), "Native duplicate control changed the existing primary inventory.");
			PhantomAssertions.assertEquals(observerInventory, canonicalInventoryHash(_environment.observer().objectId()), "Native duplicate control changed the existing observer inventory.");
		}
	}

	private void testNativeLoot(PhantomTestContext context, String kind) throws Exception
	{
		try (var fixture = kind.equals("excluded") ? openExcludedGroundFixture(context) : openNativeProductionFixture(context, kind.equals("watchdog")))
		{
			org.l2jmobius.gameserver.model.actor.instance.Monster monster = null;
			Player foreign = null;
			try
			{
				switch (kind)
				{
					case "pickup20", "pickup150" -> PhantomM1LootWatchdogChecks.pickupAndReload(context, fixture.id(), fixture.materialization, fixture.visible, fixture.seed.goal(), fixture.transaction, kind.equals("pickup20") ? 20 : 150, 57);
					case "autoloot" -> { monster = fixture.monster(true); PhantomM1LootWatchdogChecks.autoLootAndReload(context, fixture.id(), fixture.materialization, fixture.seed.goal(), fixture.transaction, monster, 57); }
					case "protected" ->
					{
						monster = fixture.monster(true); foreign = Player.load(_environment.observer().objectId());
						final int ignored = AutoPlayConfig.IGNORED_AUTO_PICK_ITEMS.stream().filter(value -> ItemData.getInstance().getTemplate(value) != null).sorted().findFirst().orElseThrow(() -> new AssertionError("Actual ignored pickup config has no native item fixture."));
						PhantomM1LootWatchdogChecks.protectedAndIgnored(context, fixture.id(), fixture.materialization, fixture.visible, fixture.seed.goal(), foreign, monster, ignored);
					}
					case "concurrent" -> PhantomM1LootWatchdogChecks.pickupDuringCleanup(context, fixture.id(), fixture.materialization, fixture.visible, fixture.seed.goal(), fixture.transaction, 1334);
					case "excluded" ->
					{
						final Player player = fixture.player();
						final var area = _production.topology().findNode(fixture.seed.farm().anchor().nodeId()).orElseThrow().area();
						final int targetX = player.getX() + 20; final int targetY = player.getY() + 20;
						final int targetZ = GeoEngine.getInstance().getHeight(targetX, targetY, player.getZ());
						for (int offset = 0; offset <= 20; offset++)
						{
							final int x = player.getX() + offset; final int y = player.getY() + offset;
							final int z = GeoEngine.getInstance().getHeight(x, y, player.getZ());
							PhantomAssertions.assertTrue(area.contains(new PhantomTopologyPoint(x, y, z, player.getInstanceId())), "INVALID excluded-ground fixture: lawful native farm segment leaves the original polygon.");
						}
						PhantomAssertions.assertTrue(GeoEngine.getInstance().canMoveToTarget(player.getX(), player.getY(), player.getZ(), targetX, targetY, targetZ, player.getInstanceId()), "INVALID excluded-ground fixture: original native farm segment is not reachable.");
						monster = fixture.monster(false);
						monster.getSpawn().setXYZ(targetX, targetY, targetZ);
						monster.spawnMe(targetX, targetY, targetZ);
						context.record("m1.loot.exclusionLawfulSegment", "originalNode=" + fixture.seed.farm().anchor().nodeId() + " source=" + player.getX() + "," + player.getY() + "," + player.getZ() + " target=" + targetX + "," + targetY + "," + targetZ + " originalAreaContainsAll21=true nativeGeo=true toleranceUnchanged=true");
						PhantomM1LootWatchdogChecks.excludedGroundCreated(context, fixture.player(), fixture.materialization, fixture.id(), fixture.visible, fixture.seed.goal(), monster, 1334);
					}
					case "capacity", "weight" ->
					{
						final Player player = fixture.player();
						final boolean weightOnly = kind.equals("weight");
						final int itemId = weightOnly ? 1866 : 875;
						try (var action = fixture.materialization.tryAcquireAction(fixture.id()).orElseThrow())
						{
							if (weightOnly)
							{
								final var item = ItemData.getInstance().getTemplate(itemId);
								PhantomAssertions.assertTrue(item.isStackable() && (item.getWeight() > 0), "Native weight fixture template is invalid.");
								final long preload = (player.getMaxLoad() - player.getCurrentLoad()) / item.getWeight();
								PhantomAssertions.assertTrue(preload > 0, "Native weight fixture has no initial load room.");
								player.getInventory().addItem(ItemProcessType.REWARD, itemId, preload, player, this);
							}
							else
							{
								final int slots = player.getInventoryLimit() - player.getInventory().getNonQuestSize();
								PhantomAssertions.assertTrue((slots > 0) && (slots <= 300) && !ItemData.getInstance().getTemplate(itemId).isStackable(), "Native slot fixture is invalid/unbounded.");
								for (int slot = 0; slot < slots; slot++) { player.getInventory().addItem(ItemProcessType.REWARD, itemId, 1, player, this); }
							}
							PhantomAssertions.assertTrue(player.getCurrentLoad() <= player.getMaxLoad(), "Native inventory fixture itself overweighted the farmer.");
						}
						monster = fixture.monster(true);
						PhantomM1LootWatchdogChecks.blockedInventory(context, fixture.id(), fixture.materialization, fixture.visible, fixture.seed.goal(), itemId, weightOnly, monster);
					}
					case "watchdog" ->
					{
						monster = fixture.monster(false);
						final var learned = fixture.player().getAllSkills().stream().filter(skill -> skill.hasEffectType(org.l2jmobius.gameserver.model.effects.EffectType.HEAL)).sorted(Comparator.comparingInt(org.l2jmobius.gameserver.model.skill.Skill::getId)).findFirst().orElseThrow(() -> new AssertionError("Actual native mage class has no learned HEAL fixture."));
						PhantomM1LootWatchdogChecks.watchdog(context, fixture.id(), fixture.materialization, fixture.visible, fixture.seed.goal(), fixture.clock, monster, learned);
					}
					default -> throw new IllegalArgumentException("Unknown native loot fixture");
				}
			}
			finally { if (monster != null) { monster.abortAttack(); monster.abortCast(); monster.deleteMe(); } if (foreign != null) { _environment.cleanupLoadedPlayer(foreign); } }
		}
	}

	private void testNativeQueuedWork(PhantomTestContext context, String producer) throws Exception
	{
		try (var nativeFixture = openNativeProductionFixture(context, producer.equals("cast")))
		{
			final Player player = nativeFixture.player();
			final var monster = nativeFixture.monster(true);
			try
			{
				switch (producer)
				{
					case "attack" -> PhantomM1QueuedWorkChecks.runAttack(context, player, nativeFixture.materialization, nativeFixture.id(), monster);
					case "cast" -> PhantomM1QueuedWorkChecks.runCast(context, player, nativeFixture.materialization, nativeFixture.id(), monster, player.getKnownSkill(1177));
					case "incomingAttack" -> PhantomM1QueuedWorkChecks.runIncomingAttack(context, player, nativeFixture.materialization, nativeFixture.id(), monster);
					case "incomingCast" -> PhantomM1QueuedWorkChecks.runIncomingCast(context, player, nativeFixture.materialization, nativeFixture.id(), monster, SkillData.getInstance().getSkill(1177, 1));
					default -> throw new IllegalArgumentException("Unknown queued native producer");
				}
			}
			finally { monster.abortAttack(); monster.abortCast(); monster.deleteMe(); }
		}
	}

	private void testNativeLogoutOrdering(PhantomTestContext context) throws Exception
	{
		final var entered = new CountDownLatch(1); final var release = new CountDownLatch(1); final var finished = new CountDownLatch(1);
		final var prepared = new AtomicBoolean(); final var callbackFailure = new AtomicReference<Throwable>();
		final var cleanupResult = new AtomicReference<PhantomMaterializationService.DematerializeResult>();
		try (var runtime = createRuntimeFixture(_environment.primary().objectId(), new PhantomBackgroundTransaction(), point -> { if (point == FailurePoint.BEFORE_STORE_OPERATION) { prepared.set(true); } }))
		{
			PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, runtime.materialization().materialize(runtime.profileId()).status(), "Q15 native fixture materialization failed.");
			final Player player; try (var action = runtime.materialization().tryAcquireAction(runtime.profileId()).orElseThrow()) { player = action.player(); }
			final long before = player.getInventory().getInventoryItemCount(1334, -1);
			final var listener = new org.l2jmobius.gameserver.model.events.listeners.ConsumerEventListener(player, org.l2jmobius.gameserver.model.events.EventType.ON_PLAYER_LOGOUT,
				(org.l2jmobius.gameserver.model.events.holders.actor.player.OnPlayerLogout event) ->
				{
					entered.countDown();
					try { if (!release.await(5, TimeUnit.SECONDS)) { throw new AssertionError("Q15 native logout barrier timed out"); } org.l2jmobius.gameserver.model.script.Quest.giveItems(event.getPlayer(), 1334, 1); }
					catch (Throwable failure) { callbackFailure.set(failure); }
					finally { finished.countDown(); }
				}, this);
			player.addListener(listener);
			final Thread cleanup = new Thread(() -> cleanupResult.set(runtime.materialization().dematerialize(runtime.profileId())), "TEST-native-logout-cleanup");
			try
			{
				cleanup.start();
				PhantomAssertions.assertTrue(entered.await(3, TimeUnit.SECONDS), "Actual native delete/logout event was not observed; Q15 fixture invalid.");
				context.record("m1.Q15.native", "logoutEntered=true beforeStore=" + prepared.get() + " callbackFinished=" + (finished.getCount() == 0));
				PhantomAssertions.assertFalse(prepared.get(), "Native logout earned callback was first published after final store boundary.");
				release.countDown(); PhantomAssertions.assertTrue(finished.await(3, TimeUnit.SECONDS), "Native logout callback did not complete."); cleanup.join(5000);
				PhantomAssertions.assertFalse(cleanup.isAlive(), "Native logout cleanup deadlocked.");
				PhantomAssertions.assertEquals(null, callbackFailure.get(), "Native logout writer failed.");
				PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, cleanupResult.get().status(), "Native logout cleanup failed.");
				final Player loaded = Player.load(player.getObjectId());
				try { PhantomAssertions.assertEquals(before + 1, loaded.getInventory().getInventoryItemCount(1334, -1), "Native logout reward was not captured by canonical reload."); }
				finally { _environment.cleanupLoadedPlayer(loaded); }
			}
			finally { release.countDown(); cleanup.join(5000); player.removeListener(listener); }
		}
	}

	/** Focused Q/L fixture only; manual decision attachment is never counted as W wiring proof. */
	private NativeProductionFixture openNativeProductionFixture(PhantomTestContext context, boolean mage) throws Exception
	{
		return openNativeProductionFixture(context, mage, new PhantomBackgroundTransaction());
	}

	private NativeProductionFixture openNativeProductionFixture(PhantomTestContext context, boolean mage, PhantomBackgroundTransaction transaction) throws Exception
	{
		return openNativeProductionFixture(context, mage, transaction, _environment.primary().objectId());
	}

	private NativeProductionFixture openNativeProductionFixture(PhantomTestContext context, boolean mage, PhantomBackgroundTransaction transaction, int objectId) throws Exception
	{
		return openNativeProductionFixture(context, mage, transaction, objectId, point -> {});
	}

	private NativeProductionFixture openNativeProductionFixture(PhantomTestContext context, boolean mage, PhantomBackgroundTransaction transaction, int objectId,
		org.l2jmobius.gameserver.phantoms.player.PhantomMaterializedPlayer.FailureInjector failures) throws Exception
	{
		return openNativeProductionFixture(context, mage, transaction, objectId, failures, null, true);
	}

	private NativeProductionFixture openNativeProductionFixture(PhantomTestContext context, boolean mage, PhantomBackgroundTransaction transaction, int objectId,
		org.l2jmobius.gameserver.phantoms.player.PhantomMaterializedPlayer.FailureInjector failures, PhantomMaterializationLifecyclePort additional, boolean materializeNow) throws Exception
	{
		final var farm = new ProductionFarmSelection(20534, _production.topology().findAnchor("population.farming.elf.20534").orElseThrow());
		final var seed = openProductionPlayerFixture(farm.anchor(), 7, mage ? PlayerClass.ELVEN_MAGE : PlayerClass.ELVEN_FIGHTER, farm, objectId);
		return ownNativeFixture(context, seed, mage, transaction, failures, additional, materializeNow);
	}

	private NativeProductionFixture openExcludedGroundFixture(PhantomTestContext context) throws Exception
	{
		final var knowledge = _production.knowledge().snapshot();
		int tested = 0;
		for (var npc : knowledge.npcById().values().stream().filter(value -> value.kind() == NpcKind.MONSTER && value.attackable() && value.targetable() && value.level() >= 1 && value.level() <= 8).sorted(Comparator.comparingInt(value -> value.npcId())).toList())
		{
			final var template = NpcData.getInstance().getTemplate(npc.npcId());
			if (template == null || (template.getDropList() != null && template.getDropList().stream().anyMatch(drop -> drop.getItemId() == 1334)) || (template.getDropGroups() != null && template.getDropGroups().stream().anyMatch(group -> group.getDropList().stream().anyMatch(drop -> drop.getItemId() == 1334)))) { continue; }
			for (var original : _production.topology().snapshot().anchors().stream().filter(anchor -> anchor.role() == PhantomTopologyAnchorRole.FARMING && anchor.point().instanceId() == 0)
				.filter(anchor -> knowledge.spawnAreasByNpc().getOrDefault(npc.npcId(), List.of()).stream().anyMatch(area -> area.instanceId() == 0 && area.totalConfiguredAmount() > 0 && anchor.nodeId().equals(area.topologyNodeId())))
				.sorted(Comparator.comparing(PhantomTopologyAnchor::id)).toList())
			{
				if (++tested > 64) { throw new AssertionError("No factual excluded-ground source in the first64 original low-level FARMING pairs."); }
				final var found = PhantomM1LootWatchdogChecks.excludedGroundSource(original);
				if (found.isEmpty()) { continue; }
				final var point = found.orElseThrow();
				final var seeded = new PhantomTopologyAnchor(original.id(), original.role(), original.nodeId(), new PhantomTopologyPoint(point.getX(), point.getY(), point.getZ(), point.getInstanceId()), original.npcId(), original.mapRegionLocId(), original.validationTolerance(), original.tags(), original.sourceRefs());
				final var farm = new ProductionFarmSelection(npc.npcId(), seeded);
				context.record("m1.loot.exclusionSource", "original=" + original.id() + " npc=" + npc.npcId() + " point=" + seeded.point() + " tolerance=" + original.validationTolerance() + " testedPairs=" + tested + " preBaseline=true");
				return ownNativeFixture(context, openProductionPlayerFixture(seeded, 7, PlayerClass.ELVEN_FIGHTER, farm), false, new PhantomBackgroundTransaction(), _ -> {}, null, true);
			}
		}
		throw new AssertionError("No factual original FARMING source supplies both native blocked and reachable TEST rays.");
	}

	private NativeProductionFixture ownNativeFixture(PhantomTestContext context, ProductionPlayerFixture seed, boolean mage, PhantomBackgroundTransaction transaction,
		org.l2jmobius.gameserver.phantoms.player.PhantomMaterializedPlayer.FailureInjector failures, PhantomMaterializationLifecyclePort additional, boolean materializeNow) throws Exception
	{
		final var profile = _repository.create(seed.player().getObjectId());
		final var goals = new PhantomGoalStateStore(_repository); goals.insert(profile.profileId(), seed.goal());
		final var owner = new AtomicReference<PhantomMaterializationService>();
		final var background = new PhantomBackgroundService(_repository, goals, PhantomIdentityLeaseRegistry.getInstance(), transaction, _production.authority(), new PhantomBackgroundCompetitionRegistry(), noSignals(), owner::get);
		final var metrics = new PhantomMetrics();
		final var lifecycle = additional == null ? background : PhantomMaterializationLifecyclePort.chain(additional, background);
		final var materialization = new PhantomMaterializationService(_repository, PhantomIdentityLeaseRegistry.getInstance(), metrics, new PhantomDiagnosticTrace(false, 64, 16, metrics), 1, failures, lifecycle, 5000, 10000);
		owner.set(materialization); background.start(); materialization.start();
		final var candidates = new PhantomCandidateRegistry(); candidates.seal(); final var handlers = new PhantomStepHandlerRegistry(); handlers.seal();
		final var engine = new PhantomDecisionEngine(goals, candidates, handlers, metrics, 1); engine.start(); engine.attach(profile.profileId());
		final var visibleClock = new java.util.concurrent.atomic.AtomicLong(System.nanoTime());
		final var visible = new PhantomVisibleAutoPlay(materialization, () -> engine, _ -> true, visibleClock::get);
		final var result = new NativeProductionFixture(seed, profile, transaction, background, materialization, engine, visible, visibleClock);
		try
		{
			if (mage)
			{
				final var learned = SkillTreeData.getInstance().getCompleteClassSkillTree(PlayerClass.ELVEN_MAGE).values().stream().filter(value -> (value.getSkillId() == 1177) && value.isAutoGet() && (value.getGetLevel() <= 7)).max(Comparator.comparingInt(value -> value.getSkillLevel())).orElseThrow();
				seed.player().addSkill(SkillData.getInstance().getSkill(1177, learned.getSkillLevel()), true);
			}
			seed.player().storeMe();
			final var factualCapture = _production.authority().capture(profile.profileId(), seed.player(), seed.goal(), null);
			final var baselineResult = captureTestBaseline(transaction, factualCapture, seed.goal());
			if (baselineResult.status() != Status.SUCCESS)
			{
				context.record("m1.focusedFixture.baselineMismatch", "status=" + baselineResult.status() + " identity=" + factualCapture.identity() + " progress=" + factualCapture.progress() + " vitals=" + factualCapture.vitals() + " position=" + factualCapture.position() + " canonical=" + canonical(seed.player().getObjectId()));
			}
			PhantomAssertions.assertEquals(Status.SUCCESS, baselineResult.status(), "Focused native factual baseline rejected.");
			seed.releaseRuntime();
			if (materializeNow) { PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, materialization.materialize(profile.profileId()).status(), "Focused native Player materialization failed."); }
			context.record("m1.focusedFixture." + profile.profileId(), "npc20534 anchor=" + seed.farm().anchor().id() + " class=" + (mage ? "ELVEN_MAGE" : "ELVEN_FIGHTER"));
			return result;
		}
		catch (Throwable failure) { try { result.close(); } catch (Throwable cleanup) { if (cleanup != failure) { failure.addSuppressed(cleanup); } } throw failure; }
	}

	private final class NativeProductionFixture implements AutoCloseable
	{
		private final ProductionPlayerFixture seed; private final PhantomProfile profile; private final PhantomBackgroundTransaction transaction;
		private final PhantomBackgroundService background; private final PhantomMaterializationService materialization; private final PhantomDecisionEngine engine;
		private final PhantomVisibleAutoPlay visible; private final java.util.concurrent.atomic.AtomicLong clock;
		private PhantomM1MaterializationRaceChecks.DiscardReceipt expectedDiscard;
		private PhantomM1MaterializationRaceChecks.Hold expectedDiscardHold;
		private PhantomTestContext expectedDiscardContext;
		private NativeProductionFixture(ProductionPlayerFixture seed, PhantomProfile profile, PhantomBackgroundTransaction transaction, PhantomBackgroundService background, PhantomMaterializationService materialization, PhantomDecisionEngine engine, PhantomVisibleAutoPlay visible, java.util.concurrent.atomic.AtomicLong clock)
		{ this.seed = seed; this.profile = profile; this.transaction = transaction; this.background = background; this.materialization = materialization; this.engine = engine; this.visible = visible; this.clock = clock; }
		private long id() { return profile.profileId(); }
		private Player player() { try (var action = materialization.tryAcquireAction(id()).orElseThrow()) { return action.player(); } }
		private org.l2jmobius.gameserver.model.actor.instance.Monster monster(boolean spawnNow) throws Exception
		{
			final Player player = player(); final var monster = new org.l2jmobius.gameserver.model.actor.instance.Monster(NpcData.getInstance().getTemplate(seed.farm().npcId()));
			monster.disableCoreAI(true); monster.setInstanceId(player.getInstanceId()); final var spawn = new org.l2jmobius.gameserver.model.spawns.Spawn(monster.getTemplate());
			spawn.setXYZ(player.getX() + 20, player.getY(), player.getZ()); monster.setSpawn(spawn); monster.setCurrentHpMp(monster.getMaxHp(), monster.getMaxMp());
			if (spawnNow) { monster.spawnMe(player.getX() + 20, player.getY(), player.getZ()); }
			return monster;
		}
		@Override public void close() throws Exception
		{
			visible.stop(id());
			final var stopped = materialization.shutdown();
			if (expectedDiscard != null)
			{
				PhantomM1MaterializationRaceChecks.verifyExpectedDiscard(expectedDiscardContext, materialization, id(), expectedDiscard, stopped);
				java.lang.ref.Reference.reachabilityFence(expectedDiscardHold);
				expectedDiscardHold = null;
			}
			else if (!stopped.failedProfileIds().isEmpty() || materialization.snapshot().retainedEntries() != 0)
			{
				final var retainedPlayer = World.getInstance().getPlayer(profile.characterObjectId());
				final var retainedState = transaction.load(id()).state();
				throw new AssertionError("Native fixture cleanup retained its original actor; canonical restore refused. failed=" + stopped.failedProfileIds() + " pose=" + (retainedPlayer == null ? "ABSENT" : retainedPlayer.getX() + "," + retainedPlayer.getY() + "," + retainedPlayer.getZ()) + " baseline=" + (retainedState == null ? "ABSENT" : retainedState.position()));
			}
			background.beginStop(); background.finishStop(); engine.beginStop(); engine.finishStop(); deleteProfile(profile); seed.close();
		}
	}

	private void testNativeBuffReload(PhantomTestContext context, boolean expired) throws Exception
	{
		final var armed = new AtomicBoolean(); final var retained = new AtomicReference<org.l2jmobius.gameserver.model.skill.BuffInfo>();
		final org.l2jmobius.gameserver.phantoms.player.PhantomMaterializedPlayer.FailureInjector failures = point ->
		{
			if (expired && point == FailurePoint.BEFORE_STORE_OPERATION && armed.compareAndSet(true, false))
			{
				try { Thread.sleep(4200L); }
				catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException(interrupted); }
				PhantomAssertions.assertTrue(retained.get().getTime() <= 0, "Native stock buff did not actually expire during bounded teardown hold.");
			}
		};
		try (var fixture = openNativeProductionFixture(context, true, new PhantomBackgroundTransaction(), _environment.primary().objectId(), failures))
		{
			final Player before = fixture.player(); final long oldEpoch = before.getNativeWorkOwner().epoch();
			final int skillId = expired ? 3125 : 1045; final var skill = SkillData.getInstance().getSkill(skillId, 1);
			try (var action = fixture.materialization.tryAcquireAction(fixture.id()).orElseThrow())
			{
				if (expired) { skill.applyEffects(before, before, false, 3); }
				else { skill.applyEffects(before, before); }
				retained.set(before.getEffectList().getBuffInfoBySkillId(skillId));
				PhantomAssertions.assertTrue(retained.get() != null && retained.get().getTime() > 0, "Native stock buff failed to install with positive duration.");
			}
			armed.set(true);
			PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, fixture.materialization.dematerialize(fixture.id()).status(), "Native buff dematerialization failed.");
			final long saved = scalarLong("SELECT COUNT(*) FROM character_skills_save WHERE charId = ? AND skill_id = " + skillId + " AND remaining_time <= 0", before.getObjectId());
			context.record("Q12.buffReload." + skillId + ".expiredRows", saved);
			PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, fixture.materialization.materialize(fixture.id()).status(), "Native buff rematerialization failed.");
			final Player after = fixture.player(); final var restored = after.getEffectList().getBuffInfoBySkillId(skillId);
			PhantomAssertions.assertTrue(after != before && after.getObjectId() == before.getObjectId() && after.getNativeWorkOwner().epoch() != oldEpoch, "Buff reload bypassed actual new native materialization.");
			context.record("Q12.buffReload." + skillId + ".actualRematerialized", true);
			if (expired) { PhantomAssertions.assertTrue(saved == 0 && restored == null, "Expired retained stock buff was serialized/restarted at full native duration."); }
			else { PhantomAssertions.assertTrue(restored != null && restored.getTime() > 0 && restored.getTime() <= retained.get().getTime() + 2, "Actual production materialization lost saved native stock buff/remaining time."); }
		}
		finally { armed.set(false); }
	}

	private void testNativeDynamicRecipients(PhantomTestContext context, boolean transfer, boolean managed, boolean sealed) throws Exception
	{
		final var farm = new ProductionFarmSelection(20534, _production.topology().findAnchor("population.farming.elf.20534").orElseThrow());
		try (var first = openProductionPlayerFixture(farm.anchor(), 7, PlayerClass.ELVEN_MAGE, farm);
			var second = openProductionPlayerFixture(farm.anchor(), 7, PlayerClass.ELVEN_MAGE, farm, _environment.observer().objectId());
			var firstOutput = first.player().attachOutboundSession(new org.l2jmobius.gameserver.phantoms.player.HeadlessPlayerOutboundSession(8, 128));
			var secondOutput = second.player().attachOutboundSession(new org.l2jmobius.gameserver.phantoms.player.HeadlessPlayerOutboundSession(8, 128)))
		{
			final Player one = first.player(), two = second.player();
			final var template = org.l2jmobius.gameserver.data.xml.PlayerTemplateData.getInstance().getTemplate(PlayerClass.ELVEN_MAGE.getId());
			PhantomAssertions.assertTrue(template != null, "Dynamic native third Player template missing.");
			final Player third = Player.create(template, _environment.primary().accountName(), "PhT007C" + Long.toUnsignedString(System.nanoTime(), 36),
				new org.l2jmobius.gameserver.model.actor.appearance.PlayerAppearance((byte) 0, (byte) 0, (byte) 0, false));
			PhantomAssertions.assertTrue(third != null, "Dynamic native third Player.create failed.");
			final var monster = new org.l2jmobius.gameserver.model.actor.instance.Monster(NpcData.getInstance().getTemplate(20534));
			try (var thirdOutput = third.attachOutboundSession(new org.l2jmobius.gameserver.phantoms.player.HeadlessPlayerOutboundSession(8, 128)))
			{
				third.getStat().setLevel((byte) 7); third.setExp(ExperienceData.getInstance().getExpForLevel(7));
				third.setXYZInvisible(one.getX() + 30, one.getY(), one.getZ()); third.setInstanceId(one.getInstanceId());
				for (Player player : List.of(one, two, third)) { player.setOnlineStatus(true, false); player.spawnMe(); }
				final var magic = SkillData.getInstance().getSkill(1177, 1); one.addSkill(magic, true);
				monster.disableCoreAI(true); monster.setInstanceId(one.getInstanceId());
				final var spawn = new org.l2jmobius.gameserver.model.spawns.Spawn(monster.getTemplate()); spawn.setXYZ(one.getX() + 20, one.getY(), one.getZ()); monster.setSpawn(spawn);
				monster.setCurrentHpMp(monster.getMaxHp(), monster.getMaxMp()); monster.spawnMe(one.getX() + 20, one.getY(), one.getZ());
				try
				{
					if (transfer)
					{
						org.l2jmobius.gameserver.model.skill.Skill incoming = null;
						for (int level = 28; level >= 1; level--)
						{
							final var original = SkillData.getInstance().getSkill(1239, level);
							if (original != null && monster.getStat().getMpConsume(original) + monster.getStat().getMpInitialConsume(original) <= monster.getCurrentMp()) { incoming = original; break; }
						}
						PhantomAssertions.assertTrue(incoming != null, "INVALID dynamic transfer: original NPC has no affordable stock Hurricane.");
						context.record("Q12.dynamic.transfer.incoming", incoming.getId() + ":" + incoming.getLevel() + " nativeMP=" + monster.getCurrentMp());
						org.l2jmobius.gameserver.phantoms.player.PhantomM1DynamicRecipientChecks.transferChange(context, one, two, third, monster, incoming, managed, sealed);
					}
					else { org.l2jmobius.gameserver.phantoms.player.PhantomM1DynamicRecipientChecks.partyChange(context, one, two, third, monster, magic, managed, sealed); }
				}
				finally
				{
					if (managed)
					{
						for (var fixture : List.of(first, second))
						{
							final Player consumed = fixture.player();
							if (consumed.getNativeWorkOwner() == null && World.getInstance().getPlayer(consumed.getObjectId()) == null && PhantomIdentityLeaseRegistry.getInstance().getOwnerKind(consumed.getObjectId()) == null)
							{
								// Failed helper lifetime was explicitly discarded at zero accounting. Restore its exact TEST DB before-image without a second successful store.
								fixture._player = null;
							}
						}
					}
				}
			}
			finally
			{
				monster.abortAttack(); monster.abortCast(); monster.deleteMe();
				org.l2jmobius.gameserver.model.groups.PartyInvitationService.getInstance().leave(third);
				_environment.cleanupLoadedPlayer(third);
				org.l2jmobius.gameserver.network.GameClient.deleteCharByObjId(third.getObjectId());
			}
		}
	}

	private void testNativeTeleportProducer(PhantomTestContext context, String producer) throws Exception
	{
		final var armed = new AtomicBoolean(); final var prepared = new CountDownLatch(1); final var release = new CountDownLatch(1);
		final var transaction = new PhantomBackgroundTransaction(DatabaseFactory::getConnection, allocator(new AtomicInteger()), point ->
		{
			if (armed.get() && point == FaultPoint.AFTER_OWNED_PREPARE && armed.compareAndSet(true, false))
			{
				prepared.countDown();
				try { if (!release.await(10, TimeUnit.SECONDS)) { throw new IllegalStateException("Teleport TEST PREPARE hold expired"); } }
				catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException(interrupted); }
			}
		});
		try (var fixture = openNativeProductionFixture(context, true, transaction))
		{
			armed.set(producer.equals("residence"));
			try
			{
				if (producer.equals("event")) { PhantomM1TeleportChecks.teleportedEvent(context, fixture.player(), fixture.materialization, fixture.id()); }
				else if (producer.equals("jail")) { PhantomM1TeleportChecks.jail(context, fixture.player(), fixture.materialization, fixture.id()); }
				else { PhantomM1TeleportChecks.residence(context, fixture.player(), fixture.materialization, fixture.id(), prepared, release); }
			}
			finally { armed.set(false); release.countDown(); }
		}
		finally { armed.set(false); release.countDown(); }
	}

	private void testOrdinaryNativeTeleportProducer(PhantomTestContext context, String producer) throws Exception
	{
		final var farm = new ProductionFarmSelection(20534, _production.topology().findAnchor("population.farming.elf.20534").orElseThrow());
		try (var fixture = openProductionPlayerFixture(farm.anchor(), 7, PlayerClass.ELVEN_MAGE, farm);
			var output = fixture.player().attachOutboundSession(new org.l2jmobius.gameserver.phantoms.player.HeadlessPlayerOutboundSession(8, 128)))
		{
			final Player player = fixture.player(); player.setOnlineStatus(true, false); player.spawnMe();
			if (producer.equals("event")) { PhantomM1TeleportChecks.ordinaryTeleportedEvent(context, player); }
			else if (producer.equals("jail")) { PhantomM1TeleportChecks.ordinaryJail(context, player); }
			else { PhantomM1TeleportChecks.ordinaryResidence(context, player); }
		}
	}

	private void testNativeRegenPhase(PhantomTestContext context) throws Exception
	{
		try (var fixture = openNativeProductionFixture(context, true))
		{
			final Player player = fixture.player(); final var monster = fixture.monster(true);
			try { PhantomM1NativePhaseChecks.regen(context, player, fixture.materialization, fixture.id(), monster, player.getKnownSkill(1177)); }
			finally { monster.abortAttack(); monster.abortCast(); monster.deleteMe(); }
		}
	}

	private void testNativeSummonPhase(PhantomTestContext context) throws Exception
	{
		final var fixture = openNativeProductionFixture(context, false);
		org.l2jmobius.gameserver.model.actor.instance.Monster monster = null;
		org.l2jmobius.gameserver.model.actor.instance.Servitor summon = null;
		PhantomM1NativePhaseChecks.CompletionFence completion = null; Throwable primary = null;
		try
		{
			final Player player = fixture.player(); monster = fixture.monster(true);
			try (var action = fixture.materialization.tryAcquireAction(fixture.id()).orElseThrow()) { summon = PhantomM1NativePhaseChecks.createSummon(action.player()); }
			completion = new PhantomM1NativePhaseChecks.CompletionFence(context, player, "profile=" + fixture.id() + " character=" + fixture.seed._original + " baseClass=" + fixture.seed._originalBaseClass + " inventoryDigest=" + canonicalInventoryHash(player.getObjectId()));
			PhantomM1NativePhaseChecks.summon(context, player, fixture.materialization, fixture.id(), summon, monster, completion);
		}
		catch (Exception | Error failure) { primary = failure; throw failure; }
		finally
		{
			if (completion != null && !completion.cleanupSafe()) { retainSummonCompletion(context, completion, primary); }
			else
			{
				Throwable cleanup = null;
				try
				{
					if (completion != null) { completion.requireSafeCleanup(); }
					try (var action = fixture.materialization.tryAcquireAction(fixture.id()).orElseThrow())
					{
						if (summon != null) { summon.abortAttack(); summon.abortCast(); summon.unSummon(action.player()); summon.deleteMe(); }
					}
				}
				catch (Exception | Error failure) { cleanup = failure; }
				try { if (monster != null) { monster.abortAttack(); monster.abortCast(); monster.deleteMe(); } }
				catch (Exception | Error failure) { cleanup = combineSummonFailure(cleanup, failure); }
				try { fixture.close(); }
				catch (Exception | Error failure) { cleanup = combineSummonFailure(cleanup, failure); }
				if (cleanup == null && completion != null) { try { completion.cleanupFinished(); } catch (Exception | Error failure) { cleanup = failure; } }
				finishSummonCleanup(context, completion, primary, cleanup);
			}
		}
	}

	private void testOrdinarySummonPhase(PhantomTestContext context) throws Exception
	{
		final var farm = new ProductionFarmSelection(20534, _production.topology().findAnchor("population.farming.elf.20534").orElseThrow());
		final var fixture = openProductionPlayerFixture(farm.anchor(), 7, PlayerClass.ELVEN_FIGHTER, farm, _environment.observer().objectId());
		AutoCloseable output = null; org.l2jmobius.gameserver.model.actor.instance.Monster monster = null;
		org.l2jmobius.gameserver.model.actor.instance.Servitor summon = null;
		PhantomM1NativePhaseChecks.CompletionFence completion = null; Throwable primary = null;
		try
		{
			final Player player = fixture.player(); output = player.attachOutboundSession(new org.l2jmobius.gameserver.phantoms.player.HeadlessPlayerOutboundSession(8, 128)); player.setOnlineStatus(true, false); player.spawnMe();
			final var spawn = new org.l2jmobius.gameserver.model.spawns.Spawn(NpcData.getInstance().getTemplate(20534));
			spawn.setXYZ(player.getX() + 20, player.getY(), player.getZ());
			spawn.stopRespawn();
			monster = (org.l2jmobius.gameserver.model.actor.instance.Monster) spawn.doSpawn(false); monster.disableCoreAI(true);
			summon = PhantomM1NativePhaseChecks.createSummon(player);
			completion = new PhantomM1NativePhaseChecks.CompletionFence(context, player, "character=" + fixture._original + " baseClass=" + fixture._originalBaseClass + " inventoryDigest=" + canonicalInventoryHash(player.getObjectId()));
			PhantomM1NativePhaseChecks.ordinarySummon(context, player, summon, monster, completion);
		}
		catch (Exception | Error failure) { primary = failure; throw failure; }
		finally
		{
			if (completion != null && !completion.cleanupSafe()) { retainSummonCompletion(context, completion, primary); }
			else
			{
				Throwable cleanup = null;
				try { if (completion != null) { completion.requireSafeCleanup(); } if (summon != null) { summon.abortAttack(); summon.abortCast(); summon.unSummon(fixture.player()); summon.deleteMe(); } }
				catch (Exception | Error failure) { cleanup = failure; }
				try { if (monster != null) { monster.abortAttack(); monster.abortCast(); monster.deleteMe(); } }
				catch (Exception | Error failure) { cleanup = combineSummonFailure(cleanup, failure); }
				try { if (output != null) { output.close(); } }
				catch (Exception | Error failure) { cleanup = combineSummonFailure(cleanup, failure); }
				try { fixture.close(); }
				catch (Exception | Error failure) { cleanup = combineSummonFailure(cleanup, failure); }
				if (cleanup == null && completion != null) { try { completion.cleanupFinished(); } catch (Exception | Error failure) { cleanup = failure; } }
				finishSummonCleanup(context, completion, primary, cleanup);
			}
		}
	}
	private static Throwable combineSummonFailure(Throwable primary, Throwable secondary) { if (primary == null) { return secondary; } if (primary != secondary) { primary.addSuppressed(secondary); } return primary; }
	private void retainSummonCompletion(PhantomTestContext context, PhantomM1NativePhaseChecks.CompletionFence completion, Throwable primary) throws Exception
	{
		_retainedSummonCompletion = "SUMMON_TEST_RETAINED_COMPLETION_UNKNOWN";
		try { _retainedSummonCompletion = completion.diagnostic(); context.record("A.SUMMON.retained", _retainedSummonCompletion); }
		catch (Exception | Error failure) { if (primary == null) { if (failure instanceof Error error) { throw error; } throw (Exception) failure; } if (primary != failure) { primary.addSuppressed(failure); } }
		if (primary == null) { throw new IllegalStateException("SUMMON_TEST_UNKNOWN_COMPLETION_REFUSED " + _retainedSummonCompletion); }
	}
	private void finishSummonCleanup(PhantomTestContext context, PhantomM1NativePhaseChecks.CompletionFence completion, Throwable primary, Throwable cleanup) throws Exception
	{
		if (cleanup == null) { return; }
		if (completion != null)
		{
			_retainedSummonCompletion = "SUMMON_TEST_RETAINED_CLEANUP_FAILED";
			try { _retainedSummonCompletion = completion.diagnostic(); context.record("A.SUMMON.retainedCleanupFailure", _retainedSummonCompletion); }
			catch (Exception | Error diagnostic) { if (cleanup != diagnostic) { cleanup.addSuppressed(diagnostic); } }
		}
		if (primary != null) { if (primary != cleanup) { primary.addSuppressed(cleanup); } return; }
		if (cleanup instanceof Error error) { throw error; } throw (Exception) cleanup;
	}

	private void testNativeMaterializationRace(PhantomTestContext context, PhantomM1MaterializationRaceChecks.Stage stage, boolean shutdown, PhantomM1MaterializationRaceChecks.AbortFailure failure) throws Exception
	{
		final var hold = new PhantomM1MaterializationRaceChecks.Hold(stage, failure);
		try (var fixture = openNativeProductionFixture(context, true, new PhantomBackgroundTransaction(), _environment.primary().objectId(), hold, hold, false))
		{
			Throwable primary = null;
			try
			{
				if (failure == PhantomM1MaterializationRaceChecks.AbortFailure.NONE) { PhantomM1MaterializationRaceChecks.concurrent(context, fixture.materialization, fixture.id(), hold, shutdown); }
				else { PhantomM1MaterializationRaceChecks.primaryAbort(context, fixture.materialization, fixture.id(), hold); }
			}
			catch (Exception | Error thrown) { primary = thrown; throw thrown; }
			finally
			{
				hold.release();
				if (failure != PhantomM1MaterializationRaceChecks.AbortFailure.NONE && hold.player() != null)
				{
					try
					{
						final var receipt = PhantomM1MaterializationRaceChecks.discardExpectedFailure(context, fixture.materialization, fixture.id(), hold);
						fixture.expectedDiscard = receipt; fixture.expectedDiscardHold = hold; fixture.expectedDiscardContext = context;
					}
					catch (Exception | Error discardFailure)
					{
						if (primary == null) { throw discardFailure; }
						if (discardFailure != primary) { primary.addSuppressed(discardFailure); }
					}
				}
			}
		}
	}

	private void testNativeAiDelay(PhantomTestContext context, String kind, boolean managed) throws Exception
	{
		final var farm = new ProductionFarmSelection(20534, _production.topology().findAnchor("population.farming.elf.20534").orElseThrow());
		final String originalInventoryHash = canonicalInventoryHash(_environment.primary().objectId());
		final var seed = openProductionPlayerFixture(farm.anchor(), 7, PlayerClass.ELVEN_MAGE, farm);
		NativeProductionFixture owned = null;
		try
		{
			final Player prepared = seed.player();
			if (!kind.equals("arrived"))
			{
				final Item bow = prepared.getInventory().addItem(ItemProcessType.REWARD, 13, 1, prepared, this);
				final Item arrows = prepared.getInventory().addItem(ItemProcessType.REWARD, 17, 20, prepared, this);
				PhantomAssertions.assertTrue(bow != null && arrows != null, "Native delayed bow TEST item creation failed.");
				prepared.getInventory().equipItem(bow);
				PhantomAssertions.assertTrue(prepared.getActiveWeaponItem() != null && prepared.getActiveWeaponItem().getId() == 13, "Native delayed bow equip did not use stock Inventory.");
			}
			prepared.storeMe(); // Actual stock gear precedes factual baseline/canonical Mat seed.
			if (managed)
			{
				owned = ownNativeFixture(context, seed, true, new PhantomBackgroundTransaction(), point -> {}, null, true);
				runNativeAiDelay(context, owned.player(), owned.materialization, owned.id(), kind);
			}
			else
			{
				try (var output = prepared.attachOutboundSession(new org.l2jmobius.gameserver.phantoms.player.HeadlessPlayerOutboundSession(8, 128)))
				{
					prepared.setOnlineStatus(true, false); prepared.spawnMe();
					runNativeAiDelay(context, prepared, null, 0, kind);
				}
			}
		}
		finally { if (owned == null) { seed.close(); } else { owned.close(); } }
		PhantomAssertions.assertEquals(originalInventoryHash, canonicalInventoryHash(_environment.primary().objectId()), "Delayed AI fixture did not restore every canonical inventory row.");
		context.record("Q12.nativeAiDelay." + kind + "." + managed + ".restoredInventoryHash", originalInventoryHash);
	}

	private static void runNativeAiDelay(PhantomTestContext context, Player player, PhantomMaterializationService service, long profileId, String kind) throws Exception
	{
		final var skill = player.getKnownSkill(1177);
		PhantomAssertions.assertTrue(skill != null, "INVALID native delayed AI: existing stock auto-get1177 missing from exact seeded Player.");
		final double distance = kind.equals("arrived") ? player.getMagicalAttackRange(skill) + 6.5 * player.getStat().getMoveSpeed() : 128;
		final Location targetPosition = nativeAiCorridor(player, distance);
		final var monster = new org.l2jmobius.gameserver.model.actor.instance.Monster(NpcData.getInstance().getTemplate(20534));
		monster.disableCoreAI(true); monster.setInstanceId(player.getInstanceId());
		final var spawn = new org.l2jmobius.gameserver.model.spawns.Spawn(monster.getTemplate()); spawn.setXYZ(targetPosition.getX(), targetPosition.getY(), targetPosition.getZ()); monster.setSpawn(spawn);
		monster.setCurrentHpMp(monster.getMaxHp(), monster.getMaxMp()); monster.spawnMe(targetPosition.getX(), targetPosition.getY(), targetPosition.getZ());
		try
		{
			context.record("Q12.nativeAiDelay." + kind + ".corridor", "player=" + player.getX() + "," + player.getY() + "," + player.getZ()
				+ " target=" + targetPosition.getX() + "," + targetPosition.getY() + "," + targetPosition.getZ() + " loadedDryDirect=true actualDistance=" + player.calculateDistance2D(monster));
			if (kind.equals("ready"))
			{
				org.l2jmobius.gameserver.phantoms.player.PhantomM1NativeAiDelayChecks.bowReady(context, player, service, profileId, monster, nativeAiCorridor(player, 160));
			}
			else if (kind.equals("cast")) { org.l2jmobius.gameserver.phantoms.player.PhantomM1NativeAiDelayChecks.bowCast(context, player, service, profileId, monster, skill); }
			else if (kind.equals("arrived")) { org.l2jmobius.gameserver.phantoms.player.PhantomM1NativeAiDelayChecks.longMove(context, player, service, profileId, monster, skill); }
			else { throw new IllegalArgumentException("Unknown actual native AI delay"); }
		}
		finally { monster.abortAttack(); monster.abortCast(); monster.deleteMe(); }
	}

	private static Location nativeAiCorridor(Player player, double distance)
	{
		PhantomAssertions.assertTrue(distance >= 128 && distance < 2500, "INVALID native delayed AI: stock distance outside bounded native corridor.");
		final var geo = GeoEngine.getInstance();
		for (int ray = 0; ray < 16; ray++)
		{
			final double angle = ray * Math.PI / 8;
			final int x = player.getX() + (int) Math.round(Math.cos(angle) * distance);
			final int y = player.getY() + (int) Math.round(Math.sin(angle) * distance);
			if (!geo.hasGeo(x, y)) { continue; }
			final int z = geo.getHeight(x, y, player.getZ());
			if (Math.abs(z - player.getZ()) > 64 || !geo.canMoveToTarget(player.getX(), player.getY(), player.getZ(), x, y, z, player.getInstanceId())
				|| !geo.canSeeTarget(player.getX(), player.getY(), player.getZ(), x, y, z, player.getInstanceId())) { continue; }
			boolean dryLoaded = true;
			for (int step = 0; step <= 32; step++)
			{
				final int sampleX = player.getX() + (int) Math.round((x - player.getX()) * step / 32.0);
				final int sampleY = player.getY() + (int) Math.round((y - player.getY()) * step / 32.0);
				final int sampleZ = geo.getHeight(sampleX, sampleY, player.getZ());
				if (!geo.hasGeo(sampleX, sampleY) || Math.abs(sampleZ - player.getZ()) > 64
					|| org.l2jmobius.gameserver.managers.ZoneManager.getInstance().getZone(sampleX, sampleY, sampleZ, org.l2jmobius.gameserver.model.zone.type.WaterZone.class) != null)
				{
					dryLoaded = false; break;
				}
			}
			if (dryLoaded) { return new Location(x, y, z, player.getHeading(), player.getInstanceId()); }
		}
		throw new AssertionError("INVALID native delayed AI: no measured loaded direct dry corridor among16 bounded rays.");
	}

	private void testNativeDelayedState(PhantomTestContext context, String kind, boolean managed) throws Exception
	{
		if (managed)
		{
			try (var fixture = openNativeProductionFixture(context, true)) { nativeDelayedState(context, fixture.player(), fixture.materialization, fixture.id(), kind); }
			return;
		}
		final var farm = new ProductionFarmSelection(20534, _production.topology().findAnchor("population.farming.elf.20534").orElseThrow());
		try (var fixture = openProductionPlayerFixture(farm.anchor(), 7, PlayerClass.ELVEN_MAGE, farm);
			var output = fixture.player().attachOutboundSession(new org.l2jmobius.gameserver.phantoms.player.HeadlessPlayerOutboundSession(8, 128)))
		{
			final Player player = fixture.player(); player.setOnlineStatus(true, false); player.spawnMe();
			nativeDelayedState(context, player, null, 0, kind);
		}
	}

	private static void nativeDelayedState(PhantomTestContext context, Player player, PhantomMaterializationService service, long profileId, String kind) throws Exception
	{
		if (kind.equals("icons")) { org.l2jmobius.gameserver.phantoms.player.PhantomM1DelayedStateChecks.icons(context, player, service, profileId); }
		else if (kind.startsWith("watchdog"))
		{
			final int original = PlayerConfig.TELEPORT_WATCHDOG_TIMEOUT;
			try
			{
				PlayerConfig.TELEPORT_WATCHDOG_TIMEOUT = kind.equals("watchdog") ? 1 : 10;
				final var destination = new Location(player.getX() + 32, player.getY(), player.getZ(), player.getHeading(), player.getInstanceId());
				if (kind.equals("watchdog-cancel")) { org.l2jmobius.gameserver.phantoms.player.PhantomM1DelayedStateChecks.watchdogPendingCancellation(context, player, service, profileId, destination); }
				else if (kind.equals("watchdog-earned")) { org.l2jmobius.gameserver.phantoms.player.PhantomM1DelayedStateChecks.watchdogEarnedConfirmation(context, player, service, profileId, destination); }
				else if (kind.equals("watchdog-claim")) { org.l2jmobius.gameserver.phantoms.player.PhantomM1DelayedStateChecks.watchdogClaimRace(context, player, service, profileId, destination); }
				else if (kind.equals("watchdog-registration") || kind.equals("watchdog-stop")) { org.l2jmobius.gameserver.phantoms.player.PhantomM1DelayedStateChecks.watchdogRegistrationRace(context, player, service, profileId, destination, kind.equals("watchdog-stop")); }
				else { org.l2jmobius.gameserver.phantoms.player.PhantomM1DelayedStateChecks.watchdog(context, player, service, profileId, destination); }
			}
			finally { PlayerConfig.TELEPORT_WATCHDOG_TIMEOUT = original; }
		}
		else { org.l2jmobius.gameserver.phantoms.player.PhantomM1DelayedStateChecks.sitStand(context, player, service, profileId, kind.equals("stand")); }
	}

	private void testNativePublisher(PhantomTestContext context, String kind, String mode, boolean managed) throws Exception
	{
		if (kind.equals("eviction") && managed)
		{
			try (var fixture = openNativeProductionFixture(context, true)) { org.l2jmobius.gameserver.phantoms.player.PhantomM1RawProducerChecks.buffEviction(context, fixture.player(), fixture.materialization, fixture.id()); }
			return;
		}
		final var farm = new ProductionFarmSelection(20534, _production.topology().findAnchor("population.farming.elf.20534").orElseThrow());
		try (var fixture = openProductionPlayerFixture(farm.anchor(), 7, PlayerClass.ELVEN_MAGE, farm);
			var output = fixture.player().attachOutboundSession(new org.l2jmobius.gameserver.phantoms.player.HeadlessPlayerOutboundSession(8, 128)))
		{
			final Player player = fixture.player(); player.setOnlineStatus(true, false); player.spawnMe();
			try
			{
				if (kind.equals("status")) { org.l2jmobius.gameserver.phantoms.player.PhantomM1RawProducerChecks.status50Submission(context, player, mode); }
				else if (kind.equals("icons")) { org.l2jmobius.gameserver.phantoms.player.PhantomM1RawProducerChecks.icons300Submission(context, player, mode); }
				else if (kind.equals("eviction")) { org.l2jmobius.gameserver.phantoms.player.PhantomM1RawProducerChecks.buffEviction(context, player, null, 0); }
				else { throw new IllegalArgumentException("Unknown native publisher control"); }
			}
			finally
			{
				if (World.getInstance().getPlayer(player.getObjectId()) == null && player.getNativeWorkOwner() == null && PhantomIdentityLeaseRegistry.getInstance().getOwnerKind(player.getObjectId()) == null) { fixture._player = null; }
			}
		}
	}

	private void testNativeRawProducer(PhantomTestContext context, String kind, boolean managed) throws Exception
	{
		if (managed && (kind.equals("follow") || kind.equals("status")))
		{
			try (var fixture = openNativeProductionFixture(context, true))
			{
				if (kind.equals("status")) { org.l2jmobius.gameserver.phantoms.player.PhantomM1RawProducerChecks.status50(context, fixture.player(), fixture.materialization, fixture.id()); }
				else
				{
					final var target = nativeFollowTarget(fixture.player());
					try { org.l2jmobius.gameserver.phantoms.player.PhantomM1RawProducerChecks.follow(context, fixture.player(), target, fixture.materialization, fixture.id()); }
					finally { target.abortAttack(); target.abortCast(); target.deleteMe(); }
				}
			}
			return;
		}
		final var farm = new ProductionFarmSelection(20534, _production.topology().findAnchor("population.farming.elf.20534").orElseThrow());
		try (var fixture = openProductionPlayerFixture(farm.anchor(), 7, PlayerClass.ELVEN_MAGE, farm);
			var output = fixture.player().attachOutboundSession(new org.l2jmobius.gameserver.phantoms.player.HeadlessPlayerOutboundSession(8, 128)))
		{
			final Player player = fixture.player(); player.setOnlineStatus(true, false); player.spawnMe();
			try
			{
				if (kind.equals("buff-control")) { org.l2jmobius.gameserver.phantoms.player.PhantomM1RawProducerChecks.buffFinishControl(context, player, managed); }
				else if (kind.equals("buff-null")) { org.l2jmobius.gameserver.phantoms.player.PhantomM1RawProducerChecks.buffFinishNull(context, player, managed); }
				else if (kind.equals("buff-inline")) { org.l2jmobius.gameserver.phantoms.player.PhantomM1RawProducerChecks.buffFinishInlineStop(context, player); }
				else if (kind.equals("status")) { org.l2jmobius.gameserver.phantoms.player.PhantomM1RawProducerChecks.status50(context, player, null, 0); }
				else
				{
					final var target = nativeFollowTarget(player);
					try { org.l2jmobius.gameserver.phantoms.player.PhantomM1RawProducerChecks.follow(context, player, target, null, 0); }
					finally { target.abortAttack(); target.abortCast(); target.deleteMe(); }
				}
			}
			finally
			{
				if (managed && kind.startsWith("buff-") && player.getNativeWorkOwner() == null && World.getInstance().getPlayer(player.getObjectId()) == null && PhantomIdentityLeaseRegistry.getInstance().getOwnerKind(player.getObjectId()) == null) { fixture._player = null; }
			}
		}
	}

	private static org.l2jmobius.gameserver.model.actor.instance.Monster nativeFollowTarget(Player player) throws Exception
	{
		final int x = player.getX() + 256, y = player.getY(), z = player.getZ();
		PhantomAssertions.assertTrue(GeoEngine.getInstance().canMoveToTarget(player.getX(), player.getY(), player.getZ(), x, y, z, player.getInstanceId()), "INVALID raw follow: controlled original NPC target lacks actual geo corridor.");
		final var monster = new org.l2jmobius.gameserver.model.actor.instance.Monster(NpcData.getInstance().getTemplate(20534));
		monster.disableCoreAI(true); monster.setInstanceId(player.getInstanceId());
		final var spawn = new org.l2jmobius.gameserver.model.spawns.Spawn(monster.getTemplate()); spawn.setXYZ(x, y, z); monster.setSpawn(spawn);
		monster.setCurrentHpMp(monster.getMaxHp(), monster.getMaxMp()); monster.spawnMe(x, y, z);
		return monster;
	}

	private void testNativePartyLoot(PhantomTestContext context, int itemId, boolean ordinary) throws Exception
	{
		if (!ordinary)
		{
			try (var first = openNativeProductionFixture(context, true); var second = openNativeProductionFixture(context, true, new PhantomBackgroundTransaction(), _environment.observer().objectId()))
			{
				final Item ground = nativePartyGround(first.player(), itemId);
				try { PhantomM1PartyLootChecks.pickup(context, new PhantomM1SharedRecipientsChecks.Managed(first.player(), first.materialization, first.id()), new PhantomM1SharedRecipientsChecks.Managed(second.player(), second.materialization, second.id()), ground); }
				finally { destroyNativePartyGround(ground); }
			}
			return;
		}
		final var farm = new ProductionFarmSelection(20534, _production.topology().findAnchor("population.farming.elf.20534").orElseThrow());
		try (var first = openProductionPlayerFixture(farm.anchor(), 7, PlayerClass.ELVEN_MAGE, farm);
			var second = openProductionPlayerFixture(farm.anchor(), 7, PlayerClass.ELVEN_MAGE, farm, _environment.observer().objectId());
			var oneOutput = first.player().attachOutboundSession(new org.l2jmobius.gameserver.phantoms.player.HeadlessPlayerOutboundSession(8, 128));
			var twoOutput = second.player().attachOutboundSession(new org.l2jmobius.gameserver.phantoms.player.HeadlessPlayerOutboundSession(8, 128)))
		{
			final Player one = first.player(), two = second.player();
			for (Player player : List.of(one, two)) { player.setOnlineStatus(true, false); player.spawnMe(); }
			final Item ground = nativePartyGround(one, itemId);
			try { PhantomM1PartyLootChecks.ordinaryPickup(context, one, two, ground); }
			finally { destroyNativePartyGround(ground); }
		}
	}

	private void testNativePartyLateJoin(PhantomTestContext context, boolean ordinary, boolean sealed) throws Exception
	{
		final var farm = new ProductionFarmSelection(20534, _production.topology().findAnchor("population.farming.elf.20534").orElseThrow());
		if (!ordinary)
		{
			try (var first = openNativeProductionFixture(context, true);
				var second = openNativeProductionFixture(context, true, new PhantomBackgroundTransaction(), _environment.observer().objectId());
				var thirdSeed = openCreatedNativePartySeed(context, first.player(), farm);
				var third = ownNativeFixture(context, thirdSeed, true, new PhantomBackgroundTransaction(), point -> {}, null, true))
			{
				final Item ground = nativePartyGround(first.player(), 57);
				try
				{
					PhantomM1PartyLootChecks.lateJoin(context, new PhantomM1SharedRecipientsChecks.Managed(first.player(), first.materialization, first.id()),
						new PhantomM1SharedRecipientsChecks.Managed(second.player(), second.materialization, second.id()),
						new PhantomM1SharedRecipientsChecks.Managed(third.player(), third.materialization, third.id()), ground, sealed);
				}
				finally { destroyNativePartyGround(ground); }
			}
			return;
		}
		try (var first = openProductionPlayerFixture(farm.anchor(), 7, PlayerClass.ELVEN_MAGE, farm);
			var second = openProductionPlayerFixture(farm.anchor(), 7, PlayerClass.ELVEN_MAGE, farm, _environment.observer().objectId());
			var third = openCreatedNativePartySeed(context, first.player(), farm);
			var oneOutput = first.player().attachOutboundSession(new org.l2jmobius.gameserver.phantoms.player.HeadlessPlayerOutboundSession(8, 128));
			var twoOutput = second.player().attachOutboundSession(new org.l2jmobius.gameserver.phantoms.player.HeadlessPlayerOutboundSession(8, 128));
			var threeOutput = third.player().attachOutboundSession(new org.l2jmobius.gameserver.phantoms.player.HeadlessPlayerOutboundSession(8, 128)))
		{
			for (Player player : List.of(first.player(), second.player(), third.player())) { player.setOnlineStatus(true, false); player.spawnMe(); }
			final Item ground = nativePartyGround(first.player(), 57);
			try { PhantomM1PartyLootChecks.ordinaryLateJoin(context, first.player(), second.player(), third.player(), ground); }
			finally { destroyNativePartyGround(ground); }
		}
	}

	private ProductionPlayerFixture openCreatedNativePartySeed(PhantomTestContext context, Player beside, ProductionFarmSelection farm) throws Exception
	{
		PhantomAssertions.assertEquals(PhantomTestDatabaseGuard.TARGET_DATABASE, context.measurements().get("headless.database"), "Native third identity requires initialized guarded TEST.");
		final var template = org.l2jmobius.gameserver.data.xml.PlayerTemplateData.getInstance().getTemplate(PlayerClass.ELVEN_MAGE.getId());
		PhantomAssertions.assertTrue(template != null, "Native third owned Player template missing.");
		final Player third = Player.create(template, _environment.primary().accountName(), "PhT007L" + Long.toUnsignedString(System.nanoTime(), 36),
			new org.l2jmobius.gameserver.model.actor.appearance.PlayerAppearance((byte) 0, (byte) 0, (byte) 0, false));
		PhantomAssertions.assertTrue(third != null, "Native third guarded Player.create failed.");
		try
		{
			PhantomAssertions.assertTrue(third.getObjectId() != _environment.primary().objectId() && third.getObjectId() != _environment.observer().objectId()
				&& _repository.findByCharacterObjectId(third.getObjectId()).isEmpty(), "Native third TEST identity collided with an existing owned character/profile.");
			// Same native TEST seed setup as the existing dynamic-recipient third Player fixture.
			third.setPlayerClass(PlayerClass.ELVEN_MAGE.getId());
			PhantomAssertions.assertEquals(PlayerClass.ELVEN_MAGE.getId(), third.getActiveClass(), "Fresh third native active class was not initialized.");
			third.getStat().setLevel((byte) 7); third.setExp(ExperienceData.getInstance().getExpForLevel(7));
			final var expectedSkills = exactAutoGetSkills(new Identity(PRODUCTION_LOOT_UNBLOCK_SEED, third.getObjectId(), 0, PlayerClass.ELVEN_MAGE.getId(), PlayerClass.ELVEN_MAGE.getRace().ordinal()), 7);
			context.record("Q12.partyLate.missingAutoGetBeforeSeed", expectedSkills.stream().filter(skill -> third.getKnownSkill(skill.skillId()) == null || third.getKnownSkill(skill.skillId()).getLevel() != skill.skillLevel()).toList());
			third.giveAvailableAutoGetSkills();
			PhantomAssertions.assertTrue(expectedSkills.stream().allMatch(skill -> third.getKnownSkill(skill.skillId()) != null && third.getKnownSkill(skill.skillId()).getLevel() == skill.skillLevel()), "Fresh third stock auto-get skills were not initialized by native API.");
			third.setCurrentHp(third.getMaxHp()); third.setCurrentMp(third.getMaxMp()); third.setCurrentCp(third.getMaxCp());
			third.setXYZInvisible(beside.getX() + 30, beside.getY(), beside.getZ()); third.setInstanceId(beside.getInstanceId()); third.storeMe();
			context.record("Q12.partyLate.created", "object=" + third.getObjectId() + " nativePlayerCreate=true guardedOwnedAccount=true exactDeleteRequired=true");
			return new ProductionPlayerFixture(third, farm, goal(farm.npcId(), farm.anchor().id()), canonical(third.getObjectId()), PlayerClass.ELVEN_MAGE.getId(), true);
		}
		catch (Throwable failure)
		{
			try { _environment.cleanupLoadedPlayer(third); org.l2jmobius.gameserver.network.GameClient.deleteCharByObjId(third.getObjectId()); }
			catch (Throwable cleanup) { if (cleanup != failure) { failure.addSuppressed(cleanup); } }
			throw failure;
		}
	}

	private void testNativePartyAutoLoot(PhantomTestContext context, int itemId, boolean ordinary) throws Exception
	{
		final boolean originalAutoLoot = PlayerConfig.AUTO_LOOT;
		try
		{
			PlayerConfig.AUTO_LOOT = true; // Existing native optional branch, guarded TEST only; exact original restored below.
			if (!ordinary)
			{
				try (var first = openNativeProductionFixture(context, true);
					var second = openNativeProductionFixture(context, true, new PhantomBackgroundTransaction(), _environment.observer().objectId()))
				{
					final var monster = first.monster(true);
					try
					{
						PhantomM1PartyLootChecks.autoLoot(context, new PhantomM1SharedRecipientsChecks.Managed(first.player(), first.materialization, first.id()),
							new PhantomM1SharedRecipientsChecks.Managed(second.player(), second.materialization, second.id()), monster, nativePartyAutoLootTemplate(monster, itemId), itemId);
					}
					finally { monster.abortAttack(); monster.abortCast(); monster.deleteMe(); }
				}
				return;
			}
			final var farm = new ProductionFarmSelection(20534, _production.topology().findAnchor("population.farming.elf.20534").orElseThrow());
			try (var first = openProductionPlayerFixture(farm.anchor(), 7, PlayerClass.ELVEN_MAGE, farm);
				var second = openProductionPlayerFixture(farm.anchor(), 7, PlayerClass.ELVEN_MAGE, farm, _environment.observer().objectId());
				var oneOutput = first.player().attachOutboundSession(new org.l2jmobius.gameserver.phantoms.player.HeadlessPlayerOutboundSession(8, 128));
				var twoOutput = second.player().attachOutboundSession(new org.l2jmobius.gameserver.phantoms.player.HeadlessPlayerOutboundSession(8, 128)))
			{
				final Player one = first.player(), two = second.player();
				for (Player player : List.of(one, two)) { player.setOnlineStatus(true, false); player.spawnMe(); }
				final var monster = nativeFollowTarget(one);
				try { PhantomM1PartyLootChecks.ordinaryAutoLoot(context, one, two, monster, nativePartyAutoLootTemplate(monster, itemId), itemId); }
				finally { monster.abortAttack(); monster.abortCast(); monster.deleteMe(); }
			}
		}
		finally { PlayerConfig.AUTO_LOOT = originalAutoLoot; }
	}

	private static NpcTemplate nativePartyAutoLootTemplate(org.l2jmobius.gameserver.model.actor.instance.Monster target, int itemId)
	{
		final var source = target.getTemplate();
		final var facts = new org.l2jmobius.gameserver.model.StatSet();
		facts.set("id", source.getId()); facts.set("displayId", source.getDisplayId()); facts.set("level", source.getLevel()); facts.set("type", "Monster"); facts.set("name", source.getName());
		facts.set("baseHpMax", target.getMaxHp()); facts.set("baseMpMax", target.getMaxMp()); facts.set("collisionRadius", source.getCollisionRadius()); facts.set("collisionHeight", source.getCollisionHeight());
		final var isolated = new NpcTemplate(facts); isolated.setSkills(Map.of());
		isolated.addDrop(new DropHolder(org.l2jmobius.gameserver.model.actor.enums.npc.DropType.DROP, itemId, itemId == 57 ? 20 : 1, itemId == 57 ? 20 : 1, 100));
		return isolated;
	}

	private static Item nativePartyGround(Player player, int itemId)
	{
		final Item item = ItemManager.createItem(ItemProcessType.LOOT, itemId, itemId == 57 ? 20 : 1, player, PhantomM1PartyLootChecks.class);
		PhantomAssertions.assertTrue(item != null, "Native party ground Item creation failed.");
		item.dropMe(player, player.getX() + 20, player.getY(), player.getZ());
		item.setOwnerId(0); item.setProtected(false); item.getDropProtection().unprotect();
		return item;
	}

	private static void destroyNativePartyGround(Item item)
	{
		if (item != null && item.isSpawned())
		{
			item.getDropProtection().unprotect(); item.resetOwnerTimer(); item.decayMe();
			ItemManager.destroyItem(ItemProcessType.DESTROY, item, null, PhantomM1PartyLootChecks.class);
		}
	}

	private void testNativeSecondaryRecipients(PhantomTestContext context, boolean transfer, boolean ordinary) throws Exception
	{
		if (!ordinary)
		{
			try (var first = openNativeProductionFixture(context, true); var second = openNativeProductionFixture(context, true, new PhantomBackgroundTransaction(), _environment.observer().objectId()))
			{
				final var monster = first.monster(true);
				try
				{
					final var one = new PhantomM1SharedRecipientsChecks.Managed(first.player(), first.materialization, first.id());
					final var two = new PhantomM1SharedRecipientsChecks.Managed(second.player(), second.materialization, second.id());
					if (transfer) { PhantomM1SharedRecipientsChecks.transferDamage(context, one, two, monster, SkillData.getInstance().getSkill(1239, 1)); }
					else { PhantomM1SharedRecipientsChecks.partyExp(context, one, two, monster, one.player().getKnownSkill(1177)); }
				}
				finally { monster.abortAttack(); monster.abortCast(); monster.deleteMe(); }
			}
			return;
		}
		final var farm = new ProductionFarmSelection(20534, _production.topology().findAnchor("population.farming.elf.20534").orElseThrow());
		try (var first = openProductionPlayerFixture(farm.anchor(), 7, PlayerClass.ELVEN_MAGE, farm);
			var second = openProductionPlayerFixture(farm.anchor(), 7, PlayerClass.ELVEN_MAGE, farm, _environment.observer().objectId());
			var oneOutput = first.player().attachOutboundSession(new org.l2jmobius.gameserver.phantoms.player.HeadlessPlayerOutboundSession(8, 128));
			var twoOutput = second.player().attachOutboundSession(new org.l2jmobius.gameserver.phantoms.player.HeadlessPlayerOutboundSession(8, 128)))
		{
			final Player one = first.player(), two = second.player();
			one.setOnlineStatus(true, false); two.setOnlineStatus(true, false); one.spawnMe(); two.spawnMe();
			final var magic = SkillData.getInstance().getSkill(1177, 1); one.addSkill(magic, true);
			final var monster = new org.l2jmobius.gameserver.model.actor.instance.Monster(NpcData.getInstance().getTemplate(20534));
			monster.disableCoreAI(true); monster.setInstanceId(one.getInstanceId());
			final var spawn = new org.l2jmobius.gameserver.model.spawns.Spawn(monster.getTemplate()); spawn.setXYZ(one.getX() + 20, one.getY(), one.getZ()); monster.setSpawn(spawn);
			monster.setCurrentHpMp(monster.getMaxHp(), monster.getMaxMp()); monster.spawnMe(one.getX() + 20, one.getY(), one.getZ());
			try
			{
				if (transfer) { PhantomM1SharedRecipientsChecks.ordinaryTransferDamage(context, one, two, monster, SkillData.getInstance().getSkill(1239, 1)); }
				else { PhantomM1SharedRecipientsChecks.ordinaryPartyExp(context, one, two, monster, magic); }
			}
			finally { monster.abortAttack(); monster.abortCast(); monster.deleteMe(); }
		}
	}

	private void testNativeReviewCheckpoint(PhantomTestContext context, boolean mismatch) throws Exception
	{
		final var armed = new AtomicBoolean(); final var reached = new AtomicInteger(); final var live = new AtomicReference<Player>();
		final var transaction = new PhantomBackgroundTransaction(DatabaseFactory::getConnection, allocator(new AtomicInteger()), point ->
		{
			if (!armed.get()) { return; }
			if (!mismatch && (point == FaultPoint.AFTER_OWNED_NATIVE_STORE) && armed.compareAndSet(true, false)) { reached.incrementAndGet(); throw new InjectedFailure(); }
			if (mismatch && (point == FaultPoint.AFTER_OWNED_FINALIZE_COMMIT) && armed.compareAndSet(true, false)) { reached.incrementAndGet(); live.get().setSp(live.get().getSp() + 1); }
		});
		try (var fixture = openNativeProductionFixture(context, true, transaction))
		{
			final Player player = fixture.player(); live.set(player); final long sp = player.getSp();
			final var scope = (org.l2jmobius.gameserver.phantoms.player.PhantomNativeWorkScope) player.getNativeWorkOwner();
			try
			{
				armed.set(true);
				PhantomAssertions.assertFalse(fixture.background.captureVisibleArrival(fixture.id(), player, fixture.seed.goal(), fixture.seed.goal().selectedAnchor().key()), "Q09 native fault arrival claimed success.");
				PhantomAssertions.assertEquals(1, reached.get(), "Q09 actual native fault boundary was not reached.");
				PhantomAssertions.assertFalse(scope.open(), "Q09 failed native checkpoint reopened admission.");
				PhantomAssertions.assertEquals(0, scope.outstanding(), "Q09 checkpoint retained own ticket.");
				if (mismatch)
				{
					PhantomAssertions.assertFalse(player.hasPendingOwnedStore(), "Q09 finalized mismatch retained a replayable receipt.");
					PhantomAssertions.assertEquals(sp, canonical(player.getObjectId()).skillPoints(), "Q09 mismatch guard wrote post-finalize SP.");
					final var pinned = canonical(player.getObjectId());
					PhantomAssertions.assertFalse(fixture.background.captureVisibleArrival(fixture.id(), player, fixture.seed.goal(), fixture.seed.goal().selectedAnchor().key()), "Q09 failed lifetime accepted a fresh arrival capture.");
					PhantomAssertions.assertEquals(pinned, canonical(player.getObjectId()), "Q09 fresh capture hid finalized mismatch.");
					PhantomAssertions.assertFalse(player.hasPendingOwnedStore() || scope.open(), "Q09 finalized mismatch became replayable/open.");
				}
				else
				{
					PhantomAssertions.assertTrue(player.hasPendingOwnedStore(), "Q09 exact native failure lost pending receipt.");
					final var route = org.l2jmobius.gameserver.phantoms.background.PhantomNormalGatekeeperTravel.load(Path.of("data/phantoms/travel/high-five-normal-gk.xml"), _production.topology());
					final var travel = new org.l2jmobius.gameserver.phantoms.background.PhantomVisibleFarmTravel(fixture.materialization, fixture.background,
						route, new PhantomNavigationService(new PhantomMetrics()), _ -> true, noSignals());
					travel.arrive(fixture.id(), fixture.seed.goal());
					PhantomAssertions.assertFalse(player.hasPendingOwnedStore(), "Q09 production travel consumer cannot resume exact active pending receipt.");
					PhantomAssertions.assertFalse(player.hasPendingOwnedStore(), "Q09 successful resume retained receipt.");
					PhantomAssertions.assertTrue(scope.open() && scope.isCurrent(), "Q09 proven resume did not reopen exact original lifetime.");
					try (var action = fixture.materialization.tryAcquireAction(fixture.id()).orElseThrow())
					{
						PhantomAssertions.assertTrue((action.player() == player) && (PlayerNativeWork.current(scope) != null), "Q09 resume did not admit original native action.");
					}
				}
				context.record("m1.Q09.review." + mismatch, "actualBoundary=1 nativeOutstanding=0 pending=" + player.hasPendingOwnedStore() + " open=" + scope.open() + " epoch=" + scope.epoch());
			}
			finally { armed.set(false); if (mismatch) { player.setSp(sp); } }
		}
	}

	private void testNativeMovementExit(PhantomTestContext context) throws Exception
	{
		final var armed = new AtomicBoolean(); final var prepared = new CountDownLatch(1); final var releasePrepare = new CountDownLatch(1);
		final var transaction = new PhantomBackgroundTransaction(DatabaseFactory::getConnection, allocator(new AtomicInteger()), point ->
		{
			if (armed.get() && (point == FaultPoint.AFTER_OWNED_PREPARE))
			{
				prepared.countDown();
				try { if (!releasePrepare.await(10, TimeUnit.SECONDS)) { throw new AssertionError("Q12 movement native PREPARE barrier timeout."); } }
				catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new AssertionError(failure); }
			}
		});
		try (var fixture = openNativeProductionFixture(context, true, transaction))
		{
			armed.set(true);
			try { PhantomM1BuffChecks.movement(context, fixture.player(), fixture.materialization, fixture.id(), prepared, releasePrepare, _environment::cleanupLoadedPlayer); }
			finally { armed.set(false); releasePrepare.countDown(); }
		}
	}

	private void testNativeOfflineReplacement(PhantomTestContext context) throws Exception
	{
		final Player player = Player.load(_environment.primary().objectId());
		PhantomAssertions.assertTrue((player != null) && !player.isNativeWorkManaged(), "Ordinary offline fixture must be a real native unmanaged load.");
		final var manager = AutoPlayTaskManager.getInstance();
		final var table = org.l2jmobius.gameserver.data.sql.OfflinePlayTable.getInstance();
		final boolean restore = org.l2jmobius.gameserver.config.custom.OfflinePlayConfig.RESTORE_AUTO_PLAY_OFFLINERS;
		final CountDownLatch oldDelete = new CountDownLatch(1);
		final CountDownLatch releaseDelete = new CountDownLatch(1);
		final CountDownLatch replaced = new CountDownLatch(1);
		final var failures = new AtomicReference<Throwable>();
		final var oldThread = new AtomicReference<Thread>();
		final Field field = DatabaseFactory.class.getDeclaredField("DATABASE_POOL"); field.setAccessible(true);
		final var original = (com.zaxxer.hikari.HikariDataSource) field.get(null);
		final AutoPlayTaskManager.PhantomPolicy replacement = new AutoPlayTaskManager.PhantomPolicy()
		{
			@Override public AutoPlayTaskManager.TickLease acquire(Player actor) { return actor == player ? () -> {} : null; }
			@Override public boolean permitsTarget(Creature target) { return false; }
		};
		Thread stop = null; Thread start = null;
		try
		{
			org.l2jmobius.gameserver.config.custom.OfflinePlayConfig.RESTORE_AUTO_PLAY_OFFLINERS = true;
			player.stopAllTasks(); player.setOfflinePlay(true); player.getAutoUseSettings().getAutoActions().add(2);
			manager.startAutoPlay(player); table.storeOfflinePlay(player);
			PhantomAssertions.assertTrue(nativeOfflineRows(original, player.getObjectId()) > 0, "Ordinary native offline positive control did not persist a row.");
			field.set(null, new com.zaxxer.hikari.HikariDataSource()
			{
				@Override public Connection getConnection() throws java.sql.SQLException
				{
					final Connection delegate = original.getConnection();
					return (Connection) java.lang.reflect.Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[] { Connection.class }, (_, method, args) ->
					{
						try
						{
							final Object value = method.invoke(delegate, args);
							if ((Thread.currentThread() == oldThread.get()) && method.getName().equals("prepareStatement") && (args[0] instanceof String sql) && sql.startsWith("DELETE FROM character_offline_play"))
							{
								final PreparedStatement statement = (PreparedStatement) value;
								return java.lang.reflect.Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(), new Class<?>[] { PreparedStatement.class }, (_, operation, parameters) ->
								{
									if (operation.getName().equals("execute")) { oldDelete.countDown(); if (!releaseDelete.await(5, TimeUnit.SECONDS)) { throw new AssertionError("TEST old native DELETE barrier timed out"); } }
									try { return operation.invoke(statement, parameters); } catch (InvocationTargetException failure) { throw failure.getCause(); }
								});
							}
							return value;
						}
						catch (InvocationTargetException failure) { throw failure.getCause(); }
					});
				}
			});
			stop = new Thread(() -> { try { manager.stopAutoPlay(player); } catch (Throwable failure) { failures.compareAndSet(null, failure); } }, "TEST-old-offline-stop");
			oldThread.set(stop); stop.start();
			PhantomAssertions.assertTrue(oldDelete.await(3, TimeUnit.SECONDS), "Actual ordinary stop did not reach native DELETE; fixture invalid.");
			start = new Thread(() -> { try { manager.startPhantomAutoPlay(player, replacement); table.storeOfflinePlay(player); } catch (Throwable failure) { failures.compareAndSet(null, failure); } finally { replaced.countDown(); } }, "TEST-new-offline-registration");
			start.start(); final boolean concurrentPublish = replaced.await(200, TimeUnit.MILLISECONDS);
			releaseDelete.countDown(); stop.join(3000); start.join(3000);
			PhantomAssertions.assertFalse(stop.isAlive() || start.isAlive(), "Actual manager replacement deadlocked around native SQL.");
			PhantomAssertions.assertEquals(null, failures.get(), "Native manager fixture thread failed.");
			PhantomAssertions.assertTrue(manager.hasPhantomRegistration(player, replacement) && player.isAutoPlaying(), "Old stop removed new native registration.");
			final int rows = nativeOfflineRows(original, player.getObjectId());
			context.record("m1.P06.native", "concurrentPublish=" + concurrentPublish + " replacementRows=" + rows);
			PhantomAssertions.assertTrue(rows > 0, "Old stop native DELETE erased the replacement offline row after generation check.");
		}
		finally
		{
			releaseDelete.countDown(); if (stop != null) { stop.join(3000); } if (start != null) { start.join(3000); }
			field.set(null, original); player.setOfflinePlay(false); manager.stopPhantomAutoPlay(player, replacement); manager.stopAutoPlay(player); table.removeOfflinePlay(player);
			org.l2jmobius.gameserver.config.custom.OfflinePlayConfig.RESTORE_AUTO_PLAY_OFFLINERS = restore;
			player.stopAllTasks(); player.deleteMe();
		}
	}

	private static int nativeOfflineRows(com.zaxxer.hikari.HikariDataSource source, int objectId) throws Exception
	{
		try (var connection = source.getConnection(); var statement = connection.prepareStatement("SELECT COUNT(*) FROM character_offline_play WHERE charId=?"))
		{
			statement.setInt(1, objectId); try (var rows = statement.executeQuery()) { rows.next(); return rows.getInt(1); }
		}
	}

	/** Fresh native lifetime at an existing factual source; no manual pose or simulated arrival. */
	private void testNativeRouteScenario(PhantomTestContext context, boolean gatekeeper) throws Exception
	{
		testNativeRouteScenario(context, gatekeeper ? "gatekeeper" : "water");
	}

	private void testNativeRouteScenario(PhantomTestContext context, String mode) throws Exception
	{
		final boolean gatekeeper = mode.equals("gatekeeper");
		final boolean sameAnchor = mode.equals("standpoint") || mode.equals("height");
		final var topology = _production.topology();
		final var arrival = topology.findAnchor(sameAnchor ? "population.farming.elf.20534" : gatekeeper ? "generated.farm.ea72d768f3c8ec3d79c1a530.anchor" : "generated.farm.c4067c834f12b99e976909a2.anchor").orElseThrow();
		final var departure = sameAnchor ? arrival : topology.findAnchor(gatekeeper ? "generated.route.live002.gludio.arrival.anchor" : "population.ingress.elf.03").orElseThrow();
		final int npcId = _production.knowledge().snapshot().spawnAreasByNpc().entrySet().stream().filter(value -> value.getValue().stream().anyMatch(area -> arrival.nodeId().equals(area.topologyNodeId()))).mapToInt(Map.Entry::getKey).sorted().findFirst().orElseThrow();
		final var farm = new ProductionFarmSelection(npcId, arrival);
		final var route = org.l2jmobius.gameserver.phantoms.background.PhantomNormalGatekeeperTravel.load(Path.of("data/phantoms/travel/high-five-normal-gk.xml"), topology);
		final var authority = new L2jPhantomBackgroundAuthority(_production::knowledge, _production::topology, _production::progression, _production::commerce, route);
		PhantomProfile profile = null;
		PhantomBackgroundService background = null;
		PhantomMaterializationService materialization = null;
		ProductionPlayerFixture fixture = null;
		try
		{
			fixture = openProductionPlayerFixture(departure, 7, PlayerClass.ELVEN_FIGHTER, farm);
			profile = _repository.create(fixture.player().getObjectId());
			final var goal = goal(npcId, arrival.id());
			final var goals = new PhantomGoalStateStore(_repository);
			goals.insert(profile.profileId(), goal);
			final var transaction = new PhantomBackgroundTransaction();
			final var ref = new AtomicReference<PhantomMaterializationService>();
			background = new PhantomBackgroundService(_repository, goals, PhantomIdentityLeaseRegistry.getInstance(), transaction, authority, new PhantomBackgroundCompetitionRegistry(), noSignals(), ref::get);
			PhantomAssertions.assertTrue(background.start(), "Route scenario native background unavailable.");
			final var metrics = new PhantomMetrics();
			materialization = new PhantomMaterializationService(_repository, PhantomIdentityLeaseRegistry.getInstance(), metrics, new PhantomDiagnosticTrace(false, 64, 16, metrics), 1, _ -> {}, background, 5_000, 10_000);
			ref.set(materialization);
			PhantomAssertions.assertTrue(materialization.start(), "Route scenario native materialization unavailable.");
			final var captured = authority.capture(profile.profileId(), fixture.player(), goal, null);
			fixture.player().storeMe();
			PhantomAssertions.assertEquals(Status.SUCCESS, captureTestBaseline(transaction, captured, goal).status(), "Route scenario factual baseline failed.");
			fixture.releaseRuntime();
			PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, materialization.materialize(profile.profileId()).status(), "Route scenario native materialize failed.");
			if (mode.equals("standpoint"))
			{
				context.record("m1.T08.nativeDestination", PhantomM1GeometryChecks.canonicalFarmStandpoint(context, profile.profileId(), materialization, background, route, goal, noSignals()));
			}
			else if (mode.equals("height")) { PhantomM1GeometryChecks.wrongHeightCanonicalFarm(context, profile.profileId(), materialization, background, route, goal, noSignals()); }
			else if (gatekeeper)
			{
				PhantomM1TravelChecks.missingGatekeeper(context, profile.profileId(), _repository, materialization, background, route, goal, new PhantomHistoricalBackgroundPlanner(_production.knowledge(), topology, authority), noSignals());
			}
			else
			{
				PhantomM1GeometryChecks.unsafeWaterRoute(context, profile.profileId(), materialization, background, route, goal, noSignals(), "bridge.099703149806ba77806cce2c");
			}
		}
		finally
		{
			if (materialization != null) { materialization.shutdown(); }
			if (background != null) { background.beginStop(); background.finishStop(); }
			if (profile != null) { deleteProfile(profile); }
			if (fixture != null) { fixture.close(); }
		}
	}

	private void testNativeTimerCompletion(PhantomTestContext context) throws Exception
	{
		testNativeTimerCompletion(context, "holder");
	}

	private void testNativeTimerCompletion(PhantomTestContext context, String kind) throws Exception
	{
		final var beforeStore = new CountDownLatch(1);
		final var releaseStore = new CountDownLatch(1);
		try (var runtime = createRuntimeFixture(_environment.primary().objectId(), new PhantomBackgroundTransaction(), point ->
		{
			if (point == FailurePoint.BEFORE_STORE_OPERATION)
			{
				beforeStore.countDown();
				try { if (!releaseStore.await(10, TimeUnit.SECONDS)) { throw new AssertionError("Q06 native BEFORE_STORE barrier timed out."); } }
				catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new AssertionError(failure); }
			}
		}))
		{
			PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, runtime.materialization().materialize(runtime.profileId()).status(), "Q06 native timer fixture did not materialize.");
			final Player player;
			try (var action = runtime.materialization().tryAcquireAction(runtime.profileId()).orElseThrow()) { player = action.player(); }
			try
			{
				switch (kind)
				{
					case "quest" -> PhantomM1TimerChecks.runQuest(context, player, runtime.materialization(), runtime.profileId(), beforeStore, releaseStore);
					case "cancel" -> PhantomM1TimerChecks.runCancel(context, player, runtime.materialization(), runtime.profileId(), beforeStore, releaseStore);
					case "repeat" -> PhantomM1TimerChecks.runRepeating(context, player, runtime.materialization(), runtime.profileId(), beforeStore, releaseStore);
					case "chain" -> PhantomM1TimerChecks.runEarnedChain(context, player, runtime.materialization(), runtime.profileId(), beforeStore, releaseStore);
					case "holder" -> PhantomM1TimerChecks.run(context, player, runtime.materialization(), runtime.profileId(), beforeStore, releaseStore);
					case "buff" -> PhantomM1BuffChecks.run(context, player, runtime.materialization(), runtime.profileId(), beforeStore, releaseStore, _environment::cleanupLoadedPlayer);
					case "buff-expiry" -> PhantomM1BuffChecks.expiry(context, player, runtime.materialization(), runtime.profileId(), beforeStore, releaseStore);
					case "zone-effect" -> PhantomM1BuffChecks.zone(context, player, runtime.materialization(), runtime.profileId(), beforeStore, releaseStore, false);
					case "zone-damage" -> PhantomM1BuffChecks.zone(context, player, runtime.materialization(), runtime.profileId(), beforeStore, releaseStore, true);
					default -> throw new IllegalArgumentException("Unknown timer fixture: " + kind);
				}
			}
			finally { releaseStore.countDown(); }
		}
	}

	private void testNativeEarlyLoadOwnership() throws Exception
	{
		final var runtimeRef = new AtomicReference<RuntimeFixture>();
		try (var runtime = createRuntimeFixture(_environment.primary().objectId(), new PhantomBackgroundTransaction(), point ->
		{
			if (point == FailurePoint.AFTER_PLAYER_LOAD)
			{
				try
				{
					final var fixture = runtimeRef.get();
					final var actor = nativeActor(fixture.materialization(), fixture.profileId());
					final Player player = actor.getPlayer();
					PhantomAssertions.assertTrue(player.isNativeWorkManaged() && player.getNativeWorkOwner().isCurrent(), "Q15 native load did not retain exact owner before reachable callbacks.");
					PhantomAssertions.assertFalse(Thread.holdsLock(actor), "Q15 native materialization holds actor lifecycle monitor across abort/drain-capable native boundary.");
					final Field field = PhantomMaterializationService.class.getDeclaredField("_activeByProfile"); field.setAccessible(true);
					final Object entry = ((Map<?, ?>) field.get(fixture.materialization())).get(fixture.profileId());
					PhantomAssertions.assertFalse(Thread.holdsLock(entry), "Q15 native materialization holds service entry monitor across abort/drain-capable native boundary.");
				}
				catch (ReflectiveOperationException failure) { throw new AssertionError("Q15 exact native fixture inspection failed", failure); }
				catch (Exception failure) { throw new AssertionError(failure); }
			}
		}))
		{
			runtimeRef.set(runtime);
			PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, runtime.materialization().materialize(runtime.profileId()).status(), "Q15 native load fixture did not materialize.");
		}
	}

	private void testNativeFirstFailure(boolean removed) throws Exception
	{
		final var attempt = new AtomicInteger();
		try (var runtime = createRuntimeFixture(_environment.primary().objectId(), new PhantomBackgroundTransaction(), point ->
		{
			if (point == FailurePoint.BEFORE_STORE_OPERATION)
			{
				final int current = attempt.incrementAndGet();
				if (current <= 2) { throw new IllegalArgumentException(current == 1 ? "FIRST_NATIVE_STORE_A" : "LATEST_NATIVE_STORE_B"); }
			}
		}))
		{
			PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, runtime.materialization().materialize(runtime.profileId()).status(), "First-failure native fixture did not materialize.");
			final long epoch = runtime.materialization().find(runtime.profileId()).orElseThrow().materializedAtNanos();
			PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.CLEANUP_FAILED_RETAINED, runtime.materialization().dematerialize(runtime.profileId()).status(), "First failure did not retain ownership.");
			PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.CLEANUP_FAILED_RETAINED, runtime.materialization().retryCleanup(runtime.profileId()).status(), "Second failure did not retain ownership.");
			if (removed)
			{
				PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, runtime.materialization().retryCleanup(runtime.profileId()).status(), "Third native cleanup did not finish.");
				PhantomAssertions.assertTrue(runtime.materialization().find(runtime.profileId()).isEmpty(), "Completed runtime entry was not removed.");
			}
			final String evidence = runtime.materialization().snapshot().toString();
			PhantomAssertions.assertTrue(evidence.contains("FIRST_NATIVE_STORE_A") && evidence.contains("LATEST_NATIVE_STORE_B"), "Actual lifecycle diagnostics lost first/latest native exceptions after retry/removal: " + evidence);
			PhantomAssertions.assertTrue(evidence.contains(Long.toString(runtime.profileId())) && evidence.contains(Long.toString(epoch)), "Detached incident lost exact profile/epoch.");
		}
	}

	private void testNativeIncidentBounds() throws Exception
	{
		final var large = new IllegalArgumentException("Ошибка\n😀".repeat(10000));
		Throwable cause = large;
		for (int index = 0; index < 20; index++)
		{
			final var next = new IllegalStateException("CAUSE_" + index + "Я".repeat(200));
			cause.initCause(next); cause = next;
			large.addSuppressed(new IllegalStateException("SUPPRESSED_" + index));
		}
		final var hostile = new IllegalStateException()
		{
			@Override public String getMessage() { throw new AssertionError("TEST_DIAGNOSTIC_ACCESSOR"); }
		};
		final var attempt = new AtomicInteger();
		try (var runtime = createRuntimeFixture(_environment.primary().objectId(), new PhantomBackgroundTransaction(), point ->
		{
			if (point == FailurePoint.BEFORE_STORE_OPERATION)
			{
				final int current = attempt.incrementAndGet();
				if (current == 1) { throw large; }
				if (current == 2) { throw hostile; }
			}
		}))
		{
			PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, runtime.materialization().materialize(runtime.profileId()).status(), "Bounded incident fixture did not materialize.");
			PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.CLEANUP_FAILED_RETAINED, runtime.materialization().dematerialize(runtime.profileId()).status(), "Huge native exception lost ownership.");
			final var first = runtime.materialization().find(runtime.profileId()).orElseThrow().firstCleanupIncident();
			PhantomAssertions.assertTrue(first.truncated() && first.nodes() <= 8 && first.frames() <= 32 && first.message().length() <= 160 && first.toString().getBytes(StandardCharsets.UTF_8).length <= 8192, "Native incident exceeded detached bounds.");
			PhantomAssertions.assertFalse(first.message().contains("\n"), "Native message was not sanitized.");
			PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.CLEANUP_FAILED_RETAINED, runtime.materialization().retryCleanup(runtime.profileId()).status(), "Diagnostic accessor masked actual native failure.");
			final var latest = runtime.materialization().find(runtime.profileId()).orElseThrow().latestCleanupIncident();
			PhantomAssertions.assertTrue(latest.truncated() && latest.detail().contains("DIAGNOSTIC_FORMAT_UNAVAILABLE"), "Hostile diagnostic accessor was not bounded.");
			for (var component : first.getClass().getRecordComponents())
			{
				PhantomAssertions.assertFalse(Player.class.isAssignableFrom(component.getType()) || Throwable.class.isAssignableFrom(component.getType()), "Detached incident retains native runtime objects.");
			}
		}
	}

	private void testNativeAbortPrimary() throws Exception
	{
		final var armed = new AtomicBoolean();
		final var owner = new AtomicReference<PhantomMaterializationService>();
		final var loadedPlayer = new AtomicReference<Player>();
		final var primary = new AssertionError("TEST_MATERIALIZE_PRIMARY_A");
		final var secondary = new AssertionError("TEST_ABORT_CANCEL_B");
		final Field futureField = Player.class.getDeclaredField("_inventoryUpdateTask"); futureField.setAccessible(true);
		try (var runtime = createRuntimeFixture(_environment.primary().objectId(), new PhantomBackgroundTransaction(), point ->
		{
			if (armed.get() && (point == FailurePoint.AFTER_PLAYER_LOAD))
			{
				try
				{
					loadedPlayer.set(nativeActor(owner.get(), _repository.findByCharacterObjectId(_environment.primary().objectId()).orElseThrow().profileId()).getPlayer());
					futureField.set(loadedPlayer.get(), failingNativeCancellation(secondary));
				}
				catch (Exception failure) { throw new AssertionError(failure); }
				throw primary;
			}
		}))
		{
			owner.set(runtime.materialization());
			try
			{
				armed.set(true); Throwable observed = null;
				try { runtime.materialization().materialize(runtime.profileId()); }
				catch (RuntimeException | Error failure) { observed = failure; }
				PhantomAssertions.assertTrue(observed == primary, "Materialization/abort swallowed or masked primary Error: " + observed);
				PhantomAssertions.assertTrue(Arrays.asList(primary.getSuppressed()).contains(secondary), "Materialization primary Error lost abort secondary Error.");
				PhantomAssertions.assertTrue(runtime.materialization().find(runtime.profileId()).orElseThrow().identityLeaseRetained(), "Abort Error lost retained identity ownership.");
			}
			finally
			{
				armed.set(false);
				if (loadedPlayer.get() != null)
				{
					futureField.set(loadedPlayer.get(), null);
					final var scope = (org.l2jmobius.gameserver.phantoms.player.PhantomNativeWorkScope) loadedPlayer.get().getNativeWorkOwner();
					if (scope.firstNativeIncident() != null)
					{
						// Expected negative TEST lifetime is discarded; its native failure stays fenced.
						PhantomAssertions.assertEquals(0, scope.outstanding(), "E04 failed TEST discard requires zero running/queued work.");
						PhantomAssertions.assertEquals(0, scope.pendingTimers(), "E04 failed TEST discard requires zero native producers.");
						final var actor = nativeActor(runtime.materialization(), runtime.profileId());
						PhantomAssertions.assertTrue(actor.getPlayer() == loadedPlayer.get() && !loadedPlayer.get().hasHeadlessOutboundSession(), "E04 exact early TEST lifetime changed.");
						final Field leaseField = actor.getClass().getDeclaredField("_identityLease"); leaseField.setAccessible(true);
						final var identity = (PhantomIdentityLeaseRegistry.Lease) leaseField.get(actor);
						PhantomAssertions.assertTrue(identity != null && identity.objectId() == loadedPlayer.get().getObjectId() && !identity.isClosed(), "E04 retained TEST lease changed.");
						loadedPlayer.get().stopAllTasks(); loadedPlayer.get().deleteMe(); loadedPlayer.get().detachNativeWorkOwner(scope); identity.close();
						PhantomAssertions.assertEquals(null, World.getInstance().getPlayer(identity.objectId()), "E04 failed TEST lifetime retained World identity.");
						PhantomAssertions.assertFalse(scope.open(), "E04 discard reopened failed native scope.");
					}
				}
			}
		}
	}

	private void testNativeCleanupPrimary() throws Exception
	{
		final var armed = new AtomicBoolean();
		final var player = new AtomicReference<Player>();
		final var primary = new IllegalArgumentException("TEST_BEFORE_STORE_A");
		final var secondary = new AssertionError("TEST_CANCEL_TASK_B");
		final Field futureField = Player.class.getDeclaredField("_inventoryUpdateTask"); futureField.setAccessible(true);
		try (var runtime = createRuntimeFixture(_environment.primary().objectId(), new PhantomBackgroundTransaction(), point ->
		{
			if (armed.get() && (point == FailurePoint.BEFORE_STORE_OPERATION))
			{
				try { futureField.set(player.get(), failingNativeCancellation(secondary)); }
				catch (IllegalAccessException failure) { throw new AssertionError(failure); }
				throw primary;
			}
		}))
		{
			PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, runtime.materialization().materialize(runtime.profileId()).status(), "Native finalizer fixture did not materialize.");
			try (var action = runtime.materialization().tryAcquireAction(runtime.profileId()).orElseThrow()) { player.set(action.player()); }
			try
			{
				armed.set(true);
				Throwable observed = null;
				try { nativeActor(runtime.materialization(), runtime.profileId()).cleanup(); }
				catch (RuntimeException | Error failure) { observed = failure; }
				PhantomAssertions.assertTrue(observed == primary, "Native stopAllTasks finalizer masked cleanup primary: " + observed);
				PhantomAssertions.assertTrue(Arrays.asList(primary.getSuppressed()).contains(secondary), "Native cleanup primary lost Error from finalizer.");
			}
			finally { armed.set(false); futureField.set(player.get(), null); }
		}
	}

	private static org.l2jmobius.gameserver.phantoms.player.PhantomMaterializedPlayer nativeActor(PhantomMaterializationService service, long profileId) throws Exception
	{
		final Field activeField = PhantomMaterializationService.class.getDeclaredField("_activeByProfile"); activeField.setAccessible(true);
		final Object entry = ((Map<?, ?>) activeField.get(service)).get(profileId);
		final Field actorField = entry.getClass().getDeclaredField("_materializedPlayer"); actorField.setAccessible(true);
		return (org.l2jmobius.gameserver.phantoms.player.PhantomMaterializedPlayer) actorField.get(entry);
	}

	private static java.util.concurrent.ScheduledFuture<Object> failingNativeCancellation(Error failure)
	{
		return new java.util.concurrent.ScheduledFuture<>()
		{
			@Override public long getDelay(TimeUnit unit) { return 0; }
			@Override public int compareTo(java.util.concurrent.Delayed other) { return 0; }
			@Override public boolean cancel(boolean interrupt) { throw failure; }
			@Override public boolean isCancelled() { return false; }
			@Override public boolean isDone() { return false; }
			@Override public Object get() { return null; }
			@Override public Object get(long amount, TimeUnit unit) { return null; }
		};
	}

	private void testNativeStorePrimary(boolean resume) throws Exception
	{
		final Player player = Player.load(_environment.primary().objectId());
		final Object ownerKey = new Object();
		final var secondary = new IllegalArgumentException("TEST_BOUNDARY_AFTER_STORE_B");
		final var completed = new AtomicBoolean(true);
		final var faults = new AtomicInteger();
		final var snapshot = new Player.OwnedStoreSnapshot(player.getCurrentHp(), player.getMaxHp(), player.getCurrentMp(), player.getMaxMp(), player.getCurrentCp(), player.getMaxCp(), player.getX(), player.getY(), player.getZ(), player.getHeading(), player.getLevel(), player.getExp(), player.getSp(), player.getExpBeforeDeath(), player.getActiveClass(), player.getRace().ordinal(), player.getClassIndex(), player.getStat().getBaseLevel(), player.getStat().getBaseExp(), player.getStat().getBaseSp());
		try
		{
			try (var lease = PhantomIdentityLeaseRegistry.getInstance().tryAcquire(player.getObjectId(), PhantomIdentityLeaseRegistry.OwnerKind.PHANTOM);
				var attachment = player.attachOwnedStoreBoundary(new Player.OwnedStoreBoundary()
				{
					@Override public Object ownerKey() { return ownerKey; }
					@Override public boolean hasPending() { return resume; }
					@Override public Player.OwnedStoreSnapshot beforeStore() { return snapshot; }
					@Override public Player.OwnedStoreSnapshot beforePendingStore(long goalId, long revision) { return snapshot; }
					@Override public void afterStore(boolean nativeCompleted) { completed.set(nativeCompleted); throw secondary; }
				});
				var sqlFailure = injectNativeStoreSqlFailure(false, faults))
			{
				Throwable observed = null;
				try { if (resume) { player.resumePendingOwnedStore(ownerKey, 1, 1); } else { player.store(false); } }
				catch (RuntimeException | Error failure) { observed = failure; }
				PhantomAssertions.assertEquals(1, faults.get(), "Primary fixture did not reach the actual native SQL writer.");
				PhantomAssertions.assertFalse(completed.get(), "Native writer failure was reported completed.");
				PhantomAssertions.assertTrue((observed instanceof IllegalStateException) && "OWNED_STORE_NATIVE_BASE_FAILED".equals(observed.getMessage()), "Boundary finalizer masked the native SQL primary: " + observed);
				PhantomAssertions.assertTrue(Arrays.asList(observed.getSuppressed()).contains(secondary), "Native primary did not retain the secondary boundary failure.");
			}
		}
		finally { _environment.cleanupLoadedPlayer(player); }
	}

	private void testNativeRewardCompletion(PhantomTestContext context, boolean mage, boolean timeout) throws Exception
	{
		testNativeRewardCompletion(context, mage, timeout, false);
	}

	private void testNativeRewardCompletion(PhantomTestContext context, boolean mage, boolean timeout, boolean delayedQuest) throws Exception
	{
		testNativeRewardCompletion(context, mage, timeout, delayedQuest, false);
	}

	private void testNativeRewardCompletion(PhantomTestContext context, boolean mage, boolean timeout, boolean delayedQuest, boolean activeCheckpoint) throws Exception
	{
		testNativeRewardCompletion(context, mage, timeout, delayedQuest, activeCheckpoint, false);
	}

	private void testNativeRewardCompletion(PhantomTestContext context, boolean mage, boolean timeout, boolean delayedQuest, boolean activeCheckpoint, boolean stockQuest) throws Exception
	{
		testNativeRewardCompletion(context, mage, timeout, delayedQuest, activeCheckpoint, stockQuest, false);
	}

	private void testNativeRewardCompletion(PhantomTestContext context, boolean mage, boolean timeout, boolean delayedQuest, boolean activeCheckpoint, boolean stockQuest, boolean nativeSensor) throws Exception
	{
		testNativeRewardCompletion(context, mage, timeout, delayedQuest, activeCheckpoint, stockQuest, nativeSensor, false);
	}

	private void testNativeRewardCompletion(PhantomTestContext context, boolean mage, boolean timeout, boolean delayedQuest, boolean activeCheckpoint, boolean stockQuest, boolean nativeSensor, boolean automaticRetry) throws Exception
	{
		if (stockQuest) { PhantomM1Q266NativeFixture.load(context); }
		final String evidence = delayedQuest ? "Q05.asyncQuest" : timeout ? "Q05" : mage ? "Q01.cast" : "Q01.hit";
		final var elvenFarm = _production.topology().findAnchor("population.farming.elf.20534").orElseThrow();
		final int nativeNpcId = stockQuest ? PhantomM1Q266NativeFixture.MONSTER_ID : 20534;
		final var nativeAnchor = stockQuest ? _production.topology().snapshot().anchors().stream()
			.filter(anchor -> (anchor.role() == PhantomTopologyAnchorRole.FARMING) && (anchor.point().instanceId() == 0))
			.filter(anchor -> _production.knowledge().snapshot().spawnAreasByNpc().getOrDefault(nativeNpcId, List.of()).stream().anyMatch(area -> (area.totalConfiguredAmount() > 0) && anchor.nodeId().equals(area.topologyNodeId())))
			.min(Comparator.comparingLong((PhantomTopologyAnchor anchor) -> elvenFarm.point().distanceSquared2D(anchor.point())).thenComparing(PhantomTopologyAnchor::id)).orElseThrow() : elvenFarm;
		final var farm = new ProductionFarmSelection(nativeNpcId, nativeAnchor);
		context.record(evidence + ".exactNativeTarget", "npc=" + nativeNpcId + " anchor=" + nativeAnchor.id());
		try (var fixture = openProductionPlayerFixture(farm.anchor(), 7, mage ? PlayerClass.ELVEN_MAGE : PlayerClass.ELVEN_FIGHTER, farm))
		{
			final var profile = _repository.create(fixture.player().getObjectId());
			final long id = profile.profileId();
			final var goals = new PhantomGoalStateStore(_repository); goals.insert(id, fixture.goal());
			final var hitInside = new CountDownLatch(1);
			final var releaseHit = new CountDownLatch(1);
			final var hitFinished = new CountDownLatch(1);
			final var questInside = new CountDownLatch(1);
			final var releaseQuest = new CountDownLatch(1);
			final var questFinished = new CountDownLatch(1);
			final var questFailure = new AtomicReference<Throwable>();
			final var questRewardCount = new java.util.concurrent.atomic.AtomicLong(-1);
			final var prepared = new CountDownLatch(1);
			final var releasePrepare = new CountDownLatch(1);
			final var armed = new AtomicBoolean();
			final var livePlayer = new AtomicReference<Player>();
			final var storeContext = new AtomicReference<String>();
			final var rewardProducer = new AtomicReference<String>();
			final var transaction = new PhantomBackgroundTransaction(DatabaseFactory::getConnection, allocator(new AtomicInteger()), point ->
			{
				if (armed.get() && (point == FaultPoint.AFTER_OWNED_PREPARE))
				{
					prepared.countDown();
					try { if (!releasePrepare.await(10, TimeUnit.SECONDS)) { throw new AssertionError("Native PREPARE barrier timed out."); } }
					catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new AssertionError(failure); }
				}
			});
			final var owner = new AtomicReference<PhantomMaterializationService>();
			final var background = new PhantomBackgroundService(_repository, goals, PhantomIdentityLeaseRegistry.getInstance(), transaction, _production.authority(), new PhantomBackgroundCompetitionRegistry(), noSignals(), owner::get);
			final var metrics = new PhantomMetrics();
			final var materialization = new PhantomMaterializationService(_repository, PhantomIdentityLeaseRegistry.getInstance(), metrics, new PhantomDiagnosticTrace(false, 64, 16, metrics), 1, point ->
			{
				if ((point == FailurePoint.BEFORE_STORE_OPERATION) && (livePlayer.get() != null)) { storeContext.set(nativeBackgroundContext(livePlayer.get())); }
			}, background, timeout ? 50 : 5000, 10000);
			owner.set(materialization); background.start(); materialization.start();
			final var candidates = new PhantomCandidateRegistry(); candidates.seal();
			final var handlers = new PhantomStepHandlerRegistry(); handlers.seal();
			final var engine = new PhantomDecisionEngine(goals, candidates, handlers, metrics, 1); engine.start(); engine.attach(id);
			final var visible = new PhantomVisibleAutoPlay(materialization, () -> engine, _ -> true);
			final var cleanupResult = new AtomicReference<PhantomMaterializationService.DematerializeResult>();
			final var cleanupFailure = new AtomicReference<Throwable>();
			final var activeStoreCompleted = new AtomicBoolean();
			final Thread cleanup = new Thread(() ->
			{
				try
				{
					if (activeCheckpoint) { livePlayer.get().store(false); activeStoreCompleted.set(true); }
					else { cleanupResult.set(materialization.dematerialize(id)); }
				}
				catch (Throwable failure) { cleanupFailure.set(failure); }
			}, "m1-native-reward-cleanup");
			final var monster = new org.l2jmobius.gameserver.model.actor.instance.Monster(NpcData.getInstance().getTemplate(stockQuest ? PhantomM1Q266NativeFixture.MONSTER_ID : farm.npcId()))
			{
				@Override protected synchronized void calculateRewards(Creature attacker)
				{
					rewardProducer.set(Arrays.stream(Thread.currentThread().getStackTrace()).map(StackTraceElement::getMethodName).limit(96).reduce((left, right) -> left + ">" + right).orElse(""));
					hitInside.countDown();
					try { if (!releaseHit.await(10, TimeUnit.SECONDS)) { throw new AssertionError("Native reward barrier timed out."); } }
					catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new AssertionError(failure); }
					try { super.calculateRewards(attacker); }
					finally { hitFinished.countDown(); }
				}
			};
			final var questListener = new org.l2jmobius.gameserver.model.events.listeners.ConsumerEventListener(monster, org.l2jmobius.gameserver.model.events.EventType.ON_ATTACKABLE_KILL,
				(org.l2jmobius.gameserver.model.events.holders.actor.npc.attackable.OnAttackableKill event) ->
				{
					questInside.countDown();
					try
					{
						if (!releaseQuest.await(10, TimeUnit.SECONDS)) { throw new AssertionError("Native quest callback barrier timed out."); }
						// Existing native quest reward writer, reached only by Attackable.doDie's real delayed event.
						if (!stockQuest) { org.l2jmobius.gameserver.model.script.Quest.giveItems(event.getAttacker(), 1334, 1); }
						questRewardCount.set(event.getAttacker().getInventory().getInventoryItemCount(1334, -1));
					}
					catch (Throwable failure) { questFailure.set(failure); }
					finally { questFinished.countDown(); }
				}, this);
			if (delayedQuest) { monster.addListener(questListener); }
			try
			{
				if (mage)
				{
					final var learn = SkillTreeData.getInstance().getCompleteClassSkillTree(PlayerClass.ELVEN_MAGE).values().stream()
						.filter(candidate -> (candidate.getSkillId() == 1177) && candidate.isAutoGet() && (candidate.getGetLevel() <= 7))
						.max(Comparator.comparingInt(candidate -> candidate.getSkillLevel())).orElseThrow(() -> new AssertionError("Native Wind Strike is absent from the current mage class tree."));
					fixture.player().addSkill(SkillData.getInstance().getSkill(learn.getSkillId(), learn.getSkillLevel()), true);
					context.record(evidence + ".stockSkill", learn.getSkillId() + ":" + learn.getSkillLevel());
				}
				if (stockQuest) { PhantomM1Q266NativeFixture.prepare(context, fixture.player()); }
				fixture.player().storeMe();
				final var baseline = _production.authority().capture(id, fixture.player(), fixture.goal(), null);
				PhantomAssertions.assertEquals(Status.SUCCESS, captureTestBaseline(transaction, baseline, fixture.goal()).status(), "Native reward baseline rejected.");
				fixture.releaseRuntime();
				PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, materialization.materialize(id).status(), "Native reward Player did not materialize.");
				final Player player;
				try (var action = materialization.tryAcquireAction(id).orElseThrow()) { player = action.player(); player.getStatus().stopHpMpRegeneration(); }
				livePlayer.set(player); context.record(evidence + ".contextBeforeAttack", nativeBackgroundContext(player));
				final var evidenceOwner = player.getNativeWorkOwner();
				if (stockQuest) { PhantomM1Q266NativeFixture.assertStarted(context, player); }
				final long experience = player.getExp(); final long sp = player.getSp();
				monster.disableCoreAI(true); monster.setInstanceId(player.getInstanceId());
				final var spawn = new org.l2jmobius.gameserver.model.spawns.Spawn(monster.getTemplate()); spawn.setXYZ(player.getX() + 20, player.getY(), player.getZ()); monster.setSpawn(spawn);
				monster.setCurrentHpMp(monster.getMaxHp(), monster.getMaxMp()); monster.spawnMe(player.getX() + 20, player.getY(), player.getZ());
				if (stockQuest) { PhantomM1Q266NativeFixture.assertKillReady(context, player, monster); }
				PhantomAssertions.assertTrue(visible.start(id, fixture.goal()), "Native reward AutoPlay did not start.");
				if (mage)
				{
					final long epoch = materialization.find(id).orElseThrow().materializedAtNanos();
					AutoUseTaskManager.getInstance().stopAutoUseTask(player);
					PhantomAssertions.assertFalse(visible.running(id, fixture.goal()), "Native mage partial pair was reported healthy.");
					PhantomAssertions.assertTrue(visible.start(id, fixture.goal()), "Native mage owner could not repair AutoUse.");
					PhantomAssertions.assertEquals(epoch, materialization.find(id).orElseThrow().materializedAtNanos(), "Native mage repair changed epoch.");
				}
				final boolean lethal = hitInside.await(35, TimeUnit.SECONDS);
				final var attributed = monster.getAggroList().get(player);
				context.record(evidence + ".nativeState", "mage=" + player.isMageClass() + " hp=" + monster.getCurrentHp() + "/" + monster.getMaxHp() + " damage=" + (attributed == null ? 0 : attributed.getDamage()) + " mp=" + player.getCurrentMp() + " skills=" + player.getAutoUseSettings().getAutoSkills() + " action=" + player.getAI().getIntention() + " casting=" + player.isCastingNow() + " attack=" + player.isAttackingNow() + " target=" + player.getTarget() + " pair=" + visible.running(id, fixture.goal()));
				PhantomAssertions.assertTrue(lethal, "Stock native AutoPlay did not reach a real lethal damage callback.");
				if (delayedQuest)
				{
					final long beforeQuest = player.getInventory().getInventoryItemCount(1334, -1);
					visible.stop(id); releaseHit.countDown();
					PhantomAssertions.assertTrue(hitFinished.await(5, TimeUnit.SECONDS) && questInside.await(5, TimeUnit.SECONDS), "Actual Attackable delayed quest event was not entered.");
					if (activeCheckpoint)
					{
						try (var action = materialization.tryAcquireAction(id).orElseThrow())
						{
							player.abortAttack(); player.abortCast(); player.stopMove(null);
							player.getAI().setIntention(org.l2jmobius.gameserver.ai.Intention.IDLE);
							player.getAI().clientStopAutoAttack();
							org.l2jmobius.gameserver.taskmanagers.AttackStanceTaskManager.getInstance().removeAttackStanceTask(player);
							player.getAI().setAutoAttacking(false);
						}
						context.record(evidence + ".activeStoreContext", nativeBackgroundContext(player));
					}
					armed.set(true); cleanup.start();
					final boolean crossedQuest = prepared.await(2, TimeUnit.SECONDS);
					context.record(evidence + ".preparedWhileQuestActive", crossedQuest);
					context.record(evidence + ".pendingSnapshot", materialization.find(id).map(Object::toString).orElse("removed"));
					context.record(evidence + ".ownershipBeforeReward", "entry=" + materialization.find(id).isPresent() + " world=" + (World.getInstance().getPlayer(player.getObjectId()) == player) + " identity=" + PhantomIdentityLeaseRegistry.getInstance().getOwnerKind(player.getObjectId()) + " outbound=" + player.hasHeadlessOutboundSession());
					releaseQuest.countDown();
					PhantomAssertions.assertTrue(questFinished.await(5, TimeUnit.SECONDS) && questFailure.get() == null, "Real native quest reward did not finish: " + questFailure.get());
					releasePrepare.countDown(); cleanup.join(10000);
					if (activeCheckpoint)
					{
						PhantomAssertions.assertTrue(activeStoreCompleted.get() && cleanupFailure.get() == null, "Actual active owned store did not complete: " + cleanupFailure.get());
						PhantomAssertions.assertTrue(materialization.find(id).isPresent() && (World.getInstance().getPlayer(player.getObjectId()) == player), "Active checkpoint released the materialized lifetime.");
						cleanupResult.set(materialization.dematerialize(id));
					}
					context.record(evidence + ".cleanup", cleanupResult.get() == null ? String.valueOf(cleanupFailure.get()) : cleanupResult.get().status());
					context.record(evidence + ".nativeQuestReward", "before=" + beforeQuest + " afterCallback=" + questRewardCount.get() + " pendingOwnedStore=" + player.hasPendingOwnedStore());
					context.record(evidence + ".backgroundInventory", transaction.load(id).state().inventory());
					PhantomAssertions.assertFalse(crossedQuest, "Owned PREPARE crossed an active native ON_ATTACKABLE_KILL quest reward; ownership was released before the earned callback completed.");
					PhantomAssertions.assertTrue(cleanupFailure.get() == null && cleanupResult.get() != null, "Native delayed reward cleanup did not complete: " + cleanupFailure.get());
					PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, cleanupResult.get().status(), "Native delayed reward retained a failure.");
					if (!stockQuest) { PhantomAssertions.assertEquals(beforeQuest + 1, questRewardCount.get(), "Native quest reward was not applied exactly once before canonical delete."); }
					final Player reloaded = stockQuest ? PhantomM1Q266NativeFixture.reloadAndAssert(context, player) : Player.load(player.getObjectId());
					try
					{
						if (!stockQuest) { PhantomAssertions.assertEquals(beforeQuest + 1, reloaded.getInventory().getInventoryItemCount(1334, -1), "Native quest reward was lost after Player.load."); }
						PhantomAssertions.assertEquals(player.getExp(), reloaded.getExp(), "Native delayed reward EXP diverged after reload.");
						if (stockQuest) { PhantomM1Q266NativeFixture.cleanupQuest(context, reloaded); }
					}
					finally { _environment.cleanupLoadedPlayer(reloaded); }
					return;
				}
				visible.stop(id); armed.set(true); cleanup.start();
				final boolean crossedActiveCompletion = prepared.await(2, TimeUnit.SECONDS);
				final var draining = materialization.find(id).orElseThrow();
				context.record(evidence + ".preparedWhileHitActive", crossedActiveCompletion);
				context.record(evidence + ".draining", draining);
				if (timeout)
				{
					PhantomAssertions.assertTrue(!draining.actionAdmissionOpen() && (draining.admittedActionCount() > 0) && draining.identityLeaseRetained() && draining.worldPresent() && draining.outboundAttached(), "Native timeout lost exact retained ownership before completion.");
				}
				releaseHit.countDown();
				releasePrepare.countDown();
				PhantomAssertions.assertTrue(hitFinished.await(5, TimeUnit.SECONDS), "Real native reward callback did not finish.");
				context.record(evidence + ".nativeReward", "object=" + player.getObjectId() + " expDelta=" + (player.getExp() - experience) + " spDelta=" + (player.getSp() - sp) + " preparedWhileHitActive=" + crossedActiveCompletion);
				releasePrepare.countDown(); cleanup.join(10000);
				context.record(evidence + ".cleanup", cleanupResult.get() == null ? String.valueOf(cleanupFailure.get()) : cleanupResult.get().status());
				context.record(evidence + ".cleanupSnapshot", materialization.find(id).map(Object::toString).orElse("removed"));
				context.record(evidence + ".contextAtStore", storeContext.get());
				PhantomAssertions.assertFalse(crossedActiveCompletion, "Owned PREPARE crossed an active native lethal-hit/reward completion.");
				PhantomAssertions.assertTrue(!cleanup.isAlive() && cleanupFailure.get() == null && cleanupResult.get() != null, "Native reward cleanup did not finish safely.");
				if (timeout)
				{
					PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.CLEANUP_FAILED_RETAINED, cleanupResult.get().status(), "Native completion timeout was silently treated as success.");
					armed.set(false);
					if (automaticRetry)
					{
						final long retryDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
						while (materialization.find(id).isPresent() && (System.nanoTime() < retryDeadline)) { Thread.sleep(10); }
						context.record("Q10.afterActualCompletion", materialization.find(id).map(Object::toString).orElse("stored-and-released"));
						PhantomAssertions.assertTrue(materialization.find(id).isEmpty(), "Actual last native completion did not enqueue a bounded safe cleanup retry after typed drain timeout.");
					}
					else { PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, materialization.retryCleanup(id).status(), "Completed native reward could not be safely stored on cleanup retry."); }
				}
				else { PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, cleanupResult.get().status(), "Native reward cleanup retained a failure; no quiescence PASS is established."); }
				PhantomAssertions.assertTrue(player.getExp() > experience && player.getSp() > sp, "Actual native kill did not award attributed EXP/SP.");
				if (nativeSensor)
				{
					PhantomAssertions.assertTrue((evidenceOwner != null) && (evidenceOwner.evidence() != null), "Successful actual native hit/kill/reward lacks its exact lifetime sensor.");
					final var observed = evidenceOwner.evidence().snapshot();
					context.record("A09.actualNativeSensor", observed.scalarMap());
					PhantomAssertions.assertTrue((observed.damageSequence() > 0) && (observed.killSequence() > 0) && (observed.rewardSequence() > 0) && (observed.expGained() > 0) && (observed.spGained() > 0), "Successful actual native writers were not attributed to their exact owner.");
				}
				context.record(evidence + ".rewardProducer", rewardProducer.get());
				if (mage) { PhantomAssertions.assertTrue(rewardProducer.get().contains("onMagicHitTimer"), "Native mage reward did not originate in the stock cast completion."); }
				final var durable = transaction.load(id).state();
				final var stored = canonical(player.getObjectId());
				PhantomAssertions.assertEquals(player.getExp(), durable.progress().experience(), "Native earned EXP was lost from immutable background store.");
				PhantomAssertions.assertEquals(player.getSp(), durable.progress().skillPoints(), "Native earned SP was lost from immutable background store.");
				PhantomAssertions.assertEquals(durable.progress().experience(), stored.experience(), "Canonical/background native reward EXP diverged.");
				PhantomAssertions.assertEquals(durable.progress().skillPoints(), stored.skillPoints(), "Canonical/background native reward SP diverged.");
			}
			finally
			{
				releaseHit.countDown(); releasePrepare.countDown(); releaseQuest.countDown(); if (cleanup.getState() != Thread.State.NEW) { cleanup.join(10000); }
				if (delayedQuest) { questFinished.await(5, TimeUnit.SECONDS); monster.removeListener(questListener); }
				armed.set(false); visible.stop(id); monster.deleteMe(); engine.beginStop(); engine.finishStop();
				context.record(evidence + ".retry", materialization.retryCleanup(id).status());
				context.record(evidence + ".retrySnapshot", materialization.find(id).map(Object::toString).orElse("removed"));
				materialization.shutdown(); background.beginStop(); background.finishStop(); deleteProfile(profile);
				if (stockQuest)
				{
					if (materialization.find(id).isEmpty())
					{
						final Player questCleanup = Player.load(profile.characterObjectId());
						try { org.l2jmobius.gameserver.model.script.Quest.playerEnter(questCleanup); PhantomM1Q266NativeFixture.cleanupQuest(context, questCleanup); }
						finally { _environment.cleanupLoadedPlayer(questCleanup); }
					}
					PhantomM1Q266NativeFixture.unload(context);
				}
			}
		}
	}

	private static String nativeBackgroundContext(Player player)
	{
		return "instance=" + player.getInstanceId() + " flying=" + player.isFlying() + " flyingMounted=" + player.isFlyingMounted() + " mounted=" + player.isMounted() + " party=" + player.isInParty() + " combat=" + player.isInCombat() + " combatFlag=" + player.isCombatFlagEquipped() + " gm=" + player.isGM() + " premium=" + player.hasPremiumStatus() + " event=" + player.isOnEvent() + " festival=" + player.isFestivalParticipant() + " karma=" + player.getKarma() + " nevit=" + player.getNevitHourglassMultiplier() + " vitality=" + player.getStat().getVitalityMultiplier();
	}

	@SuppressWarnings("unchecked")
	private void testNativeSessionOverlap() throws Exception
	{
		try (var runtime = createRuntimeFixture(_environment.primary().objectId()))
		{
			PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, runtime.materialization().materialize(runtime.profileId()).status(), "Session overlap fixture did not materialize.");
			final Player player;
			try (var action = runtime.materialization().tryAcquireAction(runtime.profileId()).orElseThrow()) { player = action.player(); }
			final var candidates = new PhantomCandidateRegistry(); candidates.seal();
			final var handlers = new PhantomStepHandlerRegistry(); handlers.seal();
			final var engine = new PhantomDecisionEngine(new PhantomGoalStateStore(_repository), candidates, handlers, new PhantomMetrics(), 1);
			engine.start(); engine.attach(runtime.profileId());
			final var visible = new PhantomVisibleAutoPlay(runtime.materialization(), () -> engine, _ -> true);
			final var firstInsideUse = new CountDownLatch(1);
			final var releaseFirst = new CountDownLatch(1);
			final var firstFailure = new AtomicReference<Throwable>();
			final var secondFailure = new AtomicReference<Throwable>();
			final var secondStarted = new AtomicBoolean();
			final Thread first = new Thread(() ->
			{
				try { visible.start(runtime.profileId(), runtime.goal()); }
				catch (Throwable failure) { firstFailure.set(failure); }
			}, "m1-session-first");
			final Thread second = new Thread(() ->
			{
				try { secondStarted.set(visible.start(runtime.profileId(), runtime.goal())); }
				catch (Throwable failure) { secondFailure.set(failure); }
			}, "m1-session-replacement");
			final Set<Player> playPool = new java.util.LinkedHashSet<>(List.of(player))
			{
				@Override public boolean contains(Object actor)
				{
					return super.contains(actor);
				}
			};
			final Set<Player> usePool = new java.util.LinkedHashSet<>(List.of(player))
			{
				private boolean injected;
				@Override public boolean contains(Object actor)
				{
					if (!injected && (actor == player) && (Thread.currentThread() == first))
					{
						injected = true; firstInsideUse.countDown();
						try { if (!releaseFirst.await(5, TimeUnit.SECONDS)) { throw new AssertionError("Session barrier timed out."); } }
						catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new AssertionError(failure); }
						throw new InjectedFailure();
					}
					return super.contains(actor);
				}
			};
			final Field playPoolsField = AutoPlayTaskManager.class.getDeclaredField("POOLS"); playPoolsField.setAccessible(true);
			final Field usePoolsField = AutoUseTaskManager.class.getDeclaredField("POOLS"); usePoolsField.setAccessible(true);
			final var playPools = (Set<Set<Player>>) playPoolsField.get(null);
			final var usePools = (Set<Set<Player>>) usePoolsField.get(null);
			playPools.add(playPool); usePools.add(usePool);
			try
			{
				first.start();
				PhantomAssertions.assertTrue(firstInsideUse.await(5, TimeUnit.SECONDS), "First native start did not reach AutoUse barrier.");
				final Field sessionsField = PhantomVisibleAutoPlay.class.getDeclaredField("_sessions"); sessionsField.setAccessible(true);
				final var sessions = (Map<Long, ?>) sessionsField.get(visible);
				final Object firstSession = sessions.get(runtime.profileId());
				AutoPlayTaskManager.getInstance().stopAutoPlay(player);
				second.start();
				final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
				while ((sessions.get(runtime.profileId()) == firstSession) && (System.nanoTime() < deadline)) { Thread.onSpinWait(); }
				PhantomAssertions.assertTrue((sessions.get(runtime.profileId()) != null) && (sessions.get(runtime.profileId()) != firstSession), "Replacement Session was not published before native registration resumed.");
				releaseFirst.countDown(); first.join(5000); second.join(5000);
				PhantomAssertions.assertFalse(first.isAlive() || second.isAlive(), "Native session overlap deadlocked.");
				PhantomAssertions.assertTrue(firstFailure.get() instanceof InjectedFailure, "First native start did not propagate its injected failure.");
				PhantomAssertions.assertTrue(secondFailure.get() == null && secondStarted.get(), "Replacement native start failed.");
				PhantomAssertions.assertTrue(visible.running(runtime.profileId(), runtime.goal()), "Stale start rollback deleted the replacement Session or pair.");
			}
			finally
			{
				releaseFirst.countDown(); first.join(5000); if (second.getState() != Thread.State.NEW) { second.join(5000); }
				visible.stop(runtime.profileId()); AutoUseTaskManager.getInstance().stopAutoUseTask(player); AutoPlayTaskManager.getInstance().stopAutoPlay(player);
				playPool.add(player); usePool.add(player); playPools.remove(playPool); usePools.remove(usePool);
				engine.beginStop(); engine.finishStop();
			}
		}
	}

	private void testNativePartialPair() throws Exception
	{
		try (var runtime = createRuntimeFixture(_environment.primary().objectId()))
		{
			PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, runtime.materialization().materialize(runtime.profileId()).status(), "Partial pair fixture did not materialize.");
			final var candidates = new PhantomCandidateRegistry(); candidates.seal();
			final var handlers = new PhantomStepHandlerRegistry(); handlers.seal();
			final var goals = new PhantomGoalStateStore(_repository);
			final var engine = new PhantomDecisionEngine(goals, candidates, handlers, new PhantomMetrics(), 1);
			engine.start(); engine.attach(runtime.profileId());
			final var visible = new PhantomVisibleAutoPlay(runtime.materialization(), () -> engine, _ -> true);
			final long epoch = runtime.materialization().find(runtime.profileId()).orElseThrow().materializedAtNanos();
			try
			{
				PhantomAssertions.assertTrue(visible.start(runtime.profileId(), runtime.goal()), "Initial visible pair did not start.");
				final Player player;
				try (var action = runtime.materialization().tryAcquireAction(runtime.profileId()).orElseThrow()) { player = action.player(); }
				AutoUseTaskManager.getInstance().stopAutoUseTask(player);
				PhantomAssertions.assertTrue(player.isAutoPlaying(), "Missing AutoUse fixture stopped AutoPlay unexpectedly.");
				PhantomAssertions.assertFalse(visible.running(runtime.profileId(), runtime.goal()), "Partial native pair was falsely healthy.");
				PhantomAssertions.assertTrue(visible.start(runtime.profileId(), runtime.goal()), "Legal owner could not repair partial native pair.");
				PhantomAssertions.assertTrue(visible.running(runtime.profileId(), runtime.goal()), "Repaired native pair is not healthy.");
				PhantomAssertions.assertEquals(epoch, runtime.materialization().find(runtime.profileId()).orElseThrow().materializedAtNanos(), "Pair repair changed the materialization epoch.");
			}
			finally { visible.stop(runtime.profileId()); engine.beginStop(); engine.finishStop(); }
		}
	}

	@SuppressWarnings("unchecked")
	private void testNativePolicyReplacement(Class<?> managerClass, boolean throwsFailure) throws Exception
	{
		try (var runtime = createRuntimeFixture(_environment.primary().objectId()))
		{
			PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, runtime.materialization().materialize(runtime.profileId()).status(), "Native policy fixture failed to materialize.");
			final Player player;
			try (var action = runtime.materialization().tryAcquireAction(runtime.profileId()).orElseThrow()) { player = action.player(); }
			final var autoPlay = AutoPlayTaskManager.getInstance();
			final var autoUse = AutoUseTaskManager.getInstance();
			final Field playPoolsField = AutoPlayTaskManager.class.getDeclaredField("POOLS"); playPoolsField.setAccessible(true);
			final Field usePoolsField = AutoUseTaskManager.class.getDeclaredField("POOLS"); usePoolsField.setAccessible(true);
			final var playPools = (Set<Set<Player>>) playPoolsField.get(null);
			final var usePools = (Set<Set<Player>>) usePoolsField.get(null);
			final Set<Player> playPool = new java.util.LinkedHashSet<>(List.of(player));
			final Set<Player> usePool = new java.util.LinkedHashSet<>(List.of(player));
			playPools.add(playPool); usePools.add(usePool);
			final AutoPlayTaskManager.PhantomPolicy replacement = new AutoPlayTaskManager.PhantomPolicy()
			{
				@Override public AutoPlayTaskManager.TickLease acquire(Player actor) { return () -> {}; }
				@Override public boolean permitsTarget(Creature target) { return false; }
			};
			final var observed = new AtomicInteger();
			final AutoPlayTaskManager.PhantomPolicy original = new AutoPlayTaskManager.PhantomPolicy()
			{
				@Override public AutoPlayTaskManager.TickLease acquire(Player actor)
				{
					observed.incrementAndGet();
					autoPlay.startPhantomAutoPlay(actor, replacement);
					autoUse.startPhantomAutoUse(actor, replacement);
					if (throwsFailure) { throw new InjectedFailure(); }
					return null;
				}
				@Override public boolean permitsTarget(Creature target) { return false; }
			};
			try
			{
				player.setAutoPlaying(true);
				autoPlay.startPhantomAutoPlay(player, original); autoUse.startPhantomAutoUse(player, original);
				runNativePool(managerClass, new java.util.LinkedHashSet<>(List.of(player)));
				PhantomAssertions.assertEquals(1, observed.get(), "Actual native loop did not capture P1.");
				for (Class<?> paired : List.of(AutoPlayTaskManager.class, AutoUseTaskManager.class))
				{
					final Field policiesField = paired.getDeclaredField("PHANTOM_POLICIES"); policiesField.setAccessible(true);
					final var policies = (Map<Player, AutoPlayTaskManager.PhantomPolicy>) policiesField.get(null);
					PhantomAssertions.assertTrue(policies.get(player) == replacement, "Stale P1 removed P2 policy in " + paired.getSimpleName());
				}
				PhantomAssertions.assertTrue(playPool.contains(player) && usePool.contains(player) && player.isAutoPlaying(), "Stale P1 destroyed replacement native membership or flag.");
			}
			finally
			{
				autoUse.stopAutoUseTask(player); autoPlay.stopAutoPlay(player);
				playPool.add(player); usePool.add(player); playPools.remove(playPool); usePools.remove(usePool);
			}
		}
	}

	@SuppressWarnings("unchecked")
	private void testNativeMissingPolicy(Class<?> managerClass) throws Exception
	{
		try (var runtime = createRuntimeFixture(_environment.primary().objectId()))
		{
			PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, runtime.materialization().materialize(runtime.profileId()).status(), "Missing policy fixture failed to materialize.");
			final Player player;
			try (var action = runtime.materialization().tryAcquireAction(runtime.profileId()).orElseThrow()) { player = action.player(); }
			final Field poolsField = managerClass.getDeclaredField("POOLS"); poolsField.setAccessible(true);
			final var pools = (Set<Set<Player>>) poolsField.get(null);
			final Set<Player> pool = new java.util.LinkedHashSet<>(List.of(player)); pools.add(pool);
			final boolean enabled = AutoPlayConfig.ENABLE_AUTO_PLAY;
			final boolean items = AutoPlayConfig.ENABLE_AUTO_ITEM;
			final var target = Player.load(_environment.observer().objectId());
			try
			{
				AutoPlayConfig.ENABLE_AUTO_PLAY = true; AutoPlayConfig.ENABLE_AUTO_ITEM = true;
				target.spawnMe(player.getX() + 40, player.getY(), player.getZ());
				player.getAutoPlaySettings().setNextTargetMode(1); player.getAutoPlaySettings().setPickup(false);
				player.getAutoUseSettings().getAutoSupplyItems().add(Integer.MAX_VALUE);
				final AutoPlayTaskManager.PhantomPolicy policy = new AutoPlayTaskManager.PhantomPolicy()
				{
					@Override public AutoPlayTaskManager.TickLease acquire(Player actor) { throw new AssertionError("Removed policy must not be used."); }
					@Override public boolean permitsTarget(Creature creature) { return false; }
				};
				if (managerClass == AutoPlayTaskManager.class) { AutoPlayTaskManager.getInstance().startPhantomAutoPlay(player, policy); }
				else { AutoUseTaskManager.getInstance().startPhantomAutoUse(player, policy); }
				player.setTarget(target);
				PhantomAssertions.assertTrue(player.getTarget() == target, "Missing-policy fixture did not establish its target before the native tick.");
				// The iterator has already yielded this exact Player when stop removes registration.
				final Set<Player> staleSelection = new java.util.AbstractSet<>()
				{
					@Override public int size() { return 1; }
					@Override public java.util.Iterator<Player> iterator()
					{
						return new java.util.Iterator<>()
						{
							private boolean pending = true;
							@Override public boolean hasNext() { return pending; }
							@Override public Player next()
							{
								pending = false;
								if (managerClass == AutoPlayTaskManager.class) { AutoPlayTaskManager.getInstance().stopAutoPlay(player); }
								else { AutoUseTaskManager.getInstance().stopAutoUseTask(player); }
								return player;
							}
						};
					}
				};
				runNativePool(managerClass, staleSelection);
				if (managerClass == AutoPlayTaskManager.class) { PhantomAssertions.assertTrue(player.getTarget() == target, "Managed missing-policy tick executed stock target mutation."); }
				else { PhantomAssertions.assertTrue(player.getAutoUseSettings().getAutoSupplyItems().contains(Integer.MAX_VALUE), "Managed missing-policy tick executed stock supply mutation."); }
			}
			finally
			{
				AutoPlayConfig.ENABLE_AUTO_PLAY = enabled; AutoPlayConfig.ENABLE_AUTO_ITEM = items;
				player.setTarget(null); player.getAutoUseSettings().getAutoSupplyItems().remove(Integer.MAX_VALUE);
				AutoUseTaskManager.getInstance().stopAutoUseTask(player); AutoPlayTaskManager.getInstance().stopAutoPlay(player);
				pool.add(player); pools.remove(pool); _environment.cleanupLoadedPlayer(target);
			}
		}
	}

	private static void runNativePool(Class<?> managerClass, Set<Player> selected) throws Exception
	{
		final Class<?> taskClass = Arrays.stream(managerClass.getDeclaredClasses()).filter(type -> type.getSimpleName().equals(managerClass == AutoPlayTaskManager.class ? "AutoPlay" : "AutoUse")).findFirst().orElseThrow();
		final var constructor = taskClass.getDeclaredConstructor(managerClass, Set.class); constructor.setAccessible(true);
		((Runnable) constructor.newInstance(managerClass.getMethod("getInstance").invoke(null), selected)).run();
	}

	private void registerDecision(PhantomTestRegistry registry)
	{
		registry.add("01-exact-goal-contract", _ -> testGoalContract());
		registry.add("02-exact-candidate-and-handlers", _ -> testDecisionRegistrations());
		registry.add("03-activity-identity-reaches-handler", _ -> testDecisionExecutionIdentity());
		registry.add("04-visible-alive-ordinary-farm-candidate", _ -> testVisibleAliveCandidate());
		registry.add("05-phantom-autoplay-admission-with-user-play-disabled", _ -> testPhantomAutoPlayAdmission());
		registry.add("06-instant-self-heal-is-not-auto-buff", _ -> testInstantSelfHealIsNotAutoBuff());
		registry.add("07-visible-death-waits-before-town-recovery", _ -> testVisibleDeathWindow());
		registry.add("08-outgrown-visible-goal-stops-old-autoplay", _ -> testVisibleOutgrownStopsAutoPlay());
		registry.add("09-local-abort-does-not-stop-neighbor", _ -> testLocalAbortContainment());
		registry.add("10-pooled-phantom-policy-exception", _ -> testPooledPolicyException());
		registry.add("11-native-refresh-lock-order", _ -> testNativeRefreshLockOrder());
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
		registry.add("03-background-store-abort-retry", _ -> testBackgroundStoreAbortRetry());
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

	private void registerPositionCanonicalization(PhantomTestRegistry registry)
	{
		if ("contact".equals(System.getProperty("phantom.background.position.focus", "")))
		{
			registry.add("12-production-visible-executor-fast-contact", context -> testProductionVisibleExecutor(context, true));
			return;
		}
		if ("executor".equals(System.getProperty("phantom.background.position.focus", "")))
		{
			registry.add("11-production-visible-executor-route-and-revision", this::testProductionVisibleExecutor);
			return;
		}
		if ("travel".equals(System.getProperty("phantom.background.position.focus", "")))
		{
			registry.add("04-visible-travel-native-movement-and-cancellation", this::testVisibleNativeTravel);
			return;
		}
		if ("owned-store-transaction".equals(System.getProperty("phantom.background.position.focus", "")))
		{
			registry.add("09-owned-store-before-after-neither-and-snapshot", this::testOwnedStoreProtocol);
			return;
		}
		if (List.of("owned-store", "cleanup-diagnostics").contains(System.getProperty("phantom.background.position.focus", "")))
		{
			registry.add("08-owned-store-crash-and-mutation-matrix", this::testOwnedStoreCrashMatrix);
			return;
		}
		registry.add("01-canonical-anchor-policy-and-negative-controls", this::testCanonicalAnchorPolicy);
		registry.add("02-real-player-travel-materialization-restart", this::testProductionPositionTransition);
		registry.add("03-native-farm-movement-keeps-real-position", _ -> testNativeFarmAreaCapture());
		registry.add("04-visible-travel-native-movement-and-cancellation", this::testVisibleNativeTravel);
		registry.add("05-restored-pending-cold-queue-to-native-player", context -> testPendingNativeHandoff(context, false));
		registry.add("06-complete-history-uncommitted-outer-cursor", context -> testPendingNativeHandoff(context, true));
		registry.add("07-dead-level-loss-native-load-and-progress-fence", context -> testPendingNativeHandoff(context, true, true));
		registry.add("08-owned-store-crash-and-mutation-matrix", this::testOwnedStoreCrashMatrix);
		registry.add("09-owned-store-before-after-neither-and-snapshot", this::testOwnedStoreProtocol);
		registry.add("10-owned-store-journal-selected-epoch-and-off-cost", this::testOwnedStoreJournalScope);
		registry.add("11-production-visible-executor-route-and-revision", this::testProductionVisibleExecutor);
		registry.add("12-production-visible-executor-fast-contact", context -> testProductionVisibleExecutor(context, true));
	}

	private void testProductionVisibleExecutor(PhantomTestContext context) throws Exception
	{
		testProductionVisibleExecutor(context, false);
	}

	private void testProductionVisibleExecutor(PhantomTestContext context, boolean contact) throws Exception
	{
		final var topology = _production.topology();
		final var routeQuery = org.l2jmobius.gameserver.phantoms.background.PhantomNormalGatekeeperTravel.load(Path.of("data/phantoms/travel/high-five-normal-gk.xml"), topology);
		final var authority = new L2jPhantomBackgroundAuthority(_production::knowledge, _production::topology, _production::progression, _production::commerce, routeQuery);
		final var edge = topology.snapshot().edges().stream().filter(value -> value.backgroundEligible() && (value.fromAnchorId() != null) && (value.toAnchorId() != null)).filter(value ->
		{
			final var from = topology.findAnchor(value.fromAnchorId()).orElseThrow();
			final var to = topology.findAnchor(value.toAnchorId()).orElseThrow();
			final double distance = Math.hypot((long) from.point().x() - to.point().x(), (long) from.point().y() - to.point().y());
			return (from.role() == PhantomTopologyAnchorRole.ROUTE) && (to.role() == PhantomTopologyAnchorRole.FARMING) && (distance > 50) && (distance < 180);
		}).sorted(Comparator.comparing(PhantomTopologyEdge::id)).findFirst().orElseThrow();
		final var arrival = topology.findAnchor(edge.toAnchorId()).orElseThrow();
		final var departure = contact ? arrival : topology.findAnchor(edge.fromAnchorId()).orElseThrow();
		final int npcId = _production.knowledge().snapshot().spawnAreasByNpc().entrySet().stream().filter(value -> value.getValue().stream().anyMatch(area -> arrival.nodeId().equals(area.topologyNodeId()))).mapToInt(Map.Entry::getKey).sorted().findFirst().orElseThrow();
		final var farm = new ProductionFarmSelection(npcId, arrival);
		final int level = Math.min(85, Math.max(3, _production.knowledge().findNpc(npcId).orElseThrow().level()));
		try (var fixture = openProductionPlayerFixture(departure, level, PlayerClass.ELVEN_FIGHTER, farm); var humanSession = new org.l2jmobius.gameserver.localplay.LocalPlaySyntheticHumanSession(_environment.observer().objectId(), _environment.observer().characterName()))
		{
			final var profile = _repository.create(fixture.player().getObjectId());
			final long id = profile.profileId();
			final var goals = new PhantomGoalStateStore(_repository);
			final var goal = goal(npcId, arrival.id()); goals.insert(id, goal);
			final var transaction = new PhantomBackgroundTransaction();
			final var materializationRef = new AtomicReference<PhantomMaterializationService>();
			final var background = new PhantomBackgroundService(_repository, goals, PhantomIdentityLeaseRegistry.getInstance(), transaction, authority, new PhantomBackgroundCompetitionRegistry(), noSignals(), materializationRef::get);
			final var metrics = new PhantomMetrics();
			final var lifecycle = new org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationLifecycleBridge();
			final var materialization = new PhantomMaterializationService(_repository, PhantomIdentityLeaseRegistry.getInstance(), metrics, new PhantomDiagnosticTrace(false, 64, 16, metrics), 1, _ -> { }, lifecycle, 5_000, 10_000);
			materializationRef.set(materialization);
			final var history = new PhantomHistoricalBackgroundService(_repository, goals, new PhantomHistoricalBackgroundPlanner(_production.knowledge(), topology, authority), background, materialization);
			final var navigation = new PhantomNavigationService(metrics);
			final var engineRef = new AtomicReference<PhantomDecisionEngine>();
			final var autoPlay = new PhantomVisibleAutoPlay(materialization, engineRef::get, history::permitsNormalOperation);
			final var travel = new org.l2jmobius.gameserver.phantoms.background.PhantomVisibleFarmTravel(materialization, background, routeQuery, navigation, history::permitsNormalOperation, noSignals(), org.l2jmobius.gameserver.phantoms.background.PhantomVisibleFarmFailureBinding.bind(history), System::nanoTime);
			lifecycle.install(org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationLifecyclePort.chain(org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationLifecyclePort.chain(history, background), org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationLifecyclePort.chain(autoPlay, travel)));
			final var candidates = new PhantomCandidateRegistry(); final var handlers = new PhantomStepHandlerRegistry();
			final var binding = PhantomBackgroundDecision.bindVisibleLife(background, travel, autoPlay, history, engineRef::get);
			binding.registerCandidates(candidates); binding.registerHandlers(handlers); candidates.seal(); handlers.seal();
			final var engine = new PhantomDecisionEngine(goals, candidates, handlers, metrics, 1); engineRef.set(engine);
			final var port = new org.l2jmobius.gameserver.phantoms.activity.PhantomReconcileFirstActivityPort(new org.l2jmobius.gameserver.phantoms.activity.PhantomMaterializationServiceActivityPort(materialization));
			port.installReadiness(candidate -> history.permitsNormalOperation(candidate) && org.l2jmobius.gameserver.phantoms.PhantomSystem.onlineHumanPoints().stream().anyMatch(point -> org.l2jmobius.gameserver.phantoms.topology.PhantomNativeLocalityEnvelope.couldKnow(point, departure.point())) ? org.l2jmobius.gameserver.phantoms.activity.PhantomActivityMaterializationPort.TransitionOutcome.success() : org.l2jmobius.gameserver.phantoms.activity.PhantomActivityMaterializationPort.TransitionOutcome.deferred());
			final var workCount = new java.util.concurrent.atomic.AtomicInteger();
			final var scheduler = new org.l2jmobius.gameserver.phantoms.PhantomScheduler(1, 100, 1, metrics, new PhantomDiagnosticTrace(false, 64, 16, metrics), port, item -> { workCount.incrementAndGet(); engine.accept(item); });
			org.l2jmobius.gameserver.model.actor.instance.Monster monster = null;
			try
			{
				PhantomAssertions.assertTrue(background.start() && materialization.start() && navigation.start(), "Executor services did not start."); engine.start(); engine.attach(id);
				final var captured = authority.capture(id, fixture.player(), goal, null); fixture.player().storeMe();
				PhantomAssertions.assertEquals(Status.SUCCESS, captureTestBaseline(transaction, captured, goal).status(), "Executor production baseline failed.");
				fixture.releaseRuntime();
				final var human = humanSession.start(); human.teleToLocation(departure.point().x(), departure.point().y(), departure.point().z()); human.onTeleported();
				PhantomAssertions.assertTrue(humanSession.valid() && !org.l2jmobius.gameserver.phantoms.PhantomSystem.onlineHumanPoints().isEmpty(), "Executor requires an ordinary World human.");
				monster = new org.l2jmobius.gameserver.model.actor.instance.Monster(NpcData.getInstance().getTemplate(npcId)); monster.disableCoreAI(true);
				final Position targetPosition = canonicalAnchorPosition(arrival, 0);
				final var spawn = new org.l2jmobius.gameserver.model.spawns.Spawn(monster.getTemplate()); spawn.setXYZ(targetPosition.x(), targetPosition.y(), targetPosition.z()); monster.setSpawn(spawn); monster.spawnMe(targetPosition.x(), targetPosition.y(), targetPosition.z());
				PhantomAssertions.assertTrue(scheduler.start(), "Executor scheduler did not start."); scheduler.register(id);
				scheduler.submitSignal(id, new org.l2jmobius.gameserver.phantoms.activity.PhantomRelevanceSignal("test.world.local", 1, PhantomActivityState.ACTIVE, 60000));
				final long moveDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
				Player actor = null;
				while (System.nanoTime() < moveDeadline)
				{
					try (var action = materialization.tryAcquireAction(id).orElse(null)) { if ((action != null) && (contact ? autoPlay.running(id, goal) : action.player().isMoving())) { actor = action.player(); break; } }
					Thread.sleep(10);
				}
				PhantomAssertions.assertTrue(actor != null, "Actual handler did not start native travel: " + engine.find(id) + "/" + travel.reason(id));
				final long epoch = materialization.find(id).orElseThrow().materializedAtNanos();
				final var replacement = new PhantomGoal(goal.goalId(), goal.goalType(), goal.status(), goal.subject(), goal.target(), goal.requiredAmount(), goal.currentAmount(), goal.acquisitionMethod(), goal.validSources(), goal.selectedAnchor(), goal.purposeKey(), goal.priority(), goal.riskBudget(), goal.expenseBudget(), goal.deadlineEpochMillis(), goal.constraints(), goal.reasonKey(), goal.revision() + 1);
				PhantomAssertions.assertEquals(PhantomDecisionEngine.MutationResult.APPLIED, engine.setGoal(id, replacement), "Travel goal revision did not change.");
				PhantomAssertions.assertFalse(autoPlay.running(id, goal), "Old revision retained an AutoPlay session.");
				final long combatDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(25);
				final Player nativeActor = actor;
				org.l2jmobius.gameserver.model.actor.holders.npc.AggroInfo damage = null;
				while (System.nanoTime() < combatDeadline)
				{
					for (var target : World.getInstance().getVisibleObjectsInRange(nativeActor, org.l2jmobius.gameserver.model.actor.instance.Monster.class, 1000)) { if (target.getId() == npcId) { final var aggro = target.getAggroList().get(nativeActor); if ((aggro != null) && (aggro.getDamage() > 0)) { damage = aggro; break; } } }
					if ((damage != null) && autoPlay.running(id, replacement)) { break; } Thread.sleep(20);
				}
				PhantomAssertions.assertTrue(damage != null, "Actual revised handler did not reach attributed native damage: " + engine.find(id) + "/" + travel.reason(id));
				PhantomAssertions.assertEquals(epoch, materialization.find(id).orElseThrow().materializedAtNanos(), "Travel revision fabricated a new materialization epoch.");
				PhantomAssertions.assertTrue(workCount.get() > 0 && autoPlay.running(id, replacement), "Replacement did not acquire its legal native session.");
				if (contact)
				{
					context.record("executor.fastContact", true); context.record("executor.nativeDamage", damage.getDamage()); context.record("executor.activityWorkCount", workCount.get()); context.record("executor.manualMaterialize", false); context.record("executor.manualAutoPlayStart", false);
					return;
				}
				final Field policiesField = AutoPlayTaskManager.class.getDeclaredField("PHANTOM_POLICIES"); policiesField.setAccessible(true);
				final var policies = (Map<?, ?>) policiesField.get(null);
				final var oldPolicy = (AutoPlayTaskManager.PhantomPolicy) policies.get(nativeActor);
				PhantomAssertions.assertTrue(oldPolicy != null, "Actual executor did not register its native policy.");
				engine.detach(id); scheduler.unregister(id);
				final long retireDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(45);
				while ((materialization.find(id).isPresent() || scheduler.find(id).isPresent() || engine.find(id).isPresent()) && (System.nanoTime() < retireDeadline)) { scheduler.retryTransition(id); Thread.sleep(100); }
				PhantomAssertions.assertTrue(materialization.find(id).isEmpty() && scheduler.find(id).isEmpty() && engine.find(id).isEmpty(), "Old executor epoch did not retire naturally.");
				engine.attach(id); scheduler.register(id);
				scheduler.submitSignal(id, new org.l2jmobius.gameserver.phantoms.activity.PhantomRelevanceSignal("test.world.local", 2, PhantomActivityState.ACTIVE, 60000));
				final long newEpochDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
				Player newActor = null;
				while (System.nanoTime() < newEpochDeadline)
				{
					try (var action = materialization.tryAcquireAction(id).orElse(null)) { if ((action != null) && autoPlay.running(id, replacement)) { newActor = action.player(); break; } }
					Thread.sleep(20);
				}
				PhantomAssertions.assertTrue((newActor != null) && (newActor != nativeActor) && (newActor.getObjectId() == nativeActor.getObjectId()), "Actual executor did not acquire a new Player for the same identity.");
				PhantomAssertions.assertTrue(materialization.find(id).orElseThrow().materializedAtNanos() != epoch, "New Player reused its old materialization epoch.");
				try (var retiredLease = oldPolicy.acquire(nativeActor)) { PhantomAssertions.assertEquals(null, retiredLease, "Retired policy acquired the old Player."); }
				try (var transferredLease = oldPolicy.acquire(newActor)) { PhantomAssertions.assertEquals(null, transferredLease, "Retired policy transferred ownership to a new Player epoch."); }
				context.record("executor.newEpoch", materialization.find(id).orElseThrow().materializedAtNanos());
				context.record("executor.route", edge.id()); context.record("executor.nativeDamage", damage.getDamage()); context.record("executor.activityWorkCount", workCount.get()); context.record("executor.manualMaterialize", false); context.record("executor.manualAutoPlayStart", false);
			}
			finally
			{
				engine.beginStop(); engine.finishStop(); autoPlay.stop(id); if (monster != null) { monster.deleteMe(); }
				scheduler.unregister(id);
				final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(45);
				while (materialization.find(id).isPresent() && (System.nanoTime() < deadline)) { scheduler.retryTransition(id); Thread.sleep(100); }
				scheduler.beginStop(); scheduler.finishStop(); navigation.beginStop(); navigation.finishStop();
				PhantomAssertions.assertTrue(materialization.find(id).isEmpty(), "Executor retained natural combat cleanup: " + materialization.find(id));
				materialization.shutdown(); background.beginStop(); background.finishStop(); deleteProfile(profile);
			}
		}
	}

	private void testOwnedStoreJournalScope(PhantomTestContext context) throws Exception
	{
		final Class<?> journal = org.l2jmobius.gameserver.localplay.LocalPlayPhantomStoreJournal.class;
		final Field rootField = journal.getDeclaredField("_root"); rootField.setAccessible(true);
		final Object originalRoot = rootField.get(null);
		final Path runtimeRoot = Files.createTempDirectory(context.moduleRoot().resolve(".phantom-local"), "m1-journal-test-");
		final Path root = Files.createDirectory(runtimeRoot.resolve("playtest-lifecycle"));
		final var principal = java.nio.file.FileSystems.getDefault().getUserPrincipalLookupService().lookupPrincipalByName(System.getProperty("user.name"));
		final var acl = List.of(java.nio.file.attribute.AclEntry.newBuilder().setType(java.nio.file.attribute.AclEntryType.ALLOW).setPrincipal(principal).setPermissions(java.util.EnumSet.allOf(java.nio.file.attribute.AclEntryPermission.class)).setFlags(java.nio.file.attribute.AclEntryFlag.FILE_INHERIT, java.nio.file.attribute.AclEntryFlag.DIRECTORY_INHERIT).build());
		Files.getFileAttributeView(root, java.nio.file.attribute.AclFileAttributeView.class).setAcl(acl);
		for (String name : List.of("stores.log", "stores.1.log", "stores.2.log")) { final Path file = Files.createFile(root.resolve(name)); Files.getFileAttributeView(file, java.nio.file.attribute.AclFileAttributeView.class).setAcl(acl); }
		final Method configure = journal.getDeclaredMethod("configure", Path.class); configure.setAccessible(true);
		try (var runtime = createRuntimeFixture(_environment.primary().objectId()))
		{
			try { configure.invoke(null, runtimeRoot); } catch (java.lang.reflect.InvocationTargetException failure) { throw new IllegalStateException("Journal fixture private guard failed", failure.getCause()); }
			PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, runtime.materialization().materialize(runtime.profileId()).status(), "Journal fixture did not materialize.");
			final Player player;
			final PlayerNativeWork.Owner nativeOwner;
			final long epoch;
			try (var action = runtime.materialization().tryAcquireAction(runtime.profileId()).orElseThrow())
			{
				player = action.player(); nativeOwner = player.getNativeWorkOwner();
				epoch = runtime.materialization().find(runtime.profileId()).orElseThrow().materializedAtNanos();
			}
			PhantomAssertions.assertTrue(nativeOwner != null && nativeOwner.player() == player && nativeOwner.isCurrent() && nativeOwner.epoch() == epoch && PlayerNativeWork.current(nativeOwner) == null, "Journal STORE did not retain exact lifetime outside ActionLease.");
			final var off = org.l2jmobius.gameserver.localplay.LocalPlayPhantomStoreJournal.operationCounts();
			player.storeMe();
			PhantomAssertions.assertEquals(off, org.l2jmobius.gameserver.localplay.LocalPlayPhantomStoreJournal.operationCounts(), "Configured journal default OFF performed work.");
			org.l2jmobius.gameserver.localplay.LocalPlayPhantomStoreJournal.select(Map.of(runtime.profileId(), epoch));
			player.storeMe();
			final var on = org.l2jmobius.gameserver.localplay.LocalPlayPhantomStoreJournal.operationCounts();
			PhantomAssertions.assertTrue((on.snapshots() > off.snapshots()) && (on.encodes() > off.encodes()) && (on.hashes() > off.hashes()) && (on.opens() > off.opens()) && (on.forces() > off.forces()), "Selected journal did not attest/force both boundary events.");
			org.l2jmobius.gameserver.localplay.LocalPlayPhantomStoreJournal.select(Map.of(runtime.profileId(), epoch + 1));
			player.storeMe();
			PhantomAssertions.assertEquals(on, org.l2jmobius.gameserver.localplay.LocalPlayPhantomStoreJournal.operationCounts(), "Wrong epoch performed diagnostic work.");
			org.l2jmobius.gameserver.localplay.LocalPlayPhantomStoreJournal.select(Map.of(runtime.profileId() + 1, epoch));
			player.storeMe();
			PhantomAssertions.assertEquals(on, org.l2jmobius.gameserver.localplay.LocalPlayPhantomStoreJournal.operationCounts(), "Unselected profile performed diagnostic work.");
			final var oversized = new LinkedHashMap<Long, Long>(); for (long id = 1; id <= 9; id++) { oversized.put(id, epoch); }
			PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> org.l2jmobius.gameserver.localplay.LocalPlayPhantomStoreJournal.select(oversized), "Journal allowed more than eight selected epochs.");
			context.record("m1.journalOff", off); context.record("m1.journalOn", on);
			org.l2jmobius.gameserver.localplay.LocalPlayPhantomStoreJournal.select(Map.of(runtime.profileId(), epoch));
			Files.write(root.resolve("stores.log"), new byte[1024 * 1024], java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
			player.storeMe();
			PhantomAssertions.assertTrue(Files.size(root.resolve("stores.1.log")) >= 1024 * 1024 && Files.size(root.resolve("stores.log")) < 1024 * 1024, "Journal rotation was not bounded.");
			final State beforeJournalFault = runtime.transaction().load(runtime.profileId()).state().state();
			Files.delete(root.resolve("stores.log")); Files.createDirectory(root.resolve("stores.log"));
			player.storeMe();
			PhantomAssertions.assertFalse(org.l2jmobius.gameserver.localplay.LocalPlayPhantomStoreJournal.enabledFor(runtime.profileId(), epoch), "Journal I/O failure remained enabled.");
			PhantomAssertions.assertEquals(beforeJournalFault, runtime.transaction().load(runtime.profileId()).state().state(), "Diagnostic failure claimed gameplay authority.");
			PhantomAssertions.assertTrue(player.getNativeWorkOwner() == nativeOwner && nativeOwner.isCurrent() && nativeOwner.epoch() == runtime.materialization().find(runtime.profileId()).orElseThrow().materializedAtNanos(), "Journal STORE changed native Player owner/epoch.");
		}
		finally
		{
			org.l2jmobius.gameserver.localplay.LocalPlayPhantomStoreJournal.select(Map.of()); rootField.set(null, originalRoot);
			for (String name : List.of("stores.log", "stores.1.log", "stores.2.log")) { Files.deleteIfExists(root.resolve(name)); } Files.delete(root); Files.delete(runtimeRoot);
		}
	}

	private void testOwnedStoreProtocol(PhantomTestContext context) throws Exception
	{
		final var farm = new ProductionFarmSelection(20534, _production.topology().findAnchor("population.farming.elf.20534").orElseThrow());
		for (String scenario : List.of("NORMAL_ARRIVAL", "NORMAL_CLEANUP", "BEFORE_STORE", "AFTER_STORE", "FINALIZE_COMMIT", "SNAPSHOT_MUTATION", "SNAPSHOT_PROGRESSION", "PARTIAL_UNKNOWN", "STALE_EPOCH", "LEVEL_UP_BEFORE", "LEVEL_UP_AFTER", "PREPARE_ACK_UNKNOWN", "FINALIZE_ACK_UNKNOWN", "PREPARE_UNCOMMITTED", "STALE_VERSION", "CHANGED_PAYLOAD", "PARTIAL_ITEM"))
		{
			try (var fixture = openProductionPlayerFixture(farm.anchor(), scenario.startsWith("LEVEL_UP") ? 19 : 7, PlayerClass.ELVEN_MAGE, farm))
			{
				final Player player = fixture.player();
				final var profile = _repository.create(player.getObjectId());
				final long id = profile.profileId(); final int objectId = player.getObjectId(); final long epoch = 123456789;
				final var goals = new PhantomGoalStateStore(_repository); goals.insert(id, fixture.goal());
				final var faults = new java.util.concurrent.atomic.AtomicBoolean();
				final var transaction = new PhantomBackgroundTransaction(() ->
				{
					final var delegate = DatabaseFactory.getConnection();
					if (!scenario.equals("PREPARE_UNCOMMITTED")) { return delegate; }
					// Reuse the existing Economy suite Connection proxy pattern, entirely inside guarded TEST.
					return (java.sql.Connection) java.lang.reflect.Proxy.newProxyInstance(java.sql.Connection.class.getClassLoader(), new Class<?>[] { java.sql.Connection.class }, (_, method, arguments) ->
					{
						if (method.getName().equals("commit") && faults.compareAndSet(true, false)) { delegate.rollback(); throw new java.sql.SQLException("TEST_COMMIT_NOT_DURABLE"); }
						try { return method.invoke(delegate, arguments); } catch (java.lang.reflect.InvocationTargetException failure) { throw failure.getCause(); }
					});
				}, allocator(new AtomicInteger()), point ->
				{
					if (faults.get() && scenario.equals("FINALIZE_COMMIT") && (point == FaultPoint.BEFORE_OWNED_FINALIZE_COMMIT)) { throw new InjectedFailure(); }
					if (scenario.equals("PREPARE_ACK_UNKNOWN") && (point == FaultPoint.AFTER_OWNED_PREPARE_COMMIT)) { throw new InjectedFailure(); }
					if (scenario.equals("FINALIZE_ACK_UNKNOWN") && (point == FaultPoint.AFTER_OWNED_FINALIZE_COMMIT)) { throw new InjectedFailure(); }
				});
				try
				{
					player.setCurrentHp(133); player.setCurrentCp(7); player.setHeading(25847);
					final var captured = _production.authority().capture(id, player, fixture.goal(), null);
					player.storeMe();
					PhantomAssertions.assertEquals(Status.SUCCESS, captureTestBaseline(transaction, captured, fixture.goal()).status(), "Protocol seed failed.");
					PhantomAssertions.assertEquals(Status.SUCCESS, transaction.markMaterialized(id, objectId).status(), "Protocol MAT seed failed.");
					final var before = transaction.load(id).state();
					final Canonical beforeCanonical = canonical(objectId);
					if (scenario.startsWith("LEVEL_UP"))
					{
						PhantomAssertions.assertTrue(player.getStat().addExpAndSp(ExperienceData.getInstance().getExpForLevel(20) - player.getExp(), 1, false), "Native level-up was rejected.");
						PhantomAssertions.assertEquals(20, player.getLevel(), "Native level-up did not advance to 20.");
						PhantomAssertions.assertEquals(19, canonical(objectId).level(), "Native level-up unexpectedly persisted character base before owned store.");
					}
					player.setCurrentHp(player.getMaxHp()); player.setCurrentMp(player.getMaxMp()); player.setCurrentCp(54);
					player.setXYZInvisible(player.getX() + 10, player.getY() + 10, player.getZ()); player.setHeading(12773);
					final var intended = _production.authority().capture(id, player, fixture.goal(), before);
					final State target = scenario.equals("NORMAL_CLEANUP") ? State.READY : State.MATERIALIZED;
					if (scenario.equals("PREPARE_UNCOMMITTED")) { faults.set(true); }
					var prepared = transaction.prepareOwnedStore(intended, fixture.goal(), epoch, target);
					PhantomAssertions.assertEquals(scenario.equals("PREPARE_ACK_UNKNOWN") || scenario.equals("PREPARE_UNCOMMITTED") ? Status.COMMIT_OUTCOME_UNKNOWN : Status.SUCCESS, prepared.status(), "Protocol PREPARE rejected: " + scenario);
					if (scenario.equals("PREPARE_UNCOMMITTED")) { PhantomAssertions.assertEquals(before, transaction.load(id).state(), "Uncommitted PREPARE changed background."); PhantomAssertions.assertTrue(transaction.resumeOwnedStore(prepared.intent()).successful(), "Exact uncommitted PREPARE could not resume."); prepared = transaction.prepareOwnedStore(intended, fixture.goal(), epoch, target); PhantomAssertions.assertTrue(prepared.successful(), "PREPARE retry failed."); }
					PhantomAssertions.assertEquals(prepared.intent(), org.l2jmobius.gameserver.phantoms.background.PhantomOwnedStoreIntent.decode(prepared.intent().encode()), "Intent roundtrip changed epoch/projections.");
					PhantomAssertions.assertEquals(beforeCanonical, canonical(objectId), "PREPARE wrote native canonical rows.");
					if (!scenario.equals("BEFORE_STORE") && !scenario.equals("LEVEL_UP_BEFORE"))
					{
						try (var lease = PhantomIdentityLeaseRegistry.getInstance().tryAcquire(objectId, PhantomIdentityLeaseRegistry.OwnerKind.PHANTOM))
						{
							PhantomAssertions.assertTrue(lease != null, "Protocol TEST lease rejected.");
							try (var attachment = player.attachOwnedStoreBoundary(new Player.OwnedStoreBoundary()
							{
								@Override public Player.OwnedStoreSnapshot beforeStore()
								{
									final var v = intended.vitals(); final var p = intended.position();
									if (scenario.equals("SNAPSHOT_MUTATION")) { setOwnedStoreTown(player); }
									if (scenario.equals("SNAPSHOT_PROGRESSION")) { player.getStat().setExp(player.getExp() + 1); player.getStat().setSp(player.getSp() + 1); }
									final var progress = intended.progress(); final var identity = intended.identity();
									return new Player.OwnedStoreSnapshot(v.currentHp(), (int) v.maximumHp(), v.currentMp(), (int) v.maximumMp(), v.currentCp(), (int) v.maximumCp(), p.x(), p.y(), p.z(), p.heading(), progress.level(), progress.experience(), progress.skillPoints(), progress.experienceBeforeDeath(), identity.activeClassId(), identity.raceOrdinal(), identity.classIndex(), progress.level(), progress.experience(), progress.skillPoints());
								}
								@Override public void afterStore(boolean completed) { PhantomAssertions.assertTrue(completed, "Native protocol TEST store threw."); }
							})) { player.storeMe(); }
						}
					}
					if (scenario.equals("PARTIAL_UNKNOWN"))
					{
						try (var connection = DatabaseFactory.getConnection(); var statement = connection.prepareStatement("UPDATE characters SET heading=heading+1 WHERE charId=?")) { statement.setInt(1, objectId); statement.executeUpdate(); }
					}
					if (scenario.equals("PARTIAL_ITEM")) { player.getInventory().addItem(ItemProcessType.REWARD, 57, 3, player, this); player.getInventory().updateDatabase(); }
					if (scenario.equals("STALE_VERSION") || scenario.equals("CHANGED_PAYLOAD"))
					{
						try (var connection = DatabaseFactory.getConnection(); var statement = connection.prepareStatement(scenario.equals("STALE_VERSION") ? "UPDATE phantom_profile_components SET row_version=row_version+1 WHERE profile_id=? AND component_type=?" : "UPDATE phantom_profile_components SET payload=? WHERE profile_id=? AND component_type=?"))
						{
							int offset = 1; if (scenario.equals("CHANGED_PAYLOAD")) { statement.setBytes(offset++, new PhantomBackgroundStateCodec().encode(prepared.intent().after().withState(State.READY))); } statement.setLong(offset++, id); statement.setString(offset, PhantomBackgroundState.COMPONENT_TYPE); PhantomAssertions.assertEquals(1, statement.executeUpdate(), "Stale component TEST fixture missing.");
						}
						final var pinnedCanonical = canonical(objectId); final var pinnedBackground = transaction.load(id).state();
						PhantomAssertions.assertEquals(Status.STATE_CONFLICT, transaction.finalizeOwnedStore(id, objectId, epoch).status(), "Stale component finalized."); PhantomAssertions.assertEquals(Status.STATE_CONFLICT, transaction.resumeOwnedStore(prepared.intent()).status(), "Stale component resumed.");
						PhantomAssertions.assertEquals(pinnedCanonical, canonical(objectId), "Stale rejection wrote canonical rows."); PhantomAssertions.assertEquals(pinnedBackground, transaction.load(id).state(), "Stale rejection wrote background."); context.record("ownedProtocol." + scenario, "STATE_CONFLICT/no write"); continue;
					}
					if (scenario.equals("STALE_EPOCH")) { PhantomAssertions.assertEquals(Status.STATE_CONFLICT, transaction.finalizeOwnedStore(id, objectId, epoch + 1).status(), "Stale epoch finalized intent."); }
					faults.set(true);
					if (scenario.startsWith("NORMAL") || scenario.equals("FINALIZE_COMMIT") || scenario.startsWith("SNAPSHOT_") || scenario.equals("FINALIZE_ACK_UNKNOWN"))
					{
						final var finalized = transaction.finalizeOwnedStore(id, objectId, epoch);
						if (scenario.equals("FINALIZE_COMMIT")) { PhantomAssertions.assertEquals(Status.BACKEND_FAILURE, finalized.status(), "Finalization fault did not rollback."); }
						else if (scenario.equals("FINALIZE_ACK_UNKNOWN")) { PhantomAssertions.assertEquals(Status.COMMIT_OUTCOME_UNKNOWN, finalized.status(), "Lost commit acknowledgement was presented as rollback."); PhantomAssertions.assertTrue(transaction.resumeOwnedStore(prepared.intent()).successful(), "Exact completed commit was not recognized."); }
						else { PhantomAssertions.assertEquals(Status.SUCCESS, finalized.status(), "Protocol finalize failed: " + scenario + ":" + finalized.status()); PhantomAssertions.assertEquals(target, finalized.state().state(), "Wrong live lifecycle target."); }
					}
					if (scenario.equals("LEVEL_UP_BEFORE")) { fixture.releaseRuntime(); }
					try (var recoveryLease = scenario.equals("LEVEL_UP_BEFORE") ? PhantomIdentityLeaseRegistry.getInstance().tryAcquire(objectId, PhantomIdentityLeaseRegistry.OwnerKind.BACKGROUND) : null)
					{
					final var restarted = new PhantomBackgroundTransaction().abortMaterialization(id, objectId);
					context.record("ownedProtocol." + scenario, restarted.status() + "/" + restarted.state().state());
					if (scenario.startsWith("PARTIAL_")) { PhantomAssertions.assertEquals(Status.OWNED_STORE_CANONICAL_NEITHER, restarted.status(), "Unknown partial canonical was guessed."); PhantomAssertions.assertEquals(State.INCONSISTENT, restarted.state().state(), "Unknown partial was admitted."); }
					else
					{
						PhantomAssertions.assertEquals(Status.SUCCESS, restarted.status(), "Fresh restart could not prove canonical side: " + scenario);
						PhantomAssertions.assertEquals(State.READY, restarted.state().state(), "Restart retained live marker without owner.");
						final var expected = scenario.equals("BEFORE_STORE") ? prepared.intent().before() : prepared.intent().after();
						PhantomAssertions.assertEquals(expected, restarted.state(), "Restart selected wrong projection or changed receipts/effects.");
					}
					}
				}
				finally { faults.set(false); deleteProfile(profile); }
			}
		}
	}

	private void testOwnedStoreCrashMatrix(PhantomTestContext context) throws Exception
	{
		final var farm = new ProductionFarmSelection(20534, _production.topology().findAnchor("population.farming.elf.20534").orElseThrow());
		final List<String> unsafe = new ArrayList<>();
		for (String boundary : List.of("SQL_BASE", "SQL_SUB", "LIVE_PREPARE", "LIVE_NATIVE_STORE", "LIVE_FINALIZE_ACK", "ARRIVAL_AFTER_CAPTURE", "ARRIVAL_AFTER_STORE", "ARRIVAL_AFTER_BASELINE", "CLEANUP_BEFORE_STORE", "CLEANUP_AFTER_STORE", "CLEANUP_CAPTURE_COMMIT", "CLEANUP_AFTER_CAPTURE", "ARRIVAL_MUTATION", "BEFORE_OWNED_PREPARE_COMMIT", "AFTER_OWNED_PREPARE", "AFTER_OWNED_NATIVE_STORE", "BEFORE_OWNED_FINALIZE_COMMIT", "NORMAL_ARRIVAL", "NORMAL_CLEANUP", "RETRY_PREPARE", "RETRY_FINALIZE", "LAZY_INVENTORY", "WORLD_OWNER", "UNSUPPORTED_ABSENT", "ARRIVAL_RETRY_MOVED", "CLEANUP_PREPARE_REJECTION", "CLEANUP_FINALIZED_POST_STORE"))
		{
			if ("cleanup-diagnostics".equals(System.getProperty("phantom.background.position.focus", "")) && !boundary.equals("CLEANUP_PREPARE_REJECTION") && !boundary.equals("CLEANUP_FINALIZED_POST_STORE")) { continue; }
			try (var fixture = openProductionPlayerFixture(farm.anchor(), 7, PlayerClass.ELVEN_MAGE, farm))
			{
				final var profile = _repository.create(fixture.player().getObjectId());
				final long id = profile.profileId();
				final int objectId = fixture.player().getObjectId();
				final var goals = new PhantomGoalStateStore(_repository);
				final var nativeGoal = fixture.goal();
				goals.insert(id, boundary.equals("UNSUPPORTED_ABSENT") ? new PhantomGoal(nativeGoal.goalId(), "enchant.item", nativeGoal.status(), nativeGoal.subject(), nativeGoal.target(), nativeGoal.requiredAmount(), nativeGoal.currentAmount(), nativeGoal.acquisitionMethod(), nativeGoal.validSources(), nativeGoal.selectedAnchor(), nativeGoal.purposeKey(), nativeGoal.priority(), nativeGoal.riskBudget(), nativeGoal.expenseBudget(), nativeGoal.deadlineEpochMillis(), nativeGoal.constraints(), nativeGoal.reasonKey(), nativeGoal.revision()) : nativeGoal);
				final var armed = new java.util.concurrent.atomic.AtomicBoolean();
				final var nativeCompleted = new AtomicInteger();
				final var live = new AtomicReference<Player>();
				final var transaction = new PhantomBackgroundTransaction(DatabaseFactory::getConnection, allocator(new AtomicInteger()), point ->
				{
					if (!armed.get()) { return; }
					if (boundary.equals("CLEANUP_PREPARE_REJECTION") && (point == FaultPoint.BEFORE_OWNED_PREPARE_COMMIT)) { throw new InjectedFailure(); }
					if (boundary.startsWith("SQL_") && (point == FaultPoint.AFTER_OWNED_NATIVE_STORE)) { nativeCompleted.incrementAndGet(); }
					if ((boundary.equals("LIVE_PREPARE") && (point == FaultPoint.AFTER_OWNED_PREPARE)) || (boundary.equals("LIVE_NATIVE_STORE") && (point == FaultPoint.AFTER_OWNED_NATIVE_STORE)) || (boundary.equals("LIVE_FINALIZE_ACK") && (point == FaultPoint.AFTER_OWNED_FINALIZE_COMMIT))) { throw new InjectedFailure(); }
					if ((boundary.equals("RETRY_PREPARE") && (point == FaultPoint.AFTER_OWNED_PREPARE)) || (boundary.equals("RETRY_FINALIZE") && (point == FaultPoint.BEFORE_OWNED_FINALIZE_COMMIT))) { throw new InjectedFailure(); }
					if (boundary.equals("ARRIVAL_RETRY_MOVED") && (point == FaultPoint.AFTER_OWNED_PREPARE)) { throw new InjectedFailure(); }
					if ((boundary.equals("ARRIVAL_MUTATION") || boundary.equals("ARRIVAL_AFTER_STORE")) && (point == FaultPoint.ARRIVAL_AFTER_CAPTURE))
					{
						setOwnedStoreTown(live.get());
						return;
					}
					if (boundary.equals(point.name()) || (boundary.equals("CLEANUP_CAPTURE_COMMIT") && (point == FaultPoint.BEFORE_OWNED_FINALIZE_COMMIT))) { throw new InjectedFailure(); }
				});
				final var owner = new AtomicReference<PhantomMaterializationService>();
				final var background = new PhantomBackgroundService(_repository, goals, PhantomIdentityLeaseRegistry.getInstance(), transaction, _production.authority(), new PhantomBackgroundCompetitionRegistry(), noSignals(), owner::get);
				final var metrics = new PhantomMetrics();
				final var postStoreFailure = new PhantomMaterializationLifecyclePort()
				{
					@Override public void beforeMaterialize(long profileId, int characterId) { }
					@Override public void afterPlayerLoad(long profileId, Player player) { }
					@Override public void materializeSucceeded(long profileId, int characterId) { }
					@Override public void materializeAborted(long profileId, int characterId) { }
					@Override public void beforeStore(long profileId, Player player) { }
					@Override public void afterStore(long profileId, Player player) { if (armed.get() && boundary.equals("CLEANUP_FINALIZED_POST_STORE")) { throw new InjectedFailure(); } }
				};
				final var materialization = new PhantomMaterializationService(_repository, PhantomIdentityLeaseRegistry.getInstance(), metrics, new PhantomDiagnosticTrace(false, 64, 16, metrics), 1, point ->
				{
					if (armed.get() && ((boundary.equals("CLEANUP_BEFORE_STORE") && (point == FailurePoint.BEFORE_STORE_OPERATION)) || (boundary.equals("CLEANUP_AFTER_STORE") && (point == FailurePoint.AFTER_NATIVE_STORE)) || (boundary.equals("CLEANUP_AFTER_CAPTURE") && (point == FailurePoint.AFTER_STORE_BEFORE_DELETE)))) { throw new InjectedFailure(); }
				}, PhantomMaterializationLifecyclePort.chain(background, postStoreFailure), 5000, 10000);
				owner.set(materialization);
				background.start(); materialization.start();
				try
				{
					final Player seed = fixture.player();
					if (boundary.startsWith("SQL_"))
					{
						final var ordinaryFaults = new AtomicInteger();
						if (boundary.equals("SQL_SUB")) { seed.getSubClasses().put(1, new org.l2jmobius.gameserver.model.actor.holders.player.SubClassHolder(1, 1)); }
						try (var ignored = injectNativeStoreSqlFailure(boundary.equals("SQL_SUB"), ordinaryFaults)) { seed.store(false); }
						finally { seed.getSubClasses().remove(1); }
						PhantomAssertions.assertEquals(1, ordinaryFaults.get(), "Ordinary null-snapshot control did not reach SQL.");
					}
					seed.setCurrentHp(Math.min(133, seed.getMaxHp() - 1));
					seed.setCurrentMp(Math.min(166, seed.getMaxMp() - 1));
					seed.setCurrentCp(7);
					seed.setHeading(25847);
					final var captured = _production.authority().capture(id, seed, fixture.goal(), null);
					seed.storeMe();
					if (!boundary.equals("UNSUPPORTED_ABSENT")) { PhantomAssertions.assertEquals(Status.SUCCESS, captureTestBaseline(transaction, captured, fixture.goal()).status(), "Coherent farm baseline failed."); }
					fixture.releaseRuntime();
					PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, materialization.materialize(id).status(), "NORMAL matrix materialization rejected.");
					try (var action = materialization.tryAcquireAction(id).orElseThrow())
					{
						live.set(action.player());
						if (boundary.equals("CLEANUP_BEFORE_STORE") || boundary.equals("CLEANUP_AFTER_STORE")) { setOwnedStoreTown(action.player()); }
						else if (!boundary.equals("ARRIVAL_MUTATION"))
						{
							action.player().setCurrentHp(action.player().getMaxHp());
							action.player().setCurrentMp(action.player().getMaxMp());
							action.player().setCurrentCp(Math.min(54, action.player().getMaxCp()));
						}
					}
					armed.set(true);
					if (boundary.equals("CLEANUP_PREPARE_REJECTION") || boundary.equals("CLEANUP_FINALIZED_POST_STORE"))
					{
						final Canonical pinnedCanonical = canonical(objectId);
						final var pinnedBackground = transaction.load(id).state();
						PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.CLEANUP_FAILED_RETAINED, materialization.dematerialize(id).status(), "Owned cleanup diagnostic boundary did not retain failure.");
						final var failed = materialization.find(id).orElseThrow();
						PhantomAssertions.assertEquals(org.l2jmobius.gameserver.phantoms.player.PhantomMaterializedPlayer.State.FAILED, failed.state(), "Owned failure lost FAILED.");
						PhantomAssertions.assertTrue(!failed.actionAdmissionOpen() && failed.worldPresent() && failed.identityLeaseRetained() && failed.outboundAttached() && (failed.admittedActionCount() == 0), "Owned failure lost fail-closed ownership.");
						PhantomAssertions.assertEquals(1L, failed.cleanupFailureSequence(), "Owned failure was not recorded once.");
						PhantomAssertions.assertFalse(live.get().hasPendingOwnedStore() || _repository.findComponent(id, org.l2jmobius.gameserver.phantoms.background.PhantomOwnedStoreIntent.COMPONENT_TYPE).isPresent(), "No-intent failure manufactured a receipt.");
						if (boundary.equals("CLEANUP_PREPARE_REJECTION"))
						{
							PhantomAssertions.assertEquals("NATIVE_STORE", failed.cleanupPhase().name(), "PREPARE rejection was assigned to the wrong lifecycle call.");
							PhantomAssertions.assertEquals(IllegalStateException.class.getName(), failed.cleanupFailureClass(), "PREPARE rejection lost its exact exception type.");
							PhantomAssertions.assertTrue(failed.cleanupFailureMessage().startsWith("OWNED_STORE_PREPARE:"), "PREPARE rejection lost its typed reason.");
							PhantomAssertions.assertEquals(pinnedCanonical, canonical(objectId), "Rejected PREPARE wrote canonical rows.");
							PhantomAssertions.assertEquals(pinnedBackground, transaction.load(id).state(), "Rejected PREPARE wrote background state.");
						}
						else
						{
							PhantomAssertions.assertEquals("POST_STORE", failed.cleanupPhase().name(), "Post-finalize lifecycle failure lost its exact phase.");
							PhantomAssertions.assertEquals(InjectedFailure.class.getName(), failed.cleanupFailureClass(), "Post-finalize lifecycle lost its exception type.");
							PhantomAssertions.assertEquals(State.READY, transaction.load(id).state().state(), "Owned FINALIZE did not reach READY before the callback fault.");
						}
						final Canonical afterFailure = canonical(objectId);
						context.record("cleanupDiagnostic." + boundary, failed.cleanupPhase() + "/" + failed.cleanupFailureClass() + "/" + failed.cleanupFailureMessage());
						armed.set(false);
						PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, materialization.retryCleanup(id).status(), "Exact no-intent failure shape did not safely retry.");
						PhantomAssertions.assertTrue(materialization.find(id).isEmpty() && (org.l2jmobius.gameserver.model.World.getInstance().findObject(objectId) == null) && (PhantomIdentityLeaseRegistry.getInstance().getOwnerKind(objectId) == null), "Safe owned retry retained runtime ownership.");
						if (boundary.equals("CLEANUP_FINALIZED_POST_STORE")) { PhantomAssertions.assertEquals(afterFailure, canonical(objectId), "Post-finalize retry changed canonical gameplay state."); }
						continue;
					}
					if (boundary.startsWith("SQL_"))
					{
						final Player actor = live.get();
						if (boundary.equals("SQL_SUB")) { actor.getSubClasses().put(1, new org.l2jmobius.gameserver.model.actor.holders.player.SubClassHolder(1, 1)); }
						final var faults = new AtomicInteger();
						try (var ignored = injectNativeStoreSqlFailure(boundary.equals("SQL_SUB"), faults))
						{
							background.captureVisibleArrival(id, actor, fixture.goal(), farm.anchor().id());
							PhantomAssertions.assertEquals(1, faults.get(), "Native SQL fault was not reached: " + boundary);
							PhantomAssertions.assertEquals(0, nativeCompleted.get(), "Native SQL exception was reported as a completed store: " + boundary);
							PhantomAssertions.assertTrue(actor.hasPendingOwnedStore(), "Native SQL failure lost its attached receipt.");
							PhantomAssertions.assertTrue(_repository.findComponent(id, org.l2jmobius.gameserver.phantoms.background.PhantomOwnedStoreIntent.COMPONENT_TYPE).isPresent(), "Native SQL failure erased durable intent.");
						}
						finally { if (boundary.equals("SQL_SUB")) { actor.getSubClasses().remove(1); } }
						armed.set(false);
						PhantomAssertions.assertEquals(PhantomBackgroundService.VisibleStoreStatus.SUCCESS, background.resumeVisibleOwnedStore(id, actor, fixture.goal()).status(), "Native SQL receipt did not resume.");
					}
					if (boundary.equals("WORLD_OWNER"))
					{
						final Player actor = live.get(); final Canonical pinned = canonical(objectId);
						final var foreign = new org.l2jmobius.gameserver.model.WorldObject(objectId) { @Override public boolean isAutoAttackable(org.l2jmobius.gameserver.model.actor.Creature attacker) { return false; } @Override public void sendInfo(Player observer) {} };
						org.l2jmobius.gameserver.model.World.getInstance().removeObject(actor); org.l2jmobius.gameserver.model.World.getInstance().addObject(foreign);
						try { PhantomAssertions.assertThrows(IllegalStateException.class, actor::storeMe, "Foreign World owner crossed owned store guard."); PhantomAssertions.assertEquals(pinned, canonical(objectId), "Foreign World rejection wrote canonical rows."); }
						finally { org.l2jmobius.gameserver.model.World.getInstance().removeObject(foreign); org.l2jmobius.gameserver.model.World.getInstance().addObject(actor); }
						try (var competing = PhantomIdentityLeaseRegistry.getInstance().tryAcquire(objectId, PhantomIdentityLeaseRegistry.OwnerKind.REAL_LOGIN)) { PhantomAssertions.assertTrue(competing == null, "Concurrent real login bypassed Phantom identity owner."); }
					}
					if (boundary.equals("LAZY_INVENTORY"))
					{
						final var actor = live.get(); final var stack = actor.getInventory().addItem(ItemProcessType.REWARD, 57, 10, actor, this); final long oldCount = stack.getCount(); stack.changeCount(ItemProcessType.REWARD, 3, actor, this); PhantomAssertions.assertEquals(oldCount + 3, stack.getCount(), "Lazy runtime inventory mutation failed.");
					}
					if (boundary.startsWith("LIVE_") || boundary.startsWith("ARRIVAL") || boundary.equals("NORMAL_ARRIVAL"))
					{
						final Player actor;
						final PlayerNativeWork.Owner nativeOwner;
						final long epoch;
						try (var action = materialization.tryAcquireAction(id).orElseThrow())
						{
							actor = action.player(); nativeOwner = actor.getNativeWorkOwner();
							epoch = materialization.find(id).orElseThrow().materializedAtNanos();
						}
						PhantomAssertions.assertTrue(actor == live.get() && nativeOwner != null && nativeOwner.player() == actor && nativeOwner.isCurrent() && nativeOwner.epoch() == epoch && PlayerNativeWork.current(nativeOwner) == null, "Matrix arrival did not retain exact lifetime outside ActionLease.");
						final boolean result = background.captureVisibleArrival(id, actor, fixture.goal(), farm.anchor().id());
						PhantomAssertions.assertTrue(actor.getNativeWorkOwner() == nativeOwner && nativeOwner.isCurrent() && nativeOwner.epoch() == materialization.find(id).orElseThrow().materializedAtNanos(), "Matrix arrival changed native Player owner/epoch.");
						if (boundary.equals("NORMAL_ARRIVAL")) { PhantomAssertions.assertTrue(result, "Normal owned arrival was rejected."); }
					}
					else if (!boundary.startsWith("SQL_"))
					{
						final var result = materialization.dematerialize(id);
						if (boundary.equals("NORMAL_CLEANUP") || boundary.equals("LAZY_INVENTORY") || boundary.equals("WORLD_OWNER") || boundary.equals("UNSUPPORTED_ABSENT")) { PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, result.status(), "Normal owned cleanup was rejected: " + boundary); }
					}
					if (boundary.equals("UNSUPPORTED_ABSENT")) { PhantomAssertions.assertEquals(Status.STATE_ABSENT, transaction.load(id).status(), "Unsupported goal acquired background ownership."); continue; }
					final var atFailure = transaction.load(id).state();
					if (boundary.equals("NORMAL_ARRIVAL"))
					{
						final Field callbackField = Player.class.getDeclaredField("_ownedStoreBoundary"); callbackField.setAccessible(true);
						final Object callback = callbackField.get(live.get());
						final Field beforeField = callback.getClass().getDeclaredField("_before"); beforeField.setAccessible(true);
						PhantomAssertions.assertEquals(null, beforeField.get(callback), "Journal OFF collected a native snapshot.");
					}
					final var canonicalAtFailure = canonical(objectId);
					context.record("ownedStore." + boundary + ".failure", atFailure.state() + " BG=" + atFailure.vitals() + "/" + atFailure.position() + " CANONICAL=" + canonicalAtFailure);
					// Simulate loss of this process's Player without a further native store.
					armed.set(false);
					if (boundary.startsWith("LIVE_"))
					{
						final var navigation = new PhantomNavigationService(new PhantomMetrics());
						final var route = org.l2jmobius.gameserver.phantoms.background.PhantomNormalGatekeeperTravel.load(Path.of("data/phantoms/travel/high-five-normal-gk.xml"), _production.topology());
						final var travel = new org.l2jmobius.gameserver.phantoms.background.PhantomVisibleFarmTravel(materialization, background, route, navigation, _ -> true, noSignals());
						final var decision = new PhantomBackgroundDecision(background, travel::arrive, (_, _) -> false, _ -> {});
						final var candidates = new PhantomCandidateRegistry(); decision.registerCandidates(candidates); candidates.seal();
						final var planning = new PhantomPlanningContext(id, fixture.goal(), PhantomCapabilitySet.empty(), PhantomActivityState.ACTIVE, 7, 9, 1234, 1);
						final var selected = new PhantomUtilitySelector().select(candidates.snapshot(), planning);
						PhantomAssertions.assertTrue(selected.candidate() != null, "Owned pending lost its visible candidate.");
						final var plan = selected.candidate().planFactory().create(planning);
						final var handlers = new PhantomStepHandlerRegistry(); decision.registerHandlers(handlers); handlers.seal();
						final var resumed = handlers.snapshot().get(plan.steps().getFirst().actionKey()).execute(new PhantomStepContext(id, fixture.goal(), plan, plan.steps().getFirst(), PhantomActivityState.ACTIVE, 7, 9, 1234, 1, () -> false));
						PhantomAssertions.assertEquals(PhantomStepResult.Type.SUCCESS, resumed.type(), "Existing visible handler could not resume own " + boundary + ": " + resumed.reasonKey());
						PhantomAssertions.assertEquals(State.MATERIALIZED, transaction.load(id).state().state(), "Live resume used cold READY recovery.");
						PhantomAssertions.assertFalse(_repository.findComponent(id, org.l2jmobius.gameserver.phantoms.background.PhantomOwnedStoreIntent.COMPONENT_TYPE).isPresent(), "Live retry retained a resolved receipt.");
					}
					if (boundary.equals("ARRIVAL_RETRY_MOVED"))
					{
						live.get().setXYZInvisible(live.get().getX() + 10, live.get().getY() + 10, live.get().getZ()); live.get().setCurrentHp(100); live.get().setHeading(3276);
						final int latestX = live.get().getX(); final int latestY = live.get().getY();
						PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, materialization.dematerialize(id).status(), "Old arrival receipt prevented fresh cleanup.");
						PhantomAssertions.assertEquals(latestX, canonical(objectId).x(), "Cleanup replay lost latest native X."); PhantomAssertions.assertEquals(latestY, canonical(objectId).y(), "Cleanup replay lost latest native Y."); PhantomAssertions.assertEquals(3276, canonical(objectId).heading(), "Cleanup replay lost latest native heading.");
					}
					if (boundary.startsWith("RETRY_"))
					{
						PhantomAssertions.assertTrue(live.get().isOnline(), "Retry regression lost the retained live Player before retry.");
						PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, materialization.retryCleanup(id).status(), "Retained Player cleanup retry failed: " + boundary);
						PhantomAssertions.assertTrue(!live.get().isOnline(), "Retry retained the native Player.");
					}
					if (live.get().isOnline()) { live.get().deleteMe(); }
					materialization.retryCleanup(id);
					final Canonical atRetirement = canonical(objectId);
					PhantomAssertions.assertThrows(IllegalStateException.class, () -> live.get().storeMe(), "Retired Player crossed a new identity/epoch store boundary.");
					PhantomAssertions.assertEquals(atRetirement, canonical(objectId), "Retired store callback wrote canonical rows.");
					final var recovered = new PhantomBackgroundTransaction().abortMaterialization(id, objectId);
					context.record("ownedStore." + boundary + ".restart", recovered.status() + "/" + (recovered.state() == null ? "null" : recovered.state().state()));
					if (!recovered.successful()) { unsafe.add(boundary); }
				}
				finally
				{
					armed.set(false);
					if ((live.get() != null) && live.get().isOnline()) { live.get().deleteMe(); }
					materialization.retryCleanup(id); materialization.shutdown();
					background.beginStop(); background.finishStop();
					deleteProfile(profile);
				}
			}
		}
		PhantomAssertions.assertEquals(List.of(), unsafe, "Owned native store lost a provable restart projection: " + unsafe);
	}

	private static AutoCloseable injectNativeStoreSqlFailure(boolean subclass, AtomicInteger faults) throws Exception
	{
		final Field field = DatabaseFactory.class.getDeclaredField("DATABASE_POOL");
		field.setAccessible(true);
		final var original = (com.zaxxer.hikari.HikariDataSource) field.get(null);
		field.set(null, new com.zaxxer.hikari.HikariDataSource()
		{
			@Override public Connection getConnection() throws java.sql.SQLException
			{
				final Connection delegate = original.getConnection();
				return (Connection) java.lang.reflect.Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[] { Connection.class }, (_, method, args) ->
				{
					if (method.getName().equals("prepareStatement") && (args[0] instanceof String sql) && sql.startsWith(subclass ? "UPDATE character_subclasses SET exp=?" : "UPDATE characters SET level=?,maxHp=?")) { faults.incrementAndGet(); throw new java.sql.SQLException("TEST_NATIVE_STORE_SQL_FAILURE"); }
					try { return method.invoke(delegate, args); }
					catch (InvocationTargetException failure) { throw failure.getCause(); }
				});
			}
		});
		return () -> field.set(null, original);
	}

	private static void setOwnedStoreTown(Player player)
	{
		player.stopAllTasks();
		player.setXYZInvisible(45978, 47886, -3488);
		player.setHeading(12773);
		player.setCurrentHp(player.getMaxHp());
		player.setCurrentMp(player.getMaxMp());
		player.setCurrentCp(Math.min(54, player.getMaxCp()));
	}

	private void testPendingNativeHandoff(PhantomTestContext context, boolean alreadyComplete) throws Exception
	{
		testPendingNativeHandoff(context, alreadyComplete, false);
	}

	private void testPendingNativeHandoff(PhantomTestContext context, boolean alreadyComplete, boolean levelLossOnly) throws Exception
	{
		final String evidencePrefix = levelLossOnly ? "m1.levelLoss" : alreadyComplete ? "m1.complete" : "m1.running";
		final boolean longHistory = !alreadyComplete;
		final var farm = levelLossOnly ? productionFarmSelection() : new ProductionFarmSelection(20481, _production.topology().findAnchor("population.farming.human-mystic.20481").orElseThrow());
		try (var fixture = openProductionPlayerFixture(farm.anchor(), levelLossOnly ? _production.knowledge().findNpc(farm.npcId()).orElseThrow().level() : 3, levelLossOnly ? null : PlayerClass.ELVEN_FIGHTER, farm))
		{
			final var profile = _repository.create(fixture.player().getObjectId());
			final long id = profile.profileId();
			final var goals = new PhantomGoalStateStore(_repository);
			goals.insert(id, fixture.goal());
			final var authority = _production.authority();
			final var transaction = new PhantomBackgroundTransaction();
			final var owner = new AtomicReference<PhantomMaterializationService>();
			final var background = new PhantomBackgroundService(_repository, goals, PhantomIdentityLeaseRegistry.getInstance(), transaction, authority, new PhantomBackgroundCompetitionRegistry(), noSignals(), owner::get);
			final var metrics = new PhantomMetrics();
			final var loadDiagnostic = new PhantomMaterializationLifecyclePort()
			{
				@Override public void beforeMaterialize(long value, int objectId) {}
				@Override public void materializeSucceeded(long value, int objectId) {}
				@Override public void materializeAborted(long value, int objectId) {}
				@Override public void beforeStore(long value, Player player) {}
				@Override public void afterStore(long value, Player player) {}
				@Override public void afterPlayerLoad(long value, Player player)
				{
					final var expected = transaction.load(value).state();
					if (alreadyComplete && (expected.state() == State.DEAD))
					{
						final long originalSp = player.getSp();
						try
						{
							player.getStat().setSp(originalSp + 1);
							PhantomAssertions.assertThrows(IllegalStateException.class, () -> background.afterPlayerLoad(value, player), "Derived-vitals refresh hid unrelated SP mismatch.");
							PhantomAssertions.assertEquals(expected, transaction.load(value).state(), "Rejected native load changed the durable snapshot.");
						}
						finally { player.getStat().setSp(originalSp); }
					}
					if (!authority.matchesRuntime(player, expected))
					{
						context.record("m1.pendingLoadExpected", expected.progress() + " / " + expected.vitals() + " / " + expected.position());
						context.record("m1.pendingLoadActual", "level=" + player.getLevel() + " exp=" + player.getExp() + " sp=" + player.getSp() + " ebd=" + player.getExpBeforeDeath() + " HP=" + player.getCurrentHp() + "/" + player.getMaxHp() + " MP=" + player.getCurrentMp() + "/" + player.getMaxMp() + " CP=" + player.getCurrentCp() + "/" + player.getMaxCp() + " pos=" + player.getX() + "," + player.getY() + "," + player.getZ());
					}
				}
			};
			final var lifecycle = new org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationLifecycleBridge();
			final var materialization = new PhantomMaterializationService(_repository, PhantomIdentityLeaseRegistry.getInstance(), metrics, new PhantomDiagnosticTrace(false, 64, 16, metrics), 1, _ -> {}, lifecycle, 5000, 10000);
			owner.set(materialization);
			background.start(); materialization.start();
			final var topology = org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyService.fromSnapshotForTesting(_production.topology().snapshot(), _production.topologyBackend(), org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPolicy.productionDefaults(), noSignals());
			topology.start();
			final var publisher = new org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPositionPublisher(topology, _ -> Optional.empty());
			publisher.ready(id);
			background.installCommittedPositionPublisher(publisher::committed);
			org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyService ecology = null;
			PhantomVisibleAutoPlay autoPlay = null;
			PhantomDecisionEngine engine = null;
			PhantomNavigationService nativeNavigation = null;
			org.l2jmobius.gameserver.phantoms.background.PhantomVisibleFarmTravel nativeTravel = null;
			org.l2jmobius.gameserver.model.actor.instance.Monster monster = null;
			try
			{
				final var captured = authority.capture(id, fixture.player(), fixture.goal(), null);
				PhantomAssertions.assertTrue(new PhantomHistoricalBackgroundPlanner(_production.knowledge(), _production.topology(), authority).remainsSuitable(captured, fixture.goal()), "Pending fixture target must match its configured TEST level.");
				fixture.player().storeMe();
				PhantomAssertions.assertEquals(Status.SUCCESS, captureTestBaseline(transaction, captured, fixture.goal()).status(), "Pending replay baseline failed.");
				publisher.committed(id, captured.position());
				fixture.releaseRuntime();
				final var planner = new PhantomHistoricalBackgroundPlanner(_production.knowledge(), _production.topology(), authority);
				final var historical = new PhantomHistoricalBackgroundService(_repository, goals, planner, background, materialization);
				lifecycle.install(PhantomMaterializationLifecyclePort.chain(loadDiagnostic, PhantomMaterializationLifecyclePort.chain(historical, background)));
				final var populationCatalog = org.l2jmobius.gameserver.phantoms.population.PhantomPopulationCatalog.load(context.moduleRoot().resolve("dist/game/data/phantoms/population/high-five-population-v1.xml"), java.time.ZoneOffset.UTC);
				final var catalog = org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyCatalog.load(context.moduleRoot().resolve("dist/game/data/phantoms/population/high-five-ecology-v1.xml"), populationCatalog, org.l2jmobius.gameserver.phantoms.social.PhantomSocialCatalog.load(context.moduleRoot().resolve("dist/game/data/phantoms/social/high-five-social-v1.xml")));
				final var metadata = new PhantomPopulationTestDoubles.MemoryStore(populationCatalog.hash());
				final var seed = metadata.seedReady(id, 1);
				long selectedTarget = 29843626L;
				if (longHistory)
				{
					final var pace = catalog.assign(org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyState.Preset.LIVING, 1, 1, context.seed(), selectedTarget, 0, 0, seed.state().scheduleTemplate());
					while (!catalog.productive(pace, Math.floorDiv(selectedTarget - 4320, pace.productiveBlockMinutes())) || Math.floorMod(selectedTarget - 4320, pace.productiveBlockMinutes()) == pace.productiveBlockMinutes() - 1 || !populationCatalog.evaluate(seed.state().scheduleTemplate(), java.time.Instant.ofEpochSecond(selectedTarget * 60), java.time.ZoneOffset.UTC, seed.state().schedulePhaseMinutes()).state().equals(PhantomActivityState.BACKGROUND))
					{
						selectedTarget++;
						PhantomAssertions.assertTrue(selectedTarget < 29843626L + 1440, "No coherent productive TEST start in the production day.");
					}
				}
				final long target = selectedTarget;
				final long from = target - (longHistory ? 4320 : 13);
				final var assignment = catalog.assign(org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyState.Preset.LIVING, 1, 1, context.seed(), target, 0, 0, seed.state().scheduleTemplate());
				final long windowTarget = longHistory ? Math.min(target, (Math.floorDiv(from, assignment.productiveBlockMinutes()) + 1) * assignment.productiveBlockMinutes()) : target;
				final var generation = planner.generation();
				final var catchupStore = new PhantomBackgroundCatchupStore(_repository, goals);
				final var restored = new PhantomBackgroundCatchupState(PhantomBackgroundCatchupState.Status.RUNNING, "d".repeat(64), context.seed(), from, windowTarget, from, 0, 0, 1, generation.knowledgeGeneration(), generation.topologyGeneration(), fixture.goal().goalId(), fixture.goal().revision(), "a".repeat(64), PhantomBackgroundState.MODEL_VERSION, generation.authorityHashes(), "");
				catchupStore.claim(id, restored);
				final var partial = historical.advance(id, alreadyComplete ? 13 : 1, alreadyComplete ? 13 : 1);
				PhantomAssertions.assertTrue(partial.successful(), "Real pending initial progress failed: " + partial.reason());
				PhantomAssertions.assertEquals(alreadyComplete ? PhantomBackgroundCatchupState.Status.COMPLETE : PhantomBackgroundCatchupState.Status.RUNNING, partial.snapshot().state().status(), "Fixture did not persist the required inner status.");
				final var populationState = seed.state().initialized(profile.characterObjectId(), seed.state().initializationHash()).ready();
				final var component = _repository.insertComponent(id, org.l2jmobius.gameserver.phantoms.population.PhantomPopulationState.COMPONENT_TYPE, org.l2jmobius.gameserver.phantoms.population.PhantomPopulationState.SCHEMA_VERSION, new org.l2jmobius.gameserver.phantoms.population.PhantomPopulationStateCodec().encode(populationState));
				final var managed = new org.l2jmobius.gameserver.phantoms.population.PhantomPopulationStore.ManagedSnapshot(profile, component, populationState);
				final var store = new org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyStore(_repository);
				final var pending = new org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyState(catalog.hash(), assignment.preset(), 1, 1, from, from, from, longHistory ? target : from, longHistory ? assignment.pace() : org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyState.Pace.CASUAL, longHistory ? assignment.productiveShareBasisPoints() : 2500, assignment.productiveBlockMinutes(), assignment.personality(), assignment.initialSocialTraits(), populationState.scheduleTemplate(), org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyState.Disposition.MANAGED, target + 10000, 0, "", 0, 0, 0, "").beginRequest(restored.requestId(), windowTarget);
				final var persisted = store.insert(id, pending);
				final var coldStore = new PhantomPopulationEcologyGoal033Suite.EcologyMemoryStore(null);
				final var coldLoads = new java.util.concurrent.atomic.AtomicInteger();
				final var firstClaim = new java.util.concurrent.atomic.AtomicLong();
				final Map<Long, org.l2jmobius.gameserver.phantoms.population.PhantomPopulationStore.ManagedSnapshot> cohort = new java.util.HashMap<>();
				cohort.put(id, managed);
				for (int index = 0; index < (longHistory ? 1279 : 9999); index++)
				{
					final long coldId = 1_000_000_000L + index;
					cohort.put(coldId, metadata.seedReady(coldId, 1));
					coldStore.insert(coldId, catalog.assign(org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyState.Preset.LIVING, 1, index + 2, context.seed(), longHistory ? from : target, 0, 0, "evening"));
				}
				final var persistence = new org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyService.PersistencePort()
				{
					@Override public Optional<org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyStore.StoredState> load(long value) { if (value == id) { firstClaim.compareAndSet(0, System.nanoTime()); return store.load(value); } coldLoads.incrementAndGet(); return coldStore.load(value); }
					@Override public org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyStore.StoredState insert(long value, org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyState state) { return value == id ? store.insert(value, state) : coldStore.insert(value, state); }
					@Override public org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyStore.StoredState save(long value, org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyStore.StoredState expected, org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyState state) { return value == id ? store.save(value, expected, state) : coldStore.save(value, expected, state); }
					@Override public org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyStore.ArchivedResult archive(org.l2jmobius.gameserver.phantoms.population.PhantomPopulationStore.ManagedSnapshot value, org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyStore.StoredState state, long generation, long now) { throw new AssertionError("Replay cannot archive."); }
				};
				final var competingHistory = new PhantomPopulationEcologyGoal033Suite.HistoricalMemoryPort();
				final var competingIntervals = new java.util.concurrent.atomic.AtomicLong();
				final var nativeIntervals = new java.util.concurrent.atomic.AtomicLong(partial.advancedIntervals());
				final var nativeWindows = new java.util.concurrent.atomic.AtomicInteger(1);
				final var history = new org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyService.HistoricalPort()
				{
					@Override public Optional<PhantomBackgroundCatchupStore.Snapshot> status(long value) { return value == id ? historical.status(value) : competingHistory.status(value); }
					@Override public PhantomHistoricalBackgroundService.Result begin(long value, long start, long end, long seedValue) { if (value != id) { return competingHistory.begin(value, start, end, seedValue); } final var result = historical.begin(value, start, end, seedValue); if (result.successful()) { nativeWindows.incrementAndGet(); } return result; }
					@Override public PhantomHistoricalBackgroundService.Result advance(long value, int intervals, int minutes) { if (value != id) { final var result = competingHistory.advance(value, intervals, minutes); competingIntervals.addAndGet(result.advancedIntervals()); return result; } final var result = historical.advance(value, intervals, minutes); nativeIntervals.addAndGet(result.advancedIntervals()); return result; }
				};
				final var presence = new org.l2jmobius.gameserver.phantoms.population.PhantomPresenceRegistry(1);
				presence.schedule(id, populationCatalog.evaluate(populationState.scheduleTemplate(), java.time.Instant.ofEpochSecond(target * 60), java.time.ZoneOffset.UTC, populationState.schedulePhaseMinutes()).state());
				PhantomAssertions.assertTrue(presence.isOnline(id), "Replay calendar is OFFLINE.");
				final var initialPoint = topology.findProfile(id).orElseThrow().point();
				final var backpressure = new PhantomRelevanceSignalPort()
				{
					@Override public SignalDelivery submit(long value, org.l2jmobius.gameserver.phantoms.activity.PhantomRelevanceSignal signal) { return SignalDelivery.BACKPRESSURE; }
					@Override public SignalDelivery withdraw(long value, String source, long sequence) { return SignalDelivery.BACKPRESSURE; }
				};
				final var humanPoint = new AtomicReference<>(initialPoint);
				final var locality = new org.l2jmobius.gameserver.phantoms.topology.PhantomHumanLocalityControl(topology, backpressure, () -> List.of(humanPoint.get()), () -> System.nanoTime() / 1_000_000L, presence::isOnline);
				final var released = new java.util.concurrent.atomic.AtomicBoolean();
				final var heldWorkers = new java.util.concurrent.ConcurrentLinkedQueue<Runnable>();
				ecology = new org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyService(catalog, populationCatalog, persistence, history, value -> materialization.find(value).isPresent(), _ -> "", java.time.Clock.fixed(java.time.Instant.ofEpochSecond(target * 60), java.time.ZoneOffset.UTC), java.time.ZoneOffset.UTC, org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyState.Preset.LIVING, 0, 10, worker -> { if (released.get()) { ThreadPool.execute(worker); } else { heldWorkers.add(worker); } return true; });
				ecology.enablePeriodicDueMode();
				ecology.installDemandPump(100, () -> System.nanoTime() / 1_000_000L, (wake, delay) -> { final var future = ThreadPool.schedule(wake, delay); return () -> future.cancel(false); });
				ecology.installRuntime(value -> Optional.ofNullable(cohort.get(value)), new org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyService.PopulationEvents()
				{
					@Override public void requestArchive(long value) { throw new AssertionError("No retirement in replay."); }
					@Override public void reconcilePopulation() {}
					@Override public void ecologyFenceChanged(long value) {}
				});
				ecology.installMaterializationDemand(locality::isCurrentLocal);
				long expectedProductiveIntervals = windowTarget - from;
				int expectedProductiveWindows = 1;
				if (longHistory)
				{
					var calendar = pending.completeRequest();
					while (calendar.calendarCursorEpochMinute() < target)
					{
						final var window = ecology.nextProductiveWindow(calendar, populationState.schedulePhaseMinutes(), target);
						if (window.productive()) { expectedProductiveIntervals += window.endEpochMinute() - window.startEpochMinute(); expectedProductiveWindows++; }
						PhantomAssertions.assertTrue(window.endEpochMinute() > calendar.calendarCursorEpochMinute(), "Production calendar scan made no progress.");
						calendar = calendar.advanceCalendar(window.endEpochMinute());
					}
				}
				final var currentEcology = ecology;
				if (longHistory)
				{
					locality.installPreparationDemand((facts, slots) ->
					{
						final var competing = new java.util.ArrayList<org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyService.DemandFact>();
						for (int index = 0; index < 973; index++) { competing.add(new org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyService.DemandFact(1_000_000_000L + index, 1, false, 10000 + index, 1)); }
						competing.addAll(facts);
						currentEcology.updateMaterializationDemand(competing, slots);
					}, () -> 1);
				}
				else { locality.installPhysicalDemand(ecology::requestMaterializationDue); }
				for (var value : cohort.values()) { ecology.register(value); }
				final var nativeBoundary = new org.l2jmobius.gameserver.phantoms.activity.PhantomMaterializationServiceActivityPort(materialization, true);
				final var gate = new org.l2jmobius.gameserver.phantoms.activity.PhantomReconcileFirstActivityPort(nativeBoundary);
				gate.installPopulationReadiness(presence, locality, ecology);
				final long startNanos = System.nanoTime();
				locality.onPulse();
				PhantomAssertions.assertEquals(PhantomRelevanceSignalPort.SignalDelivery.BACKPRESSURE, locality.deliverySnapshot().get(id), "Replay did not exercise independent physical demand.");
				released.set(true);
				for (Runnable worker; (worker = heldWorkers.poll()) != null;) { ThreadPool.execute(worker); }
				while (!ecology.dueSnapshot(id).complete() && (System.nanoTime() - startNanos < TimeUnit.SECONDS.toNanos(longHistory ? 180 : 22)))
				{
					locality.onPulse();
					if (longHistory && !locality.isCurrentLocal(id)) { humanPoint.set(topology.findProfile(id).orElseThrow().point()); }
					if (ecology.progressSnapshot(id).currentStage().equals("blocked")) { break; }
					Thread.sleep(10);
				}
				context.record(evidencePrefix + ".readyMillis", (System.nanoTime() - startNanos) / 1_000_000L);
				context.record(evidencePrefix + ".firstClaimMillis", Math.max(0, firstClaim.get() - startNanos) / 1_000_000L);
				context.record(evidencePrefix + ".coldLoads", coldLoads.get());
				context.record(evidencePrefix + ".progress", ecology.progressSnapshot(id));
				context.record(evidencePrefix + ".innerIntervals", historical.status(id).orElseThrow().state().intervalOrdinal());
				context.record(evidencePrefix + ".reason", ecology.dueSnapshot(id).reason());
				context.record(evidencePrefix + ".productiveWork", "nativeWindows=" + nativeWindows.get() + " nativeIntervals=" + nativeIntervals.get() + " ordinaryIntervals=" + competingIntervals.get());
				PhantomAssertions.assertTrue(ecology.dueSnapshot(id).complete(), "Native pending replay failed: " + ecology.dueSnapshot(id) + " / " + ecology.progressSnapshot(id));
				if (!longHistory)
				{
					PhantomAssertions.assertEquals(persisted.rowVersion() + 1, store.load(id).orElseThrow().rowVersion(), "Matching request was committed more than once.");
					PhantomAssertions.assertEquals(13L, historical.status(id).orElseThrow().state().intervalOrdinal(), "Replay reset or duplicated inner intervals.");
				}
				else
				{
					PhantomAssertions.assertEquals(4320L, target - from, "Native TEST shortened the calendar horizon.");
					PhantomAssertions.assertTrue(nativeWindows.get() > 1 && nativeIntervals.get() > 16, "Native TEST did not complete multiple production windows.");
					PhantomAssertions.assertEquals(expectedProductiveWindows, nativeWindows.get(), "Native TEST skipped or duplicated a productive window.");
					PhantomAssertions.assertEquals(expectedProductiveIntervals, nativeIntervals.get(), "Native TEST skipped or duplicated committed productive minutes.");
					PhantomAssertions.assertTrue(competingIntervals.get() > 0, "Ordinary competing history did not progress.");
					context.record(evidencePrefix + ".history", "calendarMinutes=4320 productiveWindows=" + nativeWindows.get() + " actualIntervals=" + nativeIntervals.get() + " cohort=1 native+1279 synthetic metadata; 973 synthetic far demands retained");
				}
				PhantomAssertions.assertTrue(ecology.snapshot().maximumPulseProfiles() <= 4 && ecology.snapshot().maximumPulseIntervals() <= 16, "Wake sources multiplied the ecology budget.");
				PhantomAssertions.assertTrue(coldLoads.get() >= 2, "Urgent work starved ordinary cold metadata.");
				final var beforeLoad = transaction.load(id).state();
				final long beforeLoadIntervals = historical.status(id).orElseThrow().state().intervalOrdinal();
				humanPoint.set(topology.findProfile(id).orElseThrow().point()); locality.onPulse();
				final var materialized = gate.materialize(id);
				PhantomAssertions.assertEquals(org.l2jmobius.gameserver.phantoms.activity.PhantomActivityMaterializationPort.Outcome.SUCCESS, materialized.outcome(), "Same physical demand did not materialize the native Player: " + materialized.reason() + " / " + nativeBoundary.diagnosticFailures());
				PhantomAssertions.assertEquals(beforeLoad.progress(), transaction.load(id).state().progress(), "Native derived-vitals refresh fabricated progress.");
				PhantomAssertions.assertEquals(beforeLoadIntervals, historical.status(id).orElseThrow().state().intervalOrdinal(), "Native load replayed completed history.");
				try (var lease = materialization.tryAcquireAction(id).orElseThrow()) { PhantomAssertions.assertTrue(authority.matchesRuntime(lease.player(), transaction.load(id).state()), "Materialized Player differs from its refreshed durable projection."); }
				if (levelLossOnly)
				{
					PhantomAssertions.assertEquals(State.DEAD, beforeLoad.state(), "Level-loss regression must enter the real DEAD load boundary.");
					PhantomAssertions.assertTrue(beforeLoad.progress().level() < captured.progress().level(), "Level-loss regression did not reduce the native level.");
					context.record("m1.pendingLevelLossFence", "native derived vitals refreshed; altered SP rejected before write; completed intervals unchanged");
					return;
				}
				if (beforeLoad.state() == State.DEAD)
				{
					try (var lease = materialization.tryAcquireAction(id).orElseThrow())
					{
						final var town = MapRegionData.getInstance().getTeleToLocation(lease.player(), TeleportWhereType.TOWN);
						context.record(evidencePrefix + ".recoveryTown", town + " region=" + (town == null ? 0 : MapRegionData.getInstance().getMapRegionLocId(town.getX(), town.getY())));
					}
					final var recovered = background.recoverOrdinaryNativeCorpse(id, profile.characterObjectId());
					PhantomAssertions.assertTrue(recovered.successful(), "Stored death did not perform native recovery: " + recovered.reason());
					PhantomAssertions.assertTrue(background.recover(id, goals.load(id).orElseThrow().goal(), PhantomActivityState.ACTIVE).successful(), "Native recovery did not reconcile its existing owner.");
					PhantomAssertions.assertEquals(beforeLoad.progress(), transaction.load(id).state().progress(), "Native recovery fabricated progress.");
					context.record(evidencePrefix + ".recovery", "existing native corpse/town recovery; TEST call, 45-second policy covered by existing native timer contract");
				}
				final Player player;
				try (var lease = materialization.tryAcquireAction(id).orElseThrow()) { player = lease.player(); }
				PhantomAssertions.assertEquals(profile.characterObjectId(), player.getObjectId(), "Readiness replaced native identity.");
				final var candidates = new PhantomCandidateRegistry(); candidates.seal();
				final var handlers = new PhantomStepHandlerRegistry(); handlers.seal();
				engine = new PhantomDecisionEngine(goals, candidates, handlers, metrics, 1); engine.start(); engine.attach(id);
				final var currentEngine = engine;
				final var nativeGoal = goals.load(id).orElseThrow().goal();
				final var travelQuery = org.l2jmobius.gameserver.phantoms.background.PhantomNormalGatekeeperTravel.load(Path.of("data/phantoms/travel/high-five-normal-gk.xml"), _production.topology());
				nativeNavigation = new PhantomNavigationService(metrics);
				PhantomAssertions.assertTrue(nativeNavigation.start(), "Pending native travel did not start.");
				nativeTravel = new org.l2jmobius.gameserver.phantoms.background.PhantomVisibleFarmTravel(materialization, background, travelQuery, nativeNavigation, presence::isOnline, noSignals());
				final int departureX = player.getX();
				final int departureY = player.getY();
				final long arrivalDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(100);
				boolean arrived = false;
				while (!arrived && (System.nanoTime() < arrivalDeadline))
				{
					arrived = nativeTravel.arrive(id, nativeGoal);
					if (!arrived) { Thread.sleep(25); }
				}
				PhantomAssertions.assertTrue(arrived, "Pending native farm travel did not arrive: " + nativeTravel.reason(id) + " / " + nativeNavigation.snapshot());
				PhantomAssertions.assertEquals(profile.characterObjectId(), player.getObjectId(), "Native travel changed pending identity.");
				try (var lease = materialization.tryAcquireAction(id).orElseThrow()) { PhantomAssertions.assertTrue(lease.player() == player, "Native travel replaced the materialized Player instance."); }
				final double displacement = Math.hypot((long)player.getX() - departureX, (long)player.getY() - departureY);
				if (beforeLoad.state() == State.DEAD) { PhantomAssertions.assertTrue(displacement >= nativeNavigation.policy().minimumProgress(), "Native corpse recovery did not move to the farm."); }
				PhantomAssertions.assertEquals(PhantomBackgroundGoalSpec.parse(nativeGoal).anchorId(), transaction.load(id).state().position().committedAnchorId(), "Pending native arrival did not capture its farm anchor.");
				context.record(evidencePrefix + ".nativeTravel", "distance=" + displacement + " arrival=" + transaction.load(id).state().position().committedAnchorId());
				autoPlay = new PhantomVisibleAutoPlay(materialization, () -> currentEngine, presence::isOnline);
				final int npcId = PhantomBackgroundGoalSpec.parse(nativeGoal).npcId();
				monster = new org.l2jmobius.gameserver.model.actor.instance.Monster(org.l2jmobius.gameserver.data.xml.NpcData.getInstance().getTemplate(npcId));
				monster.setInstanceId(player.getInstanceId());
				final var nativeSpawn = new org.l2jmobius.gameserver.model.spawns.Spawn(monster.getTemplate());
				nativeSpawn.setXYZ(player.getX() + 20, player.getY(), player.getZ());
				monster.setSpawn(nativeSpawn);
				monster.setCurrentHpMp(monster.getMaxHp(), monster.getMaxMp());
				monster.spawnMe(player.getX() + 20, player.getY(), player.getZ());
				PhantomAssertions.assertTrue(World.getInstance().getVisibleObjectsInRange(player, Creature.class, 100).contains(monster), "Native combat fixture NPC is absent before AutoPlay: player=" + player.getX() + "," + player.getY() + "," + player.getZ() + " npc=" + monster.getX() + "," + monster.getY() + "," + monster.getZ());
				final double hp = monster.getCurrentHp();
				PhantomAssertions.assertTrue(autoPlay.start(id, nativeGoal), "Ready identity did not enter native AutoPlay.");
				final long actionDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
				while ((monster.getCurrentHp() >= hp) && (System.nanoTime() < actionDeadline)) { Thread.sleep(20); }
				if (monster.getCurrentHp() >= hp)
				{
					context.record(evidencePrefix + ".actionFailure", "online=" + player.isOnline() + " dead=" + player.isDead() + " disabled=" + player.isDisabled() + " teleport=" + player.isTeleporting() + " moving=" + player.isMoving() + " auto=" + player.isAutoPlaying() + " target=" + player.getTarget() + " peace=" + player.isInsideZone(org.l2jmobius.gameserver.model.zone.ZoneId.PEACE) + " visible=" + World.getInstance().getVisibleObjectsInRange(player, Creature.class, 100).contains(monster) + " see=" + GeoEngine.getInstance().canSeeTarget(player, monster) + " move=" + GeoEngine.getInstance().canMoveToTarget(player.getX(), player.getY(), player.getZ(), monster.getX(), monster.getY(), monster.getZ(), player.getInstanceId()) + " npcInvul=" + monster.isInvul() + " npcName=" + monster.isShowName());
					context.record(evidencePrefix + ".actionThreads", Thread.getAllStackTraces().entrySet().stream().filter(entry -> java.util.Arrays.stream(entry.getValue()).anyMatch(frame -> frame.getClassName().endsWith("AutoPlayTaskManager$AutoPlay"))).map(entry -> entry.getKey().getName() + ": " + java.util.Arrays.stream(entry.getValue()).limit(8).toList()).limit(2).toList());
				}
				PhantomAssertions.assertTrue(monster.getCurrentHp() < hp, "Native AutoPlay did not perform a useful attack.");
				context.record(evidencePrefix + ".nativeIdentity", player.getObjectId());
				context.record(evidencePrefix + ".nativeAction", "AutoPlay target damage=" + (hp - monster.getCurrentHp()));
			}
			finally
			{
				boolean drained = false;
				PhantomMaterializationService.ShutdownResult shutdown = null;
				try
				{
					try { if (autoPlay != null) { autoPlay.stop(id); } }
					finally
					{
						try { if (nativeTravel != null) { nativeTravel.beforeMaterialize(id, profile.characterObjectId()); } }
						finally { if (nativeNavigation != null) { nativeNavigation.beginStop(); nativeNavigation.finishStop(); } }
					}
					if (monster != null) { monster.deleteMe(); }
					try (var action = materialization.tryAcquireAction(id).orElse(null))
					{
						if (action != null)
						{
							final var actor = action.player();
							actor.abortAttack(); actor.abortCast(); actor.stopMove(null); actor.setTarget(null);
							actor.getAI().setIntention(org.l2jmobius.gameserver.ai.Intention.IDLE);
							actor.getAI().setAutoAttacking(false);
							org.l2jmobius.gameserver.taskmanagers.AttackStanceTaskManager.getInstance().removeAttackStanceTask(actor);
							try
							{
								authority.capture(id, actor, goals.load(id).orElseThrow().goal(), transaction.load(id).state());
								context.record(evidencePrefix + ".teardownCapture", "supported");
							}
							catch (RuntimeException failure)
							{
								context.record(evidencePrefix + ".teardownCapture", failure.toString() + " combat=" + actor.isInCombat() + " position=" + actor.getX() + "," + actor.getY() + "," + actor.getZ());
							}
						}
					}
				}
				finally
				{
					try { if (engine != null) { engine.beginStop(); engine.finishStop(); } }
					finally
					{
						try
						{
							if (ecology != null) { ecology.beginStop(); final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5); while (!ecology.finishStop() && System.nanoTime() < deadline) { Thread.sleep(10); } drained = ecology.finishStop(); }
							else { drained = true; }
						}
						finally
						{
							try { shutdown = materialization.shutdown(); }
							finally
							{
								try { background.beginStop(); background.finishStop(); }
								finally { topology.beginStop(); topology.finishStop(); }
							}
						}
					}
				}
				PhantomAssertions.assertTrue(shutdown.failedProfileIds().isEmpty(), "Native gameplay fixture retained materialization after combat teardown: " + shutdown.failedProfileIds());
				PhantomAssertions.assertTrue(drained, "Replay commit retained shutdown ownership.");
				deleteProfile(profile);
			}
		}
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
		registry.add("03-production-town-recovery-remains-canonical", _ -> testProductionDeathRecovery());
		registry.add("04-recovery-preserves-preexisting-materialization", _ -> testPreexistingMaterializationRecovery());
		registry.add("05-normal-resurrection-cancels-town-return", _ -> testNormalResurrectionCancelsTownReturn());
		registry.add("06-native-death-timer-and-resurrection-cancellation", _ -> testNativeDeathTimer());
		registry.add("07-retired-autosave-cannot-overwrite-next-epoch", this::testRetiredAutoSaveEpoch);
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
			if (profile != null)
			{
				deleteProfile(profile);
			}
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

	private void testBackgroundStoreAbortRetry() throws Exception
	{
		final AtomicBoolean failOnce = new AtomicBoolean(true);
		final RuntimeFixture runtime = createRuntimeFixture(_environment.primary().objectId(), new PhantomBackgroundTransaction(), point ->
		{
			if ((point == FailurePoint.BEFORE_STORE_OPERATION) && failOnce.compareAndSet(true, false))
			{
				throw new InjectedFailure();
			}
		});
		try
		{
			PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, runtime.materialization().materialize(runtime.profileId()).status(), "Store-abort fixture did not materialize.");
			PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.CLEANUP_FAILED_RETAINED, runtime.materialization().dematerialize(runtime.profileId()).status(), "Injected store failure did not retain cleanup state.");
			PhantomAssertions.assertEquals(1, runtime.materialization().snapshot().retainedEntries(), "Injected store failure did not retain exactly one entry.");
			PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, runtime.materialization().retryCleanup(runtime.profileId()).status(), "Retry after store abort did not clean the retained entry.");
			PhantomAssertions.assertEquals(0, runtime.materialization().snapshot().retainedEntries(), "Retry after store abort retained the materialization entry.");
			PhantomAssertions.assertTrue(runtime.background().materializationQuiescence().ready(), "Retry after store abort leaked a background transition claim.");
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
		PhantomAssertions.assertEquals(23, audited.size(), "Deterministic FARMING anchor audit cardinality changed.");
		final String productionEvidence = audited.stream().filter(value -> value.startsWith(PRODUCTION_TARGET_NPC_ID + "@" + PRODUCTION_FARM_ANCHOR_ID + ":")).findFirst().orElseThrow(() -> new AssertionError("Shipped production farm pair is absent from the audit."));
		PhantomAssertions.assertTrue(productionEvidence.contains(":leaveOnGround=" + PRODUCTION_GROUND_LOSS_ITEM_IDS + ":"), "Shipped production ground-loss corpus changed.");
		PhantomAssertions.assertTrue(productionEvidence.contains(":autoAcquiredUnsupported=[]:supported=true"), "Shipped production pair is no longer fail-closed supported.");
		PhantomAssertions.assertEquals(List.of(PRODUCTION_TARGET_NPC_ID + "@" + PRODUCTION_FARM_ANCHOR_ID, "20545@population.farming.human-fighter.20545", "22228@population.farming.kamael.22228"), supported, "Exact supported production farm pair changed.");
		context.record("background.productionLootAudit", String.join("|", audited));
	}

	private void testCanonicalAnchorPolicy(PhantomTestContext context) throws Exception
	{
		final ProductionTravelSelection travel = productionTravelSelection();
		final L2jPhantomBackgroundAuthority authority = _production.authority();
		final Hashes hashes = authority.hashes();
		final Position departure = canonicalAnchorPosition(travel.departure(), 0);
		final Position arrival = canonicalAnchorPosition(travel.arrival(), 0);
		final int liveGeoZ = GeoEngine.getInstance().getHeight(travel.arrival().point().x(), travel.arrival().point().y(), travel.arrival().point().z());
		final long liveDelta = Math.abs((long) liveGeoZ - travel.arrival().point().z());
		final String positionMode = liveDelta == 0 ? "IDENTITY_DEGRADED" : "GEODATA_NORMALIZED";
		final PhantomTopologySnapshot topology = _production.topology().snapshot();
		final PhantomTopologySnapshot reloadedTopology = new PhantomTopologyLoader(Path.of("data/phantoms/topology"), _production.topologyBackend(), PhantomTopologyPolicy.productionDefaults()).load(topology.generation());
		PhantomAssertions.assertEquals(topology.canonicalHash(), reloadedTopology.canonicalHash(), "Corrected production topology hash is not deterministic across loader runs.");
		PhantomAssertions.assertFalse(PARENT_PRODUCTION_TOPOLOGY_HASH.equals(topology.canonicalHash()), "Corrected production topology hash did not change from the required parent.");
		PhantomAssertions.assertEquals(-4072, travel.departure().point().z(), "Production route anchor raw Z is not canonical.");
		PhantomAssertions.assertEquals(0, travel.departure().validationTolerance(), "Production route anchor tolerance changed.");
		PhantomAssertions.assertEquals(-4072, departure.z(), "Production route anchor did not remain fixed at canonical Z.");
		PhantomAssertions.assertEquals(-3061, travel.arrival().point().z(), "Production farming anchor lost its factual spawn Z.");
		PhantomAssertions.assertEquals(5, travel.arrival().validationTolerance(), "Production farming anchor tolerance is not the exact normalization delta.");
		PhantomAssertions.assertEquals(liveGeoZ, arrival.z(), "Production farming anchor canonical Z differs from the current GeoEngine result.");
		PhantomAssertions.assertTrue(liveDelta <= travel.arrival().validationTolerance(), "Current GeoEngine normalization exceeds the factual farming anchor tolerance.");
		PhantomAssertions.assertTrue(_production.topologyBackend().spawns(PRODUCTION_TARGET_NPC_ID, 4096).stream().anyMatch(spawn -> spawn.point().equals(travel.arrival().point())), "Production farming anchor no longer matches the factual NPC 22859 spawn.");
		PhantomAssertions.assertEquals("giran.route.north", travel.edge().fromAnchorId(), "Production background edge departure endpoint changed.");
		PhantomAssertions.assertEquals(PRODUCTION_FARM_ANCHOR_ID, travel.edge().toAnchorId(), "Production background edge arrival endpoint changed.");
		PhantomAssertions.assertEquals(900_000L, travel.edge().baseTravelMillis(), "Production background edge travel time changed.");
		PhantomAssertions.assertEquals(departure, canonicalAnchorPosition(travel.departure(), 0), "Canonical departure position must be deterministic.");
		PhantomAssertions.assertEquals(arrival, canonicalAnchorPosition(travel.arrival(), 0), "Canonical arrival position must be deterministic.");
		context.record("background.positionRawZ", travel.arrival().point().z());
		context.record("background.positionCanonicalZ", arrival.z());
		context.record("background.positionLiveDelta", liveDelta);
		context.record("background.positionTolerance", travel.arrival().validationTolerance());
		context.record("background.positionMode", positionMode);

		final PhantomTopologyAnchor exactToleranceAnchor = syntheticAnchor("test.anchor.tolerance", 100, 0, 5);
		final Optional<Position> exactTolerance = canonicalAnchorPosition(exactToleranceAnchor, 12345, _ -> 105);
		PhantomAssertions.assertEquals(Optional.of(new Position(0, exactToleranceAnchor.point().x(), exactToleranceAnchor.point().y(), 105, 12345, exactToleranceAnchor.id())), exactTolerance, "Normalization delta exactly equal to tolerance was rejected.");
		PhantomAssertions.assertTrue(canonicalAnchorPosition(exactToleranceAnchor, 0, _ -> 106).isEmpty(), "Normalization delta tolerance + 1 was admitted.");

		final AtomicInteger unstableCalls = new AtomicInteger();
		PhantomAssertions.assertTrue(canonicalAnchorPosition(exactToleranceAnchor, 0, _ -> unstableCalls.incrementAndGet() == 1 ? 105 : 106).isEmpty(), "Different first and second raw normalization results were admitted.");
		PhantomAssertions.assertEquals(2, unstableCalls.get(), "Unstable raw normalization did not fail at the second raw-height call.");

		final AtomicInteger nonFixedCalls = new AtomicInteger();
		PhantomAssertions.assertTrue(canonicalAnchorPosition(exactToleranceAnchor, 0, _ -> nonFixedCalls.incrementAndGet() <= 2 ? 105 : 104).isEmpty(), "A non-fixed-point normalized Z was admitted.");
		PhantomAssertions.assertEquals(3, nonFixedCalls.get(), "Fixed-point validation did not perform exactly one normalized-height call.");

		final AtomicInteger unsupportedInstanceCalls = new AtomicInteger();
		final PhantomTopologyAnchor unsupportedInstance = syntheticAnchor("test.anchor.instance", 100, 1, 5);
		PhantomAssertions.assertTrue(canonicalAnchorPosition(unsupportedInstance, 0, z ->
		{
			unsupportedInstanceCalls.incrementAndGet();
			return z;
		}).isEmpty(), "A non-zero instance anchor was admitted.");
		PhantomAssertions.assertEquals(0, unsupportedInstanceCalls.get(), "A non-zero instance anchor reached height normalization.");

		final PhantomGoal goal = goal(PRODUCTION_TARGET_NPC_ID, travel.arrival().id());
		final PhantomBackgroundGoalSpec spec = PhantomBackgroundGoalSpec.parse(goal);
		final PhantomBackgroundState initial = productionState(travel.departure(), hashes);
		final PhantomBackgroundAuthority.TravelAdvance partial = authority.advanceTravel(initial, spec, PhantomBackgroundService.FARM_TRAVEL_BUDGET_MILLIS);
		PhantomAssertions.assertEquals(PhantomBackgroundAuthority.TravelAdvance.Status.PARTIAL, partial.status(), "Partial canonical travel must succeed.");
		PhantomAssertions.assertEquals(departure, partial.position(), "Partial canonical travel must preserve the last committed position.");
		PhantomAssertions.assertTrue(partial.clock().residualTravelMillis() > 0, "Partial canonical travel must retain a residual budget.");

		final Position outsideTolerance = new Position(departure.instanceId(), departure.x(), departure.y(), departure.z() + travel.departure().validationTolerance() + 1, departure.heading(), departure.committedAnchorId());
		final PhantomBackgroundState outsideState = initial.after(initial.progress(), initial.vitals(), outsideTolerance, initial.inventory(), initial.autoGetSkills(), initial.clock(), initial.receipt());
		final PhantomBackgroundAuthority.TravelAdvance outside = authority.advanceTravel(outsideState, spec, PhantomBackgroundService.FARM_TRAVEL_BUDGET_MILLIS);
		PhantomAssertions.assertEquals(PhantomBackgroundAuthority.TravelAdvance.Status.ANCHOR_MISMATCH, outside.status(), "Position outside canonical tolerance must fail closed.");
		PhantomAssertions.assertFalse(outside.mutated(), "Position outside canonical tolerance must not mutate state.");
		PhantomAssertions.assertEquals(outsideState.position(), outside.position(), "Position outside canonical tolerance changed durable position.");
		PhantomAssertions.assertEquals(outsideState.clock(), outside.clock(), "Position outside canonical tolerance consumed travel time.");

		final Hashes staleHashes = new Hashes(hashes.knowledge(), hashes.topology() + "-stale", hashes.progression(), hashes.commerce());
		final PhantomBackgroundState staleTopologyState = new PhantomBackgroundState(initial.state(), initial.identity(), initial.progress(), initial.vitals(), initial.position(), initial.combat(), initial.loadout(), initial.inventory(), initial.autoGetSkills(), initial.clock(), initial.receipt(), staleHashes);
		final PhantomBackgroundAuthority.TravelAdvance staleTopology = authority.advanceTravel(staleTopologyState, spec, PhantomBackgroundService.FARM_TRAVEL_BUDGET_MILLIS);
		PhantomAssertions.assertEquals(PhantomBackgroundAuthority.TravelAdvance.Status.NO_ROUTE, staleTopology.status(), "Changed topology hash must fail closed.");
		PhantomAssertions.assertFalse(staleTopology.mutated(), "Changed topology hash must not mutate state.");
		PhantomAssertions.assertEquals(staleTopologyState.position(), staleTopology.position(), "Changed topology hash changed durable position.");
		PhantomAssertions.assertEquals(staleTopologyState.clock(), staleTopology.clock(), "Changed topology hash consumed travel time.");

		final PhantomBackgroundState finishing = initial.after(initial.progress(), initial.vitals(), initial.position(), initial.inventory(), initial.autoGetSkills(), new Clock(initial.clock().rngState(), 1, initial.clock().residualEncounterMillis()), initial.receipt());
		final PhantomBackgroundAuthority.TravelAdvance arrived = authority.advanceTravel(finishing, spec, 1);
		PhantomAssertions.assertEquals(PhantomBackgroundAuthority.TravelAdvance.Status.ARRIVED, arrived.status(), "Canonical travel completion must arrive.");
		PhantomAssertions.assertEquals(arrival, arrived.position(), "Canonical travel completion must commit the canonical position.");
		PhantomAssertions.assertEquals(liveGeoZ, arrived.position().z(), "ARRIVED did not persist the current GeoEngine canonical Z.");

		final Position outsideFarmPosition = new Position(0, arrival.x() + 10_000, arrival.y(), arrival.z(), 0, travel.arrival().id());
		final PhantomBackgroundState outsideFarmState = productionState(travel.arrival(), hashes).after(initial.progress(), initial.vitals(), outsideFarmPosition, initial.inventory(), initial.autoGetSkills(), initial.clock(), initial.receipt());
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> authority.farmInput(outsideFarmState, spec), "Farm position outside the native farming area must be rejected.");
		PhantomAssertions.assertEquals(outsideFarmPosition, outsideFarmState.position(), "Rejected farm position must remain unchanged.");
	}

	private void testNativeFarmAreaCapture() throws Exception
	{
		try (var fixture = openProductionPlayerFixture())
		{
			final var authority = _production.authority();
			final var player = fixture.player();
			final var baseline = authority.capture(PRODUCTION_LOOT_UNBLOCK_SEED, player, fixture.goal(), null);
			final var anchor = _production.topology().findAnchor(baseline.position().committedAnchorId()).orElseThrow();
			final var area = _production.topology().findNode(anchor.nodeId()).orElseThrow().area();
			final int movedX = baseline.position().x() + 64;
			final var point = new org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint(movedX, baseline.position().y(), baseline.position().z(), 0);
			PhantomAssertions.assertTrue(area.contains(point), "Focused movement point is outside its factual farming area.");
			player.setXYZInvisible(point.x(), point.y(), point.z());
			final var captured = authority.capture(PRODUCTION_LOOT_UNBLOCK_SEED, player, fixture.goal(), baseline);
			PhantomAssertions.assertEquals(movedX, captured.position().x(), "Native movement was snapped to the anchor.");
			PhantomAssertions.assertEquals(anchor.id(), captured.position().committedAnchorId(), "Movement inside the same native spawn area lost farm ownership.");
			PhantomAssertions.assertTrue(authority.matchesRuntime(player, captured), "Captured movement differs from the native Player.");
			final var spec = PhantomBackgroundGoalSpec.parse(fixture.goal());
			PhantomAssertions.assertTrue(authority.farmInput(captured, spec).target().npcId() == spec.npcId(), "Native movement stranded ordinary farming.");
			player.setXYZInvisible(baseline.position().x() + 10_000, baseline.position().y(), baseline.position().z());
			PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> authority.capture(PRODUCTION_LOOT_UNBLOCK_SEED, player, fixture.goal(), captured), "Unrelated world position retained the old farming area.");
		}
	}

	private void testVisibleNativeTravel(PhantomTestContext context) throws Exception
	{
		final var topology = _production.topology();
		if (_mode == Mode.NATIVE_LIFECYCLE) { PhantomM1GeometryChecks.measure(context, topology); }
		final var routeQuery = org.l2jmobius.gameserver.phantoms.background.PhantomNormalGatekeeperTravel.load(Path.of("data/phantoms/travel/high-five-normal-gk.xml"), topology);
		final var authority = new L2jPhantomBackgroundAuthority(_production::knowledge, _production::topology, _production::progression, _production::commerce, routeQuery);
		final var edge = topology.snapshot().edges().stream().filter(value -> value.backgroundEligible() && (value.fromAnchorId() != null) && (value.toAnchorId() != null)).filter(value ->
		{
			final var from = topology.findAnchor(value.fromAnchorId()).orElseThrow();
			final var to = topology.findAnchor(value.toAnchorId()).orElseThrow();
			final double distance = Math.hypot((long) from.point().x() - to.point().x(), (long) from.point().y() - to.point().y());
			return (from.role() == PhantomTopologyAnchorRole.ROUTE) && (to.role() == PhantomTopologyAnchorRole.FARMING) && (distance > 50) && (distance < 180);
		}).sorted(Comparator.comparing(PhantomTopologyEdge::id)).findFirst().orElseThrow();
		final var departure = topology.findAnchor(edge.fromAnchorId()).orElseThrow();
		final var arrival = topology.findAnchor(edge.toAnchorId()).orElseThrow();
		final int npcId = _production.knowledge().snapshot().spawnAreasByNpc().entrySet().stream().filter(value -> value.getValue().stream().anyMatch(area -> arrival.nodeId().equals(area.topologyNodeId()))).mapToInt(Map.Entry::getKey).sorted().findFirst().orElseThrow();
		ProductionPlayerFixture playerFixture = null;
		PhantomProfile profile = null;
		PhantomBackgroundService background = null;
		PhantomMaterializationService materialization = null;
		final var navigation = new PhantomNavigationService(new PhantomMetrics());
		try
		{
			playerFixture = openProductionPlayerFixture(departure);
			final int objectId = playerFixture.player().getObjectId();
			profile = _repository.create(objectId);
			final var goal = goal(npcId, arrival.id());
			final var goals = new PhantomGoalStateStore(_repository);
			goals.insert(profile.profileId(), goal);
			final var transaction = new PhantomBackgroundTransaction();
			final var materializationRef = new AtomicReference<PhantomMaterializationService>();
			background = new PhantomBackgroundService(_repository, goals, PhantomIdentityLeaseRegistry.getInstance(), transaction, authority, new PhantomBackgroundCompetitionRegistry(), noSignals(), materializationRef::get);
			PhantomAssertions.assertTrue(background.start(), "Visible travel background did not start.");
			final var metrics = new PhantomMetrics();
			materialization = new PhantomMaterializationService(_repository, PhantomIdentityLeaseRegistry.getInstance(), metrics, new PhantomDiagnosticTrace(false, 64, 16, metrics), 1, _ -> { }, background, 5_000, 10_000);
			PhantomAssertions.assertTrue(materialization.start(), "Visible travel materialization did not start.");
			materializationRef.set(materialization);
			final var captured = authority.capture(profile.profileId(), playerFixture.player(), goal, null);
			playerFixture.player().storeMe();
			PhantomAssertions.assertEquals(Status.SUCCESS, captureTestBaseline(transaction, captured, goal).status(), "Visible travel baseline failed.");
			playerFixture.releaseRuntime();
			PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, materialization.materialize(profile.profileId()).status(), "Visible travel did not materialize.");
			if (_mode == Mode.NATIVE_LIFECYCLE) { PhantomM1TravelChecks.run(context, profile.profileId(), _repository, materialization, background, routeQuery, goal, new PhantomHistoricalBackgroundPlanner(_production.knowledge(), topology, authority), noSignals()); }
			try (var action = materialization.tryAcquireAction(profile.profileId()).orElseThrow())
			{
				final Player phantom = action.player();
				final Player real = Player.load(_environment.observer().objectId());
				PhantomAssertions.assertTrue((real != null) && !real.hasHeadlessOutboundSession(), "REAL native party fixture is unavailable.");
				final var party = new org.l2jmobius.gameserver.model.groups.Party(real, org.l2jmobius.gameserver.model.groups.PartyDistributionType.FINDERS_KEEPERS);
				real.setParty(party);
				phantom.setParty(party);
				party.addPartyMember(phantom);
				try
				{
					final var facts = org.l2jmobius.gameserver.phantoms.activity.PhantomMaterializationRetentionPolicy.Facts.nativeFacts(phantom, false, false, 0);
					PhantomAssertions.assertTrue(facts.realParty(), "Native REAL party was not recognized outside locality.");
					final var retention = new org.l2jmobius.gameserver.phantoms.activity.PhantomMaterializationRetentionPolicy(_ -> facts, () -> 1, 60_000);
					final var port = new org.l2jmobius.gameserver.phantoms.activity.PhantomReconcileFirstActivityPort(new org.l2jmobius.gameserver.phantoms.activity.PhantomMaterializationServiceActivityPort(materialization));
					port.installRetention(id -> retention.observe(id).retained());
					PhantomAssertions.assertEquals(org.l2jmobius.gameserver.phantoms.activity.PhantomActivityMaterializationPort.Outcome.DEFERRED, port.dematerialize(profile.profileId()).outcome(), "Ordinary demotion broke the native REAL party.");
					PhantomAssertions.assertTrue(phantom.getParty() == party, "Retention changed canonical Party membership.");
				}
				finally
				{
					party.removePartyMember(phantom, org.l2jmobius.gameserver.model.groups.PartyMessageType.NONE);
					_environment.cleanupLoadedPlayer(real);
				}
				PhantomAssertions.assertFalse(org.l2jmobius.gameserver.phantoms.activity.PhantomMaterializationRetentionPolicy.Facts.nativeFacts(phantom, false, false, 0).realParty(), "Ended native membership kept a stale REAL_PARTY pin.");
			}
		PhantomAssertions.assertTrue(navigation.start(), "Visible travel navigation did not start.");
		final List<String> closeoutDefects = new ArrayList<>();
		final var stuckClock = new java.util.concurrent.atomic.AtomicLong(System.nanoTime());
		final var stuckNavigation = new PhantomNavigationService(org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPolicy.productionDefaults(), new org.l2jmobius.gameserver.phantoms.navigation.L2jNavigationBackend(), worker -> { worker.run(); return true; }, stuckClock::get, new PhantomMetrics());
		stuckNavigation.start();
		final var stuckFailure = new AtomicReference<org.l2jmobius.gameserver.phantoms.background.PhantomVisibleFarmTravel.Failure>();
		final var stuckTravel = new org.l2jmobius.gameserver.phantoms.background.PhantomVisibleFarmTravel(materialization, background, routeQuery, stuckNavigation, _ -> true, noSignals(), (_, failure) -> stuckFailure.set(failure), stuckClock::get);
		try
		{
			for (int attempt = 0; attempt < 3; attempt++)
			{
				stuckTravel.arrive(profile.profileId(), goal);
				try (var action = materialization.tryAcquireAction(profile.profileId()).orElseThrow()) { action.player().stopMove(null); }
				stuckClock.addAndGet(TimeUnit.MILLISECONDS.toNanos(stuckNavigation.policy().stuckWindowMillis() + 1));
				stuckTravel.arrive(profile.profileId(), goal);
				stuckClock.addAndGet(1_000_000_001L);
			}
			PhantomAssertions.assertTrue(stuckFailure.get() != null && stuckFailure.get().reason().equals("travel.native_progress_stuck"), "Three controlled STUCK did not reach exact route feedback: " + stuckTravel.reason(profile.profileId()));
			if (!stuckFailure.get().routeFailure()) { closeoutDefects.add("STUCK route feedback excluded from existing TTL replanner"); }
			stuckTravel.beforeMaterialize(profile.profileId(), objectId);
			final var busyFailure = new AtomicReference<org.l2jmobius.gameserver.phantoms.background.PhantomVisibleFarmTravel.Failure>();
			final var terminalHistory = new PhantomHistoricalBackgroundService(_repository, goals, new PhantomHistoricalBackgroundPlanner(_production.knowledge(), topology, authority), background, materialization);
			final var busyTravel = new org.l2jmobius.gameserver.phantoms.background.PhantomVisibleFarmTravel(materialization, background, routeQuery, stuckNavigation, _ -> true, noSignals(), (candidate, failure) ->
			{
				busyFailure.set(failure);
				org.l2jmobius.gameserver.phantoms.background.PhantomVisibleFarmFailureBinding.bind(terminalHistory).accept(candidate, failure);
			}, stuckClock::get);
			try
			{
				try (var action = materialization.tryAcquireAction(profile.profileId()).orElseThrow()) { action.player().setCastingNow(true); }
				busyTravel.arrive(profile.profileId(), goal);
				stuckClock.addAndGet(stuckNavigation.policy().maximumAttemptDurationNanos() + 1);
				busyTravel.arrive(profile.profileId(), goal);
				PhantomAssertions.assertTrue(busyFailure.get() != null, "Native busy did not terminate at the bounded journey deadline.");
				if (busyFailure.get().routeFailure()) { closeoutDefects.add("Native busy journey deadline falsely excludes healthy geometry"); }
				if (_mode == Mode.NATIVE_LIFECYCLE)
				{
					if (terminalHistory.replanVisibleFarmIfOutgrown(profile.profileId(), goal, null)) { closeoutDefects.add("T01 shared production binding discarded journey_deadline resolution"); }
					final long terminalSequence = busyTravel.lastFailure(profile.profileId()).sequence();
					for (int retry = 0; retry < 4; retry++)
					{
						stuckClock.addAndGet(stuckNavigation.policy().maximumAttemptDurationNanos() + 1);
						busyTravel.arrive(profile.profileId(), goal);
					}
					if (busyTravel.lastFailure(profile.profileId()).sequence() != terminalSequence) { closeoutDefects.add("T02/T03 same-revision terminal budget restarted with a fresh Journey"); }
				}
			}
			finally { try (var action = materialization.tryAcquireAction(profile.profileId()).orElseThrow()) { action.player().setCastingNow(false); } busyTravel.beforeMaterialize(profile.profileId(), objectId); }
		}
		finally { stuckTravel.beforeMaterialize(profile.profileId(), objectId); stuckNavigation.beginStop(); stuckNavigation.finishStop(); }
			final var travelHold = new java.util.concurrent.atomic.AtomicBoolean();
			final var failureSignals = new PhantomRelevanceSignalPort()
			{
				@Override
				public SignalDelivery submit(long id, org.l2jmobius.gameserver.phantoms.activity.PhantomRelevanceSignal signal)
				{
					travelHold.set(true);
					return SignalDelivery.ACCEPTED;
				}

				@Override
				public SignalDelivery withdraw(long id, String source, long sequence)
				{
					travelHold.set(false);
					return SignalDelivery.ACCEPTED;
				}
			};
			final var failedNavigation = new PhantomNavigationService(org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPolicy.productionDefaults(), new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationBackend()
			{
				@Override
				public CapabilitySnapshot capability(org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint from, org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint to)
				{
					return new CapabilitySnapshot(org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationCapability.GEODATA_DIRECT_ONLY, 1);
				}

				@Override
				public boolean canMoveDirect(org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint from, org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint to)
				{
					return false;
				}

				@Override
				public List<org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint> findPath(org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationRequest request, org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationCancellationToken cancellation)
				{
					return null;
				}
			}, worker -> { worker.run(); return true; }, System::nanoTime, new PhantomMetrics());
			failedNavigation.start();
			try
			{
				final var failedRoute = new AtomicReference<org.l2jmobius.gameserver.phantoms.background.PhantomVisibleFarmTravel.Failure>();
				final var failedTravel = new org.l2jmobius.gameserver.phantoms.background.PhantomVisibleFarmTravel(materialization, background, routeQuery, failedNavigation, _ -> true, failureSignals, (_, failure) -> failedRoute.set(failure), System::nanoTime);
				PhantomAssertions.assertFalse(failedTravel.arrive(profile.profileId(), goal), "Terminal navigation unexpectedly arrived.");
				PhantomAssertions.assertFalse(travelHold.get(), "Terminal navigation kept its own materialization presence.");
				PhantomAssertions.assertEquals("travel.navigation_pathfinding_disabled", failedTravel.reason(profile.profileId()), "Terminal native reason vanished before the census could observe it.");
				PhantomAssertions.assertEquals(0, failedNavigation.snapshot().activeRequests(), "Terminal navigation kept its owned request.");
				PhantomAssertions.assertTrue((failedRoute.get() != null) && (failedRoute.get().goal() == goal) && !failedRoute.get().stepId().isEmpty(), "Terminal travel did not publish its exact goal/route failure to alternate replanning.");
			}
			finally
			{
				failedNavigation.beginStop();
				failedNavigation.finishStop();
			}
			final var defaults = org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPolicy.productionDefaults();
			final var queuePolicy = new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPolicy(1, 1, defaults.maximumTrackedProfiles(), defaults.maximumCacheEntries(), defaults.cacheTtlMillis(), defaults.pathfindingCooldownMillis(), defaults.maximumLocalStraightDistance(), defaults.maximumWaypoints(), defaults.maximumRouteDistance(), defaults.defaultRequestDeadlineMillis(), defaults.stuckWindowMillis(), defaults.minimumProgress(), defaults.arrivalRadius(), defaults.maximumAttemptDurationMillis());
			final var workers = new java.util.ArrayDeque<Runnable>();
			final var queueNavigation = new PhantomNavigationService(queuePolicy, new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationBackend()
			{
				@Override public CapabilitySnapshot capability(org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint from, org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint to) { return new CapabilitySnapshot(org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationCapability.GEODATA_PATHFINDING, 1); }
				@Override public boolean canMoveDirect(org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint from, org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint to) { return false; }
				@Override public List<org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint> findPath(org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationRequest request, org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationCancellationToken cancellation) { return null; }
			}, worker -> { workers.add(worker); return true; }, System::nanoTime, new PhantomMetrics());
			queueNavigation.start();
			try
			{
				final long now = System.nanoTime();
				queueNavigation.submit(new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationRequest(Long.MAX_VALUE, new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint(0, 0, 0, 0), new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint(100, 0, 0, 0), now, now + 60_000_000_000L, 100_000));
				final var queueTravel = new org.l2jmobius.gameserver.phantoms.background.PhantomVisibleFarmTravel(materialization, background, routeQuery, queueNavigation, _ -> true, failureSignals);
				PhantomAssertions.assertFalse(queueTravel.arrive(profile.profileId(), goal), "Queue refusal unexpectedly arrived.");
				PhantomAssertions.assertEquals("travel.navigation_queue_backpressure", queueTravel.reason(profile.profileId()), "Consumer waited for a rejected nonzero request without a retained terminal result.");
				PhantomAssertions.assertFalse(travelHold.get(), "Queue refusal created a fake pending travel hold.");
				queueTravel.beforeMaterialize(profile.profileId(), objectId);
			}
			finally
			{
				queueNavigation.beginStop();
				while (!workers.isEmpty()) { workers.removeFirst().run(); }
				queueNavigation.finishStop();
			}
			final var permitted = new java.util.concurrent.atomic.AtomicBoolean(true);
			final var travel = new org.l2jmobius.gameserver.phantoms.background.PhantomVisibleFarmTravel(materialization, background, routeQuery, navigation, _ -> permitted.get(), noSignals());
			PhantomAssertions.assertFalse(travel.arrive(profile.profileId(), goal), "Visible travel arrived without native movement.");
			PhantomAssertions.assertEquals(departure.id(), transaction.load(profile.profileId()).state().position().committedAnchorId(), "Visible travel committed arrival before moving.");
			final long movementDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
			boolean moving = false;
			while (!moving && (System.nanoTime() < movementDeadline))
			{
				travel.arrive(profile.profileId(), goal);
				try (var action = materialization.tryAcquireAction(profile.profileId()).orElseThrow())
				{
					moving = action.player().isMoving();
				}
				if (!moving) { Thread.sleep(10); }
			}
			final Player routePlayer;
			final PlayerNativeWork.Owner routeOwner;
			final org.l2jmobius.gameserver.model.actor.PlayerNativeEvidence.Snapshot routePhase;
			try (var action = materialization.tryAcquireAction(profile.profileId()).orElseThrow())
			{
				PhantomAssertions.assertEquals(org.l2jmobius.gameserver.ai.Intention.MOVE_TO, action.player().getAI().getIntention(), "Visible travel did not enter native MOVE_TO: " + navigation.snapshot());
				final var owner = action.player().getNativeWorkOwner();
				PhantomAssertions.assertTrue(moving && owner != null && owner.isCurrent() && owner.player() == action.player(), "Route phase fixture lacks its actual moving native lifetime.");
				final var phase = owner.evidence().snapshot();
				context.record("T07.nativeRoutePhase", phase);
				PhantomAssertions.assertEquals(org.l2jmobius.gameserver.model.actor.PlayerNativeEvidence.Phase.ROUTE, phase.phase(), "Actual native MOVE_TO lacks bounded ROUTE evidence.");
				PhantomAssertions.assertTrue(!phase.overflow() && phase.objectId() == objectId && phase.epoch() == owner.epoch() && phase.phaseSinceNanos() >= owner.epoch() && phase.phaseDeadlineNanos() > phase.sampleNanos(), "Route phase does not attest exact live Player/epoch and remaining bound.");
				PhantomAssertions.assertEquals(defaults.maximumAttemptDurationNanos(), phase.phaseDeadlineNanos() - phase.phaseSinceNanos(), "Route phase changed original immutable attempt budget.");
				routePlayer = action.player(); routeOwner = owner; routePhase = phase;
			}
			final boolean repeatedArrival = travel.arrive(profile.profileId(), goal);
			try (var action = materialization.tryAcquireAction(profile.profileId()).orElseThrow())
			{
				PhantomAssertions.assertTrue(action.player() == routePlayer && action.player().getNativeWorkOwner() == routeOwner && routeOwner.isCurrent() && routeOwner.epoch() == routePhase.epoch(), "Route repeat changed the exact native lifetime.");
				final var repeated = routeOwner.evidence().snapshot();
				if (repeatedArrival) { PhantomAssertions.assertEquals(org.l2jmobius.gameserver.model.actor.PlayerNativeEvidence.Phase.NONE, repeated.phase(), "Real arrival retained route phase."); }
				else
				{
					PhantomAssertions.assertEquals(routePhase.phaseSinceNanos(), repeated.phaseSinceNanos(), "Retry renewed route episode start.");
					PhantomAssertions.assertEquals(routePhase.phaseDeadlineNanos(), repeated.phaseDeadlineNanos(), "Retry renewed route deadline.");
				}
			}
			permitted.set(false);
			PhantomAssertions.assertFalse(travel.arrive(profile.profileId(), goal), "Lost ordinary ownership retained travel.");
			try (var action = materialization.tryAcquireAction(profile.profileId()).orElseThrow())
			{
				PhantomAssertions.assertFalse(action.player().isMoving(), "Cancelled farm travel left native movement running.");
				final var cancelledPhase = action.player().getNativeWorkOwner().evidence().snapshot();
				PhantomAssertions.assertEquals(org.l2jmobius.gameserver.model.actor.PlayerNativeEvidence.Phase.NONE, cancelledPhase.phase(), "Cancelled route retained a bounded phase.");
				PhantomAssertions.assertTrue(cancelledPhase.phaseSinceNanos() == 0 && cancelledPhase.phaseDeadlineNanos() == 0 && !cancelledPhase.overflow(), "Cancelled route phase did not clear exact episode.");
			}
			permitted.set(true);
			try (var action = materialization.tryAcquireAction(profile.profileId()).orElseThrow()) { action.player().setWalking(); }
			final var nativeBackend = new org.l2jmobius.gameserver.phantoms.navigation.L2jNavigationBackend();
			final var asyncWorkers = new java.util.ArrayDeque<Runnable>();
			final var pathRequested = new java.util.concurrent.atomic.AtomicBoolean();
			final var travelClock = new java.util.concurrent.atomic.AtomicLong(System.nanoTime());
			final var asyncNavigation = new PhantomNavigationService(defaults, new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationBackend()
			{
				@Override public CapabilitySnapshot capability(org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint from, org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint to) { return nativeBackend.capability(from, to); }
				@Override public boolean canMoveDirect(org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint from, org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint to) { return pathRequested.get() && nativeBackend.canMoveDirect(from, to); }
				@Override public List<org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint> findPath(org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationRequest request, org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationCancellationToken cancellation)
				{
					pathRequested.set(true);
					// The controlled queue forces async handoff over the actual native pathfinder.
					return nativeBackend.findPath(request, cancellation);
				}
			}, worker -> { asyncWorkers.add(worker); return true; }, travelClock::get, new PhantomMetrics());
			asyncNavigation.start();
			final var asyncTravel = new org.l2jmobius.gameserver.phantoms.background.PhantomVisibleFarmTravel(materialization, background, routeQuery, asyncNavigation, _ -> permitted.get(), noSignals(), (_, _) -> {}, travelClock::get);
			PhantomAssertions.assertFalse(asyncTravel.arrive(profile.profileId(), goal), "Async consumer arrived before its worker.");
			PhantomAssertions.assertEquals("travel.navigation_pending", asyncTravel.reason(profile.profileId()), "Accepted async request was not pending.");
			while (!asyncWorkers.isEmpty()) { asyncWorkers.removeFirst().run(); }
			final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
			boolean arrived = false;
			boolean crossedMinute = false;
			while (!arrived && (System.nanoTime() < deadline))
			{
				if (!crossedMinute)
				{
					try (var action = materialization.tryAcquireAction(profile.profileId()).orElseThrow())
					{
						final var observed = asyncNavigation.progressTracker().find(profile.profileId()).orElse(null);
						final var current = new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint(action.player().getX(), action.player().getY(), action.player().getZ(), action.player().getInstanceId());
						if ((observed != null) && (Math.hypot((long)action.player().getX() - departure.point().x(), (long)action.player().getY() - departure.point().y()) >= defaults.minimumProgress()) && (((observed.bestDistance() - current.distanceTo(observed.destination())) >= defaults.minimumProgress()) || (current.distanceTo(observed.destination()) <= defaults.arrivalRadius())))
						{
							final var durableBefore = transaction.load(profile.profileId()).state().position();
							final var live = action.player().getLocation().clone();
							final var materialized = materialization.find(profile.profileId()).orElseThrow();
							PhantomAssertions.assertTrue(materialized.worldPresent() && (World.getInstance().findObject(materialized.characterObjectId()) == action.player()), "M1 native observer fixture lost its verified World Player.");
							final var choice = LocalPlayM1Observation.select(new PhantomTopologyPoint(durableBefore.x(), durableBefore.y(), durableBefore.z(), live.getInstanceId()), materialized.state(), true, new PhantomTopologyPoint(live.getX(), live.getY(), live.getZ(), live.getInstanceId()), false);
							PhantomAssertions.assertEquals(LocalPlayM1Observation.PositionSource.LIVE, choice.source(), "M1 observer ignored a moving native Player.");
							PhantomAssertions.assertEquals(live.getX(), choice.observed().x(), "M1 observer chose a stale committed X during native movement.");
							PhantomAssertions.assertFalse((durableBefore.x() == live.getX()) && (durableBefore.y() == live.getY()), "M1 native fixture did not separate live and committed positions.");
							PhantomAssertions.assertEquals(durableBefore, transaction.load(profile.profileId()).state().position(), "M1 observation wrote a live step into durable history.");
							travelClock.addAndGet(TimeUnit.SECONDS.toNanos(65));
							crossedMinute = true;
						}
					}
				}
				arrived = asyncTravel.arrive(profile.profileId(), goal);
				if (!arrived)
				{
					Thread.sleep(10);
				}
			}
			asyncNavigation.beginStop();
			asyncNavigation.finishStop();
			PhantomAssertions.assertTrue(arrived, "Native async consumer did not arrive: " + asyncTravel.reason(profile.profileId()) + " / " + asyncNavigation.snapshot());
			PhantomAssertions.assertTrue(crossedMinute, "Native displacement did not cross the controlled former minute deadline.");
			final var state = transaction.load(profile.profileId()).state();
			PhantomAssertions.assertEquals(arrival.id(), state.position().committedAnchorId(), "Native arrival did not commit its anchor.");
			try (var action = materialization.tryAcquireAction(profile.profileId()).orElseThrow())
			{
				PhantomAssertions.assertEquals(action.player().getX(), state.position().x(), "Arrival capture differs from native Player X.");
				PhantomAssertions.assertEquals(action.player().getY(), state.position().y(), "Arrival capture differs from native Player Y.");
			}
			context.record("m1.nativeVisibleTravel", departure.id() + "->" + arrival.id());
			try (var action = materialization.tryAcquireAction(profile.profileId()).orElseThrow())
			{
				final Player player = action.player();
				final var point = new Position(player.getInstanceId(), player.getX(), player.getY(), player.getZ(), player.getHeading(), arrival.id());
				player.setXYZInvisible(point.x() + 8, point.y(), point.z());
				PhantomAssertions.assertTrue(travel.arrive(profile.profileId(), goal), "Valid farmer movement inside its area was forced to exact XYZ.");
				player.setXYZInvisible(point.x() + 10000, point.y(), point.z());
				if (travel.arrive(profile.profileId(), goal)) { closeoutDefects.add("Old committed anchor admitted live Player outside its farm area"); }
				travel.beforeMaterialize(profile.profileId(), objectId);
				player.setXYZInvisible(point.x(), point.y(), point.z());
			}
			context.record("m1.closeoutTravelDefects", closeoutDefects);
			PhantomAssertions.assertEquals(List.of(), closeoutDefects, "Closeout native travel contracts failed.");
			final var planner = new PhantomHistoricalBackgroundPlanner(_production.knowledge(), topology, authority);
			PhantomAssertions.assertFalse(planner.remainsSuitable(state, goal), "Fixture must have a persisted target outgrown by its live Player.");
			final var generation = planner.generation();
			final var completed = new PhantomBackgroundCatchupState(PhantomBackgroundCatchupState.Status.COMPLETE, "v".repeat(64), context.seed(), 0, 1, 1, 0, 1, 1, generation.knowledgeGeneration(), generation.topologyGeneration(), goal.goalId(), goal.revision(), "a".repeat(64), PhantomBackgroundState.MODEL_VERSION, generation.authorityHashes(), "");
			final var catchupStore = new PhantomBackgroundCatchupStore(_repository, goals);
			catchupStore.claim(profile.profileId(), completed);
			final var visibleClock = new java.util.concurrent.atomic.AtomicLong(1);
			final var historical = new PhantomHistoricalBackgroundService(_repository, goals, planner, background, materialization, visibleClock::get);
			final var engineCandidates = new PhantomCandidateRegistry();
			engineCandidates.seal();
			final var engineHandlers = new PhantomStepHandlerRegistry();
			engineHandlers.seal();
			final var engine = new PhantomDecisionEngine(goals, engineCandidates, engineHandlers, new PhantomMetrics(), 1);
			engine.start();
			final var autoPlay = new PhantomVisibleAutoPlay(materialization, () -> engine, _ -> true, visibleClock::get);
			try
			{
				PhantomAssertions.assertEquals(PhantomDecisionEngine.AttachResult.ATTACHED, engine.attach(profile.profileId()), "Outgrown handoff did not attach its real goal store.");
				PhantomAssertions.assertTrue(autoPlay.start(profile.profileId(), goal), "Old visible goal did not enter native AutoPlay.");
				final Map<org.l2jmobius.gameserver.model.actor.Npc, Boolean> unavailableTargets = new java.util.HashMap<>();
				try (var action = materialization.tryAcquireAction(profile.profileId()).orElseThrow())
				{
					// Keep this absence check idle while advancing its clock, rather than racing native autobuffs.
					action.player().getAutoUseSettings().getAutoBuffs().clear();
					action.player().getAutoUseSettings().getAutoSkills().clear();
					action.player().getAutoUseSettings().getAutoActions().clear();
					action.player().getAutoUseSettings().getAutoSupplyItems().clear();
					action.player().getAutoUseSettings().setAutoPotionItem(0);
					action.player().abortCast();
					final int exactNpc = PhantomBackgroundGoalSpec.parse(goal).npcId();
					for (var npc : org.l2jmobius.gameserver.model.World.getInstance().getVisibleObjectsInRange(action.player(), org.l2jmobius.gameserver.model.actor.Npc.class, org.l2jmobius.gameserver.config.custom.AutoPlayConfig.AUTO_PLAY_LONG_RANGE))
					{
						if (npc.getId() == exactNpc)
						{
							unavailableTargets.put(npc, npc.isInvul());
							npc.setInvul(true);
						}
					}
					action.player().setTarget(null);
					action.player().abortAttack();
				}
				try
				{
					PhantomAssertions.assertFalse(autoPlay.noTargetExpired(profile.profileId(), goal), "No-target fallback fired before its bounded interval.");
					try (var action = materialization.tryAcquireAction(profile.profileId()).orElseThrow()) { action.player().setTarget(action.player()); action.player().setCastingNow(true); }
					visibleClock.addAndGet(29_000_000_000L);
					PhantomAssertions.assertFalse(autoPlay.noTargetExpired(profile.profileId(), goal), "No-target fallback fired before 30 seconds.");
					PhantomAssertions.assertTrue(autoPlay.running(profile.profileId(), goal), "Native tick stopped the session before absence could be measured.");
					PhantomAssertions.assertTrue(autoPlay.start(profile.profileId(), goal), "Repeated visible start lost native session.");
					visibleClock.addAndGet(2_000_000_000L);
					PhantomAssertions.assertFalse(autoPlay.noTargetExpired(profile.profileId(), goal), "First native absence recovery prematurely terminated at31s.");
					try (var action = materialization.tryAcquireAction(profile.profileId()).orElseThrow())
					{
						PhantomAssertions.assertEquals(org.l2jmobius.gameserver.ai.Intention.IDLE, action.player().getAI().getIntention(), "Missing-target31s recovery did not perform original native IDLE repair.");
						context.record("T06.absence31.native", action.player().getNativeWorkOwner().evidence().snapshot());
					}
					visibleClock.addAndGet(60_000_000_000L);
					PhantomAssertions.assertTrue(autoPlay.noTargetExpired(profile.profileId(), goal), "Unavailable exact NPCs kept unexplained IDLE beyond90s debt.");
					try (var action = materialization.tryAcquireAction(profile.profileId()).orElseThrow()) { context.record("T06.absence91.native", action.player().getNativeWorkOwner().evidence().snapshot()); }
				}
				finally
				{
					try (var action = materialization.tryAcquireAction(profile.profileId()).orElseThrow()) { action.player().setCastingNow(false); action.player().setTarget(null); }
					unavailableTargets.forEach((npc, invulnerable) -> npc.setInvul(invulnerable));
				}
				final var publicationEngine = new AtomicReference<>(new PhantomDecisionEngine(goals, engineCandidates, engineHandlers, new PhantomMetrics(), 1));
				final var decision = new PhantomBackgroundDecision(background, autoPlay::start, autoPlay::running, (id, currentGoal) -> historical.replanVisibleFarmIfOutgrown(id, currentGoal, publicationEngine.get()), autoPlay::stop);
				final var candidates = new PhantomCandidateRegistry();
				decision.registerCandidates(candidates);
				candidates.seal();
				final var handlers = new PhantomStepHandlerRegistry();
				decision.registerHandlers(handlers);
				handlers.seal();
				final var plan = candidates.snapshot().getFirst().planFactory().create(new PhantomPlanningContext(profile.profileId(), goal, PhantomCapabilitySet.empty(), PhantomActivityState.ACTIVE, 1, 1, 1, 1));
				final var await = plan.steps().get(1);
				final var start = plan.steps().getFirst();
				final var rejected = handlers.snapshot().get(start.actionKey()).execute(new PhantomStepContext(profile.profileId(), goal, plan, start, PhantomActivityState.ACTIVE, 1, 2, 2, 1, () -> false));
				PhantomAssertions.assertEquals(PhantomStepResult.Type.REPLAN, rejected.type(), "Rejected renewal allowed the unsuitable target to continue.");
				PhantomAssertions.assertEquals(goal.revision(), goals.load(profile.profileId()).orElseThrow().goal().revision(), "Rejected renewal changed durable goal revision.");
				PhantomAssertions.assertFalse(autoPlay.running(profile.profileId(), goal), "Rejected renewal left outgrown AutoPlay running.");
				publicationEngine.set(engine);
				PhantomAssertions.assertTrue(autoPlay.start(profile.profileId(), goal), "Retry fixture did not resume its old policy before renewal.");
				final var handoff = handlers.snapshot().get(await.actionKey()).execute(new PhantomStepContext(profile.profileId(), goal, plan, await, PhantomActivityState.ACTIVE, 1, 2, 2, 1, () -> false));
				PhantomAssertions.assertEquals(PhantomStepResult.Type.REPLAN, handoff.type(), "Outgrown live target did not leave the old AutoPlay plan.");
				final var renewed = goals.load(profile.profileId()).orElseThrow().goal();
				PhantomAssertions.assertEquals(goal.goalId(), renewed.goalId(), "Visible handoff replaced canonical goal identity.");
				PhantomAssertions.assertEquals(goal.revision() + 1, renewed.revision(), "Visible handoff did not persist the next goal revision.");
				PhantomAssertions.assertTrue(planner.remainsSuitable(state, renewed), "Persisted replacement is unsuitable for the native Player level.");
				PhantomAssertions.assertFalse(autoPlay.running(profile.profileId(), goal), "Outgrown exact-NPC policy remained running.");
				final var renewedCatchup = catchupStore.load(profile.profileId()).orElseThrow().state();
				PhantomAssertions.assertEquals(PhantomBackgroundCatchupState.Status.COMPLETE, renewedCatchup.status(), "Visible renewal reopened completed catch-up time.");
				PhantomAssertions.assertEquals(renewed.revision(), renewedCatchup.goalRevision(), "Visible goal and catch-up revision differ.");
				context.record("m1.durableVisibleHandoff", goal.revision() + "->" + renewed.revision());
				// Use the source NPC's level for alternate selection; this ingress has only one routed level-85 candidate.
				final int sceneLevel = _production.knowledge().findNpc(PhantomBackgroundGoalSpec.parse(goal).npcId()).orElseThrow().level();
				final PhantomBackgroundState alternateState;
				try (var action = materialization.tryAcquireAction(profile.profileId()).orElseThrow())
				{
					action.player().getStat().setLevel((byte) sceneLevel);
					action.player().getStat().setExp(ExperienceData.getInstance().getExpForLevel(sceneLevel));
					final var progress = new Progress(sceneLevel, action.player().getExp(), action.player().getSp(), action.player().getExpBeforeDeath());
					alternateState = new PhantomBackgroundState(state.state(), state.identity(), progress, state.vitals(), state.position(), state.combat(), state.loadout(), state.inventory(), state.autoGetSkills(), state.clock(), state.receipt(), state.hashes());
				}
				PhantomAssertions.assertFalse(historical.replanVisibleFarmIfOutgrown(profile.profileId(), renewed, engine), "Source-level native fixture did not renew to a suitable scene target.");
				final var failedGoal = goals.load(profile.profileId()).orElseThrow().goal();
				PhantomAssertions.assertTrue(planner.remainsSuitable(alternateState, failedGoal), "Failure fixture destination must otherwise be suitable.");
				historical.recordVisibleFailure(profile.profileId(), failedGoal, "");
				final var failedSpec = PhantomBackgroundGoalSpec.parse(failedGoal);
				final var alternatePlan = planner.replan(profile.profileId(), alternateState, failedGoal, context.seed(), 3, Set.of(failedSpec.npcId() + "@" + failedSpec.anchorId()), Set.of());
				PhantomAssertions.assertTrue(alternatePlan.ready(), "Native fixture has no suitable alternate: " + alternatePlan.reasonKey());
				PhantomAssertions.assertFalse(historical.replanVisibleFarmIfOutgrown(profile.profileId(), failedGoal, engine), "Failed suitable destination kept the same visible intention revision.");
				final var alternate = goals.load(profile.profileId()).orElseThrow().goal();
				PhantomAssertions.assertEquals(failedGoal.goalId(), alternate.goalId(), "Route/target feedback replaced the high-level farm intention.");
				PhantomAssertions.assertEquals(failedGoal.revision() + 1, alternate.revision(), "Route/target feedback did not publish alternate replan.");
				final var oldSpec = failedSpec;
				final var alternateSpec = PhantomBackgroundGoalSpec.parse(alternate);
				PhantomAssertions.assertFalse((oldSpec.npcId() == alternateSpec.npcId()) && oldSpec.anchorId().equals(alternateSpec.anchorId()), "Planner selected the failed destination during its exclusion TTL.");
				PhantomAssertions.assertTrue(planner.remainsSuitable(alternateState, alternate), "Alternate replan ignored existing suitability/reachability facts.");
				historical.recordVisibleFailure(profile.profileId(), alternate, "");
				visibleClock.addAndGet(120_000_000_000L);
				PhantomAssertions.assertTrue(historical.replanVisibleFarmIfOutgrown(profile.profileId(), alternate, engine), "Dynamic reachability exclusion became permanent.");
			}
			finally
			{
				autoPlay.stop(profile.profileId());
				engine.beginStop();
				engine.finishStop();
			}
		}
		finally
		{
			navigation.beginStop();
			navigation.finishStop();
			if (materialization != null) { materialization.shutdown(); }
			if (background != null) { background.beginStop(); background.finishStop(); }
			if (profile != null) { deleteProfile(profile); }
			if (playerFixture != null) { playerFixture.close(); }
		}
	}

	private void testProductionPositionTransition(PhantomTestContext context) throws Exception
	{
		final ProductionTravelSelection travel = productionTravelSelection();
		final Position expectedDeparture = canonicalAnchorPosition(travel.departure(), 0);
		final Position expectedArrival = canonicalAnchorPosition(travel.arrival(), 0);
		final long liveDelta = Math.abs((long) expectedArrival.z() - travel.arrival().point().z());
		if (liveDelta > 0)
		{
			testMalformedArrivalTransition(context, travel);
			context.record("background.positionMalformedTransition", "ANCHOR_MISMATCH");
		}
		else
		{
			final PhantomTopologyAnchor identityArrival = malformedArrivalTopology(travel).findAnchor(travel.arrival().id()).orElseThrow();
			PhantomAssertions.assertEquals(0, identityArrival.validationTolerance(), "Identity fallback fixture did not narrow the normalization tolerance.");
			PhantomAssertions.assertEquals(Optional.of(expectedArrival), L2jPhantomBackgroundAuthority.canonicalCommittedAnchorPosition(identityArrival, 0), "Zero-tolerance identity fallback did not remain a valid stable canonicalization.");
			context.record("background.positionMalformedTransition", "NOT_APPLICABLE_IDENTITY_FALLBACK");
		}
		ProductionPlayerFixture playerFixture = null;
		PhantomProfile profile = null;
		PhantomBackgroundService background = null;
		PhantomMaterializationService materialization = null;
		try
		{
			playerFixture = openProductionPlayerFixture(travel.departure());
			final int objectId = playerFixture.player().getObjectId();
			profile = _repository.create(objectId);
			final PhantomGoal goal = playerFixture.goal();
			final PhantomGoalStateStore goals = new PhantomGoalStateStore(_repository);
			goals.insert(profile.profileId(), goal);
			final PhantomBackgroundTransaction transaction = new PhantomBackgroundTransaction();
			final AtomicReference<PhantomMaterializationService> materializationRef = new AtomicReference<>();
			background = new PhantomBackgroundService(_repository, goals, PhantomIdentityLeaseRegistry.getInstance(), transaction, _production.authority(), new PhantomBackgroundCompetitionRegistry(), noSignals(), materializationRef::get);
			PhantomAssertions.assertTrue(background.start(), "Production position background service did not start.");
			final PhantomMetrics metrics = new PhantomMetrics();
			materialization = new PhantomMaterializationService(_repository, PhantomIdentityLeaseRegistry.getInstance(), metrics, new PhantomDiagnosticTrace(false, 64, 16, metrics), 1, point ->
			{
			}, background, 5_000, 10_000);
			PhantomAssertions.assertTrue(materialization.start(), "Production position materialization service did not start.");
			materializationRef.set(materialization);

			final PhantomBackgroundState firstCapture = _production.authority().capture(profile.profileId(), playerFixture.player(), goal, null);
			final PhantomBackgroundState seededPrevious = firstCapture.after(firstCapture.progress(), firstCapture.vitals(), firstCapture.position(), firstCapture.inventory(), firstCapture.autoGetSkills(), new Clock(context.seed(), 0, 0), firstCapture.receipt());
			final PhantomBackgroundState seededCapture = _production.authority().capture(profile.profileId(), playerFixture.player(), goal, seededPrevious);
			playerFixture.player().storeMe();
			PhantomAssertions.assertEquals(Status.SUCCESS, captureTestBaseline(transaction, seededCapture, goal).status(), "Seeded production position baseline capture failed.");
			PhantomAssertions.assertEquals(Status.SUCCESS, transaction.markMaterialized(profile.profileId(), objectId).status(), "Seeded production position baseline did not enter MATERIALIZED.");
			background.beforeStore(profile.profileId(), playerFixture.player());
			playerFixture.player().storeMe();
			background.afterStore(profile.profileId(), playerFixture.player());
			playerFixture.releaseRuntime();

			final PhantomBackgroundState ready = transaction.load(profile.profileId()).state();
			PhantomAssertions.assertEquals(State.READY, ready.state(), "Production position baseline did not dematerialize to READY.");
			PhantomAssertions.assertEquals(context.seed(), ready.clock().rngState(), "Production position baseline used the wrong deterministic seed.");
			PhantomAssertions.assertEquals(expectedDeparture, ready.position(), "Lifecycle baseline capture did not preserve the naturally loaded runtime position.");
			assertCharacterPosition(objectId, expectedDeparture);

			final PhantomBackgroundService.OperationResult partial = background.travel(profile.profileId(), goal, 1, 1, PhantomActivityState.BACKGROUND, 1);
			PhantomAssertions.assertEquals(OperationStatus.SUCCESS, partial.status(), "Real production partial travel did not commit.");
			final PhantomBackgroundState afterPartial = transaction.load(profile.profileId()).state();
			PhantomAssertions.assertEquals(expectedDeparture, afterPartial.position(), "Partial production travel changed the last committed position.");
			PhantomAssertions.assertTrue(afterPartial.clock().residualTravelMillis() > 0, "Partial production travel did not persist a residual budget.");
			assertCharacterPosition(objectId, expectedDeparture);
			PhantomAssertions.assertTrue(materialization.find(profile.profileId()).isEmpty(), "Partial production travel created a runtime Player.");

			PhantomBackgroundState arrived = afterPartial;
			long tickSequence = 2;
			while (!arrived.position().committedAnchorId().equals(travel.arrival().id()) && (tickSequence <= 32))
			{
				final PhantomBackgroundService.OperationResult step = background.travel(profile.profileId(), goal, 1, tickSequence, PhantomActivityState.BACKGROUND, tickSequence);
				PhantomAssertions.assertEquals(OperationStatus.SUCCESS, step.status(), "Real production travel continuation did not commit at tick " + tickSequence);
				arrived = transaction.load(profile.profileId()).state();
				tickSequence++;
			}
			PhantomAssertions.assertEquals(travel.arrival().id(), arrived.position().committedAnchorId(), "Real production travel did not arrive at the farm anchor.");
			PhantomAssertions.assertEquals(expectedArrival, arrived.position(), "ARRIVED transaction did not persist the canonical geodata position.");
			PhantomAssertions.assertEquals(expectedArrival.z(), arrived.position().z(), "ARRIVED transaction did not persist the current GeoEngine canonical Z.");
			assertCharacterPosition(objectId, expectedArrival);

			final PhantomBackgroundStateCodec codec = new PhantomBackgroundStateCodec();
			final byte[] arrivedBytes = codec.encode(arrived);
			final PhantomMaterializationService.MaterializeResult materialized = materialization.materialize(profile.profileId());
			PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, materialized.status(), "Canonical ARRIVED state did not materialize through the ordinary lifecycle.");
			try (var action = materialization.tryAcquireAction(profile.profileId()).orElseThrow())
			{
				assertProductionRuntimeMatches(action.player(), transaction.load(profile.profileId()).state());
				PhantomAssertions.assertEquals(expectedArrival.x(), action.player().getX(), "Materialized ARRIVED Player X differs.");
				PhantomAssertions.assertEquals(expectedArrival.y(), action.player().getY(), "Materialized ARRIVED Player Y differs.");
				PhantomAssertions.assertEquals(expectedArrival.z(), action.player().getZ(), "Materialized ARRIVED Player Z differs.");
				final Canonical stored = canonical(objectId);
				final Player nativePlayer = action.player();
				try
				{
					nativePlayer.setXYZInvisible(45975, 47879, -3488);
					nativePlayer.setHeading(12772);
					nativePlayer.setCurrentHp(Math.max(1, nativePlayer.getMaxHp() - 52));
					nativePlayer.setCurrentCp(Math.max(0, nativePlayer.getMaxCp() - 64));
					nativePlayer.autoSave();
					PhantomAssertions.assertEquals(stored, canonical(objectId), "Headless native autosave persisted HP/CP/position while MATERIALIZED projection still owned the canonical snapshot.");
				}
				finally
				{
					nativePlayer.setXYZInvisible(expectedArrival.x(), expectedArrival.y(), expectedArrival.z());
					nativePlayer.setHeading(stored.heading());
					nativePlayer.setCurrentHp(stored.currentHp());
					nativePlayer.setCurrentCp(stored.currentCp());
				}
			}
			PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, materialization.dematerialize(profile.profileId()).status(), "Canonical ARRIVED dematerialization failed.");
			final PhantomBackgroundState firstReload = transaction.load(profile.profileId()).state();
			PhantomAssertions.assertTrue(java.util.Arrays.equals(arrivedBytes, codec.encode(firstReload)), "Materialization/dematerialization changed the canonical ARRIVED state.");
			assertCharacterPosition(objectId, expectedArrival);

			materialization.shutdown();
			materialization = null;
			background.beginStop();
			PhantomAssertions.assertTrue(background.finishStop(), "First production position background service did not stop cleanly.");
			background = null;

			final PhantomBackgroundTransaction restartedTransaction = new PhantomBackgroundTransaction();
			final AtomicReference<PhantomMaterializationService> restartedMaterializationRef = new AtomicReference<>();
			background = new PhantomBackgroundService(_repository, goals, PhantomIdentityLeaseRegistry.getInstance(), restartedTransaction, _production.authority(), new PhantomBackgroundCompetitionRegistry(), noSignals(), restartedMaterializationRef::get);
			PhantomAssertions.assertTrue(background.start(), "Restarted production position background service did not start.");
			final PhantomMetrics restartedMetrics = new PhantomMetrics();
			materialization = new PhantomMaterializationService(_repository, PhantomIdentityLeaseRegistry.getInstance(), restartedMetrics, new PhantomDiagnosticTrace(false, 64, 16, restartedMetrics), 1, point ->
			{
			}, background, 5_000, 10_000);
			PhantomAssertions.assertTrue(materialization.start(), "Restarted production position materialization service did not start.");
			restartedMaterializationRef.set(materialization);
			PhantomAssertions.assertTrue(java.util.Arrays.equals(arrivedBytes, codec.encode(restartedTransaction.load(profile.profileId()).state())), "Restart/load changed the canonical ARRIVED state.");

			final PhantomMaterializationService.MaterializeResult restartedMaterialized = materialization.materialize(profile.profileId());
			PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, restartedMaterialized.status(), "Restarted canonical ARRIVED state did not materialize.");
			try (var action = materialization.tryAcquireAction(profile.profileId()).orElseThrow())
			{
				assertProductionRuntimeMatches(action.player(), restartedTransaction.load(profile.profileId()).state());
			}
			PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, materialization.dematerialize(profile.profileId()).status(), "Restarted canonical ARRIVED dematerialization failed.");
			PhantomAssertions.assertTrue(java.util.Arrays.equals(arrivedBytes, codec.encode(restartedTransaction.load(profile.profileId()).state())), "Restart materialization cycle changed the canonical ARRIVED state.");
			assertCharacterPosition(objectId, expectedArrival);
			context.record("background.positionCanonicalDeparture", expectedDeparture);
			context.record("background.positionCanonicalArrival", expectedArrival);
			context.record("background.positionTravelTicks", tickSequence - 1);
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

	private void testMalformedArrivalTransition(PhantomTestContext context, ProductionTravelSelection travel) throws Exception
	{
		final PhantomTopologyQuery malformedTopology = malformedArrivalTopology(travel);
		final L2jPhantomBackgroundAuthority malformedAuthority = _production.authority(malformedTopology);
		final PhantomTopologyAnchor departure = malformedTopology.findAnchor(travel.departure().id()).orElseThrow();
		final PhantomTopologyAnchor malformedArrival = malformedTopology.findAnchor(travel.arrival().id()).orElseThrow();
		PhantomAssertions.assertEquals(0, malformedArrival.validationTolerance(), "Malformed arrival fixture did not narrow the normalization tolerance.");
		PhantomAssertions.assertTrue(L2jPhantomBackgroundAuthority.canonicalCommittedAnchorPosition(malformedArrival, 0).isEmpty(), "Malformed arrival fixture unexpectedly canonicalized.");

		ProductionPlayerFixture playerFixture = null;
		PhantomProfile profile = null;
		PhantomBackgroundService background = null;
		try
		{
			playerFixture = openProductionPlayerFixture(departure);
			final int objectId = playerFixture.player().getObjectId();
			profile = _repository.create(objectId);
			final PhantomGoal goal = playerFixture.goal();
			final PhantomGoalStateStore goals = new PhantomGoalStateStore(_repository);
			goals.insert(profile.profileId(), goal);
			final AtomicInteger transactionMutations = new AtomicInteger();
			final PhantomBackgroundTransaction transaction = new PhantomBackgroundTransaction(DatabaseFactory::getConnection, allocator(new AtomicInteger()), _ -> transactionMutations.incrementAndGet());
			background = new PhantomBackgroundService(_repository, goals, PhantomIdentityLeaseRegistry.getInstance(), transaction, malformedAuthority, new PhantomBackgroundCompetitionRegistry(), noSignals(), () -> null);
			PhantomAssertions.assertTrue(background.start(), "Malformed-arrival background service did not start.");

			final PhantomBackgroundState firstCapture = malformedAuthority.capture(profile.profileId(), playerFixture.player(), goal, null);
			final PhantomBackgroundState seededPrevious = firstCapture.after(firstCapture.progress(), firstCapture.vitals(), firstCapture.position(), firstCapture.inventory(), firstCapture.autoGetSkills(), new Clock(context.seed(), 0, 0), firstCapture.receipt());
			final PhantomBackgroundState seededCapture = malformedAuthority.capture(profile.profileId(), playerFixture.player(), goal, seededPrevious);
			playerFixture.player().storeMe();
			PhantomAssertions.assertEquals(Status.SUCCESS, captureTestBaseline(transaction, seededCapture, goal).status(), "Malformed-arrival baseline capture failed.");
			PhantomAssertions.assertEquals(Status.SUCCESS, transaction.markMaterialized(profile.profileId(), objectId).status(), "Malformed-arrival baseline did not enter MATERIALIZED.");
			background.beforeStore(profile.profileId(), playerFixture.player());
			playerFixture.player().storeMe();
			background.afterStore(profile.profileId(), playerFixture.player());
			playerFixture.releaseRuntime();

			final PhantomBackgroundState before = transaction.load(profile.profileId()).state();
			final PhantomBackgroundStateCodec codec = new PhantomBackgroundStateCodec();
			final byte[] beforeBytes = codec.encode(before);
			final Canonical beforeCanonical = canonical(objectId);
			final PhantomBackgroundAuthority.TravelAdvance rejected = malformedAuthority.advanceTravel(before, PhantomBackgroundGoalSpec.parse(goal), PhantomBackgroundService.FARM_TRAVEL_BUDGET_MILLIS);
			PhantomAssertions.assertEquals(PhantomBackgroundAuthority.TravelAdvance.Status.ANCHOR_MISMATCH, rejected.status(), "Malformed arrival normalization did not return ANCHOR_MISMATCH.");
			PhantomAssertions.assertFalse(rejected.mutated(), "Malformed arrival normalization mutated the travel result.");
			PhantomAssertions.assertEquals(before.position(), rejected.position(), "Malformed arrival normalization changed position.");
			PhantomAssertions.assertEquals(before.clock(), rejected.clock(), "Malformed arrival normalization consumed clock.");

			transactionMutations.set(0);
			final PhantomBackgroundService.OperationResult serviceRejected = background.travel(profile.profileId(), goal, 1, 1, PhantomActivityState.BACKGROUND, 1);
			PhantomAssertions.assertEquals(OperationStatus.REPLAN, serviceRejected.status(), "Malformed arrival service result was not a typed replan.");
			PhantomAssertions.assertEquals("travel.anchor_mismatch", serviceRejected.reason(), "Malformed arrival service reason changed.");
			PhantomAssertions.assertEquals(0, transactionMutations.get(), "Malformed arrival invoked the background mutation transaction.");
			final PhantomBackgroundState after = transaction.load(profile.profileId()).state();
			PhantomAssertions.assertTrue(java.util.Arrays.equals(beforeBytes, codec.encode(after)), "Malformed arrival changed canonical background state.");
			PhantomAssertions.assertEquals(before.position(), after.position(), "Malformed arrival changed durable position.");
			PhantomAssertions.assertEquals(before.clock(), after.clock(), "Malformed arrival changed durable clock.");
			PhantomAssertions.assertEquals(beforeCanonical, canonical(objectId), "Malformed arrival changed canonical DB position.");
			assertCharacterPosition(objectId, before.position());
		}
		finally
		{
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
			}, background, 5_000, 10_000);
			PhantomAssertions.assertTrue(materialization.start(), "Production materialization service did not start.");
			materializationRef.set(materialization);

			final PhantomBackgroundState initialCapture = _production.authority().capture(profile.profileId(), playerFixture.player(), goal, null);
			playerFixture.player().storeMe();
			final PhantomBackgroundState zeroResidual = initialCapture.after(initialCapture.progress(), initialCapture.vitals(), initialCapture.position(), initialCapture.inventory(), initialCapture.autoGetSkills(), new Clock(context.seed(), 0, 0), initialCapture.receipt()).withState(State.READY);
			final PhantomBackgroundAuthority.FarmInput zeroResidualInput = _production.authority().farmInput(zeroResidual, PhantomBackgroundGoalSpec.parse(goal));
			final long residualEncounterMillis = largestSuccessfulResidual(zeroResidual, zeroResidualInput);
			final PhantomBackgroundState seededPrevious = initialCapture.after(initialCapture.progress(), initialCapture.vitals(), initialCapture.position(), initialCapture.inventory(), initialCapture.autoGetSkills(), new Clock(context.seed(), 0, residualEncounterMillis), initialCapture.receipt());
			final PhantomBackgroundState seededCapture = _production.authority().capture(profile.profileId(), playerFixture.player(), goal, seededPrevious);
			PhantomAssertions.assertEquals(Status.SUCCESS, captureTestBaseline(transaction, seededCapture, goal).status(), "Seeded real production baseline capture failed.");
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

	private void testOrdinarySpoil()
	{
		final Drop death = new Drop(57, -1, 0, 100, 100, 1, 1, 1, null, 1, 100, true, 0, DropDisposition.ACQUIRE, DropOrigin.ORDINARY);
		final Drop spoil = new Drop(10, -1, 0, 100, 100, 2, 2, 1, null, 1, 100, true, 0, DropDisposition.ACQUIRE, DropOrigin.ORDINARY_SPOIL);
		final PhantomBackgroundState state = state(1, 101, State.READY, 100, 100, new InventoryFacts(List.of(10, 57), List.of(), "model", 0, 100000, 0, 100));
		final Target both = target(1, 1, 0, List.of(death, spoil));
		final PhantomBackgroundModel model = new PhantomBackgroundModel();
		final BatchResult first = model.evaluate(request(state, both));
		final BatchResult replay = model.evaluate(request(state, both));
		final BatchResult deathOnly = model.evaluate(request(state, target(1, 1, 0, List.of(death))));
		PhantomAssertions.assertEquals(first, replay, "Ordinary spoil replay changed deterministic RNG output.");
		PhantomAssertions.assertTrue(first.encounters() > 0, "Ordinary spoil fixture did not encounter a monster.");
		PhantomAssertions.assertTrue(first.inventoryDelta().itemDeltas().getOrDefault(10, 0L) > 0, "Capable ordinary farm omitted spoil.");
		PhantomAssertions.assertEquals(deathOnly.inventoryDelta().itemDeltas().get(57), first.inventoryDelta().itemDeltas().get(57), "Ordinary spoil duplicated a death drop.");
		PhantomAssertions.assertFalse(deathOnly.inventoryDelta().itemDeltas().containsKey(10), "Death-only farm acquired spoil.");
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
		PhantomAssertions.assertTrue(objectRejected.encounters() > 0, "Ordinary object overflow permanently fenced the first encounter.");
		PhantomAssertions.assertEquals(16, objectRejected.inventoryDelta().itemDeltas().size(), "Ordinary overflow did not keep the bounded prefix.");
		PhantomAssertions.assertEquals((long) objectRejected.encounters(), objectRejected.groundLosses().get(116), "The excess ordinary item was not left on the ground.");
		PhantomAssertions.assertEquals(objectRejected, new PhantomBackgroundModel().evaluate(request(state, target(1, 1, 0, tooMany))), "Overflow partition changed deterministic replay.");
		final Drop manyObjects = new Drop(10, -1, 0, 100, 100, 12, 12, 1, null, 1, 100, false, 0);
		final BatchRequest ordinary = request(state, target(1, 1, 0, List.of(manyObjects)));
		final BatchResult spilled = new PhantomBackgroundModel().evaluate(ordinary);
		PhantomAssertions.assertEquals(8L, spilled.inventoryDelta().itemDeltas().get(10), "Ordinary non-stackable overflow exceeded or discarded the collectible prefix.");
		PhantomAssertions.assertEquals((12L * spilled.encounters()) - 8, spilled.groundLosses().get(10), "Non-stackable overflow did not conserve loot.");
		final BatchRequest acquisition = new BatchRequest(state, ordinary.target(), ordinary.rewardPolicy(), ordinary.deathPolicy(), ordinary.experienceTable(), ordinary.levelForExperience(), false, BatchMode.ACQUISITION_DEATH_DROP, 10, 12, true);
		PhantomAssertions.assertTrue(new PhantomBackgroundModel().evaluate(acquisition).indivisibleObjectCap(), "Acquisition overflow lost its strict transaction contract.");
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

	private void testMixedRateOccurrence031()
	{
		// Stock NpcTemplate guards randomDrops.isEmpty() when custom-rate awards use the occurrence budget without joining that list.
		final Drop custom = new Drop(57, 0, 0, 100, 25, 1, 1, 2, null, 1, 100, true, 0);
		final Drop unity = new Drop(4037, 1, 0, 100, 99, 1, 1, 1, null, 1, 100, true, 0);
		final Target target = singleEncounterTarget(List.of(custom, unity));
		final PhantomBackgroundModel model = new PhantomBackgroundModel();
		boolean both = false;
		for (long seed=1; seed<=256; seed++)
		{
			final var base=state(1, 101, State.READY, 100, 100, inventory());
			final var seeded=base.after(base.progress(),base.vitals(),base.position(),base.inventory(),base.autoGetSkills(),new Clock(seed,0,0),base.receipt());
			final var result=model.evaluate(request(seeded,target));
			PhantomAssertions.assertEquals(result,model.evaluate(request(seeded,target)),"Mixed-rate deterministic replay changed.");
			both |= result.inventoryDelta().itemDeltas().containsKey(custom.itemId()) && result.inventoryDelta().itemDeltas().containsKey(unity.itemId());
		}
		PhantomAssertions.assertTrue(both,"Stock custom-rate/unity reset branch was not exercised.");
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

	private void testProfile13ShapedMaterializedMismatch() throws Exception
	{
		final int objectId = _environment.primary().objectId();
		final Canonical original = canonical(objectId);
		final int originalBaseClass = (int) scalarLong("SELECT base_class FROM characters WHERE charId = ?", objectId);
		try
		{
			try (Connection connection = DatabaseFactory.getConnection();
				PreparedStatement statement = connection.prepareStatement("UPDATE characters SET curHp=128,maxHp=180,curMp=67,maxMp=67,curCp=8,maxCp=72,x=44126,y=42751,z=-3488,heading=25847 WHERE charId=?"))
			{
				statement.setInt(1, objectId);
				PhantomAssertions.assertEquals(1, statement.executeUpdate(), "Profile13-shaped TEST baseline was not installed.");
			}
			try (Fixture fixture = createFixture(objectId, null))
			{
				PhantomAssertions.assertEquals(Status.SUCCESS, fixture.transaction().markMaterialized(fixture.profileId(), objectId).status(), "Profile13-shaped TEST did not enter MATERIALIZED.");
				final Result interrupted = new PhantomBackgroundTransaction().abortMaterialization(fixture.profileId(), objectId);
				PhantomAssertions.assertEquals(Status.SUCCESS, interrupted.status(), "Matching MATERIALIZED projection did not recover after an interrupted runtime.");
				PhantomAssertions.assertEquals(State.READY, interrupted.state().state(), "Matching interrupted runtime did not recover to READY.");
				PhantomAssertions.assertEquals(128.0, canonical(objectId).currentHp(), "Matching restart recovery changed canonical HP.");
				PhantomAssertions.assertEquals(Status.SUCCESS, fixture.transaction().markMaterialized(fixture.profileId(), objectId).status(), "Recovered TEST did not re-enter MATERIALIZED.");
				try (Connection connection = DatabaseFactory.getConnection();
					PreparedStatement statement = connection.prepareStatement("UPDATE characters SET curHp=180,curCp=72,x=45975,y=47879,heading=12772 WHERE charId=?"))
				{
					statement.setInt(1, objectId);
					PhantomAssertions.assertEquals(1, statement.executeUpdate(), "Profile13-shaped TEST canonical delta was not installed.");
				}
				final Result recovered = new PhantomBackgroundTransaction().abortMaterialization(fixture.profileId(), objectId);
				PhantomAssertions.assertEquals(Status.INCONSISTENT, recovered.status(), "Exact profile13 HP/CP/position mismatch did not fail closed.");
				PhantomAssertions.assertEquals(State.INCONSISTENT, recovered.state().state(), "Profile13-shaped mismatch changed the wrong background state.");
				PhantomAssertions.assertEquals(128.0, recovered.state().vitals().currentHp(), "Profile13-shaped expected HP was lost.");
				PhantomAssertions.assertEquals(8.0, recovered.state().vitals().currentCp(), "Profile13-shaped expected CP was lost.");
				PhantomAssertions.assertEquals(new Position(0, 44126, 42751, -3488, 25847, ANCHOR_ID), recovered.state().position(), "Profile13-shaped expected position was lost.");
			}
		}
		finally
		{
			restoreCharacter(objectId, original, originalBaseClass);
		}
	}

	private void testAttestedLegacyHeadlessRecovery() throws Exception
	{
		final int objectId = _environment.primary().objectId();
		final Canonical original = canonical(objectId);
		final int originalBaseClass = (int) scalarLong("SELECT base_class FROM characters WHERE charId = ?", objectId);
		try
		{
			try (Connection connection = DatabaseFactory.getConnection();
				PreparedStatement statement = connection.prepareStatement("UPDATE characters SET curHp=128,maxHp=180,curMp=67,maxMp=67,curCp=8,maxCp=72,x=44126,y=42751,z=-3488,heading=25847 WHERE charId=?"))
			{
				statement.setInt(1, objectId);
				PhantomAssertions.assertEquals(1, statement.executeUpdate(), "Legacy TEST baseline was not installed.");
			}
			try (Fixture fixture = createFixture(objectId, null))
			{
				final PhantomBackgroundTransaction transaction = new PhantomBackgroundTransaction();
				PhantomAssertions.assertEquals(Status.SUCCESS, transaction.markMaterialized(fixture.profileId(), objectId).status(), "Legacy TEST did not enter MATERIALIZED.");
				try (Connection connection = DatabaseFactory.getConnection();
					PreparedStatement statement = connection.prepareStatement("UPDATE characters SET curHp=180,curCp=72,x=45975,y=47879,heading=12772 WHERE charId=?"))
				{
					statement.setInt(1, objectId);
					PhantomAssertions.assertEquals(1, statement.executeUpdate(), "Legacy TEST canonical drift was not installed.");
				}
				PhantomAssertions.assertEquals(Status.INCONSISTENT, transaction.abortMaterialization(fixture.profileId(), objectId).status(), "Legacy TEST did not fail closed first.");
				final Canonical drifted = canonical(objectId);
				final PhantomBackgroundTransaction.LegacyHeadlessWitness witness;
				try (Connection connection = DatabaseFactory.getConnection();
					PreparedStatement statement = connection.prepareStatement("SELECT row_version,payload FROM phantom_profile_components WHERE profile_id=? AND component_type='background.state'"))
				{
					statement.setLong(1, fixture.profileId());
					try (ResultSet result = statement.executeQuery())
					{
						PhantomAssertions.assertTrue(result.next(), "Legacy TEST component disappeared.");
						witness = new PhantomBackgroundTransaction.LegacyHeadlessWitness(fixture.profileId(), objectId, result.getLong("row_version"), HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(result.getBytes("payload"))), drifted.currentHp(), drifted.currentMp(), drifted.currentCp(), drifted.x(), drifted.y(), drifted.z(), drifted.heading());
					}
				}
				final var stale = new PhantomBackgroundTransaction.LegacyHeadlessWitness(witness.profileId(), objectId, witness.rowVersion() + 1, witness.backgroundPayloadSha256(), witness.canonicalHp(), witness.canonicalMp(), witness.canonicalCp(), witness.canonicalX(), witness.canonicalY(), witness.canonicalZ(), witness.canonicalHeading());
				PhantomAssertions.assertTrue(!transaction.recoverAttestedLegacyHeadlessDrift(stale).successful(), "Stale legacy witness recovered a profile.");
				try (Connection connection = DatabaseFactory.getConnection();
					PreparedStatement statement = connection.prepareStatement("UPDATE characters SET exp=exp+1 WHERE charId=?"))
				{
					statement.setInt(1, objectId);
					statement.executeUpdate();
				}
				PhantomAssertions.assertEquals(Status.CANONICAL_MISMATCH, transaction.recoverAttestedLegacyHeadlessDrift(witness).status(), "Mixed progress mismatch was silently healed.");
				try (Connection connection = DatabaseFactory.getConnection();
					PreparedStatement statement = connection.prepareStatement("UPDATE characters SET exp=exp-1 WHERE charId=?"))
				{
					statement.setInt(1, objectId);
					statement.executeUpdate();
				}
				final PhantomBackgroundTransaction interrupted = new PhantomBackgroundTransaction(DatabaseFactory::getConnection, allocator(new AtomicInteger()), point ->
				{
					if (point == FaultPoint.AFTER_CANONICAL_WRITES)
					{
						throw new InjectedFailure();
					}
				});
				PhantomAssertions.assertTrue(!interrupted.recoverAttestedLegacyHeadlessDrift(witness).successful(), "Interrupted legacy repair unexpectedly committed.");
				PhantomAssertions.assertEquals(drifted, canonical(objectId), "Interrupted legacy repair changed canonical character.");
				PhantomAssertions.assertEquals(State.INCONSISTENT, transaction.load(fixture.profileId()).state().state(), "Interrupted legacy repair changed background state.");
				final Result recovered = transaction.recoverAttestedLegacyHeadlessDrift(witness);
				PhantomAssertions.assertEquals(Status.SUCCESS, recovered.status(), "Attested legacy drift did not recover.");
				PhantomAssertions.assertEquals(State.READY, recovered.state().state(), "Attested legacy drift did not become READY.");
				PhantomAssertions.assertEquals(128.0, canonical(objectId).currentHp(), "Recovery did not restore the owned HP projection.");
				PhantomAssertions.assertEquals(8.0, canonical(objectId).currentCp(), "Recovery did not restore the owned CP projection.");
				PhantomAssertions.assertEquals(44126, canonical(objectId).x(), "Recovery did not restore the owned position.");
				PhantomAssertions.assertTrue(!transaction.recoverAttestedLegacyHeadlessDrift(witness).successful(), "Consumed legacy witness was reused.");
				PhantomAssertions.assertEquals(Status.SUCCESS, transaction.markMaterialized(fixture.profileId(), objectId).status(), "Recovered legacy profile cannot materialize normally.");
				PhantomAssertions.assertEquals(Status.SUCCESS, transaction.abortMaterialization(fixture.profileId(), objectId).status(), "Recovered legacy profile cannot complete owned restart recovery.");
			}
		}
		finally
		{
			restoreCharacter(objectId, original, originalBaseClass);
		}
	}

	private void testAttestedLatentMaterializedRecovery() throws Exception
	{
		final int objectId = _environment.primary().objectId();
		final Canonical original = canonical(objectId);
		final int originalBaseClass = (int) scalarLong("SELECT base_class FROM characters WHERE charId = ?", objectId);
		try
		{
			try (Connection connection = DatabaseFactory.getConnection();
				PreparedStatement statement = connection.prepareStatement("UPDATE characters SET curHp=128,maxHp=180,curMp=67,maxMp=67,curCp=8,maxCp=72,x=44126,y=42751,z=-3488,heading=25847 WHERE charId=?"))
			{
				statement.setInt(1, objectId);
				PhantomAssertions.assertEquals(1, statement.executeUpdate(), "Latent TEST baseline was not installed.");
			}
			try (Fixture fixture = createFixture(objectId, null))
			{
				final PhantomBackgroundTransaction transaction = new PhantomBackgroundTransaction();
				for (int phase = 0; phase < 2; phase++)
				{
					PhantomAssertions.assertEquals(Status.SUCCESS, transaction.markMaterialized(fixture.profileId(), objectId).status(), "Latent TEST did not enter MATERIALIZED.");
					final long version;
					final byte[] payload;
					try (Connection connection = DatabaseFactory.getConnection();
						PreparedStatement statement = connection.prepareStatement("SELECT row_version,payload FROM phantom_profile_components WHERE profile_id=? AND component_type='background.state'"))
					{
						statement.setLong(1, fixture.profileId());
						try (ResultSet result = statement.executeQuery())
						{
							PhantomAssertions.assertTrue(result.next(), "Latent TEST component disappeared.");
							version = result.getLong("row_version");
							payload = result.getBytes("payload");
						}
					}
					final byte[] marker = payload.clone();
					marker[8] = 4;
					try (Connection connection = DatabaseFactory.getConnection();
						PreparedStatement statement = connection.prepareStatement("UPDATE characters SET curHp=180,curCp=72,x=45975,y=47879,heading=12772 WHERE charId=?"))
					{
						statement.setInt(1, objectId);
						PhantomAssertions.assertEquals(1, statement.executeUpdate(), "Latent TEST canonical drift was not installed.");
					}
					final Canonical drifted = canonical(objectId);
					final var witness = new PhantomBackgroundTransaction.LegacyMaterializedWitness(fixture.profileId(), objectId, version, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload)), HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(marker)), drifted.currentHp(), drifted.currentMp(), drifted.currentCp(), drifted.x(), drifted.y(), drifted.z(), drifted.heading());
					final var stale = new PhantomBackgroundTransaction.LegacyMaterializedWitness(witness.profileId(), objectId, version + 2, witness.materializedPayloadSha256(), witness.inconsistentPayloadSha256(), witness.canonicalHp(), witness.canonicalMp(), witness.canonicalCp(), witness.canonicalX(), witness.canonicalY(), witness.canonicalZ(), witness.canonicalHeading());
					PhantomAssertions.assertEquals(Status.STATE_CONFLICT, transaction.recoverAttestedLegacyMaterializedDrift(stale).status(), "Stale latent rowVersion was admitted.");
					final var changed = new PhantomBackgroundTransaction.LegacyMaterializedWitness(witness.profileId(), objectId, version, "a".repeat(64), witness.inconsistentPayloadSha256(), witness.canonicalHp(), witness.canonicalMp(), witness.canonicalCp(), witness.canonicalX(), witness.canonicalY(), witness.canonicalZ(), witness.canonicalHeading());
					PhantomAssertions.assertEquals(Status.STATE_CONFLICT, transaction.recoverAttestedLegacyMaterializedDrift(changed).status(), "Changed latent payload was admitted.");
					try (Connection connection = DatabaseFactory.getConnection();
						PreparedStatement statement = connection.prepareStatement("UPDATE characters SET exp=exp+1 WHERE charId=?"))
					{
						statement.setInt(1, objectId);
						statement.executeUpdate();
					}
					PhantomAssertions.assertEquals(Status.CANONICAL_MISMATCH, transaction.recoverAttestedLegacyMaterializedDrift(witness).status(), "Mixed latent progress was silently healed.");
					try (Connection connection = DatabaseFactory.getConnection();
						PreparedStatement statement = connection.prepareStatement("UPDATE characters SET exp=exp-1 WHERE charId=?"))
					{
						statement.setInt(1, objectId);
						statement.executeUpdate();
					}
					try (Connection connection = DatabaseFactory.getConnection();
						PreparedStatement statement = connection.prepareStatement("UPDATE items SET count=count+1 WHERE owner_id=? AND item_id=?"))
					{
						statement.setInt(1, objectId);
						statement.setInt(2, PhantomActionFacade.FIXTURE_ITEM_ID);
						PhantomAssertions.assertEquals(1, statement.executeUpdate(), "Mixed latent inventory fixture was not installed.");
					}
					PhantomAssertions.assertEquals(Status.CANONICAL_MISMATCH, transaction.recoverAttestedLegacyMaterializedDrift(witness).status(), "Mixed latent inventory was silently healed.");
					try (Connection connection = DatabaseFactory.getConnection();
						PreparedStatement statement = connection.prepareStatement("UPDATE items SET count=count-1 WHERE owner_id=? AND item_id=?"))
					{
						statement.setInt(1, objectId);
						statement.setInt(2, PhantomActionFacade.FIXTURE_ITEM_ID);
						PhantomAssertions.assertEquals(1, statement.executeUpdate(), "Mixed latent inventory fixture was not restored.");
					}
					if (phase == 1)
					{
						PhantomAssertions.assertEquals(Status.INCONSISTENT, transaction.abortMaterialization(fixture.profileId(), objectId).status(), "Latent TEST marker transition was not recorded.");
					}
					if (phase == 0)
					{
						final PhantomBackgroundTransaction interrupted = new PhantomBackgroundTransaction(DatabaseFactory::getConnection, allocator(new AtomicInteger()), point ->
						{
							if (point == FaultPoint.AFTER_CANONICAL_WRITES)
							{
								throw new InjectedFailure();
							}
						});
						PhantomAssertions.assertTrue(!interrupted.recoverAttestedLegacyMaterializedDrift(witness).successful(), "Interrupted latent repair unexpectedly committed.");
						PhantomAssertions.assertEquals(drifted, canonical(objectId), "Interrupted latent repair changed canonical character.");
						PhantomAssertions.assertEquals(State.MATERIALIZED, transaction.load(fixture.profileId()).state().state(), "Interrupted latent repair changed background state.");
					}
					final Result recovered = transaction.recoverAttestedLegacyMaterializedDrift(witness);
					PhantomAssertions.assertEquals(Status.SUCCESS, recovered.status(), "Exact latent witness did not recover.");
					PhantomAssertions.assertEquals(State.READY, recovered.state().state(), "Latent witness did not become READY.");
					PhantomAssertions.assertEquals(128.0, canonical(objectId).currentHp(), "Latent repair lost HP projection.");
					PhantomAssertions.assertEquals(44126, canonical(objectId).x(), "Latent repair lost position projection.");
					PhantomAssertions.assertEquals(Status.STATE_CONFLICT, transaction.recoverAttestedLegacyMaterializedDrift(witness).status(), "Consumed latent witness was reused.");
				}
				PhantomAssertions.assertEquals(Status.SUCCESS, transaction.markMaterialized(fixture.profileId(), objectId).status(), "Recovered latent profile cannot materialize normally.");
				PhantomAssertions.assertEquals(Status.SUCCESS, transaction.abortMaterialization(fixture.profileId(), objectId).status(), "Recovered latent profile cannot complete owned restart recovery.");
			}
		}
		finally
		{
			restoreCharacter(objectId, original, originalBaseClass);
		}
	}

	private static void testLegacyWitnessLoader() throws Exception
	{
		final Path witnessFile = Files.createTempFile("m1-legacy-witness-", ".tsv");
		try
		{
			final String row = "13\t10013\t4\t" + "a".repeat(64) + "\t180\t67\t72\t45975\t47879\t-3488\t12772\n";
			Files.writeString(witnessFile, "M1_LEGACY_HEADLESS_AUTOSAVE_V1\n" + row, StandardCharsets.UTF_8);
			PhantomAssertions.assertEquals(1, PhantomLegacyHeadlessRecovery.load(witnessFile).size(), "Bounded witness did not load.");
			Files.writeString(witnessFile, "M1_LEGACY_HEADLESS_AUTOSAVE_V1\n" + row + row, StandardCharsets.UTF_8);
			PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> PhantomLegacyHeadlessRecovery.load(witnessFile), "Duplicate legacy witness was admitted.");
			final String materialized = "13\t10013\t4\t" + "a".repeat(64) + "\t" + "b".repeat(64) + "\t180\t67\t72\t45975\t47879\t-3488\t12772\n";
			Files.writeString(witnessFile, "M1_LEGACY_MATERIALIZED_37_V1\n" + materialized, StandardCharsets.UTF_8);
			PhantomAssertions.assertEquals(1, PhantomLegacyHeadlessRecovery.loadMaterialized(witnessFile).size(), "Bounded materialized witness did not load.");
			Files.writeString(witnessFile, "M1_LEGACY_MATERIALIZED_37_V1\n" + materialized + materialized, StandardCharsets.UTF_8);
			PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> PhantomLegacyHeadlessRecovery.loadMaterialized(witnessFile), "Duplicate materialized witness was admitted.");
		}
		finally
		{
			Files.deleteIfExists(witnessFile);
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
			final Result staleCapture = captureTestBaseline(baseline.transaction(), baseline.ready().withState(State.MATERIALIZED), staleGoal);
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
				final Result failed = point == FaultPoint.BEFORE_CAPTURE_COMMIT ? captureTestBaseline(faulting, baseline.ready().withState(State.MATERIALIZED), baseline.goal()) : faulting.markMaterialized(baseline.profileId(), baseline.characterObjectId());
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

	private void testRetiredAutoSaveEpoch(PhantomTestContext context) throws Exception
	{
		final int objectId = _environment.primary().objectId();
		final Canonical original = canonical(objectId);
		final int originalBaseClass = (int) scalarLong("SELECT base_class FROM characters WHERE charId = ?", objectId);
		// Exact profile129 class/progression/max-vitals shape; only the owned TEST identity differs.
		restoreCharacter(objectId, new Canonical(6, 7209, 7701, 460, 0, 170, 158, 158, 0, 85, 44126, 42751, -3488, 25847, 25, 1), 25);
		try (RuntimeFixture runtime = createRuntimeFixture(objectId))
		{
			PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, runtime.materialization().materialize(runtime.profileId()).status(), "Coherent dead native fixture did not materialize.");
			try (var action = runtime.materialization().tryAcquireAction(runtime.profileId()).orElseThrow())
			{
				for (int itemId : List.of(6, 425, 461))
				{
					final var item = action.player().getInventory().addItem(ItemProcessType.REWARD, itemId, 1, action.player(), this);
					PhantomAssertions.assertTrue(item != null, "Profile129 native paperdoll fixture item is absent.");
					action.player().getInventory().equipItem(item);
				}
			}
			PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, runtime.materialization().dematerialize(runtime.profileId()).status(), "Coherent dead native fixture did not store.");
			PhantomAssertions.assertEquals(State.DEAD, runtime.transaction().load(runtime.profileId()).state().state(), "Profile129 sequence did not start from coherent DEAD.");
			PhantomAssertions.assertTrue(runtime.background().recover(runtime.profileId(), runtime.goal(), PhantomActivityState.WARM).successful(), "Native DEAD recovery did not produce the prior town epoch.");
			context.record("profile129.sequence.start", "coherent DEAD -> native recovery -> READY town");
			PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, runtime.materialization().materialize(runtime.profileId()).status(), "First native epoch did not materialize.");
			final Player retired;
			try (var action = runtime.materialization().tryAcquireAction(runtime.profileId()).orElseThrow())
			{
				retired = action.player();
				retired.teleToLocation(45978, 47886, -3488); retired.onTeleported();
				// The small TEST geodata fixture resolves the teleport Z differently from PLAY.
				retired.setXYZInvisible(45978, 47886, -3488);
				retired.setHeading(12773); retired.setCurrentHp(retired.getMaxHp()); retired.setCurrentMp(retired.getMaxMp()); retired.setCurrentCp(retired.getMaxCp());
			}
			PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, runtime.materialization().dematerialize(runtime.profileId()).status(), "First native epoch did not clean up.");
			PhantomAssertions.assertTrue(!retired.isOnline() && !retired.hasHeadlessOutboundSession(), "Retired epoch did not detach its outbound ownership.");
			final var ready = runtime.transaction().load(runtime.profileId()).state();
			PhantomAssertions.assertEquals(170.0, ready.vitals().maximumHp(), "Profile129 native max HP differs.");
			PhantomAssertions.assertEquals(85.0, ready.vitals().maximumCp(), "Profile129 native max CP differs.");
			PhantomAssertions.assertEquals(158.0, ready.vitals().maximumMp(), "Profile129 native max MP differs.");
			final var nextVitals = new Vitals(123, ready.vitals().maximumHp(), ready.vitals().currentMp(), ready.vitals().maximumMp(), 7, ready.vitals().maximumCp());
			final var nextPosition = new Position(0, 44126, 42751, -3488, 25847, ANCHOR_ID);
			final var advanced = runtime.transaction().execute(new PhantomBackgroundTransaction.Command(ready, runtime.goal(), key(runtime.fixture(), 2, 1, ActionKind.FARM), ready.progress(), nextVitals, nextPosition, ready.clock(), Map.of(), ready.autoGetSkills()));
			PhantomAssertions.assertEquals(Status.SUCCESS, advanced.status(), "Guarded background work did not commit the next epoch facts.");
			PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, runtime.materialization().materialize(runtime.profileId()).status(), "Second native epoch did not materialize.");
			PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, runtime.materialization().dematerialize(runtime.profileId()).status(), "Second native epoch did not cleanly store.");
			PhantomAssertions.assertEquals(Status.SUCCESS, runtime.transaction().markMaterialized(runtime.profileId(), objectId).status(), "Persisted next epoch did not enter MATERIALIZED.");
			final Canonical before = canonical(runtime.characterObjectId());
			// The manager can already hold an iterator entry when cleanup removes it.
			// This invokes that delayed callback after the old outbound attachment closed.
			retired.autoSave();
			final Canonical afterCallback = canonical(objectId);
			final var verified = runtime.transaction().abortMaterialization(runtime.profileId(), objectId);
			context.record("profile129.sequence.callback", "before=" + before + " after=" + afterCallback + " verification=" + verified.status() + "/" + (verified.state() == null ? "absent" : verified.state().state()));
			PhantomAssertions.assertEquals(before, canonical(runtime.characterObjectId()), "Retired autosave overwrote the next Player epoch with profile129-shaped town/vitals drift.");
			PhantomAssertions.assertEquals(Status.SUCCESS, verified.status(), "Next epoch became INCONSISTENT after delayed autosave.");
		}
		finally
		{
			restoreCharacter(objectId, original, originalBaseClass);
			restorePrimaryInventoryAndSkills(objectId);
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

	private void testNativeDeathTimer() throws Exception
	{
		final RuntimeFixture runtime = createRuntimeFixture(_environment.primary().objectId());
		final java.util.concurrent.atomic.AtomicLong clock = new java.util.concurrent.atomic.AtomicLong(1_000_000_000L);
		final List<Runnable> deferredRecovery = new java.util.concurrent.CopyOnWriteArrayList<>();
		try (var observer = new org.l2jmobius.gameserver.phantoms.background.PhantomOrdinaryDeathRecovery(runtime.materialization(), new PhantomGoalStateStore(_repository), runtime.background(), clock::get, deferredRecovery::add))
		{
			runtime.materialization().materialize(runtime.profileId());
			runtime.materialization().dematerialize(runtime.profileId());
			runtime.materialization().materialize(runtime.profileId());
			PhantomAssertions.assertTrue(observer.install(), "Native death timer did not install.");
			try (var action = runtime.materialization().tryAcquireAction(runtime.profileId()).orElseThrow())
			{
				PhantomAssertions.assertTrue(action.player().doDie(null), "Native Player death did not publish its lifecycle event.");
			}
			PhantomAssertions.assertTrue(runtime.transaction().load(runtime.profileId()).state().vitals().currentHp() > 0, "Fixture must retain its pre-death durable vitals.");
			PhantomAssertions.assertEquals(PhantomBackgroundService.DirectiveKind.RECOVER, runtime.background().directive(runtime.profileId(), runtime.goal(), PhantomActivityState.ACTIVE).kind(), "Native dead Player was hidden by stale materialized vitals.");
			clock.addAndGet(44_000_000_000L);
			observer.pulse();
			try (var action = runtime.materialization().tryAcquireAction(runtime.profileId()).orElseThrow())
			{
				PhantomAssertions.assertTrue(action.player().isDead(), "Native corpse recovered before its resurrection window.");
			}
			clock.addAndGet(1_000_000_000L);
			observer.pulse();
			try (var action = runtime.materialization().tryAcquireAction(runtime.profileId()).orElseThrow())
			{
				PhantomAssertions.assertFalse(action.player().isDead(), "Deferred database reconciliation delayed native corpse recovery.");
			}
			PhantomAssertions.assertEquals(1, deferredRecovery.size(), "Native town return did not enqueue one durable reconciliation.");
			PhantomAssertions.assertEquals(PhantomBackgroundService.DirectiveKind.RECOVER, runtime.background().directive(runtime.profileId(), runtime.goal(), PhantomActivityState.ACTIVE).kind(), "Pending native town recovery was classified as ordinary farming before reconciliation.");
			deferredRecovery.remove(0).run();
			try (var action = runtime.materialization().tryAcquireAction(runtime.profileId()).orElseThrow())
			{
				PhantomAssertions.assertFalse(action.player().isDead(), "Native death event did not recover at 45 seconds.");
				PhantomAssertions.assertTrue(action.player().doDie(null), "Second native Player death did not publish its lifecycle event.");
				clock.addAndGet(10_000_000_000L);
				action.player().doRevive();
			}
			observer.pulse();
			final var resurrected = runtime.transaction().load(runtime.profileId()).state().position();
			clock.addAndGet(40_000_000_000L);
			observer.pulse();
			try (var action = runtime.materialization().tryAcquireAction(runtime.profileId()).orElseThrow())
			{
				PhantomAssertions.assertFalse(action.player().isDead(), "Normal resurrection was lost.");
				PhantomAssertions.assertEquals(resurrected.x(), action.player().getX(), "Normal resurrection did not cancel the old town-return timer.");
				PhantomAssertions.assertEquals(resurrected.y(), action.player().getY(), "Normal resurrection did not cancel the old town-return timer.");
			}
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
			PhantomAssertions.assertEquals(OperationStatus.SUCCESS, recovered.status(), "Canonical recovery must keep the ordinary farming goal active: " + recovered.reason());
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

	private void testPreexistingMaterializationRecovery() throws Exception
	{
		final RuntimeFixture runtime = createRuntimeFixture(_environment.primary().objectId());
		try
		{
			makeDead(runtime);
			PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, runtime.materialization().materialize(runtime.profileId()).status(), "Preexisting recovery materialization did not become ACTIVE.");
			final PhantomBackgroundService.OperationResult recovered = runtime.background().recover(runtime.profileId(), runtime.goal(), PhantomActivityState.ACTIVE);
			PhantomAssertions.assertEquals(OperationStatus.SUCCESS, recovered.status(), "Preexisting materialization recovery must keep the ordinary farming goal active: " + recovered.reason());
			PhantomAssertions.assertTrue(runtime.materialization().find(runtime.profileId()).filter(snapshot -> snapshot.state() == org.l2jmobius.gameserver.phantoms.player.PhantomMaterializedPlayer.State.ACTIVE).isPresent(), "Recovery dematerialized a Player owned by the existing ACTIVE lifecycle.");
			PhantomAssertions.assertEquals(State.MATERIALIZED, runtime.transaction().load(runtime.profileId()).state().state(), "Restored ACTIVE recovery did not retain matching MATERIALIZED background state.");
		}
		finally
		{
			runtime.close();
		}
	}

	private void testNormalResurrectionCancelsTownReturn() throws Exception
	{
		final RuntimeFixture runtime = createRuntimeFixture(_environment.primary().objectId());
		try
		{
			makeDead(runtime);
			PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, runtime.materialization().materialize(runtime.profileId()).status(), "Resurrection fixture did not materialize.");
			final Position before;
			try (var action = runtime.materialization().tryAcquireAction(runtime.profileId()).orElseThrow())
			{
				before = runtime.transaction().load(runtime.profileId()).state().position();
				action.player().doRevive();
			}
			final PhantomBackgroundService.OperationResult resumed = runtime.background().recover(runtime.profileId(), runtime.goal(), PhantomActivityState.ACTIVE);
			PhantomAssertions.assertEquals(OperationStatus.SUCCESS, resumed.status(), "Normal resurrection did not reconcile without town return: " + resumed.reason());
			PhantomAssertions.assertEquals(before, runtime.transaction().load(runtime.profileId()).state().position(), "Normal resurrection incorrectly teleported to town.");
			PhantomAssertions.assertTrue(runtime.materialization().find(runtime.profileId()).isPresent(), "Resurrected Player did not resume materialized life.");
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

	private void testProductionDeathRecovery() throws Exception
	{
		final PhantomTopologyAnchor initialAnchor = _production.topology().findAnchor("population.farming.dark-elf.20529").orElseThrow();
		ProductionPlayerFixture playerFixture = null;
		PhantomProfile profile = null;
		PhantomBackgroundService background = null;
		PhantomMaterializationService materialization = null;
		try
		{
			playerFixture = openProductionPlayerFixture(initialAnchor);
			final int objectId = playerFixture.player().getObjectId();
			profile = _repository.create(objectId);
			final long profileId = profile.profileId();
			final PhantomGoal goal = playerFixture.goal();
			final PhantomGoalStateStore goals = new PhantomGoalStateStore(_repository);
			goals.insert(profileId, goal);
			final PhantomBackgroundTransaction transaction = new PhantomBackgroundTransaction();
			final AtomicReference<PhantomMaterializationService> materializationRef = new AtomicReference<>();
			background = new PhantomBackgroundService(_repository, goals, PhantomIdentityLeaseRegistry.getInstance(), transaction, _production.authority(), new PhantomBackgroundCompetitionRegistry(), noSignals(), materializationRef::get);
			PhantomAssertions.assertTrue(background.start(), "Production recovery background service did not start.");
			final PhantomMetrics metrics = new PhantomMetrics();
			materialization = new PhantomMaterializationService(_repository, PhantomIdentityLeaseRegistry.getInstance(), metrics, new PhantomDiagnosticTrace(false, 64, 16, metrics), 1, point ->
			{
			}, background, 5_000, 10_000);
			PhantomAssertions.assertTrue(materialization.start(), "Production recovery materialization service did not start.");
			materializationRef.set(materialization);
			for (PhantomTopologyAnchor farmAnchor : _production.topology().snapshot().anchors().stream().filter(anchor -> anchor.id().startsWith("population.farming.")).sorted(Comparator.comparing(PhantomTopologyAnchor::id)).toList())
			{
				final Position farmPosition = canonicalAnchorPosition(farmAnchor, playerFixture.player().getHeading());
				playerFixture.player().setXYZInvisible(farmPosition.x(), farmPosition.y(), farmPosition.z());
				final Location town = MapRegionData.getInstance().getTeleToLocation(playerFixture.player(), TeleportWhereType.TOWN);
				PhantomAssertions.assertTrue(town != null, "Production recovery corpus lacks a town for " + farmAnchor.id());
				final Position recoveryPosition = _production.authority().canonicalRecoveryPosition(town.getX(), town.getY(), town.getZ(), town.getInstanceId(), playerFixture.player().getHeading()).orElseThrow(() -> new AssertionError("Production recovery corpus lacks a canonical town anchor for " + farmAnchor.id()));
				PhantomAssertions.assertEquals(MapRegionData.getInstance().getMapRegionLocId(town.getX(), town.getY()), MapRegionData.getInstance().getMapRegionLocId(recoveryPosition.x(), recoveryPosition.y()), "Canonical recovery anchor crossed the resolved town map region for " + farmAnchor.id());
				playerFixture.player().setXYZInvisible(recoveryPosition.x(), recoveryPosition.y(), recoveryPosition.z());
				try
				{
					PhantomAssertions.assertEquals(recoveryPosition.committedAnchorId(), _production.authority().capture(profileId, playerFixture.player(), goal, null).position().committedAnchorId(), "Canonical recovery position is not an exact unique topology anchor for " + farmAnchor.id());
				}
				catch (IllegalArgumentException exception)
				{
					throw new AssertionError("Canonical recovery position is ambiguous for " + farmAnchor.id() + ": " + recoveryPosition, exception);
				}
			}
			final Position initialPosition = canonicalAnchorPosition(initialAnchor, playerFixture.player().getHeading());
			playerFixture.player().setXYZInvisible(initialPosition.x(), initialPosition.y(), initialPosition.z());

			final PhantomBackgroundState captured = _production.authority().capture(profileId, playerFixture.player(), goal, null);
			playerFixture.player().storeMe();
			PhantomAssertions.assertEquals(Status.SUCCESS, captureTestBaseline(transaction, captured, goal).status(), "Production recovery baseline capture failed.");
			playerFixture.releaseRuntime();
			final PhantomBackgroundState ready = transaction.load(profileId).state();
			final Vitals deadVitals = new Vitals(0, ready.vitals().maximumHp(), ready.vitals().currentMp(), ready.vitals().maximumMp(), 0, ready.vitals().maximumCp());
			final Progress deadProgress = new Progress(ready.progress().level(), ready.progress().experience(), ready.progress().skillPoints(), ready.progress().experience());
			final PhantomBackgroundOperationKey operationKey = new PhantomBackgroundOperationKey(profileId, objectId, goal.goalId(), goal.revision(), 1, 1, ActionKind.FARM, PRODUCTION_TARGET_NPC_ID, PRODUCTION_FARM_ANCHOR_ID, PhantomBackgroundState.MODEL_VERSION, _production.authority().hashes());
			final Result dead = transaction.execute(new PhantomBackgroundTransaction.Command(ready, goal, operationKey, deadProgress, deadVitals, ready.position(), new Clock(3, 0, 0), Map.of(), exactAutoGetSkills(ready.identity(), deadProgress.level())));
			PhantomAssertions.assertEquals(Status.SUCCESS, dead.status(), "Production recovery DEAD state could not be committed.");
			PhantomAssertions.assertEquals(State.DEAD, dead.state().state(), "Production recovery fixture did not enter DEAD.");

			final PhantomBackgroundService.OperationResult recovered = background.recover(profileId, goal, PhantomActivityState.WARM);
			PhantomAssertions.assertEquals(OperationStatus.SUCCESS, recovered.status(), "Production town recovery must keep the ordinary farming goal active: " + recovered.reason());
			final PhantomBackgroundState recoveredState = transaction.load(profileId).state();
			PhantomAssertions.assertEquals(State.READY, recoveredState.state(), "Production town recovery did not restore READY.");
			PhantomAssertions.assertTrue(_production.topology().findAnchor(recoveredState.position().committedAnchorId()).isPresent(), "Production town recovery did not retain a corpus topology anchor.");
			PhantomAssertions.assertEquals(MapRegionData.getInstance().getMapRegionLocId(initialAnchor.point().x(), initialAnchor.point().y()), MapRegionData.getInstance().getMapRegionLocId(recoveredState.position().x(), recoveredState.position().y()), "Production town recovery crossed map-region ownership.");
			PhantomAssertions.assertTrue(materialization.find(profileId).isEmpty(), "Production town recovery retained a materialized entry.");
			PhantomAssertions.assertEquals(0, background.snapshot().currentTransitionClaims(), "Production town recovery retained a background transition claim.");

			final PhantomGoalStateStore.StoredGoal storedGoal = goals.load(profileId).orElseThrow();
			final PhantomGoal failedGoal = goal.withStatus(PhantomGoalStatus.FAILED);
			goals.replace(profileId, storedGoal.rowVersion(), failedGoal);
			PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, materialization.materialize(profileId).status(), "Terminal recovery goal could not rematerialize for lifecycle cleanup.");
			PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, materialization.dematerialize(profileId).status(), "Terminal recovery goal retained lifecycle cleanup ownership.");
			PhantomAssertions.assertTrue(materialization.find(profileId).isEmpty(), "Terminal recovery goal retained a materialized entry.");
			PhantomAssertions.assertEquals(State.READY, transaction.load(profileId).state().state(), "Terminal recovery lifecycle capture did not restore READY.");
			PhantomAssertions.assertEquals(0, background.snapshot().currentTransitionClaims(), "Terminal recovery lifecycle capture retained a background transition claim.");
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
			deleteProfile(profile);
			if (playerFixture != null)
			{
				playerFixture.close();
			}
		}
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
			PhantomAssertions.assertEquals(java.util.Set.of(PhantomBackgroundGoalSpec.TRAVEL_ACTION, PhantomBackgroundGoalSpec.FARM_ACTION, PhantomBackgroundGoalSpec.RECOVER_ACTION, "background.visible.start", "background.visible.await"), handlers.snapshot().keySet(), "Background action registration changed.");
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

	private void testVisibleAliveCandidate() throws Exception
	{
		final RuntimeFixture runtime = createRuntimeFixture(_environment.primary().objectId());
		try
		{
			PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, runtime.materialization().materialize(runtime.profileId()).status(), "Visible farming baseline did not materialize.");
			PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, runtime.materialization().dematerialize(runtime.profileId()).status(), "Visible farming baseline did not persist background state.");
			PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, runtime.materialization().materialize(runtime.profileId()).status(), "Visible farming fixture did not materialize.");
			final PhantomBackgroundDecision decision = new PhantomBackgroundDecision(runtime.background(), (profileId, goal) -> profileId == runtime.profileId(), (profileId, goal) -> profileId == runtime.profileId(), profileId -> {});
			final PhantomCandidateRegistry candidates = new PhantomCandidateRegistry();
			decision.registerCandidates(candidates);
			candidates.seal();
			final PhantomPlanningContext planning = new PhantomPlanningContext(runtime.profileId(), runtime.goal(), PhantomCapabilitySet.empty(), PhantomActivityState.ACTIVE, 7, 9, 1234, 1);
			final var selected = new PhantomUtilitySelector().select(candidates.snapshot(), planning);
			PhantomAssertions.assertTrue(selected.candidate() != null, "Living materialized farm.background goal had NO_CANDIDATE before the shared AutoPlay target search: " + runtime.background().directive(runtime.profileId(), runtime.goal(), PhantomActivityState.ACTIVE));
			final PhantomPlan plan = selected.candidate().planFactory().create(planning);
			PhantomAssertions.assertEquals(List.of("background.visible.start", "background.visible.await"), plan.steps().stream().map(step -> step.actionKey()).toList(), "Visible farm did not use the shared AutoPlay executor.");
			final PhantomStepHandlerRegistry handlers = new PhantomStepHandlerRegistry();
			decision.registerHandlers(handlers);
			handlers.seal();
			final PhantomStepResult started = handlers.snapshot().get(plan.steps().getFirst().actionKey()).execute(new PhantomStepContext(runtime.profileId(), runtime.goal(), plan, plan.steps().getFirst(), PhantomActivityState.ACTIVE, 7, 9, 1234, 1, () -> false));
			PhantomAssertions.assertEquals(PhantomStepResult.Type.SUCCESS, started.type(), "Visible farm did not start AutoPlay through its registered handler.");
		}
		finally
		{
			runtime.close();
		}
	}

	private void testVisibleOutgrownStopsAutoPlay() throws Exception
	{
		final RuntimeFixture runtime = createRuntimeFixture(_environment.primary().objectId());
		try
		{
			runtime.materialization().materialize(runtime.profileId());
			runtime.materialization().dematerialize(runtime.profileId());
			runtime.materialization().materialize(runtime.profileId());
			final java.util.concurrent.atomic.AtomicInteger suitabilityChecks = new java.util.concurrent.atomic.AtomicInteger();
			final java.util.concurrent.atomic.AtomicInteger stops = new java.util.concurrent.atomic.AtomicInteger();
			final PhantomBackgroundDecision decision = new PhantomBackgroundDecision(runtime.background(), (profileId, goal) -> true, (profileId, goal) -> true, (profileId, goal) -> suitabilityChecks.incrementAndGet() == 1, profileId -> stops.incrementAndGet());
			final PhantomCandidateRegistry candidates = new PhantomCandidateRegistry();
			decision.registerCandidates(candidates);
			candidates.seal();
			final PhantomStepHandlerRegistry handlers = new PhantomStepHandlerRegistry();
			decision.registerHandlers(handlers);
			handlers.seal();
			final PhantomPlanningContext planning = new PhantomPlanningContext(runtime.profileId(), runtime.goal(), PhantomCapabilitySet.empty(), PhantomActivityState.ACTIVE, 7, 9, 1234, 1);
			final PhantomPlan plan = candidates.snapshot().getFirst().planFactory().create(planning);
			final var start = plan.steps().getFirst();
			final var await = plan.steps().get(1);
			PhantomAssertions.assertEquals(PhantomStepResult.Type.SUCCESS, handlers.snapshot().get(start.actionKey()).execute(new PhantomStepContext(runtime.profileId(), runtime.goal(), plan, start, PhantomActivityState.ACTIVE, 7, 9, 1234, 1, () -> false)).type(), "Visible farm did not start.");
			PhantomAssertions.assertEquals(PhantomStepResult.Type.REPLAN, handlers.snapshot().get(await.actionKey()).execute(new PhantomStepContext(runtime.profileId(), runtime.goal(), plan, await, PhantomActivityState.ACTIVE, 7, 10, 2234, 2, () -> false)).type(), "Outgrown farm did not replan.");
			PhantomAssertions.assertEquals(1, stops.get(), "Old exact-NPC AutoPlay was not stopped.");
		}
		finally
		{
			runtime.close();
		}
	}

	private void testVisibleDeathWindow() throws Exception
	{
		final RuntimeFixture runtime = createRuntimeFixture(_environment.primary().objectId());
		try
		{
			makeDead(runtime);
			PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, runtime.materialization().materialize(runtime.profileId()).status(), "Dead visible fixture did not materialize.");
			final PhantomBackgroundDecision decision = new PhantomBackgroundDecision(runtime.background());
			final PhantomCandidateRegistry candidates = new PhantomCandidateRegistry();
			decision.registerCandidates(candidates);
			candidates.seal();
			final var candidate = candidates.snapshot().getFirst();
			final long first = 1_000_000_000L;
			final PhantomPlanningContext early = new PhantomPlanningContext(runtime.profileId(), runtime.goal(), PhantomCapabilitySet.empty(), PhantomActivityState.ACTIVE, 1, 1, first, 1);
			PhantomAssertions.assertEquals(null, new PhantomUtilitySelector().select(candidates.snapshot(), early).candidate(), "Dead Player was offered immediate town recovery without a resurrection window.");
			final PhantomPlanningContext due = new PhantomPlanningContext(runtime.profileId(), runtime.goal(), PhantomCapabilitySet.empty(), PhantomActivityState.ACTIVE, 1, 2, first + 45_000_000_000L, 2);
			PhantomAssertions.assertEquals(candidate, new PhantomUtilitySelector().select(candidates.snapshot(), due).candidate(), "Unattended dead Player did not become recovery eligible at 45 seconds.");
			PhantomAssertions.assertEquals(PhantomBackgroundGoalSpec.RECOVER_ACTION, candidate.planFactory().create(due).steps().getFirst().actionKey(), "Death timeout did not select native town recovery.");
		}
		finally
		{
			runtime.close();
		}
	}

	private void testPhantomAutoPlayAdmission() throws Exception
	{
		PhantomAssertions.assertFalse(AutoPlayConfig.ENABLE_AUTO_PLAY, "The fixture must keep ordinary user .play disabled.");
		final RuntimeFixture runtime = createRuntimeFixture(_environment.primary().objectId());
		try
		{
			PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, runtime.materialization().materialize(runtime.profileId()).status(), "Phantom AutoPlay fixture did not materialize.");
			final Player player;
			try (var lease = runtime.materialization().tryAcquireAction(runtime.profileId()).orElseThrow())
			{
				player = lease.player();
			}
			final CountDownLatch ticks = new CountDownLatch(2);
			final AutoPlayTaskManager.PhantomPolicy policy = new AutoPlayTaskManager.PhantomPolicy()
			{
				@Override
				public AutoPlayTaskManager.TickLease acquire(Player actor)
				{
					if (actor != player)
					{
						return null;
					}
					ticks.countDown();
					return () -> {};
				}

				@Override
				public boolean permitsTarget(org.l2jmobius.gameserver.model.actor.Creature target)
				{
					return false;
				}
			};
			try
			{
				AutoPlayTaskManager.getInstance().startPhantomAutoPlay(player, policy);
				AutoUseTaskManager.getInstance().startPhantomAutoUse(player, policy);
				PhantomAssertions.assertTrue(ticks.await(3, TimeUnit.SECONDS), "Shared AutoPlay/AutoUse pools did not tick the admitted phantom while .play was disabled.");
				PhantomAssertions.assertTrue(player.isAutoPlaying(), "Phantom AutoPlay admission did not set the native Player state.");
			}
			finally
			{
				AutoUseTaskManager.getInstance().stopAutoUseTask(player);
				AutoPlayTaskManager.getInstance().stopAutoPlay(player);
			}
			PhantomAssertions.assertFalse(player.isAutoPlaying(), "Phantom AutoPlay stop retained native Player state.");
		}
		finally
		{
			runtime.close();
		}
	}

	private void testLocalAbortContainment() throws Exception
	{
		final var pendingFault = new AtomicBoolean();
		final var transaction = new PhantomBackgroundTransaction(DatabaseFactory::getConnection, allocator(new AtomicInteger()), point -> { if (pendingFault.get() && (point == FaultPoint.AFTER_OWNED_PREPARE)) { throw new InjectedFailure(); } });
		try (var runtime = createRuntimeFixture(_environment.primary().objectId(), transaction, _ -> {}); var neighbor = createFixture(_environment.observer().objectId(), runtime.transaction()))
		{
			PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, runtime.materialization().materialize(runtime.profileId()).status(), "Abort fixture did not establish a baseline.");
			final Player player;
			final PlayerNativeWork.Owner nativeOwner;
			final long epoch;
			try (var action = runtime.materialization().tryAcquireAction(runtime.profileId()).orElseThrow())
			{
				player = action.player(); nativeOwner = player.getNativeWorkOwner();
				epoch = runtime.materialization().find(runtime.profileId()).orElseThrow().materializedAtNanos();
			}
			PhantomAssertions.assertTrue(nativeOwner != null && nativeOwner.player() == player && nativeOwner.isCurrent() && nativeOwner.epoch() == epoch && PlayerNativeWork.current(nativeOwner) == null, "Local pending STORE did not retain exact lifetime outside ActionLease.");
			player.storeMe(); runtime.transaction().markMaterialized(runtime.profileId(), runtime.characterObjectId());
			pendingFault.set(true);
			PhantomAssertions.assertFalse(runtime.background().captureVisibleArrival(runtime.profileId(), player, runtime.goal(), ANCHOR_ID), "Injected live PREPARE unexpectedly completed.");
			PhantomAssertions.assertTrue(player.hasPendingOwnedStore(), "Local pending actor lost its fence.");
			PhantomAssertions.assertEquals(OperationStatus.SUCCESS, runtime.background().farm(neighbor.profileId(), neighbor.goal(), 1, 1, PhantomActivityState.BACKGROUND, 1).status(), "Local pending stopped healthy neighbor.");
			pendingFault.set(false);
			PhantomAssertions.assertTrue(player.getNativeWorkOwner() == nativeOwner && nativeOwner.isCurrent() && nativeOwner.epoch() == runtime.materialization().find(runtime.profileId()).orElseThrow().materializedAtNanos() && PlayerNativeWork.current(nativeOwner) == null, "Local pending resume changed native Player owner/epoch or retained ActionLease.");
			PhantomAssertions.assertEquals(PhantomBackgroundService.VisibleStoreStatus.SUCCESS, runtime.background().resumeVisibleOwnedStore(runtime.profileId(), player, runtime.goal()).status(), "Local pending did not resolve through its attached owner.");
			PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, runtime.materialization().dematerialize(runtime.profileId()).status(), "Abort fixture did not release Player.");
			runtime.background().beforeMaterialize(runtime.profileId(), runtime.characterObjectId());
			runtime.transaction().markMaterialized(runtime.profileId(), runtime.characterObjectId());
			try (var connection = DatabaseFactory.getConnection(); var statement = connection.prepareStatement("UPDATE characters SET curHp=curHp-1 WHERE charId=?")) { statement.setInt(1, runtime.characterObjectId()); statement.executeUpdate(); }
			runtime.background().materializeAborted(runtime.profileId(), runtime.characterObjectId());
			PhantomAssertions.assertEquals(State.INCONSISTENT, runtime.transaction().load(runtime.profileId()).state().state(), "Unknown local projection was not fenced.");
			final var allowed = runtime.background().farm(neighbor.profileId(), neighbor.goal(), 1, 2, PhantomActivityState.BACKGROUND, 2);
			PhantomAssertions.assertEquals(OperationStatus.SUCCESS, allowed.status(), "Local invalid abort stopped healthy neighbor: " + allowed.reason());
		}
	}

	@SuppressWarnings("unchecked")
	private void testPooledPolicyException() throws Exception
	{
		try (var runtime = createRuntimeFixture(_environment.primary().objectId()); var neighbor = createRuntimeFixture(_environment.observer().objectId()))
		{
			runtime.materialization().materialize(runtime.profileId()); neighbor.materialization().materialize(neighbor.profileId());
			try (var action = runtime.materialization().tryAcquireAction(runtime.profileId()).orElseThrow(); var other = neighbor.materialization().tryAcquireAction(neighbor.profileId()).orElseThrow())
			{
				final Player bad = action.player(); final Player good = other.player(); good.setSitting(true);
				for (Class<?> managerClass : List.of(AutoPlayTaskManager.class, AutoUseTaskManager.class))
				{
					final Field policiesField = managerClass.getDeclaredField("PHANTOM_POLICIES"); policiesField.setAccessible(true);
					final var policies = (Map<Player, AutoPlayTaskManager.PhantomPolicy>) policiesField.get(null);
					final var visited = new AtomicInteger();
					final var closed = new AtomicInteger();
					final Class<?> coupledClass = managerClass == AutoPlayTaskManager.class ? AutoUseTaskManager.class : AutoPlayTaskManager.class;
					final Field coupledPoliciesField = coupledClass.getDeclaredField("PHANTOM_POLICIES"); coupledPoliciesField.setAccessible(true);
					final var coupledPolicies = (Map<Player, AutoPlayTaskManager.PhantomPolicy>) coupledPoliciesField.get(null);
					final Field poolsField = managerClass.getDeclaredField("POOLS"); poolsField.setAccessible(true);
					final Field coupledPoolsField = coupledClass.getDeclaredField("POOLS"); coupledPoolsField.setAccessible(true);
					final var pools = (Set<Set<Player>>) poolsField.get(null); final var coupledPools = (Set<Set<Player>>) coupledPoolsField.get(null);
					final Set<Player> pool = java.util.concurrent.ConcurrentHashMap.newKeySet(); pool.addAll(List.of(bad, good));
					final Set<Player> coupledPool = java.util.concurrent.ConcurrentHashMap.newKeySet(); coupledPool.addAll(List.of(bad, good));
					pools.add(pool); coupledPools.add(coupledPool);
					policies.put(bad, new AutoPlayTaskManager.PhantomPolicy() { @Override public AutoPlayTaskManager.TickLease acquire(Player actor) { throw new InjectedFailure(); } @Override public boolean permitsTarget(Creature target) { return false; } });
					policies.put(good, new AutoPlayTaskManager.PhantomPolicy() { @Override public AutoPlayTaskManager.TickLease acquire(Player actor) { visited.incrementAndGet(); return closed::incrementAndGet; } @Override public boolean permitsTarget(Creature target) { return false; } });
					coupledPolicies.put(bad, policies.get(bad)); coupledPolicies.put(good, policies.get(good));
					try
					{
						bad.setAutoPlaying(true);
						final Class<?> taskClass = Arrays.stream(managerClass.getDeclaredClasses()).filter(type -> type.getSimpleName().equals(managerClass == AutoPlayTaskManager.class ? "AutoPlay" : "AutoUse")).findFirst().orElseThrow();
						final var constructor = taskClass.getDeclaredConstructor(managerClass, Set.class); constructor.setAccessible(true);
						final Object manager = managerClass.getMethod("getInstance").invoke(null);
						((Runnable) constructor.newInstance(manager, new java.util.LinkedHashSet<>(List.of(bad, good)))).run();
						PhantomAssertions.assertEquals(1, visited.get(), "Actor exception skipped neighbor in " + managerClass.getSimpleName());
						PhantomAssertions.assertFalse(bad.isAutoPlaying(), "Actor exception left its coupled native AutoPlay running in " + managerClass.getSimpleName());
						PhantomAssertions.assertFalse(coupledPolicies.containsKey(bad) || coupledPool.contains(bad), "Actor exception left its coupled native pool registered.");
						((Runnable) constructor.newInstance(manager, new java.util.LinkedHashSet<>(pool))).run();
						PhantomAssertions.assertEquals(2, visited.get(), "Neighbor did not reach the next native tick.");
						PhantomAssertions.assertEquals(2, closed.get(), "Neighbor native tick leaked its lease.");
					}
					finally
					{
						bad.setAutoPlaying(false); policies.remove(bad); policies.remove(good); coupledPolicies.remove(bad); coupledPolicies.remove(good);
						pool.addAll(List.of(bad, good)); coupledPool.addAll(List.of(bad, good));
						pools.remove(pool); coupledPools.remove(coupledPool);
						PhantomAssertions.assertFalse(pools.stream().anyMatch(value -> value == pool) || coupledPools.stream().anyMatch(value -> value == coupledPool), "Fault fixture retained its native pool registration.");
					}
				}
				good.setSitting(false);
			}
		}
	}

	private void testNativeRefreshLockOrder() throws Exception
	{
		try (var runtime = createRuntimeFixture(_environment.primary().objectId()))
		{
			PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, runtime.materialization().materialize(runtime.profileId()).status(), "Lock fixture did not materialize.");
			final Player player;
			final PlayerNativeWork.Owner nativeOwner;
			final long epoch;
			try (var action = runtime.materialization().tryAcquireAction(runtime.profileId()).orElseThrow()) { player = action.player(); player.getStatus().stopHpMpRegeneration(); nativeOwner = player.getNativeWorkOwner(); epoch = runtime.materialization().find(runtime.profileId()).orElseThrow().materializedAtNanos(); }
			PhantomAssertions.assertTrue(nativeOwner != null && nativeOwner.player() == player && nativeOwner.isCurrent() && nativeOwner.epoch() == epoch && PlayerNativeWork.current(nativeOwner) == null, "Lock fixture STORE did not retain exact lifetime outside ActionLease.");
			player.storeMe();
			PhantomAssertions.assertTrue(player.getNativeWorkOwner() == nativeOwner && nativeOwner.isCurrent() && nativeOwner.epoch() == runtime.materialization().find(runtime.profileId()).orElseThrow().materializedAtNanos(), "Lock fixture STORE changed native Player owner/epoch.");
			final var loaded = runtime.transaction().load(runtime.profileId());
			final Method refresh = PhantomBackgroundService.class.getDeclaredMethod("refreshNativeVitals", long.class, Player.class, Result.class, StringBuilder.class); refresh.setAccessible(true);
			final var started = new CountDownLatch(1); final var failure = new AtomicReference<Throwable>();
			final Thread worker = Thread.ofPlatform().daemon().unstarted(() -> { started.countDown(); try { refresh.invoke(runtime.background(), runtime.profileId(), player, loaded, new StringBuilder()); } catch (Throwable exception) { failure.set(exception); } });
			boolean statusHeld = false;
			boolean blocked = false;
			synchronized (player)
			{
				worker.start(); started.await(1, TimeUnit.SECONDS);
				final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
				while (worker.isAlive() && (worker.getState() != Thread.State.BLOCKED) && (System.nanoTime() < deadline)) { Thread.sleep(5); }
				blocked = worker.getState() == Thread.State.BLOCKED;
				final var info = java.lang.management.ManagementFactory.getThreadMXBean().getThreadInfo(new long[] { worker.threadId() }, true, false)[0];
				statusHeld = info != null && Arrays.stream(info.getLockedMonitors()).anyMatch(monitor -> monitor.getIdentityHashCode() == System.identityHashCode(player.getStatus()));
			}
			worker.join(5000);
			PhantomAssertions.assertTrue(blocked && !worker.isAlive() && failure.get() == null, "Native refresh latch did not reach and finish store: " + failure.get());
			PhantomAssertions.assertFalse(statusHeld, "Native refresh waited for player while owning status (reverse store lock order).");
		}
	}

	private void testInstantSelfHealIsNotAutoBuff()
	{
		final var heal = SkillData.getInstance().getSkill(58, 1);
		final var dash = SkillData.getInstance().getSkill(4, 1);
		PhantomAssertions.assertTrue((heal != null) && (dash != null), "Native self-heal and continuous self-buff templates are missing.");
		PhantomAssertions.assertFalse(PhantomVisibleAutoPlay.autoBuffEligible(heal), "Instant Elemental Heal would be recast at full HP because AutoUse has no persistent buff to detect.");
		PhantomAssertions.assertTrue(PhantomVisibleAutoPlay.autoBuffEligible(dash), "Continuous native self-buff was excluded from AutoUse.");
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
			final Position canonicalAnchor = canonicalAnchorPosition(anchor, 0);
			try (Connection connection = DatabaseFactory.getConnection();
				PreparedStatement statement = connection.prepareStatement("UPDATE characters SET classid=?,base_class=?,race=?,level=85,exp=?,x=?,y=?,z=?,heading=? WHERE charId=?"))
			{
				statement.setInt(1, selection.playerClass().getId());
				statement.setInt(2, selection.playerClass().getId());
				statement.setInt(3, selection.playerClass().getRace().ordinal());
				statement.setLong(4, ExperienceData.getInstance().getExpForLevel(85));
				statement.setInt(5, canonicalAnchor.x());
				statement.setInt(6, canonicalAnchor.y());
				statement.setInt(7, canonicalAnchor.z());
				statement.setInt(8, canonicalAnchor.heading());
				statement.setInt(9, objectId);
				PhantomAssertions.assertEquals(1, statement.executeUpdate(), "Could not configure a real supported Player class.");
			}
			player = Player.load(objectId);
			PhantomAssertions.assertTrue(player != null, "Real Player fixture could not be loaded.");
			PhantomAssertions.assertEquals(canonicalAnchor.x(), player.getX(), "Naturally loaded production fixture X differs.");
			PhantomAssertions.assertEquals(canonicalAnchor.y(), player.getY(), "Naturally loaded production fixture Y differs.");
			PhantomAssertions.assertEquals(canonicalAnchor.z(), player.getZ(), "Naturally loaded production fixture Z differs.");
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
		return openProductionPlayerFixture(productionFarmSelection().anchor());
	}

	private ProductionPlayerFixture openProductionPlayerFixture(PhantomTopologyAnchor initialAnchor) throws Exception
	{
		return openProductionPlayerFixture(initialAnchor, 85);
	}

	private ProductionPlayerFixture openProductionPlayerFixture(PhantomTopologyAnchor initialAnchor, int level) throws Exception
	{
		return openProductionPlayerFixture(initialAnchor, level, null, productionFarmSelection());
	}

	private ProductionPlayerFixture openProductionPlayerFixture(PhantomTopologyAnchor initialAnchor, int level, PlayerClass basicAttackClass, ProductionFarmSelection farm) throws Exception
	{
		return openProductionPlayerFixture(initialAnchor, level, basicAttackClass, farm, _environment.primary().objectId());
	}

	private ProductionPlayerFixture openProductionPlayerFixture(PhantomTopologyAnchor initialAnchor, int level, PlayerClass basicAttackClass, ProductionFarmSelection farm, int objectId) throws Exception
	{
		final CapabilitySelection selection = basicAttackClass == null ? productionCapability(level) : null;
		final PlayerClass fixtureClass = selection == null ? basicAttackClass : selection.playerClass();
		PhantomAssertions.assertTrue(objectId == _environment.primary().objectId() || objectId == _environment.observer().objectId(), "Only the existing two owned TEST fixtures may be configured.");
		final Canonical original = canonical(objectId);
		final int originalBaseClass = (int) scalarLong("SELECT base_class FROM characters WHERE charId = ?", objectId);
		final Position canonicalInitial = canonicalAnchorPosition(initialAnchor, 0);
		Player player = null;
		try
		{
			try (Connection connection = DatabaseFactory.getConnection();
				PreparedStatement statement = connection.prepareStatement("UPDATE characters SET classid=?,base_class=?,race=?,level=?,exp=?,x=?,y=?,z=?,heading=0,online=0 WHERE charId=?"))
			{
				statement.setInt(1, fixtureClass.getId());
				statement.setInt(2, fixtureClass.getId());
				statement.setInt(3, fixtureClass.getRace().ordinal());
				statement.setInt(4, level);
				statement.setLong(5, ExperienceData.getInstance().getExpForLevel(level));
				statement.setInt(6, canonicalInitial.x());
				statement.setInt(7, canonicalInitial.y());
				statement.setInt(8, canonicalInitial.z());
				statement.setInt(9, objectId);
				PhantomAssertions.assertEquals(1, statement.executeUpdate(), "Could not configure the production loot Player.");
			}
			final Identity identity = new Identity(PRODUCTION_LOOT_UNBLOCK_SEED, objectId, 0, fixtureClass.getId(), fixtureClass.getRace().ordinal());
			ensureAutoGetSkills(identity, exactAutoGetSkills(identity, level));
			if (selection != null) { ensureAutoGetSkills(identity, List.of(new AutoGetSkill(selection.rule().actionSkill().skillId(), selection.rule().actionSkill().skillLevel()))); }
			player = Player.load(objectId);
			PhantomAssertions.assertTrue(player != null, "Production loot real Player could not be loaded.");
			PhantomAssertions.assertEquals(canonicalInitial.x(), player.getX(), "Naturally loaded production Player X differs.");
			PhantomAssertions.assertEquals(canonicalInitial.y(), player.getY(), "Naturally loaded production Player Y differs.");
			PhantomAssertions.assertEquals(canonicalInitial.z(), player.getZ(), "Naturally loaded production Player Z differs.");
			player.setCurrentHp(player.getMaxHp());
			player.setCurrentMp(player.getMaxMp());
			player.setCurrentCp(player.getMaxCp());
			if (selection != null) { PhantomAssertions.assertTrue((player.getKnownSkill(selection.rule().actionSkill().skillId()) != null) && (player.getKnownSkill(selection.rule().actionSkill().skillId()).getLevel() >= selection.rule().actionSkill().skillLevel()), "Production loot capability is not ready on the real Player."); }
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
		return productionCapability(85);
	}

	private CapabilitySelection productionCapability(int level)
	{
		final List<CapabilitySelection> selections = new ArrayList<>();
		for (PlayerClass playerClass : PlayerClass.values())
		{
			final CapabilityRule rule = _production.progression().capabilities(playerClass.getId()).stream().filter(candidate -> supportedCapability(candidate.capabilityKey()) && candidate.requiredEquipmentFamilies().isEmpty() && candidate.requiredItems().isEmpty() && !candidate.summonRequired() && !candidate.servitorRequired()).filter(candidate -> SkillTreeData.getInstance().getCompleteClassSkillTree(playerClass).values().stream().anyMatch(learn -> (learn.getSkillId() == candidate.actionSkill().skillId()) && (learn.getSkillLevel() == candidate.actionSkill().skillLevel()) && (learn.getGetLevel() <= level))).filter(candidate ->
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

	private ProductionTravelSelection productionTravelSelection()
	{
		final PhantomTopologyAnchor farm = productionFarmSelection().anchor();
		for (PhantomTopologyEdge edge : _production.topology().snapshot().edges().stream().filter(PhantomTopologyEdge::backgroundEligible).sorted(Comparator.comparing(PhantomTopologyEdge::id)).toList())
		{
			if (farm.id().equals(edge.toAnchorId()) && (edge.fromAnchorId() != null))
			{
				final PhantomTopologyAnchor departure = _production.topology().findAnchor(edge.fromAnchorId()).orElseThrow();
				final List<String> route = _production.topology().routeHint(departure.id(), farm.id()).orElseThrow().edgeIds();
				if (route.equals(List.of(edge.id())))
				{
					return new ProductionTravelSelection(departure, farm, edge);
				}
			}
			if (edge.bidirectional() && farm.id().equals(edge.fromAnchorId()) && (edge.toAnchorId() != null))
			{
				final PhantomTopologyAnchor departure = _production.topology().findAnchor(edge.toAnchorId()).orElseThrow();
				final List<String> route = _production.topology().routeHint(departure.id(), farm.id()).orElseThrow().edgeIds();
				if (route.equals(List.of(edge.id())))
				{
					return new ProductionTravelSelection(departure, farm, edge);
				}
			}
		}
		throw new AssertionError("Current production topology has no direct background route to " + farm.id());
	}

	private PhantomTopologyQuery malformedArrivalTopology(ProductionTravelSelection travel)
	{
		final PhantomTopologySnapshot snapshot = _production.topology().snapshot();
		final List<PhantomTopologyAnchor> anchors = snapshot.anchors().stream().map(anchor -> anchor.id().equals(travel.arrival().id()) ? new PhantomTopologyAnchor(anchor.id(), anchor.role(), anchor.nodeId(), anchor.point(), anchor.npcId(), anchor.mapRegionLocId(), 0, anchor.tags(), anchor.sourceRefs()) : anchor).toList();
		final PhantomTopologySnapshot malformed = PhantomTopologySnapshot.create(snapshot.schemaVersion(), snapshot.datasetId(), snapshot.datasetVersion(), snapshot.generation(), snapshot.nodes(), anchors, snapshot.edges(), _production.topologyBackend(), PhantomTopologyPolicy.productionDefaults());
		return new PhantomTopologyQuery(malformed, _production.topologyBackend(), new PhantomTopologyMetrics());
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
		PhantomAssertions.assertEquals(canonicalAnchorPosition(arrival, state.position().heading()), state.position(), "Completed travel did not commit the canonical geodata position.");

		final PhantomTopologyEdge doorEdge = _production.topology().snapshot().edges().stream().filter(candidate -> (candidate.doorId() != null) && (candidate.fromAnchorId() != null) && (candidate.toAnchorId() != null)).findFirst().orElseThrow();
		final PhantomTopologyQuery closedTopology = new PhantomTopologyQuery(_production.topology().snapshot(), new ClosedDoorBackend(_production.topologyBackend(), doorEdge.doorId()), new PhantomTopologyMetrics());
		final L2jPhantomBackgroundAuthority closedAuthority = _production.authority(closedTopology);
		final PhantomTopologyAnchor closedDeparture = closedTopology.findAnchor(doorEdge.fromAnchorId()).orElseThrow();
		final PhantomGoal closedGoal = goal(targetNpcId, doorEdge.toAnchorId());
		final PhantomBackgroundState closedBase = productionState(productionTravelSelection().departure(), closedAuthority.hashes());
		final PhantomTopologyPoint closedPoint = closedDeparture.point();
		final Position closedPosition = new Position(closedPoint.instanceId(), closedPoint.x(), closedPoint.y(), closedPoint.z(), 0, closedDeparture.id());
		final PhantomBackgroundState closedState = closedBase.after(closedBase.progress(), closedBase.vitals(), closedPosition, closedBase.inventory(), closedBase.autoGetSkills(), closedBase.clock(), closedBase.receipt());
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

	private void testAcquisitionBackgroundParity(PhantomAcquisitionCatalog.Method method)
	{
		final AcquisitionParityFixture fixture = acquisitionParityFixture(method);
		final BatchResult repeated = new PhantomBackgroundModel().evaluate(fixture.request());
		PhantomAssertions.assertEquals(fixture.result(), repeated, "Authoritative acquisition background replay changed for " + method);
		PhantomAssertions.assertTrue(fixture.result().acquisitionTargetDelta() > 0, "Authoritative acquisition target did not produce deterministic progress for " + method);
		PhantomAssertions.assertEquals(fixture.result().acquisitionTargetDelta(), fixture.result().inventoryDelta().itemDeltas().getOrDefault(fixture.source().itemId(), 0L), "Acquisition target progress is not backed by the committed item delta for " + method);
		PhantomAssertions.assertEquals(1L, fixture.input().target().drops().stream().filter(drop -> drop.origin() == DropOrigin.ACQUISITION_TARGET).count(), "Authoritative source did not identify exactly one acquisition target fact.");
		PhantomAssertions.assertTrue(fixture.input().target().drops().stream().filter(drop -> drop.origin() != DropOrigin.ACQUISITION_TARGET).allMatch(drop -> drop.origin() == DropOrigin.INCIDENTAL_DEATH_DROP), "Incidental death drops were not kept separate from acquisition progress.");
		PhantomAssertions.assertEquals(method == PhantomAcquisitionCatalog.Method.SPOIL_SWEEP ? BatchMode.ACQUISITION_SPOIL_SWEEP : BatchMode.ACQUISITION_DEATH_DROP, fixture.request().mode(), "Background acquisition mode changed.");
	}

	private void testAcquisitionBackgroundControls()
	{
		final AcquisitionParityFixture spoil = acquisitionParityFixture(PhantomAcquisitionCatalog.Method.SPOIL_SWEEP);
		final List<AutoGetSkill> falseLedger = List.of(new AutoGetSkill(spoil.source().sweepSkillId(), spoil.source().sweepSkillLevel()), new AutoGetSkill(spoil.source().spoilSkillId(), spoil.source().spoilSkillLevel())).stream().sorted(Comparator.comparingInt(AutoGetSkill::skillId)).toList();
		final PhantomBackgroundState missingCapability = acquisitionParityState(spoil.source(), falseLedger, spoil.state().inventory(), spoil.state().combat(), spoil.state().vitals());
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> _production.authority().acquisitionInput(missingCapability, spoil.source(), Map.of()), "Auto-get evidence was incorrectly treated as a learned-skill ledger.");
		final var restored = _production.authority().acquisitionInput(missingCapability, spoil.source(), Map.of(spoil.source().spoilSkillId(), spoil.source().spoilSkillLevel(), spoil.source().sweepSkillId(), spoil.source().sweepSkillLevel()));
		PhantomAssertions.assertEquals(spoil.source().npcId(), restored.target().npcId(), "Exact learned skill evidence did not restore the authoritative target.");
		PhantomAssertions.assertEquals(1L, restored.target().drops().stream().filter(drop -> (drop.origin() == DropOrigin.ACQUISITION_TARGET) && (drop.itemId() == spoil.source().itemId())).count(), "Exact learned skill evidence did not restore background eligibility.");
		final BatchRequest ineligible = new BatchRequest(missingCapability, spoil.input().target(), spoil.input().rewardPolicy(), spoil.input().deathPolicy(), spoil.input().experienceTable(), spoil.input().levelForExperience(), false, BatchMode.ACQUISITION_SPOIL_SWEEP, spoil.source().itemId(), 1, false);
		final BatchResult rejected = new PhantomBackgroundModel().evaluate(ineligible);
		PhantomAssertions.assertEquals(PhantomBackgroundModel.ResultReason.ACQUISITION_INELIGIBLE, rejected.reason(), "Missing spoil capability did not fail closed.");
		PhantomAssertions.assertEquals(0L, rejected.acquisitionTargetDelta(), "Missing spoil capability produced progress.");
		PhantomAssertions.assertTrue(rejected.inventoryDelta().itemDeltas().isEmpty(), "Missing spoil capability produced item mutations.");

		final InventoryFacts full = new InventoryFacts(List.of(spoil.source().itemId()), List.of(), "acquisition-capacity", 0, 0, 0, 0);
		final PhantomBackgroundState capacityState = acquisitionParityState(spoil.source(), spoil.state().autoGetSkills(), full, spoil.state().combat(), spoil.state().vitals());
		final BatchRequest capacityRequest = new BatchRequest(capacityState, spoil.input().target(), spoil.input().rewardPolicy(), spoil.input().deathPolicy(), spoil.input().experienceTable(), spoil.input().levelForExperience(), false, BatchMode.ACQUISITION_SPOIL_SWEEP, spoil.source().itemId(), 1, true);
		final BatchResult capacity = new PhantomBackgroundModel().evaluate(capacityRequest);
		PhantomAssertions.assertTrue((capacity.reason() == PhantomBackgroundModel.ResultReason.SLOT_CAPACITY) || (capacity.reason() == PhantomBackgroundModel.ResultReason.WEIGHT_CAPACITY), "Full inventory did not reject acquisition output.");
		PhantomAssertions.assertEquals(0L, capacity.acquisitionTargetDelta(), "Rejected capacity output advanced acquisition progress.");
		PhantomAssertions.assertTrue(capacity.inventoryDelta().itemDeltas().isEmpty(), "Rejected capacity output mutated inventory.");

		final CombatFacts vulnerable = new CombatFacts(ModelKind.MELEE, 1000, 1000, 1, 1, 1000, 1000, 0, 0, 1, 1, 0, 1, 1, 1, 1);
		final Vitals oneHit = new Vitals(1, 1, 100, 100, 10, 10);
		final PhantomBackgroundState deathState = acquisitionParityState(spoil.source(), spoil.state().autoGetSkills(), spoil.state().inventory(), vulnerable, oneHit);
		final Target lethal = new Target(spoil.input().target().npcId(), spoil.input().target().level(), true, spoil.input().target().maximumHp(), spoil.input().target().maximumMp(), 1_000_000, 1_000_000, spoil.input().target().physicalDefense(), spoil.input().target().magicDefense(), spoil.input().target().attackSpeed(), spoil.input().target().castSpeed(), spoil.input().target().baseExperience(), spoil.input().target().baseSkillPoints(), spoil.input().target().drops(), spoil.input().target().maximumRandomDropOccurrences());
		final BatchResult death = new PhantomBackgroundModel().evaluate(new BatchRequest(deathState, lethal, spoil.input().rewardPolicy(), spoil.input().deathPolicy(), spoil.input().experienceTable(), spoil.input().levelForExperience(), false, BatchMode.ACQUISITION_SPOIL_SWEEP, spoil.source().itemId(), 1, true));
		PhantomAssertions.assertEquals(PhantomBackgroundModel.ResultReason.DEAD, death.reason(), "Background acquisition ignored authoritative death control.");
		PhantomAssertions.assertTrue(death.dead(), "Background acquisition death result is not terminal.");
	}

	private void testAcquisitionOrdinaryRegression()
	{
		final AcquisitionParityFixture fixture = acquisitionParityFixture(PhantomAcquisitionCatalog.Method.DEATH_DROP);
		final BatchRequest ordinary = new BatchRequest(fixture.state(), fixture.input().target(), fixture.input().rewardPolicy(), fixture.input().deathPolicy(), fixture.input().experienceTable(), fixture.input().levelForExperience(), false);
		final BatchRequest explicit = new BatchRequest(fixture.state(), fixture.input().target(), fixture.input().rewardPolicy(), fixture.input().deathPolicy(), fixture.input().experienceTable(), fixture.input().levelForExperience(), false, BatchMode.ORDINARY_DEATH_DROP, 0, 0, true);
		PhantomAssertions.assertEquals(new PhantomBackgroundModel().evaluate(ordinary), new PhantomBackgroundModel().evaluate(explicit), "Goal 015 ordinary death-drop behavior changed.");
	}

	private void testConfiguredDropRateRetainsPlayerMultipliers()
	{
		final AcquisitionParityFixture fixture = acquisitionParityFixture(PhantomAcquisitionCatalog.Method.DEATH_DROP);
		final int itemId = fixture.source().itemId();
		final Float priorAmount = RatesConfig.RATE_DROP_AMOUNT_BY_ID.put(itemId, 7f);
		final Float priorChance = RatesConfig.RATE_DROP_CHANCE_BY_ID.put(itemId, 11f);
		try
		{
			final CombatFacts baseline = fixture.state().combat();
			final CombatFacts rated = new CombatFacts(baseline.modelKind(), baseline.physicalOffense(), baseline.magicOffense(), baseline.physicalDefense(), baseline.magicDefense(), baseline.attackSpeed(), baseline.castSpeed(), baseline.hpRegenPerSecond(), baseline.mpRegenPerSecond(), 2, 3, baseline.servitorExperienceMultiplier(), 3, 5, 1, baseline.normalMonsterExperienceLossMultiplier());
			final PhantomBackgroundState state = acquisitionParityState(fixture.source(), fixture.state().autoGetSkills(), fixture.state().inventory(), rated, fixture.state().vitals());
			final PhantomBackgroundAuthority.FarmInput input = _production.authority().acquisitionInput(state, fixture.source(), Map.of());
			final Drop drop = input.target().drops().stream().filter(candidate -> (candidate.itemId() == itemId) && (candidate.origin() == DropOrigin.ACQUISITION_TARGET)).findFirst().orElseThrow();
			final Drop baselineDrop = fixture.input().target().drops().stream().filter(candidate -> (candidate.itemId() == itemId) && (candidate.origin() == DropOrigin.ACQUISITION_TARGET)).findFirst().orElseThrow();
			PhantomAssertions.assertEquals(baselineDrop.rawGroupChance(), drop.rawGroupChance(), "Configured amount rate changed raw group chance.");
			PhantomAssertions.assertEquals(baselineDrop.rawItemChance(), drop.rawItemChance(), "Configured amount rate changed raw item chance.");
			PhantomAssertions.assertEquals(11d, drop.configuredChanceMultiplier(), "Configured item chance override was not preserved as evidence.");
			PhantomAssertions.assertEquals(33d, drop.chanceMultiplier(), "Configured item chance override lost the Player drop-chance multiplier.");
			PhantomAssertions.assertEquals(35d, drop.amountMultiplier(), "Configured item amount override lost the Player drop-amount multiplier.");
		}
		finally
		{
			restoreRate(RatesConfig.RATE_DROP_AMOUNT_BY_ID, itemId, priorAmount);
			restoreRate(RatesConfig.RATE_DROP_CHANCE_BY_ID, itemId, priorChance);
		}
	}

	private static void restoreRate(Map<Integer, Float> rates, int itemId, Float prior)
	{
		if (prior == null)
		{
			rates.remove(itemId);
		}
		else
		{
			rates.put(itemId, prior);
		}
	}

	private AcquisitionParityFixture acquisitionParityFixture(PhantomAcquisitionCatalog.Method method)
	{
		final float originalDeathChance = RatesConfig.RATE_DEATH_DROP_CHANCE_MULTIPLIER;
		final float originalSpoilChance = RatesConfig.RATE_SPOIL_DROP_CHANCE_MULTIPLIER;
		RatesConfig.RATE_DEATH_DROP_CHANCE_MULTIPLIER = 1_000_000;
		RatesConfig.RATE_SPOIL_DROP_CHANCE_MULTIPLIER = 1_000_000;
		try
		{
		final PhantomAcquisitionCatalog catalog = PhantomAcquisitionCatalog.load(Path.of("data/phantoms/acquisition/high-five-acquisition-v1.xml"));
		final PhantomAcquisitionSourcePlanner planner = new PhantomAcquisitionSourcePlanner(catalog, _production.knowledge(), _production.topology(), _production.progression());
		final Map<Integer, List<DropFact>> byItem = method == PhantomAcquisitionCatalog.Method.DEATH_DROP ? _production.knowledge().snapshot().dropSourcesByItem() : _production.knowledge().snapshot().spoilSourcesByItem();
		final List<Integer> items = byItem.values().stream().flatMap(List::stream).sorted(Comparator.comparingDouble(DropFact::rawItemChance).reversed().thenComparingInt(DropFact::itemId).thenComparingInt(DropFact::npcId)).map(DropFact::itemId).distinct().toList();
		int rankedCount = 0;
		int authorityCount = 0;
		int mutatedCount = 0;
		String lastFailure = "none";
		for (int itemId : items)
		{
			final int classId = method == PhantomAcquisitionCatalog.Method.SPOIL_SWEEP ? 117 : 88;
			final Map<Integer, Integer> knownSkills = method == PhantomAcquisitionCatalog.Method.SPOIL_SWEEP ? Map.of(254, 11, 42, 1) : Map.of();
			final var request = new PhantomAcquisitionSourcePlanner.Request(1, itemId, 1, PhantomActivityState.BACKGROUND, classId, 85, Map.of(), knownSkills, Set.of(method), method, "", Map.of(), 0);
			final var planned = planner.plan(request);
			rankedCount += planned.ranked().size();
			for (var ranked : planned.ranked())
			{
				final Source source = ranked.source();
				final List<AutoGetSkill> skills = method == PhantomAcquisitionCatalog.Method.SPOIL_SWEEP ? List.of(new AutoGetSkill(source.sweepSkillId(), source.sweepSkillLevel()), new AutoGetSkill(source.spoilSkillId(), source.spoilSkillLevel())).stream().sorted(Comparator.comparingInt(AutoGetSkill::skillId)).toList() : List.of();
				final InventoryFacts inventory = new InventoryFacts(List.of(source.itemId()), List.of(), "acquisition-parity", 0, 1_000_000, 0, 100);
				final CombatFacts combat = new CombatFacts(ModelKind.MELEE, 1_000_000_000, 1_000_000_000, 1_000_000_000, 1_000_000_000, 1000, 1000, 1_000, 1_000, 1, 1, 0, 1, 1, 1, 1);
				final PhantomBackgroundState state = acquisitionParityState(source, skills, inventory, combat, new Vitals(1_000_000, 1_000_000, 1_000_000, 1_000_000, 1_000_000, 1_000_000));
				try
				{
					final PhantomBackgroundAuthority.FarmInput input = _production.authority().acquisitionInput(state, source, knownSkills);
					authorityCount++;
					final BatchMode mode = method == PhantomAcquisitionCatalog.Method.SPOIL_SWEEP ? BatchMode.ACQUISITION_SPOIL_SWEEP : BatchMode.ACQUISITION_DEATH_DROP;
					final BatchRequest batch = new BatchRequest(state, input.target(), input.rewardPolicy(), input.deathPolicy(), input.experienceTable(), input.levelForExperience(), false, mode, source.itemId(), 1, true);
					final BatchResult result = new PhantomBackgroundModel().evaluate(batch);
					if (result.mutated())
					{
						mutatedCount++;
					}
					if (result.acquisitionTargetDelta() > 0)
					{
						return new AcquisitionParityFixture(source, state, input, batch, result);
					}
				}
				catch (IllegalArgumentException | IllegalStateException failure)
				{
					lastFailure = failure.getClass().getSimpleName() + ':' + failure.getMessage();
				}
			}
		}
			throw new AssertionError("No deterministic authoritative background acquisition fixture was found for " + method + ": items=" + items.size() + ",ranked=" + rankedCount + ",authority=" + authorityCount + ",mutated=" + mutatedCount + ",lastFailure=" + lastFailure);
		}
		finally
		{
			RatesConfig.RATE_DEATH_DROP_CHANCE_MULTIPLIER = originalDeathChance;
			RatesConfig.RATE_SPOIL_DROP_CHANCE_MULTIPLIER = originalSpoilChance;
		}
	}

	private PhantomBackgroundState acquisitionParityState(Source source, List<AutoGetSkill> skills, InventoryFacts inventory, CombatFacts combat, Vitals vitals)
	{
		final PhantomTopologyAnchor anchor = _production.topology().findAnchor(source.anchorId()).orElseThrow();
		return new PhantomBackgroundState(State.READY, new Identity(ACQUISITION_SEED, Math.toIntExact(ACQUISITION_SEED), 0, source.method() == PhantomAcquisitionCatalog.Method.SPOIL_SWEEP ? 117 : 88, 0), new Progress(85, ExperienceData.getInstance().getExpForLevel(85), 0, 0), vitals, canonicalAnchorPosition(anchor, 0), combat, Loadout.none(), inventory, skills, new Clock(ACQUISITION_SEED, 0, 0), Receipt.empty(), _production.authority().hashes());
	}

	private void testAcquisitionPrecommitFaults() throws Exception
	{
		final List<FaultPoint> faults = List.of(FaultPoint.AFTER_PROFILE_LOCK, FaultPoint.AFTER_GOAL_LOCK, FaultPoint.AFTER_ACQUISITION_LOCK, FaultPoint.AFTER_BACKGROUND_LOCK, FaultPoint.AFTER_CANONICAL_WRITES, FaultPoint.AFTER_BACKGROUND_STATE_WRITE, FaultPoint.AFTER_GOAL_STATE_WRITE, FaultPoint.AFTER_ACQUISITION_STATE_WRITE, FaultPoint.BEFORE_OPERATION_COMMIT);
		for (FaultPoint fault : faults)
		{
			final PhantomBackgroundTransaction transaction = new PhantomBackgroundTransaction(DatabaseFactory::getConnection, allocator(new AtomicInteger()), point ->
			{
				if (point == fault)
				{
					throw new IllegalStateException("injected acquisition fault " + fault);
				}
			});
			try (AcquisitionAtomicFixture fixture = createAcquisitionAtomicFixture(transaction))
			{
				final AcquisitionAtomicSnapshot before = acquisitionAtomicSnapshot(fixture);
				PhantomAssertions.assertEquals(Status.BACKEND_FAILURE, transaction.execute(acquisitionCommand(fixture, fixture.goal(), fixture.goalRowVersion(), fixture.stateRowVersion(), fixture.ready().hashes())).status(), "Precommit fault did not reject at " + fault);
				PhantomAssertions.assertEquals(before, acquisitionAtomicSnapshot(fixture), "Precommit fault escaped the atomic rollback at " + fault);
			}
		}
	}

	private void testAcquisitionPostcommitRestart() throws Exception
	{
		final PhantomBackgroundTransaction uncertain = new PhantomBackgroundTransaction(DatabaseFactory::getConnection, allocator(new AtomicInteger()), point ->
		{
			if (point == FaultPoint.AFTER_OPERATION_COMMIT)
			{
				throw new IllegalStateException("injected postcommit acquisition fault");
			}
		});
		try (AcquisitionAtomicFixture fixture = createAcquisitionAtomicFixture(uncertain))
		{
			final PhantomBackgroundTransaction.Command command = acquisitionCommand(fixture, fixture.goal(), fixture.goalRowVersion(), fixture.stateRowVersion(), fixture.ready().hashes());
			PhantomAssertions.assertEquals(Status.COMMIT_OUTCOME_UNKNOWN, uncertain.execute(command).status(), "Postcommit acquisition fault was reported as a rollback.");
			final PhantomBackgroundTransaction restarted = new PhantomBackgroundTransaction();
			PhantomAssertions.assertEquals(Status.SUCCESS, restarted.reconcileVerifyPending(fixture.profileId(), fixture.characterObjectId()).status(), "Restart did not reconcile acquisition VERIFY_PENDING.");
			final AcquisitionAtomicSnapshot committed = acquisitionAtomicSnapshot(fixture);
			PhantomAssertions.assertEquals(fixture.baselineCount() + fixture.requiredAmount(), committed.itemCount(), "Committed acquisition item delta was not exact.");
			PhantomAssertions.assertEquals(fixture.baselineCount(), committed.acquisition().state().baselineCount(), "Acquisition baseline was rewritten after commit.");
			PhantomAssertions.assertEquals(fixture.requiredAmount(), committed.acquisition().state().progress(), "Acquisition progress was not derived from authoritative current amount.");
			PhantomAssertions.assertEquals(PhantomAcquisitionState.Status.COMPLETED, committed.acquisition().state().status(), "Acquisition state did not complete at the required amount.");
			PhantomAssertions.assertEquals(PhantomGoalStatus.COMPLETED, committed.goal().goal().status(), "Goal did not complete in the same transaction.");
			PhantomAssertions.assertEquals(Status.IDEMPOTENT, restarted.execute(command).status(), "Exact durable acquisition replay was not idempotent after restart.");
			PhantomAssertions.assertEquals(committed, acquisitionAtomicSnapshot(fixture), "Exact acquisition replay duplicated an item, Goal, or state mutation.");
		}
	}

	private void testAcquisitionAtomicGuards() throws Exception
	{
		try (AcquisitionAtomicFixture fixture = createAcquisitionAtomicFixture(new PhantomBackgroundTransaction()))
		{
			final Hashes staleHashes = new Hashes("knowledge-stale", fixture.ready().hashes().topology(), fixture.ready().hashes().progression(), fixture.ready().hashes().commerce());
			PhantomAssertions.assertEquals(Status.HASH_STALE, fixture.transaction().execute(acquisitionCommand(fixture, fixture.goal(), fixture.goalRowVersion(), fixture.stateRowVersion(), staleHashes)).status(), "Stale authority hash was admitted.");
			PhantomAssertions.assertEquals(Status.ACQUISITION_CONFLICT, fixture.transaction().execute(acquisitionCommand(fixture, fixture.goal(), fixture.goalRowVersion(), fixture.stateRowVersion() + 1, fixture.ready().hashes())).status(), "Stale acquisition rowVersion was admitted.");
			PhantomAssertions.assertEquals(Status.GOAL_STALE, fixture.transaction().execute(acquisitionCommand(fixture, fixture.goal(), fixture.goalRowVersion() + 1, fixture.stateRowVersion(), fixture.ready().hashes())).status(), "Stale acquisition Goal rowVersion was admitted.");
			final PhantomGoal changed = new PhantomGoal(fixture.goal().goalId(), fixture.goal().goalType(), fixture.goal().status(), fixture.goal().subject(), fixture.goal().target(), fixture.goal().requiredAmount(), fixture.goal().currentAmount(), fixture.goal().acquisitionMethod(), fixture.goal().validSources(), fixture.goal().selectedAnchor(), fixture.goal().purposeKey(), fixture.goal().priority(), fixture.goal().riskBudget(), fixture.goal().expenseBudget(), fixture.goal().deadlineEpochMillis(), fixture.goal().constraints(), fixture.goal().reasonKey(), fixture.goal().revision() + 1);
			PhantomAssertions.assertEquals(Status.GOAL_STALE, fixture.transaction().execute(acquisitionCommand(fixture, changed, fixture.goalRowVersion(), fixture.stateRowVersion(), fixture.ready().hashes())).status(), "Changed acquisition Goal identity was admitted.");
			PhantomAssertions.assertEquals(fixture.baselineCount(), acquisitionAtomicSnapshot(fixture).itemCount(), "Rejected acquisition guard mutated canonical inventory.");
		}
	}

	private void testAcquisitionRepeatedTransitions() throws Exception
	{
		try (AcquisitionAtomicFixture fixture = createAcquisitionAtomicFixture(new PhantomBackgroundTransaction()))
		{
			final AcquisitionAtomicSnapshot before = acquisitionAtomicSnapshot(fixture);
			for (int index = 0; index < 20; index++)
			{
				PhantomAssertions.assertEquals(Status.SUCCESS, fixture.transaction().markMaterialized(fixture.profileId(), fixture.characterObjectId()).status(), "Acquisition materialization transition failed at " + index);
				PhantomAssertions.assertEquals(Status.SUCCESS, fixture.transaction().abortMaterialization(fixture.profileId(), fixture.characterObjectId()).status(), "Acquisition abort transition failed at " + index);
			}
			PhantomAssertions.assertEquals(before, acquisitionAtomicSnapshot(fixture), "Repeated active/background transitions changed item, Goal, or acquisition state.");
		}
	}

	private void testAcquisitionEligibilityAndRollback() throws Exception
	{
		try (AcquisitionAtomicFixture fixture = createAcquisitionAtomicFixture(new PhantomBackgroundTransaction(), PhantomAcquisitionCatalog.Method.SPOIL_SWEEP))
		{
			final Source source = fixture.acquisition().load(fixture.profileId()).orElseThrow().state().selectedSource();
			upsertSkill(fixture.characterObjectId(), fixture.ready().identity().classIndex(), source.spoilSkillId(), source.spoilSkillLevel());
			upsertSkill(fixture.characterObjectId(), fixture.ready().identity().classIndex(), source.sweepSkillId(), source.sweepSkillLevel());
			final var eligible = fixture.transaction().readAcquisitionEligibility(fixture.profileId(), fixture.characterObjectId(), fixture.ready().identity().classIndex(), fixture.ready().identity().activeClassId(), List.of(source.spoilSkillId(), source.sweepSkillId()), "d".repeat(64), fixture.ready().hashes());
			PhantomAssertions.assertEquals(Status.SUCCESS, eligible.status(), "Exact learned skill rows were not readable through the bounded eligibility boundary.");
			PhantomAssertions.assertEquals(Map.of(source.spoilSkillId(), source.spoilSkillLevel(), source.sweepSkillId(), source.sweepSkillLevel()), eligible.snapshot().skillLevels(), "Eligibility snapshot changed exact learned levels.");
			final AcquisitionAtomicSnapshot before = acquisitionAtomicSnapshot(fixture);
			deleteSkill(fixture.characterObjectId(), fixture.ready().identity().classIndex(), source.sweepSkillId());
			PhantomAssertions.assertEquals(Status.PROGRESSION_CONFLICT, fixture.transaction().execute(acquisitionCommand(fixture, fixture.goal(), fixture.goalRowVersion(), fixture.stateRowVersion(), fixture.ready().hashes())).status(), "Removed Sweep skill was admitted at the atomic mutation boundary.");
			PhantomAssertions.assertEquals(before, acquisitionAtomicSnapshot(fixture), "Eligibility drift escaped full item/background/Goal/acquisition rollback.");
		}

		final int subclassCharacterId = _environment.observer().objectId();
		try (Connection connection = DatabaseFactory.getConnection();
			PreparedStatement statement = connection.prepareStatement("INSERT INTO character_subclasses (charId,class_id,exp,sp,level,class_index) VALUES (?,117,?,0,85,1)"))
		{
			statement.setInt(1, subclassCharacterId);
			statement.setLong(2, ExperienceData.getInstance().getExpForLevel(85));
			statement.executeUpdate();
		}
		final Fixture subclass = createFixture(subclassCharacterId, null, 1, 117, 85, ExperienceData.getInstance().getExpForLevel(85), 0);
		try
		{
			upsertSkill(subclassCharacterId, 0, 254, 3);
			upsertSkill(subclassCharacterId, 1, 254, 11);
			upsertSkill(subclassCharacterId, 1, 42, 1);
			final var exactSubclass = subclass.transaction().readAcquisitionEligibility(subclass.profileId(), subclassCharacterId, 1, 117, List.of(254, 42), "d".repeat(64), subclass.ready().hashes());
			PhantomAssertions.assertEquals(Status.SUCCESS, exactSubclass.status(), "Subclass eligibility snapshot failed.");
			PhantomAssertions.assertEquals(Map.of(254, 11, 42, 1), exactSubclass.snapshot().skillLevels(), "Subclass eligibility leaked main-class skill rows.");
		}
		finally
		{
			subclass.close();
			try (Connection connection = DatabaseFactory.getConnection();
				PreparedStatement statement = connection.prepareStatement("DELETE FROM character_subclasses WHERE charId=? AND class_index=1"))
			{
				statement.setInt(1, subclassCharacterId);
				statement.executeUpdate();
			}
		}
	}

	private void testAcquisitionInventoryReadBoundary(PhantomTestContext context) throws Exception
	{
		try (AcquisitionAtomicFixture fixture = createAcquisitionAtomicFixture(new PhantomBackgroundTransaction()))
		{
			final AcquisitionAtomicSnapshot before = acquisitionAtomicSnapshot(fixture);
			final List<Integer> exactIds = List.of(57, 99999);
			final var result = fixture.transaction().readAcquisitionInventoryCounts(fixture.profileId(), fixture.characterObjectId(), fixture.ready().identity().classIndex(), fixture.ready().identity().activeClassId(), exactIds, fixture.ready().hashes());
			PhantomAssertions.assertEquals(Status.SUCCESS, result.status(), "Exact background inventory read failed.");
			PhantomAssertions.assertEquals(fixture.baselineCount(), result.snapshot().counts().get(57), "Background target item count was not canonical.");
			PhantomAssertions.assertEquals(0L, result.snapshot().counts().get(99999), "Absent background inventory item was not reported as zero.");
			PhantomAssertions.assertThrows(UnsupportedOperationException.class, () -> result.snapshot().counts().put(57, 1L), "Background inventory count map was mutable.");
			PhantomAssertions.assertEquals(Status.PROGRESSION_CONFLICT, fixture.transaction().readAcquisitionInventoryCounts(fixture.profileId(), fixture.characterObjectId(), fixture.ready().identity().classIndex(), fixture.ready().identity().activeClassId(), List.of(2, 1), fixture.ready().hashes()).status(), "Unsorted background inventory IDs were admitted.");
			PhantomAssertions.assertEquals(Status.PROGRESSION_CONFLICT, fixture.transaction().readAcquisitionInventoryCounts(fixture.profileId(), fixture.characterObjectId(), fixture.ready().identity().classIndex(), fixture.ready().identity().activeClassId(), java.util.stream.IntStream.rangeClosed(1, 129).boxed().toList(), fixture.ready().hashes()).status(), "129 background inventory IDs were admitted.");
			final PhantomBackgroundState.Hashes stale = new PhantomBackgroundState.Hashes("f".repeat(64), fixture.ready().hashes().topology(), fixture.ready().hashes().progression(), fixture.ready().hashes().commerce());
			PhantomAssertions.assertEquals(Status.PROGRESSION_CONFLICT, fixture.transaction().readAcquisitionInventoryCounts(fixture.profileId(), fixture.characterObjectId(), fixture.ready().identity().classIndex(), fixture.ready().identity().activeClassId(), exactIds, stale).status(), "Stale background inventory hash was admitted.");
			final PhantomBackgroundService service = new PhantomBackgroundService(_repository, fixture.goals(), PhantomIdentityLeaseRegistry.getInstance(), fixture.transaction(), _production.authority(), new PhantomBackgroundCompetitionRegistry(), noSignals(), () -> null);
			PhantomAssertions.assertTrue(service.acquisitionInventoryCounts(fixture.profileId(), fixture.ready(), exactIds).isEmpty(), "Background service admitted durable hashes stale against current authority.");
			PhantomAssertions.assertEquals(before, acquisitionAtomicSnapshot(fixture), "Background inventory read mutated item/background/Goal/acquisition state.");
		}

		final int characterObjectId = _environment.primary().objectId();
		final PhantomAcquisitionCatalog catalog = PhantomAcquisitionCatalog.load(context.moduleRoot().resolve("dist/game/data/phantoms/acquisition/high-five-acquisition-v1.xml"));
		final PhantomAcquisitionRecipePlanner recipePlanner = new PhantomAcquisitionRecipePlanner(_production.knowledge(), catalog.limits());
		RecipeFact selectedRecipe = null;
		int selectedIngredientId = 0;
		long selectedZeroDeficit = 0;
		for (RecipeFact recipe : _production.knowledge().snapshot().recipeByListId().values().stream().sorted(Comparator.comparingInt(RecipeFact::recipeListId)).toList())
		{
			final var probe = recipePlanner.probe(recipe.productItemId(), 1);
			final var empty = recipePlanner.plan(recipe.productItemId(), 1, Map.of(), new CraftEvidence(0, 0, false));
			if (!probe.successful() || !empty.planned() || (scalarLong("SELECT COALESCE(SUM(count),0) FROM items WHERE owner_id=? AND item_id=" + recipe.productItemId() + " AND loc='INVENTORY'", characterObjectId) != 0))
			{
				continue;
			}
			final long zeroDeficit = empty.plan().deficits().stream().mapToLong(deficit -> deficit.count()).sum();
			for (int ingredientId : probe.exactItemIds())
			{
				final var partial = recipePlanner.plan(recipe.productItemId(), 1, Map.of(ingredientId, 1L), new CraftEvidence(0, 0, false));
				if (partial.planned() && partial.plan().nodes().stream().anyMatch(node -> (node.itemId() == ingredientId) && (node.inventoryUsed() > 0)) && (partial.plan().deficits().stream().mapToLong(deficit -> deficit.count()).sum() < zeroDeficit))
				{
					selectedRecipe = recipe;
					selectedIngredientId = ingredientId;
					selectedZeroDeficit = zeroDeficit;
					break;
				}
			}
			if (selectedRecipe != null)
			{
				break;
			}
		}
		PhantomAssertions.assertTrue(selectedRecipe != null, "No bounded production recipe supports partial background ingredient evidence.");
		final RecipeFact recipe = selectedRecipe;
		final int ingredientItemId = selectedIngredientId;
		final long zeroDeficit = selectedZeroDeficit;
		final List<Integer> exactIds = recipePlanner.probe(recipe.productItemId(), 1).exactItemIds();
		final int ingredientObjectId = IdManager.getInstance().getNextId();
		try (Connection connection = DatabaseFactory.getConnection();
			PreparedStatement statement = connection.prepareStatement("INSERT INTO items (owner_id,item_id,count,loc,loc_data,enchant_level,object_id,custom_type1,custom_type2,mana_left,time) VALUES (?,?,1,'INVENTORY',0,0,?,0,0,-1,-1)"))
		{
			statement.setInt(1, characterObjectId);
			statement.setInt(2, ingredientItemId);
			statement.setInt(3, ingredientObjectId);
			statement.executeUpdate();
		}
		final Canonical canonical = canonical(characterObjectId);
		final PhantomProfile profile = _repository.create(characterObjectId);
		PhantomCombatService combat = null;
		PhantomAcquisitionService acquisition = null;
		try
		{
			final PhantomGoal goal = new PhantomGoal(21, PhantomAcquisitionGoalSpec.GOAL_TYPE, PhantomGoalStatus.ACTIVE, new PhantomDomainRef("profile", "self"), new PhantomDomainRef("item", Integer.toString(recipe.productItemId())), 1, 0, PhantomAcquisitionCatalog.Method.RECIPE_PREPARATION.key(), List.of(new PhantomDomainRef(PhantomAcquisitionGoalSpec.SOURCE_NAMESPACE, PhantomAcquisitionCatalog.Method.RECIPE_PREPARATION.key())), null, PhantomAcquisitionGoalSpec.PURPOSE_KEY, 500, 0, 0, 0, Map.of(PhantomAcquisitionGoalSpec.BASELINE_CONSTRAINT, 0L, PhantomAcquisitionGoalSpec.MAXIMUM_SWITCHES_CONSTRAINT, 4L), "acquisition.background.recipe.inventory.test", 0);
			final PhantomGoalStateStore goals = new PhantomGoalStateStore(_repository);
			goals.insert(profile.profileId(), goal);
			final Identity identity = new Identity(profile.profileId(), characterObjectId, 0, canonical.classId(), canonical.race());
			final List<AutoGetSkill> skills = exactAutoGetSkills(identity, canonical.level());
			ensureAutoGetSkills(identity, skills);
			final Hashes hashes = _production.authority().hashes();
			final PhantomBackgroundTransaction transaction = new PhantomBackgroundTransaction();
			final PhantomBackgroundState materialized = new PhantomBackgroundState(State.MATERIALIZED, identity, new Progress(canonical.level(), canonical.experience(), canonical.skillPoints(), canonical.experienceBeforeDeath()), new Vitals(canonical.currentHp(), canonical.maximumHp(), canonical.currentMp(), canonical.maximumMp(), canonical.currentCp(), canonical.maximumCp()), new Position(0, canonical.x(), canonical.y(), canonical.z(), canonical.heading(), _production.topology().snapshot().anchors().getFirst().id()), combat(ModelKind.MELEE, 1, 1, 100), Loadout.none(), new InventoryFacts(List.of(57, ingredientItemId, recipe.productItemId()).stream().distinct().sorted().toList(), List.of(), "", 0, 1_000_000, 0, 100), skills, new Clock(ACQUISITION_SEED, 0, 0), Receipt.empty(), hashes);
			final Result captured = captureTestBaseline(transaction, materialized, goal);
			PhantomAssertions.assertEquals(Status.SUCCESS, captured.status(), "Background recipe service fixture capture failed.");
			final PhantomBackgroundState backgroundBefore = captured.state();
			final PhantomBackgroundService background = new PhantomBackgroundService(_repository, goals, PhantomIdentityLeaseRegistry.getInstance(), transaction, _production.authority(), new PhantomBackgroundCompetitionRegistry(), noSignals(), () -> null);
			combat = new PhantomCombatService(PhantomCombatBackend.inert(), new PhantomCombatCapabilityResolver(_ -> List.of()), PhantomCombatPolicy.productionDefaults(1));
			combat.start();
			final PhantomAcquisitionStore store = new PhantomAcquisitionStore(_repository, goals);
			acquisition = new PhantomAcquisitionService(catalog, store, goals, new PhantomAcquisitionSourcePlanner(catalog, _production.knowledge(), _production.topology(), _production.progression()), _production.knowledge(), _production.topology(), _production.progression(), combat, background, new PhantomNavigationService(new PhantomMetrics()));
			PhantomAssertions.assertTrue(acquisition.start(), "Background recipe acquisition service did not start.");
			final PhantomAcquisitionService.OperationResult result = acquisition.plan(profile.profileId(), goal, PhantomActivityState.BACKGROUND, 1_000_000, 1, () -> false);
			PhantomAssertions.assertEquals(PhantomAcquisitionService.OperationStatus.SUCCESS, result.status(), "Background recipe service plan failed.");
			final PhantomAcquisitionState state = store.load(profile.profileId()).orElseThrow().state();
			PhantomAssertions.assertEquals(PhantomAcquisitionCatalog.Method.RECIPE_PREPARATION, state.selectedSource().method(), "Background recipe service selected a different method.");
			PhantomAssertions.assertEquals(exactIds, recipePlanner.probe(state.targetItemId(), state.requiredAmount()).exactItemIds(), "Background recipe service did not use the canonical exact probe set.");
			PhantomAssertions.assertTrue(state.recipePlan().nodes().stream().anyMatch(node -> (node.itemId() == ingredientItemId) && (node.inventoryUsed() > 0)), "Background DB ingredient evidence was not consumed by the final service plan.");
			PhantomAssertions.assertTrue(state.recipePlan().deficits().stream().mapToLong(deficit -> deficit.count()).sum() < zeroDeficit, "Background partial ingredients did not lower the final deficit.");
			PhantomAssertions.assertEquals(backgroundBefore, transaction.load(profile.profileId()).state(), "Background recipe planning mutated background state.");
			PhantomAssertions.assertEquals(1L, scalarLong("SELECT COALESCE(SUM(count),0) FROM items WHERE owner_id=? AND item_id=" + ingredientItemId + " AND loc='INVENTORY'", characterObjectId), "Background recipe planning consumed an ingredient.");
			PhantomAssertions.assertEquals(0L, scalarLong("SELECT COALESCE(SUM(count),0) FROM items WHERE owner_id=? AND item_id=" + recipe.productItemId() + " AND loc='INVENTORY'", characterObjectId), "Background recipe planning crafted the target item.");
			PhantomAssertions.assertEquals(0, acquisition.snapshot().externalClaims(), "Background recipe planning retained an active actor lease.");
		}
		finally
		{
			if (acquisition != null)
			{
				acquisition.beginStop();
				PhantomAssertions.assertTrue(acquisition.finishStop(), "Background recipe acquisition service retained claims.");
			}
			if (combat != null)
			{
				combat.beginStop();
				PhantomAssertions.assertTrue(combat.finishStop(), "Background recipe Combat service retained claims.");
			}
			_repository.find(profile.profileId()).ifPresent(current -> _repository.delete(current.profileId(), current.rowVersion()));
			restorePrimaryInventoryAndSkills(characterObjectId);
		}
	}

	private void testQuestCapBoundaries(PhantomTestContext context) throws Exception
	{
		final PhantomAcquisitionQuestCatalog catalog = PhantomAcquisitionQuestCatalog.load(context.moduleRoot().resolve("dist/game/data/phantoms/acquisition/high-five-quest-collection-v1.xml"), context.moduleRoot().resolve("dist/game/data/scripts"));
		PhantomAssertions.assertEquals(2, catalog.rules().size(), "Quest cap transaction route did not load exactly the curated Q00102/Q00152 rules.");
		for (Rule rule : catalog.rules())
		{
			testQuestCapCommit(catalog, rule, 1, true);
			testQuestCapCommit(catalog, rule, 2, false);
		}
		context.record("quest.capTransactionRules", catalog.rules().size());
		context.record("quest.capTransactionSeed", QUEST_CAP_SEED);
	}

	private void testQuestCapCommit(PhantomAcquisitionQuestCatalog catalog, Rule rule, long required, boolean completes) throws Exception
	{
		try (QuestCapFixture fixture = createQuestCapFixture(new PhantomBackgroundTransaction(), catalog, rule, required))
		{
			final BatchResult batch = questCapBatch(fixture.ready(), rule);
			PhantomAssertions.assertEquals(1L, batch.acquisitionTargetDelta(), "Real background model did not produce exact +1 at the quest cap boundary for " + rule.id() + ".");
			PhantomAssertions.assertEquals(1L, batch.inventoryDelta().itemDeltas().get(rule.questItemId()), "Real background model lost the exact curated quest item delta.");
			final PhantomBackgroundTransaction.Command command = questCommand(fixture, fixture.ready(), fixture.goal(), fixture.goalRowVersion(), fixture.acquisitionState(), fixture.stateRowVersion(), batch.progress(), batch.vitals(), new Clock(batch.nextRngState(), 0, 0), batch.inventoryDelta().itemDeltas(), 1);
			final QuestAtomicSnapshot before = questAtomicSnapshot(fixture);
			final Result result = fixture.transaction().execute(command);
			PhantomAssertions.assertEquals(Status.SUCCESS, result.status(), "Exact quest cap transaction failed for " + rule.id() + "/required=" + required + ".");
			final QuestAtomicSnapshot committed = questAtomicSnapshot(fixture);
			final PhantomAcquisitionState acquisition = committed.acquisition().state();
			PhantomAssertions.assertEquals((long) rule.itemCap(), committed.itemCount(), "Committed quest item count did not reach the exact cap.");
			PhantomAssertions.assertEquals((long) rule.itemCap(), acquisition.lastObservedCount(), "Background cap transaction lost authoritative lastObservedCount.");
			PhantomAssertions.assertEquals(1L, acquisition.progress(), "Background cap transaction lost exact partial progress.");
			PhantomAssertions.assertEquals(Phase.NONE, acquisition.phase(), "Background cap transaction retained an executable phase.");
			PhantomAssertions.assertEquals(0, acquisition.targetObjectId(), "Background cap transaction retained a target.");
			PhantomAssertions.assertEquals(1L, acquisition.receipts().stream().filter(receipt -> receipt.kind() == ReceiptKind.BACKGROUND_QUEST_COLLECTION).count(), "Background cap transaction did not retain exactly one quest receipt.");
			final var receipt = acquisition.receipts().getLast();
			PhantomAssertions.assertEquals((long) rule.itemCap() - 1, receipt.beforeCount(), "Background quest cap receipt lost the pre-kill count.");
			PhantomAssertions.assertEquals((long) rule.itemCap(), receipt.afterCount(), "Background quest cap receipt lost the committed cap count.");
			PhantomAssertions.assertEquals(TerminalResult.COMMITTED, receipt.result(), "Background quest cap receipt is not committed.");
			final QuestBinding historical = (QuestBinding) acquisition.methodBinding();
			PhantomAssertions.assertEquals((long) rule.itemCap() - 1, historical.itemCountBeforeKill(), "Background cap transaction did not retain the valid historical binding.");
			PhantomAssertions.assertTrue(historical.itemCountBeforeKill() < historical.itemCap(), "Background cap binding violates the executable baseline invariant.");
			PhantomAssertions.assertEquals(0L, historical.callbackDeadlineMillis(), "Background cap binding retained a callback deadline.");
			PhantomAssertions.assertEquals(before.questRows(), committed.questRows(), "Background cap transaction changed audited quest rows.");
			final PhantomAcquisitionStateCodec codec = new PhantomAcquisitionStateCodec();
			PhantomAssertions.assertTrue(Arrays.equals(codec.encode(result.acquisitionState()), codec.encode(acquisition)), "Post-commit verifier reconstruction is not byte-identical at the quest cap.");
			if (completes)
			{
				PhantomAssertions.assertEquals(PhantomAcquisitionState.Status.COMPLETED, acquisition.status(), "Background completion exactly at cap did not complete acquisition state.");
				PhantomAssertions.assertEquals(PhantomGoalStatus.COMPLETED, committed.goal().goal().status(), "Background completion exactly at cap did not atomically complete Goal.");
			}
			else
			{
				PhantomAssertions.assertEquals(PhantomAcquisitionState.Status.BLOCKED, acquisition.status(), "Background partial completion at cap did not block the exhausted source.");
				PhantomAssertions.assertEquals(PhantomGoalStatus.ACTIVE, committed.goal().goal().status(), "Background partial completion at cap completed Goal.");
				PhantomAssertions.assertEquals("quest.item_cap", acquisition.candidates().get(acquisition.sourceCursor()).lastFailureReason(), "Background partial completion at cap lost the typed candidate failure.");
			}
			PhantomAssertions.assertEquals(Status.IDEMPOTENT, fixture.transaction().execute(command).status(), "Exact background quest cap replay was not idempotent.");
			PhantomAssertions.assertEquals(committed, questAtomicSnapshot(fixture), "Exact background quest cap replay changed item/background/Goal/acquisition/quest-row truth.");
			if (!completes)
			{
				final PhantomBackgroundState currentBackground = fixture.transaction().load(fixture.profileId()).state();
				final var currentGoal = fixture.goals().load(fixture.profileId()).orElseThrow();
				final var currentAcquisition = fixture.acquisition().load(fixture.profileId()).orElseThrow();
				final PhantomBackgroundTransaction.Command overCap = questCommand(fixture, currentBackground, currentGoal.goal(), currentGoal.rowVersion(), currentAcquisition.state(), currentAcquisition.rowVersion(), currentBackground.progress(), currentBackground.vitals(), new Clock(currentBackground.clock().rngState() + 1, 0, 0), Map.of(rule.questItemId(), 1L), 2);
				final QuestAtomicSnapshot beforeConflict = questAtomicSnapshot(fixture);
				PhantomAssertions.assertEquals(Status.ACQUISITION_CONFLICT, fixture.transaction().execute(overCap).status(), "Explicit background after > cap was not an acquisition conflict.");
				PhantomAssertions.assertEquals(beforeConflict, questAtomicSnapshot(fixture), "Explicit background after > cap escaped atomic rollback or changed quest rows.");
			}
		}
	}

	private QuestCapFixture createQuestCapFixture(PhantomBackgroundTransaction transaction, PhantomAcquisitionQuestCatalog catalog, Rule rule, long required) throws Exception
	{
		final int characterObjectId = _environment.primary().objectId();
		final Canonical canonical = canonical(characterObjectId);
		final Map<String, String> originalQuestRows = questRows(characterObjectId, rule.questName());
		final Map<String, String> expectedQuestRows = Map.of("<state>", "Started", "cond", Integer.toString(rule.allowedConds().getFirst()));
		PhantomAssertions.assertTrue(rule.expectedVars().isEmpty(), "Quest cap fixture only supports the strictly audited pure kill-collection rows.");
		PhantomAssertions.assertEquals(0L, scalarLong("SELECT COALESCE(SUM(count),0) FROM items WHERE owner_id=? AND item_id=" + rule.questItemId() + " AND loc='INVENTORY'", characterObjectId), "Quest cap fixture started with a non-empty quest item stack.");
		replaceQuestRows(characterObjectId, rule.questName(), expectedQuestRows);
		final int itemObjectId = IdManager.getInstance().getNextId();
		try (Connection connection = DatabaseFactory.getConnection();
			PreparedStatement statement = connection.prepareStatement("INSERT INTO items (owner_id,item_id,count,loc,loc_data,enchant_level,object_id,custom_type1,custom_type2,mana_left,time) VALUES (?,?,?,'INVENTORY',0,0,?,0,0,-1,-1)"))
		{
			statement.setInt(1, characterObjectId);
			statement.setInt(2, rule.questItemId());
			statement.setLong(3, rule.itemCap() - 1);
			statement.setInt(4, itemObjectId);
			statement.executeUpdate();
		}
		final PhantomProfile profile = _repository.create(characterObjectId);
		Fixture background = null;
		try
		{
			final long baseline = rule.itemCap() - 1;
			final PhantomGoal goal = new PhantomGoal(21, PhantomAcquisitionGoalSpec.GOAL_TYPE, PhantomGoalStatus.ACTIVE, new PhantomDomainRef("profile", "self"), new PhantomDomainRef("item", Integer.toString(rule.questItemId())), required, 0, PhantomAcquisitionCatalog.Method.QUEST_COLLECTION.key(), List.of(new PhantomDomainRef(PhantomAcquisitionGoalSpec.SOURCE_NAMESPACE, PhantomAcquisitionCatalog.Method.QUEST_COLLECTION.key())), new PhantomDomainRef(PhantomAcquisitionGoalSpec.ANCHOR_NAMESPACE, ANCHOR_ID), PhantomAcquisitionGoalSpec.PURPOSE_KEY, 500, 0, 0, 0, Map.of(PhantomAcquisitionGoalSpec.BASELINE_CONSTRAINT, baseline, PhantomAcquisitionGoalSpec.MAXIMUM_SWITCHES_CONSTRAINT, 4L), "acquisition.quest.cap.transaction", 0);
			final PhantomGoalStateStore goals = new PhantomGoalStateStore(_repository);
			final var storedGoal = goals.insert(profile.profileId(), goal);
			final Identity identity = new Identity(profile.profileId(), characterObjectId, 0, canonical.classId(), canonical.race());
			final List<AutoGetSkill> skills = exactAutoGetSkills(identity, canonical.level());
			ensureAutoGetSkills(identity, skills);
			final PhantomBackgroundState materialized = new PhantomBackgroundState(State.MATERIALIZED, identity, new Progress(canonical.level(), canonical.experience(), canonical.skillPoints(), canonical.experienceBeforeDeath()), new Vitals(canonical.currentHp(), canonical.maximumHp(), canonical.currentMp(), canonical.maximumMp(), canonical.currentCp(), canonical.maximumCp()), new Position(0, canonical.x(), canonical.y(), canonical.z(), canonical.heading(), ANCHOR_ID), combat(ModelKind.MELEE, 1, 1, 100), Loadout.none(), new InventoryFacts(List.of(rule.questItemId()), List.of(), "", 0, 1_000_000, 0, 100), skills, new Clock(QUEST_CAP_SEED, 0, 0), Receipt.empty(), HASHES);
			Result captured = captureTestBaseline(transaction, materialized, goal);
			PhantomAssertions.assertEquals(Status.SUCCESS, captured.status(), "Quest cap fixture background capture failed.");
			final long grantRng = questGrantRng(captured.state(), rule);
			if (grantRng != captured.state().clock().rngState())
			{
				captured = captureTestBaseline(transaction, withQuestClock(captured.state(), State.MATERIALIZED, grantRng), goal);
				PhantomAssertions.assertEquals(Status.SUCCESS, captured.status(), "Quest cap fixture deterministic RNG recapture failed.");
			}
			final int targetNpcId = rule.targetNpcIds().getFirst();
			final Source source = new Source(rule.ruleHash(), PhantomAcquisitionCatalog.Method.QUEST_COLLECTION, targetNpcId, rule.questItemId(), "quest:" + rule.id(), "quest.cap.node", ANCHOR_ID, 0, 0, 0, 0, 0);
			final Candidate candidate = new Candidate(source.sourceId(), source.method(), 100, 0, 0, "");
			final PhantomAcquisitionState.Hashes acquisitionHashes = new PhantomAcquisitionState.Hashes("a".repeat(64), "b".repeat(64), "c".repeat(64), "d".repeat(64), "e".repeat(64));
			final QuestBinding binding = new QuestBinding(rule.id(), rule.ruleHash(), rule.questId(), rule.questName(), rule.scriptHash(), rule.requiredState(), rule.allowedConds().getFirst(), rule.questItemId(), rule.itemCap(), targetNpcId, baseline, 0, catalog.authorityHash());
			final PhantomAcquisitionState state = new PhantomAcquisitionState(acquisitionHashes, goal.goalId(), goal.revision(), rule.questItemId(), required, baseline, baseline, 0, PhantomAcquisitionState.Status.READY, source, List.of(candidate), 0, 0, Phase.TARGET_REQUIRED, 0, 0, 0, null, binding, List.of(), 0, 1);
			final PhantomAcquisitionStore acquisition = new PhantomAcquisitionStore(_repository, goals);
			final var storedState = acquisition.insert(profile.profileId(), state);
			background = new Fixture(profile.profileId(), characterObjectId, goal, transaction, captured.state(), canonical);
			return new QuestCapFixture(new AcquisitionAtomicFixture(background, goals, acquisition, storedGoal.rowVersion(), storedState.rowVersion(), baseline, required), rule, expectedQuestRows, originalQuestRows);
		}
		catch (Exception | Error failure)
		{
			if (background != null)
			{
				background.close();
			}
			else
			{
				_repository.find(profile.profileId()).ifPresent(current -> _repository.delete(current.profileId(), current.rowVersion()));
				restorePrimaryInventoryAndSkills(characterObjectId);
			}
			replaceQuestRows(characterObjectId, rule.questName(), originalQuestRows);
			throw failure;
		}
	}

	private static BatchResult questCapBatch(PhantomBackgroundState state, Rule rule)
	{
		final ItemTemplate item = ItemData.getInstance().getTemplate(rule.questItemId());
		PhantomAssertions.assertTrue(item != null, "Curated quest item template is absent: " + rule.questItemId());
		final Target target = new Target(rule.targetNpcIds().getFirst(), Math.max(1, state.progress().level()), true, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, List.of(), 0);
		final QuestFormula formula = new QuestFormula(rule.rollBound(), rule.rollThreshold(), rule.maximumCount(), rule.itemCap() - 1, rule.itemCap());
		return new PhantomBackgroundModel().evaluate(new BatchRequest(state, target, new RewardPolicy(11, 0, 0), deathPolicy(), experienceTable(), levelForExperience(), false, BatchMode.ACQUISITION_QUEST_COLLECTION, rule.questItemId(), 1, true, null, formula, 1, item.isStackable(), item.getWeight()));
	}

	private static long questGrantRng(PhantomBackgroundState state, Rule rule)
	{
		for (long rng = 1; rng <= 10_000; rng++)
		{
			if (questCapBatch(withQuestClock(state, State.READY, rng), rule).acquisitionTargetDelta() == 1)
			{
				return rng;
			}
		}
		throw new AssertionError("No deterministic exact +1 quest cap grant was found for " + rule.id() + ".");
	}

	private static PhantomBackgroundState withQuestClock(PhantomBackgroundState state, State nextState, long rng)
	{
		return new PhantomBackgroundState(nextState, state.identity(), state.progress(), state.vitals(), state.position(), state.combat(), state.loadout(), state.inventory(), state.autoGetSkills(), new Clock(rng, 0, 0), state.receipt(), state.hashes());
	}

	private static PhantomBackgroundTransaction.Command questCommand(QuestCapFixture fixture, PhantomBackgroundState background, PhantomGoal goal, long goalRowVersion, PhantomAcquisitionState acquisition, long stateRowVersion, Progress progress, Vitals vitals, Clock clock, Map<Integer, Long> itemDeltas, long tickSequence) throws Exception
	{
		final QuestBinding binding = (QuestBinding) acquisition.methodBinding();
		final Source source = acquisition.selectedSource();
		final AcquisitionIdentity identity = new AcquisitionIdentity(source.sourceId(), stateRowVersion, acquisition.targetItemId(), acquisition.hashes().catalog(), acquisition.hashes().background(), HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(binding.toString().getBytes(StandardCharsets.UTF_8))), binding.itemCountBeforeKill());
		final PhantomBackgroundOperationKey key = new PhantomBackgroundOperationKey(fixture.profileId(), fixture.characterObjectId(), goal.goalId(), goal.revision(), 1, tickSequence, ActionKind.ACQUISITION_QUEST_COLLECTION, source.npcId(), source.anchorId(), PhantomBackgroundState.MODEL_VERSION, background.hashes(), identity);
		final var mutation = new PhantomBackgroundTransaction.AcquisitionMutation(acquisition, stateRowVersion, goalRowVersion, ReceiptKind.BACKGROUND_QUEST_COLLECTION, tickSequence, Map.of(), fixture.expectedQuestRows());
		return new PhantomBackgroundTransaction.Command(background, goal, key, progress, vitals, background.position(), clock, itemDeltas, background.autoGetSkills(), List.of(acquisition.targetItemId()), mutation);
	}

	private QuestAtomicSnapshot questAtomicSnapshot(QuestCapFixture fixture) throws Exception
	{
		return new QuestAtomicSnapshot(fixture.transaction().load(fixture.profileId()).state(), fixture.goals().load(fixture.profileId()).orElseThrow(), fixture.acquisition().load(fixture.profileId()).orElseThrow(), scalarLong("SELECT COALESCE(SUM(count),0) FROM items WHERE owner_id=? AND item_id=" + fixture.rule().questItemId() + " AND loc='INVENTORY'", fixture.characterObjectId()), questRows(fixture.characterObjectId(), fixture.rule().questName()));
	}

	private static Map<String, String> questRows(int characterObjectId, String questName) throws Exception
	{
		final Map<String, String> result = new LinkedHashMap<>();
		try (Connection connection = DatabaseFactory.getConnection();
			PreparedStatement statement = connection.prepareStatement("SELECT var,value FROM character_quests WHERE charId=? AND name=? ORDER BY var"))
		{
			statement.setInt(1, characterObjectId);
			statement.setString(2, questName);
			try (ResultSet rows = statement.executeQuery())
			{
				while (rows.next())
				{
					result.put(rows.getString(1), rows.getString(2));
				}
			}
		}
		return Map.copyOf(result);
	}

	private static void replaceQuestRows(int characterObjectId, String questName, Map<String, String> rows) throws Exception
	{
		try (Connection connection = DatabaseFactory.getConnection())
		{
			connection.setAutoCommit(false);
			try (PreparedStatement delete = connection.prepareStatement("DELETE FROM character_quests WHERE charId=? AND name=?");
				PreparedStatement insert = connection.prepareStatement("INSERT INTO character_quests (charId,name,var,value) VALUES (?,?,?,?)"))
			{
				delete.setInt(1, characterObjectId);
				delete.setString(2, questName);
				delete.executeUpdate();
				for (var entry : rows.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList())
				{
					insert.setInt(1, characterObjectId);
					insert.setString(2, questName);
					insert.setString(3, entry.getKey());
					insert.setString(4, entry.getValue());
					insert.addBatch();
				}
				insert.executeBatch();
				connection.commit();
			}
			catch (Exception | Error failure)
			{
				connection.rollback();
				throw failure;
			}
		}
	}

	private void testAcquisitionOperationIdentity()
	{
		final PhantomBackgroundOperationKey ordinary = new PhantomBackgroundOperationKey(1, 2, 3, 4, 5, 6, ActionKind.FARM, 7, "anchor", 3, HASHES);
		PhantomAssertions.assertEquals("771992e2a6d056f9e8ce20d69975c3a3ad699207103bdcc2398fbd6cbe13b261", ordinary.digest(), "Ordinary Goal 015 operation digest changed.");
		final AcquisitionIdentity first = new AcquisitionIdentity("1".repeat(64), 9, 57, "a".repeat(64), "b".repeat(64));
		final AcquisitionIdentity secondSource = new AcquisitionIdentity("2".repeat(64), 9, 57, "a".repeat(64), "b".repeat(64));
		final AcquisitionIdentity secondVersion = new AcquisitionIdentity("1".repeat(64), 10, 57, "a".repeat(64), "b".repeat(64));
		final PhantomBackgroundOperationKey exact = new PhantomBackgroundOperationKey(1, 2, 3, 4, 5, 6, ActionKind.ACQUISITION_DEATH_DROP, 7, "anchor", 3, HASHES, first);
		PhantomAssertions.assertEquals(exact.digest(), new PhantomBackgroundOperationKey(1, 2, 3, 4, 5, 6, ActionKind.ACQUISITION_DEATH_DROP, 7, "anchor", 3, HASHES, first).digest(), "Exact acquisition replay changed operation identity.");
		PhantomAssertions.assertFalse(exact.digest().equals(new PhantomBackgroundOperationKey(1, 2, 3, 4, 5, 6, ActionKind.ACQUISITION_DEATH_DROP, 7, "anchor", 3, HASHES, secondSource).digest()), "Different acquisition sources shared an operation identity.");
		PhantomAssertions.assertFalse(exact.digest().equals(new PhantomBackgroundOperationKey(1, 2, 3, 4, 5, 6, ActionKind.ACQUISITION_DEATH_DROP, 7, "anchor", 3, HASHES, secondVersion).digest()), "Different acquisition generations shared an operation identity.");
	}

	private AcquisitionAtomicFixture createAcquisitionAtomicFixture(PhantomBackgroundTransaction transaction) throws Exception
	{
		return createAcquisitionAtomicFixture(transaction, PhantomAcquisitionCatalog.Method.DEATH_DROP);
	}

	private AcquisitionAtomicFixture createAcquisitionAtomicFixture(PhantomBackgroundTransaction transaction, PhantomAcquisitionCatalog.Method method) throws Exception
	{
		final int characterObjectId = _environment.primary().objectId();
		final Canonical canonical = canonical(characterObjectId);
		final long baseline = scalarLong("SELECT COALESCE(SUM(count),0) FROM items WHERE owner_id=? AND item_id=57 AND loc='INVENTORY'", characterObjectId);
		final long required = 3;
		final PhantomProfile profile = _repository.create(characterObjectId);
		try
		{
			final PhantomGoal goal = new PhantomGoal(21, PhantomAcquisitionGoalSpec.GOAL_TYPE, PhantomGoalStatus.ACTIVE, new PhantomDomainRef("profile", "self"), new PhantomDomainRef("item", "57"), required, 0, method.key(), List.of(new PhantomDomainRef(PhantomAcquisitionGoalSpec.SOURCE_NAMESPACE, method.key())), new PhantomDomainRef(PhantomAcquisitionGoalSpec.ANCHOR_NAMESPACE, ANCHOR_ID), PhantomAcquisitionGoalSpec.PURPOSE_KEY, 500, 0, 0, 0, Map.of(PhantomAcquisitionGoalSpec.BASELINE_CONSTRAINT, baseline, PhantomAcquisitionGoalSpec.MAXIMUM_SWITCHES_CONSTRAINT, 4L), "acquisition.atomic.test", 0);
			PhantomAcquisitionGoalSpec.parse(goal);
			final PhantomGoalStateStore goals = new PhantomGoalStateStore(_repository);
			final PhantomGoalStateStore.StoredGoal storedGoal = goals.insert(profile.profileId(), goal);
			final Identity identity = new Identity(profile.profileId(), characterObjectId, 0, canonical.classId(), canonical.race());
			final List<AutoGetSkill> skills = exactAutoGetSkills(identity, canonical.level());
			ensureAutoGetSkills(identity, skills);
			final PhantomBackgroundState materialized = new PhantomBackgroundState(State.MATERIALIZED, identity, new Progress(canonical.level(), canonical.experience(), canonical.skillPoints(), canonical.experienceBeforeDeath()), new Vitals(canonical.currentHp(), canonical.maximumHp(), canonical.currentMp(), canonical.maximumMp(), canonical.currentCp(), canonical.maximumCp()), new Position(0, canonical.x(), canonical.y(), canonical.z(), canonical.heading(), ANCHOR_ID), combat(ModelKind.MELEE, 1, 1, 100), Loadout.none(), new InventoryFacts(List.of(57), List.of(), "", 0, 1_000_000, 0, 100), skills, new Clock(ACQUISITION_SEED, 0, 0), Receipt.empty(), HASHES);
			final Result captured = captureTestBaseline(transaction, materialized, goal);
			PhantomAssertions.assertEquals(Status.SUCCESS, captured.status(), "Acquisition atomic fixture background capture failed.");
			final Source source = method == PhantomAcquisitionCatalog.Method.SPOIL_SWEEP ? new Source("2".repeat(64), method, TARGET_NPC_ID, 57, "test:spoil:57", "test.node", ANCHOR_ID, 0, 254, 11, 42, 1) : new Source("1".repeat(64), method, TARGET_NPC_ID, 57, "test:death-drop:57", "test.node", ANCHOR_ID, 0, 0, 0, 0, 0);
			final Candidate candidate = new Candidate(source.sourceId(), source.method(), 100, 0, 0, "");
			final PhantomAcquisitionState.Hashes acquisitionHashes = new PhantomAcquisitionState.Hashes("a".repeat(64), "b".repeat(64), "c".repeat(64), "d".repeat(64), "e".repeat(64));
			final PhantomAcquisitionState state = new PhantomAcquisitionState(acquisitionHashes, goal.goalId(), goal.revision(), 57, required, baseline, baseline, 0, PhantomAcquisitionState.Status.READY, source, List.of(candidate), 0, 0, Phase.TARGET_REQUIRED, 0, 0, 0, null, List.of(), 0);
			final PhantomAcquisitionStore acquisition = new PhantomAcquisitionStore(_repository, goals);
			final PhantomAcquisitionStore.StoredState storedState = acquisition.insert(profile.profileId(), state);
			final Fixture background = new Fixture(profile.profileId(), characterObjectId, goal, transaction, captured.state(), canonical);
			return new AcquisitionAtomicFixture(background, goals, acquisition, storedGoal.rowVersion(), storedState.rowVersion(), baseline, required);
		}
		catch (Throwable failure)
		{
			final Optional<PhantomProfile> current = _repository.find(profile.profileId());
			if (current.isPresent())
			{
				_repository.delete(profile.profileId(), current.get().rowVersion());
			}
			restorePrimaryInventoryAndSkills(characterObjectId);
			throw failure;
		}
	}

	private static PhantomBackgroundTransaction.Command acquisitionCommand(AcquisitionAtomicFixture fixture, PhantomGoal goal, long goalRowVersion, long stateRowVersion, Hashes hashes)
	{
		final PhantomBackgroundState ready = fixture.ready();
		final Source source = fixture.acquisition().load(fixture.profileId()).orElseThrow().state().selectedSource();
		final PhantomAcquisitionState expected = fixture.acquisition().load(fixture.profileId()).orElseThrow().state();
		final ActionKind actionKind = source.method() == PhantomAcquisitionCatalog.Method.SPOIL_SWEEP ? ActionKind.ACQUISITION_SPOIL_SWEEP : ActionKind.ACQUISITION_DEATH_DROP;
		final PhantomBackgroundOperationKey key = new PhantomBackgroundOperationKey(fixture.profileId(), fixture.characterObjectId(), goal.goalId(), goal.revision(), 1, 1, actionKind, source.npcId(), source.anchorId(), PhantomBackgroundState.MODEL_VERSION, hashes, new AcquisitionIdentity(source.sourceId(), stateRowVersion, expected.targetItemId(), expected.hashes().catalog(), expected.hashes().background(), NO_METHOD_BINDING_HASH, 0));
		final ReceiptKind receiptKind = source.method() == PhantomAcquisitionCatalog.Method.SPOIL_SWEEP ? ReceiptKind.BACKGROUND_SPOIL_SWEEP : ReceiptKind.BACKGROUND_DEATH_DROP;
		final var mutation = new PhantomBackgroundTransaction.AcquisitionMutation(expected, stateRowVersion, goalRowVersion, receiptKind, 1);
		return new PhantomBackgroundTransaction.Command(ready, goal, key, ready.progress(), ready.vitals(), ready.position(), new Clock(ACQUISITION_SEED + 1, 0, 0), Map.of(expected.targetItemId(), fixture.requiredAmount()), ready.autoGetSkills(), List.of(expected.targetItemId()), mutation);
	}

	private AcquisitionAtomicSnapshot acquisitionAtomicSnapshot(AcquisitionAtomicFixture fixture) throws Exception
	{
		return new AcquisitionAtomicSnapshot(fixture.transaction().load(fixture.profileId()).state(), fixture.goals().load(fixture.profileId()).orElseThrow(), fixture.acquisition().load(fixture.profileId()).orElseThrow(), scalarLong("SELECT COALESCE(SUM(count),0) FROM items WHERE owner_id=? AND item_id=57 AND loc='INVENTORY'", fixture.characterObjectId()));
	}

	/** Explicit controlled TEST policy; it cannot invent support for a legacy zero/boosted native row. */
	private static Result captureTestBaseline(PhantomBackgroundTransaction transaction, PhantomBackgroundState state, PhantomGoal goal) throws Exception
	{
		final long points = scalarLong("SELECT vitality_points FROM characters WHERE charId=?", state.identity().characterObjectId());
		PhantomAssertions.assertEquals(1L, points, "Controlled B4 baseline requires the actual ordinary native minimum; no vitality normalization is allowed here.");
		return transaction.captureBaseline(state, goal, new PhantomNativeContext.Capture(1, PhantomNativeContext.Eligibility.SUPPORTED));
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
		final Result captured = captureTestBaseline(transaction, materialized, goal);
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

	private static void upsertSkill(int characterObjectId, int classIndex, int skillId, int skillLevel) throws Exception
	{
		try (Connection connection = DatabaseFactory.getConnection();
			PreparedStatement statement = connection.prepareStatement("INSERT INTO character_skills (charId,skill_id,skill_level,class_index) VALUES (?,?,?,?) ON DUPLICATE KEY UPDATE skill_level=VALUES(skill_level)"))
		{
			statement.setInt(1, characterObjectId);
			statement.setInt(2, skillId);
			statement.setInt(3, skillLevel);
			statement.setInt(4, classIndex);
			statement.executeUpdate();
		}
	}

	private static void deleteSkill(int characterObjectId, int classIndex, int skillId) throws Exception
	{
		try (Connection connection = DatabaseFactory.getConnection();
			PreparedStatement statement = connection.prepareStatement("DELETE FROM character_skills WHERE charId=? AND skill_id=? AND class_index=?"))
		{
			statement.setInt(1, characterObjectId);
			statement.setInt(2, skillId);
			statement.setInt(3, classIndex);
			statement.executeUpdate();
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

	static PhantomBackgroundState productionState(PhantomTopologyAnchor anchor, Hashes hashes)
	{
		return new PhantomBackgroundState(State.READY, new Identity(15001501, 15001501, 0, 0, 0), new Progress(1, 0, 0, 0), new Vitals(100, 100, 100, 100, 10, 10), canonicalAnchorPosition(anchor, 0), combat(ModelKind.MELEE, 1, 1, 0), Loadout.none(), inventory(), List.of(), new Clock(SEED, 0, 0), Receipt.empty(), hashes);
	}

	private static Position canonicalAnchorPosition(PhantomTopologyAnchor anchor, int heading)
	{
		return L2jPhantomBackgroundAuthority.canonicalCommittedAnchorPosition(anchor, heading).orElseThrow(() -> new AssertionError("Current anchor has no stable canonical geodata position: " + anchor.id()));
	}

	@SuppressWarnings("unchecked")
	private static Optional<Position> canonicalAnchorPosition(PhantomTopologyAnchor anchor, int heading, IntUnaryOperator heightResolver) throws Exception
	{
		final Method method = L2jPhantomBackgroundAuthority.class.getDeclaredMethod("canonicalCommittedAnchorPosition", PhantomTopologyAnchor.class, int.class, IntUnaryOperator.class);
		method.setAccessible(true);
		return (Optional<Position>) method.invoke(null, anchor, heading, heightResolver);
	}

	private static PhantomTopologyAnchor syntheticAnchor(String id, int rawZ, int instanceId, int tolerance)
	{
		return new PhantomTopologyAnchor(id, PhantomTopologyAnchorRole.ROUTE, "giran.route.north", new PhantomTopologyPoint(100, 200, rawZ, instanceId), null, null, tolerance, List.of("route"), List.of("test"));
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

	private static String canonicalInventoryHash(int objectId) throws Exception
	{
		final var items = new ArrayList<org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundInventoryHash.CanonicalItem>();
		try (Connection connection = DatabaseFactory.getConnection();
			PreparedStatement statement = connection.prepareStatement("SELECT object_id,item_id,count,loc FROM items WHERE owner_id=? AND loc IN ('INVENTORY','PAPERDOLL') AND count>0"))
		{
			statement.setInt(1, objectId);
			try (ResultSet rows = statement.executeQuery())
			{
				while (rows.next()) { items.add(new org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundInventoryHash.CanonicalItem(rows.getInt("object_id"), rows.getInt("item_id"), rows.getLong("count"), ItemLocation.valueOf(rows.getString("loc")))); }
			}
		}
		return org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundInventoryHash.compute(items);
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

	private static void assertCharacterPosition(int objectId, Position expected) throws Exception
	{
		try (Connection connection = DatabaseFactory.getConnection();
			PreparedStatement statement = connection.prepareStatement("SELECT x,y,z,heading FROM characters WHERE charId=?"))
		{
			statement.setInt(1, objectId);
			try (ResultSet result = statement.executeQuery())
			{
				PhantomAssertions.assertTrue(result.next(), "Character row disappeared.");
				PhantomAssertions.assertEquals(expected.x(), result.getInt("x"), "Canonical X mismatch.");
				PhantomAssertions.assertEquals(expected.y(), result.getInt("y"), "Canonical Y mismatch.");
				PhantomAssertions.assertEquals(expected.z(), result.getInt("z"), "Canonical Z mismatch.");
				PhantomAssertions.assertEquals(expected.heading(), result.getInt("heading"), "Canonical heading mismatch.");
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
		final var ownedFixture = objectId == _environment.primary().objectId() ? _environment.primary() : _environment.observer();
		PhantomAssertions.assertEquals(ownedFixture.objectId(), objectId, "Only an exact owned TEST fixture may be restored.");
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
			item.setLong(1, ownedFixture.fixtureItemBaseline());
			item.setInt(2, objectId);
			item.setInt(3, PhantomActionFacade.FIXTURE_ITEM_ID);
			PhantomAssertions.assertEquals(1, item.executeUpdate(), "Primary fixture item restore failed.");
			skills.setInt(1, objectId);
			skills.setInt(2, ownedFixture.skillId());
			skills.executeUpdate();
			restoreSkill.setInt(1, objectId);
			restoreSkill.setInt(2, ownedFixture.skillId());
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
		private final int _objectId;
		private final ProductionFarmSelection _farm;
		private final PhantomGoal _goal;
		private final Canonical _original;
		private final int _originalBaseClass;
		private final boolean _createdTestIdentity;
		private final String _createdTestName, _createdTestAccount;
		private boolean _createdTestDeleted;

		private ProductionPlayerFixture(Player player, ProductionFarmSelection farm, PhantomGoal goal, Canonical original, int originalBaseClass)
		{
			this(player, farm, goal, original, originalBaseClass, false);
		}

		private ProductionPlayerFixture(Player player, ProductionFarmSelection farm, PhantomGoal goal, Canonical original, int originalBaseClass, boolean createdTestIdentity)
		{
			_player = player;
			_objectId = player.getObjectId();
			_farm = farm;
			_goal = goal;
			_original = original;
			_originalBaseClass = originalBaseClass;
			_createdTestIdentity = createdTestIdentity;
			_createdTestName = createdTestIdentity ? player.getName() : null;
			_createdTestAccount = createdTestIdentity ? player.getAccountName() : null;
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
			if (_createdTestDeleted) { return; }
			final int objectId = _objectId;
			releaseRuntime();
			if (_createdTestIdentity)
			{
				PhantomAssertions.assertTrue(World.getInstance().getPlayer(objectId) == null && PhantomIdentityLeaseRegistry.getInstance().getOwnerKind(objectId) == null,
					"Native third exact TEST identity still has a World Player or lease before deletion.");
				try (Connection connection = DatabaseFactory.getConnection();
					PreparedStatement statement = connection.prepareStatement("SELECT account_name,char_name FROM characters WHERE charId=?"))
				{
					statement.setInt(1, objectId);
					try (ResultSet rows = statement.executeQuery())
					{
						PhantomAssertions.assertTrue(rows.next() && _createdTestName.equals(rows.getString("char_name")) && _createdTestAccount.equals(rows.getString("account_name")),
							"Native third TEST deletion refused changed exact character identity.");
					}
				}
				_repository.findByCharacterObjectId(objectId).ifPresent(PhantomBackgroundSuite.this::deleteProfile);
				org.l2jmobius.gameserver.network.GameClient.deleteCharByObjId(objectId);
				PhantomAssertions.assertTrue(scalarLong("SELECT COUNT(*) FROM characters WHERE charId=?", objectId) == 0
					&& scalarLong("SELECT COUNT(*) FROM items WHERE owner_id=?", objectId) == 0 && _repository.findByCharacterObjectId(objectId).isEmpty(),
					"Native third exact TEST character/items/profile deletion did not complete.");
				_createdTestDeleted = true;
				return;
			}
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
		public PhantomNativeContext.Capture captureNativeContext(Player player)
		{
			PhantomAssertions.assertEquals(1, player.getVitalityPoints(), "Controlled FakeAuthority requires actual ordinary native minimum points.");
			return new PhantomNativeContext.Capture(player.getVitalityPoints(), PhantomNativeContext.Eligibility.SUPPORTED);
		}

		@Override
		public NativeCapture captureOwnedNative(long profileId, Player player, PhantomGoal goal, PhantomBackgroundState previous)
		{
			return new NativeCapture(capture(profileId, player, goal, previous), captureNativeContext(player));
		}

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
			final var tracked = player.getInventory().getItems().stream().filter(item -> item.getItemLocation() == org.l2jmobius.gameserver.model.item.enums.ItemLocation.INVENTORY && ((item.getId() == 10) || (item.getId() == 57))).map(item -> new ItemObject(item.getObjectId(), item.getId(), item.getCount(), item.isStackable(), ItemLocation.INVENTORY)).sorted(Comparator.comparingInt(ItemObject::objectId)).toList();
			return new PhantomBackgroundState(State.MATERIALIZED, identity, new Progress(player.getLevel(), player.getExp(), player.getSp(), player.getExpBeforeDeath()), new Vitals(player.getCurrentHp(), player.getMaxHp(), player.getCurrentMp(), player.getMaxMp(), player.getCurrentCp(), player.getMaxCp()), new Position(0, player.getX(), player.getY(), player.getZ(), player.getHeading(), ANCHOR_ID), combat(ModelKind.MELEE, 1, 1, 100), Loadout.none(), new InventoryFacts(List.of(10, 57), tracked, "", 0, 1_000_000, 0, 100), exactAutoGetSkills(identity, player.getLevel()), previous == null ? new Clock(SEED, 0, 0) : previous.clock(), previous == null ? Receipt.empty() : previous.receipt(), HASHES);
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
		public Optional<Position> canonicalRecoveryPosition(int x, int y, int z, int instanceId, int heading)
		{
			return Optional.of(new Position(instanceId, x, y, z, heading, ANCHOR_ID));
		}

		@Override
		public List<AutoGetSkill> autoGetSkills(Identity identity, int level)
		{
			return exactAutoGetSkills(identity, level);
		}
	}

	static record ProductionAuthorityFixture(PhantomGameKnowledgeService knowledgeService, PhantomGameKnowledgeQuery knowledge, PhantomTopologyQuery topology, L2jTopologyValidationBackend topologyBackend, PhantomProgressionCatalog progression, PhantomCommerceCatalog commerce, L2jPhantomBackgroundAuthority authority) implements AutoCloseable
	{
		static ProductionAuthorityFixture start()
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

	private record AcquisitionParityFixture(Source source, PhantomBackgroundState state, PhantomBackgroundAuthority.FarmInput input, BatchRequest request, BatchResult result)
	{
	}

	private record AcquisitionAtomicSnapshot(PhantomBackgroundState background, PhantomGoalStateStore.StoredGoal goal, PhantomAcquisitionStore.StoredState acquisition, long itemCount)
	{
	}

	private record QuestAtomicSnapshot(PhantomBackgroundState background, PhantomGoalStateStore.StoredGoal goal, PhantomAcquisitionStore.StoredState acquisition, long itemCount, Map<String, String> questRows)
	{
		private QuestAtomicSnapshot
		{
			questRows = Map.copyOf(questRows);
		}
	}

	private record AcquisitionAtomicFixture(Fixture background, PhantomGoalStateStore goals, PhantomAcquisitionStore acquisition, long goalRowVersion, long stateRowVersion, long baselineCount, long requiredAmount) implements AutoCloseable
	{
		private long profileId()
		{
			return background.profileId();
		}

		private int characterObjectId()
		{
			return background.characterObjectId();
		}

		private PhantomGoal goal()
		{
			return background.goal();
		}

		private PhantomBackgroundTransaction transaction()
		{
			return background.transaction();
		}

		private PhantomBackgroundState ready()
		{
			return background.ready();
		}

		@Override
		public void close() throws Exception
		{
			background.close();
		}
	}

	private record QuestCapFixture(AcquisitionAtomicFixture atomic, Rule rule, Map<String, String> expectedQuestRows, Map<String, String> originalQuestRows) implements AutoCloseable
	{
		private QuestCapFixture
		{
			expectedQuestRows = Map.copyOf(expectedQuestRows);
			originalQuestRows = Map.copyOf(originalQuestRows);
		}

		private long profileId()
		{
			return atomic.profileId();
		}

		private int characterObjectId()
		{
			return atomic.characterObjectId();
		}

		private PhantomGoal goal()
		{
			return atomic.goal();
		}

		private PhantomBackgroundTransaction transaction()
		{
			return atomic.transaction();
		}

		private PhantomBackgroundState ready()
		{
			return atomic.ready();
		}

		private PhantomGoalStateStore goals()
		{
			return atomic.goals();
		}

		private PhantomAcquisitionStore acquisition()
		{
			return atomic.acquisition();
		}

		private long goalRowVersion()
		{
			return atomic.goalRowVersion();
		}

		private long stateRowVersion()
		{
			return atomic.stateRowVersion();
		}

		private PhantomAcquisitionState acquisitionState()
		{
			return acquisition().load(profileId()).orElseThrow().state();
		}

		@Override
		public void close() throws Exception
		{
			try
			{
				atomic.close();
			}
			finally
			{
				replaceQuestRows(characterObjectId(), rule.questName(), originalQuestRows);
			}
		}
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

	private record ProductionTravelSelection(PhantomTopologyAnchor departure, PhantomTopologyAnchor arrival, PhantomTopologyEdge edge)
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
