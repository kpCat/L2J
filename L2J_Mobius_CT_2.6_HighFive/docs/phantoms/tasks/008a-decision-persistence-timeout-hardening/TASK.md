# GOAL 008A — Decision persistence and timeout hardening

## 1. Identity

- **Task:** `008a-decision-persistence-timeout-hardening`
- **Type:** mandatory bounded safety closure for Goal 008
- **Branch:** `feature/phantom-world`
- **Base:** `b6c58c37f1ba77e92b61e9499a30d17d09c82086`
- **Parent:** `357c047fdba4bc9ea3b4ee21bcedbd5ce6c64018`
- **Module:** `C:\Users\endim\L2J_Mobius\L2J_Mobius_CT_2.6_HighFive`
- **Test DB:** `l2jmobiush5_phantom_test`
- **Production DB:** forbidden during execution
- **Seed:** `20260725001`
- **Model:** Sol
- **Effort:** Very High

## 2. Independent review gate

```text
Stage I: COMPLETE
Goal 007 / 007A: ACCEPT
Goal 008 architecture direction: ACCEPT
Goal 008 commit: FIX_REQUIRED
Revert: NOT_REQUIRED
Goal 008A: REQUIRED
Goal 009: BLOCKED
```

Keep all accepted Goal 008 work:

- immutable bounded goal/DomainRef/capability model;
- deterministic binary `goal.runtime` codec;
- sealed bounded candidate and handler registries;
- deterministic integer Utility AI and top-eight explanation;
- typed plans and one handler invocation/work item;
- generation-based stale-result rejection;
- retry/replan/terminal handler results;
- zero automatic attach/registration/materialization;
- empty sealed production registries;
- scheduler current-request reconciliation from Goal 007A.

This task closes only persistence-lock, timeout and snapshot-truth findings.

## 3. Findings

### P1 — JDBC is executed while the global decision-engine monitor is held

Current code invokes `_store` inside `synchronized (_monitor)` for:

```text
attach: profileExists + load
insertGoal
setGoal
clearGoal
reload
terminal goal persistence
```

A blocked MariaDB/JDBC call therefore blocks every profile's work claim/result
reconciliation, cancellation-token observation, goal mutation/detach/reload,
`beginStop()` and `finishStop()`.

`PhantomSystem.shutdown()` calls `decisionEngine.beginStop()` synchronously
before its bounded materialization drain. A DB stall under the engine monitor can
therefore make the real server shutdown unbounded and bypass the accepted Goal
006A/006B wall-clock contract.

### P1 — persistence methods have no in-flight ownership protocol

Moving JDBC outside the monitor without a protocol would introduce new races:
two writes from one stale row version; goal replacement while terminal
persistence commits; detach/stop removing a runtime before a committed write is
reconciled; attach completion publishing after STOPPING; handler work starting
while a mutation is in progress.

### P1/P2 — step timeout is not proven and uses `0` as an unset sentinel

`_stepStartedNanos == 0` means “not started”. Scheduler logical time may begin at
`0`. The first attempt at logical time `0` leaves the sentinel unchanged, and a
later retry resets the start time. The focused suite tests total-plan timeout
only, despite the contract requiring both step and total timeout.

### P2 — persistence errors are inconsistently mapped

Explicit insert/set/clear catch only `ConcurrentModificationException`. A
constraint/database/decode failure can escape without placing the runtime into
a stable explicit reload-required state. Terminal persistence labels every
runtime failure as optimistic conflict.

### P2 — snapshot evidence can describe a previous goal

Goal replace/clear/reload and activity-generation cancellation clear the plan
but retain selected candidate, score, explanations and last step result until a
later decision.

## 4. Goal

Implement and prove:

1. no `PhantomGoalStore` call executes while `_monitor` is owned;
2. one bounded persistence operation may be in flight per runtime;
3. pending attaches are bounded and cannot publish after STOPPING;
4. work, mutation and reload do not race a persistence operation;
5. detach/stop retain the runtime until handler and persistence quiesce;
6. terminal handler persistence is two-phase and cannot overwrite a newer goal;
7. optimistic conflicts and other persistence failures are distinguishable and
   require explicit reload;
