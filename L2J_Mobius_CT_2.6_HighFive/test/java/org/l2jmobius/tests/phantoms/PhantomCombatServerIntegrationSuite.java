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
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import org.l2jmobius.gameserver.ai.Intention;
import org.l2jmobius.gameserver.data.xml.DoorData;
import org.l2jmobius.gameserver.data.xml.MapRegionData;
import org.l2jmobius.gameserver.data.xml.NpcData;
import org.l2jmobius.gameserver.data.xml.SkillData;
import org.l2jmobius.gameserver.data.xml.SpawnData;
import org.l2jmobius.gameserver.data.SpawnTable;
import org.l2jmobius.gameserver.config.RatesConfig;
import org.l2jmobius.gameserver.managers.InstanceManager;
import org.l2jmobius.gameserver.managers.ItemManager;
import org.l2jmobius.gameserver.managers.ScriptManager;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.instance.GrandBoss;
import org.l2jmobius.gameserver.model.actor.instance.Monster;
import org.l2jmobius.gameserver.model.actor.instance.RaidBoss;
import org.l2jmobius.gameserver.model.actor.templates.NpcTemplate;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.model.item.enums.ShotType;
import org.l2jmobius.gameserver.model.item.instance.Item;
import org.l2jmobius.gameserver.model.script.QuestState;
import org.l2jmobius.gameserver.model.script.State;
import org.l2jmobius.gameserver.model.skill.Skill;
import org.l2jmobius.gameserver.phantoms.PhantomDiagnosticTrace;
import org.l2jmobius.gameserver.phantoms.PhantomMetrics;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionCatalog;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionCatalog.Method;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionGoalSpec;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionService;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionSourcePlanner;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionSourcePlanner.QuestEvidence;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionSourcePlanner.RankedSource;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionSourcePlanner.ResourceEvidence;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.Hashes;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.ManorBinding;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.MethodBinding;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.Phase;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.QuestBinding;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.ReceiptKind;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.Source;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.Status;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.TerminalResult;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionStore;
import org.l2jmobius.gameserver.phantoms.acquisition.manor.PhantomAcquisitionManorAuthority;
import org.l2jmobius.gameserver.phantoms.acquisition.manor.PhantomAcquisitionManorAuthority.Candidate;
import org.l2jmobius.gameserver.phantoms.acquisition.quest.PhantomAcquisitionQuestCatalog;
import org.l2jmobius.gameserver.phantoms.acquisition.quest.PhantomAcquisitionQuestCatalog.Rule;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundCompetitionRegistry;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundService;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundTransaction;
import org.l2jmobius.gameserver.phantoms.combat.L2jCombatBackend;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatActorLease;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.AcquisitionSkillKind;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.AcquisitionTargetSnapshot;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.ActionOutcome;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.ActorSnapshot;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.LootCandidate;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.LootObservation;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.RespawnOutcome;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.ShotOutcome;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatCapabilityResolver;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatLoadout.SelectedSkill;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatMode;
import org.l2jmobius.gameserver.phantoms.combat.PhantomOwnedAction;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatPolicy;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatRequest;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatResult;
import org.l2jmobius.gameserver.phantoms.combat.PhantomRespawnRequest;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatService.CancelStatus;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatService.ExternalActionKind;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatService.ExternalActionLease;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatService.ExternalActionRequest;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatService.ExternalActionStatus;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatService;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatService.StartStatus;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatSessionSnapshot;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoal;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateStore;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus;
import org.l2jmobius.gameserver.phantoms.knowledge.L2jGameKnowledgeBackend;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomCuratedKnowledgeParser;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeBuilder;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeAuthority;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.DropFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.NpcFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.NpcKind;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.SpawnFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.SpawnPointKind;
import org.l2jmobius.gameserver.model.spawns.Spawn;
import org.l2jmobius.gameserver.model.zone.type.NpcSpawnTerritory;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgePolicy;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeQuery;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeService;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomStaticManorParser;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationService;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService.ResultStatus;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializedPlayer.ActionLease;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfile;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.gameserver.phantoms.progression.L2jProgressionBackend;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionCatalog;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionCatalogBuilder;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionPolicy;
import org.l2jmobius.gameserver.phantoms.topology.L2jTopologyValidationBackend;
import org.l2jmobius.gameserver.phantoms.topology.PhantomRelevanceSignalPort;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyLoader;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyMetrics;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPolicy;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyQuery;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologySnapshot;
import org.l2jmobius.gameserver.scripting.ScriptEngine;

public final class PhantomCombatServerIntegrationSuite implements PhantomTestSuite
{
	public enum Mode
	{
		BASELINE,
		ACQUISITION,
		MANOR,
		QUEST
	}

	private static final long ACQUISITION_SEED = 21002101L;
	private static final long CHECKPOINT_2_SEED = 21002102L;
	private static final int MELEE_CLASS_ID = 88;
	private static final int MAGIC_CLASS_ID = 94;
	private static final int MAGIC_SKILL_ID = 1339;
	private static final int WEAPON_ITEM_ID = 6;
	private static final int SOULSHOT_ITEM_ID = 1835;
	private static final int ADENA_ITEM_ID = 57;
	private static final long WAIT_MILLIS = 10000;
	private static final int SPOIL_CLASS_ID = 117;
	private static final int SPOIL_SKILL_ID = 254;
	private static final int SWEEP_SKILL_ID = 42;

	private final Mode _mode;
	private final PhantomHeadlessPlayerTestEnvironment _environment = new PhantomHeadlessPlayerTestEnvironment();
	private final List<Monster> _worldFixtures = new ArrayList<>();
	private PhantomProfileRepository _repository;
	private PhantomProfile _profile;
	private PhantomMaterializationService _materialization;
	private PhantomGameKnowledgeService _knowledge;
	private PhantomGameKnowledgeQuery _query;
	private PhantomCombatService _combat;
	private L2jCombatBackend _backend;
	private Player _player;
	private Player _observer;
	private SpawnFact _combatPoint;
	private Source _acquisitionSource;
	private PhantomProgressionCatalog _progression;
	private Path _moduleRoot;
	private float _spoilChanceRateBaseline;
	private double _acquisitionRawItemChance;
	private PhantomAcquisitionManorAuthority _manorAuthority;
	private Candidate _manorCandidate;
	private int _manorPlayerLevel;
	private PhantomAcquisitionQuestCatalog _questCatalog;
	private List<QuestSource> _questSources = List.of();
	private Rule _questRule;
	private PhantomTopologyQuery _topology;
	private NpcSpawnTerritory _sourceTerritory;
	private PhantomBackgroundSuite.ProductionAuthorityFixture _backgroundProduction;
	private PhantomAcquisitionCatalog _acquisitionCatalog;
	private PhantomAcquisitionSourcePlanner _acquisitionPlanner;
	private PhantomGoalStateStore _acquisitionGoals;
	private PhantomAcquisitionStore _acquisitionStore;
	private PhantomBackgroundService _background;
	private PhantomNavigationService _navigation;
	private PhantomAcquisitionService _acquisition;
	private final AtomicLong _epochMillis = new AtomicLong(1_800_000_000_000L);
	private long _acquisitionRevision;

	public PhantomCombatServerIntegrationSuite()
	{
		this(Mode.BASELINE);
	}

	public PhantomCombatServerIntegrationSuite(Mode mode)
	{
		_mode = mode;
	}

	@Override
	public String id()
	{
		return switch (_mode)
		{
			case ACQUISITION -> "acquisition-active-spoil";
			case MANOR -> "acquisition-manor-active";
			case QUEST -> "acquisition-quest-active";
			default -> "combat-server-integration";
		};
	}

	@Override
	public void beforeAll(PhantomTestContext context) throws Exception
	{
		if (_mode == Mode.ACQUISITION)
		{
			PhantomAssertions.assertEquals(ACQUISITION_SEED, context.seed(), "Active acquisition mode used the wrong seed.");
		}
		else if ((_mode == Mode.MANOR) || (_mode == Mode.QUEST))
		{
			PhantomAssertions.assertEquals(CHECKPOINT_2_SEED, context.seed(), "Goal 021 Checkpoint 2 active mode used the wrong seed.");
		}
		_moduleRoot = context.moduleRoot();
		_environment.initialize(context);
		try
		{
			ScriptEngine.getInstance().executeScript(ScriptEngine.MASTER_HANDLER_FILE);
			MapRegionData.getInstance();
			SpawnData.getInstance();
			DoorData.getInstance();

			final L2jTopologyValidationBackend topologyBackend = new L2jTopologyValidationBackend();
			final PhantomTopologySnapshot topology = new PhantomTopologyLoader(Path.of("data/phantoms/topology"), topologyBackend, PhantomTopologyPolicy.productionDefaults()).load(1);
			final PhantomTopologyQuery topologyQuery = new PhantomTopologyQuery(topology, topologyBackend, new PhantomTopologyMetrics());
			_topology = topologyQuery;
			final PhantomGameKnowledgePolicy knowledgePolicy = PhantomGameKnowledgePolicy.productionDefaults();
			final PhantomGameKnowledgeBuilder builder = new PhantomGameKnowledgeBuilder(new L2jGameKnowledgeBackend(), new PhantomStaticManorParser(Path.of("data/Seeds.xml"), knowledgePolicy), new PhantomCuratedKnowledgeParser(Path.of("data/phantoms/knowledge"), new L2jGameKnowledgeBackend(), knowledgePolicy), topologyQuery, knowledgePolicy);
			_knowledge = new PhantomGameKnowledgeService(builder);
			PhantomAssertions.assertTrue(_knowledge.start(), "Game Knowledge service did not start.");
			_query = _knowledge.query();
			final PhantomProgressionPolicy progressionPolicy = PhantomProgressionPolicy.productionDefaults();
			_progression = new PhantomProgressionCatalogBuilder().build(new L2jProgressionBackend(null, Path.of("."), () -> _query).load(progressionPolicy), progressionPolicy);
			if (_mode == Mode.ACQUISITION)
			{
				_acquisitionSource = selectAcquisitionSource(topologyQuery, context);
				_combatPoint = _query.snapshot().spawnFactsByNpc().getOrDefault(_acquisitionSource.npcId(), List.of()).stream().filter(fact -> (fact.pointKind() == SpawnPointKind.EXACT) && (fact.instanceId() == 0)).findFirst().orElseThrow(() -> new AssertionError("Acquisition source has no exact normal-world spawn."));
				_spoilChanceRateBaseline = RatesConfig.RATE_SPOIL_DROP_CHANCE_MULTIPLIER;
				RatesConfig.RATE_SPOIL_DROP_CHANCE_MULTIPLIER = Math.max(_spoilChanceRateBaseline, (float) Math.ceil(100 / _acquisitionRawItemChance));
			}
			else if (_mode == Mode.MANOR)
			{
				_manorAuthority = new PhantomAcquisitionManorAuthority(_query, topologyQuery, Path.of("data/mapregion"));
				_manorCandidate = selectManorCandidate();
				_combatPoint = sourceCombatPoint(_manorCandidate.npcId(), _manorCandidate.topologyNodeId(), _manorCandidate.anchorId());
				_sourceTerritory = sourceTerritory(_manorCandidate.npcId(), _manorCandidate.topologyNodeId());
			}
			else if (_mode == Mode.QUEST)
			{
				_questCatalog = PhantomAcquisitionQuestCatalog.load(Path.of("data/phantoms/acquisition/high-five-quest-collection-v1.xml"), Path.of("data/scripts"));
				ScriptEngine.getInstance().executeScript(Path.of("quests/QuestMasterHandler.java"));
				_questCatalog.validateRuntime();
				_questSources = questSources(topologyQuery);
				selectQuestSource(_questSources.getFirst());
			}
			else
			{
				_combatPoint = selectCombatPoint();
			}

			_repository = PhantomProfileRepository.open();
			_profile = _repository.create(_environment.primary().objectId());
			final PhantomMetrics metrics = new PhantomMetrics();
			_materialization = new PhantomMaterializationService(_repository, PhantomIdentityLeaseRegistry.getInstance(), metrics, new PhantomDiagnosticTrace(false, 0, 0, metrics), 1);
			PhantomAssertions.assertTrue(_materialization.start(), "Materialization service did not start.");
			PhantomAssertions.assertEquals(ResultStatus.SUCCESS, _materialization.materialize(_profile.profileId()).status(), "Test actor did not materialize.");
			_player = World.getInstance().getPlayer(_environment.primary().objectId());
			PhantomAssertions.assertTrue(_player != null, "Materialized World Player is absent.");
			if (_mode == Mode.ACQUISITION)
			{
				prepareAcquisitionActor();
			}
			relocateToCombatPoint();
			if (_mode == Mode.MANOR)
			{
				prepareManorActor();
			}
			else if (_mode == Mode.QUEST)
			{
				prepareQuestActor();
			}

			_backend = new L2jCombatBackend(_materialization, () -> _query, () -> _progression);
			_combat = new PhantomCombatService(_backend, PhantomCombatCapabilityResolver.fromGameKnowledge(() -> _query), PhantomCombatPolicy.productionDefaults(1));
			_combat.start();
			if ((_mode == Mode.MANOR) || (_mode == Mode.QUEST))
			{
				_backgroundProduction = PhantomBackgroundSuite.ProductionAuthorityFixture.start();
				_acquisitionCatalog = PhantomAcquisitionCatalog.load(Path.of("data/phantoms/acquisition/high-five-acquisition-v1.xml"));
				_acquisitionPlanner = new PhantomAcquisitionSourcePlanner(_acquisitionCatalog, _query, _topology, _progression, _manorAuthority, _questCatalog);
				_acquisitionGoals = new PhantomGoalStateStore(_repository);
				_acquisitionStore = new PhantomAcquisitionStore(_repository, _acquisitionGoals);
				_background = new PhantomBackgroundService(_repository, _acquisitionGoals, PhantomIdentityLeaseRegistry.getInstance(), new PhantomBackgroundTransaction(), _backgroundProduction.authority(), new PhantomBackgroundCompetitionRegistry(), noSignals(), () -> _materialization);
				_navigation = new PhantomNavigationService(new PhantomMetrics());
				_acquisition = new PhantomAcquisitionService(_acquisitionCatalog, _acquisitionStore, _acquisitionGoals, _acquisitionPlanner, _query, _topology, _progression, _combat, _background, _navigation, _manorAuthority, _questCatalog, _epochMillis::get);
				PhantomAssertions.assertTrue(_acquisition.start(), "Full acquisition integration service did not start.");
			}

			context.record("combatIntegration.database", PhantomTestDatabaseGuard.TARGET_DATABASE);
			context.record("combatIntegration.profileId", _profile.profileId());
			context.record("combatIntegration.actorObjectId", _player.getObjectId());
			context.record("combatIntegration.normalNpcId", _combatPoint.npcId());
			if (_mode == Mode.ACQUISITION)
			{
				context.record("acquisition.activeItemId", _acquisitionSource.itemId());
				context.record("acquisition.activeSourceId", _acquisitionSource.sourceId());
			}
			else if (_mode == Mode.MANOR)
			{
				context.record("acquisition.manorSourceId", _manorCandidate.sourceId());
				context.record("acquisition.manorSeedItemId", _manorCandidate.fact().seedItemId());
			}
			else if (_mode == Mode.QUEST)
			{
				context.record("acquisition.questRule", _questRule.id());
				context.record("acquisition.questScriptHash", _questRule.scriptHash());
			}
		}
		catch (Throwable throwable)
		{
			cleanup();
			throw throwable;
		}
	}

