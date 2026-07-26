# GOAL 009 — Navigation feasibility and PhantomNavigationService baseline

## 1. Identifier

- **Goal ID:** `009-navigation-feasibility-baseline`
- **Roadmap stage:** II — Scheduler, goals, navigation and authoritative knowledge
- **Branch:** `feature/phantom-world`
- **Accepted baseline:** `6ecd8ba155e63a2dedeeafd65c1961fdb57bf261`
- **Parent:** `b6c58c37f1ba77e92b61e9499a30d17d09c82086`
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
Goal 008: ACCEPT after Goal 008A
Goal 008A: ACCEPT
Goal 009: ALLOWED
Goal 010: NOT_STARTED
Goal 011: NOT_STARTED
```

Goal 008A accepted facts:

```text
Commit: 6ecd8ba155e63a2dedeeafd65c1961fdb57bf261
Parent: b6c58c37f1ba77e92b61e9499a30d17d09c82086
Remote: exact
Core: 35/35 ×3
Persistence: 23/23 ×3
Performance: 2/2 ×2
Scheduler: 20/20 ×3
Materialization: 20/20 ×3
Shutdown: 5/5 ×3
Verifier: 58/58 ×2, byte-identical
Independent verdict: ACCEPT
```

The committed Goal 008A report contains pre-commit verifier SHA:

```text
615165C7F2988E544C55F2111560665F0469C50CA5A152AA0922456BB50CD5C2
```

The external final handoff did not retain a full post-push verifier hash.
Do not invent one.

## 3. User-visible result

After Goal 009 the Phantom subsystem has a production-owned, bounded navigation
planning service that:

- detects geodata/pathfinding capability honestly;
- always tries a door/fence-aware direct route first;
- returns an explicitly unverified direct route when no geodata exists;
- submits bounded local A* requests to a shared queue;
- limits concurrent legacy pathfinder calls;
- supports cancellation and deadline-aware late-result discard;
- caches only bounded, revalidated local routes;
- applies per-profile pathfinding cooldown;
- exposes typed route/no-route/timeout/cancel/backpressure results;
- tracks progress, arrival, stuck and attempt timeout without owning `Player`;
- shuts down without accepting new work and retains in-flight ownership until
  legacy pathfinding returns.

Production starts with:

```text
active requests = 0
tracked attempts = 0
cache entries = 0
automatic navigation = 0
```

No decision candidate or concrete movement action is registered in Goal 009.

## 4. Factual server seam

The task is based on current High Five behavior:

1. `GeoEngine` initializes all regions as `NullRegion`.
2. If no region files load and `GeoEngineConfig.PATHFINDING > 0`, runtime changes
   `PATHFINDING` to `0`.
3. `GeoEngine.canMoveToTarget(...)` checks doors/fences and traverses geodata
   where present; without geodata it may still return true.
4. Therefore a direct result without geodata is **not** called validated or safe.
5. `PathFinding.findPath(...)` is synchronous, requires geodata at both ends,
   returns `null` for no path/buffer/error and owns no cancellation token.
6. Buffer allocation may create a temporary `NodeBuffer` when the configured
   pool has no free matching buffer.
7. Existing `Creature.moveToLocation` may fall back to direct movement when
   pathfinding fails. Goal 009 must not inherit that fallback as a “safe route”.
8. Existing pathfinding has no preemptive deadline. Goal 009 provides bounded
   queue/concurrency and discards a late result, but does not claim to interrupt
   an already-running legacy A* call.

Do not modify `GeoEngine`, `PathFinding`, `Creature`, movement managers or
global GeoEngine configuration.

## 5. Architectural boundary

Goal 009 owns:

- immutable navigation request/route/result contracts;
- factual capability snapshot;
- direct-path planning;
- bounded asynchronous local path request queue;
- request cancellation and deadlines;
- bounded route cache with validation;
- pathfinding cooldown;
- progress/stuck/timeout tracking;
- fixed metrics and bounded diagnostics;
- disabled/inert PhantomSystem integration.

It does not own:

- issuing movement commands to a `Player`;
- `Creature.moveToLocation` or AI intentions;
- anchors, regions, rooms or topology;
- route selection across Gatekeepers;
- combat/follow/party routes;
- decision candidates or handlers;
- background travel simulation;
- geodata generation or configuration;
- production profile registration/population.

## 6. Mandatory reading

Read fully:

1. roadmap, master plan, `Agents.md`, workflow/package/report standards;
2. Goal 006–008A reports/reviews/contracts;
3. current:
   - `GeoEngine.java`;
   - `GeoEngineConfig.java`;
   - `PathFinding.java`, `GeoLocation.java`, buffer classes;
   - relevant `Creature.moveToLocation`, `updatePosition`, `stopMove`;
   - `ThreadPool.java`;
   - `PhantomSystem.java`, scheduler, metrics and decision engine;
   - current tests and `build.xml`;
4. all documents in this package.

Do not read or modify another chronicle.

## 7. Initial Git audit

```bat
git rev-parse --show-toplevel
git status --short --branch
git remote -v
git fetch origin --prune
git rev-parse HEAD
git rev-parse origin/feature/phantom-world
git show --stat --oneline 6ecd8ba155e63a2dedeeafd65c1961fdb57bf261
git diff --name-status b6c58c37f1ba77e92b61e9499a30d17d09c82086..6ecd8ba155e63a2dedeeafd65c1961fdb57bf261
```

Expected:

```text
HEAD == origin/feature/phantom-world == 6ecd8ba1...
```

The extracted Goal 009 package is expected untracked. Preserve unrelated
`docs/agent-tasks/**`. Return `BLOCKED_BASELINE_DRIFT` for unreviewed
production/config/schema drift.

## 8. Close Goal 008A

Update:

```text
docs/phantoms/reports/008a-decision-persistence-timeout-hardening.md
```

Add immutable handoff:

```text
Commit: 6ecd8ba155e63a2dedeeafd65c1961fdb57bf261
Parent: b6c58c37f1ba77e92b61e9499a30d17d09c82086
Push/remote: exact
Core: 35/35 ×3
Persistence: 23/23 ×3
Performance: 2/2 ×2
Scheduler: 20/20 ×3
Materialization: 20/20 ×3
Shutdown: 5/5 ×3
Final verifier: 58/58 ×2, byte-identical
Independent review: ACCEPT
Goal 009: ALLOWED
```

Create:

```text
docs/phantoms/reviews/008a-decision-persistence-timeout-hardening-review.md
```

Verdict:

```text
Goal 008: ACCEPT after Goal 008A
Goal 008A: ACCEPT
Revert: NOT_REQUIRED
Goal 009: ALLOWED
Goal 010: NOT_STARTED
```

Roadmap progress only:

- accepted baseline becomes `6ecd8ba1...`;
- Goal 008/008A become `ACCEPT`;
- Goal 009 becomes `IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`;
- Goal 010 and Goal 011 remain `NOT_STARTED`;
- no future architecture or dependency rewrite.


## 9. Production package

Create:

```text
java/org/l2jmobius/gameserver/phantoms/navigation/
```

Required responsibility-equivalent types:

```text
PhantomNavigationService
PhantomNavigationPolicy
PhantomNavigationRequest
PhantomNavigationPoint
PhantomNavigationRoute
PhantomNavigationResult
PhantomNavigationBackend
L2jNavigationBackend
PhantomNavigationProgressTracker
PhantomNavigationCancellationToken
```

Small enums/snapshots/support records are allowed.

No navigation type stores or exposes `Player`, `Creature`, `WorldObject`,
`GameClient`, packet, AI intention, `GeoLocation` or mutable server collection.

## 10. Immutable request and result

### Point

```text
x, y, z, instanceId
```

All coordinates must remain inside current `World` coordinate bounds. Instance
ID is non-negative.

### Request

Behaviorally equivalent fields:

```text
profileId                 positive
origin                    point
destination               point
submittedLogicalNanos     non-negative
deadlineLogicalNanos      > submitted
maximumRouteDistance      1..100000
```

Origin and destination must use the same instance.

A request ID is generated by the service with overflow protection.

Exactly one nonterminal request per profile is allowed. A second returns
`PROFILE_BUSY`; it never implicitly cancels the first.

### Result categories

At minimum:

```text
DIRECT_VALIDATED
DIRECT_UNVERIFIED_NO_GEODATA
PATH_FOUND
NO_GEODATA
PATHFINDING_DISABLED
NO_PATH
ROUTE_BUDGET_EXCEEDED
QUEUE_BACKPRESSURE
PROFILE_BUSY
COOLDOWN
CANCELLED
DEADLINE_EXPIRED
BACKEND_FAILURE
SERVICE_NOT_RUNNING
```

The direct no-geodata result must remain distinguishable from a validated
geodata route.

### Route

Immutable and bounded:

```text
mode
origin
destination
waypoints                  1..64
totalDistance
geodataCapability
createdLogicalNanos
cacheable
```

- first logical segment starts at exact request origin;
- final waypoint equals exact request destination;
- adjacent duplicate points are rejected;
- every point keeps the request instance ID;
- total distance is overflow-safe and within request/policy budget;
- route lists are immutable;
- backend `GeoLocation` objects are copied into Phantom-owned values.

## 11. Capability detection

Create fixed enum equivalent to:

```text
NO_GEODATA
PARTIAL_GEODATA
GEODATA_DIRECT_ONLY
GEODATA_PATHFINDING
```

Production backend determines it per request:

```text
startGeo = GeoEngine.hasGeo(origin.x, origin.y)
targetGeo = GeoEngine.hasGeo(destination.x, destination.y)
pathfindingEnabled = GeoEngineConfig.PATHFINDING > 0
```

Rules:

- neither endpoint has geo → `NO_GEODATA`;
- exactly one has geo → `PARTIAL_GEODATA`;
- both have geo, pathfinding disabled → `GEODATA_DIRECT_ONLY`;
- both have geo, pathfinding enabled → `GEODATA_PATHFINDING`.

Do not instantiate `GeoEngine` or `PathFinding` during Phantom startup.
Production backend calls their singletons lazily only when an explicit request
is processed.

## 12. Direct-path first

Every accepted request performs exactly one initial call equivalent to:

```java
GeoEngine.canMoveToTarget(
    origin.x, origin.y, origin.z,
    destination.x, destination.y, destination.z,
    instanceId)
```

If true:

- with geo at both endpoints → `DIRECT_VALIDATED`;
- otherwise → `DIRECT_UNVERIFIED_NO_GEODATA`;
- route is origin → destination;
- no pathfinding call;
- no pathfinding cooldown;
- no computed-route cache lookup required.

If false:

- `NO_GEODATA` or `PARTIAL_GEODATA` → `NO_GEODATA`;
- `GEODATA_DIRECT_ONLY` → `PATHFINDING_DISABLED`;
- only `GEODATA_PATHFINDING` may continue to local pathfinding.

Never silently fall back to direct movement after a blocked/failed A* request.

## 13. Bounded local pathfinding

### 13.1. Policy defaults

Use immutable production defaults, not new config keys:

```text
maximum queued requests:          256
maximum concurrent pathfinders:   2
maximum tracked profile states:   10000
maximum cache entries:            1024
cache TTL:                        5000 ms
pathfinding cooldown:             1000 ms
maximum local straight distance:  12000 world units
maximum waypoints:                64
maximum route distance:           100000 world units
default request deadline:         1000 ms
stuck window:                     3000 ms
minimum progress:                 20 world units
arrival radius:                   50 world units
maximum attempt duration:         120000 ms
```

All values are constructor-validated. Tests may use smaller policies.

These are safety defaults, not population/gameplay tuning. Goal 028 may expose
them operationally after profiling.

### 13.2. Queue and workers

The service owns:

```text
ArrayBlockingQueue<RequestEntry>
bounded request registry
bounded per-profile active/cooldown state
bounded completed-result retention
```

Use no raw thread and no new executor.

Production dispatch uses existing `ThreadPool` and at most
`maximumConcurrentPathfinders` transient **shared drain workers** total.

Requirements:

- worker tasks are service-level, not per-profile permanent tasks;
- do not submit more workers than the configured concurrency;
- a worker drains bounded queued requests and exits when the queue is empty;
- lifecycle state and worker count are owned under one service monitor;
- backend work is outside the monitor;
- backend calls for different requests are bounded by worker count;
- queue saturation returns `QUEUE_BACKPRESSURE` without profile ownership;
- dispatcher failure returns a stable failure and does not strand worker count;
- tests use an injected deterministic dispatcher.

Because `ThreadPool.execute` has no acceptance result, production may use a
narrow injected dispatcher whose implementation safely schedules a zero-delay
shared task through an existing ThreadPool API that returns success/null, or an
equivalent proven non-stranding pattern. Do not modify `ThreadPool`.

### 13.3. Distance and backend budget

Before A*:

- straight-line 3D distance must be <= policy local limit;
- request maximum route distance must be <= policy maximum;
- profile cooldown must have expired;
- request deadline must not already be expired.

Only then call:

```java
PathFinding.findPath(..., playable=true)
```

The legacy call is not preemptively cancellable.

After it returns:

- if cancellation generation changed → `CANCELLED`;
- if deadline passed → `DEADLINE_EXPIRED`;
- if exception → `BACKEND_FAILURE`;
- null/short path → `NO_PATH`;
- invalid/oversized path → `ROUTE_BUDGET_EXCEEDED` or `BACKEND_FAILURE`;
- valid route → `PATH_FOUND`.

Late/cancelled results are never cached or published as successful.

A slow legacy call retains its worker/request ownership until return. No second
request for the same profile starts meanwhile.

## 14. Cache and cooldown

### Cache

Use access-ordered bounded LRU or exact equivalent:

```text
maximum 1024 entries
TTL 5000 ms
```

Key includes exact:

```text
origin xyz
destination xyz
instance ID
backend capability generation/mode
```

Do not key only by profile.

Only successful computed `PATH_FOUND` routes are cached. Direct routes are
cheaply rechecked instead of trusted from cache.

Before returning a cache hit:

1. check TTL;
2. check cancellation/deadline;
3. revalidate every segment through backend direct movement validation;
4. require capability mode still compatible.

Invalid/stale entries are removed and a normal path request proceeds.
Cache validation is bounded by 64 waypoints.

### Cooldown

After an actual A* attempt that returns `NO_PATH`, `DEADLINE_EXPIRED`,
`BACKEND_FAILURE` or invalid route, set one per-profile cooldown.

A direct route is still tested during cooldown. Cooldown blocks only another A*
attempt after direct-path failure.

Profile cooldown state is bounded and removable. It must not grow beyond policy
maximum tracked profiles.

## 15. Cancellation and polling

Submission is asynchronous and returns immutable:

```text
submission status
request ID
initial capability if already known, or UNKNOWN
```

APIs behaviorally equivalent to:

```java
boolean start()
Submission submit(PhantomNavigationRequest request)
CancelResult cancel(long profileId, long requestId)
Optional<RequestSnapshot> find(long requestId)
Optional<PhantomNavigationResult> consume(long requestId)
ServiceSnapshot snapshot()
BeginStopResult beginStop()
boolean finishStop()
```

Rules:

- cancellation is generation/state based;
- queued cancellation is removed or skipped deterministically;
- in-flight cancellation does not interrupt legacy pathfinding;
- late path result is discarded;
- completed results are retained only in a bounded map;
- `consume` removes terminal result and associated profile state;
- oldest terminal results may be evicted only under documented bounded policy;
- nonterminal requests are never evicted.

No arbitrary callback, `Consumer`, `CompletableFuture` or exposed mutable
`Future`.

## 16. Progress, arrival, stuck and attempt timeout

`PhantomNavigationProgressTracker` is pure/bounded and owns no movement.

Behaviorally equivalent API:

```java
BeginResult begin(
    long profileId,
    long requestId,
    PhantomNavigationRoute route,
    long logicalNowNanos)

ProgressResult observe(
    long profileId,
    long requestId,
    PhantomNavigationPoint current,
    long logicalNowNanos)

CancelResult cancel(long profileId, long requestId)
Optional<ProgressSnapshot> find(long profileId)
```

One attempt/profile.

Statuses:

```text
TRACKING
PROGRESS
ARRIVED
STUCK
TIMEOUT
CANCELLED
STALE
```

Rules:

- exact request/profile ownership;
- same instance required;
- arrival within configured radius of final destination;
- meaningful progress means distance-to-destination improved by at least the
  configured minimum since last progress anchor;
- no meaningful progress for stuck window → `STUCK`;
- total duration above attempt maximum → `TIMEOUT`;
- timeout checked before stuck;
- logical time regression is rejected as `STALE`;
- terminal result removes active tracking or retains only one bounded terminal
  snapshot, according to documented policy;
- no polling task/timer is created.

This tracker is a contract for future movement handlers. Goal 009 does not call
`Creature.moveToLocation`.


## 17. PhantomSystem integration

Add `PhantomNavigationService` as an inert production-owned subsystem.

Enabled startup:

```text
profile repository
→ materialization service
→ decision engine
→ navigation service start
→ scheduler start
```

Navigation construction/start must not initialize GeoEngine/PathFinding, submit
workers or create requests.

Shutdown:

```text
scheduler.beginStop
→ decisionEngine.beginStop
→ navigationService.beginStop
→ materialization service drain
→ scheduler.finishStop
→ decisionEngine.finishStop
→ navigationService.finishStop
→ PhantomSystem STOPPED
```

If navigation finish returns false because a legacy path call is still in
flight:

- PhantomSystem remains FAILED;
- configured instance remains;
- Goal 006B second server-level shutdown retries finish;
- no new request/worker starts;
- ThreadPool is not stopped before the second bounded server-level opportunity;
- persistent failure is reported by existing aggregate shutdown diagnostics.

Disabled path creates no navigation service, queue, cache, worker, GeoEngine
singleton or DB query.

Expose no production request API through global static methods in Goal 009.
Future decision handlers receive the service through explicit wiring.

## 18. Metrics and diagnostics

Add fixed aggregate counters only:

- submissions accepted/rejected;
- direct validated/unverified;
- queued/current/peak;
- workers current/peak;
- cache hit/miss/invalidated/evicted;
- path attempts/succeeded/no-path/failed/timed-out/cancelled;
- queue wait expired;
- cooldown rejected;
- route budget rejected;
- progress/arrived/stuck/attempt timeout/cancelled;
- begin/finish stop failures.

No profile/request dynamic metric labels.

Optional trace uses existing bounded sampled trace with only short event key and
request ID. No coordinates, full path or per-node logging at INFO/WARNING.

## 19. Automated suites

Create:

```text
test/java/org/l2jmobius/tests/phantoms/PhantomNavigationCoreSuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomNavigationPerformanceSuite.java
```

Launcher modes:

```text
navigation-core
navigation-performance
```

Ant targets:

```text
phantom-navigation-core-test
phantom-navigation-performance-smoke
```

No test requires installed geodata files. Use injected backend, dispatcher and
monotonic clock.

### Core: at least 25 explicit cases

Required:

1. point/request/route validation and immutability;
2. service starts empty/inert;
3. direct geo route → `DIRECT_VALIDATED`, zero A* calls;
4. direct no-geo route → `DIRECT_UNVERIFIED_NO_GEODATA`;
5. blocked no-geo → `NO_GEODATA`;
6. blocked geo with pathfinding disabled;
7. bounded local-distance rejection;
8. successful path copies backend points and exact destination;
9. null/short path;
10. waypoint count >64;
11. route length overflow/budget;
12. exact one active request/profile;
13. queue backpressure is atomic;
14. maximum worker concurrency;
15. queued cancellation;
16. in-flight cancellation with late-result discard;
17. deadline expires before worker start;
18. deadline expires during legacy backend call;
19. backend exception isolation;
20. cache hit after segment revalidation;
21. cache invalidation after dynamic obstacle;
22. bounded LRU eviction;
23. cooldown blocks A* but not a new direct route;
24. stop rejects new request and waits for in-flight worker;
25. progress/arrival;
26. stuck;
27. total attempt timeout before stuck;
28. stale logical time/request;
29. no raw thread/new executor/per-profile future;
30. production backend lazy singleton access and exact API mapping via injectable
    facade/static verifier.

### Factual source contract test

A static/focused test or verifier must prove the production adapter uses:

```text
GeoEngine.hasGeo
GeoEngine.canMoveToTarget
GeoEngineConfig.PATHFINDING
PathFinding.findPath(..., playable=true)
```

and never uses:

```text
Creature.moveToLocation
Creature.teleToLocation
AI intention
packet/request handler
```

### Performance

Run twice with byte-identical canonical summary:

```text
10,000 direct fake-backend requests
1,000 repeated local-path requests
>= 90% computed-route cache hit after first fill
queue capacity 256
workers 2
cache <= 1024
waypoints <= 64
```

Report:

- request/result counts;
- direct/path/cache/backend call counts;
- maximum queue/workers/cache;
- cancellation/timeout counts;
- elapsed time as evidence only.

No hard machine-speed pass threshold except test timeout <=120 seconds and
structural bounds.

### Regressions

- decision core 35/35 ×3;
- decision persistence 23/23 ×3;
- decision performance ×2;
- scheduler 20/20 ×3;
- production materialization 20/20 ×3;
- shutdown 5/5 ×3;
- all previous headless/profile/DB/harness/skeleton/performance;
- cumulative verify/jar.

## 20. Exact scope

Allowed production:

```text
java/org/l2jmobius/gameserver/phantoms/navigation/**
java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java
java/org/l2jmobius/gameserver/phantoms/PhantomMetrics.java
```

Allowed tests/build:

```text
build.xml
test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java
test/java/org/l2jmobius/tests/phantoms/PhantomNavigationCoreSuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomNavigationPerformanceSuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomSkeletonSuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomServerShutdownHandoffSuite.java
tools/phantoms/verify-task-009.ps1
```

Only modify existing scheduler/decision/materialization tests if a production
start/stop snapshot signature requires a minimal compile/regression adjustment.
Prefer no modification.

Allowed docs:

```text
docs/PHANTOM_BOTS_ROADMAP.md
docs/phantoms/architecture/NAVIGATION_SERVICE_CONTRACT.md
docs/phantoms/tasks/009-navigation-feasibility-baseline/**
docs/phantoms/reports/008a-decision-persistence-timeout-hardening.md
docs/phantoms/reports/009-navigation-feasibility-baseline.md
docs/phantoms/reviews/008a-decision-persistence-timeout-hardening-review.md
```

## 21. Hard out of scope

Forbidden:

- `GeoEngine.java`, `GeoEngineConfig.java`, `PathFinding.java`, buffer classes;
- `Creature`, `Player`, AI, movement managers or packets;
- geodata/pathnode/config changes;
- PhantomPlayers config changes;
- DB schema/profile/goal component changes;
- Goal 006 materialization/identity changes;
- decision candidates/handlers/concrete movement action;
- topology, anchors, rooms, Gatekeepers or Goal 010;
- Game Knowledge or Goal 011;
- combat/follow/party/background travel;
- automatic profile discovery/navigation;
- new executor/raw production thread;
- permanent/per-profile Future/task;
- production DB execution;
- other chronicles/dependencies/CI/mass formatting;
- amend/rebase/merge/force push.

## 22. Static verifier Goal 009

Create deterministic read-only:

```text
tools/phantoms/verify-task-009.ps1
```

Verify:

- base `6ecd8ba1...`, one ordinary exact-scope commit;
- no config/schema/Goal 010/011;
- GeoEngine/PathFinding/Creature/decision/lifecycle source frozen;
- navigation package imports no Player/Creature/GameClient/packet/AI;
- direct-path call precedes cache/A*;
- no-geo direct result explicitly unverified;
- no fallback direct route after blocked/failed A*;
- queue/cache/profile/result bounds;
- max shared workers and no per-profile task/future;
- lazy GeoEngine/PathFinding singleton access;
- exact capability detection;
- cancellation/deadline result discard;
- cache segment revalidation and LRU/TTL;
- cooldown applies only to A*;
- route max 64 and distance bounds;
- progress stuck/timeout ordering;
- system start inert and disabled no service;
- system stop begin/finish checks;
- fixed metrics/no dynamic labels;
- launcher/Ant/tests/performance;
- Goal 008A closure and roadmap progress;
- UTF-8, mojibake, escaped Cyrillic;
- no credentials/binaries;
- verifier deterministic/read-only.

## 23. Commands

Pre-change:

```bat
ant verify
powershell -NoProfile -ExecutionPolicy Bypass -File tools\phantoms\verify-task-008a.ps1
```

Targeted:

```bat
ant compile-tests
ant phantom-navigation-core-test
ant phantom-navigation-performance-smoke
ant phantom-decision-core-test
ant phantom-decision-persistence-test
ant phantom-decision-performance-smoke
ant phantom-activity-scheduler-test
ant phantom-production-materialization-test
ant phantom-server-shutdown-handoff-test
ant phantom-headless-player-test
ant phantom-profile-persistence-test
ant phantom-db-test
ant test
ant phantom-skeleton-test
```

Repeat:

```bat
ant phantom-navigation-core-test
ant phantom-navigation-core-test
ant phantom-navigation-core-test

ant phantom-navigation-performance-smoke
ant phantom-navigation-performance-smoke

ant phantom-decision-core-test
ant phantom-decision-core-test
ant phantom-decision-core-test
```

Full:

```bat
ant verify
ant jar
powershell -NoProfile -ExecutionPolicy Bypass -File tools\phantoms\verify-task-009.ps1
git diff --check
git status --short --branch
```

Post-commit:

```bat
git rev-parse HEAD
git show --stat --oneline --decorate HEAD
git diff --check 6ecd8ba155e63a2dedeeafd65c1961fdb57bf261...HEAD
ant verify
ant jar
powershell -NoProfile -ExecutionPolicy Bypass -File tools\phantoms\verify-task-009.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File tools\phantoms\verify-task-009.ps1
git push origin feature/phantom-world
git ls-remote --heads origin feature/phantom-world
git status --short --branch
```

Compare final verifier output byte-for-byte/SHA-256 outside the repository.

## 24. Report

Create:

```text
docs/phantoms/reports/009-navigation-feasibility-baseline.md
```

Required sections:

- Status/baseline;
- Goal 008A closure;
- factual High Five geo/pathfinding audit;
- capability modes;
- request/result/route contracts;
- direct-path behavior;
- no-geodata semantics;
- bounded queue/shared workers;
- legacy A* cancellation limitation;
- deadline/cancellation ownership;
- cache/revalidation/cooldown;
- progress/stuck/timeout tracker;
- PhantomSystem startup/disabled/shutdown;
- metrics/diagnostics;
- tests and deterministic performance;
- all regressions;
- production DB safety;
- static verifier;
- scope/deviations/limitations;
- branch/parent/subject;
- manual gate `PENDING_INDEPENDENT_REVIEW`;
- Goal 010 `NOT_STARTED`.

Use external handoff wording for self SHA/push.

## 25. Acceptance result

Successful result:

```text
NAVIGATION_SERVICE_BASELINE_IMPLEMENTED_PENDING_INDEPENDENT_REVIEW
```

Do not self-accept Goal 009 and do not start Goal 010.

## 26. Commit/push

Commit subject:

```text
feat(phantoms): add navigation service baseline
```

One ordinary commit on top of `6ecd8ba1...`.

Push regardless of SUCCESS/BLOCKED, but only safe scoped artifacts.

## 27. Blocking behavior

Return `BLOCKED` if:

- bounded shared dispatch cannot be implemented without modifying ThreadPool;
- legacy A* must run on scheduler/decision caller thread;
- request/result ownership can be lost on cancellation/stop;
- no-geodata result would be mislabeled validated;
- route cache cannot be revalidated against dynamic obstacles;
- Creature/GeoEngine/PathFinding/config/schema/Goal 010 changes are required;
- production DB is accessed;
- cumulative verify/jar fails.

On blocker, remove unsafe production wiring, preserve safe factual audit/tests/
report/verifier, ordinary commit/push and keep Goal 010 not started.

## 28. Final handoff

```text
Status:
Architecture result:
Baseline:
Goal 008A closure:
Factual geo audit:
Capability modes:
Direct validated:
Direct unverified no-geo:
Blocked no-geo:
Pathfinding disabled:
Queue capacity:
Worker concurrency:
Local distance/waypoint budgets:
Cancellation queued:
Cancellation in-flight:
Deadline late discard:
Cache hit/revalidation/eviction:
Cooldown:
Progress/arrival:
Stuck:
Attempt timeout:
Stop quiescence:
Production active requests:
Production GeoEngine initialization at startup:
Navigation core tests:
Three core runs:
Navigation performance:
Two performance runs / summary SHA:
Decision/scheduler/lifecycle regressions:
All prior suites:
ant verify:
ant jar:
Static verifier pre:
Static verifier final 1:
Static verifier final 2:
Outputs identical:
Production DB:
Production JAR navigation entries:
Production JAR test entries:
Commit:
Parent:
Branch:
Push:
Remote ref:
Report:
Manual gate:
Goal 010:
Limitations/blockers:
```
