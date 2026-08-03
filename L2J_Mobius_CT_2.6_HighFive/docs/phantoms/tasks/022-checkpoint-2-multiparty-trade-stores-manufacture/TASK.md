# Goal 022 — Checkpoint 2: multi-party trade, private stores and player manufacture

## 1. Git, accepted baseline and terminal waiver

```text
branch: feature/phantom-world
required parent: feb569efa787917411cfb5c419f0e8646c3ee84f
test DB only: l2jmobiush5_phantom_test
production DB forbidden: l2jmobiush5
deterministic Goal seed: 22002202
commit subject: feat(phantoms): add multiparty trade stores and manufacture
success token: GOAL_022_CHECKPOINT_2_MULTIPARTY_TRADE_STORES_MANUFACTURE_IMPLEMENTED_PENDING_INDEPENDENT_REVIEW
```

Create exactly one ordinary child of `feb569efa787917411cfb5c419f0e8646c3ee84f` and push it to
`origin/feature/phantom-world`.

Do not amend, rebase, squash, merge, reset, force push or force-with-lease.

Goal 022 has exactly two planned checkpoints. This task is Checkpoint 2 and
completes Goal 022. Do not create Goal 022A/022B and do not start Goal 023.

Independent review accepts Checkpoint 1 at `feb569efa787917411cfb5c419f0e8646c3ee84f` with one explicit terminal
waiver:

```text
C1 final aggregate: PASS
Goal 014/021 affected regressions: PASS
verifier 021c2 and verifier 022c1 PS5/PS7: PASS, byte-identical
ant jar: PASS
plain ant verify: one unrelated historical combat timing failure
exact isolated combat rerun: PASS 20/20 without source changes
```

The failed full verify is not represented as PASS. Record it as
`ACCEPT_WITH_EXPLICIT_UNRELATED_TIMING_FLAKE_WAIVER`.

Before C2 implementation:

1. create `docs/phantoms/reviews/022-checkpoint-1-final-review.md`;
2. pin `feb569efa787917411cfb5c419f0e8646c3ee84f`;
3. record:
   - `Goal 022 Checkpoint 1: ACCEPT_WITH_EXPLICIT_UNRELATED_TIMING_FLAKE_WAIVER`;
   - `Goal 022 Checkpoint 2: IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`;
   - `Goal 022 overall: IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`;
4. make verifier 022c1 historical and descendant-compatible, reading accepted
   C1 blobs from `feb569efa787917411cfb5c419f0e8646c3ee84f`;
5. keep the waiver exact; do not rewrite history to claim the C1 plain verify
   passed.

## 2. Product result

Deliver safe active multi-party economy for:

```text
DIRECT_TRADE
PRIVATE_STORE_BUY
PRIVATE_STORE_SELL
PLAYER_MANUFACTURE
```

Supported participant combinations:

```text
Phantom ↔ Phantom
Phantom ↔ ordinary real Player
Phantom ↔ visible offline-store Player already owned by canonical server state
```

At least one participant must be a Phantom profile. The operation initiator is
a Phantom profile.

All four operations are active/perceptible social gameplay. Background mode may
quote and request materialization, but destructive execution returns
`ACTIVE_REQUIRED`. Do not create fake background players, fake clients or
invisible private stores.

The complete C2 chain:

```text
exact opportunity / strict Goal
→ immutable offer snapshot
→ consent or canonical store/manufacture listing
→ durable accepted offer
→ participant/resource reservation
→ durable DISPATCHING
→ durable OBSERVING before canonical mutation
→ packet-independent canonical action
→ exact global conservation observation
→ COMMITTED or fail-stopped INCONSISTENT
→ one audit and Goal reconciliation
```

No restart, timeout, cancellation, disconnect or retry may duplicate an item,
Adena payment, manufacture fee or product.

## 3. Current canonical owners

### Direct trade

Current packets own only client parsing, flood protection and UI:

```text
TradeRequest
AnswerTradeRequest
AddTradeItem
TradeDone
```

Current server state is held by:

```text
Player request/transaction state
Player active TradeList
network.holders.TradeList
network.holders.TradeItem
```

