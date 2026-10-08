# DESIGN027 — один lifetime должен иметь завершение

## Архитектурное решение
Не увеличивать гору исключений вокруг native действий. Исправить координацию уже
существующих lifecycle-фаз. Для контроля остановки различать PENDING, COMPLETE и FAILED.
Проверка «ещё работает» не должна мгновенно превращаться в «операция провалена».
Phantom остаётся native Player; owned-store/crash протокол025/026 сохраняется.

## 1. Bounded shutdown handshake

В PhantomSystem добавить типизированную внутреннюю сводку, например:
`StopProgress(StopPhase phase, StopOutcome outcome, String blocker, long deadlineNanos)`.
Имена могут быть вложенными типами существующего PhantomSystem; отдельный framework не нужен.
`StopOutcome = PENDING | COMPLETE | FAILED`.
Phases: CLOSE_PRODUCERS → WAIT_ACCEPTED_CONTROL → DRAIN_PLAYERS → FINISH_DEPENDENCIES → DONE.

### CLOSE_PRODUCERS
Один раз закрыть admission новых scheduler/decision/materialization/death-discovery roots,
используя существующие beginStop/revoke механизмы. НЕ отключать возможность законно
завершить уже принятый death/store/event. Не запускать новые обычные игры на каждом poll.
Существующие social/economy/etc остановки сохранить, но не расширять их возможности.

### WAIT_ACCEPTED_CONTROL
Сохранить topology, DB, event delivery, scheduler executor и instant executor доступными.
Дождаться завершения ранее принятых background recoveries/operations/transitions и
death reconciliations. Exact blocker в progress: категория + count + profile/epoch при наличии.
Временное nonzero — PENDING, не exception/fatal. Реальный native/DB failure — FAILED.

### DRAIN_PLAYERS
Вызвать existing materialization drain/store при готовых prerequisites.
Допускается повторное продолжение только того же начатого shutdown/entry, не повтор native
writer «на всякий случай». Earned tickets завершаются один раз с собственными потомками.
Новые ordinary roots закрыты. Pending receipt продолжает существующий resolver.

### FINISH_DEPENDENCIES
background/POST_STORE/index/identity release должны закончиться до topology/game knowledge
и до того, как stock Shutdown получит terminal ответ и остановит shared executors.
`COMPLETE` требует entries0, earned0, pending0, accepted controls0, service terminal.
Не приравнивать пустой World к отсутствию retained lifetime.

### Deadline и ожидание
Переиспользовать существующий bounded window shutdown hook10s как общий monotonic deadline,
а не ecology10 + каждый сервис10 + каждый poll10. Повторный poll deadline не продлевает.
Это перенос существующего wait на весь handshake, НЕ лечение callback latency увеличением лимита.
Blocking wait допускается только на внешнем stock Shutdown thread, вне system/class/Player/
owner/DB monitors и вне native event/decision worker. Для вызова из native worker — coalesced
control request и немедленный PENDING; не ждать собственный ticket/executor.
Во время ожидания callbacks выполняются штатно. На deadline — FAILED с evidence, не forced
finish/open и не сообщение «успешно сохранили». Реальное недоставленное earned-событие
не маскировать. Re-entry/concurrent callers видят один exact stop attempt; no duplicate stores.

## 2. Неблокирующий обычный recovery

Внутри существующих BackgroundService/OrdinaryDeathRecovery/MaterializationService
разделить request/continuation и ожидание native store. Scheduler/приоритетный pulse
не должен держать свой worker или Player monitor, ожидая callback, которому нужен этот executor.
Использовать существующие instant executor/control continuation и exact quiescent callback.
Не создавать собственный executor/поток на персонажа. Каждому profile/epoch одна coalesced request.

Идентичность recovery: profile/object/epoch + original death generation/time + goal revision
где требуется. Событие чужого/старого epoch не восстанавливает нового Player.
Допустимые фазы: DEATH_OBSERVED → CORPSE_WAIT (существующее45s) → NATIVE_RETURN →
STORE_PENDING → BACKGROUND_READY → LOCAL_DEMAND_OR_BACKGROUND → FARM_RESUMED.
Ни одна повторная попытка не вызывает повторно уже успешный doRevive/teleport/reward.
После shutdown новых town-return/materialize roots нет; законное сохранение принятого recovery
должно закончиться. Already revived elsewhere обнаруживается штатно, не revive повторно.

Cold DEAD обрабатывается через существующий durable/native recovery, не фиктивное onDeath
и не SQL HP>0. Before/after native death/canonical snapshots сохраняются; penalty остаётся stock.
Конкретные изменения cold admission только после RED точного отсекающего predicate.
Нельзя раздавать NORMAL purpose поверх catchup fence; сохранить typed exact claim.

## 3. Управление опубликованными callbacks

Уточнить наблюдение в имеющемся PlayerNativeWork ParticipantWork.run и Scope dispatch record:
- capture exact owner/ticket/future at publication;
- EXECUTOR_ENTERED до попытки захватить locks;
- RUNNING после успешного exact-ticket start;
- body result + completion, не сбрасывая первый incident.
Не создавать перехват всех stock событий. Выбирать максимум8 профилей, bounded records.

Если callback не работает после исправления handshake: привести один positive RED и paired
negative. Разрешены точечные изменения existing adapters/continuation под SOURCE_MAP,
только когда установлен drop/rejection/lock/own-executor cycle. Не менять global
EventDispatcher, размеры pools или штатную задержку2500ms ради результата.

## 4. Видимая жизнь после recovery

Recovery не завершён, пока бот лишь стоит живой в городе. Если natural demand остаётся,
существующий planner выбирает подходящую локальную farm-миссию; штатные AutoPlay/AutoUse
доводят минимум три новых цикла после восстановленного lifetime. Для отсутствующего demand
доказать продолжение background и rematerialization при возвращении observer.
Дальний путь через полмира не делать обязательным для этой проверки.

Разрешён точечный handoff existing goal/runtime/route в HistoricalBackgroundService или
BackgroundDecision, когда именно старый intent препятствует окончанию recovery. Полноценный
новый local combat AI, generic route redesign и sensor phase semantics — не TASK027.

## 5. Сравнение и отсутствие подмены

Сохранять исходные 8 ID026 как longitudinal список с причинными статусами; в новой farm
сцене baseline выбирается один раз из реально online+local кандидатов до измерения.
Нельзя заменять failed actor после baseline. Для смерти/дематериализации сохранять цепочку
эпох отдельно: новые counters не складывать в старый same-epoch farm PASS.
Legacy strict farm evaluator неизменён. Дополнительная lifecycle-классификация не заменяет
его вердикт. Native payload equality и законные marker transitions показывать раздельно.
