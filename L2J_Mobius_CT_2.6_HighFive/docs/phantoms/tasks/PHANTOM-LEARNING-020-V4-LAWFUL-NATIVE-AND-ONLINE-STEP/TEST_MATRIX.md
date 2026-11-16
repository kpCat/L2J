# Минимальная матрица тестов (без 15-минутных прогонов)

## Перед единственным GS: только DB-free/короткие probes
1. Подлинный candidate019, exact SHA, веса161 и ledger — загрузка без изменения истории.
2. 016 sealed scene: checksum, class/skill/inventory/source constraints, 2 bounded candidate, без выдачи старого pre-CUT за live.
3. Offline `V4 score → stock fallback` для плохой модели, missing candidate, stale actor/skill, critical HEAL, MP/Geo отказ. Не маскировать неожиданное изменение игровой policy.
4. Positive/negative `ActionFeedbackV4.nativeAction`: чужой owner, duplicate event/episode, late/cancelled receipt, foreign split, missing settlement → reject. Один будущий receipt только после real GS.
5. PS syntax/owner bound, process identity + not just exit0. Синтетика может проверять safety, но не G3/G4.

## Native ONE boot ≤8min
- Preflight root/SQL ownership, 016 candidate marker, JAR origin, Geo, native owner; private loopback ports и проверка, что они свободны.
- Within one boot: frozen candidate1 then optional candidate2 if no CUT and exact native cleanup, each new target and actor, total deadline same; no second boot.
- On each: stage before-CUT, legal `Combat.startSession`, V4 `consulted` → selected lawful skill → native confirmed paid HP/MP → post-drain settlement. Stop after first complete proof, do not soak.
- No default automatic phantom population, production server, live real customers, spells cheats or spawn shortcuts.

## После native ≤2min каждый
- Authenticate new V4 source, update only train split, new immutable candidate, separate-JVM cold restore and replay unchanged.
- Short `RunOnce/Status/Stop` idempotency: interrupted-before-CUT, interrupted-after-settle-before-save, stale owner, old evidence imported again, missing checksum, tamper and unsafe restart.
- Focused Ant `phantom-learning020-test` + `jar` only if changed; relevant `019` and `016` focused tests when their contracts affected; targeted PS tests; compile/encoding/diff. Individual ≤120s, total verification ≤12min.
- Report elapsed/wall, own process status and privacy proof; separate source unchanged hashes. Do not run `ant test`, 51/72 episodes, 015–018 full suites or multi-hour native.
