# Исполнение PLAN010

Итог: CONSOLIDATION=VERIFIED; EVAL_CLOSURE=PARTIAL010_NATIVE_EVAL_COVERAGE_FAILED. Маршрут ниже выдан владельцем; исходный NOT_STARTED относится к моменту выдачи. Новая WIP/clone/worktree не создавались.

# PLAN010 — исходный утверждённый маршрут

Статус при выдаче: `NOT_STARTED`. Никакой локальный Git-аудит в рамках 010 ещё не выполнен. Эти значения — стартовые удалённые SHA, их нужно перепроверить.

## Точка безопасности / решение владельца

Владелец **явно разрешает**:

- Сбор read-only локального инвентаря и неразрушающие SHA backups уникальных файлов вне checkout.
- Один merge `wip/phantom-learning-009` в актуальную `feature/phantom-world` с обоими родителями, без squash/rebase/force.
- Обычные scoped commit/push только в `feature/phantom-world`, с независимым remote readback.
- Минимальные исправления EVAL/admission и owner-controlled stop при успешном merge, до 3 новых GS starts / 45 native минут. Старый счётчик009 = 10/10, 96.720667/90, не сбрасывать.

**Не разрешены:** удаление веток/worktrees/stashes, массовый clean, reset --hard, force push, изменения MAIN/PLAY, promotion DRAFT14, новые большие clones, новая модель/долгое обучение, экспериментальные изменения game mechanics, переписывание SQL/optimizer history.

### Stage A (обязателен)

- [x] Проверить actual Git roots и refs на месте; remote HEAD SHA и merge-base свежие.
- [x] Проинвентаризировать все Git worktree, локальные ветки и stash, uncommitted/untracked; manifest SHA уникальной работы.
- [x] Сверить все remote WIP ancestry с009, определить локальные уникальные коммиты/файлы, дать `LOCAL-AUDIT.md`.
- [x] Найти чистый безопасный checkout или STOP с подробным blocker.
- [x] Merge без squash/force; создать `MERGE.md` с двумя parent SHAs, comparison paths и сохранностью cleanup docs.
- [x] Debug/targeted tests/JAR/source/config safety, `git diff --check`, mojibake/escaped Cyrillic.
- [x] Обычный push feature + независимый HEAD/parents/tree/readback. Записать PASS_A до B.
- [x] Проверить что основная локальная папка не была молча перезаписана и что следующие задачи могут прямо работать в feature.

### Stage B (только после PASS_A)

- [x] По immutable F EVAL проследить 222 planning refusals до конкретных инвариантов; связать с actual game data.
- [x] Минимальный RED→GREEN на общий legal scene preparation без hardcoded class/skill.
- [x] Headless admission для frozen unseen EVAL scenes до первого GS, fail-closed при невозможности.
- [x] Исправить owner STOP accounting/confirmation, точные метки native-exit, unknown≠success.
- [ ] В рамках 3 starts/45min провести actual EVAL ≥2 matched pairs суммарно по поддерживаемым Solo и Party; честный split/negative controls.
- [x] Cold model/Adam/applications before-after, сборка/регрессии/cpu/ram/disk/resource.
- [x] Final postflight `STOPPED`, owned PID=0, ports free, flags OFF, private SQL preserved.
- [x] Scoped commits directly feature, push/readback; заполнить EVIDENCE, COMMANDS, ACCEPTANCE, RESULT/HANDOFF.

Не начинать B, если A не прошёл. Не выдавать найденные проблемы за исправленные. Нет обязательного нового fit и auto-promotion.
