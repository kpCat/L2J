# RUNBOOK — от замороженных байтов к клиентскому эпизоду

## 1. Preflight,8 минут, без инженерного аудита

Прочитать локальные обязательные instructions, этот пакет и task007 RESULT/HANDOFF/
ARTIFACT/SOURCE_MANIFEST. Это чтение происхождения; старые «NEXT=historical» не исполнять.
Проверить `feature/phantom-world` против `3fd4aa5f29cf23c1c06cc91ae7b1016c820acb25`. Если появился новый commit,
не смешивать его автоматически с candidate007: сохранить pinned base и сообщить drift.
Не делать parallel source changes в старых worktrees.

Определить настоящие runtime roots Login/Game по записанным PID, start time, commandline,
ports и configs, а не предположить, что MAIN/dist — запущенный сервер. Из текущего UI
проверить выбранный l2.exe и прочитать только L2.cmd плюс вызываемые им UI scripts.
Неизвестные пути/процессы не останавливать. Не угадывать supported command-line flags.

## 2. Source freeze и сборка

Создать разрешённый новый worktree и `experiment/m1-candidate007-observe008` от base.
Скопировать туда008 docs. Из committed task007/candidate-paused:

- проверить SHA256 manifest = `5CE23E62A5E595FF6224D27EF0691883635ECFBC95CA2A0A0A2378ACB1209350`;
- прочитать TSV с колонками module_path/state/sha256/bytes/artifact_path;
- проверить размер и SHA каждого source artifact, включая58 pending duplicates;
- убедиться:55 MODIFIED +8 PREREQUISITE_BYTES_ONLY +40 NEW =103 integrated;
- скопировать103 integrated entries в соответствующие module-relative paths нового
  worktree. Запрет абсолютных путей, `..`, symlink/reparse выхода и чужих destination edits;
-58 pending не копировать в executable tree; proposal files не применять;
- проверить103 destination hashes, сохранить CANDIDATE_SOURCE.tsv.

Разрешение на этот exact overlay — не разрешение править его содержимое.
Сделать snapshot commit с явным `UNACCEPTED candidate007 / observation008` в сообщении.
Staging — только103 manifest paths и008 docs, без private runtime/secret files.
Для raw-byte сохранения использовать per-command core.autocrlf=false и проверить Git
blob/export hashes. При заданных attributes, меняющих bytes, не скрывать расхождение:
остановить snapshot-этап; не чинить глобальные Git-настройки/чужие worktrees.

Создать свежий isolated build output; `ant -q -Dbuild=.phantom-local/observe008/build jar`
в module. До сборки нет stale classes/JAR в этом новом output. Сверить, что команды
не собирают другой checkout. Manifest чистого source tree должен соответствовать snapshot
commit. Записать codeSHA, JDK/Ant, exit code и SHA256 обоих JAR. Java compile failure
не исправлять в008. Не запускать `verify`, `phantom-m1-production-world-test` или full suites.

## 3. Копия данных, не TEST migration

Использовать установленный локальный export/import client или уже имеющийся проверенный
clone helper. SOURCE=`l2jmobiush5_localplay3`; target=`l2jmobiush5_localplay_observe008`.
Только target schema получает CREATE/INSERT от копирования. Проверить отсутствие имени
либо принадлежность008; чужое существующее имя => BLOCKED, не DROP и не другое случайное имя.
Если Login/Game используют разные source DB, копировать login только в разрешённый
`l2jmobiush5_localplay_observe008_login` на том же локальном MariaDB; remote DB => STOP.

Export должен быть согласованным snapshot: existing consistent transaction export без
записи/блокировки таблиц PLAY; при неподдержанной схеме — snapshot после graceful stop.
При выборе native dump CLI проверить его фактические параметры через локальный help.
Не использовать export с USE/DROP/CREATE исходной database или исполнением чужих routines/
events/triggers. Import обязан явно адресовать только target. Приватные dump/пароли
остаются в ignored каталоге; не выводить их в команды/публикуемые logs.

Сверить число characters/items/phantom profiles/components, READY/RETIRED и TestAdmin.
Никаких ручных UPDATE goal/runtime/vitals/vitality/online/READY; никакого повторного seed.
Сырые before-image/test journals007 не импортировать. Guarded TEST не трогать вообще.
Клон после run не возвращается в PLAY и не очищается для подбора удачного результата.

## 4. Runtime assembly и порты

