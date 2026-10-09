# SCENARIOS029

Each test failure must be semantic, not compile/setup. Negative control assertions survive.

## B — Background numbers/capabilities
B01 native and existing model reject non1 on base; new ordinary V2 supports each of:
1,240,241,2000,2001,13000,13001,16361,17000,17001,20000 and421.
Native oracle uses actual PlayerStat and Attackable reward/points entry points.
B02 thresholds crossed within batch: bonus before loss, float loss order, clamp1/20000,
zeroXP, lucky, ENABLE_VITALITY=false, useVitalityRate=false, zero/negative consume.
B03 old CombatFacts bonus already includes vitality: prove no double application;
BONUS_EXP/SP and caps explicit, no division out of capped factor.
B04 level-up during award: subsequent vitality calculation uses proper native level;
next encounter revalidates model facts. Split-at-boundary vs equivalent unsplit vector.
B05 unknown policy, expired effects, premium/party/summon unsupported V2 contexts don't
get ordinary FARM; native persistence still works; existing supported legacy semantics kept.
B06 operation-specific: points16361 alone doesn't block safe no-reward TRAVEL. POSITION
invalid blocks FARM but valid return path permits TRAVEL without pretending FARM happened.

## T — Handoff atomicity
T01 originXYZ preserved at native FINALIZE; travel changes position only after accounted
elapsed path completion. No XP/SP/items/vitality gains from that travel.
T02 invalid/water/no-forward/mismatched-instance path not moved or normalized.
T03 farm commit writes canonical vitality, rewards, background policy/state and exact
catchup interval once in same transaction. Foreign items/skills/state negatives unchanged.
T04 new native demand / materialization races with background calculation or CAS: no dual
writer, no double award. Real code/locks, not a mocked boolean returning false.
T05 PNC1/PNC2 roundtrip and all COMPATIBILITY fault windows; preserve old pending receipts.
T06 replay exact operation id after ambiguous connection => IDEMPOTENT with unchanged total.

## P — Lifetime evidence
P01 enrollA→checkpoint→switch telemetryB→terminalA. All original A receipts exported.
P02 same profile/new epoch: separate rows, no epoch rewrite; previous terminal remains.
P03 temporary SEALED+FINALIZED isn't terminal DETACHED. Missing terminal rejects wholePASS.
P04 late SQL changed via background: exact receipt→linked txn→restart matches; missing
middle event remains UNPROVEN, not repaired from late values.
P05 bounded ledger/queue reject overflow, duplicate conflicting receipt, missing final
and negative receipt IDs; duplicate identical export is idempotent only, not extra progress.

## R — Existing unresolved028
R01 cooperative route11 controlled real late entrant; existing negative stale-owner test.
Classify base/current and perform correction only after exact failure. Require 92/92.
R02 first stop after full run and after EACH of two no-Synthetic restarts. Capture exact
first shutdownProgress before pools and every pending dependency. all healthy required.
R03 returned whole cohort includes old-type994/1159 route case without IDs in code:
correct nativeXYZ+stale local goal -> permitted exact replan only when live alternative exists.
R04 repeatable alternate visible tactic test changes selected move/skill profile, not
native ownership/store receipt contract. Test seam, not new PvP/summoner feature.

## Product gates on one frozen source/JAR
F01/F02 two stationary natural4..8 scenes360–420s, unchanged028V2 and legacy score.
F03 one enrolled4..8 away→absence→actual background FARM→return. >=1 committed productive
background batch for each preselected ordinary primary with no Player in World during txn.
A metadata change/travel-only/forced manual invoke is not productive FARM.
Same scenario must independently account all nonprimaries and their eventual native work.
F04 primaries>=5 post-return cycles each in correctly segmented new epochs; other healthy
returned members>=2 or completed state-appropriate recovery with actual subsequent farm.
No primary-only PASS if other enrolled members stuck/missing unexplained.
F05 union of all observed native lifetimes acrossF1..F4 has terminal/permanent+receipt proof.
SEALED→SQL at correct commit boundary; all later state changes have lineage; sameDB restart2.
F06 final typed first shutdown COMPLETE before pools, pending0 retained0; all old
failures retained as old evidence. No force counted healthy.
F07 actual finalSHA AFTER_NATIVE and AFTER_FINALIZE crash/restart plus stale/wrong-owner
negative controls after codec/transaction changes; all intermediate phases recorded.
