# SKILL AND EQUIPMENT OPERATIONS — Goal 013

## Skill learning

Only AcquireSkillType.CLASS is executable.

The operation mirrors the authoritative RequestAcquireSkill requirements without
constructing a packet:

- exact real trainer;
- exact SkillLearn;
- previous level;
- minimum level;
- calculated SP;
- prerequisites;
- required items;
- exact persistent addSkill;
- OnPlayerSkillLearn event.

No batch auto-learn and no free skills.

## Equipment

Evaluate only actor-owned items. Equip only by exact object ID through the
existing canonical Player equipment method.

No paperdoll insertion, item creation, purchase, enchant or packet simulation.

## Profession

No production setPlayerClass. Structurally valid next class plus level without a
reusable canonical quest-authorized facade returns CANONICAL_QUEST_REQUIRED.
Canonical changes made elsewhere are observed and reconciled.
