# PHANTOM-M1-NATIVE-CONTEXT-HANDOFF-018

TASK017 proved the accepted human-local path:
ONLINE/local -> human.local ACCEPTED -> scheduler promotion ->
READY_ECOLOGY_DUE COMPLETE/ecology.native_materialization_required ->
READY_PASS -> MATERIALIZE_CALL NORMAL -> MATERIALIZE_RESULT CATCHUP_FENCED.

The contradiction is architectural:
- TASK011 ecology explicitly releases foreground materialization because a real Player
  is required to attest native context.
- one call later HistoricalBackgroundService.beforeMaterialize(NORMAL) rejects every
  non-COMPLETE catchup as catchup.normal_fenced.

Do NOT remove the general fence.

TASK018 adds an explicit exact-claim materialization intent:
NATIVE_CONTEXT_HANDOFF.

Only the exact historical request that caused ecology.native_materialization_required
may use it.
