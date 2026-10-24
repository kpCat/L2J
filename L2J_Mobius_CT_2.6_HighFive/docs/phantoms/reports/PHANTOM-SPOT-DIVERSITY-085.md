# PHANTOM-SPOT-DIVERSITY-085

24.10.2026. **TOTAL085=PARTIAL; P1 focused diversity PASS, production corpus/нативное распределение NOT_RUN.** Один canonical planner исправлен для новых global/background решений. Это не 5000 durable profiles, не World presence и не FINAL_RELEASE_GREEN.

## Authority и read-first

Фактический Git root: `D:\Codex\repos\L2J-phantom-world`, модуль High Five, `feature/phantom-world`, origin `https://github.com/kpCat/L2J`. Перед работой local HEAD, upstream и live remote branch совпали с `9c80ab6ff71b92ceb136a620317ca63cb912fd31`; tracked clean. JDK Temurin25.0.4.1, Ant1.10.17, Python3.12.10 только для read-only XML inspector. Default/master не меняются.

Прочитаны все 11 файлов пакета085, включая manifest, GOAL и NEXT_WORLD_BACKLOG; root AGENTS/README, PHANTOM_CURRENT_STATUS, CODEX_WORKFLOW_CONTRACT, OWNER_FINAL_RELEASE_TARGETS, отчёты065/072/074/076/084. Дополнительные AGENTS выше root и модульный AGENTS не найдены. SOURCE_MAP085 найден; отдельные code-map/pattern-файлы не требовались и не создавались.

Проверены planner и его вызовы/ordinals в HistoricalBackgroundService, ecology historicalSeed, KnowledgeQuery/model/builder/backend, topology query/validation, NormalGatekeeperTravel, VisibleFarmTravel, BackgroundService/CompetitionRegistry, FarmingService и PvP context. Ближайшие аналоги: immutable fixture `PhantomFarm074Checks`, canonical `PhantomGameKnowledgeQuery`/`PhantomNormalGatekeeperTravel`, существующие console suites. Новых owners, БД, worker, periodic task, schema, SQL cache или occupancy map нет.

072 и074 остаются PARTIAL по прежним native farm/supply/route цепям, включая ROUTE_BUDGET_EXCEEDED. 084 cap500 policy не пересматривается, NATIVE300/500 остаётся NOT_RUN. Studio и Semantic×4 deferred; финальный owner target5000 durable/cap128+/200+ native stores/Alt+B/QoL/owner QA сохраняется.

## Матрица

| Gate | Результат | Фактическая граница |
|---|---|---|
| P0-DATA | SOURCE_READ_PASS; PARTIAL | Свежая XML inventory для9 уровней ×4 ingress ×3 lower tiers; native Knowledge/Topology snapshot не построен |
| P0-BASELINE | PASS_FIXTURE | До изменения500/1000/5000 virtual selections; top1=100% при одинаковых и разных seeds |
| P1-DIVERSITY | PASS_FIXTURE | Фиксированные4 comparable candidates/4 anchors; top1≤60% и≥3 anchors заранее зафиксированы; итог37.1–37.5% при1000/5000 |
| P1-LEGAL | FOCUSED_PASS / SOURCE_PASS | Canonical candidate/route/exclusion/viability сохранены; duration/reward/level negatives; paid GK native admission NOT_RUN |
| P1-STABILITY | FOCUSED_PASS | Repeat/recreated shuffled facts, source-hash identity, causal goalID/revision, два отдельных JVM с одинаковым digest |
| P1-LOCAL | FOCUSED_PASS / SOURCE_PASS | Live local-first,2km test и прежние074 controls; top8/native movement/retention owners не изменены; native travel NOT_RUN |
| P1-BOUNDS | FOCUSED_PASS | Ceiling8 pages; finite5000 virtual selections, CPU/heap/p50/p95 измерены; без нового global scan/lock/thread |
| P2-COMPETITION | PASS_AUDIT / REGRESSION_PASS | Operation cap/release, background10/10, farming2/2+1/1, PvP policy2/2; spot war/native PK NOT_RUN |
| P3-REGRESS | PASS | Один полный последовательный production/test compile, focused suite,074 и GK pure; отдельные encoding/scope checks |
| P4-DELIVERY | Post-commit gate | Exact4-file allowlist; результат единственного commit/normal push и SHA equality сообщается после публикации в финальном ответе |

