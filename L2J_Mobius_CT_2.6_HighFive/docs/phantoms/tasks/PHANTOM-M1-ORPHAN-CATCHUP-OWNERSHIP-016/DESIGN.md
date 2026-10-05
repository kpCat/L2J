# DESIGN

## Source-of-truth rule

`population.ecology.currentRequestId/currentWindowTargetEpochMinute` is the durable
outer owner of one `background.catchup` request.

Normal owned state:
- ecology requestPending=true;
- ecology.currentRequestId == historical.requestId;
- ecology.calendarCursor == historical.fromEpochMinute;
- ecology.currentWindowTarget == historical.targetEpochMinute.

Historical can advance its internal cursor while ecology retains its outer cursor until
the request completes.

## Orphan definition

An ORPHAN exists when:
- ecology is MANAGED and `requestPending=false`;
- historical component exists and status != COMPLETE.

Do not infer corruption merely from this pair. Classify it.

## EXACT_ADOPTABLE_ORPHAN

Re-adoption is allowed only if ALL:

O01 ecology.disposition == MANAGED
O02 ecology.requestPending == false
O03 historical != null
O04 historical.status in PENDING/RUNNING/FAILED_REPLAN_REQUIRED
O05 historical.fromEpochMinute == ecology.calendarCursorEpochMinute
O06 historical.targetEpochMinute > ecology.calendarCursorEpochMinute
O07 historical.deterministicSeed == `historicalSeed(ecologyState)`
O08 population profile is READY/participating
O09 no current materialized/native owner
O10 safe boundary has no blocking reason at the adoption instant
O11 ecology StoredState CAS identity/version has not changed

No requirement that historical authority hashes/generation are current:
the existing HistoricalBackgroundService owns stale-generation recovery/replan.
Re-adoption restores ownership; it does not bless the historical result.

## Conflict classes — hard STOP/fail-closed

- historical.from != ecology cursor;
- target <= cursor;
- deterministicSeed mismatch;
- profile not MANAGED/READY;
- active materialized/native owner;
- safeBoundary busy;
- ecology already owns a different request;
- any write would require changing historical requestId/from/target/cursor.

Never copy ecology into historical or historical progress into ecology cursor.

## Recovery operation

Implement inside `PhantomPopulationEcologyService`, worker path only, before starting a
new productive window.

Pseudo-contract:

```text
state has no request
    |
historical.status()
    |
COMPLETE/null -> ordinary path
    |
non-COMPLETE
    |
validate O01..O11
    |
persist ecology:
  state.beginRequest(historical.requestId, historical.targetEpochMinute)
    |
return from this worker step
    |
next worker iteration uses ordinary advanceRequest()
```

The historical component itself is not written.

Use existing ecology `_store.save`/`persist` optimistic CAS.
If CAS races/fails: ordinary bounded retry, no partial ownership fabrication.

## Why adoption rather than delete/reset

`background.catchup` has PENDING/RUNNING/COMPLETE/FAILED but no ABORTED state.
Deleting/replacing a non-COMPLETE catchup can destroy already committed interval progress
or a valid goal/plan. Re-attaching the exact outer owner preserves the existing request
and lets normal recovery decide its next step.

## Native-context case

After exact adoption, if historical returns `native_context.required:*`, the existing
task011 foreground exception remains the only route that may release materialization.
Do not special-case profiles110/175 and do not weaken that gate.
