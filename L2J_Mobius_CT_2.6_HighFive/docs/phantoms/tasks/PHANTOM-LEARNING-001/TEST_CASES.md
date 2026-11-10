# TEST_CASES — gate-first, not 18-hour runs

## A. Core mathematical learning (no DB, <3min target)
1. Feature encoder deterministic across JVM run; bounded 128 inputs, no NaN/Inf; no secret player facts; explicit unknown masks. Include actor class, level, weapon, buffs, Party roles, enemy/NPC level, observed buffs/casts, geometry, MP and skillId.
2. Actor/action features are genuinely action-conditioned (two legal skills in same state have different vectors/scores after training).
3. Fixed seeded tiny supervised training dataset with balanced class/weapon/NPC/role mixtures improves held-out action-return over original round-robin by measurable margin; holdout consists of unseen pairings not duplicated training rows. Report actual score and coverage; no claiming general gameplay superiority.
4. Profile 1 learns; different profile with comparable capabilities/context uses same weights and alters native skill ordering. Different hard constraints still block illegal skill.
5. Batch reproducible from seed, same SHA; corrupted weights, mismatched feature schema and untrained model always OFF/fallback.
6. Verify no target selection/goal/party join changes, source no-policy bitwise legacy decision for >1 eligible skill; one skill => identical old behavior.

## B. Integration
7. Real `PhantomCombatService.issueAction` integration test (not wrapper calling score alone): 2+ legal SelectedSkill, trained model changes which skill is attempted first, exact actor/target ownership. SOLO and Party-owned calls both use same policy. Real action may return unavailable; fallback and lifecycle identical.
8. `SHADOW` returns legacy skill and logs bounded considered preference. `ON` only when accepted version. Existing Party `HEAL_MEMBER`, `RESURRECT_MEMBER`, `PROTECT_MEMBER`, `SPOIL/SWEEP` unrelated owners unchanged, PvP/raid/siege unchanged.
9. Capture native receipt with exact actor epoch+Combat generation+target spawn: no duplicate sample on callback replay, stale epoch, own cast cancellation, no target delta, two Party actors hitting target concurrently. No false credit. Zero-confirmed-damage case => no training credit.
10. Restart JVM: clean model loaded from MariaDB same SHA and causes same learned skill ordering; invalid/missing model fallback, no native GameServer boot required for SQL-only reload.
11. DB idempotency and transaction fault injection: no half-promoted policy, unique sample key, bounded cache/queue, crash/duplicate recovery.

## C. Performance
12. Warmup JDK25; throughput/alloc profiling with 128 concurrent actor fact streams (not 5000 hot agents), 4 skills, fixed accepted weights. Report p50/p95/p99 us or ms, allocations/op, model size and total memory. Goal p95 <1ms and no lock/DB in evaluation.

## D. Optional targeted native with strict budget
13. If existing first-party isolated TEST harness can reproduce ordinary PvE without TASK130 full party/GK/restart, launch max two isolated boot with G0/stock STOP/postflight, prove at least one actual accepted skill from model and causal positive NPC damage evidence from exact actor/epoch/action. No fabricated rewards; if no native damage available, label native attribution unproven and report honestly, do not run again and again.
14. No user DB, no main checkout changes, nonowned port/process untouched. Compile + test without native first. Budget max 2 clean JAR, max 2 GameServer boot, no hot fix marathon. Existing G0 must PASS.

## Finish classification
SUCCESS only if real training, held-out improvement, production CombatService integration in BOTH modes, verified SQL model roundtrip, fallback, evidence gating, no critical regressions, performance evidence all proven. Native positive damage proof is desirable; if absent mark exact NATIVE_UNPROVEN and classify PARTIAL only if no other real integration proof exists. Do not claim whole Phantom World AI trained for all modes in LEARN-001.
