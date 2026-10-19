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
import org.l2jmobius.gameserver.config.RatesConfig;
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
import org.l2jmobius.gameserver.phantoms.questinstance.PhantomQuestInstanceBackend.ActionResult;
import org.l2jmobius.gameserver.phantoms.questinstance.PhantomQuestInstanceBackend.ActionStatus;
import org.l2jmobius.gameserver.phantoms.questinstance.PhantomQuestInstanceBackend.Observation;
import org.l2jmobius.gameserver.phantoms.questinstance.PhantomQuestInstanceBackend.Target;
import org.l2jmobius.gameserver.phantoms.questinstance.PhantomQuestInstanceCatalog;
import org.l2jmobius.gameserver.phantoms.questinstance.PhantomQuestInstanceCatalog.Content;
import org.l2jmobius.gameserver.phantoms.questinstance.PhantomQuestInstanceCatalog.Step;
import org.l2jmobius.gameserver.phantoms.questinstance.PhantomQuestInstanceService;
import org.l2jmobius.gameserver.phantoms.questinstance.PhantomQuestInstanceService.AdvanceResult;
import org.l2jmobius.gameserver.phantoms.questinstance.PhantomQuestInstanceService.Status;
import org.l2jmobius.gameserver.phantoms.topology.L2jTopologyValidationBackend;
import org.l2jmobius.gameserver.phantoms.topology.PhantomRelevanceSignalPort;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyLoader;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyMetrics;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPolicy;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyQuery;

/** Focused Goal036 catalog and guarded native quest/instance acceptance. */
public final class PhantomQuestInstanceGoal036Suite implements PhantomTestSuite
{
	public enum Mode
	{
		GOAL036,
		GOAL037_NON_ONE_X
	}

	private static final long SEED = 36003601L;
	private static final long GOAL037_SEED = 37003702L;
	private static final long WAIT_MILLIS = 20_000;
	private static final PhantomCancellationToken NOT_CANCELLED = () -> false;
	private final PhantomHeadlessPlayerTestEnvironment _environment = new PhantomHeadlessPlayerTestEnvironment();
	private final Mode _mode;
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
	private org.l2jmobius.gameserver.phantoms.questinstance.PhantomAutonomousCareerPolicy _careerPolicy;
	private final java.util.concurrent.atomic.AtomicBoolean _careerAllowed = new java.util.concurrent.atomic.AtomicBoolean(true);
	private final java.util.concurrent.atomic.AtomicReference<org.l2jmobius.gameserver.phantoms.population.PhantomPopulationCatalog.CareerArchetype> _careerPreference = new java.util.concurrent.atomic.AtomicReference<>(org.l2jmobius.gameserver.phantoms.population.PhantomPopulationCatalog.CareerArchetype.DAMAGE);
	private PhantomQuestInstanceService _service;
	private Player _player;
	private SpawnFact _normalWorldCombatPoint;
	private long _goalId = 3600;
	private boolean _environmentInitialized;
	private RateSnapshot _rateSnapshot;

	public PhantomQuestInstanceGoal036Suite()
	{
		this(Mode.GOAL036);
	}

	public PhantomQuestInstanceGoal036Suite(Mode mode)
	{
		_mode = mode;
	}

	@Override
	public String id()
	{
		return _mode == Mode.GOAL036 ? "quest-instance-goal036" : "quest-rates-native-goal037";
	}

