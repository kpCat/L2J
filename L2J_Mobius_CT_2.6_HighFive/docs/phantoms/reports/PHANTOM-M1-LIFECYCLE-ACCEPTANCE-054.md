# TASK054 — lifecycle acceptance, 2026-10-15

RESULT: **PARTIAL_VERIFIED; G2_CURRENT=PASS 2/2; G3_SCOPED=PASS run1–6; G4_CURRENT=BLOCKED_INPUT, full lifecycle NOT_VERIFIED; M1=OPEN; READY_FOR_M2=false**.

`OWN=C:/Users/ZBook/.codex/worktrees/phantom-dev/L2J_Mobius`; `M=OWN/L2J_Mobius_CT_2.6_HighFive`; `P=M/.phantom-local/contract051a`. BASE/remote target `experiment/m1-candidate007-observe008` подтверждены: `e03bcb5b8e74f434c208535fbc2e4584be03d139`. Detached checkout сохранён; ветка/worktree/БД не создавались. Пакет054 прочитан из MAIN без записи. Уточнение пользователя продолжает тот же TASK054 и задаёт новый максимум10h с резервом45min на завершение.

Run1–4: JAR `c7ff546c47f2b00177fc149d53e741b2f40cac4c66165d1039612e15dc91009f`, source `7216f4cfbfdd9d9a9cc9e4ace220070e400370e8`. FINAL recovery JAR `5b81fc1a58812dd338e3b9c449a136a986c7ebbf1de7905f0d530842f19cd4f8`, CODE_COMMIT `0568c128bab88a3402944979ba201eb3dfa3ee94`: jar-first/live recovery/обе G2/strict run5–6 PASS. Exact051 premain recorder не менялся: source `820ca3605b4b2f570fb6201840e17648ead05779c54a27fbe7d29085a2b82d11`, JAR `bb2d3e325e213facabc10144dfef5c43087de168beb8c10de5877529b6ffae0a`; RING512/disk512MiB сохранены. Proof assertions и historical052 PASS/053 FAIL сохранены. Новых агентов/attach-механизмов нет.

| Gate | Проверенный результат |
|---|---|
| A0 OWN_SAFE | До boot JVM/listeners0; pendingOwnedStores/online/otherSessions0; protected206 + original050 runtime304 hashes совпали. Все48 INSERT-блоков41 таблицы read-only PREBOOT054.sql byte-equal STOPPED053. Reimport/reset0. |
| A1 RECORDER | Raw053: ledger330220611B, ZIP139992897B, telemetry около98MB. Не доказано неизбежное переполнение короткого окна; recorder fix не делался. Ранний stock STOP при75% internal budget, прогноз на60–70%. |
| A4 G3 run1–4, c7ff | PASS: terminal births169/968/1295/1317; owning commits10015/194461/158866/244501. FULL_SAVE/GLOBAL_DRAIN/NATIVE_BOUNDARY_INTERVAL/FINAL_STOPPED_CUT/RUN_END PASS, dropped0, COMPLETE/retained0 до pools. P/CONSERVATION-RUN1–4-054.json. Эти PASS не переносятся на новую семантику. |
| Causal recovery fix | 10037/268484902 был загружен с HP0; тот же native epoch оставался мёртвым более785s, без регистрации death control. Существующий pulse теперь принимает unregistered solo corpse через action lease/current identity/epoch/healthy evidence; прежнее45s окно начинается при наблюдении, native timer не заменяется. Изменён один owner `PhantomOrdinaryDeathRecovery.java`. |
| RED/GREEN и affected | Штатный exact053a fixture runner по явному исключению пользователя; ownership/root/schema/export hash и SQL preimage сохранены. RED c7ff:7/8, только новый loaded-corpse08 FAIL; GREEN5b81:8/8. Checkpoint recovery8/8, visible-intent10/10, boundary2/2, native evidence5/5 PASS. Один новый regression в `PhantomBackgroundSuite.java`; старые assertions сохранены, добавлено bounded ожидание async reconciliation перед чтением durable state. Первые seed-guard/async failures сохранены. Все INSERT-блоки41 таблицы053a после runner byte-equal preimage; sessions/online0. Game/Login053a/reimport/reset/schema change0. |
| A4 G3 run5, новый JAR | PASS:671/671 terminal births,243987 owning commits,246764 ledger frames; все strict cuts/RUN_END, dropped0, COMPLETE/retained0 до pools. P/CONSERVATION-RUN5-054.json. |
| A4 G3 run6, новый JAR | PASS:241/241 terminal births,191369 owning commits,192392 ledger frames; все те же strict cuts/RUN_END, dropped0, COMPLETE/retained0 до pools. P/CONSERVATION-RUN6-054.json. Последние budget checks run5/6:314431246B/299476559B, tail завершён без потерь. |
| G2 groupA, новый JAR | Original CONTINUITY_V2 PASS6/6,380.5251117s,363 fresh samples,max gap1.1121017s, telemetry mailbox0. Frozen IDs10040/10092/10185/10285/10293/10311; cycles51/33/25/40/59/41, faults0. Primary10040/10092. P/ops/raw/RUN5-GROUP_A054. |
| G2 groupB, тот же JAR | PASS7/7,380.8325818s,364 fresh samples,max gap1.1130495s, telemetry mailbox0. Frozen IDs10009/10110/10118/10180/10188/10217/10449; cycles41/36/32/30/34/37/32, faults0. Primary10009/10180. Другая locality/history, новый boot/native epochs/Synthetic UUID; пересечение с groupA пустое. P/ops/raw/RUN6-GROUP_B3-054. |
| Live recovery, новый JAR | SHA-проверенные owning after-commit records:10293 epoch801346287145400 и10311 epoch801427386450100 загружены сHP0; native store через45.43/45.73s содержитHP81 и прежнийEXP429. Следующие legal epochs дали G2 cycles59/41; manual/SQL/time heals0. P/LIVE-RECOVERY054.json. |
| Live исходного10037 | Run6: прежний object268484902 сHP0 действительно наблюдался в DEATH_RECOVERY, epoch802900744971500; затем legal epoch802947078889000, HP>0, COMBAT/damage5, EXP2314 сохранён. P/ops/raw/RUN6-054-CENSUS1–2. |
| G4_CURRENT | Frozen/enrolled0; полного native→committed productive BG→новый independent Synthetic→useful return нет. В bounded current-JAR preflight всех1280 rows simultaneous calendar/admission/current-cursor/FARM candidates0 ещё до HP/Geo проверки. 10118 — current debt/preparation_capacity;10317 — calendar OFF и recovery capacity;11241 — DEAD/current debt. P/G4-CURRENT054.json и RUN6-054-G4-CURRENT. Старые route failures и31-step dry PASS не засчитаны как G4. |
| Invalid preparations | Сохранены до-enrollment отказы: G4 heartbeat File.Move Access denied; G2 setup12>10; мой caller ошибочно отверг ACCEPTED/pending. Все с cleanup PASS. Исправлен только caller по существующему API и STATUS-arrival; измеряемая groupB получила один проверенный stock setup1773<2000 до enrollment, исходные10-step/525s/calendar/whole-cohort guards сохранены. |

