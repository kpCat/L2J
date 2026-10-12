# RESULT_HANDOFF038

TASK_RESULT=FAILED_ACTION_LOOP.
M1=OPEN; REAL_FINAL=NOT_RUN; STOP_AUTHORITY=TASK038_CONTRACT.

Общий native action-loop встроен и проходит portable native проверки. Обязательный
product gate не прошёл: финальная пара на одном Jar дала CONTINUITY_V2 B4/8 и C4/8.
FULL_SAVE всех lifetime также NOT_VERIFIED. Эти результаты не заменяются успехом
отдельных primary или тестов. Следующая задача не запускалась.

## Provenance и бюджет

BASE aa63dafb79e19a531b5620af195764c823bd9539. OWN — существующий phantom-dev;
MAIN/PLAY/старые DB только читались. MANIFEST038:23/23; pinned SOURCE_MAP проверен.
BASE.md отсутствует: required BASE задан в GOAL/GIT. Верхние AGENTS и HighFive README
не найдены; применимые .codex/AGENTS и HighFive Agents.md/workflow прочитаны.
Repository-wide code-map не найден; использован утверждённый SOURCE_MAP038.

J1 source3758d852fbeccd584b9f06030bdafccf73504c14, затем J2 source
95c0572f2ffa6c25ab92bdd6297e1f07d3f2136f. Оба normal push с local=remote.
Замороженный GameServer Jar J2:
161e304a7f90b2ec4b5b36a5913635a8a2a8cbaa2e2035d2424e226298044f96.
Login Jar e59bc36b1778f9a0fe6373b5286d382d3825eb170aef362d69b8320eb96d2c25.
После freeze Java не менялась. Final report commit/SHA/remote и archive SHA/size
связаны внешним publication-receipt038.json, чтобы избежать циклического hash.

Фактический старт 2026-10-11T23:09:28Z; hard stop 2026-10-12T07:09:28Z.
Полный runtime/evidence получен к01:38Z, затем delivery. Расход5/8 GameServer starts,
2/4 production builds,2/4 full scenes; диагностический80s probe отдельно.
Первый JVM start завершился ошибкой старого037 premain guard до core boot; сохранён.
J1_B stock-stopped до measurement, чтобы завершить переносимые native cases.
Valid native causal RED получен до60m. Лимит не продлевался; unchanged RED ради удачи
не повторялся. Настройки запрошены GPT-6.1 Sol/Very High; отдельной проверки текущего
model setting API нет. JDK25.0.4/Ant1.10.17/MariaDB11.4; hashes в PROVENANCE_FINAL038.json.

## Встроенный механизм и проверенные исправления

Production FARM entry: PhantomBackgroundDecision/PhantomVisibleFarmTravel передают
существующую navigation в PhantomVisibleAutoPlay Session. PhantomEngagementPlanner
разделяет domain eligibility и action readiness. PhantomEngagementController связывает
scalar target identity/position, существующий submitWalkCorridor, fresh native edge,
physical progress и stock AutoPlay/AutoUse. ArrivalRadius сам по себе не означает
готовность ударить: нужны actual native range и LOS. Native damage/reward остаются stock.

Session command gate общий для decision, AutoPlay, AutoUse и sameepoch replacement;
busy writer даёт PAUSED. Route/request/progress принадлежат этому controller, native
lease и exact lifetime/goal/revision/target generation проверяются перед командой.
Checkpoint/revoke отменяет own request, новый root закрывается, earned работа остаётся
на036 native path. Corpse возвращается stock; cooperative load остаётся мягким.
Новых потоков, combat engine, SQL протокола или runtime framework нет.

RED_SPAWN038 на BASE: independently FOUND native обход, spawned Player, но отсутствует
stock damage. GREEN_SPAWN038 и J2_ACTION038 доказывают настоящее движение/прибытие/damage.
RED_FRESH2_038: route-only receipt ошибочно закрывал fresh legal action. Теперь reuse
shared opportunity снимает только route-only veto; protocol/anchor/CAS guards сохранены.

Intermediate J1 matrix91/93 выявила F01/F10; nativeDirect range approach разрешён
только с non-excluded existing localRouteWitness. READY не требует MOVE.
GREEN_RANGE2_DEV0383/3 сохраняет F02 negative, final matrix93/93.
RED_GEOMETRY038 и GREEN_GEOMETRY_DEV038: нативный turn в34.727 units пропускался при
radius50, следующий edge был незаконен, три action.edge_changed без MOVE. Теперь
near waypoint пропускается только после fresh next-edge proof; policy50/20/3s/120s сохранена.

J2_ACTION0385/5: Human origin(-88984,250008,-3568), Orc origin(-50385,-115833,-186),
independently FOUND corridors и проверенные native edges. В каждой геометрии >=5
actual kills и >=5 completed cycles, EXP525/SP50; native loot получен. Последняя
completed cycle требует обычного следующего target selection: TEST fixture даёт шестой
NPC, без назначения цели или изменения native counters. FULL4/FULL5 не выдаются за это proof.

## Финальная natural пара

Оба окна stationary Synthetic,380s, одни J2 jars, ordinary native Player/GameClient=null,
без REAL_LOGIN, arm, invulnerability/heal, target commands или TELEPORT во время window.
Имя клонированного Synthetic char — TestAdmin, object268492939; это существующий RUNBOOK
Synthetic path, не TestAdmin login/оператор. Первичный setup — pre-World readonly resolver.
Все enrolled IDs и исходные epochs сохранены; deaths/return/replacement не исключены.
Primary выбраны до measurement. B368/437 class10; C115/176 class49, class provenance сохранён.

