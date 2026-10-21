# RUNTIME_INPUTS066 — изолированная среда и автономный наблюдатель

## Рабочие и защищённые места
Git root: `D:\Codex\repos\L2J-phantom-world`. Продуктовый код/семантика — только текущая feature/phantom-world, не legacy C:.
Legacy `C:\Users\ZBook\L2J_Mobius` сохранён с уникальными локальными ресурсами; только read-only. Не копировать оттуда исходники поверх нового репозитория.
Защищены `D:\PhantomWorld\Living-Presence-065-candidate`, `World-Integrated-064-candidate`, release057–063, их TEST/RELEASE схемы и worktrees. Даже065 теперь источник только для чтения.
Проверенные исходные ресурсы065: `l2jmobiush5_phantom_release_065a`, `l2jmobiush5_phantom_test_065` — не менять/не запускать старый сервер. Runtime065 был STOPPED; проверить фактически, не останавливать его, если кто-то запустил позже.
GeoData:203 региона из проверенного065/064 manifest; физически скопировать в свою среду с SHA проверкой. Не hardlink/reparse на защищённую директорию, не менять source и не публиковать GeoData.
MariaDB11.4.13 на127.0.0.1:3308, JDK25 и Ant уже установлены. Проверить фактические executable/version, не переустанавливать. Совместимость scripts с Windows PowerShell5.1; наличие pwsh не предполагать.

## Новые ресурсы, разрешённые без нового подтверждения
- Candidate: `D:\PhantomWorld\Farm-Economy-066-candidate`.
- TEST schema: `l2jmobiush5_phantom_test_066`; ordinary candidate schema: `l2jmobiush5_phantom_release_066a`.
- Local source/build/log/evidence: `<GitRoot>\.phantom-local\task066\`; новая TEST runtime папка только внутри этой066 области либо candidate-owned подкаталога, не внутри прежних release.
- Предлагаемые собственные триады loopback ports: TEST2246/7946/9246, candidate2146/7846/9146. Проверить занятость; если конфликт, выбрать другую свободную триаду и зафиксировать её в едином manifest/guards. Чужих listeners не останавливать.
До создания проверить отсутствие и realpath каждого destination. Если имя уже занято: использовать только доказанно свой066 runtime с совпадающим ownership manifest; иначе один новый уникальный суффикс пути/schema с явной записью binding. Не удалять/обнулять существующее.
Не более двух новых схем и двух runtime lanes. Секреты — из разрешённого private source config в новые приватные configs; уникальные TEST accounts, access0. Не создавать новых SQL users/grants и не копировать всё пользовательское игровое население.
Стоковые SQL/import только в отсутствовавшую свою схему. Повтор startup — без reimport. Отдельные migrations066 не применять ни к какой старой базе.

## Build/runtime bindings
Переиспользовать065 Build/Assemble/Runtime contracts, но отдельные066 wrappers и точные schema/path/JAR/port bindings. Не заменять все строки064/065 глобально и не отключать pinned guard ради запуска.
`local-play.json`, PID/start ticks, JAR hash, source/build metadata, private ACL и конечный физический путь обязаны совпадать. Self-owned path проверять целиком, а не только leaf name.
Сборка Ant пишет JAR; запускать в своей build/staging области, а не в защищённой поставке. Исходный snapshot/commit должен объяснять каждый production class поставки.
Нативные тесты, если запрещают новую066 схему/путь, расширять адресно до точного нового binding с negative guards; не превращать TEST-prefix в wildcard-доступ ко всем базам.

## Что реально умеет observer сейчас
LocalPlayPilotService загружает persisted viewpoint отдельного настроенного обычного char из БД без загрузки его Player. `PhantomSystem.onlineHumanPoints()` использует autonomous point, когда нет настоящего online human.
Рабочие настройки: `EnableLocalPlayPilot=True`, `EnableLocalPlayPilotAutoAttach=True`, непустой `LocalPlayPilotAutoAttachCharacters`, валидные `LocalPlaySyntheticCharacterObjectId/Name`; при этом `EnableLocalPlaySyntheticHuman=False`.
ObjectId/name выбрать фактически созданные в собственной066 БД. Не копировать objectId Task065View в новую схему вслепую. Origin должен лежать на существующей GeoData и в подходящей stock области.
`LocalPlayObserverProfileIds` — только bounded read-only selection (до8), НЕ команда материализовать профили. Настроить private mailbox/DACL/manifest через existing tooling.
Автономный observer не является сетевой сессией и не доказывает client rendering. `_lease`/arm/real-client command channel без клиента недоступен — это нормально, не пытаться подделать GameClient/REAL_LOGIN.
При входе настоящего игрока сохранить existing AUTONOMOUS→REAL→AUTONOMOUS поведение; активное управление его персонажем данным пакетом не разрешено. Наблюдать пассивно; сцену без клиента при необходимости отложить, не logout/teleport человека.
Отключённый synthetic не означает отключённый autonomous observer. Не смешивать TTL synthetic actor с временем жизни обычного сервера.

## Доступные доказательства без клиента
Свежие observer status/снимки operatorLifeProgress и штатные bounded diagnostics/flight recorder: identity, World Player, epoch, damage/kill/reward/loot, native work, inventory/warehouse/commerce, supply и retirement.
Разрешено добавить небольшой read-only diagnostic seam в существующий observer для недостающих counters и смены ОДНОЙ проверенной passive observation-point в своей среде; без общего remote console, arbitrary SQL или мутаций акторов.
Если seam потребует отдельной системы, использовать фиксированные точки между штатными запусками/имеющийся native test harness; отсутствие real mailbox lease не причина требовать вход владельца.
Не выдавать сведения из старого log или background projection за свежий World результат; проверять PID/incarnation/JAR/source/time/epoch.

## Остановка и сохранность
Перед START и перед STOP сверить own PID/start ticks/runtime root/JAR и actual bindings. Все Ant/server операции только в своих lanes; не разрешать две JVM писать в одну066 схему одновременно.
На выходе штатный STOP game/login; проверить own процессы, listeners и собственные DB writers. Нет права на принудительное убийство процесса. При отказе остановки сохранить диагноз/manifest и честно BLOCKED_SHUTDOWN.
Источник065 остаётся побайтово неизменным; проверить hashes реально прочитанных source resources в начале и конце. Секреты, raw rows и большие логи не попадают в committed evidence.
Подготовить START_HERE_066_RU.md: точный candidate, START/STATUS/STOP, свои БД/порты, включённые флаги, что проверено сервером и что осталось client QA. Сейчас никакого действия от владельца не требуется.
