# LOOT TRUTH — Goal 012A

A loot attempt records immutable evidence:

```text
world object ID
item ID
ground count
actor inventory count before
```

Observation states:

```text
PENDING
ACQUIRED_BY_ACTOR
LOST_WITHOUT_ACQUISITION
INELIGIBLE
```

Acquisition requires positive canonical evidence, such as the exact inventory
object becoming actor-owned or a matching inventory increase by the factual
ground count after world removal.

Candidate disappearance, another-player pickup, despawn, protection change,
instance/range/region loss never count as actor acquisition.
