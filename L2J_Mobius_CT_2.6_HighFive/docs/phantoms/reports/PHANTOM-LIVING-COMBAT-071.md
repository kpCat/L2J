# PHANTOM-LIVING-COMBAT-071

**FULL071=PARTIAL. RELEASE=PASS для отдельной поставки; PLAYABLE_OWNER_READY не объявлен. OWNER_VISUAL_PENDING.** Точные HARD gates и остатки — [ACCEPTANCE071.tsv](PHANTOM-LIVING-COMBAT-071-data/ACCEPTANCE071.tsv). Проверка поведения закрыла обмен, полный олимпийский магазин, Codex, пять ограниченных классовых сцен и Mammon. Полные причинные PvP-цепочки, отход при истощении сосок, оплаченная CP acquisition и экономическое устойчивое снабжение остаются недоказанными.

Работа продолжена в исходном дереве `feature/phantom-world`, baseline `752a91a27ef1421951246a618a9bdfa79f4769da`. Нового goal/worktree нет. Начало кампании23.10.2026 08:11:28UTC; учтён предыдущий проход23мин47с. Применён консервативный абсолютный предел14:11:28UTC; product source заморожен до13:11:28UTC. Последующие изменения — только essential native helpers, доказательства и поставка. Владелец/TestAdmin/клиент не привлекались.

## Реализованное поведение

Сохранены Economy070Shop/native multisell, PhantomPvpService/CombatService/PlayerNativeWork, SupplyJourney/NativeEquipmentPolicy. Новых AI engine, rate rebalancer, зависимостей или клиентских файлов нет. Bounded exception к8–10 файлам обусловлен связанными native owners и собственной поставкой в TASK071. Широкого рефакторинга нет.

- Alt+B физически меняет13722↔5575 ровно1↔20000, с bounded quantity, общими inventory/SQL locks, атомарными receipts и fail-closed replay/контекстом. Questitem flags13722 сохранены. Immutable native aliases91071/91072 сохраняют38/69 offers102/103, Noblesse/main-class/специальные входы и native Fame−300. Оригиналы не изменены.
- Codex6622=11млнAA=550Tokens.9625/9626/9627 сохраняют40/50/100млнAA. Все12epic цен согласованно уменьшены в100раз;21712 округлён вниз до11,111,111,111. Canonical policy, active TSV, SHA, store catalog и ручной exporter согласованы; startup/reprice Apply не добавлен. Existing globalcap1 и real-clock14/30/60day restock сохранены.
- Premium combined chance cap перенесён в существующий INI: default3, invalid/nonfinite→1, explicit item0 сохраняется. x1 gameplay, premiumXP/SP2, drop/spoil chance2 amount1, Adena chance1 amount2 сохраняют исключения; второго конфига нет.
- Economy DB authority проверяет exact own schema, installationUUID, task/version, pinned migrationSHA и InnoDB таблицы; произвольная/старая070 привязка отвергается. Старт не восстанавливает проданный stock.
- PvP использует реальные learned/native-ready skills, MP/HP/reuse/Souls/charges/weapon conditions, bounded selection и owned rear/ranged movement. Actual reciprocal clan war и редкая причинная social инициатива встроены в прежний admission; масштаб памяти attack/kill и24-entry чтение исправлены. Нового planner нет.
- Native shot reserve учитывает whole hits blessed+regular, текущий заряд один раз, исключает wrong grade; финальная action boundary требует native charge. SUPPLY_REQUIRED передаётся существующему retreat/replan. CP5591/5592 используют штатные handlers/reuse/Olympiad restrictions. Helvetia30081/list3008100 имеет настоящий источник200/500Adena+tax, выше liquidation100/250.
- Supply сохраняет owned inventory/warehouse-first, настоящий NPC/offline market и бюджет. Пороги NG19/D20/C40/B52/A61/S76/S80-80/S84-84 следуют native expertise. Ammo/shots обслуживаются раньше HP/CP; future-grade reserve/earmark, bounded debt/replan и original deadline сохраняются. Безопасный rebind разрешён только до prepared/committed receipt. Бесплатная gear/currency runtime не добавлена.
- Pushkin30300 открывает оригинальные Mammon HTML и13 списков с сохранением31126/31113, AA/materials/enchant/NPC/range checks и исходного stone exchange. Optional50/60 — SKIP_OPTIONAL_WITH_REASON: два доказанных inert H5 carriers не найдены в bounded scope; raid exemption сохранён.

## Native приёмка и её пределы

Экономика/Mammon: реальный Token1/550 roundtrip, Alt+B, physical debit, sameUUID race, cross-account/quantity/poor/Olympiad/rollback negatives; Codex за550Tokens и отдельно11млнAA; native Fame21874→100; оплаченный premium1day/replay; epic6660 last-unit race двух покупателей с одним победителем и сохранением общей суммы200млн. Mammon реально выполнил remove-SA, D/B/A exchanges с enchant, S unseal заAA, paid Varnish→Foundation, range/NPC negatives и оригинальный31126. См. `native-economy-mammon.tsv`.

