# Model contract — Goal 018

## Subjective truth

No global omniscient relationship exists. Each owner profile stores a separate
view of a counterpart:

```text
owner A → subject B
owner B → subject A
```

These records may legitimately disagree.

## Compact payload budget

Target worst-case layout:

```text
header/authority/personality              <= 256 bytes
24 relationships                         <= 1400 bytes
24 memories                              <= 1800 bytes
codec framing/checks                      <= 400 bytes
total                                     <= 4096 bytes
```

Use numeric catalog codes in durable records. Public snapshots resolve codes to
keys through the exact matching catalog hash.

## Mutation ordering

```text
validate event/catalog/profile
→ acquire fixed stripe
→ load or deterministic-create state
→ reject authority drift
→ materialize decay/expiry
→ exact event-ID duplicate check
→ apply catalog deltas/counters
→ deterministic compact/evict
→ optimistic insert/update
→ publish cache only after durable success
→ release stripe
```

A query performs:

```text
load/cache
→ authority check
→ read-only projected decay
→ immutable snapshot/modifier
```

It never writes merely because time passed.

## Party integration direction

```text
canonical Party fact
→ coordinator terminal/membership commit
→ SocialEvent value
→ social sink/service
```

The social service cannot call Party operations. The invitation core cannot
depend on social code.

## Failure policy

```text
profile absent          → PROFILE_NOT_FOUND
catalog mismatch        → AUTHORITY_STALE
corrupt component       → INCONSISTENT
important-state full    → CAPACITY_REACHED
optimistic conflict     → reload/retry <=3
service stopping        → NOT_RUNNING
duplicate event ID      → IDEMPOTENT
durable success         → RECORDED
```

Canonical Party success remains successful if social recording finally fails;
the failure is visible in fixed metrics and the returned sink result.

## Event identity

Use full SHA-256:

```text
social.event
| source subsystem
| canonical source operation/invitation identity
| owner profile ID
| event key
| counterpart stable reference
| perspective
```

No timestamp-only or random event identity.

## Decay invariants

- integer arithmetic;
- frequency independent;
- monotonic effective time;
- no value crosses zero;
- no overflow for maximum elapsed time;
- no write on observation;
- expiry and eviction use the same projected time.
