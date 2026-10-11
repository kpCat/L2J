# RUNBOOK034 — isolation, time, resources

## Git / worktree
- Source required exact `44139ca556ad3d5ac35309d5fe222269d3ada256`; verify remote HEAD first.
- Preferred single reusable Codex registered worktree: `C:\Users\ZBook\.codex\worktrees\phantom-dev\L2J_Mobius`. If it exists, use **only if** owned, clean and no foreign process; never overwrite unknown working tree. If absent, `git worktree add --detach` at exact required base in that one path only. If occupied/dirty, BLOCKED_WORKTREE and report; don't create m1-lifetime-034-2 ad infinitum.
- User main `C:\Users\ZBook\L2J_Mobius` and its `feature/phantom-world` remain READ-ONLY. PLAY/TEST shared remain READ-ONLY.
- Keep source checked-out at exact own commit; do not switch main branch nor merge into feature/phantom-world. Result normal push to experiment ref. New commits from detached HEAD allowed.

## DB & evidence
- Read retained TASK033R `a/b/c/t` and previous 031/032 only, preserving exact user data and credentials. Verify indexed external review: `C:\Users\ZBook\.codex\visualizations\2026\10\10\01a127b2-1e6e-7f12-9af8-3f3748fe3047\TASK033R-review` and its SHA manifest.
- Create only distinct task-owned DB clones `l2jmobiush5_localplay_contract034a` and `l2jmobiush5_localplay_contract034t` (or new exact suffix if occupied, with explicit inventory). No SQL heal/drop/delete/update to source. Cloning a poisoned source doesn't count as clean control unless its initial content and timeline are proved.
- Current R3 source `51cf051b3d20fbb83e6c0e16ce8c7bcad7af52cb` is ancestor of required base, not target checkout. Current code/report HEAD is above required base.
- Reuse `Prepare030/031`, `Control031/032`, existing owned fixtures/Synthetic/JDI helpers as read-first reference; do not blindly invoke their old exact-path wrappers or write to deleted worktree033R.
- Runtime caches, build trees and raw event evidence must be under task-own area or external private archive, not user main. Private logs, SQL raw dumps, account/DB credentials and JFR not staged to public Git.

## Bounded timeline
- First 45 minutes: exact profiles/initial incident source / raw safety.
- First full own JVM <=60 minutes if needed.
- At 120 minutes: either first causal RED or exact diagnosis BLOCKED; no blind 380sec scenario.
- At 240 minutes: semantic freeze; no new product edits; at 270 minutes begin final cleanup.
- Absolute 300min task deadline from true start, no automatic extension.
- <=4 actual own GameServer starts total; <=3 production builds; <=2 full scenes of 380seconds; no ≥2 repetitions without distinct hypothesis.

## Review + cleanup
- Do not delete 033R evidence or DB; its worktree was already removed.
- At completion, prefer persistent **one reusable worktree** with clean tracked state and inventoried bounded files. Optional ordinary `git worktree remove` for *only own* task worktree if user-provided disk cleanup authorization applies and manifest/evidence copied; no --force. Never forcibly remove registered tree while Codex is inside it.
- Normal Game/Login stock shutdown may happen even if `PhantomSystem` drain is FAILED; label them separately. Preserve full health truth. CPU/heap and free disk report only actual measured values.

## Exact task packet import (mandatory, before editing)
The ZIP is extracted into `C:\Users\ZBook\L2J_Mobius\L2J_Mobius_CT_2.6_HighFive\docs\phantoms\tasks\PHANTOM-M1-LIFETIME-DRAIN-034`. The newly created worktree at base HEAD has no TASK034 directory yet. Read and verify `MANIFEST.sha256` at that read-only user path. Then copy **only** its listed files plus MANIFEST to the SAME relative directory inside the task-owned worktree. Use literal absolute paths and PowerShell file operations; do not recursively copy main or stage foreign data. SHA-256 compare all copies byte-for-byte. If owner worktree already has differing TASK034 files, stop `BLOCKED_PACKET`, never overwrite uninspected files. The main task files remain untouched and untracked there; commits must be from own worktree only.