`TradeList.confirm()` currently orders the two list monitors by owner object ID,
validates both lists and then executes sequential item transfers.

C2 must create one packet-independent canonical direct-trade service. Ordinary
packets delegate to it without changing real-player messages, anti-cheat,
distance, refusal, request timeout or UI behavior.

### Private stores

Current packet adapters:

```text
RequestPrivateStoreBuy
RequestPrivateStoreSell
```

Current mutation methods:

```text
TradeList.privateStoreBuy
TradeList.privateStoreSell
```

The store `TradeList`, `PrivateStoreType`, package flag, exact object/count/price,
weight/capacity and canonical offline-trader state remain authoritative.

Extract one packet-independent private-store transaction service. Ordinary
packets delegate.

### Player manufacture

Current packet adapter:

```text
RequestRecipeShopMakeItem
```

Current authority:

```text
RecipeManager.requestManufactureItem
manufacturer ManufactureItem listing and price
manufacturer recipe/skill
customer ingredients/vitals/capacity/Adena
current RecipeManager maker lifecycle and RNG
```

Extend the accepted `RecipeCraftObserver` or add a compatible multi-party
observer. Do not duplicate RecipeManager formulas.

## 4. Initial READ_SET and efficiency

Initial READ_SET:

1. this package;
2. C1 final report/review/architecture and verifier 022c1;
3. economy operation/reservation/service/background/conflict/lifecycle classes;
4. `TradeRequest`, `AnswerTradeRequest`, `AddTradeItem`, `TradeDone`;
5. `TradeList`, `TradeItem`, `RequestTrade`;
6. `RequestPrivateStoreBuy`, `RequestPrivateStoreSell`;
7. private-store setup/list packets only where exact canonical offer formation is
   required;
8. `RequestRecipeShopMakeItem`, `RecipeManager`, `RecipeCraftObserver`;
9. Player public transaction/store/manufacture methods only by exact symbol;
10. materialization and Decision registration;
11. Goal 014 commerce and Goal 021 acquisition integration points.

At most thirty additional exact production files or symbols may be opened.
List each addition and reason in the report.

Do not scan all packets, every Player method, mail, warehouse, auctions, clan
code, other chronicles or all recipes.

Hard scope:

```text
new production/data files <= 28
changed production/data/config files <= 48
changed total files <= 78
new SQL migration files <= 2
no Player.java change
no Inventory/PlayerInventory core change
no new worker/thread/executor/Future/scheduled task
no offline-trade persistence redesign
no Goal 023+ work
report <=350 lines
soft Goal usage target <=1,500,000 tokens
maximum full ant verify invocations = 2
```

If canonical active operations cannot be observed and fail-stopped without
Player.java or inventory-core changes, commit/push an honest bounded BLOCKED
result. Do not create a second inventory engine.

## 5. C2 entry gate: participant lookup across link drift

C1 participant lifecycle is accepted for current single-participant execution,
but C2 must close two entry conditions before any multi-party mutation.

### 5.1. Indexed participant-only lookup

Current participant materialization lookup must not require the reservation
owner to equal the profile's **current** character link.

Authoritative lookup:

```text
operation initiator profile_id = requested profile
OR reservation profile_id = requested profile
```

Then lock all participants and revalidate the immutable
`profile → reservation owner character` snapshot.

Add one idempotent additive index for existing and fresh databases:

```text
phantom_economy_reservations(profile_id, operation_id)
```

Use current migration conventions. Do not rebuild C1 tables.

Tests:

- reservation participant with stable link;
- link changed to another character;
- link set NULL;
- profile row deleted;
- `beforeMaterialize` and `beforeStore`;
- RESERVED whole-operation abort;
- DISPATCHING/OBSERVING lifecycle block;
- no table scan in EXPLAIN.

### 5.2. Idempotent nonterminal transitions

Before returning idempotent success for an existing
`RESERVED`/`DISPATCHING`/`OBSERVING` state, revalidate participant set and links.

Participant drift cannot be hidden by an idempotent replay.

## 6. Participant model

### Phantom participant

```text
profileId > 0
exact linked character object ID
materialization lifecycle protected
participant-wide active-operation exclusivity
```

### External canonical participant