Новая JVM на final JAR повторила11 immutable Token receipts и premium/выигравший EPIC receipt: balances, ring object/count/enchant, wallet0, stock0, version1 и due1793965761630 неизменны. Первая retained проверка FAIL сохранена: v1 snapshot включил zero-count Adena57 object268435477, который native save законно удаляет. Исправленная проверка не переписывает snapshot и не объявляет raw SHA равным. Она требует exact positive native+SQL ring268435478:6660:1:0, исходные200m deposit/EPIC receipts, wallet/stock/due и единственный реконструированный SHA preimage в ограниченном диапазоне. Это нормализация v1 zero57, явно обозначенная в `native-economy-retained.tsv`; исходный failure также сохранён. Actual14/30/60day expiry/refill не наблюдался, clock не ускорялся: A_EPIC=PARTIAL.

Классы: native19NG/20D и сохранённые40C/52B/61A/76S/80S80/84S84 прошли expertise/equip и physical shot debit. Gear была объявленной FIXTURE, а не экономическим приобретением. Dagger подтвердил positional damage и заднюю позицию; Archer — damage/MP/reuse, shots600→598 и ranged move170; Mage — damage/MP/reuse, shots400→395 и range254.6; Tank — настоящий defender skill effect, MP/reuse, shots600→599. Support1217heal/1013recharge изменили реальные pools/MP. Это ограниченный B_SKILLS PASS, не доказательство полной party orchestration.

Первый Kamael cast использовал skill без Soul debit10→10: исходный aggregate FAIL оставлен. Отдельный control восстановил того же актёра без сброса HP/MP/gear/shots. Souls не сохраняются Player при logout; один конечный10410 заранее объявлен независимой TEST source и защищён CREATE_NEW once marker. Native handler2499 потратил бутылку1→0, Souls0→5. После materialization из уже stock-learned offensive skills оставлен478level15; новых skill/level не добавлено. CombatService cast478 дал damage8261→8227.966,MP2012→1997.25,shots598→596,Souls5→0,reuse и movement45. B_SKILLS PASS ограничен existing learned subset и finite bottle; earned Soul procurement не заявлен.

Соски: mixed blessed/regular reserve, wrong-grade exclusion, текущий charge once, native debit и zeroStock SUPPLY_REQUIRED/final charge refusal прошли. Native defensive escape/restock при PvP depletion не наблюдался: B_SHOTS=PARTIAL. Полные native SPOT warning/engagement, REVENGE, PARTY_DEFENSE и clan-war сцены не доказаны: B_CAUSE=PARTIAL несмотря на9/9PvP и6/6social regression.

CP: finite handler control дал5591 count2→1/CP1→51 и5592 count2→1/CP51→251; immediate reuse и оба Olympiad отказа сохранили stock. В настоящем Tank PvP автоматический threshold потратил оба finite stacks2→0, CP0→peak524.966, reuse наблюдался; прямых CP writes внутри эпизода нет. Последняя paid acquisition попытка на отдельном disabled TEST profile закончилась `REPLAN NPC_OUT_OF_RANGE` до purchase; commerce/materialization/ThreadPool/Hikari штатно закрыты. Бесплатного обхода/retry нет. B_CP=PARTIAL, успешный paid receipt отсутствует.

Supply: сохранённый actor прошёл настоящий2.3км route и в новой JVM durable SELL6716x1=6250, BUY1835x120=960, native return в39ед. от точки, Adena5290. Surplus6716 — FIXTURE. Затем CombatService VICTORY, но actor19/Wolf4 штатно дали0EXP/SP из-за level gap15 при лимите11. Без refill/reseed/HP/rate changes тот же actor восстановлен сHP494,MP175,A5290,SS116. Ближайший stock target подходящего rewarded level20132 находится11569ед., за bounded5км. `native-earned-supply.tsv` завершился BLOCKED_INPUT до travel/kill. C_RECOVERY=BLOCKED, C_SHOTS/C_GEAR=PARTIAL; покупка за fixture surplus и native пороги не доказывают earned sustainable20+ и economic procurement.

Предыдущие ошибки fixture/cleanup/timeout и первый retained hash FAIL сохранены в evidence, не затёрты последующими ограниченными controls. Массовых soak и повторного пополнения ради PASS нет.

## Committed source, Main и поставка

Product/datapack/runtime commit **4b84313c991dc6c620aa3a2ba1de43400e4ff6bb**,93 exact staged paths. Test-only helpers:68ff3ef49c3d362666e27bb556f282f756950226,06fd4baac4c18e9880652a35ee70fef53c39f64e,952782c0c31a28339f6b0118322ae15f1f8cca58,6bd80574c3de92f8fdeda713bc6e3b89f21a9832. Helper sources извлечены из committed archives и отдельно скомпилированы только в test namespace; production первым из final JAR. Exact production/datapack/runtime diff4b→последний helper пуст. `HELPERS071.json` связывает archive/class hashes; основной build receipt не подменён.

