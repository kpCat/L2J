# RESULT009 — BLOCKED: сохранённый runtime отключает Phantom system

```text
TASK_RESULT=BLOCKED
CLIENT_LOGIN_TRANSPORT=PASS
SERVER_REAL_LOGIN=FAIL
RUNTIME_BEHAVIOR=NOT_OBSERVED
AUTOPLAY_5_CYCLES=NOT_OBSERVED
OBSERVATION_SECONDS=0
OBSERVED_COHORT=0
FRESH_ARM=REJECTED
OFF_CONFIRMED=true
EXPERIMENT_RUNTIME_STOP=PASS_USER_AUTHORIZED_FORCE
GRACEFUL_SHUTDOWN=FAIL
M1=OPEN
```

CLIENT_LOGIN_TRANSPORT=PASS относится к последующему ручному входу пользователя,
разрешённому в этом чате. Command-line login сам не достиг server/character selection.
SERVER_REAL_LOGIN=FAIL относится к строгому pilot-контракту с identity owner
REAL_LOGIN; настоящий сетевой клиент вошёл в мир, но этот контракт не подтверждён.
Setup failure не является отрицательным результатом Phantom gameplay.

## Setup и exact verification

Использован существующий worktree
`C:/Users/ZBook/.codex/worktrees/m1-observe-008/L2J_Mobius` на ветке
`experiment/m1-candidate007-observe008`, исходный HEAD
`778613a9d39c358dbd982bdc677558a6e356762f`.
Frozen candidate `dd58a512c4cb9c6a5318d7320633a35ae849dbf0` — предок HEAD;
103 source entries совпали по SHA256, tracked worktree был чистым.
Source manifest SHA256: `5CE23E62A5E595FF6224D27EF0691883635ECFBC95CA2A0A0A2378ACB1209350`.

Сохранённые runtime JAR совпали точно:

- GameServer: `795DD7708293602B12A942C9F54DDCF7A2156BE5107383C9199DDD40C9999B6F`.
- LoginServer: `587A05944C4C635B414784287F7809ADA2CD7D2450052993256783975E0B851E`.
- Client l2.ini: `47950321EBF86EFA4DA1F8C0200E75519D950AD68518698FA54219BE61DF195A`.

После ручного запуска/выхода client l2.ini имеет SHA256
`3460E46A53B06F682D1FAD8CDF50407829F13080098D77720FD3048BE13E670B`.
Агент файл не записывал; точная причина изменения не установлена.
External Native.cs сохранил SHA256
`47FF9EAC97E527BC857824CA4384C713E807F0439667D664108196DB1E188706`.

Пересборки и изменений candidate/runtime config не было. Оба JDBC URL указывали
только на `l2jmobiush5_localplay_observe008`, localhost:3308. Clone содержит
10000 profiles; TestAdmin objectId=268492939, перед входом online=0.
Original PLAY URL сохранил `l2jmobiush5_localplay3`; 304 исходных file hashes
проверены, различий нет. Original PLAY не запускался и не изменялся.

До startup порты 2106/9014/7777 свободны. Штатный Start-LocalPlay -Background
запустил Login PID14532, start ticks639268016731406575, и Game PID17884,
start ticks639268016749830669. RuntimeId:
`f902d455bdb302c7901c5588949ea000043865269070395a5ace65ec9066f53b`.
Ownership, JVM markers и все listeners проверены. Game loaded37 seconds,
зарегистрирован Bartz/1; pilot startup подтверждён серверным java0.log.

## Client login и server-side evidence

Первый клиент PID5696 запущен штатно с заданными пользователем аргументами.
Просмотрены два кадра 12:56:29 и 12:58:10 UTC: оба показывают
«You are currently logging in. Please wait a moment.»; TCP2106 соединял его
с owned Login14532. Online оставался0; IN_GAME и auth completion не доказаны.
В 12:58:59 PID уже отсутствовал; причина завершения не установлена.

