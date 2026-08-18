/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.tests.phantoms;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

import org.l2jmobius.gameserver.model.groups.CommandChannelInvitationService;
import org.l2jmobius.gameserver.model.groups.CommandChannelInvitationService.InvitationIdentity;
import org.l2jmobius.gameserver.model.groups.CommandChannelInvitationService.InviteOutcome;
import org.l2jmobius.gameserver.model.groups.CommandChannelInvitationService.InviteResult;
import org.l2jmobius.gameserver.model.groups.CommandChannelInvitationService.RespondOutcome;
import org.l2jmobius.gameserver.model.groups.CommandChannelInvitationService.Response;
import org.l2jmobius.gameserver.model.groups.PartyDistributionType;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomCuratedKnowledgeParser;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeBuilder;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.ContentKind;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgePolicy;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeQuery;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeService;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomStaticManorParser;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyBackend;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyBackend.CurrentForceObservation;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyBackend.CurrentForceSnapshot;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyBackend.PartySnapshot;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberCapability;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberRef;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberSnapshot;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidAuthority;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidModel.BossObservation;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidModel.CandidateAssessment;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidModel.CandidateStatus;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidModel.CapabilityDeficit;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidModel.ReadinessStatus;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidModel.RecruitmentAttemptStatus;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidModel.RecruitmentPlan;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidModel.RecruitmentStatus;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidReadinessService;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidRecruitmentService;

public final class PhantomRaidRecruitmentSuite implements PhantomTestSuite
{
	private static final long SEED = 26002632L;
	private static final long NOW = 1_000_000L;
	private static final String HASH = "0".repeat(64);
	private static final String RAID = "raid.recruitment.synthetic";
	private static final String DYNAMIC_RAID = "raid.recruitment.dynamic";
	private static final String EPIC = "epic.recruitment.synthetic";
	private static final MemberRef ACTOR = MemberRef.phantom(1, 100);

	private Path _temporaryRoot;
	private PhantomGameKnowledgeService _knowledgeService;
	private PhantomGameKnowledgeQuery _knowledge;
	private MemoryPartyBackend _party;
	private StubRaidAuthority _authority;
	private PhantomRaidReadinessService _readiness;
	private PhantomRaidRecruitmentService _recruitment;

	@Override
	public String id()
	{
		return "raid-recruitment";
	}

	@Override
	public void beforeAll(PhantomTestContext context) throws Exception
	{
		PhantomAssertions.assertEquals(SEED, context.seed(), "Raid recruitment used the wrong deterministic seed.");
		_temporaryRoot = context.reportsDirectory().resolve("raid-recruitment-" + ProcessHandle.current().pid());
		Files.createDirectories(_temporaryRoot.resolve("curated"));
		Files.writeString(_temporaryRoot.resolve("Seeds.xml"), """
			<?xml version="1.0" encoding="UTF-8"?>
			<list>
				<castle id="1">
					<crop id="2" seedId="1" mature_Id="3" reward1="4" reward2="5" alternative="false" level="10" limit_seed="100" limit_crops="200" />
				</castle>
			</list>
			""", StandardCharsets.UTF_8);
		Files.writeString(_temporaryRoot.resolve("curated/knowledge.xml"), curatedXml(), StandardCharsets.UTF_8);
		final PhantomGameKnowledgePolicy policy = PhantomGameKnowledgePolicy.productionDefaults();
		final PhantomGameKnowledgeCoreSuite.SyntheticBackend backend = new PhantomGameKnowledgeCoreSuite.SyntheticBackend(false, false, false, 25d, false, 0);
		_knowledgeService = new PhantomGameKnowledgeService(new PhantomGameKnowledgeBuilder(backend, new PhantomStaticManorParser(_temporaryRoot.resolve("Seeds.xml"), policy), new PhantomCuratedKnowledgeParser(_temporaryRoot.resolve("curated"), backend, policy), PhantomGameKnowledgeCoreSuite.topology(), policy));
		PhantomAssertions.assertTrue(_knowledgeService.start(), "Raid recruitment knowledge fixture did not start.");
		_knowledge = _knowledgeService.query();
		_party = new MemoryPartyBackend();
		_authority = new StubRaidAuthority();
		_readiness = new PhantomRaidReadinessService(_knowledge, _party, _authority);
		_recruitment = new PhantomRaidRecruitmentService(_readiness, _party);
		context.record("raid.cp3.candidateLimit", PhantomRaidRecruitmentService.MAX_CANDIDATE_PARTY_LEADERS);
	}

	@Override
	public void afterAll(PhantomTestContext context) throws Exception
	{
		_knowledgeService.beginStop();
		_knowledgeService.finishStop();
		if (Files.exists(_temporaryRoot))
		{
			try (var stream = Files.walk(_temporaryRoot))
			{
				for (Path path : stream.sorted(Collections.reverseOrder()).toList())
				{
					Files.deleteIfExists(path);
				}
			}
		}
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("01-fresh-cp1-deficits-and-exact-capability-truth", _ -> testDeficitTruth());
		registry.add("02-null-duplicate-and-over-limit-input-fails-closed", _ -> testInputBounds());
		registry.add("03-exact-standalone-party-eligibility-and-whole-party-bounds", _ -> testCandidateEligibility());
		registry.add("04-hard-deficit-contribution-precedes-useful-bodies", _ -> testContributionPriority());
		registry.add("05-deterministic-ties-and-reordered-evidence", _ -> testDeterministicTies());
		registry.add("06-target-force-authority-and-ready-no-action", _ -> testNoActionAuthority());
		registry.add("07-one-canonical-invite-and-no-fallback-after-reject", _ -> testSingleInvite());
		registry.add("08-invitation-is-not-membership-or-readiness", _ -> testConsentAndFreshReadiness());
		registry.add("09-production-negative-scope", this::testNegativeScope);
	}

