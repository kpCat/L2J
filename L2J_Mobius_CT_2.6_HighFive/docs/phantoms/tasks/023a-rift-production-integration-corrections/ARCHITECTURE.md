# Goal 023A architecture contract

## 1. Principle

Goal 017 remains the only owner of durable party coordination and canonical invitation/membership mutations. Goal 023 remains a content-specific planner/readiness owner. Goal 023A adds narrow seams; it does not add a second party kernel.

Ownership chain:

```text
rift.preparation v2
  -> exact Goal 017 content binding / managed invite operation
  -> canonical PartyInvitationService / Party
```

## 2. Content-party binding seam

Add one narrow, typed Goal 017 seam. Suggested name:

```text
bindContentGoal(...)
```

Equivalent naming is allowed, but behavior is mandatory.

Inputs include:

```text
leader profile ID
exact active goal ID/revision/type = rift.prepare
objective mode/ref = exact rift.tier
current role requirements
expected canonical roster evidence
```

Return a typed immutable result containing at least:

```text
outcome
party group ID
group generation
membership revision
leader MemberRef
canonical roster/manifest evidence
operation stability/conflict state
reason key
```

Required cases:

1. `no claim + solo`: create/reconcile the ordinary Goal 017 forming/binding state without a canonical membership mutation;
2. `no claim + existing live Party`: adopt/reconcile current canonical Party, including mixed real/Phantom roster;
3. `SOLO claim + current Party`: replace only after exact canonical observation;
4. `matching committed LEADER/MEMBER claims`: preserve membership/group identity, bind current Rift objective/requirements and exact Rift goal identity;
5. `conflicting claim, pending membership mutation, mismatched canonical roster/leader`: fail closed with typed reason.

Invariants:

- binding never starts a Party, sends an invite, removes/adds a member or transfers leader;
- binding never rewrites another active content goal;
- committed matching group generation is not incremented without an actual canonical leadership/membership change;
- all managed claims for the same canonical party remain manifest-consistent;
- active `rift.prepare` remains the leader's goal; do not transition it to `party.lead`;
- route and invite use the exact returned binding identity;
- do not treat arbitrary `CLAIM_EXISTS` as success.

## 3. Decision stages

Corrected state machine must separate party binding from external actions:

```text
DISCOVER_CONTENT
SNAPSHOT_ROSTER
EVALUATE_READINESS
SELECT_CANDIDATE
ENSURE_PARTY_BINDING
REQUEST_INVITE
OBSERVE_INVITE
REQUEST_PARTY_ROUTE
OBSERVE_ROUTE
DECLARE_READY
```

Rules:

- no `bind/form + invite` in one `advance(...)` call;
- no `invite + route` in one call;
- one external mutation at most per call;
- optimistic save of the Rift receipt for that external mutation is permitted, but save conflict must reconcile the exact Goal 017 operation and must not reissue a second invite;
- idempotent replay of REQUEST_INVITE must return the same exact operation/invitation identity or a terminal observation.

## 4. Target-side managed Phantom consent

Add a general Goal 017 managed-invitation policy extension with default `DEFER`/no-op. Suggested contract:

```text
ManagedInvitationDecision evaluate(ManagedInvitationContext context)

ACCEPT
REFUSE
DEFER
UNSUPPORTED
```

The Rift-specific provider is read-only until Goal 017 performs the canonical response.

Context must contain exact immutable data:

```text
full InvitationIdentity
requester/invitee MemberRef
content objective/binding identity
selected vacancy and candidate evidence
current invitee goal/party claim
current invitee MemberFacts/readiness
current relationship modifier result when available
canonical expiry
```

Rules:

- decision is made from invitee-side facts, not forced by the leader;
- ordinary real Player never uses this provider and remains client-controlled;
- explicit conversation response and exact explicit active `party.join` consent remain valid; define deterministic precedence and test it;
- leader code must not blindly replace/create another Phantom's goal merely to force ACCEPT;
- ACCEPT only for the exact pending offer and still-eligible invitee;
- REFUSE records exact terminal reason; DEFER leaves canonical invitation pending;
- no default “all managed Phantoms accept” behavior;
- no packet invocation and no fake GameClient;
- policy registration/lifecycle is bounded and shutdown-safe; no worker/timer.

A simple implementation may keep this provider in Goal 017 and install the Rift policy during `PhantomSystem` composition. Do not create a Rift-owned invitation service.

## 5. `rift.preparation` schema v2

Preferred typed shape (equivalent compact representation allowed):

```text
PreparationV2
  leaderProfileId
  goalId / goalRevision / tier
  stage / status
  PartyBindingReceipt
    groupId
    groupGeneration
    membershipRevision
    leader MemberRef
    rosterHash / manifestHash
    stableOperationState
  SourceReceipt
    catalogHash
    policyHash
    configHash
    roleCatalogHash
  CandidateReceipt
    vacancyKey
    candidate MemberRef
    candidateEvidenceHash
    selectedRosterHash
    relationshipEvidenceHash or ZERO_HASH
  PendingInvitationReceipt
    sequence
    requesterObjectId
    inviteeObjectId
    requestedAt
    canonicalExpiresAt
    status
  refusal history <=32
  attempts <=32
  route readiness/hash
  updated logical time
  legacyUntrusted flag only during v1 migration
```

