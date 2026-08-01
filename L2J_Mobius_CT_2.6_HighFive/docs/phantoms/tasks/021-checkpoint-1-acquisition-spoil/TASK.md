# Goal 021 — Checkpoint 1: acquisition kernel, recipe planning and spoil/sweep chains

## 1. Git and accepted baseline

```text
branch: feature/phantom-world
required parent: d48dccb42dcfe5993f1c852e021086e498c0622d
test DB only: l2jmobiush5_phantom_test
production DB forbidden: l2jmobiush5
deterministic seed: 21002101
commit subject: feat(phantoms): add acquisition planning and spoil chains
success token: GOAL_021_CHECKPOINT_1_ACQUISITION_SPOIL_IMPLEMENTED_PENDING_INDEPENDENT_REVIEW
```

This is the first of two checkpoints planned before Goal 021 implementation:

```text
Checkpoint 1:
    acquisition kernel
    authoritative source planning
    recipe ingredient DAG
    ordinary death-drop acquisition
    spoil/sweep active and background parity

Checkpoint 2:
    manor sow/harvest chains
    curated quest collection
    transition-safe canonical quest/manor integration
```

It is not Goal 021A/021B and is not a corrective suffix.

Create exactly one ordinary child and push to `origin/feature/phantom-world`.
Do not amend, rebase, squash, merge, reset, force push or force-with-lease.
Publish one honest SUCCESS, PARTIAL or BLOCKED commit.

Record:

```text
Goal 020: ACCEPT
Goal 021 Checkpoint 1: IMPLEMENTED_PENDING_INDEPENDENT_REVIEW
Goal 021 Checkpoint 2: NOT_STARTED
Goal 022–027: NOT_STARTED
```

Create `docs/phantoms/reviews/020-conversation-final-review.md`, pin the required
parent and verdict `ACCEPT`.

## 2. Product result

Implement one bounded acquisition capability:

```text
ACTIVE acquire.item Goal
→ observe exact baseline inventory
→ derive authoritative source candidates
→ choose one exact source
→ persist bounded acquisition.state
→ travel / active or background acquisition
→ observe canonical inventory delta
→ update progress from observed count
→ switch source only through typed policy
→ complete without fabricating an item
```

Checkpoint 1 executable methods:

```text
DEATH_DROP
SPOIL_SWEEP
```

Checkpoint 1 planning-only method:

```text
RECIPE_PREPARATION
```

Explicitly not implemented here:

```text
MANOR_CROP execution
QUEST_COLLECTION execution
craft execution
recipe learning
ingredient consumption
player trade
private stores
buy/sell offers
enchant
mail
warehouse
Goal 022 transaction ledger
```

Manor and quest facts may be recognized as future alternatives, but must be
typed `DEFERRED_CHECKPOINT_2` and cannot be selected or create items.

## 3. Execution-efficiency contract

Do not reread old task packages, all reports, `Player.java`, `Party.java`, every
quest script, all skill handlers, all recipes, all NPC data or unrelated
subsystems.

Initial READ_SET:

1. this package;
2. Goal 021 roadmap/master-plan sections;
3. final Goal 020 report/review and verifier 020c2;
4. `PhantomGameKnowledgeQuery`, acquisition-related immutable facts and snapshot
   hashes;
5. `PhantomBackgroundService`, `PhantomBackgroundAuthority`,
   `PhantomBackgroundModel`, `PhantomBackgroundState`,
   `PhantomBackgroundTransaction`, `PhantomBackgroundGoalSpec`,
   `PhantomBackgroundDecision`;
6. `PhantomProgressionService`, capability catalog/evaluator and exact
   `profession.spoil`, `profession.sweep`, `profession.craft` data;
7. `PhantomCombatService`, `PhantomCombatBackend`,
   `PhantomCombatActorLease`, `L2jCombatBackend`, combat step handlers;
