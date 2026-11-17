# Проверенные предпосылки и ограниченный READ_SET

GitHub на 17.11.2026: `origin/feature/phantom-world` HEAD `d952a73b3d96ff2d35243251c7f9ae31c635484a`, parent `5126ee89cb2b2237601858661ac3a1fed7f21555`. Commit d952 — **только 2 документа** приёмки, 021 implementation лежит в `7e26020c464438d1b728614c1befc1fefdee9e60`; локальный статус/физические TEST datasets не проверены ассистентом.

**021 immutable facts:** 1 экспериментальный paid action stock `1230:28` → selected `1275:14`, 8 baseline paid, 9 TRAIN, 179→188, accepted SHA `90cdb499d44edde5f53819b357f1383dc65f9b9de1bd4d43120f6a154d1a55a6`; `nativeExit=1` после `EVAL012_INTEGER_stock`, offline recovery verified, `NATIVE_ACTOR_DIED`; production V4 OFF, superiority unknown.

**Изученные source facts:** `ActionValueV4` = 128 коэффициентов, `MAX_EVENTS=4096`, schema V4/1; его конструктор требует `updates <= events.size()`, поэтому простое обнуление event-map + перенос глобальных updates невозможно. `ActionLearningV4.fit` ≤256 samples, strict split и replay-key checking, save atomic create-only. `PhantomLearning020V4Native` жёстко привязан к parent 179 для 021, class94/filter, одному candidate, `train021*` и портам; это *fixture*, не готовый цикл. `AutonomousTrainingV1.nextCandidate/shortlist` умеет детерминированный lawful proposal, но сам proposal не заменяет owner/native admission. `TrainingOwner.ps1` содержит `Invoke-TrainActionValue021Step`, owned locks/processes; старый `AutonomousTraining.ini` и GUI описывают историческую V3/009, а не принятую V4 night readiness.

## Initial READ_SET — ровно 12 exact paths, только нужные symbols/диапазоны

Относительно Git root; префикс `M = L2J_Mobius_CT_2.6_HighFive/`:

1. `AGENTS.md` — глобальные Git/DB правила.
2. `M/docs/phantoms/PHANTOM_CURRENT_STATUS.md` — только актуальные разделы (не всю историю).
3. `M/docs/phantoms/CODEX_WORKFLOW_CONTRACT.md`.
4. `M/docs/phantoms/PHANTOM_CODEX_EFFICIENCY_STANDARD.md`.
5. `M/docs/phantoms/tasks/PHANTOM-LEARNING-021-V4-SAFE-EXPLORATION-EFFICIENCY/CONTRACT_V1.md`.
6. `M/docs/phantoms/tasks/PHANTOM-LEARNING-021-V4-SAFE-EXPLORATION-EFFICIENCY/CONTINUATION_EVIDENCE.json`.
7. `M/java/org/l2jmobius/gameserver/phantoms/learning/ActionValueV4.java`.
8. `M/java/org/l2jmobius/gameserver/phantoms/learning/ActionLearningV4.java`.
9. `M/tools/phantom-runtime/training/TrainingOwner.ps1` — только `Invoke-TrainActionValue02*`, ownership, process and lock symbols.
10. `M/tools/phantom-runtime/training/Training.ps1` — params/action router/status/STOP boundaries; **не читать все >2000 строк**.
11. `M/test/java/org/l2jmobius/tests/phantoms/PhantomLearning020V4Native.java` — только candidates, preflight, encounter, nativeOnce, samples, fit/cold, dispatch.
12. `M/java/org/l2jmobius/gameserver/phantoms/learning/AutonomousTrainingV1.java` — `nextCandidate`, `shortlist`, `ActorSeed`.

**Дополнительные exact paths, ≤5 при обосновании в отчёте:** `ActionExplorationV4.java`, `ActionExperimentReceiptV4.java`, `TacticalPolicyV3.java`, `test/.../PhantomLearning021V4Exploration.java`, `tools/phantom-runtime/training/TrainingBroker.ps1`. Если нужен шестой — заменить менее релевантный, не начинать обзор всего репозитория. Документы этого пакета считаются входом задачи, не историческим READ_SET.

До первого patch: ≤6 repo searches. Проверить актуальный HEAD и ключевые diff, если изменился — точечно сверить только контракты 021/V4, без слепого rebase/reset. Private TEST paths/owners/physical SHA/ports выяснить на машине, не брать из текста как гарантию.
