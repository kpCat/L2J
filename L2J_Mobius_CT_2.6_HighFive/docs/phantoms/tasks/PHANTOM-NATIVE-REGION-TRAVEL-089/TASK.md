# PHANTOM-NATIVE-REGION-TRAVEL-089 — реальное межрегиональное путешествие

**Тип:** новая крупная production feature/closure (не R2 task087). **Приоритет:** P0, blocker живого мира. **Язык:** русский. **Дата подготовки:** 25.10.2026.

## 0. Обязательные входы и границы

- Git root владельца: `D:\Codex\repos\L2J-phantom-world`; модуль `L2J_Mobius_CT_2.6_HighFive`; **только** `feature/phantom-world`; GitHub `https://github.com/kpCat/L2J`.
- Verified remote при подготовке пакета: `48a5c7f1f1b14cd0b09c4a461e4da7e4b4549560` (088). Это **не** разрешение игнорировать более свежий live HEAD. Проверь actual local/upstream/live и intervening diff. Любой конфликт по затрагиваемой навигации/данным требует пересмотра плана до записи; не делать reset/force-подгонку.
- `TOTAL087=PARTIAL`: `GRAPH_SOURCE_MISSING / PHYSICAL_SEARCH_LIMIT`, product fixes 0/2; 3 native qualified Player подготовлены, но 0 successful GK actions/debits/arrivals/attack/rewards и нет persisted paid journey. Полный источник: `docs/phantoms/reports/PHANTOM-UNIVERSAL-REGION-TRAVEL-087.md`.
- `TOTAL088=SUCCESS_CONFIG_INTEGRATION`. Владелец сохранил 19 public config и OwnerConfig; **не менять** Java/config/Player.ini/Rates.ini/Custom/OwnerConfig settings под задачи089, не нарушать `OWNER_GAME_CONFIG_CONTRACT_2026-10-25.md`.
- **Продукт:** настоящий `Player` уходит из нормальной стартовой зоны, самостоятельно выбирает достижимую дальнюю охоту, **идёт**, оплачивает штатный Gatekeeper, доходит, сражается, получает native reward, а после обычного sameDB restart продолжает без повторной оплаты. Никаких GM/teleport/setXYZ/generated Adena/заранее пройденных routes.

**Подготовка пакета не является разрешением менять пользовательский runtime.** Реальная разработка выполняется только после запуска владельцем этого `/goal`.

## 1. Read-first (выполнять до правок; не читать весь архив тысяч отчётов)

1. `AGENTS.md` в Git root, `L2J_Mobius_CT_2.6_HighFive/Agents.md` (**исторический C:-путь и утверждение об отсутствии GeoData в нём устарели**); любые более близкие применимые инструкции.
2. `PHANTOM_DEVELOPMENT_MASTER_PLAN.md` (релевантная навигация); `docs/phantoms/CODEX_WORKFLOW_CONTRACT.md`, `TASK_PACKAGE_STANDARD.md` (исторические пути заменить актуальным), `PHANTOM_CURRENT_STATUS.md`, `RELEASE_CONTRACT.md`, `OWNER_FINAL_RELEASE_TARGETS.md`, `OWNER_GAME_CONFIG_CONTRACT_2026-10-25.md`.
3. Все файлы **этого** пакета и SHA256 manifest; отчёт087 и в нём first-mile/negative/restart evidence; при необходимости конкретный отчёт086 и 074.
4. Exact source owners из `SOURCE_MAP.md`, включая `PhantomNormalGatekeeperTravel`, `PhantomVisibleFarmTravel`, `PhantomHistoricalBackgroundPlanner`, `L2jNavigationBackend`, `PhantomWalkCorridorPlanner`, `PhantomNativeTravelActions`, `PhantomNavigationService`, `PhantomBackgroundService`, native TeleporterData/SpawnData/GeoEngine/Player API. Прочитать действительные методы, не проектировать API по названиям.
5. Сначала исследовать current topology/generated xml, `dist/game/data/phantoms/travel/high-five-normal-gk.xml`, штатные Teleporter NPC+spawns, actual producer/loader/provenance. `region_travel087_source_audit.py` — **source-only evidence generator, не producer production XML**. Не переписывать D1 SHA blindly.

