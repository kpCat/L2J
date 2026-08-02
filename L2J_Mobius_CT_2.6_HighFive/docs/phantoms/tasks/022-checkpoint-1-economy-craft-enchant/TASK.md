# Goal 022 — Checkpoint 1: economy reservation kernel, self-crafting and enchant

## 1. Git and accepted baseline

```text
branch: feature/phantom-world
required parent: 043844c0fd7a0bfcac0d5f58461a21633b032332
test DB only: l2jmobiush5_phantom_test
production DB forbidden: l2jmobiush5
deterministic Goal seed: 22002201
commit subject: feat(phantoms): add economy reservations craft and enchant
success token: GOAL_022_CHECKPOINT_1_ECONOMY_CRAFT_ENCHANT_IMPLEMENTED_PENDING_INDEPENDENT_REVIEW
```

Create exactly one ordinary child of `043844c0fd7a0bfcac0d5f58461a21633b032332` and push it to
`origin/feature/phantom-world`.

Do not amend, rebase, squash, merge, reset, force push or force-with-lease.

Goal 022 is planned as exactly two checkpoints:

```text
Checkpoint 1:
    participant-neutral reservation and operation ledger
    self-crafting execution
    item enchant execution and risk policy
    active/background conservation

Checkpoint 2:
    direct trade
    private buy/sell stores
    player manufacture
    multi-party offer/accept/expiration and handoff
```

This task is Checkpoint 1, not Goal 022A/022B and not a corrective suffix.
Do not start Checkpoint 2.

Before Goal 022 code:

1. create `docs/phantoms/reviews/021-final-review.md`;
2. pin `043844c0fd7a0bfcac0d5f58461a21633b032332` and record:
   `Goal 021 Checkpoint 1: ACCEPT`,
   `Goal 021 Checkpoint 2: ACCEPT`,
   `Goal 021 overall: ACCEPT`;
3. update roadmap/master-plan status accordingly;
4. make verifier 021c2 historical and descendant-compatible:
   - inspect accepted blobs at `043844c0fd7a0bfcac0d5f58461a21633b032332`;
   - require `043844c0fd7a0bfcac0d5f58461a21633b032332` as an ancestor of current HEAD;
   - exclude all Goal 022 paths from Goal 021 scope;
5. run verifier 021c1 and 021c2 in PowerShell 5.1 and 7.x.

Record Goal status:

```text
Goal 021: ACCEPT
Goal 022 Checkpoint 1: IMPLEMENTED_PENDING_INDEPENDENT_REVIEW
Goal 022 Checkpoint 2: NOT_STARTED
Goal 022 overall: IMPLEMENTED_PENDING_INDEPENDENT_REVIEW
Goal 023–030: NOT_STARTED unless already independently accepted
```

## 2. Product result

Deliver the first safe transaction layer for destructive economic actions.

Supported execution kinds after this checkpoint:

```text
SELF_CRAFT
ITEM_ENCHANT
```

Existing accepted operations remain unchanged:

```text
NPC BUY
NPC SELL
NORMAL TELEPORT
```

Reserved for Checkpoint 2:

```text
DIRECT_TRADE
PRIVATE_STORE_BUY
PRIVATE_STORE_SELL
PLAYER_MANUFACTURE
COUNTEROFFER
MAIL
```

The complete Checkpoint 1 chain is:

```text
exact Goal / delegated acquisition recipe plan
→ authoritative quote
→ durable operation identity
→ durable exact resource reservations
→ active canonical dispatch OR background atomic projection
→ exact outcome observation
→ one committed audit result
→ Goal/acquisition/background reconciliation
```

No item, adena, enchant level, crystal, HP, MP, EXP or SP may appear, disappear
or be charged twice because of retry, restart, activity transition or a stale
operation.

## 3. Current owners that must remain authoritative

### NPC commerce

The accepted Goal 014 `PhantomCommerceService`, receipt lifecycle and
`L2jCommerceBackend` remain authoritative for NPC buy/sell/teleport.

Checkpoint 1 may install a resource-reservation conflict port around accepted
BUY/SELL resource writers. It must not rewrite their quote, price, NPC,
teleport or conservation semantics.

### Self crafting

`RecipeManager` and current `RecipeData` own:

