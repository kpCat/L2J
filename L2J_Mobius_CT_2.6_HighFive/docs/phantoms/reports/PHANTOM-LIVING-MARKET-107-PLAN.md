# TASK107 — план выполнения и точный scope

Статус: **PLAN_APPROVED** владельцем в текущем чате; выполнение начато, native107 NOT_RUN.

**Goal:** выполнить живой рынок по TASK107 и всем companion-файлам, сохранив существующие identities, роли, рецепты, квоты и protected данные.

**Spec:** `../tasks/PHANTOM-LIVING-MARKET-107/TASK.md` и все companions пакета.

**Architecture:** расширить существующие MerchantCatalog/OfflineMerchantService и native stores. Сохранить canonical pricing/enchant expected cost, общий pulse и component CAS; новые grant receipts отделить от неизменяемых Bootstrap/Craft v1.

**Tech stack:** Java25, Ant, штатная MariaDB OWN102, PowerShell OwnerConfig. Новых зависимостей, worker/timer/engine нет.

## Проверенный baseline

Local HEAD, tracking и fresh live origin feature/phantom-world: `795b160b9dc7ffb4005be577941080286edf3aef`. Чужой untracked dirt сохраняется. GitHub read-only проверка в sandbox не получила сеть; разрешённый повтор вне sandbox подтвердил remote. G0 preliminary: JVM0/TCP3308 established0, прежние authority directories существуют. Полный SQL/authority/preimage G0 ещё требуется.

ROOT_BUY_63: production buyPlan использует `budget / (linesPerStore - lines.size()) / bid`, где linesPerStore20, а не число реальных кандидатов. Отчёт106 содержит три9546/9547/9548 по12500 и wallet100000. Собственный focused RED ещё не запускался.

## Exact allowlist

Все пути ниже относительно High Five; исходный scope16 файлов — bounded exception одной зависимой market feature. После independent causal audit он уточнён двумя необходимыми ближайшими путями ниже: canonical pre-effect enchanted valuation и существующая изменяемая catalog regression. Это не новый subsystem/engine.

1. `java/org/l2jmobius/gameserver/phantoms/economy/PhantomOfflineMerchantService.java`: allocator, safe rotation/role fallback, вызов finite provision.
2. `java/org/l2jmobius/gameserver/phantoms/economy/PhantomMerchantCatalog.java`: полный universe, typed classification, sampling без общего128 cutoff.
3. Новый `java/org/l2jmobius/gameserver/phantoms/economy/PhantomMerchantMarketPolicy.java`: отдельные проверяемые market настройки, defaults и bounds.
4. Новый `java/org/l2jmobius/gameserver/phantoms/economy/PhantomMerchantMarketLedger.java`: отдельные bounded durable grants, PREPARED/COMMITTED/INCONSISTENT, frozen limits и codec.
5. Новый `java/org/l2jmobius/gameserver/phantoms/economy/PhantomMerchantMarketProvision.java`: exact owner/token, CAS intent, native inventory writer и SQL readback; unknown delivery не повторять.
6. При необходимости `dist/game/data/phantoms/merchant-market-classification.tsv`: только проверенные source-backed ID-классификации для неоднозначных pieces/general materials; без name regex и широких ID ranges.
7. `dist/game/config/Custom/PhantomMerchants.ini`: новые market-specific ключи и комментарии; существующие owner значения сохранить.
8. Новый `test/java/org/l2jmobius/gameserver/phantoms/economy/PhantomLivingMarket107Checks.java`: focused DB-free regression/contracts.
9. Новый `test/java/org/l2jmobius/tests/phantoms/PhantomLivingMarket107CatalogAudit.java`: full native-source audit и sorted TSV.
10. Новый `test/java/org/l2jmobius/tests/phantoms/PhantomMerchant107Native.java`: actual stores/price/stock/World/lease доказательства.
11. `test/java/org/l2jmobius/tests/phantoms/PhantomPopulation102Native.java`: отдельная107 lane и frozen pre-STOP evidence.
12. `test/java/org/l2jmobius/tests/phantoms/PhantomTask102Lane.java`: отдельные107 receipts/allowance.
13. Новый `tools/phantom-runtime/Task107Run.ps1`: exact G0, одна fresh physical stage107, seal, A/clean STOP/B/clean STOP; никакого force action.
14. `docs/phantoms/reports/PHANTOM-LIVING-MARKET-107.md`: точные команды, gates, ограничения и aggregate evidence.
15. `docs/phantoms/reports/market107-universe.tsv`: sorted item classification/source/price/status audit.
16. `docs/phantoms/reports/market107-coverage.tsv`: отдельные candidate/eligible/priced/funded/in-stock/offered/OPEN counts.
17. `java/org/l2jmobius/gameserver/phantoms/economy/PhantomPrivateMarketPricingAuthority.java`: чистый template/enchant quote через существующий canonical route, до reserve grant; actual SELL ownership guard сохраняется.
18. `test/java/org/l2jmobius/tests/phantoms/PhantomMerchant100Checks.java`: существующее ожидание общего128 cutoff заменить проверкой полного universe, сохранить interleave/circulation contracts.
19. `test/java/org/l2jmobius/tests/phantoms/PhantomSkeletonSuite.java`: exact patch отдельно разрешён владельцем после active stop gate; OFF fixture и read-inert canonical owner config. Рабочий PhantomPlayers.ini не меняется.

