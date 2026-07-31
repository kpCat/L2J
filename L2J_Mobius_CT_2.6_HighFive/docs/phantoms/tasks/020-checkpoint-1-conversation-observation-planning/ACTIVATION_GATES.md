# Goal 018/019 activation gate details

## Social receipt transaction outcomes

| State | Exact receipt | Event temporal state | Outcome |
|---|---:|---|---|
| any | present | any | IDEMPOTENT, no write |
| capacity available | absent | expired | atomic STALE receipt only |
| capacity available | absent | late but live | aged delta + memory + APPLIED receipt |
| capacity available | absent | current | normal delta + memory + APPLIED receipt |
| full, no expired receipt | absent | any | CAPACITY_REACHED, no write |
| component conflict | absent | any | reload/retry <=3 |
| corrupt/stale authority | any | any | fail closed |

Receipt pruning and capacity decision occur under the same owner stripe and DB
transaction as the state mutation.

## Aged delta

For each catalog dimension:

```text
age = max(0, effectiveNow - happenedMinute)
remaining = max(0, abs(delta) - floor(age * decayPerDay / 1440))
agedDelta = sign(delta) * remaining
```

Memory salience uses catalog memory decay. Agreement counters remain historical
facts but are applied only while the event is live and exactly once.

## Join producer gate

The producer captures pre-commit operation phases. Emit only when:

```text
operation.kind == JOIN
old phase == CANONICAL_OBSERVED
new phase == COMMITTED
same exact operation ID and invitation identity
```

## Semantic domain validation

| Slot | Valid namespaces |
|---|---|
| TARGET_PLAYER | profile, character.object |
| PARTY_ROLE | party.role |
| CAPABILITY | capability |
| ITEM | item |
| NPC | npc |
| CONTENT | content |
| TOPOLOGY_NODE | topology.node |
| LOCATION | topology.node, location |
| QUANTITY | numeric only |
| RESPONSE | bounded text only |

## Candidate completeness

Candidate-budget exhaustion is not normal ambiguity. It means ranking
completeness is unknown and therefore must return `clarify.complexity`.

## Clarification replacement

Given a pending clarification:

1. parse the new turn as a complete intent;
2. a complete accepted new intent replaces the pending flow;
3. otherwise resolve only the pending missing slots;
4. merge with exact known slots;
5. complete or re-clarify;
6. never merge two different authority generations.
