# GOAL 011 — Authoritative Game Knowledge and reverse indexes

## 1. Identifier

- **Goal ID:** `011-authoritative-game-knowledge`
- **Roadmap stage:** II — Scheduler, goals, navigation and authoritative knowledge
- **Branch:** `feature/phantom-world`
- **Accepted baseline:** `7575ce4c66bdf5c51a27b20bed57c4ed8721b1e2`
- **Parent:** `030184205c6bf2101cb6256086c0b85c0e26dcd4`
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
Goal 007 / 007A: ACCEPT
Goal 008 / 008A: ACCEPT
Goal 009 / 009A: ACCEPT
Goal 010: ACCEPT after Goal 010A / 010B / 010C
Goal 010A: ACCEPT
Goal 010B: ACCEPT
Goal 010C: ACCEPT
Goal 011: ALLOWED
Goal 012: NOT_STARTED
Goal 013: NOT_STARTED
```

Goal 010C accepted facts:

```text
Commit: 7575ce4c66bdf5c51a27b20bed57c4ed8721b1e2
Parent: 030184205c6bf2101cb6256086c0b85c0e26dcd4
Remote: exact
Real scheduler integration: 5/5 ×3
Signal ledger: 20/20 ×3
Generation: 17/17 ×3
All cumulative suites: PASS
Verifier: 67/67 ×2, byte-identical
Verifier SHA-256:
03F88A544D1C2D744B6E493AE3140521C97CBEAD21B0FDC7C17F0AE07CB41BE9
Independent verdict: ACCEPT
```

## 3. User-visible result

After Goal 011 the Phantom subsystem owns one immutable, language-independent
Game Knowledge snapshot built from authoritative High Five server data.

It can answer, by stable IDs:

```text
item -> mobs that drop it
item -> mobs that spoil it
item -> static manor seed/crop/reward relations
mob -> exact loaded spawn facts and topology areas
level/topology range -> suitable target facts
recipe/product -> ingredients
ingredient -> recipes/products
class -> capability facts
capability -> classes
content -> recommended role/capability requirements
```

The snapshot exposes:

- stable typed facts;
- complete reverse indexes;
- deterministic canonical component hashes;
- one combined source hash;
- fixed provenance/authority classification;
- bounded paged query results;
- no DB query or datapack scan in ordinary query paths.

Production behavior remains inert:

```text
automatic knowledge query = 0
automatic goal/action creation = 0
automatic movement/combat = 0
per-profile knowledge state = 0
knowledge worker/thread/task = 0
```

No Semantic Pack or natural-language parser is added.

## 4. Truth and authority model

Every fact must declare one authority:

```text
SERVER_LOADER_FACT
STATIC_DATAPACK_FACT
TOPOLOGY_SNAPSHOT_FACT
CURATED_RECOMMENDATION
```

### SERVER_LOADER_FACT

Copied from already loaded immutable/mechanical server objects:

- `ItemData`;
- `NpcData` / `NpcTemplate`;
- `SpawnTable`;
- `RecipeData`;
- `SkillTreeData`;
- `SkillData`;
- `PlayerClass`.

### STATIC_DATAPACK_FACT

Parsed by a dedicated read-only parser from static datapack data where the
existing runtime singleton has DB/task side effects:

- `data/Seeds.xml`;
- Goal 011 curated knowledge XML.

### TOPOLOGY_SNAPSHOT_FACT

Copied from the accepted Goal 010 immutable topology snapshot.

### CURATED_RECOMMENDATION

Semantic role/capability and party-requirement statements which the base server
does not encode as a mechanical rule.

They must:

- use stable IDs;
- cite factual class/skill/NPC/topology/source evidence;
- be versioned;
- never be presented as mandatory server enforcement;
- never be inferred from localized names or titles.

## 5. Important factual boundaries

### Drop and spoil

`NpcTemplate` exposes:

- grouped death drops;
- ungrouped death drops;
- spoil drops.

Knowledge preserves the raw loaded structure. It must not call a recorded
chance the exact probability of one runtime kill.

Runtime results can additionally depend on:

- server rates;
- level difference;
- premium status;
- champion logic;
- raid multipliers;
- seeded state;
- item-specific rate overrides.

Store raw group/item chance fields and chance model only.

### Manor

Do **not** instantiate or call `CastleManorManager`.

Its constructor:

- parses `Seeds.xml`;
- loads mutable castle procurement/production from DB;
- can schedule manor tasks.

Goal 011 parses static `Seeds.xml` directly and stores only:

- seed/crop/mature/reward relationships;
- castle association;
- seed level;
- alternative flag;
- raw limits and source evidence.

Current manor procurement, prices, amounts and mutable castle economy are out of
scope.

### Rift and other content

Do **not** instantiate `DimensionalRiftManager`, raid-boss managers or mutable
world-content managers merely to build knowledge. Some of them query DB or
create runtime spawns.

Content recommendations are loaded from dedicated curated XML and validated
against safe static/loaded facts.

## 6. Architectural boundary

Goal 011 owns:

- immutable knowledge facts and snapshot;
- factual backend copying existing loader data;
- static `Seeds.xml` parser;
- curated class-capability/content-requirement parser;
- source/provenance hashing;
- reverse indexes;
- target lookup indexes;
- bounded query API;
- single production startup build;
- aggregate metrics and diagnostics;
- source-parity and performance evidence.

Goal 011 does not own:

- text parsing;
- item-name resolution;
- Utility AI candidates or plans;
- combat/action execution;
- movement;
- party composition search/optimization;
- market prices or current manor economy;
- dynamic world spawn changes after build;
- profile inventory;
- mutable player state;
- population.

## 7. Mandatory reading

Read fully:

1. roadmap, master plan, `Agents.md`, workflow/package/report standards;
2. Goals 008–010C packages/reports/reviews/contracts;
3. current:
   - `ItemData`, `ItemTemplate`, weapon/armor/etc item types;
   - `NpcData`, `NpcTemplate`, `DropHolder`, `DropGroupHolder`;
   - `SpawnData`, `SpawnTable`, `Spawn`;
   - `RecipeData`, `RecipeList`, `RecipeHolder`;
   - `CastleManorManager` and `Seed` only for factual audit;
   - `SkillTreeData`, `SkillData`, `Skill`, `SkillLearn`;
   - `PlayerClass`;
   - current topology service/query/snapshot;
   - `PhantomSystem`, build and tests;
4. datapack:
   - item/NPC/recipe/seed/skill-tree sources;
   - selected Rift/raid/epic source evidence;
5. all files in this package.

Do not read or modify another chronicle.

## 8. Initial Git audit

```bat
git rev-parse --show-toplevel
git status --short --branch
git remote -v
git fetch origin --prune
git rev-parse HEAD
git rev-parse origin/feature/phantom-world
git show --stat --oneline 7575ce4c66bdf5c51a27b20bed57c4ed8721b1e2
git diff --name-status 030184205c6bf2101cb6256086c0b85c0e26dcd4..7575ce4c66bdf5c51a27b20bed57c4ed8721b1e2
```

Expected:

```text
HEAD == origin/feature/phantom-world == 7575ce4c...
```

The extracted Goal 011 package is expected untracked. Preserve unrelated
`docs/agent-tasks/**`. Return `BLOCKED_BASELINE_DRIFT` for unreviewed
production/config/schema drift.

## 9. Close Goal 010C

Update:

```text
docs/phantoms/reports/010c-topology-absent-source-reconciliation.md
```

Add immutable handoff:

```text
Commit: 7575ce4c66bdf5c51a27b20bed57c4ed8721b1e2
Parent: 030184205c6bf2101cb6256086c0b85c0e26dcd4
Push/remote: exact
Real scheduler integration: 5/5 ×3
Signal ledger: 20/20 ×3
Generation: 17/17 ×3
Final verifier: 67/67 ×2, byte-identical
Verifier SHA-256:
03F88A544D1C2D744B6E493AE3140521C97CBEAD21B0FDC7C17F0AE07CB41BE9
Independent review: ACCEPT
Goal 010: ACCEPT after Goal 010A/010B/010C
Goal 011: ALLOWED
```

Create:

```text
docs/phantoms/reviews/010c-topology-absent-source-reconciliation-review.md
```

Verdict:

```text
Goal 010: ACCEPT after Goal 010A/010B/010C
Goal 010A: ACCEPT
Goal 010B: ACCEPT
Goal 010C: ACCEPT
Revert: NOT_REQUIRED
Goal 011: ALLOWED
Goal 012: NOT_STARTED
```

Roadmap progress only:

- accepted baseline becomes `7575ce4c...`;
- Goal 010/010A/010B/010C become `ACCEPT`;
- Goal 011 becomes `IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`;
- Goal 012/013 remain `NOT_STARTED`;
- do not rewrite future Goal architecture.


## 10. Production package and curated data

Create:

```text
java/org/l2jmobius/gameserver/phantoms/knowledge/**
dist/game/data/phantoms/knowledge/*.xml
```

Responsibility-equivalent types:

```text
PhantomGameKnowledgeService
PhantomGameKnowledgeBuilder
PhantomGameKnowledgeBackend
L2jGameKnowledgeBackend
PhantomGameKnowledgeSnapshot
PhantomGameKnowledgeQuery
PhantomGameKnowledgePolicy
PhantomGameKnowledgeMetrics
PhantomStaticManorParser
PhantomCuratedKnowledgeParser
```

Small immutable enums/records/index helpers are allowed.

No knowledge value/query type stores or exposes:

- `Player`;
- `Creature`;
- mutable `NpcTemplate`, `ItemTemplate`, `Spawn`, `RecipeList`, `Skill`;
- DB connection/result;
- XML DOM node;
- mutable loader collection.

## 11. Fixed policy bounds

Production defaults, not config keys:

```text
source files                         <= 4096
items                                <= 100000
NPC templates                        <= 100000
drop/spoil facts                     <= 2000000
spawn facts                          <= 1000000
recipes                              <= 100000
recipe ingredients                   <= 1000000
manor facts                          <= 100000
class capability facts               <= 50000
content entries                      <= 4096
requirements per content             <= 64
evidence references per curated fact <= 16
evidence skills per capability       <= 32
query page size                      1..256
target level range width             <= 100
topology node results                <= 64
spawn samples returned               <= 256
source string length                 <= 512
curated key length                   <= 96
```

A source-truth count above a bound blocks the build. Never silently truncate an
internal authoritative index.

Public queries page results deterministically.

## 12. Immutable fact model

Detailed contract: `DATA_MODEL.md`.

### 12.1. Item fact

Fields equivalent to:

```text
itemId
category: WEAPON / ARMOR / ETC
crystalType
referencePrice
stackable
authority = SERVER_LOADER_FACT
```

No item name is identity. Optional names are excluded from canonical hashes and
public facts in Goal 011.

### 12.2. NPC fact

Fields equivalent to:

```text
npcId
level
kind: MONSTER / RAID_BOSS / GRAND_BOSS / OTHER_ATTACKABLE
attackable
targetable
canBeSown
exp
sp
authority = SERVER_LOADER_FACT
```

Only attackable target facts enter target indexes. Noncombat NPCs may exist in
the direct NPC map only if needed for content evidence; they do not enter target
queries.

### 12.3. Drop/spoil fact

Fields equivalent to:

```text
npcId
itemId
sourceKind: DEATH_DROP / SPOIL
chanceModel: UNGROUPED_INDEPENDENT / GROUP_CUMULATIVE
groupOrdinal
itemOrdinal
rawGroupChance
rawItemChance
minimumCount
maximumCount
authority = SERVER_LOADER_FACT
```

Rules:

- exact `double` values hashed by raw IEEE bits or deterministic hexadecimal;
- group and item order are canonicalized by stable factual tuple, not hash-map
  iteration;
- minimum/max are non-negative and max >= min;
- item and NPC references must exist;
- no effective rate, expected value or ranking probability is invented.

### 12.4. Spawn fact and area summary

Spawn fact equivalent to:

```text
npcId
instanceId
x/y/z
amount
locationId
pointKind: EXACT / TERRITORY_OR_UNRESOLVED
optional topologyNodeId
optional mapRegionLocId
authority = SERVER_LOADER_FACT / TOPOLOGY_SNAPSHOT_FACT
```

Area summary:

```text
npcId
instanceId
optional topologyNodeId
optional mapRegionLocId
spawnCount
totalConfiguredAmount
bounded representative points
```

Rules:

- copy exact loaded spawn values;
- preserve unresolved/random-territory facts explicitly;
- do not fabricate a topology node;
- topology mapping uses the accepted immutable Goal 010 snapshot;
- current runtime-spawn changes after the one-time build are not tracked.

### 12.5. Recipe fact

Fields equivalent to:

```text
recipeListId
recipeItemId
productItemId
productCount
rareProductItemId/count/chance
craftLevel
successRate
dwarven
ingredients: itemId + count
authority = SERVER_LOADER_FACT
```

All item references must exist.

### 12.6. Manor fact

Fields equivalent to the static `Seeds.xml` values:

```text
castleId
seedItemId
cropItemId
matureItemId
reward1ItemId
reward2ItemId
seedLevel
alternative
rawSeedLimit
rawCropLimit
sourcePath
authority = STATIC_DATAPACK_FACT
```

Do not use rate-adjusted limits from the mutable runtime manager.

Every item reference must exist.

### 12.7. Class capability fact

Fields:

```text
classId
capabilityKey
rank 1..1000
authority = CURATED_RECOMMENDATION
evidence skill IDs/levels
sourceRefs
```

Intrinsic mechanical facts are separate:

```text
classId
race
classTier
mage
summoner
parentClassId
authority = SERVER_LOADER_FACT
```

Required curated capability keys:

```text
combat.tank
combat.heal
combat.resurrection
combat.buff
combat.debuff
combat.crowd_control
combat.melee_damage
combat.ranged_physical_damage
combat.ranged_magic_damage
combat.summon
profession.spoil
profession.craft
```

Additional stable keys are allowed if documented and bounded.

Each capability fact must cite one or more factual evidence skills present in
the complete class skill tree, except an explicitly documented intrinsic fact
such as summoner.

Do not infer capability from enum/class names.

### 12.8. Content requirement

Fields:

```text
contentId
contentKind: RIFT / RAID / EPIC / INSTANCE / FARMING / OTHER
optional npcId
optional topologyNodeId
optional topologyAnchorId
recommendedMinParty
recommendedMaxParty
requirements:
  capabilityKey
  minimumCount
  minimumRank
  required/recommended
sourceRefs
authority = CURATED_RECOMMENDATION
```

This is a recommendation corpus, not server admission enforcement.

## 13. Curated knowledge XML

Create one or more strict versioned files:

```xml
<knowledge schemaVersion="1"
           datasetId="high-five-core"
           datasetVersion="1">
```

Supported entities:

```text
classCapability
contentRequirement
```

Rules:

- unknown element/attribute rejected;
- stable ID/key syntax;
- deterministic file and entity order;
- strict counts before allocation;
- source evidence path exists inside datapack root;
- class ID exists;
- evidence skill exists in the class complete skill tree;
- NPC ID exists when referenced;
- topology node/anchor exists when referenced;
- capability keys referenced by content exist;
- every requirement is satisfiable by at least one class at the minimum rank;
- no localized class/content/item names used as identity.

### Required production coverage

Without hardcoding a class count:

- every terminal playable `PlayerClass` must have at least one curated combat or
  profession capability;
- all required capability keys above must have at least one satisfying class;
- include factual, evidence-backed recommendation entries for:
  - at least one Dimensional Rift content tier;
  - at least one real RaidBoss content target;
  - at least one real GrandBoss/epic content target;
- cite exact datapack source paths and IDs;
- document that the corpus is a starting recommendation set, not complete party
  strategy for every encounter.

Do not invent NPC IDs, skill IDs, source paths or topology references.

## 14. Factual backend

Detailed contract: `SOURCE_OF_TRUTH.md`.

### Item extraction

Copy all non-null `ItemData.getAllItems()` templates.

### NPC/drop/spoil extraction

Iterate `NpcData.getTemplates(_ -> true)`.

Copy:

- template mechanical facts;
- grouped death drops;
- ungrouped death drops;
- spoil list.

The builder must include every loaded fact and no extra fact.

### Spawn extraction

Copy the current loaded `SpawnTable` deterministically.

Do not call runtime spawn methods and do not mutate the table.

### Recipe extraction

Use `RecipeData.getAllItemIds()` plus factual lookup APIs and copy every unique
recipe exactly once.

Verify no duplicate list/product ambiguity is hidden.

### Manor extraction

Parse `data/Seeds.xml` through `PhantomStaticManorParser`.

Explicitly forbidden:

```text
CastleManorManager.getInstance()
new CastleManorManager
```

### Class/skill extraction

Use:

```text
PlayerClass.values()
SkillTreeData.getCompleteClassSkillTree
SkillData
```

Copy immutable evidence only.

### Topology extraction

Use the current immutable `PhantomTopologyService` snapshot/query supplied
explicitly by `PhantomSystem`.

Knowledge does not own topology reload or profile registry.

## 15. Canonical source hashes

Snapshot fields:

```text
schemaVersion = 1
datasetId
datasetVersion
generation = 1
itemsHash
npcDropSpoilHash
spawnHash
recipeHash
manorHash
classCapabilityHash
contentRequirementHash
topologyHash
combinedHash
```

Rules:

- SHA-256;
- length-prefixed canonical encoding;
- IDs/numbers/enum codes only;
- no wall clock, object identity, localized name or map iteration order;
- source facts sorted by stable tuple;
- combined hash includes component names and hashes;
- the same facts in different loader/XML order yield the same hash.

The snapshot also records exact counts.

## 16. Reverse indexes

Build complete immutable indexes:

```text
itemById
npcById
dropSourcesByItem
spoilSourcesByItem
manorFactsByItem
dropFactsByNpc
spoilFactsByNpc
spawnFactsByNpc
spawnAreasByNpc
npcsByTopologyNode
npcsByMapRegion
npcsByLevel
recipeByListId
recipesByProduct
recipesByIngredient
classFactsByClassId
classesByCapability
contentById
contentByCapability
```

All lists are canonical-sorted and immutable.

Internal indexes contain all facts within policy bounds. Public pages are
bounded; do not truncate index construction.


## 17. Query API

Detailed contract: `QUERY_API.md`.

All results are immutable and deterministic.

Page request:

```text
limit 1..256
optional afterKey/cursor
```

The cursor is a stable fact key, not an opaque mutable iterator.

Required APIs equivalent to:

```java
Optional<ItemFact> findItem(int itemId)
Optional<NpcFact> findNpc(int npcId)

KnowledgePage<DropFact> dropSources(int itemId, PageRequest page)
KnowledgePage<DropFact> spoilSources(int itemId, PageRequest page)
KnowledgePage<ManorFact> manorSources(int itemId, PageRequest page)

KnowledgePage<SpawnAreaFact> spawnAreas(int npcId, PageRequest page)
KnowledgePage<SpawnFact> spawnFacts(int npcId, PageRequest page)

Optional<RecipeFact> findRecipeByListId(int recipeListId)
KnowledgePage<RecipeFact> recipesProducing(int itemId, PageRequest page)
KnowledgePage<RecipeFact> recipesUsing(int ingredientItemId, PageRequest page)

KnowledgePage<ClassCapabilityFact> classCapabilities(int classId, PageRequest page)
KnowledgePage<ClassCapabilityFact> classesForCapability(
    String capabilityKey,
    int minimumRank,
    PageRequest page)

Optional<ContentRequirementFact> content(String contentId)
KnowledgePage<ContentRequirementFact> contentsRequiring(
    String capabilityKey,
    PageRequest page)

KnowledgePage<TargetFact> suitableTargets(TargetQuery query)
KnowledgeSnapshot snapshot()
```

### Target query

Fields:

```text
minimumLevel
maximumLevel
optional preferredLevel
optional topologyNodeId
optional mapRegionLocId
allowed kinds
requireAttackable
requireTargetable
optional canBeSown
optional dropsItemId
optional spoilsItemId
limit/cursor
```

Rules:

- level range width <=100;
- uses level buckets and reverse indexes;
- no full NPC scan;
- topology-node filtering uses prebuilt spawn-area index;
- deterministic order:
  1. distance from preferred level when supplied;
  2. NPC level;
  3. NPC ID;
- this is factual filtering, not Utility AI ranking;
- no claim that the target is reachable, alive or currently spawned.

### No hot-path scans

Query methods may perform:

- direct map lookup;
- bounded page slicing;
- bounded merge over at most the requested level buckets;
- bounded filtering of already indexed candidates.

They may not:

- iterate all items/NPCs/recipes/spawns;
- access datapack files;
- access DB;
- call mutable server loaders;
- resolve localized names.

## 18. Service lifecycle

`PhantomGameKnowledgeService` states:

```text
NEW / BUILDING / RUNNING / STOPPED / FAILED
```

Goal 011 uses one startup build only.

### Start

1. validate `NEW`;
2. mark `BUILDING`;
3. build complete candidate outside service monitor;
4. validate all bounds/parity contracts;
5. atomically publish immutable snapshot/query;
6. enter `RUNNING`.

No lazy per-query build and no background rebuild.

If build fails:

- publish no partial snapshot;
- enter `FAILED`;
- expose a fixed failure category;
- PhantomSystem startup fails and performs its existing scoped cleanup.

### Query

- accepted only in `RUNNING`;
- obtains one immutable query reference;
- no lock is held during lookup.

### Stop

- no worker/in-flight mutable operation exists;
- `beginStop()` stops new query acquisition;
- `finishStop()` clears the service-owned query/snapshot and enters `STOPPED`;
- already returned immutable facts remain safe value objects;
- no scheduler, DB or file call during stop.

No explicit reload in Goal 011.

## 19. PhantomSystem integration

Enabled production startup:

```text
repository
→ materialization
→ decision
→ navigation
→ topology
→ Game Knowledge build
→ scheduler
→ RUNNING
```

Construction receives the current topology service/query explicitly.

Production build must not:

- register profiles;
- send relevance signals;
- attach decision runtimes;
- initialize `CastleManorManager`;
- initialize `DimensionalRiftManager`;
- query DB.

Non-production/inert test path uses an empty immutable knowledge snapshot unless
a focused test explicitly supplies a backend.

Disabled Phantom path performs no knowledge construction or source scan.

Shutdown:

```text
scheduler.beginStop
→ knowledge.beginStop
→ topology.beginStop
→ decision.beginStop
→ navigation.beginStop
→ materialization drain
→ scheduler.finishStop
→ knowledge.finishStop
→ topology.finishStop
→ decision.finishStop
→ navigation.finishStop
```

Knowledge cannot be the cause of an in-flight shutdown failure in Goal 011.

Do not expose a global static knowledge query API. Future handlers and Semantic
Pack receive it through explicit wiring.

## 20. Metrics and diagnostics

Fixed aggregate metrics only:

- builds started/completed/failed;
- item/NPC/drop/spoil/spawn/recipe/manor/class/content facts;
- query counts by fixed query category;
- pages returned;
- target candidates considered/returned;
- rejected queries;
- build duration as evidence;
- source parity failures.

No dynamic item/NPC/class/content metric labels.

Snapshot diagnostics include:

- state;
- schema/dataset/generation;
- component hashes;
- combined hash;
- fact/index counts;
- last fixed failure category.

No item names, coordinates list, recipe list, class IDs or source paths in the
aggregate server log.

## 21. Tests

Create:

```text
test/java/org/l2jmobius/tests/phantoms/PhantomGameKnowledgeCoreSuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomGameKnowledgeParitySuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomGameKnowledgeContentSuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomGameKnowledgePerformanceSuite.java
```

Launcher modes and Ant targets:

```text
knowledge-core
phantom-game-knowledge-core-test

knowledge-parity
phantom-game-knowledge-parity-test

knowledge-content
phantom-game-knowledge-content-test

knowledge-performance
phantom-game-knowledge-performance-smoke
```

### 21.1. Core: at least 32 cases

Cover:

- fact validation and immutability;
- policy bounds;
- canonical hash independent of input order;
- duplicate/reference rejection;
- grouped versus ungrouped chance preservation;
- exact double hashing;
- item drop/spoil reverse indexes;
- recipe/product/ingredient indexes;
- manor relationship roles;
- spawn exact/unresolved handling;
- topology/map-region aggregation;
- level/target indexes;
- deterministic page cursor;
- page size and query bounds;
- no full scan in query path through a guarded backend/index seam;
- startup atomic failure;
- disabled/inert state;
- no mutable server object exposure.

### 21.2. Authoritative parity

Initialize the real High Five loaders through the existing safe test
environment.

Exhaustively prove:

- every non-null loaded item appears exactly once;
- every loaded grouped/ungrouped drop appears exactly once;
- every loaded spoil appears exactly once;
- no reverse drop/spoil entry lacks the matching NPC fact;
- every loaded spawn is represented or explicitly unresolved;
- every recipe and ingredient relation matches `RecipeData`;
- static `Seeds.xml` facts and item references match the parser;
- no `CastleManorManager`/DB access occurred;
- topology mapping uses the accepted snapshot hash;
- component counts and canonical hashes are deterministic across two builds.

Production DB remains untouched.

### 21.3. Curated content/capability

Cover:

- strict XML schema/version;
- unknown fields and duplicate facts;
- class/evidence-skill parity;
- terminal class coverage;
- required capability-key coverage;
- no class-name inference;
- content source evidence;
- NPC/topology references;
- requirement satisfiability;
- Rift/RaidBoss/GrandBoss representative entries;
- explicit `CURATED_RECOMMENDATION` authority;
- no party-composition solver or action decision.

### 21.4. Production corpus evidence

Report exact:

- item/NPC/drop/spoil/spawn/recipe/manor counts;
- class capability count;
- content requirement count;
- selected Rift/raid/epic IDs and source paths;
- component and combined hashes;
- unresolved spawn/topology coverage.

Do not hardcode expected global counts in production code. Test may compare
repeat builds and factual loader counts.

### 21.5. Performance

Run twice with byte-identical canonical summary.

Build using real loaded data, then execute at least:

```text
100000 item source lookups
100000 recipe reverse lookups
100000 class capability lookups
100000 bounded target queries
```

Structural gates:

```text
query page <=256
no loader/file/DB access after build
no per-profile state
no thread/Future/task
all internal fact counts within policy
combined hash identical
```

Elapsed time is evidence only. Focused route timeout <=300 seconds.

### 21.6. Regressions

- topology scheduler signal integration 5/5 ×3;
- signal ledger 20/20 ×3;
- topology generation 17/17 ×3;
- topology perception/core/corpus/performance;
- navigation core/performance;
- decision core/persistence/performance;
- scheduler/materialization/shutdown;
- headless/profile/DB/harness/skeleton;
- cumulative verify/jar.

## 22. Static verifier Goal 011

Create deterministic read-only:

```text
tools/phantoms/verify-task-011.ps1
```

Verify:

- base `7575ce4c...`, one ordinary exact-scope commit;
- no config/DB schema/Goal 012/013;
- topology/navigation/decision/scheduler/materialization sources frozen except
  exact PhantomSystem integration;
- knowledge package contains no Player/Creature/mutable loader fields;
- no `CastleManorManager` or `DimensionalRiftManager` construction/access;
- one startup build and no background reload/task;
- fixed fact and policy bounds;
- authority enum and provenance;
- raw drop chance model;
- full immutable reverse indexes;
- bounded paged query API;
- no full scan/loader/file/DB in query methods;
- Seeds.xml static parser;
- class capability evidence validation;
- content requirement satisfiability;
- terminal class and required capability coverage;
- Rift/RaidBoss/GrandBoss corpus;
- canonical component/combined hashes;
- PhantomSystem start/disabled/stop integration;
- no global static query API;
- no executor/raw thread/per-profile Future;
- tests/Ant/parity/performance;
- Goal 010C closure and roadmap progress;
- UTF-8, mojibake, escaped Cyrillic;
- no credentials/binaries;
- verifier deterministic/read-only.


## 23. Exact scope

Allowed production:

```text
java/org/l2jmobius/gameserver/phantoms/knowledge/**
java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java
dist/game/data/phantoms/knowledge/**
dist/game/data/stats/npcs/29100-29199.xml
```

The NPC datapack path is a bounded authoritative correction exception: only
NPC `29181`, item `57`, `max="1100000"` to `max="11000000"` is allowed.
`min`, `chance`, group semantics and all other drop entries remain frozen.

Prefer knowledge-local metrics. A minimal `PhantomMetrics` change is allowed
only if PhantomSystem aggregate lifecycle requires it.

Allowed tests/build:

```text
build.xml
test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java
test/java/org/l2jmobius/tests/phantoms/PhantomGameKnowledgeCoreSuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomGameKnowledgeParitySuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomGameKnowledgeContentSuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomGameKnowledgePerformanceSuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomSkeletonSuite.java
tools/phantoms/verify-task-011.ps1
```

Minimal existing test changes are allowed only for PhantomSystem lifecycle
snapshot/start/stop integration.

Allowed documentation:

```text
docs/PHANTOM_BOTS_ROADMAP.md
docs/phantoms/architecture/GAME_KNOWLEDGE_CONTRACT.md
docs/phantoms/tasks/011-authoritative-game-knowledge/**
docs/phantoms/reports/010c-topology-absent-source-reconciliation.md
docs/phantoms/reports/011-authoritative-game-knowledge.md
docs/phantoms/reviews/010c-topology-absent-source-reconciliation-review.md
```

## 24. Hard out of scope

Forbidden:

- changes to ItemData, NpcData, SpawnData/Table, RecipeData, SkillTreeData,
  SkillData, CastleManorManager, DimensionalRiftManager or server loaders;
- production DB or current manor procurement/economy;
- topology XML or topology behavior changes;
- navigation, scheduler, decision or materialization behavior changes;
- Player/Creature/AI/packets;
- item-name or Russian-text lookup;
- Semantic Pack;
- Utility AI candidates/handlers/plans;
- party optimizer;
- combat kernel/Goal 012;
- commerce/equipment actions/Goal 013;
- automatic profile queries/population;
- new config key;
- DB migration/schema;
- new dependency/framework;
- new executor/raw production thread/per-profile task/Future;
- other chronicles/CI/mass formatting;
- amend/rebase/merge/force push.

## 25. Documentation

Create:

```text
docs/phantoms/architecture/GAME_KNOWLEDGE_CONTRACT.md
docs/phantoms/reports/011-authoritative-game-knowledge.md
```

Contract must document:

- authority classes;
- loader/static/curated truth boundary;
- raw drop chance limitation;
- static manor limitation;
- one-build lifecycle;
- fact/index/query bounds;
- hash/provenance;
- curated class/content recommendation semantics;
- no hot-path loader/file/DB access;
- explicit coverage limitations.

Report sections:

- status/baseline;
- Goal 010C closure;
- factual source audit;
- authority model;
- package/model/indexes;
- item/NPC/drop/spoil facts;
- spawn/topology mapping;
- recipe graph;
- static manor parser;
- class capability corpus;
- content requirement corpus;
- source hashes/counts;
- query API/pages/targets;
- startup/disabled/shutdown;
- source parity;
- production corpus;
- performance;
- metrics/diagnostics;
- all regressions;
- production DB safety;
- static verifier;
- scope/deviations/limitations;
- branch/parent/subject;
- manual gate `PENDING_INDEPENDENT_REVIEW`;
- Goal 012 `NOT_STARTED`;
- Goal 013 `NOT_STARTED`.

Use external handoff wording for self commit/push evidence.

## 26. Commands

Pre-change:

```bat
ant verify
powershell -NoProfile -ExecutionPolicy Bypass -File tools\phantoms\verify-task-010c.ps1
```

Targeted:

```bat
ant compile-tests
ant phantom-game-knowledge-core-test
ant phantom-game-knowledge-parity-test
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

Repeat:

```bat
ant phantom-game-knowledge-core-test
ant phantom-game-knowledge-core-test
ant phantom-game-knowledge-core-test

ant phantom-game-knowledge-parity-test
ant phantom-game-knowledge-parity-test

ant phantom-game-knowledge-content-test
ant phantom-game-knowledge-content-test
ant phantom-game-knowledge-content-test

ant phantom-game-knowledge-performance-smoke
ant phantom-game-knowledge-performance-smoke
```

Full:

```bat
ant verify
ant jar
powershell -NoProfile -ExecutionPolicy Bypass -File tools\phantoms\verify-task-011.ps1
git diff --check
git status --short --branch
```

Post-commit:

```bat
git rev-parse HEAD
git show --stat --oneline --decorate HEAD
git diff --check 7575ce4c66bdf5c51a27b20bed57c4ed8721b1e2...HEAD
ant verify
ant jar
powershell -NoProfile -ExecutionPolicy Bypass -File tools\phantoms\verify-task-011.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File tools\phantoms\verify-task-011.ps1
git push origin feature/phantom-world
git ls-remote --heads origin feature/phantom-world
git status --short --branch
```

Compare final verifier output byte-for-byte/SHA-256 outside the repository.

## 27. Acceptance result

Successful result:

```text
AUTHORITATIVE_GAME_KNOWLEDGE_IMPLEMENTED_PENDING_INDEPENDENT_REVIEW
```

Do not self-accept Goal 011 and do not start Goal 012/013.

## 28. Commit/push

Commit subject:

```text
feat(phantoms): add authoritative game knowledge
```

One ordinary commit on top of `7575ce4c...`.

Push regardless of SUCCESS/BLOCKED, using only safe scoped artifacts.

## 29. Blocking behavior

Return `BLOCKED` if:

- parity requires modifying existing server loaders;
- complete drop/spoil/recipe facts cannot be copied without mutable object
  exposure;
- static manor facts require CastleManorManager or production DB;
- curated class capability evidence cannot be validated;
- content requirements must be inferred from names;
- query API needs a full scan or mutable loader access;
- source facts exceed policy bounds;
- Goal 012/config/schema changes are required;
- production DB is accessed;
- cumulative verify/jar fails.

On blocker:

- remove unsafe production wiring/data;
- preserve safe audit/model/tests/report/verifier;
- ordinary commit/push;
- keep Goal 012/013 not started.

## 30. Final handoff

```text
Status:
Architecture result:
Baseline:
Goal 010C closure:
Authority model:
Item facts:
NPC facts:
Grouped drop facts:
Ungrouped drop facts:
Spoil facts:
Spawn facts/areas:
Recipe facts/ingredients:
Static manor facts:
Class intrinsic facts:
Class capability facts:
Terminal class coverage:
Content requirement facts:
Rift/raid/epic corpus:
Topology hash:
Items hash:
NPC/drop/spoil hash:
Spawn hash:
Recipe hash:
Manor hash:
Class capability hash:
Content requirement hash:
Combined knowledge hash:
Query page bound:
Drop/spoil queries:
Recipe reverse queries:
Target queries:
No hot-path scans:
No query loader/file/DB access:
Startup build:
Production automatic queries:
Core tests:
Parity tests:
Content tests:
Performance:
Two performance runs / summary SHA:
Topology/navigation/decision/scheduler/lifecycle regressions:
All prior suites:
ant verify:
ant jar:
Static verifier pre:
Static verifier final 1:
Static verifier final 2:
Outputs identical:
Production DB:
Production JAR knowledge entries:
Production JAR test entries:
Commit:
Parent:
Branch:
Push:
Remote ref:
Report:
Manual gate:
Goal 012:
Goal 013:
Coverage limitations/blockers:
```