Этот plan-файл — review artifact, отдельное исключение к16 delivery paths; task packages не редактировать. При необходимости другого production пути сначала обновить scope с причинным обоснованием.

## Порядок и gates

- [x] G0: Task107Run Audit exit0; exact OWN102 authority/SID/source hashes/schema/full visibility, foreign0/JVM0/SQL0/TCP0, два стабильных read-only preimages; protected pins сохранены, OwnerConfig скопирован в private evidence. Отдельный census/проверка287 tuples впереди.
- [ ] Focused RED: реальный BUY allocator должен воспроизвести starvation100000/20/12500 при3 кандидатах. GREEN: минимум1 каждого, reserve<=wallet; границы0/12499/12500/slots1/overflow/duplicates/stale/native limit.
- [ ] CLASS/PRICE: полный ItemData/RecipeData audit, B/A/S/S80/S84 разделены; D/C pieces и recipes deny precedence, общие материалы D/C доступны. Неопределённые ID явно UNRESOLVED_CLASSIFICATION; missing source не выдумывать.
- [ ] FUNDS/STOCK: finite отдельные grants с frozen caps/CAS до effects/native+SQL readback; crash/uncertainty blocks replay. Existing inventory first; gear +0/>0 только actual native items с OWNER_PROVISIONED происхождением.
- [ ] ROTATE: poll30s сохранить, отдельный refresh1800s; deterministic bounded sampling; observer/customer/pending work defer; BUY↔SELL по actual stock/budget или законный IDLE. MFG six recipes сохранить.
- [ ] OWNER: source Verify, old287 tuples unchanged; public new keys сохранены обычным механизмом, stage Apply→Verify→Apply changed0→Verify. Save нужен только при новых отклонениях от managed defaults.
- [ ] Focused/affected GREEN и independent read-only review до native; build-only scratch отдельно, source build/dist не изменять. Generic ant verify запрещён.
- [ ] Только затем одна fresh sealed shadow107 на existing owned102 DB. A cap16, >=200 actual OPEN, BUY/SELL/MFG, два native samples>=30s, SQL/World/leases/quotes/inventory/telemetry. Paid customer transaction только при законном доступном customer, иначе NOT_RUN.
- [ ] A natural exit0/JVM0/SQL0/TCP0 и durable postflight обязательны перед B. B sameDB/sameJAR, exact IDs/receipts/stock/capital continuity; второй natural STOP. Retained JVM: STOP/BLOCKED, никакого auto-force.
- [ ] Independent final review P1/P2=0, exact scope/text/diff checks, честный report; exact-file commit и normal non-force push feature/phantom-world с remote equality. TASK108 не запускать.

## Review focus и ограничения

Writers: общий merchant pulse/refresh/open, native trade/craft callbacks, Player/inventory persistence, offline realtime/bulk writers, Profile component CAS, ordinary background/reward/logout, shutdown/ThreadPool. Сохраняется service→sellList→buyList→manufactureItems; отдельный Player/Item monitor не объявляется полной serialization boundary.

OfflineTraderTable и native Item persistence подавляют SQL exceptions: invocation не доказывает persistence; нужен readback. PREPARED после crash нельзя повторять автоматически. Independent review выявил P1: PREPARED/INCONSISTENT обязаны также карантинить владельца перед refresh/open и в nonblocking beforeTransaction; восстановить карантин перед adoption. P2: +n stock нельзя резервировать по +0 quote; добавлен canonical pre-effect valuation seam. Эти требования включены в реализацию и focused negatives. Stale price generation, owner/token/World drift, quotas exhausted, unsafe source и visibility обязаны fail closed. STOP evidence frozen до остановки knowledge supplier.

