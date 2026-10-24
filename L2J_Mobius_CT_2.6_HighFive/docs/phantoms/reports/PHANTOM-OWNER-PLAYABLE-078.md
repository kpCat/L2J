# PHANTOM-OWNER-PLAYABLE-078

Дата: 24.10.2026. Статус: **OWNER_PLAY_PENDING**. Самостоятельная поставка и серверный lifecycle проверены;
реальный вход владельца и материализация рядом с человеком остаются непроверенными.
**World=0 в обеих пробах без клиента. PASS присутствия игроков, Гирана или полного мира не заявляется.**

## Authority и source

- Git root: `D:\Codex\repos\L2J-phantom-world`; единственный модуль High Five.
- Branch: `feature/phantom-world`; initial local/upstream/actual remote HEAD: `ea7eaa596883d20292ffb5967a498141748e17ad`.
- Production source078 — тот же tracked commit; Java gameplay, balance, SQL, public config и build.xml не изменяются.
- Initial tracked dirt:0. Untracked roots: `.phantom-local`, task packages068V/073/076/077/078; сохранены вне allowlist.
- Target delivery: `D:\PhantomWorld\Playable-078-owner`; target schema: `l2jmobiush5_phantom_release_078`.
- Новый staging: `<Git root>\.phantom-local\task078`; исходный `build/dist` и прежний local-play runtime не используются.

Final production SOURCE: `ea7eaa596883d20292ffb5967a498141748e17ad`.
Tracked archive SHA256: `8843E3E47A9CA2B16C48F38E79E2CC9FD88A4E951A1988F90703A50F72B3EACA`.
GameServer.jar SHA256: `1B29B4689A7A83CE17A1A2D35EE24190C10F15AC6ED57362E6BC218657341584`.
LoginServer.jar SHA256: `E492B541C31A56119DBEB7D9DC05DB6915C1C1A022B081435752628E1AE14EA1`.
Final operator shutdown JAR SHA256: `A3E913985D92F054E6660775E221B7DFC280D588E5C408D9FED5BB826BB6DDC9`.
Production JAR не патчились после сборки. Новые operator scripts входят в commit этого отчёта,
их точные bytes/classes/JAR hashes записаны в private `BUILD_INFO.json`, отдельно от production SOURCE.

## Read-first / решения

Прочитаны root/module AGENTS.md, root README, весь078 пакет, актуальные операционные разделы master plan,
CURRENT_STATUS/WORKFLOW/EFFICIENCY/RELEASE/OWNER_PROFILE, предыдущий077 и local-play001 отчёты.
Внешние AGENTS.md выше Git root отсутствуют. Отдельные pattern-файлы не найдены;
роль code map выполняет SOURCE_MAP078. Исторические campaigns полностью не перечитывались.
Локальные аналоги: fresh DB guard/installer/register, commit-archive Ant build, LocalPlay ownership,
Runtime.ps1→RequestShutdown→stock Shutdown/LoginServer.shutdown. Старые Build/Stop не запускались.
Read-set расширен для доказательства безопасной совместимости Runtime078: его Java guards,
SQL accounts/profiles, public World/Player API и canonical ecology codec. Соседние хроники не читались.

Известные writers: единственный canonical installer в новой078 schema; GameServer native Player/profile/component,
inventory и общие scheduler/persistence writers; LoginServer account/registration writers;
operator PID records/logs. Никакие новые gameplay writers, timers, engines или fixture actors не добавлены.
Probe использует read-only JDBC и public World snapshot; gameplay locks/atomicity не переопределяются.

## Exact allowlist

1. `tools/phantom-local-play/Build-FreshLocalPlay.ps1`: LibraryOnly для переиспользования существующих helpers.
2. `tools/phantom-runtime/Build-Owner078.ps1`: новый absent-only staging/output, commit archive, одна Ant jar,
   staged OwnerConfig, физическая GeoData, canonical new-schema installer/register, private ACL.
