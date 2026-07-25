# TASK 004B — Retained identity ownership fix

## 1. Идентификатор

- **Task ID:** `004b-retained-identity-ownership-fix`
- **Тип:** обязательный узкий safety hotfix после независимого ревью Task 004A
- **Branch:** `feature/phantom-world`
- **Accepted starting baseline for this task:** `d36e10e24787edce3fe4f4d933fca4d0ac884d50`
- **Parent:** `441877e75feed482b58c2b0647137739b5b07748`
- **Git root:** `C:\Users\endim\L2J_Mobius\`
- **Module:** `C:\Users\endim\L2J_Mobius\L2J_Mobius_CT_2.6_HighFive\`
- **Roadmap SHA-256:** `52C6F680582DEB91E45E4112FEDE2E70A4A64807DB76B3970D2BF24FB6455346`
- **Test DB:** `l2jmobiush5_phantom_test`
- **Production DB:** `l2jmobiush5` — forbidden
- **Seed:** `20260725001`
- **Codex model:** Sol
- **Effort:** Very High

## 2. Current gate

```text
Task 004 technical feasibility: ACCEPT
Task 004 implementation commit: closed by 004A only partially
Task 004A: FIX_REQUIRED
Task 004B: REQUIRED
ADR 0001: Proposed
Goal 005: BLOCKED
Revert: NOT_REQUIRED
```

Task 004A correctly implemented:

- shared `playerLock` for selection/disconnection;
- explicit connection-state gate;
- cleanup postcondition policy;
- retryable Phantom cleanup;
- terminal `STORED` after successful cleanup;
- bounded warnings and no automatic unbounded retry.

One critical ownership defect remains and must be fixed without broadening scope.

## 3. Root cause

Task 004A introduced:

```java
requiresRealLoginArbitration(boolean phantomSystemEnabled, OwnerKind currentOwner)
```

Current implementation:

```java
return phantomSystemEnabled || (currentOwner == OwnerKind.PHANTOM);
```

Therefore:

```text
EnablePhantomSystem=false + currentOwner=REAL_LOGIN
→ arbitration=false
→ legacy load path ignores the retained REAL_LOGIN lease
```

But Task 004A intentionally retains a `REAL_LOGIN` lease after failed or
incomplete cleanup. Ignoring that owner defeats the fail-closed guarantee.

A second related defect exists:

- `Disconnection` checks only whether the client has *some* identity lease;
- it may release that lease after successfully cleaning a different Player;
- lease ownership is object-ID scoped, therefore release must require exact
  `lease.objectId == cleanupPlayer.objectId`.

A third related hardening is required:

- cleanup postconditions currently check absence of the exact Player instance;
- identity protection is object-ID scoped;
- cleanup is complete only if no Player/WorldObject/autosave entry exists for
  that object ID.

## 4. Goal

Close only these retained-identity defects:

1. disabled ordinary login uses legacy behavior only when the registry has no
   owner for the selected object ID;
2. any existing `REAL_LOGIN` or `PHANTOM` owner is always honored, regardless of
   current feature flag;
3. a client lease can be released only for the exact matching object ID;
4. cleanup completion is object-ID based, not merely exact-instance based;
5. no wrong-character cleanup may release another character's lease;
6. all Task 004/004A lifecycle, packet, failure and performance tests remain
   GREEN;
7. roadmap remains byte-for-byte unchanged;
8. Goal 005 remains `NOT_STARTED`.

## 5. Mandatory reading

Read fully:

- `docs/PHANTOM_BOTS_ROADMAP.md`;
- `PHANTOM_DEVELOPMENT_MASTER_PLAN.md`;
- `Agents.md`;
- workflow/package/report standards;
- Task 004 and Task 004A packages, reports, reviews and ADR 0001;
- current:
  - `GameClient.java`;
  - `Disconnection.java`;
  - `CharacterSelect.java`;
  - `PhantomIdentityLeaseRegistry.java`;
  - `PhantomPlayerCleanupPolicy.java`;
  - `PhantomPlayerMaterializationSpike.java`;
  - `PlayerAutoSaveTaskManager.java`;
  - `PhantomHeadlessPlayerSuite.java`;
  - `verify-task-004a.ps1`;
- all files in this package.

Do not reinterpret future roadmap GOALs and do not start Goal 005.

## 6. Initial Git audit

```bat
git rev-parse --show-toplevel
git status --short --branch
git remote -v
git fetch origin --prune
git rev-parse HEAD
git rev-parse origin/feature/phantom-world
git show --stat --oneline d36e10e24787edce3fe4f4d933fca4d0ac884d50
git diff --name-status 441877e75feed482b58c2b0647137739b5b07748..d36e10e24787edce3fe4f4d933fca4d0ac884d50
```

Expected:

```text
HEAD == origin/feature/phantom-world == d36e10e...
```

Preserve and exclude unrelated `docs/agent-tasks/**`.

Return `BLOCKED_BASELINE_DRIFT` if there is unreviewed production drift.

## 7. Fixed architecture

### 7.1. Correct arbitration truth table

Replace the flawed policy with:

```java
return phantomSystemEnabled || (currentOwner != null);
```

Required table:

| Phantom enabled | Current owner | Arbitration |
|---|---|---|
| false | null | false — exact legacy path |
| false | REAL_LOGIN | true — retained real owner must block another load |
| false | PHANTOM | true — existing Phantom owner protected |
| true | null | true — acquire normal REAL_LOGIN reservation |
| true | REAL_LOGIN | true |
| true | PHANTOM | true |

The disabled compatibility requirement means:

```text
disabled + no owner
→ no new lease, legacy login path
```

It does **not** mean an existing retained owner may be ignored.

### 7.2. Lease-to-object identity

Add an immutable query on `Lease`, or an equivalent narrow contract:

```java
public boolean matchesObjectId(int objectId)
```

Add a synchronized GameClient query or equivalent:

```java
public boolean hasPlayerIdentityLeaseFor(int objectId)
```

Rules:

- no caller receives the mutable lease;
- no release by owner kind only;
- no release when `_player == null` but a retained lease exists;
- no release when cleanup Player object ID differs from lease object ID;
- mismatch retains ownership and emits at most one bounded warning;
- stale token protections remain unchanged.

### 7.3. Disconnection release policy

After cleanup of a Player:

```text
client has exact matching lease
AND cleanup operation had no failure
AND object-ID cleanup postconditions are complete
→ release lease
```

Otherwise:

```text
retain lease
→ bounded warning once
```

If no Player was ever loaded and the client has no lease, return normally.

If no Player is available but a lease exists, retain it; do not silently release
an ownership claim whose object ID cannot be validated.

Do not add an unbounded retry loop. Goal 006 owns recovery orchestration.

### 7.4. Object-ID cleanup postconditions

Update cleanup policy to require:

```java
!player.isOnline()
world.getPlayer(objectId) == null
world.findObject(objectId) == null
!PlayerAutoSaveTaskManager.containsObjectId(objectId)
player.getClient() == null
```

Do not accept “another object exists at the same ID” as successful cleanup.

Add only the narrow read-only autosave query:

```java
containsObjectId(int objectId)
```

Keep `contains(Player)` if already used by tests or code.

The query is cleanup-path diagnostics, not a hot-path periodic scan.

### 7.5. Phantom cleanup

Preserve the Task 004A retryable behavior:

- operation failure retains Player/output/lease;
- retry may succeed;
- release remains last;
- terminal successful state is `STORED`;
- repeated successful cleanup is no-op.

Update it only as needed to use corrected object-ID postconditions.

### 7.6. Disabled behavior

With both Phantom config flags false:

- ordinary no-owner login follows exact legacy path;
- no new REAL_LOGIN lease is created;
- an existing retained REAL_LOGIN or PHANTOM owner is still protected;
- no Phantom Player/task/DB/network activity starts.

## 8. Mandatory tests

Extend the existing explicit headless suite. Do not add a new framework.

### Arbitration

- disabled + null -> false;
- disabled + REAL_LOGIN -> **true**;
- disabled + PHANTOM -> true;
- enabled + any -> true;
- disabled/no-owner path leaves registry count unchanged;
- retained REAL_LOGIN owner remains in registry and blocks a second owner while
  disabled.

### Lease identity

- lease matches its own object ID;
- lease does not match another object ID;
- stale close still cannot release a newer lease;
- exact client-release source contract uses matching object ID;
- cleanup of B cannot release retained lease A.

The last actual integration hook may be protected by static verifier plus
executable pure lease-policy tests; do not construct a fake `GameClient` or
`Connection`.

### Cleanup policy

- active exact Player is incomplete;
- canonical cleanup is complete;
- policy checks both World maps for `null`, not inequality to exact instance;
- autosave object-ID query detects the loaded Player;
- object-ID residue is rejected;
- policy remains read-only.

### Regression

- original Task 004 failure matrix 11/11;
- Task 004A before-store and before-delete retry tests;
- action/cleanup race;
- observer visibility and packet effects;
- performance 2/2;
- all earlier harness, skeleton, DB, negative and scenario suites.

## 9. Scope

Allowed production/build:

```text
build.xml
java/org/l2jmobius/gameserver/network/GameClient.java
java/org/l2jmobius/gameserver/network/Disconnection.java
java/org/l2jmobius/gameserver/phantoms/player/PhantomIdentityLeaseRegistry.java
java/org/l2jmobius/gameserver/phantoms/player/PhantomPlayerCleanupPolicy.java
java/org/l2jmobius/gameserver/phantoms/player/PhantomPlayerMaterializationSpike.java
java/org/l2jmobius/gameserver/taskmanagers/PlayerAutoSaveTaskManager.java
```

Allowed tests/tooling/docs:

```text
test/java/org/l2jmobius/tests/phantoms/PhantomHeadlessPlayerSuite.java
tools/phantoms/verify-task-004b.ps1
docs/phantoms/tasks/004b-retained-identity-ownership-fix/**
docs/phantoms/reports/004a-real-login-lease-cleanup-hardening.md
docs/phantoms/reports/004b-retained-identity-ownership-fix.md
docs/phantoms/reviews/004a-real-login-lease-cleanup-hardening-review.md
docs/phantoms/adr/0001-headless-player-integration-seam.md
```

`CharacterSelect.java` is frozen unless a fresh audit proves a one-symbol change
is essential. Prefer no change.

## 10. Hard out of scope

Forbidden:

- `docs/PHANTOM_BOTS_ROADMAP.md` modifications;
- Goal 005 files or schema;
- `Player.java`;
- `PlayerOutboundSession`;
- headless packet sink;
- GameServer/Shutdown/config changes;
- DB schema, migrations, SQL or data;
- production DB;
- fake GameClient/Connection;
- Player subclass/fork;
- client handler families;
- automatic retry scheduler;
- navigation, AI, goals, persistence profile, Semantic Pack;
- other chronicles;
- old verifier modifications;
- dependencies/CI;
- amend/rebase/force push;
- mass formatting.

## 11. Static verifier Task 004B

Create `tools/phantoms/verify-task-004b.ps1`.

It must verify:

- base `d36e10e...`;
- one ordinary commit;
- exact scope;
- roadmap SHA exactly
  `52C6F680582DEB91E45E4112FEDE2E70A4A64807DB76B3970D2BF24FB6455346`;
- no Goal 005;
- no config/schema/SQL;
- no Player/packet-seam changes;
- corrected truth table tokens;
- disabled + REAL_LOGIN test now expects arbitration;
- lease object-ID matching API;
- Disconnection exact matching lease before release;
- no release in `_player == null && lease exists`;
- object-ID World null checks;
- autosave `containsObjectId`;
- materializer remains retryable/STORED;
- all Task 004A files/behavior preserved;
- new report/review/ADR closure;
- UTF-8, mojibake, escaped Cyrillic;
- no credentials/binaries;
- deterministic read-only verifier.

## 12. Commands

Targeted:

```bat
ant compile-tests
ant phantom-headless-player-test
ant phantom-headless-player-performance-smoke
ant test
ant phantom-skeleton-test
ant phantom-db-test
```

Full:

```bat
ant verify
ant jar
powershell -NoProfile -ExecutionPolicy Bypass -File tools\phantoms\verify-task-004b.ps1
git diff --check
```

Post-commit:

```bat
git rev-parse HEAD
git show --stat --oneline --decorate HEAD
git diff --check d36e10e24787edce3fe4f4d933fca4d0ac884d50...HEAD
ant verify
ant jar
powershell -NoProfile -ExecutionPolicy Bypass -File tools\phantoms\verify-task-004b.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File tools\phantoms\verify-task-004b.ps1
git push origin feature/phantom-world
git ls-remote --heads origin feature/phantom-world
git status --short --branch
```

Compare final verifier outputs byte-for-byte/SHA-256 outside the repository.

## 13. Documentation

Create:

```text
docs/phantoms/reviews/004a-real-login-lease-cleanup-hardening-review.md
docs/phantoms/reports/004b-retained-identity-ownership-fix.md
```

Update Task 004A report with:

```text
Independent review: FIX_REQUIRED
Root cause: retained REAL_LOGIN owner bypassed while disabled
Task 004B: REQUIRED
```

ADR remains `Proposed` in the Codex commit.

Successful implementation recommendation:

```text
FEASIBLE_WITH_SEAM_HARDENED_PENDING_INDEPENDENT_REVIEW
```

Do not self-accept ADR or authorize Goal 005.

## 14. Acceptance

Critical gates:

1. disabled/no-owner legacy behavior preserved;
2. disabled/existing REAL_LOGIN owner is never bypassed;
3. existing PHANTOM owner remains protected;
4. wrong-character cleanup cannot release another lease;
5. no-player path cannot release an unverifiable retained lease;
6. cleanup complete requires no object/player/autosave entry by object ID;
7. retryable Phantom cleanup remains GREEN and terminal `STORED`;
8. all Task 004/004A regression gates pass;
9. production DB untouched;
10. roadmap byte-identical;
11. ordinary commit/push;
12. Goal 005 not started.

## 15. Commit/push

Commit subject:

```text
fix(phantoms): preserve retained identity ownership
```

One ordinary commit on top of `d36e10e...`.

No amend, rebase, merge commit, reset history or force push.

## 16. Blocking behavior

If exact object-ID ownership cannot be proven inside this bounded scope:

- keep ownership fail-closed;
- do not release unverifiable leases;
- remove unsafe/incomplete edits;
- preserve safe tests/report/verifier;
- commit/push `BLOCKED`;
- do not start Goal 005.

## 17. Final Codex handoff

```text
Status:
Baseline:
Roadmap SHA:
Corrected arbitration table:
Disabled legacy no-owner:
Disabled retained REAL_LOGIN:
PHANTOM protection:
Lease/object ID matching:
Wrong-character release:
No-player retained lease:
World/object cleanup policy:
Autosave object-ID policy:
Phantom retry cleanup:
Headless focused tests:
Task 004 failure matrix:
Task 004A retry tests:
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
Review:
ADR:
Manual gate:
Goal 005:
Limitations/blockers:
```
