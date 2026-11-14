# Текущие отказы и пределы011

После явно согласованных local contract и Spawn sentinel scopes исходный PRE_CUT coverage blocker устранён. Ниже сохранены первоначальные отказы010/011; они не пересчитаны задним числом.

| Проверенная причина | RED /наблюдение | Исправление /предел |
|---|---|---|
|Global territory envelope применялся к локальному бою|Canonical SilentValley source не проходил exactTerritoryNode; behavioral local RED|Отдельный точный canonical source→local encounter validator; все прежние global bytes сохранены|
|Local roster попадал в global background planner|Session4e4c348…:2Solo+2Party proposals, exactAnchor отказ до CUT|Local Party intent из проверенного witness, обычный native owner отдельно; global planner не менялся|
|Native Spawn терял heading-1|Session7948c3…:target174837,56728,-5936→174868,56860,-5928; actual setHeading(-1)=0|Разрешённый override только Spawn; другие значения прежний clamp;9×2unit и8native lifecycle PASS|
|Catalog seal раньше штатного groupViable|Sessiondcbd237…:6pre-CUT arms PASS, Party WAIT_GROUP_DAMAGE|Два canonical native admissions до включения ячейки; только singleton WAIT_GROUP_DAMAGE retryable, cap12|
|Global ordinal смешивал направление и порядок|Behavioral DIRECTION-ORDER-RED|AB/BA отдельно внутри каждого направления, actual order и pair receipt совпадают|
|Config-only prepared path конфликтовал после rebuild|Behavioral PREPARED-CACHE-RED-BEHAVIOR|ConfigSHA/JAR_SHA path с прежним полным pin;8старых файлов SHA unchanged|
|Законный Solo admission не гарантирует победу|GS1:3NATIVE_ACTOR_DIED и1NATIVE_KILL в4Solo arms|Никакой нормализации результата;0completed Solo pairs, G4FAIL|

Последний отказ Director — `TRAIN010_EVAL_MATCHED_PAIR_COVERAGE`, Training.ps1:706. GameServer exit0 и наличие evaluation.complete не означают required coverage.2Party completed pairs не заменяют2Solo. Verdict PARTIAL011_NATIVE_SOLO_COMPLETION_UNMET; FULL не объявляется. GS2/3 не запускались;1/3 и328635/2700000ms.

Supplemental native-admission raw sessions содержит Java Map text, поэтому этот body не valid JSON. Сохранён verbatim с явным ограничением; versioned encounter-admission, admission facts, CUT, physical parity и settlement JSON доступны отдельно. Это не скрытая игровая победа и не основание повторять уже reserved arms.

В3linked-lethal events HP callback завершился через3,6754–10,4897ms после terminal; тот же target incarnation имеет canonical kill. Все47проходят существующий timing rule. Полных public raw witnesses8; остальные47IDs/HP/MP/actions/timings/hashes доступны в index, исходные private receipts сохранены.

Операционные ошибки continuation сохранены отдельно: первая integration compile исправила namespace/variable typo; PREPARED-CACHE-RED.log был compile-only и не засчитывается behavioral RED; behavioral cache refusal записан отдельно. Сборщик внешнего evidence сначала искал resources.json вместо существующего native-resources.json; исправлен только внешний reader, никакой игры/SQL повторно не запускалось. Доступ к private TEST и CIM потребовал штатного sandbox escalation; auto-review rejection не было.

# Исторические отказы011 до продолжения

## Наследованные факты010, сохранённые без переоценки

| Факт010 | Причина / вывод011 |
|---|---|
|GS3: episode180s → invalid CombatRequest после CUT|Request constructor не покрывался полным preflight; реальные Solo≤120000 и Party60000 остаются неизменными|
|GS4: MATCHED_PHYSICAL_FACTS_DIFFER|Canonical EnchantItemHPBonusData/active config fix010 опубликован, native PASS после него не доказан|
|GS4: NPC_FACTION_NOT_ISOLATED|Snapshot/forecast не равен текущему World; нельзя гарантировать геометрию только большим offsets page|
|Последний010:0cells,84target refusals,1656isolation|Оставшиеся target76 несовместимы с двумя acquired DAMAGE альтернативами независимых94/110; lawful73–74 placements не доказаны|

История010:4starts/715447ms;009:10starts/5803240ms, старый exact-exit предел009 не переписан.

## Наблюдённые RED011

- `RED-DISPATCH.log`: Ant compile-tests exit1, отсутствующий новый gate type; это compile RED, не runtime proof.
- `RED-PROTOCOL.log`: javac exit1, отсутствующий новый shared protocol type; также compile RED.
- `RED-LIFECYCLE.log`: реальные assertions retained worker/actor lease при rejected delegate и started-worker/STOP race. Последние fixes дали4 focused checks GREEN.
- `GREEN-TESTS.log`: историческое имя первого suite run; внутри фактический exit1/51s на started-worker cleanup. Этот файл не является GREEN receipt.
- `RED-REASONS.log`: runtime source-contract assertion на потерянный `LEARNING001_AUTONOMOUS_*` reason. Узкая нормализация сохраняет код и не раскрывает arbitrary vendor/JDBC text.
- `RED-CATALOG.log`: compile RED новых risk/freshness regressions; затем `GREEN-REVIEW*.log` и final Ant source-contract exit0.

Независимый review обнаружил stale seal перед CUT, потерянный risk pin между разными headless/live directories и потерю safe reason. Все три исправлены в разрешённом fixture scope. Native freshness/readiness/live parity остаются NOT_RUN при отсутствии обязательных сцен; unit проверка не заменяет их.

## Фактический fresh headless011

Session `fc019d570f72417a8ae2282ee5a9e66a`, worker86644, kernel exit `2026-11-14T17:51:10.8036561Z`, exit2. Wrapper Prepare exit1 с safe WORKER_FAILED. Внутренний код: `AUTONOMOUS_BLOCKED_EVAL_NO_LAWFUL_SCENE`, `requireEvaluationCoverage`. Есть4 preliminary PARTY_HEAL, SOLO_DAMAGE0; paired passive admission не начался. Полные counters и различение стадий — SCENARIO_COVERAGE.

Worker wall98718ms, CPU205.09375s, peak working set4644433920B. Это headless стоимость, не расход GS-native бюджета. Отдельный GameServer не запускался.

Основные no-solution категории: SOURCE_SKILLS/MAGIC_LEVEL для оставшихся NPC76; FACTION/TARGET_PLACEMENT/GEO_LOS и ANCHOR_AGGRO для подходящих73–74; SOURCE_TOPOLOGY/TERRITORY_ENVELOPE для других canonical passive non-faction sources. Полный Party owner admission/risk не доказан и не превращён в PASS. REQUEST_TIMEOUT теперь проверяется через canonical shapes и focused boundary tests, без изменения gameplay bounds.

## Операционные отклонения без product mutation

Первый inline encoding scan parse-failed из-за кодировки shell payload до исполнения; повтор выполнен небольшим UTF-8 Python script с полными27/6 наборами. `git diff --check` обнаружил7 CRLF-only добавленных строк Ant target; изменены только эти EOL, остальной большой build.xml не нормализован. Затем exact diff check exit0. Никакого обхода approval/ACL, force/reset/clean или runtime rule weakening.

## Стоп

G3 FAIL → G4 NOT_RUN. Native0110/3 и0/2700000ms; новые starts не запрашиваются. FULL011 запрещён. Это PARTIAL011 с final manual gate REQUIRED; следующий Goal автоматически не начинается.