	private void testDeficitTruth()
	{
		reset();
		_party.force(ACTOR, standalone(ACTOR, ACTOR, member(ACTOR, capability("combat.tank", 1000))));
		final MemberRef candidate = MemberRef.real(200);
		final MemberRef learnedFalse = MemberRef.real(201);
		final MemberRef readyFalse = MemberRef.real(202);
		final MemberRef readyHealer = MemberRef.real(203);
		_party.force(candidate, standalone(candidate, candidate,
			member(candidate, capability("combat.heal", 900, false, true, true), capability("combat.buff", 900)),
			member(learnedFalse, capability("combat.heal", 900, true, false, true)),
			member(readyFalse, capability("combat.heal", 900, true, true, false)),
			member(readyHealer, capability("combat.heal", 900, true, true, true))));
		final RecruitmentPlan plan = _recruitment.plan(ACTOR, RAID, List.of(candidate));
		PhantomAssertions.assertEquals(2, plan.memberDeficit(), "Fresh CP1 member deficit changed.");
		PhantomAssertions.assertEquals(2, plan.hardCapabilityDeficits().size(), "Optional capability entered the hard-deficit list.");
		PhantomAssertions.assertEquals(0, deficit(plan, "combat.tank").deficit(), "Current ready tank was not counted.");
		PhantomAssertions.assertEquals(1, deficit(plan, "combat.heal").deficit(), "Missing healer deficit changed.");
		final CandidateAssessment assessment = assessment(plan, candidate);
		PhantomAssertions.assertEquals(1, assessment.totalHardDeficitReduction(), "Only exact intrinsic, learned and readyNow healer should reduce the hard deficit.");
		PhantomAssertions.assertEquals(2, assessment.usefulMemberContribution(), "Candidate useful member contribution changed.");
		PhantomAssertions.assertEquals(2, assessment.excessMembers(), "Candidate excess arithmetic changed.");

		final MemberRef epicCandidate = MemberRef.real(210);
		_party.force(epicCandidate, standalone(epicCandidate, epicCandidate, member(epicCandidate, capability("combat.heal", 950), capability("combat.resurrection", 950))));
		final RecruitmentPlan epic = _recruitment.plan(ACTOR, EPIC, List.of(epicCandidate));
		PhantomAssertions.assertEquals(RecruitmentStatus.TARGET_UNKNOWN, epic.status(), "Unsupported EPIC content did not fail closed before recruitment.");
		PhantomAssertions.assertEquals(null, epic.selectedCandidate(), "Unsupported EPIC selected a contributing Party.");
	}

	private void testInputBounds()
	{
		reset();
		_party.force(ACTOR, standalone(ACTOR, ACTOR, member(ACTOR, capability("combat.tank", 1000))));
		final MemberRef candidate = MemberRef.real(200);
		_party.force(candidate, standalone(candidate, candidate, member(candidate, capability("combat.heal", 900))));
		final List<MemberRef> nullMember = new ArrayList<>();
		nullMember.add(candidate);
		nullMember.add(null);
		PhantomAssertions.assertEquals(RecruitmentStatus.INVALID_INPUT, _recruitment.plan(ACTOR, RAID, null).status(), "Null candidate list did not fail closed.");
		PhantomAssertions.assertEquals(RecruitmentStatus.INVALID_INPUT, _recruitment.plan(ACTOR, RAID, nullMember).status(), "Null candidate did not fail closed.");
		PhantomAssertions.assertEquals(RecruitmentStatus.INVALID_INPUT, _recruitment.plan(ACTOR, RAID, List.of(candidate, candidate)).status(), "Duplicate stable identity did not fail closed.");
		PhantomAssertions.assertEquals(RecruitmentStatus.INVALID_INPUT, _recruitment.plan(ACTOR, RAID, Collections.nCopies(17, candidate)).status(), "Over-16 candidate input did not fail closed.");
		PhantomAssertions.assertEquals(RecruitmentAttemptStatus.NO_INVITE, _recruitment.recruitNext(ACTOR, RAID, nullMember).status(), "Invalid input attempted an invite.");
		PhantomAssertions.assertEquals(0, _party.inviteCalls, "Invalid input reached CP2.");
	}

