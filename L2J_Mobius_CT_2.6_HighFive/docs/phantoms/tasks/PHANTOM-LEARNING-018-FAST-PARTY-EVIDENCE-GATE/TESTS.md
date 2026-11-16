# Только нужные проверки, никаких часовых прогонов

## Без GS
1. Фактическая причинная таблица class94 по уже доступным evidence: before/current present, owner epoch, overflow, firstUnprovenReason, firstUnprovenNanos, phase/deadline, exp/sp counters. Где нет поля — `UNKNOWN`, а не предположение.
2. Если на разрешённом TEST пути обнаружен конкретный дефект, тест RED (не compile error), минимальный фикс, GREEN на том же случае. Сопоставить одного проблемного и одного нормального Party actor без двойной выборки/открытия второго owner.
3. Негативы: unknown earnings остаётся reject; реальное zero reward допустимо только если оно было явно native подтверждено; stale epoch/owner/after-terminal/timing/duplicate всё ещё reject; данные016 не мутируют.
4. `ant phantom-learning016-test` (и только один ближайший тест при реальной причине); `jar` при кодовых правках по безопасной source-сборке, не над защищённой поставкой.

## GS — строго факультативно, один smoke
Только с существующим bounded native entrypoint и доказанным owner/safety. Не заставлять planner собрать три cells/51 slots для этого теста. Не использовать исходные freeze/016 на запись. Нужны one Party native encounter и сериализованный `terminalEvidenceObserver017`: фактическая первая точка invalidation; если заработало — хотя бы один native Party terminal receipt. Если native законно отказал, это информативный отказ, но не PASS.

## Положительный итог
`ROOT_CAUSE_PROVEN` = точный первый причинный native reason с гарантией, что наблюдение не мутировало actor; `PARTY_REWARD_PROVEN` дополнительно требует реального, подтверждённого EXP/SP и terminal reward. Не считать synthetic fixtures или Solo эпизоды доказательством Party.

## При отказе
Выписать «какие конкретные поля/вызовы отсутствуют», «что уже попробовано», «что делать дальше»; не тратить ещё 20 минут на теоретическое перечисление вариантов.
