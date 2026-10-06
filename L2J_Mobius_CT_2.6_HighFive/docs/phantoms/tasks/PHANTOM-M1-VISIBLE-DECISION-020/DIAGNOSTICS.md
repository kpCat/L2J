# READ-ONLY VISIBLE DIAGNOSTICS

Existing `PhantomSystem.operatorVisibleLifeCensus(Player,long)` is already read-only and
contains currentActionGuard, runtimeReason, travelReason/travelFailureReason, AI/target/
AutoPlay, target rejection counts and PlayerNativeEvidence sequences.

Do NOT call PREPARE_M1_ENVELOPE.

Minimally extend existing `SNAPSHOT_PHANTOMS`:
optional args:
- `includeCensus=true|false`
- `censusAfterProfileId=<long>`

When true, append `operatorVisibleLifeCensus(actor, after)` to the result.
Default behavior remains unchanged.

This path must:
- leave TestAdmin XYZ identical before/after;
- not mutate target/movement;
- not create an envelope/ticket;
- not teleport/move/select/attack/cast;
- preserve bounded census pagination.

No new protocol operation is required.
