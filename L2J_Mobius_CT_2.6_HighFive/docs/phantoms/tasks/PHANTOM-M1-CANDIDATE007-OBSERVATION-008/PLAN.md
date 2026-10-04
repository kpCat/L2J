# План исполнения008

> Исполняет один Codex inline. Делегирование и общий архитектурный review запрещены
> прямым scope этой задачи; независимую оценку результата выполняет координатор.

**Цель:** реальный клиентский эпизод на frozen candidate007, а не engineering GREEN.
**Архитектура:** existing server + existing Pilot + existing UI transport, disposable DB.
**Стек:** Windows11, JDK25, Ant, MariaDB127.0.0.1:3308, PowerShell.
**Спецификация:** TASK.md / DESIGN.md / RUNBOOK.md / ACCEPTANCE.md.

## Global constraints

Production/AI edits0; subagents0; full suites0. PLAY чтение. TEST не использовать.
Не исправлять отдельный historical blocker. Git только exact-path/normal push.

## Последовательность и ближайший результат

- [ ] До8 минут: required HEAD, candidate manifest, локальные процессы/портовые владельцы,
  private configs/UI tool. Записать короткий PRECHECK; не перечитывать14 часов истории.
- [ ] Примерно до25 минут: новый worktree/experimental branch,103 source snapshots,
  byte verification, snapshot commit, чистая сборка и hashes JAR. Ошибка Java — STOP.
- [ ] Примерно до40 минут: isolated DB/runtime, копирование данных без их исправления,
  loopback config и cap8, graceful port handoff если нужен, старт candidate.
- [ ] Не позднее55 минуты: TestAdmin REAL_LOGIN, fresh arm, первое реальное изображение
  плюс server snapshot. Если этого нет — BLOCKED и переход к cleanup.
- [ ] Один12-минутный эпизод (допустимо10–15): фиксированный cohort, самостоятельные
  farm cycles, один уход/возврат REAL до лимита envelope. Никаких bot commands.
- [ ] На 70-й минуте не позже: OFF/no active run, остановка observation runtime,
  возвращение прежнего PLAY runtime если останавливался, проверка files/DB targets.
- [ ] До90 минуты: RESULT/HANDOFF, evidence hashes, exact-path publication и STOP.

## Review focus — проверяется без нового общего review

1. Не запущен старый source вместо frozen candidate: source/codeSHA/JAR сверены.
2. Клиент не остался на старом сервере: native REAL_LOGIN связан с observation PID.
3. Native counters не склеены между Player/epoch и не заменены флагами autoPlay.
4. Не скрыты стоящие/FAILED участники: исходный наблюдаемый roster остаётся в отчёте.
5. OFF/stop не означают только отправленную команду; подтверждены server/process facts.
