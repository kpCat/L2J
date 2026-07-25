# Phantom Bots Roadmap

**Путь в репозитории:** `docs/PHANTOM_BOTS_ROADMAP.md`  
**Проект:** Phantom World для L2J Mobius High Five  
**Ветка разработки:** `feature/phantom-world`  
**Актуальная дата дорожной карты:** 2026-07-25  
**Статус документа:** архитектурный ориентир для ревью и постановки следующих GOAL-задач  
**Текущий принятый baseline перед выполняемой Task 004:** `eb008f2216b3e8381c0181d71ce200bbf4907ac7`

---

## 1. Назначение документа

Этот документ задаёт единую архитектурную дорожную карту разработки Phantom World: от безопасного headless-игрока до масштабируемой симуляции живых игроков Lineage 2 High Five.

Дорожная карта используется для:

- постановки следующих GOAL-задач;
- проверки соответствия каждой задачи общей архитектуре;
- контроля scope и недопущения преждевременного смешивания подсистем;
- оценки прогресса не только по номеру очередной задачи, но и по движению к рабочей системе;
- принятия решений о необходимости hotfix, пересмотра архитектуры или изменения порядка задач;
- предотвращения искусственного дробления работы на десятки малополезных микрозадач.

Текущий активный task package всегда является непосредственным контрактом для Codex. Дорожная карта не должна самовольно менять уже выполняемую задачу.

**Task 004 в момент создания этого документа выполняется в Codex. Её постановка, scope и критерии не изменяются этим документом.**

---

## 2. Цель разработки

Цель Phantom World — получить не визуально похожих NPC, а программную симуляцию живых игроков Lineage 2 High Five, использующую реальные серверные механики.

Фантомные игроки должны уметь:

- начинать развитие с первого уровня;
- получать EXP, SP, адену, ресурсы, предметы и экипировку;
- получать профессии и осваивать классовые возможности;
- выбирать подходящие зоны и цели;
- фармить, использовать spoil, manor и поддерживаемые quest-механики;
- покупать расходники, продавать добычу, торговать и крафтить;
- вступать в party, clan и другие социальные структуры;
- учитывать роли, состав группы, риск и ожидаемую выгоду;
- разумно использовать умения класса;
- реагировать на настоящих игроков;
- договариваться, спорить, сотрудничать и конфликтовать;
- помнить важные события, договорённости и отношения;
- понимать игровой сленг, объекты мира и причины просьб игрока;
- продолжать правдоподобную жизнь вне зоны наблюдения;
- корректно материализовываться при появлении настоящего игрока.

Во время работы сервера запрещена обязательная зависимость от LLM или внешнего AI-сервиса. Runtime-интеллект строится на:

- серверном Java-коде;
- конфигурации;
- детерминированных данных;
- Semantic Pack;
- read-only знаниях существующего сервера;
- Utility AI;
- goal/planning-модели;
- personality, memory и reputation;
- bounded scheduler и многоуровневой симуляции.

Допустимые offline-инструменты подготовки данных не должны становиться runtime-зависимостью сервера. Все обязательные runtime-данные должны поставляться вместе с сервером и проходить версионирование и проверку.

---

## 3. Главные архитектурные принципы

### 3.1. Canonical Player вместо NPC-имитации

Основным активным актором должен оставаться существующий `Player`.

Запрещены как финальная архитектура:

- NPC-based Fake Players;
- `PhantomPlayer extends Player`;
- копия или fork класса `Player`;
- fake `GameClient`;
- fake/null-network `Connection`;
- эмуляция TCP-сессии ради Phantom World.

Допустимы только узкие адаптеры и сервисы, сохраняющие канонические:

- inventory;
- skills;
- stats;
- party;
- clan;
- quests;
- trade;
- world visibility;
- persistence;
- class mechanics.

### 3.2. Сервер — authoritative source of truth

Semantic Pack не должен дублировать всю базу игры.

Authoritative source остаются существующие серверные данные:

- NPC templates;
- drop;
- spoil;
- manor;
- spawn;
- zones;
- skills;
- classes;
- recipes;
- items;
- quests;
- raid/epic definitions;
- party, clan и world state.

Поверх них создаётся read-only слой предметных знаний и компактные обратные индексы.

### 3.3. Языковое понимание отделяется от игрового знания

Целевой поток:

```text
текст настоящего игрока
→ Semantic Pack
→ intent / entities / slots / confidence
→ Game Knowledge query
→ восстановленная игровая цель и ситуация
→ Utility AI / planner
→ semantic act
→ реальное действие и/или текстовый ответ
```

Semantic Pack отвечает за:

- русский язык;
- сленг;
- сокращения;
- aliases;
- транслит;
- entity linking;
- intent;
- slots;
- conversation context;
- confidence.

Game Knowledge отвечает за:

