/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.farming;

import java.util.List;
import java.util.Objects;

/** Narrow typed read-only seam consumed and rendered only by Goal 020. */
@FunctionalInterface
public interface PhantomFarmingConversationFacts
{
	PhantomFarmingConversationFacts NONE = profileId -> List.of();

	enum FactType
	{
		FARMING_CLAIM_STATUS,
		FARMING_CONFLICT,
		FARMING_REMAINING,
		FARMING_ALTERNATIVE,
		FARMING_NEGOTIATION_ACT,
		FARMING_AGREEMENT,
		FARMING_ESCALATION
	}

	record Fact(FactType type, long counterpartProfileId, Long number, String value, String reasonKey)
	{
		public Fact
		{
			Objects.requireNonNull(type);
			value = Objects.requireNonNullElse(value, "");
			reasonKey = Objects.requireNonNullElse(reasonKey, "");
			if (counterpartProfileId < 0)
			{
				throw new IllegalArgumentException("Farming conversation counterpart is invalid.");
			}
		}
	}

	List<Fact> latest(long profileId);
}