3. `tools/phantom-runtime/Runtime.ps1`: bounded078 root/config/operator/GeoData/port/DB checks и понятный статус;
   существующий stock lifecycle остаётся единственным runner.
4. `tools/phantom-runtime/Owner078Probe.java`: только read-only DB hashes/cursors и native World observation.
5. `tools/phantom-runtime/Test-Owner078.ps1`: три focused safety checks.
6. `tools/phantom-runtime/Verify-Owner078.ps1`: package manifest и две отдельные проверки текста.
7. `tools/phantom-runtime/README_OWNER078_RU.md`: источник пользовательского README в candidate.
8. Этот отчёт. Task package, private runtime, credentials, ZIP и generated binaries не коммитятся.

## Preflight и effective config

- JDK Temurin25.0.4.1; Ant1.10.17. MariaDB endpoint:127.0.0.1:3308, initial service PID8956.
- Initial Java/JVM:0. Client2106/Game7777/control9014 свободны. Чужие процессы не останавливались.
- Новый delivery root и schema078 подтверждены отсутствующими до сборки/CREATE.
- 18 прежних delivery directories в `D:\PhantomWorld` инвентаризированы read-only.
- Source `artifacts/local-play/runtime` и прежний `C:\Users\ZBook\L2J_Mobius` не обнаружены при preflight.
- GeoData input: `D:\PhantomWorld\Living-Systems-074-candidate\game\data\geodata`,203 физических `.l2j`,
  1 063 452 308 bytes, reparse0. Только чтение и физическое копирование в078.
- OwnerConfig source Verify:68 files,14 overrides,0 conflicts,changed0. 38 rules проверяются canonical verifier.
- Stage operational settings:32 durable profiles,8 ACTIVE, исходный cap32, burst0, pulse100ms, LIVING.
  Выбраны по shipped small-server guidance; Lively/demo presets не применяются.
- Source Player.ini, Rates.ini, quest rates, Servitor Share1557, +25 и38 owner rules сохраняются.
  Personal QoL ограничены private allowlist обычного аккаунта; GM не выдаётся.
  Cubic=False; global AutoNoblesse остаётся False. Offline bootstrap merchants не включаются.
- `ant -q -Dbuild=<new task078/compiled> jar`: exit0,21s, одна production compilation.
  Archive содержит81312 entries/1 140 892 076 expanded bytes; WinPS Expand-Archive занял основное время.
- Canonical installer выполнен один раз:126 tables,18 empty player tables; fresh serverId1 зарегистрирован.
  Daily START не содержит CREATE/installer/reseed. New DB оставлена на месте, schema не клонировалась.
- Final GameServer.jar OwnerProfileVerifier:38/38, XML_XSD PASS, READ_ONLY=true.
  Staged Apply изменил только две profile/checksum projections; Verify затем changed0, overrides14.
- Полный copied dist:18476 script files,4041 HTML,203 GeoData. BUILD_INFO:93 config hashes,14 operator hashes.
  Root и staging защищены private ACL current owner/SYSTEM/Administrators; секреты не выводились и не stage-ились в Git.

## Матрица

| Gate | Status | Evidence |
|---|---|---|
| SOURCE_PROVENANCE | PASS | final JAR/source SHA выше,203 physical Geo, полный dist |
| NO_DESTRUCTION | PASS | новые exact paths/schema; старые данные не записывались |
| OWNER_PROFILE | PASS | source/staged Verify14 overrides; final JAR38/38 |
| NATIVE_BOOT | PASS_START / WORLD0 | ordinary Main оба раза; реальные PID/ports/DB32; физическое присутствие NOT_OBSERVED |
| SAME_DB_RESTART | PASS | тот же root/DB/JAR,32 identities/profiles, registration и cursors сохранены |
| OPERATOR_UX | PASS_CMD | настоящие CMD START/STOP/STATUS; paths/exit codes tested; GUI double-click лично не наблюдался |
| OWNER_CLIENT | OWNER_PLAY_PENDING | вручную владельцем |
| DELIVERY | LOCAL_DELIVERED / OWNER_PLAY_PENDING | README/manifest/wrappers на месте; final STOPPED |

