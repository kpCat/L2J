# Goal 023C architecture contract

## 1. Principle

Close one information-loss seam only:

```text
Navigation submission/result -> Goal017 shared route -> Rift route observation
```

Do not redesign navigation, Party routing, managed consent, schema v2 or recruitment.

## 2. Typed route attempt semantics

Replace or supplement ambiguous `Optional<RouteManifest>` boundaries with a bounded typed result. Equivalent names are allowed, but semantics must distinguish at least:

```text
PENDING
READY
FAILED
REJECTED / UNAVAILABLE
NONE
```

Carry exact route identity/generation/destination and a typed reason/status when applicable. Do not infer terminal outcomes from exception text.

### Submission mapping

```text
ACCEPTED -> PENDING with exact planner ownership
COMPLETED + usable route -> READY
COMPLETED + no usable route -> terminal failure
REJECTED -> terminal rejection/unavailable
```

Invalid route preconditions are terminal/unavailable, not invented PENDING.

## 3. Async poll mapping

`poll(...)` must not write `_routeByGroup` or `_routeDeadlines` until a usable RouteManifest exists. If an accepted async request completes without a usable route, remove planner ownership, do not create hidden route ownership, and publish enough bounded typed terminal evidence for Goal017 to observe the failure exactly/idempotently. A future normal replan must not be blocked by stale route ownership.

A small bounded per-group terminal receipt is allowed if needed; it must be cleared deterministically and bounded by existing active-group limits.

## 4. Goal017 coordinator mapping

`PhantomPartyCoordinator.requestRoute(...)` must distinguish actual pending, immediate usable route, terminal submission failure and not-running/unavailable. `advanceRoute(...)` must distinguish no terminal result yet, usable route from poll and terminal no-route result. On terminal failure: no Party membership mutation, no hidden route owner, typed failed route/operation evidence, and no same-pulse resubmit.

## 5. Rift mapping

`L2jPhantomRiftPartyPort` maps terminal route failure to `FAILED` or `REJECTED`, not PENDING. `RouteActivity.NONE` means no ownership and may not itself mean pending. `PhantomRiftService.requestRoute/observeRoute` must exit to normal evaluation/replan on terminal failure/stale vanished identity. No tight resend loop.

## 6. Preserve Goal 023B behavior

Planner-pending remains exact PENDING; MOVING/REGROUPING remain live; ARRIVED/FAILED manifest terminal cleanup remains Goal017-owned; duplicate route requests remain suppressed; final READY requires clean stable binding; managed-consent behavior is unchanged.

## 7. Safety

No new worker/thread/executor/Future/task/timer, no global player scan, production DB, SQL, fake GameClient, Rift entry/item/teleport/combat, other chronicles or `.l2j`.
