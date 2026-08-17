/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.raid;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.l2jmobius.gameserver.model.groups.CommandChannelInvitationService.InviteResult;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.CapabilityRequirement;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyBackend;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyBackend.CurrentForceObservation;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyBackend.CurrentForceSnapshot;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyBackend.CurrentForceStatus;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberRef;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberSnapshot;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidModel.CandidateAssessment;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidModel.CandidateStatus;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidModel.CapabilityContribution;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidModel.CapabilityDeficit;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidModel.RaidReadiness;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidModel.ReadinessStatus;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidModel.RecruitmentAttempt;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidModel.RecruitmentAttemptStatus;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidModel.RecruitmentPlan;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidModel.RecruitmentStatus;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidModel.TargetAvailability;

/**
 * Stateless bounded composition planning and one-shot outbound MPCC recruitment.
 */
public final class PhantomRaidRecruitmentService
{
	public static final int MAX_CANDIDATE_PARTY_LEADERS = 16;

	private static final Comparator<CandidateAssessment> SELECTION_ORDER = Comparator.comparingInt(CandidateAssessment::totalHardDeficitReduction).reversed()
		.thenComparing(Comparator.comparingInt(CandidateAssessment::usefulMemberContribution).reversed())
		.thenComparingInt(CandidateAssessment::excessMembers)
		.thenComparing(candidate -> candidate.candidateLeader().stableKey());

	private final PhantomRaidReadinessService _readiness;
	private final PhantomPartyBackend _party;

	public PhantomRaidRecruitmentService(PhantomRaidReadinessService readiness, PhantomPartyBackend party)
	{
		_readiness = Objects.requireNonNull(readiness);
		_party = Objects.requireNonNull(party);
	}

