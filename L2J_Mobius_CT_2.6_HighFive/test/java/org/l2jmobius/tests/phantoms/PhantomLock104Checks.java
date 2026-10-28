/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationCatalog;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyCatalog;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyService;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyState;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationManager;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialCatalog;

/** Bounded, real-owner lock probes. Reflection is confined to test utilities, never runtime recovery. */
public final class PhantomLock104Checks
{
    public static void main(String[] args) throws Exception
    {
        if (args.length != 1) { throw new IllegalArgumentException("Exact module required; DB-free only."); }
        int failures = 0;
        for (int direction = 0; direction < 4; direction++)
        {
            try (var fixture = new Fixture(Path.of(args[0])))
            {
                if (direction == 0) { for (int repeat = 0; repeat < 8; repeat++) { fixture.managerToEcology(); } }
                else if (direction == 1) { for (int repeat = 0; repeat < 8; repeat++) { fixture.ecologyToManager(); } }
                else if (direction == 2)
                {
                    try (var startup = new Fixture(Path.of(args[0]), true))
                    {
                        PhantomAssertions.assertEquals(1, startup.ecology.snapshot().registeredProfiles(), "Ecology metadata pump lost the unpublished READY profile during start.");
                    }
                    fixture.staleAndCancellation();
                }
                else
                {
                    final long begin = System.nanoTime();
                    try (var scale = new Fixture(Path.of(args[0]), false, 5000))
                    {
                        for (int pulse = 0; pulse < 1024; pulse++)
                        {
                            scale.manager.onPulse();
                            PhantomAssertions.assertTrue(scale.manager.snapshot().lastPulseOperations() <= 64, "Ecology-enabled5000 exceeded population pulse budget.");
                        }
                        PhantomAssertions.assertEquals(128, scale.manager.admissionSnapshot().admittedActive(), "Ecology-enabled5000 did not converge to cap128.");
                        PhantomAssertions.assertEquals(5000, scale.ecology.snapshot().registeredProfiles(), "Ecology-enabled5000 lost entries.");
                        System.out.println("LOCK104_ECOLOGY5000 profiles=5000;pulses=1024;admitted=128;elapsedMs=" + (System.nanoTime() - begin) / 1000000);
                    }
                }
                System.out.println("PASS lock104.direction" + direction);
            }
            catch (AssertionError failure) { failures++; System.out.println("FAIL lock104.direction" + direction + " " + failure.getMessage()); }
        }
        PhantomAssertions.assertFalse(DatabaseFactory.isInitialized(), "Lock checks initialized JDBC.");
        PhantomAssertions.assertEquals(null, ManagementFactory.getThreadMXBean().findDeadlockedThreads(), "Checks retained deadlocked threads.");
        if (failures != 0) { throw new AssertionError("LOCK104_FAILED directions=" + failures); }
        System.out.println("LOCK104_PASS tests=4;directions=2x8;staleStopArchiveRevision=REJECTED;ecology5000=PASS;JDBC=0;World=NOT_INITIALIZED");
    }

    private static final class Work
    {
        final AtomicReference<Throwable> error = new AtomicReference<>();
        final CountDownLatch done = new CountDownLatch(1);
        final Thread thread;
        Work(String name, Runnable body)
        {
            thread = new Thread(() -> { try { body.run(); } catch (Throwable failure) { error.set(failure); } finally { done.countDown(); } }, name);
            thread.setDaemon(true);
            thread.start();
        }
        boolean completed(long millis) throws InterruptedException { return done.await(millis, TimeUnit.MILLISECONDS); }
        void finish() throws Exception
        {
            PhantomAssertions.assertTrue(completed(2000), "Bounded worker did not drain: " + thread.getName());
            if (error.get() != null) { throw new AssertionError("Worker failed: " + thread.getName(), error.get()); }
        }
    }

    private static final class Fixture implements AutoCloseable
    {
        final PhantomPopulationManager manager;
        final PhantomPopulationEcologyService ecology;
        final Object populationMonitor;
        final Object ecologyMonitor;
        final PhantomPopulationEcologyGoal033Suite.EcologyMemoryStore states = new PhantomPopulationEcologyGoal033Suite.EcologyMemoryStore(null);
        final PhantomPopulationTestDoubles.MutableClock clock = new PhantomPopulationTestDoubles.MutableClock(Instant.parse("2026-01-05T20:30:00Z"));

