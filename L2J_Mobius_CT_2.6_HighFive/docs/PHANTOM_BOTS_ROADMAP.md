# Phantom Bots Roadmap

**Репозиторий:** `kpCat/L2J`  
**Модуль:** `L2J_Mobius_CT_2.6_HighFive`  
**Целевая ветка:** `feature/phantom-world`  
**Путь документа:** `docs/PHANTOM_BOTS_ROADMAP.md`  
**Версия дорожной карты:** 2  
**Дата архитектурного аудита:** 2026-07-25  
**Статус:** обязательный ориентир для постановки GOAL, независимого ревью и контроля прогресса

## 1. Текущее состояние

```text
Последний принятый production baseline:
922f72c0d422904dcbdc6215a5cc1167a1bb84fb

Текущий branch HEAD:
Goal 015 production loot disposition, position canonicalization и anchor
tolerance chain — `ACCEPT`.
Exact pair `22859@giran.farming.22859` поддержана при shipped AutoLoot policy:
immediate/time-limited drops участвуют в canonical RNG и остаются на земле.
Committed anchor Z канонизируется production `GeoEngine` и без test-only
координатной подмены сохраняется через travel, transaction, materialization и
restart.

Task 004 technical feasibility:
ACCEPT

Task 004A:
ACCEPT after Task 004B

Task 004B:
ACCEPT

ADR 0001:
Accepted

Task 005:
ACCEPT

Goal 006 overall:
ACCEPT

Goal 006A:
ACCEPT

Goal 006B:
ACCEPT

Stage I:
COMPLETE

Goal 007:
ACCEPT after Goal 007A

Goal 007A:
ACCEPT

Goal 008: ACCEPT after Goal 008A

Goal 008A: ACCEPT

Goal 009: ACCEPT after Goal 009A

Goal 009A: ACCEPT

Goal 010: ACCEPT after Goal 010A/010B/010C

Goal 010A: ACCEPT

Goal 010B: ACCEPT_WITH_010C_INTEGRATION_BOUNDARY

Goal 010C: ACCEPT

Goal 011: ACCEPT after Goal 011A

Goal 011A: ACCEPT

Stage II: COMPLETE

Goal 012: ACCEPT after Goal 012A

Goal 012A: ACCEPT

Goal 013: ACCEPT after Goal 013B

Goal 013A: ACCEPT after Goal 013B

Goal 013B: ACCEPT_WITH_ACTIVATION_GATE

Goal 014: ACCEPT after Goal 014A

Goal 014A + completion: ACCEPT

Goal 015: ACCEPT

Bounded completion marker:
Goal 015: ACCEPT

Historical pre-acceptance marker (superseded): Goal 015: IMPLEMENTED_PENDING_INDEPENDENT_REVIEW

Goal 016: ACCEPT_WITH_EXPLICIT_FUTURE_CONTRACTS

Goal 017: IMPLEMENTED_PENDING_INDEPENDENT_REVIEW

Goal 018: ACCEPT after activation gates closed in Goal 020 Checkpoint 1

Goal 019: ACCEPT_WITH_EXPLICIT_FUTURE_CONTRACTS

Goal 020 Checkpoint 1: ACCEPT_WITH_ACTIVATION_GATE

Goal 020 Checkpoint 2: ACCEPT

Goal 020: ACCEPT

Goal 021 Checkpoint 1: ACCEPT

Goal 021 Checkpoint 2: ACCEPT

Goal 021: ACCEPT

Goal 022 Checkpoint 1: IMPLEMENTED_PENDING_INDEPENDENT_REVIEW

Goal 022 Checkpoint 2: NOT_STARTED

Goal 022: IMPLEMENTED_PENDING_INDEPENDENT_REVIEW

Goal 024: ACCEPT

Goal 024A: ACCEPT

Goal 025A: ACCEPT

Goal 025 overall: ACCEPT

Accepted baseline: bbd29495a19a322c0629509c85c31fe508ae8d07

Goal 026 overall — `ACCEPT` на required parent `ff631e5f71a43da6e771c3541ee59ee15ea916b3`. Goal 027 Checkpoint 1, Goal 027A/027B/027C/027D/027E/027F и Checkpoint 2 — `ACCEPT`; Goal 027 overall — `ACCEPT`. Goal 028 Checkpoint 1 — `ACCEPT`; Goal 028 Checkpoint 2 — `ACCEPT`; Goal 028 Checkpoint 3 — `ACCEPT`; Goal 028 Checkpoint 4 — `ACCEPT`; Goal 028 Checkpoint 5 — `ACCEPT after Goal 028C`; Goal 028C — `ACCEPT`; Goal 028 overall — `ACCEPT`. Goal 029 Checkpoint 1 — `ACCEPT after Goal 029A/Goal 029B`; Goal 029A — `ACCEPT after Goal 029B`; Goal 029B — `ACCEPT`; Goal 029 Checkpoint 2 — `IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`; Goal 029 overall — `IN_PROGRESS`.

Goal 026 Checkpoint 5: ACCEPT

Goal 026D: ACCEPT

Goal 026 overall: ACCEPT

Goal 027 Checkpoint 1: ACCEPTED

Goal 027A: ACCEPTED

Goal 027B: ACCEPTED

Goal 027C: ACCEPTED

Goal 027 overall: ACCEPT

Goal 027 Checkpoint 2: ACCEPT
```

Task 004 доказала главный архитектурный тезис: canonical `Player` может быть
материализован без TCP, fake `GameClient`, Player subclass/fork и production DB.
Task 004A и Task 004B закрыли найденные lifecycle/retained-identity findings;
seam и ADR 0001 приняты. Goal 005 и её core profile/persistence envelope
приняты. Архитектурное направление Goal 006, Goal 006A и независимо проверенная
Goal 006B приняты; Goal 006 overall имеет `ACCEPT`, а Этап I завершён. Goal 007
и Goal 007A приняты. Независимое review Goal 008 потребовало bounded Goal 008A;
hardening принят на baseline `6ecd8ba1...`. Архитектурное направление Goal 009
принято после independently accepted Goal 009A на baseline `0780c77a...`.
Независимое review Goal 010 потребовало bounded Goal 010A. Generation/signal
ordering Goal 010A принято, а bounded ledger architecture Goal 010B принята с
узкой integration boundary Goal 010C для отсутствующих real-scheduler sources.
Goal 010C независимо принята, поэтому Goal 010 закрыта с `ACCEPT`. Review Goal
011 потребовал bounded Goal 011A; исправление независимо принято, Goal 011
закрыта, а Stage II завершён. Архитектурное направление Goal 012 принято, а
Goal 012A независимо закрыла обязательные action-ownership findings. Goal 012
принята после Goal 012A. Goal 013 и Goal 013A приняты после Goal 013B; Goal 013B
имеет `ACCEPT_WITH_ACTIVATION_GATE`. Goal 014 принята после Goal 014A, а Goal
014A с completion принята. Goal 015 production loot disposition, position
canonicalization и anchor tolerance chain приняты независимым ревью;
production-пара `22859@giran.farming.22859` доказана на shipped AutoLoot policy,
а geodata canonical position сохраняется через ARRIVED и restart.
Goal 016 принята с явными будущими контрактами admission scale и histogram
truth; Goal 017 ожидает независимого review. Goal 018 принята после закрытия
activation gates в Goal 020 Checkpoint 1; Goal 019 принята с явными будущими
контрактами; Checkpoint 1 имеет `ACCEPT_WITH_ACTIVATION_GATE`, а Checkpoint 2 —
`IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`. Goal 021 Checkpoint 1 и Checkpoint 2
приняты; overall baseline закреплён на
`043844c0fd7a0bfcac0d5f58461a21633b032332`. Goal 022 Checkpoint 1 реализован и
ожидает независимого review; Checkpoint 2 не начат. Goal 024A и Goal 024
приняты независимым review; accepted baseline — `922f72c0d422904dcbdc6215a5cc1167a1bb84fb`.
Goal 025A и Goal 025 overall — `ACCEPT`; accepted baseline — `5517081fb2bbf2aa9ad8295130714df2d4b45921`.
Goal 026 overall — `ACCEPT` на required parent `ff631e5f71a43da6e771c3541ee59ee15ea916b3`. Goal 027 Checkpoint 1, Goal 027A/027B/027C/027D/027E/027F и Checkpoint 2 — `ACCEPT`; Goal 027 overall — `ACCEPT`. Goal 028 Checkpoint 1 — `ACCEPT`; Goal 028 Checkpoint 2 — `ACCEPT`; Goal 028 Checkpoint 3 — `ACCEPT`; Goal 028 Checkpoint 4 — `ACCEPT`; Goal 028 Checkpoint 5 — `ACCEPT after Goal 028C`; Goal 028C — `ACCEPT`; Goal 028 overall — `ACCEPT`. Goal 029 Checkpoint 1 — `ACCEPT after Goal 029A/Goal 029B`; Goal 029A — `ACCEPT after Goal 029B`; Goal 029B — `ACCEPT`; Goal 029 Checkpoint 2 — `IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`; Goal 029 overall — `IN_PROGRESS`.

