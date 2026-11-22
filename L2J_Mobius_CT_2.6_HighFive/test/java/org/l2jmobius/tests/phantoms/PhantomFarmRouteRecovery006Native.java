/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import org.l2jmobius.gameserver.ai.Intention;
import org.l2jmobius.gameserver.config.GeneralConfig;
import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.handler.TargetHandler;
import org.l2jmobius.gameserver.managers.ZoneManager;
import org.l2jmobius.gameserver.model.Location;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.enums.player.PlayerClass;
import org.l2jmobius.gameserver.model.actor.instance.Monster;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.model.skill.targets.TargetType;
import org.l2jmobius.gameserver.model.stats.Stat;
import org.l2jmobius.gameserver.model.zone.ZoneId;
import org.l2jmobius.gameserver.model.zone.type.CastleZone;
import org.l2jmobius.gameserver.model.zone.type.WaterZone;
import org.l2jmobius.gameserver.phantoms.activity.PhantomRelevanceSignal;
import org.l2jmobius.gameserver.phantoms.background.L2jPhantomBackgroundAuthority;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundCompetitionRegistry;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundCatchupState;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundCatchupStore;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundDecision;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundGoalSpec;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundService;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundTransaction;
import org.l2jmobius.gameserver.phantoms.background.PhantomFarmRouteRecoveryReturnTest;
import org.l2jmobius.gameserver.phantoms.background.PhantomHistoricalBackgroundPlanner;
import org.l2jmobius.gameserver.phantoms.background.PhantomHistoricalBackgroundService;
import org.l2jmobius.gameserver.phantoms.background.PhantomNormalGatekeeperTravel;
import org.l2jmobius.gameserver.phantoms.background.PhantomVisibleAutoPlay;
import org.l2jmobius.gameserver.phantoms.background.PhantomVisibleFarmFailureBinding;
import org.l2jmobius.gameserver.phantoms.background.PhantomVisibleFarmTravel;
import org.l2jmobius.gameserver.phantoms.combat.PhantomLearnedEscape;
import org.l2jmobius.gameserver.phantoms.PhantomDiagnosticTrace;
import org.l2jmobius.gameserver.phantoms.PhantomMetrics;
import org.l2jmobius.gameserver.phantoms.decision.PhantomCandidateRegistry;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDecisionEngine;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
import org.l2jmobius.gameserver.phantoms.decision.PhantomExecutionAdmission;
import org.l2jmobius.gameserver.phantoms.decision.PhantomExecutionAdmission.Stamp;
import org.l2jmobius.gameserver.phantoms.decision.PhantomExecutionHandoff;
import org.l2jmobius.gameserver.phantoms.decision.PhantomExecutionHandoffStore;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoal;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateStore;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus;
import org.l2jmobius.gameserver.phantoms.decision.PhantomStepHandlerRegistry;
import org.l2jmobius.gameserver.phantoms.navigation.L2jNavigationBackend;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomFarmSwimRoute;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomFarmSwimRoute.Kind;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomFarmSwimRoute.Leg;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomFarmSwimRoute.Plan;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationBackend;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationCancellationToken;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPolicy;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationRequest;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationService;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService.ResultStatus;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService.ServiceState;
import org.l2jmobius.gameserver.phantoms.player.PhantomNativeWorkScope;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.gameserver.phantoms.topology.PhantomRelevanceSignalPort;
import org.l2jmobius.gameserver.scripting.ScriptEngine;

/** Controlled starting poses on the copied Bartz geodata, then stock movement only. */
final class PhantomFarmRouteRecovery006Native implements PhantomTestSuite
{
	static final String MODE = "farm-route-recovery-006";
	private static final long SEED = 600006;
	private static final PhantomNavigationPoint P24 = new PhantomNavigationPoint(-90710, 246545, -3728, 0);
	private static final PhantomNavigationPoint P24_GOAL = new PhantomNavigationPoint(-90610, 248293, -3568, 0);
	private static final PhantomNavigationPoint P27 = new PhantomNavigationPoint(-92471, 247806, -3720, 0);
	private static final PhantomNavigationPoint P27_GOAL = new PhantomNavigationPoint(-86478, 251343, -3600, 0);
	private static final PhantomNavigationPoint P32 = new PhantomNavigationPoint(113965, -165718, 40, 0);
	private static final VisibleScene VISIBLE_24 = new VisibleScene("p24", P24, 600024, "18342", "generated.farm.432802d3c2901d9686cb1651.anchor", PlayerClass.MAGE, 3, TravelMode.SWIM);
	private static final VisibleScene VISIBLE_27 = new VisibleScene("p27", P27, 600027, "20545", "population.farming.human-mystic.20481", PlayerClass.MAGE, 4, TravelMode.SWIM);
	private static final VisibleScene VISIBLE_32 = new VisibleScene("p32", P32, 600032, "20539", "generated.farm.ea72d768f3c8ec3d79c1a530.anchor", PlayerClass.DWARVEN_FIGHTER, 2, TravelMode.WALK);
	private static final VisibleScene ALTERNATE_32 = new VisibleScene("p32.alternate", P32, 600132, "20533", "generated.farm.ed8992f2edcbacf7c4f24608.anchor", PlayerClass.DWARVEN_FIGHTER, 2, TravelMode.WALK);
	private static final VisibleScene LEARNED_RETURN = new VisibleScene("return", P27, 600050, "20481", "population.farming.human-mystic.20481", PlayerClass.PROPHET, 56, TravelMode.RETURN);
	private static final VisibleScene INTERRUPTED_RETURN = new VisibleScene("return.store", P27, 600051, "20481", "population.farming.human-mystic.20481", PlayerClass.PROPHET, 40, TravelMode.RETURN);
	private static final VisibleScene UNKNOWN_RETURN = new VisibleScene("return.denial.unknown", P27, 600053, "20481", "population.farming.human-mystic.20481", PlayerClass.MAGE, 4, TravelMode.RETURN);
	private static final VisibleScene RETURN_WITHOUT_MP = new VisibleScene("return.denial.mp", P27, 600054, "20481", "population.farming.human-mystic.20481", PlayerClass.PROPHET, 56, TravelMode.RETURN);
	private static final VisibleScene RETURN_ON_REUSE = new VisibleScene("return.denial.reuse", P27, 600055, "20481", "population.farming.human-mystic.20481", PlayerClass.PROPHET, 56, TravelMode.RETURN);
	private static final VisibleScene RETURN_IN_RESTRICTED_ZONE = new VisibleScene("return.denial.zone", new PhantomNavigationPoint(-114500, -249900, -2950, 0),
		600056, "20481", "population.farming.human-mystic.20481", PlayerClass.PROPHET, 56, TravelMode.RETURN);
	private static final List<VisibleScene> DENIED_RETURNS = List.of(UNKNOWN_RETURN, RETURN_WITHOUT_MP, RETURN_ON_REUSE, RETURN_IN_RESTRICTED_ZONE);
	private enum TravelMode { WALK, SWIM, RETURN }
	private record VisibleScene(String key, PhantomNavigationPoint origin, long goalId, String npc, String anchor, PlayerClass playerClass, int level, TravelMode mode) { }

	private final PhantomHeadlessPlayerTestEnvironment _environment = new PhantomHeadlessPlayerTestEnvironment();
	private final PhantomExecutionAdmission _admission = new PhantomExecutionAdmission(3);
	private PhantomProfileRepository _profiles;
	private PhantomMaterializationService _materialization;

	static Path verifyRuntime(Path module) throws Exception
	{
		final Path runtime = PhantomCoordination130Integration.runtimeRoot(module).toRealPath();
		System.setProperty("phantom.task130.infrastructure", "true");
		System.setProperty("phantom.task130.runtime", runtime.toString());
		System.setProperty("phantom.test.config", runtime.resolve("config/Database.ini").toString());
		if (!PhantomCoordination130Native.validateInfrastructure(module)) { throw new IllegalStateException("006 requires exact owned pre-CUT infrastructure."); }
		return runtime;
	}

	static void run(Path module, Path runtime)
	{
		final var context = new PhantomTestContext(SEED, module, runtime.resolve("reports/farm-route-recovery-006"));
		System.exit(PhantomTestLauncher.runSuite(MODE, new PhantomFarmRouteRecovery006Native(), context));
	}

	@Override public String id() { return MODE; }

