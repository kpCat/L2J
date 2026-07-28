# Goal 013B — Durable class-skill learning transaction

## 0. Назначение

Это **узкая corrective Goal 013B** после независимого ревью Goal 013A.

Goal 013A доказательно исправила:

- capability variant identity;
- exact action-skill readiness;
- item/charge resource truth;
- summon/pet/cubic factual seams;
- controlled-actor typed body;
- полное bounded equipment paging;
- main/subclass/certification isolation;
- production Game Knowledge + progression composition;
- отдельный immutable Player CP context;
- multi-distinct-required-item prefix-loss.

Эти результаты сохраняются и не пересматриваются.

Независимое ревью нашло одну оставшуюся критическую границу:
текущий production `progression.learn_skill` может вернуть `SUCCESS`, хотя
навык не стал durable в `character_skills`.

Причина находится в current High Five server seam:

- `Player.addSkill(skill, true)` сначала меняет skill state в памяти;
- внутренний `Player.storeSkill` ловит database exception, только пишет warning
  и не сообщает вызывающему коду о неуспешной persistence;
- `Player.setSp` меняет только runtime state;
- `Item.updateDatabase` также скрывает database exceptions;
- текущая Goal 013A reconciliation проверяет память, SP и inventory, но не
  durable rows после commit/restart.

Следовательно, при DB failure возможно:

```text
required item и/или SP расходованы
+ skill присутствует в текущей памяти
+ character_skills row отсутствует
→ operation ошибочно возвращает SUCCESS
→ после restart skill исчезает
```

Goal 013B устраняет только эту durability/atomicity границу. Goal 014/015/017/025
не начинать.

## 1. Git contract

- Git root:
  `C:\Users\endim\L2J_Mobius\`
- Work module and Codex launch directory:
  `C:\Users\endim\L2J_Mobius\L2J_Mobius_CT_2.6_HighFive\`
- Branch: `feature/phantom-world`
- Required parent:
  `06929a2973ca2450688d413b4d58de034194053f`
- Goal 013 commit:
  `ca50ea28f233e41343035977c55c98129e5d113a`
- Last independently accepted baseline before Goal 013:
  `8dba87e9c1d5828376b80c1ea16c4578726d4947`
- Exact commit subject:
  `fix(phantoms): make class skill learning durable`
- Deterministic seed:
  `13001302`
- Success token:
  `GOAL_013B_DURABLE_CLASS_SKILL_LEARNING_IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`

Required graph:

```text
8dba87e9c1d5828376b80c1ea16c4578726d4947
  └─ ca50ea28f233e41343035977c55c98129e5d113a  Goal 013
       └─ 06929a2973ca2450688d413b4d58de034194053f  Goal 013A
            └─ <one ordinary Goal 013B commit>
