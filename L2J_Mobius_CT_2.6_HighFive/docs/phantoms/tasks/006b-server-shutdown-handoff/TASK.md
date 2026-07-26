# GOAL 006B — Server shutdown handoff

## 1. Identifier

- **Task ID:** `006b-server-shutdown-handoff`
- **Type:** final bounded server-integration closure for Goal 006
- **Branch:** `feature/phantom-world`
- **Starting baseline:** `c2f5599ef59cabb1eeff1f3d467f57219d5a1f5f`
- **Parent:** `ff0b33abad0affc4fe64b4324aee67f256dc96fa`
- **Repository root:** `C:\Users\endim\L2J_Mobius\`
- **Only module:** `C:\Users\endim\L2J_Mobius\L2J_Mobius_CT_2.6_HighFive\`
- **Test DB:** `l2jmobiush5_phantom_test`
- **Production DB:** `l2jmobiush5` — never use during Codex execution
- **Seed:** `20260725001`
- **Codex model:** Sol
- **Effort:** Very High

## 2. Independent review gate

```text
Goal 005: ACCEPT
Goal 006 architecture: ACCEPT
Goal 006A local hardening: ACCEPT
Goal 006 server shutdown integration: FIX_REQUIRED
Revert: NOT_REQUIRED
Goal 006B: REQUIRED
Goal 007: BLOCKED
```

Accepted Goal 006/006A work must remain:

- profile-backed canonical Player materialization;
- one shared per-actor lifecycle core;
- fair capacity and profile/character reservations;
- exact World/autosave identity boundaries;
- action admission atomic with service STOPPING;
- retryable cleanup retaining identity/maps/permit;
- retained REAL_LOGIN recovery;
- one tracked service-level drain attempt;
- wall-clock-bounded shutdown caller;
- safe unmaterialized restart;
- fixed metrics/trace;
- all Task 004–Goal 005 regressions.

This task changes only the real GameServer shutdown handoff.

## 3. Root cause

Current `Shutdown.startShutdownActions()` performs:

```text
disconnectAllCharacters()
...
PhantomSystem.shutdownIfStarted()
ThreadPool.shutdown()
```

This is unsafe for production Phantom actors:

1. generic `disconnectAllCharacters()` directly calls `Disconnection` on
   headless Phantom Players before the owning materialization service closes
   action admission or removes its maps/permit;
2. if the later Phantom service drain returns `FAILED` because its caller timed
   out, `Shutdown` immediately invokes `ThreadPool.shutdown()`;
3. that stops/interupts the tracked drain command introduced by Goal 006A;
4. the configured instance is retained in memory but no explicit retry is called
   before the shared ThreadPool disappears.

The Goal 006A unit/integration tests prove the service contract in isolation,
but not this actual server shutdown ordering.

## 4. Goal

Implement and prove one coordinated shutdown handoff:

1. first Phantom drain attempt begins before generic player disconnection;
2. generic `disconnectAllCharacters()` never directly cleans a Player currently
   owned by the Phantom materialization lifecycle;
3. ordinary real players and ordinary offline players preserve existing
   disconnection behavior;
4. a second explicit Phantom shutdown observation/retry occurs immediately
   before `ThreadPool.shutdown()`;
5. the second attempt reuses an in-flight drain or starts one retry according to
   the accepted service contract;
6. shared ThreadPool is stopped only after that second bounded attempt returns;
7. persistent failure is logged clearly and never reported as successful
   Phantom shutdown;
8. no concurrent generic/service cleanup occurs;
9. all Goal 004–006A tests remain GREEN;
10. Goal 007 remains `NOT_STARTED`.

## 5. Mandatory reading

Read fully:

- `docs/PHANTOM_BOTS_ROADMAP.md`;
- `PHANTOM_DEVELOPMENT_MASTER_PLAN.md`;
- `Agents.md`;
- Goal 006 and Goal 006A packages/reports/reviews/contracts;
- current:
  - `Shutdown.java`;
  - `PhantomSystem.java`;
  - `PhantomMaterializationService.java`;
  - `PhantomMaterializedPlayer.java`;
  - `PhantomIdentityLeaseRegistry.java`;
  - `Player.java`;
  - `Disconnection.java`;
  - production materialization suites;
  - `verify-task-006a.ps1`;
- all files in this package.

## 6. Initial Git audit

```bat
git rev-parse --show-toplevel
git status --short --branch
git remote -v
git fetch origin --prune
git rev-parse HEAD
git rev-parse origin/feature/phantom-world
git show --stat --oneline c2f5599ef59cabb1eeff1f3d467f57219d5a1f5f
git diff --name-status ff0b33abad0affc4fe64b4324aee67f256dc96fa..c2f5599ef59cabb1eeff1f3d467f57219d5a1f5f
```

Expected:

```text
HEAD == origin/feature/phantom-world == c2f5599e...
```

Preserve and exclude unrelated `docs/agent-tasks/**`.

Return `BLOCKED_BASELINE_DRIFT` for unreviewed production drift.

## 7. Fixed architecture

Detailed contract: `SHUTDOWN_HANDOFF.md`.

### 7.1. Managed-player classification

Add one narrow static query to `PhantomSystem`, or an equivalent production
helper:

```java
public static boolean isMaterializationManaged(Player player)
```

Required true conditions:

```text
player != null
player has headless outbound session
identity registry owner for player.objectId == PHANTOM
configured PhantomSystem instance exists
configured materialization service owns the same character object ID
```

The service must expose only a read-only exact query equivalent to:

```java
boolean ownsCharacterObjectId(int objectId)
```

It checks the current exact active-entry map and must not expose the Entry,
Player, repository or mutable collections.

Fail closed:

- a Player is skipped from generic disconnect only when all conditions prove
  service ownership;
- ordinary offline play/trade, detached real clients and unowned headless test
  objects are not skipped;
- the query starts no task and performs no DB/World work.

### 7.2. First Phantom shutdown phase

In `Shutdown.startShutdownActions()`:

```text
before disconnectAllCharacters()
→ call PhantomSystem.shutdownIfStarted()
```

Record the result:

- terminal stop: log successful drain;
- configured instance remains: log a bounded warning that a tracked drain or
  retained cleanup is still pending;
- no configured instance: no-op.

This call occurs while ThreadPool and DatabaseFactory are fully available.

### 7.3. Generic disconnect exclusion

Modify only `disconnectAllCharacters()`:

```java
for (Player player : World.getInstance().getPlayers())
{
    if (PhantomSystem.isMaterializationManaged(player))
    {
        continue;
    }
    Disconnection.of(player).storeAndDeleteWith(ServerClose.STATIC_PACKET);
}
```

Requirements:

- take a stable snapshot if the existing collection semantics require it;
- no direct service cleanup is called inside the loop;
- no PHANTOM actor is passed to `Disconnection`;
- ordinary player behavior remains byte-for-byte/behaviorally unchanged;
- skipped count may be recorded only as one aggregate shutdown log, never per
  actor;
- no list of IDs retained after shutdown.

### 7.4. Final Phantom shutdown phase

Immediately before `ThreadPool.shutdown()`:

```text
if PhantomSystem still has configured instance
→ call shutdownIfStarted() a second time
```

Accepted service behavior means this call:

- observes terminal late completion;
- reuses an in-flight DrainAttempt;
- or starts one explicit retry after a completed failed attempt.

Result handling:

- if terminal `STOPPED`: clear configured instance and log success;
- if instance remains: log `SEVERE` with aggregate retained actor count and
  explicitly state that shared ThreadPool is about to stop with incomplete
  Phantom drain;
- never print the old “skeleton shut down” success message on failure.

Do not loop indefinitely. Exactly two server-level calls are allowed.

### 7.5. State and count diagnostics

Add narrow static immutable status, or equivalent:

```java
ConfiguredShutdownSnapshot
(
    boolean configured,
    PhantomSystem.State systemState,
    PhantomMaterializationService.ServiceState serviceState,
    int retainedEntries
)
```

It must:

- allocate only when explicitly requested during shutdown/tests;
- return zero/absent when no configured instance;
- expose no profile IDs;
- perform no DB access;
- allow `Shutdown` to emit one aggregate warning/severe message.

Do not add periodic logging.

### 7.6. Failure semantics

If the final call still fails:

- do not clear configured instance before ThreadPool shutdown;
- do not invoke generic `Disconnection` on the managed actors;
- do not release identity/maps/permit;
- do not claim successful Phantom shutdown;
- allow normal process termination to continue after the explicit severe
  diagnostic.

This task cannot guarantee completion of an indefinitely blocked canonical DB
operation. Its safety guarantee is:

```text
no concurrent cleanup
+ two bounded service opportunities while ThreadPool is alive
+ fail-closed retained ownership
+ explicit terminal diagnostic
```

Do not write `characters.online=0` directly and do not invent a force-delete
fallback.

## 8. Automated tests

Create or extend one focused suite:

```text
test/java/org/l2jmobius/tests/phantoms/PhantomServerShutdownHandoffSuite.java
```

Launcher mode:

```text
server-shutdown-handoff
```

Ant target:

```text
phantom-server-shutdown-handoff-test
```

Do not instantiate full GameServer or call `System.exit`.

### Required executable tests

#### Managed classifier

- active production materialized Player → true;
- ordinary loaded Player → false;
- detached/offline real Player → false;
- headless Player with no PHANTOM owner → false;
- PHANTOM lease without configured-service character ownership → false;
- after service cleanup → false.

Use a controlled configured-system test seam rather than reflection mutation of
private fields where practical. Any seam must be package-private/test-only
factory behavior and must not weaken production `startConfigured()`.

#### Two-phase coordinator policy

Extract a small package-private/static shutdown coordinator method from
`Shutdown`, or test an equivalent pure helper, so tests can prove:

- first call happens before generic disconnect phase;
- managed players are excluded;
- second call happens before ThreadPool stop phase;
- exactly two calls maximum;
- final failure produces failure status, not success.

No full manager shutdown is executed.

#### In-flight drain integration

- configure a production service with one materialized Player;
- block its cleanup before store;
- first system shutdown returns incomplete within its wall-clock budget;
- managed classifier remains true;
- simulated generic disconnect selection excludes that Player;
- release block;
- second system shutdown reaches STOPPED;
- classifier becomes false;
- no World/autosave/lease/permit/thread residue.

#### Persistent failure

- both server-level shutdown opportunities return incomplete;
- classifier remains true;
- no generic `Disconnection` invocation is recorded for the actor;
- configured snapshot reports retained entry;
- test then removes the fault and performs explicit cleanup in teardown;
- no new executor/raw thread.

### Static ordering verification

`verify-task-006b.ps1` must prove in actual `Shutdown.java`:

```text
first Phantom shutdown call
<
disconnectAllCharacters call
<
second Phantom shutdown call
<
ThreadPool.shutdown call
```

It must also prove the generic loop contains the managed-player guard before
`Disconnection.of(player)`.

### Regression

- Goal 006A production materialization test 19/19;
- three independent production runs;
- headless/profile/server shutdown suite;
- Task 004 failure matrix;
- performance;
- cumulative verify/jar.

## 9. Scope

Allowed production:

```text
java/org/l2jmobius/gameserver/Shutdown.java
java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java
java/org/l2jmobius/gameserver/phantoms/player/PhantomMaterializationService.java
```

Allowed tests/build:

```text
build.xml
test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java
test/java/org/l2jmobius/tests/phantoms/PhantomServerShutdownHandoffSuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomProductionMaterializationSuite.java
tools/phantoms/verify-task-006b.ps1
```

Allowed docs:

```text
docs/PHANTOM_BOTS_ROADMAP.md
docs/phantoms/architecture/MATERIALIZATION_LIFECYCLE_CONTRACT.md
docs/phantoms/architecture/SERVER_SHUTDOWN_HANDOFF.md
docs/phantoms/tasks/006b-server-shutdown-handoff/**
docs/phantoms/reports/006a-materialization-boundary-hardening.md
docs/phantoms/reports/006b-server-shutdown-handoff.md
docs/phantoms/reviews/006a-materialization-boundary-hardening-review.md
```

## 10. Hard out of scope

Forbidden:

- DB schema/migrations/config;
- `Player.java`;
- `Disconnection.java`;
- `World.java`;
- identity registry/recovery truth table;
- profile repository/model;
- packet seam;
- automatic materialization;
- activity scheduler/population/AI/Goal 007;
- navigation/combat/economy/conversation;
- new executor/raw thread;
- per-profile task/future;
- production DB execution;
- other chronicles;
- dependencies/CI;
- old verifier modification;
- mass formatting;
- amend/rebase/merge/force push.

## 11. Static verifier Goal 006B

Create:

```text
tools/phantoms/verify-task-006b.ps1
```

It must verify:

- base `c2f5599e...`;
- one ordinary commit;
- exact scope;
- no schema/config/Goal 007;
- all Goal 006A production files frozen except exact allowed files;
- two shutdown calls in correct source order;
- first call before generic disconnect;
- second call before ThreadPool shutdown;
- managed-player guard before Disconnection call;
- exactly two server-level Phantom shutdown invocations;
- no direct service cleanup inside generic loop;
- exact service character ownership query is read-only;
- configured shutdown snapshot is bounded and has no IDs/DB;
- no new executor/raw thread/per-profile future;
- failure logging cannot use success wording;
- focused launcher/Ant target;
- Goal 006A review and Goal 006B report;
- roadmap progress-only edits;
- UTF-8, mojibake, escaped Cyrillic;
- no credentials/binaries;
- verifier deterministic/read-only.

## 12. Documentation

Create:

```text
docs/phantoms/reviews/006a-materialization-boundary-hardening-review.md
docs/phantoms/reports/006b-server-shutdown-handoff.md
docs/phantoms/architecture/SERVER_SHUTDOWN_HANDOFF.md
```

Update Goal 006A report with immutable handoff:

```text
Commit: c2f5599ef59cabb1eeff1f3d467f57219d5a1f5f
Parent: ff0b33abad0affc4fe64b4324aee67f256dc96fa
Push/remote: exact
Production tests: 19/19 ×3
Blocked caller measurements: 150.33–151.73 ms
Final verifier: 81/81 ×2
Verifier SHA-256:
8F459EEEB37EBF368DC6FB7E1826CDAA38B3249A469FB906D1F29220D77174C8
Independent local-hardening review: ACCEPT
Server integration review: FIX_REQUIRED
Goal 006B: REQUIRED
```

Review verdict:

```text
Goal 006A local boundary hardening: ACCEPT
Goal 006 overall: FIX_REQUIRED pending 006B
Revert: NOT_REQUIRED
Goal 007: BLOCKED
```

Roadmap progress only:

```text
Goal 006A: ACCEPT
Goal 006B: IMPLEMENTED_PENDING_INDEPENDENT_REVIEW
Goal 006 overall: FIX_REQUIRED pending 006B
Goal 007: NOT_STARTED / BLOCKED
```

Do not rewrite future GOAL architecture.

## 13. Commands

Pre-change:

```bat
ant verify
powershell -NoProfile -ExecutionPolicy Bypass -File tools\phantoms\verify-task-006a.ps1
```

Targeted:

```bat
ant compile-tests
ant phantom-server-shutdown-handoff-test
ant phantom-production-materialization-test
ant phantom-headless-player-test
ant phantom-profile-persistence-test
ant phantom-db-test
ant test
ant phantom-skeleton-test
```

Three production runs:

```bat
ant phantom-production-materialization-test
ant phantom-production-materialization-test
ant phantom-production-materialization-test
```

Run shutdown-handoff test three times:

```bat
ant phantom-server-shutdown-handoff-test
ant phantom-server-shutdown-handoff-test
ant phantom-server-shutdown-handoff-test
```

Full:

```bat
ant verify
ant jar
powershell -NoProfile -ExecutionPolicy Bypass -File tools\phantoms\verify-task-006b.ps1
git diff --check
git status --short --branch
```

Post-commit:

```bat
git rev-parse HEAD
git show --stat --oneline --decorate HEAD
git diff --check c2f5599ef59cabb1eeff1f3d467f57219d5a1f5f...HEAD
ant verify
ant jar
powershell -NoProfile -ExecutionPolicy Bypass -File tools\phantoms\verify-task-006b.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File tools\phantoms\verify-task-006b.ps1
git push origin feature/phantom-world
git ls-remote --heads origin feature/phantom-world
git status --short --branch
```

Compare final verifier outputs byte-for-byte/SHA-256 outside the repository.

## 14. Acceptance

Critical gates:

1. first Phantom drain before generic disconnect;
2. generic disconnect skips only proven managed Phantom Players;
3. ordinary player disconnect behavior unchanged;
4. second Phantom shutdown before ThreadPool shutdown;
5. exactly two server-level opportunities;
6. in-flight drain is reused, not duplicated;
7. no generic/service concurrent cleanup;
8. persistent failure logged as severe/incomplete;
9. configured instance retained on failure;
10. all Goal 006A regressions pass;
11. production DB untouched;
12. Goal 007 not started;
13. ordinary commit/push and remote exact.

## 15. Result and commit

Successful result:

```text
SERVER_SHUTDOWN_HANDOFF_IMPLEMENTED_PENDING_INDEPENDENT_REVIEW
```

Commit subject:

```text
fix(phantoms): coordinate server shutdown handoff
```

One ordinary commit on top of `c2f5599e...`.

## 16. Blocking behavior

Return `BLOCKED` if:

- managed Phantom ownership cannot be proven without changing Player/identity
  semantics;
- two-phase ordering requires a new executor or unbounded loop;
- ordinary player disconnect behavior changes;
- Goal 007/schema/config scope is required;
- cumulative verify/jar fails.

On blocker, remove unsafe production edits, preserve safe evidence, commit/push,
and keep Goal 007 blocked.

## 17. Final handoff

```text
Status:
Architecture result:
Baseline:
Goal 006A closure:
First Phantom shutdown position:
Managed-player classifier:
Ordinary player disconnect:
Generic Phantom skip:
Second Phantom shutdown position:
ThreadPool shutdown ordering:
In-flight drain reuse:
Persistent failure diagnostic:
Configured instance retention:
Shutdown handoff tests:
Three shutdown-handoff runs:
Production materialization tests:
Three production runs:
Headless/profile regressions:
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