	@Override
	public void beforeAll(PhantomTestContext context) throws Exception
	{
		_environment.initialize(context);
		ScriptEngine.getInstance().executeScript(ScriptEngine.MASTER_HANDLER_FILE);
		PhantomAssertions.assertTrue(TargetHandler.getInstance().getHandler(TargetType.SELF) != null
			&& TargetHandler.getInstance().getHandler(TargetType.ONE) != null, "006 TEST requires the stock native target handlers.");
		PhantomAssertions.assertTrue(GeneralConfig.ALLOW_WATER, "006 requires stock water and breathing config.");
		PhantomAssertions.assertTrue(ZoneManager.getInstance().loadedGeneration() >= 0, "006 requires actual loaded zones.");
		_profiles = PhantomProfileRepository.open();
		final var metrics = new PhantomMetrics();
		_materialization = new PhantomMaterializationService(_profiles, PhantomIdentityLeaseRegistry.getInstance(), metrics,
			new PhantomDiagnosticTrace(false, 64, 16, metrics), 1);
		_materialization.installExecutionAdmission(_admission);
		PhantomAssertions.assertTrue(_materialization.start(), "006 materialization could not start.");
	}

	@Override
	public void afterAll(PhantomTestContext context) throws Exception
	{
		try
		{
			if (_materialization != null)
			{
				PhantomAssertions.assertEquals(ServiceState.STOPPED, _materialization.shutdown().state(), "006 materialization did not drain.");
			}
		}
		finally { _environment.shutdown(); }
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		final String focus = System.getProperty("phantom.farm006.native.focus", "all");
		if ("return".equals(focus) || "return-retry".equals(focus))
		{
			registry.add("return-learned-stock-cast-and-receipt-at-most-once", context -> composedTravel(context, LEARNED_RETURN));
			return;
		}
		if ("return-store".equals(focus))
		{
			registry.add("return-store-drains-paid-cast-without-replay", context -> composedTravel(context, INTERRUPTED_RETURN));
			return;
		}
		if ("return-denials".equals(focus))
		{
			for (var scene : DENIED_RETURNS)
			{
				registry.add(scene.key(), context -> composedTravel(context, scene));
			}
			return;
		}
		if ("p32".equals(focus))
		{
			registry.add("p32-visible-owner-follows-dry-corridor-to-original-farm", context -> composedTravel(context, VISIBLE_32));
			return;
		}
		if ("p32-alternate".equals(focus))
		{
			registry.add("p32-visible-owner-follows-dry-corridor-to-a-level-two-farm", context -> composedTravel(context, ALTERNATE_32));
			registry.add("return-learned-stock-cast-and-receipt-at-most-once", context -> composedTravel(context, LEARNED_RETURN));
			return;
		}
		if ("p32-recovery".equals(focus))
		{
			registry.add("p32-original-farm-or-historical-successor-earns-native-reward", context -> composedTravel(context, VISIBLE_32));
			return;
		}
		if ("walk-combat".equals(focus))
		{
			registry.add("p32-yields-owned-walk-during-combat-and-resumes-from-live-origin", context -> composedTravel(context, VISIBLE_32));
			return;
		}
		if ("p24".equals(focus))
		{
			registry.add("p24-visible-owner-replans-to-a-verified-dry-farm", context -> composedTravel(context, VISIBLE_24));
			return;
		}
		if (!"all".equals(focus)) { throw new IllegalArgumentException("Unsupported 006 native focus."); }
		registry.add("p27-dry-denial-native-water-breath-and-dry-arrival", context -> scene(context, _environment.primary(), P27, P27_GOAL, false));
		registry.add("p24-dry-denial-native-water-breath-and-dry-arrival", context -> scene(context, _environment.observer(), P24, P24_GOAL, false));
		registry.add("p24-interrupted-wet-move-store-and-fresh-epoch", context -> scene(context, _environment.observer(), P24, P24_GOAL, true));
		registry.add("p24-visible-owner-replans-to-a-verified-dry-farm", context -> composedTravel(context, VISIBLE_24));
		registry.add("p27-visible-owner-queues-swim-and-replans-on-dry-landing", context -> composedTravel(context, VISIBLE_27));
		registry.add("p32-visible-owner-follows-dry-corridor-to-original-farm", context -> composedTravel(context, VISIBLE_32));
		registry.add("return-learned-stock-cast-and-receipt-at-most-once", context -> composedTravel(context, LEARNED_RETURN));
	}