## P0: фактический source inventory

Inspector `test/spot_diversity085_source_audit.py` читает текущие XML, пишет только в переданный external output и отказывает output внутри checkout. Он не подставляет permissive backend в production loader. Native `PhantomTopologyProductionCorpusSuite.beforeAll` вызывает `PhantomHeadlessPlayerTestEnvironment.initialize`, включая DB bootstrap; `L2jGameKnowledgeBackend` использует уже загруженные native loaders/SpawnTable. Нового разрешённого DB-free полного production bootstrap не найдено. Запрещённые БД и старые TEST не использовались.

Поэтому **PRODUCTION_CORPUS_NOT_RUN**: canonical runtime combined Knowledge hash, native eligibility, equipment/combat/reward policy, активные spawn conditions, NORMAL GK/Geo и production selection histogram — NOT_RUN. Source XML tree hash не называется native snapshot hash. Ни нулевые graph hints, ни одиночный source anchor не доказывают отсутствие пригодных альтернатив во всём мире.

Фактические XML counts: **1816 nodes,1817 anchors,2759 edges;1433 FARMING anchors**. Старые1031/1032/1507 в production corpus suite исторические и здесь не запускались/не обновлялись. Exact polygon-source geometry с bounded anchor-distance либо NPC-bound point association дали4759 source npcId@anchor pairs/1432 anchors. Point association допускает≤4 Z/position delta как source hint, без native normalized-point attestation. Есть1 FARMING anchor без такой source association; пригодность не выдумана.

SHA256 trees: ordinal path order; UTF-8 строки `relative/path\tfileSha256\n`, пути относительно `dist/game/data`, только XML. Все исходные bytes после работы сверены отдельно.

| Source family | Files | SHA256 |
|---|---:|---|
| phantoms/topology |15|8888d1b2765ffe1e8ddff9871e6ef94998afc2a9ae887536c183bd6dbfb83aec|
| phantoms/knowledge |2|884ffd9b466a999a9474cf5cef731b9c1f3786bd4c61f770634496cae43e0b27|
| phantoms/travel |1|04bae6f184dfdd003d1f2fd41801f79c1d82d868e431f5ef384135df50f9c65b|
| stats/npcs |137|d8223f1db6299ca1b8bbd0c0adec35f47a330ac837bc3a18e06e455ae368790c|
| spawns |187|12acb051a070b2ddc0b933d5fcf7b1f811a54a145b413da49f968c730982c05d|
| mapregion |28|862cb5fe4786536a66fc712a29c95d38ff7f77a73f2e261be93acda8c42977cd|

Ниже S2/S10 — source pairs/anchors в level[-2,+2] и полном lower[-10,+2], до native eligibility и bounded Knowledge pages. Это верхний source inventory, не promise canonical pool. Runtime summary≤64 areas/NPC и pages≤8 могут его уменьшить.

| Level | S2 pairs/anchors | S10 pairs/anchors |
|---:|---:|---:|
|1|42/32|42/32|
|10|69/37|167/95|
|20|57/24|153/76|
|40|190/73|705/244|
|52|214/90|791/255|
|61|241/74|496/165|
|76|833/234|1950/538|
|80|695/237|1813/519|
|85|126/26|1179/327|

Route-hint inventory только по authored BACKGROUND edges; без NORMAL GK и live traversability. Формат `lower radius: pairs/NPC/anchors/nodes/regions`. Regions — declared mapRegionLocId, UNKNOWN считается отдельно. При отсутствии результата после всех tiers —0. Все native eligible counts во всех ячейках **NOT_RUN**.

