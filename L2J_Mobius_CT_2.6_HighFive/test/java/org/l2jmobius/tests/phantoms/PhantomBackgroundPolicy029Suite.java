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
        registry.add("B05-unsupported-party-keeps-native-persistence", context ->
        {
            final var player = Player.load(_environment.primary().objectId());
            try (var output = player.attachOutboundSession(new HeadlessPlayerOutboundSession(8, 128)))
            {
                player.stopAllTasks();
                final var party = new org.l2jmobius.gameserver.model.groups.Party(player, org.l2jmobius.gameserver.model.groups.PartyDistributionType.FINDERS_KEEPERS);
                player.setParty(party);
                try
                {
                    final var capture = _production.authority().captureNativeContext(player);
                    PhantomAssertions.assertFalse(capture.policy().ordinaryRewards(), "Party cannot become solo ordinary FARM.");
                    PhantomAssertions.assertTrue((capture.policy().unsupportedFacts() & 2) != 0, "Party exclusion has captured facts.");
                    context.record("B05.partyCapture", capture.toString());
                    final var identity = new Identity(1, player.getObjectId(), player.getClassIndex(), player.getActiveClass(), player.getRace().ordinal());
                    PhantomAssertions.assertFalse(PhantomNativeContext.completed(identity, capture, 0, new byte[] { 1 }).simulationEligible(), "Unsupported new capsule cannot leak legacy SUPPORTED permission.");
                }
                finally { player.setParty(null); party.disbandParty(); }
            }
            finally { _environment.cleanupLoadedPlayer(player); }
        });
        registry.add("B02-native-scalar-threshold-and-float-oracle", context ->
        {
            final var player = Player.load(_environment.primary().objectId());
            try (var output = player.attachOutboundSession(new HeadlessPlayerOutboundSession(8, 128)))
            {
                player.stopAllTasks();
                // Actual stock level predicate: no Lucky at11, so this vector consumes vitality.
                player.getStat().setLevel((byte) 11);
                player.getStat().setExp(org.l2jmobius.gameserver.data.xml.ExperienceData.getInstance().getExpForLevel(11));
                PhantomAssertions.assertFalse(player.isLucky(), "Non-Lucky native consumption oracle.");
                final var monster = new org.l2jmobius.gameserver.model.actor.instance.Monster(org.l2jmobius.gameserver.data.xml.NpcData.getInstance().getTemplate(20534));
                final var floatField = player.getStat().getClass().getDeclaredField("_vitalityPoints"); floatField.setAccessible(true);
                try
                {
                    for (int points : new int[] { 1, 240, 241, 421, 2000, 2001, 13000, 13001, 16361, 17000, 17001, 20000 })
                    {
                        player.getStat().setVitalityPoints(points, true);
                        final var policy = _production.authority().captureNativeContext(player).policy();
                        float carried = points;
                        for (int i = 0; i < 6; i++)
                        {
                            final double expectedXp = player.getStat().getExpBonusMultiplier(), expectedSp = player.getStat().getSpBonusMultiplier();
                            PhantomAssertions.assertEquals(expectedXp, org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundRewardKernel.multiplier(carried, policy, true), "Native pre-consume XP bonus.");
                            PhantomAssertions.assertEquals(expectedSp, org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundRewardKernel.multiplier(carried, policy, false), "Native pre-consume SP bonus.");
                            final long beforeXp = player.getExp(), beforeSp = player.getSp();
                            player.addExpAndSp(1, 1, true);
                            final float nativeDelta = monster.getVitalityPoints(player.getLevel(), (long) Math.ceil(monster.getMaxHp()));
                            final float scalarDelta = org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundRewardKernel.targetDelta((long) Math.ceil(monster.getMaxHp()), monster.getLevel(), monster.getExpReward(player.getLevel()), monster.getTemplate().getBaseHpMax(), (float) monster.getMaxHp());
                            PhantomAssertions.assertEquals(Float.floatToIntBits(nativeDelta), Float.floatToIntBits(scalarDelta), "Actual Attackable target delta bits.");
                            final var awarded = org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundRewardKernel.award(1, 1, carried, nativeDelta, policy, true);
                            PhantomAssertions.assertEquals(awarded.experience(), player.getExp() - beforeXp, "Stock addExpAndSp XP rounding.");
                            PhantomAssertions.assertEquals(awarded.skillPoints(), player.getSp() - beforeSp, "Stock addExpAndSp SP rounding.");
                            player.getStat().updateVitalityPoints(nativeDelta, true, true);
                            carried = awarded.points();
                            PhantomAssertions.assertEquals(Float.floatToIntBits(carried), Float.floatToIntBits(floatField.getFloat(player.getStat())), "Stock repeated float carry/clamp.");
                        }
                        context.record("B02.native." + points, "PASS6 native encounters; finalFloat=" + carried);
                    }
                }
                finally { monster.deleteMe(); }
            }
            finally { _environment.cleanupLoadedPlayer(player); }
        });
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
