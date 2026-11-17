# G0–G8 — честная приёмка Goal023 (offline)

| Gate | Подтверждение |
|---|---|
| G0 | HEAD/branch/current Git scope; owner private data preserved; model216/HEAD and 022 original metadata read-only; no GS/SQL. |
| G1 | STOP policy: current update count may grow while quality degrades; policy blocks further TRAIN when no independent eval. Separate `NO_DATA` from `NO_SUPPORTED_GAIN` and `EVAL_COVERAGE_GAP`. |
| G2 | Native quality cannot be self-certified from JSON, reward, fake holdout or reused TRAIN; no `BEST_VERIFIED` on 2 historical episodes / model reorder0. `REFERENCE_ANCHOR216` immutable; producer not allowed to overwrite best or HEAD. |
| G3 | Stop pre-native admission, post-commit, recovery, restart and duplicate Start are idempotent; default max6 TRAIN before heldout boundary; no bypass via new campaign/Resume; no unauthorized JS/SQL/GS. |
| G4 | Offline fixtures: genuinely independent supported-regression proof pauses; valid matched positive evidence unlocks only TEST candidate status when rules/coverage pass; lacking evidence -> UNKNOWN, **never claimed superiority**. If native genuine comparator not available, tests for 'supported' use synthetic data but **must not mark real campaign improved**. |
| G5 | `report022.html` readable offline, MD, JSON consistent: current vs reference vs best, episodes/paid/splits, quality evidence, status, next required milestone, early stop reason, CPU/disk/time; HTML escaping; no fake 100% bar. |
| G6 | Existing 30 PS checks and post-V2 independent Start/cursor4/HEAD216 safety regressions GREEN; negative tamper/concurrency/clock/deadline/corrupt evidence tests GREEN. |
| G7 | No changes to GAME/production/SQL/Geo/ActionLearningV4 codec/ActionContinuationV4; GS=SQL=native=0, no 480min; past 022 models/evidence unchanged. |
| G8 | Exact scope commit/push feature/phantom-world; diff-check, encoding, independent GitHub HEAD+parent+changed file bodies; concise evidence and future 024 handoff. Manual owner gate REQUIRED. |

`FULL023_TEST_SAFETY_AND_PROGRESS_READY` means the *guard and UI* are implemented and verified offline. It does NOT confer `AUTONOMOUS_V4_8H_QUALITY_PROVEN` or `MODEL_BETTER_THAN_STOCK`. If source cannot support independent quality, be explicit: `QUALITY_UNPROVEN` and `PAUSED_EVAL_COVERAGE_GAP` is a valid **safe** result, not a failure to be papered over.
