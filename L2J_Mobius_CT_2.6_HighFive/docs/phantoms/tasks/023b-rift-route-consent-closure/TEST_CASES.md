# Goal 023B test cases

Seed:

```text
23002312
```

## 1. rift023b-route-planning-binding

Create canonical Party/Goal017 group and actual route-coordinator planning
ownership before a manifest is persisted.

Assert:

- binding reports non-stable;
- PartyState is not rewritten to Rift SUPPORT;
- no second navigation request;
- original route ownership survives;
- after terminal reconciliation a later bind can proceed.

## 2. rift023b-route-moving-binding

Persist Goal017 route with `RouteStatus.MOVING` and
`OperationKind.ROUTE + COMMITTED`.

Assert:

- COMMITTED does not mean route-terminal;
- content binding does not clear PartyState.route;
- no new route;
- movement ownership remains attached;
- READY blocked.

Repeat essential assertions for REGROUPING.

## 3. rift023b-route-terminal-reconciliation

For ARRIVED and FAILED:

- cleanup is Goal017-owned;
- afterward no planner/navigation/movement ownership remains;
- Party membership is unchanged;
- content binding then succeeds with exact identity.

## 4. rift023b-rift-route-once

From stable Rift binding + NEEDS_TRAVEL:

- request exactly one shared route;
- retries/pulses do not submit another;
- observe route until terminal;
- refresh/reconcile binding if manifest changed;
- READY only after route ownership is clean.

## 5. rift023b-production-managed-accept

Use real materialized/headless Players, real coordinator, real Rift port,
actual `PhantomRiftService`, canonical `PartyInvitationService`.

Register actual:

```text
riftService::evaluateManagedInvitation
```

Prepare exact eligible candidate and assert canonical Party membership occurs
with one invitation identity.

## 6. rift023b-production-managed-stale

After invitation publication but before policy pulse invalidate current:

- capability/class evidence, or
- local/instance eligibility.

Assert no auto-ACCEPT and no forged membership. Exercise dead invitee as an
explicit refusal case.

## 7. rift023b-production-managed-relationship

Provide current invitee->leader Goal018 evidence below existing refusal threshold.

Assert:

- actual provider REFUSE;
- canonical invitation terminal REFUSED;
- leader's existing Party remains intact;
- Rift preparation observes typed refusal/cooldown.

Neutral/unavailable social evidence stays neutral.

## 8. rift023b-production-managed-defer

Make required current evidence transiently unavailable without explicit refusal.

Assert:

- provider DEFER;
- invitation remains pending;
- later canonical expiry is EXPIRED, not REFUSED;
- no duplicate invitation.

## 9. rift023b-explicit-consent-precedence

Regression:

- exact active party.join consent still works;
- conversation-owned exact accept/refuse unchanged;
- content provider does not override explicit response;
- real Player remains client-controlled.

## 10. restart/shutdown

Exercise recovery around terminal route reconciliation and pending Rift invite.

Shutdown snapshot must show no dangling route/navigation/movement ownership.

## Affected regressions

Run current exact targets corresponding to:

```text
phantom-party-route-test
phantom-party-state-recovery-test
phantom-party-lifecycle-test
phantom-party-server-integration-test
phantom-rift-goal023-test
phantom-rift-goal023a-test
phantom-conversation-party-actions-test
phantom-conversation-query-execution-test
```

If an exact current target name differs, use the current `build.xml` name and
record the mapping; do not substitute a weaker unrelated test.
