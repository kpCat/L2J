# PHANTOM-FARM-ECONOMY-066 — Implementation Plan

**Goal:** выполнить A→B→C→D TASK066 с причинным RED, focused/native GREEN и самостоятельной поставкой; частичную блокировку сообщать честно.
**Architecture:** существующие Player/native-work, AutoPlay, materialization/retention, commerce/economy owners остаются каноническими. Никакого второго engine, отдельного pickup timer или обхода checkpoint/lease.
**Tech Stack:** Java/JDK25, Ant, MariaDB11.4.13, Windows PowerShell5.1; зависимости не менять.
**Spec:** TASK.md, AUTHORITY.md, ACCEPTANCE.md, TEST_CASES.md, RUNTIME_INPUTS.md в этой папке.
**Execution:** одна основная сессия, автономно; review/approval плана не требуется согласно AUTHORITY066.

## Baseline и ограничения

- Начальный local/live remote: `934c19f906aa67349f1fd498eacfbb5a56d9b90f`, `feature/phantom-world`.
- Начальный dirt: только переданный пакет066; чужого source dirt нет.
- До12 часов; после10.5ч новые функции не начинать. Native observation суммарно≤90мин.
- Не запускать/менять release057–065, MAIN/PLAY, Studio, клиента, чужие JVM/БД/worktrees.
- Только две новые066 схемы/lanes; exact path/schema/port/JAR/source/PID guards. Создание вне sandbox — только через platform approval.
- Связанный scope>10 файлов заранее разрешён AUTHORITY; точный список поддерживать в CHANGED_FILES066.txt.
- Input package неизменяем; PACKAGE_SHA256 не обновлять. Секреты/Geo/JAR/raw DB rows — private local.

## Read-first и локальные аналоги

Прочитаны root AGENTS, module Agents, весь пакет066, CURRENT_STATUS, WORKFLOW_CONTRACT, TASK_PACKAGE_STANDARD, EFFICIENCY_STANDARD, relevant bounded master/release/оба roadmap, RESULT065, PhantomSystem shot/retention/observer wiring.
Дополнительные адресные исходники A: Creature attack boundary; PlayerNativeWork.Owner/prepareOffensiveShot; PhantomNativeWorkScope shot methods; PhantomNativeShotControl целиком. Причина: отделить optional charge от native admission без потери accounting fence.
Module README.md, дополнительные AGENTS в docs-цепочке, отдельные code-map/pattern документы не найдены. Чужие исторические отчёты не перечитывать.
Аналоги: native action reservation/earned child; native item handlers; существующие Build-Task065/Assemble065/Runtime065; scoped launcher/suites. Каждый следующий этап читает только свой READ_SET и используемые аналоги.

## Review focus

1. Пустой/несовместимый stock разрешает только законный unboosted бой; stale/drain/checkpoint/accounting failure сохраняют отказ.
2. Pickup/sweep не скрывают отсутствие native damage/kill; native loot protection/party/quest остаются authority.
3. Burst расходуется атомарно; failed retained cleanup не возвращает фиктивный permit; idle cleanup происходит после grace без нового demand.
4. Классификация ingredient берёт старшую recipe product role; неизвестное/редкое/нужное/зарезервированное имущество сохраняется.
5. Продажа/склад/кристаллы используют существующие writers/receipts и native цену/плату/yield; повтор не даёт второй награды.

## Последовательные этапы

- [ ] A: existing test launcher/shot fixtures → RED empty stock + no stock creation; minimal shared native charge/admission fix; supply journey budget/backoff и background shot inputs; focused GREEN и native melee/magic/summon negatives.
  Файлы: combat/PhantomNativeShotControl, player/PhantomNativeWorkScope, при необходимости model/actor/PlayerNativeWork; commerce/L2jPhantomSupplyJourney, background/L2jPhantomBackgroundAuthority и существующие затронутые suites.
- [ ] B: прочитать AutoPlay/AutoUse, PhantomVisibleAutoPlay, CombatService/backend, Player.doPickupItem и native Spoil/Sweep. RED lawful foreign/ranged/multi loot и starvation; один bounded owner/series с native outcome; GREEN/geometry/party/capacity.
- [ ] C: прочитать MaterializationService/Retention/Reconcile/Scheduler/Locality/Ecology/config и всех consumers capacity. RED soft2/burst2 arrival before cleanup + eventual grace cleanup; минимально расширить существующий capacity/retirement contract; GREEN strict0/concurrency/holds + один native grace cycle.
- [ ] D: прочитать Commerce/backend/receipt/reservations, stock warehouse/packet rules, CrystallizationService, RecipeData/equipment policy/market pricing. Writer audit до новой mutation. RED data protection/native money/container idempotency; bounded policy и existing journey integration; GREEN own needs withdraw и safe full/error handling.
- [ ] Delivery: точные066 wrappers по065; две guarded новые схемы, физическая Geo copy/hash, synthetic OFF persisted observation-character. Выбор stock fixtures — сортировка реальных подходящих IDs, primary+один fallback записать до measurement; no spot special cases.
- [ ] Final: one impacted suite + clean committed-source JAR; ordinary S1/S2 и sameDB restart по готовым функциям; штатный STOP всех own JVM; focused self-review; две отдельные encoding проверки; exact diff/scope/input/preservation hashes; отчёт≤180 строк, matrix/handoff/start guide, commit и non-force push с live SHA.

## Evidence и продолжение

Все полные логи/source/build/native evidence: `.phantom-local/task066/`. PLAN/HANDOFF хранят текущий шаг, blockers, exact commands/exit и own PID; acceptance не подменять старым evidence.
Пока непроверено: environment versions/ports/resources, native results, полный writer set хозяйственных операций, final delivery/publication. Общий `ant verify` запрещён TASK066.
