# RESULT011

```text
TASK_RESULT=PARTIAL_ENGINEERING_GREEN_RUNTIME_BLOCKED_STOP
CODE_SHA=8457b90723e3c8ff6b419080bb2f84bfd80d6638
PRELOGIN_EVIDENCE_SHA=95ba1150d67b6ba0adaef96158651a1ef4df277e
ROOT_CAUSE_CONFIRMED=YES_DETERMINISTIC
ECOLOGY_LIVENESS_TEST=GREEN_18_OF_18
AFFECTED_REGRESSION=GREEN_6_OF_6
BUILD=ANT_JAR_GREEN
USER_MANUAL_LOGIN=YES_IN_GAME
REAL_LOGIN_OWNER=REAL_LOGIN_268492939
PILOT_AUTOATTACH=ARMED_IDLE_NO_ARM_CODE
MATERIALIZATION_90S=NOT_PROVEN_NO_VISIBLE_MATERIALIZED_PHANTOM
FIRST_MATERIALIZED_PROFILE=NONE_OBSERVED
RUNTIME_BEHAVIOR=NATIVE_LOAD_CLEANUP_BEFORE_WORLD_ADMISSION
AUTOPLAY_5_CYCLES=NOT_OBSERVED_GATE_FAILED
OBSERVATION_SECONDS=90
RUNTIME_STOP=PILOT_RUN_STOP_ACK_ARMED_IDLE_NO_ACTIVE_RUN_SERVERS_RUNNING
M1=OPEN
```

## Root cause and engineering

Required base `26061ccff5aa22450ff3c4a01a2636e660f879e9`, branch
`experiment/m1-candidate007-observe008`; без субагентов.

Deterministic RED подтвердил исходную ecology boundary: native-context requirement
становился terminal и не давал foreground readiness для создания требуемого Player.
Минимальная правка опубликована commit `8457b90723e3c8ff6b419080bb2f84bfd80d6638`.
Native-context requirement теперь сохраняет pending historical request и bounded retry.
Только текущий foreground materialization demand получает нулевой receipt с причиной
`ecology.native_materialization_required`, с проверкой владельца request/window.
Background gates и generic `isRecoverableFailure` не расширены; cursor,
initialCatchupComplete, requestPending и simulationEligible не подделаны.

Production изменены только:

- `java/org/l2jmobius/gameserver/phantoms/background/PhantomHistoricalBackgroundService.java`;
- `java/org/l2jmobius/gameserver/phantoms/population/PhantomPopulationEcologyService.java`.

Единственный test change:
`test/java/org/l2jmobius/tests/phantoms/PhantomPopulationEcologyGoal033Suite.java`.
Детали read-first pass, локальных паттернов и проверок: `ENGINEERING.md`, `RED.txt`,
`GREEN.txt`. Focused 18/18, affected cheap handoff regression 6/6, `ant -q jar` exit0.
Никаких production changes после engineering GREEN не внесено.

## Runtime and manual login

Fresh clone `l2jmobiush5_localplay_observe011`: 10002 characters, 10000 profiles,
50000 components при создании. Original PLAY не изменён. Runtime использует
GameServer JAR SHA256 `8CE3EF5FDB4478657DBFEB41D56BCE83EDCEC1EDEB684FA8109A3444F991E92C`.
Effective config: PopulationTarget=1280, ActiveTarget=8, MaxMaterialized=8,
MaxScheduled=10000, Pilot autoattach TestAdmin, Synthetic=False.
Подробности: `PRELOGIN.md`, `CONFIG_OVERRIDES.tsv`.

После сообщения пользователя «в игре» Pilot был `ARMED_IDLE`, runActive=false,
actorObjectId=268492939. Arm-код не применялся. STATUS завершился
`2026-10-05T18:46:36.1389061Z`: REAL_LOGIN, online=true, worldPresent=true,
native connected client identity=1477527988. Проверка реального клиента в
LocalPlayPilotService требует GameClient IN_GAME и точную связь client/player;
STATUS прошёл эту session guard. Эти worldPresent/identity относятся к TestAdmin,
а не к фантомам.

## Materialization trace and limits

Gate: `18:46:36.1389061Z` — `18:48:06.1389061Z` (90 секунд).
Сохранены STATUS и семь пар read-only SNAPSHOT_PHANTOMS / SELECT_VISIBLE_PHANTOM_TRACE.
Первый snapshot `18:46:38.931818800Z`, последняя visible query `18:48:02.4872877Z`.
Полные точные timestamps: `OBSERVATION.tsv`. Между начальной парой и следующей
парой был промежуток чтения существующего diagnostic protocol; наблюдение дискретное.
После deadline дальнейшего ожидания natural materialization не было.

