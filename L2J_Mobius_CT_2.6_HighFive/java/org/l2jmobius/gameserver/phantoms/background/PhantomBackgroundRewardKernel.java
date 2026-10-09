/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.background;

/** Pure PlayerStat/Attackable scalar arithmetic. No World, config, DB or Player. */
public final class PhantomBackgroundRewardKernel
{
    private PhantomBackgroundRewardKernel() { }
    public record Rewards(long experience, long skillPoints, float points) { }
    public static int vitalityLevel(float points)
    {
        valid(points);
        if (points <= 240) { return 0; }
        if (points <= 2000) { return 1; }
        if (points <= 13000) { return 2; }
        if (points <= 17000) { return 3; }
        return 4;
    }
    private static void valid(float points)
    {
        if (!Float.isFinite(points) || points < 1 || points > 20000)
        { throw new IllegalArgumentException("Invalid ordinary vitality."); }
    }
    public static double multiplier(float points, PhantomBackgroundSimulationPolicy policy, boolean experience)
    {
        final double vitality = !policy.vitalityEnabled() ? 1 : switch (vitalityLevel(points))
        {
            case 1 -> policy.level1Rate(); case 2 -> policy.level2Rate();
            case 3 -> policy.level3Rate(); case 4 -> policy.level4Rate(); default -> 1;
        };
        double result = 1;
        if (vitality > 1) { result += vitality - 1; }
        final double bonus = 1 + (experience ? policy.bonusExpPercent() : policy.bonusSpPercent()) / 100;
        if (bonus > 1) { result += bonus - 1; }
        result = Math.max(result, 1);
        final double cap = experience ? policy.maxExpBonus() : policy.maxSpBonus();
        return cap > 0 ? Math.min(result, cap) : result;
    }
    public static Rewards award(long baseExp, int baseSp, float points, float nativeDelta,
        PhantomBackgroundSimulationPolicy policy, boolean useVitalityRate)
    {
        valid(points);
        final double xp = baseExp * (useVitalityRate ? multiplier(points, policy, true) : 1);
        final double sp = baseSp * (useVitalityRate ? multiplier(points, policy, false) : 1);
        if (baseExp < 0 || baseSp < 0 || !Double.isFinite(xp) || !Double.isFinite(sp)
            || xp >= Long.MAX_VALUE || sp >= Long.MAX_VALUE || !Float.isFinite(nativeDelta))
        { throw new IllegalArgumentException("Invalid ordinary award."); }
        return new Rewards(Math.round(xp), Math.round(sp), baseExp > 0 && useVitalityRate ? update(points, nativeDelta, policy) : points);
    }
    public static float update(float points, float delta, PhantomBackgroundSimulationPolicy policy)
    {
        valid(points);
        if (!Float.isFinite(delta)) { throw new IllegalArgumentException("Invalid target delta."); }
        if (delta == 0 || !policy.vitalityEnabled() || policy.lucky()) { return points; }
        float change = delta;
        if (change < 0)
        {
            if (policy.consumeStat() == 0) { return points; }
            if (policy.consumeStat() < 0) { change = -change; }
        }
        if (change > 0) { change *= policy.gainRate(); } else { change *= policy.lostRate(); }
        final float result = change > 0 ? Math.min(points + change, 20000) : Math.max(points + change, 1);
        return Math.abs(result - points) <= 1e-6 ? points : result;
    }
    public static float targetDelta(long damage, int npcLevel, long expReward, float baseHp, float maxHp)
    {
        if (damage <= 0) { return 0; }
        if (!Float.isFinite(baseHp) || !Float.isFinite(maxHp) || baseHp < 0 || maxHp < 0)
        { throw new IllegalArgumentException("Invalid target HP."); }
        final float divider = npcLevel > 0 && expReward > 0 ? (baseHp * 9 * npcLevel * npcLevel) / (100 * expReward) : 0;
        return divider == 0 ? 0 : -Math.min(damage, maxHp) / divider;
    }
}
