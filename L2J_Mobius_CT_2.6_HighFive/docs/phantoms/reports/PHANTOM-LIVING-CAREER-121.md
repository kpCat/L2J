# TASK121 — living career: PARTIAL_PRODUCT, native continuation BLOCKED

Дата: 3–4 ноября 2026. `FULL121` не достигнут. Доставлены узкие source-изменения выбора gear, допуска canonical idle recovery и диагностики travel; native вертикаль целиком не подтверждена. Закрытие TASK121 и следующий TASK122 не объявляются.

## Текущий safety blocker

В собственной TEST121 штатный STOP окна2 завершился FAILED до `DRAIN_PLAYERS` из-за sticky ошибки ordinary death recovery профиля26. Последующий отдельно проверенный ADMIN STOP-only запрос также вернул false. Stock Shutdown, hard kill, field patches, освобождение leases вручную и подмена receipts не выполнялись.

Read-only ADMIN snapshot установил оставшийся holder: **profile2 / object268484956 / epoch541153814445700**, ACTIVE, World present, identity lease retained, cleanupPhase NONE, action admission OPEN. `nativeOwnerOutstanding=0`, `nativeOwnerPendingTimers=1`, owner OPEN, permanentSeal=false; `failedCleanupReady=false`. Это другой actor, не погибший26. Тип единственного timer этим snapshot не установлен.

Существующая FAILED-ветвь `PhantomSystem.shutdownClaimed` допускает legacy cleanup лишь после `failedCleanupReady()`. Ordinary death failure возникает раньше нормального закрытия native roots остальных actors. Получен причинный stop witness, но безопасная перестановка lifecycle-фаз с focused regression в этот source-набор не включена. Зависимый native restart не запускался.

Пользователю отправлен safety choice: оставить JVM для ручного разбора либо разрешить штатный stock Shutdown только TEST121 с сохранённым FAIL и риском неполного STORE. Пока разрешения нет, второй вариант не выполняется. Это реальный safety conflict обязательных STOP/STORE/no-force-fallback контрактов, а не лимит времени или количества исправлений.

**Exit G0 НЕ PASS:** own JVM PID67120 остаётся; JVM0/listeners0/SQL0 и clean STOP/STORE окна2 не доказаны. Никакая строка ниже не заменяет этот gate.

## Baseline, isolation и read-first

- GitHub `kpCat/L2J`, ветка публикации `feature/phantom-world`, ожидаемый и проверенный baseline `3e340e45c4c2aed42b7eca5202440b0a8817901d`.
- Source commits: gear `9c9788a1f674c741c844616294265a26810b79c9`; recovery/diagnostics `af93083ea8519a3acef5d3b141aa724d27683d1d`. Source review P1/P2=0. Оба опубликованы ordinary non-force push, live remote HEADaf93083e подтверждён; отчёт публикуется отдельным scoped commit.
- Защищённый рабочий checkout: `D:\Codex\repos\L2J-phantom-world`, локальный HEAD `236b965a116bbc79cccbef78b04c628225a629cb` и существующий dirty WIP109. Не использован как baseline source и не изменялся.
- Собственная физическая source-копия: `D:\Codex\worktrees\phantom-living-career121-source\L2J-phantom-world`, detached descendant baseline3e. Native app worktree creation не смог найти этот ref в защищённом checkout; применён no-hardlinks clone от read-only source120 с exact checkout3e и sparse HighFive. Чужой source120 не менялся.
- Собственный stage: `%LOCALAPPDATA%\Temp\Phantom-Native-Life-121-a078ca51-5673-47c4-9ced-c69e534c87e9`; owner `TASK121_NATIVE_LIFE`, UUID `a078ca51-5673-47c4-9ced-c69e534c87e9`, owner+SYSTEM private ACL, physical/disjoint paths, без reparse/hardlink reuse Geo.
- Единственная новая схема: `l2jmobiush5_phantom_test_121`, существующий MariaDB endpoint3308; configured GameServer endpoint только127.0.0.1:17781. `new GameServer()` создаёт native World в headless helper, полный `GameServer.main`/LocalRuntimeShutdown transport не вызывался. LoginServer и клиент не запускались.
- OS attestation: MariaDB service, PID8692, `C:\Program Files\MariaDB 11.4\bin\mysqld.exe`, version11.4.13; runtime image через `PrivilegedReceipt/QueryFullProcessImageNameW`. Перед CREATE/GS проверены SQL identity/datadir/server id, metadata visibility/grants, global quiescence и отсутствие посторонних JVM/listeners/sessions. Сервис/конфигурация общей MariaDB не менялись.
- CREATE121 выполнен один раз: 121 stock SQL files, 203 statements, 18 DROP исключены, foreign writes0. Повторного bootstrap/reset/import существующей схемы не было.
- Защищённый manifest1235 неоднократно проверен: drift0. Это parity именно объявленного manifest, не утверждение о полном побайтовом сканировании всех чужих файлов.
- Original Geo203, 1,063,452,308 bytes: отдельная физическая копия и SHA parity; originals не менялись. Защищённые release057–061, TEST109–120, MAIN/PLAY и чужие worktrees не запускались и не менялись.

