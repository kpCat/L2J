# PHANTOM-CRAFT-MARKET-CLOSURE-101

28.10.2026. **PARTIAL101**. Dedicated qualification и ordinary recipe reward реализованы раздельно. Обычным Phantom production-код не выдаёт EXP/SP/уровень/профессию/навыки. Реальная естественная ordinary-прогрессия в OWN101 не наблюдалась: `NATIVE_NATURAL_PENDING`. Клиентская приёмка — `OWNER_CLIENT_QA_PENDING`.

## Матрица приёмки

| Gate | Результат | Измерение / граница |
|---|---|---|
| A dedicated provision | PASS | 200 actual stores: SELL130/BUY50/MFG20; 20/200=10%, desired20. Двадцать READY `OWNER_PROVISIONED_CRAFT` receipts, native skills/recipebook/character SQL readback и sameDB reload. |
| B paid craft | PASS, recovered native + SQL | Recipe58/scroll2165, maker268481056, product916×2: штатный RARE_PRODUCT при100% recipe. Fee2340, MP147; BOM1913×9/1892×1/1889×10/1887×2/1459×16/2131×7. Native events и физический DB product/fee/BOM сверены после graceful cleanup. Исходный ошибочный harness RED сохранён. |
| C ordinary natural progression | IMPLEMENTED / NATIVE_NATURAL_PENDING | Production hook после native admission и paid learning; native class/rank, source recipes, lifetime quota20, per-rank2, persisted preimage/readback. Настоящий earned EXP/SP/class/skill witness не создан искусственно и не объявлен PASS. |
| D optional ordinary fixture | RECIPE_REWARD_NATIVE_PASS / STORE_NOT_RUN | Один `TEST_FIXTURE_PROMOTED_ORDINARY`, profile2, Maestro82/Create Item10. Native reward recipes5/6, два вызова под outer ActionLease: READY, без изменения EXP/SP/level/rank, no self-drain. Это не C и не доказательство ordinary market schedule. |
| E paid circulation | PASS | Owner268480990, item8658: BUY2→JVM STOP/restart→SELL1→last1→BUY. Customer object268482593, merchant actual object268482594; именно второй проверен после restart. Bid104/ask116, owner100000→99792→99908→100024; customer99997660→99997868→99997752→99997636; owner qty0→2→1→0, customer2→0→1→2. Subsidy replay0. |
| F sameDB/sameJAR actual scale | PASS | Current JAR: actual200→200, каждый раз MFG20; обе завершающие JVM имеют native World census, не только loader200. Identities, qualification components и durable physical quantities совпали; bootstrap capital20000000/stock value176095710 неизменны. Graceful STOP, own JVM0/SQL sessions0/listeners0. |
| G negatives | BOUNDED_PASS / REMAINDER_NOT_RUN | Actual unknown source, invalid MFG BOM, underpriced listing drift, free NPC item12006: denied before effects, goods/currency unchanged, original native Product restored. Pre-JDBC wrong cwd/flags/SID/input bytes denied. Full observer/crash/wrong-classIndex native matrix не прогонялась. |
| H quality | PASS_WITH_LIMITS | Current shadow Ant compile/tests/JAR; focused contracts; independent review5 findings исправлены, targeted follow-up P1/P2=0. Новый fresh-birth guard затем проверен causal native run. Protected source169 SHA unchanged; publication/STOP — финальный протокол ниже. |

Полный SUCCESS не заявляется: C требует естественной прогрессии; G/D имеют указанные пределы. Prepared/provisioned/fixture не выдаются за organic economy.

## Реализация и source facts

Native ancestry: DWARVEN_FIGHTER53→ARTISAN56→WARSMITH57→MAESTRO118. Dedicated tiers: Artisan20/Create2 (4 владельца), Warsmith43/Create5 (6), Warsmith55/Create7 (6), Maestro76/Create9 (4); Common Craft ранги берутся из того же complete class tree. Шесть изученных100% source-priced-BOM recipes на dedicated owner, детерминированный bounded subset. Финальные native ID/rank/type проверяются SQL, поскольку native recipe/skill/character writers могут проглотить SQL exception.

