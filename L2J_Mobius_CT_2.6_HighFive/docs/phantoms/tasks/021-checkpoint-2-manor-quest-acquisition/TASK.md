# Goal 021 — Checkpoint 2: canonical manor and curated quest acquisition chains

## 1. Git and accepted baseline

```text
branch: feature/phantom-world
required parent: 0045f60417f4605f46e3058b9a694278283b1456
test DB only: l2jmobiush5_phantom_test
production DB forbidden: l2jmobiush5
deterministic Goal seed: 21002102
commit subject: feat(phantoms): add manor and quest acquisition chains
success token: GOAL_021_CHECKPOINT_2_MANOR_QUEST_ACQUISITION_IMPLEMENTED_PENDING_INDEPENDENT_REVIEW
```

This is the second and final checkpoint planned before Goal 021 implementation:

```text
Checkpoint 1:
    acquisition kernel
    death-drop and spoil/sweep chains
    bounded recipe preparation

Checkpoint 2:
    manor sow/kill/harvest chains
    curated already-started quest collection
    active/background parity and transition safety
```

It is not Goal 021A/021B and not a corrective suffix.

Create exactly one ordinary child of `0045f60417f4605f46e3058b9a694278283b1456` and push to
`origin/feature/phantom-world`. Do not amend, rebase, squash, merge, reset, force push or
force-with-lease. Publish one honest `SUCCESS`, `PARTIAL` or `BLOCKED` commit.

Record:

```text
Goal 021 Checkpoint 1: ACCEPT
Goal 021 Checkpoint 2: IMPLEMENTED_PENDING_INDEPENDENT_REVIEW
Goal 021 overall: IMPLEMENTED_PENDING_INDEPENDENT_REVIEW
Goal 022–027: NOT_STARTED
```

Create:

```text
docs/phantoms/reviews/021-checkpoint-1-final-review.md
```

Pin `0045f60417f4605f46e3058b9a694278283b1456` and verdict `ACCEPT`.

## 2. Product result

Complete Goal 021 with two additional causal methods:

```text
MANOR_CROP
QUEST_COLLECTION
```

The complete chain is:

```text
ACTIVE acquire.item Goal
→ authoritative method/source eligibility
→ exact source binding in acquisition.state
→ active canonical execution or background projection
→ canonical inventory observation/atomic transaction
→ baseline-derived acquisition progress
→ bounded retry/switch/recovery
```

Checkpoint 2 must not create resources merely because a static `ManorFact` or a
quest name exists.

After implementation:

```text
DEATH_DROP          EXECUTABLE
SPOIL_SWEEP         EXECUTABLE
RECIPE_PREPARATION  PLANNING_ONLY
MANOR_CROP          EXECUTABLE
QUEST_COLLECTION    EXECUTABLE
```

Still out of scope:

```text
craft execution
recipe learning
ingredient consumption/reservation
castle crop procurement or reward exchange
automatic seed purchase
player trade/private stores
quest start
quest turn-in/completion dialogue
generic quest interpretation
quest rewards other than an audited kill-collection item
manor/quest combat doctrine
Goal 022+
```

## 3. Execution-efficiency and audit contract

Do not reread old task packages, all reports, all quests, all item handlers,
`Player.java`, `Party.java`, every manor class or unrelated subsystems.

Initial READ_SET:

1. this package;
2. accepted Goal 021 Checkpoint 1 final tree, report/review and verifier 021c1;
3. acquisition catalog/state/codec/store/planner/service/decision;
4. Game Knowledge `ManorFact`, static Seeds parser and manor queries;
5. `CastleManorManager`, manor `Seed`, current manor mode/config getters only;
6. exact `handlers.items.Seed`, `handlers.items.Harvester`,
   `handlers.skill.effects.Sow`, `handlers.skill.effects.Harvesting`;
7. exact `Attackable` manor fields/getters/setters/takeHarvest behavior;
8. Combat actor/external-action seams and acquisition target snapshots;
9. Background model/service/transaction/operation identity;
10. `Quest`, `QuestState`, `ScriptManager` and exact quest DB helper SQL;
11. `OnAttackableKill` delivery/current delayed callback path;
12. PhantomSystem composition/shutdown.

