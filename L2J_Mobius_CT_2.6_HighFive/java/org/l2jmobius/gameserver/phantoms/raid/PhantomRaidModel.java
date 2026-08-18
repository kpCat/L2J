/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.raid;

import java.util.List;
import java.util.Objects;

import org.l2jmobius.gameserver.model.groups.CommandChannelInvitationService.InviteResult;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.CapabilityRequirement;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.ContentKind;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.ContentRequirementFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.NpcFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.NpcKind;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyBackend.CurrentForceObservation;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberRef;

public final class PhantomRaidModel
{
	private PhantomRaidModel()
	{
	}

	public enum TargetAvailability
	{
		AVAILABLE,
		ENTRY_GATED,
		UNAVAILABLE,
		UNKNOWN
	}

	public enum ReadinessStatus
	{
		TARGET_UNKNOWN,
		TARGET_UNAVAILABLE,
		GROUP_ABSENT,
		GROUP_INCOMPLETE,
		GROUP_INCAPABLE,
		GROUP_READY
	}

	public enum RecruitmentStatus
	{
		INVALID_INPUT,
		TARGET_UNKNOWN,
		TARGET_UNAVAILABLE,
		GROUP_ABSENT,
		FORCE_UNAVAILABLE,
		FORCE_OVER_BOUND,
		ACTOR_NOT_INVITATION_AUTHORITY,
		GROUP_READY,
		NO_USEFUL_CANDIDATE,
		CANDIDATE_SELECTED
	}

	public enum CandidateStatus
	{
		CURRENT_FORCE_MEMBER,
		EVIDENCE_UNAVAILABLE,
		NOT_EXACT_PARTY_LEADER,
		NOT_STANDALONE_PARTY,
		CONTENT_MEMBER_BOUND_EXCEEDED,
		FORCE_PARTY_BOUND_EXCEEDED,
		FORCE_MEMBER_BOUND_EXCEEDED,
		NOT_USEFUL,
		RECRUITABLE
	}

	public enum RecruitmentAttemptStatus
	{
		NO_INVITE,
		INVITE_DELIVERED,
		INVITE_REJECTED
	}

	public record ContentSnapshot(ContentRequirementFact requirement, NpcFact npc, String recommendationHash)
	{
		public ContentSnapshot
		{
			Objects.requireNonNull(requirement, "requirement");
			Objects.requireNonNull(npc, "npc");
			final NpcKind expectedKind = switch (requirement.contentKind())
			{
				case RAID -> NpcKind.RAID_BOSS;
				case EPIC -> NpcKind.GRAND_BOSS;
				default -> null;
			};
			if ((expectedKind == null) || (requirement.npcId() == null) || (requirement.npcId() != npc.npcId()) || (npc.kind() != expectedKind) || (recommendationHash == null) || !recommendationHash.matches("[0-9a-f]{64}"))
			{
				throw new IllegalArgumentException("Invalid raid content snapshot.");
			}
		}
	}

	public record BossObservation(ContentKind contentKind, int npcId, boolean defined, String rawStatus, boolean livePresent, boolean liveIdentityExact, boolean liveDead, Long respawnTimeMillis, long observedAtMillis, String source)
	{
		public BossObservation
		{
			Objects.requireNonNull(contentKind, "contentKind");
			if (((contentKind != ContentKind.RAID) && (contentKind != ContentKind.EPIC)) || (npcId <= 0) || (rawStatus == null) || rawStatus.isBlank() || (respawnTimeMillis != null && respawnTimeMillis < 0) || (observedAtMillis < 0) || (source == null) || source.isBlank() || (!livePresent && (liveIdentityExact || liveDead)))
			{
				throw new IllegalArgumentException("Invalid boss observation.");
			}
		}

