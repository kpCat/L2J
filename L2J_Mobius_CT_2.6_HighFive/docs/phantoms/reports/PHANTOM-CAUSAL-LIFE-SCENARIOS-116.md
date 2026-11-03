# PHANTOM-CAUSAL-LIFE-SCENARIOS-116

Результат реализации и DB-free gates: **SUCCESS**, 32/32 сценария; focused regressions 112/112.
Дата: 03.11.2026. Seed: `11611601`. Отдельная JVM suite заняла 264 ms включая запуск.
Baseline live GitHub: `2b729ebed628d538fef8b8dae124d511658965c4`, ветка `feature/phantom-world`.
SHA enclosing commit и подтверждённый live HEAD после ordinary non-force push — в финальном ответе Codex.

## Read-first, изоляция и план

Прочитаны все 6 файлов пакета TASK116, root AGENTS.md, module Agents.md, workflow/efficiency contracts,
root README.md, PHANTOM_CURRENT_STATUS.md и отчёт TASK115: 13 обязательных/контекстных файлов.
В исходном checkout отчёт115 отсутствовал; после получения exact baseline прочитан из stage.
Дополнительно: build.xml; module readme.txt; bounded начало master plan и TASK_PACKAGE_STANDARD;
12 source-map Java файлов, PhantomAssertions, PhantomCommerceReceipt, L2jPhantomSupplyJourney,
PhantomJourneyLifetime, PhantomCombatCoreSuite и supply policy XML — расширения для API/receipt/lifecycle/build проверки.
Модульный README.md не найден, есть readme.txt; обязательные AGENTS/docs/source-map найдены.
Локальные аналоги: readiness093 direct preparation, supply071 receipt/codec fixtures, scenario-smoke/decision-core
и существующие registry/context/assertions/report writer. Новых зависимостей и альтернативного AI нет.
Стек: Temurin JDK25.0.4.1, Ant1.10.17; только High Five. Исторические paths/credentials не используются.

План выполнен: один новый suite + mode → конкретный RED → owner fix → GREEN → независимый proof-of-test
→ focused regressions/replay → exact scope и normal push. Новые design/plan files вне разрешённого scope не создавались.
Основной checkout был на `236b965a116bbc79cccbef78b04c628225a629cb`, upstream tracking был устаревшим;
live remote соответствовал заданию. Fetch получил baseline, локальная branch/HEAD и WIP109 не обновлялись.
Существующие worktrees TASK109/114 не подходят и не изменялись. Создан managed isolated TASK116 worktree.
48 protected source/test/report/task/tool SHA-256 preimages исходного checkout сверены: 48/48, changed=0.
Ни один уникальный WIP-файл не заменялся/не переносился. MAIN/PLAY, TEST109–115, release/Geo не затронуты.

## Production owner и RED → GREEN

Гипотеза: continuation принимает слишком старый receipt с теми же actor/root/request.
Первый suite: 30/31, exit1; только SELL-STALE-REVISION: receipt3 при completed child5 разрешён.
Причина: `continueSales` проверял только `receipt.goalRevision() < goal.revision()`.
Исправление одной строки: требуется `receipt.goalRevision() == goal.revision() - 1`; затем 31/31, exit0.
Source binding: `L2jPhantomSupplyJourney.advanceMaintenance` принимает COMMITTED только для точной текущей revision;
`PhantomInventoryMaintenanceIntent.advance` повышает её на1. `PhantomJourneyLifetime.pause/resume` revision сохраняют.
Новый stale guard не создаёт receipt, платежа, durable writer или новую механику; schema/API не меняются.
Writer audit: immutable child/receipt transformations; runtime save остаётся в прежнем root/child CAS,
ACTION lease и quiescent commerce owner. DB methods SupplyIntentStore не вызываются тестом.

Независимый proof-of-test: только expected HP-READY временно RECOVER вместо ALLOW → 30/31, exit1,
причина ровно HP-READY actual ALLOW; production не менялся. Expected восстановлен; suite GREEN.
Read-only review нашёл два P2 в тестах: отсутствовал stock-контраст и RETURN-BUY миновал завершение SELL batch.
Добавлена stock0/9 пара; RETURN-BUY guard дал RED31/32, exit1, затем fixture проходит production advance
для synthetic confirmed facts каждой операции, проверяет returning и использует фактический index при append BUY.
Оба замечания исправлены; финал32/32 и replay32/32. P1 не найден; иных известных замечаний не осталось.
Это переходы синтетических facts, не фактические продажи или полученные деньги.

## Source map и матрица

Пути ниже относительно модуля; actual совпал с expected во всех строках.
P = `java/org/l2jmobius/gameserver/phantoms/combat/PhantomCombatPolicy.pvePreparation`, action + exact reason.
O/U/B = `java/org/l2jmobius/gameserver/phantoms/commerce/PhantomAutonomousSupplyPolicy`:
`optionalPotionsAllowed/usefulPurchase/availableBudget`.
G = `java/org/l2jmobius/gameserver/phantoms/progression/PhantomNativeEquipmentPolicy.targetGrade`.
S/A = `java/org/l2jmobius/gameserver/phantoms/commerce/PhantomInventoryMaintenanceIntent.continueSales/advance` + parse.
PV = POLICY_VERIFIED; CV = CODEC_VERIFIED. Полные input/expected/actual/reason/tier — штатный TXT/XML runner.

