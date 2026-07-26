# PERSISTENCE — Goal 008

Use existing profile component APIs only.

```text
component: goal.runtime
schema version: 1
payload: deterministic binary <=4096
```

Require magic/version, bounded lengths before allocation, exact consumption and
rejection of truncation/trailing/unknown version. Reads occur only on explicit
attach/reload; normal decision ticks do not query DB. Restart loads the goal and
requires replan; no active plan is persisted.
