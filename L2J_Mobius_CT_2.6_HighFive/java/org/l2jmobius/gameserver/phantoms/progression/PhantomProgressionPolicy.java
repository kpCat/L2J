/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.progression;

public record PhantomProgressionPolicy(int maximumPageSize, int maximumOwnedEquipmentCandidates, int maximumClasses, int maximumSkillLearns, int maximumSkillFacts, int maximumEquipmentFacts, int maximumSummonFacts, int maximumPetFacts, int maximumCapabilityRules)
{
	public PhantomProgressionPolicy
	{
		if ((maximumPageSize < 1) || (maximumPageSize > 256) || (maximumOwnedEquipmentCandidates < 1) || (maximumOwnedEquipmentCandidates > 64) || (maximumClasses < 1) || (maximumSkillLearns < 1) || (maximumSkillFacts < 1) || (maximumEquipmentFacts < 1) || (maximumSummonFacts < 1) || (maximumPetFacts < 1) || (maximumCapabilityRules < 1))
		{
			throw new IllegalArgumentException("Invalid progression bounds.");
		}
	}

	public static PhantomProgressionPolicy productionDefaults()
	{
		return new PhantomProgressionPolicy(256, 64, 256, 500_000, 100_000, 25_000, 10_000, 1_000, 5_000);
	}
}
