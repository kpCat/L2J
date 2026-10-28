# PHANTOM-MERCHANT-WORLD-099

28.10.2026. **PARTIAL099**. Текущий native рынок и sameDB restart: **PASS, 200 → 200**. Полный SUCCESS099 не объявлен: gate D оставляет непроверенные отрицательные free-source/price-drift сценарии. **MFG_DEFERRED / NO_ELIGIBLE_MFG; OWNER_VISUAL_QA_PENDING**.

## Матрица A–G

| Gate | Статус / evidence | Факт |
|---|---|---|
| A | PASS / DB_FREE + OWN099_NATIVE | Feature/ancestor/live проверены; package SHA11; OwnerConfig Verify68/287, changed0; protected source hashes198. Fresh external private shadow, owner+SYSTEM ACL, physical paths, Geo203. TEST099 отсутствовала при полной metadata visibility, foreignSessions0. Create-only, без grants/reset/drop. |
| B | PASS / OWN099_NATIVE | После causal replay START1 и START2: 200 distinct actual World Player, native owners/nonempty/leases200; client=null, online=true, OfflineStartTime>0; 150SELL/50BUY/0MFG. Не число intentions или durable profiles. |
| C | PASS / OWN099_NATIVE | Legal251, occupied200 в трёх source sites; spacing≥60, Peace/instance0, Geo/Z/NPC/door/arrival/observer guards сохранены. Actual38 item IDs, 6 категорий, N–A, source-backed цены. Недостающие категории перечислены ниже. |
| D | PARTIAL / OWN099_NATIVE + DB_FREE | Реальные SELL/BUY, last-stack race, STOP rejection и qty/Adena conservation PASS. Unknown item fail-closed PASS. Actual free-source и price-drift fault injection NOT_RUN; source review и арифметические тесты не заменяют эти проверки. |
| E | PASS / OWN099_NATIVE | SameDB/sameJAR START2 штатно восстановил200; те же IDs и durable ledger/inventory, openings0, без нового seed. STORE200 owners/1911 lines; два cleanup/postflight, own SQL sessions0/JVM0. |
| F | PASS / DB_FREE + OWN099_NATIVE | Full shadow Ant compile/JAR, affected javac, focused checks, independent review P1/P2=0 по финальному коду. Production scope: один owner seam, без изменения owner config/schema/native shared engine. |
| G | OWNER_QA_DEFERRED / CLIENT_QA | Подключённый клиент/known-list/packets не проверялись. Это серверная приёмка native harness, не финальная playable-поставка. |

## Конкретная причина и минимальное исправление

Исходный START1: exit1, legal251, durable/native/nonempty1, blocked1. `reserveBirth()` собрал больше8 seed kinds при действующем `MerchantLinesPerStore=20`; существующий durable `Role` запрещает `stock.size()>8`. Исключение возникало **до** `commitNewRole`, поэтому failed ordinal не расходовал durable quota. Первая BUY-лавка имела пустой bootstrap stock и открылась законно.

Единственная production-правка: в `PhantomOfflineMerchantService.reserveBirth` seed ограничен `Math.min(8, _policy.linesPerStore())`. Сохранены codec, live listing limit20, finite quota, классы, native механика и owner значения. Нет нового market engine, абстракции, scheduler или subsidy.

RED receipt, DB fingerprints, byte preimage Java и RED JAR сохранены до правки во внешнем OWN099 stage. Единственный разрешённый causal replay использовал ту же TEST099 и восстановил прежнюю BUY-лавку; schema не пересоздавалась. У replay отдельные `start1-replay.*`, исходные `start1.*` не перезаписаны. Затем выполнен предусмотренный START2. Дополнительных native запусков не было.

## Actual census и layout

| Срез | Роли | Native owners / nonempty / leases | SELL / BUY / MFG | Issued capital / stock value |
|---|---:|---|---|---|
| RED START1 | 1 | 1 / 1 / 1 | 0 / 1 / 0 | 100000 / 0 |
| START1 causal replay, до STOP | 200 | 200 / 200 / 200 | 150 / 50 / 0 | 20000000 / 88019600 |
| START2 sameDB, до STOP | 200 | 200 / 200 / 200 | 150 / 50 / 0 | 20000000 / 88019600 |

