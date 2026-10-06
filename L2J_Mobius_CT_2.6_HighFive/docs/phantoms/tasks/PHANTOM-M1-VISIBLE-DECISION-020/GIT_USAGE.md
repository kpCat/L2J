# TASK020 Git authorization and commands

Direct user objective and GIT.md authorize read/history/fetch, isolated worktree, exact-path add/commit, normal push. Main checkout FOREIGN READ ONLY.
Commands executed (repeats grouped):
git branch --show-current
git rev-parse HEAD
git status --porcelain=v1
git worktree list --porcelain
git rev-parse experiment/m1-candidate007-observe008
git show -s --format='%H %s' 29f4b32509535bad5d73d93bbf7fac1daf6b6b53
git remote -v
git ls-remote --heads origin experiment/m1-candidate007-observe008
git worktree add --detach C:\Users\ZBook\.codex\worktrees\m1-visible-decision-020\L2J_Mobius 29f4b32509535bad5d73d93bbf7fac1daf6b6b53
git -C <TASK020 worktree> rev-parse HEAD
git -C <TASK020 worktree> status --porcelain=v1 -uno
git -C <TASK020 worktree> diff --stat -- <exact java/test paths>
git -C <TASK020 worktree> diff -- <3 source paths and TASK018 fixture path>
git -C <TASK020 worktree> diff --check
git -C <TASK020 worktree> status --porcelain=v1 -- <TASK020 directory and new suite>
git -C <TASK020 worktree> ls-tree --name-only HEAD <TASK019 directory>
git -C <TASK020 worktree> show 29f4b32509535bad5d73d93bbf7fac1daf6b6b53:<each4modifiedsourcepaths>

Read-only worktree commands used per-command -c safe.directory=C:/Users/ZBook/.codex/worktrees/m1-visible-decision-020/L2J_Mobius after sandbox user ownership mismatch. Global Git config unchanged. EOL utility restores unchanged lines from base blobs without source restore/reset.
Final scope/encoding verifier executes diff --name-only HEAD, diff --check and exact path inventory. Publication uses add -- <exact list from ARTIFACT_ALLOWLIST.json>, diff --cached --name-only, diff --cached --check, commit -- <same exact paths>, rev-parse HEAD, push origin HEAD:refs/heads/experiment/m1-candidate007-observe008, ls-remote. Actual publication receipt is recorded after execution.
No branch switch, reset, clean, stash, rebase, force push, broad add or historical artifact mutation.
