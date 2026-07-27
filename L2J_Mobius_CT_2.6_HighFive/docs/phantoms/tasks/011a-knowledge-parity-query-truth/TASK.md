# GOAL 011A — Game Knowledge parity and bounded query truth

## 1. Identifier

- **Task ID:** `011a-knowledge-parity-query-truth`
- **Type:** mandatory bounded safety closure for Goal 011
- **Branch:** `feature/phantom-world`
- **Starting baseline:** `dc4659fea3e76a78841dfee0429bc4ab1ed2b185`
- **Parent:** `7575ce4c66bdf5c51a27b20bed57c4ed8721b1e2`
- **Repository root:** `C:\Users\endim\L2J_Mobius\`
- **Only module:** `C:\Users\endim\L2J_Mobius\L2J_Mobius_CT_2.6_HighFive\`
- **Test DB:** `l2jmobiush5_phantom_test`
- **Production DB:** `l2jmobiush5` — never use during execution
- **Seed:** `20260725001`
- **Codex model:** Sol
- **Effort:** Very High

## 2. Independent review gate

```text
Goal 010 / 010A / 010B / 010C: ACCEPT
Goal 011 architecture direction: ACCEPT
Goal 011 commit: FIX_REQUIRED
Revert: NOT_REQUIRED
Goal 011A: REQUIRED
Goal 012: BLOCKED
Goal 013: NOT_STARTED
```

Keep all accepted Goal 011 work:

- immutable authority-tagged Game Knowledge snapshot;
- item/NPC/spawn/recipe/static-manor/class/content facts;
- complete reverse indexes;
- one startup build before scheduler;
- no production DB;
- strict curated XML;
- fixed source hashes;
- one-line Zaken datapack correction;
- no Semantic Pack, actions, movement, party solver or automatic queries.

This task closes only factual loader parity, target-filter truth, and public query
response bounds.

## 3. Independent findings

### P1 — runtime drop order is destroyed

`NpcData` and `NpcTemplate` use ordered lists with mechanical meaning:

- grouped items are sorted by chance and consumed in list order;
- cumulative `totalChance` is updated in that order;
- successful x1 group processing breaks the current group;
- one drop occurrence counter is shared across groups;
- ungrouped death/spoil lists also consume an occurrence counter in list order.

Current `L2jGameKnowledgeBackend`:

```text
sorts grouped holders by item ID
sorts groups by group chance/content
sorts ungrouped death/spoil holders by item ID
```

It then labels the new positions `groupOrdinal` and `itemOrdinal`.

This changes authoritative runtime semantics.

Concrete High Five example:

```text
NPC 29181 Zaken
first loaded drop group:
- item 13143 chance 5.92
- items 13144... chance 5.88

