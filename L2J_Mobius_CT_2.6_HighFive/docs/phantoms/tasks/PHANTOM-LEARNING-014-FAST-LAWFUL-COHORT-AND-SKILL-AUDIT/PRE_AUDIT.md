# Исходный аудит перед014 (проверено GitHub на 42d6b675)

## 1. Подтверждённая база

- `feature/phantom-world` `42d6b675a9cf57faa4df8a74cc52a8b4adda97fa` — на момент задания. Если ref двинулся, Codex обязан обновить baseline по свежему GitHub, не перезаписывая локальную работу.
- 013: `docs/phantoms/tasks/PHANTOM-LEARNING-013-AUTONOMOUS-LEARNING-LOOP/F3_RESULT.md`, `F3_EVIDENCE.json`. TIMEOUT 992298ms; 5 partial admission, 0 full TRAIN, 0 CUT/FIT/new EVAL/GS. Worker/SQL exited, old Adam/SQL preserved.
- 012: `docs/phantoms/tasks/PHANTOM-LEARNING-012-OUTCOME-AWARE-EVAL/R1_RESULT.md`: true V2 EVAL four terminal-comparable pairs incl deaths; GAME_ADVANTAGE INCONCLUSIVE; not a dataset for TRAIN014.

## 2. Известные кодовые точки

`test/java/org/l2jmobius/tests/phantoms/PhantomAutonomousTrainingNative.java`:
- `prepareSources(...)`: source SQL/actor repro + `discoverSources(...)` + `trainingSources013(...)` + `planCells(...)` + `admitHeadlessCell(...)`;
- `planCells(...)`: up to48 proposal iterations, source/class visits, candidate scenes, roster variants, native admission callback; reuse with exact source/config/JAR stamps;
- `admitHeadlessCell(...)`: STOCK and LEARNED passive admission with exact physical parity, fresh incarnations, headless artifacts;
- `prepareAcquired(...)` called in `collectEpisode(... admissionOnly=true)`;
- `training-admission013.json` sealed only after planning. Minimum: 3 cells for 51+ episode reserve (else4), 2 classes and both Solo/Party scopes;
- `nativeProbePage(...)`, `historicalFence013(...)` and `reproduce(...)` protect no-cherry-picking, identity and historical exclusion;
- no explicit per-stage timed cost in F3 proof: root bottleneck must be measured first.

`test/java/org/l2jmobius/tests/phantoms/PhantomLearning001ShadowNative.java`:
- `Runtime.prepareAcquired(...)`: materialize roster, align party anchor, native recovery; bounded wait up to `preparationSeconds` (typically180) with native MP/HP (not artificially set).

`java/org/l2jmobius/gameserver/phantoms/learning/AutonomousTrainingV1.java`:
- `cohortSlots(...)` uses native A/B TRAIN controls, TUNING/AUDIT. 51 episodes/3 cells => 17 per cell. Do not change statistical contract to fit an arbitrary clock.

`java/org/l2jmobius/gameserver/phantoms/combat/PhantomSkillInventory.java`:
- `acquired(Player, catalog)` from actual Player.getAllSkills; category, skillId, actual level, enchant metadata, source/effects, `eligibility` checks owner, target, cast, costs, Geo/LOS.
- supported TRAIN currently only SINGLE_TARGET_DAMAGE and simple HEAL; AOE/CONTROL/BUFF/SUMMON etc may be discovered but NOT_SUPPORTED_FOR_TRAIN.

`java/org/l2jmobius/gameserver/phantoms/learning/TacticalPolicyV3.java`:
- at most16 candidates, margin .025, cooldown2s, decision5ms; DAMAGE checks actual Monster/legality and HEAL target ranking. A strict mask means absence of choice is not model failure.

`java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyTactics.java`:
- `addMaintenance`: missing/expiring `combat.buff|song|dance`, actor MP floor35%, phase priority, caps; cast via native Party owner.
- `L2jPhantomPartyBackend.realCapabilities`: real learned skills, native capability, actual recipient and readiness; support status via `PhantomSupportEffectAuthority.status` comparing exact/abnormal.
- **Не доказано** что contextual ranking оптимален для физического/магического/гибридного получателя. Не утверждать это без проверки и не менять сейчас.

## 3. Ограничения проверки

Данный документ отражает только read-only публичные исходники на указанном HEAD. Нет доступа к локальным приватным SQL и runtime; Codex обязан заново подтвердить реальные пути/порты/PIDs/веса и READONLY подтверждение. Исторические 013 manual gate owner acceptance оформляется в отдельном scoped receipt, если AGENTS требует; только сам владелец авторизует статус.