        Fixture(Path module) throws Exception { this(module, false, 1); }
        Fixture(Path module, boolean inlinePump) throws Exception { this(module, inlinePump, 1); }
        Fixture(Path module, boolean inlinePump, int profiles) throws Exception
        {
            final var population = PhantomPopulationCatalog.load(module.resolve("dist/game/data/phantoms/population/high-five-population-v1.xml"), ZoneOffset.UTC);
            final var social = PhantomSocialCatalog.load(module.resolve("dist/game/data/phantoms/social/high-five-social-v1.xml"));
            final var catalog = PhantomPopulationEcologyCatalog.load(module.resolve("dist/game/data/phantoms/population/high-five-ecology-v1.xml"), population, social);
            final var store = new PhantomPopulationTestDoubles.MemoryStore(population.hash());
            final long now = clock.instant().toEpochMilli() / 60000;
            for (long id = 1; id <= profiles; id++)
            {
                store.seedReady(id, 1);
                states.insert(id, catalog.assign(PhantomPopulationEcologyState.Preset.LIVING, 1, id, 16001601, now, 0, 0, "evening").advanceCalendar(now));
            }
            ecology = new PhantomPopulationEcologyService(catalog, population, states,
                new PhantomPopulationEcologyGoal033Suite.HistoricalMemoryPort(), id -> false, id -> "", clock, ZoneOffset.UTC,
                PhantomPopulationEcologyState.Preset.LIVING, 0, 10, worker -> { if (!inlinePump) { return false; } worker.run(); return true; });
            if (inlinePump)
            {
                final var firstWake = new java.util.concurrent.atomic.AtomicBoolean(true);
                ecology.installDemandPump(1, () -> 0, (task, delay) -> { if (firstWake.getAndSet(false)) { task.run(); } return () -> {}; });
            }
            manager = new PhantomPopulationManager(store, population, null, new PhantomPopulationTestDoubles.Ownership(), clock, ZoneOffset.UTC, profiles, Math.min(128, profiles), Math.max(2, profiles), Math.min(128, profiles), 1, 64);
            manager.installEcology(ecology);
            PhantomAssertions.assertTrue(manager.start(), "Fixture start.");
            populationMonitor = field(manager, "_monitor");
            ecologyMonitor = field(ecology, "_monitor");
            for (long id = 1; id <= profiles; id++) { invoke(ecology, "process", id, 1, false, true); }
        }

        void managerToEcology() throws Exception
        {
            ecology.holdStartupPopulationPlan();
            invoke(manager, "queueEcologyScheduleRefresh", 1L);
            ecology.startupPopulationPlanApplied();
            final var queue = (java.util.Collection<?>) field(manager, "_retryActions");
            final Object action = queue.stream().filter(value -> value.toString().contains("READY_SCHEDULE")).findFirst().orElseThrow();
            Work schedule;
            Work reader = null;
            boolean free;
            synchronized (ecologyMonitor)
            {
                schedule = new Work("lock104-manager-schedule", () -> invoke(manager, "processReadySchedule", action, clock.instant()));
                awaitBlocked(schedule, ecologyMonitor);
                reader = new Work("lock104-manager-reader", () -> manager.admissionProfile(1));
                free = reader.completed(300);
            }
            schedule.finish();
            reader.finish();
            PhantomAssertions.assertTrue(free, "Manager retained its monitor while waiting for Ecology in actual processReadySchedule.");
        }

        void ecologyToManager() throws Exception
        {
            final var callbackEntered = new CountDownLatch(1);
            final var nested = new java.util.concurrent.atomic.AtomicBoolean();
            ecology.installMaterializationDemand(id ->
            {
                nested.set(Thread.holdsLock(ecologyMonitor));
                callbackEntered.countDown();
                return manager.admissionProfile(id).isPresent();
            });
            Work request;
            Work reader;
            boolean free;
            synchronized (populationMonitor)
            {
                request = new Work("lock104-ecology-demand", () -> ecology.requestMaterializationDue(1));
                PhantomAssertions.assertTrue(callbackEntered.await(2, TimeUnit.SECONDS), "Actual demand callback not entered.");
                awaitBlocked(request, populationMonitor);
                reader = new Work("lock104-ecology-reader", () -> ecology.dueSnapshot(1));
                free = reader.completed(300);
            }
            request.finish();
            reader.finish();
            PhantomAssertions.assertTrue(free && !nested.get(), "Ecology retained its monitor across demand callback re-entering Manager.");
        }

