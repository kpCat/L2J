# Привилегированная аттестация G0

## PHANTOM-G0-ONE-CLICK-AUTOMATION — 02.11.2026

Текущий результат: **AUTOMATION=PASS, LIVE_ATTESTATION=PASS, G0=NOT_ACCEPTED**. Один ordinary launcher `tools/phantom-runtime/Invoke-G0IdentityAttestation.ps1` автоматически выполняет focused3, fresh challenge, pinned RunAs bootstrap, выдачу и однократное принятие защищённого receipt. Инструкция одной команды — OWNER_RUN.md. Предыдущий отчёт ниже сохранён как история; его NOT_RUN и запрет commit/push относятся к прежней задаче.

### Причина TEST_FAILED и RED→GREEN

В sandbox старый runner проходил. В обычном Windows PowerShell 5.1 вне sandbox получен exit 1: `TEST_FAILED:FILE_ACCESS_FALSE_ACCEPT`, stack указывает на Test-G0Attestation.ps1, строку 130 старого файла, `[IO.File]::Delete($file)`. Fixture создавал родительский каталог с FullControl/DELETE_CHILD. Windows разрешает удаление через DELETE_CHILD родителя даже без DELETE в ACL самого файла: [DeleteFileW](https://learn.microsoft.com/en-us/windows/win32/api/fileapi/nf-fileapi-deletefilew). Ограниченный sandbox-токен скрывал дефект.

Исправлена только ACL fresh test directory: у reader исключён DELETE_CHILD. Проверка удаления сохранена и усилена повторным чтением неизменённого consumed receipt. ACL MariaDB, Windows и существующих каталогов не менялись. После исправления те же три focused-группы дали exit 0 вне sandbox. Новые token/bootstrap/exit/locked-request проверки сначала получили RED отсутствующего CurrentIntegrityRid, затем GREEN в том же runner. Новых test campaigns/projects нет.

### Автоматизация и границы

Переиспользованы все девять G0 файлов: шесть исходников и DESIGN/OWNER_RUN/этот отчёт. G0SqlIdentity.ps1 не менялся. Стек Windows PowerShell 5.1 и штатный Add-Type сохранён. Источник C# перед компиляцией bootstrap проверяется по SHA именно считанных байтов; затем обе стороны физически пинят исходники и проверяют hashes. Строгий helper exit не преобразует null/string в ноль. RunAs выполняется асинхронно относительно caller deadline; отсутствие окна не считается ни ошибкой, ни доказательством elevation. Автоматических retries нет.

Read-only UAC probe: EnableLUA=1, ConsentPromptBehaviorAdmin=0, PromptOnSecureDesktop=0. Ordinary caller: elevated=False, integrity RID=8192. Helper до native reads требует elevated=True, integrity RID>=12288 и действительно enabled SeDebugPrivilege после AdjustTokenPrivileges; прежнее состояние собственного токена восстанавливается до публикации. Settings/registry/policies не записывались. См. [Start-Process RunAs](https://learn.microsoft.com/en-us/powershell/module/microsoft.powershell.management/start-process?view=powershell-5.1) и [TokenElevation/TokenIntegrityLevel/TokenPrivileges](https://learn.microsoft.com/en-us/windows/win32/api/winnt/ne-winnt-token_information_class).

Сохранены PID, exact creation FILETIME, runtime EXE, CommandHash от полной исходной строки только в памяти, SCM/service/TCP owners, native/CIM visibility drift, physical paths/hashes, exact ACL, nonce/caller/request binding, monotonic TTL120/30 и replay fence. SQL/schema/UUID/foreign-session проверки прежней кампании не переносились и не изменялись: этот adapter их не заменяет. SQL, DDL/DML, GameServer и TASK114 не запускались.

Request удаляется delete-on-close handle; все pins освобождаются, собственный пустой каталог удаляется без рекурсивной очистки. При ошибке выдачи helper удаляет только свой новый .pending; чужой existing pending и опубликованный replay tombstone не удаляет. Любая ошибка cleanup даёт BLOCKED до PASS. Receipt остаётся admin-owned consumed tombstone.

### Выполненные проверки

Все команды запускались из фактического корня checkout; `powershell` — Windows PowerShell 5.1.

| Команда/проверка | Результат |
|---|---|
| `powershell -NoProfile -NonInteractive -ExecutionPolicy Bypass -File .\L2J_Mobius_CT_2.6_HighFive\tools\phantom-runtime\Test-G0Attestation.ps1` до исправления, вне sandbox | exit 1, RED FILE_ACCESS_FALSE_ACCEPT |
| Та же команда после исправления fixture и после расширения focused3, вне sandbox | exit 0, PASS1/PASS2/PASS3, elevated=0 |
| `powershell -NoProfile -NonInteractive -ExecutionPolicy Bypass -File .\L2J_Mobius_CT_2.6_HighFive\tools\phantom-runtime\Invoke-G0IdentityAttestation.ps1 -CheckOnly` | exit 0, G0_CHECKS_PASS, RunAs=0 |
| Та же команда без CheckOnly: реальная автоматическая интеграция | exit 0, G0_ATTESTATION_PASS; OS_IDENTITY_ATTESTED; G0=NOT_ACCEPTED; SQL/GameServer/TASK114=0 |
| Read-only cleanup probe под обычным владельческим токеном | один receipt, USED=True, .pending=0; UAC значения прежние |

Реальная identity: MariaDB PID8692, creation UTC `2026-10-28T15:34:43.9827017Z`, runtime `C:\Program Files\MariaDB 11.4\bin\mysqld.exe`, binary SHA256 `103FDDD6CB26F4692B2438742D498EC66AA9518A23CBF02DD6F2E3DC7F242DE3`. Секреты, полная command line и nonce не публикуются. Отдельный прочитанный TEMP request-каталог создан в09:16:17UTC, до этого Goal (09:21:46UTC); это не текущий запуск, его не изменяли/не удаляли. Probe receipt из sandbox получил NOT_FOUND из-за другого sandbox SID; повторный probe ordinary token прошёл, недоступность не выдавалась за доступность.

Byte-exact preimages девяти файлов сохранены вне checkout в `%TEMP%/Phantom-G0-oneclick-preimage-20261102`; hashes исходников/копий проверены. 23 tracked WIP файла сохраняют начальные SHA-256. Несвязанные untracked WIP файлы не изменялись. Production Java/configs/services/DB/релизы и системные ACL вне scope.

Независимый read-only review обнаружил один P2: bootstrap терял exit code вложенного helper. Замечание закрыто: общая Invoke-G0HelperScript очищает прежний LASTEXITCODE, непосредственно фиксирует результат и требует явный integer0; helper завершает успех явным exit0. Реальная регрессия с `ATTESTATION_ISSUED; exit2` сначала дала `TEST_FAILED:FALSE_ACCEPT:G0_HELPER_EXIT`, затем GREEN; отдельно no-exit, null/string и exit0. Финальные focused3 и реальная автоматическая интеграция после фикса: exit0. Неполный helper не принимается даже при напечатанном статусе. Дополнительных P1/P2 reviewer не нашёл.

Minor о EnableLUA=0 закрыт явным G0_UAC_DISABLED без записи реестра. Другой minor/ограничение: при зависшем consent pending runspace останавливается BeginStop, освобождение незавершённого runspace завершает ordinary host; отдельный fault-injection такого зависания и фактическая отмена UAC NOT_RUN. Timeout/expiry и incomplete receipt дают BLOCKED по контрактам; успешный live run не объявляется практическим доказательством всех аварийных ветвей.

Baseline HEAD `f3e1d550ca1396ff87cdb938ae9f0cff963aaf69`, ветка `feature/phantom-world`; текущий пользователь разрешил точечный commit и обычный non-force push готовой инфраструктуры после тестов и независимой проверки. Точный опубликованный SHA приводится в финальном сообщении, поскольку commit содержит этот отчёт.

Финальный cleanup probe после второго успешного запуска: receipts=2, all consumed=True, pending=0, новых request-каталогов после начала Goal=0. SHA-256 всех23 tracked WIP совпали, их tracked status неизменен; исходный index был пустым. Parser G0 scripts PASS. Final artifact scope guard: exact allowlist из девяти файлов, перечисленных в разделе «Changed files» исходного отчёта ниже; никаких других файлов в commit.

- mojibake-маркеры в изменённых файлах проверены: полный набор27, PASS9, exit0.
- escaped Cyrillic в изменённых файлах проверены: все шесть regex, PASS9, exit0.
- Строгое чтение UTF-8: PASS9, exit0.

Git использовался по root AGENTS для bounded baseline/scope и по прямому разрешению текущего Goal для публикации. Команды: `git symbolic-ref --short HEAD`; `git rev-parse HEAD`; `git status --short`; `git status --porcelain=v1 --untracked-files=no`; `git for-each-ref --format='%(upstream:short)' refs/heads/feature/phantom-world`; `git diff --cached --name-only`; `git diff --check -- <exact9>`; `git add -- <exact9>`; `git diff --cached --check -- <exact9>`; `git diff --cached --stat -- <exact9>`; `git commit -m "phantom(g0): automate privileged identity attestation"`; `git push origin HEAD:refs/heads/feature/phantom-world`; `git rev-parse origin/feature/phantom-world`. Здесь exact9 — те же девять явно перечисленных ниже путей, при вызове shell пути переданы раскрытыми аргументами, без broad add. Commit/push итог и точный SHA — в финальном сообщении. Force/history rewrite/branch/merge/restore/clean не выполняются.

STOP после публикации. Полный campaign G0 остаётся NOT_ACCEPTED; следующие задачи не запускаются.

---

## Предыдущая задача: исходная реализация

Дата: 02.11.2026. Результат разработки: SUCCESS. `OWNER_ELEVATED_GATE=REQUIRED`, `LIVE_ATTESTATION=NOT_RUN`, `G0=NOT_ACCEPTED`. Выполнены разработка, focused tests и подготовка инструкции; реальный административный запуск остаётся владельцу. Новая кампания, БД и GameServer не разрешены этим результатом.

Baseline и финальная ветка: `feature/phantom-world`, HEAD `f3e1d550ca1396ff87cdb938ae9f0cff963aaf69`. Commit/push не выполнялись.

## Что реализовано

Новый standalone OS identity adapter создаёт fresh private challenge с CSPRNG nonce 256 бит, своим SID/PID/exact creation FILETIME, request SHA и независимыми SCM/TCP/CIM/physical/hash facts. Challenge удерживается живым неповышенным G0 и действует максимум 120 секунд. Восстановления pending state после перезапуска нет.

Отдельный helper допускается только под elevated token того же пользователя. Он включает уже назначенную SeDebugPrivilege только собственного процесса, затем восстанавливает прежнее состояние до публикации receipt. Он получает runtime image через QueryFullProcessImageNameW, точное creation FILETIME через GetProcessTimes и две runtime command line через Win32_Process. Проверяются SCM service/account/state/type/PID, IPv4/IPv6 TCP owners и стабильность до/после. SCM command не используется как замена runtime command.

Существующие hash contracts сохранены: binary/config SHA-256 hex, CommandHash Base64 SHA-256 от UTF-8 исходной runtime строки. Полные runtime/SCM commands существуют только в памяти; явный allowlist сериализует лишь разрешённые поля. Ни пароли, ни исходные CIM-объекты, ни exception dumps не записываются и не выводятся.

Receipt создаётся с security descriptor сразу при CREATE_NEW, сначала в `.pending`, затем атомарно публикуется rename. Хранилище вне checkout: `%ProgramData%/PhantomG0Attestation/<caller SID>`. Owner Administrators; protected DACL; Administrators/SYSTEM full, caller read+append без overwrite/delete/WRITE_DAC/WRITE_OWNER. Неизвестные ACL не исправляются автоматически.

Обычный G0 проверяет owner/DACL/path/link count на том же exclusive handle, необратимо добавляет consumption trailer и проверяет документ, nonce/request/caller binding, PID/exact FILETIME, hashes, сроки и свежие независимые facts повторно. Любая ошибка, replay, изменение доступности native фактов или drift дают BLOCKED. Истечение 30 секунд receipt не продлевается переводом UTC назад: effective validation time ограничено снизу монотонным временем живого challenge. Возвращаются существующие identity fields, без SQL или campaign markers.

Expired/consumed receipts автоматически не удаляются. Это короткий срок допустимости, а не обещание немедленного физического удаления: сохранённый nonce нельзя повторно выпустить через CREATE_NEW. В них нет исходной command line или credentials.

## Переиспользование и read set

Прочитаны root AGENTS/README, module Agents, workflow/current status, релевантные master-plan/package-standard разделы, bounded TASK113 source-map/safety/report. Предковых AGENTS выше root и отдельных code-map/pattern-файлов не найдено; task-файла нового Goal до начала работы не было. Studio/.sln/.csproj не затронуты и для standalone PowerShell/Add-Type механизма не требовались.

Переиспользован `%TEMP%/phantom-113-g0-identity-audit`: RESULT, G0SqlIdentity.ps1, Win32SqlIdentity.cs, Test-G0SqlIdentity.ps1, proposed/private diff и старый HostG0. Исходные три regression-группы сначала повторены в отдельной byte-exact копии: exit 0. Оригиналы аудита и исторический InvokeNative113 сохранены; hashes проверены отдельно. Новый код не применён к CANCELLED_G0 кампании.

Локальные аналоги: audited strict identity seam и integer microsecond comparison; Win32 SCM/process reader; существующий Test-Task108R3Process.ps1 как отдельный PowerShell runner. Shared validators переиспользуются между helper и ordinary adapter; SQL/schema/UUID/foreign-session checks не перенесены и не дублируются.

## Проверки и review

Основная команда из корня checkout:

```powershell
powershell -NoProfile -NonInteractive -ExecutionPolicy Bypass -File .\L2J_Mobius_CT_2.6_HighFive\tools\phantom-runtime\Test-G0Attestation.ps1
```

Финальный результат: exit 0, `G0_ATTESTATION_TESTS_PASS focused3; SQL=0;GameServer=0;elevated=0`.

| Группа | Реальное покрытие |
|---|---|
| Identity | Audited positives/negatives; independent known UTF-8 SHA vector; ненулевые 100 ns; PID/listener/service/creation/hash/native visibility/runtime command races |
| Receipt | Fresh positive; request/caller/nonce binding; future/expiry/UTC rollback; malformed hashes/FILETIME; canonical format, unknown/duplicate fields и consumption trailer; owner/DACL pure contract |
| Windows boundary | Настоящий own-process native read; свой ephemeral loopback listener и native ownership; file read+append/consume; concurrent-open/truncate/delete rejection; junction/hard-link и locked-write negatives; helper/debug refusal обычному токену; parser трёх OWNER_RUN blocks и SHA pins четырёх dependencies |

Первый RED: `TEST_RED_RECEIPT_CONTRACT_MISSING`, exit 1. Новые native TCP, same-handle file pin, debug-token guard и monotonic validation сначала дали RED отсутствующего API/функции, затем GREEN. Pure mixed-native и different-error regressions сначала дали FALSE_ACCEPT, затем GREEN после исправления.

При доведении runner исправлены fixture/API mistakes: невалидный fingerprint fixture, строковый SID вместо SecurityIdentifier, обработка ожидаемого stderr native-команды и locale-dependent sharing-error expectation. Они не выдаются за product failures или успешные первые запуски.

Независимый source review выявил два P2: потерю успешного native read при отказе второго и leaf-reparse race path-based pin. Mixed success/error теперь BLOCKED; source leaf проверяется через OPEN_REPARSE_POINT, final path и link count того же удерживаемого handle без write/delete sharing. Оба замечания закрыты focused RED→GREEN; отдельный delta-review debug lease и pinned owner bootstrap не нашёл дополнительных P1/P2. Последующее усиление monotonic freshness проверено автором через focused RED→GREEN.

Живой read-only OS probe вне sandbox, с явным отказом elevated token: exit 0, `PID=8692;tokenElevated=false;nativeReadable=False;commandReadable=False;SQL=0`. Подтверждено, что OS facts для challenge доступны обычному токену, а runtime image/command line требуют helper. Codex не запускался под административным Windows-токеном. Выход из filesystem/CIM sandbox не равен повышению Windows token.

Внутри sandbox Get-NetTCPConnection и затем Win32_Process отказали CIM 0x80041003. TCP collector заменён только на read-only GetExtendedTcpTable, с прежними ownership validators; native own-loopback test PASS. CIM refusal не переименован в identity proof. Диагностические запуски под PowerShell 7 и без process-local ExecutionPolicy Bypass также отказали до корректного PowerShell 5.1 запуска; настройки системы не менялись.

Read-only guard существующих родителей ProgramData: PASS. Физические parent/file pins всех четырёх dependencies из реального checkout проверены обычным токеном: exit 0, SOURCE_BUNDLE_PHYSICAL_PINS_PASS. Поставки, БД, runtime ACL и сервисы не изменялись. Ant/Java/GameServer/SQL/DDL/DML запусков 0. C# reader компилировался штатным Add-Type в Windows PowerShell 5.1; новый проект/зависимости не созданы.

## Инструкция владельцу и границы доказательств

Точная инструкция — `docs/phantoms/tasks/PHANTOM-G0-PRIVILEGED-ATTESTATION/OWNER_RUN.md`: отдельный неповышенный G0-adapter и ровно один ручной helper launch в административном PowerShell. Bootstrap проверяет именно байты компилируемого native reader, затем пинит физические dependencies и сверяет SHA до запуска. Все четыре dependency pins и PowerShell syntax проверены runner; SHA всех шести исходников приведены в инструкции.

Реальная elevated выдача, включение/восстановление SeDebugPrivilege и admin-owned receipt create/read/consume lifecycle: NOT_RUN. Pure ACL fixtures и обычные file-handle tests этого не доказывают. Владельческий gate остаётся REQUIRED; G0 PASS не объявлен. При отказе инструкции — BLOCKED и STOP без повторного helper launch, изменения ACL или новой кампании.

## Exact scope и сохранность

Только девять новых файлов одной attestation family, согласованных владельцем:

1. `tools/phantom-runtime/G0SqlIdentity.ps1` — переиспользованный audited validator/collector.
2. `tools/phantom-runtime/Win32SqlIdentity.cs` — Win32 process/SCM/TCP и защищённые file handles.
3. `tools/phantom-runtime/G0Attestation.ps1` — protocol, freshness, binding и shared observation.
4. `tools/phantom-runtime/Invoke-G0PrivilegedAttestation.ps1` — административный entrypoint.
5. `tools/phantom-runtime/Invoke-G0IdentityAttestation.ps1` — обычный identity-only adapter.
6. `tools/phantom-runtime/Test-G0Attestation.ps1` — три focused группы.
7. `docs/phantoms/tasks/PHANTOM-G0-PRIVILEGED-ATTESTATION/DESIGN.md` — согласованный дизайн, план и ledger.
8. `docs/phantoms/tasks/PHANTOM-G0-PRIVILEGED-ATTESTATION/OWNER_RUN.md` — единственный owner launch.
9. `docs/phantoms/reports/PHANTOM-G0-PRIVILEGED-ATTESTATION.md` — этот отчёт.

Все пути выше — относительно High Five. Существующие 23 tracked WIP-файла имеют прежние SHA-256; список tracked dirt совпал с начальным. Три audited source/test файла и historical wrapper совпали с исходными hashes/preimage. Production Java, WIP109, original task evidence и защищённые runtime не менялись. Runtime writes helper ещё не выполнялись; focused tests писали только в fresh TEMP roots.

Mojibake-маркеры в изменённых файлах проверены: PASS, все 27 маркеров во всех девяти файлах, exit 0.
Escaped Cyrillic в изменённых файлах проверены: PASS, все шесть regex во всех девяти файлах, exit 0.

UTF-8 strict decode и отдельный whitespace-check девяти файлов: PASS, exit 0. Exact-path git diff --check: exit 0; exact-path status перечислил только девять новых allowlisted файлов. Он не подменяет отдельную whitespace-проверку untracked содержимого. Финальный HEAD/branch совпали с baseline.

Git использовался только по bounded read-only разрешению root AGENTS/workflow: `git rev-parse --show-toplevel`, `git symbolic-ref --short HEAD`, `git rev-parse HEAD`, `git status --short`, `git status --porcelain=v1 --untracked-files=no`, `git status --short -- L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-G0-PRIVILEGED-ATTESTATION`. Цель — baseline, scope и preservation; Git writes/commit/push/branch/restore не выполнялись. Exact scope-команды выполнялись с перечисленными ниже путями (также в эквивалентной форме с раскрытыми аргументами). Для untracked файлов diff --check сам по себе не является whitespace proof: выполнена отдельная text-проверка.

```powershell
$scope=@(
    'L2J_Mobius_CT_2.6_HighFive/tools/phantom-runtime/G0SqlIdentity.ps1',
    'L2J_Mobius_CT_2.6_HighFive/tools/phantom-runtime/Win32SqlIdentity.cs',
    'L2J_Mobius_CT_2.6_HighFive/tools/phantom-runtime/G0Attestation.ps1',
    'L2J_Mobius_CT_2.6_HighFive/tools/phantom-runtime/Invoke-G0PrivilegedAttestation.ps1',
    'L2J_Mobius_CT_2.6_HighFive/tools/phantom-runtime/Invoke-G0IdentityAttestation.ps1',
    'L2J_Mobius_CT_2.6_HighFive/tools/phantom-runtime/Test-G0Attestation.ps1',
    'L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-G0-PRIVILEGED-ATTESTATION/DESIGN.md',
    'L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-G0-PRIVILEGED-ATTESTATION/OWNER_RUN.md',
    'L2J_Mobius_CT_2.6_HighFive/docs/phantoms/reports/PHANTOM-G0-PRIVILEGED-ATTESTATION.md'
)
git diff --check -- @scope
git status --short --untracked-files=all -- @scope
```

Тестовый лог и WIP baseline находятся вне checkout в `%TEMP%/phantom-g0-attestation-dev-2d356efc4b394a2a9568100cc492c3ab/`. Чтение/запись секретных runtime evidence и повторный SQL-аудит не выполнялись.

STOP. Только разработка механизма завершена; ручной elevated gate и последующий полный G0 здесь не выполнялись.
