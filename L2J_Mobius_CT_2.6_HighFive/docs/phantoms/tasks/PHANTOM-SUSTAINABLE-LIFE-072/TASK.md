# PHANTOM-SUSTAINABLE-LIFE-072 — непрерывный автономный игровой цикл

## 1. Идентификатор и зависимость

`PHANTOM-SUSTAINABLE-LIFE-072`. Репозиторий `D:\Codex\repos\L2J-phantom-world`, ветка `feature/phantom-world`, модуль `L2J_Mobius_CT_2.6_HighFive`. Зафиксированный опубликованный базис после TASK071: `031ed5a3b73a44ba31d71d041a897a4f9247f16e`; фактический local/remote HEAD сверить **до** работы. Если есть новые коммиты — read-first сравнить, не откатывать их. Previous: `docs/phantoms/reports/PHANTOM-LIVING-COMBAT-071.md`, `-data/ACCEPTANCE071.tsv`, `-data/POST_PUBLICATION_AUDIT071.md`. Новый этап не отменяет честный `FULL071=PARTIAL`.

## 2. Цель — один законченный игровой опыт

Не создавать универсальный AI заново. Из существующих `PhantomCombatService`, `PhantomPvpService`, `L2jPhantomSupplyJourney`, `PhantomNativeShotControl`, `PhantomNativeEquipmentPolicy` получить **доказанный конечный путь** обычного материального фантома: правильно выбрать место реального фарма -> сражаться с мобами подходящего уровня -> получить настоящие EXP/SP/валюту -> оплатить реальные соски нужного грейда -> вернуться -> снова получить EXP/SP и потратить купленные соски -> после sameDB/sameJAR restart не потерять профиль, деньги, предметы, задачи или оплаченные операции. Отдельно: native покупка CP Potion с корректной геометрией; одно короткое причинное PvP с расходниками и уходом при истощении. Улучшение экипировки — один экономически честный шаг, не фиктивные S84-комплекты.

**Отличие от TASK071**: 071 создал lvl19 Player возле lvl2–8, поэтому Wolf lvl4 дал VICTORY/0 EXP/SP; ближайший rewarded target оказался 11,569 км при лимите 5 км. Для CP NPC30081 XY отличалось на16, но итоговая Z персонажа после `Player.load` отличалась от NPC на426, что нарушило native 3D-radius250. Это прежде всего **неудачные native fixtures**, не доказанные ошибки production. Ни один старый актёр071 не переделывать ради PASS. Полный код и факты — `SCENE_PREFLIGHT.md`.

## 3. Бюджет и режим выполнения

**Цель 1,5–3 часа; верхний предел 5 часов от первого действия.** Продуктовые изменения заморозить после4ч, последний час — focused tests/assembly/report/normal commit+push. Досрочный выход сразу после результата; не отрабатывать лимит. 12-часовые марафоны, repeat-Main по10мин×N, рейд/soak/все грейды и сотни однотипных тестов запрещены. Если сцена не находится за оговорённый бюджет — честный `BLOCKED_INPUT` с координатами/источниками, переход к другим независимым этапам. Не просить владельца играть/TestAdmin.

## 4. Обязательный предварительный аудит

Прочитать root `AGENTS.md`, module `Agents.md` если существует, `docs/phantoms/{CODEX_WORKFLOW_CONTRACT,RELEASE_CONTRACT,PHANTOM_CURRENT_STATUS}.md`, пакет072 целиком, только относящиеся разделы reports066/068/069/070/071. `git symbolic-ref --short HEAD`, `git status --porcelain` и точный read-only remote HEAD. Уважать неизвестные untracked/modified. Read-first пройти `READ_SET.md`: существующие native owners, source shot/D-grade, 071 failed conditions, Test Main receipts. **Не** считать исправление fixture продуктовой работой.

## 5. Архитектура: только существующие owners

