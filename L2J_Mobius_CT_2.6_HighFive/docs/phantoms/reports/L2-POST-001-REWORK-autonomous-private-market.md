# L2-POST-001-REWORK — автономный частный рынок Phantom

Status: SUCCESS / VERIFIED. Дата: 2026-09-15. Ветка: `feature/phantom-world`.
Exact parent локально и на `origin/feature/phantom-world` перед изменениями:
`085553d13c2b93e8133e320d54b7fd14371690ee`.

## Результат и архитектура

До rework production вызывал только `PhantomStoreService.shutdown()`; собственные
`quotePlan/open/restore/close` были доступны и проверялись тестами, но ни один
Decision/Scheduler owner не создавал собственную private-store заявку. Теперь
один `PhantomAutonomousMarketProducer` включается в существующую цепочку общего
scheduler. Он берёт реальные materialized ACTIVE Player/inventory, сохранённую
acquisition-цель и economy reservation state, создаёт только одну строку SELL
из безопасного материального излишка или BUY из ограниченного дефицита,
проверяет доступные Adena и вызывает канонический `quotePlan()` до durable
`PhantomStorePlan`. `open/restore` по-прежнему независимо перепроверяют hash и
цену через принятую POST-001 authority. Нативный `TradeList` получает тот же
object/count/price; никаких предметов или Adena producer не создаёт.

SELL оставляет не менее половины stackable материала и допускает equipment
только как неэкипированный дубликат предмета того же ID, который действительно
есть в paperdoll. Активная неизвестная цель, acquisition target/BOM, занятая
economy операция, quest/hero/time-limited/nontradeable предмет и native
trade/store ownership закрывают кандидат. BUY допускается только из принятого
активного `acquire.item` state с выбранным source и текущим дефицитом; строка
не превышает остаток потребности и настоящий Adena budget. Pricing authority,
NPC anti-arbitrage, recursive recipe floor и expected enchant valuation не
менялись. Автономный MANUFACTURE остаётся fail-closed: в H5 пока нет
подтверждённой ненулевой платы за услугу, а бесплатный сервис публиковать нельзя.

Native owner сидит во время магазина, а in-memory observer `PhantomStoreService`
закрывает admission новых Decision actions этого profile. TTL, изменение
goal/activity/stock/reservation и смерть сверяются на общем scheduler pulse;
после close Player встаёт. Однострочный guard в `Player.setPrivateStoreType(NONE)`
сохраняет активную headless outbound-сессию: штатный offline-trader disconnect
больше не удаляет Phantom из World при закрытии его магазина. Crash-retained
durable планы просматриваются одной ограниченной страницей, допускаются только
при живой eligibility и точной повторной котировке; stale hash закрывается.

## Scope и конфиг

Изменены только High Five `build.xml`, `PhantomMarket.ini`,
`PhantomMarketConfig.java`, `Player.java`, `PhantomSystem.java`,
`PhantomStoreService.java`, `PhantomTestLauncher.java`; добавлены
`PhantomAutonomousMarketProducer.java`, `PhantomAutonomousMarketSuite.java` и
этот отчёт. Шесть файлов предоставленного rework package, включая `/goal`
launcher и manifest, сохранены локально read-only. Три исходных пользовательских
modified файла и прочие untracked task packages не включаются в task stage.
Другие хроники, клиент, public GamePackage schema и pricing math не менялись.

`EnableAutonomousPhantomMarket=True` управляет production producer. Default
limits: одновременно не более 2 магазинов и не более трети ACTIVE population
(при двух ACTIVE — один, при одном — ноль); TTL 120 секунд; повтор того же
owner не раньше 600 секунд. Одно planning attempt не чаще 15 секунд,
детерминированная parity меняется раз в 15 минут. Все ключи имеют прямые
русские комментарии о назначении, диапазоне, default, стоимости и restart.
Отсутствующие/некорректные ключи отключают producer при старте.

## Измеренная и расчётная стоимость

Focused native gate: 3/3 PASS; реальные ASK/BID по материалу 1865 —
432/390 Adena, +4 дубликат gear 70 — 37 544 850 Adena. Одна попытка
просматривает список materialized actors O(A) и inventory одного owner O(I),
удерживает не более 24 SELL кандидатов, делает максимум четыре BUY и две SELL
`quotePlan` котировки плюс одну независимую `open` перепроверку. В худшем
случае принятой текущей H5 authority это не более семи O(W) снимков
`World.getVisibleObjects()` на 15 секунд, независимо от числа Phantom; число
активных магазинов S ограничено 0..16 конфигом. На каждом scheduler pulse
сверяются только S открытых owner; recovery читает одну страницу не длиннее
configured maximum раз в planning interval. Новый global merchant cache не
вводился: при этом верхнем bound свежая видимость Merchant, limited stock и
castle tax остаются точными на каждом quote/open. На значительно более крупном
World стоимость W следует измерять отдельно до изменения stock/tax seam.

Producer не телепортирует actor ради торговли: магазин появляется лишь там,
где текущий native `canOpenPrivateStore()` разрешает его и Player действительно
idle. Это сохраняет NO_STORE/NPC exclusion и обычное участие в контенте.

## Проверки и delivery

- `ant -q phantom-post001-autonomous-market-test`: PASS 3/3, реальные SELL/BUY
  native сделки, точные actor deltas, сохранение предметов/Adena, replay и
  overdraw rejection, cooldown/TTL, reservation/goal/trade/NO_STORE exclusion,
  stale и exact restore, +4 object identity, MANUFACTURE fail-closed.
- Все пять POST-001 pricing gates: PASS; native private-store BUY/SELL/manufacture
  и economy reservation concurrency: PASS.
- Goal022 affected aggregate, acquisition source planner, Decision core и
  persistence, background lifecycle: PASS.
- Релевантные QOL-009/Goal039 focused freeze gates: PASS.
- Один свежий полный `ant -q verify` после green preflight: BUILD SUCCESSFUL,
  36 минут 16 секунд; POST-001 route в полном прогоне 3/3 PASS.
- Использована только выделенная `l2jmobiush5_phantom_test` через существующий
  guarded bootstrap. Production DB не использовалась; provisioning и
  `prepare-phantom-test-db` не запускались.
- Mojibake-маркеры в изменённых файлах проверены отдельно: совпадений нет.
- Escaped Cyrillic в изменённых файлах проверены отдельно: совпадений нет.

Exact commit subject: `post(001): activate autonomous phantom private market`.
Commit SHA и результат non-force push приводятся в финальном сообщении после
создания и публикации commit: SHA самого commit нельзя вложить в его hashed
содержимое. Следующий шаг — проверка exact staged allowlist, один commit,
normal push и сравнение remote SHA с локальным HEAD.
