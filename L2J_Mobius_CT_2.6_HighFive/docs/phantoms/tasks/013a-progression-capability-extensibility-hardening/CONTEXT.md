# Context — Goal 013A

## Lineage

- Last independently accepted baseline before Goal 013:
  `8dba87e9c1d5828376b80c1ea16c4578726d4947`
- Goal 013 commit under correction:
  `ca50ea28f233e41343035977c55c98129e5d113a`
- Required branch:
  `feature/phantom-world`

Goal 013 correctly established:

- complete `PlayerClass` identity graph;
- distinct enum parent and skill-tree parent;
- immutable class/skill/equipment/summon/pet facts;
- canonical active `Player` snapshots;
- read-only profession boundary;
- exact basic CLASS learning and owned-item equip paths;
- no tactical doctrine in the factual catalog;
- no progression worker/thread/Future;
- correct shutdown ordering and disabled inertness.

Goal 013A must preserve these strengths.

## Why Goal 014 is blocked

Goal 014 will depend on progression/equipment facts for NPC commerce, supplies,
travel and sell decisions. It must not inherit:

- a universal equipment ranking that hides situational owned items;
- a `READY_NOW` value that ignores skill consumables;
- a capability model that cannot represent two alternatives of the same kind;
- an insufficient summon actor seam;
- a production catalog hash proven only with inert Game Knowledge.

The correction is bounded and must complete before Goal 014.

## Architectural intent

Goal 013A does not make classes tactically intelligent. It ensures future
doctrines can be added without changing the central factual model.

Examples that must remain possible after the correction:

- Destroyer burst/HP-preparation and Prophet root/Mana Burn can coexist as
  multiple factual variants; future doctrine decides when to use them.
- Sword Singer and Blade Dancer expose exact song/dance variants and resource
  facts; future party doctrine selects a set.
- Arcana Lord can have several distinct summon variants; future coordinated
  controller chooses and commands one.
- A lower-grade weapon required by a skill remains queryable even if a higher
  grade weapon exists.
