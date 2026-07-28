/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.combat;

import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatLoadout.SelectedSkill;

public record PhantomOwnedAction(long sessionGeneration, int combatTargetObjectId, SelectedSkill selectedSkill, int pickupObjectId)
{
	public PhantomOwnedAction
	{
		if ((sessionGeneration <= 0) || (combatTargetObjectId <= 0) || (pickupObjectId < 0))
		{
			throw new IllegalArgumentException("Invalid owned combat action.");
		}
	}

	public PhantomOwnedAction withSelectedSkill(SelectedSkill skill)
	{
		return new PhantomOwnedAction(sessionGeneration, combatTargetObjectId, skill, pickupObjectId);
	}

	public PhantomOwnedAction withPickupObjectId(int objectId)
	{
		return new PhantomOwnedAction(sessionGeneration, combatTargetObjectId, selectedSkill, objectId);
	}
}
