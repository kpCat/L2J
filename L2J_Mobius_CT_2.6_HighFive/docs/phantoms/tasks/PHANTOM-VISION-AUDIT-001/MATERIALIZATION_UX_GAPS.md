# Materialization UX: текущий путь и риск pop-in

## Что делает production

1. `PhantomSystem.start` формирует human locality из `World.getPlayers()`: только online REAL игроки без headless outbound session; выбор ограничен первыми 256. Передаются координаты и instance. Это выборка активных людей, а не снимок клиентской камеры.
2. `PhantomHumanLocalityControl.onPulse` обновляет выборку раз в 1 000 мс. Для каждого человека `PhantomTopologyService.perceptibleProfilesAt(human, TARGETABILITY, 1024)` ищет фантомов в текущем наиболее конкретном topology node и напрямую perceptible соседях. В последнем случае используется topology edge/channel; число 1024 — предел кандидатов, не радиус видимости. Найденным online профилям отправляется WARM relevance signal с TTL 3 000 мс; `isLocal` проверяет последнее множество, статус и TTL.
3. `PhantomSchedulerPolicy.productionDefaults` задаёт cadence ACTIVE 100 мс, NEARBY 250 мс, WARM 1 000 мс, BACKGROUND 300 000 мс с jitter 300 000–900 000 мс и demotion grace 2 000 мс. `PhantomScheduler.transitionPlanLocked` использует временную задержку при понижении уровня, а `prepareWorkLocked` передаёт работу по cadence. Эти величины не являются гарантией времени до появления: ещё нужны pulse, reconcile и admission. Фактический pulse зависит от private runtime config; в этом audit он не запускался.
4. В `PhantomSystem.start` установлен `PhantomReconcileFirstActivityPort`: для local profile вызывается `PhantomPeriodicEcologyService.reconcileMaterializationDue`. Затем материализация проходит через `PhantomMaterializationService.materialize` с bounded concurrency. `PhantomMaterializedPlayer.materialize` загружает настоящего `Player`, применяет durable state через `PhantomBackgroundService.afterPlayerLoad` и вызывает `spawnMe()`; клиент получает native spawn обычного персонажа.
5. При уходе из locality scheduler может понизить уровень после 2-секундной grace; cleanup дематериализует и сохраняет состояние. Временная grace защищает от коротких уходов, но сама не задаёт разные пространственные границы входа/выхода.

## Точный UX пробел

- В просмотренном production path нет явного client-visibility-distance prewarm radius, правила «материализовать раньше входа в обзор» или проверки camera/line-of-sight. Текущий критерий TARGETABILITY отвечает топологии взаимодействия. Соседнее ребро может оказаться слишком поздним для конкретного маршрута и дальности отображения клиента.
- Spatial hysteresis «войти раньше / уйти дальше» не найден. **Временная** demotion grace 2 с есть; поэтому утверждать, что hysteresis полностью отсутствует, было бы неверно.
- Обновление human set раз в 1 с, scheduler/reconcile/admission и создание Player происходят после обнаружения local relevance. Если REAL игрок уже видит точку появления, `spawnMe()` может стать заметным pop-in. Это вывод из порядка операций, а не измеренная частота дефекта.
- `ACTIVE`, `STABLE`, `WORK_DELIVERED` в диагностике не означают движение/бой/чат. После spawn нормальное действие зависит от наличия Goal, candidate и успешного handler. Неподвижность конкретного фантома нельзя причинно связать с materialization без профильной трассы.

## Что проверить в следующей вертикали

Один контролируемый клиентский маршрут: REAL игрок подходит к заранее выбранным durable профилям через границу topology node, задерживается, отходит и повторяет вход. Зафиксировать координаты/время первого native spawn и client visibility, переходы WARM/ACTIVE и dematerialization, число повторных появлений, а также Goal/candidate/handler для одного неподвижного профиля. Приёмка должна требовать отсутствие видимого появления перед камерой и отсутствие повторного flicker на границе, плюс осмысленное действие одного обычного видимого фантома. Изменение алгоритма и проведение опыта выходят за scope этого audit.

Текущая live база: `docs/phantoms/tasks/LIVE-003-RUNTIME-SCALE-10000/EVIDENCE.md` фиксирует несколько materialized фантомов и короткое наблюдение; `LIVE-003E-FINISH-10000-PERSISTED/EVIDENCE.md` подтверждает масштаб/soak. Ни один отчёт не измеряет дистанцию или частоту pop-in.