	@Override
	public void afterAll(PhantomTestContext context) throws Exception
	{
		cleanup();
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		if (_mode == Mode.ACQUISITION)
		{
			registry.add("01-exact-target-skill-distance-instance-and-ownership", _ -> testAcquisitionControls());
			registry.add("02-canonical-spoil-existing-combat-sweep-inventory", _ -> testCanonicalAcquisitionChain());
			registry.add("03-dispatch-crash-recovery-never-blind-repeats", _ -> testAcquisitionDispatchRecovery());
			return;
		}
		if (_mode == Mode.MANOR)
		{
			registry.add("01-real-seed-combat-harvester-chain", _ -> testCanonicalManorChain());
			registry.add("02-manor-controls-and-dispatch-recovery", _ -> testManorControls());
			return;
		}
		if (_mode == Mode.QUEST)
		{
			registry.add("01-real-delayed-on-attackable-kill", _ -> testCanonicalQuestChain());
			registry.add("02-exact-state-cond-target-and-cap-controls", _ -> testQuestControls());
			registry.add("03-full-service-owned-combat-and-epoch-recovery", _ -> testQuestServiceLifecycle());
			return;
		}
		registry.add("01-exact-world-player-action-lease", _ -> testExactActorLease());
		registry.add("02-canonical-player-ai-attack-and-death", _ -> testCanonicalAttack());
		registry.add("03-canonical-selected-skill-cast", _ -> testCanonicalCast());
		registry.add("04-canonical-shot-conservation-and-discharge", _ -> testCanonicalShot());
		registry.add("05-missing-shot-does-not-fabricate", _ -> testMissingShot());
		registry.add("06-canonical-ground-item-pickup", _ -> testCanonicalLoot());
		registry.add("07-player-raid-grandboss-rejected", _ -> testForbiddenTargets());
		registry.add("08-cancel-only-owned-action", _ -> testOwnedCancellation());
		registry.add("09-other-player-pickup-is-not-acquisition", _ -> testOtherPlayerPickupEvidence());
		registry.add("10-despawn-is-not-acquisition", _ -> testDespawnEvidence());
		registry.add("11-range-loss-is-not-acquisition", _ -> testRangeLossEvidence());
		registry.add("12-cancel-during-exact-pickup", _ -> testPickupCleanup(false));
		registry.add("13-stop-during-exact-pickup", _ -> testPickupCleanup(true));
		registry.add("14-foreign-cast-and-pickup-survive", _ -> testForeignCastAndPickup());
		registry.add("15-positive-one-target-skill-rejected", _ -> testPositiveSkillRejected());
		registry.add("16-player-death-releases-ownership", _ -> testPlayerDeath());
		registry.add("17-restricted-normal-town-respawn", _ -> testNormalTownRespawn());
		registry.add("18-production-combat-has-no-packet-route", _ -> testNoPacketRoute());
		registry.add("19-canonical-player-cp-snapshot", _ -> testCanonicalCpSnapshot());
		registry.add("20-dematerialization-waits-for-combat-lease", _ -> testDematerializationDrain());
	}

	private Source selectAcquisitionSource(PhantomTopologyQuery topology, PhantomTestContext context)
	{
		final PhantomAcquisitionCatalog catalog = PhantomAcquisitionCatalog.load(context.moduleRoot().resolve("dist/game/data/phantoms/acquisition/high-five-acquisition-v1.xml"));
		final PhantomAcquisitionSourcePlanner planner = new PhantomAcquisitionSourcePlanner(catalog, _query, topology, _progression);
		Source best = null;
		double bestChance = -1;
		for (int itemId : _query.snapshot().spoilSourcesByItem().keySet().stream().sorted().toList())
		{
			final var request = new PhantomAcquisitionSourcePlanner.Request(1, itemId, 1, PhantomActivityState.ACTIVE, SPOIL_CLASS_ID, 85, Map.of(), Map.of(SPOIL_SKILL_ID, 11, SWEEP_SKILL_ID, 1), Set.of(Method.SPOIL_SWEEP), Method.SPOIL_SWEEP, "", Map.of(), 0);
			final var result = planner.plan(request);
			if (result.selected() == null)
			{
				continue;
			}
			final Source source = result.selected().source();
			if (_query.snapshot().spawnFactsByNpc().getOrDefault(source.npcId(), List.of()).stream().noneMatch(fact -> (fact.pointKind() == SpawnPointKind.EXACT) && (fact.instanceId() == 0)))
			{
				continue;
			}
			final double chance = _query.snapshot().spoilSourcesByItem().getOrDefault(itemId, List.of()).stream().filter(fact -> fact.stableKey().equals(source.factKey())).mapToDouble(DropFact::rawItemChance).findFirst().orElse(0);
			if ((chance > bestChance) || ((Double.compare(chance, bestChance) == 0) && ((best == null) || (source.sourceId().compareTo(best.sourceId()) < 0))))
			{
				best = source;
				bestChance = chance;
			}
		}
		if (best == null)
		{
			throw new AssertionError("No production spoil/sweep source has an exact normal-world spawn.");
		}
		context.record("acquisition.activeRawItemChance", bestChance);
		_acquisitionRawItemChance = bestChance;
		return best;
	}

	private Candidate selectManorCandidate()
	{
		for (int cropItemId : _query.snapshot().manorFacts().stream().map(fact -> fact.cropItemId()).distinct().sorted().toList())
		{
			final Map<Integer, Long> inventory = _manorAuthority.probe(cropItemId).requiredItemIds().stream().collect(java.util.stream.Collectors.toMap(itemId -> itemId, _ -> 64L));
			for (int playerLevel = 1; playerLevel <= 85; playerLevel++)
			{
				final var result = _manorAuthority.candidates(cropItemId, playerLevel, inventory);
				final int currentLevel = playerLevel;
				final Candidate levelMatched = result.candidates().stream().filter(candidate -> candidate.npcLevel() == currentLevel).findFirst().orElse(null);
				if (levelMatched != null)
				{
					_manorPlayerLevel = playerLevel;
					return levelMatched;
				}
			}
		}
		throw new AssertionError("No current manor candidate has a real normal-world target.");
	}

	private SpawnFact sourceCombatPoint(int npcId, String nodeId, String anchorId)
	{
		final SpawnFact fact = _query.snapshot().spawnFactsByNpc().getOrDefault(npcId, List.of()).stream().filter(value -> nodeId.equals(value.topologyNodeId()) && (value.territoryGeometry() != null)).findFirst().orElseThrow(() -> new AssertionError("Mapped source has no territory fact: " + npcId));
		final var anchor = _topology.snapshot().anchorById().get(anchorId);
		return new SpawnFact(npcId, fact.spawnOrdinal(), fact.instanceId(), anchor.point().x(), anchor.point().y(), anchor.point().z(), fact.amount(), fact.locationId(), SpawnPointKind.EXACT, nodeId, anchor.mapRegionLocId(), PhantomGameKnowledgeAuthority.TOPOLOGY_SNAPSHOT_FACT);
	}

	private NpcSpawnTerritory sourceTerritory(int npcId, String nodeId)
	{
		final var geometry = _query.snapshot().spawnFactsByNpc().getOrDefault(npcId, List.of()).stream().filter(fact -> nodeId.equals(fact.topologyNodeId()) && (fact.territoryGeometry() != null)).map(SpawnFact::territoryGeometry).distinct().findFirst().orElseThrow(() -> new AssertionError("Mapped source geometry is missing."));
		return SpawnTable.getInstance().getSpawns(npcId).stream().map(Spawn::getSpawnTerritory).filter(java.util.Objects::nonNull).filter(territory -> territory.geometrySnapshot().map(snapshot -> snapshot.hash().equals(geometry.geometryHash()) && snapshot.sourcePath().equals(geometry.sourcePath()) && snapshot.territoryName().equals(geometry.territoryName())).orElse(false)).findFirst().orElseThrow(() -> new AssertionError("Runtime Spawn territory differs from Game Knowledge source."));
	}

	private List<QuestSource> questSources(PhantomTopologyQuery topology)
	{
		final List<QuestSource> sources = new ArrayList<>();
		for (Rule rule : _questCatalog.rules())
		{
			for (int npcId : rule.targetNpcIds())
			{
				final SpawnFact fact = _query.snapshot().spawnFactsByNpc().getOrDefault(npcId, List.of()).stream().filter(value -> (value.instanceId() == 0) && (value.amount() > 0) && (value.topologyNodeId() != null) && (value.territoryGeometry() != null)).min(Comparator.comparing(SpawnFact::stableKey)).orElseThrow(() -> new AssertionError("Curated quest target has no mapped source territory: " + npcId));
				final var anchor = topology.snapshot().anchorsByNode().getOrDefault(fact.topologyNodeId(), List.of()).stream().filter(value -> value.point().instanceId() == 0).min(Comparator.comparing(value -> value.id())).orElseThrow(() -> new AssertionError("Curated quest source has no normal-world anchor: " + npcId));
				sources.add(new QuestSource(rule, npcId, sourceCombatPoint(npcId, fact.topologyNodeId(), anchor.id()), sourceTerritory(npcId, fact.topologyNodeId())));
			}
		}
		PhantomAssertions.assertEquals(3, sources.size(), "The curated catalog must expose exactly three separately covered quest targets.");
		PhantomAssertions.assertEquals(List.of(20013, 20019, 20016), sources.stream().map(QuestSource::npcId).toList(), "Curated quest target coverage drifted.");
		return List.copyOf(sources);
	}

	private void selectQuestSource(QuestSource source)
	{
		_questRule = source.rule();
		_combatPoint = source.combatPoint();
		_sourceTerritory = source.territory();
	}

	private void prepareManorActor()
	{
		_player.setPlayerClass(MELEE_CLASS_ID);
		_player.getStat().setLevel((byte) _manorPlayerLevel);
		_player.setInvul(true);
		_player.getItemReuseTimeStamps().clear();
		ensureWeapon();
		ensureInventoryItem(_manorCandidate.fact().seedItemId(), 64);
		ensureInventoryItem(PhantomAcquisitionManorAuthority.HARVESTER_ITEM_ID, 1);
		final var anchor = _topology.snapshot().anchorById().get(_manorCandidate.anchorId());
		PhantomAssertions.assertTrue(anchor != null, "Selected manor anchor disappeared.");
		if (_player.isSpawned())
		{
			_player.decayMe();
		}
		_player.setXYZInvisible(anchor.point().x(), anchor.point().y(), anchor.point().z());
		_player.spawnMe();
		_player.revalidateZone(true);
		_player.setCurrentHp(_player.getMaxHp());
		_player.setCurrentMp(_player.getMaxMp());
	}

	private void prepareQuestActor()
	{
		_player.setPlayerClass(MELEE_CLASS_ID);
		_player.getStat().setLevel((byte) 85);
		_player.setInvul(true);
		ensureWeapon();
		final var quest = ScriptManager.getInstance().getQuest(_questRule.questId());
		PhantomAssertions.assertTrue(quest != null, "Curated quest was not loaded.");
		final QuestState state = _player.getQuestState(_questRule.questName()) == null ? new QuestState(quest, _player, State.STARTED) : _player.getQuestState(_questRule.questName());
		state.setCond(_questRule.allowedConds().getFirst(), false);
		_player.setCurrentHp(_player.getMaxHp());
		_player.setCurrentMp(_player.getMaxMp());
		_player.setCurrentCp(_player.getMaxCp());
	}

	private PhantomGoal installAcquisition(RankedSource selected, List<RankedSource> ranked, MethodBinding binding, long baseline, long required, Phase phase, int targetObjectId)
	{
		final long revision = ++_acquisitionRevision;
		final Source source = selected.source();
		final PhantomGoal goal = new PhantomGoal(21, PhantomAcquisitionGoalSpec.GOAL_TYPE, PhantomGoalStatus.ACTIVE, new PhantomDomainRef("profile", "self"), new PhantomDomainRef("item", Integer.toString(source.itemId())), required, 0, source.method().key(), List.of(new PhantomDomainRef(PhantomAcquisitionGoalSpec.SOURCE_NAMESPACE, source.method().key())), new PhantomDomainRef(PhantomAcquisitionGoalSpec.ANCHOR_NAMESPACE, source.anchorId()), PhantomAcquisitionGoalSpec.PURPOSE_KEY, 500, 0, 0, 0, Map.of(PhantomAcquisitionGoalSpec.BASELINE_CONSTRAINT, baseline, PhantomAcquisitionGoalSpec.MAXIMUM_SWITCHES_CONSTRAINT, 4L), "acquisition.checkpoint2.service", revision);
		final List<PhantomAcquisitionState.Candidate> candidates = ranked.stream().map(value -> new PhantomAcquisitionState.Candidate(value.source().sourceId(), value.source().method(), value.score(), 0, 0, "")).toList();
		final int cursor = java.util.stream.IntStream.range(0, ranked.size()).filter(index -> ranked.get(index).source().sourceId().equals(source.sourceId())).findFirst().orElseThrow();
		final PhantomBackgroundState.Hashes background = _background.authorityHashes();
		final Hashes hashes = new Hashes(_acquisitionCatalog.hash(), _query.snapshot().combinedHash(), _topology.snapshot().canonicalHash(), _progression.combinedHash().toLowerCase(java.util.Locale.ROOT), canonicalDigest(background.knowledge(), background.topology(), background.progression(), background.commerce()));
		final boolean targeted = phase != Phase.TARGET_REQUIRED;
		final PhantomAcquisitionState state = new PhantomAcquisitionState(hashes, goal.goalId(), goal.revision(), source.itemId(), required, baseline, baseline, 0, targeted ? Status.ACTIVE : Status.READY, source, candidates, cursor, 0, phase, targeted ? targetObjectId : 0, targeted ? source.npcId() : 0, 0, null, binding, List.of(), 0, revision);
		final var storedGoal = _acquisitionGoals.load(_profile.profileId());
		final var storedState = _acquisitionStore.load(_profile.profileId());
		if (storedGoal.isEmpty() && storedState.isEmpty())
		{
			_acquisitionGoals.insert(_profile.profileId(), goal);
			_acquisitionStore.insert(_profile.profileId(), state);
		}
		else
		{
			PhantomAssertions.assertTrue(storedGoal.isPresent() && storedState.isPresent(), "Acquisition Goal/state setup became partial.");
			_acquisitionStore.mutateWithGoal(_profile.profileId(), storedState.orElseThrow().rowVersion(), state, storedGoal.orElseThrow().rowVersion(), goal);
		}
		return goal;
	}

	private PhantomAcquisitionService.OperationResult advanceAcquisition(PhantomGoal goal, long sequence)
	{
		return _acquisition.activeAdvance(_profile.profileId(), goal, PhantomActivityState.ACTIVE, 1, sequence, 1_000_000 + sequence, sequence, () -> false);
	}

