/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.raid;

import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.ContentKind;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.NpcKind;

/**
 * Read-only exact live/dead raid object evidence.
 */
public record PhantomRaidTargetEvidence(ContentKind contentKind, NpcKind npcKind, int objectId, int npcId, int instanceId, boolean dead)
{
	public PhantomRaidTargetEvidence
	{
		final NpcKind requiredKind = contentKind == ContentKind.RAID ? NpcKind.RAID_BOSS : contentKind == ContentKind.EPIC ? NpcKind.GRAND_BOSS : null;
		if ((requiredKind == null) || (npcKind != requiredKind) || (objectId <= 0) || (npcId <= 0) || (instanceId < 0))
		{
			throw new IllegalArgumentException("Invalid exact raid target evidence.");
		}
	}

	public boolean sameIdentity(PhantomRaidTargetEvidence other)
	{
		return (other != null) && (contentKind == other.contentKind) && (npcKind == other.npcKind) && (objectId == other.objectId) && (npcId == other.npcId) && (instanceId == other.instanceId);
	}
}