# PATCH_GUIDE — exact base 819e3cea…

## 1. L2jPhantomBackgroundAuthority.capture
// CURRENT (реальный source)
```java
final PhantomTopologyAnchor anchor = exactAnchor(player, previous);
final Position position = new Position(player.getInstanceId(), player.getX(), player.getY(), player.getZ(), player.getHeading(), anchor.id());
```
`nativePersistence=true` сейчас не отделяет этот выбор от геометрии.

// REQUIRED SHAPE (интеграционный пример, НЕ готовый server patch)
```java
// Owned capture never changes native XYZ just to satisfy the simulation.
final NativePositionBinding binding = nativePersistence
    ? bindNativePosition(player, goal, previous)
    : bindStrictSimulationPosition(player, previous);
final Position position = new Position(player.getInstanceId(), player.getX(), player.getY(),
    player.getZ(), player.getHeading(), binding.anchorId());
```
`bindNativePosition` возвращает proven previous/goal reference и geometry validity отдельно.
Пример decision table в proposals/BoundaryPolicy024.java. Не применять geometryValid=true
без реальной проверки Instance/area/known anchor. `captureOwnedNative` должен вернуть
Capture POSITION_REQUIRES_NATIVE для off-area. Аналогичный acquisition owned capture.
`captureNativeContext` для after-load/arrival не должен стереть позиционный запрет только
потому, что vitality поддержана: использовать текущий captured state/binding или overload
в PhantomBackgroundAuthority. Проверить все refresh/prepare вызовы.

## 2. NativeContext
// CURRENT
```java
public enum Eligibility { UNKNOWN, SUPPORTED, VITALITY_REQUIRES_NATIVE }
public boolean simulationEligible() { return (phase == Phase.COMPLETED) && (afterEligibility == Eligibility.SUPPORTED) && (afterPoints == 1); }
```
// REQUIRED SHAPE
```java
public enum Eligibility { UNKNOWN, SUPPORTED, VITALITY_REQUIRES_NATIVE, POSITION_REQUIRES_NATIVE }
// simulationEligible remains exact SUPPORTED. Preserve all existing ordinal values.
```
Новый enum сам по себе недостаточен: intent pending→completed, state hash/row binding,
restore and after-load paths должны сохранить классификацию и вернуть native handoff,
не вечный pre-World отказ. Legacy payload fixtures byte-exact; crash matrix обязательна.
`PhantomOwnedStoreIntent` менять НЕ требуется: target state остаётся READY/DEAD/MATERIALIZED.
Native-context proof уже входит в существующую transaction. Не вводить raw snapshot файл
в качестве второго source of truth. Code skeleton не проверялся против полной сборки Mobius.

## 3. Creature / PlayerNativeWork
// CURRENT
```java
public void doAttack(Creature target)
{
    PlayerNativeWork.runCombat(this, Collections.singletonList(target), "attack-frontend", () -> doAttackNative(target));
}
```
`runCombat` → `run` → requireCapturedEarnedParticipants использует ambient earned context.

// REQUIRED SHAPE
```java
PlayerNativeWork.runOriginalCombat(this, Collections.singletonList(target),
    "attack-frontend", () -> doAttackNative(target));
```
Новая helper работает только для original entry по DESIGN-A: exact current healthy
CANCELLABLE roots start-all → old native body/publication → complete-all.
Не подменять generic run/schedule. Не разрешать original entry для captured stale actor.
У new roots нельзя наследовать право новых действий от DRAINING earned owner. Проверить
NPC→Player/Player→NPC/обычный Player/no managed participants и nested third actor.

## 4. PhantomSystem shutdown
// CURRENT ordered calls, показаны отдельные строки из реального метода
```java
_topologyService.beginStop();
// ... later ...
final ShutdownResult result = _materializationService.shutdown();
```
`registerProfile/updateProfile` возвращают NOT_RUNNING без runningView.
Перенести закрытие lifecycle dependencies ПОСЛЕ успешного materialization/background
финала. Применить порядок также в FAILED retry branch. Не открывать новый gameplay
через topology ради POST_STORE. Не глотать pending/native failure после FINALIZE.

## 5. Visible local plan/travel
`isVisibleLocal`/`addLocalCandidates` сейчас используют point-distance≤4_000_000.
`start` проверяет old attempt deadline до завершения arrival. `usefulArrival` требует
не находиться в точности на anchor X/Y и dry grounded cell; это не критерий доступных мобов.

Нужны named helpers внутри существующих классов: `isUsableLocalFarmPosition(...)` и
`localFarmDistance(...)`, которые используют factual node area + native candidate/path,
не hardcodeprofile и не мировую прямую дистанцию через воду. Replan исключает failed route
witness, не весь вид NPC в области. Bound8 local candidates и native geodata guards остаются.
Доказать exact причины профилей281/459 по retained traces до edits; остальные возможности
DESIGN-D не являются автоматическим разрешением «улучшить AI».
