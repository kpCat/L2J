# PHANTOM-LEARNING-013 — план реализации для раннего review

> Для исполнителя: после одобрения владельцем применять superpowers:executing-plans и выполнять шаги последовательно в этом чате. До одобрения разрешён только аудит и подготовка документов; source/runtime не изменять.

**Статус:** PLAN_REVIEW_REQUIRED. G0 ещё не закрыт. Goal активен, FULL013 не заявлен.

**Цель:** один настоящий автономный COLLECT → FIT → независимая V2 EVAL → durable CHECKPOINT → NEXT/PAUSE в own TEST, с проверяемым STOP/RESUME без повторного credit.

**Архитектура:** существующие Training.ps1/TrainingOwner.ps1 остаются единственным Director/installation-owner. Для V2 используется дочерний evaluation-only run с отдельными campaign/dataset/config pins внутри той же installation и под тем же owner. Existing SQL Adam/saveAutonomous/exactly-once contracts переиспользуются без изменений модели, objective, reward или SQL schema.

**Стек:** Java25, Ant, существующие PowerShell и C# WinForms. Source — существующий L2J-learning-004; runtime/SQL — существующий L2J-learning-006. Основной L2J-phantom-world read-only, за исключением уже разрешённого fetch metadata.

**Spec:** TASK.md, ARCHITECTURE.md, LEARNING_AND_EVAL.md, AUTO_STOP.md, PLAN.md, ACCEPTANCE.md из исходного пакета013, проверенного по MANIFEST.sha256 (11/11).

## Аудит до изменений

- GitHub, source004 и TEST006: feature/phantom-world, HEAD 52aac9e0274a63906a9fe5053611b8daf1aac778. Fresh fetch main/source004 выполнен. Основной checkout отстаёт на 7e997636dbac9d5e1754e00c793cd6fac858b0c6, содержит пользовательские untracked-файлы; не переключать и не обновлять его рабочие файлы.
- Пакет013 найден в основном checkout по docs/phantoms/tasks/PHANTOM-LEARNING-013-AUTONOMOUS-LEARNING-LOOP, а не по указанному module-relative пути. В source переносится только этот exact package, исходные документы не переписываются.
- Прочитаны root AGENTS.md основного/source/TEST checkout, module Agents.md, README.md, relevant PHANTOM_CURRENT_STATUS, CODEX_WORKFLOW_CONTRACT, TASK_PACKAGE_STANDARD, bounded master plan и COMBAT-AI-ROADMAP; весь пакет013; RESULT009, R1_RESULT/R1_PLAN и frozen/safety evidence012.
- Прочитаны релевантные участки Training.ps1, TrainingOwner.ps1, Test-Training.ps1, AutonomousTraining.ini, TrainingGui.cs, TacticalTrainerV3.java, TacticalStoreV3.java, TacticalModelV3.java, ActionAdvantageV2.java, AutonomousTrainingV1.java, TrainingConfig.java, PhantomAutonomousTrainingNative.java, PhantomEvaluationOutcomeV2.java, PhantomEvaluationReplayV2.java и build.xml. Отдельные code-map/pattern-файлы не найдены; повторно не искать без причины.
- Аналоги: атомарные Write-TrainJson/журнал009 и held exit receipts010; frozen EvaluationOnly/V2 и native-first curriculum012; freezeAutonomous/saveAutonomous с раздельными selected и terminal Adam и SQL recovery009.
- Подтверждённые разрывы: Training.ps1:706 меняет improved/patience по TUNING до V2; 712–733 не требует V2 proof для обычного TRAIN; V2 guards допускают только EvaluationOnly, что следует сохранить; Java/PowerShell TRAIN acceptance budget требует 6–10 starts/90min и несовместим с013; native checkpoint перезаписывает исторический pointer12.
- Последний исторический private cold ZIP model-2-c62a992e979743c2941644b0f7d326c2.zip прочитан без записи: accepted2, working DRAFT14, selectedEpoch0, Adam903/223; payload SHA совпали с frozen012. Selected SHA 7d3c8fc1f2ae5fc199cfb7517f71743189fdd3fbb4b959a318753637e2d3bc37, terminal optimizer SHA 6a34b7ee503f35dff75e61ba59892be823f72e45402e1a20f4036b9740ade844, terminal weights SHA f830a1cc4ead23f36dd6153fd1d818bbb0ae3b830d9e666f1988a4c82f05e9bf.
- Pointer12 SHA acc21bed707b518fb58e23fec006e7d658723763c73c237a039d950e14c9b010; его содержимое model12/Adam887/207 не является актуальным SQL parent14. Runtime JAR SHA 41072ad6aef484c6135e15f80d1d460da5c638819c7342945129a1a7b748f05c совпадает с build.json. Это ещё не полная перепроверка source/JAR состава.
- TEST director STOPPED, enabled=false/phase=OFF; прежние Director/native/worker PID отсутствуют; listeners33357/18001/19301 отсутствуют. SQL PID и полный Geo203/protected207 inventory ещё требуют canonical preflight. Свежий SQL SELECT/export НЕ выполнялся: ZIP — historical cold evidence, не новый SQL readback. Исторические 48 applications/16 gradients пока подтверждены прежними receipts и их payload SHA, свежий SQL count остаётся обязательным.
- JDK25.0.4.1 и Ant1.10.17 найдены. Manual012 остаётся REQUIRED, вручную не принимать. Budget013 пока 0GS/0ms; build/test/native не запускались.

