/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.tests.phantoms;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import org.l2jmobius.commons.config.ThreadConfig;
import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.commons.threads.ThreadPool;
import org.l2jmobius.gameserver.config.PlayerConfig;
import org.l2jmobius.gameserver.config.RatesConfig;
import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.handler.PunishmentHandler;
import org.l2jmobius.gameserver.managers.PunishmentManager;
import org.l2jmobius.gameserver.model.Location;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.PlayerNativeWork;
import org.l2jmobius.gameserver.model.actor.tasks.player.TeleportTask;
import org.l2jmobius.gameserver.model.actor.tasks.player.TeleportWatchdogTask;
import org.l2jmobius.gameserver.model.events.EventType;
import org.l2jmobius.gameserver.model.events.holders.actor.creature.OnCreatureTeleported;
import org.l2jmobius.gameserver.model.events.listeners.ConsumerEventListener;
import org.l2jmobius.gameserver.model.punishment.PunishmentAffect;
import org.l2jmobius.gameserver.model.punishment.PunishmentTask;
import org.l2jmobius.gameserver.model.punishment.PunishmentType;
import org.l2jmobius.gameserver.model.script.Quest;
import org.l2jmobius.gameserver.model.zone.form.ZoneCuboid;
import org.l2jmobius.gameserver.model.zone.type.JailZone;
import org.l2jmobius.gameserver.model.zone.type.ResidenceHallTeleportZone;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService;
import org.l2jmobius.gameserver.phantoms.player.PhantomNativeWorkScope;

/** Focused stock task/event proofs. Timing seams are TEST-only; the Player and native writers remain unchanged. */
public final class PhantomM1TeleportChecks
{
	private static final int ITEM_ID = 1334;
	private PhantomM1TeleportChecks() { }

	public static void queuedTeleport(PhantomTestContext c, Player p, PhantomMaterializationService s, long id) throws Exception { queued(c, member(c, p, s, id), false); }
	public static void teleportedEvent(PhantomTestContext c, Player p, PhantomMaterializationService s, long id) throws Exception { event(c, member(c, p, s, id)); }
	public static void jail(PhantomTestContext c, Player p, PhantomMaterializationService s, long id) throws Exception { jail(c, member(c, p, s, id)); }
	public static void residence(PhantomTestContext c, Player p, PhantomMaterializationService s, long id, CountDownLatch afterOwnedPrepare, CountDownLatch releaseOwnedPrepare) throws Exception
	{
		PhantomAssertions.assertEquals(1L, afterOwnedPrepare.getCount(), "Residence AFTER_OWNED_PREPARE must be armed after materialization.");
		residence(c, member(c, p, s, id), afterOwnedPrepare, releaseOwnedPrepare);
	}
	/** Actual stock watchdog task at time0; this does not claim that the runtime optional publisher is enabled. */
	public static void watchdogTask(PhantomTestContext c, Player p, PhantomMaterializationService s, long id) throws Exception { queued(c, member(c, p, s, id), true); }
	public static void ordinaryQueuedTeleport(PhantomTestContext c, Player p) throws Exception { queued(c, member(c, p, null, 0), false); }
	public static void ordinaryTeleportedEvent(PhantomTestContext c, Player p) throws Exception { event(c, member(c, p, null, 0)); }
	public static void ordinaryJail(PhantomTestContext c, Player p) throws Exception { jail(c, member(c, p, null, 0)); }
	public static void ordinaryResidence(PhantomTestContext c, Player p) throws Exception { residence(c, member(c, p, null, 0), null, null); }
	public static void ordinaryWatchdogTask(PhantomTestContext c, Player p) throws Exception { queued(c, member(c, p, null, 0), true); }
	/** TEST-only interrupted journal restore after the caller has stopped native punishment and drained its callbacks. */
	public static void restoreJailBeforeImage(PhantomTestContext c, Player p, Path journal) throws Exception
	{
		PhantomAssertions.assertEquals(PhantomTestDatabaseGuard.TARGET_DATABASE, c.measurements().get("headless.database"), "Jail restore requires initialized guarded TEST environment.");
		PhantomAssertions.assertTrue(p != null && World.getInstance().getPlayer(p.getObjectId()) == p && !p.isTeleporting(), "Jail restore requires the exact live TEST Player after native callback cleanup.");
		if (p.isNativeWorkManaged()) { PhantomAssertions.assertTrue(p.getNativeWorkOwner() instanceof PhantomNativeWorkScope scope && scope.isCurrent() && scope.player() == p && scope.outstanding() == 0, "Jail restore requires zero exact owner work after native callback drain."); }
		final Path directory = c.moduleRoot().resolve(".phantom-local/m1-teleport-before-images").toRealPath();
		final Path file = journal.toRealPath(); PhantomAssertions.assertTrue(file.getParent().equals(directory) && Files.isRegularFile(file) && Files.size(file) <= 1024, "Jail journal path/size rejected.");
		final var schema = PhantomTestSchemaManifest.current(c.moduleRoot());
		final int objectId; final String marker;
		try (var input = new DataInputStream(Files.newInputStream(file)))
		{
			PhantomAssertions.assertEquals(0x4D315450, input.readInt(), "Jail journal header rejected.");
			PhantomAssertions.assertEquals(schema.aggregateSha256(), input.readUTF(), "Jail journal original schema hash changed.");
			objectId = input.readInt(); marker = input.readUTF();
			PhantomAssertions.assertTrue(objectId == p.getObjectId() && marker.matches("M1_TEST_JAIL_[0-9a-f-]{36}") && input.readInt() == 0 && input.read() == -1, "Jail journal exact empty before-image rejected.");
		}
		new JailJournal(c, objectId, marker, file).close();
	}

