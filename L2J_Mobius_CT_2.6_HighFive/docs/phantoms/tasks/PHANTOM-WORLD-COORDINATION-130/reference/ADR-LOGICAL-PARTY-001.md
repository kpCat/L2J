# Phantom World — ADR-001: логические Party, физическое присутствие и устойчивое продолжение игры

**Дата:** 2026-11-06  
**Статус:** проект архитектурного решения для утверждения владельцем; реализация ещё не выполнена.  
**Исходные данные:** локальный `TASK129-review.patch` (12 коммитов, HEAD `5cb04fe8a669ebf68e5f9ae0fa927055f81a4efe`), статический аудит `TASK129-independent-code-review.md`, опубликованная основа `kpCat/L2J`, `feature/phantom-world` / `faec72161f9e4f6231764e17d6f2637a4db080af`. На GitHub TASK129 ещё не опубликована. Текущий сервер TASK129 остановлен, TEST129 и WIP109 сохранены согласно последнему отчёту Codex. Это документ решений, не доказательство runtime.

## 1. Корневая ошибка, которую исправляем

Система ищет социальных партнёров среди уже материализованных `Player`. Долговечное rendezvous из TASK129 привязано к `characterObjectId + materializationEpoch`; дематериализация разрушает возможность долгоживущего логического объединения. При этом `PhantomPopulationActiveTarget=128` — лимит логически допущенных, а `MaxMaterializedPhantoms=128` — физический максимум, НЕ гарантированная физическая численность. 200 offline shops представлены отдельными `World Player` и не расходуют слоты `PhantomMaterializationService`, хотя потребляют ресурсы World.

**Целевая инверсия зависимостей:** durable profiles → social agreement → persistent logical group → group physical demand → canonical native `Player`/`Party` при необходимости → shared gameplay → safe return to background without losing group. Существующие L2J `Party`, Combat, Navigation, Background, Economy и Clan остаются владельцами штатных игровых эффектов: новая логическая группа НЕ является вторым движком игры.

## 2. Разделение смыслов

| Сущность | Срок жизни и ключ | Что может делать |
|---|---|---|
| `DurablePhantom` | `profileId` (устойчив), текущий class/goal/relations | Жить фоном, искать товарищей, хранить историю без `Player` в World |
| `LogicalParty` / `GroupIntent` | `groupId`, `generation`, `leaderProfileId`, persistent roster по `profileId` | Согласия фантомов, цель, роли, общая позиция/регион, график, фоновая жизнь, перегруппировка |
| `PhysicalPartyBinding` | `groupId + generation + sessionEpoch`, актуальные `objectId + materializationEpoch` | Только временная проверяемая привязка к **реальным** native `Player` и штатной `Party` |
| L2J native `Party` | Canonical stock runtime | Приглашения/принятия, Party Leader, loot/XP, skills, взаимодействие в World |

**Инвариант:** в логической Party могут числиться дематериализованные фантомы, но в штатную L2J `Party` нельзя записать отсутствующий `Player` или подделать native membership. На границе появляются новые физические epochs, а `groupId` и социальные связи остаются теми же.

**Для людей:** настоящий игрок участвует **только** через штатное приглашение/согласие и native `Party`; нельзя добавлять офлайн-человека как «согласившегося» логического участника. При mixed Party с одним человеком follow anchor = человек, при наличии подтверждённого владельца — владелец, иначе законный fallback к official Party Leader. Official Party/Clan leader права не меняются. Во время Olympiad фантом покидает native Party по правилам игры, не теряет клан/социальные контакты.

## 3. Машина состояний LogicalParty

`PROPOSED → AGREED → BACKGROUND → WAKE_REQUESTED → ASSEMBLING → NATIVE_ACTIVE → DRAINING → BACKGROUND`.  
Отказы: `DECLINED`, `EXPIRED`, `REPLAN`, `DISBANDED`. Отмена предложения **не** равна уничтожению уже согласованной группы.

