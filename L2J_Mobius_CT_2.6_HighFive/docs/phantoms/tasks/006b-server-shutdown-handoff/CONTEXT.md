# CONTEXT — Goal 006B

Goal 006A correctly hardened local materialization boundaries, action/STOPPING
atomicity and one tracked service drain. The remaining defect is the actual
GameServer shutdown order: generic Player disconnection happens before Phantom
drain, and ThreadPool is stopped immediately after an incomplete drain.

Required order:

```text
first Phantom shutdown
→ generic disconnect only non-managed Players
→ normal shutdown work
→ second Phantom shutdown
→ ThreadPool shutdown
```

Goal 007 remains out of scope.
