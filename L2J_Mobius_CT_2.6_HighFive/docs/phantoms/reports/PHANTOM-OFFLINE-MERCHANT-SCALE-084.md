# PHANTOM-OFFLINE-MERCHANT-SCALE-084

24.10.2026. **TASK084=SUCCESS в разрешённом P0/P3 scope с ограниченным P1/P2.** Это не NATIVE500, не FINAL_RELEASE_GREEN и не новая приёмка исторического native200.

## Authority baseline

Фактический root — `D:\Codex\repos\L2J-phantom-world`, модуль `L2J_Mobius_CT_2.6_HighFive`, ветка `feature/phantom-world`, origin `https://github.com/kpCat/L2J`.
Local HEAD, upstream `origin/feature/phantom-world` и live remote branch перед работой и перед публикацией: `4eae00941edf2285a2ea07c79091d271c5f55fdc`. Live remote default HEAD: `master`, `16d61833b3983a3976583d0e4813e0de9457a52f`; он не изменяется.

Прочитаны root/module AGENTS, root README, current status/workflow, OWNER_FINAL_RELEASE_TARGETS, bounded merchant-разделы RESULT068, TASK_PACKAGE_STANDARD, начало development master plan и все 10 файлов пакета084, включая manifest и SEMANTIC_FUTURE. Выше root AGENTS не найдены; дополнительных AGENTS в целевых java/test/docs не найдено. SOURCE_MAP084 найден и сверен с кодом; отдельные локальные code-map/pattern-файлы в целевом docs scope не найдены, повторно не искались.

Локальные аналоги: существующие `PhantomMerchantPolicy.load`, `PhantomMerchantBootstrap068Checks`, v1 Ledger/Role, `PhantomIdentityLeaseRegistry`, native-сценка068 и DB-free `PhantomTravelGeoProbe`/`HermeticGeoMovement`/`StaticXmlCollisionOracle`. Java25/Ant, существующие native owners, inventory, quote authority, OfflineTraderTable и общий scheduler сохранены.

Исходный tracked status чистый. Сохранены исходные untracked `.phantom-local/` и 11 task-пакетов: 068V, 073, 076, 077, 078, 079, 080, 081, 082, 083, 084. Они не редактировались и не включаются в commit.

## Gates и точные counts

| Gate | Результат | Доказательство / граница |
|---|---|---|
| A0 baseline/read-first | PASS | Local/upstream/live branch совпали; чужой tracked dirt отсутствовал |
| CAPACITY_500_CODE / SOURCE_CAP_500_VALIDATED | PASS | Парсинг 500/500/500 и 500/500/512; отрицательные границы |
| P0 v1 ledger/role | PASS DB-free | 500 debit, ordinal499, исчерпание512, encode/decode, uncertain restart fail-closed |
| P0 unique leases | PASS DB-free | 500 registry leases, повторный PHANTOM/REAL_LOGIN отказ, stale close и cleanup |
| RAW shipped | 320 | 120+100+100; конфиг не изменён |
| Частичный GeoEngine proof | 260 | Height и оба прямых +16 смещения; static XML collision closure; это не legalSlots |
| GEOSLOTS_VERIFIED / LEGAL_500_VERIFIED | NOT_RUN / BLOCKED_500 | Полный live Town/Peace/NPC/doors/anchors/spacing/observer gate не исполнен |
| P1 finite funding | BLOCKED_500 | 30m хватает только на300 новых grants по100k; автоматического увеличения нет |
| NATIVE_STORES_VERIFIED(300/500) | NOT_RUN_PREREQUISITE | Existing068 lane не поддерживает новую exact schema084 |
| SAME_DB_RESTART(300/500) | NOT_RUN | Ни новой DB, ни native actors084 не создавалось |
| MEASURED_CPU/RAM/SQL/GC/stop | NOT_RUN | Нагрузочной native-сцены084 нет |
| P3 regression / source review / encoding | PASS | Команды ниже; независимый reviewer без blocking findings |
| Publishing | Post-commit gate | Результат normal push, точный SHA и equality сообщаются в финальном сообщении после исполнения |