		public TargetAvailability availability()
		{
			if (contentKind == ContentKind.RAID)
			{
				if (defined && "ALIVE".equals(rawStatus) && livePresent && liveIdentityExact && !liveDead)
				{
					return TargetAvailability.AVAILABLE;
				}
				if (defined && "DEAD".equals(rawStatus) && (!livePresent || (liveIdentityExact && liveDead)))
				{
					return TargetAvailability.UNAVAILABLE;
				}
				return TargetAvailability.UNKNOWN;
			}
			if (livePresent && liveIdentityExact && !liveDead)
			{
				return TargetAvailability.AVAILABLE;
			}
			if (!livePresent && (respawnTimeMillis != null) && (respawnTimeMillis > observedAtMillis))
			{
				return TargetAvailability.UNAVAILABLE;
			}
			return TargetAvailability.UNKNOWN;
		}
	}

	public record BossLocation(ContentKind contentKind, int npcId, int x, int y, int z, int instanceId, long observedAtMillis, String source)
	{
		public BossLocation
		{
			Objects.requireNonNull(contentKind, "contentKind");
			if (((contentKind != ContentKind.RAID) && (contentKind != ContentKind.EPIC)) || (npcId <= 0) || (instanceId < 0) || (observedAtMillis < 0) || (source == null) || source.isBlank())
			{
				throw new IllegalArgumentException("Invalid exact boss location.");
			}
		}
	}

	public record CapabilityAssessment(CapabilityRequirement requirement, int satisfyingMembers)
	{
		public CapabilityAssessment
		{
			Objects.requireNonNull(requirement, "requirement");
			if (satisfyingMembers < 0)
			{
				throw new IllegalArgumentException("Invalid capability assessment.");
			}
		}

		public boolean satisfied()
		{
			return satisfyingMembers >= requirement.minimumCount();
		}
	}

	public record CapabilityDeficit(String capabilityKey, int minimumRank, int minimumCount, int satisfyingMembers, int deficit)
	{
		public CapabilityDeficit
		{
			if ((capabilityKey == null) || capabilityKey.isBlank() || (minimumRank < 1) || (minimumCount < 1) || (satisfyingMembers < 0) || (deficit != Math.max(0, minimumCount - satisfyingMembers)))
			{
				throw new IllegalArgumentException("Invalid raid capability deficit.");
			}
		}

		public String evidenceKey()
		{
			return capabilityKey + ':' + minimumRank + ':' + minimumCount + ':' + satisfyingMembers + ':' + deficit;
		}
	}

	public record CapabilityContribution(String capabilityKey, int minimumRank, int candidateSatisfyingMembers, int deficitReduction)
	{
		public CapabilityContribution
		{
			if ((capabilityKey == null) || capabilityKey.isBlank() || (minimumRank < 1) || (candidateSatisfyingMembers < 0) || (deficitReduction < 0) || (deficitReduction > candidateSatisfyingMembers))
			{
				throw new IllegalArgumentException("Invalid raid capability contribution.");
			}
		}

		public String evidenceKey()
		{
			return capabilityKey + ':' + minimumRank + ':' + candidateSatisfyingMembers + ':' + deficitReduction;
		}
	}

	public record CandidateAssessment(MemberRef candidateLeader, CandidateStatus status, int partyMemberCount, List<MemberRef> members, List<CapabilityContribution> capabilityContributions, int totalHardDeficitReduction, int usefulMemberContribution, int excessMembers, String evidenceHash, String reason)
	{
		public CandidateAssessment
		{
			Objects.requireNonNull(candidateLeader, "candidateLeader");
			Objects.requireNonNull(status, "status");
			if ((partyMemberCount < 0) || (partyMemberCount > 9) || (members == null) || (members.size() != partyMemberCount) || (capabilityContributions == null) || (totalHardDeficitReduction < 0) || (usefulMemberContribution < 0) || (usefulMemberContribution > partyMemberCount) || (excessMembers != (partyMemberCount - usefulMemberContribution)) || (evidenceHash == null) || !evidenceHash.matches("[0-9A-Fa-f]{64}") || (reason == null) || reason.isBlank())
			{
				throw new IllegalArgumentException("Invalid raid candidate assessment.");
			}
			members = List.copyOf(members);
			capabilityContributions = List.copyOf(capabilityContributions);
			if ((status == CandidateStatus.RECRUITABLE) != ((totalHardDeficitReduction > 0) || (usefulMemberContribution > 0)))
			{
				throw new IllegalArgumentException("Recruitable raid candidate must reduce a current deficit.");
			}
		}

