# Overnight M1 implementation plan

Goal: полная серверная вертикаль, а не isolated5kills.
Architecture: DESIGN.md. Tech: JDK25/Ant/MariaDB, native Mobius; новые зависимости не нужны.
Исполнение Codex inline, Very High, без субагентов. Отдельные commits по завершённым checkpoints.

## Review focus
Поздний OPEN recipient vs stale old epoch; частичная награда до отказа; SELF_DRAIN;
исчезновение цели во время каста; observer heartbeat/identity и реальная cohort.
Покрытие N02/N03/N04/N06/L01/S01..S06 в SCENARIOS.md.

## A — 0..35 минут: автономный вход и последняя версия
- [ ] Verify remote exact SHA, isolated worktree, read AGENTS без main mutations.
- [ ] Скопировать только task-папку, создать runtime-owned manifest/log; save start UTC/deadline.
- [ ] Прочитать accepted022 evidence/patch ledger, не повторять общие аудиты.
- [ ] В новой clone включить synthetic и проверить START/STATUS/STOP без клиента.
      На неизменённом committed source это setup smoke, не final acceptance.
- [ ] Сохранить no-GameClient/LOCALPLAY_TEST_HUMAN/native World identity evidence.
      Если direct synthetic transport не готов — исправить только task runner/allowed
      LocalPlay binding и продолжить, не просить человека войти.

## B — 35..135 минут: cooperative native combat contract
- [ ] Воспроизвести два native attackers одного моба и позднего OPEN получателя (не hate-only).
- [ ] Разобрать dynamic1/7 vs2/7 по конкретным failure stages. При deadlock fixture исправить
      управление TEST gate, а не suppress errors. Ordinary control обязан реально пройти.
- [ ] N02 positive OPEN, N03 SEALED/stale negatives RED; implement DESIGN boundaries.
- [ ] Native timers/cast finalization и end-to-end multi-actor rewards+next targets GREEN.
- [ ] Прогнать TEST022 tests; S12 old unconditional rejection нового OPEN обновить согласно
      DESIGN, а sealed/old-epoch rejection оставить отдельным полноценным тестом.
- [ ] Relevant mixed party/transfer capture controls проходят; M2 party AI не реализовывать.
- [ ] Commit repaired slice, clean build, targeted same-PID native runtime with active NPC AI.

## C — 135..225 минут: обычный lifecycle и bounded decisions
- [ ] Устранить SELF_DRAIN на producer/call-site (не удалять guard).
- [ ] L01-L05: earned writers → store, cancellation, death/revive, lost target, MP/replan.
- [ ] Observer departure → мягкое исчезновение только без наблюдателя → native store→background.
- [ ] Return → rematerialization с неизменной identity и законным earned прогрессом.
- [ ] Exact leak/failed actor handling: first failure retained; нормальный run retained0.
- [ ] Existing M1 dead/recovery/native store crash windows rerun; no reset for GREEN.
- [ ] Если исправления farm повлияли на authority/saving — повторить наиболее близкую
      full vertical с теми же условиями и raw evidence, не micro-unit-only.

## D — 225..270 минут: стабилизация до заморозки исходников
- [ ] Одна сборка кандидата, targeted required regressions; без новых features/AI frameworks.
- [ ] Пробный full-server synthetic episode6min, исправить только подтверждённый relevant
      дефект до freeze, в пределах4rounds. Нельзя автоматически stop на очередном subreason.
- [ ] К270-й минуте freeze committed source/config/geodata hashes.
      Дальше никаких semantic edits. Не успешен core → final truthful BLOCKED, не новая ночь.

## E — 270..330 минут: final server acceptance на одном frozen SHA
- [ ] SceneA и SceneB по360–420s, разные natural primary пары/локальные spawn areas.
      Без новых исправлений между ними. NPC AI enabled, no harness-fed decisions.
- [ ] Отдельный leave/return native movement episode; departure evidence/refcounts/softness.
- [ ] Death/recovery controlled native episode; no direct give EXP/items.
- [ ] Graceful restart на ТЕХ ЖЕ данных, затем bounded crash-restart на отдельной owned copy
      с fault/receipt evidence. Проверить no duplicate earned rewards, recovery actor state.
- [ ] Read-only evaluator tools/verify_cohort.py и raw/SQL corroboration; evaluator не заменяет
      crash/loot/lifecycle проверки.
- [ ] Изменить исходники пришлось → оба final scenes аннулируются и не помечаются PASS.

## F — 330..360 минут: review, safety и утренний пакет
- [ ] Exact scope/encoding/diff review; необходимые failed regressions не скрывать.
- [ ] Synthetic STOP, все task-owned Game/Login graceful stop; no real session changes.
- [ ] GAME_STOPPED отдельно от PHANTOM_DRAIN_COMPLETE. Retained/tickets/timers/identity=0
      для обычного final stop. Crash-case не маскировать под graceful.
- [ ] RESULT/HANDOFF/MORNING.md + final launch script с pinned SHA/сохранённой clone.
- [ ] Normal push и verify remote, один финальный ответ. Без дополнительных receipt commits
      только ради записи собственного SHA. Не переписывать frozen input TASK/ACCEPTANCE.

Ранний успех: закончить сразу после всех gates, не ждать6ч. Неразрешённая safety проблема:
выполнить оставшиеся независимые safe checks, затем сохранить BLOCKED; не ждать пользователя.
