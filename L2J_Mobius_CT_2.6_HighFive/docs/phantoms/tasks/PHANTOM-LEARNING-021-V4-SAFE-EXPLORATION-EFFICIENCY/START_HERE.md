# PHANTOM-LEARNING-021 — безопасные эксперименты и измеряемая эффективность V4

**Один полноценный Goal021, не серия микрозадач. Мышление Codex: High. Все сообщения на русском.**

Источник: `https://github.com/kpCat/L2J`, только `feature/phantom-world`, проверенный baseline на момент выпуска — `30a7a74dd4f4777c8f8cd2670e12e3b37e2b34f8`. Перед началом сверить настоящий свежий `origin/feature/phantom-world`; параллельная Studio работа сохраняется.

Локальная SOURCE: `D:\Codex\repos\L2J-learning-004`; изолированный TEST: `D:\Codex\repos\L2J-learning-006`; защищённая грязная MAIN: `D:\Codex\repos\L2J-phantom-world` (не изменять). Модуль High Five — `L2J_Mobius_CT_2.6_HighFive`.

**Прочитать по порядку:** `TASK.md`, `EXPERIMENT_POLICY.md`, `EFFICIENCY.md`, `ARCHITECTURE.md`, `COMPATIBILITY.md`, `ACCEPTANCE.md`, `TEST_MATRIX.md`, `BUDGET.md`, `SOURCE_POINTERS.md`, `HANDOFF.md`, действующие `AGENTS.md`/`CODEX_WORKFLOW_CONTRACT.md`.

**Исторический факт:** Goal020 `ACCEPTED_FULL020_REAL_V4_NATIVE_AND_INCREMENT` только в Solo single-step. 18 новых native paid actions, V4 обновления **161→179**, checkpoint SHA `42cfff5c02fd386a31ab1e95d4234a8eb22549a62debd729a3fc9a7a602ef96c`; MODEL_CONSULTED=true, MODEL_REORDERED=false, superiority NOT_EVALUATED. Goal021 не начат. Никаких утверждений о готовом ночном тренере или production V4.

**Главная цель:** в одном коротком законном Solo PvE столкновении модель/тренер в явном TEST TRAIN режиме **действительно исследует альтернативный доступный навык**, фиксирует выбранную вероятность и последующий native результат, обновляет V4 исключительно по подтверждённым результатам, сохраняет новый checkpoint и делает независимую оценку без ложных утверждений о преимуществе.

**ЗАПРЕЩЕНО:** 51/72-эпизодные кампании; многочасовые тесты; отключение legality/MP/Geo/owner/таймингов/сейфти; тайные выдачи HP/MP; переписывание V3/V4 исторических codecs, 020 checkpoint/SQL; автоматически запускать 022; массовые BSoE/Return в PvP; общий production ON.

**Запуск:** новую задачу в новом чате Codex; архив распаковать в корень SOURCE004. Сначала read-only G0 и offline проверки, только потом ≤1 GameServer внутри изолированного TEST. Если причинная цепочка невозможна — честный PARTIAL/STOP и узкий диагноз; FULL не выдавать за green юнит-тесты.
