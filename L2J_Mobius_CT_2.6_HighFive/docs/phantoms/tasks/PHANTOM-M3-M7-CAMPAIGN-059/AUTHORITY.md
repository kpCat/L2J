# AUTHORITY059 — что разрешено и что защищено

## 1. Продолжение и приоритет инструкций
Утверждённый `/goal` TASK059 разрешает эту кампанию поверх058. Сохраняются:
`M1_TECHNICAL_STATUS=OPEN`, `M1_DEVELOPMENT_BASE=ACCEPTED_WITH_DEFERRED_GATES`, `G4=DEFERRED_BY_OWNER_NOT_PROVEN`, `G6=BLOCKED_PLATFORM_DEFERRED`, `FULL_OWNER_ACCEPTANCE=DEFERRED_BY_OWNER`, `FORWARD_WORK_M2_M7=AUTHORIZED`.

Действующий запрет058 на мутацию старых051/053/057 остаётся. Для **058t/058a** он адресно заменяется правом продолжения ниже. Ограничения платформы и неприкосновенность других сред не отменяются. Исторические отчёты не переписывать; новую поправку достаточно добавить в текущий RELEASE_CONTRACT с ссылкой на059.

## 2. Защищённые объекты
- `D:\PhantomWorld\M1-057-candidate` и `l2jmobiush5_phantom_release_057a`.
- `D:\PhantomWorld\M2-M7-058-candidate` и `l2jmobiush5_phantom_release_058a`.
- MAIN `C:\Users\ZBook\L2J_Mobius`, PLAY,040a,041final,051a,053a,057x72/057x73, остальные worktrees и старые backups/SQL/raw.
- Клиент Lineage II, `NWindow.dll`, `l2.ini`, `hosts`, аккаунты/персонажи владельца в защищённых release-средах.
- Общие MariaDB service/datadir/users/grants, другие приложения и системные сетевые настройки.

Защищённые серверы не запускать/останавливать/обновлять даже если они STOPPED. Не писать в их БД/конфиги и не трогать их логи. Read-only сверка конкретных hashes/manifest/наличия процессов допустима. Старые PID не являются разрешением. Общий Git store изменяется только обычными согласованными операциями из OWN.

## 3. Переиспользуемые среды — без новой миграции и нового bootstrap
Все относительные пути ниже — от High Five OWN.

| Среда | Реальный root | Exact schema | Полномочия059 |
|---|---|---|---|
| Mutable fixture | `.phantom-local/contract058t` (native cwd `runtime/game`) | `l2jmobiush5_phantom_test_058t` | Повторные native tests; создание/изменение собственных case fixtures до измерения; штатный адресный cleanup по receipts |
| Накопительная интеграция | `.phantom-local/contract058a/runtime` | `l2jmobiush5_phantom_test_058a` | Обычная игра, сохранения/рестарты, новые сценарии и неразрушающие миграции; без reset/reimport |
| Новый кандидат | `D:\PhantomWorld\M3-M7-059-candidate` | `l2jmobiush5_phantom_release_059a` | Новый каталог/schema; один consistent stopped export/import058a, smoke и сохранение |

Перед первым write подтвердить manifests, реальные root/schema, отсутствие чужого writer и остановку соответствующей своей JVM; сохранить один stopped baseline058a и небольшие058t fixture preimages/receipts. При повторном запуске059 продолжать собственный checkpoint, не делать новый импорт. Отсутствующий или противоречивый owner не «лечить» сбросом. Если lane недоступен, продолжить независимые pure/source задачи и явно ограничить runtime claims.

Создатель существующей058 среды остаётся058: не переименовывать058 в059, чтобы обмануть guard. Можно добавить отдельное continuation059-свидетельство полномочий. Существующие `phantom.contract058.*` свойства допустимы для реального058 runtime. Только report/case IDs059 и текущие JAR hashes должны соответствовать новому исполнению.

Для058t разрешены новые case IDs/seeds и finite набор TEST actors по штатному native fixture pattern, с exact schema/root/role и ограничениями каждого сценария. Не распространять разрешение на любой localhost/любую test-БД. Существующие отказанные account-deletion операции не возобновлять; сохранять accounts и очищать только разрешённые собственные fixture rows. Sweep/drop/reset/schema-wide cleanup запрещены.

Для058a новые подготовленные TEST roles/сценарии разрешены только на объявленной до измерения границе, с точными IDs и preimage. Сохранять заработанное существующих058 персонажей; не обнулять их SP/EXP/skills/items ради нового теста. Нужны иные начальные параметры — отдельные TEST actors в058t либо новые явно помеченные058a actors. Подготовка высокого уровня — FIXTURE_PRECONDITION, не natural career.