## Ordinary Main и sameDB continuity

| Boot | Login PID | Game PID | START | stock STOP | Native sample |
|---|---:|---:|---|---|---|
|1|21020|32848|exit0|обе STOCK_STOP_CONFIRMED,exit0|10:45:37 UTC:World0/offline0/GM0|
|2|30428|26064|exit0|обе STOCK_STOP_CONFIRMED,exit0|10:50:55 UTC:World0/offline0/GM0|

Порты2106/9014 принадлежали соответствующему Login PID,7777 — соответствующему Game PID;
incarnation/startTime/runtimeId подтверждены ownership helper. Startup2:33s,3342/4096MB Java memory,
GeoEngine203 loaded, Registered on login as Server1:Bartz. Первая измеренная Game working set около4.60GB,
CPU144.7s cumulative; это одиночные наблюдения, не benchmark. Stock drain26ms/13ms, штатный shutdown около5s.
После каждого STOP DB connections0. Final own JVM0, listeners2106/7777/9014=0; MariaDB не останавливалась.
PID records сохранены как STALE_RECORD audit, фактических процессов нет; следующий START проверяет отсутствие.

Stopped snapshots:32 identities и32 profiles имеют одинаковые hashes до/после restart:
identity `5b9f88c1d15d214190b46cea217c6ccdc5ea8bd74499d1294a98a2e401805321`;
profiles `c0b372e071f4fe12aeacff9108167fa0126f99778e7b42c32893abe6fc04bc23`.
Регистрация1 неизменна. Все32 canonical ecology cursors не уменьшились,20 продвинулись;
assignmentOrdinal/ecologyGeneration не сброшены. Components224; item rows231→233 и progress hash изменились
естественно между boots. Полный earned/acquisition маршрут и item conservation этим не доказаны.
GM characters0/accounts0;32 phantom accounts имеют штатный disabled accessLevel−1, не GM.

World0 сначала был оценён мной как blocker; после сверки exact078 acceptance оценка уточнена:
требуется реальный START и честная фиксация World/DB, а не выдача World0 за populated world.
`PhantomPopulationManager.processReadySchedule` передаёт календарный ACTIVE как BACKGROUND;
physical detail определяется locality. Это source-объяснение, не runtime proof появления рядом с человеком.
Поэтому технический boot PASS отделён от NOT_OBSERVED materialization и отложенной клиентской QA.
Капы/профили/stock для получения ненулевого World не менялись; synthetic human не запускался.

## Проверки и промежуточные результаты

- Remote `git ls-remote origin refs/heads/feature/phantom-world`: exit0, SHA совпал с local.
- Первый sandbox remote/CIM access был недоступен; адресный read-only запуск вне sandbox прошёл.
- Первый status с `--untracked-files=all` оказался избыточным; далее использованы только normal/no modes.
- Focused safety: RED без isolated builder; затем3/3 PASS (existing root sentinel unchanged,
  force fallback absent, foreign port/unknown manifest rejected). Отрицательные действия не затронули БД/процессы.
- Одна финальная короткая suite после operator-fix:3/3 PASS; реальные CMD в temporary пути
  `Путь с пробелом` со stub сохранили CWD-independent root и exit37 для START/STATUS/STOP.
- Existing DB negative: canonical assert-absent вернул exit1, old schema не изменялась.
  Login/Game mismatch: RuntimeDatabaseCheck отказал до launch, exit1; только новые private fixture-копии.
- WinPS parser изменённых PS scripts:PASS. Task078 manifest:10/10 SHA256 PASS.
- Проверка текста сначала обнаружила особенность smart quote `‚` внутри single-quoted PowerShell regex;
  причина подтверждена parser tokens, literal переведён в double quotes. Production build не затронут.
