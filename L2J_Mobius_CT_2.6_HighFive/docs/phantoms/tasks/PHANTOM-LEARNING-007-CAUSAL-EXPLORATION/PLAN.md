# PHANTOM-LEARNING-007-CAUSAL-EXPLORATION — диагностика и проект решения

> Execution007: дизайн явно одобрен владельцем. Выполнение и operational rulings — в PROGRESS; актуальный результат — RESULT. Ниже сохранён исходный read-first план.

`/goal PHANTOM-LEARNING-007-CAUSAL-EXPLORATION`

Статус: **PLAN_REVIEW_REQUIRED**. Это конкретное предложение до реализации, а не завершённый Goal. Новых GameServer starts **0/8**, native wall **0/90 минут**. Product code, SQL schema, обучение и новые игровые эпизоды ещё не изменялись. Требуется согласование крупного изменения по инструкции владельца; final manual acceptance также остаётся REQUIRED.

## 1. Source of truth и read-first

Исходный HEAD `3bfdce560a24882519a3358d405bfe4b99bb25de`; существующий independent clone TEST006, новая локальная ветка `wip/phantom-learning-007`. Clone не создавался. Main checkout не изменяется.

Прочитаны корневые AGENTS.md обоих checkout, README.md, `docs/phantoms/PHANTOM_CURRENT_STATUS.md`, `CODEX_WORKFLOW_CONTRACT.md`, RESULT004/005/006/006R1, HANDOFF/COMMANDS/PLAN и evidence006R1; далее TacticalModelV3, TerminalReplayV1, TacticalTrainerV3, TacticalStoreV3, TacticalPolicyV3, LegalActionV3, TacticalObservationV3, TacticalReceiptV3, TerminalRewardReceiptV1, LearningPolicy, PhantomCombatService, L2jCombatBackend, PhantomPartyTactics, native004/005/006 checks и Training/TrainingOwner scripts. Пути в этом пакете относительно High Five, если не указано иначе.

AGENTS.md выше repository root не найдены. В релевантных `java`, `tools`, `docs` дополнительные AGENTS.md и отдельные pattern-файлы не найдены. Общие docs существуют. Workflow contract находится в `docs/phantoms/`, не в корне модуля. Отдельный code-map для learning в выполненном bounded inventory не найден; широкий поиск по проекту не проводился.

Локальные аналоги: immutable native receipt/atomic terminal save006; bounded shared scorer/Adam continuation004; canonical ready-candidates и native cast в Combat; safeToRank/dispatch в Party; immutable comparator/ledger005 и отдельная DRAFT authority006R1. Переиспользуются они, без второго игрового executor или нового supervisor.

Java25/Ant, существующие model/action/observation codecs, fingerprints, base terminal reward и зависимости сохраняются. WinForms/Studio, Semantic Pack и прочие хроники вне scope.

## 2. Актуальные refs, TEST и cold readback

`git ls-remote --heads origin ...` exit0 подтвердил:

|Ref|SHA|
|---|---|
|feature/phantom-world|7e997636dbac9d5e1754e00c793cd6fac858b0c6|
|master|16d61833b3983a3976583d0e4813e0de9457a52f|
|wip/phantom-learning-006|5a38d35194c914c5dda90f8d57763a916201e977|
|wip/phantom-learning-006r1|3bfdce560a24882519a3358d405bfe4b99bb25de|
|wip/phantom-learning-007|ещё не опубликован|

Независимый GitHub compare: WIP006R1 и текущий feature **diverged**, общий предок `dfcba154fa1f459be6082a559744d2b4a5c9b47a`, WIP ahead3/behind1. Локальный feature ref в TEST006 устарел; попытка local merge-base с отсутствующим новым feature object завершилась exit1 и не считается проверкой ancestry. GitHub compare разрешил этот вопрос. Ветка 007 создана от заданного exact WIP, merge с feature не выполнялся.

Штатный `Training.ps1 -Action Preflight -ConfigProfile OUTCOME006 -EnableOutcome006` exit0: JDK25, MariaDB11.4, exact protected207/Geo203 и свободные SQL33357/Game18001/Login19301. Status exit0: STOPPED generation16, live Director отсутствует.

Штатный cold Export exit0 запустил и остановил только собственный loopback SQL/worker. GameServer не запускался. Экспорт проходит `optimizationParent()` с полным descending SQL parent traversal, restore model/Adam, frozen replay и terminal marker validation. Новый archive `model-2-944dc10b9be74beabf111131c0e8a6e7.zip`, SHA `967b7047c7166cf29d436d365dc7d8289b542feac9f31b42964e862a9e97210a`. Все payload entries, кроме timestamp-bearing manifest, побайтно равны прежнему verified archive; 13 application markers. Это fresh cold lineage readback, не новый fit и не controlled crash.

