# GOAL 009A — Navigation route ownership hardening

## 1. Identifier

- **Task ID:** `009a-navigation-route-ownership-hardening`
- **Type:** mandatory bounded safety closure for Goal 009
- **Branch:** `feature/phantom-world`
- **Starting baseline:** `b6e893f6bb8abf26908e441ee79b92d6f910eb91`
- **Parent:** `6ecd8ba155e63a2dedeeafd65c1961fdb57bf261`
- **Repository root:** `C:\Users\endim\L2J_Mobius\`
- **Only module:** `C:\Users\endim\L2J_Mobius\L2J_Mobius_CT_2.6_HighFive\`
- **Test DB:** `l2jmobiush5_phantom_test`
- **Production DB:** `l2jmobiush5` — never use during execution
- **Seed:** `20260725001`
- **Codex model:** Sol
- **Effort:** Very High

## 2. Independent review gate

```text
Stage I: COMPLETE
Goal 007 / 007A: ACCEPT
Goal 008 / 008A: ACCEPT
Goal 009 architecture direction: ACCEPT
Goal 009 commit: FIX_REQUIRED
Revert: NOT_REQUIRED
Goal 009A: REQUIRED
Goal 010: BLOCKED
Goal 011: NOT_STARTED
```

Keep all accepted Goal 009 work:

- inert production-owned navigation service;
- factual lazy capability adapter;
- explicit no-geodata result;
- direct route before A*;
- bounded queue, active registries, cooldown and LRU cache;
- no more than two transient shared workers;
- generation-based queued/in-flight cancellation;
- deadline-aware late-result discard;
- pure progress/arrival/stuck/timeout tracker;
- no Player/Creature/movement ownership;
- zero startup requests/workers/cache and no automatic navigation.

This task closes only route truth, backend preflight, dispatch/stop ordering and
shutdown observability.

## 3. Independent findings

### P1 — computed path segments are not validated before publication

`validateBackendPath()` validates only:

- null/short path;
- point instance;
- adjacent duplicates;
- waypoint count;
- total distance.

It does not call the door/fence-aware direct backend for any computed segment.

If the legacy path does not end at the exact request destination, the service
blindly appends the destination. The current focused test explicitly expects:

```text
backend path: origin → intermediate
service output: intermediate → exact destination appended
status: PATH_FOUND
```

The appended final segment is not validated.

Legacy `PathFinding` works primarily on geodata nodes. Dynamic doors/fences and
the exact final segment must be validated through
`GeoEngine.canMoveToTarget(...)`. Otherwise the service can publish and cache a
`PATH_FOUND` route that crosses a closed door, fence or obstructed last segment.

### P1 — deadline and impossible route budget are checked after backend direct scan

`processDirect()` currently calls:

```text
backend.capability
backend.canMoveDirect
```

before checking:

- request deadline;
- straight origin→destination distance against the request/policy route budget.

A request that is already expired or mathematically cannot fit its route budget
can still initialize GeoEngine and perform a potentially long synchronous
geodata line traversal on the caller thread.

Input budget/deadline checks are not pathfinding fallbacks and may safely precede
the direct backend call.

### P1 — worker dispatch can race STOPPING

The service increments `_workers` and releases its monitor before calling the
dispatcher. `beginStop()` may overtake that gap, cancel the queue and allow the
server shutdown sequence to proceed while the submitter has not yet scheduled
the claimed worker.

The production dispatcher uses `ThreadPool.schedule(worker, 0) != null`.
`ThreadPool` has a custom rejected handler that returns silently when the pool
is already shut down. Therefore a non-null scheduled handle is not a sufficient
acceptance signal if dispatch is allowed to occur after the Phantom service
entered STOPPING and the shared pool was stopped.

Required invariant:

```text
a worker dispatch is ordered before beginStop
OR
the worker claim/queue entry is synchronously rolled back before STOPPING
```

No worker claim may remain for a task that was never placed while the shared
ThreadPool was alive.

### P2 — real shutdown diagnostic still reports only materialization state

`ConfiguredShutdownSnapshot` exposes only materialization service state and
retained actor count. If Goal 009 alone prevents Phantom shutdown, the real
server log can say:

```text
materialization service STOPPED
retainedEntries = 0
Final materialization drain incomplete
```

while the actual blocker is one navigation worker/request.

The final severe diagnostic before `ThreadPool.shutdown()` must report both
materialization and navigation aggregate state without profile IDs or route
data.

## 4. Goal

Implement and prove:

1. every computed route segment is door/fence/geodata-direct validated before
   `PATH_FOUND`, cache insertion or publication;
2. an automatically appended exact destination is validated as the final
   segment;
3. cancellation and deadline are checked between segment validations;
4. an obstructed computed route is never cached and returns a distinct stable
   result;
5. expired or route-budget-impossible input performs zero capability/direct/A*
   backend calls;
6. dispatch cannot begin after navigation STOPPING;
7. dispatcher failure or stop race cannot strand worker/request ownership;
8. navigation finish remains false until all accepted workers return;
9. real shutdown diagnostics identify navigation queue/worker/request blockers;
10. Goal 010 and Goal 011 remain not started;
11. all Goal 001–009 regressions remain GREEN.

## 5. Mandatory reading

Read fully:

- roadmap, master plan, `Agents.md`, workflow/package/report standards;
- Goal 009 package/report/contract;
- Goal 008A closure;
- current navigation package and tests;
- `ThreadPool.schedule` and rejected-handler behavior;
- `PhantomSystem`, `Shutdown` and Goal 006B shutdown tests;
- factual `GeoEngine.canMoveToTarget` and `PathFinding.constructPath`;
- all documents in this package.

## 6. Initial Git audit

```bat
git rev-parse --show-toplevel
git status --short --branch
git remote -v
git fetch origin --prune
git rev-parse HEAD
git rev-parse origin/feature/phantom-world
git show --stat --oneline b6e893f6bb8abf26908e441ee79b92d6f910eb91
git diff --name-status 6ecd8ba155e63a2dedeeafd65c1961fdb57bf261..b6e893f6bb8abf26908e441ee79b92d6f910eb91
```

Expected:

```text
HEAD == origin/feature/phantom-world == b6e893f6...
```

The extracted Goal 009A package is expected untracked. Preserve unrelated
`docs/agent-tasks/**`. Return `BLOCKED_BASELINE_DRIFT` for unreviewed
production/config/schema drift.

## 7. Fixed route-validation contract

### 7.1. Input preflight

After service/profile/request ownership is reserved but before any backend call:

1. recheck service state and cancellation;
2. read logical clock;
3. if deadline already expired:
   - complete `DEADLINE_EXPIRED`;
   - zero capability/direct/path calls;
4. calculate exact straight 3D origin→destination distance;
5. if non-finite or greater than:

```text
min(request.maximumRouteDistance, policy.maximumRouteDistance)
```

complete `ROUTE_BUDGET_EXCEEDED` with zero backend calls.

Do not apply A* local-distance rejection before a valid direct route. Preserve
the accepted behavior that the `maximumLocalStraightDistance` gate applies when
direct movement failed and A* would be requested.

### 7.2. Computed route construction

The backend list is only an untrusted candidate path.

Normalize it first:

- copy Phantom-owned values;
- optionally remove one exact leading origin;
- reject null, wrong instance or adjacent duplicate;
- append the exact destination only when absent;
- enforce maximum 64 waypoints and route-distance budget.

Then validate all segments in order:

```text
origin → waypoint 0
waypoint 0 → waypoint 1
...
last waypoint → exact destination
```

For each segment:

1. check cancellation generation;
2. check deadline against current logical clock;
3. call `backend.canMoveDirect(previous, next)`;
4. catch backend exception as `BACKEND_FAILURE`;
5. false result becomes `ROUTE_OBSTRUCTED`.

Add result status:

```text
ROUTE_OBSTRUCTED
```

It is a terminal unsuccessful A* result:

- no route is published;
- no cache insertion;
- pathfinding cooldown is set because an actual A* attempt occurred;
- fixed metrics record it without coordinates/profile labels.

The initial direct call remains exactly one. Segment validation calls are
separate post-A* route verification and must be reflected honestly in tests and
metrics.

### 7.3. Late cancellation/deadline

If cancellation or deadline happens during segment validation:

```text
CANCELLED
DEADLINE_EXPIRED
```

takes precedence over any not-yet-published route.

No partial route is exposed or cached.

### 7.4. Cache

Only a route that passed initial segment validation may enter cache.

Cache-hit revalidation remains mandatory and uses the same bounded segment
validation helper where practical, with mode-specific metrics:

- initial computed-route validation failure;
- cache revalidation failure.

Do not double-count an initial direct request as a route segment.

## 8. Dispatch/STOPPING handoff

Introduce one narrow ordering gate, for example:

```text
private final Object _dispatchGate
```

Required ordering:

```text
submit claims queue/worker
→ dispatch gate
   → recheck service/entry/worker claim
   → either schedule worker while service is still RUNNING
   → or roll back queue/request/worker claim
→ release dispatch gate

beginStop
→ same dispatch gate
   → set STOPPING and cancel queue
→ release
```

Rules:

- no expensive backend call under `_dispatchGate` or `_monitor`;
- dispatcher is invoked at most once per claimed worker;
- dispatcher false/exception rolls back exactly the claimed worker;
- queued entry completes `BACKEND_FAILURE` only when no accepted worker can own
  it;
- an already accepted worker may start before or after beginStop, but it drains
  cancelled/empty work and releases its exact worker count;
- no dispatch call begins after STOPPING is observable;
- inline test dispatcher must not deadlock;
- `_workers` never becomes negative;
- no new executor/raw thread/per-profile Future.

Do not modify `ThreadPool`.

Mandatory race test:

1. block the dispatcher before it records acceptance;
2. start submit that has claimed a worker;
3. start beginStop concurrently;
4. prove beginStop cannot overtake the dispatch decision;
5. on accepted dispatch, run the worker and reach STOPPED;
6. on rejected dispatch, prove worker/request ownership is zero and reach
   STOPPED;
7. no second dispatch occurs.

## 9. Shutdown diagnostics

Extend `PhantomSystem.ConfiguredShutdownSnapshot` with bounded aggregate
navigation fields equivalent to:

```text
navigationState
navigationActiveRequests
navigationQueuedRequests
navigationWorkers
```

No request/profile IDs, coordinates or routes.

Update `Shutdown.java` wording from materialization-only to subsystem-wide
Phantom drain. Initial/final messages must include:

```text
systemState
materializationServiceState
retainedMaterializationEntries
navigationState
navigationActiveRequests
navigationQueuedRequests
navigationWorkers
```

Requirements:

- if materialization is stopped but navigation is incomplete, final log clearly
  identifies navigation as incomplete;
- no success message while configured instance remains;
- final persistent failure remains `SEVERE`;
- `ThreadPool.shutdown()` ordering from Goal 006B is unchanged;
- exactly two server-level Phantom shutdown calls remain;
- generic managed-player exclusion remains unchanged.

Add focused shutdown snapshot/policy tests. Do not invoke a real full server
shutdown or `System.exit`.

## 10. Tests

Extend navigation core to at least 44 explicit cases.

Mandatory new cases:

1. expired preflight performs zero capability/direct/path calls;
2. impossible route budget performs zero capability/direct/path calls;
3. backend path with blocked intermediate segment returns `ROUTE_OBSTRUCTED`;
4. backend path lacking exact destination, with blocked appended final segment,
   returns `ROUTE_OBSTRUCTED`;
5. valid appended final segment returns `PATH_FOUND`;
6. obstruction never enters cache;
7. cancellation during segment validation wins and does not cache;
8. deadline during segment validation wins and does not cache;
9. segment-validation backend exception becomes `BACKEND_FAILURE`;
10. cooldown follows route obstruction but does not block a later direct route;
11. accepted dispatch versus beginStop ordering;
12. rejected dispatch versus beginStop ordering;
13. inline dispatcher does not deadlock or double-decrement;
14. shutdown snapshot reports navigation-only blocker;
15. final shutdown diagnostic state is failure, not materialization success.

Preserve:

- navigation core ×3;
- navigation performance ×2 with deterministic canonical summary;
- decision core/persistence/performance;
- scheduler/materialization/shutdown/headless/profile/DB/harness/skeleton;
- cumulative verify/jar.

Performance summary may have more direct-backend calls because computed routes
are now validated segment-by-segment. Cache hit rate and structural bounds must
remain deterministic.

## 11. Exact scope

Allowed production:

```text
java/org/l2jmobius/gameserver/phantoms/navigation/PhantomNavigationService.java
java/org/l2jmobius/gameserver/phantoms/navigation/PhantomNavigationResult.java
java/org/l2jmobius/gameserver/phantoms/PhantomMetrics.java
java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java
java/org/l2jmobius/gameserver/Shutdown.java
```

Prefer no change to route/backend/policy contracts unless required for the new
typed status or helper extraction.

Allowed tests/build:

```text
build.xml
test/java/org/l2jmobius/tests/phantoms/PhantomNavigationCoreSuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomNavigationPerformanceSuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomServerShutdownHandoffSuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomSkeletonSuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java
tools/phantoms/verify-task-009a.ps1
```

Allowed documentation:

```text
docs/PHANTOM_BOTS_ROADMAP.md
docs/phantoms/architecture/NAVIGATION_SERVICE_CONTRACT.md
docs/phantoms/tasks/009a-navigation-route-ownership-hardening/**
docs/phantoms/reports/009-navigation-feasibility-baseline.md
docs/phantoms/reports/009a-navigation-route-ownership-hardening.md
docs/phantoms/reviews/009-navigation-feasibility-baseline-review.md
```

## 12. Hard out of scope

Forbidden:

- GeoEngine, GeoEngineConfig, PathFinding or buffer changes;
- Creature, Player, AI, movement manager, packets;
- PhantomPlayers config;
- DB schema/profile/goal persistence;
- Goal 006 lifecycle/identity semantics;
- decision candidates/handlers or movement action;
- topology/anchors/rooms/Gatekeepers/Goal 010;
- Game Knowledge/Goal 011;
- automatic navigation/population;
- new executor/raw production thread/per-profile task;
- production DB;
- other chronicles/dependencies/CI/mass formatting;
- amend/rebase/merge/force push.

## 13. Static verifier

Create deterministic read-only:

```text
tools/phantoms/verify-task-009a.ps1
```

It must verify:

- base `b6e893f6...`, one ordinary exact-scope commit;
- no config/schema/Goal 010/011;
- GeoEngine/PathFinding/Creature/decision/lifecycle sources frozen;
- preflight deadline/budget precedes backend capability/direct;
- computed path validates every segment including appended destination;
- `ROUTE_OBSTRUCTED` cannot contain/cache a route;
- cancellation/deadline checks between segment validations;
- shared helper used for cache revalidation or equivalent exact checks;
- dispatch gate orders dispatch before STOPPING;
- no dispatch after STOPPING;
- exact worker rollback and nonnegative worker count;
- no new executor/thread/per-profile Future;
- configured shutdown snapshot includes navigation aggregates;
- Shutdown retains exact two-call ordering and reports subsystem-wide states;
- navigation tests/performance and all regression targets;
- Goal 009 report/review/roadmap progress;
- UTF-8, mojibake, escaped Cyrillic;
- no credentials/binaries;
- verifier deterministic/read-only.

## 14. Documentation

Create:

```text
docs/phantoms/reviews/009-navigation-feasibility-baseline-review.md
docs/phantoms/reports/009a-navigation-route-ownership-hardening.md
```

Update Goal 009 report with immutable handoff:

```text
Commit: b6e893f6bb8abf26908e441ee79b92d6f910eb91
Parent: 6ecd8ba155e63a2dedeeafd65c1961fdb57bf261
Push/remote: exact
Navigation core: 38/38 ×3
Navigation performance: 1/1 ×2
Performance SHA-256:
D8B8BC902073847DF5C5E3AE28DE380540E43108C4B7420D778FE1659B71E377
Final verifier: 61/61 ×2
Verifier SHA-256:
E935FD5EA010BB968435FB7C3C8625AAC314F4D910B31B72D91A9CDDB28EDB96
Independent review: FIX_REQUIRED
Goal 009A: REQUIRED
Goal 010: BLOCKED
```

Review verdict:

```text
Goal 009 architecture direction: ACCEPT
Goal 009 commit: FIX_REQUIRED
Revert: NOT_REQUIRED
Goal 009A: REQUIRED
Goal 010: BLOCKED
Goal 011: NOT_STARTED
```

Roadmap progress only:

```text
Goal 009: FIX_REQUIRED
Goal 009A: IMPLEMENTED_PENDING_INDEPENDENT_REVIEW
Goal 010: NOT_STARTED / BLOCKED
Goal 011: NOT_STARTED
```

Do not rewrite future Goal architecture.

## 15. Commands

Pre-change:

```bat
ant verify
powershell -NoProfile -ExecutionPolicy Bypass -File tools\phantoms\verify-task-009.ps1
```

Targeted:

```bat
ant compile-tests
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
ant phantom-navigation-core-test
ant phantom-navigation-core-test
ant phantom-navigation-core-test

ant phantom-navigation-performance-smoke
ant phantom-navigation-performance-smoke

ant phantom-server-shutdown-handoff-test
ant phantom-server-shutdown-handoff-test
ant phantom-server-shutdown-handoff-test
```

Full:

```bat
ant verify
ant jar
powershell -NoProfile -ExecutionPolicy Bypass -File tools\phantoms\verify-task-009a.ps1
git diff --check
git status --short --branch
```

Post-commit:

```bat
git rev-parse HEAD
git show --stat --oneline --decorate HEAD
git diff --check b6e893f6bb8abf26908e441ee79b92d6f910eb91...HEAD
ant verify
ant jar
powershell -NoProfile -ExecutionPolicy Bypass -File tools\phantoms\verify-task-009a.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File tools\phantoms\verify-task-009a.ps1
git push origin feature/phantom-world
git ls-remote --heads origin feature/phantom-world
git status --short --branch
```

Compare final verifier outputs byte-for-byte/SHA-256 outside the repository.

## 16. Result and commit

Successful result:

```text
NAVIGATION_ROUTE_OWNERSHIP_HARDENED_PENDING_INDEPENDENT_REVIEW
```

Commit subject:

```text
fix(phantoms): harden navigation route ownership
```

One ordinary commit over `b6e893f6...`. Push regardless of SUCCESS/BLOCKED,
using only safe scoped artifacts.

## 17. Blocking behavior

Return `BLOCKED` if:

- computed segments cannot be validated without modifying GeoEngine/PathFinding;
- dispatch/STOPPING ordering requires a new executor or raw thread;
- shutdown navigation state cannot be exposed without changing lifecycle truth;
- Goal 010/schema/config changes are required;
- production DB is accessed;
- cumulative verify/jar fails.

On blocker, remove unsafe production edits, preserve safe tests/report/verifier,
ordinary commit/push and keep Goal 010 blocked.

## 18. Final handoff

```text
Status:
Architecture result:
Baseline:
Goal 009 review:
Preflight expired backend calls:
Preflight budget backend calls:
Computed segment validation:
Appended destination validation:
Route obstruction result:
Cancellation during validation:
Deadline during validation:
Initial route cache safety:
Dispatch/STOPPING ordering:
Accepted dispatch stop race:
Rejected dispatch stop race:
Worker ownership:
Navigation shutdown snapshot:
Final server diagnostic:
Navigation core:
Three core runs:
Navigation performance:
Two performance runs / summary SHA:
Shutdown handoff:
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
Goal 011:
Limitations/blockers:
```
