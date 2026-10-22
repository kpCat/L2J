# Кампания 068 — план и текущий журнал

Начало: 22.10.2026 05:57:24 UTC (08:57:24 Europe/Chisinau). Freeze реализации: 16:27:24 UTC; крайний срок: 17:57:24 UTC. После compaction часы не обнуляются.

Цель и полномочия: TASK.md, AUTHORITY.md, ACCEPTANCE.md, TEST_CASES.md, RUNTIME_INPUTS.md. Пользователь разрешил A–G одной кампанией, собственные среды, адресные commit и normal push только в feature/phantom-world. Промежуточные workflow approvals не требуются; обязательные platform approvals сохраняются.

## Проверенный baseline

- Checkout фактически `D:/Codex/repos/L2J-phantom-world`; ветка `feature/phantom-world`.
- Local HEAD и live remote HEAD: `b7e18670704d9b14661594b13d576df97a7ae770` (git ls-remote после network escalation).
- Исходный dirt: только входная папка пакета 068. Один checkout; других worktrees не создаём.
- Прочитаны root AGENTS.md, module Agents.md, workflow/package/release contracts, текущий status, bounded master plan/оба roadmap, пакет 068, актуальные части reports067/066R2.
- Дополнительные AGENTS выше root и в docs/task path, README.md, code-map/pattern-файлы не найдены. Module readme.txt существует и прочитан в релевантном объёме.
- Кодовая карта: supply policy/journey; StoreService/AutonomousMarketProducer; PrivateMarketPricingAuthority/Quote; NativeEquipmentPolicy; OfflineTraderTable; profile repository и canonical population creation; Build/Assemble/Runtime067.

## Архитектура и порядок

Используются Java25/Ant, существующие versioned components/receipts, native inventory/TradeList/PrivateStoreService/RecipeData. Новые merchant identities получают отдельную роль и не регистрируются как battle ACTIVE; stock offline restore — единственный загрузчик уже открытого merchant Player. Обычные population profiles и старые starter5000 права сохраняются. Никаких fake GameClient, второго inventory/экономической БД, внешнего runtime/provider или сторонних зависимостей.

Больше 10 связанных файлов разрешено AUTHORITY. Exact changed paths ведутся в итоговом отчёте; source scope ограничен High Five и адресным root ignore. Общие Player/boot/shutdown/build/launcher правит основной исполнитель; все Ant/native runs последовательны.

- [ ] A: общая бюджетная policy, bounded повтор после дохода, посильная цель/retreat, linked farm/supply/spoil.
- [ ] B: сохранить и проверить существующую background transaction/cursor модель; конечный merchant bootstrap с durable server quota и provenance.
- [ ] C: новый narrow merchant adaptor, admission batches/общая due rotation, native BUY/SELL, 200 stores и sameDB restore без duplicate owners.
- [ ] D: configured manufacture service fee, native self/private craft, evaluator разных ролей и native mid/high upgrade.
- [ ] E: общий valuation/эпики, reachable Alt+B graph, CLI Plan/Apply/Rollback и bounded cycles.
- [ ] F: независимый исполнитель champions_f; categorical tiers, compatibility, focused/native evidence; бюджет около 45 минут.
- [ ] G: независимый исполнитель rift_g; personal admission и owner/timer/terminal lifecycle; бюджет около 90–120 минут.
- [ ] SAFE/DELIVERY: собственные068 manifests/БД/порты/Geo, exact committed build, составная ordinary Main сцена и restart, review, штатный STOP, отчёты/roadmap, commit/push/live SHA.

## Фокус проверки

1. Один identity/objectId/Player и один writer при restore, listing, native сделке и shutdown.
2. Конечный bootstrap не повторяется после restart/смены роли/удаления identity; merchant не получает starter5000.
3. Количество/Adena/fees/rounding/MaxAdena и uncertain retry не дублируют результат.
4. Обычные реальные offline traders, красные чемпионы и Party2/Rift сохраняют прежние правила.
5. Late timer/death/logout не создают новую комнату, spawn или движение после terminal cleanup.

## Текущий статус