An ordinary real Player or visible canonical offline-store Player has:

```text
profileId = 0
exact character object ID
role and consent/listing evidence in the bounded intent payload
no Phantom profile/component mutation
```

Update `Reservation` and participant logic to support external participants
without weakening Phantom link checks.

The maximum participant count applies to unique character owners, including
external participants.

C2 operations use at most:

```text
2 economic owners for direct trade/store/manufacture
plus bounded service roles already represented by those owners
```

No anonymous or name-only participant identity.

## 7. Durable offer lifecycle

Add one normalized table or equivalent bounded design:

```text
phantom_economy_offers
```

Required fields:

```text
offer ID
initiating Phantom profile/character
operation kind
counterparty kind/profile/character
state
content hash
bounded offer payload
created/updated/expiry epoch
row version
terminal reason
```

States:

```text
DRAFT
OFFERED
ACCEPTED
REJECTED
EXPIRED
CANCELLED
CONSUMED
INCONSISTENT
```

Only `OFFERED` may expire into `EXPIRED`.

An accepted offer is immutable. A changed item/count/price/partner/listing creates
a new offer ID.

Payload <=4096 bytes and line count <=16 per side.

Offer identity includes:

```text
initiator
counterparty
operation kind
exact offered/requested lines
exact Adena
listing/manufacture authority hash
expiry
Goal ID/revision
```

Direct trade requires explicit counterparty acceptance. Store and manufacture
listings are canonical standing offers; acceptance is the initiating player's
exact request.

## 8. Strict C2 goals and intents

Add strict parsers:

```text
trade.exchange
private.store.buy
private.store.sell
manufacture.item
```

### trade.exchange

```text
exact partner character ID
optional exact partner Phantom profile ID
offered exact object/count lines
requested exact object/count lines
offered/requested Adena
offer expiration
maximum distance
purpose/reason
```

### private.store.buy

```text
exact store owner character ID
exact store-list hash
exact object/count/price lines
maximum total price
package flag expectation
```

### private.store.sell

```text
exact buy-store owner character ID
exact store-list hash
exact seller object/count/price lines
minimum total proceeds
```

### manufacture.item

```text
exact manufacturer character ID
exact recipe list ID
exact listing price
exact requested product item/count
maximum attempts
maximum total fee
```

Object IDs are not interchangeable for non-stackable items.

All parsers are closed-world and reject unknown constraints/sources.

## 9. Shared multi-party quote and reservation

Create a bounded immutable quote containing:

```text
operation kind
initiator/counterparty identities
offer ID/hash
authority hash
exact resource reservations
exact before evidence
capacity/weight evidence
expected outcome families
```

Reservations:

### Direct trade

- every exact offered item object/count from each Phantom participant;
- external participant lines recorded as exact observed resources;
- offered Adena for each side;
- capacity for both receivers.

### Private store buy

- buyer Adena;
- seller exact item objects/counts;
- buyer capacity;
- exact list/hash/price evidence.

### Private store sell

- store owner Adena;
- seller exact item objects/counts;
- store owner capacity;
- exact list/hash/price evidence.

### Manufacture

- customer ingredient item counts;
- customer Adena fee;
- customer capacity;
- manufacturer recipe/class skill;
- exact listing price and recipe;
- possible normal/rare output counts.

Phantom resources participate in the C1 conflict port. External resources are
revalidated immediately before canonical execution and cannot be assumed stable.

## 10. Direct-trade canonical service

Create one server-owned packet-independent service, for example:

```text
DirectTradeService
```

It owns:

```text
request
accept/refuse/expire
add exact offer line
confirm
cancel
execute/reconcile
```

Packets retain:

```text
read fields
client flood protection
packet-facing messages
anti-cheat punishment
delegate
```

Requirements:

- preserve current distance 150, instance, store, Olympiad, karma, jail,
  block-list, refusal and access checks;
- no Phantom packet invocation;
- no fake GameClient;
- exact two-sided offer snapshot;
- confirmation invalidated on any change;
- monitor/order by character object ID;
- economy participant/profile locks occur before canonical trade-list locks;
- mark operation `OBSERVING` before the first canonical transfer;
- never invoke exchange twice;
- exact all-before and all-after states recognized;
- partial exchange becomes `INCONSISTENT`;
- global item/Adena conservation is proved even for partial failure;
- no automatic compensation or reverse transfer without exact proof.