## Exact source allowlist — максимум 10 путей

Все пути ниже относительно L2J_Mobius_CT_2.6_HighFive/. Это одна связанная control-plane интеграция; нет отдельного UI/product/refactor slice. Менять только необходимую подвыборку, расширение списка требует review.

1. tools/phantom-runtime/training/Training.ps1 — versioned cycle orchestration, дочерняя V2, decision/checkpoint/status.
2. tools/phantom-runtime/training/TrainingOwner.ps1 — общий013 budget, config/state pins, atomic receipt helpers и отдельные stop reasons.
3. tools/phantom-runtime/training/Test-Training.ps1 — focused controller/recovery/stop/config tests в существующем runner.
4. tools/phantom-runtime/training/AutonomousTraining.ini — явно opt-in013 defaults OFF и ограниченные параметры без смены reward/objective.
5. tools/phantom-runtime/training/TrainingGui.cs — вывод typed cycle/selected/Adam/V2/best/pause/budget полей через существующий STATUS; layout/Designer не менять.
6. java/org/l2jmobius/gameserver/phantoms/learning/TrainingConfig.java — согласованная валидация013 config/budget, прежний012 guard сохраняется.
7. test/java/org/l2jmobius/tests/phantoms/PhantomAutonomousTrainingNative.java — новая cycle-scoped cold receipt без записи pointer12, bound child evaluation/config/readback integration в существующем adapter.
8. test/java/org/l2jmobius/gameserver/phantoms/learning/AutonomousTrainingChecks.java — focused config/budget/lineage/split guards.
9. test/java/org/l2jmobius/tests/phantoms/PhantomEvaluationOutcomeV2Checks.java — focused parent/child V2 pins и negative coverage/recovery в существующем suite; V2 outcome semantics не менять.
10. build.xml — только при необходимости подключить focused013 route по существующему DB-free шаблону.

TacticalModelV3, ActionAdvantageV2, TerminalRewardReceiptV1, TacticalTrainerV3, TacticalStoreV3, AutonomousTrainingV1, V1/V2 codecs, production Combat/Party/Player/Spawn/SkillData/Geo остаются read-only. Если окажется, что required outcome требует изменения read-only контракта, остановиться с точной diagnosis.

Документальный scope: только текущий task directory, один reports/PHANTOM-LEARNING-013-AUTONOMOUS-LEARNING-LOOP.md и COMBAT-AI-ROADMAP.md. Исходные TASK/PRE_AUDIT/ARCHITECTURE/LEARNING_AND_EVAL/AUTO_STOP/PLAN/ACCEPTANCE/GIT_POLICY/HANDOFF/README_RU/TEST_MATRIX/MANIFEST не переписывать. Для результатов создавать отдельные RESULT.md, EVIDENCE.json, COMMANDS.md, ACCEPTANCE_RESULT.md, DELIVERY_HANDOFF.md и exact scope/readback receipts.

## Versioned protocol, утверждаемый этим планом

Нужен новый013 cycle journal/receipt format, поскольку schema9 связывает patience с loss и не связывает checkpoint с V2 evidence. Это versioned filesystem control protocol внутри existing owner, без новой SQL/model/reward schema и без второго engine.