Read-first завершён для первоначального плана; адресное чтение implementation call trees продолжается перед каждой правкой. Runtime/Geo/БД/Alt+B/200 stores ещё NOT_RUN. Network sandbox первоначально отказал git ls-remote; разрешённая escalation прошла, remote подтверждён. Защищённые runtime/схемы не запускались и не менялись.

## Первый engineering checkpoint

- Подготовлены fresh TEST/release068: по 124 таблицы, 121 stock script, aggregate `2CD503128136E3E854F9EFF24236DD9F286CA58D9E950ACB9B2842E78B910E28`. Private credentials не выводились. Источник067 JAR exact;203 Geo-региона физически скопированы и проверены SHA. Новые тройки2249/7949/9249 и2149/7849/9149 были свободны.
- Новый PhantomTask068Lane использует exact068 root/config/schema/owner; старые guards не ослаблены. Mutable blanket fixture cleanup по068 запрещён.
- A: подтверждён разнобой NPC/private reserve; общий `availableBudget` защищает route fees/earmark до desired reserve, nearby partial supplies сохраняются. Policy checks PASS (3 группы). Первый compile новой API был ожидаемым отсутствием функции, не product RED. Native causal A пока NOT_RUN.
- F: policy2/2, native2/2; настоящий baseline067 aura-reset RED и GREEN. Последняя config/test правка требует final integrated rerun без isolated override. Подробности F_HANDOFF068.md.
- G: DB-free2 группы PASS; native R3 exit0 (solo/fee/jump/exit, Party2, ownership/terminal/respawn/invitation/death/restart/leave/reentry). R1 private-policy ACL и R2 отсутствующий teleport ACK — fixture failures, не product RED. Два fixture fixes; штатный ThreadPool/Hikari close. Final committed rebuild/rerun ещё required.
- Integrated dev Ant `jar compile-tests`: PASS28s, PASS31s, PASS30s; только два прежних System.runFinalization warnings. Производные private build не являются final committed delivery.
- E/D shared valuation/CLI передан бывшему F helper, согласован public `quoteItem(int): ItemQuote(itemId,priced,bidAllowed,bid,ask,fair,sourceReason)` и `manufactureFee(recipeId,productId)`; parent не правит pricing/config market параллельно.
- C/B design refinement: narrow merchant adaptor использует existing profile components/native inventory/OfflineTraderTable. Merchant records не получают population/starter components, не регистрируют battle scheduler jobs. Existing native restored Player сначала переиспользуется из World; повторный load запрещён при уже существующем owner. Общий durable quota живёт отдельно от merchant identity, не удаляется при её закрытии/замене. Exact models/validators пишутся до integration.

Текущий статус всей кампании OPEN. A–E ещё не закрыты; F/G dev evidence не заменяет final JAR. Следующий шаг — merchant models/quota/native owner и связанная экономика; доставка готовится независимо.

## Checkpoint 07:30 UTC — экономика и настоящие лавки

