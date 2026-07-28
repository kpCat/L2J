# Goal 014 — NPC commerce, supplies, travel and sell loop

## 1. Contract

```text
branch: feature/phantom-world
required parent: e9b98a243a68a710425a062155b9197ee6692b17
test DB only: l2jmobiush5_phantom_test
production DB forbidden: l2jmobiush5
seed: 140014
subject: feat(phantoms): add npc commerce supply and travel loop
success token: GOAL_014_NPC_COMMERCE_SUPPLY_TRAVEL_LOOP_IMPLEMENTED_PENDING_INDEPENDENT_REVIEW
```

One ordinary child, exact push to `origin/feature/phantom-world`. No amend/rebase/squash/
merge/force push. Commit and push an honest SUCCESS/BLOCKED/FAILED result.

## 2. Accepted handoff

Record:

```text
Goal 013: ACCEPT after Goal 013A + Goal 013B
Goal 013A: ACCEPT after Goal 013B
Goal 013B: ACCEPT_WITH_ACTIVATION_GATE
Goal 014: IMPLEMENTED_PENDING_INDEPENDENT_REVIEW
Goal 015/017/025: NOT_STARTED
```

Retain Goal 013B activation gate:

- no production candidate/plan may call `progression.learn_skill`;
- before future autonomous skill learning or Goal 015 mutation, prove common
  SP/item writer coordination against reward/addSp/same-stack writers;
- Goal 014 does not reopen progression.

Create `docs/phantoms/reviews/013b-durable-class-skill-learning-review.md`.

## 3. Required outcome

Implement one bounded explicit economic loop:

```text
explicit typed goal
→ authoritative static offer/route
→ exact current quote
→ durable operation receipt
→ one canonical buy/sell/teleport action
→ exact reconciliation
→ success/retry/replan/inconsistent
```

Provide:

1. immutable indexed buylist, multisell, teleporter and supply facts;
2. exact purchase of one unlimited buylist product;
3. exact sale of one owned object/count;
4. exact normal Gatekeeper teleport;
5. restart/idempotency receipt using existing profile component storage;
6. explicit decision candidates/handlers for acquire/sell/travel;
7. CP Potion IDs `5591/5592`, skill `2166`, actual vendors/currency/price from
   current data only;
8. deterministic hashes, tests, metrics and lifecycle.

Multisell is **catalog/query-only** in Goal 014. Do not execute it. If CP potion
is available only through multisell, report the exact positive ingredient item
ID/count and defer execution. Ancient Adena is item `5575` only when current
data actually uses it; never infer it from memory or display names.

## 4. Exact READ_SET

Read only these files/ranges initially:

1. this task and `docs/phantoms/PHANTOM_CODEX_EFFICIENCY_STANDARD.md`;
2. `docs/PHANTOM_BOTS_ROADMAP.md`, Goal 013B/014 section only;
3. `java/.../phantoms/PhantomSystem.java`, construction/start/stop wiring only;
4. `java/.../phantoms/decision/PhantomGoal.java`;
5. `java/.../phantoms/decision/PhantomDecisionCandidate.java`;
6. `java/.../phantoms/decision/PhantomPlan.java`;
7. `java/.../phantoms/decision/PhantomPlanStep.java`;
8. `java/.../phantoms/profile/PhantomProfileRepository.java`, component methods
   only;
9. `network/clientpackets/RequestBuyItem.java`, validation/mutation path only;
10. `network/clientpackets/RequestSellItem.java`, validation/mutation path only;
11. `network/clientpackets/MultiSellChoose.java`, validation/hazard path only;
12. `model/teleporter/TeleportHolder.java`, `doTeleport/fee` path only.

At most five additional exact files, only for concrete loader/model symbols:
`BuyListData`, `MultisellData`, `TeleporterData`, `PlayerInventory`, `Item`.
Record actual expansions. Do not read another chronicle or old Goal package.

## 5. Static catalog

Create `java/org/l2jmobius/gameserver/phantoms/commerce/**`.

Build once at startup from current High Five loaders. A strict XXE-safe parser is
allowed only where a loader lacks bounded enumeration/source identity; validate
all parsed IDs against current loaders/`ItemData`/`NpcData`.

Facts:

```text
BuyOffer(listId,itemId,npcIds,price,limitedStock,source)
MultisellOffer(listId,entryId,npcIds,ingredients,products,flags,source)
TeleportRoute(npcId,listName,type,ordinal,destination,feeItemId,feeCount,castles)
SupplyFact(itemId,kinds,boundSkills,reuse,Olympiad,weight,stackable,source)
```

Supply classification uses mechanics/item type/handler/skill effects, never
localized names. Include shots, HP/MP/CP restore, pet food and summon resources.

Deterministic bounded indexed queries, page <=256, no XML/loader/DB scan on
ordinary query. Component and combined SHA-256 hashes.

Deterministic fixture predicates:

- buy: lowest stable unlimited positive-price offer whose allowed NPC is a real
  Merchant and item is stackable supply; fallback lowest unlimited positive
  stackable offer;
- sell: exact object/count produced or test-owned from the selected buy item;
- teleport: lowest stable NORMAL route with real teleporter NPC, instance 0 and
  nonnegative fee;
- CP: exact item IDs 5591/5592 and exact discovered buylist/multisell sources.

Report selected IDs; do not hardcode a fabricated list/NPC.

## 6. Runtime quote and writer audit

Under exact materialization `ActionLease`, copy bounded immutable actor facts:
adena, requested item/object counts, load/capacity, class index, Noble, karma,
dead/combat/casting/moving/teleporting, instance, position, target and last Folk.