	private void testCandidateEligibility()
	{
		reset();
		_party.force(ACTOR, standalone(ACTOR, ACTOR, member(ACTOR, capability("combat.tank", 1000))));
		final MemberRef exact = MemberRef.real(200);
		_party.force(exact, standalone(exact, exact, member(exact, capability("combat.heal", 900))));
		final MemberRef nonLeader = MemberRef.real(300);
		final MemberRef actualLeader = MemberRef.real(301);
		_party.force(nonLeader, standalone(nonLeader, actualLeader, member(actualLeader), member(nonLeader)));
		final MemberRef channelCandidate = MemberRef.real(400);
		final List<MemberRef> channelOwnParty = List.of(channelCandidate, MemberRef.real(401), MemberRef.real(402), MemberRef.real(403), MemberRef.real(404), MemberRef.real(405));
		final MemberRef secondLeader = MemberRef.real(410);
		final List<MemberRef> channelOtherParty = List.of(secondLeader, MemberRef.real(411), MemberRef.real(412), MemberRef.real(413), MemberRef.real(414), MemberRef.real(415));
		_party.force(channelCandidate, channel(channelCandidate, channelCandidate, List.of(
			party(channelCandidate, channelOwnParty.toArray(MemberRef[]::new)),
			party(secondLeader, channelOtherParty.toArray(MemberRef[]::new))), java.util.stream.Stream.concat(channelOwnParty.stream(), channelOtherParty.stream()).map(reference -> member(reference)).toList()));
		final MemberRef largeChannelNonLeader = MemberRef.real(420);
		final MemberRef largeChannelLeader = MemberRef.real(421);
		final List<MemberRef> nonLeaderOwnParty = List.of(largeChannelLeader, largeChannelNonLeader, MemberRef.real(422), MemberRef.real(423), MemberRef.real(424));
		final MemberRef nonLeaderOtherLeader = MemberRef.real(430);
		final List<MemberRef> nonLeaderOtherParty = List.of(nonLeaderOtherLeader, MemberRef.real(431), MemberRef.real(432), MemberRef.real(433), MemberRef.real(434));
		_party.force(largeChannelNonLeader, channel(largeChannelNonLeader, largeChannelLeader, List.of(
			party(largeChannelLeader, nonLeaderOwnParty.toArray(MemberRef[]::new)),
			party(nonLeaderOtherLeader, nonLeaderOtherParty.toArray(MemberRef[]::new))), java.util.stream.Stream.concat(nonLeaderOwnParty.stream(), nonLeaderOtherParty.stream()).map(reference -> member(reference)).toList()));
		final MemberRef ambiguous = MemberRef.real(440);
		final MemberRef ambiguousOtherLeader = MemberRef.real(441);
		_party.force(ambiguous, channel(ambiguous, ambiguous, List.of(
			party(ambiguous, ambiguous),
			party(ambiguousOtherLeader, ambiguousOtherLeader, ambiguous)), List.of(member(ambiguous), member(ambiguousOtherLeader))));
		final MemberRef unavailable = MemberRef.real(500);
		PhantomAssertions.assertEquals(12, _party.currentForce(channelCandidate).snapshot().totalMemberCount(), "Exact-leader fixture is not a large CommandChannel.");
		PhantomAssertions.assertEquals(10, _party.currentForce(largeChannelNonLeader).snapshot().totalMemberCount(), "Non-leader fixture is not a large CommandChannel.");
		final RecruitmentPlan plan = _recruitment.plan(ACTOR, RAID, List.of(unavailable, ambiguous, largeChannelNonLeader, channelCandidate, nonLeader, exact, ACTOR));
		PhantomAssertions.assertEquals(CandidateStatus.RECRUITABLE, assessment(plan, exact).status(), "Exact standalone Party leader was rejected.");
		PhantomAssertions.assertEquals(exact, plan.selectedCandidate(), "Standalone candidate selection changed while bounding large-CC evidence.");
		PhantomAssertions.assertEquals(CandidateStatus.NOT_EXACT_PARTY_LEADER, assessment(plan, nonLeader).status(), "Non-leader candidate was accepted.");
		final CandidateAssessment channelLeaderAssessment = assessment(plan, channelCandidate);
		PhantomAssertions.assertEquals(CandidateStatus.NOT_STANDALONE_PARTY, channelLeaderAssessment.status(), "Large-CC exact Party leader was not rejected with the typed standalone status.");
		PhantomAssertions.assertEquals(channelOwnParty.size(), channelLeaderAssessment.partyMemberCount(), "Large-CC leader evidence used the whole CommandChannel member count.");
		PhantomAssertions.assertTrue(channelOwnParty.containsAll(channelLeaderAssessment.members()) && channelLeaderAssessment.members().containsAll(channelOwnParty), "Large-CC leader evidence did not contain exactly the candidate own Party.");
		final CandidateAssessment channelNonLeaderAssessment = assessment(plan, largeChannelNonLeader);
		PhantomAssertions.assertEquals(CandidateStatus.NOT_EXACT_PARTY_LEADER, channelNonLeaderAssessment.status(), "Large-CC non-leader did not receive the typed exact-leader rejection.");
		PhantomAssertions.assertEquals(nonLeaderOwnParty.size(), channelNonLeaderAssessment.partyMemberCount(), "Large-CC non-leader evidence used the whole CommandChannel member count.");
		PhantomAssertions.assertTrue(nonLeaderOwnParty.containsAll(channelNonLeaderAssessment.members()) && channelNonLeaderAssessment.members().containsAll(nonLeaderOwnParty), "Large-CC non-leader evidence did not contain exactly the candidate own Party.");
		PhantomAssertions.assertEquals(CandidateStatus.EVIDENCE_UNAVAILABLE, assessment(plan, ambiguous).status(), "Ambiguous candidate own-Party evidence did not fail closed.");
		PhantomAssertions.assertEquals(0, assessment(plan, ambiguous).partyMemberCount(), "Ambiguous candidate fabricated Party evidence.");
		PhantomAssertions.assertEquals(CandidateStatus.EVIDENCE_UNAVAILABLE, assessment(plan, unavailable).status(), "Unavailable candidate evidence was guessed.");
		PhantomAssertions.assertEquals(CandidateStatus.CURRENT_FORCE_MEMBER, assessment(plan, ACTOR).status(), "Current-force member was accepted as a candidate.");

		final List<MemberSnapshot> currentMembers = new ArrayList<>();
		currentMembers.add(member(ACTOR, capability("combat.tank", 1000)));
		for (int index = 0; index < 7; index++)
		{
			currentMembers.add(member(MemberRef.real(600 + index)));
		}
		_party.force(ACTOR, standalone(ACTOR, ACTOR, currentMembers.toArray(MemberSnapshot[]::new)));
		final MemberRef overBound = MemberRef.real(700);
		_party.force(overBound, standalone(overBound, overBound, member(overBound, capability("combat.heal", 900)), member(MemberRef.real(701))));
		final CandidateAssessment bounded = assessment(_recruitment.plan(ACTOR, RAID, List.of(overBound)), overBound);
		PhantomAssertions.assertEquals(CandidateStatus.CONTENT_MEMBER_BOUND_EXCEEDED, bounded.status(), "Whole Party exceeding recommendedMaxParty was split or accepted.");
		PhantomAssertions.assertEquals(2, bounded.partyMemberCount(), "Rejected whole-Party evidence lost exact member count.");
	}

