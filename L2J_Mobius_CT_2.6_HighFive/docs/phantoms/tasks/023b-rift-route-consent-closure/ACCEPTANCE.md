# Goal 023B acceptance

Every production gate below is mandatory for SUCCESS.

## A. Baseline/status

- exact parent `563752f6844076fdbaeb3be7c5cae979c757960a`;
- branch `feature/phantom-world`;
- Goal 023A review records `CHANGES_REQUIRED` with R023B-01/R023B-02;
- Goal 024+ remains `NOT_STARTED`.

## B. Route ownership/binding

- binding sees planner-pending route ownership, not only PartyOperation phase;
- binding sees persisted RouteManifest status;
- PLANNING/MOVING/REGROUPING cannot be overwritten to `route=null`;
- unrelated live route makes binding PENDING/CONFLICT without a new route request;
- exact already-live Rift route is observed, not duplicated;
- ARRIVED/FAILED is reconciled through Goal017 ownership before a new stable binding;
- no orphan navigation/movement ownership after terminal reconciliation;
- group/generation/membership identity is unchanged unless canonical membership changed;
- READY is impossible while route planning/movement ownership remains live.

## C. Managed Phantom consent

- production `PhantomRiftService.evaluateManagedInvitation` refreshes current
  invitee eligibility for the exact missing vacancy;
- ACCEPT requires exact current RoleMatcher/capability/local/instance/party/alive evidence;
- stale/unavailable evidence cannot auto-ACCEPT;
- REFUSE and DEFER are dynamically exercised;
- current invitee->leader relationship policy is used;
- explicit conversation and exact `party.join` precedence remains intact;
- no candidate goal is manufactured by leader;
- ordinary real player remains client-controlled.

## D. End-to-end proof

At least one test dynamically uses:

```text
PhantomPartyCoordinator
L2jPhantomRiftPartyPort
PhantomRiftService
riftService::evaluateManagedInvitation
PartyInvitationService
canonical Player membership
```

The registered provider is the real Rift service, not `ignored -> ACCEPT`.

Prove:

- eligible managed Phantom reaches canonical ACCEPT;
- stale eligibility before policy pulse does not reach canonical ACCEPT;
- negative/explicit refusal reaches canonical REFUSED;
- DEFER leaves the exact invitation pending;
- retry/reconcile does not duplicate invitation.

## E. Dynamic route proof

Prove dynamically, not by source-token assertions:

1. planner-pending Goal017 route -> Rift bind does not overwrite or issue second route;
2. persisted MOVING route -> bind preserves manifest/ownership and blocks READY;
3. REGROUPING same;
4. ARRIVED terminal cleanup -> stable bind can later succeed;
5. FAILED terminal cleanup -> safe replan/bind can later succeed;
6. Rift's own route requested exactly once and observed to terminal;
7. READY only after route activity/ownership is clean.

## F. Preserve 023A

Regression remains green for:

- exact pre-invite revalidation;
- schema v2/v1 migration;
- canonical expiry/terminal typing;
- semantic facts;
- Phantom-first discovery;
- Goal018 ranking;
- Goal017/020 affected suites.

## G. Safety

- no other chronicle changes;
- no `.l2j` change/add/delete;
- no production DB;
- no SQL migration;
- no Rift entry/item consume/teleport/room jump/spawn/combat;
- no fake GameClient;
- no global online-player scan;
- no new worker/thread/executor/Future/task/timer;
- `Player.REQUEST_TIMEOUT` unchanged.

## H. Verification

After focused development and freeze:

1. final Goal023B aggregate once;
2. historical verifier 023;
3. historical verifier 023A;
4. working-tree verifier 023B;
5. one plain `ant verify`;
6. one `ant jar`;
7. ordinary commit/push;
8. post-commit verifier 023B in PS5.1 and verified available PS7 with byte-identical stdout.

A second full verify is allowed only after a real relevant correction to a
failed first run.

## Success token

```text
GOAL_023B_RIFT_ROUTE_CONSENT_CLOSURE_IMPLEMENTED_PENDING_INDEPENDENT_REVIEW
```

This token is not self-ACCEPT.