	private void restartAcquisitionService()
	{
		_acquisition.beginStop();
		PhantomAssertions.assertTrue(_acquisition.finishStop(), "Acquisition restart retained an owned claim.");
		_acquisition = new PhantomAcquisitionService(_acquisitionCatalog, _acquisitionStore, _acquisitionGoals, _acquisitionPlanner, _query, _topology, _progression, _combat, _background, _navigation, _manorAuthority, _questCatalog, _epochMillis::get);
		PhantomAssertions.assertTrue(_acquisition.start(), "Acquisition restart did not start.");
	}

	private PhantomAcquisitionState acquisitionState()
	{
		return _acquisitionStore.load(_profile.profileId()).orElseThrow().state();
	}

	private PhantomAcquisitionSourcePlanner.Result planManor(long baseline, long required)
	{
		final Map<Integer, Long> inventory = Map.of(_manorCandidate.fact().seedItemId(), _player.getInventory().getInventoryItemCount(_manorCandidate.fact().seedItemId(), -1), _manorCandidate.fact().cropItemId(), baseline, PhantomAcquisitionManorAuthority.HARVESTER_ITEM_ID, _player.getInventory().getInventoryItemCount(PhantomAcquisitionManorAuthority.HARVESTER_ITEM_ID, -1));
		return _acquisitionPlanner.plan(new PhantomAcquisitionSourcePlanner.Request(_profile.profileId(), _manorCandidate.fact().cropItemId(), required, PhantomActivityState.ACTIVE, MELEE_CLASS_ID, _player.getLevel(), inventory, Map.of(), Set.of(Method.MANOR_CROP), Method.MANOR_CROP, _manorCandidate.anchorId(), "", ResourceEvidence.unavailable(), Map.of(), 1));
	}

	private PhantomAcquisitionSourcePlanner.Result planQuest(QuestSource questSource, long baseline)
	{
		final Rule rule = questSource.rule();
		final QuestEvidence evidence = new QuestEvidence("STARTED", rule.allowedConds().getFirst(), Map.of(), baseline);
		return _acquisitionPlanner.plan(new PhantomAcquisitionSourcePlanner.Request(_profile.profileId(), rule.questItemId(), 1, PhantomActivityState.ACTIVE, MELEE_CLASS_ID, _player.getLevel(), Map.of(rule.questItemId(), baseline), Map.of(), Set.of(Method.QUEST_COLLECTION), Method.QUEST_COLLECTION, "", "", ResourceEvidence.unavailable(), Map.of(), 1, Map.of(rule.id(), evidence)));
	}

	private static RankedSource exactRanked(PhantomAcquisitionSourcePlanner.Result result, int npcId, String nodeId)
	{
		return result.ranked().stream().filter(value -> (value.source().npcId() == npcId) && value.source().topologyNodeId().equals(nodeId)).findFirst().or(() -> result.ranked().stream().filter(value -> value.source().npcId() == npcId).findFirst()).orElseThrow(() -> new AssertionError("Planner did not expose the exact production source."));
	}

	private QuestSource plannerQuestSource(Rule rule, RankedSource ranked)
	{
		final Source source = ranked.source();
		return new QuestSource(rule, source.npcId(), sourceCombatPoint(source.npcId(), source.topologyNodeId(), source.anchorId()), sourceTerritory(source.npcId(), source.topologyNodeId()));
	}

	private Item ensureInventoryItem(int itemId, long count)
	{
		Item item = _player.getInventory().getItemByItemId(itemId);
		final long current = item == null ? 0 : item.getCount();
		if (current < count)
		{
			item = _player.getInventory().addItem(ItemProcessType.REWARD, itemId, count - current, _player, this);
		}
		PhantomAssertions.assertTrue(item != null, "Could not create test-owned inventory item: " + itemId);
		return item;
	}

	private void testCanonicalManorChain() throws Exception
	{
		prepareManorActor();
		Monster seededTarget = null;
		boolean observedFailure = false;
		for (int attempt = 0; (attempt < 32) && (seededTarget == null); attempt++)
		{
			final Monster target = spawnNormalMonster(targetMaximumHp());
			try (ExternalActionLease lease = acquireAcquisition("manor-sow-" + attempt))
			{
				final var inventory = lease.manorInventory(_manorCandidate.fact().seedItemId(), _manorCandidate.fact().cropItemId(), PhantomAcquisitionManorAuthority.HARVESTER_ITEM_ID);
				final long before = inventory.seedCount();
				final AcquisitionTargetSnapshot live = lease.acquisitionTargetSnapshot(target.getObjectId());
				PhantomAssertions.assertTrue((live != null) && live.manorLiveValidFor(lease.actorSnapshot(), _manorCandidate.npcId(), 2000), "Exact manor target was not sow-eligible.");
				PhantomAssertions.assertEquals(_sourceTerritory.geometrySnapshot().orElseThrow().hash(), live.territoryGeometryHash(), "Manor target did not retain selected factual territory ownership.");
				boolean consumed = false;
				for (int dispatch = 0; (dispatch < 3) && !consumed; dispatch++)
				{
					final ActionOutcome outcome = lease.useExactSeed(inventory.seedObjectId(), _manorCandidate.fact().seedItemId(), target.getObjectId());
					PhantomAssertions.assertTrue((outcome == ActionOutcome.ISSUED) || (outcome == ActionOutcome.ALREADY_OWNED), "Canonical Seed handler was not issued.");
					consumed = waitFor(() -> _player.getInventory().getInventoryItemCount(_manorCandidate.fact().seedItemId(), -1) < before, 4000);
				}
				PhantomAssertions.assertTrue(consumed, "Canonical sow attempts did not consume exactly one seed.");
				PhantomAssertions.assertEquals(before - 1, _player.getInventory().getInventoryItemCount(_manorCandidate.fact().seedItemId(), -1), "Canonical sow consumed an unexpected seed count.");
				if (target.isSeeded())
				{
					PhantomAssertions.assertEquals(_player.getObjectId(), target.getSeederId(), "Canonical sow recorded the wrong seeder.");
					PhantomAssertions.assertEquals(_manorCandidate.fact().seedItemId(), target.getSeed().getSeedId(), "Canonical sow recorded the wrong seed.");
					seededTarget = target;
				}
				else
				{
					observedFailure = true;
				}
			}
		}
		PhantomAssertions.assertTrue(seededTarget != null, "Bounded canonical sow attempts produced no successful seeded target.");
		final Monster harvestTarget = seededTarget;
		try (ExternalActionLease recovered = acquireAcquisition("manor-sow-recovery"))
		{
			final var inventory = recovered.manorInventory(_manorCandidate.fact().seedItemId(), _manorCandidate.fact().cropItemId(), PhantomAcquisitionManorAuthority.HARVESTER_ITEM_ID);
			PhantomAssertions.assertEquals(ActionOutcome.REJECTED, recovered.useExactSeed(inventory.seedObjectId(), _manorCandidate.fact().seedItemId(), harvestTarget.getObjectId()), "Observed sow was blindly dispatched twice.");
		}
		runManorServiceAttribution(harvestTarget);
		// A normal seed has a high canonical chance; the explicit formula suite supplies deterministic failed-attempt coverage.
		PhantomAssertions.assertTrue(observedFailure || (_manorCandidate.sowChance() >= 1), "Canonical sow failure evidence became inconsistent.");
	}

	private void testManorControls()
	{
		prepareManorActor();
		final Monster liveTarget = spawnNormalMonster(targetMaximumHp());
		try (ExternalActionLease lease = acquireAcquisition("manor-controls"))
		{
			final var inventory = lease.manorInventory(_manorCandidate.fact().seedItemId(), _manorCandidate.fact().cropItemId(), PhantomAcquisitionManorAuthority.HARVESTER_ITEM_ID);
			PhantomAssertions.assertTrue((inventory.seedObjectId() > 0) && (inventory.harvesterObjectId() > 0), "Exact seed/harvester ownership was not observed.");
			PhantomAssertions.assertEquals(ActionOutcome.REJECTED, lease.useExactSeed(inventory.seedObjectId() + 1, _manorCandidate.fact().seedItemId(), liveTarget.getObjectId()), "Wrong seed object was admitted.");
			PhantomAssertions.assertEquals(ActionOutcome.REJECTED, lease.useExactSeed(inventory.seedObjectId(), _manorCandidate.fact().seedItemId(), liveTarget.getObjectId() + 1), "Wrong manor target was admitted.");
			final ActorSnapshot actor = lease.actorSnapshot();
			final AcquisitionTargetSnapshot base = lease.acquisitionTargetSnapshot(liveTarget.getObjectId());
			final AcquisitionTargetSnapshot forbidden = new AcquisitionTargetSnapshot(base.objectId(), base.npcId(), base.instanceId(), base.distance(), false, false, true, true, false, true, true, false, true, false, 0, false, false, base.level(), true, true, false, false, 0, 0, base.onKillDelayMillis());
			PhantomAssertions.assertFalse(forbidden.manorLiveValidFor(actor, _manorCandidate.npcId(), 2000), "Raid manor target was admitted.");
		}
	}

	private void runManorServiceAttribution(Monster target) throws Exception
	{
		final int cropItemId = _manorCandidate.fact().cropItemId();
		final long existingCrop = _player.getInventory().getInventoryItemCount(cropItemId, -1);
		destroyInventoryCount(_player, cropItemId, existingCrop);
		target.setCurrentHp(1);
		target.getStatus().stopHpMpRegeneration();
		final long matureBefore = _player.getInventory().getInventoryItemCount(_manorCandidate.fact().matureItemId(), -1);
		final long reward1Before = _player.getInventory().getInventoryItemCount(_manorCandidate.fact().reward1ItemId(), -1);
		final long reward2Before = _player.getInventory().getInventoryItemCount(_manorCandidate.fact().reward2ItemId(), -1);

		final var planned = planManor(0, 100);
		final RankedSource selected = planned.ranked().stream().filter(value -> value.source().sourceId().equals(_manorCandidate.sourceId())).findFirst().orElseThrow(() -> new AssertionError("Planner lost the selected current manor source."));
		final ManorBinding plannedBinding = (ManorBinding) selected.methodBinding();
		final ManorBinding exact;
		try (ExternalActionLease lease = acquireAcquisition("manor-service-binding"))
		{
			final var inventory = lease.manorInventory(plannedBinding.seedItemId(), plannedBinding.cropItemId(), PhantomAcquisitionManorAuthority.HARVESTER_ITEM_ID);
			exact = new ManorBinding(plannedBinding.castleId(), plannedBinding.seedItemId(), plannedBinding.cropItemId(), plannedBinding.matureItemId(), plannedBinding.reward1ItemId(), plannedBinding.reward2ItemId(), plannedBinding.seedLevel(), plannedBinding.alternative(), plannedBinding.rawSeedLimit(), plannedBinding.rawCropLimit(), inventory.seedObjectId(), inventory.harvesterObjectId(), inventory.seedCount(), 0, plannedBinding.authorityHash());
		}
		final PhantomGoal goal = installAcquisition(selected, planned.ranked(), exact, 0, 100, Phase.COMBAT_PREPARED, target.getObjectId());
		PhantomAssertions.assertEquals(PhantomAcquisitionService.OperationStatus.SUCCESS, advanceAcquisition(goal, 1).status(), "Service manor Combat was not submitted.");
		PhantomAssertions.assertEquals(Phase.COMBAT_SUBMITTED, acquisitionState().phase(), "Service manor did not persist COMBAT_SUBMITTED.");
		final String owner = "acquisition:" + canonicalDigest(acquisitionState().goalId(), acquisitionState().goalRevision(), acquisitionState().selectedSource().sourceId(), target.getObjectId()).substring(0, 48);
		PhantomAssertions.assertTrue(_combat.matchesAcquisitionSession(_profile.profileId(), target.getObjectId(), owner), "Service manor Combat lost exact acquisition ownership.");
		ensureInventoryItem(cropItemId, 5);
		awaitCombatOutcome(target, "Service manor existing Combat");
		awaitTerminal();
		PhantomAssertions.assertEquals(PhantomAcquisitionService.OperationStatus.SUCCESS, advanceAcquisition(goal, 2).status(), "Service manor terminal Combat was not consumed.");
		PhantomAssertions.assertEquals(Phase.COMBAT_TERMINAL, acquisitionState().phase(), "Service manor terminal phase was not durable.");
		PhantomAssertions.assertEquals(PhantomAcquisitionService.OperationStatus.SUCCESS, advanceAcquisition(goal, 3).status(), "External crop observation did not prepare harvest.");
		final PhantomAcquisitionState prepared = acquisitionState();
		PhantomAssertions.assertEquals(Phase.HARVEST_PREPARED, prepared.phase(), "External crop observation did not retain the exact corpse.");
		PhantomAssertions.assertEquals(5L, prepared.lastObservedCount(), "External crop observation did not update overall truth.");
		PhantomAssertions.assertEquals(5L, prepared.progress(), "External crop observation did not update baseline-derived progress.");
		PhantomAssertions.assertEquals(ReceiptKind.VERIFY, prepared.receipts().getLast().kind(), "External crop delta was misattributed to Harvester.");
		PhantomAssertions.assertEquals(0L, prepared.receipts().getLast().beforeCount(), "External crop receipt before-count drifted.");
		PhantomAssertions.assertEquals(5L, prepared.receipts().getLast().afterCount(), "External crop receipt after-count drifted.");
		PhantomAssertions.assertEquals(5L, ((ManorBinding) prepared.methodBinding()).cropCountBeforeDispatch(), "Harvester baseline did not rebase to the observed crop count.");
		restartAcquisitionService();
		PhantomAssertions.assertEquals(prepared, acquisitionState(), "Prepared manor restart changed overall or handler-bound truth.");
		_player.getItemReuseTimeStamps().clear();

		long cropAfter = 5;
		for (int attempt = 0; (attempt < 3) && (cropAfter == 5); attempt++)
		{
			advanceAcquisition(goal, 4 + (attempt * 2L));
			waitFor(() -> _player.getInventory().getInventoryItemCount(cropItemId, -1) > 5, 4000);
			advanceAcquisition(goal, 5 + (attempt * 2L));
			cropAfter = _player.getInventory().getInventoryItemCount(cropItemId, -1);
		}
		cropAfter = _player.getInventory().getInventoryItemCount(cropItemId, -1);
		PhantomAssertions.assertTrue(cropAfter > 5, "Service Harvester produced no bounded crop delta.");
		final PhantomAcquisitionState harvested = acquisitionState();
		PhantomAssertions.assertEquals(cropAfter, harvested.lastObservedCount(), "Successful Harvester did not update overall truth.");
		final var harvestReceipt = harvested.receipts().stream().filter(receipt -> receipt.kind() == ReceiptKind.ACTIVE_MANOR_HARVEST).findFirst().orElseThrow(() -> new AssertionError("Successful Harvester receipt is absent."));
		PhantomAssertions.assertEquals(5L, harvestReceipt.beforeCount(), "Harvester receipt absorbed the external crop delta.");
		PhantomAssertions.assertEquals(cropAfter, harvestReceipt.afterCount(), "Harvester receipt lost its exact crop delta.");
		PhantomAssertions.assertEquals(TerminalResult.OBSERVED, harvestReceipt.result(), "Harvester receipt was not authoritative evidence.");
		PhantomAssertions.assertEquals(1L, harvested.receipts().stream().filter(receipt -> receipt.kind() == ReceiptKind.VERIFY).count(), "External and Harvester receipts were not kept separate.");
		PhantomAssertions.assertEquals(matureBefore, _player.getInventory().getInventoryItemCount(_manorCandidate.fact().matureItemId(), -1), "Manor acquisition credited mature seed.");
		PhantomAssertions.assertEquals(reward1Before, _player.getInventory().getInventoryItemCount(_manorCandidate.fact().reward1ItemId(), -1), "Manor acquisition performed reward exchange 1.");
		PhantomAssertions.assertEquals(reward2Before, _player.getInventory().getInventoryItemCount(_manorCandidate.fact().reward2ItemId(), -1), "Manor acquisition performed reward exchange 2.");
		PhantomAssertions.assertEquals(0, _acquisition.snapshot().currentClaims(), "Terminal manor service retained a state claim.");
		PhantomAssertions.assertEquals(0, _acquisition.snapshot().externalClaims(), "Terminal manor service retained an external claim.");
	}