- `PROPOSED`: предложения/ответы фантомов по устойчивым `profileId`, доступность из durable population, не `materializedFarmMembers`. Сохранять identity предложения, TTL, роли, native eligibility как прогноз, не наличие `World Player`.
- `AGREED/BACKGROUND`: одна долговечная запись группы и согласованные membership reservations. Предложения можно создавать и подтверждать при **0** `Player` в World. Группа знает стратегическую цель, режим фарма и достаточность состава без физических `objectId`.
- `WAKE_REQUESTED`: появляется законный физический спрос — хотя бы один действующий materialized Phantom из группы, живой игрок рядом, принятая игровая встреча/другое native событие. Один физически присутствующий член **инициирует попытку** материализации остальных доступных участников, но не выдаёт абсолютного права обхода расписания, магазинов, ongoing операций и лимита слотов.
- `ASSEMBLING`: участники получают bounded admission, материализуются **в канонических допустимых точках/состоянии**, затем реально идут через существующие Geo/Navigation/Gatekeeper и native invite/accept. Нельзя моментально spawn/teleport рядом с видящим игроком, выдавать EXP, предметы и ресурсы. Если кому-то отказано, group stays AGREED, допускается 2-person native Party/замена участника; ожидание не бесконечно.
- `NATIVE_ACTIVE`: native `Party` действительно существует и содержит актуальный roster; все combat/heal/loot/gameplay эффекты через stock Player. Group intent живёт параллельно только как надстройка цели, **не** проводит фоновую награду за те же интервалы.
- `DRAINING`: если поблизости нет настоящих наблюдателей, закончился grace period и нет combat/party-with-human/STORE/craft/shop/in-flight, прекратить старт новых операций, спокойно завершить текущие, сохранить каждого Player/Party receipts и только тогда перейти в `BACKGROUND`. Нельзя исчезать на глазах. Если человек остаётся в party, продолжать follow; уход из прежнего спота не значит отсутствия наблюдения.
- `BACKGROUND`: группа продолжает логическую жизнь, поддерживает membership и общую цель через **существующий** Background с ограниченной моделью группового PvE/расходников/риска. Не выдавать фоновые действия за реальные native kills. При новом спросе — пробуждение с новым `sessionEpoch` без копирования старых `objectId`.

**Запрещён split-brain:** не может одновременно происходить foreground combat/loot и начисление background group rewards для того же group interval; все переходы и receipts идемпотентны, current `group generation`/owner epoch проверяются CAS.

## 4. Владение и восстановление без нового shadow engine

1. Один durable **group aggregate** (`groupId`, generation, leader, roster+roles, target, location, phase, lastBackgroundCursor, version) — источник истины о логической группе. Использовать подходящий существующий `PhantomPartyStore`/goal ownership, если он выдерживает group-level atomicity. Если не выдерживает — минимальный versioned aggregate в текущем repository, а **не** вторая система Scheduler/Combat/Navigation.
2. На каждом `profileId` — CAS reservation/association (`groupId`, `generation`, kind), максимум одна активная автономная группа; конкурирующие предложения не перезаписывают текущую запись.
3. Multi-profile group formation — bounded saga: acquire in stable order → verify all → atomically declare group AGREED (single aggregate CAS) → compensate/release on expiry/failure. Durable recovery проходит по индексированным **group records**, а не через перебор материализованной page. Не создавать unbounded database scan на AI tick.
4. Physical bindings ephemeral: `objectId`/epoch пишутся только в текущую session snapshot, никогда как обязательная идентичность логического участника. При restore группа сохранилась, physical binding пересобирается из реального World/current materialization.
5. Если лидер умер/недоступен, group goal получает явное `REPLAN`/смену лидера по утверждённой внутренней политике, при этом штатные права Clan Leader неприкосновенны.

## 5. Единый разрешённый переход в World

Сегодня `Presence`, `Ecology`, `Background`, `ReconcileFirstActivityPort`, `MaterializationService` различно трактуют signal/demand. Вместо добавления очередного общего override:
- оставить единственный решающий admission contract: факты `schedule eligibility`, `presence`, `group demand`, `native-context demand`, `human locality`, `active lease`, `supply`, `capacity`, `in-flight`, `World visibility`;
- выражать demand **типизированным основанием** с TTL/owner/priority: `HUMAN_LOCAL`, `LOGICAL_GROUP_WAKE`, `NATIVE_CONTEXT`, `ACCEPTED_TRAVEL`, `IN_FLIGHT`, `AMBIENT_PRESENCE`; одно основание не даёт полномочий другого;
- каждый отказ возвращает `reason`, `nextDue/requiredChange` и владельца решения (например `WAIT_CAPACITY`, `WAIT_OWNER`, `WAIT_CALENDAR`, `WAIT_ROUTE`, `STALE_EPOCH`, `UNKNOWN`); при неизвестности fail closed, но **не бесконечный silent RETRY**;
- group-demand reserve bounded slots, with fairness/TTL, без монополизации всей capacity одной девяткой;
- нужная materialization не отменяет фоновый `catchup` без reconciliation/receipt; когда физическая часть допускается — штатный lifecycle.

