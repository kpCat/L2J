# Design — владение native работой до фактического завершения

## 1. Что сохраняем и что исправляем

Stock Player, World, GeoEngine, AutoPlay/AutoUse, native hit/cast/quest/drop/pickup
остаются исполнителями. b4f PREPARE → immutable snapshot → native canonical store →
FINALIZE → restart reconcile остаётся durable протоколом. Не добавляем второй combat,
reward engine, собственную базу inventory или повторное начисление по «счётчикам успеха».

Исправляем границу владения: synchronous dispatch уже учитывается, а queued/running
native continuations — не полностью. Учитывать работу необходимо ДО публикации в
очередь, а не только при входе в callback. Приёмка измеряет последствия native действий,
а не флаги намерения.

Отвергнутые варианты: sleep(2500), blanket cancel earned callbacks, guard только в
Quest.giveItems, повторный snapshot при mismatch, global Player lock на весь бой,
executor на каждого Phantom, замена native движка, принудительные teleport/reset.

## 2. Один owner и один атомарный критерий тишины

Owner привязан к exact Player instance + objectId + identity lease/lifetime token +
materialization epoch. Не искать «текущего Player с этим objectId» в позднем callback:
это может быть другой materialization lifetime или REAL login.

В native Player добавить опциональный узкий NativeWorkOwner interface. Реализация
на стороне Phantom lifecycle использует **один** work scope. Старые ActionLease
интегрируются с этим scope; отдельно обнуляющиеся action/continuation counters без
общей точки seal запрещены. `admittedActionCount` остаётся корректной диагностикой,
добавляются queued/running/continuation counts и причина отказа.

Предлагаемые компактные helpers:
- `model/actor/PlayerNativeWork.java`: интерфейсы owner/ticket/execution context;
- `phantoms/player/PhantomNativeWorkScope.java`: exact lifetime, tickets, drain/seal;
- `model/events/NativeEventWork.java`: явное определение участников M1 native событий.
Допускается эквивалентное размещение меньшим количеством классов, записанное в source map.
Не переносить core dependences на LocalPlay и не определять Phantom по client==null.

Managed origin на Player сохраняется после detach до GC самого Player. Отсутствующий
owner у managed Player означает запрет stale native work, а не ordinary bypass.
Для ordinary REAL/offline и LOCALPLAY_TEST_HUMAN, не владеемых Phantom, путь остаётся stock.

## 3. Root и child — разные виды допуска

Root — новое самостоятельное действие. Разрешено только OPEN. Оно получает ticket
до первого эффекта. Дочерний ticket резервируется под тем же monitor до scheduling;
закрытие parent и fork(child) линейризуются. У parent нет момента «уже ноль, а child
ещё не посчитали».

**В DRAINING новые roots запрещены, но живой уже принятый parent может породить законное
продолжение.** Предыдущая формула «запретить все новые continuations» неверна: hit,
который уже исполняется, вправе поставить earned kill event даже после начала drain.
Это не reopening admission. Child наследует точный owner; depth/sequence диагностируются.
Синхронная вложенность использует execution context, асинхронная — явный захваченный ticket.
ThreadLocal<Boolean> сам по себе не является ownership; использовать scoped stack,
с точным token и обязательным восстановлением previous context в finally.

Ticket живёт RESERVED/QUEUED → RUNNING → COMPLETED. Завершение ровно один раз.
Queued ordinary cancellable work может перейти CANCELLED_BEFORE_START по CAS.
Для earned callback cleanup-cancel недопустим: он должен исполниться либо оставить
явный retained unresolved outcome. RUNNING release делает только finally callback,
не Future.cancel/isDone, не cleanup и не timeout.

No strong refs на завершённые Player/Throwable в глобальных архивах. Живые tickets
ограничены lifecycle и native task family; добавить hard accounting cap 1024/actor,
не молчаливое dropping. При достижении cap новые roots не стартуют; невозможность
зарегистрировать уже заработанный child — явная protocol failure с retained ownership,
не повод продолжать PREPARE. Cap проверить на native workload и описать измерения.

## 4. EventDispatcher и native scheduler