	private void testCanonicalQuestChain() throws Exception
	{
		for (QuestSource source : _questSources)
		{
			selectQuestSource(source);
			relocateToCombatPoint();
			prepareQuestActor();
			final QuestState state = _player.getQuestState(_questRule.questName());
			PhantomAssertions.assertTrue((state != null) && state.isStarted() && (state.getCond() == _questRule.allowedConds().getFirst()), "Exact already-started quest setup is absent.");
			boolean granted = false;
			boolean notGranted = false;
			long count = _player.getInventory().getInventoryItemCount(_questRule.questItemId(), -1);
			for (int attempt = 0; (attempt < 32) && !(granted && notGranted) && (count < _questRule.itemCap()); attempt++)
			{
				resetActor(true);
				relocateToCombatPoint();
				_player.setInvul(true);
				ensureWeapon();
				final Monster target = spawnNormalMonster(1);
				target.setOnKillDelay(100);
				final long before = count;
				final var started = _combat.startSession(request(target, PhantomCombatMode.MELEE_PHYSICAL, false, false));
				PhantomAssertions.assertEquals(StartStatus.ACCEPTED, started.status(), "Existing Combat did not accept curated quest target " + source.npcId() + ".");
				awaitCombatOutcome(target, "Curated quest existing Combat " + source.npcId());
				PhantomAssertions.assertTrue(target.isDead() || target.isAlikeDead(), "Existing Combat did not kill curated quest target " + source.npcId() + ".");
				awaitTerminal();
				consumeTerminal();
				Thread.sleep(300);
				count = _player.getInventory().getInventoryItemCount(_questRule.questItemId(), -1);
				granted |= count == (before + 1);
				notGranted |= count == before;
				PhantomAssertions.assertTrue((count == before) || (count == (before + 1)), "Curated delayed callback produced an invalid item delta for " + source.npcId() + ".");
				PhantomAssertions.assertTrue(state.isStarted() && (state.getCond() == _questRule.allowedConds().getFirst()), "Kill collection changed quest state/cond below the audited cap.");
			}
			PhantomAssertions.assertTrue(granted && notGranted, "Bounded real delayed quest kills did not observe both grant and no-grant paths for " + source.npcId() + ".");
			PhantomAssertions.assertTrue(count <= _questRule.itemCap(), "Active quest collection crossed its conservative item cap.");
		}
	}

	private void testQuestControls()
	{
		PhantomAssertions.assertTrue(_questCatalog.current(), "Curated quest authority is not current after exact script loading.");
		boolean mappedTerritoryNegativeObserved = false;
		for (QuestSource source : _questSources)
		{
			selectQuestSource(source);
			relocateToCombatPoint();
			prepareQuestActor();
			PhantomAssertions.assertFalse(_questRule.supports(_questRule.allowedConds().getFirst() + 1, 0, source.npcId(), false), "Wrong quest cond was admitted.");
			PhantomAssertions.assertFalse(_questRule.supports(_questRule.allowedConds().getFirst(), _questRule.itemCap(), source.npcId(), false), "Quest item cap was admitted.");
			PhantomAssertions.assertFalse(_questRule.supports(_questRule.allowedConds().getFirst(), 0, source.npcId() + 1, false), "Wrong quest target was admitted.");
			try (ExternalActionLease lease = acquireAcquisition("quest-controls-" + source.npcId()))
			{
				final var snapshot = lease.questState(_questRule.questName(), _questRule.expectedVars());
				PhantomAssertions.assertTrue((snapshot != null) && "STARTED".equals(snapshot.state()) && (snapshot.cond() == _questRule.allowedConds().getFirst()), "Exact active quest state was not read through the acquisition seam.");
				final Source selectedSource = questAcquisitionSource(source);
				final Monster owned = spawnNormalMonster(targetMaximumHp());
				final AcquisitionTargetSnapshot target = lease.acquisitionTargetSnapshot(owned.getObjectId());
				PhantomAssertions.assertTrue((target != null) && target.spawnTerritoryPresent() && _sourceTerritory.geometrySnapshot().orElseThrow().hash().equals(target.territoryGeometryHash()), "Quest target did not retain exact mapped territory ownership.");
				PhantomAssertions.assertTrue(PhantomAcquisitionService.ownsMappedTarget(_query, selectedSource, target), "Selected factual territory target was rejected.");

				final SpawnFact otherMapped = _query.snapshot().spawnFactsByNpc().get(source.npcId()).stream().filter(fact -> (fact.territoryGeometry() != null) && (fact.topologyNodeId() != null) && !fact.topologyNodeId().equals(source.combatPoint().topologyNodeId())).findFirst().orElse(null);
				if (otherMapped != null)
				{
					final Monster mappedOther = spawnWithTerritory(source.npcId(), runtimeTerritory(source.npcId(), otherMapped), targetMaximumHp());
					PhantomAssertions.assertFalse(PhantomAcquisitionService.ownsMappedTarget(_query, selectedSource, lease.acquisitionTargetSnapshot(mappedOther.getObjectId())), "Same NPC from another mapped territory was admitted.");
					mappedTerritoryNegativeObserved = true;
				}

				final SpawnFact unmapped = _query.snapshot().spawnFactsByNpc().get(source.npcId()).stream().filter(fact -> (fact.territoryGeometry() != null) && (fact.topologyNodeId() == null)).findFirst().orElseThrow(() -> new AssertionError("Quest target lacks an infeasible negative-control territory."));
				final Monster unmappedTarget = spawnWithTerritory(source.npcId(), runtimeTerritory(source.npcId(), unmapped), targetMaximumHp());
				PhantomAssertions.assertFalse(PhantomAcquisitionService.ownsMappedTarget(_query, selectedSource, lease.acquisitionTargetSnapshot(unmappedTarget.getObjectId())), "Same NPC from an unmapped territory was admitted.");

				final Monster exactPoint = spawnExactPointMonster(source.npcId(), targetMaximumHp());
				final AcquisitionTargetSnapshot exactPointSnapshot = lease.acquisitionTargetSnapshot(exactPoint.getObjectId());
				PhantomAssertions.assertTrue((exactPointSnapshot != null) && exactPointSnapshot.exactPointSpawn(), "Exact-point negative-control Spawn was not observed.");
				PhantomAssertions.assertFalse(PhantomAcquisitionService.ownsMappedTarget(_query, selectedSource, exactPointSnapshot), "Same NPC exact-point Spawn inherited polygon source ownership.");
			}
		}
		PhantomAssertions.assertTrue(mappedTerritoryNegativeObserved, "No same-NPC alternate mapped territory negative control was exercised.");
	}

	private void testQuestServiceLifecycle() throws Exception
	{
		testQuestForeignSession();
		boolean observedGrant = false;
		boolean observedNoGrant = false;
		final Set<Integer> coveredTargets = new java.util.HashSet<>();
		for (int attempt = 0; (attempt < 24) && ((coveredTargets.size() < 3) || !observedGrant || !observedNoGrant); attempt++)
		{
			final QuestSource source = _questSources.get(attempt % _questSources.size());
			final boolean granted = runQuestServiceAttempt(source, attempt < _questSources.size());
			coveredTargets.add(source.npcId());
			observedGrant |= granted;
			observedNoGrant |= !granted;
		}
		PhantomAssertions.assertEquals(Set.of(20013, 20019, 20016), coveredTargets, "Full service quest coverage lost Q102/Q152 targets.");
		PhantomAssertions.assertTrue(observedGrant, "Full service delayed callbacks produced no real quest item.");
		PhantomAssertions.assertTrue(observedNoGrant, "Full service delayed callbacks produced no bounded no-grant path.");
		PhantomAssertions.assertEquals(0, _acquisition.snapshot().currentClaims(), "Terminal quest service retained a state claim.");
		PhantomAssertions.assertEquals(0, _acquisition.snapshot().externalClaims(), "Terminal quest service retained an external claim.");
		PhantomAssertions.assertEquals(0, _acquisition.snapshot().navigationClaims(), "Terminal quest service retained navigation ownership.");
	}

	private boolean runQuestServiceAttempt(QuestSource source, boolean restartEveryPhase) throws Exception
	{
		clearWorldFixtures();
		selectQuestSource(source);
		relocateToCombatPoint();
		prepareQuestActor();
		final Rule rule = source.rule();
		final long existing = _player.getInventory().getInventoryItemCount(rule.questItemId(), -1);
		destroyInventoryCount(_player, rule.questItemId(), existing);
		final QuestState questState = _player.getQuestState(rule.questName());
		final int originalCond = questState.getCond();
		final byte originalState = questState.getState();
		_epochMillis.addAndGet(_acquisitionCatalog.limits().questCallbackWaitMillis() + 10_000L);

		final var planned = planQuest(source, 0);
		final RankedSource selected = exactRanked(planned, source.npcId(), source.combatPoint().topologyNodeId());
		selectQuestSource(plannerQuestSource(rule, selected));
		relocateToCombatPoint();
		prepareQuestActor();
		final PhantomGoal goal = installAcquisition(selected, planned.ranked(), selected.methodBinding(), 0, 1, Phase.TARGET_REQUIRED, 0);
		PhantomAssertions.assertEquals(0L, ((QuestBinding) acquisitionState().methodBinding()).callbackDeadlineMillis(), "Quest deadline existed before callback preparation.");
		final Monster fixtureTarget = spawnNormalMonster(1);
		fixtureTarget.setOnKillDelay(100);
		PhantomAssertions.assertEquals(PhantomAcquisitionService.OperationStatus.SUCCESS, advanceAcquisition(goal, 100 + _acquisitionRevision).status(), "Full service quest target selection failed.");
		PhantomAssertions.assertEquals(Phase.QUEST_COMBAT_PREPARED, acquisitionState().phase(), "Full service quest did not persist QUEST_COMBAT_PREPARED.");
		final var selectedObject = World.getInstance().findObject(acquisitionState().targetObjectId());
		PhantomAssertions.assertTrue(selectedObject instanceof Monster, "Planner-owned exact quest target is not a real Monster.");
		final Monster target = (Monster) selectedObject;
		// Loaded territories can already contain an older object id than the controlled fixture. Keep the selected
		// production target local so this test exercises Combat and the delayed quest callback, not navigation variance.
		target.setXYZ(_player.getX() + 20, _player.getY(), _player.getZ());
		target.setCurrentHp(1);
		target.getStatus().stopHpMpRegeneration();
		target.setOnKillDelay(100);
		PhantomAssertions.assertEquals(PhantomAcquisitionService.OperationStatus.SUCCESS, advanceAcquisition(goal, 200 + _acquisitionRevision).status(), "Full service quest Combat submission failed.");
		PhantomAssertions.assertEquals(Phase.QUEST_COMBAT_SUBMITTED, acquisitionState().phase(), "Full service quest did not persist QUEST_COMBAT_SUBMITTED.");
		final String owner = "acquisition:" + canonicalDigest(acquisitionState().goalId(), acquisitionState().goalRevision(), acquisitionState().selectedSource().sourceId(), target.getObjectId()).substring(0, 48);
		PhantomAssertions.assertTrue(_combat.matchesAcquisitionSession(_profile.profileId(), target.getObjectId(), owner), "Full service quest session lost exact acquisition ownership.");
		if (restartEveryPhase)
		{
			restartAcquisitionService();
			PhantomAssertions.assertEquals(Phase.QUEST_COMBAT_SUBMITTED, acquisitionState().phase(), "Submitted quest restart changed its durable phase.");
			PhantomAssertions.assertTrue(_combat.matchesAcquisitionSession(_profile.profileId(), target.getObjectId(), owner), "Submitted quest restart lost its Combat owner.");
		}
		awaitCombatOutcome(target, "Full service curated quest Combat " + source.npcId());
		final PhantomCombatSessionSnapshot terminal = awaitTerminal();
		PhantomAssertions.assertEquals(PhantomCombatResult.VICTORY, terminal.result(), "Full service quest Combat did not terminate with a real kill.");
		final PhantomAcquisitionService.OperationResult terminalResult = advanceAcquisition(goal, 300 + _acquisitionRevision);
		PhantomAssertions.assertEquals(PhantomAcquisitionService.OperationStatus.SUCCESS, terminalResult.status(), "Full service quest terminal was not consumed: " + terminalResult.reasonKey());
		PhantomAssertions.assertEquals(Phase.QUEST_COMBAT_TERMINAL, acquisitionState().phase(), "Full service quest did not persist QUEST_COMBAT_TERMINAL.");
		if (restartEveryPhase)
		{
			restartAcquisitionService();
			PhantomAssertions.assertEquals(Phase.QUEST_COMBAT_TERMINAL, acquisitionState().phase(), "Terminal quest restart changed its durable phase.");
		}
		Thread.sleep(300);
		final long callbackCount = _player.getInventory().getInventoryItemCount(rule.questItemId(), -1);
		PhantomAssertions.assertTrue((callbackCount == 0) || (callbackCount == 1), "Real delayed quest callback produced an invalid delta.");
		PhantomAssertions.assertEquals(PhantomAcquisitionService.OperationStatus.SUCCESS, advanceAcquisition(goal, 400 + _acquisitionRevision).status(), "Full service quest callback wait was not prepared.");
		final PhantomAcquisitionState waiting = acquisitionState();
		PhantomAssertions.assertEquals(Phase.QUEST_CALLBACK_WAIT, waiting.phase(), "Full service quest did not persist QUEST_CALLBACK_WAIT.");
		final QuestBinding binding = (QuestBinding) waiting.methodBinding();
		final long deadline = binding.callbackDeadlineMillis();
		PhantomAssertions.assertTrue(deadline > _epochMillis.get(), "Quest callback deadline is not an absolute future epoch millisecond.");
		PhantomAssertions.assertEquals(waiting, new org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionStateCodec().decode(new org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionStateCodec().encode(waiting)), "Quest callback deadline did not survive schema-3 restart encoding.");
		if (restartEveryPhase)
		{
			restartAcquisitionService();
			PhantomAssertions.assertEquals(waiting, acquisitionState(), "Callback-wait restart reset its deadline or attempt.");
		}
		if (callbackCount == 1)
		{
			_epochMillis.set(deadline + 1_000_000L);
			PhantomAssertions.assertEquals(PhantomAcquisitionService.OperationStatus.SUCCESS, advanceAcquisition(goal, 500 + _acquisitionRevision).status(), "Observed quest item was not checked before an expired deadline.");
			PhantomAssertions.assertEquals(Phase.VERIFYING, acquisitionState().phase(), "Observed quest item did not advance to verification.");
			PhantomAssertions.assertEquals(PhantomAcquisitionService.OperationStatus.COMPLETE_GOAL, advanceAcquisition(goal, 600 + _acquisitionRevision).status(), "Real quest item did not complete the acquisition Goal.");
			PhantomAssertions.assertEquals(Status.COMPLETED, acquisitionState().status(), "Real quest item did not persist completed acquisition truth.");
			PhantomAssertions.assertTrue(acquisitionState().receipts().stream().anyMatch(receipt -> (receipt.kind() == ReceiptKind.ACTIVE_QUEST_COLLECTION) && (receipt.beforeCount() == 0) && (receipt.afterCount() == 1)), "Real delayed callback lacks an active quest receipt.");
		}
		else
		{
			PhantomAssertions.assertEquals(PhantomAcquisitionService.OperationStatus.RETRY, advanceAcquisition(goal, 500 + _acquisitionRevision).status(), "No-grant callback did not remain pending before its deadline.");
			PhantomAssertions.assertEquals(0, acquisitionState().phaseAttempt(), "Restart/pending observation reset or consumed a callback attempt.");
			_epochMillis.set(deadline - _acquisitionCatalog.limits().questCallbackWaitMillis() - 1);
			PhantomAssertions.assertEquals(PhantomAcquisitionService.OperationStatus.RETRY, advanceAcquisition(goal, 600 + _acquisitionRevision).status(), "Clock rollback beyond the wait window did not expire conservatively.");
			PhantomAssertions.assertEquals(1, acquisitionState().phaseAttempt(), "Clock rollback did not consume one bounded timeout attempt.");
			_epochMillis.set(deadline);
			PhantomAssertions.assertEquals(PhantomAcquisitionService.OperationStatus.RETRY, advanceAcquisition(goal, 700 + _acquisitionRevision).status(), "Exact callback deadline did not consume a bounded attempt.");
			PhantomAssertions.assertEquals(2, acquisitionState().phaseAttempt(), "Exact callback deadline attempt was not durable.");
			final var stored = _acquisitionStore.load(_profile.profileId()).orElseThrow();
			final QuestBinding legacy = new QuestBinding(binding.ruleId(), binding.ruleHash(), binding.questId(), binding.questName(), binding.scriptHash(), binding.expectedState(), binding.expectedCond(), binding.questItemId(), binding.itemCap(), binding.targetNpcId(), binding.itemCountBeforeKill(), 1, binding.authorityHash());
			_acquisitionStore.replace(_profile.profileId(), stored.rowVersion(), stored.state().withBinding(legacy, Phase.QUEST_CALLBACK_WAIT, stored.state().targetObjectId(), stored.state().targetNpcId(), stored.state().targetInstanceId(), stored.state().phaseAttempt(), stored.state().logicalMinute() + 1));
			_epochMillis.set(deadline + 10_000_000L);
			PhantomAssertions.assertEquals(PhantomAcquisitionService.OperationStatus.REPLAN, advanceAcquisition(goal, 800 + _acquisitionRevision).status(), "Legacy/small deadline did not fail the source after the bounded limit.");
			PhantomAssertions.assertFalse(acquisitionState().phase() == Phase.QUEST_CALLBACK_WAIT, "Legacy/small deadline retained callback ownership.");
			PhantomAssertions.assertTrue(acquisitionState().receipts().stream().noneMatch(receipt -> receipt.kind() == ReceiptKind.ACTIVE_QUEST_COLLECTION), "No-grant timeout fabricated a quest receipt.");
		}
		PhantomAssertions.assertEquals(originalState, questState.getState(), "Acquisition service changed the already-started quest state.");
		PhantomAssertions.assertEquals(originalCond, questState.getCond(), "Acquisition service changed the already-started quest cond.");
		PhantomAssertions.assertTrue(_combat.find(_profile.profileId()).isEmpty(), "Terminal quest attempt retained Combat ownership.");
		return callbackCount == 1;
	}