Selected DRAFT9 SHA `5eacf0b7619c82de3950c0203fcf599c149156d13d31d67cfc9e408362b416dc`; optimizer `f76ea960288f3cdb1ba85bb203a33cb327937fdffb99bc6bc4503b90cd54990c`, updates817/epochs172. **Terminal optimizer weights SHA `dc82041d52cb92918990a64d6d003931139e20d82e3a02cd3a973559994158ed`**. Только эти terminal weights с их moments являются parent007. Selected weights не соединяются с terminal moments. Accepted2 SHA `6b91706463f4e24f89f1c4f8be3a82d9ee58eadb07a4254c79449665e3fa97dc` сохранён.

## 3. Диагностика до новых native запусков

### Почему модель не меняет действия

Канонический unchanged Java scorer восстановил 44 архивных paid observations (proof, основной A/B и controls), проверяя observation SHA и совпадение selectedScore/stockScore/margin с опубликованными source receipts с допуском 1e-6. Для **16 learned paid actions основного independent A/B** neural top совпал со stock8/16 и отличался 8/16. Для DAMAGE совпадений 6/12, HEAL2/4. Следовательно, проблема не сводится к постоянному совпадению top со stock.

Все 16 gap ниже `.025`: DAMAGE `.0072920322–.008579075`, HEAL `.0035577416–.015597224`. Scores всех legal альтернатив: DAMAGE `.69674826–.70691687`, HEAL `.7711962–.7871821`. Обязательный margin gate возвращает stock даже в 8 случаях с отличающимся top. Это offline реконструкция исторических наблюдений; native receipt подтверждает исполненный stock, а не принятие offline neural top.

### Причины отказов и предел счётчиков

Сумма опубликованных policyDiagnostics по proof/A/B/control arms006R1: MARGIN22, BUDGET_5MS5, HEAL_OFFER_COUNT1372, COOLDOWN_OR_ACTOR_CAP1011, SHADOW27, DAMAGE_STOCK37, HEAL_STOCK12. Отдельные safety reason keys в этом опубликованном наборе не зарегистрированы. Это не доказывает отсутствие опасных ситуаций. Счётчики не взаимоисключающие: MARGIN/BUDGET и итоговый STOCK могут относиться к одному решению; суммировать их как число эпизодов нельзя. Cooldown и actor cap объединены. Ready/cast checks, сработавшие до policy либо в native dispatch, не имеют полного отдельного исторического census: их число **UNKNOWN**, не 0.

### Чему обучает старый target

`TerminalReplayV1.Frozen.examples()` передаёт один episode.reward каждому executed action эпизода. TRAIN006R1:7 эпизодов,16actions; все 7 — native kill; rewards `.6623064,.75001025,.7513083,.70435375,.6810777,.6799366,.74582195` в canonical receipt order. Нет paired action contrast или selection propensity. Такой supervised Monte-Carlo estimator может ассоциативно различать действия при достаточном покрытии, но здесь не идентифицирует их причинное преимущество и способен в основном предсказывать общий успешный исход.

Head использует stateProjection×actionProjection, leaky ReLU и tanh; accumulate содержит соответствующие произведения и производные. Очевидный дефект знака/пропущенная action derivative при чтении не найден; numerical gradient check ещё **NOT_RUN**. Отличия skillId/MP/cast/range представлены нормированными action features, но нынешние gaps малы. Вывод о необходимости новой feature schema не сделан.

### Законные альтернативы

DAMAGE: только существующий ready set из Combat loadout, после exact lease/known skill/MP/cooldown/Geo/LOS/native conditions; direct single-target damage без channel/fly/simultaneous/AoE и только current native owner. Реальные EVAL receipts показывают 1230:28 и 1339:1; это evidence, не обещание их наличия в любой сцене. HEAL: только существующие Party HEAL_MEMBER directives, минимум 2 lawful предложения, прежний safeToRank, не emergency и не under attack. Возможна альтернатива цели того же навыка, а не только другой skillId. Emergency rescue никогда не исследуется.

## 4. Выбранное предложение и альтернативы

Предложение исполнителя: небольшой contextual-bandit estimator поверх V3. Stock A даёт TRAIN baseline; B при epsilon.25 может выбрать legal alternative, один раз за эпизод. Обучается advantage только действительно executed decision B. Никаких counterfactual labels.

