# SERVER FACT VALIDATION — Goal 010

Validate references through existing loaders only:

```text
MapRegionData.getMapRegionLocId
NpcData.getTemplate
SpawnTable.getSpawns
DoorData.getDoor
World coordinate bounds
```

NPC semantic role is curated and must include source evidence. It is not inferred
from name/title.

NPC anchors require an existing template and a spawn within declared tolerance.
Door edges require an existing door and bounded door-side anchors. Map-region
claims must match the current server lookup.
