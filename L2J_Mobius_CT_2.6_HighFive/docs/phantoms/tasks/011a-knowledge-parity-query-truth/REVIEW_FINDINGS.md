# REVIEW FINDINGS — Goal 011

## P1 — drop runtime order lost

The adapter sorts groups and holders by content before assigning ordinals.
`NpcTemplate` consumes these lists in order with cumulative chance and occurrence
budgets, so the knowledge facts no longer describe runtime mechanics.

## P1 — empty requested filters widen target queries

A missing index entry becomes null, which is interpreted as no filter. Unknown
topology/map/item filters return unrelated mobs.

## P2 — parity is self-referential

Expected facts are produced by a second call to the same adapter.

## P2 — recipe ambiguity can be silently deduplicated

Duplicate recipe-item IDs can resolve twice to the same recipe and hide another
loaded list.

## P2 — nested area results bypass bounds

Area pages embed point lists and TargetFact embeds all areas.

## P2 — service diagnostics omit component hashes
