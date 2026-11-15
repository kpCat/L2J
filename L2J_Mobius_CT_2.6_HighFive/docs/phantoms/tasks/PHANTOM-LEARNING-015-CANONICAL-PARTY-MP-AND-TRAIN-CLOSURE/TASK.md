# TASK015 — CANONICAL PARTY MP PREVIEW → LAWFUL TRAIN CLOSURE

## 1. Идентификатор и цель
`PHANTOM-LEARNING-015-CANONICAL-PARTY-MP-AND-TRAIN-CLOSURE`. **Goal №9/13**, остаётся **4**.

Построить минимальный, консервативный read-only контракт необходимой MP-осуществимости в **существующем** каноническом `L2jPhantomPartyBackend`, использовать его для раннего отказа непригодного TRAIN Party-кандидата, добиться полного законного TRAIN cohort и, если допустимо, получить настоящие native learning receipts и один offline FIT. Производственный Combat/Party-планировщик, buff-maintenance, V3 objective и безопасность неизменны.

Не требовать гарантированной победы. Результат G3 — готовность к честному TRAIN, не выигрыш нейросети. Реальный fitted checkpoint — DRAFT, пока независимая будущая V2 EVAL не подтвердит преимущества.

## 2. Зависимости и источники
- Прочитать root `AGENTS.md`, модульный `Agents.md`, `CODEX_WORKFLOW_CONTRACT.md`, relevant `PHANTOM_CURRENT_STATUS.md` и master plan.
- Обязателен разбор `docs/phantoms/tasks/PHANTOM-LEARNING-014-FAST-LAWFUL-COHORT-AND-SKILL-AUDIT/{RESULT.md,PROFILING_RESULT.md,SKILL_AUDIT_RESULT.md,BUFF_AUDIT_RESULT.md,EVIDENCE.json}` и 013 F3 `F3_RESULT.md`.
- Read-first: `java/.../party/L2jPhantomPartyBackend.java` (особенно `groupViable`, MP formula, `autonomousFarmTarget`, `autonomousFarmEncounterReady`), `PhantomPartyFarmEligibility.java`, `PhantomAutonomousFarmParty.java`, `PhantomPartyTactics.java`, `test/java/.../PhantomAutonomousTrainingNative.java` (`planCells`,`admitHeadlessCell`,`prepareSources`), `PhantomLearning001ShadowNative.Runtime.prepareAcquired`, `AutonomousTrainingV1`, `PhantomSkillInventory`, `ActionAdvantageV2`, `Training.ps1`.
- Проверить source of truth для MP и класса, реальных learned/enchanted skills, gear/stats/regen/buffs, native target, selection, risk and policy before adding seam.

## 3. Историческая baseline, не переписывать
014 `PARTIAL`, G3 FAIL, 2 readiness = 787906 ms, 1 complete Solo A/B cell, Party110 only STOCK arm, 0 seal, 0 GS, 0 FIT. 014 измерила 269003.72 ms recovery перед двумя Party MP refusals: `required=3060`, восстановленный MP `2806`, штатный лимит `.8*2806=2244.8`. Сначала воспроизвести exact отрицательный кейс из frozen source, без новых раздач ресурсов и фиктивной победы.

Исторические: accepted TEST model2, DRAFT14, terminal Adam903 updates/223 epochs, selected14 SHA `7d3c8fc1f2ae5fc199cfb7517f71743189fdd3fbb4b959a318753637e2d3bc37`, applications48, gradients16, pointer12 (SHA `acc21bed...`), SQL183 rows SHA256 `9a9c2d0ee9a2b8f37da5dd6bcf625334b76be96fe9b76e22490ed20b4da70e7d`. Проверить FULL SHA в прежнем evidence. Geo203/protected207, MAIN/PLAY, release057–061, чужие SQL и untracked MAIN не трогать. Старые журналы и прежние budgets не сбрасывать.

