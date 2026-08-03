# Test matrix — Goal 022 Checkpoint 2

## Entry gate

- indexed reservation profile lookup;
- stable/changed/null/deleted links;
- beforeMaterialize and beforeStore;
- idempotent transition link revalidation;
- EXPLAIN uses the new index.

## Offers

- deterministic content hash;
- mutation changes hash;
- accept/reject/expire/cancel;
- replay and row-version conflicts;
- payload/line/participant bounds.

## Direct trade

- Phantom/Phantom;
- Phantom/ordinary Player;
- request refusal/timeout/disconnect;
- exact stack and multiple non-stackable lines;
- Adena both ways;
- capacity/weight;
- confirmation invalidation;
- reverse-order concurrency;
- every transfer fault;
- restart exact-before/exact-after/partial.

## Private stores

- buy SELL and PACKAGE_SELL;
- sell into BUY;
- Phantom and external store owners;
- partial stock;
- list/price/count drift;
- insufficient Adena;
- weight/capacity;
- store closes when empty;
- offline-trader callback;
- fault/restart matrix.

## Phantom store ownership

- open/restore/close SELL/PACKAGE_SELL/BUY/MANUFACTURE;
- active-only;
- dematerialization blocked;
- shutdown;
- no hidden/background/offline emulation.

## Manufacture

- Phantom customer/manufacturer combinations;
- ordinary Player on either side;
- normal/failure/rare;
- fee and ingredients;
- HP/MP and current EXP/SP;
- ALT current maker lifecycle;
- disconnect/cancel/restart;
- every event fault;
- no duplicate fee/product.

## Regression

- all C1 focused modes;
- NPC commerce;
- acquisition craft handoff;
- materialization;
- ordinary packet parity;
- disabled feature equivalence.

## Performance

- 100k offers;
- 100k conflict lookups;
- 10k quote/reconcile per operation family;
- 10k expiration/cleanup.
