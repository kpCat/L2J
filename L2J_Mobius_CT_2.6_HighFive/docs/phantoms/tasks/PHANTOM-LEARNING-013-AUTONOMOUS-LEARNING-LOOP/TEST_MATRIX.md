# TEST_MATRIX013 — обязательные RED→GREEN и настоящие проверки

## DB-free / offline

- R01 G0: frozen14, 903/223, 48 applications, acceptedTEST2/pointer12, exact baseline hash; грязные/чужие worktrees не затронуты.
- R02 Splits: TRAIN/TUNING/AUDIT/EVAL одна логическая source incarnation не попадает в две роли; historical011/012 EVAL не возвращаются в TRAIN.
- R03 `ActionAdvantageV2` controls ≥3 independent blocks/cell, class-balance/IPS bound, no duplicate native credit.
- R04 `selectedEpoch=0`: optimizer updates могут увеличиться, но selected weights SHA = old; `IMPROVEMENT_SUPPORTED=false`.
- R05 `selectedEpoch>0` без native V2: no promotion, no `ADVANTAGE_SUPPORTED`.
- R06 Кандидат с хуже AUDIT/constant/previous: reject best, DRAFT/Adam сохранены, loss регрессии виден.
- R07 V2: death/death; death/kill; kill/kill; exact bounded timeout; incomplete/cancel/stale/unknown != completed.
- R08 Split fence: при `EvaluationOnly=true` невозможны FIT, exploration и credits; roundtrip V2 buffer не переписывает V1.
- R09 Blank/no-data cohort/insufficient classes/MP/Geo/Party: `INSUFFICIENT_EVIDENCE`, no patience increment.
- R10 Missing legal V2 pair: `PAUSED_EVAL_COVERAGE_GAP`, no previous EVAL substituted, no patience increment.
- R11 Три достаточных complete cycles без прироста → `PAUSED_NO_MEASURABLE_PROGRESS` и reason LOW_POWER если n мало; не статус «модель идеальна».
- R12 Доказанный прирост → patience0; настоящая регрессия → no best promotion.
- R13 Model/best/source config mismatch при cold RESUME → fail closed; no JAR recalc hot path.
- R14 Crash after SQL COMMIT before journal: recovery reads committed data, no 2nd optimizer update/credit.
- R15 Crash before COMMIT: rollback, resume without corrupt pointer/headers.
- R16 GUI close, duplicate GUI, STOP/PAUSE/RESUME; one owned Director, correct status, no independent double-run.
- R17 Deadline/resource: CPU, memory, free disk, live PID, port, native starts and time; failures count towards budget.
- R18 Replay of genuine 012 V2 raw evidence (read-only) produces 4 comparable pairs, no training credits.
- R19 Human-readable Russian CLI/GUI report: actual status and typed fields; `UNKNOWN` not 0.
- R20 Existing 007/008/009/011/012 tests, `build.xml` targeted + jar; no regression production default OFF.

## Native13 acceptance (не подменять simulated outcomes)

- N01 Законный TRAIN на actual acquired classes/skills: native paid selected actions, HP/MP effects, terminal+settlement; failures/deaths retained.
- N02 Enough fresh controls, TRAIN/TUNING/AUDIT and ≥2 actual contexts for one honest fit; new model id, optimizer updates, selectedEpoch, cold readback.
- N03 Independently fresh Solo+Party V2 native eval; complete terminal-comparable AB/BA, death counted, no credit/FIT.
- N04 Durable phase transition after EVAL; checkpoint references actual committed optimizer, selected, eval SHA; nextCycle scheduled without Codex.
- N05 START/STOP/RESUME with new process and accurate state; processes owned0, ports free, OS exit, no duplicate application.
- N06 `TRAIN_EVAL_STOP` decision from real metrics: positive/no-gain/insufficient/unknown separated, no automatic MAIN promotion.

**Acceptance accounting:** отчёт по каждому R/N с фактическим `PASS/FAIL/NOT_RUN` и записью команды+exit. Никаких `FULL` на основании одних DB-free tests, отчётов или screenshot.
