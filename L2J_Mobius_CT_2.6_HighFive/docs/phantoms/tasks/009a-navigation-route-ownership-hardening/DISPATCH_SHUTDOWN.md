# DISPATCH AND SHUTDOWN — Goal 009A

Dispatch and beginStop share an ordering gate:

```text
claim queue/worker
→ schedule or exact rollback
→ only then may STOPPING become observable
```

No dispatcher invocation begins after STOPPING. Accepted workers release exact
ownership even when the queue was cancelled.

Configured shutdown diagnostics include aggregate materialization and navigation
state before the shared ThreadPool stops.