- Фарм/боевой исход: `PhantomCombatService`, `L2jCombatBackend`, `PhantomVisibleAutoPlay`, `PhantomFarmingService` и native Player/Monster/Skill. Reward, EXP/SP, loot — только реальные нативные события.
- Снабжение: `L2jPhantomSupplyJourney`, `PhantomCommerceCatalogLoader/Catalog`, `L2jCommerceBackend`, `PhantomCommerceReceiptStore`, `PhantomOfflineSupply`, `PhantomNormalGatekeeperTravel`, `PhantomNavigationService`/GeoData. Существующий wallet/budget, warehouse-first и stock NPC/offline supply. Не делать второй scheduler, independent AI engine/таймер или параллельную систему дохода.
- Экипировка: `PhantomNativeEquipmentPolicy` и native class expertise + реальная покупка/drop/craft. Выбираем один доступный переход, не раздаём B/A/S80/S84 тестовыми товарами в доказательство экономики.
- PvP: `PhantomPvpService`, `PhantomPvpContext`, `PhantomPvpPolicy`, `PhantomPvpSocialBridge`, `PhantomCombatService`, `PhantomNativeShotControl`, `PhantomPvpRetreatCoordinator`. Соски и CP — настоящие предметы, 071 already made skill-first/CP/retreat code, не переписывать.

## 6. Приоритет A — одна честная native life-chain (HARD)

A0 **Preflight before fixture**: read-only выбрать конкретный участок мира с spawned normal mobs, у которых расчёт EXP/SP допускает соответствующий уровень актора (см. `Rates.ini`, `MonsterExpMaxLevelDifference=11`), и настоящего NPC/публикуемого offline-магазина, продающего совместимые D/C shots; настоящие двусторонние GeoEngine/PathFinding/Gatekeeper маршруты; бюджет передвижения <=5км в упрощённой сцене либо native bounded lawful route из существующей topology, без слепого 11км WALK и без `SEARCH_LIMIT`=непроходимость. Проверить фактические NPC/моб world XY/Z/instance/LoS и допустимые зоны. Уровень и источник выбираются **совместно**. Известный потенциальный магазин: Helvetia NPC30081, `data/buylists/3008100.xml` продаёт D Soulshot1463, NG Soulshot1835, CP5591/5592; его наличие в конкретной подходящей сцене **не предполагать без проверки**. Подробный алгоритм — `SCENE_PREFLIGHT.md`.

A1 Создать **нового** обычного lvl20–39 тестового Player/Phantom только в own TEST072, с одной объявленной минимальной совместимой pre-measurement equipment fixture (если необходима для нативного боя). Любые стартовые деньги/соски/баффы/HP/skills/оружие пометить `FIXTURE`; **не считать** их добычей. Предпочтительно нулевая стартовая Adena/shot после materialization. `Player.load` меняет высоту по GeoData — выбрать и проверить окончательную reachable standable точку **до измерения**. После первого зафиксированного кадра НИКАКИХ teleport, `setXYZ`, бесплатного финансирования, вручную добитых мобов, придуманного EXP, ресета HP/loot/goal/clock или замены того же профиля новым.

A2 **Earned-before-spent**: реальные normal mob kills (не lvl4 при actor19) -> положительные native EXP/SP и полученная Adena/законный item loot; подобрать законно, если необходимо продать реально добытый surplus через native SELL, затем native оплаченный BUY корректных сосок (D1463 для D оружия и т.д.), `COMMITTED` с itemID/count/price/account/actor/stock; затем реальный возврат к доступным reward mobs и как минимум **две** native победы после покупки, с положительными EXP/SP, actual shot debit. До покупки можно ограниченно убивать без сосок в рамках настоящей shortage recovery, после покупки долгий unshot farm>=20 недопустим. Не превращать одну заранее выданную sell-fixture в `earned`! Если недостаточно реального дохода за ограниченный тест — прозрачно `PARTIAL_EARNINGS`/`BLOCKED_ECONOMY`, никакой выдачи денег.

A3 Сохранить **этого же** актёра/профиль/инвентарь/goal/receipts, штатно STOP и сделать sameDB/sameJAR restart в собственной TEST072. Новая полезная native жизнь: хотя бы одно последующее действие с доказанным эффектом (kill/reward/paid purchase/lawful loot) после рестарта, не только +метры до NPC. Сверить XP/SP/currency/item object, stock, epoch, phase, no dup/no recompute. Реальная продолжительность bounded: один короткий episode до и один после restart, без часового farm.

## 7. Приоритет B — CP и PvP-связка (HARD bounded)

