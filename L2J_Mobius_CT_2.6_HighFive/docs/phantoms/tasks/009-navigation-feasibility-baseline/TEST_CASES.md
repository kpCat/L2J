# TEST CASES — Goal 009

Core suite must cover:

- contracts/immutability;
- capability modes;
- direct validated and unverified no-geo;
- blocked no-geo/pathfinding-disabled;
- distance/route/waypoint bounds;
- queue/profile backpressure;
- worker concurrency;
- queued/in-flight cancellation;
- queue/compute deadline;
- backend failure;
- cache hit/revalidation/eviction;
- cooldown/direct bypass;
- stop quiescence;
- progress/arrival/stuck/timeout/stale;
- lazy factual backend mapping;
- no Player/Creature/action use.

Performance twice:

```text
10000 direct requests
1000 repeated local path requests
>=90% cache hit after fill
queue<=256 workers<=2 cache<=1024 waypoints<=64
```

All Goal 001–008A regression routes remain cumulative.