---

## 2. Назначение дорожной карты

Дорожная карта задаёт кратчайший безопасный путь от существующего High Five
сервера к автономной симуляции живых игроков. Она обязана:

- удерживать архитектурные зависимости в одном направлении;
- не требовать от ранней GOAL знания контрактов, определяемых позднее;
- отделять опасные lifecycle/DB/network границы;
- давать Codex заранее определённые входы, выходы и запреты;
- сохранять все пользовательские требования без искусственного размножения GOAL;
- допускать suffix-hotfix (`004A`, `006A`) только после доказанного finding;
- показывать движение к конечной системе, а не только очередной номер задачи.

Текущий task package всегда является непосредственным исполняемым контрактом.
Roadmap не меняет уже выполняемую GOAL задним числом.

---

## 3. Конечная цель

Phantom World должен моделировать не NPC, а полноценных программных игроков,
которые:

- создаются с первого уровня и развиваются;
- получают EXP, SP, профессии, skills и экипировку;
- фармят, используют spoil, manor и поддерживаемые quests;
- покупают supplies, продают loot, торгуют, крафтят и рискуют при enchant;
- формируют party, clan, alliance и социальные отношения;
- участвуют в Rift, raids, epics, PvP/PK и войнах;
- понимают русский L2-сленг и связывают фразы с реальными server IDs;
- имеют personality, memory, reputation и объяснимые цели;
- продолжают причинную жизнь в background;
- правдоподобно materialize/reconcile при приближении игрока;
- не требуют LLM или внешнего AI-сервиса во время работы GameServer.

Runtime-интеллект реализуется Java-кодом, конфигурацией, versioned data,
Semantic Pack, read-only Game Knowledge, Utility AI и bounded simulation.

---

## 4. Неподвижные архитектурные принципы

### 4.1. Canonical actor

Активный Phantom — canonical `Player`.

Запрещены как финальное ядро:

- NPC-based Fake Players;
- `PhantomPlayer extends Player`;
- fork/copy `Player`;
- fake `GameClient`;
- fake/null-network `Connection`;
- request packets как внутренний Phantom API.

### 4.2. Authoritative server data

Drop, spoil, manor, spawn, zones, items, recipes, skills, classes, quests,
raid/epic и world state остаются authoritative server data.

Semantic Pack не копирует всю игру. Над server data строятся read-only services
и compact reverse indexes.

### 4.3. Язык, знания, решение и действие разделены

```text
текст игрока
→ Semantic Pack
→ intent/entities/slots/confidence
→ Game Knowledge
→ grounded goal/situation
→ Utility AI / planner
→ semantic act
→ validated server action и/или verbalization
```

### 4.4. Explicit goals

Действующий Phantom имеет структурированную цель:

```text
type
subject/target IDs
required/current amount
acquisition method
valid sources
selected region/anchor
purpose
priority
risk/expense budget
deadline/constraint
reason
completion/replan/abandon conditions
```

### 4.5. Typed internal communication

Phantom-to-Phantom взаимодействие использует semantic acts и domain objects,
например `REQUEST_SPOT_SHARE`, `REQUEST_PARTY_ROLE`, `WARN_PVP`.

Текст генерируется только для наблюдаемого настоящим игроком канала.

### 4.6. Пять уровней simulation detail

```text
ACTIVE
NEARBY / PERCEPTIBLE
WARM
BACKGROUND
SLEEPING
```

`NEARBY / PERCEPTIBLE` обязателен. Interest model учитывает topology, instance,
room adjacency, doors, local chat, combat perceptibility, targetability,
party/conflict relation и time-to-contact, а не только расстояние.

### 4.7. Единая причинная модель

BACKGROUND — это дешёвая агрегация тех же правил, а не бесплатная генерация.
Учитываются class, skills, gear, HP/MP, supplies, kill speed, drop/spoil method,
competition, death, travel, inventory, adena sources/sinks и party composition.

### 4.8. Reconciliation

При повышении детализации согласуются position/anchor, route, goal, progress,
inventory, HP/MP, party, occupied spot и уже наблюдавшиеся события.

Запрещены дюп, невозможная телепортация и переписывание perceptible history.

### 4.9. Disabled behavior

При `EnablePhantomSystem=False`:

- не создаются Phantom Player/profile processing/tasks/threads/world objects;
- нет Phantom DB query и network activity;
- ordinary player packet/login behavior не меняется;
- нет periodic Phantom logs;
- экономика и World остаются исходными.

### 4.10. Runtime resource policy

- no per-phantom executor;
- no permanent per-phantom thread;
- shared bounded queues;
- bounded memory and traces;
- explicit cancellation and ownership;
- no high-frequency INFO/WARNING;
- degradation under overload вместо unbounded backlog.

---

## 5. Оптимальное количество задач

Дорожная карта сохраняет **30 основных GOAL**. Это оптимальный рабочий вариант
для текущего объёма требований:

- меньшее число заставило бы смешать несовместимые risk boundaries;
- большее число начало бы дробить законченные вертикальные результаты;
- suffix-задачи не планируются заранее и появляются только после реального
  P0/P1 finding или environment blocker;
- documentation-only closure по возможности включается в следующую
  содержательную GOAL.

### Правило объединения

Связанные изменения объединяются, если они дают один проверяемый результат,
имеют общий lifecycle и один уровень риска.

### Правило разделения

Разделение обязательно для сочетаний:

- session/network + economy;
- Player lifecycle + AI;
- persistence schema + pathfinding;
- real-login ownership + mass population;
- trade/mail + combat;
- Semantic Pack + destructive migration;
- geodata + social policy.

### Follow-up risk

Риск подзадач ниже означает вероятность suffix-hotfix после независимого ревью,
а не разрешение заранее дробить GOAL:

- `LOW` — follow-up маловероятен;
- `MEDIUM` — возможна узкая корректировка;
- `HIGH` — вероятен bounded hotfix на edge cases;
- `VERY_HIGH` — критическая граница; suffix-hotfix реалистичен.

---

## 6. Явный dependency DAG

Основной порядок не содержит циклов:

```text
001 → 002 → 003 → 004 → 004A → 004B → 005 → 006

006 → 007 → 008
006 → 009
007 + 009 → 010
010 → 011
008 + 009 + 010 + 011 → 012
012 → 013
011 + 013 → 014
007 + 008 + 012 + 013 + 014 → 015
005 + 006 + 007 + 015 → 016
008 + 010 + 013 + 016 → 017
005 + 008 + 017 → 018
008 + 011 → 019
017 + 018 + 019 → 020
011 + 012 + 013 + 015 + 019 → 021
005 + 006 + 014 + 021 → 022
010 + 013 + 017 + 020 → 023
010 + 018 + 020 + 021 + 023 → 024
012 + 013 + 017 + 018 + 020 + 024 → 025
009 + 010 + 011 + 013 + 017 + 023 + 025 → 026
005 + 017 + 018 + 022 + 025 + 026 → 027
003 + 006 + 007 + 015 + 020 + 022 + 027 → 028
006 + 007 + 009 + 010 + 015 + 016 + 028 → 029
001–029 ACCEPT → 030
```