	@Override
	public void beforeAll(PhantomTestContext context) throws Exception
	{
		PhantomAssertions.assertEquals(_mode == Mode.GOAL036 ? SEED : GOAL037_SEED, context.seed(), "Quest/instance suite used the wrong deterministic seed.");
		_environment.initialize(context);
		_environmentInitialized = true;
		try
		{
			if (_mode == Mode.GOAL037_NON_ONE_X)
			{
				_rateSnapshot = RateSnapshot.capture();
				RatesConfig.QUEST_ITEM_DROP_AMOUNT_MULTIPLIER = 3;
				RatesConfig.RATE_QUEST_REWARD = 5;
				RatesConfig.RATE_QUEST_REWARD_XP = 2;
				RatesConfig.RATE_QUEST_REWARD_SP = 3;
				RatesConfig.RATE_QUEST_REWARD_ADENA = 4;
				RatesConfig.RATE_QUEST_REWARD_USE_MULTIPLIERS = false;
			}
			PhantomSupportedContentScriptBootstrap.loadGoal036Owners(context);
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
			if (Boolean.getBoolean("phantom.test.careerProducer061")) { _careerPolicy = org.l2jmobius.gameserver.phantoms.questinstance.PhantomAutonomousCareerPolicy.load(context.moduleRoot().resolve("dist/game/data/phantoms/life/high-five-autonomous-career-v1.xml")); }
			_service = createService();
			preparePlayer(0, 0, 20);
			ensureWeapon();
			context.record("goal036.database", "127.0.0.1:3308/l2jmobiush5_phantom_test");
			context.record("goal036.catalogHash", _catalog.catalogHash());
			context.record("goal037.rateProfile", _mode == Mode.GOAL036 ? "1x/default" : "questItem=3,reward=5,xp=2,sp=3,adena=4");
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
		if (Boolean.getBoolean("phantom.test.careerWalkObstacle061"))
		{
			registry.add("14t-native-career-observed-walk-obstacle061", this::localWalk061);
			return;
		}
		if (Boolean.getBoolean("phantom.test.careerDefense061"))
		{
			registry.add("16d-native-career-defends-observed-pursuer061", this::careerNativeDefense061);
			registry.add("16h-native-career-owned-retreat061", this::careerNativeRetreat061);
			registry.add("16i-native-pve-walk-segment-excludes-new-threat061", this::careerNativeSegment061);
			registry.add("16j-native-career-retains-unsafe-attempt-and-requires-preparation061", this::careerNativePreparation061);
			registry.add("16k-native-career-refuses-overlevel-native-pursuer061", this::careerNativeOverlevel061);
			registry.add("16l-native-preparation-return-defends-a-manageable-pursuer061", this::careerPreparationDefense061);
			registry.add("16m-native-preparation-return-still-refuses-overlevel-pursuer061", context -> careerPreparationDefense061(context, true));
			registry.add("16g-native-career-rejects-spoiled-quest-target061", this::careerSpoiledTarget061);
			return;
		}
		if (Boolean.getBoolean("phantom.test.careerRouteProbe061"))
		{
			if (Boolean.getBoolean("phantom.test.careerWalkStep061"))
			{
				registry.add("13-native-career-two-minute-route-refusal061", this::careerSkeletonRoute061);
				registry.add("14-native-career-existing-goal-travel-deadline061", this::careerGoalTravelDeadline061);
				registry.add("14s-native-career-free-local-walk061", this::localWalk061);
			}
			else { registry.add("14r-native-career-reverse-walk-proof061", this::reverseRoute061); }
			return;
		}
		if (Boolean.getBoolean("phantom.test.careerFull061"))
		{
			registry.add("18-full-native-production-q401-career061", this::fullCareer061);
			return;
		}
		if (Boolean.getBoolean("phantom.test.careerProducer061"))
		{
			registry.add("09-farm-decision-autonomous-career-intent061", this::autonomousCareer061);
			registry.add("10-native-career-gludin-gludio-travel061", context -> careerTravel061(context, true));
			registry.add("11-native-career-paid-gk-conservation061", context -> careerTravel061(context, false));
			registry.add("12-native-career-nearest-supported-spawn061", this::careerNearestSpawn061);
			registry.add("13-native-career-two-minute-route-refusal061", this::careerSkeletonRoute061);
			registry.add("14-native-career-existing-goal-travel-deadline061", this::careerGoalTravelDeadline061);
			registry.add("15-native-career-child-combat-plan-lifetime061", this::careerCombatLifetime061);
			registry.add("16-native-warrior-owned-store-boundary061", this::careerClassStore061);
			registry.add("16a-native-career-finish-checkpoint061", this::careerFinishCheckpoint061);
			registry.add("16a2-native-career-missing-return-weapon061", context -> careerFinishCheckpoint061(context, true));
			registry.add("16b-native-accepted-career-cold-resume061", this::careerColdResume061);
			registry.add("16c-native-career-rest-before-next-combat061", this::careerNativeRest061);
			registry.add("16e-native-career-healing-supply-compatibility061", this::careerHealingSupply061);
			registry.add("16f-native-career-consumes-owned-healing-potion061", this::careerHealingCombat061);
			registry.add("17-native-career-combat-stock-drain061", this::careerCombatStop061);
			return;
		}
		registry.add("01-strict-catalog-and-fail-closed-dispatch", this::strictCatalog);
		registry.add("02-active-background-boundary-and-source-safety", this::activeBoundary);
		registry.add("03-q102-native-lifecycle-restart-and-rewards", this::q102Lifecycle);
		registry.add("04-q152-native-lifecycle-restart-and-rewards", this::q152Lifecycle);
		registry.add("05-q401-native-warrior-owner-and-idempotency", this::warriorLifecycle);
		registry.add("06-kamaloka-57-native-entry-combat-reuse", this::kamalokaLifecycle);
		registry.add("07-pailaka-128-native-lifecycle-and-rewards", this::pailakaLifecycle);
		registry.add("08-owned-lifecycle-and-native-authority", this::lifecycleAuthority);
	}

	private void localWalk061(PhantomTestContext context) throws Exception
	{
		preparePlayer(0, 0, 21);
		final boolean obstacle = Boolean.getBoolean("phantom.test.careerWalkObstacle061");
		_player.decayMe(); _player.setXYZInvisible(obstacle ? -42848 : -49005, obstacle ? 120726 : 120303, obstacle ? -3200 : -3080); _player.spawnMe(); _player.revalidateZone(true);
		context.record("career061.localWalk.input", "Independent route-only native TEST, no collection items; observedObstacle=" + obstacle + "; origin=" + _player.getX() + "," + _player.getY() + "," + _player.getZ() + "; full04 episode remains untouched; not Q401/B proof");
		final var content = _catalog.content("class.warrior-q401").orElseThrow(); final var step = content.step("simplon.handin").orElseThrow();
		// Independent route-only TEST condition, with no collection items: never full Q401/B evidence.
		final var quest = ScriptManager.getInstance().getQuest(401).getQuestState(_player, true);
		quest.setState(org.l2jmobius.gameserver.model.script.State.STARTED); quest.setCond(3, false);
		final var routeGoal = new PhantomGoal(++_goalId, PhantomQuestInstanceService.CLASS_GOAL_TYPE, PhantomGoalStatus.ACTIVE,
			new PhantomDomainRef("profile", Long.toString(_profile.profileId())), new PhantomDomainRef(PhantomQuestInstanceService.TARGET_NAMESPACE, content.id()),
			1, 0, null, List.of(), null, "questinstance.play", 500, 0, 0, System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(90), Map.of(), "career061.route_only_native_input", 1);
		_goals.store(_profile.profileId(), routeGoal);
		final var target = _backend.locate(_profile.profileId(), content, step);
		PhantomAssertions.assertTrue(target != null, "Native local WALK target unavailable.");
		final var source = new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint(_player.getX(), _player.getY(), _player.getZ(), 0);
		final var points = _backend.walkApproaches(_profile.profileId(), content, step, target, org.l2jmobius.gameserver.model.actor.Npc.INTERACTION_DISTANCE, _navigation.arrivalRadius());
		PhantomAssertions.assertTrue(!points.isEmpty() && points.size() <= 17, "No bounded native local WALK continuation for the existing long career journey.");
		boolean found = false;
		for (var point : points)
		{
			context.record("career061.localWalk.geometry." + point.x() + "." + point.y(), "sourceDistance=" + source.distanceTo(point) + "; targetDistance=" + point.distanceTo(new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint(target.x(), target.y(), target.z(), target.instanceId())) + "; priorTargetDistance=" + target.distance());
			if (obstacle && (source.distanceTo(point) > 3200 || point.distanceTo(new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint(target.x(), target.y(), target.z(), target.instanceId())) >= target.distance())) { continue; }
			PhantomAssertions.assertTrue(source.distanceTo(point) <= 3200 && point.distanceTo(new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint(target.x(), target.y(), target.z(), target.instanceId())) < target.distance(), "Free WALK candidate is not local forward progress.");
			Thread.sleep(_navigation.policy().pathfindingCooldownMillis() + 50);
			final long now = System.nanoTime();
			final var submitted = _navigation.submitWalkCorridor(new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationRequest(_profile.profileId(), source, point, now, now + TimeUnit.MINUTES.toNanos(2), 100_000));
			final var completed = new java.util.concurrent.atomic.AtomicReference<org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationResult>();
			await(() -> { _navigation.consume(submitted.requestId()).ifPresent(completed::set); return completed.get() != null; }, "Native local corridor did not settle.");
			context.record("career061.localWalk." + point.x() + "." + point.y(), completed.get());
			if (completed.get().route() != null) { found = true; break; }
		}
		PhantomAssertions.assertTrue(found, "All bounded native local WALK candidates were refused by actual geodata.");
		final long exp = _player.getExp(), sp = _player.getSp(), adena = _player.getAdena();
		final long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(45);
		while (source.distanceTo(new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint(_player.getX(), _player.getY(), _player.getZ(), 0)) < 100 && System.nanoTime() < until)
		{
			context.record("career061.localWalk.service", advance(PhantomActivityState.ACTIVE));
			Thread.sleep(250);
		}
		PhantomAssertions.assertTrue(source.distanceTo(new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint(_player.getX(), _player.getY(), _player.getZ(), 0)) >= 100, "Actual QuestInstance owner did not start the proven native free WALK.");
		PhantomAssertions.assertTrue(_player.getExp() == exp && _player.getSp() == sp && _player.getAdena() == adena && cond(401) == 3 && itemCount(1145) == 0, "A free route segment changed quest progress or native rewards/currency.");
	}

	private void reverseRoute061(PhantomTestContext context) throws Exception
	{
		preparePlayer(0, 0, 21);
		_player.decayMe(); _player.setXYZInvisible(-49005, 120303, -3080); _player.spawnMe(); _player.revalidateZone(true);
		final var content = _catalog.content("class.warrior-q401").orElseThrow(); final var step = content.step("simplon.handin").orElseThrow();
		final var target = _backend.locate(_profile.profileId(), content, step);
		PhantomAssertions.assertTrue(target != null, "Native reverse-route target unavailable.");
		final var source = new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint(_player.getX(), _player.getY(), _player.getZ(), 0);
		final var destinations = _backend.approaches(_profile.profileId(), content, step, target, org.l2jmobius.gameserver.model.actor.Npc.INTERACTION_DISTANCE, _navigation.arrivalRadius());
		context.record("career061.reverse.input", "Independent native route contract; original full-episode field origin; no quest stage/items/class transfer supplied; source=" + source + ";target=" + target + ";options=" + _backend.travelOptions(_profile.profileId(), content, TimeUnit.MINUTES.toMillis(90)));
		boolean found = false;
		for (var destination : destinations)
		{
			Thread.sleep(_navigation.policy().pathfindingCooldownMillis() + 50);
			final long now = System.nanoTime();
			final var submitted = _navigation.submitWalkCorridor(new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationRequest(_profile.profileId(), source, destination, now, now + TimeUnit.MINUTES.toNanos(2), 100_000));
			final var completed = new java.util.concurrent.atomic.AtomicReference<org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationResult>();
			await(() -> { _navigation.consume(submitted.requestId()).ifPresent(completed::set); return completed.get() != null; }, "Native reverse corridor did not settle.");
			context.record("career061.reverse." + destination.x() + "." + destination.y(), completed.get());
			System.out.println("CAREER061_REVERSE " + completed.get());
			if (completed.get().route() != null) { found = true; break; }
		}
		PhantomAssertions.assertTrue(found, "The observed Q401 field has no complete reverse native WALK corridor to any supported Simplon approach within existing bounds.");
	}

	private void careerHealingSupply061(PhantomTestContext context)
	{
		final var catalog = new org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceCatalogLoader(Path.of(".")).load().catalog();
		final var potion = org.l2jmobius.gameserver.data.xml.ItemData.getInstance().getTemplate(1539);
		PhantomAssertions.assertTrue(potion != null && java.util.Arrays.stream(potion.getSkills()).anyMatch(holder -> holder.getSkill() != null && holder.getSkill().isHealingPotionSkill())
			&& org.l2jmobius.gameserver.phantoms.commerce.L2jPhantomSupplyJourney.compatible(_player, potion), "Stock native HP_RECOVER potion is rejected by production HP preparation.");
		PhantomAssertions.assertFalse(catalog.findBuyOffers(1539, 0, 256).values().isEmpty(), "Stock healing preparation has no native buy source.");
		context.record("career061.healingSupply", "native1539/2037 HP_RECOVER/ItemTemplate/ItemSkills is compatible with production preparation and actual buy source; immutable Commerce hash unchanged; no inventory/quest/SP changes");
	}

	private void careerHealingCombat061(PhantomTestContext context) throws Exception
	{
		restartService(); preparePlayer(0, 0, 20); ensureWeapon(68);
		_player.getInventory().addItem(ItemProcessType.REWARD, 1539, 2, _player, this); // Separate finite native mechanism fixture, declared before combat; never B proof.
		_player.setCurrentHp(_player.getMaxHp() * 0.55); _player.setInvul(false);
		final var target = spawnMonster(20035); final long stock = itemCount(1539), exp = _player.getExp(), sp = _player.getSp();
		try
		{
			final var started = _combat.startContentSession(new org.l2jmobius.gameserver.phantoms.combat.PhantomCombatRequest(_profile.profileId(), target.getObjectId(), org.l2jmobius.gameserver.phantoms.combat.PhantomCombatMode.MELEE_PHYSICAL, true, false, 30_000, NOT_CANCELLED), "questinstance.class.warrior-q401");
			PhantomAssertions.assertTrue(started.status() == PhantomCombatService.StartStatus.ACCEPTED, "Independent healing combat fixture was not admitted.");
			await(() -> itemCount(1539) == stock - 1 && _player.getEffectList().getBuffInfoBySkillId(2037) != null, "Career Combat ignored its owned native healing potion at55%HP.");
			Thread.sleep(1500);
			PhantomAssertions.assertEquals(stock - 1, itemCount(1539), "Active HP_RECOVER or native item/skill reuse consumed another potion.");
			PhantomAssertions.assertFalse(_player.isDead(), "Native healing control died before observation.");
			context.record("career061.healingCombat", "independent native20035/weapon68/Fighter20/55%HP/finite2x1539 before measurement; product ItemSkills consumed exactly1 and applied native2037; no direct HP grant; EXP=" + exp + "->" + _player.getExp() + ";SP=" + sp + "->" + _player.getSp() + "; mechanism only, not Q401/B");
		}
		finally
		{
			// Measurement has ended. Restore the next independent invulnerable fixture before waiting for natural effect expiry.
			_player.setInvul(true);
			_combat.cancel(_profile.profileId());
			await(() -> _combat.find(_profile.profileId()).map(value -> value.result().terminal()).orElse(true), "Healing combat terminal cleanup did not settle.");
			for (int attempt = 0; attempt < 10 && _combat.find(_profile.profileId()).isPresent(); attempt++) { _combat.consumeTerminal(_profile.profileId()); Thread.sleep(25); }
			target.deleteMe();
			await(() -> _player.getEffectList().getBuffInfoBySkillId(2037) == null, "Earned native healing effect did not expire naturally before fixture save.");
		}
	}

	private void careerNativeDefense061(PhantomTestContext context) throws Exception
	{
		restartService(); preparePlayer(0, 0, 20); ensureWeapon(2499); _player.rewardSkills();
		PhantomAssertions.assertFalse(_player.isInsideZone(org.l2jmobius.gameserver.model.zone.ZoneId.PEACE), "Native pursuer fixture must be outside peace.");
		final var quest = ScriptManager.getInstance().getQuest(401).getQuestState(_player, true);
		quest.setState(org.l2jmobius.gameserver.model.script.State.STARTED); quest.setCond(3, false);
		installGoal("class.warrior-q401", PhantomQuestInstanceService.CLASS_GOAL_TYPE);
		final var pursuer = spawnMonster(20035);
		final var nativeBackend = new L2jCombatBackend(_materialization, () -> _knowledge.query(), () -> _progression.catalog());
		try (var actor = nativeBackend.tryAcquireActor(_profile.profileId()))
		{
			PhantomAssertions.assertTrue(actor != null && actor.observedAttackers(16).stream().noneMatch(value -> value.targetObjectId() == pursuer.getObjectId()), "An idle native Monster was mislabeled as a pursuer.");
		}
		_player.setInvul(false);
		final double hp = _player.getCurrentHp();
		pursuer.addDamageHate(_player, 0, 100); pursuer.getAI().setIntention(org.l2jmobius.gameserver.ai.Intention.ATTACK, _player);
		pursuer.doAttack(_player);
		try
		{
			await(() -> _player.getCurrentHp() < hp && pursuer.getAI().getAttackTarget() == _player && pursuer.getHating(_player) > 0, "Independent native pursuer did not produce actual damage/AI target/hate evidence.");
			context.record("career061.defense.nativeTargets", "Creature target=" + pursuer.getTarget() + "; native AI attack target=" + pursuer.getAI().getAttackTarget() + "; hate=" + pursuer.getHating(_player) + "; actual HP=" + hp + "->" + _player.getCurrentHp() + "; no Monster.setTarget input");
			try (var actor = nativeBackend.tryAcquireActor(_profile.profileId()))
			{
				PhantomAssertions.assertTrue(actor != null && actor.observedAttackers(16).stream().anyMatch(value -> value.targetObjectId() == pursuer.getObjectId()), "Native Combat missed a real damage/target/hate pursuer because Player attackBy is empty.");
			}
			final long exp = _player.getExp(), sp = _player.getSp();
			final var root = _goals.load(_profile.profileId()).orElseThrow();
			final var result = advance(PhantomActivityState.ACTIVE);
			context.record("career061.defense.input", "Independent mechanism-only native TEST: own new20035 on the existing non-peace field; real native damage/target/hate before measurement; declared cond3/no quest collection grants; no new goal after enrollment; not full Q401.");
			PhantomAssertions.assertTrue(_combat.find(_profile.profileId()).filter(value -> value.targetObjectId() == pursuer.getObjectId()).isPresent(), "Class journey ignored its actual native pursuer and continued navigation: " + result);
			await(() -> pursuer.isDead() && _player.getExp() > exp, "Defensive native Combat did not earn a real kill/reward.");
			drainCombat("career061 native travel defense");
			PhantomAssertions.assertTrue(quest.getCond() == 3 && itemCount(1140) == 0 && _player.getSp() > sp && _goals.load(_profile.profileId()).orElseThrow().equals(root), "Defensive continuation rewrote quest/root or synthesized quest swords.");
			context.record("career061.defense", "actual native damage -> current pursuer Combat -> kill/reward; EXP=" + exp + "->" + _player.getExp() + "; SP=" + sp + "->" + _player.getSp() + "; cond3/root/swords0 conserved");
		}
		finally
		{
			context.record("career061.defense.observed", "hp=" + hp + "->" + _player.getCurrentHp() + ";dead=" + _player.isDead() + ";invul=" + _player.isInvul() + ";attackBy=" + _player.getAttackByList() + ";pursuerDead=" + pursuer.isDead() + ";pursuerIntention=" + pursuer.getAI().getIntention());
			pursuer.deleteMe();
		}
	}

	private void careerPreparationDefense061(PhantomTestContext context) throws Exception
	{
		careerPreparationDefense061(context, false);
	}

	private void careerPreparationDefense061(PhantomTestContext context, boolean overlevel) throws Exception
	{
		restartService(); preparePlayer(0, 0, 19); ensureWeapon(2499); _player.rewardSkills();
		final var content = _catalog.content("class.warrior-q401").orElseThrow();
		final var root = new PhantomGoal(++_goalId, "farm.background", PhantomGoalStatus.ACTIVE, new PhantomDomainRef("profile", Long.toString(_profile.profileId())), new PhantomDomainRef("npc", Integer.toString(_normalWorldCombatPoint.npcId())), 1, 0, "background.farm", List.of(), null, "farm.background", 500, 0, 0, 0, Map.of(), "career061.return_defense_input", 1);
		_goals.store(_profile.profileId(), root);
		final var constraints = Map.ofEntries(Map.entry("owner.object", (long) _player.getObjectId()), Map.entry("owner.revision", root.revision()), Map.entry("class.from", 0L), Map.entry("class.to", 1L), Map.entry("class.index", 0L), Map.entry("weapon.object", (long) _player.getActiveWeaponInstance().getObjectId()), Map.entry("field.x", (long) _player.getX()), Map.entry("field.y", (long) _player.getY()), Map.entry("field.z", (long) _player.getZ()), Map.entry("risk.cell", Long.MIN_VALUE), Map.entry("risk.history", 1L), Map.entry("risk.preparation", 1L));
		final var child = org.l2jmobius.gameserver.phantoms.decision.PhantomJourneyLifetime.start(new PhantomGoal(root.goalId(), "class.transfer", PhantomGoalStatus.ACTIVE, root.subject(), new PhantomDomainRef("supported.content", content.id()), 1, 0, null, List.of(), null, "career.autonomous", 700, 0, 0, System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(90), constraints, "career.native.preparation_return.low_hp_stopped", 1, content.contentHash()), System.currentTimeMillis());
		final var previous = _profiles.findComponent(_profile.profileId(), "career.intent").orElse(null);
		final byte[] bytes = new org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateCodec().encode(child);
		if (previous == null) { _profiles.insertComponent(_profile.profileId(), "career.intent", 1, bytes); }
		else { _profiles.updateComponent(_profile.profileId(), "career.intent", previous.rowVersion(), 1, bytes); }
		restartService();
		final var candidates = new org.l2jmobius.gameserver.phantoms.decision.PhantomCandidateRegistry(); final var handlers = new org.l2jmobius.gameserver.phantoms.decision.PhantomStepHandlerRegistry();
		final var decision = new org.l2jmobius.gameserver.phantoms.questinstance.PhantomQuestInstanceDecision(_service); decision.registerCandidates(candidates); candidates.seal(); decision.registerHandlers(handlers); handlers.seal();
		final var planning = new org.l2jmobius.gameserver.phantoms.decision.PhantomPlanningContext(_profile.profileId(), root, org.l2jmobius.gameserver.phantoms.decision.PhantomCapabilitySet.empty(), PhantomActivityState.ACTIVE, System.nanoTime(), 1);
		final var plan = new org.l2jmobius.gameserver.phantoms.decision.PhantomUtilitySelector().select(candidates.snapshot(), planning).candidate().planFactory().create(planning);
		final var pursuer = spawnMonster(overlevel ? 20927 : 20035);
		final var spawn = new org.l2jmobius.gameserver.model.spawns.Spawn(pursuer.getTemplate()); spawn.setXYZ(pursuer.getX(), pursuer.getY(), pursuer.getZ()); pursuer.setSpawn(spawn);
		_player.setInvul(false); _player.setCurrentHp(_player.getMaxHp());
		final double hp = _player.getCurrentHp(); final long exp = _player.getExp(), sp = _player.getSp();
		context.record("career061.returnDefenseInput", "Independent saved preparation-return fixture, not Q401 progress evidence; Fighter19/native owned2499/native skills/full HP before measurement, one real" + pursuer.getId() + " pursuer; no changed quest stage/items/class during measurement; no Monster.setTarget");
		pursuer.addDamageHate(_player, 0, 100); pursuer.getAI().setIntention(org.l2jmobius.gameserver.ai.Intention.ATTACK, _player); pursuer.doAttack(_player);
		try
		{
			await(() -> _player.getCurrentHp() < hp && pursuer.getAI().getAttackTarget() == _player, "Return defense input lacks real native damage/pursuit.");
			final var execution = new org.l2jmobius.gameserver.phantoms.decision.PhantomStepContext(_profile.profileId(), root, plan, plan.steps().getFirst(), PhantomActivityState.ACTIVE, 1, 1, System.nanoTime(), 1, NOT_CANCELLED);
			final var result = handlers.snapshot().get(plan.steps().getFirst().actionKey()).execute(execution);
			final var combat = _combat.find(_profile.profileId()).orElseThrow();
			if (overlevel)
			{
				PhantomAssertions.assertTrue(combat.targetObjectId() == pursuer.getObjectId() && combat.phase() == org.l2jmobius.gameserver.phantoms.combat.PhantomCombatPhase.RETREATING, "Preparation return attacked an overlevel native pursuer: " + result + "; " + combat);
				context.record("career061.returnOverlevelObserved", "Independent native20927/27 and Fighter19; actual damage/pursuit; same preparation owner chose retreat, no native stats or quest progress bypass");
				return;
			}
			PhantomAssertions.assertTrue(combat.targetObjectId() == pursuer.getObjectId() && combat.phase() != org.l2jmobius.gameserver.phantoms.combat.PhantomCombatPhase.RETREATING, "Recovered preparation return still runs from a manageable native pursuer: " + result + "; " + combat);
			await(() -> pursuer.isDead() && _player.getExp() > exp && _player.getSp() > sp, "Return defense did not finish a real native pursuer.");
			PhantomAssertions.assertEquals(root, _goals.load(_profile.profileId()).orElseThrow().goal(), "Return defense replaced its current farm root.");
			PhantomAssertions.assertTrue(_player.getActiveClass() == 0 && itemCount(1145) == 0, "Preparation defense granted a class or quest mark.");
			context.record("career061.returnDefenseObserved", "Native pursuer killed, EXP=" + exp + "->" + _player.getExp() + "; SP=" + sp + "->" + _player.getSp() + "; same root=true; no class/mark grant; not full career/ordinary A/B");
		}
		finally { pursuer.deleteMe(); restartService(); }
	}

	private void careerNativeOverlevel061(PhantomTestContext context) throws Exception
	{
		restartService(); preparePlayer(0, 0, 19); ensureWeapon(68);
		final var content = _catalog.content("class.warrior-q401").orElseThrow();
		final var pursuer = spawnMonster(20927);
		_player.setInvul(false); _player.setCurrentHp(_player.getMaxHp());
		pursuer.addDamageHate(_player, 0, 100); pursuer.getAI().setIntention(org.l2jmobius.gameserver.ai.Intention.ATTACK, _player);
		try
		{
			PhantomAssertions.assertTrue(pursuer.getLevel() > _player.getLevel() + 3 && pursuer.getAI().getAttackTarget() == _player, "Independent native overlevel threat is unavailable.");
			final var observed = _backend.observe(_profile.profileId(), content);
			final var readiness = _backend.prepareCombat(_profile.profileId(), content, content.step("collect.skeleton").orElseThrow(), observed.fingerprint());
			context.record("career061.overlevelReadiness", "Independent TEST Fighter19/native68; native20927/27 with AI ATTACK/hate; no Creature target input, quest/class/items grants or HP changes after enrollment; result=" + readiness);
			PhantomAssertions.assertEquals("questinstance.combat.retreat_required", readiness.reasonKey(), "A healthy career accepted an unsuitable native pursuer as the next fight.");
		}
		finally { pursuer.deleteMe(); }
	}

	private void careerNativePreparation061(PhantomTestContext context) throws Exception
	{
		restartService(); preparePlayer(0, 0, 19); ensureWeapon(2499); ensureWeapon(68);
		final var quest = ScriptManager.getInstance().getQuest(401).getQuestState(_player, true);
		quest.setState(org.l2jmobius.gameserver.model.script.State.STARTED); quest.setCond(2, false);
		final PhantomGoal root = new PhantomGoal(++_goalId, "farm.background", PhantomGoalStatus.ACTIVE, new PhantomDomainRef("profile", Long.toString(_profile.profileId())), new PhantomDomainRef("npc", Integer.toString(_normalWorldCombatPoint.npcId())), 1, 0, "background.farm", List.of(), null, "farm.background", 500, 0, 0, 0, Map.of(), "career061.preparation_contract", 1);
		_goals.store(_profile.profileId(), root);
		final var candidates = new org.l2jmobius.gameserver.phantoms.decision.PhantomCandidateRegistry();
		final var handlers = new org.l2jmobius.gameserver.phantoms.decision.PhantomStepHandlerRegistry();
		final var decision = new org.l2jmobius.gameserver.phantoms.questinstance.PhantomQuestInstanceDecision(_service);
		decision.registerCandidates(candidates); candidates.seal(); decision.registerHandlers(handlers); handlers.seal();
		final var planning = new org.l2jmobius.gameserver.phantoms.decision.PhantomPlanningContext(_profile.profileId(), root, org.l2jmobius.gameserver.phantoms.decision.PhantomCapabilitySet.empty(), PhantomActivityState.ACTIVE, System.nanoTime(), 1);
		final var selector = new org.l2jmobius.gameserver.phantoms.decision.PhantomUtilitySelector();
		final var selected = selector.select(candidates.snapshot(), planning);
		PhantomAssertions.assertTrue(selected.candidate() != null, "Preparation input has no ordinary career producer.");
		final var plan = selected.candidate().planFactory().create(planning); final var step = plan.steps().getFirst();
		final var execution = new org.l2jmobius.gameserver.phantoms.decision.PhantomStepContext(_profile.profileId(), root, plan, step, PhantomActivityState.ACTIVE, 1, 1, System.nanoTime(), 1, NOT_CANCELLED);
		final var pursuer = spawnMonster(20001);
		final var spawn = new org.l2jmobius.gameserver.model.spawns.Spawn(pursuer.getTemplate()); spawn.setXYZ(pursuer.getX(), pursuer.getY(), pursuer.getZ()); pursuer.setSpawn(spawn);
		_player.setCurrentHp(_player.getMaxHp() * 0.55); _player.setInvul(false);
		final double hp = _player.getCurrentHp();
		final var actualDamage = new java.util.concurrent.atomic.AtomicBoolean();
		final var damageListener = new org.l2jmobius.gameserver.model.events.listeners.ConsumerEventListener(_player, org.l2jmobius.gameserver.model.events.EventType.ON_CREATURE_DAMAGE_RECEIVED, (org.l2jmobius.gameserver.model.events.holders.actor.creature.OnCreatureDamageReceived event) ->
		{
			if (event.getTarget() == _player && event.getAttacker() == pursuer && event.getDamage() > 0) { actualDamage.set(true); }
		}, this);
		_player.addListener(damageListener);
		pursuer.addDamageHate(_player, 0, 100); pursuer.getAI().setIntention(org.l2jmobius.gameserver.ai.Intention.ATTACK, _player);
		try
		{
			await(actualDamage::get, "Preparation contract has no real native threat.");
			context.record("career061.preparation.nativeDamage", "Native positive damage event from20001; HP=" + hp + "->" + _player.getCurrentHp() + "; regeneration may exceed this weak NPC damage; no Monster.setTarget input");
			final long exp = _player.getExp(), sp = _player.getSp();
			handlers.snapshot().get(step.actionKey()).execute(execution);
			PhantomAssertions.assertTrue(_combat.find(_profile.profileId()).isPresent(), "Low-health career did not own its native escape.");
			final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(35);
			while (_combat.find(_profile.profileId()).map(value -> !value.result().terminal()).orElse(false) && System.nanoTime() < deadline) { Thread.sleep(25); }
			handlers.snapshot().get(step.actionKey()).execute(execution);
			final var intent = _service.autonomousIntent(_profile.profileId()).orElseThrow();
			PhantomAssertions.assertTrue(intent.constraints().containsKey("risk.cell") && intent.constraints().containsKey("risk.preparation") && (intent.constraints().getOrDefault("risk.history", 0L) & 255) == 1, "Unsafe native attempt disappeared instead of persisting in its existing career owner: " + intent);
			restartService();
			final var reloaded = _service.autonomousIntent(_profile.profileId()).orElseThrow();
			PhantomAssertions.assertEquals(intent, reloaded, "Career restart discarded the unsafe region/preparation witness.");
			PhantomAssertions.assertTrue(_goals.load(_profile.profileId()).orElseThrow().goal().equals(root) && _player.getExp() == exp && _player.getSp() == sp && quest.getCond() == 2 && itemCount(1140) == 0, "Unsafe-attempt memory rewrote native quest/root/earned resources.");
			final var nextRoot = new PhantomGoal(root.goalId(), root.goalType(), root.status(), root.subject(), root.target(), root.requiredAmount(), root.currentAmount(), root.acquisitionMethod(), root.validSources(), root.selectedAnchor(), root.purposeKey(), root.priority(), root.riskBudget(), root.expenseBudget(), root.deadlineEpochMillis(), root.constraints(), "career061.preparation_next_root", root.revision() + 1);
			_goals.store(_profile.profileId(), nextRoot);
			PhantomAssertions.assertFalse(_service.needsOrOwnsAutonomousIntent(_profile.profileId(), nextRoot), "A fresh farm revision bypassed the unchanged-preparation guard.");
			await(() -> !_player.isInCombat() && !_player.isAttackingNow() && !_player.isCastingNow(), "Native preparation equip did not leave combat.");
			final var equipped = _progression.equipOwnedItem(new org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.EquipItemRequest(_profile.profileId(), _player.getInventory().getItemByItemId(2499).getObjectId(), NOT_CANCELLED));
			PhantomAssertions.assertTrue((equipped.status() == org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.OperationStatus.SUCCESS || equipped.status() == org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.OperationStatus.IDEMPOTENT) && _service.needsOrOwnsAutonomousIntent(_profile.profileId(), nextRoot), "An actual native owned-equipment improvement could not release the preparation guard: " + equipped);
			context.record("career061.preparation", "Independent contract only; premeasurement HP55%, own bag2499/68, weak native pursuer20001+Spawn; real retreat -> durable risk witness -> service reload; root/cond2/items/EXP/SP conserved; return adapter absent in this isolated service, not ordinary B");
		}
		finally { _player.removeListener(damageListener); pursuer.deleteMe(); }
	}

	private void careerNativeSegment061(PhantomTestContext context) throws Exception
	{
		restartService(); preparePlayer(0, 0, 20);
		final var hazard = spawnMonster(18910);
		hazard.decayMe(); hazard.spawnMe(_player.getX() + 500, _player.getY(), _player.getZ());
		try
		{
			PhantomAssertions.assertFalse(org.l2jmobius.gameserver.phantoms.navigation.PhantomNativeTravelActions.safePveWalkSegment(_player, _player.getX() + 1000, _player.getY(), _player.getZ(), List.of(hazard), Set.of()), "A safe endpoint admitted a walk through a native level83 threat.");
			PhantomAssertions.assertTrue(org.l2jmobius.gameserver.phantoms.navigation.PhantomNativeTravelActions.safePveWalkSegment(_player, _player.getX() - 500, _player.getY(), _player.getZ(), List.of(hazard), Set.of()), "The same native threat incorrectly blocked movement away from danger.");
			context.record("career061.safeSegment", "native18910/level83 observed only; endpoint outside radius but crossing segment rejected; opposite escape accepted; no damage/quest/reward mutation");
		}
		finally { hazard.deleteMe(); }
	}

	private void careerNativeRetreat061(PhantomTestContext context) throws Exception
	{
		restartService(); preparePlayer(0, 0, 20); ensureWeapon(68);
		// A separate mechanism scene has ordinary owned armor and a real spawn/home
		// before measurement; a spawnless Monster cannot exercise Mobius pursuit limits.
		for (int item : new int[] {24, 31})
		{
			final Item armor = _player.getInventory().getItemByItemId(item);
			_player.getInventory().equipItem(armor == null ? _player.getInventory().addItem(ItemProcessType.REWARD, item, 1, _player, this) : armor);
		}
		final var pursuer = spawnMonster(20035);
		final var spawn = new org.l2jmobius.gameserver.model.spawns.Spawn(pursuer.getTemplate());
		spawn.setXYZ(pursuer.getX(), pursuer.getY(), pursuer.getZ()); pursuer.setSpawn(spawn);
		_player.setInvul(false);
		final double hp = _player.getCurrentHp();
		context.record("career061.retreat.input", "Independent native contract only; own premeasurement armor24/31, native spawn/home; HP=" + hp + ";PDef=" + _player.getPDef(null) + ";speed=" + _player.getRunSpeed() + ";pursuerSpeed=" + pursuer.getRunSpeed() + ";nativeChase=" + org.l2jmobius.gameserver.config.NpcConfig.AGGRO_DISTANCE_CHECK_RANGE + ";spawnless/unarmored FAIL remains raw");
		final var incoming = new java.util.concurrent.ConcurrentHashMap<Integer, String>();
		final var damageListener = new org.l2jmobius.gameserver.model.events.listeners.ConsumerEventListener(_player, org.l2jmobius.gameserver.model.events.EventType.ON_CREATURE_DAMAGE_RECEIVED, (org.l2jmobius.gameserver.model.events.holders.actor.creature.OnCreatureDamageReceived event) ->
		{
			if (event.getTarget() == _player && event.getAttacker() instanceof Npc npc && incoming.size() < 16) { incoming.put(npc.getObjectId(), "npc=" + npc.getId() + ";level=" + npc.getLevel() + ";damage=" + event.getDamage() + ";skill=" + (event.getSkill() == null ? "physical" : event.getSkill().getId()) + ";crit=" + event.isCritical()); }
		}, this);
		_player.addListener(damageListener);
		pursuer.addDamageHate(_player, 0, 100);
		pursuer.getAI().setIntention(org.l2jmobius.gameserver.ai.Intention.ATTACK, _player);
		try
		{
			await(() -> _player.getCurrentHp() < hp, "Retreat input has no actual native pursuer damage.");
			final int x = _player.getX(), y = _player.getY();
			final long exp = _player.getExp(), sp = _player.getSp();
			final var started = _combat.startContentRetreatSession(new org.l2jmobius.gameserver.phantoms.combat.PhantomCombatRequest(_profile.profileId(), pursuer.getObjectId(), org.l2jmobius.gameserver.phantoms.combat.PhantomCombatMode.MELEE_PHYSICAL, false, false, 120_000, NOT_CANCELLED), "questinstance.class.warrior-q401");
			PhantomAssertions.assertTrue(started.accepted(), "Native recovery did not use the existing Combat admission.");
			await(() -> Math.hypot((long) _player.getX() - x, (long) _player.getY() - y) >= 200, "Native Geo-proved retreat issued no actual Player movement.");
			final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(35);
			while (_combat.find(_profile.profileId()).map(value -> !value.result().terminal()).orElse(false) && System.nanoTime() < deadline) { Thread.sleep(25); }
			final var terminal = _combat.find(_profile.profileId()).orElseThrow();
			PhantomAssertions.assertTrue(terminal.result() == org.l2jmobius.gameserver.phantoms.combat.PhantomCombatResult.LOW_HP_STOPPED && !_player.isAlikeDead() && !pursuer.isDead() && _player.getExp() == exp && _player.getSp() == sp, "Retreat became a victory/reward or unsafe death: " + terminal);
			context.record("career061.retreat", "Mechanism-only independent TEST; real damage then owned native Geo/Player MOVE; from=" + x + "," + y + ";to=" + _player.getX() + "," + _player.getY() + ";result=" + terminal.result() + ";HP=" + hp + "->" + _player.getCurrentHp() + ";EXP/SP conserved; no quest/class proof");
		}
		finally
		{
			context.record("career061.retreat.final", "HP=" + _player.getCurrentHp() + ";dead=" + _player.isAlikeDead() + ";xyz=" + _player.getX() + "," + _player.getY() + "," + _player.getZ() + ";pursuerDistance=" + _player.calculateDistance3D(pursuer) + ";pursuerHomeDistance=" + pursuer.calculateDistance2D(spawn.getLocation()));
			context.record("career061.retreat.damageSources", incoming);
			_player.removeListener(damageListener);
			_combat.cancel(_profile.profileId());
			await(() -> _combat.find(_profile.profileId()).map(value -> value.result().terminal()).orElse(true), "Retreat cleanup did not settle.");
			for (int attempt = 0; attempt < 10 && _combat.find(_profile.profileId()).isPresent(); attempt++) { _combat.consumeTerminal(_profile.profileId()); Thread.sleep(25); }
			pursuer.deleteMe();
		}
	}

	private void careerSpoiledTarget061(PhantomTestContext context) throws Exception
	{
		restartService(); preparePlayer(0, 0, 20); ensureWeapon(68);
		// Independent negative native protocol input, never a positive Q401 episode or class-transfer proof.
		final var quest = ScriptManager.getInstance().getQuest(401).getQuestState(_player, true);
		quest.setState(org.l2jmobius.gameserver.model.script.State.STARTED); quest.setCond(5, false);
		final var spoiled = spawnMonster(20038); spoiled.setScriptValue(2);
		final long exp = _player.getExp(), sp = _player.getSp(), adena = _player.getAdena();
		try
		{
			final var content = _catalog.content("class.warrior-q401").orElseThrow();
			final var target = _backend.locate(_profile.profileId(), content, content.step("collect.spider").orElseThrow());
			context.record("career061.spoiledTarget", "independent negative Q401 cond5/native20038 scriptValue2 before observation; selected=" + target + "; no kill, quest items, payment or class transfer supplied");
			PhantomAssertions.assertTrue(target == null || target.objectId() != spoiled.getObjectId(), "Career chose a native Q401 spider whose own script rejects every quest drop.");
			PhantomAssertions.assertTrue(quest.getCond() == 5 && itemCount(1144) == 0 && exp == _player.getExp() && sp == _player.getSp() && adena == _player.getAdena(), "Target observation changed native quest/resources.");
		}
		finally { spoiled.deleteMe(); }
	}

	private void careerNativeRest061(PhantomTestContext context) throws Exception
	{
		restartService();
		preparePlayer(0, 0, 20);
		final var home = _knowledge.query().snapshot().spawnFactsByNpc().getOrDefault(30253, List.of()).stream()
			.filter(value -> value.instanceId() == 0 && value.pointKind() == SpawnPointKind.EXACT).findFirst().orElseThrow();
		_player.decayMe(); _player.setXYZInvisible(home.x() + 64, home.y(), home.z()); _player.spawnMe(); _player.revalidateZone(true);
		final var quest = ScriptManager.getInstance().getQuest(401).getQuestState(_player, true);
		quest.setState(org.l2jmobius.gameserver.model.script.State.STARTED); quest.setCond(2, false);
		_player.setCurrentHp(_player.getMaxHp() * 0.4);
		installGoal("class.warrior-q401", PhantomQuestInstanceService.CLASS_GOAL_TYPE);
		final long exp = _player.getExp(), sp = _player.getSp(), adena = itemCount(57);
		final double hp = _player.getCurrentHp();
		advance(PhantomActivityState.BACKGROUND);
		PhantomAssertions.assertFalse(_player.isSitting(), "BACKGROUND content invoked native rest.");
		final var result = advance(PhantomActivityState.ACTIVE);
		context.record("career061.rest.input", "Independent mechanism-only TEST: safe native Simplon; cond2/no collection grants; initial40%HP before measurement; natural regeneration only after advance; not full Q401.");
		PhantomAssertions.assertTrue(result.reasonKey().equals("questinstance.combat.native_rest") && _player.isSitting(), "Career started another combat/navigation instead of native resource rest: " + result);
		Thread.sleep(8_000);
		PhantomAssertions.assertTrue(_player.getCurrentHp() > hp, "Stock seated regeneration did not recover native HP.");
		PhantomAssertions.assertTrue(_combat.find(_profile.profileId()).isEmpty() && _player.getExp() == exp && _player.getSp() == sp && itemCount(57) == adena && quest.getCond() == 2, "Rest created combat/rewards/quest progress or currency changes.");
		context.record("career061.rest", "actual stock sit; HP=" + hp + "->" + _player.getCurrentHp() + "; no combat/quest/EXP/SP/A changes; BACKGROUND refused");
		_player.standUp();
		await(() -> !_player.isSitting(), "Native rest cleanup did not finish its stock stand animation.");
	}

	private void fullCareer061(PhantomTestContext context) throws Exception
	{
		final int fullMinutes = Integer.getInteger("phantom.test.careerFullMinutes061", 45);
		PhantomAssertions.assertTrue(fullMinutes == 25 || fullMinutes == 45, "Only declared strict061 TEST episode bounds are supported.");
		// Independent finite native TEST input, declared before measurement. No ordinary058a mutation.
		preparePlayer(0, 0, 20);
		_player.getStat().setExp(1_023_783);
		_player.setSp(0);
		_player.rewardSkills();
		ensureWeapon(2499);
		for (int item : new int[] {352, 380, 2412, 61, 2428})
		{
			_player.getInventory().equipItem(_player.getInventory().addItem(ItemProcessType.REWARD, item, 1, _player, this));
		}
		_player.getInventory().addItem(ItemProcessType.REWARD, 57, 200_000, _player, this);
		_player.getInventory().addItem(ItemProcessType.REWARD, 1463, 2_000, _player, this);
		_player.getInventory().addItem(ItemProcessType.REWARD, 1835, 2_000, _player, this);
		_player.addAutoSoulShot(1463); _player.addAutoSoulShot(1835);
		final var start = _knowledge.query().snapshot().spawnFactsByNpc().getOrDefault(30253, List.of()).stream()
			.filter(value -> value.instanceId() == 0 && value.pointKind() == SpawnPointKind.EXACT).findFirst().orElseThrow();
		_player.decayMe(); _player.setXYZInvisible(start.x() + 64, start.y(), start.z()); _player.spawnMe(); _player.revalidateZone(true);
		_player.setInvul(false); _player.setCurrentHp(_player.getMaxHp()); _player.setCurrentMp(_player.getMaxMp()); _player.setCurrentCp(_player.getMaxCp());
		PhantomAssertions.assertTrue(_player.getActiveWeaponInstance() != null && _player.getActiveWeaponInstance().getId() == 2499, "Declared full career D weapon is not equipped before measurement.");
		final int originalWeaponObject = _player.getActiveWeaponInstance().getObjectId();
		final var content = _catalog.content("class.warrior-q401").orElseThrow();
		PhantomAssertions.assertEquals(org.l2jmobius.gameserver.phantoms.questinstance.PhantomQuestInstanceBackend.QuestStatus.ABSENT, _backend.observe(_profile.profileId(), content).quest().status(), "Full native career must start without Q401.");
		for (int item = 1138; item <= 1145; item++) { PhantomAssertions.assertEquals(0L, itemCount(item), "Full native career cannot start with quest items."); }
		final PhantomGoal root = new PhantomGoal(++_goalId, "farm.background", PhantomGoalStatus.ACTIVE,
			new PhantomDomainRef("profile", Long.toString(_profile.profileId())), new PhantomDomainRef("npc", Integer.toString(_normalWorldCombatPoint.npcId())),
			1, 0, "background.farm", List.of(), null, "farm.background", 500, 0, 0, 0, Map.of(), "career061.full_native_initial_farm", 1);
		_goals.store(_profile.profileId(), root);
		final var candidates = new org.l2jmobius.gameserver.phantoms.decision.PhantomCandidateRegistry();
		final var handlers = new org.l2jmobius.gameserver.phantoms.decision.PhantomStepHandlerRegistry();
		final var decision = new org.l2jmobius.gameserver.phantoms.questinstance.PhantomQuestInstanceDecision(_service);
		decision.registerCandidates(candidates); candidates.seal(); decision.registerHandlers(handlers); handlers.seal();
		final var selector = new org.l2jmobius.gameserver.phantoms.decision.PhantomUtilitySelector();
		context.record("career061.full.input", "NEW058t; Fighter20/near21/SP0/native auto-get; D2499/352/380/2412/61/2428; finite200000A grant plus existing fixture baseline/2000D/2000NG/native autoshots; native Simplon30253 start; invul=false; ABSENT/no1138..1145; farm root only; NEW episode max" + fullMinutes + "min/max10min without native progress, declared before enrollment after measuring remaining delivery path; actual native spawns/rates/quest requirements unchanged; secondary proof, not ordinary B; actualA=" + itemCount(57));
		final long until = System.nanoTime() + TimeUnit.MINUTES.toNanos(fullMinutes);
		String previous = "";
		long sequence = 0;
		long nextReport = 0;
		long lastProgress = System.nanoTime(); String progress = "";
		while (System.nanoTime() < until && _player.getActiveClass() == 0 && !_player.isAlikeDead())
		{
			final var planning = new org.l2jmobius.gameserver.phantoms.decision.PhantomPlanningContext(_profile.profileId(), root,
				org.l2jmobius.gameserver.phantoms.decision.PhantomCapabilitySet.empty(), PhantomActivityState.ACTIVE, System.nanoTime(), ++sequence);
			final var selected = selector.select(candidates.snapshot(), planning);
			if (selected.candidate() != null)
			{
				final var plan = selected.candidate().planFactory().create(planning); final var step = plan.steps().getFirst();
				final var result = handlers.snapshot().get(step.actionKey()).execute(new org.l2jmobius.gameserver.phantoms.decision.PhantomStepContext(
					_profile.profileId(), root, plan, step, PhantomActivityState.ACTIVE, 1, sequence, System.nanoTime(), 1, NOT_CANCELLED));
				final String current = "quest=" + _backend.observe(_profile.profileId(), content).quest() + ";class=" + _player.getActiveClass() + ";result=" + result;
				if (!current.equals(previous)) { context.record("career061.full.step." + sequence, current + ";exp=" + _player.getExp() + ";sp=" + _player.getSp() + ";hp=" + _player.getCurrentHp() + ";xyz=" + _player.getX() + "," + _player.getY() + "," + _player.getZ()); previous = current; }
			}
			if (System.nanoTime() >= nextReport)
			{
				System.out.println("CAREER061_NATIVE " + previous + ";exp=" + _player.getExp() + ";sp=" + _player.getSp() + ";hp=" + _player.getCurrentHp() + ";xyz=" + _player.getX() + "," + _player.getY() + "," + _player.getZ() + ";navigation=" + _navigation.snapshot() + ";combat=" + _combat.find(_profile.profileId()));
				nextReport = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
			}
			final String observedProgress = _backend.observe(_profile.profileId(), content).quest() + ":" + _player.getExp() + ":" + _player.getSp() + ":" + _player.getX() + ":" + _player.getY() + ":" + (int) _player.getCurrentHp();
			if (!observedProgress.equals(progress)) { progress = observedProgress; lastProgress = System.nanoTime(); }
			PhantomAssertions.assertTrue(System.nanoTime() - lastProgress < TimeUnit.MINUTES.toNanos(10), "Declared native no-progress bound reached: " + previous);
			Thread.sleep(1_000);
		}
		context.record("career061.full.terminal", "class=" + _player.getActiveClass() + ";dead=" + _player.isAlikeDead() + ";quest=" + _backend.observe(_profile.profileId(), content).quest() + ";exp=" + _player.getExp() + ";sp=" + _player.getSp() + ";weapon=" + (_player.getActiveWeaponInstance() == null ? "NONE" : _player.getActiveWeaponInstance().getObjectId()));
		PhantomAssertions.assertTrue(_player.getActiveClass() == 1 && _player.getBaseClass() == 1 && questState(401).isCompleted(), "Full native production career did not earn Warrior within its original episode: " + previous);
		PhantomAssertions.assertEquals(0L, itemCount(1145), "Native master did not consume its earned mark.");
		PhantomAssertions.assertTrue(_player.getActiveWeaponInstance() != null && _player.getActiveWeaponInstance().getObjectId() == originalWeaponObject, "Full native career lost its initial owned field weapon.");
	}

	private void autonomousCareer061(PhantomTestContext context) throws Exception
	{
		preparePlayer(0, 0, 19);
		PhantomAssertions.assertTrue(_backend.observe(_profile.profileId(), _catalog.content("class.warrior-q401").orElseThrow()).quest().status() == org.l2jmobius.gameserver.phantoms.questinstance.PhantomQuestInstanceBackend.QuestStatus.ABSENT && itemCount(1145) == 0 && _player.getClassIndex() == 0, "Career061 requires an unstarted native Fighter quest, no mark and main class.");
		final PhantomGoal root = new PhantomGoal(++_goalId, "farm.background", PhantomGoalStatus.ACTIVE,
			new PhantomDomainRef("profile", Long.toString(_profile.profileId())), new PhantomDomainRef("npc", Integer.toString(_normalWorldCombatPoint.npcId())),
			1, 0, "background.farm", List.of(), null, "farm.background", 500, 0, 0, 0, Map.of(), "career061.initial_farm", 1);
		_goals.store(_profile.profileId(), root);
		final var candidates = new org.l2jmobius.gameserver.phantoms.decision.PhantomCandidateRegistry();
		final var handlers = new org.l2jmobius.gameserver.phantoms.decision.PhantomStepHandlerRegistry();
		final var decision = new org.l2jmobius.gameserver.phantoms.questinstance.PhantomQuestInstanceDecision(_service);
		decision.registerCandidates(candidates); candidates.seal();
		decision.registerHandlers(handlers); handlers.seal();
		final var planning = new org.l2jmobius.gameserver.phantoms.decision.PhantomPlanningContext(_profile.profileId(), root,
			org.l2jmobius.gameserver.phantoms.decision.PhantomCapabilitySet.empty(), PhantomActivityState.ACTIVE, System.nanoTime(), 1);
		final var selector = new org.l2jmobius.gameserver.phantoms.decision.PhantomUtilitySelector();
		_careerAllowed.set(false);
		PhantomAssertions.assertTrue(selector.select(candidates.snapshot(), planning).candidate() == null, "Career escaped external HOLD/party ownership.");
		_careerAllowed.set(true); _careerPreference.set(org.l2jmobius.gameserver.phantoms.population.PhantomPopulationCatalog.CareerArchetype.TANK);
		PhantomAssertions.assertTrue(selector.select(candidates.snapshot(), planning).candidate() == null, "A Tank preference was forced to Warrior.");
		_careerPreference.set(org.l2jmobius.gameserver.phantoms.population.PhantomPopulationCatalog.CareerArchetype.DAMAGE);
		preparePlayer(0, 0, 17);
		PhantomAssertions.assertTrue(selector.select(candidates.snapshot(), planning).candidate() == null, "Career accepted a native under-level Fighter.");
		preparePlayer(18, 18, 19);
		PhantomAssertions.assertTrue(selector.select(candidates.snapshot(), planning).candidate() == null, "Career forced another native race/class into Q401.");
		preparePlayer(0, 0, 19);
		final var selected = new org.l2jmobius.gameserver.phantoms.decision.PhantomUtilitySelector().select(candidates.snapshot(), planning);
		context.record("career061.producer", "native Fighter0/main/19; Q401 absent; no class goal supplied by fixture; selection=" + selected);
		PhantomAssertions.assertTrue(selected.candidate() != null, "Eligible native farm Decision has no autonomous supported profession producer.");
		PhantomAssertions.assertTrue(_service.permitsAutonomousVisibleDecision(_profile.profileId(), root), "Visible admission waits for an already-created career child before allowing its producer to run.");
		final var plan = selected.candidate().planFactory().create(planning);
		final var step = plan.steps().getFirst();
		final int signalsBeforeActiveAdvance = _signals.submits;
		final var result = handlers.snapshot().get(step.actionKey()).execute(new org.l2jmobius.gameserver.phantoms.decision.PhantomStepContext(
			_profile.profileId(), root, plan, step, PhantomActivityState.ACTIVE, 1, 1, System.nanoTime(), 1, NOT_CANCELLED));
		final var stored = _profiles.findComponent(_profile.profileId(), "career.intent").orElseThrow(() -> new AssertionError("Production did not persist its selected career child."));
		final var intent = new org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateCodec().decode(stored.payload());
		PhantomAssertions.assertEquals("class.transfer", intent.goalType(), "Producer selected the wrong kind of intent.");
		PhantomAssertions.assertEquals("class.warrior-q401", intent.target().key(), "Producer selected unsupported content.");
		PhantomAssertions.assertEquals(root, _goals.load(_profile.profileId()).orElseThrow().goal(), "Career producer replaced the farm continuation.");
		PhantomAssertions.assertTrue(intent.status() == PhantomGoalStatus.ACTIVE && _player.getActiveClass() == 0 && itemCount(1145) == 0,
			"Producer bypassed native quest/transfer or rejected its bound farm child: " + result);
		final long exp = _player.getExp(), sp = _player.getSp();
		_careerAllowed.set(false);
		PhantomAssertions.assertTrue(selector.select(candidates.snapshot(), planning).candidate() == null && _service.ownsAutonomousIntent(_profile.profileId(), root)
			&& stored.equals(_profiles.findComponent(_profile.profileId(), "career.intent").orElseThrow()), "HOLD discarded or rewrote the durable career child.");
		PhantomAssertions.assertTrue(_service.permitsAutonomousVisibleDecision(_profile.profileId(), root), "HOLD lost the already-owned native career boundary.");
		_careerAllowed.set(true);
		final var replacement = new PhantomGoal(root.goalId(), root.goalType(), root.status(), root.subject(), root.target(), root.requiredAmount(), root.currentAmount(), root.acquisitionMethod(), root.validSources(), root.selectedAnchor(), root.purposeKey(), root.priority(), root.riskBudget(), root.expenseBudget(), root.deadlineEpochMillis(), root.constraints(), "career061.changed_root", root.revision() + 1);
		_goals.store(_profile.profileId(), replacement);
		PhantomAssertions.assertFalse(_service.permitsAutonomousVisibleDecision(_profile.profileId(), root), "Visible career gate accepted a replaced farm root.");
		PhantomAssertions.assertTrue(_service.permitsAutonomousVisibleDecision(_profile.profileId(), replacement), "A current revised farm root cannot reach a fresh eligible career Decision.");
		final var stale = handlers.snapshot().get(step.actionKey()).execute(new org.l2jmobius.gameserver.phantoms.decision.PhantomStepContext(_profile.profileId(), root, plan, step, PhantomActivityState.ACTIVE, 1, 2, System.nanoTime(), 1, NOT_CANCELLED));
		PhantomAssertions.assertTrue(stale.type() == org.l2jmobius.gameserver.phantoms.decision.PhantomStepResult.Type.REPLAN && _goals.load(_profile.profileId()).orElseThrow().goal().equals(replacement)
			&& _player.getActiveClass() == 0 && _player.getExp() == exp && _player.getSp() == sp && itemCount(1145) == 0, "Stale career callback changed native progress or the replacement root: " + stale);
		context.record("career061.guards", "HOLD preserved child; Tank/wrong native class/race/level refused; changed-root callback rejected without class/mark/EXP/SP mutation; not full Q401 proof");
		PhantomAssertions.assertTrue(_signals.submits > signalsBeforeActiveAdvance, "An ongoing ACTIVE native career did not refresh its bounded relevance signal before travelling outside locality.");
	}

	private void careerTravel061(PhantomTestContext context, boolean proveCorridor) throws Exception
	{
		preparePlayer(0, 0, 45);
		_player.addAdena(ItemProcessType.REWARD, 20_000, _player, false); // Finite new TEST travel input before measurement.
		final var content = _catalog.content("class.warrior-q401").orElseThrow();
		final var options = _backend.travelOptions(_profile.profileId(), content, 120_000);
		PhantomAssertions.assertTrue(options != null && options.legs().stream().anyMatch(leg -> leg.teleporterNpcId() == 30320 && leg.destinationIndex() == 0)
			&& options.legs().stream().anyMatch(leg -> leg.teleporterNpcId() == 30256 && leg.destinationIndex() == 8), "Native Q401 travel omits the required lawful Gludin/Gludio GK choices.");
		context.record("career061.travel", options);
		final var forward = options.legs().stream().filter(leg -> leg.teleporterNpcId() == 30320 && leg.destinationIndex() == 0).findFirst().orElseThrow();
		final var returning = options.legs().stream().filter(leg -> leg.teleporterNpcId() == 30256 && leg.destinationIndex() == 8).findFirst().orElseThrow();
		PhantomAssertions.assertTrue(org.l2jmobius.gameserver.phantoms.background.PhantomNormalGatekeeperTravel.observeNativeLeg(30320, -1, forward.sourceX(), forward.sourceY(), forward.sourceZ()).isEmpty(), "Invalid native destination index was accepted.");
		final var geo = org.l2jmobius.gameserver.geoengine.GeoEngine.getInstance();
		if (proveCorridor)
		{
			final var from = new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint(-81861, 149197, geo.getHeight(-81861, 149197, -3125), 0);
			final var source = new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint(forward.sourceX(), forward.sourceY(), forward.sourceZ(), 0);
			final var nativeNavigation = new org.l2jmobius.gameserver.phantoms.navigation.L2jNavigationBackend();
			final long walkNow = System.nanoTime();
			final var walk = nativeNavigation.findWalkCorridor(new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationRequest(_profile.profileId(), from, source, walkNow, walkNow + TimeUnit.SECONDS.toNanos(10), 100_000), new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationCancellationToken(), _navigation.policy(), System::nanoTime);
			PhantomAssertions.assertEquals(org.l2jmobius.gameserver.phantoms.navigation.PhantomWalkCorridorPlanner.Status.FOUND, walk.status(), "Free native GK approach is not proved.");
			context.record("career061.freeApproach", walk);
			final var step = content.step("simplon.begin").orElseThrow();
			final var factual = _backend.locate(_profile.profileId(), content, step);
			final var destinations = _backend.approaches(_profile.profileId(), content, step, factual, 200, _navigation.arrivalRadius());
			PhantomAssertions.assertTrue(!destinations.isEmpty() && destinations.size() <= 17, "Native legal Simplon approach is absent.");
			context.record("career061.legalApproaches", destinations);
			for (var to : destinations)
			{
				Thread.sleep(_navigation.policy().pathfindingCooldownMillis() + 50);
				final long now = System.nanoTime();
				final var submitted = _navigation.submitTravelPlan(new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationRequest(_profile.profileId(), source, to, now, now + TimeUnit.SECONDS.toNanos(10), 100_000), options);
				final var completed = new java.util.concurrent.atomic.AtomicReference<org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationResult>();
				await(() -> { _navigation.consume(submitted.requestId()).ifPresent(completed::set); return completed.get() != null; }, "Native remaining itinerary did not finish.");
				final var itinerary = _navigation.consumeTravelPlan(submitted.requestId()).orElse(null);
				if (itinerary == null) { context.record("career061.declined." + to.x() + "." + to.y(), completed.get()); continue; }
				PhantomAssertions.assertTrue(itinerary.stages().stream().anyMatch(stage -> stage.mode() == org.l2jmobius.gameserver.phantoms.navigation.PhantomTravelStrategy.Mode.GK)
					&& itinerary.stages().getFirst().from().equals(source) && itinerary.stages().getLast().to().equals(to), "Native remaining itinerary lost endpoints or its GK stage.");
				context.record("career061.remainingItinerary", itinerary);
				return;
			}
			throw new AssertionError("No complete remaining native GK itinerary reaches a legal Simplon approach.");
		}
		// Declared initial native TEST position. No repositioning/resources/quest changes between the measured two hops.
		_player.decayMe(); _player.setXYZInvisible(forward.sourceX(), forward.sourceY(), geo.getHeight(forward.sourceX(), forward.sourceY(), forward.sourceZ())); _player.spawnMe(); _player.revalidateZone(true);
		final var before = _backend.observe(_profile.profileId(), content);
		final long adena = _player.getAdena(), exp = _player.getExp(), sp = _player.getSp();
		final var stale = _backend.gatekeeper(_profile.profileId(), content, forward, "0".repeat(64));
		PhantomAssertions.assertEquals(ActionStatus.STALE, stale.status(), "Stale native travel callback was dispatched.");
		PhantomAssertions.assertEquals(adena, _player.getAdena(), "Stale callback charged native currency.");
		final long forwardFee = org.l2jmobius.gameserver.phantoms.background.PhantomNormalGatekeeperTravel.fee(forward, _player.getClassIndex(), _player.getLevel(), System.currentTimeMillis() / 60_000);
		final var first = _backend.gatekeeper(_profile.profileId(), content, forward, before.fingerprint());
		PhantomAssertions.assertEquals(ActionStatus.ISSUED, first.status(), "Native forward career GK did not arrive: " + first + ";" + _backend.travelEvidence(_profile.profileId()));
		context.record("career061.nativeForward", _backend.travelEvidence(_profile.profileId()));
		PhantomAssertions.assertEquals(adena - forwardFee, _player.getAdena(), "Forward native fee mismatch.");
		context.record("career061.afterForwardOwner", _materialization.find(_profile.profileId()) + ";sameWorldPlayer=" + (World.getInstance().getPlayer(_player.getObjectId()) == _player) + ";sameWorldObject=" + (World.getInstance().findObject(_player.getObjectId()) == _player));
		await(() -> _backend.observe(_profile.profileId(), content) != null, "Native teleport did not reopen the actual action admission.");
		final long duplicateAdena = _player.getAdena();
		final var duplicatePosition = org.l2jmobius.gameserver.phantoms.navigation.PhantomNativeTravelActions.point(_player);
		final var duplicate = _backend.gatekeeper(_profile.profileId(), content, forward, before.fingerprint());
		PhantomAssertions.assertTrue(Set.of(ActionStatus.STALE, ActionStatus.UNAVAILABLE).contains(duplicate.status()) && duplicateAdena == _player.getAdena()
			&& duplicatePosition.equals(org.l2jmobius.gameserver.phantoms.navigation.PhantomNativeTravelActions.point(_player)), "Duplicate old-source callback dispatched or charged twice: " + duplicate);
		context.record("career061.duplicateRefusal", duplicate);
		final var middle = _backend.observe(_profile.profileId(), content);
		final long returnFee = org.l2jmobius.gameserver.phantoms.background.PhantomNormalGatekeeperTravel.fee(returning, _player.getClassIndex(), _player.getLevel(), System.currentTimeMillis() / 60_000);
		final var second = _backend.gatekeeper(_profile.profileId(), content, returning, middle.fingerprint());
		PhantomAssertions.assertEquals(ActionStatus.ISSUED, second.status(), "Native return career GK did not arrive: " + second + ";" + _backend.travelEvidence(_profile.profileId()));
		context.record("career061.nativeReturn", _backend.travelEvidence(_profile.profileId()));
		PhantomAssertions.assertTrue(forwardFee > 0 && returnFee > 0 && _player.getAdena() == adena - forwardFee - returnFee, "Native paid bidirectional fee conservation failed.");
		final var after = _backend.observe(_profile.profileId(), content);
		PhantomAssertions.assertTrue(exp == _player.getExp() && sp == _player.getSp() && before.quest().equals(after.quest()) && before.itemCounts().equals(after.itemCounts()) && _player.getActiveClass() == 0, "Native travel changed career rewards/quest/class.");
	}

	private void careerNearestSpawn061(PhantomTestContext context) throws Exception
	{
		preparePlayer(0, 0, 19);
		_player.decayMe(); _player.setXYZInvisible(-15208, 123208, -3112); _player.spawnMe(); _player.revalidateZone(true); // New finite TEST input, not the retained ordinary actor.
		final var content = _catalog.content("class.warrior-q401").orElseThrow();
		final var step = content.step("collect.skeleton").orElseThrow();
		final var expected = step.npcIds().stream().flatMap(npcId -> _knowledge.query().snapshot().spawnFactsByNpc().getOrDefault(npcId, List.of()).stream())
			.filter(fact -> fact.instanceId() == 0 && fact.amount() > 0).min(Comparator.comparingDouble(fact -> _player.calculateDistance3D(fact.x(), fact.y(), fact.z()))).orElseThrow();
		final var actual = _backend.locate(_profile.profileId(), content, step);
		context.record("career061.nearestSpawn", "actual=" + actual + ";expected=" + expected);
		PhantomAssertions.assertTrue(actual != null && actual.distance() <= _player.calculateDistance3D(expected.x(), expected.y(), expected.z()) + 1, "Q401 chose an arbitrary early spawn page instead of its nearest supported native source.");
	}

	private void careerSkeletonRoute061(PhantomTestContext context) throws Exception
	{
		preparePlayer(0, 0, 19);
		_player.decayMe(); _player.setXYZInvisible(-12728, 122824, -3112); _player.spawnMe(); _player.revalidateZone(true); // Fixed independent TEST input matching the observed ordinary boundary.
		final var content = _catalog.content("class.warrior-q401").orElseThrow();
		final var step = content.step("collect.skeleton").orElseThrow();
		final var factual = _backend.locate(_profile.profileId(), content, step);
		final var destinations = _backend.approaches(_profile.profileId(), content, step, factual, 1000, _navigation.arrivalRadius());
		final var options = _backend.travelOptions(_profile.profileId(), content, 120_000);
		Thread.sleep(_navigation.policy().pathfindingCooldownMillis() + 50);
		final long now = System.nanoTime();
		final var from = new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint(_player.getX(), _player.getY(), _player.getZ(), 0);
		final var submitted = _navigation.submitTravelPlan(new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationRequest(_profile.profileId(), from, destinations.getFirst(), now, now + TimeUnit.SECONDS.toNanos(10), 100_000), options);
		final var completed = new java.util.concurrent.atomic.AtomicReference<org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationResult>();
		await(() -> { _navigation.consume(submitted.requestId()).ifPresent(completed::set); return completed.get() != null; }, "Gludio-skeleton itinerary did not finish.");
		context.record("career061.skeletonBoundary", "from=" + from + ";target=" + factual + ";result=" + completed.get());
		context.record("career061.skeletonOptions", options);
		context.record("career061.skeletonApproach", destinations);
		final var itinerary = _navigation.consumeTravelPlan(submitted.requestId()).orElse(null);
		context.record("career061.skeletonItinerary", itinerary);
		if (itinerary == null)
		{
			final var leg = options.legs().stream().filter(value -> value.teleporterNpcId() == 30256 && value.destinationIndex() == 15).findFirst().orElseThrow();
			final var geo = org.l2jmobius.gameserver.geoengine.GeoEngine.getInstance();
			final var landing = new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint(leg.destinationX(), leg.destinationY(), geo.getHeight(leg.destinationX(), leg.destinationY(), leg.destinationZ()) + 5, 0);
			final long probeNow = System.nanoTime();
			final var egress = new org.l2jmobius.gameserver.phantoms.navigation.L2jNavigationBackend().findWalkCorridor(new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationRequest(_profile.profileId(), landing, destinations.getFirst(), probeNow, probeNow + TimeUnit.SECONDS.toNanos(10), 100_000), new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationCancellationToken(), _navigation.policy(), System::nanoTime);
			context.record("career061.skeletonSeparateDiagnosticEgress", egress);
		}
		PhantomAssertions.assertTrue(itinerary == null && completed.get().status() == org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationResult.Status.NO_PATH, "The declared two-minute physical budget accepted a longer native itinerary.");
	}

	private void careerGoalTravelDeadline061(PhantomTestContext context) throws Exception
	{
		preparePlayer(0, 0, 19);
		_player.decayMe(); _player.setXYZInvisible(-12728, 122824, -3112); _player.spawnMe(); _player.revalidateZone(true);
		// Independent finite TEST boundary only; ordinary35 quest/resources are never changed here.
		final var quest = ScriptManager.getInstance().getQuest(401).getQuestState(_player, true);
		quest.startQuest(); quest.setCond(2, false);
		installGoal("class.warrior-q401", PhantomQuestInstanceService.CLASS_GOAL_TYPE);
		final var original = _goals.load(_profile.profileId()).orElseThrow().goal();
		final long deadline = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(90);
		final var goal = new PhantomGoal(original.goalId(), original.goalType(), original.status(), original.subject(), original.target(), original.requiredAmount(), original.currentAmount(), original.acquisitionMethod(), original.validSources(), original.selectedAnchor(), original.purposeKey(), original.priority(), original.riskBudget(), original.expenseBudget(), deadline, original.constraints(), original.reasonKey(), original.revision());
		_goals.store(_profile.profileId(), goal);
		final var supplied = new java.util.concurrent.atomic.AtomicLong(-1);
		final var backend = new org.l2jmobius.gameserver.phantoms.questinstance.PhantomQuestInstanceBackend()
		{
			public Observation observe(long id, Content content) { return _backend.observe(id, content); }
			public Target locate(long id, Content content, Step step) { return _backend.locate(id, content, step); }
			public ActionResult invoke(long id, Content content, Step step, String fingerprint) { return _backend.invoke(id, content, step, fingerprint); }
			public ActionResult move(long id, Content content, Step step, Target target) { return _backend.move(id, content, step, target); }
			public org.l2jmobius.gameserver.phantoms.navigation.PhantomTravelStrategy.Options travelOptions(long id, Content content, long remaining) { supplied.set(remaining); return _backend.travelOptions(id, content, remaining); }
			public List<org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint> approaches(long id, Content content, Step step, Target target, double distance, int radius) { return _backend.approaches(id, content, step, target, distance, radius); }
		};
		final var service = new PhantomQuestInstanceService(_goals, _catalog, _acquisitions, backend, _combat, _navigation, _progression, _signals);
		service.start();
		try
		{
			Thread.sleep(_navigation.policy().pathfindingCooldownMillis() + 50);
			final var result = service.advance(_profile.profileId(), goal.goalId(), goal.revision(), PhantomActivityState.ACTIVE, System.nanoTime(), NOT_CANCELLED);
			context.record("career061.goalTravelDeadline", "goalDeadline=" + deadline + ";suppliedRemaining=" + supplied.get() + ";result=" + result);
			PhantomAssertions.assertTrue(supplied.get() > TimeUnit.MINUTES.toMillis(80) && supplied.get() <= deadline - System.currentTimeMillis() + 1000, "Career passed its planner timeout instead of the existing goal's physical travel budget.");
			final var last = new java.util.concurrent.atomic.AtomicReference<>(result);
			await(() ->
			{
				if ("questinstance.navigation.ready".equals(last.get().reasonKey())) { return true; }
				last.set(service.advance(_profile.profileId(), goal.goalId(), goal.revision(), PhantomActivityState.ACTIVE, System.nanoTime(), NOT_CANCELLED));
				return "questinstance.navigation.ready".equals(last.get().reasonKey());
			}, "Existing career goal budget did not admit a complete native skeleton itinerary.");
			context.record("career061.goalTravelPlan", last.get());
		}
		finally
		{
			service.beginStop(); service.finishStop();
			await(() -> _navigation.snapshot().activeRequests() == 0, "Cancelled TEST route did not drain before fixture cleanup.");
		}
	}

	private void careerCombatLifetime061(PhantomTestContext context) throws Exception
	{
		preparePlayer(0, 0, 19); ensureWeapon(129); // Independent invulnerable TEST actor, not ordinary35 or full B.
		final var quest = ScriptManager.getInstance().getQuest(401).getQuestState(_player, true);
		quest.startQuest(); quest.setCond(2, false);
		final Monster target = spawnMonster(20035);
		spawnMonster(20035); spawnMonster(20035); // Two declared native targets for HOLD/root cancellation controls.
		final long beforeExp = _player.getExp();
		final PhantomGoal root = new PhantomGoal(++_goalId, "farm.background", PhantomGoalStatus.ACTIVE,
			new PhantomDomainRef("profile", Long.toString(_profile.profileId())), new PhantomDomainRef("npc", Integer.toString(_normalWorldCombatPoint.npcId())),
			1, 0, "background.farm", List.of(), null, "farm.background", 500, 0, 0, 0, Map.of(), "career061.combat_lifetime", 1);
		_goals.store(_profile.profileId(), root);
		final var candidates = new org.l2jmobius.gameserver.phantoms.decision.PhantomCandidateRegistry();
		final var handlers = new org.l2jmobius.gameserver.phantoms.decision.PhantomStepHandlerRegistry();
		final var decision = new org.l2jmobius.gameserver.phantoms.questinstance.PhantomQuestInstanceDecision(_service);
		decision.registerCandidates(candidates); candidates.seal(); decision.registerHandlers(handlers); handlers.seal();
		final var planning = new org.l2jmobius.gameserver.phantoms.decision.PhantomPlanningContext(_profile.profileId(), root,
			org.l2jmobius.gameserver.phantoms.decision.PhantomCapabilitySet.empty(), PhantomActivityState.ACTIVE, System.nanoTime(), 1);
		final var selected = new org.l2jmobius.gameserver.phantoms.decision.PhantomUtilitySelector().select(candidates.snapshot(), planning);
		PhantomAssertions.assertTrue(selected.candidate() != null, "Native career combat input has no production candidate.");
		final var plan = selected.candidate().planFactory().create(planning);
		final var step = plan.steps().getFirst();
		final var planCurrent = new java.util.concurrent.atomic.AtomicBoolean(true);
		final var result = handlers.snapshot().get(step.actionKey()).execute(new org.l2jmobius.gameserver.phantoms.decision.PhantomStepContext(
			_profile.profileId(), root, plan, step, PhantomActivityState.ACTIVE, 1, 1, System.nanoTime(), 1, () -> !planCurrent.get()));
		// Same normal lifetime rule as DecisionEngine: REPLAN discards this plan and cancels its token.
		if (result.type() == org.l2jmobius.gameserver.phantoms.decision.PhantomStepResult.Type.REPLAN) { planCurrent.set(false); }
		context.record("career061.childCombatLifetime", "result=" + result + ";planCurrent=" + planCurrent.get() + ";combat=" + _combat.find(_profile.profileId()));
		PhantomAssertions.assertEquals("questinstance.combat.started", result.reasonKey(), "Native child Combat did not actually start.");
		await(target::isDead, "Production child Combat did not finish its real native target without test damage/pulses.");
		await(() -> _player.getExp() > beforeExp, "Native child Combat has no actual earned reward.");
		context.record("career061.childCombatReward", "npc20035 native dead; EXP=" + beforeExp + "->" + _player.getExp() + ";quest1140=" + itemCount(1140));
		for (int guard = 0; guard < 2; guard++)
		{
			boolean started = false;
			org.l2jmobius.gameserver.phantoms.decision.PhantomStepResult last = null;
			for (int attempt = 0; attempt < 40 && !started; attempt++)
			{
				final var nextPlan = selected.candidate().planFactory().create(planning);
				final var current = new java.util.concurrent.atomic.AtomicBoolean(true);
				final var advanced = handlers.snapshot().get(step.actionKey()).execute(new org.l2jmobius.gameserver.phantoms.decision.PhantomStepContext(
					_profile.profileId(), root, nextPlan, nextPlan.steps().getFirst(), PhantomActivityState.ACTIVE, 1, attempt + 2, System.nanoTime(), 1, () -> !current.get()));
				if (advanced.type() == org.l2jmobius.gameserver.phantoms.decision.PhantomStepResult.Type.REPLAN) { current.set(false); }
				started = "questinstance.combat.started".equals(advanced.reasonKey());
				last = advanced;
				if (!started) { Thread.sleep(500); } // Same ordinary retry cadence; await native settlement, never pulse Combat.
			}
			context.record("career061.childCombatGuardStart." + guard, last);
			PhantomAssertions.assertTrue(started, "Negative child lifetime control did not start its own native Combat: " + last);
			if (guard == 0) { _careerAllowed.set(false); }
			else { _goals.store(_profile.profileId(), new PhantomGoal(root.goalId(), root.goalType(), root.status(), root.subject(), root.target(), root.requiredAmount(), root.currentAmount(), root.acquisitionMethod(), root.validSources(), root.selectedAnchor(), root.purposeKey(), root.priority(), root.riskBudget(), root.expenseBudget(), root.deadlineEpochMillis(), root.constraints(), "career061.replaced_root", root.revision() + 1)); }
			try { await(() -> _combat.find(_profile.profileId()).map(value -> value.result() == org.l2jmobius.gameserver.phantoms.combat.PhantomCombatResult.CANCELLED).orElse(false), "HOLD/replaced root did not cancel its native child Combat."); }
			finally { _careerAllowed.set(true); }
		}
		context.record("career061.childCombatGuards", "ordinary plan replan survived to actual native reward; HOLD and replaced durable root each cancelled their own subsequent native session");
	}

	private void careerClassStore061(PhantomTestContext context) throws Exception
	{
		rematerialize(); // The independent native store fixture must not inherit the preceding combat's flags.
		preparePlayer(0, 0, 20); ensureWeapon(129); _player.rewardSkills();
		final var content = _catalog.content("class.warrior-q401").orElseThrow();
		try (var production = PhantomBackgroundSuite.ProductionAuthorityFixture.start())
		{
			final var anchor = production.topology().findAnchor("generated.farm.bfb0335de417bc81a7e9b231.anchor").orElseThrow();
			_player.decayMe(); _player.setXYZInvisible(anchor.point().x(), anchor.point().y(), anchor.point().z()); _player.spawnMe(); _player.revalidateZone(true);
			// Declared secondary native protocol fixture only: never evidence for ordinary full Q401/B.
			final var quest = ScriptManager.getInstance().getQuest(401).getQuestState(_player, true);
			quest.exitQuest(false, true);
			_player.getInventory().addItem(ItemProcessType.REWARD, 1145, 1, _player, this);
			spawnNpc(30373);
			final long id = _profile.profileId();
			final var root = new PhantomGoal(++_goalId, "farm.background", PhantomGoalStatus.ACTIVE, new PhantomDomainRef("profile", Long.toString(id)), new PhantomDomainRef("npc", "20120"), 1, 0, "background.farm", List.of(new PhantomDomainRef(org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundGoalSpec.SOURCE_NAMESPACE, "20120@" + anchor.id())), new PhantomDomainRef("topology.anchor", anchor.id()), "farm.background", 500, 0, 0, 0, Map.of(), "career061.native_store_fixture", 1);
			final var durableGoals = new org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateStore(_profiles);
			durableGoals.insert(id, root);
			final var child = new PhantomGoal(root.goalId(), "class.transfer", PhantomGoalStatus.ACTIVE, root.subject(), new PhantomDomainRef("supported.content", content.id()), 1, 0, null, List.of(), root.selectedAnchor(), "career.autonomous", 700, 0, 0, System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(90), Map.of("owner.object", (long) _player.getObjectId(), "owner.revision", root.revision(), "class.from", 0L, "class.to", 1L, "class.index", 0L, "weapon.object", (long) _player.getActiveWeaponInstance().getObjectId()), "career061.native_store_fixture", 1, content.contentHash());
			final var oldChild = _profiles.findComponent(id, "career.intent").orElse(null);
			final byte[] childBytes = new org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateCodec().encode(child);
			if (oldChild == null) { _profiles.insertComponent(id, "career.intent", 1, childBytes); }
			else { _profiles.updateComponent(id, "career.intent", oldChild.rowVersion(), 1, childBytes); }
			_player.getInventory().updateDatabase(); _player.storeMe();
			final var transactions = new org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundTransaction();
			final var before = production.authority().captureOwnedNative(id, _player, root, null);
			final var baseline = transactions.captureBaseline(before.state(), root, before.context());
			PhantomAssertions.assertTrue(baseline.successful(), "Native class-store fixture baseline failed: " + baseline.status());
			final long sp = _player.getSp(), exp = _player.getExp(), coupons = itemCount(8869);
			final var observed = _backend.observe(id, content);
			final var changed = _backend.invoke(id, content, content.step("profession.warrior").orElseThrow(), observed.fingerprint());
			PhantomAssertions.assertTrue(_player.getActiveClass() == 1 && _player.getBaseClass() == 1 && itemCount(1145) == 0 && itemCount(8869) == coupons + 15, "Native master did not execute the declared class-store fixture: " + changed);
			_player.getInventory().updateDatabase();
			final var after = production.authority().captureOwnedNative(id, _player, root, baseline.state());
			final long epoch = _materialization.find(id).orElseThrow().materializedAtNanos();
			PhantomAssertions.assertEquals(org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundTransaction.Status.STATE_CONFLICT, transactions.prepareOwnedStore(after.state(), root, epoch, org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.State.READY).status(), "An unattested class change was accepted.");
			final var foreignConstraints = new java.util.LinkedHashMap<>(child.constraints());
			foreignConstraints.put("owner.revision", root.revision() + 1);
			final var foreignChild = new PhantomGoal(child.goalId(), child.goalType(), child.status(), child.subject(), child.target(), child.requiredAmount(), child.currentAmount(), child.acquisitionMethod(), child.validSources(), child.selectedAnchor(), child.purposeKey(), child.priority(), child.riskBudget(), child.expenseBudget(), child.deadlineEpochMillis(), foreignConstraints, child.reasonKey(), child.revision(), child.payloadText());
			var childRow = _profiles.findComponent(id, "career.intent").orElseThrow();
			_profiles.updateComponent(id, "career.intent", childRow.rowVersion(), 1, new org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateCodec().encode(foreignChild));
			final var rejected = transactions.prepareOwnedStore(after.state(), root, epoch, org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.State.READY, after.context());
			PhantomAssertions.assertEquals(org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundTransaction.Status.STATE_CONFLICT, rejected.status(), "An unrelated career root authorized the class store.");
			childRow = _profiles.findComponent(id, "career.intent").orElseThrow();
			_profiles.updateComponent(id, "career.intent", childRow.rowVersion(), 1, childBytes);
			final var prepared = transactions.prepareOwnedStore(after.state(), root, epoch, org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.State.READY, after.context());
			context.record("career061.classStorePrepare", "before=" + before.state().identity() + ";after=" + after.state().identity() + ";status=" + prepared.status());
			PhantomAssertions.assertTrue(prepared.successful(), "Owned persistence rejected the actual native Fighter-to-Warrior class change: " + prepared.status());
			final var decoded = org.l2jmobius.gameserver.phantoms.background.PhantomOwnedStoreIntent.decode(prepared.intent().encode());
			PhantomAssertions.assertTrue(decoded.before().identity().activeClassId() == 0 && decoded.after().identity().activeClassId() == 1, "Class transition receipt lost either native identity.");
			for (var wrong : List.of(new org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.Identity(id, _player.getObjectId(), 0, 2, 0), new org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.Identity(id, _player.getObjectId(), 0, 1, 1), new org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.Identity(id, _player.getObjectId(), 1, 1, 0)))
			{
				final var state = after.state();
				final var unrelated = new org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState(state.state(), wrong, state.progress(), state.vitals(), state.position(), state.combat(), state.loadout(), state.inventory(), state.autoGetSkills(), state.clock(), state.receipt(), state.hashes());
				PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> new org.l2jmobius.gameserver.phantoms.background.PhantomOwnedStoreIntent(decoded.preparedRowVersion(), decoded.materializedAtNanos(), decoded.targetState(), decoded.previousState(), decoded.previousPayloadHash(), decoded.skillsHash(), decoded.before(), unrelated), "Unrelated class/race/index accepted by receipt.");
			}
			_player.storeMe();
			final var finalized = transactions.finalizeOwnedStore(id, _player.getObjectId(), epoch);
			PhantomAssertions.assertTrue(finalized.successful() && finalized.state().identity().activeClassId() == 1, "Native Warrior store did not finalize: " + finalized.status());
			PhantomAssertions.assertTrue(_player.getSp() == sp && _player.getExp() == exp && itemCount(8869) == coupons + 15, "Owned class store duplicated or changed native rewards/SP/EXP.");
			rematerialize();
			PhantomAssertions.assertTrue(_player.getActiveClass() == 1 && _player.getBaseClass() == 1 && _player.getSp() == sp && _player.getExp() == exp && itemCount(1145) == 0 && itemCount(8869) == coupons + 15, "SameDB native Warrior reload lost or duplicated earned state.");
			context.record("career061.classStore", "secondary fixture nativeMaster->ownedprepare/nativeStore/finalize->sameDBreload; class1/base1;15coupons once; EXP/SP conserved; not ordinary B");
		}
	}