For Phantom-to-real-player:

- the real player must accept/confirm through normal server state;
- Phantom cannot forge their acceptance or alter their offer;
- disconnect/refusal/timeout aborts before effect.

## 11. Private-store canonical service

Create one server-owned packet-independent service for both directions.

### Buy from sell store

Preserve:

- SELL/PACKAGE_SELL type;
- exact list object/count/price;
- package semantics;
- overflow protection;
- buyer Adena;
- buyer weight/capacity;
- item tradeability/manipulation;
- store stock reduction;
- store close when empty;
- ordinary offline-trader callback after a committed transaction.

### Sell into buy store

Preserve:

- BUY type;
- exact item ID/object/count/price;
- store owner Adena;
- seller ownership/tradeability;
- store capacity/weight;
- buy-list remaining count;
- store close when empty;
- offline-trader callback after commit.

Requirements:

- packets delegate to one service;
- `OBSERVING` before Adena/item mutation;
- exact request/list hash revalidated under store-list lock;
- no price/count clamping hidden from the operation receipt;
- canonical partial mutation is `INCONSISTENT`, never retried;
- external real-player side remains exact but not Phantom-controlled;
- no Phantom automatically punishes a real player; packet adapter retains
  packet-specific punishment.

## 12. Phantom private-store ownership

C2 includes Phantoms operating visible active stores.

Supported:

```text
Phantom SELL store
Phantom PACKAGE_SELL store
Phantom BUY store
Phantom MANUFACTURE store
```

Store plan is durable in one bounded profile component or offer row.

Flow:

```text
exact store goal
→ reserve/verify offered resources
→ materialize
→ install canonical TradeList/manufacture list
→ set canonical PrivateStoreType
→ retain active/perceptible state
→ transact through C2 service
→ update durable remaining offer
→ close/expire/cancel
```

Rules:

- store owner cannot dematerialize while store offer is active;
- no invisible/background store;
- no offline-trade login emulation;
- restart reloads durable offer and requires materialization before reopening;
- no item is held in a hidden container;
- ordinary store mode remains the canonical resource exclusion;
- title text is bounded and not semantic authority.

## 13. Player manufacture

Extend the accepted RecipeManager observer with immutable multi-party evidence:

```text
ACCEPTED
INGREDIENTS_CONSUMED
FEE_TRANSFERRED
SUCCESS_PRODUCT
RARE_PRODUCT
CRAFT_FAILED
ABORTED
```

Each event includes:

```text
manufacturer and customer IDs
recipe/listing ID
price
exact ingredient deltas
exact product delta
customer HP/MP
manufacturer/customer Adena delta
EXP/SP consequence when current code applies it
```

No mutable Player/Item references in events.

Flow:

```text
exact manufacture listing
→ customer request and consent
→ reserve customer ingredients/Adena/capacity
→ reserve Phantom manufacturer recipe/skill when applicable
→ DISPATCHING
→ OBSERVING
→ RecipeManager.requestManufactureItem
→ exact event/inventory/Adena/vital observation
→ COMMITTED or fail-stop
```

Requirements:

- manufacturer != customer;
- exact current listing price;
- customer pays at most once;
- manufacturer receives at most once;
- ingredients consumed once;
- product/rare/failure follows current RecipeManager;
- multi-pass ALT_GAME_CREATION uses existing RecipeManager tasks only;
- cancellation after OBSERVING does not retry;
- manufacturer or customer disconnect fail-stops according to exact evidence.

Background execution is `ACTIVE_REQUIRED`.

## 14. Fault/restart contract

Add bounded fault injection to new canonical service seams.

Required active points:

```text
AFTER_OFFER_ACCEPTED
AFTER_RESERVATIONS
AFTER_DISPATCHING
AFTER_OBSERVING
AFTER_FIRST_ADENA_MUTATION
AFTER_FIRST_ITEM_TRANSFER
AFTER_EACH_TRANSFER_LINE
AFTER_RECIPE_INGREDIENTS
AFTER_PRODUCT_OR_FAILURE
AFTER_GOAL_WRITE
AFTER_OPERATION_AUDIT
```

