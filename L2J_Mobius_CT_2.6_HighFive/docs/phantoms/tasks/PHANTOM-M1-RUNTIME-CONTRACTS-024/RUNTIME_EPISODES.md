# Runtime episodes024

Общий предел8/8 full GameServer episodes достигнут. Isolated TEST JVM a не является full GameServer episode. Все даты2026-10-07 UTC; frozen runtime/source ca3 для episodes5–8. Исходная a содержит required-base JAR и остаётся отдельной TEST lane.

|Episode|DB|Code|Game/Login PID|Outcome|
|---:|---|---|---|---|
|1|contract024b|a43fef43|17040/22252|Probe, SEALED→SQL5/5; farm2/5; stock graceful stop|
|2|contract024c|a43fef43|22368/2756|Old final A failed; B pre-baseline environment gap; stock graceful stop|
|3|same c|5bb49932|19568/23828|Two setup gaps before baseline; stale planned PENDING diagnosed; stock graceful stop|
|4|same c|5a8e3ce7|2728/25096|Stock Z restore mismatch and Synthetic request uncertainty before baseline; stock graceful stop|
|5|same c|ca3cbef9|15332/26024|Final A/B, death fixtures, soft walk; initial retained5/kill-event incident, final stock drain stopped=true; stock graceful exit|
|6|same c|ca3cbef9|20936/8064|Whole-cohort restart; exact AFTER_NATIVE crash profile452 exit72; Login graceful|
|7|same c|ca3cbef9|22128/20372|Native-crash restart/repeat SELECT; stock Prelude; exact AFTER_FINALIZE crash187 exit73; Login graceful|
|8|same c|ca3cbef9|27704/19516|Finalize-crash restart/repeat SELECT; per-profile exact/finalized187; old pending452 retained; default stock graceful stop|

Planned crashes2/2, only authorized fault windows, REALcount0 at each exact window, before-crash dumps and SQL. Emergency force0. Crash GameServer WaitForExit labels in wrapper output do not make these healthy shutdown PASS. Default own JVM/listeners0, independently checked for b/c role manifests; see evidence/CLEANUP_STATE.json. No foreign Java/MariaDB was stopped.

Actual crash receipts: evidence/CRASH_AFTER_NATIVE/planned-crash.properties and evidence/CRASH_AFTER_FINALIZE/planned-crash.properties. No additional restart to evade episode cap. d..h were not created. Frozen JAR/config unchanged, no same-SHA reroll of failed natural cohorts.
