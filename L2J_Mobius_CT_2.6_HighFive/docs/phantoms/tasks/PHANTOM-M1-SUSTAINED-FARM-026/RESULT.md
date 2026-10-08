# RESULT026

TASK_RESULT=FAILED. Непрерывный farm всей исходной cohort не подтверждён: обе финальные сцены дали 3/8. Во втором первом shutdown подтверждён незавершённый native drain. M1=OPEN; WAITING_FINAL_CLIENT не выставляется.

```text
STOP_AUTHORITY=TASK026_CONTRACT
BASE_SHA=6ebe1d93f4ee168cd8952f0416dc920f26c430ac
FINAL_CODE_SHA=cb2d9b08d96e4f2386836b40d5ae87ceba9fcf63
REMOTE_HEAD=publication HEAD of experiment/m1-candidate007-observe008; exact verified SHA in final chat receipt
WALL_MINUTES=308 at final artifact seal15:12:44Z; first command10:04:45Z; exact publication completion time in final chat receipt; hard stop16:04Z
FIRST_RUNTIME_PROBE_MINUTE=36; full-server proof by10:40Z
PRODUCTION_PATHS=8 exact files; source/test total14; FINAL_SOURCE_ALLOWLIST.txt
N02_S12_FIRST_DIVERGENCE=actual secondary HP damage and EXP were present, but native death recipient was excluded by killer/main-damage-dealer comparison
N02_S12_PAIRED_RESULT=healthy paired E01/E02 BASE1/2,CANDIDATE2/2; S12 BASE2/3,CANDIDATE3/3; legal N02 native-kill pair1/1,1/1
CALLBACK_FIRST_PRODUCER=stock Attackable death -> ON_ATTACKABLE_KILL -> exact owner EVENT24354, epoch197184915592600; SUBMITTED future proven; delayed execution cause UNKNOWN
CALLBACK_DRAIN_PASS=false; final A PASS, final B first stop FAIL
FARM_A_PASS=false; primaries110/142 FAIL; cohort3/8
FARM_B_PASS=false; primaries260/278 PASS; cohort3/8
COHORT_PASS=false; original denominator8 per scene, all16 rows retained
RESOURCE_RECOVERY_PASS=focused2/2; whole cohort FAIL
LOCAL_ACTION_PASS=focused3/3; whole cohort FAIL
LOOT_PASS=PARTIAL_UNPROVEN; positive native loot and A inventory witnesses, whole-group proof incomplete
WHOLE_GROUP_PERSISTENCE_PASS=false; final A SEALED canonical7/8, final B1/8
COLD452_REGRESSION=PASS onR6, production recovery plus two actual sameDB restart
SAME_DB_RESTART1=d PASS8/8; c FAIL durable state MATERIALIZED->READY for142/260/404, native fields/items/skills preserved
SAME_DB_RESTART2=d PASS8/8; c PASS8/8 against reconciled restart1 baseline
RECOVERY_COMMIT_XYZ_EXACT_PASS=PASS actual R4 e; finalR6 full-server R03 NOT_RUN, R6 focused boundary2/2
POST_RECOVERY_XYZ_FIRST_WRITER=UNKNOWN exact Java writer; ordinary native movement/progression observed after admission release
AFTER_FINALIZE_CRASH_PASS=NOT_RUN
DEATH_RETURN_FARM=NOT_RUN as a dedicated final gate
SOFT_RETURN_FARM=NOT_RUN as a dedicated final gate
RETAINED_AT_FIRST_HEALTHY_STOP=A seven original owners DETACHED/outstanding0/timers0; B first stop retained materialization entries8 and original OPEN owners
PENDING_OWNED_STORE=0 on own a,b,c,d,e,h after cleanup; does not rescue missing final SEALED proof
OWNED_JVMS_LISTENERS=STOPPED; 7777/2106/9014 have no owners; f/g NOT_CREATED
FORCE_USED=no emergency process kill; one authorized planned Runtime.halt72 AFTER_OWNED_NATIVE_STORE on e
PLAY_WRITES=0
REAL_FINAL_PASS=NOT_RUN
M1=OPEN
```