Прочитаны все8 файлов пакета121: TASK, GOAL, HANDOFF, SOURCE_MAP, PRODUCT_DECISIONS, SCENARIOS, ACCEPTANCE, EVIDENCE; root/module AGENTS, current status, workflow/efficiency и обязательные релевантные master-plan/package/release contracts. Initial READ_SET —13 документов по SOURCE_MAP; далее bounded symbol/range reads. Пакет121 читался из рабочего checkout; его отсутствие в sparse baseline-копии не обходилось добавлением/переписыванием task package.

Локальные аналоги: recovery admission в `PhantomBackgroundService.recoveryGoalCurrent` и `recoverOwned`; `PhantomEarnedMarket119Checks`/`PhantomOfflineSupply.AcquisitionQuote`; `PhantomRuntimeFlightRecorder`/его suite. Для TEST-only setup переиспользованы private119 helpers, stock headless infrastructure и canonical Player/skill/recipe APIs, с новыми121 namespace/UUID/guards. Старые raw119/120 были только указателями, не результатами121.

Построены три причинные карты: A — VisibleFarmTravel→historical goal/recovery→materialization/stop; B — LootDisposition→maintenance→Commerce/warehouse/offline BUY; C — need→NPC/offline SELL/MFG→quote/reserves→native receipt/equip. Существующие owners и sole paid writers сохранены; новый AI, scheduler, durable writer или слой providers не добавлены.

JDK25/Ant; public population5000/visible128 сохранены. Только private TEST overrides: сначала24, затем28 identities, active8/materialized8, diagnosticsTrue и headless GUIFalse. Public OwnerConfig, Rates, Merchant/Market/Geo/life data не менялись. Adena item57 amount5/chance1 сохранены; публичные DeathDrop/Spoil rates не менялись. Никакого forced Giran, увеличения deadline или лимита600 ради PASS.

## Изменённые public artifacts — exact allowlist7

Все пути ниже относительно `L2J_Mobius_CT_2.6_HighFive/`.

