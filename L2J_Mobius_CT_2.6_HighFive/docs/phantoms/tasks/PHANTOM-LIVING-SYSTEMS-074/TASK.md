# PHANTOM-LIVING-SYSTEMS-074 — единая разработка живого поведения

**Цель:** за ОДНУ кампанию улучшить четыре связанные игровые способности автономного фантома: (A) снабжение и завершение настоящей сделки после заработка, (B) выбор новых спотов и дальнее путешествие, (C) законный сбор/сбыт добычи, (D) выживание и отступление в PvP. Это четыре продуктовых результата, а НЕ четвёртая задача по тестированию одной покупки сосок.

## 1. Исходные данные

- Основная репа: `D:\Codex\repos\L2J-phantom-world`, единственный checkout и ветка `feature/phantom-world`; module `L2J_Mobius_CT_2.6_HighFive/`. Исходный опубликованный HEAD на момент подготовки: `6057cc69728e5050d79806e5e6aa3c5a2bf898c8`; сначала проверить live local+remote, не сбрасывать более новые коммиты/чужие правки.
- JDK25/Ant, native L2J Mobius High Five, GeoData203, MariaDB11.4 на3308. ИИ — прежние Java native owners, **не** новая LLM/planner/worker архитектура.
- Базовые доказательства: `docs/phantoms/reports/PHANTOM-EARNED-LIFE-073.md`, `.../PHANTOM-EARNED-LIFE-073-data/{ACCEPTANCE073.tsv,CORE_ATTEMPT073.tsv,LIFE_CHAIN073.tsv}`, отчёты072/071 — только соответствующие части. Нулевые слои старых тестов не пересоздавать.
- Уже PASS: 35 настоящих rewarded kills и +1647 заработанных Adena, сохранение wallet/loot и ранних earned денег через JVM; quote+готовый `commerce.buy` intent на62×1463 по21A с balance1747A. `P0_BUY` NOT_OBSERVED — после `supplies.navigation.local_checkpoint_retry` и `supplies.navigation.cooldown` COMMITTED receipt не появился; TEST073 актор позже штатно погиб/возродился. 17/35 `VICTORY_LOOT_BLOCKED` требуют классификации, **не означают автоматически 17 потерянных наград**. Дважды PvP preflight072 возвращал `NO_ROUTE_WITHIN_BUDGET`, один раз `SEARCH_LIMIT`; native PvP с отступлением не начался.
- Отдельные OWN071/072/073 акты, БД, артефакты, private inputs и кандидаты защищены; читать уже опубликованные логи разрешено, писать туда НЕЛЬЗЯ. TASK073 живого сохранённого актора НЕ использовать для нового измеряемого эпизода.

## 2. Состав работ — именно четыре игровые улучшения

### A — АВТОНОМНОЕ СНАБЖЕНИЕ (HARD, первый приоритет)

1. Адресно установить **причину** повторяющегося `supplies.navigation.local_checkpoint_retry` в `L2jPhantomSupplyJourney.navigate/acceptRoute/travel`. Сопоставить trip.origin, request destination, native checkpoint, Geo floor, stage/waypoint, statuses, deadlines, route budget, observable movement, actual merchant location, `Trip` ownership и pending receipts. Сохранить при read-first конкретный отказ и объяснение, не придумывать root cause заранее.
2. Исправить подтверждённую ошибку в прежних `SupplyJourney`/navigation/Geo/actor owners. После лимита локальных попыток должно быть **действие**: альтернативный уже законный corridor/merchant/GK route, разумная отсрочка и свежий replan с новым witness либо устойчивый `SOURCE_UNAVAILABLE` с сохранением денежной цели. Не допускать бесконечного same-state retry; `SEARCH_LIMIT` — только предел данной попытки поиска, не окончательная непроходимость.
3. Поддержать сценарий `earned wallet/finite prior savings -> physically paid matching-grade Soulshot -> return to useful farm`: реальный native NPC/private shop/receipt, без free item; после покупки бить подходящих мобов реальным заряженным shot, расходовать стек, продолжить после sameDB restart. Заработанная Adena до покупки положительная; если в измерительной сцене есть разумная **объявленная** сумма, полученная персонажем до начала эпизода, не объявлять её заработанной этим тестом. Не требовать ещё35 убийств, если нужен только маршрут: принятые earned proofs073 остаются фактами истории; новая задача доказывает *соединение механизмов*.
4. При убывании средств/недостатке выгодных предметов не убивать root FARM, не удалять набитый лут, сохранять цели для следующего притока денег. Изменения игровых tax/reserve/price/XP-rates и отключение `usefulPurchase` для прохождения теста запрещены.

### B — ВЫБОР СПОТОВ И ДАЛЬНЯЯ НАВИГАЦИЯ (HARD)

1. Найти связку `PhantomHistoricalBackgroundService` → `PhantomHistoricalBackgroundPlanner.replanVisibleLocal` → `PhantomVisibleFarmTravel`, которая после локальной неудачи (~2км area) не всегда рассматривает правомерные другие anchors. При доказанном исчерпании награждающих мобов, устойчивом отсутствии полезных целей, возросшем уровне или опасном споте: **bounded global/topology fallback** через existing ranked sources, не бесконечное сохранение старого `npcId`.
2. Приоритеты: настоящие EXP/SP и loot, вероятность победы/риски/HP/MP, пригодность learned skills/grade/shots, реальная транспортная цена/время, overcrowding/party/instance/zones; моб на+1/+2 уровня может быть приемлем. Дистанция влияет на оценку, но не глобальный запрет5км. Никакого полного обхода45тыс. объектов каждую AI итерацию.
3. Пути — доказанные GeoEngine/PathFinding bounded WALK, topology anchors и легальные Gatekeeper/fees/requirements, затем earned/bound background travel только если уже разрешено жизненным циклом/видимостью. Ни teleport обхода препятствий, ни смерти в лицах игрока, ни отмены party/store/claim.
4. Понятные ограниченные retry/cooldown/hysteresis: same failed source не выбирать каждый pulse; сохранение plan/position/owner после restart. `NO_PATH` на конкретном маршруте не значит, что весь континент закрыт.

