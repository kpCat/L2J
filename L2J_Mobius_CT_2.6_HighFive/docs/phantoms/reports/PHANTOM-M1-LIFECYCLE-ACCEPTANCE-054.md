# TASK054 — lifecycle acceptance, 2026-10-15

Текущий checkpoint: **IN_PROGRESS; G2_CURRENT=NOT_VERIFIED; G3_SCOPED=PASS run1–4 на c7ff; G4_CURRENT=BLOCKED_INPUT до enrollment; M1=OPEN; READY_FOR_M2=false**.

`OWN=C:/Users/ZBook/.codex/worktrees/phantom-dev/L2J_Mobius`; `M=OWN/L2J_Mobius_CT_2.6_HighFive`; `P=M/.phantom-local/contract051a`. BASE/remote target `experiment/m1-candidate007-observe008` подтверждены: `e03bcb5b8e74f434c208535fbc2e4584be03d139`. Detached checkout сохранён; ветка/worktree/БД не создавались. Пакет054 прочитан из MAIN без записи. Уточнение пользователя продолжает тот же TASK054 и задаёт новый максимум10h с резервом45min на завершение.

Run1–4: JAR `c7ff546c47f2b00177fc149d53e741b2f40cac4c66165d1039612e15dc91009f`, source `7216f4cfbfdd9d9a9cc9e4ace220070e400370e8`. Новый recovery JAR `5b81fc1a58812dd338e3b9c449a136a986c7ebbf1de7905f0d530842f19cd4f8` прошёл jar-first проверки, live ещё REQUIRED. Исходный exact051 premain recorder: source `820ca3605b4b2f570fb6201840e17648ead05779c54a27fbe7d29085a2b82d11`, JAR `bb2d3e325e213facabc10144dfef5c43087de168beb8c10de5877529b6ffae0a`; RING512/disk512MiB сохранены. Proof assertions и historical052 PASS/053 FAIL не менялись. Новых агентов/attach-механизмов нет.

| Gate | Факт checkpoint |
|---|---|
| A0 OWN_SAFE | До boot JVM/listeners0; pendingOwnedStores/online/otherSessions0; protected206 + original050 runtime304 hashes совпали. Все48 INSERT-блоков41 таблицы read-only PREBOOT054.sql byte-equal STOPPED053. Reimport/reset0. |
| A1 RECORDER | Raw053: ledger330220611B, ZIP139992897B, telemetry около98MB. Не доказано неизбежное переполнение короткого окна; recorder fix не делался. Ранний stock STOP при75% internal budget, прогноз на60–70%. |
| A4 G3 run1 | PASS/exit0:169/169 terminal births,10015 owning commits; FULL_SAVE/GLOBAL_DRAIN/NATIVE_BOUNDARY_INTERVAL/FINAL_STOPPED_CUT/RUN_END PASS, dropped0. DONE/COMPLETE до pools, retained0. P/CONSERVATION-RUN1-054.json. |
| A4 G3 run2 | PASS/exit0:968/968 terminal births,194461 owning commits; все те же strict cuts и RUN_END PASS, dropped0. Stock STOP начат при228787354B; DONE/COMPLETE до pools, retained0. P/CONSERVATION-RUN2-054.json. |
| A4 G3 run3 | PASS/exit0:1295/1295 terminal births,158866 owning commits; полный strict cut/RUN_END, dropped0, COMPLETE/retained0 до pools. P/CONSERVATION-RUN3-054.json. |
| A4 G3 run4 | PASS/exit0:1317/1317 terminal births,244501 owning commits; все strict cuts/RUN_END, dropped0, COMPLETE/retained0 до pools. P/CONSERVATION-RUN4-054.json. |
| Causal recovery fix | 10037/268484902 был загружен с HP0; тот же native epoch оставался мёртвым более785s, без регистрации death control. Существующий pulse теперь принимает unregistered solo corpse через action lease/current identity/epoch/healthy evidence; прежнее45s окно начинается при наблюдении, native timer не заменяется. Изменён один owner `PhantomOrdinaryDeathRecovery.java`. |
| RED/GREEN и affected | Штатный exact053a fixture runner по явному исключению пользователя; ownership/root/schema/export hash и SQL preimage сохранены. RED c7ff:7/8, только новый loaded-corpse08 FAIL; GREEN5b81:8/8. Checkpoint recovery8/8, visible-intent10/10, boundary2/2, native evidence5/5 PASS. Один новый regression в `PhantomBackgroundSuite.java`; старые assertions сохранены, добавлено bounded ожидание async reconciliation перед чтением durable state. Первые seed-guard/async failures сохранены. Game/Login053a/reimport/reset/schema change0. |
| G4 preparation | Actor ещё не enrolled. Фактический focus довёл10117/10165 до текущей readiness и native полезных cycles;11292 остался preparation_capacity. Combat/неактуальный cursor не выданы за корректный enrollment. Первый bounded Synthetic закончился штатно, без продления lease. |
| G4 следующий preflight | Prospective10180, initially HP155/level5, ACTIVE до23:28UTC. Обычный BG до enrollment переместил его с45787/41316 в48712/55466. Два новых stock dry routes отклонены DRY_BIDIRECTIONAL_SEARCH_ABSENT/expanded60001; MOVE по ним0, actor не заменялся после enrollment, поскольку enrollment0. Прежний проверенный37-step west route больше не соответствует живому target. |
| G2/G1 | Ещё не измерялись. Первый natural cluster имеет календарную границу до конца380s; denominator не фильтровался. Идёт отдельная east preparation; original Scene053 отличается только054 labels, CONTINUITY_V2 SHA256=15c153d30fe7f85719b1698059f4e28c9c7fa325caa420a02de7ac19cff35112. |

Читались GOAL/TASK/CONTEXT/ACCEPTANCE054, Agents/workflow/task standard/master plan/release contract, acceptance/scenarios041, отчёты052/053, actual ecology/history/locality/retention/Synthetic owners и exact051 recorder/control/stop/verifier. Локальные аналоги:051 startup recorder,052 focus/lifecycle,053 group/stop/strict. README/code-map/pattern-файлов модуля не найдено; root README прочитан. JDK25/Ant/High Five; другие хроники вне scope.

Private054 orchestration переиспользует исходные053 helpers с отдельными evidence paths. Все raw/SQL остаются private. Незатронутые95/11/P07/G5/R1/O_SERVER повторно не запускались; прежние live PASS не переносятся на новый recovery JAR. G6/crash72/73 не запускались; O_REAL/REAL_FINAL/USER_ACCEPTANCE требуют отдельной личной приёмки. М2 не запускалась; review ZIP не создавался.

Git разрешён прямой командой пользователя и GOAL054: read-only `rev-parse --show-toplevel`, `rev-parse HEAD`, `branch --show-current`, `status --short`, `status --short --untracked-files=no`, `remote -v`, `worktree list --porcelain`, `ls-remote origin refs/heads/experiment/m1-candidate007-observe008`; публикация только exact-path add/ordinary commit/non-force push и diff checks. Старые untracked artifacts не включаются.

mojibake-маркеры в изменённых файлах проверены.
escaped Cyrillic в изменённых файлах проверены.
