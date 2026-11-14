# Отказы и пределы011

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
