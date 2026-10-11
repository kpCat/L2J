# TASK033R — результат исполнения

TASK_RESULT=PARTIAL. M1=OPEN. REAL_FINAL=NOT_RUN.
STOP_AUTHORITY=TASK033R_CONTRACT. Следующая задача не запущена.

Generic WALK теперь ищет обход из доказанных stock native corridors и разрешает
первый шаг от цели. Complete candidate selection сравнивает всё время/ресурсы
WALK/GK/доказанного deterministic RETURN_GK. Native actions исполняются через
существующие Player/GK/Unstuck/lifecycle interfaces. Прежняя нарезка прямого луча
на 2000-unit legs удалена. Совместная атака не запрещена.

Реальный obstacle route → native arrival/farm → пять последовательных kill/reward
cycles доказан на clean committed R3 для заранее выбранного primary123. Его
наблюдавшийся lifetime получил 10 cycles, 11 kills, 13 rewards, EXP+3458/SP+161.
Whole scene остаётся FAIL: календарные departures требуют полного lineage,
не у всех actors достаточно cycles/tail rewards. Completed RETURN→GK→farm
на actual random town/offset50 не наблюдался; unsafe landing не разрешён.
Whole subsystem shutdown также unhealthy из-за retained incidents.

| Флаг | Результат / граница доказательства |
|---|---|
| WALK_DETOUR_PASS | PASS: C83 strict actual-issued native geometry/current R3 и real route witness. |
| ROUTE_TO_FARM_PASS | Primary123, actual birth/MOVE/five consecutive native cycles; emergencyOFF. |
| ROUTE_STRATEGY_PASS | Complete-candidate/resource contracts и isolated native NORMAL GK PASS; bounded optimum среди проверенных candidates. |
| NATIVE_GK_CONTRACT | C63 T14/T21 actual NPC dispatcher, native free tariff, exact landing/final WALK/store PASS. |
| NATURAL_GK_ROUTE_TO_FARM | NOT_OBSERVED: C52 natural5,0/5 cycles; все исходные участники сохранены. |
| RETURN_CALLBACK_NATIVE_PASS | PASS: actual configured60000ms; cancel/demat/new epoch/death safe finalizer, C37/C56/C78.T13. |
| RETURN_GK_NATIVE_PASS | NOT_OBSERVED / PARTIAL_TRAVEL_MODES. Random landing envelope и onward route не доказаны. |
| RETURN_GK_AVAILABLE_THIS_RUNTIME | Typed UNAVAILABLE; random towns и native offset50 сохранены. |
| POSITIVE_NATIVE_FEE / SCROLL_EXECUTION | NOT_OBSERVED. Ресурсы не выдавались; fee0 — реальный stock low-level tariff. |
| EMERGENCY_ENABLED / OBSERVED | false / false; optional emergency execution не реализована. |
| COHORT_PASS | false; R2 C45=5/6, natural transport C52=0/5, final R3 C67=0/6. |
| NATIVE_SAVE_FIELDS | C76V all6 factual finalized receipts соответствуют stopped SQL; pending stores0. |
| SCENE_LIFETIME_DRAIN | C79 all6 DETACHED/outstanding0/pendingTimers0. |
| SAVE/CLEANUP_PASS | false: whole final subsystem drain retained3, не COMPLETE. |
| OWN_OS_PROCESSES | Game23688/Login24028 stock graceful STOP confirmed; Synthetic STOPPED. Final all-helper process guard — внешний cleanup result. |

## Исполнявшаяся версия

Required base: `df71140e659670e2e3d225605f64689a6d32d281`.
R1 `d4357795a74a8dacdb725df5d1f1ddbf1115d6c5`.
R2 `cdc9d8b514904cdc7632965b323228581d356068`.
R3 production `51cf051b3d20fbb83e6c0e16ce8c7bcad7af52cb`.
Последующий commit содержит evidence-validator correction и report/manifest artifacts; game source R3
и исполненный Jar не заменяются отчётным commit identity.