	private static Member member(PhantomTestContext c, Player p, PhantomMaterializationService s, long id) throws Exception
	{
		PhantomAssertions.assertEquals(PhantomTestDatabaseGuard.TARGET_DATABASE, c.measurements().get("headless.database"), "Teleport tests require the initialized guarded headless TEST environment.");
		PhantomAssertions.assertTrue(p != null && World.getInstance().getPlayer(p.getObjectId()) == p && p.isOnline() && !p.isAlikeDead() && !p.isMoving() && !p.isTeleporting(), "INVALID teleport fixture: exact live stationary Player required.");
		PhantomNativeWorkScope scope = null;
		if (s != null)
		{
			PhantomAssertions.assertTrue(p.isNativeWorkManaged() && p.getNativeWorkOwner() instanceof PhantomNativeWorkScope, "INVALID teleport fixture: canonical managed Player required.");
			scope = (PhantomNativeWorkScope) p.getNativeWorkOwner();
			PhantomAssertions.assertTrue(scope.isCurrent() && scope.player() == p, "INVALID teleport fixture: stale exact owner.");
		}
		else { PhantomAssertions.assertFalse(p.isNativeWorkManaged(), "INVALID ordinary control: Player must retain its original ordinary identity."); }
		final var m = new Member(p, s, id, scope, new Location(p));
		action(m, () -> p.getStatus().stopHpMpRegeneration());
		PhantomAssertions.assertEquals(null, PlayerNativeWork.inheritedPlayer(), "INVALID teleport fixture: caller retained borrowed owner context.");
		return m;
	}

