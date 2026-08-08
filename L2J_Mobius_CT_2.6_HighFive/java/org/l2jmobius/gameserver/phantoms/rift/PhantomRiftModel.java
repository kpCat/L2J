/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.rift;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.l2jmobius.gameserver.model.groups.PartyDistributionType;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberRef;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.RoleMatchResult;

public final class PhantomRiftModel
{
	public static final String COMPONENT_TYPE = "rift.preparation";
	public static final int SCHEMA_VERSION = 1;
	public static final int MAX_REFUSALS = 32;
	public static final int MAX_CANDIDATES = 32;
	public static final int MAX_PAYLOAD_BYTES = 4096;

	public enum Status
	{
		NEEDS_PARTY,
		NEEDS_ROLE,
		NEEDS_MEMBER_READY,
		NEEDS_SUPPLIES,
		NEEDS_TRAVEL,
		INVITE_PENDING,
		READY_TO_ENTER,
		BLOCKED,
		STALE
	}

	public enum Stage
	{
		DISCOVER_CONTENT,
		SNAPSHOT_ROSTER,
		EVALUATE_READINESS,
		SELECT_CANDIDATE,
		REQUEST_INVITE,
		OBSERVE_INVITE,
		REQUEST_PARTY_ROUTE,
		OBSERVE_ROUTE,
		DECLARE_READY
	}

	public enum ReadinessDimension
	{
		LEVEL,
		ALIVE,
		VITALS,
		INSTANCE,
		EQUIPMENT,
		CAPABILITIES,
		SUPPLIES,
		TRAVEL
	}

	public enum SemanticFactType
	{
		RIFT_PREP_STATUS,
		RIFT_MISSING_ROLE,
		RIFT_MEMBER_NOT_READY,
		RIFT_INVITE_REQUEST,
		RIFT_INVITE_REFUSED,
		RIFT_PARTY_FULL,
		RIFT_READY
	}

	public record CanonicalRoster(MemberRef leader, List<MemberRef> members, PartyDistributionType distribution, boolean liveParty, boolean fullParty, String evidenceHash)
	{
		public CanonicalRoster
		{
			Objects.requireNonNull(leader);
			Objects.requireNonNull(distribution);
			members = List.copyOf(members);
			if (members.isEmpty() || (members.size() > 9) || !members.contains(leader) || (members.stream().map(MemberRef::characterObjectId).distinct().count() != members.size()))
			{
				throw new IllegalArgumentException("Invalid canonical Rift roster.");
			}
			if (fullParty != (members.size() == 9))
			{
				throw new IllegalArgumentException("Rift full-party evidence is inconsistent.");
			}
			evidenceHash = requireHash(evidenceHash, "Rift roster evidence");
		}
	}

	public record DimensionEvidence(ReadinessDimension dimension, boolean ready, String reasonKey, String evidence)
	{
		public DimensionEvidence
		{
			Objects.requireNonNull(dimension);
			reasonKey = requireKey(reasonKey, "Rift readiness reason");
			evidence = bounded(evidence, 512, "Rift readiness evidence");
		}
	}

	public record SupplyDeficit(String familyKey, int itemId, long requiredCount, long actualCount)
	{
		public SupplyDeficit
		{
			familyKey = requireKey(familyKey, "Rift supply family");
			if ((itemId < 0) || (requiredCount < 1) || (actualCount < 0) || (actualCount >= requiredCount))
			{
				throw new IllegalArgumentException("Invalid Rift supply deficit.");
			}
		}
	}

	public record MemberReadiness(MemberRef member, List<String> assignedVacancyKeys, List<DimensionEvidence> dimensions, List<SupplyDeficit> deficits, boolean ready, String evidenceHash)
	{
		public MemberReadiness
		{
			Objects.requireNonNull(member);
			assignedVacancyKeys = assignedVacancyKeys.stream().map(value -> requireKey(value, "Assigned Rift vacancy")).sorted().toList();
			dimensions = dimensions.stream().sorted(Comparator.comparing(DimensionEvidence::dimension)).toList();
			deficits = List.copyOf(deficits);
			if (dimensions.size() != ReadinessDimension.values().length)
			{
				throw new IllegalArgumentException("Every Rift readiness dimension must be present.");
			}
			if (ready != dimensions.stream().allMatch(DimensionEvidence::ready))
			{
				throw new IllegalArgumentException("Rift member readiness does not match its dimensions.");
			}
			evidenceHash = requireHash(evidenceHash, "Rift member readiness evidence");
		}
	}

	public record PartyReadiness(int tierType, CanonicalRoster roster, boolean minimumPartySizeSatisfied, RoleMatchResult roles, List<MemberReadiness> members, List<String> requiredVacancies, List<String> optionalVacancies, boolean entryResourcesReady, boolean travelReady, Status status, List<String> reasonKeys, String catalogHash, String policyHash, String configHash, String evidenceHash)
	{
		public PartyReadiness
		{
			if ((tierType < 1) || (tierType > 6))
			{
				throw new IllegalArgumentException("Invalid Rift tier type.");
			}
			Objects.requireNonNull(roster);
			Objects.requireNonNull(roles);
			members = List.copyOf(members);
			requiredVacancies = requiredVacancies.stream().map(value -> requireKey(value, "Required Rift vacancy")).sorted().toList();
			optionalVacancies = optionalVacancies.stream().map(value -> requireKey(value, "Optional Rift vacancy")).sorted().toList();
			Objects.requireNonNull(status);
			reasonKeys = reasonKeys.stream().map(value -> requireKey(value, "Rift party reason")).distinct().sorted().toList();
			catalogHash = requireHash(catalogHash, "Rift catalog hash");
			policyHash = requireHash(policyHash, "Rift policy hash");
			configHash = requireHash(configHash, "Rift config hash");
			evidenceHash = requireHash(evidenceHash, "Rift readiness evidence");
		}
	}

