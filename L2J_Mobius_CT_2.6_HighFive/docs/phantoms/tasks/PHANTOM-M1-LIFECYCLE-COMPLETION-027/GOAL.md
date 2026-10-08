# GOAL027

Required base: `882afb37821bdc5d7b8ec4e982e4e1e2c411cbbf`
Branch: `experiment/m1-candidate007-observe008`
Model: GPT-6.1 Sol; Reasoning: Very High; subagents forbidden.

Прочитать TASK/ROOT_CAUSES/DESIGN/PLAN/SOURCE_MAP/ACCEPTANCE. Действовать автономно.

## Результат этой задачи

1. Штатный shutdown действительно ждёт принятый lifecycle до завершения, а не считает
   временную занятость fatal и не отдаёт shared executors на остановку через десятки ms.
2. Обычная native смерть имеет завершение: earned death/quest work → native recovery →
   корректное store/ownership handoff → возобновление жизни, без вечного DEAD/retained.
3. Exact опубликованный ON_ATTACKABLE_KILL проходит очередь и финализируется один раз;
   при реальном сбое остаётся точная причина. «SUBMITTED, start0» недостаточно для диагноза.
4. Сохраняется вся группа; sameDB restart не требует ручного finalize/reset.

Фарм остаётся обязательной наблюдаемой частью этих вертикалей. Однако GREEN027 означает
LIFECYCLE_COMPLETION_PASS, НЕ M1_CLOSED. Не скрывать ещё открытые farm/phase/route gates.

Подробная алгоритмическая граница дана в DESIGN.md: typed PENDING/COMPLETE/FAILED,
monotonic deadline, исполнители и зависимости остаются доступны, новые roots закрыты,
никакого arbitrary ticket.complete, replay заработанного или открытия failed owner.

Пять условных source-направлений в SOURCE_MAP разрешены после собственного RED.
Внутренний новый subreason в разрешённом lifecycle не требует отдельного TASK/вопроса.
Вне этой архитектуры — сохранить BLOCKED_DESIGN, не переписывать половину Mobius.

Первый full-server probe не позднее 90-й минуты; один near-shutdown native delayed
kill-event должен пройти callback→store без прерывания shared pool. Затем native death
и cold DEAD reconstruction. Не тратить первые четыре часа на новые обёртки и документацию.

Срок максимум 360 минут wall-clock. После 270-й минуты — semantic freeze, 90 минут
на итоговую проверку/публикацию. Обычный результат желательно получить раньше.
Ожидания «в игре»/«вышел»/arm/worktree approval отсутствуют. Existing Synthetic only.