	private static void queued(PhantomTestContext c, Member m, boolean watchdog) throws Exception
	{
		final String key = key(m, watchdog ? "watchdogTask" : "queuedTeleport");
		c.record(key + ".fixture", "INVALID_UNTIL_NATIVE_COMPLETION");
		c.record(key + ".runtimeWatchdogSeconds", PlayerConfig.TELEPORT_WATCHDOG_TIMEOUT);
		c.record(key + ".publisher", watchdog ? "TEST time0 of actual stock TeleportWatchdogTask; runtime branch not asserted" : "TEST time0 of actual stock TeleportTask");
		final Location destination = destination(m.player());
		final int beforeX = m.player().getX(), beforeY = m.player().getY();
		final var observed = new NativeLocation(destination, m, "org.l2jmobius.gameserver.model.actor.tasks.player.TeleportTask", false);
		final var completed = new CountDownLatch(1);
		final var listener = new ConsumerEventListener(m.player(), EventType.ON_CREATURE_TELEPORTED, (OnCreatureTeleported e) -> { if (e.getCreature() == m.player()) { completed.countDown(); } }, completed);
		if (watchdog) { m.player().addListener(listener); }
		ScheduledFuture<?> task = null;
		int queued = 0;
		Throwable primary = null;
		try
		{
			try (var gate = new WorkerGate(false))
			{
				gate.acquire(); assertBaseline(m);
				final var submitted = new AtomicReference<ScheduledFuture<?>>();
				action(m, () ->
				{
					if (watchdog) { m.player().setTeleporting(true, false); submitted.set(ThreadPool.schedule(new TeleportWatchdogTask(m.player()), 0)); }
					else { submitted.set(ThreadPool.schedule(new TeleportTask(m.player(), observed), 0)); }
				});
				task = submitted.get(); PhantomAssertions.assertTrue(task != null, "INVALID teleport fixture: actual task publication refused.");
				queued = outstanding(m); recordQueued(c, key, m, queued);
				PhantomAssertions.assertTrue(watchdog ? completed.getCount() == 1 : observed.entered.getCount() == 1, "INVALID teleport fixture: native task entered before worker release.");
			}
			task.get(5, TimeUnit.SECONDS);
			if (watchdog) { PhantomAssertions.assertTrue(completed.await(5, TimeUnit.SECONDS) && !m.player().isTeleporting(), "INVALID watchdog fixture: actual onTeleported/spawn/event did not complete."); }
			else
			{
				PhantomAssertions.assertTrue(observed.nativeStack.get(), "INVALID teleport fixture: actual TeleportTask.run did not read its stock destination.");
				PhantomAssertions.assertTrue(m.player().isTeleporting() && near(m.player(), destination) && (m.player().getX() != beforeX || m.player().getY() != beforeY), "INVALID teleport fixture: stock task did not mutate teleport flag and actual position.");
				c.record(key + ".ownedAtNativeBody", observed.owned.get());
			}
			c.record(key + ".nativePosition", position(m.player())); c.record(key + ".fixture", "VALID_ACTUAL_NATIVE_COMPLETION");
			if (m.scope() != null) { PhantomAssertions.assertTrue(queued > 0, "Exact managed receiver was not reserved after ActionLease close and before queued stock teleport task entry."); }
		}
		catch (Exception | Error failure) { primary = failure; throw failure; }
		finally
		{
			if (task != null) { task.cancel(false); }
			if (watchdog) { m.player().removeListener(listener); }
			cleanup(primary, () -> restorePosition(m));
		}
	}

	private static void event(PhantomTestContext c, Member m) throws Exception
	{
		final String key = key(m, "teleportedEvent");
		c.record(key + ".fixture", "INVALID_UNTIL_NATIVE_COMPLETION");
		PhantomAssertions.assertEquals(1.0f, RatesConfig.QUEST_ITEM_DROP_AMOUNT_MULTIPLIER, "INVALID teleported event: original quest amount rate required.");
		final long before = m.player().getInventory().getInventoryItemCount(ITEM_ID, -1);
		final var completed = new CountDownLatch(1); final var failure = new AtomicReference<Throwable>(); final var owned = new AtomicBoolean();
		final var listener = new ConsumerEventListener(m.player(), EventType.ON_CREATURE_TELEPORTED, (OnCreatureTeleported e) ->
		{
			if (e.getCreature() != m.player()) { return; }
			try { owned.set(owned(m)); Quest.giveItems(m.player(), ITEM_ID, 1); }
			catch (RuntimeException | Error problem) { failure.set(problem); throw problem; }
			finally { completed.countDown(); }
		}, completed);
		m.player().addListener(listener);
		try
		{
			final int queued;
			try (var gate = new WorkerGate(true))
			{
				gate.acquire(); assertBaseline(m);
				action(m, () -> m.player().setTeleporting(true, false));
				// Headless native completion arrives without an ActionLease; typed event participants must retain the receiver.
				m.player().onTeleported();
				queued = outstanding(m); recordQueued(c, key, m, queued);
				PhantomAssertions.assertEquals(1L, completed.getCount(), "INVALID teleported event: instant worker entered before release.");
			}
			PhantomAssertions.assertTrue(completed.await(5, TimeUnit.SECONDS) && failure.get() == null, "INVALID teleported event: native listener failed: " + failure.get());
			PhantomAssertions.assertEquals(before + 1, m.player().getInventory().getInventoryItemCount(ITEM_ID, -1), "INVALID teleported event: original native Quest item writer did not complete exactly once.");
			c.record(key + ".ownedAtListener", owned.get()); c.record(key + ".nativeQuestItemDelta", 1); c.record(key + ".fixture", "VALID_ACTUAL_NATIVE_COMPLETION");
			if (m.scope() != null) { PhantomAssertions.assertTrue(queued > 0 && owned.get(), "OnCreatureTeleported explicit exact receiver was not retained before native asynchronous listener mutation."); }
		}
		finally { m.player().removeListener(listener); restorePosition(m); }
	}

