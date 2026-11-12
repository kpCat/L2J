# PHANTOM-LEARNING-005 — начни отсюда

**Задача не о новых мегабайтах и не о PvP.** Нужно впервые честно сравнить **реальное поведение** уже принятых нейросетей со штатной тактикой в одинаковых по исходным условиям Solo/Party PvE-сценах. Никакого заранее установленного победителя.

## Зафиксированная база
- GitHub: `https://github.com/kpCat/L2J`.
- Исходный `wip/phantom-learning-004` **ровно** `dff800f4b95232c132b374f8ec97c932eda4a6b1` (проверено независимым чтением GitHub перед подготовкой пакета).
- Новый independent clone и НОВАЯ ветка `wip/phantom-learning-005`, никаких изменений в пользовательском `D:\Codex\repos\L2J-phantom-world` (строго READ-ONLY), исторических клонах/БД и защищённых ветках.
- Распаковать архив **внутрь `L2J_Mobius_CT_2.6_HighFive`** так, чтобы файлы оказались в `docs/phantoms/tasks/PHANTOM-LEARNING-005/`.
- Ветки 003/004 и `feature/phantom-world`, TASK130 не сливать/не rebase/не reset.

## Что реально уже есть
- 004: один 4,52 МиБ scorer V3, работающий с DAMAGE и HEAL по отдельности **в границах штатных владельцев Combat и Party**; 509 накопленных native событий; durable 26 PvE-эпизодов; optimizer с 769 updates, STOP/RESUME, replay, 14 COMPLETED в двух полных сегментах.
- Рабочая **принятая** TEST V3 модель №2: SHA `6b91706463f4e24f89f1c4f8be3a82d9ee58eadb07a4254c79449665e3fa97dc`, обучена прежде всего HEAL. Ее преимущество в полном бою НЕ доказано.
- Принятая V1 DAMAGE модель №1: SHA `e0691f728847cebea7293d87e45acebbd3db5bc2d71b76c7c5e40549e13c43d2`, реальная переупорядоченная атака 1230↔1339. Это **другая** модель/контракт.
- Рабочий V3 checkpoint №8 (`ab62799a566c4cf52807978a12bea023be864d58915b3a72beaf7934d6a96016`) — **DRAFT**. Он провалил gate DAMAGE/Party и не имеет достаточного Solo TUNING. **Не разрешать его ON, не переименовывать в ACCEPTED.**
- Текущий 004 `TacticalEpisodeV3` сохраняет реальные end-state, kill/EXP/SP, но **`terminalRewardUsedForTraining=false`**. Это задача **о проверке исходов**, а не о скрытой замене функции награды.

## Порядок чтения
1. `TASK.md`, `EXPERIMENT_PROTOCOL.md`, `METRICS_AND_DECISION.md`, `ACCEPTANCE.md`, `SAFETY.md`, `WORKFLOW_FAST.md`.
2. `SOURCE_MAP.md`, `TEST_CASES.md`, `RUNBOOK_RU.md`, `CONTEXT.md` и остальное из пакета.
3. По фактическому пути source root: `Agents.md`, `docs/phantoms/TASK_PACKAGE_STANDARD.md`, `CODEX_WORKFLOW_CONTRACT.md`, релевантные разделы master plan, `PHANTOM-LEARNING-004` REPORT/RESULT/HANDOFF, затем код. Если старые AGENTS содержат исторические локальные пути, приоритет имеют **текущие явно заданные пути, SHA, безопасность и scope**.

**Главный принцип:** `FULL005_EVALUATOR` означает достоверно реализованный и запущенный механизм сравнения, а НЕ обязательное превосходство обученной модели. Выводы: `ADVANTAGE_SUPPORTED`, `NO_ADVANTAGE_OBSERVED`, `REGRESSION_OBSERVED`, `INCONCLUSIVE` или `NO_POLICY_DIVERGENCE` — всегда отдельно и с размером выборки. Никакой подгонки результатов.
