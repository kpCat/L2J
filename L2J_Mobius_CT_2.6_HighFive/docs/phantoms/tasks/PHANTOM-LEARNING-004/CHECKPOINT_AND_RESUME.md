# Incremental optimization and nightly continuity

## Problem in actual 003
`TacticalModelV3.train(train,tuning,seed)` runs `initialized(seed)` anew and fresh Adam moment arrays. V3 `MAX_EXAMPLES=4096`. 003 Trainer uses frozen one-use AUDIT and independent MSE, but model2 is not a multi-night training optimizer. Existing Director stores episode ledger, not arbitrary optimizer state. This gap is PRIMARY work in 004.

## Required durable state
Introduce backward-compatible `LearningRun`/`OptimizerState` extension, not a replacement for already accepted-model2. Persist atomically at **safe batch boundaries**:
- parent model ID/SHA and exact V3 algorithm/feature/action/reward schemas;
- current candidate weight bytes, optimizer (Adam first/second moments, update count, learning schedule), selected tuning checkpoint, RNG state or explicitly reproducible epoch order;
- immutable replay sample keys and dataset fingerprints, sampler revision/curriculum state, retained per-family counts;
- last committed native episode ordinal, unique keys, generation/owner nonce, training stage/phase and versioned checkpoint chain;
- latest ACCEPTED_TEST (if any) and rejected DRAFTs as separate immutable records.

No false mid-update atomicity. If crash occurs while writing checkpoint, recover *previous fully committed* one and mark unfinished attempt INCOMPLETE. If worker completed but process died before commit, read immutable SQL state; no double optimizer step. Resume must not start from random seed when compatible accepted/committed checkpoint exists, unless explicit new campaign with new ID. Guard against stale parent, changed SQL/schema/actions, forged SHA, deleted file, partial blob. No deletion of 003 model2 or any saved prior. Optional optimizer warm-start from accepted 003 model2 uses fresh zero Adam moments with documented `MOMENTS_INITIALIZED` (no invented old states); subsequent 004 continuation must restore actual moments.

## Two-night-equivalent test
1. New own isolated TEST installation, source 003 checkpoint import with verified manifest; no user checkout changes. Run native session A: fresh events, V3 fit, persisted checkpoint and accepted/rejected outcome; STOP. Hash all SQL/checkpoint receipts.
2. New process session B from same own installation: confirm exact parent model/optimizer/ordinal/replay; produce **new** event keys and a new train attempt. Never reuse prior AUDIT cohort or credits. STOP/cold verify all identifiers.
3. Independent short deterministic test compares uninterrupted offline optimizer steps vs split+resume with same sample order, or documents precisely why safe-boundary replay produces same external result, including RNG and Adam. Bit-exact preferred. Trigger one own test-only worker interruption at safe boundary if permission permits; otherwise report NOT_RUN, no fake success.
4. New unlearned class family returns `INSUFFICIENT_EVIDENCE` rather than pretending transferred mastery. Checkpoint retains provenance and class coverage.

## Night operation
Reports in Russian show `RUNNING/COLLECTING/TRAINING/DRAFT/ACCEPTED_TEST/PAUSED/STOPPED/FAILED`, recent useful episodes/h, last new event, current family/class coverage, latest model SHA, whether learning resumed or started fresh, quality per family/class, disk/CPU/heap, no-improvement streak. `STOP` must not delete data; `RESUME` after overnight gap continues exactly at campaign boundary. Never promise Windows sleep/hibernation keeps work running; after boot must explicitly resume unless separately implemented/authorized scheduler.
