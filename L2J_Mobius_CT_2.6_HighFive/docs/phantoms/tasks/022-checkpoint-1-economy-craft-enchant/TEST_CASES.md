# Test matrix — Goal 022 Checkpoint 1

## Historical gates

- Goal 021 final review ACCEPT;
- verifier 021c1/021c2 historical and descendant-compatible;
- Goal 014 NPC commerce regressions;
- Goal 021 recipe-plan and acquisition regressions.

## Schema/ledger

- fresh migration and repeat migration;
- all state-transition pairs;
- payload/reservation bounds;
- same resource overlap;
- disjoint resources;
- multi-owner canonical ordering;
- reverse-order deadlock stress;
- expiration;
- dispatch cannot expire;
- terminal replay;
- audit retention;
- shutdown/restart.

## Active craft

- real learned common recipe;
- real learned dwarven recipe;
- current RecipeManager;
- ALT_GAME_CREATION current configuration;
- exact ingredients;
- HP/MP/stat-use;
- success;
- failure;
- rare/masterwork when factual fixture exists;
- ingredient shortage;
- capacity/weight;
- cancellation before/after dispatch;
- crash/restart at accepted/consumed/result;
- ordinary packet regression;
- no direct Phantom inventory mutation.

## Background craft

- exact formula parity for selected real recipes;
- ingredient consumption on success/failure;
- product and rare product;
- HP/MP/EXP/SP;
- deterministic RNG;
- object ID allocation;
- full fault matrix;
- replay;
- acquisition RecipePlan handoff;
- partial and completed acquire.item Goal.

## Active enchant

- real ordinary packet delegates to service;
- Phantom exact service call;
- success;
- safe failure;
- blessed reset;
- ordinary destruction and crystals;
- equipped target behavior;
- exact scroll/support object;
- target/scroll/support drift;
- no fake timestamp/punishment;
- crash/restart at each mutation boundary.

## Background enchant

- unequipped target;
- success/safe/blessed/destroy branches;
- exact scroll/support consumption;
- crystal result and capacity;
- equipped target ACTIVE_REQUIRED;
- deterministic RNG parity;
- full rollback and replay;
- Goal attempts/risk budget;
- authority drift.

## Cross-system

- NPC buy/sell against reserved resource;
- acquisition/background against reserved ingredients;
- materialization transition during dispatch;
- Goal revision drift;
- disabled feature flag;
- clean shutdown;
- no new task/thread/future.

## Performance

- 100k quotes;
- 100k reservation conflict checks;
- 10k background craft;
- 10k background enchant;
- 10k replay reconciliation.
