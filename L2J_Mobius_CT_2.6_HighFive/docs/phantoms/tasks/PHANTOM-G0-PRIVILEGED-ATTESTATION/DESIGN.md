# Дизайн привилегированной аттестации G0

## PHANTOM-G0-ONE-CLICK-AUTOMATION — 02.11.2026

Текущий `/goal` разрешает автоматизировать прежний ручной bootstrap и точечный commit/push после тестов и независимой проверки. Scope — те же девять файлов; предыдущее описание ниже сохраняется как история. План исполняется inline, один независимый review в конце.

- [x] RED вне sandbox: `FILE_ACCESS_FALSE_ACCEPT` при Delete; родитель fixture давал DELETE_CHILD. Усилить только fresh test directory ACL; все три группы GREEN вне sandbox.
- [x] Добавить focused RED→GREEN для integrity/debug token queries, строгого helper exit и автоматического bootstrap без ручного переноса параметров.
- [x] Превратить Invoke-G0IdentityAttestation.ps1 в одну команду: automatic focused gate, physical source pins, fresh request, RunAs, bounded wait, consume, повторная identity validation, safe cleanup и итоговый статус.
- [x] Независимый review: один P2 helper exit закрыт поведенческим FALSE_ACCEPT RED→GREEN; focused3 и финальная read-only RunAs интеграция exit0. Проверки кодировок и публикация отражаются в текущей секции отчёта.

Final review: дополнительных P1/P2 нет. Замечание о EnableLUA=0 закрыто явным read-only guard G0_UAC_DISABLED. При зависшем consent незавершённый runspace получает BeginStop, ресурсы ОС освобождаются завершением ordinary host; отдельный fault-injection этого сценария и реальная отмена UAC NOT_RUN. Это ограничение проверки, а не основание принять неполный ответ. В финальной интеграции оба свежих запуска завершились PASS, каждый с отдельным nonce; автоматических retries не было. Следующий goal/task не запускается.

Повторно используются canonical documents, nonce/TTL, native process/file handles и bootstrap с проверкой хеша именно компилируемых байтов. Source pins удерживаются в обычном процессе и независимо в helper. Alternative отдельного broker/нового launcher-файла не нужен: существующий entrypoint является launcher. Нет второго движка или нового protocol family.

RunAs не требует видимого диалога: EnableLUA читается, настройки не меняются; helper обязан иметь elevated token, High/System integrity и реально включённую SeDebugPrivilege. Запрос живёт 120 секунд, receipt 30 секунд, повторный helper автоматически не запускается. Любая неполнота/отмена/ошибка/timeout — BLOCKED. Успех означает только OS identity, `G0=NOT_ACCEPTED`, SQL/GameServer/TASK114=0. Защищённые consumed receipts сохраняются как replay tombstones; request удаляется закрытием собственного delete-on-close handle, незавершённый privileged pending удаляет только создавший его helper. Каталоги чужих запусков не очищаются.

Прочитано: root/module AGENTS, README, bounded workflow/current-status/master-plan/package-standard; шесть исходников и DESIGN/OWNER_RUN/report. Отдельные tool AGENTS/code-map/pattern-файлы не найдены. Аналоги: G0SqlIdentity, G0AttestationFile, Test-Task108R3Process/Task108R3Run. Стек Windows PowerShell 5.1 + штатный Add-Type, без изменения проектов. Реальный elevated lifecycle до финальных тестов NOT_RUN.

Статус: дизайн подтверждён владельцем 02.11.2026, механизм реализован и focused tests пройдены; OWNER_ELEVATED_GATE REQUIRED. Baseline: `feature/phantom-world`, `f3e1d550ca1396ff87cdb938ae9f0cff963aaf69`.

## Назначение и границы

Обычный Windows-токен Codex не может прочитать runtime image MariaDB. Отдельный helper, вручную запущенный владельцем в административном PowerShell, получает недостающие runtime-факты и выпускает короткоживущий private receipt для конкретного свежего запроса обычного G0. Codex и GameServer не повышают права.

