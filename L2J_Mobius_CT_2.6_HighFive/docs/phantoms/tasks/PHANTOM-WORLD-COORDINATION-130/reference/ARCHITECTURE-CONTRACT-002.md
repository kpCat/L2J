# Phantom World — единый архитектурный контракт жизненного цикла и социальных групп

**ADR-002 • 06.11.2026 • статус: проект архитектуры для реализации, не доказательство работающего runtime.**  
**Проект:** L2J Mobius CT 2.6 High Five; GitHub `kpCat/L2J`, ветка `feature/phantom-world`. Проверенный опубликованный baseline на момент подготовки: `faec72161f9e4f6231764e17d6f2637a4db080af`. Локальная TASK129 (`5cb04fe8a669ebf68e5f9ae0fa927055f81a4efe`) не опубликована. Контракт учитывает статический аудит патча TASK129 (F1–F9), журнал остановки и пользовательские требования. Перед реализацией перепроверить актуальный source и Git HEAD.

## 0. Цель и правила разработки

Дать существующим 5000 долговечным профилям единую логическую жизнь, автономные Party и согласованный переход в штатный World/Player без повторного расчёта одной операции, вечных WARM/RETRY или исчезновения на глазах игрока. Достичь целевых **128 управляемых физически материализованных Phantom**, если это позволяют подтверждённые ограничения производительности, **отдельно** от 250+ offline stores. Прекратить строить новые системы поверх неразрешённых конфликтов Intent/Presence/Background/Materialization/Party.

**Не переписывать L2J:** native `Player`, `Party`, `Clan`, `Combat`, `Inventory`, `Skills`, `GeoEngine`, `Gatekeeper` остаются авторитетными для физических действий. Один общий Scheduler; без второго AI, второго движка боёв или 5000 потоков. Стремиться к экономичному feedback loop: обычный детерминированный тест ~секунды, интеграционный ~минуты, профильная регрессия в обычном цикле до 5 минут; тяжёлый native/soak — отдельно и только после дешёвого доказательства.

**Главное свойство:** Safety + Liveness. Отказ безопасен, но имеет конечное ожидание или контролируемый следующий шаг; нельзя считать «ничего не сделал и не сломался» успехом игры.

## 1. Разделение идентичности, смысла и физического присутствия

| Сущность | Авторитетный ключ | Состояние и ответственность |
|---|---|---|
| Durable Profile | `profileId` | Класс, прогресс, имущество, социальная память, предпочтения, долговечное расписание. Не зависит от присутствия native `Player` |
| Intent / Goal | `profileId + goalId/revision` | Единственное выбранное намерение: Solo/Party/Commerce/Clan/Quest/Olympiad и его приоритет, срок, переходы |
| LogicalParty | `groupId + generation` | Лидер как `profileId`, roster 2–9, роли, цель, отношения, стадия, координаты/регион, background cursor. Может существовать при **0** физически материализованных участников |
| PhysicalPartySession | `groupId + generation + sessionEpoch` | Текущие `objectId`/materialization epochs, доказательство native Party; уничтожается/пересоздаётся при переходе режима |
| Native `Player` и stock `Party` | Real `objectId`, native owner/Party membership | Только они имеют боевые/loot/EXP/SP/skill/приглашения и штатные групповые права |

**Запрет:** `characterObjectId` и `materializationEpoch` **не являются обязательными полями логического участника**. Не хранить отсутствующего `Player` как ghost-member штатной L2J Party. Социальный договор группы не разрушается при обычной дематериализации; native Party безопасно завершается и при новом сеансе собирается по правилам игры.

**Люди:** живой игрок присоединяется только по штатному invite/accept, без записи человека в автоматические durable-offers. Если в Party один человек (или достоверно определён владелец), FollowTarget для управляемых фантомов — он, независимо от официального Party Leader. При нескольких людях и неизвестном owner — безопасный fallback к штатному Party Leader. Кланом руководит **официальный Clan Leader** по правам L2J, даже если пользователь является обычным членом. Olympiad временно выводит участника из Party без удаления клана/социальных связей.