Проверить пакет: UTF-8 без BOM, относительные пути, SHA256 каждого файла по `MANIFEST_SHA256.json`. Если task folder существует с другими байтами — STOP, не поверх неё.

## 2. Подтверждённый causal RED и антипаттерны

Отчёт087 (его наблюдения, не предположения):

| Actor/family | Стартовый штатный NORMAL NPC / original spawn | XY к NPC | Existing corridor |
|---|---|---:|---|
| Human (Gladiator40) | 30006, `data/spawns/Others/17_25.xml` | 18705 | SEARCH_LIMIT; 128 nodes, 512 edges, 32 native queries |
| Elf (Moonlight Sentinel76) | 30146, `data/spawns/Others/21_19.xml` | 10298 | SEARCH_LIMIT; те же hard caps |
| Orc (Dominator76) | 30576, `data/spawns/Others/18_14.xml` | 11508 | SEARCH_LIMIT; те же hard caps |
| Giran control | 30080, `data/spawns/Others/22_22.xml` | 1496 | `VALID_DIRECT` |

- Каталог содержит другие NORMAL gatekeepers (30177/30256/31964/30848/30540/31275/30080), но этих трёх **нет как certified departures**. У native holders доступны destinations/fees (у 087 первый leg0 Human 0A по native policy, Elf 9200A, Orc 23000A), но нет authorized entry graph/walk.
- `PhantomHistoricalBackgroundPlanner.candidate()` немедленно требует `_authority.travelQuery(_topology).route(currentAnchorId, anchorId)`. Отсутствующий путь отрезает глобальный фарм при сотнях viable native mobs. Это **не** проблема силы персонажа.
- `PhantomVisibleFarmTravel.proposedStep()/resumeStep()` принимают itinerary от topology, затем `walk()` / `executePlan()` требуют реального native прохода и `Npc.canInteract(player)` перед `PhantomNativeTravelActions.gatekeeper()`.
- `L2jNavigationBackend.planTravel()` enumerates только direct local или семь близких кандидатов single-GK; ограничение proximity фильтрует многие реальные interregional chains. `PhantomNormalGatekeeperTravel.route()` умеет multi-step BFS по background+GK, но **graph connectivity ≠ физическая достижимость**.
- `PhantomWalkCorridorPlanner`: 128 nodes/512 edges/32 queries/4096 labels, 1024 stride, 3000 local span. `PhantomNavigationPolicy.productionDefaults`: 12 000 maxLocalStraightDistance, 64 waypoints, 100 000 maxRouteDistance, 1s deadline. Статус `SEARCH_LIMIT` не доказывает blocked terrain. Отчёт087 сохранил Geo203 и failure без изменения budgets.
- В 087 Orc `AutoSkills1090` потерян между JVM в fixture. Это ограничение теста, а не доказательство дефекта production career policy; не «чинить» кейс повторной бесплатной выдачей и не объявлять магическую continuity GREEN.

**НЕЛЬЗЯ** решить задачу жёстким list-of-three coordinates, произвольным добавлением `BACKGROUND` edge, giant native buffers/caps/timeouts, отключением сегментной native проверки, скриптовым телепортом, `Npc.onBypassFeedback` вдали от NPC, созданием нового Phantom engine или самодельного independent persistence/world graph.

## 3. Работы (связанный production scope, последовательность)

### A. Authority/graph: первая миля и межрегиональные стыковки

