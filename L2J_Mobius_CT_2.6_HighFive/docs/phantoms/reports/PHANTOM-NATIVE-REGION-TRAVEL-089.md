# PHANTOM-NATIVE-REGION-TRAVEL-089

25.10.2026. **TOTAL089=PARTIAL.** Автономное межрегиональное путешествие не закрыто. Подготовлена ограниченная основа локальных коридоров и защита от недопустимого Geo Z; существующие gameplay callers новый episode API пока не используют. Ноль реальных Player WALK, GK actions/debits, атак, наград и paid restart. Подтверждённый геометрический маршрут не выдан за игровое последствие.

- `IMPLEMENTED=OPT_IN_CORRIDOR_GROUNDWORK_AND_VOID_Z_GUARD`.
- `FOCUSED_VERIFIED=PASS` для перечисленных ниже DB-free проверок.
- `SOURCE_GEO_VERIFIED=3/4`, Elf — FAIL, не UNREACHABLE.
- `NATIVE_VERIFIED=NOT_RUN`, `OWNER_QA_PENDING=REQUIRED`.

| Gate | Итог | Фактическая граница |
|---|---|---|
| P0 authority/safety | PARTIAL; source/shadow subset PASS | HEAD/package/ACL/bytes/inventory/build проверены. DB089, отсутствующая schema и native lane admission не проверялись; schema не создавалась |
| P1 source/graph | PARTIAL, SOURCE_AUDIT_ONLY | Найдены настоящие NORMAL sources и причина D1 graph0; catalog/topology не расширены, две multi-leg альтернативы не реализованы |
| P2 physical reachability | PARTIAL, SOURCE_PROVEN_ONLY | Native Geo + static collisions: Giran/Human/Orc получили цепи; Elf исчерпал cumulative budget. Ни одного issued MOVE_TO настоящего Player |
| P3 payment | NOT_RUN | Нет NPC interaction/action/ненулевого native debit |
| P4 farm | NOT_RUN | Нет post-travel native damage/kill/EXP/SP/Adena/loot |
| P5 restart | NOT_RUN | Нет начатого paid journey и persisted cursor; sameDB JVM restart не запускался |
| P6 regressions | focused PASS; native negatives NOT_RUN | navigation-core51/51, corridor064, NORMAL GK pure, farm074, spot085, focused089; подробная граница ниже |
| P7 efficiency/delivery | PARTIAL | Конечные probes и честные counters; три Ant вместо запланированного одного из-за последующих production corrections. Exact publication receipt сообщается после commit/push |

## Authority и read-first

Фактический Git root — `D:\Codex\repos\L2J-phantom-world`, branch `feature/phantom-world`. Начальные local/upstream/live и повтор после `git fetch origin feature/phantom-world` равны `48a5c7f1f1b14cd0b09c4a461e4da7e4b4549560`. Intervening diff отсутствует. Tracked dirt до работы отсутствовал. Существовавшие `.phantom-local/` и чужие untracked task packages сохраняются.

Все восемь entries `MANIFEST_SHA256.json` совпали по SHA256 и размеру; проверены относительные имена, strict UTF-8, отсутствие BOM. Сам manifest: `481207acc2d20b03cfd94b25e93a313a0fe5d08a9262ec2550649f2ffddcfd83`. Девять input files не редактировались.

Прочитаны root `AGENTS.md`, module `Agents.md`, root `README.md`, релевантные разделы master plan/current status/release contract/owner final targets, workflow contract, task-package standard, config contract088, весь пакет089 и отчёт087, включая first-mile/negative/restart ограничения. Module `README.md` и более близкие AGENTS в java/test/tools не найдены. Карта задачи — найденный `SOURCE_MAP.md`; исторические C:-пути и отсутствие Geo из module instructions не приняты за актуальную среду.

Изучены corridor/backend/navigation service/native route contract/travel strategy/native actions; NormalGatekeeperTravel, HistoricalBackgroundPlanner, VisibleFarmTravel, BackgroundService, authority и state/codec/owned-store contracts; native SpawnTable/SpawnData, TeleporterData/TeleportHolder/Teleporter/Npc APIs. Независимый read-only review дополнительно проверил preservation sites и historical/native admission. Локальные аналоги: corridor064, TravelGeoProbe + HermeticGeoMovement/PathFindingRawAccess, owned087 wrapper, pure GK guards, farm074 composition и spot085 checks. Сохраняются Java25/Ant, canonical native owners, config088 и прежние D1 proof identities.

## Короткая causal таблица