- реальные источники предметов;
- drop/spoil/manor/quest/craft chains;
- spawn и зоны;
- подходящий уровень;
- классы и роли;
- party composition;
- достижимость и ожидаемую выгоду.

### 3.4. Явные цели вместо случайного поведения

Каждый действующий фантом должен иметь структурированную цель.

Минимальная модель цели:

```text
goal type
target entity/resource/content
required amount
current progress
acquisition method
valid sources
selected region/zone/anchor
purpose
priority
risk budget
expense budget
deadline or constraint
reason
completion conditions
abandon/replan conditions
```

Пример:

```text
COLLECT_RESOURCE
itemId: <material>
amountRequired: 120
amountCurrent: 74
acquisitionMethod: SPOIL
preferredMobGroup: <mob IDs>
location: <catacomb room anchor>
purpose: CRAFT_DARK_CRYSTAL_ROBE
priority: HIGH
```

### 3.5. Внутреннее общение — semantic acts

Фантомы не должны генерировать текст друг для друга и затем снова разбирать его.

Внутренний обмен выполняется typed-сообщениями:

```text
REQUEST_SPOT_SHARE
CLAIM_MOB_GROUP
REQUEST_PARTY_ROLE
OFFER_TRADE
WARN_PVP
ASK_RESOURCE_SOURCE
PROPOSE_FARM_TARGET
REPORT_DROP
REQUEST_RAID_MEMBER
```

Текст создаётся только для канала, наблюдаемого настоящим игроком.

### 3.6. Производительность через уровни детализации

Полная серверная симуляция используется только там, где существование фантома может быть обнаружено или повлиять на игрока.

Операционная модель включает пять состояний:

1. `ACTIVE`
2. `NEARBY / PERCEPTIBLE`
3. `WARM`
4. `BACKGROUND`
5. `SLEEPING`

Они группируются в три макроуровня:

| Макроуровень | Операционные состояния |
|---|---|
| Полная активная симуляция | `ACTIVE` |
| Региональная/событийная симуляция | `NEARBY / PERCEPTIBLE`, `WARM` |
| Фоновая симуляция | `BACKGROUND`, `SLEEPING` |

#### ACTIVE

Фантом непосредственно наблюдаем или взаимодействует с игроком:

- находится рядом;
- виден;
- участвует в том же бою;
- общается;
- находится в той же комнате;
- является целью действия.

Используются полноценные Player-механики, движение, бой, skills и точная world state.

#### NEARBY / PERCEPTIBLE

Фантом может быть не виден, но потенциально обнаружим:

- находится в соседней комнате катакомб;
- слышим через combat effects;
- доступен local chat;
- может быть найден штатным target-механизмом;
- скоро войдёт в поле зрения;
- занимает соседний spot;
- находится на том же маршруте.

Такого фантома нельзя свободно телепортировать, пересоздавать или сводить к чистой статистике.

#### WARM

Фантом находится в значимой области или событии, но пока не обнаружим напрямую:

- решения выполняются реже;
- движение идёт по anchors/waypoints;
- бой может быть частично агрегирован;
- сохраняются цель, ресурсы, риск и прогресс;
- переход в perceptible/active должен быть быстрым и непротиворечивым.

#### BACKGROUND

Фантом далеко от наблюдения:

- не моделируется каждый удар и шаг;
- фарм, дорога, расходы и риск считаются агрегированно;
- учитываются реальные class/equipment/skills/supplies;
- используются реальные источники drop/spoil/manor;
- сохраняются economic sources и sinks;
- перед повышением детализации выполняется reconciliation.

#### SLEEPING

Фантом офлайн, отдыхает или ожидает расписания:

- нет постоянных тиков;
- пробуждение по времени, событию или потребности популяции.

### 3.7. Interest model не равен простому радиусу

Уровень детализации зависит от:

- instance;
- geographical region;
- catacomb/dungeon;
- room adjacency;
- doors and passages;
- local chat reachability;
- combat-effect perceptibility;
- штатной обнаружимости;
- expected time to contact;
- party/clan/conflict relation;
- follow/pursuit;
- shared route;
- raid/epic gathering;
- важных world events.

### 3.8. ACTIVE и BACKGROUND — одна причинная модель

Background-симуляция не имеет права бесплатно создавать прогресс.

Она учитывает:

- level;
- class;
- skills;
- equipment;
- HP/MP;
- shots и расходники;
- подходящий farming target;
- kill speed;
- competition;
- drop/spoil/manor/quest constraints;
- weight и inventory capacity;
- death;
- recovery cost;
- teleport;
- продажу loot;
- supplies replenishment;
- real adena sources/sinks;
- party composition.

Упрощается детализация вычисления, а не игровые причины результата.

### 3.9. Reconciliation сохраняет историю

При материализации восстанавливаются:

- последняя подтверждённая позиция или anchor;
- маршрут;
- цель;
- прогресс;
- inventory;
- HP/MP;
- party;
- занятый spot/room;
- недавние события;
- приблизительная фаза боя.

Запрещены:

- дюп;
- невозможная телепортация;
- противоречие с уже наблюдаемым состоянием;
- свободное перемещение perceptible-фантома за кулисами.

### 3.10. Disabled-by-default и локальность изменений

Для каждого production-этапа обязательны:

- feature disabled by default;
- выключенная конфигурация не меняет поведение сервера;
- отсутствуют фоновые Phantom tasks/threads/objects при disabled;
- startup/shutdown idempotent;
- bounded collections;
- отсутствие hot-path log spam;
- локальные обратно совместимые изменения;
- полный cleanup;
- отсутствие production DB экспериментов;
- rollback без миграции рабочего сервера, если это feasibility/spike-задача.

---

## 4. Правила размера GOAL-задач

Количество около 30 GOAL является ориентиром, а не целью само по себе.

### 4.1. Когда изменения объединяются

Связанные изменения допускается объединять, если:

- они образуют один законченный пользовательский результат;
- относятся к одному уровню риска;
- используют общий lifecycle;
- проверяются одним coherent scenario;
- раздельная реализация создала бы временно некорректную архитектуру;
- общий diff остаётся обозримым и обратимым.

### 4.2. Когда задача разделяется

Разделение обязательно, если одновременно затрагиваются разные опасные границы:

- network/session и economy;
- Player lifecycle и AI;
- persistence schema и pathfinding;
- real-login arbitration и массовая population simulation;
- trade/mail и combat;
- Semantic Pack и destructive DB migration;
- geodata и social behavior.

### 4.3. Что не является достаточной причиной для отдельной GOAL

Не создаются самостоятельные GOAL только ради:

- фиксации уже известного факта;
- документации без кода, если её можно закрыть в следующей содержательной задаче;
- отдельного verifier для verifier без нового риска;
- расширения тестовой инфраструктуры, не требуемого текущим production-изменением;
- формального увеличения номера задач.

### 4.4. Пропорциональность проверок

Глубина проверки зависит от риска:

| Риск | Примеры | Ожидаемая глубина |
|---|---|---|
| Низкий | config, immutable model, read-only index | unit, static scope, build |
| Средний | scheduler, goals, memory, background calculation | unit, scenario, performance |
| Высокий | Player lifecycle, inventory, economy, trade, login collision | DB integration, failure injection, restart, concurrency |
| Критический | anti-dup, real-login ownership, mass materialization | negative controls, soak, rollback, independent evidence |

---

## 5. Обязательный формат каждой GOAL-задачи

Каждый task package должен явно содержать:

1. цель;
2. пользовательский результат;
3. принятый baseline;
4. обязательный аудит текущего кода;
5. точный scope;
6. hard out of scope;
7. архитектурные инварианты;
8. concurrency и lifecycle risks;
9. DB/transaction rules;
10. performance и memory budgets;
11. disabled behavior;
12. обязательные automated tests;
13. negative controls;
14. критерии приёмки;
15. report contract;
16. commit/push contract;
17. поведение при blocker;
18. условие пересмотра дорожной карты.

После Codex выполняется независимое ревью:

- commit и parent;
- remote ref;
- полный diff;
- связи с существующим серверным кодом;
- hot paths;
- lifecycle;
- concurrency;
- persistence;
- disabled behavior;
- scope;
- фактическая ценность новых tests/tooling;
- соответствие текущему этапу дорожной карты.

---

## 6. Статусы задач

Используются следующие статусы:

```text
NOT_STARTED
IN_PROGRESS
IMPLEMENTED_PENDING_INDEPENDENT_REVIEW
ACCEPT
ACCEPT_WITH_FOLLOW_UP
FIX_REQUIRED
BLOCKED
NOT_FEASIBLE_WITHOUT_PLAN_CHANGE
REVERT_REQUIRED
```

Codex не присваивает финальный `ACCEPT`. Он передаёт реализацию на независимое ревью.

---

## 7. Текущее состояние разработки

### 7.1. Сводка

| Task | Состояние | Результат |
|---|---|---|
| 001 | `ACCEPT` | Baseline и полный архитектурный аудит |
| 001A | `ACCEPT` | Закрытие замечаний и provenance Task 001 |
| 002 | `ACCEPT` после 002A | JDK-only test infrastructure и изолированная test DB |
| 002A | `ACCEPT` | Safety/freshness hotfix test infrastructure |
| 003 | `ACCEPT` | Disabled-by-default Phantom skeleton, config, lifecycle, bounded queue, metrics, trace |
| 004 | `IN_PROGRESS` | Bounded feasibility spike canonical headless Player |

### 7.2. Выполненные Task 001–003

#### Task 001 — Baseline и аудит

Зафиксированы:

- существующая NPC-based Fake Player архитектура;
- lifecycle `Player`;
- `GameClient`;
- offline play/trade;
- packet effects;
- persistence;
- World;
- party/clan/trade/quest/mail/siege;
- cleanup и task risks.

Вердикт:

```text
FEASIBLE_WITH_SEAM
```

Принята идея небольшого Player-owned outbound/session seam вместо fake GameClient, Player subclass или fork.

#### Task 002 и 002A — Test infrastructure

Реализованы:

- JDK-only test runner;
- Ant targets;
- отдельная `l2jmobiush5_phantom_test`;
- dedicated DB user;
- fail-closed pre-Hikari guard;
- strict SQL provisioning;
- durable schema fingerprint;
- DB metadata consistency;
- cross-process provisioning lock;
- negative controls;
- scenario/performance smoke;
- deterministic seed.

#### Task 003 — Disabled skeleton

Реализованы:

- `PhantomPlayers.ini`;
- `EnablePhantomSystem=False`;
- `EnablePhantomDiagnostics=False`;
- fail-closed config;
- startup after ThreadPool and before IdManager;
- shutdown before ThreadPool;
- one bounded inert queue;
- fixed metrics;
- optional bounded sampled trace;
- отсутствие Player/DB/network/worker/task при disabled.

Accepted commit:

```text
eb008f2216b3e8381c0181d71ce200bbf4907ac7
```

### 7.3. Task 004 — IN PROGRESS / выполняется в Codex

**Task 004 нельзя менять, расширять или отменять этим документом.**

Фактическая постановка Task 004:

- bounded feasibility spike canonical headless `Player`;
- минимальный Player-owned outbound/session seam;
- сохранение real-client behavior;
- zero-network headless packet sink;
- `ServerPacket.runImpl(Player)` exactly once;
- identity lease с минимальным real-login hook;
- create/load/materialize/action/dematerialize/reload;
- inventory, skills и World registration;
- observer visibility;
- одна reversible canonical inventory action;
- action admission closure;
- failure injection после lifecycle steps;
- проверка World, online flag, autosave, tasks, identity и item residue;
- one/ten fixture measurements;
- работа только с `l2jmobiush5_phantom_test`;
- запрет fake GameClient/Connection, Player fork/subclass, `EnterWorld.runImpl`, production DB и per-phantom threads.

Допустимые результаты:

```text
FEASIBLE_WITH_SEAM_PENDING_INDEPENDENT_REVIEW
```

или честный:

```text
NOT_FEASIBLE_WITHOUT_PLAN_CHANGE_PENDING_INDEPENDENT_REVIEW
```

Task 005 не начинается до независимого ревью Task 004.

---

# 8. Крупные этапы дорожной карты

## Этап I. Безопасный фундамент и canonical actor

**Назначение:** доказать, что Phantom World может безопасно существовать в текущем сервере без NPC-подмены и fake network stack.

**GOAL:** 001–006  
**Состояние:** выполняется  
**Завершено:** 001–003  
**Сейчас:** 004  
**Далее:** 005–006

### Task 001 — Baseline и архитектурный аудит — `ACCEPT`

Результат:

- карта server seams;
- feasibility verdict;
- минимальная outbound/session architecture;
- список опасных lifecycle и persistence paths.

### Task 002 — Автоматическая test infrastructure — `ACCEPT`

Результат:

- repeatable isolated tests;
- test DB;
- negative controls;
- deterministic execution.

### Task 003 — Disabled skeleton — `ACCEPT`

Результат:

- безопасная production integration point;
- disabled defaults;
- lifecycle owner;
- bounded metrics/trace.

### Task 004 — Headless Player feasibility spike — `IN_PROGRESS`

Результат должен доказать или опровергнуть:

- canonical Player without TCP;
- packet effect semantics;
- identity ownership;
- materialization/cleanup;
- restart restoration.

### Task 005 — Phantom domain model и persistence

Предварительный scope после принятия Task 004:

- persistent Phantom profile;
- lifecycle-independent state;
- personality seed;
- schedule;
- long-term goal state;
- simulation state;
- versioned schema;
- repository/DAO;
- optimistic versioning;
- idempotent migrations;
- round-trip/restart tests.

Не включать:

- Utility AI;
- combat;
- Semantic Pack;
- population scale.

### Task 006 — Production materialization lifecycle

Предварительный scope:

- final materialize/dematerialize service;
- identity lease integration;
- bounded admission/cancellation;
- World registration;
- cleanup;
- restart recovery;
- materialization limits;
- diagnostics;
- disabled default.

**Основные риски этапа:**

- real-login collision;
- leaked Player tasks;
- online/world divergence;
- inventory residue;
- broad EnterWorld reuse;
- network coupling.

**Критерий завершения этапа:**