	public RecruitmentPlan plan(MemberRef actor, String contentId, List<MemberRef> candidatePartyLeaders)
	{
		Objects.requireNonNull(actor, "actor");
		final RaidReadiness readiness = _readiness.assess(actor, contentId);
		final InputValidation input = validateInput(candidatePartyLeaders);
		final CurrentForceObservation force = readiness.force();
		final CurrentForceSnapshot snapshot = force.snapshot();
		final int currentMembers = snapshot == null ? 0 : snapshot.totalMemberCount();
		final int minimum = readiness.content() == null ? 0 : readiness.content().requirement().recommendedMinParty();
		final int maximum = readiness.content() == null ? 0 : readiness.content().requirement().recommendedMaxParty();
		final String forceIdentity = snapshot == null ? "" : forceIdentity(snapshot);
		final List<CapabilityRequirement> hardRequirements = readiness.content() == null ? List.of() : hardRequirements(readiness.content().requirement().requirements());
		final List<CapabilityDeficit> deficits = snapshot == null ? List.of() : hardDeficits(snapshot.members(), hardRequirements);

		if (!input.valid())
		{
			return plan(readiness, RecruitmentStatus.INVALID_INPUT, forceIdentity, currentMembers, minimum, maximum, deficits, List.of(), null, input.evidence(), input.reason());
		}
		if (readiness.targetAvailability() == TargetAvailability.UNKNOWN)
		{
			return plan(readiness, RecruitmentStatus.TARGET_UNKNOWN, forceIdentity, currentMembers, minimum, maximum, deficits, List.of(), null, input.evidence(), "raid.recruitment.target_unknown");
		}
		if (readiness.targetAvailability() == TargetAvailability.UNAVAILABLE)
		{
			return plan(readiness, RecruitmentStatus.TARGET_UNAVAILABLE, forceIdentity, currentMembers, minimum, maximum, deficits, List.of(), null, input.evidence(), "raid.recruitment.target_unavailable");
		}
		if (readiness.status() == ReadinessStatus.GROUP_ABSENT)
		{
			return plan(readiness, RecruitmentStatus.GROUP_ABSENT, forceIdentity, currentMembers, minimum, maximum, deficits, List.of(), null, input.evidence(), "raid.recruitment.group_absent");
		}
		if ((force.status() != CurrentForceStatus.AVAILABLE) || (snapshot == null))
		{
			final RecruitmentStatus status = force.status() == CurrentForceStatus.BOUNDS_EXCEEDED ? RecruitmentStatus.FORCE_OVER_BOUND : RecruitmentStatus.FORCE_UNAVAILABLE;
			return plan(readiness, status, forceIdentity, currentMembers, minimum, maximum, deficits, List.of(), null, input.evidence(), status == RecruitmentStatus.FORCE_OVER_BOUND ? "raid.recruitment.force_over_bound" : "raid.recruitment.force_unavailable");
		}
		if ((currentMembers > maximum) || (currentMembers > PhantomPartyBackend.MAX_FORCE_MEMBERS))
		{
			return plan(readiness, RecruitmentStatus.FORCE_OVER_BOUND, forceIdentity, currentMembers, minimum, maximum, deficits, List.of(), null, input.evidence(), "raid.recruitment.force_over_bound");
		}
		final boolean invitationAuthority = snapshot.commandChannelPresent() ? actor.equals(snapshot.commandChannelLeader()) : actor.equals(snapshot.partyLeader());
		if (!invitationAuthority)
		{
			return plan(readiness, RecruitmentStatus.ACTOR_NOT_INVITATION_AUTHORITY, forceIdentity, currentMembers, minimum, maximum, deficits, List.of(), null, input.evidence(), "raid.recruitment.actor_not_invitation_authority");
		}
		if (readiness.groupReady())
		{
			return plan(readiness, RecruitmentStatus.GROUP_READY, forceIdentity, currentMembers, minimum, maximum, deficits, List.of(), null, input.evidence(), "raid.recruitment.group_ready");
		}

		final Set<MemberRef> currentForceMembers = snapshot.members().stream().map(MemberSnapshot::ref).collect(java.util.stream.Collectors.toUnmodifiableSet());
		final List<CandidateAssessment> candidates = new ArrayList<>(input.candidates().size());
		for (MemberRef candidate : input.candidates())
		{
			candidates.add(assessCandidate(candidate, snapshot, currentForceMembers, maximum, Math.max(0, minimum - currentMembers), hardRequirements, deficits));
		}
		final MemberRef selected = candidates.stream().filter(CandidateAssessment::recruitable).min(SELECTION_ORDER).map(CandidateAssessment::candidateLeader).orElse(null);
		final RecruitmentStatus status = selected == null ? RecruitmentStatus.NO_USEFUL_CANDIDATE : RecruitmentStatus.CANDIDATE_SELECTED;
		return plan(readiness, status, forceIdentity, currentMembers, minimum, maximum, deficits, candidates, selected, input.evidence(), selected == null ? "raid.recruitment.no_useful_candidate" : "raid.recruitment.candidate_selected");
	}

	public RecruitmentAttempt recruitNext(MemberRef actor, String contentId, List<MemberRef> candidatePartyLeaders)
	{
		final RecruitmentPlan plan = plan(actor, contentId, candidatePartyLeaders);
		if (plan.selectedCandidate() == null)
		{
			return new RecruitmentAttempt(plan, RecruitmentAttemptStatus.NO_INVITE, null);
		}
		final InviteResult result = _party.inviteCommandChannel(actor, plan.selectedCandidate());
		return new RecruitmentAttempt(plan, result.delivered() ? RecruitmentAttemptStatus.INVITE_DELIVERED : RecruitmentAttemptStatus.INVITE_REJECTED, result);
	}

