# DR-01 — противоречия и live gates

- В текущем High Five 103 `PlayerClass`, а не внешнее фиксированное число из непроверенных сводок.
- Терминальность вычисляется из реального enum graph, а не из display name.
- Наличие skill в class tree — `INTRINSIC`; наличие у `Player` — `LEARNED`; текущие target, equipment, resource, condition, MP/HP, reuse и actor state образуют `READY_NOW`.
- COMMON tree в текущем loader может быть пустым; это валидный parity-факт, а не повод изобретать entries.
- Subclass, certification, Noble и Hero — отдельное состояние canonical `Player`, не часть profession-chain inference.
- Profession quest и quest items не подделываются. Уровень может быть готов, но authorization остаётся `CANONICAL_QUEST_REQUIRED`.

Live gates берутся из `Player`, `Skill.checkCondition`, `Player.isSkillDisabled`, inventory и текущего trainer interaction.

Authority: `CURRENT_SERVER_IMPLEMENTATION`. Confidence: `HIGH`. Источники: `Player.java`, `Skill.java`, `RequestAcquireSkill.java`.