NpcData runtime group sorting places 5.88 entries before 5.92.
Knowledge item-ID sorting places 13143 first.
```

The knowledge snapshot therefore does not preserve the runtime group sequence.

### P1 — target filters conflate “not requested” and “requested but empty”

Current `suitableTargets()` maps a missing index list to `null`.

`null` is also used for “the caller did not request this filter”.

Consequences:

```text
unknown topology node       -> all level-matching targets
unknown map region          -> all level-matching targets
item with no death drops    -> all level-matching targets
item with no spoil sources  -> all level-matching targets
empty filter intersection   -> unrelated targets
```

A bot asking where to farm an unavailable item can be directed to arbitrary
mobs.

### P2 — parity suite compares the adapter with itself

The production fixture:

1. builds the snapshot through `L2jGameKnowledgeBackend`;
2. calls the same backend again;
3. compares backend facts to snapshot facts.

This proves adapter→snapshot consistency, but cannot detect adapter omissions,
reordering or reinterpretation of authoritative loaders.

The drop-order defect passes this parity gate for exactly that reason.

### P2 — recipe enumeration can silently lose ambiguous entries

`RecipeData.getAllItemIds()` returns one recipe-item ID per loaded recipe.
`getRecipeByItemId()` returns the first matching recipe.

If two loaded recipes share a recipe-item ID, the current backend resolves both
array entries to the same recipe, deduplicates by list ID, and silently omits the
other recipe.

Goal 011 must fail closed on ambiguity and prove loaded recipe count parity.

### P2 — public query result bounds can be bypassed through nested areas

`spawnAreas()` returns paged `SpawnAreaFact`, but each fact embeds up to 256
representative spawn points. A page of 256 areas can expose up to 65,536 nested
samples.

`TargetFact` embeds the complete unpaged `spawnAreasByNpc` list. The policy field
`maximumTopologyNodeResults=64` is not applied.

A single target page can therefore bypass the intended bounded response
contract.

### P2 — lifecycle diagnostics omit component hashes

The immutable snapshot owns all component hashes, but
`PhantomGameKnowledgeService.ServiceSnapshot` exposes only `combinedHash`.

The Goal 011 contract requires bounded component-hash diagnostics as well.

## 4. Goal

Implement and prove:

1. grouped drop group order exactly matches `NpcTemplate.getDropGroups()`;
2. grouped item order exactly matches each loaded
   `DropGroupHolder.getDropList()`;
3. ungrouped death and spoil order exactly match the loaded lists;
4. ordinals are explicitly runtime/source ordinals, not canonicalized content
   positions;
5. canonical hashing remains deterministic by sorting facts on explicit
   runtime ordinals;
6. changing only outer collection order does not change hashes;
7. changing an authoritative drop ordinal does change the drop hash;
8. requested target filters with zero matches return an empty page;
9. all filter intersections are exact;
10. public area/target responses cannot exceed nested policy bounds;
11. parity tests independently reconstruct facts from actual server loaders;
12. recipe ambiguity cannot silently omit a loaded recipe;
13. service diagnostics expose component and combined hashes;
14. Zaken correction and all accepted Goal 011 facts remain;
15. Goal 012/013 remain not started;
16. all cumulative regressions remain GREEN.

## 5. Mandatory reading

Read fully:

- roadmap, master plan, `Agents.md`, workflow/package/report standards;
- Goal 011 package/report/contract and Goal 010C review;
- `NpcData`, `NpcTemplate`, `DropGroupHolder`, `DropHolder`;
- `ItemData`, `SpawnTable`, `Spawn`, `RecipeData`, `RecipeList`;
- all knowledge production code and tests;
- all files in this package.

Do not read or modify another chronicle.

## 6. Initial Git audit

```bat
git rev-parse --show-toplevel
git status --short --branch
git remote -v
git fetch origin --prune
git rev-parse HEAD
git rev-parse origin/feature/phantom-world
git show --stat --oneline dc4659fea3e76a78841dfee0429bc4ab1ed2b185
git diff --name-status 7575ce4c66bdf5c51a27b20bed57c4ed8721b1e2..dc4659fea3e76a78841dfee0429bc4ab1ed2b185
```

Expected:

```text
HEAD == origin/feature/phantom-world == dc4659fe...
```

The extracted Goal 011A package is expected untracked. Preserve unrelated
`docs/agent-tasks/**`. Return `BLOCKED_BASELINE_DRIFT` for unreviewed
production/config/schema drift.

## 7. Runtime drop-order contract

### 7.1. Grouped death drops

For each NPC template:

```text
groupOrdinal = index in template.getDropGroups()
itemOrdinal  = index in group.getDropList()
```

Do not sort either list before assigning ordinals.

Copy each holder exactly:

```text
item ID
min/max
raw item chance
raw group chance
source kind
chance model
runtime group/item ordinal
```

The snapshot may sort the resulting immutable facts by:

```text
npcId
sourceKind
groupOrdinal
itemOrdinal
itemId
```

because the runtime ordinals already carry source sequence truth.

### 7.2. Ungrouped death and spoil

For each loaded list:

```text
groupOrdinal = -1
itemOrdinal  = exact list index
```

Do not sort by item ID/chance before assigning the ordinal.

### 7.3. Hash truth

`npcDropSpoilHash` includes the exact runtime ordinals.

Required tests:

- shuffled outer fact collection with unchanged ordinals → same hash;
- swapped runtime item ordinals → different hash;
- swapped runtime group ordinals → different hash;
- Zaken loaded first group order matches `NpcTemplate` exactly;
- at least one known group demonstrates knowledge order differs from item-ID
  order and still matches runtime order.

Update terminology from “canonical ordinal” to “runtime/source ordinal”.

## 8. Exact target-filter semantics

Represent each optional filter as two states:

```text
not requested
requested with exact candidate set, which may be empty
```

For:

```text
topologyNodeId
mapRegionLocId
dropsItemId
spoilsItemId
```

Rules:

- field is null → no filter;
- field supplied and index absent → empty candidate set;
- any requested empty set → immediate empty page;
- intersection of requested sets is exact;
- no filter may silently widen the query;
- target metrics report zero returned and bounded considered count.

Mandatory cases:

- unknown topology node;
- known topology node with zero indexed attackable NPCs;
- unknown map region;
- valid item with no death-drop sources;
- unknown item ID;
- valid item with no spoil sources;
- individually nonempty filters with empty intersection;
- empty result with an arbitrary cursor remains empty.

## 9. Bounded public area views

Keep complete internal spawn facts/indexes.

Add a lightweight immutable query view equivalent to:

```text
SpawnAreaSummary:
npcId
instanceId
topologyNodeId
mapRegionLocId
spawnCount
totalConfiguredAmount
authority
```

It contains no representative point list.

Change public query behavior:

```text
spawnAreas(npcId, page)
→ KnowledgePage<SpawnAreaSummary>
```

Exact spawn points remain available only through:

```text
spawnFacts(npcId, page)
```

which already has the page-size bound.

Change `TargetFact` to a bounded form equivalent to:

```text
npc
totalSpawnAreaCount
representativeAreas      <= maximumTopologyNodeResults
hasMoreSpawnAreas
```

`representativeAreas` uses lightweight summaries, never nested points.

Rules:

- target page <= maximumQueryPageSize;
- each target contains <= maximumTopologyNodeResults summaries;
- no public result contains more than maximumQueryPageSize exact spawn facts;
- no full area list is copied into a target result;
- ordering is deterministic;
- cursor identity remains based on target ordering, not area count.

Do not truncate internal authoritative indexes.

## 10. Independent loader parity

Rewrite real parity so expected facts are reconstructed independently from the
actual loaders, not from a second call to the knowledge backend.

### Items

Iterate non-null `ItemData.getAllItems()` and compare exact factual fields/count.

### NPCs

Iterate `NpcData.getTemplates(_ -> true)` and compare exact mechanical
fields/count.

### Drops/spoil

For every `NpcTemplate`, independently walk:

```text
getDropGroups()
each group.getDropList()
getDropList()
getSpoilList()
```

Compare exact facts, counts and runtime ordinals.

The expected reconstruction must not use production backend helper methods.

### Spawns

Independently walk `SpawnTable.getSpawnTable()` and prove every loaded spawn is
represented exactly once or explicitly unresolved according to the documented
classification.

### Recipes

Use the public loader APIs and assert:

```text
snapshot recipe count == RecipeData.getAllItemIds().length
every recipe-item ID resolves
every resolved recipe list ID is unique
every loaded recipe factual field/ingredient matches
```

Production backend must:

- detect duplicate recipe-item IDs;
- reject lookup/list-count ambiguity;
- never `continue` after silently resolving two source entries to one list.

### Static manor

Keep the dedicated parser and validate selected raw XML facts plus complete
count/reference parity without using `CastleManorManager`.

### Query source seam

Ordinary queries still perform zero backend/file/DB access.

## 11. Component hash diagnostics

Add an immutable fixed hashes record equivalent to:

```text
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

Expose it in `PhantomGameKnowledgeService.ServiceSnapshot`.

Inactive/failed states use fixed `"none"` values.

Do not expose raw facts, IDs or source paths in lifecycle diagnostics.

## 12. Tests

Extend:

```text
PhantomGameKnowledgeCoreSuite
PhantomGameKnowledgeParitySuite
PhantomGameKnowledgePerformanceSuite
PhantomSkeletonSuite
```

Add a focused query truth suite if clearer:

```text
PhantomGameKnowledgeQueryTruthSuite
launcher: knowledge-query-truth
Ant: phantom-game-knowledge-query-truth-test
```

Minimum executable additions:

1. runtime grouped item order;
2. runtime group order;
3. runtime ungrouped death order;
4. runtime spoil order;
5. outer collection shuffle hash stable;
6. ordinal swap changes hash;
7. Zaken known order regression;
8. independent direct-loader drop parity;
9. independent item/NPC parity;
10. independent spawn parity;
11. recipe loader count parity;
12. duplicate recipe-item ambiguity fails closed;
13. missing topology filter returns empty;
14. missing map-region filter returns empty;
15. no-drop item filter returns empty;
16. no-spoil item filter returns empty;
17. empty intersection returns empty;
18. target area summaries <=64;
19. spawn-area page has no nested points;
20. exact spawn facts page <=256;
21. service snapshot exposes all hashes.

Required runs:

- core >=48 ×3;
- parity >=20 ×2;
- content 18/18 ×3;
- query truth, if separate, ×3;
- performance 8/8 ×2 with deterministic summary;
- all Goal 001–011 cumulative routes;
- verify/jar.

## 13. Exact scope

Allowed production:

```text
java/org/l2jmobius/gameserver/phantoms/knowledge/L2jGameKnowledgeBackend.java
java/org/l2jmobius/gameserver/phantoms/knowledge/PhantomGameKnowledgeModel.java
java/org/l2jmobius/gameserver/phantoms/knowledge/PhantomGameKnowledgeBuilder.java
java/org/l2jmobius/gameserver/phantoms/knowledge/PhantomGameKnowledgeSnapshot.java
java/org/l2jmobius/gameserver/phantoms/knowledge/PhantomGameKnowledgeQuery.java
java/org/l2jmobius/gameserver/phantoms/knowledge/PhantomGameKnowledgeService.java
java/org/l2jmobius/gameserver/phantoms/knowledge/PhantomGameKnowledgeMetrics.java
```

One small immutable query-view class in the knowledge package is allowed.

Allowed tests/build:

```text
build.xml
test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java
test/java/org/l2jmobius/tests/phantoms/PhantomGameKnowledgeCoreSuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomGameKnowledgeParitySuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomGameKnowledgeQueryTruthSuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomGameKnowledgePerformanceSuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomSkeletonSuite.java
tools/phantoms/verify-task-011a.ps1
```

Minimal content-suite compile changes are allowed only if the immutable model
signature changes.

Allowed documentation:

```text
docs/PHANTOM_BOTS_ROADMAP.md
docs/phantoms/architecture/GAME_KNOWLEDGE_CONTRACT.md
docs/phantoms/tasks/011a-knowledge-parity-query-truth/**
docs/phantoms/reports/011-authoritative-game-knowledge.md
docs/phantoms/reports/011a-knowledge-parity-query-truth.md
docs/phantoms/reviews/011-authoritative-game-knowledge-review.md
```

## 14. Frozen scope

Do not change:

- Zaken datapack fix or any other datapack file;
- curated knowledge XML;
- ItemData/NpcData/SpawnTable/RecipeData or any server loader;
- PhantomSystem lifecycle;
- topology/navigation/decision/scheduler/materialization;
- config or DB schema;
- Goal 012/013.

## 15. Hard out of scope

Forbidden:

- additional datapack correction;
- loader behavior changes;
- effective runtime drop probability calculation;
- server rate/premium/champion logic;
- Semantic Pack or names;
- combat/actions/party optimizer;
- new executor/raw thread/per-profile task;
- production DB;
- other chronicles/dependencies/CI/mass formatting;
- amend/rebase/merge/force push.

## 16. Static verifier

Create deterministic read-only:

```text
tools/phantoms/verify-task-011a.ps1
```

Verify:

- base `dc4659fe...`, one ordinary exact-scope commit;
- Zaken XML unchanged from accepted corrected value;
- no curated/config/schema/Goal 012/013 changes;
- server loaders and PhantomSystem frozen;
- no holder/group sorting before runtime ordinal assignment;
- exact list-index ordinals for grouped/ungrouped/spoil;
- drop hash includes ordinals;
- requested-empty target filters cannot become null/no-filter;
- lightweight spawn-area public view;
- target nested areas capped by policy;
- no representative points in public area summaries;
- independent loader parity tests do not call backend for expected facts;
- recipe count/ambiguity checks;
- component hashes in service snapshot;
- no query loader/file/DB access;
- no executor/thread/Future;
- tests/Ant/performance/docs/encoding/credentials/binaries.

## 17. Documentation

Create:

```text
docs/phantoms/reviews/011-authoritative-game-knowledge-review.md
docs/phantoms/reports/011a-knowledge-parity-query-truth.md
```

Update Goal 011 report with immutable handoff:

```text
Commit: dc4659fea3e76a78841dfee0429bc4ab1ed2b185
Parent: 7575ce4c66bdf5c51a27b20bed57c4ed8721b1e2
Push/remote: exact
Core: 42/42 ×3
Parity: 14/14 ×2
Content: 18/18 ×3
Performance: 8/8 ×2
Verifier: 154/154 ×2, byte-identical
Verifier SHA-256:
73856A59AB07A1DA3DCCBD7538F60E6097CCD39FDC34AF2939026A2BAA0F27A0
Independent review:
- architecture ACCEPT
- source-order/query-truth FIX_REQUIRED
Goal 011A: REQUIRED
Goal 012: BLOCKED
```

Review verdict:

```text
Goal 011 architecture direction: ACCEPT
Goal 011 commit: FIX_REQUIRED
Revert: NOT_REQUIRED
Goal 011A: REQUIRED
Goal 012: BLOCKED
Goal 013: NOT_STARTED
```

Roadmap progress only:

```text
Goal 011: FIX_REQUIRED
Goal 011A: IMPLEMENTED_PENDING_INDEPENDENT_REVIEW
Goal 012: NOT_STARTED / BLOCKED
Goal 013: NOT_STARTED
```

## 18. Commands

Pre-change:

```bat
ant verify
powershell -NoProfile -ExecutionPolicy Bypass -File tools\phantoms\verify-task-011.ps1
```

Targeted:

```bat
ant compile-tests
ant phantom-game-knowledge-core-test
ant phantom-game-knowledge-parity-test
ant phantom-game-knowledge-content-test
ant phantom-game-knowledge-query-truth-test
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

Repeat focused suites according to §12.

Final:

```bat
ant verify
ant jar
powershell -NoProfile -ExecutionPolicy Bypass -File tools\phantoms\verify-task-011a.ps1
git diff --check
```

Post-commit run verify/jar/verifier ×2, push and confirm remote exact.

## 19. Result and commit

Successful result:

```text
GAME_KNOWLEDGE_PARITY_QUERY_TRUTH_HARDENED_PENDING_INDEPENDENT_REVIEW
```

Commit subject:

```text
fix(phantoms): harden game knowledge parity and queries
```

One ordinary commit on top of `dc4659fe...`.

Push regardless of SUCCESS/BLOCKED, using only safe scoped artifacts.

## 20. Blocking behavior

Return `BLOCKED` if:

- exact runtime drop order cannot be copied without loader changes;
- independent recipe parity reveals source ambiguity that cannot fail closed;
- bounded target/area views require a loader or topology behavior change;
- Goal 012/config/schema changes are required;
- production DB is accessed;
- cumulative verify/jar fails.

On blocker remove unsafe edits, preserve safe evidence, commit/push and keep
Goal 012 blocked.

## 21. Final handoff

```text
Status:
Architecture result:
Baseline:
Goal 011 review:
Grouped runtime order:
Ungrouped death order:
Spoil order:
Zaken order regression:
Outer shuffle hash:
Ordinal-change hash:
Independent item/NPC parity:
Independent drop/spoil parity:
Independent spawn parity:
Recipe count parity:
Recipe ambiguity guard:
Unknown topology filter:
Unknown map filter:
No-drop filter:
No-spoil filter:
Empty intersection:
Spawn area public view:
Target nested-area cap:
Exact spawn page cap:
Service component hashes:
Core:
Parity:
Content:
Query truth:
Performance:
All regressions:
ant verify:
ant jar:
Verifier final 1/final 2/identical/SHA:
Production DB:
JAR knowledge/test entries:
Commit/parent/branch/push/remote:
Report:
Manual gate:
Goal 012:
Goal 013:
Limitations/blockers:
```
