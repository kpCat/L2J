# Goal 031 — Turnkey local play readiness и финальная сверка документации

## Status

**SUCCESS**

Дата проверки: 2026-09-04.

- Branch: `feature/phantom-world`.
- Exact parent SHA: `7709dd755390b61aba574dbd09af70a74a6249c1` (`phantom(goal-030): complete cp3 release gate`).
- Commit SHA: commit, содержащий этот отчёт; его exact immutable SHA фиксируется сразу после commit и приводится в итоговой передаче (самоссылочный SHA невозможно записать внутрь того же commit).
- Push result: целевой remote `origin/feature/phantom-world`; фактический результат и exact pushed SHA приводятся в итоговой передаче после push.

## Summary

Goal031 превращает принятый Goal030 release slice в воспроизводимый локальный операторский путь: safe shipped config, отдельный консервативный preset `10/5`, read-only preflight, русскоязычный quick-start, authoritative current-status, production startup ordering regression и guarded production-composed smoke с restart/rollback. Release semantics Goal030 не расширены: матрица остаётся `20 covered / 0 pending`, а отсутствующие gameplay slices явно не объявлены готовыми.

Production DB не изменялась и не использовалась автоматическими тестами. Все DB-mutating проверки выполнялись только на guarded базе `l2jmobiush5_phantom_test`; пароль и другие credentials не выводились в лог или отчёт.

## Pre-audit findings

До изменений прочитаны `Agents.md`, `README.md`, task package Goal031, `PHANTOM_DEVELOPMENT_MASTER_PLAN.md`, `docs/phantoms/DEVELOPMENT_CHAT_HANDOFF.md`, `docs/phantoms/CONTEXT_INDEX.md`, current generator state, Goal030 release report/matrix, релевантные config/data/SQL/build/runtime/launcher-файлы и ближайшие Goal029/Goal030 test suites. Локальные аналоги: Goal030 CP1 для fail-closed config и coverage, Goal030 CP2 для full production-composed headless runtime, Goal030 CP3 для bounded drain/disable rollback, существующие `PhantomTestDatabaseGuard` и schema provisioner для изоляции test DB.

Переиспользованный паттерн: standalone deterministic suite через `PhantomTestLauncher`, guarded test-DB manifest, production-composed environment, точные operator lifecycle codes и минимальная правка существующего `GameServer` startup sequence без нового runtime-слоя.

Учитывались ограничения JDK 25/Ant, текущая High Five архитектура, Win/runtime ownership, fail-closed shipped defaults, no-provider/no-LLM runtime, отсутствие автоматических изменений рабочей БД, запрет изменений других хроник и запрет расширения release scope. Непроверенными вручную остались реальный запуск двух серверных окон и вход игровым клиентом; вместо этого выполнен production-composed headless smoke на реальных data/runtime owners.

Исходное состояние: локальный HEAD и `origin/feature/phantom-world` совпадали на exact parent. Перед commit выполнен свежий `git fetch origin feature/phantom-world`; оба SHA повторно совпали на `7709dd755390b61aba574dbd09af70a74a6249c1`, divergence отсутствует. В worktree были несвязанные пользовательские untracked task packages; они сохранены и не включаются в Goal031 commit.

Аудит установил:

- release matrix содержит ровно 20 покрытых доменов и 0 pending;
- authoritative Phantom data root содержит 19 обязательных XML/TSV packs;
- shipped `PhantomPlayers.ini` и parser согласованы на `False/0/0`;
- `DatabaseInstaller` обнаруживает все `.sql`, сортирует их case-insensitive и применяет Phantom schema в порядке `phantom_profiles.sql` -> `phantom_reservations.sql` -> `phantom_reservations_checkpoint2.sql`;
- текущему runtime нужны 6 Phantom tables, 12 named non-PK indexes и 5 foreign keys;
- локально обнаружено 203 geodata regions; отсутствие geodata поддерживается как явный `WARN/DEGRADED`, а не как скрытый успех;
- найден реальный startup defect: Phantom запускался до полной инициализации `World`, data, scripts, managers и restored offline owners. Запуск точечно перенесён после этих owners и защищён structural regression test.

## Original plan vs release scope reconciliation