### Контракты, разрывающие потенциальные циклы

- Goal 007 определяет activity state machine и принимает абстрактные
  `RelevanceSignal`; topology providers появляются в Goal 010.
- Goal 008 определяет generic `DomainRef` и capability requirements; реальные
  class capabilities появляются в Goal 013.
- Goal 011 создаёт Game Knowledge до combat/background; Goal 019 только
  связывает язык с уже существующим knowledge API.
- Goal 015 агрегирует только уже поддержанные acquisition methods; spoil/manor/
  quest/craft расширяются в Goal 021.
- Goal 017 создаёт party coordination без natural language; Goal 020 добавляет
  conversation policy поверх semantic acts.
- Goal 026 использует party/command channel без обязательного clan lifecycle;
  clan/alliances/wars появляются в Goal 027.

---

# 7. Этап I — Canonical actor, persistence и lifecycle

**GOAL:** 001–006  
**Текущий статус:** Task 004/004A/004B, Goal 005, Goal 006A и Goal 006B приняты; Goal 006 overall — `ACCEPT`; Stage I — `COMPLETE`; Goal 007 — `ACCEPT after Goal 007A`; Goal 007A — `ACCEPT`; Goal 008 — `ACCEPT after Goal 008A`; Goal 008A — `ACCEPT`; Goal 009 — `ACCEPT after Goal 009A`; Goal 009A — `ACCEPT`; Goal 010 — `ACCEPT after Goal 010A/010B/010C`; Goal 010A — `ACCEPT`; Goal 010B — `ACCEPT_WITH_010C_INTEGRATION_BOUNDARY`; Goal 010C — `ACCEPT`; Goal 011 — `ACCEPT after Goal 011A`; Goal 011A — `ACCEPT`; Stage II — `COMPLETE`; Goal 012 — `ACCEPT after Goal 012A`; Goal 012A — `ACCEPT`; Goal 013/013A — `ACCEPT after Goal 013B`; Goal 013B — `ACCEPT_WITH_ACTIVATION_GATE`; Goal 014 — `ACCEPT after Goal 014A`; Goal 014A + completion — `ACCEPT`; Goal 015 — `ACCEPT`; Goal 016 — `ACCEPT_WITH_EXPLICIT_FUTURE_CONTRACTS`; Goal 017 — `IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`; Goal 018 — `ACCEPT`; Goal 019 — `ACCEPT_WITH_EXPLICIT_FUTURE_CONTRACTS`; Goal 020 — `ACCEPT`; Goal 020 Checkpoint 1 — `ACCEPT_WITH_ACTIVATION_GATE`; Goal 020 Checkpoint 2 — `ACCEPT`; Goal 021 — `ACCEPT`; Goal 022 Checkpoint 1 — `IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`; Goal 022 Checkpoint 2 — `NOT_STARTED`; Goal 023 — `ACCEPT`; Goal 024 — `ACCEPT`; Goal 024A — `ACCEPT`; Goal 025A и Goal 025 overall — `ACCEPT`; accepted baseline — `5517081fb2bbf2aa9ad8295130714df2d4b45921`.
Goal 026 overall — `ACCEPT` на required parent `ff631e5f71a43da6e771c3541ee59ee15ea916b3`. Goal 027 Checkpoint 1, Goal 027A/027B/027C/027D/027E/027F и Checkpoint 2 — `ACCEPT`; Goal 027 overall — `ACCEPT`. Goal 028 Checkpoint 1 — `ACCEPT`; Goal 028 Checkpoint 2 — `ACCEPT`; Goal 028 Checkpoint 3 — `ACCEPT`; Goal 028 Checkpoint 4 — `ACCEPT`; Goal 028 Checkpoint 5 — `ACCEPT after Goal 028C`; Goal 028C — `ACCEPT`; Goal 028 overall — `ACCEPT`. Goal 029 Checkpoint 1 — `ACCEPT after Goal 029A/Goal 029B`; Goal 029A — `ACCEPT after Goal 029B`; Goal 029B — `ACCEPT`; Goal 029 Checkpoint 2 — `IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`; Goal 029 overall — `IN_PROGRESS`.

## Goal 001 — Baseline и полный аудит — `ACCEPT`

**Назначение:** доказать исходные seams и риски.  
**Результат:** dependency map, feasibility verdict, scope boundaries.  
**Зависимости:** нет.  
**Не включает:** production behavior.  
**Gate:** доказательный `FEASIBLE_WITH_SEAM` или пересмотр плана.  
**Follow-up risk:** `LOW` — read-only аудит; 001A уже закрыла provenance.

## Goal 002 — Автоматическая test infrastructure — `ACCEPT`

**Назначение:** isolated deterministic verification.  
**Результат:** JDK-only runner, Ant, test DB, provisioning, guards.  
**Зависимости:** 001.  
**Не включает:** Phantom runtime.  
**Gate:** production DB unreachable, negative controls и repeatable verify.  
**Follow-up risk:** `VERY_HIGH` — DB provisioning/locking; фактически потребовала 002A.

## Goal 003 — Disabled skeleton — `ACCEPT`

**Назначение:** безопасная production integration point.  
**Результат:** config, lifecycle owner, inert bounded queue, metrics/trace.  
**Зависимости:** 002.  
**Не включает:** Player, persistence, AI.  
**Gate:** disabled path allocates/changes nothing.  
**Follow-up risk:** `LOW` — малый локальный scope.

## Goal 004 — Canonical headless Player feasibility — `ACCEPT`

**Назначение:** доказать canonical Player без TCP.  
**Технический результат:** seam, zero-network effects, create/load/materialize/
cleanup/reload доказаны.  
**Зависимости:** 003.  
**Не включает:** final production lifecycle, profiles, AI.  
**Gate:** закрыт Task 004A и Task 004B: race-free real login, fail-closed exact
object-ID lease release и retryable cleanup.
**Follow-up risk:** `VERY_HIGH` — session/identity/cleanup; фактически возникли 004A и 004B.

### Goal 004A — обязательная closure-задача, не новый основной GOAL

Закрыла findings независимого ревью Task 004 по login/disconnect ordering и
retryable cleanup; принята после retained-identity correction Task 004B.

### Goal 004B — обязательная retained-identity closure-задача

Закрыла bypass удержанного `REAL_LOGIN` owner, wrong-character lease release и
exact-instance cleanup postconditions. Task 004B принята; revert не требуется;
ADR 0001 переведён в `Accepted`.

## Goal 005 — Core Phantom profile и persistence envelope — `ACCEPT`

**Назначение:** сохранить устойчивую идентичность Phantom независимо от Player
materialization.  
**Зависимости:** 004B ACCEPT.
**Архитектурный результат:**

- stable `phantomProfileId` и optional canonical `characterObjectId` link;
- lifecycle-independent core state;
- schema/version columns;
- component envelope (`componentType`, `version`, bounded payload or normalized
  table ownership) для будущих goals/memory/schedule без определения их модели;
- repository/DAO;
- optimistic locking;
- idempotent migrations;
- round-trip/restart/concurrent-update tests.

**Не включает:** personality traits, schedule model, long-term goal schema,
Utility AI, population или materialization.  
**Gate:** unknown future components могут добавляться versioned migration без
переписывания core identity.  
**Follow-up risk:** `HIGH` — schema/versioning/concurrent update.

## Goal 006 — Production materialization lifecycle — `ACCEPT`

**Назначение:** превратить spike в production-owned, disabled-by-default
materialization service.  
**Зависимости:** 004B, 005.
**Архитектурный результат:**

- identity claim/handoff;
- materialize/dematerialize;
- bounded action admission/cancellation;
- World registration;
- cleanup postconditions/retry;
- restart recovery только в safe unmaterialized state;
- materialization cap interface;
- lifecycle metrics.

**Не включает:** schedules, activity states, AI, navigation, population.  
**Gate:** restart, collision, failure injection и disabled equivalence.  
**Follow-up risk:** `VERY_HIGH` — Player/World/login/restart boundary.

