/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.combat;

public record PhantomCombatSessionSnapshot(long profileId, long generation, int targetObjectId, PhantomCombatMode mode, PhantomCombatPhase phase, PhantomCombatResult result, long startedLogicalNanos, long lastPulseLogicalNanos, int selectedSkills, int threatEntries, int rememberedLootIds, int lootPickupsIssued)
{
}
