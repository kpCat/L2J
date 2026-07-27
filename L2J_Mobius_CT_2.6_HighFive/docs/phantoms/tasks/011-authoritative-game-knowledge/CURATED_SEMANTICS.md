# CURATED SEMANTICS — Goal 011

## Class capabilities

Required keys:

```text
combat.tank
combat.heal
combat.resurrection
combat.buff
combat.debuff
combat.crowd_control
combat.melee_damage
combat.ranged_physical_damage
combat.ranged_magic_damage
combat.summon
profession.spoil
profession.craft
```

Every terminal playable class has at least one combat/profession capability.
Each capability cites evidence skill IDs present in the complete class skill
tree, except a documented intrinsic such as summoner.

Do not infer a role from enum/class names.

## Content requirements

Commit evidence-backed recommendations for at least:

- one Dimensional Rift tier;
- one real RaidBoss;
- one real GrandBoss/epic.

Requirements specify capability key, minimum count/rank and required versus
recommended status. They are explicitly `CURATED_RECOMMENDATION`; Goal 011 does
not solve party composition or enforce entry rules.
