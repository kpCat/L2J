# TEST CASES — Task 004B

## Arbitration

- disabled/null false;
- disabled/REAL_LOGIN true;
- disabled/PHANTOM true;
- enabled/null true;
- enabled/REAL_LOGIN true;
- enabled/PHANTOM true;
- disabled/null creates no lease;
- retained REAL_LOGIN blocks second owner while disabled.

## Lease identity

- own ID matches;
- different ID does not match;
- stale close safe;
- exact-match release contract in Disconnection;
- mismatch retains lease;
- no-player retained lease is not released.

## Cleanup policy

- active Player incomplete;
- canonical cleanup complete;
- World player map must be null;
- World object map must be null;
- autosave object-ID query used;
- client null required;
- read-only behavior.

## Regression

- Task 004 actual packet effects;
- observer visibility;
- action conservation;
- action/cleanup race;
- old failure matrix 11/11;
- before-store retry;
- before-delete retry;
- terminal STORED;
- performance 2/2;
- all cumulative targets.
