# TEST CASES — Task 003

## Config

- canonical file exists and both flags are false;
- missing, blank, malformed or misspelled enable values stay false;
- true/false are case-insensitive;
- malformed diagnostics false;
- diagnostics cannot be effective while system disabled.

## Disabled

- configured start returns false and creates no instance;
- direct instance becomes DISABLED;
- scheduler inactive, queue zero, scheduled tasks zero;
- metrics all zero, trace empty;
- no new non-daemon thread;
- shutdown idempotent.

## Enabled

- start reaches RUNNING once;
- start metric exactly one;
- queue empty, scheduled tasks zero;
- repeated start no-op;
- shutdown reaches STOPPED once;
- stop metric exactly one;
- repeat stop and restart after STOPPED are no-op.

## Queue

- reject before start;
- accept only to fixed capacity;
- capacity+1 rejected;
- Runnable never executed;
- stop clears queue;
- accepted/rejected metrics exact;
- no worker/thread/future.

## Trace

- disabled empty/no ring;
- deterministic sampling;
- capacity bounded;
- oldest overwritten and drop count exact;
- snapshot bounded.

## Static integration

- ConfigLoader call;
- guarded GameServer start after ThreadPool and before IdManager;
- no disabled section log;
- shutdown before ThreadPool;
- forbidden dependencies absent;
- Task 004 symbols absent.

## Regression

Run unit, skeleton, all negative controls, DB integration, scenario, performance,
aggregate verify and jar.
