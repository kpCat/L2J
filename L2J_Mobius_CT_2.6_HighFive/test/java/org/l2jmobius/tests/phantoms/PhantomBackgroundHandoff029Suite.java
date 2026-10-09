/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.file.Path;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundService;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundTransaction;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoal;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService;

/** Reuses the existing actual native composed production fixture. */
public final class PhantomBackgroundHandoff029Suite implements PhantomTestSuite
{
    private final PhantomBackgroundSuite base = new PhantomBackgroundSuite(PhantomBackgroundSuite.Mode.NATIVE_LIFECYCLE);
    public static void main(String[] args)
    {
        final var context = new PhantomTestContext(15001501, Path.of(args[0]), Path.of(args[1]));
        System.exit(PhantomTestLauncher.runSuite("background-handoff029", new PhantomBackgroundHandoff029Suite(), context));
    }
    @Override public String id() { return "background-handoff029"; }
    @Override public void beforeAll(PhantomTestContext context) throws Exception { base.beforeAll(context); }
    @Override public void afterAll(PhantomTestContext context) throws Exception { base.afterAll(context); }
    @Override public void register(PhantomTestRegistry registry)
    {
        registry.add("T04-T06-real-lease-and-exact-lost-reply", context ->
        {
            for (boolean committed : new boolean[] { false, true })
            {
                final var environment = (PhantomHeadlessPlayerTestEnvironment) field(base, "_environment");
                final var reset = Player.load(environment.primary().objectId());
                try { reset.stopAllTasks(); reset.getStat().setVitalityPoints(1, true); }
                finally { environment.cleanupLoadedPlayer(reset); }
                final var armed = new java.util.concurrent.atomic.AtomicBoolean();
                final var loseReply = new java.util.concurrent.atomic.AtomicBoolean();
                final var nativeOwner = new java.util.concurrent.atomic.AtomicReference<PhantomMaterializationService>();
                final var profile = new java.util.concurrent.atomic.AtomicLong();
                final var races = new java.util.concurrent.atomic.AtomicInteger();
                final var transaction = new PhantomBackgroundTransaction(() ->
                {
                    final var delegate = org.l2jmobius.commons.database.DatabaseFactory.getConnection();
                    return (java.sql.Connection) java.lang.reflect.Proxy.newProxyInstance(java.sql.Connection.class.getClassLoader(), new Class<?>[] { java.sql.Connection.class }, (proxy, method, arguments) ->
                    {
                        if (method.getName().equals("commit") && loseReply.compareAndSet(true, false))
                        {
                            if (committed) { delegate.commit(); } else { delegate.rollback(); }
                            throw new java.sql.SQLException("CONTROLLED_COMMIT_REPLY_LOST_029");
                        }
                        try { return method.invoke(delegate, arguments); }
                        catch (java.lang.reflect.InvocationTargetException failure) { throw failure.getCause(); }
                    });
                }, PhantomBackgroundTransaction.ObjectIdAllocator.production(), point ->
                {
                    if (armed.get() && point == PhantomBackgroundTransaction.FaultPoint.AFTER_CHARACTER_LOCK)
                    {
                        final var admission = nativeOwner.get().materialize(profile.get());
                        PhantomAssertions.assertFalse(admission.status() == PhantomMaterializationService.ResultStatus.SUCCESS, "Held background lease must fence actual native materialization.");
                        races.incrementAndGet();
                    }
                    if (armed.get() && point == PhantomBackgroundTransaction.FaultPoint.AFTER_BACKGROUND_STATE_WRITE) { loseReply.set(true); }
                });
                final var open = PhantomBackgroundSuite.class.getDeclaredMethod("openNativeProductionFixture", PhantomTestContext.class, boolean.class, PhantomBackgroundTransaction.class); open.setAccessible(true);
                try (var fixture = (AutoCloseable) open.invoke(base, context, true, transaction))
                {
                    final var id = fixture.getClass().getDeclaredMethod("id"); id.setAccessible(true); profile.set((long) id.invoke(fixture));
                    final var playerMethod = fixture.getClass().getDeclaredMethod("player"); playerMethod.setAccessible(true); ((Player) playerMethod.invoke(fixture)).getStat().setVitalityPoints(16361, true);
                    nativeOwner.set((PhantomMaterializationService) field(fixture, "materialization"));
                    PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, nativeOwner.get().dematerialize(profile.get()).status(), "Lost-reply native release.");
                    final Object seed = field(fixture, "seed"); final var goalMethod = seed.getClass().getDeclaredMethod("goal"); goalMethod.setAccessible(true); final var goal = (PhantomGoal) goalMethod.invoke(seed);
                    final var before = transaction.load(profile.get()).state(); armed.set(true);
                    final var result = ((PhantomBackgroundService) field(fixture, "background")).farm(profile.get(), goal, 4, 6, PhantomActivityState.BACKGROUND, System.nanoTime());
                    final var after = transaction.load(profile.get()).state();
                    context.record("T06." + committed, result.toString() + ";before=" + before.progress() + ";after=" + after.progress());
                    PhantomAssertions.assertEquals(1, races.get(), "One actual fenced native admission.");
                    if (!committed)
                    {
                        PhantomAssertions.assertFalse(result.successful(), "Uncommitted lost reply must not accept the old unrelated receipt as success.");
                        PhantomAssertions.assertEquals(before, after, "Rolled back scalar/state projection.");
                    }
                    else
                    {
                        PhantomAssertions.assertEquals(PhantomBackgroundService.OperationStatus.IDEMPOTENT, result.status(), "Exact durable operation id must resolve as IDEMPOTENT.");
                        PhantomAssertions.assertTrue(after.progress().experience() > before.progress().experience(), "Lost reply committed productive reward exactly once.");
                    }
                }
            }
        });
        registry.add("T01-factual-off-area-no-reward-return", context ->
        {
            final var environment = (PhantomHeadlessPlayerTestEnvironment) field(base, "_environment");
            final var reset = Player.load(environment.primary().objectId());
            try { reset.stopAllTasks(); reset.getStat().setVitalityPoints(1, true); }
            finally { environment.cleanupLoadedPlayer(reset); }
            final var method = PhantomBackgroundSuite.class.getDeclaredMethod("openNativeProductionFixture", PhantomTestContext.class, boolean.class); method.setAccessible(true);
            try (var fixture = (AutoCloseable) method.invoke(base, context, true))
            {
                final var playerMethod = fixture.getClass().getDeclaredMethod("player"); playerMethod.setAccessible(true);
                final var player = (Player) playerMethod.invoke(fixture);
                final var idMethod = fixture.getClass().getDeclaredMethod("id"); idMethod.setAccessible(true); final long id = (long) idMethod.invoke(fixture);
                final var production = (PhantomBackgroundSuite.ProductionAuthorityFixture) field(base, "_production");
                final var anchor = production.topology().findAnchor("population.farming.elf.20534").orElseThrow();
                final var area = production.topology().findNode(anchor.nodeId()).orElseThrow().area();
                final var geo = org.l2jmobius.gameserver.geoengine.GeoEngine.getInstance();
                final int x = area.minX() - 64, y = anchor.point().y(), z = geo.getHeight(x, y, player.getZ());
                player.setXYZInvisible(x, y, z); player.getStat().setVitalityPoints(16361, true);
                final var transaction = (PhantomBackgroundTransaction) field(fixture, "transaction");
                final var materialization = (PhantomMaterializationService) field(fixture, "materialization");
                final Object seed = field(fixture, "seed"); final var goalMethod = seed.getClass().getDeclaredMethod("goal"); goalMethod.setAccessible(true);
                final var goal = (PhantomGoal) goalMethod.invoke(seed);
                PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, materialization.dematerialize(id).status(), "Native store must preserve factual off-area XYZ.");
                final var state = transaction.load(id).state(); final var proof = transaction.nativeContext(id, player.getObjectId());
                PhantomAssertions.assertEquals(x, state.position().x(), "Actual XYZ must not normalize to anchor.");
                PhantomAssertions.assertFalse(proof.context().afterPolicy().farmPosition(), "Off-area FARM denied.");
                final var spec = org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundGoalSpec.parse(goal);
                final var advance = production.authority().advanceTravel(state, spec, 1, 0, proof.context().afterPolicy());
                context.record("T01.actualTravel", advance.toString());
                PhantomAssertions.assertEquals(org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundAuthority.TravelAdvance.Status.PARTIAL, advance.status(), "Actual off-area origin requires validated timed TRAVEL, not AT_DESTINATION.");
                PhantomAssertions.assertEquals(state.position(), advance.position(), "Partial travel must preserve originXYZ.");
                final var background = (PhantomBackgroundService) field(fixture, "background");
                final var committed = background.travel(id, goal, 1, 1, PhantomActivityState.BACKGROUND, System.nanoTime());
                context.record("T01.committed", committed.toString());
                PhantomAssertions.assertTrue(committed.successful(), "Validated local return must atomically commit.");
                final var after = transaction.load(id).state(); final var afterProof = transaction.nativeContext(id, player.getObjectId());
                PhantomAssertions.assertEquals(state.progress(), after.progress(), "TRAVEL cannot award XP/SP.");
                PhantomAssertions.assertEquals(state.inventory(), after.inventory(), "TRAVEL cannot alter items.");
                PhantomAssertions.assertEquals(state.autoGetSkills(), after.autoGetSkills(), "TRAVEL cannot alter skills.");
                PhantomAssertions.assertEquals(proof.context().afterPolicy().points(), afterProof.context().afterPolicy().points(), "TRAVEL cannot change float vitality.");
                PhantomAssertions.assertTrue(afterProof.context().afterPolicy().farmPosition(), "Arrival proves farming area.");
            }
        });
        registry.add("T03-historical-ordinary-prerequisite", context ->
        {
            final var history = new PhantomNativeContextHandoffSuite();
            assign(history, "_environment", field(base, "_environment"));
            assign(history, "_profiles", field(base, "_repository"));
            assign(history, "_production", field(base, "_production"));
            try (var fixture = history.new Fixture(true))
            {
                fixture.handoff();
                try (var action = fixture.materialization.tryAcquireAction(fixture.id).orElseThrow())
                { action.player().getStat().setVitalityPoints(16361, true); }
                PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, fixture.materialization.dematerialize(fixture.id).status(), "Historical real native release.");
                final var snapshot = fixture.catchups.load(fixture.id).orElseThrow();
                final var method = fixture.historical.getClass().getDeclaredMethod("ensureNativeContext", long.class, snapshot.getClass(), org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.class);
                method.setAccessible(true);
                final var result = (org.l2jmobius.gameserver.phantoms.background.PhantomHistoricalBackgroundService.Result) method.invoke(fixture.historical, fixture.id, snapshot, fixture.transactions.load(fixture.id).state());
                context.record("T03.historical", result.toString());
                PhantomAssertions.assertTrue(result.successful(), "Ordinary non1 must pass actual historical prerequisite: " + result);
            }
        });
        registry.add("T03-real-non1-release-background-commit", context ->
        {
            final var environment = (PhantomHeadlessPlayerTestEnvironment) field(base, "_environment");
            final var reset = Player.load(environment.primary().objectId());
            try { reset.stopAllTasks(); reset.getStat().setVitalityPoints(1, true); }
            finally { environment.cleanupLoadedPlayer(reset); }
            final var method = PhantomBackgroundSuite.class.getDeclaredMethod("openNativeProductionFixture", PhantomTestContext.class, boolean.class);
            method.setAccessible(true);
            try (var fixture = (AutoCloseable) method.invoke(base, context, true))
            {
                final var type = fixture.getClass();
                final var playerMethod = type.getDeclaredMethod("player"); playerMethod.setAccessible(true);
                final var idMethod = type.getDeclaredMethod("id"); idMethod.setAccessible(true);
                final var player = (Player) playerMethod.invoke(fixture);
                final long id = (long) idMethod.invoke(fixture);
                player.getStat().setVitalityPoints(16361, true); // Own TEST oracle setup only.
                final var materialization = (PhantomMaterializationService) field(fixture, "materialization");
                final var transaction = (PhantomBackgroundTransaction) field(fixture, "transaction");
                final var background = (PhantomBackgroundService) field(fixture, "background");
                final Object seed = field(fixture, "seed");
                final var goalMethod = seed.getClass().getDeclaredMethod("goal"); goalMethod.setAccessible(true);
                final var goal = (PhantomGoal) goalMethod.invoke(seed);
                final var released = materialization.dematerialize(id);
                context.record("T03.release", released.toString());
                PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, released.status(), "Actual native store/release.");
                final var before = transaction.load(id).state();
                final var observed = new java.util.concurrent.atomic.AtomicInteger();
                try (var observation = observeCommits(context, observed))
                {
                final var result = background.farm(id, goal, 1, 1, PhantomActivityState.BACKGROUND, System.nanoTime());
                context.record("T03.farm", result.toString());
                PhantomAssertions.assertTrue(result.successful(), "Non1 ordinary native release must commit productive background FARM: " + result);
                final var after = transaction.load(id).state();
                PhantomAssertions.assertTrue(after.progress().experience() > before.progress().experience(), "Actual productive XP commit required.");
                final var proof = transaction.nativeContext(id, player.getObjectId());
                context.record("T03.policy", proof.toString());
                PhantomAssertions.assertTrue(proof.context().afterPoints() < 16361, "Native-compatible background vitality consumption.");
                PhantomAssertions.assertEquals(1, observed.get(), "P04 exact productive background edge must be observable at the verified operation boundary, not inferred from late SQL.");
                }
            }
        });
    }
        private static void assign(Object target, String name, Object value) throws Exception
    {
        final var field = target.getClass().getDeclaredField(name); field.setAccessible(true); field.set(target, value);
    }
    private static AutoCloseable observeCommits(PhantomTestContext context, java.util.concurrent.atomic.AtomicInteger count) throws Exception
    {
        final java.lang.reflect.Field observer;
        try { observer = PhantomBackgroundService.class.getDeclaredField("_commitObserver"); }
        catch (NoSuchFieldException missing) { context.record("P04.existingProducer", "ABSENT"); return () -> { }; }
        observer.setAccessible(true);
        observer.set(null, (java.util.function.BiConsumer<PhantomBackgroundTransaction.Command, PhantomBackgroundTransaction.Result>) (command, result) ->
        {
            PhantomAssertions.assertTrue(result.successful() && result.state().receipt().operationKey().equals(command.operationKey().digest()), "Exact committed operation observer.");
            count.incrementAndGet();
        });
        return () -> observer.set(null, null);
    }
    private static Object field(Object target, String name) throws Exception
    {
        final var field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target);
    }
}
