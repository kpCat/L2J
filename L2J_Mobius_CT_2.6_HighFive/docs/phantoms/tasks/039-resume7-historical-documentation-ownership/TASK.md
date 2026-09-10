# Goal039 Resume 7 — historical documentation ownership sweep

## Identity

Required parent:
`49a33254f2b5645ac8f9966fb60c0b3fa3474d47`

Branch:
`feature/phantom-world`

Module:
`C:\Users\ZBook\L2J_Mobius\L2J_Mobius_CT_2.6_HighFive\`

Observed blocker:

`GOAL039_RESUME6_GOAL032_DOCUMENTATION_CONFIG_KEY_INVENTORY_STALE`

Family:

`HISTORICAL_DOCUMENTATION_FORWARD_STATE_OWNERSHIP_DRIFT`

This is Goal039 Resume 7, NOT Goal040.

## Goal

Correct the historical Goal032 documentation test so it permanently verifies the
Goal032 contract it actually owned instead of freezing future Goal033/038 config
and mutable current roadmap/status/handoff state.

Perform a bounded census for the same test-only anti-pattern and correct all
proven same-family cases.

Move the exact current 23-key declared-scope inventory responsibility to Goal039.

Then resume the original Goal039 final release gate.

## Precondition

Require:

`HEAD == origin/feature/phantom-world == 49a33254f2b5645ac8f9966fb60c0b3fa3474d47`

and exact branch.

Record current dirty tree after package extraction and preserve it.

## Required root correction

Follow CONTEXT.md and OWNERSHIP_CONTRACT.md.

Do NOT fix by merely changing:
`17 -> 23`.

Goal032 accepted report proves its original config ownership was 13 keys and
ecology was deferred.

The current 23-key shipped config is valid additive evolution:
- Goal032: 13
- Goal033: 4
- Goal038: 6

The current local-play preset intentionally has 17 explicit keys; the six Goal038
settings are optional parser defaults.

## Expected changes

Test-only:
- `PhantomPopulationResetDocumentationGoal032Suite`
- `PhantomFullVisionGoal039Suite`
- any additional same-family historical TEST-only assertions proven by census.

Docs:
- current Goal039 report/matrix and final docs only as needed by outcome.

Expected production code/config/data/build behavior changes: ZERO.

## Forbidden

- changing shipped config to satisfy Goal032;
- adding six Goal038 keys to preset merely for parity;
- changing PhantomPlayersConfig semantics;
- changing reset implementation;
- rewriting historical Goal032/038 reports;
- weakening Goal039 final current-state validation;
- gameplay/hash/topology/bootstrap changes;
- production DB;
- prepare DB.

## Validation

Run TEST_PLAN.md.

Same-family stale forward-state test assertions are in scope.

A different independent blocker receives one focused confirmation and STOP.

## Final continuation

When green:
final domains -> scale/endurance -> rollback -> fresh full verify ->
standalone final JAR -> fresh Goal034 real stack on final clean JAR ->
Goal039 freeze/docs -> commit/push.

## Git

Allowed bounded read-only Git, read-only archive, exact-path add, one normal
commit, non-force push.

Forbidden reset/restore/checkout rollback/clean/stash/rebase/merge/amend/force.

SUCCESS subject:
`phantom(goal-039): freeze declared full vision`

BLOCKED subject:
`phantom(goal-039): record resume 7 blocker`

No Goal040.
