# L2-QOL-003-HF1 evidence report

Дата: 2026-09-13

Branch: `feature/phantom-world`

Required parent: `a53fb9f4c9ebc304d0142a895103451f83dfc780`

Результат: **SUCCESS**

## Scope и read-first audit

До изменений прочитаны module `Agents.md`, корневой `README.md`, `PHANTOM_DEVELOPMENT_MASTER_PLAN.md`, Phantom workflow/task-package документы, `CURRENT_STATUS.md`, `ROADMAP.md`, исходный QOL-003 report и validation docs, весь task/evidence package L2-QOL-003-HF1, production classifier, `SkillTreeData`, `BuffInfo`, полный `QoLEffectDurationSuite` и `build.xml`. Физический корневой `AGENTS.md`, module `README.md`, `docs/personal-qol/AGENTS.md`, `CONTEXT_INDEX.md`, `DEVELOPMENT_CHAT_HANDOFF.md`, code-map и pattern-файлы не найдены.

Переиспользованы локальные паттерны immutable classifier snapshot, канонический `SkillTreeData` owner, package-private production seam для детерминированного теста и текущий suite/build harness. Scope ограничен classifier, его существующим suite и тремя документами. Не менялись множители, exclusions, allowlist, `Formulas`, `Skill`, `BuffInfo`, QOL-001/002, public schema, SQL, Phantom runtime или UI.

## Baseline negative control

До production-изменения один раз запущен поставленный изолированный probe против точного Git blob исходного classifier `93770aa8aa99c8c85382ad8353f59083957a204d`. Контролируемая последовательность воспроизвела lost invalidation: после завершившегося invalidate ожидался `DANCE`, но был опубликован устаревший `SONG`; дополнительный invalidate восстанавливал `DANCE`. Probe не обращался к БД, его временный output не используется как acceptance evidence исправленного кода.

## Реализация

- refresh, invalidate, cold build и публикация snapshot сериализованы общим `_snapshotMonitor`;
- завершившийся `invalidate()` не может быть затёрт публикацией refresh, который начал чтение старого дерева раньше invalidation;
- `classify()` и `conflictCount()` захватывают один локальный snapshot на вызов, исключая смешивание эпох внутри одного результата;
- snapshot остаётся immutable через `Map.copyOf`, а double-checked warm path возвращает уже опубликованное состояние без повторного сканирования `SkillTreeData`;
- default singleton по-прежнему использует `SkillTreeData::getInstance`; package-private supplier существует только как детерминированный seam для production classifier tests.

## Controlled concurrency regression

Исходные пять QOL-003 cases сохранены без изменения, включая фактический H5 skill-tree reload и restore результата `71`. Добавлены три focused cases:

- baseline race protocol подтверждает новый контракт «завершившаяся invalidation побеждает» и переход `SONG -> DANCE`;
- concurrent refresh/invalidate/local-snapshot test выполняет 10 000 операций каждого вида и lookups с latch/barrier coordination, конечными deadlines и нулём failures;
- warm-snapshot test фиксирует четыре cold scans и затем 20 000 lookups с нулём дополнительных scans.

Все workers имеют ограниченное ожидание, failure propagation и cleanup в `finally`; `sleep` и бесконечные ожидания не используются.

## Qualification и DB safety

Disposable sparse candidate: `C:\Users\ZBook\L2J_Mobius\L2J_Mobius_CT_2.6_HighFive\.phantom-local\qol003-hf1-rc-20260913-01`; он содержит только High Five module на `feature/phantom-world`, исходный HEAD/origin `a53fb9f4c9ebc304d0142a895103451f83dfc780`. Три исходно изменённых пользовательских файла и прочие untracked artifacts туда не переносились.