| Путь | Изменение и предел доказательства |
|---|---|
| `java/org/l2jmobius/gameserver/phantoms/commerce/PhantomOfflineSupply.java` | Package-private equipment comparator использует существующий delivered unit cost, recipe material/risk и distance tie. |
| `java/org/l2jmobius/gameserver/phantoms/commerce/L2jPhantomSupplyJourney.java` | Offline gear candidate рассматривается и при доступном NPC; сравнение с NPC price+round-trip fees и fresh authoritative quote. Owned gear priority, reserves, leases, payment/CAS owners сохранены. |
| `java/org/l2jmobius/gameserver/phantoms/background/PhantomHistoricalBackgroundService.java` | Три recovery admission gates используют existing exact-current contract вместо farm-only; ACTIVE и canonical idle validation сохранены. |
| `java/org/l2jmobius/gameserver/phantoms/background/PhantomVisibleFarmTravel.java` | Opt-in bounded retry events до clearRoute: current/issued/actual movement, route/arrival, owner boolean, goal/revision/epoch/request/timing. Compact XYZI и captured issued Z/instance. Движение/отмена/deadline/retry policy не менялись. |
| `test/java/org/l2jmobius/gameserver/phantoms/commerce/PhantomCareer121Checks.java` | Новый focused main: три synthetic cost contrast groups. Не native stock/payment/equip test. |
| `test/java/org/l2jmobius/gameserver/phantoms/background/PhantomLivingWorld112Checks.java` | Existing main получил focused `--recovery121`: canonical idle/farm, foreign/null/terminal и same-profile changed revision refusal. |
| `docs/phantoms/reports/PHANTOM-LIVING-CAREER-121.md` | Этот отчёт. |

Итого: 5 существующих файлов изменены, 2 новых; public config/datapack/SQL/build/infrastructure files не изменены. Private scripts/raw/DB/credentials/JAR не входят в commit. Scope меньше8–10 файлов; расширение на stop subsystem без regression не включено.

## Причинные RED→GREEN и review

**Gear:** старый gate рассматривал offline gear только при отсутствии NPC. Synthetic comparator RED на более дешёвом допустимом offline предложении при NPC наличии; GREEN после comparator fix. Production wiring проверен чтением и независимым diff review, не synthetic execution полного equipmentProposal. Проверены fees, material basis,60% risk, distance ties и absent quote. Это source/policy доказательство; native offline paid upgrade не доказан.

**Recovery:** native окно2 воспроизвело `recovery.control_baseline:catchup.native_recovery.goal_mismatch` для canonical historical idle. `recoverOwned` уже поддерживал такой idle, а три historical admission gates допускали только farm. Focused RED отверг valid idle; GREEN переиспользует `recoveryGoalCurrent`, включая exact equality, ACTIVE и visiblePredecessor. Claim status/rowVersion/payload, generation, saved component и object/epoch fences не ослаблены. Native GREEN нового JAR пока NOT_RUN.

**Travel:** причина STUCK120 не установлена, STUCK fixed не заявляется. Первоначальные route.toString() в retry diagnostics могли обрезаться лимитом48; заменены compact XYZI (максимум47 символов для4 int). Три события не атомарны, owner представлен boolean без полного token/evidence identity; recorder expiry/ring overflow могут терять события. Это подготовка witness, не доказательство физической непроходимости или recovery.

Независимый reviewer проверил exact diff6 source/test files: P1/P2=0. Отдельно проверены stop-only helper и read-only snapshot helper. В последнем reviewer обнаружил P2/API ошибку вызова diagnosticScalars у интерфейса Owner **до compilation/execution**; исправлено через existing `instanceof PhantomNativeWorkScope` аналог, compilation PASS. Review не засчитывается как native acceptance.

Private restart acceptance helper также прошёл RED→GREEN: phase после restart больше не может выбрать другого actor вместо exact preferred identity. Этот helper test не доказывает состоявшийся restart.

Review отчёта выявил1 P2: слишком широкий B_RESERVES/POLICY PASS из budget tests. Claim исправлен на OPEN/NOT_PROVEN; успешные source/DB-free checks не переносятся на неподтверждённые native gates.

## TEST fixtures и CUT provenance

Перед CUT подготовлены четыре реальные Player/Phantom identities. Все уровни, skills, начальные предметы, recipes, деньги и позиции — **PREPARED_NOT_EARNED**; grants/позиционирование не объявляются добычей, SP-paid learning или WALK.