	private void testQuestForeignSession()
	{
		clearWorldFixtures();
		final QuestSource source = _questSources.getFirst();
		selectQuestSource(source);
		relocateToCombatPoint();
		prepareQuestActor();
		final long existing = _player.getInventory().getInventoryItemCount(source.rule().questItemId(), -1);
		destroyInventoryCount(_player, source.rule().questItemId(), existing);
		final var planned = planQuest(source, 0);
		final RankedSource selected = exactRanked(planned, source.npcId(), source.combatPoint().topologyNodeId());
		selectQuestSource(plannerQuestSource(source.rule(), selected));
		relocateToCombatPoint();
		prepareQuestActor();
		final Monster target = spawnNormalMonster(targetMaximumHp());
		final PhantomGoal goal = installAcquisition(selected, planned.ranked(), selected.methodBinding(), 0, 1, Phase.QUEST_COMBAT_PREPARED, target.getObjectId());
		PhantomAssertions.assertTrue(_combat.startSession(request(target, PhantomCombatMode.MELEE_PHYSICAL, false, false)).accepted(), "Foreign quest Combat fixture was not established.");
		final var result = advanceAcquisition(goal, 50);
		PhantomAssertions.assertEquals(PhantomAcquisitionService.OperationStatus.REPLAN, result.status(), "Full service quest inherited a foreign Combat session.");
		PhantomAssertions.assertEquals("acquisition.combat.foreign_session", result.reasonKey(), "Foreign quest Combat lost its typed rejection.");
		PhantomAssertions.assertEquals(Phase.QUEST_COMBAT_PREPARED, acquisitionState().phase(), "Foreign quest Combat advanced acquisition state.");
		_combat.cancel(_profile.profileId());
		consumeTerminal();
	}

	private void clearWorldFixtures()
	{
		for (Monster fixture : List.copyOf(_worldFixtures))
		{
			if (fixture.isSpawned())
			{
				fixture.deleteMe();
			}
		}
		_worldFixtures.clear();
	}

	private Source questAcquisitionSource(QuestSource source)
	{
		final var anchor = _topology.snapshot().anchorsByNode().get(source.combatPoint().topologyNodeId()).getFirst();
		return new Source("a".repeat(64), Method.QUEST_COLLECTION, source.npcId(), source.rule().questItemId(), "quest:" + source.rule().id(), source.combatPoint().topologyNodeId(), anchor.id(), source.combatPoint().instanceId(), 0, 0, 0, 0);
	}

	private NpcSpawnTerritory runtimeTerritory(int npcId, SpawnFact fact)
	{
		final var geometry = fact.territoryGeometry();
		return SpawnTable.getInstance().getSpawns(npcId).stream().map(Spawn::getSpawnTerritory).filter(java.util.Objects::nonNull).filter(territory -> territory.geometrySnapshot().map(snapshot -> snapshot.hash().equals(geometry.geometryHash()) && snapshot.sourcePath().equals(geometry.sourcePath()) && snapshot.territoryName().equals(geometry.territoryName())).orElse(false)).findFirst().orElseThrow(() -> new AssertionError("Runtime negative-control territory differs from Game Knowledge."));
	}

	private record QuestSource(Rule rule, int npcId, SpawnFact combatPoint, NpcSpawnTerritory territory)
	{
	}

	private void prepareAcquisitionActor()
	{
		_player.setPlayerClass(SPOIL_CLASS_ID);
		_player.getStat().setLevel((byte) 85);
		_player.setInvul(true);
		ensureWeapon();
		for (int skillId : List.of(SPOIL_SKILL_ID, SWEEP_SKILL_ID))
		{
			final Skill skill = SkillData.getInstance().getSkill(skillId, skillId == SPOIL_SKILL_ID ? 11 : 1);
			PhantomAssertions.assertTrue(skill != null, "Canonical acquisition skill is unavailable: " + skillId);
			_player.addSkill(skill, false);
			_player.enableSkill(skill);
		}
		_player.setCurrentHp(_player.getMaxHp());
		_player.setCurrentMp(_player.getMaxMp());
		_player.setCurrentCp(_player.getMaxCp());
	}

	private void resetAcquisitionActor()
	{
		resetActor(true);
		prepareAcquisitionActor();
		relocateToCombatPoint();
	}

	private DeterministicSpoilMonster spawnDeterministicSpoilMonster()
	{
		final NpcTemplate template = NpcData.getInstance().getTemplate(_combatPoint.npcId());
		PhantomAssertions.assertTrue(template != null, "Normal-monster template is unavailable.");
		// NpcStat#getLevel reads the immutable template level, so a stat override does not remove the unseeded magic-resist roll.
		// Keep the canonical NPC/drop template while bounding only the cast fixture's effective level.
		final DeterministicSpoilMonster target = spawn(new DeterministicSpoilMonster(template), 20 + (_worldFixtures.size() * 5));
		target.setCurrentHp(Math.min(targetMaximumHp(), target.getMaxHp()));
		return target;
	}

	private ExternalActionLease acquireAcquisition(String operation)
	{
		final var result = _combat.acquireExternalAction(new ExternalActionRequest(_profile.profileId(), ExternalActionKind.ACQUISITION, operation, System.nanoTime() + TimeUnit.SECONDS.toNanos(10), () -> false));
		PhantomAssertions.assertEquals(ExternalActionStatus.ACQUIRED, result.status(), "Acquisition action lease was not acquired.");
		return result.lease();
	}

	private void testAcquisitionControls()
	{
		resetAcquisitionActor();
		final Monster target = spawnNormalMonster(targetMaximumHp());
		try (ExternalActionLease lease = acquireAcquisition("acquisition-controls"))
		{
			final long inventoryBefore = _player.getInventory().getInventoryItemCount(57, -1);
			final Map<Integer, Long> inventoryCounts = lease.acquisitionInventoryCounts(List.of(57, 99999));
			PhantomAssertions.assertEquals(inventoryBefore, inventoryCounts.get(57), "Exact active inventory count differed from canonical Player inventory.");
			PhantomAssertions.assertEquals(0L, inventoryCounts.get(99999), "Absent exact active inventory item was not zero.");
			PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> lease.acquisitionInventoryCounts(java.util.stream.IntStream.rangeClosed(1, 129).boxed().toList()), "129 active inventory IDs were admitted by the production backend.");
			PhantomAssertions.assertThrows(UnsupportedOperationException.class, () -> inventoryCounts.put(57, 1L), "Production active inventory snapshot was mutable.");
			final List<AcquisitionTargetSnapshot> targets = lease.acquisitionTargets(_acquisitionSource.npcId(), 8, 2000);
			PhantomAssertions.assertTrue(targets.stream().anyMatch(value -> value.objectId() == target.getObjectId()), "Exact authoritative acquisition target was not observed.");
			PhantomAssertions.assertTrue(lease.acquisitionTargets(_acquisitionSource.npcId() + 1, 8, 2000).stream().noneMatch(value -> value.objectId() == target.getObjectId()), "Wrong NPC identity was admitted.");
			PhantomAssertions.assertEquals(_acquisitionSource.spoilSkillLevel(), lease.knownSkillLevel(SPOIL_SKILL_ID), "Canonical known spoil skill was not observed.");
			PhantomAssertions.assertEquals(_acquisitionSource.sweepSkillLevel(), lease.knownSkillLevel(SWEEP_SKILL_ID), "Canonical known sweep skill was not observed.");
			PhantomAssertions.assertEquals(ActionOutcome.REJECTED, lease.castAcquisition(target.getObjectId(), new SelectedSkill(SPOIL_SKILL_ID + 1, 1), AcquisitionSkillKind.SPOIL), "Unknown acquisition skill was admitted.");
			PhantomAssertions.assertEquals(ActionOutcome.REJECTED, lease.castAcquisition(target.getObjectId() + 1, new SelectedSkill(SPOIL_SKILL_ID, 11), AcquisitionSkillKind.SPOIL), "Wrong exact target was admitted.");
			PhantomAssertions.assertEquals(StartStatus.REJECTED_EXISTING, _combat.startSession(request(target, PhantomCombatMode.MELEE_PHYSICAL, false, false)).status(), "Existing Combat admitted a second owner during acquisition.");
			PhantomAssertions.assertEquals(inventoryBefore, _player.getInventory().getInventoryItemCount(57, -1), "Exact active inventory read mutated canonical inventory.");
		}

