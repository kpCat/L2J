# PHANTOM-LIVING-MARKET-107

Статус: PARTIAL / BLOCKED на natural STOP A; B не запускался. TASK108 не запускался.

Baseline: live/local/tracking `feature/phantom-world`, `795b160b9dc7ffb4005be577941080286edf3aef`. Текущий owner сохранил 287 overrides. Чужой untracked dirt не затрагивался.

## Реализация

- BUY allocator резервирует минимум одну единицу каждого реально доступного кандидата, затем распределяет остаток; 20 слотов — потолок. Полный BUY pool отделён от оборудования. Проверен дефект 100000/20/12500 при трёх позициях: RED пусто, GREEN три позиции с reserve100000.
- Каталог без общего128 cutoff; классификация по native ItemData/RecipeData и724 проверенным точным ID mappings. Общие crafting materials разрешены; D/C pieces и recipe scrolls запрещены. B/A/S/S80/S84 equipment pieces и ровно60%-ные equipment recipes выделены по actual output grade. Неопределённые источники fail closed.
- SELL использует actual owned inventory, включая приобретённый товар. Недостающий ассортимент пополняется конечным owner-provision stock. Раздельные категории оружия/брони/бижутерии, +0/+n и небольшая gear share; native PvP/epic/time/attributes/augmentations исключены.
- +n оценивается существующими `PhantomCanonicalEnchantRoutes` и `PhantomEnchantExpectedCost`; новый `quoteStock` предоставляет тот же canonical расчёт до native effect. Цена заточки не придумана.
- Новые grants отделены от неизменённых Bootstrap/Craft v1: CAS ledger+PREPARED до effect, native inventory/Adena writer, SQL readback, COMMITTED после доказанного результата. PREPARED/INCONSISTENT карантинят владельца, сохраняют израсходованные квоты и не переигрываются автоматически.
- Политика фиксируется в durable ledger; изменение лимитов/интервала не обнуляет lifetime usage. Все receipts учитываются при восстановлении текущей enchanted quota. Grant counters индексируются, полный каталог не сканируется каждым торговцем.
- Поллинг `MerchantRotationSeconds=30` сохранён; отдельная смена ассортимента по умолчанию1800 секунд. Наблюдатель, customer/native work и uncertain grant откладывают изменения. При отсутствии SELL плана используется безопасный BUY либо законный IDLE. Player/лавка не удаляется на глазах игрока.

## Настройки владельца

`Custom/PhantomMerchants.ini`: рабочий grant50млн, cooldown1800 секунд, lifetime250млн/merchant и10млрд/global; старый bootstrap100000 сохранён. Stock500млн/owner и10млрд/global, отдельные per-item/per-grade/gear/enchanted/grant caps; max enchant6. Все значения конечны, проверяются при загрузке и доступны штатному OwnerConfig для будущих managed сборок. Конечные лимиты не означают автоматическое восстановление до50млн на каждом рестарте.

Существующая source-aware стоимость учитывает native drop/spoil chance/amount и текущие rates8/10/10/20. DB-free проверка удвоения spoil amount меняет source fair в ожидаемую сторону. Native цены требуют реального runtime generation, NPC availability и tax corridor; статические source candidate fair не названы ценой сделки.

После managed commit проверен обычный OwnerConfig Save→Apply→Verify→Apply→Verify на отдельной config-only fixture без Database.ini: grant60000000, refresh2400, stock global20000000000, gear share25. Save сохранил4 новых long overrides (291 total), все исходные287 tuples file/key/kind/baseValue/value совпали. Apply changed1, оба Verify changed0, повторный Apply changed0. Source OwnerConfig.json/INI и sealed stage не менялись. До корректного запуска временная fixture трижды была остановлена orchestration guards (слишком широкий filename filter, excluded catalog entries, затем неверный leaf/ProfilePath); после переиспользования точного public/excluded и CLI contract проверка прошла. Это config-only fixture attempts, не дополнительные native107 shadows.

## Проверки до native

