# ARCHITECTURE — Goal 006A

```text
identity preflight:
  World player null
  World object null
  autosave ID absent
  claim
  repeat
  Player.load
  exact autosave identity
  pre-spawn World null
  spawn
  both World maps == exact Player
```

```text
action admission:
  stateMonitor { RUNNING check + entry lookup + actor admission }
```

```text
shutdown caller:
  STOPPING
  create/reuse one DrainAttempt
  submit one command to existing ThreadPool
  await bounded latch
  timeout => FAILED, ownership retained
```
