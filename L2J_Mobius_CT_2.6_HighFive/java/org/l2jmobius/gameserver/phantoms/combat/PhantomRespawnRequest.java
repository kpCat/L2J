/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.combat;

import java.util.Objects;

import org.l2jmobius.gameserver.phantoms.decision.PhantomCancellationToken;

public record PhantomRespawnRequest(long profileId, PhantomCancellationToken planOwnershipToken)
{
	public PhantomRespawnRequest
	{
		if (profileId <= 0)
		{
			throw new IllegalArgumentException("profileId must be positive");
		}
		Objects.requireNonNull(planOwnershipToken, "planOwnershipToken");
	}
}