Читались GOAL/TASK/CONTEXT/ACCEPTANCE054, Agents/workflow/task standard/master plan/release contract, acceptance/scenarios041, отчёты052/053, actual ecology/history/locality/retention/Synthetic owners и exact051 recorder/control/stop/verifier. Локальные аналоги:051 startup recorder,052 focus/lifecycle,053 group/stop/strict. README/code-map/pattern-файлов модуля не найдено; root README прочитан. JDK25/Ant/High Five; другие хроники вне scope.

Обе группы сохранили исходный CONTINUITY_V2 SHA256 `15c153d30fe7f85719b1698059f4e28c9c7fa325caa420a02de7ac19cff35112`, native melee/caster и весь denominator. Source changes только `java/org/l2jmobius/gameserver/phantoms/background/PhantomOrdinaryDeathRecovery.java` и `test/java/org/l2jmobius/tests/phantoms/PhantomBackgroundSuite.java`; третий tracked path — этот RESULT. JDK25/Ant jar+compile-tests PASS;2 прежних runFinalization warnings.

FINAL054-SAFETY: exact own Game/Login0/listeners0; DB051a otherSessions/online/pendingOwnedStores0. Protected206 и original050 runtime304 hashes MATCH; старые STOPPED052/053 SHA MATCH. Полный read-only `P/STOPPED054.sql`10221390B, exit/footer verified, SHA256 `107e8381c4722c018aab67be54aa9d0efb5c2b982a44ee4a7d2f8cdd6611befd`. Накопительная051a и все raw/SQL сохранены, import/reset/force kill/чужие JVM/БД mutations0. Private orchestration использует прежние helpers и существующий ExternalHeartbeat; stock transport не редактировался.

Remaining M1: G4 BLOCKED_INPUT; G1 current native positives подтверждены группами, полный negative route отдельно NOT_REVERIFIED; прежние95/11/P07/G5/R1/O_SERVER сохранены без автоматического переноса на новый JAR, affected33/33 PASS. G6/crash72/73 REQUIRES_SEPARATE_AUTHORITY и не запускался; O_REAL/REAL_FINAL/USER_ACCEPTANCE остаются обязательными. М2 не запускалась; review ZIP не создавался.

<details><summary>Git: прямое разрешение пользователя/GOAL054; exact scope, normal commits/non-force push</summary>

Использованные команды; повторения опущены, пути нормализованы от OWN через S/T/R. Scope guard BASE→HEAD даёт только эти3 paths; старые untracked artifacts не включаются. Code fix0568c128bab и checkpoint cd1670125a5 опубликованы; финальный report commit проверяется HEAD=remote/tracked clean после push.

```powershell
$S='L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/background/PhantomOrdinaryDeathRecovery.java'
$T='L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomBackgroundSuite.java'
$R='L2J_Mobius_CT_2.6_HighFive/docs/phantoms/reports/PHANTOM-M1-LIFECYCLE-ACCEPTANCE-054.md'
git rev-parse --show-toplevel
git rev-parse HEAD
git branch --show-current
git status --short
git status --short --untracked-files=no
git remote -v
git worktree list --porcelain
git ls-remote origin refs/heads/experiment/m1-candidate007-observe008
git diff -- $S $T
git diff --numstat -- $S $T
git diff --check -- $S $T $R
git diff --name-only e03bcb5b8e74f434c208535fbc2e4584be03d139 HEAD
git add -- $S $T $R
git add -- $R
git diff --cached --check
git diff --cached --name-only
git commit -m 'phantom(task-054): preserve two complete scoped strict cuts'
git commit -m 'phantom(task-054): retain third strict cut and causal input findings'
git commit -m 'phantom(task-054): recover persisted native corpses on ordinary pulse'
git commit -m 'phantom(task-054): verify live corpse recovery and first current group'
git commit -m 'phantom(task-054): record two current groups and final scoped results'
git push origin HEAD:refs/heads/experiment/m1-candidate007-observe008
```

</details>

mojibake-маркеры в изменённых файлах проверены.
escaped Cyrillic в изменённых файлах проверены.