- known recipe and craft-skill validation;
- ingredient requirements;
- HP/MP/stat use;
- current ALT_GAME_CREATION passes and timing;
- ingredient consumption;
- success roll;
- rare/masterwork branch;
- product grant;
- self-craft lifecycle.

`RequestRecipeItemMakeSelf` remains only a client adapter. Phantom code calls a
packet-independent `RecipeManager` seam, never a packet or fake client.

### Item enchant

Current `RequestEnchantItem`, `EnchantItemData`, `EnchantScroll`,
`EnchantSupportItem` and canonical inventory/item behavior define:

- exact scroll/support/target eligibility;
- success chance and result type;
- scroll/support consumption;
- success enchant increment;
- safe-failure retention;
- blessed-failure reset;
- ordinary failure destruction/crystal consequence;
- equipped-item consequences;
- active enchant cleanup.

Extract the reusable game action into one packet-independent server service.
The ordinary packet must delegate to that service without changing real-player
semantics. Phantom code must never instantiate or call the packet.

## 4. Execution-efficiency and audit contract

Initial READ_SET:

1. this package;
2. Goal 021 final report/review and verifiers 021c1/021c2;
3. accepted Goal 014 commerce service/backend/receipt/store/tests;
4. Goal 021 acquisition recipe planner/state/service and background transaction;
5. `RecipeManager`, `RecipeData`, recipe holders/list and
   `RequestRecipeItemMakeSelf`;
6. `RequestEnchantItem`, `EnchantItemData`, `EnchantScroll`,
   `EnchantSupportItem`, enchant result enums;
7. exact item/inventory methods used by those canonical flows;
8. Background state/model/service/transaction and operation identity;
9. profile component and SQL migration conventions;
10. PhantomSystem composition/shutdown;
11. Decision candidate/handler conventions.

At most twenty additional exact production files or symbols may be opened.
List every additional item in the report with one sentence explaining why.

Do not scan all packet handlers, every item handler, all recipes, private-store
packets, direct-trade packets, mail, clan warehouse or other chronicles.

Hard limits:

```text
new production/data files <= 24
changed production/data/config files <= 38
changed total files <= 65
new SQL migration files <= 2
no Player.java change
no Inventory/PlayerInventory change
no TradeList/TradeItem change
no private-store/direct-trade/player-manufacture implementation
no new worker/thread/executor/Future/scheduled task
no Goal 023+ work
report <= 300 lines
soft Goal usage target <= 1,200,000 tokens
maximum full ant verify invocations: 2
```

If current canonical craft/enchant behavior cannot be observed or reconciled
without changing `Player.java`, inventory internals or duplicating formulas,
commit/push an honest bounded `BLOCKED` result. Do not invent a second economy
engine.

## 5. Strict economy policy data

Create:

```text
dist/game/data/phantoms/economy/high-five-economy-v1.xml
```

The loader must be ordered, strict, XXE-safe and content-addressed.

Required policy:

```text
operation payload <=4096 bytes
audit payload <=4096 bytes
reservations per operation <=32
distinct item IDs per operation <=24
participants per operation <=4
active operation per profile <=1
maximum retained nonterminal operations <=100000
reservation TTL 30000..600000 ms
dispatch observation timeout 30000..300000 ms
maximum craft attempts per Goal <=32
maximum enchant attempts per Goal <=16
maximum candidate scrolls <=16
maximum candidate support items <=8
maximum operation retries <=3
maximum audit rows per profile retained by cleanup policy <=256
```

Add explicit enchant risk defaults:

```text
never enchant without an exact scroll
support item must be explicitly selected
no automatic over-enchant beyond desired level
no retry after ambiguous outcome
no attempt when estimated replacement reserve is insufficient
no ordinary-destruction risk unless Goal explicitly permits it
safe/blessed/ordinary policies remain distinct
equipped target in background = ACTIVE_REQUIRED
```

Policy/data hashes include every field that changes eligibility, formula,
reservation, expiration or consequence.

## 6. Persistence schema and operation identity

Add idempotent MariaDB migration(s) for three normalized tables or one
equivalent normalized design:

```text
phantom_economy_operations
phantom_economy_reservations
phantom_economy_audit
```

### Operation row

Must contain at least:

```text
operation ID
initiating profile ID
canonical character object ID
Goal ID/revision
operation kind
state
activity generation/tick sequence when background
authority hash
intent hash
bounded intent payload
bounded expected/outcome payload
created/updated epoch
reservation expiry epoch
row version
terminal result/reason
```