	private void careerFinishCheckpoint061(PhantomTestContext context) throws Exception
	{
		careerFinishCheckpoint061(context, false);
	}

	private void careerFinishCheckpoint061(PhantomTestContext context, boolean missingWeapon) throws Exception
	{
		rematerialize(); // Independent finish input after the real native master/store result in16.
		final long id = _profile.profileId();
		final var root = new org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateStore(_profiles).load(id).orElseThrow().goal();
		final var original = _profiles.findComponent(id, "career.intent").orElseThrow();
		_goals.store(id, root);
		PhantomAssertions.assertTrue(_player.getActiveClass() == 1 && _player.getQuestState("Q00401_PathOfTheWarrior").isCompleted(), "Native finish input is not the earned Warrior result.");
		final long originalWeapon = new org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateCodec().decode(original.payload()).constraints().getOrDefault("weapon.object", 0L);
		PhantomAssertions.assertTrue(originalWeapon > 0 && _player.getInventory().getItemByObjectId((int) originalWeapon) != null, "Finish input lost its original owned weapon.");
		if (missingWeapon)
		{
			final var codec = new org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateCodec(); final var child = codec.decode(original.payload());
			final var constraints = new java.util.LinkedHashMap<>(child.constraints()); constraints.put("weapon.object", (long) Integer.MAX_VALUE);
			final var missing = new PhantomGoal(child.goalId(), child.goalType(), child.status(), child.subject(), child.target(), child.requiredAmount(), child.currentAmount(), child.acquisitionMethod(), child.validSources(), child.selectedAnchor(), child.purposeKey(), child.priority(), child.riskBudget(), child.expenseBudget(), child.deadlineEpochMillis(), constraints, child.reasonKey(), child.revision(), child.payloadText());
			_profiles.updateComponent(id, "career.intent", original.rowVersion(), 1, codec.encode(missing));
		}
		_player.getInventory().unEquipItemInSlot(org.l2jmobius.gameserver.model.itemcontainer.Inventory.PAPERDOLL_RHAND);
		// Stock ClassMaster changes active/base class without resetting this transient trainer context.
		_player.setLearningClass(org.l2jmobius.gameserver.model.actor.enums.player.PlayerClass.getPlayerClass(0));
		PhantomAssertions.assertTrue(_player.getActiveWeaponInstance() == null, "Declared post-trial native finish input is not unarmed.");
		final var candidates = new org.l2jmobius.gameserver.phantoms.decision.PhantomCandidateRegistry();
		final var handlers = new org.l2jmobius.gameserver.phantoms.decision.PhantomStepHandlerRegistry();
		final var decision = new org.l2jmobius.gameserver.phantoms.questinstance.PhantomQuestInstanceDecision(_service);
		decision.registerCandidates(candidates); candidates.seal(); decision.registerHandlers(handlers); handlers.seal();
		final var planning = new org.l2jmobius.gameserver.phantoms.decision.PhantomPlanningContext(id, root, org.l2jmobius.gameserver.phantoms.decision.PhantomCapabilitySet.empty(), PhantomActivityState.ACTIVE, System.nanoTime(), 1);
		final var selected = new org.l2jmobius.gameserver.phantoms.decision.PhantomUtilitySelector().select(candidates.snapshot(), planning);
		PhantomAssertions.assertTrue(selected.candidate() != null, "Native earned class has no production finish candidate.");
		final var plan = selected.candidate().planFactory().create(planning);
		final var step = plan.steps().getFirst();
		final var stores = new java.util.concurrent.atomic.AtomicInteger();
		final long exp = _player.getExp(), sp = _player.getSp();
		try (var boundary = _player.attachOwnedStoreBoundary(new Player.OwnedStoreBoundary()
		{
			@Override public Player.OwnedStoreSnapshot beforeStore() { stores.incrementAndGet(); return null; }
			@Override public void afterStore(boolean completed) { PhantomAssertions.assertTrue(completed, "Native finish store did not complete."); }
		}))
		{
			final var result = handlers.snapshot().get(step.actionKey()).execute(new org.l2jmobius.gameserver.phantoms.decision.PhantomStepContext(id, root, plan, step, PhantomActivityState.ACTIVE, 1, 1, System.nanoTime(), 1, () -> false));
			context.record("career061.finishCheckpoint", "actualProductionHandler=" + result + ";nativeStores=" + stores.get());
			PhantomAssertions.assertEquals(_player.getActiveClass(), _player.getLearningClass().getId(), "Native earned-class checkpoint retained the old trainer class and blocks post-career learning/return.");
			PhantomAssertions.assertTrue(result.type() == (missingWeapon ? org.l2jmobius.gameserver.phantoms.decision.PhantomStepResult.Type.REPLAN : org.l2jmobius.gameserver.phantoms.decision.PhantomStepResult.Type.SUCCESS) && stores.get() == 1, "Production class finish cannot preserve its own native checkpoint/equip refusal.");
			PhantomAssertions.assertTrue(_player.getExp() == exp && _player.getSp() == sp, "Native finish changed earned EXP/SP.");
			PhantomAssertions.assertTrue(missingWeapon ? _player.getActiveWeaponInstance() == null : _player.getActiveWeaponInstance() != null && _player.getActiveWeaponInstance().getObjectId() == originalWeapon, "Production finish did not preserve the exact original-weapon/native-refusal boundary.");
			if (missingWeapon)
			{
				final var failed = new org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateCodec().decode(_profiles.findComponent(id, "career.intent").orElseThrow().payload());
				PhantomAssertions.assertTrue(failed.status() == PhantomGoalStatus.FAILED && failed.reasonKey().equals("career.native.return_weapon_rejected.item_not_owned") && _player.getInventory().getItemByObjectId((int) originalWeapon) != null, "Missing original weapon was fabricated or left in an endless retry.");
			}
		}
		finally
		{
			final var row = _profiles.findComponent(id, "career.intent").orElseThrow(); _profiles.updateComponent(id, "career.intent", row.rowVersion(), 1, original.payload()); // Next independent cold fixture retains its declared ACTIVE child.
		}
	}

