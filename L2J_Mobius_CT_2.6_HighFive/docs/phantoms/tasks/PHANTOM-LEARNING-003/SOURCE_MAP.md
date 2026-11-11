# Прочитать в первую очередь — реальная кодовая база 002 (GitHub pin b7b55754)

| Назначение | Точный путь внутри модуля |
|---|---|
| V1 action encoder | `java/org/l2jmobius/gameserver/phantoms/learning/LearningFeatures.java` |
| Существующая MLP, residual v2 | `java/org/l2jmobius/gameserver/phantoms/learning/NeuralPolicy.java` |
| OFF/SHADOW/ON, выбор и метрики | `java/org/l2jmobius/gameserver/phantoms/learning/LearningPolicy.java` |
| Exact offensive native receipt | `java/org/l2jmobius/gameserver/phantoms/learning/LearningCastEvidence.java` |
| MariaDB schema/checkpoints, actor split | `java/org/l2jmobius/gameserver/phantoms/learning/LearningStore.java` |
| Native observed episode metadata | `java/org/l2jmobius/gameserver/phantoms/learning/TrainingCollector.java` |
| SQL ledger/canonical dataset | `java/org/l2jmobius/gameserver/phantoms/learning/TrainingStore.java` |
| Automating train/audit/export | `java/org/l2jmobius/gameserver/phantoms/learning/TrainingEvaluator.java` |
| Configuration | `java/org/l2jmobius/gameserver/phantoms/learning/TrainingConfig.java` |
| Game battle session + model hook | `java/org/l2jmobius/gameserver/phantoms/combat/PhantomCombatService.java` |
| Native candidate discovery/casting | `java/org/l2jmobius/gameserver/phantoms/combat/L2jCombatBackend.java` |
| HP heal/per-profile native receipts | `java/org/l2jmobius/gameserver/model/actor/PlayerNativeEvidence.java`; `PlayerNativeWork.java`; `model/actor/status/CreatureStatus.java` |
| Party heal/resurrection candidate ranking | `java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyTactics.java` |
| Party action ownership | `java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyCoordinator.java` |
| Party directives model | `java/org/l2jmobius/gameserver/phantoms/party/model/PhantomPartyModel.java` |
| Existing TEST scene and actors | `test/java/org/l2jmobius/tests/phantoms/PhantomTrainingActors.java`, `PhantomTrainingScenarios.java` |
| Existing live native fixture | `test/java/org/l2jmobius/tests/phantoms/PhantomTrainingNative.java`, `PhantomLearning001ShadowNative.java` |
| Fast checks | `test/java/org/l2jmobius/tests/phantoms/PhantomTrainingContracts.java`, `PhantomTrainingStoreChecks.java`, `PhantomTrainingScenarioChecks.java` |
| Director Windows | `tools/phantom-runtime/training/Training.ps1`, `TrainingOwner.ps1`, `TrainingBroker.ps1` |
| Existing user command guide | `tools/phantom-runtime/training/RUNBOOK_RU.md` |
| Driver/config 002 | `tools/phantom-runtime/training/TRAIN_CONFIG.ini` |
| Proven source of truth | `docs/phantoms/reports/PHANTOM-LEARNING-002.md`, `docs/phantoms/tasks/PHANTOM-LEARNING-002/HANDOFF.md` |

Пути следует проверить фактически. Не выводить по названиям классов разрешения на изменение соседних систем. Не перечитывать гигантские исторические отчёты, если нет прямого causal blocker. Не коммитить чужой `.phantom-local` или бинарный runtime.
