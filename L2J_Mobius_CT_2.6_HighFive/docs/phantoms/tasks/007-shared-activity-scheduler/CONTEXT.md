# CONTEXT — Goal 007

## Accepted baseline

```text
Branch: feature/phantom-world
Commit: 82a03342e52ff4b6c023b8ea224da8b1c2f6657f
Goal 006/006A/006B: ACCEPT
Stage I: COMPLETE
Goal 007: ALLOWED
Goal 008/009: NOT_STARTED
```

## Existing runtime

- canonical headless Player lifecycle;
- profile persistence envelope;
- bounded materialization service;
- exact profile/character/World ownership;
- action admission and cleanup;
- retained REAL_LOGIN recovery;
- two-phase GameServer shutdown handoff;
- inert `PhantomScheduler` with an arbitrary bounded Runnable queue.

Goal 007 replaces only the inert queue with typed shared activity scheduling.

## Why activity is runtime-only here

No population manager, schedule, goal or background simulation state exists yet.
The scheduler therefore starts empty and persists nothing. Goal 016 will select
and register profiles; Goal 015 will persist causal background state.

## Conservative perceptibility

Until a regional actor representation exists, both ACTIVE and
NEARBY_PERCEPTIBLE require canonical materialization. This spends capacity but
never falsifies player-observable history.
