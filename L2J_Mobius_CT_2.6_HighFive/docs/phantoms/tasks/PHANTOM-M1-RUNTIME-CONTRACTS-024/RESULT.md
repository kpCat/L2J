# RESULT024 — FAILED, M1=OPEN

```text
TASK_RESULT=FAILED
STOP_AUTHORITY=TASK024_CONTRACT
BASE_SHA=819e3cea5baa64e6c429e450c8fc296874e37d1c
FINAL_CODE_SHA=ca3cbef9c03b695dcb9f84a734c5535b4ac72027
SOURCE_PATH_COUNT=11
TEST_PATH_COUNT=8
WALL_MINUTES_AT_REPORT=257.3
ENGINEERING_PASS=TARGETED_PASS_FULL_SUITES_NOT_GREEN
RETALIATION_PASS=FOCUSED_PASS_FINAL_RUNTIME_NOT_PROVEN
POSITION_STORE_PASS=FOCUSED_AND_OFF_AREA_PROBE_PASS
EARNED_PERSISTENCE_PASS=FAIL
SHUTDOWN_PASS=FAIL
LOCAL_RECOVERY_PASS=FAIL
FARM_A_PASS=FAIL
FARM_B_PASS=FAIL
COHORT_PASS=FAIL
LOOT_PASS=FAIL_WHOLE_COHORT_NOT_PROVEN
DEATH_PASS=FAIL_RECOVERY_FARM_NOT_PROVEN
SOFT_RETURN_PASS=FAIL_RETURN_UNCERTAIN
RESTART_PASS=FAIL_WHOLE_COHORT
CRASH_MATRIX_PASS=FAIL_AFTER_NATIVE_PENDING
AFTER_NATIVE_NATIVE_BYTES=PASS_PROFILE_452
AFTER_NATIVE_RECOVERY=FAIL_VERIFY_PENDING
AFTER_FINALIZE_NATIVE_BYTES_AND_FINALIZATION=PASS_PROFILE_187
AFTER_FINALIZE_GLOBAL_PENDING_GATE=FAIL_PRIOR_452
SERVER_M1_PASS=FAIL
REAL_FINAL_PASS=NOT_RUN
M1=OPEN
RUNTIME_EPISODES=8/8
PLANNED_CRASHES=2/2
RETAINED_AT_EARNED_STOP=INITIAL_5_FINAL_DRAIN_STOPPED_TRUE
PENDING_OWNED_STORE_AT_EARNED_STOP=0
PENDING_OWNED_STORE_AT_FINAL_STOP=1_PROFILE_452
FORCE_USED=0
PLAY_WRITES=0
OWNED_TEST_RESTORE=SHARED_TEST_UNTOUCHED_PRIVATE_LANE_PRESERVED
OWNED_JVMS_LISTENERS=STOPPED_0
```

Реализованы B/C → A → D и четыре дополнительных B-перехода с собственными RED. Код и relevant targeted checks проходят, но реальный whole-cohort product outcome не достигнут. Engineering results не повышают M1 до WAITING_FINAL_CLIENT.

B: capture сохраняет exact native XYZ вне anchor; POSITION_REQUIRES_NATIVE отделён от background simulation eligibility, прежние ordinal/schema сохранены. B09/B10 возобновляют только exact pending owner и повторно проверяют factual arrival. B11 использует existing stale-PENDING recovery. B12 возвращает только проверенный stock pre-World GeoEngine Z transform к committed native SQL Z, с guards identity/progress/owner/epoch/transition; anchor не подставляется.

C: topology/index прекращаются после native store/POST_STORE/lifetime release; blocking shutdown вынесен из configured/instance monitor под узкий shutdown claim. A: original attack/cast получает fresh root admission и не наследует чужую EARNED obligation; stale owner/epoch и delayed guards сохранены. D: локальная геометрия и capped alternatives, useful arrival до старого nonterminal deadline, bounded exact route exclusion вместо whole-area exclusion.

Новые API проверены исходниками; stock ThreadPool/EventDispatcher/combat engine/SQL schema и PlayerNativeEvidence thresholds не переписаны. Production11/18, новых production helpers0. Изменены8 test paths; дополнительных существующих test paths2: HeadlessPlayerTestEnvironment и NativeContextHandoffSuite; Contracts024DatabaseLane — новый test-only helper. Предыдущая формулировка ledger «third additional existing» считала новый helper, эта классификация уточнена здесь.

