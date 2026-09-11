# Goal039 Resume 8 — final candidate environment qualification

## Baseline

Required branch:

`feature/phantom-world`

Required exact local and remote HEAD:

`539688cda76c06bf48528f210cbff03524818871`

Parent subject:

`phantom(goal-039): record resume 7 blocker`

Goal039 remains BLOCKED only because the fresh verify candidate environment was
not stable. Goal040 does not exist and must not be created.

## Independently confirmed Resume 7 result

Resume 7 closed the historical-documentation ownership family.

Current published matrix is:

- 28 total rows;
- 26 PASS;
- `real-stack-black-box` = NOT_RUN_BLOCKED;
- `final-documentation-freeze` = NOT_RUN_BLOCKED.

Fresh Resume-7 evidence already PASS on the current parent:

- Goal039 final-domain aggregate: PASS, 50m21s;
- Goal029 scale/environment/endurance: PASS, 32m01s;
- Goal030 rollback/release-control: PASS, 1m56s;
- Goal032 documentation/ownership/reseed: PASS;
- Goal031 documentation/readiness: PASS;
- Goal033/033A/033A1 dual-mode lineage: PASS;
- Goal036 8/8: PASS;
- Goal037 static/native: PASS;
- Goal038 focused/affected/restart: PASS;
- Goal039 static/structure: PASS;
- DB negative guard: PASS.

Production Java/config/data/build changes in Resume 7: ZERO.

## Exact blocker

`GOAL039_RESUME7_FRESH_VERIFY_CANDIDATE_ENVIRONMENT_FAILURE`

This blocker has two proven symptoms from the same release-candidate environment
family.

### Symptom A — nested candidate Git root

The first fresh `ant verify` got through the runtime/DB tail, then historical
`phantom-static-verify-014` failed.

`tools/phantoms/verify-task-014.ps1` computes:

- module root from its own script path;
- repository root via `git -C $moduleRoot rev-parse --show-toplevel`;
- historical changed paths relative to that repository root.

The Resume-7 candidate was a plain exported tree nested under the operator's real
Git working tree. Therefore `git rev-parse --show-toplevel` resolved the operator
repository, not the candidate. Historical Goal014 paths were then compared
against the wrong module-relative prefix and falsely reported out-of-scope.

A focused run in an isolated Git context passed, proving product Goal014 was not
broken.

### Symptom B — moving the already-created candidate

For the only permitted full verify retry, the candidate was moved to a different
location.

After that move:

- JDK 25 failed while opening `dist/libs/LoginServer.jar` with a zipfs
  `AccessDeniedException`;
- the test DB guard could not `toRealPath()` the candidate's existing
  `.phantom-local/Database.test.ini`;
- a focused `ant -q test` reproduced the same environment failure.

The correct correction is therefore not to weaken JDK, the DB guard, Goal014 or
the product. The candidate must be created directly in its final isolated
location and never moved.

## Important build behavior

`build.xml` requires Java 25.

`jar` creates and copies both:

- `dist/libs/LoginServer.jar`
- `dist/libs/GameServer.jar`

The Goal034 black-box target deliberately depends only on `compile-tests` and
requires those previously built JARs to exist, so the standalone final JAR gate
must precede Goal034.

## DB guard behavior

`PhantomTestDatabaseGuard.validate` intentionally uses `toRealPath()` for:

- module root;
- `.phantom-local/Database.test.ini`;
- `.phantom-local` itself.

Do not weaken these checks. They are a safety boundary.

Allowed DB remains only:

- host localhost / 127.0.0.1
- port 3308
- database `l2jmobiush5_phantom_test`
- user `l2j_phantom_test`

Production `l2jmobiush5` is forbidden even for probe/read.

`prepare-phantom-test-db` is forbidden.

## Final provenance

Current `PhantomFullVisionGoal039Suite.REQUIRED_PARENT` is the Resume-7 parent
`49a33254...`.

Resume 8 must update that test-only provenance constant to this task's exact
published parent:

`539688cda76c06bf48528f210cbff03524818871`

The final freeze/report must cite this Resume-8 required parent.

This is not production behavior.

## Expected production changes

ZERO.

Do not modify:

- production Java;
- `build.xml`;
- `PhantomTestDatabaseGuard`;
- `verify-task-014.ps1`;
- shipped config;
- topology/semantic/gameplay data;
- SQL/schema;
- Goal034 runtime implementation.

A production/build change requires a newly proven substantive defect and is not
part of this Resume.
