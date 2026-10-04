# Observation008 — BLOCKED на клиентском транспорте

```text
TASK_RESULT=BLOCKED
RUNTIME_BEHAVIOR=NOT_OBSERVED
AUTOPLAY_5_CYCLES=NOT_OBSERVED
M1=OPEN
codeSHA=dd58a512c4cb9c6a5318d7320633a35ae849dbf0
sourceManifestSha256=5CE23E62A5E595FF6224D27EF0691883635ECFBC95CA2A0A0A2378ACB1209350
gameJarSha256=795DD7708293602B12A942C9F54DDCF7A2156BE5107383C9199DDD40C9999B6F
loginJarSha256=587A05944C4C635B414784287F7809ADA2CD7D2450052993256783975E0B851E
experimentalBranch=experiment/m1-candidate007-observe008
observationDurationSeconds=0
visualReview=PERFORMED_SETUP_ONLY
observedCohortCount=0
firstBlocker=CLIENT_INPUT_TRANSPORT
originalPlayRestored=PRESERVED_STOPPED_NO_SWITCH
consentOffConfirmed=true
observationProcessesStopped=true
gracefulShutdown=FAIL
```

## Что действительно увидено

Просмотрены два настоящих кадра выбранного клиента `D:/l2afterwork-client/system/l2.exe`,
PID14380. Оба показывают экран Login. На первом поля пустые; после попытки ремонта
транспорта они также пустые. TestAdmin не вошёл в мир, серверного REAL_LOGIN/IN_GAME
нет. Игровых кадров, наблюдаемого cohort и 10–15-минутного эпизода нет.
Это результат блокировки setup, а не свидетельство пустого мира или дефекта AutoPlay.
Удары, касты, движение, reward/loot, пять последовательных циклов, смерть и уход/возврат
не наблюдались. Не проверялись actor admission и непрерывность object/epoch.

Кадры: [до ремонта](evidence/login-before-repair.png),
[после ремонта](evidence/login-after-repair.png). Визуальный просмотр выполнен только
для setup; требование минимум восьми gameplay кадров не выполнено.

| Участник | profile/object/epoch | Результат |
|---|---|---|
| TestAdmin | object268492939 в cloned DB; native session не установлена | NOT_OBSERVED |
| Phantom cohort | не выбран; roster не формировался | NOT_OBSERVED |

## Замороженный источник и сборка

Required base `3fd4aa5f29cf23c1c06cc91ae7b1016c820acb25` сохранён.
MAIN/feature HEAD при preflight: `b80cdf78560c06a9d88b8ec761e1582f0e55afba`;
его незакоммиченные файлы и остальные worktrees не переносились и не изменялись.
103 entries восстановлены только из committed candidate-paused, не из executable
source старого worktree. Проверены manifest SHA256, размеры и SHA256 всех161 artifacts:
55 MODIFIED +8 PREREQUISITE_BYTES_ONLY +40 NEW;58 pending duplicates не применены.
Проверены103 destination SHA256, затем103 Git blob SHA256 и source tree hashes.
Exact allowlist: [CANDIDATE_SOURCE.tsv](CANDIDATE_SOURCE.tsv).

Snapshot commit явно обозначен `UNACCEPTED candidate007 / observation008`.
Чистый `ant -q -Dbuild=.phantom-local/observe008/build jar`: exit0,19 секунд.
Output до сборки новый, без stale classes/JAR. Java/AI/hooks/goal.runtime не правились.
Source bytes после snapshot остались теми же; поздний diff ограничен008 docs/tooling.
WORLD, verify, guarded TEST, historical tests и synthetic runs не запускались.

## Изоляция DB и runtime

Worktree: `C:/Users/ZBook/.codex/worktrees/m1-observe-008/L2J_Mobius`.
Private root: `<worktree>/L2J_Mobius_CT_2.6_HighFive/.phantom-local/observe008`.
Runtime — его подкаталог `runtime`.

PLAY `l2jmobiush5_localplay3` использовалась только для чтения/export на127.0.0.1:3308.
122 таблицы InnoDB. Clone name до CREATE отсутствовало. Export: single-transaction,
skip-lock-tables/triggers/routines/events; без USE/CREATE/DROP DATABASE в dump.
Import адресован только `l2jmobiush5_localplay_observe008`; одна DB для Login/Game.
Никаких seed, ручных UPDATE, journal imports, migrations или записи в PLAY/TEST.

До запуска совпали counts PLAY/clone: characters10002, items150561,
profiles10000, components50000. population.state: READY1280, RETIRED8720.
TestAdmin object268492939, online0. background.state распределение также совпало:
MATERIALIZED175, READY3597, VERIFY_PENDING4, DEAD6181, INCONSISTENT43.
Эти before facts не являются runtime cohort и не объявляются новым GREEN.

