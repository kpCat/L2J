# TASK018 authorized Git usage

Authorization: goal-objective.md and GIT.md permit read/history, exact isolated worktree, exact-path add/commit and normal push to experiment/m1-candidate007-observe008. Main checkout is read-only.

Commands used for inventory/base/scope:
- git branch --show-current
- git rev-parse HEAD
- git status --porcelain
- git status --porcelain -uall
- git remote -v
- git rev-parse --git-dir
- git rev-parse --git-common-dir
- git worktree list --porcelain
- git ls-remote origin refs/heads/experiment/m1-candidate007-observe008
- git rev-parse origin/experiment/m1-candidate007-observe008
- git diff --stat
- git diff -- <the seven exact production SOURCE_MAP paths>
- git diff --check

Native create_worktree tool created/attached the authorized exact path at the remote required base. The branch is occupied by a foreign worktree, so this checkout remains detached; no foreign ref checkout is switched.

Authorized publication commands for this stage:
- git add -- <each exact verified SOURCE_MAP/evidence file, enumerated individually>
- git diff --cached --name-only
- git diff --cached --check (raw log whitespace recorded); git diff --cached --check -- <each exact non-log staged path>
- git commit -m "Implement exact native-context handoff for task018"
- git push origin HEAD:refs/heads/experiment/m1-candidate007-observe008

Runtime pinned catalog helper uses git show HEAD:<each of three exact population catalog paths> read-only to preserve blob bytes. Runtime metadata reads git -C <module> rev-parse HEAD.

No add-dot, main checkout mutation, reset/clean/stash/rebase/force, commit of private runtime/credentials, or broad restore is permitted or used. A final artifact commit/push may follow only after the runtime/manual gates are handled.
