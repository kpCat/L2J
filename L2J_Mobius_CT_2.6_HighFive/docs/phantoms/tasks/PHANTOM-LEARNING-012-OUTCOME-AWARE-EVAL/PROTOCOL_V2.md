# PROTOCOL_V2 — различать terminal evidence, цель боя и качество политики

## Разные измерения одного native arm

1. `REQUESTED` → `HEADLESS_READY` → `LIVE_ADMITTED` → `CUT` → `TERMINAL_SETTLED` — жизненный цикл доказательства. Отказ до CUT не является исходом боя.
2. `TERMINAL_SETTLED` не равен `NATIVE_KILL`: `NATIVE_ACTOR_DIED` и точно зафиксированный `TIMEOUT` тоже могут быть завершённым наблюдением.
3. `goalSuccess=true` только при canonical linked kill целевой NPC в допустимом terminal boundary и с правильной ownership. Смерть после kill, если была, учитывать отдельно по actor state, не игнорировать.
4. `nativeDeath=true` только при canonical actor death snapshot/observed death count. В Party возможны отдельные смерти участников даже при kill, и это не обязательно полный defeat. Сохранять и цель, и потери.
5. `TIMEOUT`: если корректно завершён ограниченный бой, outcome цензурирован относительно будущей победы; `goalSuccessWithinLimit=false` можно утверждать лишь при полном наблюдении времени и отсутствии linked kill. Нельзя объявить «модель никогда бы не победила».
6. `CANCELLED` / `STOP_UNCONFIRMED` / non-admitted / stale receipt = not comparable, если нет достоверного terminal settlement. No victory/death fabrication.

## Новые артефакты и совместимость

Версионировать поверх старых, например:

```
EvaluationArmV2 {
  version, pairId, side, sourceSha, configSha, buildSha, modelSha,
  physicalSha, actorIncarnations, targetIncarnation, nativeEvents,
  liveAdmitted, cutNanos, terminalNanos, settledNanos,
  terminalReason, terminalSettled, goalSuccess, observedDeathsLowerBound,
  terminalDead, timeoutWithinWindow, metricsKnown, metricsUnknown
}
EvaluationPairV2 {
  pairId, scope, family, order, comparator,
  comparableTerminal, bothGoalSuccess, reason,
  stockOutcome, learnedOutcome, winDifference, deathDifference,
  knownMetrics, unknownMetrics
}
```

Это **пример структуры**, не приказ вводить новый глобальный production API. Выбрать минимум безопасных полей/имён после анализа code. Native field values не вычислять из `completed` Boolean старого V1. Terminal boundary/time и exact event keys обязательны. `null` для неизвестных метрик, не 0.

Архивы/БД прошлого не мутировать. Re-parse old011 receipts в новую analytics-only V2 с `sourceReceiptSha`/`analysisRevision`, без замены исходных `evaluation-arm.tsv` и иных immutables. Никаких SQL migrations для изменения оценки.

## Проверка пары

Сначала физическая сопоставимость: оба arms существуют и pin/seed/context/model/run согласованы; один STOCK, один LEARNED; равные canonical pre-CUT physical fingerprints; разные actor/target epochs; нет повторных event IDs; native legal admission и source identity совпадают по семантике физической ячейки.

Потом терминальность: обе стороны после CUT с подтверждённым settlement и reason, без unknown/foreign events. Не требовать kills или paid events как условие существования terminal settlement: например, персонаж может погибнуть до первого каста. Отсутствие paid evidence делает метрику paid divergence неподтверждённой, но не даёт права удалить настоящую смерть из сравнения. При этом защита от forged/empty settlement обязательна.

Результаты пары:
- `TERMINAL_COMPARABLE`, отдельный `bothGoalSucceeded`;
- `COMPARABLE_WITH_TIMEOUT` (если выбран отдельный статус, включить в разумный denominator и отчёт censored);
- `NOT_COMPARABLE_<reason>` — отсутствующая/несогласованная native evidence.

Не использовать старое `MATCHED_COMPLETED` без namespace/version: исторически означало *оба выполнили цель*. Везде где отображается старое поле, подписать `legacy both-goal-success definition`.

## Метрики и ограничение интерпретации

`winDifference`: +1 learned-only, -1 stock-only, 0 одинаковый outcome. `stockVictory` и `learnedVictory` должны быть подтверждены в игровом временном окне. Для TIMEOUT различать цель *не достигнута за окно* и итоговую неведомую судьбу; для no-credit actor deaths и unknown EXP/SP исключать экономические дельты.

Раздельный `killTimeDelta` считать только по двум подтверждённым kills. `terminalDurationDelta` можно измерить и при смерти, но это **не время победы**, и нельзя просто считать «дольше умирал = лучше».

Список итогов в отчёте: n matched terminal, n both wins, n learned-only wins, n stock-only wins, n neither wins, n timeout, n invalid, n no-paid-divergence; отдельно Solo, Party/HEAL. Критические safety regressions важнее улучшения скорости убийства моба.

Ни один отдельный поединок или четыре пары не дают основания `ADVANTAGE_SUPPORTED` при отсутствии достаточной независимой статистики. Отдельно low confidence and OOD.
