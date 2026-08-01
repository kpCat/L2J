# Failure matrix — Goal 021 Checkpoint 1

| Boundary | Failure | Required result |
|---|---|---|
| Goal parse | invalid target/amount/constraint | REPLAN, no state |
| planner | no facts | BLOCKED source.absent |
| planner | tied source | BLOCKED source.ambiguous |
| authority | hash drift | STALE_AUTHORITY, no action |
| eligibility | missing spoil/sweep | zero spoil item |
| target | identity mismatch | source.target_stale |
| spoil cast | unknown after dispatch | VERIFYING/UNCERTAIN |
| combat | different corpse | source.target_stale |
| sweep | ineligible corpse | source.sweep_ineligible |
| inventory | count unchanged | no progress |
| background | selected fact absent | rollback/replan |
| transaction | conflict | rollback all |
| capacity | weight/slot | typed block/switch |
| recipe | cycle/bound | BLOCKED recipe.* |
| manor/quest | encountered | DEFERRED_CHECKPOINT_2 |
| switch | active claim | RETRY |
| restart | DISPATCHING | reconcile only |
| shutdown | in-flight | drain or uncertain |