Продолжение прежнего terminal-only fit не решает attribution. Полноценный actor-critic/trajectory RL требует иной модели, большего объёма данных и отдельной доказательной программы. Поэтому для 007 предлагается первый вариант. **Владелец ещё не утвердил этот конкретный дизайн.**

## 5. Exploration contract

Точная кампания `learning007-causal-v1`, dataset `learning007-advantage-v1`, отдельные opt-in TRAIN и campaign pins; shipped defaults OFF. Включение допустимо только при существующем isolated TEST ownership, TRAIN split, действующем owner/actor/target epoch и canonical ready set. TUNING/AUDIT/EVAL/PLAY/MAIN всегда OFF. Config сам по себе не является native authority.

На первом полностью eligible решении каждого B TRAIN эпизода: stock с p=.75, любая из K−1 альтернатив с p=.25/(K−1), uniform. A всегда stock с p=1. Выбор использует обычный runtime RNG; боевой RNG не подменяется. Больше одного исследуемого decision opportunity в эпизоде нет; coin=stock тоже расходует opportunity. Cooldown/5ms/safety/owner failure дают обычный fallback и сохраняются; ни один gate не обходится. Exploration — отдельный явно авторизованный TRAIN selector, не learned inference и не понижение inference margin.

Сначала регистрируются action set/stock/model pin/opportunity/propensity/coin/reason/budget, затем unchanged native dispatcher, затем отдельный exact cast+HP/MP+terminal receipt. Eligibility, refusal, fallback, no paid effect и SKIP сохраняются. Action event key/SHA связывает selection и effect. Бюджет резервируется durable до выдачи; interrupted episode не возвращает spent slot и не получает gradient.

Разделение отказов фиксируется заранее: неверный installation/campaign/split opt-in, повреждение durable budget/manifest или уже исполненная вне safety exploration — немедленный STOP кампании. Обычная смена owner/epoch/readiness/safety между snapshot и dispatch — stock fallback/отказ этого decision, без target и без повторного coin в том же episode; следующий episode разрешён только после обычных current guards. Исчерпанный общий budget запрещает новые episodes, а отдельный 5ms overrun даёт stock fallback. Fallback никогда не превращается в emergency action override.

## 6. Advantage objective: CAUSAL_ACTION_ADVANTAGE_V1

Формулы и protocol pins фиксируются до native collection; см. EXPERIMENT.md. Старый TerminalRewardReceiptV1 и его reward bytes не меняются.

`L = clip(2 * usefulHp / targetMaxHp - paidMp / 500 - effectElapsedNanos / 10e9, -1, 1)`.

`U = .70 * terminalRewardV1 + .30 * L`.

`target = clip(4 * (U - baseline), -1, 1)`.

TRAIN baseline — среднее U только stock A TRAIN решений того же заранее объявленного comparison-context; для B своего блока A исключается (leave-block-out). Минимум 3 независимых других A receipts; иначе target не допускается. Один native paid decision на episode. A нужен только baseline, его target не получает gradient. Каждый B TRAIN decision, включая coin=stock, даёт не более одного training application. При death/timeout без доказанного paid decision эпизод остаётся в outcome census, gradient отсутствует.

Loss — weighted squared error относительно executed advantage: `w = min(4, 1 / (K * propensityB))`, нормированный по среднему w только TRAIN. Это clipped inverse-propensity correction к uniform policy внутри наблюдаемого legal set; clipping и condition-on-paid selection оставляют bias, small-sample variance велика. Модель не оценивает неисполненный counterfactual. При K2 веса до normalization: stock2/3, alternative2. Возможность gradient изменить score order проверяется DB-free на controlled distinct outcomes и отдельных held-out observations, не на MSE-only.

Необходимы узкие weighted-gradient/weighted-loss entrypoints с сохранением старого unweighted API/codec. TUNING/AUDIT не участвуют в baseline, propensity normalization, reward calibration и градиенте. Их stock executed actions могут иметь диагностические targets от frozen TRAIN baseline; неподходящий context получает UNKNOWN, не подставной baseline. Они не обеспечивают off-policy outcome coverage альтернатив. Ранжирование всех их реальных legal candidates проверяется отдельно без присвоения outcome неисполненным действиям.

## 7. Предварительно ограниченная native серия

