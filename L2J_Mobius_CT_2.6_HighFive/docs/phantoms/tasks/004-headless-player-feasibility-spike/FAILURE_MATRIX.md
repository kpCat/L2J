# FAILURE MATRIX — Task 004

Use a deterministic enum/injector. Injection is test-only configuration passed to
the spike; no production config key is added.

| Step | Injection point | Required rollback |
|---|---|---|
| 1 | after identity claim | release lease; no load/World |
| 2 | after Player.load | stop canonical tasks/autosave; store/delete if safe; release |
| 3 | after identity attachment | detach ownership; cleanup loaded Player |
| 4 | after headless output attachment | restore client-bound output; cleanup Player |
| 5 | after minimal domain initialization | reverse selected init; cleanup |
| 6 | after online/session activation | DB online false; cleanup |
| 7 | after World spawn | remove visibility/World; cleanup |
| 8 | after action admission opens | close admission; wait zero; cleanup |
| 9 | after inventory add before remove | remove exact fixture delta; cleanup |
| 10 | after store before delete | delete; detach; release |
| 11 | after delete before release | verify residue; release |

For each case:

```text
failure observed
cleanup 1 succeeds
cleanup 2 succeeds/no-op
World object absent
online flag false
autosave absent
lease absent
headless output detached
action count zero
party/trade/request/instance absent
fixture item baseline restored
non-daemon thread delta zero
```

If any rollback cannot be proven, Task 004 is not accepted.
