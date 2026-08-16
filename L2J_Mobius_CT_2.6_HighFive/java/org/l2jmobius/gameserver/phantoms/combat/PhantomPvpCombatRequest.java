/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.combat;

import java.util.Objects;

import org.l2jmobius.gameserver.phantoms.decision.PhantomCancellationToken;
import org.l2jmobius.gameserver.phantoms.pvp.PhantomPvpModel.Source;

public record PhantomPvpCombatRequest(long profileId, int targetObjectId, Source source, String authorityHash, PhantomCombatMode mode, boolean forceUse, boolean useShotsIfAvailable, int cpPotionThresholdPercent, long timeoutMillis, PhantomCancellationToken planOwnershipToken)
{
	public PhantomPvpCombatRequest
	{
		if ((profileId <= 0) || (targetObjectId <= 0) || (cpPotionThresholdPercent < 1) || (cpPotionThresholdPercent > 80) || (timeoutMillis < 1000) || (timeoutMillis > PhantomCombatPolicy.MAXIMUM_TIMEOUT_MILLIS) || (authorityHash == null) || !authorityHash.matches("[0-9A-F]{64}") || (forceUse && ((source == null) || !source.proactive())))
		{
			throw new IllegalArgumentException("Invalid PvP combat request.");
		}
		Objects.requireNonNull(source, "source");
		Objects.requireNonNull(mode, "mode");
		Objects.requireNonNull(planOwnershipToken, "planOwnershipToken");
	}

	PhantomCombatRequest leaseRequest()
	{
		return new PhantomCombatRequest(profileId, targetObjectId, mode, useShotsIfAvailable, false, timeoutMillis, planOwnershipToken);
	}

	public boolean sameOperation(PhantomPvpCombatRequest other)
	{
		return (other != null) && (profileId == other.profileId) && (targetObjectId == other.targetObjectId) && (source == other.source) && authorityHash.equals(other.authorityHash) && (mode == other.mode) && (forceUse == other.forceUse) && (useShotsIfAvailable == other.useShotsIfAvailable) && (cpPotionThresholdPercent == other.cpPotionThresholdPercent) && (timeoutMillis == other.timeoutMillis) && (planOwnershipToken == other.planOwnershipToken);
	}
}