| ID / ситуация | Синтетические факты → production method | Actual = expected | Tier |
|---|---|---|---|
| HP-LOW / отдых | Fighter5 HP45%, viable, free → P | RECOVER / readiness.native_hp_mp | PV |
| HP-READY / готовность | Те же facts, только HP80% → P | ALLOW / readiness.native_lower_risk | PV |
| MP-LOW / мана | Caster5 HP100%, MP5%, выбранный primary MP не готов → P | RECOVER / readiness.native_hp_mp | PV |
| BUSY / занятость | Как HP-READY, только native busy → P | WAIT_NATIVE / readiness.native_inflight | PV |
| HIGH-GEAR / снабжение | Solo76 high-risk, shots0, funds=true → P | MAINTAIN_SUPPLIES / readiness.gear_or_shots.existing_supply_required | PV |
| HIGH-POOR / нет денег | Те же facts, только funds=false → P | SAFER_FARM_OR_OTHER_ACTIVITY / тот же gear reason | PV |
| UNREACHABLE / отказ | Как HP-READY, только viable=false → P | SAFER_FARM_OR_OTHER_ACTIVITY / readiness.native_target_or_facts_unavailable | PV |
| POTIONS-17 / уровень17 | CP0, level17 → O | false; только threshold, не supply intent | PV |
| POTIONS-18 / уровень18 | Те же facts, только level18 → O | true; только threshold | PV |
| SHOTS-NEAR / рядом | Optional, stock0/count1/perHit1, distance150/speed100 → U | true | PV |
| SHOTS-FAR / далеко | Те же facts, только distance1000 → U | false | PV |
| SHOTS-FAR-STOCK / запас | Как SHOTS-FAR, только stock9 → U | true; не full supply selection | PV |
| AMMO-MANDATORY / боеприпас | Как SHOTS-FAR, только optional=false → U | true | PV |
| SHOTS-IMPOSSIBLE / неполный удар | Mandatory, stock0/count1/perHit2, distance150/speed100 → U | false | PV |
| BUDGET-PRODUCTIVE / резерв | balance100/fees20/earmark0/productive=true/reserve500 → B | 60 | PV |
| BUDGET-OVERDRAWN / обязательства | balance100/fees20/earmark81/productive=true → B | 0 | PV |
| GRADE-19 / No-grade | level19/expertise7 → G | NONE | PV |
| GRADE-20 / D-grade | level20/expertise7 → G | D | PV |
| GRADE-40 / C-grade | level40/expertise7 → G | C | PV |
| GRADE-76 / S-grade | level76/expertise5 → G | S | PV |
| GRADE-76-NO-EXPERTISE / знание | Как GRADE-76, только expertise0 → G | NONE | PV |
| SELL-COMMITTED-1 / следующий предмет | Synthetic COMMITTED item426/object(seed), receipt4/child5, один новый object → S | index0, one SELL, returning=false | PV+CV |
| SELL-COMMITTED-3 / разные предметы | Те же facts, три новых object/item426/462/18 → S | index0, three SELL, target426 | PV+CV |
| SELL-PENDING / подготовлен | Тот же batch, PREPARED receipt → S | IllegalArgumentException | PV |
| SELL-FOREIGN-ACTOR / чужой | Receipt profile2 вместо1 → S | IllegalArgumentException | PV |
| SELL-FOREIGN-ROOT / другая цель | Receipt root117 вместо116 → S | IllegalArgumentException | PV |
| SELL-STALE-REVISION / старый | Receipt3 вместо4 при child5 → S | IllegalArgumentException | PV |
| SELL-REPLAY-OBJECT / повтор | Предлагается уже проданный exact object → S | IllegalArgumentException | PV |
| SELL-DUPLICATE / дубликат | Два одинаковых новых object → S | IllegalArgumentException | PV |
| SELL-OTHER-COUNTER / другой NPC | NPC object334 вместо333 → S | IllegalArgumentException | PV |
| SELL-FOUR-SLOTS / лимит | Четыре новых SELL вместо max3 → S | IllegalArgumentException; BUY slot сохранён | PV |
| RETURN-BUY / зарезервированная покупка | Synthetic completed three SELL, append BUY3947/list3000400 → A | current BUY, тот же actor/root/field/deadline, codec equal | PV+CV |

Контрастные пары меняют один факт: HP, funds, level17/18, distance, stock, optional flag, expertise.
Deadlines и objectIds определяются SplittableRandom(seed), без currentTimeMillis/sleep.
Повтор с тем же seed дал идентичные facts/outputs/reasons/tiers; elapsedNanos закономерно исключён из сравнения.
DECISION_VERIFIED для этих сцен **не заявляется**: реальный candidate/selector/handler здесь не исполнен.
Native SELL/BUY, supply composite, SP-paid skill/trainer, WALK/GK/Geo, rewarded kill/EXP/SP и STORE/restart —
**NATIVE_REQUIRED**. Fake Player/World/GameClient не создаются; synthetic COMMITTED не является SQL witness.