| Profile/object | Назначение | Class/level | EXP/SP при fixture preparation | Seed cash | Legal initial anchor XYZ |
|---|---|---|---|---|---|
|26/268480990|Earn|1/20|835862/0|0|`generated.farm.187426bb30110951b41e0ba9.anchor`, -46177,210132,-5080|
|27/268481180|B gear|2/52|51262490/0|5,000,000|`generated.farm.4193b9085223359f9148e155.anchor`,56877,170300,-4976|
|28/268481770|A mage|12/61|146308200/0|15,000,000|`generated.farm.2d848c4c64b764a5c6703a13.anchor`,66628,111182,-3688|
|29/268481370|Dwarf craft|57/52|51262490/0|5,000,000|anchor как27|

Dwarf подготовлен с official recipe115/scroll2222/product60/rank6/100%, реальным MFG skill и BOM `{1459=95,1887=7,1890=21,1892=4,1977=10,2131=48}`. Recipe/BOM подготовлены, native craft outcome не заявляется. Initial gear брали из real NPC buy offers и official RecipeData products; это provenance разрешённых fixture assets, не market stock или native purchase.

Fixture attempts сохранены, не скрыты:

1. Authority drift до создания fixtures: population initialization hash использует timezone id `UTC`, не ZoneOffset id `Z`. Исправлен helper, не production timezone/config.
2. Создан26, planner target/route absent; enrollment ещё не выполнен. Не восстановленные vitals перед planning исправлены до CUT; разрешены bounded authoritative anchors, все placements помечены PREPARED.
3.26/27 завершены;28 остановился на отсутствии подходящего source-backed B weapon в первоначально слишком узком NPC-only setup. Повторных grants завершённым identities не было.
4. Точный empty partial28 продолжен через official recipe product catalog,29 подготовлен; все4 сохранены/readback. Hash старых retained rows до/после continuation совпал: `61047f78e9906874d0230c55487d87e6fb27831a2e9ce9c8ea7fa3758960550a`.

После каждого принятого CUT не было grants, reposition Phantom, fake kills, direct gameplay calls, state/debt reset или receipt patches. Follow/catchup телепортировал только маркированного non-GM observer; это не Phantom travel evidence.

Ограничения setup: из-за промежутка между enrollment26 и первым native observation исторический catchup уже изменил EXP/SP/loot/HP. Поэтому fixture EXP835862→первое native842999/SP545 и появившийся до observation loot НЕ EARNED_NATIVE окна2. На первом observed CUT cash0. Следующая попытка должна фиксировать этот baseline своевременно, без сброса debts.

Warehouse pressure setup содержал ограничение128 slots при native limit250, то есть не гарантировал порог80%; несколько grade shot stacks также не гарантировали shortage для withdraw. Pre-cut bank transfer item22083×600 сохранён как FIXTURE, не warehouse outcome. После CUT эти условия не «докручивались». Для S4 потребуется отдельный корректный pre-cut fixture, после разрешённого safety recovery; текущий S4 не принят.

## Native окна и экономический ledger

**Окно1, baseline JAR:** 24 managed identities; наблюдения UTC21:01:35–21:08:28, sampled elapsed max472697ms. Selected NORMAL2/object268484956; у него no rewarded kills, no movement STUCK witness. Другой profile16 имел реальный native kill/reward sequence1, EXP87→174/SP10→20. Это baseline native reward, не доказательство recovery выбранного actor или новой policy. STOP/STORE1, exit0 и postflight1 PASS; identities preserved24. Final SQL snapshot1:224 adena stacks,total2,520,125,630; значительная merchant provision, не earnings.

**Окно2, candidate e3a7:** same DB, другой JAR и4 новых fixtures, поэтому не qualifies sameJAR restart. Selected26; наблюдения UTC21:53:10–22:03:04, sampled elapsed max649782ms. Native damage/kill/reward sequences26 все0. Он был атакован NPC и погиб; EXP842999→828670, level20→19, SP545 и cash0. Canonical native recovery перенёс его в Gludin(-83520,150560,-3120), затем admission вернул goal_mismatch. Recovery26 полезного farm не доказал; sticky failure привёл к stop blocker выше.