Исторический RESULT068 подтверждает candidate200 native owners/leases/nonempty (150 SELL,49 BUY,1 MFG), sameDB identity/quota/offline hashes; отдельный TEST имел202 owners/201 nonempty. Эти старые испытания здесь не повторялись. `PhantomMarketMaximumOpenStores=2` ограничивает обычного active producer, а не dedicated merchant service.

## Changed sources and reasons — exact allowlist

Все пути ниже относительно модуля; всего **4 файла**, без bounded exception:

1. `java/org/l2jmobius/gameserver/phantoms/economy/PhantomMerchantPolicy.java` — единственный bound hardCap300→500; target≤hardCap, lifetime≥hardCap и ≤512, остальные ограничения сохранены.
2. `java/org/l2jmobius/gameserver/phantoms/economy/PhantomOfflineMerchantService.java` — ранний отказ birth при недостатке persisted capital, без повторного исключения/WARNING раз в секунду; существующий однократный startup INFO дополнен rawSlots/remainingCapital.
3. `test/java/org/l2jmobius/tests/phantoms/PhantomMerchantBootstrap068Checks.java` — сохранены старые проверки и добавлены три focused DB-free группы: policy, finite ledger/v1, leases.
4. `docs/phantoms/reports/PHANTOM-OFFLINE-MERCHANT-SCALE-084.md` — настоящий отчёт.

## P0 cap500, ledger version и lifecycle

Формат/версии `merchant.server.v1`, `merchant.owner.*`, Ledger/Role encoding и persisted limits не изменены. Старый ledger с identityLimit300 остаётся300: смена ini не превращает его в512 и не увеличивает capital/stock/per-item quota. Source capacity500 относится к fresh либо уже достаточному durable ledger; автоматической миграции/сброса/пополнения нет.

Source audit не обнаружил отдельного runtime bound300. Ordinal499 укладывается в Role<512, component `merchant.owner.0499`; base36 suffix для300 и500 имеет одинаковую двухсимвольную ширину. Account/name ownership, atomic role+ledger commit, PENDING→INCONSISTENT, World-before-load, leases, NATIVE_OWNERS, observers, native BUY/SELL/MFG и graceful drain не менялись. При отсутствии slot birth возвращает null до reserve; roles не пересоздаются и quota не тратится. При недостатке capital новый guard также возвращает null до поиска места/записи; он использует durable остаток, а не новый config globalCapital. Runtime proof этих путей на500 не заявлен.

## P1 raw320, геометрия и источник координат

Новых merchant coordinates, sites или opt-in preset нет. Проверены только existing shipped grids и ровно два смещения +16, применяемые production legalSlots. Источник read-only GeoData: физические203 `.l2j` в `D:\PhantomWorld\Living-Economy-068-candidate\game\data\geodata`. Для всех трёх участков нужен один tile `22_22.l2j`; он скопирован в новую временную source-копию, без запуска/изменения старой поставки. SHA256 оригинала до/после и копии: `B5D508CD9294DCDED620D0E54A27B74E04274BA9F3395BC357EBB1E5CC671FA9`.

Настоящий GeoEngine загрузил1 region. Existing probe `--heights` обработал960 points: 320 origins и640 соседних heights. Production |height−seedZ|≤200 отвергает10 origins. Для остальных310 origins выполнено620 коротких connector probes:538 VALID_DIRECT,34 NO_PATH_WITHIN_BUFFER,48 PATH_SEGMENT_BLOCKED. Два прямых смещения проходят у260 origins. Все620 final collision proofs — STATIC_XML_CLEAR; более длинный найденный путь не принимается вместо production direct check.

