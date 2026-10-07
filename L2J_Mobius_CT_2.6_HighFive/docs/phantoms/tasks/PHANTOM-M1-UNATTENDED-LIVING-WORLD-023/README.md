# TASK023 — автономная ночная стабилизация живого мира

Назначение: не ещё одна проверка одного blocker, а исправление и доказательство связки
multi-actor native farm → recovery → dematerialization/store → background → rematerialization.
Исполнитель — Codex в новом чате, GPT-6.1 Sol / Very High, без субагентов.
База: fa65d4f8ae02ebb4e1c103c6e811e9352aac89f6.
Ветка публикации: experiment/m1-candidate007-observe008.

Пользователь спит. Обязательных ручных login/logout/arm/ответов нет.
Использовать существующий LocalPlaySyntheticHumanSession, а не fake REAL_LOGIN.
Нет права изменять PLAY/основной checkout или брать чужие runtime/PID.

Прочитать один раз: TASK.md → READ_FIRST.md → DESIGN.md → PLAN.md → RUNBOOK.md.
Для правок: SOURCE_MAP.tsv, PATCH_GUIDE.md, SCENARIOS.md.
Для результата: ACCEPTANCE.md, HANDOFF.md. Примеры кода: proposals/ и tools/.

Пакет не содержит уже применённого исправления сервера. Java proposal — проверенная
таблица решения допуска, НЕ готовая реализация native reservation. Python verifier —
исполняемый проверяющий агрегированных наблюдений, НЕ замена исходных серверных evidence.
Не выдавать тесты proposal за native/production PASS.

Лучший допустимый итог ночью:
TASK_RESULT=GREEN; SERVER_M1_PASS=true; M1=WAITING_FINAL_CLIENT.
Полное M1_CLOSED не объявлять без отдельной реальной клиентской приёмки.
