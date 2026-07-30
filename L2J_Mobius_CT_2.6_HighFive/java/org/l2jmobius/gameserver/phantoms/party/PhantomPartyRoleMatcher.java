/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.party;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
		final List<RoleRequirement> orderedRequirements = requirements.stream().sorted(Comparator.comparing(RoleRequirement::vacancyKey)).toList();
		final List<MemberSnapshot> orderedMembers = members.stream().sorted(Comparator.comparing(member -> member.ref().stableKey())).toList();
		final List<List<AssignmentCandidate>> candidates = new ArrayList<>(orderedRequirements.size());
		for (RoleRequirement requirement : orderedRequirements)
		{
			if (!_catalog.contains(requirement.roleKey()))
			{
				candidates.add(List.of());
				continue;
			}
			final RoleDefinition role = _catalog.require(requirement.roleKey());
			final List<AssignmentCandidate> requirementCandidates = new ArrayList<>();
			for (int memberIndex = 0; memberIndex < orderedMembers.size(); memberIndex++)
			{
				final MemberSnapshot member = orderedMembers.get(memberIndex);
				if (member.dead())
				{
					continue;
				}
				final Candidate candidate = bestCapability(objective, role, member);
				if ((candidate != null) && (candidate.score >= requirement.minimumScore()))
				{
					final RoleAssignment assignment = new RoleAssignment(requirement.vacancyKey(), role.roleKey(), member.ref(), candidate.capability.capabilityKey(), candidate.capability.variantKey(), candidate.score, "role.catalog+progression.capability+runtime.context");
					requirementCandidates.add(new AssignmentCandidate(memberIndex, assignment));
				}
			}
			requirementCandidates.sort(Comparator.comparing(value -> value.assignment().member().stableKey() + '|' + value.assignment().capabilityKey() + '|' + value.assignment().variantKey()));
			candidates.add(List.copyOf(requirementCandidates));
		}
		final MatchSolution solution = solve(0, 0, orderedRequirements, candidates, new HashMap<>());
		final List<RoleAssignment> assignments = solution.assignments();
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

	private static MatchSolution solve(int requirementIndex, int memberMask, List<RoleRequirement> requirements, List<List<AssignmentCandidate>> candidates, Map<Integer, MatchSolution> memo)
	{
		if (requirementIndex >= requirements.size())
		{
			return MatchSolution.EMPTY;
		}
		final int memoKey = (requirementIndex << PhantomPartyModel.MAX_ROSTER) | memberMask;
		final MatchSolution cached = memo.get(memoKey);
		if (cached != null)
		{
			return cached;
		}
		final RoleRequirement requirement = requirements.get(requirementIndex);
		MatchSolution best = solve(requirementIndex + 1, memberMask, requirements, candidates, memo);
		for (AssignmentCandidate candidate : candidates.get(requirementIndex))
		{
			final int bit = 1 << candidate.memberIndex();
			if ((memberMask & bit) != 0)
			{
				continue;
			}
			final MatchSolution tail = solve(requirementIndex + 1, memberMask | bit, requirements, candidates, memo);
			final ArrayList<RoleAssignment> assignments = new ArrayList<>(tail.assignments().size() + 1);
			assignments.add(candidate.assignment());
			assignments.addAll(tail.assignments());
			final MatchSolution selected = new MatchSolution(tail.requiredFilled() + (requirement.required() ? 1 : 0), tail.totalScore() + candidate.assignment().score(), tail.optionalFilled() + (requirement.required() ? 0 : 1), List.copyOf(assignments));
			if (selected.betterThan(best))
			{
				best = selected;
			}
		}
		memo.put(memoKey, best);
		return best;
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

	private record AssignmentCandidate(int memberIndex, RoleAssignment assignment)
	{
	}

	private record MatchSolution(int requiredFilled, int totalScore, int optionalFilled, List<RoleAssignment> assignments)
	{
		private static final MatchSolution EMPTY = new MatchSolution(0, 0, 0, List.of());

		private boolean betterThan(MatchSolution other)
		{
			if (requiredFilled != other.requiredFilled)
			{
				return requiredFilled > other.requiredFilled;
			}
			if (totalScore != other.totalScore)
			{
				return totalScore > other.totalScore;
			}
			if (optionalFilled != other.optionalFilled)
			{
				return optionalFilled > other.optionalFilled;
			}
			return canonical().compareTo(other.canonical()) < 0;
		}

		private String canonical()
		{
			return assignments.stream().map(value -> value.vacancyKey() + '|' + value.member().stableKey() + '|' + value.capabilityKey() + '|' + value.variantKey()).reduce("", (left, right) -> left + '\n' + right);
		}
	}
}
