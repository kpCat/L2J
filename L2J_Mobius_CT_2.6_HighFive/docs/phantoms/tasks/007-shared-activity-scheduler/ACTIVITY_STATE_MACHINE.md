# ACTIVITY STATE MACHINE — Goal 007

## Detail ordering

```text
ACTIVE > NEARBY_PERCEPTIBLE > WARM > BACKGROUND > SLEEPING
```

## Aggregation

Highest unexpired signal wins. No signals means SLEEPING.

## Promotion

Promotion is immediate. A promotion crossing the materialization boundary is
committed only after materialization succeeds.

## Demotion

Demotion waits for the configured policy grace. A demotion crossing from
ACTIVE/NEARBY to WARM/BACKGROUND/SLEEPING is committed only after
materialization service cleanup succeeds.

## Retained lifecycle failure

A retained failure never receives periodic automatic retry. The slot remains in
its truthful effective state with
`RETAINED_FAILURE_REQUIRES_EXPLICIT_RETRY` until `retryTransition(profileId)`.

## Clean transient block

Capacity/profile/identity/service clean rejection keeps current state and uses
one due entry with overflow-safe exponential retry up to 30 seconds.

## Signal race

Every transition captures slot generation. After external service work, the
result is committed only if the slot is still registered and generation is
compatible; otherwise it is reconciled and re-enqueued without falsifying state.