## 2. Контракты шести существующих подсистем

### Intent — выбор и переходы между обязательствами
- **Единственный источник текущего игрового намерения**: использовать/развить текущий GoalStore/Decision Engine, не запускать параллельный intent scheduler.
- Выбирает и приоритизирует цели по данным Profile/Social/Clan; выдаёт версионированный запрос к owner конкретной операции.
- Handoff только через явные исходы `COMPLETED | YIELD_ACCEPTED | WAIT_UNTIL | REJECTED | REPLAN`. Не отнимает ownership у Commerce/Combat/Store/Navigation в процессе действия и не сбрасывает receipt/goal ради нового приглашения.
- Минимальные приоритеты: ручные законные действия человека и выживание; начатые необратимые/native-транзакции; принятое групповое обязательство; новые необязательные предложения. Конкретные продуктовые конфликты согласовать отдельно, а не выводить из имени метода.
- Срок ожидания + причина + условие возобновления записываются устойчиво. Одна personality/identity выбирает предпочтение поиска Party монотонно: 1–39 0%, 40–51 20%, 52–60 35%, 61–75 50%, 76–85 80%; Solo не запрещён.

### Presence — расписание и логическое допущение
- Отвечает **кто** логически доступен, занят, спит, имеет право физической активности и почему. Распределяет доли/квоты по регионам на 5000 durable без требуемого `World Player`.
- **Не** создаёт native `Player`, не выдаёт награды, не изменяет состав Party и не принимает полномочия Travel.
- Даёт типизированные факты `scheduleEligible`, `admitted`, `reason`, `nextEligibleAt`, текущий claim. Статус ACTIVE у планировщика не должен быть переименован в физическую материализацию.
- Социальные соглашения могут возникать в фоне, если профили способны на логические решения; спящий/занятый персонаж не принудительно активируется.

### Background — логическая и историческая деятельность
- Владелец catchup, background cursor, stock-совместимой симуляции времени, допустимых затрат и результатов для Solo/LogicalParty без native Player.
- Не соревнуется с Combat за те же минуты: интервалы Background и Native имеют **непересекающиеся ownership/ledger**; не начислять награды дважды. Не называть background roll «native kill».
- Выдаёт типизированный `NATIVE_CONTEXT_REQUIRED` с доказанным запросом и TTL в единый admission; когда спрос исчезает, не оставляет вечный lease. Возвращает причины и следующий допустимый переход. Не создаёт Player самостоятельно.
- При group-BACKGROUND сохраняет groupId/roster/leader, общую стратегию и каноническую позицию. Индивидуальные progress/items остаются в штатных owners. Сначала доказать корректность, потом утверждать скорость группового фонового фарма — не вводить бесплатную экономику.

### Materialization — единственный хозяин физического Player
- `PhantomMaterializationService` владеет созданием/возвратом/STORE/снятием `Player`, текущим native epoch, identity lease, выделением физического слота, проверкой `World` и фактических владельцев.
- Вход: **типизированный запрос** `HUMAN_OBSERVATION | AMBIENT_TARGET | LOGICAL_PARTY_WAKE | ACCEPTED_TRAVEL | NATIVE_CONTEXT`, с source identity, TTL, owner, requested importance. Ни один тип не наследует права другого (например, PARTY_WAKE не даёт travel или SHOP bypass).
- Решение admission согласовано с Presence/Background/Intent: один итоговый reason `WAIT_CALENDAR | WAIT_CAPACITY | WAIT_OWNER | WAIT_BACKGROUND | WAIT_ROUTE | STALE_EPOCH | DENIED_POLICY`, есть `retryAt/requiredChange`; UNKNOWN fail-closed **с диагностикой**, не тихое `false`.
- Не даёт мгновенный spawn рядом с человеком, если реальная каноническая позиция и правила игры этого не допускают. Нужно реальное движение/штатный GK; нельзя teleport через Geo ради FULL.
- Дематериализация только после graceful native handoff, завершённого боя/CAST/STORE, отсутствия наблюдателя и реального человека в Party, периода grace, без исчезновения на глазах. Активные недопустимые операции удерживают native. Без dual owner.