### Goal 006A — обязательная materialization boundary closure-задача — `ACCEPT`

Закрывает findings Goal 006 по World/autosave identity boundary,
action/`STOPPING` atomicity, wall-clock budget caller `shutdown` и provenance.

### Goal 006B — обязательная server shutdown handoff closure-задача — `ACCEPT`

Координирует первый Phantom drain до generic disconnect, strict managed-actor
skip и вторую bounded shutdown/retry попытку перед shared ThreadPool stop.
Независимое review зафиксировано в
`docs/phantoms/reviews/006b-server-shutdown-handoff-review.md`; Goal 006 и
Stage I закрыты с `ACCEPT`.

### Gate Этапа I

- ADR 0001 accepted;
- canonical Player без TCP;
- production lifecycle idempotent;
- real login и Phantom ownership не пересекаются;
- profile restart-safe;
- no World/online/autosave/task/item residue;
- disabled ordinary server behavior unchanged.

---

# 8. Этап II — Scheduler, goals, navigation и authoritative knowledge

**GOAL:** 007–011  
**Зависит от:** Этап I.

## Goal 007 — Shared scheduler и activity state machine — `ACCEPT after Goal 007A`

**Назначение:** обслуживать profiles без per-phantom tasks.  
**Зависимости:** 006.  
**Архитектурный результат:**

- `ACTIVE`, `NEARBY_PERCEPTIBLE`, `WARM`, `BACKGROUND`, `SLEEPING`;
- bounded shared work queues;
- fairness and budgets;
- promotion/demotion state machine;
- abstract immutable `RelevanceSignal` input;
- overload degradation;
- no topology calculation inside scheduler.

**Не включает:** room graph, geodata, goals, combat.  
**Gate:** deterministic transitions, bounded backlog, thousands of dormant
profiles without thousands of futures.  
**Follow-up risk:** `HIGH` — fairness, cancellation and overload edges.

Goal 007A реализовала required closure только для transition ownership и stop
quiescence; статус — `ACCEPT`.

## Goal 008 — Goal model, Utility AI core и plan executor — `ACCEPT after Goal 008A`

**Назначение:** дать объяснимое решение без domain-specific giant scripts.  
**Зависимости:** 005, 007.  
**Архитектурный результат:**

- immutable/versioned goal contract;
- generic `DomainRef` IDs, не Game Knowledge implementation;
- considerations and normalized score;
- deterministic tie breaking;
- generic capability requirement keys;
- action candidate registry;
- plan steps, timeout, cancellation and replanning;
- reason/explanation snapshot.

**Не включает:** concrete class catalog, combat actions, Semantic Pack.  
**Gate:** deterministic scenario corpus и safe cancellation.  
**Follow-up risk:** `HIGH` — scoring stability/executor state machine.

## Goal 009 — Navigation feasibility и PhantomNavigationService baseline — `ACCEPT after Goal 009A`

**Назначение:** объединить benchmark и первый полезный service, не создавая
proof-only GOAL.  
**Зависимости:** 006.  
**Архитектурный результат:**

- factual geodata/no-geodata capability detection;
- direct-path first;
- bounded local path request;
- cache/cooldown/cancellation;
- stuck/timeout result contract;
- path budgets and metrics;
- graceful no-geodata behavior.

**Не включает:** semantic anchors, rooms, party routes.  
**Gate:** benchmark-backed budgets и deterministic fallback.  
**Follow-up risk:** `HIGH` — geodata variability and CPU budget.

Goal 009A закрыла обязательные findings по route truth, backend preflight,
dispatch/stop ordering и aggregate shutdown diagnostic; independent review —
`ACCEPT`.

## Goal 010 — Topology, anchors и perception graph — `ACCEPT after Goal 010A/010B/010C`

**Назначение:** реализовать server-world topology и providers для Goal 007.  
**Зависимости:** 007, 009.  
**Архитектурный результат:**

- regions, cities, shops, Gatekeepers and farming anchors;
- catacomb/dungeon rooms, adjacency, doors/passages;
- route anchors and background travel edges;
- local-chat/combat/targetability perception channels;
- `RelevanceSignal` providers;
- topology validation and versioned data.

**Не включает:** party route policy, Game Knowledge item/drop indexes.  
**Gate:** perceptible neighbor cannot be demoted to pure statistics.  
**Follow-up risk:** `HIGH` — map completeness and perceptibility correctness.

Goal 010A устранила findings generation/signal ordering: exact topology
generation для profile update и perception delivery, полная пересборка
memberships с инвалидацией provider-owned sources до swap, а также явный
retryable cleanup при unregister. Независимое review приняло это направление,
но потребовало Goal 010B для bounded lifetime signal ownership.

Goal 010B заменяет исторические sequence identities и отдельные cleanup
tombstones одним capped per-profile ledger с тремя fixed sources. Active,
retained и failed-cleanup identities используют общий
`maximumRegisteredProfiles`; release разрешён только после all-three scheduler
`NOT_REGISTERED` evidence либо final stop. Goal 010C независимо принята,
поэтому Goal 010 закрыта с `ACCEPT`.

## Goal 011 — Authoritative Game Knowledge и reverse indexes — `ACCEPT after Goal 011A`

**Назначение:** предоставить read-only предметное знание до combat/background и
до Semantic Pack.  
**Зависимости:** 010 и существующие server data loaders.  
**Архитектурный результат:** compact immutable indexes:

```text
item -> drop mobs
item -> spoil mobs
item -> manor sources
mob -> spawn/topology areas
zone/level range -> suitable targets
recipe -> ingredients
ingredient -> recipes
content -> role requirements
class/role -> capability facts
```

- stable IDs;
- source hash/version;
- startup/lazy single build;
- no DB query or full scan in hot path;
- query API independent of language and Utility AI.

**Не включает:** parsing Russian text, action decisions, mutable world economy.  
**Gate:** source-of-truth parity and bounded lookup benchmark.  
**Follow-up risk:** `HIGH` — many heterogeneous authoritative data sources.

### Gate Этапа II

- no per-profile scheduler task;
- activity transitions consume explicit relevance signals;
- goals are deterministic and cancellable;
- navigation bounded with no-geodata mode;
- topology protects perceptible history;
- knowledge queries are indexed and authoritative.

---

# 9. Этап III — Solo gameplay, progression и causal background

**GOAL:** 012–016  
**Зависит от:** Этапы I–II.

## Goal 012 — Capability-driven combat kernel — `ACCEPT after Goal 012A`

**Назначение:** минимальный реальный бой через server-side facades.  
**Зависимости:** 008–011.  
**Архитектурный результат:** target/threat, attack, selected skills, shots,
HP/MP, loot, death/resurrection для ограниченного archetype matrix; generic
combat capability interface.  
**Не включает:** полный class catalog, party combat, spoil/manor, PvP policy.  
**Gate:** canonical rules, no client packets, action cancellation and combat
failure cleanup.  
**Follow-up risk:** `VERY_HIGH` — skills/death/World timing.

### Goal 012A — Combat action ownership truth — `ACCEPT`

**Назначение:** bounded closure findings Goal 012 по shared worker dispatch,
canonical action cleanup, causal loot truth, selected-skill safety и
plan-owned respawn.
**Зависимости:** 012.
**Gate:** independent review exact child commit принят; Goal 013 разрешена.

## Goal 013 — Progression, professions, skills, equipment и class catalog — `ACCEPT after Goal 013B`

**Назначение:** расширить generic capability keys реальными High Five классами.  
**Зависимости:** 012.  
**Архитектурный результат:** EXP/SP/level, profession transitions, skill
learning, equipment scoring/equip, actual class capability catalog including
healer/tank/spoiler/dagger/summoner/escape.  
**Не включает:** enchant policy, trade economy, party coordination.  
**Gate:** representative class matrix and no one-script-per-class architecture.  
**Follow-up risk:** `HIGH` — broad class rules and progression persistence.

### Goal 013A — Progression capability extensibility hardening — `ACCEPT after Goal 013B`