Параллельно profile2 прошёл реальные paid NPC операции через existing Commerce:

|UTC/operationKey|Операция|Cash before→after|Native facts|
|---|---|---|---|
|21:55:43,`7e8543a61cda54ae18ef872eabf1e395cea910d84ba79cc278cef1922678ba83`|BUY item1835×332,amount2656,NPC30561/list3056100|5235→2579|COMMITTED,rowVersion2,count332|
|22:01:05,`d6723616df7cef8f61c5b2db839d97a03040368ef7cf1049076102b1f72b6e73`|BUY item253×1,amount678,NPC30558/list3055800|2579→1901|COMMITTED,rowVersion5; equipped object268521468/item253 в live inventory|

|Actor/window|EARNED_NATIVE cash|REALIZED_NPC|REALIZED_OFFLINE|SPENT_PURCHASE|SPENT_SHOTS|FEES|STOCK_COST_BASIS / FIXTURE_ASSET|
|---|---|---|---|---|---|---|---|
|26/window2|0 подтверждено|0|0|0 зафиксировано|0 зафиксировано|0 зафиксировано|Подготовленный D stock/gear; background loot до observation исключён|
|2/window2|0 подтверждено|0|0|2656 shots+678 gear=3334|Native consumption cost после покупки не доказан|В этих2 receipts0; полный transport ledger не доказан|332 paid shots cost2656,gear678; source cash5235,не earned-only|
|27/28/29|NOT_MEASURED|NOT_RUN|NOT_RUN|NOT_RUN|NOT_RUN|NOT_RUN|Seed cash25,000,000 суммарно,gear/recipe/BOM/warehouse stock PREPARED|

`5235-2656-678=1901` подтверждается recorded ConservationFacts и live wallet. После этих покупок profile2 native kill/reward sequences0. Не называю это earned refill, прибыльным циклом, B/A upgrade или полноценным restart durability. Liquidation estimate/остаточная стоимость всего stock не вычислены без свежих подтверждённых quotes; отсутствующие числа не заменены нулём прибыли.

Открытые200 native merchants и их provisioned capital — finite bootstrap, не paid offline trade. Для121 не найден captured COMMITTED player↔offline merchant/MFG receipt. Не выдаю отсутствие такой receipt за NO_PROFITABLE_OFFER для всех подготовленных mid-grade сцен: эти сцены ещё не запускались.

## Acceptance matrix

|Gate|Статус|
|---|---|
|G0 before CREATE/GS|PASS на выполненных стартах; final exit G0 BLOCKED|
|A_POLICY STUCK/native owner|OPEN: retry diagnostics доставлены; физическая причина STUCK не доказана|
|Canonical idle recovery admission|SOURCE_GREEN; native reproduction RED, исправленный native GREEN NOT_RUN|
|A_WORLD / A_RESTART|NOT_PASSED / BLOCKED; квалифицированных sameDB/sameJAR restart0|
|B_EARNED|NOT_PASSED; baseline другой actor reward1 и window2 NPC purchases не составляют closed earned cycle|
|B_RESERVES / warehouse|Native gate OPEN / NOT_PROVEN; existing loot category/surplus smoke PASS отдельно:275 D/C parts и6 contrasts; deposit/withdraw NOT_RUN|
|C_GEAR|SOURCE selector GREEN; native B/A paid equip+charged reward NOT_RUN|
|C_OFFLINE|NOT_PROVEN; OPEN merchants не COMMITTED player trade|
|C_CRAFT|PREPARED dwarf/recipe/BOM; native craft/restart outcome NOT_RUN|
|D scale/lifecycle|DB-free idle5000 PASS; actual128 visible/load/complete stop2 NOT_PROVEN|
|Owner visual QA|PENDING; human client login отсутствовал|
|FULL121|NO; PARTIAL_PRODUCT с реально доставленными source-модулями|