Новый private root: `<new-module>/.phantom-local/observe008/runtime`.
Datapack/libs брать из frozen experimental checkout/build, private config и необходимую
регистрацию Login/Game — из установленного текущего local-play runtime. Geodata копировать
из реально использованной локальной папки без правок и с проверкой необходимых hashes.
Не копировать старые PIDs/mailbox/session/arm files. Не скачивать новую геодату/зависимости.

Сохранить before/after config diff (секреты исключить). Разрешённые runtime overrides:
точные cloned JDBC DB names; loopback addresses; EnableLocalPlayPilot=True;
EnableLocalPlaySyntheticHuman=False; MaxMaterializedPhantoms=8; paths/JVM runtime identity.
Не менять PopulationTarget/ActiveTarget/MaxScheduled, rates/drop/AI timing/skills/gear,
conversation/market toggles, hashes и схемы. Не применять Build-LocalPlay presets.
Все JDBC URLs Login/Game должны указывать только на clone; запуск с URL PLAY/TEST запрещён.

Скопировать existing LocalPlay-Ownership, Start/Check, Pilot/Get/Prepare/Invoke/StopPilot
scripts в новый runtime. Там `local-play.json` заставляет их использовать собственный root.
Flat JSON manifest: format=1, databaseConfig=USER_CONFIRMED_EXISTING,
databaseName=<clone>, codeSha=<experiment>, реальные loginJarSha256/gameJarSha256;
только scalar fields. Это описание проверенного runtime, не подмена ownership/PID records.

**Стандартный клиент оставляем на прежнем локальном endpoint.** Если порты свободны —
запускаем isolated runtime. Если заняты подтверждённым исходным LocalPlay — разрешён
один controlled switch: backup исходных runtime configs/бинарных файлов/регистрации и
согласованный DB snapshot, затем только штатный graceful stop exact owned серверов.
Сначала завершить consent исходного TestAdmin. Использовать существующий native admin/
GUI shutdown либо проверенный Ctrl-C только в выделенной консоли конкретного процесса.
Если console содержит чужие процессы или graceful path не подтверждён — BLOCKED.
Нельзя вызвать старый Stop-LocalPlay.ps1 и дать ему незаметно уйти в Force.
Исходные runtime файлы не заменять; после наблюдения запустить тот же старый runtime.

Альтернативный endpoint допустим только если уже поддержан имеющимся UI tool/client:
не придумывать `-Port`/`-Server`, не перепаковывать/патчить l2.ini. Не разрабатывать новый
маршрутизатор/прокси. Порты и реальный connected endpoint записать.

Запустить existing Start-LocalPlay из observation root с startup timeout до8 минут.
Проверить health/actual codeSHA/JAR/runtimeId/DB targets. Не ждать10000 READY как gate.
Production exception => сохранить первый stack и состояние; никаких repairs.
Если сервер жив и показывает пустой/сломанный мир, это уже наблюдаемое поведение,
его можно зафиксировать в эпизоде; не подменять setup через manual materialize.

## 5. Real client и consent

Использовать `D:\Tools\L2ClientTools`, клиент `D:\l2afterwork-client\system\l2.exe`.
Login tool уже знает локальные тестовые credentials; не публиковать его private secrets.

```powershell
cd D:\Tools\L2ClientTools
.\L2.cmd -Action Status
.\L2.cmd -Action Login
```

Если старый клиент ещё IN_GAME на другом сервере, Login как no-op не переключит его:
штатно выйти из старой сессии и переподключиться. ForceRestart только при доказанном
зависании выбранного клиента, не как обычная кнопка Login. Не убивать все l2.exe.
Нужны native TestAdmin online, World, REAL_LOGIN, GameClient IN_GAME именно observation PID.

Из нового runtime вызвать `Prepare-LocalPlayPilot.ps1 -ExpectedName TestAdmin`;
получить fresh code и `L2.cmd -Action Arm -ArmCode <fresh>` через существующий transport.
**Не отправлять буквально <fresh>.** При SubmissionUncertain сначала server consent state,
не повторять arm. `Get-LocalPlayPilot.ps1` должен подтвердить ARMED и runActive=false
до первого action request. UI InGame/InputSentUnverified недостаточно.

## 6. Один эпизод: default12 минут, не «пока GREEN»

