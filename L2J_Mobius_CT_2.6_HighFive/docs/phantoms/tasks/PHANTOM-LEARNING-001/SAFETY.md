# Safety & operating boundaries

- High Five ONLY. Never write to `D:\Codex\repos\L2J-phantom-world`, MAIN/PLAY/TEST129, `feature/phantom-world`, TASK130 WIP, any actual player account, original TEST130 schema/3308, Geo203 or protected207.
- Dedicated independent clone and **own isolated disposable MariaDB** for schema and training. Training does not modify user game characters/progression, items, adena, deaths, karma or world. No fake XP/SP/loot, no bypass skill checks, Combat/Execution ownership, CAS, human-party rules, F7/F8.
- Never expose DB credentials, tokens, actor identifiers in exported model. Training data may be local TEST only; no sensitive records. Model package only contains weights, schema fingerprint, aggregate validation and checksum.
- No self-modifying Java, no script execution chosen by ML, no unconstrained action strings. All decisions pass original legal action generator and native verification.
- Bounded ML inference, logging, DB writing and training memory. Backpressure can drop training telemetry but NEVER block critical Combat processing or hide an actual gameplay error.
- Separate, optional training step. New untested weights cannot be automatically deployed to real players. OFF default; SHADOW for comparison; ON only on owned isolated TEST after explicit owner mode.
- Full G0 for any GameServer: exact owner PID, FILETIME, SQL datadir, loopback, schema/grants, no third-party listeners, clean protected SHA, canonical STOP with Phantom World stopped=true, clean SQL postflight. Never terminate foreign processes.
- No force push, hard reset, destructive cleanup, hidden permission changes; use scoped git add. Direct owner permission to push only `wip/phantom-learning`.
- Preserve original game semantics for PK/PvP and all unmodified modes, including negative/human tests.