B1 **CP paid**: использовать stock Helvetia `30081` / buylist `3008100`, предметы CP `5591` (50) и `5592` (200), фактическая reference price200/500 + native tax. В отдельной собственной TEST072 актёр может получить **явно записанную finite purchase fixture 2000 Adena** только ради проверки механики приобретения (НЕ `earned`). После `Player.load/materialize` записать actual NPC objectId XYZ+instance, Player XYZ+instance, dx/dy/dz, dist3D, `canSeeTarget`, возможную высоту walkSurface, native interaction250 и source quote. Если 3D далековато, пересоздать свежую premeasurement fixture (не мутировать измеряемую) либо законно подойти native MoveTo; NPC 3D radius не увеличивать и не bypass. Закончить оплаченной native покупкой хотя бы 5592 + физическим item/денежным `COMMITTED` receipt и корректным использованием в PvP с native CP delta / reuse/Olympiad negative. Существующий код CP-use из071 не переписывать без причины.

B2 **PvP shot exhaustion**: один короткий настоящий PvP/PK session (с настоящим уместным `ACTUAL_ATTACK` или `FARMING_ESCALATION`/`PARTY_DEFENSE`/`REVENGE`, без подмены cause) использует learned skill и подходящие shots, физически уменьшает stack. При нуле: не атакует наступательно и не заряжает фальшивые shots, завершает `SUPPLY_REQUIRED` -> native retreat/replan -> одна осмысленная попытка lawful resupply, без глухого endless retry. Если есть достижимый магазин и законная Adena — PURCHASE COMMITTED, затем готов к будущему бою/фарма, не обязан без причины заново агрить прежнюю жертву. Адекватные cooldown/safe zone/PK karma/risk и defense при неожиданной атаке сохраняются.

B3 **Одна причинная проактивная сцена**: по возможности настоящий `FARMING_ESCALATION` spot dispute -> предупреждение/timeout -> законный удар/скилл при риске/сосках, plus негативный guard для друга/мирной зоны/истёкшего authority. Связанные `REVENGE`, `PARTY_DEFENSE`, clan-war допускают существующие focused tests и честное `PARTIAL_FULL_CAUSE` без 4 длинных реальных боёв. Не повышать частоту агрессии и не писать новый personality engine. Не требовать массового PvP и убийства персонажа владельца.

## 8. Приоритет C — grade/economic upgrade (MED, не фальсифицировать HARD)

На том же lvl20+ либо отдельном fresh TEST actor сделать **один** реальный paid upgrade: owned/warehouse/offline/NPC actual item source, native buy/sale/wallet/receipt, equip legal D+ candidate, then actual correct shot type after weapon change. Если в native world нет доступного предложения или за короткую earned-chain невозможно честно накопить цену оружия, оставить `PARTIAL_GEAR_ECONOMY` с конкретными источниками/ценой/недостающим earned budget и prove bounded durable `grade-debt`/replan. Сама тестовая выдача D/C оружия допустима только как объявленная **боевая fixture**, но НИКОГДА не засчитывается как приобретение экипировки. Существующие границы C40/B52/A61/S76/S80-80/S84-84, native expertise/grade conversion проверить focused без повторной раздачи 7 комплектов.

## 9. Приоритет D — optional precise close (не задерживать)

`EPIC_TIMER`: только при возможности за <=10min проверить `Economy070Shop(Economy070Pricing,LongSupplier)` в **own TEST072**: sold-out before due stays0, after due restock to1 maximum, paid claim and replay/restart no dup. Контролируемый clock dependency injection в тесте, **не системный перевод часов**, не ждать настоящие 14/30/60 суток, не менять боевые данные. `SKIP_OPTIONAL_WITH_REASON` допустим. Остальные partial TASK071 / рейды / олимпиадный AI / осады / TW НЕ включать.

## 10. Конфигурации, эффективность, безопасность

Не менять x1 Rates.ini, premium multipliers, фиксированные цены, лимиты сосок, Raid Curse, Queen Ant, Epic policy, Olympiad Token↔AA, Pushkin/Mammon, QoL, population/shift rules, присущие UI. No new price BAT or automatic Apply. Max4 skills policy и native acquired prerequisites preserve; reduce polling frequency, use existing scheduled pulses and bounded caps; no O(N²) world scans каждый тик и high-frequency INFO logs. No fake GameClient, hacked EXP/SP/drop, direct native inventory SQL to purchase without receipt, no player superuser/GM exceptions. Потоки, claims, action leases, inventory locks, shutdown and migrations preserve. Любая фикстура живёт только в own TEST072.

