# Изоляция и runtime

Required base `df71140e659670e2e3d225605f64689a6d32d281`, branch `experiment/m1-candidate007-observe008`.
Own worktree:
`C:\Users\ZBook\.codex\worktrees\m1-travel-033r\L2J_Mobius`.
Если занят — первый свободный suffix, detached exact base разрешён заранее.
Main `C:\Users\ZBook\L2J_Mobius` и foreign worktrees не переключать и не чистить.

Owned DB: `l2jmobiush5_localplay_travel033ra`, `...travel033rb`,
`...travel033rc`, `...travel033rt`. Источник — READ/export retained032 (actual после
игры) и, где нужен контрпример, сохранённый immutable экспорт с указанным SHA.
Не незаметно менять исходные данные для получения подходящей группы.
Shared TEST и PLAY — READ/export, без reset/update/delete. kpCat не менять.

Population1280, Active/Materialized8, MaxScheduled10000. Не уменьшать population
ради прохождения; не увеличивать количество слотов. Baseline actual state фиксировать.
Existing Synthetic observer, не fake REAL_LOGIN; TTL525/watchdog/sequence не повышать.
Стабильная setup-точка до World publication, natural admission, исходный персонаж
Synthetic возвращается по существующему cleanup. Во время proof не teleport Phantom.

Debug: jcmd/JFR, узкий JDI exact profile/object/epoch, resume в finally. No mass
breakpoints/широкий MethodExit; паузы не входят в timing evidence. HotSwap optional
только для подходящего диагностического эксперимента; acceptance на clean committed Jar.

Новые standard helper paths получают явный approved scope; platform approvals не обходить.
Если чужой onlineREAL/занятые чужие порты — не убивать процесс, завершить конфликтный lane.
Свои процессы start/graceful stop разрешены по PID+startTime+runtime+Jar+DB.
Emergency force только своих exactPID после dump и двух bounded graceful attempts,
REALcount0; force означает CLEANUP_FAIL. Planned crash в033R не нужен и не разрешён.
Ни одного запроса «в игре/вышел/arm»; пользователь не должен ждать ночью.

## Итог
TASK_RESULT GREEN/PARTIAL/BLOCKED/FAILED; M1=OPEN; REAL_FINAL=NOT_RUN.
Отдельные flags: WALK_DETOUR_PASS, ROUTE_STRATEGY_PASS, RETURN_GK_NATIVE_PASS,
EMERGENCY_ENABLED/OBSERVED, ROUTE_TO_FARM_PASS, COHORT_PASS, SAVE/CLEANUP_PASS.
Не объявлять readonly risk или test coverage полной безопасностью runtime.
