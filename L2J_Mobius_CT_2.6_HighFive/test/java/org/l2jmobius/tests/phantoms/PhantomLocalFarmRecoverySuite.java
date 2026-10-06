/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.phantoms.PhantomMetrics;
import org.l2jmobius.gameserver.phantoms.background.*;
import org.l2jmobius.gameserver.phantoms.decision.*;
import org.l2jmobius.gameserver.phantoms.navigation.*;
import org.l2jmobius.gameserver.phantoms.topology.*;

/** Native travel safety and local planning, with deterministic terminal clock. */
public final class PhantomLocalFarmRecoverySuite implements PhantomTestSuite
{
	private final PhantomVisibleIntentRecoverySuite _intent = new PhantomVisibleIntentRecoverySuite();
	public static void main(String[] args)
	{
		System.exit(PhantomTestLauncher.runSuite("local-farm-recovery", new PhantomLocalFarmRecoverySuite(), new PhantomTestContext(21002102, Path.of(args[0]), Path.of(args[1]))));
	}
	@Override public String id() { return "local-farm-recovery"; }
	@Override public void beforeAll(PhantomTestContext context) throws Exception { _intent.beforeAll(context); }
	@Override public void afterAll(PhantomTestContext context) throws Exception { _intent.afterAll(context); }
	@Override public void register(PhantomTestRegistry registry)
	{
		registry.add("R03-stale-goal-is-typed-and-never-submitted", context ->
		{
			try (var f = _intent.handoff.new Fixture(true))
			{
				f.handoff(); final var goal = f.goals.load(f.id).orElseThrow().goal(); final var clock = new AtomicLong(System.nanoTime());
				try (var travel = new Travel(f, clock))
				{
					final String result = kind(travel.value, f.id, PhantomVisibleIntentRecoverySuite.revision(goal, goal.revision() + 1));
					PhantomAssertions.assertEquals("STALE_GOAL", result, "RED: stale intent must be distinct before navigation.");
					PhantomAssertions.assertEquals(null, travel.value.lastFailure(f.id), "Stale goal must not exclude current goal.");
				}
			}
		});
		registry.add("R03-delayed-navigation-reply-after-current-revision-is-discarded", context ->
		{
			try (var f = _intent.handoff.new Fixture(true))
			{
				f.handoff(); loadNativeRegion(21, 19);
				final var player = World.getInstance().getPlayer(f.objectId);
				final var anchor = production().topology().findAnchor("population.farming.elf.20534").orElseThrow().point();
				player.setXYZ(anchor.x(), anchor.y(), org.l2jmobius.gameserver.geoengine.GeoEngine.getInstance().getHeight(anchor.x(), anchor.y(), anchor.z()));
				final var engine = PhantomVisibleIntentRecoverySuite.engine(f);
				try
				{
					PhantomAssertions.assertTrue(PhantomVisibleIntentRecoverySuite.prepare(f, engine), "Local current plan prepared before delayed route.");
					final var stored = f.goals.load(f.id).orElseThrow();
					final var queued = new java.util.ArrayList<Runnable>(); final var clock = new AtomicLong(System.nanoTime());
					final PhantomNavigationBackend delayed = new PhantomNavigationBackend()
					{
						@Override public CapabilitySnapshot capability(PhantomNavigationPoint from, PhantomNavigationPoint to) { return new CapabilitySnapshot(PhantomNavigationCapability.GEODATA_PATHFINDING, 1); }
						@Override public boolean canMoveDirect(PhantomNavigationPoint from, PhantomNavigationPoint to) { return false; }
						@Override public java.util.List<PhantomNavigationPoint> findPath(PhantomNavigationRequest request, PhantomNavigationCancellationToken token) { return java.util.List.of(request.destination()); }
					}; // Controlled delayed reply only; R15 retains the factual native backend.
					try (var travel = new Travel(f, clock, delayed, worker -> { queued.add(worker); return true; }))
					{
						PhantomAssertions.assertEquals("PENDING", kind(travel.value, f.id, stored.goal()), "Actual native route waits for reply.");
						PhantomAssertions.assertTrue(!queued.isEmpty(), "Native navigation request actually submitted.");
						f.goals.replace(f.id, stored.rowVersion(), PhantomVisibleIntentRecoverySuite.revision(stored.goal(), stored.goal().revision() + 1));
						queued.forEach(Runnable::run);
						PhantomAssertions.assertEquals("STALE_GOAL", kind(travel.value, f.id, stored.goal()), "Delayed reply cannot execute old intent.");
						PhantomAssertions.assertFalse(player.isMoving(), "Stale callback issued no MOVE_TO.");
						PhantomAssertions.assertEquals(null, travel.value.lastFailure(f.id), "Stale reply adds no current exclusions.");
					}
				}
				finally { PhantomVisibleIntentRecoverySuite.stop(engine); }
			}
		});
		registry.add("R05-terminal-attempt-observation-is-idempotent", context ->
		{
			try (var f = _intent.handoff.new Fixture(true))
			{
				f.handoff(); final var goal = f.goals.load(f.id).orElseThrow().goal(); final var player = World.getInstance().getPlayer(f.objectId); final var clock = new AtomicLong(System.nanoTime());
				try (var travel = new Travel(f, clock))
				{
					player.setCastingNow(true);
					try
					{
						kind(travel.value, f.id, goal); clock.addAndGet(121_000_000_000L);
						PhantomAssertions.assertEquals("TERMINAL", kind(travel.value, f.id, goal), "RED: terminal deadline must not become pending/start_retry.");
						final var failure = travel.value.lastFailure(f.id);
						for (int i = 0; i < 20; i++) { PhantomAssertions.assertEquals("TERMINAL", kind(travel.value, f.id, goal), "Receipt remains terminal."); }
						PhantomAssertions.assertEquals(failure, travel.value.lastFailure(f.id), "No additional failure sequences or new attempts.");
					}
					finally { player.setCastingNow(false); }
				}
			}
		});
		registry.add("R14-visible-local-envelope-and-default-planner", context ->
		{
			try (var f = _intent.handoff.new Fixture(true))
			{
				f.handoff(); final var production = production(); final var planner = new PhantomHistoricalBackgroundPlanner(production.knowledge(), production.topology(), production.authority());
				final var state = f.background.acquisitionSnapshot(f.id).orElseThrow(); final var goal = f.goals.load(f.id).orElseThrow().goal(); final var player = World.getInstance().getPlayer(f.objectId);
				final var point = new PhantomTopologyPoint(player.getX(), player.getY(), player.getZ(), player.getInstanceId());
				final var original = planner.replan(f.id, state, goal, 1, 1);
				final PhantomHistoricalBackgroundPlanner.Result local;
				try { local = (PhantomHistoricalBackgroundPlanner.Result) planner.getClass().getMethod("replanVisibleLocal", long.class, PhantomBackgroundState.class, PhantomGoal.class, long.class, long.class, PhantomTopologyPoint.class, Set.class, Set.class).invoke(planner, f.id, state, goal, 1L, 1L, point, Set.of(), Set.of()); }
				catch (NoSuchMethodException beforeFix) { throw new AssertionError("RED: visible-local overload is absent."); }
				PhantomAssertions.assertTrue(local.ready(), "Actual native fixture has factual local farm candidates.");
				final var anchor = production.topology().findAnchor(local.spec().anchorId()).orElseThrow();
				PhantomAssertions.assertTrue(point.distanceSquared2D(anchor.point()) <= 4_000_000L, "Local radius2000 before tier selection.");
				PhantomAssertions.assertTrue(local.routeEdgeIds().isEmpty(), "No global leg required for native local approach.");
				PhantomAssertions.assertEquals(original, planner.replan(f.id, state, goal, 1, 1), "Historical default unchanged.");
				context.record("local.factual.candidate", local.spec());
			}
		});
		registry.add("R12-dry-endpoint-does-not-authorize-water-cell", context ->
		{
			loadNativeRegion(21, 19);
			final var geo = org.l2jmobius.gameserver.geoengine.GeoEngine.getInstance();
			final var from = new PhantomNavigationPoint(44126, 42751, -3488, 0);
			final int targetZ = geo.getHeight(48712, 55466, -3464);
			final var to = new PhantomNavigationPoint(48712, 55466, targetZ, 0);
			PhantomAssertions.assertEquals(null, org.l2jmobius.gameserver.managers.ZoneManager.getInstance().getZone(to.x(), to.y(), to.z(), org.l2jmobius.gameserver.model.zone.type.WaterZone.class), "Factual endpoint is dry.");
			final var check = PhantomVisibleFarmTravel.class.getDeclaredMethod("unsafeSegment", PhantomNavigationPoint.class, PhantomNavigationPoint.class, boolean.class, int.class); check.setAccessible(true);
			PhantomAssertions.assertEquals("travel.native_segment_water_entry", check.invoke(null, from, to, false, 0), "Intermediate water cell remains forbidden with dry endpoint.");
			context.record("water.current29.scene", "from=" + from + ";to=" + to + ";old28Spec=UNAVAILABLE");
		});
		registry.add("R13-local-unavailable-is-bounded-across-ticks", context ->
		{
			try (var f = _intent.handoff.new Fixture(true))
			{
				f.handoff(); final var engine = PhantomVisibleIntentRecoverySuite.engine(f);
				try
				{
					final var goal = f.goals.load(f.id).orElseThrow(); f.historical.recordVisibleFailure(f.id, goal.goal(), "");
					for (int i = 0; i < 20; i++) { PhantomAssertions.assertFalse(PhantomVisibleIntentRecoverySuite.prepare(f, engine), "No fresh plan without another factual local target."); }
					PhantomAssertions.assertEquals(goal, f.goals.load(f.id).orElseThrow(), "No hot revision churn.");
					PhantomAssertions.assertEquals("LOCAL_FARM_UNAVAILABLE", f.historical.visibleRecoveryReason(f.id), "Explicit bounded no-local outcome.");
				}
				finally { PhantomVisibleIntentRecoverySuite.stop(engine); }
			}
		});
		registry.add("R15-composed-terminal-replan-native-travel-autoplay", context ->
		{
			try (var f = _intent.handoff.new Fixture(true))
			{
				f.handoff(); final var old = f.goals.load(f.id).orElseThrow(); final var clock = new AtomicLong(System.nanoTime());
				try (var travel = new Travel(f, clock))
				{
					final var ref = new java.util.concurrent.atomic.AtomicReference<PhantomDecisionEngine>();
					final var autoPlay = new PhantomVisibleAutoPlay(f.materialization, ref::get, f.historical::permitsDecision);
					final var adapter = PhantomBackgroundDecision.bindVisibleLife(f.background, travel.value, autoPlay, f.historical, ref::get);
					final var candidates = new PhantomCandidateRegistry(); adapter.registerCandidates(candidates); candidates.seal();
					final var handlers = new PhantomStepHandlerRegistry(); adapter.registerHandlers(handlers); handlers.seal();
					final var engine = new PhantomDecisionEngine(f.goals, candidates, handlers, new PhantomMetrics(), 1, null, f.historical::permitsDecision); ref.set(engine); engine.start(); engine.attach(f.id);
					try
					{
						PhantomAssertions.assertTrue(PhantomVisibleIntentRecoverySuite.prepare(f, engine), "Initial current intent is prepared.");
						engine.accept(work(f.id, 1, clock.get()));
						PhantomAssertions.assertEquals(PhantomStepResult.Type.REPLAN, engine.find(f.id).orElseThrow().lastResult(), "Typed native terminal propagates through actual binding.");
						PhantomAssertions.assertEquals(travel.value.lastFailure(f.id).reason(), engine.find(f.id).orElseThrow().reasonKey(), "Original native terminal reason retained.");
						final var player = World.getInstance().getPlayer(f.objectId);
						final var anchor = production().topology().findAnchor("population.farming.elf.20534").orElseThrow();
						loadNativeRegion(21, 19);
						final var point = anchor.point(); final var geo = org.l2jmobius.gameserver.geoengine.GeoEngine.getInstance();
						player.setXYZ(point.x(), point.y(), geo.getHeight(point.x(), point.y(), point.z())); // TEST scene; no connected player control.
						PhantomAssertions.assertTrue(PhantomVisibleIntentRecoverySuite.prepare(f, engine), "Queued own local intent replaced at free boundary.");
						final var goal = f.goals.load(f.id).orElseThrow(); PhantomAssertions.assertEquals(old.goal().revision() + 1, goal.goal().revision(), "Single atomic replan.");
						final long deadline = System.nanoTime() + 15_000_000_000L; long tick = 2;
						while (System.nanoTime() < deadline && !autoPlay.running(f.id, goal.goal()))
						{
							clock.set(System.nanoTime());
							if (PhantomVisibleIntentRecoverySuite.prepare(f, engine)) { engine.accept(work(f.id, tick++, clock.get())); }
							Thread.sleep(100);
						}
						PhantomAssertions.assertTrue(autoPlay.running(f.id, goal.goal()), "Existing native AutoPlay/AutoUse reached only via executor after real travel.");
						PhantomAssertions.assertEquals(f.loadedEpoch, f.materialization.find(f.id).orElseThrow().materializedAtNanos(), "Same native lifetime through recovery.");
						PhantomAssertions.assertEquals(goal, f.goals.load(f.id).orElseThrow(), "No subsequent revision churn.");
						context.record("composed.native", "epoch=" + f.loadedEpoch + " reason=" + engine.find(f.id).orElseThrow().reasonKey() + " goal=" + PhantomBackgroundGoalSpec.parse(goal.goal()));
					}
					finally { autoPlay.stop(f.id); PhantomVisibleIntentRecoverySuite.stop(engine); }
				}
			}
		});
	}
	private static org.l2jmobius.gameserver.phantoms.activity.PhantomActivityWorkItem work(long id, long tick, long now)
	{
		return new org.l2jmobius.gameserver.phantoms.activity.PhantomActivityWorkItem(id, org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState.ACTIVE, 1, tick, now, org.l2jmobius.gameserver.phantoms.activity.PhantomActivityOverloadLevel.NORMAL);
	}
	private static void loadNativeRegion(int x, int y)
	{
		final var old = org.l2jmobius.gameserver.config.GeoEngineConfig.GEODATA_PATH;
		try
		{
			org.l2jmobius.gameserver.config.GeoEngineConfig.GEODATA_PATH = Path.of("C:/Users/ZBook/L2J_Mobius/L2J_Mobius_CT_2.6_HighFive/artifacts/local-play/runtime/game/data/geodata");
			PhantomAssertions.assertTrue(org.l2jmobius.gameserver.geoengine.GeoEngine.getInstance().reloadRegion(x, y), "Required retained native geodata exists; no replacement geometry.");
		}
		finally { org.l2jmobius.gameserver.config.GeoEngineConfig.GEODATA_PATH = old; }
	}
	private PhantomBackgroundSuite.ProductionAuthorityFixture production() throws Exception
	{
		final var field = PhantomNativeContextHandoffSuite.class.getDeclaredField("_production"); field.setAccessible(true);
		return (PhantomBackgroundSuite.ProductionAuthorityFixture) field.get(_intent.handoff);
	}
	static String kind(PhantomVisibleFarmTravel travel, long id, PhantomGoal goal) throws Exception
	{
		try { final Object result = travel.getClass().getMethod("observeArrival", long.class, PhantomGoal.class).invoke(travel, id, goal); return result.getClass().getMethod("kind").invoke(result).toString(); }
		catch (NoSuchMethodException beforeFix) { return travel.arrive(id, goal) ? "ARRIVED" : "PENDING"; }
	}
	final class Travel implements AutoCloseable
	{
		final PhantomVisibleFarmTravel value;
		final PhantomNavigationService navigation;
		final PhantomNativeContextHandoffSuite.Fixture fixture;
		Travel(PhantomNativeContextHandoffSuite.Fixture f, AtomicLong clock) throws Exception
		{
			this(f, clock, new L2jNavigationBackend(), worker -> { worker.run(); return true; });
		}
		Travel(PhantomNativeContextHandoffSuite.Fixture f, AtomicLong clock, PhantomNavigationBackend backend, PhantomNavigationService.Dispatcher dispatcher) throws Exception
		{
			fixture = f; final var production = production();
			navigation = new PhantomNavigationService(PhantomNavigationPolicy.productionDefaults(), backend, dispatcher, clock::get, new PhantomMetrics());
			navigation.start();
			final PhantomRelevanceSignalPort signals = new PhantomRelevanceSignalPort()
			{
				@Override public SignalDelivery submit(long id, org.l2jmobius.gameserver.phantoms.activity.PhantomRelevanceSignal signal) { return SignalDelivery.ACCEPTED; }
				@Override public SignalDelivery withdraw(long id, String source, long sequence) { return SignalDelivery.ACCEPTED; }
			};
			value = new PhantomVisibleFarmTravel(f.materialization, f.background, production.authority().travelQuery(production.topology()), navigation, f.historical::permitsDecision, signals, f.historical::recordVisibleTravelFailure, clock::get);
		}
		@Override public void close() { value.beforeMaterialize(fixture.id, fixture.objectId); navigation.beginStop(); navigation.finishStop(); }
	}
}