| Level | giran.city.center | population.ingress.human-fighter.01 | population.ingress.elf.01 | population.ingress.orc.01 |
|---:|---|---|---|---|
|1|0|2:1/1/1/1/1|2:13/3/11/11/2|2:2/2/2/2/2|
|10|0|10:1/1/1/1/1|2:8/4/4/4/1|10:2/2/2/2/2|
|20|0|0|2:2/2/2/2/1|0|
|40|0|0|0|0|
|52|0|0|0|0|
|61|0|0|0|0|
|76|0|0|0|0|
|80|0|0|0|0|
|85|0|0|0|0|

Одиночный human source hint — **NOT_ENOUGH_CANDIDATES** для diversity proof; elf20 даёт только2 anchors одного region. Нулевые ячейки — NO_SOURCE_ROUTE_CANDIDATES в этой ограниченной graph-проекции, не NO_ELIGIBLE всего native мира. Никаких новых координат/спотов, расширения зон или artificial equal coverage не добавлено.

## P1: причина и ограниченный выбор

Старый global comparator сначала строго сравнивал abs mob-level delta, затем число legs; seed использовался только после них. Ecology historicalSeed уже различается через generation/assignmentOrdinal/virtualJoinEpochMinute. Ошибка не объясняется «одинаковым seed у всех»: разные seeds тоже давали100% одного anchor на контролируемом input.

Один direct production fix pass, второй не использован. Candidate discovery сохраняет tiers[-2,-5,-10]+2,64 first/256 subsequent target pages, ceiling8, representative area/native viableTarget/route/exclusion guards. Global дубли npcId@anchor убираются до повторного route запроса, включая повторные area/page/tier факты. Live discovery/top8/distance comparator не меняют поведение.

Global reference: минимальные level delta → legs → summed actual route travelMillis → quoted GK fee → npcId/anchorId. Альтернативы допустимы только при delta≤reference+1, legs≤reference+1, time≤reference+clamp(referenceTime/2,30000,120000)ms, сумме quoted GK fee≤reference fee, EXP и SP каждого≥50% reference. Native viability и фактическая возможность маршрута обязательны до rank. Quoted fee — исходный feeCount канонических GK legs; actual wallet/discount/paid admission остаётся прежнему owner, это не обещание доступной поездки.

Вес: reward ratio(EXP, при0 — SP, при обоих0 —1) ограничен0.5..1.5; множитель configured source population `1+0.15*log2(1+min(16,amount))`; делители `1+0.35*extraLevelDelta`, `1+extraTravelMs/60000`, `1+0.15*extraLegs`. amount — max подтверждённого configuredAmount соответствующего node, не измеренная density или occupancy. Географический exponential rank `-StrictMath.log(U)/weight`; U получен из52 bits SHA256(profileId,saved seed,ordinal,anchorId). Для разных NPC того же anchor U общий, поэтому дополнительные NPC не дают независимых lottery tickets. Deterministic tie разрешается npcId/anchorId. Дальние/дорогие/невыгодные неподходящие альтернативы не получают вес за diversity.

Нового hash-key в SQL нет. Existing plan identity продолжает включать source authority hashes, generations, profile,seed,ordinal,route и constraints. Planner вызывается прежними causal initial/replan путями; status/read/remainsSuitable не rerank ACTIVE plan. Existing goalID/revision/receipts/rewards и checkpoint authority не переписываются этим кодом. Реальный DB restart существующих plan rows — NOT_RUN; immutability, same-input recreation и два JVM проверены отдельно.

## Неизменная fixture, BEFORE/AFTER

Четыре synthetic anchors разных nodes/declared test regions — прозрачная fixture, не реальные production locations. Level20,class0, один ingress, profileId1..N,ordinal0. NPC101:level20,EXP100/SP10,8 configured spawn,1leg/10s;102:21,90/9,4,1/10s;103:19,80/8,2,1/15s;104:20,95/9.5,4,2/20s. Это4 factual **внутри fixture** candidates; native Player не создавался. Authority получает immutable combat input и может отклонить target, не выдаёт наград.

Shared seed850085 проверяет индивидуальность по profileId. Ecology-shaped seeds используют существующую mix-формулу при объявленных virtual generation85/assignmentOrdinal=profileId/joinMinute29880000; это не чтение persisted population и не новые identities. Перед каждым замером100 warmup selections; один JVM запускает500→1000→5000 для shared, затем для varied. Поэтому timings зависят от JIT/GC/порядка, не являются controlled throughput comparison.