	private void testContributionPriority()
	{
		reset();
		_party.force(ACTOR, standalone(ACTOR, ACTOR, member(ACTOR, capability("combat.tank", 1000))));
		final MemberRef hard = MemberRef.real(200);
		final MemberRef bodies = MemberRef.real(300);
		_party.force(hard, standalone(hard, hard, member(hard, capability("combat.heal", 900))));
		_party.force(bodies, standalone(bodies, bodies, member(bodies), member(MemberRef.real(301))));
		final RecruitmentPlan plan = _recruitment.plan(ACTOR, RAID, List.of(bodies, hard));
		PhantomAssertions.assertEquals(hard, plan.selectedCandidate(), "Useful bodies outranked hard deficit reduction.");
		PhantomAssertions.assertEquals(1, assessment(plan, hard).totalHardDeficitReduction(), "Hard candidate contribution changed.");
		PhantomAssertions.assertEquals(0, assessment(plan, bodies).totalHardDeficitReduction(), "Bodies-only candidate fabricated a hard contribution.");
		PhantomAssertions.assertEquals(2, assessment(plan, bodies).usefulMemberContribution(), "Bodies-only candidate did not reduce the current member deficit.");

		final MemberRef second = MemberRef.real(110);
		final MemberRef third = MemberRef.real(111);
		_party.force(ACTOR, standalone(ACTOR, ACTOR, member(ACTOR, capability("combat.tank", 1000)), member(second), member(third)));
		final MemberRef neither = MemberRef.real(400);
		_party.force(neither, standalone(neither, neither, member(neither, capability("combat.buff", 900))));
		final CandidateAssessment none = assessment(_recruitment.plan(ACTOR, RAID, List.of(neither)), neither);
		PhantomAssertions.assertEquals(CandidateStatus.NOT_USEFUL, none.status(), "Party reducing neither current hard nor member deficit became recruitable.");
	}

	private void testDeterministicTies()
	{
		reset();
		_party.force(ACTOR, standalone(ACTOR, ACTOR, member(ACTOR, capability("combat.tank", 1000))));
		final MemberRef oneUseful = MemberRef.real(200);
		final MemberRef twoUseful = MemberRef.real(300);
		_party.force(oneUseful, standalone(oneUseful, oneUseful, member(oneUseful, capability("combat.heal", 900))));
		_party.force(twoUseful, standalone(twoUseful, twoUseful, member(twoUseful, capability("combat.heal", 900)), member(MemberRef.real(301))));
		PhantomAssertions.assertEquals(twoUseful, _recruitment.plan(ACTOR, RAID, List.of(oneUseful, twoUseful)).selectedCandidate(), "Greater useful member contribution did not break the hard-reduction tie.");

		final MemberRef second = MemberRef.real(110);
		_party.force(ACTOR, standalone(ACTOR, ACTOR, member(ACTOR, capability("combat.tank", 1000)), member(second)));
		PhantomAssertions.assertEquals(oneUseful, _recruitment.plan(ACTOR, RAID, List.of(twoUseful, oneUseful)).selectedCandidate(), "Lower excess did not break the useful-contribution tie.");

		final MemberRef stableHigh = MemberRef.real(600);
		final MemberRef stableLow = MemberRef.real(500);
		_party.force(stableHigh, standalone(stableHigh, stableHigh, member(stableHigh, capability("combat.heal", 900))));
		_party.force(stableLow, standalone(stableLow, stableLow, member(stableLow, capability("combat.heal", 900))));
		final RecruitmentPlan reversed = _recruitment.plan(ACTOR, RAID, List.of(stableHigh, stableLow));
		final RecruitmentPlan ordered = _recruitment.plan(ACTOR, RAID, List.of(stableLow, stableHigh));
		PhantomAssertions.assertEquals(stableLow, reversed.selectedCandidate(), "Stable key did not break the final tie.");
		PhantomAssertions.assertEquals(reversed.selectedCandidate(), ordered.selectedCandidate(), "Input order changed the selected Party.");
		PhantomAssertions.assertEquals(reversed.evidenceHash(), ordered.evidenceHash(), "Input order changed deterministic evidence.");
	}

