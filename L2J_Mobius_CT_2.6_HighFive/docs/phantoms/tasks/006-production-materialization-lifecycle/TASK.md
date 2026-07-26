# GOAL 006 — Production materialization lifecycle

## 1. Identifier

- **Goal ID:** `006-production-materialization-lifecycle`
- **Roadmap stage:** I — Canonical actor, persistence and lifecycle
- **Branch:** `feature/phantom-world`
- **Accepted baseline:** `9d0465eb62f9913644fab9f1d60feb2f4fd9a674`
- **Parent:** `f5b66c4edf1ddf18e044ef8c692d70ecea616485`
- **Git root:** `C:\Users\endim\L2J_Mobius\`
- **Only module:** `C:\Users\endim\L2J_Mobius\L2J_Mobius_CT_2.6_HighFive\`
- **Test DB:** `l2jmobiush5_phantom_test`
- **Production DB:** `l2jmobiush5` — never use during Codex execution
- **Seed:** `20260725001`
- **Codex model:** Sol
- **Effort:** Very High

## 2. Accepted gates

```text
Task 001 / 001A: ACCEPT
Task 002 / 002A: ACCEPT
Task 003: ACCEPT
Task 004 / 004A / 004B: ACCEPT
ADR 0001: Accepted
Goal 005: ACCEPT_WITH_BOUNDED_FOLLOW_UPS
Goal 006: ALLOWED
Goal 007: NOT_STARTED
```

Goal 005 accepted facts:

```text
Commit: 9d0465eb62f9913644fab9f1d60feb2f4fd9a674
Parent: f5b66c4edf1ddf18e044ef8c692d70ecea616485
Remote: exact
Schema: 118 scripts / 207 statements
Profile suite: 18/18
Three independent headless runs: 18/18 each
Verifier final: 69/69 ×2
Independent verdict: ACCEPT
```

Copy the exact full provisioning and verifier SHA-256 values from the Goal 005
report into the closure documents.

Bounded Goal 005 follow-ups included here, not a separate 005A:

1. profile test cleanup must delete only suite-owned rows and preserve a foreign
   sentinel row;
2. `PhantomProfileComponent.equals/hashCode` must compare payload bytes by value.

## 3. User-visible result

After Goal 006 an explicitly enabled Phantom subsystem has a production-owned,
bounded API that can:

- materialize a linked Phantom profile into a canonical headless `Player`;
- reject missing, unlinked, duplicate, over-cap and identity-conflicting requests;
- expose immutable lifecycle snapshots;
- close action admission before cleanup;
- dematerialize and persist the canonical Player safely;
- retain failed cleanup state for explicit retry;
- drain all active materializations during Phantom shutdown;
- recover a retained `REAL_LOGIN` identity only after strict cleanup evidence;
- restart with zero implicitly active actors while profiles remain persistent.

No profile is automatically selected or materialized. Goal 007 scheduler and
Goal 016 population policy remain absent.

## 4. Architectural boundary

Goal 006 owns only production materialization lifecycle, profile-to-character
resolution, materialization cap, identity claim/recovery, action-admission
tokens, shutdown drain, lifecycle metrics and safe-unmaterialized restart.

It does not own schedules, population, activity levels, goals, Utility AI,
navigation, combat, economy, Semantic Pack or conversation.

## 5. Mandatory reading

Read fully:

1. `docs/PHANTOM_BOTS_ROADMAP.md`;
2. `PHANTOM_DEVELOPMENT_MASTER_PLAN.md`;
3. `Agents.md`;
4. workflow/package/report standards;
5. Tasks 004/004A/004B and Goal 005 packages/reports/reviews;
6. ADR 0001 and `PROFILE_PERSISTENCE_CONTRACT.md`;
7. current Phantom config/system/metrics/trace;
8. current identity, cleanup, headless output, spike and action-facade classes;
9. current profile package;
10. `GameClient`, `Disconnection`, autosave manager and current tests/build;
11. every document in this package.

## 6. Initial Git audit

```bat
git rev-parse --show-toplevel
git status --short --branch
git remote -v
git fetch origin --prune
git rev-parse HEAD
git rev-parse origin/feature/phantom-world
git show --stat --oneline 9d0465eb62f9913644fab9f1d60feb2f4fd9a674
git diff --name-status f5b66c4edf1ddf18e044ef8c692d70ecea616485..9d0465eb62f9913644fab9f1d60feb2f4fd9a674
```

Expected: `HEAD == origin/feature/phantom-world == 9d0465eb...`.
The extracted package is expected untracked scope. Preserve and exclude
unrelated `docs/agent-tasks/**`. Block on unreviewed production/schema drift.

## 7. Goal 005 closure

Update `docs/phantoms/reports/005-core-profile-persistence-envelope.md` with:

```text
Commit: 9d0465eb62f9913644fab9f1d60feb2f4fd9a674
Parent: f5b66c4edf1ddf18e044ef8c692d70ecea616485
Push/remote: exact
Profile suite: 18/18
Headless consecutive runs: 18/18 ×3
Final verifier: 69/69 ×2
Verifier outputs identical
Production DB: no access/mutation
Independent review: ACCEPT
Goal 006: ALLOWED
```

Create `docs/phantoms/reviews/005-core-profile-persistence-envelope-review.md`:

```text
Goal 005: ACCEPT
Revert: NOT_REQUIRED
Profile schema/repository: ACCEPT
Follow-ups carried into Goal 006:
- owned-row test cleanup
- value equality for component payload
Goal 006: ALLOWED
Goal 007: NOT_STARTED
```

Update roadmap progress facts only:

- accepted baseline becomes `9d0465eb...`;
- Goal 005 becomes `ACCEPT`;
- Goal 006 becomes `IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`;
- Goal 007 remains blocked;
- no future architecture or requirement rewrite.

## 8. Goal 005 bounded follow-ups

### 8.1. Ownership-scoped test cleanup

`PhantomProfilePersistenceSuite` must stop using unqualified
`DELETE FROM phantom_profiles`.

Track exact profile IDs created by the suite and remove only those rows. Add a
foreign sentinel profile before suite-owned cleanup, prove it survives every
owned cleanup, and remove it explicitly in final teardown.

Do not add a production repository API solely for tests.

### 8.2. Component value equality

Override `PhantomProfileComponent.equals/hashCode` so payloads compare through
`Arrays.equals/hashCode`, not array identity.

Tests:

- separately loaded equal snapshots are equal and have equal hash codes;
- different payload is unequal;
- defensive copies remain intact.

## 9. Config

Add one operator safety limit:

```text
MaxMaterializedPhantoms = 32
```

This is a configurable cap, not a population target.

`Settings` gains `int maxMaterializedPhantoms`.

Contract:

- disabled settings use cap `0`;
- disabled mode does not require/parse a valid cap into runtime work;
- enabled cap is strict base-10 `1..10000`;
- missing, blank, signed, zero, out-of-range or malformed enabled cap disables
  the entire Phantom settings fail-closed;
- diagnostics remain effective only when enabled;
- defaults stay false/false/32;
- no other lifecycle/population settings.

## 10. Shared production lifecycle core

Create `PhantomMaterializedPlayer.java` (or responsibility-equivalent) as the
single per-actor lifecycle implementation:

```text
STORED
CLAIMED
LOADING
MATERIALIZING
ACTIVE
DEMATERIALIZING
FAILED
```

It owns:

- PHANTOM identity lease;
- canonical `Player`;
- headless outbound attachment;
- action-admission tokens/count;
- retryable cleanup;
- immutable snapshot.

It does not own repository, global maps, cap, scheduler, gameplay policy or
fixture item behavior.

### No duplicated lifecycle

Refactor `PhantomPlayerMaterializationSpike` into a thin compatibility/test
wrapper over this core. The wrapper may provide existing failure-point mapping,
fixture baseline restoration and reversible fixture action.

Forbidden:

- copying lifecycle into a second class;
- production service using the spike;
- production code referencing `PhantomActionFacade.FIXTURE_ITEM_ID`.

All Task 004–004B tests remain green.

### Action admission

Expose a tokenized API equivalent to `ActionLease tryAcquireAction()`:

- only ACTIVE admits;
- close decrements exactly once;
- double/stale close safe;
- cleanup closes admission first and waits with timeout;
- no arbitrary public Runnable/Consumer/callback executor;
- no new thread/future.

## 11. Production materialization service

Create `PhantomMaterializationService.java` with no singleton. `PhantomSystem`
owns one instance.

Lifecycle:

```text
NEW / RUNNING / STOPPING / STOPPED / FAILED
```

Required behaviorally equivalent API:

```java
boolean start()
MaterializeResult materialize(long profileId)
DematerializeResult dematerialize(long profileId)
DematerializeResult retryCleanup(long profileId)
Optional<MaterializationSnapshot> find(long profileId)
List<MaterializationSnapshot> list()
ShutdownResult shutdown()
ServiceSnapshot snapshot()
```

Results distinguish at least:

```text
SUCCESS
SERVICE_NOT_RUNNING
PROFILE_NOT_FOUND
PROFILE_UNLINKED
ALREADY_ACTIVE
CHARACTER_ALREADY_ACTIVE
CAPACITY_REACHED
IDENTITY_BUSY
RETAINED_IDENTITY_NOT_RECOVERABLE
MATERIALIZATION_FAILED_RETAINED
CLEANUP_FAILED_RETAINED
NOT_ACTIVE
```

### Concurrency/cap

Use a bounded fair `Semaphore` or equivalent and exact conditional maps.

- no global lock held during `Player.load`, DB store/delete or World operations;
- one active entry per profile ID and character object ID;
- permit retained while failed cleanup retains resources;
- permit released only after terminal STORED;
- no per-profile executor/future;
- concurrent same-profile request yields exactly one owner.

### Profile resolution

Materialization order:

1. repository find;
2. require linked positive character ID;
3. reserve profile and character;
4. acquire cap;
5. recover only safe retained real identity if necessary;
6. claim PHANTOM identity;
7. `Player.load` and exact object-ID validation;
8. attach headless output;
9. explicit domain initialization;
10. online/spawn;
11. open admission;
12. publish ACTIVE.

No profile/character is automatically created. Link changes while ACTIVE do not
retarget the existing actor; snapshot keeps the captured character ID.

## 12. Retained REAL_LOGIN recovery

Implement `RECOVERY_CONTRACT.md` exactly.

Identity entries gain states equivalent to:

```text
RESERVED
RETAINED
```

Only matching REAL_LOGIN ownership may become RETAINED after failed/incomplete
cleanup. `Disconnection` marks it retained before references disappear.

Recovery is explicit/on-demand only. It may release exactly the same retained
entry only when all are true:

```text
owner kind REAL_LOGIN
entry state RETAINED
World.getPlayer(objectId) == null
World.findObject(objectId) == null
autosave.containsObjectId(objectId) == false
SELECT online FROM characters WHERE charId=? returns exactly one row and 0
```

Errors, missing/multiple row or nonzero online reject recovery. A RESERVED/live
real login is never recoverable. No periodic scan, age-based release or
unbounded retry.

The service may attempt recovery once when materializing the same character and
must also expose an explicit recovery result.

## 13. Cleanup/shutdown/restart

Cleanup order:

```text
close admission
→ drain action tokens
→ stop tasks
→ store
→ delete
→ object-ID postconditions
→ detach output
→ release identity
→ remove service maps
→ release permit
→ STORED
```

Failures before postconditions retain entry, identity, maps and permit.

`shutdown()`:

1. rejects new admissions/materializations;
2. iterates entries in stable profile-ID order;
3. attempts cleanup;
4. retries failed entries at most once immediately;
5. total drain timeout <=10s;
6. returns exact failed profile IDs;
7. persistent failure leaves service `FAILED` and resources retained;
8. second explicit shutdown may retry;
9. no retry task/thread.

No ACTIVE runtime state is persisted. After process restart, service maps and
identity registry are empty, profiles remain, active count is zero, and no
automatic materialization occurs.

## 14. PhantomSystem integration

Enabled startup order inside `PhantomSystem`:

```text
scheduler start
repository open/schema validation
materialization service start
RUNNING
```

Failure rolls back service/scheduler and prevents configured publication.

Disabled path creates no repository/service and performs no DB query.

Shutdown order:

```text
materialization drain
scheduler stop
STOPPED
```

`shutdownIfStarted()` clears configured instance only after terminal STOPPED.
A failed drain retains it for explicit retry.

Expose no automatic selection loop. Goal 007 will consume a narrow package-level
service accessor.

## 15. Metrics/diagnostics

Fixed counters only:

- materialization requested/succeeded/rejected;
- materialization failures retained;
- dematerialization succeeded;
- cleanup failures retained;
- retained recovery succeeded/rejected;
- shutdown failures;
- active current/peak.

No dynamic per-profile metric map. Existing optional bounded trace may record a
short event and profile ID only. No player text or per-action INFO logs.

## 16. Tests and Ant

Create:

```text
test/java/org/l2jmobius/tests/phantoms/PhantomProductionMaterializationSuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomProductionMaterializationPerformanceSuite.java
```

Launcher modes:

```text
production-materialization
production-materialization-performance
```

Ant targets:

```text
phantom-production-materialization-test
phantom-production-materialization-performance-smoke
phantom-static-verify-006
```

Forked JVMs, guarded test DB only. No network `GameClient`/`Connection`.

Required matrix is in `TEST_CASES.md`. Run production-materialization test three
independent times before final verify.

## 17. DB rules

No schema/migration change in Goal 006.

Production DB is never used during Codex execution.

Runtime DB access is allowed only when Phantom is explicitly enabled or an
explicit materialization/recovery request is made. Default disabled GameServer
performs zero profile/materialization queries.

Retained recovery uses a prepared online query and no writes. Runtime lifecycle
state is not written into profile components.

## 18. Exact scope

Allowed production:

```text
java/org/l2jmobius/gameserver/config/custom/PhantomPlayersConfig.java
dist/game/config/Custom/PhantomPlayers.ini
java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java
java/org/l2jmobius/gameserver/phantoms/PhantomMetrics.java
java/org/l2jmobius/gameserver/phantoms/PhantomDiagnosticTrace.java
java/org/l2jmobius/gameserver/phantoms/player/PhantomIdentityLeaseRegistry.java
java/org/l2jmobius/gameserver/phantoms/player/PhantomPlayerMaterializationSpike.java
java/org/l2jmobius/gameserver/phantoms/player/PhantomMaterializedPlayer.java
java/org/l2jmobius/gameserver/phantoms/player/PhantomMaterializationService.java
java/org/l2jmobius/gameserver/phantoms/player/PhantomRetainedIdentityRecovery.java
java/org/l2jmobius/gameserver/phantoms/player/** result/snapshot/support classes
java/org/l2jmobius/gameserver/network/GameClient.java
java/org/l2jmobius/gameserver/network/Disconnection.java
java/org/l2jmobius/gameserver/phantoms/profile/PhantomProfileComponent.java
```

Allowed tests/build:

```text
build.xml
test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java
test/java/org/l2jmobius/tests/phantoms/PhantomHeadlessPlayerSuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomProfilePersistenceSuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomProductionMaterializationSuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomProductionMaterializationPerformanceSuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomHeadlessPlayerTestEnvironment.java
tools/phantoms/verify-task-006.ps1
```

Allowed docs:

```text
docs/PHANTOM_BOTS_ROADMAP.md
docs/phantoms/architecture/MATERIALIZATION_LIFECYCLE_CONTRACT.md
docs/phantoms/tasks/006-production-materialization-lifecycle/**
docs/phantoms/reports/005-core-profile-persistence-envelope.md
docs/phantoms/reports/006-production-materialization-lifecycle.md
docs/phantoms/reviews/005-core-profile-persistence-envelope-review.md
```

Conditional: `PlayerAutoSaveTaskManager.java` only if existing object-ID query is
insufficient. Prefer no change.

`GameServer.java` and `Shutdown.java` are frozen; they already call
`PhantomSystem` at correct boundaries. No schema file may change.

## 19. Hard out of scope

Forbidden:

- profile schema/new component type/profile auto creation/character creation;
- Goal 007 activity scheduler, schedules or population;
- goals/AI/navigation/combat/economy/Semantic Pack/conversation;
- fake GameClient/Connection or request packets as internal API;
- per-phantom thread/future/executor;
- production DB execution;
- other chronicles/dependencies/CI/old verifier modifications;
- mass formatting, amend, rebase, merge commit or force push.

## 20. Static verifier

Create `tools/phantoms/verify-task-006.ps1` checking:

- base `9d0465eb...`, one ordinary commit and exact scope;
- no schema change or Goal 007;
- config false defaults and strict cap;
- disabled path no repository/service open;
- no lifecycle duplication and no fixture item in production core/service;
- no arbitrary action callback;
- bounded permit/maps and no global lock around slow operations;
- retained states, exact atomic recovery and prepared DB online query;
- no periodic recovery;
- bounded two-pass shutdown and retained configured instance on failure;
- no auto profile scan/materialization;
- fixed metrics/bounded trace;
- component value equality and owned-row test cleanup/sentinel;
- launcher/Ant targets/forking;
- Goal 005 closure and roadmap progress-only edits;
- UTF-8, mojibake, escaped Cyrillic, no credentials/binaries;
- deterministic read-only verifier.

## 21. Commands

Pre-change:

```bat
ant verify
powershell -NoProfile -ExecutionPolicy Bypass -File tools\phantoms\verify-task-005.ps1
```

Targeted:

```bat
ant compile-tests
ant phantom-profile-persistence-test
ant phantom-headless-player-test
ant phantom-production-materialization-test
ant phantom-production-materialization-performance-smoke
ant phantom-db-test
ant test
ant phantom-skeleton-test
```

Three independent production lifecycle runs:

```bat
ant phantom-production-materialization-test
ant phantom-production-materialization-test
ant phantom-production-materialization-test
```

Full:

```bat
ant verify
ant jar
powershell -NoProfile -ExecutionPolicy Bypass -File tools\phantoms\verify-task-006.ps1
git diff --check
git status --short --branch
```

Post-commit:

```bat
git rev-parse HEAD
git show --stat --oneline --decorate HEAD
git diff --check 9d0465eb62f9913644fab9f1d60feb2f4fd9a674...HEAD
ant verify
ant jar
powershell -NoProfile -ExecutionPolicy Bypass -File tools\phantoms\verify-task-006.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File tools\phantoms\verify-task-006.ps1
git push origin feature/phantom-world
git ls-remote --heads origin feature/phantom-world
git status --short --branch
```

Compare final verifier output byte-for-byte/SHA-256 outside the repo.

## 22. Report

Create `docs/phantoms/reports/006-production-materialization-lifecycle.md` with:

- status/baseline/Goal 005 closure/roadmap progress;
- config/cap;
- shared lifecycle extraction and spike compatibility;
- service API/state/concurrency/profile resolution;
- identity/retained recovery;
- action admission and cleanup/retry/shutdown;
- restart contract;
- metrics/diagnostics;
- DB and disabled behavior;
- owned-row/value-equality follow-ups;
- tests, three runs and one/ten performance;
- residue, verify/jar/verifier, scope/deviations/limitations;
- manual gate `PENDING_INDEPENDENT_REVIEW`;
- Goal 007 `NOT_STARTED`.

Use external-handoff wording for self SHA/push.

## 23. Result, commit and blocking

Success result:

```text
PRODUCTION_MATERIALIZATION_LIFECYCLE_IMPLEMENTED_PENDING_INDEPENDENT_REVIEW
```

Commit subject:

```text
feat(phantoms): add production materialization lifecycle
```

One ordinary commit on top of `9d0465eb...`. Push regardless of success/blocker,
with safe scoped artifacts only.

Block if lifecycle is duplicated, live RESERVED identity can be recovered,
cleanup failure releases resources, persistent shutdown failure reports STOPPED,
disabled path opens DB, cap/ownership is bypassable, production DB is used,
Goal 007 leaks in, or verify/jar is not GREEN.

## 24. Final handoff

```text
Status:
Architecture result:
Baseline:
Goal 005 closure:
Roadmap progress:
Config defaults/cap:
Disabled DB queries:
Shared lifecycle:
Spike compatibility:
Service state/API:
Concurrent same-profile winners:
Capacity gate:
Missing/unlinked profile:
Canonical materialization:
Action admission:
Cleanup failure retention:
Retry cleanup:
Shutdown drain:
Persistent shutdown failure:
Second shutdown retry:
Retained REAL_LOGIN clean recovery:
Retained REAL_LOGIN residue rejection:
RESERVED login recovery rejection:
Restart active count:
Profile rows retained:
Metrics/trace:
Goal 005 owned cleanup:
Component value equality:
Production materialization tests:
Three consecutive runs:
Performance one/ten:
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
Goal 007:
Limitations/blockers:
```