TRAIN:32 matched block attempts,16Solo/16Party, balanced A→B/B→A;64episode slots,32B coin opportunities. Четыре frozen physical context cells, по 8blocks:2Solo/2Party. Внутри cell одинаковые class/level/skills/gear/enchant/HP/MP/CP/NPC/position/Party/Geo/rates/objective; incarnations свежие. Variation between cells объявляется до запуска из фактического catalog. При K2 ожидается 8alternative selections на 32B, но это ожидание, не гарантированная квота. После исчерпания slots retries ради divergence нет.

TUNING8episodes и AUDIT8episodes с fresh actors/episode/context identities, по обоим режимам, stock и explorationOFF. Полный episode и все его actions принадлежат одному split. Физические comparison cells могут повторять TRAIN distribution для frozen TRAIN baseline, но actor/target incarnations, context keys и observation records независимы. Отдельный holdout с изменённой экипировкой допускается только как UNKNOWN-target score diagnostic, без подмены exact baseline.

Бюджет на collection — до 60native minutes, включает bootstrap/preparation/drain, максимум 3starts. Первый start — bounded smoke, slots входят в общий 64/8/8 ledger, не новая серия. Cold recovery и fault worker без GameServer. Остаток до 90 мин/8starts — proof и EVAL; unused starts не разрешают новые trials. Если 80episode slots не укладываются, сохраняется неполная выборка; бюджет автоматически не увеличивается.

Frozen DRAFT EVAL: максимум 4pair attempts Solo и 4Party, включая proof pair каждого режима, order2/2. Отдельно 1stock-stock control pair/mode. Всего 20EVAL arms. Неудовлетворённый minimum4fair pairs после rejected/incomplete означает PARTIAL, не замену попыток до красивого результата.

## 8. Последовательность реализации и gates

1. После design approval: exact source allowlist/backup и focused RED→GREEN contracts: TRAIN-only permits+single opportunity; weighted objective меняет относительный score на controlled outcomes; propensity/split/duplicate/native linkage.
2. Полная повторная baseline/ownership проверка; canonical compile-tests и jar строго последовательно в TEST006. Без `ant verify` и чужих DB.
3. Один bounded native smoke: доказать paid exploration≠stock с receipt либо сохранить отказ; продолжить только в том же заранее объявленном ledger.
4. Закрыть bounded TRAIN/held-out collection. Freeze targets/baselines/propensities/run/replay SHA до fit. Продолжить только terminal Adam9. Новый model остаётся DRAFT.
5. TEST-only pre-COMMIT fault: worker останавливается на barrier после model/Adam/application writes, до commit; родитель проверяет exact own process и завершает только его. SQL disconnect rollback; новый cold process проверяет отсутствие partial model/optimizer/applications, сохранённый FROZEN snapshot и отсутствие повторного credit, затем выполняет ровно один successful retry.
6. Offline независимые score distributions, gaps, stock/model, feature perturbation, coverage и negatives. G4 admission заморожен: минимум 16fresh native held-out observations, минимум 4 на каждую комбинацию TUNING/AUDIT×Solo/Party, в каждом canonical legal set≥2. Для допуска каждого режима нужны минимум 2observations с neuralTop≠stock и gap≥.025, включая≥1TUNING и≥1AUDIT. Дополнительно controlled disjoint outcome tests должны менять score order и проходить weighted-gradient check. Уверенное совпадение со stock gate не закрывает. Массовый EVAL разрешён только для прошедшего режима; непрошедший получает NOT_RUN, G6 полного Goal остаётся незакрытым. AUDIT используется однократно для admission/report, не для выбора reward/hyperparameters или повторного fit. При defect fix нужны новые unused audit identities внутри исходного бюджета, иначе PARTIAL. Параметры objective не подгоняются по EVAL.
7. ExplorationOFF proof/EVAL, exact frozen DRAFT authority, no writer/fit. G5 только по neural≠stock/no fallback/current same legal set/native paid cast/terminal. Party useful stock HEAL отдельно от neural intervention. Все failures и UNKNOWN в denominator.
8. Cold STOP/RESUME, CPU/RAM/latency/throughput, scoped review/encoding/архив, ordinary non-force publication только wip/phantom-learning-007 и независимый GitHub readback. Final manual gate REQUIRED.

## 9. Bounded exception и scope

Одна причинно связанная цепь selection→receipt→advantage→atomic checkpoint→inference; она прямо требуется заданием. Предлагаемый bounded exception: максимум 18source/config/test paths ниже и 14delivery paths текущего пакета. Это не разрешение автоматически менять все 18 файлов. Actual subset и exact staged allowlist проверяются перед публикацией. Расширение требует объяснения до правки.