| Область исходного master plan | Фактическое состояние после Goal031 | Статус |
| --- | --- | --- |
| Goal030 accepted 20-domain release slice | Матрица и CP1/CP2/CP3 подтверждают весь принятый bounded slice | `IMPLEMENTED_AND_RELEASE_COVERED` |
| Полное siege AI: registration/schedule/gathering/roles/attack/defense/retreat | В Phantom production owners/data/tests отсутствует; наличие native `SiegeManager` не является реализацией Phantom AI | `DEFERRED_NOT_IMPLEMENTED` |
| Q102/Q152 acquisition subset для уже начатых quests | Catalog/data/suite существуют и покрывают ограниченный collection flow | `IMPLEMENTED_OUTSIDE_MATRIX` |
| Generic whitelist quest adapter | Есть только bounded Q102/Q152 acquisition subset; generic start/advance/complete adapter отсутствует | `PARTIAL` |
| Class quest automation | Production implementation/evidence отсутствуют | `DEFERRED_NOT_IMPLEMENTED` |
| Kamaloka | Production implementation/evidence отсутствуют | `DEFERRED_NOT_IMPLEMENTED` |
| Pailaka | Production implementation/evidence отсутствуют | `DEFERRED_NOT_IMPLEMENTED` |
| Full original-vision release gate | Не может считаться пройденным до отдельных siege и quest/instance slices | `DEFERRED_NOT_IMPLEMENTED` |
## Changed files

Bounded exception к ориентиру 8–10 файлов обоснован самим Task031: он требует согласованно изменить runtime startup, Ant/launcher registry, три test artifacts и несколько отдельных operator/documentation artifacts. Изменены только 13 файлов внутри `L2J_Mobius_CT_2.6_HighFive`:

- `java/org/l2jmobius/gameserver/GameServer.java` — Phantom startup перенесён после полной инициализации owners и offline restore;
- `dist/game/config/Custom/PhantomPlayers.ini` — только ссылки-комментарии на quick-start и preset, значения остаются safe;
- `docs/phantoms/examples/PhantomPlayers.local-play.ini` — versioned local-play preset `10/5`;
- `test/java/org/l2jmobius/tests/phantoms/PhantomLocalPlayPreflight.java` — read-only operator preflight;
- `test/java/org/l2jmobius/tests/phantoms/PhantomLocalPlayGoal031Suite.java` — preflight/schema/installer/startup/documentation regressions;
- `test/java/org/l2jmobius/gameserver/phantoms/PhantomLocalPlayReadinessGoal031Suite.java` — production-composed local-play/restart/rollback smoke;
- `test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java` — три Goal031 suite modes;
- `build.xml` — четыре канонических Goal031 targets и deterministic seeds;
- `docs/phantoms/PHANTOM_QUICKSTART_RU.md` — пошаговый operator runbook;
- `docs/phantoms/PHANTOM_CURRENT_STATUS.md` — единая таблица фактического scope;
- `PHANTOM_DEVELOPMENT_MASTER_PLAN.md` — reconciliation принятого Goal030 с исходной full vision;
- `docs/phantoms/NEW_DIALOG_START_MESSAGE.txt` — актуальный handoff без возврата к Task001;
- `docs/phantoms/reports/031-local-play-readiness.md` — этот отчёт.

Другие хроники и несвязанные untracked task packages не изменялись.

## Quick-start UX

Точный путь пользователя:

1. Установить JDK 25 и MariaDB, проверить локальные prerequisites из `Agents.md`.
2. Для fresh DB запустить `dist\db_installer\DatabaseInstaller.vbs` и выполнить стандартную установку Login/Game DB. Для existing DB сначала сделать backup и только затем использовать `Install on Existing Database`.
3. Собрать артефакты командой `ant jar`; проверить `dist\libs\LoginServer.jar` и `dist\libs\GameServer.jar`.
4. Сделать backup `dist\game\config\Custom\PhantomPlayers.ini`.
5. Скопировать `docs\phantoms\examples\PhantomPlayers.local-play.ini` поверх runtime `PhantomPlayers.ini`.
6. Запустить `ant phantom-local-play-preflight`. Нормальный результат с применённым preset — `PASS_WITH_WARNINGS`; предупреждение напоминает о backup/restore. Любой `FAIL` блокирует запуск.
7. Запустить `dist\login\LoginServer.bat`, затем `dist\game\GameServer.bat`.
8. Войти клиентом и проверить малую популяцию. Команда `//phantom status` должна показывать `RUNNING` для runtime/Scheduler/Decision, target 10 и ACTIVE target 5; фактическое число ACTIVE может кратковременно сходиться к цели.
9. Для штатного завершения вызвать `//phantom drain`; для остановки и сохранения durable identities — `//phantom disable`.
10. Остановить GameServer, восстановить backup safe config и перезапустить. Shipped состояние должно снова быть `False/0/0`.

