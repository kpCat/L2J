# План и журнал PHANTOM-LIVING-SYSTEMS-074

Начало: 2026-10-23 23:43 UTC. Product freeze: 2026-10-24 05:43 UTC; HARD: 06:43 UTC.
Baseline local/remote: `6057cc69728e5050d79806e5e6aa3c5a2bf898c8`, `feature/phantom-world`.

Спецификация: весь пакет `tasks/PHANTOM-LIVING-SYSTEMS-074/`. Прямое AUTHORITY074 разрешает последовательное выполнение без новых согласований и без worktrees.

| Направление | Действующий owner | Read-first причина / гипотеза | Предельный diff | Проверка |
|---|---|---|---|---|
| A | L2jPhantomSupplyJourney; NativeTravelActions/Navigation | Подтверждён unpaid intent073; finite local checkpoint перебор уже существует. Первичный native status и фактическая стабильность Trip неизвестны; диагноз не утверждён | Existing route lifecycle, сохранённый отказ и bounded fallback; без изменения экономики | Focused regression + owned native BUY/return/shot-consuming combat/restart |
| B | HistoricalBackgroundPlanner/Service; VisibleFarmTravel | Service после local exhaustion вызывает только replanVisibleLocal; global replan существует, но безопасный live bridge не используется | Local-first bounded topology fallback с прежними exclusions/CAS/claims | Existing planner/travel suites + native смена источника/первый segment |
| C | CombatService/L2jCombatBackend; LootDispositionPolicy | BLOCKED073 не доказывает потерю; receipts зависят от pickupSequence. Требуется классификация native rights/ack/timeout | Только доказанный pickup defect, прежняя disposition/SELL | Legal/negative pickup и native sell/keep, без создания предметов в measurement |
| D | PvpRetreatCoordinator/PvpService | Retreat требует дальний RESPAWN/CITY_CENTER; short tactical fallback отсутствует | Bounded Geo-valid escape через прежний Navigation/external lease; no fake ARRIVED | Focused retreat/admission + native CP/shot/retreat или точный PARTIAL |

## Последовательность

- [x] Прочитать все контракты074 и ближайшие owners; сверить local/remote baseline и dirt.
- [ ] Воспроизвести узкие дефекты; regression RED; минимальные production edits A–D; regression GREEN.
- [ ] Один компактный OWN074 runner с guards схем/root/UUID/ports; reuse existing primitives, старые runtime read-only.
- [ ] Focused checks и одна общая игровая приёмка; независимые статусы A–D.
- [ ] Source commit; один final Ant JAR из git archive committed source; provenance/hashes/Geo203.
- [ ] OWN074 candidate Main >3min, sameDB/sameJAR restart >2min, полезная activity, штатный STOP/DB0/JVM0/listeners0.
- [ ] Exact scope/encoding/diff; единый отчёт и normal commit/push; проверить remote SHA; без R2/075.

## Ограничения и review focus

JDK25/Ant, native High Five owners, никаких новых AI/economy/schedulers/dependencies. Reserve500, usefulPurchase, rates/tax/price сохраняются. Исторические071–073 акторы/DB/candidates/private inputs и human accounts не используются. Чужие untracked `.phantom-local`,068V,073 вне stage. Размер задачи явно допускает больше10 файлов: четыре product owners + focused tests + минимальный delivery074; без housekeeping.

Review: same-state retry и смена owner; exhausted source hysteresis; чужой/исчезнувший loot и party receipt; tactical waypoint safety/authority/deadline; exact committed-source и sameDB continuity. CONTRACT_PASS не заменяет NATIVE_EFFECT. ARRIVED требует фактического положения. TOTAL=PASS только по всем HARD; незакрытая lane не останавливает остальные.

## Журнал

