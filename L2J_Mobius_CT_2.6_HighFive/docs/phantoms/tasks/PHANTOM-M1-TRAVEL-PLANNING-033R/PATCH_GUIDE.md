# Карта правок: CURRENT → PROBLEM → REQUIRED
Все фрагменты ниже сверены на required base; не готовый production patch.

## A. Прямая нарезка в PhantomVisibleFarmTravel.walk (примерно357–428)
```java
// CURRENT
if (journey.currentAnchorReturn && distance > 2000)
{
    final int x = current.x() + (int) Math.round(((long) destination.x() - current.x()) * 2000 / distance);
    final int y = current.y() + (int) Math.round(((long) destination.y() - current.y()) * 2000 / distance);
    requested = new PhantomNavigationPoint(x, y, GeoEngine.getInstance().getHeight(x, y, current.z()), current.instanceId());
}
// PROBLEM: points lie on a ray; a later point may lie behind cliff/water.
// REQUIRED SHAPE (новый контракт, не существующая сигнатура):
// Optional<VerifiedLeg> leg = corridor.nextLeg(actualPose, goal, epoch, now);
// submit only leg proven by stock route + segment/door/height/water validation.
// ARRIVED only at final acceptable farm ingress, never at intermediate leg.
```
Не удалять существующую проверку final arrival/store и aggregate budgets.
Текущий C08 test проверяет buffer envelope, но не доказывает полный обход.
Новый RED должен падать на U-shaped detour и доходить до фактического move→farm.

## B. Native backend и normalizer
```java
// CURRENT L2jNavigationBackend.findPath
final List<GeoLocation> path = PathFinding.getInstance().findPath(
    request.origin().x(), request.origin().y(), request.origin().z(),
    request.destination().x(), request.destination().y(), request.destination().z(),
    request.origin().instanceId(), true);
```
PhantomNavigationService.validateBackendPath вызывает
`PhantomNativeRouteContract.normalize(..., _backend::canMoveDirect, ...)`.
Если путь backend получен, но normalize отверг его, фиксировать FIRST edge и оба
набора точек. Не менять validation в true. В PathFinding есть package-private
`findRawPath(...)`, но он отключает postFilter, а НЕ constructPath: сырой маршрут
всё ещё может быть сжат. Нельзя назвать его полным клеточным путём.
Разрешён узкий opt-in access к необрезанной native parent chain, только если
сравнение докажет дефект удаления поворота. Не переписывать NodeBuffer соседей.

## C. Macro routing
```java
// CURRENT PhantomNormalGatekeeperTravel.search
final ArrayDeque<String> queue = new ArrayDeque<>();
// BFS marks visited once; baseTravelMillis/fees do not define ordering.
```
Для новой стратегии — отдельный weighted selection, где полный lawful путь получает
ожидаемое время и совокупную стоимость. Старый route(String,String) для background
не менять молча. Не считать topology BACKGROUND edge native-проходом.

## D. Публикация новых намерений
Использовать existing catchupStore.replacePlan / prepareVisibleDecision и exact
claim/goal/epoch. Изменение маршрута внутри той же цели не создаёт новую reward
операцию. Отмена устаревшего callback не затрагивает новый lifetime.
Не использовать actor IDs5/19 как production условие.

## E. Unstuck
```java
// CURRENT handler
final int unstuckTimer = (player.getAccessLevel().isGm() ? 1000 : PlayerConfig.UNSTUCK_INTERVAL * 1000);
player.setSkillCast(ThreadPool.schedule(new EscapeFinalizer(player), unstuckTimer));
```
Здесь нельзя вставить простой вызов onCommand(52,player) и объявить готовность.
Нужна отдельная real managed lifetime cancellation/drain проверка. См. ESCAPE_AND_RECOVERY.

## F. Логи
Одно bounded событие на смену состояния/отказ, не тысячи строк на каждый tick:
profile/object/epoch/goalRevision/planId; strategy; from/target; edge reason;
expected time/fee; navigation generation; actual arrival; recovery outcome.
Ordinary persistence hook — без SQL/I/O/wait. Использовать существующий recorder.
