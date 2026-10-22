# PHANTOM-FARM-ECONOMY-066-R2 Implementation Plan

> Исполнитель: текущий агент, executing-plans + test-driven-development. AUTHORITY.md прямо разрешает автономное исполнение и заменяет промежуточные согласования.

**Goal:** пожизненные 5000 совместимых No-grade сосок новым фантомам, штатное расходование и приоритетная законная закупка; закрытие общих причин 066 с честными native/runtime gates.

**Architecture:** существующие profile components и атомарный population shell; отдельная версия starter entitlement, legacy creation digest неизменен. Партии до 1000 при stock <100 в materialized native checkpoint; durable debit до выдачи, неоднозначная выдача fail-closed. Существующие Commerce/SupplyJourney, AutoPlay и Background authority остаются владельцами действий.

**Tech Stack:** Java25, Ant, MariaDB11.4, PowerShell; без новых зависимостей.

**Spec:** TASK.md, AUTHORITY.md, ACCEPTANCE.md, TEST_CASES.md, RUNTIME_INPUTS.md, READ_SET.md в этом пакете.

## Global Constraints

- Максимум12ч от 2026-10-21 23:19:39 UTC; freeze новых функций в09:49:39 UTC, окончание до11:19:39 UTC22.10.2026. Завершить раньше при результате.
- Только feature/phantom-world, exact add/commit и non-force push. Baseline/live remote5f112172f31cbf36cd3e1c0e47da48ffc77ccbde проверен до mutation.
- Все057–066 releases/БД read-only; собственные R2 root/schema/ports, штатный STOP без force. Не изменять CLIENT defaults и legacy birth hash.
- Тесты focused в существующем runner, без ant verify. Native scenes без исправления данных во время измерения. Исторические FAIL сохраняются.
- Bounded exception >10 файлов по AUTHORITY: одна цепочка birth entitlement → inventory/shot accounting → paid supply → native/background reward → isolated delivery. Exact CHANGED_FILES-R2.txt обновлять по факту.

## Read-first / локальные аналоги

Прочитаны root AGENTS/README, module Agents, focused sections master plan/current status/roadmap/workflow/release/task standard, все семь документов R2, отчёт066 и его acceptance/handoff. В родительских D:/, D:/Codex/, D:/Codex/repos/ AGENTS не обнаружен; дополнительных AGENTS в целевых java/test/tools/config не обнаружено. Отдельный code-map/pattern в root inventory не обнаружен.

Аналоги: PhantomProfileRepository.createWithComponent (atomic shell), PhantomCareerChoice (versioned component), L2jCommerceBackend.flushAndVerify + PhantomNativeWorkScope (native writer/durable evidence), PhantomNativeShotControl (stock handlers), Runtime066/Build-Task066/Assemble066 (read-only packaging templates). Прочитаны PopulationStore/State seams, materialization lifecycle, supply policy/journey, BackgroundModel, близкие focused/native tests, build.xml и реальные item XML.

## Review Focus

1. Legacy/newborn boundary и crash между durable debit и inventory store: отсутствие ретрокредита/повторного кредита.
2. Конкурентные claims, restart, смена класса/оружия: lifetime <=5000, compatible stock only, native weight/slots.
3. Бесплатный фонд не превращается в Phantom SELL/private sell; человек сохраняет штатные права.
4. Бедный actor: доступная частичная закупка соразмерна дороге, отсутствие денег не зацикливает SEEK_SHOTS.
5. Optional shots во всех Background modes, mandatory MP/ammo/reagent/upkeep и World ownership fences сохраняются.

### Task 1: Starter + supply priority

Files: config/custom/PhantomPlayersConfig.java, PhantomPlayers.ini; profile/PhantomProfileRepository.java; population/PhantomPopulationStore.java; новый commerce/PhantomStarterShot*.java; materialization/supply lifecycle seam; commerce supply policy/journey, disposition/private-sale guards; существующие focused/native suites.

- [ ] Focused RED: реалистичная частичная закупка; lifetime ledger/concurrency/ambiguous retry и legacy birth absence. Ожидание: точное нарушение текущего контракта.
- [ ] Минимальная реализация атомарного нового entitlement; batch<=1000, stock<100, limit0..5000/default0, candidate5000; native compatibility/weight/slots. Не менять population initialization plan/version.
- [ ] GREEN focused, source self-review ownership/перепродажа. Commit точных исходников перед clean JAR.

