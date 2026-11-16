# Достоверные данные, новые веса и граница NIGHT

## V4, 019 — сохранить
- Вектор весов 128, `ActionValueV4`, `ActionLearningV4`, `ActionFeedbackV4`, `TacticalPolicyV3` — исходный работающий фундамент.
- Кандидат 019: SHA `569295cf4d8f4172eee0e8a7e530246534dd1cf24d34ab95e07ded65c6b2f061`; 161 updates на 161 TRAIN effects, 86 heldout, 247 total, 27 episodes. Отдельный candidate file в локальном evidence не реконструировать из SHA; открыть существующую физическую копию, проверив SHA.
- OFF по умолчанию, V3 CHECKPOINT/Adam/SQL history неизменны. Между исходными законными DAMAGE действиями `[1230,1275]` изменение score было, но выбранный stock1275 остался (margin). Не выдавать `score_changed` за `choice_changed`.

## Новый authenticated sample (after CUT)
Передавать байты настоящего `TacticalStoreV3.Row` и SHA, исходный owner receipt, реальную `TacticalEpisodeV3` со `settled` + event key + actor/epoch, split из **нового** эксперимента и SHA исходных raw. Существующий `ActionFeedbackV4.nativeAction` запрещает чужие/contract/fake sources; не ослаблять. `TacticalReceiptV3` уже знает selected action, HP/MP paid, elapsed — XP/SP необязательны. **Если нет native settlement, событие не является достаточным V4 proof**; не создавать фиктивный terminal.

Событие может быть TRAIN даже при победе/поражении, если split выдан заранее и legal effect подтверждён. Нельзя после исхода переименовать split, нельзя использовать AUDIT/TUNING для градиентов, нельзя удваивать same event; model-SHA и dataset pins уникальны. Исторические 016 records read-only, модель V4 не меняет старые V1/V2 lineages.

## Новый update
- `ActionLearningV4.fit(oldCandidate, newNativeRecords)` вне GameServer после drain. At least one fresh TRAIN update and distinct changed SHA, exact cold reimport. Использовать сохранённую старую event ledger, не сбрасывать её ради новых updates.
- Versioned candidate filenames; `save` только create-new/atomic, newest pointer при необходимости только после полного cold proof. Crash/tamper/duplicate → отказ, old version доступна. Без автоматического production promotion.
- Если V4 ranking консультирует модель, но stock остаётся лучшим/проходит margin — это честный успех консультации и исполнения, **но не доказательство улучшения поведения**. Для реального изменения выбора требовать отдельную подтверждённую границу; не снижать safety margin ради демонстрации.
- Никакой самостоятельной обучающей ночи в 020. После удачного one-step передать для 021 точные `Start/Status/Stop/Resume` требования, границы сохраняемой истории и источник живых receipts; 021 уже добавит продолжительный autonomous loop.
