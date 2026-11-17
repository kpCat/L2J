# Контекст и точный READ_SET

## Подтверждено в GitHub на 17.11.2026
- Repo `kpCat/L2J`, HEAD `8926e4da5b59d9b9a376dbac8fce05c8fe3a934f`, branch `feature/phantom-world`.
- Goal021 accepted Solo/DAMAGE, parent188 SHA `90cdb499d44edde5f53819b357f1383dc65f9b9de1bd4d43120f6a154d1a55a6`.
- Goal022 full Test-ready per review; два original sealed native эпизода (1 kill, 1 actor death), `188→206→216`, 28 distinct paid V4 receipts; checkpoint216 SHA `90e66051a80f5c36c0c4c727089708f13a8f29cd6a11a1b36712e6084132add6`; globalKeys302, seq2, cursor4. MODEL_REORDERED=false, SUPERIORITY=NOT_EVALUATED.
- Latest corrective `2cf50de22c9` + docs HEAD `8926e4da5b5`: независимый TEST new campaign inherits 216/302 keys/cursor4; 480min prepare-only offline passed, 0 GS/SQL; actual 480min NOT_RUN.
- Current `Get-Train022StopReason`: deadline, disk, boot cap, no-data, stagnation. `stagnation` currently checks **updates growth**, НЕ боевое качество. TRAIN split rounds 0..5, TUNING round6, AUDIT round7 (mod8); current TUNING/AUDIT run baseline only, full independent candidate comparison NOT_PROVEN. `Save-Train022Status` currently writes JSON/MD, no usable quality verification or HTML.
- V3 historical `Get-TrainCycleDecision013` in `TrainingOwner.ps1` has confidence/patience/independent-eval semantics; this is a **reference only**, not equivalent evidence in V4.

## Initial READ_SET (exact, <=12)
1. `AGENTS.md` repository root (exact, bounded).
2. `L2J_Mobius_CT_2.6_HighFive/docs/phantoms/CODEX_WORKFLOW_CONTRACT.md` (relevant sections).
3. `L2J_Mobius_CT_2.6_HighFive/docs/phantoms/PHANTOM_CODEX_EFFICIENCY_STANDARD.md` (relevant sections).
4. `L2J_Mobius_CT_2.6_HighFive/tools/phantom-runtime/training/TrainingV4.ps1` (only `Get-Train022StopReason`, `Get-Train022Status`, `Save-Train022Status`, `Complete-Train022Round`, `Invoke-Train022Controller`, `Invoke-TrainActionValue022`).
5. `L2J_Mobius_CT_2.6_HighFive/tools/phantom-runtime/training/Test-Training022.ps1` (focused test entrypoint).
6. `L2J_Mobius_CT_2.6_HighFive/tools/phantom-runtime/training/Training.ps1` (only argument/022 dispatch).
7. `L2J_Mobius_CT_2.6_HighFive/tools/phantom-runtime/training/TrainingOwner.ps1` (only `Get-TrainCycleDecision013`, no full read).
8. `L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/learning/ActionLearningV4.java` (short).
9. `L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/learning/ActionFeedbackV4.java` (short).
10. `L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-LEARNING-022-V4-AUTONOMOUS-SOLO-DAMAGE-TRAINER/CONTINUATION_RESULT.json` (targeted fields, no full 714-line print).
11. `L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-LEARNING-022-V4-AUTONOMOUS-SOLO-DAMAGE-TRAINER/CORRECTIVE-POST-V2/RESULT_CORRECTIVE.json` (targeted fields).
12. `L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-LEARNING-023-V4-QUALITY-GUARD-AND-PROGRESS/TASK.md` (this task).

Additional at most 5 exact files with reported reason; no history sweep. New exact extra path only if justified and within SAFETY.