	private void composedTravel(PhantomTestContext context, VisibleScene scene) throws Exception
	{
		final boolean useLearnedReturnActor = scene.mode() == TravelMode.RETURN && scene != UNKNOWN_RETURN;
		final int objectId = (useLearnedReturnActor ? _environment.observer() : _environment.primary()).objectId();
		final long id = _profiles.create(objectId).profileId();
		try (var production = PhantomBackgroundSuite.ProductionAuthorityFixture.start())
		{
			final boolean followAlternative = scene == VISIBLE_24;
			final boolean recoverExpiredFarm = scene == VISIBLE_32 && "p32-recovery".equals(System.getProperty("phantom.farm006.native.focus"));
			final boolean historicalRecovery = followAlternative || recoverExpiredFarm;
			final var anchor = production.topology().findAnchor(scene.anchor()).orElseThrow();
			final var canonical = L2jPhantomBackgroundAuthority.canonicalCommittedAnchorPosition(anchor, 0).orElseThrow();
			final var goal = new PhantomGoal(scene.goalId(), PhantomBackgroundGoalSpec.GOAL_TYPE, PhantomGoalStatus.ACTIVE, new PhantomDomainRef("profile", Long.toString(id)),
				new PhantomDomainRef("npc", scene.npc()), 1, 0, "background.farm", List.of(new PhantomDomainRef(PhantomBackgroundGoalSpec.SOURCE_NAMESPACE, scene.npc() + "@" + anchor.id())),
				new PhantomDomainRef(PhantomBackgroundGoalSpec.ANCHOR_NAMESPACE, anchor.id()), "farm.background", 500, 0, 0, 0, Map.of(), "background.explicit", 0);
			final var goals = new PhantomGoalStateStore(_profiles);
			goals.insert(id, goal);
			final var transactions = new PhantomBackgroundTransaction();
			final Player setup = Player.load(objectId);
			try
			{
				setup.setPlayerClass(scene.playerClass().getId());
				setup.setBaseClass(scene.playerClass());
				setup.getStat().setLevel((byte) scene.level());
				setup.getStat().setExp(setup.getStat().getExpForLevel(scene.level()));
				if (followAlternative) { setup.giveAvailableAutoGetSkills(); }
				if (scene.mode() == TravelMode.WALK)
				{
					// The read-only Bartz snapshot has these three stock items equipped on 32.
					for (int itemId : new int[] {2370, 1146, 1147})
					{
						final var item = setup.addItem(ItemProcessType.REWARD, itemId, 1, setup, false);
						PhantomAssertions.assertTrue(item != null, "006 could not stage the recorded native gear on the TEST character.");
						setup.getInventory().equipItem(item);
					}
					setup.setCurrentHp(setup.getMaxHp());
					setup.setCurrentMp(setup.getMaxMp());
				}
				if (scene.mode() == TravelMode.RETURN)
				{
					setup.giveAvailableSkills(false, true, false);
					setup.rewardSkills();
					PhantomAssertions.assertTrue((setup.getKnownSkill(1050) == null) == (scene == UNKNOWN_RETURN),
						"006 fixture has the wrong learned Return capability.");
					setup.setCurrentMp(scene == RETURN_WITHOUT_MP ? 0 : setup.getMaxMp());
				}
				setup.setXYZInvisible(canonical.x(), canonical.y(), canonical.z());
				setup.setVitalityPoints(1, true);
				setup.storeMe();
				final var capture = production.authority().captureOwnedNative(id, setup, goal, null);
				PhantomAssertions.assertEquals(PhantomBackgroundTransaction.Status.SUCCESS, transactions.captureBaseline(capture.state(), goal, capture.context()).status(), "006 composed baseline was rejected.");
			}
			finally { _environment.cleanupLoadedPlayer(setup); }
			final var ref = new AtomicReference<PhantomMaterializationService>();
			final var signals = new PhantomRelevanceSignalPort()
			{
				@Override public SignalDelivery submit(long profile, PhantomRelevanceSignal signal) { return SignalDelivery.ACCEPTED; }
				@Override public SignalDelivery withdraw(long profile, String source, long sequence) { return SignalDelivery.ACCEPTED; }
			};
			final var background = new PhantomBackgroundService(_profiles, goals, PhantomIdentityLeaseRegistry.getInstance(), transactions, production.authority(), new PhantomBackgroundCompetitionRegistry(), signals, ref::get);
			final var metrics = new PhantomMetrics();
			final var materialization = new PhantomMaterializationService(_profiles, PhantomIdentityLeaseRegistry.getInstance(), metrics, new PhantomDiagnosticTrace(false, 64, 16, metrics), 1, background);
			ref.set(materialization);
			final var admission = new PhantomExecutionAdmission(1);
			materialization.installExecutionAdmission(admission);
			final var domain = PhantomExecutionHandoff.solo(id);
			final var handoffs = new PhantomExecutionHandoffStore(_profiles);
			final var handoff = handoffs.initialize(id, domain);
			admission.publish(id, handoff.component().rowVersion(), handoff.state());
			if (historicalRecovery)
			{
				goals.installExecutionPublication(handoffs, admission, System::currentTimeMillis);
				background.bindRecoveryExecution(admission);
			}
			final Queue<Runnable> workers = new ConcurrentLinkedQueue<>();
			final var nativeBackend = new L2jNavigationBackend();
			final var realDry = new AtomicBoolean(false);
			final var exhaustedDry = new PhantomNavigationBackend()
			{
				@Override public CapabilitySnapshot capability(PhantomNavigationPoint from, PhantomNavigationPoint to) { return nativeBackend.capability(from, to); }
				@Override public boolean canMoveDirect(PhantomNavigationPoint from, PhantomNavigationPoint to) { return realDry.get() && nativeBackend.canMoveDirect(from, to); }
				@Override public boolean canWalkDirect(PhantomNavigationPoint from, PhantomNavigationPoint to) { return realDry.get() && nativeBackend.canWalkDirect(from, to); }
				@Override public List<PhantomNavigationPoint> findPath(PhantomNavigationRequest request, PhantomNavigationCancellationToken token)
				{ return realDry.get() ? nativeBackend.findPath(request, token) : null; }
			};
			final var navigation = new PhantomNavigationService(PhantomNavigationPolicy.productionDefaults(), scene.mode() == TravelMode.SWIM ? exhaustedDry : nativeBackend,
				worker -> { workers.add(worker); return true; }, System::nanoTime, metrics);
			final var permit = new AtomicBoolean(true);
			final var travelClockOffset = new AtomicLong();
			final var planner = historicalRecovery ? new PhantomHistoricalBackgroundPlanner(production.knowledge(), production.topology(), production.authority()) : null;
			final var history = planner == null ? null : new PhantomHistoricalBackgroundService(_profiles, goals, planner, background, materialization);
			final var travel = new PhantomVisibleFarmTravel(materialization, background, PhantomNormalGatekeeperTravel.empty(production.topology()), navigation, profile -> permit.get(), signals,
				(profile, failure) ->
				{
					context.record(scene.key() + ".composed.failure", failure);
					if (history != null) { PhantomVisibleFarmFailureBinding.bind(history).accept(profile, failure); }
				}, () -> System.nanoTime() + travelClockOffset.get());
			try (var scope = admission.enterOwner(_ -> domain))
			{
				PhantomAssertions.assertTrue(background.start() && materialization.start() && navigation.start(), "006 composed services did not start.");
				PhantomAssertions.assertEquals(ResultStatus.SUCCESS, materialization.materialize(id).status(), "006 composed materialization failed.");
				PhantomAssertions.assertEquals(PhantomBackgroundState.State.MATERIALIZED, background.acquisitionSnapshot(id).orElseThrow().state(), "006 composed Background did not attest the native Player.");
				final Actor actor;
				try (var action = materialization.tryAcquireAction(id).orElseThrow())
				{
					final var player = action.player();
					player.setXYZ(scene.origin().x(), scene.origin().y(), scene.origin().z());
					player.revalidateZone(true);
					player.setRunning();
					actor = new Actor(player, (PhantomNativeWorkScope) player.getNativeWorkOwner(), action.executionStamp());
					assertOwner(actor);
					assertWater(player, false);
					PhantomAssertions.assertFalse(L2jPhantomBackgroundAuthority.livePositionAllowed(production.topology(), player, anchor), "006 composed displacement already lies inside the goal farm.");
				}
				switch (scene.mode())
				{
				case SWIM ->
				{
					observeComposedCrossing(context, scene, id, goal, actor, travel, navigation, workers, permit, materialization, followAlternative);
					if (followAlternative)
					{
						realDry.set(true);
						observeAlternative(context, scene, id, goal, actor, travel, navigation, workers, materialization, background, goals, metrics, planner, history, admission, domain);
					}
				}
				case WALK ->
				{
					PhantomAssertions.assertTrue(actor.player().getActiveWeaponInstance() != null && actor.player().getActiveWeaponInstance().getId() == 2370,
						"006 native class fixture lost its recorded equipped weapon during materialization.");
					if (System.getProperty("phantom.farm006.native.focus", "").startsWith("walk-combat"))
					{
						observeWalkCombat(context, scene, id, goal, actor, travel, navigation, workers, background, materialization);
						break;
					}
					observeDryJourney(context, scene, id, goal, actor, travel, workers, materialization, production);
					if (!observeFarmReward(context, scene, id, goal, actor, materialization, goals, metrics, navigation, workers, recoverExpiredFarm))
					{
						history.recordVisibleFailure(id, goal, "");
						observeAlternative(context, scene, id, goal, actor, travel, navigation, workers, materialization, background, goals, metrics, planner, history, admission, domain);
					}
				}
				case RETURN ->
				{
					if (DENIED_RETURNS.contains(scene)) { observeReturnDenial(context, scene, id, goal, actor, travel, background, materialization); }
					else if (scene == INTERRUPTED_RETURN) { observeReturnStore(context, scene, id, goal, actor, travel, background, materialization); }
					else
					{
						if (scene == LEARNED_RETURN && "return-retry".equals(System.getProperty("phantom.farm006.native.focus")))
						{
							observeDeferredReturn(context, scene, id, goal, actor, travel, background, materialization, travelClockOffset);
						}
						observeNativeReturn(context, scene, id, goal, actor, travel, background,
							() -> goals.load(id).map(stored -> stored.goal().equals(goal)).orElse(false));
					}
				}
				}
			}
			finally
			{
				travel.beforeMaterialize(id, objectId);
				navigation.beginStop();
				drainWorkers(workers);
				PhantomAssertions.assertTrue(navigation.finishStop(), "006 composed navigation did not drain.");
				PhantomAssertions.assertEquals(ServiceState.STOPPED, materialization.shutdown().state(), "006 composed materialization did not drain.");
				background.beginStop();
				PhantomAssertions.assertTrue(background.finishStop(), "006 composed Background did not drain.");
			}
		}
		finally
		{
			if (World.getInstance().getPlayer(objectId) == null) { _profiles.find(id).ifPresent(profile -> _profiles.delete(id, profile.rowVersion())); }
		}
	}

	private static void observeDeferredReturn(PhantomTestContext context, VisibleScene scene, long id, PhantomGoal goal, Actor actor,
		PhantomVisibleFarmTravel travel, PhantomBackgroundService background, PhantomMaterializationService materialization, AtomicLong travelClockOffset) throws InterruptedException
	{
		final var player = actor.player();
		final var skill = player.getKnownSkill(1050);
		final var stored = background.acquisitionSnapshot(id).orElseThrow();
		try (var action = materialization.tryAcquireAction(id, actor.stamp()).orElseThrow())
		{
			action.player().disableSkill(skill, 2000);
		}
		final double beforeMp = player.getCurrentMp();
		PhantomAssertions.assertTrue(player.isSkillDisabled(skill), "006 late Return control did not establish native reuse.");
		PhantomAssertions.assertFalse(PhantomFarmRouteRecoveryReturnTest.queue(travel, id, goal, player, actor.owner().epoch(), stored, () -> true),
			"006 reuse denial must leave no active native operation.");
		await(System.nanoTime() + TimeUnit.SECONDS.toNanos(4), () -> !player.isSkillDisabled(skill), "006 late Return control did not clear native reuse.");
		PhantomAssertions.assertFalse(PhantomFarmRouteRecoveryReturnTest.queue(travel, id, goal, player, actor.owner().epoch(), stored, () -> true),
			"006 return denial must wait for the bounded retry rather than cast on the next pulse.");
		PhantomAssertions.assertEquals(PhantomFarmRouteRecoveryReturnTest.Progress.NONE, PhantomFarmRouteRecoveryReturnTest.poll(travel, id, goal),
			"006 cooldown must release the deferred cast.");
		PhantomAssertions.assertTrue(!player.isCastingNow() && point(player).equals(scene.origin()) && player.getCurrentMp() == beforeMp,
			"006 denial and cooldown must leave native position and MP unchanged.");
		travelClockOffset.addAndGet(PhantomNavigationPolicy.productionDefaults().maximumAttemptDurationNanos());
		context.record("return.retry.wait", "native reuse denied, cooldown held after reuse cleared, expired travel episode; then one stock Return");
	}

