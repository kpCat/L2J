# GOAL 012A — Combat action ownership and causal truth hardening

## 1. Identifier

- **Task ID:** `012a-combat-action-ownership-truth`
- **Type:** mandatory bounded safety closure for Goal 012
- **Branch:** `feature/phantom-world`
- **Starting baseline:** `8143cb7f89d348854fc469a0955b22405f23e9b6`
- **Parent:** `003604b4f7bda2a8d224d0adcf6349c088154e10`
- **Repository root:** `C:\Users\endim\L2J_Mobius\`
- **Only module:** `C:\Users\endim\L2J_Mobius\L2J_Mobius_CT_2.6_HighFive\`
- **Test DB:** `l2jmobiush5_phantom_test`
- **Production DB:** `l2jmobiush5` — never use during execution
- **Seed:** `20260725001`
- **Codex model:** Sol
- **Effort:** Very High

## 2. Independent review gate

```text
Stage I: COMPLETE
Stage II: COMPLETE

Goal 010 / 010A / 010B / 010C: ACCEPT
Goal 011 / 011A: ACCEPT
Goal 012 architecture direction: ACCEPT
Goal 012 commit: FIX_REQUIRED
Revert: NOT_REQUIRED
Goal 012A: REQUIRED
Goal 013: BLOCKED
Goal 014: NOT_STARTED
```

Keep all accepted Goal 012 work:

- bounded solo combat service;
- exact materialization ActionLease for active sessions;
- one shared pulse worker;
- normal-monster-only target restrictions;
- pure bounded threat table;
- generic melee/ranged-physical/ranged-magic capability resolution;
- canonical PlayerAI ATTACK/CAST/PICK_UP routes;
- canonical shot item handler;
- HP/MP/death observation;
- restricted normal-town respawn;
- plan-scoped decision cancellation;
- combat before materialization shutdown;
- zero production combat candidates/sessions/workers at startup.

This task closes only dispatch/action cleanup, offensive-skill, loot-acquisition
and respawn ownership truth.

## 3. Independent findings

### P1 — shared worker claim can be stranded

Production dispatcher is:

```java
(runnable, delay) -> ThreadPool.schedule(runnable, delay)
```

but the `Dispatcher` return type is `void`.

`ThreadPool.schedule` may return `null` after a caught scheduling exception.
Its scheduled executor rejection handler also returns silently when the executor
is shutting down.

The combat service therefore cannot distinguish:

```text
worker really scheduled
worker silently rejected
```

and can retain:

```text
_workerClaimed = true
```

forever.

Focused tests model only a dispatcher that throws an exception. They do not
model a false/null/silently rejected dispatch.

### P1 — dispatch is not atomically ordered with STOPPING

Current ordering:

```text
under service monitor:
  _workerClaimed = true
