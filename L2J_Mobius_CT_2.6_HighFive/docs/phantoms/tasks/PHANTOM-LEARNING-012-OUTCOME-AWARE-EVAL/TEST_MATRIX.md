# TEST_MATRIX012 — дешёвые RED→GREEN до первого GameServer

| ID | Доказательство | Негативный контроль |
|---|---|---|
| T01 | Воссоздать V1 ошибку: `stock death / learned kill` → legacy `MATCHED_INCOMPLETE` | Не редактировать/пересохранять legacy011 receipts |
| T02 | V2 terminal comparable death/kill | Outcome `NATIVE_ACTOR_DIED` без настоящего terminal settlement = REFUSE |
| T03 | V2 both death/death, нет goal success | `FAILED` Boolean сам по себе не доказывает death |
| T04 | V2 kill/kill ≠ обязательное условие comparable, но bothGoalSucceeded=true | Фальшивый kill без canonical target/epoch = REJECT |
| T05 | Verified timeout/time limit retains censoring | Stop/cancel/invalid owner ≠ legitimate timeout |
| T06 | Equal physical facts, unique actor/target/incarnations/event keys | Distinct sides, но reused object/epoch/event = REJECT |
| T07 | Seed order AB/BA per Solo и Party, no one-sided mode | Both sides ON или both STOCK = REJECT (кроме явно маркированного control protocol) |
| T08 | Missing HP/MP/EXP/SP = null/UNKNOWN | Missing economy не превращается в 0 |
| T09 | Time-to-kill только kill/kill | death/kill или timeout не получает псевдо-время победы |
| T10 | Обычные recorded deaths не выбрасываются из denominator | Incomplete/nonadmitted не становятся сравнимыми |
| T11 | No retroactive changing of G4 011 or historical V1 blobs | Altered source SHA/immutable receipts = FAIL |
| T12 | Solo difficulty tiers только canonical pre-CUT facts и frozen seed | Tiers/outcomes/next seed не зависят от результатов learned/stock |
| T13 | No hardcoded class/NPC IDs; актуальные learned ranks и enchant | Предлагаемый skill неизвестен/не выучен/не готов → refuse |
| T14 | Read-only V2 replay восьми authentic native011 arm receipts | Нет private receipt → PARTIAL, никакого fabricated replay |
| T15 | Atomic EVAL-only: no credit, no SQL/model mutation | `FIT`, `TRAIN exploration`, promotion, model mutation запрещены |
| T16 | Interrupted native eval reopens settled arm, not replayed | Reserved without terminal не объявлять settled |
| T17 | Exact global gameplay/Spawn/Geo/Party/Combat/source parity unaffected | No changes outside bounded task scope |
| T18 | Budget 2 GS / 35 min with each pre-CUT refusal counted | Bad preflight запрещает native start |
| T19 | Never report model superiority on n2–4 | Ambiguous outcomes → `INCONCLUSIVE` |
| T20 | `ant ... phantom-learning012-test phantom-learning011-test phantom-autonomous-training-test phantom-learning008-test phantom-learning007-test jar` | Вызывать существующие exact target names после проверки build.xml, DB verify без отдельного разрешения не запускать |

До native обязательно: authentic 011 offline replay and hash parity, new comparator correctness, read-only preflight, fresh exact model/Adam lineage and process/port guards. В doc: записать observed RED, observed GREEN, путь/exit code/test reason; compile-only RED не равен behavioral RED.