For each operation:

```text
exact before:
    safe abort or one allowed dispatch if action not issued

exact complete after:
    reconcile COMMITTED without redispatch

partial:
    INCONSISTENT, no retry

ambiguous:
    INCONSISTENT, no retry
```

Restart tests recreate service/repositories and do not reuse in-memory offer or
TradeList objects as authority.

Global conservation:

```text
sum items by item ID across both owners
sum Adena across both owners
```

must not increase except canonical manufacture product/source branch.

## 15. Ordinary-player parity

Run parent-vs-current behavior matrices for:

### Direct trade

- invalid/self/non-player/far/instance mismatch;
- access, Olympiad, karma, jail, store mode;
- busy/request/refusal/block-list;
- add invalid/locked/confirmed item;
- accept/refuse/expiry;
- confirm distance/instance/enchant;
- success/cancel.

### Private stores

- malformed packet/request;
- wrong store type;
- range/instance/access/cursed weapon;
- package semantics;
- list price/count mismatch;
- insufficient Adena;
- weight/capacity;
- partial stock;
- offline-trader callback;
- store closes when empty.

### Manufacture

- missing/far/wrong store type;
- busy crafting;
- missing recipe/listing;
- insufficient ingredients/Adena;
- success/failure/rare;
- fee and cleanup.

Packets must contain no second mutation path after extraction.

## 16. Decision integration

Add candidates/handlers with one durable transition per step:

```text
DISCOVER_OR_LOAD_OFFER
OFFER_OR_ACCEPT
RESERVE
DISPATCH
OBSERVE_RECONCILE
CLOSE
```

Candidate support:

```text
trade.exchange
private.store.buy
private.store.sell
manufacture.item
existing acquire.item handoff where exact store/manufacture source is selected
```

A Decision step cannot request, accept, reserve and transfer in one invisible
call.

Social operations require ACTIVE or NEARBY_PERCEPTIBLE. BACKGROUND returns
materialization request/ACTIVE_REQUIRED.

## 17. Materialization and lifecycle

Participant-aware C1 lifecycle remains authoritative.

Additional rules:

- any active accepted offer blocks dematerialization of its Phantom owner;
- any DISPATCHING/OBSERVING operation blocks every Phantom participant;
- pre-dispatch offer cancellation releases all reservations;
- real-player disconnect removes consent but never rewrites Phantom Goal progress;
- store closure drains canonical store state before releasing the offer;
- shutdown closes admission, cancels pre-effect offers, fail-stops observing work,
  closes Phantom stores, then drains reservations.

No retained TradeList, requester, store type, manufacture maker, offer or
reservation after terminal cleanup.

## 18. Metrics and audit

Bounded metrics:

```text
offers drafted/offered/accepted/rejected/expired/cancelled
direct trade committed/inconsistent
private buy committed/inconsistent
private sell committed/inconsistent
manufacture success/failure/rare/inconsistent
real-player timeout/disconnect
participant conflict
store open/close
current offers/operations/reservations
```

No profile/character/object/offer IDs in labels.

Audit records exact source/sink totals for both participants, fee, operation
kind and terminal result.

## 19. Exact scope

Allowed new production/data:

```text
phantoms/economy offer/intent/multi-party services
packet-independent direct-trade/private-store services under an appropriate
server package
one strict Goal 022 C2 policy/data file if needed
up to two additive SQL migrations
```

Allowed existing production:

```text
PhantomSystem.java
phantoms/economy/**
phantoms/acquisition/** only for exact store/manufacture handoff
phantoms/player materialization boundary
phantoms/decision registration
TradeList.java
TradeItem.java only for immutable snapshot/observer/service delegation
RecipeManager.java
RecipeCraftObserver.java
TradeRequest.java
AnswerTradeRequest.java
AddTradeItem.java
TradeDone.java
RequestPrivateStoreBuy.java
RequestPrivateStoreSell.java
private-store setup/list packets only for thin delegation
RequestRecipeShopMakeItem.java
```

Forbidden:

