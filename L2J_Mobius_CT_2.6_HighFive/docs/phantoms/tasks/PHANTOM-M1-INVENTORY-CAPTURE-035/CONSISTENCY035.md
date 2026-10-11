# Inventory boundary consistency

| Boundary | usedSlots meaning | Full evidence / permission |
|---|---|---|
| Native authority capture | stock nonquest count, including equipped objects | bounded representation; no acquisition |
| Owned service immutable witness | all INVENTORY rows | every INVENTORY/PAPERDOLL object contributes to canonical hash |
| SQL transaction v2 projection | all INVENTORY rows | unchanged legacy encoding and exact native witness equality |
| Background model | durable INVENTORY count plus tracked PAPERDOLL count | refuses pickup above stock250; untracked INVENTORY rows remain counted |
| Codec v2 | unchanged scalar and object encoding | compact275, original4096/8192 bounds; no schema migration |

The transient native capture is normalized through the existing immutable owned witness before PREPARED. Background execution consumes SQL-backed durable projections, not this transient capture. All equipped objects are retained by existing native tracking; unknown inventory objects remain in the scalar and full canonical hash.

At the actual TASK035 stock configuration `AutoLootSlotLimit=True`, existing stack increases are allowed at250 and rejected above250, exactly like native capacity. Quest items are excluded from native nonquest capture and retained in the full canonical hash. Historical durable INVENTORY scalars include quest rows; their existing conservative background accounting is retained. This slice does not introduce generic quest-capacity policy or change configuration. Focused C05 verifies the actual native separation, not a new background quest policy.

OLD RED: both actual inherited36/45 failed on original Jar at `Invalid bounded background inventory`, with252 objects and cap250. R1: both full native captures and six focused capacity/codec scenarios passed. These are not yet a native FINALIZED/runtime acceptance claim.

Only three production conditional paths changed: authority capture, bounded InventoryFacts representation, and background capacity check. Service, transaction, codec, lifecycle guards, Player/config/client remain unchanged.
