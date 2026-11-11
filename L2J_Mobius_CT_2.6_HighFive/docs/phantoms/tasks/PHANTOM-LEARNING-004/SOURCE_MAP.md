# Verified source map — `wip/phantom-learning-003` at 06d42410...

Module root: `L2J_Mobius_CT_2.6_HighFive`.

## Existing neural
- `java/org/l2jmobius/gameserver/phantoms/learning/TacticalModelV3.java` — 128-state/64-action, 1,185,377 params, `MAX_EXAMPLES=4096`; `train()` calls `initialized(seed)` and creates fresh Adam moments: primary checkpoint problem.
- `.../TacticalObservationV3.java`, `TacticalSnapshotV3.java`, `LegalActionV3.java`, `TacticalTimingV3.java` — immutable known-mask rich states, lawful action families, native time/cooldown query.
- `.../TacticalPolicyV3.java` — currently ranks **non-emergency HEAL** candidates inside Party stock owner, under 5ms budget.
- `.../HealEvidenceV3.java` — actual HP effect / native MP debit receipts.
- `.../TacticalTrainerV3.java`, `TacticalStoreV3.java` — independent audit, immutable native DRAFT, explicit `apply-test`. Preserve.
- `.../LearningPolicy.java`, `LearningFeatures.java`, `LearningCastEvidence.java`, `LearningStore.java`, `NeuralPolicy.java` — V1/002 offensive chosen skill and exact HP witnesses; existing accepted v1 model not V3.

## Native / direct control
- `java/org/l2jmobius/gameserver/phantoms/combat/PhantomCombatService.java` (`issueAction`), `L2jCombatBackend.java` (`learningCandidates`, `castLearning`), `PhantomCombatCapabilityResolver.java` — cannot bypass Combat owner/skill legality. Code already filters ready alternative; keep it.
- `java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyTactics.java`, `L2jPhantomPartyBackend.java`, `PhantomPartyBackend.java` — stock Heal/Resurrection/Recharge and ownership; do not override emergency.
- `java/org/l2jmobius/gameserver/model/actor/Creature.java` and `MagicUseTask.java` — native cast time/reuse and actual effects; no second formula engine.

## Native TEST / Director
- `test/java/org/l2jmobius/tests/phantoms/PhantomLearning003Native.java`, `PhantomLearning001ShadowNative.java`, `PhantomTrainingNative.java`, `PhantomTrainingScenarios.java`, `PhantomTrainingActors.java`, `PhantomTrainingSql.java` — actual native scene provisioning, evidence.
- `tools/phantom-runtime/training/Training.ps1`, `TrainingOwner.ps1`, `TrainingBroker.ps1`, `TrainingBootstrap.ps1`, `TrainingV3.ini`, `RUNBOOK_RU.md`. Existing 7 TRAIN_*.cmd default V1 unless explicit V3 profile; no owner manual CMD test requested.
- `dist/db_installer/sql/game/phantom_tactical_v3.sql`, `phantom_training.sql`, actual full installer authoritative.
- `docs/phantoms/reports/PHANTOM-LEARNING-003.md`, `docs/phantoms/tasks/PHANTOM-LEARNING-003/{RESULT,HANDOFF,OWNER_ADDENDUM,PERFORMANCE,EVIDENCE.json}.` The latter is evidence, not new API.
- Accepted model2 public zip: `docs/phantoms/tasks/PHANTOM-LEARNING-003/artifacts/accepted-model-2/model-2.zip`; SHA `04393f2cbc7ba8c4760e42c6f32ae93edd4b8bf848e50d25e97ee8b4348716b5` (verify actual bytes).

Codex must inspect actual latest code before modifying; this is a map based on pinned HEAD, not permission to trust unverified symbols or skip repo AGENTS. New files only as needed. Limit changed production seams; never rewrite all engine for this goal.
