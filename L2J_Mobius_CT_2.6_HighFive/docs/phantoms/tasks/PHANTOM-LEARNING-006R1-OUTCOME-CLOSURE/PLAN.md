# PHANTOM-LEARNING-006R1-OUTCOME-CLOSURE — план продолжения

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans. Выполнение в текущем диалоге; PROGRESS.md — единый журнал. Историческую 006 не переигрывать.

**Статус:** APPROVED_EXECUTED_PARTIAL. Пользователь подтвердил: «Подтверждаю план; продолжай автономно». Bounded выполнение завершено с PARTIAL, см. RESULT; final manual gate REQUIRED. План ниже сохраняет исходные требования, незакрытые пункты не объявляются выполненными.

**Goal:** доказать цепочку native → terminal reward → actual fit от Adam8 → сохранённый новый DRAFT → изменённое native действие → независимое A/B, либо сохранить точный PARTIAL при исчерпании заранее ограниченного бюджета.

**Architecture:** существующие Training Director, TerminalRewardReceiptV1, TerminalReplayV1, TacticalStoreV3 и TacticalTrainerV3. Combat и PartyTactics сохраняют исполнение действий. Evaluator 005 расширяется отдельной явной authority для DRAFT006R1; историческая accepted-only authority остаётся строгой.

**Tech Stack:** High Five, Java 25, Apache Ant, существующая private MariaDB TEST006. Зависимости, модель, SQL schema и инфраструктура не заменяются.

**Spec:** текущее задание пользователя `/goal PHANTOM-LEARNING-006R1-OUTCOME-CLOSURE`; исходное решение и ограничения — соседний `../PHANTOM-LEARNING-006-PVE-OUTCOME/PLAN.md`, фактическая точка остановки — его RESULT.md/HANDOFF_OUTPUT.md/EVIDENCE.json. При конфликте текущее задание пользователя имеет приоритет.

## Global Constraints

- Source base: `5a38d35194c914c5dda90f8d57763a916201e977`; единственная ветка записи/публикации `wip/phantom-learning-006r1`. Existing independent clone используется повторно.
- Feature/master, MAIN/PLAY, dirty109, прежние TEST/SQL/checkpoints/task artifacts не изменяются. Новые многогигабайтные копии не создаются.
- Старые 16 slots, 9 receipts, complete marker и split identities неизменны. Новый campaign `learning006r1`, dataset `outcome006r1-v1`, context prefix `outcome006r1|`; отдельный каталог `outcome006/campaigns/learning006r1`.
- Отсутствующий campaign key означает прежнюю `outcome006/collection`, с прежним лимитом 16; никакого автоматического переноса или очистки.
- Reward contract и SHA `3d1ae88c1e8cfc2da6e7c465254b5e8a561373c5fc047b8d0947f63c9f48f8ae` сохраняются. Новая оболочка dataset не освобождает natural episode/goal/action identities от exactly-once guards.
- Только TRAIN получает gradients. TUNING служит selection; AUDIT и EVAL никогда не обучают. EVAL005 не используется для fit.
- Fit parent: optimizer8 SHA `56cc26c43a2b2edfba23bb2a6e56a207dd10ca7243af7796611a10d7b1f58c0f`, 769 updates, 124 epochs, seed 183; terminal weights SHA `fad52efe1757ae86b234314c8c15ece54b281fdcd3de370f8d6a215f3179709c`. Selected8 с SHA `ab62799a566c4cf52807978a12bea023be864d58915b3a72beaf7934d6a96016` не подставлять.
- Новый checkpoint остаётся DRAFT. Accepted V1 №1/V3 №2 неизменны; apply-test/auto promotion/PLAY запрещены. Явный TEST ON получает exact DRAFT ID/SHA и доказательство terminal lineage.
- HEAL safeToRank, emergency safety, 5 ms, cooldown 2000 ms и margin .025 неизменны. После CUT никаких forced skills, readiness override, setup или подделки RNG.
- Один Director и только его собственные TEST процессы: SQL33357, Game18001, Login19301, loopback. Нет per-bot threads, SQL/fit/GPU/network в горячем пути.
- Ant-команды последовательны. Общий `verify` не заменяет targeted gates.
- Отчёты и пользовательский текст — читаемая кириллица. Две отдельные encoding проверки обязательны.

## Проверенный read-first срез

Прочитаны root/module AGENTS.md, README.md, CODEX_WORKFLOW_CONTRACT.md, TASK_PACKAGE_STANDARD.md, релевантные разделы PHANTOM_CURRENT_STATUS.md/PHANTOM_DEVELOPMENT_MASTER_PLAN.md; 006 RESULT/PLAN/PROGRESS/HANDOFF_OUTPUT/EVIDENCE/COMMANDS и checks006; build.xml; Training.ps1/TrainingOwner.ps1/Outcome006.ini/Evaluation005.ini; native004, Runtime001, actor allocation003, native005/evaluation005, native launcher и terminal replay/store/trainer, focused006 checks.

