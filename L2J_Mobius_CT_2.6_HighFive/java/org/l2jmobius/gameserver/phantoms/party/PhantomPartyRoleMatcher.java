/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.party;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberCapability;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberSnapshot;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.ObjectiveMode;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.RoleAssignment;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.RoleDefinition;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.RoleMatchResult;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.RoleRequirement;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.Vacancy;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.VacancyStatus;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel;

/**
 * Deterministic bounded role assignment driven only by capability evidence.
 */
public final class PhantomPartyRoleMatcher
{
	private final PhantomPartyRoleCatalog _catalog;

	public PhantomPartyRoleMatcher(PhantomPartyRoleCatalog catalog)
	{
		_catalog = catalog;
	}

	public RoleMatchResult match(ObjectiveMode objective, List<RoleRequirement> requirements, List<MemberSnapshot> members)
	{
		if ((requirements == null) || (requirements.size() > PhantomPartyModel.MAX_REQUIREMENTS) || (members == null) || (members.size() > PhantomPartyModel.MAX_ROSTER))
		{
			throw new IllegalArgumentException("Party role matching input is outside bounds.");
		}
		final List<RoleRequirement> orderedRequirements = requirements.stream().sorted(Comparator.comparing(RoleRequirement::required).reversed().thenComparing(RoleRequirement::vacancyKey)).toList();
		final List<MemberSnapshot> orderedMembers = members.stream().sorted(Comparator.comparing(member -> member.ref().stableKey())).toList();
		final Set<String> usedMembers = new HashSet<>();
		final List<RoleAssignment> assignments = new ArrayList<>();
		for (RoleRequirement requirement : orderedRequirements)
		{
			if (!_catalog.contains(requirement.roleKey()))
			{
				continue;
			}
			final RoleDefinition role = _catalog.require(requirement.roleKey());
			MemberSnapshot selectedMember = null;
			Candidate selectedCapability = null;
			for (MemberSnapshot member : orderedMembers)
			{
				if (usedMembers.contains(member.ref().stableKey()) || member.dead())
				{
					continue;
				}
				final Candidate candidate = bestCapability(objective, role, member);
				if ((candidate == null) || (candidate.score < requirement.minimumScore()))
				{
					continue;
				}
				if ((selectedCapability == null) || (candidate.score > selectedCapability.score) || ((candidate.score == selectedCapability.score) && (member.ref().stableKey().compareTo(selectedMember.ref().stableKey()) < 0)))
				{
					selectedMember = member;
					selectedCapability = candidate;
				}
			}
			if (selectedMember != null)
			{
				usedMembers.add(selectedMember.ref().stableKey());
				assignments.add(new RoleAssignment(requirement.vacancyKey(), role.roleKey(), selectedMember.ref(), selectedCapability.capability.capabilityKey(), selectedCapability.capability.variantKey(), selectedCapability.score, "role.catalog+progression.capability+runtime.context"));
			}
		}
		final List<Vacancy> vacancies = new ArrayList<>(orderedRequirements.size());
		for (RoleRequirement requirement : orderedRequirements)
		{
			final RoleAssignment assignment = assignments.stream().filter(value -> value.vacancyKey().equals(requirement.vacancyKey())).findFirst().orElse(null);
			final VacancyStatus status;
			final String provenance;
			if (!_catalog.contains(requirement.roleKey()))
			{
				status = VacancyStatus.UNSUPPORTED;
				provenance = "catalog.role.unsupported";
			}
			else if (assignment != null)
			{
				status = VacancyStatus.FILLED;
				provenance = "capability.contextual.assignment";
			}
			else if (requirement.required())
			{
				status = VacancyStatus.MISSING;
				provenance = "capability.required.missing";
			}
			else
			{
				status = VacancyStatus.OPTIONAL;
				provenance = "capability.optional.open";
			}
			vacancies.add(new Vacancy(requirement.vacancyKey(), requirement.roleKey(), requirement.required(), status, assignment, provenance));
		}
		final StringBuilder evidence = new StringBuilder(_catalog.hash()).append('|').append(objective);
		assignments.forEach(value -> evidence.append('|').append(value.vacancyKey()).append(':').append(value.member().stableKey()).append(':').append(value.capabilityKey()).append(':').append(value.variantKey()).append(':').append(value.score()));
		vacancies.forEach(value -> evidence.append('|').append(value.vacancyKey()).append(':').append(value.status()));
		return new RoleMatchResult(assignments, vacancies, _catalog.hash(), PhantomPartyModel.sha256(evidence.toString()));
	}

	private static Candidate bestCapability(ObjectiveMode objective, RoleDefinition role, MemberSnapshot member)
	{
		Candidate best = null;
		for (MemberCapability capability : member.capabilities())
		{
			final Integer weight = role.capabilityWeights().get(capability.capabilityKey());
			if ((weight == null) || !capability.intrinsic() || !capability.learned())
			{
				continue;
			}
			final int readiness = capability.readyNow() ? 500 : -500;
			final int resources = (member.hpPercent() * 2) + member.mpPercent();
			final int objectiveWeight = role.objectiveWeights().getOrDefault(objective, 0);
			final int score = Math.max(1, Math.min(10000, (capability.rank() * weight) + capability.contextualScore() + readiness + resources + objectiveWeight));
			final Candidate candidate = new Candidate(capability, score);
			if ((best == null) || (candidate.score > best.score) || ((candidate.score == best.score) && capability.identity().compareTo(best.capability.identity()) < 0))
			{
				best = candidate;
			}
		}
		return best;
	}

	private record Candidate(MemberCapability capability, int score)
	{
	}
}
