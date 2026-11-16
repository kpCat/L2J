# Verified source pointers, состояние GitHub на выпуск021

Remote: `https://github.com/kpCat/L2J`, branch `feature/phantom-world`, HEAD `30a7a74dd4f4777c8f8cd2670e12e3b37e2b34f8`. Перед работой сделать fresh fetch/readback; при другой версии точечно пересмотреть relevant diffs, не перезаписывать Studio.

- `java/org/l2jmobius/gameserver/phantoms/learning/TacticalPolicyV3.java`: `actionValueTest` ~37, `actionValueChoice` ~44, DAMAGE ~120–196, HEAL ~210–275. V4 TEST-only, when `_exploration != null` V4 critical fallback; V4 MARGIN=.025, native5ms budget. Goal021 должен иметь единственный decision-owner.
- `TrainExplorationV1.java`: `EPSILON=.25`, `pick` distribution and `Selection` with propensity/record ~15–65; existing exploration for V3. Reuse математические принципы, **не** навешивать одновременно на V4 decision. Legacy bytes unchanged.
- `ActionFeedbackV4.java`: typed `nativeAction` с immutable row/episode/owner/source SHA, `label()` return real `TacticalReceiptV3.reward()`. EXP/SP не требуется.
- `ActionLearningV4.java`: `fit(parent,samples)` batch≤256, heldout no gradient, unique event/episode ledger, atomic create-only `save/load`; old model V4 parent updates179. MAX_EVENTS=4096 in `ActionValueV4.java` — вопрос rollover/night trainer022.
- `LegalActionV3.java`: enum DAMAGE,HEAL,RECHARGE,RESURRECT_SKILL,CONTROL,DISPEL,MOVE,RETREAT,SUMMON,SPOIL,SWEEP; current trainable DAMAGE/HEAL only. Не включать все по галочке.
- `test/java/org/l2jmobius/tests/phantoms/PhantomLearning020V4Native.java`: `nativeOnce`, `encounter`, `samples`, `fit`, `cold`; Goal020 release source image→reproduceSolo020→positionAcquired016→native admission→settlement.
- `test/java/org/l2jmobius/tests/phantoms/PhantomAutonomousTrainingNative.java`: `soloSource020`, `reproduceSolo020`, `positionAcquired016`; exact source and skills. Source and private SQL image from earlier native016.
- `tools/phantom-runtime/training/TrainingOwner.ps1` and `Training.ps1`: one-step `RunOnce`/`Status`/`Stop` with source-history safety. 020 root `train020-c1` is already used; **fresh** new 021 owned runtime root and new reserved nonce.
- `docs/phantoms/tasks/PHANTOM-LEARNING-020-V4-LAWFUL-NATIVE-AND-ONLINE-STEP/CONTINUATION_RESULT.md`, `CONTINUATION_EVIDENCE.json` (owner acceptance updated at commit30a7a). Historical before SHA019=569295..., after020=42cfff..., 179 updates; native18 effects, 1 kill, model reordered=false.

No claim exact private filesystem access by assistant; Codex must validate existence, SHA and port/DB in its own environment. Do not commit raw private SQL, JAR, owner nonce, model weights, or private episode data.
