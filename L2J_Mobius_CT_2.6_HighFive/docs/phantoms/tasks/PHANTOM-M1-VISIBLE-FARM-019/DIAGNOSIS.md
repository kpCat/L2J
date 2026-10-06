# Diagnosis

Use existing read-only operations only:
- SNAPSHOT_PHANTOMS
- SELECT_VISIBLE_PHANTOM_TRACE
- SNAPSHOT_SELECTED_PHANTOM_TRACE
- REPLAY_SELECTED_PHANTOM_TRACE when trace health permits
- PREPARE_M1_ENVELOPE stage=INITIAL profileId=<same profile>
- SNAPSHOT_M1_ENVELOPE includeCensus=true

Capture selected trace at t0, t+2s, t+5s plus >=2 envelope snapshots >=1s apart.

Classify exactly one:
D0 DECISION_NOT_ATTACHED
D1 GOAL_ABSENT_OR_INACTIVE
D2 DECISION_NO_CANDIDATE
D3 VISIBLE_PLAN_NOT_SELECTED
D4 VISIBLE_START_BLOCKED
D5 VISIBLE_SUITABILITY_OR_REPLAN
D6 VISIBLE_TRAVEL_BLOCK
D7 AUTOPLAY_START_GUARD
D8 AUTOPLAY_REGISTRATION_FAIL
D9 AUTOPLAY_STARTS_THEN_STOPS
D10 AUTOPLAY_RUNNING_NO_TARGET
D11 AUTOPLAY_TARGETED_NO_NATIVE_PROGRESS
D12 OUTSIDE_ALLOWED_SCOPE

Evidence must include trace runtimeState/candidateKey/step/attempt/lastResult/reasonKey
and envelope/census currentActionGuard/runtimeReason/travelReason/travelFailureReason
where available.

If not unique: BLOCKED_VISIBLE_DIAGNOSIS and STOP.
