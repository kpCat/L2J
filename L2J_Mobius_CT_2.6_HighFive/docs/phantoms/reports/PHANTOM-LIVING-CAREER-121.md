# TASK121 — ограниченная поставка: PARTIAL_PRODUCT

Дата: 3–4 ноября 2026. После уточнения владельца scope заморожен: закончить текущий Oren arrival fix и связанную проверку существующего персонажа31, сохранить сделанное, выполнить review/regression/STOP/postflight и опубликовать результат. Новых персонажей, игровых подсистем и дополнительных независимых сцен после этого уточнения не создавали. TASK122 не запускался.

**FULL121 не достигнут.** Подтверждены native B-покупки, экипировка и возвращение31 к настоящему rewarded PvE после Oren repair. Новые rewarded kills после следующего same-JAR restart не подтверждены; полная сохранность native progress также остаётся OPEN. Это самостоятельная проверенная поставка с явно сохранёнными ограничениями, а не SUCCESS всей первоначальной вертикали.

## Что действительно произошло с31

До CUT11 подготовлен Own121GearFreshB, profile31/object268481075, Gladiator2/level54/EXP63907911/SP0, Adena5000000, C-оружие266 и исходная экипировка, C Soulshot1464×600 и другие source-backed расходники. Это PREPARED_NOT_EARNED. Исходные29 identities сохранены; затем cohort30/cap8. После CUT11 персонажу не выдавали уровни, предметы, деньги, координаты или исправленные debt/receipt состояния вручную.

|Факт|Настоящее доказательство|Граница утверждения|
|---|---|---|
|Покупка B Blue Wolf Gaiters12193×1 за960278|Native14, PRIVATE_STORE_BUY COMMITTED/SUCCESS; operation042614f72eedea4e91ebda2e97a67c4de3b2a0bcbd3c45af16198ca9cecf5d54; seller268488155, stack268488163|Оплачено fixture-капиталом|
|Экипировка этих B-гетр|Native15: supplies.equipment.owned_equipped, item268488163 в gear; postSQL PAPERDOLL; restart16 сохранил предмет|Это ещё не доказательство rewarded restart|
|Продажа прежних C-гетр382×1 за154850|Native16, NPC SELL COMMITTED, operatione05e81cc45523bd99f9e5f980ac30fc8ee74b58aae3b3a024e284e1e897c18ee; wallet3949242→4104092, item1→0|Ликвидация fixture-имущества, не заработанный лут|
|Покупка B Avadon Circlet12170×1 за387860|Native17, PRIVATE_STORE_BUY COMMITTED/SUCCESS, operation764ca8a834117002704a8c589d1888fa2920563a733d50b7f190f8a04b7f20c9; seller268484946, stack268484951|Оплачено fixture-капиталом|
|Экипировка circlet, прохождение Oren и достижение farm|Native18, оба B-object в gear, committed farm92657,-11702,-2264; actual WALK/GK, без записи Player XYZ из fixture-кода|Исходный weapon266 остаётся C-grade|
|Настоящий PvE в этой экипировке|Native18, exact owner epoch573525934789100: damage29, kills2, rewards2, farmCycle1, loot4; native EXP+30306/SP+4020|Это максимум наблюдённых counters одного epoch, не все поздние последствия|
|Правильные shots|Тот же C1464/object268481165:600→549; другой grade1465 остался600; postSQL сохранил549|Не B-shots и не earned restock: grade определяется C-оружием|
|Сохранение предметов|PostSQL18 и19:12170 и12193 PAPERDOLL,1464×549; inventory/offline hashes18→19 совпали|Не доказывает сохранность всех EXP/SP последствий|

В native18 есть exact-owner и human-local/visible/knownObject/surroundingRegion samples. Не всё окно было local=true: setup и движение наблюдателя отделены от игровой механики. Реальный клиент владельца не подключался; visual QA остаётся PENDING.

## Oren: причина, исправление, проверка

Native17 дал182 сохранённых VISIBLE_ARRIVAL_REFUSED событий с drain.anchor_position: actor31 фактически82971,53207,-1483, а source ROUTE anchor generated.route.live002.oren.arrival.anchor —82971,53207,-1488/tolerance0. Строгая проверка Z отвергала штатную GK-посадку на пять единиц над тем же Geo floor. Это конкретный причинный witness, не вывод из одного старого arrival_capture_pending.

