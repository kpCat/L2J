# Test cases — Goal 013B

Deterministic seed: `13001302`.

## 1. Main-class durable success

- real materialized Player;
- real trainer and exact CLASS SkillLearn;
- one transaction;
- exact runtime state;
- direct durable row assertions;
- dematerialize/reload;
- exact skill/SP/item preserved;
- repeated request idempotent.

## 2. Subclass durable success

- active real subclass;
- exact subclass class index;
- subclass SP row changes;
- base `characters.sp` unchanged;
- skill row uses subclass class index;
- main/subclass/main reload remains isolated.

## 3. One required item

Use an exact item object whose row is durable before the transaction.

Prove:

- exact object ID selected;
- exact count consumed once;
- durable row updated/deleted once;
- canonical runtime inventory follows committed DB;
- no item creation or compensation.

## 4. Zero required item

Prove SP + skill commit atomically without inventory mutation.

## 5. Rollback injection

Inject before each stage:

```text
BEFORE_ITEM_SQL
AFTER_ITEM_SQL
AFTER_SP_SQL
AFTER_SKILL_SQL
BEFORE_COMMIT
```

For every pre-commit injection:

- rollback succeeds;
- runtime state remains baseline;
- fresh DB state remains baseline;
- event count zero;
- operation/lease counts zero.

## 6. Postcondition/fail-stop

Inject failure in the fresh durable postcondition read after commit.

Expected:

- no ordinary SUCCESS;
- committed DB rows remain exact;
- progression service enters fail-stop/non-mutating state;
- new progression mutations are rejected;
- shutdown drains safely;
- reload restores committed state.

## 7. Conflicts

- runtime target skill with missing DB row;
- DB target skill with missing runtime skill;
- prior level mismatch;
- item object/owner/location/count mismatch;
- main/subclass SP mismatch;
- class index drift;
- trainer drift;
- token cancellation before transaction;
- cancellation after durable commit cannot roll back or duplicate.

## 8. Unsupported shapes

- more than one distinct required item ID;
- required item count split across objects;
- non-owned item;
- unsupported item location.

All stop before transaction mutation.

## 9. Concurrency

- two same-profile requests;
- only one commit;
- one cost;
- one event;
- foreign token cannot steal operation;
- concurrent autosave cannot overwrite committed SP;
- transaction timeout releases DB/Java locks.

## 10. Static negative controls

Mutated fixture/source variants must make verifier/tests fail when:

- `addSkill(skill, true)` is used;
- transaction/rollback removed;
- item mutation uses item ID instead of object ID;
- subclass SP path removed;
- event moved before postconditions;
- tests stop checking DB/reload;
- persistence exception becomes SUCCESS.

## 11. Cumulative

Minimum:

```text
ant -Dphantom.test.seed=13001302 phantom-progression-durability-test   ×3
ant -Dphantom.test.seed=13001302 phantom-progression-server-integration-test ×2
ant verify
ant jar
powershell -NoProfile -ExecutionPolicy Bypass -File tools/phantoms/verify-task-013b.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File tools/phantoms/verify-task-013b.ps1
```

Run all Goal 013/013A focused suites and all historical Phantom regressions.

Verifier outputs must be byte-identical and have the same SHA-256.
