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
                final var result = background.farm(id, goal, 1, 1, PhantomActivityState.BACKGROUND, System.nanoTime());
                context.record("T03.farm", result.toString());
                PhantomAssertions.assertTrue(result.successful(), "Non1 ordinary native release must commit productive background FARM: " + result);
                final var after = transaction.load(id).state();
                PhantomAssertions.assertTrue(after.progress().experience() > before.progress().experience(), "Actual productive XP commit required.");
                final var proof = transaction.nativeContext(id, player.getObjectId());
                context.record("T03.policy", proof.toString());
                PhantomAssertions.assertTrue(proof.context().afterPoints() < 16361, "Native-compatible background vitality consumption.");
            }
        });
    }
        private static void assign(Object target, String name, Object value) throws Exception
    {
        final var field = target.getClass().getDeclaredField(name); field.setAccessible(true); field.set(target, value);
    }
    private static Object field(Object target, String name) throws Exception
    {
        final var field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target);
    }
}
