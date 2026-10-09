# PATCH_GUIDE030 — выдержки exact base

Source SHA758ee295518be5b9abba0f757cc240782bfd2987. Выдержки ниже не complete patch;
точные методы и контракты в DESIGN. Не переносить в код буквальные многоточия.

## 1. Contract029Observer.java: enroll / active registry
```java
// CURRENT
if (RECEIPT_OWNERS.size() >= 128 || (RECEIPT_OWNERS.keySet().stream().noneMatch(value -> value.profileId() == profile)
    && RECEIPT_OWNERS.keySet().stream().map(Lifetime::profileId).distinct().count() >= 32)) { throw new IllegalStateException("TASK029_RECEIPT_CAPACITY"); }
```
PROBLEM: cumulative history is bounded by a concurrent-style constant; scopes never evicted
AFTER successful terminal export. 1280 READY means32 unique births is not a viable run budget.
REQUIRED SHAPE: bounded ACTIVE map, immutable stream, export ack removes terminal references.
Simply replacing32 with10000 does not fix unbounded long-lived JVM retention.

## 2. PhantomBackgroundService.advanceHistorical
```java
// CURRENT
final PhantomBackgroundTransaction.Command command = new PhantomBackgroundTransaction.Command(state, goal, key, batch.progress(), batch.vitals(), state.position(), clock, batch.inventoryDelta().itemDeltas(), autoSkills, List.of(), null, mutation);
return commit(claim, ordinaryCommand(claim, command, batch.policy())).withModel(batch.encounters(), batch.elapsedMillis(), batch.dead());
```
PROBLEM: batch may have a valid new-plan drop absent from the committed old footprint.
REQUIRED SHAPE: proof-derived complete ordinary footprint BEFORE `_model.evaluate`;
if projection needs refresh, atomic metadata-only refresh + typed retry with zero model delta.
No `batch.inventoryDelta().itemDeltas().keySet()` used as self-issued authority.

## 3. PhantomBackgroundTransaction.mutateItems
```java
// CURRENT — KEEP
if (!mutableItemIds.contains(itemId))
{
    throw mutationItemConflict("NON_MUTABLE_ID", command, lockedRows, mutableItemIds, itemId, delta, "NOT_APPLICABLE", backgroundComponent, goalComponent, catchupComponent, acquisitionComponent);
}
```
Guard остаётся. Fix до изменения предметов, не превращение бросившего guard в SUCCESS.

## 4. PhantomBackgroundTransaction.Command
```java
// CURRENT — acquisition semantic contract, DO NOT WEAKEN
if ((additionalMutableItemIds.size() > PhantomBackgroundState.MAX_MUTABLE_ITEM_IDS) || additionalMutableItemIds.stream().anyMatch(itemId -> itemId <= 0) || ((acquisition == null) != additionalMutableItemIds.isEmpty()))
{
    throw new IllegalArgumentException("Invalid acquisition background item allowlist.");
}
```
Не использовать acquisition bypass для ordinary farm. Proposed projection refresh не
нуждается в изменении этого guard или native receipt format.

## 5. PhantomNativeWorkScope.checkpointStage
```java
// CURRENT — observer failure already separated from native outcome; KEEP
try { observer.accept(_player, stage); }
catch (Throwable failure) { if (_checkpointObserverFailure.isEmpty()) { _checkpointObserverFailure = PhantomCleanupIncident.bounded(failure.getClass().getName() + ":" + failure.getMessage(), 256); } }
```
Capacity failure не доказанный producer cohort0; не снимать native guards ради collector.
Пример различения diagnostic fault vs product fault нужен в A/B TEST.

## 6. PhantomHistoricalBackgroundService.advance (replan section)
```java
// CURRENT — existing refresh, inspect its result instead of blindly duplicating it
if (farmProjectionChanged(storedGoal.goal(), replanned.goal()))
{
    final Result refreshed = refreshCanonicalBaseline(profileId, current);
    if (!refreshed.successful()) { return refreshed; }
    backgroundState = _background.acquisitionSnapshot(profileId).orElseThrow();
}
```
Reproducer должен пройти реальную цепочку selectedGoal → attestation → footprint → batch.
Не mock обход, где model и transaction получают заранее одинаковый inventory set.
