# TASK068 — независимое bounded review C и связности поставки

Агент champions_f, 22.10.2026. Только чтение production/test/runtime sources; единственная запись этого review — данный файл. Ant, native JVM, БД, git-команды и production-правки не выполнялись. Это source review до final codeSHA/native GREEN, не подтверждение готовой поставки.

## Вывод

Новых critical/P1/P2 дефектов в проверенных изменениях C не найдено. Ранее обнаруженный P2 истечения принятого STOP исправлен в исходнике. Остаются один P3 о документации и указанная ниже граница timeout; actual final build, START/STOP и sameDB restart должен подтвердить основной исполнитель.

## Finding P3 — описание enabled profile устарело

`tools/phantom-runtime/Assemble068.ps1:78–84` явно включает EnablePhantomSystem, EnableOfflinePhantomMerchants и CustomCommunityBoard в собственной поставке, задаёт population8 и starter lifetime0. При этом `docs/phantoms/tasks/PHANTOM-LIVING-ECONOMY-068/DELIVERY_HANDOFF068.md:32` утверждает, что switches автоматически не включаются; шаблон START_HERE в `Assemble068.ps1:103` также говорит «Исходные switches сохранены». До финальной доставки описание следует согласовать с фактическим профилем. Blue и PersonalQoL отдельными этими строками не включаются.

## Проверенные контракты C

- `PhantomOfflineMerchantService.java:155–188,220–228`: finite ledger reserve и role component записываются одним mutateComponentsAtomically с rowVersion CAS. Capital/stock/per-item/lifetime limits хранятся в durable ledger, не пересоздаются из новых config defaults. Per-identity stock cap разделяет envelope между lifetime identities.
- `PhantomMerchantBootstrap.java:24–34,47–49` и `PhantomOfflineMerchantService.java:231–234`: PENDING после неопределённого рестарта становится INCONSISTENT. Такая роль не получает повторный bootstrap; остаток quota не возвращается. Это консервативная остановка, а не автоматическое восстановление частичного grant.
- `PhantomOfflineMerchantService.java:191–216,265–280`: prepared adoption требует exact access0, disabled account(-1), отсутствие linked Phantom profile/session/headless/party/native work/identity owner и pending store/transaction. Prepared и resume теперь используют RecipeManager.isMakingItem, включая queued ALT, вместо одного Player.isCrafting. Prepared role резервирует ordinal с capital0/stock0.
- `PhantomOfflineMerchantService.java:271–296`: World Player переиспользуется до Player.load. Повторное здоровье и seed допускаются только в CHARACTER при not-restored, пустом inventory и Adena0; до native effects сохраняется PENDING. doRevive/fullHP/MP здесь инициализируют незавершённое рождение с HP0. READY не лечится и повторного grant не получает.
- `PhantomOfflineMerchantService.java:307–335,541–569`: owner map содержит exact Player/service record; NATIVE_OWNERS — ConcurrentHashMap. Lease + exact World instance исключают параллельного нового actor. При раннем отказе admission существующий ordinary offline Player сохраняется, lease освобождается. Явное удаление освобождает только matching native owner/observer/lease, обычные магазины не удаляет.
- `PhantomOfflineMerchantService.java:378–430,490–503,541–553`: refresh/STOP используют SELL → BUY → MFG. ManufactureService.java:72–80 держит native manufacture-map lock до requestManufactureItem; queued ALT регистрируется в _activeMakers до выхода из lock. Sync craft завершается под этим lock. STOP ставит volatile stopping до drain, запрещая новые merchant сделки/крафт; старое принятое native work завершается штатно.
- `RecipeManager.java:92–95,125–135,468–474`: isMakingItem учитывает Player.isCrafting и native _activeMakers; удаления marker происходят после native fee/ingredients/reward effects. Ordinary manufacture без NativeOwner сохраняет admission=true; fee/MP/ingredients/chance остаются RecipeManager.
- Новые beforeTransaction/permitsNativeManufacture callbacks не захватывают глобальный mutex merchant service: owner lookup/lease-kind lookup concurrent, stopping volatile, pricing instance supplier. Обратной цепочки MFG → SELL/BUY в проверенных изменениях не найдено. Existing private-store owner gate предшествует durable pair observer.

