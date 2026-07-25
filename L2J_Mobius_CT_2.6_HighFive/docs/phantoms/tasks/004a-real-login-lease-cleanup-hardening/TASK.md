# TASK 004A — Real-login lease and cleanup hardening

## 1. Идентификатор

- **Task ID:** `004a-real-login-lease-cleanup-hardening`
- **Тип:** обязательный safety hotfix после независимого ревью Task 004
- **Branch:** `feature/phantom-world`
- **Reviewed Task 004 commit:** `5b22b1ee9bab556cd5a14c2212dfa3f4119c4566`
- **Task 004 parent:** `1ca74a3d96e8fa51612ef3e5145c7398abf60f6d`
- **Module:** `C:\Users\endim\L2J_Mobius\L2J_Mobius_CT_2.6_HighFive\`
- **Production DB:** `l2jmobiush5` — forbidden
- **Test DB:** `l2jmobiush5_phantom_test`
- **Seed:** `20260725001`
- **Model:** Sol
- **Effort:** Very High

## 2. Independent review verdict

```text
Task 004 technical feasibility: ACCEPT
Task 004 commit: FIX_REQUIRED
Revert: NOT_REQUIRED
ADR 0001: Proposed
Task 004A: REQUIRED
Task 005: NOT_STARTED / BLOCKED
```

The outbound seam, zero-network packet effects, canonical create/load/spawn,
observer visibility and test-DB feasibility remain valid. This task changes only
identity/lifecycle safety discovered by review.

## 3. Approved documentation-only baseline drift

The user may push the revised file:

```text
L2J_Mobius_CT_2.6_HighFive/docs/PHANTOM_BOTS_ROADMAP.md
```

before running this task.

At task start accept exactly one of:

1. `HEAD == 5b22b1ee9bab556cd5a14c2212dfa3f4119c4566`; or
2. `HEAD` is exactly one ordinary child of `5b22b1ee...`, and the only changed
   path is `L2J_Mobius_CT_2.6_HighFive/docs/PHANTOM_BOTS_ROADMAP.md`.

Case 2 is an approved docs-only effective baseline. Preserve the roadmap
byte-for-byte. Any other drift is `BLOCKED_BASELINE_DRIFT`.

The final Task 004A commit must be one ordinary child of the resolved effective
baseline. No merge/rebase/amend/force push.

## 4. Goal

Close four concrete findings without starting Goal 005:

1. serialize CharacterSelect load/bind with `GameClient.onDisconnection`;
2. preserve legacy ordinary real-login behavior when Phantom system is disabled;
3. release REAL_LOGIN/PHANTOM identity lease only after cleanup postconditions;
4. make materializer cleanup fail-closed, retryable and terminally `STORED` only
   after complete cleanup.

## 5. Mandatory reading

Read fully:

- `docs/PHANTOM_BOTS_ROADMAP.md`;
- Task 004 package, report and audits;
- ADR 0001;
- current `Player`, `GameClient`, `CharacterSelect`, `Disconnection`;
- `PhantomIdentityLeaseRegistry`;
- `PhantomPlayerMaterializationSpike`;
- `PlayerAutoSaveTaskManager`;
- Task 004 tests/verifier;
- all documents in this package.

## 6. Findings to close

See `REVIEW_FINDINGS.md`. Do not broaden the task beyond them.

## 7. Fixed architecture

### 7.1. Disabled-mode real-login compatibility

Add a pure policy equivalent to:

```java
requiresRealLoginArbitration(boolean phantomSystemEnabled, OwnerKind currentOwner)
```

Required truth table:

| Phantom enabled | Current owner | Arbitration |
|---|---|---|
| false | null | false — exact legacy real-login path |
| false | REAL_LOGIN | false — legacy path, no new lease semantics |
| false | PHANTOM | true — protect an existing Phantom owner |
| true | any | true |

`GameClient.load` must not acquire a REAL_LOGIN lease on the normal disabled
path. It may use the registry only when policy says arbitration is required.

Do not add a hidden test/system-property switch. `PhantomPlayersConfig` remains
canonical and both default flags remain false.

### 7.2. CharacterSelect/disconnection synchronization

`CharacterSelect` already owns `client.getPlayerLock()` across load and bind.

Required changes:

- `GameClient.onDisconnection()` acquires the same lock;
- it sets `ConnectionState.DISCONNECTED` and performs detach/cleanup while the
  lock is held;
- `CharacterSelect`, immediately after acquiring the lock, requires the expected
  pre-selection state (`AUTHENTICATED`) before load/bind;
- therefore either selection completes and disconnect cleans it, or disconnect
  wins and selection exits; no bind can happen after disconnect;
- lock is always released in `finally`;
- do not hold the lock across an unbounded delayed wait.

No fake `GameClient` or `Connection` may be introduced for tests.

### 7.3. Shared cleanup-complete policy

Create one narrow production policy, preferably under
`gameserver.phantoms.player`, used by both `Disconnection` and the materializer.

Cleanup is complete only when all are true:

```text
Player is offline
exact Player is absent from World
Player is absent from PlayerAutoSaveTaskManager
Player client reference is null
```

Add only the minimal read-only membership method to
`PlayerAutoSaveTaskManager`, for example `contains(Player)`.

No broad diagnostics API and no periodic scan.

### 7.4. Fail-closed REAL_LOGIN lease release

`Disconnection` must:

- release immediately when no Player was ever loaded;
- after a Player cleanup attempt, release only if the shared cleanup policy is
  satisfied;
- retain the lease if store/delete throws or residue remains;
- emit one bounded warning/severe message for retained ownership;
- never schedule an unbounded retry loop;
- use the same rule in immediate, delayed and `storeAndDeleteWith` paths;
- remove every unconditional final release after a failed/incomplete cleanup.

A retained lease until explicit retry/server restart is safer than allowing a
second owner. Task 006 may later add recovery orchestration.

### 7.5. Fail-closed Phantom cleanup

`PhantomPlayerMaterializationSpike.cleanup()` must distinguish:

- **after-step injection evidence**, where cleanup may continue and report the
  injected error after postconditions are satisfied;
- **actual store/delete/postcondition failure**, where ownership must be kept.

Required behavior:

1. close admission and drain;
2. stop tasks;
3. restore fixture baseline;
4. if canonical store fails: do not delete/detach/release/clear; state `FAILED`;
5. if canonical delete fails and postconditions are incomplete: retain Player,
   outbound and identity; state `FAILED`;
6. verify shared cleanup postconditions;
7. only then detach output, release identity last, clear references;
8. set state `STORED` and mark cleanup finished;
9. a failed cleanup can be called again and succeed;
10. successful repeated cleanup is a no-op.

Extend the existing test-only `FailureInjector` with bounded **before operation**
points instead of adding production config:

```text
BEFORE_STORE_OPERATION
BEFORE_DELETE_OPERATION
```

They throw once in tests, prove ownership retention, then retry successfully.
The existing eleven Task 004 failure points must continue to pass.

### 7.6. ADR and reports

ADR 0001 stays `Proposed` in the Codex commit.

Technical recommendation after successful implementation:

```text
FEASIBLE_WITH_SEAM_IMPLEMENTED_PENDING_INDEPENDENT_REVIEW
```

Codex must not self-accept ADR or Task 004A.

## 8. Automated tests

Add a focused explicit suite or extend the existing headless suite with clearly
named tests. Avoid a new framework.

Mandatory executable tests:

### Arbitration policy

- disabled + no owner -> no arbitration;
- disabled + REAL_LOGIN -> no arbitration;
- disabled + PHANTOM -> arbitration;
- enabled -> arbitration;
- disabled policy leaves registry empty.

### Materializer cleanup

- successful cleanup ends `STORED`;
- before-store failure retains lease/output/Player and is retryable;
- before-delete failure retains lease/output/Player and is retryable;
- retry reaches `STORED` and zero residue;
- repeated successful cleanup no-op;
- existing 11/11 failure matrix remains green.

### Shared cleanup policy

Using real test fixtures:

- online/world/autosave state is incomplete;
- clean deleted state is complete;
- policy does not mutate Player/World/autosave.

### Real login source contract

Because constructing a fake `GameClient` is forbidden, use proportional proof:

- static verifier requires the same `_playerLock` in CharacterSelect and
  onDisconnection;
- static verifier requires state check inside CharacterSelect lock;
- static verifier requires disabled arbitration branch;
- static verifier forbids unconditional lease release;
- executable policy/registry tests cover decisions and ownership semantics.

### Regression

Run all Task 002/002A/003/004 suites, `ant verify`, `ant jar`, production JAR
inspection and deterministic static verifier.

## 9. Scope

Allowed:

```text
build.xml