- Historical autonomous/v1 journals и cold-checkpoint.json read-only. Новое состояние013 в отдельной области с campaign/cycle identity; незавершённое не помечается completed.
- Persisted refs: source/JAR/config/feature hashes; frozen dataset; accepted/best/selected/optimizationParent; terminal Adam SHA/updates/epochs; child config/dataset/V2 receipt hashes; issued/spent global budget; entry/exit и recovery state.
- Отдельные cycle-status.json, fit-result.json, eval-v2.json, decision.json, checkpoint.json; existing atomic pending→replace. Checkpoint/patience credit только по уникальному cycle-id после SQL fit readback и complete проверенной V2. Repeated RESUME сверяет SHA и не начисляет credit второй раз.
- STOP/PAUSE на атомарной границе; точная process identity и held handle до exit; никогда не использовать unknown exit как PASS. Training config до/после child сохраняется с hash, interrupted switch восстанавливается fail-closed.

## Шаги исполнения после approval

### A. Закрыть G0 и зафиксировать regression baseline

- [ ] Canonical read-only TEST preflight: owner/SQL identity/ports, Geo203/protected207 actual inventory, source/JAR build pins, MAIN/PLAY OFF; чужой SQL не трогать.
- [ ] Запустить только существующий собственный SQL route для fresh cold SELECT/export; зафиксировать2/14/903/223/apps48/gradients16/pointer12 и hashes до записи новых records. Закрыть SQL штатно. Mismatch → STOP, native0.
- [ ] Сохранить before source10 hashes и точные historical payload/journal/SQL baselines вне переписываемых артефактов; создать allowlist/scope receipt. Прочитать целиком необходимые test/config references перед соответствующей правкой.

### B. RED → GREEN control plane и budget

- [ ] В существующем Test-Training добавить focused route для013. Behavioral RED: fit с лучшим TUNING и без V2 не подтверждает improvement/patience; три достаточных уникальных complete low-power цикла дают PAUSED_NO_MEASURABLE_PROGRESS, reason NO_SUPPORTED_GAIN_LOW_POWER.
- [ ] GREEN в существующих Director/Owner: no-data, invalid/incomplete EVAL, EVAL_COVERAGE_GAP, owner stop, failure и resource limit не меняют patience; regression сохраняет best; достаточное signed independent improvement сбрасывает patience. Synthetic controller receipts не являются игровым evidence.
- [ ] Versioned opt-in013 acceptance budget: максимум4GS/4800000ms TOTAL во всех TRAIN/child EVAL/recovery phases. Legacy009/012 bounds не расширять; child012 ≤2GS/35min и дополнительно ограничен остатком общего013 budget. Сам факт ограничений012 не даёт ему второго бюджета.
- [ ] Config/checkpoint/patience duplicate/mismatch negative cases; однократный checkpoint commit после crash before/after journal, budgets не обнуляются при RESUME.

### C. FIT → child V2 → decision → checkpoint

- [ ] Сохранить TRAIN/TUNING/AUDIT/EVAL fences и >=3 independent TRAIN controls/cell; текущий sufficient(): TRAIN>=16, TUNING>=8, AUDIT>=8, >=2classes и>=2scopes, реальная paid divergence. Не добавлять class/Skill ID списки.
- [ ] Оставить freezeAutonomous/saveAutonomous SQL exactly-once, cold optimizationParent с Adam moments и selected/terminal отдельно. selectedEpoch0 — selected weights не улучшены. Audit/teacher/constant и optimizer measurements публиковать честно, неизвестный gradient norm/saturation обозначать UNKNOWN.
- [ ] Дочерний immutable EvaluationOnly config: EVAL V2, exploration=false, FIT/credit=false; frozen candidate и fresh dataset/source authority. Один installation-owner/SQL/GameServer owner. Прежние012 receipts используются только для replay.
- [ ] Принимать только complete typed V2 proof, Solo+Party, LOWER/HIGHER, balanced AB/BA: минимум2terminal-comparable pairs в каждом направлении по существующему протоколу. Смерти/достоверный timeout входят в denominator; cancelled/stale/unknown не completed.
- [ ] Decision не подменяет comparison-to-best comparison-to-stock. При отсутствии собственных new-vs-best evidence bestDecision=UNRESOLVED, прежний best сохраняется; acceptedTEST2 не меняется. Малый n даёт INCONCLUSIVE/INSUFFICIENT_POWER. Реальные низкомощные достаточные циклы могут начислять no-supported-gain patience, без утверждения математического плато.
- [ ] Checkpoint013 связывает SQL candidate/terminal Adam, полный V2 SHA, decision и unique cycle. Только после него NEXT plan/PAUSE. Pointer12 и исторические budgets не писать.

### D. Offline replay, stop/recovery, регрессии