	private void testNoActionAuthority()
	{
		reset();
		final MemberRef candidate = MemberRef.real(200);
		_party.force(candidate, standalone(candidate, candidate, member(candidate, capability("combat.heal", 900))));
		_authority.raid = observation(ContentKind.RAID, 100, true, "UNDEFINED", false, false, false, null);
		PhantomAssertions.assertEquals(RecruitmentStatus.TARGET_UNKNOWN, _recruitment.recruitNext(ACTOR, RAID, List.of(candidate)).plan().status(), "Unknown target did not stop recruitment.");
		_authority.raid = observation(ContentKind.RAID, 100, true, "DEAD", false, false, false, NOW + 1000);
		PhantomAssertions.assertEquals(RecruitmentStatus.TARGET_UNAVAILABLE, _recruitment.recruitNext(ACTOR, RAID, List.of(candidate)).plan().status(), "Unavailable target did not stop recruitment.");
		_authority.raid = availableRaid();
		_party.force(ACTOR, CurrentForceObservation.partyAbsent());
		PhantomAssertions.assertEquals(RecruitmentStatus.GROUP_ABSENT, _recruitment.recruitNext(ACTOR, RAID, List.of(candidate)).plan().status(), "GROUP_ABSENT attempted recruitment.");
		_party.force(ACTOR, CurrentForceObservation.unavailable("test.current_force.unavailable"));
		PhantomAssertions.assertEquals(RecruitmentStatus.FORCE_UNAVAILABLE, _recruitment.recruitNext(ACTOR, RAID, List.of(candidate)).plan().status(), "Unavailable force attempted recruitment.");
		_party.force(ACTOR, CurrentForceObservation.boundsExceeded());
		PhantomAssertions.assertEquals(RecruitmentStatus.FORCE_OVER_BOUND, _recruitment.recruitNext(ACTOR, RAID, List.of(candidate)).plan().status(), "Over-bound force attempted recruitment.");

		final MemberRef leader = MemberRef.real(101);
		_party.force(ACTOR, standalone(ACTOR, leader, member(leader, capability("combat.tank", 1000)), member(ACTOR)));
		PhantomAssertions.assertEquals(RecruitmentStatus.ACTOR_NOT_INVITATION_AUTHORITY, _recruitment.recruitNext(ACTOR, RAID, List.of(candidate)).plan().status(), "Non-Party-leader actor attempted recruitment.");
		_party.force(ACTOR, channel(ACTOR, leader, List.of(party(ACTOR, ACTOR), party(leader, leader)), List.of(member(ACTOR, capability("combat.tank", 1000)), member(leader))));
		PhantomAssertions.assertEquals(RecruitmentStatus.ACTOR_NOT_INVITATION_AUTHORITY, _recruitment.recruitNext(ACTOR, RAID, List.of(candidate)).plan().status(), "Non-CC-leader actor attempted recruitment.");

		final MemberRef healer = MemberRef.real(102);
		_party.force(ACTOR, standalone(ACTOR, ACTOR, member(ACTOR, capability("combat.tank", 1000)), member(leader), member(healer, capability("combat.heal", 900))));
		PhantomAssertions.assertEquals(RecruitmentStatus.GROUP_READY, _recruitment.recruitNext(ACTOR, RAID, List.of(candidate)).plan().status(), "GROUP_READY force attempted recruitment.");
		PhantomAssertions.assertEquals(0, _party.inviteCalls, "A no-action state reached CP2.");
	}

	private void testSingleInvite()
	{
		reset();
		final MemberRef hard = MemberRef.real(200);
		final MemberRef fallback = MemberRef.real(300);
		_party.force(ACTOR, standalone(ACTOR, ACTOR, member(ACTOR, capability("combat.tank", 1000))));
		_party.force(hard, standalone(hard, hard, member(hard, capability("combat.heal", 900))));
		_party.force(fallback, standalone(fallback, fallback, member(fallback), member(MemberRef.real(301))));
		_party.nextOutcome = InviteOutcome.TARGET_BUSY;
		final var rejected = _recruitment.recruitNext(ACTOR, RAID, List.of(fallback, hard));
		PhantomAssertions.assertEquals(RecruitmentAttemptStatus.INVITE_REJECTED, rejected.status(), "Canonical drift/reject was not returned exactly.");
		PhantomAssertions.assertEquals(InviteOutcome.TARGET_BUSY, rejected.inviteResult().outcome(), "Exact CP2 reject outcome changed.");
		PhantomAssertions.assertEquals(List.of(hard), _party.invitedCandidates, "Candidate #2 was attempted after candidate #1 rejection.");

		_party.inviteCalls = 0;
		_party.invitedCandidates.clear();
		_party.nextOutcome = InviteOutcome.DELIVERED;
		final var delivered = _recruitment.recruitNext(ACTOR, RAID, List.of(fallback, hard));
		PhantomAssertions.assertEquals(RecruitmentAttemptStatus.INVITE_DELIVERED, delivered.status(), "Delivered CP2 invite was not returned.");
		PhantomAssertions.assertEquals(hard.characterObjectId(), delivered.inviteResult().identity().inviteeObjectId(), "Exact CP2 invitation identity lost the selected leader.");
		PhantomAssertions.assertEquals(1, _party.inviteCalls, "recruitNext sent more than one invite.");
		PhantomAssertions.assertEquals(0, _party.respondCalls, "Production recruitment fabricated a target-side response.");
	}