В TEST099 всего202 character rows:200 merchants и2 отдельно маркированных FIXTURE_CUSTOMER. Единственный managed profile — server quota owner;201 components — ledger+200roles. Это не200 ordinary materialized Phantom profiles. Combat/materialization owners0, blocked0 на обоих успешных стартах. START2 openings0, sharedPulses0: владельцы были приняты из штатного restore.

| Source site | Raw | Legal | Occupied | First rejection reasons |
|---|---:|---:|---:|---|
| 0:81800,148700,-3400 | 120 | 111 | 111 | GEO_MOVE8, Z1 |
| 1:80550,148700,-3500 | 100 | 67 | 67 | GEO_MOVE11, NPC15, Z7 |
| 2:83700,149000,-3400 | 100 | 73 | 22 | GEO_MOVE25, Z2 |

Diagnostic layout повторяет exact source filters и сверяет total251 с actual service; он не выдаёт admission rights. Rejections — первая причина по source order. Observer points0. Town/Peace, arrival, door и spacing filters также выполнены; дополнительных отказов ими в этой сцене не было. Все200 actual coordinates принадлежат configured grid, pairwise spacing≥60; source sites не изменены.

После сделок и после restart:1911 native listing lines =1199SELL+712BUY. Категории по lines: armor550, common600, consumable499, enchant62, material50, recipe150. Grades по lines: NONE561/D450/C400/B300/A200. Это строки предложений, не количество магазинов или суммарных предметов.

Crystals, отдельная weapon-category, S/S80 и epic: **NO_SOURCE_OR_STOCK в actual offers**; наличие каждого такого товара не обещано. Их искусственно не выдавали; S/S80/epic исключены из merchant bootstrap. Все первоначальные merchant funds/stock — конечный BOOTSTRAP_CAPITAL_STOCK, не earned farm. Товары/деньги контролируемых клиентов помечены FIXTURE_CUSTOMER; это не доказательство заработка от обычных игроков или farm.

Примеры реальных SELL: item116/N=30 (`npc.stock.merchant`); item40/D=18596 (`npc.stock.merchant+recipe.expected_cost`); item62/C=101829 (`loot.20236.DEATH_DROP+recipe.expected_cost`); item554/B=319924 (`loot.21156.DEATH_DROP+recipe.expected_cost`); item12221/A=116072 (`loot.20963.DEATH_DROP+author.target_time`). Native census проверяет SELL≤physical inventory и current ask, BUY≤physical Adena и current bid.

## Транзакции, writers и STOP

Native SELL item2509, qty1, price17: merchant Adena100000→100017, customer100000000→99999983, merchant qty100→99, customer qty+1. Native BUY item1835, qty1, price6: merchant100000→99994/qty0→1, customer99999983→99999989/qty1→0. Tax в этих native transfers0; точные before/after assertions прошли.

Last-stack:2 concurrent customers, ровно1COMMITTED, общий stock/Adena сохранён; второй запрос штатно отклонён и оставил ожидаемый warning. Late native transaction после beginStop отклонена до effects, stock/Adena сохранены. Manufacturer не подготовлен: рецепты/skills/EXP не выдавались и MFG не имитировался.

Writer audit: service/ledger transactions и identity leases; native sell/buy/manufacture lists; PrivateStoreService owner/pair observers; OfflineTraderTable realtime/bulk/restore; native crafting, autosave, Player/ItemContainer cleanup. Native list locks и порядок sell→buy→manufacture сохранены; callbacks не берут service monitor, `_stopping` закрывает новые effects. `synchronized(Player)` универсальной блокировкой не объявлен.

OfflineTraderTable поглощает SQL exceptions, поэтому проверены actual rows и hashes после STORE **и после native deleteMe cleanup**. Только exact owned IDs: stopAllTasks/storeMe/deleteMe, World absence, releaseDeletedOwners; store type не сбрасывался. Затем ThreadPool.shutdown и DatabaseFactory.close. PIDs19760/17584 завершены; forced kill/World.remove не использовались. Postflight099: sessions0, own JVM0, собственные listeners0. Shared MariaDB не останавливалась.

