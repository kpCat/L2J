# REVIEW FINDINGS — Task 004A

## P1 — retained REAL_LOGIN owner bypassed while disabled

Current policy:

```java
return phantomSystemEnabled || currentOwner == PHANTOM;
```

A retained REAL_LOGIN owner from failed cleanup is ignored by a later disabled
login. This defeats the fail-closed ownership guarantee.

The Task 004A package itself specified the flawed truth table. This is an
architecture-specification defect, not unauthorized Codex scope expansion.

## P1 — wrong-character cleanup may release another lease

`Disconnection` checks whether the client has a lease, but not whether that
lease belongs to the Player being cleaned. A client retaining lease A can later
clean Player B and release A.

## P1/P2 — cleanup postcondition is exact-instance scoped

The policy accepts cleanup when World/autosave no longer contain the exact
Player object, even if another object with the same object ID exists.

Identity and collision protection are object-ID scoped, so cleanup postconditions
must be object-ID scoped too.

## Accepted Task 004A work

No rollback is required for:

- shared playerLock;
- connection state gate;
- retryable Phantom cleanup;
- bounded warnings;
- terminal STORED;
- autosave read-only diagnostics.

Task 004B must preserve these.