8. Decision Goal/plan/step/store contracts;
9. topology exact node/anchor queries;
10. `PhantomSystem` production composition, snapshot and shutdown;
11. exact current spoil/sweep skill handlers and Monster spoil-state methods;
12. existing background/combat/knowledge/progression/headless-player tests.

At most twelve additional exact files or symbols, each listed with one sentence
in the report. No broad repository search after the audit.

Hard limits:

```text
new production/data files <= 16
changed production/data/config files <= 30
changed total files <= 54
no schema migration
no Player.java or Party.java change
no existing skill/quest/chat handler implementation change
no new worker/thread/executor/Future/scheduled task
no Goal 022+ work
report <= 240 lines
soft Goal usage target <= 1,100,000 tokens
maximum full ant verify invocations: 2
```

Task-package files count toward total scope but not production/data scope.

If safe active spoil requires changing a canonical skill handler, or background
spoil cannot be made atomic with existing transaction ownership, stop with a
bounded BLOCKED result. Do not invent another suffix or bypass canonical logic.

## 4. Goal 020 finalization and historical verifier

Before production work:

1. create the final independent Goal 020 review;
2. update roadmap/master-plan status to `Goal 020: ACCEPT`;
3. make verifier 020c2 historical/descendant-compatible:
   - pin `d48dccb42dcfe5993f1c852e021086e498c0622d` as the final accepted Goal 020 tree;
   - verify exact implementation/completion ancestry and subjects;
   - inspect Goal 020 blobs at the pinned commit;
   - require it as ancestor of future HEAD;
   - never include Goal 021 paths in Goal 020 scope;
4. run verifier 020c2 once.

Do not modify Goal 020 runtime behavior.

## 5. Strict acquisition policy

Create:

```text
dist/game/data/phantoms/acquisition/high-five-acquisition-v1.xml
```

The file is UTF-8, strict, XXE-safe and content-addressed. It declares only
method semantics, limits, scoring weights and reason keys. It must not duplicate
the server NPC/drop/spoil/recipe/manor corpus.

Required sections:

```text
limits
methods
sourceScoring
switchPolicy
recipePlanning
reasonKeys
```

Required method keys and statuses:

```text
death_drop         EXECUTABLE
spoil_sweep        EXECUTABLE
recipe_preparation PLANNING_ONLY
manor_crop         DEFERRED_CHECKPOINT_2
quest_collection   DEFERRED_CHECKPOINT_2
```

Hard bounds:

```text
source candidates <= 8
spawn areas per source <= 4
recipe alternatives per product <= 4
recipe depth <= 6
recipe nodes <= 48
leaf deficits <= 32
tracked acquisition receipts <= 8
source failures <= 8
source switches <= 4
operations per Decision step <= 8
component payload <=4096
active target distance <=2000
spoil/sweep verification attempts <=3
```

All weights and thresholds are integer-only. No per-item, per-NPC, per-class or
Russian phrase switch in Java.

## 6. Root Goal contract

Add one exact Goal type `acquire.item`.

Root Goal meaning:

```text
target: item:<positive item ID>
requiredAmount: positive incremental amount requested by this Goal
currentAmount: observed acquired delta, never blindly incremented
purposeKey: acquisition.item
validSources: optional acquisition.method allowlist
selectedAnchor: null until one exact source is selected
```

Constraints:

```text
acquisition.baseline_count
acquisition.maximum_switches
acquisition.preferred_method_code optional
```

The baseline is the exact inventory count when acquisition state is first
created. Progress is always:

```text
clamp(current authoritative count - baseline count, 0, requiredAmount)
```

Pre-existing items do not count as newly acquired progress. A source operation
may never mutate `currentAmount` directly without observing authoritative
inventory/background state.

Reject non-ACTIVE Goals, invalid item targets, zero/negative/overflow amount,
unknown constraints, inconsistent currentAmount, stale Goal identity/revision,
and another Goal type reusing acquisition state.