## 4. Точная архитектура
Один **necessary-only** preview в каноническом Party-планировщике. Результат `PROVEN_IMPOSSIBLE` допустим только при pin exact actor/roster/target/loadout/learned version/game configuration, доказанных bounds и общем источнике арифметики `groupViable`. В остальных случаях `UNKNOWN`/`MAYBE`, далее существующая подготовка/живой admission. Ни при каких условиях preview не становится разрешением атаковать или обучаться.

Нельзя просто копировать `actions*costs > currentMp*0.8` в TEST: расход `actions` вычисляется из projected duration, native DPS, regen, skills, champion/divisor, selected loadout и состава. Запрещён второй вычислитель, который молча расходится со штатным. Предпочтение — минимальное извлечение *общего чистого* расчёта/фактов из того же backend; его production use обязан оставаться семантически/байтово эквивалентным (кроме вызываемого только TEST preview API).

Предварительная MP-граница относится к **максимально достижимому легальному MP в данном зафиксированном pre-CUT сценарии**, а не к текущему MP. Если возможны неизученные изменяемые effects, dynamic loadout, смена цели, buffs/consumables, неучтённый regenerating resource или изменение коэффициентов, нельзя объявлять невозможность — вернуть `UNKNOWN`. Доказательство, что `requiredLowerBound > 0.8 * admissibleMpUpperBound`, только при консервативно верных lower/upper bounds и exact pin. Число 3060/2806 — диагностический пример, не hardcoded rule.

Never mutate source actors, skill effects, stats, SQL, learned skills, MP/HP, cooldown, mob positions, target ownership or RNG ради прогнозирования. No cached mutable admission. Native full recheck перед CUT обязателен даже если preview MAYBE/POSSIBLE.

## 5. Scope на исходники (предел 9 существующих путей)
1. `java/org/l2jmobius/gameserver/phantoms/party/L2jPhantomPartyBackend.java` — единственный production entry, **только read-only seam/консервативный pure helper**, без новой scheduler/effects механики.
2. `java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyFarmEligibility.java` — **только при доказанной необходимости** извлечь общий pure bound, иначе не менять.
3. `test/java/org/l2jmobius/tests/phantoms/PhantomAutonomousTrainingNative.java` — early negative check, progression of discovery and bounded test receipts.
4. `test/java/org/l2jmobius/tests/phantoms/PhantomLearning001ShadowNative.java` — только instrumentation/pinning/full recheck при необходимости.
5. `test/java/org/l2jmobius/tests/phantoms/PhantomPartySuite.java` — DB-free canonical preview regression.
6. `test/java/org/l2jmobius/gameserver/phantoms/learning/AutonomousTrainingChecks.java` — stable TEST contract/negative cases.
7. `tools/phantom-runtime/training/Training.ps1` — bounded 015 readiness and phase accounting при необходимости.
8. `tools/phantom-runtime/training/Test-Training.ps1` — focused owner/session cancellation and budget tests.
9. `build.xml` — только минимальный focused target при необходимости.

Это maximum allowlist, не требование менять все 9. Новые source, `PhantomPartyBackend.java` и иные production файлы — **не разрешены**, при реальной необходимости согласовать явным owner scope before write. Docs/task/reports and roadmap отдельный разрешённый документационный scope. Исторический 014/013 не редактировать.

