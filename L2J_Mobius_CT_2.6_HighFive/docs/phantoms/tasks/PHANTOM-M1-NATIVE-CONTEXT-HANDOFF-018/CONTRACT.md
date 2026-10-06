# EXACT NATIVE-CONTEXT HANDOFF CONTRACT

## Purpose

NORMAL:
ordinary visible materialization; non-COMPLETE historical catchup remains fenced.

HISTORICAL_BASELINE:
existing internal temporary baseline/recovery purpose; semantics unchanged.

NATIVE_CONTEXT_HANDOFF:
foreground materialization requested only because exact historical/native context needs
a real native Player. The Player may remain visible after success.

## Permit minting

PhantomPopulationEcologyService.requestMaterializationDue(profileId) may mint a handoff
claim ONLY in the existing task011 branch returning:

`ecology.native_materialization_required`

All existing predicates remain required:
- current human demand;
- non-terminal;
- no wake failure;
- population plan/inventory ready;
- not metadata draining;
- MANAGED state;
- ecology requestPending;
- historical exists;
- exact requestId;
- exact from==ecology cursor;
- exact target==ecology window target;
- historical status != COMPLETE;
- last failure classified by requiresNativeMaterialization.

Permit carries only the exact historical requestId.
No new request generated.

If any predicate false: no handoff permit.

## Typed scheduler bridge

Do not encode requestId in reason strings.

Use a typed activity materialization request/intent, e.g.:

```java
MaterializationRequest {
  Kind.NORMAL
  Kind.NATIVE_CONTEXT_HANDOFF
  ownerClaim
}
```

Existing callers remain NORMAL.

PhantomReconcileFirstActivityPort propagates NATIVE_CONTEXT_HANDOFF only when ecology
returned the exact native-context permit.

## MaterializationService

Add MaterializationPurpose.NATIVE_CONTEXT_HANDOFF.

Validation:
- NORMAL => empty claim;
- HISTORICAL_BASELINE => non-empty claim;
- NATIVE_CONTEXT_HANDOFF => non-empty claim.

Recorder:
- MATERIALIZE_CALL distinguishes service.NORMAL vs service.NATIVE_CONTEXT_HANDOFF.
- on lifecycle AdmissionRejectedException emit `MATERIALIZE_ADMISSION_REJECT` with
  bounded exception message before mapping to CATCHUP_FENCED.

## HistoricalBackground lifecycle

NORMAL:
unchanged; non-COMPLETE catchup => catchup.normal_fenced.

NATIVE_CONTEXT_HANDOFF:
- catchup exists;
- requestId == ownerClaim;
- status != COMPLETE;
- capture exact catchup component row/version/payload at admission;
- any claim/component change before/after load => fail closed.

Do not require RecoveryClaim used by HISTORICAL_BASELINE.
Typed ecology permit is the foreground authority.

## Background lifecycle

Treat NATIVE_CONTEXT_HANDOFF as exact historical admission for native-context attestation:
- capture exact background.catchup component + goal component;
- verify ownerClaim;
- create HistoricalAdmission;
- after Player.load require same native work owner/epoch and same captured components;
- reuse existing native-context refresh/attestation path;
- do not fabricate rewards/progress/inventory.

## After success

Do NOT complete or advance historical intervals inside handoff.

Catchup remains owned.
After native context is attested, normal ecology/history may resume later.

No forced dematerialization in this task.
