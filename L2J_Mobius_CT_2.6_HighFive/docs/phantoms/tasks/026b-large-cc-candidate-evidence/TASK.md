# Goal 026B — bound raid candidate Party evidence

Branch: `feature/phantom-world`
Required parent: `c9d2d429b1a7655d36676f8f8496de53d9cff11d`
Required subject: `fix(phantoms): bound raid candidate party evidence`
Seed: `26002632`

Read first `PRIOR_INDEPENDENT_REVIEW.md` and fix only R026B-01.

Primary production file:
`java/org/l2jmobius/gameserver/phantoms/raid/PhantomRaidRecruitmentService.java`

`PhantomRaidModel.CandidateAssessment.partyMemberCount <= 9` is correct and must
not be relaxed.

## Required behavior

When `currentForce(candidate)` is a multi-Party CommandChannel, never use the
whole CC member list as one candidate Party. Resolve the candidate's unique own
PartySnapshot, keep only its members as CandidateAssessment evidence, and use
that exact Party leader for leader validation.

- exact leader in a >=10-member CC => `NOT_STANDALONE_PARTY`, no exception;
- non-leader in a >=10-member CC => `NOT_EXACT_PARTY_LEADER`, no exception;
- missing/ambiguous own Party => typed fail-closed result;
- standalone hard contribution/useful-members/excess/selection semantics remain
  unchanged;
- no Party splitting.

## Hard out of scope

No CP1 readiness semantic changes; no CP2 lifecycle changes; no Goal017
production changes unless a tiny compile-only signature blocker proves
necessary; no discovery, selection-order changes, consent changes,
navigation/combat/retreat/loot, persistence, scheduler/worker or other
chronicles.

## Verification budget

Use existing `PhantomRaidRecruitmentSuite` and add focused regression evidence:
1. large-CC exact leader typed reject with own-Party <=9 evidence;
2. large-CC non-leader typed reject with own-Party <=9 evidence;
3. one existing standalone scoring/selection assertion remains green.

Authorized: one `phantom-raid-recruitment-test`, one affected rerun only if the
fix itself fails, one `ant jar`, `git diff --check`, exact scope/UTF-8 checks.

Forbidden: CP3 14/14 aggregate, CP1 regression, CP2 gates, broad Goal017,
plain `ant verify`, Goal025 aggregate, all-Phantom, stress loops.

Status after delivery: CP3 remains CHANGES_REQUIRED pending 026B independent
review; 026B = IMPLEMENTED_PENDING_INDEPENDENT_REVIEW; Goal026 overall
IN_PROGRESS; CP4+ NOT_STARTED.

First context compaction = STOP new discovery and deliver safe coherent result.

Ordinary commit exact subject `fix(phantoms): bound raid candidate party evidence` and ordinary push
`origin feature/phantom-world`, even for PARTIAL/BLOCKED. No history rewrite.

Final report: branch, parent, commit, remote HEAD, subject, verdict, exact
R026B-01 evidence, focused test, jar, changed production files, unfinished
findings, `occurred_context_compaction: yes|no`.

Success token: `GOAL_026B_RAID_CANDIDATE_EVIDENCE_FIXED_PENDING_INDEPENDENT_REVIEW`
