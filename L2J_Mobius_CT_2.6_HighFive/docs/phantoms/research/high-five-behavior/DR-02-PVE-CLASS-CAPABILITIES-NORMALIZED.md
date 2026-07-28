# DR-02 — PvE class capabilities

Для Goal 013 сохранены только class-mechanical факты:

- single-target, area, melee, ranged и magic capability;
- summon, spoil, sweep и craft evidence;
- weapon family и расходуемые item requirements;
- точный target scope;
- активный навык, passive/toggle, damage/heal/debuff/control и reuse/resource mechanics.

Факт capability не задаёт «лучший класс», зону, маршрут, TTK, loot preference или farming loop. Champion/auto-loot остаются config/runtime facts и не превращаются в class ranking.

Zone selection и causal farming относятся к Goal 015. Spoil/manor/quest/craft acquisition chains относятся к Goal 021.

Источники: `SkillData`, `SkillTreeData`, `Skill`, `ItemData`, `high-five-capabilities-v1.xml`.

Authority: `SERVER_LOADER_FACT` и `CURATED_CAPABILITY`. Confidence: `HIGH` для loader facts, `MEDIUM` для curated classification.
