/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.geoengine.pathfinding.GeoNode;
import org.l2jmobius.gameserver.geoengine.pathfinding.NodeBuffer;
import org.l2jmobius.gameserver.geoengine.pathfinding.PathFinding;
import org.l2jmobius.gameserver.phantoms.navigation.L2jNavigationBackend;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNativeRouteContract;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationCancellationToken;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPolicy;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationRequest;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationResult;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomWalkCorridorPlanner;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomTravelStrategy;

/** Owned native fixture and actual stock geometry; geometry tests never write XYZ. */
public final class PhantomTravelPlanning033RSuite implements PhantomTestSuite
{
	private final PhantomVisibleIntentRecoverySuite _intent = new PhantomVisibleIntentRecoverySuite();
	public static void main(String[] args)
	{
		System.exit(PhantomTestLauncher.runSuite("travel-planning033r", new PhantomTravelPlanning033RSuite(), new PhantomTestContext(33003301, Path.of(args[0]), Path.of(args[1]))));
	}
	@Override public String id() { return "travel-planning033r"; }
	@Override public void beforeAll(PhantomTestContext context) throws Exception
	{
		_intent.beforeAll(context);
		if (List.of("return033r","returnDeath033r").contains(System.getProperty("phantom.m1.native.focus",""))) { org.l2jmobius.gameserver.scripting.ScriptEngine.getInstance().executeScript(org.l2jmobius.gameserver.scripting.ScriptEngine.MASTER_HANDLER_FILE); }
	}
	@Override public void afterAll(PhantomTestContext context) throws Exception { _intent.afterAll(context); }
	@Override public void register(PhantomTestRegistry registry)
	{
		if ("gatekeeper033r".equals(System.getProperty("phantom.m1.native.focus")))
		{
			registry.add("T14-native-GK-complete-plan-fee-arrival-and-final-MOVE", this::nativeGatekeeper); return;
		}
		if ("gatekeeperInteraction033r".equals(System.getProperty("phantom.m1.native.focus")))
		{
			registry.add("T21-native-GK-rechecks-stock-interaction-before-fee",this::nativeGatekeeper);return;
		}
		if ("decoratedWalk033r".equals(System.getProperty("phantom.m1.native.focus")))
		{
			registry.add("T20-decorated-native-WALK-keeps-corridor-and-stock-bounds", this::decoratedWalk); return;
		}
		if ("revalidation033r".equals(System.getProperty("phantom.m1.native.focus")))
		{
			registry.add("T08-pre-move-revalidation-rejects-native-illegal-edge", this::revalidation); return;
		}
		if ("return033r".equals(System.getProperty("phantom.m1.native.focus")))
		{
			registry.add("T13-native-unstuck-timer-owned-before-publication", this::returnOwnership);
			registry.add("T13-native-return-completes-actual-unchanged-timer", this::nativeReturn); return;
		}
		if ("returnDeath033r".equals(System.getProperty("phantom.m1.native.focus")))
		{
			registry.add("T13-native-unstuck-timer-owned-before-publication", this::returnOwnership);
			registry.add("T19-recorded-native-obstacle-route-and-five-cycles", this::recordedRoute); return;
		}
		if ("recordedRoute033r".equals(System.getProperty("phantom.m1.native.focus")))
		{
			registry.add("T19-recorded-native-obstacle-route-and-five-cycles", this::recordedRoute); return;
		}
		if ("strategy033r".equals(System.getProperty("phantom.m1.native.focus")))
		{
			registry.add("T11-visible-candidate-rejects-full-unaffordable-native-fees", this::strategyBudget);
			registry.add("T12-complete-plan-time-and-cumulative-resources", this::completeCost); return;
		}
		registry.add("T06-unsafe-stock-parent-chain-remains-rejected", this::lostTurn);
		registry.add("T01-complete-native-walk-to-dry-farm-ingress", this::completeWalk);
		registry.add("T03-U-detour-keeps-directed-surface-identity", this::detour);
		registry.add("T07-search-and-deadline-remain-bounded", this::budget);
		registry.add("T08-pre-move-revalidation-rejects-native-illegal-edge", this::revalidation);
		registry.add("T11-visible-candidate-rejects-full-unaffordable-native-fees", this::strategyBudget);
		registry.add("T12-complete-plan-time-and-cumulative-resources", this::completeCost);
		if("finalTravel033r".equals(System.getProperty("phantom.m1.native.focus")))
		{
			registry.add("T14-native-GK-complete-plan-fee-arrival-and-final-MOVE",this::nativeGatekeeper);
			registry.add("T20-decorated-native-WALK-keeps-corridor-and-stock-bounds",this::decoratedWalk);
			registry.add("T21-native-GK-rechecks-stock-interaction-before-fee",context -> nativeGatekeeper(context,true));
		}
	}
	private void lostTurn(PhantomTestContext context) throws Exception
	{
		final var from = new PhantomNavigationPoint(-88984, 250008, -3568, 0);
		final var to = new PhantomNavigationPoint(-87883, 251678, -3632, 0);
		final var geo = GeoEngine.getInstance();
		PhantomAssertions.assertTrue(geo.hasGeo(from.x(), from.y()) && geo.hasGeo(to.x(), to.y()), "Real retained032 geometry is required.");
		final var backend = new L2jNavigationBackend();
		final var full = nativeChain(from, to);
		PhantomAssertions.assertFalse(full.isEmpty(), "The unchanged stock A* must have a parent-chain witness.");
		var previous = from;
		String firstBad = "";
		for (var point : full)
		{
			if (firstBad.isEmpty() && !backend.canMoveDirect(previous, point)) { firstBad = previous + " -> " + point; }
			previous = point;
		}
		context.record("T06.parentChainPoints", full.size());
		context.record("T06.parentChainFirstBad", firstBad);
		final long now = System.nanoTime();
		final var points = backend.findPath(new PhantomNavigationRequest(1, from, to, now, now + 1_000_000_000L, 100000), new PhantomNavigationCancellationToken());
		final var normalized = PhantomNativeRouteContract.normalize(from, to, points, PhantomNavigationPolicy.productionDefaults(), 100000, backend::canMoveDirect, () -> PhantomNavigationResult.Status.PATH_FOUND);
		context.record("T06.backendPoints", points);
		context.record("T06.normalized", normalized.status());
		PhantomAssertions.assertFalse(firstBad.isEmpty(), "This witness disproves a lost-turn-only diagnosis; raw native parents also cross an illegal height transition.");
		PhantomAssertions.assertEquals(PhantomNavigationResult.Status.ROUTE_OBSTRUCTED, normalized.status(), "Unsafe native output must remain rejected; no smoothing or XYZ correction.");
	}
	private void completeWalk(PhantomTestContext context) throws Exception
	{
		final var from = new PhantomNavigationPoint(-88984, 250008, -3568, 0);
		final var geo = GeoEngine.getInstance();
		final var to = new PhantomNavigationPoint(-86401, 253927, geo.getHeight(-86401, 253927, -3736), 0);
		final var backend = new L2jNavigationBackend();
		PhantomAssertions.assertTrue(org.l2jmobius.gameserver.managers.ZoneManager.getInstance().getZone(to.x(), to.y(), to.z(), org.l2jmobius.gameserver.model.zone.type.WaterZone.class) == null, "Final native farm ingress must be dry.");
		final long now = System.nanoTime();
		final var corridor = backend.findWalkCorridor(new PhantomNavigationRequest(5, from, to, now, now + 1_000_000_000L, 100000), new PhantomNavigationCancellationToken(), PhantomNavigationPolicy.productionDefaults(), System::nanoTime);
		final var normalized = PhantomNativeRouteContract.normalize(from, to, corridor.points(), PhantomNavigationPolicy.productionDefaults(), 100000, backend::canWalkDirect, () -> PhantomNavigationResult.Status.PATH_FOUND);
		context.record("T01.destination", to);
		context.record("T01.corridor", corridor);
		context.record("T01.nativeProof", normalized.status());
		PhantomAssertions.assertEquals(PhantomWalkCorridorPlanner.Status.FOUND, corridor.status(), "Complete corridor required within unchanged deadline and stock buffers.");
		PhantomAssertions.assertEquals(PhantomNavigationResult.Status.PATH_FOUND, normalized.status(), "RED: a complete verified native corridor is required before movement, including a lawful alternative to the rejected ray.");
	}
	private void detour(PhantomTestContext context)
	{
		final List<PhantomNavigationPoint> nodes = List.of(point(0,0,0), point(-1024,0,0), point(-1024,1024,0), point(-1024,2048,0), point(0,2048,0), point(1024,2048,0), point(2048,2048,0), point(2048,1024,0), point(2048,0,0));
		final var oracle = new PhantomWalkCorridorPlanner.Oracle()
		{
			@Override public PhantomNavigationPoint project(int x, int y, PhantomNavigationPoint surface) { return nodes.stream().filter(p -> p.x() == x && p.y() == y && p.z() == surface.z()).findFirst().orElse(null); }
			@Override public boolean direct(PhantomNavigationPoint from, PhantomNavigationPoint to) { final int i = nodes.indexOf(from); return i >= 0 && i + 1 < nodes.size() && nodes.get(i + 1).equals(to); }
			@Override public List<PhantomNavigationPoint> path(PhantomNavigationPoint from, PhantomNavigationPoint to) { return null; }
		};
		final var planner = new PhantomWalkCorridorPlanner(oracle, () -> 1);
		final var result = planner.plan(new PhantomNavigationRequest(1, nodes.getFirst(), nodes.getLast(), 1, 1000, 100000), new PhantomNavigationCancellationToken(), PhantomNavigationPolicy.productionDefaults());
		PhantomAssertions.assertEquals(PhantomWalkCorridorPlanner.Status.FOUND, result.status(), "U-shaped corridor requires going away from the goal before coming around the wall.");
		PhantomAssertions.assertTrue(result.points().getFirst().distanceTo(nodes.getLast()) > nodes.getFirst().distanceTo(nodes.getLast()), "First useful step increases distance; no greedy ray chopping.");
		final var reverse = planner.plan(new PhantomNavigationRequest(1, nodes.getLast(), nodes.getFirst(), 1, 1000, 100000), new PhantomNavigationCancellationToken(), PhantomNavigationPolicy.productionDefaults());
		PhantomAssertions.assertEquals(PhantomWalkCorridorPlanner.Status.NO_ROUTE_WITHIN_BUDGET, reverse.status(), "Forward proof grants no reverse traversal.");
		final var otherFloor = planner.plan(new PhantomNavigationRequest(1, nodes.getFirst(), point(2048,0,256), 1, 1000, 100000), new PhantomNavigationCancellationToken(), PhantomNavigationPolicy.productionDefaults());
		PhantomAssertions.assertEquals(PhantomWalkCorridorPlanner.Status.NO_ROUTE_WITHIN_BUDGET, otherFloor.status(), "Same XY on another surface is a different unreachable node.");
		context.record("T03.detour", result);
	}
	private void budget(PhantomTestContext context)
	{
		final var oracle = new PhantomWalkCorridorPlanner.Oracle()
		{
			@Override public PhantomNavigationPoint project(int x, int y, PhantomNavigationPoint surface) { return point(x,y,0); }
			@Override public boolean direct(PhantomNavigationPoint from, PhantomNavigationPoint to) { return to.x() == from.x() + 1024 && to.y() == from.y() && to.z() == from.z(); }
			@Override public List<PhantomNavigationPoint> path(PhantomNavigationPoint from, PhantomNavigationPoint to) { return null; }
		};
		final var request = new PhantomNavigationRequest(1, point(0,0,0), point(0,2048,0), 1, 1000, 100000);
		final var limited = new PhantomWalkCorridorPlanner(oracle, () -> 1).plan(request, new PhantomNavigationCancellationToken(), PhantomNavigationPolicy.productionDefaults());
		PhantomAssertions.assertEquals(PhantomWalkCorridorPlanner.Status.SEARCH_LIMIT, limited.status(), "Unknown skipped edges after budget exhaustion cannot become UNREACHABLE.");
		PhantomAssertions.assertTrue(limited.nodes() <= 128 && limited.edges() <= 512 && limited.queries() <= 32 && limited.labels() <= 4096 && limited.points().isEmpty(), "All coordinator bounds and empty partial result enforced.");
		final var expired = new PhantomWalkCorridorPlanner(oracle, () -> 1000).plan(request, new PhantomNavigationCancellationToken(), PhantomNavigationPolicy.productionDefaults());
		PhantomAssertions.assertEquals(PhantomWalkCorridorPlanner.Status.DEADLINE_EXPIRED, expired.status(), "Existing request deadline wins over structural budget.");
		context.record("T07.bounds", limited);
	}
	private static PhantomNavigationPoint point(int x, int y, int z) { return new PhantomNavigationPoint(x,y,z,0); }
	private void revalidation(PhantomTestContext context) throws Exception
	{
		final var from=point(-88088,250792,-3616); final var to=point(-88088,250808,-3712);
		PhantomAssertions.assertFalse(new L2jNavigationBackend().canMoveDirect(from,to),"Actual retained032 native NSWE/height witness is blocked.");
		final var check=org.l2jmobius.gameserver.phantoms.background.PhantomVisibleFarmTravel.class.getDeclaredMethod("unsafeSegment",PhantomNavigationPoint.class,PhantomNavigationPoint.class,boolean.class,int.class);
		check.setAccessible(true); final var rejected=check.invoke(null,from,to,false,0);
		context.record("T08.actualEdge",from+" -> "+to+";preMove="+rejected);
		PhantomAssertions.assertTrue(rejected!=null,"RED: pre-MOVE checks must include factual native NSWE/doors, not only endpoint height/water.");
		final var origin=point(-88984,250008,-3568); final var waypoint=point(-88280,250568,-3592);
		final var backend=new L2jNavigationBackend();
		PhantomAssertions.assertTrue(backend.canWalkDirect(origin,waypoint),"Existing C14 verified native edge required for the changed-edge replan contract.");
		final var blocked=new org.l2jmobius.gameserver.phantoms.navigation.PhantomWalkCorridorPlanner.Edge(origin,waypoint);
		final long now=System.nanoTime();
		final var replan=backend.findWalkCorridor(new PhantomNavigationRequest(5,origin,waypoint,now,now+1_000_000_000L,100000),new PhantomNavigationCancellationToken(),PhantomNavigationPolicy.productionDefaults(),System::nanoTime,java.util.Set.of(blocked));
		context.record("T08.replan",replan);
		PhantomAssertions.assertEquals(PhantomWalkCorridorPlanner.Status.FOUND,replan.status(),"A complete native alternative exists around this exact blocked ray; no deadline/debt reset.");
		var previous=origin;
		for(var next:replan.points()) { PhantomAssertions.assertFalse(blocked.equals(new PhantomWalkCorridorPlanner.Edge(previous,next)),"The failed directed edge is not repeated."); PhantomAssertions.assertTrue(backend.canWalkDirect(previous,next),"Every alternative segment has current native proof."); previous=next; }
	}
	private void returnOwnership(PhantomTestContext context) throws Exception
	{
		final var handler = org.l2jmobius.gameserver.handler.UserCommandHandler.getInstance().getHandler(52);
		PhantomAssertions.assertTrue(handler != null, "Actual stock Unstuck handler must be loaded, not a simulated return.");
		try (var f = _intent.handoff.new Fixture(true))
		{
			f.handoff(); final var player = f.loadedPlayer;
			final var scope = (org.l2jmobius.gameserver.phantoms.player.PhantomNativeWorkScope)player.getNativeWorkOwner();
			PhantomAssertions.assertTrue(!player.isGM() && !player.isCastingNow(), "Ordinary uncased native fixture required.");
			final long timer = Math.multiplyExact((long)org.l2jmobius.gameserver.config.PlayerConfig.UNSTUCK_INTERVAL, 1000);
			final int before = pendingNative(scope); final var accepted = new java.util.concurrent.atomic.AtomicBoolean();
			try
			{
				org.l2jmobius.gameserver.model.actor.PlayerNativeWork.run(player, "TEST_NATIVE_RETURN033R", () -> accepted.set(handler.onCommand(52, player)));
				context.record("T13.nativeTimer", timer); context.record("T13.publication", "accepted="+accepted+";casting="+player.isCastingNow()+";before="+before+";after="+pendingNative(scope)+";facts="+scope.diagnosticScalars());
				PhantomAssertions.assertTrue(accepted.get() && player.isCastingNow(), "Native handler must actually publish the unchanged return timer.");
				PhantomAssertions.assertTrue(pendingNative(scope) > before, "RED: the actual delayed Unstuck callback must reserve cancellable native ownership before publication.");
				org.l2jmobius.gameserver.model.actor.PlayerNativeWork.run(player, "TEST_CANCEL_RETURN033R", player::abortCast);
				PhantomAssertions.assertEquals(before, pendingNative(scope), "Cancelling the actual cast retires its pending native callback.");
				org.l2jmobius.gameserver.model.actor.PlayerNativeWork.run(player, "TEST_PENDING_RETURN033R", () -> accepted.set(handler.onCommand(52, player)));
				PhantomAssertions.assertTrue(accepted.get() && pendingNative(scope) > before, "A second actual native callback must be pending before dematerialization.");
				PhantomVisibleIntentRecoverySuite.complete(f);
				PhantomAssertions.assertEquals(org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService.ResultStatus.SUCCESS, f.materialization.dematerialize(f.id).status(), "Existing lifecycle cancels and stores the exact returning lifetime.");
				PhantomAssertions.assertEquals(org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService.ResultStatus.SUCCESS, f.materialization.materialize(f.id).status(), "Actual replacement lifetime admitted normally.");
				final var replacement = f.loadedPlayer; final var position = org.l2jmobius.gameserver.phantoms.navigation.PhantomNativeTravelActions.point(replacement);
				PhantomAssertions.assertTrue(replacement != player && f.loadedEpoch != scope.epoch(), "New Player and epoch required.");
				org.l2jmobius.gameserver.model.actor.PlayerNativeWork.run(replacement,"TEST_RETURN_BEFORE_DEATH033R",()->accepted.set(handler.onCommand(52,replacement)));
				PhantomAssertions.assertTrue(accepted.get() && replacement.isCastingNow(),"Actual replacement lifetime starts its own ordinary pending return.");
				final var castField=org.l2jmobius.gameserver.model.actor.Creature.class.getDeclaredField("_skillCast"); castField.setAccessible(true);
				final var capturedDeathReturn=(java.util.concurrent.Future<?>)castField.get(replacement);
				PhantomAssertions.assertTrue(capturedDeathReturn!=null,"Capture the actual published Unstuck Future before death, rather than unrelated native safety timers.");
				org.l2jmobius.gameserver.model.actor.PlayerNativeWork.run(replacement,"TEST_NATIVE_DEATH033R",()->replacement.doDie(null));
				PhantomAssertions.assertTrue(replacement.isDead(),"Actual native death must precede the delayed finalizer; no synthetic flag.");
				final long waitUntil = System.nanoTime() + (timer + 1500) * 1_000_000L;
				while (System.nanoTime() < waitUntil) { Thread.sleep(100); }
				PhantomAssertions.assertEquals(position, org.l2jmobius.gameserver.phantoms.navigation.PhantomNativeTravelActions.point(replacement), "Late original timer cannot teleport the replacement lifetime.");
				PhantomAssertions.assertEquals(0, scope.outstanding(), "Retired callback leaves no outstanding old native work.");
				PhantomAssertions.assertTrue(capturedDeathReturn.isDone(),"The exact dead lifetime's native return Future is completed or cancelled after the unchanged interval.");
				context.record("T13.lateTimer", "waitMillis="+(timer+1500)+";oldEpoch="+scope.epoch()+";newEpoch="+f.loadedEpoch+";replacementXYZ="+position+";oldOutstanding="+scope.outstanding());
			}
			finally { org.l2jmobius.gameserver.model.actor.PlayerNativeWork.run(player, "TEST_RETURN033R_CLEANUP", player::abortCast); }
		}
	}
	private static int pendingNative(org.l2jmobius.gameserver.phantoms.player.PhantomNativeWorkScope scope)
	{
		return scope.outstanding()+Integer.parseInt(scope.diagnosticScalars().get("nativeOwnerPendingTimers"));
	}
	private void recordedRoute(PhantomTestContext context) throws Exception
	{
		final var evidenceRoot=context.moduleRoot().resolve("docs/phantoms/tasks/PHANTOM-M1-TRAVEL-PLANNING-033R/evidence");
		final var input=new java.util.Properties();
		try(var reader=java.nio.file.Files.newBufferedReader(evidenceRoot.resolve("C45_ROUTE_PROOF_INPUT.properties"))){input.load(reader);}
		final var birthPath=evidenceRoot.resolve(input.getProperty("birth")).normalize(); final var ledgerPath=evidenceRoot.resolve(input.getProperty("ledger")).normalize();
		PhantomAssertions.assertTrue(birthPath.startsWith(evidenceRoot) && ledgerPath.startsWith(evidenceRoot),"Exact owned raw evidence paths required.");
		for(var entry:java.util.Map.of("birth",birthPath,"ledger",ledgerPath).entrySet())
		{
			final var hash=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(java.nio.file.Files.readAllBytes(entry.getValue())));
			PhantomAssertions.assertEquals(input.getProperty(entry.getKey()+"Sha256"),hash,"Immutable actual evidence hash required.");
		}
		final var birth=new java.util.Properties(); try(var reader=java.nio.file.Files.newBufferedReader(birthPath)){birth.load(reader);}
		PhantomAssertions.assertEquals("true",birth.getProperty("worldAbsentAtRegister"),"Factual initial coordinates captured before World publication.");
		PhantomAssertions.assertEquals("true",birth.getProperty("nativeBirthBoundary"),"Actual native birth hook required.");
		final var origin=point(Integer.parseInt(birth.getProperty("initialX")),Integer.parseInt(birth.getProperty("initialY")),Integer.parseInt(birth.getProperty("initialZ")));
		final var backend=new L2jNavigationBackend(); final var geo=GeoEngine.getInstance();
		final List<PhantomNavigationPoint> turns=new ArrayList<>(); int provedCycles=0; long firstSample=0; String destination="";
		PhantomNavigationPoint previousPose=null; boolean previousMoving=false;
		try(var lines=java.nio.file.Files.lines(ledgerPath))
		{
			for(var line:(Iterable<String>)lines::iterator)
			{
				final var row=line.split("\t",-1);
				if(row.length!=24 || !row[1].equals(birth.getProperty("profileId")) || !row[3].equals(birth.getProperty("epoch"))){continue;}
				PhantomAssertions.assertEquals(birth.getProperty("objectId"),row[2],"Same exact Player object throughout proof.");
				if(firstSample==0){firstSample=Long.parseLong(row[0]);}
				PhantomAssertions.assertEquals("NONE",row[23],"No unproven native evidence in the recorded lifetime.");
				if(row[18].equals("ROUTE"))
				{
					PhantomAssertions.assertEquals("false",row[10],"Actual obstacle route has no teleport.");
					for(var field:java.util.Map.of(14,"initialExp",15,"initialSp",16,"initialAdena",17,"initialScrolls").entrySet()){PhantomAssertions.assertEquals(birth.getProperty(field.getValue()),row[field.getKey()],"Planning/native route creates no resources or rewards.");}
					final var key=row[12]+","+row[13];
					if(row[8].equals("true") && !key.equals(destination))
					{
						PhantomAssertions.assertEquals("MOVE_TO",row[11],"Observed stock MOVE intention required.");
						final var sampled=point(Integer.parseInt(row[4]),Integer.parseInt(row[5]),Integer.parseInt(row[6]));
						// Creature.updatePosition snaps to the factual prior destination before the next native leg.
						// A 100ms sample can already be partway along that leg; it is not its issued origin.
						final var from=turns.isEmpty()?origin:turns.getLast();
						final int x=Integer.parseInt(row[12]),y=Integer.parseInt(row[13]); final var to=point(x,y,geo.getHeight(x,y,from.z()));
						PhantomAssertions.assertTrue(previousPose!=null && (previousMoving || previousPose.equals(from)),"Idle pre-MOVE pose must equal the actual issued origin.");
						final double sampledDistance=Math.hypot((long)sampled.x()-from.x(),(long)sampled.y()-from.y())+Math.hypot((long)sampled.x()-to.x(),(long)sampled.y()-to.y());
						PhantomAssertions.assertTrue(sampledDistance<=Math.hypot((long)from.x()-to.x(),(long)from.y()-to.y())+2,"Actual native sampled XY must remain on its issued segment (integer movement rounding only).");
						PhantomAssertions.assertTrue(backend.canWalkDirect(from,to),"Every actual issued segment has native height/dry/NSWE proof: " + from + " -> " + to);
						if(!backend.canWalkDirect(sampled,to)){context.record("T19.sampleRayDifference"+turns.size(),"issued="+from+" -> "+to+";sampled="+sampled+";issuedDirect="+backend.canMoveDirect(from,to)+";sampledDirect="+backend.canMoveDirect(sampled,to));}
						turns.add(to); destination=key;
					}
				}
				final int cycle=Integer.parseInt(row[19]);
				if(cycle>provedCycles && provedCycles<5)
				{
					PhantomAssertions.assertEquals(provedCycles+1,cycle,"Five consecutive actual native cycle transitions required, no skipped proof.");
					PhantomAssertions.assertTrue(Integer.parseInt(row[20])>=cycle && Integer.parseInt(row[21])>=cycle && Long.parseLong(row[14])>Long.parseLong(birth.getProperty("initialExp")) && Long.parseLong(row[15])>Long.parseLong(birth.getProperty("initialSp")),"Each cycle has actual kill/reward and native EXP/SP; shared targets remain lawful.");
					provedCycles=cycle; context.record("T19.cycle"+cycle,line);
				}
				previousPose=point(Integer.parseInt(row[4]),Integer.parseInt(row[5]),Integer.parseInt(row[6])); previousMoving=row[8].equals("true");
			}
		}
		PhantomAssertions.assertTrue(firstSample>0 && firstSample-Long.parseLong(birth.getProperty("registerNanos"))<1_000_000_000L,"Evidence starts within one second of actual birth, before the first route completes.");
		PhantomAssertions.assertTrue(turns.size()>1,"Actual native detour must contain useful turns.");
		PhantomAssertions.assertFalse(backend.canWalkDirect(origin,turns.getLast()),"Actual full goal ray is blocked; the recorded native route is an obstacle detour.");
		PhantomAssertions.assertEquals(5,provedCycles,"Actual route is followed by five consecutive proven native cycles.");
		context.record("T19.actualBirth",origin); context.record("T19.actualMoves",turns);
	}
	private void decoratedWalk(PhantomTestContext context)
	{
		final var stock=new L2jNavigationBackend(); final var maximum=new java.util.concurrent.atomic.AtomicInteger();
		final var wrapped=new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationBackend()
		{
			@Override public CapabilitySnapshot capability(PhantomNavigationPoint from,PhantomNavigationPoint to){return stock.capability(from,to);}
			@Override public boolean canMoveDirect(PhantomNavigationPoint from,PhantomNavigationPoint to){return stock.canMoveDirect(from,to);}
			@Override public List<PhantomNavigationPoint> findPath(PhantomNavigationRequest request,PhantomNavigationCancellationToken cancellation)
			{
				final var a=request.origin();final var b=request.destination();
				maximum.accumulateAndGet(64+2*Math.max(Math.abs(GeoEngine.getGeoX(a.x())-GeoEngine.getGeoX(b.x())),Math.abs(GeoEngine.getGeoY(a.y())-GeoEngine.getGeoY(b.y()))),Math::max);
				return stock.findPath(request,cancellation);
			}
		};
		final var navigation=new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationService(PhantomNavigationPolicy.productionDefaults(),wrapped,worker -> {worker.run();return true;},System::nanoTime,new org.l2jmobius.gameserver.phantoms.PhantomMetrics());
		navigation.start();
		try
		{
			final long now=System.nanoTime();
			final var submitted=navigation.submitWalkCorridor(new PhantomNavigationRequest(1,point(-88984,250008,-3568),point(-86401,253927,-3736),now,now+1_000_000_000L,100000));
			final var result=navigation.consume(submitted.requestId()).orElseThrow(); context.record("T20.actualResult",result);context.record("T20.actualBuffer",maximum.get());
			PhantomAssertions.assertEquals(PhantomNavigationResult.Status.PATH_FOUND,result.status(),"A decorated native WALK must use the same complete native corridor, including after transport; no fallback to the oversized whole query.");
			PhantomAssertions.assertTrue(maximum.get()>0 && maximum.get()<=500,"Every actual native query fits unchanged production stock buffers.");
		}
		finally {navigation.beginStop();PhantomAssertions.assertTrue(navigation.finishStop(),"Existing worker/queue lifecycle drains normally.");}
	}
	private void nativeGatekeeper(PhantomTestContext context) throws Exception
	{
		nativeGatekeeper(context,"gatekeeperInteraction033r".equals(System.getProperty("phantom.m1.native.focus")));
	}
	private void nativeGatekeeper(PhantomTestContext context,boolean closedInteraction) throws Exception
	{
		// Reuse the initialized native fixture and ordinary materialization lifecycle (production suite pattern).
		final var environmentField=PhantomNativeContextHandoffSuite.class.getDeclaredField("_environment"); environmentField.setAccessible(true);
		final var environment=(PhantomHeadlessPlayerTestEnvironment)environmentField.get(_intent.handoff);
		final var productionField=PhantomNativeContextHandoffSuite.class.getDeclaredField("_production"); productionField.setAccessible(true);
		final var production=(PhantomBackgroundSuite.ProductionAuthorityFixture)productionField.get(_intent.handoff);
		final var travel=org.l2jmobius.gameserver.phantoms.background.PhantomNormalGatekeeperTravel.load(Path.of("data/phantoms/travel/high-five-normal-gk.xml"),production.topology());
		final var backend=new L2jNavigationBackend(); final var geo=GeoEngine.getInstance();
		final var leg=travel.legs().stream().filter(org.l2jmobius.gameserver.phantoms.navigation.PhantomNativeTravelActions::available).filter(value ->
		{
			final var landing=point(value.destinationX(),value.destinationY(),geo.getHeight(value.destinationX(),value.destinationY(),value.destinationZ())+5);
			final var target=point(value.destinationX()+128,value.destinationY(),geo.getHeight(value.destinationX()+128,value.destinationY(),landing.z()));
			return backend.canWalkDirect(landing,target);
		}).findFirst().orElseThrow();
		final var origin=point(leg.sourceX(),leg.sourceY(),geo.getHeight(leg.sourceX(),leg.sourceY(),leg.sourceZ()));
		final var target=point(leg.destinationX()+128,leg.destinationY(),geo.getHeight(leg.destinationX()+128,leg.destinationY(),leg.destinationZ()));
		final int objectId=environment.primary().objectId();
		PhantomAssertions.assertEquals(null,org.l2jmobius.gameserver.model.World.getInstance().getPlayer(objectId),"TEST initial setup must precede World publication.");
		final var setup=org.l2jmobius.gameserver.model.actor.Player.load(objectId);
		try { setup.setXYZInvisible(origin.x(),origin.y(),origin.z()); setup.storeMe(); }
		finally { environment.cleanupLoadedPlayer(setup); }
		final var profiles=org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository.open();
		final long profile=profiles.create(objectId).profileId(); final var metrics=new org.l2jmobius.gameserver.phantoms.PhantomMetrics();
		final var materialization=new org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService(profiles,org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry.getInstance(),metrics,new org.l2jmobius.gameserver.phantoms.PhantomDiagnosticTrace(false,64,16,metrics),1);
		materialization.start();
		try
		{
			PhantomAssertions.assertEquals(org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService.ResultStatus.SUCCESS,materialization.materialize(profile).status(),"Ordinary TEST lifetime admitted by existing native lifecycle.");
			final var player=org.l2jmobius.gameserver.model.World.getInstance().getPlayer(objectId);
			final long exp=player.getExp(),sp=player.getSp(),adena=player.getAdena(),scrolls=player.getInventory().getInventoryItemCount(736,-1);
			final long now=System.nanoTime();
			final var plan=backend.planTravel(new PhantomNavigationRequest(profile,origin,target,now,now+1_000_000_000L,100000),new PhantomNavigationCancellationToken(),PhantomNavigationPolicy.productionDefaults(),System::nanoTime,org.l2jmobius.gameserver.phantoms.navigation.PhantomNativeTravelActions.snapshot(player,List.of(leg),120000));
			context.record("T14.completePlan",plan);
			PhantomAssertions.assertEquals(PhantomNavigationResult.Status.PATH_FOUND,plan.status(),"Complete native GK ingress/landing/egress is proven before action.");
			PhantomAssertions.assertTrue(plan.candidate().stages().stream().anyMatch(stage -> stage.mode()==PhantomTravelStrategy.Mode.GK),"Remote TEST goal requires the actual catalog GK.");
			final var receipt=new java.util.concurrent.atomic.AtomicReference<org.l2jmobius.gameserver.phantoms.navigation.PhantomNativeTravelActions.GatekeeperResult>();
			if(closedInteraction)
			{
				org.l2jmobius.gameserver.model.actor.PlayerNativeWork.run(player,"TEST_NATIVE_SIT033R",player::sitDown);
				final var npc=org.l2jmobius.gameserver.phantoms.navigation.PhantomNativeTravelActions.gatekeeperAt(leg);
				PhantomAssertions.assertTrue(player.isSitting() && !npc.canInteract(player),"Actual native sitting closes stock NPC interaction after planning.");
				org.l2jmobius.gameserver.model.actor.PlayerNativeWork.run(player,"TEST_NATIVE_GK_CLOSED_INTERACTION033R",()->receipt.set(org.l2jmobius.gameserver.phantoms.navigation.PhantomNativeTravelActions.gatekeeper(player,leg)));
				context.record("T21.actualReceipt",receipt.get());
				PhantomAssertions.assertEquals(org.l2jmobius.gameserver.phantoms.navigation.PhantomNativeTravelActions.Status.UNAVAILABLE,receipt.get().status(),"Native adapter must recheck stock NPC interaction before spending or teleporting.");
				PhantomAssertions.assertEquals(origin,org.l2jmobius.gameserver.phantoms.navigation.PhantomNativeTravelActions.point(player),"Denied native interaction changes no XYZ.");
				PhantomAssertions.assertEquals(adena,player.getAdena(),"Denied interaction spends nothing.");return;
			}
			org.l2jmobius.gameserver.model.actor.PlayerNativeWork.run(player,"TEST_NATIVE_GK033R",()->receipt.set(org.l2jmobius.gameserver.phantoms.navigation.PhantomNativeTravelActions.gatekeeper(player,leg)));
			context.record("T14.nativeReceipt",receipt.get());
			PhantomAssertions.assertEquals(org.l2jmobius.gameserver.phantoms.navigation.PhantomNativeTravelActions.Status.ARRIVED,receipt.get().status(),"Stock holder charges its actual tariff once and arrives at the factual native XYZ.");
			PhantomAssertions.assertEquals(adena-receipt.get().fee(),player.getAdena(),"Actual native fee; no grant or substitute balance.");
			PhantomAssertions.assertTrue(backend.canWalkDirect(org.l2jmobius.gameserver.phantoms.navigation.PhantomNativeTravelActions.point(player),target),"Actual landing has current final-WALK proof.");
			org.l2jmobius.gameserver.model.actor.PlayerNativeWork.run(player,"TEST_NATIVE_GK_EGRESS033R",()->player.getAI().setIntention(org.l2jmobius.gameserver.ai.Intention.MOVE_TO,new org.l2jmobius.gameserver.model.Location(target.x(),target.y(),target.z())));
			PhantomAssertions.assertTrue(player.isMoving(),"Final native MOVE actually starts.");
			final long deadline=System.nanoTime()+5_000_000_000L;
			while(player.isMoving() && System.nanoTime()<deadline){Thread.sleep(50);}
			final var after=org.l2jmobius.gameserver.phantoms.navigation.PhantomNativeTravelActions.point(player);
			PhantomAssertions.assertTrue(!player.isMoving() && after.distanceTo(target)<=16 && after.instanceId()==target.instanceId(),"Final native WALK actually arrives without XYZ writes.");
			PhantomAssertions.assertEquals(exp,player.getExp(),"Transport creates no EXP."); PhantomAssertions.assertEquals(sp,player.getSp(),"Transport creates no SP."); PhantomAssertions.assertEquals(scrolls,player.getInventory().getInventoryItemCount(736,-1),"NORMAL GK consumes no scroll.");
			PhantomAssertions.assertEquals(null,((org.l2jmobius.gameserver.phantoms.player.PhantomNativeWorkScope)player.getNativeWorkOwner()).firstNativeIncident(),"Each native mutation belongs to this exact lifetime.");
			PhantomAssertions.assertEquals(org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService.ResultStatus.SUCCESS,materialization.dematerialize(profile).status(),"Native arrival is stored through ordinary cleanup.");
			final var reloaded=org.l2jmobius.gameserver.model.actor.Player.load(objectId);
			try { PhantomAssertions.assertEquals(after,org.l2jmobius.gameserver.phantoms.navigation.PhantomNativeTravelActions.point(reloaded),"Exact actual XYZ persists."); PhantomAssertions.assertEquals(player.getAdena(),reloaded.getAdena(),"Actual charged balance persists."); }
			finally { environment.cleanupLoadedPlayer(reloaded); }
			context.record("T14.finalNativeXYZ",after);
		}
		finally
		{
			PhantomAssertions.assertEquals(org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService.ServiceState.STOPPED,materialization.shutdown().state(),"Owned native GK fixture drains normally.");
			profiles.find(profile).ifPresent(value -> profiles.delete(profile,value.rowVersion()));
		}
	}
	private void nativeReturn(PhantomTestContext context) throws Exception
	{
		try (var f = _intent.handoff.new Fixture(true))
		{
			f.handoff(); final var player=f.loadedPlayer; final var before=org.l2jmobius.gameserver.phantoms.navigation.PhantomNativeTravelActions.point(player);
			final long exp=player.getExp(), sp=player.getSp(), adena=player.getAdena(), scrolls=player.getInventory().getInventoryItemCount(736,-1);
			final long timer=Math.multiplyExact((long)org.l2jmobius.gameserver.config.PlayerConfig.UNSTUCK_INTERVAL,1000), start=System.nanoTime();
			final var accepted=new java.util.concurrent.atomic.AtomicBoolean();
			org.l2jmobius.gameserver.model.actor.PlayerNativeWork.run(player,"TEST_COMPLETE_RETURN033R",()->accepted.set(org.l2jmobius.gameserver.handler.UserCommandHandler.getInstance().getHandler(52).onCommand(52,player)));
			PhantomAssertions.assertTrue(accepted.get() && player.isCastingNow(), "Actual ordinary native return must start.");
			final long deadline=start+(timer+5000)*1_000_000L;
			while (player.isCastingNow() && System.nanoTime()<deadline) { Thread.sleep(100); }
			final long elapsed=(System.nanoTime()-start)/1_000_000; final var after=org.l2jmobius.gameserver.phantoms.navigation.PhantomNativeTravelActions.point(player);
			context.record("T13.actualReturn", "nativeTimer="+timer+";elapsedMillis="+elapsed+";before="+before+";after="+after+";incident="+((org.l2jmobius.gameserver.phantoms.player.PhantomNativeWorkScope)player.getNativeWorkOwner()).firstNativeIncident());
			PhantomAssertions.assertTrue(!player.isCastingNow() && !player.isTeleporting() && !after.equals(before) && elapsed>=timer-500 && elapsed<timer+5000, "Stock timer and native random town teleport complete without shortening or manual XYZ.");
			PhantomAssertions.assertEquals(exp,player.getExp(),"Native return grants no EXP."); PhantomAssertions.assertEquals(sp,player.getSp(),"Native return grants no SP.");
			PhantomAssertions.assertEquals(adena,player.getAdena(),"Unstuck charges its actual zero fee."); PhantomAssertions.assertEquals(scrolls,player.getInventory().getInventoryItemCount(736,-1),"Unstuck consumes no scroll.");
			PhantomAssertions.assertEquals(null,((org.l2jmobius.gameserver.phantoms.player.PhantomNativeWorkScope)player.getNativeWorkOwner()).firstNativeIncident(),"Native callback owns each actual mutation.");
		}
	}
	private void strategyBudget(PhantomTestContext context) throws Exception
	{
		try (var fixture = PhantomBackgroundSuite.ProductionAuthorityFixture.start())
		{
			final var travel = org.l2jmobius.gameserver.phantoms.background.PhantomNormalGatekeeperTravel.load(Path.of("data/phantoms/travel/high-five-normal-gk.xml"), fixture.topology());
			final var legacy = travel.route("generated.route.live002.oren.arrival.anchor", "generated.route.live002.gludio.arrival.anchor").orElseThrow();
			PhantomAssertions.assertFalse(legacy.isEmpty(), "Legacy background BFS remains a route query, not a resource selector.");
			final long minute = java.time.Instant.now().toEpochMilli() / 60000;
			long fullFee = 0;
			for (var step : legacy) { if (step.gatekeeper() != null) { fullFee += org.l2jmobius.gameserver.phantoms.background.PhantomNormalGatekeeperTravel.fee(step.gatekeeper(), 1, 80, minute); } }
			context.record("T11.legacy", legacy.stream().map(org.l2jmobius.gameserver.phantoms.background.PhantomNormalGatekeeperTravel.Step::id).toList());
			context.record("T11.nativeFullFee", fullFee);
			PhantomAssertions.assertTrue(fullFee > 0, "Actual current catalog/native tariff must require a fee in this subclass contract.");
			final var nativeLeg = legacy.stream().filter(s -> s.gatekeeper() != null).findFirst().orElseThrow().gatekeeper();
			final var candidate = new PhantomTravelStrategy.Candidate("native-priced-leg", List.of(new PhantomTravelStrategy.Stage(PhantomTravelStrategy.Mode.GK,
				point(nativeLeg.sourceX(),nativeLeg.sourceY(),nativeLeg.sourceZ()), point(nativeLeg.destinationX(),nativeLeg.destinationY(),nativeLeg.destinationZ()), List.of(), nativeLeg, nativeLeg.travelMillis(), fullFee, 0)));
			final var selected = PhantomTravelStrategy.select(List.of(candidate), 0, 0, 120000);
			PhantomAssertions.assertEquals(PhantomTravelStrategy.Status.NO_LAWFUL_PLAN, selected.status(), "Whole priced itinerary rejected before walking to a GK; legacy BFS itself remains unchanged.");
		}
	}
	private void completeCost(PhantomTestContext context)
	{
		// Composed price/resource contract only; these abstract stages grant no native availability.
		final var a = point(0,0,0); final var b = point(1024,0,0); final var c = point(2048,0,0);
		final var walk = new PhantomTravelStrategy.Candidate("walk", List.of(new PhantomTravelStrategy.Stage(PhantomTravelStrategy.Mode.WALK,a,c,List.of(c),null,30000,0,0)));
		final var returning = new PhantomTravelStrategy.Candidate("return-with-onward-proof", List.of(new PhantomTravelStrategy.Stage(PhantomTravelStrategy.Mode.RETURN_GK,a,b,List.of(),null,60000,6,1), new PhantomTravelStrategy.Stage(PhantomTravelStrategy.Mode.WALK,b,c,List.of(c),null,2000,0,0)));
		PhantomAssertions.assertEquals(walk, PhantomTravelStrategy.select(List.of(returning,walk), 10, 1, 120000).candidate(), "Full actual 60-second return plus onward WALK loses to the 30-second complete WALK.");
		final var slowerWalk = new PhantomTravelStrategy.Candidate("slower-walk", List.of(new PhantomTravelStrategy.Stage(PhantomTravelStrategy.Mode.WALK,a,c,List.of(c),null,90000,0,0)));
		PhantomAssertions.assertEquals(returning, PhantomTravelStrategy.select(List.of(slowerWalk,returning),10,1,120000).candidate(), "A supplied lawful complete RETURN candidate may beat longer WALK.");
		PhantomAssertions.assertEquals(slowerWalk, PhantomTravelStrategy.select(List.of(slowerWalk,returning),10,0,120000).candidate(), "No actual scroll cannot authorize that candidate.");
		final var cumulative = new PhantomTravelStrategy.Candidate("two-costs", List.of(new PhantomTravelStrategy.Stage(PhantomTravelStrategy.Mode.RETURN_GK,a,b,List.of(),null,1000,6,1), new PhantomTravelStrategy.Stage(PhantomTravelStrategy.Mode.RETURN_GK,b,c,List.of(),null,1000,6,1)));
		PhantomAssertions.assertEquals(PhantomTravelStrategy.Status.NO_LAWFUL_PLAN, PhantomTravelStrategy.select(List.of(cumulative),10,2,120000).status(), "Per-stage affordability cannot spend twelve with ten.");
		PhantomAssertions.assertEquals(PhantomTravelStrategy.Status.NO_LAWFUL_PLAN, PhantomTravelStrategy.select(List.of(cumulative),12,1,120000).status(), "Scroll constraints accumulate across the whole itinerary too.");
		context.record("T12.abstractContracts", "full-time;total-adena;total-scrolls;stable-id;no-runtime-action");
	}
	private static List<PhantomNavigationPoint> nativeChain(PhantomNavigationPoint from, PhantomNavigationPoint to) throws Exception
	{
		final var alloc = PathFinding.class.getDeclaredMethod("alloc", int.class);
		alloc.setAccessible(true);
		final int gx = GeoEngine.getGeoX(from.x()), gy = GeoEngine.getGeoY(from.y());
		final int tx = GeoEngine.getGeoX(to.x()), ty = GeoEngine.getGeoY(to.y());
		final var buffer = (NodeBuffer) alloc.invoke(PathFinding.getInstance(), 64 + 2 * Math.max(Math.abs(gx - tx), Math.abs(gy - ty)));
		PhantomAssertions.assertTrue(buffer != null, "The test query must fit the unchanged native buffer.");
		try
		{
			GeoNode node = buffer.findPath(gx, gy, from.z(), tx, ty, to.z());
			final List<PhantomNavigationPoint> points = new ArrayList<>();
			while (node != null && points.size() <= 7000)
			{
				final var point = node.getLocation();
				points.addFirst(new PhantomNavigationPoint(point.getX(), point.getY(), point.getZ(), from.instanceId()));
				node = node.getParent();
			}
			PhantomAssertions.assertTrue(node == null, "Parent-chain witness must stay inside the native search bound.");
			return List.copyOf(points);
		}
		finally { buffer.free(); }
	}
}