## Config and preset

Committed shipped config остаётся fail-closed:

- `EnablePhantomSystem=False`;
- `PhantomPopulationTarget=0`;
- `PhantomPopulationActiveTarget=0`.

Preset хранится отдельно и не применяется автоматически. Канонические значения: enabled, `population=10`, `active=5`, `MaxMaterializedPhantoms=32`, `MaxScheduledPhantomProfiles=10000`, pulse 100 ms, profiles-per-pulse 128, UTC и сохранённые accepted caps. Diagnostics в versioned preset выключены; только test process включает diagnostics для наблюдения уже существующего autonomous decision trace.

Preflight различает два допустимых runtime состояния: safe shipped config даёт `PASS`, точная копия validated preset даёт явный `WARNING` и общий `PASS_WITH_WARNINGS`. Любая третья комбинация считается `FAIL`.

## DB and schema behavior

Fresh install использует один существующий `DatabaseInstaller`: он рекурсивно собирает `.sql` и сортирует пути case-insensitive. Phantom schema применяется после базовой схемы в точном порядке:

1. `dist/db_installer/sql/game/phantom_profiles.sql`;
2. `dist/db_installer/sql/game/phantom_reservations.sql`;
3. `dist/db_installer/sql/game/phantom_reservations_checkpoint2.sql`.

Existing DB не имеет отдельного автоматического migration engine: стандартный operator route `Install on Existing Database` повторно проходит game SQL. Поэтому обязательны backup и operator review. Три Phantom SQL-файла используют idempotent `CREATE TABLE IF NOT EXISTS`/bounded ALTER checks; regression подтверждает discovery, порядок и повторное применение на чистой guarded test DB.

Readiness contract проверяет таблицы `phantom_profiles`, `phantom_profile_components`, `phantom_economy_operations`, `phantom_economy_reservations`, `phantom_economy_audit`, `phantom_economy_offers`, а также 12 точных named indexes и 5 foreign keys. Отсутствующий объект называется точно в `FAIL`.

`ant phantom-local-play-preflight` открывает только JDBC metadata connection с read-only intent и не выполняет SQL statements. DB-mutating suites сначала требуют loopback URL, отдельное имя `l2jmobiush5_phantom_test`, guard environment и matching provision manifest. Production credentials нигде не печатаются.

## Geodata behavior

На машине найдено 203 `.l2j` regions, поэтому текущий локальный путь имеет полноценную geodata. Если каталог отсутствует или пуст, preflight возвращает `GEODATA_DEGRADED` как warning и итог `PASS_WITH_WARNINGS`: запуск допустим, но pathing/terrain realism ограничены. Этот режим проверен на изолированной пустой fixture.

## Startup and lifecycle findings

До Goal031 `PhantomSystem.startConfigured()` вызывался сразу после Database/ThreadPool и мог обратиться к неинициализированным `World`, data, scripts, managers или offline owners. Вызов перенесён после загрузки всех этих owners и восстановления offline play, перед restart managers. Structural regression проверяет единственный вызов и его положение относительно обязательных anchors.

Production-composed smoke подтвердил lifecycle: создаются ровно 10 durable identities, Scheduler и Decision переходят в `RUNNING`, не менее одного реального headless `Player` присутствует в `World` с lease `PHANTOM`, выбранный профиль выдаёт autonomous decision trace, restart сохраняет те же 10 profile/character/account identities без дублей, а bounded drain/disable идемпотентно освобождает runtime и сохраняет durable world для восстановления.
## Preflight behavior

Канонический entrypoint — `ant phantom-local-play-preflight`. Он проверяет runtime config/preset, 19 authoritative Phantom data files, ожидаемые JAR artifacts, geodata, доступность DB и schema contract. Результат имеет три состояния: `PASS`, `PASS_WITH_WARNINGS`, `FAIL`. Вывод не содержит password, URL credentials или exception message; DB error ограничен безопасными SQLState/errorCode.

Негативные regressions покрывают invalid target/cap relation, точное имя отсутствующего data/schema object, отсутствие geodata, secret sentinel, installer discovery/idempotency и startup order. Production entrypoint автоматическими тестами не запускался: рабочая DB не использовалась даже для read-only test command.

## Commands and tests

