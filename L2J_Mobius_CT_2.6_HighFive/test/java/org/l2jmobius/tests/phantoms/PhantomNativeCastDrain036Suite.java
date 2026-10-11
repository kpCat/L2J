/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.l2jmobius.gameserver.data.xml.ExperienceData;
import org.l2jmobius.gameserver.data.xml.NpcData;
import org.l2jmobius.gameserver.data.xml.SkillData;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.PlayerNativeWork;
import org.l2jmobius.gameserver.model.actor.instance.Monster;
import org.l2jmobius.gameserver.model.spawns.Spawn;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService;
import org.l2jmobius.gameserver.phantoms.player.PhantomNativeWorkScope;

/** Actual doCast/MagicUseTask on the existing owned native materialization fixture. */
public final class PhantomNativeCastDrain036Suite implements PhantomTestSuite
{
	private final PhantomNativeContextHandoffSuite _handoff = new PhantomNativeContextHandoffSuite();
	public static void main(String[] args)
	{
		System.exit(PhantomTestLauncher.runSuite("native-cast-drain036", new PhantomNativeCastDrain036Suite(), new PhantomTestContext(36003601, Path.of(args[0]), Path.of(args[1]))));
	}
	@Override public String id() { return "native-cast-drain036"; }
	@Override public void beforeAll(PhantomTestContext context) throws Exception
	{
		_handoff.beforeAll(context);
		context.record("cast036.productionClassSource", PlayerNativeWork.class.getProtectionDomain().getCodeSource().getLocation().toString());
		org.l2jmobius.gameserver.scripting.ScriptEngine.getInstance().executeScript(org.l2jmobius.gameserver.scripting.ScriptEngine.MASTER_HANDLER_FILE);
		PhantomAssertions.assertTrue(org.l2jmobius.gameserver.handler.TargetHandler.getInstance().size() > 0, "Actual stock target handlers.");
		// Same ordinary pre-lifetime native mage setup as continuation022.
		for (int objectId : java.util.List.of(environment().primary().objectId(), environment().observer().objectId()))
		{
			try (var connection = org.l2jmobius.commons.database.DatabaseFactory.getConnection(); var statement = connection.prepareStatement("UPDATE characters SET classid=10,base_class=10,race=0,level=7,exp=?,online=0 WHERE charId=?"))
			{
				statement.setLong(1, ExperienceData.getInstance().getExpForLevel(7)); statement.setInt(2, objectId);
				PhantomAssertions.assertEquals(1, statement.executeUpdate(), "Exact ordinary owned mage fixture before profile.");
			}
			final var player = Player.load(objectId);
			try { player.addSkill(SkillData.getInstance().getSkill(1177, 1), true); player.setCurrentHp(player.getMaxHp()); player.setCurrentMp(player.getMaxMp()); player.storeMe(); }
			finally { environment().cleanupLoadedPlayer(player); }
		}
	}
	private PhantomHeadlessPlayerTestEnvironment environment() throws Exception
	{
		final var field = PhantomNativeContextHandoffSuite.class.getDeclaredField("_environment"); field.setAccessible(true);
		return (PhantomHeadlessPlayerTestEnvironment) field.get(_handoff);
	}
	@Override public void afterAll(PhantomTestContext context) throws Exception { _handoff.afterAll(context); }
	@Override public void register(PhantomTestRegistry registry)
	{
		final var focus = System.getProperty("phantom.m1.native.focus", "all");
		if (focus.equals("diagnostic")) { registry.add("E01-actual-doCast-dispatch-trace", context -> cast(context, false, false)); return; }
		if (focus.equals("publication")) { registry.add("E07-late-bind-after-actual-zero-delay-completion", this::lateBind); return; }
		if (focus.equals("additional")) { additional(registry); return; }
		if (focus.equals("shared")) { registry.add("E11-managed-cooperative-casts-and-late-corpse-hit", context -> rawPair(context, false)); return; }
		registry.add("E02-actual-launch-hit-finalizer", context -> cast(context, false, false));
		registry.add("E03-reserved-earned-before-dematerialize", context -> cast(context, true, false));
		registry.add("E05-stock-abort-retires-stale-wrapper", context -> cast(context, true, true));
		registry.add("E07-late-bind-after-actual-zero-delay-completion", this::lateBind);
		additional(registry);
	}
	private void additional(PhantomTestRegistry registry)
	{
		registry.add("E04-running-native-body-drain-without-self-wait", this::running);
		registry.add("E08E09-native-body-fault-and-earned-sibling", context -> rawPair(context, true));
		registry.add("E11-managed-cooperative-casts-and-late-corpse-hit", context -> rawPair(context, false));
		registry.add("E01-disabled-diagnostics-stock-control", this::disabled);
		registry.add("E11-ordinary-player-native-cast-control", this::ordinaryControl);
	}
	private void ordinaryControl(PhantomTestContext context) throws Exception
	{
		final var player = Player.load(environment().observer().objectId()); final var npc = new Monster(NpcData.getInstance().getTemplate(20121));
		try (var output = player.attachOutboundSession(new org.l2jmobius.gameserver.phantoms.player.HeadlessPlayerOutboundSession(8, 128)))
		{
			PhantomAssertions.assertFalse(player.isNativeWorkManaged(), "Ordinary Player control has no phantom native owner.");
			player.stopAllTasks(); player.setCurrentHp(player.getMaxHp()); player.setCurrentMp(player.getMaxMp()); player.setOnlineStatus(true, false); player.spawnMe(player.getX(), player.getY(), player.getZ());
			final var spawn = new Spawn(npc.getTemplate()); spawn.setXYZ(player.getX() + 40, player.getY(), player.getZ()); npc.setSpawn(spawn); npc.setCurrentHpMp(npc.getMaxHp(), npc.getMaxMp()); npc.spawnMe(spawn.getX(), spawn.getY(), spawn.getZ());
			final double hp = npc.getCurrentHp(); player.setTarget(npc); player.doCast(player.getKnownSkill(1177));
			final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(12);
			while (player.isCastingNow() && System.nanoTime() < deadline) { Thread.sleep(10); }
			PhantomAssertions.assertTrue(!player.isCastingNow() && npc.getCurrentHp() < hp && !player.isNativeWorkManaged(), "Stock ordinary doCast still executes native HP/finalizer without phantom tickets.");
			context.record("E11.ordinary", "hp=" + hp + "->" + npc.getCurrentHp() + ";managed=" + player.isNativeWorkManaged());
		}
		finally { npc.abortAttack(); npc.abortCast(); npc.deleteMe(); environment().cleanupLoadedPlayer(player); }
	}
	private void rawPair(PhantomTestContext context, boolean failure) throws Exception
	{
		final var first = Player.load(environment().primary().objectId()); final var second = Player.load(environment().observer().objectId());
		try (var one = first.attachOutboundSession(new org.l2jmobius.gameserver.phantoms.player.HeadlessPlayerOutboundSession(8, 128)); var two = second.attachOutboundSession(new org.l2jmobius.gameserver.phantoms.player.HeadlessPlayerOutboundSession(8, 128)))
		{
			for (var player : java.util.List.of(first, second)) { player.stopAllTasks(); player.setCurrentHp(player.getMaxHp()); player.setCurrentMp(player.getMaxMp()); player.setOnlineStatus(true, false); }
			first.spawnMe(first.getX(), first.getY(), first.getZ()); second.spawnMe(first.getX() + 30, first.getY(), first.getZ());
			if (failure) { org.l2jmobius.gameserver.phantoms.player.PhantomCastDrain036Checks.failedSibling(context, first, second); }
			else { org.l2jmobius.gameserver.phantoms.player.PhantomCastDrain036Checks.sharedTarget(context, first, second); }
		}
		finally { for (var player : java.util.List.of(first, second)) { if (!player.isNativeWorkManaged() && World.getInstance().getPlayer(player.getObjectId()) == player) { environment().cleanupLoadedPlayer(player); } } }
	}
	private void disabled(PhantomTestContext context) throws Exception
	{
		final var config = Path.of(org.l2jmobius.gameserver.config.custom.PhantomPlayersConfig.PHANTOM_PLAYERS_CONFIG_FILE);
		final var bytes = java.nio.file.Files.readAllBytes(config);
		try
		{
			java.nio.file.Files.writeString(config, new String(bytes, java.nio.charset.StandardCharsets.UTF_8).replace("EnablePhantomDiagnostics = True", "EnablePhantomDiagnostics = False"));
			org.l2jmobius.gameserver.config.custom.PhantomPlayersConfig.load();
			PhantomAssertions.assertFalse(org.l2jmobius.gameserver.config.custom.PhantomPlayersConfig.settings().diagnosticsEnabled(), "Exact diagnostics-off control.");
			cast(context, false, false);
		}
		finally { java.nio.file.Files.write(config, bytes); org.l2jmobius.gameserver.config.custom.PhantomPlayersConfig.load(); }
	}
	private void running(PhantomTestContext context) throws Exception
	{
		try (var f = _handoff.new Fixture())
		{
			f.removeCatchup(); PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, f.materialization.materialize(f.id).status(), "Actual running-cast lifetime.");
			final var player = World.getInstance().getPlayer(f.objectId); final var scope = (PhantomNativeWorkScope) player.getNativeWorkOwner();
			final var npc = new HoldingMonster(); final var spawn = new Spawn(npc.getTemplate()); spawn.setXYZ(player.getX() + 40, player.getY(), player.getZ()); npc.setSpawn(spawn); npc.setCurrentHpMp(npc.getMaxHp(), npc.getMaxMp()); npc.spawnMe(spawn.getX(), spawn.getY(), spawn.getZ());
			final double hp = npc.getCurrentHp();
			try
			{
				try (var action = f.materialization.tryAcquireAction(f.id).orElseThrow()) { player.setTarget(npc); player.doCast(player.getKnownSkill(1177)); }
				PhantomAssertions.assertTrue(npc._entered.await(10, TimeUnit.SECONDS), "Actual MagicUseTask reaches native HP writer without a paused VM.");
				final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10), started = System.nanoTime();
				f.materialization.requestDematerialize(f.id, deadline);
				PhantomAssertions.assertTrue(System.nanoTime() - started < TimeUnit.MILLISECONDS.toNanos(100) && scope.outstanding() > 0, "Accepted running drain yields control without waiting on its callback.");
				context.record("E04.running", scope.diagnosticScalars().toString());
				npc._release.countDown();
				while (f.materialization.find(f.id).isPresent() && System.nanoTime() < deadline) { f.materialization.requestDematerialize(f.id, deadline); Thread.sleep(10); }
				PhantomAssertions.assertTrue(npc._original && npc._calls == 1 && npc.getCurrentHp() < hp && scope.outstanding() == 0 && scope.pendingTimers() == 0 && scope.firstNativeIncident() == null && f.materialization.find(f.id).isEmpty(), "Running native HP body and earned finalizer finish once before release.");
				context.record("E04.completed", scope.diagnosticScalars().toString());
			}
			finally { npc._release.countDown(); npc.abortAttack(); npc.abortCast(); npc.deleteMe(); }
		}
	}
	private static final class HoldingMonster extends Monster
	{
		final java.util.concurrent.CountDownLatch _entered = new java.util.concurrent.CountDownLatch(1), _release = new java.util.concurrent.CountDownLatch(1);
		volatile int _calls; volatile boolean _original;
		HoldingMonster() { super(NpcData.getInstance().getTemplate(20121)); }
		@Override public void reduceCurrentHp(double damage, org.l2jmobius.gameserver.model.actor.Creature attacker, boolean awake, boolean dot, org.l2jmobius.gameserver.model.skill.Skill skill)
		{
			if (skill != null && damage > 0)
			{
				PhantomAssertions.assertFalse(Thread.holdsLock(this) || Thread.holdsLock(attacker), "Native writer barrier precedes actor/status locks.");
				_original = java.util.Arrays.stream(Thread.currentThread().getStackTrace()).anyMatch(frame -> frame.getClassName().endsWith(".MagicUseTask") && frame.getMethodName().equals("run"));
				_calls++; _entered.countDown();
				try { PhantomAssertions.assertTrue(_release.await(5, TimeUnit.SECONDS), "Bounded TEST writer release."); }
				catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new IllegalStateException(failure); }
			}
			super.reduceCurrentHp(damage, attacker, awake, dot, skill);
		}
	}
	private void lateBind(PhantomTestContext context) throws Exception
	{
		try (var f = _handoff.new Fixture())
		{
			f.removeCatchup();
			PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, f.materialization.materialize(f.id).status(), "Actual native zero-delay lifetime.");
			final Player player = World.getInstance().getPlayer(f.objectId);
			final var scope = (PhantomNativeWorkScope) player.getNativeWorkOwner();
			final var ticket = new java.util.concurrent.atomic.AtomicReference<PlayerNativeWork.Ticket>();
			final var calls = new java.util.concurrent.atomic.AtomicInteger();
			final java.util.concurrent.ScheduledFuture<?> future;
			try (var action = f.materialization.tryAcquireAction(f.id).orElseThrow())
			{
				future = PlayerNativeWork.schedule(player, java.util.List.of(), "EVENT:TEST036_ZERO_DELAY", PlayerNativeWork.Semantics.EARNED, () -> { ticket.set(PlayerNativeWork.current(scope)); calls.incrementAndGet(); }, 0);
			}
			future.get(5, TimeUnit.SECONDS);
			PhantomAssertions.assertTrue(scope.outstanding() == 0 && ticket.get() != null && calls.get() == 1, "Actual callback and wrapper complete once before late bind.");
			// Deterministic publication boundary: callback completion can precede SUBMITTED.
			ticket.get().dispatchStage("SUBMITTED", 0, future);
			final var field = ticket.get().getClass().getDeclaredField("_dispatchFuture"); field.setAccessible(true);
			context.record("E07.lateBind", "futureDone=" + future.isDone() + ";retained=" + (field.get(ticket.get()) != null) + ";" + scope.diagnosticScalars());
			PhantomAssertions.assertEquals(null, field.get(ticket.get()), "RED E07: late SUBMITTED resurrects a completed diagnostic Future reference.");
			future.cancel(false); future.cancel(false);
			PhantomAssertions.assertThrows(IllegalStateException.class, () -> scope.reserve(ticket.get(), "cast-hit", PlayerNativeWork.Semantics.EARNED), "Expired parent cannot open a transitive child.");
			PhantomAssertions.assertTrue(scope.outstanding() == 0 && calls.get() == 1 && scope.firstNativeIncident() == null, "Repeated cancel cannot rerun/retire completed callback twice.");
		}
	}
	private void cast(PhantomTestContext context, boolean drainPending, boolean abort) throws Exception
	{
		final String key = drainPending + "." + abort + ".diagnostics=" + org.l2jmobius.gameserver.config.custom.PhantomPlayersConfig.settings().diagnosticsEnabled();
		try (var f = _handoff.new Fixture())
		{
			f.removeCatchup();
			PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, f.materialization.materialize(f.id).status(), "Actual owned native materialization.");
			final Player player = World.getInstance().getPlayer(f.objectId);
			final var scope = (PhantomNativeWorkScope) player.getNativeWorkOwner();
			final Monster npc = new Monster(NpcData.getInstance().getTemplate(20121));
			final var spawn = new Spawn(npc.getTemplate()); spawn.setXYZ(player.getX() + 40, player.getY(), player.getZ()); npc.setSpawn(spawn);
			npc.setCurrentHpMp(npc.getMaxHp(), npc.getMaxMp()); npc.spawnMe(spawn.getX(), spawn.getY(), spawn.getZ());
			final double hp = npc.getCurrentHp(), mp = player.getCurrentMp();
			try
			{
				try (var action = f.materialization.tryAcquireAction(f.id).orElseThrow()) { player.setTarget(npc); player.doCast(player.getKnownSkill(1177)); }
				final var reserved = scope.diagnosticScalars();
				context.record("cast036.reserved." + key, "profile=" + f.id + ";object=" + f.objectId + ";epoch=" + scope.epoch() + ";" + reserved);
				PhantomAssertions.assertTrue(player.isCastingNow() && reserved.get("nativeOwnerWork").contains("cast-launch:RESERVED:EARNED"), "Real delayed stock cast-launch is reserved.");
				final boolean diagnostics = org.l2jmobius.gameserver.config.custom.PhantomPlayersConfig.settings().diagnosticsEnabled();
				PhantomAssertions.assertTrue(diagnostics ? reserved.get("nativeEventDispatch").contains("cast-launch:SUBMITTED") && reserved.get("nativeEventDispatch").contains(":submit=") : reserved.get("nativeEventDispatch").equals("[]"), "Actual cast trace follows the existing diagnostic switch.");
				if (abort) { player.abortCast(); }
				final long drainDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
				if (drainPending)
				{
					final var result = f.materialization.requestDematerialize(f.id, drainDeadline);
					context.record("cast036.request." + key, result.toString());
					PhantomAssertions.assertTrue(scope.outstanding() > 0 && World.getInstance().getPlayer(f.objectId) == player, "Reserved EARNED retains exact owner before due.");
					PhantomAssertions.assertTrue(scope.reserve(null, "TEST036_NEW_ROOT", PlayerNativeWork.Semantics.CANCELLABLE) == null, "Draining closes new roots.");
				}
				final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(12);
				while (System.nanoTime() < deadline && (scope.outstanding() != 0 || player.isCastingNow() || (drainPending && f.materialization.find(f.id).isPresent())))
				{
					// Existing control API is polled with one original deadline, as in system shutdown.
					if (drainPending) { f.materialization.requestDematerialize(f.id, drainDeadline); }
					Thread.sleep(10);
				}
				context.record("cast036.completed." + key, "hp=" + hp + "->" + npc.getCurrentHp() + ";mp=" + mp + "->" + player.getCurrentMp() + ";" + scope.diagnosticScalars());
				PhantomAssertions.assertTrue(scope.outstanding() == 0 && !player.isCastingNow() && scope.firstNativeIncident() == null, "Actual cast wrappers complete without owner incident.");
				PhantomAssertions.assertTrue(abort ? npc.getCurrentHp() == hp : npc.getCurrentHp() < hp, "Stock abort no-op or actual native HP writer.");
				if (!abort && diagnostics) { PhantomAssertions.assertTrue(scope.diagnosticScalars().get("nativeRecentCompletedEvents").contains("cast-finalizer:COMPLETED"), "Native finalizer completed through wrapper."); }
				if (!drainPending) { PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, f.materialization.dematerialize(f.id).status(), "Native final store."); }
				PhantomAssertions.assertTrue(f.materialization.find(f.id).isEmpty() && World.getInstance().getPlayer(f.objectId) == null && scope.outstanding() == 0 && scope.pendingTimers() == 0, "FINALIZED precedes exact world/owner release.");
			}
			finally { npc.abortAttack(); npc.abortCast(); npc.deleteMe(); }
		}
	}
}