## 7. Bounded acquisition.state

Use the existing profile component table:

```text
componentType: acquisition.state
schemaVersion: 1
payload <=4096
```

No schema change.

Store:

```text
acquisition catalog hash
Game Knowledge combined hash
topology hash
progression catalog hash
background model/hash generation
Goal ID and revision
target item ID
required amount
baseline count
last observed count
observed progress
status
selected source
up to eight ranked source candidates
source cursor/switch count/failure counters
active runtime phase
exact active target object/NPC/instance identity when claimed
bounded recipe plan
up to eight receipts
logical minute
```

Do not store a second inventory ledger, raw item objects, full Game Knowledge
facts, full recipe corpus, mutable server objects or unbounded event history.

Required state statuses:

```text
PLANNING
READY
ACTIVE
BLOCKED
COMPLETED
FAILED
STALE_AUTHORITY
DEFERRED_CHECKPOINT_2
INCONSISTENT
```

Active phases:

```text
NONE
TRAVEL_REQUIRED
TARGET_REQUIRED
SPOIL_PREPARED
SPOIL_DISPATCHING
SPOIL_OBSERVED
COMBAT_SUBMITTED
COMBAT_TERMINAL
SWEEP_PREPARED
SWEEP_DISPATCHING
VERIFYING
```

Receipts contain full operation ID, source ID, phase kind, before/after inventory
count, terminal result and logical minute.

Codec requirements: deterministic compact binary; strict order/uniqueness;
unknown version/status/method, duplicate source, truncated/trailing bytes,
invalid counts and stale phase combinations fail closed; declared worst case
<=4096; no silent truncation.

## 8. Source identities

Every candidate is a compact reference to authoritative data, not copied truth.

### Death drop

```text
method DEATH_DROP
NPC ID
item ID
exact DropFact stable key(s)
topology node and anchor IDs
instance ID
Game Knowledge hash
```

Only `DropSourceKind.DEATH_DROP`.

### Spoil/sweep

```text
method SPOIL_SWEEP
NPC ID
item ID
exact SPOIL DropFact stable key(s)
topology node and anchor IDs
instance ID
spoil and sweep capability rule refs
Game Knowledge/progression hashes
```

Only `DropSourceKind.SPOIL`.

### Recipe preparation

```text
method RECIPE_PREPARATION
recipeListId
product item/count
recipe item ID
craft level/success rate/dwarven flag
exact ingredient nodes
Game Knowledge hash
```

No execution identity is created for manor or quest in Checkpoint 1. Stable
source ID is full SHA-256 over canonical fields and authority hashes.

## 9. Authoritative source planner

Create one read-only planner over current immutable authorities.

Input includes profile/acquisition identity, target item, remaining amount,
activity state, inventory snapshot, class/capability snapshot, allowed method
set and current topology position.

Output includes ranked candidates, recipe preparation plan, typed blocked or
deferred reasons and canonical evidence.

Use only indexed bounded queries:

```text
Game Knowledge dropSources
Game Knowledge spoilSources
Game Knowledge spawnAreas / spawnFacts
Game Knowledge recipesProducing / recipesUsing
Game Knowledge item facts
topology node/anchor queries
progression capability catalog
current inventory/background facts
```

No scan of all NPCs/items/recipes on a request.

For each drop/spoil source require a normal attackable/targetable monster, one
instance-0 topology-mapped spawn area, at most four valid areas, a valid anchor,
exact item/NPC/source refs, and positive finite chance/count facts.

Scoring is generic integer-only: method preference, topology cost, level gap,
chance/count utility, spawn capacity, resource reserve, failure/cooldown and
switch penalties, recipe leaf reuse. Near-tied top candidates return
`BLOCKED source.ambiguous` rather than lexical guessing.

## 10. Eligibility

### Active