8. cancellation-token checks and `beginStop()` remain responsive while another
   profile's store call is blocked;
9. step timeout works when the first step begins at logical time `0`;
10. total timeout, retry attempts, replan and terminal persistence remain GREEN;
11. snapshot decision evidence is reset at goal/activity ownership boundaries;
12. Goal 009 remains `NOT_STARTED`.

## 5. Mandatory reading

Read fully:

- roadmap, master plan, `Agents.md`, workflow/package/report standards;
- Goal 008 package/report/contract;
- Goal 007A report/review;
- current decision package, scheduler/system integration and all decision tests;
- `PhantomProfileRepository` component exception/optimistic behavior;
- all documents in this package.

## 6. Initial Git audit

```bat
git rev-parse --show-toplevel
git status --short --branch
git remote -v
git fetch origin --prune
git rev-parse HEAD
git rev-parse origin/feature/phantom-world
git show --stat --oneline b6c58c37f1ba77e92b61e9499a30d17d09c82086
git diff --name-status 357c047fdba4bc9ea3b4ee21bcedbd5ce6c64018..b6c58c37f1ba77e92b61e9499a30d17d09c82086
```

Expected:

```text
HEAD == origin/feature/phantom-world == b6c58c37...
```

The extracted 008A package is expected untracked. Preserve unrelated
`docs/agent-tasks/**`. Return `BLOCKED_BASELINE_DRIFT` for unreviewed
production/config/schema drift.

## 7. Fixed two-phase persistence protocol

Detailed contract: `PERSISTENCE_OWNERSHIP.md`.

### 7.1. Runtime markers

Each attached runtime may contain bounded fields equivalent to:

```text
persistenceInFlight
persistenceOperationId
persistenceOperationKind
```

No Future, thread, executor, callback queue or history per profile.

Engine-wide attach state may contain a bounded set/map of pending profile IDs.
The combined number of attached and pending attaches may not exceed configured
capacity.

### 7.2. General sequence

For every store operation:

```text
under _monitor:
  validate engine/runtime/generation/current row version
  reject if another persistence operation is in flight
  claim one operation token and immutable inputs
release _monitor

call PhantomGoalStore

under _monitor:
  verify exact slot and operation token
  reconcile committed result or stable failure state
  clear persistence claim
  finish pending detach/stop as applicable
```

No `_store.profileExists/load/insert/replace/delete` call may appear inside a
`synchronized (_monitor)` block or helper whose caller holds it.

### 7.3. Attach

1. Validate RUNNING, ID, duplicate and capacity under monitor.
2. Reserve profile ID in a bounded pending-attach set.
3. Call `profileExists` and `load` outside monitor.
4. Remove reservation under monitor.
5. Publish only if engine remains RUNNING and no duplicate appeared.
6. STOPPING returns `CANCELLED_BY_STOP`; no late slot.
7. Store/decode failure returns `PERSISTENCE_FAILED`; no slot.

No automatic retry.

### 7.4. Explicit mutation and reload

While `persistenceInFlight`:

- insert/set/clear/reload return explicit BUSY;
- ordinary work for that profile is ignored;
- detach becomes PENDING;
- beginStop cancels generation but retains slot;
- finishStop returns false.

Capture goal identity/revision/component row version before the call.

On `ConcurrentModificationException` enter
`PERSISTENCE_CONFLICT_REQUIRES_EXPLICIT_RELOAD`.

On other store/decode `RuntimeException` enter
`PERSISTENCE_FAILURE_REQUIRES_EXPLICIT_RELOAD`.

Successful reload clears either state. No automatic retry.

### 7.5. Terminal handler persistence

`COMPLETE_GOAL` or `FAIL_GOAL`:

1. passes generation/goal/plan checks;
2. claims terminal persistence while runtime remains in-flight;
3. constructs incremented terminal goal under monitor;
4. performs store replacement outside monitor;
5. reconciles exact operation token;
6. publishes TERMINAL only after persistence success;
7. maps conflict/failure distinctly;
8. clears handler/persistence markers and pending detach afterward.

Concurrent mutation returns BUSY and cannot race the same row version.

### 7.6. Responsiveness

A blocked fake store for profile A must not prevent:

- cancellation-token observation for handler B;
- `beginStop()` returning promptly;
- `find/list/snapshot` for B;
- generation cancellation.

`finishStop()` stays false until blocked persistence finishes, then succeeds.
Use latch tests and a one-second upper gate for monitor responsiveness.

## 8. Step timeout truth

Use explicit unset value outside valid logical time, e.g.:

```text
stepStartedNanos = -1
```

- initialize/reset to `-1`;
- set once on first attempt;
- RETRY never resets it;
- SUCCESS advancing step resets to `-1`;
- REPLAN/cancel/new plan resets to `-1`;
- check step timeout before another handler.

Mandatory test:

```text
plan begins at logical time 0
first handler returns RETRY
clock advances beyond step timeout but below total timeout
next work invokes no handler
plan becomes NEEDS_REPLAN with step-timeout reason
```

Keep separate total-timeout regression.

## 9. Plan completion semantics

Do not change accepted semantics:

- final ordinary `SUCCESS` completes the plan and leaves ACTIVE goal in
  `NEEDS_REPLAN`;
- only `COMPLETE_GOAL` terminally completes the goal;
- `FAIL_GOAL` terminally fails it.

Add a focused assertion documenting this distinction.

## 10. Snapshot truth

Clear selected candidate, score, explanations and last result when:

- goal inserted/replaced/cleared/reloaded;
- activity generation changes;
- persistence conflict/failure entered;
- detach/stop cancellation begins.

## 11. System integration

Keep startup and shutdown ordering. `beginStop()` must not wait for JDBC.
`finishStop()` returns false while any handler, persistence operation or pending
attach remains. A later Goal 006B shutdown retries. No wait loop/task.

## 12. Tests

Decision core >=35 cases; decision persistence >=20 cases.

New cases:

1. fake store asserts no method runs under engine monitor;
2. blocked attach + beginStop, no late publish;
3. blocked mutation A does not block token/stop for B;
4. blocked terminal persistence + setGoal BUSY;
5. detach during persistence pending then removed;
6. finishStop false during attach/persistence;
7. conflict vs generic failure distinct;
8. reload clears both;
9. logical-zero step timeout;
10. total timeout;
11. final SUCCESS plan/nonterminal goal semantics;
12. snapshot reset;
13. no ordinary-tick DB read;
14. scheduler/system stop regressions.

Run core ×3, persistence ×3, performance ×2, scheduler ×3, production ×3,
shutdown ×3, and all cumulative routes.

## 13. Scope

Allowed production:

```text
java/org/l2jmobius/gameserver/phantoms/decision/PhantomDecisionEngine.java
java/org/l2jmobius/gameserver/phantoms/decision/PhantomGoalStore.java
java/org/l2jmobius/gameserver/phantoms/decision/PhantomGoalStateStore.java
java/org/l2jmobius/gameserver/phantoms/PhantomMetrics.java
java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java
```

Allowed tests/build:

```text
build.xml
test/java/org/l2jmobius/tests/phantoms/PhantomDecisionCoreSuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomDecisionPersistenceSuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomDecisionPerformanceSuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomSkeletonSuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomServerShutdownHandoffSuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java
tools/phantoms/verify-task-008a.ps1
```

Allowed docs:

```text
docs/PHANTOM_BOTS_ROADMAP.md
docs/phantoms/architecture/DECISION_GOAL_PLAN_CONTRACT.md
docs/phantoms/tasks/008a-decision-persistence-timeout-hardening/**
docs/phantoms/reports/008-goal-utility-plan-core.md
docs/phantoms/reports/008a-decision-persistence-timeout-hardening.md
docs/phantoms/reviews/008-goal-utility-plan-core-review.md
```