- Уточнение владельца по Spoil применено к существующей LootDispositionPolicy: unknown/shared/production/market-valued D/C ресурсы сохраняются; давление inventory не отменяет запас. Дешёвая матрица шести случаев и275 реальных D/C key parts PASS. Настоящий baseline067 RED на item4055; полный склад не включает автоматическую распродажу ресурсов.
- Merchant bootstrap имеет durable server quota и отдельные versioned role components в существующем repository; atomic reserve до выдачи, PENDING после неопределённого restart закрывает повтор. Новые роли без starter/population components, без battle/materialization регистрации. Source switch по умолчанию OFF.
- Native малый запуск: первоначальная fixture ошибочно загрузила SpawnData до MerchantPriceConfig; отсутствующие exact NPC quotes исключили BUY. После исправления по локальному POST001 аналогу —14 непустых лавок (сохранились14 identities первого запуска), реальные BUY/SELL и30s PASS.
- Исправлены общие причины: seed одного владельца ограничен долей пожизненного stock envelope; transient listing failure сохраняет due rotation; адресная native owner-проверка не отключает dedicated merchant при последней продаже. Обычный stock offline path сохраняется.
- Native ramp50 PASS; затем200 настоящих непустых stores достигнуты. Первая200-сцена распродала последнюю позицию, создала конечную замену201 и ошибочно ожидала неизменную квоту — это fixture assertion, не скрытый replay. Сцена исправлена для частичной покупки.
- Новый JVM той же TEST068 БД:200 непустых stores,201 durable/native owners (один depleted),201 leases,0 combat/materialization owners,251 Geo/town/NPC/door/arrival-validated slots. После native BUY/SELL и30s квоты неизменны; merchant-native-200-restored.log PASS. Все JVM завершились через ThreadPool/Hikari close. Это dev component evidence; ordinary Main/final committed JAR ещё required.
- E: общий source valuation, epic-source/premium policy и CLI Plan/Apply/Rollback. Java pure3/3, CLI3/3; x10/x20 планы по53 PRICED/46 UNPRICED, illustrative Apply запрещён. E_HANDOFF068.md фиксирует границы. Native Alt+B/final apply ещё NOT_RUN. Для raw materials исследуется общая причина unresolved dynamic NPC quotes; unknown пока не продаётся автоматически.
- A: helper подключил existing combat estimator к real current loadout/HP/MP/target/champion viability и bounded replan. Pure2/2 и прежний background-model10/10 PASS, native common scene ещё required.
- D: helper расширяет существующий NativeEquipmentPolicy actual learned equipment masteries/set compatibility; pure2/2 PASS. Native craft/equip ещё NOT_RUN.
- B: подготовлена отдельная own068 component-сцена через существующие production catalogs/native capture, реальную минуту history, canonical transaction/retry и World handoff; ещё не запускалась. Она не выдаётся за ordinary PopulationManager/ecology proof.
- Сборки dev продолжаются последовательно; committed-source delivery, standalone Main, final review/STOP/reports/commit/push ещё OPEN. Исторические runtime/БД и отчёты не менялись.

## Диагностика B и корректировка доказательства, 07:45 UTC

Применён systematic-debugging: сохранены stdout/exit codes, выполнено только read-only чтение собственных компонентов. B1 attempt1 остановился до baseline на unmapped topology area (null node). Attempt2 сохранил native baseline, но загруженный после незавершённого setup actor оставался corpse; persisted state DEAD, cursor не двигался, rewards не начислялись. Дополнительно ручной предварительный goal не был связан с initial historical request (goalId0/PENDING). Это invalid component fixture, не product RED; данные Bg068B1/profile2 сохранены без reset/grant/удаления.

Незавершённый экспериментальный test source убран из поставляемого scope после побайтового сохранения вне module в .phantom-local/task068/PhantomBackground068Native-experiment.java (SHA проверен). Доказательство B переносится в штатную PopulationManager/ecology/bootstrap цепочку вместе с A. Новые имена того же неудачного эпизода не создаются. Независимые D native craft и E source-quote correction продолжаются.

## Checkpoint 08:30 UTC — составная native экономика