|Scene|Profile|Primary|Same epoch|Missing samples|Max idle s|Cycles|Rewards|Tail120|EXP/SP delta|Result|
|---|---:|---|---|---:|---:|---:|---:|---:|---|---|
|A|110|True|True|0|307.9|5|5|0|5565/265|FAIL|
|A|175|True|False|4|270.3|0|0|0|0/0|FAIL|
|A|187|False|True|0|75.9|13|14|7|2940/140|PASS|
|A|252|False|True|0|383.8|0|0|0|0/0|FAIL|
|A|275|False|False|5|38.7|0|0|0|0/0|FAIL|
|A|506|False|False|24|91.1|0|0|0|0/0|FAIL|
|B|110|False|False|13|76.8|0|0|0|0/0|FAIL|
|B|175|False|False|57|382.7|0|0|0|0/0|FAIL|
|B|252|True|True|0|310.9|4|5|0|1050/50|FAIL|
|B|832|True|True|8|105.5|26|28|3|2144/245|FAIL|
|B|879|False|True|0|349.1|3|4|0|233/25|FAIL|
|B|1179|False|False|28|186.7|0|0|0|0/0|FAIL|

Fighter/mage coverage подтверждено: B832/1179 class18 ELVEN_FIGHTER; остальные class25 ELVEN_MAGE.

Earned stop после сцен: proof A/B проверяет все6+6 выбранных записей, включая все9 разных профилей. Immutable SEALED snapshots, SQL и decoded receipts сохранены. A post-stop: 506 не exact/finalized; B: 832 не exact/finalized. SameDB restart A=4/6, B=4/6 exact+finalized; failures A110/506, B110/832. Missing final capture и законная background/native деятельность могут менять earned/death totals между наблюдениями: эти differences не объявлены доказанной DATA_LOSS и не «исправлены» счётчиками. Полная lossless линия не доказана — FAIL.

Shutdown scene runtime: initial drain FAILED, retained5, topology RUNNING; native ON_ATTACKABLE_KILL RESERVED ticket profile752/object268491177/epoch129470106273600 дал NATIVE_WORK_DRAIN_TIMEOUT. Затем stock final drain stopped=true за6119ms, JVM вышла штатно. Final stopped=true требует полного освобождения, но не стирает первый incident; healthy acceptance FAIL. Файл R4_HEALTHY_SHUTDOWN_STDERR.log назван до проверки, его имя не означает PASS. Automated proof retained=-1 означает отсутствующее подходящее initial healthy receipt, а не отрицательное количество entries.

Death: R4 native death452, без recovery farm; R5 native deaths175/187, без recovery farm. Использованы stock NPC20933/21320, native AI и scheduler, HP/EXP/items не записывались. Death observed, full death/return/farm gate FAIL. Own NPC cleanup подтверждён.

Soft return: native dry-path walk от44131 к38000, away80s; return request65c7d830-06ae-4701-ae4a-b49d99ef8d90 остался UNCERTAIN. Stock Synthetic watchdog закрыл session при свежем heartbeat. Запрос не replayed, fake REAL_LOGIN отсутствует. Возврат/rematerialization/farm не доказаны — FAIL.

Crash1: PID20936, profile452, exact AFTER_OWNED_NATIVE_STORE, exit72, REALcount0. SEALED native values полностью равны post-crash и restart SQL; row8423/context PENDING/owned receipt сохраняются. Повтор SELECT и stock Synthetic prelude не завершили recovery; final catchup FAILED_REPLAN_REQUIRED, failure catchup.baseline.conflict. Receipt не удалялся. AFTER_NATIVE recovery FAIL.

Crash2: PID22128, profile187, exact AFTER_OWNED_FINALIZE_COMMIT, exit73, REALcount0. Row8336→8340, context COMPLETED, exact native fields/full inventory+skills hashes совпали после restart и повторного SELECT. Per-profile AFTER_FINALIZE PASS; общий verifier FAIL из-за старого pending452. Повторное SELECT не равно повторному process restart: отдельный second-restart idempotence gate NOT_RUN из-за8/8 runtime cap.

