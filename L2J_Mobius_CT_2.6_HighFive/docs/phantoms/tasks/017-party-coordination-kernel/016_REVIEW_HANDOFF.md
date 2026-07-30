# Goal 016 independent-review handoff

## Accepted line

```text
Goal 015: ACCEPT
Goal 016 implementation: 92a0040f8eb919154067db6c6297b02c858b1b72
Goal 016 completion: 57caea2e5b5597c9a06b87cb8e868f227c4aa88e
Goal 016 verdict: ACCEPT_WITH_EXPLICIT_FUTURE_CONTRACTS
Goal 017: ALLOWED
```

The completion closes the direct Goal 016 blockers:

- transport-neutral population shortcut/macro registration;
- versioned exact creation authority;
- strict durable projection and restart repair;
- exact-object autosave suppression and read-only verification;
- explicit scheduler ownership retry actions;
- pending-creation retirement/return;
- shutdown publication barrier;
- PopulationManager observability;
- one green full `ant verify`, standalone jar and deterministic verifier.

No Goal 016 suffix is required.

## Future contract F016-ADMISSION-SCALE

Current admission work is bounded by `MaxMaterializedPhantoms` and production
defaults are target/activeTarget zero. Before Goal 029 scale acceptance, or
before operating with an ACTIVE target materially larger than the population
pulse budget:

- account admission selection and changed-member processing in the same explicit
  per-pulse operation budget;
- slice large dirty admission sets across pulses;
- prove no pulse performs more than its declared profile/action budget;
- run scale/soak evidence with the configured production target.

This does not block Goal 017, whose canonical live party has at most nine
members.

## Future contract F016-HISTOGRAM-TRUTH

Population class/level histograms currently describe durable creation metadata.
After progression, gameplay truth must come from canonical background or
materialized Player state. Do not use the population creation histogram for
party-role suitability or live level/class decisions.

## Documentation authority

`docs/PHANTOM_BOTS_ROADMAP.md` is authoritative. The legacy numbered feature
list in the master plan still labels 017/021/022/023 inconsistently. Goal 017
must align only those headings/descriptions to the accepted roadmap; it must not
renumber or redesign later Goals.
