# TASK 001 — Baseline и полный архитектурный аудит Phantom World

## 1. Идентификатор

- **Task ID:** `001-baseline-architecture-audit`
- **Этап master plan:** `001. Baseline и полный аудит`
- **Целевая ветка:** `feature/phantom-world`
- **Репозиторий:** `https://github.com/kpCat/L2J`
- **Git-корень:** `C:\Users\endim\L2J_Mobius\`
- **Единственный рабочий модуль:** `C:\Users\endim\L2J_Mobius\L2J_Mobius_CT_2.6_HighFive\`
- **Каталог запуска Codex:** рабочий модуль High Five
- **Review snapshot:** `origin/master` на момент подготовки задания указывал на `16d61833b3983a3976583d0e4813e0de9457a52f`
- **Детерминированный audit seed:** `20260725001`
- **Production DB:** `l2jmobiush5` — запрещено изменять или использовать в проверках
- **Зарезервированная test DB:** `l2jmobiush5_phantom_test` — в задаче 001 только зафиксировать контракт; не подключаться и не изменять

## 2. Цель

Создать воспроизводимый baseline проекта Phantom World и выполнить полный доказательный архитектурный аудит существующего High Five-кода без изменения production-поведения.

Задача должна дать однозначный ответ на главный gate этапа 001:

> Возможно ли безопасно материализовать и эксплуатировать полноценный штатный `Player` без реального TCP-клиента, не форкая значительную часть `Player`, не подменяя игровые правила и не создавая отдельный поток/таймер на каждого фантома?

Итоговый ответ обязан иметь один из статусов:

- `FEASIBLE`;
- `FEASIBLE_WITH_SEAM`;
- `NOT_FEASIBLE_WITHOUT_PLAN_CHANGE`.

Ответ должен опираться на конкретные классы, методы, call paths, lifecycle и риски актуального кода, а не на предположение.

## 3. Зависимости и источники требований

Перед любыми действиями прочитать полностью:

1. `PHANTOM_DEVELOPMENT_MASTER_PLAN.md`;
2. `docs/phantoms/CODEX_WORKFLOW_CONTRACT.md`;
3. `docs/phantoms/TASK_PACKAGE_STANDARD.md`;
4. `docs/phantoms/CODEX_REPORT_TEMPLATE.md`;
5. этот `TASK.md`;
6. `CONTEXT.md`;
7. `ARCHITECTURE_CONSTRAINTS.md`;
8. `ACCEPTANCE.md`.

Предыдущей задачи и предыдущего отчёта нет: это Task 001.

Master plan имеет приоритет над любыми гипотезами из вспомогательных файлов. При расхождении актуального кода с review snapshot использовать актуальный `origin/master`, зафиксировать drift и повторно проверить все затронутые выводы.

## 4. Безопасная подготовка Git и baseline

### 4.1. Зафиксировать исходное состояние

Из рабочего модуля определить Git-корень и выполнить:

```bat
git rev-parse --show-toplevel
git status --short --branch
git remote -v
git fetch origin --prune
git rev-parse HEAD
git rev-parse origin/master
git branch --all --list "*feature/phantom-world*"
git log -1 --format=fuller origin/master
```

Сохранить исходный `git status` в audit-материалах.

Файлы распакованного task package в
`docs/phantoms/tasks/001-baseline-architecture-audit/`
считаются ожидаемыми новыми файлами. Любые иные локальные изменения считаются pre-existing user work.

### 4.2. Правила ветки

1. Если `feature/phantom-world` не существует локально и на `origin`, создать её от актуального `origin/master`.
2. Если ветка существует, проверить её upstream, HEAD, историю и отсутствие расхождения с ожидаемым workflow.
3. Не использовать `reset --hard`, `clean`, force checkout, force push или удаление чужих файлов.
4. Не stash-ить и не откатывать pre-existing user work.
5. В staging добавлять только явно разрешённые Task 001 paths.
6. Если `origin/master` отличается от review snapshot `16d618...`, это не автоматическая блокировка: зафиксировать новый SHA, изучить diff/drift и использовать актуальный код. При конфликте требований — `BLOCKED`.
7. Целевая ветка после задачи обязана быть запушена как `origin/feature/phantom-world`.

## 5. Обязательный предварительный аудит

Нельзя ограничиваться поиском по именам. Для каждого seam проследить создание, runtime-использование, ошибку, shutdown/restart и persistence.

### 5.1. Built-in Fake Players

Аудировать:

- `org.l2jmobius.gameserver.config.custom.FakePlayersConfig`;
- `org.l2jmobius.gameserver.data.xml.FakePlayerData`;
- `org.l2jmobius.gameserver.managers.FakePlayerChatManager`;
- разбор `<fakePlayer>` в `org.l2jmobius.gameserver.data.xml.NpcData`;
- связанные NPC template/spawn/AI/broadcast/chat paths;
- datapack-файлы Fake Player;
- startup registration в `GameServer`.

Зафиксировать:

- что является реальным runtime-объектом;
- какие механики лишь имитируются внешним видом или пакетами;
- какие механики `Player` отсутствуют;
- какие части можно переиспользовать только как данные/fixtures/визуальные идеи;
- какие части нельзя использовать как доменное ядро Phantom World;
- существующие случайные задержки и scheduled tasks;
- текущую зависимость от английских шаблонов/substring matching;
- отсутствие либо наличие persistence, inventory, real trade, party, clan, quest, mail, siege.

### 5.2. `Player`: создание, загрузка и состояние

Аудировать минимум:

- конструкторы `Player`;
- `Player.create(...)`;
- `Player.load(...)`;
- `createDb`, `restore`, `storeMe`, `deleteMe`;
- `setClient`, `getClient`;
- `sendPacket` и все перегрузки;
- `setOnlineStatus`, `isOnline`, `isInOfflineMode`, `setOfflinePlay`;
- `spawnMe`, `decayMe`, world registration/unregistration;
- `setEnteredWorld` и связанные поля;
- `getAI`, startup tasks, `stopAllTasks`;
- inventory, skills, shortcuts, quests, variables, clan/party references;
- autobroadcast/update tasks;
- null-client branches и прямые `getClient().…` dereference;
- участки, где `ServerPacket.runImpl(Player)` содержит серверный side effect.

Сделать инвентаризацию client-coupling:

| Категория | Обязательный результат |
|---|---|
| Безопасно при `client == null` | конкретные методы и доказательства |
| Требует outbound packet only | методы, где пакет можно отбросить |
| Требует `ServerPacket.runImpl` | методы, где отбрасывание пакета изменит gameplay |
| Требует реальный `GameClient` | конкретная причина |
| Неясно/опасно | риск и тест, который снимет неопределённость |

### 5.3. `GameClient` и network boundary

Аудировать:

- конструктор и требование `Connection<GameClient>`;
- connection state;
- player association;
- `sendPacket`, `writePacket`, `packet.runImpl`;
- `close`, `disconnect`, `onDisconnection`;
- account/session/LoginServer interactions;
- flood protectors;
- client packet handlers, которые содержат доменную логику;
- server packets с `runImpl(Player)`;
- возможность recording/no-op packet sink без потери side effects;
- почему наследование от `GameClient` с fake/null connection безопасно либо небезопасно.

Отдельно составить список client packet handlers, чью доменную логику Phantom World позднее должен вызывать через server-side facade, а не конструировать сетевые пакеты как внутренний API.

### 5.4. Enter/leave lifecycle

Аудировать:

- character select/load;
- `EnterWorld`;
- `Disconnection`;
- logout/delete/store;
- restart restoration;
- offline play restoration;
- offline trader restoration;
- world visibility/broadcast/known-list behavior;
- cleanup party, clan, summons, instances, quests, trades, requests, task managers;
- ошибки в середине materialization и частичный rollback.

Итог: точная state machine существующего игрока и предлагаемая минимальная state machine headless materialization.

### 5.5. Offline play и offline trade как доказательство возможностей

Аудировать минимум:

- `OfflinePlayTable`;
- `OfflineTraderTable`;
- `OfflinePlayConfig`;
- `OfflineTradeConfig`;
- места их запуска при startup/shutdown;
- таблицы `character_offline_play*`, `character_offline_trade*`;
- восстановление `Player.load → online status → spawn → effects/tasks`;
- cleanup при исключении;
- party restore;
- private store lists;
- null/detached client branches;
- транзакционные и anti-dup риски.

Не считать offline play готовым Phantom World. Использовать его как доказательство существующих lifecycle seams и как источник рисков.

### 5.6. Игровые подсистемы

Для каждой подсистемы сделать dependency map:

1. party / command channel;
2. clan / alliance / clan war;
3. direct trade;
4. private buy/sell/manufacture store;
5. NPC buy/sell/multisell;
6. inventory/item transfer/reservation;
7. mail;
8. quest / `QuestState` / timers;
9. instance;
10. PvP/PK/karma/drop;
11. death/resurrection;
12. siege/fort/territory war;
13. raid/epic participation;
14. chat/PM/trade chat;
15. skills/shot/autouse/autoplay;
16. teleport/navigation/geodata;
17. scheduled task managers and global ThreadPool.

Для каждой подсистемы указать:

- canonical domain objects;
- публичные server-side методы;
- client packet handlers с доменной логикой;
- обязательность `GameClient`;
- persistence tables/repositories;
- transaction boundary;
- cleanup/restart behavior;
- concurrency model;
- безопасный future seam (`PhantomActionFacade` либо прямой штатный API);
- тестовый gate будущей задачи;
- степень готовности: `REUSE_DIRECT`, `REUSE_WITH_ADAPTER`, `NEEDS_SERVER_FACADE`, `UNSAFE/UNKNOWN`.

### 5.7. Build и тестируемость

Аудировать:

- `build.xml`;
- Ant targets и classpath;
- JDK 25 enforcement;
- фактический `ant jar`;
- существующие тестовые каталоги/фреймворки/зависимости;
- доступные patterns для headless/static/integration tests;
- DB config loading;
- возможность передать test DB без изменения production config;
- текущие CI/workflows, если имеются;
- воспроизводимость на Windows.

Зафиксировать, что Task 002 должна добавить, но не реализовывать это в Task 001:

- `test`;
- `verify`;
- `phantom-scenario-test`;
- `phantom-performance-smoke`;
- отдельную конфигурацию `l2jmobiush5_phantom_test`;
- отрицательный контрольный тест;
- deterministic seed injection.

## 6. Заранее заданное архитектурное направление

Task 001 не реализует архитектуру, но обязан проверить следующий целевой контракт и либо подтвердить его, либо доказательно отклонить.

### 6.1. Предпочтительный контракт

1. В мире материализуется штатный `Player`, а не NPC-копия.
2. Phantom-код не наследует и не форкает половину `Player`.
3. Реальный `GameClient` не подделывается через fake/null network connection.
4. Outbound transport отделяется от обязательных server-side packet effects.
5. Реальный клиент использует adapter к текущему network transport.
6. Headless player использует no-op/recording sink с bounded diagnostics.
7. Доменные действия вызываются через узкий `PhantomActionFacade`, использующий штатные server-side механики; client packets не становятся внутренним Phantom API.
8. Materialization/dematerialization принадлежит отдельному lifecycle service и должна быть идемпотентной.
9. Scheduler общий; отдельный поток и бесконтрольный scheduled task на каждого Phantom запрещены.
10. Любая новая система по умолчанию disabled и fail-closed, но runtime feature flag будет реализован только в Task 003.

### 6.2. Обязательные альтернативы для оценки

Оценить и сравнить:

- **A. Fake `GameClient` / fake network `Connection`;**
- **B. Повсеместное разрешение `Player.client == null`;**
- **C. Малый packet/output/session seam при сохранении штатного `Player`;**
- **D. `PhantomPlayer extends Player`;**
- **E. Fork/копия `Player`;**
- **F. Продолжение NPC-based Fake Players как конечного ядра.**

Для каждой альтернативы дать:

- изменения и blast radius;
- lifecycle;
- packet side effects;
- совместимость party/clan/trade/quest/mail/siege;
- риски NPE;
- тестируемость;
- производительность;
- rollback;
- решение `ACCEPT / REJECT / SPIKE_ONLY`.

Нельзя выбрать вариант только по удобству реализации. Решение должно минимизировать долгосрочный fork и сохранять штатные игровые правила.

## 7. Scope

Разрешено создавать/изменять только:

```text
docs/phantoms/tasks/001-baseline-architecture-audit/**
docs/phantoms/audits/001-baseline-architecture-audit/**
docs/phantoms/adr/0001-headless-player-integration-seam.md
docs/phantoms/reports/001-baseline-architecture-audit.md
tools/phantoms/verify-task-001.ps1
```

Разрешены только документация, machine-readable baseline manifest и статический verifier Task 001.

## 8. Out of scope и жёсткие запреты

Запрещено:

- изменять любой production `.java`;
- изменять `build.xml`;
- изменять runtime config;
- создавать `PhantomPlayers.ini`;
- изменять datapack XML/HTML/CSV/JSON;
- изменять SQL schema/data;
- подключаться к MariaDB;
- читать/изменять данные production DB;
- создавать test DB в этой задаче;
- изменять зависимости/JAR;
- добавлять тестовый framework;
- исправлять найденные production-баги;
- менять Fake Player runtime;
- добавлять Phantom classes;
- добавлять packet sink production implementation;
- менять startup/shutdown;
- менять другие хроники;
- массово форматировать;
- коммитить generated JAR/build output/logs;
- добавлять постоянное high-frequency logging;
- использовать force push.

Найденные дефекты оформить как риски и prerequisites следующих задач. Production fixes возможны только отдельной задачей после ревью.

## 9. Требуемые артефакты

Создать:

### 9.1. Baseline manifest

`docs/phantoms/audits/001-baseline-architecture-audit/BASELINE_MANIFEST.json`

Минимальная схема:

```json
{
  "schemaVersion": 1,
  "taskId": "001-baseline-architecture-audit",
  "auditSeed": 20260725001,
  "repository": {
    "remote": "origin",
    "defaultBranch": "master",
    "reviewSnapshot": "16d61833b3983a3976583d0e4813e0de9457a52f",
    "originMasterAtTaskStart": "<sha>",
    "headAtTaskStart": "<sha>",
    "workBranch": "feature/phantom-world"
  },
  "environment": {
    "javaVersion": "<text>",
    "antVersion": "<text>",
    "os": "<text>",
    "geodataPresent": "<true|false|unknown>",
    "pathfindingConfigured": "<enabled|disabled|unknown>"
  },
  "databaseContract": {
    "productionDatabase": "l2jmobiush5",
    "testDatabase": "l2jmobiush5_phantom_test",
    "databaseConnectionPerformed": false,
    "databaseMutationPerformed": false
  },
  "build": {
    "targets": ["<stable sorted list>"],
    "jarExitCode": 0,
    "gameServerJarCopied": "<true|false|unknown>",
    "loginServerJarCopied": "<true|false|unknown>"
  },
  "gateVerdict": "<FEASIBLE|FEASIBLE_WITH_SEAM|NOT_FEASIBLE_WITHOUT_PLAN_CHANGE>"
}
```

Требования:

- valid UTF-8 JSON;
- deterministic field ordering;
- stable sorted arrays;
- никаких паролей, токенов, приватных IP кроме уже заданного localhost-контракта;
- фактические значения, не placeholders.

### 9.2. Baseline report

`docs/phantoms/audits/001-baseline-architecture-audit/BASELINE.md`

Содержит:

- Git baseline и drift;
- environment;
- build;
- geodata/pathfinding observed state;
- существующие configs;
- отсутствие/наличие test infrastructure;
- DB isolation contract;
- hashes/SHA основных audited files;
- известные ограничения достоверности.

### 9.3. Current system audit

`docs/phantoms/audits/001-baseline-architecture-audit/CURRENT_SYSTEM_AUDIT.md`

Содержит полный аудит разделов 5.1–5.7 с доказательствами `path + symbol/method + commit SHA`, при необходимости line range.

### 9.4. Dependency map

`docs/phantoms/audits/001-baseline-architecture-audit/DEPENDENCY_MAP.md`

Содержит:

- диаграммы Mermaid или текстовые графы;
- creation/load/enter/action/output/store/leave/restart flows;
- таблицу подсистем из 5.6;
- client-coupling matrix;
- persistence/transaction map;
- thread/task ownership map;
- failure/rollback points.

Диаграмма не заменяет текстовые доказательства.

### 9.5. Feasibility decision

`docs/phantoms/audits/001-baseline-architecture-audit/HEADLESS_PLAYER_FEASIBILITY.md`

Содержит:

- один итоговый gate verdict;
- минимальный seam;
- точные production files/methods, которые, вероятно, будут затронуты Task 004;
- список invariants;
- варианты A–F и решения;
- unresolved questions;
- автоматические tests/spike, необходимые Task 004;
- rollback;
- условие официального пересмотра master plan.

### 9.6. ADR

`docs/phantoms/adr/0001-headless-player-integration-seam.md`

Структура:

- Status: `Proposed`;
- Context;
- Decision;
- Invariants;
- Alternatives;
- Consequences;
- Risks;
- Validation plan;
- Rollback;
- Supersession condition.

ADR остаётся `Proposed`, потому что Task 001 — аудит, а Task 004 — feasibility spike.

### 9.7. Roadmap gates

`docs/phantoms/audits/001-baseline-architecture-audit/NEXT_TASK_GATES.md`

Точные prerequisites и acceptance gates минимум для:

- Task 002 test infrastructure;
- Task 003 skeleton/config/metrics;
- Task 004 headless Player spike;
- отдельно список рисков, которые нельзя откладывать дальше Task 004.

### 9.8. Автоматический verifier

`tools/phantoms/verify-task-001.ps1`

Verifier обязан:

1. работать из любого текущего каталога внутри repo;
2. найти Git root и High Five module;
3. принимать параметры либо использовать defaults:
   - branch `feature/phantom-world`;
   - seed `20260725001`;
   - test DB `l2jmobiush5_phantom_test`;
4. не подключаться к БД и сети;
5. валидировать required artifacts;
6. парсить JSON manifest;
7. проверять точные `taskId`, seed, DB names, `databaseConnectionPerformed=false`, `databaseMutationPerformed=false`;
8. проверять допустимый gate verdict;
9. проверять наличие обязательных headings/таблиц;
10. проверять, что diff Task 001 не содержит:
    - production `.java`;
    - `build.xml`;
    - runtime `dist/game/config/**`;
    - datapack runtime data;
    - SQL;
    - другие хроники;
    - binary/build/log artifacts;
11. выводить детерминированный stable-sorted список PASS/FAIL;
12. возвращать exit code `0` только при полном успехе;
13. не скрывать исключения;
14. не модифицировать репозиторий.

Verifier должен корректно учитывать, что сравнение до первого commit на новой ветке выполняется по working tree/index против `origin/master`, а после commit — по `origin/master...HEAD`.

## 10. Конфиги и feature flags

Production config в Task 001 не изменяется.

В аудите необходимо:

- перечислить существующие Fake Player/offline play/offline trade flags;
- определить будущий canonical config path:
  `dist\game\config\Custom\PhantomPlayers.ini`;
- зафиксировать будущий startup flag:
  `EnablePhantomSystem=false`;
- определить, где Task 003 безопасно подключит config loader и lifecycle;
- зафиксировать fail-closed semantics;
- не создавать сам config и не вносить код.

## 11. Производительность

Production benchmark в Task 001 не выполняется.

Аудит обязан определить:

- существующие per-player scheduled tasks;
- global task managers;
- hot paths;
- packet broadcast amplification;
- DB writes during store/autosave/offline restore;
- потенциальные O(N), O(N²) paths;
- bounded metrics, которые потребуются позже;
- почему отдельный thread/task per phantom запрещён;
- минимальные performance smoke gates для Task 004 и Task 030.

Нельзя добавлять runtime logging для измерений.

## 12. Конкурентность и lifecycle

Зафиксировать:

- owners каждого task/future;
- cancellation path;
- thread-safety collections/locks;
- world registration race;
- double materialization;
- concurrent login реального владельца/phantom identity;
- partial enter;
- partial store;
- shutdown ordering;
- idempotent cleanup;
- party/trade/request timers;
- packet sink thread-safety;
- исключения и rollback.

Обязательные invariants будущей реализации:

1. один persistent profile/character не материализован дважды;
2. один objectId не зарегистрирован в World дважды;
3. cleanup допускает повторный вызов;
4. после failed materialization нет world object, tasks, party/trade reservation или online flag;
5. shutdown не создаёт новые decisions/actions;
6. нет per-phantom executor;
7. outbound packet sink не выполняет network I/O для headless player;
8. обязательные `ServerPacket.runImpl(Player)` side effects не теряются.

## 13. БД и транзакции

В Task 001 любые DB connections запрещены.

Аудитировать статически:

- character create/load/store tables;
- offline play/trade tables;
- inventory/items;
- skills/quests/variables;
- party persistence, если имеется;
- clan/alliance/war;
- mail;
- trade/store;
- siege registration;
- instance state;
- transaction/autocommit usage;
- anti-dup/partial failure risks.

В `NEXT_TASK_GATES.md` описать:

- создание `l2jmobiush5_phantom_test` только в Task 002;
- отдельные test credentials/config, не совпадающие с production;
- fail-fast guard против `l2jmobiush5`;
- migration ownership;
- rollback/cleanup;
- DB fixture strategy;
- запрет тестов на production DB.

Никакие credentials не коммитить.

## 14. Автоматические проверки

Обязательны:

1. `ant jar`;
2. `tools/phantoms/verify-task-001.ps1`;
3. `git diff --check`;
4. scope check;
5. required artifact/JSON validation;
6. проверка отсутствия DB access/mutation;
7. проверка отсутствия production changes;
8. проверка отсутствия изменений других хроник;
9. проверка чистого staging перед commit;
10. проверка clean status после commit, кроме заранее зафиксированного pre-existing user work.

Детерминизм:

- seed `20260725001`;
- verifier не использует случайность;
- списки сортируются ordinal/stable;
- timestamps не используются как доказательство идентичности;
- повторный запуск verifier на одном commit должен давать одинаковый PASS/FAIL result.

## 15. Команды проверки

Минимум выполнить и привести реальные exit codes/output summary:

```bat
java -version
ant -version
ant -p
ant jar
powershell -NoProfile -ExecutionPolicy Bypass -File tools\phantoms\verify-task-001.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File tools\phantoms\verify-task-001.ps1
git diff --check
git status --short --branch
git diff --name-status origin/master...HEAD
```

До commit verifier должен быть запущен против working tree/index. После commit — повторно против `origin/master...HEAD`.

Если `powershell` недоступен, допускается `pwsh`, но это отклонение явно записать. Нельзя вручную объявить verifier PASS без запуска.

## 16. Критерии приёмки

Полный список находится в `ACCEPTANCE.md`. Критические gates:

1. production behavior не изменено;
2. другие хроники не затронуты;
3. реальный baseline и drift зафиксированы;
4. `ant jar` успешно завершён либо честно доказана pre-existing baseline failure;
5. полный dependency map создан;
6. client-coupling не сводится к поиску `getClient`;
7. packet side effects отдельно исследованы;
8. offline play/trade lifecycle исследован;
9. все подсистемы 5.6 покрыты;
10. выбран один headless feasibility verdict;
11. minimal seam конкретен и ограничен;
12. альтернативы A–F оценены;
13. Task 002/003/004 gates конкретны;
14. verifier дважды PASS на финальном commit;
15. отчёт, commit и push выполнены.

## 17. Формат отчёта

Создать:

`docs/phantoms/reports/001-baseline-architecture-audit.md`

Использовать `docs/phantoms/CODEX_REPORT_TEMPLATE.md` и добавить:

- `Baseline`;
- `Gate verdict`;
- `Evidence index`;
- `Scope verification`;
- `Database safety`;
- `Determinism`;
- `Verifier runs`;
- `Review snapshot drift`;
- `Pre-existing working tree changes`;
- `Commit parent`;
- `Remote branch verification`.

Все команды и результаты честные. Нельзя писать `PASS`, если команда не запускалась или exit code неизвестен.

## 18. Commit и push

Рекомендуемое сообщение:

```text
docs(phantoms): complete task 001 baseline audit
```

Перед commit:

```bat
git diff --check
git status --short
git diff --name-only
git diff --cached --name-only
```

Добавлять в staging только разрешённые paths.

После commit:

```bat
git rev-parse HEAD
git show --stat --oneline --decorate HEAD
git diff --check origin/master...HEAD
powershell -NoProfile -ExecutionPolicy Bypass -File tools\phantoms\verify-task-001.ps1
git push -u origin feature/phantom-world
git ls-remote --heads origin feature/phantom-world
git status --short --branch
```

В отчёте указать:

- branch;
- commit SHA;
- parent SHA;
- push command;
- push result;
- remote ref SHA;
- clean/dirty status.

## 19. Поведение при блокировке

Статусы Codex: `SUCCESS`, `PARTIAL`, `BLOCKED`.

При любой блокировке:

1. не изменять production-код;
2. не сбрасывать и не удалять user work;
3. завершить безопасную часть аудита;
4. создать report с точным блокером;
5. verifier должен проверять доступные audit artifacts; недостающие обязательные gates дают честный FAIL;
6. создать безопасный commit только разрешённых docs/tooling;
7. попытаться push;
8. указать SHA и результат push;
9. не выдавать `BLOCKED` за успех.

Примеры BLOCKED:

- нельзя безопасно отделить pre-existing changes;
- branch history конфликтует с контрактом;
- актуальный master plan противоречит task package;
- `origin/master` содержит неразрешимый drift;
- baseline `ant jar` падает и причина мешает достоверному аудиту;
- недостаточно кода/данных для доказательного gate;
- push auth/remote rejection;
- обнаружена необходимость изменить master plan до feasibility spike.

## 20. Финальное сообщение Codex

Кратко:

```text
Статус:
Gate verdict:
Что сделано:
Проверки:
Commit:
Parent:
Branch:
Push:
Remote ref:
Отчёт:
Ограничения/блокеры:
```
