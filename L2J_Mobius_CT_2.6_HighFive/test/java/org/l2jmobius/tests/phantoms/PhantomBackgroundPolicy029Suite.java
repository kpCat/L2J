/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.file.Path;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.phantoms.background.PhantomNativeContext;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.Identity;
import org.l2jmobius.gameserver.phantoms.player.HeadlessPlayerOutboundSession;

/** Actual stock Player capture, not fabricated canonical permission. */
public final class PhantomBackgroundPolicy029Suite implements PhantomTestSuite
{
    private final PhantomHeadlessPlayerTestEnvironment _environment = new PhantomHeadlessPlayerTestEnvironment();
    private PhantomBackgroundSuite.ProductionAuthorityFixture _production;
    public static void main(String[] args)
    {
        final var context = new PhantomTestContext(29002901, Path.of(args[0]), Path.of(args[1]));
        System.exit(PhantomTestLauncher.runSuite("background-policy029", new PhantomBackgroundPolicy029Suite(), context));
    }
    @Override public String id() { return "background-policy029"; }
    @Override public void beforeAll(PhantomTestContext context) throws Exception
    {
        _environment.initialize(context);
        _production = PhantomBackgroundSuite.ProductionAuthorityFixture.start();
    }
    @Override public void afterAll(PhantomTestContext context) throws Exception
    {
        try { if (_production != null) { _production.close(); } }
        finally { _environment.shutdown(); }
    }
    @Override public void register(PhantomTestRegistry registry)
    {
        registry.add("B01-ordinary-native-non1-farm-policy", context ->
        {
            final var player = Player.load(_environment.primary().objectId());
            try (var output = player.attachOutboundSession(new HeadlessPlayerOutboundSession(8, 128)))
            {
                player.stopAllTasks();
                // Controlled oracle setup in own TEST; no product character altered.
                player.getStat().setVitalityPoints(16361, true);
                final var capture = _production.authority().captureNativeContext(player);
                PhantomAssertions.assertEquals(16361, capture.vitalityPoints(), "Native integer projection.");
                context.record("B01.base.capture", capture.toString());
                final var identity = new Identity(1, player.getObjectId(), player.getClassIndex(), player.getActiveClass(), player.getRace().ordinal());
                final var proof = PhantomNativeContext.completed(identity, capture, 0, new byte[] { 1 });
                PhantomAssertions.assertTrue(proof.afterPolicy() != null && proof.afterPolicy().ordinaryRewards(), "Ordinary native vitality16361 must have captured scalar FARM policy; baseV1 rejects it.");
                PhantomAssertions.assertFalse(proof.simulationEligible(), "Legacy points1 predicate remains unchanged.");
                PhantomAssertions.assertEquals(proof, PhantomNativeContext.decode(proof.encode()), "V2 exact roundtrip.");
            }
            finally { _environment.cleanupLoadedPlayer(player); }
        });
    }
}