Это только механизм OS identity. Успех аттестации не означает G0 PASS, разрешение SQL, создание схемы или продолжение CANCELLED_G0 кампании. SQL/schema/UUID/foreign-session guards остаются на существующем пути. Production Java, WIP109, historical evidence и защищённые runtime не меняются; DB, DDL/DML и GameServer не запускаются.

## Основание и переиспользование

Источник: `%TEMP%/phantom-113-g0-identity-audit/RESULT.txt`, `G0SqlIdentity.ps1`, `Win32SqlIdentity.cs`, `Test-G0SqlIdentity.ps1` и `private-g0.diff`. Старые файлы только читаются. Исходные три focused regression-группы повторены из отдельной копии: exit 0, SQL 0, GameServer 0, markers 0.

Переиспользуются native `QueryFullProcessImageNameW`/`GetProcessTimes`, SCM `QueryServiceConfigW`/`QueryServiceStatusEx`, physical/reparse guard с обходом `FileInfo.Directory`, проверки listener PID и integer-сравнение CIM creation с native FILETIME на общей микросекундной точности. Точное native FILETIME не округляется и передаётся десятичной строкой.

Контракт hashes сохраняется: binary/config SHA-256 — 64 hex; runtime CommandHash — Base64 SHA-256 от UTF-8 исходной runtime command line. SCM command не подставляется вместо runtime command. Существующая проверка согласованности SCM и runtime command сохраняется, при этом hash вычисляется из исходной runtime строки, а не из Trim или SCM.

## Выбор подхода

Предлагается файловый challenge/receipt с Windows ACL и одноразовым потреблением. Он использует существующий PowerShell 5.1 и небольшой Win32-reader без проекта сборки, службы, установщика или внешней зависимости.

Альтернатива с постоянной службой или named-pipe broker создаёт дополнительный privileged lifecycle. Подписанные offline receipts требуют отдельного защищённого ключа и его ротации. Для одного вручную запускаемого helper эти варианты добавляют инфраструктуру без необходимого результата.

## Запрос обычного G0

G0 обязан выполняться с неповышенным токеном. Он создаёт один pending challenge: CSPRNG nonce 256 бит, protocol version, UTC issuance/expiry, собственные SID/PID/exact creation FILETIME, service/path/port policy и предварительные независимые OS-факты. Запрос не содержит command line, credentials, SQL/JDBC URL или произвольную команду исполнения.

Pending state остаётся в памяти живого G0. Связь с request-файлом защищается открытым handle, исключающим запись/замену до завершения. Запрос хранится вне checkout в fresh private каталоге caller+SYSTEM; его полный путь и SHA-256 показываются владельцу. Helper принимает только данные фиксированной схемы; пути скриптов, выражения PowerShell, SQL и произвольные output paths из запроса не исполняются.

Один challenge действует максимум 120 секунд; receipt максимум 30 секунд и не дольше challenge. Конкретные лимиты являются предложением реализации. Обычный G0 дополнительно ограничивает ожидание монотонным таймером и отклоняет будущее время или перевод UTC назад относительно своего начала. Перезапущенный G0 не загружает старый pending challenge.

## Привилегированный сбор фактов

Helper сначала проверяет elevated token, свежесть и строгую схему запроса, SID и живой процесс G0 с точным FILETIME, физический путь и ACL запроса. Он не использует произвольные request-пути для записи. Если нужный query-access недоступен, результат BLOCKED; правки process/service ACL запрещены. Возможное включение уже назначенной SeDebugPrivilege допустимо только в собственном токене helper с восстановлением исходного состояния; это не распространяется на Codex или GameServer.

Helper удерживает read-only process handle MariaDB во время сбора. До и после чтения runtime command line проверяются runtime image/native creation, SCM state/type/account/PID/config и полный набор TCP listener endpoints/owners. Runtime command line читается из Win32_Process, а не из SCM; её отсутствие и изменение между двумя чтениями означают BLOCKED. Native и CIM identity сопоставляются. Executable/config и все родители должны быть физическими и без reparse points; hashes читаются с terminating errors и сверяются до/после.

