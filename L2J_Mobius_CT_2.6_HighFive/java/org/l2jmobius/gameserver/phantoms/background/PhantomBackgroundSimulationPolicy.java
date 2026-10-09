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
    String rulesFingerprint)
{
    public static final int ALGORITHM_VERSION = 1;
    public enum Operation { FARM, TRAVEL, REST }
    public PhantomBackgroundSimulationPolicy
    {
        Objects.requireNonNull(rulesFingerprint, "rulesFingerprint");
        if (algorithmVersion != ALGORITHM_VERSION || level < 1 || level > 255
            || canonicalPoints < 1 || canonicalPoints > 20000 || !Float.isFinite(points)
            || points < 1 || points > 20000 || (int) points != canonicalPoints
            || !rulesFingerprint.matches("[0-9a-f]{64}"))
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
            case FARM -> ordinaryRewards && farmPosition && rulesFingerprint.equals(currentRules);
            case TRAVEL -> travelPosition;
            case REST -> false;
        };
    }
    public PhantomBackgroundSimulationPolicy withPoints(float nextPoints, int nextLevel)
    {
        return new PhantomBackgroundSimulationPolicy(algorithmVersion, nextLevel, (int) nextPoints,
            nextPoints, ordinaryRewards && nextLevel == level, farmPosition, travelPosition, vitalityEnabled, lucky,
            consumeStat, gainRate, lostRate, level1Rate, level2Rate, level3Rate, level4Rate,
            bonusExpPercent, bonusSpPercent, maxExpBonus, maxSpBonus, rulesFingerprint);
    }
    public PhantomBackgroundSimulationPolicy withPosition(boolean farm, boolean travel)
    {
        return new PhantomBackgroundSimulationPolicy(algorithmVersion, level, canonicalPoints,
            points, ordinaryRewards, farm, travel, vitalityEnabled, lucky, consumeStat, gainRate,
            lostRate, level1Rate, level2Rate, level3Rate, level4Rate, bonusExpPercent, bonusSpPercent,
            maxExpBonus, maxSpBonus, rulesFingerprint);
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
    }
    static PhantomBackgroundSimulationPolicy read(DataInputStream input) throws IOException
    {
        return new PhantomBackgroundSimulationPolicy(input.readInt(), input.readInt(), input.readInt(),
            input.readFloat(), input.readBoolean(), input.readBoolean(), input.readBoolean(),
            input.readBoolean(), input.readBoolean(), input.readInt(), input.readDouble(), input.readDouble(),
            input.readDouble(), input.readDouble(), input.readDouble(), input.readDouble(),
            input.readDouble(), input.readDouble(), input.readDouble(), input.readDouble(), input.readUTF());
    }
}
