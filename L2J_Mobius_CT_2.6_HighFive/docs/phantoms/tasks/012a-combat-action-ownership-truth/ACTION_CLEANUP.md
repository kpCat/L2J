# ACTION CLEANUP — Goal 012A

Cleanup state:

```text
NONE
PENDING
IN_PROGRESS
FAILED_RETRYABLE
COMPLETE
```

Owned descriptor:

```text
combat target object ID
selected skill ID/level
pickup object ID
session generation
```

Canonical cleanup covers ATTACK, CAST and PICK_UP and clears only the exact
owned current target. Foreign/newer actions remain untouched.

If canonical cancellation fails, keep the actor lease, retain truthful cleanup
ownership, retry through the one shared worker, and block terminal consumption
and service stop. Do not hide the failure by closing the lease.

Maximum automatic attempts: 3. Explicit retry remains possible.
