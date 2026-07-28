/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.combat;

import java.util.Objects;

import org.l2jmobius.gameserver.phantoms.decision.PhantomCancellationToken;

public record PhantomCombatRequest(long profileId, int targetObjectId, PhantomCombatMode mode, boolean useShotsIfAvailable, boolean lootAfterVictory, long timeoutMillis, PhantomCancellationToken planOwnershipToken)
{
	public PhantomCombatRequest
	{
		if ((profileId <= 0) || (targetObjectId <= 0) || (timeoutMillis < 1000) || (timeoutMillis > PhantomCombatPolicy.MAXIMUM_TIMEOUT_MILLIS))
		{
			throw new IllegalArgumentException("Invalid combat request.");
		}
		Objects.requireNonNull(mode, "mode");
		Objects.requireNonNull(planOwnershipToken, "planOwnershipToken");
	}

	public boolean sameOperation(PhantomCombatRequest other)
	{
		return (other != null) && (profileId == other.profileId) && (targetObjectId == other.targetObjectId) && (mode == other.mode) && (useShotsIfAvailable == other.useShotsIfAvailable) && (lootAfterVictory == other.lootAfterVictory) && (timeoutMillis == other.timeoutMillis) && (planOwnershipToken == other.planOwnershipToken);
	}
}