	private static void observeReturnDenial(PhantomTestContext context, VisibleScene scene, long id, PhantomGoal goal, Actor actor,
		PhantomVisibleFarmTravel travel, PhantomBackgroundService background, PhantomMaterializationService materialization)
	{
		final var player = actor.player();
		final var skill = player.getKnownSkill(1050);
		PhantomAssertions.assertTrue((skill == null) == (scene == UNKNOWN_RETURN), "006 known skill changed during materialization.");
		if (scene == RETURN_ON_REUSE)
		{
			try (var action = materialization.tryAcquireAction(id, actor.stamp()).orElseThrow())
			{
				action.player().disableSkill(skill, skill.getReuseDelay());
			}
		}
		else if (scene == RETURN_IN_RESTRICTED_ZONE)
		{
			final var zone = ZoneManager.getInstance().getZoneById(10501);
			PhantomAssertions.assertTrue(zone != null && zone.isInsideZone(player.getX(), player.getY(), player.getZ()) && !skill.checkCondition(player, player, false),
				"006 native zone does not deny Return at the TEST point.");
		}
		final var stored = background.acquisitionSnapshot(id).orElseThrow();
		final double mp = player.getCurrentMp();
		final boolean onReuse = skill != null && player.isSkillDisabled(skill);
		PhantomAssertions.assertEquals(scene == RETURN_ON_REUSE, onReuse, "006 wrong initial native reuse state.");
		if (scene == RETURN_WITHOUT_MP)
		{
			PhantomAssertions.assertTrue(mp < player.getStat().getMpInitialConsume(skill), "006 insufficient-MP control recovered before admission.");
		}
		PhantomAssertions.assertFalse(PhantomFarmRouteRecoveryReturnTest.queue(travel, id, goal, player, actor.owner().epoch(), stored, () -> true),
			"006 a denied native Return gained recovery authority.");
		PhantomAssertions.assertEquals(PhantomFarmRouteRecoveryReturnTest.Progress.NONE, PhantomFarmRouteRecoveryReturnTest.poll(travel, id, goal),
			"006 denied Return retained an owned cast or landing.");
		PhantomAssertions.assertTrue(!player.isCastingNow() && !player.isTeleporting() && point(player).equals(scene.origin())
			&& player.getCurrentMp() >= mp && onReuse == (skill != null && player.isSkillDisabled(skill))
			&& background.acquisitionSnapshot(id).orElseThrow().position().equals(stored.position()),
			"006 denied Return moved, debited MP, changed reuse, or committed a position.");
		context.record(scene.key(), "point=" + point(player) + ";mp=" + mp + ";reuse=" + onReuse + ";epoch=" + actor.owner().epoch());
	}

	private static void observeWalkCombat(PhantomTestContext context, VisibleScene scene, long id, PhantomGoal goal, Actor actor,
		PhantomVisibleFarmTravel travel, PhantomNavigationService navigation, Queue<Runnable> workers,
		PhantomBackgroundService background, PhantomMaterializationService materialization) throws InterruptedException
	{
		final var player = actor.player();
		final var initial = background.acquisitionSnapshot(id).orElseThrow().position();
		await(System.nanoTime() + TimeUnit.SECONDS.toNanos(15), () ->
		{
			PhantomAssertions.assertFalse(travel.arrive(id, goal), "006 combat control arrived before interruption.");
			drainWorkers(workers);
			return player.isMoving() && scene.origin().distanceTo(point(player)) > 32;
		}, "006 combat control requires a physical WALK in progress.");
		final var ownMove = player.captureNativeMoveIdentity();
		final var ai = player.getAI();
		ai.clientStartAutoAttack();
		try
		{
			PhantomAssertions.assertTrue(player.isInCombat(), "006 combat stance was not established.");
			for (int pulse = 0; pulse < 8; pulse++)
			{
				PhantomAssertions.assertFalse(travel.arrive(id, goal), "006 combat became false ARRIVED.");
				drainWorkers(workers);
			}
			PhantomAssertions.assertTrue(player.isInCombat() && !ownMove.getAsBoolean() && !player.isMoving()
				&& navigation.snapshot().activeRequests() == 0 && background.acquisitionSnapshot(id).orElseThrow().position().equals(initial),
				"006 combat did not own the actor exclusively while WALK was suspended.");
		}
		finally
		{
			org.l2jmobius.gameserver.taskmanagers.AttackStanceTaskManager.getInstance().removeAttackStanceTask(player);
			ai.setAutoAttacking(false);
		}
		final var resumeFrom = point(player);
		PhantomAssertions.assertTrue(!resumeFrom.equals(scene.origin()), "006 combat control lost its actual intermediate XYZ.");
		await(System.nanoTime() + TimeUnit.SECONDS.toNanos(15), () ->
		{
			PhantomAssertions.assertFalse(travel.arrive(id, goal), "006 resumption teleported to the farm.");
			drainWorkers(workers);
			return player.isMoving() && !ownMove.getAsBoolean() && resumeFrom.distanceTo(point(player)) > 32;
		}, "006 fresh WALK did not resume after combat.");
		try (var action = materialization.tryAcquireAction(id, actor.stamp()).orElseThrow()) { assertOwner(actor); }
		context.record("p32.combat.resume", "interrupted=" + resumeFrom + ";after=" + point(player) + ";epoch=" + actor.owner().epoch());
	}

	private static void observeComposedCrossing(PhantomTestContext context, VisibleScene scene, long id, PhantomGoal goal, Actor actor, PhantomVisibleFarmTravel travel, PhantomNavigationService navigation,
		Queue<Runnable> workers, AtomicBoolean permit, PhantomMaterializationService materialization, boolean followAlternative) throws InterruptedException
	{
		final var player = actor.player();
		final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(65);
		boolean sawWet = false;
		boolean sawQueued = false;
		context.record(scene.key() + ".composed.fixtureLevel", player.getLevel());
		context.record(scene.key() + ".composed.threatsAtStart", PhantomVisibleFarmTravel.pveThreats(scene.origin(), player.getLevel(),
			List.copyOf(World.getInstance().getVisibleObjectsInRange(player, Monster.class, 3500))));
		while (System.nanoTime() < deadline && !"travel.swim_landing_replan".equals(travel.reason(id)))
		{
			PhantomAssertions.assertFalse(travel.arrive(id, goal), "006 shoreline departure unexpectedly finished the original farm goal.");
			if (travel.lastFailure(id) != null) { break; }
			sawQueued |= navigation.snapshot().activeRequests() > 0 && !workers.isEmpty();
			drainWorkers(workers);
			sawWet |= waterMatches(player, true) && !scene.origin().equals(point(player));
			Thread.sleep(20);
		}
		final var landing = point(player);
		PhantomAssertions.assertEquals("travel.swim_landing_replan", travel.reason(id), "006 visible owner did not consume a dry landing receipt: " + landing);
		PhantomAssertions.assertTrue(sawQueued && sawWet && scene.origin().distanceTo(landing) > 256, "006 composed crossing lacks actual queue, water or displacement evidence.");
		assertWater(player, false);
		try (var action = materialization.tryAcquireAction(id, actor.stamp()).orElseThrow()) { assertOwner(actor); }
		if (followAlternative)
		{
			context.record(scene.key() + ".composed.landing", "point=" + landing + ";epoch=" + actor.owner().epoch());
			return;
		}
		PhantomAssertions.assertFalse(travel.arrive(id, goal), "006 landing did not replan from its actual position.");
		PhantomAssertions.assertTrue(!workers.isEmpty() && navigation.snapshot().activeRequests() > 0, "006 did not queue the post-landing dry replan.");
		permit.set(false);
		PhantomAssertions.assertFalse(travel.arrive(id, goal), "006 ordinary owner revocation retained travel.");
		drainWorkers(workers);
		PhantomAssertions.assertEquals(0, navigation.snapshot().activeRequests(), "006 cancelled post-landing query remained owned.");
		PhantomAssertions.assertTrue(!player.isMoving() && landing.equals(point(player)), "006 stale worker moved the actor after cancellation.");
		context.record(scene.key() + ".composed.landing", "point=" + landing + ";epoch=" + actor.owner().epoch() + ";postLandingRequestCancelled=true");
	}

