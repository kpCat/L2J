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
		final var geo = GeoEngine.getInstance(); final var backend = new L2jNavigationBackend();
		final var origin = new PhantomNavigationPoint(-88984, 250008, -3568, 0);
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
			context.record("S02.premise."+x+"."+y,route);
			if(route.status()==PhantomWalkCorridorPlanner.Status.FOUND)
			{
				var previous=origin;
				for(var next:route.points()){PhantomAssertions.assertTrue(backend.canWalkDirect(previous,next),"Independent native corridor edge must be legal.");previous=next;}
				destination=point; break;
			}
		}
		PhantomAssertions.assertTrue(destination!=null,"INPUT: no independently proven local obstructed path; this is not a causal RED.");
		context.record("S02.origin",origin);context.record("S02.destination",destination);
		try(var f=_intent.handoff.new Fixture(true))
		{
			f.handoff();final var engine=PhantomVisibleIntentRecoverySuite.engine(f);
			final var autoPlay=new PhantomVisibleAutoPlay(f.materialization,()->engine,f.historical::permitsDecision);
			final var navigation=new PhantomNavigationService(new PhantomMetrics());navigation.start();
			// Optional only on the historical Jar: the old real entry has no approach binding.
			try { PhantomVisibleAutoPlay.class.getMethod("bindNavigation",PhantomNavigationService.class).invoke(autoPlay,navigation); }
			catch(NoSuchMethodException baseline){context.record("S02.historicalBinding","absent");}
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
				final long expBefore=player.getExp(),spBefore=player.getSp();
				PhantomAssertions.assertTrue(autoPlay.start(f.id,goal),"Actual native session starts with current goal/owner.");
				final boolean cycles=java.util.Set.of("cycles038","full038").contains(System.getProperty("phantom.m1.native.focus", ""));
				// The unarmed native TEST fixture takes about 25 seconds per kill; observe five lawful
				// kills across native reselection, without changing any production 45/90/120 deadline.
				final long deadline=System.nanoTime()+(cycles?160_000_000_000L:20_000_000_000L);
				int completed=0;
				long observedKills=killsBefore;
				String previous="";int transitions=0;
				while((cycles?observedKills<killsBefore+5:player.getNativeWorkOwner().evidence().snapshot().damageSequence()==damage)&&System.nanoTime()<deadline)
				{
					autoPlay.noTargetExpired(f.id,goal);
					final var nativeSample=player.getNativeWorkOwner().evidence().snapshot();
					if(nativeSample.killSequence()>observedKills){observedKills=nativeSample.killSequence();context.record("S02.nativeCycle."+observedKills,nativeSample);}
					final var fields=autoPlay.snapshotContinuation(f.id).scalarMap();final String state=fields.getOrDefault("actionReason","");
					if(!state.equals(previous)&&transitions<32){context.record("S02.transition."+(++transitions),fields+";pose="+PhantomNativeTravelActions.point(player)+";moving="+player.isMoving()+";destination="+player.getXdestination()+","+player.getYdestination()+","+player.getZdestination()+";intention="+player.getAI().getIntention()+";disabled="+player.isMovementDisabled()+";speed="+player.getStat().getMoveSpeed()+";tick="+org.l2jmobius.gameserver.taskmanagers.GameTimeTaskManager.getInstance().getGameTicks()+";targetHP="+target.getCurrentHp());previous=state;}
					if(cycles&&target.isDead())
					{
						completed++; context.record("S02.cycle."+completed, player.getNativeWorkOwner().evidence().snapshot());
						if(completed<5)
						{
							target.deleteMe(); target=new Monster(NpcData.getInstance().getTemplate(PhantomBackgroundGoalSpec.parse(goal).npcId())); target.setSpawn(spawn);
							target.disableCoreAI(true); target.setImmobilized(true); target.setCurrentHpMp(target.getMaxHp(),target.getMaxMp()); target.spawnMe(spawn.getX(),spawn.getY(),spawn.getZ());
						}
					}
					Thread.sleep(100);
				}
				context.record("S02.actual",autoPlay.snapshotContinuation(f.id).scalarMap());context.record("S02.finalPosition",PhantomNativeTravelActions.point(player));
				PhantomAssertions.assertTrue(player.getNativeWorkOwner().evidence().snapshot().damageSequence()>damage,"RED: a domain-valid target with independently proven native detour must reach actual stock damage.");
				if(cycles)
				{
					context.record("S02.rewards", "expDelta="+(player.getExp()-expBefore)+";spDelta="+(player.getSp()-spBefore)+";nativeKills="+(observedKills-killsBefore)+";controlledNpcDeaths="+completed);
					PhantomAssertions.assertTrue(observedKills>=killsBefore+5, "Five successive actual native deaths required across lawful stock reselection.");
					PhantomAssertions.assertTrue(player.getExp()>expBefore&&player.getSp()>spBefore, "Stock native EXP/SP must increase.");
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