AGENTS.md выше корня, новые task-specific AGENTS/code-map/pattern-файлы в затронутых каталогах не найдены. Повторный поиск не нужен.

Локальные аналоги:

1. Native004/006: pre-CUT prepare → callback durable CUT → штатный GameServer → native drain → settlement и timing-bound receipt.
2. TacticalStoreV3.freezeTerminal/saveTerminal: frozen immutable rows, expected optimizer parent, model+Adam+application markers в одной транзакции.
3. Evaluator005: независимые native arms, exact физические pre-CUT facts, отдельные incarnations, неизменяемый ledger, balanced order, сохранение UNKNOWN и invalid denominator.

Подтверждены clean tracked HEAD и remote WIP006/feature/master; новая ветка создана от точного WIP006. Private TEST006: STOPPED generation7, 96 старых collection-файлов, 9 terminal JSON; процессов java/mariadbd и слушателей собственных портов не обнаружено. Inventory SHA96: `a2036279c91b92914b90075fa02440f82e05a63dc954a1aef76ddce550440f7c`.

Не проверены заново: live SQL parent traversal, baseline207/Geo203 hashes, сборки/регрессии и исправления на новой native серии. Исторические PASS не считаются свежими PASS006R1.

## Bounded exception и точный scope

Одна причинно связанная задача затрагивает campaign, terminal acceptance и native evaluator. Bounded exception: максимум **13 source/config/test paths**, 8 task/report/allowlist paths, до 8 компактных JSON evidence и один компактный native ZIP; не новая подсистема или broad refactor. Фактический subset фиксируется final scope guard. Расширение за этот список требует отдельного объяснения до правки.

Paths ниже относительно High Five:

- `java/org/l2jmobius/gameserver/phantoms/learning/TerminalReplayV1.java` — admission полной TRAIN episode/context quota.
- `java/org/l2jmobius/gameserver/phantoms/learning/TacticalStoreV3.java` — только если needed terminal campaign/idempotency guard не покрывается существующим контрактом; transaction/ancestry сохраняются.
- `java/org/l2jmobius/gameserver/phantoms/learning/TacticalTrainerV3.java` — точные fit/export/readback evidence; existing continueTraining сохраняется.
- `test/java/org/l2jmobius/tests/phantoms/PhantomLearning004Native.java` — новая bounded adaptive campaign и immutable slot ledger, legacy16 branch сохраняется.
- `test/java/org/l2jmobius/tests/phantoms/PhantomLearning001ShadowNative.java` — только необходимые TEST draft/timing evidence seams, без изменения боевых правил.
- `test/java/org/l2jmobius/tests/phantoms/PhantomTrainingNative.java` — явная маршрутизация TEST evaluation без train collector credit.
- `test/java/org/l2jmobius/tests/phantoms/PhantomLearning005Native.java` — переиспользование native setup/arms для отдельно авторизованного V3 DAMAGE и HEAL.
- `test/java/org/l2jmobius/tests/phantoms/PhantomLearning005Evaluation.java` — отдельная typed DRAFT authority, exact ID/SHA и общие compare/statistics; прежний EVAL005 constructor/admit остаётся accepted-only.
- `test/java/org/l2jmobius/tests/phantoms/PhantomLearning005Ledger.java` — versioned namespace/readback006R1, без изменения исторических записей/значения005.
- `test/java/org/l2jmobius/tests/phantoms/PhantomLearning006Checks.java` — focused campaign/authority/boundary negatives в существующем runner.
- `test/java/org/l2jmobius/gameserver/phantoms/learning/Learning006OutcomeChecks.java` — focused quota, SQL duplicate/transaction/cold readback controls; прежние509-only checks не выдаются за проверки нынешнего536 ledger.
- `tools/phantom-runtime/training/Training.ps1` — campaign marker/path/phase и единственный supervisor.
- `tools/phantom-runtime/training/Outcome006.ini` — bounded R1 configuration, safe opt-in default.

build.xml, SQL schema, модели/receipt codecs/reward config, Player/Attackable/Party writers, TrainingOwner baseline pins и Studio остаются вне allowlist. Ненужные разрешённые файлы не менять.

## 1. Campaign и RED→GREEN до дорогого native

**Interfaces:** существующие `PhantomLearning004Native.run`, `TrainingConfig.text/integer`, `TerminalReplayV1.Frozen.sufficient`, `TacticalStoreV3.freezeTerminal`, `TacticalTrainerV3.evaluate`.

