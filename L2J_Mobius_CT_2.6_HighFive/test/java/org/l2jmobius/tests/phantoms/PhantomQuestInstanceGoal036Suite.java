/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.tests.phantoms;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.data.xml.DoorData;
import org.l2jmobius.gameserver.data.xml.ExperienceData;
import org.l2jmobius.gameserver.data.xml.MapRegionData;
import org.l2jmobius.gameserver.data.xml.NpcData;
import org.l2jmobius.gameserver.data.xml.SpawnData;
import org.l2jmobius.gameserver.managers.InstanceManager;
import org.l2jmobius.gameserver.managers.ScriptManager;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Npc;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.instance.Monster;
import org.l2jmobius.gameserver.model.actor.templates.NpcTemplate;
import org.l2jmobius.gameserver.model.groups.Party;
import org.l2jmobius.gameserver.model.groups.PartyDistributionType;
import org.l2jmobius.gameserver.model.instancezone.Instance;
import org.l2jmobius.gameserver.model.instancezone.InstanceWorld;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.model.item.instance.Item;
import org.l2jmobius.gameserver.model.script.Quest;
import org.l2jmobius.gameserver.model.script.QuestState;
import org.l2jmobius.gameserver.network.GameClient;
import org.l2jmobius.gameserver.phantoms.PhantomDiagnosticTrace;
import org.l2jmobius.gameserver.phantoms.PhantomMetrics;
import org.l2jmobius.gameserver.phantoms.acquisition.quest.PhantomAcquisitionQuestCatalog;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState;
import org.l2jmobius.gameserver.phantoms.activity.PhantomRelevanceSignal;
import org.l2jmobius.gameserver.phantoms.combat.L2jCombatBackend;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatCapabilityResolver;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatPolicy;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatService;
import org.l2jmobius.gameserver.phantoms.decision.PhantomCancellationToken;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoal;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStore;
import org.l2jmobius.gameserver.phantoms.knowledge.L2jGameKnowledgeBackend;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomCuratedKnowledgeParser;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeBuilder;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgePolicy;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeService;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.NpcFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.NpcKind;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.SpawnFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.SpawnPointKind;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomStaticManorParser;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationService;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService.ResultStatus;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfile;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.gameserver.phantoms.progression.L2jProgressionBackend;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionPolicy;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionService;
import org.l2jmobius.gameserver.phantoms.questinstance.L2jPhantomQuestInstanceBackend;
import org.l2jmobius.gameserver.phantoms.questinstance.PhantomQuestInstanceBackend.ActionStatus;
import org.l2jmobius.gameserver.phantoms.questinstance.PhantomQuestInstanceCatalog;
import org.l2jmobius.gameserver.phantoms.questinstance.PhantomQuestInstanceCatalog.Content;
import org.l2jmobius.gameserver.phantoms.questinstance.PhantomQuestInstanceService;
import org.l2jmobius.gameserver.phantoms.questinstance.PhantomQuestInstanceService.AdvanceResult;
import org.l2jmobius.gameserver.phantoms.questinstance.PhantomQuestInstanceService.Status;
import org.l2jmobius.gameserver.phantoms.topology.L2jTopologyValidationBackend;
import org.l2jmobius.gameserver.phantoms.topology.PhantomRelevanceSignalPort;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyLoader;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyMetrics;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPolicy;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyQuery;
import org.l2jmobius.gameserver.scripting.ScriptEngine;

/** Focused Goal036 catalog and guarded native quest/instance acceptance. */
public final class PhantomQuestInstanceGoal036Suite implements PhantomTestSuite
{
	private static final long SEED = 36003601L;
	private static final long WAIT_MILLIS = 20_000;
	private static final PhantomCancellationToken NOT_CANCELLED = () -> false;
	private final PhantomHeadlessPlayerTestEnvironment _environment = new PhantomHeadlessPlayerTestEnvironment();
	private final List<Npc> _fixtures = new ArrayList<>();
	private final List<Integer> _instanceIds = new ArrayList<>();
	private final MemoryGoalStore _goals = new MemoryGoalStore();
	private final RecordingSignals _signals = new RecordingSignals();
	private PhantomProfileRepository _profiles;
	private PhantomProfile _profile;
	private PhantomMaterializationService _materialization;
	private PhantomGameKnowledgeService _knowledge;
	private PhantomProgressionService _progression;
	private PhantomCombatService _combat;
	private PhantomNavigationService _navigation;
	private PhantomAcquisitionQuestCatalog _acquisitions;
	private PhantomQuestInstanceCatalog _catalog;
	private L2jPhantomQuestInstanceBackend _backend;
	private PhantomQuestInstanceService _service;
	private Player _player;
	private SpawnFact _normalWorldCombatPoint;
	private long _goalId = 3600;
	private boolean _environmentInitialized;

	@Override
	public String id()
	{
		return "quest-instance-goal036";
	}