**Назначение:** bounded closure доказанных variant/resource/summon/equipment,
production-composition, skill-learning atomicity и Player CP snapshot findings.
**Зависимости:** exact Goal 013 commit `ca50ea28...`; accepted pre-013 baseline
`8dba87e9...`.
**Архитектурный результат:** independently addressable capability variants;
authoritative action resources; typed controlled-actor facts; complete bounded
equipment paging; exact main/subclass truth; separate canonical Player CP.
**Не включает:** tactical doctrine, commerce, reconciliation, party или PvP.
**Gate:** independent review сохранило extensibility-результаты, а bounded Goal
013B закрыла durability finding; Goal 013 и Goal 013A приняты после Goal 013B.

### Goal 013B — Durable CLASS skill learning transaction — `ACCEPT_WITH_ACTIVATION_GATE`

**Назначение:** закрыть единственный критический finding Goal 013A: memory-first
skill learning, допускавший `SUCCESS` без durable `character_skills` row.
**Зависимости:** exact Goal 013A commit `06929a297...`; accepted pre-013 baseline
`8dba87e9...`.
**Архитектурный результат:** один MariaDB transaction для exact item object,
main/subclass SP и exact class-indexed skill row; runtime reconciliation только
после commit; fresh DB/runtime postconditions; fail-stop после post-commit
invariant failure.
**Не включает:** profession/subclass mutation, commerce, combat/CP,
materialization, scheduler, profile schema, config или future Goal.
**Gate:** Goal 013B принят с activation gate: production candidates не вызывают
`progression.learn_skill`; перед будущей автономной mutation требуется отдельное
доказательство общей координации SP/item writers.

## Goal 014 — NPC commerce, supplies, travel и sell loop — `ACCEPT after Goal 014A`

**Назначение:** замкнуть одиночный economic maintenance loop.  
**Зависимости:** 009–011, 013 и independent acceptance 013B.
**Архитектурный результат:** immutable authoritative buylist/multisell-query/
teleporter/supply catalog; exact unlimited NPC buy, exact owned-object sell и
NORMAL Gatekeeper teleport через durable `commerce.operation` receipt с
консервативным restart/idempotency reconciliation.
CP potion supplies, vendors, restrictions, currency и cost извлекаются только
из current authoritative item/NPC/buylist/multisell data. Ancient Adena не
предполагается.
**Не включает:** player trade, private stores, crafting ledger, enchant.  
**Gate:** independent review item/adena/position conservation, exact current
data parity и restart-safe interruption.
**Follow-up risk:** `HIGH` — canonical commerce validation and partial actions.
**Correction:** Goal 014A + completion — `ACCEPT`.

## Goal 015 — Background farming baseline и reconciliation — `ACCEPT`

**Назначение:** causal cheap simulation для уже поддержанных plans.  
**Зависимости:** 007, 008, 011–014.  
**Архитектурный результат:** aggregated normal combat/drop/EXP/SP, supplies,
death, travel, competition, inventory limits and active/background
reconciliation for already selected targets.  
Materialization/background transition не должен бесплатно сбрасывать или
восстанавливать canonical Player CP.
**Не включает:** spoil/manor/quest/craft chains, которые добавляет Goal 021.  
**Gate:** active/background conservation and anti-dup under repeated transitions.  
**Production loot disposition:** exact pair `22859@giran.farming.22859`
поддерживает ordinary `ACQUIRE` и immediate/time-limited
`LEAVE_ON_GROUND`; loot-policy config входит в authority hash.
**Position canonicalization:** committed X/Y принадлежат topology anchor, Z
нормализуется production `GeoEngine`; partial travel сохраняет последнюю
position, ARRIVED атомарно пишет canonical coordinates, которые без test-only
snap проходят materialization и restart.
**Anchor normalization tolerance:** helper fail closed сравнивает normalized Z с
raw anchor Z через long arithmetic. `giran.route.north` хранит canonical
`-4072` при tolerance `0`; `giran.farming.22859` сохраняет factual spawn
`-3061`, допускает exact delta `5` и канонизируется в `-3056`. Production
topology loader, factual spawn, node geometry и edge endpoints валидируются.
**Follow-up risk:** `VERY_HIGH` — probabilistic causality and reconciliation.

## Goal 016 — PopulationManager и schedules — `ACCEPT_WITH_EXPLICIT_FUTURE_CONTRACTS`

**Назначение:** создать и обслуживать популяцию profiles.  
**Зависимости:** 005–007, 015.  
**Архитектурный результат:** level-1 creation, schedule model, sleeping/wakeup,
online/region distribution, materialization requests, retirement/return,
backpressure and configurable population targets.  
**Не включает:** social personality, clans, conversation.  
**Gate:** population limits, restart, no hardcoded fantasy counts, bounded DB
writes.  
**Safety completion:** packet-free initialization, versioned exact authority,
restart-safe projection repair, autosave-suppressed read-only verification,
explicit ownership retries, bounded pulse и shutdown publication barrier
реализованы; независимое review приняло Goal с явными будущими контрактами
admission scale и histogram truth.
**Follow-up risk:** `HIGH` — mass state and load shaping.

### Gate Этапа III

Новый Phantom может причинно развиваться, покупать supplies, фармить, умирать,
спать, возобновляться и переходить background/active без дюпа.

---

# 10. Этап IV — Party, memory, Semantic Pack и conversation

**GOAL:** 017–020

## Goal 017 — Party coordination kernel, semantic acts и party routes — `IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`

**Назначение:** структурированное групповое поведение до natural language.  
**Зависимости:** 008, 010, 013, 016.  
**Архитектурный результат:** party roles/vacancies, invite/accept/refuse,
leader/follower responsibilities, group goals, typed semantic acts, shared party
route, assist/protect/heal priorities.  
**Не включает:** clan hooks, Rift-specific composition, text generation.  
**Gate:** real party lifecycle, cancellation and leader/member recovery.  
**Follow-up risk:** `HIGH` — party concurrency and route coordination.

## Goal 018 — Personality, memory, reputation и relationship modifiers — `ACCEPT`

**Назначение:** устойчивые индивидуальные решения и отношения.  
**Зависимости:** 005, 008, 017.  
**Архитектурный результат:** traits; trust/respect/fear/anger/friendship/rivalry/
debt; bounded important-event memory; decay/expiry; agreement history; goal,
risk and conversation modifiers.  
**Не включает:** language parsing, clan group memory, PvP implementation.  
**Gate:** bounded persistence, deterministic decay and no unbounded event log.  
**Follow-up risk:** `MEDIUM` — primarily bounded model/persistence tuning.

## Goal 019 — Semantic Pack и entity grounding — `ACCEPT_WITH_EXPLICIT_FUTURE_CONTRACTS`

**Назначение:** понять русский L2-язык без runtime LLM.  
**Зависимости:** 008, 011.  
**Архитектурный результат:** normalization, slang, abbreviations, aliases,
transliteration, intents, slots, context, confidence, clarification and entity
linking only through Game Knowledge IDs; deterministic corpus.  
**Не включает:** world data duplication, conversation action dispatch,
personality policy.  
**Gate:** precision/recall corpus, ambiguity and low-confidence safety.  
**Follow-up risk:** `HIGH` — Russian slang/entity ambiguity and corpus breadth.

## Goal 020 — Conversation policy, verbalization и action dispatch — `ACCEPT`

Checkpoint 1: `ACCEPT_WITH_ACTIVATION_GATE`.
Checkpoint 2: `ACCEPT`; final baseline
`d48dccb42dcfe5993f1c852e021086e498c0622d`.

**Назначение:** связать understanding с безопасным semantic act/action.  
**Зависимости:** 017–019.  
**Архитектурный результат:** dialogue state, semantic act selection,
confidence gates, clarification, personality-aware verbalization, chat channels,
action dispatch allowlist and observer-only text.  
**Не включает:** domain-specific spoil/Rift/PvP policies, runtime LLM, text
round-trip between Phantoms.  
**Gate:** low confidence cannot trigger dangerous action; generated response
matches structured decision.  
**Follow-up risk:** `HIGH` — action safety and dialogue state.

