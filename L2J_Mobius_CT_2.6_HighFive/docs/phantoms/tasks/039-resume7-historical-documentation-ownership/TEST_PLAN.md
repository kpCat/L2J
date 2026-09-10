# Goal039 Resume 7 execution plan

## Phase 0 — precondition

Require:

`HEAD == origin/feature/phantom-world ==
49a33254f2b5645ac8f9966fb60c0b3fa3474d47`

and exact branch.

Record CURRENT dirty-tree paths/fingerprint after task package extraction.

Preserve all user-owned tracked/untracked changes.

No reset/restore/checkout/stash/clean/rebase/merge/amend/force.

## Phase 1 — read-first ownership audit

Read:
- Agents.md
- all Resume-7 package files
- current Goal039 report/matrix
- `PhantomPopulationResetDocumentationGoal032Suite`
- Goal032 historical report
- current shipped config
- current local-play preset
- `PhantomPlayersConfig`
- Goal038 report
- `PhantomLocalPlayGoal031Suite`
- `PhantomFullVisionGoal039Suite`
- current roadmap/status/handoff only to identify the proper current owner

Confirm:
- historical Goal032 says 13 keys and ecology deferred;
- current shipped is 23 = 13+4+6;
- current preset is 17 = 13+4;
- Goal038 six settings are optional defaults;
- Goal032 contains stale forward-state assertions beyond the first key-count fail.

## Phase 2 — documentation assertion census

Run CENSUS_CONTRACT.md before edits.

Record a small table of all relevant historical suite hits and classifications.

Do not indiscriminately rewrite historical documentation tests.

## Phase 3 — correct Goal032 ownership

Refactor only the test contract, not production behavior.

Expected:
- explicit 13-key Goal032 owned set;
- subset/presence checks rather than full key-set equality;
- duplicate detection;
- parser/tuning coverage only for Goal032's 13;
- preserve reset command/admin/no-auto-reset safety;
- anchor historical Goal032 status/13-key fact in its historical report;
- remove current roadmap version / next Goal / handoff ownership.

Do not update 17 to 23 as the primary solution.

Do not add Goal038 keys to local-play preset just to satisfy parity.

## Phase 4 — move current inventory ownership to Goal039

In `PhantomFullVisionGoal039Suite` structure/static mode, require:

- shipped exact declared-scope union = 23:
  - Goal032 13
  - Goal033 4
  - Goal038 6
- no duplicate/unknown current shipped keys;
- preset explicit union = Goal032+Goal033 = 17;
- Goal038 six omitted preset settings parse to exact defaults;
- safe shipped defaults remain unchanged.

Keep current roadmap/status/handoff/freeze ownership in Goal039 final/documentation
gate, not Goal032.

If Goal039 already proves some of these, reuse rather than duplicate.

## Phase 5 — focused tests

Run from CLEAN no-geodata candidate:

1. `phantom-population-reset-documentation-goal032-test` — 1/1 PASS.
2. Goal039 structure/static — PASS with new config ownership.
3. Goal032 reset/reseed — 2/2 PASS.
4. Goal032 ownership — 3/3 PASS.
5. Goal031 documentation — full documentation mode PASS.
6. Goal031 readiness — 3/3 PASS.
7. Goal033 production — 2/2 PASS.
8. Goal036 — 8/8 PASS.
9. Goal037 native — 8/8 PASS.
10. Goal037 static — PASS.
11. Goal038 catalog/focused relevant static config checks — PASS.
12. DB negative guard — PASS.

Run additional same-family corrected historical documentation suites from census.

No `prepare-phantom-test-db`.

## Phase 6 — affected lineage

After focused green:
- Goal033/033A/033A1;
- Background position;
- Goal021 acquisition/restart;
- Goal030 release/restart/rollback;
- shipped-disabled baseline.

Geodata-present Goal033A1/Background can reuse current local external geodata if
available; do not commit geodata.

## Phase 7 — Resume Goal039 final sequence

Only after Phase 5/6 fully green.

Use CLEAN candidate:

1. Goal039 final-domain aggregate.
2. Goal029 scale/environment/endurance.
3. Goal030 rollback/release-control.
4. one fresh full `ant verify` under original retry budget.
5. standalone final `ant -q jar`.
6. fresh Goal034 real local-stack acceptance using THAT final clean JAR.
7. final Goal039 documentation/freeze consistency.
8. commit/push.

No production Java/data/config/build change after final JAR.

## Stop budget

Resume 7 may fix all proven
`STALE_FORWARD_STATE_OWNERSHIP`
test-only assertions from the historical-documentation census.

A different independent blocker:
- one focused confirmation;
- STOP/BLOCKED;
- do not fix that different family;
- no Goal040.

## Documentation

Update:
`docs/phantoms/reports/039-final-full-vision-release-gate.md`

Record:
- historical ownership root cause;
- census;
- 13/4/6 config ownership;
- shipped 23 / preset 17;
- current parser defaults;
- focused/final results.

Do NOT rewrite Goal032 or Goal038 historical reports.

On ACCEPT:
- update current status/roadmap/handoff/freeze as Goal039 final current owner;
- exact `FEATURE_COMPLETE_FOR_DECLARED_SCOPE`;
- no Goal040.

On BLOCKED:
- marker absent;
- record independent blocker;
- no Goal040.
