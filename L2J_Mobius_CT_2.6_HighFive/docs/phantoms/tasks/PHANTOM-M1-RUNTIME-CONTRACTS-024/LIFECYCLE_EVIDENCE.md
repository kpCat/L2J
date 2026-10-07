# Lifecycle evidence024

Все строки относятся к frozen ca3 runtime, ActorMode=Synthetic, полный GameServer/штатный scheduler/активные NPC. Клиентский REAL gate NOT_RUN.

|Gate|Observed|Unproven/failed|Result|
|---|---|---|---|
|Natural A|383.78s, complete6, primaries110/175; mage cohort|5failed participants, tail/idle/missing/overflow|FAIL|
|Natural B|382.73s, complete6, primaries252/832; mage+fighter|All6 failed; 832 still26cycles but missing8/idle105.5/overflow|FAIL|
|Native death R4|Stock20933 NPC, natural452 died; own NPC removed|No post-return farm|FAIL|
|Native death R5|Stock21320 NPC, natural175+187 died; own NPCs removed|No post-return farm|FAIL|
|Soft leave/return|Native dry walk out, away80s, stock Synthetic stopped|Return request UNCERTAIN, no remat/farm; no replay|FAIL|
|Earned healthy stop|Native SQL captured; final drain stopped=true6119ms, no force|Initial retained5 and native reserved kill-event drain timeout|FAIL|
|Whole group restart|All6A+6B compared; union9; A4/6 andB4/6 exact+finalized|A110/506, B110/832; final snapshot alignment/new income unresolved|FAIL|
|AFTER_NATIVE crash|Exact SEALED452 values and receipt/SQL/dump hashes; REAL0; exit72|Receipt still VERIFY_PENDING row8423 after startup/prelude/finalstop|FAIL_RECOVERY|
|AFTER_FINALIZE crash|Exact SEALED187, inventory/skills, context finalized8336→8340; REAL0; exit73|Global pending gate old452; continued farm/index separately unmeasured|PER_PROFILE_PASS, GLOBAL_FAIL|
|Repeat recovery|Two read-only comparisons preserve canonical outcome|Second separate process restart idempotence NOT_RUN,8/8 cap|NOT_RUN_PROCESS_REPEAT|
|Default cleanup|All own b/c roles absent/listeners0; preserved c DB|No remaining own JVM, emergency force0|PASS_CLEANUP|

Fixture27-window matrix PASS does not fill missing full-runtime proof. Expected state comes only from immutable SEALED PREPARE, never max EXP/deltas or SQL reverse reconstruction. A/B errors are retained, not declared DATA_LOSS without isolating legitimate income/death. See RESULT.md and exact per-profile JSON verifiers.
