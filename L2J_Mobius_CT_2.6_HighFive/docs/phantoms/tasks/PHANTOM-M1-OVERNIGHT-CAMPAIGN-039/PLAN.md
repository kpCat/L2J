# M1 overnight campaign — implementation plan
> Исполнитель: executing-plans inline, без субагентов. Spec:DESIGN.md + COMMIT_BOUNDARY.md.
> Goal: повторяемая native жизнь, полное сохранение и максимально полный серверный M1.
> Architecture: текущий038 loop, existing native/background executor и bounded observers.
> Tech stack: Java25, Ant, MariaDB, existing PowerShell/Python tools.

## Review focus
1) Busy branch проглатывает настоящее progress — тестA02.
2) Тот же objectID, другой spawn/epoch — A04/A05.
3) LateSQL не соответствует моменту saved receipt — P01–P07.
4) Death/away выдаётся за исчезновение без сохранения — L01–L04.
5) Диагностический observer сам меняет расписание/нагрузку — P05 и runtime no-breakpoint.

### 0. Вход, 0–30мин
- [ ] Читать только текущий пакет,038 handoff/index и затронутую карту; не все38задач.
- [ ] Проверить MANIFEST/BASE/remote/OWN, raw038 archive receipt; code/source graph сохранён.
- [ ] Импортировать только packet files MAIN→OWN. Four old038 clones read-only/export.
- [ ] Сохранить старт wallclock+monotonic,budget/status в CHECKPOINT039.md; состояние после
      compression восстанавливать отсюда и literal SHA, не из памяти о J1/J2.
- [ ] Одна таблица B16 actor outcomes + parity классы, не16 отдельных задач.
- [ ] Проверить reusable wrappers038 DB/premain/cwd без JVM dry-run, чтобы не тратить запуск
      на старую authority. TEST guard добавить для039t в существующем environment.

### 1. Два причинных прохода, 30–120мин
- [ ] A: B437 first useful failure, B515/576 longgap; current healthy controlB368/C181.
      Исходные snapshots не доказывают причину — сопоставить поля/флаги/native tickets/target.
- [ ] Native fixture с тем же классом возможностей и реальными casts/target motion;
      проверить ранний progress accounting и queued/actual cast состояние.
- [ ] P: один доказанный legitimate successor и один настоящий/missing native parity
      пример; RED старого lateSQL assumption, negative mutation не принимается.
- [ ] Первый native runtime proof или semantic RED до60мин, если verified input готов.
      Нет смысла стартовать неподготовленный сервер только ради таймера.
- [ ] Исправлять выявленный producer, а не его последний симптом. Root class за пределами
      обычного visibility допустим только в conditional SOURCE_MAP, не в Player/ThreadPool.

### 2. Реализация до6часов, с промежуточной отдачей
- [ ] Для каждого fix: raw fact→hypothesis→valid RED→minimal code→GREEN→negatives.
- [ ] Закрыть путь до следующего actual damage/reward либо законного recovery, а не до
      autoPlay=true. Общая цель остаётсяFARM; unreachable конкретныйmob не вечная цель.
- [ ] Conservation — единый алгоритм для всех current lifetimes: actual commit snapshot,
      lineage и read-only stoppedSQL. Не подделывать 1000 отсутствующих old receipts.
- [ ] Проверить native direct/indirect, две геометрии, targetdead/respawn, noProgress,
      sameepochreplan, checkpointrevocation; reuse tests038/037/036/035.
- [ ] После подтверждённого блока публиковать exact source checkpoint. Clean committed
      candidate build; для development tests допускается маркированныйdevelopment,
      но final — строгоJar-first с единой provenance.
- [ ] При failed первой пары вернуться к A/P в оставшемся бюджете; предыдущий freeze —
      checkpoint, а не запрещение продолжения. Нельзя переносить его PASS на changed Jar.

### 3. Natural candidate gates
- [ ] Предварительно зафиксировать local geometry/calendar/enrollment,6–8 alive actors,
      дваprimary (из разных подходящих возможностей), без использования будущих PASS.
      Dead-at-baseline отклоняет целую подготовку stable-scene до measurement, не удаляет ID.
- [ ] Две независимые natural сцены по360–420с на одном frozen source/Jar/config/catalog.
      Поведение групп не управляется целями/HP/MP из observer. All IDs и epoch faults сохранять.
- [ ] Native observer→soft away, background productive commit, same-profile return:
      использовать existing LocalPlay path; план фазы и deadlines описать доSTART.
- [ ] Genuine death+native resurrection/reentry→полезное действие; отдельный gate.
      Если natural death не случилась, контролируемый native TEST death не выдавать за
      случайную natural death, но допускается как воспроизводимая lifecycle-проверка.
- [ ] Каждое завершение current runtime: FULL_SAVE и global COMPLETE/retained0 доpoolstop.
      Missing proofGAP нельзя трактовать как healthy full-save.

### 4. SameDB и аварийное восстановление
- [ ] Только после рабочего gameplay/conservation и достаточного remaining budget.
- [ ] Два actual sameDB restart, не импорт свежего dump; проверить state/items/context/
      goal/owned receipts до/после, legitimate successor только через доказанную цепочку.
- [ ] Crash72/73 — существующие native fault windows AFTER_OWNED_PREPARE и
      AFTER_OWNED_NATIVE_STORE, коды/фактический injector проверить в source.
      Только отдельные own039x72/x73 clones, никаких force чужого процесса.
- [ ] После каждого crash: штатное reconciliation; следующий повторный restart доказывает
      idempotence, отсутствие double rewards, intact items и coherentXYZ. Fault выключен
      для recovery. Процессexit72/73 сам по себе не recoveryPASS.
- [ ] Если runtime/контракт конкретного fault не позволяет безопасный тест —NOT_RUN;
      остальные независимые gates можно завершить, но M1=OPEN.

### 5. Финальная верификация и остановка
- [ ] Последние45мин зарезервированы на остановку/публикацию, не новую гипотезу.
- [ ] На финальномsource/Jar93/93+11/11,native036/Q15/inventory035+action038/debt037 и
      targeted039; failed intermediate routes раскрыть. Нет бесконечного rerun доудачи.
- [ ] RESULT_HANDOFF/MORNING: что видит игрок, сколько фактическихциклов/смертей/возвратов,
      что сохранено; exact unresolved boundary; base/source/Jar/commits/elapsed.
- [ ] Самостоятельное adversarial review по SCENARIOS и full diff; потом normal push,
      local=remote, exact protected hashes и выбранный private archive.
- [ ] OwnJVM0;DB/worktree retained. При настоящем незавершённом штатном shutdown не
      скрывать живойPID, не применять неразрешённыйforce: CLEANUP_BLOCKED.
- [ ] Все обязательные серверные gates → WAITING_REAL_FINAL, а не CLOSED. Не ждать игрока.