Порог до GREEN: **≥3 anchors и top1≤60% при1000/5000**. После исправления естественная неравномерность сохранена.

| Seed | N | BEFORE histogram | AFTER histogram farm.1/2/3/4 | Top1 before→after | Anchors/nodes/fixture regions before→after |
|---|---:|---|---|---|---|
|shared|500|farm.1=500|200/101/93/106|100%→40%|1→4|
|shared|1000|farm.1=1000|374/208/185/233|100%→37.4%|1→4|
|shared|5000|farm.1=5000|1875/1084/877/1164|100%→37.5%|1→4|
|varied|500|farm.1=500|187/107/81/125|100%→37.4%|1→4|
|varied|1000|farm.1=1000|371/212/168/249|100%→37.1%|1→4|
|varied|5000|farm.1=5000|1864/1099/864/1173|100%→37.28%|1→4|

## CPU/heap и focused proofs

`-Xms64m -Xmx256m`, current-thread CPU через ThreadMXBean; wall/latency через nanoTime, heapUsed через MemoryMXBean. Полный call включает candidate discovery/routes/rank/goal/identity; heap samples не являются allocation/retained-size оценкой и могут уменьшаться после GC. Ни real5000, ни production large-pool/World/SQL/GC load устойчивость не доказаны.

| Seed/N | Phase | Wall ms | CPU ms | p50/p95 µs | Heap before→after bytes |
|---|---|---:|---:|---:|---|
|shared1000|before|106.461|78.125|65/229.3|29513952→17144032|
|shared1000|after|82.264|93.75|64.3/157.8|33708256→26581216|
|shared5000|before|241.129|187.5|27/79.2|20289760→13894736|
|shared5000|after|502.745|375|36.9/145.3|29726944→14944792|
|varied1000|before|41.958|46.875|23/68.8|27526224→8652984|
|varied1000|after|61.697|31.25|24.7/150|31722008→18091568|
|varied5000|before|125.110|109.375|19.7/31.2|10750136→26588896|
|varied5000|after|124.525|125|17.3/27.3|20188720→23446960|

Focused085 covers threshold, repeat/recreate/shuffle, duplicate NPC geography, changed-source identity, excluded current→next, rootID/revision+1, saved suitable goal, wrong level+3/no spawn/no route/nonviable target, low/dead combat, excessive duration/poor reward, one candidate, lower-10 fallback, unknown85, corrupt goal,8-page ceiling, live2km/local-first, operation cap32/release/idempotent close. Extra-leg и increased quoted GK fee negative cases отдельно не исполнены: эти два guards **source-reviewed**, не выдаются за native-tested.

После полного compile два отдельных JVM `--identity` дали `a8869e31bf97d2c9b2004640fd90f9da7bc3a326e1bee23a9cfe6a5507e4e7cc`. В отличие от BEFORE, source-bound fixture authority использует реальные combined/canonical hashes самой fixture; состав comparable candidates/уровни/пути/порог не менялся ради GREEN.

## P2 и регрессии

CompetitionRegistry остаётся CAS owner лишь текущей операции node+npc, capacity=min(configuredAmount,32), release закрывает lease; не permanent region assignment. BackgroundService FARM/ADVANCE lease boundary, FarmingService SHARE/MOVE/WAIT/REFUSE/ESCALATE и PvPContext FARMING_ESCALATION/native/social admission неизменны. Pure registry exercise не доказывает concurrent native spot war.

Final console gates: background-model10/10; farming-resource-policy2/2; farming-bilateral1/1; pvp-policy2/2. `PhantomFarm074Checks` —3 controls PASS(local fallback/exclusions/disconnected/admission/revision). `PhantomNormalGatekeeperPureTest` — PASS(provenance/castle negatives). Это15 existing launcher tests плюс named standalone controls и085, без native conflict/gang/PvP claims.

## Команды, ошибки и compile budget

