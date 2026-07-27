# ABSENT-SOURCE RECONCILIATION — Goal 010C

Safe STALE previous states:

```text
NEVER_SUBMITTED
INACTIVE_CONFIRMED
```

Both transition to `INACTIVE_CONFIRMED`.

Unsafe STALE previous states:

```text
POSSIBLY_ACTIVE
OWNERSHIP_UNCERTAIN
```

They remain fail-closed.

Safe STALE never proves scheduler profile absence and never releases the ledger.