- [ ] Три focused contracts: независимость legacy/R1 path и consumed-slot resume; full quota/split/old-native duplicate rejection; exact DRAFT evaluation authority с rejection старым accepted-only005. Сначала наблюдаемый RED, затем минимальный GREEN.
- [ ] Pins валидируются до CUT. Campaign ID — безопасный одиночный path component; physical path/reparse guards; campaign plan/config SHA immutable. Зарезервированный slot не повторяется после crash, pre-CUT refusal или interruption.
- [ ] Существующий deterministic scenario discovery, seed183 и lawful параметры. До первого CUT сохранить ordered scene/split/mode plan; исключить historical evaluation/audit physical contexts из TRAIN, не маскировать overlap новым prefix.
- [ ] Новые actors/targets/epochs, AUTO_INCREMENT allocation003 подтверждается фактическим manifest. До CUT сохраняются identity/native owner/target/Party/skills/Geo/LOS и реальная combat readiness; before.json снимается после всей подготовки.
- [ ] Последовательно compile-tests, focused006 и schema/lineage/negative controls, jar, baseline207/Geo203, 143 affected checks, 28 old005 contracts; F7/F8 из-за общего native evidence/evaluator scope. Exact команды/exit codes в COMMANDS/evidence.

## 2. Новая bounded collection

**Interfaces:** existing native timing observers, Runtime.prepareOutcome006/runLearning006Encounter/settleLearning004, store.stageTerminal; новый campaign namespace только внутри существующего Director.

**Ceiling:** 48 НОВЫХ slots: до32 TRAIN, до8 TUNING, до8 AUDIT. Каталог — до24 новых scene identities с заранее закреплёнными split, каждой соответствуют независимые Solo/Party попытки. Отказ расходует slot; следующий — новый уникальный slot. Quota не снижать.

**Stop quota:** TRAIN≥16 подтверждённых native actions, ≥2 COMPLETED Solo и ≥2 COMPLETED Party TRAIN episodes, ≥2 законных TRAIN contexts; TUNING≥8 actions; AUDIT≥2 distinct actors и ≥2 contexts. Native COMPLETED требует canonical kill; action completeness хранится отдельно. Реальные context/split/actor identities проверяются до freeze.

- [ ] Первые четыре TRAIN slots (два Solo, два Party) — native smoke той же кампании. На отдельной завершённой segment boundary доказать batch.complete, правильный before.json, temporal/CUT/terminal/deadline ordering, actor allocation/admission/ancestry. Старые failures сохраняются.
- [ ] Выполнить штатные pause → stop → resume между segments. Ledger не разрешает повторить consumed slots. Каждому завершённому segment нужен собственный batch.complete; campaign native.complete возникает только при quota либо исчерпании бюджета.
- [ ] После smoke выбирать следующий заранее упорядоченный slot только по дефициту фиксированной coverage quota: TRAIN, TUNING, AUDIT; не по успеху/предсказанию сети. Если split достиг quota, его ещё не зарезервированные slots не запускать.
- [ ] При общей quota collection прекращается сразу. При48 slots, временном лимите или прямом blocker — PARTIAL с denominator/reasons, без fit при insufficient quota.

Исторические9 receipts не включаются в R1 dataset; заново им training credit не назначается. Дедупликация natural episode/goal/action проверяется вне dataset wrapper.

## 3. Terminal fit и cold restore

**Interfaces:** existing `optimizationParent`, `freezeTerminal`, `TacticalModelV3.continueTraining`, `saveTerminal`, `terminalSnapshot`, `candidate`, trainer export.

- [ ] До fit fresh SQL readback подтверждает source SHA, весь optimizer parent chain8, terminal/selected weights различны, accepted1/2 неизменны.
- [ ] Freeze только новых terminal receipts с full quota и exact native row/timing provenance. Сохранить source rows SHA, terminal dataset SHA, config SHA, split counts, native action keys и unique application identities.
- [ ] Запустить существующий terminal trainer ровно от parent8. Сохранить реальные new updates/epochs/weights и DRAFT ID/SHA; gradients только TRAIN.
- [ ] Controlled interruption перед commit/rollback и repeated apply/freeze controls в собственном stopped-worker SQL доказывают отсутствие второго credit и частичного model/Adam commit. Не вставлять synthetic reward в настоящий fit dataset.
- [ ] Cold Director/worker restore: exact optimizer blob/SHA, candidate weights/SHA, application markers и dataset неизменны; повторное обучение consumed receipts не выполняется. Export компактный, SQL/checkpoints/ledger сохраняются для продолжения.

## 4. Изменённое действие и независимый A/B

**Interfaces:** Runtime native owners + существующие005 pre-CUT facts/ledger/compare/statistics. DRAFT authority namespace `EVAL006R1`, exact ID/SHA + terminal run/dataset/optimizer pin. Старый `EVAL005` продолжает отвергать DRAFT.