`M=<actual-root>/L2J_Mobius_CT_2.6_HighFive`; `S=%TEMP%/phantom085-4298219c76c843d1a2ea7b00b3b4923f`; `C=S/source`. Все logs/build/inventory — в новом собственном S, вне checkout. Обычный Copy-Item копировал `build.xml,java,test,tools,dist/game,dist/libs` в C. Старые runtime и JAR не использованы для GREEN.

1. Focused RED compile: `javac -encoding UTF-8 -source 25 -target 25 -cp "<root>/build/bin;M/dist/libs/*" -d S/red <Planner.java> <PhantomSpotDiversity085Checks.java> <PhantomAssertions.java>`. Baseline planner каждый раз компилировался из прочитанного source; прежний build/bin использовался только как dependency classpath для RED. Первые два fixture запуска exit1: world-bound transit point, затем shared farming anchor без polygon evidence. Исправлены только fixture geometry/evidence; это не product RED. После этого `java -Xms64m -Xmx256m -cp "S/red;<root>/build/bin;M/dist/libs/*" org.l2jmobius.gameserver.phantoms.knowledge.PhantomSpotDiversity085Checks --baseline` —exit0; без `--baseline` —exit1 на085-a top1=100% при1000. RED assertion сохранён.
2. `python M/test/spot_diversity085_source_audit.py M S/source-audit` —exit0;108 rows=9levels×4ingress×3tiers. Предварительный output source-before имел3ingress; после сверки canonical human-fighter ID полный output имеет4. Production input hashes совпадают.
3. Один narrow GREEN javac тех же3 source paths в S/green-focused; runner —exit0. После этого test-only authority hash assertion уточнён; окончательные proofs ниже используют полный clean compile.
4. `ant -f C/build.xml -Dbuild=S/build compile-tests`: первый запуск exit1 **до javac** на OwnerConfig CONFLICT. Прямой штатный `java C/java/org/l2jmobius/gameserver/config/OwnerProfileVerifier.java C/dist/game` выявил AccessDeniedException sandbox к temp. Тот же неизменный Ant с разрешённым доступом: **exit0/46s**,2365 production+396 test sources, OwnerConfig Apply68files/14overrides/changed0,38 owner rules/XML_XSD PASS. Одна полная production/test compilation; guards не отключались. Пять existing warnings:3 ExtendedOpenOption,2 runFinalization.
5. `CP=S/build/bin;S/build/phantom-test/bin;S/build/phantom-test/resources;C/dist/libs/*`. `java -Xms64m -Xmx256m -cp CP org.l2jmobius.gameserver.phantoms.knowledge.PhantomSpotDiversity085Checks` —exit0, final table выше; `--identity` в двух новых JVM —оба exit0/equal; `java -Xmx256m -cp CP org.l2jmobius.gameserver.phantoms.knowledge.PhantomFarm074Checks` —exit0.
6. Из C/dist/game: `java -Xmx256m -Dphantom.module.root=C -Dphantom.test.reports=S/regression-reports -cp CP org.l2jmobius.tests.phantoms.PhantomTestLauncher <mode> <seed>` последовательно: background-model15001501; farming-resource-policy24002401; farming-bilateral24002401; pvp-policy25002501 —все exit0. Первый sandbox background-model attempt exit3 до suite на AccessDeniedException в C/.phantom-local; разрешённый повтор использовал тот же compiled code/guards. Затем `java -Xmx256m -cp CP org.l2jmobius.tests.phantoms.PhantomNormalGatekeeperPureTest` —exit0.
7. UTF-8 strict decode, полный mojibake set, шесть escaped Cyrillic regex, package manifest10/10, protected283 SHA256, input source hashes и live/snapshot Java bytes проверяются независимо; exact4-file diff/scope guard —exit0. Подробности проверок находятся в S; ничего из scratch не staged.

Начальный sandbox live `git ls-remote` exit128; разрешённый повтор exit0 с исходным SHA. Поиск ошибочно предположенного GeneratedHistoricalRouteTest.java и HumanTerritory/TalkingIsland.xml дал missing path; использованы найденный GeneratedHistoricalRouteSuite и фактические source XML, production changes для этого не делались. Git inventory предупредил о недоступных прежних private dirs073–078; доступ к ним не обходился, runtime/DB не запускались. Автоматических approval rejection не было.

