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
        registry.add("T03-real-non1-release-background-commit", context ->
        {
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
    private static Object field(Object target, String name) throws Exception
    {
        final var field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target);
    }
}