release monitor
dispatcher.dispatch(...)
```

`beginStop()` can enter the gap, move the service to STOPPING and clear queues.
The dispatch can then begin after STOPPING.

If server shutdown performs its second Phantom attempt before the delayed pulse
runs, `finishStop()` remains false. The later shared `ThreadPool.shutdown()` can
cancel the pulse before it releases the claim.

### P1 — a pulse Throwable can retain worker ownership

`pulse()` releases `_workerClaimed` only at its normal tail.

`process()` catches `RuntimeException`, not `Throwable`.

An `Error` or any unexpected Throwable can escape before the worker release and
leave a permanent combat worker claim.

### P1 — canonical cleanup failure is hidden

Current cleanup:

```text
cancelOwnedAction throws
→ exception is swallowed
→ ActionLease is still closed
→ actor lease count is decremented
→ cleanupPending becomes false
```

The service can report quiescence and allow materialization drain even though
the owned AI action may still be active.

Cleanup failure must remain truthful and retryable; it may not be converted into
successful lease release.

### P1 — owned PICK_UP action is not cancelled

Session state remembers `_lastLootObjectId`, but the cleanup descriptor contains
only:

```text
combat target object ID
selected skill
```

`L2jCombatBackend.cancelOwnedAction()` handles ATTACK and CAST only.

If cancellation, timeout, stop or actor death occurs while PlayerAI owns a
`PICK_UP` intention, the combat ActionLease is released while the pickup action
continues outside combat ownership.

### P1 — loot disappearance is treated as actor acquisition

Current loot truth:

```text
last attempted item no longer appears in lootCandidates()
→ increment lootPickupsObserved
```

An item can disappear because:

- another player picked it up;
- it expired or was deleted;
- protection/eligibility changed;
- the actor or item moved beyond 300 units;
- the actor changed region/instance.

Those outcomes currently produce `VICTORY_LOOTED` or
`VICTORY_LOOT_PARTIAL` without evidence that the item entered this actor's
inventory.

### P1 — selected skill is not required to be offensive

The adapter currently validates:

- known exact skill/level;
- non-passive;
- non-toggle;
- not transformed;
- `TargetType.ONE`;
- physical versus magic mode.

It does not require an offensive/negative skill.

A positive one-target buff or other non-hostile skill can satisfy the generic
shape and be cast on the Monster. `checkDoCastConditions()` is not a substitute
for the explicit loadout safety gate.

### P1 — respawn has incomplete plan/session/stop ownership

`respawnTown(profileId)`:

1. checks RUNNING and increments a generic start-operation count;
2. releases the monitor;
3. acquires an actor lease;
4. immediately performs teleport/revive.

It does not:

- receive or check the exact plan cancellation token;
- reject an active combat session;
- require active combat cleanup to be complete;
- reconcile the operation with the exact service/session generation before the
  side effect.

A respawn can overlap an active/death-detection combat session or execute after
its plan ownership was cancelled.

## 4. Goal

Implement and prove:

1. worker claim, dispatch acceptance, dispatch handle and STOPPING are exactly
   ordered;
2. no dispatch begins after STOPPING;
3. a scheduled-but-not-started worker can be cancelled and its claim released;
4. false/null/throwing/silent-rejection test dispatchers never strand ownership;
5. every pulse releases its worker claim through a top-level `finally`;
6. backend `Throwable` terminalizes owned sessions without hiding a worker;
7. action cleanup failure retains the actor lease and explicit cleanup
   ownership;
8. failed action cleanup is bounded and retryable;
9. `finishStop()` remains false while action cleanup is unresolved;
10. exact ATTACK, CAST and PICK_UP ownership are all cancellable;
11. foreign/newer actions and targets remain untouched;
12. loot success requires positive actor-acquisition evidence;
13. disappearance without acquisition evidence is never reported as looted;
14. selected skills are explicitly hostile/offensive and safe for the supported
    one-target modes;
15. positive buffs, PvP-only/special and self-destructive skills are rejected;
16. respawn is plan-owned, cannot overlap an active combat session, and cannot
    start after STOPPING;
17. all accepted Goal 012 plan-token and canonical facade behavior remains;
18. Goal 013/014 remain not started;
19. all cumulative regressions remain GREEN.

## 5. Mandatory reading

Read fully:

- roadmap, master plan, `Agents.md`, workflow/package/report standards;
- Goal 012 package/report/contract and Goal 011A closure;
- `PhantomCombatService`, backend/lease/session/handlers/tests;
- `PhantomMaterializationService` and `ActionLease`;
- `ThreadPool.schedule`, rejected handler and shutdown ordering;
- `PlayerAI` ATTACK/CAST/PICK_UP;
- `Skill` offensive/negative/special predicates;
- `Item`, `Inventory`, `Player.doPickupItem`;
- normal-town respawn path;
- `PhantomSystem` and server shutdown handoff;
- all files in this package.

## 6. Initial Git audit

```bat
git rev-parse --show-toplevel
git status --short --branch
git remote -v
git fetch origin --prune
git rev-parse HEAD
git rev-parse origin/feature/phantom-world
git show --stat --oneline 8143cb7f89d348854fc469a0955b22405f23e9b6
git diff --name-status 003604b4f7bda2a8d224d0adcf6349c088154e10..8143cb7f89d348854fc469a0955b22405f23e9b6
```

Expected:

```text
HEAD == origin/feature/phantom-world == 8143cb7f...
```

Preserve all 203 user-owned untracked geodata files. The extracted Goal 012A
package is expected untracked.

Return `BLOCKED_BASELINE_DRIFT` for any other unreviewed production/config/schema
drift.

## 7. Shared worker dispatch ownership

Detailed contract: `WORKER_OWNERSHIP.md`.

### 7.1. Dispatch handle

Replace the void dispatcher with a bounded result/handle equivalent to:

```text
DispatchResult:
  ACCEPTED(handle)
  REJECTED

DispatchHandle:
  cancelIfNotStarted()
  state: SCHEDULED / RUNNING / FINISHED / CANCELLED