## 14. Hard out of scope

No config/schema/profile repository changes. No Goal 006 lifecycle, identity,
Player, network or World changes. No scoring/model/codec-format changes unless
compile-only. No concrete action, combat, navigation, Game Knowledge,
population, Semantic Pack or Goal 009. No new executor/raw production
thread/per-profile Future. No production DB, dependencies, CI, other chronicles,
mass formatting, amend/rebase/force push.

## 15. Static verifier

Create `tools/phantoms/verify-task-008a.ps1` checking:

- base, one ordinary exact-scope commit;
- no config/schema/Goal 009;
- no `_store.` call under global monitor;
- bounded pending attach;
- one persistence claim/runtime;
- work/mutation/reload exclusion;
- detach/finishStop retention;
- terminal persistence two-phase;
- distinct conflict/failure states;
- no automatic retry;
- `-1` step sentinel and logical-zero timeout test;
- final SUCCESS semantics test;
- snapshot reset;
- no executor/thread/per-profile future;
- docs/tests/encoding/credentials/binaries.

## 16. Documentation

Create review/report for Goal 008/008A. Update Goal 008 report with:

```text
Commit: b6c58c37f1ba77e92b61e9499a30d17d09c82086
Parent: 357c047fdba4bc9ea3b4ee21bcedbd5ce6c64018
Push/remote: exact
Core: 30/30
Persistence: 14/14
Performance: 2/2
Scheduler: 20/20
Final verifier: 68/68 ×2
External final verifier SHA-256:
B2968457F0F59C0CEFDCF4566F4CA1C9FF456CB05FC886E1C111915BF67689C0
Independent review: FIX_REQUIRED
Goal 008A: REQUIRED
Goal 009: BLOCKED
```

Label the report's existing different verifier SHA by its actual stage; do not
misattribute it.

Roadmap progress only:

```text
Goal 008: FIX_REQUIRED
Goal 008A: IMPLEMENTED_PENDING_INDEPENDENT_REVIEW
Goal 009: NOT_STARTED / BLOCKED
```

## 17. Commands

Run pre-change verify/007A verifier, all targeted decision/scheduler/lifecycle
tests, core ×3, persistence ×3, performance ×2, scheduler ×3, production ×3,
shutdown ×3, then:

```bat
ant verify
ant jar
powershell -NoProfile -ExecutionPolicy Bypass -File tools\phantoms\verify-task-008a.ps1
git diff --check
```

Post-commit repeat verify/jar/verifier ×2, push and confirm remote exact.

## 18. Result and commit

Successful result:

```text
DECISION_PERSISTENCE_TIMEOUT_HARDENED_PENDING_INDEPENDENT_REVIEW
```

Commit subject:

```text
fix(phantoms): harden decision persistence and timeouts
```

One ordinary commit over `b6c58c37...`.

## 19. Blocking behavior

Return BLOCKED if store calls cannot leave the global monitor safely, stop can
remove unresolved persistence, terminal persistence can overwrite a newer goal,
logical-zero timeout cannot be proven, Goal 009/schema/config is required,
production DB is accessed, or cumulative verify/jar fails.

## 20. Final handoff

```text
Status:
Architecture result:
Baseline:
Goal 008 review:
Store calls under global monitor:
Pending attach:
Per-runtime persistence claim:
Blocked-store cancellation responsiveness:
Blocked-store beginStop responsiveness:
Mutation busy behavior:
Terminal persistence:
Conflict state:
Failure state:
Explicit reload:
Detach during persistence:
finishStop during persistence:
Step timeout at logical zero:
Total timeout:
Final SUCCESS plan semantics:
Snapshot evidence reset:
Core tests:
Persistence tests:
Performance:
Scheduler/materialization/shutdown regressions:
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
Goal 009:
Limitations/blockers:
```
