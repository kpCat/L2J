# DESIGN038 — тонкий локальный action-loop поверх Mobius

## 1. Владельцы и данные
Сохранить существующий PhantomGoal FARM и DecisionEngine. Не заменить их новым Brain.
Новый controller — локальное продолжение выполняемого шага, не менеджер всех игроков.
Состояние привязано к active Session/Player+epoch; навигация хранит только scalar key:
profileId, objectId, epoch, goalId/revision, actionSequence, targetObject/instance/spawnGeneration.
Использовать existing PlayerNativeEvidence.Target для NPC incarnation; не вводить свой
генератор spawnGeneration. RequestId — от PhantomNavigationService.

Не хранить живого Player/Creature в pathfinding worker или бессрочной карте. Target
восстанавливается через World и перепроверяется при выдаче команды. Record в proposals
показывает ключ/результат; не заменяет реальный native owner и permission checks.

## 2. Возможность действия ≠ видимость по прямой
Ввести один `opportunity` путь для start/arrival и live recovery. Domain eligibility
(тип NPC, alive для атаки, instance, policy, known local area) отделяется от геометрии.
- READY: свежие native условия действия, LOS/range и безопасная позиция уже соблюдены.
- NEED_APPROACH: цель допустима, но текущая позиция не подходит; поиск позиции/коридора.
- TRANSIENT: очередь занята, checkpoint или краткая native блокировка. Не blacklist.
- INELIGIBLE / TARGET_GONE: цель перестала подходить, выбираем другую, не лезем к ней.
- NO_ROUTE_FOR_ATTEMPT: доказан лишь отказ ограниченного запроса, не «путь не существует нигде».

Нельзя отменить native restrictions. Цель за стеной — кандидат для поиска, не разрешение
ATTACK/CAST через стену. Локальная область ограничена существующими range/knowledge;
никакого сканирования всех World NPC. shortlist максимум8 из текущих локальных кандидатов,
детерминированный порядок; одно активное route request на profile. Existing ranking shared
load остаётся мягким: кооперативная атака разрешена, exclusive reservation не вводится.

## 3. Позиция действия
Native PlayerAI уже умеет move-to-range, attack/cast/pickup. Пользоваться этим, когда
native путь удовлетворяет задаче; не строить второй контроллер ударов.
Для случая, который current direct-only discovery отбрасывает, planning строит путь
к пригодной позиции с native physical/magical range, LOS, корректным слоем и instance.
Не обязательно идти к центру NPC. Кандидаты позиции — текущее положение и ограниченный
детерминированный набор до8 точек вокруг цели на её проверенной поверхности.
`getPhysicalAttackRange()`/`getMagicalAttackRange(skill)` берутся из native, не classID table.
Для магии выбранное умение известно Player, имеет native условия/resources, конечная
легальность снова проверяется сервером. Нет MP — не «геодата сломалась».
Известные способности сначала брать из существующих combat capability/knowledge API;
не открывать второй PhantomCombatActorLease параллельно действующему action lease.

Планирование выполняет существующий PhantomNavigationService.submitWalkCorridor,
результат потребляет existing consume; A* не запускается под Session/actor/SQL lock.
Проекция высоты — лишь кандидат. Сам endpoint и каждый edge проверяются native geometry.
Слой сверху/снизу с одинаковыми XY и teleport-edge не взаимозаменяемы. Для038 только
local WALK; portal/GK модели остаются существующими, новый переход этажей не изобретается.

## 4. Единственный канал управления
В рабочем FARM Session допускается только один route owner: farm travel или local approach.
В начале approach зарезервировать контроль состояния, опубликовать yield для offensive
AutoPlay/AutoUse roots, затем выполнить native перестройку намерения вне monitor.
NavigationWorker никогда не двигает Player: только результат для текущего controller.
Старые accepted EARNED callbacks продолжаются; controller не cancel/replay earned.

Минимальная интеграция: controller связывается через bindVisibleLife и используемый Session.
Существующий pre-work/decision handler выполняет продвижение один раз, второй native pool
читает published mode. Нельзя параллельно выполнять tick из обоих pool без single-writer
gate. При занятости gate — не wait/spin, bounded PAUSED; не добавлять thread/executor.
Точная точка tick — existing START/AWAIT handlers в PhantomBackgroundDecision,
не обе native pool-задачи и не второй scheduler. AutoPlay/AutoUse читают published mode.
Для короткой смены mode/выдачи новых намерений использовать один nonblocking command gate
на same Player+epoch, общий для controller и policy TickLease. Gate берётся try-acquire,
освобождается в finally; при занятости PAUSED, без ожидания/spin. Это НЕ native owner:
сам native action lease остаётся обязательным, accepted earned callbacks gate не проходят.
Новый Session в том же epoch наследует этот gate/state; retired epoch — нет. Никаких
SQL/path searches/Future.wait внутри gate. Проверять source mode прямо перед новым root.
Если scheduler/controller держит собственный work ticket, не ждать scope.outstanding==0
внутри него. Сначала yield/release, затем следующая проверка на том же deadline.

Hook AutoPlay target selection нужен только там, где без него native менеджер тут же
заменяет планируемую цель. Default STOCK для ordinary Player. Для Phantom доступны
STOCK / HOLD / USE_EXACT_TARGET; HOLD не означает REVOKED. USE всё равно проходит
native current checks. AutoUse не получает обход canUseMagic; он уступает только новым
атакующим действиям при approach. Native earned и существующий self/resource механизм
не уничтожать. Точные места и API-shape приведены в PATCH_GUIDE.

## 5. Продолжение и recovery
Не считать повтор MOVE прогрессом. Path progress — фактическое изменение положения
по текущему route tracker; farm progress — native damage/kill/reward/next-target evidence.
Раздельные часы: movement progress не обнуляет useful debt037.
При сдвиге цели/смене spawn/закрытии двери: invalidate текущий path, отменить только own
navigation request, проверить новую позицию. Deadline попытки не обновляется каждым tick.
Сначала использовать already READY opportunity, затем route; cached blocked stand-point
не запрещает свежую пригодную позицию с тем же FARM intent. Это не очистка sticky ошибок:
owner/store failure или NATIVE_UNPROVEN никогда не маскируются выбором нового NPC.

Число geometry retries и сроки — existing navigation policy. После исчерпания конкретного
attempt — bounded альтернативная цель; после исчерпания локального варианта — существующий
typed replan/cooldown. Не зависать на bool false. Не телепортироваться и не дематериализовать
под наблюдателем ради нового epoch. В M1 дальний background travel остаётся другим executor.

## 6. Future compatibility
ENGAGE требует живого enemy; SWEEP/RESURRECT имеют другую семантику target validity.
Нельзя поместить «любая мёртвая цель запрещена» в универсальный контракт: это правило атаки.
Никакой автоматической party/PvP policy в038. ROADMAP указывает existing consumers.
Все future механизмы должны получать native result и тот же cancellation/instance contract.