ON_ATTACKABLE_KILL: ownership резервируется в ветке Attackable.doDie перед вызовом
native scheduling либо непосредственно в EventDispatcher до ThreadPool. Обвязка охватывает
весь listener traversal, не один выбранный ConsumerEventListener. Реальный Quest.onKill,
Quest.giveItems/giveItemRandomly, QuestState mutation проходят внутри этого ticket.
Участники определяются типизированно; нельзя считать NPC container владельцем Player.

Проверить оба overload notifyEventAsync (single/varargs), delayed и вложенные события,
которые реально достижимы из M1. Global/local listener ordering и abort semantics
сохранить. Ошибку listener, которую штатный dispatcher перехватывает, всё равно связать
с ticket/incident: завершение с ошибкой не равно доказанному successful reward.
Никаких повторных запусков listener при partial failure — это потенциальный duplicate.

Особенность этого source: ThreadPool.schedule ловит Exception и возвращает null;
execute глотает Exception; rejection handler при executor.isShutdown() молча return.
Ненулевой ScheduledFuture тоже нельзя без теста считать подтверждением исполнения.

Добавить **строгий путь постановки для owned work**, сохранив API и поведение обычного
игрового кода. Предпочтительный минимальный shape: `scheduleOrThrow/executeOrThrow`
для новых owned callsites и явный RejectedExecutionException при shutdown в handler;
legacy public wrappers продолжают свою прежнюю catch/log/return семантику. Если выбран
другой механизм подтверждения — он обязан закрывать те же interleavings из Q07.
Нельзя менять rejection handler на лету, синхронно вызывать reward «для спасения» или
запускать новый поток на каждый Phantom callback.

Submission/rejection может произойти до возврата API, включая inline execution.
Состояние wrapper/ticket должно быть опубликовано до submit. Rejection/null/throw
освобождает неисполнявшийся ticket accounting, но фиксирует UNSUBMITTED_EARNED_WORK
и запрещает successful handoff. Already-running task не отменяется вторым close.
ShutdownNow после accepted submit тоже не означает drain; first shutdown Phantom
owners, затем выключение executor, либо честный retained/abort процесса без GREEN.

## 5. QuestTimer / TimerHolder: отдельно pending и executing

Pending обычный session timer не должен держать action lease весь час до deadline.
Он зарегистрирован как cancellable scheduled producer; close roots/registration gate
не допускает новый таймер в закрытый lifetime. Start callback и stop pending
линейризуются: либо callback получил execution ticket, либо он никогда не пишет.

Для уже выполняющегося callback cancel(false) НЕ доказательство выхода. Дождаться
execution finally. TimerHolder включает `_postExecutor.onTimerPostExecute`,
`_eventScript.onTimerEvent` и mutating cancel callbacks — не только последнюю строку.
`cancelTimer` и `cancelTask` имеют разную native семантику: не подменять один другим.

QuestTimer сейчас снимает one-shot из списков до notifyEvent. TimerHolder также может
снять запись до script body. Поэтому обход списка Player timers не заменяет учёт
выполняющегося тела. Учесть registration-before-publication и run-before-Future-assignment
при нулевой задержке, повторный cancel и task construction failure.

One-shot continuation, созданное earned callback для выдачи награды, не должно быть
ошибочно классифицировано как ambient timer. Оно наследует earned ticket; не уничтожать
его под видом штатного logout. Обычные долгие/periodic timers сохраняют native cancellation
semantics. Для реально достижимого долгого earned таймера документировать сохранение
обязательства/retention, а не invent silent cancel; не создавать новый quest milestone.

## 6. Checkpoint — не только dematerialization

Тот же барьер нужен у CLEANUP_STORE, ARRIVAL_CAPTURE, OTHER_OWNED_STORE и pending resume.
Дренировать callbacks лишь перед delete недостаточно: beforeStore уже делает inventory
updateDatabase/capture/PREPARE, и callback может пересечь эти операции у видимого Player.

