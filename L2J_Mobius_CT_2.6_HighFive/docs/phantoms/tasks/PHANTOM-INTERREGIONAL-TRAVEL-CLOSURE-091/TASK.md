# TASK091 — PHANTOM-INTERREGIONAL-TRAVEL-CLOSURE

**Продуктовая цель:** замкнуть настоящую автономную поездку Phantom `Player` от стартовой зоны до далёкого подходящего фарма: физические WALK-сегменты → native NORMAL Gatekeeper с фактическим списанием Adena → прибытие → бой/награда → продолжение после остановки/перезапуска. Универсальный механизм, не демонстрация со сделанным вручную teleport. Продолжение открытой задачи 089, а не смена цели.

Репозиторий `https://github.com/kpCat/L2J`, Git-root `D:\Codex\repos\L2J-phantom-world`, модуль `L2J_Mobius_CT_2.6_HighFive`, ветка `feature/phantom-world`.

Baseline при подготовке **2026-10-25**: `627bca947fd565ce18aa5cc85c7e1aa1288ccaef` (опубликованная 090). Динамически сверить actual local/upstream/live HEAD, branch и дерево до любых правок; baseline НЕ повод reset/checkout/fetch overwrite. При новых чужих изменениях релевантных владельцев — безопасный STOP и report, не решать конфликт самовольно.

## 0. Обязательный read-first до реализации

1. Сверить SHA256 всех файлов этого пакета с `MANIFEST_SHA256.json`. Проверить exact task path и наличие пакета089 в Git. Осмотреть tracked dirt, untracked, current branch, upstream и live GitHub. Сохранять все исходные данные, включая крупные чужие untracked.
2. Прочитать корневой `AGENTS.md`, модульный `Agents.md` (содержит **исторические C:-пути, они НЕ authority**), `PHANTOM_DEVELOPMENT_MASTER_PLAN.md` в модуле, `docs/phantoms/{PHANTOM_CURRENT_STATUS.md,CODEX_WORKFLOW_CONTRACT.md,RELEASE_CONTRACT.md,OWNER_FINAL_RELEASE_TARGETS.md,OWNER_GAME_CONFIG_CONTRACT_2026-10-25.md,TASK_PACKAGE_STANDARD.md}`. Только релевантные части огромной истории, не весь архив целей.
3. Прочитать `docs/phantoms/reports/{PHANTOM-UNIVERSAL-REGION-TRAVEL-087.md,PHANTOM-NATIVE-REGION-TRAVEL-089.md,PHANTOM-QUEST-REWARD-RATES-090.md}`, исходный `docs/phantoms/tasks/PHANTOM-NATIVE-REGION-TRAVEL-089/TASK.md` и документы ТЕКУЩЕГО пакета091.
4. Открыть текущие production-классы и native script/data из `SOURCE_MAP.md`, тесты 087/089 и их ограничения. Никаких имён/методов из памяти без сверки actual HEAD.
5. Отдельно подтвердить, что TASK090: `RateQuestReward=50`, сохранены 272 overrides и конфиг-контракт. TASK091 не трогает Rates, OwnerConfig, Quest, Client, Semantic Studio.

## 1. Подтверждённая исходная поломка (не расследовать её снова с нуля)

- TASK087: `TOTAL087=PARTIAL`, `GRAPH_SOURCE_MISSING/PHYSICAL_SEARCH_LIMIT`, `0` реальных GK debit/arrival/native farm/paid restart.
- TASK089: `TOTAL089=PARTIAL`. Доступный D1 runtime GK catalog содержит **54 legs/7 source NPC**, хотя authoritative D1 facts имеют **256 NORMAL facts/26 source NPC**. Human NPC30006, Elf NPC30146, Orc NPC30576 существуют в native source, но не имеют сертифицированного starter connector в маршруте.
- `PhantomHistoricalBackgroundPlanner.candidate(...)` (~381–390) до выбора дальнего фарма требует `_authority.travelQuery(_topology).route(currentAnchorId,targetAnchorId)`; отсутствие D1 route исключает цель ещё до физической попытки.
- `PhantomVisibleFarmTravel.proposedStep(...)` (~110) привязана к тому же graph route; `advance/executePlan/walk` (~300,492,528) используют volatile Journey/Attempt и прежнюю одно-GK `submitTravelPlan()`. Точная отгрузка goal/arrival в `PhantomBackgroundService` проверяется отдельно, но полный paid itinerary cursor не persistent.
- В 089 добавлены `PhantomWalkCorridorPlanner.planEpisode`, `L2jNavigationBackend.findWalkEpisode`, `PhantomNavigationService.submitWalkEpisode` и fail-closed Geo-Z guards. API **opt-in**, production gameplay caller пока не подключён. В source-only Geo Giran/Human/Orc дошли до локальной цели; Elf застрял на нижнем слое (`~Z=-3900` против GK `Z=-2976`) даже после 57 эпизодов/100+ тыс. единиц поисковых смещений. Это не доказывает физическую непроходимость.
- `L2jNavigationBackend.planTravel()` предлагает max7 candidate single-GK по local straight bounds, а `PhantomNormalGatekeeperTravel.route()` содержит более широкую anchor-графовую структуру. Конечные расходы, движение и перезапуск с оплатой не связаны.
- `PhantomNavigationPolicy.productionDefaults()` содержит `maximumAttemptDurationMillis=120000`; не подменять эту локальную границу бесконечными таймаутами дальнего путешествия. Для многоступенчатого пути требуется продуманный конечный, owner-связанный бюджет и реальный progress.

