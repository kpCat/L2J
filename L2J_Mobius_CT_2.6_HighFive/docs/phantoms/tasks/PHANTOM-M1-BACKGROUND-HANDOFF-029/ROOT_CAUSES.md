# ROOT_CAUSES029 — установленное отдельно от предположений

## Подтверждено remote source/evidence028
R1. `PhantomNativeContext.Capture` отвергает SUPPORTED при points!=1;
`simulationEligible()` требует COMPLETED+SUPPORTED+points1. Authority nativeContext()
добавляет обычную consume policy. Это намеренная ограниченная V1-модель, а не доказанный
баг native Vitality. Просто удалить predicate небезопасно.
R2. Реальный away633: READY/v11368, points16361, VITALITY_REQUIRES_NATIVE;
759: READY/v3850, points421, POSITION_REQUIRES_NATIVE. В observed absent interval
state version не сдвинулась. Whole post-return3/5:994/1159 route_absent/cycles0.
R3. `combatFacts()` сохраняет УЖЕ СУММАРНЫЕ getExpBonusMultiplier/getSpBonusMultiplier.
При добавлении vitality нельзя умножить эти значения повторно или вычитать vitality
из capped total: независимые policy facts нужны до caps.
R4. `PhantomBackgroundModel.evaluate()` вычисляет rewards внутри encounter-loop;
текущая state/Command не переносит changing vitality. `UPDATE_MAIN` не пишет vitality_points.
Поэтому правка одного eligibility if оставила бы неверные выплаты/расход ресурса.
R5. Фильтр hooks Contract028Observer зависит от текущей selection. Новая cohort
перезаписала selection; terminal PREPARED/FINALIZED872/1272 не захвачены. Это факт
обсервации, НЕ доказательство потери этих native rewards.
R6. Stationary F1 прошла8/8 и V2, и старую строгую проверку; F2 V2=4/4.
Результаты earned cycles в этих сценах настоящие, но не закрывают whole-return3/5.

## Открыто, не выдавать за доказанный root
U1. First restart028 stop:10s/FAILED, retained0, причина первой фазы не экспортирована.
Следующий43ms PASS не объясняет её. Base027 shutdown contract не ослаблять.
U2. Cooperative route11: real late-damage fixture precondition failure; first cause UNKNOWN.
U3. Windows latest telemetry AccessDenied: shared-reader race правдоподобна, не доказана.
U4. Route994/1159: reason известен, не доказана неправильная геодата либо конкретный producer.
U5. Legacy027 EVENT24354 first delayed executor-entry cause остаётся UNKNOWN; не приписывать
ему нынешние отказы и не replay.

## Решение координатора
Не заменять весь behavior controller. Вертикаль029 — operation-specific background
capability + точная передача состояния и полный lifetime proof. Existing farming,
native actions и bounded lifecycle — переиспользовать. Бонусы/position должны быть
входными данными модели, а не специальным разрешением для633/759 либо Келтиров.

## Доказательства candidate C3
- EXACT_REPLY_RED: rollback с потерянным ответом принимался как SUCCESS по старому receipt.
  Исправление commit() сверяет identity и точный operationKey; committed reply-loss даёт
  IDEMPOTENT, unchanged prestate даёт RETRY. EXACT_REPLY_GREEN:4/4, реальная SQL/lease race.
- P05_REGISTER_RED: отрицательный profile создавал обязательство. Теперь positive identity
  и conflicting duplicate проверяются до регистрации; bounded32/128 без eviction.
- P02_TERMINAL_RED: отсутствующий published work sample ошибочно считался ненулевым.
  Повтор с именами штатных counters опроверг эту модель. Terminal sampler вне hooks
  теперь читает actual outstanding()/pendingTimers() после DETACHED+permanent.
  P02_NATIVE_COUNT_GREEN подтверждает temporary SEALED не terminal и отдельную new epoch.
- FIRST_ACTUAL_AWAY_R2 остановлен на NATURAL_COHORT_COUNT0 до отхода; F03 НЕ выполнен.
  R2_EMPTY_CENSUS фиксирует ecology_fenced и незавершённые catchup; root пока не установлен.
- B_R2_STOP: Game/Login graceful exit и отсутствие listeners подтверждены. Login attach
  получил Premature EOF при выходе и оставил native exit1. Это диагностика helper,
  не доказательство first shutdown028; старый U1 остаётся UNKNOWN.
