# Goal 023C context

## Baseline

```text
branch: feature/phantom-world
required parent: 041e23502e5701716bab77dbe73304dc375a157e
Goal 023 overall: CHANGES_REQUIRED
Goal 023B review: ACCEPT_WITH_REQUIRED_023C_ROUTE_FAILURE_CLOSURE
Goal 024+: NOT_STARTED
```

## Independent review of Goal 023B

Goal 023B successfully closes its two intended findings:

- `R023B-01 CLOSED`: planner-pending and persisted PLANNING/MOVING/REGROUPING route ownership now blocks content rebinding, duplicate route requests and premature READY.
- `R023B-02 CLOSED`: production `PhantomRiftService.evaluateManagedInvitation` refreshes exact current candidate eligibility and the canonical integration installs the actual Rift provider method reference.

Do not redesign those areas unless the new route failure fix directly requires a compatibility adjustment.

## New finding R023C-01

The remaining blocker is loss of terminal navigation failure semantics across:

```text
PhantomNavigationService
  -> PhantomPartyRouteCoordinator
  -> PhantomPartyCoordinator
  -> L2jPhantomRiftPartyPort
  -> PhantomRiftService
```

Current baseline collapses accepted asynchronous planning, rejected submission and completed-without-route outcomes to `Optional.empty()` / `PENDING`.

### Synchronous failure

`PhantomPartyRouteCoordinator.request(...)` returns empty for accepted async planning, `SubmissionStatus.REJECTED`, `SubmissionStatus.COMPLETED` with `result.route()==null`, and invalid request preconditions. `PhantomPartyCoordinator.requestRoute(...)` maps all empty results to `PENDING`; Rift maps `RouteActivity.NONE` to `PENDING`. Therefore service-not-running, profile-busy, queue backpressure, no-geodata, pathfinding-disabled, route-budget/deadline/backend failures can become unbounded OBSERVE_ROUTE retry.

### Asynchronous failure

`PhantomPartyRouteCoordinator.poll(...)` consumes a terminal navigation result, removes planner-pending state, writes `_routeByGroup` / `_routeDeadlines`, and only then builds a manifest. If `result.route()==null`, no manifest is produced but runtime route ownership remains. Subsequent poll cannot recover the consumed result, while `RouteActivity` can report NONE because persisted PartyState.route is null.

## User workflow rule

There is no artificial maximum file count. Use the pre-audited read/change set; additional High Five files are allowed only when the exact call path proves them necessary, and each such expansion must be explained in the report.