        void staleAndCancellation() throws Exception
        {
            final var facts = List.of(new PhantomPopulationEcologyService.DemandFact(1, 1, true, 1, 1));
            ecology.updateMaterializationDemand(facts, 1);
            for (int pulse = 0; pulse < 8; pulse++) { manager.onPulse(); }
            PhantomAssertions.assertTrue(manager.admissionProfile(1).orElseThrow().admitted(), "Completed current profile was not admitted.");
            ecology.updateMaterializationDemand(facts, 1);
            PhantomAssertions.assertTrue(manager.admissionProfile(1).orElseThrow().admitted(), "Unchanged locality facts revoked ordinary admission.");
            var snapshot = ecology.schedulingSnapshot(List.of(1L), clock.instant());
            PhantomAssertions.assertTrue(snapshot.permitted(1) && ecology.schedulingSnapshotCurrent(snapshot), "Current completed ecology was not schedulable.");
            ecology.installMaterializationDemand(id -> { ecology.withdrawMaterializationDue(id); return manager.admissionProfile(id).isPresent(); });
            final var stale = ecology.requestMaterializationDue(1);
            PhantomAssertions.assertFalse(stale.complete(), "Callback withdrawal published a stale positive receipt.");
            PhantomAssertions.assertEquals("ecology.readiness_stale", stale.reason(), "Withdrawal did not fence the captured request.");
            PhantomAssertions.assertTrue(ecology.schedulingSnapshotCurrent(snapshot), "Demand-only withdrawal revoked scheduling facts.");

            clock.set(clock.instant().plusSeconds(60));
            PhantomAssertions.assertFalse(ecology.schedulingSnapshotCurrent(snapshot), "Non-periodic scheduling facts survived calendar expiry.");
            PhantomAssertions.assertFalse(ecology.permitsScheduling(1), "Expired non-periodic cursor was schedulable.");
            clock.set(clock.instant().minusSeconds(60));
            ecology.installMaterializationDemand(id -> { clock.set(clock.instant().plusSeconds(60)); return true; });
            PhantomAssertions.assertFalse(ecology.requestMaterializationDue(1).complete(), "Due receipt survived minute expiry during external demand callback.");
            clock.set(clock.instant().minusSeconds(60));

            snapshot = ecology.schedulingSnapshot(List.of(1L), clock.instant());
            ecology.updateParticipation(1, false);
            PhantomAssertions.assertFalse(ecology.schedulingSnapshotCurrent(snapshot) || ecology.permitsScheduling(1), "Paused participation retained admission.");
            PhantomAssertions.assertEquals(0, manager.admissionSnapshot().admittedActive(), "Aggregate reported stale admission while every profile was fenced.");
            PhantomAssertions.assertTrue(manager.admissionSnapshot().pendingRebalance(), "Aggregate hid stale ecology revision.");
            ecology.updateParticipation(1, true);
            snapshot = ecology.schedulingSnapshot(List.of(1L), clock.instant());
            ecology.holdStartupPopulationPlan();
            PhantomAssertions.assertFalse(ecology.schedulingSnapshotCurrent(snapshot) || ecology.permitsScheduling(1), "Startup hold retained admission.");
            ecology.startupPopulationPlanApplied();

            var stored = states.load(1).orElseThrow();
            final long minute = clock.instant().toEpochMilli() / 60000;
            final var pending = states.save(1, stored, stored.state().beginRequest("a".repeat(64), minute + 1));
            snapshot = ecology.schedulingSnapshot(List.of(1L), clock.instant());
            invoke(ecology, "publish", 1L, pending);
            PhantomAssertions.assertFalse(ecology.schedulingSnapshotCurrent(snapshot) || ecology.permitsScheduling(1), "Pending history retained ordinary admission.");
            stored = states.save(1, pending, pending.state().completeRequest());
            invoke(ecology, "publish", 1L, stored);
            snapshot = ecology.schedulingSnapshot(List.of(1L), clock.instant());
            final var archived = states.save(1, stored, stored.state().archived(1, minute + 1, "test.archive"));
            invoke(ecology, "publish", 1L, archived);
            PhantomAssertions.assertFalse(ecology.schedulingSnapshotCurrent(snapshot) || ecology.permitsScheduling(1), "Archive retained admission.");
            PhantomAssertions.assertFalse(ecology.schedulingSnapshot(List.of(1L), clock.instant()).managed(1), "Archived identity counted as managed.");

            ecology.installMaterializationDemand(id -> { ecology.beginStop(); return true; });
            PhantomAssertions.assertFalse(ecology.requestMaterializationDue(1).complete(), "STOP during external callback published readiness.");
            PhantomAssertions.assertFalse(ecology.permitsScheduling(1), "STOP retained scheduling permission.");
            PhantomAssertions.assertTrue(ecology.finishStop(), "Empty native STOP did not finish.");
        }

        @Override public void close()
        {
            manager.beginStop();
            PhantomAssertions.assertTrue(manager.finishStop(), "Fixture stop.");
        }
    }

    private static void awaitBlocked(Work work, Object monitor) throws Exception
    {
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < deadline)
        {
            final var info = ManagementFactory.getThreadMXBean().getThreadInfo(work.thread.threadId());
            if (info != null && info.getThreadState() == Thread.State.BLOCKED && info.getLockInfo() != null && info.getLockInfo().getIdentityHashCode() == System.identityHashCode(monitor)) { return; }
            if (work.done.getCount() == 0) { throw new AssertionError("Worker finished before controlled monitor contention: " + work.error.get()); }
            java.util.concurrent.locks.LockSupport.parkNanos(100_000);
        }
        throw new AssertionError("Worker never reached exact controlled monitor.");
    }

    private static Object field(Object owner, String name) throws Exception
    {
        final Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }
    private static void invoke(Object owner, String name, Object... args)
    {
        try
        {
            final Method method = java.util.Arrays.stream(owner.getClass().getDeclaredMethods()).filter(value -> value.getName().equals(name) && value.getParameterCount() == args.length).findFirst().orElseThrow();
            method.setAccessible(true);
            method.invoke(owner, args);
        }
        catch (ReflectiveOperationException failure) { throw new AssertionError("Actual owner method failed: " + name, failure); }
    }
}