	private static void residence(PhantomTestContext c, Member m, CountDownLatch prepared, CountDownLatch releasePrepare) throws Exception
	{
		final String key = key(m, "residence"); c.record(key + ".fixture", "INVALID_UNTIL_NATIVE_COMPLETION");
		final Location destination = destination(m.player());
		final var observed = new NativeLocation(destination, m, "org.l2jmobius.gameserver.model.zone.type.ResidenceHallTeleportZone$TeleportTask", true);
		final var zone = new ResidenceFixture(-700024);
		zone.addSpawn(destination.getX(), destination.getY(), destination.getZ()); zone.getSpawns().set(0, observed);
		zone.setZone(new ZoneCuboid(m.player().getX() - 64, m.player().getX() + 64, m.player().getY() - 64, m.player().getY() + 64, m.player().getZ() - 64, m.player().getZ() + 64));
		final var storeFailure = new AtomicReference<Throwable>();
		final Thread checkpoint = new Thread(() -> { try { m.player().store(false); } catch (Throwable failure) { storeFailure.set(failure); } }, "TEST-residence-checkpoint");
		ScheduledFuture<?> body = null; ScheduledFuture<?> pending = null; Throwable primary = null;
		try
		{
			action(m, () -> zone.revalidateInZone(m.player())); assertBaseline(m);
			zone.checkTeleportTask(); pending = residenceFuture(zone);
			PhantomAssertions.assertTrue(pending != null && pending.getDelay(TimeUnit.SECONDS) >= 25 && !pending.isDone(), "INVALID residence fixture: stock30s registration was not pending.");
			c.record(key + ".pending30sOutstanding", outstanding(m));
			PhantomAssertions.assertEquals(0, outstanding(m), "Shared residence delay must not own a Player ticket for its entire30s wait.");
			PhantomAssertions.assertTrue(pending.cancel(false), "INVALID residence fixture: exact stock pending timer could not be cancelled before TEST timing seam.");
			c.record(key + ".bodyTimingSeam", "same stock protected TeleportTask at time0 after exact native30s registration/cancellation");
			body = ThreadPool.schedule(zone.body(), 0);
			PhantomAssertions.assertTrue(body != null && observed.entered.await(5, TimeUnit.SECONDS) && observed.nativeStack.get(), "INVALID residence fixture: actual shared stock per-player teleport body did not enter.");
			c.record(key + ".outstandingDuringNativeBody", outstanding(m)); c.record(key + ".ownedDuringNativeBody", observed.owned.get());
			boolean crossed = false;
			if (m.scope() != null)
			{
				checkpoint.start(); crossed = prepared.await(2, TimeUnit.SECONDS); c.record(key + ".prepareWhileNativeBody", crossed);
				if (crossed) { releasePrepare.countDown(); }
			}
			observed.release.countDown(); body.get(5, TimeUnit.SECONDS);
			PhantomAssertions.assertEquals(null, observed.failure.get(), "INVALID residence fixture: native invocation gate failed.");
			PhantomAssertions.assertTrue(m.player().isTeleporting() && m.player().getX() == destination.getX() && m.player().getY() == destination.getY(), "INVALID residence fixture: original native no-offset teleport writer did not complete.");
			c.record(key + ".nativePosition", position(m.player())); c.record(key + ".fixture", "VALID_ACTUAL_NATIVE_COMPLETION");
			if (m.scope() != null)
			{
				PhantomAssertions.assertTrue(prepared.await(5, TimeUnit.SECONDS), "Residence checkpoint did not prepare after native body exit.");
				releasePrepare.countDown(); checkpoint.join(10000);
				PhantomAssertions.assertTrue(!checkpoint.isAlive() && storeFailure.get() == null, "Residence active checkpoint failed: " + storeFailure.get());
				PhantomAssertions.assertFalse(crossed, "Owned snapshot crossed actual shared residence per-player native teleport invocation.");
				PhantomAssertions.assertTrue(observed.owned.get(), "Shared residence invocation lacked its exact Player owner context.");
			}
		}
		catch (Exception | Error failure) { primary = failure; throw failure; }
		finally
		{
			observed.release.countDown(); if (releasePrepare != null) { releasePrepare.countDown(); }
			final var executing = body; final var delayed = pending;
			cleanup(primary, () -> { if (delayed != null) { delayed.cancel(false); } if (executing != null) { executing.get(5, TimeUnit.SECONDS); } if (checkpoint.getState() != Thread.State.NEW) { checkpoint.join(10000); } PhantomAssertions.assertFalse(checkpoint.isAlive(), "Residence fixture retained checkpoint thread."); action(m, () -> zone.removeCharacter(m.player())); restorePosition(m); });
		}
	}

