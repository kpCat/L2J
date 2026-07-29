# Acceptance — Goal 015

## Git/scope

- [ ] parent `9c9412bc4a05a520a83b5187054d6c8a8c12db3c`, branch/subject exact, one ordinary child, remote exact;
- [ ] Goal 014/014A accepted review recorded; 016/017/025 not started;
- [ ] no Player/Item/Inventory/Attackable/loader/schema/config/other-chronicle edit;
- [ ] no `progression.learn_skill` production candidate;
- [ ] no new worker/thread/task/Future or unbounded scan/retry.

## Ownership/transitions

- [ ] typed background identity lease blocks materialization and REAL_LOGIN;
- [ ] exact operations/leases/transactions/transition counters drain on stop;
- [ ] activity generation/tick propagate into plan/step and operation key;
- [ ] dematerialization captures fresh stored baseline before identity release;
- [ ] materialization drains/verifies background before Player load/spawn;
- [ ] no transition restores HP/MP/CP/items/EXP/position for free;
- [ ] 50 transition loop and restart matrix conserve byte-exact state.

## Model/transaction

- [ ] versioned deterministic MELEE/RANGED/MAGIC/SUMMON model, no class names;
- [ ] exact single-player EXP/SP parity and exact drop chance/group semantics;
- [ ] persisted RNG, bounded encounters/elapsed/items and competition;
- [ ] main/subclass, level/delevel, expBeforeDeath and auto-get-only skills exact;
- [ ] one MariaDB transaction locks goal/state/character/subclass/skills/items;
- [ ] row-count guards, rollback, VERIFY_PENDING fresh proof and fail-stop;
- [ ] duplicate/stale work is idempotent/rejected with zero extra mutation.

## Gameplay

- [ ] explicit target/anchor only; optional background-eligible route;
- [ ] exact shots/spiritshots/summon resources and inventory constraints;
- [ ] travel partial/full/closed-edge and last-anchor promotion;
- [ ] causal attrition/regen/death/EXP loss;
- [ ] WARM canonical revive/to-town recovery and typed farm-goal failure;
- [ ] unsupported party/champion/raid/instance/vitality/etc. fail closed.

## Evidence

- [ ] all six focused modes and affected historical suites green;
- [ ] real Player/monster/drop/topology/main-subclass integration;
- [ ] fault, concurrency, restart, REAL_LOGIN and performance matrices;
- [ ] historical 014/014A + new 015 verifiers green before full verify;
- [ ] final `ant verify` and standalone `ant jar` green;
- [ ] post-commit verifier 2× byte-identical;
- [ ] report <=220 lines with full telemetry;
- [ ] token `GOAL_015_BACKGROUND_FARMING_RECONCILIATION_IMPLEMENTED_PENDING_INDEPENDENT_REVIEW` only after every gate.