- G0: exact OWN102 owner/schema/SID/UUID/source inputs/DB-config hash, полная SQL visibility, foreign0/JVM0/SQL0/TCP0; два одинаковых whole-schema preimages; SQL writes0.
- Read-only census: ordinary READY5000/distinct/original IDs/init неизменны; profiles5001, characters5256, inventory54712, components34801, recipebook150, offliners187/offlineItems1446. EXP/SP delta0 до107.
- Защищённые source pins:173 исходных,172 unchanged; единственное разрешённое отличие PhantomMerchants.ini. Sealed A manifest186 pins. OwnerConfig.json побайтово сохранён.
- Ant compile-tests в отдельном build-only scratch: exit0,5 прежних compiler warnings. Database.ini отсутствует; этот scratch не native stage.
- Focused RED→GREEN: BUY starvation; catalog200→128 cutoff; SELL→BUY fallback; grants reserve/replay/caps; unsold-stock reissuance; rotation interval1800→3600; craft-output-only gear omission. GREEN и6 отрицательных policy cases пройдены.
- Harness unit66/66; skeleton первоначально14/15 из-за исторического OFF assertion при owner True. Stop gate соблюдён; владелец явно разрешил test-only fix. Повтор skeleton16/16: OFF на явной fixture, owner load inert/no writes/no JDBC.
- Affected102/103/104/105/106/099/100 targeted checks пройдены. Enchant4, market quote6, recipe cost4, loot fair3 — PASS. Generic `ant verify` не запускался.
- Полный native XML source audit:19200 item templates,1000 recipes,1276 ingredient IDs,724 mappings,10228 candidates,5660 class eligible; unresolved crafting IDs0.63818 drop facts,3638 declared NPC IDs,9649 item IDs с source evidence. Все кандидаты и исключения в [universe](market107-universe.tsv); category/grade coverage в [coverage](market107-coverage.tsv).
- DB-free audit явно маркирует runtime spawn/tax corridor UNRESOLVED, funded/in-stock/offered/open NOT_RUN. Native effect-handler warnings при XML-only load не являются combat proof; actors0/JDBC0 подтверждены.
- Independent source review и follow-up: P1=0/P2=0; runtime proof этим не подменяется.

## Единственная shadow107

Fresh physical stage107:29053 inputs, Geo203, новая preparation authority, Database.ini изначально отсутствует. Existing owned102 DB сохраняется. Stage OwnerConfig Apply→Verify→Apply→Verify:68 files,287 overrides,conflicts0,changed0.

A, три среза first/second/prestop:250 actual OPEN, BUY63/SELL162/MFG25,100 distinct offered IDs,551 строки gear+0,4 положительные заточки. World ordinary16/16 с интервалом30s; это typed native owners. Все250 merchant World/service/token mappings проверены. BUY reserve<=actual wallet; actual inventory/object/enchant, disabled account, offline metadata/lines, canonical prices и71 grant SQL/cache readbacks прошли.63 funding grants по50млн=3150000000;8 stock grants суммарной canonical value767112, без дальнейшего роста между срезами.

Примеры native +n ASK: item2413 +6=233361;631 +6=197353;883 +4=116361;604 +6=57380. В receipts сохранены actual native scroll routes952/956, safe chances1.0, последующие0.85, failure DESTROY и ожидаемые стоимости; это owner-provisioned экземпляры, не поддельные результаты успешных enchant rolls.

Полный runtime catalog:1410 SOURCE_PRICED,4250 UNPRICED (`No legitimate market acquisition anchor`),4568 DENIED. Public universe сохраняет static source evidence и добавляет отдельные runtimeQuote/runtimeStatus/runtimeExclusionReason. `priced` означает наличие canonical quote, `fundedListed` — actual BUY item IDs, `inStock` — actual inventory включая невыставляемые legacy вещи, `everOfferedObserved` — только наблюдавшиеся срезы этого JVM, а не lifetime history.

`runtimeExclusionReason=NONE` обозначает отсутствие исключения. A JAR SHA256 `ebfd6745b64d381b50f17ed235d967bbc33db3b470add10602e037d0424dd05e`. Prestop quote counters:13229 template quotes,10549 stock quotes,6269 source-cache entries. Реальные p50/p95/SQL latency/GC и lifetime coverage UNAVAILABLE, а не оценены по одному elapsed time.