Quest-content audit:

- perform one bounded filename/content search under
  `dist/game/data/scripts/quests`;
- inspect at most twelve exact quest script files;
- report every inspected path and why it was accepted or rejected;
- select at least two and at most four distinct scripts;
- publish at most eight exact collection rules;
- no broad quest scan after this audit.

At most sixteen additional exact files or symbols may be opened, each listed in
the report with one sentence.

Hard limits:

```text
new production/data files <= 18
changed production/data/config files <= 34
changed total files <= 58
no SQL schema migration
no Player.java, Party.java or Attackable.java change
no existing item/skill/quest handler or quest-script change
no CastleManorManager mutation/rewrite
no new worker/thread/executor/Future/scheduled task
no Goal 022+ work
report <= 260 lines
soft Goal usage target <= 1,250,000 tokens
maximum full ant verify invocations: 2
```

If two safe quest rules cannot be proven, or manor requires modifying canonical
handlers, commit/push a bounded `BLOCKED` result. Do not invent a third Goal 021
checkpoint or bypass the canonical mechanics.

## 4. Checkpoint 1 acceptance and historical verifier

Before Checkpoint 2 runtime work:

1. create the Checkpoint 1 final review;
2. update roadmap/master-plan status to:
   `Goal 021 Checkpoint 1: ACCEPT`;
3. make verifier 021c1 historical and descendant-compatible:
   - pin `0045f60417f4605f46e3058b9a694278283b1456` as accepted Checkpoint 1;
   - verify its complete foundation/safety/closure/micro ancestry and subjects;
   - inspect Checkpoint 1 blobs at `0045f60417f4605f46e3058b9a694278283b1456`;
   - require `0045f60417f4605f46e3058b9a694278283b1456` as an ancestor of current HEAD;
   - never include Checkpoint 2 paths in Checkpoint 1 scope;
4. run verifier 021c1 once in PowerShell 5.1 and 7.x.

Do not change accepted Checkpoint 1 runtime semantics except exact extensions
required by the method-union/state-version contract below.

## 5. Strict policy and curated quest catalog

### 5.1 Acquisition policy

Update:

```text
dist/game/data/phantoms/acquisition/high-five-acquisition-v1.xml
```

Only method status/retry/formula limits needed by Checkpoint 2 may change.
Existing Checkpoint 1 weights and limits remain compatible.

Add hard bounds:

```text
manor attempts per target <= 3
harvest attempts per corpse <= 3
quest callback wait <= 6000 ms
quest rules <= 8
quest scripts <= 4
quest target NPCs per rule <= 8
quest expected vars per rule <= 4
quest item IDs per read <= 16
method bindings <= 1
source candidates remain <= 8
operations per Decision step remain <= 8
component payload remains <=4096
```

### 5.2 Curated quest data

Create:

```text
dist/game/data/phantoms/acquisition/high-five-quest-collection-v1.xml
```

The loader must be strict, ordered, XXE-safe and content-addressed.

Each rule contains:

```text
rule ID
quest numeric ID
exact quest class/name
relative source script path
full source-file SHA-256
required quest state = STARTED
allowed exact cond values
target NPC IDs
quest item ID
grant shape
chance/rate kind
minimum/maximum count
item cap
summon-kill policy
party-distribution policy
registered quest-item evidence
source references
```

Supported grant shapes:

```text
GUARANTEED_ITEM
SINGLE_BOUNDED_ROLL
```

A selected rule must satisfy all of the following:

- exact quest script is present and source hash matches;
- `onKill` is registered only for the declared target set or the declared
  targets are an exact subset with identical logic;
- the player must already have an exact STARTED `QuestState`;
- eligibility uses only declared cond and at most four read-only variables;
- kill-side gameplay effect is only a bounded quest-item grant and optional
  sound/message;
- no quest-state/cond/variable mutation on the supported kill branch;
- no quest completion, turn-in, timer, global variable, instance, zone,
  transform, class, skill, random branch chain or unrelated item mutation;
