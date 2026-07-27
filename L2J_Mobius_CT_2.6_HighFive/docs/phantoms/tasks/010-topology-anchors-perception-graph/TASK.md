# GOAL 010 — Topology, anchors and perception graph

## 1. Identifier

- **Goal ID:** `010-topology-anchors-perception-graph`
- **Roadmap stage:** II — Scheduler, goals, navigation and authoritative knowledge
- **Branch:** `feature/phantom-world`
- **Accepted baseline:** `0780c77ae605d8b2c36a4ff0345092506fb9f9c5`
- **Parent:** `b6e893f6bb8abf26908e441ee79b92d6f910eb91`
- **Git root:** `C:\Users\endim\L2J_Mobius\`
- **Only module:** `C:\Users\endim\L2J_Mobius\L2J_Mobius_CT_2.6_HighFive\`
- **Test DB:** `l2jmobiush5_phantom_test`
- **Production DB:** `l2jmobiush5` — never use during execution
- **Seed:** `20260725001`
- **Codex model:** Sol
- **Effort:** Very High

## 2. Accepted gates

```text
Stage I: COMPLETE
Goal 007 / 007A: ACCEPT
Goal 008 / 008A: ACCEPT
Goal 009: ACCEPT after Goal 009A
Goal 009A: ACCEPT
Goal 010: ALLOWED
Goal 011: NOT_STARTED
Goal 012: NOT_STARTED
```

Goal 009A accepted facts:

```text
Commit: 0780c77ae605d8b2c36a4ff0345092506fb9f9c5
Parent: b6e893f6bb8abf26908e441ee79b92d6f910eb91
Remote: exact
Navigation core: 50/50 ×3
Navigation performance: 1/1 ×2
Shutdown handoff: 7/7 ×3
Verifier: 56/56 ×2, byte-identical
Independent verdict: ACCEPT
```

Committed Goal 009A evidence:

```text
Pre-commit verifier SHA-256:
0CC17166F2E296CC46F4FD12E74AEB8329158CD1379E4DEB65F22AF878FDE397