Обязательная полная BUY/SELL coverage не доказана: B/A/S/S80/S84 pieces и recipes в actual offers0; из125 resource candidates119 class eligible,69 priced,17 фактически в SELL, fundedListed0. Native bidAllowed имеется только у23 OTHER_USEFUL IDs; crafting BUY блокирует `npc.visible.merchant.unresolved`, например1864. Наличие source-backed предмета не обходит canonical acquisition/tax safety. Это отдельный gap, а не PASS всех ресурсов или всех цен. Ротацию1800s на runtime не ждали; смена ассортимента и exhausted-role fallback подтверждены DB-free, а не реальной оплаченной сделкой.

STOP A: configured system drained=true за1880ms, затем существующий `proveStop105` отказал: `ordinary census/actual EARNED logout completion not proven`; logout=NOT_OBSERVED. `stop6.blocked` и retained process receipt созданы; natural exit/lease closure/checkpoint отсутствуют. Фактическое состояние после отказа: JVM1, owned SQL4, foreign SQL0. B, restart idempotence, clean STOP и final stage128 restore: NOT_RUN/BLOCKED. Source128 неизменён. Автоматических force/retry/reseed/DROP не было.

Позднее владелец отдельно явно разрешил однократное завершение только exact retained JVM A как UNCLEAN recovery. Перед действием совпали PID/creation/SID/executable/command SHA/JAR SHA; использован bound process handle без process-tree kill. Первый guard отказал до эффектов из-за UTC literal conversion; исправлена только явная UTC интерпретация, точность CIM microseconds и native handle100ns проверена. После единственного разрешённого завершения проверены JVM0/SQL0/TCP0/foreign0. A классифицирован HUMAN_APPROVED_FORCED_UNCLEAN; это не natural clean STOP, не разрешение на B/new A и не PASS restart. `exit6.clean` не создавался; sealed cap16 не менялся. Exact approval/recovery receipts и postflight находятся в private archive.

Read-only post-UNCLEAN census:10 slices, original ordinary IDs EXACT5000, SQL writes0. Полный preimage не совпал с более ранним retained snapshot: offline codec установил только252 изменившихся character rows (vitality_points250, onlinetime2), EXP/SP delta0, negativeRows0. Inventory54720, components34880, recipebook150, offliners250/offlineItems2364, accounts/profiles/identity membership и250 merchant birth/lifetime payloads не изменились; новых grants/stock/Adena delta0. Этот mutable timer delta не скрыт и не назван побайтовым сохранением всей БД.

Independent causal review нашёл P2 harness: все16 objectIds совпадают между second-cohort и before-stop, но первый выбранный witness уже имеет другой Player reference и lease token; только15 из16 lifecycle совпадают. Player.prepareNativeCleanup отправляет logout до удаления, поэтому поздний listener может пропустить событие. Точный момент пропуска receipts не устанавливают; production EARNED failure этим не доказан.

Post-A test-only correction: выбор current stable witness и регистрация listener внутри существующего `PlayerNativeWork.run` owned root с повторной проверкой scope/lifecycle/World/token и cleanupPrepared. Assert фактического EARNED сохранён. Focused stale-first RED exit1→GREEN exit0, all-stale reject и JDBC0; harness javac exit0. Correction не копировалась в sealed stage и не запускалась native. Sealed manifest сохранён: delivery отличается от него только этим test-файлом; production source/JAR A не менялись.

После блокера два read-only whole-schema снимка совпали. Existing offline codec подтвердил original5000 READY/distinct/IDs/init, profiles5001 и accounts/characters membership unchanged;250 READY merchant birth/lifetime payloads unchanged; recipebook150 unchanged. Inventory54712→54720:8 stock objects added,78 quantity updates, removed0; merchant Adena+3150000000, ordinary Adena+596. Components34801→34880:79 added (72 market ledger/receipts,7 ordinary supply.intent),231 updated, removed0, versions не уменьшились. Characters278 changed, EXP/SP net+4212/+520, отрицательных EXP/SP/level0; native/background earned split NOT_PROVEN. Offliners187→250, offlineItems1446→2364. Это retained-state data audit, не STOP proof.