Независимые source/negative/diagnostic milestones продолжались при native blocker. Остальные native сцены требуют того же полного G0 и только одной owned121 schema; запуск их поверх незавершённого holder нарушил бы safety contract. Другую TEST DB в обход blocker не создавали.

## Проверки и фактические отказы

Приведённые команды сборки и DB-free проверок выполнялись из собственной source-копии; Ant последовательно. Native/helper commands выполнялись в private stage. Общий `ant verify` и старые native DB suites не запускались вместо targeted gate.

```powershell
ant -f .\L2J_Mobius_CT_2.6_HighFive\build.xml compile-tests
java -Dphantom.module.root=<source-module> -Dphantom.test.reports=<own-evidence> -cp <build/bin;build/phantom-test/bin;dist/libs/*> org.l2jmobius.tests.phantoms.PhantomTestLauncher life-scenarios116 121121
# Аналогично supply071 и post001-recipe-cost с seed121121.
java -cp <same-classpath> org.l2jmobius.tests.phantoms.PhantomRuntimeFlightRecorderSuite
java -cp <same-classpath> org.l2jmobius.gameserver.phantoms.background.PhantomLivingWorld112Checks --recovery121
java -cp <same-classpath> org.l2jmobius.gameserver.phantoms.background.PhantomLivingWorld112Checks
java -cp <same-classpath> org.l2jmobius.gameserver.phantoms.commerce.PhantomCareer121Checks
java -cp <same-classpath> org.l2jmobius.gameserver.phantoms.commerce.PhantomEarnedMarket119Checks
# Из source-module/dist/game, без GameServer/DatabaseFactory init:
java -cp <same-classpath> org.l2jmobius.tests.phantoms.PhantomLootDisposition068Checks
ant -f .\L2J_Mobius_CT_2.6_HighFive\build.xml jar
```

Exact expanded orchestration сохранена в private `Verify121.ps1`, результаты `final-*.log`. Recorder direct main запускался из module directory. Его suite отсутствует в mapping PhantomTestLauncher: первоначальный guessed `runtime-flight-recorder` mode дал exit2; исправлено на существующий direct main,3/3 PASS, уже прошедшие suite не повторялись.

|Проверка|Результат|
|---|---|
|Final compile-tests|exit0,28s,5 прежних warnings (ExtendedOpenOption×3,runFinalization×2)|
|life-scenarios116|exit0,32/32|
|supply071|exit0,9/9|
|post001-recipe-cost|exit0,4/4|
|Flight recorder direct|exit0,3/3|
|Recovery121 admission|exit0, focused group; DB0/actors0|
|Idle112 full|exit0;5000×121min:605000 planning calls,10083 probes,threadDelta0;32×1441min:46112 calls,768 probes|
|Equipment121|exit0,3 synthetic groups|
|Market119 selector|exit0,5 synthetic contrasts|
|LootDisposition068 existing smoke|exit0; actual1000 recipe catalog,275 D/C key parts не SELL_NPC,6 valuable/cheap/shared/full-bank/unknown contrasts; category/surplus policy, не native warehouse transaction|
|Final jar|exit0,20s после EOL-only repair|
|Native1/postflight1|exit0/PASS|
|Native2 cleanup|FAILED `DRAIN_BLOCKED_NO_FORCED_KILL`; wrapper ещё ждёт JVM|
|Separate ADMIN STOP-only|exit1,agent loaded,`DRAIN_REMAINS_BLOCKED`; stock Shutdown not called|
|Read-only ADMIN holder snapshot|exit0; exact holder2,one pending timer|
|Protected1235|drift0; read-only повторная проверка|
|Final STOP/JVM0/listeners0/SQL0|BLOCKED, не выдан PASS|

