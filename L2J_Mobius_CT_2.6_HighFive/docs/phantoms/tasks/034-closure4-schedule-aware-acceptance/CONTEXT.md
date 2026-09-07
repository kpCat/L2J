# Goal034 closure 4 context

Required parent: `b43959bcfec2d21840b1abc8f54a7bc6900d849e`.

Important correction: previous black-box treated `PhantomPopulationActiveTarget=5` as a mandatory constant online count. This contradicts production semantics.

Authoritative facts:
- shipped Phantom config describes ACTIVE target as the maximum ACTIVE target;
- PopulationManager admission limit is `min(activeTarget, maximumMaterialized, desired ACTIVE count)`;
- durable population state stores schedule template + schedule phase;
- LIVING assigns morning/evening/late schedules with human-like variation.

Therefore observed online `2/3/2` with managed=10 can be valid. Closure must calculate desired ACTIVE from the same catalog/timezone/phase and prove actual online equals the admitted schedule count, capped at 5.

Independent known restart issue: sandbox inherits `ServerRestartDays=4`; set all seven days in sandbox and assert the actual scheduled instant before waiting.
