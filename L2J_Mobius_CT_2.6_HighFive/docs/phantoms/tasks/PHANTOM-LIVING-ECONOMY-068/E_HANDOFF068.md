# E/D — shared pricing и Alt+B CLI

Агент champions_f. E production, material quote fix и live NPC Supplier вошли в последующие родительские integrated Ant builds. D-native сцена подтвердила material fix. Actual own068 CLI Apply и native Alt+B purchase выполнены этим агентом в выделенном последовательном слоте; подробные результаты ниже. Собственный Ant или полный GameServer launch не выполнялся.

## Read-first и scope

Прочитаны root AGENTS.md, module Agents.md, module readme.txt, PHANTOM_CURRENT_STATUS.md, CODEX_WORKFLOW_CONTRACT.md, текущие TASK/AUTHORITY/READ_SET/TEST_CASES/ACCEPTANCE.md в TASK068, relevant E/D clauses. Переиспользованы pricing authority, private quote, rate-aware loot value, recursive recipe expected cost, NPC acquisition, canonical enchant routes/expected enchant cost, существующий test launcher. Reachability подтверждена фактическим HomeBoard.java и Custom HTML. Отдельного pricing CLI в репозитории не найдено.

Bounded exception: 15 файлов одной цепочки valuation → Java XML exporter → защищённый exact-price CLI → focused tests/native helper/docs; независимые Loot, merchant lifecycle, PhantomSystem, launcher и build.xml принадлежат родителю. Нового runtime scheduler/DB/provider нет. JDK25 / Ant / PowerShell 5.1 сохранены. AUTHORITY068 разрешает автономную реализацию и own068 Apply; старые release/MAIN/PLAY не затронуты.

## Exact agent files

Все пути ниже относительно L2J_Mobius_CT_2.6_HighFive:

1. java/org/l2jmobius/gameserver/phantoms/economy/PhantomPrivateMarketPricingAuthority.java
2. java/org/l2jmobius/gameserver/config/custom/PhantomMarketConfig.java
3. dist/game/config/Custom/PhantomMarket.ini
4. java/org/l2jmobius/gameserver/phantoms/economy/PhantomEconomyValuation.java — новый общий policy/helper
5. java/org/l2jmobius/gameserver/phantoms/economy/PhantomEpicItemPolicy.java — новый explicit classifier
6. java/org/l2jmobius/gameserver/phantoms/economy/PhantomEconomyXml.java — новый безопасный XML helper
7. java/org/l2jmobius/gameserver/phantoms/economy/PhantomAltBPriceCatalog.java — новый bounded reachability/catalog
8. java/org/l2jmobius/gameserver/phantoms/economy/PhantomEconomyPricingExporter.java — новый DB-free exporter
9. test/java/org/l2jmobius/tests/phantoms/PhantomEconomy068PricingSuite.java
10. tools/phantom-economy/Price-PhantomEconomy.ps1
11. tools/phantom-economy/Test-PricePhantomEconomy.ps1
12. tools/phantom-economy/README_RU.md
13. docs/phantoms/tasks/PHANTOM-LIVING-ECONOMY-068/E_HANDOFF068.md
14. test/java/org/l2jmobius/tests/phantoms/PhantomEconomy068LiveNpcSuite.java
15. test/java/org/l2jmobius/gameserver/qol/PhantomAltB068Native.java

PhantomPrivateMarketQuote, RateAwareLootFairValue, RecipeExpectedCost и NpcAcquisitionPrice не изменялись. Общие файлы/launcher не изменялись этим агентом. F имеет отдельный F_HANDOFF068.md и собственные 18 путей.

## Public API и runtime

```java
public record ItemQuote(int itemId, boolean priced, boolean bidAllowed,
    long bid, long ask, long fair, String sourceReason) {}
public ItemQuote quoteItem(int itemId)
public long manufactureFee(int recipeListId, int productId)
PhantomEpicItemPolicy.isEpic(ItemTemplate item)
PhantomEpicItemPolicy.isEpic(int itemId)
```

