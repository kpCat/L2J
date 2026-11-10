# Learning architecture V1 — компактное настоящее обучение

## Слои
`Observations (read-only, exact native leases) -> LegalActions (existing combat owner) -> FeatureEncoderV1 -> SharedNeuralPolicy.score -> existing action dispatch -> VerifiedOutcome -> Bounded Evidence Queue -> Separate Trainer -> versioned MariaDB model -> immutable loaded snapshot`.

## Модель
Начальный тип: компактный чисто Java MLP с 128 входными компонентами, 24 скрытыми ReLU, одним числовым score для `(state, proposedAction)`. Один и тот же набор весов для всех профилей/классов и обоих PvE режимов. 128x24+24+24+1 параметров, хранить float32 с конечными bounds и сильной проверкой fingerprint. Утвердить fixed normalization/missing mask/feature groups; можно заменить размерности только после mini-benchmark. Не путать 128 компонент с 128 materialized target.

Training: bounded supervised/contextual update (`reward` от реального атрибутируемого результата) с зафиксированным seed, gradient clipping, L2, early stop по held-out, отсутствием NaN/Inf; фиксированный размер batch, best checkpoint, недопуск плохой версии к promotion. Не называть MLP «обученной» за одну созданную random инициализацию. Синтетические fixtures допускаются для проверки математики; `NATIVE` sample размечается строго по evidence.

Exploration: OFF/SHADOW/ON. ON only when approved model version and sufficient evidence, bounded deterministic epsilon in TRAIN-only. Default OFF in user/normal server. Score deterministic for same state/action/model/seed. ALL candidate actions must already satisfy ordinary lease/learned/cooldown/MP checks, no extra target selection. If only 0/1 eligible skill or model invalid -> legacy. Even when ON scored 2+ learned skills, old fallback normal attack remains. No SQL/compilation/inference RPC in tick.

## Context feature groups (mandatory V1 schema and masks)
1. Actor level, classId (hashed categorical), active class, proficiency/capability family, HP/MP/CP fractions, vitals, defensive and offensive stats.
2. Item/equip facts readable through exact actor owner: grade/weapon kind, enchant, shot usability, armor categories, magical/physical emphasis; buff/debuff categories, durations/count/status, current CC. Unknown -> separate present mask, never invent opponent private gear.
3. Party: SOLO/PARTY, memberCount, role composition counts, group level min/max/mean and differences, HP/MP aggregates, endangered ally count, party focus, heal availability. All roster snapshots only if permitted by owner/epoch.
4. Target: NPC type/template id as hashed category, level diff, current HP fraction, physical/magical defenses or safe projected proxies, resistances observable, enemy group/aggression/assist count, observed casts/attack target. PvP placeholder for visible player class/equip/buff facts but no illegal peeking.
5. Geometry: actor-target range, height delta, LOS, walkability, current movement/cast state, within-range flags; richer path/terrain inputs later, never do pathfinding inside hot loop.
6. Proposed action: actual skillId/level, family, element/type, MP/cast/reuse cost, range, target type, shot effect availability, normal-attack token if lawful.

No new separate models for classId, partyId, playerId, NPC id. Feature hashing bounded with signed hash and categorical masks; at minimum 30 meaningful source facts across groups and deterministic unit tests that changing class/level/equip/buffs/role/NPC/position/action changes encoded context where observed. Include schema Version & fingerprint; no silent reinterpretation after data/skill catalog change. Only readable facts — no hidden opponent inventory or precognition.

## Attribution, data provenance, training and reward
Each sample has `mode, policyVersion, featureSchema, actionId, actorProfile (training only or salted), actorObjectId, epoch, combatGeneration, actionSeq, targetObjectId, targetSpawnGeneration, outcome, evidenceKind, timestamp`, unique event key. Need exact `ISSUED` and native completion/target damage/reward receipt; valid expiration and no overlap for same actor action before using as labeled evidence. Never use Party total damage as actor-specific action credit. Record refusals/no-proof as UNKNOWN and skip weights. Native non-game effects/phantom Background rolls are not labeled actual PvE action result.

Initial bounded reward function for combat-skill choice: positive verified personal NPC damage with relative HP/expected damage, discounted by MP/time/item cost and immediate survival consequences; bounded [-1,1] and no positive award for mere attempts. Use terminal rewards only when attribution justified; avoid circular Heal or intentionally taking damage for credit. No learner mutation on realtime game thread; training worker run as separate CLI invocation / controlled training mode outside GAME hot path.

## MariaDB/global state
Create `phantom_ai_model_versions` (model_id, algo_version, schema_fingerprint, step, training_hash, weights_blob, SHA256, validation metrics, status DRAFT/ACCEPTED/REVOKED, created_at) and `phantom_ai_training_examples` (unique event_key and exact evidence type, feature version+bytes, action+reward, origin NATIVE/SYNTHETIC, timestamps, bounded retention). Existing installer script path `dist/db_installer/sql/game/phantom_learning.sql` or adjacent established pattern. Never overwrite previous accepted weights. Atomic accept/promote by status CAS, load only verified accepted version and reject corrupt/unknown. Only authorized own TEST DB, no user data migration. Distinguish SYNTHETIC samples for math verification; no representation as real native observation.

## Future seams already defined, not implemented now
`LearningObservation`, `LegalAction`, `Decision`, `Experience`, `ModelVersion` can represent HEAL, RESURRECT, MOVE/KITE, SPOIL/SWEEP, PvP/PK, raids, sieges, Olympiad. Include `mode` enum and action-type union without manually enumerating all future actions. Later Training Director learns game dynamics, group plans and self-play league. No architecture rewrite required if V1 legal action family restricts to native PvE offensive skills.