Source commit1d5245d767f6e7d2d79aeb37f12f37e64bb96596 добавляет общий recognition predicate для live anchor, durable anchor и uniqueness. Original tolerance проверяется первой; исключение только ROUTE/tolerance0/exactXY/instance/+5 с наличием Geo и стабильным тем же floor. Повторно используется существующая atWalkDestination семантика. FARMING/CITY rules, ambiguity rejection, committedAnchorId, raw Player/stored XYZ, owner/goal/STORE/leases/CAS не изменены. Координаты не округляются и не подменяются canonical intent.

Actual production predicate воспроизвёл focused RED, затем GREEN. Negative matrix: CITY/FARMING, другой instance/XY, Z−5/Z+6, nonzero tolerance, неправильный Geo floor; canonical arrival не вызывает lazy Geo. Полный existing itinerary suite прошёл. Независимое read-only review этого diff и private18/19 guards: P1/P2=0. Native18 затем действительно прошёл прежнее место отказа и выполнил rewarded combat.

## Restart и сохранность progress: OPEN

Native18 и19 использовали один JAR93e14ec6e4b2ecce470008e66bdcd123976de6c240610c3465534cbfcb381da3 и public input seal3402ba47f4fe62ad82c93b36908bc537ba0b57135c9eeabb8cbe29d4ec9c9378. CUT19 связан с checkpoint18 SHA7795a0a30ddaf80f93a999ef0684ce63d0f2dcf7c7312a19340243ef92edb533. Сохранились все30 identity pairs; reset/regrant/replay вручную не выполнялись.

В flight19 был ранний MATERIALIZE_SUCCESS/HISTORICAL_BASELINE→WORLD. Поэтому неверно говорить, что World restore вообще отсутствовал. Но во всех21 последующих samples06:59:06→07:02:27Z actor31 был WARM, eligible=true, admitted=false, reason=capacity, без sampled World owner/locality; setup19 selected=0. Root стал canonical idle/revision6 с planner.target_or_route.absent. Нового native rewarded PvE19 не доказано.

Здесь capacity означает отсутствие31 в логически выбранных admittedIds после population policy, а не доказанную занятость физических8 World slots. Причина пустого setup target и конкретный внутренний target/route/viability predicate не установлены. В operator setup нет farm-only фильтра. Knowledge hash18→19 изменился при остальных трёх одинаковых hash; причинность этого изменения не доказана. После устойчивого witness19 остановлен, следующая ветка исправлений не открывалась.

Отдельное существенное ограничение: последний live31 sample18 в06:54:57Z — level54/EXP63938217/SP4020/Adena3613217. Сохранённые SQL после STOP18 и STOP19 одинаковы: level53/EXP63705869/SP10050/Adena3617027. Поздняя дельта EXP−232348/SP+6030/Adena+3810 не покрыта приведёнными native counters и не засчитывается как новые native earnings или successful conservation. Actor уже отсутствовал в World в samples06:55:07 и17Z, до GM shutdown08:55:23 local; приписывать всю дельту финальному STOP drain нельзя. Точная причина остаётся UNPROVEN. Observed PvE18 подтверждён; durable native progress и A_RESTART product gate — OPEN.

## Экономика и остальные milestones

Для единого последнего live среза18 денежная сверка:5000000 fixture +10585 observed cash increase +154850 fixture liquidation −960278 B-legs −387860 B-head −3480 potions −200600 transport =3613217. Transport собран по native кошельковым переходам13/14/16/17/18; это не независимый полный банковский audit. SQL tail3617027 показан отдельно выше. Оставшиеся prepared stocks не считаются earned stock cost basis; стоимость прочего loot/liquidation не выдумывается.

Реальная offline покупка31 healing1061×20@174 за3480 также COMMITTED: operation8a2db720f53f6f5c28701705b64ae9d7fc4f3af67964d609a8489342157bfc18. В native5 профили27/29 купили такие же партии у seller268485148: cash100000→103480→106960, stock268485155100→80→60, buyer items сохранены. Это реальные player/merchant сделки; provisioned merchant capital и стартовые кошельки не считаются заработком.

