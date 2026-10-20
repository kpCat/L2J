# AUTHORITY064 — прямое разрешение владельца

TASK064 является продолжением игрового Phantom World после TASK061/062/063, но **не** продлением их старых ownership contracts. Суммарный лимит TASK064: **12 часов** от первого действия, финальный резерв **≥90 минут**, без сброса после compaction. Владелец не планирует отвечать на промежуточные вопросы. Утверждение этого пакета разрешает выполнить прочтение, краткий план, тесты, исправления, сборки, собственные START/STOP, exact-path commits и ordinary non-force push в пределах scope. Ограничения платформы и обязательные внешние подтверждения не обходить.

## Разрешённая запись

- Исходный репозиторий: только `D:\Codex\repos\L2J-phantom-world\L2J_Mobius_CT_2.6_HighFive` и этот task/report; Java production/test, необходимые model data и инструменты нового TASK064, исключительно при доказанном causal scope.
- Текущая ветка: только `feature/phantom-world`; сначала live `git ls-remote`, ancestry и status. Commit/push только exact paths после проверки, без force/reset/clean/rebase/amend/mass staging.
- Новые приватные файлы тестов — под собственным `.phantom-local/task064` в выбранном чистом checkout и/или в новом `D:\PhantomWorld\World-Integrated-064-candidate`, если эти пути свободны. Компиляция в свой build. Копирование подтверждённых ресурсов туда допустимо.
- По необходимости **однократно** новая схема `l2jmobiush5_phantom_test_064` и/или `l2jmobiush5_phantom_release_064a`, только после read-only absence preflight, точной проверки источника, state и прав. Создание и наполнение только своей схемы; не удалять/пересоздавать её автоматически, если уже существует. Для нового candidate — только собственный exact runtime PID/ports/manifests.
- Подготовка разрешённых test actors, предметов, gear, уровня, party и encounter допускается **только до начала соответствующего измерения**. Существующие старые IDs, accounts, profiles и stale quarantine не удалять, не переносить молча.
- Разрешено настроить собственную тестовую Phantom-популяцию, расписание, собственный observer, Geo и network ports так, чтобы она действительно появилась в обычном `World`. Отрицательный сценарий не исправлять скрытой mutation в ходе episode.

## Источники для чтения и точечного переноса (не для записи)

- Старый пользовательский checkout `C:\Users\ZBook\L2J_Mobius`, в том числе High Five и `\.phantom-local`; он содержит локальный dirt и отстаёт от основной ветки. **Ни один его Java/class/XML/конфиг не перезаписывать и не делать merge из него без отдельной причины.**
- `D:\Codex\worktrees\phantom-dev\L2J_Mobius` и прочие старые worktrees, включая крупный untracked массив. Не сканировать рекурсивно весь массив, не удалять.
- Защищённые release-root: `D:\PhantomWorld\M1-057-candidate`, `M2-M7-058-candidate`, `M3-M7-059-candidate`, `Living-060-candidate`, `World-Career-061-candidate`. Read-only только точные подтверждённые input paths GeoData/config/ownership/report и соответствующие checksums; никакого запуска, изменения и STOP.
- Все соответствующие схемы release_057a ... release_061a, старые058a/058t, MAIN/PLAY/`l2jmobiush5`, клиенты, global MariaDB service/datadir/privileges/settings — **не трогать**. Для существующих source DB допускается только ограниченное read-only подтверждение таблиц/состояния, если требуется безопасный one-time export. Не делать schema DROP/reset, reimport, grant, server restart или чужой process kill.
- Собственные `D:\Codex\repos\L2J-phantom-world\PhantomSemanticStudio` и GitHub исходники студии **вне scope TASK064**; серверные semantic/functional XML в новом committed HEAD имеют приоритет над устаревшими копиями.

## Запреты и развязка конфликтов

- Нельзя отключать защиту владельцев, idempotency/transaction/safety, `GeoEngine`, timeout, релизный JAR/hash или отрицательные проверки просто ради PASS.
- Нельзя объявлять all-Phantom TEST Party доказанным фактом реального сетевого клиента. Реального owner-входа нет: `OWNER_CLIENT_QA_DEFERRED`.
- Нельзя использовать запрещённый crash/kill, Attach/jcmd/jstack, стороннее зондирование/сетевые испытания, запрашивать секреты у владельца, копировать пароли/SQL dumps/GeoData/privately staged XML в публичный Git.
- Существующие `tools/phantom-runtime/Build-Package.ps1`, `Export-Campaign058.ps1`, `Campaign058Ownership.ps1` и `Runtime.ps1` имеют **жёсткие привязки к старым OWN/schema**. Не подсовывать им новую схему под старой идентичностью. Допускается создать точную TASK064-совместимую настройку/адаптер только с прежними checks и новыми позитивными/негативными тестами.
- Незапланированное изменение общих игровых правил, реального игрока, миграция production DB и новая бизнес-функция — вне полномочий.

Если безопасная операция не разрешена текущими инструментами или возникает подлинно опасная неоднозначность, **BLOCKED только у этой операции**, остальная разрешённая работа продолжается. Не задавать владельцу вопросы о планах/обычной сборке; все такие действия уже утверждены. Не обещать 100% закрытие M1–M7 при ограничениях.
