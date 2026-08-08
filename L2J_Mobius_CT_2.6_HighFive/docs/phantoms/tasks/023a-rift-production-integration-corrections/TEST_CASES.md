# Goal 023A mandatory test cases

## General harness rules

- Seed only `23002311`.
- Existing Goal 023 eight focused modes remain green.
- Pure codec/scoring units may use fakes.
- Acceptance scenarios for party binding, managed consent, canonical timeout and route handoff must instantiate real `PhantomPartyCoordinator` and production `L2jPhantomRiftPartyPort`.
- At least one invitation lifecycle scenario must pass through canonical `PartyInvitationService` and existing headless/canonical Player harness. No fake `GameClient`.
- Do not mock the method whose integration is under test.
- Production DB is forbidden; DB-backed component tests use only `l2jmobiush5_phantom_test`.

## Required launcher modes

```text
rift023a-party-binding
rift023a-managed-consent
rift023a-preinvite-revalidation
rift023a-invitation-authority
rift023a-restart-migration
rift023a-semantic-facts
rift023a-candidate-ordering
rift023a-route-binding
rift023a-performance
```

One aggregate Ant target:

```text
phantom-rift-goal023a-test
```

One affected target:

```text
phantom-rift-goal023a-affected-test
```

## 1. `rift023a-party-binding`

### B01 — Existing committed party switches to Rift

Arrange:

- canonical live Party with Phantom leader + Phantom/real members;
- matching committed Goal 017 LEADER/MEMBER claims from a previous non-Rift goal;
- active exact `rift.prepare` goal;
- one mandatory vacancy missing.

Assert:

- content binding succeeds;
- canonical Party object and membership are unchanged;
- same group ID/generation unless canonical leader/membership actually changed;
- objective/requirements and exact Rift goal identity are bound;
- next pulse may request one invite;
- no `CLAIM_EXISTS` loop.

### B02 — No claim but existing mixed Party

- live mixed Party is adopted/reconciled;
- real members are retained;
- no new Party is created;
- leader remains canonical.

### B03 — SOLO claim plus live Party

- exact reconciliation succeeds once;
- replay is idempotent.

### B04 — Conflicting claim

- candidate/leader has incompatible pending membership operation or mismatched canonical party;
- binding returns typed conflict;
- no claim is silently overwritten;
- no invite/route.

### B05 — One external mutation per stage

Instrument Goal 017 commands. One `advance(...)` may not call both bind/form and invite, or invite and route.

## 2. `rift023a-managed-consent`

### C01 — Eligible Phantom target accepts by target-side policy

- exact content offer and canonical invitation;
- invitee remains eligible and policy returns ACCEPT;
- canonical response commits membership;
- both party claims reconcile;
- leader Rift state observes ACCEPTED and reevaluates roster.

### C02 — Phantom target refuses

- target-side policy returns REFUSE;
- canonical roster unchanged;
- typed refusal receipt/cooldown stored;
- next candidate is selected only after re-evaluation.

### C03 — Phantom target defers

- DEFER does not auto-accept;
- invitation remains pending until explicit response/expiry;
- no resend.

### C04 — Explicit join/conversation precedence

- existing exact explicit `party.join` consent still works;
- exact conversation refusal works;
- incompatible explicit join target cannot be overridden by Rift policy.

### C05 — Ordinary real Player

- exactly one canonical invitation is delivered;
- Rift policy never calls canonical ACCEPT for real Player;
- only real response/expiry changes terminal state.

### C06 — No blanket managed auto-accept

- managed Phantom with conflicting goal, another party, dead/unready or stale offer is not accepted.

## 3. `rift023a-preinvite-revalidation`

For each mutation between SELECT_CANDIDATE and REQUEST_INVITE assert zero canonical invite and zero attempt/cooldown mutation:

```text
candidate dies
candidate moves to another instance/outside perceptible range
candidate joins another party
candidate class/capability no longer fills vacancy
candidate gets incompatible Goal 017 claim
vacancy is filled by someone else
leader changes
party becomes full
catalog hash changes
policy/config/role hash changes
goal ID/revision changes
```

Then prove a fresh unchanged candidate receives exactly one invite.

## 4. `rift023a-invitation-authority`

### I01 — Full identity

Persist and restore sequence + requester object ID + invitee object ID + canonical expiry. A matching sequence with different participants is STALE, never the same invitation.

### I02 — Canonical 15-second authority

- policy default 15000;
- effective deadline does not exceed canonical invitation expiry;
- no change to `Player.REQUEST_TIMEOUT`;
- no hardcoded Rift 30-second assumption.

### I03 — Typed terminal mapping