Перед публикацией проверяются неизменность challenge и повторная свежесть. Полные runtime/SCM command strings существуют только в памяти. Receipt строится по явному allowlist полей: версия, nonce, request SHA-256, caller binding, времена действия, точный server PID/creation, runtime image, binary/config/CommandHash, service identity и listener facts. Никакие исходные объекты CIM или exception dumps не сериализуются. Вывод содержит только безопасный статус и receipt identifier.

## Защита receipt и потребление

Receipt создаётся атомарно с `CREATE_NEW` и защитным security descriptor сразу при создании. Фиксированное хранилище вне checkout: `%ProgramData%/PhantomG0Attestation/<caller SID>/`. Все управляемые каталоги и receipt принадлежат Administrators, имеют protected DACL; полный доступ только Administrators и SYSTEM. Caller имеет лишь необходимые traverse/read права, а на receipt — дополнительно FILE_APPEND_DATA для необратимой отметки потребления. FILE_WRITE_DATA, DELETE, WRITE_DAC и WRITE_OWNER caller не получает. Неизвестный owner/ACE, наследование, подмена, reparse, hard-link alias или небезопасный родитель означают BLOCKED; существующие чужие каталоги/ACL автоматически не исправляются.

Обычный G0 открывает receipt с read+append правами и share mode 0. Проверка owner/DACL, file identity/physical path, ограничение размера и чтение выполняются для того же handle. После проверки доверенного происхождения он добавляет фиксированный consumption trailer и подтверждает запись до возврата identity; erase/truncate/replace этому токену запрещены. Формат принимает только один исходный документ без trailer; повторное чтение, конкурентное потребление, повреждённый или частично записанный trailer дают BLOCKED. Даже неуспешная последующая валидация не возвращает receipt в unused состояние.

Receipt имеет короткий срок допустимости, а не обещание немедленного физического удаления. Истёкшие файлы не принимаются; сохранение исходного файла также не позволяет helper повторно выпустить тот же receipt через CREATE_NEW. Автоматической очистки и перезаписи нет. Наличие минимальных expired/consumed receipts описывается владельцу; секретов в них нет.

G0 проверяет nonce/request digest/pending caller binding, UTC и монотонный срок, PID/exact receipt FILETIME, строгий формат всех hashes и соответствие предварительным и свежим независимым SCM/TCP/CIM/physical/hash фактам. Если обычному токену доступен native/runtime факт, он также обязан совпасть; наблюдаемый конфликт не скрывается receipt. CIM microsecond precision не объявляется точным native FILETIME. Непустой independently readable runtime CommandHash также сравнивается. После consume выполняются повторные live/physical проверки и проверка срока. Любая неопределённость или drift — BLOCKED без pin/pass marker.

## Интеграционная граница

Новый обычный adapter возвращает existing identity shape `PID`, `Created`, `Binary`, `CommandHash` и отдельные подтверждённые metadata. Он не вызывает старый InvokeNative113 и не пишет `server-os.pin`, `g0.*.pass`, blocked markers или campaign receipts старой кампании. Подключение к будущему разрешённому G0 — отдельный явный вызов adapter на месте получения OS identity; оставшаяся последовательность guards не переносится и не дублируется.

Модель угроз включает подмену файлов обычным токеном, stale/replayed receipts, PID reuse, гонки процесса/SCM/TCP и секреты в output. Администратор, kernel и подменённый код самого G0/helper вне доверенной границы. Инструкция владельцу требует сверить точные SHA-256 всех исполняемых helper dependencies и request непосредственно перед единственным ручным запуском.

## Focused tests перед реализацией