Оба crash receipts содержат точную границу, epoch/object/PID, snapshot/SQL/pre-dump SHA и stack. Wrapper пишет STOCK_GRACEFUL_STOP_CONFIRMED после WaitForExit даже при разрешённом halt: для GameServer этих двух эпизодов это только exit confirmation, не healthy graceful PASS. LoginServer остановлен штатно.

27 targeted fault/mutation boundaries проверены existing TEST route, including BEFORE_PREPARE/PREPARE/native/FINALIZE. Все branch outcomes проверены fixture-level; actual process coverage только два авторизованных окна. Native FINALIZE crash2 доказан; index/POST_STORE reconstruction и дальнейший farm/liveness этого профиля отдельно не измерены и не объявлены PASS.

RED/GREEN и точные routes: FIX_LEDGER.md и raw RED_*/GREEN_*/REG_*.log. B3/3; C3/3 и shutdown8/8; Aactual HitTask2/2, stale3/3, dynamic6/6; Dlocality1/1 иarrival/route2/2, current-intent10/10; B09/B10/B11/B12 отдельные RED/GREEN, owned matrix27 PASS.

Сохранены неуспехи: GREEN_C01 некорректный repeat-stop fixture; REG_A_LIVING_FULL N02 совпадает с unchanged required base; случайный unsupported focus запустил full position suite6/12, это не full PASS. Старые a43 final/probe failures и pre-baseline ENVIRONMENT_GAP не удалены. Package self-check не считается server gate.

Оставшиеся blockers имеют fresh runtime evidence: stalled/missing natural cohort; RESERVED kill-event drain timeout; persisted AFTER_NATIVE VERIFY_PENDING452; death-return farm не появился; Synthetic return uncertainty. C dependency fix устраняет прежний NOT_RUNNING index, но не доказывает отсутствие прочих lifecycle defects. B exact native capture устранён локально, end-to-end recovery ещё открыт. Их нельзя закрыть снять guards/reset receipts/увеличить thresholds. Frozen code сохранён; full8 episodes и planned2 crash caps исчерпаны. Это остановка по TASK024_CONTRACT, не старому4repair limit. Не запускается следующая задача.

Reviewed GameServer SHA256: E2278F7E782A374CDC9932A43CE9F2F194359B48CD0814070CC97F5555C41D2A.
Reviewed LoginServer SHA256: D19D55B7AD0926A8BF706866A3C7B9EDD9062B68791B08937888AE84DA5C6059.
Config/data fingerprint: BC61EC50FCA9C1E266F86DE3C059771AA6CB1EE2E72AC7582661A9C90D876D26 (25767 files).
Preserved runtime: `C:\Users\ZBook\.codex\worktrees\m1-contracts-024\L2J_Mobius\L2J_Mobius_CT_2.6_HighFive\.phantom-local\contract024c\runtime`; DB `l2jmobiush5_localplay_contract024c`, loopback MariaDB3308. a isolated TEST and b probe также сохранены; d..h не использовались. PLAY/kpCat/night023 не менялись. Runtime и private credentials не входят в Git.

Проверенная команда запуска, реально использована для episodes6/7/8 и ValidateOnly после остановки:

```powershell
& 'C:\Users\ZBook\.codex\worktrees\m1-contracts-024\L2J_Mobius\L2J_Mobius_CT_2.6_HighFive\docs\phantoms\tasks\PHANTOM-M1-RUNTIME-CONTRACTS-024\Start-Reviewed024.ps1' -Background
```

Команда проверяет frozen SHA/JAR/config/data, использует тот же preserved clone и stock Start-LocalPlay. Она не обновляет runtime до documentation publication SHA. Сейчас default own JVM/listeners0; последние Game27704/Login19516 остановлены stock graceful, force0. При будущем human TestAdmin login используется существующий native REAL autoattach; такой login в этой задаче не выполнен.

Publication receipt и exact Git commands: PUBLICATION.md/GIT_COMMANDS.md. Final scope/encoding receipt: FINAL_AUDIT.json.

* mojibake-маркеры в изменённых файлах проверены; manual source matches0, raw read-only dry-path stdout содержит2030matches, сохранён без изменения.
* escaped Cyrillic в изменённых файлах проверены; manual source и raw evidence matches0.
