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
