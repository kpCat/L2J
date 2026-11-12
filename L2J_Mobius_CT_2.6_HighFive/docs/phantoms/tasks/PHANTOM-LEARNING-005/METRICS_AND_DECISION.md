# METRICS_AND_DECISION — измеримый профит, не выдуманный «ИИ сильнее»

## Принцип
Измерять **каждый** подтверждённый показатель отдельно; не складывать случайными коэффициентами HP, EXP, MP и время в единую магическую оценку. `NULL / UNKNOWN ≠ 0`. Приоритет для обычного Solo/Party фарма: законное достижение цели + выживание + скорость с учётом ресурсов. При отсутствии сравнимых знаменателей результат не определён.

## Поля пары и arm
- Arm: `modelFamily`, `mode`, approved id + weights SHA, `actualSelectedSkill/Target`, `owner identity`, `decisionRecorded`, native cast/effect receipts, `nativeElapsedMillis`, `preparationMillis` отдельно, `NPC target/spawn`, `finishReason`, `COMPLETED/FAILED/INCOMPLETE`, `wasTargetKilled` только exact writer.
- Персонаж: class/level/gear/buffs/HP/MP before and after, `death` status (lower bound, если полного receipt нет), EXP/SP **actual credited**. Не смешивать однократное EXP и EXP/time без точного delta.
- Party: состав, роли, native heal, actual effect, MP/HP/death каждого; «сумма EXP участников группы» обозначать суммой персональных кредитов, не суммой EXP NPC. `totalPartyDamage` не выводить из выбранных exact damage labels.
- Стоимость: MP/HP, shots/consumables/adenа только при законном native receipt; иначе UNKNOWN. Потраченные соски нельзя оценивать выдуманной ценой.
- Metadata: pre-CUT paired fingerprint, arm order, engine build SHA, policy schema/SHA, probe type, sample count, native timeouts and skip reasons.

## Сравнение
Для каждого pair вычислять `Δ = LEARNED − STOCK` по измеренным величинам, и доли/скорости только при корректном времени и одинаковой цели. Победитель по двум критериям `kill=1, survival=1` может быть хуже по MP/time, показывать Pareto trade-off. По проигранной схватке не ставить в числитель EXP=0, если player XP writer недоступен: UNKNOWN.

Формировать: победы/завершения по arm с `n`, время до kill (только matched eligible pairs), EXP/SP за время, потери HP/MP, полезное лечение, доля погибших, расходы если известны, количество изменённых и исполненных действий, список отклонённых пар. Важно не перепутать «модель изменила выбор цели Heal» с «эта Party победила благодаря Heal» — причинность требует наблюдаемый парный эффект.

## Статистика
При малом `n` давать paired deltas, медиану, разброс, доверительные интервалы / bootstrap **с честной пометкой очень малой выборки**. Нельзя утверждать значимое улучшение по 4 парам. Для вывода `ADVANTAGE_SUPPORTED` (если вообще достигнут) требуется заранее установленная минимальная независимая выборка (например ≥20 valid pairs на mode), 95% CI преимущества целевой метрики выше нуля, отсутствие убедительной регрессии смертей/безопасности и фактические neural divergences. При меньшем n результат `INCONCLUSIVE` / `NO_ADVANTAGE_OBSERVED`, даже при положительном среднем. Не перенастраивать критерии после просмотра результатов.

### Лейблы (не смешивать со статусом implementation)
- `ADVANTAGE_SUPPORTED`: достаточная выборка, реальное расхождение решений, стабильный положительный эффект в согласованной objective и отсутствие существенной safety-регрессии.
- `NO_ADVANTAGE_OBSERVED`: нет подтверждённого выигрыша на протестированном объёме; **не** означает доказательство равенства.
- `REGRESSION_OBSERVED`: явное негативное направление в конкретных измеренных показателях; с размером n и неопределённостью.
- `INCONCLUSIVE`: данных или точности недостаточно.
- `NO_POLICY_DIVERGENCE`: B не изменила реально исполняемые действия; сравнение исходов не может доказать влияние сети.

## Нельзя
- `DPS` из одного выбранного exact cast; `WIN` только потому что NPC потерял HP; `HP_saved` на основании предположения; `farm net profit` без adena/loot/consumables receipts.
- Выдавать улучшение MSE из 004 за увеличение win-rate. DRAFT8 не участник живого рейтинга.
- Утверждать «нейросеть научилась побеждать» если нет native ON и altered/observed action.
