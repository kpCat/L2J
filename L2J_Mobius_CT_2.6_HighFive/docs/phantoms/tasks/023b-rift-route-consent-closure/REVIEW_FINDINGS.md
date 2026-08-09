# Independent review findings — Goal 023A

Baseline under review:

```text
commit: 563752f6844076fdbaeb3be7c5cae979c757960a
parent: 840e159a989f6372da9c471c915413f1e4470daf
branch: feature/phantom-world
subject: fix(phantoms): harden rift recruitment integration
decision: CHANGES_REQUIRED
```

Goal 024+ remains `NOT_STARTED`.

The corrective Goal 023A substantially improved Goal 023. Preserve these results unless a direct fix requires touching them:

- exact pre-invite component/roster/source/candidate revalidation;
- `rift.preparation` schema v2 and explicit v1 migration/replan;
- full canonical invitation identity and canonical expiry observation;
- typed REFUSED / EXPIRED / CANCELLED / REJECTED mapping;
- `RIFT_INVITE_REQUEST` / `RIFT_INVITE_REFUSED` facts and Goal 020 mapping;
- Phantom-first local candidate discovery before the <=32 cap;
- Goal 018 relationship modifier with neutral fallback;
- expanded bounded Rift metrics;
- separation of `ENSURE_PARTY_BINDING` from external invite/route actions;
- preservation of an existing canonical party on a Rift invite refusal.

Two production blockers remain.

## R023B-01 — P1 — active shared route is not part of content-binding stability

At baseline `563752f...`:

1. `PhantomPartyCoordinator.persistRoute(...)` represents a route with a `PartyOperation`
   of kind `ROUTE` and phase `COMMITTED`, while `PartyState.route()` can still be
   `PLANNING`, `MOVING` or `REGROUPING`.
2. `pendingMembership(...)` treats `ROUTE` as conflicting only in
   `PREPARED`, `CANONICAL_PENDING` or `CANONICAL_OBSERVED`; therefore an active
   `ROUTE + COMMITTED` is considered non-conflicting.
3. `bindContentGoal(...)` can then adopt the same group and save a new
   `SUPPORT + COMMITTED` state with `route = null` for every Phantom member.
4. A route can also be in `PhantomPartyRouteCoordinator` planner-pending state before a
   `RouteManifest` has been persisted; this runtime ownership is invisible to
   the current content-binding result.
5. The old route coordinator may still own a navigation request, route identity
   or movement leases after the durable claim has been overwritten.

Consequences include loss of durable route identity, a second route being
requested over an already live route, movement leases becoming detached from
the new route identity, and `READY_TO_ENTER` being observed while Goal 017 still
owns route work.

Required correction:

- Goal 017 exposes one bounded read-only per-group route-activity/stability view
  including both planner-pending runtime ownership and persisted route status.
- PLANNING/MOVING/REGROUPING and unresolved planner ownership are non-stable for
  content rebinding and final READY.
- `bindContentGoal(...)` never silently clears or overwrites a non-terminal route
  and never cancels another active content route just to make Rift green.
- terminal ARRIVED/FAILED state is reconciled by Goal 017 ownership before/while
  publishing a new stable binding; no orphan navigation/movement ownership remains.
- a Rift route already requested by this exact preparation is observed, not duplicated.
- restart/shutdown cleanup guarantees remain intact.

## R023B-02 — P1 — production Rift managed-consent eligibility is not fully refreshed or end-to-end proven

Production composition installs:

```text
riftService::evaluateManagedInvitation
```

but the new real coordinator/canonical invitation integration test substitutes:

```text
ignored -> ManagedInvitationDecision.ACCEPT
```

Thus it proves the generic policy extension, not the production Rift provider.

`PhantomRiftService.evaluateManagedInvitation(...)` also uses the persisted
candidate receipt plus current requester readiness and a limited invitee refresh,
but does not refresh exact current candidate facts and rerun the missing-vacancy
RoleMatcher/readiness eligibility at the actual target-side response point.

The Goal 023A architecture contract required:

```text
ACCEPT only for the exact pending offer and still-eligible invitee
```

Required correction:

- refresh the exact current invitee candidate at managed-policy decision time;
- verify exact invitation/preparation/binding/roster/source identity, still-missing
  vacancy, local/perceptible eligibility, instance/party/alive state and current
  RoleMatcher capability evidence;
- use current invitee->leader relationship modifier;
- ACCEPT only if exact offer remains eligible now;
- REFUSE for explicit policy refusal; DEFER for transient/stale evidence where
  auto-accept is not justified;
- never create/replace invitee goal to manufacture consent;
- ordinary real players remain client-controlled;
- add a real integration case registering `riftService::evaluateManagedInvitation`,
  not an unconditional lambda;
- mutate candidate eligibility before the policy pulse and prove no stale auto-accept;
- prove REFUSE/DEFER and explicit conversation / `party.join` precedence.

## Review result

```text
Goal 023 baseline 840e159a989f6372da9c471c915413f1e4470daf:
CHANGES_REQUIRED

Goal 023A baseline 563752f6844076fdbaeb3be7c5cae979c757960a:
CHANGES_REQUIRED

Goal 024+:
NOT_STARTED
```