## 6. Алгоритм проверки и оптимизации
A. Read-only audit G0/frozen SQL, exact candidate provenance, native stock selection, cost/MP/target physical facts.
B. Сформулировать доказуемый necessary predicate с проекцией разных состояний: `PROVEN_IMPOSSIBLE`, `UNKNOWN`, `MAYBE`. Если для кейса 3060/2806 невозможно доказать целевой maximum MP без новой семантики — доказать `UNKNOWN`, объяснить владельцу options и не маскировать отказ жёстким предсказанием.
C. Negative unit/property tests на false refusal с изменением buffs/enchant/target/loadout, proof strength и reset/restart; только потом production minimal seam.
D. In TEST применять preview ДО expensive roster materialization/natural recovery, но после достаточной source/target идентификации. Сохранять исходные candidate denominators, fairness, deterministic cursor/seed и reasons. Не ломать корректный выбор более экономного skill/alternative/Party actor.
E. Candidate pool должен продолжать искать другие lawful варианты, а не застревать на одной роли. Не hardcode классы94/103/110, SkillIDs, NPC, координаты. Проверять минимум три полные разные cells/две classes/Solo+Party, ≥2 реальных альтернативы в relevant action family, A/B physical parity/fresh actors.
F. Headless evidence reuse только read-only verified immutable provenance, допуск *в текущем мире* переоценивается перед seal/CUT. Никаких повторов оплаченных эпизодов.
G. После полного sealed TRAIN, только тогда native GS collection (без автоматического действия на MAIN). Если legal samples достаточны по unchanged ActionAdvantageV2, разрешён **один** offline FIT (не более) с отдельным selected/terminal Adam, независимыми TRAIN/TUNING/AUDIT. Если sample insufficient — no FIT, сохранить объяснение.

## 7. Budget и lifecycle
- Baseline/preflight offline/DB-free до расходования live.
- Не более **2** новых headless readiness sessions, **суммарно ≤20 минут** wall включая SQL boot, worker и cleanup. Делить не более 480/720 секунд при сохранении existing immutable issued budget semantics; не возвращать непрожитые минуты как дополнительный запуск. Начинать только после positive pure proof.
- Не более **3** GS boots и **60 native-минут** суммарно, только после полного G3. Stop earlier if qualified. До 1 offline FIT, NO new independent EVAL в 015 (для избежания data leakage и раздувания scope). Вновь полученная модель остаётся DRAFT, game verdict NO_EVAL / INCONCLUSIVE.
- До **2** прямых causal fixes на утверждённой архитектуре; третий независимый blocker → PARTIAL и STOP. Никаких дополнительных сеансов/бюджета молча.
- Default-OFF, exact owner, no orphan, cancellation/headless deadline, no duplicate SQL application. Никаких synthetic scenes/damage/rewards.

## 8. Производительность и конкуренция
Preview bounded, readonly, только при планировании; no hot path SQL/large allocations/second supervisor; observe actual CPU/wall and candidate denominator. Не добавлять глобальный кэш или high-frequency log. После изменения old production Party and farm paths проходят targeted parity/performance tests. Source fingerprint change invalidates planning proofs. Session boundaries atomic and resumable using current owner.

## 9. Автоматические тесты / команды
Конкретная матрица `TEST_MATRIX.md`. Сначала DB-free RED→GREEN, включая upper-bound conservative cases и normal Party parity; затем targeted Ant + JAR. Native play разрешён только при полном headless G3. Не запускать общий `ant verify` и чужие SQL. После тестов exact cold 5 SELECT, postflight 203 Geo/207 protected, UTF8/mojibake/escaped Cyrillic, Git scope/readback.

## 10. Финальная отчётность и Git
Docs в `docs/phantoms/tasks/PHANTOM-LEARNING-015-CANONICAL-PARTY-MP-AND-TRAIN-CLOSURE/`: `IMPLEMENTATION_PLAN.md`,`PROGRESS.md`,`RESULT.md`,`EVIDENCE.json`,`COMMANDS.md`,`ACCEPTANCE_RESULT.md`,`HANDOFF_RESULT.md`; reports + roadmap. Хранить отрицательные/UNKNOWN receipts и расходы. Текущая owner manual acceptance REQUIRED.

Разрешаю scoped commit и ordinary **non-force push прямо в `feature/phantom-world`**, без новых WIP/worktrees/clones, merge/master. Проверить GitHub HEAD, parent, exact diff, source + evidence full readback. При dirty main checkout беречь чужие файлы, использовать существующий безопасный checkout.

## 11. При BLOCKED/PARTIAL
Отрицательный MP forecast с `UNKNOWN`, отсутствие full cohort, недостаток gradient или независимой валидации **не оправдывают** FULL. Не повышать веса на production. После исчерпания лимитов — остановить; всё проверенное с сохранением данных опубликовать с честным verdict. Новый Goal самостоятельно не начинать.