	private static void jail(PhantomTestContext c, Member m) throws Exception
	{
		final String key = key(m, "jail"); c.record(key + ".fixture", "INVALID_UNTIL_NATIVE_COMPLETION");
		final var handler = PunishmentHandler.getInstance().getHandler(PunishmentType.JAIL);
		PhantomAssertions.assertTrue(handler != null && handler.getClass().getName().equals("handlers.punishments.JailHandler"), "INVALID Jail fixture: actual stock JailHandler must be loaded by MASTER.");
		PhantomAssertions.assertFalse(m.player().isJailed(), "INVALID Jail fixture: fresh exact TEST Player already has a native punishment.");
		final var zone = new JailZone(-700025);
		Throwable primary = null;
		try (var journal = JailJournal.open(c, m.player()))
		{
			try
			{
				// Original constructor inserts the one guarded TEST row and publishes stock JailHandler's initial teleport.
				final int initialQueued;
				try (var gate = new WorkerGate(false))
				{
					gate.acquire(); assertBaseline(m);
					action(m, () -> { final var task = new PunishmentTask(m.player().getObjectId(), PunishmentAffect.CHARACTER, PunishmentType.JAIL, 0, journal.marker, journal.marker); PhantomAssertions.assertTrue(task.isStored(), "INVALID Jail fixture: native punishment insert failed."); PunishmentManager.getInstance().startPunishment(task); });
					initialQueued = outstanding(m); recordQueued(c, key + ".handlerInitial", m, initialQueued);
					PhantomAssertions.assertFalse(m.player().isTeleporting(), "INVALID Jail fixture: initial task entered before held scheduled workers were released.");
				}
				await(() -> m.player().isTeleporting() && near(m.player(), JailZone.getLocationIn()), "INVALID Jail fixture: initial stock handler TeleportTask did not complete.");
				finishTeleport(m);
				zone.setZone(new ZoneCuboid(m.player().getX() - 64, m.player().getX() + 64, m.player().getY() - 64, m.player().getY() + 64, m.player().getZ() - 64, m.player().getZ() + 64));
				action(m, () -> zone.revalidateInZone(m.player()));
				final int queued;
				try (var gate = new WorkerGate(false))
				{
					gate.acquire(); assertBaseline(m);
					action(m, () -> zone.removeCharacter(m.player()));
					queued = outstanding(m); recordQueued(c, key, m, queued);
					PhantomAssertions.assertTrue(m.player().isJailed() && !m.player().isTeleporting(), "INVALID Jail fixture: native exit publisher frontend was not established before queued body.");
				}
				await(() -> m.player().isTeleporting() && near(m.player(), JailZone.getLocationIn()), "INVALID Jail fixture: actual JailZone exit TeleportTask did not mutate native teleport flag/position.");
				c.record(key + ".nativePosition", position(m.player())); c.record(key + ".fixture", "VALID_ACTUAL_NATIVE_COMPLETION");
				if (m.scope() != null)
				{
					PhantomAssertions.assertTrue(initialQueued > 0, "Actual stock JailHandler constructor publication did not retain its managed receiver before queued TeleportTask entry.");
					PhantomAssertions.assertTrue(queued > 0, "Actual JailZone exit did not retain its exact managed receiver before queued TeleportTask entry.");
				}
			}
			catch (Exception | Error failure) { primary = failure; throw failure; }
			finally
			{
				cleanup(primary, () -> { finishTeleport(m); action(m, () -> { zone.removeCharacter(m.player()); PunishmentManager.getInstance().stopPunishment(m.player().getObjectId(), PunishmentAffect.CHARACTER, PunishmentType.JAIL); }); await(() -> m.player().isTeleporting() && near(m.player(), JailZone.getLocationOut()), "Jail cleanup: stock handler out-teleport did not complete."); finishTeleport(m); restorePosition(m); await(() -> outstanding(m) == 0, "Jail cleanup retained exact owner earned callbacks."); PhantomAssertions.assertFalse(m.player().isJailed(), "Jail cleanup retained native punishment."); });
			}
		}
	}

