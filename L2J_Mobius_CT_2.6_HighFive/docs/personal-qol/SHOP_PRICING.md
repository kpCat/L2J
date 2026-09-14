# Personal QoL storefront — цены и владелец данных

Дата аудита: 2026-09-14

## Контракт витрины

`dist/game/data/multisell/91001.xml` — единственный владелец цен четырёх пропусков разницы уровней. Валюта для всех предложений — Adena, item ID `57`. Названия и границы тиров принадлежат `dist/game/data/custom/personal-qol/level-gap-items.xml`. Java не содержит таблицу цен: `PersonalPremiumQoLService` валидирует оба XML и передаёт те же значения Alt+B и native multisell.

`91001` предназначен только для четырёх существующих level-gap pass. Расходники, экипировка и ресурсы в этот список не добавляются.

| Категория | Предложение | Результат | Валюта | Цена | Авторитетный источник | Почему эта цена |
|---|---|---|---|---:|---|---|
| Дроп и спойл | Защита до ±5 уровней | item `22290`, 1 шт. | `57`, Adena | 100 000 | `dist/game/data/multisell/91001.xml` | Нижняя граница постоянной нерасходуемой привилегии; точный Adena service anchor `100000` уже есть в native `multisell/622.xml`. |
| Дроп и спойл | Защита до ±10 уровней | item `22296`, 1 шт. | `57`, Adena | 500 000 | `dist/game/data/multisell/91001.xml` | Следующая ступень между существующими native anchors `400000`/`440000` и `960000`; не цена обычного расходника. |
| Дроп и спойл | Защита до ±20 уровней | item `22297`, 1 шт. | `57`, Adena | 2 000 000 | `dist/game/data/multisell/91001.xml` | Точный повторяющийся service anchor `2000000` из native `multisell/622.xml`; широкий постоянный диапазон. |
| Дроп и спойл | Защита до ±40 уровней | item `22298`, 1 шт. | `57`, Adena | 8 000 000 | `dist/game/data/multisell/91001.xml` | Точный верхний service anchor `8000000` из native `multisell/622.xml`; максимальная постоянная граница. |

До L2-QOL-004 значения `1 000 / 5 000 / 20 000 / 50 000` считались `PRE_QOL004_PROVISIONAL` и заменены. Финальная лестница учитывает `StartingLevel=1`, `StartingAdena=0` из `dist/game/config/Player.ini`, базовый `RateXp=1` из `dist/game/config/Rates.ini`, цены обычных расходников и диапазон штатных Adena services в `dist/game/data/multisell/622.xml`. Она не объявляется универсальным балансом для любого сервера: оператор может изменить её осознанно.

## Как изменить

1. В `dist/game/data/multisell/91001.xml` измените только `count` у `<ingredient id="57">` нужного предложения.
2. Не меняйте product ID и не добавляйте строки: строгий catalog guard требует точное соответствие четырём item ID из level-gap catalog.
3. Запустите `ant qol-004-storefront-controls-test`: UI и native transaction должны прочитать одинаковые значения.
4. Перезапустите Game Server. Одного `//reload multisell` недостаточно, потому что валидированная metadata витрины кэшируется `PersonalPremiumQoLService` при старте.

Смена валюты не является простой операторской правкой: текущий validator намеренно принимает только Adena `57`. Другая валюта требует отдельной задачи с economy audit, кодовым изменением и тестами.

## Аудит расходников

- Mana Potion `728` имеет серверный template/ItemSkills и reference price `2000`, но repository-local Adena retail owner для новой личной витрины не найден.
- Revita-Pop `20034`, Vitality potions `20391/20392` и XP rune `21084` имеют client-visible templates/actions, но являются premium и/или non-trade/non-sellable; безопасный Adena price anchor отсутствует.

Поэтому mana/vitality/rate-продажи в L2-QOL-004 явно отложены. Новый multisell ID не выделялся, collision scan не требовался, client data и цены не выдумывались.

## L2-QOL-008 — curated progression

`dist/game/data/multisell/91002.xml` — отдельный и единственный владелец цен progression-витрины. Collision census подтвердил, что до QOL-008 list ID `91002` не использовался. Java содержит только строгий allowlist ожидаемых product ID/count и читает цены из XML; Alt+B preview и native transaction используют один runtime snapshot. Валюта всех предложений — Adena `57`.

| Переход clan level | Предложение | Результат | Цена | Canonical consumption owner | Ценовой anchor и rationale |
|---|---|---:|---:|---|---|
| 2 -> 3 | Blood Mark | `1419 x1` | 5 000 000 | `Clan.levelUpClan(Player)` | Точный native Adena anchor `5 000 000` присутствует в `multisell/644.xml`; это первый item-gated переход и цена уже выше QOL-004 convenience ladder. |
| 3 -> 4 | Alliance Manifesto | `3874 x1` | 15 000 000 | `Clan.levelUpClan(Player)` | Точный native Adena anchor `15 000 000` присутствует в `multisell/644.xml`; следующая ступень сохраняет рост вместе с требованием `1 000 000 SP`. |
| 4 -> 5 | Seal of Aspiration | `3870 x1` | 30 000 000 | `Clan.levelUpClan(Player)` | Точный native Adena anchor `30 000 000` присутствует в `multisell/323470001.xml`; последний ранний item-gated переход также требует `2 500 000 SP`. |
| 8 -> 9 | Blood Oath | `9910 x150` | 75 000 000 | `Clan.levelUpClan(Player)` | Serious late-clan midpoint между повторяющимися native anchors `50 000 000` и `100 000 000` в `multisell/631.xml`; сохраняются `40 000 CRP` и 120 members. |
| 9 -> 10 | Blood Alliance | `9911 x5` | 100 000 000 | `Clan.levelUpClan(Player)` | Точный верхний native Adena anchor `100 000 000` из `multisell/631.xml`; остаются `40 000 CRP` и 140 members. |

Покупка не повышает clan level. `Clan.levelUpClan` остаётся единственным mutation/consumption owner и повторно проверяет текущий level, exact item count, SP/CRP, member count и territory requirements. Поэтому цены дают bounded путь к реальному inventory bottleneck, но не обходят прочие условия.

В `91002` намеренно не включены hidden QuestState markers, промежуточные quest tokens, Noblesse items, raid jewelry, обычное equipment, enchant-scroll dump, GM/hero items и arbitrary rare loot. Ancient Adena также не продаётся: её штатное получение через seal stones, Black Marketeer и quests доказано достаточным. Это curated progression surface, а не generic GM shop.

Цены QOL-004 в `91001.xml` остаются без изменений: `100 000 / 500 000 / 2 000 000 / 8 000 000 Adena`.

### Как изменить progression-цену

1. В `dist/game/data/multisell/91002.xml` измените только `count` у `<ingredient id="57">` нужного предложения.
2. Не меняйте product ID/count и не добавляйте строки: strict catalog guard требует точное соответствие пяти audited clan prerequisites.
3. Запустите `ant qol-economy-progression-closure-test` и `ant qol-004-storefront-controls-test`.
4. Перезапустите Game Server; одного `//reload multisell` недостаточно для обновления кэшированного preview metadata.