- D native R3 exit0: четыре архетипа, 28 строк NONE..S80 metadata, реальные оплаченные B/S80 weapons и B armor, native self-craft и manufacture рецепта1 (4 Stem+2 Iron Ore→500 arrows, MP30, service100). Полный S80 armor set не заявлен: одиночная chest слабее имеющейся full-body; отказ сохранён. D_HANDOFF068.md и equipment-craft068.tsv содержат exact receipts.
- C составная R3 exit0: после stock restore202 unique native owners/leases,201 непустая лавка включая adopted existing native manufacturer; adoption выдал0 capital/stock. Настоящие платный manufacture, SELL/BUY, race двух покупателей последнего стека (committed1/2), pre-effect owner rejection без pair callbacks и ordinary offline close прошли. Квоты20.1m capital/499927988 stock value не выросли.30s sample CPU515625000ns/wall30032100100ns, heap2882019904bytes,122threads, global SQL Questions delta1 (не isolated DB-write benchmark). Log merchant-native-manufacture-race-r3.log.
- Две причины C fixture: загруженный customer с persisted HP0 не был native revived; затем повторный spawn уже существующего customer вызвал штатный duplicate rejection. Исправлены только premeasurement customer preparation и размещение через isSpawned; identities/квоты не сбрасывались. Предыдущие failed receipts сохранены.
- Reviewer нашёл OwnerObserver gate после durable pair beforeMutation. Gate перенесён перед pair callback; native regression подтвердил pairCallbacks0/items+funds unchanged. E supplier теперь читает текущие spawned World Merchant instances; новый focused native mode economy068-live-npc ещё NOT_RUN.
- Полная A–B factory выявила старый source pin Kamaloka из067. Current source и own copy rawSHA совпадают; git show2939b7054ca подтвердил067 personal admission changes, все остальные записи supported-content catalog совпали. Обновлён только expected canonical UTF8 hash Kamaloka f624..→f2100ed10a36b4ae9f8ad0caa9a09371eb7592acad56f65174bfaf776199e22b. Сам verifySource не ослаблен; исторический runtime не изменён. После этого штатные PopulationManager/ecology создали8 READY профилей и нормально остановились. Native causal A/B ещё OPEN: read-only диагноз planner prerequisite на тех же identities, без третьего mob/ручного goal/cursor.
- Общий Ant jar+compile-tests после market guards/Supplier/source pin: exit0,35s,2355 product/323 test source, два прежних runFinalization warnings. Последующие test-only scene правки пока isolated javac; final committed build ещё required.
- Delivery assembly включает standalone price CLI+README в exact TOOLS manifest и явный own profile: Phantom masterTrue, population8/active8/cap8, starter shots0, native merchants200, custom Alt+BTrue. Personal QoL/blue champions остаются shipped OFF. Passive observer получил read-only offlineMerchantRole snapshot.
- Следующие независимые проверки: E actual1/1 Plan/Apply/native Alt+B/Rollback; manufacture STOP admission/pending craft; A/B canonical continuation. Финальные committed JAR/Main/restart/review/STOP/commit/push остаются OPEN.

## Checkpoint 09:05 UTC — проверка общих владельцев и переход к Main

