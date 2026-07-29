# Goal 015 production unblock — canonical ground-loss drop disposition

## Contract

```text
branch: feature/phantom-world
required parent: 32be3bbc320bc3a054aab8c5d39001910f35e4b8
test DB only: l2jmobiush5_phantom_test
production DB forbidden: l2jmobiush5
seed: 15001502
commit subject: fix(phantoms): support ground-loss production drops
success token: GOAL_015_PRODUCTION_LOOT_DISPOSITION_UNBLOCKED_PENDING_INDEPENDENT_REVIEW
```

Continue the existing Goal 015 capability. Do not create Goal 015A/015B and do
not start Goal 016/017/025. Create one ordinary child and push to
`origin/feature/phantom-world`. No amend/rebase/squash/merge/force push.

Independent disposition:

```text
Goal 015 reconciliation implementation: ACCEPT
Goal 015 bounded completion: ACCEPT as honest BLOCKED baseline
remaining product gate: production loot disposition only
```

All accepted lifecycle, identity, compact-inventory, transaction, shot,
recovery, login and shutdown contracts are frozen unless an exact regression
fix is required.

## Why the production pair is valid

Current topology contains one exact FARMING pair:

```text
22859@giran.farming.22859
```

Its immediate/time-limited drops were previously rejected categorically.
Canonical current server behavior is conditional:

```text
specific AutoLootItemIds
or ordinary AutoLoot
or AutoLootHerbs
    -> item/effect is auto-acquired

otherwise
    -> item is created on the ground
```

The shipped High Five `Player.ini` has:

```text
AutoLootHerbs = False
AutoLoot = False
AutoLootItemIds = 0
```

Therefore immediate/time-limited drops of this pair are not required to mutate
the Player. Background farming may roll them with exact canonical group/
occurrence semantics and deliberately leave them on the ground. It must not
fabricate their effects, timers or inventory objects.

## Exact READ_SET

Read only exact symbols/ranges:

1. this task and efficiency standard;
2. current Goal 015 report/review status ranges only;
3. `L2jPhantomBackgroundAuthority`: hashes, tracking, drops and resource checks;
4. `PhantomBackgroundModel`: Drop/DropRoll, roll order, inventory delta/result;
5. `PhantomBackgroundAuthority` records;
6. `PhantomBackgroundState/Codec` only if a record field must change;
7. `PhantomBackgroundService` farm commit handoff only;
8. `PhantomBackgroundTransaction` duplicate/command boundary only;
9. current `PlayerConfig` AutoLoot fields and shipped `Player.ini` lines;
10. `Attackable.doItemDrop` and `NpcTemplate.calculateDrops` exact ranges;
11. Goal 015 production audit/model/integration tests;
12. `build.xml` and verifier 015 relevant targets.

No old task packages, master plan reread, full server classes, other chronicle or
broad corpus exploration. Maximum four additional exact files with a reported
reason.

## 1. Versioned loot disposition

Add an immutable production/model disposition:

```text
ACQUIRE
LEAVE_ON_GROUND
```

For each exact death-drop fact, production authority determines canonical
auto-acquisition using current loaded configuration:

```text
specificAutoLoot = AUTO_LOOT_ITEM_IDS contains itemId
autoLoot =
    specificAutoLoot
    || (!item.hasExImmediateEffect() && AUTO_LOOT)
    || (item.hasExImmediateEffect() && AUTO_LOOT_HERBS)
```

The supported normal-solo Player must also be non-flying/non-mounted. Raid
rules remain excluded.

Classification:

```text
ordinary non-time-limited item
    -> ACQUIRE
       (background pickup policy may collect an ordinary ground item)

immediate-effect or time-limited item and autoLoot == false
    -> LEAVE_ON_GROUND

immediate-effect or time-limited item and autoLoot == true
    -> reject the target before baseline/operation
```

`LEAVE_ON_GROUND` means:

- no Player inventory row;
- no effect, skill, timer, variable or reuse state;
- no weight/slot consumption;
- no object-ID reservation;
- no deferred grant;
- no later materialization reconciliation;
- the item is intentionally lost when the aggregated encounter ends.

Do not mutate Player, Item, Attackable, loaders, config or datapack.

## 2. Exact RNG and occurrence semantics

Every `LEAVE_ON_GROUND` fact remains in the complete ordered drop corpus and
must participate exactly in:

- group cumulative chance;
- group break/selection;
- grouped and ungrouped occurrence budgets;
- level-gap roll;
- chance roll;
- inclusive amount roll;
- deterministic persisted RNG advancement.

Only after an award has been determined may disposition route it to acquired
inventory deltas or ground loss.

An ignored herb selected from a group must still suppress later alternatives
exactly as the canonical group algorithm would. Do not filter unsupported facts
before rolling.

Expose bounded immutable `groundLosses` evidence in `BatchResult` (item/count or
equivalent), for tests/metrics only. It is not canonical Player property and
must not enter the MariaDB item mutation command. Duplicate operation
reconciliation must not reroll it.

## 3. Loot-policy authority fingerprint

Current `Hashes` must become stale when canonical loot configuration changes.

Create a deterministic `LOOT_POLICY_V1` fingerprint over:

```text
AUTO_LOOT
AUTO_LOOT_HERBS
AUTO_LOOT_SLOT_LIMIT
sorted AUTO_LOOT_ITEM_IDS
```

Fold this fingerprint into the existing authority hash without fabricating a
datapack generation. Prefer a documented composite knowledge hash so no new
state schema version is needed.

Rules:

- same current knowledge/config -> byte-identical hash;
- any relevant flag/item-ID change -> different hash;
- old READY state then fails closed as authority/hash stale before mutation;
- capture after materialization stores the current composite hash;
- restore every static config value modified by a test in `finally`.

No runtime config mutation exists in production; tests may temporarily mutate
static loaded values only under isolated sequential control.

## 4. Current production positive path

Update the deterministic production audit across every FARMING anchor. Record:

```text
normal/spawned
ACQUIRE item IDs
LEAVE_ON_GROUND item IDs
auto-acquired unsupported item IDs
supported yes/no
```

On the shipped current configuration require exactly:

```text
supported pair:
22859@giran.farming.22859

LEAVE_ON_GROUND:
8600-8614, 10655-10657, 13028

auto-acquired unsupported:
none
```

Keep a negative control:

```text
AUTO_LOOT_HERBS=true
or an excluded ID in AUTO_LOOT_ITEM_IDS
-> pair unsupported and no mutation
```

Using real current loaders/catalogs, real Player, exact pair and production
`L2jPhantomBackgroundAuthority`:

1. configure a supported real class/capability as the existing suite does;
2. place the Player at the exact anchor;
3. persist the exact ACTIVE farm goal;
4. capture and dematerialize a real baseline;
5. run at least one successful production farm batch;
6. commit through the real `PhantomBackgroundTransaction`;
7. prove canonical EXP/SP, HP/MP and exact acquired item/resource deltas;
8. prove every ground-loss item is absent from Player inventory;
9. prove receipt/RNG/hash and exact duplicate idempotency;
10. materialize/dematerialize and verify DB/runtime/reload conservation;
11. restore the fixture fully.

The positive path may use a no-shot validated capability. Existing
authoritative-shot tests remain mandatory.

## 5. Required tests

Add/extend focused evidence for:

- shipped Player.ini/config parity;
- ordinary ACQUIRE;
- immediate LEAVE_ON_GROUND;
- time-limited LEAVE_ON_GROUND;
- immediate auto-loot rejection;
- time-limited ordinary AutoLoot rejection;
- specific AutoLootItemIds rejection;
- non-flying/non-mounted guard;
- grouped ignored award suppresses alternative;
- grouped/ungrouped occurrence budgets include ignored awards;
- RNG replay including ground loss;
- inventory weight/slots/object IDs ignore ground loss;
- config fingerprint drift rejects old state;
- exact production audit and successful real batch;
- duplicate does not reroll or regrant;
- existing compact inventory, lifecycle, login, recovery and shutdown suites.

Keep the existing 13 Goal 015 focused modes green. A separate small mode such
as `background-production-loot-unblock` may be added, but do not duplicate all
historical tests.

## 6. Scope

Allowed production:

```text
java/org/l2jmobius/gameserver/phantoms/background/**
```

Allowed tests/build/tools/docs:

```text
build.xml
test/java/org/l2jmobius/tests/phantoms/PhantomBackgroundSuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java
tools/phantoms/verify-task-015.ps1
docs/PHANTOM_BOTS_ROADMAP.md
PHANTOM_DEVELOPMENT_MASTER_PLAN.md status line only
docs/phantoms/architecture/BACKGROUND_FARMING_RECONCILIATION_CONTRACT.md
docs/phantoms/reports/015-background-farming-reconciliation.md
docs/phantoms/reviews/015-production-loot-disposition-unblock-review.md
docs/phantoms/tasks/015-production-loot-disposition-unblock/**
```

Forbidden:

- `Player`, `Attackable`, `Item`, `Inventory`, `NpcTemplate`, loaders;
- `PlayerConfig` or `Player.ini` modification;
- topology/datapack/geodata/schema/migrations;
- GameClient/materialization/commerce/progression changes;
- other chronicles;
- Goal 016/017/025;
- `progression.learn_skill`;
- new worker/thread/Future/task.

## 7. Verification and status

Before cumulative verification:

```text
compile affected
new production-loot focused mode
all 13 existing Goal 015 focused modes
affected background/model/transaction suites
static verifier 015
```

Then:

```text
one final focused aggregate green
one green ant verify
one standalone ant jar
commit/push
two post-commit byte-identical verifier 015 runs
```

A repeated full verify is allowed only after an exact targeted fix. Report all
runs, exact production audit evidence and usage. Keep report <=170 lines.

On full success update truth:

```text
Goal 015: IMPLEMENTED_PENDING_INDEPENDENT_REVIEW
Goal 016/017/025: NOT_STARTED
```

Create the requested independent-review evidence file but do not self-accept.
If the shipped pair still cannot complete a real production batch, preserve
evidence, commit/push and return honest BLOCKED.

Print `GOAL_015_PRODUCTION_LOOT_DISPOSITION_UNBLOCKED_PENDING_INDEPENDENT_REVIEW` only after every gate.
