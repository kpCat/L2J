/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.l2jmobius.commons.threads.ThreadPool;
import org.l2jmobius.gameserver.data.xml.NpcData;
import org.l2jmobius.gameserver.data.xml.SkillData;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.instance.Monster;
import org.l2jmobius.gameserver.model.spawns.Spawn;
import org.l2jmobius.gameserver.phantoms.PhantomMetrics;
import org.l2jmobius.gameserver.phantoms.activity.*;
import org.l2jmobius.gameserver.phantoms.background.*;
import org.l2jmobius.gameserver.phantoms.decision.*;
import org.l2jmobius.gameserver.phantoms.navigation.*;
import org.l2jmobius.gameserver.phantoms.topology.*;

/** TEST-only scene; production binding, timer pools and native reward writers do the work. */
public final class PhantomNativeFarmContinuation022Suite implements PhantomTestSuite
{
	final PhantomNativeContextHandoffSuite handoff = new PhantomNativeContextHandoffSuite();
	public static void main(String[] args)
	{
		System.exit(PhantomTestLauncher.runSuite("native-farm-continuation022", new PhantomNativeFarmContinuation022Suite(), new PhantomTestContext(22002201, Path.of(args[0]), Path.of(args[1]))));
	}
	@Override public String id() { return "native-farm-continuation022"; }
	@Override public void beforeAll(PhantomTestContext context) throws Exception
	{
		handoff.beforeAll(context);
		org.l2jmobius.gameserver.scripting.ScriptEngine.getInstance().executeScript(org.l2jmobius.gameserver.scripting.ScriptEngine.MASTER_HANDLER_FILE);
		PhantomAssertions.assertTrue(org.l2jmobius.gameserver.handler.TargetHandler.getInstance().size() > 0, "Actual stock target handlers initialized, as in native production fixture.");
	}
	@Override public void afterAll(PhantomTestContext context) throws Exception { handoff.afterAll(context); }
	@Override public void register(PhantomTestRegistry registry)
	{
		final String focus = System.getProperty("phantom023.nativeFarmFocus", "all");
		if (focus.equals("contract024"))
		{
			registry.add("D02-real-arrival-precedes-old-nonterminal-journey-deadline", this::arrival024);
			registry.add("D03-one-local-route-failure-does-not-exclude-entire-farm", this::routeScope024);
			return;
		}
		if (focus.equals("long-range")) { registry.add("N01-stock-long-range-native-selection-and-five-cycles", context -> composed(context, false, true)); return; }
		if (focus.equals("fighter")) { registry.add("N01-production-native-fighter-five-next-targets", context -> composed(context, true)); return; }
		if (focus.equals("mage")) { registry.add("S02-production-binding-native-cast-reward-five-next-targets", this::composed); return; }
		if (focus.equals("ordinary")) { registry.add("S05-S06-real-like-stock-control-continues-in-same-pools-after-revocation", this::realControl); return; }
		registry.add("S11-native-cast-ignores-unearned-hate-only-recipient", this::hateOnlyRecipient);
		registry.add("S12-fresh-real-native-damage-recipient-and-delayed-fence", context -> recipient(context, true));
		registry.add("S13-tutorial-missing-state-and-existing-state-control", this::tutorialState);
		if (Boolean.getBoolean("phantom022.round3") || focus.equals("raw")) { return; }
		registry.add("S02-production-binding-native-cast-reward-five-next-targets", this::composed);
		registry.add("N01-production-native-fighter-five-next-targets", context -> composed(context, true));
		registry.add("S05-S06-real-like-stock-control-continues-in-same-pools-after-revocation", this::realControl);
	}
	private PhantomHeadlessPlayerTestEnvironment environment() throws Exception
	{
		final var field = PhantomNativeContextHandoffSuite.class.getDeclaredField("_environment"); field.setAccessible(true);
		return (PhantomHeadlessPlayerTestEnvironment) field.get(handoff);
	}
	private PhantomBackgroundSuite.ProductionAuthorityFixture production024() throws Exception
	{
		final var field = PhantomNativeContextHandoffSuite.class.getDeclaredField("_production"); field.setAccessible(true);
		return (PhantomBackgroundSuite.ProductionAuthorityFixture) field.get(handoff);
	}
	private static PhantomRelevanceSignalPort signals024()
	{
		return new PhantomRelevanceSignalPort()
		{
			@Override public SignalDelivery submit(long id, PhantomRelevanceSignal signal) { return SignalDelivery.ACCEPTED; }
			@Override public SignalDelivery withdraw(long id, String source, long sequence) { return SignalDelivery.ACCEPTED; }
		};
	}
	private void arrival024(PhantomTestContext context) throws Exception
	{
		final var production = production024();
		try (var f = handoff.new Fixture(true))
		{
			f.handoff();
			final var goal = f.goals.load(f.id).orElseThrow().goal();
			final var anchor = production.topology().findAnchor(PhantomBackgroundGoalSpec.parse(goal).anchorId()).orElseThrow();
			final var area = production.topology().findNode(anchor.nodeId()).orElseThrow().area();
			final var player = World.getInstance().getPlayer(f.objectId);
			final var clock = new AtomicLong(System.nanoTime());
			final var navigation = new PhantomNavigationService(PhantomNavigationPolicy.productionDefaults(), new L2jNavigationBackend(), worker -> { ThreadPool.execute(worker); return true; }, System::nanoTime, new PhantomMetrics());
			PhantomAssertions.assertTrue(navigation.start(), "D02 original navigation started.");
			final var travel = new PhantomVisibleFarmTravel(f.materialization, f.background, production.authority().travelQuery(production.topology()), navigation, f.historical::permitsDecision, signals024(), (_id, failure) -> context.record("D02.failure", failure.toString()), clock::get);
			try
			{
				try (var action = f.materialization.tryAcquireAction(f.id).orElseThrow())
				{
					player.stopMove(null); player.setXYZ(area.minX() - 64, anchor.point().y(), anchor.point().z());
				}
				PhantomAssertions.assertFalse(L2jPhantomBackgroundAuthority.livePositionAllowed(production.topology(), player, anchor), "D02 controlled outside-area initial point.");
				PhantomAssertions.assertFalse(travel.arrive(f.id, goal), "D02 nonterminal journey starts before arrival.");
				PhantomAssertions.assertEquals(null, travel.lastFailure(f.id), "INVALID D02: initial attempt cannot already be terminal.");
				try (var action = f.materialization.tryAcquireAction(f.id).orElseThrow())
				{
					player.stopMove(null); player.setXYZ(anchor.point().x() + 32, anchor.point().y(), org.l2jmobius.gameserver.geoengine.GeoEngine.getInstance().getHeight(anchor.point().x() + 32, anchor.point().y(), anchor.point().z()));
				}
				PhantomAssertions.assertTrue(L2jPhantomBackgroundAuthority.livePositionAllowed(production.topology(), player, anchor), "D02 actual grounded area arrival fixture.");
				clock.addAndGet(navigation.policy().maximumAttemptDurationNanos() + 1);
				PhantomAssertions.assertTrue(travel.arrive(f.id, goal), "D02 useful native arrival must precede deadline of an older nonterminal journey.");
				PhantomAssertions.assertEquals(null, travel.lastFailure(f.id), "D02 no invented deadline failure after actual arrival.");
			}
			finally { travel.beforeMaterialize(f.id, f.objectId); navigation.beginStop(); PhantomAssertions.assertTrue(navigation.finishStop(), "D02 navigation drained."); }
		}
	}
	private void routeScope024(PhantomTestContext context) throws Exception
	{
		try (var f = handoff.new Fixture(true))
		{
			f.handoff(); final var engine = PhantomVisibleIntentRecoverySuite.engine(f);
			try
			{
				final var goal = f.goals.load(f.id).orElseThrow().goal();
				final var player = World.getInstance().getPlayer(f.objectId);
				final var spec = PhantomBackgroundGoalSpec.parse(goal);
				final String witness = "live.approach." + spec.anchorId() + "@" + player.getX() + ":" + player.getY() + ":" + player.getZ();
				PhantomAssertions.assertTrue(f.historical.recordVisibleTravelFailure(f.id, new PhantomVisibleFarmTravel.Failure(f.id, player, f.loadedEpoch, goal, witness, "travel.navigation_route_obstructed", PhantomVisibleFarmTravel.Disposition.ROUTE_UNUSABLE, System.nanoTime(), 1)), "D03 exact controlled failed route admitted.");
				PhantomAssertions.assertTrue(f.historical.prepareVisibleDecision(f.id, engine), "D03 bounded local replan must remain possible after one route failure.");
				final var next = f.goals.load(f.id).orElseThrow().goal();
				PhantomAssertions.assertEquals(goal.revision() + 1, next.revision(), "D03 failure requires new plan identity, not resetting terminal attempt.");
				PhantomAssertions.assertEquals(spec.anchorId(), PhantomBackgroundGoalSpec.parse(next).anchorId(), "D03 one waypoint cannot blacklist the entire still-suitable FARM area.");
				context.record("D03.routeWitness", witness);
			}
			finally { PhantomVisibleIntentRecoverySuite.stop(engine); }
		}
	}
	private static AutoCloseable nativeLifetime(org.l2jmobius.gameserver.model.actor.Player player) throws Exception
	{
		final var type = Class.forName("org.l2jmobius.gameserver.phantoms.player.PhantomM1DynamicRecipientChecks$NativeLifetime");
		final var constructor = type.getDeclaredConstructor(org.l2jmobius.gameserver.model.actor.Player.class); constructor.setAccessible(true);
		return (AutoCloseable) constructor.newInstance(player);
	}
	private void hateOnlyRecipient(PhantomTestContext context) throws Exception
	{
		recipient(context, false);
	}
	private void recipient(PhantomTestContext context, boolean earnedRecipient) throws Exception
	{
		for (int objectId : List.of(environment().primary().objectId(), environment().observer().objectId()))
		{
			try (var connection = org.l2jmobius.commons.database.DatabaseFactory.getConnection(); var statement = connection.prepareStatement("UPDATE characters SET classid=10,base_class=10,race=0,level=1,exp=0,online=0 WHERE charId=?"))
			{
				statement.setInt(1, objectId); PhantomAssertions.assertEquals(1, statement.executeUpdate(), "Exact owned ordinary raw TEST mage setup before lifetime.");
			}
		}
		final var first = org.l2jmobius.gameserver.model.actor.Player.load(environment().primary().objectId());
		final var second = org.l2jmobius.gameserver.model.actor.Player.load(environment().observer().objectId());
		final var monster = new Monster(NpcData.getInstance().getTemplate(earnedRecipient ? 20121 : 20534));
		try (var firstOutput = first.attachOutboundSession(new org.l2jmobius.gameserver.phantoms.player.HeadlessPlayerOutboundSession(8, 128));
			var secondOutput = second.attachOutboundSession(new org.l2jmobius.gameserver.phantoms.player.HeadlessPlayerOutboundSession(8, 128)))
		{
			first.stopAllTasks(); second.stopAllTasks();
			first.addSkill(SkillData.getInstance().getSkill(1177, 1), true);
			second.addSkill(SkillData.getInstance().getSkill(1177, 1), true);
			first.setCurrentHp(first.getMaxHp()); first.setCurrentMp(first.getMaxMp());
			first.setOnlineStatus(true, false); second.setOnlineStatus(true, false);
			first.spawnMe(first.getX(), first.getY(), first.getZ()); second.spawnMe(first.getX() + 30, first.getY(), first.getZ());
			final var spawn = new Spawn(monster.getTemplate()); spawn.setXYZ(first.getX() + 40, first.getY(), first.getZ()); monster.setSpawn(spawn);
			monster.setCurrentHpMp(monster.getMaxHp(), monster.getMaxMp()); monster.spawnMe(spawn.getX(), spawn.getY(), spawn.getZ());
			final double hp = monster.getCurrentHp(); final long secondExp = second.getExp(), secondSp = second.getSp();
			if (earnedRecipient)
			{
				monster.disableCoreAI(false);
				org.l2jmobius.gameserver.phantoms.player.PhantomM1DynamicRecipientChecks.lateNativeDamage(context, first, second, monster, first.getKnownSkill(1177));
				return;
			}
			try (var firstLifetime = nativeLifetime(first); var secondLifetime = nativeLifetime(second))
			{
				final var gateType = Class.forName("org.l2jmobius.gameserver.phantoms.player.PhantomM1DynamicRecipientChecks$WorkerGate");
				final var constructor = gateType.getDeclaredConstructor(); constructor.setAccessible(true);
				final var acquire = gateType.getDeclaredMethod("acquire"); acquire.setAccessible(true);
				final var release = gateType.getDeclaredMethod("release"); release.setAccessible(true);
				try (var gate = (AutoCloseable) constructor.newInstance())
				{
					acquire.invoke(gate);
					final var owner = first.getNativeWorkOwner();
					final var ticket = owner.reserve(null, "TEST022_NATIVE_CAST", org.l2jmobius.gameserver.model.actor.PlayerNativeWork.Semantics.CANCELLABLE);
					PhantomAssertions.assertTrue(ticket != null && ticket.tryStart(), "Exact TEST native root admitted.");
					try (var nativeContext = org.l2jmobius.gameserver.model.actor.PlayerNativeWork.enter(ticket))
					{
						first.setTarget(monster); first.doCast(first.getKnownSkill(1177));
					}
					finally { ticket.complete(null); }
					PhantomAssertions.assertTrue(first.isCastingNow(), "Actual stock cast published before recipient change.");
					// Hate-only negative control is not a real HP/EXP participant.
					monster.addDamageHate(second, 0, 1);
					PhantomAssertions.assertEquals(0L, monster.getAggroList().get(second).getDamage(), "Exact hate-only metadata.");
					release.invoke(gate);
				}
				final var scope = (org.l2jmobius.gameserver.phantoms.player.PhantomNativeWorkScope) first.getNativeWorkOwner();
				final long deadline = System.nanoTime() + 15_000_000_000L;
				while (first.isCastingNow() && scope.firstNativeIncident() == null && System.nanoTime() < deadline) { Thread.sleep(10); }
				context.record("S11.native", "recipientMetadata=0;casting=" + first.isCastingNow() + ";hp=" + hp + "→" + monster.getCurrentHp() + ";firstIncident=" + scope.firstNativeIncident());
					PhantomAssertions.assertEquals(null, scope.firstNativeIncident(), "RED: hate-only non-recipient must not poison actual earned cast.");
					PhantomAssertions.assertTrue(!first.isCastingNow() && monster.getCurrentHp() < hp, "Actual native effects and finalizer completed.");
				PhantomAssertions.assertTrue(second.getExp() == secondExp && second.getSp() == secondSp, "Hate-only actor earned no reward.");
			}
		}
		finally
		{
			monster.abortAttack(); monster.abortCast(); monster.deleteMe();
			for (var player : List.of(first, second)) { if (!player.isNativeWorkManaged() && World.getInstance().getPlayer(player.getObjectId()) == player) { environment().cleanupLoadedPlayer(player); } }
		}
	}
	private void tutorialState(PhantomTestContext context) throws Exception
	{
		org.l2jmobius.gameserver.scripting.ScriptEngine.getInstance().executeScript(Path.of("quests/Q00255_Tutorial/Q00255_Tutorial.java"));
		// The single-source executor compiles a constructor-only quest; instantiate it under its exact loading path.
		final var executorField = org.l2jmobius.gameserver.scripting.ScriptEngine.class.getDeclaredField("SCRIPT_EXECUTOR"); executorField.setAccessible(true);
		final var executor = executorField.get(null);
		final var loaderField = executor.getClass().getDeclaredField("SCRIPT_CLASS_LOADER"); loaderField.setAccessible(true);
		final var loadingField = executor.getClass().getDeclaredField("_currentExecutingScript"); loadingField.setAccessible(true);
		final Object previous = loadingField.get(executor);
		try
		{
			loadingField.set(executor, Path.of("data/scripts/quests/Q00255_Tutorial/Q00255_Tutorial.java").toAbsolutePath());
			((ClassLoader) loaderField.get(null)).loadClass("quests.Q00255_Tutorial.Q00255_Tutorial").getDeclaredConstructor().newInstance();
		}
		finally { loadingField.set(executor, previous); }
		final var quest = org.l2jmobius.gameserver.managers.ScriptManager.getInstance().getQuest(255);
		PhantomAssertions.assertTrue(quest != null, "Actual stock Tutorial script loaded.");
		final var player = org.l2jmobius.gameserver.model.actor.Player.load(environment().observer().objectId());
		final var gremlin = new Monster(NpcData.getInstance().getTemplate(18342));
		try
		{
			PhantomAssertions.assertEquals(null, quest.getQuestState(player, false), "Lawful missing QuestState fixture.");
			quest.onKill(gremlin, player, false);
			PhantomAssertions.assertEquals(null, quest.getQuestState(player, false), "No synthetic Tutorial state or reward.");
			final var state = quest.newQuestState(player); state.setState(org.l2jmobius.gameserver.model.script.State.STARTED); state.setMemoStateEx(1, 3);
			quest.onKill(gremlin, player, false);
			PhantomAssertions.assertTrue(quest.getQuestState(player, false) == state && state.getMemoStateEx(1) == 3, "Existing-state stock control preserved.");
			context.record("S13.tutorial", "missing state safe; existing state3 preserved; no fake quest for Phantom");
		}
		finally { gremlin.deleteMe(); environment().cleanupLoadedPlayer(player); }
	}
	private void realControl(PhantomTestContext context) throws Exception
	{
		final var field = PhantomNativeContextHandoffSuite.class.getDeclaredField("_environment"); field.setAccessible(true);
		final var environment = (PhantomHeadlessPlayerTestEnvironment) field.get(handoff);
		final int objectId = environment.observer().objectId();
		try (var connection = org.l2jmobius.commons.database.DatabaseFactory.getConnection(); var statement = connection.prepareStatement("UPDATE characters SET classid=25,base_class=25,race=1,level=7,exp=?,online=0 WHERE charId=?"))
		{
			statement.setLong(1, org.l2jmobius.gameserver.data.xml.ExperienceData.getInstance().getExpForLevel(7)); statement.setInt(2, objectId);
			PhantomAssertions.assertEquals(1, statement.executeUpdate(), "Exact owned REAL-like TEST setup.");
		}
		final var ordinary = org.l2jmobius.gameserver.model.actor.Player.load(objectId);
		final boolean enabled = org.l2jmobius.gameserver.config.custom.AutoPlayConfig.ENABLE_AUTO_PLAY;
		final List<Monster> monsters = new ArrayList<>();
		try (var output = ordinary.attachOutboundSession(new org.l2jmobius.gameserver.phantoms.player.HeadlessPlayerOutboundSession(8, 128)); var f = handoff.new Fixture(true))
		{
			f.handoff(); final var primary = World.getInstance().getPlayer(f.objectId); primary.setSitting(true);
			final var engine = PhantomVisibleIntentRecoverySuite.engine(f);
			final var adapter = new PhantomVisibleAutoPlay(f.materialization, () -> engine, f.historical::permitsDecision);
			try
			{
				ordinary.addSkill(SkillData.getInstance().getSkill(1177, 1), true);
				ordinary.setCurrentHp(ordinary.getMaxHp()); ordinary.setCurrentMp(ordinary.getMaxMp()); ordinary.setOnlineStatus(true, false);
				ordinary.spawnMe(primary.getX() + 400, primary.getY(), primary.getZ());
				PhantomAssertions.assertTrue(ordinary.getNativeWorkOwner() == null && !ordinary.isPhantomAutoPlayManaged(), "REAL-like stock control has no Phantom owner or permission; no fake REAL_LOGIN.");
				ordinary.getAutoUseSettings().getAutoActions().add(2); ordinary.getAutoUseSettings().getAutoSkills().add(1177);
				ordinary.getAutoPlaySettings().setNextTargetMode(1); ordinary.getAutoPlaySettings().setPickup(true); ordinary.getAutoPlaySettings().setShortRange(true);
				for (int i = 0; i < 3; i++)
				{
					final var monster = new Monster(NpcData.getInstance().getTemplate(20534));
					final var spawn = new Spawn(monster.getTemplate()); spawn.setXYZ(ordinary.getX() + 40 + 25 * i, ordinary.getY(), ordinary.getZ()); monster.setSpawn(spawn);
					monster.setCurrentHpMp(monster.getMaxHp(), monster.getMaxMp()); monster.spawnMe(spawn.getX(), spawn.getY(), spawn.getZ()); monsters.add(monster);
				}
				org.l2jmobius.gameserver.config.custom.AutoPlayConfig.ENABLE_AUTO_PLAY = true;
				PhantomAssertions.assertTrue(adapter.start(f.id, f.goals.load(f.id).orElseThrow().goal()), "Native primary joins actual pools.");
				org.l2jmobius.gameserver.taskmanagers.AutoPlayTaskManager.getInstance().startAutoPlay(ordinary);
				org.l2jmobius.gameserver.taskmanagers.AutoUseTaskManager.getInstance().startAutoUseTask(ordinary);
				for (var manager : List.of(org.l2jmobius.gameserver.taskmanagers.AutoPlayTaskManager.class, org.l2jmobius.gameserver.taskmanagers.AutoUseTaskManager.class))
				{
					final var pools = manager.getDeclaredField("POOLS"); pools.setAccessible(true);
					PhantomAssertions.assertTrue(((java.util.Set<?>) pools.get(null)).stream().anyMatch(value -> ((java.util.Set<?>) value).contains(primary) && ((java.util.Set<?>) value).contains(ordinary)), "Both actors occupy the same actual " + manager.getSimpleName() + " pool.");
				}
				final long baseline = ordinary.getExp(); long deadline = System.nanoTime() + 30_000_000_000L;
				while (ordinary.getExp() == baseline && System.nanoTime() < deadline) { Thread.sleep(100); }
				PhantomAssertions.assertTrue(ordinary.getExp() > baseline, "Actual policy-null manager/cast/native reward path reached before revocation.");
				final long atRevoke = ordinary.getExp(); f.historical.revokeForegroundDecisions(); deadline = System.nanoTime() + 30_000_000_000L;
				while (ordinary.getExp() == atRevoke && System.nanoTime() < deadline) { Thread.sleep(100); }
				PhantomAssertions.assertTrue(ordinary.getExp() > atRevoke && ordinary.isAutoPlaying(), "Other actual actor continues to another native kill after primary revocation.");
				PhantomAssertions.assertFalse(adapter.running(f.id, f.goals.load(f.id).orElseThrow().goal()), "Revoked primary is not live running.");
				context.record("S05-S06.control", "REAL_LIKE_TEST_ONLY;no fake login;shared actual pools;EXP=" + baseline + "→" + atRevoke + "→" + ordinary.getExp() + ";SP=" + ordinary.getSp());
			}
			finally { adapter.stop(f.id); primary.setSitting(false); PhantomVisibleIntentRecoverySuite.stop(engine); }
		}
		finally
		{
			org.l2jmobius.gameserver.taskmanagers.AutoUseTaskManager.getInstance().stopAutoUseTask(ordinary);
			org.l2jmobius.gameserver.taskmanagers.AutoPlayTaskManager.getInstance().stopAutoPlay(ordinary);
			org.l2jmobius.gameserver.config.custom.AutoPlayConfig.ENABLE_AUTO_PLAY = enabled;
			for (var monster : monsters) { monster.deleteMe(); }
			environment.cleanupLoadedPlayer(ordinary);
		}
	}