## Exact allowlist, сохранность и публикация

Всего4 файла относительно M:

1. `java/org/l2jmobius/gameserver/phantoms/background/PhantomHistoricalBackgroundPlanner.java` — единственное product изменение, global dedup/comparable weighted geography.
2. `test/java/org/l2jmobius/gameserver/phantoms/knowledge/PhantomSpotDiversity085Checks.java` — standalone immutable DB-free regression/measurement; launcher/build.xml не менялись.
3. `test/spot_diversity085_source_audit.py` — read-only source inspector с external output.
4. `docs/phantoms/reports/PHANTOM-SPOT-DIVERSITY-085.md` — этот отчёт.

Planner BEFORE SHA256 `D781278F1778917B5030B3995B1A8D3FAAF7F39713A33F1D0C02A0839871076B`; AFTER `DF673897317041359B6F9E4620A71589A37844F7EAC3E881E70BFCA2EBF3DFC4`. Protected283 originals включают config/OwnerConfig, все phantoms data, build.xml, OwnerConfig.ps1, оба merchant084 owners. Source hash inventory покрывает370 XML из таблицы. Tracked scope guard защищает остальные source families; Studio/semantic/current status/old reports не редактируются. Никаких writes в GeoData, Playable078, old DB/release, MAIN/PLAY/client, существующие worktrees; собственная temp-copy не Git worktree. Исходный untracked inventory292105 доступных entries сохранён; в чужие пути не записывалось.

- mojibake-маркеры в изменённых файлах проверены отдельным полным набором27 markers.
- escaped Cyrillic в изменённых файлах проверены отдельно всеми6 regex, включая XML x/X.
- UTF-8 strict decode изменённых4 files — отдельная проверка.

Git прямо разрешён TASK085/DELIVERY и запросом владельца для baseline/scope/публикации. Выполнены `git rev-parse --show-toplevel`; `git symbolic-ref --short HEAD`; `git rev-parse HEAD '@{upstream}'`; `git status --porcelain=v1 --untracked-files=all` с output в S/status-before.txt; `git status --porcelain=v1 --untracked-files=no`; `git for-each-ref --format='%(refname) %(objectname) %(upstream:short)' refs/heads/feature/phantom-world refs/remotes/origin/feature/phantom-world`; `git ls-remote origin refs/heads/feature/phantom-world`; `git remote get-url origin`; exact-path `git diff --stat -- <Planner>` и reviewer `git diff -- <exact review paths>`.

Заключительная разрешённая последовательность: `git diff --check -- <exact4>`; `git diff -- <exact Planner>`; `git add -- <exact4>`; `git diff --cached --check -- <exact4>`; `git diff --cached --stat -- <exact4>`; `git diff --cached --name-only`; один `git commit -m "phantom(task-085): diversify lawful farm spot selection"`; `git push origin HEAD:refs/heads/feature/phantom-world` без force; `git rev-parse HEAD '@{upstream}'`; `git ls-remote origin refs/heads/feature/phantom-world`; tracked status и повторный untracked inventory. Результат и SHA после исполнения публикуются в финальном ответе; self-referential commit SHA в этот отчёт не добавляется. Reset/clean/stash/rebase/amend/branch/worktree/force отсутствуют.

## NEXT_SCOPE — STOP

P0 native corpus и actual same-state production histogram требуют отдельного безопасного DB-free loader пути либо явной новой DB/native задачи; нельзя выдать source hints за eligibility. Настоящий multi-region travel/paid GK/farm arrival, long-lived occupancy,5000 durable/cap128+ performance, native spot conflict/PvP/PK, merchant300/500, owner client QA остаются NOT_RUN. Extra-leg и increased GK-fee negatives можно добавить в bounded fixture отдельно; текущие guards source-reviewed. 086/R2/semantic×4/Studio/новый release/long soak не начинаются. **STOP после scoped публикации085.**