A1. В одной короткой root-cause таблице покажи production chain `source NPC/spawn + stock NORMAL TeleporterData → certified catalog/topology anchor → planner currentAnchor/targetAnchor → visible physical owner`. Для H/E/O зафиксируй precise divergence (нет anchor? source connector? fee? физический ingress? неправильный currentAnchor?); отличай missing source/graph от pathfinder limits. Фиксировать именно причины, а не просто результаты 087.

A2. Добавь **обобщаемый** способ использовать доступный штатный NORMAL Gatekeeper и соединять его source с текущим местом/топологическим anchor посредством доказуемого физического маршрута. Должны использоваться реальные source NPC, native teleporter IDs/list indices, координаты и fees, provenance из spawns/teleporters. Не подменять stock `TeleportHolder` и `TeleportLocation`. Стартовые NPC 30006/30146/30576 — обязательные регрессионные fixtures, **не единственные допустимые NPC**.

A3. Используй существующие `PhantomNormalGatekeeperTravel`/topology. Если потребуется расширить catalog/XML generator, выясни фактического producer, обеспечь воспроизводимую native/source-driven генерацию, неизменяемое source attestation и реальную валидацию `matchesNative()` + topology IDs; не подставляй вымышленные `sourceRefs`/sha. При изменении catalog schema/provenance нужны совместимые reader + focused tests, а не просто замена embedded SHA. Нельзя открыть произвольный malformed/unknown GK leg. Обоснуй любой рост существующего MAX_LEGS и память/CPU.

A4. Поддержи несколько настоящих GK-переходов и WALK-подходы между их source/destination там, где требуются. Route candidate должен быть причинным: фактические available legs, native castle/siege/karma restrictions, текущая достаточность Adena, реальная оценка расстояния/времени/цен. Не превращай расстояние в произвольное жесткое отсечение **при наличии достижимого поэтапного пути**. Если после анализа выявлен другой canonical path, можно использовать его, но с обоснованием и без второго graph owner.

### B. Physical navigation: конечные сегменты вместо раздувания бюджета

B1. Для длинного first-mile не требуй доказать 10–19 km одним search вызовом. Разработай bounded hierarchical corridor/checkpoint approach на существующем `PhantomWalkCorridorPlanner` + `L2jNavigationBackend` + `PhantomNavigationService`: physical progress через серию **локально проверенных** native corridors, где `PathFinding/GeoEngine` подтверждает каждый выпускаемый `MOVE_TO` сегмент.

B2. Сохрани ограниченные CPU/queries/nodes/waypoints/deadline **на один локальный поиск**. Можно добавлять конечные гео-подтверждённые route checkpoints/отдельные worker episodes с bounded reuse/cache, cancellation, cost accounting и backpressure; нельзя циклом тысячи раз синхронно повторять native путь или бездумно повышать `SEARCH_LIMIT`. Никаких очередей/таймеров per-phantom. Каждый отказ различать `SEARCH_LIMIT`/`DEADLINE`/`NO_PROVEN_CORRIDOR`/blocked native segment; bounded retry/hysteresis не маркирует регион unreachable без доказательства.

B3. Кэшировать только доказанные legs с контекстом: topology + Geo epoch, instance, start/surface, obstacles/blocked-edge witness, player/navigator relevant restrictions. При двери, воде, Z-грани, смене epoch, реальной недоступности NPC/смерти/смене authority — проверить, инвалидировать/перепланировать и **не делать прыжок**. Отсутствие клиента/known-list не выдаёт разрешение на teleport.

B4. Уважать текущие native ownership/progress/arrival rules (`PhantomNativeRouteContract.normalize`, `PhantomVisibleFarmTravel.unsafeSegment`, move owner). Обычный `Player` физически проходит маршруты; только уже проверенные locality transitions могут работать в background, но **не засчитываются** вместо native proof этой задачи.

### C. Execution, reward, restart

C1. Свяжи долгий путь с существующим `PhantomHistoricalBackgroundPlanner` и `PhantomVisibleFarmTravel` — реальный избранный farm goal → последовательность HIERARCHICAL WALK → NORMAL GK → WALK → farm, без открепления текущего intent по первому transient `SEARCH_LIMIT` и без «Giran only» policy.