## Финальные natural сцены

Обе сцены на frozen R6 и одинаковом observer/pilot input. Полный stock GameServer, scheduler, NPC/AutoPlay/AutoUse/PlayerAI; существующий Synthetic observer без REAL_LOGIN и ручного управления Phantom. Исходные IDs026:110,142,175,260,275,278,404,447. Они получены при первом native наблюдении026 и сохранены для обеих сцен, включая отсутствующих. Исторические025 IDs не объявлены продолжающейся natural lifetime: их отдельные неудачные discovery requests сохранены.

Сцена A: contract026d,380.7322529s, заранее primary110/142;121 samples, максимальный gap4.535s. Сцена B: contract026c,381.3929314s, заранее primary260/278;133 samples, gap4.050s. Нативные epochs внутри каждой живой строки не менялись; отсутствующие epoch0 не являются lifetime. Между сценами epochs не склеены.

В таблицах EXP/SP — доказанные native evidence deltas, tail — native rewards в final120s, idle — максимальный интервал без доказанного useful progress. Каждый FAIL остаётся FAIL при положительной ранней статистике.

| A profile | object / epoch | cycles | EXP / SP | tail120 | maxIdle s | outcome / доказанный факт |
|---|---|---:|---:|---:|---:|---|
| 110 primary |268485779 /195981072148600|1|525 /25|0|376.83|FAIL: sticky PHASE_DEADLINE; final travel.route_absent|
| 142 primary |268486041 /195981089138900|11|9649 /455|4|147.19|FAIL: PHASE_DEADLINE(COMBAT), idle>90|
| 175 |268486304 /195981108870200|58|27454 /3153|24|22.36|PASS farm|
| 260 |268487041 /195981123221300|31|37326 /1768|10|30.44|PASS farm|
| 275 |0 /0|0|0 /0|0|380.71|FAIL: absent121/121; SQL DEAD before baseline; final receipt missing|
| 278 |268487193 /195981143814700|5|2783 /131|0|306.50|FAIL: PHASE_DEADLINE(REGEN), no tail|
| 404 |268488272 /195981280897200|18|9153 /435|10|74.21|PASS farm|
| 447 |268488615 /195981370909900|0|0 /0|0|380.73|FAIL: live idle travel.route_absent, no native session|

| B profile | object / epoch | cycles | EXP / SP | tail120 | maxIdle s | outcome / доказанный факт |
|---|---|---:|---:|---:|---:|---|
| 110 |0 /0|0|0 /0|0|381.32|FAIL: absent133/133; SQL DEAD/HP0|
| 142 |268486041 /197184859502900|0|0 /0|0|381.34|FAIL: live idle travel.route_absent|
| 175 |0 /0|0|0 /0|0|381.35|FAIL: absent133/133; SQL DEAD/HP0|
| 260 primary |268487041 /197184876123600|19|6339 /294|6|38.99|PASS farm; first stop OPEN/timer1|
| 275 |0 /0|0|0 /0|0|381.37|FAIL: absent133/133; SQL alive/HP263, not a proven death|
| 278 primary |268487193 /197184897908700|23|5985 /285|3|82.53|PASS farm; final SEALED canonical PASS|
| 404 |268488272 /197184915592600|24|8674 /399|11|29.53|PASS farm; first stop EVENT24354 still RESERVED/SUBMITTED|
| 447 |0 /0|0|0 /0|0|381.39|FAIL: absent133/133; SQL alive/HP214, not a proven death|

Raw summaries: [A cohort](evidence/FINAL_R6_A_D_380S/cohort-result.json), [B cohort](evidence/FINAL_R6_B_C_380S/cohort-result.json), [all16](evidence/FINAL_R6_ALL16_REPORTED.json), [initial/final scalars](evidence/FINAL_R6_INITIAL_FINAL_SCALARS.json). Complete raw snapshots, XML and full-native JSONL are retained in each scene directory. The all16 file is an observation summary, not a second evaluator.

