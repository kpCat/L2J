# PRE_AUDIT017 — проверенные входные факты и открытые вопросы

Основано на независимом чтении GitHub `e8b4d6ae1be7bc9dc7d2c4c5b76bf480ca81c177`, `RESULT.md`, `EVIDENCE.json`, `NEGATIVE_TESTS.md` 016 и Java/PS-исходников. **Приватный TEST SQL или runtime напрямую отсюда не наблюдался.** Codex обязан сделать новый cold readback в рамках собственного разрешённого TEST.

| Проверенный факт | Число / результат | Что нельзя заключать |
|---|---:|---|
| CUT/settled | 51/51 | Не равно 51 валидным train samples |
| Native paid effects | 399 | Не равно 399 независимым gradient samples |
| Исходы | 29 уникальных NPC kills; минимум16 deaths;5 no-target;1 drain | Смерть — допустимый исход, не повод искажать reward |
| Party эпизоды | 17 (11 TRAIN,3 TUNING,3 AUDIT) | Нельзя считать их пригодными без terminal receipt |
| Party terminal receipts | 0/17 | Точная причина не доказана типом IllegalArgumentException |
| Solo terminal receipts | 27/34 (TRAIN17,TUNING6,AUDIT4) | Ещё должны совпасть selection, block и baseline |
| Excluded terminal actions | 84 всего, в том числе Party71/Solo13 | Одна строка исключения — не индивидуальный диагноз |
| Использованный TRAIN exploratory в Solo | 9 валидных receipts из 12 exploratory slots | Не все 9 обязательно имеют корректный leave-block-out baseline |
| Party TRAIN exploratory | 6 слотов, 0 receipts | Даже идеальное восстановление даёт лишь +6 кандидатов |
| AUDIT Solo/Party | 4 валидных Solo, максимум3 Party | Макс7 при идеальном Party; порог8 не достигнут |
| Optimizer | Adam903 updates/223 epochs | FIT016=0, новой модели нет |
| Исторические строки | 183 five-SELECT, SHA `9A9C2D0EE9A2B8F37DA5DD6BCF625334B76BE96FE9B76E22490ED20B4DA70E7D` | Это сохранность старого набора, а не вся возможная таблица SQL |
| Изоляция | собственных процессов0, порты33357/18001/19301 свободны, OFF | Перед новой работой повторить G0 |

## Доказанный предел sufficiency 016

`ActionAdvantageV2.Frozen.sufficient()` требует >=16 TRAIN gradient, >=8 TUNING, >=8 AUDIT, минимум2класса и2 scope, хотя бы одно осмысленное расхождение STOCK/LEARNED. Для каждого target также необходимы >=3 независимых control blocks в том же контексте.

Существующие 016: **максимум9 валидных Solo exploratory +6 Party exploratory = 15**, даже если восстановить вообще все Party. AUDIT максимум **4 Solo +3 Party = 7**. Это *верхние границы*, фактических пригодных примеров может быть меньше. Следовательно, **FIT по неизменённому датасету016 заведомо недостижим только восстановлением Party**. Не пытаться «переобучить» или менять пороги ради отчёта.

## Что остаётся UNKNOWN

1. Какая именно проверка `TerminalRewardReceiptV1.create` отклонила каждое из 71 Party-действий: actor identity, terminal target, time envelope, linked lethal, exp/sp, event membership и т. п.
2. Достаточно ли подлинных timing/event/selection/episode receipts для точного read-only воспроизведения этих ошибок. Отсутствующие поля не восстанавливать догадкой.
3. Сколько из 27 Solo receipts имеют одну точную selection и корректный контекст для `ActionAdvantageV2.Sample`.
4. Какая будущая структура выборки позволит надёжно получить нужные данные в пределах бюджета без outcome-based отбора сцен.
5. Работает ли новый `DONE016` close/recovery в живом, а не только в синтетическом сценарии; 016 исторический ошибочный переход сохраняется.

## Источники в репозитории

- `.../learning/TerminalRewardReceiptV1.java`, validations в конструкторе и `create`.
- `.../learning/ActionAdvantageV2.java`, `Frozen.sufficient()` и exact baseline.
- `.../learning/TacticalStoreV3.java`, `autonomousSources()` / `freezeAutonomous()`.
- `.../tests/phantoms/PhantomAutonomousTrainingNative.java`, `collectEpisode` и `excluded-actions.jsonl`.
- `.../tools/phantom-runtime/training/TrainingOwner.ps1`, `Save-TrainCycleState013`.
- `.../tools/phantom-runtime/training/Training.ps1`, `DONE016` в FIT ветке.