| Family | Native source → catalog/topology → planner → physical owner | Divergence |
|---|---|---|
| Human | NPC30006 NORMAL; `data/spawns/Others/17_25.xml`, −84108/244604/−3728; `population.ingress.human-fighter.01` существует | Source NPC не представлен certified departure в catalog. `candidate()` требует D1 `route()` раньше physical planning. Старый complete search возвращал SEARCH_LIMIT; episode prototype доказал геометрическую цепь24,8км, без Player |
| Elf | NPC30146 NORMAL; `Others/21_19.xml`, 46926/51511/−2976; `population.ingress.elf.01` существует | Такой же отсутствующий certified departure. Локальные поиски остаются преимущественно на нижнем слое около−3900; подтверждённого выхода на слой GK−2976 не получено даже с target-surface candidates |
| Orc | NPC30576 NORMAL; `Others/18_14.xml`, −45264/−112512/−240; `population.ingress.orc.01` существует | Такой же D1 source gap; дополнительно probe обнаружил Geo projection с Z вне World bounds. После fail-closed фильтра и локальных альтернатив получена геометрическая цепь13,7км, без Player |
| Giran control | NPC30080; `Others/22_22.xml`, 83396/147904/−3404; `giran.city.center` | Certified catalog содержит этот NPC; direct Geo control1,5км проходит |

Stock XML NORMAL index0: Human18000A, Elf9200A, Orc23000A до native fee policy. Это данные, не quote текущего Player и не оплата. Human free teleport из087 не переоценён. Actual `Npc.canInteract`089 не вызывался.

Canonical producer найден: `tools/phantom-world-data/normal_gk_catalog.py`. Он join-ит D1 facts только с доказанными source/destination connectors. `region_travel087_source_audit.py` переиспользован только как source exporter, не publisher. Неизменный accepted `GATEKEEPER_FACTS.tsv` содержит256 NORMAL facts/26 source NPC, включая30006/30146/30576; SHA256 `a8318075ef6ea3c3f266aa975ef1565fb8d6c93d2f074f076a059ed5d2ea2fe4`. Неизменный runtime catalog содержит54 legs и7 source NPC; SHA256 `f12ac5c5ea47367bc932e732b202541d76d73c6920aa1b2e4f6a5d2ed1da8f2c`. Следовательно, native destinations существуют, но это не certificate physical ingress. MAX_LEGS/D1 SHA/schema/XML не подменялись.

`planTravel()` по-прежнему содержит ограниченный single-GK selection; itinerary/paid resume этой задачей не исправлены. В `VisibleFarmTravel` plan/stageIndex находятся в памяти; `resumeStep()` использует bounded approach к canonical anchor, а не сохранённый foreground leg. Existing120s Attempt не заменён новым cumulative journey accounting. Эти причины остаются открытыми.

## Что действительно изменено

1. `PhantomWalkCorridorPlanner.planEpisode(...)` возвращает только локально доказанный checkpoint. Не добавляет непроверенный хвост до requested remote goal. Native candidates сначала проверяются с текущей и целевой surface; lateral/backward escape разрешён. Любая смена Z требует полного native segment proof. Максимум64 supplied physical checkpoints используются только для bounded hysteresis. Все alternatives делят128nodes/512edges/32path queries/4096labels и deadline исходного запроса; counters cumulative.
2. `L2jNavigationBackend.findWalkEpisode(...)` переиспользует dry/native oracle. Projection и walkSurface отвергают Z вне native World bounds до создания NavigationPoint. Runtime validation/doors/water checks сохраняются.
3. `PhantomNavigationService.submitWalkEpisode(...)` — opt-in в той же очереди, с теми же workers/cancel/deadline/backpressure. Result заканчивается в доказанном checkpoint, cache отключён. Старые submit/submitWalkCorridor/planTravel contracts сохранены. Автоматического MOVE_TO/GK новый API не выполняет.
4. Два focused/probe test owners и own shadow wrapper с exact byte/inventory fence, воспроизводимым source audit и compile receipt. Receipt связывает source pin, owner и hashes actual classes/resources; source Refresh инвалидирует successful compilation. Guard-only RefreshHarness разрешает ровно два script-файла при неизменности всех прочих inputs и compiled outputs.

Review не обнаружил Important причины удалить эти три безопасных production additions. Они остаются groundwork без gameplay integration. Непроверенные foreground graph/goal/persistence изменения не добавлены.

## RED → GREEN и неуспешные попытки