| Shipped site (порядок сохранён) | RAW | Height rejected | Оба direct offsets прошли |
|---|---:|---:|---:|
| 81800,148700,-3400;12×10 |120|1|111|
| 80550,148700,-3500;10×10 |100|7|76|
| 83700,149000,-3400;10×10 |100|2|73|
| Всего |320|10|260|

Таким образом, RAW уже меньше500 на180, частичный geometry subset меньше500 на240 (и меньше300 на40). Это не точное число runtime legal slots: `legalSlots()` дополнительно требует TownZone/PeaceZone, GK anchors≥300, NPC distance≥max(160,radius+60), doors≥160, uniqueness/spacing≥60; `resume` проверяет native PEACE/instance0, birth/restore запрещены в радиусе2000 реального observer. Полный rejection breakdown по этим live guards **не измерен**, их нельзя выдумывать или считать нулём. Проверенных appended площадок нет; координатный поиск без совместимого native guard не подменялся synthetic count. GEO500 не пройден.

## P1 finite subsidies / assortment truth

Default сохранён побайтово: False/200/250/300, batch5/rotation30, lines20, capital100000/identity, global30000000, stock500000000, perItem10000, stack100, BUY25/MFG10. При500 новых subsidized identities требуется **50000000 Adena**, дефицит к30m — **20000000**. При lifetime512 и grant100k полный lifetime envelope равен51200000. Это расчёт требуемой конечной квоты, не согласованное повышение и не runnable preset.

Stock budget500m при lifetime500 даёт ceiling1m value/identity, при512 —976562 (целочисленно); per-item10000 при stack100 допускает максимум100 выдач одного такого stack. Lines20 — верхний limit, не обещание20 доступных native строк. Фактическая жизнеспособность500 зависит от canonical catalog/quotes, assortment, item quotas и уже потреблённого ledger. Synthetic ledger test тратит по1m stock value и20 units для проверки исчерпания; он не выдаёт реальные вещи и не доказывает assortment.

BOOTSTRAP_CAPITAL/STOCK — одноразовая субсидия, не earned inventory. S/S80/epics в seed не добавлялись, quotes/fees/tax не менялись. MANUFACTURE без реального recipe/skill законно остаётся SELL; новые skills, recipes и бесплатный craft не создавались.

## P2 native / sameDB restart / performance

`PhantomMerchant068Native` требует `PhantomTask068Lane.enabled()`, target1..200 и exact `_test_068`, owner/root/config under `.phantom-local/task068`. `PhantomHeadlessPlayerTestEnvironment` не имеет lane084. Общий FreshLocalPlayDatabaseGuard поддерживает absent-only CREATE, но это не разрешает существующей native-сценке использовать084 и не заменяет её exact DB owner guard.

Поэтому не выполнялись JDBC connect, absent-schema query, CREATE/import, native300/500, load старых merchants, запуск Game/Login/client и restart. Существование `l2jmobiush5_phantom_test_084` **не проверено**; оно не объявляется отсутствующим. Старые базы/078/общая MariaDB не затронуты. Guard068 и target200 сохранены; простое увеличение тестового target без sites/funds/exact084 lane было бы небезопасным. CPU/heap/GC/threads/SQL и stop/restart300/500 — NOT_RUN. Исторический068 CPU234375000ns за30s, heapUsed3199359200 и SQL Questions delta3 относятся ко всей прежней JVM и не экстраполируются на500.

## Tests / negative / команды и неудачные попытки

Source snapshot: `%TEMP%\phantom084-f0ce334345b74952adf46babbbae4163` (далее S). В него обычным Copy-Item скопированы build.xml/java/test/dist/tools; JAR не создавался. Никакие build outputs не писались в checkout или защищённые release. SHA256 live и final snapshot совпали для всех трёх изменённых Java files, PhantomMerchants.ini, OwnerConfig.json, build.xml.

