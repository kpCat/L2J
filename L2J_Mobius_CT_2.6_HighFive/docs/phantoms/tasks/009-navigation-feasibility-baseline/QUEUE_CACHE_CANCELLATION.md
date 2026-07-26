# QUEUE, CACHE AND CANCELLATION — Goal 009

Production defaults:

```text
queue 256
workers 2
cache 1024 / TTL 5 seconds
cooldown 1 second
local distance 12000
route distance 100000
waypoints 64
request deadline 1 second
```

Workers are transient service-level drains on the existing ThreadPool, not
per-profile tasks.

Cancellation is generation based. Queued work is removed/skipped. Running
legacy pathfinding is not interrupted; its late result is discarded.

Only computed routes are cached. Every cache hit revalidates each segment
through the current backend, including door/fence state.
