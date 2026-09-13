# Personal/Premium QoL roadmap

Этот roadmap начинается после принятого Phantom World Goal039 и использует независимую нумерацию `L2-QOL`. Старый Phantom freeze не открывается заново.

## L2-QOL-001 — защита от level-gap и магазин

Статус: **SUCCESS**.

Добавлены четыре нерасходуемых inventory-tier 5/10/20/40, отдельные INI/XML, русская страница Alt+B и штатный multisell с демонстрационными ценами. Максимальный найденный tier снимает только DROP/SPOIL level-gap penalty в пределах включительной границы `abs(actual native level gap) <= N`. XP/SP, quest rates, Spoil cast success, reward actor, amount/chance/RNG и Phantom/Fake Players не изменены. Shipped feature и shop выключены.

## L2-QOL-002 — личные cross-class skills и crystallization

Статус: **SUCCESS**.

Добавлены отдельный shipped-OFF/empty account+character allowlist, personal-only доступ real main-class Player к обычным foreign `CLASS`-деревьям реального trainer owner и native alternative SP 1x/2x/3x без включения глобального `AltGameSkillLearn`. Уровни, предыдущие навыки, required items, persistence и stock skill data остаются штатными. Non-dwarf должен реально изучить `CRYSTALLIZE`; native packet и Alt+B используют один mutation owner с точным `Item.getCrystalCount()`, preview, одноразовым token до 120 секунд и at-most-once защитой. GM/hero/clan/subclass/transform/FS/auto trees не расширены.

## L2-QOL-003 — длительность buff/dance/song

Статус: **SUCCESS**.

Добавлены отдельные shipped-OFF множители положительных buff/dance/song и bounded override по skill ID. Stock duration сначала полностью вычисляется в `Formulas.calcEffectAbnormalTime`, после чего personal policy применяется один раз к конкретному allowlisted real Player в `BuffInfo`; общие skill templates не меняются. Passive/toggle/triggered/abnormal-instant/debuff/negative, explicit abnormal time, неизвестное или конфликтное music ownership, summon/pet/NPC и headless Phantom остаются stock. Restore/relog/recast не создают повторного умножения.

## L2-QOL-003-HF1 — гонка reload music classifier

Статус: **SUCCESS**.

Refresh, invalidate и публикация immutable music snapshot сериализованы одним монитором, поэтому завершившаяся invalidation побеждает ранее начатую сборку. `classify()` и `conflictCount()` используют по одному локальному snapshot, а прогретый путь остаётся без повторного обхода skill trees. Исправление защищено controlled concurrency regressions с конечными deadlines и не меняет множители, exclusions, allowlist, `Formulas`, `Skill` или `BuffInfo`.

L2-QOL-001/002/003 и hotfix L2-QOL-003-HF1 завершены: базовый Personal QoL закрыт со статусом **SUCCESS**.

## Backlog

- предметы vitality;
- временные и постоянные rate items;
- дополнительные premium-бонусы и storefronts;
- собственные client names/icons для QoL-предметов.

Backlog сохранён только как список идей: он не реализован и не включён автоматически.