- canonical Player materializes without TCP;
- production lifecycle is idempotent;
- real login and phantom ownership cannot overlap;
- restart restores persistent Phantom state;
- disabled server behavior remains unchanged;
- no retained World/autosave/task/item residue.

---

## Этап II. Планирование, relevance и перемещение

**Назначение:** дать фантомам дешёвый общий runtime, цели и topology-aware движение без per-phantom scheduler.

**GOAL:** 007–011  
**Зависит от:** завершённого Этапа I

### Task 007 — Interest/relevance model и scheduler budgets

Scope:

- `ACTIVE`;
- `NEARBY / PERCEPTIBLE`;
- `WARM`;
- `BACKGROUND`;
- `SLEEPING`;
- topology/event-aware relevance;
- fairness;
- bounded shared scheduler;
- per-state decision frequency;
- overload degradation;
- no per-phantom futures;
- promotion/demotion protocol;
- visibility/perceptibility invariants.

Не ограничиваться только `distance < N`.

### Task 008 — Goal model, Utility AI и plan executor

Scope:

- explicit goals;
- considerations;
- normalized scoring;
- deterministic ties;
- action candidates;
- replanning;
- timeout;
- cancellation;
- explanation/reason;
- confidence/risk gates;
- generic class capabilities;
- no domain-specific giant scripts.

### Task 009 — Geodata и navigation benchmark

Scope:

- factual geodata availability;
- supported formats;
- no-geodata mode;
- direct path baseline;
- local A* benchmark;
- path budgets;
- no external geodata in repository.

### Task 010 — PhantomNavigationService

Scope:

- direct path first;
- anchor fallback;
- cached local path;
- bounded A*;
- cooldown;
- target movement threshold;
- cancellation;
- stuck detection;
- overload degradation;
- metrics.

### Task 011 — Topology, anchors, rooms и party routes

Scope:

- cities;
- shops;
- Gatekeepers;
- farming anchors;
- catacomb rooms;
- room adjacency;
- doors/passages;
- dungeon topology;
- party leader route;
- followers;
- perceptibility topology;
- background travel anchors.

**Основные риски этапа:**

- scheduler starvation;
- pathfinding CPU spikes;
- false dematerialization near players;
- impossible reconciliation;
- topology disconnected from actual server geometry.

**Критерий завершения этапа:**

- thousands of profiles can remain scheduled without thousands of tasks;
- relevance transitions are deterministic and observable;
- perceptible phantoms are not treated as pure statistics;
- navigation respects budgets and graceful no-geodata mode;
- goals can be planned and cancelled without gameplay-specific hardcode.

---

## Этап III. Одиночный игровой цикл и причинный background

**Назначение:** создать работающую жизнь одиночного персонажа: бой, развитие, supplies, фарм и background reconciliation.

**GOAL:** 012–016  
**Зависит от:** Этапов I–II

### Task 012 — Capability-driven базовый бой

Scope:

- target selection;
- attack;
- selected skills;
- shots;
- HP/MP;
- death;
- resurrection;
- loot;
- threat;
- controlled class/zone matrix;
- real action facades;
- no request-packet API.

Архитектура должна быть capability-driven, чтобы новые классы добавлялись через capabilities/considerations, а не отдельный монолитный скрипт.

### Task 013 — Progression, professions, skills и equipment

Scope:

- EXP/SP;
- levels;
- profession transitions;
- skill learning;
- equipment evaluation;
- equip/unequip;
- class capability registry;
- enchant risk policy;
- gear progression goals.

Примеры, которые должна поддерживать общая система:

- healer;
- tank;
- spoiler;
- dagger positioning;
- summoner;
- escape/teleport capability.

### Task 014 — NPC commerce, supplies, travel и sell loop

Scope:

- grocery;
- weapons/armor;
- shots;
- consumables;
- budget;
- sell loot;
- Gatekeeper;
- teleport costs;
- restock;
- inventory/weight;
- economic conservation.

### Task 015 — Background farming и reconciliation

Scope:

- aggregated combat/farming;
- EXP/SP;
- drop;
- spoil;
- consumption;
- death probability;
- travel time;
- competition;
- active/background equivalence tests;
- promotion reconciliation;
- anti-dup;
- no free resource generation.

### Task 016 — PopulationManager и schedules

Scope:

- profile population;
- new level-1 characters;
- login/logout schedules;
- sleeping/wakeup;
- online distribution;
- region distribution;
- materialization limits;
- retirement/return;
- backpressure;
- no fixed fantasy numbers embedded in code.

**Основные риски этапа:**

- economic inflation;
- item duplication;
- active/background divergence;
- class behavior hardcode;
- unbounded population state;
- mass DB writes.

**Критерий завершения этапа:**

- a new Phantom can progress from low level;
- obtain supplies and equipment;
- farm with real costs and risk;
- sleep and resume;
- transition between background and active without duplication or impossible state.