Сначала остановить новые roots; закончить уже принятые транзитивные continuations;
под общим monitor подтвердить zero+owner и перейти в exclusive QUIESCENT checkpoint.
Только после этого inventory.flush → immutable capture → PREPARE/native store/FINALIZE.
Seal остаётся закрытым на всём interval. Нельзя проверить zero, отпустить gate и допустить
нового writer перед PREPARE. Native damage/status/EXP/item writers по reachable M1
путям должны быть классифицированы в ASYNC_PRODUCERS.tsv; неизвестный writer не PASS.

**Не ждать drain из callback/action lease, который сам входит в счётчик.** В частности,
PhantomVisibleFarmTravel.arrive сейчас вызывает resumeVisibleOwnedStore внутри ActionLease.
Не ждать под synchronized(Player.store), actor lifecycle monitor, DB transaction,
manager lock или timer list lock, нужными callback. Разделить request и checkpoint:
root/producer запрашивает save; после выхода из root existing per-profile control lane
выполняет checkpoint. При невозможности сейчас вернуть явный deferred/pending результат,
не «успешно сохранили». Дочерний callback не вызывает store/drain рекурсивно.

Для существующих обязательных синхронных store callers сохранить контракт: разрешение
checkpoint получают до входа в synchronized native writer. Для coalescible autosave-like
callers допустима очередь с completion evidence; нельзя silently skip required store.
Проверить все owned storeMe/store/resume callers, не изменяя ordinary Player stores.

Retry pending intent продолжает **тот же** immutable intent. Successful resume и уже
существующий authorized cleanup recapture допускаются только в том же frozen lifetime;
не переснимать snapshot ради скрытия несовпадения. Guard
OWNED_STORE_RUNTIME_CHANGED_AFTER_FINALIZE сохраняется и обязан ловить негативный TEST.
При DB/FINALIZE failure admission не открывается поверх uncertain pending state.

## 7. Timeout — сохранность И возможность завершить cleanup

Сохранить existing finite drain deadline (default 5000 ms), не лечить баг большим sleep.
По timeout PREPARE не начат, exact owner/Player/World/outbound удержаны; diagnostic содержит
outstanding kind/id/state/age. Counters не обнуляются. Исполняющийся earned callback
может законно закончиться на том же lifetime после возврата retained failure.

Последний completion может поставить **один** control retry через существующий service/
scheduler cleanup path, не вызвать cleanup inline. Авто-retry только для typed
NATIVE_WORK_DRAIN_TIMEOUT, exact same owner/epoch, before-PREPARE и доказанного zero.
Максимум 2 automatic cleanup continuations на incident; ошибки store/protocol/UNKNOWN
не открывают admission и требуют отдельной диагностики. Retry снова проверяет условия.
Это разрешённое узкое расширение scheduler/recovery в scope, не бесконечный healer.

Старые 110/142/175 UNKNOWN не оживлять SQL/снятием fence. После controlled restart они
проходят существующую startup reconciliation; если дефект повторится — новый incident
даст точный producer. Их нельзя удалять из cohort, если они попали в observation set.

## 8. Native pools/session и диагностика

Завершить P/E из006. Missing policy + managed origin всегда fail-closed. Stale tick
P1 не снимает P2 ни в основном path, ни в catch. Session содержит exact policy;
health требует обе registrations, exact Player/goal/revision, не autoPlay flag.
Repair partial pair на том же Player, rollback только собственного owner.

Не держать одновременно AutoPlay/AutoUse monitors; callouts/DB вне manager locks.
Обычный offline SQL generation-check-before-effect недостаточен; либо не менять ordinary
путь, либо сериализовать действительное effect с start, а не только его проверку.

First incident immutable на lifetime, latest обновляется. Primary exception/Error
сохраняется, secondary suppressed. Phase и hook точны. Archive detached bounded;
census не содержит огромные traces и не теряет first при removal. Параметры006:
160 chars message, 8 cause nodes, 32 frames, 8192 bytes detail; overflow/truncation явны.

## 9. Travel, отсутствие вечного idle, вода

Failure disposition задаётся у каждого terminal producer, не boolean whitelist.
ROUTE_UNUSABLE → existing target exclusion/replan; TRANSIENT_SERVICE → cooldown с общим
budget; STORE_PENDING → checkpoint continuation; PROTOCOL_VIOLATION → incident.
Ledger exact profile/lifetime/goal/revision хранит общий deadline/attempts независимо
от удаления Journey. Maximum attempt duration берётся из existing policy (120000 ms
в просмотренном default), не обнуляется новым Journey. Stale terminal не меняет новый goal.