	private void testConsentAndFreshReadiness()
	{
		reset();
		final MemberRef realCandidate = MemberRef.real(200);
		_party.force(ACTOR, standalone(ACTOR, ACTOR, member(ACTOR, capability("combat.tank", 1000))));
		_party.force(realCandidate, standalone(realCandidate, realCandidate, member(realCandidate, capability("combat.heal", 900))));
		final var invitation = _recruitment.recruitNext(ACTOR, DYNAMIC_RAID, List.of(realCandidate));
		PhantomAssertions.assertEquals(RecruitmentAttemptStatus.INVITE_DELIVERED, invitation.status(), "REAL candidate did not retain ordinary pending semantics.");
		PhantomAssertions.assertEquals(1, _party.currentForce(ACTOR).snapshot().totalMemberCount(), "Invitation counted the candidate Party as joined.");
		PhantomAssertions.assertEquals(ReadinessStatus.GROUP_INCOMPLETE, _readiness.assess(ACTOR, DYNAMIC_RAID).status(), "Invitation granted free readiness.");
		PhantomAssertions.assertEquals(0, _party.respondCalls, "Recruitment auto-accepted a REAL candidate.");
		final var accepted = _party.respondCommandChannel(realCandidate, Response.ACCEPT, invitation.inviteResult().identity());
		PhantomAssertions.assertEquals(RespondOutcome.ACCEPTED, accepted.outcome(), "Fixture exact target-side ACCEPT failed.");
		PhantomAssertions.assertEquals(2, _party.currentForce(ACTOR).snapshot().totalMemberCount(), "Exact target-side ACCEPT did not change canonical force membership.");
		PhantomAssertions.assertEquals(ReadinessStatus.GROUP_READY, _readiness.assess(ACTOR, DYNAMIC_RAID).status(), "Fresh CP1 did not observe accepted members/capabilities.");

		reset();
		final MemberRef phantomCandidate = MemberRef.phantom(2, 300);
		_party.force(ACTOR, standalone(ACTOR, ACTOR, member(ACTOR, capability("combat.tank", 1000))));
		_party.force(phantomCandidate, standalone(phantomCandidate, phantomCandidate, member(phantomCandidate, capability("combat.heal", 900))));
		final var phantomInvitation = _recruitment.recruitNext(ACTOR, DYNAMIC_RAID, List.of(phantomCandidate));
		PhantomAssertions.assertEquals(RecruitmentAttemptStatus.INVITE_DELIVERED, phantomInvitation.status(), "PHANTOM pending invitation was not returned.");
		PhantomAssertions.assertEquals(phantomInvitation.inviteResult().identity(), _party.observeCommandChannelInvitation(phantomCandidate).orElseThrow().identity(), "PHANTOM pending identity is not observable for later policy.");
		PhantomAssertions.assertEquals(0, _party.respondCalls, "PHANTOM candidate was auto-accepted.");
		PhantomAssertions.assertEquals(1, _party.currentForce(ACTOR).snapshot().totalMemberCount(), "PHANTOM invitation changed membership without consent.");
	}

	private void testNegativeScope(PhantomTestContext context) throws Exception
	{
		final String source = Files.readString(context.moduleRoot().resolve("java/org/l2jmobius/gameserver/phantoms/raid/PhantomRaidRecruitmentService.java"), StandardCharsets.UTF_8);
		PhantomAssertions.assertTrue(source.contains("_readiness.assess(actor, contentId)") && source.contains("_party.currentForce(candidate)") && source.contains("_party.inviteCommandChannel(actor, plan.selectedCandidate())"), "CP3 does not use the accepted CP1/Goal017/CP2 seams.");
		PhantomAssertions.assertEquals(1, occurrences(source, "inviteCommandChannel("), "Production CP3 contains more than one outbound invite call site.");
		for (String forbidden : List.of("respondCommandChannel", "World.getPlayers", "World.getInstance", "PhantomProfile", "materialize(", "ThreadPool", "ScheduledFuture", "new Thread", "Navigation", "Combat", "CreatureSay", "Chat", "RaidStore", "new CommandChannel", ".addParty(", ".removeParty(", ".setCommandChannel("))
		{
			PhantomAssertions.assertFalse(source.contains(forbidden), "Production CP3 crossed a forbidden boundary: " + forbidden);
		}
		final String system = Files.readString(context.moduleRoot().resolve("java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java"), StandardCharsets.UTF_8);
		PhantomAssertions.assertTrue(system.contains("new PhantomRaidRecruitmentService(_raidReadinessService, partyBackend)") && system.contains("raidRecruitment()"), "Passive CP3 production access is not constructed.");
	}

	private void reset()
	{
		_party.reset();
		_authority.raid = availableRaid();
		_authority.epic = observation(ContentKind.EPIC, 101, true, "test", true, true, false, 0L);
	}

	private static CapabilityDeficit deficit(RecruitmentPlan plan, String key)
	{
		return plan.hardCapabilityDeficits().stream().filter(value -> value.capabilityKey().equals(key)).findFirst().orElseThrow();
	}

	private static CandidateAssessment assessment(RecruitmentPlan plan, MemberRef candidate)
	{
		return plan.candidates().stream().filter(value -> value.candidateLeader().equals(candidate)).findFirst().orElseThrow();
	}

	private static CurrentForceObservation standalone(MemberRef actor, MemberRef leader, MemberSnapshot... members)
	{
		final List<MemberSnapshot> snapshots = List.of(members);
		final List<MemberRef> references = snapshots.stream().map(MemberSnapshot::ref).toList();
		return CurrentForceObservation.available(new CurrentForceSnapshot(actor, leader, "", null, 0, snapshots.size(), List.of(new PartySnapshot(leader, references, PartyDistributionType.FINDERS_KEEPERS)), snapshots));
	}

