# Failure matrix — Goal 022 Checkpoint 2

| Boundary | Failure | Required result |
|---|---|---|
| participant lookup | link changed/null/deleted | operation found by profile, then abort/block after revalidation |
| offer | expiry/refusal/disconnect | terminal offer, no operation effect |
| reserve | resource or participant conflict | no canonical action |
| DISPATCHING | cancel before action | ABORTED |
| OBSERVING | cancel/restart | reconcile or INCONSISTENT, no redispatch |
| direct trade | first/any item transferred then fault | global conservation, INCONSISTENT |
| direct trade | exact all-after | COMMITTED |
| store | Adena moved, item not moved | INCONSISTENT, no retry |
| store | stock/list/price drift | abort before effect |
| manufacture | ingredients consumed, no terminal callback | INCONSISTENT |
| manufacture | exact failure | COMMITTED failure |
| manufacture | exact normal/rare product | COMMITTED |
| goal write | fault after effect | restart no duplicate |
| operation audit | fault after Goal/store update | restart no duplicate |
| materialization | active offer/dispatch | blocked |
| shutdown | offered only | cancel/expire |
| shutdown | observing | fail-stop and drain |
