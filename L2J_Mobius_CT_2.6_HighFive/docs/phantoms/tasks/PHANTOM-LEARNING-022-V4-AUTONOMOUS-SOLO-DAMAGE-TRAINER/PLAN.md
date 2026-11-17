# План исполнения Goal022

Source of truth: TASK, CONTEXT, SAFETY, ARCHITECTURE, ACCEPTANCE и TEST_MATRIX этого пакета. Исполнение в текущем чате; финальный owner gate REQUIRED.

## Границы

- Один TEST controller, штатные owner/process guards и native020/021; без второго AI и production scheduler.
- JDK25/Ant, неизменённый V4/1, batch≤256, MAX_EVENTS4096, точные 128 weights при rollover.
- Historical train/V3/019/020/021 и dirty TEST006 — read-only. Новые записи только в fresh private train022 и локальные build/logs.
- До native: offline GREEN и G0. Максимум 2 GS starts, каждый≤8 минут, всего≤16 минут. 480 минут NOT_RUN. Hard stop150 минут от начала Goal.
- Bounded exception: одна learning artifact family; helper, native adapter, controller, router, focused Java/PS tests, config, build target и документы022. Чужой dirt не публикуется.

## Последовательность

- [x] Проверить Git/инструкции/READ_SET; сохранить случайно переключённый checkout и восстановить fast-forward baseline.
- [x] G0: physical parent188, два отдельных cold/replay checks; approved snapshot022 по прямому решению владельца; SQL/source/Geo/owner/ports.
- [x] G5/G6 offline: append-only full-key ledger, import188, atomic HEAD, physical rollover4095→4096→next, killed-writer/replay/tamper/heldout tests.
- [x] G1: отдельный ActionValue022, durable deadline, detached lifetime, pause/resume/stop/report, double Start и actual pre-native Director death/recovery.
- [ ] G2/G3/G4 acceptance PARTIAL: один completed native round,18 TRAIN,188→206/cold/replay0. Второй boot был израсходован до CUT на CPU startup refusal; два success rounds не доказаны. Третий boot запрещён.
- [x] G7/G8: реальные метрики, ограничения, OFF/process0/ports free и historical preservation;480min NOT_RUN.
- [ ] G9: targeted compile/Java/PS/regressions, encoding checks, scope guard, отчёт≤180 строк, scoped commit и ordinary feature push, independent GitHub readback.

## Риски проверки

1. Неполный native complete не получает credit; paid-before-complete после сбоя UNKNOWN.
2. Checkpoint-before-HEAD завершается offline по тем же sealed bytes; second credit запрещён.
3. Полный внешний ledger сохраняет event fingerprints и episode splits через rollover.
4. Resume не продлевает deadline; stale/reused PID не считается own process.
5. CPU/память/диск/no-data/clock skew останавливают новые native эпизоды.

## Recovery Git

Владелец отдельно разрешил исправить случайное переключение. Два stash сохранены. До bounded восстановления 487 пересекающихся файлов и индекс скопированы вне checkout с SHA-256. Прежний HEAD7e997636 восстановлен mixed reset без изменения файлов, затем точные46 backed-up paths восстановлены и441 backed-up collision files освобождены для обычного fast-forward к d952a73b. Backup-манифест остаётся локальным; private пути в publication не включаются.