	private void careerColdResume061(PhantomTestContext context) throws Exception
	{
		rematerialize();
		final long id = _profile.profileId();
		final var root = new org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateStore(_profiles).load(id).orElseThrow().goal();
		final var component = _profiles.findComponent(id, "career.intent").orElseThrow();
		final var codec = new org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateCodec();
		final var child = codec.decode(component.payload());
		_goals.store(id, root);
		PhantomAssertions.assertTrue(_player.getQuestState("Q00401_PathOfTheWarrior").isCompleted(), "Cold accepted fixture must have real native quest progress, not a fresh proposal.");
		PhantomAssertions.assertEquals(ResultStatus.SUCCESS, _materialization.dematerialize(id).status(), "Cold accepted fixture did not save its existing native progress.");
		try
		{
			final int before = _signals.submits;
			final long now = System.currentTimeMillis();
			final var disabledSignals = new RecordingSignals();
			final var disabled = new PhantomQuestInstanceService(_goals, _catalog, _acquisitions, _backend, _combat, _navigation, _progression, disabledSignals);
			disabled.enableAutonomous(_profiles, _materialization, (_, _) -> true, _ -> true, _ -> org.l2jmobius.gameserver.phantoms.population.PhantomPopulationCatalog.CareerArchetype.DAMAGE,
				new org.l2jmobius.gameserver.phantoms.questinstance.PhantomAutonomousCareerPolicy(false, 30, 90, 128, _careerPolicy.routes()));
			PhantomAssertions.assertTrue(disabled.start(), "Disabled policy control did not start.");
			try { disabled.resumeAcceptedIntents(now); PhantomAssertions.assertEquals(0, disabledSignals.submits, "Disabled career policy resumed a Player."); }
			finally { disabled.beginStop(); disabled.finishStop(); }
			PhantomAssertions.assertTrue(_service.ownsAutonomousIntent(id, root), "Accepted career authority disappeared solely because its native Player was saved.");
			_service.resumeAcceptedIntents(now);
			PhantomAssertions.assertEquals(before + 1, _signals.submits, "An existing bound native quest journey lost its ACTIVE relevance after sameDB cold reload.");
			_goals.store(id, root.withStatus(PhantomGoalStatus.ACTIVE)); // Revision changes; existing child cannot own it.
			_service.resumeAcceptedIntents(now + 30_001);
			PhantomAssertions.assertEquals(before + 1, _signals.submits, "Cold resume woke a replaced root.");
			_goals.store(id, root);
			final var expired = new PhantomGoal(child.goalId(), child.goalType(), child.status(), child.subject(), child.target(), child.requiredAmount(), child.currentAmount(), child.acquisitionMethod(), child.validSources(), child.selectedAnchor(), child.purposeKey(), child.priority(), child.riskBudget(), child.expenseBudget(), now - 1, child.constraints(), child.reasonKey(), child.revision(), child.payloadText());
			var row = _profiles.findComponent(id, "career.intent").orElseThrow(); _profiles.updateComponent(id, "career.intent", row.rowVersion(), 1, codec.encode(expired));
			_service.resumeAcceptedIntents(now + 60_002);
			PhantomAssertions.assertEquals(before + 1, _signals.submits, "Cold resume woke an expired journey.");
			row = _profiles.findComponent(id, "career.intent").orElseThrow(); _profiles.updateComponent(id, "career.intent", row.rowVersion(), 1, component.payload());
			PhantomAssertions.assertEquals(ResultStatus.SUCCESS, _materialization.materialize(id).status(), "Independent ABSENT-quest control could not restore its native body.");
			_player = World.getInstance().getPlayer(_environment.primary().objectId());
			_player.getQuestState("Q00401_PathOfTheWarrior").setState(org.l2jmobius.gameserver.model.script.State.STARTED); // Independent negative fixture: exitQuest ignores COMPLETED states.
			_player.getQuestState("Q00401_PathOfTheWarrior").exitQuest(true, true);
			PhantomAssertions.assertTrue(_player.getQuestState("Q00401_PathOfTheWarrior") == null, "ABSENT control still has a native QuestState.");
			PhantomAssertions.assertEquals(ResultStatus.SUCCESS, _materialization.dematerialize(id).status(), "Independent ABSENT-quest control did not save.");
			_service.resumeAcceptedIntents(now + 90_003);
			PhantomAssertions.assertEquals(before + 1, _signals.submits, "A fresh ABSENT quest bypassed ordinary calendar sleep.");
			context.record("career061.coldResume", "existing native Q401 completion/current ACTIVE child/root signal restored without Player or new proposal; replaced root/expired refused; no calendar mutation");
		}
		finally
		{
			_goals.store(id, root);
			final var row = _profiles.findComponent(id, "career.intent").orElseThrow(); _profiles.updateComponent(id, "career.intent", row.rowVersion(), 1, component.payload());
			if (_materialization.find(id).isEmpty()) { PhantomAssertions.assertEquals(ResultStatus.SUCCESS, _materialization.materialize(id).status(), "Independent native fixture did not restore its saved body."); }
			_player = World.getInstance().getPlayer(_environment.primary().objectId());
		}
	}