## Исправленный STOP P2 и timeout

`commons/util/LocalRuntimeShutdown.java:129–151`: requested/dispatched разделены. Exact PID/runtime request проверяется и atomically переносится accepted один раз; pending accepted request затем проверяет только drained в том же JVM. Deadline больше не отменяет уже принятую authority. Дополнительного timer/pool нет; старый overload делегирует drained=true.

`Shutdown.java:347–353,155,161`: false после drain deadline не запускает System.exit и не начинает offline bulk persistence/disconnection. Operational boundary: эти 20 секунд проверяются между возвратами beginStop; ожидание самого synchronized monitor или storeMe внутри beginStop не имеет отдельного timed acquisition. Это не найденная новая lock inversion, но точный wall-clock bound этим исходником не доказан.

## Resource policy

Прочитаны `PhantomLootDispositionPolicy.java`, relevant maintenance plan/native revalidation в `L2jPhantomSupplyJourney.java` и valuation bridge в `PhantomSystem.java:469–473`.

`PhantomLootDispositionPolicy.java:83–89` не объявляет ресурс дешёвым по grade/NPC price: unknown valuation, common/recipe products, multi-product inputs, B+ demand, observed production demand или fair>NPC сохраняются MARKET_RESERVE. Own recipe inputs/needed items/actual equipment upgrade сохраняются в evaluate. `L2jPhantomSupplyJourney.java:370–374` проверяет warehouse capacity; при полном warehouse отсутствует fallback conversion в NPC sale. Перед native execution policy вычисляется повторно (`:744–745`). Opportunity-only pricing fallback не становится reliable source: общий quoteItem возвращает UNPRICED; bridge использует quote.priced.

Граница: recipeRoles — статический BOM classifier; observed market demand здесь отражает active BUY listings, не доказанную общую модель всех производственных планов. В неизвестном состоянии policy выбирает сохранение ресурса.

## Source pin и delivery provenance

`dist/game/data/phantoms/quests/high-five-supported-content-v1.xml:27` Kamaloka owner pin равен `f2100ed10a36b4ae9f8ad0caa9a09371eb7592acad56f65174bfaf776199e22b`. Независимый read-only SHA пересчёт script UTF-8 с canonical CRLF/CR→LF дал то же значение. Raw script SHA-256: `DA8023FB03E9BF86215BD8A23B46585261679A6B23D5D60A5EB77F8831A45137`. Это разные корректные представления hash; verifySource/PhantomUtf8SourceHash проверяют canonical form и не ослаблены.

Прочитаны Build-Task068.ps1, Assemble068.ps1, Runtime068.ps1, Task068Common.ps1 и DELIVERY_HANDOFF068.md. Build требует exact committed HEAD/branch/clean module и archive; assembly перепроверяет archive/tree/test/JAR hashes, exact own roots/schema/private inputs и203 physical Geo hashes. Runtime использует свои tools/JAR/provenance receipts; исходный checkout для START/STATUS/STOP не читается. Caches source metadata не заменяют final source/JAR identity. Own runtime pricing CLI находит runtime libs относительно GameRoot, поэтому его default classpath не требует source checkout.

Этот review не запускал эти scripts, не проверял final codeSHA и не выдаёт source inspection за ordinary Main/native результат. Final candidate Plan1/1/Apply необходимо сформировать заново из committed JAR и exact candidate root; dev native-test receipt на candidate не переносится.

## Read-first и проверки

Переиспользованы существующие patterns native ManufactureService/RecipeManager, PrivateStoreService owner observer, profile component CAS, identity lease registry, stock offline restore,067 delivery contracts. Изучены relevant TASK068 ACCEPTANCE/TEST_CASES и текущие handoffs. Архитектура Java25/Ant/stock Semantic Engine сохранена; review не расширял runtime и не менял владельцев shared files.

Mojibake-маркеры в новом review файле проверены отдельно: полный набор,0 совпадений. Escaped Cyrillic/XML escaped Cyrillic проверены отдельно: все6 regex,0 совпадений.
