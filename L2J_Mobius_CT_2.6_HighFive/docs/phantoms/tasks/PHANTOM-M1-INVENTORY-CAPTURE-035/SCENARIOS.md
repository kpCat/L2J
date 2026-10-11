# SCENARIOS035 — no overfull reward loophole

Every current-Jar claimed PASS has: exact source commit SHA, compiled GameServer Jar SHA, DB clone identifier, process incarnation, profileId/objectId/epoch, before/after item inventory proof, exact FINALIZED and typed drain.

| ID | Scenario | Required outcome |
|---|---|---|
| C01 | Original 36/45 249 INVENTORY+3 PAPERDOLL, max250, old Jar | RED: `Invalid bounded background inventory` at NATIVE_STORE; original real inventory intact, retained; isolate old environment |
| C02 | Same exact inventory, new Jar/approved contract | All252 canonical objects preserved; native store FINALIZED (or honest unrelated typed blocker), no item lost, added or moved; correct hash, skills, EXP/SP, owner epoch |
| C03 | Over-cap 252/250 then attempted **new** ordinary nonstackable object | Rejected by background model/stock capacity; no earned item/delta, no phantom fake pickup |
| C04 | Valid stock occupancy: 247 INVENTORY+3 PAPERDOLL, 248+2, 250+0; at-limit +new item | No false store rejection for stock-valid items; normal capacity stops further slot; legitimate existing-stack updates follow native rule |
| C05 | Quest item excluded vs included; a few equipped and unknown/untracked items | Non-quest stock semantics remain correct, all canonical objects in hash, quest limit separate; never reinterpret PAPERDOLL as free inventory slots |
| C06 | Existing 031 codec negative bounds (250+25; compact 275), oversized >allowed, duplicate IDs, forged hash | Legal roundtrip passes; negative/safety checks still reject. No new schema version or silently lifted max. |
| C07 | Native store before/after sameDB reload for 36/45 on own copy | Exact item ID/objectID/count/loc and canonical hash, native context, goalRevision, earned values preserved; no duplicate over restart |
| C08 | Exact World/AutoSave/lease release only after FINALIZED | Current character no longer in World and owner released; typed `DONE/COMPLETE` with retained0 and no orphan timers/transactions; no forced release |
| C09 | 93-case matrix and 11-case lifecycle on one frozen Java Jar | Both truly pass with unchanged original acceptance semantics after product fix |
| C10 | Profile27 reserved earned cast-launch (unrelated) | Preserve unresolved 27 evidence; do not claim its correction unless same confirmed root, and don't use count difference as proof |

NEGATIVE: A test passing because it hides profile36/45, deletes their SQL records, strips equipment, raises `maximumSlots`, suppresses exception, fabricates FINALIZED, or transfers another Jar's PASS is NOT ACCEPTABLE.

If legitimate old data representation requires allowing overfull state, show a proof that the constructor cannot imply free capacity. The simulator's new-slot check must use the authoritative stock occupancy; update old healthy expectations only if strict semantic equivalence proves it necessary.