C2. `PhantomNativeTravelActions.gatekeeper()` делает только штатное разрешённое действие при `npc.canInteract=true`. Перед и после — родной Player inventory/Adena, native coords, GK index+fee и source/destination; учесть native бесплатный teleport в низкоуровневых классах. Доказательство **платной** ветки: actual nonzero debit, а не `quote` или начальные деньги теста. В `PAID_WITHOUT_ARRIVAL` нельзя без reconciliation пробовать тот же платный leg повторно.

C3. Используй существующие durable `PhantomBackgroundState` / goals / committed position / checkpoints / native character state. Для недооконченного путешествия фиксируй stable goal/revision, физический checkpoint, текущий leg с фазой intent/action/arrival в минимально необходимом canonical owner. Перед restart/после — восстановление *того же* profile/objectId/goal и native item/wallet/XYZ, без reseed и без повторного списания; если нельзя достоверно определить, списало ли native деньги перед аварией, **fail closed** с explicit recovery state, а не повторять оплату вслепую. Не вводить отдельную конкурирующую «travel economy» или дублирующие payment ledger.

C4. После arrival существующая engagement/farm цепь должна найти NPC подходящего уровня, нанести реальный HP damage, получить настоящие EXP/SP/Adena/item/loot (точный источник/owner по native правилам) и продолжить деятельность. Запрещено выдавать вознаграждение тестовым методом, двигать actor вручную и считать подготовленные fixture items earned.

C5. Сохранить party/store/combat/death/claim locks, materialization owner, native GK policy и protected ownership, без регрессий в farm074, spot085, travel064/033R, QoL088. Не переделывать контентную экономику или саммонеров ради этой задачи.

## 4. Scope: разрешённые файлы и STOP

**Предпочтительные production owners** (менять только реально нужные):

- `java/org/l2jmobius/gameserver/phantoms/navigation/{PhantomWalkCorridorPlanner,L2jNavigationBackend,PhantomNavigationService,PhantomNativeTravelActions,PhantomNativeRouteContract,PhantomTravelStrategy}.java`
- `java/org/l2jmobius/gameserver/phantoms/background/{PhantomNormalGatekeeperTravel,PhantomHistoricalBackgroundPlanner,PhantomVisibleFarmTravel,L2jPhantomBackgroundAuthority,PhantomBackgroundService}.java`
- Внутри `java/org/l2jmobius/gameserver/phantoms/topology/` — только validated loader/query/position owner при доказанной необходимости.
- `dist/game/data/phantoms/travel/high-five-normal-gk.xml`, конкретные existing `dist/game/data/phantoms/topology/high-five-*.xml` — **только** source-driven verified necessary changes; перед stage указать все exact filenames и pourquoi. Stock teleporters/spawns/GeoData **read-only**.
- `test/java/org/l2jmobius/tests/phantoms/` — новые `*089*` focused/native tests и минимальные updates к `PhantomTask087Lane`, `PhantomHeadlessPlayerTestEnvironment` **только если доказана совместимость, лучше отдельный scoped089 test owner**.
- `tools/phantom-runtime/Task089.ps1` и один собственный source audit/test helper `test/*089*` при необходимости, без копипаста 087 × несколько тысяч строк.
- `docs/phantoms/reports/PHANTOM-NATIVE-REGION-TRAVEL-089.md` и **неизменённый** input package `docs/phantoms/tasks/PHANTOM-NATIVE-REGION-TRAVEL-089/*`.

Всё остальное **deny**: source config/OwnerConfig, Studio, Semantic Pack, Client, другие хроники, старые reports/packages, private runtime/DB/credentials, path outside current repo. При root-cause требующем дополнительный файл **в пределах названных подсистем** заранее опиши exact path, causal necessity и safety в отчёте/working notes; иначе STOP и запрос на отдельное разрешение, не произвольное расширение.