	private static void action(Member m, Runnable body) throws Exception
	{
		if (m.service() == null) { body.run(); return; }
		try (var action = m.service().tryAcquireAction(m.profileId()).orElseThrow()) { PhantomAssertions.assertTrue(action.player() == m.player(), "Teleport fixture ActionLease changed exact Player."); body.run(); }
	}
	private static void finishTeleport(Member m) throws Exception { action(m, () -> { if (m.player().isTeleporting()) { m.player().onTeleported(); } m.player().getStatus().stopHpMpRegeneration(); }); }
	private static void restorePosition(Member m) throws Exception
	{
		action(m, () -> { if (m.player().getX() != m.origin().getX() || m.player().getY() != m.origin().getY() || m.player().getZ() != m.origin().getZ() || m.player().getInstanceId() != m.origin().getInstanceId()) { m.player().teleToLocation(m.origin(), false); } if (m.player().isTeleporting()) { m.player().onTeleported(); } m.player().getStatus().stopHpMpRegeneration(); });
	}
	private static void assertBaseline(Member m) { PhantomAssertions.assertEquals(0, outstanding(m), "INVALID teleport fixture: existing native work hides exact producer accounting."); }
	private static int outstanding(Member m) { return m.scope() == null ? 0 : m.scope().outstanding(); }
	private static boolean owned(Member m) { return m.scope() != null && PlayerNativeWork.current(m.scope()) != null; }
	private static String key(Member m, String name) { return "Q12.teleport." + (m.scope() == null ? "ordinary." : "managed.") + name; }
	private static String position(Player p) { return p.getX() + "/" + p.getY() + "/" + p.getZ() + "/" + p.getInstanceId(); }
	private static Location destination(Player p) { final int x = p.getX() + 192; return new Location(x, p.getY(), GeoEngine.getInstance().getHeight(x, p.getY(), p.getZ()), p.getHeading(), p.getInstanceId()); }
	private static boolean near(Player p, Location l) { final long radius = Math.max(64, PlayerConfig.MAX_OFFSET_ON_TELEPORT + 16); return Math.abs((long) p.getX() - l.getX()) <= radius && Math.abs((long) p.getY() - l.getY()) <= radius; }
	private static void recordQueued(PhantomTestContext c, String key, Member m, int queued) { c.record(key + ".outstandingAfterLeaseBeforeEntry", queued); c.record(key + ".exactEpoch", m.scope() == null ? "ordinary" : m.scope().epoch()); if (m.scope() != null) { PhantomAssertions.assertEquals(null, PlayerNativeWork.current(m.scope()), "Teleport fixture retained root ActionLease after publication."); } }
	private static void await(BooleanSupplier predicate, String message) throws Exception { final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(6); while (!predicate.getAsBoolean() && System.nanoTime() < deadline) { Thread.sleep(10); } PhantomAssertions.assertTrue(predicate.getAsBoolean(), message); }
	private static ScheduledFuture<?> residenceFuture(ResidenceHallTeleportZone zone) throws Exception { final var field = ResidenceHallTeleportZone.class.getDeclaredField("_teleTask"); field.setAccessible(true); return (ScheduledFuture<?>) field.get(zone); }
	private static void cleanup(Throwable primary, Checked action) throws Exception { try { action.run(); } catch (Exception | Error failure) { if (primary == null) { throw failure; } primary.addSuppressed(failure); } }
	private static PhantomTestDatabaseGuard.ValidatedSettings testSettings(PhantomTestContext c) throws Exception
	{
		final String path = System.getProperty("phantom.test.config");
		PhantomAssertions.assertTrue(path != null && !path.isBlank(), "Jail fixture requires the existing explicit guarded phantom.test.config.");
		return PhantomTestDatabaseGuard.validate(c.moduleRoot(), Path.of(path));
	}
	@FunctionalInterface private interface Checked { void run() throws Exception; }
	private record Member(Player player, PhantomMaterializationService service, long profileId, PhantomNativeWorkScope scope, Location origin) { }
	private static final class ResidenceFixture extends ResidenceHallTeleportZone { private ResidenceFixture(int id) { super(id); } private Runnable body() { return new TeleportTask() { }; } }

