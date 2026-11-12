# SAFETY — границы контроля и воспроизводимости

- Git: only independent clone and `wip/phantom-learning-005`. User checkout `D:\Codex\repos\L2J-phantom-world` strictly READ-ONLY; previous004 clone and historical TEST read-only. No resetting, cleaning, stashing or force pushing other branches.
- TEST physical: own SQL/datadir, ports, configs, GameServer clone, own actor profiles only. Never MAIN/PLAY, no `l2jmobiush5` writes, no reuse external game account credentials. G0 Geo203/protected207 read-only SHA. No local privileged changes.
- DRAFT8 not allowed to control actual game. Accepted V1 model1 and V3 model2 only after exact SHA/status/schema validation on owned TEST. `apply-test` remains explicit; no code to bypass admission, no auto promotion/ON in source defaults.
- No `setCurrentHp`/`setCurrentMp` after CUT or modifying skill/monster power, rates, spawn config, XP, RNG or game damage to improve results. Pre-CUT setup must be disclosed and matched.
- Only native valid targets/instances; no fake LOS, Geo bypass, movement/teleport after CUT, no false level/gear/skill eligibility. Controls `Party/Combat` own actions, model only ranks allowed alternatives.
- No private enemy data in model observation, no hidden NPC property outside lawful API. No human live players or forced PVP/PK.
- Timestamp/actor/model identity and event attribution; exact effect receipts. Incomplete/abort as own state, not dropped. Missing fact UNKNOWN and not zero.
- Must not use comparison episodes as secretly reused TRAIN/TUNING/AUDIT: evaluation split/lineage explicit, no leakage. No duplicated credit on resumption.
- CPU budget: bounded test actors and rare decisions; no per-actor thread, no SQL writes in combat tick; preserve stock fallback if 5ms. Fail-closed on server identity mismatch or missing protected/Geo data, report reason.
- No past checkpoint weights/optimizer modification; no binary secrets/data/logs in git. Final stop every owned process, verify ports actually free and retained ledgers.
- Restrict third-party/scenario resources to existing game files with stable provenance; do not browse or generate unrelated narrative semantic data.
