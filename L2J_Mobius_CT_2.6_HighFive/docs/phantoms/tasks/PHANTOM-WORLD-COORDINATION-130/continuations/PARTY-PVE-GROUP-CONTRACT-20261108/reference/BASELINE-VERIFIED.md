# Baseline source and evidence — checked 2026-11-08

Remote: https://github.com/kpCat/L2J
WIP ref: `wip/coordination-130-preserved`
WIP SHA: `8140167705e6a6e76f370e3aa772b50f909b4d37`
Feature SHA: `faec72161f9e4f6231764e17d6f2637a4db080af`
Prior useful source commits: `4fa72128b61c5b4c455d0fc2322e9c5c126c9939` (native authority recovery), `f7fca59b2d6f50fbdae4e265e3d92b5a7f4d911a` (conservation/preflight, intents). Last reported commit `81401677` adds only reports, not prod files.

Current final verified candidate JAR from Codex report:
`16699C6CDA5DDC84309B34D1801791CFF09F75A32B66F4D79AFC00085059C1C3`
1 clean build, 4736 classes parity, one GameServer bootstrap, no restart. This is baseline JAR, NOT a substitute for final JAR built from the new group-role code.

Native GameServer facts (user supplied Codex report + GitHub evidence):
- current Background knowledge restored on profiles 70/72 from `87fafd93...60be4` to `41edd0de...5c999` using actual Player materialize/HISTORICAL_BASELINE native STORE/readback; COMPLETE Catchup and goals unchanged; 207 protected and Geo203 exact.
- Party planner: 70 sources0, 72 sources58; `NATIVE130_NO_LAWFUL_FEASIBLE_SCENE` before CUT; native Party kills/heal/restart0. 58 source keys (NPC@anchor), NOT 58 NPC ids.
- Both level76, same position (170156,-36726,-3376), committedAnchor `generated.farm.38520d85788548d726048a27.anchor`.
- Profile70: Phoenix Knight class90, HP4556 MP1479 CP2733; PAtk684.0723 MAtk235.7827 PDef790.995 MDef216.592; Power Strike 3:9 MP17, Aggression 28:49, Sacrifice69:25; Forgotten Blade6364 S+0.
- Profile72: Cardinal class97, HP3119 MP2480 CP2807; PAtk492.3934 MAtk999.3814; native Heal1011:18, Greater Heal1217:33, Major Heal1401:11; Imperial Staff6366 S+0.
- Both consent eligible when last native started. Calendar windows dated last attempt; **must re-evaluate at actual future boot**.

Root-cause evidence:
`PhantomHistoricalBackgroundPlanner.rendezvousSources` queries level [L−10,L+2], branches on logical support; all other roles require Solo viableTarget. For 70+72 same significant target/route inputs, that difference accounts for observed source-set discrepancy. Specific mechanic MP/death/timeout blocker unknown and NOT needed to prove incorrect GROUP admission.

Original TASK130 proof gates and rules remain in:
`docs/phantoms/tasks/PHANTOM-WORLD-COORDINATION-130/GOAL.txt`
`docs/phantoms/tasks/PHANTOM-WORLD-COORDINATION-130/ACCEPTANCE.md`
`docs/phantoms/tasks/PHANTOM-WORLD-COORDINATION-130/reference/ARCHITECTURE-CONTRACT-002.md`

External raw evidence from Codex local visualization path may be available but is not in this ZIP; do not claim it was included. User will provide Codex result; ChatGPT independently checks public GitHub.