Assert distinct:

```text
ACCEPTED
REFUSED
EXPIRED/TIMED_OUT
CANCELLED
REJECTED
NONE/STALE
```

Canonical `party.invite.expired` must map to expiry, not refusal.

### I04 — Save conflict after canonical invite

Inject Rift component optimistic conflict after Goal 017 accepted the request. Replay must recover the same exact invitation/operation and never deliver a duplicate.

## 5. `rift023a-restart-migration`

### R01 — Decode schema v1

- existing v1 payload decodes without startup failure;
- it is marked operationally untrusted;
- next advance rebuilds from live canonical facts and saves v2 before mutation.

### R02 — Restart states

Restart separately at:

```text
existing party bound
candidate selected
invite request accepted before Rift receipt save
invite pending
invite accepted before roster refresh
refusal/expiry recorded
route pending
ready
```

Assert no duplicate invite, no stale candidate use, canonical roster wins.

### R03 — Source drift

Change catalog/policy/config/role hash across restart. Pending/selected action is quarantined or cleared and re-planned; no invite before fresh evidence.

### R04 — Binding drift

Change group/generation/leader/membership. Old route/invite/READY receipt cannot be reused.

### R05 — Bounds

- payload <=4096 bytes;
- refusal history capped at 32;
- attempts capped at 32;
- unknown future schema fails closed.

## 6. `rift023a-semantic-facts`

- exact pending invite yields `RIFT_PREP_STATUS=INVITE_PENDING` and `RIFT_INVITE_REQUEST`;
- exact refusal and exact expiry yield `RIFT_INVITE_REFUSED` with distinct reason keys;
- Goal 020 production adapter maps both fact types without phrase bank;
- roster/source/goal/binding mutation removes stale pending/refusal fact;
- current missing-role answer comes from a freshly recomputed RoleMatchResult;
- READY fact requires stable no-conflict party binding.

## 7. `rift023a-candidate-ordering`

### O01 — Source order under cap

Visible set:

- at least 32 ordinary real Players with lower object IDs;
- one eligible managed Phantom with higher object ID;
- policy limit 32.

Assert managed Phantom is evaluated before ordinary real candidates and can be selected.

### O02 — Stable deterministic order

Repeat with shuffled source iteration; output order/ranking and evidence hash are byte-identical.

### O03 — Bound

- no more than 32 `memberFacts` evaluations;
- no `World.getPlayers()`;
- no DB/profile full scan;
- noneligible candidates are rejected with bounded reason family metrics.

### O04 — Relationship modifier

- exact Goal 018 relationship snapshot changes ranking deterministically;
- social service unavailable/stale gives neutral 0 with typed evidence, not guessed value.

## 8. `rift023a-route-binding`

### T01 — Composition-ready existing Party without prior Rift claim

- content binding stage adopts/reconciles party;
- next stage requests one shared Goal 017 route;
- no direct teleport.

### T02 — Existing matching committed claim

- route uses same exact binding group/generation;
- no `NOT_PHANTOM_LEADER` loop.

### T03 — Pending invite/conflicting operation

- route request is suppressed;
- READY is suppressed.

### T04 — Arrival/failure

- exact canonical arrival -> final DECLARE_READY revalidation -> READY;
- route failed -> NEEDS_TRAVEL/BLOCKED;
- no Rift entry side effect.

## 9. `rift023a-performance`

Mandatory structural proof:

```text
100000 content-binding read/idempotency checks
100000 9-member readiness evaluations
10000 exact pre-invite revalidations
10000 bounded candidate discoveries <=32
10000 v1/v2/restart reconciliation checks
10000 semantic latest-fact evaluations
```

Assert:

- no XML parse in a decision pulse;
- no NPC/class/full World player scan;
- no production DB access;
- no unbounded metric labels/logs;
- maximum observed candidate/member/claim operations match configured bounds.

## Required regressions

Run exact existing targets:

```text
ant phantom-rift-goal023-test
ant phantom-party-canonical-invitation-test
ant phantom-party-state-recovery-test
ant phantom-party-role-vacancy-test
ant phantom-party-route-test
ant phantom-party-lifecycle-test
ant phantom-party-server-integration-test
ant phantom-conversation-party-actions-test
ant phantom-conversation-query-execution-test
```

`phantom-rift-goal023-test` must run in historical/descendant-compatible mode for verifier 023 and must not rewrite the accepted evidence of commit `840e159a...`. Concretely, remove `-WorkingTree` from the existing Ant target `phantom-static-verify-023`; leave `verify-task-023.ps1` pinned to its original parent/subject and let its historical mode select the first child.