	private void careerCombatStop061(PhantomTestContext context) throws Exception
	{
		rematerialize(); preparePlayer(0, 0, 20); ensureWeapon(129);
		final Monster first = spawnMonster(20035);
		final long before = _player.getExp();
		final long id = _profile.profileId();
		final var owner = (org.l2jmobius.gameserver.phantoms.player.PhantomNativeWorkScope) _player.getNativeWorkOwner();
		final var started = _combat.startContentSession(new org.l2jmobius.gameserver.phantoms.combat.PhantomCombatRequest(id, first.getObjectId(), org.l2jmobius.gameserver.phantoms.combat.PhantomCombatMode.MELEE_PHYSICAL, true, false, 120_000, () -> false), "career061.stop.fixture");
		PhantomAssertions.assertEquals(PhantomCombatService.StartStatus.ACCEPTED, started.status(), "Native stop fixture did not start actual Combat.");
		try { await(() -> first.isDead() && _player.getExp() > before, "Native stop fixture has no earned kill/reward."); }
		finally
		{
			context.record("career061.stopCombatObservation", "EXP=" + before + "->" + _player.getExp() + ";actorDead=" + _player.isAlikeDead() + ";sitting=" + _player.isSitting() + ";hp=" + _player.getCurrentHp() + ";nativeIntention=" + _player.getAI().getIntention() + ";targetHp=" + first.getCurrentHp() + ";targetDead=" + first.isDead() + ";targetDistance=" + _player.calculateDistance3D(first) + ";combat=" + _combat.find(id));
		}
		context.record("career061.normalCombatStopBefore", "earned=" + before + "->" + _player.getExp() + ";combat=" + _combat.find(id) + ";native=" + owner.snapshot());
		_service.beginStop(); _combat.beginStop(); _navigation.beginStop();
		try { await(_combat::finishStop, "Shared Combat did not stop its actual native action."); }
		finally { context.record("career061.normalCombatStopSettlement", "combat=" + _combat.find(id) + ";native=" + owner.snapshot()); }
		final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
		org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService.ShutdownResult result;
		do { result = _materialization.requestShutdown(deadline); if (result.state() != PhantomMaterializationService.ServiceState.STOPPING) { break; } Thread.sleep(10); } while (System.nanoTime() < deadline);
		context.record("career061.normalCombatStop", "result=" + result + ";blockers=" + _materialization.cleanupBlockers() + ";native=" + owner.snapshot());
		PhantomAssertions.assertEquals(PhantomMaterializationService.ServiceState.STOPPED, result.state(), "Normal native combat Stop did not drain/save its earned result.");
		PhantomAssertions.assertEquals(_player.getExp(), scalar("SELECT exp FROM characters WHERE charId=?", _player.getObjectId()), "Normal Stop lost actual earned EXP.");
	}

