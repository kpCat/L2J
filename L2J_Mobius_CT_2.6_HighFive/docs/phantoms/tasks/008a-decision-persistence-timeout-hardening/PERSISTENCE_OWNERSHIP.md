# PERSISTENCE OWNERSHIP — Goal 008A

```text
monitor: validate and claim immutable operation token
outside monitor: execute PhantomGoalStore call
monitor: reconcile exact token and clear in-flight marker
```

One persistence operation/runtime. Combined attached + pending attach is bounded.
Work/mutation/reload reject while persistence is active. Detach/stop retain the
slot until handler and persistence quiesce.

Conflict and generic failure are distinct explicit reload-required states.
There is no automatic retry.
