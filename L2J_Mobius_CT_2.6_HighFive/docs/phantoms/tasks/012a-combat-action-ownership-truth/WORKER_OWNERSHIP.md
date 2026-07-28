# WORKER OWNERSHIP — Goal 012A

One shared worker owns an explicit dispatch result and handle.

```text
claim under dispatch ordering
→ schedule
→ publish accepted handle or exact rollback
→ worker marks RUNNING
→ top-level finally releases exact claim
```

`beginStop()` uses the same gate, cancels a scheduled-not-started handle and
releases the claim only when cancellation wins.

Required dispatcher outcomes:

```text
ACCEPTED(handle)
REJECTED
```

A null `ThreadPool.schedule` future maps to REJECTED. Inline execution must not
deadlock or release a newer claim.