Immediately before mutation revalidate exact actor, NPC object/template,
list/offer/route, range, instance, price/fee, tax, weight, capacity, item
ownership/sellability and restrictions.

Audit all writers reachable through the five allowed additional files. Current
SP/item activation finding proves Java monitors are not automatically global.
Therefore commerce must not claim cross-server atomicity.

## 7. Durable receipt and ambiguity rule

Use existing component:

```text
componentType: commerce.operation
schemaVersion: 1
payload <=4096
```

Stable key:

```text
profileId + goalId + goalRevision + operationKind + canonical request hash
```

States:

```text
PREPARED → COMMITTING → COMMITTED
PREPARED → ABORTED
COMMITTING → INCONSISTENT
```

Persist `COMMITTING` before the first canonical side effect. One operation per
profile; no worker, scan or unbounded retry.

On same-key retry/restart compare exact recorded before/expected-after facts:

- exact after: mark/return idempotent success;
- exact before: safely resume once;
- one unambiguous paid/output partial: complete only the recorded missing side;
- any third-party/concurrent delta or mixed state: `INCONSISTENT`, no replay,
  no guessed compensation, no next commerce mutation for that profile.

This is conservative reconciliation, not a claim of database ACID across
ordinary server writers.

## 8. Safe execution subset

### Buy

One exact unlimited product only. Reject limited stock. Require current
Merchant/list/NPC/range/instance and exact dynamic price/tax/budget/weight/
capacity. If nonzero castle treasury side effect cannot be conserved by the
receipt, return typed `CASTLE_TREASURY_UNSUPPORTED`. Use canonical Player/
inventory APIs; no packet/bypass/direct container insertion.

### Sell

One exact owned object/count. Require `checkItemManipulation`, sellable,
reference price overflow safety and exact merchant/list/range/instance.
If refund mode or zero-sell-price semantics cannot be conserved exactly, return
typed unsupported. No “sell all junk”.

### Teleport

One exact NORMAL Gatekeeper route. Match current Noble/siege/karma/combat-flag/
free-level/discount/fee rules using an injected clock. Persist COMMITTING before
fee removal. Use canonical teleport. Restart reconciliation distinguishes source
and destination; ambiguity is INCONSISTENT.

## 9. Decision and lifecycle

Register before seal:

```text
actions: commerce.observe, commerce.buy, commerce.sell, commerce.teleport
goal types: acquire.item, maintain.supplies, sell.item, travel.teleport
```

Candidates consume only explicit persisted goals/valid sources and create at
most one mutating step. They do not create goals, scan World, choose combat
targets or call `progression.learn_skill`.

Start commerce after authoritative data dependencies and before scheduler work.
Stop/drain commerce before materialization shutdown. Disabled mode creates no
catalog/service/candidate/DB access/thread/log.

## 10. Allowed scope

Production:

```text
java/org/l2jmobius/gameserver/phantoms/commerce/**
java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java
java/org/l2jmobius/gameserver/phantoms/PhantomMetrics.java   (aggregate only)
```

Tests/build/tools:

```text
build.xml
test/java/org/l2jmobius/tests/phantoms/PhantomCommerce*.java
test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java
tools/phantoms/verify-task-014.ps1
```

Docs:

```text
docs/PHANTOM_BOTS_ROADMAP.md
docs/phantoms/PHANTOM_CODEX_EFFICIENCY_STANDARD.md
docs/phantoms/architecture/COMMERCE_SUPPLY_TRAVEL_CONTRACT.md
docs/phantoms/reviews/013b-durable-class-skill-learning-review.md
docs/phantoms/reports/014-npc-commerce-supply-travel-loop.md
docs/phantoms/tasks/014-npc-commerce-supply-travel-loop/**
```

Forbidden: server core/packet/loader edits, accepted progression/Game Knowledge,
config/schema/migrations, production DB, other chronicles/geodata, complex
multisell execution, limited stock, player/private-store trade, crafting,
enchant, tactical potion use, Goal 015/017/025.

## 11. Tests and efficient cadence

Use test DB only.

Focused suites:

```text
commerce-catalog
commerce-supply
commerce-quote
commerce-receipt
commerce-decision
commerce-server-integration
commerce-performance
```

Mandatory distinct evidence:

- independent minimal source parity;
- CP 5591/5592 mechanics and exact sources/currency;
- buy/sell/teleport conservation;
- restart at PREPARED, COMMITTING, after first effect, after final effect;
- concurrent same-item/adena ambiguity becomes INCONSISTENT, not duplicate;
- same-key idempotency and new goal revision;
- exact explicit candidates, no `progression.learn_skill`;
- disabled/lifecycle/no-worker;
- 100k static queries and 10k receipt reconciliations without leaks.

Cadence from efficiency standard:

```text
focused final: one green run
ant verify: one green run; maximum one repeat after exact failed-target fix
ant jar: one run
verifier: two final byte-identical runs
```

Full logs go to `.phantom-local/logs/014/`.

## 12. Report/verifier/completion

Report <=180 lines and includes IDs/hashes, writer audit, receipt matrix,
focused/cumulative evidence, actual READ_SET expansions and usage telemetry.

Verifier checks exact graph/scope, no packet/bypass/core edit, no multisell
execution, receipt ordering, no false atomicity claim, no learn-skill candidate,
no worker, test routes, UTF-8, JAR and deterministic read-only behavior.

On success print `GOAL_014_NPC_COMMERCE_SUPPLY_TRAVEL_LOOP_IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`. On blocker remove unsafe production mutation,
preserve catalog/audit/tests/report, create ordinary commit/push and return an
honest BLOCKED token.
