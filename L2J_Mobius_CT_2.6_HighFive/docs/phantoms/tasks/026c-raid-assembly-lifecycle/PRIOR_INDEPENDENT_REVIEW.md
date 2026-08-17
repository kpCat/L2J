# Goal 026 CP4 independent review — corrective handoff

Reviewed remote commit:
`867ff96e1b19c4cd0435cb51bfd7c31d14cae762`

Independent verdict:
`Goal026 Checkpoint 4 = CHANGES_REQUIRED`

Accepted CP4 architecture:
- raid.prepare / raid.participate Decision wiring;
- bounded validSources + accepted CP3 recruitment;
- one MPCC invite per advance, consent on later advance;
- Phantom consent requires matching ACTIVE raid.participate;
- REAL consent remains manual;
- exact stale-safe MPCC cancel;
- structural-force drift replans;
- per-Party existing PartyRouteCoordinator routes <=9;
- mixed Party moves Phantom only, all-REAL is observation-only;
- staging authority priority and no teleport/hardcoded boss coordinates;
- READY_AT_STAGING requires physical staging + fresh CP1 GROUP_READY;
- no raid combat/entry/retreat/loot;
- production registration and shutdown ordering are correct.

Fix only the following findings.

## R026C-01 — mixed clock domains

Production constructs PhantomRaidAssemblyService with System::currentTimeMillis.
Assembly passes that wall-ms value and PhantomGoal.deadlineEpochMillis directly
to PhantomPartyRouteCoordinator request/advance.

That route owner creates PhantomNavigationRequest.submittedLogicalNanos and
deadlineLogicalNanos. Production PhantomNavigationService compares them against
its own System::nanoTime clock.

The CP4 test hid this by using the same synthetic NOW for assembly and
NavigationService.

Required:
- explicit wall epoch-millis clock for goal deadlines / READY timestamps;
- explicit monotonic logical-nanos clock for PartyRoute/Navigation/action
  deadlines;
- production wiring: System::currentTimeMillis + System::nanoTime;
- convert remaining positive wall duration to nanos and add to current logical
  nanos with overflow-safe fail-closed behavior;
- request and advance routes entirely in monotonic domain;
- do not change PhantomGoal or NavigationService clock contracts.

## R026C-02 — terminal Assemblies retain active capacity

READY_AT_STAGING/BLOCKED/EXPIRED/CANCELLED Assembly objects remain in `_active`.
Capacity uses `_active.size() >= 64`. After 64 distinct terminal leaders, a 65th
leader can be rejected although there are no live assemblies.

Required:
- `_active` contains only live/nonterminal Assembly state;
- terminal transition removes the exact live Assembly;
- bounded terminal history <=256 stores immutable terminal outcome;
- same exact terminal goal identity remains idempotent (must not recreate while
  Decision has not yet persisted terminal status);
- newer goal revision starts fresh;
- stale cancel cannot affect newer revision;
- snapshot.activeAssemblies is live count;
- terminal history bounded;
- READY receipt remains queryable after live state is released.

No DB persistence.

## R026C-03 — late raid.participate after READY fails

`participation()` skips terminal Assemblies. A candidate may already have joined
the canonical force, then the leader reaches READY_AT_STAGING, and only later
the participant Decision step runs. It currently returns IMPOSSIBLE instead of
JOINED.

Required:
- bounded terminal/READY evidence keeps exact matching participation observable;
- joined participant returns JOINED even after leader READY terminalization;
- PhantomRaidDecision maps it to COMPLETE_GOAL;
- unrelated candidate/content does not inherit JOINED;
- expired goal stays EXPIRED;
- raid.participate never creates an assembly.

## Documentation debt

CP4 updated PHANTOM_DEVELOPMENT_MASTER_PLAN.md but did not update
docs/PHANTOM_BOTS_ROADMAP.md. The roadmap still says CP3 pending and CP4 not
started.

026C updates both docs to:
- CP1+026A ACCEPT
- CP2 ACCEPT
- CP3+026B ACCEPT
- CP4 CHANGES_REQUIRED pending 026C independent review
- 026C IMPLEMENTED_PENDING_INDEPENDENT_REVIEW
- Goal026 overall IN_PROGRESS
- CP5 NOT_STARTED