- Mojibake-маркеры в изменённых файлах проверены; intentional verifier marker literal выделен отдельно.
- Escaped Cyrillic в изменённых файлах проверены.
- Полный ant verify, исторические native scenes и stress не запускались по scope078.
- Единственное адресное исправление после packaging — operator probe ошибочно считал accessLevel<>0 GM;
  canonical PopulationStore подтвердил−1 для disabled accounts. Исправлено на>0, добавлен disabled count,
  уточнены status/blocked-marker сообщения. Только helper javac/JAR и их manifest hashes обновлены;
  production Game/Login SHA остались прежними. Preimages с SHA-проверкой сохранены вне checkout:
  `<delivery>/backup/operator-fix078`. Повторной production compilation не было.
- Первоначальный ручной comparator ошибочно требовал неизменности items; исправлена только проверка:
  естественная работа меняет items/progress. Identity/profile/registration/cursor contracts прошли.

## Команды, scope и публикация

Основные действия: `Build-Owner078.ps1 -OutputRoot D:\PhantomWorld\Playable-078-owner
-StageRoot D:\Codex\repos\L2J-phantom-world\.phantom-local\task078
-GeoDataRoot D:\PhantomWorld\Living-Systems-074-candidate\game\data\geodata`;
`cmd /d /c call START_078.cmd <nul`, `STOP_078.cmd <nul` — по два раза, STATUS — отдельно.
`Owner078Probe world` — по одному sample/boot; `quiescent` и `Verify-Owner078.ps1 -RuntimeRoot <delivery>` — read-only.

Git использовался по прямому разрешению078 и root/module AGENTS для provenance/scope и normal publication:
`git rev-parse --show-toplevel`, `git symbolic-ref --short HEAD`, `git rev-parse HEAD`, `git rev-parse '@{upstream}'`;
`git status --porcelain=v1 --untracked-files=all`, затем `normal`/`no`;
`git ls-remote origin refs/heads/feature/phantom-world`;
`git archive --format=zip --output=<new stage>/tracked-input.zip <SOURCE> -- L2J_Mobius_CT_2.6_HighFive`;
`git diff --numstat -- <java,dist,build.xml exact paths>`, `git diff --stat -- <two changed scripts>`;
`git diff --check -- <allowlist8>`, `git diff -- <two changed scripts>`;
финальный `git diff --cached --check/--stat -- <allowlist8>`, `git add -- <allowlist8>`,
`git commit -m "phantom(task-078): add isolated owner playable delivery"`,
`git push origin HEAD:refs/heads/feature/phantom-world`, затем remote HEAD verification.
Allowlist — ровно8 файлов выше; credentials/task packages/generated artifacts не включаются.
Один publication commit содержит этот отчёт и operator source; его self-SHA не встраивается в собственное содержимое.
Exact publication SHA и фактический результат push сообщаются в финальном ответе после проверки remote.
Reset/clean/worktree/force/stash/restore/merge/rebase, master и default branch не использовались/не изменялись.

Telemetry: initial read-set включал инструкции/078 package/операционные docs; expanded set перечислен выше.
Repository rg searches превысили6 (включая несколько ошибочных exact-path lookups); bounded exception —
доказательство совместимости build/shutdown, API/SQL и диагностика World0; полного чтения файлов>1000 строк не было.
Safety RED1; intermediate green checks2; final operator regression1; full ant verify0; production ant jar1;
ordinary boots2; forced stops0; new schema1; new candidate1; native scenes0.
Goal telemetry checkpoint:240921 tokens,2526s; основная задержка — WinPS распаковка81312 entries.

## Непроверенные границы

Клиентская QA,074 дальний farm/PvP/acquisition,075 D/E/F,076 native Party,077 native S80 остаются
OWNER_QA_PENDING/PARTIAL. Q298 high-level повтор отложен по решению владельца. Сборка078 не доказывает
S80/raid/full life и не меняет исторические статусы. R2/079 не разрешены этой задачей.

Владельцу: используйте `README_OWNER_RU.md`, затем STATUS_078/START_078/STOP_078.
Дампы/прогресс не удалять. Старые PARTIAL не повышены; никакой автоматической078-R2/079.
