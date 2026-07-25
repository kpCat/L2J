# GOAL 005 — Core Phantom profile and persistence envelope

## 1. Identifier

- **Goal ID:** `005-core-profile-persistence-envelope`
- **Roadmap stage:** I — Canonical actor, persistence and lifecycle
- **Branch:** `feature/phantom-world`
- **Accepted baseline:** `f5b66c4edf1ddf18e044ef8c692d70ecea616485`
- **Baseline parent:** `d36e10e24787edce3fe4f4d933fca4d0ac884d50`
- **Repository root:** `C:\Users\endim\L2J_Mobius\`
- **Only module:** `C:\Users\endim\L2J_Mobius\L2J_Mobius_CT_2.6_HighFive\`
- **Roadmap SHA-256 at baseline:** `52C6F680582DEB91E45E4112FEDE2E70A4A64807DB76B3970D2BF24FB6455346`
- **Production DB:** `l2jmobiush5` — do not connect/read/mutate
- **Test DB:** `l2jmobiush5_phantom_test`
- **Seed:** `20260725001`
- **Codex model:** Sol
- **Effort:** Very High

## 2. Accepted gates

```text
Task 001 / 001A: ACCEPT
Task 002 / 002A: ACCEPT
Task 003: ACCEPT
Task 004 technical feasibility: ACCEPT
Task 004A: ACCEPT after Task 004B
Task 004B: ACCEPT
ADR 0001: may transition to Accepted
Goal 005: ALLOWED
Goal 006: NOT_STARTED
```

Task 004B accepted facts:

```text
Commit: f5b66c4edf1ddf18e044ef8c692d70ecea616485
Parent: d36e10e24787edce3fe4f4d933fca4d0ac884d50
Remote: exact
Focused headless: 18/18
Task 004 failure matrix: 11/11
Task 004A retry tests: PASS
Verifier final: 66/66 ×2
Verifier SHA-256:
39A1D87DB35AE8B2DDE28EB11776A69E2F7359AC6539A900BB78D114BDBB7BC9
Independent verdict: ACCEPT_WITH_TEST_STABILIZATION_FOLLOW_UP
```

One non-product follow-up is included in this Goal: stabilize the existing
headless test-environment thread baseline. Do not create a separate 004C.

## 3. User-visible result

After Goal 005 the server code has a small, versioned, restart-safe persistence
foundation for Phantom identities:

- a stable `phantomProfileId`;
- an optional unique link to a canonical character object ID;
- optimistic row versions;
- a bounded opaque component envelope for future small/low-churn state;
- explicit repository methods;
- deterministic migration and schema validation;
- round-trip, restart and concurrent-update tests.

No Phantom profile is automatically created, loaded, scheduled or materialized.
The feature remains disabled by default and ordinary server behavior is
unchanged.

## 4. Architectural boundary

Goal 005 defines only the persistence **envelope**.

It must not define future models for:

- personality;
- memory;
- reputation;
- schedules;
- activity states;
- long-term goals;
- Utility AI;
- population;
- navigation;
- combat;
- economy;
- conversation.

Future components may store their own versioned state without changing core
identity, but their domain schema is owned by their future GOAL.

## 5. Mandatory reading

Read fully before editing:

1. `docs/PHANTOM_BOTS_ROADMAP.md`;
2. `PHANTOM_DEVELOPMENT_MASTER_PLAN.md`;
3. `Agents.md`;
4. workflow/package/report standards;
5. Task 004, 004A and 004B packages, reports, reviews and ADR 0001;
6. current:
   - `DatabaseFactory.java`;
   - `DatabaseConfig.java`;
   - `PhantomTestDatabaseBootstrap.java`;
   - `PhantomTestDatabaseProvisioner.java`;
   - `PhantomTestSchemaManifest.java`;
   - `StrictSqlScriptRunner.java`;
   - `PhantomHeadlessPlayerTestEnvironment.java`;
   - `build.xml`;
   - `PhantomTestLauncher.java`;
7. existing SQL installer conventions under `dist/db_installer/sql/game`;
8. all documents in this package.

Do not use files from another chronicle.

## 6. Initial Git audit

```bat
git rev-parse --show-toplevel
git status --short --branch
git remote -v
git fetch origin --prune
git rev-parse HEAD
git rev-parse origin/feature/phantom-world
git show --stat --oneline f5b66c4edf1ddf18e044ef8c692d70ecea616485
git diff --name-status d36e10e24787edce3fe4f4d933fca4d0ac884d50..f5b66c4edf1ddf18e044ef8c692d70ecea616485
```

Expected:

```text
HEAD == origin/feature/phantom-world == f5b66c4...
```

The extracted Goal 005 package is expected untracked scope.

Preserve and exclude unrelated `docs/agent-tasks/**`.

Return `BLOCKED_BASELINE_DRIFT` for unreviewed production or schema drift.

## 7. Close Task 004B and ADR 0001

### 7.1. Task 004B report

Update:

```text
docs/phantoms/reports/004b-retained-identity-ownership-fix.md
```

Add immutable handoff:

```text
Commit: f5b66c4edf1ddf18e044ef8c692d70ecea616485
Parent: d36e10e24787edce3fe4f4d933fca4d0ac884d50
Push/remote: exact
Final verifier 1: 66/66
Final verifier 2: 66/66
Outputs identical SHA-256:
39A1D87DB35AE8B2DDE28EB11776A69E2F7359AC6539A900BB78D114BDBB7BC9
Independent review: ACCEPT
Follow-up: stabilize test-only ThreadPool baseline in Goal 005
```

### 7.2. Review record

Create:

```text
docs/phantoms/reviews/004b-retained-identity-ownership-fix-review.md
```

Required verdict:

```text
Task 004 technical feasibility: ACCEPT
Task 004A: ACCEPT after Task 004B
Task 004B: ACCEPT
Revert: NOT_REQUIRED
ADR 0001: ACCEPTED
Goal 005: ALLOWED
Goal 006: NOT_STARTED
```

### 7.3. ADR 0001

Change ADR status from `Proposed` to `Accepted`.

Record independent acceptance commit `f5b66c4...`, the retained-identity
correction and the remaining explicit limitation:

- retained REAL_LOGIN lease recovery orchestration belongs to Goal 006;
- no automatic retry loop is introduced by Goal 005.

Do not otherwise rewrite the accepted seam.

## 8. Roadmap progress update

Update only current progress/dependency facts in
`docs/PHANTOM_BOTS_ROADMAP.md`.

Required factual updates:

- accepted baseline becomes `f5b66c4...`;
- Task 004, 004A and 004B are accepted;
- ADR 0001 is Accepted;
- Goal 005 is `IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`;
- Goal 006 remains `NOT_STARTED / BLOCKED`;
- DAG becomes:
  `001 → 002 → 003 → 004 → 004A → 004B → 005 → 006`;
- Goal 005 dependency becomes `004B ACCEPT`;
- Goal 004/closure section records 004B;
- architecture, Goal 005 result, later GOAL definitions and all user
  requirements remain unchanged.

No roadmap version bump. This is progress maintenance, not another architecture
revision.

The static verifier must reject changes outside the exact progress/dependency
regions.

## 9. Fixed schema

Create exactly one production installer script:

```text
dist/db_installer/sql/game/phantom_profiles.sql
```

It contains exactly two idempotent `CREATE TABLE IF NOT EXISTS` statements and
no `DROP`, `TRUNCATE`, `ALTER`, `INSERT`, `UPDATE`, `DELETE` or procedure.

Full schema contract is in `SCHEMA.md`.

### Table 1: `phantom_profiles`

Required columns:

```text
profile_id           BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY
character_object_id  INT NULL
schema_version       SMALLINT UNSIGNED NOT NULL DEFAULT 1
row_version          BIGINT UNSIGNED NOT NULL DEFAULT 0
created_at           TIMESTAMP(3) NOT NULL
updated_at           TIMESTAMP(3) NOT NULL
```

Required unique index:

```text
uq_phantom_profiles_character_object_id(character_object_id)
```

No foreign key to `characters` in Goal 005. Character existence and handoff
validation belong to Goal 006.

### Table 2: `phantom_profile_components`

Required columns:

```text
profile_id               BIGINT UNSIGNED NOT NULL
component_type            VARCHAR(64) ASCII/BINARY collation NOT NULL
component_schema_version  SMALLINT UNSIGNED NOT NULL
row_version               BIGINT UNSIGNED NOT NULL DEFAULT 0
payload                   VARBINARY(4096) NOT NULL
created_at                TIMESTAMP(3) NOT NULL
updated_at                TIMESTAMP(3) NOT NULL
```

Primary key:

```text
(profile_id, component_type)
```

Required foreign key:

```text
profile_id -> phantom_profiles(profile_id) ON DELETE CASCADE
```

Both tables:

```text
ENGINE=InnoDB
DEFAULT CHARSET=utf8mb4
```

No JSON column and no serialized Java object.

Expected schema inventory after this exact two-statement script:

```text
scripts: 118
statements: 207
```

The aggregate SHA-256 is generated by the existing manifest and must be reported.

## 10. Component-envelope policy

The inline envelope is only for small, low/medium-frequency state.

Required component type syntax:

```regex
^[a-z][a-z0-9_.-]{0,63}$
```

Required version:

```text
1..65535
```

Payload:

```text
0..4096 bytes
```

Java APIs must defensively copy payload on input and output.

Future large, relational or high-churn components must create their own
normalized table with `profile_id`, component schema version and optimistic row
version. They must not duplicate large data into this envelope.

Goal 005 must not register actual component names such as personality, goal,
schedule or memory. Tests may use `test.opaque`.

## 11. Production package

Create:

```text
java/org/l2jmobius/gameserver/phantoms/profile/
```

Required classes or exact responsibility-equivalent names:

```text
PhantomProfile.java
PhantomProfileComponent.java
PhantomProfileRepository.java
PhantomProfilePersistenceException.java
```

Do not create service, scheduler, cache, singleton, manager or background task.

### 11.1. `PhantomProfile`

Immutable snapshot:

```java
long profileId
Integer characterObjectId
int schemaVersion
long rowVersion
Instant createdAt
Instant updatedAt
```

Validation:

- profile ID positive;
- character ID null or positive;
- schema version positive;
- row version non-negative;
- timestamps non-null.

### 11.2. `PhantomProfileComponent`

Immutable snapshot:

```java
long profileId
String componentType
int componentSchemaVersion
long rowVersion
byte[] payload
Instant createdAt
Instant updatedAt
```

Constructor and accessor both make defensive copies.

### 11.3. Repository lifecycle

Required factory:

```java
public static PhantomProfileRepository open()
```

`open()`:

- obtains a normal `DatabaseFactory` connection;
- validates current schema sufficiently to reject missing/wrong tables, columns,
  primary/unique/foreign keys and payload bound;
- holds no connection after return;
- starts no thread;
- caches no profile;
- creates no profile automatically.

The repository obtains and closes a connection per operation.

No repository method is called from `GameServer`, `PhantomSystem`, config
loading, scheduler or shutdown in Goal 005.

### 11.4. Repository API

Required behaviorally equivalent operations:

```java
PhantomProfile create(Integer characterObjectId)
Optional<PhantomProfile> find(long profileId)
Optional<PhantomProfile> findByCharacterObjectId(int characterObjectId)

PhantomProfile updateCharacterLink(
    long profileId,
    long expectedRowVersion,
    Integer characterObjectId)

void delete(
    long profileId,
    long expectedRowVersion)

PhantomProfileComponent insertComponent(
    long profileId,
    String componentType,
    int componentSchemaVersion,
    byte[] payload)

Optional<PhantomProfileComponent> findComponent(
    long profileId,
    String componentType)

List<PhantomProfileComponent> listComponents(
    long profileId)

PhantomProfileComponent updateComponent(
    long profileId,
    String componentType,
    long expectedRowVersion,
    int componentSchemaVersion,
    byte[] payload)

void deleteComponent(
    long profileId,
    String componentType,
    long expectedRowVersion)
```

List results are immutable and ordered by `component_type` using database binary
order.

### 11.5. Optimistic locking

Core and component updates use one SQL statement equivalent to:

```sql
UPDATE ...
SET ..., row_version = row_version + 1
WHERE ... AND row_version = ?
```

A zero affected-row update/delete throws `ConcurrentModificationException` or a
narrow repository exception explicitly categorized as `OPTIMISTIC_CONFLICT`.

Do not implement last-write-wins, silent retry, `SELECT FOR UPDATE`, table locks,
global synchronization or an unbounded retry loop.

Unique character-link conflicts propagate as persistence errors and leave both
rows unchanged.

### 11.6. Transaction boundaries

Each public write operation is atomic and commits only its own statement.

No generic public transaction callback is added.

No operation edits canonical `characters`, `items`, accounts or Task 004 tables.

Profile deletion cascades only component-envelope rows; it never deletes the
linked character.

## 12. Test-only ThreadPool baseline stabilization

Task 004/004A/004B production code is frozen.

Allowed file:

```text
test/java/org/l2jmobius/tests/phantoms/PhantomHeadlessPlayerTestEnvironment.java
```

Fix only the known false-red race where shared ThreadPool workers appear after
the test captures its baseline.

Required approach:

1. after all bootstrap singletons and fixtures are initialized;
2. perform a bounded test-only infrastructure quiescence/warm-up;
3. wait for the set of L2J shared infrastructure non-daemon thread names/IDs to
   remain unchanged for at least four consecutive samples;
4. sample interval 25–50 ms;
5. total timeout no greater than 2 seconds;
6. only then capture `_environmentThreadIds`.

It is permissible to submit bounded no-op work to existing instant/scheduled
pools and wait for completion.

Forbidden:

- production `ThreadPool.java` changes;
- unconditional multi-second sleep;
- ignoring arbitrary unknown threads;
- removing final infrastructure shutdown checks;
- weakening World/autosave/lease/future residue assertions.

Required proof: `ant phantom-headless-player-test` passes three consecutive
independent invocations before final aggregate verify.

## 13. Automated suite

Create:

```text
test/java/org/l2jmobius/tests/phantoms/PhantomProfilePersistenceSuite.java
```

Add launcher mode `profile-persistence` and Ant target
`phantom-profile-persistence-test`.

Forked JVM, test DB only, timeout <= 120 seconds.

The suite uses `PhantomTestDatabaseBootstrap` before `DatabaseFactory`.

Do not initialize `GameServer`, `Player`, World, network or ThreadPool for the
profile suite.

## 14. Migration and provisioning gates

Because production installer SQL changes, local test DB is stale until explicit
re-provisioning.

Run provisioning twice with environment-only admin credentials:

```powershell
$env:PHANTOM_DB_ADMIN_URL = 'jdbc:mysql://127.0.0.1:3308/'
$env:PHANTOM_DB_ADMIN_USER = '<local-admin-user>'
$env:PHANTOM_DB_ADMIN_PASSWORD = '<local-admin-password>'
powershell -NoProfile -ExecutionPolicy Bypass -File tools\phantoms\prepare-test-db.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File tools\phantoms\prepare-test-db.ps1
Remove-Item Env:PHANTOM_DB_ADMIN_URL,Env:PHANTOM_DB_ADMIN_USER,Env:PHANTOM_DB_ADMIN_PASSWORD
```

Both runs must report identical:

```text
scriptCount=118
statementCount=207
aggregateSha256=<same 64-hex>
```

The profile suite also executes `phantom_profiles.sql` twice against the same
test schema before data tests to prove in-place idempotency.

Production DB is never connected, read or mutated.

## 15. Ant contract

Modify `build.xml` minimally:

- add `phantom-profile-persistence-test`;
- add `phantom-static-verify-005`;
- preserve historical verifier targets;
- include profile suite and verifier in cumulative `verify`;
- all Java executions forked.

## 16. Exact scope

Allowed production/schema:

```text
dist/db_installer/sql/game/phantom_profiles.sql
java/org/l2jmobius/gameserver/phantoms/profile/**
```

Allowed build/tests:

```text
build.xml
test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java
test/java/org/l2jmobius/tests/phantoms/PhantomProfilePersistenceSuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomHeadlessPlayerTestEnvironment.java
tools/phantoms/verify-task-005.ps1
```

Allowed documentation:

```text
docs/PHANTOM_BOTS_ROADMAP.md
docs/phantoms/architecture/PROFILE_PERSISTENCE_CONTRACT.md
docs/phantoms/tasks/005-core-profile-persistence-envelope/**
docs/phantoms/reports/004b-retained-identity-ownership-fix.md
docs/phantoms/reports/005-core-profile-persistence-envelope.md
docs/phantoms/reviews/004b-retained-identity-ownership-fix-review.md
docs/phantoms/adr/0001-headless-player-integration-seam.md
```

## 17. Hard out of scope

Forbidden:

- `Player`, `GameClient`, `CharacterSelect`, `Disconnection`;
- Task 004 identity/cleanup/seam classes;
- `PhantomSystem`, GameServer/Shutdown/config;
- automatic profile creation/loading;
- production materialization;
- personality/memory/reputation;
- schedule/activity/population;
- goals/Utility AI;
- navigation/topology/Game Knowledge;
- combat/economy/conversation;
- canonical character/item/account mutations;
- production DB;
- login database schema;
- JSON libraries or Java serialization;
- background cache/worker/task;
- other chronicles;
- dependencies/CI;
- old verifier modifications;
- mass formatting;
- amend/rebase/merge/force push.

## 18. Static verifier Goal 005

Create `tools/phantoms/verify-task-005.ps1`.

It must verify:

- base `f5b66c4...`;
- one ordinary commit;
- exact scope;
- High Five only;
- roadmap changes only in approved progress/DAG/dependency regions;
- Goal 006 absent;
- Task 004 production files unchanged;
- production config unchanged;
- exact SQL file and exactly two statements;
- no destructive SQL/DML;
- exact table/column/index/FK tokens;
- no FK to `characters`;
- required profile classes;
- immutable records and defensive payload copy;
- type/payload/version bounds;
- repository has no singleton/cache/thread/scheduler;
- repository not referenced by GameServer/PhantomSystem/config;
- optimistic `WHERE row_version = ?`;
- no silent retry/`SELECT FOR UPDATE`/table lock;
- deterministic component ordering;
- launcher/Ant targets;
- profile suite uses test DB bootstrap;
- headless test stabilization is test-only and bounded;
- three-pass command recorded in report;
- Task 004B closure and ADR Accepted;
- profile architecture contract/report headings;
- UTF-8, mojibake, escaped Cyrillic;
- no credentials/binaries;
- verifier read-only and deterministic.

## 19. Required profile tests

Minimum expected suite: 15 explicit cases.

Critical gates:

- schema exact and repository open;
- SQL replay twice;
- create unlinked profile;
- link/unlink round-trip;
- unique character link conflict;
- stale core update rejected;
- concurrent core update exactly one winner;
- component input validation;
- 4096 accepted / 4097 rejected;
- defensive input/output payload copies;
- insert/read/update component;
- stale component update rejected;
- deterministic list order;
- optimistic component delete;
- profile delete cascades components;
- new repository instance reloads same state;
- final owned-row residue zero.

The suite may contain more cases but must not invent future domain models.

## 20. Commands

Before schema change:

```bat
ant verify
powershell -NoProfile -ExecutionPolicy Bypass -File tools\phantoms\verify-task-004b.ps1
```

Thread baseline proof, three independent runs:

```bat
ant phantom-headless-player-test
ant phantom-headless-player-test
ant phantom-headless-player-test
```

Provision test DB twice using section 14.

Targeted:

```bat
ant compile-tests
ant phantom-profile-persistence-test
ant phantom-db-test
ant test
ant phantom-skeleton-test
ant phantom-headless-player-test
ant phantom-headless-player-performance-smoke
```

Full:

```bat
ant verify
ant jar
powershell -NoProfile -ExecutionPolicy Bypass -File tools\phantoms\verify-task-005.ps1
git diff --check
git status --short --branch
```

Post-commit:

```bat
git rev-parse HEAD
git show --stat --oneline --decorate HEAD
git diff --check f5b66c4edf1ddf18e044ef8c692d70ecea616485...HEAD
ant verify
ant jar
powershell -NoProfile -ExecutionPolicy Bypass -File tools\phantoms\verify-task-005.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File tools\phantoms\verify-task-005.ps1
git push origin feature/phantom-world
git ls-remote --heads origin feature/phantom-world
git status --short --branch
```

Compare final verifier output byte-for-byte/SHA-256 outside the repository.

## 21. Report

Create:

```text
docs/phantoms/reports/005-core-profile-persistence-envelope.md
```

Required sections:

- Status and accepted baseline;
- Task 004B independent closure;
- ADR 0001 acceptance;
- roadmap progress update;
- test-flake stabilization;
- schema design and actual fingerprint;
- table/index/FK evidence;
- production classes/API;
- optimistic-lock contract;
- component-envelope boundaries;
- DB transaction behavior;
- migration replay/provisioning ×2;
- round-trip/restart/concurrent update evidence;
- row cleanup;
- disabled behavior/no wiring;
- production DB safety;
- test counts and exit codes;
- three consecutive headless passes;
- cumulative verify/jar;
- static verifier;
- scope/deviations/limitations;
- branch/parent/subject;
- manual gate `PENDING_INDEPENDENT_REVIEW`;
- Goal 006 `NOT_STARTED`.

Use:

```text
Exact immutable commit SHA, push result and post-commit verifier outputs are
external final-handoff evidence generated after this report is committed.
```

## 22. Acceptance and result

Critical result:

```text
PROFILE_PERSISTENCE_ENVELOPE_IMPLEMENTED_PENDING_INDEPENDENT_REVIEW
```

Codex must not self-accept Goal 005 or start Goal 006.

## 23. Commit/push

Commit subject:

```text
feat(phantoms): add profile persistence envelope
```

One ordinary commit on top of `f5b66c4...`.

Push regardless of SUCCESS/BLOCKED, but only safe scoped artifacts.

## 24. Blocking behavior

Return `BLOCKED` if:

- migration cannot be idempotent;
- exact test DB provisioning/fingerprint is not GREEN;
- optimistic concurrency allows two winners;
- payload is unbounded;
- repository is wired into disabled production path;
- production DB is accessed;
- Task 004B regression or three-pass headless stabilization fails;
- scope expands to future domain models;
- cumulative verify/jar is not GREEN.

On blocker, remove unsafe production edits, clean exact test-owned profile rows,
preserve safe evidence, commit/push, and keep Goal 006 not started.

## 25. Final handoff

```text
Status:
Architecture result:
Baseline:
Task 004B closure:
ADR 0001:
Roadmap progress:
Thread baseline stabilization:
Headless consecutive runs:
Schema scripts/statements/fingerprint:
Provision run 1:
Provision run 2:
Tables/indexes/FK:
Repository API:
Optimistic core update:
Optimistic component update:
Concurrent winners:
Component payload bounds:
Round-trip/restart:
Final owned DB residue:
Disabled production DB queries:
Production DB access/mutation:
Profile tests:
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
Report:
Manual gate:
Goal 006:
Limitations/blockers:
```
