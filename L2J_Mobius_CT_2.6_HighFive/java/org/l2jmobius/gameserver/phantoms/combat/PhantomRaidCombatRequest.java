/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.combat;

import java.util.Objects;

import org.l2jmobius.gameserver.phantoms.decision.PhantomCancellationToken;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.ContentKind;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.NpcKind;

/**
 * Exact authority carried by the additive raid branch of the shared combat owner.
 */
public record PhantomRaidCombatRequest(long profileId, int targetObjectId, int targetNpcId, ContentKind contentKind, NpcKind expectedNpcKind, String attemptAuthorityHash, PhantomCombatMode mode, boolean useShotsIfAvailable, boolean lootAfterVictory, int maximumActorLevel, long timeoutMillis, PhantomCancellationToken planOwnershipToken)
{
	public PhantomRaidCombatRequest
	{
		final NpcKind requiredKind = contentKind == ContentKind.RAID ? NpcKind.RAID_BOSS : contentKind == ContentKind.EPIC ? NpcKind.GRAND_BOSS : null;
		if ((profileId <= 0) || (targetObjectId <= 0) || (targetNpcId <= 0) || (requiredKind == null) || (expectedNpcKind != requiredKind) || (attemptAuthorityHash == null) || !attemptAuthorityHash.matches("[0-9A-Fa-f]{64}") || (maximumActorLevel < 0) || (maximumActorLevel > 1000) || (timeoutMillis < 1000) || (timeoutMillis > PhantomCombatPolicy.MAXIMUM_TIMEOUT_MILLIS))
		{
			throw new IllegalArgumentException("Invalid exact raid combat request.");
		}
		Objects.requireNonNull(mode, "mode");
		Objects.requireNonNull(planOwnershipToken, "planOwnershipToken");
		attemptAuthorityHash = attemptAuthorityHash.toUpperCase(java.util.Locale.ROOT);
	}

	PhantomCombatRequest leaseRequest()
	{
		return new PhantomCombatRequest(profileId, targetObjectId, mode, useShotsIfAvailable, lootAfterVictory, timeoutMillis, planOwnershipToken);
	}

	public boolean sameOperation(PhantomRaidCombatRequest other)
	{
		return (other != null) && (profileId == other.profileId) && (targetObjectId == other.targetObjectId) && (targetNpcId == other.targetNpcId) && (contentKind == other.contentKind) && (expectedNpcKind == other.expectedNpcKind) && attemptAuthorityHash.equals(other.attemptAuthorityHash) && (mode == other.mode) && (useShotsIfAvailable == other.useShotsIfAvailable) && (lootAfterVictory == other.lootAfterVictory) && (maximumActorLevel == other.maximumActorLevel) && (timeoutMillis == other.timeoutMillis) && (planOwnershipToken == other.planOwnershipToken);
	}
}
