# GOAL 013 — Class progression, skills, equipment and capability catalog

## 1. Identifier

- **Goal ID:** `013-class-progression-capability-catalog`
- **Roadmap stage:** III — Solo gameplay, progression and causal background
- **Branch:** `feature/phantom-world`
- **Accepted baseline candidate:** `8dba87e9c1d5828376b80c1ea16c4578726d4947`
- **Parent chain:**
  - Goal 012: `8143cb7f89d348854fc469a0955b22405f23e9b6`
  - reviewed unrelated `.gitignore`: `74dd973c167adf0a74e7af78ed7944e2518c16cb`
  - Goal 012A: `8dba87e9c1d5828376b80c1ea16c4578726d4947`
- **Git root:** `C:\Users\endim\L2J_Mobius\`
- **Only module:** `C:\Users\endim\L2J_Mobius\L2J_Mobius_CT_2.6_HighFive\`
- **Test DB:** `l2jmobiush5_phantom_test`
- **Production DB:** `l2jmobiush5` — never use during Codex execution
- **Seed:** `20260725001`
- **Codex model:** Sol
- **Effort:** Very High

## 2. Accepted gates

```text
Stage I: COMPLETE
Stage II: COMPLETE

Goal 010 / 010A / 010B / 010C: ACCEPT
Goal 011 / 011A: ACCEPT
Goal 012: ACCEPT after Goal 012A
Goal 012A: ACCEPT
Revert: NOT_REQUIRED

Accepted baseline:
8dba87e9c1d5828376b80c1ea16c4578726d4947

