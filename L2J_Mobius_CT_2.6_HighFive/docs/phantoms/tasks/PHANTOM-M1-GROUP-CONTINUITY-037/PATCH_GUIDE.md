# PATCH_GUIDE037 — текущие точки и допустимая форма решения

BASE из GOAL. Номера строк — ориентиры для exact source, полные Git blobs в SOURCE_MAP.
Это не готовый игровой patch: наименование причины каждого 037 fix определяется реальным
raw trace. Нельзя применять альтернативы ниже одновременно «на всякий случай».

## 1. Сначала правильно прочитать COMPLETE_LINEAGE_REQUIRED
CURRENT — proposals/continuity028.py, evaluate:
```python
present = row.get("worldPresent") == "true" and row.get("dead") == "false"
# ... absence ends current segment; epoch change ends another segment ...
if absent or len(segments) != 1:
    faults.append("COMPLETE_LINEAGE_REQUIRED")
```
PROBLEM: сводное имя допускает неверное толкование как DB corruption.
REQUIRED: unchanged evaluator остаётся; отдельно таблица фактических epochs/reasons+receipts.
Никаких late-SQL замещений, преобразования raw absent в present или зачёта 6/6 FULL_SAVE как continuity6/6.

## 2. Подходящий Handler заканчивается реальным resume, не только REPLAN
CURRENT — PhantomBackgroundDecision.awaitVisible:
```java
if (!_visibleSuitable.apply(context.profileId(), context.goal()))
{
    _visibleStop.accept(context.profileId());
    return PhantomStepResult.of(Type.REPLAN, "background.visible.replan_required");
}
```
CURRENT — PhantomHistoricalBackgroundService.visibleFarmReady:
```java
if (failures.protocolBlocked(goal, epoch)) { return visibleFarmResult(profileId, goal, epoch, false, "visible.protocol_blocked"); }
if (failures.routeBlocked(goal, epoch)) { return visibleFarmResult(profileId, goal, epoch, false, "visible.route_blocked"); }
```
REQUIRED SHAPE: first real VISIBLE_FARM_READINESS event → origin of failure/exclusion →
prepareVisibleDecision outside handler/inFlight → stop only own old session → wait native
quiet without shared-worker blocking → exact replacePlan/rebind/reload → native start.
Assert не только newRevision, но resumed current registration и следующий реальный hit/reward.
Protocol/stale/foreign denial сохраняется. Уже существующую диагностику не дублировать.

## 3. Пауза не должна самопроизвольно становиться вечной остановкой
CURRENT — PhantomVisibleAutoPlay.Policy.acquireTick:
```java
return new TickAdmission(paused ? TickStatus.PAUSED : TickStatus.REVOKED, null, reason);
```
CURRENT — healthy:
```java
&& AutoPlayTaskManager.getInstance().hasPhantomRegistration(player, session._policy)
&& AutoUseTaskManager.getInstance().hasPhantomRegistration(player, session._policy)
```
REQUIRED SHAPE: проверить actual policy/session epoch в обоих pools, current goal/revision,
owner/open/pending checkpoint. PAUSED сохраняет exact resumable session; настоящая REVOKED
не должна получить чужой permit. При смене revision old registries окончательно остановлены,
new session не теряет useful debt того же live epoch. Синхронных SQL/read-under-owner-lock не добавлять.

## 4. Ресурсы и useful debt
CURRENT — observeProgress:
```java
if ((progress.damageSequence() > session._damageSequence)
    || (progress.farmCycleSequence() > session._cycleSequence) || reward)
{
    session._usefulSince = now;
    session._repairAttempted = false;
    session._stallReported = false;
    session._resourceSince = 0; session._resourceExpired = false;
}
```
REQUIRED: trace минимальной доступной offensive skill, реальной MP/regen/cooldown,
состояний sit/stand, угрозы и native target. Лишь доказанная причина даёт право исправить
adapter choice/resume. Не менять *_DELAY_NANOS, не считать HP heal/loot/новый target как damage.
Функция ресурса и shared targeting не должны запретить cooperative атаку при альтернативе.

## 5. NATIVE_UNPROVEN — найти источник, не стирать наблюдение
CURRENT — PlayerNativeEvidence:
```java
public enum UnprovenReason { NONE, OWNER_FAILURE, INVALID_VALUE, INVALID_TARGET,
    TARGET_CAP, COUNTER_OVERFLOW, TIME_REGRESSION, INVALID_PHASE, PHASE_DEADLINE, COMBAT_ACCOUNTING }
```
CURRENT — cycle completion:
```java
if (!state._counted && state._nextSelected && state._damaged
    && state._killed && state._exp && state._sp)
{
    state._counted = true;
    _farmCycleSequence = add(_farmCycleSequence, 1);
    _targets.remove(target);
}
else if (state._rewardSettled && state._nextSelected) { _targets.remove(target); }
```
REQUIRED:173 first reason/timestamp vs current object/epoch, event-before-fault, native impact
и reward settlement. TARGET_CAP нельзя лечить простым LRU eviction unresolved targets;
PHASE_DEADLINE нельзя лечить reset timer или snapshot omission. При пропущенной native
публикации разрешена минимальная observation-only правка вызывающего already-listed bridge,
с самостоятельным доказательством реального writer. Если требуется иной native core path,
зафиксировать boundary, а не разносить fix на 20 files.

## 6. Извлечение исходных фактов без нового acceptance
В tools/cohort_forensics037.py реализован:
`load_evaluator(path)` — exact pinned blob (CRLF conversion отдельно допускается);
`analyze(baseline, frames, primaries, capture, evaluator)` — исходный evaluate + raw first windows;
`write_new(path, data)` — только новый output ВНЕ immutable input episode.
Tool не устанавливает SQL conservation/root cause и не повышает pass. Все ошибки evaluator
остаются видимыми. Чтение четырёх JSON из реального 036 — обязательно до переписывания helper.
