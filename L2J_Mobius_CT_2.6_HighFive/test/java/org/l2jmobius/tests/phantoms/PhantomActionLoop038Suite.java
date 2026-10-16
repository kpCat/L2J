/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.file.Path;
import org.l2jmobius.gameserver.data.xml.NpcData;
import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.PlayerNativeWork;
import org.l2jmobius.gameserver.model.actor.instance.Monster;
import org.l2jmobius.gameserver.model.spawns.Spawn;
import org.l2jmobius.gameserver.phantoms.PhantomMetrics;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundGoalSpec;
import org.l2jmobius.gameserver.phantoms.background.PhantomVisibleAutoPlay;
import org.l2jmobius.gameserver.phantoms.navigation.*;

/** Actual native geometry and production continuation, in the exact owned TEST lane. */
public final class PhantomActionLoop038Suite implements PhantomTestSuite
{
	private final PhantomVisibleIntentRecoverySuite _intent = new PhantomVisibleIntentRecoverySuite();
	public static void main(String[] args)
	{
		System.exit(PhantomTestLauncher.runSuite("action-loop038", new PhantomActionLoop038Suite(), new PhantomTestContext(38003801, Path.of(args[0]), Path.of(args[1]))));
	}
	@Override public String id() { return "action-loop038"; }
	@Override public void beforeAll(PhantomTestContext context) throws Exception { _intent.beforeAll(context); }
	@Override public void afterAll(PhantomTestContext context) throws Exception { _intent.afterAll(context); }
	@Override public void register(PhantomTestRegistry registry)
	{
		if ("readyApproach057".equals(System.getProperty("phantom.m1.native.focus")))
		{
			registry.add("S057-ready-token-refreshes-native-approach", this::casterObstructed); return;
		}
		if ("caster040".equals(System.getProperty("phantom.m1.native.focus")))
		{
			registry.add("S040-native-caster-detour-five-stock-cycles", this::casterObstructed); return;
		}
		if ("geometry038".equals(System.getProperty("phantom.m1.native.focus")))
		{
			registry.add("S06-native-second-zone-obstructed-action", context -> obstructed(context, new PhantomNavigationPoint(-50385, -115833, -186, 0), "S06"));
			return;
		}
		if ("continuation038".equals(System.getProperty("phantom.m1.native.focus")))
		{
			registry.add("S11-S12-S16-one-writer-and-same-epoch-debt", this::writerAndDebt);
			return;
		}
		if ("boundaries038".equals(System.getProperty("phantom.m1.native.focus")))
		{
			registry.add("S03-S08-S09-S10-native-target-and-owner-boundaries", this::boundaries);
			return;
		}
		if ("freshStandpoint038".equals(System.getProperty("phantom.m1.native.focus")))
		{
			registry.add("S05-old-standpoint-receipt-allows-fresh-native-action", this::freshStandpoint);
			return;
		}
		registry.add("S02-native-obstructed-discovery-reaches-stock-damage", this::obstructed);
		if ("full038".equals(System.getProperty("phantom.m1.native.focus")))
		{
			registry.add("S05-old-standpoint-receipt-allows-fresh-native-action", this::freshStandpoint);
			registry.add("S03-S08-S09-S10-native-target-and-owner-boundaries", this::boundaries);
			registry.add("S06-native-second-zone-obstructed-action", context -> obstructed(context, new PhantomNavigationPoint(-50385, -115833, -186, 0), "S06"));
			registry.add("S11-S12-S16-one-writer-and-same-epoch-debt", this::writerAndDebt);
		}
	}
	private void writerAndDebt(PhantomTestContext context) throws Exception
	{
		try (var f = _intent.handoff.new Fixture(true))
		{
			f.handoff(); final var player = f.loadedPlayer; final var goal = f.goals.load(f.id).orElseThrow().goal();
			final var clock = new java.util.concurrent.atomic.AtomicLong(System.nanoTime());
			final var engine = PhantomVisibleIntentRecoverySuite.engine(f);
			final var navigation = new PhantomNavigationService(new PhantomMetrics()); navigation.start();
			final var autoPlay = new PhantomVisibleAutoPlay(f.materialization, () -> engine, f.historical::permitsDecision, f.historical::permitsDecision, clock::get);
			autoPlay.bindNavigation(navigation);
			try
			{
				PhantomAssertions.assertTrue(autoPlay.start(f.id, goal), "Actual shared action controller is registered.");
				final var policies = org.l2jmobius.gameserver.taskmanagers.AutoPlayTaskManager.class.getDeclaredField("PHANTOM_POLICIES"); policies.setAccessible(true);
				final var policy = (org.l2jmobius.gameserver.taskmanagers.AutoPlayTaskManager.PhantomPolicy) ((java.util.Map<?, ?>) policies.get(null)).get(player);
				org.l2jmobius.gameserver.taskmanagers.AutoPlayTaskManager.TickLease writer = null;
				final long deadline = System.nanoTime() + 1_000_000_000L;
				while (writer == null && System.nanoTime() < deadline) { writer = policy.acquire(player); if (writer == null) { Thread.sleep(10); } }
				PhantomAssertions.assertTrue(writer != null, "Current policy grants its exact native writer.");
				try
				{
					final var busy = policy.acquireTick(player, "AutoUse");
					PhantomAssertions.assertEquals(org.l2jmobius.gameserver.taskmanagers.AutoPlayTaskManager.TickStatus.PAUSED, busy.status(), "S11/S12 a competing stock root is PAUSED while the command writer is held.");
					PhantomAssertions.assertTrue(busy.lease() == null, "The competing root has no second native writer.");
					context.record("writer.actual", busy);
				}
				finally { writer.close(); }
				final var before = autoPlay.snapshotContinuation(f.id).scalarMap();
				autoPlay.stop(f.id); clock.addAndGet(5_000_000_000L);
				PhantomAssertions.assertTrue(autoPlay.start(f.id, goal), "Same native lifetime resumes through the same production entry.");
				final var after = autoPlay.snapshotContinuation(f.id).scalarMap();
				PhantomAssertions.assertEquals(before.get("liveUsefulSinceNanos"), after.get("liveUsefulSinceNanos"), "S16 controller restart does not renew original useful debt.");
				PhantomAssertions.assertEquals(before.get("liveSessionEpoch"), after.get("liveSessionEpoch"), "Same epoch is retained.");
				context.record("debt.before", before); context.record("debt.after", after);
			}
			finally { autoPlay.beforeStore(f.id, player); navigation.beginStop(); PhantomAssertions.assertTrue(navigation.finishStop(), "Writer/debt navigation drains."); PhantomVisibleIntentRecoverySuite.stop(engine); }
		}
	}
	private void boundaries(PhantomTestContext context) throws Exception
	{
		try (var f = _intent.handoff.new Fixture(true))
		{
			f.handoff(); final var player = f.loadedPlayer; final var goal = f.goals.load(f.id).orElseThrow().goal();
			final int npcId = PhantomBackgroundGoalSpec.parse(goal).npcId();
			final var pendingWorker = new java.util.concurrent.atomic.AtomicReference<Runnable>();
			final var navigation = new PhantomNavigationService(PhantomNavigationPolicy.productionDefaults(),new L2jNavigationBackend(),worker -> { pendingWorker.set(worker); return true; },System::nanoTime,new PhantomMetrics()); navigation.start();
			final var current = new java.util.concurrent.atomic.AtomicBoolean(true);
			final var controller = new org.l2jmobius.gameserver.phantoms.actions.PhantomEngagementController(navigation, current::get);
			final var planner = new org.l2jmobius.gameserver.phantoms.actions.PhantomEngagementPlanner();
			final var target = new Monster(NpcData.getInstance().getTemplate(npcId));
			final var routeTarget = new Monster(NpcData.getInstance().getTemplate(npcId));
			final var spawn = new Spawn(target.getTemplate()); spawn.setXYZ(player.getX(), player.getY(), player.getZ()); target.setSpawn(spawn);
			final int instance = org.l2jmobius.gameserver.managers.InstanceManager.getInstance().createDynamicInstance(0).getId();
			try (var action = f.materialization.tryAcquireAction(f.id).orElseThrow())
			{
				target.disableCoreAI(true); target.setImmobilized(true); target.setCurrentHpMp(target.getMaxHp(),target.getMaxMp()); target.spawnMe(spawn.getX(),spawn.getY(),spawn.getZ());
				PhantomAssertions.assertEquals(org.l2jmobius.gameserver.phantoms.actions.PhantomEngagementController.Outcome.NATIVE, controller.advance(f.id,player,goal,npcId,_ -> 0,System.nanoTime()), "S01 direct native opportunity.");
				PhantomAssertions.assertEquals(target, controller.directive(player).target(), "Exact native target key is restored.");
				final var bounded = new org.l2jmobius.gameserver.phantoms.actions.PhantomEngagementController(navigation, current::get);
				try
				{
					final long attempt = System.nanoTime();
					bounded.advance(f.id, player, goal, npcId, _ -> 0, attempt);
					PhantomAssertions.assertEquals(org.l2jmobius.gameserver.phantoms.actions.PhantomEngagementController.Outcome.EXHAUSTED, bounded.advance(f.id, player, goal, npcId, _ -> 0, attempt + navigation.policy().maximumAttemptDurationNanos() + 1), "S17 unchanged attempt deadline prevents an infinite local continuation.");
					PhantomAssertions.assertFalse(player.isMoving(), "A deadline cannot issue another native MOVE.");
					context.record("S17.actual", bounded.snapshot());
				}
				finally { bounded.revoke(); }
				target.setXYZ(target.getX(),target.getY(),target.getZ()+400);
				PhantomAssertions.assertTrue(planner.inspect(player,target,npcId).kind()!=org.l2jmobius.gameserver.phantoms.actions.PhantomEngagementPlanner.Kind.READY, "S08 same XY on another height cannot mean ARRIVED.");
				target.setXYZ(spawn.getX(),spawn.getY(),spawn.getZ()); target.setInstanceId(instance);
				PhantomAssertions.assertFalse(planner.eligible(player,target,npcId), "S08 another actual instance is ineligible.");
				PhantomAssertions.assertEquals(org.l2jmobius.gameserver.taskmanagers.AutoPlayTaskManager.TargetDirective.Mode.HOLD,controller.directive(player).mode(), "S09 fresh target mutation vetoes a new root.");
				target.setInstanceId(0); target.setXYZ(spawn.getX(),spawn.getY(),spawn.getZ());
				controller.advance(f.id,player,goal,npcId,_ -> 0,System.nanoTime()); player.setTarget(target);
				final long exp = player.getExp(), sp = player.getSp();
				target.doDie(null);
				PhantomAssertions.assertEquals(org.l2jmobius.gameserver.taskmanagers.AutoPlayTaskManager.TargetDirective.Mode.STOCK,controller.directive(player).mode(), "S03/S15 native corpse is handed back to stock processing before any root.");
				PhantomAssertions.assertEquals(exp,player.getExp(), "No fabricated reward for a pre-root death."); PhantomAssertions.assertEquals(sp,player.getSp(), "No fabricated SP.");
				// A native scalar route really enters the existing queue. Only its executor is held at
				// the existing TEST port, so busy and revocation do not depend on timing luck.
				player.setTarget(null); target.deleteMe();
				player.setXYZ(-88984,250008,-3568);
				final var routeSpawn=new Spawn(routeTarget.getTemplate());routeSpawn.setXYZ(-87984,250908,-3656);routeTarget.setSpawn(routeSpawn);
				routeTarget.disableCoreAI(true);routeTarget.setImmobilized(true);routeTarget.setCurrentHpMp(routeTarget.getMaxHp(),routeTarget.getMaxMp());routeTarget.spawnMe(routeSpawn.getX(),routeSpawn.getY(),routeSpawn.getZ());player.setTarget(routeTarget);
				final long now=System.nanoTime();
				final var request=navigation.submitWalkCorridor(new PhantomNavigationRequest(f.id,new PhantomNavigationPoint(-88984,250008,-3568,0),new PhantomNavigationPoint(-87984,250908,-3656,0),now,now+1_000_000_000L,100000));
				PhantomAssertions.assertTrue(request.requestId()>0&&pendingWorker.get()!=null,"Actual existing native route is queued.");
				PhantomAssertions.assertEquals(org.l2jmobius.gameserver.phantoms.actions.PhantomEngagementController.Outcome.TRANSIENT,controller.advance(f.id,player,goal,npcId,_ -> 0,System.nanoTime()),"S12 actual profile queue busy is transient, not target failure or ownership revocation.");
				navigation.cancel(f.id,request.requestId());pendingWorker.getAndSet(null).run();navigation.consume(request.requestId());
				Thread.sleep(navigation.policy().pathfindingCooldownMillis()+20);
				controller.advance(f.id,player,goal,npcId,_ -> 0,System.nanoTime());
				final long ownedRequest=Long.parseLong(controller.snapshot().get("actionRequestId"));
				PhantomAssertions.assertTrue(ownedRequest>0&&pendingWorker.get()!=null,"The current controller owns an actual queued native request.");
				pendingWorker.getAndSet(null).run();
				PhantomAssertions.assertEquals(PhantomNavigationResult.Status.PATH_FOUND,navigation.find(ownedRequest).orElseThrow().resultStatus(),"Actual native reply succeeded before ownership was revoked.");
				current.set(false);
				PhantomAssertions.assertEquals(org.l2jmobius.gameserver.phantoms.actions.PhantomEngagementController.Outcome.REVOKED,controller.advance(f.id,player,goal,npcId,_ -> 0,System.nanoTime()), "S10 revoked owner cannot write another command.");
				controller.revoke();
				PhantomAssertions.assertEquals("0",controller.snapshot().get("actionRequestId"), "Revocation releases the own navigation handle.");
				PhantomAssertions.assertFalse(player.isMoving(),"S10 canceled native reply cannot issue MOVE in the revoked lifetime.");
				context.record("boundaries.actual", controller.snapshot());
			}
			finally { controller.revoke(); target.setInstanceId(0); target.deleteMe(); routeTarget.deleteMe(); org.l2jmobius.gameserver.managers.InstanceManager.getInstance().destroyInstance(instance); navigation.beginStop(); PhantomAssertions.assertTrue(navigation.finishStop(), "Native boundary navigation drains."); }
		}
	}
	private void freshStandpoint(PhantomTestContext context) throws Exception
	{
		try (var production = PhantomBackgroundSuite.ProductionAuthorityFixture.start(); var f = _intent.handoff.new Fixture(true))
		{
			f.handoff();
			final var goal = f.goals.load(f.id).orElseThrow().goal();
			final var spec = PhantomBackgroundGoalSpec.parse(goal);
			final var anchor = production.topology().findAnchor(spec.anchorId()).orElseThrow();
			final var player = f.loadedPlayer;
			final var geo = GeoEngine.getInstance();
			PlayerNativeWork.run(player, "TEST_ACTION038_FRESH_POSITION", () -> player.setXYZ(anchor.point().x(), anchor.point().y(), geo.getHeight(anchor.point().x(), anchor.point().y(), anchor.point().z())));
			final var target = new Monster(NpcData.getInstance().getTemplate(spec.npcId()));
			final var spawn = new Spawn(target.getTemplate()); spawn.setXYZ(player.getX(), player.getY(), player.getZ()); target.setSpawn(spawn);
			final var navigation = new PhantomNavigationService(new PhantomMetrics()); navigation.start();
			final var route = org.l2jmobius.gameserver.phantoms.background.PhantomNormalGatekeeperTravel.load(Path.of("data/phantoms/travel/high-five-normal-gk.xml"), production.topology());
			final var signals = new org.l2jmobius.gameserver.phantoms.topology.PhantomRelevanceSignalPort()
			{
				@Override public SignalDelivery submit(long id, org.l2jmobius.gameserver.phantoms.activity.PhantomRelevanceSignal signal) { return SignalDelivery.ACCEPTED; }
				@Override public SignalDelivery withdraw(long id, String source, long sequence) { return SignalDelivery.ACCEPTED; }
			};
			final var travel = new org.l2jmobius.gameserver.phantoms.background.PhantomVisibleFarmTravel(f.materialization, f.background, route, navigation, f.historical::permitsDecision, signals);
			f.historical.bindVisibleRecovery(travel, new PhantomVisibleAutoPlay(f.materialization, () -> null, f.historical::permitsDecision));
			try
			{
				target.disableCoreAI(true); target.setImmobilized(true); target.setCurrentHpMp(target.getMaxHp(), target.getMaxMp()); target.spawnMe(spawn.getX(), spawn.getY(), spawn.getZ());
				context.record("S05.premise", "npc=" + spec.npcId() + ";actor=" + PhantomNativeTravelActions.point(player) + ";range=" + player.getPhysicalAttackRange() + ";autoAttackable=" + target.isAutoAttackable(player) + ";see=" + geo.canSeeTarget(player, target) + ";move=" + geo.canMoveToTarget(player.getX(), player.getY(), player.getZ(), target.getX(), target.getY(), target.getZ(), 0));
				PhantomAssertions.assertTrue(target.isAutoAttackable(player) && geo.canSeeTarget(player, target) && player.calculateDistance3D(target) <= player.getPhysicalAttackRange()
					&& geo.canMoveToTarget(player.getX(), player.getY(), player.getZ(), target.getX(), target.getY(), target.getZ(), 0), "INPUT: fresh factual native action must already be legal.");
				final String witness = "live.approach." + spec.anchorId() + "@" + (player.getX() + 400) + "/" + player.getY() + "/" + player.getZ() + "/0";
				PhantomAssertions.assertTrue(f.historical.recordVisibleTravelFailure(f.id, new org.l2jmobius.gameserver.phantoms.background.PhantomVisibleFarmTravel.Failure(f.id, player, f.loadedEpoch, goal, witness, "travel.native_direct_blocked", org.l2jmobius.gameserver.phantoms.background.PhantomVisibleFarmTravel.Disposition.ROUTE_UNUSABLE, System.nanoTime(), 1)), "Exact native failure receipt admitted.");
				context.record("S05.receipt", witness);
				PhantomAssertions.assertTrue(f.historical.visibleFarmReady(f.id, goal), "RED: old failed stand-point must not veto the independently legal current action.");
				PhantomAssertions.assertEquals(goal, f.goals.load(f.id).orElseThrow().goal(), "Readiness does not revise or rewrite durable goal.");
				PhantomAssertions.assertTrue(f.historical.recordVisibleTravelFailure(f.id, new org.l2jmobius.gameserver.phantoms.background.PhantomVisibleFarmTravel.Failure(f.id, player, f.loadedEpoch, goal, witness, "travel.protocol_negative", org.l2jmobius.gameserver.phantoms.background.PhantomVisibleFarmTravel.Disposition.PROTOCOL_VIOLATION, System.nanoTime(), 1)), "Exact protocol negative recorded.");
				PhantomAssertions.assertFalse(f.historical.visibleFarmReady(f.id, goal), "Fresh action never clears the protocol fence.");
			}
			finally { target.deleteMe(); travel.beforeStore(f.id, player); navigation.beginStop(); PhantomAssertions.assertTrue(navigation.finishStop(), "Navigation drained."); }
		}
	}
	private void obstructed(PhantomTestContext context) throws Exception
	{
		obstructed(context, new PhantomNavigationPoint(-88984, 250008, -3568, 0), "S02");
	}
	private void casterObstructed(PhantomTestContext context) throws Exception
	{
		// Reuse039's lawful pre-World TEST class/skill setup; production actors receive no resources.
		final var field = PhantomNativeContextHandoffSuite.class.getDeclaredField("_environment"); field.setAccessible(true);
		final var environment = (PhantomHeadlessPlayerTestEnvironment) field.get(_intent.handoff);
		try (var connection = org.l2jmobius.commons.database.DatabaseFactory.getConnection(); var statement = connection.prepareStatement("UPDATE characters SET classid=25,base_class=25,race=1,level=7,exp=?,online=0 WHERE charId=?"))
		{
			statement.setLong(1, org.l2jmobius.gameserver.data.xml.ExperienceData.getInstance().getExpForLevel(7)); statement.setInt(2, environment.primary().objectId());
			PhantomAssertions.assertEquals(1, statement.executeUpdate(), "Exact owned pre-World TEST caster.");
		}
		final var setup = Player.load(environment.primary().objectId());
		try
		{
			final var learn = org.l2jmobius.gameserver.data.xml.SkillTreeData.getInstance().getCompleteClassSkillTree(setup.getPlayerClass()).values().stream().filter(entry -> entry.getSkillId() == 1177 && entry.getSkillLevel() == 1 && entry.getGetLevel() <= setup.getLevel()).findFirst().orElseThrow();
			setup.addSkill(org.l2jmobius.gameserver.data.xml.SkillData.getInstance().getSkill(learn.getSkillId(), learn.getSkillLevel()), true); setup.storeMe();
		}
		finally { environment.cleanupLoadedPlayer(setup); }
		org.l2jmobius.gameserver.scripting.ScriptEngine.getInstance().executeScript(org.l2jmobius.gameserver.scripting.ScriptEngine.MASTER_HANDLER_FILE);
		if ("readyApproach057".equals(System.getProperty("phantom.m1.native.focus"))) { readyApproach(context); return; }
		obstructed(context, new PhantomNavigationPoint(-88984, 250008, -3568, 0), "S040");
	}
	private void readyApproach(PhantomTestContext context) throws Exception
	{
		try (var f = _intent.handoff.new Fixture(true))
		{
			f.handoff(); final var player = f.loadedPlayer; final var goal = f.goals.load(f.id).orElseThrow().goal();
			final int npcId = PhantomBackgroundGoalSpec.parse(goal).npcId();
			player.getAutoUseSettings().getAutoSkills().add(1177);
			final var planner = new org.l2jmobius.gameserver.phantoms.actions.PhantomEngagementPlanner();
			final var navigation = new PhantomNavigationService(new PhantomMetrics()); navigation.start();
			final var controller = new org.l2jmobius.gameserver.phantoms.actions.PhantomEngagementController(navigation, () -> true);
			final var target = new Monster(NpcData.getInstance().getTemplate(npcId));
			final var spawn = new Spawn(target.getTemplate()); spawn.setXYZ(-87984, 250908, -3656); target.setSpawn(spawn);
			try (var action = f.materialization.tryAcquireAction(f.id).orElseThrow())
			{
				PlayerNativeWork.run(player, "TEST_READY057_INITIAL_GEOMETRY", () -> player.setXYZ(-88399, 250472, -3572));
				target.disableCoreAI(true); target.setImmobilized(true); target.spawnMe(spawn.getX(), spawn.getY(), spawn.getZ());
				final var ready = planner.inspect(player, target, npcId); context.record("S057.ready", ready);
				PhantomAssertions.assertTrue(ready.kind() == org.l2jmobius.gameserver.phantoms.actions.PhantomEngagementPlanner.Kind.READY && ready.skillId() == 1177, "INPUT: actual caster READY at the observed native stand-point.");
				player.setTarget(target);
				controller.advance(f.id, player, goal, npcId, _ -> 0, System.nanoTime());
				PhantomAssertions.assertEquals("action.ready_native", controller.snapshot().get("actionReason"), "Same native owner acquired a READY token.");
				final var token = controller.snapshot();
				PhantomAssertions.assertTrue(GeoEngine.getInstance().canMoveToTarget(player.getX(), player.getY(), player.getZ(), -88378, 250490, -3576, 0), "INPUT: actual stock movement edge is legal.");
				PlayerNativeWork.run(player, "TEST_READY057_NATIVE_WALK", () -> player.getAI().setIntention(org.l2jmobius.gameserver.ai.Intention.MOVE_TO, new org.l2jmobius.gameserver.model.Location(-88378, 250490, -3576)));
				final long deadline = System.nanoTime() + 3_000_000_000L;
				var approach = planner.inspect(player, target, npcId);
				while (System.nanoTime() < deadline && (approach.kind() != org.l2jmobius.gameserver.phantoms.actions.PhantomEngagementPlanner.Kind.NEED_APPROACH || approach.positions().isEmpty() || approach.nativeDirect())) { Thread.sleep(5); approach = planner.inspect(player, target, npcId); }
				context.record("S057.approach", approach); context.record("S057.nativePosition", PhantomNativeTravelActions.point(player));
				PhantomAssertions.assertTrue(approach.kind() == org.l2jmobius.gameserver.phantoms.actions.PhantomEngagementPlanner.Kind.NEED_APPROACH && approach.skillId() == ready.skillId() && !approach.positions().isEmpty() && !approach.nativeDirect(), "INPUT: same target/capability needs actual fresh native stand-points during lawful stock movement.");
				controller.advance(f.id, player, goal, npcId, _ -> 0, System.nanoTime()); context.record("S057.actual", controller.snapshot());
				PhantomAssertions.assertFalse("action.positions_exhausted".equals(controller.snapshot().get("actionReason")), "RED: READY's empty cached positions must not exclude a target with fresh legal stand-points.");
				PhantomAssertions.assertEquals(token.get("actionStartedNanos"), controller.snapshot().get("actionStartedNanos"), "Refreshing geometry cannot renew the attempt deadline.");
				PhantomAssertions.assertEquals(token.get("actionSequence"), controller.snapshot().get("actionSequence"), "Same target/capability keeps its original token.");
				PhantomAssertions.assertEquals(f.loadedEpoch, player.getNativeWorkOwner().epoch(), "Same native incarnation.");
				PhantomAssertions.assertEquals(goal, f.goals.load(f.id).orElseThrow().goal(), "Native opportunity refresh does not rewrite the goal.");
			}
			finally { controller.stopMovement(player, f.loadedEpoch); controller.revoke(); target.deleteMe(); navigation.beginStop(); PhantomAssertions.assertTrue(navigation.finishStop(), "Focused native navigation drains."); }
		}
	}
	private void obstructed(PhantomTestContext context, PhantomNavigationPoint origin, String evidence) throws Exception
	{
		final var geo = GeoEngine.getInstance(); final var backend = new L2jNavigationBackend();
		PhantomAssertions.assertTrue(geo.hasGeo(origin.x(), origin.y()), "INPUT: actual retained native geometry required.");
		PhantomNavigationPoint destination = null;
		final int[][] offsets = {{896,800},{1100,600},{1000,900},{600,1000},{0,1100},{-600,1000},{1100,0},{800,-600}};
		for (var offset : offsets)
		{
			final int x = origin.x()+offset[0], y = origin.y()+offset[1];
			final var point = new PhantomNavigationPoint(x,y,geo.getHeight(x,y,origin.z()),0);
			if (backend.canMoveDirect(origin,point)) { continue; }
			final long now=System.nanoTime();
			final var route=backend.findWalkCorridor(new PhantomNavigationRequest(1,origin,point,now,now+1_000_000_000L,100000),new PhantomNavigationCancellationToken(),PhantomNavigationPolicy.productionDefaults(),System::nanoTime);
			context.record(evidence+".premise."+x+"."+y,route);
			if(route.status()==PhantomWalkCorridorPlanner.Status.FOUND)
			{
				var previous=origin;
				for(var next:route.points()){PhantomAssertions.assertTrue(backend.canWalkDirect(previous,next),"Independent native corridor edge must be legal.");previous=next;}
				context.record(evidence+".firstTurn", "distance="+origin.distanceTo(route.points().getFirst())+";nextDirect="+(route.points().size()>1&&backend.canWalkDirect(origin,route.points().get(1))));
				destination=point; break;
			}
		}
		PhantomAssertions.assertTrue(destination!=null,"INPUT: no independently proven local obstructed path; this is not a causal RED.");
		context.record(evidence+".origin",origin);context.record(evidence+".destination",destination);
		try(var f=_intent.handoff.new Fixture(true))
		{
			f.handoff();final var engine=PhantomVisibleIntentRecoverySuite.engine(f);
			final var autoPlay=new PhantomVisibleAutoPlay(f.materialization,()->engine,f.historical::permitsDecision);
			final var navigation=new PhantomNavigationService(new PhantomMetrics());navigation.start();
			// Optional only on the historical Jar: the old real entry has no approach binding.
			try { PhantomVisibleAutoPlay.class.getMethod("bindNavigation",PhantomNavigationService.class).invoke(autoPlay,navigation); }
			catch(NoSuchMethodException baseline){context.record(evidence+".historicalBinding","absent");}
			final Player player=f.loadedPlayer;final var goal=f.goals.load(f.id).orElseThrow().goal();
			Monster target=new Monster(NpcData.getInstance().getTemplate(PhantomBackgroundGoalSpec.parse(goal).npcId()));
			try
			{
				PlayerNativeWork.run(player,"TEST_ACTION038_INITIAL_GEOMETRY",()->player.setXYZ(origin.x(),origin.y(),origin.z()));
				PhantomAssertions.assertTrue(player.isSpawned(), "INPUT: movement requires the actual spawned Player.");
				final var spawn=new Spawn(target.getTemplate());spawn.setXYZ(destination.x(),destination.y(),destination.z());target.setSpawn(spawn);
				target.disableCoreAI(true);target.setImmobilized(true);target.setCurrentHpMp(target.getMaxHp(),target.getMaxMp());target.spawnMe(spawn.getX(),spawn.getY(),spawn.getZ());
				PhantomAssertions.assertTrue(target.isAutoAttackable(player)&&!target.isAlikeDead()&&!target.isInvul(),"Live domain-valid native target required.");
				PhantomAssertions.assertFalse(geo.canMoveToTarget(player.getX(),player.getY(),player.getZ(),target.getX(),target.getY(),target.getZ(),0),"Actual actor-target ray must be obstructed.");
				final long damage=player.getNativeWorkOwner().evidence().snapshot().damageSequence();
				final long killsBefore=player.getNativeWorkOwner().evidence().snapshot().killSequence();
				final long cyclesBefore=player.getNativeWorkOwner().evidence().snapshot().farmCycleSequence();
				final long expBefore=player.getExp(),spBefore=player.getSp();
				PhantomAssertions.assertTrue(autoPlay.start(f.id,goal),"Actual native session starts with current goal/owner.");
				final boolean caster="caster040".equals(System.getProperty("phantom.m1.native.focus", ""));
				if(caster){PhantomAssertions.assertTrue(player.getPlayerClass().isMage()&&player.getKnownSkill(1177)!=null,"Actual known native caster input.");}
				final var castHits=new java.util.HashSet<String>();
				final boolean cycles=java.util.Set.of("cycles038","full038","caster040").contains(System.getProperty("phantom.m1.native.focus", ""));
				// The unarmed native TEST fixture takes about 25 seconds per kill; observe five lawful
				// kills across native reselection, without changing any production 45/90/120 deadline.
				// The unchanged evidence contract closes a cycle on the next actual target selection;
				// keep one sixth NPC available for that stock reevaluation, without assigning it.
				final long deadline=System.nanoTime()+(cycles?160_000_000_000L:20_000_000_000L);
				int completed=0;
				boolean accountedDeath=false;
				long observedKills=killsBefore;
				String previous="";int transitions=0;
				while((cycles?(observedKills<killsBefore+5||player.getNativeWorkOwner().evidence().snapshot().farmCycleSequence()<cyclesBefore+5):player.getNativeWorkOwner().evidence().snapshot().damageSequence()==damage)&&System.nanoTime()<deadline)
				{
					autoPlay.noTargetExpired(f.id,goal);
					final var nativeSample=player.getNativeWorkOwner().evidence().snapshot();
					if(caster)
					{
						final var owner=(org.l2jmobius.gameserver.phantoms.player.PhantomNativeWorkScope)player.getNativeWorkOwner();
						final var hit=java.util.regex.Pattern.compile("(\\d+):cast-hit:COMPLETED:epoch=(\\d+):[^\\[\\]]*?bodyOutcome=SUCCESS").matcher(owner.diagnosticScalars().getOrDefault("nativeRecentCompletedEvents",""));
						while(hit.find()){castHits.add(hit.group(2)+":"+hit.group(1));}
					}
					if(nativeSample.killSequence()>observedKills){observedKills=nativeSample.killSequence();context.record(evidence+".nativeCycle."+observedKills,nativeSample);}
					final var fields=autoPlay.snapshotContinuation(f.id).scalarMap();final String state=fields.getOrDefault("actionReason","");
					if(!state.equals(previous)&&transitions<32){context.record(evidence+".transition."+(++transitions),fields+";pose="+PhantomNativeTravelActions.point(player)+";moving="+player.isMoving()+";destination="+player.getXdestination()+","+player.getYdestination()+","+player.getZdestination()+";intention="+player.getAI().getIntention()+";disabled="+player.isMovementDisabled()+";speed="+player.getStat().getMoveSpeed()+";tick="+org.l2jmobius.gameserver.taskmanagers.GameTimeTaskManager.getInstance().getGameTicks()+";targetHP="+target.getCurrentHp());
						if(caster){context.record(evidence+".capability."+transitions,"mp="+player.getCurrentMp()+";castDisabled="+player.isSkillDisabled(player.getKnownSkill(1177))+";target="+target.getObjectId()+";opportunity="+new org.l2jmobius.gameserver.phantoms.actions.PhantomEngagementPlanner().inspect(player,target,PhantomBackgroundGoalSpec.parse(goal).npcId())+";native="+nativeSample+";castHits="+castHits.size());}
						previous=state;}
					if(cycles&&target.isDead()&&!accountedDeath)
					{
						accountedDeath=true;
						completed++; context.record(evidence+".cycle."+completed, player.getNativeWorkOwner().evidence().snapshot());
						if(completed<6)
						{
							target.deleteMe(); target=new Monster(NpcData.getInstance().getTemplate(PhantomBackgroundGoalSpec.parse(goal).npcId())); target.setSpawn(spawn);
							target.disableCoreAI(true); target.setImmobilized(true); target.setCurrentHpMp(target.getMaxHp(),target.getMaxMp()); target.spawnMe(spawn.getX(),spawn.getY(),spawn.getZ());
							accountedDeath=false;
						}
					}
					Thread.sleep(100);
				}
				context.record(evidence+".actual",autoPlay.snapshotContinuation(f.id).scalarMap());context.record(evidence+".finalPosition",PhantomNativeTravelActions.point(player));
				context.record(evidence+".nativeFinal",player.getNativeWorkOwner().evidence().snapshot());
				if(caster){context.record(evidence+".castHits",castHits.size());}
				PhantomAssertions.assertTrue(player.getNativeWorkOwner().evidence().snapshot().damageSequence()>damage,"RED: a domain-valid target with independently proven native detour must reach actual stock damage.");
				if(cycles)
				{
					context.record(evidence+".rewards", "expDelta="+(player.getExp()-expBefore)+";spDelta="+(player.getSp()-spBefore)+";nativeKills="+(observedKills-killsBefore)+";controlledNpcDeaths="+completed);
					PhantomAssertions.assertTrue(observedKills>=killsBefore+5, "Five successive actual native deaths required across lawful stock reselection.");
					PhantomAssertions.assertTrue(player.getNativeWorkOwner().evidence().snapshot().farmCycleSequence()>=cyclesBefore+5, "Five completed native EXP+SP farm cycles are required before cleanup, not only five deaths.");
					PhantomAssertions.assertTrue(player.getExp()>expBefore&&player.getSp()>spBefore, "Stock native EXP/SP must increase.");
					if(caster){context.record(evidence+".castHits",castHits.size());PhantomAssertions.assertTrue(castHits.size()>=5,"Five actual earned stock cast hits across the blocked native approach.");}
				}
			}
			finally
			{
				autoPlay.beforeStore(f.id,player);target.abortAttack();target.abortCast();target.deleteMe();
				navigation.beginStop();PhantomAssertions.assertTrue(navigation.finishStop(),"Own native navigation drains normally.");PhantomVisibleIntentRecoverySuite.stop(engine);
			}
		}
	}
}
