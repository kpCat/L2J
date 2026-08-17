# Goal 026 Checkpoint 3 independent review — corrective handoff

Reviewed remote commit: `c9d2d429b1a7655d36676f8f8496de53d9cff11d`

Independent verdict: `Goal026 Checkpoint 3 = CHANGES_REQUIRED`.

Only one production blocker is authorized.

## Accepted CP3 portions

Independent source review accepted fresh CP1 readiness ownership, exact
`intrinsic && learned && readyNow` capability truth, bounded 16-candidate input,
deterministic selection, one CP2 invite call site, no candidate #2 fallback,
no production `respondCommandChannel`, dynamic `invite != membership/readiness`,
and UTF-8 closure of the CP2 report. Final `ant jar` plus focused targets make
the earlier native JDK crash an environment/toolchain flake, not a source
blocker.

## R026B-01 — large existing CommandChannel candidate throws instead of typed reject

`PhantomRaidRecruitmentService.assessCandidate(...)` calls
`_party.currentForce(candidate)`, then copies **all**
`candidateForce.members()` into `members`, and only afterwards checks whether
the candidate is already in a CommandChannel / the force contains more than one
Party.

Goal017 `currentForce(candidate)` returns the whole current CommandChannel force,
which is bounded up to 144 members. But `CandidateAssessment` correctly models
one candidate Party and enforces `partyMemberCount <= 9`.

Therefore an exact candidate already in a CommandChannel with 10+ total members
can throw `IllegalArgumentException` while constructing the rejected
`CandidateAssessment`, instead of returning typed `NOT_STANDALONE_PARTY`.
A non-leader candidate in a large CommandChannel has the same shape for
`NOT_EXACT_PARTY_LEADER`.

### Required correction

- derive the candidate's unique exact own `PartySnapshot` from
  `CurrentForceSnapshot.parties()` by membership;
- use only that Party's <=9 members as CandidateAssessment evidence;
- compare exact leadership to that PartySnapshot leader;
- large-CC exact leader => typed `NOT_STANDALONE_PARTY`, no exception;
- large-CC non-leader => typed `NOT_EXACT_PARTY_LEADER`, no exception;
- ambiguous/missing own Party => fail closed typed unavailable/inconsistent
  evidence, not fabricated state and not exception;
- standalone contribution uses only exact own-Party member snapshots;
- keep CandidateAssessment <=9 invariant unchanged.

No other CP3 production change is authorized.
