# Passive ITEM conflict witness staging

Author scope is the single pending `PhantomBackgroundTransaction.java` source. This is diagnostic staging for the actual WORLD11 `transaction.item_conflict_canonical`; it does not establish an item defect or change its handling. The 309 reported failures are repeated observations, not 309 established bugs.

| Artifact | SHA-256 |
| --- | --- |
| Published Transaction, unchanged | `30BF53E0C4EF789A6CD27AF12F642CA50270CCCFB3F38F180D597CFC92A974C7` |
| Frozen Transaction.pending and matching private compiler source | `C144970C74D41862AB957D43B4DAD815ED486C0DF6BABDD8ED50F0C018A2CD11` |
| Original `evidence/red-world11.txt` | `006B952FA8084A330EE99A0D087439795C793F0DFEB4BD74AFEB5A6A815A7FAF` |

Read-first inspected `execute`, `prepareOwnedStore`, `mutateItems`, `lockAndValidateGoal`, `inventoryFacts`, `failureResult`, existing `rollback`, and private `StateConflict`. The local once-only diagnostic analogue is the existing WorldSuite `NativeSqlDiagnostic` CAS latch. `PhantomBackgroundState.InventoryFacts` stores the actual tracked objects and locations; native items have no admitted row-version field. The existing private conflict transport and original cleanup order are reused.

The unchanged failure predicates now attach an immutable scalar witness with three separate branch labels: `EXECUTE_NON_MUTABLE_ID`, `EXECUTE_INVENTORY_UNDERFLOW`, and `OWNED_PREPARE_INVENTORY_MISMATCH`. It records the original delta, admitted mutable membership, separately summed expected and locked INVENTORY/PAPERDOLL counts, exact underflow remainder, profile/character/class/goal/operation identity, state, and component metadata already locked by the original path. Component metadata is schema version, row version, and payload digest. Absent metadata is `NOT_LOCKED`; execute's native-context metadata is explicitly `NOT_EXPOSED_BY_ORIGINAL_CONTEXT_GUARD`. No extra query or native item row version is introduced.

The execute object identifier is the first actual locked INVENTORY object for the original failing item, with zero indicating absence. Owned PREPARE selects a differing tracked object where available; equality differences without such an object are reported as `NO_TRACKED_OBJECT_MISMATCH` with item/object zero. Its unavailable mutation delta and shortage are `NOT_APPLICABLE`. These sentinels prevent an aggregate/hash disagreement from being presented as a proven individual item shortage.

Witness construction reads only existing locked values and retains only primitives and strings. It adds no JDBC, actor, owner, command, list, raw payload, or callback reference to the retained record. Original mutation SQL, mutable IDs, quantities, PAPERDOLL exclusion, status, result, rollback, commit uncertainty, and public APIs remain unchanged.

Emission occurs in the outer `finally`, after original resource closure. A witness is eligible only after the original pre-commit rollback returns without adding a suppressed rollback failure. Original close failure clears eligibility; commit-attempted uncertainty never collects a witness. Diagnostic construction, formatting, or emission failure cannot replace the original result. A per-Transaction CAS retains only the first eligible completed conflict and permits at most one emission attempt, with no reset or retry spam. This is not a claim of chronological ordering between concurrent conflicts.

The exact output prefix is `PHANTOM_BACKGROUND_ITEM_CONFLICT_FIRST `. The default immutable-record formatting then supplies the scalar facts. No logger or output callback runs inside the original transaction/resource block or while its database locks are held.

Private filename-matching source is `.phantom-local/task007-item-conflict-witness-author/source/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundTransaction.java`. Isolated JDK 25 compilation completed with exit 0 using UTF-8, source/target 25, no annotation processing, and an empty sourcepath. The classpath was the immutable `.phantom-local/task007-frozen-phase09-classpath/production` and `/tests` plus existing `dist/libs` jars excluding sources, GameServer, and LoginServer. No compiled class was executed.

Required gates remain independent source review and a subsequent original WORLD run supplying the first actual witness. A future product repair needs that factual branch/item/quantity evidence. No Ant, database access, Git command, published-source mutation, native GREEN, or full W acceptance is claimed by this staging.