## 2. Production work — упорядоченные этапы

**A — Native authority и admissible route**

A1. Проследить связку `Knowledge/Spawn/TeleporterData/Town GK → D1 facts/connectors → topology → historical selection → visible action`. Исправить общий production gate, при котором отсутствие *certified background route* исключает реально достижимую foreground цель. Разделить `BACKGROUND_CERTIFIED` и `FOREGROUND_NATIVE_PENDING`: второе разрешает **только физическое планирование**; оно НЕ разрешает виртуальный farm progress, simulated travel, автоматическую запись arrival или выдуманную GK fee.

A2. Перепроверять у действующего `Player` доступность NPC, NORMAL list/index, native XML destinations и текущий `Player` wallet/karma/siege/range; допустим `PhantomNormalGatekeeperTravel.observeNativeLeg()` для bounded transient foreground leg. Не копировать все 256 facts в `_legs` (MAX_LEGS128 и D1 hash), не записывать provisional edges как `TOPOLOGY_BACKGROUND`. При сертифицированном connector использовать прежнюю систему; для NPC вне D1 — только native foreground с подтверждённым source+physical ingress.

A3. Поддержать цепочку 1+ законных NORMAL GK transitions и WALK ingress/egress при реальной необходимости. Каждая стадия получает native identity, цену и последующую проверку arrival. До полного route proof можно сохранять **намерение** следующего шага, но не выдавать его за весь подтверждённый `PhantomTravelStrategy.Candidate`.

**B — Physical first-mile и Elf geometry**

B1. Подключить 089 episodic WALK к действующему `PhantomVisibleFarmTravel` или уже существующему единственному travel owner. Каждая episode выдаёт не более одного **доказанного конечного checkpoint**. Затем обычный `Player.getAI().setIntention(MOVE_TO)`, наблюдение фактической позиции, подтверждение arrival, только после этого следующий episode. Прежние `unsafeSegment`, doors/water/NSWE/height, lifetime epoch/owned scope и progress tracker обязательны.

B2. Исправить системную причину остановки Elf на разных Z уровнях. Учитывать 3D Geo surface, связные переходы, подтверждённые проходы/выходы и допускаемые native PathFinding альтернативы; повторно выбирать осмысленный frontier, а не 57 раз двигаться по одной эвристике к GK. Lateral/backward/иерархические intermediate anchor допустимы, только если каждый локальный сегмент имеет native proof. `SEARCH_LIMIT` = ресурсный отказ, а не утверждение UNREACHABLE. Не проектировать маршруты по одному сохранённому XYZ.

B3. Бюджетировать совокупность episodes, native queries, очереди, прогресс и retries. Не поднимать просто глобальный SEARCH_LIMIT/64 waypoints/2 workers/120s на гигантские величины; если меняется lifetime deadline, выделить конечный документированный journey budget с честным progress. Никаких threads/timers на каждого Player и бесконечной CPU-геометрии.

**C — Native GK debit, farm continuation**

C1. Использовать штатный вызов `PhantomNativeTravelActions.gatekeeper(...)`/`Npc.onBypassFeedback` и штатные проверки. Сохранять фактическую цену/валюту/класс/уровень, снимки wallet-before/after, actual landing. Quote ≠ debit. Free newbie GK **не засчитывается** в два обязательных ненулевых списания.

C2. После перехода обязательно выполнить реальные WALK last-mile и native combat against grounded spawn, зафиксировать actual Monster HP decrease/kill и полученный XP/SP/Adena/предметы. Тестовые исходные уровни, экипировка и кошелёк помечать TEST_FIXTURE; их не выдавать за earned loot. Без искусственных Player.setXYZ/teleToLocation/ручной выдачи наград.

**D — Durable exact journey + paid restart**

D1. Сделать однозначный durable travel intent/leg state: profileId, characterObjectId, exact goalId/revision, desired farm anchor, selected source/NPC/list/index, current leg/checkpoint, last confirmed XYZ/instance, status INTENT/ARRIVED (или строгий аналог), wallet/operation identity, update revision и safe ownership. **Не дублировать inventory как второй источник истины**. Восстановление rebind к тому же profile/goal/Player после новой JVM, не к старому volatile epoch.