```

Before edits:

```text
git branch --show-current == feature/phantom-world
git rev-parse HEAD == 06929a2973ca2450688d413b4d58de034194053f
git rev-parse origin/feature/phantom-world == 06929a2973ca2450688d413b4d58de034194053f
git status --short
```

Mandatory:

- one ordinary child commit;
- no amend/rebase/squash/merge/force push;
- push exact commit to `origin/feature/phantom-world`;
- commit and push even for honest BLOCKED/FAILED after removing unsafe code and
  preserving safe audit/tests/evidence.

## 2. Mandatory read-first audit

Read fully:

1. `PHANTOM_DEVELOPMENT_MASTER_PLAN.md`;
2. `docs/PHANTOM_BOTS_ROADMAP.md`;
3. workflow/package/report standards;
4. Goal 013 and Goal 013A task packages, contracts and reports;
5. this Goal 013B package;
6. all production progression files;
7. `Player.addSkill`, `Player.storeSkill`, `Player.store`, `Player.setSp`;
8. `RequestAcquireSkill`;
9. `ItemContainer.destroyItem*`, `Item.updateDatabase`;
10. active/base/subclass SP storage and `character_skills` class-index rules;
11. materialization `ActionLease`;
12. current Goal 013A tests and verifier;
13. current MariaDB tables through the isolated test DB only.

Before implementation, produce an internal audit table:

```text
state                  runtime owner          durable row/table
skill level            Player skill map       character_skills
main-class SP          PlayerStat             characters.sp
subclass SP            SubClassHolder/stat    character_subclasses.sp
required item object   Inventory/Item          items
```

Do not infer row semantics from memory. Verify exact current schema and SQL used
by High Five code.

## 3. Required architecture

### 3.1. Dedicated transaction facade

Create one bounded production facade equivalent to:

```text
PhantomClassSkillLearningTransaction
L2jClassSkillLearningTransaction
```

Names may differ locally, but responsibilities may not.

Only this facade may perform the Goal 013B durable mutation. SQL must not be
scattered through service, evaluator, handlers or tests.

The transaction facade is an implementation detail behind the existing
progression backend/operation port. No global static Phantom API.

### 3.2. Exact supported operation

Continue to support only:

```text
AcquireSkillType.CLASS
one exact profile
one exact materialized Player
one exact active class index
one exact real trainer
one exact SkillLearn
one exact target skill level
zero or one distinct required item ID
```

Goal 013A fail-closed behavior for more than one distinct required item remains.

For one required item ID, the transaction is executable only when a single exact
owned item object can satisfy the whole count under the current inventory
semantics. If the count is split across unsupported multiple objects, fail
closed before mutation.

No batch learning.

### 3.3. Lock and ownership order

Required order:

```text
progression operation slot
→ exact materialization ActionLease
→ final plan-token/cancellation check
→ synchronized exact Player
→ synchronized exact required Item, if any
→ one MariaDB transaction
→ durable commit
→ runtime reconciliation
→ postcondition verification
→ event
→ release Item/Player/ActionLease/operation slot
```

No database call while holding the progression service monitor.

No lock-order inversion with autosave or inventory item mutation.

Use bounded SQL/query timeouts where current JDBC infrastructure supports them.
No unbounded retry.

### 3.4. Durable preconditions

Immediately before opening/mutating the transaction, recheck:

- service RUNNING and exact operation generation;
- exact cancellation token current;
- actor object/profile ownership;
- active class and class index unchanged;
- actor state still permits learning;
- trainer identity/interaction/teaching unchanged;
- exact Skill and SkillLearn unchanged;
- previous runtime skill level;
- SP baseline;
- exact required-item object, owner, item ID, location and count;
- no unsupported multi-object/multi-item plan.

Before any cost mutation, the durable facade must verify that DB rows do not
contradict the runtime baseline.

Typed conflict, no side effect:

```text
DURABLE_SKILL_STATE_CONFLICT
DURABLE_SP_STATE_CONFLICT
DURABLE_ITEM_STATE_CONFLICT
DURABLE_SCHEMA_OR_ROW_MISSING
```

An already learned exact level is `IDEMPOTENT` only when runtime and durable
`character_skills` state agree. Memory-only skill presence is not idempotent
success.

### 3.5. One database transaction

Use one `DatabaseFactory` connection with `autoCommit=false`.

Lock/read deterministic rows using exact current schema and `SELECT ... FOR
UPDATE` or an equivalent current MariaDB row-locking contract:

1. SP owner row:
   - `characters` for class index 0;
   - `character_subclasses` for class index >0.
2. exact `character_skills` row/key;
3. exact required `items.object_id` row, when applicable.

Mutation in the same transaction:

1. decrement/delete the exact required item row with owner/item/location/count
   guards and exact affected-row count;
2. write exact post-cost SP to the correct main/subclass row;
3. insert or upgrade the exact `character_skills` row for the exact class index;
4. verify affected-row counts;
5. commit.

Rules:

- do not use `REPLACE` where it can hide an unexpected pre-existing row;
- first-level learning uses guarded insert;
- level upgrade uses guarded update from exact previous level;
- no direct class, quest, certification or Noble mutation;
- no item creation;
- no paperdoll insertion;
- no packet/bypass invocation;
- no partial commit;
- any pre-commit SQLException, timeout, affected-row mismatch or injected fault
  rolls back the whole transaction.

After rollback, runtime skill/SP/item state must remain byte-for-byte/equal to
the pre-operation snapshot.

### 3.6. Runtime reconciliation after durable commit

Only after successful DB commit:

- remove/decrement the exact already-committed item object through the canonical
  inventory in-memory path by exact object ID;
- set runtime SP to the exact committed value;
- call `Player.addSkill(skill, false)`, never `addSkill(skill, true)`;
- update shortcuts as required;
- copy/verify exact runtime skill/SP/item postconditions.

The second DB write attempted by a canonical in-memory item cleanup must be
idempotent relative to the already committed row state. It must not decrement a
second time.

Production progression code must contain no `Player.addSkill(..., true)`.

A post-commit runtime invariant violation is not a normal rejection and must
never be reported as ordinary SUCCESS. It is a fail-stop invariant:

```text
DURABLE_COMMIT_RUNTIME_RECONCILIATION_FAILED
```

The progression service enters `FAILED`/non-operational state and refuses new
progression mutations until shutdown/reload. Do not compensate by creating
items, restoring SP heuristically or deleting the committed skill.

### 3.7. Durable postconditions

Before returning SUCCESS, independently verify through a fresh connection:

- exact `character_skills(charId, skill_id, class_index, skill_level)`;
- exact main/subclass durable SP;
- exact required-item durable count/object absence;
- exact runtime skill;
- exact runtime SP;
- exact runtime item count.

`SUCCESS` is impossible unless all durable and runtime facts agree.

Only then dispatch `OnPlayerSkillLearn`, once in the successful synchronous
execution. No event on rollback, conflict, cancellation or fail-stop.

### 3.8. Idempotency

Repeated exact request after committed success:

- returns `IDEMPOTENT`;
- consumes no SP/item;
- performs no second DB mutation;
- dispatches no second event.

If runtime and DB disagree, return typed conflict/fail-stop, not idempotency.

### 3.9. Main/subclass durability

Mandatory separate paths:

```text
classIndex == 0
  → characters.sp
  → character_skills.class_index = 0

