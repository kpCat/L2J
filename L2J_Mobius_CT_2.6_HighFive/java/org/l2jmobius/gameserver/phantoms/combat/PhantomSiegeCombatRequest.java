/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.combat;

import java.util.Locale;
import java.util.Objects;

import org.l2jmobius.gameserver.phantoms.decision.PhantomCancellationToken;
import org.l2jmobius.gameserver.phantoms.siege.PhantomSiegeModel.TargetKind;

/** Exact native-siege authority carried by the additive shared-combat branch. */
public record PhantomSiegeCombatRequest(long profileId, int targetObjectId, int castleId, TargetKind targetKind, int targetNativeId, String authorityHash, PhantomCombatMode mode, boolean useShotsIfAvailable, long timeoutMillis, PhantomCancellationToken planOwnershipToken)
{
	public PhantomSiegeCombatRequest
	{
		if ((profileId <= 0) || (targetObjectId <= 0) || (castleId != 3) || (targetNativeId <= 0) || (authorityHash == null) || !authorityHash.matches("[0-9A-Fa-f]{64}") || (timeoutMillis < 1000) || (timeoutMillis > PhantomCombatPolicy.MAXIMUM_TIMEOUT_MILLIS))
		{
			throw new IllegalArgumentException("Invalid exact siege combat request.");
		}
		Objects.requireNonNull(targetKind, "targetKind");
		Objects.requireNonNull(mode, "mode");
		Objects.requireNonNull(planOwnershipToken, "planOwnershipToken");
		authorityHash = authorityHash.toUpperCase(Locale.ROOT);
	}

	PhantomCombatRequest leaseRequest()
	{
		return new PhantomCombatRequest(profileId, targetObjectId, mode, useShotsIfAvailable, false, timeoutMillis, planOwnershipToken);
	}

	public boolean sameOperation(PhantomSiegeCombatRequest other)
	{
		return (other != null) && (profileId == other.profileId) && (targetObjectId == other.targetObjectId) && (castleId == other.castleId) && (targetKind == other.targetKind) && (targetNativeId == other.targetNativeId) && authorityHash.equals(other.authorityHash) && (mode == other.mode) && (useShotsIfAvailable == other.useShotsIfAvailable) && (timeoutMillis == other.timeoutMillis) && (planOwnershipToken == other.planOwnershipToken);
	}
}
