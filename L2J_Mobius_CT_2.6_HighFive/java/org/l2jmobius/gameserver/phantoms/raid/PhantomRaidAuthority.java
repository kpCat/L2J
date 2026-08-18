/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.raid;

import java.util.Optional;

import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.ContentKind;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidModel.BossLocation;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidModel.BossObservation;

/**
 * Read-only exact boss-state authority. Implementations must not mutate or schedule boss state.
 */
public interface PhantomRaidAuthority
{
	BossObservation observe(ContentKind contentKind, int npcId);

	default Optional<PhantomRaidTargetEvidence> observeTarget(ContentKind contentKind, int npcId)
	{
		return Optional.empty();
	}

	default boolean confirmsDeath(PhantomRaidTargetEvidence expected)
	{
		return observeTarget(expected.contentKind(), expected.npcId()).filter(current -> current.sameIdentity(expected) && current.dead()).isPresent();
	}

	default Optional<BossLocation> observeLocation(ContentKind contentKind, int npcId)
	{
		return Optional.empty();
	}
}