1. RED: `javac -encoding UTF-8 -cp .\build\bin -d %TEMP%\phantom084-capacity-red <Policy.java> <Bootstrap.java> <IdentityLeaseRegistry.java> <PhantomAssertions.java> <PhantomMerchantBootstrap068Checks.java>`, затем `java -Dphantom.module.root=<module> -cp "%TEMP%\phantom084-capacity-red;.\build\bin" org.l2jmobius.tests.phantoms.PhantomMerchantBootstrap068Checks`. Компиляция успешна; runner exit1 на 500/500/512 в старой policy. Старые OWN068 cases3 перед этим PASS.
2. `ant -f S\build.xml -Dbuild=S\build compile-tests`: первая попытка exit1 до компиляции. Диагностика прямым Java source launcher показала AccessDeniedException при чтении temp-копии из sandbox; исходный read-only OwnerProfileVerifier дал PASS38. Тот же Ant после разрешения sandbox доступа exit0/27s:2365 production+395 test sources. OwnerConfig Apply в копии `filesChecked=68 overridesStored=14 changed=0`; verifier PASS38/XML_XSD. Существующие5 warnings:3 ExtendedOpenOption в LocalPlayPilotWriter070Checks и2 runFinalization в scale suites.
3. `java -Dphantom.module.root=S -cp "S\build\bin;S\build\phantom-test\bin" org.l2jmobius.tests.phantoms.PhantomMerchantBootstrap068Checks`: exit0, OWN068 cases3 и TASK084 groups3 PASS, DB_WRITES=0/NATIVE_STORES=NOT_RUN.
4. После независимого review — один bounded correction: `javac -encoding UTF-8 -cp "S\build\bin;S\build\phantom-test\bin;S\dist\libs\*" -d S\build\bin S\java\org\l2jmobius\gameserver\phantoms\economy\PhantomOfflineMerchantService.java`; затем javac уточнённого existing checks в S\build\phantom-test\bin и повтор команды3. Все exit0. Второй полной Ant-компиляции нет.
5. Из `S\dist\game`: `java -Xmx512m -cp "..\..\build\bin;..\..\build\phantom-test\bin;..\libs\*" org.l2jmobius.tests.phantoms.PhantomTravelGeoProbe ..\..\heights-input.tsv ..\..\heights-proof.tsv --heights`, затем тот же runner с `..\..\movement-input.tsv ..\..\movement-proof.tsv`. Оба exit0. TSV построены только из shipped sites/+16 offsets; evidence остаётся в temp, GeoData/бинарники в Git не добавлены.

Policy negatives: target501>hard500, hard501, lifetime513/499, batch11, rotation14, lines21, negative grant, globalCapital<grant, stock>1e12, perItem0, stack1001, invalid percentages, duplicate/>32 prepared owners, malformed/empty/>8 sites, raw1001 (raw1000 accepted). Default OFF/200/250/300 и site order сохранены. Ledger: finite50m/500m/perItem10000, replay rejection, default30m исчерпание на301-й grant, ordinal499 v1 READY roundtrip, PENDING fail-closed, unknown version/trailing bytes; leases500 — только process-local ownership contract.

Независимый bounded source review: blocking findings нет после capital guard; lifecycle/native/performance и полнота Geo probe reviewer не запускал. Service guard проверен source review и компиляцией, отдельный native pulse test NOT_RUN. Общий ant verify/soak/semantic validation не запускался.

Неудачные read-only команды также учтены: первый `git ls-remote` exit128 из-за sandbox network, разрешённый повтор exit0; один rg по `tools\*` дал invalid path123, исправлен на tools directory; предположенные файлы `PhantomIdentityLeaseRegistrySuite.java`, `PhantomNavigationSuite.java` и production `StaticXmlCollisionOracle.java` не найдены — использованы найденные реальные registry/probe/test-package файлы. Первый inline encoding guard получил PowerShell ParserError из-за передачи кириллицы через command encoding; повтор использовал ASCII hex codepoints только для технических search markers. Это ошибки поиска/окружения, не product failures; несвязанных исправлений нет. Manifest всех9 файлов пакета084 совпал с SHA256.