Interfaces: существующие profile components/native checkpoint; отдельный version1 starter component, exact limit/issued/pending/type. Потребитель supply использует текущий материализованный Player и profileId.

### Task 2: Native magic + Background parity

Files: PhantomBackgroundModel.java, PhantomBackgroundSuite.java; native combat owners и PhantomCombatServerIntegrationSuite.java только по подтверждённому trace.

- [ ] RED acquisition death/spoil zero-stock при сохранённых mandatory costs; затем общий optional-stock fix и GREEN.
- [ ] Прочитать исходный G1 trace и реальный direct cast lifecycle; одна диагностическая native сцена, исправление общей доказанной причины. Не повторять fixture без нового диагноза.

Interfaces: неизменный Loadout/BatchRequest и World/Background ownership; фактические HP/MP/item counters.

### Task 3: Ranged + dwarf

Files: существующие native suites/Task066 setup адаптировать в R2; product owners только при общем доказанном дефекте.

- [ ] До сцены зафиксировать проверенный Geo primary+fallback; одна ranged kill>200/pickup сцена и сохранённые негативы.
- [ ] Проверить class skill tree/spoil facts/topology. Spoiler Spoil→Sweep→loot, отдельный законный warehouse/merchant→return; Crystallize отдельно у native crafter. TEST_PREIMAGE до измерений.
- [ ] Проверить exact receipts/deltas; результат PASS либо точный BLOCKED, без spot hacks.

### Task 4: Delivery + ordinary Main + restart + publication

Files: новые Build/Assemble/Runtime066R2 и ownership helpers по read-only066 аналогам; отчёт и handoff/acceptance/changed list; current status/roadmap только current R2 факт.

- [ ] Создать private task066r2 и два exact-owned schemas; stopped066 consistent read-only clone в новую release schema; source preimage/hash preservation.
- [ ] Clean committed source archive, последовательные jar compile-tests, focused/native проверки на actual JAR. Ожидание: exit0 или точные отдельные FAIL/BLOCKED.
- [ ] Самостоятельный candidate START/STATUS/STOP, ordinary Main syntheticOFF/passive observer, два actor, один sameDB sameJAR штатный restart, до45мин наблюдения (max75).
- [ ] STOP собственных JVM, PID/startTicks/6ports/writers и preservation; матрицаR0–R7, self-review точного diff, две отдельные encoding checks, diff --check.
- [ ] Exact commits, push origin HEAD:refs/heads/feature/phantom-world, проверка live remote; report-only HEAD разрешён. CORE066R2=PASS только при всехR0–R7 PASS.

## Progress

