# CONTEXT — Goal 009

```text
Accepted baseline: 6ecd8ba155e63a2dedeeafd65c1961fdb57bf261
Goal 008/008A: ACCEPT
Goal 009: ALLOWED
Goal 010/011: NOT_STARTED
```

Current High Five facts:

- GeoEngine sets all regions to NullRegion initially.
- Runtime disables `GeoEngineConfig.PATHFINDING` when zero geodata regions load.
- `canMoveToTarget` checks doors/fences and geodata where present, but can return
  true with no geodata.
- `PathFinding.findPath` is synchronous, requires geo at both endpoints and has
  no cancellation/deadline input.
- Pathfinding may allocate a temporary NodeBuffer when pooled buffers are busy.
- Existing Creature movement falls back to direct movement when pathfinding
  fails; the Phantom navigation service must not label that fallback safe.

Goal 009 creates an inert bounded planning/progress service. It issues no Player
movement and registers no decision action.