- [ ] Existing typed read-only replay реальных012R1 receipts даёт4pairs/43paid effects, credit0, исходные hashes unchanged. Replay не считается новой EVAL013.
- [ ] Focused crash after SQL COMMIT before journal, rollback before COMMIT, wrong source/JAR/config/model/Adam pins, interrupted child config switch, duplicate completion, timeout/cancel/unknown guards.
- [ ] WinForms STATUS отображает выбранные веса отдельно от terminal Adam, completed/unfinished V2, best/pause/budget/resources; GUI close не владеет training lifecycle. Designer layout не менять.
- [ ] Ant последовательно: phantom-learning007-test, phantom-learning008-test, phantom-autonomous-training-test, phantom-learning011-test, phantom-learning012-test, нужный013 route, jar. Общий verify не запускать. RED не является compilation failure.

### E. Native readiness и bounded end-to-end

- [ ] Дешёвый canonical headless admission до GS: новый legal TRAIN cohort с достаточным potential controls/diversity; отдельные lawful Solo/Party V2 proposal sources, physical parity и lower/higher. Для неизвестной будущей candidate-model authority окончательный child pin/headless seal строится автоматически после FIT. Если необходимого legal набора нет — GS0, точный PARTIAL/EVAL_COVERAGE_GAP, без reroll/ослабления.
- [ ] План расходов: первый GS обслуживает collection cohort; второй — свежую child V2; два оставшихся только как резерв или допустимый автоматически запланированный следующий цикл. Каждый start до boot резервируется в едином ledger; полный native wall time с boot/drain/exit ≤80min. Никакого обещания fit при недостаточности данных.
- [ ] Одним START Director сам выполняет COLLECT→FIT→V2→CHECKPOINT→NEXT/PAUSE; Codex не посылает фазовые команды. Проверить STOP/RESUME на атомарных границах и cold readback без двойного credit. Не требовать три native цикла: patience3 проверяется offline.
- [ ] После запуска каждого дорогого шага статус: phase/data/selected-vs-terminal/budget/nextgate. Реальный n и результаты не подбирать по исходам. Закрыть собственные процессы, SQL и порты штатно, exact held exit evidence.

### F. Доставка и независимый readback

- [ ] Final cold SELECT/hash parity исторических models1–14/Adam/apps/pointer12/Geo203/protected207; новые legal records только own TEST. MAIN/PLAY без promotion. G0–G8 и R/N matrix с PASS/FAIL/NOT_RUN, nightReady=false.
- [ ] Две отдельные encoding проверки изменённых файлов: полный набор mojibake-маркеров; escaped Cyrillic/XML escaped Cyrillic. Strict UTF-8, exact diff/check/scope guard.
- [ ] Russian RESULT/EVIDENCE/COMMANDS/ACCEPTANCE_RESULT/DELIVERY_HANDOFF и общий report; roadmap №7/13, осталось6. Один исторический manual012 gate не принимать за владельца, final manual013 REQUIRED.
- [ ] Exact-path scoped commit(s), обычный non-force push origin feature/phantom-world; перед публикацией проверить fresh origin. Независимый GitHub HEAD/parents/exact diff/full changed source/full evidence readback. В отчёте перечислить точные git-команды и основания.
- [ ] FULL013_AUTONOMOUS_LEARNING_LOOP только при G0–G8; иначе честный PARTIAL с точным gate. Следующий Goal не запускать.

## Review focus и остановка scope

- Один SQL COMMIT с несколькими RESUME не должен менять applications/gradient credit повторно — taskD.
- Чужие/mismatched/неполные V2 receipt hashes не должны завершать checkpoint или начислять patience — tasksB/C/D.
- Crash между TRAIN→child config switch не должен включить FIT/exploration в EVAL или потерять исходный pin — tasksC/D.
- Отдельные child budgets не должны дать больше4starts/80min или сбросить старые009–012 расходы — tasksB/E.
- Readiness по historical template не доказывает fresh split и не заменяет candidate-specific seal — tasksA/C/E.

Не более двух прямых causal blockers внутри approved scope. Третий независимый blocker или необходимость нового objective/model/reward/SQL schema/production owner — STOP с diagnosis. План не разрешает новые ветки/клоны, production promotion, PvP/raid, менять protected roots или запускать следующий Goal.

## Решение владельца

Требуется одно раннее approval этого exact10 scope и versioned filesystem cycle protocol. Основание исходного PLAN.md: «Один ранний короткий PLAN approval по root AGENTS, потом рутинно автономно». До ответа source edits, SQL starts, training и native запуск не выполняются. После approval — последовательное выполнение в текущем чате до результата/required gate, без новых рутинных запросов.
