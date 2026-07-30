/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.combat;

import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatLoadout.SelectedSkill;

/**
 * Exact catalog-backed party support action carried across combat ownership.
 */
public record PhantomPartySupportAction(String capabilityKey, String variantKey, String targetScope, int targetObjectId, SelectedSkill skill)
{
	public PhantomPartySupportAction
	{
		if ((capabilityKey == null) || capabilityKey.isBlank() || (variantKey == null) || variantKey.isBlank() || (targetScope == null) || targetScope.isBlank() || (targetObjectId <= 0) || (skill == null))
		{
			throw new IllegalArgumentException("Invalid party support action.");
		}
	}
}