UNPRICED возвращает false/false/0/0/0 и явную причину. Opportunity-only liquidation fallback не считается достоверным market source. Native BUY для nonstackable gear выключен; SELL/fair допустимы при наличии источника. Parent C использует эти exact accessors. Existing recipeRoles в knowledge остаётся единственным demand/recipe classifier.

Shared policy отделяет stock rate-aware Adena/item opportunity от авторских target-time/scarcity. Ordinary source + liquidation/crystal floor + recipe expected floor переиспользуются runtime и CLI. Эпики требуют paired boss source, имеют отдельный premium относительно обычных S/S80/S84 jewel anchors; B-grade Queen Ant не получает обычный NPC fallback. Неизвестное остаётся UNPRICED. Fee по умолчанию минимум 100 Adena, 2% source BOM per attempt; native ingredients/MP/chance не заменяются.

Кэши source/reason — ConcurrentHashMap, recursive computeIfAbsent отсутствует. NPC acquisition намеренно не кэшируется: видимость Merchant, налог и конечный stock проверяются заново, чтобы падение налога не оставляло старый высокий BID. Authority можно безопасно публиковать для конкурентного чтения после конструктора. Новый knowledge/rates/config snapshot требует нового authority; lifetime видимых Merchant также влияет на cached source fairness, хотя свежий acquisition ceiling защищает прямой обратный loop. Parent owns generation publication/cache.

## Реальный Alt+B scope

HomeBoard `_bbssell` использует buylist 423. BFS Custom/home.html + navigation.html, `_bbstop` и response pages находит multisell custom 600010/600012/600014/600016/600024. Поддерживаются только обычные Adena offers с простыми products, без скрытых restricted атрибутов/условий. `_bbsqol` отмечен UNKNOWN. Неподдерживаемые строки сохраняются без изменения.

Plan содержит source/config/profile/formula SHA-256, exact row edits и pre/postimage. Apply требует own binding, точный root/hash, актуальные source/preimage, byte backup, атомарную замену каждого файла. Rollback требует exact receipt/postimage и сохраняет поздние изменения. Scales кроме 1/1 никогда не применяются. Подробные команды и ограничения находятся в tools/phantom-economy/README_RU.md.

## Фактические проверки

Изолированный javac production E + suite/launcher: exit 0. Далее существующий launcher, seed 20260725001, classpath с .phantom-local/task068/economy-bin перед build-dev и module dependencies:

```text
org.l2jmobius.tests.phantoms.PhantomTestLauncher economy068-pricing 20260725001
org.l2jmobius.tests.phantoms.PhantomTestLauncher post001-recipe-cost 20260725001
org.l2jmobius.tests.phantoms.PhantomTestLauncher post001-market-quote 20260725001
org.l2jmobius.tests.phantoms.PhantomTestLauncher post001-enchant-cost 20260725001
```

Результаты соответственно PASS 3/3, 4/4, 6/6, 4/4, exit 0. Проверены author x10/x20 budget, отсутствие blanket inflation материалов, NPC ceiling, эпики/no-source, fee rounding/overflow, crystal liquidation; существующие BOM/quote/enchant регрессии сохранены. Loader-backed item/enchant suites не подключались к DB. Working directory — own068/native-test/game. Логи находятся в .phantom-local/task068/reports/economy068-pricing и post001-recipe-cost/post001-market-quote/post001-enchant-cost.

CLI smoke выполнен командой Test-PricePhantomEconomy.ps1 с source module/dist/game, отдельным OutputDirectory .phantom-local/task068/price-cli-smoke-fixed и явным JavaClassPath. Первый smoke обнаружил отсутствующий runtime PhantomMarket.ini в копируемом manifest, второй — PowerShell 5.1 binding File.Replace с null backup. Исправлены manifest и отдельный owned prior path. После безопасного resume существующей fixture (-ResumePreparedFixture) PASS 3/3, exit 0: deterministic Plan, preserved preimage conflict, Apply пяти файлов, ALREADY_APPLIED, preserved later-change Rollback conflict, exact original-byte Rollback. Source game не менялся. Сохранены первоначальные failure artifacts; native transaction этим не доказана. После этого в exporter добавлены raid rates, полный control hash scope и crystal-floor assertion; mutation helper не менялся. Финальная собственная candidate CLI smoke остаётся отдельным шагом родителя.