	private CandidateAssessment assessCandidate(MemberRef candidate, CurrentForceSnapshot current, Set<MemberRef> currentForceMembers, int recommendedMaximum, int memberDeficit, List<CapabilityRequirement> hardRequirements, List<CapabilityDeficit> deficits)
	{
		if (currentForceMembers.contains(candidate))
		{
			return rejected(candidate, CandidateStatus.CURRENT_FORCE_MEMBER, List.of(), "raid.recruitment.candidate.current_force_member");
		}
		final CurrentForceObservation observation = _party.currentForce(candidate);
		if ((observation.status() != CurrentForceStatus.AVAILABLE) || (observation.snapshot() == null))
		{
			return rejected(candidate, CandidateStatus.EVIDENCE_UNAVAILABLE, List.of(), "raid.recruitment.candidate.evidence_unavailable");
		}
		final CurrentForceSnapshot candidateForce = observation.snapshot();
		final List<MemberRef> members = candidateForce.members().stream().map(MemberSnapshot::ref).sorted(Comparator.comparing(MemberRef::stableKey)).toList();
		if (!candidate.equals(candidateForce.actor()) || !candidate.equals(candidateForce.partyLeader()))
		{
			return rejected(candidate, CandidateStatus.NOT_EXACT_PARTY_LEADER, members, "raid.recruitment.candidate.not_exact_party_leader");
		}
		if (candidateForce.commandChannelPresent() || (candidateForce.parties().size() != 1))
		{
			return rejected(candidate, CandidateStatus.NOT_STANDALONE_PARTY, members, "raid.recruitment.candidate.not_standalone_party");
		}
		if (members.stream().anyMatch(currentForceMembers::contains))
		{
			return rejected(candidate, CandidateStatus.CURRENT_FORCE_MEMBER, members, "raid.recruitment.candidate.current_force_overlap");
		}
		final int partyMembers = candidateForce.totalMemberCount();
		if ((current.totalMemberCount() + partyMembers) > recommendedMaximum)
		{
			return rejected(candidate, CandidateStatus.CONTENT_MEMBER_BOUND_EXCEEDED, members, "raid.recruitment.candidate.content_member_bound");
		}
		if ((current.parties().size() + 1) > PhantomPartyBackend.MAX_FORCE_PARTIES)
		{
			return rejected(candidate, CandidateStatus.FORCE_PARTY_BOUND_EXCEEDED, members, "raid.recruitment.candidate.force_party_bound");
		}
		if ((current.totalMemberCount() + partyMembers) > PhantomPartyBackend.MAX_FORCE_MEMBERS)
		{
			return rejected(candidate, CandidateStatus.FORCE_MEMBER_BOUND_EXCEEDED, members, "raid.recruitment.candidate.force_member_bound");
		}

		final List<CapabilityContribution> contributions = new ArrayList<>(deficits.size());
		int totalHardReduction = 0;
		for (int index = 0; index < deficits.size(); index++)
		{
			final CapabilityDeficit deficit = deficits.get(index);
			final CapabilityRequirement requirement = hardRequirements.get(index);
			final int satisfying = (int) candidateForce.members().stream().filter(member -> PhantomRaidReadinessService.satisfies(member, requirement)).count();
			final int reduction = Math.min(deficit.deficit(), satisfying);
			contributions.add(new CapabilityContribution(deficit.capabilityKey(), deficit.minimumRank(), satisfying, reduction));
			totalHardReduction += reduction;
		}
		final int usefulMembers = Math.min(memberDeficit, partyMembers);
		final CandidateStatus status = (totalHardReduction > 0) || (usefulMembers > 0) ? CandidateStatus.RECRUITABLE : CandidateStatus.NOT_USEFUL;
		final String reason = status == CandidateStatus.RECRUITABLE ? "raid.recruitment.candidate.recruitable" : "raid.recruitment.candidate.not_useful";
		return candidate(candidate, status, members, contributions, totalHardReduction, usefulMembers, observationEvidence(candidateForce), reason);
	}

	private static List<CapabilityRequirement> hardRequirements(List<CapabilityRequirement> requirements)
	{
		return requirements.stream().filter(CapabilityRequirement::required).sorted(Comparator.comparing(CapabilityRequirement::capabilityKey).thenComparingInt(CapabilityRequirement::minimumRank)).toList();
	}

	private static List<CapabilityDeficit> hardDeficits(List<MemberSnapshot> members, List<CapabilityRequirement> requirements)
	{
		return requirements.stream().map(requirement ->
		{
			final int satisfying = (int) members.stream().filter(member -> PhantomRaidReadinessService.satisfies(member, requirement)).count();
			return new CapabilityDeficit(requirement.capabilityKey(), requirement.minimumRank(), requirement.minimumCount(), satisfying, Math.max(0, requirement.minimumCount() - satisfying));
		}).toList();
	}

	private static CandidateAssessment rejected(MemberRef candidate, CandidateStatus status, List<MemberRef> members, String reason)
	{
		return candidate(candidate, status, members, List.of(), 0, 0, "", reason);
	}