	private void observeAlternative(PhantomTestContext context, VisibleScene scene, long id, PhantomGoal predecessor, Actor actor, PhantomVisibleFarmTravel travel,
		PhantomNavigationService navigation, Queue<Runnable> workers, PhantomMaterializationService materialization, PhantomBackgroundService background, PhantomGoalStateStore goals,
		PhantomMetrics metrics, PhantomHistoricalBackgroundPlanner planner, PhantomHistoricalBackgroundService history,
		PhantomExecutionAdmission admission, PhantomDomainRef domain) throws InterruptedException
	{
		final var player = actor.player();
		final var landing = point(player);
		final boolean afterSwim = scene.mode() == TravelMode.SWIM;
		final var catchups = new PhantomBackgroundCatchupStore(_profiles, goals);
		final var generation = planner.generation();
		catchups.claim(id, new PhantomBackgroundCatchupState(PhantomBackgroundCatchupState.Status.COMPLETE, "d".repeat(64), SEED, 0, 1, 1, 0, 1, 1,
			generation.knowledgeGeneration(), generation.topologyGeneration(), predecessor.goalId(), predecessor.revision(), "a".repeat(64),
			PhantomBackgroundState.MODEL_VERSION, generation.authorityHashes(), ""));
		final var stored = goals.load(id).orElseThrow();
		final var historyRow = catchups.load(id).orElseThrow();
		final var candidates = new PhantomCandidateRegistry();
		final var handlers = new PhantomStepHandlerRegistry();
		final var engine = new AtomicReference<PhantomDecisionEngine>();
		final var autoPlay = new PhantomVisibleAutoPlay(materialization, engine::get, history::permitsNormalOperation);
		final var binding = PhantomBackgroundDecision.bindVisibleLife(background, travel, autoPlay, history, engine::get);
		binding.registerCandidates(candidates);
		binding.registerHandlers(handlers);
		candidates.seal();
		handlers.seal();
		final var decision = new PhantomDecisionEngine(goals, candidates, handlers, metrics, 1);
		engine.set(decision);
		decision.start();
		PhantomGoal successor = null;
		try
		{
			decision.attach(id);
			if (afterSwim)
			{
				final long exploration = System.nanoTime() + TimeUnit.SECONDS.toNanos(40);
				while (System.nanoTime() < exploration && travel.lastFailure(id) == null)
				{
					PhantomAssertions.assertFalse(travel.arrive(id, predecessor), "006 expected exhaustion of the original farm after shore landing.");
					drainWorkers(workers);
					Thread.sleep(25);
				}
				final var failed = travel.lastFailure(id);
				PhantomAssertions.assertTrue(failed != null && landing.equals(failed.proof().origin()) && landing.equals(point(player)),
					"006 did not establish an exact post-landing failed approach: " + failed + ", point=" + point(player) + ", reason=" + travel.reason(id));
			}
			final var stages = new java.util.LinkedHashSet<String>();
			final long publication = System.nanoTime() + TimeUnit.SECONDS.toNanos(40);
			boolean raced = false;
			while (System.nanoTime() < publication)
			{
				if (!raced && stages.contains("pre_visible.spatial_walk.pending"))
				{
					concurrentPreparation(id, history, decision, admission, domain);
					raced = true;
				}
				else { history.prepareVisibleDecision(id, decision); }
				stages.add(history.visiblePreparationStage(id));
				final var current = goals.load(id).orElseThrow();
				final var currentHistory = catchups.load(id).orElseThrow();
				if (current.goal().equals(predecessor))
				{
					PhantomAssertions.assertTrue(current.rowVersion() == stored.rowVersion() && currentHistory.rowVersion() == historyRow.rowVersion(),
						"006 pending route search changed one of the persistent plan rows.");
					drainWorkers(workers);
					Thread.sleep(25);
					continue;
				}
				successor = current.goal();
				PhantomAssertions.assertTrue(current.rowVersion() == stored.rowVersion() + 1 && currentHistory.rowVersion() == historyRow.rowVersion() + 1
					&& successor.goalId() == predecessor.goalId() && successor.revision() == predecessor.revision() + 1
					&& successor.revision() == currentHistory.state().goalRevision() && !successor.validSources().equals(predecessor.validSources())
					&& (!afterSwim || !successor.selectedAnchor().equals(predecessor.selectedAnchor()))
					&& landing.equals(point(player)), "006 ordinary CAS did not retain goal root, factual origin and matched generations.");
				break;
			}
			context.record(scene.key() + ".composed.alternativeStages", stages);
			PhantomAssertions.assertTrue(!afterSwim || raced, "006 published a successor without an observable pending dry proof.");
			PhantomAssertions.assertTrue(successor != null, "006 no legal alternate was published by Historical. Stages: " + stages);
			final var target = PhantomBackgroundGoalSpec.parse(successor);
			context.record(scene.key() + ".composed.alternative", "anchor=" + target.anchorId() + ";npc=" + target.npcId() + ";revision=" + successor.revision());
			boolean arrived = false;
			final long travelLimit = System.nanoTime() + TimeUnit.SECONDS.toNanos(95);
			while (System.nanoTime() < travelLimit && !arrived)
			{
				arrived = travel.arrive(id, successor);
				drainWorkers(workers);
				assertWater(player, false);
				Thread.sleep(25);
			}
			PhantomAssertions.assertTrue(arrived && (!afterSwim || landing.distanceTo(point(player)) > 256), "006 verified alternate did not receive native arrival: " + travel.reason(id));
			context.record(scene.key() + ".composed.alternateFarmArrival", point(player));
		}
		finally
		{
			autoPlay.stop(id);
			decision.beginStop();
			PhantomAssertions.assertTrue(decision.finishStop(), "006 Historical decision owner did not drain.");
		}
		final var spec = PhantomBackgroundGoalSpec.parse(successor);
		final var farm = new VisibleScene(scene.key(), landing, successor.goalId(), Integer.toString(spec.npcId()), spec.anchorId(), player.getPlayerClass(), player.getLevel(), TravelMode.WALK);
		final var arrivedGoal = successor;
		final BooleanSupplier finish = () ->
		{
			try (var action = materialization.tryAcquireAction(id, actor.stamp()).orElse(null))
			{
				return action != null && PhantomFarmRouteRecoveryReturnTest.finishEarnedRecovery(travel, id, arrivedGoal, action.player(), actor.owner().epoch());
			}
		};
		PhantomAssertions.assertFalse(finish.getAsBoolean(), "006 an arrival alone closed the recovery incident.");
		observeFarmReward(context, farm, id, successor, actor, materialization, goals, metrics, navigation, workers, false);
		if (afterSwim)
		{
			await(System.nanoTime() + TimeUnit.SECONDS.toNanos(4), finish, "006 settled native farm reward did not close the incident.");
			PhantomAssertions.assertFalse(finish.getAsBoolean(), "006 the same native farm reward closed the incident twice.");
			context.record(scene.key() + ".composed.recoveryClosedAfterReward", true);
		}
	}

