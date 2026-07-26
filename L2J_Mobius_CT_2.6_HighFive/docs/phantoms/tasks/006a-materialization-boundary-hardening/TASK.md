# GOAL 006A — Materialization boundary hardening

## 1. Identifier

- Task ID: `006a-materialization-boundary-hardening`
- Type: mandatory bounded safety closure for Goal 006
- Branch: `feature/phantom-world`
- Baseline: `ff0b33abad0affc4fe64b4324aee67f256dc96fa`
- Parent: `9d0465eb62f9913644fab9f1d60feb2f4fd9a674`
- Git root: `C:\Users\endim\L2J_Mobius\`
- Only module: `C:\Users\endim\L2J_Mobius\L2J_Mobius_CT_2.6_HighFive\`
- Test DB: `l2jmobiush5_phantom_test`
- Production DB: `l2jmobiush5` — never use during Codex execution
- Seed: `20260725001`
- Model: Sol
- Effort: Very High

## 2. Independent review gate

```text
Goal 005: ACCEPT
Goal 006 architecture direction: ACCEPT
Goal 006 commit: FIX_REQUIRED
Revert: NOT_REQUIRED
Goal 006A: REQUIRED
Goal 007: BLOCKED
```

Preserve the accepted Goal 006 architecture: canonical profile-to-Player service,
one shared per-actor lifecycle, thin Task 004 wrapper, fair cap, profile/character
reservation maps, tokenized actions, retryable cleanup, retained-real recovery,
safe unmaterialized restart, fixed metrics and bounded trace.

## 3. Findings

### P1 — incomplete World/autosave identity preflight

`PhantomMaterializedPlayer.materialize()` checks `World.getPlayer(objectId)` but
not any `World.findObject(objectId)` or pre-existing autosave entry. `World.addObject`
can retain a non-Player in the general map while adding the Player to the player
map, creating split identity.

### P1 — action admission race with STOPPING

The service reads RUNNING and calls `actor.tryAcquireAction()` outside the same
critical section. Shutdown may set STOPPING between them and a new action can be
admitted after shutdown starts.

### P1/P2 — shutdown timeout is not a caller wall-clock bound

The deadline currently bounds action-drain waiting and loop checks, but not
entry-monitor acquisition or canonical `storeMe/deleteMe`. The caller can block
past the reported 10-second total budget.

### Documentation — Goal 005 verifier SHA misattributed

`39A1D87D...B7BC9` belongs to Task 004B. Goal 005 external handoff reported
`69/69 x2` and SHA prefix/suffix `483B6CAD…6E97`. Do not invent a full value.

## 4. Mandatory reading

Read the roadmap, master plan, Agents.md, workflow standards, Task 004–006
packages/reports/reviews, World.java, PlayerAutoSaveTaskManager.java,
PhantomMaterializedPlayer.java, PhantomMaterializationService.java,
PhantomSystem.java, production materialization tests and verifier 006.

## 5. Initial Git audit

```bat
git rev-parse --show-toplevel
git status --short --branch
git remote -v
git fetch origin --prune
git rev-parse HEAD
git rev-parse origin/feature/phantom-world
git show --stat --oneline ff0b33abad0affc4fe64b4324aee67f256dc96fa
git diff --name-status 9d0465eb62f9913644fab9f1d60feb2f4fd9a674..ff0b33abad0affc4fe64b4324aee67f256dc96fa
```

Require HEAD and remote exact. Preserve/exclude unrelated `docs/agent-tasks/**`.

## 6. Fixed architecture

### 6.1. Materialization identity preflight

Before PHANTOM claim and immediately after claim require:

```text
World.getPlayer(objectId) == null
World.findObject(objectId) == null
PlayerAutoSaveTaskManager.containsObjectId(objectId) == false
```

After `Player.load` require:

```text
loaded Player ID == claimed ID
both World maps are null
exact loaded Player is in autosave
no other Player with the same ID is in autosave
```

Immediately before `spawnMe`, recheck both World maps. Immediately after spawn:

```text
World.getPlayer(objectId) == loaded Player
World.findObject(objectId) == loaded Player
```

Add a narrow read-only autosave query such as
`containsOtherObjectId(int, Player)` only if needed. Do not modify World.

Expose distinguishable failures/results equivalent to:

```text
WORLD_PLAYER_IDENTITY_BUSY
WORLD_OBJECT_IDENTITY_BUSY
AUTOSAVE_IDENTITY_BUSY
WORLD_REGISTRATION_MISMATCH
```

Every failure uses existing retryable cleanup and releases service map/permit
only after terminal STORED.

### 6.2. Atomic action admission

`tryAcquireAction(profileId)` must use `_stateMonitor` for the RUNNING check,
entry lookup and bounded actor admission call. The monitor must not cover DB,
Player or World work.

Thus either the action is acquired before STOPPING and is drained, or STOPPING
wins and the action is rejected.

### 6.3. Wall-clock-bounded shutdown caller

Use exactly one transient service-level drain attempt on the existing shared
`ThreadPool`. Do not create an executor or raw thread.

Required behavior:

```text
shutdown
 -> under state monitor set STOPPING
 -> create or reuse one DrainAttempt
 -> submit one service-level drain command to existing ThreadPool
 -> wait on its latch no longer than configured shutdown timeout
 -> timeout returns FAILED with exact retained profile IDs
```

DrainAttempt is guarded by `_stateMonitor` and guarantees:

- one in-flight drain command per service;
- concurrent/second shutdown reuses the same in-flight attempt;
- no concurrent cleanup of an entry;
- after completion, a later explicit shutdown may start a retry if entries remain;
- successful late completion may reach STOPPED;
- timeout itself never releases maps, permits or identity.

One transient service-level ScheduledFuture/latch is allowed. Per-profile task,
future, thread or executor remains forbidden.

The command keeps stable profile order, at most two passes, and exact release
only after STORED. Canonical store/delete is not force-cancelled. If it continues
after caller timeout, ownership remains fail-closed and the tracked attempt
prevents duplicate cleanup.

Submission failure returns FAILED without releasing ownership.

### 6.4. PhantomSystem

Preserve configured instance retention until STOPPED, scheduler retention after
failed drain, second explicit shutdown, and disabled zero-repository/DB behavior.
No GameServer.java or Shutdown.java changes.

## 7. Required tests

### Identity collision

- existing Player in World is rejected and untouched;
- non-Player WorldObject with character ID is rejected;
- autosave Player with same ID is rejected;
- deterministic object insertion after Player load but before spawn is detected;
- no split World maps, leaked PHANTOM lease, map or permit.

### Action/STOPPING

- materialize and hold an existing ActionLease;
- start shutdown, observe STOPPING;
- every later action attempt returns empty;
- release held token and complete shutdown;
- bounded repeated concurrency coverage;
- verifier confirms admission is inside `_stateMonitor`.

### Shutdown wall-clock

Block `BEFORE_STORE_OPERATION` on a latch with timeout around 100–250 ms:

1. shutdown returns FAILED in less than one second;
2. entry, identity and permit are retained;
3. second early shutdown does not invoke cleanup again;
4. release latch;
5. tracked attempt completes;
6. later shutdown reaches STOPPED;
7. no new executor/raw thread;
8. no residue.

Run all production/headless/profile/recovery/performance/cumulative regressions
and three independent production materialization runs.

## 8. Provenance/docs

Create:

```text
docs/phantoms/reviews/006-production-materialization-lifecycle-review.md
docs/phantoms/reports/006a-materialization-boundary-hardening.md
```

Update Goal 006 report with FIX_REQUIRED / Goal 006A REQUIRED / Goal 007 BLOCKED.

Correct Goal 005/006 provenance:

- label `39A1D87D...B7BC9` only as Task 004B verifier;
- keep Goal 005 accepted on 69/69 x2;
- search existing generated/external handoff artifacts for full SHA matching
  `483B6CAD` prefix and `6E97` suffix;
- if unavailable, say so explicitly and retain only the supplied abbreviated
  evidence; never substitute/invent a hash.

Update roadmap progress only:

```text
Goal 006: FIX_REQUIRED
Goal 006A: IMPLEMENTED_PENDING_INDEPENDENT_REVIEW
Goal 007: NOT_STARTED / BLOCKED
```

Do not alter roadmap architecture or future GOAL definitions.

## 9. Scope

Allowed production:

```text
java/org/l2jmobius/gameserver/phantoms/player/PhantomMaterializedPlayer.java
java/org/l2jmobius/gameserver/phantoms/player/PhantomMaterializationService.java
java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java
java/org/l2jmobius/gameserver/taskmanagers/PlayerAutoSaveTaskManager.java
```

Allowed build/tests:

```text
build.xml
test/java/org/l2jmobius/tests/phantoms/PhantomProductionMaterializationSuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomProductionMaterializationPerformanceSuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java
tools/phantoms/verify-task-006a.ps1
```

Allowed docs:

```text
docs/PHANTOM_BOTS_ROADMAP.md
docs/phantoms/architecture/MATERIALIZATION_LIFECYCLE_CONTRACT.md
docs/phantoms/tasks/006a-materialization-boundary-hardening/**
docs/phantoms/reports/005-core-profile-persistence-envelope.md
docs/phantoms/reports/006-production-materialization-lifecycle.md
docs/phantoms/reports/006a-materialization-boundary-hardening.md
docs/phantoms/reviews/005-core-profile-persistence-envelope-review.md
docs/phantoms/reviews/006-production-materialization-lifecycle-review.md
```

## 10. Hard out of scope

No schema, config, profile model/repository, identity recovery truth table,
World, Player, GameClient, Disconnection, packet seam, auto-materialization,
Goal 007/activity/population/AI/navigation/combat/economy/Semantic Pack, new
executor/raw thread, per-profile task/future, production DB execution, other
chronicles, dependencies/CI, old verifier edits, mass formatting, amend/rebase/
merge/force push.

## 11. Static verifier

Create `tools/phantoms/verify-task-006a.ps1` checking:

- base ff0b33ab and one ordinary exact-scope commit;
- no schema/config/Goal 007/network/World/identity-recovery changes;
- both World maps and autosave preflight/post-spawn exact identity;
- explicit collision statuses;
- action acquisition inside `_stateMonitor`;
- one shared service-level drain attempt using existing ThreadPool only;
- bounded latch wait with configured timeout;
- no new Thread/Executor/per-profile Future;
- blocking timeout test and elapsed assertion;
- no duplicate drain and timeout ownership retention;
- report/review/provenance correction and progress-only roadmap edits;
- UTF-8, mojibake, escaped Cyrillic, credentials/binaries;
- deterministic read-only behavior.

## 12. Commands

```bat
ant verify
powershell -NoProfile -ExecutionPolicy Bypass -File tools\phantomserify-task-006.ps1
ant compile-tests
ant phantom-production-materialization-test
ant phantom-production-materialization-performance-smoke
ant phantom-headless-player-test
ant phantom-profile-persistence-test
ant phantom-db-test
ant test
ant phantom-skeleton-test
```

Run `ant phantom-production-materialization-test` three independent times.

Final:

```bat
ant verify
ant jar
powershell -NoProfile -ExecutionPolicy Bypass -File tools\phantomserify-task-006a.ps1
git diff --check
git status --short --branch
```

Post-commit run verify/jar/verifier twice, compare outputs byte-for-byte, push,
verify remote and clean status.

## 13. Acceptance/result

Critical gates:

- all World/autosave collision paths reject safely;
- both World maps point to exact Player after spawn;
- no action after STOPPING;
- shutdown caller returns inside configured wall-clock budget;
- one tracked drain attempt, no duplicate cleanup;
- timeout retains map/permit/identity;
- late completion/explicit retry reaches STOPPED;
- no new executor/raw thread/per-profile future;
- provenance corrected without invented SHA;
- all regressions, verify, jar and verifier x2 pass;
- production DB untouched; Goal 007 not started.

Successful result:

```text
PRODUCTION_MATERIALIZATION_LIFECYCLE_HARDENED_PENDING_INDEPENDENT_REVIEW
```

Commit subject:

```text
fix(phantoms): harden materialization boundaries
```

One ordinary commit on top of ff0b33ab. Push regardless of SUCCESS/BLOCKED with
safe scoped artifacts only.

## 14. Final handoff

```text
Status:
Architecture result:
Baseline:
Goal 006 review:
World Player collision:
World object collision:
Autosave collision:
Post-load identity:
Post-spawn identity:
Action/STOPPING atomicity:
Shutdown caller timeout:
Blocked cleanup elapsed:
Single drain attempt:
Timeout ownership retention:
Late completion:
Explicit retry:
Goal 005 provenance correction:
Production materialization tests:
Three consecutive runs:
Headless/profile regressions:
Performance:
All prior suites:
ant verify:
ant jar:
Static verifier pre:
Static verifier final 1:
Static verifier final 2:
Outputs identical:
Production DB:
Commit:
Parent:
Branch:
Push:
Remote ref:
Report:
Manual gate:
Goal 007:
Limitations/blockers:
```
