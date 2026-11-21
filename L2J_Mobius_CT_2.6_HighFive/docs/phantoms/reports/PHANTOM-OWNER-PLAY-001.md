# PHANTOM-OWNER-PLAY-001 — BLOCKED_SAFE

21 ноября 2026 г. Предварительная проверка P0 завершилась до сборки и запуска.

## Блокер A0

Для этой сессии отсутствует подтверждённый собственный экземпляр SQL. Оба `dist/{game,login}/config/Database.ini` указывают на `127.0.0.1:3308/l2jmobiush5`. Штатный `tools/phantom-local-play/Build-FreshLocalPlay.ps1` берёт SQL endpoint и реквизиты из этих исходных конфигов, поэтому с текущим `dist` обратился бы к защищённой MariaDB на порту 3308 даже с новым именем схемы. `Get-Process` и `netstat.exe -ano -p TCP` показали два процесса MariaDB: порт 3308, PID 7664, и PLAY Preview на `127.0.0.1:33318`, PID 20756. Для другого экземпляра отсутствует аттестация выделенных порта, datadir, схемы и роли. SQL-подключений, импорта и запуска процессов в рамках задачи не было.

Для повторной P0-проверки нужен путь к уже подготовленному изолированному тестовому окружению с подтверждением его SQL-порта, datadir, схемы и роли. Реквизиты передаются только локально. Технических действий с клиентом до `PLAY_READY_FOR_OWNER` не требуется.

## Факты P0

- Ветка `feature/phantom-world`. Фактический GitHub HEAD совпал с локальным `1d78c0f904247f4a0877c8f34764b50da24a41d0`, прямой родитель `5e57ea7a1820a83857dad35a9d889a424db13b3a`. GitHub compare с HEAD вернул `identical`, сравнение с родителем — один коммит вперёд.
- Отслеживаемых изменений на входе не найдено. В Studio подтвердился untracked WIP, в `tools/phantom-runtime` — отдельный untracked скрипт. Полный `git status` завершился с кодом 0 и предупреждениями доступа к историческим каталогам `.phantom-local`. Для Studio и серверных путей результаты дополнительно проверены адресно.
- Во время P0 процессов Java и клиента L2 среди проверенных имён не обнаружено. `Get-CimInstance` и `Get-NetTCPConnection` вернули отказ в доступе. Сведения о PID и слушателях получены через `Get-Process` и `netstat`, полная аттестация командных строк и принадлежности будущих портов осталась `NOT_RUN`.
- `dist/START.cmd`, `STATUS.cmd` и `STOP.cmd` делегируют действия в `dist/tools/Runtime.ps1`. В локальном `dist` отсутствуют этот файл, `local-play.json` и `BUILD_INFO.json`. Исходный `tools/phantom-runtime/Runtime.ps1` требует манифесты, привязку схемы, SHA JAR и подтверждённого владельца процессов. Альтернативный `tools/phantom-local-play/Stop-LocalPlay.ps1` содержит принудительное завершение как резервный путь. Процедура запуска и штатного сохранения для OWNER-PLAY-001 не аттестована.
- Оба старых JAR физически присутствуют в `dist/libs`. Их связь с текущим HEAD не доказана. Цель `jar` в `build.xml` компилирует с Java 25 и перезаписывает оба JAR в `dist/libs`. Новая сборка и SHA свежих артефактов — `NOT_RUN`.
- В `dist/game/data/geodata` найдено 0 файлов `.l2j`. Отдельная физическая копия GeoData203 для этой задачи не готовилась. `EnablePhantomSystem=True`, `PhantomPopulationTarget=5000`, `PhantomPopulationActiveTarget=128`, `LearningMode=OFF`; pilot, auto-attach и synthetic human выключены, allowlist и выборка observer пусты. Эти значения описывают конфигурацию.

## Приёмка

| Gate | Статус |
| --- | --- |
| A0 SAFETY | `BLOCKED_SAFE`, выделенный SQL не подтверждён |
| A1 PLAY_READY | `NOT_RUN`, LS/GS не запускались, адрес для входа не назначен |
| A2 WORLD | `NOT_RUN`, обычные World-фантомы и offline merchants отдельно не измерены |
| A3 FARM | `NOT_RUN`, нет native telemetry по движению, атаке, kill, EXP/SP |
| A4 OWNER | `OWNER_NOT_RUN` |
| A5 PARTY | `NOT_RUN` |
| A6 CLEANUP | `NOT_APPLICABLE`, собственные PID и слушатели не создавались |

Стартов LS/GS, тестовых прогонов, `ant jar`, `ant verify` и изменений конфигов — 0. Записей в Studio WIP, MAIN/PLAY, MariaDB3308/33318, protected207, Geo203, training10, checkpoint216 и Goal024 latch эта задача не делала. Побайтовая сверка защищённых runtime-артефактов со старыми контрольными суммами не выполнялась.

Единственный новый файл — этот отчёт. Стартовый набор — пакет `PHANTOM-OWNER-PLAY-001`, корневой и модульный `AGENTS.md`, корневой README, текущий статус и документы рабочего процесса, участки `build.xml`, штатные обёртки и конфиги `dist`, исторические `START_HERE`. Дополнительное чтение ограничено SQL bootstrap `Build-FreshLocalPlay.ps1`, `Start-LocalPlay.ps1`, `Stop-LocalPlay.ps1`, ownership-селектором `LocalPlay-Ownership.ps1` и заголовком `Initialize-Task131N01Runtime.ps1` для исключения исторического TASK131 контура.
