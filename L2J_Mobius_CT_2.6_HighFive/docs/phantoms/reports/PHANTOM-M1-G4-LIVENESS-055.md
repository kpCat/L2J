# TASK055 — causal eligibility и native regeneration

RESULT: **PARTIAL_VERIFIED; G4_FULL=NOT_VERIFIED; M1=OPEN; READY_FOR_M2=false**.

`OWN=C:/Users/ZBook/.codex/worktrees/phantom-dev/L2J_Mobius`; `M=OWN/L2J_Mobius_CT_2.6_HighFive`; `P=M/.phantom-local/contract051a`. BASE/remote перед работой: `2caced021d883b6191de3ddc4f3162e523fe2be1`, target `experiment/m1-candidate007-observe008`. Сохранён detached HEAD. Вариант OWN без `\` перед `.codex` из сообщения отсутствует; использован существующий OWN из GOAL. MAIN — только read-only task input. Новых worktree/branches/subagents/import/reseed/reset/БД нет.

Прочитаны пакет055, Agents/master/workflow/task standard/release contract, SCENARIOS041 S_LIFE, RESULT052–054 и адресные population/ecology/readiness/native-context/retention/materialization/constructor/timer/store owners. Root README прочитан; README/code-map/pattern-файлы модуля и AGENTS выше модуля не найдены. Переиспользованы exact051 recorder/control/stock stop/verifier, Life054, existing053a fixture runner. JDK25/Ant, только High Five; tracked allowlist — Player.java, один regression suite, этот RESULT.

| Срез до первой Synthetic | Population READY | Calendar ACTIVE | Current-ready всего / ACTIVE | Admitted | Физические native |
|---|---:|---:|---:|---:|---:|
| CENSUS1, 15:52:44 UTC |1280|641|19 / 0|0|1|
| CENSUS2, следующая минута |1280|641|0 / 0|0|2|
| CENSUS3, следующая минута |1280|641|2 / 0|0|2|

Это NONATOMIC_READ_ONLY runtime cuts, не поздний SQL вместо live. Все прошедшие одновременно ACTIVE/current/admitted/FARM:0. Calendar first-failure639; current fence из оставшихся641 отсекает641; примеры10006/10010/10011. Background states CENSUS3: READY429, DEAD849, MATERIALIZED2; FARM+PNC839. Native branch: World2→FARM1→HP>0/earned/current epoch1→без hard action hold0 в этом кадре; 10037 исключён по FARM,10185 удерживается законной работой. Initial/request flags existing census экспортирует для выбранных identities: отдельный более поздний consistent SQL cut даёт initialComplete115/1280, requestPending=false97/1280, HP>0 423/1280; эти числа **не** выданы за atomic frozen runtime waterfall. Детали/ограничение: `P/ELIGIBILITY055.json`, `WATERFALL055-*`, raw `CENSUS1–3-055`.

Причина нуля не равна отсутствию lawful native: `registerDue()` допускает точный `NATIVE_CONTEXT_HANDOFF` при принадлежащем history request и действительном locality demand; admission snapshot продолжает показывать общий ecology fence. Уже живой owner удерживается native ownership/retention; ecology не выполняет вторую историческую запись поверх него. Для нового обычного materialization по-прежнему нужны server readiness и current locality; никакой bypass не добавлен. Test-only selector теперь отдельно распознаёт уже World-present earned owner: старый predicate0, actual native10185; ordinary READY10118 и неверная epoch отвергнуты (`P/PREFLIGHT-PREDICATE055.json`, offline controls).

| Truth table CENSUS3 | READY10118 | Уже native10185 |
|---|---|---|
| Calendar / initial / request |ACTIVE / true / pending|ACTIVE / true / pending|
| Cursor / horizon |29867940 / 29867995|29867955 / 29867995|
| Admitted / scheduler |false / SLEEPING|false / NEARBY_PERCEPTIBLE|
| World / HP / epoch |absent; live HP неприменим|present /216 /807769549229700|
| FARM+PNC / pins |true / none|true / ACTIVE_ACTION, RECENT_HUMAN|
| Background transition |history debt/request fence|native writer/context, до законного release BG запрещён|

10118 продвинул cursor29867912→29867940, inner history продолжалась под budget16; у10185 `_liveOwner=true`, worker RUNNING, history cursor оставался fenced. Это progress/ownership, а не доказанный потерянный permission notification. Подробные preparation/progress/signal facts — исходные `LIFE055A/full-cohort-samples.jsonl`; реальный BG due/commit после release не измерен, contract300–900s не ускорялся.

До первой Synthetic сохранены provisional queue и затем final order **10185,10118,10188,10293,10311,10110,10180,10449**; final включает уже native10185 из второго read-only среза. Исходный provisional файл сохранён. Единственный измеряемый профиль10185/object268486024/epoch807769549229700; replacement0. Birth FULL items/skills/goal/context и SHA-pinned register lineage: `P/IDENTITY055.json`; consistent enrollment cut: `ops/raw/LIFE055-ENROLL-SQL`. Native21 cycles до Synthetic; episode `433a1d61-4818-42bc-a0b8-71264507d263`, raw `LIFE055A`. GeoEngine bidirectional25-step route44131/42673↔44131/36800 PASS; walking не выполнялся.

**G4_NOT_VERIFIED; первый не пройденный переход — settled departure:** в объявленном departure-controller окне230s genuine combat не дал settled cut. Offline найден ранний пригодный кадр на45.253s, **до** запуска контроллера: он пропущен при подготовке; это отклонение исполнения, не доказательство вечного product idle/hold. Дополнительно independent return input не подготовлен: cached AUTONOMOUS и default новой Synthetic начинаются44131/42673, внутри couldKnow/prewarm исходного спота. Не доказаны post-release BG position и отсутствие ложного fallback anchor; нельзя объявлять ни независимый stock walking return, ни невозможность любого законного G4. Ни teleport, ни снятия pins, ни изменения observer policy ради PASS нет. Полного owned absence→productive BG→independent return нет; второй G4 target/session не подбирался. `P/SESSION055.json`, `DEPART055.log`, исходные frames/cleanup PASS сохраняются.

**Один доказанный product defect и minimum fix.** Strict run1 нашёл10711/object268490175/epoch808225401932700: prepared rawMP22.1739936, AFTER/PNC/SQL MP22, FINALIZED rawMP23.5310856; commit72653, receipt `abd31a170bb22d03e8948928b42990df1a7b0504c0c6aa4ba24e3699bb74e9d1`. Constructor max-vital getters публиковали обычный regen **до** `beforePublication` native owner; этот writer обходил drain. Regression на BASE JAR воспроизвёл SEALED MP19→20.14048 (RED1/1 FAIL). В Player constructor owner/load ticket перенесён перед первыми max-vital reads/tasks; существующий cleanup failure path сохранён. GREEN MP19→19 при SEALED и штатный рост после reopen; full handoff16/16 PASS. Новых API/таймеров/абстракций нет; rates/manual heals/clock/TTL/cap/retention/network/client не менялись. JVM startup использует уже принятый exact051 premain recorder с новым private input spec; новых JVM mechanisms/options нет.

Старый JAR `5b81fc1a58812dd338e3b9c449a136a986c7ebbf1de7905f0d530842f19cd4f8`; новый `0d0762d490be662146ea3436cb0c96efee347524a1f28f16ef041e32f3dde774`. Ant jar+compile-tests PASS, две прежние runFinalization warnings. JAR parity: изменены только Player.class и два его вложенных класса (`CLASS_PARITY055.json`); recorder source `820ca360…`, JAR `bb2d3e32…`, RING512/disk512MiB неизменны.

CODE_COMMIT `abb92f070ac4c1788708c156f10e1996be9c04d3`. Current-JAR affected **95/95**, lifecycle **11/11**, focused G1 boundaries/writer **2/2**, handoff **16/16**, causal GREEN **1/1** PASS; `P/ops/tests055/GREEN055*`, `AFFECTED055.json`. BASE/current-before-fix matrix **94/95** сохранена: N02 cooperative второй участник умер до reward, firstEXP401/secondEXP0; новый candidate прошёл этот route без изменения assertions. Этому отдельному FAIL не приписан причинный fix. Existing exact053a guards/lock/export hash сохранены, все48 INSERT-блоков41 таблицы после tests byte-equal preimage (`FIXTURE-RESTORATION055.json`); Game/Login053a/reboot/reimport0.

Run1 normal stock stop: Game23264/Login29240, RUN_END1272 births/dropped0; all terminal, GLOBAL_DRAIN и FINAL_STOPPED_CUT PASS, **FULL_SAVE/NATIVE_BOUNDARY_INTERVAL FAIL** из-за указанного MP mismatch (1309 native checks PASS,1 FAIL). Этот FAIL не переписан. Raw `STARTUP051a-3ab2f6b7dc934d388432ebfbef8b3e8a`, `CONSERVATION-RUN1-055.*`, `RUN1-055-{STOP,SQL}`. Старые G2_CURRENT0542/2 и strict0546/6 сохранены; не объявлены автоматически новой coverage изменённого candidate.

Run2 — единственное дополнительное live confirmation **после causal RED→fix→GREEN**, не G5/R1/G2 replay: Game12596/Login28324, sourceabb92f0/JAR0d0762. Для **той же10711/268490175** записаны native epoch810512143844000, FINALIZED MP/rawMP22/22, receipt `7d3488682d043b1f5852041c476011d76a51bc716059ca2d58069d7f0aff3fbd`, owning `finalizeOwnedStore` commit с canonicalChecked/nativeAfterMatched=true (`LIVE-IDENTITY055.json`). Это live native save boundary; useful World-return10711 не заявлен.

Её отдельный predeclared observation `4e95c9cc-ddd5-412f-b7fe-8c1ff9d0be2c` стартовал через stock setup46253/41091. History продвинулась29868000→29868030, inner cursor29868033/target29868039; committed position ушла39050/41882, physicalDemand=false→native_context.required при работающем worker. Прямой bidirectional route отказал на native edge44456/41336/-3416→44456/41288/-3456 (forward/reverse=false); один VIA input отклонён **до** маршрута guard DRY_VIA_HOP_BOUND2000. MOVE0, target replacement0, finite lease не продлевалась. Это точная readiness/demand/Geo цепочка, не вывод о недостижимости всех маршрутов. Raw `REGEN055LIVE`, `REGENLIVE1/2-055`, `REGEN055-APPROACH-{DRY,VIA}`; stock cleanup PASS.

**Current-JAR strict run2 PASS:**1270/1270 terminal births,131067 owning commits,136215 ledger frames;1276 native checks PASS/0 FAIL, stopped verifier exit0, FULL_SAVE/GLOBAL_DRAIN/NATIVE_BOUNDARY_INTERVAL/FINAL_STOPPED_CUT/RUN_END PASS, dropped0. Proof `CONSERVATION-RUN2-055.json` SHA256 `79f2440b17b476473287631f20eacd63195cf7f08634829dfbbbf0a55dd4c233`; raw `STARTUP051a-b531e67a262e427b81a254de32d16a30`, `RUN2-055-{STOP,SQL}`. Новый PASS не заменяет run1 FAIL и не закрывает consolidated G3 с G6/recovery.

Preflight/final: protected206/original050304 MATCH; own JVM/listeners/online/pending/other051sessions0. PREBOOT05548 INSERT-блоков byte-equal STOPPED054; его SHA `107e8381…` сохранён. Накопительная051a сохранена без импорта. Run1 full stopped dump `STOPPED-055.sql`, SHA256 `8438afda46ab762a693b1c1d9a8c14a3952a90e144fe15dc7703a94b337d86bb`; final usable immutable `STOPPED-LIVE-055.sql`,10225081B, SHA256 `a4fab8180845e4f8aa753fa5e0810c09a043a6564b4514950b918ae0da46f248`, complete footer/exit0. `P/Safety-055.json` и exact pre-stop receipts. Оба runs завершены задолго до512MiB cap; бюджет5h с35min reserve соблюдён. Ordinary module dist/libs содержит оба свежих JAR; старый standalone R1 не выдаётся за новую проверку.

G6 только read-only authority оценка: exact051 control не допускает crash modes, recorder явно бросает TASK050_NORMAL_ONLY до старого separate-lane блока. PREPARED/FINALIZED witnesses не дают полномочия на72/73. G6/crash/recovery не запускались; G5/R1/O_SERVER/P07 и G2 группы не повторялись. Remaining M1: полный G4, отдельно разрешённые G6/recovery/idempotence и consolidated G3, актуальность оставшихся final gates, O_REAL/REAL_FINAL/USER_ACCEPTANCE. M2 не начат, review ZIP не создавался.

Команды: Ant `-Dbuild=.phantom-local/contract051a/build055-red compile-tests`, `-Dbuild=.phantom-local/contract051a/build055-green jar compile-tests`; один javac sync изменённого test class; unchanged `Verify053.ps1` Matrix/Test с exact JarPath/BuildRoot/ReportRoot; Control032 Start/Collector(Census,Probe032,StopMonitor)/DryPath/Export/Stop и Update только своей051a; unchanged `verify_commit_run039.py`; read-only mariadb consistent snapshots/dumps, production codecs, protected SHA и два text scans. Private orchestration источники сохранены в `P/ops/task055`; original raw/FAIL/SQL не переписаны. Самопроверка полного diff выполнена без subagents по GOAL.

Git использовался по прямому разрешению /goal, GOAL055 и Agents.md. Exact allowlist: только `S`, `T`, `R` ниже; normal source commitabb92f0 и финальный report commit. Публикация — non-force push в указанный target; финальные SHA/HEAD=remote/tracked clean фиксируются после push в `P/PUBLICATION055.json`, без самоссылки SHA отчёта. Старые untracked artifacts не добавлялись.

<details><summary>Точные Git-команды (от OWN; повторения опущены)</summary>

```powershell
$S='L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/model/actor/Player.java'
$T='L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomNativeContextHandoffSuite.java'
$R='L2J_Mobius_CT_2.6_HighFive/docs/phantoms/reports/PHANTOM-M1-G4-LIVENESS-055.md'
git status --short
git status --short --untracked-files=no
git rev-parse HEAD
git -C C:\Users\ZBook\.codex\worktrees\phantom-dev\L2J_Mobius rev-parse HEAD
git branch --show-current
git remote -v
git ls-remote origin refs/heads/experiment/m1-candidate007-observe008
git diff --stat -- $T
git diff --numstat -- $S
git diff --check -- $S $T
git diff -- $S $T
git diff --name-only 2caced021d883b6191de3ddc4f3162e523fe2be1 HEAD
git diff --cached --name-only
git add -- $S $T
git diff --cached --check
git diff --cached --stat
git commit -m 'phantom(task-055): bind native owner before constructor regeneration'
git diff --check -- $R
git add -- $R
git diff --cached -- $R
git commit -m 'phantom(task-055): record liveness findings and current native save proof'
git push origin HEAD:refs/heads/experiment/m1-candidate007-observe008
```

</details>

mojibake-маркеры в изменённых файлах проверены.
escaped Cyrillic в изменённых файлах проверены.
