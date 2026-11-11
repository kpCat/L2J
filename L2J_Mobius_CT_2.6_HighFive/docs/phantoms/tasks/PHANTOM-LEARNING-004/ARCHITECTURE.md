# Preserve, extend, don't rewrite

Existing chain: `native Player+Party+Skill+Geo → stock legal candidates/lease → V1 DAMAGE (001/002) OR V3 HEAL (003) → exact native receipt → owned MariaDB → offline fitting → explicit TEST admission`. V3 `TacticalObservationV3` has one SELF plus bounded ALLY/SUMMON/ENEMY, typed actions and known masks; `TacticalTimingV3` captures native cast/cooldown/availability. Keep these constraints and unknown handling. 

Target chain: `existing OWNERS (Combat/Party) generate legal V3 candidates → shared immutable V3 checkpoint ranks comparable candidates within owner → identical native Combat/Party execution with final legality recheck → per-event receipt and per-episode summary → durable append-only owned TEST ledger → offline replay sampler + incremental optimizer → immutable candidate → fresh independent evaluation → explicit TEST admission/pin → cold V3 loading`.

Owner boundaries:
- Combat and Party remain ONLY native action owners. Model cannot order actions outside lease, directly edit HP, teleport, reset cooldown or choose illegal enemies. No new PvP/PK authority. Emergency heal always stock priority. V3 DAMAGE should not create a second competing offense owner; V1 unchanged OFF/rollback.
- V3 model shared one immutable reference per JVM. Inference CPU native path, no SQL/LLM/GPU IPC; feature extraction bounded. Implement state encode once per current decision, then rank <=16 lawful actions. Avoid every-tick forced encoder for 128 bots; invoke at meaningful decisions and record invocation rate.
- V3 compatibility: old `TacticalModelV3` weights/manifest fingerprint/accepted model2 continue to restore exactly. If new action feature schema changes, version it explicitly, add a validated compatibility mapping; never patch existing bytes or silently decode incompatible vectors. Historical V1 vectors CANNOT be relabeled V3 native evidence.
- V3 runner in existing `Training.ps1`, `TrainingV3.ini`, Java `PhantomTrainingNative`/`PhantomLearning003Native` and existing Trainer/Store. Avoid separate Windows worker launcher or a duplicate MariaDB manager.

Attribution: one real skill may generate multiple effect hits (e.g. future AoE). In 004 DAMAGE only proved for existing legal single-target skills. Attribute actual useful native damage to skill/target/spawn/cast; no multi-target assumption. HEAL considers useful restored HP, not overheal; critical heals and randomized crit are genuine outcomes. Never share private enemy buffs/gear in PvP future; UNKNOWN is first class.

Shared score across family does NOT prove capability to arbitrate attacking vs healing. Within Combat score DAMAGE candidates; within Party score lawful non-critical HEAL. Strategic owner-level arbitration remains stock. Explicitly flag this limit in runbook, report and next task.

Future group fights and farm: PvP 1×1, 1×2, 2×1, N×M and multi-sided skirmishes; native `Death/escape/return/CC/summon/AoE` receipts; farm ×Mob group/EXP+SP/drop/economy; raid boss. All attach via typed action and episode goal contracts but do not implement them now.
