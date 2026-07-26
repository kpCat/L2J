# PROGRESS TRACKING — Goal 009

The tracker owns no Player or movement command.

One attempt/profile records:

```text
request/route ownership
start time
last meaningful-progress time
best distance to destination
terminal status
```

Statuses:

```text
TRACKING
PROGRESS
ARRIVED
STUCK
TIMEOUT
CANCELLED
STALE
```

Timeout is checked before stuck. Logical time regression and wrong request ID
are stale. No timer or polling task is created.
