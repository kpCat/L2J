# Goal 024A architecture contract

## 1. Scope

Goal024A fixes only lifecycle of already-implemented Goal024 claims, negotiations and agreements.

Do not redesign Goal021 acquisition/source planning, Goal010 topology model, Goal017 Party kernel, Goal018 social model, Goal020 conversation engine or Goal024 resource-key policy.

The three required corrections are:

```text
A. proposal evidence vs final binding semantics
B. causal perceptibility / restart rehydration
C. production agreement resolution / social history
```

## 2. Stable binding vs mutable arbitration evidence

Before bilateral FINAL, exact negotiation draft is bound to mutable evidence including goal ids/revisions, source ids, ResourceKey, required/progress/remaining, acquisition row/evidence, social evidence/modifiers, topology generation/hash, causal perception receipt and policy hash.

If any material input changes before FINAL: do not finalize old evidence, do not silently resume old holder, mark/restart active negotiation and derive new deterministic agreement/evidence identity.

After both sides contain exact final receipts, normal acquisition progress is not identity. Final binding is current while same pair, same exact goals/revisions, same exact source IDs, both current sources still derive expected ResourceKey, compatible policy/topology authority, bilateral exactPair, live status and TTL.

`remaining/progress` in `AgreementReceipt` remain historical arbitration evidence. Do not compare current remaining to final remaining to authorize live agreement.

## 3. Completion is distinct from drift

Classify Goal021 changes:

```text
PROGRESS: same goal/revision/source/resource, remaining decreased -> agreement remains live
COMPLETED/RELEASED: old resource no longer claimed normally -> fulfill where appropriate
MOVE: losing source changed through existing Goal021 switch -> old MOVE agreement fulfilled
AUTHORITY_DRIFT: source/resource/topology/policy no longer trustworthy -> STALE
GOAL_REPLACED: different goal/revision -> STALE unless exact normal completion contract proves otherwise
```

Add only minimum Goal021 read-only status required; do not expose stores.

## 4. Causal perceptibility receipt

Add bounded typed receipt to active negotiation/final agreement (or equivalent persisted pair evidence) containing sufficient facts:

```text
lower/higher profile IDs
topology generation
canonical topology hash
lower node id + profile topology sequence
higher node id + profile topology sequence
perception channel
observed minute
causal expiry minute
evidence hash
```

At negotiation start obtain exact current Goal010 profile snapshots, verify current bounded LOCAL_CHAT perceptibility and persist exact causal evidence.

For subsequent exact pair steps allow continuation within causal TTL even if current one-hop relationship disappeared, while still requiring Goal021 goal/source/resource and topology generation/hash current. Never authorize different pair/resource/goal and never extend causal expiry just by replay.

A materially new negotiation needs fresh current perceptibility.

## 5. Exact-pair lazy rehydration after restart

Runtime claim maps remain caches.

When one profile has persisted active/final pair history:

1. load its exact current Goal021 facts;
2. obtain counterpart ID from persisted state;
3. exact-load only counterpart Goal024 component + Goal021 snapshot;
4. derive both current ResourceKeys;
5. validate policy/topology/causal receipt/TTL;
6. reinstall bounded runtime claims for exact pair, or return conservative blocked/stale semantics.

Do not require counterpart scheduler pulse. No profile page scan, `TopologyService.listProfiles()` or `World.getPlayers()`.

If peer revalidation fails, loser must not get exclusive ALLOW due missing runtime peer.

## 6. Schema evolution

If persisted layout changes, use new farming schema, preferably `farming.conflict schema 2`.

Requirements:

- deterministic v2 codec;
- v1 decode path migrates safe immutable fields and marks causal history untrusted, or decodes legacy state requiring revalidation before effect;
- v1 never directly authorizes SHARE/WAIT/MOVE;
- no SQL migration.

## 7. Production reconciliation state machine

Replace manual boolean lifecycle authority with bounded internal reconciliation, e.g. `reconcileAgreement(profileId, currentFacts, now)`.

Call from farming decision/evaluate/refresh before granting gate truth based on old receipt.

### MOVE

If loser still has same old goal/revision/source/resource -> loser MOVE, holder ALLOW.

If loser source changes to different current Goal021 source/resource after Goal021-owned switch -> old agreement FULFILLED, old runtime claim removed, new claim evaluated separately.

### WAIT

Holder ordinary progress -> agreement remains WAITING. Holder completion/release/source move -> FULFILLED. TTL -> EXPIRED.

### SHARE

Both still exact source/resource -> SHARE survives ordinary progress. Normal completion/release -> FULFILLED.

### REFUSED / ESCALATED

Semantic act remains while canonical holder/loser outcome continues. Normal progress does not restart refusal/escalation. No Goal025 action.

### STALE

Topology/policy/source authority drift that cannot be interpreted as normal outcome -> STALE and no generic fulfilled/broken event.

## 8. Bilateral terminal transition

Resolution to FULFILLED/BROKEN/EXPIRED/STALE must be bilateral before authoritative.

Stable write order lower profile ID then higher. Partial write must not create contradictory gameplay effect; exact replay mirrors same terminal ID/status.

## 9. Social-event outbox semantics

Goal018 remains long-term memory owner.

For exact terminal FULFILLED/BROKEN, `agreement.fulfilled` / `agreement.broken` must deliver once per owner. If first record attempt fails transiently, keep durable pending-social evidence; later bounded reconciliation retries same deterministic event ID. EXPIRED/STALE never emit broken.

Likewise mandatory final offer/accept/refuse/escalation events must be retriable. Use clear durable per-event/social state rather than a flag that is never updated.

## 10. Acquisition ownership

Goal024 never writes `PhantomAcquisitionState.selectedSource`.

```text
Farming MOVE
  -> Goal021 DirectiveKind.SWITCH
  -> existing PhantomAcquisitionService.switchSource(...)
  -> later Farming reconciliation observes source change
```

Do not call `switchSource()` from `PhantomFarmingService`.

## 11. Perceptibility/current resource

Do not regress discovery:

- new conflict uses current bounded Goal010 LOCAL_CHAT perceptibility;
- exact same Party SHARE still exact current Party evidence;
- bucket bounded/profile-sorted;
- three-claimant pairing bounded.

Causal history only continues exact already-started pair.

## 12. Conversation facts

Goal020 facts must continue to expose exact live agreement after normal progress, suppress stale/expired/one-sided agreement, and show **current** own/counterpart remaining from Goal021 rather than historical receipt remaining.

No language moves into farming domain.

## 13. No PvP

No 024A production code may introduce CombatService dispatch, attack, force attack, hostile skill, Player target mutation or PvP/PK. ESCALATE stays semantic/social only.

## 14. Concurrency/lifecycle

- no worker/thread/executor/Future/task/timer;
- pair operations stable lower->higher order;
- exact counterpart reads only;
- shutdown drains mutation claims and clears runtime cache;
- persistent unresolved receipts remain safe for lazy reconciliation.

## 15. No artificial file budget

No numerical changed/read/new file ceiling. Use pre-audited sets and justify additional High Five changes by exact call path.
