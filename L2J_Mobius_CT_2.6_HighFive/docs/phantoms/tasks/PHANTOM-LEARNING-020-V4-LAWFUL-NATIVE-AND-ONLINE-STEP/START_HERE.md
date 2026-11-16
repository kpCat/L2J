# PHANTOM-LEARNING-020 — начать здесь

**Одна сквозная задача, не серия микрозадач.** Общение, кодовые пояснения и отчёт на русском. Уровень мышления Codex: **High**. Цель: после PARTIAL019 закрыть реальную цепочку **GameServer native V4 choice → paid action → authenticated V4 feedback → one increment → cold reload**, сохранив обычный боевой ИИ. Не проектировать весь тренер заново.

## Источники
- Репозиторий `https://github.com/kpCat/L2J`, **только** `feature/phantom-world`, независимый проверенный HEAD при постановке: `75567cfe7c6e1c5a215045208c388902b9e6b3cd` (позже Studio может передвинуть branch).
- SOURCE: `D:\Codex\repos\L2J-learning-004`; защищённый MAIN: `D:\Codex\repos\L2J-phantom-world`; TEST с историческим overlay: `D:\Codex\repos\L2J-learning-006`. Модуль `L2J_Mobius_CT_2.6_HighFive`.
- Перед работой прочитать применимые `AGENTS.md`, `docs/phantoms/CODEX_WORKFLOW_CONTRACT.md`, `PHANTOM-LEARNING-019.../RESULT` и `EVIDENCE019.json`; далее `TASK.md`, `NATIVE_SCENE.md`, `TRAINING_DATA.md`, `ACCEPTANCE.md`, `BUDGET.md`, `TEST_MATRIX.md`, `PRESERVATION.md`.
- Базовое состояние 019: **ACCEPTED_PARTIAL**, 247 реальных native actions из 27 episodes, 161 V4 SGD update, SHA модели после обучения `569295cf4d8f4172eee0e8a7e530246534dd1cf24d34ab95e07ded65c6b2f061`, cold PASS. G5 FAIL: 1 GS, `V4_NO_LAWFUL_SOLO_OPENING`, V4 consulted 0, paid 0. Историю не переделывать.

## Немедленное правило
В первую очередь исправить **отсутствие законной сцены**, а не строить новый training framework. Проверить возможность получить real native V4 evidence, прежде чем расширять автоматизацию. Не требовать ещё одного формального approval при точном выполнении этого пакета. Не делать несанкционированных расширений архитектуры или бюджета.
