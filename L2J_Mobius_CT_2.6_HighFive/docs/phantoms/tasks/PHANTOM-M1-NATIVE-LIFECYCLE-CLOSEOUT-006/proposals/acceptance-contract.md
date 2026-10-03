# Proposed passive proof contract

Это новые поля/вычисления, не утверждение, что они уже есть в source.

```text
actorKey = profileId/objectId/materializedAtNanos
intentKey = goalId/goalRevision
sample = actorKey + intentKey + sampledAtNanos + evidenceSource
selectedNativeDamageDelta : long >= 0 (exact actor, fresh interval)
selectedNativeExpDelta / selectedNativeSpDelta : signed long
selectedNativeKillRewardObserved : boolean + native attribution identity
pairedNativeRegistrationHealthy : boolean
firstCleanupIncidentId / phase / class / sequence
observationComplete : boolean
```

Pure evaluator `evaluateNativeLife(before, after, attributedEvents)`:
- identity/epoch/revision change → split intervals, no transfer of proof;
- new damage attributed exact selected native actor >0;
- positive EXP/SP or verified native kill/reward in same epoch/observation window;
- no generated damage/EXP, no self-heal flag proxy;
- incomplete attribution = UNPROVEN;
- first seen pre-existing AggroInfo amount is baseline, not delta;
- damage by another actor and monster HP reduction alone cannot qualify;
- death/reward and reset AggroInfo require passive event correlation, not negative delta.

Pure evaluator `evaluateCohort(initialKeys, samples)`:
- initial natural candidate set >=4; do not silently prune failed actors;
- >=2 distinct actors have native farm progress;
- others progressing in native travel/recovery or bounded diagnosed waiting;
- repeat FAILED/closed admission/stale ownership ⇒ FAIL;
- no two real observations / missing pages ⇒ UNPROVEN;
- new materialization epoch of visible actor is continuity violation unless an explicitly
  permitted lifecycle event with matching proof, not a way to reset the denominator.

Тесты этой функции включить в существующий offline runner test и native Pilot suite.
Не делать bool-only mock зелёным вместо реального датчика attribution.