Use current materialized Progression capability evaluations and exact known
skill evidence. `SPOIL_SWEEP` requires both `profession.spoil` and
`profession.sweep` with exact known skill IDs/levels and legal NPC scope.

### Background

Use only durable background facts: active class, level, auto-get skills,
progression hash and exact capability evidence. Background spoil is allowed only
when every evidence skill required by selected spoil/sweep rules is present at
the required level in durable background state. Do not infer capability merely
because a class could eventually learn it.

### Targeted progression-data completion

Audit exact High Five rules for `profession.spoil`, `profession.sweep` and
`profession.craft`. If missing, add the smallest data-only rules validated
against current class skill trees. Do not change progression Java semantics.
Every rule needs exact skill ID/level and source path. If proof is unavailable,
fail closed and report the missing evidence.

## 11. Recipe ingredient DAG

Implement deterministic planning only:

1. enumerate at most four exact recipes producing the item;
2. reject invalid/stale facts;
3. choose by observable recipe evidence, craft eligibility, fewer missing leaf
   units, lower depth/node count, then stable recipeListId;
4. recursively expand ingredients;
5. subtract authoritative inventory counts once;
6. use ceiling division for product batches;
7. detect cycles;
8. enforce depth 6, nodes 48 and deficits 32;
9. expose bounded death-drop/spoil alternatives for leaves;
10. type manor/quest leaves deferred rather than omit them.

Store recipeListId, requested output, batch count, product output, factual success
rate, ordered nodes, deficits, craft evidence and missing recipe/skill reason.

No crafting, recipe learning, ingredient reservation/consumption, success roll
or rare-product assumption. Goal 022 owns those transactions.

## 12. Active acquisition owner

Do not create a second combat loop.

Extend existing Combat external-action ownership with one typed kind
`ACQUISITION`. One acquisition Decision step advances at most one persisted
phase and uses existing Combat sessions for killing.

### Death drop flow

```text
select exact current monster instance for source NPC
→ start existing Combat session
→ observe terminal kill
→ use existing canonical loot/pickup behavior
→ verify target inventory count
```

Do not reimplement attack/cast/pickup logic.

### Spoil/sweep flow

```text
claim ACQUISITION external action
→ validate actor/target/instance/distance/source NPC
→ validate exact spoil skill/capability
→ persist SPOIL_DISPATCHING before cast
→ invoke canonical skill cast through L2j combat actor lease
→ observe canonical target spoil state
→ release external action
→ start existing Combat session for the same target
→ observe exact dead corpse
→ claim ACQUISITION external action
→ validate sweep skill/capability and corpse eligibility
→ persist SWEEP_DISPATCHING before cast
→ invoke canonical sweep cast
→ observe inventory delta and sweep terminal state
→ verify progress
```

Production acquisition code must not call `Player.addItem`, inventory add APIs,
Monster spoil mutators, direct drop generation or direct EXP/SP grants. It may
invoke current canonical skill/Combat seams and observe facts.

Recovered `SPOIL_DISPATCHING` or `SWEEP_DISPATCHING` becomes VERIFYING/UNCERTAIN,
not a blind recast. Reconcile exact target identity/state, spoil owner/state,
sweep state, before/after inventory count and receipt. Missing target without
inventory proof is uncertain, never success.

## 13. Background death-drop and spoil parity

Extend current background authority/model/transaction, not a parallel simulator.

Add exact modes:

```text
ORDINARY_DEATH_DROP
ACQUISITION_DEATH_DROP
ACQUISITION_SPOIL_SWEEP
```

Pin root Goal ID/revision, acquisition row version, selected source ID, item ID,
exact fact keys, before count and authority hashes.

Ordinary Goal 015 behavior remains compatible. Acquisition death drop uses only
selected death-drop facts for target progress. Background spoil requires durable
spoil+sweep capability evidence and selected SPOIL facts. No spoil item is
awarded if eligibility, spoil or sweep fails. Death drops remain separately
attributable. No manor or quest reward generation.

