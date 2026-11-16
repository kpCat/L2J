# Git, protected inputs, explicit allowlist

## Git
- Только `https://github.com/kpCat/L2J`, ветка `feature/phantom-world`; baseline при выпуске `75567cfe7c6e1c5a215045208c388902b9e6b3cd` включает parallel Studio commit `75567cfe`. До изменений `git fetch origin feature/phantom-world`, проверить ancestry/foreign diff и **только ff-only SOURCE004** при чистом дереве. MAIN грязный и не для работы. TEST006 исторический — не чистить и не переписывать overlay. Без новых worktrees, clone, веток, `reset/clean/stash`, force/force-with-lease.
- При параллельном Studio push перед своим push новый fetch/ff с проверкой отсутствия конфликтов, только scoped commit для 020 в `feature/phantom-world`.
- В конце независимый GitHub API readback 100% изменённых файлов и актуальный HEAD, не доверять одному `git status` или своему RESULT.

## Approved bounded source scope
1. `test/java/org/l2jmobius/tests/phantoms/PhantomLearning019ActionValue.java` (reuse 019 runner and CLI).
2. `test/java/org/l2jmobius/tests/phantoms/PhantomLearning001ShadowNative.java` (short lawful acquired TEST scene; no legacy main overhaul).
3. `test/java/org/l2jmobius/tests/phantoms/PhantomAutonomousTrainingNative.java` — **только** узкий reuse helper if absolutely needed; не менять 016 loops/admission/history; лучше private helper within 020 test.
4. **Один** новый focused `test/java/org/l2jmobius/tests/phantoms/PhantomLearning020V4Native.java` при необходимости, без ещё одного framework.
5. `java/org/l2jmobius/gameserver/phantoms/learning/TacticalPolicyV3.java` только TEST V4 hook safety/observability; `ActionFeedbackV4.java`, `ActionLearningV4.java`, `ActionValueV4.java` только если нужен реальный direct native step/ledger guard; без смены схемы старых V4 checkpoint.
6. `tools/phantom-runtime/training/Training.ps1`, `TrainingOwner.ps1`, `Test-Training.ps1` — one-step runner/stop/native proof; не менять 016/017 historical flow.
7. `build.xml` — один focused 020 target, только нужная compile/tests.
8. `docs/phantoms/tasks/PHANTOM-LEARNING-020-V4-LAWFUL-NATIVE-AND-ONLINE-STEP/` — docs/evidence; `docs/phantoms/reports/PHANTOM-LEARNING-020.md`, одна свежая строка roadmap (не переписывать historical markers).

**Без согласования нельзя менять**: Gameplay production `PlayerNativeEvidence`, `Combat`, `Party`, `SkillData`, game data/spawns/Geo, existing training SQL schema, `TerminalRewardReceiptV1`, `ActionAdvantageV2`, `TacticalModelV3`, `TrainingConfig` глобально, reward weights, historical model rows, 016/017/018/019 artifacts. В 020 не добавлять новую систему фоновых таймеров/планировщик и не включать модель глобально.

## Hard preservation
- 016/017/018/019 original bytes, 183 SQL rows SHA `9A9C2D0EE9A2B8F37DA5DD6BCF625334B76BE96FE9B76E22490ED20B4DA70E7D` (при отсутствии fresh SQL test только `preserved files`, не писать fake `fresh SQL passed`).
- selected `DRAFT14`, accepted `TEST2`, Adam `903updates/223epochs`, `pointer12`, V4 019 candidate SHA `569295cf4d8f4172eee0e8a7e530246534dd1cf24d34ab95e07ded65c6b2f061`/161 updates.
- `Geo203/protected207`, MAIN/PLAY/user dirt, parallel Studio commits, secrets. Private SQL only copy, unique loopback ports and process ownership; final OFF, no owned processes, listeners free.
- No public raw SQL/credentials/owner nonces/model weights/native private dumps, no copied jar in Git. Public only anonymized receipts/aggregates/SHA.