## 6. Численность World / наблюдатели

Требования владельца: **5000 durable identities; 250+ stock offline shops; не меньше 128 физически присутствующих управляемых Phantom при достаточных ресурсах** (распределённых по регионам и заметных людям). `128` — отдельная **цель**, а не только потолок. Нужны различающиеся метрики: `durable`, `logical ACTIVE`, `materialized managed`, `offline shops`, `World total`, `physical target`, `requested`, `denied by reason`, `hard cap`, `reserved party slots`.

Рекомендуемая политика для будущего измерения: `TargetMaterializedManaged = 128` и достаточный physical hard-cap **выше** 128 (например, 160 с резервом до 32 для срочных групп и присутствия человека), но конкретные значения подтверждаются профилированием CPU/RAM/SQL/knownlist/Geo, а не слепой сменой INI. Уровень near-player приоритетнее случайных удалённых игроков; невидимые автономные группы могут жить фоном. 200+ offline merchants отдельно по slot accounting, но учитываются в бюджете World и нагрузке.

Переход 1 materialized member → wake остальных **не означает мгновенно 8 дополнительных Player**. Это заявка на bounded admissions; при отсутствии ресурса группа может продолжать частичным составом или фоном, честно сообщая причину. Никакой бесконечной цепной материализации.

## 7. Поиск Party и маршрутов: исправить найденные F1–F9

| Находка | Обязательное решение |
|---|---|
| **F1** материализация нужна до social discovery | Индекс durable profiles и stable logical group; `objectId`/epoch лишь physical binding |
| **F2** `.limit(4)` до общего маршрута | Canonical `RoutePreview` через **существующий** full travel planner, в том числе WALK ingress; `UNKNOWN` не считать `UNREACHABLE`; не отклонять группу из-за отсутствия graph-only пути |
| **F3** `.limit(15)` до eligibility/reachability | Пагинация через весь доступный набор с bounded work per pulse; фильтры и ranking до окончательного ограничения состава; честное `BUDGET_EXHAUSTED`, а не ложное `NO_CANDIDATES` |
| **F4** CAS по участникам, recovery только materialized page | Один durable group aggregate + CAS membership saga, durable group recovery с 0 World Player, идемпотентное expiry/replan |
| **F5** `catch(RuntimeException){return false;}` | Structured bounded error event/metric, profile/group/phase/reason, нет скрытой ошибки; safe refusal без проглатывания |
| **F6** healer предфильтруют solo combat feasibility | Разные требования боевой роли и безопасного support travel; нельзя выдавать support персонажу фиктивный DAMAGE/XP |
| **F7** `offscreenReceiptHashCurrent` ослабляет hash-check для untagged | При непустом `expectedAfterHash` не разрешать конфликт без доказанной точной migration/version; предоставить negative tests для stale/corrupt receipt; до прохождения не сливать |
| **F8** observer затрагивает stock `Heal.java` | По возможности read-only наблюдение на границе уже существующего skill/Combat, без изменения поведения HP-writer; MP до/после и HP до/после в native witness; regressions ordinary humans |
| **F9** data model 2–9, engine требует 3–4 | Логическая Party разрешает 2–9 и частичную группу, опциональная поддержка, штатный max native Party=9; отдельный END-TO-END тест 2 и 3, потом 9 |

`Phase.REPLAN` и `WAIT_*` могут быть результатами planner, но terminal `CANCELLED` только по доказанной невосстановимости/expiry, а не потому, что 5-й спот не успели проверить. Группа сохраняет выбранную цель и меняет route через существующий Navigation CAS; не создаём универсальный новый Travel engine.

Предпочтения поиска по уровню **не менять**: 1–39 = 0%; 40–51 = 20%; 52–60 = 35%; 61–75 = 50%; 76–85 = 80%. Воспроизводимая индивидуальная склонность; 0% до40 запрещает только *самостоятельный поиск*, не native приглашение человека. Наличие одной логической Party не даёт права на одновременные несколько клановых Party, shop или Olympiad.

## 8. Порядок применения и дешёвые критерии готовности

