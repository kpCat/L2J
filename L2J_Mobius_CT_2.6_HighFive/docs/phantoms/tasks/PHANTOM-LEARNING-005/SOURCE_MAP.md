# SOURCE_MAP — read-first paths, verified from public 004 HEAD

Base `wip/phantom-learning-004 @ dff800f4b95232c132b374f8ec97c932eda4a6b1`. Repo root from cloned checkout; module `L2J_Mobius_CT_2.6_HighFive`.

## Models and admission
- `java/org/l2jmobius/gameserver/phantoms/learning/TacticalModelV3.java`: scorer128/64 and Adam warm continuation.
- `.../TacticalPolicyV3.java`: `damage(...)` ranks native-ready damage alternatives; `rank(...)` ranks noncritical Party HEAL choices. `Mode.OFF/SHADOW/ON`, budget5ms, CPU_SLOT, no cross-owner arbitration.
- `.../TacticalStoreV3.java`: accepted/model registry, immutable SQL, frozen dataset, `applyTest`, optimization parent/chain. DO NOT bypass `ACCEPTED_TEST`.
- `.../TacticalTrainerV3.java`: offline evaluator/group checks; no auto-ON; performance 004 based on MSE only.
- `.../LearningPolicy.java`, `LearningCastEvidence.java`, `NeuralPolicy.java`, `LearningStore.java`: V1 DAMAGE model; accepted model №1. More than one policy family means direct causal attribution must be explicit.
- 003 accepted V3 model2 ZIP: `docs/phantoms/tasks/PHANTOM-LEARNING-003/artifacts/accepted-model-2/model-2.zip`, exact ZIP SHA `04393f2cbc7ba8c4760e42c6f32ae93edd4b8bf848e50d25e97ee8b4348716b5`.
- 002 accepted V1 model1 ZIP: `docs/phantoms/tasks/PHANTOM-LEARNING-002/artifacts/accepted-model-1/model-1.zip`, exact ZIP SHA `10fc9c6c8c450e4974ca7abed861805a7640e2c67c8474ac5a4c96e003b493da`.

## World, life cycle, episodes
- `.../TacticalEpisodeV3.java`: exact end of episode native kill actors HP/MP/EXP/SP, `terminalRewardUsedForTraining=false` and UNKNOWN complete deaths/loot etc.
- `.../TacticalReceiptV3.java`, `HealEvidenceV3.java`, `TacticalFailureV3.java`, `TacticalReplayV3.java`: actions and attributable receipts.
- `combat/PhantomCombatService.java`, `combat/L2jCombatBackend.java`, `combat/PhantomCombatCapabilityResolver.java`: ready skill filters, cast owners, actual native damage.
- `party/PhantomPartyTactics.java`, `party/L2jPhantomPartyBackend.java`, `party/PhantomPartyBackend.java`: stock legal HEAL, emergency control retained.

## Native TEST
- `test/java/org/l2jmobius/tests/phantoms/PhantomTrainingNative.java`, `PhantomLearning004Native.java`, `PhantomLearning003Damage.java`, `PhantomLearning003Native.java`, `PhantomLearning001ShadowNative.java`, `PhantomTrainingActors.java`, `PhantomTrainingScenarios.java` and safe TEST fixtures.
- `tools/phantom-runtime/training/Training.ps1`, `TrainingV3.ini`, `TrainingOwner.ps1`, `TrainingBroker.ps1`, `RUNBOOK_RU.md`, G0 existing native asset authority. Do not add another supervisor.
- `dist/db_installer/sql/game/phantom_tactical_v3.sql` and `phantom_training.sql`, existing DB installer.

## Latest reports and evidence
- `docs/phantoms/reports/PHANTOM-LEARNING-004.md`.
- `docs/phantoms/tasks/PHANTOM-LEARNING-004/{RESULT,HANDOFF,EVIDENCE.json,COMMANDS}.md` (EVIDENCE.json has its own extension).
- `docs/phantoms/tasks/PHANTOM-LEARNING-004/evidence/{native-c.json,checkpoint-8.json,stop.json,controlled-interrupt.json}`.
- 004 selected DRAFT8 `ab62799...` is **not a lawful ON candidate**. Existing 004 full segments are **not** matched stock comparisons.

All API names must be confirmed against actual source at pinned HEAD; do not fabricate missing Java methods or assume CLI supports new params. Root/module AGENTS contain historical paths; current explicit restrictions prevail.