У A110/142/278 реальные Player EXP deltas20031/13881/5696 больше sticky evidence525/9649/2783. Это сохранённое расхождение, а не восстановленные counters: после PHASE_DEADLINE обычные действия могли продолжаться, но same-epoch proof уже fenced. Не объявляем этих actors непрерывно здоровыми и не сбрасываем incident.

Loot: A native sequences110=0,142=8,175=33,260=2,278=0,404=27,447=0; B142=0,260=4,278=0,404=3. A baseline/final inventory и final sealed SQL сохранены. B live inventory SQL export не изменился, несмотря на native loot событий260/404; при failed final seal это не даёт полного inventory proof. Для нулевых/absent actors нет достаточного drop/eligibility witness, поэтому NOT_APPLICABLE не выдуман. Whole-group loot остаётся UNPROVEN.

## Исправления с собственным RED

Переиспользованы существующие PhantomBackgroundDecision/VisibleFarmTravel/VisibleAutoPlay, exact native work scope, штатный native damage/reward path и deterministic025 contracts. Battle formulas, Player, ThreadPool, EventDispatcher и Shutdown не переработаны. Scope exception14 файлов явно ограничен [allowlist](FINAL_SOURCE_ALLOWLIST.txt); новых product abstractions нет, новых test source files три.

| Причина | RED -> GREEN | Ограниченный результат |
|---|---|---|
| F1 локальная возможность после standpoint failure |RED_F1_SCOPED0261/3 -> GREEN_F1_SCOPED0263/3|Тот же committed anchor: проверенная nearby native opportunity до прежнего route failure; LoS/path/floor/water/dry-segment guards сохранены|
| F2 native MP и paid cast |RED_F2_RESOURCE0260/2 -> F2_NATIVE_TARGET_CENSUS0262/2|Exact mage session, stock sit/stand/regen;45s bound,2xcost hysteresis/threat exit; published paid cast не обрывается generic repair,90s useful debt сохранён|
| F3 planner reentry |RED_F3_SCOPED_REENTRY0260/1 -> GREEN_F3_EXACT_SESSION_REENTRY0261/1|Reuse exact player/epoch/goal/revision/session; travel не отзывает здоровый AutoPlay/AutoUse при повторном входе|
| E1 N02/S12 recipient |healthy paired BASE1/2 -> CANDIDATE2/2; S12 BASE2/3 ->3/3|After-native frozen roster contribution>1 получает наблюдение native death, включая main damage dealer, если actual killer другой; existing duplicate guard сохранён|
| E2 callback diagnostics |R6_CALLBACK_ACTUAL_DEATH0262/2|Original stock ON_ATTACKABLE_KILL delay2500ms и stock child QuestTimer150ms завершаются по actual callback; THROW native-writer negative control остаётся drain FAIL; финальный full-server B всё равно FAIL|
| E3 старые NPC generations |RED_E03_ACTUAL_TRANSITIONS0261/2 -> GREEN_E03_NATIVE_GENERATION0262/2|После реального нового selection retire только undamaged older same object+instance generations; damaged pending,16 cap, sticky overflow и counters сохранены;17 distinct NPC negative control fenced|

Полный [FIX_LEDGER](FIX_LEDGER.md) сохраняет исходные ошибки fixture и invalid probes. Изменённые test prerequisites отделены от product expectations: legal Human Mystic level7 для stock NPC20121 level5 до native lifetime; N02 использует максимум3 actual original doCast finishers с HP decrease, а не предполагаемый kill; E03 сначала очищает target original setTarget(null), иначе selection того же объекта был no-op. Counter/assertion/cap gates не ослаблены. Первые недискриминирующие/нездоровые attempts не считаются RED proof.

## Exact callback и первый shutdown

