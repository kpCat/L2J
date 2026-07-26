# ACTIVITY SCHEDULER ARCHITECTURE — Goal 007

## Production graph

```text
PhantomSystem
  -> PhantomProfileRepository
  -> PhantomMaterializationService
  -> PhantomScheduler
       -> registered slots (bounded)
       -> ready queue (bounded/coalesced)
       -> due set (one entry/profile)
       -> relevance signals (max 16/profile)
       -> one recurring ThreadPool pulse
       -> PhantomActivityMaterializationPort
       -> PhantomActivityWorkSink.noop()
```

## State truth

```text
ACTIVE / NEARBY_PERCEPTIBLE
  => materialization service owns canonical Player

WARM / BACKGROUND / SLEEPING
  => service does not own a materialized Player
```

Requested state and effective state are distinct while transitions are pending.

## Signal flow

```text
explicit registered profile
+ immutable relevance signal
→ bounded coalesced ready queue
→ signal aggregation
→ deterministic transition
→ typed due work
```

No topology or AI is inside this graph.

## Stop flow

```text
scheduler.beginStop
→ cancel one pulse/reject inputs
→ materialization service drain
→ scheduler.finishStop only after service STOPPED
```