java/org/l2jmobius/gameserver/network/GameClient.java
java/org/l2jmobius/gameserver/network/Disconnection.java
java/org/l2jmobius/gameserver/network/clientpackets/CharacterSelect.java
java/org/l2jmobius/gameserver/phantoms/player/PhantomIdentityLeaseRegistry.java
java/org/l2jmobius/gameserver/phantoms/player/PhantomPlayerMaterializationSpike.java
java/org/l2jmobius/gameserver/phantoms/player/PhantomPlayerCleanupPolicy.java
java/org/l2jmobius/gameserver/taskmanagers/PlayerAutoSaveTaskManager.java

test/java/org/l2jmobius/tests/phantoms/**
tools/phantoms/verify-task-004a.ps1

docs/phantoms/tasks/004a-real-login-lease-cleanup-hardening/**
docs/phantoms/reports/004-headless-player-feasibility-spike.md
docs/phantoms/reports/004a-real-login-lease-cleanup-hardening.md
docs/phantoms/reviews/004-headless-player-feasibility-spike-review.md
docs/phantoms/adr/0001-headless-player-integration-seam.md
```

`build.xml`/launcher changes are allowed only for the focused suite/verifier and
cumulative `verify` wiring.

## 10. Hard out of scope

Forbidden:

- modifying `docs/PHANTOM_BOTS_ROADMAP.md`;
- Goal 005 profile/schema work;
- production config/SQL/data;
- `Player.java` unless a fresh P1 proof makes one exact-symbol change essential;
- `GameServer`, `Shutdown`, World or packet families;
- fake GameClient/Connection;
- Player subclass/fork;
- client handler families other than CharacterSelect;
- final login handoff UX;
- background scheduler, AI, goals, Semantic Pack, navigation, combat;
- production DB;
- per-phantom task/thread;
- dependencies/CI;
- amend/rebase/merge/force push;
- mass formatting.

If `Player.java` becomes essential, stop first, document exact symbol/reason and
keep the change minimal. More than one new out-of-envelope production file is a
blocker.

## 11. Static verifier 004A

Create `tools/phantoms/verify-task-004a.ps1`.

It must:

- resolve the effective baseline according to section 3;
- enforce one ordinary Task 004A child commit;
- preserve roadmap byte-for-byte relative to effective baseline;
- check exact scope and High Five only;
- reject Goal 005 artifacts/config/SQL/binaries;
- verify disabled arbitration truth-table tokens/tests;
- verify `onDisconnection` locks `_playerLock` and sets DISCONNECTED inside it;
- verify CharacterSelect state check occurs inside its lock before load;
- verify no unconditional lease release on incomplete Player cleanup;
- verify shared cleanup policy includes World/offline/autosave/client conditions;
- verify materializer retains ownership on operation failure and ends STORED only
  after complete cleanup;
- verify before-store/before-delete regression tests;
- verify old 11 failure points remain;
- verify reports/review/ADR state;
- verify UTF-8, mojibake, escaped Cyrillic, no secrets;
- print stable ordinal PASS/FAIL;
- perform no DB/network/write.

## 12. Commands

Targeted:

```bat
ant compile-tests
ant test
ant phantom-headless-player-test
ant phantom-headless-player-performance-smoke
ant phantom-skeleton-test
ant phantom-db-test
```

Full:

```bat
ant verify
ant jar
powershell -NoProfile -ExecutionPolicy Bypass -File tools\phantoms\verify-task-004a.ps1
git diff --check
```

Post-commit:

```bat
git rev-parse HEAD
git show --stat --oneline --decorate HEAD
ant verify
ant jar
powershell -NoProfile -ExecutionPolicy Bypass -File tools\phantoms\verify-task-004a.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File tools\phantoms\verify-task-004a.ps1
git push origin feature/phantom-world
git ls-remote --heads origin feature/phantom-world
git status --short --branch
```

Compare final verifier outputs byte-for-byte/SHA-256 outside the repository.

## 13. Documentation closure

Update Task 004 report with:

```text
Commit: 5b22b1ee9bab556cd5a14c2212dfa3f4119c4566
Parent: 1ca74a3d96e8fa51612ef3e5145c7398abf60f6d
Push/remote: exact
Final verifier 1: 97/97
Final verifier 2: 97/97
Verifier SHA-256:
FA94A404CC98A16BA892DCD93CFC979C8CB0F2D51B0AC4978696404E54B251E9
Independent feasibility verdict: ACCEPT
Independent commit verdict: FIX_REQUIRED
Task 004A: REQUIRED
```

Create review record with the four findings and no false claim that the seam
itself failed.

Create Task 004A report with exact tests, states, scope, commit/push and:

```text
Manual gate: PENDING_INDEPENDENT_REVIEW
Task 005: NOT_STARTED
ADR: Proposed
```

## 14. Acceptance

Full checklist is in `ACCEPTANCE.md`. Critical gates:

1. disabled normal login path creates no lease;
2. PHANTOM owner is still protected while disabled;
3. CharacterSelect and disconnect cannot overlap load/bind;
4. incomplete cleanup retains REAL_LOGIN lease;
5. materializer operation failure retains PHANTOM ownership/output/reference;
6. retry succeeds and ends `STORED`;
7. postconditions include World/offline/autosave/client;
8. existing Task 004 feasibility tests remain green;
9. all prior suites, verify and jar green;
10. roadmap unchanged;
11. ordinary commit/push;
12. Task 005 not started.

## 15. Commit

```text
fix(phantoms): harden identity lease cleanup
```

## 16. Blocking behavior

Return `BLOCKED` if the race cannot be closed without fake network, broad handler
changes, production DB or redesigning ordinary login. Preserve safe audit/tests,
commit/push the bounded result, and keep Task 005 blocked.

## 17. Final Codex handoff

```text
Статус:
Effective baseline:
Roadmap preserved:
Disabled arbitration:
Selection/disconnect synchronization:
REAL_LOGIN fail-closed release:
PHANTOM cleanup retry:
Successful final state:
Existing 11-point matrix:
New focused tests:
All prior suites:
ant verify:
ant jar:
Static verifier pre:
Static verifier final 1:
Static verifier final 2:
Outputs identical:
Production DB access/mutation:
Commit:
Parent:
Branch:
Push:
Remote ref:
Report:
ADR:
Manual gate:
Task 005:
Limitations/blockers:
```
