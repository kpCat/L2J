# Startup seam census

Before edits search TEST sources for `PhantomSystem.startConfiguredForTesting(`.
For every call site record: file/suite, headless environment yes/no, script setup before first full start, shared helper yes/no, later PhantomSystem restarts yes/no, classification.

Classify as ALREADY_CURRENT, REFERENCE_FOCUSED, LEGACY_SAME_FAMILY, CUSTOM_SCRIPT_LIFECYCLE_REVIEW, or NOT_HEADLESS_FULL_RUNTIME.

Expected LEGACY_SAME_FAMILY minimum:
1. PhantomPopulationResetReseedGoal032Suite
2. PhantomPopulationResetOwnershipGoal032Suite
3. PhantomLocalPlayReadinessGoal031Suite
4. PhantomRestartFailureRecoveryGoal030Checkpoint3Suite
5. PhantomReleaseDecisionRollbackGoal030Checkpoint3Suite
6. PhantomCrossDomainAutonomousAlphaGoal030Checkpoint2Suite

Correction pattern: headless initialize -> shared helper ONCE -> suite setup -> first full PhantomSystem start. Never helper inside restart/startRuntime method.

CrossDomain CP2: replace its direct MASTER_HANDLER_FILE call with helper, then preserve native WHISPER handler lookup.

Do not change Goal033 (already current), Goal036 reference behavior, generic headless EffectMaster-only contract, or production PhantomSystem script ownership.

Add/extend a TEST-only static guard so the audited allowlist cannot regress to full PhantomSystem startup without the helper.
