# DATA_SUFFICIENCY017 — до запуска GS доказать, что новая выборка может дать FIT

## Реальные требования текущей системы

Источник — `ActionAdvantageV2.Frozen.sufficient()` и `TacticalStoreV3.freezeAutonomous()`, не произвольный новый список условий:

1. >=16 **валидных TRAIN gradient** после authentic selection/terminal pairing.
2. >=8 TUNING и >=8 AUDIT; held-out отделены от TRAIN.
3. >=2 класса и >=2 scope (Solo+Party) среди TRAIN gradient.
4. Минимум одно доказанное отличие selected versus stock; mere SHADOW equality не считается тренировочным сигналом.
5. Для альтернативной попытки — >=3 независимых baseline control blocks на exact context, а для TRAIN исключить собственный block; контроль/эпизоды не дублировать.
6. Нет ранее credited action/event, нет leakage исходов при выборе scene/seed, no reuse чужих samples.

Числа относятся к *валидным примерам*, **не к числу начатых боёв, кастов или завершённых эпизодов**.

## Проверка заранее

Построить `DATA_CENSUS017` по authentic 016:

| Scope | Split | Planned slots | Native settled | Terminal receipts | Exact selection | Baseline >=3 | Gradient/held-out |
|---|---|---|---|---|---|---|---|
| Solo | TRAIN/TUNING/AUDIT | ... | ... | ... | ... | ... | ... |
| Party | TRAIN/TUNING/AUDIT | ... | ... | ... | ... | ... | ... |

Известное из 016: Solo TRAIN stock10 slots/8 terminal; Solo TRAIN exploratory12/9 terminal; Solo TUNING6/6, AUDIT6/4. Party TRAIN stock5/0, exploratory6/0; Party TUNING3/0,AUDIT3/0. Это **не доказательство** скольких gradient samples хватит после исправления.

Построить upper bound и attainable bound для следующего **нового** dataset до live запуска, учитывая потери прошлой кампании. Если новый план не может дать требуемые минимумы даже при 100% успехе, **никакого GS/FIT** и остановка с рекомендацией изменения планирования будущей кампании (не менять frozen016).

Нельзя требовать, чтобы все реальные бои оканчивались победами: смерть — законный полезный terminal, если есть настоящая запись. А вот pre-CUT отказ, `NO_LEGAL_TARGET`, drain без терминала — не превращать в reward.

## Важная граница

Текущий Goal017 не запускает ещё одну длительную 51-эпизодную кампанию. Его задача — **устранить потерю доказательств и подготовить честный измеримый план достижения следующей обученной модели**. Если полезных данных 016 недостаточно, FIT=0 — правильный результат; поднимать optimizer без новых доказанных примеров запрещено.
