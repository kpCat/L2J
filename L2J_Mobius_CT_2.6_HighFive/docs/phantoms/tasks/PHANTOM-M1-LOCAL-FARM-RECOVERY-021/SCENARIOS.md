# Проверки, которые держат весь исправляемый цикл

Новые standalone main suites:
`PhantomVisibleIntentRecoverySuite.java`
`PhantomLocalFarmRecoverySuite.java`
Оба в test/java/org/l2jmobius/tests/phantoms/.

R01. Runtime28/current29 после legitimate attach-before-canonical-plan: до MOVE_TO/AutoPlay
выполнен reload29; БД не откатилась, decision history больше не использует28.
R02. Reload BUSY не выдаёт stale action; после освобождения _inFlight ровно одна sync.
R03. Route submit на28, reply после29: reply не исполняется, не засоряет exclusions29.
R04. Terminal failure старого object/epoch/goal игнорируется; актуальный accepted однажды.
R05. Один current terminal water failure + 20 decision ticks: не 20 новых starts/планов,
а один recovery request. Новый planId не сбрасывает budget текущей episode.
R06. Pending exact native handoff + terminal: permitted local replacement, тот же
request/epoch, атомарно связанные catchup-plan/goal/runtime/permit.
R07. NORMAL с pending без handoff и HISTORICAL_BASELINE не получают эту возможность.
R08. CAS conflict меняет 0 components; commit не частичный. Произвольная новая revision
от постороннего writer не принимается как разрешённый foreground rebind.
R09. DB commit succeeded, runtime publish/reload failure: old runtime не атакует;
retry завершает exact new state один раз. После restart можно штатно материализоваться
с durable new plan. Не присуждается ни одного исторического interval/reward.
R10. Cleanup между commit и publish, pending owned store, dead/foreign Player — no grant.
R11. Для FAILED_REPLAN_REQUIRED plan replacement не превращает status в RUNNING/COMPLETE
и не стирает failureReason. Cursor/from/target/interval/EXP/SP/items не меняются.
R12. Первый route имеет dry endpoint, но water cell внутри: отклонён до native MOVE_TO.
Следующий bounded local candidate сухой и native-reachable: используется он.
R13. Все местные routes плохи: максимум3 different targets за60s, затем явный bounded
unavailable, без бесконечного retry, teleports или скрытого forced dematerialize.
R14. Исторический default planner выдаёт прежние результаты; visible-local candidate
не выходит за radius2000/instance и не использует GK/boat/global leg.
R15. Совмещённая production-path проверка: current goal -> travel failure -> queued
recovery -> atomic replan -> runtime sync -> native travel -> existing AutoPlay.
Не тестировать отдельно mock=arrived и mock=AutoPlaySuccess как доказательство R15.
R16. Финальный ordinary positive path на реальном native Player: не менее5 полных
kill/reward/next-target cycles, реальные EXP/SP, same epoch. Native samples overflow=false.

Тестовые injectable clock/routes допустимы для negative/CAS tests. R15/R16 дополнить
реальным existing headless/native environment; materialize/attach только через настоящий
production-style composition. Никакого прямого AutoPlay.start в тесте вместо executor.
Guarded TEST только через существующий gate/restore. При retained journal до начала
проверок не запускать новый TEST и не удалять журнал вручную.
