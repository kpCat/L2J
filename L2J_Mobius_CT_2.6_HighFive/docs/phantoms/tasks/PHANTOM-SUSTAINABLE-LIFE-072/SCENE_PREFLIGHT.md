# SCENE_PREFLIGHT072 — предотвратить повтор Wolf4/11,569км/CP-Z

## A. Read-only native world search до создания measurable actor

1. Загрузить собственную isolated TEST072 с реальными GeoData203, native `SpawnData`, `MerchantPriceConfigTable` и `PhantomCommerceCatalogLoader` (как в `PhantomSupply071JourneyNative.selectScene`). Составить bounded список **фактически появившихся** нормальных Monster и Merchant, максимум 3 candidate areas / 30 секунд первичного чтения, затем до 30 секунд проверки маршрутов. Не запускать full world scan каждый тик, не ограничивать выбор первоначально ТОЛЬКО lvl2..8.
2. Кандидат: Player sample-level внутри **20..39** для D-grade (если нет годного источника, выбирать и документировать иной совместимый 20+). Требовать native mob EXP/SP, уровень rewardable для sample Player: вычислить stock reward level gate `MonsterExpMaxLevelDifference` и приоритет ±3..5 уровней. Mob lvl4 для actor19, несмотря на положительный mob.EXP в знаниях, НЕ подходит. `victim.isRaid()==false`, normal instance0, peace zone excluded, alive/spawned, visible/LoS. Evaluate `actual expected positive reward`: fallback actor-scoped source logic, не просто `fact.exp()>0`.
3. Требуется точный продаваемый compatible shot item (для D оружия `1463`), buylist + native spawned NPC + current tax+item reference price, или достоверная актуальная физическая offline SELL с лимитом наличного stock. `30081/3008100` — полезный кандидат, но НЕ предписанная сцена независимо от мира. Katerina30004 НЕ предлагать D shot1463 на основании продажи 1835.
4. Bounded forward+return travel: native GeoEngine altitude/`walkSurface`, real `findWalkCorridor` with bounded time; require farm point, merchant interaction approach, back to farm point; direct segments+intermediate anchors; no faked direct teleport between farm and NPC, no `SEARCH_LIMIT` treated as impassable. Normal Gatekeeper allowed only with actual fee, supplied normal route and native budget. Для единой короткой тест-сцены идеальна дистанция <=2км, max5км, иначе другой area.
5. Preflight receipt `SCENE072.tsv` **до Player creation**: candidate source NPC/template/objectId XYZ/instance/buylist/quoted itemID+price; mob template/object ID/level/EXP/SP/radius/XY+GeoZ/line-of-sight; proposed actor class/level/weapon grade/item source, reward gap and currency estimate; both route statuses/distances; evidence screenshot не нужен. Если не найдено — `SCENE_INPUT_MISSING` c best 3 failure reasons, не создавать неадекватный actor.

## B. Fresh fixture lifecycle

6. TEST072 only: создать новый actor в выбранной legal starting area **перед measurement**. Минимальные setup fields (level/known skills/one ordinary weapon/HP/MP) можно получить из штатных механизмов и раскрыть как `FIXTURE`; **нулевой earned wallet и purchased stack** желательно явно зафиксировать. Если занят NPC spawn point, выбирать пешую поверхность в 100–180 units и validate exact `isInsideRadius3D`, `canSeeTarget`, safe zone. Оригинальные TEST071 profile1/retained facts не трогать.
7. ВНИМАНИЕ: `Player.load` использует `GeoEngine.getHeight(x,y,savedZ)` и фактически меняет Z. После первого настоящего materialize/save/reload записать и **проверить** `actualXYZ` для actor/NPC и 3D interaction. Если не подходит — до начала измерения разрешена **новая** premeasurement fixture в другом корректном месте (старая не уничтожается/не пересеивается); либо native walk (без изменения radius, item charges). После `MEASUREMENT_START` никакого `setXYZ/spawnMe(new XYZ)/teleportTo` ради green.
8. Фиксировать marker-состояния `PREFLIGHT_ACCEPTED`, `SETUP_DECLARED`, `MEASUREMENT_START`, `NATIVE_RECEIPT`, `RESTART` в отдельном не перезаписываемом журнале. Предыдущие failed logs не затирать. Контрольный по-настоящему заработанный item/Adena обязан иметь различимый event/provenance, не путать с premeasurement item grants.

## C. CP merchant отдельный short slice

9. Для Helvetia30081 источник `dist/game/data/buylists/3008100.xml`, CP5591/5592, cost200/500A+tax. В TEST072 отдельный actor с явно premeasurement `2000A` может доказывать **покупку**, но не общую устойчивость экономики. Создать на legal Geo point in same layer; materialize -> record `npcXYZ actual+actorXYZ actual`, dx/dy/dz, `distance3D<=native250`, LoS, `player.getLastFolkNPC()` binding if native flow requires. Не увеличивать radius и не переносить после `MEASUREMENT_START`; действительный NPC spawned object must remain the same. Если проиграна геометрия — сохранить FAIL и подобрать fresh premeasurement scene до повторной покупки.
10. PASS только при настоящем `PhantomCommerceReceipt.State.COMMITTED`, Adena debit + item count delta и повторном native item handler usage/reuse. `REPLAN NPC_OUT_OF_RANGE` — не PASS. Negative: wrong NPC/objectId, NPC unspawned, wrong instance, too far, nonexistent source, insufficient Adena.

## D. Что никогда не делать

- Не исправлять EXP-фильтр изменением всего `Rates.ini` либо моба/персонажа после измерения.
- Не считать `VICTORY` полноценным reward, если deltaEXP/SP==0.
- Не считать 12км search result настоящим route end. Для обычного игрового background пути возможны штатные Gatekeeper/anchors, но это другой тест, не замена life-chain.
- Не добавлять бесплатного NPC, invisible vendor, fake quote, direct SQL item grant, in-game GM, Player client impersonation.