Все source paths относительно High Five:

1. `java/org/l2jmobius/gameserver/phantoms/learning/TacticalPolicyV3.java` — guarded selector в существующем owner.
2. `java/org/l2jmobius/gameserver/phantoms/learning/TrainExplorationV1.java` — новый bounded permit/decision/budget contract, без executor.
3. `java/org/l2jmobius/gameserver/phantoms/learning/ActionAdvantageV1.java` — validated objective snapshot/target/propensity, без native execution.
4. `java/org/l2jmobius/gameserver/phantoms/learning/TacticalModelV3.java` — weighted loss/gradient entrypoints, старый codec и методы сохранены.
5. `java/org/l2jmobius/gameserver/phantoms/learning/TacticalTrainerV3.java` — отдельный objective route/frozen targets/optimizer continuation.
6. `java/org/l2jmobius/gameserver/phantoms/learning/TacticalStoreV3.java` — atomic objective/applications/cold validation и fault barrier.
7. `dist/db_installer/sql/game/phantom_tactical_v3.sql` — только additive advantage snapshot/application tables, без изменения старых rows.
8. `test/java/org/l2jmobius/tests/phantoms/PhantomLearning004Schema.java` — exact additive migration нового собственного TEST, previous manifests immutable.
9. `test/java/org/l2jmobius/tests/phantoms/PhantomLearning007Native.java` — campaign adapter над existing native runtime/owners, не executor.
10. `test/java/org/l2jmobius/tests/phantoms/PhantomLearning005Native.java` — минимальные shared setup/arm seams и отдельный EVAL007 namespace.
11. `test/java/org/l2jmobius/tests/phantoms/PhantomLearning005Evaluation.java` — typed frozen authority007, сохранение legacy005/006 semantics.
12. `test/java/org/l2jmobius/tests/phantoms/PhantomLearning005Ledger.java` — versioned007 immutable pins/records; historical namespaces read-only.
13. `test/java/org/l2jmobius/tests/phantoms/PhantomLearning001ShadowNative.java` — только если нужен native setup/test policy binding.
14. `test/java/org/l2jmobius/tests/phantoms/PhantomTrainingNative.java` — opt-in campaign routing existing Director.
15. `test/java/org/l2jmobius/gameserver/phantoms/learning/Learning007Checks.java` —3focused groups, включая SQL worker modes; не новый test project.
16. `tools/phantom-runtime/training/Training.ps1` — opt-in007/phase/budget в existing supervisor.
17. `tools/phantom-runtime/training/Exploration007.ini` — defaultsOFF и frozen protocol pins.
18. `build.xml` — только targeted007 route existing compile-tests runner при необходимости.

Не требуется менять PhantomCombatService, L2jCombatBackend, PhantomPartyTactics, Player/Attackable HP/MP writers или их игровые правила. Если реализация покажет такую необходимость, сначала новый scope review. Baseline207/Geo203, MAIN/PLAY, accepted1/2, DRAFT8/9, старые optimizer/run/application/EVAL records и чужие worktrees неизменны.

## 10. Что ещё не проверено

Реализация, RED→GREEN, сборки 007, numerical weighted gradient, новый schema upgrade, actual TRAIN alternative, advantage fit, fault crash, G4 independent discrimination, learned native divergence и EVAL007 **NOT_RUN**. Формулы — заранее опубликованное предложение, не empirically tuned reward и не доказательство преимущества. Новый partial/fix goal тоже расходует development task budget.007 уже задача 1/13 после 006R1; до review threshold остаётся 12.

## Implementation plan — approved design execution

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:executing-plans; шаги отслеживаются в PROGRESS.md.

**Goal:** законный TRAIN exploration, обучение executed action advantage и проверяемое самостоятельное inference решение V3.

**Architecture:** существующие Combat/Party owners и Director остаются единственными исполнителями. Новый selection envelope связывается с прежним native receipt; advantage snapshot и application markers сохраняются атомарно с прежним V3/Adam codec.

**Tech Stack:** Java25, Ant, собственная MariaDB11.4; зависимости не добавляются.

**Spec:** разделы 1–10 этого PLAN и EXPERIMENT.md. Владелец подтвердил PLAN сообщением «Подтверждаю план, продолжай реализацию».

### Global Constraints

