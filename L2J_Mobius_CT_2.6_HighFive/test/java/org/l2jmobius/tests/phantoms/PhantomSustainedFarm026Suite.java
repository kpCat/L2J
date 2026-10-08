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
		if ("generation026".equals(System.getProperty("phantom.m1.native.focus")))
		{
			registry.add("E03-native-respawn-selection-retires-only-undamaged-stale-generations", context -> generations(context, true));
			registry.add("E03-distinct-native-target-cap-remains-fenced", context -> generations(context, false)); return;
		}
		if ("continuation026".equals(System.getProperty("phantom.m1.native.focus")))
		{
			registry.add("F03-existing-exact-native-session-survives-planner-reentry", this::continuation); return;
		}
		if ("resource026".equals(System.getProperty("phantom.m1.native.focus")))
		{
			registry.add("F05-stock-low-MP-starts-bounded-native-rest", context -> resources(context, false));
			registry.add("F07-generic-repair-preserves-original-published-cast", context -> resources(context, true));
			return;
		}
		registry.add("F10-native-z-local-opportunity-precedes-exact-standpoint", context -> local(context, false, true));
		registry.add("F01-fresh-independent-local-opportunity-after-terminal-standpoint", context -> local(context, true, true));
		registry.add("F02-terminal-route-without-lawful-independent-target-remains-closed", context -> local(context, true, false));
	}
	private void generations(PhantomTestContext context, boolean reused) throws Exception
	{
		try (var f = _handoff.new Fixture(true))
		{
			f.handoff(); final Player player = World.getInstance().getPlayer(f.objectId);
			final var owner = player.getNativeWorkOwner(); final long experience = player.getExp(), points = player.getSp();
			final var monsters = new java.util.ArrayList<Monster>();
			Monster target = null;
			try
			{
				for (int index = 0; index <= org.l2jmobius.gameserver.model.actor.PlayerNativeEvidence.MAX_TARGETS; index++)
				{
					if (!reused || target == null) { target = monster(player, 20534, player.getInstanceId()); monsters.add(target); }
					else { target.deleteMe(); target.spawnMe(player.getX() + 24, player.getY(), player.getZ()); }
					final Monster selected = target;
					org.l2jmobius.gameserver.model.actor.PlayerNativeWork.run(player, java.util.List.of(selected), "TEST026_ORIGINAL_NATIVE_SELECTION", () -> { player.setTarget(null); player.setTarget(selected); });
					if (reused) { PhantomAssertions.assertEquals(index + 1L, target.getNativeEvidenceTarget().spawnGeneration(), "Actual onSpawn creates the next native generation."); }
				}
				final var evidence = owner.evidence().snapshot();
				context.record("E03.nativeGenerations." + reused, evidence);
				PhantomAssertions.assertTrue(evidence.targetSequence() >= org.l2jmobius.gameserver.model.actor.PlayerNativeEvidence.MAX_TARGETS, "Each fixture performs actual original selection transitions; repeated same-object no-op is not a native proof.");
				PhantomAssertions.assertEquals(!reused, evidence.overflow(), "RED: expired undamaged incarnations must not consume the unfinished-target cap; distinct targets must still overflow.");
				PhantomAssertions.assertEquals(reused ? org.l2jmobius.gameserver.model.actor.PlayerNativeEvidence.UnprovenReason.NONE : org.l2jmobius.gameserver.model.actor.PlayerNativeEvidence.UnprovenReason.TARGET_CAP, evidence.firstUnprovenReason(), "The genuine distinct-target first cause stays unchanged.");
				PhantomAssertions.assertTrue(evidence.damageSequence() == 0 && evidence.killSequence() == 0 && evidence.rewardSequence() == 0 && evidence.farmCycleSequence() == 0 && player.getExp() == experience && player.getSp() == points, "Selection/respawn cannot invent native damage, death, cycles or rewards.");
			}
			finally { for (Monster monster : monsters) { monster.abortAttack(); monster.abortCast(); monster.deleteMe(); } }
		}
	}
	private void resources(PhantomTestContext context, boolean inFlight) throws Exception
	{
		final var field = PhantomNativeContextHandoffSuite.class.getDeclaredField("_environment"); field.setAccessible(true);
		final var environment = (PhantomHeadlessPlayerTestEnvironment) field.get(_handoff);
		try (var connection = org.l2jmobius.commons.database.DatabaseFactory.getConnection(); var statement = connection.prepareStatement("UPDATE characters SET classid=25,base_class=25,race=1,level=7,exp=?,online=0 WHERE charId=?"))
		{
			statement.setLong(1, org.l2jmobius.gameserver.data.xml.ExperienceData.getInstance().getExpForLevel(7)); statement.setInt(2, environment.primary().objectId());
			PhantomAssertions.assertEquals(1, statement.executeUpdate(), "Guard-owned mage setup before native baseline.");
		}
		final Player setup = Player.load(environment.primary().objectId());
		try
		{
			final var learn = org.l2jmobius.gameserver.data.xml.SkillTreeData.getInstance().getCompleteClassSkillTree(setup.getPlayerClass()).values().stream().filter(entry -> entry.getSkillId() == 1177 && entry.getSkillLevel() == 1 && entry.getGetLevel() <= setup.getLevel()).findFirst().orElseThrow();
			setup.addSkill(org.l2jmobius.gameserver.data.xml.SkillData.getInstance().getSkill(learn.getSkillId(), learn.getSkillLevel()), true);
			setup.setCurrentHp(setup.getMaxHp()); setup.setCurrentMp(inFlight ? setup.getMaxMp() : 1); setup.storeMe();
		}
		finally { environment.cleanupLoadedPlayer(setup); }
		org.l2jmobius.gameserver.scripting.ScriptEngine.getInstance().executeScript(org.l2jmobius.gameserver.scripting.ScriptEngine.MASTER_HANDLER_FILE);
		try (var f = _handoff.new Fixture(true))
		{
			f.handoff(); final var engine = PhantomVisibleIntentRecoverySuite.engine(f);
			final var clock = new AtomicLong(System.nanoTime());
			final var adapter = new PhantomVisibleAutoPlay(f.materialization, () -> engine, f.historical::permitsDecision, inFlight ? clock::get : System::nanoTime);
			final Player player = World.getInstance().getPlayer(f.objectId);
			final var goal = f.goals.load(f.id).orElseThrow().goal();
			final var skill = player.getKnownSkill(1177);
			Monster target = null;
			try
			{
				if (!inFlight)
				{
					target = monster(player, PhantomBackgroundGoalSpec.parse(goal).npcId(), player.getInstanceId());
					final double nativeHp = target.getCurrentHp();
					final var nativeTargets = World.getInstance().getVisibleObjectsInRange(player, Monster.class, 1400).stream()
						.filter(candidate -> candidate.getId() == PhantomBackgroundGoalSpec.parse(goal).npcId() && candidate.getInstanceId() == player.getInstanceId())
						.collect(java.util.stream.Collectors.toMap(candidate -> candidate, Monster::getCurrentHp));
					final long beforeDamage = player.getNativeWorkOwner().evidence().snapshot().damageSequence();
					PhantomAssertions.assertTrue(player.getCurrentMp() < skill.getMpInitialConsume() + skill.getMpConsume(), "F05 actual MP is below a legal original offensive cast cost.");
					PhantomAssertions.assertTrue(adapter.start(f.id, goal), "Exact original pair starts.");
					adapter.noTargetExpired(f.id, goal);
					context.record("F05.native", "mp=" + player.getCurrentMp() + ";cost=" + (skill.getMpInitialConsume() + skill.getMpConsume()) + ";sitting=" + player.isSitting() + ";owner=" + player.getNativeWorkOwner().epoch());
					PhantomAssertions.assertTrue(player.isSitting(), "RED: factual low MP must choose bounded native rest, not generic abort/idle.");
					try { await(55_000, () -> player.getNativeWorkOwner().evidence().snapshot().damageSequence() > beforeDamage && nativeTargets.entrySet().stream().anyMatch(entry -> entry.getKey().getCurrentHp() < entry.getValue()), "Native regeneration, stand and unchanged stock AutoUse must produce a real HP write and exact damage evidence on an originally observed target."); }
					finally
					{
						context.record("F05.recoveryBoundary", "mp=" + player.getCurrentMp() + ";maxMp=" + player.getMaxMp() + ";sitting=" + player.isSitting() + ";online=" + player.isOnline() + ";dead=" + player.isDead() + ";target=" + player.getTarget() + ";scope=" + ((org.l2jmobius.gameserver.phantoms.player.PhantomNativeWorkScope) player.getNativeWorkOwner()).snapshot() + ";evidence=" + player.getNativeWorkOwner().evidence().snapshot() + ";policy=" + adapter.snapshotContinuation(f.id).scalarMap());
					}
					context.record("F05.nextNativeHit", "mp=" + player.getCurrentMp() + ";hpBefore=" + nativeHp + ";hpAfter=" + target.getCurrentHp() + ";sitting=" + player.isSitting() + ";epoch=" + player.getNativeWorkOwner().epoch());
					PhantomAssertions.assertEquals(f.loadedEpoch, player.getNativeWorkOwner().epoch(), "Recovery retains the original epoch.");
				}
				else
				{
					final var gateType = Class.forName("org.l2jmobius.gameserver.phantoms.player.PhantomM1DynamicRecipientChecks$WorkerGate");
					final var constructor = gateType.getDeclaredConstructor(); constructor.setAccessible(true);
					final var acquire = gateType.getDeclaredMethod("acquire"); acquire.setAccessible(true);
					final var release = gateType.getDeclaredMethod("release"); release.setAccessible(true);
					try (var gate = (AutoCloseable) constructor.newInstance())
					{
						acquire.invoke(gate);
						target = monster(player, PhantomBackgroundGoalSpec.parse(goal).npcId(), player.getInstanceId());
						PhantomAssertions.assertTrue(adapter.start(f.id, goal), "Exact pair starts before original paid cast.");
						try (var action = f.materialization.tryAcquireAction(f.id).orElseThrow()) { player.setTarget(target); player.doCast(skill); }
						PhantomAssertions.assertTrue(player.isCastingNow() && ((org.l2jmobius.gameserver.phantoms.player.PhantomNativeWorkScope) player.getNativeWorkOwner()).outstanding() > 0, "Original native cast has a counted published task.");
						clock.addAndGet(31_000_000_000L); adapter.noTargetExpired(f.id, goal);
						context.record("F07.native", "casting=" + player.isCastingNow() + ";scope=" + ((org.l2jmobius.gameserver.phantoms.player.PhantomNativeWorkScope) player.getNativeWorkOwner()).snapshot());
						PhantomAssertions.assertTrue(player.isCastingNow(), "RED: generic 30s repair must not abort a published original native cast.");
						release.invoke(gate);
						final Monster originalTarget = target;
						final double beforeNativeHit = target.getCurrentHp();
						await(12_000, () -> originalTarget.getCurrentHp() < beforeNativeHit, "Preserved original published cast must execute its native damage body.");
						context.record("F07.originalNativeHit", "hpBefore=" + beforeNativeHit + ";hpAfter=" + target.getCurrentHp());
					}
				}
			}
			finally { adapter.stop(f.id); if (target != null) { target.deleteMe(); } PhantomVisibleIntentRecoverySuite.stop(engine); }
		}
	}
	private static void await(long millis, java.util.function.BooleanSupplier condition, String message) throws Exception
	{
		final long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(millis);
		while (!condition.getAsBoolean() && System.nanoTime() < deadline) { Thread.sleep(10); }
		PhantomAssertions.assertTrue(condition.getAsBoolean(), message);
	}
	private PhantomBackgroundSuite.ProductionAuthorityFixture production() throws Exception
	{
		final var field = PhantomNativeContextHandoffSuite.class.getDeclaredField("_production"); field.setAccessible(true);
		return (PhantomBackgroundSuite.ProductionAuthorityFixture) field.get(_handoff);
	}
	private void continuation(PhantomTestContext context) throws Exception
	{
		final var production = production();
		try (var f = _handoff.new Fixture(true))
		{
			f.handoff(); final var engine = PhantomVisibleIntentRecoverySuite.engine(f);
			final var goal = f.goals.load(f.id).orElseThrow().goal(); final var spec = PhantomBackgroundGoalSpec.parse(goal);
			final var anchor = production.topology().findAnchor(spec.anchorId()).orElseThrow();
			final var player = World.getInstance().getPlayer(f.objectId);
			final var navigation = new PhantomNavigationService(PhantomNavigationPolicy.productionDefaults(), new L2jNavigationBackend(), worker -> { ThreadPool.execute(worker); return true; }, System::nanoTime, new PhantomMetrics());
			PhantomAssertions.assertTrue(navigation.start(), "Existing native navigation.");
			final var travel = new PhantomVisibleFarmTravel(f.materialization, f.background, production.authority().travelQuery(production.topology()), navigation, f.historical::permitsDecision, signals(), f.historical::recordVisibleTravelFailure, System::nanoTime);
			final var autoPlay = new PhantomVisibleAutoPlay(f.materialization, () -> engine, f.historical::permitsDecision);
			try
			{
				final int x = anchor.point().x() + 32, y = anchor.point().y(), z = GeoEngine.getInstance().getHeight(x, y, anchor.point().z()) + 15;
				try (var action = f.materialization.tryAcquireAction(f.id).orElseThrow()) { player.stopMove(null); player.setXYZ(x, y, z); }
				final var priorWitnesses = World.getInstance().getVisibleObjectsInRange(player, Monster.class, 1400).stream().filter(candidate -> candidate.getId() == spec.npcId())
					.map(candidate -> "live.approach." + anchor.id() + "@" + candidate.getX() + ":" + candidate.getY() + ":" + GeoEngine.getInstance().getHeight(candidate.getX(), candidate.getY(), candidate.getZ())).collect(java.util.stream.Collectors.toUnmodifiableSet());
				final var adapter = PhantomBackgroundDecision.bindVisibleLife(f.background, travel, autoPlay, f.historical, () -> engine);
				travel.bindRouteExclusions(_ -> priorWitnesses);
				final var type = Class.forName("org.l2jmobius.gameserver.phantoms.player.PhantomM1DynamicRecipientChecks$WorkerGate"); final var constructor = type.getDeclaredConstructor(); constructor.setAccessible(true);
				final var acquire = type.getDeclaredMethod("acquire"); acquire.setAccessible(true); final var release = type.getDeclaredMethod("release"); release.setAccessible(true);
				try (var gate = (AutoCloseable) constructor.newInstance())
				{
					acquire.invoke(gate);
					PhantomAssertions.assertTrue(autoPlay.start(f.id, goal) && autoPlay.running(f.id, goal), "Exact native session exists before planner reentry.");
					final var before = autoPlay.snapshotContinuation(f.id).scalarMap();
					final var field = PhantomBackgroundDecision.class.getDeclaredField("_typedVisibleStart"); field.setAccessible(true);
					@SuppressWarnings("unchecked") final var start = (java.util.function.BiFunction<Long, org.l2jmobius.gameserver.phantoms.decision.PhantomGoal, org.l2jmobius.gameserver.phantoms.decision.PhantomStepResult>) field.get(adapter);
					final var result = start.apply(f.id, goal);
					context.record("F03.reentry", "result=" + result + ";nativeZ=" + player.getZ() + ";before=" + before + ";after=" + autoPlay.snapshotContinuation(f.id).scalarMap());
					PhantomAssertions.assertEquals(org.l2jmobius.gameserver.phantoms.decision.PhantomStepResult.Type.SUCCESS, result.type(), "RED: planner reentry must reuse a healthy exact session without a new stand-point acquisition.");
					PhantomAssertions.assertTrue(autoPlay.running(f.id, goal), "Original registrations remain current.");
					PhantomAssertions.assertEquals(before.get("livePolicyIdentity"), autoPlay.snapshotContinuation(f.id).scalarMap().get("livePolicyIdentity"), "No replacement policy or farm debt reset.");
					release.invoke(gate);
				}
			}
			finally { autoPlay.stop(f.id); travel.beforeMaterialize(f.id, f.objectId); navigation.beginStop(); navigation.finishStop(); PhantomVisibleIntentRecoverySuite.stop(engine); }
		}
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