	/** Reads the unchanged real Location at the native task call boundary, before teleport writers or receiver monitors. */
	private static final class NativeLocation extends Location
	{
		private final Member member; private final String expected; private final boolean hold;
		private final CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
		private final AtomicBoolean nativeStack = new AtomicBoolean(), owned = new AtomicBoolean(); private final AtomicReference<String> failure = new AtomicReference<>();
		private NativeLocation(Location l, Member m, String task, boolean holdBody) { super(l.getX(), l.getY(), l.getZ(), l.getHeading(), l.getInstanceId()); member = m; expected = task; hold = holdBody; }
		@Override public int getX()
		{
			if (entered.getCount() > 0 && StackWalker.getInstance().walk(frames -> frames.anyMatch(frame -> frame.getClassName().equals(expected) && frame.getMethodName().equals("run"))))
			{
				nativeStack.set(true); owned.set(owned(member)); entered.countDown();
				if (hold) { try { if (!release.await(8, TimeUnit.SECONDS)) { failure.set("Native location body release timed out."); } } catch (InterruptedException problem) { Thread.currentThread().interrupt(); failure.set("Native location body interrupted."); } }
			}
			return super.getX();
		}
	}

	/** Existing headless warm-up plus QueuedWorkChecks pool barrier, with a distinct instant event queue. */
	private static final class WorkerGate implements AutoCloseable
	{
		private final boolean instant; private final int count; private final CountDownLatch started, finished, release = new CountDownLatch(1); private final List<ScheduledFuture<?>> tasks = new ArrayList<>(); private final AtomicReference<String> failure = new AtomicReference<>();
		private WorkerGate(boolean useInstant) { instant = useInstant; count = instant ? ThreadConfig.INSTANT_THREAD_POOL_SIZE : ThreadConfig.SCHEDULED_THREAD_POOL_SIZE; PhantomAssertions.assertTrue(count > 0 && count <= 128, "INVALID teleport worker budget: pool size must be1..128."); started = new CountDownLatch(count); finished = new CountDownLatch(count); }
		private void acquire() throws Exception
		{
			for (int i = 0; i < count; i++)
			{
				final Runnable worker = () -> { started.countDown(); try { if (!release.await(10, TimeUnit.SECONDS)) { failure.compareAndSet(null, "TEST worker release timed out."); } } catch (InterruptedException problem) { Thread.currentThread().interrupt(); failure.compareAndSet(null, "TEST worker interrupted."); } finally { finished.countDown(); } };
				if (instant) { ThreadPool.execute(worker); } else { final var task = ThreadPool.schedule(worker, 0); PhantomAssertions.assertTrue(task != null, "INVALID teleport worker publication refused."); tasks.add(task); }
			}
			PhantomAssertions.assertTrue(started.await(5, TimeUnit.SECONDS) && failure.get() == null, "INVALID teleport fixture: all actual workers did not reach barrier.");
		}
		@Override public void close() throws Exception { release.countDown(); try { PhantomAssertions.assertTrue(finished.await(5, TimeUnit.SECONDS) && failure.get() == null, "Teleport worker barrier failed: " + failure.get()); } finally { tasks.forEach(task -> task.cancel(false)); } }
	}

