# ACCEPTANCE039 — конечная серверная граница

Правила фиксируются ДО новой серии. Старые038 результаты и исходник CONTINUITY_V2
сохраняются. Средний процент по gates не является готовностью M1.

| Gate | Обязательное доказательство |
|---|---|
|G0 INPUT|Exact BASE/remote/OWN, проверенные inputs038, protected snapshots, отсутствие чужих изменений.|
|G1 ACTION|Реальный обход → native damage → минимум5 завершённых native cycles у melee и caster; native loot, где применим; отрицательные проверки stale/instance/busy; код без actor/zone hardcode.|
|G2 GROUP|Две независимые natural группы6–8: все enrolled проходят неизменённый CONTINUITY_V2 на финальном кандидате. Два primary имеют ≥5 cycles, остальные ≥2; действуют все исходные проверки counters, хвоста наград, отдыха и простоя.|
|G3 SAVE|Все текущие births учтены. Exact PREPARE/native/FINALIZE; skills, full items, state и PNC сверены на своей commit-границе; последующие версии объяснены. Global COMPLETE/retained0 подтверждены до остановки пулов.|
|G4 LIFE|Native смерть → законное восстановление → полезное действие. Отдельно soft human away → настоящий продуктивный background transition → тот же profile возвращается в native farm. Нет потерянных эпох или повторных наград.|
|G5 RESTART|Два actual sameDB restart текущего кандидата. Полная цепочка state/context/items/goal и отсутствие повторного начисления.|
|G6 CRASH|Оба существующих crash-окна на текущем кандидате. Recovery и второй restart для idempotence каждого; согласованные native XYZ, earned work и store.|
|G7 REGRESS|Текущие93/93 и11/11, native0369, Q15, inventory0356, action0385, debt0372, затронутые и новые native tests. Jar-first, единая provenance.|
|G8 DELIVERY|Независимый self-review полного diff, exact commit и normal push, проверенный архив, own JVM0, сохранены worktree/DB, понятный утренний отчёт.|

Все G0–G8 PASS: TASK_RESULT=M1_SERVER_READY; M1=WAITING_REAL_FINAL; REAL_FINAL=NOT_RUN.
M1=CLOSED запрещён без настоящего connected gate. Ночного ручного входа не требуется.
M2–M7 нельзя закрыть по наличию классов или одному smoke test.

Частичный полезный итог: PARTIAL_VERIFIED и M1=OPEN, с матрицей каждого gate.
Текущий runtime failure: FAILED_CURRENT_GATE. Внешняя или опасная граница: BLOCKED_INPUT/SAFETY.
Всегда перечислять конкретно работающий код, проверки и оставшуюся границу.

## Что не является PASS

autoPlay=true или завершённые callbacks без результата; один удачный primary вместо
всей группы; новый cohort вместо неудобных failed ID; старый Jar или симулятор вместо
final native; OS0 вместо committed save; v+1 в SQL без producer/hash chain.
Нельзя списывать native mismatch на «фон», выдавать MP, удалять вещи, сбрасывать debt,
пересоздавать epoch или увеличивать ограничения ради результата.

## Разделение сценариев

Stable farm — не полный жизненный цикл. Законная смерть или уход может провалить G2
и одновременно проверить часть G4. Не делать персонажей бессмертными ради G2.
Не ослаблять его договор: сохранить FAIL, выбрать разумные натуральные предпосылки ДО
исхода и исправлять реальные причины. Если all-PASS не получен, G2 остаётся открыт.

Loot бывает native auto-loot или pickup. Если предмет не выпал или stock inventory full,
не выдумывать приобретение. Применимость фиксировать отдельно; доступный native pickup/Sweep
можно дополнительно проверить в fixture.

М1 не требует пройти все локации, реализовать PvP и кланы или восстановить каждый старый
raw038. Универсальность проверяется на геометрических и статусных классах задач, без
исключений для конкретного персонажа. Исторические UNKNOWN не выдаются за исправленные.
