# Goal 024 architecture contract

## 1. Ownership map

Goal 024 owns only:

```text
ephemeral/durable farming resource claims
conflict detection between current Phantom acquisition intents
bounded deterministic negotiation protocol
agreement/decision receipts
typed semantic conflict acts/facts
the gate that tells Goal021 whether it may use its selected resource
```

It does **not** own acquisition/source planning (Goal021), topology/perception (Goal010), personality/relationship/memory (Goal018), language/chat (Goal020), Party membership/routes (Goal017), combat (Goal012), PvP/PK (Goal025), or navigation (Goal009).

Preferred production package: `java/org/l2jmobius/gameserver/phantoms/farming/**`.

## 2. Canonical resource identity

A farming claim is derived from the exact current executable Goal021 `Source`, never arbitrary coordinates or chat text. Only monster-backed executable sources participate: `DEATH_DROP`, `SPOIL_SWEEP`, `MANOR_CROP`, `QUEST_COLLECTION`. `RECIPE_PREPARATION` never creates a spot claim.

If the source resolves to an exact topology `ROOM` node:

```text
scope = ROOM
identity = exact topology node ID
```

Item ID and method are evidence, not the resource identity: two players farming different drops in one exact room still compete for the room.

Otherwise for ordinary outdoor farming:

```text
scope = MOB_GROUP
identity = topologyNodeId + anchorId + npcId
```

Again item/method are evidence, not conflict identity. Persist source ID and authority hashes in the receipt. Stale/missing source/node/anchor/generation fails closed.

## 3. Goal021 read-only conflict snapshot

Add a narrow immutable Goal021 seam, preferably `PhantomAcquisitionService.conflictSnapshot(profileId)`, containing:

```text
profileId
goalId / goalRevision
targetItemId
requiredAmount / progress / remainingAmount
selected Source
current status / phase
acquisition row version or evidence hash
ranked alternatives IDs/scores
whether switchSource is currently feasible
authority hashes
```

It must load exact current goal + acquisition state and reject mismatch. No mutation and no exposure of the store itself.

## 4. Acquisition execution gate

Add a narrow bridge analogous to `PhantomEconomyConflictPort`, conceptually `PhantomFarmingConflictPort`. Default when not installed is ALLOW so disabled/legacy tests retain old behavior.

Outcomes:

```text
ALLOW
SHARE
NEGOTIATE
WAIT
MOVE
STALE
```

- ALLOW/SHARE: Goal021 proceeds normally.
- NEGOTIATE/WAIT: Goal021 starts no new travel or target/combat acquisition for this resource.
- MOVE: Goal021 follows its existing SWITCH lifecycle; Goal024 does not rewrite selected Source.
- STALE: Goal021 replans from current authority.

Gate only at safe resource boundaries: before a new TRAVEL_REQUIRED route starts and before TARGET_REQUIRED selects a new target. Do not abort an already dispatched kill/spoil/sow/harvest/quest callback/sweep operation mid-action. Recheck at the next safe boundary.

## 5. Runtime claim index

The farming service owns a bounded in-memory index keyed by `ResourceKey`:

- one current intent per profile/current Goal021 source;
- capacity derives from existing `maxScheduledPhantomProfiles`/production settings, not an unrelated hard-coded scale;
- claim carries exact goal/source/topology evidence;
- short policy lease refreshed only from exact current facts;
- lazy expiry/cleanup, no worker/timer;
- source/revision/completion/shutdown invalidates claim;
- stable ordering: ResourceKey then profile ID;
- no full profile or online-player scan.

A runtime lease is not durable agreement proof.

## 6. Bounded perceptibility query

Do not use `PhantomTopologyService.listProfiles()` in the Goal024 hot path. Add/reuse one Goal010-owned bounded read query, conceptually:

```text
List<ProfileTopologySnapshot> perceptibleProfiles(long observerProfileId, PhantomPerceptionChannel channel, int limit)
```

It reuses the current topology generation, registered observer point/node, same + one-hop `query.isPerceptible(...)`, and `PhantomTopologyProfileRegistry.listForNodes(...)`. Result stable-sorted, bounded, excludes observer.