D2. Предпочтительный owner — существующий canonical `background.state`/transaction. При добавлении optional marker сохранить byte-for-byte roundtrip для marker-free старого format2/compact3 и не потерять его во всех `withState/after`, state copies/transaction projections. Если по реальному коду безопаснее отдельный *optional typed profile component* в существующей `phantom_profile_components` (без новой SQL таблицы), сначала доказать **атомарную** связь с background goal/inventory и единственным owner, обосновать в отчёте; не создавать независимый route engine.

D3. Для оплачиваемого leg: durable INTENT перед side effect → освободить owned action lease → заново проверить Player/goal/epoch/NPC/resources → вызвать native GK ровно один раз → фактически проверить landing/debit → durable ARRIVED checkpoint → продолжить. Не держать `ActionLease` во время owned checkpoint (риск `NATIVE_WORK_SELF_DRAIN`). **Если crash между fee и checkpoint даёт неоднозначный результат — fail closed, без повторного debit**. Допустим отдельный операторский recovery вместо дюпа/скрытого возмещения. После нормального сохранённого ARRIVED restart должен продолжить маршрут без повторной оплаты.

D4. Негативы: double-submit, stale goal/revision, old materialization epoch, retry after paid, insufficient Adena, NPC missing, siege/karma, wrong instance, interrupted move, death/Party/Store, two concurrent actions, captured wallet changed, invalid marker/old snapshot. Нельзя перезаписывать деньги/вещи/завершённые receipts фиктивным состоянием.

**E — Closing native proof**

E1. Сначала focused RED→GREEN для A–D, затем **одна ограниченная isolated native campaign** в новой guarded TEST091. Создать 3 настоящих Player (Human/Elf/Orc; квалифицированные 40/76/80, подбирать точные классы из текущих native fixtures), не подменять геометрию. Giran как независимый control, не заменяющий starter journeys.

E2. Для полного `SERVER_VERIFIED` требуется прохождение **всех H/E/O** по A→B→C→farm с native attack/reward, **как минимум два ненулевых GK debit у двух разных families**, и одно saved interrupted paid journey → controlled sameDB/new JVM → continue без второй оплаты. Несколько разных target regions/переходов, не один подогнанный NPC/spot. В случае достижимой бесплатной ветки этот leg полезен, но не считается paid proof.

E3. Если физическая Geo 203, isolated DB или native pipeline законно недоступны — не рисовать PASS. Зафиксировать точную причинную границу и сохранить совместимый production code, не объявлять `FULL091` закрытым. **Не превращать P5 в бесконечную серию R2/R3**. Не более 3 причинно различных диагностических обходов одного local blocker без архитектурного пересмотра; не создавать вторую кампанию тестов ради повторного форматирования отчётов.

## 3. Scope, запреты и STOP

Разрешены только владельцы существующих phantom `navigation`, `background`, условно `topology`/GK production, `dist/game/data/phantoms/{topology,travel}` при строго доказанной native provenance, вспомогательные targeted tests и `tools/phantom-runtime`/`tools/phantom-world-data` по необходимости. Точные стартовые пути — `SOURCE_MAP.md`. Изменение любого другого package/Npc/Teleport/GeoEngine/native Quest/SQL schema требует отдельного явного решения; не делать широкого refactor.

Запрещены: overwrite config088/090 (Rates.ini=50; 272 OwnerConfig overrides), изменение других хроник/Studio/клиента, старой поставки `D:\PhantomWorld\Playable-078-owner` и её DB `l2jmobiush5_phantom_release_078`, production/main/shadow других задач, `master`, старые C:-checkout/worktrees, скачивание или публикация секретов, новые Git worktrees. Не использовать `git add .`, reset/clean/stash/rebase/force push; exact-path staging only.

Разрешены целевая Java25/Ant compile/jar **только в собственной physical shadow copy**, новая отдельная schema `l2jmobiush5_phantom_test_091` только после проверки отсутствия и права на конкретный namespace; никакого DROP/RESET существующих схем, никаких тестов в production DB. TEST config/логины/credentials хранить вне Git с private ACL. При отсутствии прав или test login — STOP_NATIVE_INPUT без обращения к protected DB. 203 Geo копировать без модификации источника. Не завершать чужие JVM/DB процессы; own PID + timestamps, orderly STOP.

Измерить безопасность данных/производительность на штатном bounded scope. Финальная владельческая поставка/JAR в D:\PhantomWorld не создаётся. Цель закрывается только фактическими receipt, а не изменением статуса документа.

## 4. Результат

Один отчёт `docs/phantoms/reports/PHANTOM-INTERREGIONAL-TRAVEL-CLOSURE-091.md` с матрицей из `ACCEPTANCE.md`, actual native evidence, diff/class list, P0–P7, точной причиной каждого `NOT_RUN`, ссылкой на прежний `TOTAL089=PARTIAL`, собственными limitations, подтверждением 090 config immutable, Git SHA, обычным non-force push и preservation protected/untracked. При полном `SERVER_VERIFIED` не заявлять OWNER_QA_DONE без клиента. При PARTIAL/BLOCKED только безопасный scoped commit. **Следующую задачу не начинать.**
