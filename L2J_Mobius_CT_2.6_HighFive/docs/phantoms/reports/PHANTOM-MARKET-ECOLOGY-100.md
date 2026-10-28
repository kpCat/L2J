# PHANTOM-MARKET-ECOLOGY-100

28.10.2026. **PARTIAL100**. Изменены существующий source-priced catalog и выбор native плана BUY-роли: приобретённый физический inventory получает приоритет SELL, без нового капитала или stock. **MFG_DEFERRED / OWNER_CLIENT_QA_DEFERRED**. Фикстура не доказывает заработок от farm.

## Матрица приёмки

| Gate | Результат | Доказательство и предел |
|---|---|---|
| A MFG | MFG_DEFERRED | Новые owners уровня1 не имеют Create Item. Stock DwarvenFighter требует level5/SP310 для172:1; common1320 также начинается с5. TASK100 SAFETY запрещает grants level/EXP/SP/skill/recipe. Prepared allowlist пуст; запрещённые grants не выполнялись. Paid MFG/BOM/MP/restart-maker NOT_RUN. |
| B assortment | NATIVE, initial PASS | Catalog128, bootstrap-eligible97; actual90 unique native listing item IDs против38 в099. Семь категорий: armor/common/consumable/enchant/material/recipe/weapon; NONE–A. Это фактические предложения200 World owners, не XML candidates. |
| C circulation | PARTIAL / CIRCULATION_OPEN | Исходный native BUY item9635, qty2, bid6: owner100000→99988, customer99999983→99999995; qty0→2 и2→0. Проверка немедленного reopen упала после двух pulses. Causal retry остановлен layout gate до final sale. Полный paid BUY→SELL→final sale и точный objectId через все этапы не доказаны. |
| D negatives | PARTIAL | Unknown quote и focused policy fences пройдены; native actual free-source/price-drift, wrong-owner/expired-role, active-craft, human observer, полное исчерпание BUY и SELL→BUY NOT_RUN. Fresh100 last-stack race не достигнут после causal interruption;099 остаётся историческим PASS. |
| E restart | PARTIAL | SameDB/sameJAR loader восстановил200 offline traders; role/ledger и полный inventory SHA до/после совпали. Штатный cleanup двух JVM, SQL sessions0. Полный scheduled generation2/STORE/сравнение ids1 не достигнуты. |
| F retained200 | initial NATIVE200; SCALE_REGRESSION_NOT_RUN | Initial START достиг200 owners/nonempty/leases,150SELL/50BUY, legal251, combat0, blocked0. Loader200 на restart не подменяет повторный census200 и полный scale gate. |
| G quality | code review/STOP/source PASS | Открытых P1/P2=0. Source168 protected hashes сохранены; OwnerConfig final0, own JVM0/SQL sessions0, normal exact-file publication по контракту ниже. |

## Реализация и существующие владельцы

`PhantomMerchantCatalog.java`: удалены прежний24-prefix и3-per-category/grade потолок. Каждый уже отфильтрованный native category/grade рассматривается в стабильном reference-price/itemId порядке; принимаются только canonical `quoteItem().priced()`; общий shortlist по-прежнему≤128, с round-robin по группам. Bootstrap по-прежнему только NONE–A, без epic; authoritative цены, Rates и acquisition validation не менялись. Полный аудит всех возможных source IDs не заявлен:128 — ограниченный принятый shortlist,97 — его birth subset.

`PhantomOfflineMerchantService.java`: MFG сохраняет приоритет при действительной квалификации; SELL из фактических inventory object IDs проверяется перед новым BUY demand. Для частично исполненного BUY owner rotation под sell→buy→manufacture locks сначала проверяет текущую цену, затем закрывает прежний native BUY и открывает SELL. Повторный bootstrap не вызывается; durable role kind остаётся прежним. После сбоя восстановление опирается на тот же inventory и native lists. Stock≤8/role codec v1/CAS/finite quotas не изменены.

Перед refresh/open проверяются STOP, native ownership, current World identity, transaction/pending-store/craft; open также требует instance0 и отсутствие human observer в слоте. Комментарий reviewer о полном исполнении BUY учтён: переход через NONE также проходит observer guard в open. Проверка этого пути статическая; она не выдана за native observer test.

