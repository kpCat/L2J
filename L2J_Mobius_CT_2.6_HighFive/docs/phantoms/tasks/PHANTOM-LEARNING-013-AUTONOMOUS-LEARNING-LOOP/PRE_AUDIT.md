# PRE_AUDIT013 — подтверждено до выдачи

Источник: прямое чтение GitHub `kpCat/L2J`, `feature/phantom-world` на `52aac9e0274a63906a9fe5053611b8daf1aac778`. Перед кодом заново проверять актуальный HEAD и изменения.

1. `TacticalModelV3.continueTrainingWeighted` выполняет offline Adam, `selected Fit(model, epochs, loss)` и `continuation Optimizer(...)` — **разные** сущности. `epoch 0` значит выбран родительский scorer. Структурное изменение модели без проверки не требуется.
2. `TacticalTrainerV3.evaluateAutonomous`: `freezeAutonomous()`, `continueTrainingWeighted(parent.state(), frozen.weightedExamples(), frozen.examples("TUNING"))`, `saveAutonomous(...)`, `AUDIT/constant/teacher MSE`, DRAFT и model SHA. AUDIT не доказывает игровую силу.
3. `ActionAdvantageV2` — exact pre-CUT cell, leave-own-block-out ≥3 TRAIN controls, class-balanced IPS clipped; one paid action/episode. Обучение знает исход *выбранного* действия, а не настоящие counterfactual outcomes. Его threshold и revision неизменны без согласования.
4. `Training.ps1` автономные фазы `NEW/COLLECT/FIT/EVAL/CHECKPOINT`. Текущий `stagnation` основан на `tuningMse < tuningInitialMse`, `if stagnation>=3 then PAUSED`, независимо от полезности V2 A/B. `EVAL` может быть legacy V1; V2 ранее включалась `evaluationOutcomeVersion=2` в bounded EvaluationOnly. Это требуемый интеграционный разрыв.
5. `TrainingOwner.ps1` содержит versioned `autonomous009` config, `DurationMinutes`, `CheckpointMinutes`, `StagnationLimit=3`, `NoDataLimit`, exact native process exit accounting. Не писать второго supervisor.
6. `PhantomEvaluationOutcomeV2` и `PhantomEvaluationReplayV2` в `test/java` уже корректно считают нативные `NATIVE_KILL`, `NATIVE_ACTOR_DIED`, bounded TIMEOUT независимо от обеих побед. 012 fresh EVAL получила 4 terminal-comparable pairs, Solo1/2wins на сторону и Party2/2wins на сторону, 43 paid effects; GAME `INCONCLUSIVE`.
7. Состояние DRAFT14 / optimizer903 updates,223 epochs,48 applications,16 gradient episodes; accepted TEST2 и completed pointer12 сохранены. Холодный frozen SQL183 rows hash `9A9C2D0EE9A2B8F37DA5DD6BCF625334B76BE96FE9B76E22490ED20B4DA70E7D` — исторический ориентир, до G0 проверить новый фактический baseline.
8. `training UI` уже имеет START/STATUS/PAUSE/STOP/RESUME/EXPORT. Не добавлять второй интерфейс ради этого задания.
9. `PHANTOM-LEARNING-012` code head `5ee7af44caa70c62fbf044ac755f14d65c7e9c3f`, финальный docs head `52aac9e0…`. Техническое G0–G8 PASS, игровой verdict INCONCLUSIVE, manual owner gate может оставаться REQUIRED.
10. В составе обычного `PhantomSystem` V3 ещё не включена в production, `MAIN/PLAY` по умолчанию OFF. Задача013 НЕ интегрирует модель в игровой сервер — отдельная задача будущего этапа.

**Главный риск:** просто добавить ещё учебных эпизодов и посчитать updates за интеллект. Требуется реальный V2 feedback и честный best-model/stop-controller. Не объявлять «победило обучение» на одном изменившемся skill или 4 парах.