Performance SHA-256:
0F7391E50732132B86F931C62120344281203B05FDFE169AB51BE80A7BC148F3
```

The external final handoff did not retain a full post-push verifier hash. Do not
invent it.

## 3. Result

After Goal 010 the Phantom subsystem has:

- immutable versioned server-world topology;
- stable region, area, room and anchor IDs;
- city, shop, Gatekeeper, farming and route anchor roles;
- walk, passage, door, teleport and background-travel edges;
- live door-state overlay;
- bounded spatial and adjacency indexes;
- deterministic topology queries;
- explicitly registered profile positions;
- event-driven local-chat, combat and targetability perception providers;
- scheduler `RelevanceSignal` delivery through a narrow port;
- a hard invariant that a perceptible neighbor receives at least
  `NEARBY_PERCEPTIBLE`;
- bounded source hashes, validation evidence, snapshots and metrics.

Production remains inert:

```text
registered topology profiles = 0
events in flight = 0
automatic profile discovery = 0
automatic scheduler registration = 0
automatic movement = 0
automatic decision actions = 0
```

Goal 010 does not build Game Knowledge indexes and does not parse natural
language.

## 4. Truth model

Do not infer semantic meaning from NPC names, titles or coordinate heuristics.

Separate:

### Server facts

Validate through existing High Five loaders:

- `MapRegionData.getMapRegionLocId(x, y)`;
- `NpcData.getTemplate(npcId)`;
- `SpawnTable.getSpawns(npcId)`;
- `DoorData.getDoor(doorId)`;
- world coordinate/instance bounds;
- live door state when querying dynamic passage truth.

### Curated semantic topology

Versioned XML declares:

- node and anchor IDs;
- roles such as CITY, SHOP, GATEKEEPER, FARMING and ROOM;
- room bounds and hierarchy;
- adjacency and travel edge meaning;
- door association;
- perception-channel connectivity;
- source references/evidence paths.

The curated file is authoritative for semantic roles, but every referenced
server entity and coordinate must pass factual validation.

No role is inferred from localized display text.

## 5. Mandatory reading

Read fully:

1. roadmap, master plan, `Agents.md`, workflow/package/report standards;
2. Goals 007–009A packages/reports/reviews/contracts;
3. current:
   - scheduler and `PhantomRelevanceSignal`;
   - navigation service;
   - `PhantomSystem` and shutdown handoff;
   - `MapRegionData`;
   - `NpcData`, `NpcTemplate`;
   - `SpawnData`, `SpawnTable`, `Spawn`;
   - `DoorData`, `Door`;
   - `World`, `WorldRegion`, `ZoneManager`;
   - current build/tests;
4. relevant datapack sources selected for the production seed;
5. every file in this package.

Do not read or modify another chronicle.

## 6. Initial Git audit

```bat
git rev-parse --show-toplevel
git status --short --branch
git remote -v
git fetch origin --prune
git rev-parse HEAD
git rev-parse origin/feature/phantom-world
git show --stat --oneline 0780c77ae605d8b2c36a4ff0345092506fb9f9c5
git diff --name-status b6e893f6bb8abf26908e441ee79b92d6f910eb91..0780c77ae605d8b2c36a4ff0345092506fb9f9c5
```

Expected:

```text
HEAD == origin/feature/phantom-world == 0780c77a...
```

The extracted Goal 010 package is expected untracked. Preserve unrelated
`docs/agent-tasks/**`. Return `BLOCKED_BASELINE_DRIFT` for unreviewed
production/config/schema drift.

## 7. Close Goal 009A

Update:

```text
docs/phantoms/reports/009a-navigation-route-ownership-hardening.md
```

Add immutable handoff:

```text
Commit: 0780c77ae605d8b2c36a4ff0345092506fb9f9c5
Parent: b6e893f6bb8abf26908e441ee79b92d6f910eb91
Push/remote: exact
Navigation core: 50/50 ×3
Navigation performance: 1/1 ×2
Shutdown handoff: 7/7 ×3
Final verifier: 56/56 ×2, byte-identical
Independent review: ACCEPT
Goal 010: ALLOWED
```

Create:

```text
docs/phantoms/reviews/009a-navigation-route-ownership-hardening-review.md
```

Verdict:

```text
Goal 009: ACCEPT after Goal 009A
Goal 009A: ACCEPT
Revert: NOT_REQUIRED
Goal 010: ALLOWED
Goal 011: NOT_STARTED
```

Roadmap progress only:

- accepted baseline becomes `0780c77a...`;
- Goal 009/009A become `ACCEPT`;
- Goal 010 becomes `IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`;
- Goal 011/012 remain `NOT_STARTED`;
- do not rewrite future Goal architecture.

## 8. Production package and data

Create:

```text
java/org/l2jmobius/gameserver/phantoms/topology/**
dist/game/data/phantoms/topology/*.xml
```

Responsibility-equivalent types:

```text
PhantomTopologyService
PhantomTopologyLoader
PhantomTopologyValidationBackend
L2jTopologyValidationBackend
PhantomTopologySnapshot
PhantomTopologyNode
PhantomTopologyArea
PhantomTopologyAnchor
PhantomTopologyEdge
PhantomTopologyQuery
PhantomTopologyProfileRegistry
PhantomPerceptionProvider
PhantomRelevanceSignalPort
PhantomSchedulerRelevanceSignalPort
PhantomTopologyPolicy
```

Small enums/value records are allowed.

No topology value object stores or exposes `Player`, `Creature`, `Npc`, `Door`,
`Spawn`, `WorldObject`, packet, AI, mutable loader collection or XML Node.

## 9. Versioned topology XML

Root equivalent:

```xml
<topology schemaVersion="1"
          datasetId="high-five-core"
          datasetVersion="1">
```

Identifiers:

```text
^[a-z][a-z0-9_.-]{0,95}$
```

Bounds:

```text
files                         <= 64
nodes                         <= 100000
anchors                       <= 100000
edges                         <= 200000
hierarchy depth               <= 8
tags/entity                   <= 16
source references/entity      <= 8
node area vertices            <= 32
```

Supported area forms:

```text
POINT_RADIUS
CUBOID
POLYGON
```

Polygon must be simple, non-self-intersecting and contain 3..32 points.
Z bounds are mandatory for CUBOID/POLYGON.

Node kinds:

```text
WORLD_REGION
CITY
SETTLEMENT
OUTDOOR_AREA
DUNGEON
CATACOMB
ROOM
CORRIDOR
SHOP_AREA
FARMING_AREA
ROUTE_AREA
```

Anchor roles:

```text
CITY_CENTER
RESPAWN
SHOP
WAREHOUSE
GATEKEEPER
FARMING
ROUTE
ROOM_CENTER
DOOR_SIDE
INSTANCE_ENTRY
INSTANCE_EXIT
```

Edge modes:

```text
WALK
PASSAGE
DOOR
GATEKEEPER
TELEPORT
BACKGROUND
```

Perception channels:

```text
LOCAL_CHAT
COMBAT
TARGETABILITY
```

Every entity is immutable and canonical-sorted. XML order must not change the
canonical snapshot hash.

No localized name is used as identity. Optional display/resource keys are
metadata only.

## 10. Entity contracts

### Node

Fields equivalent to:

```text
id
kind
instanceId
area
optional parentId
tags
sourceRefs
```

Rules:

- one parent maximum;
- no hierarchy cycles;
- child instance equals parent instance;
- child representative point must lie inside parent;
- room/corridor nodes require DUNGEON/CATACOMB/ROOM parent chain;
- all coordinates inside World bounds.

### Anchor

Fields equivalent to:

```text
id
role
nodeId
point
optional npcId
optional mapRegionLocId
tags
sourceRefs
```

Rules:

- point must be inside referenced node;
- NPC anchor requires existing template and at least one matching spawn within
  a declared validation tolerance `0..500`;
- map-region claim must equal
  `MapRegionData.getMapRegionLocId(point.x, point.y)`;
- GATEKEEPER/SHOP/WAREHOUSE semantic role requires explicit source evidence,
  not template-name inference;
- FARMING anchor may reference one or more factual Monster NPC IDs/spawns;
- no item/drop/spoil knowledge is stored.

### Edge

Fields equivalent to:

```text
id
fromNodeId
toNodeId
mode
bidirectional
baseCost
baseTravelMillis
backgroundEligible
perceptionChannels
optional doorId
optional fromAnchorId
optional toAnchorId
sourceRefs
```

Rules:

- no dangling node/anchor refs;
- self-edge rejected;
- instance IDs compatible unless TELEPORT/GATEKEEPER/BACKGROUND explicitly
  declares a cross-instance transition;
- DOOR mode requires a factual door ID;
- door-side anchors must be within bounded distance of the factual door geometry;
- WALK/PASSAGE/DOOR edges require endpoint anchors;
- `backgroundEligible` is metadata only; no travel simulation occurs;
- duplicate semantic edge keys rejected.

## 11. Production seed corpus

Commit a small real High Five topology seed selected from existing datapack
facts.

Minimum evidence coverage, not hardcoded product counts:

1. one real map-region/city cluster;
2. one factual SHOP or WAREHOUSE NPC anchor;
3. one factual GATEKEEPER anchor;
4. one factual outdoor FARMING anchor with at least one Monster spawn reference;
5. two curated ROOM/CORRIDOR nodes connected by one factual DOOR passage;
6. one ROUTE anchor and one BACKGROUND-eligible edge;
7. perception connectivity for LOCAL_CHAT, COMBAT and TARGETABILITY.

For every entity record exact:

```text
source datapack path
NPC/door/map-region identifier where applicable
validation tolerance
```

Do not invent NPC IDs, door IDs, spawn coordinates or source paths.

If a factual room/door corpus cannot be proven from current High Five datapack,
return `BLOCKED_TOPOLOGY_CORPUS`; do not fabricate it. Safe loader/query/provider
code and test corpus may still be committed/pushed, but Goal 011 stays blocked.

The production seed is representative, not a claim of full-map completeness.
Report the exact covered area and explicit omissions.

## 12. Loader, canonical hash and reload boundary

`PhantomTopologyLoader`:

- reads only `data/phantoms/topology`;
- deterministic file order;
- rejects unknown schema/version/element/attribute;
- strict counts and string lengths before allocation;
- rejects duplicates/dangling refs/cycles/invalid geometry;
- performs factual validation through the injected backend;
- creates one immutable snapshot;
- canonical SHA-256 over sorted semantic content and source evidence;
- never exposes parser structures.

`PhantomTopologyService.start()` loads exactly once.

Explicit reload API is allowed only in tests/admin-neutral service API:

```text
ReloadResult reload()
```

Reload:

- builds a complete candidate snapshot outside service monitor;
- atomically swaps only after full validation;
- increments topology generation;
- invalid reload leaves previous snapshot active;
- clears profile node resolution only when required by changed generation;
- no background file watcher or periodic reload.

Production starts without a reload task.

## 13. Query indexes

Build bounded immutable indexes:

```text
nodeById
anchorById
edgeById
childrenByParent
edgesByNode
anchorsByNode
anchorsByRole
spatial grid/buckets
```

Required APIs equivalent to:

```java
Optional<Node> findNode(String id)
Optional<Anchor> findAnchor(String id)
List<Node> locate(TopologyPoint point)
List<Anchor> nearestAnchors(
    TopologyPoint point,
    AnchorRole role,
    int limit,
    int maximumDistance)
List<EdgeView> edges(String nodeId)
boolean isTraversable(String edgeId)
boolean isPerceptible(String edgeId, PerceptionChannel channel)
Optional<RouteHint> routeHint(String fromAnchorId, String toAnchorId)
TopologySnapshot snapshot()
```

Bounds:

```text
nearest limit      1..64
query radius       <=100000
returned edges     <=1024
returned nodes     <=64
graph BFS nodes    <=256/event
```

Dynamic door truth is queried at call time through a narrow backend:

```text
missing/dead/open door → traversable according to factual door state
closed live door       → not traversable
```

Do not cache live door open/closed state in the immutable topology snapshot.

No A* duplication. Route hints may identify anchors/edges but local path
calculation stays in `PhantomNavigationService`.

## 14. Profile position registry

Explicit API equivalent to:

```java
RegistrationResult register(long profileId)
UpdateResult update(
    long profileId,
    TopologyPoint point,
    long sequence)
UnregisterResult unregister(long profileId)
Optional<ProfileTopologySnapshot> find(long profileId)
List<ProfileTopologySnapshot> list()
```

Rules:

- no automatic profile discovery;
- capacity equals scheduler profile capacity, injected at construction;
- monotonic sequence per profile;
- stale update rejected;
- point resolves deterministically to the most specific containing node:
  deepest hierarchy, then smallest area, then ID ascending;
- unresolved point is allowed and explicitly represented;
- spatial bucket membership updated atomically;
- no Player reference;
- no timer/task/Future/profile worker;
- all lists immutable and bounded.

## 15. Relevance signal port

Topology must not call scheduler internals directly.

Port equivalent to:

```java
SignalDelivery submit(long profileId, PhantomRelevanceSignal signal)
SignalDelivery withdraw(long profileId, String sourceKey, long sequence)
```

Production adapter delegates only to:

```text
PhantomScheduler.submitSignal
PhantomScheduler.withdrawSignal
```

It does not register/unregister scheduler profiles.

Delivery statuses are mapped explicitly. Backpressure or NOT_REGISTERED affects
only the recipient and does not abort the event fanout.

## 16. Perception events

Typed immutable events:

```text
LocalChatEvent
CombatEvent
TargetabilityEvent
```

No message text is stored or passed.

Fields are bounded and equivalent to:

### Local chat

```text
eventId
source point/node
logical time
radius
ttl
```

### Combat

```text
eventId
source point/node
participant profile IDs, max 32
logical time
radius
ttl
```

### Targetability

```text
eventId
observer profile ID
target profile ID
active/inactive
logical time
ttl
```

No chat parser, packet listener or combat listener is added in Goal 010.
Future subsystems call providers explicitly.

## 17. Perception graph semantics

Profile recipients come only from the explicit topology profile registry.

### Same node

A profile in the event node is perceptible.

### Neighbor node

A profile in a directly connected node is perceptible only when:

- the edge includes the event channel;
- dynamic passage state allows perception;
- a closed door does not permit that channel;
- the event radius and bounded point distance allow it.

Graph traversal is limited to the event node plus one edge in Goal 010.
Do not perform unbounded BFS.

### Signals

Use fixed source keys:

```text
topology.local_chat
topology.combat
topology.targetability
```

Provider-owned monotonic sequence per source/profile.

Required states:

```text
local-chat recipient              NEARBY_PERCEPTIBLE
combat participant                ACTIVE
combat perceptible neighbor       NEARBY_PERCEPTIBLE
active targetability target       ACTIVE
inactive targetability            withdraw signal
```

Hard gate:

```text
a profile that is perceptible by same-node or allowed neighbor topology
must never receive WARM, BACKGROUND or SLEEPING from that event
```

The scheduler may coalesce/hysteresis normally after receiving the minimum
signal.

No provider calls materialization directly.

## 18. Event fanout and stop ownership

Perception processing is synchronous on the caller thread, but bounded.

Policy defaults, no new config:

```text
maximum registered profiles       10000
maximum concurrent events         32
maximum recipients/event          1024
maximum neighbor nodes/event       64
maximum event radius               100000
default local chat TTL             5000 ms
default combat TTL                 3000 ms
default targetability TTL          2000 ms
```

Service state:

```text
NEW / RUNNING / STOPPING / STOPPED
```

Protocol:

1. under monitor validate state/event and claim one event token/generation;
2. capture immutable topology/profile candidates;
3. release monitor;
4. evaluate bounded perception and deliver signals;
5. re-enter monitor and release exact token.

`beginStop()`:

- enters STOPPING;
- rejects new profile/event operations;
- cancels generation;
- no new scheduler delivery starts afterward.

`finishStop()` returns false while any event token is in flight. After
quiescence it clears profile/event registries and becomes STOPPED.

No new executor/thread/task. No blocking wait under monitor.

## 19. PhantomSystem integration

Enabled startup:

```text
repository
→ materialization
→ decision engine
→ navigation service
→ topology service/load/validate
→ scheduler start
→ RUNNING
```

The scheduler adapter may be constructed before scheduler start, but production
has zero topology profiles/events and therefore sends no signal during startup.

Disabled path:

```text
no topology loader/service/data scan
no navigation service
no DB
```

Shutdown:

```text
scheduler.beginStop
→ topology.beginStop
→ decision.beginStop
→ navigation.beginStop
→ materialization drain
→ scheduler.finishStop
→ topology.finishStop
→ decision.finishStop
→ navigation.finishStop
→ system STOPPED
```

If topology has an in-flight event:

- system remains FAILED;
- configured instance remains;
- later Goal 006B server-level shutdown retries finish;
- no event delivery starts after STOPPING.

Extend aggregate shutdown snapshot with:

```text
topologyState
topologyRegisteredProfiles
topologyEventsInFlight
topologyGeneration
```

No IDs or coordinates in shutdown logs.

Keep exactly two server-level Phantom shutdown attempts before
`ThreadPool.shutdown()`.

## 20. Metrics and diagnostics

Fixed aggregate metrics only:

- topology loads/reloads/reload failures;
- nodes/anchors/edges current;
- validation failures by fixed category;
- spatial queries/nearest queries/edge queries;
- door traversability checks;
- profiles registered/current/peak/update rejected;
- events accepted/rejected/in-flight/peak;
- recipients considered/delivered/backpressured/unregistered;
- local-chat/combat/targetability signal outcomes;
- stop failures.

No dynamic node/anchor/profile labels.

Bounded snapshot includes dataset ID/version/hash, generation, entity counts,
profile/event counts and last fixed failure category. No raw XML, paths list,
coordinates or event history.

## 21. Tests

Create:

```text
test/java/org/l2jmobius/tests/phantoms/PhantomTopologyCoreSuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomTopologyPerceptionSuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomTopologyProductionCorpusSuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomTopologyPerformanceSuite.java
```

Launcher modes and Ant targets:

```text
topology-core        / phantom-topology-core-test
topology-perception  / phantom-topology-perception-test
topology-corpus      / phantom-topology-production-corpus-test
topology-performance / phantom-topology-performance-smoke
```

### Core: at least 30 cases

Cover:

- ID/schema/string/count bounds;
- immutable canonical sorting/hash independent of XML order;
- area geometry/containment/polygon rejection;
- hierarchy cycle/depth/instance validation;
- duplicate/dangling edge/anchor refs;
- map-region/NPC/spawn/door factual validation;
- invalid reload retains prior snapshot;
- most-specific spatial resolution;
- nearest-anchor deterministic order;
- live door traversability;
- route-hint bounds;
- no server mutable object exposure.

### Perception: at least 22 cases

Cover:

- explicit profile registration/update/stale sequence;
- same-node local chat;
- allowed neighbor local chat;
- closed-door blocked neighbor;
- combat participants ACTIVE;
- combat neighbor NEARBY_PERCEPTIBLE;
- targetability ACTIVE and withdraw;
- radius/channel restrictions;
- recipient cap/backpressure;
- scheduler NOT_REGISTERED isolation;
- event sequence monotonicity;
- event stop race and finish quiescence;
- hard gate: perceptible recipient never below NEARBY_PERCEPTIBLE;
- no direct materialization/navigation call.

### Production corpus

Validate every committed production entity against current High Five loaders and
source files. Report exact counts, IDs and covered area.

It must fail on any missing NPC/spawn/door/map-region reference.

### Performance

Two byte-identical canonical runs:

```text
10000 nodes
20000 edges
50000 anchors
10000 registered profiles
1000 local-chat events
1000 combat events
nearest anchor limit 16
one-hop perception only
```

Structural gates:

```text
returned nodes <=64
returned edges <=1024
recipients/event <=1024
no per-profile thread/future/task
no DB query
```

Elapsed is evidence only; timeout 120 seconds.

### Regressions

- navigation core 50/50 ×3;
- navigation performance ×2;
- shutdown 7/7 ×3;
- decision core/persistence/performance;
- scheduler/materialization/headless/profile/DB/harness/skeleton;
- cumulative verify/jar.

## 22. Exact scope

Allowed production:

```text
java/org/l2jmobius/gameserver/phantoms/topology/**
java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java
java/org/l2jmobius/gameserver/phantoms/PhantomMetrics.java
java/org/l2jmobius/gameserver/Shutdown.java
dist/game/data/phantoms/topology/**
```

Allowed tests/build:

```text
build.xml
test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java
test/java/org/l2jmobius/tests/phantoms/PhantomTopologyCoreSuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomTopologyPerceptionSuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomTopologyProductionCorpusSuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomTopologyPerformanceSuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomServerShutdownHandoffSuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomSkeletonSuite.java
tools/phantoms/verify-task-010.ps1
```

Minimal compile/regression adjustments to existing scheduler/navigation tests are
allowed only if PhantomSystem snapshot/start/stop signature changes.

Allowed docs:

```text
docs/PHANTOM_BOTS_ROADMAP.md
docs/phantoms/architecture/TOPOLOGY_PERCEPTION_CONTRACT.md
docs/phantoms/tasks/010-topology-anchors-perception-graph/**
docs/phantoms/reports/009a-navigation-route-ownership-hardening.md
docs/phantoms/reports/010-topology-anchors-perception-graph.md
docs/phantoms/reviews/009a-navigation-route-ownership-hardening-review.md
```

## 23. Hard out of scope

Forbidden:

- changes to MapRegionData, NpcData, SpawnData/Table, DoorData/Door, World,
  ZoneManager or other loaders;
- GeoEngine/PathFinding/Creature/Player/AI/packets;
- PhantomPlayers config;
- DB schema/profile/goal persistence;
- Goal 006 materialization/identity semantics;
- decision candidates/handlers or actual movement;
- full world completeness claim;
- Game Knowledge item/drop/spoil/manor/recipe indexes;
- party route policy, combat kernel or conversation;
- automatic profile discovery/scheduler registration;
- new dependency/framework;
- new executor/raw production thread/per-profile task;
- production DB execution;
- other chronicles/CI/mass formatting;
- amend/rebase/merge/force push.

## 24. Static verifier Goal 010

Create deterministic read-only:

```text
tools/phantoms/verify-task-010.ps1
```

Verify:

- base `0780c77a...`, one ordinary exact-scope commit;
- no config/schema/Goal 011/012;
- server loaders/navigation/decision/lifecycle sources frozen;
- topology XML schema/version/bounds;
- no localized name identity;
- canonical hash/order;
- factual MapRegion/NPC/Spawn/Door validation;
- production representative corpus and source evidence;
- no semantic role inference by NPC name/title;
- immutable indexes/query bounds;
- live door state not cached;
- explicit profile registration only;
- fixed one-hop perception semantics;
- perceptible signal minimum NEARBY_PERCEPTIBLE;
- combat participant/targetability ACTIVE;
- no direct materialization/navigation calls;
- event token/STOPPING/finish quiescence;
- no executor/thread/per-profile Future;
- system startup/disabled/shutdown ordering;
- shutdown snapshot includes topology aggregates;
- tests/Ant/performance/corpus;
- Goal 009A closure and roadmap progress;
- UTF-8, mojibake, escaped Cyrillic;
- no credentials/binaries;
- verifier deterministic/read-only.

## 25. Commands

Pre-change:

```bat
ant verify
powershell -NoProfile -ExecutionPolicy Bypass -File tools\phantoms\verify-task-009a.ps1
```

Targeted:

```bat
ant compile-tests
ant phantom-topology-core-test
ant phantom-topology-perception-test
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
ant phantom-topology-core-test
ant phantom-topology-core-test
ant phantom-topology-core-test

ant phantom-topology-perception-test
ant phantom-topology-perception-test
ant phantom-topology-perception-test

ant phantom-topology-performance-smoke
ant phantom-topology-performance-smoke

ant phantom-topology-production-corpus-test
ant phantom-topology-production-corpus-test
```

Full:

```bat
ant verify
ant jar
powershell -NoProfile -ExecutionPolicy Bypass -File tools\phantoms\verify-task-010.ps1
git diff --check
git status --short --branch
```

Post-commit:

```bat
git rev-parse HEAD
git show --stat --oneline --decorate HEAD
git diff --check 0780c77ae605d8b2c36a4ff0345092506fb9f9c5...HEAD
ant verify
ant jar
powershell -NoProfile -ExecutionPolicy Bypass -File tools\phantoms\verify-task-010.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File tools\phantoms\verify-task-010.ps1
git push origin feature/phantom-world
git ls-remote --heads origin feature/phantom-world
git status --short --branch
```

Compare final verifier output byte-for-byte/SHA-256 outside the repository.

## 26. Report

Create:

```text
docs/phantoms/reports/010-topology-anchors-perception-graph.md
```

Required sections:

- status/baseline;
- Goal 009A closure;
- factual server-loader audit;
- semantic/data truth boundary;
- XML schema/version/hash;
- production seed corpus and exact coverage;
- node/anchor/edge contracts;
- server reference validation;
- spatial/adjacency indexes;
- live door overlay;
- profile registry;
- local-chat/combat/targetability providers;
- relevance signal mapping;
- perceptible-neighbor hard gate;
- event fanout/stop ownership;
- PhantomSystem startup/disabled/shutdown;
- metrics/snapshots;
- tests/corpus/performance;
- all regressions;
- production DB safety;
- static verifier;
- scope/deviations/map-completeness limitations;
- branch/parent/subject;
- manual gate `PENDING_INDEPENDENT_REVIEW`;
- Goal 011 `NOT_STARTED`.

Use external handoff wording for self SHA/push.

## 27. Acceptance result

Successful result:

```text
TOPOLOGY_PERCEPTION_GRAPH_IMPLEMENTED_PENDING_INDEPENDENT_REVIEW
```

Do not self-accept Goal 010 and do not start Goal 011/012.

## 28. Commit/push

Commit subject:

```text
feat(phantoms): add topology perception graph
```

One ordinary commit on top of `0780c77a...`.

Push regardless of SUCCESS/BLOCKED, but only safe scoped artifacts.

## 29. Blocking behavior

Return `BLOCKED` if:

- semantic roles must be guessed from localized names;
- factual NPC/spawn/door references cannot be validated;
- representative room/door corpus cannot be proven;
- perception can deliver a state below NEARBY_PERCEPTIBLE to a perceptible
  neighbor;
- stop can clear an in-flight event;
- server-loader/config/schema/Goal 011 changes are required;
- production DB is accessed;
- cumulative verify/jar fails.

On blocker, remove unsafe production data/wiring, preserve safe loader/core/tests/
report/verifier, ordinary commit/push and keep Goal 011 not started.

## 30. Final handoff

```text
Status:
Architecture result:
Baseline:
Goal 009A closure:
Dataset schema/version:
Canonical topology SHA:
Production seed covered area:
Production nodes/anchors/edges:
Map-region validation:
NPC/spawn validation:
Door passage validation:
Room corpus:
Spatial queries:
Nearest anchors:
Live door overlay:
Route hints:
Registered production profiles:
Local chat provider:
Combat provider:
Targetability provider:
Perceptible-neighbor minimum:
Signal backpressure isolation:
Event stop quiescence:
Topology shutdown snapshot:
Core tests:
Perception tests:
Production corpus tests:
Performance:
Two performance runs / summary SHA:
Navigation/decision/scheduler/lifecycle regressions:
All prior suites:
ant verify:
ant jar:
Static verifier pre:
Static verifier final 1:
Static verifier final 2:
Outputs identical:
Production DB:
Production JAR topology entries:
Production JAR test entries:
Commit:
Parent:
Branch:
Push:
Remote ref:
Report:
Manual gate:
Goal 011:
Goal 012:
Coverage limitations/blockers:
```