Writers сохранены: service и identity leases; bootstrap ledger CAS; Player/ItemContainer inventory; PrivateStoreService native list lock, owner/pair observers и transfer; OfflineTraderTable realtime/bulk/restore; ManufactureService/RecipeManager; autosave/shutdown. Callback не захватывает service monitor. Native close/reopen не является транзакцией нового ledger. Crash injection между close/reopen и swallowed SQL failure отдельно NOT_RUN.

## G0, безопасность и scope

Исходный local/upstream/live HEAD: `d5addf542552836d5a0d7ff27b4ce4723b0ede75`, точная `feature/phantom-world`. Live ref проверен через read-only ls-remote; первый sandbox network call exit128, тот же запрос под разрешённым source owner exit0. Все10 package SHA совпали с исходным manifest. Чужие untracked packages и `.phantom-local` не staged.

OWN100 создана физически вне checkout, owner+SYSTEM private ACL, без reparse;29007 input hashes, однократная физическая копия Geo203. SQL G0: полная metadata visibility, TEST100 отсутствует, foreign sessions0. Create-only stock bootstrap121files/203statements,18DROP пропущены; отдельная own100 authority. DROP/reset/reseed/grants/изменения общей MariaDB не выполнялись. Database credentials не выводились. TEST100/stage сохраняются после STOP, не пересоздаются.

Сборка только в shadow. Source OwnerConfig Verify до/после обязателен. Runtime overlay только shadow: обычные population5000/128→0/0, exact schema100/JDBC pool4; merchant200 policy сохранена. Первый final Ant после overlay закономерно остановлен OwnerConfig CONFLICT, exit1 до compile: в shadow возвращён exact source population, выполнен Ant, затем runtime overlay применён после сборки. Проверка не отключалась. Первый native вызов отклонён до DB/World из-за лишних JDBC query options; shadow получил exact allowlisted URL. Source Database/config не менялись.

Product preimages до правок побайтово скопированы в private OWN100 и SHA сверены. Исходные RED runtime receipt, source и JAR сохранены. Три tools/test protocol fork происходят из exact SHA-pinned099 sources; authority/schema/UUID/start files отдельные100. Исторические tools, packages и отчёты099 не менялись, bypass старой authority не использовался.

Read-first: root/module инструкции, README, ограниченные текущий status/master plan/workflow/package standard; весь TASK100 package; отчёт099; catalog/policy/bootstrap/pricing/service;068 prepared-MFG,099 native/lane/prepare, headless infrastructure/build/OwnerConfig; native store/manufacture/offline writers. Дополнительных AGENTS, code-map/pattern-файлов не найдено. Локальные аналоги переиспользованы; новые зависимости, timers, schema production, Player, Rates, Semantic Pack/Studio и owner config не изменялись.

Exact changed set: два production Java; `PhantomHeadlessPlayerTestEnvironment.java`; новые `PhantomMerchant100Checks.java`, `PhantomTask100Lane.java`, `PhantomMerchant100Native.java`; `Task100.ps1`, `Task100DatabaseProbe.java`; этот report и `test/resources/phantoms/market100-census.tsv` — всего10 файлов. Это bounded единая merchant integration, без housekeeping и следующей задачи.

## Проверки и неудачные попытки

`M` — HighFive, `S` — приватная fresh OWN100, `SM=S/L2J_Mobius_CT_2.6_HighFive`; `E=M/java/org/l2jmobius/gameserver/phantoms/economy`, `T=M/test/java/org/l2jmobius/tests/phantoms`. Native `CP=S/build/phantom-test/bin;S/build/phantom-test/resources;SM/dist/libs/*`, cwd строго `SM/dist/game`. UUID/абсолютные private пути и реквизиты остаются только в stage.

