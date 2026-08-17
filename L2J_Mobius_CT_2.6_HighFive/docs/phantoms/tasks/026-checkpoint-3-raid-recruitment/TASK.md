# Goal 026 Checkpoint 3 — raid force composition and bounded recruitment

## Identity

Branch: `feature/phantom-world`
Required parent: `bbd29495a19a322c0629509c85c31fe508ae8d07`
Required commit subject: `feat(phantoms): add raid recruitment planning`
Seed: `26002631`
Target verdict: `IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`

Goal026 overall remains IN_PROGRESS.

## User-visible result

Given an exact current Phantom Party/CommandChannel leader, an exact RAID/EPIC
content ID, and a caller-supplied bounded list of already-known exact candidate
Party leaders, the server can:

- explain current member/capability deficits;
- inspect each candidate only through Goal017 canonical facts;
- deterministically choose a candidate Party that really reduces those deficits
  without exceeding authoritative group size/bounds;
- issue at most one canonical MPCC invite through accepted CP2;
- return the exact invitation identity/status for later target-side consent.

An invited Party is never counted as joined until canonical CommandChannel
membership actually changes and CP1 readiness is reassessed.

## Architecture

### Raid-domain service

Add a small raid service such as `PhantomRaidRecruitmentService` plus bounded
typed records/enums in `PhantomRaidModel` or one small dedicated model.

Do not create a second generic Party coordinator.

Suggested concepts: RecruitmentStatus, CapabilityDeficit,
CandidateAssessment, RecruitmentPlan, RecruitmentAttempt. Exact names may vary.

### Inputs and bounds

Planning/execution accepts:

- exact leader `MemberRef actor`;
- exact `contentId`;
- `List<MemberRef> candidatePartyLeaders`.

Candidate list maximum: 16 leaders.

Nulls, duplicate stable identities and over-limit input fail closed. Input order
must not affect selection. Actor/current-force members are not candidates.

No hidden candidate expansion or discovery.

### Fresh current-force authority

Start from fresh `PhantomRaidReadinessService.assess(actor, contentId)`.

No mutation when:

- target UNKNOWN or UNAVAILABLE;
- force unavailable/inconsistent/over bound;
- actor lacks current invitation authority:
  - standalone: exact Party leader;
  - existing CC: exact CommandChannel leader;
- current force already GROUP_READY;
- current force is GROUP_ABSENT.

CP3 does not form an ordinary Party.

### Exact deficits

Member deficit:

`max(0, recommendedMinParty - currentMemberCount)`.

For every REQUIRED capability:

`max(0, minimumCount - current satisfyingMembers)`.

Optional requirements are not hard deficits.

Capability satisfaction MUST remain the accepted Goal026A predicate:

- exact capability key;
- rank >= required minimum rank;
- intrinsic;
- learned;
- readyNow.

Do not fork subtly different readiness semantics. If needed, expose/reuse a
package-private shared helper from the existing readiness service without
changing behavior.

### Candidate Party observation

Each caller-supplied candidate is eligible only if:

- exact MemberRef is currently observable;
- it is the exact current Party leader;
- it belongs to a real Party;
- that Party is standalone, not already in any CommandChannel;
- exactly one Party is represented by the candidate observation;
- member snapshots are exact and within existing bounds;
- it is not actor/current force;
- adding the WHOLE Party does not exceed content recommendedMaxParty;
- adding it does not exceed PhantomPartyBackend.MAX_FORCE_MEMBERS.

Never split a Party.

Unavailable/stale evidence is a typed rejected candidate, not guessed from
profile state. Do not auto-materialize background candidates merely to inspect
them.

### Candidate contribution

For each eligible Party compute:

- exact Party member count;
- units by which it reduces each CURRENT hard capability deficit;
- useful contribution to the CURRENT member deficit.

A Party reducing neither hard capability deficits nor member deficit is not
recruitable in CP3.

### Deterministic selection

Priority:

1. greater total reduction of current HARD capability deficits;
2. greater useful contribution to member deficit;
3. fewer excess members after useful contribution;
4. candidate leader stableKey ascending.

Document exact arithmetic. No RNG. Reordered input must yield same winner and
same evidence hash.

Do not claim global optimality. Replan after every actual canonical membership
change.

### One canonical outbound invite

Provide an operation such as `recruitNext(...)` that:

1. recomputes a fresh plan;
2. selects at most one candidate;
3. calls only `PhantomPartyBackend.inviteCommandChannel(actor,
   candidateLeader)`;
4. returns typed attempt + exact CP2 invitation identity if delivered.

Never invite candidate #2 in the same call if candidate #1 is canonically
rejected because state drifted.

### Consent boundary

CP3 is OUTBOUND recruitment only.

REAL candidate:
- ordinary CP2/High Five prompt is preserved;
- CP3 fabricates no response.

PHANTOM candidate:
- CP3 never auto-accepts;
- pending identity is returned/observable for a later target-side policy.

Production CP3 MUST NOT call `respondCommandChannel`.

### No free readiness

A plan or delivered invitation cannot alter RaidReadiness.

Only after an independently executed exact target-side ACCEPT changes the
canonical CommandChannel may a subsequent fresh CP1 assessment observe the
candidate members/capabilities.

This must be a dynamic test.

### Deterministic evidence

Plan carries bounded evidence including:

- content ID;
- target/readiness status;
- current force identity;
- current member count;
- authoritative min/max;
- exact hard deficits;
- ordered candidate assessments;
- selected candidate if any;
- deterministic evidence hash over these facts.

No DB persistence in CP3.

## Documentation closure

As part of this substantive checkpoint:

1. rewrite
   `docs/phantoms/reports/026-checkpoint-2-command-channel-lifecycle.md`
   as valid UTF-8, preserving factual CP2 results/limitations;
2. update `docs/PHANTOM_BOTS_ROADMAP.md`;
3. update `PHANTOM_DEVELOPMENT_MASTER_PLAN.md`.

Truth after CP3 delivery:

- Goal026 CP1 + Goal026A: ACCEPT;
- Goal026 CP2: ACCEPT at `bbd29495a19a322c0629509c85c31fe508ae8d07`;
- Goal026 CP3: IMPLEMENTED_PENDING_INDEPENDENT_REVIEW;
- Goal026 overall: IN_PROGRESS;
- later checkpoints: NOT_STARTED.

No CP2 product change/rerun solely for docs.

## Hard out of scope

NO candidate discovery/global scans, World.getPlayers/profile population scan,
automatic MPCC acceptance, chat/relationship scoring, gathering/navigation,
raid entry/combat/retreat/loot, boss-specific Queen Ant/Zaken execution, raid
DB saga/persistence, scheduler wiring, new worker/thread/timer/Future,
clan/alliance strategy, other chronicles, or CP2 lifecycle redesign.

## Read budget

Read only this package, current four raid classes, needed Goal017
PartyBackend/MemberSnapshot records, CP2 API signatures, focused fixtures, and
at most one small Goal023 recruitment section if needed. Do not reopen accepted
Goals or reread the repository.

## Execution / stop rule

Implement one coherent production block before testing. Do not test after every
edit.

First automatic context compaction = STOP new discovery; finish only current
coherent block, mandatory focused gates, commit/push/handoff.

After task-defined gates are green, newly noticed nonfatal issues are
report-only and do not expand CP3.

## Verification budget

Authorized:

1. one compile/compile-tests after coherent production block;
2. focused CP3 recruitment suite;
3. directly affected CP1 readiness regression ONLY if readiness production
   helper/code is touched;
4. one final CP3 aggregate containing CP3 plus only that affected readiness
   target if applicable;
5. one `ant jar`;
6. `git diff --check`;
7. exact scope/UTF-8 checks.

Forbidden: plain `ant verify`, Goal025 aggregate, Goal026 CP1 aggregate,
Goal026 CP2 15-test aggregate, broad Goal017 unless its production code changes,
all-Phantom regression, stress loops, rerunning green product gates for docs.

## Delivery

Ordinary commit exact subject:

`feat(phantoms): add raid recruitment planning`

Ordinary push origin feature/phantom-world even for PARTIAL/BLOCKED safe result.

No amend/rebase/squash/reset/force-push.

Final report: branch, parent, commit, remote HEAD, subject, verdict,
current-force authority, exact deficit model, candidate bound/eligibility,
deterministic selection, exact CP2 invite result, proof invitation !=
membership/readiness, tests, docs/UTF-8 closure, unfinished findings and
`occurred_context_compaction: yes|no`.

Success token:

`GOAL_026_CHECKPOINT_3_RAID_RECRUITMENT_IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`
