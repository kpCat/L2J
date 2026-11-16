# Goal021 — результат разрешённого продолжения

**NATIVE_CHAIN021_VERIFIED; финальный FULL зависит от G8 readback в CONTINUATION_DELIVERY.json. Manual gate REQUIRED.** Это тот же Goal021, не022. Исходные PARTIAL RESULT/EVIDENCE и первый train021 сохранены.

Владелец отдельно разрешил один fresh GS≤8min и дополнительные проверки≤7min, см. CONTINUATION_AUTHORITY.md. Fresh train021-c1, loopback33363/18023/19323, тот же fixed production JAR75f37c80…5782297 и immutable parent179/42cfff…ef96c. Изменены4 TEST/CLI файла; production Java/JAR/skills/resources/Geo не менялись. New branch/worktree/provider/scheduler отсутствуют.

## Реальная цепочка

Исходный sealed016→reproduceSolo020→natural180→position/Geo/LOS/owner/skills admission→новый CUT. Class94/level77, NPC20966, acquired1230:28 и1275:14, initial HP4827/MP2837, original inventory fingerprint62003279…d6. Policy COVERAGE_TEST_V1, seed21001, cap1, p1 зафиксирована до CUT;16 immutable decisions/intents:15 BASELINE,1 EXPLORE_COVERAGE. EPSILON остаётся отдельным проверенным offline контрактом; MODEL/EXPLORE_EPSILON native0.

**Cursor0: stock1230:28 → selected1275:14, EXPLORE_COVERAGE, propensity1.** Существующий Observer зафиксировал exact native owner до dispatch. Original paid effect: **HP328, MP35, elapsed961041700ns**, HP fraction0.0262904777. EventKey `d6625e51139aaa5b13e80be906c33130d28e44bdcebd7a2244e32d50e3c4df79`, decision SHAa95d75b9…e9966; raw→row→Intent→existing Effect bytes совпадают, selected/stock/epoch/owner/target/episode/split проверены. EXPERIMENT_REORDERED=true, MODEL_REORDERED=false.

Original episode FAILED/NATIVE_ACTOR_DIED: kill0, death lower bound1,13 paid eventKeys, HP6786/MP625, duration36.0568888s. Из них **9 model-bound individual rows**,1 experiment+8 controls, являются authenticated TRAIN samples;4 остальных aggregate events не получают credit. Это не9 новых whole-episode rewards. EXP/SP не входят в label. Label — неизменённый ActionFeedbackV4.label().

| Mode / skill | n | mean HP | sample SD HP | mean MP | mean elapsed s | mean HP fraction |
|---|---:|---:|---:|---:|---:|---:|
| BASELINE1230:28 |3|749|140.3531|69|1.8992908|0.0600353|
| BASELINE1275:14 |5|386|54.1479|35|0.9508105|0.0309394|
| EXPLORE_COVERAGE1275:14 |1|328|UNKNOWN|35|0.9610417|0.0262905|

Одна deterministic exploration не randomized comparison; contexts внутри боя меняются. SAMPLE_SIZE experiment1, INSUFFICIENT_SAMPLE_SIZE, counterfactual UNKNOWN, selection bias присутствует, IPS не применяется. Смерть/kill — отдельные episode facts, не causal label cast. Superiority и ухудшение от эксперимента **NOT_EVALUATED**. Нельзя выбирать лучший навык по этой таблице.

## Честная offline finalization

Второй GS сохранил все16 decisions/intents и9 original raw/row/effect связок, затем **вышел с1**: EVAL012_INTEGER_stock при подсчёте in-memory summary. JSON helper принимал только Long, skill ID был Integer. Это второй causal local blocker после исходного sparse-map отказа. Focused RED воспроизвёл точный exception; Number-based count дал GREEN. Третьего GS и третьего causal fix нет.