R3 GameServer SHA256 `EBC6918A38D634964028A171AFF62CB24926B5E7432040EB50B3A5E5CD620D46`.
LoginServer SHA256 `5A1C686A7E63FFB99E1A75DFDBDE261D7D204F187EB484FB6CD757C72EC830AF`.
Premain observer source SHA256 `54A7454BAD3D29DD98FB839EC55AD6B325F7EC903890A813725D0368AD898B7D`.
Observer Jar SHA256 `F6D35AA810302209F9FD40F01FBE21CB4072C873BE7B7D48FBD818D09A7525DF`.
Java25.0.4.101 / Ant1.10.17 / own MariaDB3308. C64 clean committed build PASS;
две прежние System.runFinalization warnings не менялись.

Branch публикации: `experiment/m1-candidate007-observe008`, normal refspec push.
Фактический final local=remote SHA записывается во внешнем `PUBLICATION.json`;
exact-path commit inventories — `FINAL_SCOPE_GUARD.tsv`, команды — `GIT_COMMANDS.md`.
Merge/force/history rewrite/branch deletion не выполняются.

## Текущие проверки

| Evidence | Результат |
|---|---|
| C63 finalTravel033r | 10/10 PASS, включая actual GK, decorated-WALK и closed stock NPC interaction. |
| C68 final matrix | 93/93 PASS на R3,18 unchanged existing routes; cooperative/earned callbacks сохранены. |
| C69 helpers028 | 24 PASS,1 existing SKIP (25 total). Это отдельные helper tests, не lifecycle11. |
| C72 composed native stop | S01 1/1 PASS; не выдаётся за11-case acceptance. |
| C73 affected visible-continuity032 | 8/8 PASS. |
| C74 original H16 travel031 | 1/1 PASS. |
| C75 unchanged local recovery | 4/7, прежние R03/R05/R15 failures. Baseline032 committed-Jar comparison имел те же failures. |
| C77 lifecycle acceptance | 11/11 PASS на R3. |
| C78 returnDeath033r / recorded R3 | Suite1/2: native T13 PASS; T19 measurement FAIL сохранён. |
| C81/C83 actual-issued-origin proof | C81 sampled origin FAIL; corrected strict validator C83 1/1 PASS на unchanged R3 Jar. |
| C76V stopped persistence | collected6; единственная validator error: healthy complete subsystem drain not proved. |

Genuine RED/GREEN: C12 full native buffer554/500 → bounded native corridor;
C26 unmanaged delayed Unstuck → captured owned ambient timer; C32 outstanding
reserved ticket → cancellable optional native timer; C35 dry but NSWE-illegal
segment → native pre-MOVE revalidation; C59 decorated full-query regression →
same bounded oracle; C61 actual sitting/NPC-interaction violation → stock
canInteract + dispatcher. Bootstrap/measurement failures C03/C08/C09/C27/C30/
C36/C40/C49/C66/C76/C78.T19/C81 сохранены отдельно и не выдаются за semantic RED или PASS.

## Native сцены и данные

C45 frozen R2:381.081s,6 initial participants19/27/28/40/57/106,primaries19/27,
370 samples,maxgap1.075s. Original19 actual birth(-89016,250024,-3568), three
native MOVE destinations, blocked full goal ray,11 cycles PASS. C56 SHA-bound
stock geometry/five-cycle validator PASS. Profile28 useful debt93.495s FAIL.
All6 native field saves совпали; global drain retained4 не пройден.

C52 natural transport R2:380.723s,initial62/154/193/254/300,primaries62/193;
0/5 cycles,no GK observed. Игровые календари, native resources, goals и XYZ
не менялись ради транспорта. Failed/missing actors не удалены из denominator.

C67 frozen R3:380.387241s,6 initial123/328/375/437/492/601,primaries123/328,
369 samples,maxgap1.0918475s,2 setup/control commands,0 telemetry mailbox
commands. Setup27 выбран по readonly current census/TTL до исходов; actual
materialized group зафиксирована отдельно до результатов. Original19 к этому
моменту не имел требуемого setup TTL540; его календарь не продлевался.

