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
                final boolean premiumEnabled = org.l2jmobius.gameserver.config.custom.PremiumSystemConfig.PREMIUM_SYSTEM_ENABLED;
                org.l2jmobius.gameserver.config.custom.PremiumSystemConfig.PREMIUM_SYSTEM_ENABLED = true; player.setPremiumStatus(true);
                try
                {
                    boolean persisted = false;
                    try
                    {
                        final var premium = _production.authority().captureNativeContext(player);
                        persisted = !premium.policy().ordinaryRewards() && (premium.policy().unsupportedFacts() & 1) != 0;
                    }
                    catch (IllegalArgumentException failure) { context.record("B05.premiumRejected", failure.toString()); }
                    PhantomAssertions.assertTrue(persisted, "Unsupported premium rewards must preserve native persistence with explicit policy facts.");
                }
                finally { player.setPremiumStatus(false); org.l2jmobius.gameserver.config.custom.PremiumSystemConfig.PREMIUM_SYSTEM_ENABLED = premiumEnabled; }
            }
            finally { _environment.cleanupLoadedPlayer(player); }
        });
        registry.add("B02-B03-native-controls-and-independent-caps", context ->
        {
            final var player = Player.load(_environment.primary().objectId());
            final boolean enabled = org.l2jmobius.gameserver.config.PlayerConfig.ENABLE_VITALITY;
            final double capXp = org.l2jmobius.gameserver.config.PlayerConfig.MAX_BONUS_EXP, capSp = org.l2jmobius.gameserver.config.PlayerConfig.MAX_BONUS_SP;
            try (var output = player.attachOutboundSession(new HeadlessPlayerOutboundSession(8, 128)))
            {
                player.stopAllTasks(); player.getStat().setLevel((byte) 11);
                player.getStat().setExp(org.l2jmobius.gameserver.data.xml.ExperienceData.getInstance().getExpForLevel(11));
                final var skills = org.l2jmobius.gameserver.data.xml.SkillData.getInstance();
                player.addSkill(skills.getSkill(22039, 1), false); player.addSkill(skills.getSkill(22040, 2), false);
                final var floatField = player.getStat().getClass().getDeclaredField("_vitalityPoints"); floatField.setAccessible(true);
                for (double cap : new double[] { 0, 2 })
                {
                    org.l2jmobius.gameserver.config.PlayerConfig.MAX_BONUS_EXP = cap;
                    org.l2jmobius.gameserver.config.PlayerConfig.MAX_BONUS_SP = cap;
                    for (boolean vitality : new boolean[] { true, false })
                    {
                        org.l2jmobius.gameserver.config.PlayerConfig.ENABLE_VITALITY = vitality;
                        for (boolean use : new boolean[] { true, false })
                        {
                            for (long baseXp : new long[] { 0, 1 })
                            {
                                player.getStat().setVitalityPoints(16361, true);
                                final var policy = _production.authority().captureNativeContext(player).policy();
                                PhantomAssertions.assertEquals(30.0, policy.bonusExpPercent(), "Actual independent EXP rune, before cap.");
                                PhantomAssertions.assertEquals(50.0, policy.bonusSpPercent(), "Actual independent SP rune, before cap.");
                                PhantomAssertions.assertEquals(player.getStat().getExpBonusMultiplier(), org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundRewardKernel.multiplier(16361, policy, true), "Native cap without doubling old total.");
                                final long beforeXp = player.getExp(), beforeSp = player.getSp();
                                player.addExpAndSp(baseXp, 1, use);
                                final var award = org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundRewardKernel.award(baseXp, 1, 16361, -123.25f, policy, use);
                                if (baseXp > 0 && use) { player.getStat().updateVitalityPoints(-123.25f, true, true); }
                                PhantomAssertions.assertEquals(award.experience(), player.getExp() - beforeXp, "Native zeroXP/use=false/enabled=false XP.");
                                PhantomAssertions.assertEquals(award.skillPoints(), player.getSp() - beforeSp, "Native independent SP cap.");
                                PhantomAssertions.assertEquals(Float.floatToIntBits(award.points()), Float.floatToIntBits(floatField.getFloat(player.getStat())), "Native disabled/zeroXP/use=false points.");
                            }
                        }
                    }
                }
                org.l2jmobius.gameserver.config.PlayerConfig.ENABLE_VITALITY = true;
                player.stopAllEffects();
                for (int skill : new int[] { 8360, 2580 })
                {
                    skills.getSkill(skill, 1).applyEffects(player, player);
                    player.getStat().setVitalityPoints(16361, true);
                    final var policy = _production.authority().captureNativeContext(player).policy();
                    PhantomAssertions.assertTrue(skill == 8360 ? policy.consumeStat() == 0 : policy.consumeStat() < 0, "Actual XML consume effect.");
                    for (float delta : new float[] { -0.25f, -100000, 100000 })
                    {
                        final float before = floatField.getFloat(player.getStat());
                        final float expected = org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundRewardKernel.update(before, delta, policy);
                        player.getStat().updateVitalityPoints(delta, true, true);
                        PhantomAssertions.assertEquals(Float.floatToIntBits(expected), Float.floatToIntBits(floatField.getFloat(player.getStat())), "Native consume0/sign/rates/clamp float bits.");
                    }
                    player.stopAllEffects();
                }
                context.record("B02-B03.actualControls", "PASS native caps/independent modifiers/zeroXP/use=false/enabled=false/consume0/negative/clamps");
            }
            finally
            {
                org.l2jmobius.gameserver.config.PlayerConfig.ENABLE_VITALITY = enabled;
                org.l2jmobius.gameserver.config.PlayerConfig.MAX_BONUS_EXP = capXp; org.l2jmobius.gameserver.config.PlayerConfig.MAX_BONUS_SP = capSp;
                _environment.cleanupLoadedPlayer(player);
            }
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
                final var legacy = PhantomNativeContext.completed(identity, 1, PhantomNativeContext.Eligibility.SUPPORTED, 0, new byte[] { 1 });
                PhantomAssertions.assertEquals(1, legacy.schemaVersion(), "PNC1 header unchanged.");
                PhantomAssertions.assertEquals(legacy, PhantomNativeContext.decode(legacy.encode()), "PNC1 exact roundtrip.");
                PhantomAssertions.assertTrue(legacy.simulationEligible(), "Old supported points1 preserved.");
                final var pending = PhantomNativeContext.pending(identity, 0, PhantomNativeContext.Eligibility.UNKNOWN,
                    new PhantomNativeContext.Capture(16361, PhantomNativeContext.Eligibility.VITALITY_REQUIRES_NATIVE), 4, new byte[] { 2 }, 17, new byte[] { 3 });
                PhantomAssertions.assertEquals(pending, PhantomNativeContext.decode(pending.encode()), "Old pending-before0/after16361 remains readable.");
                PhantomAssertions.assertFalse(pending.simulationEligible(), "Pending cannot be promoted.");
                PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> PhantomNativeContext.decode(java.util.Arrays.copyOf(proof.encode(), proof.encode().length + 1)), "V2 trailing bytes rejected.");
            }
            finally { _environment.cleanupLoadedPlayer(player); }
        });
    }
}