**Шаг 0 — guard перед любым слиянием (без native-кампании):** сохранить 12 локальных коммитов и отдельные черновики. Проверить F7 hash-negative, F8 impact обычного Heal и F5 диагностические причины. Не публиковать автоматически весь paused HEAD, не терять WIP109.

**Шаг 1 — единая модель без GameServer:** три НЕматериализованных durable profile находят друг друга, соглашаются, создают логическую группу; после перезапуска group store все трое остаются в roster без `objectId`/epoch; конкурентное предложение одному отклонено. Детерминированные тесты как быструю обратную связь.

**Шаг 2 — boundary без полноценной экономики:** одна materialization demand от члена группы вызывает bounded wake остальных; native `Player` identities проверяются по новым epochs; невозможность/недостаток slots → явный `WAIT_*`, без двойной materialize, без сброса background cursor; после исчезновения observers group DRAINING→BACKGROUND, сохраняет members/reward ledger exactly once. Доказательство в integrated test на настоящих repositories/port interfaces (без полного GameServer пока можно).

**Шаг 3 — одна настоящая маленькая native вертикаль:** от согласованных профилей к 2 native Player и реальному invite+accept, одному законному travel и одному rewarded group kill. Потом sameDB/sameJAR restart: ещё одно полезное групповое действие, никаких фальшивых наград. Heal проверить отдельной focused/native серией, не делать его условием того, что само формирование Party «не существует».

**Шаг 4 — полная модель 3/9 и фоновая жизнь:** healer, групповые EXP/SP/loot, зафиксированный переход native→background→native, single World Player triggers assembly, без произвольной teleport. Измерить производительность 5000 durable+250 offline stores + target128 managed materialized, отдельно per-region/near-human факты, в собственной TEST schema.

Каждый шаг даёт **самостоятельно проверяемую завершённую способность**. Если шаг 1 не проходит, нет смысла запускать GameServer. Если шаг 3 не проходит, не переходить к осадам, кланам и full-world soak. Никакого искусственного требования провести 18-часовую кампанию ради одного общего FULL.

## 9. Условия надёжности

- Логическая группа и native Party имеют чёткую one-way authority: native Party никого не «знает» без Player, а logical membership не вручает native party rights.
- Не дематериализовать на глазах, посреди combat/loot/craft/store/in-flight или при присутствии живого участника Party; внешнее наблюдение не всегда полностью отражается одним human locality флагом.
- В background награды и расходы — только через утверждённые существующие симуляционные транзакции и group interval ledger. Фоновый rewarded event не называется «native kill»; в foreground не симулировать те же минуты второй раз.
- При отсутствии достижимого/безопасного маршрута использовать `WAIT_ROUTE` + replan/TTL, не спавнить телепортом рядом с лидером. Вход WALK/GK с Geo/doors/LOS остаётся настоящим.
- Native official Clan Leader управляет стратегией клана, native Party Leader — полномочиями группы, человек-участник — может быть FollowTarget без передачи лидерства. Native Olympiad individual leave не удаляет клан и отношения.
- Разработка в независимом physical clone от опубликованной ветки с WIP109 read-only; G0/TEST schema/protected hash/normal STOP/STORE остаются. Закладывать reusable diagnostics/fixture, а не новые 385-МБ архивы на каждую правку.

## 10. Открытые вопросы (решить до изменения соответствующих продуктовых правил)

1. Допускается ли *принудительная* физическая активация спящей по календарю логической Party ради одного материализованного участника? Рекомендация: **нет**; только те участники, у кого это законное короткое social demand без нарушения занятости/запретов. Остальных ждать/заменять/продолжать меньшим составом.
2. С какой частотой фоновые Party должны получать симулированные EXP/SP/ресурсы и как делить risk/death/loot? Рекомендация: переиспользовать существующую Background model и согласовать отдельный product policy после доказательства group persistence; не придумывать multipliers ради теста.
3. Насколько строго держать 128 физических фантомов при отсутствии людей и высокой CPU-нагрузке? Рекомендация: `target=128` с нагрузочным guard и честным объяснением дефицита, резерв отдельный; *не* форсировать 128 через unsafe bypass.

**Решение для следующей работы:** не создавать отдельный клановый движок и не строить TASK130 до завершения этой модели. Не продолжать нынешний TASK129-диалог с огромным старым контекстом; после утверждения создать *отдельную управляемую архитектурную задачу*, которая сначала завершит шаги 0–2 и не обещает автоматически 3–4.