Сначала сохранить настоящий снимок окна. Затем existing Pilot STATUS/CAPABILITIES/
SNAPSHOT_PHANTOMS, при наличии кандидата PREPARE_M1_ENVELOPE INITIAL и его snapshots.
Предпочтение Talking Island/стартовому споту с гремлинами, **если там уже есть легально
допускаемый фантом и его goal разрешает этих NPC**. Не менять goal/npcId ради локации.
Иначе использовать существующий стартовый Elven/другой малый farm spot и явно указать его.
Не тратить больше 5 минут на выбор места и не перебирать ботов до успеха.
Если PREPARE_M1_ENVELOPE отказывает по старым условиям выбора, не чинить его и не
прекращать уже работающий клиентский осмотр: использовать существующие SNAPSHOT_PHANTOMS,
SNAPSHOT_TARGETS и выбранный read-only trace. Недоступный census так и отметить.
Нельзя объявлять пустой мир только по отказу selector, если на кадрах есть Player.

Фиксировать первый выбранный profileId/objectId/epoch и до 4 наблюдаемых профилей рядом
по детерминированному порядку profileId. Зафиксировать также всех FAILED/retained рядом.
Реальных участников может быть меньше4: записать число и причины, не дорисовывать cohort.
Первичный roster не уменьшать после отказов; новые участники — отдельная дополнительная строка.

Первые5 минут — только автономный local farm выбранного Player, попытка5 consecutive cycles.
Никаких SELECT_TARGET/ATTACK_NPC/CAST_LEARNED_SKILL/loot за фантома; не убивать/лечить/бафать
мобов или фантомов TestAdmin. Не вызывать materialize/start AutoPlay/DecisionEngine вручную.
Не давать предметы, навыки, EXP и не менять drop/RNG.

На5–6-й минуте — один уход TestAdmin за locality и короткое возвращение. Переиспользовать
existing validated LEAVE/RETURN ticket или обычное native движение наблюдателя. Envelope
ограничен480 секундами: закончить ticket-часть до7:30, не увеличивать серверную константу.
Далее до12-й минуты — пассивно наблюдать текущую жизнь. Если нужен обычный пассивный сбор
за пределами envelope, применять уже существующие snapshots/trace, не новый protocol.
Pilot run ограничен20 минутами и512 records; сбор должен уложиться, heartbeat поддерживается
существующим transport. Базовый интервал5 секунд, census30 секунд, общий предел300 запросов.

Смерть наблюдать, если случилась естественно; отсутствие смерти = NOT_OBSERVED, не PASS.
Не насильно провоцировать смерть/утопление и не создавать summon/party для покрытия матрицы.
Короткий уход может оставить того же Player по retention: это не доказательство полного
demat/background/remat. Длительный lifecycle будет отдельным следующим vertical.
При натуральном изменении epoch не склеивать счётчики разных жизней в5-cycle PASS.

Снимать окно выбранного l2.exe каждые15–30 секунд, не весь чужой desktop. Просмотреть
минимум8 реальных кадров по разным фазам. Имеющаяся видеозапись предпочтительна, но новый
capture-stack/загрузка программ не требуются. Чёрные/невалидные кадры не доказательство.
Если агент лишь сохранил файлы, но не открыл их, visualReview=NOT_PERFORMED.

## 7. Выход и сохранение

До конца20-минутной Pilot lease/run budget восстановить положение наблюдателя, где это
допускают existing operations, STOP_MOVE и Stop-LocalPlayPilot; затем в L2ClientTools:

```powershell
.\L2.cmd -Action Off
```

Server-side подтвердить OFF/no active run. Off не запускает игру. Если NotSubmitted и
consent ещё активен: Login → Off → server verification. Если JVM упала, записать, что
server OFF не подтверждён, но lifetime завершён; не выдавать отправку за подтверждение.

Штатно остановить observation Game/Login. Только если его собственные процессы зависли,
сохранить stack/log и после проверки PID/start/console/clone URL допускается остановка
**экспериментальных** PID; gracefulShutdown тогда FAIL, не PASS persistence.
Никогда не force-stop исходный PLAY или MariaDB. Не удалять clone/private diagnostics.
Если исходный PLAY останавливался, вернуть прежний runtime с прежними JAR/config/DB,
проверить health и сохранность файлов; новый PID при controlled restart ожидаем.

В RESULT/HANDOFF: настоящее увиденное поведение, ссылки на кадры/таблицы, codeSHA/JAR,
конфигурационные различия, timings и первый blocker. Короткий readable итог, не320reports.
Sanitised screenshots/TSV и hashes — на experiment branch normal commit/push. Видео/полные
логи и DB dump оставить локально с точным путём; передавать remote только безопасный минимум.