- Epsilon.25, один decision opportunity/episode; только TRAIN B и exact campaign.
- TRAIN32 matched block attempts; held-out8+8;8GSstarts/90min, collection≤3starts/60min.
- Никаких изменений safeToRank/emergency,5ms,cooldown2000,margin.025 и штатного cast.
- Продолжение только terminal Adam9 f76ea960…; DRAFT остаётся TEST-only.
- Source exception18 paths и delivery14 paths, без расширения независимых подсистем.

### Review Focus

1. Stale owner между выбором и cast — native отказ, no target; проверка native linkage.
2. Eligibility/5ms fallback после coin — spent opportunity, no training credit; selection negative.
3. Разные legal sets/physical contexts в блоке — unmatched, no invented counterfactual; objective negative.
4. Crash после model/Adam writes — SQL rollback и ровно один retry; собственный fault worker.
5. AUDIT/EVAL попадание в baseline/normalization/fit — deterministic rejection; split negative.

### Task1: selector и математический контракт

Files: TrainExplorationV1.java, TacticalPolicyV3.java, TacticalModelV3.java, Learning007Checks.java.
Interfaces: validatePermit(isolated,optIn,phase,campaign,split); pick(candidateCount,stockIndex,draw,experimental); WeightedExample(Example,double); continueTrainingWeighted(Optimizer,List<WeightedExample>,List<Example>).
- [ ] Написать selection/mathematics focused groups и увидеть RED из-за отсутствия 007 API.
- [ ] Реализовать permit/propensity/single-opportunity contract и weighted gradient/loss без изменения старого API/codec.
- [ ] Проверить запрет остальных splits и изменение относительного score на disjoint controlled observations. Expected: groups PASS, native=NOT_RUN.

### Task2: objective, frozen targets и atomic store

Files: ActionAdvantageV1.java, TacticalStoreV3.java, TacticalTrainerV3.java, phantom_tactical_v3.sql, PhantomLearning004Schema.java, Learning007Checks.java.
Interfaces: validated executed Selection+TerminalRewardReceiptV1 → Frozen advantage snapshot; save/resume в прежнем store/optimizer chain.
- [ ] RED: чужая selection/episode/split, baseline из held-out, duplicate action, отсутствие 3otherA, неверная propensity.
- [ ] GREEN: canonical frozen snapshot/own-SQL additive tables, TRAIN-only target/normalization, compatible lineage readback/export.
- [ ] Native pre-COMMIT barrier проверяется отдельно после настоящей collection; synthetic SQL game evidence не создаётся.

### Task3: existing Director/native campaign adapter

Files: PhantomLearning007Native.java и approved minimal seams001/005/TrainingNative, Training.ps1, Exploration007.ini; build.xml только targeted route при необходимости.
- [ ] RED: routing/split/config defaultsOFF, immutable protocol/budget.
- [ ] Реализовать fixed slots/physical cells/balanced order через прежние prepare/run/settle/native owners.
- [ ] GREEN: canonical compile-tests/jar последовательно; targeted007 и affected legacy checks.
- [ ] До первого GS записать exact physical protocol SHA, campaign/config/JAR pins и reservation counters.

### Task4: collection, fit, fault, G4

- [ ] Штатный preflight; bounded native smoke входит в общий ledger.
- [ ] Доказать lawful paid exploration≠stock либо сохранить честный отказ; завершить ограниченную collection.
- [ ] Freeze baseline/targets/run; продолжить terminal Adam9, inject own pre-COMMIT worker failure, cold rollback/readback и single successful retry.
- [ ] Отдельные independent offline G4 diagnostics с фиксированными критериями PLAN§8. Expected: реальное различение либо честный NOT_RUN последующего режима.

### Task5: frozen inference EVAL и доставка

- [ ] ExplorationOFF, no fit credit, exact frozen DRAFT; proof включён в 4pair attempts/mode плюс controls.
- [ ] Сохранить receipt/paid effect/terminal, все failed/unmatched/UNKNOWN и CPU/RAM/latency.
- [ ] Final fresh-context branch review, один focused fix pass при необходимости; scope/encoding/архив/cold postflight.
- [ ] Scoped commit и ordinary non-force push только 007, независимый GitHub readback; обновить все отчёты/gates/verdict. Final manual gate REQUIRED.

Ruling: выполняю inline, как уже объявлено при подтверждении PLAN; повторный выбор способа исполнения не запрашивается. Ledger — этот task package PROGRESS.md, exact SHA overlay/backup — вне checkout. SDD shell scripts, требующие дополнительных Git-команд и файлов вне allowlist, не применяются. Существующий independent TEST006 переиспользуется согласно прямому заданию; новый worktree/clone не создаётся.
