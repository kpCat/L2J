# Admission root proof — TASK020

Source: PhantomSystem DecisionEngine admission used permitsNormalOperation; claimWorkLocked returns before planning and decisionSequence increment when this admission denies.
Retained observe019a profile110/object268485779: catchup PENDING row20153, request95ebe1a96c55ea7f9364e1b86e8166021179f3b8fbf388f9cc65ade678c16b82; goal ACTIVE farm.background revision29, matching catchup goal identity. Background READY row34539 is the post-shutdown state, not the earlier live MATERIALIZED state.
Accepted TASK019 evidence gives live worldPresent/alive, attached, sequence0, goal.reloaded. Retained DB confirms the exact historical fence still exists.
V01 uses real composed successful NATIVE_CONTEXT_HANDOFF with a non-COMPLETE catchup and a real DecisionEngine bound to the original admission: sequence0 and goal.reloaded. V18 changes only admission and advances sequence with the existing visible farm candidate.
Genuine RED: 15/18 pass; V02/V17/V18 fail for missing foreground decision admission/census. GREEN: 18/18. ROOT_PROVEN=true.
RED.log preserves semantic assertions and summary. Full pre-fix console output is private because the original JVM console encoding corrupted date prefixes; no source strings were escaped or repaired from guesses.