Нужен actual production composition test: history/replan callback именно из composition
PhantomSystem (или извлечённого shared production factory), не копия лямбды в TEST.

`noTargetExpired` сейчас проверяет доступный target, но наличие target не доказывает farm.
Добавить полезный progress watchdog: fresh attributed damage/reward, сокращение native
path, обоснованный regen/resource recovery. Flags/self-heal loop не сбрасывают farm debt.
30 s без полезного или проверенного временного progress → одно reasoned native recovery;
90 s без полезного прогресса/ограниченного recovery → видимый stalled outcome и replan,
не infinite stop/start AutoPlay. У valid long combat учитывать текущий damage/HP changes,
не требовать kill каждую секунду. Ограниченный regen объясняет паузу, но не вечность.

Вода: сначала exact live coordinates + path segments + GeoEngine coverage/height/NSWE +
WaterZone/underwater state. `canMoveToTarget=true` не означает безопасную сухопутную цель.
Для dry-land farm выбрать/проверить достижимую сухую stand point и segment corridor;
не вести всех в anchor centroid, если он оказался водой/непроходимым местом. Использовать
existing pathfinding/zone APIs и ограниченный поиск соседней легальной stand point.
Не добавлять глобальный запрет воды: lawful swim и выход из уже водного положения
не должны ломаться. Повторный неверный маршрут исключается с TTL и replan, а не teleport.
Geodata не заменять сторонним пакетом и не заглушать коллизии без отдельного evidence.

## 10. Loot и наблюдаемая жизнь

Stock AutoPlay уже имеет Pickup и PhantomVisibleAutoPlay.configure ставит pickup=true.
Проверять/чинить его wiring: reachable item, owner/protection, distance, capacity,
ignored items, auto-loot configuration, inventory mutation, canonical save и reload.
У auto-loot нет необходимости визуально подходить к предмету; доказать штатное начисление.
Нет дропа по RNG — не ошибка, но и не доказательство работающего ground pickup.
TEST создаёт допустимый предмет только через guarded native fixture и вызывает stock
pickup worker, не ручной addItem как замену. В PLAY запрещены forced drops/rates/grants.
Не подходить бесконечно к защищённому чужому предмету и не блокировать им следующую цель.

Passive evidence: exact Player+epoch + target object/instance/lifetime + монотонный
sequence + observed damage/reward/loot events. Baseline sample не считается новым событием.
Данные фиксировать в actual native completion, не в выборе target и не в test runner.
Убийство и очистка aggro не должны терять attribution. REWARD из party не доказывает
собственную атаку, а чужая атака не подтверждает selected Phantom.

Cohort фиксируется до оценки поведения, минимум 4 natural eligible participants.
FAILED/пропавшая строка/смена epoch/недоступная страница не уменьшают denominator.
Expected death/recovery объясняется progression и deadline; unexplained permanent idle — RED.
Не выводить универсальную исправность всего High Five из двух сцен: acceptance scope
и representative native scenario matrix перечисляются в финальном отчёте явно.

## 11. Совместимость времён приёмки и native состояния

Сохранить значение SOFT_RETURN: краткий выход observer за region и возврат без churn,
тот же object/epoch. W06 long absence/background/reentry — самостоятельный TEST, не
повод требовать исчезновения Player в штатном 15s soft absence.

OBSERVE40s заменяется bounded наблюдением до180s с ранним выходом лишь после полных
required cycles и cohort evidence. Глобальный480s/cleanup45s, mailbox budget и calendar
horizon пересчитываются согласованно; не повышать лимиты тайком после очередного RED.
INITIAL nextBoundary должен покрывать worst-case оставшуюся сцену и безопасное cleanup.

Второй natural selected profile выбирается через существующий server selector с
проверенным необязательным исключением только первого завершённого selected ID.
Этот параметр не даёт права исключать failed/idle участников cohort или менять state.
