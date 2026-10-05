# PHANTOM-M1-ORPHAN-CATCHUP-OWNERSHIP-016

Bounded architectural recovery task after TASK014/TASK015.

Known facts:
- TASK014 first lost edge is scheduler promotion -> ecology/readiness DEFER,
  reason `native_context.required:coalesced`.
- TASK015 offline retained observe014 state for profiles 110/175:
  ecology `requestPending=false`, ecology target=0;
  historical catchup `PENDING`, target=29852115;
  request ownership does not match.
- TASK015 did not modify/push production and did not establish the exact live false guard.

The current model has no ABORTED/ORPHAN historical status and `HistoricalBackgroundService.begin()`
rejects a different non-COMPLETE catchup as `catchup.claim.stale`.

This task establishes a general safe ownership recovery rule:
an exact orphan historical request may be RE-ADOPTED by ecology; a conflicting orphan
must remain fail-closed.

No profile-specific exception and no direct DB repair.