Первый diff whitespace check выявил CRLF на добавленных строках mixed-EOL files. Исправлены только добавленные строки, старые EOL сохранены; outside-checkout preimages и normalized-text equality проверены. Semantic source не менялся, затем выполнен jar build. Whitespace checks и постоянный Git config не менялись.

## SHA и raw provenance

|Артефакт|SHA256|
|---|---|
|Baseline native1 GameServer.jar|`7c3738c14c21bd9db01e4d1c3353f736b72fea345a826b37e31b5d2c05ee0f94`|
|Active sealed native2 GameServer.jar|`e3a7259b3e9047a369d7ea330b541a386211cce0614519ff550327b15a793ba2`|
|Final source GameServer.jar, NOT_PROMOTED|`710a0955dd926bc8bc40ce98a50f1ea8e2714052cf7744e590c0920432d91c4b`|
|Native2 full input manifest|`8d8ebf04c9aaab1627751c8757d3c83680d286e53dcd6971f26684dc008561f2`|
|Public input seal owner field|`5738bf3fb74f85427a95334e066524e8fa2639bc2693a8f958dfe4bbca45cd5a`|
|Native2 owner.properties|`b882c1e2c9bf02be494eaf1e365f6d0b9e8ef5410c6ab187bfc4c0a39975ad72`|
|fixtures121.complete.tsv|`6afa7c4b5d6fb055e7b185f0d5128b2ca61bc0f816c82e6990c7aa2ccafb9329`|
|Stop-only agent JAR|`7f9b3283d354d675bd83652b546df9452eb22d7b8c2e32e61f19c14832ae68e4`|
|Read-only snapshot agent JAR|`7dee6836f3dda622419b742e28a12777f79d84b340b4fdaef224640567b8bc9f`|

Full manifests содержат per-file config/data/Geo/helpers hashes, а не только JAR. Без нового G0/promotion final source JAR не подменял работающий sealed e3a7.

Private raw: `%LOCALAPPDATA%\Temp\phantom-living-career-121` (plans, helper sources, build/test/RED/GREEN/reviews/summary/repair receipts); stage `private` (authority/seals, fixture attempts/manifests, samples/inventory/skills/census/life/flight/payments/resources, stop snapshots). До повторного prep старый log архивировался и SHA сверялся. Stage/public seal promotion использовал immutable backups, fail-closed pending и single-row own121 authority input-hash CAS; game/profile/receipt rows этим CAS не патчились. Native/input helper reseal не считался игровым событием.

ADMIN STOP op `e783efa03b06408ba7ad4cd9ad4fbe6d`; read-only op `f78180bb6c0a40f084117612c7f63444`. Instrumentation/redefine/retransform/field mutation отсутствуют. Оба helpers — отдельные121 копии existing operator pattern, не startup/fault agent и не носители игровой функции.

## Затраты, Git и оставшаяся работа

Native starts2, повторный sameDB start1 с changedJAR; qualified sameJAR restart0; fixture preparation invocations4. Summed sampled native elapsed18.71min, без startup/shutdown и времени оставшегося FAILED JVM. Heap peaks window1=4,052,964,472 bytes /136 threads; window2=4,057,434,936 /132. Это samples, не профилирование и не доказательство production capacity.

Login9014 reconnect noise: для headless окна2 ровно135 connect +135 unavailable строк в captured prefix до main finish failure. После failure живой JVM продолжает писать noise; он не вырезался из raw и не включён в это фиксированное число. Window1 работал с GUI, private wrapper console не покрывает весь GameServer log; число его retry не заявляется0.

На cost snapshot перед source publication goal metadata показывала976973 tokens и6576s (следующая clock receipt UTC22:35:54). Это промежуточная фактическая usage, не заданный лимит; денежная стоимость через tools не предоставлена. Дополнительные source/fixture/authority/object-store проверки увеличили расход. Полный счётчик distinct прочитанных файлов и rg invocations не велся, точное число не выдумывается. Initial READ_SET13; public changes7, новые public artifacts2. Много времени ушло на защиту authority/fixtures и разбор отказов; это не native farm soak и не прокачка1→52.

