# AUTO_STOP013 — автоматическая остановка без ложных побед

**Цель:** пользователь нажал START и не обязан вручную будить Codex или сравнивать модели после каждых восьми часов. Автостоп никогда не утверждает «оптимальный ИИ».

## Условия

| Ситуация | Состояние | Влияет на patience? |
|---|---|---|
| Нет достаточных legal TRAIN controls/held-out | `INSUFFICIENT_EVIDENCE` или `PAUSED_NO_DATA` | Нет |
| Нет независимых законных V2 сцен/пар | `PAUSED_EVAL_COVERAGE_GAP` | Нет |
| Completed fit (реальные gradients), complete V2 EVAL, нет доказанного улучшения | `NO_MEASURABLE_PROGRESS` | Да, +1 |
| Completed fit + подтверждённое устойчивое улучшение без существенных регрессий | `IMPROVEMENT_SUPPORTED` | Сброс до 0, сохранить новый best TEST только в рамках разрешённого TEST policy |
| Новая модель хуже/опасные регрессии | `REGRESSION_OBSERVED` | Да, не продвигать, возможно остановить немедленно при критической |
| Сломан SQL/owner/ports/cold SHA/схема | `FAILED_FAIL_CLOSED` | Нет |
| Достигнут explicit time/native starts/RAM/free disk | `RESOURCE_LIMIT` | Нет |
| Владелец нажал PAUSE/STOP | `PAUSED_BY_OWNER`/`STOPPED_BY_OWNER` | Нет |

`patience=3` означает 3 **достаточных** независимых законченных последовательных fit+eval cycles без доказанного прироста, а не 3 периода времени, 3 раза ошибочного PREPARE или 3 случайные победы. После порога `PAUSED_NO_MEASURABLE_PROGRESS`. В отчёте указывать `confidence=INSUFFICIENT` при маленьком n; никогда не выдавать `NO_FURTHER_IMPROVEMENT_POSSIBLE`.

**Отсутствие улучшения**: `selectedEpoch=0` или V2 игровая эффективность `INCONCLUSIVE/NO_ADVANTAGE_OBSERVED` не подтверждает new best. Не продавать пользователю ложную «победу» модели. `INCONCLUSIVE` при достаточно выполненном протоколе может считаться одним *циклом без подтверждённого улучшения* для экономии ресурсов, но не свидетельством ухудшения или математического плато; обязателен reason `NO_SUPPORTED_GAIN_LOW_POWER` и консервативная пауза после трёх таких циклов.

**Минимум измерений:** n, count/contexts/classes, paired outcome, accepted vs selected vs optimizer ids, lower/upper strata, 95% interval если уместно, начальная/конечная CPU/RAM/disk, время native и расход starts.

## Проверки контроллера без многосуточного обучения

- 3 synthetic *state-machine* receipts с подтверждёнными phase/metric signatures дают pause; такие receipts **не являются игровым evidence**.
- Новое независимое улучшение сбрасывает patience; no-data и invalid-eval не увеличивают.
- Regression блокирует best/test promotion; статусы остаются раздельны.
- STOP между COMMIT и journal не повторяет credits; reboot/RESUME exact SQL readback.
- GUI closed/agent closed не останавливает процесс; новый GUI видит состояние и может нажать STOP.
- По умолчанию no autorun Windows; не менять power settings. Ночной24h soak НЕ обязателен для013 и `nightReady` не маркировать истинным без реального soak.
