# TEST CASES — Goal 011

## Core

- immutable validated facts and policy bounds;
- canonical hashes independent of input order;
- complete drop/spoil, recipe and manor reverse indexes;
- exact raw chance encoding;
- deterministic pages/cursors;
- bounded target lookup;
- guarded no-scan query paths;
- atomic build failure and inert lifecycle.

## Parity

Exhaustively compare real loaded High Five items, NPC grouped/ungrouped drops,
spoil, spawns and recipes to the knowledge snapshot. Parse static `Seeds.xml`
and prove no `CastleManorManager`, DB access or runtime task is used. Build twice
and require identical component/combined hashes.

## Curated content

Validate strict schema, class/skill evidence, terminal class capability coverage,
required capability coverage, satisfiable content requirements and factual
Rift/RaidBoss/GrandBoss IDs/source paths.

## Performance

After one real-data build, run at least 100000 lookups in each major query family.
Queries perform no loader/file/DB access and return pages <=256. Run twice with
byte-identical canonical summary.