### Gate Этапа IV

Phantom формирует party, помнит отношения, понимает русский игровой текст,
связывает entities с server IDs и отвечает/действует только через confidence-safe
semantic acts.

---

# 11. Этап V — Domain depth: acquisition, economy, conflicts и group content

**GOAL:** 021–027

## Goal 021 — Spoil, manor, quest drop и craft acquisition chains — `ACCEPT`

Checkpoint 1: `ACCEPT`; pinned baseline
`0045f60417f4605f46e3058b9a694278283b1456`.
Checkpoint 2 (manor/quest): `ACCEPT`; pinned baseline
`043844c0fd7a0bfcac0d5f58461a21633b032332`. Консервативное factual coverage
остаётся `15` mapped и `20` distance-infeasible territories без invented anchors.

**Назначение:** предметные способы получения ресурсов.  
**Зависимости:** 011–015, 019.  
**Архитектурный результат:** eligibility, source selection, progress, source
switching and active/background integration for spoil/manor/quest collection;
recipe ingredient planning and craft preparation.  
**Не включает:** transactionally executing player crafting/trade — Goal 022.  
**Gate:** ресурс не появляется без допустимого метода/capability/source.  
**Follow-up risk:** `HIGH` — multi-source rules and active/background parity.

## Goal 022 — Economy transaction kernel, trade, crafting и enchant — `IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`

Checkpoint 1: `IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`.

Checkpoint 2: `NOT_STARTED`.

**Назначение:** безопасные multi-party/item/adena operations.  
**Зависимости:** 005, 006, 014, 021.  
**Архитектурный результат:** reservation ledger, expiration, lock order,
direct trade, private stores, buy/sell offers, crafting execution, enchant risk
policy, significant-operation audit, sources/sinks and crash/restart
conservation.  
**Не включает:** combat/PvP policy, clan warehouse.  
**Gate:** no item/adena duplication across injected failures and restart.  
**Follow-up risk:** `VERY_HIGH` — transaction/anti-dup/deadlock boundary.

## Goal 023 — Rift и advanced party recruitment — `CORRECTIVE_023C_IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`

**Назначение:** дополнить базовую Goal 017 content-specific composition.  
**Зависимости:** 010, 013, 017, 020.  
**Архитектурный результат:** Rift destination/requirements, real roster,
missing roles, class/supply/travel readiness, full-party detection and
invite/refuse policy; route-aware Goal 017 binding и exact target-side managed
consent закрыты corrective Goal 023B. Corrective Goal 023C сохраняет typed terminal
Navigation failure, исключает hidden route/deadline ownership и возвращает Rift к
обычному replan. Goal 023 overall: `CHANGES_REQUIRED` до независимого review 023C.

**Не включает:** general party kernel, raid/epic orchestration.  
**Gate:** ответы о недостающей роли следуют из реального состава.  
**Follow-up risk:** `HIGH` — instance/content and composition edge cases.

## Goal 024 — Farming spot negotiation и resource conflict — `ACCEPT`

**Назначение:** согласование реальных целей в perceptible topology.  
**Зависимости:** 010, 018, 020, 021, 023.  
**Архитектурный результат:** claims на mob groups/rooms, alternatives, remaining
amount, agreement history, share/wait/move/refuse/escalate semantic acts and
perceptible-history protection.  
Corrective Goal 024A принят: mutable evidence до FINAL отделено от stable live binding;
persisted causal receipt поддерживает exact pair после restart; Goal021 lifecycle
автоматически сводит bilateral terminal truth и durable Goal018 retry.
R024A-01/02/03 закрыты; Goal 024 overall — `ACCEPT`.
**Не включает:** actual PvP/PK execution.  
**Gate:** решения объяснимы целями обеих сторон и world facts.  
**Follow-up risk:** `HIGH` — topology, goals, memory and dialogue convergence.

## Goal 025 — PvP/PK, threat и escalation — `ACCEPT`

**Назначение:** безопасно исполнять конфликтные решения.  
**Зависимости:** 012, 013, 017, 018, 020, 024.  
**Implementation:** bounded causal PvP/PK реализован через Goal 012 combat owner, exact owner evidence Goals 017/018/020/024, canonical Player consequences и navigation-owned retreat; Goal 025A и Goal 025 overall приняты на baseline `5517081fb2bbf2aa9ad8295130714df2d4b45921`.
**Архитектурный результат:** strength/risk, party/friend allies, retreat,
warning, help calls, revenge memory, zone rules, karma/drop consequences and
bounded escalation.  
Doctrine учитывает current/max CP, canonical PvP damage order CP → HP, natural
CP regeneration, stock/reuse CP potions, economic consumption и Olympiad
restrictions.
**Не включает:** formal alliances/clan wars — Goal 027.  
**Gate:** canonical PvP/PK/karma rules and no uncontrolled aggression.  
**Follow-up risk:** `VERY_HIGH` — gameplay harm, concurrency and consequence rules.

## Goal 026 — Raid и epic orchestration — `ACCEPT`

**Назначение:** правдоподобный large-group content без free victory.  
**Зависимости:** 009–011, 013, 017, 023, 025.  
**Checkpoint truth:** Goal 026 overall — `ACCEPT` на required parent `ff631e5f71a43da6e771c3541ee59ee15ea916b3`. Documentation-only closure не является причиной повторного запуска accepted gates.
**Архитектурный результат:** content facts, party/command-channel composition,
readiness, gathering, route, timing, recruitment, retreat and win feasibility.  
**Не включает:** mandatory clan strategy; Goal 027 extends it.  
**Gate:** objectively incapable group cannot aggregate a victory.  
**Follow-up risk:** `VERY_HIGH` — multi-group orchestration and content diversity.

## Goal 027 — Clan lifecycle, alliances и wars — `ACCEPT`

**Checkpoint truth:** Checkpoint 1, Goal 027A/027B/027C/027D/027E/027F и Checkpoint 2 — `ACCEPT`; Goal 027 overall — `ACCEPT` на baseline `fba76efdc5a42d93aeb8a9d64185da3e9d3c7585`.
**Назначение:** persistent long-term social organizations.  
**Зависимости:** 005, 017, 018, 022, 025, 026.  
**Архитектурный результат:** recruitment, roles, contributions, clan goals,
warehouse policy, alliances, wars, reputation, group memory and safe membership
changes.  
**Не включает:** final scale/operations work.  
**Gate:** restart-safe membership and canonical clan/war rules.  
**Follow-up risk:** `VERY_HIGH` — broad persistence/social/economy interactions.

### Gate Этапа V

Phantoms добывают ресурсы допустимыми способами, безопасно торгуют/крафтят,
договариваются о spots, участвуют в Rift/PvP/raids и формируют clans без дюпа и
без случайных phrase-bank решений.

---

# 12. Этап VI — Operations, scale и release

**GOAL:** 028–030

## Goal 028 — Operations, admin controls, observability и replay — `ACCEPT`

**Checkpoint truth:** Checkpoint 1 — `ACCEPT`; Checkpoint 2 — `ACCEPT`; Checkpoint 3 — `ACCEPT`; Checkpoint 4 — `ACCEPT`; Checkpoint 5 — `ACCEPT after Goal 028C`; Goal 028C — `ACCEPT`; Goal 028 overall — `ACCEPT`.
**Назначение:** получить инструменты до массового soak, а не после него.  
**Зависимости:** 003, 006, 007, 015, 020, 022, 027.  
**Архитектурный результат:** enable/disable/drain, per-state metrics, bounded
selected-Phantom trace, reason view, stuck/slow thresholds, economic audit and
deterministic replay.  
**Не включает:** target-scale tuning.  
**Gate:** operator может понять, остановить и воспроизвести проблему без log
spam.  
**Follow-up risk:** `HIGH` — cross-subsystem observability and safe controls.

## Goal 029 — Scale, soak и overload degradation — `IN_PROGRESS`