	@Override
	public void beforeAll(PhantomTestContext context) throws Exception
	{
		PhantomAssertions.assertEquals(SEED, context.seed(), "Goal036 used the wrong deterministic seed.");
		_environment.initialize(context);
		_environmentInitialized = true;
		try
		{
			ScriptEngine.getInstance().executeScript(ScriptEngine.MASTER_HANDLER_FILE);
			ScriptEngine.getInstance().executeScript(Path.of("quests/QuestMasterHandler.java"));
			ScriptEngine.getInstance().executeScript(Path.of("village_master/ElfHumanFighterChange1/ElfHumanFighterChange1.java"));
			ScriptEngine.getInstance().executeScript(Path.of("instances/Kamaloka/Kamaloka.java"));
			ScriptEngine.getInstance().executeScript(Path.of("instances/PailakaSongOfIceAndFire/PailakaSongOfIceAndFire.java"));
			MapRegionData.getInstance();
			SpawnData.getInstance();
			DoorData.getInstance();

			final L2jTopologyValidationBackend topologyBackend = new L2jTopologyValidationBackend();
			final var topology = new PhantomTopologyLoader(Path.of("data/phantoms/topology"), topologyBackend, PhantomTopologyPolicy.productionDefaults()).load(1);
			final PhantomTopologyQuery topologyQuery = new PhantomTopologyQuery(topology, topologyBackend, new PhantomTopologyMetrics());
			final PhantomGameKnowledgePolicy knowledgePolicy = PhantomGameKnowledgePolicy.productionDefaults();
			final L2jGameKnowledgeBackend knowledgeBackend = new L2jGameKnowledgeBackend();
			_knowledge = new PhantomGameKnowledgeService(new PhantomGameKnowledgeBuilder(knowledgeBackend, new PhantomStaticManorParser(Path.of("data/Seeds.xml"), knowledgePolicy), new PhantomCuratedKnowledgeParser(Path.of("data/phantoms/knowledge"), knowledgeBackend, knowledgePolicy), topologyQuery, knowledgePolicy));
			PhantomAssertions.assertTrue(_knowledge.start(), "Goal036 Game Knowledge service did not start.");
			_normalWorldCombatPoint = selectNormalWorldCombatPoint();

			_profiles = PhantomProfileRepository.open();
			_profile = _profiles.create(_environment.primary().objectId());
			final PhantomMetrics metrics = new PhantomMetrics();
			_materialization = new PhantomMaterializationService(_profiles, PhantomIdentityLeaseRegistry.getInstance(), metrics, new PhantomDiagnosticTrace(false, 0, 0, metrics), 1);
			PhantomAssertions.assertTrue(_materialization.start(), "Goal036 materialization service did not start.");
			PhantomAssertions.assertEquals(ResultStatus.SUCCESS, _materialization.materialize(_profile.profileId()).status(), "Goal036 Player did not materialize.");
			_player = World.getInstance().getPlayer(_environment.primary().objectId());
			PhantomAssertions.assertTrue(_player != null, "Goal036 materialized Player is absent from World.");

			_progression = new PhantomProgressionService(new L2jProgressionBackend(_materialization, Path.of("."), () -> _knowledge.query()), PhantomProgressionPolicy.productionDefaults());
			_progression.start();
			_combat = new PhantomCombatService(new L2jCombatBackend(_materialization, () -> _knowledge.query(), () -> _progression.catalog()), PhantomCombatCapabilityResolver.fromProgression(() -> _progression.catalog()), PhantomCombatPolicy.productionDefaults(1));
			_combat.start();
			_navigation = new PhantomNavigationService(metrics);
			PhantomAssertions.assertTrue(_navigation.start(), "Goal036 Navigation service did not start.");
			_acquisitions = PhantomAcquisitionQuestCatalog.load(Path.of("data/phantoms/acquisition/high-five-quest-collection-v1.xml"), Path.of("data/scripts"));
			_catalog = PhantomQuestInstanceCatalog.load(Path.of("data/phantoms/quests/high-five-supported-content-v1.xml"), Path.of("."));
			_backend = new L2jPhantomQuestInstanceBackend(_materialization, _catalog, () -> _knowledge.query());
			_service = createService();
			preparePlayer(0, 0, 20);
			ensureWeapon();
			context.record("goal036.database", "127.0.0.1:3308/l2jmobiush5_phantom_test");
			context.record("goal036.catalogHash", _catalog.catalogHash());
		}
		catch (Throwable throwable)
		{
			cleanup(context);
			throw throwable;
		}
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("01-strict-catalog-and-fail-closed-dispatch", this::strictCatalog);
		registry.add("02-active-background-boundary-and-source-safety", this::activeBoundary);
		registry.add("03-q102-native-lifecycle-restart-and-rewards", this::q102Lifecycle);
		registry.add("04-q152-native-lifecycle-restart-and-rewards", this::q152Lifecycle);
		registry.add("05-q401-native-warrior-owner-and-idempotency", this::warriorLifecycle);
		registry.add("06-kamaloka-57-native-entry-combat-reuse", this::kamalokaLifecycle);
		registry.add("07-pailaka-128-native-lifecycle-and-rewards", this::pailakaLifecycle);
		registry.add("08-owned-lifecycle-and-native-authority", this::lifecycleAuthority);
	}

	private void strictCatalog(PhantomTestContext context) throws Exception
	{
		final Path catalogPath = context.moduleRoot().resolve("dist/game/data/phantoms/quests/high-five-supported-content-v1.xml");
		final Path sourceRoot = context.moduleRoot().resolve("dist/game");
		final PhantomQuestInstanceCatalog catalog = PhantomQuestInstanceCatalog.load(catalogPath, sourceRoot);
		PhantomAssertions.assertEquals(List.of("class.warrior-q401", "instance.kamaloka-57", "instance.pailaka-128", "quest.102", "quest.152"), catalog.contents().stream().map(Content::id).toList(), "Goal036 whitelist identities drifted.");
		catalog.validateRuntime(_acquisitions);
		PhantomAssertions.assertEquals(57, catalog.content("instance.kamaloka-57").orElseThrow().instanceTemplateId(), "Audited Kamaloka template changed.");
		PhantomAssertions.assertFalse(catalog.content("class.warrior-q401").orElseThrow().eligible(20, "HUMAN", 1), "Q401 whitelist accepted a non-Fighter class.");

		final String canonical = Files.readString(catalogPath, StandardCharsets.UTF_8);
		final List<String> invalid = List.of(
			canonical.replace("id=\"quest.152\"", "id=\"quest.102\""),
			canonical.replaceFirst("sourceSha256=\"[0-9a-f]{64}\"", "sourceSha256=\"" + "0".repeat(64) + "\""),
			canonical.replaceFirst("operation=\"TALK\"", "operation=\"CRAWL\""),
			canonical + " ".repeat(128 * 1024));
		for (int index = 0; index < invalid.size(); index++)
		{
			final Path malformed = Files.createTempFile(context.reportsDirectory(), "goal036-invalid-", ".xml");
			try
			{
				Files.writeString(malformed, invalid.get(index), StandardCharsets.UTF_8);
				PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> PhantomQuestInstanceCatalog.load(malformed, sourceRoot), "Malformed Goal036 catalog case " + index + " did not fail closed.");
			}
			finally
			{
				Files.deleteIfExists(malformed);
			}
		}

