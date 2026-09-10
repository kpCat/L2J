# Goal039 Resume 5 — close Goal033 native-owner bootstrap blocker

## Identity

Required parent:
`8e77b4b94e0a58eafac29504a2845a96b02a3e51`

Branch:
`feature/phantom-world`

Module:
`C:\Users\ZBook\L2J_Mobius\L2J_Mobius_CT_2.6_HighFive\`

Exact blocker:

`GOAL039_RESUME4_GOAL033_HEADLESS_QUEST_OWNER_NOT_LOADED`

This is Goal039 Resume 5, NOT Goal040.

## Goal

Correct only the stale Goal033 production-composed test bootstrap so its modern
PhantomSystem starts with the same relevant native-script prerequisites already
proven by Goal036.

Then resume the original Goal039 final release gate.

## Precondition

Require:
`HEAD == origin/feature/phantom-world == 8e77b4b94e0a58eafac29504a2845a96b02a3e51`

and exact branch.

Record current dirty tree after package extraction. Preserve user files.

## Required correction

Follow CONTEXT.md and BOOTSTRAP_CONTRACT.md.

Expected blocker-owned code:
- new test-only supported-content script bootstrap helper;
- Goal036 suite delegates its existing script setup;
- Goal033 production suite invokes helper once after headless initialize and
  before first PhantomSystem startup.

Production Java/data/config/XML/build changes are NOT expected.

## Forbidden

- changing generic headless default bootstrap;
- making PhantomSystem load scripts;
- weakening supported-content runtime validation;
- using full `executeScriptList()` as shortcut;
- mocks/fake owners;
- Q401/class-transfer special casing;
- quest/instance gameplay changes;
- catalog/hash/pin changes;
- topology changes;
- DB migration.

If any becomes necessary, STOP/BLOCKED before doing it.

## Validation

Run TEST_PLAN.md.

The exact blocker is not closed until:
- Goal036 remains green through helper;
- Goal037 native mode green;
- Goal033 production both cases green;
- Goal033 cold reseed 10 identities;
- restart/cleanup invariants remain green.

Then run directly affected predecessor gates.

## Resume final Goal039

After focused green:
domain aggregate -> scale/endurance -> rollback -> fresh full verify ->
standalone final jar -> fresh Goal034 real stack on clean final jar ->
freeze/docs -> commit/push.

## Stop rule

A NEW independent blocker gets one focused confirmation then STOP/BLOCKED.
Do not fix another family in Resume 5.
No Goal040.

## DB

Only:
`127.0.0.1:3308/l2jmobiush5_phantom_test`
user `l2j_phantom_test`.

Production `l2jmobiush5` forbidden even read/probe.

`prepare-phantom-test-db` forbidden.

## Git

Allowed:
bounded read-only Git, read-only `git archive`, exact-path add, one normal
commit, non-force push.

Forbidden:
reset/restore/checkout rollback/clean/stash/rebase/merge/amend/force.

SUCCESS subject:
`phantom(goal-039): freeze declared full vision`

BLOCKED subject:
`phantom(goal-039): record resume 5 blocker`

## Final handoff

Return:
- ACCEPT/BLOCKED;
- root cause;
- production changes count;
- helper/call-site paths;
- loaded owners;
- focused counts;
- final aggregate/verify/JAR/Goal034 evidence if reached;
- DB safety;
- final SHA/push/HEAD==origin;
- marker state;
- explicit no Goal040.
