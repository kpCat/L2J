# PHANTOM-LIVING-ECONOMY-068

22.10.2026. Кампания ещё выполняется: **CORE068=OPEN, FULL068=OPEN**. Этот файл заполняется по мере финальной интеграции; незаконченные проверки ниже не являются PASS. Клиентская визуальная QA — DEFERRED_BY_OWNER. Владелец не входил в игру; fake GameClient не создавался.

Baseline local/live `b7e18670704d9b14661594b13d576df97a7ae770`, единственная ветка публикации `feature/phantom-world`. Финальный code commit `fd85c3103325eead1a1735e053af810d64fbc118`; committed source/JAR receipt готов. Report-only descendant и remote HEAD фиксируются после доставки в отдельном PUBLICATION068.json. Доставка готового объёма выполняется независимо от оставшихся ограничений A–G.

## Результаты A–G

| Блок | Статус сейчас | Реализованный результат и граница доказательства |
|---|---|---|
| A | OPEN_NATIVE_CHAIN | Общий бюджет снабжения защищает обязательные расходы, а желательный reserve зависит от доступного остатка; оба native NPC/private-market пути используют одну политику. Реальные gear/skills/HP/MP/цель участвуют в viability. Исправлены бесконечное продление combat debt мелким уроном и cleanup занятой native атаки. Loot classifier сохраняет ценные D/C spoil-ресурсы, recipe BOM/shared materials, B+ и неизвестную стоимость; полный склад не разрешает их автоматическую продажу/уничтожение. Ordinary Main causal farm→earned BUY→charged continuation→Spoil→bank пока не доказан. |
| B | OPEN_HANDOFF_PROOF | Использованы прежние Background/Acquisition/Transaction/Economy components, cursor и receipts, без второй БД/инвентаря. В обычном Main сохранены настоящие фоновые результаты: profile5, intervalOrdinal5, EXP367/SP14, item1865×1/Adena50 с operationKey/afterHash; это виртуальная выработка, не native kill. Полный retry/native handoff proof ещё отдельно не закрыт. Merchant bootstrap конечен и сохраняется после restart. |
| C | PASS_NATIVE_BOUNDED | На committed v2 восстановлены202 native owners/identity leases,201 непустая лавка,0 combat/materialization owners,0 human demand от offline stores. Реальные SELL/BUY, paid manufacture, last-stack race1of2, отказ owner до effects и ordinary offline control прошли. Финальный candidate/ordinary restart проверяется ниже. |
| D | PASS_NATIVE_BOUNDED | Native self-craft и manufacture используют RecipeManager, реальные BOM/MP/chance/плату. Recipe1:4Stem+2IronOre/MP30, fee100→500Arrow, conservation PASS. Четыре archetypes и28 metadata tiers NONE–S80; native B/S80 weapon и B armor upgrades подтверждены. Четыре S80 chest решения SKIPPED_NOT_UPGRADE относительно full-body, полного комплекта S80 не заявляем. TEST10b/gear/materials — подготовка, не earned. |
| E | PASS_BOUNDED | Общая quote authority для native рынка/CLI, positive manufacture fee, epic rarity/boss-source policy, failclosed UNPRICED. Настоящие TEST HomeBoard/MultiSellChoose purchase/Apply/Rollback прошли на прежнем согласованном1/1 Plan. Canonical rebalance matrix x10→Apply→x20→Apply→x10→Apply4/4 повторена на final committed exporter, targets/quotes x10 совпали. Candidate оставляет stock XML/CB OFF. |
| F | PASS_NATIVE_BOUNDED | NORMAL/RED/BLUE categorical выбор, независимые итоговые probabilities, sum>100 guard, отдельные stats/rewards/spoil и совместимость red-only. Native HP/drop/spoil/NPC_INFO team/reset2/2 прошли на final JAR; отдельный baseline067 aura RED воспроизведён. Blue OFF по умолчанию, client rendering deferred. |
| G | PASS_NATIVE_BOUNDED | Existing Rift получил owner-token room reservation, serialized/idempotent terminal/timer lifecycle, death/restart exit и queued respawn cancellation. Native solo/Party2/fee/jump/exit/death/revive/reentry/late-callback matrix повторена на final JAR; actual queued-respawn RED→GREEN. Stock wrong-level/reuse guards отсутствуют: NOT_APPLICABLE. Полного сетевого logout/invite race proof не заявляем. |

## Общие причины, устранённые при интеграции