States:

```text
PREPARED
RESERVED
DISPATCHING
OBSERVING
COMMITTED
ABORTED
EXPIRED
INCONSISTENT
```

Only `PREPARED` and `RESERVED` may expire automatically.

`DISPATCHING` and `OBSERVING` require exact reconciliation. They must never
silently expire into a redispatch.

### Reservation row

One canonical row per exact resource key:

```text
operation ID
ordinal
owner character object ID
owner class index
resource kind
item object ID when exact object-bound
item ID
reserved count
expected canonical count/enchant level/location
```

Resource kinds include:

```text
ITEM_OBJECT
ITEM_COUNT
ADENA
```

Do not reserve HP/MP/EXP/SP as item-like resources. Store them as expected
canonical facts in the operation payload and validate them under the commit
transaction.

Reservations are logical claims; reserving never moves or destroys resources.

### Audit row

Append one bounded terminal audit record for each committed, aborted, expired
or inconsistent significant operation.

The audit records source/sink deltas, outcome and authority hashes. It contains
no account password, IP, chat text or unbounded object dump.

### Identity

Operation ID is deterministic over:

```text
profile
character
Goal ID/revision
operation kind
attempt sequence
exact intent
authority hashes
activity generation/tick when background
```

Exact replay returns the prior terminal result.

A changed Goal revision, recipe, item object, scroll/support, source authority or
activity generation cannot reuse the old operation.

## 7. Global lock order and reservation ownership

Use one documented stable order everywhere:

```text
1. participating profile rows ordered by profile ID
2. economy operation row
3. economy reservation rows ordered by canonical resource key
4. Goal/component rows ordered by profile ID then component type
5. canonical character/subclass rows ordered by character ID/class index
6. recipe/skill evidence rows
7. item rows ordered by owner ID then object ID
```

A non-locking operation lookup may be used to discover participant IDs before
the transaction. After locks are acquired, every identity is revalidated.

No code path may lock item rows and then attempt to acquire a profile or
economy-operation lock.

Create a shared, no-worker `PhantomEconomyReservationService` or equivalent.
It owns:

```text
reserve
renew before dispatch
release pre-dispatch
mark dispatching
reconcile
commit terminal audit
expire safe reservations
shutdown drain
```

It must support multiple owner IDs in one operation even though C1 uses one.
This is the extension point for Checkpoint 2, not a hidden direct-trade
implementation.

### Integration conflicts

Install a narrow conflict port in:

- accepted NPC BUY/SELL;
- acquisition/background item mutations;
- materialization/dematerialization boundary;
- Checkpoint 1 craft/enchant.

A Phantom-owned writer must not mutate an exact reserved resource for a
different operation.

Ordinary real-player ownership after login remains protected by the accepted
identity/materialization handoff. Checkpoint 1 does not globally intercept all
ordinary player inventory actions.

## 8. Goal and acquisition integration

### Recipe handoff

Goal 021 remains the owner of acquisition planning.

Checkpoint 1 may execute a recipe only when an exact accepted
`PhantomAcquisitionState` contains:

```text
selected method = RECIPE_PREPARATION
status = PLANNING_ONLY
exact RecipePlan
no unresolved deficits
exact current recipe/knowledge/progression hashes
```

Before reservation, re-read exact active/background ingredient inventory and
prove every RecipePlan node.

Do not recompute a different recipe alternative after the Goal 021 plan has
been selected.

One craft attempt is one economy operation.

After the attempt:

- product delta is reconciled against the immutable acquisition baseline;
- success may complete or advance the existing `acquire.item` Goal;
- canonical craft failure commits ingredient/vital consequences but no product;
- a failed attempt records source failure and bounded replan;
- ambiguous dispatch becomes `INCONSISTENT`, never a blind repeat.

### Enchant Goal

Add strict goal type:

```text
enchant.item
```

Required facts:

```text
exact target item object ID
desired enchant level
maximum attempts
expense/risk budget
whether ordinary destruction is permitted
allowed scroll item IDs
allowed support item IDs
purpose/reason
```

The target object ID cannot be replaced by another same-item stack.

Completion means the exact surviving target object reaches the desired level.
If canonical failure destroys it, the Goal records the terminal loss; it does
not silently substitute another object.