| Команда / route | Exit / фактический результат |
|---|---|
| `powershell -File M/tools/OwnerConfig.ps1 -Action Verify` | initial0;68files/287overrides/changed0 |
| `Task100.ps1 -Action Prepare -SourceRoot M -StageRoot S -InstallationId <own UUID>` |0;package10/Geo203/physical/private ACL;DDL_DML0 |
| `javac -encoding UTF-8 -cp M/dist/libs/* -d S/private/checks T/PhantomMerchant100Checks.java` до policy extraction |1;missing selection methods, не behavioral proof |
| `javac -encoding UTF-8 -cp M/dist/libs/* -d S/private/checks E/PhantomMerchantCatalog.java E/PhantomOfflineMerchantService.java T/PhantomMerchant100Checks.java`, затем `java -cp S/private/checks;M/dist/libs/* org.l2jmobius.gameserver.phantoms.economy.PhantomMerchant100Checks catalog` / `circulation` |compile0; оба runtime exit1 на прежней логике: three-per-group truncation / purchased inventory stranded |
| Те же focused groups после реализации |0/0; bounded128 deterministic selection, BUY purchased stock priority, empty-stock return BUY, MFG priority; native effects не доказывают |
| `java ... Task100DatabaseProbe preflight/plan/create S <UUID>` |0/0/0;absent/full visibility/sessions0;121/203;only schema100 created |
| `ant -f SM/build.xml compile-tests jar` |0;production/tests/full JAR,52s; два top-level targets повторили production compile |
| `ant -f SM/build.xml jar` после overlay |1;OwnerConfig CONFLICT, до compile |
| `ant -f SM/build.xml jar` до runtime overlay |0;23s;финальный production JAR |
| `PhantomMerchant100Checks catalog/circulation` из shadow build |0/0 |
| `PhantomMerchant099Checks <shadow merchant policy>` |0;3 groups quota/v1/recovery |
| `PhantomTestLauncher economy068-pricing 10010001` |0;3/3 |
| `PhantomTestLauncher post001-market-quote 10010001` |0;6/6 |
| `PhantomMerchant100Native 1` без opt-in |1 expected до DB/World |
| Native sealing, первоначальный `config/OfflineTrade.ini` pin path |shell0 с PowerShell diagnostics о missing file; исправлен на фактический `config/Custom/OfflineTrade.ini`, pin добавлен до START; guard не обходился |
| Native opt-in с избыточным JDBC query |1 до DB/World/start.begun;guard не ослаблен |
| `java -Xms512m -Xmx4g -Dphantom.task100.native=true -Dphantom.task100.stage=S -Dphantom.task100.uuid=<UUID> -Dphantom.module.root=SM -Dphantom.test.config=SM/dist/game/config/Database.ini -cp CP org.l2jmobius.tests.phantoms.PhantomMerchant100Native 1` |1;native200/90IDs и SELL/BUY conserved; harness слишком рано проверил dirty owner;graceful cleanup/SQL sessions0 |
| Affected javac native harness |0;bounded scheduler wait и continuation прежнего оплаченного stock без новых grants |
| Native `1-replay` с теми же flags/JAR |1;loader200, service durable200;layout assertion: diagnostic filters отличаются от snapshot legal241;native chain не продолжалась;cleanup drained/owners0 |
| Native `2` |NOT_RUN: после второго независимого harness blocker единственный causal retry исчерпан; отсутствуют successful ids1/durable1, guard не обходился |
| `Task100DatabaseProbe postflight` после RED и replay |0/0;schema100 только read-only;own SQL sessions0;ledger/inventory SHA неизменны |
| `OwnerConfig.ps1 -Action Verify`, final |0;68files/287overrides/changed0 |

Общий Ant verify, чужие DB suites, 5000 ordinary, soak/PvP/city/TASK098, клиентская UI/known-list и новая playable release — NOT_RUN. Это не закрытие исторических PARTIAL.

## Independent review и bindings

Отдельный read-only reviewer проверил product diff, role/ledger/pricing/native writers и OWN100 safety. Открытых P1/P2=0; full-BUY observer issue закрыт адресным guard. После causal harness fix проведена одна адресная перепроверка без нового review loop. Отдельно принят предел C: исходный BUY receipt не записал buyer/objectId, поэтому полная сквозная identity до сбоя не объявляется доказанной.

SHA256: catalog `99600f5c278343be4fb6c5da9772381a0e8763963e4d4c73cd6182a3cd3a4e81`; service `afc2191de736a8cbbe9b7a46ef77cc2a3fc03f11d521e0a49eebe5886054e453`; final/sameDB JAR `75fb9919cadf14847c47bd57729c22f352dc0dc350f5592b9a8442db0beaa45d`. Production после native RED не изменялся. Harness после адресной поправки `b585081532133255b882f2f8779693bd4577630633b383012303f04912ac8bbe`.

## Native результат, restart и postflight