- Read-only preflight: local/live HEAD совпадают; исходный dirt только R2 package. JVM/предлагаемые R2 ports в elevated read-only snapshot отсутствуют/свободны.
- Commit467a959: birth-only durable subsidy и optional-stock parity; commitfcf3e738: выдача после native admission, skill rank и Geo inputs.
- На JAR14247C71: новые native-born Mage10/Fighter31 получили1000/5000, handler потратил1, dematerialize/rematerialize сохранил issued/stock. Native magic с реальным rank1177:5 дал HP/MP delta; первоначальный rank1 FAIL сохранён.
- Ranged trace обнаружил пробел проверки: consumed herb не доказывает ordinary inventory pickup. Критерий усилен exact ordinary receipt/count; новый итог ещё не измерен. Старые protection/instance/Z/weight/capacity negatives прошли.
- Следующий bounded slice: guards ordinary/advanced/concurrent/pending, poor paid supply, три новые R2 Main identities (fighter/mage level1 без fixture gear и явно помеченный Scavenger TEST input), затем ordinary Main/restart. Legacy48 и старые releases не изменяются подготовкой.
- Self-review выявил связь lifecycle guard с ранним afterPlayerLoad: hydration целиком переносится в materializeSucceeded, когда native owner уже установлен. До native GREEN это не закрытый gate.
- Уточнение владельца учтено: direct READY preparation49–51 обошла Manager.planNew/attachNew; первый Main не доказывает продуктовый дефект Ecology. Ни cursor, ни goal revision, ни guard не исправлялись под эти профили. Созданные ими строки сохранены; helper прямой подготовки Main удалён после verified byte backup вне checkout. Следующие новые identities создаёт обычный PopulationManager через увеличение target собственного R2 candidate.
- Два новых causal RED на a4f1cde3/JARFD77E3F0: pending historical baseline получил1000 вместо0; entitlement нового No-grade novice после level5 получил0 вместо1000. Исправление читает существующий versioned background.catchup и запрещает физическую выдачу до COMPLETE; первый грант остаётся только root novice<20 с незаточенным No-grade оружием. Catchup ownership/cursor/revision не меняются.
- Бедный native actor с215 Adena выполнил17 kills и заработалEXP/SP/loot108, но даже верхняя граница денег323 ниже резерва500: платная цепочка FAIL, fallback подтверждён. Следующий native TEST-вход — stable Fighter19/NG gear/16 finite charges/20 healing pots и НОЛЬ Adena, stock reward band14–19 с проверенным отдельным Merchant trip. Любая оплата в нём должна происходить только из реального заработка; уровни/gear/pots явно TEST, не earned1->19. Промежуточные варианты800/1200 кошелька не запускались.
- Ranged ordinary-only trace: два native consumed herb receipts, обычного drop нет. Native drop cap может вытеснять ordinary group; новая ограниченная сцена заранее создаёт три stock NPC на проверенном350/700/1050 коридоре, без изменения шансa/HP/MP/rates/позиции actor. Herb никогда не закрываетR5.
- Scavenger: отдельная native-only composed сцена с теми же Farm/Navigation/Supply/Commerce owners; stock Spoil/Sweep, earned material, законный отдельный Warehouse и return/new kill. Ручной current-history cursor для Main не создаётся.
- Первый native dwarf preflight остановился до birth/сцены: для фиксированного profileId career choice зависит от profileId, поэтому seed selection не гарантирует ветку117. Убран лишний population/birth selector дляR6; существующий own native fixture получает до измерения законный Scavenger54/20 и stock254:2/42:1/239:1, без population.state, fake managed identity или Crystallize.

## Финальные решения22.10.2026

- Owner clarification: канонический Manager→Ecology→bootstrap→initial catchup обязателен для Main. Ручные49/50/51 оставлены fail-closed, helpers удалены после внешнего byte backup. Следующие52..60 созданы штатно; нет cursor/goal revision reset, identity clone, Ecology disable или «clock fix».
- Historical callback мог выдать subsidy до capture baseline: native RED→GREEN с issued0/stock0 при blocked catchup. Novice L5 сохраняет entitlement; поздний first claim L20 не разрешается.
- Independent read-only review нашёл admitted equipment race: NG→D во время drain на прежнем JAR выдавал1000. Final sealed selection/revalidation и instance snapshot исправлены; native clean rejection issued0/stock0/READY, exceptions0. Cheap fast stock prefilter сохранён.
- Владелец разрешил ускорение проверки refill: отдельные finite TEST-фазы физического остатка102, native charges до99, ledger неизменяемый вход. Final native classes124/10 получили ровно5×1000; sixth denied. Не считать это естественным расходом5000.
- R5: три full-stock NPC заранее на Geo corridor; ordinary Adena143 с exact source/receipt/inventory, не herb. Magic/charge proofs и неизменённые relevant production sources переиспользованы без нового fixture loop.
- Native earned-only actual L19/NG/Adena0 дал combat progress, но смерть до покупки; native Scavenger с legitimate skills также не завершил material/bank/return. Полный путь не объявлен PASS; новых систем или подбора спотов ради зелёного результата нет. Final Artisan56/Crystallize1 отдельно3/3 PASS.
- Ordinary EFE: Mage57 и Orc53 расходуют starter; final F0 после sameDB/sameJAR restart Elf59 дал20kills/rewards, EXP+580/SP+40,SS939. Coarse ecology_fenced label не означает отсутствия actual live native owner; actual physical1/preparation0/waiting0. Guards не менялись.
- Итог **CORE066R2=PARTIAL**, exact matrix/report/handoff и standalone source/JAR/evidence; normal STOP, hash conservation и non-force publication в единственную разрешённую ветку. Остальное не переводится в фиктивный PASS.
