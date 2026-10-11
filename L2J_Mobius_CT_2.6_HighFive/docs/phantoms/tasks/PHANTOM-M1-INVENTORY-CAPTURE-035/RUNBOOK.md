# RUNBOOK035 — reusable worktree + retained data

## Ownership
MAIN read-only: `C:\Users\ZBook\L2J_Mobius`. OWN only: `C:\Users\ZBook\.codex\worktrees\phantom-dev\L2J_Mobius`. Never create `.codex/worktrees/m1-inventory-035` or duplicate whole checkout. The OWN worktree is retained from TASK034: verify registration with `git worktree list --porcelain`, exact HEAD and no tracked outside scope. If mismatch or own process active: stop with ID, no `git reset/clean/switch` under assumption.

1. Read packet from MAIN `L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-INVENTORY-CAPTURE-035` and validate MANIFEST SHA of every file. Copy only packet files into same path under OWN. Main unaffected; exact allowlist in this packet. Existing previously-generated own task files/data unmodified.
2. Use TASK034 raw review ZIP outside worktree after SHA validation (see GOAL). Preserve source032a and034t; prepare dedicated 035t/035a copies with readonly source provenance and exact paths only. Never operate on protected PLAY DB. If clone creation cannot be proven, BLOCKED_DATA.
3. Existing Control/Observe/Verify 032/034 tooling may be read/reused, but old task command allowlists and generated helper paths may be hardcoded to their own worktree. Inspect and copy only verified helpers; do not edit previous task's tracked files or leak credentials to Git. New helper if needed belongs in ignored `.phantom-local/ops035`, not new framework.
4. JVM options for JFR/JDWP only own GameServer. No global suspending debugger, no SQL in native callbacks. Snapshot exact birth/owner/epoch/slot count/World/AutoSave before test and state after. Private raw archive outside worktree, SHA-256 manifest.
5. Iterate RED→GREEN with ≤3 production builds and ≤4 server starts total. Production fix not required to run old tests if no semantic change. No synthetic gameplay loop if no safe native store yet.
6. At final status, exact file list/diff + encoding checks and normal push. Verify remote SHA by `ls-remote`. Gracefully stop own Game/Login and synthetic, ensure exact process path+PID provenance, zero own listeners. Archive raw and immutable result. Keep OWN worktree for TASK036, cleanup only known task-owned temporary build data if strictly safe; don't delete any DB.

## Resource and safety bounds
- No `git clean`, `git reset`, force push/merge/rebase, branch deletion, `worktree remove`, SQL-heal, row deletion, `UPDATE`, forced native owner release, arbitrary item drops. No archive deletion.
- Need not run full 380-sec natural scenes or crash72/73 to close this inventory slice. One bounded real owned store/reload and honest global stop are enough.
- A FAILED native subsystem shutdown must remain FAILED even when Java processes stopped. No processing of 27 first cleanup incident as if proven.
- Existing `feature/phantom-world` remains untouched; candidate branch remains `experiment/m1-candidate007-observe008`. Future personal1000-slots task explicitly excluded.
