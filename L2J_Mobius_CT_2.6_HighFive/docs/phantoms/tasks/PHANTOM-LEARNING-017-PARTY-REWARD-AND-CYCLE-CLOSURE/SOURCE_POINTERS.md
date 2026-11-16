# SOURCE_POINTERS017 — публичные источники для read-first

HEAD подготовки: `e8b4d6ae1be7bc9dc7d2c4c5b76bf480ca81c177`. **Перед реализацией проверить свежий HEAD.** Ссылки — на конкретную версию для воспроизводимости, не на текущую ветку.

- [Отчёт016](https://github.com/kpCat/L2J/blob/e8b4d6ae1be7bc9dc7d2c4c5b76bf480ca81c177/L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-LEARNING-016-INCREMENTAL-COHORT-AND-NATIVE-COLLECT/RESULT.md): 51 CUT / 399 events / 27 rewards /0 Party / DONE016 ошибка.
- [EVIDENCE016](https://github.com/kpCat/L2J/blob/e8b4d6ae1be7bc9dc7d2c4c5b76bf480ca81c177/L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-LEARNING-016-INCREMENTAL-COHORT-AND-NATIVE-COLLECT/EVIDENCE.json): generation2/native.rows по scope/split/experimental/reward и frozen lineage.
- `TerminalRewardReceiptV1.java`: ctor, `Boundary`, `TimedAction`, episode membership и native timestamp validation, persisted V1 codec.
- `PhantomAutonomousTrainingNative.java`: `collectEpisode`, `timings`, `excluded-actions.jsonl`, `terminal.bin/json`, `selection.bin`, live admission.
- `ActionAdvantageV2.java`: exact-cell leave-own-block-out baseline, `sufficient()` и `gradient()`.
- `TacticalStoreV3.java`: `autonomousSources()`, `freezeAutonomous()`.
- `Training.ps1`: NO_FIT checkpoint и `journal.phase='DONE016'`.
- `TrainingOwner.ps1`: `Save-TrainCycleState013`, regex `^[A-Z_]{1,32}$`.
- `AGENTS.md`: ветка feature и сохранение чужих worktrees/SQL.

**Непроверенные моменты:** личное содержимое TEST SQL, точные сообщения exception у всех Party действий, live readiness после исправления, стоимость будущей выборки. Их нельзя выдавать за подтверждённые по этому документу.