## 9. Active self-crafting

Add a narrow structured observer seam to `RecipeManager`.

Old public packet-facing methods remain compatible.

The observer/token must expose immutable events:

```text
ACCEPTED
INGREDIENTS_CONSUMED
SUCCESS_PRODUCT
RARE_PRODUCT
CRAFT_FAILED
ABORTED
```

Each event includes exact recipe, crafter/target identities and bounded item/
vital consequences. It exposes no mutable `Player`, `Item` or maker object to
Phantom code.

Flow:

```text
exact actor ActionLease
→ quote current RecipeManager facts
→ persist RESERVED
→ persist DISPATCHING
→ invoke canonical self-craft
→ observe existing maker/callback and exact inventory/vitals
→ reconcile one canonical outcome
→ commit economy/acquisition/Goal result
```

Requirements:

- only self craft (`crafter == target`) in C1;
- recipe already known;
- exact craft skill and level;
- ingredients and output capacity exact;
- actor not dead, in trade/store/combat/duel/another craft;
- current shipped ALT_GAME_CREATION behavior is supported or task blocks;
- existing RecipeManager task/timing owns asynchronous passes;
- Phantom adds no task/thread/future;
- cancellation before dispatch releases reservations;
- cancellation after dispatch waits/reconciles canonical outcome;
- no Phantom direct ingredient destruction or product grant;
- ordinary packet self-craft uses the same canonical implementation.

## 10. Background self-crafting

Background craft uses one existing Background/economy transaction, not
`RecipeManager` with a fake Player.

Reproduce current shipped self-craft rules exactly:

```text
known recipe
craft skill/level
recipe ingredient quantities
HP/MP/stat-use types
ALT_GAME_CREATION passes
success rate
rare/masterwork eligibility and chance
product/rare counts
ingredient consumption on success and failure
EXP/SP effects when present
weight/capacity
current config/rate inputs
```

Unsupported recipe stat type or formula drift is `ACTIVE_REQUIRED` or
authority stale, never an approximation.

One attempt:

- locks exact recipe/skill/item/vital evidence;
- reserves/validates all ingredients;
- consumes ingredients exactly once;
- advances deterministic Background RNG exactly once per canonical roll;
- grants at most one canonical product branch;
- commits background state, economy operation/audit, acquisition and Goal in one
  transaction;
- allocates item object IDs idempotently;
- exact replay is idempotent;
- any fault rolls back all item/vital/Goal/background/economy changes.

## 11. Packet-independent enchant service

Extract current reusable enchant action into a server-owned service, for example:

```text
EnchantItemService
```

The current packet remains responsible only for:

```text
read client fields
resolve Player
client flood/anti-cheat timing
send packet-facing response
delegate exact action
```

The service owns canonical validation and mutation for both ordinary players and
Phantoms.

Structured request:

```text
actor
target item object ID
scroll object ID
optional support object ID
operation observer/token
```

Structured result:

```text
ERROR
SUCCESS
SAFE_FAILURE
BLESSED_RESET
DESTROYED_WITH_CRYSTALS
```

The service must preserve current:

- scroll/support validation and consumption order;
- result calculation;
- enchant increment/reset/destruction;
- crystal item/count;
- equip/skill/user-info consequences;
- item DB updates;
- active-enchant cleanup.

Ordinary packet regression must prove before/after behavior parity.

Phantom active flow persists `DISPATCHING` before calling the service and
reconciles only the exact target/scroll/support operation.

No Phantom call to `RequestEnchantItem`, no fake timestamp and no punishment
path for a valid server-owned Phantom action.

## 12. Enchant risk policy

Before reservation, calculate a bounded decision from exact current facts:

```text
target replacement/reference value
current and desired enchant
scroll/support counts and values
current success chance/result family
safe/blessed/ordinary type
spare/backup evidence
Goal expense/risk limits
attempt count
```

Hard rules:

- never exceed desired level;
- exact target object required;
- exact scroll and support objects required;
- no support substitution after reservation;
- ordinary destruction requires explicit Goal permission;
- ambiguous prior result blocks every retry;
- destroyed target ends the Goal with terminal loss;
- maximum attempts and expense are conserved across restart;
- no expected-value model overrides canonical chance.

## 13. Background enchant

Background enchant may execute only when the exact target is:

```text
owned by the canonical character
in INVENTORY, not PAPERDOLL
not time/lease/augment/attribute/special-state unsupported
valid for the exact scroll/support
```

An equipped target returns `ACTIVE_REQUIRED`.

Use current `EnchantItemData` and `EnchantScroll` facts with the existing
Background RNG.

One atomic transaction locks target, scroll, support, capacity and every
possible crystal-result row.

Branches:

```text
SUCCESS:
    consume scroll/support
    target enchant +1

SAFE_FAILURE:
    consume scroll/support
    target unchanged

BLESSED_RESET:
    consume scroll/support
    target enchant = 0

DESTROYED_WITH_CRYSTALS:
    consume scroll/support
    destroy target object
    grant exact canonical crystal item/count
```

Any `ERROR` or unsupported consequence rolls back.

The operation, audit, Goal and Background state commit in the same transaction.
Replay cannot consume another scroll or destroy another item.

## 14. Active/background transitions and restart

For `RESERVED`:

- expiration may release reservations if no canonical effect started.

For `DISPATCHING`/`OBSERVING`:

- materialization transition is blocked;
- background execution is blocked;
- restart loads and reconciles exact before/after facts;
- exact before may permit one bounded redispatch only when no action was issued;
- partial or ambiguous facts become `INCONSISTENT`;
- exact after becomes `COMMITTED`.

Dematerialization must not occur while RecipeManager maker, enchant action or
economy dispatch claim exists.

Materialization after a background result observes already committed canonical
items/vitals and does not replay the operation.

Shutdown:

```text
economy admission closes
→ pre-dispatch reservations abort/release
→ dispatched craft/enchant operations reconcile or persist uncertainty
→ economy claims drain
→ dependent services stop in existing order
```

## 15. Decision integration

Create one `PhantomEconomyDecision` or equivalent candidate family.

One Decision step performs at most one durable transition:

```text
QUOTE
RESERVE
DISPATCH
OBSERVE
RECONCILE
```

Do not run quote, reserve and destructive dispatch invisibly in one Decision
step.

Candidate support:

```text
acquire.item with exact delegated RecipePlan
enchant.item
```

Explanations expose bounded reason keys, not item dumps.

## 16. Metrics and audit

Bounded metrics:

```text
prepared/reserved/dispatched/committed/aborted/expired/inconsistent
craft success/failure/rare
enchant success/safe failure/blessed reset/destroyed
reservation conflict
reconciliation outcome
current operations/reservations
```

No profile, character, item object or operation IDs in metric labels.

Audit sums:

```text
items consumed
items produced
adena source/sink
crystals produced
target items destroyed
```

These are audit facts, not mutable economy counters used as authority.

## 17. Exact scope

Allowed new production/data:

```text
java/org/l2jmobius/gameserver/phantoms/economy/**
dist/game/data/phantoms/economy/high-five-economy-v1.xml
sql migration(s) for Goal 022 C1
packet-independent enchant service under an existing appropriate server package
```

Allowed existing production:

```text
PhantomSystem.java
phantoms/commerce/**
phantoms/acquisition/** only for exact recipe handoff/conflict integration
phantoms/background/**
phantoms/player materialization boundary
phantoms/decision registration
RecipeManager.java
RequestEnchantItem.java only as a thin delegate adaptation
```

Allowed tests/build/tools/docs:

```text
build.xml
new economy/craft/enchant suites
targeted commerce/acquisition/background/materialization regressions
PhantomTestLauncher.java
tools/phantoms/verify-task-021c1.ps1 only if historical adaptation is needed
tools/phantoms/verify-task-021c2.ps1 historical adaptation
tools/phantoms/verify-task-022c1.ps1
Goal 021 final review
Goal 022 C1 architecture/report/review/task docs
roadmap/master-plan status
```

Forbidden:

- `Player.java`;
- Inventory/PlayerInventory and Item core rewrites;
- TradeList/TradeItem;
- TradeRequest/AnswerTradeRequest/AddTradeItem/TradeDone;
- all private-store packets and execution;
- player manufacture;
- mail/clan warehouse/auction house;
- combat/PvP policy;
- fake GameClient or internal packet invocation;
- direct Phantom ingredient destruction/product grant/enchant mutation;
- new worker/thread/executor/Future/task;
- other chronicles/geodata;
- Goal 022 Checkpoint 2 or Goal 023+.

