# Independent review findings — Goal 013A

## Verdict

`FIX_REQUIRED / Goal 013B`

Goal 013A closes the architectural findings from the first Goal 013 review
except durable CLASS skill acquisition.

## Confirmed PASS areas

- commit is one ordinary child of the required parent;
- remote branch points to the exact commit;
- High Five-only allowlist;
- no geodata, `.gitignore`, config, schema, other chronicle or future Goal;
- capability variants and exact action skills;
- item/charge readiness;
- full ItemData resource validation;
- cubic no-body and controlled-actor body facts;
- servitor/pet own mechanics;
- complete equipment paging without global score;
- main/subclass/certification integration;
- ordinary production composition;
- CP current/max exact under ActionLease;
- no new worker/thread/Future;
- cumulative test/verifier routes.

## Direct defect F-013A-01

`L2jProgressionBackend.learnClassSkill` calls
`Player.addSkill(skill, true)` after item/SP mutation and treats in-memory
reconciliation as success.

Current `Player.addSkill(..., true)` calls `storeSkill`, but `storeSkill` catches
database exceptions and returns no failure signal. The caller cannot distinguish
durable persistence from a warning-only failure.

Therefore `OperationStatus.SUCCESS` does not prove the exact
`character_skills` row.

## Direct defect F-013A-02

`Player.setSp` changes runtime stat state. Durable main/subclass SP is written by
later store paths whose exceptions are caught. Goal 013A does not freshly query
the durable SP row before success.

## Direct defect F-013A-03

Inventory item mutation calls `Item.updateDatabase`, whose database failures
are logged and swallowed. Goal 013A checks runtime inventory count, not the
durable `items` row.

## Test gap F-013A-T01

The real integration suite checks the current materialized Player only. It does
not:

- independently query all three durable row domains;
- inject persistence failures;
- dematerialize/reload and prove the skill remains;
- prove rollback of item/SP/skill together.

## Verifier gap F-013A-V01

The 013A verifier checks source ordering and event placement but does not inspect
the silent-failure behavior of `Player.storeSkill`, `Player.store*` or
`Item.updateDatabase`.

## Documentation finding F-013A-D01

The top of roadmap records Goal 013A pending review, but the lower mandatory
progress snapshot still describes Goal 013 as the branch head under review.

## Out of scope

No need to revisit:

- capability taxonomy or tactical doctrines;
- summon behavior;
- equipment policy;
- CP usage doctrine;
- commerce;
- farming;
- party;
- PvP/PK/Olympiad.

Only the durable class-learning transaction is corrective scope.
