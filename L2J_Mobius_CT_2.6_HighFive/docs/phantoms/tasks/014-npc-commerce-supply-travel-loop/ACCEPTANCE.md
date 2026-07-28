# Acceptance — Goal 014

## Git/scope

- [ ] parent `e9b98a243a68a710425a062155b9197ee6692b17`, branch `feature/phantom-world`, one ordinary child;
- [ ] exact subject `feat(phantoms): add npc commerce supply and travel loop`, remote equals HEAD;
- [ ] High Five only; no core/loader/packet/config/schema/geodata changes;
- [ ] Goal 015/017/025 not started;
- [ ] efficiency standard committed at `docs/phantoms/`.

## Catalog/truth

- [ ] deterministic buylist/multisell/teleporter/supply catalog and hashes;
- [ ] supply classification is mechanical, not name-based;
- [ ] CP 5591/5592 + skill 2166 and exact current source/currency truth;
- [ ] Ancient Adena only when exact item 5575 is present in current offer;
- [ ] multisell execution absent;
- [ ] no query-time scan.

## Operations

- [ ] exact quote under ActionLease and immediate revalidation;
- [ ] one unlimited buy line, one sell object/count, one NORMAL route;
- [ ] limited stock/castle/refund/zero-price unsupported paths are typed;
- [ ] canonical APIs only; no packets/bypass/direct insertion;
- [ ] receipt PREPARED/COMMITTING before effects;
- [ ] same-key retry idempotent;
- [ ] exact-before resume, exact-after success;
- [ ] ambiguous concurrent delta -> INCONSISTENT with no compensation/replay;
- [ ] buy/sell/teleport conservation and restart matrix pass.

## Decision/lifecycle

- [ ] explicit acquire/supply/sell/travel candidates before seal;
- [ ] no goal invention or World scan;
- [ ] no candidate/action references `progression.learn_skill`;
- [ ] disabled path inert;
- [ ] no new worker/thread/task/Future;
- [ ] commerce drains before materialization.

## Evidence/efficiency

- [ ] initial READ_SET <=12, additions <=5 and reported;
- [ ] repository searches <=6 before first patch;
- [ ] focused suites one final green run;
- [ ] full `ant verify` count <=2;
- [ ] standalone `ant jar` PASS;
- [ ] verifier 2× byte-identical;
- [ ] report <=180 lines with usage telemetry;
- [ ] token `GOAL_014_NPC_COMMERCE_SUPPLY_TRAVEL_LOOP_IMPLEMENTED_PENDING_INDEPENDENT_REVIEW` only after every gate.