| Команда / проверка | Результат | Счётчик / примечание |
| --- | --- | --- |
| guarded `prepare-phantom-test-db` | `PASS` | 121 schema files: login 4, game 115, migrations 2; 214 statements; manifest создан без credentials |
| `ant compile-tests` | `PASS` | 2219 production + 128 test sources; 2 старых deprecation warnings |
| `ant phantom-local-play-preflight-test` | `PASS` | 8/8, seed `31003100` |
| `ant phantom-local-play-readiness-test` | `PASS` | 3/3, seed `31003101` |
| `ant phantom-release-baseline-goal030cp1-test` | `PASS` | 3/3, seed `30003001` |
| `ant phantom-cross-domain-autonomous-alpha-goal030cp2-test` | `PASS` | 6/6, seed `30003002` |
| `ant phantom-release-decision-rollback-goal030cp3-test` | `PASS` | 3/3, seed `30003004` |
| `ant phantom-local-play-documentation-test` | `PASS` | 4/4, seed `31003102` |
| `git diff --check` и exact scope inventory | `PASS` | whitespace errors отсутствуют; только 13 Goal031 files |
| mojibake-маркеры в изменённых файлах | `PASS` | 13/13 UTF-8 files, совпадений нет |
| escaped Cyrillic/XML escaped Cyrillic в изменённых файлах | `PASS` | 13/13 files, совпадений нет |
| единственный финальный `ant jar` | `PASS` | 2219 sources; 3 JAR собраны |

Итог обязательных deterministic suites: **27 passed, 0 failed**. Все три Goal030 gate suites повторно подтверждены.

Промежуточная разработческая обратная связь также сохранена честно: первый provisioning вызов без guard environment завершился безопасным `FAIL` до подключения к БД, после чего штатный guarded provisioning прошёл. Ранний compile выявил UTF-8 BOM в `GameServer.java`; BOM удалён. После добавления runtime-config helper compile выявил два неверных accessor-вызова `status()` вместо существующего `level()`; оба исправлены, полный compile повторён с `PASS`. Четыре ранних readiness итерации выявили слишком строгие test-harness ожидания для queue/selected profile/transient shutdown/bounded drain retry; production semantics не менялись, assertions приведены к существующим operator codes, а финальный production-composed прогон прошёл 3/3 с cleanup.

## Final jar result

Ровно один финальный вызов `ant jar` завершился `BUILD SUCCESSFUL` за 15 секунд. Собраны:

- `build/dist/libs/LoginServer.jar`;
- `build/dist/libs/GameServer.jar`;
- `build/dist/db_installer/DatabaseInstaller.jar`.

Runtime `LoginServer.jar` и `GameServer.jar` скопированы Ant target в `dist/libs`.

## Deviations

- Найденный pre-audit startup defect исправлен в разрешённом task scope минимальным перемещением существующего start block; API и архитектура Phantom runtime не менялись.
- Для понятного разделения operator entrypoint и suite logic добавлен отдельный `PhantomLocalPlayPreflight.java`; это увеличило ожидаемый файловый список, но уменьшило риск зависимости production preflight от test runner.
- `apply_patch` был недоступен из-за Windows sandbox DPAPI `CryptUnprotectData`. Согласно fallback из `Agents.md`, правки выполнены ограниченными exact replacements через временные UTF-8 files; содержимое больших source files не встраивалось в shell-команды.
- Реальный client login/manual UI gate не выполнялся. Task031 имеет один автоматизированный product smoke, а user-facing ручные шаги задокументированы.
- `ant phantom-local-play-preflight` не запускался против рабочей DB, чтобы автоматические проверки не касались production DB. Его read-only поведение покрыто static/guarded regressions.

## Risks and limitations

- Existing-DB installer route проходит общий набор game SQL; перед ним обязательны backup и operator review.
- Geodata-less запуск поддержан только как degraded mode: он не гарантирует полноценный terrain/pathing realism.
- Local-play preset рассчитан на небольшую проверочную популяцию 10/5 и не является scale/soak профилем.
- Console locale отображала часть названий месяца как `????`; это внешний console rendering, а не mojibake в изменённых source/docs.
- Siege AI, generic whitelist quest lifecycle, class quests, Kamaloka и Pailaka остаются вне принятого release slice с явными статусами выше.
- Полный end-user путь с двумя server consoles и игровым клиентом требует ручного выполнения quick-start оператором.

## Recommended next Goal

Следующий Goal должен быть отдельным bounded siege gameplay slice: registration, schedule, gathering, roles, attack, defense и retreat с production/test evidence. Quest/instance work нельзя смешивать с ним: generic quest adapter, class quests, Kamaloka и Pailaka должны идти отдельной следующей задачей. Full original-vision release gate допустим только после принятия обоих gameplay slices.