### Party — логическая группа и физическая проекция
- Отвечает за долговечный `LogicalParty` (необходимое минимальное расширение существующих PartyStore/Goal/ CAS) и отдельную временную native-привязку; **не** симулирует Skills/Combat/Navigation.
- Сначала bounded/paged durable discovery и переговоры 2–9 фантомов без материализации. `groupId/generation`, stable roster/roles, objective, leader, TTL. На одну identity не более одного активного группового commitment.
- Formation: multi-profile CAS reservation → единый авторитетный group aggregate commit → идемпотентная компенсация при сбое. Recovery по индексированным group records даже при 0 Player в World, не по странице materialized.
- Wake: хотя бы один физически материализованный член группы может запросить **bounded** materialization остальных доступных участников. Это не автоматический обход календаря, лимитов, занятости или экономических правил; при частичном допуске продолжать парой/меньшим составом или ждать, без вечного зависания.
- Foreground: штатные встречи по допустимой карте/Geo/GK, stock invitation/accept и native Party membership. Native Party Leader ≠ FollowTarget ≠ Clan Leader.
- Exit: `NATIVE_ACTIVE → DRAINING → BACKGROUND` по всей логической группе при отсутствии нужды World, с неизменным groupId. Group XP/loot не дублируются; при повторном появлении — новый physical epoch и законное создание native Party.

### Scheduler — один bounded диспетчер, не второй AI
- Один существующий `PhantomScheduler`: факты событий и bounded work batches, fairness/cursors, priority windows и budgets; 5000 профилей **не** означают 5000 thread/timer/task или full 5000 SQL reads на pulse.
- У любого `WAIT` есть event trigger или `nextDue`, дедупликация повторных запросов, лимит повторений без смены причины, надёжная диагностика; ничего не «бежит» каждые 100 мс ради неизменного `WAIT_ROUTE`.
- Наблюдаемость: `profileId/groupId`, phase, current owner, blocker code, nextDue, age/last meaningful action, relevant count. Не печатать секреты и не раздувать лог мегабайтами на каждый тик.
- Не отменяет leases и цели при истечении бюджетов; останавливает только собственные новые work items, оставляя owner корректно завершить текущие.

## 3. Машина состояний группы

`SEEKING → OFFERED → AGREED → BACKGROUND → WAKE_REQUESTED → ASSEMBLING → NATIVE_ACTIVE → DRAINING → BACKGROUND`.

Альтернативы: `EXPIRED`, `DECLINED`, `WAIT_*`, `REPLAN`, `DISBANDED`. Переход OFFERED→EXPIRED не уничтожает существующую AGREED группу. Отказ физического допуска **не** означает распуск логической группы.

Инварианты:
1. На одну identity не более одной актуальной логической группы и не более одной stock native Party; конфликтует с Solo/Store/Olympiad только через явные owners.
2. Durable membership на `profileId`; current native `objectId/epoch` только в `PhysicalPartySession`.
3. При STOP/STORE/background↔foreground сохраняются ресурсы и exactly-once settlement.
4. При materialize не «прижимаем» уже удалённую Party к наблюдателю; физическое появление/переход — штатные, географически законные.
5. Ошибка не превращается молча в «нет кандидатов».
6. Ни один guard не создаёт deadlock между `Party ↔ Supply ↔ Background ↔ Presence`; ownership сменяется через typed handoff с конечным WAIT/REPLAN.
7. Никакой переписки `Heal.java`, `TeleportHolder` или иных штатных обработчиков только ради сбора тестовой телеметрии без доказанного отсутствия альтернатив и регрессии обычных людей.

