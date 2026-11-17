# Focused offline test matrix, no GS/SQL

1. Baseline: actual feature HEAD, old Goal022 216/keys302 from real sealed provenance (read-only); protected/historical SHA unchanged.
2. Quality absence: 6 consecutive real-like TRAIN receipts with growing updates, no independently matched evaluation → STOP/PAUSE before TRAIN7; scheduled TUNING/AUDIT allowed only in frozen split slots, no gradient; no false `REGRESSION_SUPPORTED`.
3. Patience: fake three missing audits ⇒ `EVAL_COVERAGE_GAP`, not plateau. Three genuine *authenticated* separate non-improving comparable windows ⇒ `PAUSED_NO_SUPPORTED_GAIN`. One statistically credible safety regression ⇒ `PAUSED_REGRESSION`; one noisy death/kill or a fake posthoc comparison ⇒ `QUALITY_UNPROVEN`.
4. Source validation: TRAIN reused as AUDIT; changed split, duplicated eventKey, replayed paid receipt, forged modelSha, changed owner/episode, corrupt outer seal or missing physical receipt → no quality credit, fail closed.
5. Safety: attempt `Resume`/`Start`/`NewCampaign` after quality stop, restarted controller post-commit, status stale, timing boundary 479/480, no-data, disk low, boot cap, concurrent Start, Pause→Stop → same conservative result; no history resets.
6. User view: generated offline HTML (escape `<`, `&`, quotes and injected strings), summary MD/JSON consistent; paths do not expose credentials; `Status` and `Report` do not open GS/SQL and do not mutate model HEAD.
7. Cost: append only **measured** historical native time / episode time and clearly labeled approximate overhead; no unmeasured 8h throughput forecast.
8. Existing `Test-Training022.ps1` and focused post-V2 regression (no broad ant verify / DB lane), one final GREEN run; all changed test code compile/syntax; UTF-8 readable Cyrillic, 27 mojibake markers+6 escaped Cyrillic regex zero.
9. Scoped Git publication, independent GitHub exact body SHA readback, historical files/owner dirt unchanged.

Report counts RED/GREEN runs, exact pass/failed, extra reads, elapsed time; all test outputs in `.phantom-local/logs/PHANTOM-LEARNING-023-V4-QUALITY-GUARD-AND-PROGRESS/` and only summary in Codex chat.
