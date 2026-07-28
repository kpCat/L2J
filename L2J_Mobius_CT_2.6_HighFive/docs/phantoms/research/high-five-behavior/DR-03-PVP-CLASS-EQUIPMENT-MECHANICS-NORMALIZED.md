# DR-03 — PvP class и equipment mechanics

| Claim ID | Нормализованный факт | Authority | Confidence | Source paths |
|---|---|---|---|---|
| `DR03-PVP-MECH-001` | Catalog сохраняет negative/control, PvP-only, suicide/special restrictions, target scope, weapon/equipment condition, item/charge/soul mechanics и Olympiad block как factual inputs. | `SERVER_CODE_FACT`, `SERVER_LOADER_FACT` | `HIGH` | `java/org/l2jmobius/gameserver/model/skill/Skill.java`; `java/org/l2jmobius/gameserver/model/item/ItemTemplate.java` |
| `DR03-PVP-MECH-002` | Canonical `Player` имеет отдельные HP, MP и CP; combat snapshot копирует exact current/max CP отдельно. Controlled actors не получают fabricated Player CP. | `CURRENT_SERVER_IMPLEMENTATION` | `HIGH` | `java/org/l2jmobius/gameserver/model/actor/Player.java`; `java/org/l2jmobius/gameserver/phantoms/combat/L2jCombatBackend.java`; `java/org/l2jmobius/gameserver/phantoms/progression/PhantomProgressionModel.java` |
| `DR03-PVP-MECH-003` | Current Mobius удаляет pet при входе в Olympiad, но не удаляет servitor тем же путём; summon damage attribution относится к owner. | `CURRENT_SERVER_IMPLEMENTATION` | `HIGH` | `java/org/l2jmobius/gameserver/model/olympiad/OlympiadGameNormal.java`; `java/org/l2jmobius/gameserver/model/actor/Summon.java` |
| `DR03-PVP-MECH-004` | Catalog не определяет target priority, engagement/chase/PK thresholds, equipment matchup, CP potion use или Olympiad strategy. Эти решения относятся к Goal 025; memory/reputation — к Goal 018. | `ROADMAP_CONTRACT` | `HIGH` | `docs/PHANTOM_BOTS_ROADMAP.md`; `docs/phantoms/tasks/013a-progression-capability-extensibility-hardening/CP_CONTEXT_ADDENDUM.md` |

Конкретные CP potion vendors, buylist/multisell, currency и cost здесь не утверждаются.
