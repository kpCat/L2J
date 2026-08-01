# Goal 020 Checkpoint 2 — architecture

## Dependency direction

```text
CLIENT_CHAT actual delivery
→ Checkpoint 1 planner
→ atomic conversation.state + conversation.execution
→ Checkpoint 2 execution owner
→ read authority OR canonical goal/party service
→ durable result
→ generated chat handler dispatch
→ terminal execution receipt
```

No network or gameplay layer depends on conversation implementation.

## Truth owners

| Fact | Owner |
|---|---|
| actual client-chat recipient | ChatObservationService / CreatureSay delivery |
| interpretation | Semantic Understanding generation |
| social style | Social service projection |
| conversation context | conversation.state |
| executable plan state | conversation.execution |
| active goal | goal.runtime / Decision engine |
| Party invitation/membership | PartyInvitationService / Party coordinator |
| item/NPC/content facts | Game Knowledge |
| topology destination | Topology snapshot |
| materialized sender | Materialization service |
| recipient/range/channel delivery | existing IChatHandler |
| outbound certainty | execution transition state |

## Atomic handoff invariant

A response/action plan is executable only if its PREPARED execution entry and
the corresponding observation/session mutation committed in one DB transaction.

The in-memory sink is never authority.

## Action invariant

Conversation may authorize and submit an existing canonical goal or exact Party
response. It may not perform the underlying movement, invite, leave or other
gameplay mutation directly.

## Outbound invariant

Generated text is sent at most once:

```text
PREPARED
→ durable DISPATCHING
→ handler call
→ SENT
```

A recovered DISPATCHING entry becomes UNCERTAIN, not PREPARED.

## One source turn, one response

One source dispatch may produce zero or one generated response. Long-running
goal completion does not automatically produce a second chat message.

## No-loop invariant

Generated delivery is explicitly tagged `PHANTOM_GENERATED`; conversation
ingress accepts only `CLIENT_CHAT`.

## Busy-goal invariant

An active goal not owned by the exact conversation plan is immutable from
Checkpoint 2. The conversation returns `goal.busy`.

## Deferred tactical invariant

Support, assist and regroup proposals are structured and understood but remain
DEFERRED until Goal 024 owns party farming/tactical execution.
