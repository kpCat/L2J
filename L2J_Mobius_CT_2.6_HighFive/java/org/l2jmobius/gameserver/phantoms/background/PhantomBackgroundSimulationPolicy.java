/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.background;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Objects;

/** Immutable native-attested scalar facts; state digest carries identity/XYZ/loadout. */
public record PhantomBackgroundSimulationPolicy(int algorithmVersion, int level, int canonicalPoints,
    float points, boolean ordinaryRewards, boolean farmPosition, boolean travelPosition,
    boolean vitalityEnabled, boolean lucky, int consumeStat, double gainRate, double lostRate,
    double level1Rate, double level2Rate, double level3Rate, double level4Rate,
    double bonusExpPercent, double bonusSpPercent, double maxExpBonus, double maxSpBonus,
    String rulesFingerprint, double runSpeed, long maximumBatchMillis, int unsupportedFacts)
{
    public static final int ALGORITHM_VERSION = 2;
    public enum Operation { FARM, TRAVEL, REST }
    public PhantomBackgroundSimulationPolicy
    {
        Objects.requireNonNull(rulesFingerprint, "rulesFingerprint");
        if ((algorithmVersion < 1 || algorithmVersion > ALGORITHM_VERSION) || level < 1 || level > 255
            || canonicalPoints < 1 || canonicalPoints > 20000 || !Float.isFinite(points)
            || points < 1 || points > 20000 || (int) points != canonicalPoints
            || !rulesFingerprint.matches("[0-9a-f]{64}") || !Double.isFinite(runSpeed) || runSpeed < 0 || runSpeed > 10000
            || maximumBatchMillis < 1 || maximumBatchMillis > PhantomBackgroundModel.MAX_ELAPSED_MILLIS || unsupportedFacts < 0 || unsupportedFacts > 255)
        { throw new IllegalArgumentException("Invalid ordinary policy binding."); }
        final double[] values = { gainRate, lostRate, level1Rate, level2Rate, level3Rate,
            level4Rate, bonusExpPercent, bonusSpPercent, maxExpBonus, maxSpBonus };
        for (double value : values)
        { if (!Double.isFinite(value)) { throw new IllegalArgumentException("Non-finite ordinary policy."); } }
        if (gainRate < 0 || lostRate < 0 || level1Rate < 0 || level2Rate < 0 || level3Rate < 0 || level4Rate < 0)
        { throw new IllegalArgumentException("Negative ordinary rate."); }
    }
    public boolean permits(Operation operation, String currentRules)
    {
        return switch (operation)
        {
            case FARM -> algorithmVersion == ALGORITHM_VERSION && ordinaryRewards && farmPosition && rulesFingerprint.equals(currentRules);
            case TRAVEL -> algorithmVersion == ALGORITHM_VERSION && travelPosition && runSpeed > 0;
            case REST -> false;
        };
    }
    public PhantomBackgroundSimulationPolicy withPoints(float nextPoints, int nextLevel)
    {
        return new PhantomBackgroundSimulationPolicy(algorithmVersion, nextLevel, (int) nextPoints,
            nextPoints, ordinaryRewards && nextLevel == level, farmPosition, travelPosition, vitalityEnabled, lucky,
            consumeStat, gainRate, lostRate, level1Rate, level2Rate, level3Rate, level4Rate,
            bonusExpPercent, bonusSpPercent, maxExpBonus, maxSpBonus, rulesFingerprint, runSpeed, maximumBatchMillis, nextLevel == level ? unsupportedFacts : unsupportedFacts | 128);
    }
    public PhantomBackgroundSimulationPolicy withPosition(boolean farm, boolean travel)
    {
        return new PhantomBackgroundSimulationPolicy(algorithmVersion, level, canonicalPoints,
            points, ordinaryRewards, farm, travel, vitalityEnabled, lucky, consumeStat, gainRate,
            lostRate, level1Rate, level2Rate, level3Rate, level4Rate, bonusExpPercent, bonusSpPercent,
            maxExpBonus, maxSpBonus, rulesFingerprint, runSpeed, maximumBatchMillis, unsupportedFacts);
    }
    void write(DataOutputStream output) throws IOException
    {
        output.writeInt(algorithmVersion); output.writeInt(level); output.writeInt(canonicalPoints);
        output.writeFloat(points); output.writeBoolean(ordinaryRewards); output.writeBoolean(farmPosition);
        output.writeBoolean(travelPosition); output.writeBoolean(vitalityEnabled); output.writeBoolean(lucky);
        output.writeInt(consumeStat); output.writeDouble(gainRate); output.writeDouble(lostRate);
        output.writeDouble(level1Rate); output.writeDouble(level2Rate); output.writeDouble(level3Rate);
        output.writeDouble(level4Rate); output.writeDouble(bonusExpPercent); output.writeDouble(bonusSpPercent);
        output.writeDouble(maxExpBonus); output.writeDouble(maxSpBonus); output.writeUTF(rulesFingerprint);
        if (algorithmVersion >= 2) { output.writeDouble(runSpeed); output.writeLong(maximumBatchMillis); output.writeInt(unsupportedFacts); }
    }
    static PhantomBackgroundSimulationPolicy read(DataInputStream input) throws IOException
    {
        final int version = input.readInt(), level = input.readInt(), canonical = input.readInt();
        final float points = input.readFloat(); final boolean ordinary = input.readBoolean(), farm = input.readBoolean(), travel = input.readBoolean();
        final boolean enabled = input.readBoolean(), lucky = input.readBoolean(); final int consume = input.readInt();
        final double gain = input.readDouble(), lost = input.readDouble(), rate1 = input.readDouble(), rate2 = input.readDouble(), rate3 = input.readDouble(), rate4 = input.readDouble();
        final double xp = input.readDouble(), sp = input.readDouble(), capXp = input.readDouble(), capSp = input.readDouble(); final String fingerprint = input.readUTF();
        final double speed = version >= 2 ? input.readDouble() : 0;
        final long horizon = version >= 2 ? input.readLong() : 60000;
        final int unsupported = version >= 2 ? input.readInt() : ordinary ? 0 : 255;
        return new PhantomBackgroundSimulationPolicy(version, level, canonical, points, ordinary, farm, travel, enabled, lucky, consume,
            gain, lost, rate1, rate2, rate3, rate4, xp, sp, capXp, capSp, fingerprint, speed, horizon, unsupported);
    }
}