**Не менять:** `D:\PhantomWorld\Playable-078-owner`, БД `l2jmobiush5_phantom_release_078`, иные protected data, старая `C:\Users\ZBook\L2J_Mobius`, чужие JVM, 087 schema/evidence. Не создавать новых Git worktrees/веток, не rebase/clean/reset/stash/force/add-dot. Никаких попыток выйти на сеть/сканировать хосты: доступ только GitHub по Git-командам и native `127.0.0.1:3308` в разрешённом lane.

## 5. Runtime authority и проверка

Сначала focused DB-free RED→GREEN на 3 first-miles и минимум 2 multi-leg сценариях из настоящих stock XML/Geo (плюс негативы). Затем **один** production-shadow Ant `compile-tests` (и отдельный `jar` только если genuinely нужен для owned Main), затем одна isolated native test089 campаign + максимум один контролируемый sameDB restart; не запускать `ant verify` (широкая mutable DB) или полные historical suites без необходимости. Можно поправить тестовый harness с отдельным маленьким targeted `javac`, но не пересобирать всё после каждой редакции теста.

**Разрешён только новый test089 lane**, например `l2jmobiush5_phantom_test_089`, если подтверждены фактическое отсутствие схемы, разные owner UUID/paths от всех protected, private host ACL, source-verified copy и output fence. 087 lane — образец read-first, не mutable среда для089. Это разрешение только на `CREATE` новой отсутствующей 089 schema по canonical migration без DROP/TRUNCATE/DELETE и на own-process bootstrap; никаких правок общей MariaDB-службы. Запрещено использовать source Database.ini для подключения к живой release: read-only extraction только в разрешённом own-stage; собственный private config и удостоверенная DB89. Если не удаётся безопасно создать — `BLOCKED_SAFE_NATIVE`, product proof НЕ PASS; не ослаблять DACL.

Защищены ПИДы/сервисы: процесс можно завершать только если это **собственная** JVM089 с сохранённым PID + start time + stage UUID; graceful normal stop, не `taskkill java*`. Перед и после DB connections/final STOP/own resources проверять. Test fixture item/levels/SP permitted **один раз до measurement и только в new089**, receipt before state; не превращать baseline inventory в earned. Native 2 JVM подразумевают restart с фактической persisted state.

`TEST_PLAN.md`, `ACCEPTANCE.md` и `EVIDENCE.md` обязательны. Не переименовывать частичный результат в SUCCESS из-за focused/projection tests. Бюджет времени/ресурсов конечен: использовать один хорошо сконструированный pipeline, а не бесконечные R2 в том же запуске.

## 6. Delivery / Git

1. Подготовить один публичный sanitized report: точная root cause, source change+guards, один benchmark, evidence/negative table, честный statuses, scope, first fail logs, remaining gaps.
2. Стейджить строго **точные** изменённые файлы этого TASK089, включая этот пакет как неизменённые входные документы и один новый report. Список exact paths сформировать до staging. `git diff --check -- <exact paths>`, verify staged diff/UTF-8/mojibake/escaped Cyrillic, outside tracked changes=0. Не использовать `git add .`.
3. Commit `phantom(task-089): close native interregional travel` **только** если implemented/tests действительно подтверждают содержимое; при PARTIAL/BLOCKED subject должен явно отражать границы. Normal `git push origin HEAD:refs/heads/feature/phantom-world` (без force), затем local=upstream=live и tracked clean.
4. При блокере/неустойчивом коде не сохранять broken production: безопасно убрать **только собственные** рабочие изменения и оставить ограниченный аудит/тесты/report, input package. Не трогать чужую работу. Следующую задачу не начинать.

**Не создавать сейчас финальную пользовательскую поставку/JAR в `D:\PhantomWorld`**. Достижение089 не означает готовность финала 5000 profiles/cap128+/200stores.