**Checkpoint truth:** Checkpoint 1 — `ACCEPT after Goal 029A/Goal 029B`; Goal 029A — `ACCEPT after Goal 029B`; Goal 029B — `ACCEPT`; Checkpoint 2 — `IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`; Goal 029 overall — `IN_PROGRESS`.
**Назначение:** доказать целевой масштаб с уже готовой observability.  
**Зависимости:** 006, 007, 009, 010, 015, 016, 028.  
**Архитектурный результат:** profile/activity counts, materialization caps,
fairness, queue/tick/path/DB/memory budgets, long soak, load spike recovery and
degradation policies.  
**Не включает:** новые gameplay features.  
**Gate:** bounded memory/queues/DB rate and recovery under overload.  
**Follow-up risk:** `VERY_HIGH` — environment-dependent long-running behavior.

## Goal 030 — End-to-end autonomous world alpha и release gate

**Назначение:** принять всю систему как один living-world product.  
**Зависимости:** 001–029 ACCEPT.  
**Архитектурный результат:** fresh bootstrap, population, progression,
activity transitions, farming, spoil/craft/trade, party/Rift/PvP/raid,
conversation, clans, restart/failure recovery, soak, operator docs and rollback.  
**Не включает:** новые функции вне roadmap.  
**Gate:** end-to-end alpha scenarios, disabled regression and release decision.  
**Follow-up risk:** `VERY_HIGH` — cross-system integration/release stabilization.

---

## 13. Сводная оценка риска suffix-подзач

| Goal | Риск follow-up | Краткое обоснование |
|---:|---|---|
| 001 | LOW | read-only audit |
| 002 | VERY_HIGH | DB provisioning/lock; 002A уже потребовалась |
| 003 | LOW | inert local skeleton |
| 004 | VERY_HIGH | session/identity/cleanup; 004A требуется |
| 005 | HIGH | schema/versioning/concurrent updates |
| 006 | VERY_HIGH | Player/World/login/restart lifecycle |
| 007 | HIGH | fairness, transitions, overload |
| 008 | HIGH | scoring/executor/cancellation |
| 009 | HIGH | geodata variance and CPU budget |
| 010 | HIGH | topology/perceptibility correctness |
| 011 | HIGH | heterogeneous authoritative data sources |
| 012 | VERY_HIGH | combat/skills/death/World timing |
| 013 | HIGH | broad class/progression matrix |
| 014 | HIGH | canonical commerce and partial actions |
| 015 | VERY_HIGH | probabilistic causality/reconciliation/anti-dup |
| 016 | HIGH | mass state, schedules and DB shaping |
| 017 | HIGH | party concurrency and routes |
| 018 | MEDIUM | bounded model/persistence tuning |
| 019 | HIGH | Russian slang and entity ambiguity |
| 020 | HIGH | confidence/action safety |
| 021 | HIGH | multi-source acquisition parity |
| 022 | VERY_HIGH | transactions, deadlocks and anti-dup |
| 023 | HIGH | Rift/instance composition edges |
| 024 | HIGH | goals/topology/memory/dialogue convergence |
| 025 | VERY_HIGH | PvP/PK consequences and safety |
| 026 | VERY_HIGH | large-group orchestration |
| 027 | VERY_HIGH | clan/persistence/economy/war interactions |
| 028 | HIGH | cross-system controls and observability |
| 029 | VERY_HIGH | long soak and overload behavior |
| 030 | VERY_HIGH | full integration/release stabilization |

Высокий риск не означает предварительное дробление. Перед GOAL задаётся один
максимально полезный coherent scope; suffix-задача создаётся только по факту
конкретного finding.

---

## 14. Обязательный контракт task package

Каждая GOAL обязана определить до кода:

1. пользовательский результат;
2. accepted baseline и dependency gates;
3. current-code audit;
4. точные input/output contracts;
5. exact scope и hard out of scope;
6. lifecycle/concurrency ownership;
7. DB/transaction/conservation policy;
8. memory/performance budgets;
9. disabled behavior;
10. failure/rollback model;
11. proportional automated tests and negative controls;
12. acceptance criteria;
13. report/commit/push contract;
14. stop-rule и roadmap reconsideration condition.

Codex не получает формулировку «выбери архитектуру». Task package заранее
фиксирует responsibility boundaries и оставляет Codex только локальные имена и
реализационные детали, подтверждаемые current code.

---

## 15. Сквозные acceptance invariants

### Lifecycle

- one persistent character — one owner;
- one object ID — one World actor;
- real login and Phantom do not overlap;
- admission closes before cleanup;
- cleanup retryable/idempotent;
- release occurs only after postconditions;
- no retained World/online/autosave/task/action residue.

### Concurrency

- shared bounded scheduler;
- no per-phantom executor/thread;
- stable lock order;
- bounded waits/timeouts;
- stale token cannot release current owner;
- overload degrades rather than grows without bound.

### Persistence/economy

- production DB never used for experiments;
- versioned/idempotent migrations;
- optimistic conflict policy;
- item/adena conservation;
- reservation before multi-step transfer;
- restart reconciliation;
- no free background generation.

### Language/decision safety

- low confidence cannot trigger dangerous action;
- language does not own game rules;
- server IDs and Game Knowledge ground entities;
- internal Phantom communication remains typed;
- verbalization follows a structured decision.

### Diagnostics

- bounded counters/traces;
- no packet/decision/path log spam;
- selected-entity trace only;
- slow-operation thresholds;
- deterministic replay where required.

---

## 16. Definition of Done

Первая законченная система считается достигнутой, когда доказано:

1. canonical Player works without TCP;
2. real-login ownership is race-safe;
3. profiles are persistent/restart-safe;
4. materialization is idempotent/retryable;
5. scheduler scales without per-profile tasks;
6. relevance includes perceptible topology;
7. background simulation is causal/anti-dup;
8. goals and Utility AI are explainable;
9. class capabilities are extensible;
10. Semantic Pack understands Russian L2 slang;
11. entities map to real server IDs;
12. Game Knowledge uses authoritative server data;
13. party/Rift/raid decisions use real composition;
14. trade/craft/economy conserve items/adena;
15. personality/memory affect decisions;
16. conversation dispatch is confidence-safe;
17. long soak has bounded memory/queues/DB rate;
18. operators can inspect/drain/disable/recover;
19. disabled behavior matches original server;
20. end-to-end alpha scenarios pass.

---

## 17. Progress format after every review

```text
Phantom World Progress

Current stage:
Current accepted baseline:
Current branch HEAD under review:

Completed:
- ...

In progress / required closure:
- ...

Next:
1. ...
2. ...
3. ...

Stage gate:
- ...

New risks:
- ...

Roadmap changes:
- none / exact evidence-backed change

Overall:
- on track / delayed / architecture reconsideration required
```

### Current progress

