# PATCH_GUIDE038 — реальные точки и required shape

BASE=aa63dafb79e19a531b5620af195764c823bd9539. Строки ориентировочные;
методы и Git blobs — SOURCE_MAP. CURRENT ниже взят из опубликованного source.
Это design patch guide; не вставлять фрагменты без native integration и RED.

## A. Discovery не должен окончательно отбрасывать цель только из-за прямой
`AutoPlayTaskManager.AutoPlay.run`, секция Find target:
```java
// CURRENT
if ((Math.abs(player.getZ() - nearby.getZ()) < 800)
    && GeoEngine.getInstance().canSeeTarget(player, nearby)
    && GeoEngine.getInstance().canMoveToTarget(player.getX(), player.getY(), player.getZ(),
        nearby.getX(), nearby.getY(), nearby.getZ(), player.getInstanceId()))
{
    final double creatureDistance = player.calculateDistance2D(nearby);
    final int load = phantomPolicy == null ? 0 : Math.max(0, phantomPolicy.targetSelectionLoad(nearby));
    // ranking ...
}
```
PROBLEM: это valid direct-action filter, но не полный discovery для route-capable bot.
REQUIRED: ordinary Player остаётся на старом пути. Phantom при наличии current action
controller использует отдельную domain-valid shortlist и route opportunity; native guard
перед фактической атакой сохраняется. Не заменить && на || и не пропускать LOS ради цели.

Минимальный conditional seam внутри существующего PhantomPolicy (названия фиксируем):
```java
// PROPOSED API SHAPE, defaults preserve old implementations
record TargetDirective(Mode mode, Creature target) {
    enum Mode { STOCK, HOLD, USE_EXACT_TARGET }
}
default TargetDirective targetDirective(Player player) {
    return new TargetDirective(TargetDirective.Mode.STOCK, null);
}
```
`HOLD` — никаких новых offensive roots/stock reselection, но не остановка earned и не
permission revoke. `USE_EXACT_TARGET` — синхронная borrowed reference, не перенос Creature
в async request. При stale/current rejection controller возвращает hold/reselect.
AutoUse использует те же current facts только для offensive branch; не ломает self-buffs.
Если без добавления AutoUse hook достаточно existing acquireTick PAUSED — не менять AutoUse.

## B. Уже есть native исполнители
`PlayerAI.thinkAttack`:
```java
// CURRENT
if (checkTargetLostOrDead(target)) { setAttackTarget(null); return; }
if (maybeMoveToPawn(target, _actor.getPhysicalAttackRange())) { return; }
clientStopMoving(null);
_actor.doAttack(target);
```
`thinkCast` уже вызывает `maybeMoveToPawn(target, _actor.getMagicalAttackRange(_skill))`
и `doCast`; `thinkPickUp` использует36 и `doPickupItem`.
REQUIRED: выдавать намерения этим механизмам. Не добавлять direct HP/MP/count writes,
свой skill timer, свой loot/reward loop. Helper038 не исполняет ни одну игровую команду.

## C. Общий запрос возможности вместо sticky failed stand-point
`PhantomVisibleFarmTravel.advance`:
```java
// CURRENT
if (sameAnchor && (targetAnchor != null) && hasLocalOpportunity(profileId, player, targetAnchor, spec.npcId()))
{
    if (journey != null) { remove(profileId, journey); }
    return true;
}
if ((existingAttempt != null) && existingAttempt.matches(goal, player, epoch) && existingAttempt.terminal) { return false; }
```
Уже задумано, что terminal относится к тому пути, не ко всем целям. Проверить exact
`hasLocalOpportunity` bounds, witness и передаваемую позицию. Не «убрать terminal guard».
REQUIRED: один common `PhantomEngagementPlanner` возвращает READY/NEED_APPROACH/
UNAVAILABLE с actual target key/action position. Его используют и этот путь, и Session.
Fresh action READY может завершить старый route-only attempt; сначала actual checks,
затем cancel/consume только его request. Canonical anchor store идёт existing path.
Переход в другой FARM anchor всё ещё требует existing CAS/arrival protocol.

## D. Бюджет движения и sameepoch useful debt — разные часы
`PhantomVisibleAutoPlay.Policy.acquireTick` уже выдаёт PAUSED для checkpoint/resource/
settlement. `Session` сохраняет `_usefulSince` предыдущего Player+epoch даже при revision.
REQUIRED: controller route progress может двигать route tracker, но не `_usefulSince`.
Результат нового action не равен native reward и не меняет sticky PlayerNativeEvidence.
На target gone не срывать законно запущенный cast: перейти к новому намерению после
native boundary. Повторная команда не продлевает original deadline.

## E. Reuse queue и token binding
```java
// CURRENT API
navigation.submitWalkCorridor(request);
navigation.cancel(profileId, requestId);
navigation.consume(requestId);
```
REQUIRED: request result связывается с `(epoch, goalRevision, actionSequence,targetGeneration)`.
На replan/stop — cancel own request и discard stale result, без очередного retry-loop
на removed owner. `PROFILE_BUSY` означает yield существующему путешествию, не вторую queue.
Полные пути вынести из native tick в existing pathfinding worker. Между submit/consume
lease не держать. Перед MOVE/acquire новый lease и native проверки.

## F. Suggested integration contracts (внутри phantoms)
```java
// Final names can use nested records instead of extra files; semantics must match DESIGN.
PhantomEngagementPlanner.Opportunity inspect(Player player, PhantomGoal goal);
PhantomEngagementController.Outcome advance(long profileId, PhantomGoal goal);
void revoke(long profileId, long epoch);
```
Planner не двигает Player и не делает SQL. Controller возвращает typed continuation
и выполняет только разрешённый native action в single-writer context. Existing backend
snapshots/capabilities переиспользовать, но не запускать PhantomCombatService и AutoPlay
одновременно на одном actor. При party/PvP takeover FARM controller уступает.

## G. Контекст будущих milestones
`PhantomSystem` уже строит `PhantomPartyTactics`, `PhantomRaidDecision`,
`PhantomPartyRouteCoordinator`, semantic/clan services. Их нельзя объявлять отсутствующими
и генерировать заново. В038 только compatibility чтение + additive seam. M2/M4/M5
позднее подадут другую action policy в тот же approach-result путь.