Goal 013: ALLOWED
Goal 014: NOT_STARTED
Goal 015: NOT_STARTED
```

Goal 012A accepted evidence:

```text
Commit: 8dba87e9c1d5828376b80c1ea16c4578726d4947
Parent: 74dd973c167adf0a74e7af78ed7944e2518c16cb
Combat core: 47/47 ×3
Ownership: 17/17 ×3
Action ownership: 33/33 ×3
Real integration: 19/19 ×2
Performance: 1/1 ×2
ant verify: PASS ×2 post-commit
ant jar: PASS ×2 post-commit
Verifier: 102/102 ×2, byte-identical
Verifier SHA-256:
7F5EFA1D3D506E73A5741010833DF82685A0530BBF24D0E7C9326F8514E81A16
Independent verdict: ACCEPT
```

## 3. Goal

Create a bounded authoritative progression/capability layer that answers:

```text
What class/stage is this actor?
What class transitions are structurally possible?
What skills belong to this class and are actually learned?
What prevents a skill from being learned or used?
What equipment does the actor own and what can it canonically equip?
Which capabilities are intrinsic, learned and ready now?
What servitor/pet/cubic actors are available to this class?
What is the exact current EXP/SP/level/subclass/noblesse state?
```

The Goal must provide:

1. immutable loader-parity class/progression catalog for every High Five
   `PlayerClass`;
2. complete class skill-tree and acquisition facts;
3. exact actor progression snapshots from canonical `Player`;
4. generic capability evaluation with evidence, target scope and readiness;
5. safe explicit class-skill learning at a real valid trainer;
6. safe explicit equip of an already-owned item through canonical server API;
7. profession-target planning and canonical profession-change reconciliation;
8. subclass/Noblesse/certification facts and eligibility observations;
9. servitor/pet/cubic/siege/quest-summon taxonomy and current-server facts;
10. normalized, source-audited research outputs derived from DR-01…DR-05;
11. deterministic hashes, bounded indexed queries, fixed metrics and lifecycle;
12. zero automatic progression/equipment operations at startup.

Production inertness:

```text
automatic class changes       = 0
automatic skill learning      = 0
automatic equipment changes   = 0
automatic subclass changes    = 0
automatic summon creation     = 0
production progression plans  = 0
progression workers/tasks      = 0
```

Only explicit service calls or registered decision step handlers may perform a
supported operation.

## 4. Critical boundary: no fabricated profession quests

There is no permission to bypass High Five class-transfer quests.

Production progression code must not:

- call `Player.setPlayerClass` directly;
- complete or mutate class-transfer quest state;
- fabricate quest items/marks;
- invoke packet or NPC bypass text;
- assume level alone authorizes a profession;
- use the custom ClassMaster unless its exact canonical server action is enabled,
  reachable and independently validated through an existing non-packet facade.

Required behavior:

```text
structurally valid next class
+ level reached
+ no canonical generic transition authorization
→ CANONICAL_QUEST_REQUIRED
```

Goal 013 models profession targets and observes/reconciles actual canonical
profession changes. If a safe existing shared server facade is found during the
mandatory audit, it may be reused without changing server core and only with its
full quest/item/NPC eligibility. If no such facade exists, direct profession
mutation remains unsupported by design and does not block the rest of Goal 013.

This is not permission to reduce Goal 013 to documentation: class catalog,
runtime capability, skill learning, equipment and canonical EXP/SP/level
observation must be implemented and proven.

## 5. Mandatory reading

Read fully:

1. `Agents.md`, master plan, roadmap, workflow/package/report standards;
2. Goal 011/011A and Goal 012/012A packages, reports, reviews and contracts;
3. all files in this Goal package;
4. current server sources:
   - `PlayerClass`, `ClassListData`, `CategoryData`;
   - `SkillTreeData`, `SkillData`, `Skill`, `SkillLearn`,
     `AcquireSkillType`;
   - `RequestAcquireSkill`, `Folk`, `VillageMaster`;
   - `Player`, `PlayerStat`, profession-change events;
   - `SubClassHolder`, subclass add/change/modify paths;
   - `ItemData`, `ItemTemplate`, `Weapon`, `Armor`, `Item`,
     `Inventory`, equipment/use-item path;
   - `PetDataTable`, `PetData`, `PetLevelData`;
   - `Summon`, `Servitor`, `Pet`, `BabyPet`, `Cubic`;
   - summon-related skill effects and actual summon NPC templates;
   - accepted Game Knowledge class capability/query code;
   - materialization `ActionLease`;
   - decision handler registry and plan cancellation;
   - `PhantomSystem` lifecycle;
5. actual class/skill/item/pet/summon datapack files in this chronicle only.

Do not inspect or modify another chronicle.

## 6. Initial Git audit

```bat
git rev-parse --show-toplevel
git status --short --branch
git remote -v
git fetch origin --prune
git rev-parse HEAD
git rev-parse origin/feature/phantom-world
git show --stat --oneline 8dba87e9c1d5828376b80c1ea16c4578726d4947
git diff --name-status 74dd973c167adf0a74e7af78ed7944e2518c16cb..8dba87e9c1d5828376b80c1ea16c4578726d4947
```

Expected:

```text
HEAD == origin/feature/phantom-world == 8dba87e9...
```

The extracted Goal 013 package is expected untracked. Geodata `.l2j` files are
ignored by the reviewed root `.gitignore`. Do not change `.gitignore`.

Return `BLOCKED_BASELINE_DRIFT` for any other unreviewed production/config/schema
drift.

## 7. Close Goal 012A

Create:

```text
docs/phantoms/reviews/012a-combat-action-ownership-truth-review.md
```

Update the Goal 012A report with the immutable handoff from §2.

Verdict:

```text
Goal 012: ACCEPT after Goal 012A
Goal 012A: ACCEPT
Revert: NOT_REQUIRED
Goal 013: ALLOWED
Goal 014: NOT_STARTED
Goal 015: NOT_STARTED
```

Roadmap progress only:

- accepted baseline becomes `8dba87e9...`;
- Goal 012/012A become `ACCEPT`;
- Goal 013 becomes `IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`;
- Goal 014/015 remain `NOT_STARTED`;
- do not rewrite later Goal architecture.

## 8. Source authority model

Use fixed authority values equivalent to:

```text
SERVER_LOADER_FACT
SERVER_CODE_FACT
DATAPACK_FACT
CONFIG_FACT
CURRENT_SERVER_IMPLEMENTATION
CURATED_CAPABILITY
CURATED_RECOMMENDATION
EXTERNAL_RETAIL_CLAIM
DISPUTED
DEFERRED
```

Rules:

- class IDs, parent IDs, skill IDs, item IDs, NPC IDs and enum values are
  identities;
- localized/display names are presentation only;
- `CURRENT_SERVER_IMPLEMENTATION` outranks an external retail claim when
  describing what Phantom World actually executes;
- external claims never drive production logic unless revalidated against current
  code/datapack;
- recommendations never become hard mechanical gates;
- every curated production capability has stable evidence IDs and source paths;
- no inference from class or skill names.

## 9. Production package

Create:

```text
java/org/l2jmobius/gameserver/phantoms/progression/**
```

Responsibility-equivalent types:

```text
PhantomProgressionService
PhantomProgressionPolicy
PhantomProgressionBackend
L2jProgressionBackend
PhantomProgressionCatalogBuilder
PhantomProgressionCatalog
PhantomProgressionQuery
PhantomProgressionModel
PhantomProgressionAuthority
PhantomActorProgressionSnapshot
PhantomCapabilityEvaluator
PhantomCapabilitySnapshot
PhantomSkillLearningService
PhantomEquipmentEvaluator
PhantomProgressionStepHandlers
PhantomProgressionMetrics
```

Small immutable records/enums/helpers and strict datapack parsers are allowed.

Only `L2jProgressionBackend`/an opaque operation lease may retain an
`ActionLease` and `Player`. Public facts/results/snapshots must not expose
`Player`, `Skill`, `Item`, `Summon`, `Pet`, `Npc`, mutable collections or packet
objects.

No global static progression API.

## 10. Immutable catalog model

### 10.1. Class facts

For every `PlayerClass` value:

```text
classId
enumKey
race
enumParentClassId
skillTreeParentClassId
rootClassId
tier
mage
summoner
terminal
nextClassIds
authority
source paths
```

Keep enum parent and XML skill-tree parent distinct.

Mandatory invariants:

- every `PlayerClass` appears exactly once;
- every non-root enum parent exists;
- every skill-tree parent exists;
- no parent cycle;
- terminal definition is based on the current class graph;
- current High Five terminal count is independently reconstructed;
- male and female Soul Hound are separate class IDs;
- Inspector and Judicator are separate stages;
- Judicator is terminal;
- no display name is used as identity or hash input.

### 10.2. Skill learning facts

For every complete class-tree entry:

```text
classId
acquireType
skillId
skillLevel
minimumCharacterLevel
baseSpCost / calculated-cost inputs
required item IDs/counts
prerequisite skill IDs/levels
learned-once/upgradable relationship
authority
source path
```

Preserve exact `SkillLearn` facts. Do not flatten away previous-level or required
item semantics.

Catalog separately includes current:

- CLASS;
- TRANSFER;
- SUBCLASS certification;
- NOBLE;
- COMMON/TRANSFORM entries relevant to player progression.

Only CLASS learning is executable in Goal 013. Other acquire types are queryable
and explicitly `NOT_EXECUTABLE_IN_GOAL_013`.

### 10.3. Skill mechanical facts

For every referenced skill:

```text
skillId/level
active/passive/toggle
physical/magic
targetType
damage/negative/heal/resurrection/buff/debuff/control indicators
item consume ID/count
MP/HP consume
reuse
weapon/equipment condition presence
blockedInOlympiad
PvP-only
suicide/special/transformation flags
authority
source paths
```

Do not claim that a condition is satisfied merely because a skill exists.

Where current public `Skill` APIs do not expose a condition fully:

```text
conditionPresence = DYNAMIC_SERVER_CONDITION
```

and runtime readiness must use the canonical condition/check path.

### 10.4. Equipment facts

For every equippable item:

```text
itemId
bodyPart
weapon/armor/accessory family
weaponType / armorType
crystal grade
default action
stackable
weight
authority
source path
```

Do not duplicate complete combat stat formulas or invent a universal item power.

### 10.5. Summon/pet facts

Distinguish:

```text
SERVITOR
PET
BABY_PET
CUBIC
SIEGE_SUMMON
QUEST_SUMMON
OTHER_CONTROLLED_ACTOR
```

For each discoverable actor relation:

```text
owner class IDs
summon/pet skill ID and level
summon NPC ID
actor kind
lifetime
EXP multiplier
summon item
upkeep item/count/interval
control item
food IDs
soulshots/spiritshots per hit
mountable
inventory/pickup support
heal/recharge/buff capability evidence
follow/hold/move/attack support
authority
source paths
```

Use actual current loader/effect data. If a value cannot be recovered through an
authoritative loader, a strict parser may read only the relevant current
datapack XML and must validate references through `SkillData`, `NpcData`,
`ItemData` and `PetDataTable`.

Do not infer Kai/Mew/Queen/King roles from display names.

## 11. Catalog completeness and hashing

The snapshot owns complete immutable indexes:

```text
classById
childrenByClass
terminalClasses
skillLearnsByClass
classesBySkill
skillFactsByIdLevel
equippableItemsByBodyPart
itemsByWeaponFamily
summonActorsByOwnerClass
summonActorsBySkill
summonActorsByNpc
petFactsByNpc
capabilityRulesByKey
```

Component hashes:

```text
classGraphHash
skillLearningHash
skillMechanicsHash
equipmentHash
summonPetHash
capabilityRuleHash
combinedHash
```

Requirements:

- SHA-256;
- deterministic across repeated builds;
- shuffled outer loader iteration does not alter hashes;
- changing a source identity/fact changes its component hash;
- no localized names in hashes;
- startup build once;
- no query-time loader/file/DB scan.

## 12. Capability taxonomy

Keep all existing accepted keys and add only stable needed keys:

```text
combat.tank
combat.aggro_control
combat.heal
combat.resurrection
combat.recharge
combat.cleanse
combat.buff
combat.debuff
combat.crowd_control
combat.melee_damage
combat.ranged_physical_damage
combat.ranged_magic_damage
combat.aoe_damage
combat.summon
combat.stealth
combat.detection
combat.mobility
combat.escape
profession.spoil
profession.sweep
profession.craft
logistics.summon_friend
```

A capability rule contains:

```text
capabilityKey
rank
class-stage applicability
one or more evidence skill IDs/levels
target scope
weapon/equipment family requirements where factual
resource item requirements where factual
actor-state requirements
authority
source paths
```

Target scope is explicit:

```text
SELF
SINGLE_TARGET
PARTY
CLAN
ALLY
COMMAND_CHANNEL
AREA
SERVITOR
PET
NPC
```

Do not merge:

- heal and recharge;
- main buff and songs/dances;
- party buffs and clan-only buffs;
- servitor body and cubic utility;
- spoil and sweep;
- male/female Soul Hound identities.

## 13. Runtime actor progression snapshot

Under one exact materialization `ActionLease`, copy bounded immutable facts:

```text
profileId
actorObjectId
baseClassId
activeClassId
classIndex
activeClassTier
level
EXP
SP
noble
hero
subclassActive
subclasses <= current configured maximum
learned skill IDs/levels
equipped item object IDs/item IDs/slots
owned equippable candidate summaries
resource item counts only for referenced requirements
active summon/pet/cubic facts
current transformation/mount/combat/dead state
catalog combined hash
```

Rules:

- canonical `Player` is the sole owner of this state;
- no duplicate level/class/skills/equipment persistence in `PhantomProfile`;
- no Player/Item/Skill references escape the lease;
- actor snapshot reads are bounded;
- inventory candidate view is capped and paged;
- subclass list uses current configured maximum;
- missing actor returns a typed result, not fabricated defaults.

## 14. Three-level capability evaluation

For each capability return separate truth:

```text
INTRINSIC
class/catalog has evidence

LEARNED
actor knows required evidence skill(s)

READY_NOW
learned and current equipment/resources/state satisfy canonical checks
```

Readiness reasons are fixed:

```text
READY
WRONG_CLASS_STAGE
SKILL_NOT_LEARNED
PREVIOUS_SKILL_MISSING
LEVEL_TOO_LOW
SP_TOO_LOW
REQUIRED_ITEM_MISSING
WEAPON_OR_EQUIPMENT_MISMATCH
DYNAMIC_CONDITION_FAILED
INSUFFICIENT_MP_OR_HP
SKILL_DISABLED_OR_REUSE
TRANSFORMED
MOUNTED
DEAD
SUMMON_REQUIRED
SERVITOR_NOT_PRESENT
TARGET_REQUIRED
UNSUPPORTED_ACQUIRE_TYPE
```

Do not report `READY_NOW` for a skill that merely appears in a tree.

The evaluator may use the exact actual server condition methods under the actor
lease. It must not mutate actor state while evaluating.

## 15. Canonical EXP/SP/level observation

Goal 013 does not award synthetic production EXP/SP.

Canonical combat/quest/server paths continue to own rewards and level changes.

The progression service:

- observes exact current EXP/SP/level;
- detects changes between explicit snapshots;
- exposes typed `LEVEL_REACHED`/`PROGRESS_PENDING`;
- records fixed aggregate metrics;
- reconciles class/capability after a canonical level or profession event.

Real integration must prove that a test-owned canonical monster reward changes
Player EXP/SP/level and the progression service observes the same values.

Direct `addExpAndSp`, `setExp`, `setLevel` or SP grants are forbidden in
production progression code.

Test fixtures may configure isolated test-owned actor/monster starting values.

## 16. Profession graph and canonical transition boundary

Build immediate next-class targets from stable class graph facts.

A target result includes:

```text
currentClassId
targetClassId
targetTier
structurallyValid
minimumLevelFact
canonicalQuestRequired
authorizationState
```

Authorization states:

```text
ALREADY_CURRENT
STRUCTURALLY_INVALID
LEVEL_PENDING
CANONICAL_QUEST_REQUIRED
CANONICAL_ACTION_AVAILABLE
TRANSITION_OBSERVED
```

Production rules:

- no direct class mutation;
- no quest-state mutation;
- no ClassMaster cheat path;
- actual class changes made by existing quest/NPC systems are observed and
  reflected immediately;
- `progression.await_profession` succeeds only after canonical Player class state
  equals the requested target;
- if a reusable canonical non-packet facade is found, document it and use it only
  with full existing eligibility; otherwise report `CANONICAL_QUEST_REQUIRED`.

Real integration may change a test-owned actor through a test-only profession
event/seam to prove observation. That test mutation must not be reachable from
production progression code.

## 17. Subclass, certification and Noblesse facts

Use current code/config, not external memory:

- `PlayerConfig.MAX_SUBCLASS`;
- base/max subclass level;
- `VillageMaster`/`CategoryData` restrictions;
- Elf/Dark Elf exclusion;
- non-Kamael/Kamael isolation;
- similar-class exclusions;
- Overlord/Warsmith exclusion;
- Inspector special rule;
- actual quest completion predicates for subclass eligibility;
- current active/base class and class index;
- actual learned certification skills;
- actual Noble/Hero state.

Goal 013 provides read-only eligibility/query and actor snapshot facts.

It does not add, delete, modify or switch subclasses automatically and does not
grant certifications/Noblesse.

Any external claim that contradicts current code is documented as a
`CURRENT_SERVER_IMPLEMENTATION` difference.

## 18. Safe class-skill learning

Register explicit synchronous operation:

```text
progression.learn_skill
```

Request:

```text
profileId
trainerObjectId
skillId
skillLevel
planOwnershipToken
```

Only `AcquireSkillType.CLASS` is executable.

Preconditions, rechecked under exact actor action lease:

- service RUNNING and exact token current;
- one progression operation/profile;
- actor alive, not transformed, not mounted, not in combat/casting;
- exact trainer is current `lastFolkNPC`;
- trainer is valid `Folk`/`VillageMaster`, can interact and is in range;
- exact skill/level exists;
- exact `SkillLearn` exists for active class/acquire type;
- previous level known;
- character level sufficient;
- SP sufficient;
- prerequisite skills sufficient;
- all required items/counts present;
- operation generation still current immediately before consumption.

Canonical side effect sequence:

1. precheck every requirement;
2. consume exact required items through Player inventory methods;
3. deduct exact calculated SP through canonical Player state;
4. add exact skill persistently;
5. update shortcuts/skill state as needed without constructing packets;
6. dispatch existing `OnPlayerSkillLearn` event when applicable;
7. reconcile exact learned skill/SP/item state.

Requirements:

- idempotent if exact skill level already learned;
- no batch learning;
- no packet construction or packet-handler invocation;
- no free skill when `AutoLearnSkills=false`;
- no partial silent success;
- no negative SP/item count;
- no learning TRANSFER/SUBCLASS/NOBLE/HERO in this Goal;
- cancellation before side effect causes zero consumption;
- once consumption starts, operation completes synchronously under lease.

If safe conservation cannot be achieved without changing server core, return
`BLOCKED_CANONICAL_SKILL_LEARNING`.

## 19. Equipment evaluation and equip

### 19.1. Evaluation

Rank only items already owned by the actor.

Fixed bounded result per requested slot/capability:

```text
itemObjectId
itemId
bodyPart
weapon/armor family
grade
enchant
currentlyEquipped
canonicalCompatibility
compatibilityReasons
deterministicPreferenceScore
```

Score is a recommendation, not a combat formula.

Allowed deterministic inputs:

- canonical compatibility;
- matching requested weapon/armor family;
- grade/expertise;
- enchant;
- exact body part;
- current capability rule requirements;
- stable item ID tie-break.

Forbidden score inputs:

- localized item name;
- market price;
- invented DPS;
- external tier list;
- future trade availability.

At most 64 candidates per slot and page size <=256.

### 19.2. Equip operation

Register:

```text
progression.equip_item
```

Request:

```text
profileId
itemObjectId
planOwnershipToken
```

Rules:

- exact owned inventory object;
- item is equippable and still in valid location;
- actor alive, not transformed/mounted/casting/in incompatible combat state;
- canonical class/sex/grade/condition checks;
- exact operation generation and token recheck;
- use the existing canonical server equipment method;
- canonical slot conflicts and paperdoll updates remain authoritative;
- verify final equipped object/slot;
- idempotent if already equipped;
- no direct paperdoll insertion;
- no purchase, creation, enchant, augmentation or item destruction;
- no packet simulation.

If no safe existing canonical equip method exists, return
`BLOCKED_CANONICAL_EQUIP_FACADE`; do not mutate inventory/paperdoll directly.

## 20. Summon/pet current-server truth

The production catalog and normalized research must explicitly record:

- `Summon` is a separate `Playable` with its own AI;
- follow and hold map to real AI intentions;
- shots per hit are template-driven;
- servitor lifetime/upkeep/EXP multiplier are per summon effect;
- pet has control item, inventory, food, persistent level/EXP and pickup;
- servitor pickup is unsupported;
- summon death transfers known hate to owner in current code;
- BabyPet capabilities derive from actual loaded pet skills;
- pet is removed for Olympiad while servitor remains;
- current Mobius credits summon Olympiad damage to owner;
- current `Servitor` elemental getters mirror owner values;
- external retail 20/80 attribute-sharing claim conflicts with current code;
- Servitor Barrier is removed on any action except move when the exact skill data
  says so;
- Mutual Response affects servitor according to current skill data, not owner;
- Summon Friend remains party/item/territory restricted.

Do not modify `Summon`, `Servitor`, `Pet`, `BabyPet`, Olympiad or attribute
formulas in Goal 013.

The attribute discrepancy is a documented integration gate, not a silent fix.

## 21. Research normalization

The user supplied five external deep-research documents outside Git.

Do not copy their raw prose into the repository.

Use the audited summaries in this package and revalidate every retained claim
against the current repository/datapack.

Create:

```text
docs/phantoms/research/high-five-behavior/README.md
docs/phantoms/research/high-five-behavior/SOURCE_AUTHORITY_MODEL.md
docs/phantoms/research/high-five-behavior/DR-01-CLASS-PROGRESSION-NORMALIZED.md
docs/phantoms/research/high-five-behavior/DR-01-CONTRADICTIONS-AND-LIVE-GATES.md
docs/phantoms/research/high-five-behavior/DR-02-PVE-CLASS-CAPABILITIES-NORMALIZED.md
docs/phantoms/research/high-five-behavior/DR-03-PVP-CLASS-EQUIPMENT-MECHANICS-NORMALIZED.md
docs/phantoms/research/high-five-behavior/DR-04-PARTY-ROLE-CAPABILITY-MATRIX-NORMALIZED.md
docs/phantoms/research/high-five-behavior/DR-05-SUMMON-PET-ACTOR-CATALOG-NORMALIZED.md
docs/phantoms/research/high-five-behavior/DR-05-CURRENT-SERVER-CONTRADICTIONS.md
docs/phantoms/research/high-five-behavior/DEFERRED-CLAIMS-BY-GOAL.md
```

Requirements:

- no raw `turn...` citation markers;
- no copied forum tier lists;
- every retained mechanical fact has a current source path and stable IDs;
- recommendations clearly marked;
- contradictions preserved, not resolved by preference;
- external-only claims are `EXTERNAL_RETAIL_CLAIM` or `DISPUTED`;
- deferred PvE farming policy points to Goal 015/021;
- party lifecycle/Rift points to Goal 017/023;
- PvP/PK/Olympiad tactics points to Goal 025;
- raid/epic orchestration points to Goal 026;
- personality/reputation points to Goal 018;
- Goal 013 docs contain only class/progression/skill/equipment/summon mechanics
  and capability implications.

## 22. Queries

Create bounded indexed queries equivalent to:

```text
findClass(classId)
classChildren(classId, page)
terminalClasses(page)
classSkillLearns(classId, acquireType, page)
classesForSkill(skillId, level, page)
findSkillFact(skillId, level)
equippableItems(bodyPart/family, page)
summonActorsForClass(classId, page)
summonActorForSkill(skillId, level)
actorProgression(profileId)
actorCapabilities(profileId, page)
equipmentCandidates(profileId, slot/capability, page)
professionTargets(profileId, page)
subclassEligibility(profileId, page)
```

Rules:

- page size <=256;
- no loader/file/DB scan in ordinary query;
- actor queries may acquire one exact action lease;
- no full inventory copy beyond fixed candidate bound;
- deterministic cursor/order;
- unknown ID or empty intersection returns empty, never widened results;
- query metrics use fixed labels only.

## 23. Service ownership and lifecycle

Progression service has no worker.

One synchronous operation/profile at a time:

```text
OBSERVE
LEARN_SKILL
EQUIP_ITEM
```

Ownership:

- reserve operation under service monitor;
- release monitor;
- acquire exact materialization ActionLease;
- read/recheck canonical actor state;
- perform at most one supported side effect;
- reconcile operation generation/token;
- release lease exactly once;
- release operation slot;
- backend calls outside service monitor.

Construction/register handlers before decision registry seal.

Startup order:

```text
materialization
→ construct progression and register handlers
→ decision
→ navigation
→ topology
→ Game Knowledge
→ progression.start/build catalog
→ combat.start
→ scheduler
```

Disabled path creates no progression service.

Shutdown:

```text
scheduler.beginStop
→ decision.beginStop
→ combat.beginStop
→ progression.beginStop
→ knowledge/topology/navigation beginStop
→ combat.finishStop
→ progression.finishStop
→ only then materialization shutdown
```

`progression.finishStop()` is false while an operation or actor lease is owned.

Aggregate diagnostics:

```text
progressionState
catalogHash
operationsCurrent
actorLeases
learnRequests
equipRequests
```

No profile/class/skill/item IDs in aggregate logs.

## 24. Decision handlers

Register before seal:

```text
progression.observe
progression.await_level
progression.await_profession
progression.learn_skill
progression.equip_item
```

No production candidate is registered.

Handler rules:

- strict target namespace and numeric argument validation;
- exact context cancellation token;
- typed SUCCESS/RETRY/REPLAN/CANCELLED mappings;
- no packet/bypass simulation;
- `await_level` and `await_profession` are observation-only;
- `learn_skill` and `equip_item` execute one synchronous action;
- unknown/extra argument rejects;
- no unbounded retries.

## 25. Metrics

Fixed aggregate counters:

- catalog builds/failures;
- class/skill/equipment/summon facts;
- queries and empty queries;
- actor snapshot requested/success/missing;
- capability evaluations;
- operations requested/accepted/rejected/current/peak;
- actor leases acquired/rejected/released/current;
- skill learn success/idempotent/rejected/failure;
- SP/items consumed;
- equip success/idempotent/rejected/failure;
- canonical quest required;
- cancellation;
- stop failure.

No dynamic labels by class/skill/item/profile.



## 26. Tests

Create:

```text
test/java/org/l2jmobius/tests/phantoms/PhantomProgressionCatalogSuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomProgressionParitySuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomCapabilityRuntimeSuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomProgressionOperationSuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomProgressionServerIntegrationSuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomProgressionPerformanceSuite.java
```

Launcher/Ant:

```text
progression-catalog
phantom-progression-catalog-test

progression-parity
phantom-progression-parity-test

capability-runtime
phantom-capability-runtime-test

progression-operations
phantom-progression-operations-test

progression-server-integration
phantom-progression-server-integration-test

progression-performance
phantom-progression-performance-smoke
```

### 26.1. Catalog: at least 60 cases ×3

Cover:

- every PlayerClass exactly once;
- parent/root/tier/next graph;
- no cycles;
- enum parent versus skill-tree parent distinction;
- terminal class count reconstructed;
- male/female Soul Hound distinct;
- Inspector/Judicator stage truth;
- every complete class-tree entry represented;
- CLASS/TRANSFER/SUBCLASS/NOBLE separation;
- previous-level/prerequisite/item/SP facts;
- every referenced Skill and Item resolves;
- capability keys and evidence;
- target scopes;
- all existing Goal 011 capabilities preserved;
- equipment family/body-part/grade facts;
- every summon relation resolves skill/NPC/item refs;
- pet/control/food/skill facts;
- deterministic component/combined hashes;
- outer iteration shuffle invariance;
- source fact change sensitivity.

### 26.2. Independent parity: at least 32 cases ×2

Expected facts must be reconstructed directly from loaders/current code seams,
not by calling the production catalog backend twice.

Independently compare:

- PlayerClass graph;
- SkillTreeData complete class trees and acquire types;
- SkillData facts;
- ItemData equippables;
- CategoryData terminal/subclass groups;
- PlayerConfig subclass limits;
- PetDataTable facts;
- NpcData summon/pet templates;
- summon effect facts;
- current Servitor/Pet/BabyPet behavior flags.

### 26.3. Runtime capability: at least 40 cases ×3

Representative matrix:

- tank/aggro;
- healer/resurrection/recharge/cleanse;
- buffer versus song/dance versus clan scope;
- melee/pole/dagger;
- bow/crossbow;
- nuker/debuffer/control;
- spoiler/sweeper/crafter;
- summoner/servitor/pet/cubic;
- Doombringer/Soul Hound/Trickster/Judicator;
- main versus subclass;
- intrinsic but unlearned;
- learned but wrong weapon/resource;
- ready now;
- target required;
- missing summon/servitor;
- wrong transformation/mount/death state.

No case may infer a capability from a class name.

### 26.4. Operations: at least 24 cases ×3

Skill learning:

- exact valid class skill;
- already learned idempotency;
- previous skill missing;
- wrong active class;
- level too low;
- SP too low;
- required item missing;
- trainer absent/wrong/out of range;
- transformed/mounted/in combat;
- cancelled before side effect;
- operation/profile conflict;
- unsupported acquire types;
- exact SP/item/skill conservation;
- event dispatch;
- backend Throwable/lease release.

Equipment:

- exact owned compatible item;
- already equipped;
- foreign object ID;
- non-equippable;
- wrong grade/expertise/condition;
- cancelled before equip;
- actor state restrictions;
- exact canonical slot result;
- no item creation/destruction;
- foreign operation cannot replace ownership.

Profession:

- invalid branch;
- level pending;
- canonical quest required;
- observed canonical class transition;
- no direct production `setPlayerClass`.

### 26.5. Real server integration: at least 18 cases ×2

Use only `l2jmobiush5_phantom_test`, existing headless/materialization environment
and shared loaders.

Mandatory:

1. exact materialized actor snapshot equals canonical Player;
2. test-owned normal monster reward changes canonical EXP/SP and is observed;
3. level-up observation matches canonical Player level;
4. one real class skill appears in catalog and can be learned at a valid
   test-owned trainer with exact SP/item conservation;
5. previous-level/required-item rejection is real;
6. real compatible owned weapon/armor equips through canonical method;
7. incompatible/foreign item is rejected without mutation;
8. profession target graph identifies exact next classes;
9. canonical test-only profession change is observed without production direct
   mutation;
10. subclass/config restrictions match current VillageMaster/CategoryData;
11. active main/subclass snapshots differ correctly;
12. actual Noble/certification state is observed;
13. one real servitor relation resolves skill→NPC/upkeep/EXP facts;
14. one real pet resolves control/food/inventory facts;
15. BabyPet heal/recharge capability derives from loaded skills;
16. current Servitor attribute behavior is recorded as implementation fact;
17. action lease blocks dematerialization during a progression operation;
18. cancellation and shutdown leave zero operations/leases;
19. production progression package constructs no packet or NPC bypass;
20. production JAR contains progression classes and no tests.

Test-only actor setup may set starting EXP/SP/class/inventory and may simulate a
canonical profession event. Production progression code must not expose those
test mutations.

### 26.6. Research normalization verifier

Verify every required normalized document:

- exists;
- contains current source paths/stable IDs;
- contains authority/confidence;
- contains no `turn[0-9]`, raw browser citation or pasted raw research header;
- preserves the listed contradictions;
- defers out-of-scope doctrine to exact future Goals;
- does not assert class rankings as server facts.

### 26.7. Performance ×2

Byte-identical canonical summary:

```text
catalogBuilds=3
classQueries=100000
skillQueries=100000
capabilityEvaluations=100000
equipmentQueries=50000
summonPetQueries=50000
operations=10000
```

Structural gates:

```text
workers/tasks/futures = 0
operations current after run = 0
actor leases after run = 0
query page <=256
equipment candidates <=64/slot
capability evidence bounded
snapshot hashes identical
no query loader/file/DB scans
```

Elapsed is evidence only. Focused timeout <=120 seconds.

### 26.8. Cumulative regressions

Repeat:

- progression catalog ×3;
- progression parity ×2;
- capability runtime ×3;
- progression operations ×3;
- progression real integration ×2;
- progression performance ×2;
- combat core/ownership/action-ownership ×3;
- combat real integration ×2;
- combat performance ×2;
- all Goal 011A knowledge routes;
- topology/navigation/decision/scheduler/materialization/headless/profile/DB;
- all historical static/negative controls;
- cumulative `ant verify`;
- standalone `ant jar`.

## 27. Exact scope

Allowed production:

```text
java/org/l2jmobius/gameserver/phantoms/progression/**
java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java
java/org/l2jmobius/gameserver/phantoms/PhantomMetrics.java
java/org/l2jmobius/gameserver/Shutdown.java
```

One minimal compatibility change is allowed to:

```text
java/org/l2jmobius/gameserver/phantoms/combat/PhantomCombatCapabilityResolver.java
```

only to consume the new catalog through a port while preserving all Goal 012A
safety behavior and zero production candidates.

Allowed datapack:

```text
dist/game/data/phantoms/progression/**
```

Only strict versioned curated capability/progression metadata. Do not duplicate
loader-owned class/skill/item facts.

Allowed tests/build:

```text
build.xml
test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java
test/java/org/l2jmobius/tests/phantoms/PhantomProgressionCatalogSuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomProgressionParitySuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomCapabilityRuntimeSuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomProgressionOperationSuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomProgressionServerIntegrationSuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomProgressionPerformanceSuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomCombatCoreSuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomServerShutdownHandoffSuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomSkeletonSuite.java
tools/phantoms/verify-task-013.ps1
```

Minimal compile/regression changes to existing Phantom tests are allowed for
System snapshots/constructor wiring.

Allowed documentation:

```text
docs/PHANTOM_BOTS_ROADMAP.md
docs/phantoms/architecture/PROGRESSION_CAPABILITY_CONTRACT.md
docs/phantoms/research/high-five-behavior/**
docs/phantoms/tasks/013-class-progression-capability-catalog/**
docs/phantoms/reports/012a-combat-action-ownership-truth.md
docs/phantoms/reports/013-class-progression-capability-catalog.md
docs/phantoms/reviews/012a-combat-action-ownership-truth-review.md
```

## 28. Frozen scope

Do not modify:

- `.gitignore`;
- Player/PlayerStat;
- PlayerClass/ClassListData/CategoryData;
- Skill/SkillData/SkillTreeData/SkillLearn;
- RequestAcquireSkill;
- Folk/VillageMaster;
- Item/Inventory/item handlers;
- Summon/Servitor/Pet/BabyPet/Cubic;
- PetDataTable/NpcData/ItemData;
- materialization/decision/navigation/topology/knowledge semantics;
- Goal 012A combat service/backend/action ownership;
- datapack class/skill/item/pet/NPC XML;
- config or DB schema;
- Goal 014/015.

## 29. Hard out of scope

Forbidden:

- direct production `setPlayerClass`, EXP/SP/level grants;
- quest completion or quest-item fabrication;
- automatic subclass/certification/Noblesse;
- free skills or batch auto-learn;
- buying/selling/travel/supply restock;
- equipment purchase, creation, enchant or augmentation;
- farming zone policy/background rewards;
- party/Rift/raid/PvP/PK/Olympiad doctrine implementation;
- summon/pet combat command execution;
- attribute-sharing formula fix;
- packet or NPC bypass simulation;
- direct paperdoll/inventory insertion;
- new executor/raw thread/per-profile task/Future;
- production DB;
- other chronicles/dependencies/CI/mass formatting;
- amend/rebase/merge/force push.

## 30. Static verifier

Create deterministic read-only:

```text
tools/phantoms/verify-task-013.ps1
```

Verify:

- exact base `8dba87e9...`, one ordinary exact-scope commit;
- reviewed `.gitignore` unchanged;
- no server core/datapack/config/schema/Goal 014/015;
- no direct `setPlayerClass`, `addExpAndSp`, `setExp`, `setLevel` or synthetic SP
  grant in production progression;
- no packet imports/constructors/handlers/NPC bypass;
- no direct paperdoll insertion/item creation/destruction for equip;
- only CLASS skill learning executable;
- exact trainer/SkillLearn/level/SP/prerequisite/item checks;
- no batch free learning;
- one synchronous operation/profile and exact ActionLease;
- no thread/executor/Future/worker;
- complete class/skill/equipment/summon indexes;
- terminal and male/female Soul Hound/Inspector/Judicator tests;
- runtime INTRINSIC/LEARNED/READY separation;
- no localized identity/hash;
- page/candidate bounds and no query scans;
- current-server summon contradictions documented, not fixed;
- normalized DR docs, no raw `turn...` citations;
- no class ranking as mechanical fact;
- lifecycle/shutdown before materialization;
- focused/real/performance tests;
- Goal 012A closure and roadmap progress;
- UTF-8, mojibake, escaped Cyrillic;
- no credentials/binaries;
- verifier deterministic/read-only.

## 31. Documentation

Create:

```text
docs/phantoms/architecture/PROGRESSION_CAPABILITY_CONTRACT.md
docs/phantoms/reports/013-class-progression-capability-catalog.md
```

Contract sections:

- authority/source precedence;
- catalog model and hashes;
- class graph and profession boundary;
- skill learn facts/readiness;
- actor progression snapshot;
- capability levels and target scope;
- skill learning transaction;
- equipment evaluation/equip;
- subclass/Noble/certification observation;
- summon/pet taxonomy and current-server contradictions;
- handlers;
- lifecycle/bounds/metrics;
- research normalization;
- explicit exclusions.

Report sections:

- status/baseline;
- Goal 012A closure;
- source audit;
- class/skill/equipment/summon corpus counts;
- catalog hashes;
- profession canonical boundary;
- runtime capability model;
- skill learning;
- equipment;
- subclass/Noble;
- research normalization DR-01…DR-05;
- lifecycle/handlers/inertness;
- tests/performance/regressions;
- DB/config/scope;
- verifier;
- limitations;
- branch/parent/subject;
- manual gate `PENDING_INDEPENDENT_REVIEW`;
- Goal 014/015 `NOT_STARTED`.

## 32. Commands

Pre-change:

```bat
ant verify
powershell -NoProfile -ExecutionPolicy Bypass -File tools\phantoms\verify-task-012a.ps1
```

Targeted:

```bat
ant compile-tests
ant phantom-progression-catalog-test
ant phantom-progression-parity-test
ant phantom-capability-runtime-test
ant phantom-progression-operations-test
ant phantom-progression-server-integration-test
ant phantom-progression-performance-smoke
ant phantom-combat-core-test
ant phantom-combat-ownership-test
ant phantom-combat-action-ownership-test
ant phantom-combat-server-integration-test
ant phantom-combat-performance-smoke
ant phantom-game-knowledge-core-test
ant phantom-game-knowledge-parity-test
ant phantom-game-knowledge-query-truth-test
ant phantom-game-knowledge-content-test
ant phantom-game-knowledge-performance-smoke
ant phantom-topology-scheduler-signal-integration-test
ant phantom-topology-signal-ledger-test
ant phantom-topology-generation-test
ant phantom-topology-perception-test
ant phantom-topology-core-test
ant phantom-topology-production-corpus-test
ant phantom-topology-performance-smoke
ant phantom-navigation-core-test
ant phantom-navigation-performance-smoke
ant phantom-server-shutdown-handoff-test
ant phantom-decision-core-test
ant phantom-decision-persistence-test
ant phantom-decision-performance-smoke
ant phantom-activity-scheduler-test
ant phantom-production-materialization-test
ant phantom-headless-player-test
ant phantom-profile-persistence-test
ant phantom-db-test
ant test
ant phantom-skeleton-test
```

Repeat focused suites according to §26.

Final:

```bat
ant verify
ant jar
powershell -NoProfile -ExecutionPolicy Bypass -File tools\phantoms\verify-task-013.ps1
git diff --check
git status --short --branch
```

Post-commit:

```bat
git rev-parse HEAD
git show --stat --oneline --decorate HEAD
git diff --check 8dba87e9c1d5828376b80c1ea16c4578726d4947...HEAD
ant verify
ant jar
powershell -NoProfile -ExecutionPolicy Bypass -File tools\phantoms\verify-task-013.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File tools\phantoms\verify-task-013.ps1
git push origin feature/phantom-world
git ls-remote --heads origin feature/phantom-world
git status --short --branch
```

Compare verifier and performance summaries byte-for-byte/SHA-256 outside the
repository.

## 33. Acceptance result

Successful result:

```text
CLASS_PROGRESSION_CAPABILITY_CATALOG_IMPLEMENTED_PENDING_INDEPENDENT_REVIEW
```

Do not self-accept Goal 013 and do not start Goal 014/015.

## 34. Commit/push

Commit subject:

```text
feat(phantoms): add class progression capability catalog
```

One ordinary commit on top of `8dba87e9...`.

Push regardless of SUCCESS/BLOCKED using only safe scoped artifacts.

## 35. Blocking behavior

Return `BLOCKED` if:

- complete loader parity cannot be built without changing server loaders;
- class-skill learning cannot conserve SP/items through existing Player APIs;
- canonical equip requires direct paperdoll mutation;
- catalog query requires hot-path loader/file/DB scans;
- profession support would require bypassing canonical quests;
- summon effect facts cannot be recovered safely and no strict bounded parser is
  possible;
- Goal 014/015/config/schema/core changes are required;
- production DB is accessed;
- cumulative verify/jar fails.

Profession direct mutation being unavailable is not by itself a blocker when the
required `CANONICAL_QUEST_REQUIRED` boundary, target planning and canonical
change observation are fully implemented.

On blocker remove unsafe production wiring, preserve safe catalog/audit/tests/
report/verifier, ordinary commit/push and keep Goal 014/015 not started.

## 36. Final handoff

```text
Status:
Architecture result:
Baseline:
Goal 012A closure:
Class facts:
Terminal classes:
Class graph hash:
Skill learning facts:
Skill mechanics facts:
Equipment facts:
Summon/servitor facts:
Pet facts:
Catalog combined hash:
Male/Female Soul Hound:
Inspector/Judicator:
Profession target planning:
Canonical profession action:
Canonical quest-required boundary:
EXP/SP/level observation:
Actor snapshots:
Intrinsic/Learned/Ready capability:
Target scope:
Class skill learning:
SP/item conservation:
Equipment evaluation:
Canonical equip:
Subclass eligibility:
Noble/certification observation:
DR-01 normalized:
DR-02 subset normalized:
DR-03 subset normalized:
DR-04 subset normalized:
DR-05 normalized:
Current-server summon contradictions:
Decision handlers:
Production candidates:
Workers/tasks:
Lifecycle/shutdown:
Catalog tests:
Parity tests:
Capability runtime tests:
Operation tests:
Real server integration:
Performance:
Performance summary SHA:
All regressions:
ant verify:
ant jar:
Verifier final 1/final 2/identical/SHA:
Production DB:
JAR progression/test entries:
Commit/parent/branch/push/remote:
Report:
Manual gate:
Goal 014:
Goal 015:
Limitations/blockers:
```
