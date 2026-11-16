# Совместимость, чтобы 022–024 не ломали уже работающий тренер

Абсолютной гарантии «никогда не сломается» не бывает. Здесь реализовать **версионирование + регрессионные контрактные проверки**, чтобы несовместимые изменения обнаруживались перед публикацией.

### Неизменяемые исторические артефакты

- ACCEPTED_FULL020 на HEAD `30a7a74dd4f4777c8f8cd2670e12e3b37e2b34f8`.
- `candidate020-42cfff5c02fd386a31ab1e95d4234a8eb22549a62debd729a3fc9a7a602ef96c.bin`, V4 updates179, old checkpoint019 SHA `569295cf4d8f4172eee0e8a7e530246534dd1cf24d34ab95e07ded65c6b2f061`.
- Native 020 original episode, 18 paid samples; 016 data/247 import, V3/Adam903/223, TEST2, DRAFT14, pointer12, original SQL183 historical SHA `9A9C2D0EE9A2B8F37DA5DD6BCF625334B76BE96FE9B76E22490ED20B4DA70E7D`.
- Geo203/protected207, private MAIN/PLAY, Studio concurrently committed files, historical budgets/owner journals.

### Защитные регрессии

1. Legacy 020 raw bytes & `ActionLearningV4.load`: exact SHA179 + score for recorded alternatives unchanged; historical fit replay0; no credit duplicate.
2. 021 new experiment policy OFF: V3, V4/old stock and other families identical to pre-021. `TacticalPolicyV3.actionValueTest` TEST-only guard, ordinary production remains OFF. No automatic Explore if mode disabled.
3. New policy version mismatch: fail-closed, unknown family metadata ignored by older trainer or routed to baseline; no reinterpretation of old 020 selected action as experiment.
4. Protected legality: `LegalActionV3.trainable`, actor/owner/target epoch, candidate set, condition check, native MP, LOS/Geo, safety, emergency HEAL/critical, 5ms budget unchanged.
5. Adversarial: duplicate eventKey/different fingerprint, tampered propensity after CUT, selection mismatch, stale model/owner, fabricated raw receipt, random draw committed after outcome, switched split TRAIN↔AUDIT, interrupted write, two concurrent owned runs, corrupted partial checkpoint → never training credit.
6. Future family adapters can be added additively; no hardcoded `class94` or `npc20966` inside reusable production policy/record. These IDs allowed only within TEST fixture021/020.
7. Test results distinguish `EXPERIMENT_REORDERED`, `MODEL_REORDERED`, `STOCK`, `FALLBACK`. A single experiment cannot claim general superiority or change production model by itself.

### Source/publish rules

- Only feature/phantom-world, no force, exact scoped commits, `git diff --check`, readback GitHub HEAD/parent/changed paths/full relevant bodies. No unrelated mass EOL changes.
- If compatibility requires mutation of old `ActionValueV4` byte codec, `ActionFeedbackV4` or historical SQL: NO_GO without explicit owner approval; prefer additive new schema.
- No nightly/8h soak by Codex, no operational autostart from 021. 022 uses these same contracts; later extensions must rerun them.