		final Monster wrongInstance = spawnNormalMonster(targetMaximumHp());
		final int wrongInstanceId = InstanceManager.getInstance().createDynamicInstance(0).getId();
		wrongInstance.setInstanceId(wrongInstanceId);
		final Monster distant = spawnAtDistance(targetMaximumHp(), 2101);
		try (ExternalActionLease lease = acquireAcquisition("acquisition-negative-targets"))
		{
			final AcquisitionTargetSnapshot instanceSnapshot = lease.acquisitionTargetSnapshot(wrongInstance.getObjectId());
			final AcquisitionTargetSnapshot distanceSnapshot = lease.acquisitionTargetSnapshot(distant.getObjectId());
			PhantomAssertions.assertTrue((instanceSnapshot != null) && !instanceSnapshot.liveValidFor(lease.actorSnapshot(), _acquisitionSource.npcId(), 2000), "Wrong instance was admitted.");
			PhantomAssertions.assertTrue((distanceSnapshot != null) && !distanceSnapshot.liveValidFor(lease.actorSnapshot(), _acquisitionSource.npcId(), 2000), "Out-of-range target was admitted.");
		}
		finally
		{
			wrongInstance.setInstanceId(0);
			InstanceManager.getInstance().destroyInstance(wrongInstanceId);
		}
	}

	private void testCanonicalAcquisitionChain() throws Exception
	{
		resetAcquisitionActor();
		final long before = _player.getInventory().getInventoryItemCount(_acquisitionSource.itemId(), -1);
		final DeterministicSpoilMonster target = spawnDeterministicSpoilMonster();
		try
		{
			try (ExternalActionLease lease = acquireAcquisition("acquisition-chain-spoil"))
			{
				final ActionOutcome spoil = lease.castAcquisition(target.getObjectId(), new SelectedSkill(_acquisitionSource.spoilSkillId(), _acquisitionSource.spoilSkillLevel()), AcquisitionSkillKind.SPOIL);
				PhantomAssertions.assertTrue((spoil == ActionOutcome.ISSUED) || (spoil == ActionOutcome.ALREADY_OWNED), "Canonical spoil cast was not issued.");
				await(() -> target.isSpoiled() && (target.getSpoilerObjectId() == _player.getObjectId()), "Canonical spoil was not observed on the exact Monster.");
			}
			await(() -> !_player.isCastingNow() && !_player.isAttackingNow(), "Acquisition lease did not release the canonical spoil action.");
			target.restoreTemplateLevel();
			target.setCurrentHp(1);
			target.getStatus().stopHpMpRegeneration();
			_player.setPlayerClass(MELEE_CLASS_ID);

			final PhantomCombatService.StartResult started = _combat.startSession(request(target, PhantomCombatMode.MELEE_PHYSICAL, false, false));
			PhantomAssertions.assertEquals(StartStatus.ACCEPTED, started.status(), "Existing Combat did not accept the spoiled exact target.");
			awaitCombatOutcome(target, "Existing Combat");
			PhantomAssertions.assertTrue(target.isDead() || target.isAlikeDead(), "Existing Combat did not kill the spoiled exact target: " + _combat.find(_profile.profileId()).orElse(null));
			PhantomAssertions.assertEquals(PhantomCombatResult.VICTORY, awaitTerminal().result(), "Existing Combat did not publish canonical victory.");
			consumeTerminal();
			PhantomAssertions.assertTrue(target.isSweepActive(), "Canonical spoiled corpse did not expose sweep state.");
			prepareAcquisitionActor();

			try (ExternalActionLease lease = acquireAcquisition("acquisition-chain-sweep"))
			{
				final AcquisitionTargetSnapshot corpse = lease.acquisitionTargetSnapshot(target.getObjectId());
				PhantomAssertions.assertTrue((corpse != null) && corpse.sweepValidFor(lease.actorSnapshot(), _acquisitionSource.npcId(), 2000), "Exact spoiled corpse was not sweep-eligible.");
				final ActionOutcome sweep = lease.castAcquisition(target.getObjectId(), new SelectedSkill(_acquisitionSource.sweepSkillId(), _acquisitionSource.sweepSkillLevel()), AcquisitionSkillKind.SWEEP);
				PhantomAssertions.assertTrue((sweep == ActionOutcome.ISSUED) || (sweep == ActionOutcome.ALREADY_OWNED), "Canonical sweep cast was not issued.");
				await(() -> !target.isSweepActive(), "Canonical sweep did not consume the exact corpse loot state.");
			}
			final long after = _player.getInventory().getInventoryItemCount(_acquisitionSource.itemId(), -1);
			PhantomAssertions.assertTrue(after > before, "Canonical spoil/sweep produced no authoritative target-item inventory delta.");
		}
		finally
		{
			final long after = _player.getInventory().getInventoryItemCount(_acquisitionSource.itemId(), -1);
			destroyInventoryCount(_player, _acquisitionSource.itemId(), Math.max(0, after - before));
		}
	}

	private void testAcquisitionDispatchRecovery() throws Exception
	{
		resetAcquisitionActor();
		final DeterministicSpoilMonster target = spawnDeterministicSpoilMonster();
		try (ExternalActionLease lease = acquireAcquisition("acquisition-recovery-spoil-1"))
		{
			PhantomAssertions.assertEquals(ActionOutcome.ISSUED, lease.castAcquisition(target.getObjectId(), new SelectedSkill(SPOIL_SKILL_ID, 11), AcquisitionSkillKind.SPOIL), "Initial spoil dispatch was not issued.");
			await(() -> target.isSpoiled(), "Initial spoil dispatch did not become observable.");
		}
		try (ExternalActionLease recovered = acquireAcquisition("acquisition-recovery-spoil-2"))
		{
			PhantomAssertions.assertEquals(ActionOutcome.ALREADY_OWNED, recovered.castAcquisition(target.getObjectId(), new SelectedSkill(SPOIL_SKILL_ID, 11), AcquisitionSkillKind.SPOIL), "Observed spoil was blindly repeated after dispatch recovery.");
		}
		await(() -> !_player.isCastingNow() && !_player.isAttackingNow(), "Recovered acquisition lease did not release the canonical spoil action.");
		target.restoreTemplateLevel();
		target.setCurrentHp(1);
		target.getStatus().stopHpMpRegeneration();
		_player.setPlayerClass(MELEE_CLASS_ID);
		final PhantomCombatService.StartResult started = _combat.startSession(request(target, PhantomCombatMode.MELEE_PHYSICAL, false, false));
		PhantomAssertions.assertEquals(StartStatus.ACCEPTED, started.status(), "Recovery fixture Combat start failed.");
		awaitCombatOutcome(target, "Recovery fixture Combat");
		PhantomAssertions.assertTrue(target.isDead() || target.isAlikeDead(), "Recovery fixture target did not die: " + _combat.find(_profile.profileId()).orElse(null));
		awaitTerminal();
		consumeTerminal();
		prepareAcquisitionActor();
		try (ExternalActionLease lease = acquireAcquisition("acquisition-recovery-sweep-1"))
		{
			PhantomAssertions.assertEquals(ActionOutcome.ISSUED, lease.castAcquisition(target.getObjectId(), new SelectedSkill(SWEEP_SKILL_ID, 1), AcquisitionSkillKind.SWEEP), "Initial sweep dispatch was not issued.");
			await(() -> !target.isSweepActive(), "Initial sweep dispatch did not become observable.");
		}
		try (ExternalActionLease recovered = acquireAcquisition("acquisition-recovery-sweep-2"))
		{
			PhantomAssertions.assertEquals(ActionOutcome.REJECTED, recovered.castAcquisition(target.getObjectId(), new SelectedSkill(SWEEP_SKILL_ID, 1), AcquisitionSkillKind.SWEEP), "Consumed sweep was blindly repeated after dispatch recovery.");
		}
	}

	private void testExactActorLease()
	{
		try (ActionLease lease = _materialization.tryAcquireAction(_profile.profileId()).orElseThrow())
		{
			PhantomAssertions.assertEquals(_player, lease.player(), "Materialization lease did not retain the exact actor.");
			PhantomAssertions.assertEquals(_player, World.getInstance().getPlayer(_player.getObjectId()), "Materialization actor is not the exact World Player.");
		}
	}

	private void testCanonicalCpSnapshot()
	{
		resetActor(true);
		final double firstCp = Math.max(1, Math.floor(_player.getMaxCp() / 3));
		final double secondCp = Math.min(_player.getMaxCp(), firstCp + 7);
		final double hp = _player.getCurrentHp();
		final double mp = _player.getCurrentMp();
		_player.setCurrentCp(firstCp);
		final ActorSnapshot first;
		try (PhantomCombatActorLease lease = Optional.ofNullable(_backend.tryAcquireActor(_profile.profileId())).orElseThrow())
		{
			first = lease.actorSnapshot();
		}
		PhantomAssertions.assertTrue(Double.compare(first.currentCp(), _player.getCurrentCp()) == 0, "Combat snapshot current CP differs from canonical Player.");
		PhantomAssertions.assertTrue(Double.compare(first.maximumCp(), _player.getMaxCp()) == 0, "Combat snapshot maximum CP differs from canonical Player.");
		PhantomAssertions.assertTrue(Double.compare(first.currentHp(), hp) == 0 && Double.compare(first.currentMp(), mp) == 0, "HP, MP and CP were mixed in the combat snapshot.");
		PhantomAssertions.assertTrue(Double.compare(_player.getCurrentCp(), firstCp) == 0, "Combat snapshot mutated canonical Player CP.");
		_player.setCurrentCp(secondCp);
		final ActorSnapshot second;
		try (PhantomCombatActorLease lease = Optional.ofNullable(_backend.tryAcquireActor(_profile.profileId())).orElseThrow())
		{
			second = lease.actorSnapshot();
		}
		PhantomAssertions.assertTrue(Double.compare(second.currentCp(), secondCp) == 0, "Next combat snapshot did not observe changed canonical CP.");
		PhantomAssertions.assertTrue(Double.compare(first.currentCp(), firstCp) == 0, "Immutable combat snapshot changed after canonical CP mutation.");
	}

	private void testCanonicalAttack() throws Exception
	{
		resetActor(true);
		final Monster target = spawnNormalMonster(1);
		final PhantomCombatService.StartResult started = _combat.startSession(request(target, PhantomCombatMode.MELEE_PHYSICAL, false, false));
		PhantomAssertions.assertEquals(StartStatus.ACCEPTED, started.status(), "Normal monster combat was not accepted.");
		await(() -> target.isDead() || target.isAlikeDead(), "Canonical PlayerAI attack did not kill the deterministic target.");
		final PhantomCombatSessionSnapshot terminal = awaitTerminal();
		PhantomAssertions.assertEquals(PhantomCombatResult.VICTORY, terminal.result(), "Canonical target death did not produce victory.");
		PhantomAssertions.assertTrue(_player.getTarget() == null, "Victory cleanup retained the exact dead combat target.");
		consumeTerminal();
	}

	private void testCanonicalCast() throws Exception
	{
		resetActor(true);
		_player.setPlayerClass(MAGIC_CLASS_ID);
		_player.getStat().setLevel((byte) 85);
		final Skill skill = SkillData.getInstance().getSkill(MAGIC_SKILL_ID, 1);
		PhantomAssertions.assertTrue(skill != null, "Deterministic offensive skill is unavailable.");
		_player.addSkill(skill, false);
		_player.setCurrentMp(_player.getMaxMp());
		final Monster target = spawnNormalMonster(targetMaximumHp());
		final double initialHp = target.getCurrentHp();
		final double initialMp = _player.getCurrentMp();
		try (PhantomCombatActorLease lease = Optional.ofNullable(_backend.tryAcquireActor(_profile.profileId())).orElseThrow())
		{
			PhantomAssertions.assertTrue(lease.supportsSkill(new org.l2jmobius.gameserver.phantoms.combat.PhantomCombatLoadout.SelectedSkill(MAGIC_SKILL_ID, 1), PhantomCombatMode.RANGED_MAGIC), "Real hostile one-target skill was rejected.");
		}
		final PhantomCombatService.StartResult started = _combat.startSession(request(target, PhantomCombatMode.RANGED_MAGIC, false, false));
		PhantomAssertions.assertEquals(StartStatus.ACCEPTED, started.status(), "Supported magic loadout was not accepted.");
		await(() -> _player.isCastingNow() || (_player.getCurrentSkill() != null) || (target.getCurrentHp() < initialHp) || (_player.getCurrentMp() < initialMp), "Canonical CAST produced no observable cast state or effect.");
		PhantomAssertions.assertTrue((_player.getCurrentMp() <= initialMp) && (target.getCurrentHp() <= initialHp), "Canonical cast fabricated HP or MP.");
		_combat.cancel(_profile.profileId());
		consumeTerminal();
	}

	private void testCanonicalShot() throws Exception
	{
		resetActor(true);
		final Item weapon = ensureWeapon();
		final Item shots = _player.getInventory().addItem(ItemProcessType.REWARD, SOULSHOT_ITEM_ID, 5, _player, this);
		PhantomAssertions.assertTrue(shots != null, "Could not create test-owned soulshot fixture.");
		final long before = _player.getInventory().getInventoryItemCount(SOULSHOT_ITEM_ID, -1);
		final Monster target = spawnNormalMonster(targetMaximumHp());
		try (PhantomCombatActorLease lease = Optional.ofNullable(_backend.tryAcquireActor(_profile.profileId())).orElseThrow())
		{
			PhantomAssertions.assertEquals(ShotOutcome.ACTIVATED, lease.activateShot(PhantomCombatMode.MELEE_PHYSICAL), "Canonical soulshot activation failed.");
			PhantomAssertions.assertEquals(before - 1, _player.getInventory().getInventoryItemCount(SOULSHOT_ITEM_ID, -1), "Canonical soulshot activation consumed an unexpected count.");
			PhantomAssertions.assertTrue(_player.isChargedShot(ShotType.SOULSHOTS), "Canonical soulshot handler did not charge the weapon.");
			PhantomAssertions.assertEquals(ActionOutcome.ISSUED, lease.attack(target.getObjectId()), "Canonical shot-backed attack was not issued.");
			await(() -> !_player.isChargedShot(ShotType.SOULSHOTS), "Canonical attack did not discharge the soulshot.");
			lease.cancelOwnedAction(new PhantomOwnedAction(1, target.getObjectId(), null, 0));
		}
		destroyInventoryItem(shots);
		PhantomAssertions.assertTrue(weapon.isEquipped(), "Shot test unexpectedly unequipped its weapon.");
	}

	private void testMissingShot()
	{
		resetActor(true);
		ensureWeapon();
		final Item lingeringShots = _player.getInventory().getItemByItemId(SOULSHOT_ITEM_ID);
		if (lingeringShots != null)
		{
			destroyInventoryItem(lingeringShots);
		}
		final long before = _player.getInventory().getInventoryItemCount(SOULSHOT_ITEM_ID, -1);
		try (PhantomCombatActorLease lease = Optional.ofNullable(_backend.tryAcquireActor(_profile.profileId())).orElseThrow())
		{
			PhantomAssertions.assertEquals(ShotOutcome.UNAVAILABLE, lease.activateShot(PhantomCombatMode.MELEE_PHYSICAL), "Missing shot was not reported as unavailable.");
		}
		PhantomAssertions.assertEquals(before, _player.getInventory().getInventoryItemCount(SOULSHOT_ITEM_ID, -1), "Missing shot path fabricated inventory.");
		PhantomAssertions.assertFalse(_player.isChargedShot(ShotType.SOULSHOTS), "Missing shot path fabricated charge.");
		destroyInventoryItem(_player.getInventory().getItemByItemId(WEAPON_ITEM_ID));
	}

	private void testCanonicalLoot() throws Exception
	{
		resetActor(true);
		final Monster target = spawnNormalMonster(targetMaximumHp());
		final long before = _player.getInventory().getInventoryItemCount(ADENA_ITEM_ID, -1);
		final PhantomCombatService.StartResult started = _combat.startSession(request(target, PhantomCombatMode.MELEE_PHYSICAL, false, true));
		PhantomAssertions.assertEquals(StartStatus.ACCEPTED, started.status(), "Loot combat was not accepted.");
		PhantomAssertions.assertTrue(target.doDie(_player), "Could not kill the test-owned loot target.");
		final Item dropped = target.dropItem(_player, ADENA_ITEM_ID, 1);
		PhantomAssertions.assertTrue(dropped != null, "Canonical monster drop did not create a ground item.");
		try
		{
			await(() -> World.getInstance().findObject(dropped.getObjectId()) == null, "Canonical PlayerAI pickup did not remove the ground item.");
			final PhantomCombatSessionSnapshot terminal = awaitTerminal();
			PhantomAssertions.assertTrue((terminal.result() == PhantomCombatResult.VICTORY_LOOTED) || (terminal.result() == PhantomCombatResult.VICTORY_LOOT_PARTIAL), "Canonical loot produced unexpected result " + terminal.result() + ".");
			PhantomAssertions.assertEquals(before + 1, _player.getInventory().getInventoryItemCount(ADENA_ITEM_ID, -1), "Ground item and inventory conservation failed.");
		}
		finally
		{
			consumeTerminal();
			final long added = _player.getInventory().getInventoryItemCount(ADENA_ITEM_ID, -1) - before;
			if (added > 0)
			{
				final Item adena = _player.getInventory().getItemByItemId(ADENA_ITEM_ID);
				PhantomAssertions.assertTrue(_player.getInventory().destroyItem(ItemProcessType.DESTROY, adena, added, _player, this) != null, "Could not restore fixture adena baseline.");
			}
		}
	}

	private void testForbiddenTargets()
	{
		resetActor(true);
		_observer = Player.load(_environment.observer().objectId());
		PhantomAssertions.assertTrue(_observer != null, "Could not load the test-owned Player target.");
		_observer.setXYZInvisible(_player.getX() + 20, _player.getY(), _player.getZ());
		_observer.spawnMe();
		PhantomAssertions.assertEquals(StartStatus.REJECTED_TARGET, _combat.startSession(request(_observer.getObjectId(), PhantomCombatMode.MELEE_PHYSICAL)).status(), "Player target was accepted.");

		final NpcTemplate template = NpcData.getInstance().getTemplate(_combatPoint.npcId());
		final RaidBoss raid = spawn(new RaidBoss(template), 30);
		PhantomAssertions.assertEquals(StartStatus.REJECTED_TARGET, _combat.startSession(request(raid.getObjectId(), PhantomCombatMode.MELEE_PHYSICAL)).status(), "RaidBoss target was accepted.");
		final GrandBoss grand = spawn(new GrandBoss(template), 40);
		PhantomAssertions.assertEquals(StartStatus.REJECTED_TARGET, _combat.startSession(request(grand.getObjectId(), PhantomCombatMode.MELEE_PHYSICAL)).status(), "GrandBoss target was accepted.");

		final Monster observed = spawnNormalMonster(targetMaximumHp());
		observed.setTarget(_player);
		_player.getAttackByList().clear();
		_player.getAttackByList().add(observed);
		try (PhantomCombatActorLease lease = Optional.ofNullable(_backend.tryAcquireActor(_profile.profileId())).orElseThrow())
		{
			PhantomAssertions.assertTrue(lease.observedAttackers(16).stream().anyMatch(entry -> entry.targetObjectId() == observed.getObjectId()), "Valid normal-monster threat was not observed.");
			observed.setInvul(true);
			PhantomAssertions.assertTrue(lease.observedAttackers(16).isEmpty(), "Forbidden invulnerable threat entered the table.");
		}
		finally
		{
			observed.setInvul(false);
			_player.getAttackByList().clear();
		}
	}

	private void testOwnedCancellation() throws Exception
	{
		resetActor(true);
		final Monster owned = spawnNormalMonster(targetMaximumHp());
		final Monster foreign = spawnNormalMonster(targetMaximumHp());
		foreign.setXYZ(_player.getX() + 60, _player.getY(), _player.getZ());
		PhantomAssertions.assertEquals(StartStatus.ACCEPTED, _combat.startSession(request(owned, PhantomCombatMode.MELEE_PHYSICAL, false, false)).status(), "Owned-action combat did not start.");
		await(() -> _player.hasAI() && (_player.getAI().getIntention() == Intention.ATTACK) && (_player.getAI().getAttackTarget() == owned), "Combat did not establish the owned attack.");
		_player.setTarget(foreign);
		_player.getAI().setIntention(Intention.ATTACK, foreign);
		PhantomAssertions.assertEquals(CancelStatus.CANCELLED_CLEAN, _combat.cancel(_profile.profileId()), "Combat cancellation was not accepted.");
		PhantomAssertions.assertEquals(Intention.ATTACK, _player.getAI().getIntention(), "Combat cancellation stopped a foreign action.");
		PhantomAssertions.assertEquals(foreign, _player.getAI().getAttackTarget(), "Combat cancellation replaced a foreign target.");
		consumeTerminal();
	}

	private void testPlayerDeath() throws Exception
	{
		resetActor(true);
		final Monster target = spawnNormalMonster(targetMaximumHp());
		PhantomAssertions.assertEquals(StartStatus.ACCEPTED, _combat.startSession(request(target, PhantomCombatMode.MELEE_PHYSICAL, false, false)).status(), "Death-observation combat did not start.");
		PhantomAssertions.assertTrue(_player.doDie(target), "Could not kill the test-owned actor.");
		final PhantomCombatSessionSnapshot terminal = awaitTerminal();
		PhantomAssertions.assertEquals(PhantomCombatResult.PLAYER_DEAD, terminal.result(), "Actor death did not produce PLAYER_DEAD.");
		await(() -> (_combat.snapshot().actorLeases() == 0) && (_combat.snapshot().currentWorkers() == 0), "Player death retained combat lease or worker ownership.");
	}

	private void testNormalTownRespawn() throws Exception
	{
		PhantomAssertions.assertTrue(_player.isDead(), "Respawn case did not inherit the canonical dead actor.");
		PhantomAssertions.assertEquals(RespawnOutcome.COMPLETED, _combat.respawnTown(new PhantomRespawnRequest(_profile.profileId(), () -> false)), "Restricted normal-town respawn was not accepted.");
		await(() -> !_player.isDead() && !_player.isPendingRevive(), "Canonical normal-town teleport did not revive the actor.");
		PhantomAssertions.assertEquals(0, _player.getInstanceId(), "Normal-town respawn retained an instance.");
		PhantomAssertions.assertTrue(_player.isSpawned() && !_player.isTeleporting(), "Headless normal-town respawn did not complete canonical teleport lifecycle.");
		consumeTerminal();
	}

	private void testDematerializationDrain() throws Exception
	{
		resetActor(true);
		relocateToCombatPoint();
		final Monster target = spawnNormalMonster(targetMaximumHp());
		PhantomAssertions.assertEquals(StartStatus.ACCEPTED, _combat.startSession(request(target, PhantomCombatMode.MELEE_PHYSICAL, false, false)).status(), "Lease-drain combat did not start.");
		final AtomicReference<PhantomMaterializationService.DematerializeResult> result = new AtomicReference<>();
		final AtomicReference<Throwable> failure = new AtomicReference<>();
		final Thread dematerialize = new Thread(() ->
		{
			try
			{
				result.set(_materialization.dematerialize(_profile.profileId()));
			}
			catch (Throwable throwable)
			{
				failure.set(throwable);
			}
		}, "Task012-dematerialization-drain");
		dematerialize.start();
		Thread.sleep(200);
		PhantomAssertions.assertTrue(dematerialize.isAlive(), "Dematerialization passed an active combat ActionLease.");
		PhantomAssertions.assertEquals(CancelStatus.CANCELLED_CLEAN, _combat.cancel(_profile.profileId()), "Could not cancel combat for materialization drain.");
		dematerialize.join(WAIT_MILLIS);
		PhantomAssertions.assertFalse(dematerialize.isAlive(), "Dematerialization did not complete after combat lease release.");
		if (failure.get() != null)
		{
			throw new AssertionError("Dematerialization failed.", failure.get());
		}
		PhantomAssertions.assertTrue(result.get() != null, "Dematerialization returned no result.");
		PhantomAssertions.assertEquals(ResultStatus.SUCCESS, result.get().status(), "Dematerialization failed after combat cancellation.");
		consumeTerminal();
	}

	private void testNoPacketRoute() throws Exception
	{
		final Path combatRoot = _moduleRoot.resolve("java/org/l2jmobius/gameserver/phantoms/combat");
		final String source;
		try (var paths = Files.walk(combatRoot))
		{
			source = paths.filter(path -> path.toString().endsWith(".java")).sorted().map(path ->
			{
				try
				{
					return Files.readString(path, StandardCharsets.UTF_8);
				}
				catch (Exception e)
				{
					throw new IllegalStateException(e);
				}
			}).reduce("", String::concat);
		}
		PhantomAssertions.assertFalse(source.contains("network.clientpackets"), "Combat production code imports a client packet.");
		PhantomAssertions.assertFalse(source.contains("network.serverpackets"), "Combat production code imports a server packet.");
		PhantomAssertions.assertFalse(source.contains("sendPacket("), "Combat production code sends a packet directly.");
		PhantomAssertions.assertFalse(source.contains("RequestRestartPoint"), "Combat production code simulates restart packet handling.");
	}

	private void testOtherPlayerPickupEvidence()
	{
		resetActor(true);
		final Player observer = ensureObserver();
		final Monster target = spawnNormalMonster(targetMaximumHp());
		PhantomAssertions.assertTrue(target.doDie(_player), "Could not kill other-player pickup fixture.");
		final Item dropped = target.dropItem(_player, ADENA_ITEM_ID, 1);
		PhantomAssertions.assertTrue(dropped != null, "Could not create other-player ground item.");
		final long observerBefore = observer.getInventory().getInventoryItemCount(ADENA_ITEM_ID, -1);
		try (PhantomCombatActorLease lease = Optional.ofNullable(_backend.tryAcquireActor(_profile.profileId())).orElseThrow())
		{
			final LootCandidate candidate = exactCandidate(lease, dropped);
			dropped.getDropProtection().unprotect();
			dropped.setOwnerId(0);
			observer.doPickupItem(dropped);
			PhantomAssertions.assertEquals(observerBefore + 1, observer.getInventory().getInventoryItemCount(ADENA_ITEM_ID, -1), "Other test player did not acquire the item.");
			PhantomAssertions.assertEquals(LootObservation.LOST_WITHOUT_ACQUISITION, lease.observeLoot(candidate), "Other-player pickup was attributed to the phantom.");
		}
		finally
		{
			destroyInventoryCount(observer, ADENA_ITEM_ID, observer.getInventory().getInventoryItemCount(ADENA_ITEM_ID, -1) - observerBefore);
			destroyGroundItem(dropped);
		}
	}

	private void testDespawnEvidence()
	{
		resetActor(true);
		final Monster target = spawnNormalMonster(targetMaximumHp());
		PhantomAssertions.assertTrue(target.doDie(_player), "Could not kill despawn fixture.");
		final Item dropped = target.dropItem(_player, ADENA_ITEM_ID, 1);
		PhantomAssertions.assertTrue(dropped != null, "Could not create despawn ground item.");
		try (PhantomCombatActorLease lease = Optional.ofNullable(_backend.tryAcquireActor(_profile.profileId())).orElseThrow())
		{
			final LootCandidate candidate = exactCandidate(lease, dropped);
			dropped.decayMe();
			PhantomAssertions.assertEquals(LootObservation.LOST_WITHOUT_ACQUISITION, lease.observeLoot(candidate), "Despawn was attributed to the phantom.");
		}
		finally
		{
			destroyGroundItem(dropped);
		}
	}

	private void testRangeLossEvidence()
	{
		resetActor(true);
		final Monster target = spawnNormalMonster(targetMaximumHp());
		PhantomAssertions.assertTrue(target.doDie(_player), "Could not kill range-loss fixture.");
		final Item dropped = target.dropItem(_player, ADENA_ITEM_ID, 1);
		PhantomAssertions.assertTrue(dropped != null, "Could not create range-loss ground item.");
		final int x = _player.getX();
		final int y = _player.getY();
		final int z = _player.getZ();
		try (PhantomCombatActorLease lease = Optional.ofNullable(_backend.tryAcquireActor(_profile.profileId())).orElseThrow())
		{
			final LootCandidate candidate = exactCandidate(lease, dropped);
			_player.setXYZ(x + 1000, y, z);
			PhantomAssertions.assertEquals(LootObservation.INELIGIBLE, lease.observeLoot(candidate), "Out-of-radius item was attributed to the phantom.");
		}
		finally
		{
			_player.setXYZ(x, y, z);
			destroyGroundItem(dropped);
		}
	}

	private void testPickupCleanup(boolean stop)
	{
		resetActor(true);
		final Monster target = spawnNormalMonster(targetMaximumHp());
		PhantomAssertions.assertTrue(target.doDie(_player), "Could not kill exact PICK_UP target.");
		final Item dropped = target.dropItem(_player, ADENA_ITEM_ID, 1);
		PhantomAssertions.assertTrue(dropped != null, "Could not create exact PICK_UP item.");
		dropped.setXYZ(_player.getX() + 200, _player.getY(), _player.getZ());
		try
		{
			try (PhantomCombatActorLease lease = Optional.ofNullable(_backend.tryAcquireActor(_profile.profileId())).orElseThrow())
			{
				exactCandidate(lease, dropped);
				PhantomAssertions.assertEquals(ActionOutcome.ISSUED, lease.pickUp(dropped.getObjectId()), "Canonical PICK_UP was not issued.");
				PhantomAssertions.assertEquals(Intention.PICK_UP, _player.getAI().getIntention(), "Canonical PICK_UP intention was not established.");
				PhantomAssertions.assertEquals(dropped, _player.getTarget(), "Canonical PICK_UP did not own the exact item.");
				lease.cancelOwnedAction(new PhantomOwnedAction(stop ? 2 : 1, target.getObjectId(), null, dropped.getObjectId()));
			}
			PhantomAssertions.assertEquals(Intention.IDLE, _player.getAI().getIntention(), "Exact PICK_UP intention survived cleanup.");
			PhantomAssertions.assertTrue(_player.getTarget() == null, "Exact PICK_UP target survived cleanup.");
		}
		finally
		{
			destroyGroundItem(dropped);
		}
	}

	private void testForeignCastAndPickup()
	{
		resetActor(true);
		_player.setPlayerClass(MAGIC_CLASS_ID);
		_player.getStat().setLevel((byte) 85);
		final Skill skill = SkillData.getInstance().getSkill(MAGIC_SKILL_ID, 1);
		PhantomAssertions.assertTrue(skill != null, "Foreign CAST skill is unavailable.");
		_player.addSkill(skill, false);
		_player.enableSkill(skill);
		_player.setCurrentMp(_player.getMaxMp());
		final Monster owned = spawnNormalMonster(targetMaximumHp());
		try (PhantomCombatActorLease lease = Optional.ofNullable(_backend.tryAcquireActor(_profile.profileId())).orElseThrow())
		{
			final org.l2jmobius.gameserver.phantoms.combat.PhantomCombatLoadout.SelectedSkill staleSelected = new org.l2jmobius.gameserver.phantoms.combat.PhantomCombatLoadout.SelectedSkill(MAGIC_SKILL_ID + 1, 1);
			_player.abortCast();
			_player.setTarget(owned);
			_player.getAI().setIntention(Intention.CAST, skill, owned);
			PhantomAssertions.assertEquals(Intention.CAST, _player.getAI().getIntention(), "Foreign CAST intention was not established.");
			lease.cancelOwnedAction(new PhantomOwnedAction(1, owned.getObjectId(), staleSelected, 0));
			PhantomAssertions.assertEquals(Intention.CAST, _player.getAI().getIntention(), "Stale cleanup stopped a foreign CAST.");
			PhantomAssertions.assertEquals(owned, _player.getAI().getCastTarget(), "Stale cleanup replaced a foreign CAST target.");
			PhantomAssertions.assertEquals(owned, _player.getTarget(), "Stale cleanup cleared the selected target of a foreign CAST.");
		}
		finally
		{
			_player.abortCast();
			_player.getAI().setIntention(Intention.IDLE);
			_player.setTarget(null);
		}

		final Monster dropTarget = spawnNormalMonster(targetMaximumHp());
		PhantomAssertions.assertTrue(dropTarget.doDie(_player), "Could not kill foreign PICK_UP fixture.");
		final Item ownedItem = dropTarget.dropItem(_player, ADENA_ITEM_ID, 1);
		final Item foreignItem = ItemManager.createItem(ItemProcessType.LOOT, ADENA_ITEM_ID, 1, _player, this);
		PhantomAssertions.assertTrue((ownedItem != null) && (foreignItem != null), "Could not create foreign PICK_UP items.");
		foreignItem.dropMe(_player, _player.getX() + 100, _player.getY(), _player.getZ());
		try (PhantomCombatActorLease lease = Optional.ofNullable(_backend.tryAcquireActor(_profile.profileId())).orElseThrow())
		{
			PhantomAssertions.assertEquals(ActionOutcome.ISSUED, lease.pickUp(ownedItem.getObjectId()), "Owned PICK_UP was not issued.");
			_player.setTarget(foreignItem);
			_player.getAI().setIntention(Intention.PICK_UP, foreignItem);
			lease.cancelOwnedAction(new PhantomOwnedAction(2, dropTarget.getObjectId(), null, ownedItem.getObjectId()));
			PhantomAssertions.assertEquals(Intention.PICK_UP, _player.getAI().getIntention(), "Stale cleanup stopped a foreign PICK_UP.");
			PhantomAssertions.assertEquals(foreignItem, _player.getTarget(), "Stale cleanup replaced a foreign PICK_UP target.");
		}
		finally
		{
			_player.getAI().setIntention(Intention.IDLE);
			_player.setTarget(null);
			destroyGroundItem(ownedItem);
			destroyGroundItem(foreignItem);
		}
	}

	private void testPositiveSkillRejected()
	{
		resetActor(true);
		final Skill positive = SkillData.getInstance().getSkill(1040, 1);
		PhantomAssertions.assertTrue(positive != null, "Known positive one-target skill is unavailable.");
		_player.addSkill(positive, false);
		try (PhantomCombatActorLease lease = Optional.ofNullable(_backend.tryAcquireActor(_profile.profileId())).orElseThrow())
		{
			PhantomAssertions.assertFalse(lease.supportsSkill(new org.l2jmobius.gameserver.phantoms.combat.PhantomCombatLoadout.SelectedSkill(1040, 1), PhantomCombatMode.RANGED_MAGIC), "Known positive one-target skill was accepted.");
		}
	}

	private PhantomCombatRequest request(Monster target, PhantomCombatMode mode, boolean shots, boolean loot)
	{
		return new PhantomCombatRequest(_profile.profileId(), target.getObjectId(), mode, shots, loot, 30_000, () -> false);
	}

	private PhantomCombatRequest request(int targetObjectId, PhantomCombatMode mode)
	{
		return new PhantomCombatRequest(_profile.profileId(), targetObjectId, mode, false, false, 30_000, () -> false);
	}

	private PhantomCombatSessionSnapshot awaitTerminal() throws Exception
	{
		await(() -> _combat.find(_profile.profileId()).map(snapshot -> snapshot.result().terminal()).orElse(false), "Combat session did not become terminal.");
		return _combat.find(_profile.profileId()).orElseThrow();
	}

	private void awaitCombatOutcome(Monster target, String label) throws Exception
	{
		try
		{
			await(() -> target.isDead() || target.isAlikeDead() || _combat.find(_profile.profileId()).map(value -> value.result().terminal()).orElse(false), label + " neither killed nor terminated.");
		}
		catch (AssertionError error)
		{
			throw new AssertionError(label + " neither killed nor terminated: session=" + _combat.find(_profile.profileId()).orElse(null) + ", actorIntention=" + _player.getAI().getIntention() + ", actorTarget=" + _player.getTarget() + ", attackTarget=" + _player.getAI().getAttackTarget() + ", actorAttacking=" + _player.isAttackingNow() + ", targetHp=" + target.getCurrentHp(), error);
		}
	}

	private void consumeTerminal()
	{
		_combat.consumeTerminal(_profile.profileId());
	}

	private Player ensureObserver()
	{
		if (_observer == null)
		{
			_observer = Player.load(_environment.observer().objectId());
			PhantomAssertions.assertTrue(_observer != null, "Could not load the test-owned observer.");
			_observer.setXYZInvisible(_player.getX() + 30, _player.getY(), _player.getZ());
			_observer.spawnMe();
		}
		return _observer;
	}

	private static LootCandidate exactCandidate(PhantomCombatActorLease lease, Item item)
	{
		return lease.lootCandidates(32, 300).stream().filter(candidate -> candidate.worldObjectId() == item.getObjectId()).findFirst().orElseThrow(() -> new AssertionError("Exact real ground item was not observed."));
	}

	private static void destroyInventoryCount(Player owner, int itemId, long count)
	{
		if (count <= 0)
		{
			return;
		}
		final Item item = owner.getInventory().getItemByItemId(itemId);
		PhantomAssertions.assertTrue((item != null) && (owner.getInventory().destroyItem(ItemProcessType.DESTROY, item, count, owner, PhantomCombatServerIntegrationSuite.class) != null), "Could not restore test-owned inventory baseline.");
	}

	private static void destroyGroundItem(Item item)
	{
		if ((item != null) && (World.getInstance().findObject(item.getObjectId()) != null))
		{
			item.resetOwnerTimer();
			if (item.isSpawned())
			{
				item.decayMe();
			}
			ItemManager.destroyItem(ItemProcessType.DESTROY, item, null, PhantomCombatServerIntegrationSuite.class);
		}
	}

	private void resetActor(boolean revive)
	{
		if (_combat != null)
		{
			_combat.cancel(_profile.profileId());
			consumeTerminal();
		}
		if (_player == null)
		{
			return;
		}
		if (revive && _player.isDead())
		{
			_player.doRevive();
		}
		if (_player.isTeleporting())
		{
			_player.onTeleported();
		}
		_player.abortAttack();
		_player.abortCast();
		_player.setInvul(false);
		_player.setTarget(null);
		_player.getAI().setIntention(Intention.IDLE);
		_player.setPlayerClass(MELEE_CLASS_ID);
		_player.getStat().setLevel((byte) 85);
		_player.setCurrentHp(_player.getMaxHp());
		_player.setCurrentMp(_player.getMaxMp());
		_player.setCurrentCp(_player.getMaxCp());
	}

	private Item ensureWeapon()
	{
		Item weapon = _player.getInventory().getItemByItemId(WEAPON_ITEM_ID);
		if (weapon == null)
		{
			weapon = _player.getInventory().addItem(ItemProcessType.REWARD, WEAPON_ITEM_ID, 1, _player, this);
		}
		PhantomAssertions.assertTrue(weapon != null, "Could not create the test-owned weapon.");
		if (!weapon.isEquipped())
		{
			_player.getInventory().equipItem(weapon);
		}
		return weapon;
	}

	private void destroyInventoryItem(Item item)
	{
		if (item != null)
		{
			_player.getInventory().destroyItem(ItemProcessType.DESTROY, item, _player, this);
		}
	}

	private Monster spawnNormalMonster(double hp)
	{
		final NpcTemplate template = NpcData.getInstance().getTemplate(_combatPoint.npcId());
		PhantomAssertions.assertTrue(template != null, "Normal-monster template is unavailable.");
		final Monster monster = spawn(new Monster(template), 20 + (_worldFixtures.size() * 5));
		monster.setCurrentHp(Math.min(hp, monster.getMaxHp()));
		return monster;
	}

	private Monster spawnAtDistance(double hp, int distance)
	{
		final NpcTemplate template = NpcData.getInstance().getTemplate(_combatPoint.npcId());
		PhantomAssertions.assertTrue(template != null, "Normal-monster template is unavailable.");
		final Monster monster = new Monster(template);
		monster.setInstanceId(0);
		monster.spawnMe(_player.getX() + distance, _player.getY(), _player.getZ());
		monster.setCurrentHp(Math.min(hp, monster.getMaxHp()));
		_worldFixtures.add(monster);
		return monster;
	}

	private Monster spawnWithTerritory(int npcId, NpcSpawnTerritory territory, double hp)
	{
		final NpcTemplate template = NpcData.getInstance().getTemplate(npcId);
		PhantomAssertions.assertTrue(template != null, "Negative-control normal-monster template is unavailable.");
		final Monster monster = spawn(new Monster(template), 20 + (_worldFixtures.size() * 5), territory);
		monster.setCurrentHp(Math.min(hp, monster.getMaxHp()));
		return monster;
	}

	private Monster spawnExactPointMonster(int npcId, double hp)
	{
		final NpcTemplate template = NpcData.getInstance().getTemplate(npcId);
		PhantomAssertions.assertTrue(template != null, "Exact-point normal-monster template is unavailable.");
		final Monster monster = new Monster(template);
		monster.setInstanceId(0);
		final int offset = 20 + (_worldFixtures.size() * 5);
		try
		{
			final Spawn sourceSpawn = new Spawn(template);
			sourceSpawn.setXYZ(_player.getX() + offset, _player.getY(), _player.getZ());
			monster.setSpawn(sourceSpawn);
		}
		catch (ReflectiveOperationException exception)
		{
			throw new AssertionError("Could not bind exact-point negative-control Spawn.", exception);
		}
		monster.spawnMe(_player.getX() + offset, _player.getY(), _player.getZ());
		monster.setCurrentHp(Math.min(hp, monster.getMaxHp()));
		_worldFixtures.add(monster);
		return monster;
	}

	private <T extends Monster> T spawn(T monster, int xOffset)
	{
		return spawn(monster, xOffset, _sourceTerritory);
	}

	private <T extends Monster> T spawn(T monster, int xOffset, NpcSpawnTerritory territory)
	{
		monster.setInstanceId(0);
		if (territory != null)
		{
			try
			{
				final Spawn sourceSpawn = new Spawn(monster.getTemplate());
				sourceSpawn.setXYZ(_player.getX() + xOffset, _player.getY(), _player.getZ());
				sourceSpawn.setSpawnTerritory(territory);
				monster.setSpawn(sourceSpawn);
			}
			catch (ReflectiveOperationException exception)
			{
				throw new AssertionError("Could not bind factual source Spawn.", exception);
			}
		}
		monster.spawnMe(_player.getX() + xOffset, _player.getY(), _player.getZ());
		_worldFixtures.add(monster);
		return monster;
	}

	private double targetMaximumHp()
	{
		final NpcTemplate template = NpcData.getInstance().getTemplate(_combatPoint.npcId());
		return Math.max(1, template.getBaseHpMax());
	}

	private SpawnFact selectCombatPoint()
	{
		final List<NpcFact> candidates = _knowledge.query().snapshot().npcById().values().stream().filter(fact -> (fact.kind() == NpcKind.MONSTER) && fact.attackable() && fact.targetable()).sorted(Comparator.comparingInt(NpcFact::level).thenComparingInt(NpcFact::npcId)).toList();
		for (NpcFact candidate : candidates)
		{
			final Optional<SpawnFact> point = _knowledge.query().snapshot().spawnFactsByNpc().getOrDefault(candidate.npcId(), List.of()).stream().filter(fact -> (fact.pointKind() == SpawnPointKind.EXACT) && (fact.instanceId() == 0)).findFirst();
			if (point.isPresent())
			{
				return point.orElseThrow();
			}
		}
		throw new AssertionError("No deterministic normal-monster spawn fact is available.");
	}

	private void relocateToCombatPoint()
	{
		if (_player.isTeleporting())
		{
			_player.onTeleported();
		}
		if (_player.isSpawned())
		{
			_player.decayMe();
		}
		_player.setXYZInvisible(_combatPoint.x(), _combatPoint.y(), _combatPoint.z());
		_player.spawnMe();
		_player.revalidateZone(true);
	}

	private static void await(BooleanSupplier condition, String message) throws Exception
	{
		final long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(WAIT_MILLIS);
		while (!condition.getAsBoolean() && (System.nanoTime() < deadline))
		{
			Thread.sleep(10);
		}
		PhantomAssertions.assertTrue(condition.getAsBoolean(), message);
	}

	private static boolean waitFor(BooleanSupplier condition, long waitMillis) throws Exception
	{
		final long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(waitMillis);
		while (!condition.getAsBoolean() && (System.nanoTime() < deadline))
		{
			Thread.sleep(10);
		}
		return condition.getAsBoolean();
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
			throw new IllegalStateException("SHA-256 is unavailable.", exception);
		}
	}

	private static final class DeterministicSpoilMonster extends Monster
	{
		private int _effectiveLevel = 1;

		private DeterministicSpoilMonster(NpcTemplate template)
		{
			super(template);
		}

		@Override
		public int getLevel()
		{
			return _effectiveLevel;
		}

		private void restoreTemplateLevel()
		{
			_effectiveLevel = getTemplate().getLevel();
		}
	}

	private void cleanup() throws Exception
	{
		if (_mode == Mode.ACQUISITION)
		{
			RatesConfig.RATE_SPOIL_DROP_CHANCE_MULTIPLIER = _spoilChanceRateBaseline;
		}
		Throwable failure = null;
		try
		{
			if (_acquisition != null)
			{
				_acquisition.beginStop();
				PhantomAssertions.assertTrue(_acquisition.finishStop(), "Acquisition integration service did not stop cleanly.");
			}
			if (_combat != null)
			{
				_combat.beginStop();
				final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
				while (!_combat.finishStop() && (System.nanoTime() < deadline))
				{
					Thread.sleep(10);
				}
				PhantomAssertions.assertTrue(_combat.finishStop(), "Combat service did not stop cleanly.");
			}
			for (Monster fixture : List.copyOf(_worldFixtures))
			{
				if (fixture.isSpawned())
				{
					fixture.deleteMe();
				}
			}
			_worldFixtures.clear();
			if (_observer != null)
			{
				_environment.cleanupLoadedPlayer(_observer);
				_observer = null;
			}
			if (_materialization != null)
			{
				PhantomAssertions.assertEquals(PhantomMaterializationService.ServiceState.STOPPED, _materialization.shutdown().state(), "Materialization service did not stop.");
			}
			if ((_repository != null) && (_profile != null))
			{
				_repository.find(_profile.profileId()).ifPresent(profile -> _repository.delete(profile.profileId(), profile.rowVersion()));
			}
			if (_knowledge != null)
			{
				_knowledge.beginStop();
				PhantomAssertions.assertTrue(_knowledge.finishStop(), "Game Knowledge service did not stop.");
			}
			if (_backgroundProduction != null)
			{
				_backgroundProduction.close();
				_backgroundProduction = null;
			}
			if (_player != null)
			{
				_environment.assertClean(_environment.primary(), _player);
			}
		}
		catch (Throwable throwable)
		{
			failure = throwable;
		}
		try
		{
			_environment.shutdown();
		}
		catch (Throwable throwable)
		{
			if (failure == null)
			{
				failure = throwable;
			}
			else
			{
				failure.addSuppressed(throwable);
			}
		}
		if (failure instanceof Exception exception)
		{
			throw exception;
		}
		if (failure != null)
		{
			throw new RuntimeException(failure);
		}
	}

}
