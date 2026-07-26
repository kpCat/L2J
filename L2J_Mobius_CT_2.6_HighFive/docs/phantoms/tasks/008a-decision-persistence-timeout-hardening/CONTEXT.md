# CONTEXT — Goal 008A

```text
Goal 007/007A: ACCEPT
Goal 008 commit: b6c58c37f1ba77e92b61e9499a30d17d09c82086
Goal 008 independent verdict: FIX_REQUIRED
Goal 008A: REQUIRED
Goal 009: BLOCKED
```

Keep the accepted domain model, codec, registries, scoring, plan executor,
cancellation and inert production integration.

The remaining defect is that synchronous persistence currently owns the global
decision-engine monitor, making unrelated cancellation and real server shutdown
dependent on JDBC completion. A separate logical-zero step-timeout gap and
snapshot-truth gap are closed in the same bounded task.
