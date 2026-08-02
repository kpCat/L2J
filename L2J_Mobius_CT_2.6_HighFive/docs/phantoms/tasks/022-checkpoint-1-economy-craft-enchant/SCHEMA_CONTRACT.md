# Economy schema and lock-order contract

## Tables

### phantom_economy_operations

One durable operation identity and state. Payloads are canonical, versioned and
bounded to 4096 bytes.

### phantom_economy_reservations

Exact logical resource claims. Primary/unique keys must prevent two live
operations reserving the same exact object or overlapping one owner/item count.

### phantom_economy_audit

Bounded terminal significant-operation record. It is not used to reconstruct
mutable inventory authority.

## State transitions

```text
PREPARED → RESERVED
RESERVED → DISPATCHING | ABORTED | EXPIRED
DISPATCHING → OBSERVING | COMMITTED | INCONSISTENT
OBSERVING → COMMITTED | INCONSISTENT
terminal: COMMITTED | ABORTED | EXPIRED | INCONSISTENT
```

No transition from a terminal state to a nonterminal state.

## Lock order

```text
profiles by profile_id
economy operation
reservations by canonical key
components by profile/type
characters/subclasses by object/class index
recipe/skill evidence
items by owner/object
```

Tests must start conflicting operations in reverse caller order and prove the
same database lock order.

## Expiration

Only PREPARED/RESERVED may expire. Expiration deletes/releases logical
reservations and appends an EXPIRED audit.

DISPATCHING/OBSERVING are reconciled, never TTL-retried.

## C2 extension

Reservation owner and resource keys must already support several character
object IDs. No C1 code assumes all rows belong to the initiator even though C1
operations contain one participant.