Новый MFG intent рождается из53, только если53 разрешён текущим MerchantClassIds. Human/Elf READY roles не меняют расу. SELL/BUY не квалифицируются. Disabled account/token/name/object/role/lease должны совпасть; ordinary profile link запрещён. Quiescence, отсутствие наблюдателя и native list locks сохраняются. До эффектов записывается отдельный PLANNED; неопределённый результат не переигрывает grants. Role/ledger binary v1, issued quotas и stock/capital не изменены.

Ordinary admission использует существующий materialization ActionLease и положительный profile/character link. Native Create Item должен уже существовать; разрешены только Artisan/Warsmith/Maestro. Рецепты выдаются через `registerDwarvenRecipeList(...,true)`, а не scroll items. Подтверждение native квалификации выполняется до intent; uncheckpointed progression откладывается до следующего admission. Recipe INSERT проверяется без `storeMe` внутри ACTION; base/subclass SQL разделены. Receipt fingerprint включает native code и source recipe/BOM/rank. Квота общая между classIndex, без нового timer/polling.

Оба новых INI additive, absence/False=OFF, shipped opt-in=True по OWNER_DECISIONS. Старые source INI и OwnerConfig не сохранялись/не применялись. Verify исходника:68 files/287 overrides/conflicts0/changed0.

## Native хронология и отклонения

1. G0: schema101 ABSENT, full metadata visibility,160 schemas, foreignSessions0, DDL/DML0/World0. Создана ровно одна новая `l2jmobiush5_phantom_test_101`; audited121 stock SQL/203 statements,18 historical DROP statements пропущены; отдельно собственная authority table/row. Ни reset/reseed/drop/grant, ни вмешательства в MariaDB service/защищённые БД не было.
2. Physical external shadow: owner+SYSTEM ACL, no reparse, Geo203, initial29021 input hashes; source config/semantic preimages. Отдельные UUID/expiry/SID/codeSource/cwd/authority и actual input manifest проверяются до JDBC/World, включая XML/skills/Geo/JAR/test classes. Source runtime release не создавалась.
3. Первый native START1: RED на10 roles, до первого qualification intent. Причина source-backed: `Player.create` задаёт native dwarf template/base53, но activeClass остаётся0 до load/class change. Guard исправлен по фактическому `getPlayerClass()==DWARVEN_FIGHTER`, с прежним base53/level1/empty book/ownership. SQL подтвердил craftReceipts0; исходные JAR/source/receipts/seal сохранены. Та же DB, без новых initial grants для READY identities.
4. Causal resume: actual200/MFG20,181 unique item IDs вместе с MFG products, catalog128, legal admission slots251; no combat/materialization owners. Ordinary fixture и native negatives прошли. Fee/BOM/MP и RARE_PRODUCT были настоящими; harness ошибочно ожидал только SUCCESS_PRODUCT и quantity1. RED не удалён и не переименован в успешный START1.
5. После повторного прямого указания владельца «продолжай до завершения» выполнено ограниченное продолжение harness на том же JAR. Fee/product/BOM подтверждены read-only SQL; receipt помечен RECOVERED_PARTIAL_START1. Первый planned START2 остановился на raw inventory SHA: native restore слил Adena100000 и paid fee2340. Полная реконструкция прежнего SHA доказала ровно эти две строки: retained object268481057, merged object268482599, owner268481056; прочие bytes идентичны. Per-owner Role.stock/capital+fee всех200 совпали, non-inventory/zero rows0. Это не потеря/создание денег.
6. Harness теперь сверяет native quantities по owner/item/location и точные component bytes; строгий objectId купленного товара сохраняется отдельно. Исходные raw SHA/RED/seals/classes архивированы. START2-replay и START3 завершились exit0: actual200/MFG20, BUY2 cutpoint, настоящий JVM restart, обе resale и BUY. Production JAR между ними не менялся. Это явное исключение из исходного retry budget по повторному указанию владельца: один product correction до grants, две доказанные harness corrections, всего5 World invocations (3 RED,2 PASS); без новой schema/reset/нового START1/повторного крафта/дополнительных ordinary fixtures. Больше запусков нет.