1. Identity: перенесённые positive/negative проверки аудита, exact FILETIME с ненулевыми 100 ns, два runtime command reads, SCM/TCP/PID/creation/hash races, native failure и пустая command line. Hash сравнивается с заранее заданным независимым test vector.
2. Receipt: корректный свежий запрос/receipt; wrong nonce/request/caller, future/expired timestamps, повтор и конкурентное потребление, corruption/partial trailer, malformed/unknown/duplicate fields, неожиданное содержимое и изменение независимых фактов. Pure fixtures не выдают себя за elevated integration proof.
3. Windows boundary: настоящий own-process native read, non-elevated helper refusal, physical parent-junction negative, настоящие ограничения read+append без overwrite/truncate/delete/ACL mutation в доступном test fixture. Production trust policy не получает test bypass. Фактическая admin-owned receipt выдача/чтение остаётся NOT_RUN до ручного запуска владельца.

Сначала фиксируется RED для нового контракта, затем GREEN. Existing runner style — отдельный PowerShell test script, без нового test-проекта и длинного inline shell harness. Native tests пишут только в fresh test root; SQL/DB/GameServer 0. Никакого повторного SQL-аудита.

## Allowlist и приёмка

Предлагаемая реализация в `tools/phantom-runtime/`: `G0SqlIdentity.ps1`, `Win32SqlIdentity.cs`, `G0Attestation.ps1`, `Invoke-G0PrivilegedAttestation.ps1`, `Invoke-G0IdentityAttestation.ps1`, `Test-G0Attestation.ps1`. Дополнительно `docs/phantoms/tasks/PHANTOM-G0-PRIVILEGED-ATTESTATION/OWNER_RUN.md` и `docs/phantoms/reports/PHANTOM-G0-PRIVILEGED-ATTESTATION.md`. Вместе с текущим DESIGN.md это девять новых файлов одной attestation family; существующие файлы не меняются. Новые зависимости/.sln/.csproj/build targets не нужны.

Приёмка разработки: focused RED/GREEN с реальными exit codes, source review, точная инструкция владельцу для одного elevated helper launch, отдельные mojibake и escaped Cyrillic проверки, exact artifact scope и сохранность исторического аудита/WIP109. Elevated integration не объявляется PASS по mock-тестам. Затем отчёт и STOP, без DB/native continuation, commit или push.

## Первичные Windows контракты