A первый stop без jcmd pauses: семь удержанных original scopes DETACHED, outstanding0, pendingTimers0, failure empty; observer captured/exported15/15, overflow0. В [A native log](evidence/FINAL_R6_A_D_NATIVE_SHUTDOWN_LOGS/java2.log) InitialSubsystemDrain завершён stopped=true. Отсутствующий275 не исключён из persistence denominator.

B первый stop без dumps не является healthy. [Native log](evidence/FINAL_R6_B_C_NATIVE_SHUTDOWN_LOGS/java0.log) до Shared ThreadPool stop сообщает systemStateFAILED, retainedMaterializationEntries8, materializationRUNNING, progression/navigationSTOPPING. Затем штатный pool shutdown прерывает ACTION_DRAIN другого object268486909/epoch197702319542000 с NATIVE_WORK_DRAIN_INTERRUPTED.

У [original owner404](evidence/FINAL_R6_B_C_380S/full-native/final-owner-state-197707677899300.json) EVENT24354 ON_ATTACKABLE_KILL остаётся RESERVED/EARNED, dispatch SUBMITTED, epoch197184915592600, reserve197700794193800, submit197700794198100, start0, scheduled delay2500ms; future done=false/cancelled=false/dueMs=-4383. Это доказательство actual publication, причина позднего исполнения UNKNOWN. Нет достаточного worker/producer stack для утверждения starvation всех threads. Snapshot NONATOMIC_VOLATILE не используется как атомарный causal transcript. Native timer не complete/cancel по сроку; guards не отключены.

B142/260 OPEN/timer1;278 DETACHED/0/0;404 OPEN/outstanding1/timer1. Exporter overflow0 не заменяет drain. Физический выход JVM по штатному Shutdown не даёт CALLBACK_DRAIN_PASS. Ошибка Login attach PrematureEOF в stop log сохранена; emergency kill не применялся. Ни последующий stop, ни restart не стирает этот первый FAIL.

## Сохранение, restart и crash boundary

[A final exact](evidence/FINAL_R6_A_D_WHOLE_GROUP_EXACT.json):7/8, только275 без immutable final SEALED receipt. [B final exact](evidence/FINAL_R6_B_C_WHOLE_GROUP_EXACT.json):1/8, только278. Проверка canonical SEALED использует существующие full native scalar fields, inventory hash и current-class skills hash; full SQL exports дополнительно сохраняют все item columns/locations и skills. Сам по себе canonical inventory hash не объявляется доказательством дополнительных item metadata.

Два actual new-process sameDB restart каждой группы сделаны на R6, early export до Synthetic progression. [d restart1](evidence/FINAL_R6_D_RESTART1_EXACT.json) и [d restart2](evidence/FINAL_R6_D_RESTART2_EXACT.json) PASS8/8. [c restart1](evidence/FINAL_R6_C_RESTART1_EXACT.json) FAIL только durable state:142/260/404 MATERIALIZED->READY ([exact changes](evidence/FINAL_R6_C_RESTART1_STATE_CHANGES.json)); characters кроме online, items/skills, progress/XYZ и linked COMPLETED context сохранены. [c restart2](evidence/FINAL_R6_C_RESTART2_EXACT.json) PASS против первого reconciled state; это не две успешные проверки исходного failed final store. Plan CAS versions/digests могут штатно увеличиваться без потери native данных.

Cold452 R6: production resolver собственной026h прочитал сохранённый024c crash receipt8423; immutable witness SHA fb12ae699e3f2c77907c09ffd8e4c0f0ac910bb05e1737141f81bc72b9993efd. [Initial exact](evidence/R6-cold452-initial-exact.json), [actual restart1](evidence/R6-h-restart1-exact.json), [actual restart2](evidence/R6-h-restart2-exact.json) PASS. Без manual finalize, HP/EXP/XYZ repair или reseed.

