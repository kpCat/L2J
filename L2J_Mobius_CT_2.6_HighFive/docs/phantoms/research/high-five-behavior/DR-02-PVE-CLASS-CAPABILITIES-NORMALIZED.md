# DR-02 — PvE class capabilities

| Claim ID | Нормализованный факт | Authority | Confidence | Source paths |
|---|---|---|---|---|
| `DR02-PVE-CAP-001` | Capability catalog сохраняет single-target/area, melee/ranged/magic, summon, spoil, sweep и craft evidence как механические факты. | `SERVER_LOADER_FACT`, `CURATED_CAPABILITY` | `HIGH` для loader facts, `MEDIUM` для classification | `java/org/l2jmobius/gameserver/data/xml/SkillData.java`; `SkillTreeData.java`; `dist/game/data/phantoms/progression/high-five-capabilities-v1.xml` |
| `DR02-PVE-CAP-002` | Каждый executable вариант имеет точный action skill, target scope, equipment family и skill mechanics; несколько вариантов одной capability group не сливаются. | `SERVER_LOADER_FACT`, `CURATED_CAPABILITY` | `HIGH` | `java/org/l2jmobius/gameserver/model/skill/Skill.java`; `java/org/l2jmobius/gameserver/phantoms/progression/L2jProgressionBackend.java`; `high-five-capabilities-v1.xml` |
| `DR02-PVE-CAP-003` | `READY_NOW` учитывает authoritative `Skill` item/charge requirements, MP/HP, reuse и dynamic condition. `maximumSoulConsumeCount` хранится как верхняя граница, а не как выдуманный minimum. | `SERVER_CODE_FACT`, `SERVER_LOADER_FACT` | `HIGH` | `Skill.java`; `java/org/l2jmobius/gameserver/phantoms/progression/PhantomProgressionCapabilityEvaluator.java` |
| `DR02-PVE-CAP-004` | Capability fact не задаёт лучший класс, zone, route, TTK, loot preference или farming loop. | `BOUNDED_SCOPE_CONTRACT` | `HIGH` | `docs/phantoms/tasks/013a-progression-capability-extensibility-hardening/TASK.md`; `docs/phantoms/architecture/PROGRESSION_CAPABILITY_CONTRACT.md` |
| `DR02-PVE-CAP-005` | Zone selection и causal farming относятся к Goal 015; spoil/manor/quest/craft acquisition chains — к Goal 021. | `ROADMAP_CONTRACT` | `HIGH` | `docs/PHANTOM_BOTS_ROADMAP.md` |

Recommendations и disputed retail rankings отсутствуют.