		public boolean recruitable()
		{
			return status == CandidateStatus.RECRUITABLE;
		}
	}

	public record RecruitmentPlan(String contentId, RecruitmentStatus status, ReadinessStatus readinessStatus, TargetAvailability targetAvailability, String currentForceIdentity, int currentMemberCount, int recommendedMinParty, int recommendedMaxParty, int memberDeficit, List<CapabilityDeficit> hardCapabilityDeficits, List<CandidateAssessment> candidates, MemberRef selectedCandidate, String evidenceHash, String reason)
	{
		public RecruitmentPlan
		{
			if ((contentId == null) || contentId.isBlank() || (status == null) || (readinessStatus == null) || (targetAvailability == null) || (currentForceIdentity == null) || (currentMemberCount < 0) || (recommendedMinParty < 0) || (recommendedMaxParty < recommendedMinParty) || (memberDeficit != Math.max(0, recommendedMinParty - currentMemberCount)) || (hardCapabilityDeficits == null) || (hardCapabilityDeficits.size() > 32) || (candidates == null) || (candidates.size() > 16) || (evidenceHash == null) || !evidenceHash.matches("[0-9A-Fa-f]{64}") || (reason == null) || reason.isBlank())
			{
				throw new IllegalArgumentException("Invalid raid recruitment plan.");
			}
			hardCapabilityDeficits = List.copyOf(hardCapabilityDeficits);
			candidates = List.copyOf(candidates);
			if ((status == RecruitmentStatus.CANDIDATE_SELECTED) != (selectedCandidate != null))
			{
				throw new IllegalArgumentException("Selected raid candidate does not match plan status.");
			}
			if ((selectedCandidate != null) && candidates.stream().noneMatch(candidate -> candidate.recruitable() && candidate.candidateLeader().equals(selectedCandidate)))
			{
				throw new IllegalArgumentException("Selected raid candidate is absent from recruitable evidence.");
			}
		}
	}

	public record RecruitmentAttempt(RecruitmentPlan plan, RecruitmentAttemptStatus status, InviteResult inviteResult)
	{
		public RecruitmentAttempt
		{
			Objects.requireNonNull(plan, "plan");
			Objects.requireNonNull(status, "status");
			if ((status == RecruitmentAttemptStatus.NO_INVITE) != (inviteResult == null))
			{
				throw new IllegalArgumentException("Invalid raid recruitment attempt result.");
			}
			if ((status == RecruitmentAttemptStatus.INVITE_DELIVERED) && !inviteResult.delivered())
			{
				throw new IllegalArgumentException("Delivered raid recruitment attempt requires a delivered CP2 result.");
			}
			if ((status == RecruitmentAttemptStatus.INVITE_REJECTED) && inviteResult.delivered())
			{
				throw new IllegalArgumentException("Rejected raid recruitment attempt cannot carry a delivered CP2 result.");
			}
		}
	}

	public record RaidReadiness(String contentId, ContentSnapshot content, BossObservation target, TargetAvailability targetAvailability, CurrentForceObservation force, List<CapabilityAssessment> capabilities, ReadinessStatus status, String reason)
	{
		public RaidReadiness
		{
			if ((contentId == null) || contentId.isBlank() || (targetAvailability == null) || (force == null) || (capabilities == null) || (status == null) || (reason == null) || reason.isBlank())
			{
				throw new IllegalArgumentException("Invalid raid readiness result.");
			}
			capabilities = List.copyOf(capabilities);
			if ((status == ReadinessStatus.GROUP_READY) && (targetAvailability != TargetAvailability.AVAILABLE) && (targetAvailability != TargetAvailability.ENTRY_GATED))
			{
				throw new IllegalArgumentException("A ready group requires an available target or exact entry workflow.");
			}
		}

		public boolean groupReady()
		{
			return status == ReadinessStatus.GROUP_READY;
		}
	}
}