Extend the existing background transaction command so one DB transaction
commits:

```text
character progress/vitals
exact item mutations
background.state + receipt
goal.runtime currentAmount/status
acquisition.state + receipt/progress
```

All expected row versions are required. Any conflict rolls back all mutations.
No public arbitrary SQL callback. After commit, currentAmount equals progress
derived from committed inventory. Restart verifies every component and item
invariant before another batch.

## 14. Source switching

Switch only when no active Combat/external/background operation remains.

Typed reasons:

```text
source.exhausted
source.ineligible
source.target_unavailable
source.resource_reserve
source.inventory_capacity
source.authority_stale
source.repeated_failure
source.ambiguous
```

Maximum four switches and eight failures per source. Selection of the next
candidate is deterministic. Same source cannot repeat without changed authority
or expired cooldown. Switch persists before action. Baseline/progress never
reset. No candidate yields honest BLOCKED/FAILED.

## 15. Decision integration

Create `PhantomAcquisitionDecision`.

```text
candidate: candidate.acquisition.item
Goal type: acquire.item
Allowed states: ACTIVE, WARM, BACKGROUND
```

Actions:

```text
acquisition.plan
acquisition.travel
acquisition.active.advance
acquisition.background.advance
acquisition.verify
acquisition.switch
```

Every step is exact to Goal ID/revision, source ID and acquisition generation.
ACTIVE/WARM uses materialization/navigation/combat; BACKGROUND uses the existing
background transaction. Existing Party participation restrictions remain.
Each step is bounded and returns typed SUCCESS/RETRY/REPLAN/FAIL_GOAL.

Do not add a scheduler control port unless the audit proves Decision cannot own
restart reconciliation. If needed, use only the existing shared scheduler.

## 16. Lifecycle and composition

Production order:

```text
topology → Game Knowledge → progression → combat → background
→ acquisition catalog/store/service → acquisition Decision → Decision engine
```

Shutdown closes acquisition admission, drains or persists active external/combat/
background claims, then stops acquisition before dependencies. Disabled Phantom
World reads no acquisition XML, DB component or candidate.

Expose only fixed aggregate metrics: state/hash, planned/active/completed/blocked,
switches, claims, uncertain recoveries, recipe nodes and maximum bounds. No IDs in
metric labels.

## 17. Exact scope

Allowed existing production:

```text
java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java
java/org/l2jmobius/gameserver/phantoms/background/**
java/org/l2jmobius/gameserver/phantoms/combat/PhantomCombatService.java
java/org/l2jmobius/gameserver/phantoms/combat/PhantomCombatBackend.java
java/org/l2jmobius/gameserver/phantoms/combat/PhantomCombatActorLease.java
java/org/l2jmobius/gameserver/phantoms/combat/L2jCombatBackend.java
java/org/l2jmobius/gameserver/phantoms/progression/PhantomProgressionService.java
java/org/l2jmobius/gameserver/phantoms/decision/PhantomGoalStateStore.java
```

Only targeted changes are allowed. Do not rewrite ordinary Goal 015 semantics.

Allowed new production/data:

```text
java/org/l2jmobius/gameserver/phantoms/acquisition/**
dist/game/data/phantoms/acquisition/high-five-acquisition-v1.xml
```

Allowed targeted progression data only for missing exact spoil/sweep/craft rules:

```text
dist/game/data/phantoms/progression/high-five-capabilities-v1.xml
```

Allowed tests/build/tools/docs:

```text
build.xml
PhantomAcquisition*.java
targeted background/combat/knowledge/progression/System test adaptations
PhantomTestLauncher.java
tools/phantoms/verify-task-020c2.ps1
tools/phantoms/verify-task-021c1.ps1
master plan/roadmap status only
Goal 020 final review
Goal 021 Checkpoint 1 architecture/report/review/task docs
```

