# SOURCE MAP — exact files and known current behavior

Исходная ветка baseline 63f7c819; строки сверять по актуальной копии.

| Source file relative to module | Read-first points | Required change |
|---|---|---|
| `java/org/l2jmobius/gameserver/phantoms/activity/PhantomMaterializationRetentionPolicy.java` | `observe()` ~78-111: при `!materialized` удаляет `_lastHuman`; `refresh()` ~116-119 удаляет всё не из списка materialized | Сохранить recent observation до grace expiry, дать read-only check; не менять остальную Retention/permitsNewActions семантику. Старый `Hold` не должен заставить STORED удерживаться физически |
| `java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java` | `retentionFacts()` ~3201-3220, `onlineHumanPoints()` ~3367-3373 содержит `.limit(256)`, `humanObserver()` ~3375-3382; `PhantomNativeLocalityEnvelope.prewarm` уже используется в `softCleanupHumanAbsent` | Новый read-only narrow offscreen-observation supplier/facade с honest overflow, actual live human source, source+dest проверками. Не ломать старый API |
| `java/org/l2jmobius/gameserver/phantoms/topology/PhantomNativeLocalityEnvelope.java` | `region()`, `couldKnow()`, `prewarm()`; prewarm conservatively expands 2 region cells along XYZ | Reuse, не переписывать геометрию |
| `java/org/l2jmobius/gameserver/phantoms/topology/PhantomHumanLocalityControl.java` | `PhysicalDemand.overflow`: overflow запросов profile candidates, **не human census** | Read-only comparison, НЕ использовать для разрешения offscreen |
| `test/java/org/l2jmobius/tests/phantoms/PhantomObserverHandoff040Suite.java` | Existing observer/retention tests | Targeted regression; если тест нельзя запустить без runtime, честно NOT_RUN |
| `test/java/org/l2jmobius/tests/phantoms/PhantomHumanLocalityTraceSuite.java` | Existing native human locality tests | Related regression only, do not launch expensive native campaign |
| `test/java/org/l2jmobius/gameserver/phantoms/knowledge/PhantomOffscreen092Checks.java` | Repro `retentionBoundary()` 92: `observe STORED` forgets 60s; TEST_ONLY graph0 witness | Read-only predecessor; не переписывать history, можно создать новый focused `092A` test |

### Runtime API guidance, not a mandate to invent extra service layers

Recommended a compact immutable typed `ObservationVerdict` (e.g. `ALLOW`, `DENY_RECENT`, `DENY_ORIGIN_HUMAN`, `DENY_DESTINATION_HUMAN`, `UNKNOWN_OVERFLOW`, `UNKNOWN_WARMUP`, `UNKNOWN_WORLD`) with a `Reason`, no authority to mutate. Separate deterministic pure evaluation from `PhantomSystem` live census wiring. A census with 257 actual humans yields `UNKNOWN_OVERFLOW`, not truncated `ALLOW`. For no people, only grant `ALLOW` after conservative server startup grace (or stronger explicit evidence). A new human moving into an inspected region must revoke allowance on fresh call. Never cache `ALLOW` beyond the call; 092B will re-evaluate inside its commit admission.

Current `PhantomBackgroundTransaction.execute()` already validates exact `Command.goal` and creates operation keys including `goalId/revision`; do not modify it in 092A. Missing full binding for virtual travel will be designed and implemented separately as 092B, avoiding task092's original all-at-once failure mode.
