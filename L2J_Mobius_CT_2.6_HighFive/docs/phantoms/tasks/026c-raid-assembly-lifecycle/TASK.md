# Goal 026C — harden raid assembly lifecycle

Branch: `feature/phantom-world`
Required parent: `867ff96e1b19c4cd0435cb51bfd7c31d14cae762`
Required subject: `fix(phantoms): harden raid assembly lifecycle`
Seed: `26002642`
Target: `IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`

Read PRIOR_INDEPENDENT_REVIEW.md. Fix only R026C-01/02/03 plus the named status
docs. Do not reopen accepted CP4 architecture.

## Clock correction

Refactor PhantomRaidAssemblyService to receive two clocks:
1. wall epoch milliseconds;
2. monotonic logical nanoseconds.

Wall clock is used only for leader/participant deadline validation, assembly
expiry and READY completion timestamps.

Logical clock is used only for PhantomPartyRouteCoordinator request/advance and
its navigation/external-action deadlines.

Production PhantomSystem must pass:
- System::currentTimeMillis
- System::nanoTime

For route creation:
- remainingMillis = goal.deadlineEpochMillis - wallNow;
- require remainingMillis > 0;
- convert duration to nanos;
- logicalDeadline = logicalNow + duration using checked/safe arithmetic;
- fail closed on overflow/invalid duration;
- use logicalNow/logicalDeadline consistently for request and subsequent
  advance.

Do not alter NavigationService, PhantomNavigationRequest or PhantomGoal clock
contracts.

## Terminal lifecycle

Separate live and terminal assembly state.

- max64 applies to live assemblies only;
- every terminal transition performs owned pending-invite/route cleanup, removes
  the exact live Assembly, and records bounded terminal state;
- terminal history maximum <=256;
- exact same terminal AssemblyIdentity returns its prior terminal result
  idempotently rather than creating a new live Assembly;
- a newer goal revision is not shadowed;
- stale cancel cannot affect a new revision;
- READY receipt survives live-state release and remains available through
  readyReceipt(leaderProfileId);
- no unbounded references to terminal Assembly route/pending structures.

Do not add DB/schema/config.

## Late participation

participation(profileId, goalId, revision) may inspect exact live state and
bounded exact terminal/READY history.

- participant that canonically joined the matching raid force before
  READY_AT_STAGING must still return JOINED afterwards;
- Decision handler then COMPLETE_GOAL;
- same-content but unrelated profile does not return JOINED;
- mismatched content does not return JOINED;
- expired participation remains EXPIRED;
- raid.participate never creates an assembly.

Prefer exact AssemblyIdentity + candidate membership + current canonical force
or READY receipt final force. No global scan or profile-global inference.

## Preserve

Do not change CP1 readiness, CP3 scoring, MPCC semantics, one-invite-per-advance,
Phantom/REAL consent policy, staging priority/radii, structural hash semantics,
per-Party routing, REAL movement prohibition, READY gate, raid authority,
Decision names, or combat/entry/retreat/loot scope.

## Focused tests

1. Divergent clock domains:
   - wall NOW around 1_000_000 ms;
   - logical NOW much larger (e.g. 9_000_000_000_000 ns);
   - NavigationService uses logical clock;
   - future wall goal gathers without immediate DEADLINE_EXPIRED;
   - truly expired wall goal still expires.
   This test must fail on reviewed CP4 source.

2. Terminal capacity:
   - terminalize 64 distinct leader assemblies cheaply;
   - activeAssemblies == 0 afterwards;
   - 65th live leader is admitted;
   - terminal history remains <= bound;
   - exact terminal identity is idempotent;
   - newer revision starts fresh.

3. Late participation:
   - candidate joins matching force;
   - leader reaches READY_AT_STAGING;
   - participant handler runs afterwards -> COMPLETE_GOAL;
   - negative unrelated candidate/content cases.

## Documentation

Update:
- PHANTOM_DEVELOPMENT_MASTER_PLAN.md
- docs/PHANTOM_BOTS_ROADMAP.md

Truth after delivery:
CP4 = CHANGES_REQUIRED pending 026C independent review;
026C = IMPLEMENTED_PENDING_INDEPENDENT_REVIEW;
CP5 = NOT_STARTED.

## Verification budget

Read only this package, PhantomRaidAssemblyService, PhantomRaidDecision,
production raid wiring in PhantomSystem, relevant CP4 test fixture and the
NavigationRequest/NavigationService clock contract.

Authorized:
- one compile after coherent fix;
- focused CP4 gathering/staging with clock regression;
- focused CP4 Decision/lifecycle with capacity + late participant;
- assembly/consent only if shared API/fixture change requires it;
- one final 026C aggregate of affected CP4 suites;
- one ant jar;
- diff/scope/strict UTF-8/mojibake/escaped-Cyrillic.

Forbidden:
- CP2 lifecycle;
- raid-authority target;
- CP3/026B aggregate;
- broad Goal017;
- plain ant verify;
- Goal025 aggregate;
- all-Phantom;
- stress loops.

No test-after-every-edit. First automatic context compaction = STOP new
discovery, mandatory focused gates, safe commit/push/handoff.

## Delivery

Ordinary commit exact subject:
`fix(phantoms): harden raid assembly lifecycle`

Ordinary push:
`git push origin feature/phantom-world`

Push safe result even PARTIAL/BLOCKED.
No amend/rebase/squash/reset/force-push.

Final report: branch, parent, SHA, remote HEAD, subject, verdict, clock bridge,
production clocks, live/terminal counts, 64->65 evidence, idempotent terminal +
new revision evidence, late participation evidence, docs closure, exact tests,
unfinished findings, `occurred_context_compaction: yes|no`.

Success token:
`GOAL_026C_RAID_ASSEMBLY_LIFECYCLE_FIXED_PENDING_INDEPENDENT_REVIEW`