Исходная причина TASK100 layout устранена в диагностике: admission set фиксируется сервисом и выдаётся read-only; каждый фактический owner проверяется по этому set и текущим Geo/Peace/spacing/ownership. Пересчёт подвижных NPC из другой World-сцены не обязан иметь прежний count. Zone/Geo/actual-position checks не ослаблены. NPC context записан в census; native movement/observer guards production сохранены.

## Итоговые native receipts

START2-replay: types BUY50/MFG20/SELL130,181 unique IDs; START3 перед resale BUY49/MFG20/SELL131, после BUY50/MFG20/SELL130. Все200 имеют живой owned Player, private listing, Geo/Peace/spacing и source admission point. Final sharedPulses1/openings2/blocked0. Ordinary profile2 после каждого load сохранил READY recipes5/6, rank10 и прежние EXP/SP/level. Ordinary natural count0, promoted fixture count1. Градации/категории181 IDs отдельно не измерялись; S80 или добыча этих recipes из farm не заявляются.

Postflight:202 characters,2 profiles,222 components,200 durable offliners,1998 listing rows,20 craft receipts. SHA merchant components до/после рестартов `00c8cafb34005ca0019efbf78b7dc5ba1e252541ea842ba0120fd9d43c3eae5d`; final inventory `4659665014b175ab29fb6e3f35b9e7e5a7ae1e28d9bcbe7f7641f723ef6724ee`; offline listings `8b72bad2b41b32e6092fb4a84cbb0f658d8f0343be192be071829efee8f7dcb5`. Inventory изменён ровно реальными paid операциями/нормализацией stack; он не объявляется побайтово неизменным. Own SQL sessions0, native JVM0, listeners собственного JVM0, native lease cleanup0 remaining owners. Исходные private logs/authorities/RED/receipts сохранены, реквизиты/приватный stage path не публикуются.

## Изменённые файлы и read-set

Bounded exception:20 exact files для трёх прямо заданных TASK101 цепочек и их собственного safety/test/delivery; cleanup/Studio/Semantic changes отсутствуют.

- `java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java` — wiring.
- `java/org/l2jmobius/gameserver/phantoms/economy/PhantomOfflineMerchantService.java` — birth/hook/admission evidence.
- `java/org/l2jmobius/gameserver/phantoms/economy/PhantomMerchantCraftProvision.java` — one-shot qualification.
- `java/org/l2jmobius/gameserver/phantoms/progression/PhantomCraftRecipePolicy.java` — finite selection.
- `java/org/l2jmobius/gameserver/phantoms/progression/PhantomNativeCraftRecipes.java` — receipt/native readback.
- `java/org/l2jmobius/gameserver/phantoms/progression/PhantomOrdinaryCraftRecipes.java` — recipe-only reward.
- `java/org/l2jmobius/gameserver/phantoms/progression/L2jPhantomLearningJourney.java` — callbacks.
- `dist/game/config/Custom/PhantomMerchantCrafting.ini` — новый opt-in.
- `dist/game/config/Custom/PhantomOrdinaryCraftRecipes.ini` — новый opt-in.
- `test/java/org/l2jmobius/tests/phantoms/PhantomHeadlessPlayerTestEnvironment.java` — отдельная authority.
- `test/java/org/l2jmobius/tests/phantoms/PhantomCraft101Checks.java` — focused contracts.
- `test/java/org/l2jmobius/tests/phantoms/PhantomCraft101Native.java` — native campaign.
- `test/java/org/l2jmobius/tests/phantoms/PhantomTask101Lane.java` — sealed DB lane.
- `test/java/org/l2jmobius/tests/phantoms/PhantomTask101Authority.java` — pre-JDBC guards.
- `tools/phantom-runtime/Task101.ps1` — prepare.
- `tools/phantom-runtime/Task101Run.ps1` — owned orchestration.
- `tools/phantom-runtime/Task101DatabaseProbe.java` — create-only/read-only SQL proof.
- `tools/phantom-runtime/Task101Preflight.java` — initial metadata G0.
- `tools/phantom-runtime/Task101TextChecks.ps1` — two text checks.
- `docs/phantoms/reports/PHANTOM-CRAFT-MARKET-CLOSURE-101.md` — этот отчёт.