Candidate-only materialization повторила уже квалифицированную EOL/schema рецептуру предыдущего QOL-003 candidate: 115 game SQL, 4 login SQL, 2 schema fixtures и 2 canonical Goal020 semantic inputs, всего 123 byte inputs. Они нужны только для воспроизводимой qualification и не входят в commit. Overlay production/test SHA-256 до validation совпал `2/2`, а финальный overlay вместе с документами — `5/5`; `git diff --check`, historical Goal020 semantic hashes и Goal022 schema hash прошли.

Production DB `l2jmobiush5` не читалась, не проверялась и не пробовалась; `prepare-phantom-test-db` не запускался. Использовалась только разрешённая test DB. Отдельные freshness/DB guard negative controls прошли за 35 секунд: stale schema и production-name были отклонены до загрузки Hikari/драйвера, `driverLoads=0`, `connectionAttempts=0`.

## Test evidence

- `ant compile-tests`: **PASS**, 2260 production и 149 test sources, 24 секунды; сохранены два существующих предупреждения `System.runFinalization`.
- `ant qol-effect-duration-test`: **8/8 PASS**, 36 секунд; пять прежних случаев и три concurrency regression.
- `ant qol-003-verify`: **PASS**, 15 минут 12 секунд; focused, affected, historical 014..022c2 и Goal039 structure/static/docs.
- Один квалифицированный fresh `ant verify`: **PASS**, 29 минут 2 секунды. Повторный qualified full run не выполнялся.
- Standalone `ant -q jar`: **PASS**, 15 секунд; оба итоговых архива успешно прошли `jar tf`.
- Post-documentation `ant phantom-full-vision-goal039-documentation-test`: **2/2 PASS**, 17 секунд вне sandbox.

До финального `qol-003-verify` две диагностические попытки aggregate не дошли до product tests: первая остановилась на Git safe-directory проверке disposable candidate, вторая — на отсутствии prerequisite `dist/libs/GameServer.jar`. После process-local `safe.directory` и отдельной prerequisite JAR-сборки финальный aggregate прошёл. Ранний sandboxed `compile-tests` дважды встретил Windows `AccessDeniedException` на локальном Hikari JAR; такой же diagnostic появился при первом post-documentation canary. Те же compile/canary вне sandbox прошли. Первая попытка standalone JAR-команды не запустила Ant из-за ошибочного относительного пути; команда с разрешённым абсолютным путём прошла. Эти случаи не были отказами production кода или regression tests.

Ожидаемые negative-control сообщения lifecycle/XXE и предупреждения optional custom buylists в полном verify не являются отказами. Goal029 soak, Goal034 real-stack и новый Goal039 aggregate не запускались.

## Final artifacts

Артефакты из `C:\Users\ZBook\L2J_Mobius\L2J_Mobius_CT_2.6_HighFive\.phantom-local\qol003-hf1-rc-20260913-01\build\dist\libs`:

- `GameServer.jar`: SHA-256 `C4575475EE30A8CB3A008482A894EE36EF54B2B6A69BEEC179B15B9F0647FEBD`, 9023095 bytes, 4321 entries; `jar tf` подтвердил `PersonalEffectMusicClassifier`, `Snapshot` и `SingletonHolder`;
- `LoginServer.jar`: SHA-256 `BC246237646B81DA3CD2BD5D072012A36ED1B845F42A25212C54ABEEBA8F59B7`, 313193 bytes, 200 entries.

Commit subject: `qol(003-hf1): fix music classifier reload races`.

Client UI: **NOT_TESTED_CLIENT_UI**. UI не менялся.

Финальный task scope — ровно пять файлов: classifier, существующий duration suite, `CURRENT_STATUS.md`, `ROADMAP.md` и этот evidence report. Task package, candidate materialization, local DB configs, JAR и исходные пользовательские изменения не входят в commit.

Mojibake-маркеры в изменённых файлах проверены: совпадений нет.

Escaped Cyrillic в изменённых файлах проверены: совпадений нет.

Control characters в изменённых файлах проверены: совпадений нет.

После HF1 базовый Personal QoL закрыт. Vitality/rate/premium-item backlog не начат и автоматически не запускается.
