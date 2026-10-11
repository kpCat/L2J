# GIT035 — exact authorised workflow

Repository `kpCat/L2J`; **base** `d6d8c4b9d32276aff5563c239c5f822cf8ec83fa`; **remote branch** `experiment/m1-candidate007-observe008`. User explicitly authorizes exact-path commit + normal push after TASK035 GREEN/PARTIAL/BLOCKED/FAILED. Do not change existing `feature/phantom-world` checkout or merge to it.

OWN: `C:\Users\ZBook\.codex\worktrees\phantom-dev\L2J_Mobius`. MAIN: `C:\Users\ZBook\L2J_Mobius` (read-only). Read-first Git and source:

```powershell
$main = 'C:\Users\ZBook\L2J_Mobius'
$own = 'C:\Users\ZBook\.codex\worktrees\phantom-dev\L2J_Mobius'
$branch = 'experiment/m1-candidate007-observe008'
git -C $main worktree list --porcelain
git -C $own rev-parse HEAD
git -C $own status --porcelain --untracked-files=no
git -C $own ls-remote origin refs/heads/$branch
```

Required: own HEAD and origin ref both equal pinned BASE before import, or BLOCKED_BASE; worktree registration matches path, tracked unrelated changes absent. No detached auto-reset/switch when HEAD differs. If Git tool needs user approval under its policy, respect that approval; no alternate unsafe command.

After packet import verify every MANIFEST path and compute exact changed path list. Scope whitelist: task035 own docs/tools, and conditional source/test only from SOURCE_MAP (not all paths automatically). No `git add .`, no broad `-A`.

```powershell
$stagePaths = @(<literal approved task docs and actually modified conditional paths>)
git -C $own diff -- $stagePaths
git -C $own add -- @stagePaths
git -C $own diff --cached --check
git -C $own diff --cached --name-only
git -C $own commit -m 'TASK035: reconcile inherited inventory capture and stock capacity'
git -C $own push origin HEAD:refs/heads/experiment/m1-candidate007-observe008
git -C $own rev-parse HEAD
git -C $own ls-remote origin refs/heads/experiment/m1-candidate007-observe008
```

Do not execute placeholder syntax: fill `$stagePaths` with literal paths from checked packet and actually modified source. A BLOCKED result uses an appropriate truthful message rather than claiming a fix. Normal non-force push; zero force/reset/rebase/merge/prune/clean/branch delete.

Public result location: `L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-INVENTORY-CAPTURE-035/RESULT_HANDOFF035.md`, `EVIDENCE_INDEX035.md`, `CLEANUP035.md` created in own checkout. Private raw in external SHA-verified archive outside worktree. No credentials, DB snapshots or giant JFR in public Git. Source checks: mojibake and escaped Cyrillic separately, UTF-8; preserve original file newlines on untouched lines.

Post-task leave reusable owned worktree registered. Clean only fully identified OWN transient items with explicit guard, never assume `git clean` safe. Main remains intact.