Данные не восстанавливались из aggregate/выбора задним числом. TEST-only `recover-publication` допускает только остановленный C1 и этот exact error stage. Он сверил pre-CUT policy/parent, whole decision count с original diagnostics, exact raw writer/row/Intent и **уже существующие** Effect bytes, требовал paid experiment/control. Create-only recovery021.json хранит SHA всего исходного набора; samples/cold повторно проверяют эти SHA. Созданы только недостающие manifest/proof/complete metadata. Исходные native files/effects не переписывались; nativeExit1 и postDrainSummaryRecovered=true остаются явными. Не сохранившиеся scores — UNKNOWN, не пересчитанные якобы pre-cast scores. Независимый read-only review finalizer Important/Critical не выявил.

Явный повтор RunOnce в уже остановленном C1 выполнил offline finalization→fit→cold, **GS0/SQL0**. Получено **179→188 (+9)**, новый create-only atomic checkpoint `candidate021-90cdb499d44edde5f53819b357f1383dc65f9b9de1bd4d43120f6a154d1a55a6.bin`. Separate JVM cold exact188/SHA, replay additional updates0, tamper reject. Parent179 SHA exact. Post на adversarial копии: missing trailing decision REJECT, summary propensity tamper с перепривязанным outer seal REJECT, restored copy PASS, original unchanged, trainingCredit=false. Stop вернул STOPPED_PROOF_PRESERVED.

## G0–G8 и бюджет

| Gate | Доказательство |
|---|---|
|G0|Fresh feature + owner authority, immutable179 cold/replay0, sealed016/sourceSQL read-only exact, private owned root.|
|G1|Один TEST arbiter, frozen COVERAGE policy249aca96…5d9bd,16 pre-native records, exact probabilities, old route preserved.|
|G2|Lawfulness/current native owner/HPMP/skills/Geo/LOS/cooldown/safety и existing5ms selection guards сохранены; offline negative contracts GREEN. Full p99 отдельно NOT_RUN.|
|G3|Original paid alternative1275:14 != stock1230:28, HP328/MP35/0.961s +8 genuine baseline controls, exact individual bindings.|
|G4|9 новых authenticated TRAIN rows из original after-drain bytes, complete metadata + recovery provenance;4 other episode events credit0.|
|G5|Parent179→188, immutable checkpoint SHA90cdb499…a55a6, atomic save, separate cold/replay0/tamper.|
|G6|Observed grouped metrics/SD/sample caveats, stable CONTRACT_V1 for022–024,4096 cap/256 batch/append-only rollover STOP.|
|G7|Дополнительный GS81917ms; всего2 явно разрешённых GS/175107ms. Wrapper343961ms; extra focused checks малые, весь additional budget<420s. OFF/own0/portsFree;117719 files drift0, включая весь old train021; Geo203/protected207 exact.|
|G8|Scoped ordinary feature push и independent full readback фиксируются в CONTINUATION_DELIVERY; owner manual gate REQUIRED.|

Focused javac двух существующих TEST classes + continuation admission RED→GREEN, existing021 policy/legacy020/parent179 replay regression GREEN, PS proof contracts GREEN. Production rebuilt не был: GS использовал exact уже исправленный75f JAR. Source-SQL660ms, preflight309ms; offline recovery414ms, fit445ms, cold433ms (отдельные процессы). Offline finalization wrapper2422ms; post exit0. First RunOnce343961ms exit1 не объявлен exit0. Дополнительные тесты/диагностика/публикация укладываются отдельно разрешённым7min; исходный бюджет677.935s не обнулён. Полные suites/campaigns/ночь не запускались.

Scope: ровно9 paths — Native020/Test021/TrainingOwner/Training, четыре continuation task docs и одна roadmap; exact allowlist в CONTINUATION_DELIVERY. Original report/RESULT/EVIDENCE/BUDGET и исторические checkpoints сохранены. Git применяется по явному Goal/continuation разрешению: bounded inspect, exact add/commit и обычный push feature; force/reset/clean/stash/branch/worktree не применялись. Обе encoding проверки и exact diff-check — в delivery receipt.

**PRODUCTION_V4=OFF; NIGHT_TRAINER=NOT_READY; SUPERIORITY=NOT_EVALUATED. Goal022 не начат.**
