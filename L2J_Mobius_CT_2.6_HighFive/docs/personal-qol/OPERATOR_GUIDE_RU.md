# L2-QOL-001 — инструкция оператора

## Файлы и включение

- конфигурация: `dist/game/config/Custom/PersonalPremiumQoL.ini`;
- каталог tier: `dist/game/data/custom/personal-qol/level-gap-items.xml`;
- магазин: `dist/game/data/multisell/91001.xml`;
- страница: `dist/game/data/html/CommunityBoard/Custom/personal-qol/main.html`.

Поставка безопасная: оба ключа выключены.

```ini
EnablePersonalPremiumQoL=False
EnablePersonalPremiumShop=False
LevelGapItemsFile=data/custom/personal-qol/level-gap-items.xml
```

Для эффекта включите `EnablePersonalPremiumQoL=True` и перезапустите Game Server. Для магазина дополнительно нужны `EnablePersonalPremiumShop=True`, штатный `EnableCommunityBoard=True` и `EnableCustomCommunityBoard=True`. Модуль ничего из этого не включает молча. Изменения INI/XML требуют перезапуска.

## Предметы

| Item ID | Исходное имя H5 в инвентаре | Русская метка в Alt+B | N | DEMO цена |
|---:|---|---|---:|---:|
| 22290 | Recipe: Happy Cake - Event | Оберег разницы уровней: 5 | 5 | 1 000 Adena |
| 22296 | Cake Ingredient: Dark Chocolate - Event | Оберег разницы уровней: 10 | 10 | 5 000 Adena |
| 22297 | Cake Ingredient: White Chocolate - Event | Оберег разницы уровней: 20 | 20 | 20 000 Adena |
| 22298 | Cake Ingredient: Creme Fraiche - Event | Оберег разницы уровней: 40 | 40 | 50 000 Adena |

Это проверенные stackable inert H5 templates без handler/skill/action/timer/condition/reference-price поведения. Сами item templates не изменены, поэтому client patch не нужен и stock-клиент показывает исходные английские имена/иконки. Русские tier labels существуют только на серверной странице Alt+B.

## Семантика защиты

Сервер просматривает только четыре настроенных item ID в основном инвентаре reward actor. Если найдено несколько предметов или несколько tier, применяется один максимальный N. Предмет не расходуется. Склад не считается инвентарём: после перемещения со склада эффект появляется, после перемещения на склад исчезает; обычный relog сохраняет предмет и эффект.

Граница включительная и симметричная: при `abs(actual native level gap) <= N` только native DROP/SPOIL level-gap multiplier поднимается до нейтрального значения. Если native chance уже не ниже нейтральной, она не уменьшается. При `N+1` и дальше используется stock formula без поправки. Уже созданный drop/spoil не пересчитывается.

Reward actor остаётся штатным: для party/servitor действует тот Player, которого выбирает текущий native reward path. Предмет другого члена группы не создаёт ауру. Phantom/Fake Player reward actor всегда исключён.

Предмет не даёт Spoil skill, не меняет шанс успешного Spoil cast, не гарантирует выпадение или количество, не меняет RNG/check order, XP/SP, quest rewards/rates, manor и raid curse.

## Магазин и цены

Alt+B вызывает отдельный guarded route, который подготавливает native multisell `91001`. Generic multisell bypass для этого list ID запрещён. При execute повторно проверяются dedicated provenance и live `EnablePersonalPremiumShop`; поэтому prepared до выключения список после disable отклоняется до списания.

Оплата, ownership, capacity, inventory slots/weight и flood protection остаются штатными в `MultiSellChoose`/multisell engine.

Цены в `91001.xml` демонстрационные. Для замены цены измените `count` у соответствующего `<ingredient id="57">`. Для другой валюты измените `id` и `count`, убедившись, что template существует и экономика согласована. Новый товар добавляется штатным `<item>` с ingredient/production, но текущий QoL guard намеренно принимает только четыре ожидаемых tier-carrier; расширение allowlist требует отдельного кодового изменения и теста, а не только XML-правки.

## Откат

Установите оба switch в `False` и перезапустите Game Server. DB migration отсутствует. Купленные предметы останутся обычными stock items, но QoL-эффект прекратится; stale shop execution будет отклонён до debit. PhantomPlayers.ini и Phantom schema для включения/отката не меняются.

## Проверка

Обязательная серверная composition:

```text
ant qol-level-gap-test
ant qol-shop-test
ant qol-affected-test
ant verify
ant -q jar
```

`qol-level-gap-test` покрывает grouped/ungrouped/spoil, boundaries, inventory/relog и bot exclusions. `qol-shop-test` использует реальные native debit/credit и отрицательные prepare/execute/flood/capacity controls. База должна быть заранее подготовленным allowlisted test schema по действующей Phantom test policy; `prepare-phantom-test-db` в этом workflow не запускается.

Клиентский визуальный статус релиза: **NOT_TESTED_CLIENT_UI**.

Следующие L2-QOL-002/003 остаются PLANNED: см. `docs/personal-qol/ROADMAP.md`.