Primary123: object268485874,epoch403975958560900,actual pre-World birth
(-90072,248328,-3568),EXP24071/SP4286/adena2089/scroll736=0. Full raw birth,
100ms travel ledger, native terminals и finalized payloads сохранены.
Birth SHA256 `8a168c0907d773a9520ce2f088799f3638cbb0a37fb7a555635b9536125fe4f3`;
ledger SHA256 `410120427374e7b04e72bb4412cf1a023fe0816149541102548bfb5677a1f75f`.
Native segment maxIdle40.112s,tail120Rewards4,cycles10; strict whole-life
COMPLETE_LINEAGE_REQUIRED остаётся FAIL. Initial437 также достиг5cycles, но
не заменяет preselected primaries. Остальные3/4/3/1cycles и departures сохранены.
Final source clone уже до Synthetic воспроизводит bounded-inventory incidents
objects268484533/268484616, присутствовавшие на неизменённом baseline C07.
Current final global drain retained3; эти вне-slice payloads не SQL-heal/reset.

C78/C81 показали ошибку измерения issued origin: первый sample нового MOVE уже
на8units дальше фактического начала(-89048,249352,-3568). Проверка нового луча
из sampled(-89056,249360,-3568) дала nativeDirect=false. C83 сохраняет оба
результата: actual issued ray nativeDirect=true, sampled ray=false. Validator
теперь использует фактический birth/previous native destination, как stock
Creature.updatePosition snap перед следующим leg; проверяет observed idle pose,
принадлежность sampled XY этому segment с integer rounding≤2 и неизменённый
strict native height/dry/NSWE. Raw XYZ не переписываются. Все9 фактических MOVE
рёбер, blocked full goal ray и5 последовательных cycles PASS. Никакого игрового
semantic fix после R3 нет;93+11 на R3 не повторяются из-за test-only correction.
Cycle5 actual XYZ(-86198,253964,-3731),EXP25929/SP4373,kill5/reward7/target12;
nativeFirstUnprovenReason=NONE. Совместные rewards не запрещены.

## Ограничения и независимое ревью

GK proposals ≤7, complete candidates ≤8, ingress/egress только direct-proven.
Это bounded поиск, не world-wide shortest path. Нет proof всей random native
RETURN landing envelope, completed RETURN→GK→farm, paid positive fee runtime,
полного natural group lineage, full away/bg/return, crash recovery или REAL_FINAL.
M1 gate остаётся OPEN. Inventory/persistence/whole-M1 debt вне scope не чинится.

Внешний архив:
`C:\Users\ZBook\.codex\visualizations\2026\10\10\01a127b2-1e6e-7f12-9af8-3f3748fe3047\TASK033R-review`.
Там raw evidence/ops/source/Jars, SHA-256 copy manifest, exact disk inventory,
retained database inventory, PUBLICATION.json и factual CLEANUP_RESULT.json.
Полные runtime trees/geodata/DB exports/credentials не дублируются. Базы a/b/c/t
retained вне worktree, предыдущие базы и shared service не удаляются.
Правила/единственная normal removal команда: `CLEANUP033R.md`.

Повтор: existing Run033R.ps1 Build → owned Update/SyncNative/Start exactSha →
readonly census → Observe Scene380/native pre-publication setup → unchanged
Evaluate → stock stop/export → existing persistence validator. Verify Matrix,
LifecycleCompletion027Suite focus acceptance, VisibleContinuity032Suite,
NativeContextHandoffSuite focus travel031, LocalFarmRecoverySuite,
TravelPlanning033RSuite focus finalTravel033r/returnDeath033r. Перед rerun
нужна точная owned path/DB authority для выделенного reusable worktree;
автоматический новый worktree или новая задача не создаётся.

Mojibake-маркеры в изменённых файлах: отдельная final проверка перед commit,0 совпадений.
Escaped Cyrillic в изменённых файлах: отдельная final проверка перед commit,0 совпадений.
