# PHANTOM-M1-LIVING-CONTINUITY-028

Следующий исполняемый пакет после GREEN027 / LIFECYCLE_COMPLETION_PASS.
Required base: `9aeb4ac6c52970372f97637d26a5eb54c760ed1a`. Ветка: `experiment/m1-candidate007-observe008`.
Содержит только task-файлы; распаковка не заменяет production.

Цель: полноценная непрерывная жизнь наблюдаемой группы, включая bounded rest/replan,
нативный уход наблюдателя → сохранение/background → возвращение → повторный фарм.
Не создавать новый combat engine/второго хозяина действий. Использовать существующие
DecisionEngine, BackgroundDecision, VisibleFarmTravel, VisibleAutoPlay и lifecycle027.

Порядок чтения: GOAL → TASK → ROOT_CAUSES → DESIGN → ACCEPTANCE_CHANGE → PLAN.
SOURCE_MAP, PATCH_GUIDE, STATE_MACHINE, SCENARIOS и RUNBOOK — обязательные контракты.

Модель: GPT-6.1 Sol. Reasoning: Very High. Новый Codex-диалог, без субагентов.
Автономно до 360 минут, без ручного login/arm/повторного согласования.

proposals/mailbox_audit.py — готовый read-only анализатор одного mailbox request.
Он не посылает запросы, не пишет в mailbox и никогда не разрешает replay.
proposals/window_budget.py — preflight арифметика существующих TTL/sequence limits.
Проверки этих вспомогательных файлов не являются тестами Mobius и не дают M1_PASS.
