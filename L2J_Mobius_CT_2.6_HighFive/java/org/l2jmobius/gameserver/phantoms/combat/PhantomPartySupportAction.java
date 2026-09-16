/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.combat;

import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatLoadout.SelectedSkill;

/**
 * Exact catalog-backed party support action carried across combat ownership.
 */
public record PhantomPartySupportAction(String capabilityKey, String variantKey, String targetScope, int targetObjectId, SelectedSkill skill, Audience audience, int rebuffRemainingSeconds)
{
	public enum Audience
	{
		PARTY,
		EXACT_REQUESTER
	}

	public PhantomPartySupportAction
	{
		if ((capabilityKey == null) || capabilityKey.isBlank() || (variantKey == null) || variantKey.isBlank() || (targetScope == null) || targetScope.isBlank() || (targetObjectId <= 0) || (skill == null) || (audience == null) || (rebuffRemainingSeconds < 0) || (rebuffRemainingSeconds > 300))
		{
			throw new IllegalArgumentException("Invalid party support action.");
		}
	}

	public PhantomPartySupportAction(String capabilityKey, String variantKey, String targetScope, int targetObjectId, SelectedSkill skill)
	{
		this(capabilityKey, variantKey, targetScope, targetObjectId, skill, Audience.PARTY, 0);
	}
}