	private static CurrentForceObservation channel(MemberRef actor, MemberRef commandChannelLeader, List<PartySnapshot> parties, List<MemberSnapshot> members)
	{
		final MemberRef partyLeader = parties.stream().filter(party -> party.members().contains(actor)).map(PartySnapshot::leader).findFirst().orElseThrow();
		return CurrentForceObservation.available(new CurrentForceSnapshot(actor, partyLeader, "command-channel:" + commandChannelLeader.characterObjectId(), commandChannelLeader, 1, members.size(), parties, members));
	}

	private static PartySnapshot party(MemberRef leader, MemberRef... members)
	{
		return new PartySnapshot(leader, List.of(members), PartyDistributionType.FINDERS_KEEPERS);
	}

	private static MemberSnapshot member(MemberRef reference, MemberCapability... capabilities)
	{
		return new MemberSnapshot(reference, 1, 0, 0, 0, 0, 100, 100, 100, false, false, false, false, 0, List.of(), List.of(capabilities), HASH);
	}

	private static MemberCapability capability(String key, int rank)
	{
		return capability(key, rank, true, true, true);
	}

	private static MemberCapability capability(String key, int rank, boolean intrinsic, boolean learned, boolean readyNow)
	{
		return new MemberCapability(key, "test", rank, 500, 1, "SELF", intrinsic, learned, readyNow, readyNow ? "ready" : "not.ready", rank, "goal026cp3.fixture");
	}

	private static BossObservation availableRaid()
	{
		return observation(ContentKind.RAID, 100, true, "ALIVE", true, true, false, 0L);
	}

	private static BossObservation observation(ContentKind kind, int npcId, boolean defined, String rawStatus, boolean live, boolean exact, boolean dead, Long respawn)
	{
		return new BossObservation(kind, npcId, defined, rawStatus, live, exact, dead, respawn, NOW, "test.raid.recruitment");
	}

	private static int occurrences(String value, String token)
	{
		int count = 0;
		int index = 0;
		while ((index = value.indexOf(token, index)) >= 0)
		{
			count++;
			index += token.length();
		}
		return count;
	}