	public record CandidateScore(MemberRef member, String vacancyKey, int roleScore, int readinessScore, long distanceSquared, boolean ordinaryRealPlayer, String evidenceHash)
	{
		public CandidateScore
		{
			Objects.requireNonNull(member);
			vacancyKey = requireKey(vacancyKey, "Rift candidate vacancy");
			if ((roleScore < 1) || (roleScore > 10000) || (readinessScore < 0) || (readinessScore > 10000) || (distanceSquared < 0))
			{
				throw new IllegalArgumentException("Invalid Rift candidate score.");
			}
			evidenceHash = requireHash(evidenceHash, "Rift candidate evidence");
		}
	}

	public record RecruitmentDecision(String vacancyKey, List<CandidateScore> evaluated, CandidateScore selected, String reasonKey)
	{
		public RecruitmentDecision
		{
			vacancyKey = vacancyKey == null || vacancyKey.isEmpty() ? "" : requireKey(vacancyKey, "Rift recruitment vacancy");
			evaluated = List.copyOf(evaluated);
			if (evaluated.size() > MAX_CANDIDATES)
			{
				throw new IllegalArgumentException("Rift candidate bound exceeded.");
			}
			if ((selected != null) && !evaluated.contains(selected))
			{
				throw new IllegalArgumentException("Selected Rift candidate was not evaluated.");
			}
			reasonKey = requireKey(reasonKey, "Rift recruitment reason");
		}
	}

	public record Refusal(MemberRef candidate, String vacancyKey, long refusedEpochMillis, long cooldownUntilEpochMillis, String reasonKey)
	{
		public Refusal
		{
			Objects.requireNonNull(candidate);
			vacancyKey = requireKey(vacancyKey, "Rift refusal vacancy");
			reasonKey = requireKey(reasonKey, "Rift refusal reason");
			if ((refusedEpochMillis < 0) || (cooldownUntilEpochMillis <= refusedEpochMillis))
			{
				throw new IllegalArgumentException("Invalid Rift refusal interval.");
			}
		}
	}

	public record Preparation(long leaderProfileId, long goalId, long goalRevision, int tierType, Stage stage, Status status, String rosterHash, String catalogHash, String policyHash, String configHash, String roleHash, String missingVacancyKey, MemberRef pendingCandidate, long pendingInvitationSequence, int totalAttempts, int seatAttempts, List<Refusal> refusals, String routeHash, long updatedEpochMillis)
	{
		public Preparation
		{
			if ((leaderProfileId <= 0) || (goalId <= 0) || (goalRevision < 0) || (tierType < 1) || (tierType > 6) || (pendingInvitationSequence < 0) || (totalAttempts < 0) || (totalAttempts > 32) || (seatAttempts < 0) || (seatAttempts > 8) || (updatedEpochMillis < 0))
			{
				throw new IllegalArgumentException("Invalid Rift preparation identity or bounds.");
			}
			Objects.requireNonNull(stage);
			Objects.requireNonNull(status);
			rosterHash = requireHash(rosterHash, "Rift preparation roster hash");
			catalogHash = requireHash(catalogHash, "Rift preparation catalog hash");
			policyHash = requireHash(policyHash, "Rift preparation policy hash");
			configHash = requireHash(configHash, "Rift preparation config hash");
			roleHash = requireHash(roleHash, "Rift preparation role hash");
			missingVacancyKey = missingVacancyKey == null || missingVacancyKey.isEmpty() ? "" : requireKey(missingVacancyKey, "Rift preparation vacancy");
			refusals = List.copyOf(refusals);
			if (refusals.size() > MAX_REFUSALS)
			{
				throw new IllegalArgumentException("Rift refusal history bound exceeded.");
			}
			routeHash = requireHash(routeHash, "Rift preparation route hash");
		}
	}

	public record SemanticFact(SemanticFactType type, Map<String, String> slots, String rosterEvidenceHash)
	{
		public SemanticFact
		{
			Objects.requireNonNull(type);
			slots = Map.copyOf(slots);
			if (slots.size() > 12)
			{
				throw new IllegalArgumentException("Rift semantic fact has too many slots.");
			}
			rosterEvidenceHash = requireHash(rosterEvidenceHash, "Rift semantic roster evidence");
		}
	}

	public static String requireKey(String value, String label)
	{
		return org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.requireKey(value, label);
	}

	public static String requireHash(String value, String label)
	{
		return org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.requireHash(value, label);
	}

	private static String bounded(String value, int maximum, String label)
	{
		Objects.requireNonNull(value, label);
		if ((value.length() > maximum) || !value.equals(value.trim()))
		{
			throw new IllegalArgumentException(label + " is outside bounds.");
		}
		return value;
	}

	private PhantomRiftModel()
	{
	}
}