Одинаковые hashes после обоих успешных STOP:

- merchant components: `94d095fab3f97b0318cc16bda0087095c9b5f8a6a23daf720d534458e91de04d`;
- inventory, включая fixture: `d09f46895d177b9cb5ac7692fbf72d1e63fc1bc62d7e6ce1cdc35196069fea61`;
- offline item rows: `7cd21de68a92924139028bdc3fc4a78363b6a2480eb63ac7f7ad71e507f317f5`;
- combined merchant role/ledger/inventory native digest: `6fc9f2f9ae2fa0feab211d703cfb9e850eb28110e164e9871b7ef903b30525d7`.

## Проверки, источники и ограничения

| Команда / route | Exit / результат |
|---|---|
| Task099.ps1 Prepare, sandbox owner negative | 1 expected; до записи/DB/World |
| Task099.ps1 Prepare, source owner | 0;29002inputs/Geo203/private ACL |
| Task099DatabaseProbe preflight/plan/create | 0; full metadata, schema absent;121stockfiles/203statements;18stockDROP omitted |
| SQL plan initial failure | 1 до DDL: отсутствовал allowlisted CREATE INDEX; после чтения exact characters.sql расширен только stock statement allowlist, plan0 |
| OwnerConfig.ps1 -Action Verify, до/после | 0/0;68files/287overrides/changed0 |
| ant -f <shadow-module>/build.xml compile-tests jar | 0;2373production/423test;50s; Ant повторил compile для второго top-level target |
| ant -f <shadow-module>/build.xml jar, после causal fix | 0;26s; единственная полная rebuild по конкретному native blocker |
| Affected javac, после каждого изменения test harness | 0; output только OWN099 stage |
| PhantomMerchant099Checks | 0;3groups: current policy/quota/recovery; native proof не подменяет |
| PhantomTestLauncher economy068-pricing 99009902 | 0;3/3 |
| PhantomTestLauncher post001-market-quote 99009902 | первый exit3: ошибочный cwd, отсутствующий General.ini/default WORLD; единственный targeted retry из shadow/dist/game exit0,6/6 |
| Native opt-in negative | 1 expected; нет flags — до DB/World |
| PhantomMerchant099Native 1 / 1-replay / 2 | 1 causal RED /0 GREEN /0 sameDB |
| Task099DatabaseProbe postflight | 0 после RED и обоих успешных STOP; SQL hashes совпали |

Общий ant verify не запускался: включает чужие DB suites. Native free-source/price-drift fault injection, owner-death race, positive MFG, client known-list/visual, full soak/300–500shops/5000profiles/TASK098 city visit — **NOT_RUN**. Не использовать этот отчёт как full D, долгосрочный market soak или принятие клиентского поведения. Новых native replay сверх разрешённого одного не делать.

20 nominal seconds, wall21.3164495s: CPU1.328125s (~0.0623core), heap2954457920bytes, GC0ms; global SQL Questions delta1 включает собственные probes и не считается числом writes. Scheduler sharedPulses61/dueOwners200, combat owners0; p95/opening latency/backlog latency NOT_MEASURED. Полные raw receipts хранятся приватно; versioned census агрегирован до234 строк.

Initial read set: все12 exact seams из READ_SET плюс root/module instructions, README, current status, bounded workflow/efficiency и package099. Аналоги068 native/bootstrap,096N prepare и073 read-only DB probe. Дополнительно причинно прочитаны StoreService.installNative, PrivateStoreService.mutate, OfflineTraderTable lifecycle; runner/build/OwnerConfig/HeadlessEnvironment/PhantomPlayersConfig entrypoints — только для безопасного запуска. Reviewer адресно проследил Player/Creature/ItemContainer cleanup. Промежуточные AGENTS, code-map/pattern-файлы не найдены; другие хроники не читались. Исторические task packages не менялись.

## Scope, review и публикация

