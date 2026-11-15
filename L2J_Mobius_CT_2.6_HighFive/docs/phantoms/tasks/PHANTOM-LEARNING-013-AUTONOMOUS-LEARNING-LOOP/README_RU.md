# PHANTOM-LEARNING-013 — автономный цикл боевого обучения

**Место распаковки:** `<checkout>/L2J_Mobius_CT_2.6_HighFive/docs/`. Внутри ZIP уже есть `phantoms/tasks/PHANTOM-LEARNING-013-AUTONOMOUS-LEARNING-LOOP/`.

**GitHub:** https://github.com/kpCat/L2J; единственная dev-ветка `feature/phantom-world`.
**База на момент выдачи:** `52aac9e0274a63906a9fe5053611b8daf1aac778`. Перед работой проверять свежий HEAD.
**Счётчик:** задание №7/13, после выдачи остаётся 6. Возобновления/суффиксы тоже входят в бюджет.

**Суть:** вывести уже созданные в 009–012 инструменты в один работающий цикл `COLLECT → FIT → V2 EVAL → CHECKPOINT → NEXT / PAUSE`, со строго раздельными понятиями «обучение произведено», «новые веса выбраны», «поведение отличается», «есть доказанное игровое преимущество». Прогресс не гарантируется; тренер должен уметь честно остановиться без обмана владельца.

**Порядок чтения:** TASK.md → PRE_AUDIT.md → ARCHITECTURE.md → LEARNING_AND_EVAL.md → AUTO_STOP.md → PLAN.md → TEST_MATRIX.md → ACCEPTANCE.md → GIT_POLICY.md → HANDOFF.md.

**НЕ входит:** PvP/self-play, новая нейросеть или RL-алгоритм, полноценный Team Coordinator, рейды, релиз в MAIN/PLAY и автоматическая активация модели на реальном сервере. Они остаются roadmap. Не путать учебный V3 в TEST с включением на игровом сервере.

**Результат:** русский RESULT + EVIDENCE + COMMANDS, отчёт, независимая проверка ветки GitHub. Время native, GPU/CPU, RAM и остаток диска — измерять, не угадывать.