Offline Player с isOnline=true и отсутствующим headless flag ошибочно считался человеком и подавлял passive autonomous origin. Общий predicate теперь требует настоящий attached client либо конечный LOCALPLAY_TEST_HUMAN owner. Он используется demand и retention. Native market assertion0humanPoints и повторный Main v2 подтвердили исправление.

Saved background anchor может быть lineage назначенного farm при неизменных native XYZ. Main v2 был в Dwarf ingress, а goal Hungry Eye находился в343км: existing topology route содержит14steps/два GK, single-GK physical request его не покрывал. Visible executor теперь выбирает factual departure из existing containing topology/livePositionAllowed, исполняет по одному прежнему route step и сохраняет actual owned checkpoint. Новый120s attempt появляется только после успешного промежуточного checkpoint; retries/движение без результата время не обновляют. Между anchors admission сохраняет exact текущий Journey. Для radius1 farming area canonical point проверяется перед offsets. Радиусы/таймауты не расширялись, новой навигации и правок actor/goal/cursor ради сцены нет. Native outcome этого fix ещё проверяется.

Реальная ошибка source pin Kamaloka067 блокировала общий factory до births. Обновлён только ожидаемый canonical hash в supported-content catalog после сверки source/own copy/истории; verifySource не ослаблен. Историческая067 не менялась.

## Профиль, цены и R2

Самостоятельный candidate: `D:\PhantomWorld\Living-Economy-068-candidate`. START/STATUS/STOP работают contained tools, без checkout. Phantom population/active/cap8; merchant target200/hard250/lifetime identities300/batch5/rotation30s. BOOTSTRAP capital100000/identity, global30000000; stock value500000000/per-item10000. Это одноразовые ограниченные стартовые источники, не live earned; выдача не возобновляется по restart, смене роли или уничтожению identity.

Фонд R2 **5000 сохранён** вместе с issued/limit и replay guards: пять1000 batches, шестой/replay запрещены в existing commerce-decision regression. `PhantomStarterShotLifetimeLimit=0` — измерительная настройка068 для проверки самостоятельного снабжения; продуктовая функция не удалена. Значение5000 задаётся до рождения новых боевых identities; старые права не увеличиваются, merchant roles его не получают.

Candidate сохраняет исходные XML-цены и `CustomCommunityBoard=False`. Обычные Phantom выбирают доступные native NPC/private-market offers, нужные предметы и бюджет; CLI/TEST purchase не включают Alt+B equipment bypass. Причина осторожного default: stock600010 row1 price250000 ниже вычисленного supported liquidation bound795000; это модельный bound, не новый native profit episode.

Рабочий rebalance: **STOP → однократный Baseline до первого Apply → backup/изменение Rates.ini → Plan с тем же BaselineDirectory → просмотр diff/UNPRICED/warnings → явный Apply exact SHA → START**. Текущие preimages используются только для diff/conflict, canonical prices — для acquisition anchors. Death/Spoil/Raid chance+amount, item-specific overrides включая57, NPC/BOM/rarity/target-time учитываются отдельно; XP/SP не являются price multiplier. Rollback восстанавливает цены по exact postimage/backup, Rates.ini возвращается отдельно. Automatic startup Apply отсутствует. Точные команды: [CLI README](../../../tools/phantom-economy/README_RU.md), [E_HANDOFF068](../tasks/PHANTOM-LIVING-ECONOMY-068/E_HANDOFF068.md).

Blue и personal QoL/solo Rift остаются OFF/allowlists пустые. Собственные TEST child settings не переносились в пользовательские аккаунты. Native packet payload доказывает серверные team/trade данные; клиентский цвет/окно не объявлялись увиденными.

## Сборка и проверки

Предыдущие source commits сохранены без переписывания истории; причины и checkpoints — в PROGRESS068. Final core-v8 committed archive Ant jar+compile-tests: exit0/33s/2355product; полный archive/source/test receipt PASS. Отдельный штатный phantom-humanized-v3-content-validate exit0/30s/3of3. Ранее существующие два runFinalization warnings не связаны с068.

