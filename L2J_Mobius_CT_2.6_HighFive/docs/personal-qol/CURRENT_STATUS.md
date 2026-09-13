# Personal/Premium QoL — current status

Дата: 2026-09-13

Ветка: `feature/phantom-world`

Базовый commit: `4e338a83a2db9bc99a828c2f19b6e05722d3339c`

Текущая задача: `L2-QOL-001`

Статус: **SUCCESS**

## Поставленное поведение

- `EnablePersonalPremiumQoL=False` и `EnablePersonalPremiumShop=False` в shipped INI.
- Каталог содержит ровно четыре tier: 5, 10, 20 и 40.
- Используется только максимальный tier из основного инвентаря; предметы не суммируются и не расходуются.
- В пределах `abs(actual native level gap) <= tier` нейтрализуется только штатный level-gap penalty для DROP и SPOIL.
- За пределами tier полностью сохраняется stock formula.
- XP/SP, quest rates, Spoil cast success, amount/chance/RNG, native reward actor и raid curse не изменены.
- Phantom Players во всех headless состояниях и legacy NPC Fake Players бонус не получают.
- QoL multisell `91001` доступен только через dedicated Alt+B provenance и повторно проверяет live shop switch на prepare и execute.

## Артефакты

- INI: `dist/game/config/Custom/PersonalPremiumQoL.ini`
- tier XML: `dist/game/data/custom/personal-qol/level-gap-items.xml`
- native multisell: `dist/game/data/multisell/91001.xml`
- Alt+B HTML: `dist/game/data/html/CommunityBoard/Custom/personal-qol/main.html`
- операторская инструкция: `docs/personal-qol/OPERATOR_GUIDE_RU.md`
- evidence report: `docs/personal-qol/reports/001-level-gap-and-shop.md`

Серверные focused и affected проверки выполнены на отдельном чистом sparse HighFive candidate. Финальный `ant verify` и standalone jar зафиксированы в evidence report.

Клиентский UI: **NOT_TESTED_CLIENT_UI**. Серверная страница и bypass проверены, но визуальная проверка в H5-клиенте не выдаётся за выполненную. Client patch не требуется: в инвентаре остаются исходные stock names/icons.

L2-QOL-002 и L2-QOL-003 только опубликованы в roadmap и не запущены. Phantom freeze и алгоритмы Phantom не переписывались.
