# DESIGN035 — conservation before release

## Problem
An inherited Phantom can enter actual owned cleanup with legitimate durable item records, but native `captureOwnedNative` currently makes `InventoryFacts` using `player.getInventory().getSize()` (all tracked item objects). For 36/45, 249 INVENTORY + 3 PAPERDOLL, nonQuest252, maximumSlots250. `InventoryFacts` compact constructor allows up to275 tracked objects but rejects `usedSlots > maximumSlots` when relying on compact capacity. Result: `Invalid bounded background inventory` before PREPARED, native work sealed and the character must remain retained. Deleting World/lease here is unsafe.

## Key design rule
Keep two different ideas separate: (a) **representation** of an already existing real inventory, possibly above its current capacity; (b) **permission to acquire an additional item slot**. Representation must preserve every canonical item and full HASH/IDs/counts/locations, without minting objects. Acquisition must remain at least as strict as stock `PlayerInventory.validateCapacity` and the existing background-object/weight/canonical rules. Existing over-capacity is not permission to grow further. There may be quest items: distinguish non-quest stock slot semantics from `getSize` and from paperdoll tracked subset.

Do not assume that treating PAPERDOLL as free slots is safe. Stock non-quest capacity includes EQUIPPED PAPERDOLL items. Do not solve by clipping usedSlots to 250, setting maximumSlots to 1000, fake unequip, silent truncation, cleanup on failed store, or backend-only SQL modifications. Do not silently relax the existing `MAX_TRACKED_ITEMS=160`, compact=275, or packet size limits. Any overfull representation acceptance must be narrowly justified, bounded and paired with acquisition rejection tests. Healthy preexisting payloads must retain byte parity whenever possible.

## Work boundaries
Capture: `L2jPhantomBackgroundAuthority`, `PhantomBackgroundService.captureOwnedInventory`.
Value object: `PhantomBackgroundState.InventoryFacts`.
Simulation: `PhantomBackgroundModel.inventoryCheck` (no new ordinary drops at overcapacity).
Canonical transaction: `PhantomBackgroundTransaction.inventoryFacts`, owned store prepare/finalize with exact SQL/canonical witnesses.
Codec: `PhantomBackgroundStateCodec` read/inspect; only change if existing v2 format cannot encode safe result (then BLOCKED_SCHEMA_DESIGN by default).
Lifecycle services: retained state is an intentional guard. Do not change global materialization/cleanup state machine to cover a failed store.

## Exact proof
Compare before native Player snapshot (every nonquest and quest item, inventory/paperdoll counts, full hash, native count/limit), PREPARED/FINALIZED immutable receipts, durable SQL items, reloaded Player, versions/epoch, and post-release World/AutoSave/identity. A late SELECT alone is not full lineage. No mutation during forensic reads. The untouched old candidate must reject this case; new candidate must save without changing item values.

## Negative cases
Stock normal occupancy, stock limit reached, already-over-limit occupancy, quest items, more than compact object bound, malicious duplicate ObjectIDs, wrong hash, foreign Player/lease, stale epoch, lost-reply and unchanged reward rules. If insufficient physical stock slots, normal nonstackable loot must not be awarded or falsely recorded as earned. Ordinary stack increase follows native policy; don't invent an extra restriction if stock allows it.

No general purpose migration or new persistence/telemetry subsystem. Changes should be easy to review in Java and include comments explaining the capacity semantics.
