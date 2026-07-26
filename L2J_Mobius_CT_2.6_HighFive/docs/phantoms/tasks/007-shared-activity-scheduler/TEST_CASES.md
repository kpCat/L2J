# TEST CASES — Goal 007

## Config/disabled

- disabled defaults allocate no scheduler/repository/service/future;
- strict max scheduled, pulse and per-pulse ranges;
- max scheduled must cover materialization cap;
- enabled missing/malformed values fail closed.

## Registration

- positive ID validation;
- bounded capacity;
- duplicate idempotency;
- initial SLEEPING, no ready/due;
- unregister stable sleeping;
- unregister materialized transition pending then removal.

## Signals

- source regex/TTL/sequence validation;
- 16 sources accepted, seventeenth rejected;
- update existing source at limit;
- stale replacement/withdrawal rejected without mutation;
- coalesced updates keep one ready entry;
- queue saturation rejects new mutation;
- highest-detail aggregation;
- deterministic expiry.

## State transitions

- promotion immediate;
- demotion grace;
- ACTIVE↔NEARBY no extra lifecycle call;
- WARM→NEARBY materializes;
- ACTIVE→BACKGROUND dematerializes;
- effective state never lies during pending transition;
- clean capacity block retries with bounded backoff;
- retained cleanup failure requires explicit retry.

## Work/fairness

- cadence per state;
- SLEEPING no work;
- sink receives no Player/domain object;
- sink exception isolated;
- same due cohort fair under small budget;
- immediate signal cannot create duplicate processing in one pulse.

## Overload

- thresholds exact;
- ACTIVE/NEARBY cadence unchanged;
- WARM/BACKGROUND multipliers 1/2/4/8;
- no state demotion or signal loss.

## Lifecycle

- one production recurring future;
- beginStop rejects all input and cancels future;
- failed materialization drain retains scheduler STOPPING slots;
- second shutdown finishes only after service STOPPED;
- Goal 006B two-phase handoff remains GREEN.

## Scale

- 10,000 SLEEPING slots;
- no per-slot Future/Thread/Executor;
- zero ready/due before signals;
- 10,000 WARM burst bounded/fair;
- deterministic performance summaries twice;
- final zero residue.