Paid customer BUY/SELL/craft: NOT_RUN; законный аутентифицированный customer пока отсутствует. Typed ordinary cap16 не является доказательством normal ACTIVE admission,32/128/5000 simultaneous ACTIVE или p95.

## Процесс и ограничения

Одна зависимая market feature; bounded scope20 файлов с планом и отчётом. Первоначальные16 delivery paths уточнены canonical pricing seam, существующей affected100 regression и отдельно разрешённой skeleton fixture; это ближайшие необходимые локальные контракты. Historical task packages/reports, Player.ini, rates, enchant routes, semantic/Studio, protected releases и чужие worktrees не менялись. Новые DB schema/tables/dependencies/timers отсутствуют.

Raw SQL/owner/account/character/object/lease/process/receipts находятся во внешнем private owner+SYSTEM archive; public TSV содержат только game item IDs, источники и агрегаты. Private pointer: `.phantom-local/logs/PHANTOM-LIVING-MARKET-107/index.private.json`.

Затраты превысили500000 tokens: оплачены повторный live/authority audit, полная XML/source classification, durable grant state machine, прежние causal tests и independent reviews; это превышение workflow budget требует явного отражения, а не заявления об efficiency PASS. В ходе работы исправлены неверный cwd первого price-suite запуска и неверные дополнительные CLI args; затем точные affected команды повторены успешно. Ошибки patch application были атомарными, частичных исходников не оставили.

- Mojibake-маркеры в изменённых файлах проверены:20 файлов, полный набор, совпадений0.
- Escaped Cyrillic в изменённых файлах проверены:20 файлов, все6 patterns, совпадений0.
- Git использовался по прямому разрешению TASK107 для baseline/scope, штатного OwnerConfig managed-source чтения и normal commit/push. Code commit `711426efcecabbcc5d0d3923e0cef4bcbabb16dd`; normal push exit0 обновил только feature/phantom-world с795b160b9dc. Финальные документы публикуются отдельным обычным commit; его SHA и final remote equality фиксируются в итоговом сообщении, без самоссылочного изменения отчёта.
- Итоговый independent source/report review после post-A correction: P1=0/P2=0 для PARTIAL/BLOCKED поставки; native correction NOT_RUN. До correction найденный P2 не скрывался.

Git-команды: `git rev-parse HEAD`, `git rev-parse --show-toplevel`, `git symbolic-ref --short HEAD`, `git status --short --untracked-files=no`, `git status --porcelain=v1 --untracked-files=no`, bounded `git worktree list`/`git for-each-ref`, `git merge-base --is-ancestor 795b160b9dc7ffb4005be577941080286edf3aef HEAD`, Task102 Prepare ancestry check `git merge-base --is-ancestor 9245f95aa6c4dd7c50df47c71cd68f2aa4b0b9d0 HEAD`, `git ls-remote origin refs/heads/feature/phantom-world`. OwnerConfig Save штатно выполняет `git rev-parse --verify <revision>^{commit}` и `git show <resolvedCommit>:<module>/dist/game/config/<publicFile>`; только68 public catalog INI, без private configs.

Для scope20 (exact allowlist плана плюс сам план) выполнены `git diff --stat -- <8 exact tracked paths>`, `git -c core.whitespace=cr-at-eol diff --check -- <scope20>`, `git add -- <scope20>`, `git diff --cached --name-only -- <scope20>`, `git -c core.whitespace=cr-at-eol diff --cached --check -- <scope20>`, `git commit -m 'phantom(task-107): add finite living market with audited blocked native proof'`, `git push origin HEAD:feature/phantom-world`; exit0. Финальный doc commit использует только два exact report/plan paths. Git LF→CRLF warnings не изменили source bytes; staged whitespace check exit0. Add-all/stash/reset/restore/force/amend/merge/rebase не использовались.
