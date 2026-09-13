# Personal/Premium QoL — инструкция оператора

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

## Личный allowlist QOL-002

Конфигурация: `dist/game/config/Custom/PersonalCharacterQoL.ini`. Она независима от QOL-001 и поставляется полностью выключенной:

```ini
EnablePersonalCharacterQoL=False
EnablePersonalCrossClassSkills=False
EnablePersonalCrystallization=False
AllowedCharacterIds=
AllowedAccounts=
```

Для одного или нескольких персонажей включите master switch и нужные subfeature, затем заполните хотя бы один allowlist. `AllowedCharacterIds` принимает положительные decimal object ID персонажей, `AllowedAccounts` — имена аккаунтов без пробелов; регистр аккаунта не учитывается. Разделители — запятая или точка с запятой, максимум 256 значений и 4096 символов на список. Пример с синтетическими значениями:

```ini
EnablePersonalCharacterQoL=True
EnablePersonalCrossClassSkills=True
EnablePersonalCrystallization=True
AllowedCharacterIds=100001;100002
AllowedAccounts=test_account
```

После изменения нужен перезапуск Game Server. Ошибка формата отключает только QOL-002; QOL-001 продолжает использовать свой отдельный конфиг. Пустые allowlist не разрешают доступ никому. Headless Phantom не допускается даже при совпадении ID/account.

## Cross-class обучение

Глобальный `AltGameSkillLearn` оставляйте в прежнем состоянии; для personal path включать его не требуется. Allowlisted Player на основной профессии использует обычный `SkillList` у NPC-наставника. Выбранная профессия должна реально входить в production teach set этого NPC, а её hierarchy level не может быть выше активной профессии игрока.

Доступны только штатные `CLASS`-навыки, изучаемые у NPC: forgotten-scroll и auto-get не добавляются, GM/hero/clan/subclass/transform trees не расширяются. Сервер повторно проверяет trainer, выбранный class, level, previous level, prerequisites, required items и SP непосредственно перед mutation. Для чужой профессии действует native alternative цена: одинаковый fighter/mage тип — 2x, противоположный — 3x; свой class — 1x. Изученные навыки сохраняются обычным `character_skills` path и восстанавливаются после relog, пока персонаж остаётся allowlisted и функция включена.

## Кристаллизация

Race bypass отсутствует: персонаж любой расы обязан реально знать `CRYSTALLIZE` нужного уровня. Native client packet продолжает работать; если H5 client не показывает действие non-dwarf, доступен server-side fallback на личной странице Alt+B.

Alt+B выводит только ограниченный список подходящих предметов. После выбора показываются exact item/count/enchant и ожидаемый `Item.getCrystalCount()` result. Подтверждение необратимо, одноразово, действует не более 120 секунд и заменяется при подготовке другого предмета. При replay, смене владельца, count/enchant/item drift, отзыве allowlist или гонке с native packet операция закрывается без повторного credit.

Сохраняются native ограничения: достаточный уровень навыка для D/C/B/A/S grade, ownership/manipulation, store/in-flight state, hero/shadow/time-limited/augmentation и `isCrystallizable`. Экипированный предмет сначала снимается. Кристаллы начисляются только после точного destruction; `inCrystallize` очищается в `finally`.

## Магазин и цены

Alt+B вызывает отдельный guarded route, который подготавливает native multisell `91001`. Generic multisell bypass для этого list ID запрещён. При execute повторно проверяются dedicated provenance и live `EnablePersonalPremiumShop`; поэтому prepared до выключения список после disable отклоняется до списания.

Оплата, ownership, capacity, inventory slots/weight и flood protection остаются штатными в `MultiSellChoose`/multisell engine.

Цены в `91001.xml` демонстрационные. Для замены цены измените `count` у соответствующего `<ingredient id="57">`. Для другой валюты измените `id` и `count`, убедившись, что template существует и экономика согласована. Новый товар добавляется штатным `<item>` с ingredient/production, но текущий QoL guard намеренно принимает только четыре ожидаемых tier-carrier; расширение allowlist требует отдельного кодового изменения и теста, а не только XML-правки.

## Откат

Для QOL-001 установите оба switch в `PersonalPremiumQoL.ini` в `False`. Для QOL-002 установите `EnablePersonalCharacterQoL=False` или выключите отдельные subfeature в `PersonalCharacterQoL.ini`, затем перезапустите Game Server. DB migration отсутствует. Купленные предметы останутся обычными stock items, но level-gap эффект прекратится; stale shop/crystallization execution будет отклонён до debit/credit. Изученные foreign skills хранятся штатно; при выключенном admission штатный skill checker может удалить их как недопустимые при следующем restore. `PhantomPlayers.ini` и Phantom schema не меняются.

## Проверка

Обязательная серверная composition:

```text
ant qol-level-gap-test
ant qol-shop-test
ant qol-personal-skills-test
ant qol-crystallization-test
ant qol-002-affected-test
ant qol-002-verify
ant verify
ant -q jar
```

`qol-level-gap-test` покрывает grouped/ungrouped/spoil, boundaries, inventory/relog и bot exclusions. `qol-shop-test` использует реальные native debit/credit и отрицательные prepare/execute/flood/capacity controls. Новые focused targets покрывают personal policy, настоящий `RequestAcquireSkill`, relog и required items, а также native/common/Alt+B crystallization, replay/stale/expiry/concurrency. База должна быть заранее подготовленным allowlisted test schema по действующей Phantom test policy; `prepare-phantom-test-db` в этом workflow не запускается.

Клиентский визуальный статус релиза: **NOT_TESTED_CLIENT_UI**.

L2-QOL-003 остаётся PLANNED: см. `docs/personal-qol/ROADMAP.md`. Vitality/rate items остаются только в backlog.