- `Player.java`;
- Inventory/PlayerInventory core;
- fake GameClient;
- Phantom packet invocation;
- mail, freight, clan warehouse, auction house;
- offline-trade persistence redesign;
- combat/PvP/clan;
- new worker/thread/executor/Future/task;
- Goal 023+.

## 20. Mandatory focused modes

```text
economy-participant-index-c2
economy-offer-lifecycle
economy-direct-trade
economy-private-store-buy
economy-private-store-sell
economy-manufacture
economy-multiparty-restart-fault
economy-checkpoint2-performance
```

## 21. Mandatory evidence

### Entry gate

- indexed profile-only participant lookup;
- link changed/null/deleted boundary;
- idempotent transition revalidation;
- C1 regressions.

### Direct trade

- Phantom↔Phantom exact reciprocal trade;
- Phantom↔real request/accept path;
- refusal/expiry/disconnect;
- multiple same-template non-stackable objects;
- stack partial counts;
- Adena both directions;
- capacity/weight;
- offer mutation invalidates confirmation;
- reverse object/order deadlock stress;
- fault at every transfer boundary;
- no duplicate and no redispatch.

### Private stores

- Phantom buyer from real/Phantom sell store;
- real/Phantom buyer from Phantom sell store;
- Phantom seller into real/Phantom buy store;
- package sell;
- partial stock and store close;
- exact price/list hash;
- Adena/item conservation;
- offline-store callback without redesign;
- restart/fault matrix.

### Manufacture

- Phantom customer and Phantom manufacturer;
- one side real Player;
- normal/failure/rare;
- fee transfer;
- ingredients/vitals/product;
- ALT current path;
- disconnect/cancel/restart;
- no duplicate fee/product.

### Store ownership

- open/restore/close SELL/PACKAGE_SELL/BUY/MANUFACTURE;
- active/perceptible requirement;
- dematerialization blocked;
- no background/invisible/offline emulation;
- shutdown cleanup.

### Performance

```text
100000 offer hash/lookup operations
100000 participant/resource conflict checks
10000 direct-trade quote/reconcile
10000 private-store quote/reconcile
10000 manufacture quote/reconcile
10000 expiration/cleanup
```

## 22. Verification discipline

Development order:

1. C1 final review and historical verifier;
2. participant index/link-drift entry gate;
3. offer schema/model;
4. direct-trade canonical service and packet parity;
5. private-store service and packet parity;
6. Phantom store ownership;
7. manufacture observer/service;
8. Decision and Goal integration;
9. restart/fault/conservation;
10. lifecycle/shutdown/performance;
11. verifier 022c2;
12. one final `phantom-economy-checkpoint2-test`.

Use only:

```text
phantom.goal022c2.seed=22002202
```

Do not override the global Phantom seed.

After focused/static gates are green, freeze production/data/test/build/verifier:

```text
one primary plain ant verify
one standalone ant jar
ordinary commit/push
two post-commit byte-identical verifier 022c2 runs
```

Maximum two full verify invocations:

- the second is allowed after a relevant source/test/build/verifier fix; or
- after one isolated rerun proves the sole first failure was an unrelated
  timing-flake, with no source changes, as one stability confirmation.

A third full verify is forbidden.

Verifier 022c2 pins `feb569efa787917411cfb5c419f0e8646c3ee84f`, exact parent/subject/scope, C1 waiver text,
participant index, offer schema, packet delegation, canonical service ownership,
active-only social execution, multi-party conservation, fault tests, forbidden
paths, disabled behavior, UTF-8 and JAR classes. It remains
descendant-compatible.

Create:

```text
docs/phantoms/architecture/MULTIPARTY_ECONOMY_CONTRACT.md
docs/phantoms/reports/022-checkpoint-2-multiparty-trade-stores-manufacture.md
docs/phantoms/reviews/022-checkpoint-2-independent-review.md
```

Print `GOAL_022_CHECKPOINT_2_MULTIPARTY_TRADE_STORES_MANUFACTURE_IMPLEMENTED_PENDING_INDEPENDENT_REVIEW` only after every mandatory gate. Otherwise commit/push one
honest bounded result and stop without Goal 023.