classIndex > 0
  → character_subclasses.sp for exact class_index
  → character_skills.class_index = exact active class index
```

Learning on subclass must not mutate base SP or base skill row. Learning on main
must not mutate subclass SP/skills.

### 3.10. No journal/schema expansion

Goal 013B must not introduce a saga, outbox or duplicate Player state in
`phantom_profile_components`.

No SQL migration or schema change is required or allowed. The existing High
Five InnoDB tables are the durable authority.

If the exact current schema cannot support the specified single-transaction
mutation without a server-core rewrite, return BLOCKED before unsafe production
code. Do not silently weaken the contract.

## 4. Exact allowed scope

Allowed production:

```text
java/org/l2jmobius/gameserver/phantoms/progression/**
```

Allowed minimal wiring only if constructor signatures require it:

```text
java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java
```

`PhantomSystem.java` may only pass an already existing dependency/port. No
startup order, scheduler, materialization or shutdown semantics change.

Allowed tests/build/tools:

```text
build.xml
test/java/org/l2jmobius/tests/phantoms/PhantomProgressionDurabilitySuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomProgressionOperationSuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomProgressionServerIntegrationSuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomProgressionPerformanceSuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java
tools/phantoms/verify-task-013b.ps1
```

Minimal compile-only changes to existing progression test fixtures are allowed.

Allowed documentation:

```text
docs/PHANTOM_BOTS_ROADMAP.md
docs/phantoms/architecture/PROGRESSION_CAPABILITY_CONTRACT.md
docs/phantoms/reports/013b-durable-class-skill-learning-transaction.md
docs/phantoms/tasks/013b-durable-class-skill-learning-transaction/**
```

Any other production/core file requires a proven blocker and explicit user
authorization. Do not modify `Player`, `Item`, `Inventory`, `RequestAcquireSkill`
or ordinary player behavior in this Goal.

## 5. Hard forbidden scope

Forbidden:

- other chronicles;
- `.gitignore` or `.l2j`;
- accepted Game Knowledge code/data;
- combat semantics, CP model or summon/equipment model changes;
- scheduler, decision architecture, materialization semantics, identity,
  profile repository/schema or shutdown changes;
- config or SQL migrations;
- production DB;
- Goal 014/015/017/025;
- commerce, supplies, travel, farming, party or PvP doctrine;
- profession/subclass/Noble mutation;
- packet/bypass simulation;
- direct paperdoll insertion;
- item creation/compensating reward;
- new executor/thread/task/Future;
- high-frequency logging;
- retry loops without fixed bounds;
- using `Player.addSkill(skill, true)` in production progression;
- accepting memory-only skill state as durable success.

## 6. Required tests

Use only `l2jmobiush5_phantom_test` and deterministic seed `13001302`.

### 6.1. Real durable success

For a real materialized canonical Player and real trainer:

- learn one real CLASS skill;
- verify runtime skill/SP/item;
- query exact durable rows independently;
- dematerialize/close;
- restore/rematerialize from DB;
- verify the learned skill remains;
- verify exact SP remains;
- verify exact item cost remains;
- repeat request and prove idempotency.

A test with zero required items is not sufficient by itself. Include one
controlled real/test-owned `SkillLearn` route with one exact required item, or a
strict isolated test fixture that exercises the real transaction against the
real High Five tables and real `Player`/inventory state.

### 6.2. Main and subclass

- main-class success writes only main SP and class index 0 skill;
- subclass success writes only exact subclass SP and exact subclass skill row;
- switch main/subclass/main and verify no contamination after reload.

### 6.3. Transaction failure matrix

Provide a test-only fault-injection port inside the transaction facade, absent
from production wiring.

Inject before:

1. item row mutation;
2. SP row mutation;
3. skill row mutation;
4. commit;
5. durable postcondition read.

For pre-commit faults:

```text
DB rollback exact
runtime item unchanged
runtime SP unchanged
runtime skill unchanged
event count 0
operation slot/lease released
```

For postcondition-read failure after a committed transaction, do not report
ordinary success; prove fail-stop state and durable rows remain internally
consistent.

### 6.4. Conflict matrix

- memory skill target but durable row missing;
- durable skill target but runtime skill missing;
- item DB count differs from runtime count;
- main/subclass SP durable row differs from runtime baseline;
- wrong class index;
- unexpected prior skill level;
- unsupported multiple required item IDs;
- unsupported required count split over multiple item objects.

Every case fails before cost mutation unless the durable transaction has already
committed.

### 6.5. Concurrency and locks

- concurrent same-profile requests: one winner, no duplicate cost;
- autosave attempt cannot interleave between durable commit and runtime
  reconciliation;
- foreign operation token cannot replace ownership;
- stable lock order;
- transaction timeout/rollback releases all Java and DB locks;
- shutdown waits for the transaction and actor lease;
- zero current operations/leases after every test.

### 6.6. Negative structural controls

Static and executable controls must fail if:

- `addSkill(skill, true)` returns to progression;
- DB transaction is removed;
- `autoCommit=false`, rollback or commit is absent;
- exact affected-row checks are absent;
- subclass SP path is omitted;
- item mutation uses item ID instead of exact object ID;
- event occurs before durable/runtime postconditions;
- tests check only in-memory skill;
- a persistence exception is swallowed and mapped to SUCCESS;
- direct SQL appears outside the dedicated transaction facade.

### 6.7. Cumulative gates

Run:

- durability focused suite at least ×3;
- real durability/restart integration at least ×2;
- existing Goal 013/013A catalog, parity, runtime, operations, composition,
  extensibility and performance suites;
- combat CP regressions;
- all historical Phantom tests;
- `ant verify`;
- standalone `ant jar`;
- Goal 013B verifier twice, byte-identical.

Performance:

- no transaction or DB call in ordinary catalog/readiness/equipment query paths;
- learning transaction is synchronous and bounded;
- no worker/task/Future;
- focused timeout <=120 seconds excluding existing real integration setup.

## 7. Static verifier

Create deterministic read-only:

```text
tools/phantoms/verify-task-013b.ps1
```

Minimum gates:

- exact parent/branch/subject/one ordinary child;
- exact allowlist;
- no other chronicle/geodata/gitignore/config/schema/future Goal;
- no production DB string/credentials;
- dedicated transaction facade only;
- no `addSkill(..., true)` in production progression;
- exact main/subclass durable row paths;
- exact item object/owner/location/count guards;
- `SELECT ... FOR UPDATE`;
- `setAutoCommit(false)`, rollback and commit;
- exact affected-row validation;
- runtime mutation only after commit;
- event only after fresh durable and runtime verification;
- fail-stop result for post-commit invariant failure;
- restart persistence test;
- failure injection matrix;
- no new threads/executors/Futures;
- UTF-8/mojibake/escaped Cyrillic;
- verifier itself read-only and deterministic.

Verifier must not accept mere method-name presence where ordering is the
contract. Check source ordering and negative fixtures.

## 8. Documentation and progress truth

Update `PROGRESSION_CAPABILITY_CONTRACT.md`:

- Goal 013A variant/resource/summon/equipment/CP contracts remain;
- class learning success now means one committed DB transaction plus runtime and
  fresh durable postconditions;
- memory-only `Player.addSkill(..., true)` is explicitly insufficient;
- unsupported transaction shapes fail before mutation.

Create report:

```text
docs/phantoms/reports/013b-durable-class-skill-learning-transaction.md
```

Include:

- full commit/parent/branch/push;
- exact source audit;
- SQL/table/row-lock contract;
- runtime/durable ownership table;
- success and rollback evidence;
- restart evidence;
- main/subclass evidence;
- fault matrix;
- event count;
- performance/lifecycle;
- DB guard;
- verifier outputs and SHA-256;
- exact changed files;
- limitations;
- explicit Goal 014/015/017/025 NOT_STARTED.

Correct the stale lower progress snapshot in roadmap. Before independent review:

```text
Goal 013: FIX_REQUIRED after first review
Goal 013A: FIX_REQUIRED after durability review
Goal 013B: IMPLEMENTED_PENDING_INDEPENDENT_REVIEW
Goal 014: NOT_STARTED / BLOCKED
Goal 015: NOT_STARTED
Goal 017: NOT_STARTED
Goal 025: NOT_STARTED
```

Do not self-accept Goal 013/013A/013B.

## 9. Completion and blocking

Successful handoff token:

```text
GOAL_013B_DURABLE_CLASS_SKILL_LEARNING_IMPLEMENTED_PENDING_INDEPENDENT_REVIEW
```

On blocker:

- remove unsafe transaction production code;
- make `progression.learn_skill` fail closed before all costs;
- preserve safe audit/tests/docs/verifier;
- do not mark Goal 013 accepted;
- ordinary commit and push;
- return an explicit `BLOCKED_...` token.

Do not ask for re-confirmation of architecture fixed by this package.
