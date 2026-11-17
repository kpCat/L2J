# Контекст и ограниченный READ_SET

Remote `kpCat/L2J`; HEAD до corrective: `0cf5681cf156219a43930bbdeb283e126c2d7ccc`. Goal022 native acceptance claimed FULL, но owner gate открыт. Исторический Goal022 V1: один completed TRAIN episode, checkpoint188→206; V2: ещё один completed sealed native episode 206→216, исходный счётчик 2+1=3, после V2 `AUTO_STOPPED/BOOT_LIMIT`, OFF/own0/portsFree. Никакой новый GS в этой задаче не допускается.

Прямые коды:
- `tools/phantom-runtime/training/TrainingV4.ps1` — `Invoke-TrainActionValue022`, `Get-Train022HistoricalBoots`, `Get-Train022Cursor`, `Get-Train022Layout`, `Sync-Train022CommittedState`. Только bounded sections около строк 20–110, 310–490.
- `tools/phantom-runtime/training/Training.ps1` — param и `ActionValue022` dispatch (строки 1–38).
- `tools/phantom-runtime/training/Test-Training022.ps1` — 30 PS checks и новые targeted tests.
- `docs/phantoms/tasks/PHANTOM-LEARNING-022-V4-AUTONOMOUS-SOLO-DAMAGE-TRAINER/CONTINUATION_V2.md` — использованное однократное разрешение V2; НЕ редактировать.
- `.../CONTINUATION_RESULT.json` — читать только `authority`, `lineage`, `nativeEpisodes` summary, `cleanup`, `limitations`; не распечатывать все 28 receipts.

Ориентиры physical state (проверить локально, не подменять):
- checkpoint 188: `90cdb499d44edde5f53819b357f1383dc65f9b9de1bd4d43120f6a154d1a55a6`.
- checkpoint 206: `99ebcd8fe606ab54379f59dc411fec0f493b8d388009b0f564eec548f76ba537`.
- checkpoint 216: `90e66051a80f5c36c0c4c727089708f13a8f29cd6a11a1b36712e6084132add6`; ledger HEAD `1ff7547d9d4571673e1e5bfa58a1f3ac3799e4d95f587a32db9ae39622df2f69`, global keys302, sequence2; cursor2→4.

**Initial READ_SET (не более 12 exact files):**
1. Корневой `AGENTS.md`.
2. Этот `TASK.md`.
3. Этот `CONTEXT.md`.
4. `ARCHITECTURE.md`.
5. `ACCEPTANCE.md`.
6. `SAFETY.md`.
7. `TEST_MATRIX.md`.
8. `tools/phantom-runtime/training/TrainingV4.ps1` — targeted symbol ranges.
9. `tools/phantom-runtime/training/Training.ps1` — первые 38 строк.
10. `tools/phantom-runtime/training/Test-Training022.ps1` — короткий файл.
11. Исторический `CONTINUATION_V2.md` — один раз.
12. Исторический `CONTINUATION_RESULT.json` — bounded selective fields.

Дополнительный READ_SET ≤5 exact files по причинному запросу: `ActionContinuationV4.java` (API head/lineage), `PhantomLearning022V4Trainer.java` (cursor/preview), `TrainingOwner.ps1` relevant symbols, `CODEX_WORKFLOW_CONTRACT.md`, `PHANTOM_CODEX_EFFICIENCY_STANDARD.md`. Обычная работа: максимум шесть scoped repository searches, никаких глобальных хроник и полных многостраничных дампов.
