# HANDOFF — состояние без перечитывания старого чата

## База

`kpCat/L2J`, `feature/phantom-world`, HEAD `82fae37aff225e959213e1d0432b53d50360318a`; deployed source `de40c81411cf6501cb9446840aef98f7dd9369e2`. Последний запуск `Run-M1SmartContinuity.ps1` завершился `PHASE_TIMEOUT:PREWARM`. TestAdmin возвращён, REAL_LOGIN подтверждён. Контейнер составителя не подключён к локальному Windows runtime; свежие PID/health проверить на машине владельца.

## Что на самом деле показывает последний TSV

Источник: `docs/phantoms/tasks/PHANTOM-LIVE-M1-SMART-CONTINUITY-004/M1_CONNECTED_WORLD.tsv` на исходном HEAD.

- 15:19:31.650304500Z: profile 5079, `presenceReason=none`, admitted=true, `localityCurrent=false`, STORED/SLEEPING, requested ACTIVE, DEFERRED.
- 15:19:32.846936400Z: начало подхода, всё ещё none/admitted.
- 15:19:34.449525400Z: уже `presenceReason=offline`, admitted=false, locality=false, requested NEARBY_PERCEPTIBLE; далее PREWARM не материализуется.
- Идентификатор 5079 — только исторический пример; запрещено вшивать его в production/выбор следующего успешного теста.

По этой таблице нельзя установить, был ли OFFLINE настоящей календарной границей или техническим ecology fence. Raw schedule/nextBoundary/cursor в ней отсутствуют. Не называть это доказанным отказом materialization delegate: он мог вообще не вызываться. Сначала использовать уже существующий admission snapshot/логи для различения; если исторических данных нет, так и записать, не устраивать новый live-audit для их реконструкции.

## Подтверждённые места риска в коде

1. `PhantomPopulationManager.processReadySchedule`: `_effectiveState` становится SLEEPING при `!_ecology.permitsScheduling(...)`; это effective-значение затем передаётся `_presence.schedule(...)`. Calendar и readiness смешаны.
2. `PhantomSystem`: locality-gate синхронно вызывает `reconcileMaterializationDue`; periodic farm и background work тоже связаны с reconciliation. Ecology выполняет `process → begin/advance → persistence` в вызывающем потоке.
3. `PhantomNavigationService`: queue overflow возвращает REJECTED с ненулевым requestId и immediateResult, но без сохранения completed-записи. `PhantomVisibleFarmTravel.walk` читает только requestId/consume. Это воспроизводимый протокольный разрыв; его связь с конкретным прошлым персонажем не измерена.
4. Геометрический probe и live navigation используют разные проверки пути/ограничения. `PhantomGeoValidationRules` принимает до 256 точек; live policy — 64. Геометрический VALID_PATH не равен executable runtime route.

## Что уже существует и сохраняется

004: DEFERRED, soft recent-human 60 s, native-visible/REAL-party/action pins, soft-only eviction, bounded goal/route exclusions, AutoPlay session continuity и реакция на длительное отсутствие подходящей цели. `PhantomVisibleFarmTravel` уже имеет failure callback, ограниченные retries/общий срок Journey и 5-секундный hold; не описывать его как прежний бесконечный eager hold из 003.

Canonical broad 85/85, carried 1..85 и native death tests ранее прошли по отчётам. Это не результат текущей реализации 005. В 003 census охватил всего два unique profiles, не всю видимую популяцию; была реальная navigation/idle проблема. В 004 useful life/continuity не достигнуты из-за prewarm.

## Продуктовые границы

Нужны долговечные персонажи с непрерывной жизнью, а не декорации и не принудительно заспавненная витрина. Пати с REAL не должна рушиться от ухода из региона; это сохраняется через существующие pins. Следующий milestone после реально закрытого M1 — чат и совместная игра M2. Нынешняя задача не заменяет весь оставшийся план и не обещает готовность остальных подсистем.
