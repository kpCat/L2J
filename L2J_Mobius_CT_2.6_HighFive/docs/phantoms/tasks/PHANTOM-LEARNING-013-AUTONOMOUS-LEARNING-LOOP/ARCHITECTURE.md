# ARCHITECTURE013 — одна система, без второго Director

## Цикл

```text
IDLE → PREFLIGHT → PLAN_AND_SEAL_COHORT → COLLECT_NATIVE (TRAIN/TUNING/AUDIT)
     → FROZEN_DATASET → FIT_OFFLINE → STORE_DRAFT_AND_TERMINAL_ADAM
     → EVAL_V2_PREPARE (held-out) → EVAL_V2_NATIVE (STOCK/FROZEN_CANDIDATE)
     → QUALITY_DECISION → CHECKPOINT_ATOMIC
     → NEXT_CYCLE | PAUSED_NO_MEASURABLE_PROGRESS | INSUFFICIENT_EVIDENCE
                    | PAUSED_EVAL_COVERAGE_GAP | FAILED | RESOURCE_LIMIT
```

## Состояние

- Immutable `campaign`, `cycle`, `dataset`, `sourceSha`, `jarSha`, `configSha`, `modelSource`, `optimizerParent`, `trainingSplitSha`, `evaluationSplitSha`, `outcomeProtocol=V2`, `issuedBudget`, `spentBudget`.
- `selectedCheckpoint` может иметь epoch0, `optimizerContinuation` содержит действительные Adam moments, changes/updates/epochs, `acceptedTEST` отдельно.
- `bestVerifiedTEST` — только модель с отдельными достаточными проверками. Если такого сравнительного результата нет — прежняя лучшая остаётся, `bestDecision=UNRESOLVED`, а не «новая лучшая».
- Процессы/дедлайны: `ownerNonce`, PID/start, held process handle/exit, `pending` reservations; после аварии `recover` сверяет SQL receipt и journal, не повторяет CUT.

## Split fence

TRAIN-only exploration; TUNING для выбора checkpoint; свежий AUDIT не используется для подгонки; независимая V2 EVAL не имеет `TrainingCredit` и не становится TRAIN ни при Resume, ни при ошибке. Разводить фактических actor incarnations, physical source snapshots, NPC/Geo facts и semantic contexts. Прежние 011/012 EVAL datasets — read-only regression/control, не training data и не новая независимая EVAL013.

## V2 интеграция

Переиспользовать `PhantomEvaluationOutcomeV2` и существующий native adapter, а не переписывать терминальное событие. V2 был TEST-only `EvaluationOnly`; для рабочего Director можно оформить отдельный временный строго скопированный evaluation-only config, pinned к frozen candidate, который не включает FIT/exploration. Не ослаблять валидацию исходного 012 режима ради удобства. Вернуть исходный собственный TRAIN config после завершения V2 фазы без exposure MAIN.

## Atomic delivery

Каждый цикл пишет machine-readable `cycle-status.json` (version + phase + reason + refs), `fit-result.json`, `eval-v2.json`, `decision.json`, `checkpoint.json`; только completed/immutable. Промежуточные файлы `.pending` с проверенной атомарной заменой. Reporter/GUI читает versioned state без перестроения собственного knowledge. Детальный источник истины SQL+exact native receipts, а не display string.

## Разделение результатов

- TRAINING_WORKED: реальный native dataset, fit и optimizer transaction.
- SELECTED_WEIGHTS_CHANGED: checkpoint epoch > 0, модель отличается по SHA.
- POLICY_CHANGED: самостоятельно выбранный paid action отличается при exploration OFF.
- IMPROVED_QUALITY: **независимый** evidence + заранее определённый критерий, нет критической регрессии; ни AUDIT MSE, ни одна победа сами по себе недостаточны.
- MODEL_DEPLOYED: **не в013**. MAIN/PLAY остаётся OFF.

## Запреты

Не заменять canonical native receipt искусственным исходом, не исполнять неучённые навыки, не делать тренировку внутри combat tick, не добавлять runtime LLM и не удалять старые CSV/SQL/ZIP. Новое persisted schema только при обоснованной necessity и отдельном approval; предпочтительна существующая state/journal persistence.
