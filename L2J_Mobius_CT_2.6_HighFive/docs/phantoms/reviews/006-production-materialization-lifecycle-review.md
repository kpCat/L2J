# Независимое ревью Goal 006 — production materialization lifecycle

```text
Goal 005: ACCEPT
Goal 006 architecture direction: ACCEPT
Goal 006 commit: FIX_REQUIRED
Revert: NOT_REQUIRED
Goal 006A: REQUIRED
Goal 007: BLOCKED
```

Ревью сохраняет принятую архитектуру: canonical profile-to-`Player` service,
единый per-actor lifecycle, thin Task 004 wrapper, fair cap, две reservation
map, tokenized actions, retryable cleanup, retained-real recovery, safe
unmaterialized restart, фиксированные metrics и bounded trace.

Обязательные findings:

1. `PhantomMaterializedPlayer.materialize()` проверяет `World.getPlayer`, но не
   любой `World.findObject` и pre-existing autosave owner. Это допускает split
   identity между двумя World maps.
2. Service проверяет `RUNNING`, а затем допускает actor action вне одного
   критического участка с переходом в `STOPPING`.
3. Deadline старого `shutdown` не ограничивает wall-clock ожидание caller во
   время захвата entry monitor и `storeMe`/`deleteMe`.
4. SHA-256 verifier Task 004B
   `39A1D87DB35AE8B2DDE28EB11776A69E2F7359AC6539A900BB78D114BDBB7BC9`
   ошибочно указан как provenance Goal 005.

Goal 006A должна закрыть только эти findings: полный World/autosave identity
boundary, атомарный action admission с `STOPPING`, один tracked service-level
drain command на существующем `ThreadPool` и честную provenance. Schema,
config, retained identity recovery truth table и Goal 007 менять нельзя.
