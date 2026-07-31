# Генеральный план разработки Phantom World для L2J Mobius High Five

**Версия:** 1.0  
**Статус:** основной источник требований и порядка разработки  
**Репозиторий:** https://github.com/kpCat/L2J  
**Корень Git-репозитория:** `C:\Users\endim\L2J_Mobius\`  
**Единственный рабочий модуль:** `C:\Users\endim\L2J_Mobius\L2J_Mobius_CT_2.6_HighFive\`  
**Рабочие конфиги GameServer:** `C:\Users\endim\L2J_Mobius\L2J_Mobius_CT_2.6_HighFive\dist\game\config\`

---

## 1. Роль документа

Этот файл — главный источник целей, ограничений, архитектурных принципов и последовательности задач Phantom World.

Перед подготовкой каждой задачи необходимо:

1. перечитать этот план;
2. изучить отчёт предыдущей задачи;
3. проверить реальный commit/diff в GitHub;
4. проверить актуальный код;
5. сверить задачу с текущим этапом и зависимостями;
6. заранее определить архитектуру, scope, тесты, конфиги, риски и rollback;
7. не заставлять Codex изобретать то, что можно качественно спроектировать заранее;
8. не изменять другие хроники.

План можно менять только отдельным осознанным коммитом с объяснением причины. Нельзя молча уводить проект в сторону.

---

## 2. Исходное состояние

- L2J Mobius CT 2.6 High Five.
- JDK 25.
- Сборка Apache Ant.
- Цель `jar` создаёт и автоматически копирует `GameServer.jar` и `LoginServer.jar` в рабочий `dist\libs`.
- MariaDB: `127.0.0.1:3308`.
- Рабочая база: `l2jmobiush5`.
- Локальные реквизиты: `root/root`.
- LoginServer и GameServer запускаются.
- GameServer регистрируется в LoginServer.
- Клиент High Five подключается.
- Аккаунт и персонаж создаются.
- В проекте уже есть Fake Players на базе NPC, но это не считается достаточным ядром.
- Геодата пока отсутствует, поэтому полноценный pathfinding отключён.

---

## 3. Конечная цель

Создать постоянное автономное население сервера, которое воспринимается как реальные игроки, а не декоративные NPC.

Фантомы должны:

- появляться как новые персонажи первого уровня;
- получать опыт, уровни, SP, профессии, skills и экипировку;
- покупать расходники, оружие, броню и бижутерию;
- продавать лут NPC и другим персонажам;
- использовать соски, банки, стрелы, телепорты;
- фармить и выбирать подходящие зоны;
- конкурировать за споты и комнаты катакомб;
- защищать территорию;
- вступать в PvP и PK по осмысленным причинам;
- учитывать karma, риск смерти и выпадения предметов;
- собираться в партии и распределять роли;
- приглашать настоящего игрока;
- ходить на рейдов и эпиков;
- торговать через private store, торговый чат, личку, direct trade и mail;
- торговаться;
- иметь характер, память, репутацию, друзей, врагов и долги;
- реагировать на сообщения не только словами, но и действиями;
- создавать кланы и альянсы;
- вести клановые войны;
- участвовать в осадах;
- поддерживать ограниченный whitelist квестов, Kamaloka и Pailaka;
- работать производительно на одном локальном компьютере.

---

## 4. Честные ограничения

Нельзя обещать:

- полное отсутствие ошибок;
- автоматическое понимание любого квеста;
- человеческий разговор на любую тему без генеративной модели;
- сотни одновременно полностью активных персонажей без профилирования;
- безошибочную навигацию во всех местах с первой версии.

Обязательно обеспечить:

- воспроизводимость ошибок;
- feature flags;
- автоматические проверки;
- отдельную тестовую БД;
- контролируемые миграции;
- атомарные задачи и коммиты;
- диагностический режим по запросу;
- отсутствие постоянного hot-path логирования;
- возможность отката;
- постепенное профилирование.

---

## 5. Целевая архитектура

### 5.1. Не строить конечную систему только на NPC

Встроенная Fake Player-система полезна для аудита и повторного использования отдельных механизмов, но NPC не должен быть окончательным доменным объектом.

Конечная система должна максимально переиспользовать штатные механики `Player`: inventory, skills, party, clan, trade, quest, PvP/PK, mail, instance и siege.

### 5.2. Предварительные компоненты

Точные классы подтверждаются задачей 001:

- `PhantomProfile` — постоянная личность и состояние;
- `PhantomPlayer` или безопасный адаптер над `Player` — объект в мире;
- `PhantomClient` / `PacketSink` — серверная замена TCP-клиента;
- `PhantomBrain` — выбор цели;
- `PhantomPlanExecutor` — выполнение плана;
- `PhantomActionFacade` — безопасный вход в стандартные игровые действия;
- `PhantomScheduler` — общий планировщик;
- `PhantomNavigationService` — direct path, A*, budgets, cache, stuck recovery;
- `PhantomPopulationManager` — население;
- `PhantomMemoryService` — память и отношения;
- `PhantomConversationService` — Semantic Pack и диалог;
- `PhantomEconomyService` — покупки, продажи, резервы и цены;
- `PhantomMetrics` — дешёвые агрегированные метрики.

### 5.3. Запрет отдельного потока на фантома

Запрещено:

- `Thread` на каждого фантома;
- бесконтрольные scheduled tasks на каждого фантома;
- pathfinding в каждом AI-тике;
- запись в БД после каждого действия;
- INFO/WARNING-лог каждого решения.

---

## 6. Уровни детализации

### ACTIVE

Фантом рядом с настоящим игроком или участвует в важном событии:

- полноценный объект;
- реальное движение и бой;
- штатные механики;
- ограниченный бюджет решений и pathfinding.

### WARM

Фантом в активной зоне, но не наблюдается напрямую:

- решения реже;
- сниженная визуальная точность;
- ограниченное перепланирование.

### BACKGROUND

Фантом вне наблюдения:

- не разыгрывается каждый удар и шаг;
- фарм, дорога и расходы считаются агрегировано;
- перед материализацией выполняется reconciliation.

### SLEEPING

Фантом офлайн или ждёт расписания:

- нет постоянных тиков;
- пробуждение по событию или расписанию.

---

## 7. Навигация и pathfinding

Порядок:

1. проверить прямой безопасный путь;
2. использовать anchor-маршрут;
3. использовать кэшированный локальный путь;
4. вызвать локальный A* только при необходимости;
5. при превышении бюджета отказаться от цели или применить stuck recovery.

Для городов, магазинов, Gatekeeper, катакомб, рейдов, инстансов и осад используется небольшой граф смысловых точек.

Основной маршрут партии строит лидер. Остальные следуют waypoint-ам и делают только локальную коррекцию.

Обязательны:

- лимит pathfinding-запросов на тик;
- приоритетная очередь;
- cooldown;
- порог перемещения цели;
- cache hit/miss;
- slow-path metric;
- cancellation;
- stuck detector;
- деградация при перегрузке.

---

## 8. Логирование и диагностика

В Java нет стандартного C/C++-подобного препроцессора. Самодельный препроцессор не нужен.

Запрещены обычные логи в высокочастотных циклах.

Использовать:

- редкие ошибки и startup-сообщения;
- ленивое формирование debug-сообщений;
- sampling;
- trace только для выбранных фантомов;
- `LongAdder` и агрегированные метрики;
- slow-operation thresholds;
- периодическую публикацию сводки.

Целевые параметры конфига:

- `EnablePhantomSystem`;
- `EnablePhantomMetrics`;
- `EnableDecisionTrace`;
- `TracePhantomNames`;
- `TraceSampleRate`;
- `SlowDecisionThresholdMs`;
- `SlowPathThresholdMs`;
- `MetricsPublishIntervalSeconds`;
- `DeterministicSeed`;
- лимиты ACTIVE/WARM;
- бюджеты действий и навигации.

---

## 9. Конфиги и данные

Основной конфиг:

`dist\game\config\Custom\PhantomPlayers.ini`

Данные без перекомпиляции:

`dist\game\data\phantoms\`

Там могут находиться:

- archetypes;
- personalities;
- zone/anchor data;
- Semantic Pack;
- словари;
- шаблоны;
- population rules;
- сценарии поддерживаемых инстансов.

Настройки поведения, лимиты и feature flags выносятся в конфиг.

---

## 10. Semantic Pack

Runtime не должен обязательно зависеть от LLM или интернета.

Конвейер:

1. регистр и пунктуация;
2. `ё` → `е`;
3. повторяющиеся символы;
4. токенизация;
5. игровой жаргон;
6. транслит;
7. ограниченный fuzzy matching;
8. лёгкая морфология/стемминг;
9. entities;
10. intent;
11. контекст диалога;
12. память и отношение;
13. выбор действия;
14. формирование фразы.

Минимальные intents:

- greeting/farewell;
- insult/threat/apology;
- help;
- party invite/accept/reject;
- buy/sell/price/counteroffer/accept/reject;
- location;
- raid invitation;
- PvP provocation;
- small talk;
- unknown.

Результат разбора может запускать действие: party, trade, mail, движение, память, изменение отношения, вызов союзников или атаку.

---

## 11. Память и отношения

Хранить структурированные события, а не бесконечный полный чат:

- помог;
- убил;
- обманул;
- украл моба;
- вылечил;
- дал скидку;
- бросил party;
- оскорбил;
- спас;
- вместе убили босса;
- нарушил договор.

Производные отношения:

- trust;
- respect;
- fear;
- anger;
- friendship;
- rivalry;
- debt.

У события есть importance, timestamp, decay/expiry и связанный контекст.

---

## 12. Экономика

Фантомы используют реальные предметы и адену.

Обязательны:

- ledger значимых операций;
- резервирование товара;
- атомарное завершение сделки;
- expiration резерва;
- контроль источников и стоков адены;
- мониторинг денежной массы;
- ограничение фоновой генерации;
- отсутствие дюпа после сбоя;
- согласование ACTIVE/BACKGROUND.

Заточка учитывает цену, шанс, запасную экипировку, богатство и risk tolerance.

---

## 13. Автоматические тесты

Ручное тестирование — только крайняя мера.

Нужны:

1. unit:
   - Utility AI;
   - semantics;
   - memory;
   - economy;
   - pricing;
   - planner;
   - path cache;
   - determinism;

2. DB integration:
   - отдельная база `l2jmobiush5_phantom_test`;
   - рабочая `l2jmobiush5` не меняется;

3. headless integration:
   - create/materialize/action/dematerialize/restart;

4. scenario tests:
   - фиксированный seed;
   - ожидаемые события и итог;

5. soak/performance:
   - число профилей;
   - tick time;
   - memory;
   - queue growth;
   - DB query count;
   - pathfinding budget.

В ранней задаче добавить Ant-цели уровня `test`, `verify`, `phantom-scenario-test`, `phantom-performance-smoke`.

---

## 14. Git и Codex

Целевая ветка:

`feature/phantom-world`

Каждая задача завершается commit и push независимо от результата.

Статусы:

- `SUCCESS`;
- `PARTIAL`;
- `BLOCKED`.

При `BLOCKED` Codex откатывает опасный/некомпилируемый production-код, оставляет безопасный аудит/тесты/отчёт, коммитит и пушит.

Запрещено:

- изменять другие хроники;
- несвязанный рефакторинг;
- обновление библиотек «заодно»;
- массовое форматирование;
- удаление старой Fake Player-системы без отдельного решения;
- скрытие ошибок;
- force push;
- сторонние большие бинарники без согласования.

---

## 15. Пакет каждой задачи

Архив распаковывается в:

`C:\Users\endim\L2J_Mobius\L2J_Mobius_CT_2.6_HighFive\`

Основной файл:

`docs\phantoms\tasks\NNN-short-name\TASK.md`

Дополнительно по необходимости:

- `CONTEXT.md`;
- `ARCHITECTURE.md`;
- `ACCEPTANCE.md`;
- `TEST_CASES.md`;
- fixtures;
- подготовленные SQL/XML/XSD.

`TASK.md` обязан содержать:

- цель;
- зависимости;
- контекст;
- обязательный аудит;
- точное архитектурное решение;
- scope;
- out of scope;
- ожидаемые файлы;
- конфиги;
- performance;
- concurrency/lifecycle;
- DB/transactions;
- tests;
- commands;
- acceptance;
- report;
- commit/push;
- blocking behavior.

---

## 16. Отчёт Codex

Путь:

`docs\phantoms\reports\NNN-short-name.md`

Содержание:

- status;
- summary;
- changed files;
- architecture;
- DB;
- configs;
- commands;
- test results;
- measurements;
- deviations;
- limitations;
- risks;
- branch;
- commit SHA;
- push;
- next step.

---

## 17. Ревью

После отчёта:

1. прочитать отчёт;
2. проверить commit/diff в GitHub;
3. сравнить с `TASK.md`;
4. проверить scope;
5. проверить тесты;
6. проверить hot paths, БД, конкурентность, lifecycle, конфиги;
7. вынести `ACCEPT`, `ACCEPT WITH FOLLOW-UP`, `FIX REQUIRED` или `REVERT`;
8. только затем готовить следующую задачу.

---

## 18. План до 30 задач

### 001. Baseline и полный аудит

- зафиксировать рабочую точку;
- создать/проверить `feature/phantom-world`;
- аудит Fake Players, `Player`, `GameClient`, offline play/trade;
- аудит party/clan/trade/quest/mail/siege;
- аудит build/test;
- dependency map;
- production-поведение не менять.

**Gate:** обоснованный ответ, возможно ли безопасно создать headless `Player`, и минимальный seam без fork половины `Player`.

### 002. Автоматическая тестовая инфраструктура

- тестовая структура;
- Ant integration;
- отдельная test DB;
- deterministic seed;
- `verify`;
- отрицательный контрольный тест.

### 003. Каркас, конфиг, метрики и feature flags

- phantom package;
- config;
- no-op managers;
- startup/shutdown;
- metrics;
- system disabled by default.

### 004. Feasibility spike headless Player

- создать игрока без TCP;
- packet sink;
- inventory/skills/world registration;
- logout cleanup;
- автоматический create → enter → leave;
- ADR.

**Gate:** если реализация небезопасна, официальный пересмотр плана.

### 005. Доменная модель и persistence

- profile/state/personality;
- отдельные таблицы;
- repositories;
- versioning/optimistic locking;
- idempotent migrations;
- round-trip tests.

### 006. Materialization lifecycle

- materialize/dematerialize;
- world registration;
- cleanup;
- restart recovery;
- limits;
- diagnostics без обязательного клиента.

### 007. Scheduler и уровни активности

- ACTIVE/WARM/BACKGROUND/SLEEPING;
- budgets;
- fairness;
- overload degradation;
- no per-phantom tasks.

### 008. Utility AI и plan executor

- goals;
- considerations;
- scoring;
- deterministic ties;
- cancellation;
- timeout;
- on-demand trace.

### 009. Геодата и navigation benchmark

- paths/formats;
- runner;
- direct/A* baseline;
- graceful no-geodata mode;
- стороннюю геодату не коммитить.

### 010. PhantomNavigationService

- direct path first;
- A* budget/cache/cooldown;
- target threshold;
- cancellation;
- stuck recovery;
- metrics.

### 011. Anchor graph, зоны и party route

- смысловые точки;
- города/магазины/Gatekeeper/фарм;
- комнаты катакомб;
- leader route;
- background travel.

### 012. Базовый бой

- mob target;
- attack/skills/shots;
- loot;
- HP/MP;
- death/resurrection;
- controlled zone/class.

### 013. Progression, skills, equipment, enchant

- EXP/SP/level;
- skill learning;
- equipment scoring;
- equip;
- safe enchant policy;
- risk profile.

### 014. NPC commerce и supplies

- grocery;
- weapon/armor;
- расходники;
- budget;
- sell loot;
- teleport;
- transaction safety.

### 015. Background farming и reconciliation — `ACCEPT`

- aggregated farming;
- расход ресурсов;
- drop/EXP/death probability;
- active/background comparison;
- anti-dup.

### 016. PopulationManager — `IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`

- новые персонажи level 1;
- class/level distribution;
- online schedule;
- retirement/return;
- population limits.

### 017. Party coordination kernel, semantic acts и party routes

Status: `ACCEPT_WITH_EXPLICIT_FUTURE_CONTRACTS`.

- canonical invite/accept/refuse;
- contextual roles и vacancies;
- durable Phantom coordination intent;
- shared leader route и regroup;
- typed semantic acts;
- assist/protect/heal priorities.

### 018. Personality, memory, reputation

Status: `ACCEPT_WITH_ACTIVATION_GATE`.

- traits;
- memory events;
- decay;
- relationships;
- action modifiers;
- deterministic tests.

### 019. Semantic Pack: понимание

Status: `IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`.

- русский;
- транслит;
- жаргон;
- fuzzy;
- intents/entities/context;
- test corpus;
- accuracy/performance metrics.

### 020. Conversation engine и действия

Status: `NOT_STARTED`.

- PM/local/trade chat;
- observer-aware generation;
- cooldown;
- personality phrasing;
- action intents;
- нет бессмысленного текста без наблюдателя.

### 021. Spoil, manor, quest drop и craft acquisition chains

Status: `NOT_STARTED`.

- eligibility и source selection;
- spoil/manor/quest collection;
- active/background parity;
- recipe ingredient planning;
- craft preparation без transaction execution.

### 022. Economy transaction kernel, trade, crafting и enchant

- reservation ledger и expiration;
- direct trade и private stores;
- crafting execution;
- enchant risk policy;
- sources/sinks и audit;
- crash/restart conservation;
- anti-dup и lock order.

### 023. Rift и advanced party recruitment

- Rift destination/requirements;
- content-specific composition;
- real roster и missing roles;
- class/supply/travel readiness;
- full-party detection;
- invite/refuse policy.

### 024. Party farming и катакомбы

- follow/assist;
- target assignment;
- room ownership;
- defend/retreat;
- competition.

### 025. PvP, PK, karma и revenge

Status: `NOT_STARTED`.

- risk;
- flags/karma/drop;
- allies;
- spot conflict;
- surrender/retreat;
- защита от бесконечной мясорубки.

### 026. RaidPlanner и эпики

- composition;
- preparation;
- gathering;
- real-player invite;
- attempt/retreat;
- loot policy;
- Queen Ant/Zaken profiles.

### 027. Clans, alliances и wars

- creation;
- leadership;
- recruitment;
- treasury;
- relations;
- war lifecycle;
- clan chat.

### 028. Sieges

- registration;
- schedule;
- gathering;
- roles;
- attack/defense;
- retreat;
- первый сценарий одного замка.

### 029. Whitelist quests, Kamaloka и Pailaka

- adapter framework;
- kill/collect;
- один class quest;
- Kamaloka;
- Pailaka;
- не притворяться универсальным solver.

### 030. Масштабирование и release candidate

- 10 → 25 → 50 → 100 → 250 → 500 профилей;
- controlled materialization;
- soak;
- CPU/memory/DB/path;
- queue stability;
- restart;
- economy balance;
- default-safe config;
- release tag.

---

## 19. Definition of Done

- scope соблюдён;
- архитектура соответствует плану;
- другие хроники не затронуты;
- config/feature flags документированы;
- tests добавлены и выполнены;
- результаты честно записаны;
- нет hot-path лог-спама;
- ошибки/cancellation обрабатываются;
- ресурсы освобождаются;
- транзакции безопасны;
- нет очевидного дюпа;
- отчёт создан;
- commit и push выполнены;
- ветка не оставлена заведомо сломанной.

---

## 20. Правило подготовки задачи

Автор задачи обязан:

1. прочитать этот план;
2. прочитать предыдущий отчёт;
3. проверить предыдущий diff;
4. проверить gates;
5. изучить актуальный код;
6. перечислить точные seams;
7. заранее решить архитектурные вопросы;
8. выделить только те вопросы, где Codex обязан провести кодовый аудит;
9. ограничить scope;
10. определить автоматические проверки;
11. определить блокировку/rollback;
12. дать точный prompt Codex.

---

## 21. Главный принцип

Фантом выглядит живым не из-за потока случайных фраз, а потому что:

- у него есть потребности;
- действия имеют причины;
- он помнит;
- решения имеют последствия;
- он реально владеет предметами и аденой;
- зависит от экономики;
- меняет отношения;
- выполняет или нарушает договорённости;
- развивается во времени;
- его речь отражает состояние и события.

Этот принцип важнее количества декоративных функций.
