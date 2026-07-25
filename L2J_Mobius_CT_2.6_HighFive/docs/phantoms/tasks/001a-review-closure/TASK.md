# TASK 001A — Закрытие замечаний независимого ревью Task 001

## 1. Идентификатор

- **Task ID:** `001a-review-closure`
- **Тип:** узкая документальная follow-up-задача перед Task 002
- **Связанная задача:** `001-baseline-architecture-audit`
- **Целевая ветка:** `feature/phantom-world`
- **Git-корень:** `C:\Users\endim\L2J_Mobius\`
- **Единственный рабочий модуль:** `C:\Users\endim\L2J_Mobius\L2J_Mobius_CT_2.6_HighFive\`
- **Каталог запуска Codex:** рабочий модуль High Five
- **Исходный commit Codex Task 001:** `e7dcf575dd45a94c83560fd140144635bbf96e37`
- **Parent исходного Task 001:** `16d61833b3983a3976583d0e4813e0de9457a52f`
- **Пользовательский amended commit перед Task 001A:** `cdca7a3d96554285eb8c992fa14f65b27f7f36ae`
- **Ожидаемый remote branch при старте:** `origin/feature/phantom-world`
- **Детерминированный verifier seed:** `20260725001`

## 2. Цель

Закрыть исключительно документальные замечания независимого ревью Task 001 и создать проверяемую provenance-цепочку перед Task 002.

После выполнения должно быть однозначно зафиксировано:

1. что Codex первоначально создал и проверил commit `e7dcf575...`;
2. что пользователь самостоятельно выполнил amend и добавил `Agents.md`, в результате чего текущим commit стал `cdca7a3d...`;
3. что исходная архитектурная работа Task 001 принята содержательно;
4. какие follow-up замечания были устранены Task 001A;
5. что Task 002 ещё не начиналась;
6. что окончательное разрешение начинать Task 002 требует независимого ревью commit Task 001A.

Task 001A не пересматривает архитектуру, не реализует Phantom World и не изменяет production-поведение.

## 3. Обязательные документы

Перед изменениями полностью прочитать:

1. `PHANTOM_DEVELOPMENT_MASTER_PLAN.md`;
2. `Agents.md`;
3. `docs/phantoms/CODEX_WORKFLOW_CONTRACT.md`;
4. `docs/phantoms/TASK_PACKAGE_STANDARD.md`;
5. `docs/phantoms/CODEX_REPORT_TEMPLATE.md`;
6. `docs/phantoms/tasks/001-baseline-architecture-audit/TASK.md`;
7. `docs/phantoms/reports/001-baseline-architecture-audit.md`;
8. `docs/phantoms/audits/001-baseline-architecture-audit/BASELINE.md`;
9. `docs/phantoms/audits/001-baseline-architecture-audit/HEADLESS_PLAYER_FEASIBILITY.md`;
10. `docs/phantoms/adr/0001-headless-player-integration-seam.md`;
11. этот `TASK.md`;
12. `CONTEXT.md`;
13. `ACCEPTANCE.md`.

Master plan и принятый архитектурный verdict Task 001 менять запрещено.

## 4. Предварительная Git-проверка

Из рабочего модуля выполнить:

```bat
git rev-parse --show-toplevel
git status --short --branch
git remote -v
git fetch origin --prune
git rev-parse HEAD
git rev-parse origin/feature/phantom-world
git cat-file -t e7dcf575dd45a94c83560fd140144635bbf96e37
git cat-file -t cdca7a3d96554285eb8c992fa14f65b27f7f36ae
git show --stat --oneline e7dcf575dd45a94c83560fd140144635bbf96e37
git show --stat --oneline cdca7a3d96554285eb8c992fa14f65b27f7f36ae
git diff --name-status 16d61833b3983a3976583d0e4813e0de9457a52f..e7dcf575dd45a94c83560fd140144635bbf96e37
git diff --name-status 16d61833b3983a3976583d0e4813e0de9457a52f..cdca7a3d96554285eb8c992fa14f65b27f7f36ae
```

### Правила drift

- Нормальная стартовая точка — `HEAD` и `origin/feature/phantom-world` равны `cdca7a3d...`.
- Распакованные файлы Task 001A считаются ожидаемыми untracked files.
- Если remote HEAD изменился после подготовки пакета, провести аудит drift.
- Если уже началась Task 002, появились production-изменения, SQL, runtime config или другие несвязанные изменения — `BLOCKED`.
- Не использовать `reset --hard`, `clean`, stash, rebase, amend или force push.
- Не откатывать пользовательский `Agents.md`.
- Task 001A создаёт новый commit поверх актуального безопасного branch tip.

## 5. Факты, которые нельзя исказить

### 5.1. История Task 001

- Codex завершил Task 001 commit-ом:
  `e7dcf575dd45a94c83560fd140144635bbf96e37`.
- Parent:
  `16d61833b3983a3976583d0e4813e0de9457a52f`.
- Пользователь затем самостоятельно amend-ил commit и добавил `Agents.md`.
- После amend branch tip стал:
  `cdca7a3d96554285eb8c992fa14f65b27f7f36ae`.
- Amend не был скрытым действием Codex.
- Исходный `e7dcf575...` остаётся audit evidence, хотя не является ancestor текущего amended commit.

### 5.2. Проверки исходного Task 001

По финальному handoff Codex:

- `ant jar`: PASS;
- 1 895 Java source files compiled;
- pre-commit verifier: `43/43`, exit `0`;
- final commit verifier run 1: `43/43`, exit `0`;
- final commit verifier run 2: `43/43`, exit `0`;
- два final-run вывода идентичны;
- `git diff --check`, scope, JSON и DB safety: PASS;
- DB connection/mutation не выполнялись;
- рабочее дерево после исходной задачи было чистым.

Не писать, что эти проверки запускались на `cdca7a3d...`: они относятся к исходному результату Codex `e7dcf575...`.

### 5.3. Независимое ревью

Зафиксированный review verdict:

- содержательная работа Codex Task 001: `ACCEPT`;
- состояние ветки после пользовательского amend: `ACCEPT WITH FOLLOW-UP`;
- P0/P1 архитектурных дефектов не найдено;
- `FEASIBLE_WITH_SEAM` принят как корректный gate verdict;
- ADR 0001 остаётся `Proposed` до Task 004;
- Task 002 нельзя начинать до независимого ревью Task 001A.

Codex не имеет права сам объявить независимый review gate окончательно пройденным. После Task 001A статус должен быть:

`IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`.

## 6. Требуемые изменения

### 6.1. Уточнить `Agents.md`

Изменить только связанные с ревью формулировки.

#### A. Geodata/pathfinding

Текущая краткая формулировка не должна создавать впечатление, что config выключен.

Зафиксировать точный смысл:

- геодата отсутствует;
- на baseline Task 001 в `GeoEngine.ini` наблюдалось `PathFinding = 2`;
- без region files полноценный pathfinding фактически недоступен;
- runtime fallback без геодаты не проверялся;
- до Task 009 нельзя считать навигационный контур подтверждённым.

Не менять `PHANTOM_DEVELOPMENT_MASTER_PLAN.md`.

#### B. Headless seam

В разделе минимального seam убрать двусмысленность `headless client adapter`.

Явно зафиксировать:

- fake/null-network `GameClient` отвергнут;
- целевой вариант — небольшой outbound/session seam;
- headless packet/output sink не выполняет network I/O;
- обязательные `ServerPacket.runImpl(Player)` effects выполняются ровно один раз;
- client packet handlers не становятся Phantom internal API;
- ADR 0001 остаётся `Proposed` до Task 004.

Остальные правила `Agents.md` без необходимости не переписывать.

### 6.2. Актуализировать отчёт Task 001

Файл:

`docs/phantoms/reports/001-baseline-architecture-audit.md`

Обязательные исправления:

1. заменить placeholders:
   - `required after commit`;
   - `to be recorded after commit`;
2. записать фактические три verifier runs:
   - pre-commit `43/43`;
   - final run 1 `43/43`;
   - final run 2 `43/43`;
   - два final outputs идентичны;
3. добавить provenance:
   - original Codex commit `e7dcf575...`;
   - user-amended commit `cdca7a3d...`;
   - amend добавил `Agents.md`;
   - это действие пользователя, не Codex;
4. добавить раздел `Independent review`;
5. записать:
   - original Task 001 content verdict `ACCEPT`;
   - amended branch verdict `ACCEPT WITH FOLLOW-UP`;
   - P0/P1 не найдено;
   - follow-up переносится в Task 001A;
6. изменить recommended next step:
   - выполнить/проверить Task 001A;
   - только после её независимого `ACCEPT` готовить Task 002.

Не переписывать исходный audit verdict и не утверждать, что исходный verifier запускался на amended commit.

### 6.3. Создать review record

Создать:

`docs/phantoms/reviews/001-baseline-architecture-audit-review.md`

Обязательные разделы:

- Scope reviewed;
- Git provenance;
- Findings;
- Architectural verdict;
- Follow-ups;
- Closure implementation;
- Current gate;
- Next allowed action.

Записать:

```text
Original task content: ACCEPT
Amended branch state: ACCEPT WITH FOLLOW-UP
Task 001A closure: IMPLEMENTED_PENDING_INDEPENDENT_REVIEW
Task 002: NOT_STARTED
```

В findings отразить:

1. исходный commit Task 001 чист по scope;
2. `Agents.md` добавлен пользователем через amend;
3. placeholders post-commit verifier в отчёте;
4. неоднозначная pathfinding-формулировка;
5. неоднозначная фраза `headless client adapter`;
6. критических P0/P1 нет.

### 6.4. Создать verifier Task 001A

Создать:

`tools/phantoms/verify-task-001a.ps1`

Требования:

- работает из любого каталога внутри repo;
- находит Git root и High Five module;
- defaults:
  - branch `feature/phantom-world`;
  - base commit `cdca7a3d96554285eb8c992fa14f65b27f7f36ae`;
  - original Codex commit `e7dcf575dd45a94c83560fd140144635bbf96e37`;
  - seed `20260725001`;
- не подключается к БД и сети;
- не изменяет репозиторий;
- проверяет существование обоих commit objects;
- проверяет required artifacts;
- проверяет точные commit SHA и review states;
- проверяет отсутствие placeholders:
  - `required after commit`;
  - `to be recorded after commit`;
- проверяет три результата `43/43`;
- проверяет provenance user amend;
- проверяет `Agents.md`:
  - `PathFinding = 2`;
  - geodata absent;
  - runtime fallback not verified;
  - fake `GameClient` rejected;
  - outbound/session seam;
  - exactly-once `ServerPacket.runImpl`;
  - ADR `Proposed`;
- проверяет review record states;
- проверяет exact allowlist;
- отклоняет production `.java`, `build.xml`, runtime config/data, SQL, binary/build/log files и другие хроники;
- не меняет и не разрешает изменение:
  - master plan;
  - Task 001 verifier;
  - ADR 0001;
  - Task 001 audit artifacts;
- выдаёт stable ordinal-sorted PASS/FAIL;
- exit `0` только при полном успехе;
- повторный запуск на одном commit имеет идентичный вывод.

Verifier должен поддерживать:

- pre-commit working tree/index относительно base commit;
- post-commit `base...HEAD`;
- ожидаемые untracked файлы task package.

### 6.5. Создать отчёт Task 001A

Создать:

`docs/phantoms/reports/001a-review-closure.md`

Использовать report template и дополнительно указать:

- original Task 001 commit;
- amended starting commit;
- actual starting HEAD;
- drift;
- exact changed files;
- provenance corrections;
- Agents corrections;
- Task 001 report corrections;
- verifier runs;
- scope;
- production/DB safety;
- branch;
- parent;
- commit/push;
- current manual gate:
  `IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`;
- Task 002:
  `NOT_STARTED`.

## 7. Scope

Разрешено изменять/создавать только:

```text
Agents.md
docs/phantoms/tasks/001a-review-closure/**
docs/phantoms/reports/001-baseline-architecture-audit.md
docs/phantoms/reports/001a-review-closure.md
docs/phantoms/reviews/001-baseline-architecture-audit-review.md
tools/phantoms/verify-task-001a.ps1
```

## 8. Out of scope

Запрещено:

- изменять production `.java`;
- изменять `build.xml`;
- изменять master plan;
- изменять ADR 0001;
- изменять Task 001 audit artifacts;
- изменять `tools/phantoms/verify-task-001.ps1`;
- создавать Task 002;
- создавать test DB;
- подключаться к MariaDB;
- изменять SQL;
- изменять runtime config/datapack;
- изменять другие хроники;
- обновлять зависимости;
- скачивать библиотеки;
- запускать сервер;
- менять архитектурный verdict;
- переводить ADR в `Accepted`;
- amend/rebase/reset/force push;
- массово форматировать `Agents.md`;
- исправлять несвязанные формулировки.

## 9. Конфиги, БД и production

Task 001A не изменяет:

- feature flags;
- config;
- runtime data;
- DB schema/data;
- production code.

DB connection/mutation обязаны остаться `false`.

`ant jar` не является acceptance gate Task 001A, потому что compilation inputs запрещено изменять, а успешный baseline build уже относится к Task 001. Основные проверки Task 001A — scope, deterministic verifier, diff и Git provenance.

## 10. Автоматические проверки

Обязательны:

1. `tools/phantoms/verify-task-001a.ps1` до commit;
2. `tools/phantoms/verify-task-001a.ps1` после commit два раза;
3. идентичность двух final outputs;
4. `git diff --check`;
5. exact scope check;
6. prohibited-path check;
7. отсутствие mojibake-маркеров в изменённых файлах;
8. отсутствие escaped Cyrillic в изменённых файлах;
9. проверка UTF-8;
10. проверка remote ref после push.

Не запускать старый `verify-task-001.ps1` на amended branch и не модифицировать его contract. Его исходные результаты относятся к `e7dcf575...`.

## 11. Команды проверки

Минимум:

```bat
powershell -NoProfile -ExecutionPolicy Bypass -File tools\phantoms\verify-task-001a.ps1
git diff --check
git status --short --branch
git diff --name-status cdca7a3d96554285eb8c992fa14f65b27f7f36ae
git diff --cached --name-status
```

После commit:

```bat
git rev-parse HEAD
git show --stat --oneline --decorate HEAD
git diff --check cdca7a3d96554285eb8c992fa14f65b27f7f36ae...HEAD
powershell -NoProfile -ExecutionPolicy Bypass -File tools\phantoms\verify-task-001a.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File tools\phantoms\verify-task-001a.ps1
git push origin feature/phantom-world
git ls-remote --heads origin feature/phantom-world
git status --short --branch
```

Сохранить два final verifier outputs во временные файлы вне repo и сравнить через `Compare-Object` либо SHA-256. В отчёте указать результат, временные файлы не коммитить.

## 12. Критерии приёмки

Полный checklist в `ACCEPTANCE.md`.

Критические gates:

1. current starting commit и user amend корректно определены;
2. Task 002 не начата;
3. production/DB/config не затронуты;
4. `Agents.md` уточнён без массовой переработки;
5. fake `GameClient` явно отвергнут;
6. outbound/session seam и exactly-once effects отражены;
7. Task 001 report больше не содержит post-commit placeholders;
8. фактические `43/43` записаны с правильной provenance;
9. review record создан;
10. Codex не объявляет независимый gate пройденным;
11. новый verifier трижды PASS;
12. два final outputs идентичны;
13. commit обычный, не amend;
14. push успешен;
15. remote ref совпадает с новым commit.

## 13. Commit и push

Commit message:

```text
docs(phantoms): close task 001 review follow-ups
```

Создать новый commit поверх актуального безопасного branch tip.

Запрещены:

- `git commit --amend`;
- rebase;
- force push;
- переписывание `cdca7a3d...`.

В staging добавлять только exact allowlist Task 001A.

## 14. Поведение при блокировке

При `BLOCKED`:

- не менять production;
- не начинать Task 002;
- не переписывать историю;
- оставить безопасные docs/verifier/report;
- честно описать drift или конфликт;
- создать обычный commit разрешённых файлов, если это безопасно;
- push;
- указать SHA и remote result.

Блокеры:

- current branch содержит Task 002 или production changes;
- remote branch diverged непредсказуемо;
- `e7dcf575...` или `cdca7a3d...` недоступны;
- provenance нельзя подтвердить;
- required report facts противоречат Git;
- verifier не может быть сделан deterministic;
- push rejected.

## 15. Финальное сообщение Codex

```text
Статус:
Что исправлено:
Original Task 001 commit:
Starting amended commit:
Проверки:
Verifier pre-commit:
Verifier final run 1:
Verifier final run 2:
Final outputs identical:
Commit:
Parent:
Branch:
Push:
Remote ref:
Отчёт:
Manual gate:
Task 002:
Ограничения/блокеры:
```
