# Phantom World roadmap — learning added WITHOUT erasing old tasks

- `TASK130 PHANTOM-WORLD-COORDINATION-130`: FROZEN/PARTIAL, historically true playable Party/GK/PvE/reward/Heal/loot/STORE evidence on successive JAR, last accepted JAR not FULL130 A–G. Preserve `wip/coordination-130-preserved`, RESULT, raw hashes and unresolved ownership/risk/replan caveats. Not claim FULL, not discard code, not continue countless clean-JAR runs. Needed production bugfixes later reviewed and integrated explicitly.
- `PHANTOM-LEARNING-001` **THIS**: shared trainable neural tactic action scorer, actual integration to solo + Party PvE skills, SQL versions, evidence gating, deterministic holdout and bounded benchmark.
- `PHANTOM-LEARNING-002`: autonomous isolated GameServer Training Director running independent of Codex / terminal, managed start/pause/status/stop, reproducible legal scenario generation, feature coverage, session checkpoints. Separate owned MariaDB and safe control. No direct production DB injection.
- `PHANTOM-LEARNING-003`: league/self-play on actual available Solo/Party PvE/PvP/PK modes, varied class/level/equip/buffs/team/terrain, evaluation with held-out scenarios, avoid one-sided weak policies, async batch training; gameplay modes not implemented must be marked unavailable, not fabricated.
- `PHANTOM-LEARNING-004`: model export/import and rollback (weights+schema+hash+metrics), independent regression, 128 active materialized performance, controlled promotion to real Phantom World; no copy of training players/items or DB.
- `TASK131`: enhanced Party PvP/PK/group Spoil/Sweep, resurrection, group tactics and optionally healing/movement policy action families; integrate into ONE shared learner, don't train model per Party.
- `TASK132`: clans, leader rights, alliances, clan wars.
- `TASK133`: raids, Rift and instances.
- `TASK134`: sieges.
- `TASK135`: territory wars.
- `TASK136`: Olympiad.

Each future gameplay mode adds legal action operators, observations and training scenarios to shared learning schema, not a separate AI clone. When integrating, revisit frozen TASK130 remaining A–G as a focused readiness gate, without constraining LEARN-001 with a 7-day PARTIAL loop. New Task ID = new Codex chat and new concise handoff. Every Codex report must be verified against actual GitHub branch and diff before drafting next task.