## 11. Данные и миграции

Создать ТОЛЬКО при отсутствующих собственных схемах: `l2jmobiush5_phantom_test_072` и `l2jmobiush5_phantom_release_072`, строго с `ownership072` и отдельными hexid; не пересоздавать старые. Reuse original070/071 economy schema+migration authority согласно `Economy070Shop.verifySchema(Connection)` (каталог должен совпадать с явными `phantom.economy.schema` + UUID, `economy071_authority.task_id=72`, source migration SHA). Не подменять DB071, не переклеивать identity, не копировать чужих TEST accounts. При неожиданном уже существующем 072 schema/root без exact ownership — STOP/BLOCKED. Commit receipts durable and idempotent. Подробно `AUTHORITY.md`.

## 12. Команды проверки и серверная поставка

Адресные existing test suites и new focused checks: например `PhantomSupply071JourneyNative` (read-only source reference; **не пересоздавать retained071 actor**), `PhantomCombat071Native`, `PhantomLivingCombat071Native`, `PhantomPvpSuite`, `PhantomSupply071Suite`; existing 071 FAIL никогда не стирать. `ant -f <committed-module>/build.xml jar compile-tests` в отдельном own build из committed archive с JDK25 и Ant; не использовать mutable working-source JAR как final evidence. Реальный `GameServer`/`LoginServer` с203 физическими GeoData, нормальный Main <=5–8 минут и один sameDB restart (если focused TEST already proves restart, не дублировать полно). Собственный release `D:\PhantomWorld\Sustainable-Life-072-candidate` и OWN072 STOP/STATUS/START. В финале все собственные JVM остановлены штатно, DB otherConnections0, ports/listeners0, чужие JVM/БД не тронуты. Public sanitized ZIP — только если оправдан, без credentials/DB backup и без upload1.2GB в Git.

## 13. Приёмка

Матрица `ACCEPTANCE.tsv` обязательна, PASS для каждого HARD только по фактическим native receipts; source-only и FIXTURE-only не могут заменить paid/earned outcomes. Report `docs/phantoms/reports/PHANTOM-SUSTAINABLE-LIFE-072.md` + `...-data/ACCEPTANCE072.tsv`, exact actual path/run/commit/JAR SHA/log ids, positive+negative checks, originals071 status. `SUSTAINABLE_LIFE072=PASS` только если `P0_SCENE,P0_EARNED,P0_RESTART,P1_CP_PAID,P1_PVP_SUPPLY,SAFETY,DELIVERY` доказаны; `P1_PROACTIVE` и `P2_GEAR` видимы отдельно, не маскировать их остаток общим PASS. Даже PASS072 **не** равен полномасштабному `FULL071=PASS` и не подменяет не доказанную в071 полную тактику.

## 14. Git и завершение

Коммитить только собственные точные изменённые source/docs/scripts/test файлы в `feature/phantom-world`, normal non-force push, сохранить фактический SHA и remote compare; никакого master/default, force/rebase, `git add .`, reset/clean/stash/drop, worktrees, неожиданного удаления. Любые чужие user modifications read-first и не stage. Если задача не завершена — `PARTIAL/BLOCKED` честно, безопасные commits и report по контракту. Кодировка UTF-8, отдельный scan mojibake/escaped Cyrillic, `git diff --check` exact scope.

## 15. Запрет типичного плохого решения

НЕ чинить тест lvl19/Wolf4 вручную увеличением EXP, снятием level-gap или переносом уже измеренного actor. НЕ увеличивать native radius250 ради CP. НЕ регенерировать бесплатные shot/CP/Adena. НЕ «закрывать» ошибку `PHASE_DEADLINE` сбросом legacy deadline. НЕ проводить четырёхчасовой поход к мобу за11,569км. НЕ объявлять FULL071/READY_OWNER_PLAY от одних метров пути, skill-call, исходного unit теста или компиляции.