## 4. Население и отдельные счётчики

Нынешний `PhantomPopulationTarget=5000` — durable count, `PhantomPopulationActiveTarget=128` — расписание/допуск, `MaxMaterializedPhantoms=128` — **потолок** управляемых физических фантомов, не гарантия. `PhantomOfflineMerchantService` владеет отдельными настоящими offline `World Player`; они не занимают `PhantomMaterializationService` permits, но влияют на суммарную нагрузку World. Не суммировать категории без пересечений.

Требуемые отдельные runtime-метрики:
- `durableManagedProfiles`, `scheduledEligible`, `presenceAdmitted`, `logicalActive`, `logicalPartyCount`, `logicalPartyMembers`;
- `materializedManaged`, `materializationTarget`, `materializationCap`, `materializationPending`, `materializationDeniedByReason`, `nativePartyCount`;
- `nativeOfflineMerchants`, `humanOnline`, `worldTotalPlayers`, `visibleManagedNearHuman`, `backgroundGroupCount`;
- `lastUsefulActionAge`, `medianReadyToAction`, `groupOfferToFarmLatency`, `backgroundAndNativeSettlementConflicts`.

**Целевая политика:** 128 managed materialized при включённом мире и допустимых CPU/RAM/SQL/Geo, с region/near-human приоритетом. Если нет интереса человека, допустима ambient физическая популяция для оживления мира, а не обязательная полная дематериализация всего мира. Если 128 заняты, reservation группы ожидает ресурс или вытесняет **только** доказанно безопасного невидимого soft actor; не трогать offline shops. Для резерва выше 128 нужен отдельный профилированный physical cap (например 144/160), не слепая правка конфигурации.

В TEST прогоне 207 World Players (200 offline merchants + 7 иных) нельзя считать «128 активных фантомов». Фактическая перепись должна показывать disjoint категории и причины недостижения materialization target.

## 5. Исправления патча TASK129 (F1–F9)

| ID | Решение |
|---|---|
| F1 | Durable discovery по profileId с нулём World Player, без обязательных positive objectId/epoch для LogicalParty |
| F2 | Не отрезать top-4 anchors до проверки общего маршрута; canonical route preview включает штатный `WALK ingress`, `UNKNOWN` ≠ `UNREACHABLE` |
| F3 | Fair paged eligible/reachable discovery, до final cap проверять нужных кандидатов; доказать «16-й подходит» и «5-й маршрут подходит» |
| F4 | Single authoritative group aggregate + идемпотентный CAS/saga для member claims, восстановление без native Player |
| F5 | `catch (RuntimeException) {return false;}` заменить на bounded structured exception diagnostics с fail-closed boundary |
| F6 | Для healer поддержка и безопасная дорога не требуют solo DD combat-feasibility |
| F7 | Не выпускать ослабленное offscreen receipt/hash proof без отрицательных тестов и формального миграционного контракта; сохранить строгую защиту |
| F8 | Native Heal observer по возможности read-only; если затронут stock `Heal.java`, регрессии обычных людей и MP/HP before-after обязательны |
| F9 | Реальная логическая Party 2–9, без жёсткого DD+support+DD для любой группы. Ролевые ограничения определяет конкретное задание/спот |

Реализация не должна одновременно менять rates, Clan leader rights, PvP/PK legality, OwnerConfig secrets, Geo203 или выкатывать существующую незавершённую TASK129 в production. Хороший код и позитивные focused tests из 12 TASK129 commits сохраняются как исходный материал, но критичные F1/F7/F8 проходят audit до любого release merge.

## 6. Инженерная проверка и скорость цикла

Одна **цельная архитектурная TASK**, а не отдельные TASK для каждого неправильного branch/catch/anchor. Внутри задачи допускаются обычные engineering checkpoints, но они не становятся новыми оплачиваемыми R2/R3. Профильные тесты проверяют попарные контракты *до* GameServer.

