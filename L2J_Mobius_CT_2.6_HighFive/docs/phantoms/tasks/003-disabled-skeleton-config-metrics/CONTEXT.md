# CONTEXT — Task 003

## Accepted state

```text
Branch: feature/phantom-world
Baseline: 84f29a0002b25d2b1ff1a19fa9c92867479fd6a5
Task 002A safety implementation: ACCEPT
Task 003: ALLOWED
Task 004: NOT_STARTED
ADR 0001: Proposed
```

## Current startup order

```text
ConfigLoader.init
DatabaseFactory.init
ThreadPool.init
GameTimeTaskManager
IdManager
...
network listener
LoginServerThread
```

Insert the guarded Phantom start after ThreadPool and before IdManager.

## Current shutdown order

```text
offline stores
disconnect players
GameTimeTaskManager interrupt
ThreadPool.shutdown
LoginServerThread interrupt
save data
DatabaseFactory.close
```

Insert Phantom shutdown immediately before ThreadPool shutdown.

## Config style

Custom config classes live in `gameserver.config.custom`, are loaded explicitly
from `ConfigLoader`, and use files under `dist/game/config/Custom`.

Task 003 creates a separate Phantom World config and does not alter Fake Players.

## Disabled guarantee

Default behavior adds only loading one small local config and evaluating false.
No runtime object, queue, trace, task, DB or network work.

## Enabled guarantee

Enabled mode is still inert: no Player/NPC/profile, no worker, no scheduled task,
no DB/network and no gameplay effect.

## Task 002A final evidence

```text
Commit: 84f29a0002b25d2b1ff1a19fa9c92867479fd6a5
Parent: 36e5411e01e8e73f8a0fd4d9460e327c28a6798b
Verifier: 52/52 pre and two final runs
Output SHA-256:
3DEBD45D104620BE262FC6AE83A0A9244F80D9D409E9FEA504DF0EA815E0249E
Push/remote: exact
Independent verdict: ACCEPT
```
