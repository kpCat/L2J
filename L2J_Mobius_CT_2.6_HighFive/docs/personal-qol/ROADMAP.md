# Personal/Premium QoL roadmap

Этот roadmap начинается после принятого Phantom World Goal039 и использует независимую нумерацию `L2-QOL`. Старый Phantom freeze не открывается заново.

## L2-QOL-001 — защита от level-gap и магазин

Статус: **SUCCESS**.

Добавлены четыре нерасходуемых inventory-tier 5/10/20/40, отдельные INI/XML, русская страница Alt+B и штатный multisell с демонстрационными ценами. Максимальный найденный tier снимает только DROP/SPOIL level-gap penalty в пределах включительной границы `abs(actual native level gap) <= N`. XP/SP, quest rates, Spoil cast success, reward actor, amount/chance/RNG и Phantom/Fake Players не изменены. Shipped feature и shop выключены.

## L2-QOL-002 — личные cross-class skills и crystallization

Статус: **SUCCESS**.

Добавлены отдельный shipped-OFF/empty account+character allowlist, personal-only доступ real main-class Player к обычным foreign `CLASS`-деревьям реального trainer owner и native alternative SP 1x/2x/3x без включения глобального `AltGameSkillLearn`. Уровни, предыдущие навыки, required items, persistence и stock skill data остаются штатными. Non-dwarf должен реально изучить `CRYSTALLIZE`; native packet и Alt+B используют один mutation owner с точным `Item.getCrystalCount()`, preview, одноразовым token до 120 секунд и at-most-once защитой. GM/hero/clan/subclass/transform/FS/auto trees не расширены.

## L2-QOL-003 — длительность buff/dance/song

Статус: **PLANNED**. Это отдельная будущая задача, сейчас она не запущена.

Настраиваемая длительность относится только к положительным buff/dance/song. Debuffs исключены. Нужны per-recipient semantics без протекания в общие skill templates и без повторного умножения при relog/restore/refresh.

## Backlog

- предметы vitality;
- временные и постоянные rate items;
- дополнительные premium-бонусы и storefronts;
- собственные client names/icons для QoL-предметов.

Backlog сохранён только как список идей: он не реализован и не включён автоматически.
