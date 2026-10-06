# FOREGROUND DECISION ADMISSION

Keep `permitsNormalOperation()` unchanged.

Add a distinct `permitsDecision(profileId)` path. A non-COMPLETE catchup may pass only
while the exact successful NATIVE_CONTEXT_HANDOFF actor is the current foreground owner.

Required all:
F01 active foreground handoff record exists;
F02 it originated from NATIVE_CONTEXT_HANDOFF;
F03 exact admitted catchup Snapshot/requestId/component retained in memory;
F04 current catchup still equals that exact claim/component;
F05 current goal component still equals the handoff admission goal component;
F06 current materialization exists;
F07 State.ACTIVE;
F08 worldPresent=true;
F09 actionAdmissionOpen=true;
F10 characterObjectId matches;
F11 materializedAtNanos matches the successful handoff epoch;
F12 canonical Background state is MATERIALIZED;
F13 Background identity profile/object matches;
F14 cleanup/dematerialization has not begun.

Lifecycle:
- beforeMaterialize already has exact NATIVE_CONTEXT_HANDOFF admission.
- materializeSucceeded: validate and publish bounded in-memory ActiveForegroundHandoff
  with exact claim/component/goal/character/epoch, then release temporary admission.
- materializeAborted: remove matching active handoff.
- beforeStore: remove active handoff before native store/cleanup.
- stop/shutdown: no stale active handoff survives.

Never persist ActiveForegroundHandoff.
Never change requestId/status/cursor/goal merely to allow decisions.

PhantomSystem DecisionEngine admission changes from permitsNormalOperation to
permitsDecision, preserving the existing store-decision fence.

Do not modify DecisionEngine internals unless the hypothesis is disproved.
