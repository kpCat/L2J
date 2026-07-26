# REVIEW FINDINGS — Goal 007

## P1 — in-flight unregister orphan
A non-materialized slot can be removed while its external materialize call is
still running. Late success leaves a service-owned Player without a scheduler
slot.

## P1 — retained state can disappear
Requested/effective equality is checked before retained failure and can erase
the explicit cleanup requirement after signal changes or expiry.

## P1 — false materialized effective state
Successful retained-dematerialization cleanup may directly assign a newer
ACTIVE/NEARBY request although the Player was just removed.

## P1 — adapter ownership mismatch
Specific result statuses may retain a service entry but are mapped as transient
instead of retained.

## P1/P2 — stop race
An already-running pulse may start/deliver work after STOPPING, and finishStop
does not prove quiescence.