	private static void concurrentPreparation(long id, PhantomHistoricalBackgroundService history, PhantomDecisionEngine decision,
		PhantomExecutionAdmission admission, PhantomDomainRef domain)
	{
		final var ready = new CountDownLatch(2);
		final Runnable pulse = () ->
		{
			ready.countDown();
			try
			{
				PhantomAssertions.assertTrue(ready.await(3, TimeUnit.SECONDS), "006 concurrent owner pulses did not reach the publication boundary.");
				try (var scope = admission.enterOwner(_ -> domain)) { history.prepareVisibleDecision(id, decision); }
			}
			catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException(interrupted); }
		};
		CompletableFuture.allOf(CompletableFuture.runAsync(pulse), CompletableFuture.runAsync(pulse)).orTimeout(5, TimeUnit.SECONDS).join();
	}

	private static void observeNativeReturn(PhantomTestContext context, VisibleScene scene, long id, PhantomGoal goal, Actor actor,
		PhantomVisibleFarmTravel travel, PhantomBackgroundService background, BooleanSupplier currentRoot) throws Exception
	{
		final var player = actor.player();
		final var baseline = background.acquisitionSnapshot(id).orElseThrow();
		final var cast = new AtomicReference<PhantomLearnedEscape.AuditReceipt>();
		final var failure = new AtomicReference<String>();
		final String property = "phantom.learning131.nonce";
		final String original = System.setProperty(property, UUID.randomUUID().toString().replace("-", ""));
		try (var observation = PhantomLearnedEscape.observeNativeAudit(cast::set, failure::set))
		{
			final var skill = player.getKnownSkill(1050);
			PhantomAssertions.assertTrue(skill != null, "006 native materialization did not retain learned Return.");
			final boolean started = PhantomFarmRouteRecoveryReturnTest.queue(travel, id, goal, player, actor.owner().epoch(), baseline, currentRoot);
			if (!started)
			{
				context.record("return.native.denial", "class=" + player.getPlayerClass() + ";skillLevel=" + skill.getLevel()
					+ ";classLevel=" + player.getLevel() + ";mp=" + player.getCurrentMp() + ";moving=" + player.isMoving()
					+ ";disabled=" + player.isMovementDisabled() + ";castCheck=" + player.checkDoCastConditions(skill)
					+ ";skillCondition=" + skill.checkCondition(player, player, false) + ";effects="
					+ skill.getEffects(org.l2jmobius.gameserver.model.skill.EffectScope.GENERAL) + ";castAudit=" + failure.get());
				throw new AssertionError("006 learned Return was not admitted by the ordinary travel owner.");
			}
			final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(28);
			await(deadline, () -> cast.get() != null || failure.get() != null, "006 native learned Return did not report a cast receipt.");
			PhantomAssertions.assertEquals(null, failure.get(), "006 native Return audit reported a failure.");
			final var receipt = cast.get().cast();
			PhantomAssertions.assertTrue(receipt.skillId() == 1050 && receipt.status() == PhantomLearnedEscape.Status.ARRIVED
				&& receipt.casterObjectId() == player.getObjectId() && receipt.casterEpoch() == actor.owner().epoch()
				&& receipt.arrivals().size() == 1 && receipt.arrivals().getFirst().before().equals(scene.origin())
				&& receipt.arrivals().getFirst().after().equals(point(player)) && !scene.origin().equals(point(player))
				&& (paidMp(receipt.initialMp()) || paidMp(receipt.hitMp())), "006 learned Return lacks exact arrival or native MP debit.");
			final long checkpointDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
			var disposition = PhantomFarmRouteRecoveryReturnTest.poll(travel, id, goal);
			while (disposition == PhantomFarmRouteRecoveryReturnTest.Progress.HOLD && System.nanoTime() < checkpointDeadline)
			{
				background.continueVisibleCheckpoint(id, goal);
				Thread.sleep(25);
				disposition = PhantomFarmRouteRecoveryReturnTest.poll(travel, id, goal);
			}
			final var reconciled = background.acquisitionSnapshot(id).orElseThrow().position();
			PhantomAssertions.assertEquals(PhantomFarmRouteRecoveryReturnTest.Progress.LANDED, disposition,
				"006 native Return did not reconcile actual landing.");
			PhantomAssertions.assertEquals(point(player), new PhantomNavigationPoint(reconciled.x(), reconciled.y(), reconciled.z(), reconciled.instanceId()),
				"006 owned checkpoint substituted unrelated anchor coordinates for the stock landing.");
			PhantomAssertions.assertFalse(PhantomFarmRouteRecoveryReturnTest.queue(travel, id, goal, player, actor.owner().epoch(), baseline, currentRoot),
				"006 learned Return could be replayed in the same incident.");
			context.record("return.native.landingDisposition", disposition);
			context.record("return.native.checkpoint", reconciled);
			context.record("return.native.receipt", receipt);
		}
		finally
		{
			if (original == null) { System.clearProperty(property); }
			else { System.setProperty(property, original); }
		}
	}

	private static boolean paidMp(PhantomLearnedEscape.MpDebit debit)
	{
		return debit != null && debit.before() > debit.after() && debit.requested() > 0;
	}

	private static void observeReturnStore(PhantomTestContext context, VisibleScene scene, long id, PhantomGoal goal, Actor actor,
		PhantomVisibleFarmTravel travel, PhantomBackgroundService background, PhantomMaterializationService materialization) throws Exception
	{
		final var player = actor.player();
		final var baseline = background.acquisitionSnapshot(id).orElseThrow();
		final var skill = player.getKnownSkill(1050);
		PhantomAssertions.assertTrue(skill != null && skill.getLevel() == 1, "006 requires the stock slower Return at Prophet level 40.");
		final double before = player.getCurrentMp();
		PhantomAssertions.assertTrue(PhantomFarmRouteRecoveryReturnTest.queue(travel, id, goal, player, actor.owner().epoch(), baseline, () -> true),
			"006 did not admit the learned Return before STORE.");
		final double afterDebit = player.getCurrentMp();
		PhantomAssertions.assertTrue(player.isCastingNow() && afterDebit < before && scene.origin().equals(point(player)),
			"006 STORE precondition requires a pending paid native cast at its original point.");
		final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
		await(deadline, () ->
		{
			if (materialization.find(id).isEmpty()) { return true; }
			final var store = materialization.requestDematerialize(id, player.getObjectId(), actor.owner().epoch(), deadline);
			PhantomAssertions.assertTrue(store.status() == ResultStatus.CLEANUP_PENDING || store.status() == ResultStatus.SUCCESS,
				"006 STORE did not accept the pending Return owner: " + store.status());
			return materialization.find(id).isEmpty();
		}, "006 STORE did not drain interrupted Return.");
		PhantomAssertions.assertTrue(!actor.owner().isCurrent() && !player.isCastingNow() && scene.origin().equals(point(player)),
			"006 interrupted Return retained authority or supplied a landing.");
		PhantomAssertions.assertEquals(PhantomFarmRouteRecoveryReturnTest.Progress.NONE, PhantomFarmRouteRecoveryReturnTest.poll(travel, id, goal),
			"006 old Return receipt survived the completed STORE.");
		PhantomAssertions.assertEquals(ResultStatus.SUCCESS, materialization.materialize(id).status(), "006 stored Return actor did not reload.");
		try (var action = materialization.tryAcquireAction(id, actor.stamp()).orElseThrow())
		{
			final var fresh = action.player();
			PhantomAssertions.assertTrue(fresh != player && fresh.getNativeWorkOwner().epoch() != actor.owner().epoch()
				&& scene.origin().equals(point(fresh)) && !fresh.isCastingNow() && fresh.getCurrentMp() < before,
				"006 native reload replayed movement or refunded the paid interrupted Return.");
			context.record("return.store", "oldEpoch=" + actor.owner().epoch() + ";newEpoch=" + fresh.getNativeWorkOwner().epoch()
				+ ";actual=" + point(fresh) + ";mp=" + before + "->" + afterDebit + "->" + fresh.getCurrentMp());
		}
	}

	private static boolean observeFarmReward(PhantomTestContext context, VisibleScene scene, long id, PhantomGoal goal, Actor actor,
		PhantomMaterializationService materialization, PhantomGoalStateStore goals, PhantomMetrics metrics,
		PhantomNavigationService navigation, Queue<Runnable> workers, boolean acceptExpiry) throws InterruptedException
	{
		final var evidence = actor.owner().evidence();
		final var baseline = evidence.snapshot();
		final var player = actor.player();
		final long exp = player.getExp();
		final long sp = player.getSp();
		final var candidates = new PhantomCandidateRegistry();
		candidates.seal();
		final var handlers = new PhantomStepHandlerRegistry();
		handlers.seal();
		final var decision = new PhantomDecisionEngine(goals, candidates, handlers, metrics, 1);
		final var autoPlay = new PhantomVisibleAutoPlay(materialization, () -> decision, profile -> profile == id);
		autoPlay.bindNavigation(navigation);
		decision.start();
		try
		{
			decision.attach(id);
			context.record(scene.key() + ".composed.nearbyFarmTargets", World.getInstance().getVisibleObjectsInRange(player, Monster.class, 3500).stream()
				.filter(monster -> Integer.toString(monster.getId()).equals(scene.npc()) && !monster.isAlikeDead())
				.map(monster -> monster.getObjectId() + "@" + monster.getX() + "," + monster.getY() + "," + monster.getZ()).limit(12).toList());
			PhantomAssertions.assertTrue(autoPlay.start(id, goal), "006 ordinary farm controller did not take over after native arrival.");
			final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(75);
			while (System.nanoTime() < deadline && !player.isAlikeDead())
			{
				drainWorkers(workers);
				// The foreground decision preflight consumes completed engagement navigation.
				final boolean expired = autoPlay.noTargetExpired(id, goal);
				final var receipts = evidence.targetReceipts();
				final var snapshot = evidence.snapshot();
				if (receipts.rewardSequence() > baseline.rewardSequence() && receipts.killSequence() > baseline.killSequence()
					&& receipts.rewardTarget() != null && receipts.rewardTarget().equals(receipts.killTarget())
					&& receipts.rewardTarget().instanceId() == scene.origin().instanceId() && snapshot.expGained() > baseline.expGained()
					&& snapshot.spGained() > baseline.spGained() && player.getExp() > exp && player.getSp() > sp)
				{
					try (var action = materialization.tryAcquireAction(id, actor.stamp()).orElseThrow()) { assertOwner(actor); }
					context.record(scene.key() + ".composed.nativeFarmReward", "target=" + receipts.rewardTarget() + ";killSeq=" + receipts.killSequence()
						+ ";rewardSeq=" + receipts.rewardSequence() + ";exp=" + exp + "->" + player.getExp() + ";sp=" + sp + "->" + player.getSp());
					return true;
				}
				if (acceptExpiry && expired)
				{
					context.record(scene.key() + ".composed.nativeFarmExpired", snapshot);
					return false;
				}
				Thread.sleep(25);
			}
			context.record(scene.key() + ".composed.farmController", autoPlay.snapshotContinuation(id).scalarMap());
			throw new AssertionError("006 ordinary controller did not attribute a kill and positive EXP/SP after actual farm arrival: " + evidence.snapshot());
		}
		finally
		{
			autoPlay.stop(id);
			decision.beginStop();
			PhantomAssertions.assertTrue(decision.finishStop(), "006 farm decision owner did not drain.");
		}
	}

	private static void observeDryJourney(PhantomTestContext context, VisibleScene scene, long id, PhantomGoal goal, Actor actor,
		PhantomVisibleFarmTravel travel, Queue<Runnable> workers, PhantomMaterializationService materialization,
		PhantomBackgroundSuite.ProductionAuthorityFixture production) throws InterruptedException
	{
		final var player = actor.player();
		final long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(7);
		final var checkpoints = new java.util.LinkedHashSet<PhantomNavigationPoint>();
		while (System.nanoTime() < deadline && travel.lastFailure(id) == null)
		{
			final boolean arrived = travel.arrive(id, goal);
			drainWorkers(workers);
			assertWater(player, false);
			if ("travel.native_episode_arrived".equals(travel.reason(id)) && scene.origin().distanceTo(point(player)) > 256 && checkpoints.add(point(player)))
			{
				try (var action = materialization.tryAcquireAction(id, actor.stamp()).orElseThrow()) { assertOwner(actor); }
				PhantomAssertions.assertFalse(player.isMoving(), "006 dry checkpoint still has pending native movement.");
				context.record(scene.key() + ".composed.checkpoint." + checkpoints.size(), "point=" + point(player) + ";epoch=" + actor.owner().epoch());
			}
			if (arrived)
			{
				final var anchor = production.topology().findAnchor(scene.anchor()).orElseThrow();
				PhantomAssertions.assertTrue(!checkpoints.isEmpty() && L2jPhantomBackgroundAuthority.livePositionAllowed(production.topology(), player, anchor),
					"006 final admission lacks native checkpoints or actual farm position.");
				try (var action = materialization.tryAcquireAction(id, actor.stamp()).orElseThrow()) { assertOwner(actor); }
				context.record(scene.key() + ".composed.farmArrival", "from=" + scene.origin() + ";point=" + point(player)
					+ ";epoch=" + actor.owner().epoch() + ";verifiedCheckpoints=" + checkpoints.size());
				return;
			}
			Thread.sleep(20);
		}
		throw new AssertionError("006 dry journey lacks a final farm arrival: " + point(player) + " checkpoints=" + checkpoints + " reason=" + travel.reason(id));
	}

	private static void drainWorkers(Queue<Runnable> workers)
	{
		Runnable worker;
		while ((worker = workers.poll()) != null) { worker.run(); }
	}

	private void scene(PhantomTestContext context, PhantomHeadlessPlayerFixture fixture, PhantomNavigationPoint origin, PhantomNavigationPoint goal, boolean interrupt) throws Exception
	{
		final long id = _profiles.create(fixture.objectId()).profileId();
		try
		{
			final var domain = PhantomExecutionHandoff.solo(id);
			final var stored = new PhantomExecutionHandoffStore(_profiles).initialize(id, domain);
			_admission.publish(id, stored.component().rowVersion(), stored.state());
			final Stamp stamp;
			try (var permit = _admission.tryAcquire(id, domain)) { stamp = permit.stamp(); }
			PhantomAssertions.assertEquals(ResultStatus.SUCCESS, _materialization.materialize(id).status(), "006 could not load owned actor.");
			final Actor actor;
			try (var action = _materialization.tryAcquireAction(id, stamp).orElseThrow())
			{
				final var player = action.player();
				player.stopMove(null);
				player.setXYZ(origin.x(), origin.y(), origin.z());
				player.revalidateZone(true);
				player.setRunning();
				final var owner = (PhantomNativeWorkScope) player.getNativeWorkOwner();
				actor = new Actor(player, owner, owner.currentExecutionStamp());
				assertOwner(actor);
				assertWater(player, false);
			}
			final var player = actor.player();
			final var geometry = geometry();
			final double speed = player.getSwimRunSpeed();
			final long breath = Math.min(60_000L, (long) (600 * player.calcStat(Stat.BREATH, player.getBaseTemplate().getBaseBreath())));
			final Plan plan = PhantomFarmSwimRoute.plan(origin, goal, geometry, speed, breath, () -> false);
			PhantomAssertions.assertTrue(plan != null && plan.requiredMillis(0, speed) <= breath, "006 has no bounded plan on copied Bartz geometry: " + origin);
			final Leg entry = plan.legs().stream().filter(leg -> leg.kind() == Kind.ENTER_WATER).findFirst().orElseThrow();
			final var landing = plan.legs().getLast().to();
			final var walking = new L2jNavigationBackend();
			PhantomAssertions.assertFalse(walking.canWalkDirect(origin, landing) || walking.canWalkDirect(entry.from(), entry.to()), "006 dry executor accepted the wet crossing fixture.");
			final String scene = origin.equals(P24) ? interrupt ? "p24.interrupted" : "p24.complete" : "p27.complete";
			context.record(scene + ".plan", plan);
			final long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(breath);
			for (Leg leg : plan.legs())
			{
				final var receipt = dispatch(id, actor, leg, geometry);
				if (interrupt && (leg.kind() == Kind.SWIM || leg.kind() == Kind.EXIT_WATER) && leg.from().distanceTo(leg.to()) > 160)
				{
					interruptAndStore(id, actor, leg, receipt, deadline, context);
					return;
				}
				await(deadline, () -> receipt.getAsBoolean() && !player.isMoving() && waterMatches(player, wetArrival(leg)), "006 native arrival did not settle: " + leg);
				try (var action = _materialization.tryAcquireAction(id, actor.stamp()).orElseThrow())
				{
					assertOwner(actor);
					PhantomAssertions.assertEquals(leg.to(), point(player), "006 exact native receipt has a different position.");
					assertWater(player, wetArrival(leg));
				}
				context.record(scene + ".arrived." + leg.kind() + "." + leg.to().x() + "." + leg.to().y(),
					"point=" + point(player) + ";epoch=" + actor.owner().epoch() + ";waterTask=" + player.isInWater());
			}
			PhantomAssertions.assertFalse(interrupt, "006 p24 has no long wet move to interrupt.");
			if (origin.equals(P27))
			{
				PhantomAssertions.assertTrue(plan.legs().stream().anyMatch(leg -> leg.kind() == Kind.SWIM), "006 p27 did not exercise a wet-to-wet native leg.");
			}
			PhantomAssertions.assertEquals(landing, point(player), "006 shoreline landing was not physically reached.");
			PhantomAssertions.assertTrue(origin.distanceTo(landing) > 256, "006 shoreline landing remained next to its original trap.");
			assertWater(player, false);
		}
		finally
		{
			if (_materialization.find(id).isPresent())
			{
				PhantomAssertions.assertEquals(ResultStatus.SUCCESS, _materialization.dematerialize(id).status(), "006 actor did not STORE on cleanup.");
			}
			_profiles.find(id).ifPresent(profile -> _profiles.delete(id, profile.rowVersion()));
		}
	}

	private BooleanSupplier dispatch(long id, Actor actor, Leg leg, PhantomFarmSwimRoute.Geometry geometry)
	{
		try (var action = _materialization.tryAcquireAction(id, actor.stamp()).orElseThrow())
		{
			final var player = action.player();
			assertOwner(actor);
			PhantomAssertions.assertEquals(leg.from(), point(player), "006 move started at a different position.");
			PhantomAssertions.assertFalse(player.isMoving(), "006 previous move still owned the lane.");
			assertWater(player, leg.kind() == Kind.SWIM || leg.kind() == Kind.EXIT_WATER);
			PhantomAssertions.assertTrue(PhantomFarmSwimRoute.valid(leg, geometry), "006 planned leg failed native geometry recheck.");
			if (leg.kind() == Kind.ENTER_WATER)
			{
				PhantomAssertions.assertTrue((long) leg.from().z() - leg.to().z() <= player.getBaseTemplate().getSafeFallHeight(), "006 shoreline entry exceeds native safe fall height.");
			}
			final var to = leg.to();
			player.getAI().setIntention(Intention.MOVE_TO, new Location(to.x(), to.y(), to.z()));
			PhantomAssertions.assertTrue(player.isMoving() && player.getAI().getIntention() == Intention.MOVE_TO, "006 stock MOVE_TO did not start: " + leg);
			final var receipt = player.nativeStraightMoveArrival(to.x(), to.y(), to.z());
			PhantomAssertions.assertTrue(receipt != null && !receipt.getAsBoolean(), "006 exact straight move receipt was missing or completed on dispatch: " + leg);
			return receipt;
		}
	}

	private void interruptAndStore(long id, Actor actor, Leg leg, BooleanSupplier receipt, long deadline, PhantomTestContext context) throws Exception
	{
		final var player = actor.player();
		await(deadline, () -> player.isMoving() && point(player).distanceTo(leg.from()) > 16 && point(player).distanceTo(leg.to()) > 100
			&& waterMatches(player, true) && !receipt.getAsBoolean(), "006 did not observe a pending native wet displacement before STORE.");
		final long storeDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
		final var store = _materialization.requestDematerialize(id, player.getObjectId(), actor.owner().epoch(), storeDeadline);
		PhantomAssertions.assertTrue(store.status() == ResultStatus.CLEANUP_PENDING || store.status() == ResultStatus.SUCCESS, "006 STORE did not accept the exact in-flight owner: " + store.status());
		await(storeDeadline, () ->
		{
			if (_materialization.find(id).isEmpty()) { return true; }
			final var continuation = _materialization.requestDematerialize(id, player.getObjectId(), actor.owner().epoch(), storeDeadline);
			PhantomAssertions.assertTrue(continuation.status() != ResultStatus.CLEANUP_FAILED_RETAINED, "006 STORE retained the wet owner: " + continuation.snapshot());
			return _materialization.find(id).isEmpty();
		}, "006 STORE did not drain the exact in-flight owner.");
		final var saved = point(player);
		PhantomAssertions.assertFalse(actor.owner().isCurrent() || receipt.getAsBoolean() || player.isMoving() || player.isInWater(), "006 old motion, breathing, owner, or arrival survived STORE.");
		PhantomAssertions.assertTrue(World.getInstance().getPlayer(player.getObjectId()) == null, "006 STORE left the old Player in World.");
		PhantomAssertions.assertEquals(ResultStatus.SUCCESS, _materialization.materialize(id).status(), "006 persisted actor did not reload.");
		PhantomAssertions.assertEquals(ResultStatus.NOT_ACTIVE,
			_materialization.requestDematerialize(id, player.getObjectId(), actor.owner().epoch(), storeDeadline).status(), "006 stale STORE targeted the new native epoch.");
		try (var action = _materialization.tryAcquireAction(id, actor.stamp()).orElseThrow())
		{
			final var fresh = action.player();
			final var owner = (PhantomNativeWorkScope) fresh.getNativeWorkOwner();
			PhantomAssertions.assertTrue(fresh != player && owner != actor.owner() && owner.isCurrent() && owner.epoch() != actor.owner().epoch(), "006 reload reused old lifetime.");
			PhantomAssertions.assertEquals(saved, point(fresh), "006 STORE did not save actual interrupted position.");
			PhantomAssertions.assertFalse(receipt.getAsBoolean(), "006 reload completed an interrupted old native receipt.");
			context.record("p24.interruptedStore", "saved=" + saved + ";oldEpoch=" + actor.owner().epoch() + ";newEpoch=" + owner.epoch());
		}
	}

	private record Actor(Player player, PhantomNativeWorkScope owner, Stamp stamp) { }

	private static void assertOwner(Actor actor)
	{
		final var player = actor.player();
		PhantomAssertions.assertTrue(actor.owner().isCurrent() && actor.owner().player() == player && player.getNativeWorkOwner() == actor.owner()
			&& actor.stamp() != null && actor.stamp().equals(actor.owner().currentExecutionStamp()), "006 movement lost its exact native owner or execution stamp.");
	}

	private static void await(long deadline, BooleanSupplier condition, String failure) throws InterruptedException
	{
		while (System.nanoTime() < deadline)
		{
			if (condition.getAsBoolean()) { return; }
			Thread.sleep(20);
		}
		throw new AssertionError(failure);
	}

	private static boolean wetArrival(Leg leg) { return leg.kind() == Kind.ENTER_WATER || leg.kind() == Kind.SWIM; }
	private static boolean waterMatches(Player player, boolean wet)
	{
		return player.isInsideZone(ZoneId.WATER) == wet && player.isInWater() == wet && !player.isInsideZone(ZoneId.CASTLE);
	}
	private static void assertWater(Player player, boolean wet)
	{
		PhantomAssertions.assertTrue(waterMatches(player, wet), "006 native water zone and breathing timer disagree at " + point(player));
	}
	private static PhantomNavigationPoint point(Player player)
	{
		return new PhantomNavigationPoint(player.getX(), player.getY(), player.getZ(), player.getInstanceId());
	}

	private static PhantomFarmSwimRoute.Geometry geometry()
	{
		final var geo = GeoEngine.getInstance();
		final var zones = ZoneManager.getInstance();
		return new PhantomFarmSwimRoute.Geometry()
		{
			@Override public PhantomNavigationPoint ground(int x, int y, PhantomNavigationPoint surface)
			{
				if (!geo.hasGeo(x, y)) { return null; }
				final int z = geo.getHeight(x, y, surface.z());
				return z == geo.getHeight(x, y, z) ? new PhantomNavigationPoint(GeoEngine.getWorldX(GeoEngine.getGeoX(x)),
					GeoEngine.getWorldY(GeoEngine.getGeoY(y)), z, surface.instanceId()) : null;
			}
			@Override public int water(PhantomNavigationPoint point)
			{
				if (zones.getZone(point.x(), point.y(), point.z(), CastleZone.class) != null) { return -1; }
				final var water = zones.getZone(point.x(), point.y(), point.z(), WaterZone.class);
				return water == null ? 0 : water.getId();
			}
			@Override public boolean line(PhantomNavigationPoint from, PhantomNavigationPoint to)
			{
				return geo.canMoveToTarget(from.x(), from.y(), from.z(), to.x(), to.y(), to.z(), from.instanceId());
			}
		};
	}
}