Bounds remain:

```text
payload <=4096 bytes
one component per leader
refusals <=32
attempts <=32
```

### v1 compatibility

- decoder must read existing schema v1 payloads;
- no startup crash and no SQL migration;
- v1 pending candidate/invitation/route receipts are insufficiently exact and must be marked untrusted;
- before any external mutation, v1 state is reset/replanned from live canonical facts and saved as v2;
- v1 READY may be re-evaluated, not blindly trusted;
- unknown future schema fails closed.

## 6. Exact pre-invite revalidation

Immediately before the one Goal 017 invite command:

1. reload exact Rift component row/version;
2. verify exact active `rift.prepare` goal ID/revision/tier;
3. reload/verify current content party binding;
4. rebuild canonical live roster and leader;
5. verify not full and no pending/conflicting party operation;
6. rerun RoleMatcher and verify the same mandatory vacancy is still missing;
7. refresh candidate through production backend;
8. verify perceptibility/local bound, instance/location, alive/vitals, current party membership, capability/role score, cooldown, relationship policy and incompatible Goal 017 claim;
9. compare catalog/policy/config/role hashes;
10. issue exactly one idempotent Goal 017 invite.

Any mismatch:

```text
no canonical invite
no attempt increment
no cooldown record
clear stale candidate receipt
return typed retry/replan reason
```

## 7. Canonical timeout and terminal typing

- Do not change `Player.REQUEST_TIMEOUT`.
- Policy default becomes 15000 ms.
- Effective deadline is `min(policy deadline, exact canonical invitation expiry)`.
- Persist full invitation identity and canonical expiry.
- Do not infer terminal type from a substring in failure text.
- Preserve exact typed outcomes:

```text
PENDING
ACCEPTED
REFUSED
EXPIRED (or TIMED_OUT, one canonical spelling)
CANCELLED
REJECTED
NONE/STALE
```

REFUSED and EXPIRED create distinct durable reason keys and metrics.

## 8. Party stability and READY

`READY_TO_ENTER` additionally requires an exact stable content party binding:

```text
no pending JOIN/LEAVE/EXPEL/TRANSFER/ROUTE conflict
binding group/generation still exact
canonical roster/leader still exact
source hashes still exact
```

`DECLARE_READY` must perform the final read-only revalidation. Do not complete the goal merely because a previous saved status equals READY if the intended final stage has not revalidated current binding/roster/source evidence.

No Rift entry side effects are added.

## 9. Candidate source order and relation score

Production discovery:

1. collect only leader-visible/local/perceptible Players in current instance;
2. resolve managed identity in bounded form;
3. order managed Phantom candidates first;
4. ordinary real candidates second;
5. stable identity tie-break inside source class;
6. evaluate no more than policy limit, max 32.

Do not sort/score an unbounded global online-player set. No `World.getPlayers()`.

Ranking tuple:

```text
mandatory vacancy priority
RoleMatcher score
current readiness
travel proximity/feasibility
Goal 018 relationship/reputation modifier when exact query is available
source preference
stable identity
```

Relationship failure modes (`not running`, `not found`, `authority stale`) are typed and fail-neutral with modifier 0; do not invent a relationship value.

## 10. Semantic facts

`latest(...)` combines fresh canonical readiness with persisted receipts only when goal, binding, roster and source identity remain exact.

Required facts:

```text
RIFT_PREP_STATUS
RIFT_MISSING_ROLE
RIFT_MEMBER_NOT_READY
RIFT_INVITE_REQUEST
RIFT_INVITE_REFUSED
RIFT_PARTY_FULL
RIFT_READY
```

During exact pending invite:

```text
status = INVITE_PENDING
RIFT_INVITE_REQUEST includes tier, vacancy, candidate character ID, party size
```

After exact refusal/expiry:

```text
RIFT_INVITE_REFUSED includes candidate character ID, vacancy, typed reason
```

Stale receipts are omitted. Goal 020 remains language/outbound owner; no phrase bank.

## 11. Metrics

Add bounded counters required by original Goal 023:

```text
preparations/evaluations
ready
needs party
needs role
needs member ready
needs supplies
needs travel
invite requested
invite accepted
invite refused
invite expired
candidate rejected by bounded reason family
roster stale
source stale
binding conflict
```

No profile/candidate IDs in metric labels and no per-pulse log spam.

## 12. Forbidden shortcuts

- no second Party/Invitation kernel;
- no unconditional Phantom auto-accept;
- no leader-side forced replacement of candidate goal;
- no `CLAIM_EXISTS` blanket success;
- no sequence-only invitation reconciliation;
- no string matching for terminal outcome;
- no test-only fix with production adapter unchanged;
- no global player scan;
- no direct Party membership mutation;
- no entry item consumption, Rift start, teleport, room jump, spawn or combat;
- no worker/thread/executor/Future/task;
- no other chronicle changes.
