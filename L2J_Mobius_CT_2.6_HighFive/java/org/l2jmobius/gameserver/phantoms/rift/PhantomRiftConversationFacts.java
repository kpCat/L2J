/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.rift;

import java.util.List;

import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftModel.SemanticFact;

@FunctionalInterface
public interface PhantomRiftConversationFacts
{
	PhantomRiftConversationFacts NONE = profileId -> List.of();

	/**
	 * Returns a newly evaluated typed snapshot. Persisted text is never treated
	 * as current roster evidence.
	 */
	List<SemanticFact> latest(long profileId);
}
