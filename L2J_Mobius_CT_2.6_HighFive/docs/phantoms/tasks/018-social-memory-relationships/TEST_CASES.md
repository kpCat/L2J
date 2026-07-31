# Test cases — Goal 018

## Catalog/model

- current XML loads twice with identical hash/order;
- XXE, duplicate code/key, missing required dimension, invalid delta/TTL/weight,
  excessive limit and unknown modifier source are rejected;
- deterministic trait generation for 10,000 profile IDs;
- same profile/seed/catalog is byte-identical;
- no class/race/name input affects traits;
- worst-case state at 24/24 is <=4096 bytes;
- codec rejects trailing, truncation, duplicate subject/event and invalid order.

## Decay

For positive and negative values:

```text
elapsed 0
elapsed 1 minute
elapsed 1439/1440/1441 minutes
multiple days
huge elapsed
clock rollback
exact zero crossing
decay rate 0
```

Repeated intermediate queries equal one direct final query. Queries write zero
rows.

## Events/capacity

- exact event duplicate is IDEMPOTENT;
- same event facts with different ID are distinct;
- concurrent same-owner events do not lose updates;
- insert collision reloads durable winner;
- one optimistic conflict retries; fourth conflict fails typed;
- expired memories evict before live memories;
- lowest salience/oldest/hash tie-break is exact;
- neutral relationship may evict;
- nonzero debt, unresolved agreement or live memory prevents relationship eviction;
- out-of-order timestamp cannot reverse monotonic state.

## Relationships/reputation

- A→B differs from B→A;
- accepted/refused/expired invitations apply configured perspective deltas;
- join/leave/expel/leader transfer update exact counterpart;
- agreement fulfilled/broken changes counters, trust and reliability;
- debt incurred/repaid preserves signed perspective;
- real counterpart survives restart as CHARACTER_OBJECT.

## Modifiers

- all six required keys exist;
- neutral unknown subject is neutral;
- trait-only and relationship-only contributions are separately visible;
- clamp ±3000;
- evidence keys <=8 and stable;
- query does not create memories or change decay boundary.

## Party integration

- inactive FAILED/COMPLETED/ABANDONED join goal rejects preparation and response;
- accepted Phantom↔Phantom writes two asymmetric events once;
- Phantom→real refusal/expiry writes only managed owner perspective;
- real→Phantom accepted event requires ACTIVE exact join goal;
- stale terminal callback writes no new event;
- leave/expel/transfer events occur only after canonical postcondition;
- injected social failure does not roll back Party;
- coordinator stop drains callback before social stop.

## Restart/performance

- real DB insert/update/reload;
- catalog drift returns AUTHORITY_STALE without payload mutation;
- 100,000 modifier/decay queries after initialization, DB writes zero;
- 10,000 synthetic profiles, cache never exceeds config;
- no startup population scan;
- no thread/executor/Future/scheduled task;
- final service/system snapshots have zero operation/write claims.