Неразрушающие versioned SQL-миграции новых функций разрешены только058t/058a/новой release059a: preflight/version, backup, repeat-safe upgrade. При остановке процесса миграции сохранять фактическую границу; не удалять старые поля/данные для видимости PASS.

## 4. Штатные инструменты
Разрешены минимальная адаптация и сборка существующих TEST bootstrap/launcher/runner/validator, exporter и Runtime/Build-Package к059 candidate и текущему коду на прежних058 lanes. Перед мутациями — exact-positive и wrong-root/schema/role/JAR negatives. Старые допустимые mappings сохранять; никакого catch-all или представления release059a как release058a.

Нормальные тесты работают с обычным свежим GameServer.jar и штатными services/typed callbacks. Предпочитать это, а не новый recorder. Ранее разрешённый normal-only premain recorder/STOP_MONITOR и штатный shutdown helper разрешены только своим058a и новому кандидату при соответствующем owner; защищённые release057/058 не инструментировать. Оригинальные agent sources/JAR в старых snapshots не менять. Не добавлять debugger, late diagnostic Attach, injector, сетевой transport hack или новый фреймворк наблюдения.

Обычные тесты отрицательных исходов/SQLException/rollback и управляемой конкуренции внутри своей тестовой JVM разрешены. Они не являются разрешением на аварийное завершение OS/JVM/MariaDB, G6, process kill или восстановление запрещённого платформой механизма. При platform отказе не маскировать действие другим названием или инструментом.

## 5. Порты и клиент
058a сохраняет свой exact layout `Login2138/Game7838/control9138`; запускать только если эти endpoints свободны и защищённая058-копия не работает. Если они заняты, не завершать владельца порта: остановить только свой конфликтующий шаг и выполнить независимую работу.

Для **нового**059 candidate разрешены два конечных layout:
- предпочтительно `Login2106/Game7839/control9139`, только если2106 свободен по локальной таблице ОС и старые057/058 не работают;
- иначе `Login2139/Game7839/control9139`, если свободны. Записать точный выбранный layout в manifest/BUILD_INFO/инструкции.

Это разрешение использовать свободный2106 НОВОЙ059 сборкой, а не менять/останавливать057, не обходить занятый порт и не делать port forwarding. Перед START и guards повторно сверить фактическую принадлежность. Guard должен принять только один записанный согласованный layout; отсутствие совпадения — отказ. Если надо расширить прежний exact058 package guard, разрешено добавить explicit059 case с тестами. Не читать/менять клиентские DLL. При2139 честно указать ограничение подключения; функциональная разработка от этого не блокируется.

## 6. Конфиги и пределы
В058 integration сохраняется development profile `population32 / ACTIVE8 / materialized16`; не ждать population1280 и не объявлять этим исправление М1. Разрешены bounded defaults новых command/role policies и их документация. Native rates/skill costs/drop/геодата/время/профессии/instance rules не подгоняются под тест.

Для финальной QA observer allowlist должен содержать `TestAdmin,kpCat`; singular origin/character fields — одно валидное имя. Synthetic OFF в ordinary package. Пользовательские QoL и economy assets сохраняются, но не используются как доказательство заслуженной профессии/награды.

## 7. Git и завершение
BASE при первом запуске должен быть в ancestry фактического OWN и совпасть с ожидаемой точкой до собственных059 commits. Чужой remote advance не затирать; сохранить собственную работу и не применять force/rebase/merge без отдельного согласования. При resume своих commits не требовать возврата к BASE.

Разрешены адресные status/diff/log/rev-parse/merge-base/ls-remote, `git diff --check`, `git add -- <exact paths>`, содержательный commit, `git push origin HEAD:refs/heads/experiment/m1-candidate007-observe008`, `git archive` для clean package. Без branch/worktree creation, миграции, reset/clean/force/rebase и git-add-all.

Незавершённый собственный код сохранять patch+preimage/пути; сломанную сборку не публиковать ради clean-status. Адресно восстановить собственный фрагмент можно только по сохранённому preimage после исключения чужих правок. Старые untracked/evidence не добавлять и не удалять.

Своим058 JVM — штатный Stop/cleanup. Новый059 candidate после save/restart smoke оставить STOPPED. Обе защищённые сборки остаются в исходном состоянии. Один публичный RESULT, один приватный CHECKPOINT059 и одна acceptance-матрица.
