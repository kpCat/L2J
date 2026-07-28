/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.progression;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;

import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.ActorProgressionSnapshot;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.CapabilityRule;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.ClassFact;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.EquipmentFact;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.EquipItemRequest;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.LearnSkillRequest;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.OperationResult;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.OwnedEquipmentFact;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.OwnedEquipmentFilter;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.Page;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.PageRequest;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.PetFact;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.SkillFact;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.SkillLearnFact;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.SkillReadinessProbe;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.SkillRef;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.SubclassEligibility;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.SummonActorFact;

/**
 * Copying/source and canonical actor-operation boundary. Implementations keep
 * all mutable server objects behind an opaque lease.
 */
public interface PhantomProgressionBackend
{
	BackendData load(PhantomProgressionPolicy policy);

	Optional<ActorLease> tryAcquireActor(long profileId);

	interface ActorLease extends AutoCloseable
	{
		ActorProgressionSnapshot snapshot(String catalogHash, Set<Integer> referencedResourceItemIds, Set<Integer> certificationSkillIds);

		Page<OwnedEquipmentFact> ownedEquipment(OwnedEquipmentFilter filter, PageRequest page);

		SkillReadinessProbe canonicalSkillReadiness(SkillRef skill, Integer targetObjectId);

		List<SubclassEligibility> subclassEligibility(List<ClassFact> classes);

		OperationResult learnClassSkill(LearnSkillRequest request, BooleanSupplier ownershipCurrent);

		OperationResult equipOwnedItem(EquipItemRequest request, BooleanSupplier ownershipCurrent);

		@Override
		void close();
	}

	record BackendData(List<ClassFact> classes, List<SkillLearnFact> skillLearns, List<SkillFact> skills, List<EquipmentFact> equipment, List<SummonActorFact> summons, List<PetFact> pets, List<CapabilityRule> capabilityRules, Set<Integer> knownItemIds)
	{
		public BackendData
		{
			classes = List.copyOf(classes);
			skillLearns = List.copyOf(skillLearns);
			skills = List.copyOf(skills);
			equipment = List.copyOf(equipment);
			summons = List.copyOf(summons);
			pets = List.copyOf(pets);
			capabilityRules = List.copyOf(capabilityRules);
			knownItemIds = Set.copyOf(knownItemIds);
		}
	}
}