```

Production may wrap the single `ScheduledFuture<?>` returned by
`ThreadPool.schedule`.

Only one shared handle exists. This is not a per-profile Future.

A `null` future is rejection.

### 7.2. Ordering gate

Use one narrow `_dispatchGate` shared by:

- worker claim/schedule publication;
- worker start transition;
- `beginStop()`.

Protocol:

```text
under dispatch gate + service monitor:
  require RUNNING, queue nonempty, no worker
  create exact WorkerClaim
release service monitor, retain dispatch ordering
schedule
publish handle or exact rollback
release dispatch gate
```

`beginStop()`:

```text
acquire dispatch gate
set STOPPING
cancel scheduled/not-started shared handle
release exact worker claim if cancellation won
release gate
terminalize sessions
```

No backend call under either lock.

No dispatch after STOPPING.

Inline and immediate dispatchers must not deadlock or double-release.

### 7.3. Pulse finally

Worker claim has exact idempotent release.

The whole pulse body is enclosed in top-level `try/finally`.

Each session processing boundary catches `Throwable`, converts it to
`BACKEND_FAILURE`, and continues bounded processing where safe.

A stale/cancelled worker cannot release a newer claim.

## 8. Retryable action cleanup

Detailed contract: `ACTION_CLEANUP.md`.

### 8.1. Cleanup state

Session owns explicit cleanup state:

```text
NONE
PENDING
IN_PROGRESS
FAILED_RETRYABLE
COMPLETE
```

Terminal result cannot be consumed until cleanup is COMPLETE.

The actor lease remains owned while cleanup is pending/failed.

Do not decrement actor-lease gauges until the exact lease successfully closes.

### 8.2. Cleanup attempts

Cleanup action contains:

```text
session generation
combat target object ID
selected skill
optional pickup object ID
```

Attempt:

1. validate exact cleanup ownership;
2. outside service monitor cancel exact owned action;
3. close exact actor lease;
4. reconcile session generation;
5. mark COMPLETE and release gauges.

If `cancelOwnedAction` throws:

- do not close/release the actor lease;
- mark `FAILED_RETRYABLE`;
- record fixed cleanup failure;
- enqueue one bounded retry through the shared worker;
- `finishStop()` remains false.

Maximum cleanup attempts:

```text
3
```

After exhaustion:

- service state becomes FAILED;
- lease remains visibly owned;
- no hidden materialization drain;
- explicit retry API may continue attempting;
- shutdown diagnostics remain truthful.

Do not create a cleanup thread/task.

### 8.3. Bounded explicit cancel

Remove the unbounded `wait()` loop.

`cancel()` returns typed equivalent:

```text
CANCELLED_CLEAN
CLEANUP_PENDING
CLEANUP_FAILED
NOT_FOUND
ALREADY_TERMINAL
NOT_RUNNING
```

It may wait only up to the fixed materialization action-drain timeout or a
smaller combat cleanup timeout.

`combat.cancel` handler:

- SUCCESS for clean/not-found idempotent outcome;
- RETRY for pending;
- REPLAN for exhausted failure;
- CANCELLED when its plan token is cancelled.

## 9. Exact action descriptor

Change the lease cleanup facade to receive one immutable owned-action descriptor:

```text
combatTargetObjectId
selectedSkill
pickupObjectId
```

Canonical adapter behavior:

### ATTACK

Abort only when AI intention and attack target match the combat target.

### CAST

Abort only when intention, cast target and exact skill match.

### PICK_UP

Abort/cancel only when AI intention and exact pickup item object match the
session pickup object.

### Current target

After owned action cleanup:

- clear current target if it is the exact combat target or exact pickup object;
- this applies even if ATTACK/CAST already returned to IDLE;
- never clear a foreign/newer target;
- never stop a foreign/newer action.

Add real and fake tests for:

- cancellation during PICK_UP;
- victory cleanup clears exact dead target;
- foreign target/action survives;
- stale cleanup descriptor cannot cancel a newer session.

## 10. Causal loot acquisition

Detailed contract: `LOOT_TRUTH.md`.

### 10.1. Candidate fact

Extend the immutable loot candidate/attempt with enough factual data:

```text
worldObjectId
itemId
groundCount
actorInventoryCountBefore
```

No mutable Item reference crosses the backend boundary.

### 10.2. Observation

Add backend observation equivalent to:

```text
PENDING
ACQUIRED_BY_ACTOR
LOST_WITHOUT_ACQUISITION
INELIGIBLE
```

`ACQUIRED_BY_ACTOR` requires canonical evidence such as:

- exact inventory object ownership; or
- ground object removed plus actor inventory count increased by the factual
  ground count.

Use overflow-safe count arithmetic.

A disappearance alone is not acquisition evidence.

### 10.3. Session truth

Only `ACQUIRED_BY_ACTOR` increments successful pickups.

`LOST_WITHOUT_ACQUISITION`:

- records blocked/lost pickup;
- never increments acquired count;
- produces `VICTORY_LOOT_BLOCKED` or partial only if some other item was
  positively acquired.

If actor/item leaves distance or region, do not call it acquired.

Remembered IDs and timeout bounds remain.

Required real tests:

- actor pickup → inventory delta and LOOTED/PARTIAL;
- another test-owned player picks the item → not looted;
- item deleted/despawned → not looted;
- actor moves outside radius while item remains → not looted;
- cancel/stop during PICK_UP leaves no owned pickup intention.

## 11. Offensive skill gate

The exact actual server skill must satisfy all applicable safe predicates:

```text
known exact ID/level
active
not passive
not toggle
TargetType.ONE
hasNegativeEffect / explicit hostile effect
mode physical/magic matches
not PvP-only
not suicide
not hero/GM/SevenSigns/special-only
not transformed-only
canonical cast conditions
```

Use current server predicates; do not infer from skill name.

If the codebase has no single `isOffensive()` method, define a documented
combat-local predicate from authoritative `Skill` flags/effect scopes.

The predicate must reject a positive one-target buff even when the curated
capability XML mistakenly references it.

Pass the exact session mode into the cast revalidation; do not derive a mode
that trivially matches the selected skill itself.

Required tests:

- real supported offensive skill accepted;
- known positive `TargetType.ONE` skill rejected;
- PvP-only/suicide/special skill rejected through deterministic fixture or fake
  skill seam;
- physical/magic mode mismatch rejected;
- unsupported magic remains `UNSUPPORTED_LOADOUT`.

Do not change curated knowledge XML in Goal 012A.

## 12. Respawn ownership

Add an explicit plan-owned respawn request equivalent to:

```text
profileId
planOwnershipToken
```

Before acquiring actor:

- service RUNNING;
- token current;
- no active nonterminal combat session;
- no cleanup pending/in progress;
- no other respawn operation for profile;
- capacity/operation slot reserved.

After acquiring actor and immediately before canonical respawn:

- reconcile service state;
- reconcile exact respawn operation generation;
- recheck plan token;
- recheck no active session/cleanup ownership.

An operation claimed before `beginStop()` may complete as an in-flight bounded
operation; no new respawn may begin after STOPPING.

Handler passes the exact context token.

Required races:

- token already cancelled → zero actor/backend calls;
- token cancelled while actor acquisition is blocked → zero respawn side effect;
- beginStop before operation claim → rejected;
- beginStop after exact claim → finishStop waits;
- active combat session → rejected/retry;
- cleanup-pending terminal session → retry;
- no active session and canonically dead actor → normal-town respawn succeeds.

## 13. Tests

Extend:

```text
PhantomCombatCoreSuite
PhantomCombatOwnershipSuite
PhantomCombatServerIntegrationSuite
PhantomCombatPerformanceSuite
```

Add a focused suite if clearer:

```text
PhantomCombatActionOwnershipSuite
launcher: combat-action-ownership
Ant: phantom-combat-action-ownership-test
```

Minimum new executable cases:

### Worker

1. dispatcher returns explicit rejection;
2. dispatcher returns null production handle;
3. throw rejection;
4. beginStop wins claim-to-dispatch gap;
5. accepted scheduled worker cancelled before start;
6. inline dispatcher no deadlock/double-release;
7. stale worker cannot release newer claim;
8. backend Error does not strand worker;
9. no dispatch begins after STOPPING.

### Cleanup/action

10. cancelOwnedAction throws: lease retained and cleanup retryable;
11. retry success releases lease once;
12. retry exhaustion leaves service FAILED and lease visible;
13. finishStop false while cleanup failed;
14. cancellation during PICK_UP cancels exact pickup;
15. exact dead target cleared after victory;
16. foreign attack/cast/pickup survives stale cleanup;
17. explicit cancel wait is bounded.

### Loot

18. actor acquisition positive proof;
19. other-player pickup is not actor acquisition;
20. despawn/delete is not actor acquisition;
21. out-of-radius disappearance from candidates is not acquisition;
22. partial result requires at least one positively acquired item.

### Skills

23. positive one-target skill rejected;
24. offensive one-target skill accepted;
25. mode mismatch rejected;
26. special/PvP/suicide skill rejected.

### Respawn

27. cancelled token before actor acquisition;
28. cancellation during blocked actor acquisition;
29. active session rejects respawn;
30. cleanup-pending session retries;
31. STOPPING rejects new respawn;
32. in-flight claimed respawn participates in stop barrier.

Repeat:

- combat core ×3;
- combat ownership ×3;
- combat action ownership ×3;
- real integration ×2;
- performance ×2;
- every Goal 011A focused route;
- all cumulative Goal 001–012 routes;
- verify/jar.

## 14. Exact scope

Allowed production:

```text
java/org/l2jmobius/gameserver/phantoms/combat/**
java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java
java/org/l2jmobius/gameserver/phantoms/PhantomMetrics.java
java/org/l2jmobius/gameserver/Shutdown.java
```

Prefer no further decision-engine change. A minimal compile/test adjustment to
combat handler registration is allowed.

Allowed tests/build:

```text
build.xml
test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java
test/java/org/l2jmobius/tests/phantoms/PhantomCombatCoreSuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomCombatOwnershipSuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomCombatActionOwnershipSuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomCombatServerIntegrationSuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomCombatPerformanceSuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomServerShutdownHandoffSuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomSkeletonSuite.java
tools/phantoms/verify-task-012a.ps1
```

Allowed documentation:

```text
docs/PHANTOM_BOTS_ROADMAP.md
docs/phantoms/architecture/COMBAT_KERNEL_CONTRACT.md
docs/phantoms/tasks/012a-combat-action-ownership-truth/**
docs/phantoms/reports/012-capability-driven-combat-kernel.md
docs/phantoms/reports/012a-combat-action-ownership-truth.md
docs/phantoms/reviews/012-capability-driven-combat-kernel-review.md
```

## 15. Frozen scope

Do not change:

- decision plan-token semantics unless a compile-only signature adaptation is
  unavoidable;
- Game Knowledge behavior or curated XML;
- materialization lifecycle;
- Player/Creature/AI/Skill/Item/Inventory/World/ThreadPool core;
- datapack, geodata, config or DB schema;
- Goal 013/014.

## 16. Hard out of scope

Forbidden:

- new production candidate or automatic target scan;
- progression/class/equipment catalog;
- commerce or supply purchasing;
- PvP/raid/party/spoil/manor;
- direct damage/HP/MP/EXP/inventory mutation;
- packet simulation;
- loader/server-core modification;
- new executor/raw production thread/per-profile Future/task;
- production DB;
- other chronicles/dependencies/CI/mass formatting;
- amend/rebase/merge/force push.

## 17. Static verifier

Create deterministic read-only:

```text
tools/phantoms/verify-task-012a.ps1
```

Verify:

- base `8143cb7f...`, one ordinary exact-scope commit;
- geodata/datapack/config/schema/Goal 013/014 absent;
- server core, ThreadPool, materialization, decision and knowledge frozen;
- dispatcher has explicit accepted handle/result;
- shared dispatch/STOPPING gate;
- cancel-not-started scheduled handle;
- top-level worker finally and Throwable isolation;
- no per-profile task/Future;
- cleanup failure does not close/release actor lease;
- cleanup retry state/attempt bound;
- consume/finishStop blocked until cleanup complete;
- PICK_UP ID included in owned action cleanup;
- actor-acquisition evidence required for loot success;
- item disappearance alone never increments acquired count;
- offensive/negative one-target skill predicate;
- exact session mode passed to cast validation;
- plan-owned respawn request and stop/session reconciliation;
- no direct damage/inventory/packet route;
- focused/race/real/performance tests;
- Goal 012 report/review/roadmap progress;
- UTF-8, mojibake, escaped Cyrillic;
- no credentials/binaries;
- verifier deterministic/read-only.

## 18. Documentation

Create:

```text
docs/phantoms/reviews/012-capability-driven-combat-kernel-review.md
docs/phantoms/reports/012a-combat-action-ownership-truth.md
```

Update Goal 012 report with immutable handoff:

```text
Commit: 8143cb7f89d348854fc469a0955b22405f23e9b6
Parent: 003604b4f7bda2a8d224d0adcf6349c088154e10
Push/remote: exact
Combat core: 47/47 ×3
Ownership: 17/17 ×3
Real integration: 12/12 ×2
Performance: 1/1 ×2
Final verifier: 112/112 ×2, byte-identical
Verifier SHA-256:
9EC6EF14E662BF6BEAF33356F985A99F7AFCF321A3E75548B2974C4ABD22BB1E
Independent review:
- architecture ACCEPT
- action ownership/causal truth FIX_REQUIRED
Goal 012A: REQUIRED
Goal 013: BLOCKED
```

Review verdict:

```text
Goal 012 architecture direction: ACCEPT
Goal 012 commit: FIX_REQUIRED
Revert: NOT_REQUIRED
Goal 012A: REQUIRED
Goal 013: BLOCKED
Goal 014: NOT_STARTED
```

Roadmap progress only:

```text
Goal 012: FIX_REQUIRED
Goal 012A: IMPLEMENTED_PENDING_INDEPENDENT_REVIEW
Goal 013: NOT_STARTED / BLOCKED
Goal 014: NOT_STARTED
```

## 19. Commands

Pre-change:

```bat
ant verify
powershell -NoProfile -ExecutionPolicy Bypass -File tools\phantoms\verify-task-012.ps1
```

Targeted:

```bat
ant compile-tests
ant phantom-combat-core-test
ant phantom-combat-ownership-test
ant phantom-combat-action-ownership-test
ant phantom-combat-server-integration-test
ant phantom-combat-performance-smoke
ant phantom-game-knowledge-core-test
ant phantom-game-knowledge-parity-test
ant phantom-game-knowledge-query-truth-test
ant phantom-game-knowledge-content-test
ant phantom-game-knowledge-performance-smoke
ant phantom-topology-scheduler-signal-integration-test
ant phantom-topology-signal-ledger-test
ant phantom-topology-generation-test
ant phantom-topology-perception-test
ant phantom-topology-core-test
ant phantom-topology-production-corpus-test
ant phantom-topology-performance-smoke
ant phantom-navigation-core-test
ant phantom-navigation-performance-smoke
ant phantom-server-shutdown-handoff-test
ant phantom-decision-core-test
ant phantom-decision-persistence-test
ant phantom-decision-performance-smoke
ant phantom-activity-scheduler-test
ant phantom-production-materialization-test
ant phantom-headless-player-test
ant phantom-profile-persistence-test
ant phantom-db-test
ant test
ant phantom-skeleton-test
```

Repeat Goal 012A focused matrix from §13.

Final:

```bat
ant verify
ant jar
powershell -NoProfile -ExecutionPolicy Bypass -File tools\phantoms\verify-task-012a.ps1
git diff --check
```

Post-commit run verify/jar/verifier ×2, push and confirm remote exact.

## 20. Result and commit

Successful result:

```text
COMBAT_ACTION_OWNERSHIP_TRUTH_HARDENED_PENDING_INDEPENDENT_REVIEW
```

Commit subject:

```text
fix(phantoms): harden combat action ownership
```

One ordinary commit on top of `8143cb7f...`.

Push regardless of SUCCESS/BLOCKED using safe scoped artifacts only.

## 21. Blocking behavior

Return `BLOCKED` if:

- exact scheduled worker cancellation requires modifying ThreadPool core;
- causal loot acquisition cannot be proven through existing World/Inventory
  facts;
- offensive skill safety requires Skill/server-core modification;
- PICK_UP cleanup cannot be expressed through canonical AI APIs;
- respawn ownership requires decision or materialization semantic changes;
- Goal 013/config/schema/datapack changes are required;
- production DB is accessed;
- cumulative verify/jar fails.

On blocker remove unsafe production edits, preserve safe audit/tests/report/
verifier, commit/push and keep Goal 013 blocked.

## 22. Final handoff

```text
Status:
Architecture result:
Baseline:
Goal 012 review:
Dispatch result/handle:
Dispatch-STOPPING ordering:
Scheduled worker cancellation:
Worker Throwable finally:
Cleanup state/retry:
Cleanup failure lease truth:
Explicit cancel bound:
Owned ATTACK cleanup:
Owned CAST cleanup:
Owned PICK_UP cleanup:
Foreign action preservation:
Loot candidate proof:
Actor acquisition:
Other-player pickup:
Despawn/out-of-radius:
Offensive skill gate:
Positive skill rejection:
Mode/special skill rejection:
Respawn plan token:
Respawn active-session gate:
Respawn stop barrier:
Core:
Ownership:
Action ownership:
Real integration:
Performance:
All regressions:
ant verify:
ant jar:
Verifier final 1/final 2/identical/SHA:
Production DB:
JAR combat/test entries:
Commit/parent/branch/push/remote:
Report:
Manual gate:
Goal 013:
Goal 014:
Limitations/blockers:
```
