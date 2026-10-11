# TASK034 — журнал исполнения

START_UTC=2026-10-11T02:09:10Z; FIRST_GAME_DEADLINE_UTC=2026-10-11T03:09:10Z.
SEMANTIC_FREEZE_UTC=2026-10-11T06:09:10Z; CLEANUP_BEGIN_UTC=2026-10-11T06:39:10Z; HARD_DEADLINE_UTC=2026-10-11T07:09:10Z.

M1=OPEN; REAL_FINAL=NOT_RUN. Один агент, без субагентов.
Required base и проверенный remote: 44139ca556ad3d5ac35309d5fe222269d3ada256.
Own detached worktree: C:/Users/ZBook/.codex/worktrees/phantom-dev/L2J_Mobius.
Main остаётся read-only. TASK.md отсутствует в поставленном SHA manifest; исполнение по GOAL/DESIGN/PLAN/SOURCE_MAP/SCENARIOS/ACCEPTANCE/RUNBOOK/GIT.

Read-first: module AGENTS.md, repository README.md, релевантные разделы master plan,
CODEX_WORKFLOW_CONTRACT.md, TASK_PACKAGE_STANDARD.md, все 10 файлов пакета034,
required-base RESULT_HANDOFF033R.md и PROGRESS033R.md, build.xml, materialization
service/player/incident, native work/timer, background state/authority, stock inventory,
существующие Prepare/Control/Verify032 и проверенные производные033R.
В родителях и task-каталогах AGENTS.md не найден; отдельного SOURCE_MAP.md нет,
есть SOURCE_MAP.tsv. Повторный поиск отсутствующих файлов не выполняется.

Локальные аналоги: exact owned runtime Prepare032/033R; existing native owner
observer и FINALIZED receipts; materialization suite retained/action-drain/first-incident
fixtures; stock inventory capacity validation. Переиспользуются эти контракты,
без нового runtime слоя, thread per Phantom, SQL-heal или изменения native semantics.

Ограничения: High Five, Java25/Ant, exact own034a/034t DB, не более четырёх
GameServer starts и трёх production Jar-builds. Mandatory93+11 только после
доказанного product fix на стабильном новом Jar. Исторические033R/031/032 read-only.
Bounded exception: обязательный import11 packet paths плюс связанные034 task-doc
и evidence helpers; production/test edits только exact SOURCE_MAP conditional paths.

Пакет034 проверен: 10/10 SHA. Внешний архив033R проверен заново read-only:
25 metadata rows, 14698 raw rows (329939175 bytes), 316 support rows, все SHA совпали.
Verification record находится в приватном external evidence034.

До изменения production установлено:
- Retained36/object268484533/epoch403904657047000: первый NATIVE_STORE /
  PLAYER_STORE_ME, IllegalArgumentException Invalid bounded background inventory,
  UTC2026-10-10T23:42:12.995217500Z; pre-Synthetic live service entry, не archive only.
- Retained45/object268484616/epoch403906003877600: тот же boundary,
  UTC2026-10-10T23:42:14.361240600Z; pre-Synthetic live service entry.
- Native observer final state содержит27/object268484461/epoch404179606830700,
  DRAINING, outstanding1, pendingTimers0, reserved earned cast-launch9692.
  Это новая incarnation после parent403903739619700, не initial scene six actors.
- Read-only SQL032a и033rc: profiles36/45 имеют252 objects, включая249 INVENTORY
  и3 PAPERDOLL. Stock getNonQuestSize учитывает экипировку; вычитать её нельзя.

Непроверено: точные runtime capture arguments и earliest guard для36/45;
exact live service/first incident27 на финальном stop; baseline own JVM causal
reproduction; возможность разрешённого product fix; natural lifetime/global drain.

План уже разрешён пользователем: forensic baseline → узкий causal RED/GREEN при
исправимом product defect → bounded actual native lifetime → freeze/regression
при product fix → truth-preserving cleanup, archive, exact commit/normal push.

## Causal baseline и остановка

