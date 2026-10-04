# Входной handoff task007 (не отчёт о выполнении)

INPUT_BASE=d924a7d2c2f1e06af1cd54bedf1bee861560044f
INPUT_PRODUCTION_BASE=461a4abe32be4aa08532b8417a6147684a8889c6
INPUT_CANDIDATE=task006/evidence/CANDIDATE.patch; UNACCEPTED
CURRENT_PRODUCT_STATUS=M1_OPEN
ENGINEERING_EXECUTION=REQUESTED_BY_USER
RUNTIME_EXECUTION=LOCKED_PENDING_INDEPENDENT_REVIEW
HISTORICAL_110_142_175_FIRST_EXCEPTION=UNAVAILABLE
OLD_SYNTHETIC_BUDGET=4/5; old#5 remains forbidden
REAL_ARM=NOT_AUTHORIZED_FOR_ENGINEERING
M2=NOT_STARTED

Перед началом прочитать TASK/GOAL/DESIGN/PLAN/CANDIDATE_REUSE. Архив не содержит новый
принятый production fix; никаких GREEN assertions в нём нет.

При завершении исполнитель заменяет этот входной handoff фактическим результатом:
status, verified remote/code/report SHA, exact artifact path/hash, completed/open
Q/P/E/T/L/A/W, scope extensions, TEST commands, preservation evidence, reviewer issues,
next runtime readiness. Не заполнять предполагаемые PASS заранее.

Координатор после отчёта проверяет remote source/diff/RESULT/ARTIFACT, а не только
финальный текст Codex. После разрешения runtime новый короткий Codex-чат; если BLOCKED,
сохранённый exact reproducer/candidate переходит в новую инженерную сессию, без повторного
открытия гола006 и без бесконечных одинаковых «BLOCKED подтверждён» сообщений.
