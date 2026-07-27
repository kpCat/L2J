# CONTEXT — Goal 010B

```text
Goal 010A generation/signal ordering: ACCEPT
Goal 010 overall: FIX_REQUIRED
Goal 010B: REQUIRED
Goal 011: BLOCKED
```

Exact generation, reload re-resolution and unregister ordering are accepted.

The remaining issue is bounded lifetime ownership: historical source sequences
and cleanup tombstones grow with every distinct profile ID, and inactive
targetability for a never-owned ID can allocate permanent state.