Изменены ровно10 файлов: production merchant service; existing HeadlessPlayerTestEnvironment (только guarded099 wiring); новые Merchant099Checks/Task099Lane/Merchant099Native; Task099.ps1/Task099DatabaseProbe.java; этот report; `PHANTOM-MERCHANT-WORLD-099/market-census.tsv` и `runtime-metrics.tsv`. Конфиги/Semantic Pack/Studio/Player/World/OfflineTraderTable/schema production не изменены. Новая authority table находится только в собственной TEST099. Ordinary market cap2 сохранён.

Independent read-only reviewer проверил точные changes, причинный fix, transitive writers и sanitized native receipts. P1/P2 по финальному коду0; два ранних evidence P2 (per-site rejects и customer BUY qty) исправлены до native. Reviewer отдельно рекомендовал **PARTIAL099/D=PARTIAL** из-за отрицательных coverage gaps; рекомендация принята.

Baseline/local/upstream/live: `3a8a3587971e94750e21597d14148a23bf046111`, feature/phantom-world. Production preimage SHA `06005a3de84d3ff55647cd49092b607e66ba9a546d60ed3b6d7c5f88ab02ce23`; final service SHA `5b8b51d3a3b1138bff1595e55293cb1816beeb70da44ffa486dfe4b04181ca6b`. RED JAR `192fff3043e83eeb9a05850f08d97de45bf0dd852cde5e0cf974cdcfa04204c4`; final/sameDB JAR `05d3893f089d354629612c825b23e1a803e5160b95b474c8a8ea49de727f66e4`. Exact source copies сверены; source protected198 hashes сохранены.

Private evidence: `.phantom-local/logs/PHANTOM-MERCHANT-WORLD-099/`; external `%TEMP%/Phantom-Merchant-World-099` (owner+SYSTEM only). Stage/TEST099 сохранены STOPPED, не adopted/reset для будущих запусков. Native UUID/expiry, input SQL/Geo manifest, config overlay before/after SHA и credentials остаются private. Overlay только shadow: ordinary population5000/128→0/0; current validator принят, source5000/128 не менялся.

Git использовался по TASK099/root bounded authority: `git symbolic-ref --short HEAD`; `git rev-parse HEAD`; `git rev-parse --abbrev-ref --symbolic-full-name @{upstream}`; `git rev-parse origin/feature/phantom-world`; `git status --short` (также bounded --untracked-files=no); `git worktree list --porcelain`; `git merge-base --is-ancestor 3a8a3587971e94750e21597d14148a23bf046111 HEAD`; `git ls-remote origin refs/heads/feature/phantom-world`; `git diff -- <exact paths>`/`git diff --check -- <exact paths>`/`git diff --cached -- <exact paths>`/`git diff --cached --check -- <exact paths>`/`git diff --cached --name-only -- <exact paths>`. Source untracked WIP/task packages не staged. Публикация: `git add -- <10 exact paths>`; `git commit -m 'phantom(task-099): validate and stabilize offline merchant world'`; `git push origin HEAD:refs/heads/feature/phantom-world`, без force. Implementation SHA — commit, содержащий этот report; literal SHA и push receipt — в private `PUBLICATION099.json` и финальном ответе. Scoped diff SHA также сохраняется private до commit.

Две ошибки shell-оркестрации не меняли продукт: первый encoding checker не распарсился из-за smart quote в списке mojibake-маркеров, исправлено quoting; первый git add был отклонён read-only `.git` sandbox и потребовал запуска под разрешённым source owner. Native/Ant из-за них не повторялись.

- mojibake-маркеры в изменённых файлах проверены;
- escaped Cyrillic в изменённых файлах проверены.

Telemetry snapshot перед публикацией:301014 goal tokens,1946s (~32.4min); full Ant invocations2, production javac3 из-за top-level Ant dependency repetition, test javac1, JAR2; focused suite invocations6 (099×3,pricing068×1,quote×2), negative guards2; native JVM generations3 = RED + единственный causal replay + sameDB START2. Usage не означает полный soak. Источник — get_goal; финальная проверка и публикация добавляют расход.

Будущая owner visual QA: подключиться обычным клиентом, осмотреть три source участка Гирана/spacing, открыть фактические SELL/BUY окна, проверить видимость titles/товаров и обычную оплачиваемую покупку. MFG проверять только с законно подготовленным owner. Клиент агентом не запускался. **STOP; TASK100 не начат.**