Пользователь остановил работу, затем вручную вошёл под TestAdmin и разрешил
продолжить. Новый клиент PID15116 распознан и визуально проверен как InGame.
DB SELECT дал TestAdmin online=1 и обновлённый accounts.lastactive.
Established localhost:64012 -> localhost:7777 принадлежал клиенту15116,
ответный endpoint — Game17884. Это доказывает обычный native login в clone runtime,
но не заменяет pilot identityOwner=REAL_LOGIN snapshot.
В дальнейшем пользователь просит приглашать его к ручному входу.

## Fresh arm и точная граница blocker

Один fresh permit создан штатным Prepare-LocalPlayPilot в 13:01:15 UTC.
L2ClientTools Arm остановился на Click: Access is denied; submissionAttempted=false,
commandSubmitted=false. Код не пересылался инструментом повторно.
Пользователь ввёл команду вручную; screenshot показывает два серверных отказа
«Пилот недоступен или настоящий клиент не подтверждён.».
Session не создана; Get-LocalPlayPilot вернул WAITING_ARM/no active run/actor=null.

Frozen PhantomPlayersConfig.read ограничивает PhantomPopulationActiveTarget
диапазоном 0..min(populationTarget, maximumMaterialized). Сохранённый task008 runtime:
populationTarget=1280, maximumMaterialized=8, populationActiveTarget=64.
64 превышает8; read возвращает Settings.disabled(). В startup log отсутствует
Phantom World startup, тогда как LocalPlay pilot startup присутствует.
Это deterministic config-contract mismatch; штатный Check-LocalPlay CONFIG PASS
проверяет совпадение manifest/preset, а не Java validation этого диапазона.

При отключённом Phantom system обычный GameClient допускает
loadWithoutIdentityArbitration для персонажа без другого owner.
Pilot realClient дополнительно требует identity owner REAL_LOGIN. Это объясняет
наблюдаемый отказ; owner=null непосредственно runtime snapshot не получен,
поэтому причинная связь с этим конкретным predicate обозначена как вывод из кода.
Pilot enabled=True, synthetic=False; четыре private mailbox directories и их
ZBook-only protected ACL проверены, user.name=ZBook. Guard не обходился.
Population/cap, AI, Player и historical state не исправлялись.

## Наблюдение и пять циклов

Полноценный 10–15-минутный observation episode не начат: fresh arm не принят.
Два просмотренных InGame setup-кадра показывают TestAdmin и обычных Keltir NPC;
наблюдаемый Phantom cohort не установлен. Восемь gameplay frames отсутствуют.
Profile/object/epoch, автономные targets, damage/kill/EXP/SP/loot,
nativeFarmCycleSequence, idle/stuck, смерть и leave/return не наблюдались.
Никаких manual materialization, Phantom attacks/targets/casts, synthetic run,
guarded TEST, WORLD suites или DB corrections не выполнялось.

## Cleanup

Штатный CloseMainWindow для обеих JVM вернулFalse: main window отсутствует.
Пользователь отдельно разрешил force ровно Game17884 и Login14532 и сообщил,
что закрывает клиент после .playtest off. Перед force свежий permit отозван
удалением только созданного arm.properties; session/run отсутствовали,
Get-LocalPlayPilot подтвердил OFF/runActive=false/actor=null.
Оба PID повторно сверены по ownership/start ticks и остановлены.
2106/9014/7777 свободны, MariaDB8696/3308 сохранена. L2 process к cleanup отсутствовал.
Stale PID records сохранены; graceful persistence/shutdown не доказаны.
Clone DB и private diagnostics сохранены. External tooling/client config не правились.

## Scope, проверки и publication

Только RESULT/HANDOFF и sanitized evidence task009 публикуются exact-path commit
и normal push в experiment/m1-candidate007-observe008. Входной task package остаётся
в исходном workspace; его файлы не переписываются и не включаются в commit.
Git явно разрешён исходным запросом и TASK009; точные команды — evidence/COMMANDS.md.
Модель/субагенты не переключались; субагентов0. Работа начата12:51 UTC,
IN_GAME установлен до30-й минуты. Новых engineering tasks не начинать.
M1 остаётся OPEN; следующий шаг определяет координатор по этому blocker.

mojibake-маркеры в изменённых файлах проверены: 0 совпадений.
escaped Cyrillic в изменённых файлах проверены: 0 совпадений.