- E actual TEST1/1: Plan дважды SHA276d6381ed5583dd3462316dec5ca4b48b2ce5f949faf2273d88fe833b7122ea, Apply5XML, реальный зарегистрированный HomeBoard→socket-backed GameClient→MultiSellChoose списал3428774 Adena и выдал119×1/854×2/886×2. Rollback вернул5точных preimage; native prepared price250000 подтвердился без покупки/денег. ReApply оставлен для следующих общих TEST-сцен. Первоначальный rollback-check отказал загруженному corpse из createDb HP0; только fixture native revive/fullHP/MP до quote, исходный failure сохранён. Native runtime rate/chance не менялись.
- Дополнительный E native economy068-live-npc: same authority видит отсутствие→unspawned→current→wrongWorld→despawned→current→removed Merchant,1/1 PASS, exit0, inventory/Adena writes0; все пулы закрыты.
- STOP реальный RED: после beginStop ManufactureService принял новый native заказ, перевёл100A/MP30 и продукт500. Исправлены existing native service admission под manufacture-map lock, actual RecipeManager pending query, parked-owner eligibility/refresh/STOP. Новый owner record сохраняет тот же exact Player/service. Shared native callback не берёт mutex merchant service; порядок SELL→BUY→MFG проверен независимым reviewer.
- GREEN queued ALT craft: настоящий ALT_GAME_CREATION до measurement, native pending удерживает STOP/owner, после terminal новый manufacture отвергается до эффекта, events0; exit0, штатный Hikari/ThreadPool close. Log manufacture-stop-pending-GREEN.log. Обычный/private craft остаётся RecipeManager authority; unlisted recipe теперь отклоняется на packet-facing service.
- Operator STOP принимает identity/deadline один раз и ждёт native drain без отзыва уже принятого запроса из-за expiry. General normal Shutdown при недренированном craft не выполняет persistence/disconnection/System.exit;20s не ограничивает ожидание Java monitor/DB storeMe — это операционная граница, не обещание deadline всех native методов.
- Общий Ant R1 выявил ошибочно поставленный return в saveData; исправлено адресно. R2 jar+compile-tests exit0/35s/324tests, GameServerSHA C94966E1E5AFAE7E9407B604357D8936380A1EF602F684484030DE1B5CE2E60D. Последующие два merchant admission/initial HP и tool snapshot/guide edits требуют следующей общей сборки.
- A helper final R7:8канонических READY/эcology сохранены (profiles3..10, выбран10/268481943), но измерение не началось.115passive NPC/57mapped Geo anchors, ни одной точки с merchant И warehouse одновременно≤12000; minimum17961.18. Это дополнительное условие helper, не product viability failure. Больше R8/подбора/новых имён/goal/cursor/добавления HP нет; A_NATIVE_HANDOFF068.md сохраняет R1–R7. На обычный Main переключаемся отдельно в обязательной интеграции.
- TEST Main для same8 требует точный PhantomPopulationTimeZone=-03:30 из actual pending (не -03:15 старой failed-beforebirth попытки): timezone входит в initialization contract. FRESH/age0,8/8/8,starter0 и passive AUTONOMOUS origin можно задать существующей character-row выбранного Phantom без Player.load/coord updates. Имя/XYZ пока требуют узкого read-only lookup. AutoAttach names только собственный exact disabled-account character, не owner/user allowlist.
- Независимый C review: новых critical/P1/P2 не найдено; STOP expiry P2 исправлен; Kamaloka canonical pin сверён повторно. P3 о неверном delivery prose исправлен в assembly guide/DELIVERY_HANDOFF. Новый read-only stopped snapshot action DatabaseProbe нужен для exact identities/items/quota hashes и lookup managed positions; ещё compile/runtime NOT_RUN.
- Остаются: committed-source build, final native impacted checks, standalone TEST+release assembly/actual candidate pricing Apply, ordinary Main/sameDB restart/useful action, truthful A–G report/roadmaps, сохранность входов, финальный STOP и normal feature push. Кампания OPEN; нет claim CORE/FULL PASS.

## Уточнение владельца и precommit, 09:30 UTC

- Владелец уточнил итоговую политику Alt+B: настоящая TEST-покупка разрешена, итоговый candidate должен сохранять исходные цены по умолчанию. Поэтому дальнейший candidate Apply отменён; CLI/план поставляются отдельно. Source phantom purchasing search не обнаружил вызовов HomeBoard/MultiSellChoose; независимый A/B reviewer проверяет stock commerce path. Измерительный TEST остаётся с уже доказанным Apply до своего штатного завершения; его XML не копируются в candidate.
- Starter R2 code/entitlement не менялись. Existing DB-free commerce-decision прошёл, включая пять1000-батчей до5000, encode/decode pending, no sixth/replay/advanced-grade. Assemble guide явно называет0 настройкой измерительного068 профиля;5000 выбирается до рождения новых боевых identities. Merchant roles не получают starter component.
- Дешёвая ресурсная матрица дополнена actual stock no-grade Short Sword как ordinary-junk control: SELL_NPC category, при этом evaluate сохраняет equipped/needed. PASS:275 реальных D/C parts,6surplus cases, ordinaryJunk1, DB_WRITES0.
- Precommit pure: champion068-policy2/2, economy068-pricing3/3, equipment068-policy2/2, combat-viability068 2/2, background-model10/10, commerce-decision PASS. Live feature HEAD повторно b7e18670704d9b14661594b13d576df97a7ae770; новый remote descendant отсутствует.
- Scope115 source/text files внутри068/module+.gitignore; отдельные полные mojibake/escaped Cyrillic проверки0, whitespace0. Это precommit snapshot, не финальный scope после следующих правок.
- A review нашёл новый общий дефект: EngagementController busy-return до kill/deadline и VisibleAutoPlay renewal от мелких damage/farmCycle. Узкая правка с focused regression перед ordinary Main, без новой навигации/персонажей/ручных goals. Отдельный finite release-maker helper готовится вне module; Main не зависит от его завершения.
