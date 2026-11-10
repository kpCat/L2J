/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.combat;

import java.util.Objects;
import java.util.Set;

import org.l2jmobius.gameserver.phantoms.decision.PhantomCancellationToken;

public record PhantomCombatRequest(long profileId, int targetObjectId, PhantomCombatMode mode, boolean useShotsIfAvailable, boolean lootAfterVictory, long timeoutMillis, PhantomCancellationToken planOwnershipToken, LearningScope learningScope, PartyRoles partyRoles, PhantomCancellationToken receiptCancellation)
{
	public enum LearningScope { NONE, SOLO, PARTY }

	public record PartyRoles(Set<Integer> members, int tanks, int healers, int damage)
	{
		public PartyRoles
		{
			members = Set.copyOf(members);
		}
	}

	public PhantomCombatRequest(long profileId, int targetObjectId, PhantomCombatMode mode, boolean useShotsIfAvailable, boolean lootAfterVictory, long timeoutMillis, PhantomCancellationToken planOwnershipToken)
	{
		this(profileId, targetObjectId, mode, useShotsIfAvailable, lootAfterVictory, timeoutMillis, planOwnershipToken, LearningScope.NONE, null, () -> true);
	}

	public PhantomCombatRequest
	{
		if ((profileId <= 0) || (targetObjectId <= 0) || (timeoutMillis < 1000) || (timeoutMillis > PhantomCombatPolicy.MAXIMUM_TIMEOUT_MILLIS))
		{
			throw new IllegalArgumentException("Invalid combat request.");
		}
		Objects.requireNonNull(mode, "mode");
		Objects.requireNonNull(planOwnershipToken, "planOwnershipToken");
		Objects.requireNonNull(learningScope, "learningScope");
		Objects.requireNonNull(receiptCancellation, "receiptCancellation");
	}

	public boolean sameOperation(PhantomCombatRequest other)
	{
		return (other != null) && (profileId == other.profileId) && (targetObjectId == other.targetObjectId) && (mode == other.mode) && (useShotsIfAvailable == other.useShotsIfAvailable) && (lootAfterVictory == other.lootAfterVictory) && (timeoutMillis == other.timeoutMillis) && (planOwnershipToken == other.planOwnershipToken) && (learningScope == other.learningScope) && Objects.equals(partyRoles, other.partyRoles);
	}
}