`Build-Task071.ps1 -Label core-v1 -SourceCommit4b84313...`: git archive source, Ant jar+compile-tests SUCCESS31с. Рабочий dist/libs и старые candidates не заменялись. [BUILD_INFO071](PHANTOM-LIVING-COMBAT-071-data/BUILD_INFO071.json):

- GameServer SHA256 `0F469814330A4AA7DF0B72BC2A1ED36455B4C8035569A6D821D390FC4078958B`.
- LoginServer SHA256 `C63FB7810D8E8009045B487A77001F910CDD5EC5C64417DB8022590062F2E25D`.
- SourceZIP SHA256 `C862923E194D8343D3E15E849FE3DCF659995172320E5B129C7BDE11856F821B`; source tree `AA1CC8CED5CF7690955472F6D020D24630CEEA18E23FDDF68937A4B4824972B0`; tests `D885249A063C08568A437135F47B40D666CA99374C2D5905F544F885FC3280DF`.

18 economics и319 combat/commerce/progression/player production classes побайтово совпали с native proof binaries. Final JAR повторно прошёл PvP9/9,social6/6,supply5/5,economy12prices/Codex4/aliases38/69/SHA,Mammon13. Existing content Java route `post002-semantic-v3-content2002002` из committed archive PASS3/3, без нового build. Ant/native runs последовательны.

Own TEST/RELEASE071 были absent и установлены отдельно:128tables/123stock scripts, aggregate8699ACDD436D29F5666CCC501E6B8D9A09265F064FC16AB7A7166BE04C84AEA2, migrationSHA e6c39363c6ca288196f90f0808d062d7e50ca9acace56f2e63e1242b7dd212e3 и exact installation authority. Старые schemas не мигрировались. `Assemble071` создал **D:\PhantomWorld\Living-Combat-071-candidate**,203physicalGeo сSHA, START/STATUS/STOP, whole JAR/source/test/tool manifests. TEST paid accounts/fixtures не перенесены в RELEASE.

Обычный Main1 готов12:45:40UTC; STOP запрошен12:51:01 после>5мин полезной работы, native STOP/Hikari завершены12:51:11. Main2 готов12:55:41, затем штатный STOP. Оба используют одну RELEASE071 DB и тот же committed JAR.216identities/17profiles/merchantQuota/200shop owners/600listings hashes совпали; restart loaded200shops/openings0, issued capital20m и stock82,093,352 не перевыданы. EXP725→2077/SP54→108, delta1352/54,9награждённых profiles. HP/deaths сохранены, inventory765→767 меняется от loot/consumption. Это Main rewards, но не замена C3 earned chain.

Финальная SELECT-only проверка обеих OWN071 schemas: otherConnections0. Runtime071 STATUS: game/login STOPPED,PID0,DB0,exact JAR/source hashes. CIM ownJava0 и все собственные TEST/RELEASE listeners0. См. `FINAL_CONNECTIONS071.tsv`, `FINAL_RUNTIME071.txt`, `FINAL_PROCESSES071.txt`. Force kill/остановка общей MariaDB не применялись.

Настроенная локальная поставка самостоятельна и остаётся STOPPED. Публичный ZIP — **Living-Combat-071-public-template.zip** с per-file manifest/checksum и committed source archives; по RELEASE_CONTRACT исключены credentials/registration/private runtime identities, PID/control/log inputs и DB backup. Это шаблон для нового размещения; локальный candidate уже настроен и участия владельца для приёмки не требует. Archive receipt находится рядом с evidence. RELEASE PASS означает проверенную доставку, не FULL071 gameplay PASS.

## Scope, проверки и публикация

Прочитаны root/module AGENTS, root README, current status/workflow/release contract,070 analogues и весь071пакет; module README и дополнительные AGENTS не найдены. Паттерны070 durable receipts/runtime ownership, native multisell и066–070 supply continuation переиспользованы. Independent source/acceptance review завершён; новых открытых source defects не найдено, недостающие native proofs перечислены выше.

Исходный пакет071 и7чужих068V файлов сохранены; protected74 candidate inputs повторно сверены поSHA. Exact changed-file inventory — `CHANGED_PATHS071.txt`; private/runtime binaries вGit не включаются. Исторические отчёты/клиент/старые DB не изменялись.

- mojibake-маркеры в изменённых исходных/документальных файлах проверены отдельным полным набором; raw native console encoding сохранён локально, в Git помещены читаемые TSV и выбранные ASCII строки с raw SHA.
- escaped Cyrillic в изменённых файлах проверены отдельно по всем шести Unicode/XML patterns.
- Exact-path `git -c core.whitespace=cr-at-eol diff --check` выполнен; CRLF учитывается без массового преобразования.

Git разрешён прямым запросом и AUTHORITY071: bounded branch/scope inspection, exact staging, commit и normal non-force push толькоfeature/phantom-world. Использованы symbolic-ref,rev-parse,status,worktree list,for-each-ref,ls-remote, exact-path diff/--check, archive,add --exact paths,commit,push и финальное сравнение remoteHEAD. Детали команд/SHAs — `GIT_COMMANDS071.txt` и publication receipt. Ни master/default, ни история не менялись; чужой068V dirt остался незастейдженным.