Initial census:2038 actual lines,90 unique item IDs,200 owners,3sites111/67/22. Category lines: consumable748, enchant138, recipe251, material100, common294, armor369, weapon138. Grade lines: NONE827/D409/C334/B249/A219. Sanitized313-row SKU/site/grade census сохранён отдельно; он помечен `initial100_before_causal_failure`, не final successful run. В099 было1911 lines/38 IDs/6категорий без weapon; исходный отчёт099 не менялся.

First native RED не является product money/stock failure: paid SELL2509/qty1/17 и BUY9635/qty2/6 сохранили деньги и предметы. Проверка через два pulses не учитывала общую очередь due200/batch5. Исправлен только harness wait/continuation; production JAR не менялся. В единственном retry loader действительно восстановил200 offliners, затем новый диагностический layout gate остановил выполнение: source service snapshot legal241, повторный текущий обход NPC/Geo не совпал. Причина динамического расхождения двух layout observations не устранена и не выдана за source parity PASS. Дополнительного retry или принудительного запуска generation2 не было.

После обоих запусков202 character rows,1 quota profile,201components,200 offline owners. Offline item rows2038→2022; это наблюдаемое изменение native persisted lists, не доказательство завершённой C продажи. Merchant components SHA неизменен: `1975c3bccb467c6ec111d42540dbe100fd27b3a18c38154a2d938b5ef4d53559`; полный inventory SHA неизменен: `e8918c181d80d95b4dd0ef13235ba75ec468fae0b08694f9f1aa9a3b5257a316`. Offline lines SHA после RED `24d78cfa7eb694688d939454cc078fdac7acaa5d4664785bcc12350860dbaf07`, после retry `a0ee0829f26c47a9cac94d1cf231dbd2c2b5b1b7b931336113e8ff6c8868f18c`. Это ограниченная restart continuity, не полный E/F PASS.

Cleanup выполнялся только для своих service owners через stopAllTasks/storeMe/deleteMe/releaseDeletedOwners. Оба receipts:drained=true/remainingOwners0; ThreadPool/Hikari штатно закрыты. Final SQL own sessions0, own JVM0; собственных серверных listeners не открывалось и собственных JVM, способных их удерживать, нет. Shared MariaDB/чужие runtime не останавливались. Bulk STORE, финальный paid resale, sample CPU/heap/GC/p95 — NOT_RUN после interruption; показатели не выдуманы.

## Завершение и публикация

Git использован по явной TASK100/root authority: `git symbolic-ref --short HEAD`; `git rev-parse HEAD`; `git rev-parse --abbrev-ref --symbolic-full-name @{upstream}`; `git rev-parse @{upstream}`; `git worktree list`; `git status --short`/`--untracked-files=no`; `git merge-base --is-ancestor d5addf542552836d5a0d7ff27b4ce4723b0ede75 HEAD`; `git ls-remote origin refs/heads/feature/phantom-world`; `git diff -- <exact paths>`; `git -c core.whitespace=cr-at-eol diff --check -- <exact paths>` и bounded cached check/inventory. Публикация только `git add -- <10 exact paths>`, `git commit -m "phantom(task-100): qualify and circulate native merchant stock"`, `git push origin HEAD:feature/phantom-world`, без force. Final implementation HEAD — commit, содержащий этот report; literal SHA/push receipt сверяются после публикации и приводятся в финальном ответе и private publication receipt. Master/default/history не изменяются, чужой WIP не staged.

- mojibake-маркеры в изменённых файлах проверены;
- escaped Cyrillic в изменённых файлах проверены.

Telemetry snapshot перед публикацией:353695 cumulative goal tokens,1366s (~22.8min), источник get_goal; финальные проверки/push добавляют расход. Это не native benchmark. Full Ant invocations3: две успешных/одна owner-conflict; production javac3, test javac1, JAR2. Affected javac5: первоначальный missing-method RED, behavioral RED, GREEN, DB probe, causal harness. Новые focused selection groups выполнены четырежды каждый (RED/GREEN/shadow GREEN/final GREEN);099 quota один раз, pricing068 один раз, quote один раз. Native invocations4: opt-in negative, JDBC pre-World rejection, actual RED START1, единственный sameDB causal replay; последние два — реальные World generations. Плановый generation2 NOT_RUN. Ни одного нового запуска после исчерпания retry.

Следующий допустимый scope — отдельная задача законного получения MFG-квалификации и недостающих negative/recovery proofs; автоматически она не начинается. Текущий task доставляется как **PARTIAL100**, без SUCCESS, full scale или client QA. **STOP после normal push.**
