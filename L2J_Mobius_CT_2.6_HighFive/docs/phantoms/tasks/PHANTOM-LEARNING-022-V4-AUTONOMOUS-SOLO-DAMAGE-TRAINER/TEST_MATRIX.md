# Проверки в порядке стоимости, без многочасового обучения

## A. Static / offline, без GS и без записи в MAIN

1. Source/physical parent188 + independent cold SHA, idempotent replay0; allowlist и `PRODUCTION_V4=OFF` доказаны. Верификация исходного stale/absent checkpoint отказывает до SQL/GS.
2. CLI argument matrix: `Start/Status/Pause/Resume/Stop/Report`, default OFF, invalid duration/unknown profile, 2 concurrent Start, no-op Status/Report; CLI не поднимает GameServer в статусном режиме.
3. Plan/Decision/Intent/Effect binding: stock vs selected, probabilities COVERAGE1 и EPSILON stock/alternatives, safety fallback, illegal MP/LOS/owner/cast; no receipt/unknown/heldout no TRAIN.
4. Rolling ledger: import 188, key+fingerprint exact replay0, same key changed fingerprint tamper, episode split contamination; batch256 max, 257 reject, segment 4095→4096→new, global counts monotonically, 128 weights exactly preserved, cold both segments.
5. Fault injection: before reservation, after reservation/pre-CUT, after original paid-before-complete, post-complete/pre-FIT, after candidate fsync/pre-HEAD, during atomic HEAD publication, rollover stage, resume after prior pause, corrupt manifest/part, stale PID/reused PID, empty/corrupt source, no available lawful candidate. Test no fabricated fit, no duplicate GS boot, no lost committed checkpoint.
6. Deterministic curriculum: same seed+valid source→same candidate order; alternate source/skills only when lawful; no-data/backoff finite; chosen strategy remains from predeclared legal list; no initial/new skills grant.
7. Time/resource: virtual clock 479→480min auto STOP with no new episode, Resume does not reset budget; disk/CPU/memory cap fails closed. Config immutable while live or applied only at stopped boundary.
8. Regression for accepted 020/021, V3 CLI and old schema; no 020/021 historical root modified. Encoding/mojibake/escaped Cyrillic zero for edited files. Preserve safety/5ms decision selection budget; if p99 truly NOT_RUN report NOT_RUN.

## B. Native smoke — максимум 2 GS boot / 16 native минут

- G0 + A green → reserve first fresh owned TEST root, copy exact trusted data, bind source SQL read-only, approved class94 candidate allowed if no other. Pre-CUT signed policy + owner/skills/Geo/LOS; finite recovery in bounded native time.
- Boot1: ≥1 fully settled native TRAIN episode; if possible, execute **в этом же boot** следующий fresh episode after cleanup/rearm and verify new event/owner/episode IDs. Check >=1 actual alternative and control, original paid receipts, no fake aggregate rewards.
- If one GS cannot safely process 2 episodes, Boot2 разрешён только с documented cause и после OFF/ports/process release. Никакого третьего. Если 2nd lawful source недоступен, PARTIAL без seed-hunting/health injections.
- Between rounds: fit/atomic candidate/cold separate JVM, committed HEAD/readback; second consumes **новый** candidate1, not 179/188. Final verify two incremental checkpoints, global event/episode ledger, no duplicates, report.
- Native `exit=1` либо смерть персонажа записать точно. Crash after native original files may complete **offline only with immutable proof**; как минимум одна сцена с normal process exit=0. Previous 021 `EVAL012_INTEGER_stock` must have offline Number regression.

## C. Финальная проверка и ограничения затрат

- After changed code compiled: focused Java TEST + PowerShell tests only; on failure fix cause and retry failed target. `ant verify` once only when it cannot mutate unapproved DB; `ant jar` once after safe verify/affected green in protected-independent build tree. Never overwrite owner `dist`/private SQL to force green.
- MAX: 2 native boots, each ≤8min, 16min total; no mandatory 480min, no full EVAL campaign, no recurring history replay. Hard stop 150min goal wall; if unresolved, scoped safe report + PARTIAL/BLOCKED, no auto continuation.
- Reports record exact command/case counts, exit codes, timings, TEST process identity/port release, source/parent SHA, batch counts, native receipt SHA, child candidate SHA, stop reason and independent publication. Не считать mock 4096 samples native samples.
