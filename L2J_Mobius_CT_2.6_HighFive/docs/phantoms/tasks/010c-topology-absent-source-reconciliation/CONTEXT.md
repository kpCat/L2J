# CONTEXT — Goal 010C

```text
Goal 010A generation ordering: ACCEPT
Goal 010B bounded ledger architecture: ACCEPT
Goal 010 overall: FIX_REQUIRED
Goal 010C: REQUIRED
Goal 011: BLOCKED
```

The real scheduler reports `STALE` when a source entry does not exist. A fresh
ledger's `NEVER_SUBMITTED` state is locally proven inactive, but Goal 010B treats
that STALE as uncertainty. This breaks ordinary unregister and reload before
events.