	private static CandidateAssessment candidate(MemberRef candidate, CandidateStatus status, List<MemberRef> members, List<CapabilityContribution> contributions, int hardReduction, int usefulMembers, String observationEvidence, String reason)
	{
		final List<MemberRef> orderedMembers = members.stream().sorted(Comparator.comparing(MemberRef::stableKey)).toList();
		final String evidence = PhantomPartyModel.sha256(candidate.stableKey() + '|' + status + '|' + orderedMembers.stream().map(MemberRef::stableKey).toList() + '|' + contributions.stream().map(CapabilityContribution::evidenceKey).toList() + '|' + hardReduction + '|' + usefulMembers + '|' + observationEvidence + '|' + reason);
		return new CandidateAssessment(candidate, status, orderedMembers.size(), orderedMembers, contributions, hardReduction, usefulMembers, orderedMembers.size() - usefulMembers, evidence, reason);
	}

	private static String observationEvidence(CurrentForceSnapshot snapshot)
	{
		return PhantomPartyModel.sha256(snapshot.partyLeader().stableKey() + '|' + snapshot.commandChannelIdentity() + '|' + snapshot.members().stream().map(member -> member.ref().stableKey() + ':' + member.progressionHash() + ':' + member.capabilities()).toList());
	}

	private static RecruitmentPlan plan(RaidReadiness readiness, RecruitmentStatus status, String forceIdentity, int currentMembers, int minimum, int maximum, List<CapabilityDeficit> deficits, List<CandidateAssessment> candidates, MemberRef selected, String inputEvidence, String reason)
	{
		final List<CandidateAssessment> orderedCandidates = candidates.stream().sorted(Comparator.comparing(candidate -> candidate.candidateLeader().stableKey())).toList();
		final int memberDeficit = Math.max(0, minimum - currentMembers);
		final String selectedKey = selected == null ? "none" : selected.stableKey();
		final String evidence = PhantomPartyModel.sha256(readiness.contentId() + '|' + status + '|' + readiness.status() + '|' + readiness.targetAvailability() + '|' + forceIdentity + '|' + currentMembers + '|' + minimum + '|' + maximum + '|' + memberDeficit + '|' + deficits.stream().map(CapabilityDeficit::evidenceKey).toList() + '|' + orderedCandidates.stream().map(CandidateAssessment::evidenceHash).toList() + '|' + selectedKey + '|' + inputEvidence + '|' + reason);
		return new RecruitmentPlan(readiness.contentId(), status, readiness.status(), readiness.targetAvailability(), forceIdentity, currentMembers, minimum, maximum, memberDeficit, deficits, orderedCandidates, selected, evidence, reason);
	}

	private static String forceIdentity(CurrentForceSnapshot snapshot)
	{
		return snapshot.commandChannelPresent() ? snapshot.commandChannelIdentity() : "party:" + snapshot.partyLeader().stableKey();
	}

	private static InputValidation validateInput(List<MemberRef> candidates)
	{
		if (candidates == null)
		{
			return new InputValidation(false, List.of(), "raid.recruitment.candidates.null", PhantomPartyModel.sha256("candidates:null"));
		}
		if (candidates.size() > MAX_CANDIDATE_PARTY_LEADERS)
		{
			return new InputValidation(false, List.of(), "raid.recruitment.candidates.over_limit", PhantomPartyModel.sha256("candidates:over_limit:" + candidates.size()));
		}
		final Set<String> identities = new HashSet<>();
		for (MemberRef candidate : candidates)
		{
			if (candidate == null)
			{
				return new InputValidation(false, List.of(), "raid.recruitment.candidates.null_member", PhantomPartyModel.sha256("candidates:null_member:" + candidates.size()));
			}
			if (!identities.add(candidate.stableKey()))
			{
				return new InputValidation(false, List.of(), "raid.recruitment.candidates.duplicate", PhantomPartyModel.sha256("candidates:duplicate:" + candidate.stableKey()));
			}
		}
		final List<MemberRef> ordered = candidates.stream().sorted(Comparator.comparing(MemberRef::stableKey)).toList();
		return new InputValidation(true, ordered, "raid.recruitment.candidates.valid", PhantomPartyModel.sha256("candidates:" + ordered.stream().map(MemberRef::stableKey).toList()));
	}

	private record InputValidation(boolean valid, List<MemberRef> candidates, String reason, String evidence)
	{
	}
}
