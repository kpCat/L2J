# GOAL 007 — Shared scheduler and activity state machine

## 1. Identifier

- **Goal ID:** `007-shared-activity-scheduler`
- **Roadmap stage:** II — Scheduler, goals, navigation and authoritative knowledge
- **Branch:** `feature/phantom-world`
- **Accepted baseline:** `82a03342e52ff4b6c023b8ea224da8b1c2f6657f`
- **Parent:** `c2f5599ef59cabb1eeff1f3d467f57219d5a1f5f`
- **Repository root:** `C:\Users\endim\L2J_Mobius\`
- **Only module:** `C:\Users\endim\L2J_Mobius\L2J_Mobius_CT_2.6_HighFive\`
- **Test DB:** `l2jmobiush5_phantom_test`
- **Production DB:** `l2jmobiush5` — never use during Codex execution
- **Seed:** `20260725001`
- **Codex model:** Sol
- **Effort:** Very High

## 2. Accepted gates

```text
Stage I: COMPLETE after independent Goal 006B review
Task 001 / 001A: ACCEPT
Task 002 / 002A: ACCEPT
Task 003: ACCEPT
Task 004 / 004A / 004B: ACCEPT
Goal 005: ACCEPT
Goal 006 / 006A / 006B: ACCEPT
ADR 0001: Accepted
Goal 007: ALLOWED
Goal 008: NOT_STARTED
Goal 009: NOT_STARTED
```

Goal 006B immutable handoff:

```text
Commit: 82a03342e52ff4b6c023b8ea224da8b1c2f6657f
Parent: c2f5599ef59cabb1eeff1f3d467f57219d5a1f5f
Subject: fix(phantoms): coordinate server shutdown handoff
Push/remote: exact
Shutdown-handoff suite: 4/4 ×3
Production materialization suite: 19/19 ×3
Verifier: 71/71 ×2
Verifier SHA-256:
9719AC3E485565D08C357165CCCE0FA790973652CAD7B1C0D6659A2ADE53123F
Production DB: no access/mutation
Independent verdict: ACCEPT
```

One nonblocking operational wording note remains: the legacy aggregate message
`All players disconnected and saved` may execute after managed Phantom actors
were deliberately excluded. Do not touch `Shutdown.java` in Goal 007; record
this for Goal 028 operations/observability.

## 3. User-visible result

After Goal 007 the enabled Phantom subsystem has a real shared activity runtime
that can explicitly register Phantom profile IDs, accept bounded relevance
signals and maintain deterministic effective simulation detail:

```text
ACTIVE
NEARBY_PERCEPTIBLE
WARM
BACKGROUND
SLEEPING
```

It provides:

- no per-profile thread, executor, scheduled future or timer;
- one shared scheduler pulse on the existing `ThreadPool`;
- bounded/coalesced ready work;
- one bounded due entry per registered profile;
- deterministic promotion/demotion with hysteresis;
- fair profile processing under item and wall-clock budgets;
- conservative materialization for `ACTIVE` and `NEARBY_PERCEPTIBLE`;
- typed due-work delivery for future Goal 008;
- explicit backpressure and overload degradation;
- safe stop-before-materialization-drain integration with `PhantomSystem`.

Production startup registers **zero** profiles and materializes nobody. Goal 016
will own population/profile registration. Goal 010 will own topology-based
signal providers. Goal 008 will own decision/plan work.

## 4. Hard architectural boundary

Goal 007 defines only:

- runtime profile registration;
- activity states;
- abstract relevance signal contract;
- scheduler queues/due ordering;
- transition orchestration;
- typed work cadence;
- materialization bridge;
- overload policy;
- scheduler metrics/snapshots;
- scheduler lifecycle inside `PhantomSystem`.

It does **not** define:

- goals, Utility AI, plans or action candidates;
- topology, rooms, distance, geodata or perception calculations;
- population selection or schedules;
- background farming/economy state;
- combat, navigation or class capabilities;
- personality, memory or Semantic Pack;
- automatic database scan/registration;
- persistent activity state or new DB schema.

## 5. Mandatory reading

Read fully before editing:

1. `docs/PHANTOM_BOTS_ROADMAP.md`;
2. `PHANTOM_DEVELOPMENT_MASTER_PLAN.md`;
3. `Agents.md`;
4. workflow/package/report standards;
5. Goal 006/006A/006B packages, contracts, reports and reviews;
6. Goal 005 profile persistence contract;
7. current:
   - `PhantomScheduler.java`;
   - `PhantomSystem.java`;
   - `PhantomMetrics.java`;
   - `PhantomDiagnosticTrace.java`;
   - `PhantomPlayersConfig.java` and `PhantomPlayers.ini`;
   - `PhantomMaterializationService.java`;
   - `PhantomMaterializedPlayer.java`;
   - `Shutdown.java`;
   - all Phantom test suites and `build.xml`;
8. all files in this package.

Do not use another chronicle.

## 6. Initial Git audit

```bat
git rev-parse --show-toplevel
git status --short --branch
git remote -v
git fetch origin --prune
git rev-parse HEAD
git rev-parse origin/feature/phantom-world
git show --stat --oneline 82a03342e52ff4b6c023b8ea224da8b1c2f6657f
git diff --name-status c2f5599ef59cabb1eeff1f3d467f57219d5a1f5f..82a03342e52ff4b6c023b8ea224da8b1c2f6657f
```

Expected:

```text
HEAD == origin/feature/phantom-world == 82a03342...
```

The extracted Goal 007 package is expected untracked scope. Preserve and
exclude unrelated `docs/agent-tasks/**`.

Return `BLOCKED_BASELINE_DRIFT` for unreviewed production/schema drift.

## 7. Close Goal 006B and Stage I

### 7.1. Goal 006B report

Update:

```text
docs/phantoms/reports/006b-server-shutdown-handoff.md
```

Add the exact immutable handoff from section 2 and:

```text
Independent review: ACCEPT
Goal 006 overall: ACCEPT
Stage I: COMPLETE
Goal 007: ALLOWED
```

### 7.2. Independent review

Create:

```text
docs/phantoms/reviews/006b-server-shutdown-handoff-review.md
```

Verdict:

```text
Goal 006B: ACCEPT
Goal 006 overall: ACCEPT
Goal 006A: ACCEPT
Revert: NOT_REQUIRED
Stage I: COMPLETE
Goal 007: ALLOWED
```

Document that the classifier requires configured service ownership, headless
output and PHANTOM identity, that two server-level attempts precede ThreadPool
stop, and that persistent failure remains fail-closed with aggregate SEVERE.

### 7.3. Roadmap progress

Update progress facts only:

- accepted baseline becomes `82a03342...`;
- Goal 006/006A/006B become `ACCEPT`;
- Stage I becomes `COMPLETE`;
- Goal 007 becomes `IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`;
- Goal 008 and Goal 009 remain `NOT_STARTED / BLOCKED`;
- DAG gains explicit closure path
  `006 → 006A → 006B → 007`;
- no future GOAL architecture or user requirements change.

No roadmap version bump.

## 8. Fixed state model

Create package:

```text
java/org/l2jmobius/gameserver/phantoms/activity/
```

Required responsibility-equivalent types:

```text
PhantomActivityState
PhantomRelevanceSignal
PhantomActivityTransitionStatus
PhantomActivitySnapshot
PhantomActivityWorkItem
PhantomActivityWorkSink
PhantomActivityMaterializationPort
PhantomSchedulerPolicy
```

`PhantomScheduler` remains the single scheduler owner and uses these contracts.

### 8.1. Activity state order

Highest detail first:

```text
ACTIVE
NEARBY_PERCEPTIBLE
WARM
BACKGROUND
SLEEPING
```

Required properties:

- fixed stable wire/code value per state, not ordinal persistence;
- `ACTIVE` and `NEARBY_PERCEPTIBLE` require a canonical materialized Player;
- `WARM`, `BACKGROUND` and `SLEEPING` require no materialized Player;
- `SLEEPING` has no periodic due work;
- overload never changes effective state by itself.

`NEARBY_PERCEPTIBLE` uses canonical materialization as a conservative bridge in
Goal 007 because no regional actor representation exists yet. Capacity failure
leaves the requested transition pending/blocked; it must not pretend the profile
is perceptible while represented only as background statistics.

### 8.2. Relevance signal

Immutable input record:

```java
String sourceKey
long sequence
PhantomActivityState requiredState
long ttlMillis
```

Validation:

```text
sourceKey: ^[a-z][a-z0-9_.-]{0,63}$
sequence: >= 0
ttlMillis: 1..86_400_000
requiredState: non-null
```

The signal contains no position, room, distance, Player, NPC or topology data.
Goal 010 providers will translate their observations into this contract.

Signal policy:

- identity is `(profileId, sourceKey)`;
- sequence must strictly increase for replacement or withdrawal;
- stale/equal sequence is rejected without mutation;
- maximum 16 active source keys per profile;
- updating an existing source is allowed at the limit;
- signal expiration uses scheduler monotonic time stamped at acceptance;
- `withdrawSignal(profileId, sourceKey, sequence)` is explicit and stale-safe;
- effective requested state is the highest-detail unexpired requirement;
- no active signal means requested `SLEEPING`.

## 9. Scheduler policy

`PhantomSchedulerPolicy` is immutable and injectable in tests.

Production defaults:

```text
max signal sources/profile: 16
max signal TTL: 86_400_000 ms
promotion: immediate
 demotion grace: 2_000 ms
clean transition retry base: 1_000 ms
clean transition retry max: 30_000 ms
ACTIVE cadence: 100 ms
NEARBY_PERCEPTIBLE cadence: 250 ms
WARM cadence: 1_000 ms
BACKGROUND cadence: 10_000 ms
SLEEPING cadence: none
pulse wall budget: min(75% pulse interval, 50 ms), at least 1 ms
```

The values are technical scheduler defaults, not population/game-content
numbers. Tests must inject smaller deterministic values. Goal 028 may expose
additional operational tuning after scale evidence.

## 10. Config

Add exactly three scheduler safety settings:

```text
MaxScheduledPhantomProfiles = 10000
PhantomSchedulerPulseMillis = 100
PhantomSchedulerProfilesPerPulse = 128
```

They are capacity/budget guards, not population targets.

Enabled validation:

```text
MaxScheduledPhantomProfiles: 1..1_000_000
PhantomSchedulerPulseMillis: 10..1000
PhantomSchedulerProfilesPerPulse: 1..10000
MaxScheduledPhantomProfiles >= MaxMaterializedPhantoms
```

Missing, blank, signed, malformed or out-of-range enabled values fail the entire
Phantom settings closed to disabled.

Disabled effective settings:

```text
all materialization/scheduler capacities and intervals = 0
```

Defaults remain disabled. Do not add state cadence config in Goal 007.

## 11. Scheduler lifecycle and APIs

Replace the inert arbitrary `Runnable` queue in `PhantomScheduler`.

Forbidden API:

```java
offer(Runnable)
```

Required behaviorally equivalent API:

```java
boolean start()
RegistrationResult register(long profileId)
UnregisterResult unregister(long profileId)
SignalResult submitSignal(long profileId, PhantomRelevanceSignal signal)
SignalResult withdrawSignal(long profileId, String sourceKey, long sequence)
TransitionResult retryTransition(long profileId)
Optional<PhantomActivitySnapshot> find(long profileId)
List<PhantomActivitySnapshot> list()
BeginStopResult beginStop()
boolean finishStop()
SchedulerSnapshot snapshot()
```

No automatic profile scan or registration.

### 11.1. Registration

- positive profile ID only;
- one runtime slot per profile;
- bounded by `MaxScheduledPhantomProfiles`;
- initial effective/requested state `SLEEPING`;
- no queue/due work until a signal arrives;
- duplicate registration is idempotent and distinguishable;
- scheduler does not query DB on registration;
- actual profile existence is validated when the materialization service is
  called; future Goal 016 registers known profiles from persistence.

### 11.2. Data structures

Use bounded structures:

```text
ConcurrentHashMap<profileId, Slot> registered slots
ArrayBlockingQueue<profileId> ready queue
one ordered due set/tree with at most one due entry/profile
one bounded signal map/profile, max 16
one scheduled pulse future total
```

No stale due-entry accumulation. Rescheduling removes/replaces the prior due
entry. Queue capacity equals the configured maximum scheduled profiles.

Each slot has at most:

- current/effective state;
- requested state;
- transition status;
- signal generation/source map;
- enqueue/coalescing flag;
- one due key;
- retry/backoff state;
- tick sequence;
- last transition/reason summary.

No `Player`, Future, Thread, Executor or unbounded history is stored in a slot.

### 11.3. Queue acceptance

For a slot not already enqueued:

- reserve ready queue capacity before mutating the accepted signal;
- queue full returns explicit `BACKPRESSURE` and leaves signal/state unchanged.

For an already enqueued slot:

- a valid newer signal may update/coalesce without another queue entry;
- record `COALESCED`.

Due work that cannot enter a full ready queue remains due and is retried by a
later pulse. It is never silently dropped.

## 12. Deterministic state machine

Detailed transitions: `ACTIVITY_STATE_MACHINE.md`.

### 12.1. Signal aggregation

On profile processing:

1. remove expired signals;
2. compute highest-detail required state;
3. promotions are eligible immediately;
4. demotions become eligible only after the fixed demotion grace since the last
   higher-detail requirement disappeared;
5. signal updates during a transition are applied by generation check before
   committing the result.

### 12.2. Materialization bridge

`PhantomActivityMaterializationPort` is a typed interface; it does not expose
arbitrary callbacks or a `Player`.

Production adapter wraps only:

```text
PhantomMaterializationService.materialize(profileId)
PhantomMaterializationService.dematerialize(profileId)
PhantomMaterializationService.retryCleanup(profileId)
PhantomMaterializationService.find(profileId)
```

Rules:

- transition from non-materialized state to ACTIVE/NEARBY calls materialize;
- transition ACTIVE↔NEARBY changes only scheduler state;
- transition ACTIVE/NEARBY to WARM/BACKGROUND/SLEEPING calls dematerialize;
- transition commits only after service success;
- clean rejection leaves current state unchanged and schedules bounded
  exponential retry;
- retained materialization/cleanup failure sets
  `RETAINED_FAILURE_REQUIRES_EXPLICIT_RETRY` and schedules no automatic retry;
- `retryTransition(profileId)` is required for retained failure;
- while materialization is pending/failed, scheduler does not report ACTIVE or
  NEARBY effective state falsely;
- service calls occur outside global scheduler locks.

No profile/character is created.

### 12.3. Transition status

At minimum:

```text
STABLE
PROMOTION_PENDING
DEMOTION_GRACE
DEMOTION_PENDING
TRANSIENTLY_BLOCKED
RETAINED_FAILURE_REQUIRES_EXPLICIT_RETRY
UNREGISTER_PENDING
```

Snapshots include a stable coarse last result category but no exception object,
stack trace or dynamic log history.

## 13. Shared pulse and fairness

Production scheduler owns exactly one recurring `ScheduledFuture` on the
existing `ThreadPool`.

Each pulse:

1. moves due profiles to ready queue in `(dueNanos, fairnessSequence, profileId)`
   order while capacity permits;
2. processes at most configured profiles-per-pulse;
3. stops after the policy wall-clock budget is exceeded;
4. processes each profile at most once per pulse;
5. reschedules exactly one next due entry;
6. catches failure per profile and continues;
7. never overlaps the same periodic pulse with itself.

External work and materialization calls run outside global locks. A slow call may
overrun one pulse and is measured, but no second per-profile task is created.

Fairness gate:

```text
for profiles due at the same logical time,
no profile receives its next cadence before every still-registered peer in that
cohort has received one processing opportunity, subject only to removal or a
new immediate signal.
```

## 14. Typed due work

`PhantomActivityWorkSink` receives immutable `PhantomActivityWorkItem`:

```text
profileId
effectiveState
tickSequence
logicalNowNanos
overloadLevel
```

No Player, goal, target, position, inventory or arbitrary Runnable is included.

Production Goal 007 installs a no-op sink. Goal 008 will replace it with the
Utility AI/plan executor integration.

Work cadence:

- ACTIVE/NEARBY/WARM/BACKGROUND according to policy;
- SLEEPING never due;
- sink exception is isolated, counted and next normal cadence remains;
- no action is executed by Goal 007.

## 15. Overload degradation

Define fixed levels from ready-queue occupancy:

```text
NORMAL: < 50%
ELEVATED: >= 50%
HIGH: >= 75%
CRITICAL: >= 90%
```

Behavior:

- ACTIVE and NEARBY cadence never degrades;
- WARM cadence multiplier: 1 / 2 / 4 / 8;
- BACKGROUND cadence multiplier: 1 / 2 / 4 / 8;
- SLEEPING remains unscheduled;
- overload never demotes state;
- overload never drops accepted signal state;
- saturation returns explicit backpressure for a not-yet-enqueued signal;
- multipliers are overflow-safe and bounded.

## 16. Scheduler stop and PhantomSystem integration

### 16.1. Startup

Configured enabled startup order becomes:

```text
repository open/schema validation
→ materialization service start
→ scheduler construct with materialization port and no-op work sink
→ scheduler start / one recurring future
→ PhantomSystem RUNNING
```

No profiles are registered and no materialization occurs at startup.

Disabled path:

- no repository;
- no materialization service;
- no scheduler;
- no future/queue/profile/DB query.

### 16.2. Shutdown

Because Goal 007 scheduler becomes active, `PhantomSystem.shutdown()` must:

```text
scheduler.beginStop()
  reject new register/signal/action-work
  cancel the one pulse future
  retain slot snapshots until service drain finishes
→ materialization service shutdown/retry
→ scheduler.finishStop() only after service STOPPED
→ PhantomSystem STOPPED
```

If service drain fails:

- system remains FAILED;
- scheduler remains STOPPING with no pulse;
- registered state is retained in memory;
- second explicit server-level shutdown retries service;
- `finishStop()` clears slots only after service STOPPED.

The accepted Goal 006B two-phase `Shutdown.java` ordering and managed classifier
must remain unchanged.

### 16.3. Access

Expose one package-private configured scheduler accessor for future Phantom
components. Do not expose mutable maps or public admin API in Goal 007.

## 17. Metrics and diagnostics

Extend fixed aggregate metrics without dynamic labels:

- registered current/peak;
- registration accepted/rejected;
- signal accepted/coalesced/stale/rejected/expired;
- pulses started/completed/overrun;
- ready enqueued/backpressure;
- due moved/deferred;
- work delivered/failures;
- promotions/demotions;
- materialization transition success/transient block/retained failure;
- explicit retries;
- scheduler begin-stop/finish-stop;
- overload level transitions/current peak.

State counts may use a fixed-size `AtomicLongArray` indexed by an explicit stable
state code. No map keyed by profile/source/reason.

Trace remains bounded/sampled and records only short events with profile ID.
No per-pulse or per-profile INFO/WARNING logs.

## 18. Testability seams

Use package-private injectable contracts:

```text
monotonic clock
pulse driver
materialization port
work sink
scheduler policy
```

Production pulse driver uses existing `ThreadPool.scheduleAtFixedRate`.

Tests use a manual clock/pulse driver. Do not use sleeps for deterministic state
machine/fairness tests except bounded integration waits around actual ThreadPool
shutdown regressions.

No production hidden system property or config switch.

## 19. Automated suites

Create:

```text
test/java/org/l2jmobius/tests/phantoms/PhantomActivitySchedulerSuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomActivitySchedulerPerformanceSuite.java
```

Launcher modes:

```text
activity-scheduler
activity-scheduler-performance
```

Ant targets:

```text
phantom-activity-scheduler-test
phantom-activity-scheduler-performance-smoke
```

Both forked; deterministic core suite does not require DB. Materialization bridge
cases use the guarded test DB/headless environment.

## 20. Required tests

Full matrix: `TEST_CASES.md`.

Critical gates:

- config strictness and disabled zero-allocation;
- start with one recurring future and zero profiles;
- register capacity/idempotency/unregister;
- signal validation, stale withdrawal and 16-source bound;
- queue full rejects without mutation;
- coalescing has one queue entry;
- signal TTL and highest-detail aggregation;
- promotion immediate, demotion grace deterministic;
- ACTIVE/NEARBY conservative materialization;
- actual service bridge materialize/dematerialize;
- capacity transient block and bounded retry;
- retained cleanup failure requires explicit retry;
- state work cadences and no SLEEPING work;
- fair cohort processing under small pulse budget;
- overload multipliers and no state demotion;
- sink failure isolation;
- stop rejects input and cancels one future;
- failed service drain leaves scheduler STOPPING;
- second shutdown finishes scheduler only after service STOPPED;
- Goal 006B shutdown-handoff regression;
- 10,000 dormant profiles with zero per-profile future/due work;
- no production auto registration/materialization;
- no production DB access while disabled.

## 21. Performance smoke

Use pure/manual scheduler ports for scale:

1. register 10,000 SLEEPING profiles;
2. prove:
   - registered `10000`;
   - ready `0`;
   - due `0`;
   - scheduled future count `1` for production driver or `0` manual;
   - no Slot field assignable to Future/Thread/Executor;
3. submit a deterministic 10,000-profile WARM signal burst in bounded batches;
4. process with budget 128;
5. prove every profile gets one fair processing opportunity before any gets a
   second cadence;
6. queue/due/signal structures never exceed declared bounds;
7. apply CRITICAL overload and prove ACTIVE/NEARBY cadence unchanged while
   WARM/BACKGROUND cadence is multiplied by 8;
8. final stop leaves zero queue/due/registered residue.

This is a bounded structural/performance smoke, not a target population promise.

## 22. DB and persistence

No schema or migration change.

Goal 007 does not write activity state to profile components. Registered slots,
signals, deadlines and work cadence are process-local runtime scheduling state.
On restart:

```text
scheduler starts with zero registered profiles
materialization service starts with zero active actors
persistent profile rows remain unchanged
```

Goal 016 will own population/schedule registration. Goal 015 will own causal
background state/reconciliation.

Production DB is never used during Codex execution.

## 23. Exact scope

Allowed production/config:

```text
dist/game/config/Custom/PhantomPlayers.ini
java/org/l2jmobius/gameserver/config/custom/PhantomPlayersConfig.java
java/org/l2jmobius/gameserver/phantoms/PhantomScheduler.java
java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java
java/org/l2jmobius/gameserver/phantoms/PhantomMetrics.java
java/org/l2jmobius/gameserver/phantoms/PhantomDiagnosticTrace.java
java/org/l2jmobius/gameserver/phantoms/activity/**
```

Allowed build/tests:

```text
build.xml
test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java
test/java/org/l2jmobius/tests/phantoms/PhantomSkeletonSuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomActivitySchedulerSuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomActivitySchedulerPerformanceSuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomServerShutdownHandoffSuite.java
tools/phantoms/verify-task-007.ps1
```

The shutdown-handoff suite may change only as needed for the new scheduler
begin-stop/finish-stop lifecycle; its two-phase production ordering contract is
frozen.

Allowed documentation:

```text
docs/PHANTOM_BOTS_ROADMAP.md
docs/phantoms/architecture/ACTIVITY_SCHEDULER_CONTRACT.md
docs/phantoms/tasks/007-shared-activity-scheduler/**
docs/phantoms/reports/006b-server-shutdown-handoff.md
docs/phantoms/reports/007-shared-activity-scheduler.md
docs/phantoms/reviews/006b-server-shutdown-handoff-review.md
```

## 24. Hard out of scope

Forbidden:

- DB schema/migrations/profile component changes;
- `Player`, `World`, network, identity registry/recovery;
- materialization core/service behavior changes;
- `Shutdown.java` changes;
- automatic profile scan/registration/materialization;
- population/schedules;
- topology, rooms, distance, geodata, perception implementation;
- goals, Utility AI, plan executor;
- navigation, combat, economy, class behavior;
- personality/memory/Semantic Pack;
- arbitrary `Runnable` public queue;
- new executor/raw thread;
- per-profile Future/task/thread/timer;
- production DB execution;
- other chronicles;
- dependencies/CI;
- old verifier changes;
- mass formatting;
- amend/rebase/merge/force push.

## 25. Static verifier Goal 007

Create:

```text
tools/phantoms/verify-task-007.ps1
```

It must verify:

- base `82a03342...`;
- one ordinary commit and exact scope;
- Stage I/006B closure and roadmap progress-only edits;
- no schema/Goal 008/009 implementation;
- `Shutdown`, materialization service/core, identity and profile packages frozen;
- default config disabled and exact three scheduler keys;
- strict config ranges and maxScheduled >= maxMaterialized;
- no `offer(Runnable)` or arbitrary Runnable queue;
- exact five states and conservative materialization rule;
- immutable relevance signal validation;
- source/TTL bounds and stale sequence guard;
- bounded ready queue, one due entry/profile and source cap;
- no slot Future/Thread/Executor/Player;
- exactly one production recurring scheduler future;
- service calls outside global locks;
- action/goal/topology types absent;
- atomic signal acceptance/backpressure contract;
- demotion grace and overload no-demotion rule;
- typed work item without Player/domain fields;
- scheduler begin-stop before materialization service shutdown and finish-stop
  only after service STOPPED;
- Goal 006B `Shutdown.java` hash/source ordering unchanged;
- scale/fairness tests and 10,000 dormant evidence;
- UTF-8, mojibake, escaped Cyrillic;
- no credentials/binaries;
- verifier deterministic/read-only.

## 26. Commands

Pre-change:

```bat
ant verify
powershell -NoProfile -ExecutionPolicy Bypass -File tools\phantoms\verify-task-006b.ps1
```

Targeted:

```bat
ant compile-tests
ant phantom-activity-scheduler-test
ant phantom-activity-scheduler-performance-smoke
ant phantom-server-shutdown-handoff-test
ant phantom-production-materialization-test
ant phantom-headless-player-test
ant phantom-profile-persistence-test
ant phantom-db-test
ant test
ant phantom-skeleton-test
```

Run scheduler suite three independent times:

```bat
ant phantom-activity-scheduler-test
ant phantom-activity-scheduler-test
ant phantom-activity-scheduler-test
```

Run performance smoke twice and compare deterministic summaries:

```bat
ant phantom-activity-scheduler-performance-smoke
ant phantom-activity-scheduler-performance-smoke
```

Full:

```bat
ant verify
ant jar
powershell -NoProfile -ExecutionPolicy Bypass -File tools\phantoms\verify-task-007.ps1
git diff --check
git status --short --branch
```

Post-commit:

```bat
git rev-parse HEAD
git show --stat --oneline --decorate HEAD
git diff --check 82a03342e52ff4b6c023b8ea224da8b1c2f6657f...HEAD
ant verify
ant jar
powershell -NoProfile -ExecutionPolicy Bypass -File tools\phantoms\verify-task-007.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File tools\phantoms\verify-task-007.ps1
git push origin feature/phantom-world
git ls-remote --heads origin feature/phantom-world
git status --short --branch
```

Compare final verifier output byte-for-byte/SHA-256 outside the repository.

## 27. Report

Create:

```text
docs/phantoms/reports/007-shared-activity-scheduler.md
```

Required sections:

- Status/baseline;
- Goal 006B independent closure and Stage I complete;
- roadmap progress;
- config/defaults/disabled behavior;
- activity states and conservative NEARBY bridge;
- relevance signal/source/TTL contract;
- data structures and boundedness;
- queue acceptance/coalescing/backpressure;
- state machine/hysteresis;
- materialization bridge/result mapping;
- retained failure/explicit retry;
- due ordering/fairness/pulse budgets;
- typed work sink/no-op production integration;
- overload degradation;
- PhantomSystem startup/shutdown order;
- metrics/diagnostics;
- no persistence/auto registration;
- deterministic suite and three runs;
- 10,000 dormant/fairness performance evidence;
- all regression results;
- cumulative verify/jar/verifier;
- production DB safety;
- scope/deviations/limitations;
- branch/parent/subject;
- manual gate `PENDING_INDEPENDENT_REVIEW`;
- Goal 008 and 009 `NOT_STARTED`.

Use external handoff wording for self commit/push evidence.

## 28. Acceptance result

Successful implementation result:

```text
ACTIVITY_SCHEDULER_IMPLEMENTED_PENDING_INDEPENDENT_REVIEW
```

Codex must not self-accept Goal 007 or start Goal 008/009.

## 29. Commit/push

Commit subject:

```text
feat(phantoms): add shared activity scheduler
```

One ordinary commit on top of `82a03342...`.

No amend, rebase, merge commit, reset history or force push.

Push regardless of SUCCESS/BLOCKED, but only safe scoped artifacts.

## 30. Blocking behavior

Return `BLOCKED` if:

- bounded queue/state cannot be implemented without per-profile tasks;
- signal can be accepted and silently lost on saturation;
- fairness allows a hot profile to starve a due cohort;
- ACTIVE/NEARBY can be reported without successful materialization;
- overload automatically demotes perceptible state;
- scheduler stop permits new work or clears state before failed service drain;
- Goal 006B shutdown order regresses;
- scope requires topology, goals, population or schema;
- production DB is used;
- cumulative verify/jar is not GREEN.

On blocker:

1. remove unsafe/uncompilable production edits;
2. preserve safe contracts/tests/report/verifier;
3. keep Stage I accepted baseline intact;
4. do not start Goal 008/009;
5. ordinary commit/push with exact blocker.

## 31. Final handoff

```text
Status:
Architecture result:
Baseline:
Goal 006B closure:
Stage I:
Roadmap progress:
Config defaults:
Disabled runtime allocations/DB:
Scheduler state/API:
Activity states:
Relevance signal:
Signal source/TTL bounds:
Ready/due bounds:
Coalescing/backpressure:
Promotion/demotion:
Materialization bridge:
Retained failure retry:
Pulse future count:
Fairness:
Overload degradation:
Typed work sink:
Scheduler shutdown ordering:
Registered startup count:
Auto registration/materialization:
Scheduler tests:
Three scheduler runs:
Performance run 1:
Performance run 2:
10,000 dormant evidence:
All prior suites:
ant verify:
ant jar:
Static verifier pre:
Static verifier final 1:
Static verifier final 2:
Outputs identical:
Production DB:
Production JAR test entries:
Commit:
Parent:
Branch:
Push:
Remote ref:
Report:
Manual gate:
Goal 008:
Goal 009:
Limitations/blockers:
```
