# Bounded guarded TEST restore evidence

Before: 30 rows. Current: 40 rows. Added: 10. Missing or changed original rows: 0.
Source: private retained PopulationFixture journal before-image and read-only SELECT
`festivalId,cabal,cycle,date,score,members FROM seven_signs_festival ORDER BY festivalId,cabal,cycle`
on `l2jmobiush5_phantom_test`, localhost3308, user `l2j_phantom_test`.

| festivalId | cabal | cycle | date | score | members |
|---|---|---|---|---|---|
| 0 | dawn | 4 | 0 | 0 | empty |
| 0 | dusk | 4 | 0 | 0 | empty |
| 1 | dawn | 4 | 0 | 0 | empty |
| 1 | dusk | 4 | 0 | 0 | empty |
| 2 | dawn | 4 | 0 | 0 | empty |
| 2 | dusk | 4 | 0 | 0 | empty |
| 3 | dawn | 4 | 0 | 0 | empty |
| 3 | dusk | 4 | 0 | 0 | empty |

Output bounded to eight rows. Two further added rows have festivalId4, cabal
dawn/dusk and the same cycle/date/score/members. No correction SQL executed.
No direct proof of the writer/timer was captured.

Source metadata for selected profile68: DEAD; catchup COMPLETE; goal ACTIVE,
goalId6792112958247919054, revision14. The diagnostic selector's READY-only
precondition was invalid for this witness. No loaded native inventory/autoGet
evidence exists from task012.

Journal was retained after restore transaction rollback. Its hash and original
before/schema/source aggregate hashes are recorded in RESULT.md. Do not delete,
overwrite or replace it with a new snapshot; do not declare guarded TEST restored.