Все семь snapshots: profile1241, admitted=true, materialized=false, committed
position=(53398,63278,-3480), instance0. Это stored candidate. TestAdmin находился
в (44131,42673,-3488), instance0. Все семь visible queries:
`REJECTED / NO_VISIBLE_MATERIALIZED_PHANTOM`.

Ни один sample не доказал одновременно phantom worldPresent=true, objectId>0,
materialized epoch>0. Наличие положительных epoch в cleanup incidents означает
эпоху неуспешной попытки, а не живого world actor. Visible query проверяет только
видимых TestAdmin живых фантомов; из неё нельзя выводить глобальное отсутствие.
Дополнительный read-only SELECT в clone в `18:52:41.100Z` показал ноль online
phantom characters; это corroborating DB evidence, не замена world/epoch proof.

Пользователь разрешил телепорт к материализованным ботам. Подтверждённого живого
адресата не было, поэтому TELEPORT_SELF не выполнялся. PREPARE_M1_ENVELOPE,
operator materialize, movement, combat, replay и synthetic actor не применялись.

## Earliest remaining boundary — STOP

Новая точная runtime boundary наблюдалась уже при prelogin baseline refresh:

`PhantomHistoricalBackgroundService.refreshCanonicalBaseline:864` →
`PhantomMaterializationService.materialize:322` →
`PhantomMaterializedPlayer.materialize:244` →
`PhantomBackgroundService.afterPlayerLoad:1540`.

Первый incident: `18:40:58.362156200Z`, profile68 / object268485428,
attemptEpoch=81521086970000, hook=AFTER_IDENTITY_CLAIM, phase=NONE,
admittedActions=0. Exact failure:
`Historical native context attestation did not complete under the exact claim: NATIVE_CONTEXT_REQUIRED;refresh=CAPTURE_INVENTORY_OR_AUTOGET`.

Read-only code inspection связывает refresh reason с
`PhantomBackgroundService.refreshNativeVitalsLocked:1629`: captured inventory
objects или autoGetSkills не совпали с committed state, метод вернул null.
Повторная native-context attestation осталась required; exception возник до
`markMaterialized` и world admission. Лог не разделяет inventory и autoGetSkills;
конкретная сторона mismatch в этой задаче не установлена и не исправлялась.

Восемь таких incidents относятся к profiles68/189/211/220/300/908/916/1059.
Ещё два prelogin incidents — profiles1145/1179: loaded native maxHp/maxMp/maxCp
отличались от committed DEAD state; refresh отказал с `state=DEAD,hashMatch=false`.
Все десять cleanup incidents имеют admittedActions=0; см. `NATIVE_INCIDENTS.tsv`.
Их timestamps предшествуют подтверждённому ручному входу. Это точная наблюдённая
native-load boundary, но не доказательство, что тот же callback был вызван для
profile1241 после login. Для profile1241 доказано только отсутствие visible live
actor в samples; его конкретная foreground failure не установлена.

Соблюдён STOP: следующий native/gameplay blocker не чинится, task007 не продолжался,
общий аудит не проводился. 10–15 минут farm observation и пять автономных cycles
не запускались из-за недоказанной materialization. M1 остаётся OPEN.

## Cleanup and publication

Штатный Stop-LocalPlayPilot вернул STOPPED acknowledgement. Повторный
Get-LocalPlayPilot: ARMED_IDLE, runActive=false; autoattach lease сохраняется
для реального клиента, новых observation requests не отправляется.
GameServer PID14472 и LoginServer PID17672 оставлены для текущей пользовательской
сессии; их force stop не выполнялся. Clone и приватные raw evidence сохранены.
Предыдущие observe010 PID5956/19584 остановлены после отдельного разрешения пользователя
и повторной проверки точной ownership; разрешение не переносилось на observe011.

Git-команды использовались по прямому требованию и task011 GIT.md: bounded read,
exact-path commits и normal push той же experiment branch. Команды и allowlist:
`GIT_COMMANDS.md`. Никаких force/reset/clean/rebase/merge/stash/broad add.
Raw session IDs, arm credentials, DB passwords и локальные tokens не опубликованы.

mojibake-маркеры в изменённых файлах проверены: 0.
escaped Cyrillic в изменённых файлах проверены: 0.