	/** Empty exact CHARACTER/JAIL before-image only. The row is inserted by stock code and restored by full-row CAS. */
	private static final class JailJournal implements AutoCloseable
	{
		private final PhantomTestContext context; private final int objectId; private final String marker; private final Path path;
		private JailJournal(PhantomTestContext c, int id, String reason, Path file) { context = c; objectId = id; marker = reason; path = file; }
		private static JailJournal open(PhantomTestContext c, Player p) throws Exception
		{
			final var settings = testSettings(c);
			final var schema = PhantomTestSchemaManifest.current(c.moduleRoot()); PhantomTestSchemaManifest.requireExact(schema, PhantomTestSchemaManifest.read(PhantomTestSchemaManifest.localPath(c.moduleRoot())));
			PhantomAssertions.assertTrue(PhantomTestSchemaManifest.inventory(c.moduleRoot()).stream().anyMatch(script -> script.relativePath().replace('\\', '/').equals("dist/db_installer/sql/game/punishments.sql")), "INVALID Jail fixture: punishments is outside original schema inventory.");
			try (Connection connection = DatabaseFactory.getConnection())
			{
				PhantomAssertions.assertEquals(PhantomTestDatabaseGuard.TARGET_DATABASE, connection.getCatalog(), "Jail journal database catalog rejected."); PhantomTestSchemaManifest.requireExactDatabaseMetadata(connection, schema);
				PhantomAssertions.assertTrue(settings.login().equals(PhantomTestDatabaseGuard.TARGET_USER), "Jail journal dedicated TEST user rejected.");
				PhantomAssertions.assertEquals(0, rowCount(connection, p.getObjectId()), "INVALID Jail fixture: exact prior CHARACTER/JAIL rows require a different fresh TEST objectId; no rows were changed.");
			}
			final Path directory = c.moduleRoot().resolve(".phantom-local/m1-teleport-before-images"); Files.createDirectories(directory);
			PhantomAssertions.assertTrue(directory.toRealPath().getParent().equals(c.moduleRoot().resolve(".phantom-local").toRealPath()), "Jail private journal directory escaped existing TEST local root.");
			final String marker = "M1_TEST_JAIL_" + UUID.randomUUID(); final Path path = directory.resolve(p.getObjectId() + "-" + marker + ".bin");
			try (var output = new DataOutputStream(Files.newOutputStream(path, StandardOpenOption.CREATE_NEW))) { output.writeInt(0x4D315450); output.writeUTF(schema.aggregateSha256()); output.writeInt(p.getObjectId()); output.writeUTF(marker); output.writeInt(0); }
			try (var channel = FileChannel.open(path, StandardOpenOption.WRITE)) { channel.force(true); }
			c.record("Q12.teleport.jail.beforeImage", path.toString()); return new JailJournal(c, p.getObjectId(), marker, path);
		}
		@Override public void close() throws Exception
		{
			PhantomAssertions.assertFalse(PunishmentManager.getInstance().hasPunishment(objectId, PunishmentAffect.CHARACTER, PunishmentType.JAIL), "Jail before-image restore requires native punishment/callback cleanup first.");
			testSettings(context);
			final var schema = PhantomTestSchemaManifest.current(context.moduleRoot()); PhantomTestSchemaManifest.requireExact(schema, PhantomTestSchemaManifest.read(PhantomTestSchemaManifest.localPath(context.moduleRoot())));
			try (Connection connection = DatabaseFactory.getConnection())
			{
				PhantomAssertions.assertEquals(PhantomTestDatabaseGuard.TARGET_DATABASE, connection.getCatalog(), "Jail restore database catalog rejected."); PhantomTestSchemaManifest.requireExactDatabaseMetadata(connection, schema);
				try (var select = connection.prepareStatement("SELECT id, expiration, reason, punishedBy FROM punishments WHERE `key` = ? AND affect = 'CHARACTER' AND type = 'JAIL'"))
				{
					select.setString(1, String.valueOf(objectId));
					try (var rows = select.executeQuery())
					{
						if (rows.next())
						{
							final int id = rows.getInt(1); final long expiration = rows.getLong(2); final String reason = rows.getString(3), issuer = rows.getString(4);
							PhantomAssertions.assertTrue(marker.equals(reason) && marker.equals(issuer) && expiration > 0 && !rows.next(), "Jail restoration refused changed/unowned exact row; journal retained.");
							try (var delete = connection.prepareStatement("DELETE FROM punishments WHERE id = ? AND `key` = ? AND affect = 'CHARACTER' AND type = 'JAIL' AND expiration = ? AND reason = ? AND punishedBy = ?"))
							{ delete.setInt(1, id); delete.setString(2, String.valueOf(objectId)); delete.setLong(3, expiration); delete.setString(4, reason); delete.setString(5, issuer); PhantomAssertions.assertEquals(1, delete.executeUpdate(), "Jail full-row CAS restore conflict; journal retained."); }
						}
					}
				}
				PhantomAssertions.assertEquals(0, rowCount(connection, objectId), "Jail exact EMPTY before-image was not restored.");
			}
			context.record("Q12.teleport.jail.beforeAfterRows", "0/0 exact objectId=" + objectId); Files.delete(path);
		}
		private static int rowCount(Connection c, int id) throws Exception { try (var query = c.prepareStatement("SELECT COUNT(*) FROM punishments WHERE `key` = ? AND affect = 'CHARACTER' AND type = 'JAIL'")) { query.setString(1, String.valueOf(id)); try (var rows = query.executeQuery()) { PhantomAssertions.assertTrue(rows.next(), "Jail exact row count unavailable."); return rows.getInt(1); } } }
	}
}