|Milestone|Итог|
|---|---|
|A_POLICY / Oren→observed native PvE|Подтверждены причинный fix и настоящее исполнение18|
|A_WORLD полный acceptance|PARTIAL: observed PvE есть, durable native progress не подтверждён|
|A_RESTART / durable progress|OPEN: после-restart rewarded continuation не доказан, поздняя progress-дельта не атрибутирована|
|B_EARNED farm→earned refill→farm|OPEN: есть реальные kills/loot, но нет полностью после-CUT заработком профинансированного refill→нового rewarded kill|
|B_RESERVES|SOURCE/DB-free policy checks: ценные materials/recipes/crystals не NPC trash; native warehouse receipts отсутствуют|
|C_GEAR B|Покупка+экипировка+matching-shot rewarded combat18 подтверждены в наблюдённом epoch; капитал fixture|
|A-grade|NOT_RUN native; подготовленный ранее61+ fixture не выдаётся за состоявшуюся покупку/бой|
|C_OFFLINE|Подтверждены COMMITTED реальные сделки и durable предметы; не earned-only|
|C_CRAFT|OPEN: source recipe/delevel/cost checks есть, native ingredient consumption/result/restart outcome не получен|
|D scale|DB-free5000 planner bounds PASS; actual128 visible/production capacity NOT_PROVEN|
|Owner visual QA|PENDING, без login клиента|
|FULL121|NO — PARTIAL_PRODUCT|

Craft30 до freeze был законно подготовлен на44 с recipe115/BOM/warehouse reserve, но native10 остановился на historical transaction.state_conflict до полноценного исполнения. Причинный paid-travel witness получен для31, его нельзя автоматически переносить на30. Native deposit/withdraw/craft не засчитаны. Новую архитектурную разработку, A-grade/warehouse/craft сцены после freeze не начинали. Исследовательский Dion/earned32 код сохранён только в private evidence; новый32 не родился и код не активирован в final runtime.

## Почему было19 native starts

|Окна|Зачем и что установлено|
|---|---|
|1|Baseline: managed ordinary World/reward observation; не полный selected career loop|
|2|Pre-cut earning fixture; death/idle recovery и FAILED STOP с оставшимся holder|
|3–4|Canonical idle recovery/handoff проверки; нет rewarded farm|
|5|Pre-cut gear/craft сцены; реальная C-покупка и offline trades; recipe/delevel failure|
|6–8|Проверки native landing, recipe defer и interrupted ingress; без farm rewards|
|9|Реальное движение по segmented ingress; optional supply WALK достиг unchanged deadline|
|10|Новый ранее разрешённый craft44 fixture: historical fence; craft/warehouse не произошли|
|11|Новый ранее разрешённый B54 fixture31; точный paid historical travel STATE_CONFLICT witness|
|12|Paid native handoff fix; обнаружен knowledge-hash mismatch legacy proof|
|13|Knowledge-compatible proof repair: тот же31 восстановлен в World, native GK/offline potion buy; observer оказался dead|
|14|Same-JAR13 с новым заранее подготовленным живым human observer; B-legs buy; preparation starvation witness|
|15|Pending-arrival drain fix; настоящий B-legs equip|
|16|Same-JAR15: durable B gear, native sale старых C-гетр/GK; новый arrival refusal|
|17|Только bounded diagnostic JAR; определён exact Oren+5 cause, куплен B-head|
|18|Oren repair JAR; B-head equip, canonical farm, два observed rewarded kills|
|19|Same-JAR18; baseline restore, затем idle/WARM; after-restart PvE не подтверждён|

Это19 gameN.begun с соответствующими GameServer logs, а не19 cold fixture/probe JVM. Окна2 и5 сохранили FAILED, game2/5.exit0 не создавались. Их отдельные recovery/failed-window close receipts не превращают исходные окна в PASS. Старый PID67120 завершён через проверенный STOP-only recovery bridge; позднее разрешение владельца на stock Shutdown этого PID не переносилось на другие процессы. Hard kill/force lease reset не использовались.

Сумма max sampled elapsed —5972097ms≈99.53min; она включает startup до соответствующих samples, но не полную process duration/tails/старый failed holder. Это не99минут PvE. Heap peak по19 окнам4063158168bytes, threads peak136; окно18 —3998900016/131,19 —3974941640/130. CPU18 exact PID58380/creation06:52:01.411466Z/JDK25.0.4.101:19.8125CPU-seconds за122.5241wall-seconds между двумя readings. Это samples, не production profiling или128-visible benchmark.

9014 connect/unavailable pairs по captured logs:1=0/0(GUI capture неполный),2=782/782,3=42,4=17,5=137,6=38,7=35,8=51,9=161,10=33,11=12,12=13,13=55,14=29,15=38,16=44,17=38,18=30,19=44; для3–19 оба числа каждой пары одинаковы. LoginServer не запускался; этот шум отделён от product summary, raw не вырезан.