## Команды и проверки

Из корня isolated worktree, штатный classpath build.xml; один запуск новой библиотеки после компиляции:

```powershell
ant -f .\L2J_Mobius_CT_2.6_HighFive\build.xml compile-tests
$moduleRoot = (Resolve-Path .\L2J_Mobius_CT_2.6_HighFive).Path
$classpath = 'build\bin;build\phantom-test\bin;build\phantom-test\resources;L2J_Mobius_CT_2.6_HighFive\dist\libs\*'
java "-Dphantom.module.root=$moduleRoot" '-Dphantom.test.reports=.phantom-local/logs/task116/final' -cp $classpath org.l2jmobius.tests.phantoms.PhantomTestLauncher life-scenarios116 11611601
```

- Ant compile-tests: baseline26s/final49s, оба exit0; 5 существующих test warnings (internal API/finalization).
- Точечный javac --release25/UTF-8: suite+launcher1, production owner1, proof suite1, review suite2; все exit0.
- life-scenarios116 final32/32, exit0, 264ms; same-seed replay32/32, exit0, deterministic measurements equal.
- supply0717/7, combat-core69/69 (включает readiness093), decision-core36/36: все exit0, 112/112.
- Сохранены решения TASK114/115 в пределах этих focused contracts; старое native evidence не переоценивается.
- Целых Ant verify=0, standalone jar=0: DB/native не требуются; запуск DB-containing verify запрещён задачей.
- Launcher missing-mode baseline exit2 — setup RED регистрации, не production defect.
- Suite запусков8 (включая informative RED/proof/review/replay), regression запусков3, missing-mode1.
- Новых Java production files0; изменён production1; test files2 (один новый); scripts0/harness0.
- DB=0, migrations0, GameServer=0, native starts0; writes suite только штатные TXT/XML reports.
- Full output: stage `.phantom-local/logs/task116/`; baseline/final build logs, causal-red/green, proof-red,
  review-red, final и replay TXT/XML. Новые scripts, DSL, fixtures files и Ant targets не создавались.
- Mojibake-маркеры в изменённых файлах проверены отдельным полным guard.
- Escaped Cyrillic в изменённых файлах проверены отдельным regex guard.
- Первый inline PowerShell guard не разобрал кириллические marker literals из-за shell transport encoding;
  проверка не считалась выполненной. Полный mojibake guard повторён через codepoints/UTF-8 reader, exit0;
  отдельный escaped-Cyrillic guard exit0.
- Exact diff/scope guard и CRLF-aware diff --check выполнены перед публикацией; logs/build/binaries не stage.

## Scope, Git и telemetry

Exact allowlist публикации относительно модуля:
`test/java/org/l2jmobius/tests/phantoms/PhantomLifeScenarios116Suite.java`,
`test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java`,
`java/org/l2jmobius/gameserver/phantoms/commerce/PhantomInventoryMaintenanceIntent.java`,
`docs/phantoms/reports/PHANTOM-CAUSAL-LIFE-SCENARIOS-116.md`.
Git разрешён текущей TASK для baseline/scope и публикации. Точные команды (повторы опущены);
`$task116Paths` — четыре exact пути allowlist, `$task116JavaPaths` — только три Java-пути:

```powershell
git rev-parse HEAD
git symbolic-ref --short HEAD
git rev-parse --abbrev-ref '@{upstream}'
git rev-parse --verify '2b729ebed628d538fef8b8dae124d511658965c4^{commit}'
git status --short
git status --porcelain
git worktree list --porcelain
git for-each-ref --format='%(refname) %(objectname)' refs/heads refs/remotes/origin
git ls-remote origin refs/heads/feature/phantom-world
git fetch origin refs/heads/feature/phantom-world
git diff -- $task116JavaPaths
git diff --stat -- $task116JavaPaths
git -c core.whitespace=cr-at-eol diff --check -- $task116Paths
git add -- $task116Paths
git diff --cached -- $task116JavaPaths
git diff --cached --stat -- $task116Paths
git -c core.whitespace=cr-at-eol diff --cached --check -- $task116Paths
git commit -m "phantom(task-116): add causal life policy scenarios"
git push origin HEAD:refs/heads/feature/phantom-world
```

Stage только четыре пути; force/reset/clean/stash/rebase нет.
Рабочий stage detached; push не двигает локальную branch в protected checkout. Master/default branch не меняются.
Sandbox network/write отказы обработаны approved escalation; auto-review rejection отсутствует.
Initial files13; additional/source reads перечислены выше. Repository rg calls16:
две широкие path inventories оказались избыточными, затем запросы сужены до нужных symbols/ranges.
Static scope/encoding checks и все execution commands фиксируются по фактическим exit codes.
Goal snapshot при подготовке отчёта: 215575 tokens, 778s, unlimited; денежный cost недоступен.

В следующих city/Giran задачах эта библиотека быстро локализует readiness/supply/grade/receipt regressions
до native кампании. Она не доказывает полную автономную карьеру или живой город.
TASK116 завершается этой поставкой. TASK117 не начат.