## Готовые x10/x20 snapshots

Оба Plan выполнены финальным isolated exporter без server/DB, scales 10/10 и 20/20, source module/dist/game, exit 0. По 53 PRICED / 46 UNPRICED / 5 файлов изменений. Полные facts и планы находятся в task-private evidence:

- .phantom-local/task068/pricing-x10/plan.json — SHA-256 `912f2d499195ab24a5a256f5fbc2f50b522e1ffeb24b58809601496d1bcd6df7`
- .phantom-local/task068/pricing-x20/plan.json — SHA-256 `e07f4acc0111cd7d4110091e5fea39e08b8416a798cc143d37b0aeaf78cdf5db`

Точные команды имеют форму:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File <module>/tools/phantom-economy/Price-PhantomEconomy.ps1 -Mode Plan -GameRoot <module>/dist/game -OutputDirectory <repo>/.phantom-local/task068/pricing-x10 -JavaClassPath <explicit-final-E-classpath> -AdenaScale 10 -LootScale 10
powershell -NoProfile -ExecutionPolicy Bypass -File <module>/tools/phantom-economy/Price-PhantomEconomy.ps1 -Mode Plan -GameRoot <module>/dist/game -OutputDirectory <repo>/.phantom-local/task068/pricing-x20 -JavaClassPath <explicit-final-E-classpath> -AdenaScale 20 -LootScale 20
```

Selected fair/ask values приведены в README. Не переносить plan.json на другой GameRoot под видом готового Apply: root binding входит в hash. Для final own candidate родитель должен заново создать Plan scales1/1 из final JAR и фактических входов.

## Остаток и честные границы

Native Alt+B purchase на own068 TEST PASS; CLI exact-byte Rollback PASS, отдельная native rollback price inspection PASS после исправления подготовки HP сохранённой fixture, подробности ниже. Final committed release candidate Apply остаётся отдельным шагом родителя. Native paid manufacture выполнен в D R3: canonical recipe1, fee100 Adena, MP30, ингредиенты4 Stem +2 Iron Ore ровно один раз, продукт500 Wooden Arrow. См. D_HANDOFF068.md. Родитель владеет Ant/native слотом. Не использовать старые TEST/MAIN/PLAY или blanket cleanup.

Offline catalog NPC source — untaxed lower bound, не proof присутствия живого Merchant. Runtime использует реального видимого Merchant. Экспортёр — stock XML adapter, не второй full server knowledge loader. Arbitrage guard покрывает поддерживаемые прямые acquisition→NPC/crystal/private BID и expected craft→BID; произвольные квестовые/валютные/enchanted циклы не доказаны. Эти ограничения отражены в UNPRICED/unknown и README, широкого green не заявлено.

Git write/commit/push не выполнялись. Ранее в F применялся только разрешённый bounded `git -c core.whitespace=cr-at-eol diff --check -- <exact F tracked paths>`; E проверки кодировки/whitespace фиксируются в финальном сообщении после документации.

## Native material regression — подтверждение и исправление

D R1/R2: quoteItem1864/1865/1866/1458 давал `UNPRICED:npc.visible.merchant.unresolved`; 1785 сохранял точный native NPC source. Отсутствующий видимый Merchant делал неизвестным acquisition ceiling и ошибочно обнулял также достоверный loot/BOM SELL/fair.

Согласованное с родителем исправление: unknown NPC запрещает BUY (`bidAllowed=false`, bid0), но source-backed SELL/fair сохраняются при выполнении прежнего legitimate-anchor guard. Source reason получает `+BUY_BLOCKED:npc.visible.merchant.unresolved`. Opportunity-only fallback по-прежнему UNPRICED; неизвестная NPC-цена не заменена вымышленным минимумом.

D R3 actual native PASS exit0, .phantom-local/task068/reports/equipment-craft068.tsv и equipment-craft068-native-r3.log:

| ID | Результат |
|---|---|
| 1864 | fair210, ask221, bid0/false; loot21613.SPOIL + BUY_BLOCKED |
| 1865 | fair411, ask432, bid0/false; loot22230.SPOIL + BUY_BLOCKED |
| 1866 | fair630, ask662, bid0/false; loot22090.SPOIL + BUY_BLOCKED |
| 1458 | UNPRICED: No legitimate market acquisition anchor |
| 1785 | fair275, ask289, bid261/true; npc.stock.merchant |

Offline exporter formulas и x10/x20 планы этим runtime-only split не менялись. Их untaxed catalog anchor остаётся отдельной указанной границей offline фактов.

## Live NPC membership

Сохранён прежний ctor(Collection<WorldObject>) с snapshot semantics для существующих клиентов и controlled tests. Добавлен runtime overload с `Supplier<? extends Collection<WorldObject>>`; parent PhantomSystem передаёт `() -> World.getInstance().getVisibleObjects()`. Каждый npc() снова получает коллекцию и принимает только Merchant.isSpawned() с точным `World.findObject(id) == merchant`. World API подтверждён исходником. NPC arrival/respawn больше не зависит от immutable knowledgeHash; source fair cache остаётся generation-owned, а acquisition ceiling пересчитывается с текущими NPC.

Изолированный javac Authority + PhantomEconomy068LiveNpcSuite PASS. Suite economy068-live-npc подготовлен для exact own068: одна и та же authority проходит absent/unspawned/current/wrong World identity/despawned/current/supplier removal. Inventory/funds не выдаются. Native запуск ожидает последовательного слота; PASS без запуска не заявлен.

## Actual Alt+B helper

PhantomAltB068Native расположен в existing qol test package и напрямую переиспользует package-private QoLTestClient из QoLEconomyProgressionClosureSuite. Это реальный localhost async socket/Connection/GameClient; Reflection, fake client, второй multisell engine и копия production pricing отсутствуют. Guard — public initializeNativeInfrastructure с exact own068 binding, без старого blanket initialize.

Helper сверяет exact approved plan SHA, owner/root/scales1/1, Apply receipt и postHash выбранного XML. Changed supported multisell row берётся из exporter facts, сверяется с flat row approved Plan; authoritative catalog подтверждает actual products/price. Bypass обнаруживается в уже ограниченном reachable catalog HTML; зарегистрированный MasterHandler/HomeBoard готовит native PreparedEntry. Настоящий MultiSellChoose read/run должен списать exact Adena и выдать exact products. Native packet layout взят из двух действующих QoL suites. Уровни/skills/PersonalQoL admission не подменяются.

CLI Apply выполняется до нового JVM. Actual own config должен иметь CustomCommunityBoard=True до Plan; helper не включает static flags обходом. Стабильный AltB068Buyer на disabled account task068altb получает только при создании конечный TEST фонд одной покупки. Повторный topup отсутствует. Отдельный rollback-check проверяет preHash и восстановленную native PreparedEntry цену без сделки/выдачи денег. XML/config helper не пишет.

```text
org.l2jmobius.gameserver.qol.PhantomAltB068Native purchase <plan.json> <apply-receipt.json> <planSHA256>
org.l2jmobius.gameserver.qol.PhantomAltB068Native rollback-check <plan.json> <apply-receipt.json> <planSHA256>
```

Helper-only javac PASS. Source XML не менялся. Final release candidate Apply на committed JAR остаётся отдельным шагом parent.

## Actual own068 TEST Plan / Apply / native purchase

Перед Plan только собственный native-test/game/config/Custom/CommunityBoard.ini переключён в CustomCommunityBoard=True; его preimage сохранён в reports/altb-community-preimage.ini. Exact OWNERSHIP068.json привязан к этому gameRoot. Два DB-free Plan1/1 из общего build-dev GameServer.jar совпали побайтово: SHA-256 `276d6381ed5583dd3462316dec5ca4b48b2ce5f949faf2273d88fe833b7122ea`, 53 PRICED / 46 UNPRICED / 5 XML. Артефакты: .phantom-local/task068/pricing-native/plan-first и plan-repeat.

CLI Apply exit0, 5 файлов, byte backups и receipt в pricing-native/apply. Native JVM с -Xmx4g и own068 guard завершился exit0: реальный зарегистрированный HomeBoard bypass `_bbsmultisell;600010,merchant/main` подготовил list600010/entry100000 с новой ценой 3428774 вместо 250000. Реальный socket-backed GameClient/MultiSellChoose списал Adena 3428774→0 и выдал product delta {119=1,854=2,886=2}. Конечный TEST фонд выдан однократно при создании AltB068Buyer; заработанным не называется. Доказательства: reports/altb068-purchase.tsv и altb068-purchase.log. ThreadPool/Hikari закрыты штатно.

CLI Rollback exit0 восстановил исходные байты всех 5 XML. Первый отдельный rollback-check JVM прошёл exact preHash, но exit1 на отсутствии подготовленного HomeBoard списка; reports/altb068-rollback-check-r1.tsv/log сохранены. Read-only причина подтверждена исходниками и последующим адресным повтором: исходный Player.create сохранял HP0; Player.load при HP<0.5 выставляет dead=true, а HomeBoard отклоняет isAlikeDead. Fixture исправлена по ближайшему native паттерну: doRevive при isDead и fullHP/MP перед измерением, без выдачи Adena/skills/levels; helper-only javac exit0.

После диагностики исходный Plan повторно применён через новый audit directory pricing-native/reapply: CLI Apply exit0, 5 XML. Затем в отдельном согласованном коротком слоте повторены Rollback (exit0, 5 exact original files) и исправленный rollback-check на том же AltB068Buyer: native JVM exit0, HomeBoard list600010/entry100000 подготовил исходную цену 250000 и те же products. Покупки/выдачи средств не было; reports/altb068-rollback-check.tsv/log PASS. ThreadPool/Hikari штатно закрыты.

Финальный собственный TEST ReApply exit0, 5 XML, audit receipt в pricing-native/reapply-final-test. TEST оставлен с actual1/1 Apply и CustomCommunityBoard=True для следующих согласованных сцен. Слот возвращён родителю; production алгоритмы ради fixture не менялись. Первая попытка rollback-check до JVM имела PowerShell quoting error для -D properties; native DB тогда не открывалась, затем -D аргументы взяты в кавычки. Это не native failure и не дополнительная покупка.

## Повторный rebalance: actual rates x10 → x20 → x10

Последний bounded пакет меняет ровно4 пути: PhantomEconomyPricingExporter.java и tools/phantom-economy/Price-PhantomEconomy.ps1, Test-PricePhantomEconomy.ps1, README_RU.md. Canonical baseline впервые сохраняется явным Mode Baseline во внешнюю папку: только reachable Alt+B XML/HTML, root binding и hashes. Rates.ini остаётся динамическим. Plan с BaselineDirectory использует canonical acquisition prices, а old_price/preHash берёт из текущего game. Есть diff.tsv, target-prices.tsv и facts/rates.tsv; Apply проверяет неизменность baseline. Missing57 override теперь наследует native death/raid amount своего источника, а не1.

RED reports/rebalance-baseline-RED.log, exit1: прежний CLI не принимал Mode Baseline. Это **missing capability RED**, не воспроизведённое доказательство накопленного compounded price. После правки isolated javac Exporter против committed build-final-core-v2 JAR exit0; DB-free CLI smoke r2 exit0,4/4. Classpath начинался с own ignored rebalance-bin только для exporter; matching final committed verification нового пакета этим агентом ещё НЕ выполнен.

Evidence: .phantom-local/task068/rebalance-smoke-r2/ и reports/rebalance-smoke-r2.log. Actual Rates.ini profiles: death/spoil/raid amount10/20, chance2/3, per-item57 amount10/20 с сохранением остальных overrides. Выполнены x10 Plan/Apply → x20 Plan/Apply → x10 Plan/Apply; затем3 обратных Rollback и возврат fixture Rates.ini.

| Артефакт | SHA-256 |
|---|---|
| x10-first и x10-repeat target-prices.tsv | `5aba6c068e5390042fbf9db2bd1c8dd614469a18f71a171bd2e40268887352f9` |
| x20 target-prices.tsv | `0c8fd22522ee850b93d33920eafed4c3f052aef52df4f08c02a315d289fcef75` |
| оба x10 facts/quotes.tsv | `267945458038232a07d74dd57eb6ee690b983b2bddd38b93a309bb18daaed07d` |
| canonical baseline.properties | `1e2df15f96e3cee646ede548c14beb37afc4b9f8cc6f6a92fdaf1d4e7e4c96f5` |

Отдельные assertions подтвердили inheritance57 при отсутствии override, разные death10/raid20, отсутствие price эффекта XP999/SP777 и отказ при повреждённом baseline. Full Plan SHA после промежуточного Apply закономерно иной из-за текущих preimages; возврат цен проверялся по target/quote bytes. Source game, native TEST, candidate и runtime configs этим smoke не менялись. Native/БД/Ant не запускались. Исходные stock цены и CustomCommunityBoard OFF остаются defaults. Полные mojibake и6 escaped Cyrillic regex проверки4 файлов:0 совпадений; bounded git diff --check exact4paths exit0, только LF→CRLF уведомления; git mutations отсутствуют.

### Повторный Plan в собственной candidate

Ниже фактический путь текущей среды; на другом диске используется её own candidate. В candidate уже сохранён `pricing/canonical` и выполнен read-only `pricing/stock-plan`; Apply не выполнялся. Повторный пересчёт использует **существующий** baseline. Для каждого нового Plan/Apply выбрать новый output, старые receipts сохранять. Только для другой новой поставки сначала однократно выполняют `-Mode Baseline` до её первого Apply по CLI README.

```powershell
$candidate068 = 'D:\PhantomWorld\Living-Economy-068-candidate'
$game068 = Join-Path $candidate068 'game'
$tool068 = Join-Path $candidate068 'tools/phantom-economy/Price-PhantomEconomy.ps1'
$prices068 = Join-Path $candidate068 'pricing'
$baseline068 = Join-Path $prices068 'canonical'
$plan068 = Join-Path $prices068 'next-rates-plan'
$apply068 = Join-Path $prices068 'next-rates-apply'
$owner068 = Join-Path $candidate068 'OWNERSHIP068.json'
$rates068 = Join-Path $game068 'config/Rates.ini'
$ratesBackup068 = Join-Path $prices068 'Rates.before-next.ini'
& (Join-Path $candidate068 'STOP.cmd')
if ($LASTEXITCODE -ne 0) { throw 'STOP failed; preserve the running runtime.' }
if (!(Test-Path -LiteralPath (Join-Path $baseline068 'baseline.properties') -PathType Leaf)) { throw 'Original canonical baseline is missing; do not recreate it from Applied prices.' }
if (Test-Path -LiteralPath $ratesBackup068) { throw 'Existing rate backup must be preserved; choose a fresh path.' }
Copy-Item -LiteralPath $rates068 -Destination $ratesBackup068
notepad.exe $rates068
# Сохранить реальные rates; затем проверить Plan и warnings перед явным Apply.
powershell -NoProfile -ExecutionPolicy Bypass -File $tool068 -Mode Plan -GameRoot $game068 -OutputDirectory $plan068 -BaselineDirectory $baseline068
if ($LASTEXITCODE -ne 0) { throw 'Plan failed; preserve evidence and do not Apply.' }
Import-Csv -LiteralPath (Join-Path $plan068 'diff.tsv') -Delimiter "`t" | Format-Table path,ordinal,old_price,new_price -AutoSize
Get-Content -LiteralPath (Join-Path $plan068 'UNPRICED.tsv')
Get-Content -LiteralPath (Join-Path $plan068 'facts/unknown.txt')
$hash068 = (Get-FileHash -LiteralPath (Join-Path $plan068 'plan.json') -Algorithm SHA256).Hash.ToLowerInvariant()
powershell -NoProfile -ExecutionPolicy Bypass -File $tool068 -Mode Apply -GameRoot $game068 -OutputDirectory $apply068 -PlanPath (Join-Path $plan068 'plan.json') -ExpectedPlanHash $hash068 -OwnershipManifest $owner068
if ($LASTEXITCODE -ne 0) { throw 'Apply failed; do not START.' }
& (Join-Path $candidate068 'START.cmd')
```

При следующем x20→x10 использовать **тот же** baseline и новые Plan/Apply outputs. Ни XP/SP, ни уже Applied price не становятся множителем новой target price. Любой неуспешный STOP/Plan/Apply требует остановиться и разобрать error; последовательность выше не является unattended batch. Никакой automatic startup Apply не добавлен. Обычный ALT+B admission/CB switch отдельно решает оператор: CLI цены не включает CB и не даёт фантомам новый обход native снабжения.

```powershell
& (Join-Path $candidate068 'STOP.cmd')
if ($LASTEXITCODE -ne 0) { throw 'STOP failed; do not rollback a running runtime.' }
powershell -NoProfile -ExecutionPolicy Bypass -File $tool068 -Mode Rollback -GameRoot $game068 -OutputDirectory $apply068 -PlanPath (Join-Path $plan068 'plan.json') -ExpectedPlanHash $hash068 -OwnershipManifest $owner068
if ($LASTEXITCODE -ne 0) { throw 'Rollback conflict; preserve current prices and Rates.ini.' }
# Только после успешного Rollback: вернуть сохранённый rate snapshot отдельно.
Copy-Item -LiteralPath $ratesBackup068 -Destination $rates068
& (Join-Path $candidate068 'START.cmd')
```

Rollback возвращает цены только при exact postimages и не затирает поздние правки; Rates.ini автоматически не откатывает. Старый own native TEST Alt+B purchase/rollback-check выше доказывает реальный HomeBoard/MultiSellChoose путь на прежнем build-dev и его1/1 Plan, но не новый canonical rebalance пакет и не committed release Apply. До root committed verify новая rates matrix имеет только isolated-exporter DB-free evidence.

### Root: committed verification завершена

Source `b2803e3093e94f2e4b1f32819d7cddb57a044242`, GameServer SHA256 `8FE9B3046BBB677390571F1A1D2E3D54ABF05DB2C5389DFE108274FFE1EC9EE2`. Exact committed testbin/JAR, без isolated override: `economy068-pricing`3/3 PASS. Source CLI `Test-PricePhantomEconomy.ps1` против этого JAR и immutable archived game выполнил4/4 PASS; output `.phantom-local/task068/rebalance-committed-v3`, log `reports/rebalance-committed-v3.log`. Source game и настоящие TEST/candidate rates/цены этим DB-free smoke не менялись.

| Actual Rates профиль | Stem1864 fair/ask | Queen Ant6660 fair/ask | target-prices SHA256 |
|---|---:|---:|---|
| amount10/chance2, per-item57 amount10/chance1 |107/113|1278000000/1341900000|5ABA6C068E5390042FBF9DB2BD1C8DD614469A18F71A171BD2E40268887352F9|
| amount20/chance3, per-item57 amount20/chance1 |72/76|2556000000/2683800000|0C8FD22522EE850B93D33920EAFED4C3F052AEF52DF4F08C02A315D289FCEF75|
| повтор первого после обоих Apply |107/113|1278000000/1341900000|5ABA6C068E5390042FBF9DB2BD1C8DD614469A18F71A171BD2E40268887352F9|

Остальные per-item overrides сохранялись, включая epic amount1. Поэтому таблица показывает два полных профиля Rates.ini, а не blanket price multiplier; более частый обычный ресурс может дешеветь, редкий epic с отдельным amount — дорожать. Quotes SHA обоих x10 `267945458038232A07D74DD57EB6EE690B983B2BDDD38B93A309BB18DAAED07D`. Missing57→death10/raid20, XP999/SP777, baseline conflict, три обратных Rollback и восстановление fixture Rates также PASS. Baseline metadata SHA root-bound к fixture, поэтому закономерно отличается от isolated r2, при совпадающих targets/quotes.

Это закрывает новый повторный расчёт на final committed exporter. Старый native purchase остаётся отдельным bounded evidence реального HomeBoard transaction; candidate Apply не выполняется и исходные цены не заменяются по умолчанию.
