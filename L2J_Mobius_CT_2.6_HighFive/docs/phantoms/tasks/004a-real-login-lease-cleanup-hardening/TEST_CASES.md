# TEST CASES — Task 004A

## Arbitration

- disabled/null owner -> false;
- disabled/REAL_LOGIN -> false;
- disabled/PHANTOM -> true;
- enabled/null/REAL_LOGIN/PHANTOM -> true;
- disabled normal policy leaves registry empty.

## Cleanup policy

- loaded/online/autosave -> incomplete;
- World-present -> incomplete;
- client-attached -> incomplete;
- canonical deleted/offline/autosave-absent/client-null -> complete;
- policy is read-only.

## Materializer

- normal cleanup state `STORED`;
- before-store injected operation failure retains Player/output/lease;
- second cleanup succeeds and leaves zero residue;
- before-delete failure retains Player/output/lease;
- retry succeeds;
- repeated success no-op;
- original 11/11 after-step matrix remains green.

## Source/ordering gates

- onDisconnection owns `_playerLock`;
- DISCONNECTED assignment inside lock;
- CharacterSelect requires AUTHENTICATED inside lock before load;
- disabled branch bypasses REAL_LOGIN lease;
- no unconditional release after incomplete Player cleanup.

## Regression

- Task 002/002A tests;
- Task 003 skeleton;
- Task 004 functional/performance;
- ant verify/jar;
- production JAR contains no tests.