Прочитаны root/HighFive AGENTS, README, workflow/efficiency/master-plan bounded sections и весь TASK101 package; initial code read-set —12 seams SOURCE_MAP. Локальные аналоги:099/100 merchant receipts/native harness,068 paid native craft, existing materialization/learning/profile component CAS. Дополнительное узкое чтение по конкретным writer/safety причинам: ProfileRepository/Component, SkillTreeData/SkillLearn, PlayerClass/PlayerStat, RecipeList/RecipeCraftObserver/RecipeManager, ManufactureService, MaterializationService/NativeWorkScope/IdentityLeaseRegistry, pricing/NPC quote/Product/BuyListHolder, ItemContainer.restore/PlayerInventory/Item.restoreFromDb/OfflineTraderTable, character_skills/subclasses SQL и Ant targets. Большие Player/System/journey целиком не печатались. Narrow symbol searches превысили рекомендованные6 из-за all-writer/review/native diagnosis; повторного широкого G0 поиска крафтера не было.

## Проверки и telemetry

`M` — исходный HighFive, `S` — собственный stage, `SM=S/L2J_Mobius_CT_2.6_HighFive`. Native cwd=SM/dist/game. CP=S/build/phantom-test/bin;S/build/phantom-test/resources;SM/dist/libs/*. Конкретные private пути/UUID не публикуются.

| Команда | Фактический результат |
|---|---|
| `java -cp M/dist/libs/* M/tools/phantom-runtime/Task101Preflight.java M` |0; read-only G0 ABSENT/fullVisibility/sessions0 |
| `Task101.ps1 -Action Prepare ...` |первый exit1 до stage: TASK101 manifest — map, не прежний array; исправлено, итог0 |
| Focused `javac` + `PhantomCraft101Checks` |три behavioral RED: birth/creator/finite selection; GREEN3 contracts |
| Focused javac с исходным dist/libs |1; source JAR устарел относительно HEAD; переключение на полный shadow, не изменение dependencies |
| Shadow Ant `task101-build` depends compile-tests,jar |3 успешных full graphs:38s/38s/35s, final current production+430 test sources/JAR;5 прежних JDK warnings |
| `PhantomCraft101Checks`, TASK100 catalog/circulation на final JAR |0/0/0; DB-free, native claims не подменяют |
| Java wrong cwd/foreign flags/wrong SID/input SHA negatives |expected rejection до JDBC/World; bytes восстановлены |
| `Task101Run.ps1 -Action Create` |0; preflight/plan/create,121/203, only schema101 |
| `Task101Run.ps1 -Action Repair` |Ant0; первый reseal exit1 на CreateNew для существующего exact shadow Database.ini; INI сохранён, безопасный existing-equality path, reseal0 |
| Native START1 / causal1-replay |1/1; причины и достигнутые измерения выше; оба graceful drain |
| Harness-only javac + SQL continuation |0; paid rare/fee/BOM physically confirmed, current JAR unchanged |
| Первый START2 / exact SHA reconstruction / START2-replay / START3 |1/0/0/0; native Adena merge доказан полным старым SHA, не удалением assertions; обе завершающие JVM actual200/MFG20 и полный paid cycle |
| `OwnerConfig.ps1 -Action Verify` |исходник до/после68/287/changed0 |
| Protected source hash check |169 config/semantic/conversation files, drift0 |
| Две отдельные `Task101TextChecks.ps1` проверки |0/0; первые inline/single-quoted marker attempts имели parser error на типографской кавычке; итог UTF-8 BOM matcher с double-quoted dictionary |

Full `ant verify`, protected DB suites,5000 ordinary, soak, PvP/raid/city098, клиент и новая PLAYABLE release — NOT_RUN. CPU/p95/heap/GC/SQL-rate benchmark не проводился; попытка внешнего15s CPU sample обнаружила уже завершённый JVM и помечена NOT_MEASURED. Новая JVM ради метрик не запускалась. Три full Ant graphs,3 JAR,0 standalone jar/verify,2 harness javac corrections; focused101:3 RED/5 GREEN,100 catalog1/circulation2. Native World invocations5:3 RED/2 PASS; additional input-SHA denial произошёл до World.

Current и sameDB/sameJAR SHA256 `0c384fc37dce7b6f7466f4ef0765542a17f5534d027167f726569dc15195bd0b`. Source service `57fbda5cab0a7134d331f63432a8ff990f632566f5a3c6ced681d88a23209d2d`; provision `1e6af0bf3ed3657be11ed742412926773709b5915601de0173d7f5c00ed9509c`; native recipe seam `0ef126aa7e60aeb9a867cd19d64cdae8423373d879485117288533b3251651be`; ordinary service `b8e4d3a56e99e49d6c5d2ebf4a8eb172d39b0e0953b461b56346545b33a54b98`. Source Database SHA unchanged отдельно,169 protected files drift0.

Telemetry: доступный `get_goal` snapshot заморожен со статусом blocked на398336 tokens/2008s и не учитывает последующее прямое продолжение владельца; это не итоговая token usage. Wall clock от06:25:12 до07:59:37 UTC —94m25s до финальных проверок/публикации. Причина превышения ordinary cost orientation — independent review пяти safety/runtime findings и причинное закрытие native birth/rare/stack-merge gaps в одной owned DB. Точное последующее потребление tool не предоставил.

Independent review: один полный read-only pass и один targeted follow-up. Пять замечаний (ordinary self-drain, pre-JDBC authority, actual input seal, subclass readback, Create Item10) исправлены; follow-up оставшихся P1/P2=0. Последующие fresh-birth guard и две test-only rare/stack corrections подтверждены causal native/SQL evidence, без третьего review pass. Natural progression и native outcomes reviewer не подменял статическим PASS. Отложенных minor findings reviewer не выдал.

Mojibake-маркеры в изменённых файлах проверены: полный список,0. Технический matcher сам содержит этот список и исключается из самопроверки; user-facing matches отсутствуют.
Escaped Cyrillic в изменённых файлах проверены отдельно: все6 regex,0.

## Git, публикация и STOP

Baseline/current starting HEAD `e3b4a978a333ae280c922896c418cc2d9df04dd3`, branch feature/phantom-world. Fresh remote check совпал с baseline; чужие untracked tasks/reports/.phantom-local сохранены. Git использован по bounded root exception и явной TASK101 publication authority: `rev-parse --show-toplevel`, `symbolic-ref --short HEAD`, `rev-parse HEAD`, `status --short` (включая exact scope), `worktree list`, `for-each-ref` для feature/upstream, `ls-remote origin refs/heads/feature/phantom-world`, `diff --numstat/--check/-- <exact paths>`. Reviewer использовал только exact diff четырёх существующих файлов.

Final artifact scope guard: `git diff --cached --name-only` дал ровно20 перечисленных paths, foreign staged changes0; `git diff --cached --check` exit0 и `git diff --cached --stat` подтвердили text-only scope. Эти команды разрешены TASK101 для exact staged inventory/публикации. Обе текстовые проверки после финального отчёта exit0; исходный OwnerConfig Verify68/287/changed0.

Publication protocol: exact20-file `git add -- <allowlist>` (при уточнении отчёта повторно только его exact path), `git diff --cached --check`, `git commit -m "phantom(task-101): provision dedicated crafters and close market circulation"`, обычный `git push origin HEAD:feature/phantom-world`, `git rev-parse HEAD` и `git ls-remote origin refs/heads/feature/phantom-world` для remote/local SHA comparison. SHA delivery commit (содержащего этот отчёт) и фактический push result выводятся в финальном сообщении; собственный hash commit нельзя включить внутрь его же содержимого. Force/history/reset/stash/clean/StageAll не использовались.

После final postflight/report/normal push — STOP. TASK102/новая кампания/PLAYABLE/фоновое продолжение не запускаются.
