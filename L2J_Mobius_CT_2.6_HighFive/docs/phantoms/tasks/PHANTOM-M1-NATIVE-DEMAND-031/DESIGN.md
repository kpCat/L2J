# DESIGN031 — место наблюдения не равно допуску к симуляции

## Сохраняемые контракты
- Population READY/online schedule: кто вообще участвует сейчас.
- Physical locality demand: к кому приблизился human/Synthetic.
- Runtime readiness: можно ли безопасно загрузить/продолжить native Player.
- Background FARM eligibility: можно ли симулировать награды без Player.
- Scene acceptance: фактически живой natural Player и проверенная игровая работа.
Последние четыре понятия не эквивалентны. Ни одному FALSE не присваивать TRUE.

## Setup
Прочитать текущий census один раз с per-row timestamps, state/generation provenance.
До rewards/outcomes выбрать один географический кластер по текущим committed XYZ,
из participating population READY, calendarOnline, durable READY и instance0.
DEAD/INCONSISTENT/identity conflict — не healthy candidates; отчётные строки сохранить.
Местность проверить штатным dry GeoEngine. Не выбирать по kill/reward counters.

Для setup НЕ требовать PNC permits(FARM), dueSnapshot.complete или already materialized:
это место, где наблюдатель создаст demand, а не выдача права загрузить персонажей.
4 profiles рядом желательны для перспективы cohort; отсутствие4 не препятствует
диагностике одного/нескольких естественных кандидатов и не даёт gaming PASS.
В адаптированном task-only PROBE031 разрешены1..8 tracked profiles; соответствующий
FullObserve guard адаптировать ТОЛЬКО для диагностического mode031. Не вызывать
старый Scene/FullObserve guard4..8 для одиночного probe. Старые evaluator028 и
пороги final scenes остаются побайтно неизменными. Отсутствующие actors тоже
измеряются: не ждать World spawn, чтобы начать trace того, почему spawn не случился.
Ранжирование: число подходящих по описанным статическим данным в радиусе1500,
затем расстояние от Synthetic, затем profileId. Выбранные IDs/XYZ фиксируются до хода.
Если ни одного такого кандидата нет — никаких принудительных загрузок; доказать это
по текущим данным и разбивке состояний. Не создавать искусственную популяцию.

Разрешён один initial TELEPORT_SELF ТОЛЬКО Synthetic на проверенную точку через
existing LocalPlay operation; это setup вне измеряемого away/return, не gameplayPASS.
После подхода реальные demand, request due, identity lease и native lifecycle
работают только через existing production dispatch. Нельзя самому materialize(),
создавать DecisionEngine, start AutoPlay, вызывать requestDue вне обычного пути,
переписывать admittedPreparation/lastFailure/terminal/schedule или DB flags.

## Что измерять
T0 до подхода, затем примерно T+5,15,30,60,90с:
profile/object/current epoch, observer point/run, physical demand/positionRevision,
calendarOnline, population state, actual entry state, safe-boundary reason,
dueSnapshot.complete/reason, requested horizon/cursor/requestId, queue/retry/progress,
exact actual materialization request kind/claim/outcome, actual World presence.
PNC FARM permission хранить отдельным диагностическим столбцом, не setup filter.

Не называть cached _lastReportedFailure первым текущим исключением без нового события
в этой JVM. Сохранять времена всех измерений; 1280 SQL reads не atomic global snapshot.
Правильный STATE_ABSENT/INCONSISTENT отказ не превращать в «бот мёртв/не умеет AI».

## First-cause diagnostics
При реально исполняющемся отказе захватить immutable короткую запись:
PID/startTicks/sourceSHA, profile/object/requestId, stage, exceptionClass,
rootCauseClass/message, SQLState/vendorCode (если есть), до16 stack frames,
state/goal/context versions и caller. Секреты/SQL credentials не выводить.
Foreground-набор до8 profiles фиксируется ДО подхода. Для каждого — отдельные
первые8 записей; посторонние startup errors НЕ занимают эти слоты. Global-summary
отдельно: до16 сигнатур (method,class,SQLState) с счётчиками, без Player references.
Запись ограничена4KiB; первый root сохраняется неизменно. Неограниченных maps нет.
Пропуск выбранного необходимого события явно делает diagnosis incomplete; глобальная
статистика не претендует на full error coverage всех1280.

Переиспользовать existing logger/flight recorder/hooks. Capture под нужной границей
только скаляров, публикация после release/rollback. Не делать FS/SQL/foreign-lock
из native hook и не менять исключения/return status/rollback/fail-closed логику.
OFF mode имеет прежнюю семантику. Диагностике запрещено запускать readiness worker.

## Решение после probe
Выводы независимы (могут сочетаться):
SETUP_CONTRACT_MISMATCH — исправленный подход добрался до законного native пути,
старый setup исключал его из-за фонового/завершённого предиката.
CURRENT_PRODUCER_FAILURE — свежий исходный exception и exact call/state доказаны.
INHERITED_INVALID_STATE — проблема уже существует в baseline; допустимый recovery
либо имеется, либо требует отдельного дизайна. Никакого rebaseline по поздней SQL.
INSUFFICIENT_EVIDENCE — точного ответа не получилось; короткий полный handoff, не
общий «всё неизвестно» и не новая серия fixed-time повторов.

Политика решения: исправляемые UI/observer условия не должны становиться правами
на игровое исполнение. Модель исторического прогресса не должна случайно дублировать
контракт сохранности персонажа. Это архитектурная проверка, а не новый controller.

## Runtime-debug-first — дополнительное утверждённое ограничение
До внедрения новой диагностики сначала получить passive `jcmd`/JFR и, если
есть JDWP, 1–3 foreground-specific stack/value observations. За ложным
`ready=false` может не стоять исключения; проследить логическое первое ребро.
Короткие breakpoints дают causal evidence, но не timing acceptance.
Формат `DEBUG_PLAYBOOK.md` — обязательный; это не создание второго контроллера.
Изменения в `DIAGNOSTIC_ONLY` оставляют исходный behavior точно неизменным.