- [ ] Отдельный proof TEST ON: максимум4 fresh episodes (до2 Solo, до2 Party). Нужен хотя бы один stock≠neural selection при том же legal set с score/margin, model ID/SHA, actor/owner epoch, target, native cast, paid HP/useful HEAL/MP и receipt SHA. Party useful paid HEAL обязателен. Нулевую divergence сохранять как результат.
- [ ] Independent A/B: A=stock, B=одна frozen DRAFT V3 для BOTH Solo DAMAGE и Party HEAL. Ни V1_DAMAGE, ни accepted2 не выдаются за новый candidate.
- [ ] Бюджет A/B: до8 attempted pairs на каждый режим, всего16pairs/32arms, включая smoke по1pair на режим. Цель≥4 честных COMPLETED comparable pairs каждого режима, из них2 A→B и2 B→A. До4 attempted pairs на каждый порядок/режим; invalid pairs не заменяются повтором того же identity.
- [ ] После small smoke проверить физические facts/receipt authority и только затем основную серию. Два stock-vs-stock controls (один на режим), до4 дополнительных arms, отдельно от A/B.
- [ ] Внутри pair равны class/level/skills/equipment/HP/MP/CP/NPC/Geo/LOS/Party/config facts. Fresh actor/target epochs различны; GameServer RNG независим. Все invalid/incomplete/unmatched arms сохраняются.
- [ ] Native outcome и actionEvidenceStatus раздельны; UNKNOWN deaths/economy/loot не заменяются0. При малой выборке или недостаточной информации verdict INCONCLUSIVE. FULL technical chain не означает преимущество.

## 5. Ресурсы, финальные gates и публикация

- [ ] Общий ceiling собственных GameServer starts —8; native wall time —180мин суммарно (collection≤90, proof≤20, evaluation/controls≤70, включая их bootstrap). При достижении лимита — controlled stop, сохранение PARTIAL. Это максимумы, не требование расходовать весь бюджет. Offline compile/fit/readback измеряются отдельно.
- [ ] Измерять actual process CPU/peak RAM, starts, preparation/episode duration; policy p50/p95/p99 с n, allocation/fallback/overBudget и причины ranking. Недоступное значение UNKNOWN, без подмены cost GameServer временем scorer. CPU4/Xmx4096MiB сохраняются.
- [ ] Повторять после изменений только затронутые проверки; финальные code review и exact scope guard; обе encoding проверки отдельно. Новых test projects, Reflection shell harness или stress loops нет.
- [ ] G0 source/ownership/baseline; G1 native coverage; G2 actual terminal fit/atomic persisted DRAFT; G3 changed paid action+Party HEAL; G4 independent A/B; G5 negatives/no double credit/split leakage; G6 cold recovery; G7 regression/performance; G8 docs/publication/readback/postflight. Каждый PASS/PARTIAL/BLOCKED/NOT_RUN по фактическим evidence.
- [ ] Все собственные процессы остановить и порты освободить. Старую collection SHA96 перепроверить; SQL/checkpoints/ledger не удалять. Временные staging-файлы чистить только после подтверждённой публикации и проверки exact paths.
- [ ] Ordinary scoped commit/push только `wip/phantom-learning-006r1`, без force. Независимые remote SHA/file readback; feature/master hashes до/после. Runtime secrets/datadir/full geodata/binaries не публиковать.
- [ ] FULL006_OUTCOME_CORE только при всей обязательной доказанной цепочке. Иначе честный PARTIAL с конкретной причиной; final manual gate REQUIRED не принимается агентом.

## Review Focus

1. Новый prefix может скрыть старый physical split/context: проверять physical scene и global native actor/action/episode identities, не только строки dataset.
2. Crash между reservation/CUT/SETTLE не должен разрешить повторный бой: interrupted slot consumed и помечен UNKNOWN, без training credit.
3. Четыре train episodes могут дать16 actions только в Party: episode/mode quota должна блокировать такой fit при отсутствии2Solo.
4. DRAFT authority не должна попасть в accepted-only005 или training recovery: wrong ID/SHA/status/dataset/split reject, прежние28 checks остаются GREEN.
5. Changed choice без paid effect, late HEAL или неполные pair facts не доказывают changed native action/comparable outcome; сохранять raw evidence и отдельный статус.

## Gate перед реализацией

План подготовлен после read-first. Пользовательские AGENTS.md instructions явно требуют: «если задача крупная, сначала предложи план и дождись подтверждения». До подтверждения разрешены только подготовленные branch/docs/read-only проверки; source edits, SQL starts, native collection и fit НЕ выполняются. После подтверждения — автономное выполнение всей одной цели, без промежуточных запросов ручных запусков; остановка только на реальном blocker или final manual gate.