- Read-first: root/module AGENTS, root README, workflow/release/current-status, master plan architecture/intake, task-package standard, build.xml; полный074 пакет; опубликованный073 отчёт/attempt/chain/acceptance; SupplyJourney/travel/local checkpoints, planner/service, native loot/CP/disposition, retreat. Других релевантных code-map/pattern файлов в текущем074 нет, SOURCE_MAP074 используется вместо них. Непроверено: native route/loot/PvP и OWN074 environment.
- Git intake: rev-parse/symbolic-ref/status/worktree list и remote feature SHA разрешены AUTHORITY074; network read повторён с escalation после restricted-network failure. HEAD совпал; tracked dirt отсутствует.
- Решение: AUTHORITY074 заменяет промежуточные design/plan approval и требование split; работаем одной кампанией. Независимые B/C/D могут выполняться агентами по dispatching-parallel-agents, shared build/runtime только у root.
- Уточнения владельца в текущей074: глобальный индекс опубликованных native SELL/MANUFACTURE вместо6000/nearest16; сравнение полной цены/дороги/stock, B+native покупка и расход; связанные component BUY→native производство; native Common/обычная crystallization как ресурсная альтернатива. Никакого нового goal/R2, срок прежний. Допустим точный PARTIAL отсутствующего шага дерева.
- Дополнительный bounded scope: World/Player publication hooks нужны для store index без world scan; CrystallizationService/DispositionPolicy — единая readonly native prospective inspection с обязательным owned inspect перед mutation; LocalRuntimeShutdown — exact074 control/UUID/root/schema guard. Другие native mechanics и schemas не перерабатываются.
- A focused RED→GREEN: первый local checkpoint совпадал с уже отказанным полным endpoint. Теперь выбирается другой checkpoint либо прежняя конечная alternative/source policy. Бесконечный успех/arrival не добавлен; primary status сохраняется в bounded reason.
- A первый native контроль:4000A объявленные prior savings +170A от2kills;174D1463 за3654A; возврат;2kills расход174→153. Test cleanup ошибочно повторил finishStop после STOPPED и не дошёл до materialization store. Read-only SQL подтвердил старый исходный inventory/EXP, поэтому этот эпизод НЕ является durable paid-chain PASS. Сохранён без reset. Отдельный единственный причинный control `task074chain` выполняется с исправленным reuse closeAll и обязательным store; исходный `task074supply` остаётся для точечнойC диагностики.
- C native RED0→0 / GREEN0→1 selected sequence на том же actor268480990/profile1. Detached normal combat selection не входил в NativeWork context; после16 targets native evidence достигал TARGET_CAP и переставал публиковать pickup receipts. Scalar reducer reproduces cap/receipt relationship, не имитирует17kills. Native rights и caps не ослаблены.
- Focused текущие: Navigation core51/51, performance1/1; Supply0715/5; visible itinerary4/4; historical idle3/3; combat ownership35/35; PvP combat13/13, admission2/2, CP3/3, restart1/1, performance1/1; commerce quote4/4, receipt9/9, decision7/7. Farm074 fallback/fences/admission, Supply074 checkpoint, Offline074 cost и Common12200 disposition GREEN. Две старые DB-mutating suites normal-gatekeeper-travel/commerce-supply остановлены на working-directory guard до DB; guard не обходится, их native эффекты проверяет OWN074 acceptance. Initial wrong PvP seed и wrong ConfigLoader cwd сохранены как ошибки запуска тестов.
- Fresh review: исправлены starvation первого неприемлемого distant candidate (bounded3native-admitted proposals) и stale full-BOM quote перед component BUY. Index lifecycle/lock ordering, selection context и prospective ownership проверены отдельно; Common paid full-chain native ещё NOT_OBSERVED.
- Подтверждённые границы: SELF_CRAFT executor требует canonical acquire.item root и acquisition.state; безопасного FARM-child handoff нет. NPC component procurement и normal/jewelry crystallization не объявляются универсальными. Owned material reference valuation — консервативная оценка, не фактическая цена смешанных paid lots. Новые покупки используют настоящие listing prices; Common surplus не вычитается из расходов как воображаемая выручка.
