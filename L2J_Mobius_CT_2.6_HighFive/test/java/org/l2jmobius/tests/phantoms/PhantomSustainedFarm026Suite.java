/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;

import org.l2jmobius.commons.threads.ThreadPool;
import org.l2jmobius.gameserver.data.xml.NpcData;
import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.managers.InstanceManager;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.instance.Monster;
import org.l2jmobius.gameserver.model.spawns.Spawn;
import org.l2jmobius.gameserver.phantoms.PhantomMetrics;
import org.l2jmobius.gameserver.phantoms.activity.PhantomRelevanceSignal;
import org.l2jmobius.gameserver.phantoms.background.*;
import org.l2jmobius.gameserver.phantoms.navigation.*;
import org.l2jmobius.gameserver.phantoms.topology.PhantomRelevanceSignalPort;

/** Owned native fixtures catch stand-point failure being promoted to a permanent farm veto. */
public final class PhantomSustainedFarm026Suite implements PhantomTestSuite
{
	private final PhantomNativeContextHandoffSuite _handoff = new PhantomNativeContextHandoffSuite();
	public static void main(String[] args)
	{
		System.exit(PhantomTestLauncher.runSuite("sustained-farm026", new PhantomSustainedFarm026Suite(), new PhantomTestContext(26002601, Path.of(args[0]), Path.of(args[1]))));
	}
	@Override public String id() { return "sustained-farm026"; }
	@Override public void beforeAll(PhantomTestContext context) throws Exception { _handoff.beforeAll(context); }
	@Override public void afterAll(PhantomTestContext context) throws Exception { _handoff.afterAll(context); }
	@Override public void register(PhantomTestRegistry registry)
	{
		registry.add("F10-native-z-local-opportunity-precedes-exact-standpoint", context -> local(context, false, true));
		registry.add("F01-fresh-independent-local-opportunity-after-terminal-standpoint", context -> local(context, true, true));
		registry.add("F02-terminal-route-without-lawful-independent-target-remains-closed", context -> local(context, true, false));
	}
	private PhantomBackgroundSuite.ProductionAuthorityFixture production() throws Exception
	{
		final var field = PhantomNativeContextHandoffSuite.class.getDeclaredField("_production"); field.setAccessible(true);
		return (PhantomBackgroundSuite.ProductionAuthorityFixture) field.get(_handoff);
	}
	private static PhantomRelevanceSignalPort signals()
	{
		return new PhantomRelevanceSignalPort()
		{
			@Override public SignalDelivery submit(long id, PhantomRelevanceSignal signal) { return SignalDelivery.ACCEPTED; }
			@Override public SignalDelivery withdraw(long id, String source, long sequence) { return SignalDelivery.ACCEPTED; }
		};
	}
	private static Monster monster(Player player, int npcId, int instanceId) throws Exception
	{
		final var monster = new Monster(NpcData.getInstance().getTemplate(npcId));
		monster.setInstanceId(instanceId);
		PhantomAssertions.assertEquals(instanceId, monster.getInstanceId(), "Factual NPC instance setup must succeed.");
		final var spawn = new Spawn(monster.getTemplate()); spawn.setXYZ(player.getX() + 48, player.getY(), player.getZ()); monster.setSpawn(spawn);
		monster.setCurrentHpMp(monster.getMaxHp(), monster.getMaxMp()); monster.spawnMe(spawn.getX(), spawn.getY(), spawn.getZ());
		return monster;
	}
	private void local(PhantomTestContext context, boolean terminal, boolean lawful) throws Exception
	{
		final var production = production();
		try (var f = _handoff.new Fixture(true))
		{
			f.handoff();
			final var goal = f.goals.load(f.id).orElseThrow().goal();
			final var spec = PhantomBackgroundGoalSpec.parse(goal);
			final var anchor = production.topology().findAnchor(spec.anchorId()).orElseThrow();
			final var area = production.topology().findNode(anchor.nodeId()).orElseThrow().area();
			final Player player = World.getInstance().getPlayer(f.objectId);
			final var clock = new AtomicLong(System.nanoTime());
			final var navigation = new PhantomNavigationService(PhantomNavigationPolicy.productionDefaults(), new L2jNavigationBackend(), worker -> { ThreadPool.execute(worker); return true; }, System::nanoTime, new PhantomMetrics());
			PhantomAssertions.assertTrue(navigation.start(), "Original navigation starts.");
			final var travel = new PhantomVisibleFarmTravel(f.materialization, f.background, production.authority().travelQuery(production.topology()), navigation, f.historical::permitsDecision, signals(), (_id, failure) -> context.record("F.failure", failure.toString()), clock::get);
			Monster target = null;
			final int foreignInstance = lawful ? 0 : InstanceManager.getInstance().createDynamicInstance(0).getId();
			try
			{
				if (terminal)
				{
					try (var action = f.materialization.tryAcquireAction(f.id).orElseThrow()) { player.stopMove(null); player.setXYZ(area.minX() - 64, anchor.point().y(), anchor.point().z()); }
					PhantomAssertions.assertFalse(travel.arrive(f.id, goal), "Controlled TEST route starts outside local area.");
					clock.addAndGet(navigation.policy().maximumAttemptDurationNanos() + 1);
					PhantomAssertions.assertFalse(travel.arrive(f.id, goal), "Stand-point deadline closes that exact route.");
					PhantomAssertions.assertTrue(travel.lastFailure(f.id) != null, "Actual terminal witness retained.");
				}
				final int x = anchor.point().x() + 32, y = anchor.point().y();
				final int geoZ = GeoEngine.getInstance().getHeight(x, y, anchor.point().z());
				try (var action = f.materialization.tryAcquireAction(f.id).orElseThrow()) { player.stopMove(null); player.setXYZ(x, y, geoZ + (terminal ? 0 : 15)); }
				// Stock infrastructure also spawns real monsters. Keep them, but scope this probe to its fresh witness.
				final var priorWitnesses = World.getInstance().getVisibleObjectsInRange(player, Monster.class, 1400).stream().filter(candidate -> candidate.getId() == spec.npcId())
					.map(candidate -> "live.approach." + anchor.id() + "@" + candidate.getX() + ":" + candidate.getY() + ":" + GeoEngine.getInstance().getHeight(candidate.getX(), candidate.getY(), candidate.getZ())).collect(java.util.stream.Collectors.toUnmodifiableSet());
				travel.bindRouteExclusions(_ -> priorWitnesses);
				context.record("F.priorWitnesses." + terminal + "." + lawful, priorWitnesses.toString());
				target = monster(player, spec.npcId(), lawful ? player.getInstanceId() : foreignInstance);
				context.record("F.census." + terminal + "." + lawful, World.getInstance().getVisibleObjectsInRange(player, Monster.class, 1400).stream().filter(candidate -> candidate.getId() == spec.npcId()).map(candidate -> candidate.getObjectId() + ":instance=" + candidate.getInstanceId() + ":xyz=" + candidate.getX() + "," + candidate.getY() + "," + candidate.getZ()).toList().toString());
				context.record("F.nativeWitness", "origin=" + player.getX() + "," + player.getY() + "," + player.getZ() + ";geo=" + geoZ + ";target=" + target.getX() + "," + target.getY() + "," + target.getZ() + ";nativePath=" + GeoEngine.getInstance().canMoveToTarget(player, target));
				PhantomAssertions.assertTrue(L2jPhantomBackgroundAuthority.livePositionAllowed(production.topology(), player, anchor), "Independent actual position remains inside original FARM.");
				if (lawful)
				{
					PhantomAssertions.assertTrue(GeoEngine.getInstance().canMoveToTarget(player, target) && GeoEngine.getInstance().canSeeTarget(player, target), "Factual native reachable/visible Monster witness.");
					PhantomAssertions.assertEquals(PhantomVisibleFarmTravel.ArrivalKind.ARRIVED, travel.observeArrival(f.id, goal).kind(), "RED: fresh lawful native local opportunity must execute without exact stand-point or old terminal route veto.");
					PhantomAssertions.assertEquals(goal, f.goals.load(f.id).orElseThrow().goal(), "Opportunity cannot reset durable goal/revision.");
					PhantomAssertions.assertEquals(f.loadedEpoch, player.getNativeWorkOwner().epoch(), "Opportunity cannot replace native epoch.");
				}
				else { PhantomAssertions.assertEquals(PhantomVisibleFarmTravel.ArrivalKind.TERMINAL, travel.observeArrival(f.id, goal).kind(), "Foreign-instance NPC cannot reopen failed route."); }
			}
			finally
			{
				if (target != null) { target.deleteMe(); }
				if (foreignInstance != 0) { InstanceManager.getInstance().destroyInstance(foreignInstance); }
				travel.beforeMaterialize(f.id, f.objectId); navigation.beginStop(); PhantomAssertions.assertTrue(navigation.finishStop(), "Navigation drained.");
			}
		}
	}
}
