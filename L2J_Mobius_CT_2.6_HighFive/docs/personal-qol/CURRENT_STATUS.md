# Personal/Premium QoL — current status

Дата: 2026-09-13

Ветка: `feature/phantom-world`

Required parent: `78f41e441cef6b70e65a087c4e463df4779e7844`

Текущая задача: `L2-QOL-002`

Статус: **SUCCESS**

## L2-QOL-002

- Новый `PersonalCharacterQoL.ini` поставляется с master/subfeature switch в `False` и пустыми allowlist character ID/account.
- Только allowlisted real main-class Player может открыть у реального владельца-тренера обычное `CLASS`-дерево другой профессии того же или более низкого hierarchy level. Headless Phantom и active subclass исключены.
- Глобальный `AltGameSkillLearn` не включён. Stock `SkillList` / `Folk` / `SkillTreeData` / `RequestAcquireSkill`, требования уровня/предыдущего навыка/предметов и сохранение в БД остаются владельцами процесса.
- Personal foreign skill сохраняет native alternative SP: own 1x, same fighter/mage type 2x, opposite type 3x; цена списка совпадает со списанием.
- Реально изученный `CRYSTALLIZE` разрешает non-dwarf штатную кристаллизацию без race bypass.
- Native packet и Alt+B используют один mutation service с исходными ограничениями и точным `Item.getCrystalCount()`.
- Alt+B показывает bounded inventory page и необратимый preview; подтверждение имеет непредсказуемый одноразовый token не дольше 120 секунд, один на Player, с повторной проверкой item/count/enchant/result и at-most-once защитой от native/board гонки.
- Ошибка нового конфига fail-closed отключает только QOL-002 и не повреждает валидное состояние QOL-001.

## Сохранённое поведение L2-QOL-001

- Четыре нерасходуемых level-gap tier 5/10/20/40 и guarded multisell `91001` работают без изменения semantics.
- XP/SP, quest rates, Spoil cast success, amount/chance/RNG, native reward actor, raid curse и Phantom/Fake Player exclusions не изменены.

## Артефакты

- INI: `dist/game/config/Custom/PersonalPremiumQoL.ini`
- tier XML: `dist/game/data/custom/personal-qol/level-gap-items.xml`
- native multisell: `dist/game/data/multisell/91001.xml`
- Alt+B HTML: `dist/game/data/html/CommunityBoard/Custom/personal-qol/main.html`
- personal access INI: `dist/game/config/Custom/PersonalCharacterQoL.ini`
- Alt+B crystallization HTML: `dist/game/data/html/CommunityBoard/Custom/personal-qol/crystallization*.html`
- операторская инструкция: `docs/personal-qol/OPERATOR_GUIDE_RU.md`
- evidence reports: `docs/personal-qol/reports/001-level-gap-and-shop.md`, `docs/personal-qol/reports/002-cross-class-skills-crystallization.md`

Focused, affected, historical static, Goal039 structure/static/docs, финальный fresh `ant verify` и standalone jar зафиксированы в evidence report. Использовалась только allowlisted test DB; production DB не читалась и не проверялась.

Клиентский UI: **NOT_TESTED_CLIENT_UI**. Серверная страница и bypass проверены, но визуальная проверка в H5-клиенте не выдаётся за выполненную. Client patch не требуется: в инвентаре остаются исходные stock names/icons.

L2-QOL-003 остаётся **PLANNED**. Vitality/rate items сохранены в backlog. Phantom freeze и алгоритмы Phantom не переписывались; Goal040 не создавался.