- no party-wide or command-channel grant;
- summon-kill behavior is explicit;
- item cap and chance/count formula are statically auditable;
- the quest item is a real item and is registered by the quest;
- the rule can be tested against the actual loaded quest script without editing
  it.

Unsupported branches remain unsupported even if they are in the same script.

Startup fails closed on script-hash drift, unknown rule fields, missing
quest/item/NPC refs, overlapping rule identity or unverifiable grant semantics.

No runtime reflection over arbitrary quest scripts and no generic Java-source
interpreter.

## 6. Acquisition state version 3

Extend `acquisition.state` to schema version 3.

Readers must accept versions 1, 2 and 3. Writer emits only version 3.

Add one optional method binding.

### Manor binding

```text
castle ID
seed item ID
crop item ID
mature/reward factual IDs
seed level
alternative flag
raw limits
exact seed stack object ID when active
exact harvester object ID when active
seed count before dispatch
crop count before dispatch
manor authority hash
```

### Quest binding

```text
rule ID/hash
quest ID/name
script hash
expected state/cond
quest item ID/cap
target NPC ID
item count before kill
callback deadline
quest authority hash
```

Exactly one binding is allowed and it must match the selected method. Existing
death/spoil/recipe states have no binding.

Add phases:

```text
SOW_PREPARED
SOW_DISPATCHING
SOW_OBSERVED
HARVEST_PREPARED
HARVEST_DISPATCHING
QUEST_COMBAT_PREPARED
QUEST_COMBAT_SUBMITTED
QUEST_COMBAT_TERMINAL
QUEST_CALLBACK_WAIT
```

Existing Combat phases may be reused only when the codec/state invariants still
distinguish the owning method unambiguously.

Persist bounded attempt/recovery counters for sow, harvest and quest callback.
No silent truncation.

Codec requirements:

- exact canonical order and uniqueness;
- invalid binding/phase combinations fail closed;
- source/binding item and NPC identities agree;
- legacy versions recover conservatively with no fabricated binding;
- unknown version/method/rule, partial binding, trailing/truncated bytes fail;
- declared worst-case version 3 <=4096.

## 7. Manor authority and source planning

Create a read-only manor authority adapter. It may read:

```text
GeneralConfig.ALLOW_MANOR
current CastleManorManager mode
exact current Seed objects
static ManorFact generation
RatesConfig.RATE_DROP_MANOR
MapRegionData castle mapping
exact NPC template level/canBeSown/strong-type skills
exact seed and harvester item handler/skill identity
```

It must not modify CastleManorManager, castle production/procure collections,
treasury, clan warehouse or manor DB rows.

The manor authority publishes one deterministic hash over all facts that affect
sow/harvest execution or background projection.

### Candidate generation

For target item `X`, only a `ManorFact` with:

```text
cropItemId == X
```

is a direct `MANOR_CROP` source.

`matureItemId`, `reward1ItemId` and `reward2ItemId` are not direct player
harvest products.

For each exact fact:

1. current manor is enabled and the runtime Seed matches the static fact;
2. enumerate bounded normal, attackable, targetable, non-raid, non-chest
   monsters with `canBeSown=true`;
3. target level must produce a non-impossible current sow formula;
4. require an instance-0 topology-mapped spawn/anchor;
5. the anchor's current castle area must equal the seed castle;
6. seed and crop item refs exist;
7. resolve the current canonical Seed item handler/skill;
8. resolve the current canonical Harvester item/handler/skill;
9. active/background inventory evidence contains at least one seed and one
   harvester;
10. source ID hashes exact manor fact, target NPC, topology/castle, rate/formula,
    handler identities and authority hashes.

Missing seed or harvester is typed:

```text
manor.seed_missing
manor.harvester_missing
```

Checkpoint 2 does not purchase them automatically.

Static seed limits do not create inventory and current castle production does not
prove the Phantom owns a seed.

## 8. Active manor execution

Do not call the manor item/skill handlers through packets or a fake client.

Add a narrow method to the existing ACQUISITION external actor lease or one
equivalent port:

```text
manorInventory(source)
manorTargetSnapshot(objectId)
useExactSeed(seedObjectId, targetObjectId)
useExactHarvester(harvesterObjectId, targetObjectId)
```