### C — ЛУТ И ДЕНЬГИ (HARD, узкие исправления, а не новая экономика)

1. Для серии073 `VICTORY_LOOT_BLOCKED` и `VICTORY_LOOT_PARTIAL` собрать по существующему `PhantomCombatService.processLoot`, `L2jCombatBackend`, `PlayerNativeEvidence`: есть ли реально legal candidate, pickup ack, чужая защита, owner/party distribution, инвентарь/вес, Geo/distance/time, исчезновение объекта или похищение другой стороной. **Не трактовать метку результата как доказанный потерянный item**.
2. Исправить лишь воспроизводимый дефект: неудачный/чужой pickup не должен зацикливать фарм или убивать все следующие попытки; законный выпавший Adena/material/items подбирать без требования `killer == picker`, соблюдать stock rights/party/quests/sweep/weight. Если лут уже пропал или защищён — корректно пропускать, а не выдумывать предмет.
3. Для экономически лишних вещей использовать существующие `PhantomLootDispositionPolicy`, `L2jPhantomSupplyJourney.maintenanceProposal`, NPC SELL/warehouse/offline. Не продавать актуальное оружие, нужные shots, квестовые и ценные ресурсы без policy/quote/rights. Проверить хотя бы одну реальную legal sell/keep decision, при наличии разрешённого surplus — физическую SELL с receipt. Не создавать новый market engine.

### D — PvP/PK-ВЫЖИВАНИЕ И ВОЗВРАЩЕНИЕ (HARD, отдельный владелец результата)

1. `PhantomCombatService`/`PhantomPvpService` уже обрабатывают `SUPPLY_REQUIRED`, `LOW_HP_STOPPED`, useCpPotion и skill-first admission; прежний `PhantomPvpRetreatCoordinator` ведёт к nearest RESPAWN/CITY_CENTER, что не всегда достижимо в коротком бою. Добавить **короткий безопасный tactical fallback** к существующему retreat без второго AI engine: возможные Geo-valid промежуточные укрытия/точки разрыва дистанции, затем при возможности безопасный anchor/маршрут. Никакой гарантированной победы/неуязвимости/покупки на месте.
2. В native PvP сохранить ограничения зон, karma, target legality, союзов и обычного Player; реальные learned skills, shots для20+ и 5591/5592 с native500ms reuse, не фейковать эффекты. При отсутствии расходников — закончить наступление, не наносить безshots offensive hit, отойти/выждать/восстановить полезное снабжение. Если route не найден — заявить конкретный fallback/stop, не ложный ARRIVED и не бесконечный attack.
3. Доказать один короткий приёмлемый PvP эпизод либо честно локализовать ранее скрытый blocker: реальный противник → native физические skills/shots/CP → depletion/lowHP → безопасное действие → последующее намерение снабжения/восстановления. Отдельные случаи конфликтов за спот/клановую войну не делать глобальным blocker074.

## 3. Общие технические контракты

- Не создавать второго AI scheduler, ещё одной экономики, новой БД/товарного каталога, PathFinding library, собственного Player implementation, LLM-зависимости, фальшивого GameClient, packet bypass или конфигов ради одного теста. Править существующих owners, API только минимально необходимый.
- Поведение не может быть достигнуто упрощением combat/loot/merchant/native access или бесплатной генерацией наград. Native Java Player и item objects остаются authoritative; расходные receipts/idempotency/version/lease, party/store/persistence неизменны.
- Главный лимит: **целевое выполнение4–5 часов, HARD7 часов** с момента первого действия Codex. Product freeze в6 часов; последние60мин — final validation, release, STOP, report, git. Приоритет A→B→C→D по необходимости интеграции, но с обязательным временем/видимыми изменениями на ВСЕ четыре направления. При раннем завершении — остановка сразу, без soak.
- Четыре independent gates; один не закрытый не мешает безопасно коммитить оставшиеся три. **TOTAL074=PASS только при PASS всех HARD**, иначе честный PARTIAL с перечислением новых работающих способностей; `DELIVERY=PASS` не заменяет игровой PASS. Исторические FULL071/072/073 не переписывать.
- Вместо копирования огромных TASK071/072/073 инфраструктур — один компактный owned074 тестовый runner и reuse уже существующих точечных suites/строгих скриптов только где обязательно. Не создавать10тысяч строк тестовой обвязки вместо игровых функций.
- Запрещено выполнять следующий R2/075 автоматически. Завершение — один итоговый отчёт, без требуемого owner входа/ручного GUI.

## 4. Обязательные файлы пакета

Прочитать `PRODUCT_DECISIONS.md`, `SOURCE_MAP.md`, `ROOT_CAUSE_START.md`, `TEST_PLAN.md`, `ACCEPTANCE.tsv`, `AUTHORITY.md`, `DELIVERY.md`; они уточняют политику, адреса и доказательства. При конфликте — безопасность/root AGENTS, затем явное решение в TASK074 и AUTHORITY074. Не «улучшать» бизнес-правила молча.
