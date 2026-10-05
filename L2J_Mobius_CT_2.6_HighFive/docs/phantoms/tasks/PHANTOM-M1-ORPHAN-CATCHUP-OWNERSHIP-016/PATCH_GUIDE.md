# PATCH GUIDE

## PhantomPopulationEcologyService.java

Add a small worker-only helper, suggested semantic shape:

```java
private StoredState recoverOrphanHistoricalOwnership(
    long profileId,
    ManagedSnapshot population,
    StoredState ecology)
```

Rules:
- never call HistoricalPort while holding `_monitor`;
- if ecology already `requestPending()`, return unchanged;
- read `_historical.status(profileId)` once;
- cache the snapshot through existing `cacheHistorical`;
- null/COMPLETE => unchanged;
- validate exact adoption facts;
- if safely adoptable:
  `persist(profileId, ecology, ecology.state().beginRequest(hist.requestId(), hist.target))`
  and return the saved StoredState;
- caller MUST return from that `process()` iteration after a successful adoption so the
  normal request path resumes on the next bounded worker turn;
- do not call `historical.begin()` in the adoption turn;
- do not advance ecology calendar cursor;
- do not mutate historical state.

For factual conflict, publish a typed bounded failure:
`ecology.orphan_historical_conflict`
and fence that profile from starting another historical request.
Do not globally disable Phantom World.

For transient owner/safe-boundary/CAS races:
defer/retry, not terminal corruption.

## Flight recorder

Reuse task014 recorder. Allowed observation events from EcologyService:
- `ECOLOGY_ORPHAN_FOUND`
- `ECOLOGY_ORPHAN_ADOPTED`
- `ECOLOGY_ORPHAN_CONFLICT`

No recorder API changes should be necessary.
Recorder OFF/default remains no-op.

## No other production changes

Do NOT modify:
- PhantomHistoricalBackgroundService semantics;
- BackgroundCatchupStore;
- Player;
- ThreadPool;
- MaterializationService;
- DB schema;
- AutoPlay/AutoUse.
