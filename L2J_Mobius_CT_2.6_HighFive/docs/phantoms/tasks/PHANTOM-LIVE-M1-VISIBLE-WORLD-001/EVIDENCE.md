# EVIDENCE — accepted starting ledger

Baseline HEAD: `7872e18fbf37177cf08ab23f342a25e3742bb1d0`

Accepted: functional 10k scale GREEN; LocalPlay Pilot GREEN; 8-char arm live-proven; TestAdmin REAL_LOGIN/non-GM/no PersonalQoL; pilot status/sit/stand/move/teleport/reuse proven. Final pilot `NO_CANDIDATE` means chat/invite not attempted.

Vision audit: locality ~1s, topology TARGETABILITY, ~3s TTL, temporal demotion grace, no proven client-visibility prewarm or spatial hysteresis, native spawn after locality/reconcile/admission. Scheduler ACTIVE/STABLE/WORK_DELIVERED is not gameplay proof. Exact idle cause requires runtime trace.

User observation: multiple level-1 phantom players visible around Gremlins/newbie area; they appeared to stand still. This proves symptom, not cause.

Existing `PhantomSelectedDecisionTrace` already records Goal/candidate/plan/step/result/reason. Reuse it.

## READ_FIRST — 2026-09-27

- `git rev-parse HEAD` = `7872e18fbf37177cf08ab23f342a25e3742bb1d0`; branch `feature/phantom-world`, upstream `origin/feature/phantom-world`. Checkout уже содержал чужие modified/untracked файлы. Для `PhantomMaterializationService.java` текущий `git diff --` не показал семантических строк, только предупреждение LF/CRLF. Эти изменения не тронуты.
- Прочитаны корневой `Agents.md`, обязательные master/workflow/task-package документы, весь заданный пакет M1, материализационный вывод Vision Audit, финальный результат/передача Pilot, и адресные исходники `World`, `WorldObject`, `PhantomHumanLocalityControl`, `PhantomTopologyService/Query/Point`, `PhantomSystem`, `PhantomBackgroundService/Decision`, `PhantomDecisionEngine`, `LocalPlayPilotActions/Service` и существующие focused suites. Вложенных `AGENTS.md`, module README, `DEVELOPMENT_CHAT_HANDOFF.md`, `CURRENT_GENERATOR_STATE.*`, `CONTEXT_INDEX.md` и отдельного code-map/pattern-файла для M1 нет. Повторный поиск не нужен.
- Локальные аналоги: `PhantomTopologyPerceptionSuite.testHumanPointLocality`, `testGroundNormalizedRouteHumanLocality`; существующие bounded `perceptibleProfilesAt` и `PhantomSchedulerRelevanceSignalPort`; `PhantomTopologySchedulerSignalIntegrationSuite`; Pilot `SNAPSHOT_PHANTOMS`/`STOP`. Паттерн для будущего изменения: bounded topology query → relevance signal → штатный scheduler/materialization и focused Ant suite; для gameplay — сохранённый Goal → candidate → handler и native action.
- Native `World.SHIFT_BY=11` даёт регион 2048×2048. `World` распространяет object info по текущему и соседним регионам, `forEachVisibleObject` проверяет instance, `WorldObject.isVisibleFor` — invisibility/GM. Поэтому единого native circular visibility radius в изученном пути нет; prewarm надо сравнивать с региональной границей/областью, не с выдуманным числом.
- Locality сейчас раз в 1000 ms вызывает `perceptibleProfilesAt(... TARGETABILITY, 1024)`, затем посылает WARM с TTL 3000 ms. Это topology bound, без доказанной пространственной prewarm/hysteresis. `farm.background` для WARM/ACTIVE живого профиля возвращает `REPLAN recovery.not_dead`; это только статическая гипотеза idle до профильной трассы.
- Штатный `Check-LocalPlay.ps1` подтвердил RUNNING LoginServer/GameServer, Population=10000, Active=64, MaterializedCap=128, PulseMs=100; diagnostics=False. Действующая безопасная Pilot-привязка REAL_LOGIN TestAdmin была доступна без нового действия пользователя. Snapshot 2026-09-27T15:44:44Z: TestAdmin (-90825,248162,-3568, instance 0), ближайший admitted natural profile 6 (-90875,248162,-3568, instance 0), расстояние 50, `materialized=false`. Повторный успешный snapshot 15:45:36Z показал то же. Между ними Pilot run штатно остановлен после обнаруженного `SESSION_OR_DEADLINE`; новый run после снимка тоже остановлен. Это не доказательство pop-in или точного idle cause.
- Выбранный trace сейчас недоступен: `EnablePhantomDiagnostics=False` в эффективном runtime, `PhantomSystem` создаёт `PhantomSelectedDecisionTrace` disabled и не передаёт его `PhantomDecisionEngine`. Existing `AdminPhantom` обслуживает выбор trace только через GM command; TestAdmin намеренно non-GM, а Pilot не имеет trace operation. Перезапуск/диагностическое расширение до RED/GREEN и clean detached jar противоречит порядку TASK. Профильный Goal/candidate/handler и реальная граница появления остаются непроверенными; production fix по предположению запрещён.

## Blocker and scope

Blocker: `M1_SELECTED_TRACE_DISABLED_ORDER_CONFLICT`. `TASK.md`/`ACCEPTANCE.md` требуют существующий selected trace обычного материализованного профиля до visible-life fix. Текущий runtime trace не собирает. Включение требует конфигурационного restart, а M1 разрешает deployment только после всего automated GREEN и detached jar; существующий Pilot не предоставляет безопасный non-GM trace selector. Нет доказанного RED для visible-life и нет права подменить его статической догадкой. Текущая Pilot snapshot выбирает ближайший admitted профиль, но тот может быть не материализован; делать из него «видимый idle sample» было бы неверно.

Сделано: только read-first и read-only Pilot snapshot/stop. Не сделано: source/test изменения, RED/GREEN, detached jar, deployment, клиентский визуальный check, direct PLAY DML/DDL, ручной вход/arm. USER_CLIENT_ACTION=0. Population=10000, ActiveTarget=64, MaxMaterialized=128, PulseMs=100 оставлены. Несвязанные пользовательские файлы не изменялись.