---

## Этап IV. Социальное поведение, память и понимание мира

**Назначение:** превратить автономных персонажей в социальных участников мира, понимающих игровые объекты и причины сообщений.

**GOAL:** 017–020  
**Зависит от:** работающих goals, classes, party-capable Player lifecycle и read-only server data

### Task 017 — Party/clan coordination kernel и semantic acts

Scope:

- party role model;
- party vacancies;
- invitations/acceptance/refusal;
- leader/follower responsibilities;
- internal semantic acts;
- group goals;
- shared route;
- assist/protect/heal priorities;
- initial clan membership hooks;
- no natural-language generation between phantoms.

### Task 018 — Personality, memory, reputation и relationship modifiers

Scope:

- personality traits;
- trust;
- respect;
- fear;
- anger;
- friendship;
- rivalry;
- debt;
- important events;
- decay/expiry;
- agreement history;
- goal and risk modifiers;
- deterministic memory limits.

### Task 019 — Semantic Pack и Game Knowledge

Scope:

#### Semantic Pack

- Russian intents;
- slang;
- abbreviations;
- aliases;
- transliteration;
- entity linking;
- slots;
- context;
- confidence;
- clarification policy;
- deterministic corpus.

#### Game Knowledge

Read-only services and compact indexes:

```text
itemId -> mobs that drop
itemId -> mobs that spoil
itemId -> manor sources
mobId -> spawn areas
zoneId -> mobs
level range -> farming zones
recipeId -> ingredients
ingredientId -> recipes
contentId -> recommended roles
class/role -> party vacancies
```

Не копировать всю server DB в Semantic Pack.

### Task 020 — Conversation policy, verbalization и action dispatch

Scope:

- dialogue state;
- semantic act selection;
- safe action dispatch;
- confidence gates;
- clarification;
- phrase variation;
- personality-aware verbalization;
- observer-visible text only;
- chat channels;
- no runtime LLM;
- no text round-trip between phantoms.

**Основные риски этапа:**

- unsafe action from low confidence;
- stale or duplicated knowledge;
- memory growth;
- random phrase-bank answers detached from world state;
- language logic leaking into gameplay rules.

**Критерий завершения этапа:**

- Phantom understands what the player means;
- grounds language to real server IDs;
- queries authoritative game knowledge;
- relates the request to both sides’ goals;
- chooses a rational semantic act;
- executes only confidence-safe actions;
- answers without runtime external AI.

---

## Этап V. Предметная глубина: ресурсы, экономика и конфликты

**Назначение:** реализовать сложные повседневные ситуации Lineage 2 поверх уже готовых generic goals, knowledge и social reasoning.

**GOAL:** 021–026  
**Зависит от:** Этапов III–IV

### Task 021 — Spoil, manor, quest drop и craft acquisition chains

Scope:

- spoiler capabilities;
- spoil source selection;
- manor sources;
- quest item goals;
- recipe ingredient chains;
- craft preparation;
- method eligibility;
- progress tracking;
- source switching;
- knowledge-driven acquisition.

### Task 022 — Player economy, trade, shops, crafting и anti-dup ledger

Scope:

- direct trade;
- private stores;
- buy/sell offers;
- item reservation;
- adena reservation;
- atomic completion;
- expiration;
- crafting;
- significant operation ledger;
- sources/sinks;
- crash/restart conservation;
- anti-dup.

### Task 023 — Party formation, Rift и role vacancies

Scope:

- party destination;
- Rift requirements;
- current composition;
- missing roles;
- class suitability;
- supplies/equipment readiness;
- travel readiness;
- invite/refuse;
- full-party detection;
- group goal.

### Task 024 — Farming spot negotiation и resource conflict

Scope:

- claimed mob groups;
- occupied rooms;
- shared spots;
- alternative sources;
- remaining amount;
- trust/agreement history;
- negotiation;
- wait/share/move/refuse;
- local conflict;
- perceptible-neighbor invariants.

### Task 025 — PvP/PK, threat, alliances и escalation

Scope:

- PvP/PK risk;
- strength comparison;
- allies;
- retreat;
- warnings;
- revenge memory;
- territory conflict;
- help calls;
- safe escalation rules;
- zone rules;
- character/personality modifiers.

### Task 026 — Raid и epic orchestration

Scope:

- raid/epic content knowledge;
- party/command-channel composition;
- role readiness;
- supplies;
- gathering;
- route;
- timing;
- failure/retreat;
- recruitment;
- realistic win feasibility;
- no aggregate “free victory”.

**Основные риски этапа:**

- item/adena duplication;
- deadlocks in trade/reservation;
- griefing behavior;
- uncontrolled PK;
- impossible group composition;
- false semantic grounding.

**Критерий завершения этапа:**