02:20:45UTC первый own GameServer PID16604, Login28340, DB034a из033rc.
Первый start через11м35с от фактического старта. R3 Jar reused, production builds0.
До Synthetic сервис имел retained0. Profile36/45 durable state=MATERIALIZED
без current World/identity. Один ordinary materialize36 probe вернул
BACKGROUND_RECONCILIATION_BLOCKED до Player load; это negative guard, не RED.
Stock stop034a: typed DONE/COMPLETE, materialization STOPPED/retained0,
background STOPPED/operations0/leases0/transactions0. Это baseline на старом Jar,
не GREEN034 и не доказательство clean recovery профилей36/45.

02:29:27UTC второй own GameServer PID29348, Login33652, DB034t из read-only032a.
Новые live epochs36=413992015958800 и45=413993041020700. До Synthetic:
retained2, FAILED/NATIVE_STORE/PLAYER_STORE_ME, exact exception воспроизведена.
First36 UTC02:30:20.322434900, first45 UTC02:30:21.346836800.
Read-only capture на SEALED/current owners/outstanding0/timers0 подтвердил:
mutableCount6, trackedObjects246 (243 INVENTORY+3 PAPERDOLL), total/nonQuest252,
limit250, load281301/max7866000. Только capacity guard compactCapacity=false;
native owned capture throws Invalid bounded background inventory.

Подтверждена разница: captureOwnedInventory считает INVENTORY249, native
authority передаёт inventory.getSize252. Stock getNonQuestSize тоже252.
Происхождение всех накопленных объектов не установлено; нельзя объявлять их
внешней порчей или ошибкой033R navigation. В lifecycle allowlist нет безопасного
способа FINALIZED+release при этих facts без изменения health/capacity либо
data reconciliation. Production/test source не менялись; SQL-heal не выполнялся.

02:32UTC stop034t: typed DRAIN_PLAYERS/FAILED, retained2, native owners SEALED
и outstanding0, first incidents сохранены при stock retry; OS Game/Login STOPPED.
Нет typed DONE: исходный StopMonitor пишет только COMPLETE. FAILED подтверждён
полными global034 samples, не заменён отсутствием файла либо выходом JVM.

Historical27: native birth register404179617908900, epoch404179606830700,
parent403903739619700 already terminal. EXP29913→35655, SP6266→6926, cycles36.
Final native sample: DRAINING/current, outstanding1, pendingTimers0,
9692:cast-launch:RESERVED:EARNED. Его nativeFirstIncident пуст; точный first/latest
cleanup incident и final live service phase в033R raw отсутствуют (UNKNOWN).
Cached work sampleNanos404391086713600 нельзя приравнивать к времени записи
final file404396162199900. Fast FAILED shutdown/thread-pool stop — гипотеза,
не доказанный источник этого ticket и не основание для product patch.

02:34UTC semantic decision: PARTIAL_VERIFIED / repair BLOCKED_DATA.
Own GameServer starts2/4, production Jar builds0/3, Synthetic scenes0/2.
No crashes, away/return, sameDB restart, forced release или foreign mutations.
Mandatory93/11 и new-Jar natural scene NOT_RUN: условие product fix не достигнуто.
Остаются archive SHA/readback, exact public diff review и normal publication.

02:38:47UTC raw034 moved в private TASK034-review. Directory10001 files,
ZIP10002 entries independently SHA verified. ZIP SHA
7D0D604E06528652D95E3C533EF60BD9B469D1D04A7C7F1EC21CB1F8B6B2F9CF.
02:43:35UTC две encoding checks: mojibake markers0, escaped Cyrillic0,29 files.
Main packet10+manifest hashes неизменны; main HEAD по-прежнемуb80cdf7…;
remote перед публикацией44139ca…; own production/build diff пуст.
Exact staged scope guard16 public packet/report paths; raw/source/test paths0.
Самостоятельный полный review staged пяти новых task artifacts выполнен:
first27 UNKNOWN сохранён, inherited archived incidents не подменяют live map,
typed FAILED не подменён OS exit, conditional regressions не объявлены passed.
Последний шаг — exact commit/normal push/read-back; PUBLICATION034.json будет
записан вне immutable raw ZIP после появления actual commit SHA.
