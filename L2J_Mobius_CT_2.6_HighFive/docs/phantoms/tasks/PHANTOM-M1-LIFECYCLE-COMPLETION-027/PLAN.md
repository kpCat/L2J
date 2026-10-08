# Implementation plan TASK027

Execution: inline Codex, без субагентов. Spec: DESIGN.md.
Review focus: shutdown-before-callback; monitor cycle; queued-but-not-counted recovery;
stale epoch; repeated death return/duplicate store. Каждому есть S/D сценарий.

## 0. Read-first / reproduce (0–35min)
- [ ] Exact base/branch/worktree; read RESULT026 и указанные native shutdown lines.
- [ ] Зафиксировать первый blocked prerequisite in composed S01. Проверить, что не
      testing-only failure flag. Не читать всю историю023..026 заново.
- [ ] Три command-ledger категории: read / own TEST / own runtime. Не строить новый audit engine.
- [ ] Подготовить own lane TEST a и clone b исходного026c для short probe.

## 1. Stop handshake (35–90min)
- [ ] RED S01/S02, paired fatal S04/S05.
- [ ] Реализовать typed attempt в PhantomSystem, existing bounded external wait.
- [ ] Проверить exact native callbacks + terminal store + topology POST_STORE.
- [ ] Clean committed build и первый short full-server shutdown probe <=90min.
      Если setup задержал — записать причину, НЕ выдавать test-only GREEN.

## 2. Death/control completion (90–190min)
- [ ] Восстановить per-profile lifecycle причины dead/absent исходных026 IDs, из registry,
      presence/locality/capacity и durable state. Отделить штатный offline от дефекта.
- [ ] D01..D06 на native fixtures; исправить только доказанные admission/continuation/ordering.
- [ ] Убрать blocking cleanup из собственных event/scheduler contexts, используя existing control.
- [ ] Заранее включён native return→local plan handoff L01. Не STOP на каждом gate этой цепочки.
- [ ] Если callback всё ещё late: снять EXECUTOR_ENTERED/queue/owner wait evidence до pool shutdown,
      затем фиксировать доказанный adapter defect. Нет evidence — честный remaining UNKNOWN.

## 3. Runtime вертикали (190–270min)
- [ ] Новый committed build. Natural death→return→3+ новых cycles; soft away/return отдельная
      session в пределах TTL; original IDs не заменять после baseline.
- [ ] Точное сохранение всех enrolled actors и два sameDB restarts.
- [ ] Если benign marker READY заменяет abandoned MATERIALIZED — отдельный допустимый state
      transition, не native byte mismatch; старый026 FAIL не переписывать.
- [ ] Внутри времени можно чинить следующие связанные ошибки этих контрактов с RED.
      Нет механического лимита rounds. 270min semantic freeze.

## 4. Frozen acceptance / publication (270–360min)
- [ ] На одном frozen SHA повторить S2 real shutdown и D/L vertical на full GameServer.
- [ ] Две natural farm scenes360–420s на same frozen SHA, evaluator026 unchanged.
      Результаты целой группы сохранять, даже если уже понятно, что M1 не будет CLOSED.
- [ ] SEALED→SQL, pending/retained, sameDB restart дважды. Health первого stop отдельно
      от последующего успешного retry и process exit.
- [ ] Actual final-SHA crash AFTER_NATIVE/AFTER_FINALIZE только если persistence/drain уже PASS
      и остаётся время; иначе NOT_RUN, M1 не поднять до WAITING_FINAL_CLIENT.
- [ ] Freeze diff/hash/build, 18 regression routes026 из существующего verified matrix.
- [ ] Остановить свои JVM, exact-path commit/push, один RESULT/HANDOFF/MORNING.
      Последние30min неприкосновенны. Никакой automatic TASK028.

Чистую сцену не переснимать на тех же bytes «для удачи». Новый probe требует новой
диагностической гипотезы или семантической поправки, failed evidence остаётся immutable.