## Progress ledger

- G0107 PASS: Task107Run Audit exit0, read-only SQL/OS; свежий private archive вне checkout, SQL writes0. Preliminary sandbox OS/private reads были denied; разрешённый exact read-only повтор прошёл.
- Build-only scratch:28843 files, Database.ini отсутствует, не native stage107. Ant compile-tests exit0/28s;5 существующих compiler warnings.
- ROOT_BUY_63 RED: exit1, actual=[] на прежнем allocator. GREEN после исправления exit0,3 позиции/reserve100000,11 focused cases. Проверка выполнялась без БД/native actors.
- Ruling: единственный coordinator выполняет implementation inline; independent reviewer read-only. Новые git worktrees не создаются: source checkout текущей feature-ветки, builds/native изолируются физическими shadow согласно TASK107.
- Уточнение владельца: богатый рынок и будущие изменения бюджета через SaveOwnerConfig. Новые стартовые defaults50млн/tranche,250млн/identity,10млрд/global; old Bootstrap100000 неизменён. Stock global10млрд/identity500млн; это новые starting defaults, не прежние owner overrides.
- Source OwnerConfig Verify exit0:68 files/287 overrides/conflicts0/changed0.
- Catalog200→128 RED exit1; GREEN200 и affected100 catalog PASS. Native XML audit19200 items/1000 recipes/724 exact mappings/unresolvedCraft0. Reviewer NPC-gear defect RED→GREEN; допускается native gear без craft output с обязательным source quote.
- Grants reserve RED→GREEN:250млн finite reservation, cooldown/global/per-owner/item caps, sequence replay/codec/quarantine flag. Native writer/callback proof ещё NOT_RUN.
- Reviewer P2 unsold stock emission и interval change1800→3600 воспроизведены RED; focused GREEN для reuse-first category deficit и восстановленного из receipts epoch. Lifetime/unknown usage не сбрасываются.
- Pre-native source audit:63818 native drop facts,3638 declared enabled NPC IDs,9649 item source evidence; actual rates8/10/10/20, native spoil sensitivity PASS. Static candidate source fair явно отделён от unresolved runtime spawn/tax corridor. NPC/skills XML loader предупреждает об отсутствующих effect handlers в DB-free audit; combat/effect correctness этим аудитом не подтверждается.
- Harness unit66/66 PASS. Skeleton первоначально14/15: historical `config-canonical-disabled` требовал OFF вопреки owner True. TASK107 TEST_PLAN11 stop gate соблюдён; native107 не запускался. Владелец отдельно разрешил конкретный test-only patch и продолжение; повтор failed suite16/16 PASS.
- Единственная fresh shadow107 подготовлена/собрана/запечатана. A first/second/prestop250 OPEN=BUY63+SELL162+MFG25, ordinary16/16;63 grants по50млн,8 stock grants,4 +n. STOP configured drained=true1880ms, но EARNED witness NOT_OBSERVED: retained JVM1/SQL4, B запрещён, force/retry не выполнялись.
- Independent causal P2: snapshot-first Player/token заменён до STOP. Post-A source-only harness correction через current witness и существующий running native root; stale-first RED→GREEN, all-stale reject, JDBC0. Sealed A stage/JAR не менялись и не перезапускались. Полная crafting BUY/SELL coverage также PARTIAL: unresolved native merchant corridor и missing acquisition anchors явно отражены в public TSV.
- Final independent source/report review после correction P1=0/P2=0 для PARTIAL/BLOCKED.20 exact files staged/diff-check exit0, code commit711426efcec normal push exit0. Future OwnerConfig config-only Save→Apply→Verify→Apply→Verify прошёл для4 новых long settings; исходные287 tuples exact, повтор Apply0. Source protected bytes и original owner287 сохранены; B/clean STOP/restart остаются BLOCKED, TASK108 не запускался.

Не менять Player.ini/rates/enchant XML/TSV, Bootstrap/Craft v1 receipts, обычные5000 identities/250 roles, owner287, Studio, semantic/conversation/Geo, исторические stages/reports. SQL reseed/DROP/GRANT и запуск MAIN/PLAY/защищённых releases запрещены. Новые defaults funding50млн/cooldown1800/lifetime250млн/global10млрд следуют уточнению владельца; это не прежние owner overrides. Полный PASS только при всех обязательных gates; иначе точный PARTIAL/BLOCKED.
