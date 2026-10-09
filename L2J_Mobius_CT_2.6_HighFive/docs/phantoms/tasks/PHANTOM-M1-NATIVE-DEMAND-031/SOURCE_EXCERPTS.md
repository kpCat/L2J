# Проверенные source-пункты на required base

Все ссылки на один commit e083f35d9b3b1c1441f484c8f760c8dc34bbdbc0.
Ниже точные короткие фрагменты. Это read-first карта, не готовый патч.

## 1. Task-only выбор setup: Observe030.ps1
```powershell
$eligible=@($globalRows | Where-Object {$_.state -ceq 'READY' -and $_.calendarOnline -ceq 'true' -and $_.farmAllowed -ceq 'true' -and $_.readinessComplete -ceq 'true'})
```
Проблема: требует результаты background/finish, прежде чем появится локальный demand.
REQUIRED: только выбор диагностической точки не использует последние два условия;
никакой замены фактических criteria в production/native scene.

## 2. Contract030Observer.java: около строк303–331
```java
final boolean farm = context != null && context.permits(org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundSimulationPolicy.Operation.FARM,
    org.l2jmobius.gameserver.phantoms.background.L2jPhantomBackgroundAuthority.configuredSimulationFingerprint());
```
Это BACKGROUND FARM, а не «native Player вправе действовать».

## 3. PhantomPopulationEcologyService.java: около845–920
```java
// Foreground supplies the missing native context; the pending history remains fenced.
if (materializationDue && _currentDemand.test(profileId) && !entry._terminal && (_wakeFailure == null) && _populationPlanApplied && _inventoryReady && !_metadataDraining
    && (state != null) && (state.disposition() == Disposition.MANAGED) && state.requestPending() && (historical != null)
    && historical.requestId().equals(state.currentRequestId()) && (historical.fromEpochMinute() == state.calendarCursorEpochMinute()) && (historical.targetEpochMinute() == state.currentWindowTargetEpochMinute())
    && (historical.status() != Status.COMPLETE) && PhantomHistoricalBackgroundService.requiresNativeMaterialization(entry._lastReportedFailure))
{
    entry._materializationDemand = true;
    return new DueReconciliation(true, 0, "ecology.native_materialization_required", org.l2jmobius.gameserver.phantoms.activity.PhantomActivityMaterializationPort.MaterializationRequest.nativeContextHandoff(historical.requestId()));
}
```
Этот exact claim path СОХРАНЯЕТСЯ. Не подменять его принудительным true.

## 4. Там же: около1680
```java
private static String typedFailure(RuntimeException exception)
{
    final String message = exception.getMessage();
    return (message != null) && message.matches("[a-z0-9_.-]{1,96}") ? message : "ecology.persistence_or_runtime_failure";
}
```
Это классификация результата, не достаточный first-cause evidence. Исходный Throwable
нужно сохранить ограниченно на existing boundary, не выводить диагноз из общей строки.

## 5. PhantomBackgroundTransaction.java, readAcquisitionEligibility и другие boundaries
```java
catch (Throwable failure)
{
    rollback(connection, failure);
    return EligibilityResult.rejected(failure instanceof StateConflict conflict ? conflict._status : Status.BACKEND_FAILURE);
}
```
Не угадывать, что именно выбросило исключение. Не превращать все backend failures
в transient success. Diagnostic side channel не меняет существующий результат.

## 6. Фактический census030
`evidence/C5_FINAL_SCENE_A030C/cohort0-first-guards/global-admission.tsv`:
profile10 READY/calendarOnline=true/farmAllowed=true/readinessComplete=false,
reason=ecology_fenced:transaction.backend_failure:SUCCESS.
profile13 READY/online=true/farmAllowed=false/contextUNKNOWN,
reason=ecology_fenced:transaction.item_conflict_canonical:NATIVE_CONTEXT_REQUIRED.
profile30 INCONSISTENT/calendarOnline=false; профиль не healthy candidate.
`all-materializations.txt` в том же capture — [].
Это доказывает отсутствие entries в том snapshot, а не отсутствие всех4182 lifetimes
за время пяти JVM. Все эти счётчики имеют разные интервалы и смысл.

## Прямые ссылки
ROOT = https://github.com/kpCat/L2J/blob/e083f35d9b3b1c1441f484c8f760c8dc34bbdbc0/L2J_Mobius_CT_2.6_HighFive/
- docs/phantoms/tasks/PHANTOM-M1-PLAN-INVENTORY-CONTINUITY-030/RESULT.md
- docs/phantoms/tasks/PHANTOM-M1-PLAN-INVENTORY-CONTINUITY-030/Observe030.ps1
- docs/phantoms/tasks/PHANTOM-M1-PLAN-INVENTORY-CONTINUITY-030/Contract030Observer.java
- java/org/l2jmobius/gameserver/phantoms/population/PhantomPopulationEcologyService.java
- java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundTransaction.java