Git использовался по bounded root/module разрешению для baseline/scope и по явному TASK121 разрешению для собственных source commits/ordinary push. Рабочий root read-only; ветки master/default/experiment не изменены. Доставка только descendant baseline3e в `feature/phantom-world`, без force/history rewrite. Ниже перечислены формы выполненных команд; exact пути приведены в allowlist и isolation выше:

- Read-only: `git rev-parse HEAD`, `git rev-parse --is-shallow-repository`, `git symbolic-ref`, `git status --short`, `git status --porcelain=v1 -uall`, `git worktree list`, `git for-each-ref`, `git ls-tree -r --name-only HEAD .idea`, `git ls-tree -r -l --full-tree HEAD`, `git ls-remote https://github.com/kpCat/L2J.git refs/heads/feature/phantom-world`.
- Exact diffs: `git diff -- <exact source paths>`, `git diff --numstat -- <exact source paths>`, `git diff --unified=0 -- <one exact source path>`, `git diff --check -- <allowlist>`, `git diff --cached --check -- <commit paths>`.
- Own clone setup: `git clone --no-hardlinks --single-branch --no-checkout <source120> <source121>`, own sparse-checkout HighFive и detached checkout exact3e. Root/foreign refs при этом не писались.
- Own object-store repair: первый commit выявил absent `.idea` blobs; bounded current tree inventory нашёл554 missing blob references. Один exact blob получен GitHub `fetch_blob`, ещё99 raw и426 normalized immutable blobs восстановлены из read-only root files **только при совпадении ожидаемого Git blob SHA**. Использован `git hash-object -w --stdin` только в clone121; реальные root/WIP bytes и tree не менялись. Повторяющиеся references разделяют object; это не554 разных записанных файлов. Обхода/удаления `.idea` или Account_Manager не было. Commit failures сохранены; после repair missing0.
- Автоматическая Git maintenance при двух commits отдельно сообщила `unable to read 39c24010ebe80ca7df56e40e304c8be1214d736d` / geometric repack failed. Commits завершились exit0; historical clone completeness не объявляется PASS. Репозиторная история и чужие object stores не исправлялись. Для оставшегося report commit auto maintenance подавляется только command-scoped настройками, без постоянного config change; whitespace/safety checks не отключаются.
- Scoped staging/commits: `git add -- <exact three gear paths>`, `git commit -m 'TASK121 compare offline equipment with delivered NPC cost' -- <same three paths>`; аналогично exact3 recovery/diagnostic paths с message `TASK121 accept canonical idle recovery and capture travel retries`; report отдельно. `add .`, Stage All, clean/reset/stash/force/restore не использовались.
- Source publication: `git push https://github.com/kpCat/L2J.git HEAD:refs/heads/feature/phantom-world`, exit0; remote3e→af93083e подтверждён последующим `git ls-remote`. Before-push exact scope guard7 PASS, outsideScope0.

- mojibake-маркеры в изменённых файлах проверены:27 markers,7 файлов,0 matches.
- escaped Cyrillic в изменённых файлах проверены:6 patterns,7 файлов,0 matches.

Final exact allowlist receipts сохраняются в private evidence; полный baseline/current tree inventory проверяет отсутствие committed paths вне allowlist, кроме проверки current dirt. Из-за native safety blocker успешная source publication не означает FULL121 или final G0 PASS.

Продолжение только TASK121: решить safe stop holder2 без снятия fences, затем новый full G0, promotion final exact JAR и targeted recovery/independent B/C/D scenes с correct pre-cut fixtures; для sameJAR restart нужен тот же exact actor и новые rewards. Native stop fix сначала требует focused lifecycle regression. TASK122 не запускать. Owner visual gate остаётся pending.
