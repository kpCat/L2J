# PHANTOM-M1-VISIBLE-DECISION-020
TASK020 repairs the narrow contract where a successful NATIVE_CONTEXT_HANDOFF Player is
visible in World but DecisionEngine rejects every work item because the same exact
historical catchup intentionally remains non-COMPLETE.

It also exposes the already-existing read-only visible-life census without using the
moving PREPARE_M1_ENVELOPE path.