		installGoal("quest.999", PhantomQuestInstanceService.QUEST_GOAL_TYPE);
		final AdvanceResult unsupported = advance(PhantomActivityState.ACTIVE);
		PhantomAssertions.assertEquals(Status.FAIL, unsupported.status(), "Unsupported content did not fail closed.");
		PhantomAssertions.assertEquals("questinstance.content.unsupported", unsupported.reasonKey(), "Unsupported content reached an executable owner.");
		context.record("goal036.catalogEntries", catalog.contents().size());
	}

	private void activeBoundary(PhantomTestContext context) throws Exception
	{
		preparePlayer(0, 0, 20);
		installGoal("quest.152", PhantomQuestInstanceService.QUEST_GOAL_TYPE);
		final Quest quest = ScriptManager.getInstance().getQuest(152);
		PhantomAssertions.assertTrue(quest.getQuestState(_player, false) == null, "Q152 fixture was not pristine before BACKGROUND boundary.");
		final AdvanceResult result = advance(PhantomActivityState.BACKGROUND);
		PhantomAssertions.assertEquals(Status.RETRY, result.status(), "BACKGROUND executed an ACTIVE-only native step.");
		PhantomAssertions.assertEquals("questinstance.active.required", result.reasonKey(), "BACKGROUND did not request ACTIVE promotion.");
		PhantomAssertions.assertTrue(quest.getQuestState(_player, false) == null, "BACKGROUND created a native QuestState.");
		PhantomAssertions.assertEquals(1, _signals.submits, "ACTIVE relevance was not published exactly once.");
		PhantomAssertions.assertTrue(_acquisitions.rule("q00102-dryads-tear").orElseThrow().supports(2, 0, 20013, false), "Existing Q102 collection subset was displaced.");
		PhantomAssertions.assertTrue(_acquisitions.rule("q00152-golem-shard").orElseThrow().supports(2, 0, 20016, false), "Existing Q152 collection subset was displaced.");

		final String source = Files.readString(context.moduleRoot().resolve("java/org/l2jmobius/gameserver/phantoms/questinstance/PhantomQuestInstanceService.java")) + Files.readString(context.moduleRoot().resolve("java/org/l2jmobius/gameserver/phantoms/questinstance/L2jPhantomQuestInstanceBackend.java"));
		for (String forbidden : List.of("setClassId(", "setPlayerClass(", "UPDATE character_quests", "INSERT INTO character_quests", "teleToLocation(", "startQuestTimer(", "new Thread(", "ThreadPool.", "addExpAndSp(", "giveItems("))
		{
			PhantomAssertions.assertFalse(source.contains(forbidden), "Goal036 production source contains forbidden authority: " + forbidden);
		}
		context.record("goal036.background", "native ACTIVE steps wait; accepted acquisition rules retained");
	}

	private void q102Lifecycle(PhantomTestContext context) throws Exception
	{
		preparePlayer(18, 18, 11);
		ensureWeapon();
		installGoal("quest.102", PhantomQuestInstanceService.QUEST_GOAL_TYPE);
		spawnNpc(30284);
		PhantomAssertions.assertEquals(Status.PROGRESS, advance(PhantomActivityState.ACTIVE).status(), "Q102 native CREATED state was not prepared.");
		PhantomAssertions.assertTrue(questState(102).isCreated(), "Q102 native CREATED truth is absent.");
		PhantomAssertions.assertEquals(Status.FAIL, advance(PhantomActivityState.ACTIVE).status(), "Q102 level gate did not reject level 11.");

		preparePlayer(18, 18, 12);
		final long rewardBefore = itemCount(1060);
		PhantomAssertions.assertEquals(Status.PROGRESS, advance(PhantomActivityState.ACTIVE).status(), "Q102 native start event failed.");
		assertCond(102, 1, "Q102 did not enter cond 1.");
		restartService();
		spawnNpc(30156);
		advanceUntil(() -> cond(102) == 2, 8, "Q102 Cobendell transition did not reach cond 2.");
		killUntil(102, 3, 20013, 80, "Q102 Dryad collection");
		restartService();
		advanceUntil(() -> cond(102) == 4, 8, "Q102 collection hand-in did not reach cond 4.");
		advanceUntil(() -> cond(102) == 5, 8, "Q102 Alberius report did not reach cond 5.");
		spawnNpc(30217);
		spawnNpc(30219);
		spawnNpc(30221);
		spawnNpc(30285);
		advanceUntil(() -> cond(102) == 6, 16, "Q102 sentinel deliveries did not reach cond 6.");
		advanceUntil(() -> questState(102).isCompleted(), 8, "Q102 native terminal hand-in did not complete.");
		final long rewardAfter = itemCount(1060);
		PhantomAssertions.assertEquals(100L, rewardAfter - rewardBefore, "Q102 native Lesser Healing Potion reward drifted.");
		restartService();
		PhantomAssertions.assertEquals(Status.COMPLETE, advance(PhantomActivityState.ACTIVE).status(), "Completed Q102 was not idempotent after restart.");
		PhantomAssertions.assertEquals(rewardAfter, itemCount(1060), "Q102 retry duplicated native rewards.");
		context.record("goal036.q102", "COMPLETED,reward1060=100");
	}

	private void q152Lifecycle(PhantomTestContext context) throws Exception
	{
		preparePlayer(0, 0, 10);
		ensureWeapon();
		installGoal("quest.152", PhantomQuestInstanceService.QUEST_GOAL_TYPE);
		spawnNpc(30035);
		spawnNpc(30283);
		final long rewardBefore = itemCount(23);
		advanceUntil(() -> questStarted(152), 8, "Q152 did not start through Harris.");
		advanceUntil(() -> cond(152) == 2, 8, "Q152 Altran transition did not reach cond 2.");
		killUntil(152, 3, 20016, 60, "Q152 Stone Golem collection");
		restartService();
		advanceUntil(() -> cond(152) == 4, 8, "Q152 Altran hand-in did not reach cond 4.");
		advanceUntil(() -> questState(152).isCompleted(), 8, "Q152 native Harris terminal did not complete.");
		final long rewardAfter = itemCount(23);
		PhantomAssertions.assertEquals(1L, rewardAfter - rewardBefore, "Q152 native Wooden Breastplate reward drifted.");
		PhantomAssertions.assertEquals(Status.COMPLETE, advance(PhantomActivityState.ACTIVE).status(), "Completed Q152 was not idempotent.");
		PhantomAssertions.assertEquals(rewardAfter, itemCount(23), "Q152 retry duplicated native rewards.");
		context.record("goal036.q152", "COMPLETED,reward23=1");
	}

	private void warriorLifecycle(PhantomTestContext context) throws Exception
	{
		rematerialize();
		preparePlayer(0, 0, 17);
		installGoal("class.warrior-q401", PhantomQuestInstanceService.CLASS_GOAL_TYPE);
		ensureWeapon();
		spawnNpc(30010);
		PhantomAssertions.assertEquals(Status.PROGRESS, advance(PhantomActivityState.ACTIVE).status(), "Q401 native CREATED state was not prepared.");
		PhantomAssertions.assertEquals(Status.FAIL, advance(PhantomActivityState.ACTIVE).status(), "Q401 accepted level 17.");

		preparePlayer(0, 0, 20);
		ensureWeapon(129);
		spawnNpc(30373);
		final Content content = _catalog.content("class.warrior-q401").orElseThrow();
		final var beforeMissingMark = _backend.observe(_profile.profileId(), content);
		final var missingMark = _backend.invoke(_profile.profileId(), content, content.step("profession.warrior").orElseThrow(), beforeMissingMark.fingerprint());
		PhantomAssertions.assertTrue((missingMark.status() == ActionStatus.IDEMPOTENT) || (missingMark.status() == ActionStatus.ISSUED), "Canonical profession owner was not callable for missing-mark negative control.");
		PhantomAssertions.assertEquals(0, _player.getPlayerClass().getId(), "Missing mark changed Fighter class.");

		spawnNpc(30253);
		advanceUntil(() -> questStarted(401), 8, "Q401 did not start through Auron.");
		advanceUntil(() -> cond(401) == 2, 8, "Q401 Simplon transition did not reach cond 2.");
		killUntil(401, 3, 20035, 80, "Q401 skeleton collection");
		advanceUntil(() -> cond(401) == 4, 8, "Q401 Simplon hand-in did not reach cond 4.");
		advanceUntil(() -> cond(401) == 5, 8, "Q401 Auron sword transition did not reach cond 5.");
		await(() -> !_player.isInCombat() && !_player.isAttackingNow() && !_player.isCastingNow(), "Q401 actor did not leave native combat state before Progression equip.");
		advanceUntil(() -> _player.getActiveWeaponInstance() != null && (_player.getActiveWeaponInstance().getId() == 1142), 8, "Q401 sword was not equipped through Progression.");
		killUntil(401, 6, 20038, 25, "Q401 spider collection");
		advanceUntil(() -> questState(401).isCompleted(), 8, "Q401 native terminal did not complete.");
		PhantomAssertions.assertEquals(1L, itemCount(1145), "Q401 native Medallion is absent.");
		restartService();
		final long couponsBefore = itemCount(8869);
		advanceUntil(() -> _player.getPlayerClass().getId() == 1, 8, "Canonical normal-player profession owner did not change Fighter to Warrior.");
		PhantomAssertions.assertEquals(1, _player.getBaseClass(), "Canonical profession owner did not update base class.");
		PhantomAssertions.assertEquals(0L, itemCount(1145), "Canonical profession owner did not consume Medallion.");
		PhantomAssertions.assertEquals(15L, itemCount(8869) - couponsBefore, "Canonical profession owner reward drifted.");
		final long couponsAfter = itemCount(8869);
		PhantomAssertions.assertEquals(Status.COMPLETE, advance(PhantomActivityState.ACTIVE).status(), "Warrior retry was not idempotent.");
		PhantomAssertions.assertEquals(couponsAfter, itemCount(8869), "Warrior retry duplicated canonical coupons.");
		context.record("goal036.professionOwner", "village_master.ElfHumanFighterChange1 event=1 npc=30373");
	}

	private void kamalokaLifecycle(PhantomTestContext context) throws Exception
	{
		preparePlayer(1, 1, 17);
		installGoal("instance.kamaloka-57", PhantomQuestInstanceService.INSTANCE_GOAL_TYPE);
		PhantomAssertions.assertEquals(Status.FAIL, advance(PhantomActivityState.ACTIVE).status(), "Kamaloka accepted level 17.");
		preparePlayer(1, 1, 23);
		PhantomAssertions.assertEquals(Status.RETRY, advance(PhantomActivityState.ACTIVE).status(), "Kamaloka entered without a party.");
		final Party party = new Party(_player, PartyDistributionType.FINDERS_KEEPERS);
		_player.setParty(party);
		spawnNpc(30332);
		advanceUntil(() -> instanceTemplate() == 57, 8, "Kamaloka native owner did not create template 57.");
		final int instanceId = _player.getInstanceId();
		_instanceIds.add(instanceId);
		acknowledgeTeleport();
		final Instance instance = InstanceManager.getInstance().getInstance(instanceId);
		PhantomAssertions.assertTrue((instance != null) && instance.containsPlayer(_player.getObjectId()), "Player is not physically owned by Kamaloka Instance.");
		final Monster boss = instanceMonster(18554);
		relocate(boss);
		boss.setCurrentHp(1);
		boss.getStatus().stopHpMpRegeneration();
		restartService();
		PhantomAssertions.assertEquals(instanceId, _player.getInstanceId(), "Kamaloka restart created a duplicate instance.");
		combatUntil(() -> InstanceManager.getInstance().getInstanceTime(_player.getObjectId(), 57) > System.currentTimeMillis(), 20, "Kamaloka boss/reuse");
		final long remaining = instance.getInstanceEndTime() - System.currentTimeMillis();
		PhantomAssertions.assertTrue((remaining > TimeUnit.MINUTES.toMillis(4)) && (remaining <= TimeUnit.MINUTES.toMillis(5) + 5000), "Kamaloka native EXIT_TIME was not observed.");
		PhantomAssertions.assertEquals(Status.COMPLETE, advance(PhantomActivityState.ACTIVE).status(), "Kamaloka native reuse did not terminalize the goal.");
		PhantomAssertions.assertEquals(instanceId, _player.getInstanceId(), "Kamaloka terminal retry entered another instance.");
		context.record("goal036.kamaloka", "template=57,level=23,partyMax=6,boss=18554,reuse=native");
		InstanceManager.getInstance().destroyInstance(instanceId);
		await(() -> _player.getInstanceId() == 0, "Kamaloka fixture did not leave the destroyed native instance.");
		_player.setParty(null);
	}

	private void pailakaLifecycle(PhantomTestContext context) throws Exception
	{
		preparePlayer(1, 1, 35);
		installGoal("instance.pailaka-128", PhantomQuestInstanceService.INSTANCE_GOAL_TYPE);
		spawnNpc(32497);
		PhantomAssertions.assertEquals(Status.PROGRESS, advance(PhantomActivityState.ACTIVE).status(), "Pailaka Q128 native CREATED state was not prepared.");
		PhantomAssertions.assertEquals(Status.FAIL, advance(PhantomActivityState.ACTIVE).status(), "Pailaka accepted level 35.");
		preparePlayer(1, 1, 43);
		PhantomAssertions.assertEquals(Status.FAIL, advance(PhantomActivityState.ACTIVE).status(), "Pailaka accepted level 43.");
		preparePlayer(1, 1, 40);
		advanceUntil(() -> questState(128).isStarted(), 8, "Pailaka Q128 did not start through Adler.");
		advanceUntil(() -> instanceTemplate() == 43, 8, "Pailaka native owner did not create template 43.");
		final int instanceId = _player.getInstanceId();
		_instanceIds.add(instanceId);
		acknowledgeTeleport();
		final Instance instance = InstanceManager.getInstance().getInstance(instanceId);
		PhantomAssertions.assertTrue((instance != null) && instance.containsPlayer(_player.getObjectId()), "Player is not physically owned by Pailaka Instance.");
		restartService();
		PhantomAssertions.assertEquals(instanceId, _player.getInstanceId(), "Pailaka restart created a duplicate instance.");
		relocate(instanceNpc(32500));
		advanceUntil(() -> cond(128) == 2, 8, "Pailaka Sinai transition did not reach cond 2.");

		pailakaCombat(18610, 3, "Hillas");
		relocate(instanceNpc(32507));
		advanceUntil(() -> cond(128) == 4, 8, "Pailaka water inspector transition did not reach cond 4.");
		pailakaCombat(18609, 5, "Papion");
		pailakaCombat(18608, 6, "Kinsus");
		relocate(instanceNpc(32507));
		advanceUntil(() -> cond(128) == 7, 8, "Pailaka fire inspector transition did not reach cond 7.");
		pailakaCombat(18607, 8, "Gargos");
		pailakaCombat(18620, 9, "Adiantum");
		relocate(instanceNpc(32510));
		final Map<Integer, Long> rewardsBefore = Map.of(13294, itemCount(13294), 13293, itemCount(13293), 736, itemCount(736));
		advanceUntil(() -> questState(128).isCompleted(), 8, "Pailaka Adler terminal did not complete Q128.");
		for (int itemId : rewardsBefore.keySet())
		{
			PhantomAssertions.assertEquals(1L, itemCount(itemId) - rewardsBefore.get(itemId), "Pailaka native reward drifted: " + itemId);
		}
		final long remaining = instance.getInstanceEndTime() - System.currentTimeMillis();
		PhantomAssertions.assertTrue((remaining > TimeUnit.MINUTES.toMillis(4)) && (remaining <= TimeUnit.MINUTES.toMillis(5) + 5000), "Pailaka native EXIT_TIME was not observed.");
		restartService();
		PhantomAssertions.assertEquals(Status.COMPLETE, advance(PhantomActivityState.ACTIVE).status(), "Completed Pailaka was not idempotent after restart.");
		for (int itemId : rewardsBefore.keySet())
		{
			PhantomAssertions.assertEquals(rewardsBefore.get(itemId) + 1, itemCount(itemId), "Pailaka retry duplicated reward: " + itemId);
		}
		PhantomAssertions.assertEquals(instanceId, _player.getInstanceId(), "Pailaka terminal retry entered another instance.");
		context.record("goal036.pailaka", "quest=128,template=43,conds=1-9,rewards=13294/13293/736");
	}

	private void lifecycleAuthority(PhantomTestContext context)
	{
		PhantomAssertions.assertEquals(0, _service.snapshot().activeOperations(), "Goal036 retained an active caller operation.");
		PhantomAssertions.assertEquals(0, _service.snapshot().combatOperations(), "Goal036 retained Combat ownership.");
		PhantomAssertions.assertEquals(0, _service.snapshot().travelOperations(), "Goal036 retained Navigation ownership.");
		PhantomAssertions.assertTrue(_catalog.current(_acquisitions), "Goal036 native owner/source authority became stale.");
		PhantomAssertions.assertFalse(Files.exists(Path.of("data/phantoms/quests").resolve("quest-state.xml")), "Goal036 created a shadow QuestState artifact.");
		context.record("goal036.lifecycle", "caller-driven,active=0,combat=0,travel=0");
	}

	private PhantomQuestInstanceService createService()
	{
		final PhantomQuestInstanceService service = new PhantomQuestInstanceService(_goals, _catalog, _acquisitions, _backend, _combat, _navigation, _progression, _signals);
		PhantomAssertions.assertTrue(service.start(), "Goal036 service did not start.");
		return service;
	}

	private void restartService()
	{
		_service.beginStop();
		PhantomAssertions.assertTrue(_service.finishStop(), "Goal036 service did not drain for restart.");
		_service = createService();
	}

	private void rematerialize()
	{
		_service.beginStop();
		PhantomAssertions.assertTrue(_service.finishStop(), "Goal036 service did not drain for rematerialization.");
		PhantomAssertions.assertEquals(ResultStatus.SUCCESS, _materialization.dematerialize(_profile.profileId()).status(), "Goal036 Player did not dematerialize cleanly.");
		PhantomAssertions.assertEquals(ResultStatus.SUCCESS, _materialization.materialize(_profile.profileId()).status(), "Goal036 Player did not rematerialize cleanly.");
		_player = World.getInstance().getPlayer(_environment.primary().objectId());
		PhantomAssertions.assertTrue(_player != null, "Goal036 rematerialized Player is absent from World.");
		_service = createService();
	}

	private void installGoal(String contentId, String goalType)
	{
		final PhantomGoal goal = new PhantomGoal(++_goalId, goalType, PhantomGoalStatus.ACTIVE, new PhantomDomainRef("profile", Long.toString(_profile.profileId())), new PhantomDomainRef(PhantomQuestInstanceService.TARGET_NAMESPACE, contentId), 1, 0, null, List.of(), null, "questinstance.play", 500, 0, 0, 0, Map.of(), "goal036.test", 1);
		_goals.store(_profile.profileId(), goal);
	}

	private AdvanceResult advance(PhantomActivityState state)
	{
		final PhantomGoal goal = _goals.load(_profile.profileId()).orElseThrow().goal();
		return _service.advance(_profile.profileId(), goal.goalId(), goal.revision(), state, Math.max(1, System.nanoTime()), NOT_CANCELLED);
	}

	private void advanceUntil(BooleanSupplier condition, int maximum, String message) throws Exception
	{
		AdvanceResult last = null;
		for (int attempt = 0; (attempt < maximum) && !condition.getAsBoolean(); attempt++)
		{
			last = advance(PhantomActivityState.ACTIVE);
			PhantomAssertions.assertFalse((last.status() == Status.FAIL) || (last.status() == Status.CANCELLED), message + " Result=" + last);
			Thread.sleep(25);
		}
		PhantomAssertions.assertTrue(condition.getAsBoolean(), message + " Last=" + last);
	}

	private void killUntil(int questId, int expectedCond, int npcId, int maximum, String label) throws Exception
	{
		for (int attempt = 0; (attempt < maximum) && (cond(questId) < expectedCond); attempt++)
		{
			final Monster target = spawnMonster(npcId);
			target.setCurrentHp(1);
			target.getStatus().stopHpMpRegeneration();
			target.setOnKillDelay(100);
			AdvanceResult last = null;
			for (int pulse = 0; (pulse < 20) && !target.isAlikeDead(); pulse++)
			{
				last = advance(PhantomActivityState.ACTIVE);
				PhantomAssertions.assertFalse((last.status() == Status.FAIL) || (last.status() == Status.CANCELLED), label + " failed: " + last);
				Thread.sleep(100);
			}
			final long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(WAIT_MILLIS);
			while (!target.isAlikeDead() && (System.nanoTime() < deadline))
			{
				Thread.sleep(20);
			}
			final Item activeWeapon = _player.getActiveWeaponInstance();
			PhantomAssertions.assertTrue(target.isAlikeDead(), label + " target did not die through native Combat. Last=" + last + ", combat=" + _combat.find(_profile.profileId()).orElse(null) + ", targetHp=" + target.getCurrentHp() + "/" + target.getMaxHp() + ", pAtk=" + _player.getPAtk(target) + ", accuracy=" + _player.getAccuracy() + ", targetEvasion=" + target.getEvasionRate(_player) + ", attacking=" + _player.isAttackingNow() + ", attackRemainingNanos=" + Math.max(0, _player.getAttackEndTime() - System.nanoTime()) + ", pAtkSpd=" + _player.getPAtkSpd() + ", weapon=" + (activeWeapon == null ? 0 : activeWeapon.getId()) + ", casting=" + _player.isCastingNow() + ", intention=" + _player.getAI().getIntention());
			Thread.sleep(125);
			drainCombat(label);
		}
		PhantomAssertions.assertEquals(expectedCond, cond(questId), label + " did not reach native cond " + expectedCond + ".");
	}

	private void pailakaCombat(int npcId, int expectedCond, String label) throws Exception
	{
		final int requiredWeapon = switch (cond(128))
		{
			case 2 -> 13034;
			case 4, 5 -> 13035;
			case 7, 8 -> 13036;
			default -> 0;
		};
		if ((requiredWeapon > 0) && ((_player.getActiveWeaponInstance() == null) || (_player.getActiveWeaponInstance().getId() != requiredWeapon)))
		{
			await(() -> !_player.isInCombat() && !_player.isAttackingNow() && !_player.isCastingNow(), "Pailaka actor did not leave native combat state before quest-weapon equip.");
			advanceUntil(() -> (_player.getActiveWeaponInstance() != null) && (_player.getActiveWeaponInstance().getId() == requiredWeapon), 8, "Pailaka native quest weapon was not equipped through Progression: " + requiredWeapon);
		}
		final Monster target = instanceMonster(npcId);
		relocate(target);
		target.setCurrentHp(1);
		target.getStatus().stopHpMpRegeneration();
		target.setOnKillDelay(100);
		combatUntil(() -> cond(128) == expectedCond, 20, "Pailaka " + label);
		PhantomAssertions.assertEquals(expectedCond, cond(128), "Pailaka " + label + " did not use native Q128 callback.");
	}

	private void combatUntil(BooleanSupplier nativeTerminal, int maximumPulses, String label) throws Exception
	{
		for (int pulse = 0; (pulse < maximumPulses) && !nativeTerminal.getAsBoolean(); pulse++)
		{
			final AdvanceResult result = advance(PhantomActivityState.ACTIVE);
			PhantomAssertions.assertFalse((result.status() == Status.FAIL) || (result.status() == Status.CANCELLED), label + " failed: " + result);
			if (_combat.find(_profile.profileId()).filter(snapshot -> snapshot.result().terminal()).isPresent())
			{
				advance(PhantomActivityState.ACTIVE);
			}
			Thread.sleep(100);
		}
		final long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(WAIT_MILLIS);
		while (!nativeTerminal.getAsBoolean() && (System.nanoTime() < deadline))
		{
			Thread.sleep(20);
		}
		PhantomAssertions.assertTrue(nativeTerminal.getAsBoolean(), label + " did not reach native terminal truth. Combat=" + _combat.find(_profile.profileId()).orElse(null) + ", quest128Cond=" + (questStarted(128) ? cond(128) : 0) + ", instance=" + instanceTemplate());
		drainCombat(label);
	}

	private void drainCombat(String label) throws Exception
	{
		await(() -> _combat.find(_profile.profileId()).map(snapshot -> snapshot.result().terminal()).orElse(true), label + " Combat did not publish a terminal result.");
		for (int attempt = 0; (attempt < 10) && _combat.find(_profile.profileId()).isPresent(); attempt++)
		{
			advance(PhantomActivityState.ACTIVE);
			Thread.sleep(25);
		}
		PhantomAssertions.assertTrue(_combat.find(_profile.profileId()).isEmpty(), label + " retained a Combat session.");
		await(() -> !_player.isAttackingNow() && !_player.isCastingNow(), label + " actor did not return to native idle state.");
	}

	private Npc spawnNpc(int npcId)
	{
		final NpcTemplate template = NpcData.getInstance().getTemplate(npcId);
		PhantomAssertions.assertTrue(template != null, "NPC template is absent: " + npcId);
		final Npc npc = new Npc(template);
		npc.setInstanceId(_player.getInstanceId());
		npc.spawnMe(_player.getX() + 20 + (_fixtures.size() % 10), _player.getY(), _player.getZ());
		_fixtures.add(npc);
		return npc;
	}

	private Monster spawnMonster(int npcId)
	{
		final NpcTemplate template = NpcData.getInstance().getTemplate(npcId);
		PhantomAssertions.assertTrue(template != null, "Monster template is absent: " + npcId);
		final Monster monster = new Monster(template);
		monster.setInstanceId(_player.getInstanceId());
		monster.spawnMe(_player.getX() + 30, _player.getY(), _player.getZ());
		_fixtures.add(monster);
		return monster;
	}

	private Npc instanceNpc(int npcId)
	{
		final Instance instance = InstanceManager.getInstance().getInstance(_player.getInstanceId());
		return instance == null ? null : instance.getNpcs().stream().filter(npc -> (npc.getId() == npcId) && npc.isSpawned() && !npc.isAlikeDead()).findFirst().orElseThrow(() -> new AssertionError("Native instance NPC is absent: " + npcId));
	}

	private Monster instanceMonster(int npcId)
	{
		final Npc npc = instanceNpc(npcId);
		PhantomAssertions.assertTrue(npc instanceof Monster, "Native instance target is not a Monster: " + npcId);
		return (Monster) npc;
	}

	private void relocate(Npc npc)
	{
		PhantomAssertions.assertTrue(npc != null, "Native NPC fixture is absent.");
		if (npc.isSpawned())
		{
			npc.decayMe();
		}
		npc.setXYZInvisible(_player.getX() + 30, _player.getY(), _player.getZ());
		npc.spawnMe();
	}

	private void acknowledgeTeleport()
	{
		if (_player.isTeleporting())
		{
			_player.onTeleported();
		}
		_player.revalidateZone(true);
	}

	private void preparePlayer(int classId, int baseClassId, int level)
	{
		_player.abortAttack();
		_player.abortCast();
		_player.setTarget(null);
		if (_player.getInstanceId() == 0)
		{
			if (_player.isSpawned())
			{
				_player.decayMe();
			}
			_player.setXYZInvisible(_normalWorldCombatPoint.x(), _normalWorldCombatPoint.y(), _normalWorldCombatPoint.z());
			_player.spawnMe();
			_player.revalidateZone(true);
		}
		if (_player.getPlayerClass().getId() != classId)
		{
			_player.setPlayerClass(classId);
		}
		if (_player.getBaseClass() != baseClassId)
		{
			_player.setBaseClass(baseClassId);
		}
		_player.getStat().setExp(ExperienceData.getInstance().getExpForLevel(level));
		_player.getStat().setLevel((byte) level);
		_player.setInvul(true);
		_player.setCurrentHp(_player.getMaxHp());
		_player.setCurrentMp(_player.getMaxMp());
		_player.setCurrentCp(_player.getMaxCp());
	}

	private SpawnFact selectNormalWorldCombatPoint()
	{
		final Set<Integer> reservedTargets = Set.of(18554, 18607, 18608, 18609, 18610, 18620, 20013, 20016, 20019, 20035, 20038, 20042, 20043);
		return _knowledge.query().snapshot().npcById().values().stream()
			.filter(fact -> (fact.kind() == NpcKind.MONSTER) && fact.attackable() && fact.targetable() && !reservedTargets.contains(fact.npcId()))
			.sorted(Comparator.comparingInt(NpcFact::level).thenComparingInt(NpcFact::npcId))
			.flatMap(fact -> _knowledge.query().snapshot().spawnFactsByNpc().getOrDefault(fact.npcId(), List.of()).stream())
			.filter(fact -> (fact.pointKind() == SpawnPointKind.EXACT) && (fact.instanceId() == 0))
			.findFirst().orElseThrow(() -> new AssertionError("No isolated normal-world Goal036 combat point is available."));
	}

	private Item ensureWeapon()
	{
		return ensureWeapon(6);
	}

	private Item ensureWeapon(int itemId)
	{
		Item weapon = _player.getInventory().getItemByItemId(itemId);
		if (weapon == null)
		{
			weapon = _player.getInventory().addItem(ItemProcessType.REWARD, itemId, 1, _player, this);
		}
		PhantomAssertions.assertTrue(weapon != null, "Goal036 test weapon could not be created.");
		if (!weapon.isEquipped())
		{
			_player.getInventory().equipItem(weapon);
		}
		return weapon;
	}

	private QuestState questState(int questId)
	{
		final Quest quest = ScriptManager.getInstance().getQuest(questId);
		PhantomAssertions.assertTrue(quest != null, "Native quest is not loaded: " + questId);
		final QuestState state = quest.getQuestState(_player, false);
		PhantomAssertions.assertTrue(state != null, "Native QuestState is absent: " + questId);
		return state;
	}

	private int cond(int questId)
	{
		return questState(questId).getCond();
	}

	private boolean questStarted(int questId)
	{
		final Quest quest = ScriptManager.getInstance().getQuest(questId);
		final QuestState state = quest == null ? null : quest.getQuestState(_player, false);
		return (state != null) && state.isStarted();
	}

	private void assertCond(int questId, int expected, String message)
	{
		PhantomAssertions.assertEquals(expected, cond(questId), message);
	}

	private long itemCount(int itemId)
	{
		return _player.getInventory().getInventoryItemCount(itemId, -1);
	}

	private int instanceTemplate()
	{
		final InstanceWorld world = InstanceManager.getInstance().getPlayerWorld(_player);
		return world == null ? 0 : world.getTemplateId();
	}

	private static void await(BooleanSupplier condition, String message) throws Exception
	{
		final long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(WAIT_MILLIS);
		while (!condition.getAsBoolean() && (System.nanoTime() < deadline))
		{
			Thread.sleep(20);
		}
		PhantomAssertions.assertTrue(condition.getAsBoolean(), message);
	}

	@Override
	public void afterAll(PhantomTestContext context) throws Exception
	{
		cleanup(context);
	}

	private void cleanup(PhantomTestContext context) throws Exception
	{
		final List<Throwable> failures = new ArrayList<>();
		attempt(failures, () ->
		{
			if (_service != null)
			{
				_service.beginStop();
				PhantomAssertions.assertTrue(_service.finishStop(), "Goal036 service did not stop cleanly.");
			}
		});
		attempt(failures, () ->
		{
			if (_combat != null)
			{
				_combat.beginStop();
				final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
				while (!_combat.finishStop() && (System.nanoTime() < deadline))
				{
					Thread.sleep(20);
				}
				PhantomAssertions.assertTrue(_combat.finishStop(), "Goal036 Combat did not drain.");
			}
		});
		attempt(failures, () ->
		{
			for (int instanceId : List.copyOf(_instanceIds))
			{
				if (InstanceManager.getInstance().getInstance(instanceId) != null)
				{
					InstanceManager.getInstance().destroyInstance(instanceId);
				}
			}
			_instanceIds.clear();
			if (_player != null)
			{
				_player.setParty(null);
			}
			for (Npc fixture : List.copyOf(_fixtures))
			{
				if (fixture.isSpawned())
				{
					fixture.deleteMe();
				}
			}
			_fixtures.clear();
		});
		attempt(failures, () ->
		{
			if (_navigation != null)
			{
				_navigation.beginStop();
				PhantomAssertions.assertTrue(_navigation.finishStop(), "Goal036 Navigation did not stop.");
			}
			if (_progression != null)
			{
				_progression.beginStop();
				PhantomAssertions.assertTrue(_progression.finishStop(), "Goal036 Progression did not stop.");
			}
		});
		attempt(failures, () ->
		{
			if (_materialization != null)
			{
				PhantomAssertions.assertEquals(PhantomMaterializationService.ServiceState.STOPPED, _materialization.shutdown().state(), "Goal036 Materialization did not stop.");
			}
			if ((_profiles != null) && (_profile != null))
			{
				_profiles.find(_profile.profileId()).ifPresent(profile -> _profiles.delete(profile.profileId(), profile.rowVersion()));
			}
			if (_knowledge != null)
			{
				_knowledge.beginStop();
				PhantomAssertions.assertTrue(_knowledge.finishStop(), "Goal036 Game Knowledge did not stop.");
			}
		});
		attempt(failures, () ->
		{
			if (_environmentInitialized && DatabaseFactory.isInitialized())
			{
				GameClient.deleteCharByObjId(_environment.primary().objectId());
				GameClient.deleteCharByObjId(_environment.observer().objectId());
				for (int objectId : List.of(_environment.primary().objectId(), _environment.observer().objectId()))
				{
					PhantomAssertions.assertEquals(0L, scalar("SELECT COUNT(*) FROM character_quests WHERE charId=?", objectId), "Owned quest rows remain after Goal036 cleanup.");
					PhantomAssertions.assertEquals(0L, scalar("SELECT COUNT(*) FROM character_instance_time WHERE charId=?", objectId), "Owned instance reuse rows remain after Goal036 cleanup.");
					PhantomAssertions.assertEquals(0L, scalar("SELECT COUNT(*) FROM items WHERE owner_id=?", objectId), "Owned item rows remain after Goal036 cleanup.");
					PhantomAssertions.assertEquals(0L, scalar("SELECT COUNT(*) FROM characters WHERE charId=?", objectId), "Owned character row remains after Goal036 cleanup.");
				}
				PhantomAssertions.assertEquals(0, PhantomIdentityLeaseRegistry.getInstance().getActiveLeaseCount(), "Goal036 retained a materialization lease.");
				context.record("goal036.cleanup", "characters=0,quests=0,items=0,instanceReuse=0,instances=0,leases=0");
			}
		});
		attempt(failures, () ->
		{
			if (_environmentInitialized)
			{
				_environment.shutdown();
				_environmentInitialized = false;
			}
		});
		if (!failures.isEmpty())
		{
			final RuntimeException failure = new RuntimeException("Goal036 cleanup failed.");
			failures.forEach(failure::addSuppressed);
			throw failure;
		}
	}

	private static long scalar(String sql, int value) throws Exception
	{
		try (Connection connection = DatabaseFactory.getConnection(); PreparedStatement statement = connection.prepareStatement(sql))
		{
			statement.setInt(1, value);
			try (ResultSet result = statement.executeQuery())
			{
				PhantomAssertions.assertTrue(result.next(), "Goal036 cleanup query returned no row.");
				return result.getLong(1);
			}
		}
	}

	private static void attempt(List<Throwable> failures, CheckedAction action)
	{
		try
		{
			action.run();
		}
		catch (Throwable throwable)
		{
			failures.add(throwable);
		}
	}

	@FunctionalInterface
	private interface CheckedAction
	{
		void run() throws Exception;
	}

	private static final class MemoryGoalStore implements PhantomGoalStore
	{
		private long _profileId;
		private StoredGoal _stored;

		private void store(long profileId, PhantomGoal goal)
		{
			_profileId = profileId;
			_stored = new StoredGoal(goal, _stored == null ? 1 : _stored.rowVersion() + 1);
		}

		@Override
		public boolean profileExists(long profileId)
		{
			return profileId == _profileId;
		}

		@Override
		public Optional<StoredGoal> load(long profileId)
		{
			return profileId == _profileId ? Optional.ofNullable(_stored) : Optional.empty();
		}

		@Override
		public StoredGoal insert(long profileId, PhantomGoal goal)
		{
			store(profileId, goal);
			return _stored;
		}

		@Override
		public StoredGoal replace(long profileId, long expectedRowVersion, PhantomGoal goal)
		{
			_profileId = profileId;
			_stored = new StoredGoal(goal, expectedRowVersion + 1);
			return _stored;
		}

		@Override
		public void delete(long profileId, long expectedRowVersion)
		{
			_stored = null;
		}
	}

	private static final class RecordingSignals implements PhantomRelevanceSignalPort
	{
		private int submits;

		@Override
		public SignalDelivery submit(long profileId, PhantomRelevanceSignal signal)
		{
			submits++;
			return SignalDelivery.ACCEPTED;
		}

		@Override
		public SignalDelivery withdraw(long profileId, String source, long sequence)
		{
			return SignalDelivery.ACCEPTED;
		}
	}
}