- [Owner и неявный WRITE_DAC](https://learn.microsoft.com/en-us/windows/win32/secauthz/owner-of-a-new-object).
- [File security и раздельные file access rights](https://learn.microsoft.com/en-us/windows/win32/fileio/file-security-and-access-rights).
- [CreateFileW: descriptor при создании, CREATE_NEW и share mode](https://learn.microsoft.com/en-us/windows/win32/api/fileapi/nf-fileapi-createfilew).
- [WriteFile: FILE_APPEND_DATA и append semantics](https://learn.microsoft.com/en-us/windows/win32/api/fileapi/nf-fileapi-writefile).

## План реализации и ledger

Владелец явно подтвердил: «Подтверждаю, реализовать дизайн». Исполнение в текущем checkout, только новые allowlisted файлы; сохранность WIP подтверждается отдельными hashes. Git mutation/commit/push не разрешены. Skill scripts, создающие worktree/commits или дополнительную инфраструктуру, не запускаются: ограничение текущего задания приоритетно.

Review focus: неоднозначный формат; смена identity между чтениями; owner с неявным WRITE_DAC; consume/replay после crash; потеря точности времени. Эти случаи включаются в три focused группы.

### Task 1: Контракты identity и receipt

- [x] Создать Test-G0Attestation.ps1: known hash vector, прежние identity negatives, два runtime command reads; fresh/wrong/expired/replay/duplicate-field receipt cases.
- [x] Запустить runner: ожидается RED `TEST_RED_RECEIPT_CONTRACT_MISSING` до появления механизма.
- [x] Переиспользовать audited reader/validator. Реализовать строгий line-based документ с фиксированными полями (UTF-8 значения в canonical Base64, без неоднозначности JSON), binding и bounded freshness.
- [x] Запустить runner: ожидается GREEN для pure contract групп.

Интерфейсы: Assert-G0IdentityEvidence(fact,expected,requireElevated=false); ConvertTo/From-G0Document(map,fields); Assert-G0Receipt(receipt,request,requestHash,observation,now); Get-G0Observation(expected,port).

### Task 2: Windows boundary и entrypoints

- [x] Добавить boundary negatives: non-elevated helper, junction parent, file access masks/consume/truncate/replay и own-process FILETIME.
- [x] Реализовать descriptor-at-create, handle-bound owner/ACL/path/link checks, append-only consume и защищённые parents.
- [x] Реализовать Invoke-G0PrivilegedAttestation.ps1 и Invoke-G0IdentityAttestation.ps1. Helper пишет только receipt; обычный adapter не вызывает SQL/старую кампанию.
- [x] Запустить все три группы: exit 0; elevated integration явно NOT_RUN.

Интерфейсы: Win32SqlIdentity.Hold/Read; G0AttestationFile.Open/Create/ReadText/Consume/AssertAcl; helper -RequestPath/-RequestSha256; ordinary adapter -ServiceName/-ServiceAccount/-Executable/-Config/-Port/-RequestDirectory.

### Task 3: Review, инструкция и итог

- [x] Независимый whole-source review по DESIGN и exact allowlist, focused RED/GREEN для существенных замечаний.
- [x] Подготовить OWNER_RUN.md с pinned dependencies, одной admin-командой и ожидаемыми безопасными статусами.
- [x] Проверить parser/compile, focused runner, scope/preservation, mojibake27 и escaped Cyrillic6; записать реальные команды/exit codes в отчёт.
- [x] Completion audit по каждому требованию: разработка завершена, OWNER_ELEVATED_GATE REQUIRED; реальный elevated/DB/GameServer запуск не выполнялся.

Builder check дизайна: nothing important left. Baseline reuse tests: focused3 PASS, exit 0; SHA-256 source/copy трёх audited файлов совпали.

Ruling: TCP facts собираются GetExtendedTcpTable, а не Get-NetTCPConnection — read-only live probe обычного sandbox получил CIM 0x80041003; сами SCM/TCP ownership guards не меняются. Native own-loopback listener test сначала RED MethodNotFound, затем GREEN. Новый collector не меняет SQL, firewall или сетевые настройки; тест открывает и закрывает только свой ephemeral loopback listener.

Task 1 complete: RED отсутствующего receipt-контракта, затем GREEN identity/receipt групп. Task 2 complete: native own-process/TCP/file boundary и обычный-token refusal GREEN. Реальный elevated lifecycle NOT_RUN.

Review: два P2 устранены. Mixed native success и разные native error codes теперь дают BLOCKED; false-accept RED → GREEN. Source leaf pin теперь OPEN_REPARSE_POINT + metadata/final path/link count того же handle, с запретом write/delete sharing; actual hard-link и locked-write negatives GREEN. Delta-review собственного debug-token lease и pinned bootstrap: дополнительных P1/P2 нет.

Task 3 verification: final focused3 exit 0; WIP23 SHA exact и original audit/wrapper preservation PASS. Read-only SCM/TCP/CIM/hash observation вне sandbox: PID8692, tokenElevated=false, nativeReadable=false, commandReadable=false, SQL0. Sandbox CIM refusal не обходился elevated токеном. OWNER_RUN содержит один helper launch, parser/pins проверены runner.

Completion audit: девять exact allowlisted файлов существуют; source/contracts/runner/OWNER_RUN/report проверены. UTC rollback внутри challenge не продлевает receipt: Get-G0ValidationTime использует максимум UTC и Start+monotonic elapsed; targeted RED → GREEN. Кодировки проверены отдельно: mojibake27 PASS, escaped Cyrillic6 PASS, UTF-8/whitespace PASS. OWNER_ELEVATED_GATE остаётся REQUIRED; эта задача завершается на разработке и инструкции, без реального helper launch.
