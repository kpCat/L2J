# GOAL035 — preserve real overfull Phantom inventory through native store

**Repository:** kpCat/L2J; High Five only (`L2J_Mobius_CT_2.6_HighFive`). **Required base:** `d6d8c4b9d32276aff5563c239c5f822cf8ec83fa`. **Push destination:** `experiment/m1-candidate007-observe008`.

**Codex:** GPT-6.1 Sol / Very High, no subagents. Execute autonomously, no repeated approvals in the approved boundaries; stop on truly unsupported changes or unsafe data mutation.

**Goal:** prove and fix the mismatch that makes current inherited native profiles 36/45 retain Player and lease after `Invalid bounded background inventory`: native stock non-quest size 252 (249 INVENTORY + 3 PAPERDOLL), capacity 250, whereas some background projections count only INVENTORY. Do NOT bypass native store or pretend the inventory is not over capacity. Preserve every item exactly; disallow creation of new slot while over limit; allow lawful store/FINALIZED and safe release. Verify exact client-free native runtime on own clones, then focused tests and required full regressions.

**Not goals:** personal inventory expansion to 1000; gameplay/client GUI; profile27 cast-launch repair; full away/background/return; crash72/73; navigation rewrite; SQL cleanup, canonical row rewrites or deletion. Never change `PlayerConfig`, `PlayerInventory`, common `Player`, max bot slots, game rewards, `GeoEngine`, personal QoL or existing persisted objects merely to force PASS.

Time: usual target 2–4 hours, hard **6 hours** elapsed, semantic changes stop at 4h45m; final 30 min reserved for healthy shutdown, evidence, commit and normal push. ≤3 complete production Jar builds; ≤4 owned GameServer starts. A focused RED on unchanged baseline comes before product code changes. Stop early with an exact blocker if safe semantics cannot be proven. No repeating two unchanged failing 380s scenes.

Pinned evidence: `docs/phantoms/tasks/PHANTOM-M1-LIFETIME-DRAIN-034/RESULT_HANDOFF034.md`; raw archive `C:\Users\ZBook\.codex\visualizations\2026\10\11\01a128b8-3743-7722-8be5-be4e5a95ca9a\TASK034-review`. Original source032a/034t/034a/033R DBs are READ ONLY. Create only bounded new own DB copies with exact source and checksum inventory. Do not SQL-heal.

**Single reusable worktree** `C:\Users\ZBook\.codex\worktrees\phantom-dev\L2J_Mobius`: check exact registration, HEAD and clean tracked scope, then import only files of this package from `C:\Users\ZBook\L2J_Mobius` by manifest, never change main. Do not create a task-specific worktree. If scope/HEAD differ, fail closed and report; do not force switch, checkout, reset or overwrite unrelated files.

User authorized exact-path commits and **normal non-force push** of any GREEN/PARTIAL/BLOCKED/FAILED result to origin/experiment/m1-candidate007-observe008. Report changed files, actual RED/GREEN, current Jar SHA, exact native/SQL inventory evidence, all open gates, next steps, and full cleanup. Keep own reusable worktree; do not remove it. Leave all DBs, source review archives, other worktrees untouched.

**Task status**: `GREEN_INVENTORY_SAFE` only with accepted causal runtime + strict inventory parity + healthy complete drain + targeted and 93/93+11/11 on one verified Jar. Otherwise `PARTIAL_VERIFIED` or `BLOCKED_SAFE_SEMANTICS`; `M1=OPEN`, `REAL_FINAL=NOT_RUN` regardless.
