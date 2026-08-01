# Durable execution transition contract

## Entry transitions

### Outbound

```text
NONE → PREPARED
PREPARED → DISPATCHING | FAILED | EXPIRED
DISPATCHING → SENT | FAILED | UNCERTAIN
SENT/FAILED/UNCERTAIN/EXPIRED → terminal only
```

### Action

```text
NONE → PREPARED
PREPARED → SUBMITTED | COMPLETED | REJECTED | DEFERRED | EXPIRED
SUBMITTED → COMPLETED | REJECTED | EXPIRED | UNCERTAIN
terminal → terminal only
```

Invalid transitions fail closed.

## Crash matrix

| Crash point | Restart truth |
|---|---|
| before atomic handoff | no state and no execution |
| inside transaction | rollback both components |
| after handoff before scheduler signal | recovered PREPARED |
| after goal insert before in-memory update | goal/execution atomic; recovered SUBMITTED |
| after canonical refuse before result write | reconcile exact invitation terminal |
| before outbound DISPATCHING | retry allowed |
| after DISPATCHING before handler | UNCERTAIN; no retry |
| inside/after handler before SENT | UNCERTAIN; no retry |
| after SENT | terminal receipt, no retry |

## Terminal compaction

Terminal entries may move to compact receipts only when:

- no exact active goal/invitation remains;
- no outbound state is PREPARED/DISPATCHING;
- terminal minute is known;
- plan/observation hashes remain in the receipt.

Evict oldest terminal receipt only after its replay horizon. Never evict a
nonterminal entry to admit new work.

## Goal transaction

For GOAL entries, mutate in one repository transaction:

```text
goal.runtime
conversation.execution
```

The exact expected row versions for both are required. Insert/replace ordering is
stable by component type.

## Query result

A query result is an immutable bounded fact record. Rendering happens only after
the query completes. No result template may imply a fact not present in the
record.
