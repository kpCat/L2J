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

## Адресный аудит C/D

C: прочитаны MaterializationService/MaterializedPlayer, Retention/Reconcile/Scheduler, Locality, Settings/config, Population wiring и ScaleEnvelope consumers; аналоги065 native pressure/epoch guards и pure scheduler. Soft target отделён от physical target+burst; тот же permit удерживает loading/failed owner. Idle retirement использует existing ready queue и native seal, current human recheck не берёт admission monitors других actors. Burst0 сохраняет065 strict policy. Native C на committed8a5e9e73a, JAR EE63F05653D0E10251CFED83159BA95300677D309B2EC7C36080DF69CBD9D7F2; результат ожидается.

D read-first: прочитаны CommerceService/Receipt/ReceiptStore/Backend, SupplyIntentStore/SupplyJourney proposal/advance/navigation/terminal, EconomyConflictPort/ReservationService, stock RequestSellItem и warehouse deposit/withdraw packets, PlayerWarehouse/ItemContainer.transferItem, CrystallizationService, RecipeData/RecipeList, NativeEquipmentPolicy, MarketProducer surplus и PricingAuthority. Дополнительные адресные Player Adena/store и NativeWorkScope checkpoint нужны для writer audit.

Writer audit до D mutation: World rewards/pickup/party shares и автоиспользование принадлежат native work; autosave/store — checkpoint; Background — identity lease; market/trade/craft — economy reservations. Один ActionLease не сериализует этих writers. Новый maintenance writer должен сначала yield текущий combat/loot, затем закрыть собственный ActionLease, получить существующий native checkpoint и economy claim; receipt PREPARED/COMMITTING/COMMITTED проверяет точные conservation facts. Pending/inconsistent не компенсировать автоматически. Native inventory/warehouse flush предшествует подтверждению receipt. Нельзя drain из собственного native context.

D намечен в том же SupplyJourney: bounded maintenance child в существующем Goal codec с root/class/epoch/field binding, source operations≤3, cooldown/low-high watermarks, существующие navigation и return. Recipe→product index один на процесс, highest grade/неоднозначность защищают ingredients; неизвестное, jewelry/rare, equipped/enchant/augmentation, active needs/reservations сохраняются. Warehouse stock проверяется до новой покупки. Stock AllowRefund=True не отключать; SELL должен использовать native transfer в Refund, затем штатную Adena. Warehouse fee30 за один object line, transfer/merge/capacity штатные; CrystallizationService inspect/crystallize с настоящим skill/yield. Всё это пока план, D implementation/RED/native results ещё NOT_RUN.

Все полные логи/source/build/native evidence: `.phantom-local/task066/`. PLAN/HANDOFF хранят текущий шаг, blockers, exact commands/exit и own PID; acceptance не подменять старым evidence.
Пока непроверено: environment versions/ports/resources, native results, полный writer set хозяйственных операций, final delivery/publication. Общий `ant verify` запрещён TASK066.