На usage snapshot перед финальной публикацией goal metadata:4451594tokens/35914active-seconds; это не заданный лимит. Денежная стоимость инструментами не предоставлена. Основной ненативный расход — подготовка/сборки/G0/разбор последовательных refusal; общий счётчик всех прочитанных файлов/rg-команд не вёлся. Временный automatic approval review failure по usage limit остановил одну команду до её исполнения; после возобновления штатный review разрешил её повтор, обхода approval не было.

## Source, regressions и безопасное завершение

Original GitHub baseline3e340e45c4c2aed42b7eca5202440b0a8817901d. Работали в отдельном detached source checkout и физически изолированном owner+SYSTEM ACL stage Phantom-Native-Life-121-a078ca51-5673-47c4-9ced-c69e534c87e9, только schema l2jmobiush5_phantom_test_121. Root/WIP109, MAIN/PLAY, release057–061, другие TEST и общий MariaDB service не изменялись. Перед работой прочитаны все8 файлов TASK121, root/module AGENTS и обязательные контракты/локальные аналоги. Старые пути из исторических инструкций не использовались как рабочий baseline.

Кроме Oren fix, поставка сохраняет проверенные изменения: delivered-cost comparison NPC/offline gear с приоритетом owned item; canonical idle recovery; FAILED-stop drain ordering; narrow native paid-travel handoff/legacy knowledge proof без ослабления transaction guards; bounded ingress/whole-WALK budget; drain existing arrival перед preparation без нового segment/deadline; recipe defer при delevel без grant; opt-in bounded refusal diagnostics. Это штатные existing services/Player/native stores, без нового движка/LLM/UI/provider.

Final JAR собран `ant -f .\L2J_Mobius_CT_2.6_HighFive\build.xml compile-tests jar`, exit0/72s. После native19 выполнены на том же candidate, все exit0:

- `ant -f .\L2J_Mobius_CT_2.6_HighFive\build.xml owner-profile-validate`:70files/288overrides, changed0/conflicts0,38rules PASS; Adena amount×5/chance×1 сохранены.
- `PhantomVisibleItinerary068Checks` — полный existing suite с production Oren/preparation seam и negatives.
- `PhantomLivingWorld112Checks --paid-travel121` и `--recovery121` — paid-proof/canonical idle/stale/foreign/terminal contracts.
- `PhantomLivingWorld112Checks` —5000×121 и32×1441 virtual-minute planning, threadDelta0; только planner stage, не Player/DB simulation.
- `PhantomShutdown103Checks --stop121` — controlled FAILED сохраняется, другие partial actors drain; DB0/World0, не native STOP proof.

