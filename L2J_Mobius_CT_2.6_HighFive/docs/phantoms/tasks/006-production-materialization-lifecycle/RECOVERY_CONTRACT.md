# RECOVERY CONTRACT — Goal 006

Lease states:

```text
RESERVED
RETAINED
```

Only a matching REAL_LOGIN lease may become RETAINED after failed/incomplete
cleanup. PHANTOM ownership is never released by this recovery path.

All recovery evidence is mandatory:

```text
owner REAL_LOGIN
state RETAINED
World player absent by object ID
World object absent by object ID
autosave absent by object ID
characters.online exactly 0
```

DB error, missing row, multiple row or nonzero online rejects recovery.

Evidence is followed by atomic conditional removal of the same retained entry.
Entry/token changes fail closed.

Invocation is only explicit or one on-demand attempt for the same materialize
request. Periodic, startup-release-all, age-based and unbounded retry are
forbidden. RESERVED ownership is never recoverable.
