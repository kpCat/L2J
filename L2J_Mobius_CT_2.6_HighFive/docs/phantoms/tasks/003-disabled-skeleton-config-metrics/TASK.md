# TASK 003 — Disabled-by-default каркас Phantom World, config, lifecycle, metrics

## Идентификатор

- Task ID: `003-disabled-skeleton-config-metrics`
- Branch: `feature/phantom-world`
- Accepted baseline: `84f29a0002b25d2b1ff1a19fa9c92867479fd6a5`
- Parent: `36e5411e01e8e73f8a0fd4d9460e327c28a6798b`
- Module: `C:\Users\endim\L2J_Mobius\L2J_Mobius_CT_2.6_HighFive\`
- Canonical config: `dist/game/config/Custom/PhantomPlayers.ini`
- Seed: `20260725001`
- Codex model: Sol
- Effort: Very High

## Accepted gates

```text
Task 001: ACCEPT
Task 001A: ACCEPT
Task 002: FIX REQUIRED, closed by Task 002A
Task 002A code/safety verdict: ACCEPT
Task 003: ALLOWED
ADR 0001: Proposed
Task 004: NOT_STARTED
```

Task 002A closed ownership-safe locking, schema freshness, partial-beforeAll cleanup,
JDBC query filtering and secret redaction. Task 003 must preserve those gates.

## Goal

Create the smallest production Phantom World skeleton that:

1. loads through existing `ConfigLoader`;
2. uses canonical `PhantomPlayers.ini`;
3. is disabled by default and fail-closed;
4. integrates deterministically into GameServer startup/shutdown;
5. when disabled creates no runtime instance, queue, trace, task, thread, DB or network work;
6. when enabled creates only an inert lifecycle skeleton:
   - one shared bounded queue without worker;
   - fixed aggregate counters;
   - optional bounded sampled in-memory trace;
   - zero scheduled tasks;
   - zero players/profiles/actions;
7. is covered by JDK-only Ant tests;
8. does not start Task 004.

## Read before editing

Read fully:

- `PHANTOM_DEVELOPMENT_MASTER_PLAN.md`
- `Agents.md`
- workflow/package/report standards under `docs/phantoms`
- `NEXT_TASK_GATES.md`
- ADR 0001
- Task 002 and 002A packages/reports/review
- current `GameServer.java`, `Shutdown.java`, `ConfigLoader.java`
- `FakePlayersConfig.java`, `FakePlayers.ini`
- current `build.xml` and test harness
- all files in this Task 003 package.

## Initial Git audit

```bat
git rev-parse --show-toplevel
git status --short --branch
git remote -v
git fetch origin --prune
git rev-parse HEAD
git rev-parse origin/feature/phantom-world
git show --stat --oneline 84f29a0002b25d2b1ff1a19fa9c92867479fd6a5
```

Expected:

```text
HEAD == origin/feature/phantom-world == 84f29a...
```

The extracted Task 003 package is expected untracked scope. Preserve and exclude
all unrelated `docs/agent-tasks/**`. If drift contains Task 004, Player/GameClient
seam work or unrelated production changes, return `BLOCKED`.

## Close Task 002A documentation

Update `docs/phantoms/reports/002a-test-infrastructure-safety-hotfix.md` with:

```text
Commit: 84f29a0002b25d2b1ff1a19fa9c92867479fd6a5
Parent: 36e5411e01e8e73f8a0fd4d9460e327c28a6798b
Push: successful
Remote ref: exact
Final verifier 1: 52/52
Final verifier 2: 52/52
Outputs identical SHA-256:
3DEBD45D104620BE262FC6AE83A0A9244F80D9D409E9FEA504DF0EA815E0249E
Independent review: ACCEPT
```

Remove its post-commit placeholders.

Update `docs/phantoms/reviews/002-automated-test-infrastructure-review.md`:

```text
Original Task 002 implementation: FIX REQUIRED
Task 002A closure: ACCEPT
Combined Task 002 test infrastructure: ACCEPT
Task 003: ALLOWED
Task 004: NOT_STARTED
```

Keep original findings and evidence.

## Exact production architecture

### PhantomPlayersConfig

Create:

```text
java/org/l2jmobius/gameserver/config/custom/PhantomPlayersConfig.java
dist/game/config/Custom/PhantomPlayers.ini
```

Required contract:

- final config utility;
- public canonical config path constant;
- immutable `Settings` record;
- volatile current settings initialized disabled;
- `load()` for production;
- `read(Path)` for deterministic tests without mutating global state;
- `settings()` and `isEnabled()`;
- strict booleans: only trimmed `true`/`false`, case-insensitive;
- missing, blank or malformed values become false;
- diagnostics effective only when system enabled;
- missing file returns disabled settings;
- optional Phantom config must not abort normal server startup;
- no environment, DB or system-property input.

Keys only:

```text
EnablePhantomSystem
EnablePhantomDiagnostics
```

Do not add population, rates, classes, locations, economy or AI settings.

Canonical file defaults:

```text
EnablePhantomSystem = False
EnablePhantomDiagnostics = False
```

Comments must state that this is an inert skeleton and production should remain
disabled until later gates.

### ConfigLoader

Add one import and one `PhantomPlayersConfig.load()` in the custom config block.
Do not reorder or rewrite unrelated calls.

### Production package

Create only:

```text
java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java
java/org/l2jmobius/gameserver/phantoms/PhantomScheduler.java
java/org/l2jmobius/gameserver/phantoms/PhantomMetrics.java
java/org/l2jmobius/gameserver/phantoms/PhantomDiagnosticTrace.java
```

One extra class is allowed only for a clearly separated responsibility.

Forbidden imports/references in this package:

```text
Player
World
GameClient
clientpackets
serverpackets
ConnectionManager
DatabaseFactory
java.sql
javax.sql
ThreadPool
Thread
Executor
ScheduledFuture
NPC/FakePlayer
```

No persistence, file I/O, AI, navigation, LLM or network.

### PhantomSystem

Required API or behaviorally equivalent:

```java
enum State { NEW, DISABLED, RUNNING, STOPPED }

public PhantomSystem(PhantomPlayersConfig.Settings settings)
public synchronized boolean start()
public synchronized boolean shutdown()
public synchronized Snapshot snapshot()

public static synchronized boolean startConfigured()
public static synchronized boolean shutdownIfStarted()
```

Rules:

- public direct constructor exists for tests;
- disabled `startConfigured()` returns false and does not create a configured instance;
- disabled path does not log or allocate queue/trace;
- enabled path creates at most one configured instance;
- enabled start reaches RUNNING but schedules nothing;
- direct disabled start reaches DISABLED with zero metrics;
- STOPPED is terminal;
- repeated start/stop do not duplicate transitions/counters;
- `shutdownIfStarted()` never creates an instance and clears configured reference.

Technical defaults may be constants, not gameplay quantities. Preferred:

```text
queue capacity 256
trace capacity 64
trace sampleEvery 16
```

### PhantomScheduler

- one shared bounded `ArrayBlockingQueue<Runnable>` or equivalent;
- no worker or consumer;
- no execution of Runnable;
- `start()` changes state only;
- `offer()` works only while running, is capacity bounded, and updates metrics;
- `stop()` marks stopped and clears queue;
- snapshot contains running, queued, capacity and `scheduledTaskCount=0`;
- no production Task 003 caller submits work.

### PhantomMetrics

Fixed-memory counters only, using fixed `AtomicLong` fields or fixed array:

- lifecycle starts/stops;
- queue accepted/rejected;
- trace recorded/dropped.

Immutable snapshot. No dynamic map keyed by phantom/action, no exporter, timer or
periodic logging. Disabled path remains all-zero.

### PhantomDiagnosticTrace

- disabled by default;
- disabled instance allocates no ring array;
- enabled ring has fixed capacity and positive deterministic sample interval;
- overwrite oldest when full and increment dropped counter;
- stores short internal event names only;
- no player text, credentials or chat;
- snapshot copies bounded data only on request;
- no logs or background task.

## GameServer integration

Minimal changes only.

After `ThreadPool.init()` and before `IdManager`:

```text
if config enabled:
    print Phantom World section
    PhantomSystem.startConfigured()
```

Disabled path must not create Phantom instance or print a Phantom section.
Enabled start failure must fail startup, not leave partial state. Do not move
other startup calls and do not place Phantom start after network listeners.

## Shutdown integration

In `Shutdown.startShutdownActions()`, immediately before `ThreadPool.shutdown()`:

```text
PhantomSystem.shutdownIfStarted()
```

- local try/catch in existing style;
- log only if a real started instance was stopped;
- disabled path creates nothing;
- no DB save or player iteration.

## Tests

Create:

```text
test/java/org/l2jmobius/tests/phantoms/PhantomSkeletonSuite.java
```

Add explicit launcher mode `skeleton`.

Minimum tests:

### Config
- canonical file is disabled;
- missing file disabled;
- blank/malformed disabled;
- `True` recognized;
- malformed diagnostics false;
- diagnostics true with system false becomes false;
- misspelled key cannot enable.

### Disabled
- configured start false and no instance;
- direct disabled instance state DISABLED;
- queue inactive/zero;
- scheduled tasks zero;
- metrics zero;
- trace empty;
- no new non-daemon thread;
- shutdown idempotent.

### Enabled
- RUNNING;
- starts exactly 1;
- queue empty;
- scheduled tasks zero;
- repeat start no-op;
- shutdown STOPPED;
- stops exactly 1;
- repeat stop no-op;
- cannot restart after STOPPED.

### Queue
- reject before start;
- accept only to capacity;
- next rejected;
- Runnable body never executes;
- stop clears;
- metrics exact;
- no thread/future.

### Trace
- disabled has no entries;
- deterministic sampling;
- bounded capacity;
- overwrite/drop exact;
- bounded snapshot.

Do not start GameServer/LoginServer in tests.

## Ant

Modify `build.xml` minimally:

- add `phantom-skeleton-test`;
- add `phantom-static-verify-003`;
- add both to cumulative `verify`;
- preserve every Task 002A target;
- all Java runs forked.

## Scope

Allowed only:

```text
build.xml
java/org/l2jmobius/gameserver/GameServer.java
java/org/l2jmobius/gameserver/Shutdown.java
java/org/l2jmobius/gameserver/config/ConfigLoader.java
java/org/l2jmobius/gameserver/config/custom/PhantomPlayersConfig.java
java/org/l2jmobius/gameserver/phantoms/**
dist/game/config/Custom/PhantomPlayers.ini
test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java
test/java/org/l2jmobius/tests/phantoms/PhantomSkeletonSuite.java
tools/phantoms/verify-task-003.ps1
docs/phantoms/tasks/003-disabled-skeleton-config-metrics/**
docs/phantoms/reports/002a-test-infrastructure-safety-hotfix.md
docs/phantoms/reports/003-disabled-skeleton-config-metrics.md
docs/phantoms/reviews/002-automated-test-infrastructure-review.md
```

## Hard out of scope

- other chronicles;
- master plan, Agents, ADR;
- DatabaseConfig/DatabaseFactory;
- Task 002/002A guard, provisioner, lock, manifest or migrations;
- SQL and `.gitignore`;
- old verifiers and `prepare-test-db.ps1`;
- Player/GameClient/packets/World;
- existing Fake Players/NPC changes;
- DB tables, profiles or characters;
- scheduler worker/thread/future;
- actions, AI, navigation, headless seam;
- Task 004;
- dependencies, CI, JUnit/Maven/Gradle;
- manual GameServer runtime;
- amend/rebase/force push;
- mass formatting.

## Safety invariants

1. at most one configured runtime;
2. disabled creates none;
3. queue fixed capacity;
4. no worker/task/thread/future;
5. stop clears queue;
6. idempotent lifecycle;
7. STOPPED terminal;
8. shutdown before ThreadPool;
9. fixed metrics memory;
10. fixed trace memory;
11. no hot-path logs;
12. no DB/network/Player state.

Task 003 values:

```text
production DB access/read/mutation: false
test DB schema mutation: false
Phantom network activity: false
```

The existing DB integration suite may read only the isolated test DB during
aggregate `ant verify`.

## Static verifier

Create `tools/phantoms/verify-task-003.ps1`.

It must check:

- base `84f29a...`, branch and one-commit shape;
- exact scope and High Five only;
- required files;
- no Task 004;
- no forbidden DB/network/Player imports;
- no thread/executor/ThreadPool/schedule in Phantom package;
- canonical config and false defaults;
- strict parser contract;
- ConfigLoader call;
- GameServer order: ConfigLoader → Database → ThreadPool → guarded Phantom → IdManager;
- disabled guard prevents section logging;
- Shutdown Phantom stop before ThreadPool shutdown;
- bounded queue, fixed metrics and bounded trace tokens;
- launcher mode and Ant targets;
- Task 002A provenance closure;
- Task 003 report headings;
- old safety files/verifiers unchanged;
- UTF-8, mojibake, escaped Cyrillic;
- no credentials/binaries;
- ordinal deterministic output;
- verifier itself performs no DB/network/write.

## Commands

Targeted:

```bat
ant compile-tests
ant test
ant phantom-skeleton-test
ant phantom-negative-control
ant phantom-db-guard-negative-control
ant phantom-provisioning-lock-control
ant phantom-schema-freshness-negative-control
ant phantom-lifecycle-negative-control
ant phantom-db-test
ant phantom-scenario-test
ant phantom-performance-smoke
```

Full:

```bat
ant verify
ant jar
powershell -NoProfile -ExecutionPolicy Bypass -File tools\phantoms\verify-task-003.ps1
git diff --check
git status --short --branch
```

Do not provision unless the existing isolated DB/config/manifest is missing or
stale. If provisioning is necessary, use environment-only admin credentials and
state why.

Post-commit:

```bat
git rev-parse HEAD
git show --stat --oneline --decorate HEAD
git diff --check 84f29a0002b25d2b1ff1a19fa9c92867479fd6a5...HEAD
ant verify
ant jar
powershell -NoProfile -ExecutionPolicy Bypass -File tools\phantoms\verify-task-003.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File tools\phantoms\verify-task-003.ps1
git push origin feature/phantom-world
git ls-remote --heads origin feature/phantom-world
git status --short --branch
```

Compare final verifier outputs byte-for-byte/SHA-256 outside the repo.

## Report

Create:

```text
docs/phantoms/reports/003-disabled-skeleton-config-metrics.md
```

Required sections:

- Status and baseline;
- Task 002A closure;
- changed files;
- config/fail-closed behavior;
- disabled/enabled skeleton behavior;
- lifecycle ordering;
- queue/scheduled tasks;
- metrics/trace;
- DB/network safety;
- concurrency/memory;
- tests and counts;
- Ant targets, verify and jar;
- static verifier;
- scope, commands, deviations, limitations;
- branch/parent/subject;
- manual gate `PENDING_INDEPENDENT_REVIEW`;
- Task 004 `NOT_STARTED`.

Do not use a `pending` placeholder for self SHA/push. State:

```text
Exact immutable commit SHA, push result and post-commit verifier outputs are
external final-handoff evidence generated after this report is committed.
```

## Critical acceptance gates

- Task 002A docs closure exact;
- default/malformed/missing config disabled;
- ConfigLoader integrated;
- disabled GameServer path creates no instance/log/task;
- enabled skeleton inert;
- shutdown before ThreadPool;
- bounded queue and no worker;
- fixed metrics;
- bounded disabled-by-default trace;
- no DB/network/Player/NPC;
- skeleton tests and all previous gates PASS;
- `ant verify` and `ant jar` PASS;
- new verifier pre and final twice PASS, outputs identical;
- production JAR contains zero test classes;
- ordinary commit/push;
- Task 004 not started.

## Commit

```text
feat(phantoms): add disabled system skeleton
```

One ordinary commit on top of `84f29a...`. No amend/rebase/force push.

## Blocked behavior

If disabled path creates tasks/DB/network, lifecycle order is unsafe, or tests are
not GREEN:

- remove unsafe/uncompilable production changes;
- keep safe tests/report/verifier;
- do not start Task 004;
- ordinary commit/push with exact blocker.

## Final Codex handoff

```text
Статус:
Task 002A docs closure:
Baseline:
Config defaults:
Fail-closed tests:
Disabled behavior:
Enabled skeleton:
Lifecycle ordering:
Queue/tasks/threads:
Metrics/trace:
DB/network access:
Skeleton tests:
All prior suites:
ant verify:
ant jar:
Static verifier pre:
Static verifier final 1:
Static verifier final 2:
Outputs identical:
Production JAR test entries:
Commit:
Parent:
Branch:
Push:
Remote ref:
Отчёт:
Manual gate:
Task 004:
Ограничения/блокеры:
```
