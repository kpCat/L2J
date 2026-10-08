# Lifecycle state/outcome matrix

| Origin | Facts | Next | Gameplay |
|---|---|---|---|
| OPEN | new exact checkpoint, admitted once | WAIT_EARNED | closed |
| WAIT_EARNED | outstanding>0, deadline not expired | coalesced wait | closed |
| WAIT_EARNED | quiescent current owner | SEALED/CAPTURE | closed |
| CAPTURE | typed no-write rejection, unchanged owner, no incident | bounded defer/replan + OPEN | after control release |
| INVENTORY_FLUSH/PREPARE_ATTEMPTED | any uncertain exception | VERIFY_WRITE_OUTCOME | closed |
| PREPARED | exact receipt pending | RESOLVE_RECEIPT | closed |
| NATIVE_ATTEMPTED | canonical BEFORE | existing resolver policy, no invented reward | closed |
| NATIVE_ATTEMPTED | canonical AFTER | FINALIZE via existing receipt | closed until proof |
| NATIVE_ATTEMPTED | canonical NEITHER | retained conflict | closed |
| FINALIZED | publication incomplete | replay index/permit only | closed until exact |
| COMPLETED | owner/epoch/goal current | OPEN, then native continuation | open |
| ANY | real native writer failure/foreign epoch | TERMINAL_RETAIN | never reopened |
| COLD_VERIFY_PENDING | no owner, exact durable proof | resolver BEFORE/AFTER/NEITHER | before Player.load |

Temporary SEALED без pending сам по себе ничего не разрешает. Нужна подтверждённая
phase/witness для конечного transition. Null first reason при таком состоянии — diagnostic
contract failure, не COMMON_GUARDS_CLEAR.

Только новая materialization создаёт новую epoch. Создавать её ради сброса incident,
overflow или прошедшего test watchdog нельзя. Death/remat имеют отдельный lawful lifecycle.
