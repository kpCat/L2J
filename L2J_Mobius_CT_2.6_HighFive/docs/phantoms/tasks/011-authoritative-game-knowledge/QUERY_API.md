# QUERY API — Goal 011

All query results are immutable and paged with a stable fact-key cursor.
Maximum page size: 256.

Required query families:

```text
find item / NPC
item drop sources
item spoil sources
item manor relations
NPC spawn facts and topology areas
recipe by list ID
recipes producing an item
recipes using an ingredient
class capabilities
classes satisfying a capability/rank
content requirements by ID/capability
bounded suitable-target filtering
```

Suitable-target filtering supports level range, preferred level, topology node,
map-region, NPC kind, attackable/targetable/sowable and drop/spoil-item filters.
It is factual filtering, not Utility AI scoring.

Ordinary queries may use direct lookup, bounded page slicing and bounded merges
over indexed level buckets. They may not scan all loaders/facts, open files,
query DB, resolve names or call mutable server managers.