- Первый targeted javac с implicit source closure не собрался: отсутствующие внешние classpath dependencies. Это harness failure, не behavioral RED. Последующая узкая компиляция явно перечисленных navigation sources использовала прежний `World.class` только для constants; окончательные проверки выполнялись на fresh shadow build.
- Behavioral RED: прежний complete `plan()` на длинном finite-buffer corridor дал SEARCH_LIMIT. Новый opt-in episode отдал доказанный локальный checkpoint; full journey этим не объявлен пройденным.
- Отдельный RED: единственный безопасный локальный выход назад давал NO_ROUTE_WITHIN_BUDGET. После bounded alternatives — GREEN. Отдельный RED для target surface также стал focused GREEN; реальный Elf от этого GREEN не стал.
- Дополнительно проверены cumulative failed-probe cost, wrong-instance request rejection и deadline. Попытки теста вызвать package-private `cancel()` и создать запрещённый cross-instance Request были исправлены согласно существующим API. Отдельный native cancellation089 не заявляется.
- Geo attempt1: Human прошёл source-only prefix6163units/14waypoints; затем probe упал на out-of-bounds Z. Первый log сохранён, runtime connector не опубликован.
- Geo attempt2: Giran/Human/Orc complete, Elf101058units и FAIL. Geo attempt3 с target-surface candidates: опять3/4, Elf100335units и FAIL. Последняя локальная проекция пересекла cumulative diagnostic threshold, после чего adapter остановился; Player movement отсутствует.

Четвёртого Geo исправления/прогона нет. [Systematic-debugging](C:/Users/ZBook/.codex/plugins/cache/openai-curated-remote/superpowers/7.0.1/skills/systematic-debugging/SKILL.md) требует после трёх неудачных исправлений: “STOP and question the architecture”. В этом пакете также задан конечный pipeline без бесконечных R2. Для продолжения требуется review global checkpoint/frontier selection и native-only itinerary/owned checkpoint design; новый native PASS не придуман.

## Изолированная проверка и benchmark

`R=<repo>`, `M=R/L2J_Mobius_CT_2.6_HighFive`, `W=M/tools/phantom-runtime/Task089.ps1`, `S=<private TEMP>/own089`, `C=S/source`. Stage физически вне checkout; host owner/SYSTEM ACL, новый UUID,29007 byte-attested inputs,203 физические Geo tiles. Geo скопирована read-only из прежнего068 Geo source; protected078 не читался. Source Database.ini исключён без чтения credentials. В S нет DB config, JDBC/SQL/native fixtures не запускались. Ни одного нового Git worktree/branch/runtime release.

Первый own PowerShell preflight тратил CPU на повторные ancestry cmdlets и остановлен **до Ant**. Завершён только точный собственный PID44564/startUtc2026-10-25T03:34:33.8413009Z с command/stage identity; receipt сохранён private. Чужие процессы не завершались. Проверка перенесена в bounded Python inventory + SHA; ACL не ослаблялась.

Команды из R, только S outputs:

| Команда | Exit / фактический результат |
|---|---|
| `W -Action Prepare -Stage S`, затем `Refresh` |0; physical copy/backup/recomputed source candidates/pin; SQL0 |
| `W -Action Compile -Stage S` |0 ×3; первый full2365 production/402 test sources, затем необходимые rebuilds после production corrections; без jar |
| Source OwnerConfig Verify → staged Apply → Verify |0; каждый68files/271overrides/conflicts0/changed0 |
| `W -Action Geo -Stage S` |1 ×3; результаты выше, не PASS |
| `W -Action RefreshHarness -Stage S` |0; только два guard scripts, Java/data/libs/classes/resources byte-exact; без Ant/Geo |
| `PhantomTravel089Test` |0;3 focused группы, synthetic only |
| `PhantomWalkCorridor064Test` |0;4 checks |
| `PhantomNormalGatekeeperPureTest` |0; provenance/castle controls |
| `PhantomFarm074Checks` |0;3 focused controls; DB writes0 |
| `PhantomSpotDiversity085Checks` |0; a/b/c/d/e; fixture selections, не5000 native profiles |
| `PhantomTestLauncher navigation-core 20260725001` |0;51/51; existing model queue/cache/cancellation/progress contracts |

Java CP: `S/build/bin;S/build/phantom-test/bin;S/build/phantom-test/resources;<copied vendor jars>`, GameServer/LoginServer/source jars исключены; cwd=`C/dist/game`. FQNs: focused/064/GK/launcher — `org.l2jmobius.tests.phantoms`; farm074/spot085 — `org.l2jmobius.gameserver.phantoms.knowledge`. Launcher properties: `-Dphantom.module.root=C`, `-Dphantom.test.reports=S/focused-reports`; heap256m для focused,4g для Geo.

Один benchmark финального Geo source-only прохода; per-episode nearest-rank percentiles. `queries` — вызовы PathFinding oracle, не все GeoEngine.hasGeo/getHeight/canMove calls:

| Source | Episodes | Path queries total | max nodes/edges/queries/labels | wall total | p50 / p95 |
|---|---:|---:|---|---:|---:|
| Giran |1|0|2/1/0/2|15,546ms|15,546 /15,546ms |
| Human |9|21|9/8/8/9|87,982ms|5,665 /33,160ms |
| Elf, FAIL |57|347|16/15/15/16|2719,056ms|42,743 /100,375ms |
| Orc |6|18|9/8/7/9|117,123ms|4,873 /47,789ms |

Финальный public Geo TSV SHA256: `0ba37cd4f439401ad7c2d0a7b8de4cebe7d5db5f514bd3489d00805250cc634c`. Final compiled-input identity: `c0315721a69137c9c9b675725ebd12ab689eb79456adf8a21dd8600634266e19`; outputs identity: `46452a79d3b665dc02619b98f438514e2c52e1984651ef7c8d2810d55ddf7e7b`. Harness-only update не меняет эти identities; runtime pin учитывает обновлённые guard scripts.

Native geometry использует HermeticGeoMovement/PathFindingRawAccess и static XML collisions. Dynamic door state, WaterZone runtime, ordinary L2jBackend/Player parity, actual movement time, full native requests count, CPU/peak heap/GC и p50/p95 gameplay legs не измерялись. Стоимость загрузки/attestation не смешана с corridor wall. Native insufficient funds/karma/siege/NPC disappearance, paid ambiguity, stale goal/epoch, party/store/death, actual farm reward и restart — NOT_RUN. Полные mutable travel033R/generated/local recovery suites, ant verify, jar/Main/client — NOT_RUN.

Public source SHA256 фактически проверенного checkout:

| Файл | SHA256 |
|---|---|
| PhantomWalkCorridorPlanner.java | `4aea230e4f49cfad4aff080dea7e1e5aa66a7f0e9ad0c35b7329b3036ab980c4` |
| L2jNavigationBackend.java | `a36ed8a722eee4ee2d98fbdbee48c4e6ed9c2b92f77cbb8ee2a480424d207c56` |
| PhantomNavigationService.java | `7aae44e107657b4b71d2c9e171cca2feeb34ced60c25386c8129cf9e9a8acb14` |
| PhantomTravel089Test.java | `1cdc9be9729c4ddf0c7db2aa3449cb443be33841c46e41dd2c6605525c7b568f` |
| PhantomTravel089Geo.java | `c35da255e389e548bf17a1cdafacd50aaffd53c757ad6ea4aa7e3ba2fa3f9a24` |
| region_travel089.py | `7a5089281db71f4b4a9253e50714a1ab2997443aad0bd71cf088770392183852` |
| Task089.ps1 | `fda8762aaef9f9d590e0495b1cc3a47e641549b08522f26a9e77a8a28eb13511` |

Guard negatives выполнены отдельно: подменённый compile inputPin отвергнут как `TASK089_COMPILE_PIN_MISMATCH`; добавленный неизвестный `.java` — `TASK089_COPY_INVENTORY`; header-only/tampered source TSV — `TASK089_PIN_DRIFT`. Каждый отказ произошёл до запуска Java/SQL; original receipts/candidates восстановлены побайтово с SHA256, единственный own probe file удалён. Эти negatives не выдаются за native gameplay guards.

## Открытый integration review

Нельзя просто перенести256 facts в D1 `_legs`: это сделает их BACKGROUND edges, превысит Options128 и изменит whole-file travel authority hash. Tentative endpoints также нельзя выдавать за `PhantomTravelStrategy.Candidate`: его WALK stages требуют полного proof. Нужен явно foreground-only source selection в существующем owner и native-only goal guard до historical suitability/replan/projection.

Paid resume требует typed marker в canonical `background.state`, с сохранением marker-free format2/compact3 bytes и preservation во всех state copies. Receipt.operationKey/Clock не заменяют cursor. ActionLease нельзя удерживать при owned checkpoint: возникает NATIVE_WORK_SELF_DRAIN. Требуется existing split `release action → durable INTENT checkpoint/RESUME → reacquire/recheck → native action → release → ARRIVED checkpoint → advance`. Неопределённый INTENT должен fail closed без повторной оплаты. Эти изменения здесь не реализованы и не проверены.

## Exact scope и публикация

Bounded exception к обычному лимиту8–10 files: ровно17 paths — семь связанных navigation/test/tool files, один report и девять неизменённых owner input documents. Никаких независимых housekeeping/UI/config/data changes.

Все пути ниже относительны M:

1. `java/org/l2jmobius/gameserver/phantoms/navigation/PhantomWalkCorridorPlanner.java`
2. `java/org/l2jmobius/gameserver/phantoms/navigation/L2jNavigationBackend.java`
3. `java/org/l2jmobius/gameserver/phantoms/navigation/PhantomNavigationService.java`
4. `test/java/org/l2jmobius/tests/phantoms/PhantomTravel089Test.java`
5. `test/java/org/l2jmobius/tests/phantoms/PhantomTravel089Geo.java`
6. `test/region_travel089.py`
7. `tools/phantom-runtime/Task089.ps1`
8. `docs/phantoms/reports/PHANTOM-NATIVE-REGION-TRAVEL-089.md`
9. `docs/phantoms/tasks/PHANTOM-NATIVE-REGION-TRAVEL-089/GOAL.txt`
10. `docs/phantoms/tasks/PHANTOM-NATIVE-REGION-TRAVEL-089/TASK.md`
11. `docs/phantoms/tasks/PHANTOM-NATIVE-REGION-TRAVEL-089/SOURCE_MAP.md`
12. `docs/phantoms/tasks/PHANTOM-NATIVE-REGION-TRAVEL-089/PRODUCT_DECISIONS.md`
13. `docs/phantoms/tasks/PHANTOM-NATIVE-REGION-TRAVEL-089/ACCEPTANCE.md`
14. `docs/phantoms/tasks/PHANTOM-NATIVE-REGION-TRAVEL-089/TEST_PLAN.md`
15. `docs/phantoms/tasks/PHANTOM-NATIVE-REGION-TRAVEL-089/EVIDENCE.md`
16. `docs/phantoms/tasks/PHANTOM-NATIVE-REGION-TRAVEL-089/DELIVERY.md`
17. `docs/phantoms/tasks/PHANTOM-NATIVE-REGION-TRAVEL-089/MANIFEST_SHA256.json`

Git разрешён текущим TASK089 и явным /goal. Использованы `git rev-parse --show-toplevel`, `git symbolic-ref --short HEAD`, `git rev-parse HEAD`, `git rev-parse '@{upstream}'`, `git rev-parse --abbrev-ref --symbolic-full-name '@{upstream}'`, `git status --short`, `git status --porcelain=v1 --untracked-files=no`, `git worktree list`, `git ls-remote origin refs/heads/feature/phantom-world`, `git fetch origin feature/phantom-world`; review — только `git diff --` по точным шести, затем семи own paths. Первый sandbox ls-remote отказал по сети; разрешённый host retry подтвердил HEAD.

Для delivery `A` означает только перечисленные17 paths с module prefix: `git diff --check -- A`, `git diff -- A`, `git add -- A`, `git diff --cached --check -- A`, `git diff --cached --stat -- A`, `git diff --cached --name-only -- A`, `git diff --cached -- A`; scope по tracked status. Дополнительно `git diff --cached --raw --no-abbrev --` по девяти exact input paths: все9 index blob SHA1 совпали с Git blob hash исходных bytes; пакет не нормализован при staging. Commit subject: `phantom(task-089): add opt-in corridors with open native gates`. Публикация: `git push origin HEAD:refs/heads/feature/phantom-world`, затем повтор local/upstream/live и tracked-clean. Commit SHA и фактический результат push сообщаются в финальном ответе и private publication receipt: собственный SHA нельзя включить в создающий его же commit.

Private stage и первые fail logs сохранены для review. SQL/JDBC0, DB089 не создавалась; final database connections check NOT_RUN. Последняя read-only проверка Java/Javaw дала0 процессов; own JVM0. Protected078/чужие БД/JVM/worktrees/config088, старые087 evidence, Semantic Pack, Studio и клиент не изменялись; пользовательская поставка/JAR не создавались. Protected078 hashes не проверялись обходом её ACL.

Положительный `Focused` после всех negative restores прошёл через новый compile receipt guard и выполнил три089 группы, exit0. Финальный read-only review guard delta не оставил Important findings. Pre-stage scope guard: exact allowlist17, outside tracked0; package SHA/strict UTF-8/noBOM совпали. Первая encoding shell-команда остановилась на parser error до проверок; повтор с ASCII-передачей технических маркеров выполнил оба полных прохода.

- mojibake-маркеры в изменённых файлах проверены:17files,27маркеров,0совпадений.
- escaped Cyrillic в изменённых файлах проверены:17files,6regex-паттернов,0совпадений.

Staged bytes/diff и publication receipts проверяются после подготовки отчёта; фактический commit/push сообщаются в финальном ответе. Следующую задачу не запускать. Полный089 остаётся непринятым.
