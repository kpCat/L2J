# Source allowlist и сохранение

**Исходная граница**: редакция только перечисленных существующих файлов, если фактический root-cause audit подтверждает необходимость. Полные реальные пути относительно `L2J_Mobius_CT_2.6_HighFive/`:

1. `test/java/org/l2jmobius/tests/phantoms/PhantomAutonomousTrainingNative.java`
2. `test/java/org/l2jmobius/tests/phantoms/PhantomTrainingScenarios.java`
3. `test/java/org/l2jmobius/tests/phantoms/PhantomLearning001ShadowNative.java`
4. `java/org/l2jmobius/gameserver/phantoms/learning/AutonomousTrainingV1.java` — только если доказан прямой bottleneck в algorithm scheduling; без изменения reward/thresholds.
5. `test/java/org/l2jmobius/gameserver/phantoms/learning/AutonomousTrainingChecks.java`
6. `tools/phantom-runtime/training/Training.ps1` — только telemetry/admission/scope integration внутри существующего Director.
7. `tools/phantom-runtime/training/Test-Training.ps1`
8. `build.xml` — только точные новые focused test targets.

Ни один из них **не обязан измениться** без доказанной необходимости. Если нужен другой source path — STOP и запрос владельцу с последствиями, без молчаливого scope expansion. Обязательные новые docs/evidence/report и ROADMAP не считаются source расширением. Read-only разрешён в других исходниках.

**Запрещены изменения без новой явной санкции:** `PhantomPartyTactics.java`, `L2jPhantomPartyBackend.java`, `PhantomSupportEffectAuthority.java`, `PhantomSkillInventory.java`, `PhantomCombatService.java`, `TacticalPolicyV3.java`, `TacticalTrainerV3.java`, `TacticalStoreV3.java`, `TacticalModelV3.java`, `PhantomSystem.java`, штатный SkillData, general XML/progression, `Spawn`, GeoData, global farm и MAIN/PLAY. Никаких sql migrations или изменения ObjectiveV2.

Нельзя использовать `reset --hard`, `clean`, blanket stash, `git add .`, force push, новый WIP/worktree без разрешения, переписывание истории, удаление старых logs/snapshots. Source checkout `D:\Codex\repos\L2J-learning-004` — только после ревизии ветки/dirty; private TEST006 без SQL reset. Приватные модели, SQL, credentials, raw actor snapshots, GeoData не публиковать. Только curated non-sensitive evidence в Git.

Перед stage/push: exact status, `git diff --check`, source scope diff, targeted Ant/JAR, tracked clean where appropriate, `git push origin feature/phantom-world` обычный non-force, independently GitHub current feature HEAD, parents, diff/full source, source SHA/evidence. Обязательные русские отчёты без mojibake/escaped Cyrillic.