	private void strictCatalog(PhantomTestContext context) throws Exception
	{
		Files.createDirectories(context.reportsDirectory());
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
		PhantomAssertions.assertEquals(1L, itemCount(964), "Q102 Alberius letter control token multiplied.");
		restartService();
		spawnNpc(30156);
		advanceUntil(() -> cond(102) == 2, 8, "Q102 Cobendell transition did not reach cond 2.");
		killUntil(102, 3, 20013, 80, "Q102 Dryad collection");
		PhantomAssertions.assertEquals(10L, itemCount(966), "Q102 objective amount did not clamp to its native cap.");
		restartService();
		advanceUntil(() -> cond(102) == 4, 8, "Q102 collection hand-in did not reach cond 4.");
		advanceUntil(() -> cond(102) == 5, 8, "Q102 Alberius report did not reach cond 5.");
		spawnNpc(30217);
		spawnNpc(30219);
		spawnNpc(30221);
		spawnNpc(30285);
		advanceUntil(() -> cond(102) == 6, 16, "Q102 sentinel deliveries did not reach cond 6.");
		final long expBefore = _player.getExp();
		final long spBefore = _player.getSp();
		final long adenaBefore = itemCount(57);
		advanceUntil(() -> questState(102).isCompleted(), 8, "Q102 native terminal hand-in did not complete.");
		final long rewardAfter = itemCount(1060);
		final long itemRate = _mode == Mode.GOAL036 ? 1 : 5;
		PhantomAssertions.assertEquals(100L * itemRate, rewardAfter - rewardBefore, "Q102 native Lesser Healing Potion reward drifted.");
		PhantomAssertions.assertEquals(30202L * (_mode == Mode.GOAL036 ? 1 : 2), _player.getExp() - expBefore, "Q102 quest XP rate drifted.");
		PhantomAssertions.assertEquals(1339L * (_mode == Mode.GOAL036 ? 1 : 3), _player.getSp() - spBefore, "Q102 quest SP rate drifted.");
		PhantomAssertions.assertEquals(6331L * (_mode == Mode.GOAL036 ? 1 : 4), itemCount(57) - adenaBefore, "Q102 quest Adena rate drifted.");
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
		PhantomAssertions.assertEquals(1L, itemCount(1008), "Q152 receipt control token multiplied.");
		advanceUntil(() -> cond(152) == 2, 8, "Q152 Altran transition did not reach cond 2.");
		killUntil(152, 3, 20016, 60, "Q152 Stone Golem collection");
		PhantomAssertions.assertEquals(5L, itemCount(1010), "Q152 objective amount did not clamp to its native cap.");
		restartService();
		advanceUntil(() -> cond(152) == 4, 8, "Q152 Altran hand-in did not reach cond 4.");
		advanceUntil(() -> questState(152).isCompleted(), 8, "Q152 native Harris terminal did not complete.");
		final long rewardAfter = itemCount(23);
		PhantomAssertions.assertEquals(_mode == Mode.GOAL036 ? 1L : 5L, rewardAfter - rewardBefore, "Q152 native Wooden Breastplate reward drifted.");
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
		PhantomAssertions.assertEquals(1L, itemCount(13034), "Pailaka sword control token multiplied.");
		PhantomAssertions.assertEquals(1L, itemCount(13130), "Pailaka book control token multiplied.");

		pailakaCombat(18610, 3, "Hillas");
		PhantomAssertions.assertEquals(1L, itemCount(13131), "Pailaka Hillas book control token multiplied.");
		PhantomAssertions.assertEquals(1L, itemCount(13038), "Pailaka water essence control token multiplied.");
		relocate(instanceNpc(32507));
		advanceUntil(() -> cond(128) == 4, 8, "Pailaka water inspector transition did not reach cond 4.");
		PhantomAssertions.assertEquals(1L, itemCount(13132), "Pailaka water inspector book control token multiplied.");
		PhantomAssertions.assertEquals(1L, itemCount(13035), "Pailaka first enhanced sword control token multiplied.");
		pailakaCombat(18609, 5, "Papion");
		PhantomAssertions.assertEquals(1L, itemCount(13133), "Pailaka Papion book control token multiplied.");
		pailakaCombat(18608, 6, "Kinsus");
		PhantomAssertions.assertEquals(1L, itemCount(13134), "Pailaka Kinsus book control token multiplied.");
		PhantomAssertions.assertEquals(1L, itemCount(13039), "Pailaka fire essence control token multiplied.");
		relocate(instanceNpc(32507));
		advanceUntil(() -> cond(128) == 7, 8, "Pailaka fire inspector transition did not reach cond 7.");
		PhantomAssertions.assertEquals(1L, itemCount(13135), "Pailaka fire inspector book control token multiplied.");
		PhantomAssertions.assertEquals(1L, itemCount(13036), "Pailaka second enhanced sword control token multiplied.");
		pailakaCombat(18607, 8, "Gargos");
		PhantomAssertions.assertEquals(1L, itemCount(13136), "Pailaka Gargos book control token multiplied.");
		pailakaCombat(18620, 9, "Adiantum");
		relocate(instanceNpc(32510));
		final Map<Integer, Long> rewardsBefore = Map.of(13294, itemCount(13294), 13293, itemCount(13293), 736, itemCount(736));
		advanceUntil(() -> questState(128).isCompleted(), 8, "Pailaka Adler terminal did not complete Q128.");
		for (int itemId : rewardsBefore.keySet())
		{
			PhantomAssertions.assertEquals(_mode == Mode.GOAL036 ? 1L : 5L, itemCount(itemId) - rewardsBefore.get(itemId), "Pailaka native reward drifted: " + itemId);
		}
		final long remaining = instance.getInstanceEndTime() - System.currentTimeMillis();
		PhantomAssertions.assertTrue((remaining > TimeUnit.MINUTES.toMillis(4)) && (remaining <= TimeUnit.MINUTES.toMillis(5) + 5000), "Pailaka native EXIT_TIME was not observed.");
		restartService();
		PhantomAssertions.assertEquals(Status.COMPLETE, advance(PhantomActivityState.ACTIVE).status(), "Completed Pailaka was not idempotent after restart.");
		for (int itemId : rewardsBefore.keySet())
		{
			PhantomAssertions.assertEquals(rewardsBefore.get(itemId) + (_mode == Mode.GOAL036 ? 1 : 5), itemCount(itemId), "Pailaka retry duplicated reward: " + itemId);
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
		if (_careerPolicy != null) { service.enableAutonomous(_profiles, _materialization, (_, _) -> true, _ -> _careerAllowed.get(), _ -> _careerPreference.get(), _careerPolicy); }
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
		final var knowledge = _knowledge.query().snapshot();
		final var dangerous = knowledge.npcById().values().stream().filter(fact -> fact.kind() == NpcKind.MONSTER && fact.attackable() && fact.level() > 25)
			.flatMap(fact -> knowledge.spawnFactsByNpc().getOrDefault(fact.npcId(), List.of()).stream()).filter(fact -> fact.instanceId() == 0).toList();
		return _knowledge.query().snapshot().npcById().values().stream()
			.filter(fact -> (fact.kind() == NpcKind.MONSTER) && fact.attackable() && fact.targetable() && !reservedTargets.contains(fact.npcId()))
			.sorted(Comparator.comparingInt(NpcFact::level).thenComparingInt(NpcFact::npcId))
			.flatMap(fact -> _knowledge.query().snapshot().spawnFactsByNpc().getOrDefault(fact.npcId(), List.of()).stream())
			.filter(fact -> (fact.pointKind() == SpawnPointKind.EXACT) && (fact.instanceId() == 0))
			.filter(fact -> dangerous.stream().noneMatch(other -> Math.abs((long) other.z() - fact.z()) < 256 && Math.hypot((long) other.x() - fact.x(), (long) other.y() - fact.y()) < 3000))
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
			if (_rateSnapshot != null)
			{
				_rateSnapshot.restore();
				_rateSnapshot = null;
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

	private record RateSnapshot(float questItem, float reward, float xp, float sp, float adena, boolean useMultipliers)
	{
		static RateSnapshot capture()
		{
			return new RateSnapshot(RatesConfig.QUEST_ITEM_DROP_AMOUNT_MULTIPLIER, RatesConfig.RATE_QUEST_REWARD, RatesConfig.RATE_QUEST_REWARD_XP, RatesConfig.RATE_QUEST_REWARD_SP, RatesConfig.RATE_QUEST_REWARD_ADENA, RatesConfig.RATE_QUEST_REWARD_USE_MULTIPLIERS);
		}

		void restore()
		{
			RatesConfig.QUEST_ITEM_DROP_AMOUNT_MULTIPLIER = questItem;
			RatesConfig.RATE_QUEST_REWARD = reward;
			RatesConfig.RATE_QUEST_REWARD_XP = xp;
			RatesConfig.RATE_QUEST_REWARD_SP = sp;
			RatesConfig.RATE_QUEST_REWARD_ADENA = adena;
			RatesConfig.RATE_QUEST_REWARD_USE_MULTIPLIERS = useMultipliers;
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