UTF-8 strict decode изменённых файлов — отдельная проверка.

Mojibake-маркеры в изменённых файлах проверены отдельной проверкой полного набора27markers.

Escaped Cyrillic в изменённых файлах проверены отдельно всеми6 regex, включая обе XML x/X формы.

## Safety matrix

| Защита | Результат |
|---|---|
| v1 encoding / historical quotas | Unchanged; DB-free roundtrip PASS; migration/refill нет |
| Duplicate owners / subsidy replay | Registry/ledger contracts PASS; native500 NOT_RUN |
| Existing slot order / saved XYZ | Конфиг unchanged; v1 XYZ roundtrip PASS |
| Geo/peace/NPC/doors/spacing/observer | Production guards unchanged; полный live proof NOT_RUN |
| Real inventory / quotes / BUY budget / MFG skill | Native authority unchanged; новых товаров/денег не выдавалось |
| Default / owner config / build.xml / QoL/rates/Alt+B | Tracked edits отсутствуют; key hashes live=snapshot |
| Studio / Semantic V3 / старые базы / release078 / untracked | Не изменялись; native среды не запускались |

## Exact Git commands / публикация

Git прямо разрешён текущим TASK084 для baseline/scope и normal commit/push. Выполнены: `git rev-parse --show-toplevel`; `git symbolic-ref --short HEAD`; `git rev-parse HEAD`; `git status --short --branch`; `git status --short --untracked-files=no`; `git for-each-ref --format='%(refname) %(objectname) %(upstream)' refs/heads/feature/phantom-world refs/remotes/origin/feature/phantom-world`; `git ls-remote --symref origin HEAD refs/heads/feature/phantom-world`; `git ls-remote origin refs/heads/feature/phantom-world`; `git diff --check`; `git diff --stat -- <three exact Java paths>`; reviewer/author `git diff -- <exact changed Java paths>`.

Заключительная последовательность для четырёх exact allowlist paths выше: `git diff --check`; `git diff -- <exact Java paths>`; `git status --short --branch`; `git add -- <four exact paths>`; `git diff --cached --check`; `git diff --cached --stat`; `git diff --cached --name-only`; `git commit -m "phantom(task-084): allow bounded offline merchant capacity 500"`; повтор live branch check; `git push origin HEAD:refs/heads/feature/phantom-world`; `git rev-parse HEAD`; `git rev-parse origin/feature/phantom-world`; `git ls-remote origin refs/heads/feature/phantom-world`; `git status --short --branch`; `git status --porcelain --untracked-files=no`. Результат post-commit/post-push проверки и self-identifying commit SHA — в финальном сообщении; они не подменяются заранее записанным PASS. Нового branch/force/reset/clean/stash/rebase/amend/изменения default branch нет.

## PRODUCT_BACKLOG — STOP

Остаются отдельными задачами: 300–500 actual native merchants с доказанными additional legal sites, совместимым exact fresh absent-only084-подобным guard, согласованными конечными квотами, memory preflight и CPU/RAM/GC/SQL/stop/sameDB restart; 5000 durable distinct battle profiles и начальный cap128+ с реальной нагрузочной устойчивостью; финальные owner account/config/SaveOwnerConfig, Custom Alt+B/QoL/rates и личная игровая приёмка по OWNER_FINAL_RELEASE_TARGETS. Старые PARTIAL не закрыты этим cap change.

Semantic Pack ×4 / массовая генерация Codex **DEFERRED**, только требование SEMANTIC_FUTURE: ориентиры32768 patterns/131072 replies/256segments/128MiB требуют отдельного benchmark/audit, ничего не реализовано. Studio на паузе. 085/R2 не запускались. После scope084 commit/push и проверки equality/clean — **STOP**.