```text
Current stage:
III. Solo gameplay, progression and causal background

Current accepted baseline:
bbd29495a19a322c0629509c85c31fe508ae8d07

Current branch HEAD under review:
Goal 026D: ACCEPT

Completed:
- 001 / 001A
- 002 / 002A
- 003
- 004
- 004A after 004B
- 004B
- ADR 0001 Accepted
- Goal 005
- Goal 006A
- Goal 006B
- Goal 006 overall
- Stage I COMPLETE
- Goal 007 after Goal 007A
- Goal 007A
- Goal 008 after Goal 008A
- Goal 008A
- Goal 009 after Goal 009A
- Goal 009A
- Goal 010 after Goal 010A/010B/010C
- Goal 010A
- Goal 010B
- Goal 010C
- Goal 011 after Goal 011A
- Goal 011A
- Stage II COMPLETE
- Goal 012 after Goal 012A
- Goal 012A

Accepted corrective truth:
- Goal 013/013A ACCEPT after Goal 013B
- Goal 013B ACCEPT_WITH_ACTIVATION_GATE
- Goal 014 ACCEPT after Goal 014A
- Goal 014A + completion ACCEPT

Next:
1. Goal 015 anchor-tolerance completion принята независимым ревью.
2. Goal 016 — `ACCEPT_WITH_EXPLICIT_FUTURE_CONTRACTS`; Goal 017 —
   `IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`; Goal 018 — `ACCEPT`; Goal 019 —
   `ACCEPT_WITH_EXPLICIT_FUTURE_CONTRACTS`; Goal 020 Checkpoint 1 —
   `ACCEPT_WITH_ACTIVATION_GATE`; Checkpoint 2 —
   `ACCEPT`; Goal 021 — `ACCEPT`; Goal 022 Checkpoint 1 —
   `IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`; Goal 022 Checkpoint 2 —
   `NOT_STARTED`; Goal 023 — `ACCEPT`; Goal 024 — `ACCEPT`;
   Goal 024A — `ACCEPT`; Goal 025A и Goal 025 overall — `ACCEPT`; accepted baseline — `5517081fb2bbf2aa9ad8295130714df2d4b45921`.
   Goal 026 overall — `ACCEPT` на required parent `ff631e5f71a43da6e771c3541ee59ee15ea916b3`. Goal 027 Checkpoint 1, Goal 027A/027B/027C/027D/027E/027F и Checkpoint 2 — `ACCEPT`; Goal 027 overall — `ACCEPT`. Goal 028 Checkpoint 1 — `ACCEPT`; Goal 028 Checkpoint 2 — `ACCEPT`; Goal 028 Checkpoint 3 — `ACCEPT`; Goal 028 Checkpoint 4 — `ACCEPT`; Goal 028 Checkpoint 5 — `ACCEPT after Goal 028C`; Goal 028C — `ACCEPT`; Goal 028 overall — `ACCEPT`. Goal 029 Checkpoint 1 — `ACCEPT after Goal 029A/Goal 029B`; Goal 029A — `ACCEPT after Goal 029B`; Goal 029B — `ACCEPT`; Goal 029 Checkpoint 2 — `IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`; Goal 029 overall — `IN_PROGRESS`.

Stage gate:
- Stage I COMPLETE
- Stage II COMPLETE

New risks:
- Goal 015 activation остаётся выключена глобальным feature flag.
- Goal 005 test-only ThreadPool baseline stabilization remains regression-covered.

Roadmap changes:
- future-dependent fields removed from Goal 005
- abstract contracts break 007/010 and 008/013 cycles
- navigation benchmark and baseline service merged
- Game Knowledge moved before combat/background/Semantic Pack
- party routes moved to Goal 017
- enchant moved to economic Goal 022
- Goal 015 limited to already supported acquisition methods
- operations moved before scale soak

Overall:
- Goal 006 overall ACCEPT; Goal 006A ACCEPT; Goal 006B ACCEPT;
  Stage I COMPLETE;
  Goal 007 ACCEPT after Goal 007A;
  Goal 007A ACCEPT;
  Goal 008 ACCEPT after Goal 008A;
  Goal 008A ACCEPT;
  Goal 009 ACCEPT after Goal 009A;
  Goal 009A ACCEPT;
  Goal 010 ACCEPT after Goal 010A/010B/010C;
  Goal 010A ACCEPT;
  Goal 010B ACCEPT_WITH_010C_INTEGRATION_BOUNDARY;
  Goal 010C ACCEPT;
  Goal 011 ACCEPT after Goal 011A;
  Goal 011A ACCEPT;
  Stage II COMPLETE;
  Goal 012 ACCEPT after Goal 012A;
  Goal 012A ACCEPT;
  Goal 013/013A ACCEPT after Goal 013B;
  Goal 013B ACCEPT_WITH_ACTIVATION_GATE;
  Goal 014 ACCEPT after Goal 014A;
  Goal 014A + completion ACCEPT;
  Goal 015 ACCEPT;
  Goal 016 ACCEPT_WITH_EXPLICIT_FUTURE_CONTRACTS;
  Goal 017 IMPLEMENTED_PENDING_INDEPENDENT_REVIEW;
  Goal 018 ACCEPT;
  Goal 019 ACCEPT_WITH_EXPLICIT_FUTURE_CONTRACTS;
  Goal 020 Checkpoint 1 ACCEPT_WITH_ACTIVATION_GATE;
  Goal 020 Checkpoint 2 ACCEPT;
  Goal 021 ACCEPT;
  Goal 022 Checkpoint 1 IMPLEMENTED_PENDING_INDEPENDENT_REVIEW;
  Goal 022 Checkpoint 2 NOT_STARTED;
  Goal 023 ACCEPT;
  Goal 024 ACCEPT;
  Goal 024A ACCEPT;
  Goal 025A ACCEPT;
  Goal 025 overall ACCEPT;
  Goal 026 overall — `ACCEPT` на required parent `ff631e5f71a43da6e771c3541ee59ee15ea916b3`. Goal 027 Checkpoint 1, Goal 027A/027B/027C/027D/027E/027F и Checkpoint 2 — `ACCEPT`; Goal 027 overall — `ACCEPT`. Goal 028 Checkpoint 1 — `ACCEPT`; Goal 028 Checkpoint 2 — `ACCEPT`; Goal 028 Checkpoint 3 — `ACCEPT`; Goal 028 Checkpoint 4 — `ACCEPT`; Goal 028 Checkpoint 5 — `ACCEPT after Goal 028C`; Goal 028C — `ACCEPT`; Goal 028 overall — `ACCEPT`. Goal 029 Checkpoint 1 — `ACCEPT after Goal 029A/Goal 029B`; Goal 029A — `ACCEPT after Goal 029B`; Goal 029B — `ACCEPT`; Goal 029 Checkpoint 2 — `IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`; Goal 029 overall — `IN_PROGRESS`.
  Goal 026 Checkpoint 5: ACCEPT
  Goal 026D: ACCEPT
  Goal 026 overall: ACCEPT
  Goal 027 Checkpoint 1 ACCEPTED
  Goal 027A ACCEPTED
  Goal 027B ACCEPTED
  Goal 027C ACCEPT
  Goal 027 overall ACCEPT
  Goal 027 Checkpoint 2 ACCEPT
  Goal 027F ACCEPT
  Goal 028 Checkpoint 1 ACCEPT
  Goal 028 Checkpoint 2 ACCEPT
  Goal 028 Checkpoint 3 ACCEPT
  Goal 028 Checkpoint 4 ACCEPT
  Goal 028 Checkpoint 5 ACCEPT after Goal 028C
  Goal 028C ACCEPT
  Goal 028 overall ACCEPT
  Goal 029 Checkpoint 1 ACCEPT after Goal 029A/Goal 029B
  Goal 029A ACCEPT after Goal 029B
  Goal 029B ACCEPT
  Goal 029 Checkpoint 2 IMPLEMENTED_PENDING_INDEPENDENT_REVIEW
  Goal 029 overall IN_PROGRESS

```

---

## 18. Изменения относительно версии 1

Приняты только правки с явной выгодой выше стоимости:

1. Goal 005 больше не изобретает personality/schedule/goal models из будущего.
2. Goal 007 принимает abstract relevance signals; Goal 010 реализует topology.
3. Goal 008 определяет generic capability keys; Goal 013 — реальный class catalog.
4. Старые navigation audit/service объединены в одну полезную Goal 009.
5. Game Knowledge выделен в Goal 011 до combat/background/Semantic Pack.
6. Party routes перенесены из topology в Goal 017.
7. Enchant перенесён из progression в transactional economy Goal 022.
8. Goal 015 не использует spoil/manor/quest/craft до Goal 021.
9. Goal 017 не содержит premature clan hooks; Goal 025 — formal alliances.
10. Operations/observability выполняются до scale/soak.
11. Добавлены explicit DAG, per-goal boundaries и follow-up risk matrix.
12. Обновлён фактический статус Task 004/004A.

Количество основных GOAL остаётся 30, пользовательские возможности не урезаны.

---

## 19. Правило изменения roadmap

Roadmap меняется только по evidence:

- P0/P1 независимого ревью;
- несовместимый current code;
- доказанный performance blocker;
- отсутствующий High Five subsystem;
- новый пользовательский product requirement;
- необходимость изменить dependency order для safety.

Не являются основанием удобство Codex, желание показать NPC-демо или стремление
формально удержать номер задачи.
