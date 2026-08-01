# Goal 020 Checkpoint 1 — architecture contract

## Dependency direction

```text
final filtered Say2 dispatch
→ generic actual-delivery observation
→ synchronous DISPATCH_CLOSED boundary
→ bounded Phantom ingress queue
→ managed-observer aggregation/election
→ immutable context snapshot
→ semantic understanding / slot-fragment resolution
→ read-only social modifiers
→ durable conversation.state
→ observer-only response plan
```

No dependency may point back from core chat/network code into Phantom packages.

`GameClient.sendPacket` invokes `ServerPacket.runImpl(Player)` synchronously, and
all four supported chat handlers iterate/send synchronously. `Say2` therefore
closes the observation scope only after every legitimate `CreatureSay` recipient
callback has returned. A delivery after that close is a mismatch and cannot open
a new batch.

## Safety boundary

```text
understanding      != authorization
proposal           != goal
response plan      != outbound message
social preference  != consent
```

Checkpoint 1 cannot mutate canonical gameplay state.

## Actual-observer invariant

A conversation turn exists only when `CreatureSay.runImpl` confirms an actual
recipient of the exact filtered `Say2` dispatch. No world scan, radius
reimplementation or inferred recipient list is allowed.

## One-response invariant

One Say2 dispatch ID may create:

```text
zero plans
or
one plan for one elected managed observer
```

Never one plan per Party/local/trade recipient.

## Persistence boundary

`conversation.state` stores context and cooldown only. It does not store raw
messages, rendered text, mutable Player facts or an executable outbox.

## Social atomicity

`social.state` and `social.receipts` form one atomic mutation unit. The receipt
is not a substitute for important memory and important memory is not an
idempotency ledger.

## Authority boundary

Conversation state pins:

```text
conversation catalog
semantic pack/corpus
Game Knowledge
topology
party-role catalog
social catalog
```

Any drift fails closed before state reuse or plan publication.

## Scheduler fairness

Ingress, batching, context construction, state mutation and plan publication all
consume one shared operation budget. Unfinished batches are requeued after other
due batches; no profile/session full scan occurs on a pulse.

Each batch remains owned by one resumable state machine:

```text
COLLECTING → RESOLVING_OBSERVERS → ELECTING → LOADING_STATE
→ BUILDING_CONTEXT → UNDERSTANDING → READING_SOCIAL
→ PERSISTING → PUBLISHING → DONE | FAILED
```

The pulse owner is a CAS claim. A bounded delayed/due queue plus membership set
replaces full-map discovery. Observer lookup, election, load, context, each
semantic operation, each of three social reads, persistence and publication each
consume exactly one operation. No index monitor crosses an external boundary.

## Persistence outcomes

Persistence is typed as `SAVED`, `DUPLICATE`, `FAILED` or `AUTHORITY_STALE`.
Only `SAVED` may publish. Existing state with a different authority generation is
read-only: its payload, row version, sessions and recent hashes are not reset.
Recent observation hashes retain temporal oldest-to-newest order across codec
round-trips and restart.

## Checkpoint 2 handoff

Checkpoint 2 may consume only an immutable `ConversationResponsePlan` and its
`ConversationActionProposal`. It must independently:

- authorize against current canonical state;
- reserve action/outbound ownership;
- execute through existing services;
- deliver generated chat with explicit generated origin;
- prevent loops and duplicate sends;
- record terminal result.

No hidden execution seam is permitted in Checkpoint 1.