Use existing `LOCAL_CHAT` perceptibility for whether two Phantoms may begin a farming negotiation. Do not allocate a fourth scheduler signal source.

### Perceptible-history protection

Negotiation may begin only from fresh Goal010 perceptibility. Once begun, persist exact topology generation/node/counterpart evidence for a bounded TTL so causal history is not erased by one immediate movement. New offer/final effect revalidates current goal/resource authority; long-stale counterpart expires. Stale history cannot authorize combat/PvP.

## 7. Same-Party cooperation

Query accepted Goal017 Party state read-only. If two exact conflicting Phantom profiles belong to the same current canonical Party/group generation, resolution is `SHARE` with reason `farming.conflict.same_party`, without negotiation rounds or Party mutation.

Stale/mismatched Party evidence is fail-neutral.

## 8. Durable negotiation state

Use profile-component persistence; no new SQL table expected. Preferred component:

```text
component = farming.conflict
schema = 1
```

Bounded state per profile:

```text
current ClaimReceipt (or null)
one ActiveNegotiation (or null)
bounded AgreementReceipt history
policy/authority hashes
logical minute
```

Persisted claim receipts are not trusted as live after restart until Goal021/topology revalidation.

### ClaimReceipt

ResourceKey, goal ID/revision, source ID, required/progress/remaining, authority evidence, lease expiry.

### ActiveNegotiation

At least: agreement ID, resource key, lower/higher profile IDs, both goal IDs/revisions, both source IDs, both remaining amounts at evidence time, round, proposal act/owner/status, perception/topology evidence, social evidence/modifiers, creation/expiry.

Only one active negotiation per profile. With >2 claimants, choose next counterpart deterministically and converge pairwise.

### AgreementReceipt

Terminal statuses:

```text
SHARED
WAITING
MOVING
REFUSED
ESCALATED
FULFILLED
BROKEN
EXPIRED
STALE
```

Keep only bounded history needed for idempotency/restart; long-term memory belongs to Goal018.

### Bilateral durability

An effect on two Phantoms is active only after both components contain the same exact final agreement identity/evidence. Safe protocol:

```text
OFFER persisted by proposer
RESPONSE persisted by responder
FINAL persisted on one side
FINAL mirrored on the other
only then gate effect active
```

Equivalent two-sided optimistic protocol is acceptable. A crash/write conflict between sides grants no MOVE/WAIT/SHARE effect. Reconciliation completes the exact same ID or marks stale/expired; replay never invents a second agreement ID.

## 9. Typed acts

Domain vocabulary exactly:

```text
SHARE
WAIT
MOVE
REFUSE
ESCALATE
```

They are typed acts, not strings of Russian prose. Each carries agreement/resource identity, actor/counterpart, both goal bindings, both remaining amounts, reason/evidence, round, expiry.

`ESCALATE` means only semantic/social escalation evidence reserved for Goal025. It must never attack, force attack, cast hostile skills, set a PvP target, damage or start combat.

## 10. Deterministic policy and convergence

Load strict versioned `dist/game/data/phantoms/farming/high-five-farming-conflict-v1.xml` with bounded claim lease, negotiation TTL, wait duration, pair cooldown, maximum rounds, maximum alternatives, cooperation/share threshold, escalation threshold and scoring weights. No General.ini keys.

Decision evidence is derived from:

```text
both real Goal021 remaining/progress values
both Goal priorities/revisions
claim age/current holder evidence
alternative-source availability
same Party truth
Goal018 goal.persistence modifier
Goal018 conflict.escalation modifier
relationship/reputation evidence
current topology/perception evidence
stable profile ID tie-break
```

All weights/ranges strict, hashed and negative-tested. Stable profile ID is the final tie-break only after factual/policy scores tie.

Convergence rules:

- same Party/cooperative policy -> SHARE where permitted;
- yielding side with alternative -> MOVE;
- yielding side without usable alternative -> WAIT;
- REFUSE records refusal, but canonical arbitration still selects at most one exclusive holder;
- high escalation -> ESCALATE semantic record, then losing side still MOVE/WAIT; no PvP;
- max rounds/cooldown prevent ping-pong/chat spam;
- never two mutually exclusive holders both ALLOW.