| Scene | Seconds/samples/max sample gap | Primary | CONTINUITY_V2 | Legacy strict |
|---|---|---|---|---|
|B Human|381.033/370/1.100s|368,437|4/8 FAILED|4/8 FAILED|
|C Orc|380.751/370/1.064s|115,176|4/8 FAILED|2/8 FAILED|

| Scene/ID | Original epoch | Cycles | Max idle s | Tail rewards | Verdict/faults |
|---|---|---:|---:|---:|---|
|B302|496019502639300|7|69.796|5|FAIL COMPLETE_LINEAGE_REQUIRED; dead at baseline, native return|
|B368|496019532517400|11|71.581|2|PASS|
|B437|496019555307400|0|297.082|0|FAIL PHASE_DEADLINE/NATIVE_UNPROVEN, cycles/tail/debt|
|B489|496019569038200|10|61.898|7|PASS|
|B515|496019590403500|5|113.871|4|FAIL USEFUL_PROGRESS_DEBT|
|B576|496019606442600|12|99.712|5|FAIL USEFUL_PROGRESS_DEBT|
|B646|496019626648400|5|82.464|3|PASS|
|B694|496019646473800|11|81.384|2|PASS|
|C115|496727828178700|15|81.087|4|PASS|
|C176|496727844757400|19|81.087|3|PASS|
|C181|496727856092900|27|23.918|8|PASS|
|C215|496727893681000|13|23.777|0|FAIL COMPLETE_LINEAGE_REQUIRED, TAIL120_REWARD|
|C324|496727914943700|25|18.969|10|PASS|
|C415|496727924402500|13|22.855|0|FAIL COMPLETE_LINEAGE_REQUIRED, TAIL120_REWARD|
|C594|496727804496200|23|20.087|8|FAIL COMPLETE_LINEAGE_REQUIRED|
|C615|496727804405700|7|81.087|3|FAIL COMPLETE_LINEAGE_REQUIRED|

Cycles — максимум внутри одного фактического segment; все segments/counters/EXP/SP
и raw statuses есть в RESULT_DATA038.json и неизменённых evaluator outputs.
Existing036 ticket validation отдельно подтверждает B368:25 completed cast-launch+11
cycles в epoch496019532517400; C181:10 launches+27 cycles в epoch496727856092900.
C caster primaries115/176 имеют по2 наблюдённых completed launches, не заявлены как
пять native magic launches. Этот отдельный proof не подменяет failed FULL_SAVE verifier.

## Conservation и сохранённые отрицательные исходы

| Incarnation | Native births / terminal | Exact FULL_SAVE verified | NOT_VERIFIED |
|---|---:|---:|---:|
|B21672|792/792|339|453:382 late SQL boundary/UNKNOWN lineage,71 native fields parity|
|C35004|746/746|332|414:348 late SQL boundary/UNKNOWN lineage,66 native fields parity|

Ledger seq/run/source/raw hashes проверены; exporter drained, dropped0. Original cohort
verifier в обоих случаях FAIL на sealed payload != поздний SQL read; это не объявлено
corruption или доказанной потерей save. All-lifetime audit требует exact commit boundary,
а не только terminal или primary. SQL mismatch producer остаётся UNKNOWN.
Typed global DONE/COMPLETE, materialization STOPPED/retained0, background operations/
leases/transactions/transitions0 доказаны до shared pools shutdown в B и C.

037 archive502aa769ddebc410e33003f14e825f0ef7a9561838188938af50509e26bb0509
проверен, шесть selected inputs сохранены. Historical357:21cycles, gap118.193/tail0 FAILED;
current exact historical replay NOT_REPRODUCED/UNKNOWN. Historical253 route failure остаётся
UNKNOWN; новый Orc turn defect — текущий fixture, не установленная historical cause.
Historical566 death/lateSQL8294 vs sealed8293: FULL_SAVE NOT_VERIFIED, producer UNKNOWN.
Ранние invalid setXYZInvisible fixtures, old037 observer crash, setup402 READY→DEAD,
FULL_DEV/FULL2/FULL4/FULL5, invalid beforeStore debt premise, J1 matrix91/93 сохранены.
J1 matrix пересекалась с test-output rebuild: только intermediate, не final proof.

## Gates и завершение

A0/A1/A3/A4/A7 подтверждены указанными source/targeted gates. A2 имеет указанное
ограничение S15: native corpse handoff/pickup проверены, actual available Sweep body
в новом fixture отдельно NOT_RUN. A5 FAILED; A6 partial/NOT_VERIFIED. A8 delivery
связана с external publication/archive/cleanup receipts; GREEN_ACTION_LOOP038 не заявлен.

Final Jar-first: action5/5,current93/93,lifecycle11/11,native0369/9,Q15,inventory0356/6,
visible0328/8,debt0372/2,travel033r10/10; projection4,capacity2,model9,SQL11,compact2 PASS.
Jar/provenance каждого actual runner проверен; shadow development bin не используется.
Policy250/cap8/pop1280/scheduled10000,45/90/120,радиусы/evaluator и035/036/037 contracts
не менялись. MILESTONES M1–M10 сохранён; M2–M7 не реализовывались.

Own JVM0/GameLogin listeners0, stock STOP; четыре038 DB по122 tables/sessions0,
Maria PID7704/worktree сохранены. Удалений/force stops0; disk cleanup PAUSED.
Git использован по явному GIT038 разрешению; exact commands/paths — GIT_LOG038 и receipts.
mojibake-маркеры в изменённых файлах проверены;
escaped Cyrillic в изменённых файлах проверены.

Следующий возможный шаг: отдельная постановка для native lifetime lineage/save boundary
и причин stable-farm failures с сохранением protected contracts. Это рекомендация,
не автоматически начатый Goal и не утверждение, что native death надо запрещать.
