# TEST CASES — Goal 005

## Schema

- exact two tables/columns/types/defaults;
- unique character link;
- component primary key and FK cascade;
- SQL replay twice;
- schema inventory 118 scripts / 207 statements.

## Profile

- create unlinked;
- generated ID, schema version 1, row version 0;
- find/missing;
- link/unlink with version increments;
- unique link conflict leaves both rows unchanged;
- stale update and stale delete rejected.

## Concurrency

- two repository instances and deterministic barrier;
- two writes from same row version;
- exactly one success and one conflict;
- final row version increments once.

## Components

- type validation;
- schema version validation;
- payload 0 and 4096 accepted;
- 4097 rejected;
- defensive input/output copies;
- insert/read/update;
- duplicate insert;
- stale update/delete;
- deterministic binary list order;
- opaque unknown future-like type round-trip.

## Restart/cascade

- second repository instance reads identical state;
- profile delete cascades component rows;
- no canonical character row is changed.

## Final cleanup

- all owned profile/component rows zero;
- DatabaseFactory closes;
- no non-daemon pool residue.