Forbidden: Player.java, Party.java, skill handlers, quest scripts, schema,
direct inventory/EXP/SP/drop/spoil mutation from acquisition package, craft/trade/
private store/enchant/manor/quest execution, new workers, other chronicles,
Checkpoint 2 and Goal 022–027.

## 18. Mandatory focused modes

```text
acquisition-catalog-codec
acquisition-source-planner
acquisition-recipe-planning
acquisition-active-spoil
acquisition-background-parity
acquisition-atomic-restart
acquisition-source-switching
acquisition-lifecycle-performance
```

## 19. Mandatory tests

### Catalog/model/codec

Strict XML/hash/order/XXE controls; exact bounds; worst-case payload <=4096; all
statuses/phases/methods roundtrip; duplicate/truncated/trailing/unknown fail
closed; no silent truncation.

### Source planner

Real production Game Knowledge/topology/progression death-drop and spoil facts;
normal instance-0 topology sources; exact source kind; deterministic ranking and
ambiguity; stale hashes; bounded pages; no full scans or corpus duplication.

### Recipe planning

Direct and multi-level recipes; alternatives; inventory subtraction; ceiling
batches; shared ingredients; cycles; depth/node/deficit overflow; missing recipe/
craft prerequisite; manor/quest deferred; zero item or Goal022 mutation.

### Active death drop and spoil

Materialized headless Players and current canonical skill/Monster behavior:
known skill, exact target, spoil observation, existing Combat kill, corpse/sweep,
inventory delta, negative skill/target/instance/distance/ownership controls,
crash at both dispatch phases and no blind repeat.

### Background parity

Same source facts/capability evidence as active; deterministic death-drop and
spoil; missing capability/failure yields zero spoil item; incidental drops remain
separate; capacity/death controls; ordinary Goal015 regression.

### Atomicity/restart

Inject failure before transaction, after item, after background state, after Goal,
after acquisition state, before commit and after commit before publication.
Prove all-or-none, exact replay, baseline preservation, currentAmount derivation,
stale identity/hash rejection and repeated active/background transition
conservation.

### Switching/lifecycle/performance

Typed thresholds/cooldowns, no switch under claim, deterministic next source,
partial progress, authority drift, exhausted alternatives, 100k source plans,
10k recipe DAGs, 10k Decision advances, no scans/workers and zero claims after
shutdown.

## 20. Verification discipline

Development:

1. compile exact affected production/tests;
2. run eight focused modes;
3. run exact affected Goal015 background, combat ownership, progression,
   Game Knowledge, Decision persistence, materialization and shutdown regressions;
4. verifier 020c2 and working verifier 021c1;
5. one final `phantom-acquisition-checkpoint1-test` aggregate.

Do not run broad historical affected aggregates during development.

After green gates freeze production/data/test/build/verifier files:

```text
one final full ant verify
one standalone ant jar
update report terminal section only
ordinary commit/push
two post-commit byte-identical verifier 021c1 runs
```

A second full verify is allowed only after a real relevant source/test/build/
verifier fix. One unrelated preflight-green flake receives one exact targeted
retry without broad/full rerun. Third full verify is forbidden.

Verifier 021c1 must pin accepted Goal 020, verify graph/subject/scope, enforce
forbidden paths, Game Knowledge-only source authority, no direct inventory grant,
payload/source/recipe bounds, active Combat ownership, background atomicity,
deferred manor/quest/craft boundaries, disabled mode, lifecycle, UTF-8, datapack
and JAR classes, and remain descendant-compatible.

Create:

```text
docs/phantoms/architecture/ACQUISITION_CHAIN_CONTRACT.md
docs/phantoms/reports/021-checkpoint-1-acquisition-spoil.md
docs/phantoms/reviews/021-checkpoint-1-independent-review.md
```

Print the success token only after every mandatory gate. Otherwise commit/push
an honest bounded result without starting Checkpoint 2 or Goal 022.