## 18. Mandatory focused modes

```text
economy-reservation-schema
economy-reservation-concurrency
economy-self-craft-active
economy-self-craft-background
economy-enchant-active
economy-enchant-background
economy-restart-transition
economy-lifecycle-performance
```

## 19. Mandatory evidence

### Schema/reservations

- migration idempotency and rollback;
- exact operation/reservation/audit bounds;
- duplicate resource reservation rejected;
- same operation replay accepted;
- different operation conflict;
- expiration only before dispatch;
- multi-owner rows and deterministic order supported without executing trade;
- deadlock stress with reversed request order;
- shutdown and restart.

### Active craft

- real materialized Phantom;
- actual known recipe and craft skill;
- actual RecipeManager self-craft;
- current ALT_GAME_CREATION path;
- ingredient consumption;
- success, failure and rare branch where current data permits;
- HP/MP/EXP/SP consequences;
- cancellation before/after dispatch;
- crash at every event boundary;
- no packet/direct mutation;
- ordinary player packet regression.

### Background craft

- real RecipeData recipes covering common/dwarven, success/failure, rare and
  stat-use branches;
- exact active/background formula parity;
- ingredient/output/vital conservation;
- one transaction fault matrix;
- replay;
- capacity/weight;
- acquisition RecipePlan handoff and Goal progress.

### Active enchant

- real target/scroll/support objects;
- ordinary packet and Phantom service use the same action;
- success;
- safe failure;
- blessed reset;
- ordinary destruction/crystals;
- equipped-item consequence;
- exact object ownership;
- scroll/support consumed once;
- restart after dispatch;
- no punishment/fake timestamp path.

### Background enchant

- success/safe/blessed/destruction with deterministic RNG;
- exact current chances and crystal result;
- target/scroll/support locks;
- equipped target ACTIVE_REQUIRED;
- capacity rollback;
- item/enchant/audit/Goal/background atomicity;
- replay and source drift.

### Integration

- NPC BUY/SELL conflict with reserved item/adena;
- acquisition/background conflict with reserved ingredients;
- materialization/dematerialization blocked during dispatch;
- Goal revision drift;
- authority/config drift;
- disabled mode;
- no retained claims/tasks.

### Performance

```text
100000 reservation conflict checks
100000 craft/enchant quotes
10000 background craft operations
10000 background enchant operations
10000 replay reconciliations
```

No unbounded scan, query or allocation per profile.

## 20. Verification discipline

Development order:

1. Goal 021 final review and historical verifiers;
2. schema/operation/lock-order tests;
3. reservation conflict and expiry;
4. craft authority and packet-independent observer seam;
5. active craft;
6. background craft;
7. enchant service extraction and ordinary packet regression;
8. active enchant;
9. background enchant;
10. acquisition/commerce/materialization integration;
11. restart/transition/shutdown;
12. lifecycle/performance;
13. verifier 022c1;
14. one final `phantom-economy-checkpoint1-test`.

Use only:

```text
phantom.goal022c1.seed=22002201
```

Do not override the global Phantom test seed.

After focused/static gates are green, freeze production/data/test/build/verifier:

```text
one final plain ant verify
one standalone ant jar
update report terminal section only
ordinary commit/push
two post-commit byte-identical verifier 022c1 runs
```

A second aggregate/full verify is permitted only after a real relevant
production/test/build/verifier fix. A third is forbidden.

Verifier 022c1 must pin `043844c0fd7a0bfcac0d5f58461a21633b032332`, exact parent/subject/scope, schema,
lock order, reservation states, craft/enchant ownership, active/background
tests, forbidden paths, disabled behavior, UTF-8, datapack and JAR classes.
It must remain descendant-compatible.

Create:

```text
docs/phantoms/architecture/ECONOMY_TRANSACTION_CONTRACT.md
docs/phantoms/reports/022-checkpoint-1-economy-craft-enchant.md
docs/phantoms/reviews/022-checkpoint-1-independent-review.md
```

Print `GOAL_022_CHECKPOINT_1_ECONOMY_CRAFT_ENCHANT_IMPLEMENTED_PENDING_INDEPENDENT_REVIEW` only after every mandatory gate. Otherwise commit/push one
honest bounded result and stop without starting Checkpoint 2.
