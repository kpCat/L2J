# TASK025 Implementation Plan
> Исполнитель: executing-plans inline, без субагентов. Пользователь выбрал Codex/new chat.
**Goal:** cold pending разрешается и live checkpoint возвращает исполнение, затем farm/save/restart.
**Architecture:** existing ownership + B4 transaction resolver + bounded exact control continuation.
**Tech stack:** High Five Java25, Ant, Windows, MariaDB3308.
**Spec:** DESIGN.md, TASK.md, SOURCE_MAP.tsv.

## Review focus
- Исключение после inventory flush, но до `_intent` assignment — не безопасный no-write.
- FINALIZE прошёл, публикация индекса не прошла — не повторять earned начисление.
- Параллельные смена goal/death/logout/epoch — старый control не оживляет новую эпоху.
- Pending owned store при старом catchup FAILED — recovery не ждёт ordinary baseline.
- Instrumentation, mailbox и worker starvation — наблюдение не создаёт дефект самó.

## 0. Preflight и base, целевой срок0–20мин
- [ ] Verify remote/base/foreign/worktree; создать own detached worktree по TASK.md.
- [ ] Прочитать только RESULT024/FIX_LEDGER, 452 receipt и финальный110 timeline.
- [ ] Скопировать retained contract024c read-only export в own contract025a, hash source.
- [ ] Зафиксировать дефекты RCA confidence; воспроизводимый runtime baseline уже дан,
      не повторять шесть минут исходного cohort ради красивого RED.
- [ ] Reuse task024 tools/test lane; до3 thin wrappers, не создавать десятки apply-*.json.

## 1. Cold resolver R01–R08, целевой срок20–65мин
- [ ] Native fixture: canonical==AFTER, background VERIFY_PENDING, pending receipt,
      no materialized entry, catchup PENDING и вариант FAILED baseline.conflict.
- [ ] RED: production ensureBaseline не достигает существующего resolver.
- [ ] Узкий service recovery entry + приоритет в historical preflight до обычных guards.
- [ ] GREEN before/after/neither/epoch/live owner и repeated restart fixtures.
- [ ] Full server на КОПИИ024c:452 автоматически finalized, idempotent второй restart.
      Если раньше M1-policy мешает, именно recovery routing входит в scope — не STOP.
- [ ] Вывести cohort-independent receipt proof в компактный RESULT_CURRENT.

## 2. Live checkpoint R09–R20, целевой срок40–120мин (часть можно подготовить параллельно по времени, не агентами)
- [ ] Сохранить first exception/stage captureVisibleArrival без изменения семантики.
- [ ] Короткий synthetic probe≤120с нового runtime; снимок110 или первого эквивалентного
      natural owner, без навязывания profile ID. Если зависание раньше, снять timeline.
- [ ] RED на actual producer/phase + тест generic no-write failure trap.
- [ ] Implement typed phase/outcome, one-control-owner, bounded retry; no blanket reopen.
- [ ] Разделить pending receipt, no-write rejection и finalized publication failure.
- [ ] GREEN и работающий completion path до следующего native боя без ручного AutoPlay.start.

## 3. Earned drain + composed closure R21–R28, ориентир120–210мин
- [ ] Original ON_ATTACKABLE_KILL reservation: due/start/locks/queue подтвердить evidence.
- [ ] RED: два одновременных checkpoint + delayed kill reward/quest; true native callback.
- [ ] Использовать quiescence continuation, а не блокировать нужные workers/monitors.
- [ ] Короткий полный server probe: farm→arrival/store→resume→earned stop без retained.
- [ ] Re-run relevant native scope/closure/timer/store/restart/intent suites.
      Их known-negative cases перечислить, новый relevant FAIL не списывать на debt.
- [ ] Exact source commit/push, clean committed build. Нет незакоммиченного live patch.

## 4. Fix/verify loop внутри контракта, до270мин
После нового in-scope first cause делать узкий RED/fix, не просить TASK026.
Не более3 неудачных попыток ОДНОЙ гипотезы без нового evidence. Число разных исправлений
не является искусственным stop condition. Не менять scope по инерции.
К120мин обязателен server probe либо ранний точный safety/outside-design BLOCKED.
Если к240мин остаётся reproducible unsafe store, прекратить новые полные сцены и
сохранить доказательства; не расходовать ещё час на заведомо FAILED acceptance.

## 5. Frozen проверка, зарезервировать90мин
- [ ] До freeze пройти короткий composed probe; нативный бой и saved restart уже работают.
- [ ] Две natural сцены360–420с на одном SHA/config, primaries заранее, ≥4 actor каждая.
- [ ] Проверить tail120 и все actor outcomes, не только aggregate kill count.
- [ ] Из same frozen scene сохранить expected SEALED witnesses ВСЕЙ группы; native stop;
      read SQL после stop ДО background advancement следующего запуска.
- [ ] SameDB restart: bounded early receipt reconciliation snapshot до допуска simulation,
      затем наблюдать новое farm continuation. Проверить вторым restart идемпотентность.
- [ ] Один новый AFTER_NATIVE process crash на отдельной clone и повторный restart;
      допускается второй AFTER_FINALIZE только если первый fully resolved.
- [ ] Death/soft-return: изолированные regression episodes, не переносить неудачный
      receiver/profile в primary natural scene. Время synthetic сессии учитывать заранее.

## 6. Завершение
- [ ] Остановить own JVM штатно; exact emergency only по TASK, не CLEANUP_PASS.
- [ ] Один RESULT с доказанными PASS/FAIL/NOT_RUN. M1=OPEN пока full server+REAL не доказаны.
- [ ] Exact path commit/push own diff. Одно подтверждение remote, без self-SHA receipt loop.
- [ ] MORNING: verified SHA/JAR/config/DB, одна команда запуска, что ещё не проверено.