Frozen datapack/libs + original private config/registration/java.cfg/log.cfg/geodata.
304 original file hashes проверены до/после, differences0; geodata copy SHA256 сверены.
Не копировались старые PID/mailbox/session/arm records. Различия:
[CONFIG_OVERRIDES.tsv](CONFIG_OVERRIDES.tsv). Только cloned JDBC names, loopback
binds, Pilot=True, SyntheticHuman=False, MaxMaterializedPhantoms8.
Population1280/Active64/MaxScheduled10000, gameplay/rates/timing/data сохранены.
Не запускался Build-LocalPlay preset. Manifest runtime flat и описывает actual snapshot/JAR.

Штатный Start-LocalPlay -Background: Login3064, Game328. Game loaded33 секунды,
зарегистрирован Bartz/1, CONFIG PASS, ownership/PID/start/runtime marker проверены.
RuntimeId `f902d455bdb302c7901c5588949ea000043865269070395a5ace65ec9066f53b`.
Ожидаемый клиентский endpoint127.0.0.1:2106/7777; listeners принадлежали этим JVM.
Фактический connected game endpoint отсутствует: native login не состоялся.

## Первый blocker и ограниченный tooling repair

UTC22:29:31 первый Login остановился на CLIENT_OCCLUDED сразу после запуска клиента.
Следующая попытка уже распознала Login, но Click дал Access denied.
Computer Use capture выбранного окна дважды завершился timeout; gameplay frames
из него не получены. Реальные кадры прочитаны из существующего L2ClientTools.

Один внешний файл `D:/Tools/L2ClientTools/src/Native.cs` был временно изменён после
exact backup. Попытки: guarded SendInput mouse movement вместо failing SetCursorPos;
не отправлять лишний keyboard-layout PostMessage, когда English уже подтверждён.
Login22:34:05 дошёл до проверки полей;22:34:12 LOGIN_FIELDS_NOT_CONFIRMED.
Кадр подтвердил пустые поля. Guard не отключался, Log In не нажат, arm не готовился.
Никакого обхода защит клиента, memory/DLL/network injection или патча l2.ini.
Ремонт прекращён примерно через7 минут; restart observation0, ForceRestart0.
Обе временные правки отменены exact backup; retained external tooling changes0.
Native.cs SHA256 до/после: `47FF9EAC97E527BC857824CA4384C713E807F0439667D664108196DB1E188706`.
Причина недоставки input ниже этой границы не установлена; новый стек не разрабатывался.

## Cleanup

Get-LocalPlayPilot до остановки дважды подтвердил OFF/runActive=false/actor=null.
Arm/session/run никогда не создавались. L2.cmd Off: NotSubmitted, Login screen;
это не выдаётся за серверное подтверждение — OFF подтверждён отдельно серверным helper.

JVM отвечали и не имели main window; force для них выходил за исходный RUNBOOK,
который разрешает его только при зависании. Пользователь отдельно разрешил exact-PID
force cleanup328/3064. Перед ним сохранены thread stacks, повторно проверены codeSHA,
clone URLs, runtime marker, PID/start ticks и ownership. Оба PID остановлены;
2106/9014/7777 свободны, MariaDB PID8696/3308 сохранена.
`gracefulShutdown=FAIL`: native persistence/shutdown этим не доказаны.
Original PLAY в начале STOPPED, не переключался, в конце STOPPED; прежние файлы сохранены.
Созданному клиенту14380 отправлено штатное CloseMainWindow, без force.
Наличие client process после запроса закрытия не трактуется как gameplay session.

Clone/private diagnostics сохранены, не очищались и не переносились в PLAY.
Локальные пути: `play-snapshot.sql`, `clean-build.log`, `original-preservation.tsv`,
`GameServer-cleanup-stack.txt`, `LoginServer-cleanup-stack.txt`, `cleanup.tsv`
под private root; server logs в `runtime/local-play/logs` и `runtime/game/log`.
UI logs: `C:/Users/ZBook/AppData/Local/L2ClientTools/Runs/20261005-013020-e7a6e6f4`,
`20261005-013405-8be2515a`, `20261005-013807-e7f50a82` в той же папке Runs.
Дампы, полные логи, secrets, configs, arm tokens и binaries не опубликованы.

## Publication и ограничения

Git разрешён TASK008 и прямым запросом пользователя. Команды перечислены в
[COMMANDS.md](evidence/COMMANDS.md); exact staging, normal push только experiment branch.
Никаких merge в feature/phantom-world, reset/clean/stash/rebase/force push/broad restore.
reportSHA сообщается после final commit/push, без self-receipt commit.
Результат не закрывает M1 и не является продолжением task007. Следующий fix не назначен.
После отчёта — STOP без automatic continuation.

Финальные статические проверки: [VALIDATION.txt](evidence/VALIDATION.txt),
[exact artifact allowlist](evidence/FINAL_SCOPE.tsv), [PNG hashes](evidence/SCREENSHOTS.tsv).
mojibake-маркеры в изменённых файлах проверены:0 user-facing совпадений;
27 technical matches только в буквальном regex валидатора.
escaped Cyrillic в изменённых файлах проверены отдельно всеми шестью паттернами:0.
Frozen whitespace diff --check exit2 (raw CRLF/trailing whitespace); bytes сохранены
точно по manifest. Это не whitespace GREEN кандидата. Поздний docs/tooling diff --check
проверен отдельно перед publication, без правок source.
