# Goal 023B architecture contract

## 1. Ownership

Keep the existing ownership chain:

```text
Rift readiness / rift.preparation
  -> Goal 017 content binding and invitation/route seams
  -> canonical PartyInvitationService / Party
  -> existing navigation/combat movement ownership
```

Goal 023B does not add a second Party, invitation, navigation or scheduler kernel.

## 2. Route-aware content binding

Goal 017 content binding must include route ownership in its definition of stability.

Add or reuse a bounded typed per-group query, conceptually:

```text
RouteActivity observeGroupRoute(String groupId)
```

Equivalent naming/shape is allowed. It must distinguish enough states to enforce:

```text
NONE
PLANNING
MOVING
REGROUPING
ARRIVED
FAILED
```

and retain exact route identity/destination where available.

The view must account for both:

1. runtime planner ownership before a manifest is persisted;
2. persisted `PartyState.route()` plus live route/movement ownership after the manifest exists.

### Binding rules

- no route activity -> binding may proceed;
- unrelated PLANNING/MOVING/REGROUPING -> binding returns typed PENDING/CONFLICT
  and makes no PartyState rewrite;
- exact Rift-owned route already in progress -> do not issue a second route;
  observation may continue, but final READY stability is false while route work remains live;
- ARRIVED/FAILED -> Goal 017 performs bounded terminal reconciliation/cleanup
  before publishing a new stable content binding;
- no code may obtain stability simply by assigning `route = null`;
- no active navigation/movement lease may be orphaned by binding rewrite;
- route cleanup/cancel remains Goal 017 / route-coordinator owned.

`ContentBindingResult` / Rift `PartyBinding` may be extended if exact route
activity/evidence must cross the port.

## 3. Rift route flow

Required semantics:

```text
EVALUATE_READINESS
  -> ENSURE_PARTY_BINDING
  -> REQUEST_PARTY_ROUTE
  -> OBSERVE_ROUTE
  -> route terminal reconciliation
  -> refresh exact binding when its manifest/route identity changed
  -> DECLARE_READY
```

Existing stages may remain if they implement the same durable transitions.

A new route request requires:

- exact current rift.prepare goal;
- current canonical leader/roster/source evidence;
- exact stable party binding;
- no pre-existing unrelated live route;
- no second request if the exact Rift route already exists.

Final READY requires:

- current readiness READY;
- no canonical pending invitation;
- no JOIN/LEAVE/EXPEL/TRANSFER conflict;
- no route planner request;
- no MOVING/REGROUPING route;
- no outstanding route movement/navigation ownership;
- exact current binding/source/roster evidence.

No Rift entry/teleport/item-consumption side effect is added.

## 4. Production managed invitation policy

The Goal 017 policy registry added in 023A remains target-side response owner.

The Rift provider must decide from **current** facts at the actual policy pulse.

Resolve:

```text
exact InvitationIdentity
requester/invitee identities
exact current leader Rift preparation
current party binding
current canonical requester roster
current missing vacancy
current invitee candidate facts
current invitee party/instance/location/dead state
current RoleMatcher/capability eligibility
current invitee goal
current invitee->leader relationship modifier
canonical invitation still pending
```

The pre-invite CandidateReceipt is an offer/evidence binding, not a substitute
for current eligibility.

Suggested decisions:

```text
ACCEPT
  exact invitation/preparation/binding/vacancy still current
  and invitee is still locally eligible now
  and no explicit refusal condition applies

REFUSE
  exact explicit/durable refusal condition

DEFER
  transient stale/unavailable evidence where auto-ACCEPT is unjustified

UNSUPPORTED
  provider does not own the offer
```

Preserve precedence:

1. explicit conversation-owned exact response;
2. exact active `party.join` consent/refusal handling;
3. content provider policy;
4. default no auto-accept.

Never replace/create another Phantom's goal to force ACCEPT.

## 5. Acceptance integration

At least one dynamic test must connect:

```text
real materialized/headless canonical Player(s)
+ real PhantomPartyCoordinator
+ real L2jPhantomRiftPartyPort
+ real PhantomRiftService
+ PhantomRiftService.evaluateManagedInvitation
+ canonical PartyInvitationService
```

Registration must use the actual service provider:

```text
installManagedInvitationPolicy(
    PhantomRiftService.GOAL_TYPE,
    riftService::evaluateManagedInvitation)
```

or exact equivalent. `ignored -> ACCEPT` is not acceptance evidence.

Dynamic branches:

- eligible Phantom -> provider ACCEPT -> canonical membership;
- capability/class/instance/local eligibility invalidated after invite but before
  policy pulse -> no automatic ACCEPT;
- negative relationship / explicit refusal -> REFUSE;
- transient unavailable evidence -> DEFER and exact invitation remains pending;
- ordinary real player remains client-controlled;
- no duplicate invite.

The fixture may implement bounded read-only Rift facts for deterministic setup,
but the service under test must be production `PhantomRiftService` and
membership mutation must use the real coordinator/canonical invitation service.

## 6. Restart and cleanup

- restart never reconstructs an active route from guessed data;
- persisted route identity and route-coordinator runtime ownership cannot silently diverge;
- stale route/binding -> re-evaluate without external mutation in that transition;
- shutdown leaves zero planner/navigation/movement ownership;
- pending invitation recovery remains exact/idempotent.

## 7. Frozen accepted 023A areas

Do not redesign unless directly necessary:

- factual Rift catalog;
- policy structure except strictly necessary existing values;
- schema-v2 format and v1 decoding;
- candidate source ordering;
- Goal 018 relationship query;
- Goal 020 semantic mapping;
- pre-invite exact revalidation;
- canonical expiry/terminal typing;
- metrics families.

## 8. Forbidden shortcuts

- no arbitrary file-count ceiling;
- no broad repository/chronicle scan;
- no treating `ROUTE + COMMITTED` alone as route-terminal;
- no clearing a live route to manufacture stability;
- no unconditional managed Phantom ACCEPT;
- no policy stub as sole acceptance proof;
- no worker/thread/executor/Future/task/timer;
- no fake GameClient or request-packet invocation;
- no global `World.getPlayers()` scan;
- no direct Party membership mutation from Rift;
- no Rift start/item consume/teleport/room jump/spawn/combat;
- no production DB;
- no geodata changes.