## 11. Honoring final agreements

SHARE is valid only while both exact bindings remain current, both final receipts match, and TTL is valid. It does not create a Party.

WAIT blocks new travel/target acquisition until holder release/completion/move, wait expiry or authority change; then normal re-evaluation resumes.

MOVE makes acquisition invoke its existing source switch exactly once for exact agreement/source identity. After source changes, old claim/agreement becomes fulfilled/stale and the new source claims afresh.

REFUSE/ESCALATE do not permit both sides to farm exclusively.

## 12. Goal018 social integration

Goal018 remains sole personality/relationship/memory owner. Add narrow catalog events only if needed, suggested:

```text
farming.agreement.offered
farming.agreement.accepted
farming.agreement.refused
farming.conflict.escalated
```

Use existing `agreement.fulfilled` / `agreement.broken` when an exact final farming agreement is honored/broken. Event ID/evidence includes exact agreement/resource identity for idempotency. Do not duplicate social dimensions or traits. Use existing `goal.persistence` and `conflict.escalation` modifiers.

## 13. Goal020 conversation integration

Add a narrow read-only `PhantomFarmingConversationFacts` seam. Expose typed facts such as:

```text
FARMING_CLAIM_STATUS
FARMING_CONFLICT
FARMING_REMAINING
FARMING_ALTERNATIVE
FARMING_NEGOTIATION_ACT
FARMING_AGREEMENT
FARMING_ESCALATION
```

Extend Goal020 query execution with `farming.conflict.query` so current claim/agreement can be explained from exact facts. Stale receipts suppressed.

Language stays entirely in Goal019/020 data. No Russian prose or chat dispatch in farming service. Autonomous negotiation does not require chat.

## 14. Decision integration

Add one Goal024 Decision candidate on the existing Goal021 acquisition goal type. It is executable only while farming service has bounded work: claim current source, observe conflict, propose/respond/finalize, honor/reconcile/expire.

Register explicit action keys. One Decision step performs one bounded logical transition. Acquisition candidate must not race resource work while gate is NEGOTIATE/WAIT. MOVE remains an acquisition-owned SWITCH action. Goal024 step never directly executes navigation/combat/chat.

## 15. Ordinary real players

Full bilateral negotiation requires two real Phantom goals. Ordinary human Player may be perceived/chatted with, but receives no fabricated PhantomGoal, ClaimReceipt or automatic SHARE/WAIT/MOVE intent. Do not globally scan humans to infer farming intent.

## 16. Metrics

Fixed-cardinality counters at least:

```text
claims requested/active/expired/stale
conflicts discovered
negotiations started/resolved/expired
acts SHARE/WAIT/MOVE/REFUSE/ESCALATE
agreements finalized/fulfilled/broken
gates allow/share/negotiate/wait/move/stale
source switches requested by agreement
perception stale/unavailable
optimistic persistence conflicts
social event success/failure
maximum bucket size
maximum active negotiations
```

No IDs as metric labels, no per-pulse log spam.

## 17. Lifecycle/performance

- no worker/thread/executor/Future/task/timer;
- lazy lease/TTL cleanup;
- no global profile/online player scan;
- no per-pulse DB scan;
- O(1) resource bucket lookup + bounded bucket work;
- bounded perceptible peer query via Goal010 index;
- bounded alternatives/counterparts/rounds;
- shutdown blocks new mutations, drains active claims, clears runtime leases;
- partial bilateral receipts remain safe for restart reconciliation.

## 18. Forbidden shortcuts

No second acquisition/source planner, arbitrary XYZ claim key, `World.getPlayers()`, `TopologyService.listProfiles()` hot path, fourth topology signal source, direct Player/Party/combat/navigation/chat mutation, auto Party creation for SHARE, PvP/PK execution, inferred human goal, phrase bank/runtime LLM, other chronicle, `.l2j`, production DB test path.
