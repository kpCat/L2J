# DR-04 — party-role capability matrix

Goal 013A различает factual capability и target scope, не назначая тактические party roles.

| Claim ID | Capability | Проверяемое различие | Authority | Confidence | Source paths |
|---|---|---|---|---|---|
| `DR04-PARTY-CAP-001` | heal / resurrection | HP restoration и возврат из смерти | `SERVER_LOADER_FACT` | `HIGH` | `java/org/l2jmobius/gameserver/model/skill/Skill.java`; class skill trees |
| `DR04-PARTY-CAP-002` | recharge | MP restoration отдельно от heal | `SERVER_LOADER_FACT` | `HIGH` | `Skill.java`; class skill trees |
| `DR04-PARTY-CAP-003` | buff | positive effect evidence | `SERVER_LOADER_FACT`, `CURATED_CAPABILITY` | `HIGH` | `Skill.java`; `dist/game/data/phantoms/progression/high-five-capabilities-v1.xml` |
| `DR04-PARTY-CAP-004` | song / dance | отдельные exact evidence skills | `SERVER_LOADER_FACT`, `CURATED_CAPABILITY` | `HIGH` | `Skill.java`; `high-five-capabilities-v1.xml` |
| `DR04-PARTY-CAP-005` | tank / control | aggro и control mechanics | `SERVER_LOADER_FACT`, `CURATED_CAPABILITY` | `MEDIUM` для mapping | `Skill.java`; `high-five-capabilities-v1.xml` |
| `DR04-PARTY-CAP-006` | damage | single-target или area scope | `SERVER_LOADER_FACT` | `HIGH` | `Skill.java`; class skill trees |
| `DR04-PARTY-CAP-007` | summon | отдельный servitor/pet/cubic actor variant | `SERVER_CODE_FACT`, `DATAPACK_FACT` | `HIGH` | `java/org/l2jmobius/gameserver/model/actor/Summon.java`; summon skill XML |
| `DR04-PARTY-CAP-008` | spoil / sweep / craft | отдельные mechanical capabilities | `SERVER_LOADER_FACT`, `CURATED_CAPABILITY` | `HIGH` | `Skill.java`; `high-five-capabilities-v1.xml` |
| `DR04-PARTY-CAP-009` | future party policy | lifecycle, vacancies, leader coordination и shared routes не являются catalog facts | `ROADMAP_CONTRACT` | `HIGH` | `docs/PHANTOM_BOTS_ROADMAP.md` |

Party lifecycle относится к Goal 017, Rift — к Goal 023, raid/epic — к Goal 026.
