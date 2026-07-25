# ACCEPTANCE — Task 001

## A. Git и scope

- [ ] Git root определён как `C:\Users\endim\L2J_Mobius\`.
- [ ] Изменения только в `L2J_Mobius_CT_2.6_HighFive`.
- [ ] Работа выполнена в `feature/phantom-world`.
- [ ] Parent ветки и actual `origin/master` записаны.
- [ ] Drift от review snapshot записан.
- [ ] Pre-existing user changes не сброшены, не удалены и не закоммичены.
- [ ] Нет force push.
- [ ] Нет изменений других хроник.
- [ ] Нет production `.java`.
- [ ] Нет изменений `build.xml`.
- [ ] Нет runtime config/data/SQL changes.
- [ ] Нет generated JAR/log/build artifacts.

## B. Baseline

- [ ] `BASELINE_MANIFEST.json` валиден.
- [ ] Seed равен `20260725001`.
- [ ] Production DB указана как `l2jmobiush5`.
- [ ] Test DB указана как `l2jmobiush5_phantom_test`.
- [ ] DB connection/mutation flags равны false.
- [ ] Java/Ant/OS зафиксированы.
- [ ] Actual SHAs зафиксированы.
- [ ] `ant -p` target list зафиксирован.
- [ ] `ant jar` exit code и copy behavior зафиксированы.
- [ ] Geodata/pathfinding status описан как observed/unknown, без догадок.
- [ ] Credentials отсутствуют.

## C. Built-in Fake Players

- [ ] Доказана фактическая NPC-based модель.
- [ ] Аудированы config/data/parser/chat/spawn/startup.
- [ ] Реальные и имитируемые механики разделены.
- [ ] Reusable и rejected parts перечислены.
- [ ] Scheduled chat behavior и determinism risks указаны.
- [ ] NPC-based core не принят молча как финальная архитектура.

## D. Headless Player

- [ ] Аудированы constructors/create/load/store/delete.
- [ ] Аудированы client fields/getters/setters.
- [ ] Аудированы packet methods и overloads.
- [ ] Аудированы `ServerPacket.runImpl` side effects.
- [ ] Аудированы client packet business logic paths.
- [ ] Аудированы world enter/leave.
- [ ] Аудированы task creation/cancellation.
- [ ] Null-client matrix заполнена.
- [ ] Offline play/trade использованы как evidence, а не готовое решение.
- [ ] `Disconnection.of(player)` path исследован.
- [ ] Real login/phantom identity collision исследован.

## E. Игровые подсистемы

- [ ] Party.
- [ ] Command channel.
- [ ] Clan/alliance/war.
- [ ] Direct trade.
- [ ] Private stores/manufacture.
- [ ] NPC commerce.
- [ ] Inventory/reservation.
- [ ] Mail.
- [ ] Quests/timers.
- [ ] Instances.
- [ ] PvP/PK/karma/drop.
- [ ] Death/resurrection.
- [ ] Siege/fort/territory war.
- [ ] Raid/epic.
- [ ] Chat/PM/trade chat.
- [ ] Skills/shots/autouse/autoplay.
- [ ] Teleport/navigation/geodata.
- [ ] Global ThreadPool/task managers.

Для каждого есть canonical objects, API, client coupling, persistence, concurrency, cleanup, future seam и test gate.

## F. Архитектурное решение

- [ ] Ровно один verdict: `FEASIBLE`, `FEASIBLE_WITH_SEAM` или `NOT_FEASIBLE_WITHOUT_PLAN_CHANGE`.
- [ ] Verdict поддержан evidence.
- [ ] Minimal seam конкретен.
- [ ] Touch points Task 004 перечислены.
- [ ] Fake GameClient оценён.
- [ ] Nullable client everywhere оценён.
- [ ] Small output/session seam оценён.
- [ ] `PhantomPlayer extends Player` оценён.
- [ ] Fork `Player` оценён.
- [ ] NPC final core оценён.
- [ ] Packet side effects не потеряны.
- [ ] `PhantomActionFacade` boundary описан.
- [ ] Lifecycle state machine описана.
- [ ] Rollback описан.
- [ ] ADR status `Proposed`.

## G. Performance/concurrency/DB

- [ ] Нет per-phantom thread design.
- [ ] Existing per-player tasks перечислены.
- [ ] Cancellation ownership указан.
- [ ] Hot paths и log policy указаны.
- [ ] DB tables/transactions audited статически.
- [ ] Production DB не затронута.
- [ ] Test DB guard подготовлен для Task 002.
- [ ] Anti-dup/partial failure risks описаны.
- [ ] Shutdown/restart ordering описан.

## H. Автоматические проверки

- [ ] `ant jar` выполнен.
- [ ] Verifier выполнен до commit.
- [ ] Verifier выполнен дважды на final commit.
- [ ] Повторные результаты детерминированы.
- [ ] `git diff --check` PASS.
- [ ] Scope guard PASS.
- [ ] Required artifacts PASS.
- [ ] JSON validation PASS.
- [ ] DB safety PASS.
- [ ] Other chronicles guard PASS.
- [ ] Exit codes записаны.

## I. Отчёт и публикация

- [ ] Report соответствует template.
- [ ] Status честный.
- [ ] Deviations перечислены.
- [ ] Limitations/risks перечислены.
- [ ] Commit создан.
- [ ] Commit SHA указан.
- [ ] Parent SHA указан.
- [ ] Push выполнен.
- [ ] Remote ref проверен.
- [ ] Final status clean либо pre-existing dirty state точно объяснён.

## Итоговый review gate

Task 001 может быть принята только если независимое ревью GitHub подтвердит:

- scope;
- отсутствие production changes;
- фактическую полноту аудита;
- достоверность команд;
- обоснованность headless verdict;
- минимальность seam;
- готовность Task 002 и Task 004.
