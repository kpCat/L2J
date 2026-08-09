# Goal 023B independent review / Goal 023C finding

Reviewed baseline:

```text
commit: 041e23502e5701716bab77dbe73304dc375a157e
parent: 563752f6844076fdbaeb3be7c5cae979c757960a
subject: fix(phantoms): close rift route and consent gaps
branch: feature/phantom-world
```

## Result for Goal 023B

```text
R023B-01: CLOSED
R023B-02: CLOSED
Goal 023B: ACCEPT_WITH_REQUIRED_023C_ROUTE_FAILURE_CLOSURE
Goal 023 overall: CHANGES_REQUIRED
Goal 024+: NOT_STARTED
```

## R023C-01 — P1 — terminal navigation failures collapse into Rift PENDING

### Evidence A: synchronous terminal/no-route submission

`PhantomNavigationService.submit(...)` distinguishes `ACCEPTED`, `COMPLETED`, and `REJECTED`. A COMPLETED result may still have no route. `PhantomPartyRouteCoordinator.request(...)` exposes only `Optional<RouteManifest>` and therefore loses the distinction between accepted async planning and terminal/no-route outcomes. Goal017 then reports PENDING, and Rift can enter OBSERVE_ROUTE indefinitely.

### Evidence B: asynchronous terminal failure

For accepted async navigation later ending as `NO_PATH`, `BACKEND_FAILURE`, `ROUTE_BUDGET_EXCEEDED`, timeout/cancel or another no-route terminal result, `poll(...)` currently writes `_routeByGroup` / `_routeDeadlines` before checking whether a RouteManifest exists. If manifest construction returns empty, planner state is gone, the navigation result is consumed, no durable route exists, runtime ownership may remain, and future route requests can be blocked.

### Required invariant

At every layer distinguish:

```text
PENDING / PLANNING
READY_ROUTE
TERMINAL_FAILURE
NO_ROUTE_OWNERSHIP
```

No terminal navigation result may be represented as indefinite PENDING. No runtime route identity may remain hidden when there is no usable/persisted route.
