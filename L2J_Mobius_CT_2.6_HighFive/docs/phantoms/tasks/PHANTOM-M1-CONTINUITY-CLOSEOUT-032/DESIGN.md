# DESIGN032 — законченный control transition, не ещё один controller

## Выбранное решение

Переиспользовать нынешний scheduler/DecisionEngine/PhantomHistoricalBackgroundService.
Раскрыть точную причину решения в том же вызове, который реально останавливает session.
Исправить доказанный путь producer→finite recovery→current goal→native execution.
Не создавать новую durable state machine, вторую очередь combat, C# или новую БД.

Отклонены:
- blanket allow true, очистка всех exclusions, увеличение recovery timeout;
- запрет общей цели, обучение только на слабом NPC;
- возврат к C21 потому что там подобралась успешная группа;
- переписывание ownership/persistence до доказанной потери данных.

## Контракт diagnosis

Одна evaluation, прежний short-circuit порядок и прежние исключения.
Причины разделены: READY / COOLDOWN / PROTOCOL_BLOCK / ROUTE_BLOCK /
TARGET_EXCLUDED / STORED_GOAL_MISMATCH. Дополнительные failures до этого метода
снимать отдельно, не сливать их с false handler.

Запись: run/source/Jar, monotonic time, profile/object/epoch, caller:start|await|prepare,
goalId/revision/componentVersion, firstReason, episode started/cooldownUntil,
originating failure reason/step/goal/epoch, публикация/новая revision, native progress.
Писать только для выбранных диагностических профилей и при изменении decision tuple;
existing recorder/queue. Не опрашивать SQL дважды ради reason. Не исполнять gameplay
через JDI, не брать новый lock вокруг штатных вызовов.

## Разрешённые решения после установления причины

A. COOLDOWN: доказать ограниченное ожидание и продолжение после срока. Повторный tick
не должен продлевать тот же episode только потому, что ещё нет новой награды.
Законный cooldown сохранить; параметры45/90/120с не менять. Если это часть длинного
циклического retry, исправлять producer/выход, не метрический порог.

B. ROUTE/TARGET: отличать один путь/один объект от запрета всей зоны. Старый terminal
не может запрещать иной current plan без совпадения ownership. Восстановление через
существующий replanVisibleLocal и атомарный replacePlan; не открывать непроверенный
путь через воду. При исчерпании вариантов нужен конечный, наблюдаемый control outcome.
Не дематериализовать перед REAL лишь для сброса тестового долга/epoch.

C. PROTOCOL: оставлять fenced, пока подтверждённый протокол нарушен. Исправить producer,
если он классифицировал обычную игровую неудачу как protocol; проверить парным
negative stale/foreign тестом. Не TTL-разбан истинного ownership violation.

D. GOAL: existing pre-work finishVisiblePublication/reload, exact old/new CAS.
Handler не публикует goal внутри inFlight. Старый callback не влияет на новую revision.
После подтверждённого перехода один текущий исполнитель, без duplicate rewards.

E. RESOURCE117: отдельно доказать первый момент утраты полезного прогресса и его
связь с sit/stand, MP spend, cast, targets, rest episode, phase/debt. Stock resource
mechanics не изменять; сохранить C26 reentry standUp и cooperative semantics.
Падение цели между launch/impact само по себе допустимо; не возвращать MP и не
придумывать урон ради progress. Любой отказ callback/owner документировать отдельно.

## Persistence/away

См. LINEAGE.md и RUNBOOK.md. Это финальная проверка текущего fix, а не разрешение
на новую транзакционную архитектуру. Metadata projection обязана сохранить native
поля; chain version+1 без совпадения payload/operation identity недостаточна.
Отсутствующая telemetry не считается поломкой source, но не даёт GREEN.

## Критерий границы scope

Переходы policy/replan/resource/locality/release queue разрешены в SOURCE_MAP после RED.
Настоящий дефект rewards writer, store protocol или shared executors вне разрешённых
методов — зафиксировать, сохранить проверенный кандидат, не расширять task молча.
Количество таких findings не заменяет решение об архитектуре.
