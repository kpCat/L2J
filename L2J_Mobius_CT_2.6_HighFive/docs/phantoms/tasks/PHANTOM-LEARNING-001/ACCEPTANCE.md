# Acceptance gates — PHANTOM-LEARNING-001

| Gate | Mandatory result |
|---|---|
| L0 Git | Correct source SHA + OWN isolated clone + `wip/phantom-learning`; protected MAIN/PLAY/TEST129, feature & TASK130 WIP unchanged; scoped normal push |
| L1 REAL LEARNING | Actual trainable MLP changes weights; deterministic before/after and independent held-out improvement on at least 2 class/level/equip contexts; not hardcoded conditional scores |
| L2 STATE CONTRACT | Versioned bounded feature representation: actor/class/level/gear/buffs/Party size/roles/target NPC and level/position/Geo/action skill; unknown masks, no hidden opponent data |
| L3 REAL INTEGRATION | `PhantomCombatService.issueAction` uses common model to reorder 2–4 lawful skills in Solo AND Party-owned PvE, not merely call an isolated mock; session owner/epoch/skill caps enforced, safe fallback, one-skill & OFF exact old behavior |
| L4 LEARNED KNOWLEDGE PERSISTS | Shared versioned model and evidence in TEST MariaDB; model SHA/readback, exact restart/reload returns same learned preference; corrupt / stale / wrong schema fails closed to legacy; no synchronous SQL at action selection |
| L5 ATTRIBUTION | Only verifiably attributable native actor/skill/action/target events can update weights; replay, ambiguous Party damage, stale epochs, Background rolls, bare `ISSUED` do not teach; bounded queue, no duplicate sample |
| L6 SAFETY | F7/F8 & owner regressions, no changing core mechanics, loot/spoil/sweeper, Party/heal, PvP/PK, no free progress; protected files untouched |
| L7 PERFORMANCE | JDK25 128-actor/4-action warmed benchmark, reported p50/p95/p99, memory, no per-bot threads/DB in hot loop; microbenchmark p95 target <1ms |
| L8 DELIVERY | Existing test toolchain; preflight G0 if native, zero owned leftover JVM/SQL/listeners, targeted tests PASS, P1/P2=0, SHA, normal WIP push, short RU report incl. exact limitations |

NO false FULL for model-only logs, recording synthetic traces without neural training or SQL rows without real change to decision, nor gaming held-out by copied train rows. NO FULL130 claim. Avoid automatic scope inflation to Director/PvP.