Classpath: isolated build/bin;build/phantom-test/bin;build/phantom-test/resources;module/dist/libs/*. JAR после проверок остался93e14…. Ранее в задаче прошли existing life116(32/32), supply071(9/9), recipe-cost(4/4), recorder(3/3), gear121/market119 и loot-disposition/recipe smoke. Они защищают соответствующие policies; не подменяют native warehouse/craft/restart. Четыре craft fingerprint classes совпали побайтово.

Последние native18/19: healthy STOP/STORE, configured=false/retained entries0, stock Shutdown ready, observer removed, run/postflight exit0. Final postflight19: global ownSessions0, charactersOnline0; final host G0 после regressions: JVM0/listeners0/SQL_TCP0, protected1235 SHA и original Geo203 без drift, exact OS/private ACL/live HEAD guards PASS. Общий `ant verify` и чужие DB suites не запускались. Successful shutdown не выдаётся за successful durable career progress.

## Exact final artifact scope и Git

Bounded exception:14 public files,12 existing+2 new;14-й L2jPhantomBackgroundAuthority добавлен только для доказанного Oren cause в той же recovery family. Точный allowlist под module L2J_Mobius_CT_2.6_HighFive:

```text
java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java
java/org/l2jmobius/gameserver/phantoms/background/PhantomHistoricalBackgroundService.java
java/org/l2jmobius/gameserver/phantoms/background/PhantomVisibleFarmTravel.java
java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundTransaction.java
java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundService.java
java/org/l2jmobius/gameserver/phantoms/background/L2jPhantomBackgroundAuthority.java
java/org/l2jmobius/gameserver/phantoms/commerce/L2jPhantomSupplyJourney.java
java/org/l2jmobius/gameserver/phantoms/commerce/PhantomOfflineSupply.java
java/org/l2jmobius/gameserver/phantoms/progression/PhantomOrdinaryCraftRecipes.java
test/java/org/l2jmobius/gameserver/phantoms/background/PhantomLivingWorld112Checks.java
test/java/org/l2jmobius/gameserver/phantoms/background/PhantomVisibleItinerary068Checks.java
test/java/org/l2jmobius/gameserver/phantoms/commerce/PhantomCareer121Checks.java
test/java/org/l2jmobius/gameserver/phantoms/player/PhantomShutdown103Checks.java
docs/phantoms/reports/PHANTOM-LIVING-CAREER-121.md
```

Git использован по task/root разрешению. Read-only команды: `git rev-parse HEAD`, `git rev-parse --is-shallow-repository`, `git symbolic-ref`, `git status --short`, `git status --porcelain=v1 -uall`, `git worktree list`, `git for-each-ref`, `git ls-tree -r -z <baseline|HEAD>`, `git ls-tree -r --name-only HEAD .idea`, `git ls-tree -r -l --full-tree HEAD`, `git merge-base`, exact-path `git diff --`, `git diff --numstat --`, `git diff --unified=0 --`, `git diff --check <baseline> -- <allowlist>`, `git diff --cached --check -- <commit paths>`, `git ls-remote https://github.com/kpCat/L2J.git refs/heads/feature/phantom-world`. Цель — baseline/scope/remote verification.

Own clone создан `git clone --no-hardlinks --single-branch --no-checkout <own-source120> <own-source121>`, sparse-checkout HighFive, detached checkout exact3e. Для отсутствовавших blobs использован `git hash-object -w --stdin` только в clone121 и только при совпадении expected immutable Git blob SHA; root bytes не менялись. Существующие old-history maintenance errors сохранены, history completeness не заявлена. Автоматическая maintenance отключалась только command-scoped `-c maintenance.auto=false -c gc.auto=0` для commits, не whitespace/safety checks.

Запись: scoped `git add -- <exact paths>` в первых commits, затем `git -c maintenance.auto=false -c gc.auto=0 commit --only -m <message> -- <exact paths>`; report отдельно. Публикация только `git push https://github.com/kpCat/L2J.git HEAD:refs/heads/feature/phantom-world` и последующий `git ls-remote`. Последний code commit1d5245d767f6e7d2d79aeb37f12f37e64bb96596, предыдущий diagnostic e81e18e775a5582ce2c2db10d3012c501eb94cf4; final report публикуется отдельным commit. Master/default/experiment refs не изменены; force/reset/clean/stash/history rewrite/StageAll не применялись.

Final exact14 scope guard: outsideScope0, baseline/current tree и рабочие файлы; два одинаковых allowlist/hash receipt сохраняются в evidence. EOL менялся только в added hunks с preimages и normalized-text equality. Перед заменой этого текущего отчёта сохранена побайтовая копия report-before-owner-completion121.md, SHAe1500cfb80efcdad5d841f628984e2e16fdab8f2ca0e2bf18db285b22ce8cb59. Исторические task packages и чужие reports не переписывались.

- mojibake-маркеры в изменённых файлах проверены: полный набор27,14files,0matches.
- escaped Cyrillic в изменённых файлах проверены:6patterns,14files,0matches.

## Raw evidence

Private evidence: `%LOCALAPPDATA%\Temp\phantom-living-career-121`; owner stage с UUID выше, каталог private. Основные immutable files: game1–19 logs/markers, checkpoint18/19.tsv, cut18/19.txt, samples18/19.tsv, life18-31.log, flight17/18/19.log, final-sql18/19.log, stop18/19.txt, shutdown18/19.ready, postflight18/19.pass. Также delivery-native-summary121.json, cpu18-first/last.json, delivery-*121.log и финальные scope/encoding/publication receipts. Full input manifests содержат hashes source/JAR/config/data/203Geo/helpers; promotion делала только own121 control metadata CAS, не игровые grants/receipt resets.

Основные hashes: JAR93e14ec6e4b2ecce470008e66bdcd123976de6c240610c3465534cbfcb381da3; public inputs3402ba47f4fe62ad82c93b36908bc537ba0b57135c9eeabb8cbe29d4ec9c9378; post18/19 inventory7d86817880e090aef27e5e32c0c05f5d8986f56485d7d253fdf0aa0bd33d7186; offline4cdd245406e4c48a75f028bb5a99501a0a27525245eb22ce467c2098d9b15e0d.

Дальнейшие работы перечислены как OPEN, но не запущены. Поставка TASK121 завершается в согласованном ограниченном scope; FULL121 и TASK122 не объявляются.