The production adapter must:

- acquire current exact Player/action ownership;
- resolve exact owned item object IDs;
- set/revalidate the exact target;
- invoke the currently registered canonical `IItemHandler`;
- let existing `Seed`/`Sow` and `Harvester`/`Harvesting` logic own seed
  consumption, target state, chance and crop grant;
- return typed `ISSUED`, `ALREADY_OWNED`, `UNAVAILABLE` or `REJECTED`;
- expose immutable snapshots only.

It must not call:

```text
Attackable.setSeeded
Attackable.takeHarvest
Player.destroyItemByItemId
Inventory.addItem
Quest.onKill
```

from Phantom acquisition code.

### Active chain

```text
exact target and owned seed/harvester
→ persist SOW_DISPATCHING
→ canonical Seed item use / Sow cast
→ observe exact seed count and target seed/seeder state
→ existing Combat kill on the same target
→ observe exact seeded corpse and seeder
→ persist HARVEST_DISPATCHING
→ canonical Harvester item use / Harvesting cast
→ observe exact crop inventory delta
→ verify baseline-derived progress
```

Requirements:

- target must remain exact object/NPC/instance/castle;
- exact seeder is the Phantom Player;
- a failed sow consumes exactly what canonical Sow consumed;
- no Combat starts until sow success is observed;
- harvest is attempted only on an exact dead seeded corpse owned by the seeder;
- ordinary death drops remain independently attributable;
- crop progress is never inferred from sow success alone;
- active manor does not sell crops or produce mature/reward items.

### Crash/restart

Persist dispatch before each item-handler call.

Recovered SOW_DISPATCHING:

- exact active cast → wait;
- exact seeded target/seeder → SOW_OBSERVED;
- seed count decreased without seeded proof → bounded uncertain/failure;
- no cast/effect/count change → bounded retry;
- never consume another seed blindly after uncertain evidence.

Recovered HARVEST_DISPATCHING:

- crop count increased → VERIFYING;
- exact active cast → wait;
- no delta and corpse still safely harvestable → bounded retry;
- target gone, object reused or proof insufficient → bounded uncertain/failure;
- no duplicate crop can be credited.

All external/Combat claims are released on terminal/block/switch/shutdown.

## 9. Background manor parity

Extend the existing background model and transaction. Do not create a parallel
simulator or mutate live CastleManorManager state.

Add:

```text
BatchMode.ACQUISITION_MANOR_CROP
ActionKind.ACQUISITION_MANOR_CROP
```

The selected background request pins:

```text
Goal ID/revision
acquisition row version
source ID
manor binding
seed/crop item IDs
exact expected seed count
manor/static/topology/background hashes
```

### Formula parity

Audit and reproduce the current shipped semantics exactly:

- `Seed.onItemUse` target/castle restrictions;
- `Sow` seed consumption and current success formula;
- current `Rnd.get(99)` boundary semantics;
- current alternative/non-alternative base chance;
- current level penalties, including current implementation behavior;
- `Attackable.setSeeded(Player)` crop count, strong-type multipliers and
  `RATE_DROP_MANOR`;
- `Harvesting` authorization and current level-based success formula;
- failed harvest does not consume the pending harvest payload;
- only successful canonical-equivalent harvest grants the crop.

Do not silently “fix” a canonical formula inside background projection.

One bounded background operation may model several sow retries and harvest
retries only within policy limits and available seeds. Every consumed seed,
elapsed time, kill, death drop and crop result is separately attributable.

### Atomic transaction

One existing Background transaction commits:

```text
seed item decrement
crop item increment when proven
ordinary death-drop item deltas
character EXP/SP/vitals/position
background.state and receipt
goal.runtime
acquisition.state and receipt
```

All expected row versions and authority hashes are mandatory.

Any seed/crop count, class, source, rate, authority or acquisition conflict rolls
back everything.

Exact replay is idempotent. Source switch or changed acquisition generation
cannot replay an old manor operation.

## 10. Curated quest source planning

`QUEST_COLLECTION` candidates come only from the strict curated catalog.

For one exact target item:

1. find rules whose quest item ID equals the target;
2. verify current script hash;
3. read exact current quest state/cond/allowed variables;
4. require STARTED and one allowed cond;
5. require current item count below the rule cap;
6. require the exact target NPC to be a normal attackable/targetable monster;
7. require an instance-0 topology-mapped spawn/anchor;
8. bind exact rule, quest, target NPC, item cap and authority hashes;
9. score using the existing generic acquisition scoring factors;
10. equal top candidates obey the accepted ambiguity contract.

No source exists when the quest has not already been started.

Typed reasons:

```text
quest.not_started
quest.cond_ineligible
quest.item_cap
quest.script_stale
quest.rule_unsupported
quest.target_unavailable
quest.callback_timeout
```

The acquisition service never starts the quest, changes dialogue state or
selects a turn-in NPC.

## 11. Active quest collection

Use the existing Combat service for the exact rule target.

Flow:

```text
revalidate QuestState/rule/source
→ persist QUEST_COMBAT_PREPARED
→ submit exact acquisition-owned Combat session
→ observe terminal kill
→ persist QUEST_CALLBACK_WAIT with bounded deadline
→ allow existing delayed OnAttackableKill → loaded quest script onKill
→ observe exact QuestState and quest-item count
→ verify acquisition progress
```

Contracts:

- production acquisition code never invokes `Quest.onKill` manually;
- no direct quest item grant;
- callback wait accounts for the current delayed kill-event path;
- item growth before deadline may complete immediately;
- unchanged item count before deadline remains pending;
- after deadline an unchanged count records one bounded source failure;
- changed state/cond/script hash or item cap is typed and fail closed;
- no item from another quest/rule counts as progress;
- summon-kill behavior follows the rule exactly;
- callback delivery remains owned by the server quest/event system.

Active tests must use the actual loaded selected quest script and its real
OnAttackableKill path.

## 12. Background quest projection

Background quest execution is allowed only for curated rules whose supported
kill branch has no quest-state or quest-variable mutation.

Add:

```text
BatchMode.ACQUISITION_QUEST_COLLECTION
ActionKind.ACQUISITION_QUEST_COLLECTION
```

The transaction must lock/read exactly:

```text
profile→character link
active class/index
exact character_quests rows for quest name
state row
cond row
at most four declared read-only variables
quest item rows
background.state
goal.runtime
acquisition.state
```

Validate exact expected quest state/cond/variables and rule/script hashes under
the same transaction locks.

The background model uses the rule's exact current formula and existing
background RNG state. It may add only the declared quest item, clamped to the
exact cap.

One transaction commits:

```text
quest item delta
ordinary combat/drop/EXP/SP/vitals consequences
background.state
goal.runtime
acquisition.state
receipts
```

Quest state/cond/vars remain byte-identical.

No script invocation, Java-source interpretation or arbitrary quest SQL callback
is allowed in background mode.

If a selected active quest script changes its supported kill branch after a
source-hash update, the old rule is stale and cannot execute.

## 13. Active/background transitions

For both methods:

- progress remains `current target-item count - immutable baseline`;
- materialization cannot repeat a committed background seed consumption, crop or
  quest item;
- dematerialization cannot aggregate over an active exact target/callback;
- method binding, source ID and operation receipt survive restart;
- transition requires zero active external/Combat/navigation claim;
- active perceptible target history is never rewritten by background;
- an uncertain active sow/harvest/quest callback blocks background execution
  until reconciled;
- partial progress and source failure history survive source switching.

## 14. Decision integration and lifecycle

Extend the accepted `PhantomAcquisitionDecision`; do not add another decision
engine.

Actions remain within the existing acquisition action family.

One Decision step performs at most one persisted transition.

Production order:

```text
Game Knowledge/topology/progression
→ Combat/background
→ quest collection catalog/manor authority
→ acquisition service
→ acquisition decision
→ existing Decision engine
```

Shutdown:

```text
acquisition admission closes
→ quest callback/manor external/Combat/background claims drain or persist
   conservative uncertainty
→ acquisition stops
→ dependencies stop in existing order
```

Disabled Phantom World:

- does not load the quest collection XML;
- does not query quest/manor acquisition state;
- registers no additional handler/observer/worker;
- ordinary manor and quest behavior remains unchanged.

Expose bounded metrics:

```text
manor planned/sow/harvest/completed/blocked
quest planned/kills/callback waits/completed/blocked
source/rule stale
uncertain recoveries
current claims
```

No profile, quest, item, NPC or source IDs in metric labels.

## 15. Exact scope

Allowed existing production:

```text
java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java
java/org/l2jmobius/gameserver/phantoms/acquisition/**
java/org/l2jmobius/gameserver/phantoms/background/**
java/org/l2jmobius/gameserver/phantoms/combat/PhantomCombatActorLease.java
java/org/l2jmobius/gameserver/phantoms/combat/PhantomCombatBackend.java
java/org/l2jmobius/gameserver/phantoms/combat/L2jCombatBackend.java
java/org/l2jmobius/gameserver/phantoms/combat/PhantomCombatService.java
java/org/l2jmobius/gameserver/phantoms/knowledge/PhantomGameKnowledgeModel.java
java/org/l2jmobius/gameserver/phantoms/knowledge/PhantomGameKnowledgeQuery.java
```

Knowledge changes are allowed only for a bounded exact manor target query if the
accepted indexed API is insufficient. Do not move quest script interpretation
into Game Knowledge.

Allowed new production/data:

```text
java/org/l2jmobius/gameserver/phantoms/acquisition/manor/**
java/org/l2jmobius/gameserver/phantoms/acquisition/quest/**
or equivalent bounded files under acquisition/**
dist/game/data/phantoms/acquisition/high-five-quest-collection-v1.xml
```

Allowed tests/build/tools/docs:

```text
build.xml
PhantomAcquisitionManor*.java
PhantomAcquisitionQuest*.java
targeted adaptations to acquisition/background/combat/System tests
PhantomTestLauncher.java
tools/phantoms/verify-task-021c1.ps1
tools/phantoms/verify-task-021c2.ps1
master plan/roadmap status only
Checkpoint 1 final review
Checkpoint 2 architecture/report/review/task docs
```

Forbidden:

- `Player.java`, `Party.java`, `Attackable.java`;
- `CastleManorManager.java`;
- existing item handlers, skill effects or quest scripts;
- schema/migrations;
- packet handlers or fake GameClient;
- direct Phantom calls to `setSeeded`, `takeHarvest`, `addItem`,
  `destroyItemByItemId`, `Quest.onKill`, `QuestState.set`, `QuestState.unset`;
- castle procurement/reward exchange;
- quest start/turn-in/completion;
- generic quest interpreter/reflection;
- craft/trade/private-store/enchant execution;
- new worker/thread/executor/Future/task;
- other chronicles/geodata;
- Goal 022–027.

## 16. Mandatory focused modes

```text
acquisition-manor-catalog-source
acquisition-manor-active
acquisition-manor-background
acquisition-manor-restart-transition
acquisition-quest-catalog-source
acquisition-quest-active
acquisition-quest-background
acquisition-checkpoint2-lifecycle-performance
```

## 17. Mandatory evidence

### State/catalog

- strict quest XML, script hashes and negative controls;
- two to four real supported quest scripts, at most eight rules;
- rejected audited scripts and exact reasons;
- acquisition schema 1/2/3 compatibility;
- maximum method bindings/state payload <=4096;
- no silent truncation.

### Manor source

- target item matches crop, never mature/reward;
- current runtime Seed equals static ManorFact;
- enabled/disabled manor;
- castle-area mapping;
- canBeSown normal target;
- seed/harvester ownership;
- missing requirement and ambiguity;
- source/authority hash drift.

### Active manor

Using a real headless materialized Phantom, real item handlers/effects and real
Monster:

- exact seed object and target;
- castle mismatch;
- raid/chest/dead/already-seeded/wrong-instance controls;
- seed consumption on canonical sow attempt;
- successful and failed sow;
- exact seeder ownership;
- existing Combat kill;
- canonical harvest;
- crop inventory delta;
- no mature/reward item;
- sow/harvest DISPATCHING crash recovery;
- no blind duplicate seed consumption/crop credit;
- claims drain.

