# PRE_AUDIT012 — что подтверждено до задачи

Источник: опубликованная `feature/phantom-world` `9e78e3db1153126c616812bfeb8ff5bdbb8c8278`. Это снимок для чтения, не разрешение пропускать fresh remote audit.

1. `test/java/org/l2jmobius/tests/phantoms/PhantomAutonomousTrainingNative.java`:
   - `EvaluationArm` хранит `completed:boolean`;
   - `encodeArm/decodeArm` записывают `EVAL_ARM_V1`;
   - `pairStatus()` возвращает `MATCHED_COMPLETED` только если оба `completed` истинны и есть events, после проверки physical fingerprint, уникальности incarnations и событий;
   - `collectEpisode()` передаёт `completed = cut && episode != null && episode.outcome().equals("COMPLETED")`;
   - `evaluateNative()` считает только `MATCHED_COMPLETED`.
2. `java/org/l2jmobius/gameserver/phantoms/learning/TacticalEpisodeV3.java`: `outcome()` — показатель успеха игровой цели (`FAILED` при смерти), но `reason`, `actors.dead`, `kills`, `settledNanos`, `eventKeys` дают самостоятельное терминальное свидетельство.
3. `java/org/l2jmobius/gameserver/phantoms/learning/TerminalRewardReceiptV1.java`: `NATIVE_KILL`, `NATIVE_ACTOR_DIED`, `TIMEOUT` допустимы для terminal reward. Это не означает, что любое событие timeout автоматически доказывает завершённый сопоставимый бой. Историческую reward revision и SQL natural keys не менять.
4. `test/java/org/l2jmobius/tests/phantoms/PhantomLearning005Evaluation.java`: прежний `compare()` также отделяет `MATCHED_INCOMPLETE` при любом `FAILED`; нельзя менять его историческую статистику и итоги 005–011 задним числом.
5. Реальная 011: 2 SOLO и 2 PARTY пары с равными native pre-CUT facts, свежими incarnations, 47 unique paid effects. SOLO AB: STOCK `NATIVE_ACTOR_DIED`, LEARNED `NATIVE_KILL`; SOLO BA: оба `NATIVE_ACTOR_DIED`. Party AB/BA: kill/kill. Это маленькая выборка; преимущества модели статистически не доказано. Solo learned divergences 6 только в первом learned Solo; Party 0.
6. Frozen: DRAFT14 selected/terminal scope раздельно; optimizer Adam903 updates/223 epochs; 48 applications/16 gradient episodes; accepted TEST2 и completed-cycle pointer12 сохранены. TEST006 STOPPED/OFF по отчёту011; повторно проверить.
7. `feature/phantom-world` — единственная постоянная dev ветка; protected main dirty checkout не трогать. Известные source: `D:\Codex\repos\L2J-learning-004`; private TEST: `D:\Codex\repos\L2J-learning-006`. Оба проверять фактически; новые worktrees/clones не создавать без явной необходимости и разрешения.

**Отдельное замечание:** сравнимая пара ≠ победа обоих участников. До G4 нельзя уничтожать/менять старый `EVAL_ARM_V1`, исторические receipts и COUNTERS. Если реальные терминальные детали старого arm невозможно аутентифицировать, статус `UNKNOWN/NOT_COMPARABLE`, а не угадывание смерти из `completed=false`.