	private static String curatedXml()
	{
		final StringBuilder result = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<knowledge schemaVersion=\"1\" datasetId=\"raid-recruitment\" datasetVersion=\"1\">\n");
		for (String capability : PhantomGameKnowledgeBuilder.REQUIRED_CAPABILITIES.stream().sorted().toList())
		{
			result.append("\t<classCapability classId=\"1\" capabilityKey=\"").append(capability).append("\" rank=\"1000\">\n\t\t<skill id=\"500\" level=\"1\" />\n\t\t<source path=\"data/source.xml\" />\n\t</classCapability>\n");
		}
		result.append("""
				<contentRequirement contentId="rift.recruitment.fixture" contentKind="RIFT" recommendedMinParty="1" recommendedMaxParty="9">
					<requirement capabilityKey="combat.tank" minimumCount="1" minimumRank="800" required="true" />
					<source path="data/source.xml" />
				</contentRequirement>

				<contentRequirement contentId="raid.recruitment.synthetic" contentKind="RAID" npcId="100" recommendedMinParty="3" recommendedMaxParty="9">
					<requirement capabilityKey="combat.tank" minimumCount="1" minimumRank="800" required="true" />
					<requirement capabilityKey="combat.heal" minimumCount="1" minimumRank="850" required="true" />
					<requirement capabilityKey="combat.buff" minimumCount="1" minimumRank="800" required="false" />
					<source path="data/source.xml" />
				</contentRequirement>
				<contentRequirement contentId="raid.recruitment.dynamic" contentKind="RAID" npcId="100" recommendedMinParty="2" recommendedMaxParty="9">
					<requirement capabilityKey="combat.tank" minimumCount="1" minimumRank="800" required="true" />
					<requirement capabilityKey="combat.heal" minimumCount="1" minimumRank="850" required="true" />
					<source path="data/source.xml" />
				</contentRequirement>
				<contentRequirement contentId="epic.recruitment.synthetic" contentKind="EPIC" npcId="101" recommendedMinParty="2" recommendedMaxParty="45">
					<requirement capabilityKey="combat.tank" minimumCount="1" minimumRank="850" required="true" />
					<requirement capabilityKey="combat.heal" minimumCount="1" minimumRank="900" required="true" />
					<requirement capabilityKey="combat.resurrection" minimumCount="1" minimumRank="900" required="true" />
					<source path="data/source.xml" />
				</contentRequirement>
			</knowledge>
			""");
		return result.toString();
	}

	private static final class StubRaidAuthority implements PhantomRaidAuthority
	{
		private BossObservation raid = availableRaid();
		private BossObservation epic = observation(ContentKind.EPIC, 101, true, "test", true, true, false, 0L);

		@Override
		public BossObservation observe(ContentKind contentKind, int npcId)
		{
			return contentKind == ContentKind.RAID ? raid : epic;
		}
	}

	private static final class MemoryPartyBackend implements PhantomPartyBackend
	{
		private final Map<MemberRef, CurrentForceObservation> forces = new HashMap<>();
		private final List<MemberRef> invitedCandidates = new ArrayList<>();
		private InviteOutcome nextOutcome = InviteOutcome.DELIVERED;
		private int inviteCalls;
		private int respondCalls;
		private long sequence;
		private MemberRef pendingRequester;
		private MemberRef pendingTarget;
		private InvitationIdentity pendingIdentity;

		private void reset()
		{
			forces.clear();
			invitedCandidates.clear();
			nextOutcome = InviteOutcome.DELIVERED;
			inviteCalls = 0;
			respondCalls = 0;
			pendingRequester = null;
			pendingTarget = null;
			pendingIdentity = null;
		}

		private void force(MemberRef actor, CurrentForceObservation observation)
		{
			forces.put(actor, observation);
		}

		@Override
		public CurrentForceObservation currentForce(MemberRef actor)
		{
			return forces.getOrDefault(actor, CurrentForceObservation.unavailable("test.current_force.missing"));
		}

		@Override
		public InviteResult inviteCommandChannel(MemberRef requester, MemberRef target)
		{
			inviteCalls++;
			invitedCandidates.add(target);
			if (nextOutcome != InviteOutcome.DELIVERED)
			{
				return new InviteResult(nextOutcome, null);
			}
			pendingRequester = requester;
			pendingTarget = target;
			pendingIdentity = new InvitationIdentity(++sequence, requester.characterObjectId(), target.characterObjectId());
			return new InviteResult(InviteOutcome.DELIVERED, pendingIdentity);
		}

		@Override
		public CommandChannelInvitationService.RespondResult respondCommandChannel(MemberRef invitee, Response response, InvitationIdentity identity)
		{
			respondCalls++;
			if ((pendingIdentity == null) || !pendingTarget.equals(invitee) || !pendingIdentity.equals(identity))
			{
				return new CommandChannelInvitationService.RespondResult(RespondOutcome.STALE_INVITE, identity, false);
			}
			if (response == Response.REFUSE)
			{
				clearPending();
				return new CommandChannelInvitationService.RespondResult(RespondOutcome.REFUSED, identity, false);
			}
			final CurrentForceSnapshot requester = forces.get(pendingRequester).snapshot();
			final CurrentForceSnapshot target = forces.get(pendingTarget).snapshot();
			final List<PartySnapshot> parties = new ArrayList<>(requester.parties());
			parties.addAll(target.parties());
			final List<MemberSnapshot> members = new ArrayList<>(requester.members());
			members.addAll(target.members());
			final MemberRef requesterRef = pendingRequester;
			final MemberRef targetRef = pendingTarget;
			forces.put(requesterRef, channel(requesterRef, requesterRef, parties, members));
			forces.put(targetRef, channel(targetRef, requesterRef, parties, members));
			clearPending();
			return new CommandChannelInvitationService.RespondResult(RespondOutcome.ACCEPTED, identity, true);
		}

		@Override
		public Optional<CommandChannelInvitationService.InvitationSnapshot> observeCommandChannelInvitation(MemberRef invitee)
		{
			if ((pendingIdentity == null) || !pendingTarget.equals(invitee))
			{
				return Optional.empty();
			}
			return Optional.of(new CommandChannelInvitationService.InvitationSnapshot(pendingIdentity, pendingRequester.characterObjectId(), pendingTarget.characterObjectId(), pendingRequester.characterObjectId(), 1));
		}

		private void clearPending()
		{
			pendingRequester = null;
			pendingTarget = null;
			pendingIdentity = null;
		}

		@Override public OptionalLong managedProfileId(int characterObjectId) { return OptionalLong.empty(); }
		@Override public Optional<MemberRef> currentMember(long profileId) { return Optional.empty(); }
		@Override public org.l2jmobius.gameserver.model.groups.PartyInvitationService.InviteResult invite(MemberRef requester, MemberRef target, PartyDistributionType distribution) { throw new UnsupportedOperationException(); }
		@Override public org.l2jmobius.gameserver.model.groups.PartyInvitationService.RespondResult respond(MemberRef invitee, org.l2jmobius.gameserver.model.groups.PartyInvitationService.Response response, org.l2jmobius.gameserver.model.groups.PartyInvitationService.InvitationIdentity identity) { throw new UnsupportedOperationException(); }
		@Override public org.l2jmobius.gameserver.model.groups.PartyInvitationService.MembershipOutcome leave(MemberRef member) { throw new UnsupportedOperationException(); }
		@Override public org.l2jmobius.gameserver.model.groups.PartyInvitationService.MembershipOutcome expel(MemberRef requester, MemberRef member) { throw new UnsupportedOperationException(); }
		@Override public org.l2jmobius.gameserver.model.groups.PartyInvitationService.MembershipOutcome transferLeader(MemberRef requester, MemberRef member) { throw new UnsupportedOperationException(); }
		@Override public Optional<PartySnapshot> observe(MemberRef member) { return currentForce(member).snapshot() == null ? Optional.empty() : currentForce(member).snapshot().parties().stream().filter(party -> party.members().contains(member)).findFirst(); }
		@Override public Optional<MemberSnapshot> memberSnapshot(MemberRef member) { return currentForce(member).snapshot() == null ? Optional.empty() : currentForce(member).snapshot().members().stream().filter(value -> value.ref().equals(member)).findFirst(); }
		@Override public List<MemberCapability> capabilities(MemberRef actor, int exactTargetObjectId) { return List.of(); }
		@Override public boolean materialize(long profileId) { return false; }
	}
}
