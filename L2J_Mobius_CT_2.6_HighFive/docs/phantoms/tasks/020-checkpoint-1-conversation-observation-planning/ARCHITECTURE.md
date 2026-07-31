# Goal 020 Checkpoint 1 — architecture contract

## Dependency direction

```text
final filtered Say2 dispatch
→ generic actual-delivery observation
→ bounded Phantom ingress queue
→ managed-observer aggregation/election
→ immutable context snapshot
→ semantic understanding / slot-fragment resolution
→ read-only social modifiers
→ durable conversation.state
→ observer-only response plan
```

No dependency may point back from core chat/network code into Phantom packages.

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