R03 actual full-server e выполнен до freeze на R4=4ef248030365d4fc126d6bd3f8084daa54a3b37f. [Planned crash](evidence/R4-e-crash-native/planned-crash.properties) Runtime.halt72 AFTER_OWNED_NATIVE_STORE original110/object268485779/epoch190320354272500/prepared34573, REALcount0, native snapshot/receipt/window SQL/precrash dump hashes. После actual restart observer снял resolver witness после COMMIT/connection close до ordinary admission, с bounded45s barrier. Exact READY XYZ40307/41105/-3568, heading19790, EXP34624/SP1641; [all native fields/full items six columns/skills/vitality](evidence/R4-e-early-canonical/early-comparison.json) PASS, [canonical receipt](evidence/R4-e-recovery-boundary/RECOVERY_COMMIT_EXACT.json) PASS. Anchor не подставлялся. После release наблюдались ordinary native movement и progression; exact first Java writer не captured, UNKNOWN. [Второй actual e restart](evidence/R4-e-second-restart-exact.json) PASS. Ошибочный per-line properties comparison с nulls сохранён как INVALID, используется corrected evidence. На финальном R6 boundary focused2/2, но actual R03 scene не повторялась: final-SHA gate NOT_RUN.

## Регрессии, scope и остаток

Frozen build PASS33s, две существующие deprecation warnings. [Матрица18 routes](REGRESSION_MATRIX.tsv) подтверждена XML/counts/exit0 в [verification](evidence/R6-REGRESSION-MATRIX-VERIFIED.json):025 recovery8/8, drain3/3, current intent10/10, review2/2, retaliation3/3, ecology30/30+6/6, shutdown8/8, owned-store27 boundaries, S12/N02/native/callback/local/resource/generation/recovery/evidence contracts. Это сохранённые regression positives, не full-server acceptance PASS. Историческое025 whole-group8/8 остаётся baseline;026 R02 не прошёл.

После semantic freeze product/test не менялись. [Source scope guard](evidence/R6-SOURCE-SCOPE-GUARD.json) exact14 paths, неожиданных/missing/post-freeze source changes нет. [Runtime hashes](evidence/FINAL_R6_RUNTIME_HASHES.json), [own roles](evidence/FINAL_OWNED_ROLES_STOPPED.json), [listeners](evidence/FINAL_LISTENERS_AND_OWNERS.json), [pending/connections](evidence/FINAL_DATABASE_PENDING_AND_CONNECTIONS.json). STALE_RECORD означает отсутствие отмеченного живого PID, а не поддельную запись STOPPED; записи сохраняются. Private clones/runtime сохранены остановленными. Credentials, private snapshots/JAR/geodata не публикуются. Входной PACKAGE_MANIFEST не является финальным acceptance; [финальный artifact SHA256 manifest](evidence/FINAL_ARTIFACT_MANIFEST.tsv) исключает собственный manifest и два self-referential index guards. Task-local .gitattributes сохраняет evidence bytes без изменения root/global Git configuration.

Остаток: natural idle/route_absent и sticky deadlines всей cohort; exact full-server drain producer; R02 всех исходных actors; final-SHA R03/R04, dedicated death return, soft background/rematerialization/human locality, final client. Один рекомендуемый следующий bounded этап: воспроизвести original B failed drain с exact task event producer/queue/native cleanup writer evidence до pool shutdown, одновременно удерживая фиксированную полную cohort и unchanged natural acceptance. Это рекомендация; следующий goal не открыт.

- mojibake-маркеры в изменённых файлах проверены: product/test и вручную добавленные тексты без совпадений; единственное исключение —68 replacement characters в неизменённом raw R4-observer-census-build.log от неуспешного jar overwrite. Потерянные байты не восстанавливаются достоверно; исходный log сохранён, общий all-text scan поэтому не объявлен чистым. [Отдельный scan](evidence/FINAL_MOJIBAKE_CHECK.json).
- escaped Cyrillic в изменённых файлах проверены отдельно: совпадений нет. [Отдельный scan](evidence/FINAL_ESCAPED_CYRILLIC_CHECK.json).

Git использовался только по явному TASK026 exception; точные формы read inspection/exact-path commits/normal push в [GIT_ACTIONS](GIT_ACTIONS.md). Main/foreign/kpCat, PLAY и старые024/025 не изменялись.