| Binding | SHA / SHA256 |
|---|---|
| Code |fd85c3103325eead1a1735e053af810d64fbc118|
| Committed archive |008FC595602A60D00F722C01231654EF24AE29C5172B9BEC644620D805FA74FE|
| Committed source tree |E4FF42A74D9ACFBA229C66B865958C0F77AD935A9C2E70F9B25A49377410B35B|
| GameServer.jar |D3A8908C05BCF3265971B9C6A2056513DA4A04F43652AC4E3AB871050D67FC43|
| LoginServer.jar |34EBE523D1C27EF7C132978442B26BB4E68FE762C726A021C72A9A3514F38FDA|
| Test/resources tree |A54DD9ECDB6337CE4110D77C90AE3CA10D64C76CD24353FDD854E8C0017FBAAD|

Jar-class comparisons сохраняют4547entries: v2→v3 изменили11VisibleTravel/Exporter, v3→v4 только7Authority, v4→v5 только16HistoricalService, v5→v6 только2Career, v6→v7 только28HistoricalPlanner/HistoricalService/VisibleTravel, v7→v8 только17BackgroundService/VisibleTravel entries. C/D/F/G class bytes прежних committed native proofs сохранены; E exporter не менялся послеv3. Это provenance адресных native доказательств, не повторный запуск всех сцен наv8 и не замена Main/transaction proof.

Pure targeted checks: purchase budget3, resource matrix275realD/C+ordinaryjunk+6surplus cases, viability2, combat debt2, pricing3, equipment2, champion policy2, commerce-decision7, merchant bootstrap и runtime/Rift guards. На exact committed v8: visible-itinerary0684/4, historical-idle-visible0683/3, background-model10/10 PASS. Career-choice-admission0681/1 наv6, этот class не менялся. Pure itinerary/planner tests не объявляются native checkpoints. Общий ant verify с чужими DB gates не запускался.

Committed v3 native: `champion-native-committed-v3.log`2/2; `rift-native-committed-v3.log` SOLO/TERMINAL/ADMISSION/PARTY2 PASS. `rebalance-committed-v3.log` CLI4/4. Native TEST Alt+B purchase выполнен на прежнем build-dev и1/1 Plan; committed repeat доказывает canonical CLI, не повторную purchase. Ошибочные caller seed/report-property/PowerShell5 UTF8 invocation сохранены как ошибки команд, не product RED; исправленные targeted commands прошли. R2 commerce-decision с обязательным140014 прошёл7/7.

Native C v2: `merchant-native-core-v2-human068.log`, `merchant-200-1792665583550.tsv`;30s CPU234375000ns/wall30057815400ns, heap3199359200, threads122, global SQL Questions delta3. Это короткий scale sample, не долгий soak и не точный SQL write benchmark. Bootstrap20100000/499927988 не вырос. Ordinary stock deleteMe warning в cleanup после native close сохранён; exit0/pools closed. История неудачных fixture prerequisites и настоящие RED/GREEN находятся в [PROGRESS068](../tasks/PHANTOM-LIVING-ECONOMY-068/PROGRESS068.md) и профильных handoffs, не выдаются за новые product failures.

## Доставка и сохранность — ещё выполняется

Собственные schemas `l2jmobiush5_phantom_test_068` / `l2jmobiush5_phantom_release_068`, MariaDB127.0.0.1:3308, pool4. Обе созданы один раз121stock scripts/124tables; aggregate SQL SHA2562CD503128136E3E854F9EFF24236DD9F286CA58D9E950ACB9B2842E78B910E28. Никаких reset/reseed/reimport, изменений общего MariaDB или миграции персонажей владельца.

TEST ordinary runtime `.phantom-local/task068/runtime-test` отдельный от component `native-test`, использует ту же единственную TEST schema. TEST ports2249/7949/9249; candidate2149/7849/9149. Protected057–067/MAIN/PLAY/client/Studio/Account_Manager не запускаются и не меняются. Source/read-only входы перепроверяются адресно:4protected files,6owner package inputs,203Geo originals и физические203копии каждой068поставки.

Финальные BUILD_INFO/SOURCE_BUILD_INFO/TOOLS/GEODATA, PROFILE/ACCEPTANCE/HANDOFF, штатный STOP с JVM0/listeners0/DB0, exact scope/encoding и normal push verification ещё требуются. Никакого claim полной доставки до них нет.

## Один конечный остаток

Не закрываются автоматически: незавершённые A/B/native integration checks; групповые роли и raid participation с владельцем; индивидуальные PvP/PK и последствия; прочие поддержанные instances/M7 routes; большие имена/кириллица; Semantic Pack+Studio+runtime context; длительная устойчивость/нагрузка. Выполненные067 Cubic/fishing/Henna/augmentation повторно в остаток не добавлены. Следующий Goal/автоматизация не создавались.
