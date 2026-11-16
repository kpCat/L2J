# Порядок проверок — короткие, прицельные

## 0. Read-only admission (≤15мин на аудит+код, без GS)

- Сверить private TEST OFF, ownership, source image 016 и checkpoint020=179/SHA; новый physical TEST root и private DB; native source SQL только private copy.
- Показать на коде single decision-owner в `TacticalPolicyV3`, разделение старой exploration и V4 и отсутствие прямого production enable.
- Source regression `phantom-learning020-test`, минимум нужных 019/016 focused tests — точечно, не общий verify. Отдельные пределы <=120s на вызов; сборки в суммарный бюджет.

## 1. Offline RED→GREEN

- COVERAGE_TEST_V1: заранее фиксированный deterministic order выбирает одну safe alternative (не stock) с `p=1`; после quota следующие решения stock с p=1. Не выдавать coverage за randomized epsilon/IPS data.
- Known legal stock+alternative (class94 fixturе), probability eps `0.15`, `k=2`: stock P=.85, alt P=.15. При `k=3` alt P=.075. Проверить sum=1 и decision до native execution; после exhausted cap BASELINE p(stock)=1. Для deterministic policy revision честная propensity=1, не выдавать за epsilon draw.
- V4 normal score often `MARGIN` and stays stock. Experiment must explicitly override only **safe TRAIN test** with mode `EXPLORE`; not fake `MODEL_REORDERED`.
- Heldout/critical/MP-low/owner-loss/Geo/LOS/current-cast/unready skill/stop/cancel/budget cutoff all fallback. No `TRAIN` credit for unexecuted or tampered selection/receipt.
- Cold loading V4 parent179 exact; repeat input replay0. Corrupt new receipts/SHA/selection/seed/split/owner mismatch fail closed.
- Consumer compatibility test: 020 source/score bytes unchanged, 020 receipt restore and old checkpoint recognized; no altered legacy codecs.

## 2. Native (≤1 GS, ≤8min including boot/drain)

- Reuse original020 sealed class94 source image→`reproduceRoster016`→natural180 recovery→TEST positioning/Geo/LOS→native admission→CUT. Fresh private owned root, new fresh episode, no extra skills/HP/MP.
- Prior to CUT commit policy/seed/experiment cap (COVERAGE_TEST_V1 — для единственной native-проверки, EPSILON_V1 — хотя бы offline математический контракт); for each eligible choice record mode/draw/propensity before the native skill call. No per-cast fsync inside 5ms budget; bind in post-drain immutable ledger. **Do not seed-hunt after seeing outcomes**. If no safe non-stock alternative occurs, do not fabricate exploration or force illegal action; report exact counters and PARTIAL.
- Require at least1 `EXPERIMENT` alternative selected/executed by native and at least1 `BASELINE/MODEL stock` execution if safe. Bind event IDs and exact receipts. Native not equal to model reorder.
- After stop/settle: new source SHA, native paid receipts, TRAIN update179→higher, atomic save, cold separate JVM, replay0; status returned.

## 3. Final check and delivery

- Verify old 020 native result, V4 179, V3/Adam and historical SQL/Geo hashes remain unchanged; no unauthorized source/prod code and no leaked secrets in public artifacts.
- `git diff --check`, scoped Git commit, ordinary push, independent GitHub readback. Stop owned GameServer/MariaDB; ports free. Report measured time in each phase, including failures.

**No long tests:** offline each<=120s, automatic verification total<=12min, one GS<=8min, no nightly tests. If a build tool's `compile-tests` repeats an expensive broad build, use an existing focused compiled artifact when safe; do not skip changed-source compilation. No unbounded retries.