1. **Быстрые deterministic:** 3 отсутствующих в World профиля создают и сохраняют LogicalParty; 2–9 members; конкурирующие offers; timeout, отказ и retryAt; выбор 5-го anchor и 16-го кандидата; stable preference. Цель — секунды.
2. **Интеграционные:** существующий GoalStore/Presence/Background/Materialization fake ports + настоящая тестовая SQL repository: restart с 0 native Players; group wake/partial admission; safe drain→background; idempotent CAS/settlement; source/owner hash regression; CPU/time. Цель — обычно 1–3 минуты.
3. **Native smoke только после 1–2:** одна предсказуемая законная группа из 2–3 фантомов, stock materialization, invite, один shared rewarded PvE, безопасный STORE/restart и новое действие; в отдельном сценарии native Heal/MP/HP. Нельзя подменять результат «готовой» mock party. Вся исходная готовность проверяется до CUT. Один полноценный запуск может быть длиннее пяти минут из-за загрузки GameServer; это **не** повод ослабить инварианты, но повод оптимизировать автоматизацию и частоту запусков.
4. **Популяционная регрессия:** 5000 durable, 250 offline магазинов, distinct 128-target, региональные quotas, CPU/SQL/heap/Geo/time-to-action. Отдельный performance benchmark, не 18-часовой тест каждого исправления.

**Правило прекращения бесполезной работы:** две попытки одного native сценария без новой подтверждённой причинной гипотезы → остановить этот lane, отладить deterministic boundary. Новая переподготовка World и многочасовая кампания запрещены до доказанного исправления входного условия. Лимит стоимости не подменяет качество: метрика — **полученная игровая способность / время и токены**.

## 7. План единой продуктовой задачи (после освобождения диска)

**Рабочее имя:** `PHANTOM-WORLD-COORDINATION-REWORK` (не присваивать номер до инвентаризации и согласования). Одно направление: source-of-truth Intent/Presence, logical Party, group materialization/background handoff, finite scheduling + дешёвый reusable test harness. Не строить новую всю клановую/осадную систему в этой задаче; но не ломать их существующие контракты.

**Внутри одного задания, не как отдельные микрозадачи:**
- A — аудит существующих владельцев, устранение F7/F8 и выбор минимальных seams; сохранить работающие части TASK129;
- B — единая семантика Intent/Presence/Background/Scheduler и типизированный admission with liveness;
- C — logical Party aggregate и direct durable discovery, start→restart→background при 0 World Players;
- D — wake→native Party→graceful demat with explicit counters (128 physical target, offline merchants separately), no safety bypass;
- E — интеграционная и одна native vertical, QA и bounded performance proof, scoped commits и отчёт.

**Нет формального FULL по числу папок/тестов:** завершение — проверяемые самостоятельные способности и отсутствие P1/P2; native доказательства только там, где native действительно требуется. Уровни приёмки отмечать FOCUSED/INTEGRATION/NATIVE/RESTART, без ложного ALL-PASS. Если архитектура не способна пройти дешёвые границы — не запускать следующий дорогой GameServer.

**Источники и ограничения:** `TASK129-review.patch`, `TASK129-independent-code-review.md`, `PHANTOM-WORLD-ADR-LOGICAL-PARTY-001.md`, текущие `PhantomSystem.java`, `PhantomPopulationManager.java`, `PhantomReconcileFirstActivityPort.java`, `PhantomAutonomousFarmParty.java`, `PhantomMaterializationService.java`, `PhantomBackgroundService.java`, `PhantomClanService.java`. События TEST129 и 12 local commits ещё не являются подтверждённой рабочей партией. Основной `D:\Codex\repos\L2J-phantom-world`/WIP109 только для чтения; clone/test schema/STOP/STORE/G0/normal push защищены.