- Phantom can explain and pursue a concrete resource goal;
- negotiate a farming conflict;
- form a suitable Rift/raid party;
- trade/craft without duplication;
- escalate or de-escalate conflict according to real state and personality.

---

## Этап VI. Кланы, масштаб и эксплуатационная готовность

**Назначение:** довести систему до устойчивого автономного мира, пригодного для длительной работы и управляемого релиза.

**GOAL:** 027–030  
**Зависит от:** всех предыдущих этапов

### Task 027 — Clan lifecycle, recruitment, alliances и wars

Scope:

- clan goals;
- recruitment;
- role distribution;
- warehouse contributions;
- alliances;
- wars;
- clan reputation;
- group memory;
- persistence;
- safe membership changes.

### Task 028 — Scale, soak и overload degradation

Scope:

- profile counts;
- active/perceptible/warm/background counts;
- materialization caps;
- scheduler fairness;
- queue growth;
- tick budget;
- path budget;
- DB write rate;
- memory;
- long soak;
- overload degradation;
- recovery after load spike;
- no per-phantom tasks.

### Task 029 — Operations, admin controls, observability и replay

Scope:

- enable/disable controls;
- safe drain;
- per-state metrics;
- bounded traces;
- selected Phantom inspection;
- reason/explanation visibility;
- stuck diagnostics;
- slow operation thresholds;
- audit of significant economic actions;
- deterministic scenario replay;
- no hot-path logging spam.

### Task 030 — End-to-end autonomous world alpha и release gate

Scope:

- fresh server bootstrap;
- population creation;
- progression;
- background/active transitions;
- farming;
- spoil/craft/trade;
- party;
- Rift;
- PvP;
- raid;
- conversation;
- restart;
- failure recovery;
- soak;
- disabled regression;
- operator documentation;
- rollback plan.

**Основные риски этапа:**

- long-running memory leak;
- DB write amplification;
- scheduler unfairness;
- operational opacity;
- cross-system recovery failure;
- acceptable behavior in isolated tests but unstable behavior in a living world.

**Критерий завершения этапа:**

- long-running autonomous Phantom population behaves causally;
- server remains stable under target scale;
- operators can inspect, drain, disable and recover the subsystem;
- restart does not duplicate state;
- disabled mode remains equivalent to the original server;
- alpha acceptance scenarios pass.

---

# 9. Межэтапные зависимости

```text
I. Canonical actor and lifecycle
    ↓
II. Relevance, goals and navigation
    ↓
III. Solo gameplay and background causality
    ↓
IV. Social reasoning, memory and language
    ↓
V. Domain depth: economy, conflict, Rift, raids
    ↓
VI. Clans, scale, operations and release
```

Некоторые read-only исследования могут идти раньше, но production implementation не должна перескакивать через обязательные gates.

Примеры:

- Semantic corpus можно собирать заранее, но action dispatch нельзя принимать до confidence и goal gates.
- Drop/spoil indexes можно исследовать до Task 019, но нельзя внедрять их хаотично в combat task.
- Performance measurements выполняются на каждом этапе, но полный scale gate остаётся Task 028.
- Personality data model может обсуждаться раньше, но не должна попасть в Task 005, если это расширит риск persistence до неподтверждённой модели.

---

# 10. Сквозные инварианты

Эти инварианты действуют для всех этапов.

## 10.1. Lifecycle

- один persistent character имеет не более одного owner;
- один object ID имеет не более одного world actor;
- real login и Phantom materialization не пересекаются;
- admission закрывается до dematerialization;
- cleanup idempotent;
- repeated shutdown безопасен;
- failed transition откатывает только завершённые steps;
- no retained world/task/autosave/action residue.

## 10.2. Concurrency

- no per-phantom executor;
- no per-phantom permanent thread;
- shared bounded queues;
- explicit ownership;
- cancellation;
- timeouts;
- stable lock order;
- bounded waits;
- no blocking hot path on slow DB/network-independent logic.

## 10.3. Persistence и экономика

- production DB не используется для экспериментов;
- test DB isolated;
- migrations versioned/idempotent;
- item/adena conservation;
- reservation before multi-step transfer;
- restart recovery;
- no free background generation;
- significant operations auditable.

## 10.4. Performance

- fixed or bounded memory in hot structures;
- no unbounded traces;
- no full NPC/drop scan per query;
- compact read-only indexes;
- materialization caps;
- overload degradation;
- no INFO/WARNING per decision/action/path.

## 10.5. Disabled behavior

При `EnablePhantomSystem=False`:

- no Phantom Player;
- no Phantom DB queries;
- no Phantom scheduler activity;
- no Phantom thread/future;
- no new world object;
- no packet behavior change for ordinary players;
- no periodic Phantom log;
- no modified economy;
- no background profile processing.

---

# 11. Definition of Done для всей системы

Phantom World считается достигшим первой законченной цели, когда одновременно доказано:

1. canonical Player работает без обязательного TCP;
2. real login и Phantom ownership безопасно разделены;
3. profiles persistent и restart-safe;
4. materialization/dematerialization idempotent;
5. scheduler масштабируется без per-phantom tasks;
6. relevance model включает perceptible topology;
7. background simulation causal и anti-dup;
8. goals и Utility AI объяснимы;
9. class capabilities расширяемы;
10. Semantic Pack понимает русский L2-сленг;
11. entities связываются с реальными server IDs;
12. Game Knowledge использует authoritative server data;
13. party/Rift/raid decisions зависят от реального состава;
14. trade/craft/economy сохраняют conservation;
15. personality/memory влияют на решения;
16. conversation dispatch безопасен по confidence;
17. long soak не показывает неконтролируемого роста памяти, очередей или DB-нагрузки;
18. subsystem можно безопасно disable/drain/restart;
19. original server behavior при disabled не изменён;
20. end-to-end alpha scenarios проходят.

---

# 12. Формат наглядного прогресса после каждого ревью

После каждого независимого ревью публикуется краткая сводка:

```text
Phantom World Progress

Current stage:
Current accepted baseline:

Completed:
- ...

In progress:
- ...

Next:
1. ...
2. ...
3. ...

Stage completion:
- completed goals / total goals
- stage gate status

New risks:
- ...

Roadmap changes:
- none / description

Overall assessment:
- on track / delayed / architecture reconsideration required
```

## Текущая сводка

```text
Current stage:
I. Безопасный фундамент и canonical actor

Current accepted baseline:
eb008f2216b3e8381c0181d71ce200bbf4907ac7

Completed:
- Task 001 — baseline and audit
- Task 001A — audit closure
- Task 002 — test infrastructure
- Task 002A — safety/freshness closure
- Task 003 — disabled skeleton

In progress:
- Task 004 — canonical headless Player feasibility spike in Codex

Next:
1. Independent review Task 004
2. Task 005 — domain model and persistence
3. Task 006 — production materialization lifecycle

Stage completion:
- 3 main GOAL accepted from 6
- Task 004 in progress
- Stage gate not yet complete

New risks:
- Task 004 may prove that canonical Player requires a wider session/lifecycle seam
- real-login collision and cleanup remain critical gates
- no change yet to roadmap estimate

Overall assessment:
- on track, pending Task 004 feasibility verdict
```

---

# 13. Изменение дорожной карты

Дорожная карта меняется только после доказанного архитектурного факта.

Основания:

- Task 004 возвращает `NOT_FEASIBLE_WITHOUT_PLAN_CHANGE`;
- production code differs materially from audited assumptions;
- required subsystem does not exist in High Five;
- performance evidence disproves the planned model;
- safety requires a different task order;
- new user requirement changes the final product.

Не являются основанием:

- желание Codex расширить scope;
- удобство реализации;
- наличие похожего кода в другой хронике;
- стремление быстрее показать визуальную демонстрацию через NPC;
- формальное желание сохранить ровно 30 задач.

При изменении roadmap фиксируются:

```text
reason
affected stages/tasks
new dependencies
risk impact
migration/rollback
accepted baseline
user approval
```

---

# 14. Обязательное использование документа

После добавления и push этого файла:

- перед ревью Task 004 сверять результат с Этапом I и текущими invariants;
- перед каждой следующей GOAL перечитывать этот roadmap;
- не позволять Codex преждевременно внедрять future-stage scope;
- при подготовке Task 007 учитывать обязательный `NEARBY / PERCEPTIBLE`;
- при Task 008 использовать explicit structured goals;
- при Task 015 требовать causal background/reconciliation;
- при Task 019 разделять Semantic Pack и Game Knowledge;
- при Task 020 сохранять semantic acts и confidence gates;
- при Task 023–026 связывать party, Rift, farming conflicts, PvP и raids с реальным world state;
- после каждого ревью обновлять краткий progress block;
- не превращать roadmap в неизменяемый догмат: изменения допустимы только на основании evidence и отдельного решения.

---

## 15. Итоговая стратегия

Кратчайший безопасный путь к полноценным фантомным игрокам:

```text
canonical Player
→ safe lifecycle and persistence
→ bounded relevance/scheduler
→ goals and Utility AI
→ navigation and topology
→ causal solo gameplay
→ background reconciliation
→ population
→ party/social memory
→ Semantic Pack + Game Knowledge
→ conversation/action dispatch
→ economy/conflicts/group content
→ scale/operations/release
```

Главный критерий качества:

> Фантом должен понимать, зачем он находится в мире, что он пытается получить, какие реальные игровые способы ему доступны, как действия других игроков влияют на его цель и какое действие рационально выполнить — при этом сервер должен тратить полные ресурсы только на тех фантомов, чьё существование может быть непосредственно или косвенно обнаружено настоящим игроком.