### Background manor

- exact active/background formula branch parity;
- alternative and normal seed;
- level penalties;
- strong-type multiplier;
- manor rate;
- multiple failed sow attempts consume exact seeds;
- failed harvest retains bounded retry semantics;
- no seed → no crop;
- ordinary drops remain separate;
- seed/crop/Goal/acquisition/background atomic rollback at every write;
- replay/source-switch conservation;
- inventory capacity/weight;
- active/background transitions.

### Quest catalog

For every accepted rule:

- real script path and SHA;
- real quest/item/NPC refs;
- exact STARTED/cond requirements;
- exact registered kill targets;
- exact grant helper/formula/cap;
- no hidden state/var/timer/party/global side effect;
- script drift fails closed.

### Active quest

- real loaded quest script;
- actual OnAttackableKill path;
- exact delayed callback wait;
- item grant and no-grant paths;
- cap;
- wrong cond/state/target/script;
- summon policy;
- no manual onKill and no direct item grant;
- no quest start/turn-in/state mutation by acquisition.

### Background quest

- same exact rule formula and target;
- exact character_quests state/cond/var locks;
- quest rows byte-identical after operation;
- deterministic item/no-item result;
- exact cap;
- ordinary combat/drop consequences;
- full atomic rollback;
- exact replay;
- rule/script/state drift;
- active/background count conservation.

### Lifecycle/performance

- 100,000 bounded manor source plans;
- 100,000 bounded quest source plans;
- 10,000 manor background operations;
- 10,000 quest background operations;
- no all-quest/script scan at runtime;
- no new worker/thread/executor/Future/task;
- shutdown at every item-handler/Combat/callback/DB boundary;
- zero claims and zero pending callback ownership after stop.

## 18. Verification discipline

Development:

1. exact compile;
2. Checkpoint 1 verifier and regressions;
3. manor catalog/source;
4. active manor;
5. background manor;
6. manor restart/transitions;
7. quest catalog/source audit;
8. active quest real-script path;
9. background quest projection;
10. quest restart/transitions;
11. lifecycle/performance;
12. exact affected acquisition/background/combat/knowledge/Decision/System
    regressions;
13. working verifier 021c2;
14. one final `phantom-acquisition-checkpoint2-test` aggregate.

Do not use a global override of `phantom.test.seed`. Goal 021 Checkpoint 2 routes
use only:

```text
phantom.goal021c2.seed=21002102
```

After focused/static gates are green, freeze production/data/test/build/verifier:

```text
one final plain ant verify
one standalone ant jar
update report terminal section only
ordinary commit/push
two post-commit byte-identical verifier 021c2 runs
```

A second aggregate or full verify is allowed only after a real relevant
production/test/build/verifier fix. Third aggregate/full verify is forbidden.

Verifier 021c2 must:

- pin accepted Checkpoint 1 `0045f60417f4605f46e3058b9a694278283b1456`;
- verify exact parent/subject/scope;
- enforce no canonical handler/Quest/Attackable/Player/Party/schema changes;
- verify schema 1/2/3 and method-binding bounds;
- verify manor source/action/formula ownership;
- verify no direct manor mutation bypass;
- verify curated source-hashed quest rules and no generic interpreter;
- verify active quest uses real OnAttackableKill rather than manual invocation;
- verify background quest exact-row locking and atomic item/background/Goal/
  acquisition mutation;
- verify no quest start/turn-in/state mutation;
- verify aggregate dependency wiring, disabled mode, UTF-8, datapack and JAR
  classes;
- be descendant-compatible after acceptance.

Create:

```text
docs/phantoms/architecture/MANOR_QUEST_ACQUISITION_CONTRACT.md
docs/phantoms/reports/021-checkpoint-2-manor-quest-acquisition.md
docs/phantoms/reviews/021-checkpoint-2-independent-review.md
```

Print `GOAL_021_CHECKPOINT_2_MANOR_QUEST_ACQUISITION_IMPLEMENTED_PENDING_INDEPENDENT_REVIEW` only after every mandatory gate. Otherwise commit/push an honest
bounded result without starting Goal 022.
