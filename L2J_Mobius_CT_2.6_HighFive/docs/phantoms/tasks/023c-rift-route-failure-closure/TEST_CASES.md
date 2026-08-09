# Goal 023C test cases

Seed: `23002313`. Dynamic tests are mandatory.

## 1. Synchronous REJECTED
Exercise a real deterministic `PhantomNavigationService` rejection (for example SERVICE_NOT_RUNNING, PROFILE_BUSY or backpressure). Assert Goal017/Rift reports terminal/unavailable, not PENDING; route ownership remains zero; normal later replan is possible.

## 2. Synchronous COMPLETED with no route
Exercise NO_GEODATA, PATHFINDING_DISABLED, ROUTE_BUDGET_EXCEEDED, DEADLINE_EXPIRED or another deterministic terminal result with `result.route()==null`. Assert it reaches terminal failure and never enters indefinite PENDING.

## 3. Async accepted -> NO_PATH
While queued/in-flight assert PLANNING/PENDING. After terminal result: failure is observable, `_pending` is cleared, `_routeByGroup`/deadline/movement are not polluted, and a later legitimate replan can submit.

## 4. Async accepted -> BACKEND_FAILURE
Repeat ownership and terminal assertions for a distinct async terminal failure.

## 5. Immediate usable route regression
COMPLETED with a valid route still creates and persists exactly one RouteManifest.

## 6. Async successful route regression
ACCEPTED -> PATH_FOUND -> MOVING/REGROUPING -> ARRIVED remains compatible with Goal 023B.

## 7. Rift failure escape
Drive NEEDS_TRAVEL and prove both request-time and observe-time terminal no-route failure return to EVALUATE_READINESS/replan rather than remaining in OBSERVE_ROUTE with zero route hash. No same-pulse resubmit.

## 8. Existing regressions
Run current Goal023/023A/023B, Party route/recovery/lifecycle, relevant Navigation core/failure/cancellation, and Goal017/020 affected targets. Use exact current target names from build.xml and record mappings.

## 9. Lifecycle
After every injected failure, exact route/navigation ownership for that attempt returns to zero. Final shutdown drains cleanly.