	private void composed(PhantomTestContext context) throws Exception
	{
		composed(context, false);
	}
	private void composed(PhantomTestContext context, boolean fighter) throws Exception
	{
		composed(context, fighter, false);
	}
	private void composed(PhantomTestContext context, boolean fighter, boolean longRange) throws Exception
	{
		final var environmentField = PhantomNativeContextHandoffSuite.class.getDeclaredField("_environment"); environmentField.setAccessible(true);
		final var environment = (PhantomHeadlessPlayerTestEnvironment) environmentField.get(handoff);
		final int objectId = environment.primary().objectId();
		// Same guarded class/level setup as openProductionPlayerFixture, before baseline capture.
		final var fixtureClass = fighter ? org.l2jmobius.gameserver.model.actor.enums.player.PlayerClass.FIGHTER : org.l2jmobius.gameserver.model.actor.enums.player.PlayerClass.ELVEN_MAGE;
		try (var connection = org.l2jmobius.commons.database.DatabaseFactory.getConnection(); var statement = connection.prepareStatement(fighter ? "UPDATE characters SET classid=0,base_class=0,race=0,level=7,exp=?,online=0 WHERE charId=?" : "UPDATE characters SET classid=25,base_class=25,race=1,level=7,exp=?,online=0 WHERE charId=?"))
		{
			statement.setLong(1, org.l2jmobius.gameserver.data.xml.ExperienceData.getInstance().getExpForLevel(7)); statement.setInt(2, objectId);
			PhantomAssertions.assertEquals(1, statement.executeUpdate(), "Exact owned TEST mage setup.");
		}
		final var setup = org.l2jmobius.gameserver.model.actor.Player.load(objectId);
		try
		{
			if (fighter) { setup.removeSkill(1177, true); }
			for (var learn : org.l2jmobius.gameserver.data.xml.SkillTreeData.getInstance().getCompleteClassSkillTree(fixtureClass).values())
			{
				if (learn.isAutoGet() && (learn.getGetLevel() <= 7))
				{
					final var current = setup.getKnownSkill(learn.getSkillId());
					if ((current == null) || (current.getLevel() < learn.getSkillLevel())) { setup.addSkill(SkillData.getInstance().getSkill(learn.getSkillId(), learn.getSkillLevel()), true); }
				}
			}
			setup.setCurrentHp(setup.getMaxHp()); setup.setCurrentMp(setup.getMaxMp()); setup.setCurrentCp(setup.getMaxCp()); setup.storeMe();
		}
		finally { environment.cleanupLoadedPlayer(setup); }
		context.record("S02.TEST_setup." + fighter, "existing guarded owned fixture; class=" + fixtureClass + " level7; native auto-get skills; seven stock Monsters, NPC AI ON; no post-baseline damage/reward injection");
		try (var f = handoff.new Fixture(true))
		{
			f.handoff();
			final var player = World.getInstance().getPlayer(f.objectId);
			final var productionField = PhantomNativeContextHandoffSuite.class.getDeclaredField("_production"); productionField.setAccessible(true);
			final var production = (PhantomBackgroundSuite.ProductionAuthorityFixture) productionField.get(handoff);
			final var anchor = production.topology().findAnchor("population.farming.elf.20534").orElseThrow().point();
			final var geo = org.l2jmobius.gameserver.geoengine.GeoEngine.getInstance();
			final var oldGeo = org.l2jmobius.gameserver.config.GeoEngineConfig.GEODATA_PATH;
			try
			{
				org.l2jmobius.gameserver.config.GeoEngineConfig.GEODATA_PATH = Path.of("C:/Users/ZBook/L2J_Mobius/L2J_Mobius_CT_2.6_HighFive/artifacts/local-play/runtime/game/data/geodata");
				PhantomAssertions.assertTrue(geo.reloadRegion(21, 19), "Retained native geodata required.");
			}
			finally { org.l2jmobius.gameserver.config.GeoEngineConfig.GEODATA_PATH = oldGeo; }
			// Controlled TEST scene and skill setup, before the production driver starts.
			player.setXYZ(anchor.x(), anchor.y(), geo.getHeight(anchor.x(), anchor.y(), anchor.z()));
			final var ref = new AtomicReference<PhantomDecisionEngine>();
			final var autoPlay = new PhantomVisibleAutoPlay(f.materialization, ref::get, f.historical::permitsDecision);
			final var navigation = new PhantomNavigationService(PhantomNavigationPolicy.productionDefaults(), new L2jNavigationBackend(), worker -> { ThreadPool.execute(worker); return true; }, System::nanoTime, new PhantomMetrics());
			navigation.start();
			final PhantomRelevanceSignalPort signals = new PhantomRelevanceSignalPort()
			{
				@Override public SignalDelivery submit(long id, PhantomRelevanceSignal signal) { return SignalDelivery.ACCEPTED; }
				@Override public SignalDelivery withdraw(long id, String source, long sequence) { return SignalDelivery.ACCEPTED; }
			};
			final var travel = new PhantomVisibleFarmTravel(f.materialization, f.background, production.authority().travelQuery(production.topology()), navigation, f.historical::permitsDecision, signals, f.historical::recordVisibleTravelFailure, System::nanoTime);
			final var adapter = PhantomBackgroundDecision.bindVisibleLife(f.background, travel, autoPlay, f.historical, ref::get);
			final var candidates = new PhantomCandidateRegistry(); adapter.registerCandidates(candidates); candidates.seal();
			final var handlers = new PhantomStepHandlerRegistry(); adapter.registerHandlers(handlers); handlers.seal();
			final var engine = new PhantomDecisionEngine(f.goals, candidates, handlers, new PhantomMetrics(), 1, null, f.historical::permitsDecision); ref.set(engine); engine.start(); engine.attach(f.id);
			final List<Monster> monsters = new ArrayList<>();
			final var failures = new AtomicReference<Throwable>();
			final var enters = new AtomicLong(); final var finishes = new AtomicLong();
			java.util.concurrent.ScheduledFuture<?> driver = null;
			long expectedExp = 0; long expectedSp = 0;
			try
			{
				PhantomAssertions.assertTrue(f.historical.prepareVisibleDecision(f.id, engine), "Current native local intent prepared.");
				final var goal = f.goals.load(f.id).orElseThrow().goal();
				final int npcId = PhantomBackgroundGoalSpec.parse(goal).npcId();
				for (int i = 0; i < 7; i++)
				{
					final var monster = new Monster(NpcData.getInstance().getTemplate(npcId));
					PhantomAssertions.assertFalse(monster.isCoreAIDisabled(), "N01 stock NPC AI enabled."); monster.setInstanceId(player.getInstanceId());
					final var spawn = new Spawn(monster.getTemplate()); spawn.setXYZ(player.getX() + (longRange ? 900 : 40) + i * 25, player.getY(), player.getZ()); monster.setSpawn(spawn);
					monster.setCurrentHpMp(monster.getMaxHp(), monster.getMaxMp()); monster.spawnMe(spawn.getX(), spawn.getY(), spawn.getZ()); monsters.add(monster);
				}
				final long exp = player.getExp(); final long sp = player.getSp();
				final long inventoryBefore = player.getInventory().getItems().stream().mapToLong(item -> item.getCount()).sum();
				final var sensor = player.getNativeWorkOwner().evidence(); final var baseline = sensor.snapshot();
				driver = ThreadPool.schedulePriorityTaskAtFixedRate(() ->
				{
					final long tick = enters.incrementAndGet();
					try
					{
						if (f.historical.prepareVisibleDecision(f.id, engine)) { engine.accept(new PhantomActivityWorkItem(f.id, PhantomActivityState.ACTIVE, 1, tick, System.nanoTime(), PhantomActivityOverloadLevel.NORMAL)); }
					}
					catch (Throwable failure) { failures.compareAndSet(null, failure); }
					finally { finishes.incrementAndGet(); }
				}, 0, 250);
				PhantomAssertions.assertTrue(driver != null, "Production-style shared driver was submitted.");
				if (longRange)
				{
					final long selectionDeadline = System.nanoTime() + 35_000_000_000L;
					while (sensor.snapshot().damageSequence() == baseline.damageSequence() && System.nanoTime() < selectionDeadline && failures.get() == null) { Thread.sleep(100); }
					context.record("N01.longRange", "short=" + org.l2jmobius.gameserver.config.custom.AutoPlayConfig.AUTO_PLAY_SHORT_RANGE + ";long=" + org.l2jmobius.gameserver.config.custom.AutoPlayConfig.AUTO_PLAY_LONG_RANGE + ";target=" + player.getTarget() + ";damage=" + sensor.snapshot().damageSequence());
					PhantomAssertions.assertTrue(sensor.snapshot().damageSequence() > baseline.damageSequence(), "RED: a lawful reachable stock target inside native long range must receive real native damage.");
				}
				final long deadline = System.nanoTime() + (fighter ? 180_000_000_000L : 90_000_000_000L);
				boolean castSeen = false;
				boolean attackSeen = false;
				while ((System.nanoTime() < deadline) && (sensor.snapshot().farmCycleSequence() - baseline.farmCycleSequence() < 5))
				{
					castSeen |= player.isCastingNow();
					attackSeen |= player.isAttackingNow();
					if (failures.get() != null) { break; }
					Thread.sleep(100);
				}
				final var sample = sensor.snapshot();
				driver.cancel(false);
				final long completionDeadline = System.nanoTime() + 2_000_000_000L;
				while ((enters.get() != finishes.get()) && (System.nanoTime() < completionDeadline)) { Thread.sleep(10); }
				context.record("S02.skills", "known=" + player.getKnownSkill(1177) + ";auto=" + player.getAutoUseSettings().getAutoSkills() + ";mp=" + player.getCurrentMp());
				context.record("S02.native", sample.scalarMap());
				context.record("S02.execution", "entered=" + enters + ";finished=" + finishes + ";failure=" + failures.get() + ";castSeen=" + castSeen + ";runtime=" + engine.find(f.id) + ";owner=" + ((org.l2jmobius.gameserver.phantoms.player.PhantomNativeWorkScope) player.getNativeWorkOwner()).snapshot() + ";casting=" + player.isCastingNow() + ";intention=" + player.getAI().getIntention() + ";target=" + player.getTarget() + ";expDelta=" + (player.getExp() - exp) + ";spDelta=" + (player.getSp() - sp));
				PhantomAssertions.assertTrue(failures.get() == null, "RED: composed driver must finish without native incident: " + failures.get());
				PhantomAssertions.assertEquals(enters.get(), finishes.get(), "Last actually dispatched decision body completed.");
				PhantomAssertions.assertTrue(fighter ? attackSeen : castSeen, "RED: actual native offense callback path was not reached.");
				PhantomAssertions.assertTrue(!sample.overflow() && sample.farmCycleSequence() - baseline.farmCycleSequence() >= 5, "RED: native continuation must complete five same-epoch cycles, actual=" + sample);
				PhantomAssertions.assertTrue(player.getExp() > exp && player.getSp() > sp && sample.expGained() > 0 && sample.spGained() > 0, "RED: real native EXP/SP required.");
				PhantomAssertions.assertEquals(f.loadedEpoch, sample.epoch(), "No rematerialization or sensor reset.");
				final long inventoryAfter = player.getInventory().getItems().stream().mapToLong(item -> item.getCount()).sum();
				context.record("S12.nativeLoot", "events=" + sample.lootSequence() + ";inventoryBefore=" + inventoryBefore + ";inventoryAfter=" + inventoryAfter);
				if (sample.lootSequence() > baseline.lootSequence()) { PhantomAssertions.assertTrue(inventoryAfter > inventoryBefore, "Actual native inventory delta accompanies observed pickup."); }
				expectedExp = player.getExp(); expectedSp = player.getSp();
			}
			finally
			{
				if (driver != null) { driver.cancel(false); }
				autoPlay.stop(f.id); travel.beforeMaterialize(f.id, f.objectId);
				PhantomVisibleIntentRecoverySuite.stop(engine); navigation.beginStop(); navigation.finishStop();
				for (var monster : monsters) { monster.deleteMe(); }
			}
			PhantomAssertions.assertEquals(org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService.ServiceState.STOPPED, f.materialization.shutdown().state(), "S15 actual native farm drains before store verification.");
			PhantomAssertions.assertEquals(0, f.materialization.snapshot().retainedEntries(), "Native farm cleanup has no retained lifetime.");
			try (var connection = org.l2jmobius.commons.database.DatabaseFactory.getConnection(); var statement = connection.prepareStatement("SELECT exp,sp FROM characters WHERE charId=?"))
			{
				statement.setInt(1, f.objectId);
				try (var saved = statement.executeQuery())
				{
					PhantomAssertions.assertTrue(saved.next(), "Owned native canonical row exists.");
					PhantomAssertions.assertEquals(expectedExp, saved.getLong(1), "S11 earned EXP persisted exactly.");
					PhantomAssertions.assertEquals(expectedSp, saved.getLong(2), "S11 earned SP persisted exactly.");
					context.record("S11.saved", "exp=" + saved.getLong(1) + ";sp=" + saved.getLong(2) + ";retained=0");
				}
			}
		}
	}
}
